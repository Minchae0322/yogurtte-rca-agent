# RCA Report — `6a694fc02926ea82d0bdbb4434237626`

| 항목 | 값 |
|---|---|
| 모드 | rca |
| 질문 | 최근 1시간 안에 피드 작성이 실패했다는 제보가 있다. 원인을 조사해줘 |
| 시각 | 2026-07-29T01:04:35.897187900Z |
| provider | claude-cli |
| model | `claude-opus-5` · turns 1 |
| prompt | `.\prompts\system-prompt.md` |
| tokens | in 90359 (cacheRead 23,453 · cacheCreate 66,904) / out 10239 · cost $0.9863 |
| elapsed | total 166039ms (tempo 494 · loki 275 · mimir 2429 · assemble 2 · llm 153189) |

## 탐색 (Triage)

| 항목 | 값 |
|---|---|
| 시간창 해석 | 상대 표현 '최근 1시간' |
| 스윕 창 | 2026-07-29T00:03:43.616500200Z ~ 2026-07-29T01:03:43.616500200Z |
| 좁힌 창 | 2026-07-29T00:53:00Z ~ 2026-07-29T01:03:43Z |
| 대상 | content-service |
| traceId | 6a694fc02926ea82d0bdbb4434237626 |
| 트레이스 후보 | 1건 |
| 계획 파싱 | 성공 |
| prompt | `.\prompts\triage-prompt.md` |
| tokens | in 47963 / out 3030 · cost $0.3765 |
| chars | 컨텍스트 38,040 + 프롬프트 1,231 = **39,271** |
| elapsed | survey 1194ms · llm 51058ms |

**선정 이유**: 유일한 에러 트레이스(00:56:32Z)와 유일한 ERROR/WARN 버킷(01:00:00Z, 직전 5분 집계)이 동일 실패를 가리키고 둘 다 content-service에만 나타나므로, 그 앞뒤 여유를 둔 00:53~01:03 구간의 content-service를 정밀 조회 대상으로 잡았다.

**근거**

- Tempo: 조회 1시간 중 유일한 에러 트레이스가 content-service 루트 'http post /feeds', 시작 2026-07-29T00:56:32.321Z, durationMs=74, serviceStats.content-service = spanCount 23 / errorCount 1 (에러 스팬 10c8313bf6e4c25c, 00:56:32.370Z, 6.2ms)
- Loki: ERROR/WARN 시계열이 content-service 단일 시리즈이고 값이 1785286800(=2026-07-29T01:00:00Z) 버킷의 '4' 한 점뿐 — 나머지 버킷과 auth-service/chat-service는 전 구간 0건이라 실패가 content-service에 국한됨
- Mimir up: content-service 파드 2개(10.42.1.35:8090, 10.42.3.41:8090) 및 auth/chat 모두 00:03:43~01:03:43 전 구간 1 유지 — 파드 재시작·트래픽 미진입으로 인한 '트레이스 소실' 시나리오는 배제됨
- Mimir 인프라: mongodb_up=1, kafka_brokers=1, 모든 consumergroup lag 0(값 -1은 미할당 파티션) — 저장소/메시징 정체 흔적 없음, 74ms 즉시 실패와도 부합
- content -> Kafka -> chat 하류에 아무 신호가 없음(chat 로그 0건, notification/db-writer lag 0)은 발행 이전 단계에서 요청이 끊겼음을 시사

**스윕이 찾은 트레이스** (고른 것은 6a694fc02926ea82d0bdbb4434237626)

| traceId | root service | root span | ms |
|---|---|---|---:|
| `6a694fc02926ea82d0bdbb4434237626` ←선택 | content-service | http post /feeds | 74 |

## 수집 범위 (Coverage)

- **window**: 2026-07-29T00:53:00Z ~ 2026-07-29T01:03:43Z (643s)
- **trace**: 18,907B / 23 spans
- **logs**: errwarn=5,709B · traceId=5,708B
- **metrics**: 8 수집 / 84,359B, 누락 [sum(rate(http_server_requests_seconds_count{application="content-service", status="401"}[1m]))]
- **context**: 115,328 chars (+ 시스템 프롬프트 597 chars)

### 토큰 축 (개선 지표)

