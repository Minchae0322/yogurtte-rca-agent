# RCA Report — `scan-1786497720`

| 항목 | 값 |
|---|---|
| 모드 | rca |
| 질문 | 최근 1시간 안에 댓글 작성이 실패했다는 제보가 있다. 원인을 조사해줘 |
| 시각 | 2026-08-12T01:33:54.635747200Z |
| provider | claude-cli |
| model | `claude-opus-5` · turns 1 |
| prompt | `.\prompts\system-prompt.md` |
| tokens | in 76475 (cacheRead 23,796 · cacheCreate 52,677) / out 8231 · cost $0.7445 |
| elapsed | total 152496ms (tempo 530 · loki 373 · mimir 659 · assemble 240 · llm 137895) |

## 탐색 (Triage)

| 항목 | 값 |
|---|---|
| 시간창 해석 | 상대 표현 '최근 1시간' |
| 시간창 확신도 | EXACT |
| 스윕 창 | 2026-08-12T00:33:14.078877600Z ~ 2026-08-12T01:33:14.078877600Z |
| 좁힌 창 | 2026-08-12T01:22:00Z ~ 2026-08-12T01:27:00Z |
| 대상 | content-service |
| traceId | 6a7bcad606c9ad16a434d244e431e33d |
| 트레이스 후보 | 1건 |
| 장애 후보 | 4건 · 선택 INC-2, INC-3 |
| 계획 파싱 | 성공 |
| 스윕 컨텍스트 | **후보만 — 원본 제외 (B)** |
| prompt | `.\prompts\triage-prompt.md` |
| tokens | in 32928 / out 1847 · cost $0.1766 |
| chars | 컨텍스트 2,166 + 프롬프트 1,399 = **3,565** |
| elapsed | survey 2879ms · llm 37344ms |

**선정 이유**: 댓글 작성 엔드포인트의 error 트레이스(INC-3)와 같은 시각·같은 서비스의 DataException 반복(INC-2)이 질문의 증상·시각과 정확히 맞고, 둘은 한 장애의 트레이스 지문과 로그 지문일 가능성이 높아 함께 골랐다.

**근거**

- INC-3: content-service http post /feeds/{feedId}/comments 가 error 채널, 208ms, traceId 6a7bcad606c9ad16a434d244e431e33d — 질문의 '댓글 작성 실패'와 엔드포인트가 정확히 일치
- INC-2: content-service ERROR/WARN 4건(01:22~01:23)이 INC-3 트레이스와 같은 분에 발생 — 로그와 트레이스가 같은 요청을 가리킬 가능성이 높다
- INC-2: 원인 예외 org.hibernate.exception.DataException 이 01:22~01:27 사이 x5회, 평균 60초 간격 — DB 영속화 단계 실패가 5분간 반복됨(단발 아님)
- Tempo 지연 검색 '{duration > 3s && status != error}' 0건 — 느려진 장애가 아니라 즉시 실패(208ms 종료)하는 유형임을 뒷받침
- min_over_time(up[1m]), mongodb_up, kafka_brokers, kafka_consumergroup_lag 모두 이상 신호 0건 — 프로세스 다운/인프라 단절이 아니라 애플리케이션-DB 계층 문제로 범위가 좁혀짐

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

- INC-1 — 01:22~01:23으로 시각은 겹치지만 chat-service ERROR/WARN 1건뿐이고 댓글 작성 경로(content-service)의 상·하류로 볼 근거가 없으며, websocket_active_users 이상 0건이라 chat 계열 장애 징후도 없다 — INC-2 창에 포함되므로 필요 시 조사 중 함께 보인다.
- INC-4 — auth-service NullPointerException은 01:31~01:33으로 댓글 실패 시각(01:22)보다 9분 뒤이고, 그 시각에 댓글 엔드포인트 트레이스 오류가 없어 이번 제보와 시각이 맞지 않는다 — 별건으로 남겨 둔다.

**보정 기록**

