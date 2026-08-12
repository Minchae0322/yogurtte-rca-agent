# RCA Report — `scan-1786497720`

| 항목 | 값 |
|---|---|
| 모드 | rca |
| 질문 | 최근 1시간 안에 댓글 작성이 실패했다는 제보가 있다. 원인을 조사해줘 |
| 시각 | 2026-08-12T02:45:53.458633700Z |
| provider | claude-cli |
| model | `claude-opus-5` · turns 1 |
| prompt | `.\prompts\system-prompt.md` |
| tokens | in 103090 (cacheRead 23,796 · cacheCreate 79,292) / out 9662 · cost $1.0464 |
| elapsed | total 161292ms (tempo 494 · loki 227 · mimir 579 · assemble 86 · llm 148249) |

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
| tokens | in 32913 / out 1475 · cost $0.1671 |
| chars | 컨텍스트 2,143 + 프롬프트 1,399 = **3,542** |
| elapsed | survey 1772ms · llm 32748ms |

**선정 이유**: 댓글 작성 엔드포인트의 에러 트레이스(INC-3)와 같은 시각 content-service의 DataException(INC-2)은 한 장애의 상·하류 지문이므로 함께 골라 조사 창을 합친다.

**근거**

- INC-3: content-service http post /feeds/{feedId}/comments 트레이스가 error 채널로 종료 (2026-08-12T01:22:30.309754Z, 208ms, traceId 6a7bcad606c9ad16a434d244e431e33d) — 질문의 '댓글 작성' 엔드포인트와 정확히 일치
- INC-2: 같은 분(01:22~01:23) content-service ERROR/WARN 4건 + 원인 예외 org.hibernate.exception.DataException 1건 — DB 쓰기 실패 계열이라 '작성 실패' 증상과 인과가 맞음
- INC-2의 DataException이 01:22~01:27 구간에 x5회, 평균 60초 간격으로 반복 — 일회성 노이즈가 아니라 지속 중인 실패
- 208ms 종료 + Tempo 지연 검색(duration>3s) 0건 — 타임아웃/포화가 아니라 애플리케이션 레벨에서 즉시 거부된 실패임을 뒷받침
- up/mongodb_up/kafka_brokers/consumergroup_lag/websocket_active_users 이상 신호 0건 — 인프라·플랫폼 장애가 아니라 content-service 코드/데이터 경로 문제로 좁혀짐

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

- INC-1 — 시각은 겹치지만 chat-service는 댓글 작성 경로에 있지 않고 ERROR/WARN 1건뿐이라 증상과 연결할 근거가 없다 — content-service 쪽에서 원인이 안 나오면 다음 후보로 되짚을 것.
- INC-4 — auth-service NullPointerException은 01:31~01:33으로 댓글 실패 시각(01:22)보다 9분 뒤이고, 인증 실패였다면 트레이스가 401/403 계열로 auth 쪽에 남았어야 하므로 별개 이슈로 본다.

**보정 기록**

- 창을 후보 [INC-2, INC-3] 의 신호 시각에서 계산했다 (2026-08-12T01:22:00Z ~ 2026-08-12T01:27:00Z)

**스윕 무신호/실패**

- Tempo 지연 검색 '{ duration > 3s && status != error }'이 이 창에서 0건이다. 임계값보다 느린 요청이 없었다는 뜻이다.

## 수집 범위 (Coverage)

- **window**: 2026-08-12T01:22:00Z ~ 2026-08-12T01:27:00Z (300s)
- **trace**: 136,608B / 147 spans (상위 span만)
- **창 안 후보 트레이스**: 10건 / 136,608B
- **logs**: errwarn=37,143B · traceId=6,333B
- **metrics**: 8 수집 / 54,134B, 누락 [sum(rate(http_server_requests_seconds_count{application="content-service", status="401"}[1m]))]
- **context**: 135,971 chars (+ 시스템 프롬프트 805 chars)

### 토큰 축 (개선 지표)

| 단계 | 총 in | chars | 컨텍스트 토큰 |
|---|---:|---:|---:|
| 탐색 | 32,913 | 3,542 | 2,903 |
| 분석 | 103,090 | 136,776 | 73,080 |
| **합계** | **136,003** | | **75,983** |

- **overheadTokens 30,010 tok — 이 회차에 실측**(1자 프롬프트 프로브, 같은 명령·모델·샌드박스).
  호출마다 한 번씩 얹히므로 단계별로 뺀다. **다른 날 상수를 쓰지 않았다 = `█ 실측`.**
