# RCA Report — `scan-1785913383`

| 항목 | 값 |
|---|---|
| 모드 | rca |
| 질문 | 최근 1시간 안에 댓글 알림이 안 왔다는 제보가 있어요. 확인해줘 |
| 시각 | 2026-08-05T07:23:41.997055100Z |
| provider | claude-cli |
| model | `claude-opus-5` · turns 1 |
| prompt | `.\prompts\system-prompt.md` |
| tokens | in 101591 (cacheRead 23,447 · cacheCreate 78,142) / out 9671 · cost $1.0349 |
| elapsed | total 164793ms (tempo 7342 · loki 867 · mimir 814 · assemble 339 · llm 155069) |

## 탐색 (Triage)

| 항목 | 값 |
|---|---|
| 시간창 해석 | 상대 표현 '최근 1시간' |
| 시간창 확신도 | EXACT |
| 스윕 창 | 2026-08-05T06:23:03.071546200Z ~ 2026-08-05T07:23:03.071546200Z |
| 좁힌 창 | 2026-08-05T07:03:03Z ~ 2026-08-05T07:23:03.071546200Z |
| 대상 | chat-service, content-service |
| traceId | 6a72e268f451ff89279cca9591fca91b |
| 트레이스 후보 | 25건 |
| 장애 후보 | 9건 · 선택 INC-5, INC-6, INC-7, INC-8, INC-9 |
| 계획 파싱 | 성공 |
| 스윕 컨텍스트 | **후보만 — 원본 제외 (B)** |
| prompt | `.\prompts\triage-prompt.md` |
| tokens | in 34076 / out 1607 · cost $0.1853 |
| chars | 컨텍스트 5,197 + 프롬프트 1,399 = **6,596** |
| elapsed | survey 3004ms · llm 35619ms |

**선정 이유**: 제보 증상(최근 1시간 내 댓글 알림 미수신)과 시각·경로가 맞는 것은 07:10~07:20 구간의 mongodb 다운과 그에 동반된 chat-service 30초 타임아웃 클러스터이며, 상류 content-service 로그까지 한 장애의 여러 지문으로 보아 함께 고른다.

**근거**

- mongodb_up 1→0, 07:13:03Z~07:18:03Z 동안 0 유지 후 복구 (min_over_time(mongodb_up[5m]) 이상 신호 3건)
- chat-service ERROR/WARN 07:10~07:15 22건, 07:15~07:20 34건 — 창 내 최대 로그 폭증
- chat-service 'security filterchain before' 30,006~30,015ms 13건 (07:12:40Z~07:16:41Z) — 30초 정각에 몰린 타임아웃형 지연, 에러가 아니라 지연 채널에만 걸림
- <root span not yet received> 30,005~30,010ms 7건 (07:13:30Z~07:16:10Z) — 루트 스팬 미수신 = 요청이 완결되지 못하고 끊긴 흔적
- content-service ERROR/WARN 4건 (07:10~07:15) — 댓글 생성 상류 측도 같은 창에 영향받았을 가능성
- Tempo 에러 검색은 5건(전부 06:40 구간)뿐 — 07:10대 장애는 에러 없이 느려지기만 한 형태
- up[5m], kafka_brokers, kafka_consumergroup_lag, websocket_active_users는 이상 0건 — 파드 다운·Kafka·WS 연결 문제는 배제됨

**스윕이 찾은 트레이스** (고른 것은 6a72e268f451ff89279cca9591fca91b)

| traceId | 채널 | root service | root span | ms |
|---|---|---|---|---:|
| `6a72db05cfae5ff3f4886ac97eb8fda2` | error | content-service | http get /products/admin/all | 11 |
| `6a72db04e5654a0aabae50c8bacae39e` | error | content-service | http get /products/admin/all | 10 |
| `6a72db03775a09766a26f9635711e5ae` | error | content-service | http get /products/admin/all | 12 |
| `6a72db020b537604d388f6f3369683c3` | error | content-service | http get /products/admin/all | 40 |
| `6a72daf99fd8104acb651bc32455b16e` | error | content-service | http get /products/admin/all | 54 |
| `6a72e33be15fb95014a761d0ff43a0a4` | slow | chat-service | security filterchain before | 30009 |
| `6a72e331b2098912815772496bb7a475` | slow | chat-service | security filterchain before | 30006 |
| `6a72e326c40e3e2087808df7e1fd0a52` | slow | chat-service | security filterchain before | 30008 |
| `6a72e31c65fdf38ab48df03946375255` | slow | <root span not yet received> | (없음) | 30006 |
| `6a72e3121a86c96044be900b238201ee` | slow | <root span not yet received> | (없음) | 30007 |
| `6a72e30830e4b455e6c172d83c606b09` | slow | <root span not yet received> | (없음) | 30006 |
| `6a72e2feb9deb8931237f12baf9e7ea0` | slow | chat-service | security filterchain before | 30006 |
| `6a72e2f480d43f18ec84c63abec43a49` | slow | chat-service | security filterchain before | 30015 |
| `6a72e2eaa29bcdb806a0537c0f51396e` | slow | chat-service | security filterchain before | 30006 |
| `6a72e2e0d3a0c67c8fa94c86578938bf` | slow | <root span not yet received> | (없음) | 30007 |
| `6a72e2d6e06f0c82cd59f8e32d863292` | slow | <root span not yet received> | (없음) | 30010 |
| `6a72e2cc5e3543aeaa68198c1551fe07` | slow | <root span not yet received> | (없음) | 30005 |
| `6a72e2c2ff18f4692010e54b90dac132` | slow | chat-service | security filterchain before | 30006 |
| `6a72e2b80c840a9b6ef56c59b9a1d5d7` | slow | chat-service | security filterchain before | 30006 |
| `6a72e2aece2ada80111614c2519dba03` | slow | chat-service | security filterchain before | 30009 |
| `6a72e2a4985a96e3c711fcaf353b619c` | slow | chat-service | security filterchain before | 30006 |
| `6a72e29a8b2c0fe2474c520c1e58704b` | slow | <root span not yet received> | (없음) | 30006 |
| `6a72e27cf19a1463acd9dbbc89ea6708` | slow | chat-service | security filterchain before | 30008 |
| `6a72e272956e10b90f663c036b2fbf9f` | slow | chat-service | security filterchain before | 30006 |
| `6a72e268f451ff89279cca9591fca91b` ←선택 | slow | chat-service | security filterchain before | 30012 |

**장애 후보** (코드가 신호를 묶은 것 · 창은 여기서 계산됨)

## INC-1  auth-service  |  ERROR/WARN
- 구간: 2026-08-05T06:40:00Z ~ 2026-08-05T06:45:00Z  (LOKI · 집계 해상도만큼 흐림)
- ERROR/WARN 4건 (2026-08-05T06:40:00Z ~ 2026-08-05T06:45:00Z)
- 같은 시각의 다른 후보: INC-2, INC-3, INC-4  (인과 여부는 판단하지 않았다)

## INC-2  chat-service  |  ERROR/WARN
- 구간: 2026-08-05T06:40:00Z ~ 2026-08-05T06:45:00Z  (LOKI · 집계 해상도만큼 흐림)
- ERROR/WARN 6건 (2026-08-05T06:40:00Z ~ 2026-08-05T06:45:00Z)
- 같은 시각의 다른 후보: INC-1, INC-3, INC-4  (인과 여부는 판단하지 않았다)

## INC-3  content-service  |  ERROR/WARN
- 구간: 2026-08-05T06:40:00Z ~ 2026-08-05T06:45:00Z  (LOKI · 집계 해상도만큼 흐림)
- ERROR/WARN 43건 (2026-08-05T06:40:00Z ~ 2026-08-05T06:45:00Z)
- 같은 시각의 다른 후보: INC-1, INC-2, INC-4  (인과 여부는 판단하지 않았다)

## INC-4  content-service  |  http get /products/admin/all
- 구간: 2026-08-05T06:40:57.187549Z ~ 2026-08-05T06:41:09.128227Z  (TEMPO · 시각 정확)
- content-service http get /products/admin/all 54ms (error 채널)
- content-service http get /products/admin/all 40ms (error 채널)
- content-service http get /products/admin/all 12ms (error 채널)
- content-service http get /products/admin/all 10ms (error 채널)
- content-service http get /products/admin/all 11ms (error 채널)
- traceId: 6a72daf99fd8104acb651bc32455b16e, 6a72db020b537604d388f6f3369683c3, 6a72db03775a09766a26f9635711e5ae, 6a72db04e5654a0aabae50c8bacae39e, 6a72db05cfae5ff3f4886ac97eb8fda2
- 같은 시각의 다른 후보: INC-1, INC-2, INC-3  (인과 여부는 판단하지 않았다)

## INC-5  mongodb  |  mongodb_up
- 구간: 2026-08-05T07:08:03Z ~ 2026-08-05T07:23:03Z  (MIMIR · 집계 해상도만큼 흐림)
- mongodb_up 1 → 0
- mongodb_up 가 0이었다 (2026-08-05T07:13:03Z ~ 2026-08-05T07:18:03Z)
- mongodb_up 0 → 1
- 같은 시각의 다른 후보: INC-6, INC-7, INC-8, INC-9  (인과 여부는 판단하지 않았다)

## INC-6  chat-service  |  ERROR/WARN
- 구간: 2026-08-05T07:10:00Z ~ 2026-08-05T07:20:00Z  (LOKI · 집계 해상도만큼 흐림)
- ERROR/WARN 22건 (2026-08-05T07:10:00Z ~ 2026-08-05T07:15:00Z)
- ERROR/WARN 34건 (2026-08-05T07:15:00Z ~ 2026-08-05T07:20:00Z)
- 같은 시각의 다른 후보: INC-5, INC-7, INC-8, INC-9  (인과 여부는 판단하지 않았다)

## INC-7  content-service  |  ERROR/WARN
- 구간: 2026-08-05T07:10:00Z ~ 2026-08-05T07:15:00Z  (LOKI · 집계 해상도만큼 흐림)
- ERROR/WARN 4건 (2026-08-05T07:10:00Z ~ 2026-08-05T07:15:00Z)
- 같은 시각의 다른 후보: INC-5, INC-6, INC-8, INC-9  (인과 여부는 판단하지 않았다)

