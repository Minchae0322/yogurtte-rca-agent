# RCA Report — `scan-1785907500`

| 항목 | 값 |
|---|---|
| 모드 | rca |
| 질문 | 최근 1시간 안에 피드에 작성자 이름이 이상하게 나온다는 제보가 있다. 원인을 조사해줘 |
| 시각 | 2026-08-05T05:51:57.976864200Z |
| provider | claude-cli |
| model | `claude-opus-5` · turns 1 |
| prompt | `.\prompts\system-prompt.md` |
| tokens | in 74124 (cacheRead 23,449 · cacheCreate 50,673) / out 7417 · cost $0.7039 |
| elapsed | total 138352ms (tempo 965 · loki 255 · mimir 734 · assemble 76 · llm 122736) |

## 탐색 (Triage)

| 항목 | 값 |
|---|---|
| 시간창 해석 | 상대 표현 '최근 1시간' |
| 시간창 확신도 | EXACT |
| 스윕 창 | 2026-08-05T04:51:14.192249900Z ~ 2026-08-05T05:51:14.192249900Z |
| 좁힌 창 | 2026-08-05T05:25:00Z ~ 2026-08-05T05:40:00Z |
| 대상 | content-service, auth-service |
| traceId | 6a72cad73a66188238bc26fcdaa7db0f |
| 트레이스 후보 | 4건 |
| 장애 후보 | 8건 · 선택 INC-5, INC-6, INC-7, INC-8 |
| 계획 파싱 | 성공 |
| 스윕 컨텍스트 | **후보만 — 원본 제외 (B)** |
| prompt | `.\prompts\triage-prompt.md` |
| tokens | in 32934 / out 2366 · cost $0.1929 |
| chars | 컨텍스트 3,073 + 프롬프트 1,399 = **4,472** |
| elapsed | survey 2488ms · llm 41162ms |

**선정 이유**: 질문의 증상(피드 작성자 이름)과 엔드포인트(/feeds/scroll)·시각(05:32)이 정확히 맞고, 이름의 출처인 auth-service 에러·지연이 같은 5분 창에 겹치므로 content↔auth 사용자 조회 경로를 하나의 사건으로 함께 판다.

**근거**

- INC-7: content-service http get /feeds/scroll 216ms이 error 채널로 잡힘 (05:32:07.313Z, traceId 6a72cad73a66188238bc26fcdaa7db0f) — 제보된 '피드' 엔드포인트 그 자체
- INC-5: auth-service ERROR/WARN 4건 (05:30~05:35Z) — 피드 에러와 동일 5분 창, 작성자 이름의 출처 서비스
- INC-8: auth-service http post /login 4,483ms slow (05:34:17.022Z) — auth 측 응답 지연이 같은 창에 존재, 이름 조회 타임아웃/fallback 가설의 근거
- INC-6: content-service ERROR/WARN 1건 (05:30~05:35Z) — INC-7 트레이스와 짝이 되는 로그 측 흔적, 예외 메시지 확인용으로 함께 포함
- Tempo 에러 검색 전체 1건이 곧 INC-7 — 조회 창 1시간 통틀어 유일한 에러 트레이스가 피드 엔드포인트
- up / mongodb_up / kafka_brokers / kafka_consumergroup_lag / websocket_active_users 모두 이상 0건 — 인프라·브로커·세션 계층 원인 배제

**스윕이 찾은 트레이스** (고른 것은 6a72cad73a66188238bc26fcdaa7db0f)

| traceId | 채널 | root service | root span | ms |
|---|---|---|---|---:|
| `6a72cad73a66188238bc26fcdaa7db0f` ←선택 | error | content-service | http get /feeds/scroll | 216 |
| `6a72cb5944baef1250a95f87685bf977` | slow | auth-service | http post /login | 4483 |
| `6a72c3507df6aa550e82d18871008356` | slow | content-service | task battle-hot-score-scheduler.time-weight-update | 4023 |
| `6a72c240515fe3ce1bdd0c0fc9830077` | slow | content-service | connection | 3462 |

**장애 후보** (코드가 신호를 묶은 것 · 창은 여기서 계산됨)

## INC-1  auth-service  |  ERROR/WARN
- 구간: 2026-08-05T04:45:00Z ~ 2026-08-05T04:55:00Z  (LOKI · 집계 해상도만큼 흐림)
- ERROR/WARN 3건 (2026-08-05T04:45:00Z ~ 2026-08-05T04:50:00Z)
- ERROR/WARN 2건 (2026-08-05T04:50:00Z ~ 2026-08-05T04:55:00Z)
- 같은 시각의 다른 후보: INC-2  (인과 여부는 판단하지 않았다)

## INC-2  content-service  |  ERROR/WARN
- 구간: 2026-08-05T04:50:00Z ~ 2026-08-05T05:10:00Z  (LOKI · 집계 해상도만큼 흐림)
- ERROR/WARN 3건 (2026-08-05T04:50:00Z ~ 2026-08-05T04:55:00Z)
- ERROR/WARN 42건 (2026-08-05T04:55:00Z ~ 2026-08-05T05:00:00Z)
- ERROR/WARN 148건 (2026-08-05T05:00:00Z ~ 2026-08-05T05:05:00Z)
- ERROR/WARN 56건 (2026-08-05T05:05:00Z ~ 2026-08-05T05:10:00Z)
- 같은 시각의 다른 후보: INC-1, INC-3, INC-4  (인과 여부는 판단하지 않았다)

## INC-3  content-service  |  connection
- 구간: 2026-08-05T04:55:28.641523Z ~ 2026-08-05T04:55:32.103523Z  (TEMPO · 시각 정확)
- content-service connection 3,462ms (slow 채널)
- traceId: 6a72c240515fe3ce1bdd0c0fc9830077
- 같은 시각의 다른 후보: INC-2  (인과 여부는 판단하지 않았다)

## INC-4  content-service  |  task battle-hot-score-scheduler.time-weight-update
- 구간: 2026-08-05T05:00:00.132015Z ~ 2026-08-05T05:00:04.155015Z  (TEMPO · 시각 정확)
- content-service task battle-hot-score-scheduler.time-weight-update 4,023ms (slow 채널)
- traceId: 6a72c3507df6aa550e82d18871008356
- 같은 시각의 다른 후보: INC-2  (인과 여부는 판단하지 않았다)

## INC-5  auth-service  |  ERROR/WARN
- 구간: 2026-08-05T05:30:00Z ~ 2026-08-05T05:35:00Z  (LOKI · 집계 해상도만큼 흐림)
- ERROR/WARN 4건 (2026-08-05T05:30:00Z ~ 2026-08-05T05:35:00Z)
- 같은 시각의 다른 후보: INC-6, INC-7, INC-8  (인과 여부는 판단하지 않았다)

