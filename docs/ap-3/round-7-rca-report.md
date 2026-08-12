# RCA Report — `scan-1786498493`

| 항목 | 값 |
|---|---|
| 모드 | rca |
| 질문 | 최근 1시간 안에 피드 작성이 실패했다는 제보가 있다. 원인을 조사해줘 |
| 시각 | 2026-08-12T05:05:48.977178900Z |
| provider | claude-cli |
| model | `claude-opus-5` · turns 1 |
| prompt | `.\prompts\system-prompt.md` |
| tokens | in 75390 (cacheRead 23,796 · cacheCreate 51,592) / out 9663 · cost $0.7694 |
| elapsed | total 159951ms (tempo 853 · loki 399 · mimir 601 · assemble 66 · llm 148673) |

## 탐색 (Triage)

| 항목 | 값 |
|---|---|
| 시간창 해석 | 명시적 from/to |
| 시간창 확신도 | EXACT |
| 스윕 창 | 2026-08-12T00:43:34Z ~ 2026-08-12T01:43:34Z |
| 좁힌 창 | 2026-08-12T01:34:53.777306Z ~ 2026-08-12T01:40:00Z |
| 대상 | content-service, auth-service |
| traceId | 6a7bcdbd052a495e0b6b5c4c9b4c3a61 |
| 트레이스 후보 | 3건 |
| 장애 후보 | 7건 · 선택 INC-5, INC-6, INC-7 |
| 계획 파싱 | 성공 |
| 스윕 컨텍스트 | **후보만 — 원본 제외 (B)** |
| prompt | `.\prompts\triage-prompt.md` |
| tokens | in 33622 / out 2053 · cost $0.1887 |
| chars | 컨텍스트 3,208 + 프롬프트 1,399 = **4,607** |
| elapsed | survey 1625ms · llm 37208ms |

**선정 이유**: 질문이 지목한 POST /feeds 실패 트레이스와 content-service의 제약 위반 예외가 01:35에 정확히 겹치고, 직전 auth 업로드 지연이 그 재시도의 상류 후보이기 때문.

**근거**

- INC-7: content-service http post /feeds 트레이스가 2026-08-12T01:35:08.522Z에 error 채널, 86ms — 질문의 '피드 작성' 엔드포인트와 정확히 일치
- INC-6: 같은 분(01:35:00~01:36:00) content-service ERROR/WARN 4건 + SQLIntegrityConstraintViolationException / ConstraintViolationException 각 1건, 01:35~01:40 5회 60초 간격 반복 — 일회성이 아니라 지속 실패
- INC-5: auth-service http post /files/upload 3,063ms (slow 채널, 01:34:53.777Z) — 피드 실패 15초 전 상류 지연, 재시도 유발 시 중복 키 위반과 인과 가능
- 86ms 실패는 지연이 아닌 즉시 거절 → DB 제약 위반 경로와 지문이 맞음
- up / mongodb_up / kafka_brokers / consumergroup_lag / websocket_active_users 모두 이상 0건 — 인프라·큐·소켓 계층은 이 창에서 배제

**스윕이 찾은 트레이스** (고른 것은 6a7bcdbd052a495e0b6b5c4c9b4c3a61)

| traceId | 채널 | root service | root span | ms |
|---|---|---|---|---:|
| `6a7bcdccfb0b88f17f425d5e2fb9908f` | error | content-service | http post /feeds | 86 |
| `6a7bcad606c9ad16a434d244e431e33d` | error | content-service | http post /feeds/{feedId}/comments | 208 |
| `6a7bcdbd052a495e0b6b5c4c9b4c3a61` ←선택 | slow | auth-service | http post /files/upload | 3063 |

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

- INC-1 — chat-service ERROR/WARN 1건뿐이고 피드 작성 경로와 무관한 서비스 — 01:22대 잡음으로 본다.
- INC-2 — 01:22대 content-service DataException은 댓글 경로(INC-3)와 묶이는 별개 지문이고, 제보된 '피드 작성' 실패 시각(01:35)과 13분 어긋난다.
- INC-3 — 엔드포인트가 POST /feeds/{feedId}/comments — 댓글 작성이지 피드 작성이 아니다.
- INC-4 — auth-service NullPointerException은 업로드 지연(INC-5)과 지문이 다르고, 피드 작성 실패의 SQL 제약 위반을 설명하지 못한다 — INC-5로 auth 쪽 창은 이미 덮인다.