## INC-8  chat-service  |  security filterchain before
- 구간: 2026-08-05T07:12:40.240522Z ~ 2026-08-05T07:16:41.091093Z  (TEMPO · 시각 정확)
- chat-service security filterchain before 30,012ms (slow 채널)
- chat-service security filterchain before 30,006ms (slow 채널)
- chat-service security filterchain before 30,008ms (slow 채널)
- chat-service security filterchain before 30,006ms (slow 채널)
- chat-service security filterchain before 30,009ms (slow 채널)
- chat-service security filterchain before 30,006ms (slow 채널)
- chat-service security filterchain before 30,006ms (slow 채널)
- chat-service security filterchain before 30,006ms (slow 채널)
- chat-service security filterchain before 30,015ms (slow 채널)
- chat-service security filterchain before 30,006ms (slow 채널)
- chat-service security filterchain before 30,008ms (slow 채널)
- chat-service security filterchain before 30,006ms (slow 채널)
- chat-service security filterchain before 30,009ms (slow 채널)
- traceId: 6a72e268f451ff89279cca9591fca91b, 6a72e272956e10b90f663c036b2fbf9f, 6a72e27cf19a1463acd9dbbc89ea6708, 6a72e2a4985a96e3c711fcaf353b619c, 6a72e2aece2ada80111614c2519dba03, 6a72e2b80c840a9b6ef56c59b9a1d5d7, 6a72e2c2ff18f4692010e54b90dac132, 6a72e2eaa29bcdb806a0537c0f51396e, 6a72e2f480d43f18ec84c63abec43a49, 6a72e2feb9deb8931237f12baf9e7ea0, 6a72e326c40e3e2087808df7e1fd0a52, 6a72e331b2098912815772496bb7a475, 6a72e33be15fb95014a761d0ff43a0a4
- 같은 시각의 다른 후보: INC-5, INC-6, INC-7, INC-9  (인과 여부는 판단하지 않았다)

## INC-9  <root span not yet received>
- 구간: 2026-08-05T07:13:30.452255Z ~ 2026-08-05T07:16:10.961097Z  (TEMPO · 시각 정확)
- <root span not yet received>  30,006ms (slow 채널)
- <root span not yet received>  30,005ms (slow 채널)
- <root span not yet received>  30,010ms (slow 채널)
- <root span not yet received>  30,007ms (slow 채널)
- <root span not yet received>  30,006ms (slow 채널)
- <root span not yet received>  30,007ms (slow 채널)
- <root span not yet received>  30,006ms (slow 채널)
- traceId: 6a72e29a8b2c0fe2474c520c1e58704b, 6a72e2cc5e3543aeaa68198c1551fe07, 6a72e2d6e06f0c82cd59f8e32d863292, 6a72e2e0d3a0c67c8fa94c86578938bf, 6a72e30830e4b455e6c172d83c606b09, 6a72e3121a86c96044be900b238201ee, 6a72e31c65fdf38ab48df03946375255
- 같은 시각의 다른 후보: INC-5, INC-6, INC-7, INC-8  (인과 여부는 판단하지 않았다)

**기각한 후보**

- INC-1 — 06:40~06:45 구간 auth-service ERROR/WARN 4건으로, 알림 경로와 무관하고 07:10대 증상 창과 시각이 어긋난다.
- INC-2 — chat-service이긴 하나 06:40 구간 6건에 그치고 지연 신호가 동반되지 않아, 30초 블로킹이 관측된 07:10대 지문과 별개다.
- INC-3 — 06:40 구간 content-service 43건은 아래 INC-4의 admin 엔드포인트 에러와 같은 시각·같은 리소스로 보이며, 댓글 알림 경로가 아니다.
- INC-4 — http get /products/admin/all 관리자 조회 에러 5건(10~54ms)으로 지연도 없고 알림 발행 경로와 무관하다.

**보정 기록**

- 창을 후보 [INC-5, INC-6, INC-7, INC-8, INC-9] 의 신호 시각에서 계산했다 (2026-08-05T07:03:03Z ~ 2026-08-05T07:23:03.071546200Z)

## 수집 범위 (Coverage)

- **window**: 2026-08-05T07:03:03Z ~ 2026-08-05T07:23:03.071546200Z (1200s)
- **trace**: 79,752B / 80 spans
- **창 안 후보 트레이스**: 20건 / 79,752B
- **logs**: errwarn=223,388B · traceId=34,680B
- **metrics**: 8 수집 / 139,517B, 누락 [sum(rate(http_server_requests_seconds_count{application="content-service", status="401"}[1m]))]
- **context**: 132,558 chars (+ 시스템 프롬프트 805 chars)

### 토큰 축 (개선 지표)

| 단계 | 총 in | chars | 컨텍스트 토큰 |
|---|---:|---:|---:|
| 탐색 | 34,076 | 6,596 | 측정 안 됨 |
| 분석 | 101,591 | 133,363 | 측정 안 됨 |
| **합계** | **135,667** | | **측정 안 됨** |

- ⚠ **overheadTokens 측정 안 됨** — 컨텍스트 토큰이 비어 있다. 이 회차 수치를 쓰려면
  `docs/round-1-input-tokens.md`의 다른 날 상수로 **추정**해야 하고, 그건 `▓ 추정`이다
  (그 상수는 하루 만에 20% 움직인 적이 있다).
- contextTokens (count_tokens API): 측정 안 됨 — 구독 CLI 경로엔 API 키가 없다
- 비용은 **API 환산 추정치**다. 구독 계정으로 호출하므로 실제 청구액이 아니다.

## 수집 실패/누락

- Metric 'sum(rate(http_server_requests_seconds_count{application="content-service", status="401"}[1m]))' returned no series in this window; skipped.

---

## 1. 원인 후보 랭킹 (최대 3개)

1. **content-service의 댓글 작성 API가 JWT 서명 검증 실패로 401 반환 → 댓글 자체가 생성되지 않아 알림 트리거가 발생하지 않음**
2. **MongoDB(172.31.46.124:27017) 다운(07:12:33~07:18:03) → chat-service의 Mongo 의존 경로 전면 실패**
3. **chat-service 관측 공백(07:13:48~07:18:03) — 원인 판정이 아니라, 알림 발송 경로 자체를 볼 수 없게 만든 데이터 결손**

---

## 2. 후보별 근거

### 후보 1 — 댓글 POST가 401로 거절됨

- **근거**
  - `content-service-5489b58cbc-r8r5j`, 16:12:20.155 KST(=07:12:20 UTC):
    `c.e.t.app.auth.token.JwtParser - [auth-jwt] JWT 서명 검증 실패 — 위조 또는 시크릿 불일치`
    이어서 `RequestLoggingFilter - [HTTP] POST /api/battles/22/items/125/comments 401 - 8ms`
  - `content-service-5489b58cbc-2ckp6`, 16:13:34.154:
    동일한 `JWT 서명 검증 실패` → `POST /api/battles/22/items/125/comments 401 - 20ms`
  - 실패한 엔드포인트가 **정확히 댓글 작성 경로**이고, content-service **두 파드 모두**에서 발생 → 특정 파드의 시크릿 로컬 오염이 아니라 토큰 측 또는 공유 시크릿 불일치 쪽.
  - 댓글이 201로 생성되지 않으면 그 뒤의 알림 생성·발행은 아예 시작되지 않는다. 관측 창 전체에서 `kafka_consumergroup_lag`이 `notification`(chat.messages), `notification-processors`(user.notifications), `notification-recovery`(user.notifications.dlq) 전부 **0**인 것과 정합한다 — 밀린 게 아니라 **애초에 들어온 게 없다**에 부합.
- **대기·지연 판정**: 해당 없음(대기 구간 아님). 요청은 8ms / 20ms에 **완결**되었고 타임아웃 만료가 아니다. 최종 상태 = **실패(401 응답 반환, 재시도 흔적 없음, 댓글 미생성 → 폐기)**.
- **확신도**: **중간**
  (엔드포인트 일치는 결정적이나, 관측된 사례가 2건뿐이고 `sum(rate(http_server_requests_seconds_count{application="content-service", status="401"}[1m]))`가 **시리즈 없음으로 수집 실패** → 401이 전체 댓글 요청의 몇 %인지, 제보자 요청이 이 중 하나인지 확인 불가.)
- **반증 데이터**
  - 401 로그 2건의 `traceId`(`6a72e254…`, `6a72e29e…`)는 수집된 트레이스 20건 어디에도 없어, 이 요청들의 스팬 전문으로 교차 확인이 안 된다.
  - `userId=NONE`이라 어느 사용자인지 특정 불가 — 제보자와 동일인이라는 근거 없음.
  - 같은 창에서 **성공한 댓글 POST 로그가 하나도 없다**. 이는 이 후보를 강화하는 게 아니라, 애초에 댓글 트래픽 로그 자체가 이 수집 범위에 거의 안 잡혔다는 뜻이므로 대표성에 한계가 있다.

---

### 후보 2 — MongoDB 다운

- **근거**
  - 07:12:33.070 UTC, chat-service:
    `com.mongodb.MongoNodeIsRecoveringException: Command failed with error 11600 (InterruptedAtShutdown): 'interrupted at shutdown' on server 172.31.46.124:27017` (x2)
  - 07:12:40.261부터 성격 전환:
    `com.mongodb.MongoSocketOpenException: Exception opening socket` → `Caused by: java.net.ConnectException: Connection refused: /172.31.46.124:27017`
    → **정상 종료 후 프로세스 부재**. 07:16:41.210까지 동일 예외가 10초 주기로 계속.
  - 메트릭 `mongodb_up`: 전 구간 1이다가 **07:12:48~07:18:03 = 0**, 이후 1로 복귀. 로그와 시각이 일치.
  - 반면 `up{job=mongodb}`(exporter 자체)는 전 구간 1 → 관측 파이프라인이 아니라 **mongod 인스턴스가 죽은 것**.
