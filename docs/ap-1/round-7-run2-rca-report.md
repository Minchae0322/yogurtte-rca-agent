# RCA Report — `scan-1786497720`

| 항목 | 값 |
|---|---|
| 모드 | rca |
| 질문 | 최근 1시간 안에 댓글 작성이 실패했다는 제보가 있다. 원인을 조사해줘 |
| 시각 | 2026-08-12T05:12:42.923921100Z |
| provider | claude-cli |
| model | `claude-opus-5` · turns 1 |
| prompt | `.\prompts\system-prompt.md` |
| tokens | in 67434 (cacheRead 23,796 · cacheCreate 43,636) / out 8531 · cost $0.6615 |
| elapsed | total 153509ms (tempo 529 · loki 404 · mimir 564 · assemble 231 · llm 139348) |

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
| tokens | in 32913 / out 1997 · cost $0.1802 |
| chars | 컨텍스트 2,143 + 프롬프트 1,399 = **3,542** |
| elapsed | survey 1622ms · llm 44665ms |

**선정 이유**: 댓글 작성 API의 error 트레이스(INC-3)와 같은 시각 content-service의 반복되는 DataException(INC-2)이 하나의 장애를 트레이스·로그 두 면에서 본 것이며, 질문의 증상·엔드포인트·시각이 모두 맞다.

**근거**

- INC-3: content-service http post /feeds/{feedId}/comments 208ms error 채널 트레이스 1건 (2026-08-12T01:22:30.309754Z, traceId 6a7bcad606c9ad16a434d244e431e33d) — 질문의 '댓글 작성 실패' 증상과 엔드포인트가 정확히 일치
- INC-2: content-service 원인 예외 org.hibernate.exception.DataException, 01:22~01:27 사이 5회 반복 (평균 60초 간격) — DB 저장 단계의 데이터 제약 위반 계열
- INC-2/INC-3 시각 일치: ERROR/WARN 4건(01:22:00Z~01:23:00Z)이 error 트레이스와 같은 분에 발생 — 같은 장애의 로그 지문과 트레이스 지문
- 208ms로 빠르게 실패 + Tempo 지연 검색 '{duration > 3s && status != error}' 0건 — 타임아웃/포화형 지연 장애가 아니라 즉시 거부되는 예외형 실패
- min_over_time(up[1m]), mongodb_up, kafka_brokers, kafka_consumergroup_lag, websocket_active_users 모두 이상 신호 0건 — 파드 다운·DB 다운·메시징 지연은 배제

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

- INC-1 — 댓글 작성 경로는 content-service가 처리하며 chat-service를 거치지 않는다. 같은 분(01:22)이지만 ERROR/WARN 1건뿐이고 예외 지문도 없어, 댓글 실패의 부수 알림이거나 무관한 노이즈일 가능성이 높다 — 창이 동일하므로 INC-2/INC-3 조사 중 로그에 함께 잡히면 그때 확인한다.
- INC-4 — auth-service NullPointerException은 01:31~01:33Z로 댓글 실패 시각(01:22~01:27)과 8분 이상 떨어져 있고 서비스·예외 지문도 달라, 별개의 사안으로 본다.

**보정 기록**

- 창을 후보 [INC-2, INC-3] 의 신호 시각에서 계산했다 (2026-08-12T01:22:00Z ~ 2026-08-12T01:27:00Z)

**스윕 무신호/실패**

- Tempo 지연 검색 '{ duration > 3s && status != error }'이 이 창에서 0건이다. 임계값보다 느린 요청이 없었다는 뜻이다.

## 수집 범위 (Coverage)

- **window**: 2026-08-12T01:22:00Z ~ 2026-08-12T01:27:00Z (300s)
- **trace**: 40,033B / 46 spans
- **창 안 후보 트레이스**: 2건 / 40,033B
- **logs**: errwarn=37,189B · traceId=6,383B
- **metrics**: 8 수집 / 54,134B, 누락 [sum(rate(http_server_requests_seconds_count{application="content-service", status="401"}[1m]))]
- **context**: 67,957 chars (+ 시스템 프롬프트 805 chars)

### 토큰 축 (개선 지표)

| 단계 | 총 in | chars | 컨텍스트 토큰 |
|---|---:|---:|---:|
| 탐색 | 32,913 | 3,542 | 2,903 |
| 분석 | 67,434 | 68,762 | 37,424 |
| **합계** | **100,347** | | **40,327** |

- **overheadTokens 30,010 tok — 이 회차에 실측**(1자 프롬프트 프로브, 같은 명령·모델·샌드박스).
  호출마다 한 번씩 얹히므로 단계별로 뺀다. **다른 날 상수를 쓰지 않았다 = `█ 실측`.**
