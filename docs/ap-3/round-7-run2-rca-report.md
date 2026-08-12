# RCA Report — `scan-1786498500`

| 항목 | 값 |
|---|---|
| 모드 | rca |
| 질문 | 최근 1시간 안에 피드 작성이 실패했다는 제보가 있다. 원인을 조사해줘 |
| 시각 | 2026-08-12T05:13:16.236232700Z |
| provider | claude-cli |
| model | `claude-opus-5` · turns 1 |
| prompt | `.\prompts\system-prompt.md` |
| tokens | in 70031 (cacheRead 23,796 · cacheCreate 46,233) / out 9831 · cost $0.7200 |
| elapsed | total 169440ms (tempo 523 · loki 363 · mimir 560 · assemble 162 · llm 157033) |

## 탐색 (Triage)

| 항목 | 값 |
|---|---|
| 시간창 해석 | 명시적 from/to |
| 시간창 확신도 | EXACT |
| 스윕 창 | 2026-08-12T00:43:34Z ~ 2026-08-12T01:43:34Z |
| 좁힌 창 | 2026-08-12T01:35:00Z ~ 2026-08-12T01:40:00Z |
| 대상 | content-service |
| traceId | 6a7bcdccfb0b88f17f425d5e2fb9908f |
| 트레이스 후보 | 3건 |
| 장애 후보 | 7건 · 선택 INC-6, INC-7 |
| 계획 파싱 | 성공 |
| 스윕 컨텍스트 | **후보만 — 원본 제외 (B)** |
| prompt | `.\prompts\triage-prompt.md` |
| tokens | in 33622 / out 2794 · cost $0.2072 |
| chars | 컨텍스트 3,208 + 프롬프트 1,399 = **4,607** |
| elapsed | survey 2508ms · llm 45723ms |

**선정 이유**: POST /feeds 에러 트레이스(INC-7)와 같은 시각·같은 서비스의 DB 제약 위반 로그(INC-6)가 제보된 증상·엔드포인트·시각에 모두 맞아떨어지는 유일한 조합이다.

**근거**

- INC-7: content-service http post /feeds 86ms error 채널 트레이스 (2026-08-12T01:35:08.522901Z, traceId 6a7bcdccfb0b88f17f425d5e2fb9908f) — 제보된 '피드 작성' 엔드포인트와 정확히 동일
- INC-6: 같은 분(01:35:00~01:36:00) content-service ERROR/WARN 4건, 원인 예외 java.sql.SQLIntegrityConstraintViolationException 및 org.hibernate.exception.ConstraintViolationException 각 1건
- INC-6 반복성: 두 예외 모두 01:35:00~01:40:00 구간에서 x5회 · 평균 60초 간격 — 단발 사고가 아니라 5분간 지속된 DB 제약 위반
- 인프라 채널은 전부 무신호: up / mongodb_up / kafka_brokers / kafka_consumergroup_lag / websocket_active_users 이상 0건 → 노드·MongoDB·Kafka 레이어가 아니라 content-service의 RDB 쓰기 경로로 범위가 좁혀짐
- Tempo 에러 검색 총 2건 중 나머지 1건(INC-3)은 /feeds/{feedId}/comments로 엔드포인트가 다름 — 피드 작성 실패에 해당하는 트레이스는 INC-7 하나뿐

**스윕이 찾은 트레이스** (고른 것은 6a7bcdccfb0b88f17f425d5e2fb9908f)

| traceId | 채널 | root service | root span | ms |
|---|---|---|---|---:|
| `6a7bcdccfb0b88f17f425d5e2fb9908f` ←선택 | error | content-service | http post /feeds | 86 |
| `6a7bcad606c9ad16a434d244e431e33d` | error | content-service | http post /feeds/{feedId}/comments | 208 |
| `6a7bcdbd052a495e0b6b5c4c9b4c3a61` | slow | auth-service | http post /files/upload | 3063 |

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
- 구간: 2026-08-12T01:31:00Z ~ 2026-08-12T01:37:00Z  (LOKI · 집계 해상도만큼 흐림)
- ERROR/WARN 2건 (2026-08-12T01:31:00Z ~ 2026-08-12T01:32:00Z)  [x4회 · 2026-08-12T01:31:00Z~2026-08-12T01:35:00Z · 평균 60초 간격]
- 예외 java.lang.NullPointerException 2건 (2026-08-12T01:32:00Z ~ 2026-08-12T01:33:00Z)  [x5회 · 2026-08-12T01:32:00Z~2026-08-12T01:37:00Z · 평균 60초 간격]
- 같은 시각의 다른 후보: INC-5, INC-6, INC-7  (인과 여부는 판단하지 않았다)

## INC-5  auth-service  |  http post /files/upload
- 구간: 2026-08-12T01:34:53.777306Z ~ 2026-08-12T01:34:56.840306Z  (TEMPO · 시각 정확)
- auth-service http post /files/upload 3,063ms (slow 채널)
- traceId: 6a7bcdbd052a495e0b6b5c4c9b4c3a61
- 같은 시각의 다른 후보: INC-4  (인과 여부는 판단하지 않았다)

## INC-6  content-service  |  ERROR/WARN
- 구간: 2026-08-12T01:35:00Z ~ 2026-08-12T01:40:00Z  (LOKI · 집계 해상도만큼 흐림)
- ERROR/WARN 4건 (2026-08-12T01:35:00Z ~ 2026-08-12T01:36:00Z)
- 원인 예외 java.sql.SQLIntegrityConstraintViolationException 1건 (2026-08-12T01:35:00Z ~ 2026-08-12T01:36:00Z)  [x5회 · 2026-08-12T01:35:00Z~2026-08-12T01:40:00Z · 평균 60초 간격]
- 원인 예외 org.hibernate.exception.ConstraintViolationException 1건 (2026-08-12T01:35:00Z ~ 2026-08-12T01:36:00Z)  [x5회 · 2026-08-12T01:35:00Z~2026-08-12T01:40:00Z · 평균 60초 간격]
- 같은 시각의 다른 후보: INC-4, INC-7  (인과 여부는 판단하지 않았다)