- 창을 후보 [INC-2, INC-3] 의 신호 시각에서 계산했다 (2026-08-12T01:22:00Z ~ 2026-08-12T01:27:00Z)

**스윕 무신호/실패**

- Tempo 지연 검색 '{ duration > 3s && status != error }'이 이 창에서 0건이다. 임계값보다 느린 요청이 없었다는 뜻이다.

## 수집 범위 (Coverage)

- **window**: 2026-08-12T01:22:00Z ~ 2026-08-12T01:27:00Z (300s)
- **trace**: 59,185B / 59 spans
- **창 안 후보 트레이스**: 10건 / 59,185B
- **logs**: errwarn=37,139B · traceId=6,329B
- **metrics**: 8 수집 / 54,134B, 누락 [sum(rate(http_server_requests_seconds_count{application="content-service", status="401"}[1m]))]
- **context**: 84,658 chars (+ 시스템 프롬프트 805 chars)

### 토큰 축 (개선 지표)

| 단계 | 총 in | chars | 컨텍스트 토큰 |
|---|---:|---:|---:|
| 탐색 | 32,928 | 3,565 | 2,918 |
| 분석 | 76,475 | 85,463 | 46,465 |
| **합계** | **109,403** | | **49,383** |

- **overheadTokens 30,010 tok — 이 회차에 실측**(1자 프롬프트 프로브, 같은 명령·모델·샌드박스).
  호출마다 한 번씩 얹히므로 단계별로 뺀다. **다른 날 상수를 쓰지 않았다 = `█ 실측`.**
- contextTokens (count_tokens API): 측정 안 됨 — 구독 CLI 경로엔 API 키가 없다
- 비용은 **API 환산 추정치**다. 구독 계정으로 호출하므로 실제 청구액이 아니다.

## 수집 실패/누락

- Metric 'sum(rate(http_server_requests_seconds_count{application="content-service", status="401"}[1m]))' returned no series in this window; skipped.

---

# 장애 분석 — scan-1786497720

**결론 요약:** `POST /api/feeds/145/comments` 가 MySQL `tb_feed_comment.content` 컬럼 길이 초과(SQL Error 1406, SQLState 22001)로 롤백되어 500을 반환했다. 인프라(Hikari·Kafka·Redis·Mongo·GC·pod) 지표는 전 구간 정상이다.

## 1. 원인 후보 랭킹 (최대 3개)

1. **애플리케이션 계층의 댓글 본문 길이 검증 부재** — 검증 없이 INSERT까지 내려가 DB가 데이터 절단 오류로 거부
2. **예외 → HTTP 상태 매핑 결함** — 클라이언트 입력 오류(4xx 성격)를 500 SERVER_ERROR로 반환 (피해 확대 요인)
3. **`tb_feed_comment.content` 컬럼 폭이 제품이 허용하는 본문 길이보다 작음** (스키마/스펙 불일치)

---

## 2. 후보별 근거

### 후보 1. 애플리케이션 계층 길이 검증 부재

- **근거**
  - trace `6a7bcad606c9ad16a434d244e431e33d`, span `query`(spanId `erj2M1H8Yxs=`, 시작 01:22:30.357Z, 112.4ms)에 `status.code=STATUS_CODE_ERROR`, 속성 `error: "Data truncation: Data too long for column 'content' at row 1"`, 쿼리 `insert into tb_feed_comment (content,created_at,creator_id,...)`.
  - Loki WARN 원문: `SQL Error: 1406, SQLState: 22001` (10:22:30.470 KST = 01:22:30.470Z, `traceId=6a7bcad606c9ad16a434d244e431e33d, userId=1`).
  - 예외 체인 원문: `org.springframework.dao.DataIntegrityViolationException: could not execute statement [Data truncation: Data too long for column 'content' at row 1]` → `Caused by: org.hibernate.exception.DataException` → `Caused by: com.mysql.cj.jdbc.exceptions.MysqlDataTruncation`.
  - 스택 프레임에 앱 코드 `FeedCommentService.createComment(FeedCommentService.java:64)`, `FeedCommentController.createComment(FeedCommentController.java:53)`. **컨트롤러/서비스 어디에서도 길이 위반이 걸러지지 않고 Hibernate flush 시점에 DB가 거부**했다 — 즉 서버 측 사전 검증이 없다.
  - 실패 직전 흐름은 정상: `select ... from tb_feed f1_0 where f1_0.id=?` (row-count 1), redis `GET` 0.535ms, `[user-cache] 캐시 HIT - userId: 1, elapsed: 1ms`. 인증도 통과(`userId=1`). 실패 지점은 오직 INSERT 한 곳.