| 단계 | 총 in | chars | 컨텍스트 토큰 |
|---|---:|---:|---:|
| 탐색 | 47,963 | 39,271 | 21,387 |
| 분석 | 90,359 | 115,925 | 63,783 |
| **합계** | **138,322** | | **85,170** |

- **overheadTokens 26,576 tok — 이 회차에 실측**(1자 프롬프트 프로브, 같은 명령·모델·샌드박스).
  호출마다 한 번씩 얹히므로 단계별로 뺀다. **다른 날 상수를 쓰지 않았다 = `█ 실측`.**
- contextTokens (count_tokens API): 측정 안 됨 — 구독 CLI 경로엔 API 키가 없다
- 비용은 **API 환산 추정치**다. 구독 계정으로 호출하므로 실제 청구액이 아니다.

## 수집 실패/누락

- Metric 'sum(rate(http_server_requests_seconds_count{application="content-service", status="401"}[1m]))' returned no series in this window; skipped.

---

# 장애 분석: traceId `6a694fc02926ea82d0bdbb4434237626`

**결론 요약:** 인프라 장애가 아니라, **단일 피드 작성 요청 내부에서 동일한 해시태그 연결(`feed_id=151`, `hashtag_id=174`)을 두 번 INSERT** 하면서 유니크 제약 `uk_feed_hashtag`를 위반 → 트랜잭션 전체 롤백 → HTTP 500.

---

## 1. 원인 후보 랭킹 (최대 3개)

1. **단일 요청 내 중복 해시태그 저장 시도 → `tb_feed_hashtags.uk_feed_hashtag` 유니크 제약 위반** (직접 원인, 확정에 가까움)
2. **해시태그 이름 조회/정규화 불일치** — 서로 다른 입력 문자열이 DB 조회에서는 동일 행(`id=174`)으로 귀결되어, 애플리케이션이 별개 태그로 오인하고 재삽입
3. **`DataIntegrityViolationException` 전용 핸들러 부재** — `handleAllException`으로 폴백되어 4xx가 아닌 **500 + 피드 전체 롤백**으로 증폭 (증상 확대 요인)

---

## 2. 후보별 근거

### 후보 1 — 중복 해시태그 연결 INSERT로 인한 유니크 제약 위반

- **근거 (트레이스 시간순, 요청 시작 `00:56:32.321Z` 기준):**

  | 시각(ms) | span / 이벤트 | 결과 |
  |---|---|---|
  | .327 | `connection` (HikariPool-1, `com.mysql.cj.jdbc.Driver`) | `acquired` |
  | .330 | `query` `select ... from categories c1_0 where c1_0.category_id=?` | `jdbc.row-count=1` |
  | .336 | `query` `insert into tb_feed (...)` | `jdbc.generated-keys=**151**` |
  | .339 / .342 | `insert into tb_feed_attachment_file` ×2 | keys `231`, `232` |
  | .346 | `query` `select h1_0.id ... from tb_hashtags h1_0 where h1_0.name=?` | `jdbc.row-count=**0**` |
  | .350 | `query` `insert into tb_hashtags (...)` | `jdbc.generated-keys=**174**` |
  | .355 | `query` `insert into tb_feed_hashtags (created_at,feed_id,hashtag_id,updated_at)` | key `216` (성공) |
  | .361 | `query` `update tb_hashtags set updated_at=?,usage_count=? where id=?` | `row-affected=1` |
  | .364 | `query` `select h1_0.id ... where h1_0.name=?` (**두 번째 조회**) | `jdbc.row-count=**1**` |
  | .370 | `query` `insert into tb_feed_hashtags (...)` | `error: Duplicate entry '151-174' for key 'tb_feed_hashtags.uk_feed_hashtag'`, `status.code=STATUS_CODE_ERROR` |
  | .383 | `connection` 이벤트 | `rollback` |
  | .394 | 루트 span `http post /feeds` | `outcome=SERVER_ERROR`, `status=500` |

