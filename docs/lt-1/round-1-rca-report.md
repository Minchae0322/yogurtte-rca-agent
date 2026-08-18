# RCA Report — `scan-1786974960`

| 항목 | 값 |
|---|---|
| 모드 | rca |
| 질문 | 어젯밤(8월 17일) 밤 11시 전후로 로그인이 너무 느리거나 아예 안 된다는 문의가 여러 건 들어왔다. 원인을 조사해줘 |
| 시각 | 2026-08-18T10:04:22.680319Z |
| provider | claude-cli |
| model | `claude-opus-5` · turns 1 |
| prompt | `./prompts/system-prompt.md` |
| tokens | in 170757 (cacheRead 15,560 · cacheCreate 155,195) / out 9912 · cost $1.8075 |
| elapsed | total 170255ms (tempo 6224 · loki 489 · mimir 787 · assemble 29 · llm 159064) |

## 탐색 (Triage)

| 항목 | 값 |
|---|---|
| 시간창 해석 | 명시적 from/to |
| 시간창 확신도 | EXACT |
| 스윕 창 | 2026-08-17T13:50:00Z ~ 2026-08-17T14:10:00Z |
| 좁힌 창 | 2026-08-17T13:56:00Z ~ 2026-08-17T14:07:00Z |
| 대상 | auth-service |
| traceId | 6a8313998ffffec2a18e55e626e90016 |
| 트레이스 후보 | 20건 |
| 장애 후보 | 3건 · 선택 INC-1, INC-2 |
| 계획 파싱 | 성공 |
| 스윕 컨텍스트 | **후보만 — 원본 제외 (B)** |
| prompt | `./prompts/triage-prompt.md` |
| tokens | in 25047 / out 1247 · cost $0.1338 |
| chars | 컨텍스트 2,353 + 프롬프트 1,399 = **3,752** |
| elapsed | survey 3871ms · llm 22273ms |

**선정 이유**: INC-2의 /login 31초 지연과 INC-1의 auth-service JDBC 커넥션 예외는 같은 시각·같은 서비스에서 관측된 한 장애의 하류(증상)와 상류(원인 예외) 지문이므로 함께 조사해야 한다.

**근거**

- auth-service http post /login 지연 트레이스 31,822ms x20건 (2026-08-17T13:58:49.599758Z~13:59:26.432117Z, TEMPO 시각 정확) — 문의 시각(한국시간 23시 전후)과 일치
- auth-service ERROR/WARN 91건 (13:56~13:57Z), x7회 60초 간격으로 14:03Z까지 반복 — 지연 트레이스보다 약 3분 먼저 시작
- 원인 예외 java.sql.SQLTransientConnectionException 55건 + org.hibernate.exception.JDBCConnectionException 55건 (13:58~13:59Z), 각 x9회 60초 간격으로 14:07Z까지 — HikariCP 커넥션 획득 실패/풀 고갈 지문
- Tempo 에러 검색 '{ status = error }' 0건 — 5xx가 아니라 커넥션 대기로 응답만 지연되는 장애임을 뒷받침 (에러 0건을 정상 근거로 쓰지 않음)
- up / mongodb_up / kafka_brokers / kafka_consumergroup_lag / websocket_active_users 모두 수집되고 이상 0건 — 파드 소실·Kafka·WebSocket 계열 원인은 배제되고 auth-service의 RDB 경로로 좁혀짐

**스윕이 찾은 트레이스** (고른 것은 6a8313998ffffec2a18e55e626e90016)

| traceId | 채널 | root service | root span | ms |
|---|---|---|---|---:|
| `6a83139d32114d538c0983e015ac06fd` | slow | auth-service | http post /login | 32433 |
| `6a83139da8966f81abf12c62445d2692` | slow | auth-service | http post /login | 32096 |
| `6a83139dc8a58c92473e4d9e1c04f142` | slow | auth-service | http post /login | 30286 |
| `6a83139d4afbee98edb1f0ee0862278d` | slow | auth-service | http post /login | 31415 |
| `6a83139c8467f75dad7fffc2f2db9929` | slow | auth-service | http post /login | 32586 |
| `6a83139c095c239146100e8005555fcd` | slow | auth-service | http post /login | 30191 |
| `6a83139c2ff262f6e236d870a1575a3d` | slow | auth-service | http post /login | 32038 |
| `6a83139c07eb0c8b8a5a3d54a7f6283d` | slow | auth-service | http post /login | 31487 |
| `6a83139cc53fecb5a59ec9ddd4e8bef8` | slow | auth-service | http post /login | 31488 |
| `6a83139b7172e6aa7cd0d98baa38eca0` | slow | auth-service | http post /login | 32225 |
| `6a83139bc643c27da967435e2333f585` | slow | auth-service | http post /login | 30211 |
| `6a83139b323e140917bdcadcf598040a` | slow | auth-service | http post /login | 31780 |
| `6a83139b0b6568484427ce2f7a70fc30` | slow | auth-service | http post /login | 32000 |
| `6a83139b089fb0a239c9601c26408014` | slow | auth-service | http post /login | 30787 |
| `6a83139b85f991ec1df1c5ae611c7dba` | slow | auth-service | http post /login | 31906 |
| `6a83139a2093750007d02d52210791dc` | slow | auth-service | http post /login | 32204 |
| `6a83139a60ac003aab6922e6c9f20c0e` | slow | auth-service | http post /login | 32225 |
| `6a83139a9c4a8e999b2948813a670236` | slow | auth-service | http post /login | 31382 |
| `6a8313999759b3bfdaaee9723089a99a` | slow | auth-service | http post /login | 32406 |
| `6a8313998ffffec2a18e55e626e90016` ←선택 | slow | auth-service | http post /login | 31822 |

**장애 후보** (코드가 신호를 묶은 것 · 창은 여기서 계산됨)

## INC-1  auth-service  |  ERROR/WARN
- 구간: 2026-08-17T13:56:00Z ~ 2026-08-17T14:07:00Z  (LOKI · 집계 해상도만큼 흐림)
- ERROR/WARN 91건 (2026-08-17T13:56:00Z ~ 2026-08-17T13:57:00Z)  [x7회 · 2026-08-17T13:56:00Z~2026-08-17T14:03:00Z · 평균 60초 간격]
- 원인 예외 java.sql.SQLTransientConnectionException 55건 (2026-08-17T13:58:00Z ~ 2026-08-17T13:59:00Z)  [x9회 · 2026-08-17T13:58:00Z~2026-08-17T14:07:00Z · 평균 60초 간격]
- 원인 예외 org.hibernate.exception.JDBCConnectionException 55건 (2026-08-17T13:58:00Z ~ 2026-08-17T13:59:00Z)  [x9회 · 2026-08-17T13:58:00Z~2026-08-17T14:07:00Z · 평균 60초 간격]
- 같은 시각의 다른 후보: INC-2, INC-3  (인과 여부는 판단하지 않았다)

## INC-2  auth-service  |  http post /login
- 구간: 2026-08-17T13:58:49.599758Z ~ 2026-08-17T13:59:26.432117Z  (TEMPO · 시각 정확)
- auth-service http post /login 31,822ms (slow 채널)  [x20회 · 2026-08-17T13:58:49.599758Z~2026-08-17T13:59:26.432117Z · 평균 0초 간격]
- traceId: 6a8313998ffffec2a18e55e626e90016, 6a8313999759b3bfdaaee9723089a99a (+18건)
- 같은 시각의 다른 후보: INC-1  (인과 여부는 판단하지 않았다)

