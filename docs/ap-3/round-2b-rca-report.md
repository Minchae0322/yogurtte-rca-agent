# RCA Report — `6a69c37f7a3eebf11d620864776781be`

| 항목 | 값 |
|---|---|
| 모드 | rca |
| 질문 | 최근 1시간 안에 피드 작성이 실패했다는 제보가 있다. 원인을 조사해줘 |
| 시각 | 2026-07-29T09:14:48.945550100Z |
| provider | claude-cli |
| model | `claude-opus-5` · turns 1 |
| prompt | `.\prompts\system-prompt.md` |
| tokens | in 138903 (cacheRead 23,453 · cacheCreate 115,448) / out 7908 · cost $1.4526 |
| elapsed | total 129709ms (tempo 523 · loki 222 · mimir 827 · assemble 5 · llm 122210) |

## 탐색 (Triage)

| 항목 | 값 |
|---|---|
| 시간창 해석 | 상대 표현 '최근 1시간' |
| 스윕 창 | 2026-07-29T08:13:16.881601100Z ~ 2026-07-29T09:13:16.881601100Z |
| 좁힌 창 | 2026-07-29T08:49:00Z ~ 2026-07-29T09:13:16Z |
| 대상 | content-service, auth-service |
| traceId | 6a69c37f7a3eebf11d620864776781be |
| 트레이스 후보 | 2건 |
| 계획 파싱 | 성공 |
| prompt | `.\prompts\triage-prompt.md` |
| tokens | in 48311 / out 6405 · cost $0.4374 |
| chars | 컨텍스트 38,773 + 프롬프트 1,231 = **40,004** |
| elapsed | survey 921ms · llm 91138ms |

**선정 이유**: 인프라 신호가 전부 정상인 상태에서 content-service 쓰기 실패 트레이스가 08:51:47Z와 09:10:23Z에 있고 그 사이 08:55:00Z에 content+auth ERROR/WARN이 유일하게 동시 집중되므로, 두 실패와 로그 버스트를 모두 포함하도록 08:49Z부터 조회창 끝까지를 content-service와 auth-service로 좁혔다.

**근거**

- Tempo: traceID 6a69c37f7a3eebf11d620864776781be, root=content-service `http post /feeds`, 2026-07-29T09:10:23.873Z 시작, 76ms, serviceStats content-service spanCount=23 errorCount=1 (에러 스팬 be307298167fb5f8, 09:10:23.923Z, 4.33ms) — 조회 시간창 내 유일한 피드 작성 실패 트레이스이자 제보 증상과 정확히 일치하는 엔드포인트
- Tempo: traceID 6a69bf23e45c1e51c3475f5e5f3a1b04, `http post /feeds/{feedId}/comments`, 08:51:47.463Z, 119ms, errorCount=1 (에러 스팬 46.18ms) — 같은 content-service 쓰기 경로의 선행 실패
- Loki: 1시간 전체에서 ERROR/WARN 시계열이 08:55:00Z(ts=1785315300) 단 하나의 버킷에만 값이 존재하고 나머지 구간은 데이터포인트 자체가 없음 — 오류가 이 구간에 집중되었다는 신호
- Loki: 그 단일 버킷에서 content-service 4건과 auth-service 4건이 동시 발생 — content 쓰기의 인증/토큰 검증 의존 경로 의심
- Mimir: up 전 대상 13/13 포인트 모두 1 (content-service 파드 10.42.1.35:8090, 10.42.3.41:8090 포함), 결측·재시작 흔적 없음 → 프로세스 다운이나 스크랩 단절 아님
- Mimir: mongodb_up=1, kafka_brokers=1, 모든 consumergroup lag가 전 구간 0 유지(일부 파티션 -1은 1시간 내내 동일한 미할당 상태로 변화 없음) → DB·브로커·소비 지연 원인 배제
- Mimir: websocket_active_users=0이 전 구간 상수 — 변화가 없으므로 이번 증상과 무관하며 chat-service는 조사 대상에서 제외

**스윕이 찾은 트레이스** (고른 것은 6a69c37f7a3eebf11d620864776781be)

| traceId | root service | root span | ms |
|---|---|---|---:|
| `6a69c37f7a3eebf11d620864776781be` ←선택 | content-service | http post /feeds | 76 |
| `6a69bf23e45c1e51c3475f5e5f3a1b04` | content-service | http post /feeds/{feedId}/comments | 119 |