- contextTokens (count_tokens API): 측정 안 됨 — 구독 CLI 경로엔 API 키가 없다
- 비용은 **API 환산 추정치**다. 구독 계정으로 호출하므로 실제 청구액이 아니다.

## 수집 실패/누락

- Metric 'sum(rate(http_server_requests_seconds_count{application="content-service", status="401"}[1m]))' returned no series in this window; skipped.

---

## 1. 원인 후보 랭킹 (최대 3개)

1. **댓글 본문 길이가 `tb_feed_comment.content` 컬럼 한계를 초과해 MySQL이 INSERT를 거부(에러 1406 / SQLState 22001), 트랜잭션 롤백 후 500 반환** — 애플리케이션 계층에 길이 사전 검증이 없어 DB까지 도달함
2. **컬럼 정의 자체가 실제 입력 스펙보다 좁음** (컬럼 길이 과소 정의 또는 charset 바이트 초과 — 예: 이모지/4바이트 문자)
3. **인프라·용량 요인(커넥션 풀 고갈, GC, Kafka, DB/Redis/Mongo 장애)** — 데이터상 거의 전면 반증됨

---

## 2. 후보별 근거

### 후보 1 — 입력 길이 초과 + 애플리케이션 검증 부재

**근거**
- 실패 트레이스 `6a7bcad606c9ad16a434d244e431e33d`, span `http post /feeds/{feedId}/comments` → `"status":"500"`, `"outcome":"SERVER_ERROR"`, `"http.url":"/api/feeds/145/comments"`, dur 208.897ms
- 그 하위 `query` span (start 01:22:30.357377Z, dur 112.422ms)에 `"error":"Data truncation: Data too long for column 'content' at row 1"`, `"status":{"code":"STATUS_CODE_ERROR"}`, 쿼리는 `insert into tb_feed_comment (content,created_at,creator_id,...)`
- 로그 원문: `WARN ... o.h.e.jdbc.spi.SqlExceptionHelper - SQL Error: 1406, SQLState: 22001` (01:22:30.470), `ERROR ... Data truncation: Data too long for column 'content' at row 1` (01:22:30.471)
- 스택: `Caused by: com.mysql.cj.jdbc.exceptions.MysqlDataTruncation` → `org.hibernate.exception.DataException` → `org.springframework.dao.DataIntegrityViolationException`, 앱 진입점 `FeedCommentService.createComment(FeedCommentService.java:64)` ← `FeedCommentController.createComment(FeedCommentController.java:53)`
- 검증 부재의 근거: 예외가 컨트롤러/서비스에서 걸리지 않고 JDBC까지 내려갔고, 최종적으로 `c.e.t.a.c.e.GlobalExceptionHandler - [api-error] handleAllException`(포괄 핸들러)로 떨어져 **400이 아닌 500**으로 응답됨 (`RequestLoggingFilter - [HTTP] POST /api/feeds/145/comments 500 - 208ms`)
- 대조군: 성공 트레이스 `6a7bcb1282936b27a58ef575b8e0baf2`는 **동일 엔드포인트·동일 feed 145**에 대해 `"status":"200"`, 동일 INSERT가 `"jdbc.row-affected":"1"`, `jdbc.generated-keys: 1469`로 통과 → 코드 경로·DB·엔드포인트가 아니라 **요청 본문 값**이 갈린 변수임

**확신도: 높음**

**반증 데이터**: 없음. (단, 실패 요청의 실제 본문 길이는 로그·트레이스 어디에도 없어 "얼마나 초과했는지"는 확인 불가)

**대기·지연 구간 판정**
| 구간 | 실측 | 상한(타임아웃 설정) | 만료 여부 | 최종 상태 |
|---|---|---|---|---|
| HikariCP 커넥션 획득 (`connection` span start 30.323947 → `acquired` 30.325543) | 1.596ms | 설정값 미수집 | 만료 아님(획득 성공) | 성공 |
| INSERT 실행 (`query`) | 112.422ms | 설정값 미수집 (`socketTimeout`/statement timeout 없음) | **만료 아님** — 타임아웃이 아니라 서버가 1406 에러를 응답 | **실패(서버 거부)** |
| 트랜잭션 (`connection` span, dur 193.108ms) | `rollback` @01:22:30.499223 | 해당 없음 | 해당 없음 | **롤백 완료 — 부분 저장 없음, 재시도 흔적 없음(폐기)** |
| HTTP 요청 전체 | 208.897ms | 클라이언트 타임아웃 미수집 | 판정 불가 | 500 응답 완료 |