- **핵심 추론:** 중복 키 값 `'151-174'` 중 `174`는 **같은 트랜잭션 내 .350에서 방금 생성된** 해시태그다(`jdbc.generated-keys=174`). 즉 다른 요청/다른 사용자가 끼어든 동시성 충돌이 아니라, **이 요청 하나가 같은 (피드, 해시태그) 쌍을 두 번 넣으려 한 것**이다. `feed_id=151` 역시 .336에서 이 트랜잭션이 생성했으므로 외부 요청이 선점했을 가능성은 구조적으로 배제된다.
- **로그 원문:**
  - `WARN ... o.h.e.jdbc.spi.SqlExceptionHelper - SQL Error: 1062, SQLState: 23000`
  - `ERROR ... o.h.e.jdbc.spi.SqlExceptionHelper - Duplicate entry '151-174' for key 'tb_feed_hashtags.uk_feed_hashtag'` (userId=1)
  - `ERROR ... RequestLoggingFilter - [HTTP] POST /api/feeds 500 - 73ms`
- **확신도:** **높음**
- **반증 데이터:** 없음. (인프라 측 반증은 오히려 이 후보를 강화한다 — `hikaricp_connections_pending`은 content-service 전 구간 `0`, `hikaricp_connections_active`는 최대 `1`이라 풀 고갈 아님. content-service GC pause rate 최대 `3.75e-5`s/s로 무시 가능. `up{job="content-service"}`=1 유지, `kafka_brokers`=1, `mongodb_up`=1, 전 컨슈머그룹 lag=0.)

---

### 후보 2 — 해시태그 이름 정규화/조회 불일치

- **근거:** 첫 번째 `select ... where h1_0.name=?`는 `row-count=0`(신규 → `id=174` 생성), 두 번째 동일 쿼리는 `row-count=1`이고 이후 삽입이 `hashtag_id=174`로 충돌했다. 두 번의 조회가 **같은 행으로 수렴**했다는 뜻이므로 가능한 경우는 두 가지뿐이다.
  1. 요청 payload에 **문자 그대로 동일한 해시태그가 2개** 들어 있었고 애플리케이션이 dedupe하지 않음
  2. 입력은 서로 달랐으나(`#Seoul` vs `#seoul`, 공백/유니코드 정규화 차이 등) MySQL 컬레이션(예: `utf8mb4_general_ci`)상 `name=?` 조회가 같은 행에 매칭됨 → 애플리케이션 레벨의 dedupe 키와 DB 유니크 키의 판정 기준이 어긋남
  두 경우 모두 "요청 단위 dedupe 부재 + `tb_feed_hashtags` 삽입 전 존재 확인 부재"라는 동일한 코드 결함을 가리킨다.
- **확신도:** **낮음** (메커니즘은 확실하나 1과 2 중 어느 쪽인지 구분 불가). 트레이스에 **바인드 파라미터가 없어 실제 해시태그 문자열을 알 수 없음 → 이 지점은 데이터 부족.**
- **반증 데이터:** 없음.

---

### 후보 3 — 예외 매핑 부재로 인한 500 및 전체 롤백

- **근거:** `WARN ... c.e.t.a.c.e.GlobalExceptionHandler - handleAllException` — 제약 위반이 전용 핸들러가 아닌 **범용 catch-all**로 처리됐다. 결과적으로 루트 span은 `outcome=SERVER_ERROR`, `status=500`이고, 스팬 속성 `exception`은 `"none"`으로 남아 있다(핸들러가 예외를 흡수해 span에 기록되지 않음 → 관측성 저하). 또한 `.383`의 `rollback` 이벤트로 **피드 본문(151)과 첨부 2건(231, 232)까지 전부 취소**되어, 사용자 입력 오류 성격의 실패가 "피드 작성 자체 실패"로 확대됐다.
- **확신도:** **중간** (로그상 `handleAllException` 도달과 500 반환은 확정이나, 핸들러 코드/의도된 매핑 정책은 관측 데이터로 확인 불가)
- **반증 데이터:** 없음. 단, 이는 500의 **표면화 경로**이지 쓰기 실패 자체의 근본 원인은 아니다 — 예외 매핑을 고쳐도 후보 1을 고치지 않으면 피드 저장은 계속 실패한다.

---

### 배제된 후보 (반증 근거 명시)