- contextTokens (count_tokens API): 측정 안 됨 — 구독 CLI 경로엔 API 키가 없다
- 비용은 **API 환산 추정치**다. 구독 계정으로 호출하므로 실제 청구액이 아니다.

## 수집 실패/누락

- Metric 'sum(rate(http_server_requests_seconds_count{application="content-service", status="401"}[1m]))' returned no series in this window; skipped.

---

## 1. 원인 후보 랭킹 (최대 3개)

1. **댓글 본문 길이가 `tb_feed_comment.content` 컬럼 최대 길이를 초과 → MySQL 1406(Data truncation) → 트랜잭션 롤백 → HTTP 500** (애플리케이션 계층 길이 검증 부재)
2. **컬럼 정의 쪽 문제 (content 길이가 서비스 정책보다 짧게 정의/축소됨) — 정상 길이 입력이 잘리는 경우**
3. **chat-service 알림 소비 경로의 자원 이상 (JDBC 커넥션 1050ms 점유 + Redis `Connection reset by peer`)** — 댓글 실패와는 인과 없음

---

## 2. 후보별 근거

### 후보 1 — 입력 길이 초과 + 검증 부재

**근거**
- 실패 트레이스 `6a7bcad606c9ad16a434d244e431e33d`, span `http post /feeds/{feedId}/comments` (`http.url: /api/feeds/145/comments`) → `"status":"500"`, `"outcome":"SERVER_ERROR"`, 208.897ms.
- 그 안의 `query` span (start 10:22:30.357, **112.422ms**)에 `"error":"Data truncation: Data too long for column 'content' at row 1"`, `status.code: STATUS_CODE_ERROR`. SQL은 `insert into tb_feed_comment (content,...) values (...)`.
- 로그 원문:
  - `WARN ... o.h.e.jdbc.spi.SqlExceptionHelper - SQL Error: 1406, SQLState: 22001` (10:22:30.470)
  - `ERROR ... SqlExceptionHelper - Data truncation: Data too long for column 'content' at row 1` (10:22:30.471)
  - `org.springframework.dao.DataIntegrityViolationException: could not execute statement [Data truncation: Data too long for column 'content' at row 1]` → `at com.example.toycontent.app.feed.service.FeedCommentService.createComment(FeedCommentService.java:64)` → `FeedCommentController.createComment(FeedCommentController.java:53)`
  - `ERROR ... RequestLoggingFilter - [HTTP] POST /api/feeds/145/comments 500 - 208ms` (10:22:30.517), `userId=1`
- 22001(String data right truncation)은 **DB/인프라 고장이 아니라 값 자체가 컬럼 폭을 넘었다**는 뜻이다. 컨트롤러→서비스까지 예외 없이 도달한 뒤 DB에서 처음 거부된 것 = 앱 계층에 길이 검증이 없다는 근거(`JwtFilter`, `JwtAuthenticationFilter` 모두 통과, `exception:"none"`으로 인증은 정상).
- 같은 시간창 동일 엔드포인트·동일 피드(145) 요청 3건은 모두 성공: `6a7bcaca7bb66c4bc353eaf51adbf13a`(200, 412ms), `6a7bcb1282936b27a58ef575b8e0baf2`(200, 64ms), `6a7bcb133ec0e6e398823dbb96b1afb0`(200, 131ms). → 전면 장애가 아니라 **요청 페이로드 의존적 실패**.
- 부수 효과 없음의 증거: `tb_feed_comment` generated-keys가 1468(10:22:18) → 1469(10:23:30) → 1470(10:23:31)로 **연속**, `tb_exp_history`도 529 → 530 → 531 연속. 실패 요청은 auto-increment를 소모하지 않았다.

**확신도: 높음**

**반증 데이터**: 없음. (다만 `content` 컬럼의 선언 길이와 실제 전송된 본문 길이는 수집 데이터에 없어, "입력이 과했는지"와 "컬럼이 짧은지"는 후보 2와 분리 판정 불가.)