> 실패 트레이스에는 exp/보상 쿼리, `update tb_feed set comment_count=?`, `publish user.notifications` span이 **전혀 없다** — 첫 INSERT에서 끊겨 후속 부수효과가 실행되지 않았음이 span 부재로 확인됨(성공 트레이스에는 모두 존재).

---

### 후보 2 — 컬럼 정의 과소 / charset 바이트 초과

**근거**
- 에러가 `content` 컬럼 **한 개**에만 발생하고, 동일 INSERT의 `creator_nickname`·`creator_profile_url` 등 다른 문자열 컬럼은 문제없음 → 해당 컬럼의 정의 길이가 실서비스 입력 분포 대비 좁을 가능성
- MySQL 1406은 "문자 수 초과"와 "바이트 수 초과(utf8mb3에 4바이트 이모지 입력 등)"를 구분하지 않으므로, 정상 길이의 입력이라도 이모지 포함 시 동일 에러가 난다 — 관측 데이터만으로 두 경우를 구분할 수 없음
- 실패/성공이 파드나 노드로 갈리지 않음: 실패는 `content-service-85f648fcff-sp24n`(노드 ip-172-31-45-39), 성공은 `content-service-85f648fcff-v2pw9`(노드 ip-172-31-40-241)이나 **ReplicaSet 해시가 `85f648fcff`로 동일** → 배포 버전 차이가 아니라 데이터 차이임을 뒷받침

**확신도: 중간** (에러 자체는 확정이나, "컬럼이 좁은 것"인지 "입력이 비정상적으로 긴 것"인지 가르는 근거 — DDL과 실제 payload 길이 — 가 둘 다 미수집)

**반증 데이터**: 같은 시간대·같은 엔드포인트에서 200 성공이 존재한다는 사실. 컬럼이 상시 과소했다면 일반 길이 댓글도 광범위하게 실패해야 하나, 관측창 내 실패는 1건뿐이다. 이는 후보 1(비정상적으로 긴 입력) 쪽으로 무게를 옮긴다.

**대기·지연 구간 판정**: 후보 1과 동일 구간이므로 중복 판정 생략 — 해당 없음.

---

### 후보 3 — 인프라·용량 요인

**근거(및 대부분 반증)**
- `hikaricp_connections_active` / `hikaricp_connections_pending`: content-service 두 파드, chat-service, auth-service **전 구간 0** → 풀 고갈·대기 없음
- `up`: 앱·노드·kafka·mongodb·redis 익스포터 **전 구간 1**, `mongodb_up`=1, `kafka_brokers`=1 → 인프라 다운 없음
- `kafka_consumergroup_lag`: `notification-processors`/`user.notifications` 전 파티션 0, DLQ(`user.notifications.dlq`) 랙 0 → 알림 적체·DLQ 유입 없음
- `rate(jvm_gc_pause_seconds_sum[5m])`: 실패 파드(sp24n) 최대 1.67e-5, 나머지 0~3e-4 수준 → GC 정지 영향 무시 가능
- Redis: `KEYS`/`GET` 0.5ms대, `server.address":"redis://172.31.46.124?timeout=2s"` → **상한 2s 대비 0.533ms, 만료 아님, 성공**
- MongoDB: `find`/`insert` 최대 1.5ms, 전부 성공

**확신도: 낮음** (원인으로서는 사실상 배제)

**반증 데이터**: 위 전부. 특히 커넥션 대기 0과 인프라 up=1이 용량·가용성 가설과 정면 배치된다.

**대기·지연 구간 판정** (관측된 유일한 장시간 구간 — 참고용, 댓글 실패와 인과 없음)
| 구간 | 실측 | 상한 | 만료 여부 | 최종 상태 |
|---|---|---|---|---|
| chat-service `receive`(user.notifications, offset 1160, partition 3) | 407.209ms | `max.poll.interval.ms` 미수집 | 판정 불가(기본값 300s 가정 시 여유) | **성공(처리 완료)** |
| chat-service가 `HikariPool-1`(datasource `content`) 점유 | `acquired` +1.557ms → `commit` @01:23:31.378915, 총 406.362ms | 설정값 미수집 | 만료 아님 | **커밋 성공** |
| chat-service `PushDispatcher#dispatch` | 380.099ms | 미수집 | 판정 불가 | 성공 |

> 이 경로는 **성공 트레이스 쪽**이며, chat-service가 MySQL 커넥션을 406ms 잡은 채 Mongo/Redis I/O를 수행하는 구조는 현재 부하(active=0)에서는 무해하나 부하 증가 시 병목 후보다. 이번 장애의 원인은 아니다.

