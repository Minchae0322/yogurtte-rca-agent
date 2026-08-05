# RCA Report — `scan-1785895200`

| 항목 | 값 |
|---|---|
| 모드 | rca |
| 질문 | 최근 1시간 안에 댓글 작성이 실패했다는 제보가 있다. 원인을 조사해줘 |
| 시각 | 2026-08-05T02:14:44.641436800Z |
| provider | claude-cli |
| model | `claude-opus-5` · turns 1 |
| prompt | `.\prompts\system-prompt.md` |
| tokens | in 67693 (cacheRead 23,449 · cacheCreate 44,242) / out 6108 · cost $0.6069 |
| elapsed | total 112496ms (tempo 902 · loki 408 · mimir 907 · assemble 169 · llm 98670) |

## 탐색 (Triage)

| 항목 | 값 |
|---|---|
| 시간창 해석 | 상대 표현 '최근 1시간' |
| 시간창 확신도 | EXACT |
| 스윕 창 | 2026-08-05T01:14:12.436908200Z ~ 2026-08-05T02:14:12.436908200Z |
| 좁힌 창 | 2026-08-05T02:00:00Z ~ 2026-08-05T02:14:12.436908200Z |
| 대상 | content-service |
| traceId | 6a729b55f741850507e3ddfd50cc4d65 |
| 트레이스 후보 | 2건 |
| 장애 후보 | 4건 · 선택 INC-1, INC-3 |
| 계획 파싱 | 성공 |
| 스윕 컨텍스트 | **후보만 — 원본 제외 (B)** |
| prompt | `.\prompts\triage-prompt.md` |
| tokens | in 32049 / out 1387 · cost $0.1596 |
| chars | 컨텍스트 1,876 + 프롬프트 1,399 = **3,275** |
| elapsed | survey 1639ms · llm 30479ms |

**선정 이유**: 질문의 증상(댓글 작성 실패)과 엔드포인트·시각·서비스가 모두 일치하는 유일한 후보 쌍이고, 트레이스(INC-3)와 로그(INC-1)는 같은 장애의 서로 다른 지문이라 합쳐서 봐야 한다.

**근거**

- INC-3: content-service http post /feeds/{feedId}/comments 119ms error 채널, traceId 6a729b55f741850507e3ddfd50cc4d65 (2026-08-05T02:09:25Z) — 제보된 '댓글 작성' 엔드포인트와 정확히 일치
- INC-1: 같은 content-service에서 02:05:00Z~02:10:00Z ERROR/WARN 4건 — INC-3 트레이스 시각을 포함하는 구간이라 같은 장애의 로그 지문으로 함께 봄
- 119ms 만의 실패 = 타임아웃 아님. 즉시 예외/거부 경로이므로 content-service 로그의 스택트레이스가 원인 판별의 1차 증거
- up / mongodb_up / kafka_brokers min_over_time 이상 0건 — 파드 다운·DB 다운·브로커 소실은 배제됨

**스윕이 찾은 트레이스** (고른 것은 6a729b55f741850507e3ddfd50cc4d65)

| traceId | 채널 | root service | root span | ms |
|---|---|---|---|---:|
| `6a729b55f741850507e3ddfd50cc4d65` ←선택 | error | content-service | http post /feeds/{feedId}/comments | 119 |
| `6a729bd0cf63e6ea421b273667e35a3f` | slow | auth-service | http get /user/{userid}/following | 3231 |

**장애 후보** (코드가 신호를 묶은 것 · 창은 여기서 계산됨)

## INC-1  content-service  |  ERROR/WARN
- 구간: 2026-08-05T02:05:00Z ~ 2026-08-05T02:10:00Z  (LOKI · 집계 해상도만큼 흐림)
- ERROR/WARN 4건 (2026-08-05T02:05:00Z ~ 2026-08-05T02:10:00Z)
- 같은 시각의 다른 후보: INC-2, INC-3  (인과 여부는 판단하지 않았다)

## INC-2  kafka  |  kafka_consumergroup_lag
- 구간: 2026-08-05T02:09:12Z ~ 2026-08-05T02:14:12Z  (MIMIR · 집계 해상도만큼 흐림)
- kafka_consumergroup_lag{consumergroup=notification-processors, partition=3, topic=user.notifications} 0 → 1
- 같은 시각의 다른 후보: INC-1, INC-3, INC-4  (인과 여부는 판단하지 않았다)

## INC-3  content-service  |  http post /feeds/{feedId}/comments
- 구간: 2026-08-05T02:09:25.627296Z ~ 2026-08-05T02:09:25.746296Z  (TEMPO · 시각 정확)
- content-service http post /feeds/{feedId}/comments 119ms (error 채널)
- traceId: 6a729b55f741850507e3ddfd50cc4d65
- 같은 시각의 다른 후보: INC-1, INC-2  (인과 여부는 판단하지 않았다)

## INC-4  auth-service  |  http get /user/{userid}/following
- 구간: 2026-08-05T02:11:28.128454Z ~ 2026-08-05T02:11:31.359454Z  (TEMPO · 시각 정확)
- auth-service http get /user/{userid}/following 3,231ms (slow 채널)
- traceId: 6a729bd0cf63e6ea421b273667e35a3f
- 같은 시각의 다른 후보: INC-2  (인과 여부는 판단하지 않았다)

**기각한 후보**