- **대기·지연 구간 판정**
  - JDBC connection span `GxhhZcVeFQI=`: `acquired` 01:22:30.325543Z → `rollback` 01:22:30.499223Z, 커넥션 보유 173.7ms, span 전체 193.1ms. **상한 대조: HikariCP `connection-timeout`/트랜잭션 타임아웃 설정값을 수집하지 못해 만료 여부는 판정 불가.** 다만 `hikaricp_connections_pending`이 전 구간 0이므로 획득 대기로 인한 만료는 아니다.
  - **해당 작업의 최종 상태: 실패 → 롤백 → 폐기.** 근거: connection span 이벤트가 `commit`이 아닌 `rollback`, 같은 트레이스에 재시도 INSERT span 없음, HTTP span `outcome: SERVER_ERROR, status: 500`, 그리고 성공 트레이스에 존재하는 `publish user.notifications` span이 이 트레이스에는 **없음**(알림도 발행되지 않음).
- **확신도: 높음**
- **반증 데이터: 없음.** 다만 관측된 실패 사례는 이 1건뿐이며(조회 창 5분 내 ERROR 로그 1벌), 제보가 말하는 "최근 1시간" 전체 실패 건수는 확인되지 않았다.

### 후보 2. 예외 → HTTP 상태 매핑 결함 (500 반환)

- **근거**
  - Loki: `c.e.t.a.c.e.GlobalExceptionHandler - [api-error] handleAllException` (01:22:30.501Z). 입력 무결성 위반이 전용 핸들러가 아닌 **포괄 핸들러**로 떨어졌다.
  - 그 결과 `RequestLoggingFilter - [HTTP] POST /api/feeds/145/comments 500 - 208ms`, HTTP span 속성 `status: "500"`, `outcome: "SERVER_ERROR"`.
  - 클라이언트 입력 길이 초과는 재시도해도 동일하게 실패하는 결정적 오류인데 5xx로 표기되면 클라이언트/게이트웨이 재시도 로직과 에러 알람을 오염시키고, 사용자에게는 "서버 장애"로 보인다. 실제 제보("댓글 작성 실패")와 일치.
- **대기·지연 구간 판정:** 해당 없음(대기 구간 아님). 요청 총 소요 208.897ms, 최종 상태 **실패(500 응답 반환 완료, 재시도 없음)**.
- **확신도: 높음** (다만 이는 근본 원인이 아니라 후보 1의 노출 방식이다)
- **반증 데이터: 없음.**

### 후보 3. `content` 컬럼 폭이 제품 스펙보다 작음 (스키마 불일치)

- **근거**
  - 동일 오류 메시지가 컬럼 정의 초과를 가리킨다: `Data too long for column 'content' at row 1`.
  - 후보 1과 **관측 증거가 동일**하며, 둘을 가르는 데이터(= `tb_feed_comment` DDL의 `content` 길이, 실제 요청 본문 바이트 길이, 프론트 `maxlength`)는 이번 수집에 **없다**. 따라서 "검증만 추가하면 되는가" vs "컬럼을 넓혀야 하는가"는 현 데이터로 결정할 수 없다 — **데이터 부족**.
  - 추가 수집 필요: `SHOW CREATE TABLE tb_feed_comment`, 해당 요청의 본문 길이(문자 수/바이트 수), 컬럼 charset(utf8mb4면 이모지 1자 = 4바이트), 댓글 최대 길이 제품 스펙, 최근 스키마·배포 변경 이력.