---

## 3. 권장 다음 조치

### 이미 발생한 피해: 복구 가능한가
**서버 측 자동 복구 불가. 사용자 재작성이 유일한 경로.**
- 근거: `connection` span의 `rollback`(01:22:30.499223) 이벤트로 트랜잭션이 완전히 롤백됨 → 부분 저장·정합성 깨짐 없음(수리할 데이터가 없음). 동시에 **댓글 본문 원문이 로그·트레이스 어디에도 남지 않았다** — 예외 메시지와 SQL 바인딩 자리표시자(`values (?,?,...)`)만 있고 실제 값이 없으므로 복원 소스가 없다.
- 부수 피해 없음(확인됨): exp 지급·`comment_count` 증가·`user.notifications` 발행 span이 실패 트레이스에 부재 → 유령 알림이나 카운트 불일치가 생기지 않았다. DLQ 랙 0도 이를 뒷받침.
- 조치: 해당 사용자(로그상 `userId=1`)에게 재작성 안내. 다만 **관측창이 5분(01:22~01:27)뿐이라 "최근 1시간" 제보 전체의 피해 규모는 미확정** — 아래 추가 수집 필요.

### 재발 방지
1. DTO/엔티티에 길이 검증 추가(`@Size(max=…)`) — `FeedCommentController.createComment:53` 진입 시점에서 걸러 **400**으로 응답. 현재는 `GlobalExceptionHandler.handleAllException`이 포괄 처리해 500으로 나가므로, `DataIntegrityViolationException`/`DataException`을 4xx로 매핑하는 핸들러도 함께 추가.
2. `tb_feed_comment.content` DDL 확인 후 제품 스펙과 정렬, charset이 `utf8mb4`인지 검증(이모지 1자 = 4바이트).
3. 클라이언트 입력창에 동일 상한을 `maxlength`로 노출(서버 검증은 유지 — 신뢰 경계이므로 클라이언트만으로 대체 금지).
4. 알림: SQLState `22001` 또는 `POST /feeds/{feedId}/comments` 5xx 발생 시 알람. (참고: 이번 조사에서 `status="401"` content-service 메트릭은 **시리즈 자체가 없어 수집 실패** — 인증 계열 실패 가시성이 비어 있으므로 4xx/5xx 상태코드 메트릭이 실제로 기록되는지도 함께 점검할 것.)

### 복구 확인
- 재현 테스트: 상한 초과 본문으로 POST → **500이 아닌 400**, 트레이스에 `insert into tb_feed_comment` span이 아예 생성되지 않아야 함(DB 도달 전 차단).
- 정상 본문 POST → 200, 성공 트레이스와 동일하게 `jdbc.row-affected=1` + `update tb_feed set comment_count=?` + `publish user.notifications` span이 모두 존재하고 `connection` span이 `commit`으로 종료되는지 확인.
- 정합성 스팟체크: feed 145의 `tb_feed.comment_count`와 `tb_feed_comment` 실제 행 수 일치 확인(롤백이 온전했다면 일치해야 함).
- 회귀 감시: 배포 후 1시간 동안 해당 URI의 5xx 카운트 0 유지.

### 데이터 부족 — 추가 수집 필요
- `tb_feed_comment` DDL(컬럼 길이·charset·collation) → 후보 1과 후보 2를 가르는 결정적 근거
- **조회창 확대(01:22~01:27 → 실제 1시간)**: `SQLState: 22001` / `status=500` 로그 카운트로 피해 건수·영향 사용자 수 확정. 현재 결론은 실패 1건·성공 1건 표본에 기반하므로 **범위(scope)에 대한 확신도는 낮춰서 읽어야 함**
- 실패 요청의 본문 길이(요청 로깅에 길이만이라도 기록 — 원문 저장은 지양)
- `http_server_requests_seconds_count{application="content-service"}` 상태코드별 시리즈 존재 여부(401 쿼리 무결과 = 계측 공백 가능성)

---

인증은 배제된다: 실패 트레이스에서 `security filterchain before`가 12개 필터 전부 통과했고 `JwtAuthenticationFilter`/`JwtFilter`를 지나 컨트롤러까지 도달, 비즈니스 로그에 `userId=1`과 `[user-cache] 캐시 HIT - userId: 1`이 남아 있다. 로그 마지막 줄의 `userId=NONE`은 응답 단계 `RequestLoggingFilter`에서 보안 컨텍스트가 정리된 뒤 찍힌 값이며 인증 실패 근거가 아니다.

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