- INC-2 — kafka_consumergroup_lag가 notification-processors/user.notifications에서 0→1로 1건 증가한 수준이며, 알림 소비 지연은 댓글 쓰기 요청의 동기 경로 밖이고 lag 1은 정상 변동 범위라 증상과 연결되지 않는다.
- INC-4 — auth-service GET /user/{userid}/following 3,231ms는 slow 채널의 조회 경로이고 02:11:28로 실패 트레이스보다 늦으며, 댓글 실패가 119ms 즉시 실패라 이 지연이 원인일 수 없다 — 다만 content-service 로그에서 auth 호출 흔적이 나오면 재검토 대상.

**보정 기록**

- 창을 후보 [INC-1, INC-3] 의 신호 시각에서 계산했다 (2026-08-05T02:00:00Z ~ 2026-08-05T02:14:12.436908200Z)

## 수집 범위 (Coverage)

- **window**: 2026-08-05T02:00:00Z ~ 2026-08-05T02:14:12.436908200Z (852s)
- **trace**: 33,415B / 26 spans
- **창 안 후보 트레이스**: 10건 / 33,415B
- **logs**: errwarn=37,139B · traceId=6,327B
- **metrics**: 8 수집 / 105,113B, 누락 [sum(rate(http_server_requests_seconds_count{application="content-service", status="401"}[1m]))]
- **context**: 68,839 chars (+ 시스템 프롬프트 805 chars)

### 토큰 축 (개선 지표)

| 단계 | 총 in | chars | 컨텍스트 토큰 |
|---|---:|---:|---:|
| 탐색 | 32,049 | 3,275 | 2,623 |
| 분석 | 67,693 | 69,644 | 38,267 |
| **합계** | **99,742** | | **40,890** |

- **overheadTokens 29,426 tok — 이 회차에 실측**(1자 프롬프트 프로브, 같은 명령·모델·샌드박스).
  호출마다 한 번씩 얹히므로 단계별로 뺀다. **다른 날 상수를 쓰지 않았다 = `█ 실측`.**
- contextTokens (count_tokens API): 측정 안 됨 — 구독 CLI 경로엔 API 키가 없다
- 비용은 **API 환산 추정치**다. 구독 계정으로 호출하므로 실제 청구액이 아니다.

## 수집 실패/누락

- Metric 'sum(rate(http_server_requests_seconds_count{application="content-service", status="401"}[1m]))' returned no series in this window; skipped.

---

## 1. 원인 후보 랭킹 (최대 3개)

1. **댓글 본문 길이가 `tb_feed_comment.content` 컬럼 정의를 초과해 MySQL이 INSERT를 거부 → 트랜잭션 롤백 → HTTP 500** (MySQL error 1406 / SQLState 22001)
2. **애플리케이션 계층에 본문 길이 검증이 없어 DB까지 도달하고, 입력 오류가 4xx가 아닌 500으로 매핑됨** (1번의 상위 원인 계층 — 왜 막히지 않았는가)
3. **제보된 다른 실패 건에 대한 별도 원인** — 관측 창에서 실패 트레이스는 1건뿐이라 확인 불가 (데이터 부족)

---

## 2. 후보별 근거

### 후보 1 — content 컬럼 길이 초과로 INSERT 실패

**근거**
- 실패 트레이스 `6a729b55f741850507e3ddfd50cc4d65`: 루트 span `http post /feeds/{feedId}/comments`, `"status":"500"`, `"outcome":"SERVER_ERROR"`, `"http.url":"/api/feeds/145/comments"`, durNs 119,485,000 (119.5ms).
- 자식 span `query` (`spanId` `pWkAaUE3PyM=`, 51.335ms)에 `"error":"Data truncation: Data too long for column 'content' at row 1"`, `status.code = STATUS_CODE_ERROR`, SQL은 `insert into tb_feed_comment (content,created_at,creator_id,...) values (?,...)`.
- 로그 원문:
  - `WARN … o.h.e.jdbc.spi.SqlExceptionHelper - SQL Error: 1406, SQLState: 22001` (11:09:25.704, userId=1)
  - `ERROR … o.h.e.jdbc.spi.SqlExceptionHelper - Data truncation: Data too long for column 'content' at row 1`
  - `org.springframework.dao.DataIntegrityViolationException: could not execute statement [Data truncation: Data too long for column 'content' at row 1]` → `Caused by: org.hibernate.exception.DataException` → `Caused by: com.mysql.cj.jdbc.exceptions.MysqlDataTruncation`
  - 앱 프레임: `com.example.toycontent.app.feed.service.FeedCommentService.createComment(FeedCommentService.java:64)`, 진입점 `FeedCommentController.createComment(FeedCommentController.java:53)`
  - `RequestLoggingFilter - [HTTP] POST /api/feeds/145/comments 500 - 118ms`
- 인프라 원인은 모두 배제됨: `hikaricp_connections_active`·`pending` 4개 인스턴스 전 구간 0, `up` 전 구간 1(mysql 노드 포함 infra-server 계열 전부), GC pause rate 최대 5.4e-5초/초 수준, Redis `GET` 0.557ms + 로그 `[user-cache] 캐시 HIT - userId: 1, elapsed: 1ms`.

**확신도: 높음**

**반증 데이터: 없음** (관측 창 내 500 응답·에러 로그·에러 span이 모두 동일 traceId·동일 예외로 일치)