## INC-6  content-service  |  ERROR/WARN
- 구간: 2026-08-05T05:30:00Z ~ 2026-08-05T05:35:00Z  (LOKI · 집계 해상도만큼 흐림)
- ERROR/WARN 1건 (2026-08-05T05:30:00Z ~ 2026-08-05T05:35:00Z)
- 같은 시각의 다른 후보: INC-5, INC-7, INC-8  (인과 여부는 판단하지 않았다)

## INC-7  content-service  |  http get /feeds/scroll
- 구간: 2026-08-05T05:32:07.313648Z ~ 2026-08-05T05:32:07.529648Z  (TEMPO · 시각 정확)
- content-service http get /feeds/scroll 216ms (error 채널)
- traceId: 6a72cad73a66188238bc26fcdaa7db0f
- 같은 시각의 다른 후보: INC-5, INC-6  (인과 여부는 판단하지 않았다)

## INC-8  auth-service  |  http post /login
- 구간: 2026-08-05T05:34:17.022170Z ~ 2026-08-05T05:34:21.505170Z  (TEMPO · 시각 정확)
- auth-service http post /login 4,483ms (slow 채널)
- traceId: 6a72cb5944baef1250a95f87685bf977
- 같은 시각의 다른 후보: INC-5, INC-6  (인과 여부는 판단하지 않았다)

**기각한 후보**

- INC-2 — 04:50~05:10 content-service 에러 폭증(최대 148건)으로 규모는 가장 크지만, 증상 제보 시각대(05:30대)와 30분 이상 떨어져 있고 지문이 아래 INC-3/INC-4의 스케줄러·커넥션 계열이라 이름 렌더링과 연결할 근거가 없다 — 05:30대 조사에서 auth 조회 실패의 선행 원인 정황이 나오면 즉시 재소환할 1순위 후보다.
- INC-3 — content-service connection 3,462ms(04:55:28Z)는 DB/커넥션풀 지연 지문이고 시각도 이르며, 피드 응답이 느려졌다는 제보가 아니라 이름 값이 이상하다는 제보라 증상과 맞지 않는다.
- INC-4 — battle-hot-score-scheduler.time-weight-update 4,023ms(05:00:00Z)는 핫스코어 가중치 배치로 작성자 이름 필드를 만들지 않는 경로이고, 시각도 제보 창과 어긋난다.
- INC-1 — auth-service ERROR/WARN 5건(04:45~04:55Z)은 건수가 낮은 상시 잡음 수준이고 05:30대 증상과 35분 이상 떨어져 있어, 같은 auth라도 이번 창의 근거로 쓰기 어렵다.

**보정 기록**

- 창을 후보 [INC-5, INC-6, INC-7, INC-8] 의 신호 시각에서 계산했다 (2026-08-05T05:25:00Z ~ 2026-08-05T05:40:00Z)

## 수집 범위 (Coverage)

- **window**: 2026-08-05T05:25:00Z ~ 2026-08-05T05:40:00Z (900s)
- **trace**: 48,865B / 45 spans
- **창 안 후보 트레이스**: 10건 / 48,865B
- **logs**: errwarn=19,800B · traceId=5,365B
- **metrics**: 8 수집 / 108,994B, 누락 [sum(rate(http_server_requests_seconds_count{application="content-service", status="401"}[1m]))]
- **context**: 80,265 chars (+ 시스템 프롬프트 805 chars)

### 토큰 축 (개선 지표)

| 단계 | 총 in | chars | 컨텍스트 토큰 |
|---|---:|---:|---:|
| 탐색 | 32,934 | 4,472 | 3,508 |
| 분석 | 74,124 | 81,070 | 44,698 |
| **합계** | **107,058** | | **48,206** |

- **overheadTokens 29,426 tok — 이 회차에 실측**(1자 프롬프트 프로브, 같은 명령·모델·샌드박스).
  호출마다 한 번씩 얹히므로 단계별로 뺀다. **다른 날 상수를 쓰지 않았다 = `█ 실측`.**
- contextTokens (count_tokens API): 측정 안 됨 — 구독 CLI 경로엔 API 키가 없다
- 비용은 **API 환산 추정치**다. 구독 계정으로 호출하므로 실제 청구액이 아니다.

## 수집 실패/누락

- Metric 'sum(rate(http_server_requests_seconds_count{application="content-service", status="401"}[1m]))' returned no series in this window; skipped.

---

## 1. 원인 후보 랭킹 (최대 3개)

1. **auth-service 인스턴스 부재 구간(파드 교체) 중 content-service의 사용자 조회 호출이 connection refused → 익명 이름으로 대체**
2. **content-service의 fail-open 폴백 설계 자체(장애를 200 OK + 익명 이름으로 은폐, 재시도·캐시 폴백 없음)가 "이상한 작성자 이름"의 직접 산출 경로**
3. **auth-service 신규 파드의 콜드스타트 지연으로 무응답 구간이 연장 (기여 요인, 근거 약함)**

---

## 2. 후보별 근거

### 후보 1 — auth-service 부재 구간의 connection refused

**근거**
- 트레이스 `6a72cad73a66188238bc26fcdaa7db0f`, span `http get` (kind=CLIENT), 시작 `05:32:07.371Z`, `durNs=51,585,000` (51.6ms), `status=CLIENT_ERROR`, `STATUS_CODE_ERROR`
  - `http.url = http://auth-service:8081/api/external/users?userIds=1,3,7,9`
  - `error = "finishConnect(..) failed: Connection refused: auth-service.default.svc.cluster.local/10.43.13.21:8081"`
  - `exception = WebClientRequestException`
- 같은 traceId 로그: `2026-08-05 14:32:07.400 ... c.e.t.e.u.s.ExternalUserApiClient - [user-fallback] auth 목록 조회 실패 → 4명 익명 대체: userIds=[1, 3, 7, 9]` — 제보 증상(작성자 이름 이상)과 **정확히 일치**하는 유일한 관측값
- 실패 시점에 auth-service 프로세스가 없었다는 정황:
  - 메트릭 시리즈가 두 파드로 갈린다. `auth-service-5999bb9f5c-jv2jn`(10.42.1.48)의 `jvm_gc_pause` 는 **05:25:00~05:25:15 2점에서 끊기고**, `auth-service-5999bb9f5c-lzp4p`(10.42.1.51)은 **05:35:45부터** 시작. `up{job=auth-service}` 도 lzp4p가 **05:34:45부터**만 존재 → 05:25:15~05:34:45 구간에 스크랩되는 auth 인스턴스가 **하나도 없다**. 실패 시각 05:32:07은 이 공백 한가운데다.
  - 신규 파드 lzp4p의 `[main]` 부팅 로그가 **05:32:43(Bean 경고) → 05:33:05(Hibernate) → 05:33:42(open-in-view) → 05:33:47(SecurityFilterChain 등록)** 로 이어진다. 즉 05:32:07 시점엔 Spring 컨텍스트가 아직 시작조차 안 됐다.
  - 거부된 주소 `10.43.13.21`은 Service ClusterIP 대역이다. 타임아웃이 아니라 **즉시 refused**인 것은 Ready 엔드포인트가 없어 kube-proxy가 거절한 형태와 부합한다.