## 수집 범위 (Coverage)

- **window**: 2026-07-29T08:49:00Z ~ 2026-07-29T09:13:16Z (1456s)
- **trace**: 18,908B / 23 spans
- **logs**: errwarn=8,388B · traceId=5,715B
- **metrics**: 8 수집 / 164,988B, 누락 [sum(rate(http_server_requests_seconds_count{application="content-service", status="401"}[1m]))]
- **context**: 198,644 chars (+ 시스템 프롬프트 597 chars)

### 토큰 축 (개선 지표)

| 단계 | 총 in | chars | 컨텍스트 토큰 |
|---|---:|---:|---:|
| 탐색 | 48,311 | 40,004 | 측정 안 됨 |
| 분석 | 138,903 | 199,241 | 측정 안 됨 |
| **합계** | **187,214** | | **측정 안 됨** |

- ⚠ **overheadTokens 측정 안 됨** — 컨텍스트 토큰이 비어 있다. 이 회차 수치를 쓰려면
  `docs/round-1-input-tokens.md`의 다른 날 상수로 **추정**해야 하고, 그건 `▓ 추정`이다
  (그 상수는 하루 만에 20% 움직인 적이 있다).
- contextTokens (count_tokens API): 측정 안 됨 — 구독 CLI 경로엔 API 키가 없다
- 비용은 **API 환산 추정치**다. 구독 계정으로 호출하므로 실제 청구액이 아니다.

## 수집 실패/누락

- Metric 'sum(rate(http_server_requests_seconds_count{application="content-service", status="401"}[1m]))' returned no series in this window; skipped.

---

## 1. 원인 후보 랭킹 (최대 3개)

1. **단일 요청 내 중복 해시태그를 제거(dedupe)하지 않아 `tb_feed_hashtags` 유니크 키(`uk_feed_hashtag`) 위반 → 트랜잭션 롤백 → HTTP 500**
2. **해시태그 링크 삽입 경로에 멱등 처리(존재 확인 / ON DUPLICATE KEY / 예외 매핑)가 없어, 제약 위반이 그대로 500으로 노출**
3. **(별건 가능성) 제보가 이 트레이스가 아니라 17:51:47의 댓글 작성 실패(`Data too long for column 'content'`)를 가리킬 가능성**

---

## 2. 후보별 근거

### 후보 1 — 요청 내 중복 해시태그로 인한 유니크 제약 위반

- **근거:**
  - 루트 span `http post /feeds` (spanId `HWIIZHdngb4=`, 18:10:23.873245 ~ .949277, **75.9ms**)에 `status=500`, `outcome=SERVER_ERROR`, `uri=/feeds`, `http.url=/api/feeds`.
  - 트랜잭션 내 span 순서가 중복 태그 시나리오와 정확히 일치한다.
    1. `select ... from tb_hashtags h1_0 where h1_0.name=?` → `result-set` **`jdbc.row-count=0`** (미존재)
    2. `insert into tb_hashtags (...)` → `generated-keys` **`175`**
    3. `insert into tb_feed_hashtags (created_at,feed_id,hashtag_id,updated_at)` → `jdbc.row-affected=1`, `generated-keys=220` (성공)
    4. `update tb_hashtags set updated_at=?,usage_count=? where id=?` → `row-affected=1`
    5. **같은 쿼리 재실행** `select ... from tb_hashtags h1_0 where h1_0.name=?` → `result-set` **`jdbc.row-count=1`** (방금 만든 175를 찾음)
    6. 동일한 `insert into tb_feed_hashtags ...` 재시도 → span `vjBymBZ/tfg=`, `status.code=STATUS_CODE_ERROR`, `error="Duplicate entry '154-175' for key 'tb_feed_hashtags.uk_feed_hashtag'"`
  - 충돌 키 `'154-175'`의 두 값이 모두 **같은 트랜잭션에서 생성**됨: `tb_feed` insert의 `generated-keys=154`, `tb_hashtags` insert의 `generated-keys=175`. 즉 "같은 피드 + 같은 해시태그" 링크를 **한 요청 안에서 두 번** 삽입 시도했다.
  - 로그 원문: `18:10:23.927 WARN ... o.h.e.jdbc.spi.SqlExceptionHelper - SQL Error: 1062, SQLState: 23000` → `18:10:23.928 ERROR ... Duplicate entry '154-175' for key 'tb_feed_hashtags.uk_feed_hashtag'` → `18:10:23.942 WARN ... GlobalExceptionHandler - handleAllException` → `18:10:23.947 ERROR ... RequestLoggingFilter - [HTTP] POST /api/feeds 500 - 74ms`
  - JDBC `connection` span 이벤트: `acquired`(.881127) → **`rollback`**(.938193). 피드(154), 첨부 2건(237/238), 해시태그(175), 링크(220)가 **전부 롤백**되어 사용자에게는 "작성 실패"로 보인다.