**대기·지연 구간 판정**
| 구간 | 실측 | 상한(타임아웃 설정값) | 만료 여부 | 최종 상태 |
|---|---|---|---|---|
| Hikari 커넥션 획득 (`connection` span 시작 …639905000 → `acquired` …641574000) | 1.669ms | `connectionTimeout` 설정값 미확보 | 만료 아님 (획득 성공이 이벤트로 확인됨) | **성공** |
| INSERT 실행 (`query` span) | 51.335ms | 쿼리/트랜잭션 타임아웃 설정값 미확보 → **만료 여부 판정 불가**. 단 종료 사유는 타임아웃이 아니라 SQLState 22001 데이터 오류 | 타임아웃 아님 | **실패** |
| 트랜잭션 | `connection` span 105.255ms, `rollback` 이벤트 …730706000 (11:09:25.730) | 해당 없음 | — | **롤백 → 폐기.** 재시도 근거 없음(동일 traceId 재실행 span·재시도 로그·재시도 토픽 유입 없음. `chat.messages-retry-*` lag은 전 구간 0이며 댓글 경로와 무관) |
| 전체 요청 | 119.5ms → 500 응답 | — | — | **클라이언트에 실패 반환** |

---

### 후보 2 — 애플리케이션 계층 입력 길이 검증 부재 / 입력 오류의 500 매핑

**근거**
- 스택트레이스에 Bean Validation 계열 프레임이 전혀 없고, `FeedCommentController.createComment:53` → `FeedCommentService.createComment:64` → Hibernate → MySQL 순으로 **검증 없이 DB까지 도달**했다. 길이 위반이 DB 제약에서 처음 잡혔다.
- `WARN … c.e.t.a.c.e.GlobalExceptionHandler - [api-error] handleAllException` — 전용 핸들러가 아닌 포괄 핸들러가 처리했고, 결과가 `status=500`, `outcome=SERVER_ERROR`. 사용자 입력 오류가 서버 오류로 표시되어 제보가 "장애"로 인지된 원인이기도 하다.
- 요청은 인증을 정상 통과했다: `JwtAuthenticationFilter`·`JwtFilter` 프레임 통과, MDC `userId=1`, security filterchain before/after 12/12 완주.

**확신도: 중간** (관측된 사실은 확실하나, 컬럼 정의 길이와 실제 입력 길이·DTO 검증 애너테이션 유무는 관측 데이터에 없음 — 코드/스키마 확인 필요)

**반증 데이터: 없음**

**대기·지연 구간 판정: 해당 없음** (대기 구간이 아니라 검증 로직 부재 건)

---

### 후보 3 — 제보된 다른 실패 건의 별도 원인

**근거**
- 제보는 "댓글 작성 실패"였으나 창(02:00:00Z ~ 02:14:12Z) 내에서 관측된 댓글 작성 실패는 **트레이스 1건·에러 로그 1세트**뿐이다. 로그 접기 표식에도 반복 지문(`xN회`)이 없어 동일 오류의 다발 발생 근거가 없다.
- 인증 실패 가설은 배제 불가: `sum(rate(http_server_requests_seconds_count{application="content-service", status="401"}[1m]))`가 **no series로 수집 실패**했다. 다만 관측된 실패 건 자체는 401이 아니라 500이다.
- 함께 수집된 auth-service `http get /user/{userId}/following`이 3.23초(durNs 3,231,689,000)로 느리지만 `"status":"200"`, `"outcome":"SUCCESS"`이며, 호출 그래프에 content→auth 엣지가 없어 댓글 경로와의 인과 근거가 없다.

**확신도: 낮음**

**반증 데이터**: 실패 건이 1건뿐이라는 사실 자체 — 광범위한 다발 장애를 시사하는 관측값(에러율 급증, 풀 고갈, 서비스 다운)이 하나도 없다. `up` 전 구간 1, Hikari pending 전 구간 0.

**대기·지연 구간 판정**
- auth-service 3.23초 요청: 상한(타임아웃) 설정값 미확보 → **만료 여부 판정 불가**. 최종 상태는 명시적으로 **성공**(200/SUCCESS).

---

## 3. 권장 다음 조치

### 이미 발생한 피해: 복구 가능한가
**서버 측 복구 불가.** 근거: `connection` span에 `rollback` 이벤트가 기록되어 해당 INSERT는 커밋되지 않았고, 재시도·DLQ 경로 근거가 없다(댓글 작성은 동기 HTTP 경로이며 Kafka lag 전 구간 0). 또한 INSERT 파라미터는 `?`로 바인딩되어 로그·span 어디에도 원문 댓글 내용이 남아 있지 않으므로 본문 재구성도 불가능하다.
- 실행 가능한 조치: 피해 범위는 **userId=1, feedId=145, 2026-08-05 11:09:25 KST(02:09:25 UTC) 1건**으로 특정된다. 해당 사용자에게 재작성을 안내하는 것이 유일한 복구 경로다.
- 단, 창 밖(최근 1시간 전체) 동일 오류 건수는 미확인 — 아래 추가 수집 항목 참조.