## INC-3  content-service  |  ERROR/WARN
- 구간: 2026-08-17T14:01:00Z ~ 2026-08-17T14:06:00Z  (LOKI · 집계 해상도만큼 흐림)
- ERROR/WARN 2건 (2026-08-17T14:01:00Z ~ 2026-08-17T14:02:00Z)
- 예외 java.util.concurrent.TimeoutException 1건 (2026-08-17T14:01:00Z ~ 2026-08-17T14:02:00Z)  [x5회 · 2026-08-17T14:01:00Z~2026-08-17T14:06:00Z · 평균 60초 간격]
- 같은 시각의 다른 후보: INC-1  (인과 여부는 판단하지 않았다)

**기각한 후보**

- INC-3 — content-service의 TimeoutException 1~2건 규모로 로그인 경로와 무관하고 14:01Z 이후 시작되어 /login 지연(13:58Z)보다 늦으므로, auth-service 장애의 하류 파급일 가능성은 있으나 로그인 문의의 직접 원인 후보로는 우선순위가 낮다.

**보정 기록**

- 창을 후보 [INC-1, INC-2] 의 신호 시각에서 계산했다 (2026-08-17T13:56:00Z ~ 2026-08-17T14:07:00Z)

**스윕 무신호/실패**

- Tempo 에러 검색 '{ status = error }'이 이 창에서 0건이다. 트레이스가 생성되지 않는 장애(컨슈머 전멸·파드 부재)이거나 에러가 아닌 형태의 장애(200 성공 + 지연)일 수 있으니 이 사실 자체를 근거로 쓸 것.

## 수집 범위 (Coverage)

- **window**: 2026-08-17T13:56:00Z ~ 2026-08-17T14:07:00Z (660s)
- **trace**: 127,540B / 84 spans (상위 span만)
- **창 안 후보 트레이스**: 21건 / 127,540B
- **logs**: errwarn=239,795B · traceId=14,673B
- **metrics**: 8 수집 / 104,940B, 누락 [sum(rate(http_server_requests_seconds_count{application="content-service", status="401"}[1m]))]
- **context**: 265,041 chars (+ 시스템 프롬프트 779 chars)

### 토큰 축 (개선 지표)

| 단계 | 총 in | chars | 컨텍스트 토큰 |
|---|---:|---:|---:|
| 탐색 | 25,047 | 3,752 | 3,446 |
| 분석 | 170,757 | 265,820 | 149,156 |
| **합계** | **195,804** | | **152,602** |

- **overheadTokens 21,601 tok — 이 회차에 실측**(1자 프롬프트 프로브, 같은 명령·모델·샌드박스).
  호출마다 한 번씩 얹히므로 단계별로 뺀다. **다른 날 상수를 쓰지 않았다 = `█ 실측`.**
- contextTokens (count_tokens API): 측정 안 됨 — 구독 CLI 경로엔 API 키가 없다
- 비용은 **API 환산 추정치**다. 구독 계정으로 호출하므로 실제 청구액이 아니다.

## 수집 실패/누락

- Metric 'sum(rate(http_server_requests_seconds_count{application="content-service", status="401"}[1m]))' returned no series in this window; skipped.

---

## 1. 원인 후보 랭킹 (최대 3개)

1. **auth-service의 HikariCP 커넥션 풀 고갈** — 활성 10개 포화 + 대기 190건, 30초 획득 타임아웃 만료로 로그인 지연·500 발생 (근접 원인, 사실상 확정)
2. **로그인 요청 동시성 급증이 풀·스레드 용량을 초과** (1번을 유발한 상위 원인 후보)
3. **커넥션 1건당 점유 시간(쿼리/트랜잭션 소요) 증가** (1번을 유발한 대안 상위 원인 후보 — 데이터 부족)

---

## 2. 후보별 근거

### 후보 1. auth-service HikariCP 커넥션 풀 고갈

- **근거**
  - 메트릭 `hikaricp_connections_active{application="auth-service", pod=auth-service-5999bb9f5c-zrvgl}`: 13:57:15까지 0 → **13:58:30 이후 계속 10** → 14:03:30부터 0. 정체 구간 내내 상수 10이라 **풀 상한이 10으로 추정**됨(실제 설정값 `hikaricp_connections_max`는 미수집).
  - 같은 pod의 `hikaricp_connections_pending`: **최대 190 (13:59:30)**, 13:58:30=135 · 14:01:00=188 · 14:02:00=190 · 14:03:15=166 → 14:03:30 이후 0. 커넥션을 못 받고 대기 중인 스레드가 190개.
  - 로그 원문: `o.h.e.jdbc.spi.SqlExceptionHelper - HikariPool-1 - Connection is not available, request timed out after 30000ms.` → `org.springframework.transaction.CannotCreateTransactionException: Could not open JPA EntityManager for transaction` → `Caused by: java.sql.SQLTransientConnectionException: HikariPool-1 - Connection is not available, request timed out after 30000ms.`
  - 스택 최상단 앱 코드: `com.example.toyauth.app.auth.controller.AuthController.login(AuthController.java:45)`, 그 바로 위가 `CglibAopProxy$DynamicAdvisedInterceptor.intercept` → 트랜잭션 프록시 진입 시점에 커넥션 획득 실패.
  - 트레이스 21건 중 창 내 20건이 전부 `http post /login`, `durNs` 30.2~32.6초. 소요의 거의 전부가 자식 span `secured request`(예: `31,817,405,000ns` / 전체 `31,822,652,000ns`)에 몰려 있고, `security filterchain before/after`는 0.09~76ms에 불과 → 지연은 필터체인이 아니라 컨트롤러/트랜잭션 구간.
  - 정상 비교군: 창 직전 트레이스 `6a8313125113bbc0444801b0ed95e323`(13:56:34)는 동일 `POST /login`이 **147,848,000ns(147ms)**. 즉 같은 엔드포인트가 147ms → 30,000ms+로 200배 악화.
  - 스레드 이름이 `http-nio-8081-exec-2`에서 `exec-148`까지 증가 → Tomcat 워커가 DB 대기로 전부 묶임.
- **확신도: 높음**
- **대기·지연 구간 판정**
  - *커넥션 획득 대기(상한 = HikariCP `connectionTimeout` 30000ms, 예외 메시지에서 직접 확인)*
    - **만료된 건**: 실측 대기 `30000ms`(traceId 6a83135bfe4e71bf…, 6a83135b582134ed…, 6a83135ff894b5aa…), `30001ms`(6a83139bc643c27d…), `30002ms`(6a83139c095c2391…), `30079ms`(6a83139dc8a58c92…) → **모두 상한 도달·만료**. 최종 상태: **실패**. 로그 `RequestLoggingFilter - [HTTP] POST /api/login 500 - 30210ms / 30191ms / 30202ms / 30322ms / 30123ms`로 **HTTP 500 응답 확정**. 재시도 span·로그는 없음 → 서버 측 재시도 **없음(폐기)**.
    - **만료 안 된 건**: 나머지 17건은 총 30.8~32.6초에 `status=200 / outcome=SUCCESS`(예: `[HTTP-SLOW] POST /api/login 200 - 32406ms`) → 30초 만료 전에 커넥션을 획득해 **최종 상태 성공(200)**. 다만 "총 소요"만 있을 뿐 **대기 구간만의 실측치를 분리할 span/로그가 없어, 각 건의 순수 대기 시간 자체는 판정 불가**.
  - *Tomcat 수용 큐 대기 / 클라이언트(브라우저·게이트웨이) 타임아웃*: 상한 설정값이 데이터에 없음 → **판정 불가**.