**확신도: 높음** (파드 교체 이벤트 자체 — kube_pod_*, Deployment 이벤트 — 는 수집되지 않아 "왜 교체됐는지"는 미확인)

**대기·지연 구간 판정**
- 실측 대기: **51.585ms** (연결 시도)
- 상한(WebClient connect/response timeout 설정값): **데이터 부족 — 판정 불가**. 다만 종료 사유가 타임아웃이 아니라 `ConnectException: Connection refused`이므로 **타임아웃 만료는 아님**(상한 도달 전 능동 거부로 조기 종료).
- 최종 상태: **실패 확정**. 관측 범위 내 **재시도 span·로그 없음**(해당 traceId에 `http get` 스팬 1개, 폴백 로그 1줄). 결과는 폐기 후 **익명 4명으로 대체**되어 상위 요청은 `status=200`, `outcome=SUCCESS`로 완료(root span `http get /feeds/scroll`, 216.8ms).

**반증 데이터**
- `up{job=auth-service}`에 **0으로 떨어진 샘플은 없다** — 다운이 값으로 관측된 게 아니라 시리즈 결측(공백)일 뿐이다. 스크랩 타깃 자체가 사라진 것과 구분이 안 된다.
- 실패 관측 건수는 **1회**(트레이스 1건, 로그 1줄). 수집된 feed 트레이스도 이 1건뿐이라 "1시간 내 다수 사용자" 규모는 이 데이터로 확인 불가.
- 실패 이후 auth-service는 정상 응답한다: `05:34:17`의 `http post /login`이 `status=200`으로 완료.

---

### 후보 2 — fail-open 폴백 설계가 증상의 직접 산출 경로

**근거**
- 로그 원문 `[user-fallback] auth 목록 조회 실패 → 4명 익명 대체` — 업스트림 실패를 오류로 노출하지 않고 **익명 값으로 치환**하는 코드 경로가 명시적으로 존재(`ExternalUserApiClient`).
- 그 결과 `/feeds/scroll` 응답은 `status=200, exception=none, outcome=SUCCESS`. 즉 **모니터링상 정상**으로 보이는 채로 잘못된 이름이 사용자에게 전달됐다. 수집 실패한 `status="401"` 계열 메트릭이 아니더라도, 5xx/에러율로는 이 장애가 절대 잡히지 않는다.
- 폴백 직전에 **캐시 조회 흔적이 없다**: 같은 트레이스의 Redis 스팬 4개(`GET`, 0.67~2.5ms)는 모두 auth 호출(05:32:07.371) **이전인 05:32:07.350~.355**에 끝났고, 실패 이후 구간(.423~.528)에는 Redis 스팬이 없다. 실패 후 캐시/DB로 이름을 되살리려는 시도가 관측되지 않는다.

**확신도: 중간** (코드·설정을 못 봤고, 폴백 정책이 의도된 것인지 여부는 관측 데이터만으로 단정 불가)

**대기·지연 구간 판정**: 해당 없음 — 이 후보는 대기 구간이 아니라 실패 처리 분기다. 관련 대기는 후보 1의 51.585ms가 전부이며 판정은 위와 동일(만료 아님 / 실패 / 재시도 없음 / 익명 대체 후 폐기).

**반증 데이터**
- 폴백 덕분에 피드 자체는 200으로 서빙됐다(가용성 관점에서는 의도대로 동작). 이 후보는 "장애 원인"이라기보다 **증상을 이름 오류 형태로 만든 변환기**다.

---

### 후보 3 — auth-service 콜드스타트로 무응답 구간 연장

**근거**
- 신규 파드 lzp4p 부팅 로그가 05:32:43~05:33:47에 걸쳐 있고, 첫 메트릭 스크랩은 05:34:45.
- 기동 직후 첫 요청이 느리다: `2026-08-05 14:34:21 ... RequestLoggingFilter - [HTTP-SLOW] POST /api/login 200 - 4469ms`, 트레이스 `6a72cb59...`의 `http post /login` `durNs=4,483,338,000`(4.48s), 그중 `secured request` 4.40s.

**확신도: 낮음**

**대기·지연 구간 판정**
- 실측: 로그인 처리 **4,469ms / span 4,483ms**.
- 상한(해당 요청의 타임아웃 설정값, `[HTTP-SLOW]` 임계값): **데이터 부족 — 판정 불가**.
- 최종 상태: **성공** (`status=200`, `outcome=SUCCESS`, `exception=none`).

**반증 데이터**
- 이 지연은 auth **자기 자신의 로그인 경로**에서만 관측됐고, content-service가 부르는 `/api/external/users`와는 다른 엔드포인트다. 게다가 실패 시각(05:32:07)은 이 파드가 뜨기 **전**이라, 콜드스타트는 이름 오류의 원인이 될 수 없다 — 기껏해야 총 복구 시간을 늘렸을 뿐이다.
- `hikaricp_connections_active/pending`은 auth 포함 전 파드 **전 구간 0**, GC pause도 전 구간 0 → 자원 포화형 지연 근거는 없다.

---

## 3. 권장 다음 조치

### 이미 발생한 피해: 복구 가능한가
**부분 복구 가능 — 단, 별도 조치가 필요 없는 형태다.**
- 근거: 해당 트레이스의 JDBC 스팬은 전부 `select`(tb_feed, tb_feed_hashtags, tb_user_reward, tb_level_exp, tb_feed_attachment_file, categories, tb_product)이며 **INSERT/UPDATE 스팬이 없다**. 익명 이름이 DB에 기록됐다는 근거는 없다 → 영속 오염은 **관측되지 않음**. auth 복구(05:33:47 이후) 뒤 재조회하면 정상 이름이 나온다.
- 이미 전송된 응답 자체는 되돌릴 수 없다(사용자 화면 새로고침 외 방법 없음).
- **미해결 리스크 — 캐시 오염 여부는 판정 불가**: content-service가 Redis를 쓰는 것은 확실하나(`GET` 4회 + `INFO`), 폴백 결과를 `SET`한 스팬이 없어 익명 값이 캐시에 남았는지 **확인 불가**. 사용자 이름 캐시 키를 실제로 조회해 익명 값이 들어있는지 확인하고, 있으면 해당 키만 삭제할 것.