**대기·지연 구간 판정**
| 구간 | 실측 | 상한(설정값) | 만료 여부 | 최종 상태 |
|---|---|---|---|---|
| Hikari 커넥션 획득 (`connection` span start .323947 → `acquired` .325543) | **1.596ms** | `connectionTimeout` 미확보 | 만료 아님(획득 성공). `hikaricp_connections_pending` 전 구간 0, `active` 전 구간 0으로 대기 정황 없음 | **성공** |
| INSERT 실행 | **112.422ms** | 쿼리 타임아웃 설정 미확보 → 만료 여부 **판정 불가** | 타임아웃이 아니라 SQLSTATE 22001로 즉시 거부 | **실패** |
| 트랜잭션 (`connection` span 193.108ms) | `acquired` .325543 → `rollback` **.499223** | — | — | **롤백(폐기)**. 같은 traceId 내 재시도 span 없음 → 재시도 없이 폐기, 댓글·경험치·`comment_count` 모두 미반영 |
| 캐시 조회 | `[user-cache] 캐시 HIT - userId: 1, elapsed: 1ms` | — | — | 성공 |

---

### 후보 2 — 컬럼 길이 정의가 서비스 정책보다 짧음

**근거**
- 후보 1과 동일한 오류(1406/22001)는 "값이 길다"와 "컬럼이 짧다"를 구분하지 못한다. 수집 데이터에는 `tb_feed_comment.content`의 DDL(선언 길이)도, 실패 요청의 본문 길이도 **없다**.
- 인접 성공 트레이스들의 INSERT는 `jdbc.row-affected: 1`로 정상 → 짧은 본문은 통과. 즉 경계값이 존재하며, 그 경계가 어디인지가 두 후보를 가른다.
- **데이터 부족**: 판정하려면 `SHOW CREATE TABLE tb_feed_comment`(또는 information_schema의 `CHARACTER_MAXIMUM_LENGTH`), 최근 스키마 마이그레이션 이력, 실패 요청의 본문 바이트 길이(요청 로깅에 없음)가 필요하다.

**확신도: 낮음**

**반증 데이터**: 직접적 반증은 없으나, 동일 창의 댓글 요청 4건 중 3건이 성공했다는 점은 "컬럼이 비정상적으로 짧게 축소됐다"는 가설과 잘 맞지 않는다(축소됐다면 실패 비율이 더 높을 것으로 기대). 다만 요청 본문 길이 분포를 모르므로 배제까지는 못 한다.

**대기·지연 구간 판정**: 해당 없음(대기 구간이 아니라 스키마 정의 문제).

---

### 후보 3 — chat-service 알림 경로의 커넥션 점유 / Redis 리셋

**근거**
- 트레이스 `6a7bcaca7bb66c4bc353eaf51adbf13a`의 chat-service 배치: `receive`(kafka `user.notifications`, offset 1159) **1051.608ms**, 그 하위 `connection`(HikariPool-1, datasource `content`) **1050.045ms** — `acquired` 10:22:19.157519 → `commit` 10:22:20.204045. 호출 그래프에도 `chat-service --jdbc--> mysql/content 3회 최대 1050.0ms`로 집약돼 있다.
- 같은 트레이스에서 `KEYS 0` span에 `"lettuce.command.error":"recvAddress(..) failed: Connection reset by peer"` (`server.address: redis://172.31.46.124?timeout=2s`), 발생 위치는 `UserNotificationWebSocketSender.sendNotification`.
- 즉 알림 발송 경로에 (a) 컨슈머 스레드가 JDBC 커넥션을 1초 이상 잡고 있는 구조, (b) Redis 커넥션 리셋이 실재한다.

**확신도: 낮음** (댓글 실패의 원인으로서)

**반증 데이터**
- 이 경로는 **댓글 저장 이후** 단계다: 실패 트레이스에는 `notification-publish`·`publish user.notifications` span이 **아예 없다** — 롤백으로 알림 단계에 도달조차 못 했다. 인과 방향이 반대.
- `hikaricp_connections_pending`이 4개 인스턴스 전 구간 0, `hikaricp_connections_active`도 전 구간 0 → 풀 고갈 정황 없음.
- `kafka_consumergroup_lag{consumergroup="notification-processors", topic="user.notifications"}` 파티션 0~5 전 구간 0, `user.notifications.dlq` 랙도 0 → 알림 처리 적체·데드레터 없음.
- `up` 전 시리즈 1, `mongodb_up` 1, `kafka_brokers` 1, GC pause는 chat-service minor GC가 1.4e-4 → 3.0e-4 s/s 수준(무시 가능). 인프라 이상 없음.