- **반증 데이터**: 없음. (보강 반증 배제: `up{job=auth-service}`는 전 구간 1로 파드 재시작·다운 아님, `rate(jvm_gc_pause_seconds_sum[5m])` 최대 0.0161 = **CPU 시간의 약 1.6%**로 GC 정지는 30초 지연을 설명하지 못함.)

### 후보 2. 로그인 요청 동시성 급증이 용량을 초과

- **근거**
  - Tomcat 워커 스레드 번호가 `exec-2`(13:56:39) → `exec-77`(13:58:17) → `exec-147/148`(13:59:21~24)로 단조 증가 → 동시 처리 중인 요청 수가 계속 늘어남.
  - 지연이 **1093ms(13:56:39) → 2~4초(13:56:5x) → 5~10초(13:57:1x~3x) → 20~30초(13:58:1x)** 로 톱니 없이 단조 상승 후, 14:03:30 이후 `pending`이 0으로 복귀하며 자연 소멸 → 외부 개입 없이 유입이 줄자 해소되는 **대기행렬 포화의 전형적 형태**.
  - 배포/재시작 흔적 없음: 전 구간 동일 pod `auth-service-5999bb9f5c-zrvgl`, `up`=1 상수 → 코드 변경보다 부하 변화로 설명하는 편이 자연스러움.
  - auth-service는 관측된 인스턴스가 **1개**(content-service는 `…-sp24n`, `…-v2pw9` 2개) → 수평 여유가 없음.
- **확신도: 중간** (요청량 지표 `http_server_requests_seconds_count{application="auth-service"}`를 수집하지 못해, 유입 증가를 직접 계측한 값이 없음. 스레드 번호·큐 길이라는 간접 증거뿐)
- **대기·지연 구간 판정**: 위 후보 1의 판정과 동일(30초 상한, 3건 만료→500 실패 / 17건 미만료→200 성공). 유입 자체의 상한(레이트리밋·큐 용량)은 데이터 없음 → **판정 불가**.
- **반증 데이터**
  - 대기 190건이 유입 급증만으로 생기려면 커넥션 회전이 정상이어야 하는데, 대기 190·대기시간 30초(Little의 법칙: 처리율 ≈ 190/30 ≈ 6.3 req/s, 커넥션 10개 기준 **1건당 점유 ≈ 1.6초**)는 평시 요청 전체가 147ms였던 것과 맞지 않음 → 순수 유입 증가만으로는 설명이 부족하고 후보 3이 함께 작용했을 가능성을 시사.

### 후보 3. 커넥션 1건당 점유 시간(쿼리/트랜잭션 소요) 증가

- **근거**
  - 위 반증에서 도출된 추정 점유 시간 **약 1.6초/건** vs 평시 요청 전체 **147ms** → 커넥션을 잡고 있는 시간이 10배 이상으로 보임.
  - `secured request` span이 전체 소요의 99% 이상을 차지하고 그 아래 DB/외부호출 자식 span이 **하나도 없음**(트레이스 고지: "span 4개와 속성은 하나도 빠지지 않았다") → 지연이 계측되지 않은 구간(JDBC 대기/실행)에 숨어 있음.
- **확신도: 낮음** (전부 간접 추정. DB 서버 지표, `hikaricp_connections_usage_seconds`/`acquire` 히스토그램, 슬로우 쿼리 로그 중 **어느 것도 수집되지 않음** → **데이터 부족**)
- **대기·지연 구간 판정**: 쿼리 실행 시간의 실측치와 상한(`statement_timeout` 등) 모두 데이터에 없음 → **판정 불가**.
- **반증 데이터**
  - auth-service의 DB가 어떤 엔진인지, 그 부하 지표가 데이터에 전혀 없음. 다만 같은 클러스터의 `mongodb_up`=1, `kafka_brokers`=1, `redis`/`node-infra` `up`=1로 **공용 인프라 전반의 장애 징후는 없음** → "DB 서버 전체 다운"류 원인은 배제됨.
  - content-service(2개 파드)와 chat-service의 `hikaricp_connections_active`/`pending`이 **전 구간 0** → 공용 DB의 전역 성능 저하였다면 다른 서비스에도 흔적이 남아야 하나 없음. 단, 이들 서비스는 창 내내 DB를 거의 쓰지 않아(활성 0) **민감도가 낮은 반증**임.

### 데이터 공백 (결론 확신도 하향 요인)

- 조사 지시문이 참조하는 **"호출 그래프" 절이 실제 데이터에 존재하지 않음**. 수집된 21개 트레이스의 모든 span은 `service.name=auth-service` 단일 서비스이며, auth→content/chat 등 서비스 간 호출 span은 **한 건도 관측되지 않음**. 따라서 서비스 간 전파는 전제하지 않았다.
- 수집 실패: `content-service`의 401 카운터 시리즈 없음 → content-service 연루 여부를 이 지표로는 확인 불가(단, content-service의 커넥션 지표가 전 구간 0이므로 결론에 미치는 영향은 작음).
- 창 시작 시점(13:56:00)의 **첫 로그가 이미 `[HTTP-SLOW] 1093ms`** → 장애 시작 시각이 조회창보다 앞설 수 있어, 발단(트리거)은 이 데이터로 확정 불가.

---

## 3. 권장 다음 조치

### 이미 발생한 피해: 복구 가능한가

- **부분 복구 가능**.
  - **데이터 정합성 피해는 없음**: 실패 경로가 `CannotCreateTransactionException: Could not open JPA EntityManager for transaction`, 즉 **트랜잭션을 열기도 전에** 실패했다. 부분 커밋·중간 상태 쓰기가 발생할 수 없다. 별도 데이터 정정 작업 불필요.
  - **500을 받은 로그인 요청 자체는 서버 측에서 되돌릴 수 없음**(응답이 이미 반환됨). 로그인은 재실행해도 부작용이 없으므로 **사용자 재시도로 완전 복구**된다. 현재 `pending`/`active`가 0이므로 지금 재시도하면 성공한다.
  - **200이지만 30초 이상 걸린 17건**은 클라이언트가 먼저 끊었다면 사용자 체감상 실패했을 수 있다. 클라이언트/게이트웨이 타임아웃 설정값이 데이터에 없어 **실제 사용자 도달 여부는 판정 불가** → 게이트웨이 액세스 로그로 확인 필요.
  - 영향 범위: `POST /api/login` 한정, pod `auth-service-5999bb9f5c-zrvgl`, 대략 13:56:39~14:03:30 UTC(22:56~23:03 KST). 문의 시각과 일치.

### 재발 방지

- **먼저 계측 공백을 메울 것** (지금 근본 원인을 후보 2/3으로 좁힐 수 없는 이유가 이것):
  - `http_server_requests_seconds_count{application="auth-service", uri="/login"}` 요청량·상태코드 rate
  - `hikaricp_connections_max` / `min_idle` / `timeout_total` / `acquire`·`usage` 히스토그램 (커넥션 점유 시간을 직접 측정 → 후보 2 vs 3 판별)
  - auth DB 서버 지표(연결 수, 슬로우 쿼리, CPU) — **현재 이 DB에 대한 지표가 전무함**
  - auth 파드 CPU/스로틀링, `tomcat_threads_busy`/`max`
  - JDBC span 계측 활성화(현재 `secured request` 아래에 DB span이 없어 30초의 소재를 트레이스로 특정할 수 없음)
