# RCA Report — `scan-1786497720`

| 항목 | 값 |
|---|---|
| 모드 | rca |
| 질문 | 최근 1시간 안에 댓글 작성이 실패했다는 제보가 있다. 원인을 조사해줘 |
| 시각 | 2026-08-12T05:05:10.201310100Z |
| provider | claude-cli |
| model | `claude-opus-5` · turns 1 |
| prompt | `.\prompts\system-prompt.md` |
| tokens | in 67430 (cacheRead 23,796 · cacheCreate 43,632) / out 7427 · cost $0.6339 |
| elapsed | total 132176ms (tempo 420 · loki 223 · mimir 677 · assemble 148 · llm 119086) |

## 탐색 (Triage)

| 항목 | 값 |
|---|---|
| 시간창 해석 | 명시적 from/to |
| 시간창 확신도 | EXACT |
| 스윕 창 | 2026-08-12T00:33:14Z ~ 2026-08-12T01:33:14Z |
| 좁힌 창 | 2026-08-12T01:22:00Z ~ 2026-08-12T01:27:00Z |
| 대상 | content-service |
| traceId | 6a7bcad606c9ad16a434d244e431e33d |
| 트레이스 후보 | 1건 |
| 장애 후보 | 4건 · 선택 INC-2, INC-3 |
| 계획 파싱 | 성공 |
| 스윕 컨텍스트 | **후보만 — 원본 제외 (B)** |
| prompt | `.\prompts\triage-prompt.md` |
| tokens | in 32913 / out 1801 · cost $0.1753 |
| chars | 컨텍스트 2,143 + 프롬프트 1,399 = **3,542** |
| elapsed | survey 1474ms · llm 41268ms |

**선정 이유**: 질문의 증상(댓글 작성 실패)과 엔드포인트·서비스·시각이 모두 일치하는 유일한 조합이고, 에러 트레이스(INC-3)와 그 원인 예외 로그(INC-2)는 같은 장애를 두 채널에서 본 것이므로 함께 골랐다.

**근거**

- INC-3: content-service http post /feeds/{feedId}/comments 트레이스가 error 채널, 208ms 종료 (2026-08-12T01:22:30.309Z, traceId 6a7bcad606c9ad16a434d244e431e33d) — 제보된 '댓글 작성' 엔드포인트와 정확히 일치
- INC-2: 같은 분(01:22~01:23) content-service ERROR/WARN 4건 + 원인 예외 org.hibernate.exception.DataException 1건 — 에러 트레이스와 로그의 시각·서비스가 일치하므로 같은 장애의 상·하류 지문으로 판단
- DataException x5회 · 01:22~01:27 · 평균 60초 간격 — 단발이 아니라 반복 재현되는 DB 쓰기 거부(컬럼 길이/타입/인코딩 계열)
- Tempo 지연 검색 '{ duration > 3s && status != error }' 0건 — 느려짐형 장애가 아니라 즉시 실패형임을 확정
- up / mongodb_up / kafka_brokers / consumergroup_lag / websocket_active_users 모두 이상 신호 0건 — 인프라·브로커·프로세스 다운이 아닌 애플리케이션 레벨 데이터 오류

**스윕이 찾은 트레이스** (고른 것은 6a7bcad606c9ad16a434d244e431e33d)

| traceId | 채널 | root service | root span | ms |
|---|---|---|---|---:|
| `6a7bcad606c9ad16a434d244e431e33d` ←선택 | error | content-service | http post /feeds/{feedId}/comments | 208 |

**장애 후보** (코드가 신호를 묶은 것 · 창은 여기서 계산됨)

## INC-1  chat-service  |  ERROR/WARN
- 구간: 2026-08-12T01:22:00Z ~ 2026-08-12T01:23:00Z  (LOKI · 집계 해상도만큼 흐림)
- ERROR/WARN 1건 (2026-08-12T01:22:00Z ~ 2026-08-12T01:23:00Z)
- 같은 시각의 다른 후보: INC-2, INC-3  (인과 여부는 판단하지 않았다)

## INC-2  content-service  |  ERROR/WARN
- 구간: 2026-08-12T01:22:00Z ~ 2026-08-12T01:27:00Z  (LOKI · 집계 해상도만큼 흐림)
- ERROR/WARN 4건 (2026-08-12T01:22:00Z ~ 2026-08-12T01:23:00Z)
- 원인 예외 org.hibernate.exception.DataException 1건 (2026-08-12T01:22:00Z ~ 2026-08-12T01:23:00Z)  [x5회 · 2026-08-12T01:22:00Z~2026-08-12T01:27:00Z · 평균 60초 간격]
- 같은 시각의 다른 후보: INC-1, INC-3  (인과 여부는 판단하지 않았다)

## INC-3  content-service  |  http post /feeds/{feedId}/comments
- 구간: 2026-08-12T01:22:30.309754Z ~ 2026-08-12T01:22:30.517754Z  (TEMPO · 시각 정확)
- content-service http post /feeds/{feedId}/comments 208ms (error 채널)
- traceId: 6a7bcad606c9ad16a434d244e431e33d
- 같은 시각의 다른 후보: INC-1, INC-2  (인과 여부는 판단하지 않았다)

## INC-4  auth-service  |  ERROR/WARN
- 구간: 2026-08-12T01:31:00Z ~ 2026-08-12T01:33:00Z  (LOKI · 집계 해상도만큼 흐림)
- ERROR/WARN 2건 (2026-08-12T01:31:00Z ~ 2026-08-12T01:32:00Z)
- ERROR/WARN 4건 (2026-08-12T01:32:00Z ~ 2026-08-12T01:33:00Z)
- 예외 java.lang.NullPointerException 2건 (2026-08-12T01:32:00Z ~ 2026-08-12T01:33:00Z)

**기각한 후보**