### 재발 방지
1. **auth-service 무중단 교체 보장**: 이번 공백은 약 7분 이상(05:25:15 마지막 스크랩 → 05:32:43 신규 파드 부팅 시작)이며, 그 사이 Ready 엔드포인트가 0이었음이 refused 에러로 드러났다. replicas ≥ 2 + `maxUnavailable: 0` + PodDisruptionBudget + readiness probe로 엔드포인트 0 구간을 없앨 것. (교체 원인 자체는 데이터 부족 — 아래 수집 항목 참조)
2. **폴백 정책 교정**: 익명 치환 대신 ① 짧은 TTL의 last-known 이름 캐시 폴백, ② 그래도 없으면 해당 필드를 오류로 표시(무성의한 성공 응답 금지). 최소한 폴백 발생 시 응답을 200/SUCCESS로 계산하지 말고 별도 카운터 메트릭(`user_fallback_total`)을 노출해 알람을 걸 것 — 현재는 5xx가 0이라 이 장애가 대시보드에 전혀 뜨지 않는다.
3. **재시도·서킷브레이커**: 관측상 재시도 흔적이 0회다. connection refused는 즉시(51ms) 실패하므로 짧은 백오프 재시도 1~2회는 비용이 거의 없다.
4. **수집 공백 메우기**: `kube_pod_container_status_restarts_total`, Deployment 롤아웃 이벤트, auth-service의 `http_server_requests` 시리즈, WebClient connect/response 타임아웃 설정값 — 이번 판정에서 "판정 불가"가 난 항목들이다.

### 복구 확인
- `up{job="auth-service"}`가 **결측 없이** 연속 1인지 (현재는 05:34:45부터만 존재).
- content-service에서 `/api/external/users` 호출 트레이스를 다시 떠서 `http get` 스팬이 `STATUS_CODE_ERROR` 없이 2xx인지 확인.
- Loki에서 `[user-fallback]` 문자열 발생 건수가 0인지 (`{service_name="content-service"} |= "user-fallback"`).
- `/feeds/scroll` 재호출 결과에서 userIds `1, 3, 7, 9`의 작성자 이름이 실제 값으로 나오는지 직접 확인.
- Redis의 사용자 이름 캐시 키에 익명 값이 남아있지 않은지 확인.

---

**결론 요약**: 05:32:07에 content-service가 auth-service ClusterIP로 건 `/api/external/users` 호출이 **connection refused**로 51.6ms 만에 실패했고(타임아웃 만료 아님, 재시도 없음, 실패 확정), 코드가 이를 **익명 4명으로 대체**해 200으로 서빙한 것이 제보된 "작성자 이름 이상"의 직접 원인이다. 그 시각 auth-service는 파드 교체 공백(05:25:15~05:33:47 부팅 완료) 상태였다. **교체가 왜 일어났는지는 수집된 데이터로 판정 불가.**

---

## 관측 증거 (Evidence)

> 이 조사가 실제로 본 값이다. 원본 응답 전체는 `reports/raw/scan-1785907500-*.json`에 있다.

### 호출 그래프 (트레이스에서 추출)

```
content-service --db--> redis  5회  최대 2.5ms  [GET, INFO]
content-service --jdbc--> mysql/content (HikariPool-1)  21회  최대 211.2ms
    events: acquired, commit
content-service --service--> auth-service  1회  최대 51.6ms
    error: WebClientRequestException
    error: finishConnect(..) failed: Connection refused: auth-service.default.svc.cluster.local/10.43.13.21:8081
```

### span (duration 상위 15 / 전체 45)

| ms | service | span | 시작 |
|---:|---|---|---|
| 4483.34 | auth-service | `http post /login` | 2026-08-05T05:34:17.022170Z |
| 4403.50 | auth-service | `secured request` | 2026-08-05T05:34:17.089867Z |
| 216.84 | content-service | `http get /feeds/scroll` | 2026-08-05T05:32:07.313648Z |
| 213.84 | content-service | `secured request` | 2026-08-05T05:32:07.314434Z |
| 211.17 | content-service | `connection` | 2026-08-05T05:32:07.315985Z |
| 57.51 | auth-service | `security filterchain before` | 2026-08-05T05:34:17.031421Z |
| 51.59 | content-service | `http get` | 2026-08-05T05:32:07.371738Z |
| 25.78 | content-service | `query` | 2026-08-05T05:32:07.491129Z |
| 8.41 | content-service | `secured request` | 2026-08-05T05:37:15.748649Z |
| 5.65 | content-service | `query` | 2026-08-05T05:32:07.321153Z |
| 5.33 | content-service | `result-set` | 2026-08-05T05:32:07.446380Z |
| 5.09 | content-service | `query` | 2026-08-05T05:32:07.481701Z |
| 4.79 | content-service | `result-set` | 2026-08-05T05:32:07.327176Z |
| 4.47 | content-service | `result-set` | 2026-08-05T05:32:07.340696Z |
| 4.10 | content-service | `query` | 2026-08-05T05:32:07.335957Z |

### 로그 원문 (60 / 전체 83줄)

전체가 상한을 넘어 ERROR/WARN 줄을 우선 발췌했다. 나머지는 원본 파일에 있다.