- **확신도: 높음**
- **반증 데이터:** 없음. (다만 쿼리 바인딩 파라미터가 수집되지 않아 "중복이 동일 문자열 입력 때문인지, 정규화 후 충돌(대소문자·공백·`#` 처리)인지"는 이 데이터만으로 구분 불가 — 후보 2 참조.)

### 후보 2 — 링크 삽입 경로의 멱등성/예외 처리 부재

- **근거:**
  - 해시태그 **엔티티**에는 "조회 후 없으면 생성" 로직이 있다(`select ... where name=?` → 0건 → `insert into tb_hashtags`, 두 번째는 1건 조회로 재사용). 그런데 **링크 테이블 `tb_feed_hashtags`에는 동일한 방어가 없어** 곧바로 insert하고 DB 유니크 키에서 터진다(`row-affected` 없이 `error` 속성만 존재하는 span `vjBymBZ/tfg=`, 4.33ms).
  - 예외가 도메인 예외로 변환되지 않고 `c.e.t.a.c.e.GlobalExceptionHandler - handleAllException`(catch-all)로 흘러 **500**으로 응답됨. 입력 문제(중복 태그)가 서버 오류로 표시되는 상태다.
  - 동일 패턴이 다른 엔드포인트에도 존재: 17:51:47 `Data truncation: Data too long for column 'content' at row 1`(`SQL Error: 1406, SQLState: 22001`) 역시 `handleAllException` → `POST /api/feeds/145/comments 500 - 114ms`. 즉 **DB 제약 위반이 애플리케이션 검증 없이 500으로 새는 구조적 문제**.
- **확신도: 중간** (근거는 관측값으로 확실하나, 실제 코드 확인 전까지 "검증 누락"인지 "정규화 로직 버그"인지는 추정)
- **반증 데이터:** 없음.

### 후보 3 — 제보가 댓글 작성 실패(1406)를 가리킬 가능성

- **근거:** 조회 창(17:49~18:13 KST) 안의 content-service 500은 두 건뿐이며, 다른 한 건이 `17:51:47.577 ... [HTTP] POST /api/feeds/145/comments 500 - 114ms` (원인: `Data too long for column 'content' at row 1`, `SQL Error: 1406, SQLState: 22001`, traceId `6a69bf23e45c1e51c3475f5e5f3a1b04`)다. 사용자가 "피드에서 실패했다"고 표현했을 여지가 있다.
- **확신도: 낮음**
- **반증 데이터:** URI가 `/api/feeds/145/comments`로 **피드 작성이 아니라 댓글 작성**이며, 조사 대상 traceId `6a69c37f7a3eebf11d620864776781be`는 `POST /api/feeds`로 제보와 정확히 일치한다.

### 공통 배제 근거 (인프라/의존성 요인)

- **DB 커넥션 풀 고갈 아님:** `hikaricp_connections_pending` 전 시계열 **0**, 장애 pod `content-service-6c5fff897-scw7k`(10.42.3.41)의 `hikaricp_connections_active`는 창 전체 **0**. 커넥션도 `acquired`가 insert 시작 1.7ms 전에 완료.
- **GC/자원 압박 아님:** 해당 pod `rate(jvm_gc_pause_seconds_sum[5m])` 최대 **0.0000333초/초** 수준. 전체 요청 74ms, DB 구간 68ms로 지연 요소 없음.
- **Kafka / chat 경로 무관:** `kafka_brokers=1`, 모든 컨슈머그룹 lag **0**(`-1`은 미할당 파티션), 트레이스에 producer span 자체가 없다 → 이벤트 발행 이전 단계에서 롤백됨.
- **인증(auth) 문제 아님:** `security filterchain before/after` 12개 필터가 모두 정상 통과(`JwtAuthenticationFilter` 포함), 내부 로그 `userId=1`로 인증된 사용자. 마지막 `RequestLoggingFilter` 라인의 `userId=NONE`은 응답 시점 MDC 정리로 보이며 401 근거가 아니다.
- **가용성 정상:** `up`=1 (모든 서비스/노드/kafka/mongodb/redis), `mongodb_up`=1.
- **⚠️ 수집 공백:** `sum(rate(http_server_requests_seconds_count{application="content-service", status="401"}[1m]))`가 **no series**로 스킵됨. 따라서 "401이 없었다"가 아니라 **확인 불가**다. 또한 500 카운터 계열 메트릭도 확보하지 못해 **영향 범위(몇 명/몇 건이 실패했는지)는 데이터 부족**이다. 본 분석은 **단일 트레이스 1건 + 로그 2건**에 근거하므로, "모든 피드 작성 실패의 원인"으로 일반화하는 데는 확신도를 낮춰야 한다.