- INC-1 — 시각(01:22~01:23)은 겹치지만 chat-service는 댓글 작성 경로에 없고 ERROR/WARN 단 1건에 원인 예외도 없어, 별개의 산발 로그이거나 content 실패의 부수 효과일 가능성이 높다 — content 쪽 조사에서 chat 연동 흔적이 나오면 재검토 대상.
- INC-4 — auth-service NullPointerException은 01:31~01:33으로 댓글 실패 시각(01:22~01:27)보다 9분 이상 뒤이고 서비스도 다르며, 인증 실패였다면 트레이스가 401/403로 auth 구간에서 끊겼을 텐데 실제 error 트레이스는 content-service 댓글 핸들러에서 발생했다 — 별건으로 분리.

**보정 기록**

- 창을 후보 [INC-2, INC-3] 의 신호 시각에서 계산했다 (2026-08-12T01:22:00Z ~ 2026-08-12T01:27:00Z)

**스윕 무신호/실패**

- Tempo 지연 검색 '{ duration > 3s && status != error }'이 이 창에서 0건이다. 임계값보다 느린 요청이 없었다는 뜻이다.

## 수집 범위 (Coverage)

- **window**: 2026-08-12T01:22:00Z ~ 2026-08-12T01:27:00Z (300s)
- **trace**: 40,033B / 46 spans
- **창 안 후보 트레이스**: 2건 / 40,033B
- **logs**: errwarn=37,175B · traceId=6,383B
- **metrics**: 8 수집 / 54,134B, 누락 [sum(rate(http_server_requests_seconds_count{application="content-service", status="401"}[1m]))]
- **context**: 67,943 chars (+ 시스템 프롬프트 805 chars)

### 토큰 축 (개선 지표)

| 단계 | 총 in | chars | 컨텍스트 토큰 |
|---|---:|---:|---:|
| 탐색 | 32,913 | 3,542 | 2,903 |
| 분석 | 67,430 | 68,748 | 37,420 |
| **합계** | **100,343** | | **40,323** |

- **overheadTokens 30,010 tok — 이 회차에 실측**(1자 프롬프트 프로브, 같은 명령·모델·샌드박스).
  호출마다 한 번씩 얹히므로 단계별로 뺀다. **다른 날 상수를 쓰지 않았다 = `█ 실측`.**
- contextTokens (count_tokens API): 측정 안 됨 — 구독 CLI 경로엔 API 키가 없다
- 비용은 **API 환산 추정치**다. 구독 계정으로 호출하므로 실제 청구액이 아니다.

## 수집 실패/누락

- Metric 'sum(rate(http_server_requests_seconds_count{application="content-service", status="401"}[1m]))' returned no series in this window; skipped.

---

## 1. 원인 후보 랭킹 (최대 3개)

1. **댓글 본문 길이가 `tb_feed_comment.content` 컬럼 정의를 초과 → MySQL 1406(Data truncation)으로 INSERT 실패, 트랜잭션 롤백, HTTP 500** (애플리케이션 계층 입력 길이 검증 부재)
2. **관측되지 않은 다른 실패 유형(예: 401 인증 실패)** — 해당 메트릭 수집 실패로 배제 불가 (데이터 부족)
3. **인프라·자원 계열 원인(DB 커넥션 풀 고갈 / GC / Kafka·Mongo·Redis 장애)** — 관측값이 정면으로 반증

---

## 2. 후보별 근거

### 후보 1 — content 컬럼 길이 초과 (입력 검증 부재)

- **근거**
  - 실패 트레이스 `6a7bcad606c9ad16a434d244e431e33d`: 서버 span `http post /feeds/{feedId}/comments` = `"status":"500"`, `"outcome":"SERVER_ERROR"`, `"http.url":"/api/feeds/145/comments"`, 소요 208.897ms.
  - 그 하위 `query` span (start 01:22:30.357377Z, 112.422ms)에 `"error":"Data truncation: Data too long for column 'content' at row 1"`, `status.code = STATUS_CODE_ERROR`, SQL은 `insert into tb_feed_comment (content,created_at,creator_id,...) values (?,...)`.
  - 로그 원문: `WARN ... SqlExceptionHelper - SQL Error: 1406, SQLState: 22001` (10:22:30.470), 이어서 `ERROR ... SqlExceptionHelper - Data truncation: Data too long for column 'content' at row 1` (10:22:30.471).
  - 스택: `org.springframework.dao.DataIntegrityViolationException` → `Caused by: org.hibernate.exception.DataException` → `Caused by: com.mysql.cj.jdbc.exceptions.MysqlDataTruncation`. 애플리케이션 진입점은 `FeedCommentController.createComment(FeedCommentController.java:53)` → `FeedCommentService.createComment(FeedCommentService.java:64)`. **컨트롤러/서비스 어느 프레임에도 길이 검증 실패(400) 경로가 없고, 그대로 DB까지 내려가 DB 제약이 최후 방어선 역할을 했다.**
  - 처리 결과: `GlobalExceptionHandler - [api-error] handleAllException` → `RequestLoggingFilter - [HTTP] POST /api/feeds/145/comments 500 - 208ms`. 즉 검증 오류가 4xx가 아니라 500으로 나갔다.
  - 60초 뒤 같은 엔드포인트·같은 feed(145) 요청 `6a7bcb1282936b27a58ef575b8e0baf2`는 `"status":"200"`, `"outcome":"SUCCESS"`, 동일 INSERT가 `jdbc.row-affected: 1`, `generated-keys: 1469`로 성공. → 엔드포인트·DB·경로 자체는 정상이며 **요청 본문(입력값)에 종속된 실패**임을 보여준다.