- **대기·지연 구간 판정:** 해당 없음(대기 구간 아님).
- **확신도: 낮음**
- **반증 데이터: 없음** (배제할 근거도, 확정할 근거도 없음)

### 후보에서 배제한 것들 (모두 반증됨)

- **커넥션 풀 고갈/DB 부하**: `hikaricp_connections_active`, `hikaricp_connections_pending` 4개 인스턴스 전부 01:22:00~01:27:00 전 구간 **0**. 실패 트레이스에서도 `acquired`까지 1.6ms.
- **서비스/인프라 다운**: `up` 전 시리즈 1, `mongodb_up` 1, `kafka_brokers` 1, 전 구간 변화 없음.
- **Kafka 적체/유실**: `kafka_consumergroup_lag` 전 구간 0(또는 -1=미할당 파티션). `user.notifications.dlq` lag 0 → 폐기 큐 유입 없음.
- **GC/메모리**: content-service GC pause rate 최대 1.67e-5, 01:23:45Z 이후 0. chat-service도 3e-4 수준.
- **인증 문제**: 실패 트레이스 필터 체인 12/12 통과, `userId=1`로 진입, 캐시 HIT 로그 존재. 단 `status="401"` 메트릭은 **시리즈 없음으로 수집 실패** → 401 발생률은 확인 불가(아래 확신도 감안 사항).
- **엔드포인트 전면 장애 아님**: 60초 뒤 `6a7bcb1282936b27a58ef575b8e0baf2`, 동일 `POST /api/feeds/145/comments` → **200 SUCCESS, 64.4ms**, INSERT `row-affected 1`, `generated-keys 1469`, `commit`, `publish user.notifications` 정상. 즉 코드/스키마 상태가 아니라 **요청 본문에 따라 갈리는** 실패다 — 후보 1·3을 지지하고 인프라 원인을 반증한다.
- **알림 파이프라인 지연(참고)**: 성공 트레이스의 chat-service `receive` 407.2ms / connection 406.4ms / `push-dispatcher#dispatch` 380.1ms. **상한 대조: Redis `server.address=redis://172.31.46.124?timeout=2s`에 대해 실측 `KEYS` 0.492·0.533ms → 만료 아님.** Kafka 리스너의 `max.poll.interval.ms` 등 상한값은 미수집이라 **만료 여부 판정 불가**. **최종 상태: 성공** (connection 이벤트 `commit` 01:23:31.378915Z, consumer lag 0). 댓글 실패와는 무관한 별개 트레이스다.

**수집 공백에 따른 확신도 조정:** ① content-service 401 메트릭 시리즈 없음, ② 조회 창이 제보의 "1시간"이 아닌 **5분(01:22~01:27)**, ③ 스키마 DDL·요청 본문 길이 미수집. 따라서 "이 메커니즘이 실패의 원인"은 높음이지만, **"1시간 동안의 모든 실패가 이 원인"은 중간**으로 낮춘다.

---

## 3. 권장 다음 조치

### 이미 발생한 피해: 복구 가능한가

- **피해 범위(확인된 것):** userId=1의 feedId=145 댓글 작성 1건 실패(01:22:30.517Z, 500). 알림 미발행.
- **서버 측 자동 복구: 불가능.** 근거: (a) 트랜잭션이 `rollback`으로 종료되어 DB에 부분 데이터가 없다 — `tb_feed_comment` INSERT, `tb_feed.comment_count` 갱신, `tb_exp_history` 적립 모두 미반영이며 **정합성 훼손은 없다**. (b) 요청 본문이 어디에도 보존되지 않았다(로그·트레이스에 payload 없음, Kafka 미발행, DLQ lag 0). 즉 재처리할 원본이 존재하지 않는다.
- **유일한 복구 경로:** 해당 사용자의 재작성. 후보 3이 사실로 확인되면(컬럼이 스펙보다 좁음) 컬럼 확장 후 재작성해야 원래 길이로 저장된다.
- **먼저 할 일:** 조회 창을 실제 제보 구간(최근 1시간)으로 넓혀 `SQL Error: 1406` / `Data too long for column 'content'` 로그를 전수 조사해 영향 사용자 목록을 확정할 것. 현재 5분 창으로는 1건만 확인된다.