- **대기·지연 판정** (요구 항목)
  - **대기 상한**: 로그 `Waiting for server to become available for operation with ID 73405. Remaining time: 29999 ms` → 서버 선택 타임아웃 설정값 **30000ms**.
  - **실측 대기**: `Health contributor …(mongo) took 30001ms to respond` (최소 30001ms, 최대 30003ms), 트레이스의 `secured request` span `durNs` = **30,005,975,000 ~ 30,015,206,000 ns (30.006~30.015초)**.
  - **판정**: 실측 ≥ 상한 → **만료됨(expired)**.
  - **최종 상태**: **실패**. `org.springframework.dao.DataAccessResourceFailureException: Timed out while waiting for a server that matches ReadPreferenceServerSelector{readPreference=primary}` **x22회 (07:13:10.249~07:16:41.087)**, `Mongo health check failed` WARN **x23회 (07:13:10.249~07:16:51.129)**. 동일 작업에 대한 **재시도 없음** — 10초마다 오는 것은 새 헬스체크 요청이며, 만료된 작업은 예외로 **폐기**됨.
  - 참고로 JDBC 쪽은 정반대다: `connection` span 40회, 최대 5.0ms, 전부 `acquired` 이벤트로 종료, `hikaricp_connections_pending` 전 구간 0 → **만료 아님, 성공**. MySQL은 무관.
- **확신도**: **중간**
  (Mongo가 죽었다는 사실 자체는 **높음** — 로그·메트릭 이중 확인. 하지만 이것이 *댓글 알림 미수신*의 원인이라는 연결은 **중간 이하**다. 아래 반증 참조.)
- **반증 데이터**
  - **Mongo 타임아웃을 겪은 스팬은 전부 액추에이터 헬스체크다.** 수집된 트레이스 20건의 `traceId`가 `Health contributor …(mongo) took 30001ms` WARN의 `traceId`와 **1:1로 일치**하며(예: `6a72e268f451ff89279cca9591fca91b`, `6a72e2f480d43f18ec84c63abec43a49`), 스팬 구성도 `security filterchain before` → `secured request` → `connection`(HikariPool) 2개로 20건 모두 동일하다. **알림 발송·댓글 처리 스팬은 단 하나도 없다.**
  - `kafka_consumergroup_lag`이 `notification`, `notification-processors`, `chat-service-fcm-tokens`, DLQ까지 전 구간 0 — Mongo 장애 5분간 알림 컨슈머가 **밀린 흔적이 전혀 없다**. 장애가 알림 처리를 막았다면 lag 상승 또는 DLQ 유입이 보여야 한다.
  - `websocket_active_users`가 **장애 이전부터 전 구간 0** — Mongo와 무관하게 실시간 푸시 수신 대상이 애초에 0이었다.
  - 제보는 "최근 1시간", Mongo 장애는 **5분 15초**. 나머지 시간대는 설명하지 못한다.
  - `chat.messages` / `user.notifications.dlq` 일부 파티션(2, 6, 9, 10 / dlq 1)의 lag이 전 구간 **-1** = 커밋된 오프셋 없음. 해당 파티션이 소비되지 않는 상태일 가능성이 있으나, 이는 창 전체에서 상수라 이번 장애와 무관하며 단독 근거로는 **데이터 부족**.

---

### 후보 3 — chat-service 관측 공백 / 액추에이터 스레드 점유

- **근거**
  - chat-service(`10.42.1.47:8090`)의 `up`, `hikaricp_connections_active`, `hikaricp_connections_pending`, `websocket_active_users` 전부 **07:13:48~07:18:03 결측(샘플 없음)**, `rate(jvm_gc_pause_seconds_sum[5m])`은 07:16:48~07:19:03 결측. 다른 파드(auth 81점, content 81점)는 결측 없음 → **chat-service 파드 단독**.
  - 결측 시작(07:13:48)이 30초 헬스체크 타임아웃이 처음 만료된 시각(07:13:10)의 직후이고, 회복(07:18:03)이 `mongodb_up=1` 복귀와 정확히 같다.
  - 헬스체크가 10초 주기로 들어와 각 30초 블록 → 상시 3개 워커 점유. 점유 스레드가 `nio-8090-exec-1`~`exec-10` 전 범위에 걸쳐 로테이션하는 것이 로그에 그대로 남아 있다.
- **대기·지연 판정**: 상한 30000ms, 실측 30001~30003ms → **만료**, 최종 상태 **실패**(후보 2와 동일 작업). 스크레이프 요청 자체의 타임아웃 설정값은 데이터에 없어 결측 원인 판정은 **판정 불가**.
- **확신도**: **낮음** (사용자 영향의 원인이라기보다 결과 및 관측 손실)
- **반증 데이터**
  - `up`이 결측 구간 외에는 전 구간 1이고 파드 이름(`chat-service-fdcc7c776-xf4sv`)이 창 전체에서 동일 → **재시작·OOM·크래시 없음**.
  - GC: `end of major GC` 전 구간 0, minor GC 최대 6.29E-4초 → 메모리 압박 없음.
  - HikariPool active/pending 모두 0, JDBC 획득 최대 5.0ms → 커넥션 풀 고갈 없음.

---

### 데이터 부족 — 정직하게 말하면

**질문("댓글 알림이 안 왔다")에 직접 답할 데이터가 없다.** 구체적으로:

- 호출 그래프에 존재하는 엣지는 `chat-service --jdbc--> mysql/content` **단 하나**다. 댓글 → 알림 생성 → Kafka 발행 → FCM 발송으로 이어지는 경로가 트레이스에 **전혀 나타나지 않는다**. 그 경로가 실제로 어떻게 생겼는지 이 데이터로는 알 수 없다.
- 수집된 트레이스 20/20이 액추에이터 헬스체크다. 사용자 요청 트레이스는 0건.
- content-service 401 메트릭은 **수집 실패**했다.

이 공백 때문에 위 세 후보의 확신도를 모두 한 단계씩 낮춰 적었다.

**추가 수집이 필요한 것**:
1. `{job="chat-service"} |= "FCM"` / `|= "notification"` / `|= "push"` 로그 (전송 성공·실패 원문)
2. 알림 발송 span 이름 기준 TraceQL (`{name=~".*notif.*|.*fcm.*"}`) — 있는지 여부부터
3. content-service의 성공한 `POST /api/battles/*/comments` 201 로그 (401 대비 비율)
4. content-service ↔ auth-service 토큰 검증 트레이스 및 JWT 시크릿 배포 이력(파드 기동 시각 vs. Secret 변경 시각)
5. `kafka_consumergroup_current_offset` 추이 (lag 0이 "지연 없음"인지 "메시지 자체가 없음"인지 구분)
6. `chat.messages` / `user.notifications` 파티션 2·6·9·10의 컨슈머 할당 상태 (lag=-1 원인)

---

## 3. 권장 다음 조치

### 이미 발생한 피해 — 복구 가능한가

- **401로 거절된 댓글 2건 (07:12:20, 07:13:34, 둘 다 `battles/22/items/125`)**: **복구 불가**. 요청이 인증 필터에서 거절되어 서버에 아무것도 기록되지 않았다(`RequestLoggingFilter` 401 외 후속 로그 없음). 재현 데이터가 없으므로 서버 측 재처리 대상이 없고, **사용자가 다시 작성하는 수밖에 없다**. 해당 게시물 작성자에게 안내 필요.
- **Mongo 장애 구간(07:12:33~07:18:03)에 유실된 알림**: **판정 불가**. 이 구간에 실패한 것으로 확인된 Mongo 작업은 헬스체크뿐이고, 알림 경로가 Mongo를 쓰는지조차 관측 데이터로 확인되지 않는다. 위 추가 수집 1·2번을 먼저 확보한 뒤 판단할 것.
- **재처리 가능한 백로그**: **해당 없음**. `notification`, `notification-processors`, `notification-recovery`(DLQ) 모든 컨슈머 그룹 lag이 전 구간 0이라 재소비할 미처리 메시지가 큐에 남아 있지 않다. 즉 "밀린 걸 다시 돌려서 복구"하는 선택지는 없다.
- **관측 결측(07:13:48~07:18:03)**: **복구 불가**. 스크레이프되지 않은 시점의 값은 소급 생성되지 않는다. 이 4분 15초는 영구 사각지대로 남는다.

### 재발 방지

1. **JWT 시크릿 불일치 (후보 1)** — content-service 두 파드가 동시에 서명 검증에 실패했다. auth-service의 서명 키와 content-service의 검증 키가 같은 소스에서 오는지, 그리고 파드 기동 시각이 Secret 변경 시각보다 앞서지 않는지 확인. 위조 토큰이라면 401이 정상 동작이므로, **먼저 시크릿 불일치인지 위조인지부터 가려야 한다** — 현재 로그 메시지는 두 경우를 구분하지 못한다(`위조 또는 시크릿 불일치`). 로그에 key id를 남기도록 수정할 것.
2. **헬스체크가 30초를 통째로 블로킹하는 문제 (후보 2·3)** — Mongo 리액티브 헬스 인디케이터에 서버 선택 타임아웃(현재 30000ms)보다 짧은 헬스체크 전용 타임아웃을 걸어라. 의존 인프라 하나가 죽으면 액추에이터 엔드포인트가 30초씩 잠기고, 그 결과 **메트릭 스크레이프까지 4분 넘게 끊겼다** — 장애 중에 관측을 잃는 최악의 조합이다. 2~3초면 충분하다.
3. **MongoDB 단일 인스턴스** — `InterruptedAtShutdown` 직후 `Connection refused`가 이어졌고, topology가 `servers=[{address=172.31.46.124:27017}]` 단일 노드다. 페일오버 대상이 없다. 레플리카셋 구성 또는 최소한 종료 시 무중단 절차 필요.
4. **알림 경로 계측 부재** — 이번 조사의 최대 제약. 댓글 생성 → 알림 발행 → 발송에 명시적 span과 결과 로그(성공/실패/수신자 수)를 심어라. 지금은 "알림이 안 왔다"를 관측으로 확인하거나 반박할 방법이 없다.
5. **content-service 401 메트릭 결측** — 해당 시리즈가 아예 없다. `http_server_requests` 메트릭에 401이 기록되도록(인증 필터가 Micrometer 계측 앞단에서 응답을 종료시키고 있을 가능성) 확인할 것.

### 복구 확인