### 재발 방지
1. `tb_feed_comment.content` 실제 컬럼 정의 확인(`SHOW CREATE TABLE tb_feed_comment`) 후, 요청 DTO에 동일한 상한의 길이 검증 추가 → DB 도달 전에 차단.
2. 컬럼 정의와 클라이언트 입력 상한이 어긋나 있다면 둘 중 하나로 정렬(컬럼 확장 시 `VARCHAR`↔`TEXT` 변경 영향 검토 필요).
3. `DataIntegrityViolationException`/`DataException`을 `GlobalExceptionHandler`에서 400 계열로 매핑 — 현재는 `handleAllException`이 잡아 500으로 나가고 있어 사용자·모니터링 양쪽에서 서버 장애로 오인된다.
4. **데이터 부족**으로 판단 불가한 부분(추가 수집 필요):
   - 최근 1시간 전체에 대한 `SQLState: 22001` / error 1406 로그 건수 — 실제 영향 사용자 수 산정용
   - content-service의 4xx/5xx 상태별 요청 수 메트릭 (401 시리즈가 결측이었으므로 `http_server_requests_seconds_count` 라벨 수집 자체를 점검)
   - `tb_feed_comment` DDL 및 댓글 DTO의 검증 애너테이션 유무
   - HikariCP `connectionTimeout` / JPA 쿼리·트랜잭션 타임아웃 설정값 — 위 표의 "판정 불가" 해소용

### 복구 확인
1. 수정 배포 후 상한 초과 본문으로 `POST /api/feeds/{feedId}/comments` 호출 → **400**(500 아님)이 반환되고 `tb_feed_comment` INSERT span이 생성되지 않는지 확인.
2. 상한 이내 본문으로 호출 → 200 및 `query` span에 error 속성 없이 커밋(`rollback` 이벤트 부재) 확인.
3. Loki에서 `SQLState: 22001` 및 `Data too long for column 'content'` 건수가 배포 후 0으로 유지되는지 24시간 관찰.
4. 회귀 감시 지표: content-service 5xx 비율, `hikaricp_connections_pending`(현재 정상값 0 유지) — 검증 추가로 인한 부작용 확인.

> 확신도 보정: `status="401"` 메트릭 수집 실패로 인증 실패 경로를 계량적으로 배제하지 못했다. 다만 실패 트레이스는 JWT 필터를 통과해 `userId=1`로 DB까지 진입했으므로, **관측된 이 1건에 한해** 후보 1은 높음을 유지한다. 제보 전체가 이 1건으로 설명되는지는 위 추가 로그 집계 전까지 확정할 수 없다.

---

## 관측 증거 (Evidence)

> 이 조사가 실제로 본 값이다. 원본 응답 전체는 `reports/raw/scan-1785895200-*.json`에 있다.

### 호출 그래프 (트레이스에서 추출)

```
content-service --db--> redis  2회  최대 0.6ms  [GET, INFO]
content-service --jdbc--> mysql/content (HikariPool-1)  6회  최대 105.3ms
    error: Data truncation: Data too long for column 'content' at row 1
    events: acquired, rollback
```

### span (duration 상위 15 / 전체 26)

| ms | service | span | 시작 |
|---:|---|---|---|
| 3231.69 | auth-service | `http get /user/{userid}/following` | 2026-08-05T02:11:28.128454Z |
| 3211.93 | auth-service | `secured request` | 2026-08-05T02:11:28.146913Z |
| 119.49 | content-service | `http post /feeds/{feedId}/comments` | 2026-08-05T02:09:25.627296Z |
| 117.35 | content-service | `secured request` | 2026-08-05T02:09:25.627879Z |
| 105.26 | content-service | `connection` | 2026-08-05T02:09:25.639905Z |
| 51.34 | content-service | `query` | 2026-08-05T02:09:25.652279Z |
| 18.03 | auth-service | `security filterchain before` | 2026-08-05T02:11:28.128797Z |
| 13.29 | content-service | `secured request` | 2026-08-05T02:06:41.797213Z |
| 4.77 | content-service | `secured request` | 2026-08-05T02:06:44.747096Z |
| 2.45 | content-service | `query` | 2026-08-05T02:09:25.643996Z |
| 1.83 | content-service | `connection` | 2026-08-05T02:06:44.747343Z |
| 1.56 | content-service | `connection` | 2026-08-05T02:06:44.749251Z |
| 0.56 | chat-service | `secured request` | 2026-08-05T02:06:41.914503Z |
| 0.56 | content-service | `GET` | 2026-08-05T02:09:25.647544Z |
| 0.51 | content-service | `INFO` | 2026-08-05T02:06:44.750994Z |

### 로그 원문 (60 / 전체 226줄)

전체가 상한을 넘어 ERROR/WARN 줄을 우선 발췌했다. 나머지는 원본 파일에 있다.