| 가설 | 반증 데이터 |
|---|---|
| DB 커넥션 풀 고갈 | `hikaricp_connections_pending` 전 시리즈·전 구간 `0`, `active` 최대 `1` (한도 대비 여유). 커넥션 획득은 `.327`에 즉시 성공(`acquired`) |
| GC / 메모리 압박 | content-service `rate(jvm_gc_pause_seconds_sum[5m])` 최대 `0.0000375`, major GC 시리즈 없음 |
| 파드/노드 다운, 롤링 배포 | `up`이 content(2 파드), auth, chat, kafka, mongodb, redis, 전 노드에서 전 구간 `1` |
| Kafka 지연/컨슈머 장애로 인한 피드 실패 | 전 컨슈머그룹 lag `0`, `kafka_brokers=1` 유지. 애초에 트레이스에 **Kafka producer span이 없고**, 실패는 발행 이전 DB 단계에서 종료 |
| auth 서비스 인증 실패(401) | 이 트레이스는 `JwtAuthenticationFilter` 포함 12개 필터를 모두 통과(`before/after` 이벤트 전량 존재)했고 DB 로그에 `userId=1`이 기록됨. 응답 로그의 `userId=NONE`은 SecurityContext 정리 후 `RequestLoggingFilter` 시점 값이라 인증 실패 근거가 아님. **단, 401 메트릭은 시리즈 없음으로 수집 실패 → 전역 401 추세는 확인 불가** |
| Kafka lag `-1` 파티션 (db-writer/notification p2·p6·p9·p10, dlq p1) | 전 구간 상수 `-1`(커밋된 오프셋 없음)로, 이번 요청 시각 전후 변화 없음 → 무관한 상시 노이즈 |

---

### 데이터 부족 항목 (결론 확신도 하향 요인)

- **조회 시간창이 제보 범위를 못 덮음:** 요청은 "최근 1시간"이나 실제 창은 `00:53:00Z ~ 01:03:43Z`(약 10.7분). **나머지 약 49분 미조회** → 재발 빈도·영향 사용자 수·다른 실패 유형 존재 여부는 판단 불가.
- **401 메트릭 수집 실패** (`http_server_requests_seconds_count{application="content-service", status="401"}` 시리즈 없음). 시리즈 부재가 "401이 0건"인지 "메트릭명/라벨 불일치"인지 구분 불가 — 인증 계열 후보를 **완전히 배제하지는 못함**.
- **SQL 바인드 파라미터 부재**로 실제 해시태그 문자열 확인 불가 → 후보 2의 두 시나리오 판별 불가.
- 창 내 ERROR/WARN 조회 결과는 **총 4줄, 전부 이 traceId** 뿐이다(`totalEntriesReturned: 4`). content-service 파드에서 유사한 DB 활동 구간이 2회 더 관측되나(`active=1` @ `1785286815~860`, `1785286935~980`) 에러 로그가 없어 **성공한 것으로 보이며**, 이는 "특정 입력에서만 터지는 결함"이라는 해석을 뒷받침한다. 다만 트래픽 총량 메트릭이 없어 실패율 산정은 불가.

---

## 3. 권장 다음 조치

**즉시 확인 (읽기 전용)**

1. **시간창 확장 재조회** — `00:03:43Z ~ 01:03:43Z` 전체 1시간에 대해 Loki에서 `{job="default/content-service"} |= "uk_feed_hashtag"` 및 `|= "SQLState: 23000"` 검색 → 재발 횟수, 영향 `userId` 목록 집계.
2. **DB 상태 확인** — `SELECT * FROM tb_feed WHERE feed_id=151;` (롤백이 정상 동작했다면 **0건이어야 함**), `SELECT * FROM tb_hashtags WHERE id=174;`, `SELECT * FROM tb_feed_hashtags WHERE hashtag_id=174;` → 부분 커밋으로 인한 고아 데이터 유무 확인.
3. **스키마/컬레이션 확인** — `SHOW CREATE TABLE tb_feed_hashtags;` (`uk_feed_hashtag` 구성 컬럼), `SHOW FULL COLUMNS FROM tb_hashtags LIKE 'name';` (컬레이션이 `_ci`면 후보 2-②가 성립).
4. **메트릭 쿼리 교정** — 401 시리즈 부재 원인 규명. `count by (status, uri) (http_server_requests_seconds_count{application="content-service"})`로 실제 라벨명(`application` vs `job`/`service`)과 존재하는 status 값 확인 후, `status="500", uri="/feeds"` 계열 재조회로 실패 건수 정량화.