- **확신도: 높음**
- **대기·지연 구간 판정**
  - JDBC 커넥션 획득: `connection` span(193.108ms) 내 `acquired` 이벤트가 span 시작 1.596ms 후 발생 → 대기 사실상 없음. 획득 **성공**. (Hikari `connectionTimeout` 설정값은 수집 데이터에 없어 상한 대조는 **판정 불가**, 다만 `hikaricp_connections_pending`이 전 구간 0이라 대기 자체가 없었음은 확정.)
  - INSERT 실행: 112.422ms 후 SQLException 반환. 상한(쿼리 타임아웃) 설정값 미수집 → 만료 여부 **판정 불가**. 단 반환된 것은 타임아웃이 아니라 **서버 측 오류코드 1406**이므로 타임아웃 만료가 아님은 확정.
  - 트랜잭션 최종 상태: `connection` span 이벤트가 `acquired` → **`rollback`**(01:22:30.499223Z). 성공 트레이스의 `commit`과 대비됨. → 해당 댓글 작성 작업은 **실패 후 롤백 폐기, 재시도 흔적 없음**(동일 traceId·동일 INSERT의 재실행 span 없음, 리트라이 토픽 lag 전 구간 0).
- **반증 데이터: 없음.** (참고로 실패 INSERT 112ms vs 성공 INSERT 2.486ms의 차이는 오류 반환 경로 비용으로 보이며, 이를 지연 원인으로 볼 근거는 없다.)

### 후보 2 — 관측되지 않은 다른 실패 유형(401 등)

- **근거**: 수집 실패 항목 `sum(rate(http_server_requests_seconds_count{application="content-service", status="401"}[1m]))` — no series, skipped. 이 창에서 content-service의 401 발생 여부를 **확인할 수 없다**. 또한 창 전체(01:22~01:27)의 5xx/4xx 총량 메트릭도 수집되지 않아, 실제로 관측된 댓글 실패는 **트레이스 1건·로그 1건뿐**이다. 제보가 다수 사용자라면 이 1건으로 전량을 설명한다고 단정할 수 없다.
  - 오해 주의: 실패 로그의 `userId=NONE`은 `RequestLoggingFilter` 라인에서만 나타나고, 같은 traceId의 앞선 라인들은 `userId=1`이며 `[user-cache] 캐시 HIT - userId: 1`도 남아 있다. → **인증은 통과했다.** 필터 종료 시점의 컨텍스트 클리어로 보이며 인증 실패 근거가 아니다.
- **확신도: 낮음** (수집 공백에 근거한 "배제 불가"일 뿐, 이를 지지하는 양성 관측값은 하나도 없음)
- **반증 데이터**: 확보된 유일한 실패 트레이스는 401이 아니라 500이고 인증 필터를 모두 통과했다(`security filterchain before/after` 12/12 완주). Redis 사용자 캐시 HIT도 정상.
- **대기·지연 구간 판정**: 해당 없음(관측된 대기 구간 자체가 없음). **판정 불가**.

### 후보 3 — 인프라·자원 계열(풀 고갈/GC/브로커·DB 장애)

- **근거(및 그 반증)**: 이 후보를 지지하는 관측값은 **없다**. 오히려 전부 반대 방향이다.
- **확신도: 낮음** (사실상 배제)
- **반증 데이터**
  - `hikaricp_connections_active`, `hikaricp_connections_pending`: content(2 파드)·chat·auth 전부 **전 구간 0**.
  - `up`: 앱 4개, kafka/mongodb/redis/node/kubelet 등 **전 구간 1**. `mongodb_up=1`, `kafka_brokers=1`.
  - `kafka_consumergroup_lag`: `notification-processors`(user.notifications) 전 파티션 0, DLQ `user.notifications.dlq` 0, 리트라이 토픽 전부 0.
  - `rate(jvm_gc_pause_seconds_sum[5m])`: content-service sp24n는 1.67e-5 → 01:23:45 이후 0, v2pw9는 전 구간 0. chat-service minor GC가 1.375e-4→3.0e-4로 완만히 증가하나 절대값은 초당 0.3ms 수준으로 무의미. Major GC(MarkSweepCompact) 전 구간 0.
- **대기·지연 구간 판정** (이 창에서 가장 긴 구간들 — 실패와의 인과는 없으나 상태는 명시)
  - chat-service Kafka `receive` span 407.209ms (offset 1160, partition 3, group `notification-processors`). `max.poll.interval.ms` 등 상한 설정값 미수집 → **만료 여부 판정 불가**. 다만 최종 상태는 **성공**: 하위 `connection` span이 `acquired`(1.557ms 후) → **`commit`**(01:23:31.378915Z)으로 종료, 소비자 그룹 lag 0, DLQ lag 0 → 재시도·폐기 없음.
  - 위 chat-service `connection`(406.362ms)은 Mongo `find`×5(최대 1.5ms)·`insert`×1·Redis `KEYS`×2를 **감싼 채** 유지된 상태다. 현재 부하에선 무해하나(`pending`=0) 커넥션 보유시간 관점의 잠재 위험으로만 기록한다.
  - Redis: `server.address":"redis://172.31.46.124?timeout=2s"` 로 **상한 2s** 명시, 실측 `KEYS` 0.492~0.533ms, `GET` 0.502~0.535ms → **만료 아님, 성공**.
  - content-service Kafka `publish user.notifications` 16.757ms → **성공**(하류 chat-service가 동일 traceId로 수신).

---

## 3. 권장 다음 조치

### 이미 발생한 피해: 복구 가능한가

**서버 측 복구 불가.** 근거:
- 트랜잭션이 `rollback`으로 종료되어 `tb_feed_comment`에 부분 기록이 없다. 동시에 `tb_feed` 카운트 갱신(`update tb_feed set comment_count=...`)·경험치 적립·`user.notifications` publish도 실패 트레이스에는 **아예 존재하지 않는다**(성공 트레이스에는 모두 존재). → **데이터 정합성 훼손은 없다. 고아 알림·중복 카운트도 없다.**
- 반면 사용자가 입력한 본문 원문은 어디에도 남아 있지 않다. 로그에는 바인딩 파라미터가 기록되지 않고(`values (?,?,...)`), 트레이스 속성에도 값이 없다. → 서버에서 재생(replay)할 소스가 없으므로 **해당 사용자에게 재작성 요청이 유일한 복구 경로**다.
- 영향 범위: 확정적으로 관측된 피해는 **userId=1, feedId=145, 2026-08-12 01:22:30Z 요청 1건**. 그 외 범위는 4xx/5xx 카운터 미수집으로 **확정 불가**.