**보정 기록**

- 창을 후보 [INC-5, INC-6, INC-7] 의 신호 시각에서 계산했다 (2026-08-12T01:34:53.777306Z ~ 2026-08-12T01:40:00Z)

## 수집 범위 (Coverage)

- **window**: 2026-08-12T01:34:53.777306Z ~ 2026-08-12T01:40:00Z (306s)
- **trace**: 55,865B / 64 spans
- **창 안 후보 트레이스**: 4건 / 55,865B
- **logs**: errwarn=39,952B · traceId=6,862B
- **metrics**: 8 수집 / 54,302B, 누락 [sum(rate(http_server_requests_seconds_count{application="content-service", status="401"}[1m]))]
- **context**: 81,728 chars (+ 시스템 프롬프트 805 chars)

### 토큰 축 (개선 지표)

| 단계 | 총 in | chars | 컨텍스트 토큰 |
|---|---:|---:|---:|
| 탐색 | 33,622 | 4,607 | 3,612 |
| 분석 | 75,390 | 82,533 | 45,380 |
| **합계** | **109,012** | | **48,992** |

- **overheadTokens 30,010 tok — 이 회차에 실측**(1자 프롬프트 프로브, 같은 명령·모델·샌드박스).
  호출마다 한 번씩 얹히므로 단계별로 뺀다. **다른 날 상수를 쓰지 않았다 = `█ 실측`.**
- contextTokens (count_tokens API): 측정 안 됨 — 구독 CLI 경로엔 API 키가 없다
- 비용은 **API 환산 추정치**다. 구독 계정으로 호출하므로 실제 청구액이 아니다.

## 수집 실패/누락

- Metric 'sum(rate(http_server_requests_seconds_count{application="content-service", status="401"}[1m]))' returned no series in this window; skipped.

---

관측 데이터만으로 분석했다. 결론부터: **인프라 장애가 아니라 단일 요청의 애플리케이션 데이터 무결성 위반**이다.

## 1. 원인 후보 랭킹 (최대 3개)

1. **한 요청 안에서 동일 해시태그가 중복 전달 → `tb_feed_hashtags` 유니크 키(`uk_feed_hashtag`) 위반 → 트랜잭션 전체 롤백 → HTTP 500** (피드 작성 실패의 직접 원인)
2. **예외 매핑·트랜잭션 경계 설계 문제** — 입력에서 비롯된 제약 위반이 `handleAllException`을 타고 500으로 나가고, 피드 본문·첨부까지 통째로 롤백됨 (1번의 피해를 키운 증폭 요인)
3. **동시 중복 제출(더블클릭/재시도)로 인한 경합** — 데이터상 거의 배제됨

## 2. 후보별 근거

### 후보 1 — 요청 내 해시태그 중복으로 유니크 제약 위반

**근거 (traceId `6a7bcdccfb0b88f17f425d5e2fb9908f`, 2026-08-12 10:35:08 KST / 01:35:08 UTC)**

같은 커넥션 span(`connection`, 77.873ms) 안의 JDBC 시퀀스가 원인을 그대로 보여준다:

| 시각(절대) | span | 내용 |
|---|---|---|
| 08.537609 | `query` | `insert into tb_feed (...)` → `jdbc.row-affected=1` |
| 08.541377 | `generated-keys` | `jdbc.generated-keys=166` ← **feed_id = 166** |
| 08.550104 | `query` | `select ... from tb_hashtags h1_0 where h1_0.name=?` |
| 08.552036 | `result-set` | **`jdbc.row-count=0`** (없음) |
| 08.556827 | `query` | `insert into tb_hashtags (...)` → row-affected=1 |
| 08.560932 | `generated-keys` | **`jdbc.generated-keys=179`** ← hashtag_id = 179 |
| 08.564095 | `query` | `insert into tb_feed_hashtags (...)` → **row-affected=1** (166-179 성공) |
| 08.573103 | `query` | `select ... from tb_hashtags h1_0 where h1_0.name=?` (재조회) |
| 08.575059 | `result-set` | **`jdbc.row-count=1`** ← 방금 만든 179가 조회됨 |
| 08.579116 | `query` | `insert into tb_feed_hashtags (...)` → **`error: Duplicate entry '166-179' for key 'tb_feed_hashtags.uk_feed_hashtag'`**, `status.code=STATUS_CODE_ERROR` |

