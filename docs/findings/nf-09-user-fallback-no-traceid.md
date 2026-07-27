# NF-09. user fallback 실패 로그가 traceId 없이 남는다 — 로그는 있는데 조사에서 구조적으로 누락

- 심각도: **높음** (관측성)
- 상태: **확정 · 의도적 미수정** — 회차 1을 baseline으로 두고 **회차 2 직전에 수정**한다.
  지금 고치면 전후 비교의 기준점이 사라진다(RUNBOOK §6 AP 공통 원칙, CH-1이 쓴 패턴).
- 근거: 실측 로그 1건 + 코드 위치 5곳 + 재현된 상태코드
- 발견 경로: [AU-4 회차 1](../au-4/round-1.md) 장애 주입 (2026-07-27, auth 22분 51초 다운 + 캐시 만료)
- 계열: [NF-08](nf-08-dlq-trace-discontinuity.md)(DLQ 경계 trace 단절)과 같은 문제 —
  **"로그는 남는데 traceId가 없어 조사에 연결되지 않는다"**

## 무엇이 문제인가

auth-service가 죽어 사용자 정보 조회가 실패하면 content는 익명 fallback(`사용자N`)으로
저하시켜 응답한다. 이 설계 자체는 옳고, 실제로 의도대로 동작했다(AU-4 회차 1: 피드 200 유지).

문제는 **그 저하를 사후에 특정할 수 없다**는 것이다. 실패 로그는 남지만 `traceId=NONE`이라
**어느 요청이 저하됐는지 연결할 방법이 없다.**

## 실측 근거

**① 로그는 존재한다** (AU-4 회차 1, 주입 창 07:00:51~07:23:42Z)

```
07:23:40.346Z  ERROR  [reactor-http-epoll-1]  [traceId=NONE,spanId=NONE,userId=NONE]
               c.e.t.e.u.s.ExternalUserApiClient - 사용자 목록 조회 실패: userIds=...
```

쿼리: `{service_name="content-service"} |~ "사용자 목록 조회 실패"` · 07:00~07:27Z → **1건**

**② 그런데 traceId가 비어 있다.** 스레드가 `reactor-http-epoll-1` — Reactor Netty 이벤트
루프다. MDC(traceId/spanId/userId)는 요청 스레드에 바인딩되는데 `.doOnError()` 콜백은 다른
스레드에서 실행되므로 전파되지 않는다.

**③ 같은 요청은 HTTP 200으로 성공 처리된다.**

| 관측면 | AU-4 회차 1 실측 |
|---|---|
| HTTP 상태 | **200** (`outcome=SUCCESS`, `exception=none`) |
| 응답 시간 | **0.1703s** — baseline 0.2749s보다 **빠름** |
| 익명 저하 사용자 | **10명** (`사용자N`) |
| trace의 실패 흔적 | client span `http get` 23.5ms `STATUS_CODE_ERROR` ✅ |
| 로그의 traceId | **NONE** ❌ |

**④ 코드 위치**

`toy-content/src/main/java/com/example/toycontent/external/user/service/ExternalUserApiClient.java`

| 행 | 내용 |
|---|---|
| **130** | `.doOnError(error -> log.error("사용자 목록 조회 실패: userIds={}", userIds, error))` — 배치 경로. **AU-4가 탄 곳** |
| 131 | `.onErrorReturn(Collections.emptyList())` — 빈 목록 반환 |
| 54~55 | `userIds.forEach(userId -> result.computeIfAbsent(userId, this::createFallbackUserInfo))` — 빠진 사용자를 익명으로 채움. **여기엔 로그도 카운터도 없다** |
| 96~98 | 단건 경로의 대응 코드(`.doOnError` + `.onErrorReturn`) — 같은 구조 |

## 메커니즘 — 왜 조사에서 누락되는가

rca-agent가 로그를 찾는 두 경로가 **둘 다 이 로그를 못 잡는다**:

| 쿼리 | 결과 | 이유 |
|---|---|---|
| `traceIdQuery` — `{...} \|= "<traceId>"` | ❌ | 로그에 traceId가 **NONE**이라 문자열 매칭이 안 된다. **셀렉터를 고쳐도 못 잡는다** |
| `errorWarnQuery` — `{...} \| logfmt \| level=~"ERROR\|WARN"` | ❌ | 셀렉터·파싱 결함(별건). 고치면 잡히지만, **어느 요청인지는 여전히 모른다** |