- **이미 확인된 것**: `mongodb_up`이 07:18:03부터 창 종료(07:23:03)까지 1, 그 이후 Mongo 예외 로그 없음(마지막 `Connection refused` 07:16:41, 마지막 `Mongo health check failed` 07:16:51). chat-service 메트릭도 07:18:03부터 재개, 파드 재시작 없음. **Mongo 및 chat-service 관측은 복구 완료**로 판정.
- **아직 확인 안 된 것 — 여기부터 실행할 것**:
  1. 지금 시점에 댓글 POST를 실제로 한 건 넣고 `POST /api/battles/*/comments`가 **201**을 받는지, JWT WARN이 재발하지 않는지 확인. 후보 1은 이걸 해야 닫힌다.
  2. 그 댓글에 대해 알림이 실제 발송되는지 end-to-end 확인 (수신 디바이스 또는 발송 로그).
  3. 07:23 이후 `kafka_consumergroup_lag{consumergroup=~"notification.*"}`과 `user.notifications.dlq` lag이 계속 0인지 — DLQ에 뒤늦게 유입되면 유실 알림이 있었다는 증거가 된다.
  4. `websocket_active_users`가 0에서 올라오는지. 창 전체에서 0인 것이 정상 상태인지 아니면 그 자체로 별개 문제인지 **현재 데이터로는 판정 불가**이며, 실사용자 접속 시 값이 오르는지로 갈린다.

---

## 관측 증거 (Evidence)

> 이 조사가 실제로 본 값이다. 원본 응답 전체는 `reports/raw/scan-1785913383-*.json`에 있다.

### 호출 그래프 (트레이스에서 추출)

```
chat-service --jdbc--> mysql/content (HikariPool-1)  40회  최대 5.0ms
    events: acquired
```

### span (duration 상위 15 / 전체 80)

| ms | service | span | 시작 |
|---:|---|---|---|
| 30015.21 | chat-service | `secured request` | 2026-08-05T07:15:00.795255Z |
| 30012.15 | chat-service | `secured request` | 2026-08-05T07:12:40.240790Z |
| 30010.47 | chat-service | `secured request` | 2026-08-05T07:14:30.704119Z |
| 30008.92 | chat-service | `secured request` | 2026-08-05T07:16:11.082321Z |
| 30008.78 | chat-service | `secured request` | 2026-08-05T07:13:50.535268Z |
| 30008.09 | chat-service | `secured request` | 2026-08-05T07:15:50.996957Z |
| 30007.83 | chat-service | `secured request` | 2026-08-05T07:13:00.328448Z |
| 30007.68 | chat-service | `secured request` | 2026-08-05T07:14:40.745025Z |
| 30007.05 | chat-service | `secured request` | 2026-08-05T07:15:30.915009Z |
| 30006.81 | chat-service | `secured request` | 2026-08-05T07:13:40.494032Z |
| 30006.73 | chat-service | `secured request` | 2026-08-05T07:15:40.955097Z |
| 30006.69 | chat-service | `secured request` | 2026-08-05T07:13:30.452255Z |
| 30006.67 | chat-service | `secured request` | 2026-08-05T07:15:20.872347Z |
| 30006.54 | chat-service | `secured request` | 2026-08-05T07:14:00.577282Z |
| 30006.53 | chat-service | `secured request` | 2026-08-05T07:12:50.282616Z |

### 로그 원문 (60 / 전체 1,040줄)

전체가 상한을 넘어 ERROR/WARN 줄을 우선 발췌했다. 나머지는 원본 파일에 있다.

