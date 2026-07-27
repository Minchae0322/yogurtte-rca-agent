# 회차 2 변경 대기열 — 지금 고치지 않고 모아두는 것들

AU-4 회차 1에서 확정된 결함들의 **수정안을 모아두되, 지금은 적용하지 않는다.**
회차 1을 baseline으로 고정하고 수정 후 재측정해야 **전후 델타가 성립**하기 때문이다.

> RUNBOOK §6 AP 계열 공통 원칙: "**채록·채점 전에 결함을 고치지 말 것** — 고치면 문항이
> 소멸한다." CH-1이 이미 쓴 패턴이다 — 회차 1에서 컨슈머 예외 삼킴을 발견하고,
> toy-chat `5eecb0a`로 수정한 뒤, 회차 2에서 DLQ 경유 복구를 검증했다.
> **그 대조가 "계측이 RCA 정확도를 결정한다"는 명제의 실측 근거가 됐다**(AE-02).

관련: [AU-4 회차 1](../au-4/round-1.md) · [NF-09](../findings/nf-09-user-fallback-no-traceid.md) ·
[NF-10](../findings/nf-10-content-db-connection-held-during-external-call.md) ·
[v0.1 개선 계획](../v0.1-plan.md) · [채점 대장](../scoring/README.md)

---

## ⚠️ 설계상 가장 중요한 것 — 변경군을 섞지 말 것

수정할 것이 두 계열인데, **한꺼번에 적용하면 점수가 올라도 무엇 때문인지 증명할 수 없다.**

| 변경군 | 무엇을 고치나 | 검증하는 명제 | 주입 필요? |
|---|---|---|---|
| **A — 관측 데이터(앱)** | 로그 규약 · traceId 전파 · fallback 카운터 | "관측 데이터가 좋아지면 RCA가 좋아지는가" | **필요** (회차 2) |
| **B — 조사 도구(에이전트)** | Loki 셀렉터 · `logfmt` · metric-queries · 수집 창 | "도구를 고치면 RCA가 좋아지는가" | **불필요** |

**B는 주입 없이 검증된다.** 회차 1의 traceId를 그대로 재조사하면 *같은 관측 데이터*에 대해
도구만 바뀐 델타가 나온다. 그래서 순서를 이렇게 잡는다:

```
① B 적용 → 기존 traceId 6개 재조사 → 도구 델타 확정      (주입 0회)
② ①의 결과를 새 baseline으로 고정
③ A 적용 → AU-4 회차 2 주입 → 계측 델타 확정            (주입 1회)
```

이러면 **델타가 두 번 분리되고 추가 주입은 한 번뿐**이다. 반대로 A·B를 같이 넣으면
주입 1회로 끝나지만 결과 해석이 불가능해진다 — auth 22분 다운의 값어치를 버리는 셈이다.

---

## 변경군 A — 앱 (toy-content) · **회차 2 직전 적용**

### A-1. 로그 메시지 규약 도입 · 근거 [NF-09](../findings/nf-09-user-fallback-no-traceid.md)

**문제**: `external/user` 패키지 하나에 실패 로그 **29개**, 접두사 규약 **3종 혼용**
(없음 / `[외부사용자 조회]` / 이모지 `⚠️❌`), 같은 개념을 4가지 표현으로.
→ 안정적인 쿼리·프로브·알람 룰을 쓸 수 없다. AU-4에서 하네스가 `0건`으로 오보고한 근인.

**변경**: 도메인 단위 검색 가능 접두사를 붙인다. 메시지 본문은 자유.

| 파일:행 | 현재 | 변경 후 |
|---|---|---|
| `ExternalUserApiClient.java:130` | `사용자 목록 조회 실패: userIds={}` | `[user-fallback] auth 목록 조회 실패 → {}명 익명 대체: userIds={}` |
| `:97` | `사용자 정보 조회 실패: userId={}` | `[user-fallback] auth 단건 조회 실패: userId={}` |
| `:109` · `:134` | `외부 서비스 (일괄) 호출 중 예외` | `[user-fallback] auth 호출 예외 ...` |
| `:75` | `[외부사용자 조회] 외부 API 호출 실패` | `[user-fallback] ...` |
| `UserCacheStore.java` 다수 | `⚠️`/`❌` 접두 | `[user-cache] ...` (이모지 제거) |

**범위 주의**: 이번엔 `external/user` 패키지만. 전 코드베이스 일괄 변경은 diff가 커져
델타 해석을 흐린다.

### A-2. 리액티브 경계 traceId 전파 · 근거 [NF-09](../findings/nf-09-user-fallback-no-traceid.md)

**문제**: `.doOnError` 콜백이 `reactor-http-epoll-1`에서 실행돼 MDC가 전파되지 않는다.
실측 로그가 `traceId=NONE`이라 **rca-agent의 `traceIdQuery`로는 영원히 도달 불가**.
셀렉터를 고쳐도(B-1) 이건 해결되지 않는다.

**변경**: `ExternalUserApiClient`의 `WebClient` 호출부에 Micrometer Tracing 컨텍스트 전파를
연결하거나, 최소한 호출 시점 traceId를 캡처해 로그 인자로 넘긴다.

### A-3. fallback 카운터 추가 · 근거 [NF-09](../findings/nf-09-user-fallback-no-traceid.md)

**문제**: 익명 저하 10명이 발생해도 **집계 지표가 없어 알람이 불가능**하다.
`http_client_requests_seconds_bucket{application="content-service"}`도 시리즈 부재.