## INC-7  content-service  |  http post /feeds
- 구간: 2026-08-12T01:35:08.522901Z ~ 2026-08-12T01:35:08.608901Z  (TEMPO · 시각 정확)
- content-service http post /feeds 86ms (error 채널)
- traceId: 6a7bcdccfb0b88f17f425d5e2fb9908f
- 같은 시각의 다른 후보: INC-4, INC-6  (인과 여부는 판단하지 않았다)

**기각한 후보**

- INC-1 — chat-service ERROR/WARN 1건, 01:22 단발이며 피드 작성 경로와 서비스도 시각도 무관하다.
- INC-2 — content-service이긴 하나 01:22 대이고 원인 예외가 DataException으로, 같은 시각 유일한 트레이스인 INC-3(댓글 작성)의 로그로 보이며 피드 작성 실패와 지문이 다르다.
- INC-3 — 실패한 엔드포인트가 POST /feeds/{feedId}/comments — 댓글 작성이지 피드 작성이 아니다.
- INC-4 — auth-service NullPointerException으로 서비스와 예외 지문이 다르며, 피드 작성 실패가 인증 실패로 나타났다는 신호(401/인증 관련 트레이스)가 없다.
- INC-5 — auth-service /files/upload 3,063ms는 slow 채널(실패가 아닌 지연)이라 '실패' 제보와 맞지 않는다. 다만 INC-7보다 15초 앞선 인접 시각이라, 첨부 업로드 지연 후 재시도가 INC-6의 제약 위반(중복 삽입)을 유발했을 가능성은 남아 있다 — INC-6/7 조사에서 중복 키가 확인되면 가장 먼저 되짚을 후보.
- INC-7 — 선택함

**보정 기록**

- 창을 후보 [INC-6, INC-7] 의 신호 시각에서 계산했다 (2026-08-12T01:35:00Z ~ 2026-08-12T01:40:00Z)

## 수집 범위 (Coverage)

- **window**: 2026-08-12T01:35:00Z ~ 2026-08-12T01:40:00Z (300s)
- **trace**: 44,973B / 56 spans
- **창 안 후보 트레이스**: 2건 / 44,973B
- **logs**: errwarn=39,958B · traceId=5,746B
- **metrics**: 8 수집 / 54,304B, 누락 [sum(rate(http_server_requests_seconds_count{application="content-service", status="401"}[1m]))]
- **context**: 71,336 chars (+ 시스템 프롬프트 805 chars)

### 토큰 축 (개선 지표)

| 단계 | 총 in | chars | 컨텍스트 토큰 |
|---|---:|---:|---:|
| 탐색 | 33,622 | 4,607 | 3,612 |
| 분석 | 70,031 | 72,141 | 40,021 |
| **합계** | **103,653** | | **43,633** |

- **overheadTokens 30,010 tok — 이 회차에 실측**(1자 프롬프트 프로브, 같은 명령·모델·샌드박스).
  호출마다 한 번씩 얹히므로 단계별로 뺀다. **다른 날 상수를 쓰지 않았다 = `█ 실측`.**
- contextTokens (count_tokens API): 측정 안 됨 — 구독 CLI 경로엔 API 키가 없다
- 비용은 **API 환산 추정치**다. 구독 계정으로 호출하므로 실제 청구액이 아니다.

## 수집 실패/누락

- Metric 'sum(rate(http_server_requests_seconds_count{application="content-service", status="401"}[1m]))' returned no series in this window; skipped.

---

조사 데이터만으로 분석했다. 툴 호출은 하지 않았다(추가 조회 없이 주어진 관측치로 판정 가능).

## 1. 원인 후보 랭킹 (최대 3개)

1. **한 요청 안에 같은 해시태그가 2번 들어와, 같은 `(feed_id, hashtag_id)` 조합을 두 번 INSERT → 유니크 제약 `uk_feed_hashtag` 위반(MySQL 1062)** — 애플리케이션의 해시태그 중복 제거 누락
2. **해시태그 링크 실패가 트랜잭션 전체 롤백 + HTTP 500으로 확대** — 예외 매핑/트랜잭션 경계 문제(피해 확대 요인, 근본 원인 아님)
3. **DB·커넥션풀·인프라 자원 문제** — 반증 다수, 사실상 배제

---

## 2. 후보별 근거

### 후보 1 — 요청 내 해시태그 중복 (근본 원인)

**근거 (실패 트레이스 `6a7bcdccfb0b88f17f425d5e2fb9908f`, 시각은 epoch ns 하위 자릿수):**

한 커넥션(`connection` span `t3gxHAeG6mU=`, 77.873ms) 안에서 순서가 이렇다.

| 시각(ns) | span / 값 | 의미 |
|---|---|---|
| 537609 | `insert into tb_feed …` row-affected 1 | 피드 생성 |
| 541377 | `generated-keys` = **166** | feed_id = 166 |
| 550104 | `select … from tb_hashtags h1_0 where h1_0.name=?` | 태그 1회차 조회 |
| 552036 | `result-set` **row-count 0** | 미존재 → 신규 생성 경로 |
| 556827 | `insert into tb_hashtags …` row-affected 1 | 태그 신규 삽입 |
| 560932 | `generated-keys` = **179** | hashtag_id = 179 |
| 564095 | `insert into tb_feed_hashtags …` **row-affected 1** | (166,179) 링크 **성공** |
| 570147 | `update tb_hashtags set usage_count …` | 1회차 마무리 |
| 573103 | `select … tb_hashtags where h1_0.name=?` | 태그 2회차 조회 |
| 575059 | `result-set` **row-count 1** | 방금 만든 179를 그대로 반환 |
| 579116 | `insert into tb_feed_hashtags …` | (166,179) **재삽입 → 실패** |