```
2026-08-05T07:12:33.070923897Z  [chat-service]  [2m2026-08-05T16:12:33.069+09:00[0;39m [32m INFO [traceId=NONE,spanId=NONE,userId=NONE][0;39m [35m7[0;39m [2m--- [chat-service] [31.46.124:27017] [                                                 ] [0;39m[36morg.mongodb.driver.cluster              [0;39m [2m:[0;39m Exception in monitor thread while connecting to server 172.31.46.124:27017
2026-08-05T07:12:33.070957047Z  [chat-service]  com.mongodb.MongoNodeIsRecoveringException: Command failed with error 11600 (InterruptedAtShutdown): 'interrupted at shutdown' on server 172.31.46.124:27017. The full response is {"ok": 0.0, "errmsg": "interrupted at shutdown", "code": 11600, "codeName": "InterruptedAtShutdown"}
2026-08-05T07:12:33.070960081Z  [chat-service]  at com.mongodb.internal.connection.ProtocolHelper.createSpecialException(ProtocolHelper.java:264) ~[mongodb-driver-core-5.5.1.jar!/:na]
2026-08-05T07:12:33.070962609Z  [chat-service]  at com.mongodb.internal.connection.ProtocolHelper.getCommandFailureException(ProtocolHelper.java:206) ~[mongodb-driver-core-5.5.1.jar!/:na]
2026-08-05T07:12:33.071702481Z  [chat-service]  [2m2026-08-05T16:12:33.071+09:00[0;39m [32m INFO [traceId=NONE,spanId=NONE,userId=NONE][0;39m [35m7[0;39m [2m--- [chat-service] [31.46.124:27017] [                                                 ] [0;39m[36morg.mongodb.driver.cluster              [0;39m [2m:[0;39m Exception in monitor thread while connecting to server 172.31.46.124:27017
2026-08-05T07:12:33.071722961Z  [chat-service]  com.mongodb.MongoNodeIsRecoveringException: Command failed with error 11600 (InterruptedAtShutdown): 'interrupted at shutdown' on server 172.31.46.124:27017. The full response is {"ok": 0.0, "errmsg": "interrupted at shutdown", "code": 11600, "codeName": "InterruptedAtShutdown"}
2026-08-05T07:12:33.071767707Z  [chat-service]  at com.mongodb.internal.connection.ProtocolHelper.createSpecialException(ProtocolHelper.java:264) ~[mongodb-driver-core-5.5.1.jar!/:na]
2026-08-05T07:12:33.071770602Z  [chat-service]  at com.mongodb.internal.connection.ProtocolHelper.getCommandFailureException(ProtocolHelper.java:206) ~[mongodb-driver-core-5.5.1.jar!/:na]
2026-08-05T07:12:40.246806318Z  [chat-service]  [2m2026-08-05T16:12:40.246+09:00[0;39m [32m INFO [traceId=6a72e268f451ff89279cca9591fca91b,spanId=6002ea06ce77216b,userId=NONE][0;39m [35m7[0;39m [2m--- [chat-service] [nio-8090-exec-2] [6a72e268f451ff89279cca9591fca91b-6002ea06ce77216b] [0;39m[36morg.mongodb.driver.cluster              [0;39m [2m:[0;39m Waiting for server to become available for operation with ID 73405. Remaining time: 29999 ms. Selector: ReadPreferenceServerSelector{readPreference=primary}, topology description: {type=UNKNOWN, servers=[{address=172.31.46.124:27017, type=UNKNOWN, state=CONNECTING, exception={com.mongodb.MongoNodeIsRecoveringException: Command failed with error 11600 (InterruptedAtShutdown): 'interrupted at shutdown' on server 172.31.46.124:27017. The full response is {"ok": 0.0, "errmsg": "interrupted at shutdown", "code": 11600, "codeName": "InterruptedAtShutdown"}}}].
2026-08-05T07:12:40.261119332Z  [chat-service]  [2m2026-08-05T16:12:40.257+09:00[0;39m [32m INFO [traceId=NONE,spanId=NONE,userId=NONE][0;39m [35m7[0;39m [2m--- [chat-service] [31.46.124:27017] [                                                 ] [0;39m[36morg.mongodb.driver.cluster              [0;39m [2m:[0;39m Exception in monitor thread while connecting to server 172.31.46.124:27017
2026-08-05T07:12:40.261143520Z  [chat-service]  com.mongodb.MongoSocketOpenException: Exception opening socket
2026-08-05T07:12:40.261237938Z  [chat-service]  Caused by: io.netty.channel.AbstractChannel$AnnotatedConnectException: Connection refused: /172.31.46.124:27017
2026-08-05T07:12:40.261240478Z  [chat-service]  Caused by: java.net.ConnectException: Connection refused
2026-08-05T07:12:43.073424377Z  [chat-service]  [2m2026-08-05T16:12:43.072+09:00[0;39m [32m INFO [traceId=NONE,spanId=NONE,userId=NONE][0;39m [35m7[0;39m [2m--- [chat-service] [31.46.124:27017] [                                                 ] [0;39m[36morg.mongodb.driver.cluster              [0;39m [2m:[0;39m Exception in monitor thread while connecting to server 172.31.46.124:27017
2026-08-05T07:12:43.073461673Z  [chat-service]  com.mongodb.MongoSocketOpenException: Exception opening socket
2026-08-05T07:12:43.073537275Z  [chat-service]  Caused by: io.netty.channel.AbstractChannel$AnnotatedConnectException: Connection refused: /172.31.46.124:27017
2026-08-05T07:12:43.073538995Z  [chat-service]  Caused by: java.net.ConnectException: Connection refused
2026-08-05T07:12:50.286484395Z  [chat-service]  [2m2026-08-05T16:12:50.286+09:00[0;39m [32m INFO [traceId=6a72e272956e10b90f663c036b2fbf9f,spanId=d02bfc9213c278f2,userId=NONE][0;39m [35m7[0;39m [2m--- [chat-service] [nio-8090-exec-9] [6a72e272956e10b90f663c036b2fbf9f-d02bfc9213c278f2] [0;39m[36morg.mongodb.driver.cluster              [0;39m [2m:[0;39m Waiting for server to become available for operation with ID 73430. Remaining time: 29999 ms. Selector: ReadPreferenceServerSelector{readPreference=primary}, topology description: {type=UNKNOWN, servers=[{address=172.31.46.124:27017, type=UNKNOWN, state=CONNECTING, exception={com.mongodb.MongoSocketOpenException: Exception opening socket}, caused by {io.netty.channel.AbstractChannel$AnnotatedConnectException: Connection refused: /172.31.46.124:27017}, caused by {java.net.ConnectException: Connection refused}}].
2026-08-05T07:13:00.333565900Z  [chat-service]  [2m2026-08-05T16:13:00.333+09:00[0;39m [32m INFO [traceId=6a72e27cf19a1463acd9dbbc89ea6708,spanId=533744be67bce604,userId=NONE][0;39m [35m7[0;39m [2m--- [chat-service] [nio-8090-exec-4] [6a72e27cf19a1463acd9dbbc89ea6708-533744be67bce604] [0;39m[36morg.mongodb.driver.cluster              [0;39m [2m:[0;39m Waiting for server to become available for operation with ID 73454. Remaining time: 29999 ms. Selector: ReadPreferenceServerSelector{readPreference=primary}, topology description: {type=UNKNOWN, servers=[{address=172.31.46.124:27017, type=UNKNOWN, state=CONNECTING, exception={com.mongodb.MongoSocketOpenException: Exception opening socket}, caused by {io.netty.channel.AbstractChannel$AnnotatedConnectException: Connection refused: /172.31.46.124:27017}, caused by {java.net.ConnectException: Connection refused}}].
2026-08-05T07:13:10.249477276Z  [chat-service]  org.springframework.dao.DataAccessResourceFailureException: Timed out while waiting for a server that matches ReadPreferenceServerSelector{readPreference=primary}. Client view of cluster state is {type=UNKNOWN, servers=[{address=172.31.46.124:27017, type=UNKNOWN, state=CONNECTING, exception={com.mongodb.MongoSocketOpenException: Exception opening socket}, caused by {io.netty.channel.AbstractChannel$AnnotatedConnectException: Connection refused: /172.31.46.124:27017}, caused by {java.net.ConnectException: Connection refused}}]
2026-08-05T07:13:10.249480732Z  [chat-service]  at org.springframework.data.mongodb.core.MongoExceptionTranslator.doTranslateException(MongoExceptionTranslator.java:97) ~[spring-data-mongodb-4.5.1.jar!/:4.5.1]
2026-08-05T07:13:10.249483462Z  [chat-service]  at org.springframework.data.mongodb.core.MongoExceptionTranslator.translateExceptionIfPossible(MongoExceptionTranslator.java:74) ~[spring-data-mongodb-4.5.1.jar!/:4.5.1]
2026-08-05T07:13:10.249485946Z  [chat-service]  at org.springframework.data.mongodb.core.ReactiveMongoTemplate.potentiallyConvertRuntimeException(ReactiveMongoTemplate.java:2768) ~[spring-data-mongodb-4.5.1.jar!/:4.5.1]
2026-08-05T07:13:10.249488673Z  [chat-service]  at org.springframework.data.mongodb.core.ReactiveMongoTemplate.lambda$translateException$100(ReactiveMongoTemplate.java:2751) ~[spring-data-mongodb-4.5.1.jar!/:4.5.1]
2026-08-05T07:13:10.249606785Z  [chat-service]  Caused by: com.mongodb.MongoTimeoutException: Timed out while waiting for a server that matches ReadPreferenceServerSelector{readPreference=primary}. Client view of cluster state is {type=UNKNOWN, servers=[{address=172.31.46.124:27017, type=UNKNOWN, state=CONNECTING, exception={com.mongodb.MongoSocketOpenException: Exception opening socket}, caused by {io.netty.channel.AbstractChannel$AnnotatedConnectException: Connection refused: /172.31.46.124:27017}, caused by {java.net.ConnectException: Connection refused}}]
2026-08-05T07:13:10.249614214Z  [chat-service]  at com.mongodb.internal.connection.BaseCluster.logAndThrowTimeoutException(BaseCluster.java:427) ~[mongodb-driver-core-5.5.1.jar!/:na]
2026-08-05T07:13:10.372719467Z  [chat-service]  [2m2026-08-05T16:13:10.372+09:00[0;39m [32m INFO [traceId=6a72e2864bcd78aa7ef8957e84a0acc6,spanId=e257e2695800aee1,userId=NONE][0;39m [35m7[0;39m [2m--- [chat-service] [nio-8090-exec-1] [6a72e2864bcd78aa7ef8957e84a0acc6-e257e2695800aee1] [0;39m[36morg.mongodb.driver.cluster              [0;39m [2m:[0;39m Waiting for server to become available for operation with ID 73478. Remaining time: 29999 ms. Selector: ReadPreferenceServerSelector{readPreference=primary}, topology description: {type=UNKNOWN, servers=[{address=172.31.46.124:27017, type=UNKNOWN, state=CONNECTING, exception={com.mongodb.MongoSocketOpenException: Exception opening socket}, caused by {io.netty.channel.AbstractChannel$AnnotatedConnectException: Connection refused: /172.31.46.124:27017}, caused by {java.net.ConnectException: Connection refused}}].
2026-08-05T07:13:20.287845189Z  [chat-service]  org.springframework.dao.DataAccessResourceFailureException: Timed out while waiting for a server that matches ReadPreferenceServerSelector{readPreference=primary}. Client view of cluster state is {type=UNKNOWN, servers=[{address=172.31.46.124:27017, type=UNKNOWN, state=CONNECTING, exception={com.mongodb.MongoSocketOpenException: Exception opening socket}, caused by {io.netty.channel.AbstractChannel$AnnotatedConnectException: Connection refused: /172.31.46.124:27017}, caused by {java.net.ConnectException: Connection refused}}]
2026-08-05T07:13:20.287851113Z  [chat-service]  at org.springframework.data.mongodb.core.MongoExceptionTranslator.doTranslateException(MongoExceptionTranslator.java:97) ~[spring-data-mongodb-4.5.1.jar!/:4.5.1]
2026-08-05T07:13:20.287871791Z  [chat-service]  at org.springframework.data.mongodb.core.MongoExceptionTranslator.translateExceptionIfPossible(MongoExceptionTranslator.java:74) ~[spring-data-mongodb-4.5.1.jar!/:4.5.1]
2026-08-05T07:13:20.287875263Z  [chat-service]  at org.springframework.data.mongodb.core.ReactiveMongoTemplate.potentiallyConvertRuntimeException(ReactiveMongoTemplate.java:2768) ~[spring-data-mongodb-4.5.1.jar!/:4.5.1]
2026-08-05T07:13:20.287877844Z  [chat-service]  at org.springframework.data.mongodb.core.ReactiveMongoTemplate.lambda$translateException$100(ReactiveMongoTemplate.java:2751) ~[spring-data-mongodb-4.5.1.jar!/:4.5.1]
2026-08-05T07:13:20.287977942Z  [chat-service]  Caused by: com.mongodb.MongoTimeoutException: Timed out while waiting for a server that matches ReadPreferenceServerSelector{readPreference=primary}. Client view of cluster state is {type=UNKNOWN, servers=[{address=172.31.46.124:27017, type=UNKNOWN, state=CONNECTING, exception={com.mongodb.MongoSocketOpenException: Exception opening socket}, caused by {io.netty.channel.AbstractChannel$AnnotatedConnectException: Connection refused: /172.31.46.124:27017}, caused by {java.net.ConnectException: Connection refused}}]
2026-08-05T07:13:20.287980465Z  [chat-service]  at com.mongodb.internal.connection.BaseCluster.logAndThrowTimeoutException(BaseCluster.java:427) ~[mongodb-driver-core-5.5.1.jar!/:na]
2026-08-05T07:13:20.414062276Z  [chat-service]  [2m2026-08-05T16:13:20.413+09:00[0;39m [32m INFO [traceId=6a72e29019c5d69cbf4d2bdc6b0fc999,spanId=fd73bc2bf6a9e4a1,userId=NONE][0;39m [35m7[0;39m [2m--- [chat-service] [nio-8090-exec-5] [6a72e29019c5d69cbf4d2bdc6b0fc999-fd73bc2bf6a9e4a1] [0;39m[36morg.mongodb.driver.cluster              [0;39m [2m:[0;39m Waiting for server to become available for operation with ID 73502. Remaining time: 29999 ms. Selector: ReadPreferenceServerSelector{readPreference=primary}, topology description: {type=UNKNOWN, servers=[{address=172.31.46.124:27017, type=UNKNOWN, state=CONNECTING, exception={com.mongodb.MongoSocketOpenException: Exception opening socket}, caused by {io.netty.channel.AbstractChannel$AnnotatedConnectException: Connection refused: /172.31.46.124:27017}, caused by {java.net.ConnectException: Connection refused}}].
2026-08-05T07:13:30.334838542Z  [chat-service]  org.springframework.dao.DataAccessResourceFailureException: Timed out while waiting for a server that matches ReadPreferenceServerSelector{readPreference=primary}. Client view of cluster state is {type=UNKNOWN, servers=[{address=172.31.46.124:27017, type=UNKNOWN, state=CONNECTING, exception={com.mongodb.MongoSocketOpenException: Exception opening socket}, caused by {io.netty.channel.AbstractChannel$AnnotatedConnectException: Connection refused: /172.31.46.124:27017}, caused by {java.net.ConnectException: Connection refused}}]
2026-08-05T07:13:30.334844414Z  [chat-service]  at org.springframework.data.mongodb.core.MongoExceptionTranslator.doTranslateException(MongoExceptionTranslator.java:97) ~[spring-data-mongodb-4.5.1.jar!/:4.5.1]
2026-08-05T07:13:30.334847300Z  [chat-service]  at org.springframework.data.mongodb.core.MongoExceptionTranslator.translateExceptionIfPossible(MongoExceptionTranslator.java:74) ~[spring-data-mongodb-4.5.1.jar!/:4.5.1]
2026-08-05T07:13:30.334850062Z  [chat-service]  at org.springframework.data.mongodb.core.ReactiveMongoTemplate.potentiallyConvertRuntimeException(ReactiveMongoTemplate.java:2768) ~[spring-data-mongodb-4.5.1.jar!/:4.5.1]
2026-08-05T07:13:30.334852916Z  [chat-service]  at org.springframework.data.mongodb.core.ReactiveMongoTemplate.lambda$translateException$100(ReactiveMongoTemplate.java:2751) ~[spring-data-mongodb-4.5.1.jar!/:4.5.1]
2026-08-05T07:13:30.456131648Z  [chat-service]  [2m2026-08-05T16:13:30.455+09:00[0;39m [32m INFO [traceId=6a72e29a8b2c0fe2474c520c1e58704b,spanId=794d633e0857a6ed,userId=NONE][0;39m [35m7[0;39m [2m--- [chat-service] [nio-8090-exec-8] [6a72e29a8b2c0fe2474c520c1e58704b-794d633e0857a6ed] [0;39m[36morg.mongodb.driver.cluster              [0;39m [2m:[0;39m Waiting for server to become available for operation with ID 73526. Remaining time: 29999 ms. Selector: ReadPreferenceServerSelector{readPreference=primary}, topology description: {type=UNKNOWN, servers=[{address=172.31.46.124:27017, type=UNKNOWN, state=CONNECTING, exception={com.mongodb.MongoSocketOpenException: Exception opening socket}, caused by {io.netty.channel.AbstractChannel$AnnotatedConnectException: Connection refused: /172.31.46.124:27017}, caused by {java.net.ConnectException: Connection refused}}].
2026-08-05T07:13:40.497939234Z  [chat-service]  [2m2026-08-05T16:13:40.497+09:00[0;39m [32m INFO [traceId=6a72e2a4985a96e3c711fcaf353b619c,spanId=34c31f3ff2e5e1fe,userId=NONE][0;39m [35m7[0;39m [2m--- [chat-service] [nio-8090-exec-7] [6a72e2a4985a96e3c711fcaf353b619c-34c31f3ff2e5e1fe] [0;39m[36morg.mongodb.driver.cluster              [0;39m [2m:[0;39m Waiting for server to become available for operation with ID 73550. Remaining time: 29999 ms. Selector: ReadPreferenceServerSelector{readPreference=primary}, topology description: {type=UNKNOWN, servers=[{address=172.31.46.124:27017, type=UNKNOWN, state=CONNECTING, exception={com.mongodb.MongoSocketOpenException: Exception opening socket}, caused by {io.netty.channel.AbstractChannel$AnnotatedConnectException: Connection refused: /172.31.46.124:27017}, caused by {java.net.ConnectException: Connection refused}}].
2026-08-05T07:13:50.540861852Z  [chat-service]  [2m2026-08-05T16:13:50.539+09:00[0;39m [32m INFO [traceId=6a72e2aece2ada80111614c2519dba03,spanId=df5591dd9fd046e6,userId=NONE][0;39m [35m7[0;39m [2m--- [chat-service] [nio-8090-exec-3] [6a72e2aece2ada80111614c2519dba03-df5591dd9fd046e6] [0;39m[36morg.mongodb.driver.cluster              [0;39m [2m:[0;39m Waiting for server to become available for operation with ID 73574. Remaining time: 29999 ms. Selector: ReadPreferenceServerSelector{readPreference=primary}, topology description: {type=UNKNOWN, servers=[{address=172.31.46.124:27017, type=UNKNOWN, state=CONNECTING, exception={com.mongodb.MongoSocketOpenException: Exception opening socket}, caused by {io.netty.channel.AbstractChannel$AnnotatedConnectException: Connection refused: /172.31.46.124:27017}, caused by {java.net.ConnectException: Connection refused}}].
2026-08-05T07:14:00.581238815Z  [chat-service]  [2m2026-08-05T16:14:00.581+09:00[0;39m [32m INFO [traceId=6a72e2b80c840a9b6ef56c59b9a1d5d7,spanId=39ad089307aa656e,userId=NONE][0;39m [35m7[0;39m [2m--- [chat-service] [nio-8090-exec-2] [6a72e2b80c840a9b6ef56c59b9a1d5d7-39ad089307aa656e] [0;39m[36morg.mongodb.driver.cluster              [0;39m [2m:[0;39m Waiting for server to become available for operation with ID 73598. Remaining time: 29999 ms. Selector: ReadPreferenceServerSelector{readPreference=primary}, topology description: {type=UNKNOWN, servers=[{address=172.31.46.124:27017, type=UNKNOWN, state=CONNECTING, exception={com.mongodb.MongoSocketOpenException: Exception opening socket}, caused by {io.netty.channel.AbstractChannel$AnnotatedConnectException: Connection refused: /172.31.46.124:27017}, caused by {java.net.ConnectException: Connection refused}}].
2026-08-05T07:14:10.622899583Z  [chat-service]  [2m2026-08-05T16:14:10.622+09:00[0;39m [32m INFO [traceId=6a72e2c2ff18f4692010e54b90dac132,spanId=77357496d5208421,userId=NONE][0;39m [35m7[0;39m [2m--- [chat-service] [nio-8090-exec-9] [6a72e2c2ff18f4692010e54b90dac132-77357496d5208421] [0;39m[36morg.mongodb.driver.cluster              [0;39m [2m:[0;39m Waiting for server to become available for operation with ID 73622. Remaining time: 29999 ms. Selector: ReadPreferenceServerSelector{readPreference=primary}, topology description: {type=UNKNOWN, servers=[{address=172.31.46.124:27017, type=UNKNOWN, state=CONNECTING, exception={com.mongodb.MongoSocketOpenException: Exception opening socket}, caused by {io.netty.channel.AbstractChannel$AnnotatedConnectException: Connection refused: /172.31.46.124:27017}, caused by {java.net.ConnectException: Connection refused}}].
2026-08-05T07:14:20.665756878Z  [chat-service]  [2m2026-08-05T16:14:20.665+09:00[0;39m [32m INFO [traceId=6a72e2cc5e3543aeaa68198c1551fe07,spanId=196fed106652b703,userId=NONE][0;39m [35m7[0;39m [2m--- [chat-service] [nio-8090-exec-1] [6a72e2cc5e3543aeaa68198c1551fe07-196fed106652b703] [0;39m[36morg.mongodb.driver.cluster              [0;39m [2m:[0;39m Waiting for server to become available for operation with ID 73646. Remaining time: 29999 ms. Selector: ReadPreferenceServerSelector{readPreference=primary}, topology description: {type=UNKNOWN, servers=[{address=172.31.46.124:27017, type=UNKNOWN, state=CONNECTING, exception={com.mongodb.MongoSocketOpenException: Exception opening socket}, caused by {io.netty.channel.AbstractChannel$AnnotatedConnectException: Connection refused: /172.31.46.124:27017}, caused by {java.net.ConnectException: Connection refused}}].
2026-08-05T07:14:30.708065748Z  [chat-service]  [2m2026-08-05T16:14:30.707+09:00[0;39m [32m INFO [traceId=6a72e2d6e06f0c82cd59f8e32d863292,spanId=47ae0c0477308546,userId=NONE][0;39m [35m7[0;39m [2m--- [chat-service] [io-8090-exec-10] [6a72e2d6e06f0c82cd59f8e32d863292-47ae0c0477308546] [0;39m[36morg.mongodb.driver.cluster              [0;39m [2m:[0;39m Waiting for server to become available for operation with ID 73670. Remaining time: 29999 ms. Selector: ReadPreferenceServerSelector{readPreference=primary}, topology description: {type=UNKNOWN, servers=[{address=172.31.46.124:27017, type=UNKNOWN, state=CONNECTING, exception={com.mongodb.MongoSocketOpenException: Exception opening socket}, caused by {io.netty.channel.AbstractChannel$AnnotatedConnectException: Connection refused: /172.31.46.124:27017}, caused by {java.net.ConnectException: Connection refused}}].
2026-08-05T07:14:40.748856082Z  [chat-service]  [2m2026-08-05T16:14:40.748+09:00[0;39m [32m INFO [traceId=6a72e2e0d3a0c67c8fa94c86578938bf,spanId=1e49a7a117342f2a,userId=NONE][0;39m [35m7[0;39m [2m--- [chat-service] [nio-8090-exec-5] [6a72e2e0d3a0c67c8fa94c86578938bf-1e49a7a117342f2a] [0;39m[36morg.mongodb.driver.cluster              [0;39m [2m:[0;39m Waiting for server to become available for operation with ID 73694. Remaining time: 29999 ms. Selector: ReadPreferenceServerSelector{readPreference=primary}, topology description: {type=UNKNOWN, servers=[{address=172.31.46.124:27017, type=UNKNOWN, state=CONNECTING, exception={com.mongodb.MongoSocketOpenException: Exception opening socket}, caused by {io.netty.channel.AbstractChannel$AnnotatedConnectException: Connection refused: /172.31.46.124:27017}, caused by {java.net.ConnectException: Connection refused}}].
2026-08-05T07:14:50.789949061Z  [chat-service]  [2m2026-08-05T16:14:50.789+09:00[0;39m [32m INFO [traceId=6a72e2eaa29bcdb806a0537c0f51396e,spanId=06bfed26430302fb,userId=NONE][0;39m [35m7[0;39m [2m--- [chat-service] [nio-8090-exec-6] [6a72e2eaa29bcdb806a0537c0f51396e-06bfed26430302fb] [0;39m[36morg.mongodb.driver.cluster              [0;39m [2m:[0;39m Waiting for server to become available for operation with ID 73718. Remaining time: 29999 ms. Selector: ReadPreferenceServerSelector{readPreference=primary}, topology description: {type=UNKNOWN, servers=[{address=172.31.46.124:27017, type=UNKNOWN, state=CONNECTING, exception={com.mongodb.MongoSocketOpenException: Exception opening socket}, caused by {io.netty.channel.AbstractChannel$AnnotatedConnectException: Connection refused: /172.31.46.124:27017}, caused by {java.net.ConnectException: Connection refused}}].
2026-08-05T07:15:00.808430381Z  [chat-service]  [2m2026-08-05T16:15:00.807+09:00[0;39m [32m INFO [traceId=6a72e2f480d43f18ec84c63abec43a49,spanId=8e0ac200d65a83c3,userId=NONE][0;39m [35m7[0;39m [2m--- [chat-service] [nio-8090-exec-7] [6a72e2f480d43f18ec84c63abec43a49-8e0ac200d65a83c3] [0;39m[36morg.mongodb.driver.cluster              [0;39m [2m:[0;39m Waiting for server to become available for operation with ID 73742. Remaining time: 29999 ms. Selector: ReadPreferenceServerSelector{readPreference=primary}, topology description: {type=UNKNOWN, servers=[{address=172.31.46.124:27017, type=UNKNOWN, state=CONNECTING, exception={com.mongodb.MongoSocketOpenException: Exception opening socket}, caused by {io.netty.channel.AbstractChannel$AnnotatedConnectException: Connection refused: /172.31.46.124:27017}, caused by {java.net.ConnectException: Connection refused}}].
2026-08-05T07:15:10.835738218Z  [chat-service]  [2m2026-08-05T16:15:10.835+09:00[0;39m [32m INFO [traceId=6a72e2feb9deb8931237f12baf9e7ea0,spanId=664a11d72d405cd4,userId=NONE][0;39m [35m7[0;39m [2m--- [chat-service] [nio-8090-exec-4] [6a72e2feb9deb8931237f12baf9e7ea0-664a11d72d405cd4] [0;39m[36morg.mongodb.driver.cluster              [0;39m [2m:[0;39m Waiting for server to become available for operation with ID 73766. Remaining time: 29999 ms. Selector: ReadPreferenceServerSelector{readPreference=primary}, topology description: {type=UNKNOWN, servers=[{address=172.31.46.124:27017, type=UNKNOWN, state=CONNECTING, exception={com.mongodb.MongoSocketOpenException: Exception opening socket}, caused by {io.netty.channel.AbstractChannel$AnnotatedConnectException: Connection refused: /172.31.46.124:27017}, caused by {java.net.ConnectException: Connection refused}}].
2026-08-05T07:15:20.876711761Z  [chat-service]  [2m2026-08-05T16:15:20.876+09:00[0;39m [32m INFO [traceId=6a72e30830e4b455e6c172d83c606b09,spanId=b32a7e892f46ff21,userId=NONE][0;39m [35m7[0;39m [2m--- [chat-service] [nio-8090-exec-2] [6a72e30830e4b455e6c172d83c606b09-b32a7e892f46ff21] [0;39m[36morg.mongodb.driver.cluster              [0;39m [2m:[0;39m Waiting for server to become available for operation with ID 73790. Remaining time: 29999 ms. Selector: ReadPreferenceServerSelector{readPreference=primary}, topology description: {type=UNKNOWN, servers=[{address=172.31.46.124:27017, type=UNKNOWN, state=CONNECTING, exception={com.mongodb.MongoSocketOpenException: Exception opening socket}, caused by {io.netty.channel.AbstractChannel$AnnotatedConnectException: Connection refused: /172.31.46.124:27017}, caused by {java.net.ConnectException: Connection refused}}].
2026-08-05T07:15:30.919077338Z  [chat-service]  [2m2026-08-05T16:15:30.918+09:00[0;39m [32m INFO [traceId=6a72e3121a86c96044be900b238201ee,spanId=227964fe46db8291,userId=NONE][0;39m [35m7[0;39m [2m--- [chat-service] [nio-8090-exec-9] [6a72e3121a86c96044be900b238201ee-227964fe46db8291] [0;39m[36morg.mongodb.driver.cluster              [0;39m [2m:[0;39m Waiting for server to become available for operation with ID 73814. Remaining time: 29999 ms. Selector: ReadPreferenceServerSelector{readPreference=primary}, topology description: {type=UNKNOWN, servers=[{address=172.31.46.124:27017, type=UNKNOWN, state=CONNECTING, exception={com.mongodb.MongoSocketOpenException: Exception opening socket}, caused by {io.netty.channel.AbstractChannel$AnnotatedConnectException: Connection refused: /172.31.46.124:27017}, caused by {java.net.ConnectException: Connection refused}}].
2026-08-05T07:15:40.959613128Z  [chat-service]  [2m2026-08-05T16:15:40.959+09:00[0;39m [32m INFO [traceId=6a72e31c65fdf38ab48df03946375255,spanId=2104f10c7117ea5b,userId=NONE][0;39m [35m7[0;39m [2m--- [chat-service] [nio-8090-exec-8] [6a72e31c65fdf38ab48df03946375255-2104f10c7117ea5b] [0;39m[36morg.mongodb.driver.cluster              [0;39m [2m:[0;39m Waiting for server to become available for operation with ID 73838. Remaining time: 29999 ms. Selector: ReadPreferenceServerSelector{readPreference=primary}, topology description: {type=UNKNOWN, servers=[{address=172.31.46.124:27017, type=UNKNOWN, state=CONNECTING, exception={com.mongodb.MongoSocketOpenException: Exception opening socket}, caused by {io.netty.channel.AbstractChannel$AnnotatedConnectException: Connection refused: /172.31.46.124:27017}, caused by {java.net.ConnectException: Connection refused}}].
2026-08-05T07:15:51.001024540Z  [chat-service]  [2m2026-08-05T16:15:51.000+09:00[0;39m [32m INFO [traceId=6a72e326c40e3e2087808df7e1fd0a52,spanId=1390a61a1614a8d0,userId=NONE][0;39m [35m7[0;39m [2m--- [chat-service] [io-8090-exec-10] [6a72e326c40e3e2087808df7e1fd0a52-1390a61a1614a8d0] [0;39m[36morg.mongodb.driver.cluster              [0;39m [2m:[0;39m Waiting for server to become available for operation with ID 73862. Remaining time: 29999 ms. Selector: ReadPreferenceServerSelector{readPreference=primary}, topology description: {type=UNKNOWN, servers=[{address=172.31.46.124:27017, type=UNKNOWN, state=CONNECTING, exception={com.mongodb.MongoSocketOpenException: Exception opening socket}, caused by {io.netty.channel.AbstractChannel$AnnotatedConnectException: Connection refused: /172.31.46.124:27017}, caused by {java.net.ConnectException: Connection refused}}].
2026-08-05T07:16:01.044253283Z  [chat-service]  [2m2026-08-05T16:16:01.044+09:00[0;39m [32m INFO [traceId=6a72e331b2098912815772496bb7a475,spanId=75c14d6223813093,userId=NONE][0;39m [35m7[0;39m [2m--- [chat-service] [nio-8090-exec-5] [6a72e331b2098912815772496bb7a475-75c14d6223813093] [0;39m[36morg.mongodb.driver.cluster              [0;39m [2m:[0;39m Waiting for server to become available for operation with ID 73886. Remaining time: 29999 ms. Selector: ReadPreferenceServerSelector{readPreference=primary}, topology description: {type=UNKNOWN, servers=[{address=172.31.46.124:27017, type=UNKNOWN, state=CONNECTING, exception={com.mongodb.MongoSocketOpenException: Exception opening socket}, caused by {io.netty.channel.AbstractChannel$AnnotatedConnectException: Connection refused: /172.31.46.124:27017}, caused by {java.net.ConnectException: Connection refused}}].
2026-08-05T07:16:11.086368747Z  [chat-service]  [2m2026-08-05T16:16:11.086+09:00[0;39m [32m INFO [traceId=6a72e33be15fb95014a761d0ff43a0a4,spanId=0e08400c2f663a83,userId=NONE][0;39m [35m7[0;39m [2m--- [chat-service] [nio-8090-exec-3] [6a72e33be15fb95014a761d0ff43a0a4-0e08400c2f663a83] [0;39m[36morg.mongodb.driver.cluster              [0;39m [2m:[0;39m Waiting for server to become available for operation with ID 73910. Remaining time: 29999 ms. Selector: ReadPreferenceServerSelector{readPreference=primary}, topology description: {type=UNKNOWN, servers=[{address=172.31.46.124:27017, type=UNKNOWN, state=CONNECTING, exception={com.mongodb.MongoSocketOpenException: Exception opening socket}, caused by {io.netty.channel.AbstractChannel$AnnotatedConnectException: Connection refused: /172.31.46.124:27017}, caused by {java.net.ConnectException: Connection refused}}].
2026-08-05T07:16:21.128457979Z  [chat-service]  [2m2026-08-05T16:16:21.128+09:00[0;39m [32m INFO [traceId=6a72e345fe061c364aa7e89d50d013e2,spanId=edeeba4c2aab94fb,userId=NONE][0;39m [35m7[0;39m [2m--- [chat-service] [nio-8090-exec-7] [6a72e345fe061c364aa7e89d50d013e2-edeeba4c2aab94fb] [0;39m[36morg.mongodb.driver.cluster              [0;39m [2m:[0;39m Waiting for server to become available for operation with ID 73934. Remaining time: 29999 ms. Selector: ReadPreferenceServerSelector{readPreference=primary}, topology description: {type=UNKNOWN, servers=[{address=172.31.46.124:27017, type=UNKNOWN, state=CONNECTING, exception={com.mongodb.MongoSocketOpenException: Exception opening socket}, caused by {io.netty.channel.AbstractChannel$AnnotatedConnectException: Connection refused: /172.31.46.124:27017}, caused by {java.net.ConnectException: Connection refused}}].
2026-08-05T07:16:31.169887543Z  [chat-service]  [2m2026-08-05T16:16:31.169+09:00[0;39m [32m INFO [traceId=6a72e34fe1c1adcc810d31a776269b7a,spanId=91915fdd15104e4f,userId=NONE][0;39m [35m7[0;39m [2m--- [chat-service] [nio-8090-exec-4] [6a72e34fe1c1adcc810d31a776269b7a-91915fdd15104e4f] [0;39m[36morg.mongodb.driver.cluster              [0;39m [2m:[0;39m Waiting for server to become available for operation with ID 73959. Remaining time: 29999 ms. Selector: ReadPreferenceServerSelector{readPreference=primary}, topology description: {type=UNKNOWN, servers=[{address=172.31.46.124:27017, type=UNKNOWN, state=CONNECTING, exception={com.mongodb.MongoSocketOpenException: Exception opening socket}, caused by {io.netty.channel.AbstractChannel$AnnotatedConnectException: Connection refused: /172.31.46.124:27017}, caused by {java.net.ConnectException: Connection refused}}].
2026-08-05T07:16:41.211343846Z  [chat-service]  [2m2026-08-05T16:16:41.210+09:00[0;39m [32m INFO [traceId=6a72e35956662d9421101e6fb385334a,spanId=abe362b3a2740223,userId=NONE][0;39m [35m7[0;39m [2m--- [chat-service] [nio-8090-exec-1] [6a72e35956662d9421101e6fb385334a-abe362b3a2740223] [0;39m[36morg.mongodb.driver.cluster              [0;39m [2m:[0;39m Waiting for server to become available for operation with ID 73983. Remaining time: 29999 ms. Selector: ReadPreferenceServerSelector{readPreference=primary}, topology description: {type=UNKNOWN, servers=[{address=172.31.46.124:27017, type=UNKNOWN, state=CONNECTING, exception={com.mongodb.MongoSocketOpenException: Exception opening socket}, caused by {io.netty.channel.AbstractChannel$AnnotatedConnectException: Connection refused: /172.31.46.124:27017}, caused by {java.net.ConnectException: Connection refused}}].
```