**코드 수정 (근본 대응)**

5. **요청 단위 해시태그 dedupe** — 피드 저장 서비스에서 해시태그 목록을 **정규화(trim + 소문자화 등 DB 컬레이션과 동일 기준) 후 `distinct`** 처리하고 나서 연결 테이블에 저장. DB 유니크 키 판정 기준과 애플리케이션 dedupe 기준을 일치시킬 것.
6. **연결 삽입 멱등화** — `tb_feed_hashtags` 삽입을 `INSERT ... ON DUPLICATE KEY UPDATE` 또는 `INSERT IGNORE`로 전환하거나, 삽입 전 `(feed_id, hashtag_id)` 존재 확인. 이 경로는 `usage_count` 증분(`update tb_hashtags set usage_count=?`)도 중복 호출되므로 **집계값 오염 여부도 함께 점검**.
7. **예외 매핑 추가** — `GlobalExceptionHandler`에 `DataIntegrityViolationException`(및 `ConstraintViolationException`) 전용 핸들러를 두어 409/400으로 응답하고, `handleAllException` 폴백 시에는 스택트레이스와 `exception` span 속성을 반드시 기록하도록 수정(현재 루트 span의 `exception="none"`은 트리아지를 방해함).

**후속 확인**

8. **알림 누락 영향 평가** — 이 트레이스에는 Kafka producer span이 없다. 롤백으로 이벤트가 아예 발행되지 않은 것인지, 아니면 발행 경로가 미계측인 것인지 확인 필요(전자라면 `content -> Kafka -> chat` 알림도 함께 누락됨). content-service의 Kafka 프로듀서 계측 활성화를 권장.
9. 5·6번 수정 후 동일 해시태그를 중복 포함한 요청으로 회귀 테스트하여 200 응답 및 연결 1건만 생성됨을 확인.

---

## 관측 증거 (Evidence)

> 이 조사가 실제로 본 값이다. 원본 응답 전체는 `reports/raw/6a694fc02926ea82d0bdbb4434237626-*.json`에 있다.

### span (duration 상위 15 / 전체 23)

| ms | service | span | 시작 |
|---:|---|---|---|
| 74.31 | content-service | `http post /feeds` | 2026-07-29T00:56:32.321490Z |
| 71.93 | content-service | `secured request` | 2026-07-29T00:56:32.321929Z |
| 66.29 | content-service | `connection` | 2026-07-29T00:56:32.327455Z |
| 6.21 | content-service | `query` | 2026-07-29T00:56:32.370415Z |
| 2.55 | content-service | `query` | 2026-07-29T00:56:32.361400Z |
| 1.85 | content-service | `query` | 2026-07-29T00:56:32.336249Z |
| 1.77 | content-service | `query` | 2026-07-29T00:56:32.364451Z |
| 1.71 | content-service | `query` | 2026-07-29T00:56:32.355824Z |
| 1.68 | content-service | `query` | 2026-07-29T00:56:32.330466Z |
| 1.67 | content-service | `query` | 2026-07-29T00:56:32.350373Z |
| 1.61 | content-service | `query` | 2026-07-29T00:56:32.339450Z |
| 1.60 | content-service | `query` | 2026-07-29T00:56:32.346005Z |
| 1.55 | content-service | `query` | 2026-07-29T00:56:32.342232Z |
| 0.24 | content-service | `generated-keys` | 2026-07-29T00:56:32.352219Z |
| 0.22 | content-service | `generated-keys` | 2026-07-29T00:56:32.341329Z |

### 로그 원문 (8 / 전체 8줄)

