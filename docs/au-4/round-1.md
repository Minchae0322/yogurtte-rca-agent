# AU-4 회차 1 — auth 다운 22분 51초 + 캐시 만료: fallback이 버텼다

## 한눈 요약

| | |
|---|---|
| **실제 원인** | `kubectl scale deploy/auth-service --replicas=0` — auth 전면 다운 **22분 51초**. user 캐시(TTL 10분)까지 만료돼 content가 auth 직행 |
| **갈래** | **A — fallback 정상 저하** (T2 200 + 작성자 익명). 붕괴(5xx) 아님 |
| **실제 영향** | 로그인 503 전면 불가 / 피드는 **200 유지, 작성자 10명 전원 익명 `사용자N`**. 데이터 품질만 저하 |
| **에이전트 파악 원인** | "auth-service 연결 불가(Connection refused) → 작성자 배치 조회 실패 → content가 삼킨 채 익명 폴백으로 렌더링" **확신도 높음 — 정답**. 상위 원인으로 "파드/엔드포인트 부재(지속 다운)"를 별도 후보로 계층 분리 |
| **판정** | 원인·계층 분리·오귀인 배제·조치 전부 정확. **앵커가 요구한 "3s timeout"이 실제로는 23.5ms refused였고, 에이전트가 그 구별을 정확히 해냈다** |
| **§8 채점** | **채점 불가 (앵커 부적합)** — 근본원인 **40/40** · 오귀인 **20/20** · 조치 **10/10** 만점, 근거 경로만 산출 불가. 앵커가 **틀린 사실**을 만점 요건으로 요구했다. [채점 대장](../scoring/README.md#au-4-회차-1--채점-불가-앵커-부적합) |
| **토큰·비용·시간** | in 61,528 / out 10,511 tok · **$0.7089** · 165.5s — **6회 중 최저 비용** |
| **에이전트 보고서 전문** | [round-1-rca-report.md](round-1-rca-report.md) |

> 이 회차는 `ClaudeCliLlmClient`의 **cwd 격리 적용 후 첫 조사**다(레포 `CLAUDE.md`가 에이전트
> 컨텍스트로 새던 문제, [v0.1-plan.md §0](../v0.1-plan.md)). AU-2와 컨텍스트 크기가 거의
> 같은데(65,366자 vs 65,958자) 입력 토큰이 3,886 줄었다 — 격리 효과로 추정된다.

## 장애 상황

- 주입: master 셸 `kubectl -n $NS scale deploy/auth-service --replicas=0`
  — **07:00:51 ~ 07:23:42 UTC (22분 51초)** = KST 16:00:51 ~ 16:23:42
- baseline 채록 `07:00:29Z` · symptom 채록 **`07:23:39Z` (주입 후 22분 48초)**
  → user 캐시 TTL 10분의 **2.3배**를 경과시켰으므로 캐시 만료가 보장된다
- 원복 후 `07:26:06Z` **로그인 200 복귀 확인** (rollout 완료 후 폴링 통과)
- 실행: `./chaos.sh AU-4 run`

## 실측 대조 (하네스)

| 프로브 | baseline (07:00:29) | symptom (07:23:39) | 해석 |
|---|---|---|---|
| 로그인 `POST /auth/login` | **200** | **503** | 직접 경로 전면 불가 |
| T2 `GET /feeds/scroll?size=10` | **200** / 0.274902s | **200** / **0.170260s** | 5xx 없음 = **fallback 정상** |
| 익명 작성자 수 | 0 | **10명** (`사용자N`) | 캐시 만료 → 직행 실패 → 익명 저하 |
| `user_fallback(1h)` Loki | 0건 | **0건** | ← 아래 참조 |
| `user_client_p99` PromQL | (없음) | (없음) | ← 아래 참조 |

**장애 중에 오히려 빨라졌다** — 0.2749s → 0.1703s. baseline은 auth 왕복 100ms를 기다렸고,
symptom은 23.5ms만에 거절당했다. **지연 기반 알람으로는 원리적으로 못 잡는 장애**다.

## 스크린샷용 traceId

| 용도 | traceId | spans | duration |
|---|---|---|---|
| **정상 대조** (baseline, 캐시 미스 + auth 정상) | `6a67020d9d618589141817d961c25f9d` | **74** | 232.11ms |
| **장애 창** (symptom, 캐시 만료 + auth 다운) | `6a67077c87b8b863f15cc6ee1ac95fbb` | **66** | 126.61ms |

`measure_AU_4`도 `tempo_search`를 호출하지 않아 Tempo API로 직접 확보했다.

## 실제 신호 발췌

### Tempo — 두 트레이스의 결정적 차이

| | baseline (74 spans) | symptom (66 spans) |
|---|---|---|
| auth 호출 client span | `http get` **112.55ms** 정상 | `http get` **23.55ms** `STATUS_CODE_ERROR` |
| auth 서버 span | `http get /external/users` **100.65ms** | **없음** |

symptom span의 error 원문:

```
http.url : http://auth-service:8081/api/external/users?userIds=3,7,9,56
error    : finishConnect(..) failed: Connection refused:
           auth-service.default.svc.cluster.local/10.43.13.21:8081
exception: WebClientRequestException
구간     : .329514 ~ .353060  =  23.5ms
```

**3초 타임아웃이 아니라 23.5ms 즉시 거절이다.** 코드에 `TIMEOUT = Duration.ofSeconds(3)`이
있지만 그건 *대기 상한*이라, TCP RST로 즉시 실패하면 발동할 틈이 없다.
→ RUNBOOK·앵커의 "3s timeout 후 fallback" 서술은 실측과 어긋난다 (아래 "앵커 결함" 참조).

선행 신호도 정확하다: 실패 직전 Redis `GET` **4건**(`.326934`~`.328716`) = 캐시 미스 4건 →
원격 배치 조회(`userIds=3,7,9,56`) → 거절. **캐시 만료가 방아쇠였음이 trace에 남아 있다.**

### Loki — 로그는 있다. 그런데 `traceId=NONE`이다

> ⚠️ **최초 판독 정정 (2026-07-27).** 하네스가 `user_fallback: 0건`으로 보고해 처음엔
> "앱에 fallback 로그가 없다"고 적었으나, **오판이었다.** 실측으로 로그를 찾았다.
> 경위는 아래 "0건은 없음이 아니었다" 절.

```
07:23:40.346Z  ERROR  [reactor-http-epoll-1]  [traceId=NONE,spanId=NONE,userId=NONE]
               c.e.t.e.u.s.ExternalUserApiClient - 사용자 목록 조회 실패: userIds=...
```

쿼리: `{service_name="content-service"} |~ "사용자 목록 조회 실패"` · 07:00~07:27Z → **1건**

`ExternalUserApiClient.java:130`이 `.doOnError(...)`로 ERROR 로그를 남긴다. 로그는 **정상적으로
기록되고 있다.** 문제는 **`traceId=NONE`**이다 — `.doOnError` 콜백이 Reactor Netty 이벤트
루프(`reactor-http-epoll-1`)에서 실행돼 요청 스레드의 MDC가 전파되지 않는다.

그 결과 rca-agent의 두 로그 경로가 **둘 다 이 로그를 못 잡는다**:

| 쿼리 | 결과 | 이유 |
|---|---|---|
| `traceIdQuery` (`\|= "<traceId>"`) | ❌ | 로그에 traceId가 NONE. **셀렉터를 고쳐도 못 잡는다** |
| `errorWarnQuery` | ❌ | 셀렉터·`logfmt` 결함(별건). 고쳐도 **어느 요청인지는 모른다** |

→ [NF-09](../findings/nf-09-user-fallback-no-traceid.md)로 등재. [NF-08](../findings/nf-08-dlq-trace-discontinuity.md)
(DLQ 경계 trace 단절)과 **같은 구조적 문제**다 — 비동기·리액티브 경계에서 traceId가 끊긴다.

### "0건은 없음이 아니었다" — 3층이 각각 다른 이유로 못 봤다

이 회차에서 가장 값어치 있는 관찰이다. 같은 로그를 세 주체가 서로 다른 이유로 놓쳤다.

| 주체 | 결과 | 실제 이유 |
|---|---|---|
| **하네스** (`chaos.sh`) | `user_fallback: 0건` | 프로브 정규식이 `"대체 사용자\|fallback\|Fallback"`인데 실제 메시지는 `"사용자 목록 조회 실패"` — **패턴 불일치** |
| **rca-agent** | 로그 0건으로 조사 | Loki 셀렉터 결함(`app` → `service_name`) + `traceId=NONE` |
| **사람(조사자)** | "앱에 로그가 없다"고 판단 | 하네스의 0건을 **검증 없이 부재로 해석** |

셋 다 "0건"을 보고 "없다"로 읽었다. 실제로는 **있는데 못 찾은 것**이다.
이건 이 프로젝트가 계속 부딪히는 주제와 정확히 같다 — IN-2의 `kafka_brokers`도 "0이 아니라
시계열이 끊긴 것"이었고, [AE-03](../findings/ae-03-rca-v0-in2-blind-eval.md)이 지적한
"부재 신호를 못 쓴다"의 이면이다. **부재를 주장하려면 쿼리가 맞는지부터 증명해야 한다.**

### Mimir — 관측 채널 자체가 비어 있다

`histogram_quantile(0.99, ... http_client_requests_seconds_bucket{application="content-service"})`
→ **baseline·symptom 모두 (없음)**. content-service의 HTTP 클라이언트 메트릭이 노출되지 않는다.
auth 직행 호출의 실패율·지연을 메트릭으로 볼 수단이 없다.

## 원인 대조

| | 내용 |
|---|---|
| **실제 원인** | auth-service `replicas=0` 22분 51초 + user 캐시(TTL 10분) 만료 → content가 auth 직행 → connection refused → `createFallbackUserInfo` 익명 저하 |
| **정답지** | "auth 전면 다운 + 캐시 만료 → content가 auth 직행 → 익명 fallback으로 저하. 원인은 auth 다운이고, content 500이면 fallback 붕괴(별개)." → **갈래 A 그대로 재현** |
| **에이전트 파악** | 후보1 "Connection refused로 배치 조회 실패 → 익명 폴백 렌더링"(**확신도 높음**), 후보2 "auth 파드/엔드포인트 부재 — **후보1의 상위 원인**"(중간). 원인을 **직접 원인 / 상위 원인 계층으로 분리**했다 |
| **근거 경로 백미** | ① **timeout vs RST 구별** — "23.5ms 즉시 실패(타임아웃이 아니라 TCP RST)"를 명시. ② **캐시 미스를 방아쇠로 특정** — Redis GET 4건이 조회 대상 4명과 개수 일치함을 근거로. ③ **메트릭 부재를 대조군과 함께 제시** — "auth만 3종 메트릭 전부에서 시리즈 0개, content·chat은 17개 데이터포인트 연속" |
| **자기 한계 표명** | "'실패 → `사용자{id}` 문자열 생성'이라는 마지막 고리는 코드/로그로 확인되지 않은 추론", "대상 ID가 `3,7,9,56`으로 제보된 `사용자123`과 직접 일치하지 않는다 — 제보 건 자체를 관측한 것은 아니다" |
| **오귀인** | 없음. 후보3(Redis negative caching)은 "auth 복구 후에도 익명 잔존"이라는 **다른 증상**에 대한 저확신 가설이고, 스스로 "이 트레이스에 Redis 쓰기 스팬이 없다"고 반증했다 |

## 이 회차가 찾아낸 것

**1. fallback 저하가 조사에 연결되지 않는다 → [NF-09](../findings/nf-09-user-fallback-no-traceid.md)**

사용자 10명의 데이터 품질이 저하됐는데 관측면은 이렇다:

| 채널 | 상태 |
|---|---|
| HTTP 상태 | **200 SUCCESS** — 정상으로 집계됨 |
| 지연 | 오히려 **단축**(0.27s → 0.17s) — auth 왕복 100ms를 안 기다리니까 |
| 로그 | **있다**(`ExternalUserApiClient.java:130`) — 단 **`traceId=NONE`** |
| 집계 지표 | **없다** — fallback 카운터 부재, `http_client_requests` 시리즈도 부재 |

**사후 조사(RCA)는 trace의 client span error로 가능하다** — 에이전트가 실제로 그렇게 했다.
**막힌 것은 두 가지**다: ① 로그를 특정 요청에 연결할 수 없고(traceId 없음), ② 알람을 걸
집계 지표가 없다. 에이전트도 조치 9에서 후자를 독립적으로 짚었다 — "외부 의존성이 완전히
죽어도 `200 SUCCESS`로만 관측되어 알림이 전혀 울리지 않습니다."

**2. DB 커넥션을 쥔 채 외부 HTTP 호출 (에이전트 독립 발견)**

실패한 `http get` span의 **부모가 JDBC `connection` span**(`.307480 ~ .429418`)이다.
이번엔 refused로 23.5ms만에 끝나 무해했지만, auth가 *느려지는* 장애였다면 커넥션 풀
고갈로 번졌을 구조다. [NF-01](../findings/nf-01-consumer-holds-connection-during-dispatch.md)
(chat 컨슈머)과 **같은 계열의 문제가 content 읽기 경로에도 있다.**

## 앵커 결함 — 앵커가 틀리고 에이전트가 맞았다

v1 앵커의 근거 경로 만점 요건은 "user client span **timeout(3s)** → **fallback 로그** →
작성자 익명"인데, 실측 결과 **3개 중 2개가 사실과 다르다**:

| 요건 | 실측 |
|---|---|
| 3s timeout | ❌ **23.5ms connection refused**. `.timeout(3s)`는 대기 상한이라 RST엔 발동조차 안 함 |
| fallback 로그 | ⚠️ 로그는 **존재한다**(`ExternalUserApiClient.java:130`). 그러나 **`traceId=NONE`**이라 traceId 기반 조사로는 도달 불가 → [NF-09](../findings/nf-09-user-fallback-no-traceid.md) |
| 작성자 익명 | ✅ |

에이전트는 앵커가 요구한 "3s timeout"을 찾는 대신 **"타임아웃이 아니라 TCP RST"라고 정정**했다.
앵커 기준으로는 만점을 줄 수 없지만, 그건 에이전트가 아니라 **앵커가 틀렸기 때문**이다.

같은 회차에 확인된 부수 오류: 런북·앵커가 찾으라고 한 `GET user-service` client span은
존재하지 않는다 — 실제 span 이름은 `http get`이고 서비스 식별은 `client.name=auth-service`
속성에 있다.

→ **v2 앵커도 같은 결함을 물려받았다**(제가 코드 상수 `Duration.ofSeconds(3)`만 보고 작성).
[v0.1-plan.md](../v0.1-plan.md)의 "앵커 작성 전 소급 채록 선행" 교훈을 또 어긴 것이므로,
다음 회차 전에 v3로 정정해야 한다.