**변경**: `fetchAndCacheUserInfos` 54~55행 부근.

```java
long fallbackCount = userIds.size() - fetched.size();
if (fallbackCount > 0) {
    userFallbackCounter.increment(fallbackCount);
}
```

→ `rate(user_fallback_total[5m]) > 0` 알람이 성립한다.

### A-4. 하네스 프로브 정규식 정정 (toy-content `chaos.sh`)

`loki_count user_fallback`을 **A-1의 접두사 기반**으로 바꾼다:
`{service_name="content-service"} |= "[user-fallback]"`
→ 메시지 문구를 나중에 고쳐도 프로브가 깨지지 않는다.

### A-5. `measure_AU_4`에 `tempo_search` 추가 (toy-content `chaos.sh`)

회차 1에서 traceId를 수동으로 확보해야 했다. AU-2·AU-4·CH-2 모두 같은 결함이 있다.

---

## 변경군 B — 조사 도구 (rca-agent) · **주입 없이 먼저 적용**

상세와 예측은 [v0.1-plan.md](../v0.1-plan.md) 1절. 요약만 둔다.

| # | 변경 | 파일 |
|---|---|---|
| B-1 | Loki 셀렉터 `app` → `service_name`, 값도 `-service` 접미 | `.env` (코드 변경 불필요) |
| B-2 | `errorWarnQuery`의 `\| logfmt \| level=~` → 라인 필터 `\|~ "ERROR\|WARN"` | `CollectProperties.java:21` |
| B-3 | metric-queries에 `up` · `kafka_consumergroup_lag` · `kafka_brokers` · `mongodb_up` 추가 | `application.yml` |
| B-4 | 수집 창이 복구를 담도록 — padding 상향 또는 후행 창 별도 수집 | `application.yml` / `Collector` |
| B-5 | 어셈블 컨텍스트에 span 절대 시각 명시 (`-120s` 앵커링 차단) | `ContextAssembler` |
| B-6 | `LlmResult`에 `cacheRead`/`cacheCreation` 분리 기록 | `ClaudeCliLlmClient.java:116-121` |

---

## 회차 2에 넣지 않는 것

### NF-10 (DB 커넥션 점유 중 외부 HTTP) — **AU-1 준비물로 미룬다**

[NF-10](../findings/nf-10-content-db-connection-held-during-external-call.md)은 구조 리팩터링이라
diff가 크고, **AU-4로는 검증되지 않는다** — connection refused(23.5ms)가 timeout(3s)보다
빨라서 커넥션 점유가 오히려 짧아지기 때문이다.

이 결함이 드러나는 건 auth가 *죽을* 때가 아니라 *느려질* 때다 → **AU-1(auth CPU 기아)**의
사전 가설로 두고, AU-1 실행 후에 수정 여부를 판단한다.

### AU-4 앵커 v3 정정 — 회차 2 **주입 전**에 별도로

v1·v2 앵커가 "3s timeout"과 "traceId 붙은 fallback 로그"를 만점 요건으로 요구하는데 둘 다
실현되지 않는다([채점 대장 결함 ⑩](../scoring/README.md)). 이건 코드 변경이 아니라
**앵커 수정**이므로 §8.2에 따라 회차 2 채록 **전에** 박제해야 한다.

---

## 실행 체크리스트

**단계 ① — 도구(B), 주입 없음**

- [ ] B-1 ~ B-6 적용
- [ ] 기존 traceId 6개 재조사 (CH-1×2 · CH-2 · IN-2 · AU-2 · AU-4)
- [ ] [채점 대장](../scoring/README.md)에 재조사 회차 추가, v1/v2 앵커 표기 명확히
- [ ] [v0.1-plan.md](../v0.1-plan.md) 예측 P1·P2·P3·P4 검증 결과 기록

**단계 ② — baseline 고정**

- [ ] ①의 결과를 "v0.1 baseline"으로 명명하고 동결
- [ ] AU-4 앵커 **v3** 박제 + 커밋 (채록 전이어야 유효)

**단계 ③ — 앱(A), 주입 1회**

- [ ] A-1 ~ A-5 적용 및 배포 확인
- [ ] AU-4 회차 2 주입 (auth 22분+ 다운 — 저트래픽 시간대)
- [ ] 회차 2 채록 → 조사 → v3 앵커로 채점
- [ ] 회차 1 대비 델타 기록

## 회차 2에서 무엇이 달라져야 하는가 (검증 기준)

| 항목 | 회차 1 (실측) | 회차 2 기대 | 반증 조건 |
|---|---|---|---|
| 하네스 `user_fallback` | **0건** (패턴 불일치) | 실제 발생 건수 | 여전히 0건 → 수집 경로 문제 |
| 로그의 traceId | `NONE` | 실제 traceId | NONE 유지 → 전파 지점 오판 |
| rca-agent 로그 수집 | 0건 | fallback 로그 도달 | 0건 유지 → 셀렉터 외 원인 |
| `user_fallback_total` | 지표 없음 | 익명 인원수만큼 증가 | 안 오르면 진입 지점 오판 |
| 조사 리포트 | trace 근거만 | **trace + 로그 양쪽 근거** | 변화 없으면 병목은 로그가 아님 |

**전체 가설**: 회차 1의 조사는 trace 단일 채널로만 원인에 도달했다. A를 적용하면 로그
채널이 살아나 **같은 결론에 두 개의 독립 근거**가 붙어야 한다. 근거 경로 점수가 오르지
않으면 "로그가 있으면 더 잘 찾는다"는 가정 자체를 재검토해야 한다.