실패 span 속성 원문: `"error":"Duplicate entry '166-179' for key 'tb_feed_hashtags.uk_feed_hashtag'"`, `status.code = STATUS_CODE_ERROR`.
로그 원문: `SQL Error: 1062, SQLState: 23000` (10:35:08.586), 이어서 `org.springframework.dao.DataIntegrityViolationException: could not execute statement [Duplicate entry '166-179' for key 'tb_feed_hashtags.uk_feed_hashtag'] [insert into tb_feed_hashtags (created_at,feed_id,hashtag_id,updated_at) values (?,?,?,?)]; constraint [tb_feed_hashtags.uk_feed_hashtag]`.

키 값 `'166-179'`이 이 트레이스에서 방금 발급된 feed_id 166 · hashtag_id 179와 정확히 일치하므로, `uk_feed_hashtag`는 `(feed_id, hashtag_id)` 복합 유니크이고 **동일 요청 내부의 자기 충돌**이다. 재시도·중복 제출이 아니라는 근거: feed_id 166이 이 트레이스 안에서 새로 채번됐다(기존 피드에 덧붙인 게 아니다).

정상 트레이스(`6a7bce094ee8b7a55835af4577155a8b`, 200)와의 차이도 같은 결론을 가리킨다. 정상 요청은 `select tb_hashtags by name → insert tb_feed_hashtags`가 **1회**만 돌고 `commit`으로 끝난다. 즉 코드 경로 자체는 정상이며, 갈린 변수는 요청이 실어 보낸 태그 목록뿐이다.

예외가 터진 지점이 해시태그 코드가 아니라 `ExpGrantService.isDuplicate(ExpGrantService.java:225)` → `grantWithCap:159` → `grant:151` → `grantFeedCreate:43` → `FeedService.createFeed(FeedService.java:223)`인 것은 Hibernate 지연 flush 때문이다. 보류돼 있던 `tb_feed_hashtags` insert가 경험치 중복 체크의 조회 직전 auto-flush에서 실행돼 터졌다. 실패 트레이스에 `tb_exp_history` 조회 span이 **아예 없는 것**이 이를 뒷받침한다(정상 트레이스에는 `select eh1_0.exp_history_id … where user_id=? and source=? and source_id=? limit ?`가 있다). flush가 먼저 실패해 그 select는 실행되지 못했다.

- **확신도: 높음**
- **반증 데이터: 없음.** 단, 태그 파라미터 값은 트레이스에 남지 않아 "완전히 같은 문자열 2개"인지 "대소문자·공백 차이가 정규화 후 같은 이름이 된 것"인지는 구분 불가 — 수정 방식(정규화 후 중복 제거 vs 원문 중복 제거)에 영향이 있으므로 아래 조치에 반영했다.
- **대기·지연 구간 판정:** 이 요청에 타임아웃 만료성 대기는 없다. HikariCP 커넥션 획득 대기는 span 시작 528689 → `acquired` 528878 = **0.189ms**. `connectionTimeout` 설정값은 수집 데이터에 없으나(설정값 미확보), 전 구간 `hikaricp_connections_pending = 0`, `hikaricp_connections_active = 0`이고 커넥션 span이 타임아웃이 아니라 `rollback`(596343) 이벤트로 종료된 점에서 **만료 아님**으로 판정한다. 최종 상태: **실패 → 트랜잭션 전체 롤백(폐기)**. 재시도 흔적은 관측 창에 없다.

### 후보 2 — 링크 실패가 요청 전체 롤백·500으로 확대

**근거:** `connection` span 이벤트가 정상 트레이스의 `commit`과 달리 `rollback`(596343)이다. 즉 성공했던 `insert tb_feed`(feed_id 166), 첨부파일 2건(generated-keys 261, 262), `insert tb_hashtags`(179), 첫 링크(236), `update tb_hashtags.usage_count`가 **전부 되감겼다**. 루트 span `http post /feeds` 속성은 `"outcome":"SERVER_ERROR"`, `"status":"500"`, `"exception":"none"`이고, 로그는 `GlobalExceptionHandler - [api-error] handleAllException` → `[HTTP] POST /api/feeds 500 - 84ms`다. 입력 데이터 형태 문제(중복 태그)가 4xx가 아니라 **범용 핸들러의 500**으로 떨어졌다 — 클라이언트는 재시도해도 되는지 알 수 없고, 알림 기준으로는 서버 장애로 집계된다.

- **확신도: 중간** (관측된 사실은 확실하나, 이것이 "설계 의도"인지 "누락"인지는 코드 없이 단정 불가. 태그 링크 실패 시 피드까지 버리는 게 의도된 원자성일 수도 있다.)
- **반증 데이터: 없음** — 다만 롤백 자체는 데이터 일관성 측면에선 정상 동작이다. 문제는 500 매핑과 실패 범위이지 롤백 메커니즘이 아니다.
- **대기·지연 구간 판정:** 지연성 대기 없음. 실패 감지(586839) → 롤백(596343) → 응답(606) 총 약 20ms. 타임아웃 대조 대상 없음. 최종 상태: **폐기(롤백 확정)**.

### 후보 3 — DB / 커넥션풀 / 인프라 자원 문제

**근거:** 사실상 없음. 후보로만 올리고 배제한다.

- **확신도: 낮음**
- **반증 데이터(모두 배제 방향):**
  - `hikaricp_connections_pending` = 전 구간 0 (content 2 파드, auth, chat 모두). 풀 고갈 없음.
  - `up` = 전 구간 1 (content 2 파드, auth, chat, mysql 노드 포함 전 대상), `mongodb_up`=1, `kafka_brokers`=1.
  - `rate(jvm_gc_pause_seconds_sum[5m])` content-service 최대 2.5e-5 s/s — GC 정지 무시 가능, 01:36:45 이후 0.
  - `kafka_consumergroup_lag` 전 파티션 0(또는 -1=미할당). 비동기 적체 없음.
  - JDBC 48회 중 실패 1건, 최대 108.0ms. DB 응답 지연 없음(개별 쿼리 1.7~7.7ms).
  - 에러 로그는 이 traceId 1건에만 존재하고 반복 접힘(`xN회`)이 0벌 — 동일 지문 반복 실패가 아니다.