- **용량·격리 조치**:
  - 풀 상한(추정 10) 재산정 및 auth-service 수평 확장(현재 인스턴스 1개, content-service는 2개).
  - `POST /api/login`에 동시성 제한/부하 차단(bulkhead·큐 상한)을 두어, 초과분은 30초 매달리지 말고 **즉시 503으로 빠르게 실패**시키기. 지금은 190개 요청이 30초씩 Tomcat 스레드를 점유해 장애를 증폭시켰다.
  - 커넥션 획득 타임아웃 30초는 로그인 UX 대비 과도 — 수 초 수준으로 낮추는 것을 검토.
  - `AuthController.login(AuthController.java:45)`의 트랜잭션 경계 점검: 트랜잭션 안에 불필요한 I/O가 포함돼 있으면 커넥션 점유 시간이 그만큼 늘어난다.
- **알람**: `hikaricp_connections_pending > 0`이 1분 이상 지속, `hikaricp_connections_active == max` 지속, `/api/login` p99 > 1s. 이번 건은 `pending`이 6분간 100 이상이었는데 사용자 문의로 인지됐다.

### 복구 확인

- **이미 확인된 회복 신호**: `hikaricp_connections_pending`이 14:03:30~14:07:00 전 구간 0, `hikaricp_connections_active`도 같은 구간 0. 트레이스·에러 로그도 13:59:26 이후 끊김. `up{job=auth-service}`는 전 구간 1로 파드 교체 없이 자연 회복.
- **추가로 확인할 것**:
  - 합성 로그인 요청 1건을 실행해 응답시간이 평시(≈150ms)로 돌아왔는지 확인.
  - 14:07 이후 현재까지 `[HTTP-SLOW] POST /api/login`, `HikariPool-1 - Connection is not available` 로그가 0건인지 재조회(이번 창은 14:07에서 끊겨 있음).
  - `/api/login`의 5xx 비율과 p95/p99 지연을 24시간 관찰 — 유입 패턴(후보 2)이 원인이라면 같은 시간대에 재발할 수 있다.

---

## 관측 증거 (Evidence)

> 이 조사가 실제로 본 값이다. 원본 응답 전체는 `reports/raw/scan-1786974960-*.json`에 있다.

### span (duration 상위 15 / 전체 84)

| ms | service | span | 시작 |
|---:|---|---|---|
| 32586.11 | auth-service | `http post /login` | 2026-08-17T13:58:52.915763Z |
| 32505.24 | auth-service | `secured request` | 2026-08-17T13:58:52.916241Z |
| 32433.70 | auth-service | `http post /login` | 2026-08-17T13:58:53.999117Z |
| 32423.20 | auth-service | `secured request` | 2026-08-17T13:58:54.007821Z |
| 32406.54 | auth-service | `http post /login` | 2026-08-17T13:58:49.804324Z |
| 32405.68 | auth-service | `secured request` | 2026-08-17T13:58:49.804793Z |
| 32225.53 | auth-service | `http post /login` | 2026-08-17T13:58:50.500033Z |
| 32225.48 | auth-service | `http post /login` | 2026-08-17T13:58:51.899194Z |
| 32218.92 | auth-service | `secured request` | 2026-08-17T13:58:50.505929Z |
| 32210.72 | auth-service | `secured request` | 2026-08-17T13:58:51.906828Z |
| 32204.29 | auth-service | `http post /login` | 2026-08-17T13:58:50.717965Z |
| 32203.58 | auth-service | `secured request` | 2026-08-17T13:58:50.718363Z |
| 32096.49 | auth-service | `http post /login` | 2026-08-17T13:58:53.816146Z |
| 32090.64 | auth-service | `secured request` | 2026-08-17T13:58:53.821500Z |
| 32038.94 | auth-service | `http post /login` | 2026-08-17T13:58:52.368874Z |

### 로그 원문 (60 / 전체 1,029줄)

전체가 상한을 넘어 ERROR/WARN 줄을 우선 발췌했다. 나머지는 원본 파일에 있다.