```
2026-07-29T00:56:32.377738423Z  [content-service]  2026-07-29 09:56:32.376 [http-nio-8082-exec-4]  WARN [traceId=6a694fc02926ea82d0bdbb4434237626,spanId=bf3e2380fd2992ce,userId=1] o.h.e.jdbc.spi.SqlExceptionHelper - SQL Error: 1062, SQLState: 23000
2026-07-29T00:56:32.377738423Z  [content-service]  2026-07-29 09:56:32.376 [http-nio-8082-exec-4]  WARN [traceId=6a694fc02926ea82d0bdbb4434237626,spanId=bf3e2380fd2992ce,userId=1] o.h.e.jdbc.spi.SqlExceptionHelper - SQL Error: 1062, SQLState: 23000
2026-07-29T00:56:32.377847074Z  [content-service]  2026-07-29 09:56:32.376 [http-nio-8082-exec-4] ERROR [traceId=6a694fc02926ea82d0bdbb4434237626,spanId=bf3e2380fd2992ce,userId=1] o.h.e.jdbc.spi.SqlExceptionHelper - Duplicate entry '151-174' for key 'tb_feed_hashtags.uk_feed_hashtag'
2026-07-29T00:56:32.377847074Z  [content-service]  2026-07-29 09:56:32.376 [http-nio-8082-exec-4] ERROR [traceId=6a694fc02926ea82d0bdbb4434237626,spanId=bf3e2380fd2992ce,userId=1] o.h.e.jdbc.spi.SqlExceptionHelper - Duplicate entry '151-174' for key 'tb_feed_hashtags.uk_feed_hashtag'
2026-07-29T00:56:32.392461078Z  [content-service]  2026-07-29 09:56:32.385 [http-nio-8082-exec-4]  WARN [traceId=6a694fc02926ea82d0bdbb4434237626,spanId=bf3e2380fd2992ce,userId=1] c.e.t.a.c.e.GlobalExceptionHandler - handleAllException
2026-07-29T00:56:32.392461078Z  [content-service]  2026-07-29 09:56:32.385 [http-nio-8082-exec-4]  WARN [traceId=6a694fc02926ea82d0bdbb4434237626,spanId=bf3e2380fd2992ce,userId=1] c.e.t.a.c.e.GlobalExceptionHandler - handleAllException
2026-07-29T00:56:32.394234362Z  [content-service]  2026-07-29 09:56:32.394 [http-nio-8082-exec-4] ERROR [traceId=6a694fc02926ea82d0bdbb4434237626,spanId=d0bdbb4434237626,userId=NONE] c.e.t.a.c.f.RequestLoggingFilter - [HTTP] POST /api/feeds 500 - 73ms
2026-07-29T00:56:32.394234362Z  [content-service]  2026-07-29 09:56:32.394 [http-nio-8082-exec-4] ERROR [traceId=6a694fc02926ea82d0bdbb4434237626,spanId=d0bdbb4434237626,userId=NONE] c.e.t.a.c.f.RequestLoggingFilter - [HTTP] POST /api/feeds 500 - 73ms
```

### 메트릭 시계열