즉 셀렉터를 고쳐도 **"이 traceId의 요청이 저하됐다"는 연결은 복원되지 않는다.**
AU-4 회차 1에서 에이전트는 로그 0건으로 조사했고, 원인 규명은 trace의 client span error로
해냈다 — 로그는 처음부터 도달 불가능한 경로였다.

**부하가 오르면 무엇이 무너지나**

- 캐시 미스가 늘수록 익명 저하 비율이 오르는데 **여전히 전부 HTTP 200**이다. 저하 비율을
  셀 수단이 없다(카운터 부재 + 로그를 traceId로 집계 불가).
- auth가 *refused*가 아니라 *느려지는* 장애면 `.timeout(3s)`이 실제로 발동한다. 그때는
  요청당 3초가 [NF-10](nf-10-content-db-connection-held-during-external-call.md)의 DB 커넥션
  점유와 곱해져 풀 고갈로 번진다. 이 경우에도 사용자 응답은 200이다.
- 즉 **저하가 영구화돼도 알 방법이 없다.** auth가 며칠 죽어 있어도 피드는 200이고 전원
  익명으로 서빙된다.

## 함께 드러난 문제 둘 (별건이지만 같은 창에서 확인)

**(a) 집계 지표가 없다.** fallback 발동 횟수를 세는 카운터가 없고,
`http_client_requests_seconds_bucket{application="content-service"}`도 시리즈가 **부재**하다
(AU-4 baseline·symptom 모두 "(없음)"). 알람을 걸 지표가 하나도 없다.

**(b) 하네스 프로브 패턴이 코드와 불일치한다.** `chaos.sh`의 `loki_count user_fallback`이
`"대체 사용자|fallback|Fallback"`을 찾는데 실제 메시지는 `"사용자 목록 조회 실패"`다.
AU-4 회차 1에서 **0건으로 보고돼 "로그가 아예 없다"고 오판할 뻔했다.** → toy-content
`chaos.sh` 수정 필요. 이 문서의 최초 초안도 그 오판 위에 쓰였다가 실측으로 정정했다.

## 개선안과 검증 가능한 예측

**개선 1 — MDC를 리액티브 컨텍스트로 전파** (핵심)

`.doOnError` 콜백에서 traceId가 살아 있게 한다. Micrometer Tracing의
`ContextSnapshot`/`ObservationRegistry`를 WebClient에 연결하거나, 최소한 호출 시점의
traceId를 캡처해 로그 인자로 넘긴다.

> **예측**: 수정 후 같은 주입을 재현하면 `사용자 목록 조회 실패` 로그에 traceId가 실린다.
> 그러면 rca-agent의 `traceIdQuery`가 그 로그를 잡고, 조사 리포트가 "이 요청이 익명으로
> 저하됐다"를 **trace와 로그 양쪽 근거로** 말할 수 있어야 한다.
> **반증 조건**: 로그에 traceId가 실렸는데도 조사 결과가 달라지지 않으면, 병목은 로그가
> 아니라 다른 곳이다.

**개선 2 — fallback 카운터 추가**

```java
// fetchAndCacheUserInfos, 54~55행 부근
long fallbackCount = userIds.size() - fetched.size();
if (fallbackCount > 0) {
    userFallbackCounter.increment(fallbackCount);
}
```

> **예측**: `rate(user_fallback_total[5m]) > 0` 알람이 성립한다. AU-4를 재주입하면
> 익명 저하 인원 수(회차 1 기준 10명)만큼 카운터가 오른다.
> **반증 조건**: 카운터가 안 오르면 fallback 진입 지점을 잘못 짚은 것이다.

**개선 3 — 하네스 프로브 패턴 정정** (toy-content `chaos.sh`)

`loki_count user_fallback`의 정규식을 실제 로그 메시지에 맞춘다:
`"사용자 목록 조회 실패|외부 서비스 일괄 호출 중 예외|사용자 정보 조회 실패"`

단, 이건 **대증요법**이다. 메시지가 바뀌면 또 어긋난다. 근본은 개선 4다.

**개선 4 — 로그 메시지 규약 도입** (근본 원인)

프로브 정규식이 어긋난 것은 **맞출 규약이 없었기 때문**이다. `external/user` 패키지 하나만
훑어도 실패 로그가 **29개**인데 표기가 제각각이다:

| 문제 | 실측 |
|---|---|
| 접두사 규약 **3종 혼용** | 없음(`사용자 목록 조회 실패`) / 대괄호(`[외부사용자 조회] 외부 API 호출 실패`) / 이모지(`⚠️ 사용자 캐시 갱신 실패`, `❌ Redis 일괄 캐시 저장 실패`) |
| 같은 개념을 **4가지 표현**으로 | `사용자 정보 조회 실패`(97) · `외부 서비스 호출 중 예외`(109) · `사용자 목록 조회 실패`(130) · `외부 서비스 일괄 호출 중 예외`(134) |
| 이모지가 로그 본문에 | `⚠️` 8건 · `❌` 8건 — LogQL 정규식·알람 룰에서 다루기 불안정하고 인코딩 이슈를 만든다 |

**이 상태에서는 안정적인 쿼리를 쓸 수 없다.** "사용자 조회 실패를 세라"는 요구에 정답
정규식이 존재하지 않는다 — 5개 메시지를 다 열거하거나, 하나라도 빠뜨리면 과소 집계된다.
AU-4에서 실제로 그 일이 일어났다.

**규약안**: 도메인 단위 검색 가능한 접두사를 붙이고 메시지 본문은 자유롭게 둔다.

```java
// 예: [user-fallback], [user-cache], [kafka-retry] ...
log.error("[user-fallback] auth 조회 실패 → {}명 익명 대체: userIds={}", fallbackCount, userIds, e);
```

그러면 쿼리·프로브·알람이 전부 접두사 하나로 안정된다:
`{service_name="content-service"} |= "[user-fallback]"`

> **예측**: 규약 적용 후 `chaos.sh`의 프로브를 접두사 기반으로 바꾸면, 메시지 문구를 나중에
> 고쳐도 프로브가 깨지지 않는다. AU-4 재주입 시 `user_fallback` 카운트가 **0건이 아니라
> 실제 발생 건수**로 보고되어야 한다.
> **반증 조건**: 접두사를 붙였는데도 프로브가 0건이면 문제는 메시지가 아니라 수집 경로다.

## 근인과 근본 원인 — 이 건의 인과 구조

RCA 프로젝트답게 층을 나눠 적는다.

| 층 | 무엇이 |
|---|---|
| **증상** | 익명 저하 10명이 관측·조사 어디에도 잡히지 않음 |
| **근인 1** | 하네스 프로브 정규식이 실제 메시지와 불일치 → `0건` 오보고 |
| **근인 2** | 로그에 `traceId=NONE` → traceId 기반 조사로 도달 불가 |
| **근인 3** | rca-agent Loki 셀렉터 결함(별건, 6회 연속) |
| **근본 원인 A** | **로그 메시지 규약 부재** — 근인 1을 필연적으로 만든다. 맞출 규약이 없으면 쿼리는 계속 어긋난다 |
| **근본 원인 B** | **리액티브 경계에서 MDC 미전파** — 근인 2. [NF-08](nf-08-dlq-trace-discontinuity.md)과 동일 구조 |
| **공백** | 집계 지표(카운터) 부재 — 위를 다 고쳐도 **알람은 여전히 불가** |

세 근인이 독립적이라, **하나만 고쳐서는 탐지가 복원되지 않는다.** 규약만 고치면 프로브는
맞지만 여전히 어느 요청인지 모르고, traceId만 고치면 프로브는 계속 0건이다.

## 왜 이걸 기록하는가

**설계가 옳았기 때문에 발견하기 어려웠던 문제**다. 코드 리뷰로는 안 나온다 — fallback이
있으니 통과다. 부하 테스트로도 안 나온다 — 200이 나오니까. **의존 서비스를 실제로 죽이고
캐시를 말려봐야** 드러난다.

그리고 회복탄력성과 관측성의 관계를 보여준다: **fallback이 장애를 잘 흡수할수록 계측이
더 중요해진다.** fallback이 없으면 500이 터져 알람이 울리지만, 있으면 계측이 유일한 발견
수단이 된다. 지금은 그 계측이 절반만(로그는 있으나 traceId 없음, 카운터 없음) 되어 있다.

## 참조

- 회차 기록: [au-4/round-1.md](../au-4/round-1.md) · 에이전트 평가: [AE-06](ae-06-rca-v0-au4-blind-eval.md)
- 같은 계열: [NF-08](nf-08-dlq-trace-discontinuity.md) — DLQ 재처리가 `traceId=NONE`으로 끊겨
  RCA가 유실/복구를 구분 못 한 사례. **두 건 모두 "비동기·리액티브 경계에서 traceId가
  끊긴다"는 하나의 구조적 문제다.**
- 인접 결함: [NF-10](nf-10-content-db-connection-held-during-external-call.md)