**데이터 부족 항목:** 제보는 "최근 1시간"인데 조회 창은 **5분(01:35~01:40Z)** 뿐이고, 트레이스는 실패 1건·성공 1건만 확보됐다. 따라서 "이 중복-태그 결함이 그 1시간 실패 전체를 설명하는가"는 **판정 불가**다. `content-service` 401 메트릭도 시리즈 없음으로 수집 실패했으나, 실패 트레이스에서 시큐리티 필터체인 12/12가 정상 통과했고 SQL 예외까지 도달했으므로 인증 계열 원인과는 무관하다. 추가 수집 대상은 §3에 적었다.

---

## 3. 권장 다음 조치

### 이미 발생한 피해: 복구 가능한가

**부분 복구 가능 — 단, 유실된 피드 본문은 서버 측 자동 복구 불가.**

- 잔여물 없음: 트랜잭션이 `rollback`으로 끝나 `tb_feed`(166), `tb_feed_attachment_file`(261, 262), `tb_hashtags`(179), `tb_feed_hashtags`(236)가 모두 되감겼다. 고아 행 정리 작업은 **해당 없음**. 확인만 하려면 `SELECT * FROM tb_feed WHERE id=166;`, `SELECT * FROM tb_hashtags WHERE id=179;`, `SELECT * FROM tb_feed_hashtags WHERE feed_id=166;` 가 모두 0행이어야 한다(AUTO_INCREMENT 번호 166/179는 소모됐고, 이는 무해).
- 복구 불가한 것: 사용자가 입력한 피드 본문·첨부다. 요청 페이로드를 어디에도 보존하지 않았으므로 **서버가 재생할 수 없고 사용자 재작성이 필요하다**. 대상은 실패 로그의 `userId=1` 1건(관측 창 내 확인된 범위).
- 주의: 수정 배포 전에 사용자가 같은 내용을 그대로 재게시하면 **동일하게 실패한다**. 중복 태그를 지워달라는 안내 없이 "다시 시도해달라"고만 하면 반복 실패한다.

### 재발 방지

1. **근본 수정(한 줄급):** `FeedService.createFeed`에서 해시태그 목록을 저장 루프에 넣기 전에 정규화(트림/케이스 통일 — 기존 정규화 규칙이 있다면 그것에 맞춤) 후 중복 제거한다. `LinkedHashSet`으로 감싸는 수준이면 충분하고, 입력 순서도 보존된다. 파라미터 값이 안 남아 "원문 동일"인지 "정규화 후 동일"인지 구분이 안 되므로, **정규화를 적용한 뒤 중복을 제거**하면 두 경우를 모두 덮는다.
2. **방어선(제약은 유지):** `uk_feed_hashtag`는 그대로 둔다 — 이번에 데이터 오염을 막아준 게 그 제약이다. 대신 링크 삽입을 멱등하게(존재하면 skip) 처리한다.
3. **응답 코드 교정:** `GlobalExceptionHandler`가 `DataIntegrityViolationException`을 `handleAllException`으로 흘려 500을 내고 있다. 입력 기인 제약 위반은 409/400으로 매핑해 클라이언트가 "재시도해도 소용없음"을 알 수 있게 한다. 이번 건은 그 매핑만 있었어도 500 알림이 아니라 사용자 오류로 끝났다.
4. **관측 보강:** `POST /feeds` 5xx 비율 알림, 그리고 `SQLState: 23000` / 에러코드 1062 로그에 대한 별도 카운터. 지금은 트레이스를 열어봐야만 원인이 보인다.

> 우선순위상 1번만으로 장애는 멈춘다. 2·3번은 같은 계열 재발(다른 경로의 중복 링크, 오분류된 500)을 막는 몫이다.

### 복구 확인

- 재현 검증: 동일 해시태그를 2회 포함한 피드 작성 요청이 **200**을 반환하고, 생성된 `tb_feed_hashtags` 행 수 = 중복 제거 후 고유 태그 수와 일치할 것.
- 트레이스 확인: 해당 traceId의 `connection` span 이벤트가 `rollback`이 아니라 **`commit`**일 것. 그리고 정상 트레이스와 마찬가지로 `select eh1_0.exp_history_id …`부터 `update tb_user_reward`까지 리워드 구간이 끝까지 실행될 것(실패 시엔 이 구간이 통째로 누락된다 — 이게 가장 확실한 판별 신호다).
- 로그 확인: 배포 후 창에서 `Duplicate entry … uk_feed_hashtag` 및 `SQL Error: 1062` 발생 0건.
- 미해결분 추적: 위 5분 창은 실패 1건만 담고 있다. **제보 범위인 1시간(01:00~02:00Z) 전체로 `POST /api/feeds` 5xx와 content-service ERROR 로그를 재조회**해, 같은 지문(1062/uk_feed_hashtag)으로 전부 설명되는지 확인해야 결론의 커버리지가 확정된다. 다른 지문의 실패가 섞여 있으면 이 리포트는 그 부분을 설명하지 못한다.

---

## 관측 증거 (Evidence)

> 이 조사가 실제로 본 값이다. 원본 응답 전체는 `reports/raw/scan-1786498500-*.json`에 있다.

### 호출 그래프 (트레이스에서 추출)

```
content-service --jdbc--> mysql/content (HikariPool-1)  48회  최대 108.0ms
    error: Duplicate entry '166-179' for key 'tb_feed_hashtags.uk_feed_hashtag'
    events: acquired, rollback, commit
```