### 재발 방지

1. **입력 길이 검증을 애플리케이션 경계로 올린다.** 요청 DTO에 `@Size(max=<컬럼 길이>)`를 걸고 `FeedCommentController.createComment`(:53)에서 400으로 거른다. 지금은 DB 제약이 유일한 방어선이라 정상적인 사용자 입력 오류가 500으로 나간다.
2. **`DataIntegrityViolationException` 매핑 추가.** `GlobalExceptionHandler`가 `handleAllException`으로 흡수해 500을 반환 중 → 데이터 무결성 위반은 400/422로 매핑.
3. **컬럼 길이 자체의 적정성 검토.** 현재 `content` 컬럼 길이는 수집 데이터에 없다(**데이터 부족**). DDL 확인 후, 제품이 허용하려는 최대 댓글 길이와 컬럼 정의·DTO 제약 3자를 정합시킬 것. (컬럼 확대는 제품 판단 영역)
4. **관측 공백 메우기.** `http_server_requests_seconds_count`를 status별로 확실히 수집(현재 401 시리즈 결측)하고, `SQLState=22001` / `SQL Error: 1406` 로그에 대한 알람을 건다.
5. (부차) chat-service 알림 소비 경로에서 Mongo·Redis 호출을 JDBC 트랜잭션 밖으로 빼 커넥션 보유 406ms를 줄인다. 현재 무해하나 부하 증가 시 풀 압박 요인.

### 복구 확인

- 경계 길이(허용 최대, 최대+1) 댓글로 재현 테스트 → 각각 200 / **400**(500 아님) 확인.
- Loki에서 `SQLState: 22001` 및 `Data too long for column 'content'` 발생 0건 확인.
- 트레이스에서 `POST /feeds/{feedId}/comments`의 `connection` span 종료 이벤트가 `commit`인지 확인(실패 시 `rollback`이 지표).
- 401 메트릭 시리즈 복구 후 동일 시간창을 재조회해, 이번에 배제하지 못한 후보 2를 확정적으로 정리.

---

추가 수집 필요 항목: `tb_feed_comment` DDL(content 컬럼 길이), 창 전체의 status별 요청 카운터, Hikari `connectionTimeout`·JPA 쿼리 타임아웃·Kafka `max.poll.interval.ms` 설정값(상한 대조가 현재 불가한 구간들).

---

## 관측 증거 (Evidence)

> 이 조사가 실제로 본 값이다. 원본 응답 전체는 `reports/raw/scan-1786497720-*.json`에 있다.

### 호출 그래프 (트레이스에서 추출)

```
chat-service --db--> mongodb  7회  최대 1.5ms  [find, insert]
chat-service --db--> redis  2회  최대 0.5ms  [KEYS]
content-service --db--> redis  2회  최대 0.5ms  [GET]
chat-service --jdbc--> mysql/content (HikariPool-1)  1회  최대 406.4ms
    events: acquired, commit
content-service --jdbc--> mysql/content (HikariPool-1)  20회  최대 193.1ms
    error: Data truncation: Data too long for column 'content' at row 1
    events: acquired, rollback, commit
content-service --messaging--> kafka/user.notifications  1회  최대 16.8ms  [publish]
kafka/user.notifications --messaging--> chat-service  1회  최대 407.2ms  [receive]
```

### span (duration 상위 15 / 전체 46)

| ms | service | span | 시작 |
|---:|---|---|---|
| 407.21 | chat-service | `receive` | 2026-08-12T01:23:30.973498Z |
| 406.36 | chat-service | `connection` | 2026-08-12T01:23:30.974064Z |
| 399.97 | chat-service | `user-notification-service#process-notification` | 2026-08-12T01:23:30.977292Z |
| 380.10 | chat-service | `push-dispatcher#dispatch` | 2026-08-12T01:23:30.997051Z |
| 208.90 | content-service | `http post /feeds/{feedId}/comments` | 2026-08-12T01:22:30.309754Z |
| 207.05 | content-service | `secured request` | 2026-08-12T01:22:30.310174Z |
| 193.11 | content-service | `connection` | 2026-08-12T01:22:30.323947Z |
| 112.42 | content-service | `query` | 2026-08-12T01:22:30.357377Z |
| 64.39 | content-service | `http post /feeds/{feedId}/comments` | 2026-08-12T01:23:30.890499Z |
| 63.35 | content-service | `secured request` | 2026-08-12T01:23:30.890974Z |
| 57.86 | content-service | `connection` | 2026-08-12T01:23:30.896381Z |
| 16.76 | content-service | `publish user.notifications` | 2026-08-12T01:23:30.953810Z |
| 3.71 | content-service | `query` | 2026-08-12T01:22:30.327680Z |
| 2.49 | content-service | `query` | 2026-08-12T01:23:30.905561Z |
| 2.08 | content-service | `query` | 2026-08-12T01:23:30.913190Z |

### 로그 원문 (60 / 전체 226줄)

전체가 상한을 넘어 ERROR/WARN 줄을 우선 발췌했다. 나머지는 원본 파일에 있다.