즉 **동일 이름의 해시태그가 한 요청 안에서 두 번 처리**됐다. 첫 번째는 미존재 → 생성(179) → 연결(166-179) 삽입 성공, 두 번째는 조회 성공(179) → **같은 (166,179) 쌍을 다시 삽입** → 1062 위반.

로그 원문이 이를 확정한다:
- `WARN ... SqlExceptionHelper - SQL Error: 1062, SQLState: 23000` (10:35:08.586)
- `ERROR ... SqlExceptionHelper - Duplicate entry '166-179' for key 'tb_feed_hashtags.uk_feed_hashtag'` (10:35:08.587)
- `org.springframework.dao.DataIntegrityViolationException: could not execute statement [Duplicate entry '166-179' ...]; constraint [tb_feed_hashtags.uk_feed_hashtag]`

스택의 앱 프레임:
```
FeedController.createFeed(FeedController.java:114)
 → FeedService.createFeed(FeedService.java:223)
   → ExpGrantService.grantFeedCreate(ExpGrantService.java:43)
     → grant(:151) → grantWithCap(:159) → isDuplicate(:225)
```
예외가 `ExpGrantService.isDuplicate:225`에서 터진 것은 **미플러시 상태였던 두 번째 `tb_feed_hashtags` insert가 이 지점의 쿼리 직전 Hibernate 오토플러시로 밀려 나갔기 때문**이다. 근거: 정상 트레이스(`6a7bce09…`)에는 `select eh1_0.exp_history_id from tb_exp_history ...` span이 있으나, **실패 트레이스에는 그 쿼리 span이 아예 없다** — 플러시 단계에서 죽어 조회까지 못 갔다. 따라서 ExpGrantService 자체는 원인이 아니라 플러시 트리거 지점일 뿐이다.

**확신도: 높음**

**반증 데이터: 없음.** (오히려 보강: 61초 뒤 10:36:09의 `POST /feeds`(traceId `6a7bce094ee8b7a55835af4577155a8b`)는 `outcome=SUCCESS, status=200`으로 완주했고, 그 트레이스에서는 `select h1_0 ... where h1_0.name=?` → `result-set row-count=1` 이후 `insert into tb_feed_hashtags` **1회만** 실행됐다. 서비스 전면 장애가 아니라 특정 페이로드 의존 실패임을 뒷받침한다.)