---

## 3. 권장 다음 조치

**즉시 확인 (읽기 전용)**

1. 영향 범위 산정 — Loki: `{job="default/content-service"} |= "POST /api/feeds" |= " 500 "` 를 최근 24h로 카운트, 그리고 `|= "uk_feed_hashtag"` 발생 건수/사용자(userId) 분포 확인.
2. 메트릭 공백 해소 — `http_server_requests_seconds_count{job="content-service"}` 로 라벨을 바꿔 시리즈 존재 여부 확인(`application` 라벨 미부착 가능성). 존재하면 `uri="/feeds", status="500"` 비율로 실패율 산정.
3. DB 상태 확인 — `SHOW CREATE TABLE tb_feed_hashtags\G` 로 `uk_feed_hashtag`(feed_id, hashtag_id) 정의 확인, `SELECT * FROM tb_feed WHERE feed_id=154;` / `SELECT * FROM tb_hashtags WHERE id=175;` 로 **롤백 정상 여부** 확인(정상이면 0건, 잔존 시 별도 정합성 이슈).
4. 코드 확인 — 피드 생성 서비스의 해시태그 처리 경로에서 ① 태그 파싱 후 `distinct`/정규화(trim, `#` 제거, 대소문자 통일) 적용 여부, ② `tb_feed_hashtags` 삽입 전 중복 검사 여부.

**재현**

5. 동일 해시태그를 두 번 포함한 payload(예: `#맛집 #맛집`)와, 정규화 시 충돌하는 payload(예: `#맛집 #맛집 ` / `#Cafe #cafe`)로 `POST /api/feeds` 호출 → 어느 쪽에서 1062가 재현되는지로 후보 1의 세부 원인(입력 그대로 중복 vs 정규화 충돌) 확정.

**조치**

6. 단기: 서비스 레이어에서 해시태그 정규화 후 `LinkedHashSet` 등으로 dedupe. 링크 삽입은 `INSERT ... ON DUPLICATE KEY UPDATE` 또는 삽입 전 존재 검사로 멱등화. `usage_count` 증가도 dedupe 이후 1회만 수행되도록 함께 점검(현재 중복 태그면 카운트 중복 증가 위험).
7. 예외 매핑: `DataIntegrityViolationException`을 `handleAllException`(500)에서 분리해 400/409로 응답 → 입력 문제가 서버 오류로 계측되지 않게 함.
8. 별건 후속: 댓글 `content` 컬럼 길이 vs API 입력 검증(`@Size`) 불일치 수정 — 1406(22001)도 400으로 처리.
9. 알람: `tb_feed_hashtags.uk_feed_hashtag` / `SQLState: 23000` 로그 패턴과 `POST /api/feeds 5xx` 비율에 대한 알람 추가(현재 제보 기반으로만 인지됨).

---

## 관측 증거 (Evidence)

> 이 조사가 실제로 본 값이다. 원본 응답 전체는 `reports/raw/6a69c37f7a3eebf11d620864776781be-*.json`에 있다.

### span (duration 상위 15 / 전체 23)