**대기·지연 구간 판정**
| 구간 | 실측 | 상한(설정값) | 만료 여부 | 최종 상태 |
|---|---|---|---|---|
| Redis `KEYS 0` (span `aGg7zP4hfaQ=`) | **0.580ms** | `timeout=2s` (span 속성 `server.address`에 명시) | **미만료** — 타임아웃이 아니라 연결 리셋으로 즉시 실패 | **실패 후 폐기**. 동일 트레이스에 재시도 span 없음. 다만 상위 `push-dispatcher#dispatch`는 계속 진행(이후 mongo `fcm_tokens.find` 등 수행)했고 상위 `connection`은 `commit` → 알림 트랜잭션 자체는 성공 |
| Kafka `receive` (offset 1159) | **1051.608ms** | `max.poll.interval.ms` 미확보 → 만료 여부 **판정 불가** | — | **성공**. `commit` 이벤트 존재, 컨슈머 랙 0, DLQ 랙 0 → 리밸런스·재처리·데드레터 없음 |
| chat-service JDBC 커넥션 점유 | **1050.045ms** | `connectionTimeout` 미확보 | 획득 대기는 1.565ms로 사실상 없음 | **성공(commit)** |
| content-service → auth-service `http get /api/external/users/1` | **31.426ms** | 미확보 | — | **성공** (`status: 200`) |

---

## 3. 권장 다음 조치

### 이미 발생한 피해 — 복구 가능한가
**서버 측 복구 불가. 사용자 재작성만이 유일한 복구 경로.**
- 근거: `connection` span에 `rollback`(10:22:30.499)만 있고 `commit`이 없다. `tb_feed_comment` generated-keys 1468→1469→1470, `tb_exp_history` 529→530→531이 **모두 연속** → 실패 요청은 행을 쓰지 않았고 auto-increment도 소모하지 않았다. 부분 커밋·고아 레코드·`tb_feed.comment_count` 오증가 같은 정합성 피해는 **없다**(정리 작업 불필요).
- 반면 **댓글 본문 자체는 어디에도 남아있지 않다**: 요청 body를 기록하는 로그가 없고(`RequestLoggingFilter`는 메서드·경로·상태·소요시간만 출력), 트레이스의 `jdbc.query[0]`는 바인드 파라미터를 담지 않는다. → 서버가 대신 재저장할 수 없다. 해당 사용자(`userId=1`)에게 재작성 안내가 필요.
- 피해 범위는 **판정 불가**: 관측된 실패는 5분 창(01:22~01:27) 내 1건뿐이고, 제보가 가리키는 "최근 1시간" 전체의 실패 건수를 셀 메트릭은 수집되지 않았다(`status="401"` 시리즈만 조회했고 그마저 결측). 아래 "추가 수집" 참조.

### 재발 방지
1. **입력 검증을 DB 앞으로 당긴다** — 댓글 생성 DTO에 `@Size(max=<content 컬럼 길이>)`를 붙이고 `FeedCommentController.createComment`(FeedCommentController.java:53)에 `@Valid` 적용. 사용자 입력 오류가 500이 아니라 **400**으로 나가게 한다.
2. **예외 매핑 보정** — `GlobalExceptionHandler`가 현재 이 케이스를 `handleAllException`(catch-all)으로 잡아 500을 반환하고 있다(로그 원문: `[api-error] handleAllException`). `DataIntegrityViolationException` / SQLState 22001은 400 계열로 매핑.
3. **컬럼 길이 확정** — `SHOW CREATE TABLE tb_feed_comment` 확인 후, 서비스 정책 길이보다 짧으면 `TEXT`/`VARCHAR` 확장 마이그레이션. 클라이언트 입력창 maxlength도 동일 값으로 맞춰 서버 도달 전에 차단.
4. **관측 공백 메우기** — `http_server_requests_seconds_count{application="content-service", uri="/feeds/{feedId}/comments", status="500"}` 알림 룰 추가, `RequestLoggingFilter`에 요청 본문 **길이**(내용 아님) 기록 추가. 이번 조사에서 실패 건수를 셀 수 없었던 원인이다.
5. (별건, 낮은 우선순위) chat-service 알림 컨슈머가 JDBC 커넥션을 1050ms 점유하는 구조와 Redis `Connection reset by peer` — 현재는 랙 0·풀 pending 0으로 무해하나, 트래픽 증가 시 먼저 터질 지점.