| 쿼리 | series | 점 | min | max | last | 값이 0이던 구간 |
|---|---|---:|---:|---:|---:|---|
| `hikaricp_connections_active` | `{__name__=hikaricp_connections_active, application=auth-service, cluster=yogurtte-k3s-prod, container=auth-service, instance=10.42.1.34:8090, job=auth-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=auth-service-855c75679d-45fxb, pool=HikariPool-1, service=auth-service}` | 43 | 0 | 0 | 0 | **2026-07-29T00:53:00Z ~ 2026-07-29T01:03:30Z** |
| `hikaricp_connections_active` | `{__name__=hikaricp_connections_active, application=chat-service, cluster=yogurtte-k3s-prod, container=chat-service, instance=10.42.1.31:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-857c54dd97-s5fbl, pool=HikariPool-1}` | 43 | 0 | 0 | 0 | **2026-07-29T00:53:00Z ~ 2026-07-29T01:03:30Z** |
| `hikaricp_connections_active` | `{__name__=hikaricp_connections_active, application=content-service, cluster=yogurtte-k3s-prod, container=content-service, instance=10.42.1.35:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-6c5fff897-qnxk6, pool=HikariPool-1}` | 43 | 0 | 1 | 0 | **2026-07-29T00:53:00Z ~ 2026-07-29T00:56:00Z, 2026-07-29T00:57:15Z ~ 2026-07-29T01:00:00Z, 2026-07-29T01:01:15Z ~ 2026-07-29T01:02:00Z, 2026-07-29T01:03:15Z ~ 2026-07-29T01:03:30Z** |
| `hikaricp_connections_active` | `{__name__=hikaricp_connections_active, application=content-service, cluster=yogurtte-k3s-prod, container=content-service, instance=10.42.3.41:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-6c5fff897-scw7k, pool=HikariPool-1}` | 43 | 0 | 0 | 0 | **2026-07-29T00:53:00Z ~ 2026-07-29T01:03:30Z** |
| `hikaricp_connections_pending` | `{__name__=hikaricp_connections_pending, application=auth-service, cluster=yogurtte-k3s-prod, container=auth-service, instance=10.42.1.34:8090, job=auth-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=auth-service-855c75679d-45fxb, pool=HikariPool-1, service=auth-service}` | 43 | 0 | 0 | 0 | **2026-07-29T00:53:00Z ~ 2026-07-29T01:03:30Z** |
| `hikaricp_connections_pending` | `{__name__=hikaricp_connections_pending, application=chat-service, cluster=yogurtte-k3s-prod, container=chat-service, instance=10.42.1.31:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-857c54dd97-s5fbl, pool=HikariPool-1}` | 43 | 0 | 0 | 0 | **2026-07-29T00:53:00Z ~ 2026-07-29T01:03:30Z** |
| `hikaricp_connections_pending` | `{__name__=hikaricp_connections_pending, application=content-service, cluster=yogurtte-k3s-prod, container=content-service, instance=10.42.1.35:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-6c5fff897-qnxk6, pool=HikariPool-1}` | 43 | 0 | 0 | 0 | **2026-07-29T00:53:00Z ~ 2026-07-29T01:03:30Z** |
| `hikaricp_connections_pending` | `{__name__=hikaricp_connections_pending, application=content-service, cluster=yogurtte-k3s-prod, container=content-service, instance=10.42.3.41:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-6c5fff897-scw7k, pool=HikariPool-1}` | 43 | 0 | 0 | 0 | **2026-07-29T00:53:00Z ~ 2026-07-29T01:03:30Z** |
| `rate(jvm_gc_pause_seconds_sum[5m])` | `{action=end of major GC, application=chat-service, cause=Allocation Failure, cluster=yogurtte-k3s-prod, container=chat-service, gc=MarkSweepCompact, instance=10.42.1.31:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-857c54dd97-s5fbl}` | 43 | 0 | 0 | 0 | **2026-07-29T00:53:00Z ~ 2026-07-29T01:03:30Z** |
| `rate(jvm_gc_pause_seconds_sum[5m])` | `{action=end of minor GC, application=auth-service, cause=G1 Evacuation Pause, cluster=yogurtte-k3s-prod, container=auth-service, gc=G1 Young Generation, instance=10.42.1.34:8090, job=auth-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=auth-service-855c75679d-45fxb, service=auth-service}` | 43 | 0 | 0 | 0 | **2026-07-29T00:53:00Z ~ 2026-07-29T01:03:30Z** |
| `rate(jvm_gc_pause_seconds_sum[5m])` | `{action=end of minor GC, application=chat-service, cause=Allocation Failure, cluster=yogurtte-k3s-prod, container=chat-service, gc=Copy, instance=10.42.1.31:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-857c54dd97-s5fbl}` | 43 | 0.000 | 0.000 | 0.000 | — |
| `rate(jvm_gc_pause_seconds_sum[5m])` | `{action=end of minor GC, application=content-service, cause=Allocation Failure, cluster=yogurtte-k3s-prod, container=content-service, gc=Copy, instance=10.42.1.35:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-6c5fff897-qnxk6}` | 43 | 0 | 0.000 | 0.000 | **2026-07-29T00:55:15Z ~ 2026-07-29T01:01:00Z** |
| `rate(jvm_gc_pause_seconds_sum[5m])` | `{action=end of minor GC, application=content-service, cause=Allocation Failure, cluster=yogurtte-k3s-prod, container=content-service, gc=Copy, instance=10.42.3.41:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-6c5fff897-scw7k}` | 43 | 0 | 0.000 | 0.000 | **2026-07-29T00:55:00Z ~ 2026-07-29T01:02:45Z** |
| `up` | `{__name__=up, app=node-exporter, cluster=yogurtte-k3s-prod, component=metrics, container=node-exporter, instance=ip-172-31-40-241, job=integrations/node_exporter, k8s_cluster_name=yogurtte-k3s-prod, namespace=monitoring, pod=grafana-k8s-monitoring-node-exporter-cph5l, source=kubernetes, workload=DaemonSet/grafana-k8s-monitoring-node-exporter}` | 43 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, app=node-exporter, cluster=yogurtte-k3s-prod, component=metrics, container=node-exporter, instance=ip-172-31-45-39, job=integrations/node_exporter, k8s_cluster_name=yogurtte-k3s-prod, namespace=monitoring, pod=grafana-k8s-monitoring-node-exporter-9kvqt, source=kubernetes, workload=DaemonSet/grafana-k8s-monitoring-node-exporter}` | 43 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, cluster=yogurtte-k3s-prod, container=auth-service, instance=10.42.1.34:8090, job=auth-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=auth-service-855c75679d-45fxb}` | 43 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, cluster=yogurtte-k3s-prod, container=chat-service, instance=10.42.1.31:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-857c54dd97-s5fbl}` | 43 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, cluster=yogurtte-k3s-prod, container=content-service, instance=10.42.1.35:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-6c5fff897-qnxk6}` | 43 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, cluster=yogurtte-k3s-prod, container=content-service, instance=10.42.3.41:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-6c5fff897-scw7k}` | 43 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, cluster=yogurtte-k3s-prod, instance=10.42.1.248:8080, job=integrations/kubernetes/kube-state-metrics, k8s_cluster_name=yogurtte-k3s-prod, source=kubernetes}` | 43 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, cluster=yogurtte-k3s-prod, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod}` | 43 | 1 | 1 | 1 | — |
| `mongodb_up` | `{__name__=mongodb_up, cluster=yogurtte-k3s-prod, instance=infra-server, job=mongodb, k8s_cluster_name=yogurtte-k3s-prod}` | 43 | 1 | 1 | 1 | — |
| `kafka_brokers` | `{__name__=kafka_brokers, cluster=yogurtte-k3s-prod, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod}` | 43 | 1 | 1 | 1 | — |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=chat-service-fcm-tokens, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=0, topic=user.fcm-tokens}` | 43 | 0 | 0 | 0 | **2026-07-29T00:53:00Z ~ 2026-07-29T01:03:30Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=chat-service-fcm-tokens, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=1, topic=user.fcm-tokens}` | 43 | 0 | 0 | 0 | **2026-07-29T00:53:00Z ~ 2026-07-29T01:03:30Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=chat-service-fcm-tokens, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=2, topic=user.fcm-tokens}` | 43 | 0 | 0 | 0 | **2026-07-29T00:53:00Z ~ 2026-07-29T01:03:30Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=chat-service-notification-settings, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=0, topic=user.notification-settings}` | 43 | 0 | 0 | 0 | **2026-07-29T00:53:00Z ~ 2026-07-29T01:03:30Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=chat-service-notification-settings, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=1, topic=user.notification-settings}` | 43 | 0 | 0 | 0 | **2026-07-29T00:53:00Z ~ 2026-07-29T01:03:30Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=chat-service-notification-settings, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=2, topic=user.notification-settings}` | 43 | 0 | 0 | 0 | **2026-07-29T00:53:00Z ~ 2026-07-29T01:03:30Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=db-writer, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=0, topic=chat.messages}` | 43 | 0 | 0 | 0 | **2026-07-29T00:53:00Z ~ 2026-07-29T01:03:30Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=db-writer, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=1, topic=chat.messages}` | 43 | 0 | 0 | 0 | **2026-07-29T00:53:00Z ~ 2026-07-29T01:03:30Z** |
| `websocket_active_users` | `{__name__=websocket_active_users, application=chat-service, cluster=yogurtte-k3s-prod, container=chat-service, instance=10.42.1.31:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-857c54dd97-s5fbl}` | 43 | 0 | 0 | 0 | **2026-07-29T00:53:00Z ~ 2026-07-29T01:03:30Z** |

값이 0이던 구간은 굵게 표시했다 — 프로세스가 사라져 시계열이 꺾인 것이 유일한 신호인 장애가 있다.