### span (duration 상위 15 / 전체 56)

| ms | service | span | 시작 |
|---:|---|---|---|
| 116.86 | content-service | `http post /feeds` | 2026-08-12T01:36:09.253876Z |
| 114.31 | content-service | `secured request` | 2026-08-12T01:36:09.254175Z |
| 108.02 | content-service | `connection` | 2026-08-12T01:36:09.259531Z |
| 86.40 | content-service | `http post /feeds` | 2026-08-12T01:35:08.522901Z |
| 83.38 | content-service | `secured request` | 2026-08-12T01:35:08.523268Z |
| 77.87 | content-service | `connection` | 2026-08-12T01:35:08.528689Z |
| 7.72 | content-service | `query` | 2026-08-12T01:35:08.579116Z |
| 3.69 | content-service | `query` | 2026-08-12T01:35:08.556827Z |
| 3.45 | content-service | `query` | 2026-08-12T01:36:09.347248Z |
| 3.39 | content-service | `query` | 2026-08-12T01:35:08.537609Z |
| 2.59 | content-service | `query` | 2026-08-12T01:36:09.351167Z |
| 2.48 | content-service | `query` | 2026-08-12T01:36:09.269510Z |
| 2.37 | content-service | `query` | 2026-08-12T01:35:08.570147Z |
| 2.14 | content-service | `query` | 2026-08-12T01:36:09.332575Z |
| 1.94 | content-service | `query` | 2026-08-12T01:36:09.301042Z |

### 로그 원문 (60 / 전체 251줄)

전체가 상한을 넘어 ERROR/WARN 줄을 우선 발췌했다. 나머지는 원본 파일에 있다.