```
2026-08-12T01:22:30.470433748Z  [content-service]  2026-08-12 10:22:30.470 [http-nio-8082-exec-4]  WARN [traceId=6a7bcad606c9ad16a434d244e431e33d,spanId=1b186165c55e1502,userId=1] o.h.e.jdbc.spi.SqlExceptionHelper - SQL Error: 1406, SQLState: 22001
2026-08-12T01:22:30.470433748Z  [content-service]  2026-08-12 10:22:30.470 [http-nio-8082-exec-4]  WARN [traceId=6a7bcad606c9ad16a434d244e431e33d,spanId=1b186165c55e1502,userId=1] o.h.e.jdbc.spi.SqlExceptionHelper - SQL Error: 1406, SQLState: 22001
2026-08-12T01:22:30.471799097Z  [content-service]  2026-08-12 10:22:30.471 [http-nio-8082-exec-4] ERROR [traceId=6a7bcad606c9ad16a434d244e431e33d,spanId=1b186165c55e1502,userId=1] o.h.e.jdbc.spi.SqlExceptionHelper - Data truncation: Data too long for column 'content' at row 1
2026-08-12T01:22:30.471799097Z  [content-service]  2026-08-12 10:22:30.471 [http-nio-8082-exec-4] ERROR [traceId=6a7bcad606c9ad16a434d244e431e33d,spanId=1b186165c55e1502,userId=1] o.h.e.jdbc.spi.SqlExceptionHelper - Data truncation: Data too long for column 'content' at row 1
2026-08-12T01:22:30.514375349Z  [content-service]  2026-08-12 10:22:30.501 [http-nio-8082-exec-4]  WARN [traceId=6a7bcad606c9ad16a434d244e431e33d,spanId=1b186165c55e1502,userId=1] c.e.t.a.c.e.GlobalExceptionHandler - [api-error] handleAllException
2026-08-12T01:22:30.514375349Z  [content-service]  2026-08-12 10:22:30.501 [http-nio-8082-exec-4]  WARN [traceId=6a7bcad606c9ad16a434d244e431e33d,spanId=1b186165c55e1502,userId=1] c.e.t.a.c.e.GlobalExceptionHandler - [api-error] handleAllException
2026-08-12T01:22:30.514402204Z  [content-service]  org.springframework.dao.DataIntegrityViolationException: could not execute statement [Data truncation: Data too long for column 'content' at row 1] [insert into tb_feed_comment (content,created_at,creator_id,creator_nickname,creator_profile_url,deleted,deleted_at,feed_id,parent_comment_id,updated_at) values (?,?,?,?,?,?,?,?,?,?)]; SQL [insert into tb_feed_comment (content,created_at,creator_id,creator_nickname,creator_profile_url,deleted,deleted_at,feed_id,parent_comment_id,updated_at) values (?,?,?,?,?,?,?,?,?,?)]
2026-08-12T01:22:30.514407226Z  [content-service]  at org.springframework.orm.jpa.vendor.HibernateJpaDialect.convertHibernateAccessException(HibernateJpaDialect.java:293)
2026-08-12T01:22:30.514410768Z  [content-service]  at org.springframework.orm.jpa.vendor.HibernateJpaDialect.translateExceptionIfPossible(HibernateJpaDialect.java:241)
2026-08-12T01:22:30.514414844Z  [content-service]  at org.springframework.orm.jpa.AbstractEntityManagerFactoryBean.translateExceptionIfPossible(AbstractEntityManagerFactoryBean.java:560)
2026-08-12T01:22:30.514419142Z  [content-service]  at org.springframework.dao.support.ChainedPersistenceExceptionTranslator.translateExceptionIfPossible(ChainedPersistenceExceptionTranslator.java:61)
2026-08-12T01:22:30.514422928Z  [content-service]  at org.springframework.dao.support.DataAccessUtils.translateIfNecessary(DataAccessUtils.java:343)
2026-08-12T01:22:30.514438766Z  [content-service]  at org.springframework.dao.support.PersistenceExceptionTranslationInterceptor.invoke(PersistenceExceptionTranslationInterceptor.java:160)
2026-08-12T01:22:30.514442580Z  [content-service]  at org.springframework.aop.framework.ReflectiveMethodInvocation.proceed(ReflectiveMethodInvocation.java:184)
2026-08-12T01:22:30.514445366Z  [content-service]  at org.springframework.data.jpa.repository.support.CrudMethodMetadataPostProcessor$CrudMethodMetadataPopulatingMethodInterceptor.invoke(CrudMethodMetadataPostProcessor.java:165)
2026-08-12T01:22:30.514449536Z  [content-service]  at org.springframework.aop.framework.ReflectiveMethodInvocation.proceed(ReflectiveMethodInvocation.java:184)
2026-08-12T01:22:30.514452261Z  [content-service]  at org.springframework.aop.framework.JdkDynamicAopProxy.invoke(JdkDynamicAopProxy.java:223)
2026-08-12T01:22:30.514457694Z  [content-service]  at com.example.toycontent.app.feed.service.FeedCommentService.createComment(FeedCommentService.java:64)
2026-08-12T01:22:30.514469176Z  [content-service]  at org.springframework.aop.support.AopUtils.invokeJoinpointUsingReflection(AopUtils.java:359)
2026-08-12T01:22:30.514471411Z  [content-service]  at org.springframework.aop.framework.ReflectiveMethodInvocation.invokeJoinpoint(ReflectiveMethodInvocation.java:196)
2026-08-12T01:22:30.514473653Z  [content-service]  at org.springframework.aop.framework.ReflectiveMethodInvocation.proceed(ReflectiveMethodInvocation.java:163)
2026-08-12T01:22:30.514476058Z  [content-service]  at org.springframework.transaction.interceptor.TransactionAspectSupport.invokeWithinTransaction(TransactionAspectSupport.java:380)
2026-08-12T01:22:30.514478210Z  [content-service]  at org.springframework.transaction.interceptor.TransactionInterceptor.invoke(TransactionInterceptor.java:119)
2026-08-12T01:22:30.514480346Z  [content-service]  at org.springframework.aop.framework.ReflectiveMethodInvocation.proceed(ReflectiveMethodInvocation.java:184)
2026-08-12T01:22:30.514482699Z  [content-service]  at org.springframework.aop.framework.CglibAopProxy$DynamicAdvisedInterceptor.intercept(CglibAopProxy.java:727)
2026-08-12T01:22:30.514487222Z  [content-service]  at com.example.toycontent.app.feed.controller.FeedCommentController.createComment(FeedCommentController.java:53)
2026-08-12T01:22:30.514499058Z  [content-service]  at org.springframework.web.method.support.InvocableHandlerMethod.doInvoke(InvocableHandlerMethod.java:257)
2026-08-12T01:22:30.514501638Z  [content-service]  at org.springframework.web.method.support.InvocableHandlerMethod.invokeForRequest(InvocableHandlerMethod.java:190)
2026-08-12T01:22:30.514508295Z  [content-service]  at org.springframework.web.servlet.mvc.method.annotation.ServletInvocableHandlerMethod.invokeAndHandle(ServletInvocableHandlerMethod.java:118)
2026-08-12T01:22:30.514510700Z  [content-service]  at org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter.invokeHandlerMethod(RequestMappingHandlerAdapter.java:986)
2026-08-12T01:22:30.514519018Z  [content-service]  at org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter.handleInternal(RequestMappingHandlerAdapter.java:891)
2026-08-12T01:22:30.514521395Z  [content-service]  at org.springframework.web.servlet.mvc.method.AbstractHandlerMethodAdapter.handle(AbstractHandlerMethodAdapter.java:87)
2026-08-12T01:22:30.514523765Z  [content-service]  at org.springframework.web.servlet.DispatcherServlet.doDispatch(DispatcherServlet.java:1088)
2026-08-12T01:22:30.514526109Z  [content-service]  at org.springframework.web.servlet.DispatcherServlet.doService(DispatcherServlet.java:978)
2026-08-12T01:22:30.514528315Z  [content-service]  at org.springframework.web.servlet.FrameworkServlet.processRequest(FrameworkServlet.java:1014)
2026-08-12T01:22:30.514530781Z  [content-service]  at org.springframework.web.servlet.FrameworkServlet.doPost(FrameworkServlet.java:914)
2026-08-12T01:22:30.514533271Z  [content-service]  at jakarta.servlet.http.HttpServlet.service(HttpServlet.java:590)
2026-08-12T01:22:30.514535860Z  [content-service]  at org.springframework.web.servlet.FrameworkServlet.service(FrameworkServlet.java:885)
2026-08-12T01:22:30.514538379Z  [content-service]  at jakarta.servlet.http.HttpServlet.service(HttpServlet.java:658)
2026-08-12T01:22:30.514541231Z  [content-service]  at org.apache.catalina.core.ApplicationFilterChain.internalDoFilter(ApplicationFilterChain.java:195)
2026-08-12T01:22:30.514543714Z  [content-service]  at org.apache.catalina.core.ApplicationFilterChain.doFilter(ApplicationFilterChain.java:140)
2026-08-12T01:22:30.514545917Z  [content-service]  at org.apache.tomcat.websocket.server.WsFilter.doFilter(WsFilter.java:51)
2026-08-12T01:22:30.514548102Z  [content-service]  at org.apache.catalina.core.ApplicationFilterChain.internalDoFilter(ApplicationFilterChain.java:164)
2026-08-12T01:22:30.514550345Z  [content-service]  at org.apache.catalina.core.ApplicationFilterChain.doFilter(ApplicationFilterChain.java:140)
2026-08-12T01:22:30.514552382Z  [content-service]  at org.springframework.web.filter.OncePerRequestFilter.doFilter(OncePerRequestFilter.java:110)
2026-08-12T01:22:30.514554689Z  [content-service]  at org.apache.catalina.core.ApplicationFilterChain.internalDoFilter(ApplicationFilterChain.java:164)
2026-08-12T01:22:30.514556951Z  [content-service]  at org.apache.catalina.core.ApplicationFilterChain.doFilter(ApplicationFilterChain.java:140)
2026-08-12T01:22:30.514559198Z  [content-service]  at com.example.toycontent.app.auth.filter.JwtFilter.doFilterInternal(JwtFilter.java:73)
2026-08-12T01:22:30.514561422Z  [content-service]  at org.springframework.web.filter.OncePerRequestFilter.doFilter(OncePerRequestFilter.java:116)
2026-08-12T01:22:30.514563800Z  [content-service]  at org.apache.catalina.core.ApplicationFilterChain.internalDoFilter(ApplicationFilterChain.java:164)
2026-08-12T01:22:30.514582775Z  [content-service]  at org.springframework.security.web.access.ExceptionTranslationFilter.doFilter(ExceptionTranslationFilter.java:126)
2026-08-12T01:22:30.514584903Z  [content-service]  at org.springframework.security.web.access.ExceptionTranslationFilter.doFilter(ExceptionTranslationFilter.java:120)
2026-08-12T01:22:30.515123961Z  [content-service]  Caused by: org.hibernate.exception.DataException: could not execute statement [Data truncation: Data too long for column 'content' at row 1] [insert into tb_feed_comment (content,created_at,creator_id,creator_nickname,creator_profile_url,deleted,deleted_at,feed_id,parent_comment_id,updated_at) values (?,?,?,?,?,?,?,?,?,?)]
2026-08-12T01:22:30.515186442Z  [content-service]  at org.hibernate.exception.internal.SQLExceptionTypeDelegate.convert(SQLExceptionTypeDelegate.java:55)
2026-08-12T01:22:30.515190182Z  [content-service]  at org.hibernate.exception.internal.StandardSQLExceptionConverter.convert(StandardSQLExceptionConverter.java:58)
2026-08-12T01:22:30.515192855Z  [content-service]  at org.hibernate.engine.jdbc.spi.SqlExceptionHelper.convert(SqlExceptionHelper.java:108)
2026-08-12T01:22:30.515388099Z  [content-service]  at org.springframework.dao.support.PersistenceExceptionTranslationInterceptor.invoke(PersistenceExceptionTranslationInterceptor.java:138)
2026-08-12T01:22:30.515410852Z  [content-service]  at com.mysql.cj.jdbc.exceptions.SQLExceptionsMapping.translateException(SQLExceptionsMapping.java:96)
2026-08-12T01:22:30.517658662Z  [content-service]  2026-08-12 10:22:30.517 [http-nio-8082-exec-4] ERROR [traceId=6a7bcad606c9ad16a434d244e431e33d,spanId=a434d244e431e33d,userId=NONE] c.e.t.a.c.f.RequestLoggingFilter - [HTTP] POST /api/feeds/145/comments 500 - 208ms
2026-08-12T01:22:30.517658662Z  [content-service]  2026-08-12 10:22:30.517 [http-nio-8082-exec-4] ERROR [traceId=6a7bcad606c9ad16a434d244e431e33d,spanId=a434d244e431e33d,userId=NONE] c.e.t.a.c.f.RequestLoggingFilter - [HTTP] POST /api/feeds/145/comments 500 - 208ms
```