```
2026-08-05T02:09:25.704345964Z  [content-service]  2026-08-05 11:09:25.704 [http-nio-8082-exec-1]  WARN [traceId=6a729b55f741850507e3ddfd50cc4d65,spanId=a7c6e4ef0c5e7a18,userId=1] o.h.e.jdbc.spi.SqlExceptionHelper - SQL Error: 1406, SQLState: 22001
2026-08-05T02:09:25.704345964Z  [content-service]  2026-08-05 11:09:25.704 [http-nio-8082-exec-1]  WARN [traceId=6a729b55f741850507e3ddfd50cc4d65,spanId=a7c6e4ef0c5e7a18,userId=1] o.h.e.jdbc.spi.SqlExceptionHelper - SQL Error: 1406, SQLState: 22001
2026-08-05T02:09:25.704509571Z  [content-service]  2026-08-05 11:09:25.704 [http-nio-8082-exec-1] ERROR [traceId=6a729b55f741850507e3ddfd50cc4d65,spanId=a7c6e4ef0c5e7a18,userId=1] o.h.e.jdbc.spi.SqlExceptionHelper - Data truncation: Data too long for column 'content' at row 1
2026-08-05T02:09:25.704509571Z  [content-service]  2026-08-05 11:09:25.704 [http-nio-8082-exec-1] ERROR [traceId=6a729b55f741850507e3ddfd50cc4d65,spanId=a7c6e4ef0c5e7a18,userId=1] o.h.e.jdbc.spi.SqlExceptionHelper - Data truncation: Data too long for column 'content' at row 1
2026-08-05T02:09:25.743092742Z  [content-service]  2026-08-05 11:09:25.733 [http-nio-8082-exec-1]  WARN [traceId=6a729b55f741850507e3ddfd50cc4d65,spanId=a7c6e4ef0c5e7a18,userId=1] c.e.t.a.c.e.GlobalExceptionHandler - [api-error] handleAllException
2026-08-05T02:09:25.743092742Z  [content-service]  2026-08-05 11:09:25.733 [http-nio-8082-exec-1]  WARN [traceId=6a729b55f741850507e3ddfd50cc4d65,spanId=a7c6e4ef0c5e7a18,userId=1] c.e.t.a.c.e.GlobalExceptionHandler - [api-error] handleAllException
2026-08-05T02:09:25.743132730Z  [content-service]  org.springframework.dao.DataIntegrityViolationException: could not execute statement [Data truncation: Data too long for column 'content' at row 1] [insert into tb_feed_comment (content,created_at,creator_id,creator_nickname,creator_profile_url,deleted,deleted_at,feed_id,parent_comment_id,updated_at) values (?,?,?,?,?,?,?,?,?,?)]; SQL [insert into tb_feed_comment (content,created_at,creator_id,creator_nickname,creator_profile_url,deleted,deleted_at,feed_id,parent_comment_id,updated_at) values (?,?,?,?,?,?,?,?,?,?)]
2026-08-05T02:09:25.743147681Z  [content-service]  at org.springframework.orm.jpa.vendor.HibernateJpaDialect.convertHibernateAccessException(HibernateJpaDialect.java:293)
2026-08-05T02:09:25.743150638Z  [content-service]  at org.springframework.orm.jpa.vendor.HibernateJpaDialect.translateExceptionIfPossible(HibernateJpaDialect.java:241)
2026-08-05T02:09:25.743153998Z  [content-service]  at org.springframework.orm.jpa.AbstractEntityManagerFactoryBean.translateExceptionIfPossible(AbstractEntityManagerFactoryBean.java:560)
2026-08-05T02:09:25.743157926Z  [content-service]  at org.springframework.dao.support.ChainedPersistenceExceptionTranslator.translateExceptionIfPossible(ChainedPersistenceExceptionTranslator.java:61)
2026-08-05T02:09:25.743161438Z  [content-service]  at org.springframework.dao.support.DataAccessUtils.translateIfNecessary(DataAccessUtils.java:343)
2026-08-05T02:09:25.743164510Z  [content-service]  at org.springframework.dao.support.PersistenceExceptionTranslationInterceptor.invoke(PersistenceExceptionTranslationInterceptor.java:160)
2026-08-05T02:09:25.743167358Z  [content-service]  at org.springframework.aop.framework.ReflectiveMethodInvocation.proceed(ReflectiveMethodInvocation.java:184)
2026-08-05T02:09:25.743170756Z  [content-service]  at org.springframework.data.jpa.repository.support.CrudMethodMetadataPostProcessor$CrudMethodMetadataPopulatingMethodInterceptor.invoke(CrudMethodMetadataPostProcessor.java:165)
2026-08-05T02:09:25.743173295Z  [content-service]  at org.springframework.aop.framework.ReflectiveMethodInvocation.proceed(ReflectiveMethodInvocation.java:184)
2026-08-05T02:09:25.743176178Z  [content-service]  at org.springframework.aop.framework.JdkDynamicAopProxy.invoke(JdkDynamicAopProxy.java:223)
2026-08-05T02:09:25.743181852Z  [content-service]  at com.example.toycontent.app.feed.service.FeedCommentService.createComment(FeedCommentService.java:64)
2026-08-05T02:09:25.743195193Z  [content-service]  at org.springframework.aop.support.AopUtils.invokeJoinpointUsingReflection(AopUtils.java:359)
2026-08-05T02:09:25.743197973Z  [content-service]  at org.springframework.aop.framework.ReflectiveMethodInvocation.invokeJoinpoint(ReflectiveMethodInvocation.java:196)
2026-08-05T02:09:25.743200606Z  [content-service]  at org.springframework.aop.framework.ReflectiveMethodInvocation.proceed(ReflectiveMethodInvocation.java:163)
2026-08-05T02:09:25.743203038Z  [content-service]  at org.springframework.transaction.interceptor.TransactionAspectSupport.invokeWithinTransaction(TransactionAspectSupport.java:380)
2026-08-05T02:09:25.743205758Z  [content-service]  at org.springframework.transaction.interceptor.TransactionInterceptor.invoke(TransactionInterceptor.java:119)
2026-08-05T02:09:25.743208386Z  [content-service]  at org.springframework.aop.framework.ReflectiveMethodInvocation.proceed(ReflectiveMethodInvocation.java:184)
2026-08-05T02:09:25.743210938Z  [content-service]  at org.springframework.aop.framework.CglibAopProxy$DynamicAdvisedInterceptor.intercept(CglibAopProxy.java:727)
2026-08-05T02:09:25.743216155Z  [content-service]  at com.example.toycontent.app.feed.controller.FeedCommentController.createComment(FeedCommentController.java:53)
2026-08-05T02:09:25.743234028Z  [content-service]  at org.springframework.web.method.support.InvocableHandlerMethod.doInvoke(InvocableHandlerMethod.java:257)
2026-08-05T02:09:25.743236622Z  [content-service]  at org.springframework.web.method.support.InvocableHandlerMethod.invokeForRequest(InvocableHandlerMethod.java:190)
2026-08-05T02:09:25.743244555Z  [content-service]  at org.springframework.web.servlet.mvc.method.annotation.ServletInvocableHandlerMethod.invokeAndHandle(ServletInvocableHandlerMethod.java:118)
2026-08-05T02:09:25.743247830Z  [content-service]  at org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter.invokeHandlerMethod(RequestMappingHandlerAdapter.java:986)
2026-08-05T02:09:25.743250684Z  [content-service]  at org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter.handleInternal(RequestMappingHandlerAdapter.java:891)
2026-08-05T02:09:25.743253307Z  [content-service]  at org.springframework.web.servlet.mvc.method.AbstractHandlerMethodAdapter.handle(AbstractHandlerMethodAdapter.java:87)
2026-08-05T02:09:25.743255978Z  [content-service]  at org.springframework.web.servlet.DispatcherServlet.doDispatch(DispatcherServlet.java:1088)
2026-08-05T02:09:25.743258604Z  [content-service]  at org.springframework.web.servlet.DispatcherServlet.doService(DispatcherServlet.java:978)
2026-08-05T02:09:25.743261243Z  [content-service]  at org.springframework.web.servlet.FrameworkServlet.processRequest(FrameworkServlet.java:1014)
2026-08-05T02:09:25.743263782Z  [content-service]  at org.springframework.web.servlet.FrameworkServlet.doPost(FrameworkServlet.java:914)
2026-08-05T02:09:25.743266620Z  [content-service]  at jakarta.servlet.http.HttpServlet.service(HttpServlet.java:590)
2026-08-05T02:09:25.743269255Z  [content-service]  at org.springframework.web.servlet.FrameworkServlet.service(FrameworkServlet.java:885)
2026-08-05T02:09:25.743271814Z  [content-service]  at jakarta.servlet.http.HttpServlet.service(HttpServlet.java:658)
2026-08-05T02:09:25.743274660Z  [content-service]  at org.apache.catalina.core.ApplicationFilterChain.internalDoFilter(ApplicationFilterChain.java:195)
2026-08-05T02:09:25.743277319Z  [content-service]  at org.apache.catalina.core.ApplicationFilterChain.doFilter(ApplicationFilterChain.java:140)
2026-08-05T02:09:25.743279959Z  [content-service]  at org.apache.tomcat.websocket.server.WsFilter.doFilter(WsFilter.java:51)
2026-08-05T02:09:25.743282536Z  [content-service]  at org.apache.catalina.core.ApplicationFilterChain.internalDoFilter(ApplicationFilterChain.java:164)
2026-08-05T02:09:25.743284919Z  [content-service]  at org.apache.catalina.core.ApplicationFilterChain.doFilter(ApplicationFilterChain.java:140)
2026-08-05T02:09:25.743287247Z  [content-service]  at org.springframework.web.filter.OncePerRequestFilter.doFilter(OncePerRequestFilter.java:110)
2026-08-05T02:09:25.743290027Z  [content-service]  at org.apache.catalina.core.ApplicationFilterChain.internalDoFilter(ApplicationFilterChain.java:164)
2026-08-05T02:09:25.743293246Z  [content-service]  at org.apache.catalina.core.ApplicationFilterChain.doFilter(ApplicationFilterChain.java:140)
2026-08-05T02:09:25.743296392Z  [content-service]  at com.example.toycontent.app.auth.filter.JwtFilter.doFilterInternal(JwtFilter.java:73)
2026-08-05T02:09:25.743299187Z  [content-service]  at org.springframework.web.filter.OncePerRequestFilter.doFilter(OncePerRequestFilter.java:116)
2026-08-05T02:09:25.743301879Z  [content-service]  at org.apache.catalina.core.ApplicationFilterChain.internalDoFilter(ApplicationFilterChain.java:164)
2026-08-05T02:09:25.743329598Z  [content-service]  at org.springframework.security.web.access.ExceptionTranslationFilter.doFilter(ExceptionTranslationFilter.java:126)
2026-08-05T02:09:25.743332281Z  [content-service]  at org.springframework.security.web.access.ExceptionTranslationFilter.doFilter(ExceptionTranslationFilter.java:120)
2026-08-05T02:09:25.743574150Z  [content-service]  Caused by: org.hibernate.exception.DataException: could not execute statement [Data truncation: Data too long for column 'content' at row 1] [insert into tb_feed_comment (content,created_at,creator_id,creator_nickname,creator_profile_url,deleted,deleted_at,feed_id,parent_comment_id,updated_at) values (?,?,?,?,?,?,?,?,?,?)]
2026-08-05T02:09:25.743576013Z  [content-service]  at org.hibernate.exception.internal.SQLExceptionTypeDelegate.convert(SQLExceptionTypeDelegate.java:55)
2026-08-05T02:09:25.743577813Z  [content-service]  at org.hibernate.exception.internal.StandardSQLExceptionConverter.convert(StandardSQLExceptionConverter.java:58)
2026-08-05T02:09:25.743579530Z  [content-service]  at org.hibernate.engine.jdbc.spi.SqlExceptionHelper.convert(SqlExceptionHelper.java:108)
2026-08-05T02:09:25.743846186Z  [content-service]  at org.springframework.dao.support.PersistenceExceptionTranslationInterceptor.invoke(PersistenceExceptionTranslationInterceptor.java:138)
2026-08-05T02:09:25.743851815Z  [content-service]  at com.mysql.cj.jdbc.exceptions.SQLExceptionsMapping.translateException(SQLExceptionsMapping.java:96)
2026-08-05T02:09:25.745531986Z  [content-service]  2026-08-05 11:09:25.745 [http-nio-8082-exec-1] ERROR [traceId=6a729b55f741850507e3ddfd50cc4d65,spanId=07e3ddfd50cc4d65,userId=NONE] c.e.t.a.c.f.RequestLoggingFilter - [HTTP] POST /api/feeds/145/comments 500 - 118ms
2026-08-05T02:09:25.745531986Z  [content-service]  2026-08-05 11:09:25.745 [http-nio-8082-exec-1] ERROR [traceId=6a729b55f741850507e3ddfd50cc4d65,spanId=07e3ddfd50cc4d65,userId=NONE] c.e.t.a.c.f.RequestLoggingFilter - [HTTP] POST /api/feeds/145/comments 500 - 118ms
```