### 복구 확인
1. **재현 테스트**: 컬럼 한계 **초과** 본문으로 `POST /api/feeds/{feedId}/comments` → **400** 반환, 로그에 `SQLState: 22001` **미출현**. 한계 **이하** 본문 → **200** + `tb_feed_comment` 행 1건 + `tb_feed.comment_count` +1.
2. **로그 확인**: Loki에서 `SQL Error: 1406` / `Data too long for column 'content'` / `handleAllException` 0건 (배포 후 최소 1시간).
3. **트레이스 확인**: 신규 댓글 트레이스의 `connection` span 이벤트가 `acquired` → **`commit`**(rollback 아님)인지, `notification-publish` → `publish user.notifications` span까지 이어지는지.
4. **정합성 확인**: `tb_feed_comment`·`tb_exp_history` id 연속성과 `tb_feed.comment_count`가 실제 댓글 수와 일치하는지 대조(현재는 이미 일치 상태이므로 회귀 감지용 기준선).

### 결론의 확신도 조정 (수집 실패 반영)
- `status="401"` 메트릭이 결측이라 **인증 계열 원인은 메트릭으로 배제하지 못했다**. 다만 실패 트레이스가 필터체인 12단을 모두 통과하고 `userId=1`로 서비스 코드까지 진입했으므로, 트레이스 근거만으로 인증 문제는 배제 가능(확신도 높음).
- 조회 창이 **5분(01:22~01:27)**뿐이라 "최근 1시간" 제보의 전체 규모·다른 실패 유형 존재 여부는 커버되지 않는다. **추가 수집 필요**: ① 1시간 전 구간의 content-service 5xx 카운트(uri별), ② `tb_feed_comment.content` DDL, ③ 실패 요청들의 본문 길이 분포, ④ 영향받은 distinct userId 수.

skipped: 코드 수정·쿼리 실행 없음(조사 대상 저장소가 이 세션에 없음). 위 1~3번은 실제 리포지토리에서 `FeedCommentController.java:53` / `FeedCommentService.java:64` / `GlobalExceptionHandler`를 열면 각각 한 줄~수 줄 diff로 끝난다.

---

## 관측 증거 (Evidence)

> 이 조사가 실제로 본 값이다. 원본 응답 전체는 `reports/raw/scan-1786497720-*.json`에 있다.

### 호출 그래프 (트레이스에서 추출)

```
chat-service --db--> mongodb  21회  최대 4.3ms  [find, insert]
chat-service --db--> redis  8회  최대 1.1ms  [KEYS, CLIENT]
content-service --db--> redis  6회  최대 1.3ms  [GET, SET]
chat-service --jdbc--> mysql/content (HikariPool-1)  3회  최대 1050.0ms
    events: acquired, commit
content-service --jdbc--> mysql/content (HikariPool-1)  54회  최대 394.8ms
    error: Data truncation: Data too long for column 'content' at row 1
    events: acquired, rollback, commit
content-service --messaging--> kafka/user.notifications  3회  최대 155.5ms  [publish]
kafka/user.notifications --messaging--> chat-service  3회  최대 1051.6ms  [receive]
content-service --service--> auth-service  2회  최대 31.4ms
```

### span (duration 상위 15 / 전체 147)

| ms | service | span | 시작 |
|---:|---|---|---|
| 1051.61 | chat-service | `receive` | 2026-08-12T01:22:19.155035Z |
| 1050.05 | chat-service | `connection` | 2026-08-12T01:22:19.155954Z |
| 1043.18 | chat-service | `user-notification-service#process-notification` | 2026-08-12T01:22:19.159113Z |
| 972.39 | chat-service | `push-dispatcher#dispatch` | 2026-08-12T01:22:19.229487Z |
| 495.87 | chat-service | `receive` | 2026-08-12T01:23:32.006398Z |
| 494.24 | chat-service | `connection` | 2026-08-12T01:23:32.007781Z |
| 487.33 | chat-service | `user-notification-service#process-notification` | 2026-08-12T01:23:32.011477Z |
| 461.58 | chat-service | `push-dispatcher#dispatch` | 2026-08-12T01:23:32.037037Z |
| 411.98 | content-service | `http post /feeds/{feedId}/comments` | 2026-08-12T01:22:18.599829Z |
| 409.86 | content-service | `secured request` | 2026-08-12T01:22:18.600209Z |
| 407.21 | chat-service | `receive` | 2026-08-12T01:23:30.973498Z |
| 406.36 | chat-service | `connection` | 2026-08-12T01:23:30.974064Z |
| 399.97 | chat-service | `user-notification-service#process-notification` | 2026-08-12T01:23:30.977292Z |
| 394.84 | content-service | `connection` | 2026-08-12T01:22:18.615122Z |
| 380.10 | chat-service | `push-dispatcher#dispatch` | 2026-08-12T01:23:30.997051Z |

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