```
2026-08-05T05:32:07.421859468Z  [content-service]  2026-08-05 14:32:07.400 [reactor-http-epoll-4] ERROR [traceId=6a72cad73a66188238bc26fcdaa7db0f,spanId=58b52521d7429768,userId=NONE] c.e.t.e.u.s.ExternalUserApiClient - [user-fallback] auth 목록 조회 실패 → 4명 익명 대체: userIds=[1, 3, 7, 9]
2026-08-05T05:32:07.421859468Z  [content-service]  2026-08-05 14:32:07.400 [reactor-http-epoll-4] ERROR [traceId=6a72cad73a66188238bc26fcdaa7db0f,spanId=58b52521d7429768,userId=NONE] c.e.t.e.u.s.ExternalUserApiClient - [user-fallback] auth 목록 조회 실패 → 4명 익명 대체: userIds=[1, 3, 7, 9]
2026-08-05T05:32:07.421926801Z  [content-service]  org.springframework.web.reactive.function.client.WebClientRequestException: finishConnect(..) failed: Connection refused: auth-service.default.svc.cluster.local/10.43.13.21:8081
2026-08-05T05:32:07.421932575Z  [content-service]  at org.springframework.web.reactive.function.client.ExchangeFunctions$DefaultExchangeFunction.lambda$wrapException$9(ExchangeFunctions.java:137)
2026-08-05T05:32:07.421937268Z  [content-service]  Suppressed: reactor.core.publisher.FluxOnAssembly$OnAssemblyException:
2026-08-05T05:32:07.421979425Z  [content-service]  at org.springframework.web.reactive.function.client.ExchangeFunctions$DefaultExchangeFunction.lambda$wrapException$9(ExchangeFunctions.java:137)
2026-08-05T05:32:07.421982621Z  [content-service]  at reactor.core.publisher.MonoErrorSupplied.subscribe(MonoErrorSupplied.java:55)
2026-08-05T05:32:07.421985458Z  [content-service]  at reactor.core.publisher.Mono.subscribe(Mono.java:4576)
2026-08-05T05:32:07.421988665Z  [content-service]  at reactor.core.publisher.FluxOnErrorResume$ResumeSubscriber.onError(FluxOnErrorResume.java:103)
2026-08-05T05:32:07.421991460Z  [content-service]  at reactor.core.publisher.FluxPeek$PeekSubscriber.onError(FluxPeek.java:222)
2026-08-05T05:32:07.421994178Z  [content-service]  at reactor.core.publisher.FluxPeek$PeekSubscriber.onError(FluxPeek.java:222)
2026-08-05T05:32:07.421997440Z  [content-service]  at reactor.core.publisher.FluxPeek$PeekSubscriber.onError(FluxPeek.java:222)
2026-08-05T05:32:07.422000182Z  [content-service]  at reactor.core.publisher.MonoNext$NextSubscriber.onError(MonoNext.java:93)
2026-08-05T05:32:07.422002939Z  [content-service]  at reactor.core.publisher.MonoFlatMapMany$FlatMapManyMain.onError(MonoFlatMapMany.java:205)
2026-08-05T05:32:07.422005678Z  [content-service]  at reactor.core.publisher.SerializedSubscriber.onError(SerializedSubscriber.java:124)
2026-08-05T05:32:07.422008505Z  [content-service]  at reactor.core.publisher.FluxRetryWhen$RetryWhenMainSubscriber.whenError(FluxRetryWhen.java:229)
2026-08-05T05:32:07.422011279Z  [content-service]  at reactor.core.publisher.FluxRetryWhen$RetryWhenOtherSubscriber.onError(FluxRetryWhen.java:279)
2026-08-05T05:32:07.422014042Z  [content-service]  at reactor.core.publisher.FluxContextWrite$ContextWriteSubscriber.onError(FluxContextWrite.java:121)
2026-08-05T05:32:07.422017192Z  [content-service]  at reactor.core.publisher.FluxConcatMapNoPrefetch$FluxConcatMapNoPrefetchSubscriber.maybeOnError(FluxConcatMapNoPrefetch.java:327)
2026-08-05T05:32:07.422020266Z  [content-service]  at reactor.core.publisher.FluxConcatMapNoPrefetch$FluxConcatMapNoPrefetchSubscriber.onNext(FluxConcatMapNoPrefetch.java:212)
2026-08-05T05:32:07.422023028Z  [content-service]  at reactor.core.publisher.FluxContextWrite$ContextWriteSubscriber.onNext(FluxContextWrite.java:107)
2026-08-05T05:32:07.422025707Z  [content-service]  at reactor.core.publisher.SinkManyEmitterProcessor.drain(SinkManyEmitterProcessor.java:476)
2026-08-05T05:32:07.422028442Z  [content-service]  at reactor.core.publisher.SinkManyEmitterProcessor$EmitterInner.drainParent(SinkManyEmitterProcessor.java:620)
2026-08-05T05:32:07.422031202Z  [content-service]  at reactor.core.publisher.FluxPublish$PubSubInner.request(FluxPublish.java:874)
2026-08-05T05:32:07.422033872Z  [content-service]  at reactor.core.publisher.FluxContextWrite$ContextWriteSubscriber.request(FluxContextWrite.java:136)
2026-08-05T05:32:07.422036611Z  [content-service]  at reactor.core.publisher.FluxConcatMapNoPrefetch$FluxConcatMapNoPrefetchSubscriber.request(FluxConcatMapNoPrefetch.java:337)
2026-08-05T05:32:07.422039300Z  [content-service]  at reactor.core.publisher.FluxContextWrite$ContextWriteSubscriber.request(FluxContextWrite.java:136)
2026-08-05T05:32:07.422042232Z  [content-service]  at reactor.core.publisher.Operators$DeferredSubscription.request(Operators.java:1743)
2026-08-05T05:32:07.422044882Z  [content-service]  at reactor.core.publisher.FluxRetryWhen$RetryWhenMainSubscriber.onError(FluxRetryWhen.java:196)
2026-08-05T05:32:07.422047865Z  [content-service]  at reactor.core.publisher.MonoCreate$DefaultMonoSink.error(MonoCreate.java:205)
2026-08-05T05:32:07.422050541Z  [content-service]  at reactor.netty.http.client.HttpClientConnect$MonoHttpConnect$ClientTransportSubscriber.onError(HttpClientConnect.java:318)
2026-08-05T05:32:07.422053303Z  [content-service]  at reactor.core.publisher.MonoCreate$DefaultMonoSink.error(MonoCreate.java:205)
2026-08-05T05:32:07.422056037Z  [content-service]  at reactor.netty.resources.DefaultPooledConnectionProvider$DisposableAcquire.onError(DefaultPooledConnectionProvider.java:174)
2026-08-05T05:32:07.422065396Z  [content-service]  at reactor.netty.internal.shaded.reactor.pool.AbstractPool$Borrower.fail(AbstractPool.java:479)
2026-08-05T05:32:07.422068268Z  [content-service]  at reactor.netty.internal.shaded.reactor.pool.SimpleDequePool.lambda$drainLoop$9(SimpleDequePool.java:436)
2026-08-05T05:32:07.422070950Z  [content-service]  at reactor.core.publisher.FluxDoOnEach$DoOnEachSubscriber.onError(FluxDoOnEach.java:186)
2026-08-05T05:32:07.422073744Z  [content-service]  at reactor.core.publisher.MonoCreate$DefaultMonoSink.error(MonoCreate.java:205)
2026-08-05T05:32:07.422097308Z  [content-service]  at reactor.netty.resources.DefaultPooledConnectionProvider$PooledConnectionAllocator$PooledConnectionInitializer.onError(DefaultPooledConnectionProvider.java:593)
2026-08-05T05:32:07.422100572Z  [content-service]  at reactor.core.publisher.MonoFlatMap$FlatMapMain.secondError(MonoFlatMap.java:241)
2026-08-05T05:32:07.422103369Z  [content-service]  at reactor.core.publisher.MonoFlatMap$FlatMapInner.onError(MonoFlatMap.java:315)
2026-08-05T05:32:07.422106109Z  [content-service]  at reactor.core.publisher.FluxOnErrorResume$ResumeSubscriber.onError(FluxOnErrorResume.java:106)
2026-08-05T05:32:07.422108778Z  [content-service]  at reactor.core.publisher.Operators.error(Operators.java:198)
2026-08-05T05:32:07.422111650Z  [content-service]  at reactor.core.publisher.MonoError.subscribe(MonoError.java:53)
2026-08-05T05:32:07.422114309Z  [content-service]  at reactor.core.publisher.Mono.subscribe(Mono.java:4576)
2026-08-05T05:32:07.422116949Z  [content-service]  at reactor.core.publisher.FluxOnErrorResume$ResumeSubscriber.onError(FluxOnErrorResume.java:103)
2026-08-05T05:32:07.422119565Z  [content-service]  at reactor.netty.transport.TransportConnector$MonoChannelPromise.tryFailure(TransportConnector.java:576)
2026-08-05T05:32:07.422122200Z  [content-service]  at reactor.netty.transport.TransportConnector$MonoChannelPromise.setFailure(TransportConnector.java:522)
2026-08-05T05:32:07.422124930Z  [content-service]  at reactor.netty.transport.TransportConnector.lambda$doConnect$7(TransportConnector.java:261)
2026-08-05T05:32:07.422127594Z  [content-service]  at io.netty.util.concurrent.DefaultPromise.notifyListener0(DefaultPromise.java:590)
2026-08-05T05:32:07.422130242Z  [content-service]  at io.netty.util.concurrent.DefaultPromise.notifyListeners0(DefaultPromise.java:583)
2026-08-05T05:32:07.422175767Z  [content-service]  Caused by: io.netty.channel.AbstractChannel$AnnotatedConnectException: finishConnect(..) failed: Connection refused: auth-service.default.svc.cluster.local/10.43.13.21:8081
2026-08-05T05:32:07.422178408Z  [content-service]  Caused by: java.net.ConnectException: finishConnect(..) failed: Connection refused
2026-08-05T05:32:07.422181076Z  [content-service]  at io.netty.channel.unix.Errors.newConnectException0(Errors.java:166)
2026-08-05T05:32:43.594987699Z  [auth-service]  [2m2026-08-05 14:32:43[0;39m [2m[main][0;39m [33m WARN [traceId=NONE,spanId=NONE,userId=NONE][0;39m [36mo.s.c.s.PostProcessorRegistrationDelegate$BeanPostProcessorChecker[0;39m [2m-[0;39m Bean 'org.springframework.ws.config.annotation.DelegatingWsConfiguration' of type [org.springframework.ws.config.annotation.DelegatingWsConfiguration$$SpringCGLIB$$0] is not eligible for getting processed by all BeanPostProcessors (for example: not eligible for auto-proxying). The currently created BeanPostProcessor [annotationActionEndpointMapping] is declared through a non-static factory method on that class; consider declaring it as static instead.
2026-08-05T05:33:05.599831233Z  [auth-service]  [2m2026-08-05 14:33:05[0;39m [2m[main][0;39m [33m WARN [traceId=NONE,spanId=NONE,userId=NONE][0;39m [36morg.hibernate.orm.deprecation[0;39m [2m-[0;39m HHH90000025: MySQLDialect does not need to be specified explicitly using 'hibernate.dialect' (remove the property setting and it will be selected by default)
2026-08-05T05:33:42.774790947Z  [auth-service]  [2m2026-08-05 14:33:42[0;39m [2m[main][0;39m [33m WARN [traceId=NONE,spanId=NONE,userId=NONE][0;39m [36mo.s.b.a.o.j.JpaBaseConfiguration$JpaWebConfiguration[0;39m [2m-[0;39m spring.jpa.open-in-view is enabled by default. Therefore, database queries may be performed during view rendering. Explicitly configure spring.jpa.open-in-view to disable this warning
2026-08-05T05:33:47.499669944Z  [auth-service]  [2m2026-08-05 14:33:47[0;39m [2m[main][0;39m [32m INFO [traceId=NONE,spanId=NONE,userId=NONE][0;39m [36mo.s.s.web.DefaultSecurityFilterChain[0;39m [2m-[0;39m Will secure Or [Mvc [pattern='/api/external/**']] with [org.springframework.security.web.session.DisableEncodeUrlFilter@194e913b, org.springframework.security.web.context.request.async.WebAsyncManagerIntegrationFilter@7382ec67, org.springframework.security.web.context.SecurityContextHolderFilter@25c66a5a, org.springframework.security.web.header.HeaderWriterFilter@49ed96e3, org.springframework.web.filter.CorsFilter@dd909bb, org.springframework.security.web.authentication.logout.LogoutFilter@5ef5455c, com.example.toyauth.app.common.filter.ExternalAuthenticationFilter@79c48ad5, org.springframework.security.web.savedrequest.RequestCacheAwareFilter@16139b0, org.springframework.security.web.servletapi.SecurityContextHolderAwareRequestFilter@5b133d16, org.springframework.security.web.authentication.AnonymousAuthenticationFilter@31d29626, org.springframework.security.web.session.SessionManagementFilter@11cac750, org.springframework.security.web.access.ExceptionTranslationFilter@fbcba93, org.springframework.security.web.access.intercept.AuthorizationFilter@4edea211]
2026-08-05T05:33:47.867738161Z  [auth-service]  [2m2026-08-05 14:33:47[0;39m [2m[main][0;39m [32m INFO [traceId=NONE,spanId=NONE,userId=NONE][0;39m [36mo.s.s.web.DefaultSecurityFilterChain[0;39m [2m-[0;39m Will secure any request with [org.springframework.security.web.session.DisableEncodeUrlFilter@3434890, org.springframework.security.web.context.request.async.WebAsyncManagerIntegrationFilter@381f0bf4, org.springframework.security.web.context.SecurityContextHolderFilter@6ffd0aa8, org.springframework.security.web.header.HeaderWriterFilter@4b17474d, org.springframework.web.filter.CorsFilter@a56277b, org.springframework.security.web.authentication.logout.LogoutFilter@24d5a11a, org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestRedirectFilter@75d7e128, org.springframework.security.oauth2.client.web.OAuth2LoginAuthenticationFilter@1d3aad20, com.example.toyauth.app.common.filter.JwtFilter@7335aae5, org.springframework.security.web.authentication.ui.DefaultLoginPageGeneratingFilter@7d8e1c87, org.springframework.security.web.authentication.ui.DefaultLogoutPageGeneratingFilter@351bfccc, org.springframework.security.web.savedrequest.RequestCacheAwareFilter@1c5f6c2, org.springframework.security.web.servletapi.SecurityContextHolderAwareRequestFilter@7099f498, org.springframework.security.web.authentication.AnonymousAuthenticationFilter@2c053537, org.springframework.security.web.session.SessionManagementFilter@4279fc70, org.springframework.security.web.access.ExceptionTranslationFilter@37eb27a2]
2026-08-05T05:34:21.496997614Z  [auth-service]  [2m2026-08-05 14:34:21[0;39m [2m[http-nio-8081-exec-1][0;39m [33m WARN [traceId=6a72cb5944baef1250a95f87685bf977,spanId=50a95f87685bf977,userId=NONE][0;39m [36mc.e.t.a.c.f.RequestLoggingFilter[0;39m [2m-[0;39m [HTTP-SLOW] POST /api/login 200 - 4469ms
2026-08-05T05:34:21.496997614Z  [auth-service]  [2m2026-08-05 14:34:21[0;39m [2m[http-nio-8081-exec-1][0;39m [33m WARN [traceId=6a72cb5944baef1250a95f87685bf977,spanId=50a95f87685bf977,userId=NONE][0;39m [36mc.e.t.a.c.f.RequestLoggingFilter[0;39m [2m-[0;39m [HTTP-SLOW] POST /api/login 200 - 4469ms
```

