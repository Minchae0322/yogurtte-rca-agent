# NF-10. content 읽기 경로가 DB 커넥션을 쥔 채 외부 HTTP를 호출한다

- 심각도: **중간** (현재 무해, 부하·지연 조건에서 위험)
- 상태: **확정** (트레이스 부모-자식 관계 + 타이밍 실측)
- 발견 경로: [AU-4 회차 1](../au-4/round-1.md) — **에이전트가 먼저 지적했고** 트레이스로 확인
- 계열: [NF-01](nf-01-consumer-holds-connection-during-dispatch.md)과 동일한 안티패턴이
  **chat 컨슈머(쓰기)뿐 아니라 content 읽기 경로에도** 있다

## 무엇이 문제인가

`GET /feeds/scroll` 처리 중 **JDBC 커넥션을 점유한 상태에서 auth-service로 외부 HTTP 호출**을
한다. 외부 서비스의 응답 시간이 그대로 DB 커넥션 점유 시간이 된다.

## 실측 근거

**트레이스 `6a67077c87b8b863f15cc6ee1ac95fbb`** (AU-4 symptom, 2026-07-27 07:23:40Z)

```
http get /feeds/scroll                     .304218 ~ .430825   126.6ms  200 SUCCESS
└─ connection (JDBC)                       .307480 ~ .429418   121.9ms  ← 커넥션 점유
   ├─ (Redis GET ×4)                       .326934 ~ .328716
   ├─ http get  [client.name=auth-service] .329514 ~ .353060    23.5ms  STATUS_CODE_ERROR
   │     └─ Connection refused: auth-service.default.svc.cluster.local/10.43.13.21:8081
   └─ commit                               .425700
```

**외부 HTTP span의 부모가 JDBC `connection` span이다.** 커넥션 121.9ms 중 23.5ms가 외부 호출
대기였다. 워터폴 스크린샷: [au-4/round-1.md](../au-4/round-1.md) — `connection` 아래에
`redisGET`·`http get`·`contentquery`가 전부 중첩돼 있는 것이 한눈에 보인다.

**커넥션 점유는 피드 건수에 비례해 늘어난다.** 같은 트레이스 하단에 `contentquery` →
`contentresult-set` 쌍이 20회 넘게 반복되는데(피드 11건 기준 `categories` 11회 +
`tb_feed_hashtags` 11회 = N+1), 이것도 전부 같은 `connection` span 안이다. 페이지 크기가
커지면 외부 호출 대기와 N+1이 **같은 커넥션 위에서 함께** 길어진다.

**정상 시(baseline `6a67020d9d618589141817d961c25f9d`, 07:00:29Z)는 더 길다** —
auth 호출 client span이 **112.55ms**, auth 서버 span `http get /external/users`가 100.65ms.
즉 **평상시에도 DB 커넥션을 100ms 넘게 외부 호출 대기로 점유한다.**

## 메커니즘 — 부하가 오르면 무엇이 무너지나

이번 장애는 **connection refused라 23.5ms만에 끝나서 무해했다.** 위험한 건 반대 경우다:

| auth 상태 | 외부 호출 대기 | 커넥션 점유 | 결과 |
|---|---|---|---|
| 정상 | ~100ms | ~120ms | 현재 상태 |
| **다운(refused)** | **23.5ms** | ~122ms | AU-4 회차 1 — 오히려 **빨라짐** |
| **느림(timeout까지)** | **3,000ms** (`TIMEOUT = Duration.ofSeconds(3)`) | **3,000ms+** | ⚠️ **풀 고갈** |

세 번째가 AU-1(auth CPU 기아) 시나리오다. 요청당 커넥션 점유가 25배로 뛰므로,
동시 요청 수가 커넥션 풀 크기를 넘는 순간 **auth 지연이 content 전면 장애로 전이**된다.
fallback은 사용자 정보만 보호할 뿐 **커넥션 풀은 보호하지 않는다.**