```
2026-08-12T01:35:08.587799840Z  [content-service]  2026-08-12 10:35:08.586 [http-nio-8082-exec-3]  WARN [traceId=6a7bcdccfb0b88f17f425d5e2fb9908f,spanId=b778311c0786ea65,userId=1] o.h.e.jdbc.spi.SqlExceptionHelper - SQL Error: 1062, SQLState: 23000
2026-08-12T01:35:08.587799840Z  [content-service]  2026-08-12 10:35:08.586 [http-nio-8082-exec-3]  WARN [traceId=6a7bcdccfb0b88f17f425d5e2fb9908f,spanId=b778311c0786ea65,userId=1] o.h.e.jdbc.spi.SqlExceptionHelper - SQL Error: 1062, SQLState: 23000
2026-08-12T01:35:08.587848820Z  [content-service]  2026-08-12 10:35:08.587 [http-nio-8082-exec-3] ERROR [traceId=6a7bcdccfb0b88f17f425d5e2fb9908f,spanId=b778311c0786ea65,userId=1] o.h.e.jdbc.spi.SqlExceptionHelper - Duplicate entry '166-179' for key 'tb_feed_hashtags.uk_feed_hashtag'
2026-08-12T01:35:08.587848820Z  [content-service]  2026-08-12 10:35:08.587 [http-nio-8082-exec-3] ERROR [traceId=6a7bcdccfb0b88f17f425d5e2fb9908f,spanId=b778311c0786ea65,userId=1] o.h.e.jdbc.spi.SqlExceptionHelper - Duplicate entry '166-179' for key 'tb_feed_hashtags.uk_feed_hashtag'
2026-08-12T01:35:08.603679191Z  [content-service]  2026-08-12 10:35:08.598 [http-nio-8082-exec-3]  WARN [traceId=6a7bcdccfb0b88f17f425d5e2fb9908f,spanId=b778311c0786ea65,userId=1] c.e.t.a.c.e.GlobalExceptionHandler - [api-error] handleAllException
2026-08-12T01:35:08.603679191Z  [content-service]  2026-08-12 10:35:08.598 [http-nio-8082-exec-3]  WARN [traceId=6a7bcdccfb0b88f17f425d5e2fb9908f,spanId=b778311c0786ea65,userId=1] c.e.t.a.c.e.GlobalExceptionHandler - [api-error] handleAllException
2026-08-12T01:35:08.603736202Z  [content-service]  org.springframework.dao.DataIntegrityViolationException: could not execute statement [Duplicate entry '166-179' for key 'tb_feed_hashtags.uk_feed_hashtag'] [insert into tb_feed_hashtags (created_at,feed_id,hashtag_id,updated_at) values (?,?,?,?)]; SQL [insert into tb_feed_hashtags (created_at,feed_id,hashtag_id,updated_at) values (?,?,?,?)]; constraint [tb_feed_hashtags.uk_feed_hashtag]
2026-08-12T01:35:08.603742170Z  [content-service]  at org.springframework.orm.jpa.vendor.HibernateJpaDialect.convertHibernateAccessException(HibernateJpaDialect.java:290)
2026-08-12T01:35:08.603746609Z  [content-service]  at org.springframework.orm.jpa.vendor.HibernateJpaDialect.translateExceptionIfPossible(HibernateJpaDialect.java:241)
2026-08-12T01:35:08.603750978Z  [content-service]  at org.springframework.orm.jpa.AbstractEntityManagerFactoryBean.translateExceptionIfPossible(AbstractEntityManagerFactoryBean.java:560)
2026-08-12T01:35:08.603770242Z  [content-service]  at org.springframework.dao.support.ChainedPersistenceExceptionTranslator.translateExceptionIfPossible(ChainedPersistenceExceptionTranslator.java:61)
2026-08-12T01:35:08.603774450Z  [content-service]  at org.springframework.dao.support.DataAccessUtils.translateIfNecessary(DataAccessUtils.java:343)
2026-08-12T01:35:08.603808172Z  [content-service]  at org.springframework.dao.support.PersistenceExceptionTranslationInterceptor.invoke(PersistenceExceptionTranslationInterceptor.java:160)
2026-08-12T01:35:08.603811628Z  [content-service]  at org.springframework.aop.framework.ReflectiveMethodInvocation.proceed(ReflectiveMethodInvocation.java:184)
2026-08-12T01:35:08.603816115Z  [content-service]  at org.springframework.data.jpa.repository.support.CrudMethodMetadataPostProcessor$CrudMethodMetadataPopulatingMethodInterceptor.invoke(CrudMethodMetadataPostProcessor.java:136)
2026-08-12T01:35:08.603819047Z  [content-service]  at org.springframework.aop.framework.ReflectiveMethodInvocation.proceed(ReflectiveMethodInvocation.java:184)
2026-08-12T01:35:08.603822321Z  [content-service]  at org.springframework.aop.framework.JdkDynamicAopProxy.invoke(JdkDynamicAopProxy.java:223)
2026-08-12T01:35:08.603828467Z  [content-service]  at com.example.toycontent.app.reward.exp.service.ExpGrantService.isDuplicate(ExpGrantService.java:225)
2026-08-12T01:35:08.603831582Z  [content-service]  at com.example.toycontent.app.reward.exp.service.ExpGrantService.grantWithCap(ExpGrantService.java:159)
2026-08-12T01:35:08.603834482Z  [content-service]  at com.example.toycontent.app.reward.exp.service.ExpGrantService.grant(ExpGrantService.java:151)
2026-08-12T01:35:08.603837379Z  [content-service]  at com.example.toycontent.app.reward.exp.service.ExpGrantService.grantFeedCreate(ExpGrantService.java:43)
2026-08-12T01:35:08.603852560Z  [content-service]  at org.springframework.aop.support.AopUtils.invokeJoinpointUsingReflection(AopUtils.java:359)
2026-08-12T01:35:08.603855527Z  [content-service]  at org.springframework.aop.framework.ReflectiveMethodInvocation.invokeJoinpoint(ReflectiveMethodInvocation.java:196)
2026-08-12T01:35:08.603858411Z  [content-service]  at org.springframework.aop.framework.ReflectiveMethodInvocation.proceed(ReflectiveMethodInvocation.java:163)
2026-08-12T01:35:08.603861358Z  [content-service]  at org.springframework.transaction.interceptor.TransactionAspectSupport.invokeWithinTransaction(TransactionAspectSupport.java:380)
2026-08-12T01:35:08.603864426Z  [content-service]  at org.springframework.transaction.interceptor.TransactionInterceptor.invoke(TransactionInterceptor.java:119)
2026-08-12T01:35:08.603867256Z  [content-service]  at org.springframework.aop.framework.ReflectiveMethodInvocation.proceed(ReflectiveMethodInvocation.java:184)
2026-08-12T01:35:08.603888061Z  [content-service]  at org.springframework.aop.framework.CglibAopProxy$DynamicAdvisedInterceptor.intercept(CglibAopProxy.java:727)
2026-08-12T01:35:08.603893892Z  [content-service]  at com.example.toycontent.app.feed.service.FeedService.createFeed(FeedService.java:223)
2026-08-12T01:35:08.603929722Z  [content-service]  at org.springframework.aop.support.AopUtils.invokeJoinpointUsingReflection(AopUtils.java:359)
2026-08-12T01:35:08.603977067Z  [content-service]  at org.springframework.aop.framework.ReflectiveMethodInvocation.invokeJoinpoint(ReflectiveMethodInvocation.java:196)
2026-08-12T01:35:08.603980426Z  [content-service]  at org.springframework.aop.framework.ReflectiveMethodInvocation.proceed(ReflectiveMethodInvocation.java:163)
2026-08-12T01:35:08.603983565Z  [content-service]  at org.springframework.transaction.interceptor.TransactionAspectSupport.invokeWithinTransaction(TransactionAspectSupport.java:380)
2026-08-12T01:35:08.603986946Z  [content-service]  at org.springframework.transaction.interceptor.TransactionInterceptor.invoke(TransactionInterceptor.java:119)
2026-08-12T01:35:08.603989979Z  [content-service]  at org.springframework.aop.framework.ReflectiveMethodInvocation.proceed(ReflectiveMethodInvocation.java:184)
2026-08-12T01:35:08.603993050Z  [content-service]  at org.springframework.aop.framework.CglibAopProxy$DynamicAdvisedInterceptor.intercept(CglibAopProxy.java:727)
2026-08-12T01:35:08.603998971Z  [content-service]  at com.example.toycontent.app.feed.controller.FeedController.createFeed(FeedController.java:114)
2026-08-12T01:35:08.604014558Z  [content-service]  at org.springframework.web.method.support.InvocableHandlerMethod.doInvoke(InvocableHandlerMethod.java:257)
2026-08-12T01:35:08.604017365Z  [content-service]  at org.springframework.web.method.support.InvocableHandlerMethod.invokeForRequest(InvocableHandlerMethod.java:190)
2026-08-12T01:35:08.604020160Z  [content-service]  at org.springframework.web.servlet.mvc.method.annotation.ServletInvocableHandlerMethod.invokeAndHandle(ServletInvocableHandlerMethod.java:118)
2026-08-12T01:35:08.604023112Z  [content-service]  at org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter.invokeHandlerMethod(RequestMappingHandlerAdapter.java:986)
2026-08-12T01:35:08.604026040Z  [content-service]  at org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter.handleInternal(RequestMappingHandlerAdapter.java:891)
2026-08-12T01:35:08.604028936Z  [content-service]  at org.springframework.web.servlet.mvc.method.AbstractHandlerMethodAdapter.handle(AbstractHandlerMethodAdapter.java:87)
2026-08-12T01:35:08.604032045Z  [content-service]  at org.springframework.web.servlet.DispatcherServlet.doDispatch(DispatcherServlet.java:1088)
2026-08-12T01:35:08.604056773Z  [content-service]  at org.springframework.web.servlet.DispatcherServlet.doService(DispatcherServlet.java:978)
2026-08-12T01:35:08.604060059Z  [content-service]  at org.springframework.web.servlet.FrameworkServlet.processRequest(FrameworkServlet.java:1014)
2026-08-12T01:35:08.604062946Z  [content-service]  at org.springframework.web.servlet.FrameworkServlet.doPost(FrameworkServlet.java:914)
2026-08-12T01:35:08.604066317Z  [content-service]  at jakarta.servlet.http.HttpServlet.service(HttpServlet.java:590)
2026-08-12T01:35:08.604160379Z  [content-service]  at org.springframework.security.web.access.ExceptionTranslationFilter.doFilter(ExceptionTranslationFilter.java:126)
2026-08-12T01:35:08.604162810Z  [content-service]  at org.springframework.security.web.access.ExceptionTranslationFilter.doFilter(ExceptionTranslationFilter.java:120)
2026-08-12T01:35:08.604786769Z  [content-service]  Caused by: org.hibernate.exception.ConstraintViolationException: could not execute statement [Duplicate entry '166-179' for key 'tb_feed_hashtags.uk_feed_hashtag'] [insert into tb_feed_hashtags (created_at,feed_id,hashtag_id,updated_at) values (?,?,?,?)]
2026-08-12T01:35:08.604789756Z  [content-service]  at org.hibernate.dialect.MySQLDialect.lambda$buildSQLExceptionConversionDelegate$3(MySQLDialect.java:1245)
2026-08-12T01:35:08.604792609Z  [content-service]  at org.hibernate.exception.internal.StandardSQLExceptionConverter.convert(StandardSQLExceptionConverter.java:58)
2026-08-12T01:35:08.604794721Z  [content-service]  at org.hibernate.engine.jdbc.spi.SqlExceptionHelper.convert(SqlExceptionHelper.java:108)
2026-08-12T01:35:08.604957264Z  [content-service]  at org.springframework.dao.support.PersistenceExceptionTranslationInterceptor.invoke(PersistenceExceptionTranslationInterceptor.java:138)
2026-08-12T01:35:08.604962596Z  [content-service]  Caused by: java.sql.SQLIntegrityConstraintViolationException: Duplicate entry '166-179' for key 'tb_feed_hashtags.uk_feed_hashtag'
2026-08-12T01:35:08.604965031Z  [content-service]  at com.mysql.cj.jdbc.exceptions.SQLError.createSQLException(SQLError.java:109)
2026-08-12T01:35:08.604967568Z  [content-service]  at com.mysql.cj.jdbc.exceptions.SQLExceptionsMapping.translateException(SQLExceptionsMapping.java:114)
2026-08-12T01:35:08.606957583Z  [content-service]  2026-08-12 10:35:08.606 [http-nio-8082-exec-3] ERROR [traceId=6a7bcdccfb0b88f17f425d5e2fb9908f,spanId=7f425d5e2fb9908f,userId=NONE] c.e.t.a.c.f.RequestLoggingFilter - [HTTP] POST /api/feeds 500 - 84ms
2026-08-12T01:35:08.606957583Z  [content-service]  2026-08-12 10:35:08.606 [http-nio-8082-exec-3] ERROR [traceId=6a7bcdccfb0b88f17f425d5e2fb9908f,spanId=7f425d5e2fb9908f,userId=NONE] c.e.t.a.c.f.RequestLoggingFilter - [HTTP] POST /api/feeds 500 - 84ms
```