```
2026-08-17T13:56:39.215750020Z  [auth-service]  [2m2026-08-17 22:56:39[0;39m [2m[http-nio-8081-exec-2][0;39m [33m WARN [traceId=6a8313160f0cfead7285ebc7e30e0016,spanId=7285ebc7e30e0016,userId=NONE][0;39m [36mc.e.t.a.c.f.RequestLoggingFilter[0;39m [2m-[0;39m [HTTP-SLOW] POST /api/login 200 - 1093ms
2026-08-17T13:56:39.301203797Z  [auth-service]  [2m2026-08-17 22:56:39[0;39m [2m[http-nio-8081-exec-4][0;39m [33m WARN [traceId=6a831316ba856c8b39f1ef80f6c648d8,spanId=39f1ef80f6c648d8,userId=NONE][0;39m [36mc.e.t.a.c.f.RequestLoggingFilter[0;39m [2m-[0;39m [HTTP-SLOW] POST /api/login 200 - 1002ms
2026-08-17T13:56:40.610724568Z  [auth-service]  [2m2026-08-17 22:56:40[0;39m [2m[http-nio-8081-exec-8][0;39m [33m WARN [traceId=6a83131735b2993fb36104d56f159d2c,spanId=b36104d56f159d2c,userId=NONE][0;39m [36mc.e.t.a.c.f.RequestLoggingFilter[0;39m [2m-[0;39m [HTTP-SLOW] POST /api/login 200 - 1311ms
2026-08-17T13:56:40.617782545Z  [auth-service]  [2m2026-08-17 22:56:40[0;39m [2m[http-nio-8081-exec-10][0;39m [33m WARN [traceId=6a8313170f291c9eaec86fbd7c109855,spanId=aec86fbd7c109855,userId=NONE][0;39m [36mc.e.t.a.c.f.RequestLoggingFilter[0;39m [2m-[0;39m [HTTP-SLOW] POST /api/login 200 - 1297ms
2026-08-17T13:56:40.701513921Z  [auth-service]  [2m2026-08-17 22:56:40[0;39m [2m[http-nio-8081-exec-5][0;39m [33m WARN [traceId=6a831317163c22087fb877460f367c7a,spanId=7fb877460f367c7a,userId=NONE][0;39m [36mc.e.t.a.c.f.RequestLoggingFilter[0;39m [2m-[0;39m [HTTP-SLOW] POST /api/login 200 - 1301ms
2026-08-17T13:56:40.713768704Z  [auth-service]  [2m2026-08-17 22:56:40[0;39m [2m[http-nio-8081-exec-9][0;39m [33m WARN [traceId=6a8313178ad4dbbbd2ac0d39169bd841,spanId=d2ac0d39169bd841,userId=NONE][0;39m [36mc.e.t.a.c.f.RequestLoggingFilter[0;39m [2m-[0;39m [HTTP-SLOW] POST /api/login 200 - 1198ms
2026-08-17T13:56:41.513890623Z  [auth-service]  [2m2026-08-17 22:56:41[0;39m [2m[http-nio-8081-exec-11][0;39m [33m WARN [traceId=6a831318787cc1146d14616661e39cdf,spanId=6d14616661e39cdf,userId=NONE][0;39m [36mc.e.t.a.c.f.RequestLoggingFilter[0;39m [2m-[0;39m [HTTP-SLOW] POST /api/login 200 - 1215ms
2026-08-17T13:56:41.900851147Z  [auth-service]  [2m2026-08-17 22:56:41[0;39m [2m[http-nio-8081-exec-6][0;39m [33m WARN [traceId=6a831318c21ea491dbda0d9f7bbad10d,spanId=dbda0d9f7bbad10d,userId=NONE][0;39m [36mc.e.t.a.c.f.RequestLoggingFilter[0;39m [2m-[0;39m [HTTP-SLOW] POST /api/login 200 - 1183ms
2026-08-17T13:56:42.214755860Z  [auth-service]  [2m2026-08-17 22:56:42[0;39m [2m[http-nio-8081-exec-3][0;39m [33m WARN [traceId=6a831318a8270caeffee111c48128a3f,spanId=ffee111c48128a3f,userId=NONE][0;39m [36mc.e.t.a.c.f.RequestLoggingFilter[0;39m [2m-[0;39m [HTTP-SLOW] POST /api/login 200 - 1514ms
2026-08-17T13:56:42.401788288Z  [auth-service]  [2m2026-08-17 22:56:42[0;39m [2m[http-nio-8081-exec-1][0;39m [33m WARN [traceId=6a831318d8257df2b5e953599e21d911,spanId=b5e953599e21d911,userId=NONE][0;39m [36mc.e.t.a.c.f.RequestLoggingFilter[0;39m [2m-[0;39m [HTTP-SLOW] POST /api/login 200 - 1600ms
2026-08-17T13:56:43.014704058Z  [auth-service]  [2m2026-08-17 22:56:43[0;39m [2m[http-nio-8081-exec-8][0;39m [33m WARN [traceId=6a831319a60d9eb63cee2133bb183048,spanId=3cee2133bb183048,userId=NONE][0;39m [36mc.e.t.a.c.f.RequestLoggingFilter[0;39m [2m-[0;39m [HTTP-SLOW] POST /api/login 200 - 1502ms
2026-08-17T13:56:43.116839421Z  [auth-service]  [2m2026-08-17 22:56:43[0;39m [2m[http-nio-8081-exec-5][0;39m [33m WARN [traceId=6a8313196544382e610d949af0e0738e,spanId=610d949af0e0738e,userId=NONE][0;39m [36mc.e.t.a.c.f.RequestLoggingFilter[0;39m [2m-[0;39m [HTTP-SLOW] POST /api/login 200 - 1516ms
2026-08-17T13:56:43.303586968Z  [auth-service]  [2m2026-08-17 22:56:43[0;39m [2m[http-nio-8081-exec-9][0;39m [33m WARN [traceId=6a831319fab5c8965229f2e042146f6e,spanId=5229f2e042146f6e,userId=NONE][0;39m [36mc.e.t.a.c.f.RequestLoggingFilter[0;39m [2m-[0;39m [HTTP-SLOW] POST /api/login 200 - 1501ms
2026-08-17T13:56:43.320801526Z  [auth-service]  [2m2026-08-17 22:56:43[0;39m [2m[http-nio-8081-exec-2][0;39m [33m WARN [traceId=6a8313191515a3be38b7da3ca119e7a3,spanId=38b7da3ca119e7a3,userId=NONE][0;39m [36mc.e.t.a.c.f.RequestLoggingFilter[0;39m [2m-[0;39m [HTTP-SLOW] POST /api/login 200 - 1406ms
2026-08-17T13:56:43.616995325Z  [auth-service]  [2m2026-08-17 22:56:43[0;39m [2m[http-nio-8081-exec-11][0;39m [33m WARN [traceId=6a83131a349247a32542c6535d4cbb27,spanId=2542c6535d4cbb27,userId=NONE][0;39m [36mc.e.t.a.c.f.RequestLoggingFilter[0;39m [2m-[0;39m [HTTP-SLOW] POST /api/login 200 - 1317ms
2026-08-17T13:56:43.624299733Z  [auth-service]  [2m2026-08-17 22:56:43[0;39m [2m[http-nio-8081-exec-4][0;39m [33m WARN [traceId=6a83131afa761235f81e87243e61538e,spanId=f81e87243e61538e,userId=NONE][0;39m [36mc.e.t.a.c.f.RequestLoggingFilter[0;39m [2m-[0;39m [HTTP-SLOW] POST /api/login 200 - 1424ms
2026-08-17T13:56:43.769382194Z  [auth-service]  [2m2026-08-17 22:56:43[0;39m [2m[http-nio-8081-exec-6][0;39m [33m WARN [traceId=6a83131ace217756e2c8bac4940abd0d,spanId=e2c8bac4940abd0d,userId=NONE][0;39m [36mc.e.t.a.c.f.RequestLoggingFilter[0;39m [2m-[0;39m [HTTP-SLOW] POST /api/login 200 - 1349ms
2026-08-17T13:56:44.501252819Z  [auth-service]  [2m2026-08-17 22:56:44[0;39m [2m[http-nio-8081-exec-5][0;39m [33m WARN [traceId=6a83131bbed0c5bb6e4dab67b966c10a,spanId=6e4dab67b966c10a,userId=NONE][0;39m [36mc.e.t.a.c.f.RequestLoggingFilter[0;39m [2m-[0;39m [HTTP-SLOW] POST /api/login 200 - 1102ms
2026-08-17T13:56:44.769779615Z  [auth-service]  [2m2026-08-17 22:56:44[0;39m [2m[http-nio-8081-exec-2][0;39m [33m WARN [traceId=6a83131bd2718ef9f0a7924cd5c6c5c4,spanId=f0a7924cd5c6c5c4,userId=NONE][0;39m [36mc.e.t.a.c.f.RequestLoggingFilter[0;39m [2m-[0;39m [HTTP-SLOW] POST /api/login 200 - 1067ms
2026-08-17T13:56:44.810803047Z  [auth-service]  [2m2026-08-17 22:56:44[0;39m [2m[http-nio-8081-exec-11][0;39m [33m WARN [traceId=6a83131b6a83338bf5083945f9281c6d,spanId=f5083945f9281c6d,userId=NONE][0;39m [36mc.e.t.a.c.f.RequestLoggingFilter[0;39m [2m-[0;39m [HTTP-SLOW] POST /api/login 200 - 1012ms
2026-08-17T13:56:45.104244782Z  [auth-service]  [2m2026-08-17 22:56:45[0;39m [2m[http-nio-8081-exec-8][0;39m [33m WARN [traceId=6a83131bab466651fbe41ccf538859ed,spanId=fbe41ccf538859ed,userId=NONE][0;39m [36mc.e.t.a.c.f.RequestLoggingFilter[0;39m [2m-[0;39m [HTTP-SLOW] POST /api/login 200 - 2089ms
2026-08-17T13:56:45.209975965Z  [auth-service]  [2m2026-08-17 22:56:45[0;39m [2m[http-nio-8081-exec-1][0;39m [33m WARN [traceId=6a83131b87fc73639fdbb57cd5b7acbd,spanId=9fdbb57cd5b7acbd,userId=NONE][0;39m [36mc.e.t.a.c.f.RequestLoggingFilter[0;39m [2m-[0;39m [HTTP-SLOW] POST /api/login 200 - 2040ms
2026-08-17T13:56:45.211878843Z  [auth-service]  [2m2026-08-17 22:56:45[0;39m [2m[http-nio-8081-exec-3][0;39m [33m WARN [traceId=6a83131b5a311dd441989ae522b85cff,spanId=41989ae522b85cff,userId=NONE][0;39m [36mc.e.t.a.c.f.RequestLoggingFilter[0;39m [2m-[0;39m [HTTP-SLOW] POST /api/login 200 - 2109ms
2026-08-17T13:56:45.517880006Z  [auth-service]  [2m2026-08-17 22:56:45[0;39m [2m[http-nio-8081-exec-10][0;39m [33m WARN [traceId=6a83131b3a54b944d8d63580165848c1,spanId=d8d63580165848c1,userId=NONE][0;39m [36mc.e.t.a.c.f.RequestLoggingFilter[0;39m [2m-[0;39m [HTTP-SLOW] POST /api/login 200 - 2200ms
2026-08-17T13:56:45.840196309Z  [auth-service]  [2m2026-08-17 22:56:45[0;39m [2m[http-nio-8081-exec-9][0;39m [33m WARN [traceId=6a83131bd4c2d8333b67052419130c49,spanId=3b67052419130c49,userId=NONE][0;39m [36mc.e.t.a.c.f.RequestLoggingFilter[0;39m [2m-[0;39m [HTTP-SLOW] POST /api/login 200 - 2139ms
2026-08-17T13:56:46.402310479Z  [auth-service]  [2m2026-08-17 22:56:46[0;39m [2m[http-nio-8081-exec-6][0;39m [33m WARN [traceId=6a83131c013ca972ce4c4de87c20850e,spanId=ce4c4de87c20850e,userId=NONE][0;39m [36mc.e.t.a.c.f.RequestLoggingFilter[0;39m [2m-[0;39m [HTTP-SLOW] POST /api/login 200 - 1890ms
2026-08-17T13:56:46.469775779Z  [auth-service]  [2m2026-08-17 22:56:46[0;39m [2m[http-nio-8081-exec-4][0;39m [33m WARN [traceId=6a83131cb2f36470e14f4eb33a799904,spanId=e14f4eb33a799904,userId=NONE][0;39m [36mc.e.t.a.c.f.RequestLoggingFilter[0;39m [2m-[0;39m [HTTP-SLOW] POST /api/login 200 - 2169ms
2026-08-17T13:56:46.601729922Z  [auth-service]  [2m2026-08-17 22:56:46[0;39m [2m[http-nio-8081-exec-5][0;39m [33m WARN [traceId=6a83131cb277510ae13eb792f2a5c5db,spanId=e13eb792f2a5c5db,userId=NONE][0;39m [36mc.e.t.a.c.f.RequestLoggingFilter[0;39m [2m-[0;39m [HTTP-SLOW] POST /api/login 200 - 1789ms
2026-08-17T13:56:46.616465618Z  [auth-service]  [2m2026-08-17 22:56:46[0;39m [2m[http-nio-8081-exec-2][0;39m [33m WARN [traceId=6a83131cc1d83470ff6923c5c03bc6fb,spanId=ff6923c5c03bc6fb,userId=NONE][0;39m [36mc.e.t.a.c.f.RequestLoggingFilter[0;39m [2m-[0;39m [HTTP-SLOW] POST /api/login 200 - 1717ms
2026-08-17T13:58:17.010786679Z  [auth-service]  [2m2026-08-17 22:58:17[0;39m [2m[http-nio-8081-exec-77][0;39m [31mERROR [traceId=6a83135bfe4e71bf4ac9cabe0d37e646,spanId=09965ed483aefe92,userId=NONE][0;39m [36mo.h.e.jdbc.spi.SqlExceptionHelper[0;39m [2m-[0;39m HikariPool-1 - Connection is not available, request timed out after 30000ms.
2026-08-17T13:58:17.312492209Z  [auth-service]  [2m2026-08-17 22:58:17[0;39m [2m[http-nio-8081-exec-77][0;39m [31mERROR [traceId=6a83135bfe4e71bf4ac9cabe0d37e646,spanId=09965ed483aefe92,userId=NONE][0;39m [36mc.e.t.a.c.e.GlobalExceptionHandler[0;39m [2m-[0;39m [api-error] handleAllException
2026-08-17T13:58:17.312516964Z  [auth-service]  org.springframework.transaction.CannotCreateTransactionException: Could not open JPA EntityManager for transaction
2026-08-17T13:58:17.312743832Z  [auth-service]  at org.springframework.security.web.access.ExceptionTranslationFilter.doFilter(ExceptionTranslationFilter.java:126)
2026-08-17T13:58:17.312746320Z  [auth-service]  at org.springframework.security.web.access.ExceptionTranslationFilter.doFilter(ExceptionTranslationFilter.java:120)
2026-08-17T13:58:17.313832780Z  [auth-service]  Caused by: org.hibernate.exception.JDBCConnectionException: Unable to acquire JDBC Connection [HikariPool-1 - Connection is not available, request timed out after 30000ms.] [n/a]
2026-08-17T13:58:17.313835876Z  [auth-service]  at org.hibernate.exception.internal.SQLExceptionTypeDelegate.convert(SQLExceptionTypeDelegate.java:51)
2026-08-17T13:58:17.313838180Z  [auth-service]  at org.hibernate.exception.internal.StandardSQLExceptionConverter.convert(StandardSQLExceptionConverter.java:58)
2026-08-17T13:58:17.313840367Z  [auth-service]  at org.hibernate.engine.jdbc.spi.SqlExceptionHelper.convert(SqlExceptionHelper.java:108)
2026-08-17T13:58:17.313842824Z  [auth-service]  at org.hibernate.engine.jdbc.spi.SqlExceptionHelper.convert(SqlExceptionHelper.java:94)
2026-08-17T13:58:17.313888335Z  [auth-service]  Caused by: java.sql.SQLTransientConnectionException: HikariPool-1 - Connection is not available, request timed out after 30000ms.
2026-08-17T13:58:17.313890810Z  [auth-service]  at com.zaxxer.hikari.pool.HikariPool.createTimeoutException(HikariPool.java:696)
2026-08-17T13:58:17.322991216Z  [auth-service]  [2m2026-08-17 22:58:17[0;39m [2m[http-nio-8081-exec-77][0;39m [31mERROR [traceId=6a83135bfe4e71bf4ac9cabe0d37e646,spanId=4ac9cabe0d37e646,userId=NONE][0;39m [36mc.e.t.a.c.f.RequestLoggingFilter[0;39m [2m-[0;39m [HTTP] POST /api/login 500 - 30322ms
2026-08-17T13:58:17.407332767Z  [auth-service]  [2m2026-08-17 22:58:17[0;39m [2m[http-nio-8081-exec-56][0;39m [31mERROR [traceId=6a83135b582134ed19e77c716322b2e2,spanId=50cf00c11220e6a0,userId=NONE][0;39m [36mo.h.e.jdbc.spi.SqlExceptionHelper[0;39m [2m-[0;39m HikariPool-1 - Connection is not available, request timed out after 30000ms.
2026-08-17T13:58:17.512812059Z  [auth-service]  [2m2026-08-17 22:58:17[0;39m [2m[http-nio-8081-exec-56][0;39m [31mERROR [traceId=6a83135b582134ed19e77c716322b2e2,spanId=50cf00c11220e6a0,userId=NONE][0;39m [36mc.e.t.a.c.e.GlobalExceptionHandler[0;39m [2m-[0;39m [api-error] handleAllException
2026-08-17T13:58:17.512834039Z  [auth-service]  org.springframework.transaction.CannotCreateTransactionException: Could not open JPA EntityManager for transaction
2026-08-17T13:58:17.513012979Z  [auth-service]  at org.springframework.security.web.access.ExceptionTranslationFilter.doFilter(ExceptionTranslationFilter.java:126)
2026-08-17T13:58:17.513015233Z  [auth-service]  at org.springframework.security.web.access.ExceptionTranslationFilter.doFilter(ExceptionTranslationFilter.java:120)
2026-08-17T13:58:17.513410540Z  [auth-service]  Caused by: org.hibernate.exception.JDBCConnectionException: Unable to acquire JDBC Connection [HikariPool-1 - Connection is not available, request timed out after 30000ms.] [n/a]
2026-08-17T13:58:17.513415655Z  [auth-service]  at org.hibernate.exception.internal.SQLExceptionTypeDelegate.convert(SQLExceptionTypeDelegate.java:51)
2026-08-17T13:58:17.513418040Z  [auth-service]  at org.hibernate.exception.internal.StandardSQLExceptionConverter.convert(StandardSQLExceptionConverter.java:58)
2026-08-17T13:58:17.513420654Z  [auth-service]  at org.hibernate.engine.jdbc.spi.SqlExceptionHelper.convert(SqlExceptionHelper.java:108)
2026-08-17T13:58:17.513423158Z  [auth-service]  at org.hibernate.engine.jdbc.spi.SqlExceptionHelper.convert(SqlExceptionHelper.java:94)
2026-08-17T13:58:17.513451594Z  [auth-service]  Caused by: java.sql.SQLTransientConnectionException: HikariPool-1 - Connection is not available, request timed out after 30000ms.
2026-08-17T13:58:17.513453900Z  [auth-service]  at com.zaxxer.hikari.pool.HikariPool.createTimeoutException(HikariPool.java:696)
2026-08-17T13:58:17.523155828Z  [auth-service]  [2m2026-08-17 22:58:17[0;39m [2m[http-nio-8081-exec-56][0;39m [31mERROR [traceId=6a83135b582134ed19e77c716322b2e2,spanId=19e77c716322b2e2,userId=NONE][0;39m [36mc.e.t.a.c.f.RequestLoggingFilter[0;39m [2m-[0;39m [HTTP] POST /api/login 500 - 30123ms
2026-08-17T13:58:21.207321685Z  [auth-service]  [2m2026-08-17 22:58:21[0;39m [2m[http-nio-8081-exec-16][0;39m [31mERROR [traceId=6a83135ff894b5aa03f2aa437057fe27,spanId=7be04a9612355f5e,userId=NONE][0;39m [36mo.h.e.jdbc.spi.SqlExceptionHelper[0;39m [2m-[0;39m HikariPool-1 - Connection is not available, request timed out after 30000ms.
2026-08-17T13:58:21.308357250Z  [auth-service]  [2m2026-08-17 22:58:21[0;39m [2m[http-nio-8081-exec-16][0;39m [31mERROR [traceId=6a83135ff894b5aa03f2aa437057fe27,spanId=7be04a9612355f5e,userId=NONE][0;39m [36mc.e.t.a.c.e.GlobalExceptionHandler[0;39m [2m-[0;39m [api-error] handleAllException
2026-08-17T13:58:21.308382532Z  [auth-service]  org.springframework.transaction.CannotCreateTransactionException: Could not open JPA EntityManager for transaction
2026-08-17T13:58:21.308538337Z  [auth-service]  at org.springframework.security.web.access.ExceptionTranslationFilter.doFilter(ExceptionTranslationFilter.java:126)
2026-08-17T13:58:21.308540812Z  [auth-service]  at org.springframework.security.web.access.ExceptionTranslationFilter.doFilter(ExceptionTranslationFilter.java:120)
```