**대기·지연 구간 판정**
- 커넥션 획득 대기: `connection` span 시작 08.528689 → `acquired` 이벤트 08.528878 = **0.189ms**. `hikaricp_connections_pending`은 content-service 두 파드 모두 전 구간 0. 상한(Hikari `connectionTimeout`) **설정값은 미수집**이나, 실측 0.189ms는 어떤 현실적 상한도 넘지 않으므로 **만료 아님**. 최종 상태: **획득 성공**.
- 실패 쿼리 대기: 문제의 insert span 자체는 7.723ms — 락 대기·타임아웃 흔적 없음. **만료 아님**, 최종 상태 **실패(SQLState 23000)**.
- 트랜잭션 최종 상태: `connection` span의 events가 `acquired` → **`rollback`(08.596343)`**. 정상 트레이스의 같은 자리는 `commit`이다. → **이 피드 작성은 전량 롤백되어 폐기**됐다. 재시도 흔적 없음(동일 페이로드의 후속 트레이스·로그 없음).
- HTTP 최종 상태: `http post /feeds` span `outcome=SERVER_ERROR, status=500`, durNs 86.397ms. 로그 `[HTTP] POST /api/feeds 500 - 84ms`.

---

### 후보 2 — 예외 매핑/트랜잭션 경계 (증폭 요인)

**근거**
- `WARN ... GlobalExceptionHandler - [api-error] handleAllException` (10:35:08.598) — 제약 위반이 **전용 핸들러가 아닌 포괄 핸들러**로 떨어졌고, 그 결과 사용자 입력에서 비롯된 충돌이 4xx가 아닌 **500**으로 나갔다. 제보자가 본 "피드 작성 실패"의 표면 증상이 이것이다.
- 트랜잭션 경계가 `FeedService.createFeed` → `ExpGrantService.grantFeedCreate`까지 하나로 묶여 있어(단일 `connection` span 안에 `tb_feed`·`tb_feed_attachment_file`·`tb_hashtags`·경험치 로직이 전부 포함), 해시태그 링크 한 줄 때문에 **이미 성공한 `tb_feed`(id 166), 첨부 2건(generated-keys 261·262)까지 전부 롤백**됐다.

**확신도: 중간** (관측된 동작은 확정적이나, 핸들러 매핑 정책·`@Transactional` 설정 원본 코드는 미수집)

**반증 데이터: 없음.** 단, 이것은 후보 1의 대체 원인이 아니라 종속적 증폭 요인이다.

**대기·지연 구간 판정: 해당 없음** (이 후보에 대기 구간 자체가 없음). 최종 상태는 후보 1과 동일 — **롤백·폐기, 500 응답**.

---

### 후보 3 — 동시 중복 제출(더블클릭/클라이언트 재시도) 경합

**근거(약함)**: 중복 키 위반은 일반적으로 동시 제출에서도 발생한다.

**확신도: 낮음**

**반증 데이터(결정적)**:
- 위반된 키 값이 `'166-179'`인데, **`feed_id=166`은 바로 이 트랜잭션 안에서 08.541377에 생성**된 값이다(`generated-keys=166`). 커밋되지 않은 이 id를 다른 요청이 참조해 링크를 삽입하는 것은 불가능하다. → **트랜잭션 내부 중복**이지 트랜잭션 간 경합이 아니다.
- 동일 창에서 다른 `POST /feeds` 실패 트레이스나 추가 1062 로그가 없다(ERROR/WARN 스트림에 해당 블록 1벌, 반복 `xN` 표기 없음).

**대기·지연 구간 판정: 판정 불가** (경합을 뒷받침할 락 대기 span·타임아웃 이벤트가 애초에 관측되지 않음).

---

### 원인에서 배제한 것 (근거 있는 배제)

- **커넥션 풀 고갈**: `hikaricp_connections_pending` 4개 시리즈 전부 전 구간 0. (다만 `hikaricp_connections_active`도 전 구간 0인데 실제로는 쿼리가 돌았다 — 스크레이프 간격이 짧은 트랜잭션을 놓친 것으로, 이 메트릭 쌍은 이번 판단에 증거력이 낮다.)
- **GC/메모리**: content-service `jvm_gc_pause` rate 최대 2.5e-5초/초, 01:36:38 이후 0. 무시 가능.
- **인프라 다운**: `up` 전 시리즈 1 (mysql 노드 포함 노드/kubelet/cadvisor, `mongodb_up=1`, `kafka_brokers=1`), `kafka_consumergroup_lag` 전 구간 0(일부 -1은 미할당 파티션). 
- **인증 실패**: 응답이 401이 아닌 500이고, `RequestLoggingFilter` 라인의 `userId=NONE`은 SecurityContext 정리 후 필터 로그라 무의미하다(같은 트레이스의 예외 로그는 `userId=1`). 401 메트릭은 **시리즈 없음으로 수집 실패**했으나, 401 자체가 없었다는 정황과 모순되지 않는다.
- **호출 그래프**: 트레이스에서 추출된 엣지는 `content-service --jdbc--> mysql/content` 하나뿐(48회, 최대 108.0ms, error 1건). 서비스 간 원격 호출 실패는 관측 범위에 존재하지 않는다.

---

## 3. 권장 다음 조치

### 이미 발생한 피해 — 복구 가능한가

**DB: 복구 불필요(자동 복구됨).** `connection` span의 `rollback` 이벤트로 트랜잭션 전량 롤백이 확인된다. `tb_feed` 166, `tb_feed_attachment_file` 261·262, `tb_hashtags` 179 모두 남지 않는다. 정리할 부분 데이터 없음. 소모된 AUTO_INCREMENT 번호(166, 179, 261~262)만 영구 결번으로 남으나 기능 영향 없음.

**사용자 피해: 재작성 요청으로만 복구 가능.** 해당 요청은 폐기됐고 재시도 흔적이 없으므로, userId=1의 피드는 **작성되지 않은 상태**다. 서버 측에서 재생할 원본 페이로드가 없다(로그에 요청 본문 미포함) → **관측 데이터만으로는 서버 주도 복구 불가**, 사용자 재제출 필요.

**S3 고아 객체 가능성 — 확인 필요.** 같은 창·같은 userId=1의 auth-service 업로드가 성공해 있다:
`[file] S3 파일 업로드 성공: uploads/2026/08/12/20260812103454_170bceb9.png`, `[file] 파일 업로드 성공 - ID: 2566, 파일명: ap3-1x1.png, 스토리지: S3` (10:34:56, traceId `6a7bcdbd052a495e0b6b5c4c9b4c3a61`, `POST /files/upload` 200).
이 업로드는 **content-service의 DB 트랜잭션 밖**이므로 롤백되지 않았다. 다만 실패한 피드와는 **traceId가 다르고 연결 span도 없어 인과는 미증명**(같은 사용자·12초 선행이라는 정황뿐). → 파일 ID 2566이 어느 피드에도 참조되지 않는지 조회하고, 미참조면 고아 정리 대상.

### 재발 방지

1. **`FeedService.createFeed`에서 해시태그 목록 정규화 후 중복 제거** — 근본 수정 지점. DB 조회·삽입 전에 정규화된 이름 기준으로 dedupe하면 `(166,179)` 이중 삽입 자체가 사라진다. (실패 트레이스의 두 `select ... where name=?`가 같은 행 179로 수렴한 것이 이 정규화 누락의 증거다.)
2. **`tb_feed_hashtags` 삽입을 멱등하게** — 애플리케이션 dedupe가 뚫려도 500으로 새지 않도록 `INSERT ... ON DUPLICATE KEY UPDATE`(또는 `INSERT IGNORE`) 상당으로 전환. 유니크 제약 `uk_feed_hashtag`는 **유지**(데이터 보호 장치이므로 제거하지 말 것).
3. **`DataIntegrityViolationException` 전용 핸들러 추가** — `handleAllException` 포괄 처리에서 분리해 409/400으로 매핑. 500은 클라이언트가 무한 재시도하기 쉬운 상태 코드다.
4. **트랜잭션 경계 재검토** — 경험치 지급(`ExpGrantService`)이 피드 생성과 동일 트랜잭션에 묶여 있어, 부수 로직의 플러시 시점 예외가 본질 데이터(피드·첨부)까지 되돌린다. 경험치 지급을 커밋 후 처리로 분리하면 동일 사고에서 피드 자체는 보존된다.
5. **첨부 파일 수명주기** — 업로드는 auth-service에서 선행 커밋되고 피드 생성은 content-service 트랜잭션이라 실패 시 고아가 남는 구조다. 미참조 파일 정리 배치 또는 업로드 TTL 필요.

### 복구 확인

1. **재현 테스트**: 동일 해시태그를 중복 포함한 `POST /api/feeds`를 스테이징에 전송 → 수정 전 500 재현 확인, 수정 후 200 및 `tb_feed_hashtags`에 `(feed_id, hashtag_id)` 쌍이 **1행만** 생성되는지 확인.
2. **트레이스 확인**: 해당 요청 트레이스의 `connection` span events가 `rollback`이 아닌 **`commit`**인지, `insert into tb_feed_hashtags` span이 1회이며 `STATUS_CODE_ERROR`가 없는지 확인(정상 기준선은 `6a7bce094ee8b7a55835af4577155a8b`).
3. **로그 감시**: `SQL Error: 1062` / `uk_feed_hashtag` / `handleAllException` 발생 0건을 배포 후 최소 1시간 관측.
4. **메트릭 보강 후 확인**: 아래 수집 공백을 메운 뒤 content-service 5xx 비율이 0으로 유지되는지 확인.

---

## 데이터 부족 — 추가 수집 필요 항목

이 공백들 때문에 후보 2의 확신도를 "중간"으로, 후보 3을 "낮음"으로 낮췄다.

- **content-service 상태코드별 요청 메트릭 전체** — 제공된 것은 401 쿼리 하나뿐이고 그마저 시리즈 없음으로 실패했다. `http_server_requests_seconds_count{application="content-service", uri="/feeds"}`를 status별로 수집해야 **실패가 1건인지 다수인지** 확정할 수 있다. 현재는 트레이스 4건·로그 1블록에 근거한 "1건" 추정이다.
- **원본 요청 페이로드(해시태그 배열)** — 중복이 "같은 문자열 반복"인지 "정규화 후 충돌(대소문자·공백·유니코드)"인지 구분해야 1번 수정의 정규화 규칙이 정해진다.
- **`FeedService.java:223` 주변 및 해시태그 처리 코드** — dedupe 위치 확정용.
- **Hikari/JPA/HTTP 타임아웃 설정값** — 이번 건은 대기 자체가 없어 영향 없었으나, 대기 구간의 만료 여부를 앞으로 판정하려면 상한값이 필요하다.
- **두 번째 content-service 파드(`content-service-85f648fcff-v2pw9`, 10.42.3.46) 로그·트레이스** — 관측된 트레이스가 전부 `sp24n` 파드 것이라, v2pw9에서 동일 실패가 더 있었는지 미확인.
- **파일 2566의 피드 참조 여부** — 고아 판정용.

skipped: 코드 수정·쿼리 실행. 재현·수정 지점이 확정되면 `FeedService.createFeed` dedupe 한 줄 + `DataIntegrityViolation` 핸들러가 최소 diff다.

---

## 관측 증거 (Evidence)

> 이 조사가 실제로 본 값이다. 원본 응답 전체는 `reports/raw/scan-1786498493-*.json`에 있다.

### 호출 그래프 (트레이스에서 추출)

```
content-service --jdbc--> mysql/content (HikariPool-1)  48회  최대 108.0ms
    error: Duplicate entry '166-179' for key 'tb_feed_hashtags.uk_feed_hashtag'
    events: acquired, rollback, commit