### 메트릭 시계열

| 쿼리 | series | 점 | min | max | last | 값이 0이던 구간 |
|---|---|---:|---:|---:|---:|---|
| `hikaricp_connections_active` | `{__name__=hikaricp_connections_active, application=auth-service, cluster=yogurtte-k3s-prod, container=auth-service, instance=10.42.1.51:8090, job=auth-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=auth-service-5999bb9f5c-lzp4p, pool=HikariPool-1, service=auth-service}` | 21 | 0 | 0 | 0 | **2026-08-12T01:35:00Z ~ 2026-08-12T01:40:00Z** |
| `hikaricp_connections_active` | `{__name__=hikaricp_connections_active, application=chat-service, cluster=yogurtte-k3s-prod, container=chat-service, instance=10.42.1.55:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-fdcc7c776-kh2dl, pool=HikariPool-1}` | 21 | 0 | 0 | 0 | **2026-08-12T01:35:00Z ~ 2026-08-12T01:40:00Z** |
| `hikaricp_connections_active` | `{__name__=hikaricp_connections_active, application=content-service, cluster=yogurtte-k3s-prod, container=content-service, instance=10.42.1.53:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-85f648fcff-sp24n, pool=HikariPool-1}` | 21 | 0 | 0 | 0 | **2026-08-12T01:35:00Z ~ 2026-08-12T01:40:00Z** |
| `hikaricp_connections_active` | `{__name__=hikaricp_connections_active, application=content-service, cluster=yogurtte-k3s-prod, container=content-service, instance=10.42.3.46:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-85f648fcff-v2pw9, pool=HikariPool-1}` | 21 | 0 | 0 | 0 | **2026-08-12T01:35:00Z ~ 2026-08-12T01:40:00Z** |
| `hikaricp_connections_pending` | `{__name__=hikaricp_connections_pending, application=auth-service, cluster=yogurtte-k3s-prod, container=auth-service, instance=10.42.1.51:8090, job=auth-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=auth-service-5999bb9f5c-lzp4p, pool=HikariPool-1, service=auth-service}` | 21 | 0 | 0 | 0 | **2026-08-12T01:35:00Z ~ 2026-08-12T01:40:00Z** |
| `hikaricp_connections_pending` | `{__name__=hikaricp_connections_pending, application=chat-service, cluster=yogurtte-k3s-prod, container=chat-service, instance=10.42.1.55:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-fdcc7c776-kh2dl, pool=HikariPool-1}` | 21 | 0 | 0 | 0 | **2026-08-12T01:35:00Z ~ 2026-08-12T01:40:00Z** |
| `hikaricp_connections_pending` | `{__name__=hikaricp_connections_pending, application=content-service, cluster=yogurtte-k3s-prod, container=content-service, instance=10.42.1.53:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-85f648fcff-sp24n, pool=HikariPool-1}` | 21 | 0 | 0 | 0 | **2026-08-12T01:35:00Z ~ 2026-08-12T01:40:00Z** |
| `hikaricp_connections_pending` | `{__name__=hikaricp_connections_pending, application=content-service, cluster=yogurtte-k3s-prod, container=content-service, instance=10.42.3.46:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-85f648fcff-v2pw9, pool=HikariPool-1}` | 21 | 0 | 0 | 0 | **2026-08-12T01:35:00Z ~ 2026-08-12T01:40:00Z** |
| `rate(jvm_gc_pause_seconds_sum[5m])` | `{action=end of major GC, application=chat-service, cause=Allocation Failure, cluster=yogurtte-k3s-prod, container=chat-service, gc=MarkSweepCompact, instance=10.42.1.55:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-fdcc7c776-kh2dl}` | 21 | 0 | 0 | 0 | **2026-08-12T01:35:00Z ~ 2026-08-12T01:40:00Z** |
| `rate(jvm_gc_pause_seconds_sum[5m])` | `{action=end of minor GC, application=auth-service, cause=G1 Evacuation Pause, cluster=yogurtte-k3s-prod, container=auth-service, gc=G1 Young Generation, instance=10.42.1.51:8090, job=auth-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=auth-service-5999bb9f5c-lzp4p, service=auth-service}` | 21 | 0 | 0.000 | 0 | **2026-08-12T01:36:45Z ~ 2026-08-12T01:40:00Z** |
| `rate(jvm_gc_pause_seconds_sum[5m])` | `{action=end of minor GC, application=auth-service, cause=Metadata GC Threshold, cluster=yogurtte-k3s-prod, container=auth-service, gc=G1 Young Generation, instance=10.42.1.51:8090, job=auth-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=auth-service-5999bb9f5c-lzp4p, service=auth-service}` | 21 | 0 | 0 | 0 | **2026-08-12T01:35:00Z ~ 2026-08-12T01:40:00Z** |
| `rate(jvm_gc_pause_seconds_sum[5m])` | `{action=end of minor GC, application=chat-service, cause=Allocation Failure, cluster=yogurtte-k3s-prod, container=chat-service, gc=Copy, instance=10.42.1.55:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-fdcc7c776-kh2dl}` | 21 | 0.000 | 0.000 | 0.000 | — |
| `rate(jvm_gc_pause_seconds_sum[5m])` | `{action=end of minor GC, application=chat-service, cause=GCLocker Initiated GC, cluster=yogurtte-k3s-prod, container=chat-service, gc=Copy, instance=10.42.1.55:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-fdcc7c776-kh2dl}` | 21 | 0 | 0 | 0 | **2026-08-12T01:35:00Z ~ 2026-08-12T01:40:00Z** |
| `rate(jvm_gc_pause_seconds_sum[5m])` | `{action=end of minor GC, application=content-service, cause=Allocation Failure, cluster=yogurtte-k3s-prod, container=content-service, gc=Copy, instance=10.42.1.53:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-85f648fcff-sp24n}` | 21 | 0 | 0.000 | 0 | **2026-08-12T01:36:45Z ~ 2026-08-12T01:40:00Z** |
| `rate(jvm_gc_pause_seconds_sum[5m])` | `{action=end of minor GC, application=content-service, cause=Allocation Failure, cluster=yogurtte-k3s-prod, container=content-service, gc=Copy, instance=10.42.3.46:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-85f648fcff-v2pw9}` | 21 | 0 | 0 | 0 | **2026-08-12T01:35:00Z ~ 2026-08-12T01:40:00Z** |
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
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=chat-service-fcm-tokens, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=0, topic=user.fcm-tokens}` | 21 | 0 | 0 | 0 | **2026-08-12T01:35:00Z ~ 2026-08-12T01:40:00Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=chat-service-fcm-tokens, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=1, topic=user.fcm-tokens}` | 21 | 0 | 0 | 0 | **2026-08-12T01:35:00Z ~ 2026-08-12T01:40:00Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=chat-service-fcm-tokens, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=2, topic=user.fcm-tokens}` | 21 | 0 | 0 | 0 | **2026-08-12T01:35:00Z ~ 2026-08-12T01:40:00Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=chat-service-notification-settings, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=0, topic=user.notification-settings}` | 21 | 0 | 0 | 0 | **2026-08-12T01:35:00Z ~ 2026-08-12T01:40:00Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=chat-service-notification-settings, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=1, topic=user.notification-settings}` | 21 | 0 | 0 | 0 | **2026-08-12T01:35:00Z ~ 2026-08-12T01:40:00Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=chat-service-notification-settings, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=2, topic=user.notification-settings}` | 21 | 0 | 0 | 0 | **2026-08-12T01:35:00Z ~ 2026-08-12T01:40:00Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=db-writer, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=0, topic=chat.messages}` | 21 | 0 | 0 | 0 | **2026-08-12T01:35:00Z ~ 2026-08-12T01:40:00Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=db-writer, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=1, topic=chat.messages}` | 21 | 0 | 0 | 0 | **2026-08-12T01:35:00Z ~ 2026-08-12T01:40:00Z** |
| `websocket_active_users` | `{__name__=websocket_active_users, application=chat-service, cluster=yogurtte-k3s-prod, container=chat-service, instance=10.42.1.55:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-fdcc7c776-kh2dl}` | 21 | 0 | 0 | 0 | **2026-08-12T01:35:00Z ~ 2026-08-12T01:40:00Z** |

값이 0이던 구간은 굵게 표시했다 — 프로세스가 사라져 시계열이 꺾인 것이 유일한 신호인 장애가 있다.