### 메트릭 시계열

| 쿼리 | series | 점 | min | max | last | 값이 0이던 구간 |
|---|---|---:|---:|---:|---:|---|
| `hikaricp_connections_active` | `{__name__=hikaricp_connections_active, application=auth-service, cluster=yogurtte-k3s-prod, container=auth-service, instance=10.42.1.51:8090, job=auth-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=auth-service-5999bb9f5c-lzp4p, pool=HikariPool-1, service=auth-service}` | 21 | 0 | 0 | 0 | **2026-08-12T01:22:00Z ~ 2026-08-12T01:27:00Z** |
| `hikaricp_connections_active` | `{__name__=hikaricp_connections_active, application=chat-service, cluster=yogurtte-k3s-prod, container=chat-service, instance=10.42.1.55:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-fdcc7c776-kh2dl, pool=HikariPool-1}` | 21 | 0 | 0 | 0 | **2026-08-12T01:22:00Z ~ 2026-08-12T01:27:00Z** |
| `hikaricp_connections_active` | `{__name__=hikaricp_connections_active, application=content-service, cluster=yogurtte-k3s-prod, container=content-service, instance=10.42.1.53:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-85f648fcff-sp24n, pool=HikariPool-1}` | 21 | 0 | 0 | 0 | **2026-08-12T01:22:00Z ~ 2026-08-12T01:27:00Z** |
| `hikaricp_connections_active` | `{__name__=hikaricp_connections_active, application=content-service, cluster=yogurtte-k3s-prod, container=content-service, instance=10.42.3.46:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-85f648fcff-v2pw9, pool=HikariPool-1}` | 21 | 0 | 0 | 0 | **2026-08-12T01:22:00Z ~ 2026-08-12T01:27:00Z** |
| `hikaricp_connections_pending` | `{__name__=hikaricp_connections_pending, application=auth-service, cluster=yogurtte-k3s-prod, container=auth-service, instance=10.42.1.51:8090, job=auth-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=auth-service-5999bb9f5c-lzp4p, pool=HikariPool-1, service=auth-service}` | 21 | 0 | 0 | 0 | **2026-08-12T01:22:00Z ~ 2026-08-12T01:27:00Z** |
| `hikaricp_connections_pending` | `{__name__=hikaricp_connections_pending, application=chat-service, cluster=yogurtte-k3s-prod, container=chat-service, instance=10.42.1.55:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-fdcc7c776-kh2dl, pool=HikariPool-1}` | 21 | 0 | 0 | 0 | **2026-08-12T01:22:00Z ~ 2026-08-12T01:27:00Z** |
| `hikaricp_connections_pending` | `{__name__=hikaricp_connections_pending, application=content-service, cluster=yogurtte-k3s-prod, container=content-service, instance=10.42.1.53:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-85f648fcff-sp24n, pool=HikariPool-1}` | 21 | 0 | 0 | 0 | **2026-08-12T01:22:00Z ~ 2026-08-12T01:27:00Z** |
| `hikaricp_connections_pending` | `{__name__=hikaricp_connections_pending, application=content-service, cluster=yogurtte-k3s-prod, container=content-service, instance=10.42.3.46:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-85f648fcff-v2pw9, pool=HikariPool-1}` | 21 | 0 | 0 | 0 | **2026-08-12T01:22:00Z ~ 2026-08-12T01:27:00Z** |
| `rate(jvm_gc_pause_seconds_sum[5m])` | `{action=end of major GC, application=chat-service, cause=Allocation Failure, cluster=yogurtte-k3s-prod, container=chat-service, gc=MarkSweepCompact, instance=10.42.1.55:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-fdcc7c776-kh2dl}` | 21 | 0 | 0 | 0 | **2026-08-12T01:22:00Z ~ 2026-08-12T01:27:00Z** |
| `rate(jvm_gc_pause_seconds_sum[5m])` | `{action=end of minor GC, application=auth-service, cause=G1 Evacuation Pause, cluster=yogurtte-k3s-prod, container=auth-service, gc=G1 Young Generation, instance=10.42.1.51:8090, job=auth-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=auth-service-5999bb9f5c-lzp4p, service=auth-service}` | 21 | 0 | 0 | 0 | **2026-08-12T01:22:00Z ~ 2026-08-12T01:27:00Z** |
| `rate(jvm_gc_pause_seconds_sum[5m])` | `{action=end of minor GC, application=auth-service, cause=Metadata GC Threshold, cluster=yogurtte-k3s-prod, container=auth-service, gc=G1 Young Generation, instance=10.42.1.51:8090, job=auth-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=auth-service-5999bb9f5c-lzp4p, service=auth-service}` | 21 | 0 | 0 | 0 | **2026-08-12T01:22:00Z ~ 2026-08-12T01:27:00Z** |
| `rate(jvm_gc_pause_seconds_sum[5m])` | `{action=end of minor GC, application=chat-service, cause=Allocation Failure, cluster=yogurtte-k3s-prod, container=chat-service, gc=Copy, instance=10.42.1.55:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-fdcc7c776-kh2dl}` | 21 | 0.000 | 0.000 | 0.000 | — |
| `rate(jvm_gc_pause_seconds_sum[5m])` | `{action=end of minor GC, application=chat-service, cause=GCLocker Initiated GC, cluster=yogurtte-k3s-prod, container=chat-service, gc=Copy, instance=10.42.1.55:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-fdcc7c776-kh2dl}` | 21 | 0 | 0 | 0 | **2026-08-12T01:22:00Z ~ 2026-08-12T01:27:00Z** |
| `rate(jvm_gc_pause_seconds_sum[5m])` | `{action=end of minor GC, application=content-service, cause=Allocation Failure, cluster=yogurtte-k3s-prod, container=content-service, gc=Copy, instance=10.42.1.53:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-85f648fcff-sp24n}` | 21 | 0 | 0.000 | 0 | **2026-08-12T01:23:45Z ~ 2026-08-12T01:27:00Z** |
| `rate(jvm_gc_pause_seconds_sum[5m])` | `{action=end of minor GC, application=content-service, cause=Allocation Failure, cluster=yogurtte-k3s-prod, container=content-service, gc=Copy, instance=10.42.3.46:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-85f648fcff-v2pw9}` | 21 | 0 | 0 | 0 | **2026-08-12T01:22:00Z ~ 2026-08-12T01:27:00Z** |
| `up` | `{__name__=up, app=node-exporter, cluster=yogurtte-k3s-prod, component=metrics, container=node-exporter, instance=ip-172-31-40-241, job=integrations/node_exporter, k8s_cluster_name=yogurtte-k3s-prod, namespace=monitoring, pod=grafana-k8s-monitoring-node-exporter-cph5l, source=kubernetes, workload=DaemonSet/grafana-k8s-monitoring-node-exporter}` | 21 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, app=node-exporter, cluster=yogurtte-k3s-prod, component=metrics, container=node-exporter, instance=ip-172-31-45-39, job=integrations/node_exporter, k8s_cluster_name=yogurtte-k3s-prod, namespace=monitoring, pod=grafana-k8s-monitoring-node-exporter-9kvqt, source=kubernetes, workload=DaemonSet/grafana-k8s-monitoring-node-exporter}` | 21 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, cluster=yogurtte-k3s-prod, container=auth-service, instance=10.42.1.51:8090, job=auth-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=auth-service-5999bb9f5c-lzp4p}` | 21 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, cluster=yogurtte-k3s-prod, container=chat-service, instance=10.42.1.55:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-fdcc7c776-kh2dl}` | 21 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, cluster=yogurtte-k3s-prod, container=content-service, instance=10.42.1.53:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-85f648fcff-sp24n}` | 21 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, cluster=yogurtte-k3s-prod, container=content-service, instance=10.42.3.46:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-85f648fcff-v2pw9}` | 21 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, cluster=yogurtte-k3s-prod, instance=10.42.1.248:8080, job=integrations/kubernetes/kube-state-metrics, k8s_cluster_name=yogurtte-k3s-prod, source=kubernetes}` | 21 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, cluster=yogurtte-k3s-prod, instance=62bd8b254df94616e43279f35eed72d3, job=integrations/cloudwatch, k8s_cluster_name=yogurtte-k3s-prod}` | 21 | 1 | 1 | 1 | — |
| `mongodb_up` | `{__name__=mongodb_up, cluster=yogurtte-k3s-prod, instance=infra-server, job=mongodb, k8s_cluster_name=yogurtte-k3s-prod}` | 21 | 1 | 1 | 1 | — |
| `kafka_brokers` | `{__name__=kafka_brokers, cluster=yogurtte-k3s-prod, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod}` | 21 | 1 | 1 | 1 | — |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=chat-service-fcm-tokens, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=0, topic=user.fcm-tokens}` | 21 | 0 | 0 | 0 | **2026-08-12T01:22:00Z ~ 2026-08-12T01:27:00Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=chat-service-fcm-tokens, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=1, topic=user.fcm-tokens}` | 21 | 0 | 0 | 0 | **2026-08-12T01:22:00Z ~ 2026-08-12T01:27:00Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=chat-service-fcm-tokens, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=2, topic=user.fcm-tokens}` | 21 | 0 | 0 | 0 | **2026-08-12T01:22:00Z ~ 2026-08-12T01:27:00Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=chat-service-notification-settings, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=0, topic=user.notification-settings}` | 21 | 0 | 0 | 0 | **2026-08-12T01:22:00Z ~ 2026-08-12T01:27:00Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=chat-service-notification-settings, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=1, topic=user.notification-settings}` | 21 | 0 | 0 | 0 | **2026-08-12T01:22:00Z ~ 2026-08-12T01:27:00Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=chat-service-notification-settings, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=2, topic=user.notification-settings}` | 21 | 0 | 0 | 0 | **2026-08-12T01:22:00Z ~ 2026-08-12T01:27:00Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=db-writer, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=0, topic=chat.messages}` | 21 | 0 | 0 | 0 | **2026-08-12T01:22:00Z ~ 2026-08-12T01:27:00Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=db-writer, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=1, topic=chat.messages}` | 21 | 0 | 0 | 0 | **2026-08-12T01:22:00Z ~ 2026-08-12T01:27:00Z** |
| `websocket_active_users` | `{__name__=websocket_active_users, application=chat-service, cluster=yogurtte-k3s-prod, container=chat-service, instance=10.42.1.55:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-fdcc7c776-kh2dl}` | 21 | 0 | 0 | 0 | **2026-08-12T01:22:00Z ~ 2026-08-12T01:27:00Z** |

값이 0이던 구간은 굵게 표시했다 — 프로세스가 사라져 시계열이 꺾인 것이 유일한 신호인 장애가 있다.