```

### span (duration 상위 15 / 전체 64)

| ms | service | span | 시작 |
|---:|---|---|---|
| 3063.88 | auth-service | `http post /files/upload` | 2026-08-12T01:34:53.777306Z |
| 2738.75 | auth-service | `secured request` | 2026-08-12T01:34:54.101230Z |
| 323.23 | auth-service | `security filterchain before` | 2026-08-12T01:34:53.777939Z |
| 155.26 | auth-service | `http post /files/upload` | 2026-08-12T01:36:08.952182Z |
| 138.13 | auth-service | `secured request` | 2026-08-12T01:36:08.968989Z |
| 116.86 | content-service | `http post /feeds` | 2026-08-12T01:36:09.253876Z |
| 114.31 | content-service | `secured request` | 2026-08-12T01:36:09.254175Z |
| 108.02 | content-service | `connection` | 2026-08-12T01:36:09.259531Z |
| 86.40 | content-service | `http post /feeds` | 2026-08-12T01:35:08.522901Z |
| 83.38 | content-service | `secured request` | 2026-08-12T01:35:08.523268Z |
| 77.87 | content-service | `connection` | 2026-08-12T01:35:08.528689Z |
| 16.01 | auth-service | `security filterchain before` | 2026-08-12T01:36:08.952919Z |
| 7.72 | content-service | `query` | 2026-08-12T01:35:08.579116Z |
| 3.69 | content-service | `query` | 2026-08-12T01:35:08.556827Z |
| 3.45 | content-service | `query` | 2026-08-12T01:36:09.347248Z |

### 로그 원문 (60 / 전체 253줄)

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
| `hikaricp_connections_active` | `{__name__=hikaricp_connections_active, application=auth-service, cluster=yogurtte-k3s-prod, container=auth-service, instance=10.42.1.51:8090, job=auth-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=auth-service-5999bb9f5c-lzp4p, pool=HikariPool-1, service=auth-service}` | 21 | 0 | 0 | 0 | **2026-08-12T01:34:53Z ~ 2026-08-12T01:39:53Z** |
| `hikaricp_connections_active` | `{__name__=hikaricp_connections_active, application=chat-service, cluster=yogurtte-k3s-prod, container=chat-service, instance=10.42.1.55:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-fdcc7c776-kh2dl, pool=HikariPool-1}` | 21 | 0 | 0 | 0 | **2026-08-12T01:34:53Z ~ 2026-08-12T01:39:53Z** |
| `hikaricp_connections_active` | `{__name__=hikaricp_connections_active, application=content-service, cluster=yogurtte-k3s-prod, container=content-service, instance=10.42.1.53:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-85f648fcff-sp24n, pool=HikariPool-1}` | 21 | 0 | 0 | 0 | **2026-08-12T01:34:53Z ~ 2026-08-12T01:39:53Z** |
| `hikaricp_connections_active` | `{__name__=hikaricp_connections_active, application=content-service, cluster=yogurtte-k3s-prod, container=content-service, instance=10.42.3.46:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-85f648fcff-v2pw9, pool=HikariPool-1}` | 21 | 0 | 0 | 0 | **2026-08-12T01:34:53Z ~ 2026-08-12T01:39:53Z** |
| `hikaricp_connections_pending` | `{__name__=hikaricp_connections_pending, application=auth-service, cluster=yogurtte-k3s-prod, container=auth-service, instance=10.42.1.51:8090, job=auth-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=auth-service-5999bb9f5c-lzp4p, pool=HikariPool-1, service=auth-service}` | 21 | 0 | 0 | 0 | **2026-08-12T01:34:53Z ~ 2026-08-12T01:39:53Z** |
| `hikaricp_connections_pending` | `{__name__=hikaricp_connections_pending, application=chat-service, cluster=yogurtte-k3s-prod, container=chat-service, instance=10.42.1.55:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-fdcc7c776-kh2dl, pool=HikariPool-1}` | 21 | 0 | 0 | 0 | **2026-08-12T01:34:53Z ~ 2026-08-12T01:39:53Z** |
| `hikaricp_connections_pending` | `{__name__=hikaricp_connections_pending, application=content-service, cluster=yogurtte-k3s-prod, container=content-service, instance=10.42.1.53:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-85f648fcff-sp24n, pool=HikariPool-1}` | 21 | 0 | 0 | 0 | **2026-08-12T01:34:53Z ~ 2026-08-12T01:39:53Z** |
| `hikaricp_connections_pending` | `{__name__=hikaricp_connections_pending, application=content-service, cluster=yogurtte-k3s-prod, container=content-service, instance=10.42.3.46:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-85f648fcff-v2pw9, pool=HikariPool-1}` | 21 | 0 | 0 | 0 | **2026-08-12T01:34:53Z ~ 2026-08-12T01:39:53Z** |
| `rate(jvm_gc_pause_seconds_sum[5m])` | `{action=end of major GC, application=chat-service, cause=Allocation Failure, cluster=yogurtte-k3s-prod, container=chat-service, gc=MarkSweepCompact, instance=10.42.1.55:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-fdcc7c776-kh2dl}` | 21 | 0 | 0 | 0 | **2026-08-12T01:34:53Z ~ 2026-08-12T01:39:53Z** |
| `rate(jvm_gc_pause_seconds_sum[5m])` | `{action=end of minor GC, application=auth-service, cause=G1 Evacuation Pause, cluster=yogurtte-k3s-prod, container=auth-service, gc=G1 Young Generation, instance=10.42.1.51:8090, job=auth-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=auth-service-5999bb9f5c-lzp4p, service=auth-service}` | 21 | 0 | 0.000 | 0 | **2026-08-12T01:36:38Z ~ 2026-08-12T01:39:53Z** |
| `rate(jvm_gc_pause_seconds_sum[5m])` | `{action=end of minor GC, application=auth-service, cause=Metadata GC Threshold, cluster=yogurtte-k3s-prod, container=auth-service, gc=G1 Young Generation, instance=10.42.1.51:8090, job=auth-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=auth-service-5999bb9f5c-lzp4p, service=auth-service}` | 21 | 0 | 0 | 0 | **2026-08-12T01:34:53Z ~ 2026-08-12T01:39:53Z** |
| `rate(jvm_gc_pause_seconds_sum[5m])` | `{action=end of minor GC, application=chat-service, cause=Allocation Failure, cluster=yogurtte-k3s-prod, container=chat-service, gc=Copy, instance=10.42.1.55:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-fdcc7c776-kh2dl}` | 21 | 0.000 | 0.000 | 0.000 | — |
| `rate(jvm_gc_pause_seconds_sum[5m])` | `{action=end of minor GC, application=chat-service, cause=GCLocker Initiated GC, cluster=yogurtte-k3s-prod, container=chat-service, gc=Copy, instance=10.42.1.55:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-fdcc7c776-kh2dl}` | 21 | 0 | 0 | 0 | **2026-08-12T01:34:53Z ~ 2026-08-12T01:39:53Z** |
| `rate(jvm_gc_pause_seconds_sum[5m])` | `{action=end of minor GC, application=content-service, cause=Allocation Failure, cluster=yogurtte-k3s-prod, container=content-service, gc=Copy, instance=10.42.1.53:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-85f648fcff-sp24n}` | 21 | 0 | 0.000 | 0 | **2026-08-12T01:36:38Z ~ 2026-08-12T01:39:53Z** |
| `rate(jvm_gc_pause_seconds_sum[5m])` | `{action=end of minor GC, application=content-service, cause=Allocation Failure, cluster=yogurtte-k3s-prod, container=content-service, gc=Copy, instance=10.42.3.46:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-85f648fcff-v2pw9}` | 21 | 0 | 0 | 0 | **2026-08-12T01:34:53Z ~ 2026-08-12T01:39:53Z** |
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
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=chat-service-fcm-tokens, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=0, topic=user.fcm-tokens}` | 21 | 0 | 0 | 0 | **2026-08-12T01:34:53Z ~ 2026-08-12T01:39:53Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=chat-service-fcm-tokens, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=1, topic=user.fcm-tokens}` | 21 | 0 | 0 | 0 | **2026-08-12T01:34:53Z ~ 2026-08-12T01:39:53Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=chat-service-fcm-tokens, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=2, topic=user.fcm-tokens}` | 21 | 0 | 0 | 0 | **2026-08-12T01:34:53Z ~ 2026-08-12T01:39:53Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=chat-service-notification-settings, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=0, topic=user.notification-settings}` | 21 | 0 | 0 | 0 | **2026-08-12T01:34:53Z ~ 2026-08-12T01:39:53Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=chat-service-notification-settings, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=1, topic=user.notification-settings}` | 21 | 0 | 0 | 0 | **2026-08-12T01:34:53Z ~ 2026-08-12T01:39:53Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=chat-service-notification-settings, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=2, topic=user.notification-settings}` | 21 | 0 | 0 | 0 | **2026-08-12T01:34:53Z ~ 2026-08-12T01:39:53Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=db-writer, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=0, topic=chat.messages}` | 21 | 0 | 0 | 0 | **2026-08-12T01:34:53Z ~ 2026-08-12T01:39:53Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=db-writer, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=1, topic=chat.messages}` | 21 | 0 | 0 | 0 | **2026-08-12T01:34:53Z ~ 2026-08-12T01:39:53Z** |
| `websocket_active_users` | `{__name__=websocket_active_users, application=chat-service, cluster=yogurtte-k3s-prod, container=chat-service, instance=10.42.1.55:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-fdcc7c776-kh2dl}` | 21 | 0 | 0 | 0 | **2026-08-12T01:34:53Z ~ 2026-08-12T01:39:53Z** |

값이 0이던 구간은 굵게 표시했다 — 프로세스가 사라져 시계열이 꺾인 것이 유일한 신호인 장애가 있다.