| ms | service | span | 시작 |
|---:|---|---|---|
| 76.03 | content-service | `http post /feeds` | 2026-07-29T09:10:23.873245Z |
| 73.88 | content-service | `secured request` | 2026-07-29T09:10:23.873731Z |
| 68.03 | content-service | `connection` | 2026-07-29T09:10:23.879375Z |
| 4.33 | content-service | `query` | 2026-07-29T09:10:23.923270Z |
| 3.09 | content-service | `query` | 2026-07-29T09:10:23.913992Z |
| 2.70 | content-service | `query` | 2026-07-29T09:10:23.889310Z |
| 1.81 | content-service | `query` | 2026-07-29T09:10:23.883528Z |
| 1.80 | content-service | `query` | 2026-07-29T09:10:23.903890Z |
| 1.79 | content-service | `query` | 2026-07-29T09:10:23.908422Z |
| 1.79 | content-service | `query` | 2026-07-29T09:10:23.917764Z |
| 1.77 | content-service | `query` | 2026-07-29T09:10:23.893442Z |
| 1.69 | content-service | `query` | 2026-07-29T09:10:23.899301Z |
| 1.63 | content-service | `query` | 2026-07-29T09:10:23.896126Z |
| 0.25 | content-service | `result-set` | 2026-07-29T09:10:23.885567Z |
| 0.22 | content-service | `generated-keys` | 2026-07-29T09:10:23.892188Z |

### 로그 원문 (16 / 전체 16줄)

```
2026-07-29T08:51:47.533044970Z  [content-service]  2026-07-29 17:51:47.532 [http-nio-8082-exec-4]  WARN [traceId=6a69bf23e45c1e51c3475f5e5f3a1b04,spanId=502823a48367b328,userId=1] o.h.e.jdbc.spi.SqlExceptionHelper - SQL Error: 1406, SQLState: 22001
2026-07-29T08:51:47.533241986Z  [content-service]  2026-07-29 17:51:47.533 [http-nio-8082-exec-4] ERROR [traceId=6a69bf23e45c1e51c3475f5e5f3a1b04,spanId=502823a48367b328,userId=1] o.h.e.jdbc.spi.SqlExceptionHelper - Data truncation: Data too long for column 'content' at row 1
2026-07-29T08:51:47.574871281Z  [content-service]  2026-07-29 17:51:47.560 [http-nio-8082-exec-4]  WARN [traceId=6a69bf23e45c1e51c3475f5e5f3a1b04,spanId=502823a48367b328,userId=1] c.e.t.a.c.e.GlobalExceptionHandler - handleAllException
2026-07-29T08:51:47.577916733Z  [content-service]  2026-07-29 17:51:47.577 [http-nio-8082-exec-4] ERROR [traceId=6a69bf23e45c1e51c3475f5e5f3a1b04,spanId=c3475f5e5f3a1b04,userId=NONE] c.e.t.a.c.f.RequestLoggingFilter - [HTTP] POST /api/feeds/145/comments 500 - 114ms
2026-07-29T08:53:09.504294986Z  [auth-service]  [2m2026-07-29 17:53:09[0;39m [2m[http-nio-8081-exec-10][0;39m [33m WARN [traceId=6a69bf75104087178f847ca2bc07c3ea,spanId=f766a498be4bc53d,userId=1][0;39m [36morg.hibernate.orm.query[0;39m [2m-[0;39m HHH90003004: firstResult/maxResults specified with collection fetch; applying in memory
2026-07-29T08:53:20.477320690Z  [auth-service]  [2m2026-07-29 17:53:20[0;39m [2m[http-nio-8081-exec-3][0;39m [33m WARN [traceId=6a69bf80068eadb430b349f4a5a18a81,spanId=53153fe31738ac10,userId=1][0;39m [36mc.e.t.a.c.e.GlobalExceptionHandler[0;39m [2m-[0;39m handleAllException
2026-07-29T08:53:20.674791136Z  [auth-service]  [2m2026-07-29 17:53:20[0;39m [2m[http-nio-8081-exec-5][0;39m [33m WARN [traceId=6a69bf80a5d164255cf31a17d14bce8a,spanId=45fd31525a93c32f,userId=1][0;39m [36mc.e.t.a.c.e.GlobalExceptionHandler[0;39m [2m-[0;39m handleAllException
2026-07-29T08:54:21.080088851Z  [auth-service]  [2m2026-07-29 17:54:21[0;39m [2m[http-nio-8081-exec-7][0;39m [33m WARN [traceId=6a69bfbd9a6193735d02de23abcfe8f7,spanId=fc00f8faed47b0da,userId=1][0;39m [36morg.hibernate.orm.query[0;39m [2m-[0;39m HHH90003004: firstResult/maxResults specified with collection fetch; applying in memory
2026-07-29T09:10:23.927948681Z  [content-service]  2026-07-29 18:10:23.927 [http-nio-8082-exec-5]  WARN [traceId=6a69c37f7a3eebf11d620864776781be,spanId=18cc78bf2e409cab,userId=1] o.h.e.jdbc.spi.SqlExceptionHelper - SQL Error: 1062, SQLState: 23000
2026-07-29T09:10:23.927948681Z  [content-service]  2026-07-29 18:10:23.927 [http-nio-8082-exec-5]  WARN [traceId=6a69c37f7a3eebf11d620864776781be,spanId=18cc78bf2e409cab,userId=1] o.h.e.jdbc.spi.SqlExceptionHelper - SQL Error: 1062, SQLState: 23000
2026-07-29T09:10:23.928081380Z  [content-service]  2026-07-29 18:10:23.928 [http-nio-8082-exec-5] ERROR [traceId=6a69c37f7a3eebf11d620864776781be,spanId=18cc78bf2e409cab,userId=1] o.h.e.jdbc.spi.SqlExceptionHelper - Duplicate entry '154-175' for key 'tb_feed_hashtags.uk_feed_hashtag'
2026-07-29T09:10:23.928081380Z  [content-service]  2026-07-29 18:10:23.928 [http-nio-8082-exec-5] ERROR [traceId=6a69c37f7a3eebf11d620864776781be,spanId=18cc78bf2e409cab,userId=1] o.h.e.jdbc.spi.SqlExceptionHelper - Duplicate entry '154-175' for key 'tb_feed_hashtags.uk_feed_hashtag'
2026-07-29T09:10:23.946549837Z  [content-service]  2026-07-29 18:10:23.942 [http-nio-8082-exec-5]  WARN [traceId=6a69c37f7a3eebf11d620864776781be,spanId=18cc78bf2e409cab,userId=1] c.e.t.a.c.e.GlobalExceptionHandler - handleAllException
2026-07-29T09:10:23.946549837Z  [content-service]  2026-07-29 18:10:23.942 [http-nio-8082-exec-5]  WARN [traceId=6a69c37f7a3eebf11d620864776781be,spanId=18cc78bf2e409cab,userId=1] c.e.t.a.c.e.GlobalExceptionHandler - handleAllException
2026-07-29T09:10:23.947917115Z  [content-service]  2026-07-29 18:10:23.947 [http-nio-8082-exec-5] ERROR [traceId=6a69c37f7a3eebf11d620864776781be,spanId=1d620864776781be,userId=NONE] c.e.t.a.c.f.RequestLoggingFilter - [HTTP] POST /api/feeds 500 - 74ms
2026-07-29T09:10:23.947917115Z  [content-service]  2026-07-29 18:10:23.947 [http-nio-8082-exec-5] ERROR [traceId=6a69c37f7a3eebf11d620864776781be,spanId=1d620864776781be,userId=NONE] c.e.t.a.c.f.RequestLoggingFilter - [HTTP] POST /api/feeds 500 - 74ms
```