### 재발 방지

1. **원인 확정 먼저:** `SHOW CREATE TABLE tb_feed_comment`로 `content` 길이·charset 확인 → 스펙보다 좁으면 컬럼 확장(예: `TEXT`/충분한 `VARCHAR`), 스펙과 같으면 검증만 추가.
2. **입력 검증 추가:** 댓글 생성 DTO에 `@Size(max=…)`(= 컬럼 길이와 동일 값) + 컨트롤러 `@Valid`. `FeedCommentController.createComment:53` / `FeedCommentService.createComment:64` 경로가 DB까지 내려가기 전에 거르도록.
3. **예외 매핑 수정:** `GlobalExceptionHandler`에 `DataIntegrityViolationException`(및 `MethodArgumentNotValidException`) 전용 핸들러를 추가해 400 + 사유 응답. `handleAllException` 포괄 처리에서 빠지게 한다.
4. **클라이언트 방어:** 입력창 `maxlength` 및 잔여 글자 수 표시 — 단, 서버 검증을 대체하지 않는다(신뢰 경계는 서버).
5. **관측 보강:** `http_server_requests_seconds_count{application="content-service", status="500"}` 기반 5xx 알람 추가. 이번 조사에서 401 메트릭이 시리즈 없음으로 스킵된 것처럼, 상태코드별 시리즈가 실제로 생성되는지 함께 점검할 것.

### 복구 확인

- 경계값 회귀 테스트: 컬럼 한계 길이 정확히 = N, N+1, 그리고 이모지/멀티바이트 포함 본문으로 `POST /api/feeds/{feedId}/comments` 호출 → **N은 200 저장, N+1은 400**(500 아님)임을 확인.
- Loki에서 `SQL Error: 1406` / `Data too long for column 'content'` / `handleAllException` 발생 **0건** (배포 후 최소 1시간).
- 성공 경로 재확인: 트레이스에 `insert into tb_feed_comment` → `row-affected 1`, `generated-keys` 발급, connection span `commit`, `publish user.notifications` span 존재 (정상 트레이스 `6a7bcb12…`와 동일 형태).
- `kafka_consumergroup_lag{topic="user.notifications"}` 및 `user.notifications.dlq` lag 0 유지, `hikaricp_connections_pending` 0 유지 확인.

---

## 관측 증거 (Evidence)

> 이 조사가 실제로 본 값이다. 원본 응답 전체는 `reports/raw/scan-1786497720-*.json`에 있다.

### 호출 그래프 (트레이스에서 추출)

```
chat-service --db--> mongodb  7회  최대 1.5ms  [insert, find]
chat-service --db--> redis  2회  최대 0.5ms  [KEYS]
content-service --db--> redis  3회  최대 0.5ms  [GET, INFO]
chat-service --jdbc--> mysql/content (HikariPool-1)  1회  최대 406.4ms
    events: acquired, commit
content-service --jdbc--> mysql/content (HikariPool-1)  22회  최대 193.1ms
    error: Data truncation: Data too long for column 'content' at row 1
    events: acquired, rollback, commit
content-service --messaging--> kafka/user.notifications  1회  최대 16.8ms  [publish]
kafka/user.notifications --messaging--> chat-service  1회  최대 407.2ms  [receive]
```

### span (duration 상위 15 / 전체 59)

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
| 5.64 | chat-service | `secured request` | 2026-08-12T01:23:07.139720Z |
| 5.03 | content-service | `secured request` | 2026-08-12T01:23:08.239104Z |
| 3.71 | content-service | `query` | 2026-08-12T01:22:30.327680Z |

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