### 메트릭 시계열

| 쿼리 | series | 점 | min | max | last | 값이 0이던 구간 |
|---|---|---:|---:|---:|---:|---|
| `hikaricp_connections_active` | `{__name__=hikaricp_connections_active, application=auth-service, cluster=yogurtte-k3s-prod, container=auth-service, instance=10.42.1.57:8090, job=auth-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=auth-service-5999bb9f5c-zrvgl, pool=HikariPool-1, service=auth-service}` | 45 | 0 | 10 | 0 | **2026-08-17T13:56:00Z ~ 2026-08-17T13:57:15Z, 2026-08-17T14:03:30Z ~ 2026-08-17T14:07:00Z** |
| `hikaricp_connections_active` | `{__name__=hikaricp_connections_active, application=chat-service, cluster=yogurtte-k3s-prod, container=chat-service, instance=10.42.1.55:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-fdcc7c776-kh2dl, pool=HikariPool-1}` | 45 | 0 | 0 | 0 | **2026-08-17T13:56:00Z ~ 2026-08-17T14:07:00Z** |
| `hikaricp_connections_active` | `{__name__=hikaricp_connections_active, application=content-service, cluster=yogurtte-k3s-prod, container=content-service, instance=10.42.1.53:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-85f648fcff-sp24n, pool=HikariPool-1}` | 45 | 0 | 0 | 0 | **2026-08-17T13:56:00Z ~ 2026-08-17T14:07:00Z** |
| `hikaricp_connections_active` | `{__name__=hikaricp_connections_active, application=content-service, cluster=yogurtte-k3s-prod, container=content-service, instance=10.42.3.46:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-85f648fcff-v2pw9, pool=HikariPool-1}` | 45 | 0 | 0 | 0 | **2026-08-17T13:56:00Z ~ 2026-08-17T14:07:00Z** |
| `hikaricp_connections_pending` | `{__name__=hikaricp_connections_pending, application=auth-service, cluster=yogurtte-k3s-prod, container=auth-service, instance=10.42.1.57:8090, job=auth-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=auth-service-5999bb9f5c-zrvgl, pool=HikariPool-1, service=auth-service}` | 45 | 0 | 190 | 0 | **2026-08-17T13:56:00Z ~ 2026-08-17T13:57:15Z, 2026-08-17T14:03:30Z ~ 2026-08-17T14:07:00Z** |
| `hikaricp_connections_pending` | `{__name__=hikaricp_connections_pending, application=chat-service, cluster=yogurtte-k3s-prod, container=chat-service, instance=10.42.1.55:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-fdcc7c776-kh2dl, pool=HikariPool-1}` | 45 | 0 | 0 | 0 | **2026-08-17T13:56:00Z ~ 2026-08-17T14:07:00Z** |
| `hikaricp_connections_pending` | `{__name__=hikaricp_connections_pending, application=content-service, cluster=yogurtte-k3s-prod, container=content-service, instance=10.42.1.53:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-85f648fcff-sp24n, pool=HikariPool-1}` | 45 | 0 | 0 | 0 | **2026-08-17T13:56:00Z ~ 2026-08-17T14:07:00Z** |
| `hikaricp_connections_pending` | `{__name__=hikaricp_connections_pending, application=content-service, cluster=yogurtte-k3s-prod, container=content-service, instance=10.42.3.46:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-85f648fcff-v2pw9, pool=HikariPool-1}` | 45 | 0 | 0 | 0 | **2026-08-17T13:56:00Z ~ 2026-08-17T14:07:00Z** |
| `rate(jvm_gc_pause_seconds_sum[5m])` | `{action=end of major GC, application=chat-service, cause=Allocation Failure, cluster=yogurtte-k3s-prod, container=chat-service, gc=MarkSweepCompact, instance=10.42.1.55:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-fdcc7c776-kh2dl}` | 45 | 0 | 0 | 0 | **2026-08-17T13:56:00Z ~ 2026-08-17T14:07:00Z** |
| `rate(jvm_gc_pause_seconds_sum[5m])` | `{action=end of minor GC, application=auth-service, cause=G1 Evacuation Pause, cluster=yogurtte-k3s-prod, container=auth-service, gc=G1 Young Generation, instance=10.42.1.57:8090, job=auth-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=auth-service-5999bb9f5c-zrvgl, service=auth-service}` | 45 | 0 | 0.016 | 0.002 | **2026-08-17T13:56:00Z ~ 2026-08-17T13:57:15Z** |
| `rate(jvm_gc_pause_seconds_sum[5m])` | `{action=end of minor GC, application=chat-service, cause=Allocation Failure, cluster=yogurtte-k3s-prod, container=chat-service, gc=Copy, instance=10.42.1.55:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-fdcc7c776-kh2dl}` | 45 | 0.000 | 0.000 | 0.000 | — |
| `rate(jvm_gc_pause_seconds_sum[5m])` | `{action=end of minor GC, application=chat-service, cause=GCLocker Initiated GC, cluster=yogurtte-k3s-prod, container=chat-service, gc=Copy, instance=10.42.1.55:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-fdcc7c776-kh2dl}` | 45 | 0 | 0 | 0 | **2026-08-17T13:56:00Z ~ 2026-08-17T14:07:00Z** |
| `rate(jvm_gc_pause_seconds_sum[5m])` | `{action=end of minor GC, application=content-service, cause=Allocation Failure, cluster=yogurtte-k3s-prod, container=content-service, gc=Copy, instance=10.42.1.53:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-85f648fcff-sp24n}` | 45 | 0 | 0.000 | 0 | **2026-08-17T13:56:00Z ~ 2026-08-17T14:01:30Z, 2026-08-17T14:05:45Z ~ 2026-08-17T14:07:00Z** |
| `rate(jvm_gc_pause_seconds_sum[5m])` | `{action=end of minor GC, application=content-service, cause=Allocation Failure, cluster=yogurtte-k3s-prod, container=content-service, gc=Copy, instance=10.42.3.46:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-85f648fcff-v2pw9}` | 45 | 0 | 0.000 | 0 | **2026-08-17T13:56:00Z ~ 2026-08-17T13:59:45Z, 2026-08-17T14:04:00Z ~ 2026-08-17T14:07:00Z** |
| `rate(jvm_gc_pause_seconds_sum[5m])` | `{action=end of minor GC, application=content-service, cause=GCLocker Initiated GC, cluster=yogurtte-k3s-prod, container=content-service, gc=Copy, instance=10.42.1.53:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-85f648fcff-sp24n}` | 45 | 0 | 0 | 0 | **2026-08-17T13:56:00Z ~ 2026-08-17T14:07:00Z** |
| `rate(jvm_gc_pause_seconds_sum[5m])` | `{action=end of minor GC, application=content-service, cause=GCLocker Initiated GC, cluster=yogurtte-k3s-prod, container=content-service, gc=Copy, instance=10.42.3.46:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-85f648fcff-v2pw9}` | 45 | 0 | 0 | 0 | **2026-08-17T13:56:00Z ~ 2026-08-17T14:07:00Z** |
| `up` | `{__name__=up, app=node-exporter, cluster=yogurtte-k3s-prod, component=metrics, container=node-exporter, instance=ip-172-31-40-241, job=integrations/node_exporter, k8s_cluster_name=yogurtte-k3s-prod, namespace=monitoring, pod=grafana-k8s-monitoring-node-exporter-cph5l, source=kubernetes, workload=DaemonSet/grafana-k8s-monitoring-node-exporter}` | 45 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, app=node-exporter, cluster=yogurtte-k3s-prod, component=metrics, container=node-exporter, instance=ip-172-31-45-39, job=integrations/node_exporter, k8s_cluster_name=yogurtte-k3s-prod, namespace=monitoring, pod=grafana-k8s-monitoring-node-exporter-9kvqt, source=kubernetes, workload=DaemonSet/grafana-k8s-monitoring-node-exporter}` | 45 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, cluster=yogurtte-k3s-prod, container=auth-service, instance=10.42.1.57:8090, job=auth-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=auth-service-5999bb9f5c-zrvgl}` | 45 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, cluster=yogurtte-k3s-prod, container=chat-service, instance=10.42.1.55:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-fdcc7c776-kh2dl}` | 45 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, cluster=yogurtte-k3s-prod, container=content-service, instance=10.42.1.53:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-85f648fcff-sp24n}` | 45 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, cluster=yogurtte-k3s-prod, container=content-service, instance=10.42.3.46:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-85f648fcff-v2pw9}` | 45 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, cluster=yogurtte-k3s-prod, instance=10.42.1.248:8080, job=integrations/kubernetes/kube-state-metrics, k8s_cluster_name=yogurtte-k3s-prod, source=kubernetes}` | 45 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, cluster=yogurtte-k3s-prod, instance=62bd8b254df94616e43279f35eed72d3, job=integrations/cloudwatch, k8s_cluster_name=yogurtte-k3s-prod}` | 45 | 1 | 1 | 1 | — |
| `mongodb_up` | `{__name__=mongodb_up, cluster=yogurtte-k3s-prod, instance=infra-server, job=mongodb, k8s_cluster_name=yogurtte-k3s-prod}` | 45 | 1 | 1 | 1 | — |
| `kafka_brokers` | `{__name__=kafka_brokers, cluster=yogurtte-k3s-prod, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod}` | 45 | 1 | 1 | 1 | — |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=chat-service-fcm-tokens, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=0, topic=user.fcm-tokens}` | 45 | 0 | 0 | 0 | **2026-08-17T13:56:00Z ~ 2026-08-17T14:07:00Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=chat-service-fcm-tokens, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=1, topic=user.fcm-tokens}` | 45 | 0 | 0 | 0 | **2026-08-17T13:56:00Z ~ 2026-08-17T14:07:00Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=chat-service-fcm-tokens, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=2, topic=user.fcm-tokens}` | 45 | 0 | 0 | 0 | **2026-08-17T13:56:00Z ~ 2026-08-17T14:07:00Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=chat-service-notification-settings, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=0, topic=user.notification-settings}` | 45 | 0 | 0 | 0 | **2026-08-17T13:56:00Z ~ 2026-08-17T14:07:00Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=chat-service-notification-settings, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=1, topic=user.notification-settings}` | 45 | 0 | 0 | 0 | **2026-08-17T13:56:00Z ~ 2026-08-17T14:07:00Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=chat-service-notification-settings, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=2, topic=user.notification-settings}` | 45 | 0 | 0 | 0 | **2026-08-17T13:56:00Z ~ 2026-08-17T14:07:00Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=db-writer, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=0, topic=chat.messages}` | 45 | 0 | 0 | 0 | **2026-08-17T13:56:00Z ~ 2026-08-17T14:07:00Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=db-writer, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=1, topic=chat.messages}` | 45 | 0 | 0 | 0 | **2026-08-17T13:56:00Z ~ 2026-08-17T14:07:00Z** |
| `websocket_active_users` | `{__name__=websocket_active_users, application=chat-service, cluster=yogurtte-k3s-prod, container=chat-service, instance=10.42.1.55:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-fdcc7c776-kh2dl}` | 45 | 0 | 0 | 0 | **2026-08-17T13:56:00Z ~ 2026-08-17T14:07:00Z** |

값이 0이던 구간은 굵게 표시했다 — 프로세스가 사라져 시계열이 꺾인 것이 유일한 신호인 장애가 있다.