### 메트릭 시계열

| 쿼리 | series | 점 | min | max | last | 값이 0이던 구간 |
|---|---|---:|---:|---:|---:|---|
| `hikaricp_connections_active` | `{__name__=hikaricp_connections_active, application=auth-service, cluster=yogurtte-k3s-prod, container=auth-service, instance=10.42.1.38:8090, job=auth-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=auth-service-855c75679d-pr892, pool=HikariPool-1, service=auth-service}` | 98 | 0 | 0 | 0 | **2026-07-29T08:49:00Z ~ 2026-07-29T09:13:15Z** |
| `hikaricp_connections_active` | `{__name__=hikaricp_connections_active, application=chat-service, cluster=yogurtte-k3s-prod, container=chat-service, instance=10.42.1.39:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-857c54dd97-w7bf7, pool=HikariPool-1}` | 98 | 0 | 0 | 0 | **2026-07-29T08:49:00Z ~ 2026-07-29T09:13:15Z** |
| `hikaricp_connections_active` | `{__name__=hikaricp_connections_active, application=content-service, cluster=yogurtte-k3s-prod, container=content-service, instance=10.42.1.35:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-6c5fff897-qnxk6, pool=HikariPool-1}` | 98 | 0 | 1 | 0 | **2026-07-29T08:49:00Z ~ 2026-07-29T08:50:00Z, 2026-07-29T08:51:15Z ~ 2026-07-29T09:00:00Z, 2026-07-29T09:02:15Z ~ 2026-07-29T09:06:00Z, 2026-07-29T09:07:15Z ~ 2026-07-29T09:10:00Z, 2026-07-29T09:11:15Z ~ 2026-07-29T09:13:15Z** |
| `hikaricp_connections_active` | `{__name__=hikaricp_connections_active, application=content-service, cluster=yogurtte-k3s-prod, container=content-service, instance=10.42.3.41:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-6c5fff897-scw7k, pool=HikariPool-1}` | 98 | 0 | 0 | 0 | **2026-07-29T08:49:00Z ~ 2026-07-29T09:13:15Z** |
| `hikaricp_connections_pending` | `{__name__=hikaricp_connections_pending, application=auth-service, cluster=yogurtte-k3s-prod, container=auth-service, instance=10.42.1.38:8090, job=auth-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=auth-service-855c75679d-pr892, pool=HikariPool-1, service=auth-service}` | 98 | 0 | 0 | 0 | **2026-07-29T08:49:00Z ~ 2026-07-29T09:13:15Z** |
| `hikaricp_connections_pending` | `{__name__=hikaricp_connections_pending, application=chat-service, cluster=yogurtte-k3s-prod, container=chat-service, instance=10.42.1.39:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-857c54dd97-w7bf7, pool=HikariPool-1}` | 98 | 0 | 0 | 0 | **2026-07-29T08:49:00Z ~ 2026-07-29T09:13:15Z** |
| `hikaricp_connections_pending` | `{__name__=hikaricp_connections_pending, application=content-service, cluster=yogurtte-k3s-prod, container=content-service, instance=10.42.1.35:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-6c5fff897-qnxk6, pool=HikariPool-1}` | 98 | 0 | 0 | 0 | **2026-07-29T08:49:00Z ~ 2026-07-29T09:13:15Z** |
| `hikaricp_connections_pending` | `{__name__=hikaricp_connections_pending, application=content-service, cluster=yogurtte-k3s-prod, container=content-service, instance=10.42.3.41:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-6c5fff897-scw7k, pool=HikariPool-1}` | 98 | 0 | 0 | 0 | **2026-07-29T08:49:00Z ~ 2026-07-29T09:13:15Z** |
| `rate(jvm_gc_pause_seconds_sum[5m])` | `{action=end of major GC, application=chat-service, cause=Allocation Failure, cluster=yogurtte-k3s-prod, container=chat-service, gc=MarkSweepCompact, instance=10.42.1.39:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-857c54dd97-w7bf7}` | 98 | 0 | 0 | 0 | **2026-07-29T08:49:00Z ~ 2026-07-29T09:13:15Z** |
| `rate(jvm_gc_pause_seconds_sum[5m])` | `{action=end of minor GC, application=auth-service, cause=G1 Evacuation Pause, cluster=yogurtte-k3s-prod, container=auth-service, gc=G1 Young Generation, instance=10.42.1.38:8090, job=auth-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=auth-service-855c75679d-pr892, service=auth-service}` | 98 | 0 | 0.000 | 0 | **2026-07-29T08:49:00Z ~ 2026-07-29T08:53:15Z, 2026-07-29T08:57:30Z ~ 2026-07-29T09:13:15Z** |
| `rate(jvm_gc_pause_seconds_sum[5m])` | `{action=end of minor GC, application=chat-service, cause=Allocation Failure, cluster=yogurtte-k3s-prod, container=chat-service, gc=Copy, instance=10.42.1.39:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-857c54dd97-w7bf7}` | 98 | 0.000 | 0.000 | 0.000 | — |
| `rate(jvm_gc_pause_seconds_sum[5m])` | `{action=end of minor GC, application=content-service, cause=Allocation Failure, cluster=yogurtte-k3s-prod, container=content-service, gc=Copy, instance=10.42.1.35:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-6c5fff897-qnxk6}` | 98 | 0 | 0.000 | 0.000 | **2026-07-29T08:50:15Z ~ 2026-07-29T08:54:00Z, 2026-07-29T08:58:15Z ~ 2026-07-29T09:04:00Z, 2026-07-29T09:08:15Z ~ 2026-07-29T09:13:00Z** |
| `rate(jvm_gc_pause_seconds_sum[5m])` | `{action=end of minor GC, application=content-service, cause=Allocation Failure, cluster=yogurtte-k3s-prod, container=content-service, gc=Copy, instance=10.42.3.41:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-6c5fff897-scw7k}` | 98 | 0 | 0.000 | 0.000 | **2026-07-29T08:49:00Z ~ 2026-07-29T08:49:45Z, 2026-07-29T08:54:00Z ~ 2026-07-29T08:59:45Z, 2026-07-29T09:04:00Z ~ 2026-07-29T09:09:45Z** |
| `up` | `{__name__=up, app=node-exporter, cluster=yogurtte-k3s-prod, component=metrics, container=node-exporter, instance=ip-172-31-40-241, job=integrations/node_exporter, k8s_cluster_name=yogurtte-k3s-prod, namespace=monitoring, pod=grafana-k8s-monitoring-node-exporter-cph5l, source=kubernetes, workload=DaemonSet/grafana-k8s-monitoring-node-exporter}` | 98 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, app=node-exporter, cluster=yogurtte-k3s-prod, component=metrics, container=node-exporter, instance=ip-172-31-45-39, job=integrations/node_exporter, k8s_cluster_name=yogurtte-k3s-prod, namespace=monitoring, pod=grafana-k8s-monitoring-node-exporter-9kvqt, source=kubernetes, workload=DaemonSet/grafana-k8s-monitoring-node-exporter}` | 98 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, cluster=yogurtte-k3s-prod, container=auth-service, instance=10.42.1.38:8090, job=auth-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=auth-service-855c75679d-pr892}` | 98 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, cluster=yogurtte-k3s-prod, container=chat-service, instance=10.42.1.39:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-857c54dd97-w7bf7}` | 98 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, cluster=yogurtte-k3s-prod, container=content-service, instance=10.42.1.35:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-6c5fff897-qnxk6}` | 98 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, cluster=yogurtte-k3s-prod, container=content-service, instance=10.42.3.41:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-6c5fff897-scw7k}` | 98 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, cluster=yogurtte-k3s-prod, instance=10.42.1.248:8080, job=integrations/kubernetes/kube-state-metrics, k8s_cluster_name=yogurtte-k3s-prod, source=kubernetes}` | 98 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, cluster=yogurtte-k3s-prod, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod}` | 98 | 1 | 1 | 1 | — |
| `mongodb_up` | `{__name__=mongodb_up, cluster=yogurtte-k3s-prod, instance=infra-server, job=mongodb, k8s_cluster_name=yogurtte-k3s-prod}` | 98 | 1 | 1 | 1 | — |
| `kafka_brokers` | `{__name__=kafka_brokers, cluster=yogurtte-k3s-prod, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod}` | 98 | 1 | 1 | 1 | — |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=chat-service-fcm-tokens, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=0, topic=user.fcm-tokens}` | 98 | 0 | 0 | 0 | **2026-07-29T08:49:00Z ~ 2026-07-29T09:13:15Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=chat-service-fcm-tokens, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=1, topic=user.fcm-tokens}` | 98 | 0 | 0 | 0 | **2026-07-29T08:49:00Z ~ 2026-07-29T09:13:15Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=chat-service-fcm-tokens, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=2, topic=user.fcm-tokens}` | 98 | 0 | 0 | 0 | **2026-07-29T08:49:00Z ~ 2026-07-29T09:13:15Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=chat-service-notification-settings, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=0, topic=user.notification-settings}` | 98 | 0 | 0 | 0 | **2026-07-29T08:49:00Z ~ 2026-07-29T09:13:15Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=chat-service-notification-settings, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=1, topic=user.notification-settings}` | 98 | 0 | 0 | 0 | **2026-07-29T08:49:00Z ~ 2026-07-29T09:13:15Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=chat-service-notification-settings, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=2, topic=user.notification-settings}` | 98 | 0 | 0 | 0 | **2026-07-29T08:49:00Z ~ 2026-07-29T09:13:15Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=db-writer, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=0, topic=chat.messages}` | 98 | 0 | 0 | 0 | **2026-07-29T08:49:00Z ~ 2026-07-29T09:13:15Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=db-writer, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=1, topic=chat.messages}` | 98 | 0 | 0 | 0 | **2026-07-29T08:49:00Z ~ 2026-07-29T09:13:15Z** |
| `websocket_active_users` | `{__name__=websocket_active_users, application=chat-service, cluster=yogurtte-k3s-prod, container=chat-service, instance=10.42.1.39:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-857c54dd97-w7bf7}` | 98 | 0 | 0 | 0 | **2026-07-29T08:49:00Z ~ 2026-07-29T09:13:15Z** |

값이 0이던 구간은 굵게 표시했다 — 프로세스가 사라져 시계열이 꺾인 것이 유일한 신호인 장애가 있다.