AU-4 회차 1은 이 위험을 **증명하지 못했다** — refused가 timeout보다 빨랐기 때문이다.
검증하려면 auth를 *죽이는* 게 아니라 *느리게* 만들어야 한다(AU-1).

## 개선안과 검증 가능한 예측

**개선 — 외부 호출을 트랜잭션/커넥션 경계 밖으로 분리**

피드 목록을 먼저 조회해 커넥션을 반납한 뒤, 사용자 정보 조회를 별도 단계로 수행한다.
즉시 적용이 어렵다면 최소한 `TIMEOUT`을 커넥션 풀 여유에 맞춰 낮추고(3s → 300~500ms)
서킷 브레이커를 붙인다.

> **예측**: 수정 후 트레이스에서 `http get`(auth) span이 JDBC `connection` span의 **자식이
> 아니어야** 한다. 그리고 부하 상태에서 AU-1(auth CPU 기아)을 주입했을 때
> `hikaricp_connections_pending`이 0을 유지해야 한다 — 현재 구조라면 pending이 쌓인다.
> **반증 조건**: 분리 후에도 pending이 쌓이면 병목은 다른 곳이다.

## 검증 시점 — **부하 테스트 트랙 (AU-1 · IN-3)**

이 결함은 **auth가 죽을 때가 아니라 느려질 때만** 드러나고, **부하가 있어야** 커넥션 풀이
실제로 마른다. 두 조건이 다 필요하므로 단독 주입으로는 증명되지 않는다.

- AU-4에서는 refused(23.5ms)가 timeout(3s)보다 빨라 커넥션 점유가 **오히려 짧아졌다**
- AU-1 단독 주입도 부하가 없으면 `hikaricp_connections_pending`이 0을 유지한다

→ **content·chat·auth 통합 부하 테스트**에서 AU-1·IN-3와 함께 검증한다
(STATUS ①-d). 순서:

```
① 부하 + AU-1/IN-3 주입 (NF-10 그대로) → pending 곡선 실측
② NF-10 수정 (외부 호출을 커넥션 밖으로)
③ 같은 조건 재주입 → 곡선 전후 대조
```

이러면 "커넥션 밖으로 뺐다"가 아니라 **"고쳤더니 풀 고갈 임계점이 얼마나 밀렸다"**를
수치로 말할 수 있다.

## 왜 이걸 기록하는가

- **에이전트가 먼저 찾았다.** AU-4 리포트 조치 10: "실패한 `http get` 스팬의 부모는 JDBC
  `connection` 스팬입니다. **DB 커넥션을 점유한 채 외부 HTTP를 호출**하는 구조로,
  auth-service가 refused가 아니라 **타임아웃**으로 느려졌다면 커넥션 풀 고갈로 번졌을
  구조입니다." — 이번 장애의 원인이 아님을 명시하면서 별건으로 분리해 제시했다.
- **NF-01이 단발 사례가 아님을 보인다.** chat 컨슈머(쓰기 경로)에서 발견한 안티패턴이
  content 읽기 경로에도 있다 = 코드베이스 전반의 패턴일 가능성. 다른 외부 호출 지점도
  같은 기준으로 훑어볼 근거가 된다.
- **부하 테스트 트랙의 사전 가설이 된다.** AU-1·IN-3는 아직 미실행인데, 이 구조 때문에
  "content가 3s timeout으로 잘리고 풀이 고갈된다"는 예측을 **주입 전에** 세울 수 있다.
  맞으면 예측의 정확성이, 틀리면 구조 이해의 공백이 산출물이다.

## 참조

- 회차 기록: [au-4/round-1.md](../au-4/round-1.md) · 에이전트 평가: [AE-06](ae-06-rca-v0-au4-blind-eval.md)
- 같은 계열: [NF-01](nf-01-consumer-holds-connection-during-dispatch.md) (chat 컨슈머)
- 인접 결함: [NF-09](nf-09-user-fallback-no-traceid.md)