### 메트릭 시계열

| 쿼리 | series | 점 | min | max | last | 값이 0이던 구간 |
|---|---|---:|---:|---:|---:|---|
| `hikaricp_connections_active` | `{__name__=hikaricp_connections_active, application=auth-service, cluster=yogurtte-k3s-prod, container=auth-service, instance=10.42.1.46:8090, job=auth-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=auth-service-5999bb9f5c-hmgp9, pool=HikariPool-1, service=auth-service}` | 57 | 0 | 0 | 0 | **2026-08-05T02:00:00Z ~ 2026-08-05T02:14:00Z** |
| `hikaricp_connections_active` | `{__name__=hikaricp_connections_active, application=chat-service, cluster=yogurtte-k3s-prod, container=chat-service, instance=10.42.1.47:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-fdcc7c776-xf4sv, pool=HikariPool-1}` | 57 | 0 | 0 | 0 | **2026-08-05T02:00:00Z ~ 2026-08-05T02:14:00Z** |
| `hikaricp_connections_active` | `{__name__=hikaricp_connections_active, application=content-service, cluster=yogurtte-k3s-prod, container=content-service, instance=10.42.1.43:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-6995bb7d94-h2f6n, pool=HikariPool-1}` | 57 | 0 | 0 | 0 | **2026-08-05T02:00:00Z ~ 2026-08-05T02:14:00Z** |
| `hikaricp_connections_active` | `{__name__=hikaricp_connections_active, application=content-service, cluster=yogurtte-k3s-prod, container=content-service, instance=10.42.3.42:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-6995bb7d94-nq9l2, pool=HikariPool-1}` | 57 | 0 | 0 | 0 | **2026-08-05T02:00:00Z ~ 2026-08-05T02:14:00Z** |
| `hikaricp_connections_pending` | `{__name__=hikaricp_connections_pending, application=auth-service, cluster=yogurtte-k3s-prod, container=auth-service, instance=10.42.1.46:8090, job=auth-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=auth-service-5999bb9f5c-hmgp9, pool=HikariPool-1, service=auth-service}` | 57 | 0 | 0 | 0 | **2026-08-05T02:00:00Z ~ 2026-08-05T02:14:00Z** |
| `hikaricp_connections_pending` | `{__name__=hikaricp_connections_pending, application=chat-service, cluster=yogurtte-k3s-prod, container=chat-service, instance=10.42.1.47:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-fdcc7c776-xf4sv, pool=HikariPool-1}` | 57 | 0 | 0 | 0 | **2026-08-05T02:00:00Z ~ 2026-08-05T02:14:00Z** |
| `hikaricp_connections_pending` | `{__name__=hikaricp_connections_pending, application=content-service, cluster=yogurtte-k3s-prod, container=content-service, instance=10.42.1.43:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-6995bb7d94-h2f6n, pool=HikariPool-1}` | 57 | 0 | 0 | 0 | **2026-08-05T02:00:00Z ~ 2026-08-05T02:14:00Z** |
| `hikaricp_connections_pending` | `{__name__=hikaricp_connections_pending, application=content-service, cluster=yogurtte-k3s-prod, container=content-service, instance=10.42.3.42:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-6995bb7d94-nq9l2, pool=HikariPool-1}` | 57 | 0 | 0 | 0 | **2026-08-05T02:00:00Z ~ 2026-08-05T02:14:00Z** |
| `rate(jvm_gc_pause_seconds_sum[5m])` | `{action=end of major GC, application=chat-service, cause=Allocation Failure, cluster=yogurtte-k3s-prod, container=chat-service, gc=MarkSweepCompact, instance=10.42.1.47:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-fdcc7c776-xf4sv}` | 57 | 0 | 0 | 0 | **2026-08-05T02:00:00Z ~ 2026-08-05T02:14:00Z** |
| `rate(jvm_gc_pause_seconds_sum[5m])` | `{action=end of minor GC, application=auth-service, cause=G1 Evacuation Pause, cluster=yogurtte-k3s-prod, container=auth-service, gc=G1 Young Generation, instance=10.42.1.46:8090, job=auth-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=auth-service-5999bb9f5c-hmgp9, service=auth-service}` | 57 | 0 | 0.000 | 0.000 | **2026-08-05T02:00:00Z ~ 2026-08-05T02:11:30Z** |
| `rate(jvm_gc_pause_seconds_sum[5m])` | `{action=end of minor GC, application=chat-service, cause=Allocation Failure, cluster=yogurtte-k3s-prod, container=chat-service, gc=Copy, instance=10.42.1.47:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-fdcc7c776-xf4sv}` | 57 | 0.000 | 0.001 | 0.000 | — |
| `rate(jvm_gc_pause_seconds_sum[5m])` | `{action=end of minor GC, application=content-service, cause=Allocation Failure, cluster=yogurtte-k3s-prod, container=content-service, gc=Copy, instance=10.42.1.43:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-6995bb7d94-h2f6n}` | 57 | 0 | 0.000 | 0.000 | **2026-08-05T02:00:00Z ~ 2026-08-05T02:01:45Z, 2026-08-05T02:06:00Z ~ 2026-08-05T02:12:45Z** |
| `rate(jvm_gc_pause_seconds_sum[5m])` | `{action=end of minor GC, application=content-service, cause=Allocation Failure, cluster=yogurtte-k3s-prod, container=content-service, gc=Copy, instance=10.42.3.42:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-6995bb7d94-nq9l2}` | 57 | 0 | 0.000 | 0.000 | **2026-08-05T02:00:00Z ~ 2026-08-05T02:00:30Z, 2026-08-05T02:04:45Z ~ 2026-08-05T02:10:30Z** |
| `up` | `{__name__=up, app=node-exporter, cluster=yogurtte-k3s-prod, component=metrics, container=node-exporter, instance=ip-172-31-40-241, job=integrations/node_exporter, k8s_cluster_name=yogurtte-k3s-prod, namespace=monitoring, pod=grafana-k8s-monitoring-node-exporter-cph5l, source=kubernetes, workload=DaemonSet/grafana-k8s-monitoring-node-exporter}` | 57 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, app=node-exporter, cluster=yogurtte-k3s-prod, component=metrics, container=node-exporter, instance=ip-172-31-45-39, job=integrations/node_exporter, k8s_cluster_name=yogurtte-k3s-prod, namespace=monitoring, pod=grafana-k8s-monitoring-node-exporter-9kvqt, source=kubernetes, workload=DaemonSet/grafana-k8s-monitoring-node-exporter}` | 57 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, cluster=yogurtte-k3s-prod, container=auth-service, instance=10.42.1.46:8090, job=auth-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=auth-service-5999bb9f5c-hmgp9}` | 57 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, cluster=yogurtte-k3s-prod, container=chat-service, instance=10.42.1.47:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-fdcc7c776-xf4sv}` | 57 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, cluster=yogurtte-k3s-prod, container=content-service, instance=10.42.1.43:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-6995bb7d94-h2f6n}` | 57 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, cluster=yogurtte-k3s-prod, container=content-service, instance=10.42.3.42:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-6995bb7d94-nq9l2}` | 57 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, cluster=yogurtte-k3s-prod, instance=10.42.1.248:8080, job=integrations/kubernetes/kube-state-metrics, k8s_cluster_name=yogurtte-k3s-prod, source=kubernetes}` | 57 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, cluster=yogurtte-k3s-prod, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod}` | 57 | 1 | 1 | 1 | — |
| `mongodb_up` | `{__name__=mongodb_up, cluster=yogurtte-k3s-prod, instance=infra-server, job=mongodb, k8s_cluster_name=yogurtte-k3s-prod}` | 57 | 1 | 1 | 1 | — |
| `kafka_brokers` | `{__name__=kafka_brokers, cluster=yogurtte-k3s-prod, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod}` | 57 | 1 | 1 | 1 | — |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=chat-service-fcm-tokens, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=0, topic=user.fcm-tokens}` | 57 | 0 | 0 | 0 | **2026-08-05T02:00:00Z ~ 2026-08-05T02:14:00Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=chat-service-fcm-tokens, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=1, topic=user.fcm-tokens}` | 57 | 0 | 0 | 0 | **2026-08-05T02:00:00Z ~ 2026-08-05T02:14:00Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=chat-service-fcm-tokens, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=2, topic=user.fcm-tokens}` | 57 | 0 | 0 | 0 | **2026-08-05T02:00:00Z ~ 2026-08-05T02:14:00Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=chat-service-notification-settings, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=0, topic=user.notification-settings}` | 57 | 0 | 0 | 0 | **2026-08-05T02:00:00Z ~ 2026-08-05T02:14:00Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=chat-service-notification-settings, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=1, topic=user.notification-settings}` | 57 | 0 | 0 | 0 | **2026-08-05T02:00:00Z ~ 2026-08-05T02:14:00Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=chat-service-notification-settings, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=2, topic=user.notification-settings}` | 57 | 0 | 0 | 0 | **2026-08-05T02:00:00Z ~ 2026-08-05T02:14:00Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=db-writer, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=0, topic=chat.messages}` | 57 | 0 | 0 | 0 | **2026-08-05T02:00:00Z ~ 2026-08-05T02:14:00Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=db-writer, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=1, topic=chat.messages}` | 57 | 0 | 0 | 0 | **2026-08-05T02:00:00Z ~ 2026-08-05T02:14:00Z** |
| `websocket_active_users` | `{__name__=websocket_active_users, application=chat-service, cluster=yogurtte-k3s-prod, container=chat-service, instance=10.42.1.47:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-fdcc7c776-xf4sv}` | 57 | 0 | 0 | 0 | **2026-08-05T02:00:00Z ~ 2026-08-05T02:14:00Z** |

값이 0이던 구간은 굵게 표시했다 — 프로세스가 사라져 시계열이 꺾인 것이 유일한 신호인 장애가 있다.