### 메트릭 시계열

| 쿼리 | series | 점 | min | max | last | 값이 0이던 구간 |
|---|---|---:|---:|---:|---:|---|
| `hikaricp_connections_active` | `{__name__=hikaricp_connections_active, application=auth-service, cluster=yogurtte-k3s-prod, container=auth-service, instance=10.42.1.51:8090, job=auth-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=auth-service-5999bb9f5c-lzp4p, pool=HikariPool-1, service=auth-service}` | 81 | 0 | 0 | 0 | **2026-08-05T07:03:03Z ~ 2026-08-05T07:23:03Z** |
| `hikaricp_connections_active` | `{__name__=hikaricp_connections_active, application=chat-service, cluster=yogurtte-k3s-prod, container=chat-service, instance=10.42.1.47:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-fdcc7c776-xf4sv, pool=HikariPool-1}` | 65 | 0 | 0 | 0 | **2026-08-05T07:03:03Z ~ 2026-08-05T07:23:03Z** |
| `hikaricp_connections_active` | `{__name__=hikaricp_connections_active, application=content-service, cluster=yogurtte-k3s-prod, container=content-service, instance=10.42.1.50:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-5489b58cbc-2ckp6, pool=HikariPool-1}` | 81 | 0 | 0 | 0 | **2026-08-05T07:03:03Z ~ 2026-08-05T07:23:03Z** |
| `hikaricp_connections_active` | `{__name__=hikaricp_connections_active, application=content-service, cluster=yogurtte-k3s-prod, container=content-service, instance=10.42.3.45:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-5489b58cbc-r8r5j, pool=HikariPool-1}` | 81 | 0 | 0 | 0 | **2026-08-05T07:03:03Z ~ 2026-08-05T07:23:03Z** |
| `hikaricp_connections_pending` | `{__name__=hikaricp_connections_pending, application=auth-service, cluster=yogurtte-k3s-prod, container=auth-service, instance=10.42.1.51:8090, job=auth-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=auth-service-5999bb9f5c-lzp4p, pool=HikariPool-1, service=auth-service}` | 81 | 0 | 0 | 0 | **2026-08-05T07:03:03Z ~ 2026-08-05T07:23:03Z** |
| `hikaricp_connections_pending` | `{__name__=hikaricp_connections_pending, application=chat-service, cluster=yogurtte-k3s-prod, container=chat-service, instance=10.42.1.47:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-fdcc7c776-xf4sv, pool=HikariPool-1}` | 65 | 0 | 0 | 0 | **2026-08-05T07:03:03Z ~ 2026-08-05T07:23:03Z** |
| `hikaricp_connections_pending` | `{__name__=hikaricp_connections_pending, application=content-service, cluster=yogurtte-k3s-prod, container=content-service, instance=10.42.1.50:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-5489b58cbc-2ckp6, pool=HikariPool-1}` | 81 | 0 | 0 | 0 | **2026-08-05T07:03:03Z ~ 2026-08-05T07:23:03Z** |
| `hikaricp_connections_pending` | `{__name__=hikaricp_connections_pending, application=content-service, cluster=yogurtte-k3s-prod, container=content-service, instance=10.42.3.45:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-5489b58cbc-r8r5j, pool=HikariPool-1}` | 81 | 0 | 0 | 0 | **2026-08-05T07:03:03Z ~ 2026-08-05T07:23:03Z** |
| `rate(jvm_gc_pause_seconds_sum[5m])` | `{action=end of major GC, application=chat-service, cause=Allocation Failure, cluster=yogurtte-k3s-prod, container=chat-service, gc=MarkSweepCompact, instance=10.42.1.47:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-fdcc7c776-xf4sv}` | 73 | 0 | 0 | 0 | **2026-08-05T07:03:03Z ~ 2026-08-05T07:23:03Z** |
| `rate(jvm_gc_pause_seconds_sum[5m])` | `{action=end of minor GC, application=auth-service, cause=G1 Evacuation Pause, cluster=yogurtte-k3s-prod, container=auth-service, gc=G1 Young Generation, instance=10.42.1.51:8090, job=auth-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=auth-service-5999bb9f5c-lzp4p, service=auth-service}` | 81 | 0 | 0.000 | 0 | **2026-08-05T07:05:48Z ~ 2026-08-05T07:23:03Z** |
| `rate(jvm_gc_pause_seconds_sum[5m])` | `{action=end of minor GC, application=auth-service, cause=Metadata GC Threshold, cluster=yogurtte-k3s-prod, container=auth-service, gc=G1 Young Generation, instance=10.42.1.51:8090, job=auth-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=auth-service-5999bb9f5c-lzp4p, service=auth-service}` | 81 | 0 | 0 | 0 | **2026-08-05T07:03:03Z ~ 2026-08-05T07:23:03Z** |
| `rate(jvm_gc_pause_seconds_sum[5m])` | `{action=end of minor GC, application=chat-service, cause=Allocation Failure, cluster=yogurtte-k3s-prod, container=chat-service, gc=Copy, instance=10.42.1.47:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-fdcc7c776-xf4sv}` | 73 | 0.000 | 0.001 | 0.001 | — |
| `rate(jvm_gc_pause_seconds_sum[5m])` | `{action=end of minor GC, application=content-service, cause=Allocation Failure, cluster=yogurtte-k3s-prod, container=content-service, gc=Copy, instance=10.42.1.50:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-5489b58cbc-2ckp6}` | 81 | 0 | 0.000 | 0 | **2026-08-05T07:03:03Z ~ 2026-08-05T07:12:48Z, 2026-08-05T07:17:03Z ~ 2026-08-05T07:23:03Z** |
| `rate(jvm_gc_pause_seconds_sum[5m])` | `{action=end of minor GC, application=content-service, cause=Allocation Failure, cluster=yogurtte-k3s-prod, container=content-service, gc=Copy, instance=10.42.3.45:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-5489b58cbc-r8r5j}` | 81 | 0 | 0.000 | 0 | **2026-08-05T07:03:03Z ~ 2026-08-05T07:08:33Z, 2026-08-05T07:12:48Z ~ 2026-08-05T07:23:03Z** |
| `up` | `{__name__=up, app=node-exporter, cluster=yogurtte-k3s-prod, component=metrics, container=node-exporter, instance=ip-172-31-40-241, job=integrations/node_exporter, k8s_cluster_name=yogurtte-k3s-prod, namespace=monitoring, pod=grafana-k8s-monitoring-node-exporter-cph5l, source=kubernetes, workload=DaemonSet/grafana-k8s-monitoring-node-exporter}` | 81 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, app=node-exporter, cluster=yogurtte-k3s-prod, component=metrics, container=node-exporter, instance=ip-172-31-45-39, job=integrations/node_exporter, k8s_cluster_name=yogurtte-k3s-prod, namespace=monitoring, pod=grafana-k8s-monitoring-node-exporter-9kvqt, source=kubernetes, workload=DaemonSet/grafana-k8s-monitoring-node-exporter}` | 81 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, cluster=yogurtte-k3s-prod, container=auth-service, instance=10.42.1.51:8090, job=auth-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=auth-service-5999bb9f5c-lzp4p}` | 81 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, cluster=yogurtte-k3s-prod, container=chat-service, instance=10.42.1.47:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-fdcc7c776-xf4sv}` | 65 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, cluster=yogurtte-k3s-prod, container=content-service, instance=10.42.1.50:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-5489b58cbc-2ckp6}` | 81 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, cluster=yogurtte-k3s-prod, container=content-service, instance=10.42.3.45:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-5489b58cbc-r8r5j}` | 81 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, cluster=yogurtte-k3s-prod, instance=10.42.1.248:8080, job=integrations/kubernetes/kube-state-metrics, k8s_cluster_name=yogurtte-k3s-prod, source=kubernetes}` | 81 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, cluster=yogurtte-k3s-prod, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod}` | 81 | 1 | 1 | 1 | — |
| `mongodb_up` | `{__name__=mongodb_up, cluster=yogurtte-k3s-prod, instance=infra-server, job=mongodb, k8s_cluster_name=yogurtte-k3s-prod}` | 81 | 0 | 1 | 1 | **2026-08-05T07:12:48Z ~ 2026-08-05T07:18:03Z** |
| `kafka_brokers` | `{__name__=kafka_brokers, cluster=yogurtte-k3s-prod, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod}` | 81 | 1 | 1 | 1 | — |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=chat-service-fcm-tokens, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=0, topic=user.fcm-tokens}` | 81 | 0 | 0 | 0 | **2026-08-05T07:03:03Z ~ 2026-08-05T07:23:03Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=chat-service-fcm-tokens, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=1, topic=user.fcm-tokens}` | 81 | 0 | 0 | 0 | **2026-08-05T07:03:03Z ~ 2026-08-05T07:23:03Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=chat-service-fcm-tokens, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=2, topic=user.fcm-tokens}` | 81 | 0 | 0 | 0 | **2026-08-05T07:03:03Z ~ 2026-08-05T07:23:03Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=chat-service-notification-settings, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=0, topic=user.notification-settings}` | 81 | 0 | 0 | 0 | **2026-08-05T07:03:03Z ~ 2026-08-05T07:23:03Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=chat-service-notification-settings, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=1, topic=user.notification-settings}` | 81 | 0 | 0 | 0 | **2026-08-05T07:03:03Z ~ 2026-08-05T07:23:03Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=chat-service-notification-settings, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=2, topic=user.notification-settings}` | 81 | 0 | 0 | 0 | **2026-08-05T07:03:03Z ~ 2026-08-05T07:23:03Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=db-writer, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=0, topic=chat.messages}` | 81 | 0 | 0 | 0 | **2026-08-05T07:03:03Z ~ 2026-08-05T07:23:03Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=db-writer, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=1, topic=chat.messages}` | 81 | 0 | 0 | 0 | **2026-08-05T07:03:03Z ~ 2026-08-05T07:23:03Z** |
| `websocket_active_users` | `{__name__=websocket_active_users, application=chat-service, cluster=yogurtte-k3s-prod, container=chat-service, instance=10.42.1.47:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-fdcc7c776-xf4sv}` | 65 | 0 | 0 | 0 | **2026-08-05T07:03:03Z ~ 2026-08-05T07:23:03Z** |

값이 0이던 구간은 굵게 표시했다 — 프로세스가 사라져 시계열이 꺾인 것이 유일한 신호인 장애가 있다.