### 메트릭 시계열

| 쿼리 | series | 점 | min | max | last | 값이 0이던 구간 |
|---|---|---:|---:|---:|---:|---|
| `hikaricp_connections_active` | `{__name__=hikaricp_connections_active, application=auth-service, cluster=yogurtte-k3s-prod, container=auth-service, instance=10.42.1.51:8090, job=auth-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=auth-service-5999bb9f5c-lzp4p, pool=HikariPool-1, service=auth-service}` | 22 | 0 | 0 | 0 | **2026-08-05T05:34:45Z ~ 2026-08-05T05:40:00Z** |
| `hikaricp_connections_active` | `{__name__=hikaricp_connections_active, application=chat-service, cluster=yogurtte-k3s-prod, container=chat-service, instance=10.42.1.47:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-fdcc7c776-xf4sv, pool=HikariPool-1}` | 61 | 0 | 0 | 0 | **2026-08-05T05:25:00Z ~ 2026-08-05T05:40:00Z** |
| `hikaricp_connections_active` | `{__name__=hikaricp_connections_active, application=content-service, cluster=yogurtte-k3s-prod, container=content-service, instance=10.42.1.50:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-5489b58cbc-2ckp6, pool=HikariPool-1}` | 61 | 0 | 0 | 0 | **2026-08-05T05:25:00Z ~ 2026-08-05T05:40:00Z** |
| `hikaricp_connections_active` | `{__name__=hikaricp_connections_active, application=content-service, cluster=yogurtte-k3s-prod, container=content-service, instance=10.42.3.45:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-5489b58cbc-r8r5j, pool=HikariPool-1}` | 61 | 0 | 0 | 0 | **2026-08-05T05:25:00Z ~ 2026-08-05T05:40:00Z** |
| `hikaricp_connections_pending` | `{__name__=hikaricp_connections_pending, application=auth-service, cluster=yogurtte-k3s-prod, container=auth-service, instance=10.42.1.51:8090, job=auth-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=auth-service-5999bb9f5c-lzp4p, pool=HikariPool-1, service=auth-service}` | 22 | 0 | 0 | 0 | **2026-08-05T05:34:45Z ~ 2026-08-05T05:40:00Z** |
| `hikaricp_connections_pending` | `{__name__=hikaricp_connections_pending, application=chat-service, cluster=yogurtte-k3s-prod, container=chat-service, instance=10.42.1.47:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-fdcc7c776-xf4sv, pool=HikariPool-1}` | 61 | 0 | 0 | 0 | **2026-08-05T05:25:00Z ~ 2026-08-05T05:40:00Z** |
| `hikaricp_connections_pending` | `{__name__=hikaricp_connections_pending, application=content-service, cluster=yogurtte-k3s-prod, container=content-service, instance=10.42.1.50:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-5489b58cbc-2ckp6, pool=HikariPool-1}` | 61 | 0 | 0 | 0 | **2026-08-05T05:25:00Z ~ 2026-08-05T05:40:00Z** |
| `hikaricp_connections_pending` | `{__name__=hikaricp_connections_pending, application=content-service, cluster=yogurtte-k3s-prod, container=content-service, instance=10.42.3.45:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-5489b58cbc-r8r5j, pool=HikariPool-1}` | 61 | 0 | 0 | 0 | **2026-08-05T05:25:00Z ~ 2026-08-05T05:40:00Z** |
| `rate(jvm_gc_pause_seconds_sum[5m])` | `{action=end of major GC, application=chat-service, cause=Allocation Failure, cluster=yogurtte-k3s-prod, container=chat-service, gc=MarkSweepCompact, instance=10.42.1.47:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-fdcc7c776-xf4sv}` | 61 | 0 | 0 | 0 | **2026-08-05T05:25:00Z ~ 2026-08-05T05:40:00Z** |
| `rate(jvm_gc_pause_seconds_sum[5m])` | `{action=end of minor GC, application=auth-service, cause=G1 Evacuation Pause, cluster=yogurtte-k3s-prod, container=auth-service, gc=G1 Young Generation, instance=10.42.1.48:8090, job=auth-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=auth-service-5999bb9f5c-jv2jn, service=auth-service}` | 2 | 0 | 0 | 0 | **2026-08-05T05:25:00Z ~ 2026-08-05T05:25:15Z** |
| `rate(jvm_gc_pause_seconds_sum[5m])` | `{action=end of minor GC, application=auth-service, cause=G1 Evacuation Pause, cluster=yogurtte-k3s-prod, container=auth-service, gc=G1 Young Generation, instance=10.42.1.51:8090, job=auth-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=auth-service-5999bb9f5c-lzp4p, service=auth-service}` | 18 | 0 | 0 | 0 | **2026-08-05T05:35:45Z ~ 2026-08-05T05:40:00Z** |
| `rate(jvm_gc_pause_seconds_sum[5m])` | `{action=end of minor GC, application=auth-service, cause=Metadata GC Threshold, cluster=yogurtte-k3s-prod, container=auth-service, gc=G1 Young Generation, instance=10.42.1.48:8090, job=auth-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=auth-service-5999bb9f5c-jv2jn, service=auth-service}` | 2 | 0 | 0 | 0 | **2026-08-05T05:25:00Z ~ 2026-08-05T05:25:15Z** |
| `rate(jvm_gc_pause_seconds_sum[5m])` | `{action=end of minor GC, application=auth-service, cause=Metadata GC Threshold, cluster=yogurtte-k3s-prod, container=auth-service, gc=G1 Young Generation, instance=10.42.1.51:8090, job=auth-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=auth-service-5999bb9f5c-lzp4p, service=auth-service}` | 18 | 0 | 0 | 0 | **2026-08-05T05:35:45Z ~ 2026-08-05T05:40:00Z** |
| `rate(jvm_gc_pause_seconds_sum[5m])` | `{action=end of minor GC, application=chat-service, cause=Allocation Failure, cluster=yogurtte-k3s-prod, container=chat-service, gc=Copy, instance=10.42.1.47:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-fdcc7c776-xf4sv}` | 61 | 0.000 | 0.000 | 0.000 | — |
| `rate(jvm_gc_pause_seconds_sum[5m])` | `{action=end of minor GC, application=content-service, cause=Allocation Failure, cluster=yogurtte-k3s-prod, container=content-service, gc=Copy, instance=10.42.1.50:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-5489b58cbc-2ckp6}` | 61 | 0 | 0.000 | 0 | **2026-08-05T05:25:00Z ~ 2026-08-05T05:26:45Z, 2026-08-05T05:31:00Z ~ 2026-08-05T05:40:00Z** |
| `rate(jvm_gc_pause_seconds_sum[5m])` | `{action=end of minor GC, application=content-service, cause=Allocation Failure, cluster=yogurtte-k3s-prod, container=content-service, gc=Copy, instance=10.42.3.45:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-5489b58cbc-r8r5j}` | 61 | 0 | 0.000 | 0.000 | **2026-08-05T05:25:00Z ~ 2026-08-05T05:36:30Z** |
| `up` | `{__name__=up, app=node-exporter, cluster=yogurtte-k3s-prod, component=metrics, container=node-exporter, instance=ip-172-31-40-241, job=integrations/node_exporter, k8s_cluster_name=yogurtte-k3s-prod, namespace=monitoring, pod=grafana-k8s-monitoring-node-exporter-cph5l, source=kubernetes, workload=DaemonSet/grafana-k8s-monitoring-node-exporter}` | 61 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, app=node-exporter, cluster=yogurtte-k3s-prod, component=metrics, container=node-exporter, instance=ip-172-31-45-39, job=integrations/node_exporter, k8s_cluster_name=yogurtte-k3s-prod, namespace=monitoring, pod=grafana-k8s-monitoring-node-exporter-9kvqt, source=kubernetes, workload=DaemonSet/grafana-k8s-monitoring-node-exporter}` | 61 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, cluster=yogurtte-k3s-prod, container=auth-service, instance=10.42.1.51:8090, job=auth-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=auth-service-5999bb9f5c-lzp4p}` | 22 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, cluster=yogurtte-k3s-prod, container=chat-service, instance=10.42.1.47:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-fdcc7c776-xf4sv}` | 61 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, cluster=yogurtte-k3s-prod, container=content-service, instance=10.42.1.50:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-5489b58cbc-2ckp6}` | 61 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, cluster=yogurtte-k3s-prod, container=content-service, instance=10.42.3.45:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-5489b58cbc-r8r5j}` | 61 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, cluster=yogurtte-k3s-prod, instance=10.42.1.248:8080, job=integrations/kubernetes/kube-state-metrics, k8s_cluster_name=yogurtte-k3s-prod, source=kubernetes}` | 61 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, cluster=yogurtte-k3s-prod, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod}` | 61 | 1 | 1 | 1 | — |
| `mongodb_up` | `{__name__=mongodb_up, cluster=yogurtte-k3s-prod, instance=infra-server, job=mongodb, k8s_cluster_name=yogurtte-k3s-prod}` | 61 | 1 | 1 | 1 | — |
| `kafka_brokers` | `{__name__=kafka_brokers, cluster=yogurtte-k3s-prod, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod}` | 61 | 1 | 1 | 1 | — |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=chat-service-fcm-tokens, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=0, topic=user.fcm-tokens}` | 61 | 0 | 0 | 0 | **2026-08-05T05:25:00Z ~ 2026-08-05T05:40:00Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=chat-service-fcm-tokens, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=1, topic=user.fcm-tokens}` | 61 | 0 | 0 | 0 | **2026-08-05T05:25:00Z ~ 2026-08-05T05:40:00Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=chat-service-fcm-tokens, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=2, topic=user.fcm-tokens}` | 61 | 0 | 0 | 0 | **2026-08-05T05:25:00Z ~ 2026-08-05T05:40:00Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=chat-service-notification-settings, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=0, topic=user.notification-settings}` | 61 | 0 | 0 | 0 | **2026-08-05T05:25:00Z ~ 2026-08-05T05:40:00Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=chat-service-notification-settings, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=1, topic=user.notification-settings}` | 61 | 0 | 0 | 0 | **2026-08-05T05:25:00Z ~ 2026-08-05T05:40:00Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=chat-service-notification-settings, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=2, topic=user.notification-settings}` | 61 | 0 | 0 | 0 | **2026-08-05T05:25:00Z ~ 2026-08-05T05:40:00Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=db-writer, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=0, topic=chat.messages}` | 61 | 0 | 0 | 0 | **2026-08-05T05:25:00Z ~ 2026-08-05T05:40:00Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=db-writer, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=1, topic=chat.messages}` | 61 | 0 | 0 | 0 | **2026-08-05T05:25:00Z ~ 2026-08-05T05:40:00Z** |
| `websocket_active_users` | `{__name__=websocket_active_users, application=chat-service, cluster=yogurtte-k3s-prod, container=chat-service, instance=10.42.1.47:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-fdcc7c776-xf4sv}` | 61 | 0 | 0 | 0 | **2026-08-05T05:25:00Z ~ 2026-08-05T05:40:00Z** |

값이 0이던 구간은 굵게 표시했다 — 프로세스가 사라져 시계열이 꺾인 것이 유일한 신호인 장애가 있다.

