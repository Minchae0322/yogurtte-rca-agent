# RCA Report — `scan-1786498260`

| 항목 | 값 |
|---|---|
| 모드 | rca |
| 질문 | 최근 1시간 안에 피드 작성이 실패했다는 제보가 있다. 원인을 조사해줘 |
| 시각 | 2026-08-12T01:44:20.260563300Z |
| provider | claude-cli |
| model | `claude-opus-5` · turns 1 |
| prompt | `.\prompts\system-prompt.md` |
| tokens | in 87037 (cacheRead 23,796 · cacheCreate 63,239) / out 10040 · cost $0.8953 |
| elapsed | total 174145ms (tempo 913 · loki 314 · mimir 770 · assemble 354 · llm 158378) |

## 탐색 (Triage)

| 항목 | 값 |
|---|---|
| 시간창 해석 | 상대 표현 '최근 1시간' |
| 시간창 확신도 | EXACT |
| 스윕 창 | 2026-08-12T00:43:34.974424900Z ~ 2026-08-12T01:43:34.974424900Z |
| 좁힌 창 | 2026-08-12T01:31:00Z ~ 2026-08-12T01:40:00Z |
| 대상 | content-service, auth-service |
| traceId | 6a7bcdbd052a495e0b6b5c4c9b4c3a61 |
| 트레이스 후보 | 3건 |
| 장애 후보 | 7건 · 선택 INC-4, INC-5, INC-6, INC-7 |
| 계획 파싱 | 성공 |
| 스윕 컨텍스트 | **후보만 — 원본 제외 (B)** |
| prompt | `.\prompts\triage-prompt.md` |
| tokens | in 33637 / out 2159 · cost $0.1915 |
| chars | 컨텍스트 3,231 + 프롬프트 1,399 = **4,630** |
| elapsed | survey 3170ms · llm 41723ms |

**선정 이유**: 질문이 지목한 피드 작성 엔드포인트(POST /feeds)의 error 트레이스와 동일 시각 content-service DB 제약 위반 로그가 겹치므로 INC-6/INC-7을 핵심으로, 직전 auth-service 업로드 지연·NPE(INC-4/INC-5)를 상류 후보로 함께 고른다.

**근거**

- INC-7: content-service http post /feeds 86ms error 채널, traceId 6a7bcdccfb0b88f17f425d5e2fb9908f (2026-08-12T01:35:08.522Z) — 질문의 '피드 작성' 엔드포인트와 정확히 일치
- INC-6: 같은 분(01:35~01:36) content-service ERROR/WARN 4건 + SQLIntegrityConstraintViolationException / ConstraintViolationException 각 x5회(01:35~01:40, 평균 60초 간격) — 트레이스 error와 로그 지문이 같은 시각에 겹침
- 86ms라는 짧은 지속시간은 타임아웃이 아니라 DB 제약 위반으로 즉시 롤백된 형태와 부합
- INC-5: auth-service http post /files/upload 3,063ms (slow 채널, 01:34:53.777Z) — 피드 작성 15초 전, 첨부 업로드가 상류일 경우 원인 후보
- INC-4: auth-service ERROR/WARN x4회(01:31~01:35) + NullPointerException x5회(01:32~01:37, 60초 간격) — INC-5와 같은 서비스·시각대의 로그측 지문
- 무신호 근거: up / mongodb_up / kafka_brokers / kafka_consumergroup_lag / websocket_active_users 모두 수집되었고 이상 0건 — 파드 다운·인프라 계층 원인은 이 창에서 배제

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

- INC-3 — 엔드포인트가 POST /feeds/{feedId}/comments로 댓글 작성이며, 시각도 01:22:30으로 제보 증상(피드 작성)과 대상이 다르다.
- INC-2 — 01:22 구간 content-service DataException은 INC-3의 댓글 요청과 짝을 이루는 로그 지문으로 보이며, 피드 작성 창(01:35)과 13분 떨어져 있다.
- INC-1 — chat-service ERROR/WARN 1건뿐이고 피드 작성 경로에 chat-service가 관여한다는 신호가 없다.

**보정 기록**

- 창을 후보 [INC-4, INC-5, INC-6, INC-7] 의 신호 시각에서 계산했다 (2026-08-12T01:31:00Z ~ 2026-08-12T01:40:00Z)

## 수집 범위 (Coverage)

- **window**: 2026-08-12T01:31:00Z ~ 2026-08-12T01:40:00Z (540s)
- **trace**: 71,294B / 74 spans
- **창 안 후보 트레이스**: 10건 / 71,294B
- **logs**: errwarn=102,105B · traceId=7,530B
- **metrics**: 8 수집 / 78,632B, 누락 [sum(rate(http_server_requests_seconds_count{application="content-service", status="401"}[1m]))]
- **context**: 104,193 chars (+ 시스템 프롬프트 805 chars)

### 토큰 축 (개선 지표)

| 단계 | 총 in | chars | 컨텍스트 토큰 |
|---|---:|---:|---:|
| 탐색 | 33,637 | 4,630 | 3,627 |
| 분석 | 87,037 | 104,998 | 57,027 |
| **합계** | **120,674** | | **60,654** |

- **overheadTokens 30,010 tok — 이 회차에 실측**(1자 프롬프트 프로브, 같은 명령·모델·샌드박스).
  호출마다 한 번씩 얹히므로 단계별로 뺀다. **다른 날 상수를 쓰지 않았다 = `█ 실측`.**
- contextTokens (count_tokens API): 측정 안 됨 — 구독 CLI 경로엔 API 키가 없다
- 비용은 **API 환산 추정치**다. 구독 계정으로 호출하므로 실제 청구액이 아니다.

## 수집 실패/누락

- Metric 'sum(rate(http_server_requests_seconds_count{application="content-service", status="401"}[1m]))' returned no series in this window; skipped.

---

## 1. 원인 후보 랭킹 (최대 3개)

1. **같은 피드에 같은 해시태그가 두 번 연결되어 `uk_feed_hashtag` 유니크 제약 위반 → 트랜잭션 전체 롤백 → 500** (피드 작성 실패의 직접 원인)
2. **`DataIntegrityViolationException`이 전용 핸들러 없이 `handleAllException`으로 떨어져 500으로 응답** (1의 결과를 "장애"로 만든 증폭 요인)
3. **auth-service의 NPE(`FollowCondition.size`)·업로드 지연** — 같은 창에서 관측되지만 피드 작성 실패와는 무관으로 판정

---

## 2. 후보별 근거

### 후보 1 — 해시태그 중복 링크 삽입 (uk_feed_hashtag 위반)

**근거**

실패 트레이스 `6a7bcdccfb0b88f17f425d5e2fb9908f` (content-service, pod `content-service-85f648fcff-sp24n`) 의 단일 커넥션 span 내 시퀀스(시작 시각 절대값, `1786498508.xxx`):

| 시각(ns 하위) | span | 값 |
|---|---|---|
| .537609 | `query` insert into **tb_feed** | row-affected 1 |
| .541377 | `generated-keys` | **166** ← feed_id |
| .550104 | `query` select tb_hashtags where name=? | |
| .552036 | `result-set` | **row-count 0** (미존재) |
| .556827 | `query` insert into tb_hashtags | row-affected 1 |
| .560932 | `generated-keys` | **179** ← hashtag_id |
| .564095 | `query` insert into **tb_feed_hashtags** | **row-affected 1** (166-179 최초 링크 성공) |
| .573103 | `query` select tb_hashtags where name=? | |
| .575059 | `result-set` | **row-count 1** (이번엔 179를 찾음) |
| .579116 | `query` insert into **tb_feed_hashtags** | **error: `Duplicate entry '166-179' for key 'tb_feed_hashtags.uk_feed_hashtag'`**, `STATUS_CODE_ERROR`, 7.723ms |

즉 `'166-179'` = (feed_id 166, hashtag_id 179). **한 요청 안에서 동일 해시태그가 두 번 처리**되어, 첫 번째는 신규 생성 후 링크 성공, 두 번째는 같은 id를 조회해 같은 쌍을 다시 insert → 1062/23000.

로그 원문이 일치한다:
- `WARN … o.h.e.jdbc.spi.SqlExceptionHelper - SQL Error: 1062, SQLState: 23000` (10:35:08.586)
- `ERROR … SqlExceptionHelper - Duplicate entry '166-179' for key 'tb_feed_hashtags.uk_feed_hashtag'` (10:35:08.587)
- `org.springframework.dao.DataIntegrityViolationException: could not execute statement [Duplicate entry '166-179' …] [insert into tb_feed_hashtags (created_at,feed_id,hashtag_id,updated_at) values (?,?,?,?)]; constraint [tb_feed_hashtags.uk_feed_hashtag]`
- `ERROR … RequestLoggingFilter - [HTTP] POST /api/feeds 500 - 84ms`

**스택의 `ExpGrantService`는 원인이 아니라 발현 지점이다.** 예외 스택은 `ExpGrantService.isDuplicate(ExpGrantService.java:225)` → `grantWithCap(:159)` → `grant(:151)` → `grantFeedCreate(:43)` → `FeedService.createFeed(FeedService.java:223)` 인데, 실패한 SQL은 `tb_feed_hashtags` insert다. `createFeed`가 큐에 쌓아둔 insert가 `ExpGrantService`의 조회 직전 Hibernate flush에서 터진 것이다 — 보상/경험치 로직 자체에는 결함 근거가 없다.

**정상 트레이스와의 대조**: 같은 엔드포인트의 성공 트레이스 `6a7bce094ee8b7a55835af4577155a8b` (10:36:09, `POST /feeds 200`, 116.8ms)에는 `insert into tb_feed_hashtags`가 **정확히 1회**뿐이고, 이후 exp 흐름(`tb_exp_history`, `tb_user_reward … for update`, `tb_daily_exp_cap`, `update tb_feed set quality_bonus_granted`)까지 끝난 뒤 `commit` 이벤트로 종료된다. 차이는 오직 "같은 해시태그 쌍의 2회 insert" 하나다.

**확신도: 높음**

**반증 데이터: 없음.** (인프라 측 대안 원인은 모두 배제됨 — `up` 전 구간 1, `hikaricp_connections_active/pending` 4개 인스턴스 전 구간 0, `jvm_gc_pause` content-service 최대 2.5e-5 s/s, `kafka_brokers`=1·consumer lag 전 구간 0, `mongodb_up`=1.)

**대기·지연 구간 판정**
- HikariCP 커넥션 획득: span `connection` 시작 `.528689` → 이벤트 `acquired` `.528878` = **0.189ms**. 타임아웃 설정값(`connectionTimeout`) 미확보이나, `hikaricp_connections_pending`이 전 구간 0이므로 **만료 아님 / 획득 성공**.
- 트랜잭션 보유: `connection` span 77.873ms, 이벤트 **`rollback` @ `.596343`** (성공 트레이스의 `commit`과 대비).
- **최종 상태: 실패 · 롤백 · 재시도 없음.** 재시도 근거(동일 traceId 재발, 리트라이 로그, 후속 동일 요청)는 관측되지 않았다. 롤백으로 feed_id 166, hashtag 179, 첨부 261·262는 **모두 미영속**이다(자동증가 id만 소모).

---

### 후보 2 — DataIntegrityViolationException의 전용 처리 부재

**근거**
`WARN … c.e.t.a.c.e.GlobalExceptionHandler - [api-error] handleAllException` (10:35:08.598) — 제약 위반이 **포괄 핸들러**로 흘러 `POST /api/feeds 500`이 되었다. 서버 span 속성도 `"outcome":"SERVER_ERROR","status":"500","exception":"none"`. 사용자 입력(중복 해시태그)에서 비롯된 상황이 5xx로 보고되어, 제보가 "서버 장애"로 접수되고 클라이언트 재시도도 유도되지 않았다.

이는 후보 1과 독립된 원인이 아니라 **동일 사건의 두 번째 결함 지점**이다(입력 정규화 부재 + 예외 매핑 부재).

**확신도: 중간** (핸들러 이름과 500 응답은 확정 사실이나, 핸들러 코드/매핑 정책 자체는 관측 데이터에 없음)

**반증 데이터: 없음**

**대기·지연 구간 판정**: 해당 없음(대기 구간 아님). 요청 최종 상태는 **실패(500), 폐기**.

---

### 후보 3 — auth-service NPE 및 업로드 지연 (피드 작성 실패와 무관)

**근거 및 배제 사유**
- `java.lang.NullPointerException: Cannot invoke "java.lang.Integer.intValue()" because "this.size" is null` at `FollowCondition$FollowingSearch.limit(FollowCondition.java:25)` / `FollowerSearch.limit(FollowCondition.java:45)` → `GET /api/user/1/following 500 - 99ms`, `GET /api/user/1/followers 500 - 67ms` (10:32:07, traceId `6a7bcd177ec7…`, `6a7bcd1719fe…`). **엔드포인트가 팔로우 목록 조회이고 서비스도 auth-service다.** 호출 그래프에 content→auth 엣지가 없어 피드 작성 경로와 연결할 근거가 없다.
- `[HTTP-SLOW] POST /api/files/upload 200 - 3063ms` (10:34:56, traceId `6a7bcdbd052a…`) — **200 SUCCESS**, 로그도 `S3 파일 업로드 성공: uploads/2026/08/12/20260812103454_170bceb9.png`, `파일 업로드 성공 - ID: 2566`. 실패가 아니다.
- `HHH90003004: firstResult/maxResults specified with collection fetch; applying in memory` (10:31:56, 10:33:07) — 팔로우 조회의 인메모리 페이징 경고, `GET /api/user/1/following 200 - 2983ms`의 지연 요인. 피드 경로와 무관.

**확신도(피드 작성 실패의 원인으로서): 낮음** / (auth-service의 독립 결함으로서는: 높음)

**반증 데이터**: 실패한 피드 요청 `6a7bcdcc…`의 span 23개 전체에 auth-service·redis·외부 호출 span이 하나도 없다. 실패는 content-service 단일 트랜잭션 내부에서 완결된다.

**대기·지연 구간 판정**
- `POST /files/upload` 3063ms, `GET /following` 2983ms: **타임아웃 설정값(서버 소켓/게이트웨이/클라이언트) 미확보 → 만료 여부 판정 불가.** 다만 두 건 모두 **최종 상태 200 성공**이므로 어떤 상한도 실제로 만료되지 않았음이 결과로 확인된다.
- NPE 2건: 대기 구간 아님. **최종 상태 실패(500), 재시도 없음, 폐기.**

---

### 데이터 부족 항목 (결론 확신도에 반영)

- **수집 실패**: `sum(rate(http_server_requests_seconds_count{application="content-service", status="401"}[1m]))` 무시리즈. → 인증 계열 원인은 **확인도 배제도 불가**. 다만 실패 트레이스의 필터체인이 12/12 전부 통과하고 로그에 `userId=1`이 찍혀 인증은 성립했다.
- **`status="5xx"` 카운터 미수집**: 창 내 `/feeds` 실패가 **몇 건인지 알 수 없다**. 관측된 500은 1건(10:35:08)이고, 트레이스 10건은 "수집 순서"의 표본일 뿐이다. → **피해 규모 판정 불가.**
- **바인딩 파라미터 미수집**: 두 번의 `select tb_hashtags where name=?`에 들어간 실제 문자열을 알 수 없다. 따라서 원인이 (a) 클라이언트가 동일 문자열을 중복 전송했는지, (b) 정규화(대소문자/공백/`#` 제거)가 서로 다른 입력을 같은 name으로 접었는지 **구분 불가**. 어느 쪽이든 "링크 삽입 전 (feed_id, hashtag_id) 중복 제거 부재"라는 결함은 동일하다.
- **두 번째 content-service 파드(`content-service-85f648fcff-v2pw9`, 10.42.3.46)** 의 트레이스·로그가 표본에 없다. 관측된 사건은 전부 `sp24n`.

---

## 3. 권장 다음 조치

### 이미 발생한 피해: 복구 가능한가

**content DB: 복구 불필요.** `connection` span의 `rollback` 이벤트(`1786498508596343000`)로 해당 트랜잭션 전체가 되돌려졌다. `tb_feed`(166), `tb_hashtags`(179), `tb_feed_attachment_file`(261·262), `tb_feed_hashtags`(236)는 **커밋되지 않았다.** 부분 저장된 고아 피드는 없다. AUTO_INCREMENT 값만 소모(성공 트레이스는 feed 167로 이어짐) — 무해.

**S3/파일 레코드: 고아 가능성 있음, 복구 방법 존재.** `POST /api/files/upload`가 별도 트랜잭션·별도 서비스(auth-service)에서 `ID: 2566`, `uploads/2026/08/12/20260812103454_170bceb9.png`로 **성공 커밋**된 반면, 이를 참조할 피드는 롤백되었다. 단, 업로드 트레이스(`6a7bcdbd…`, 10:34:56)와 실패한 피드 요청(`6a7bcdcc…`, 10:35:08)은 **traceId가 달라 동일 사용자 플로우임이 직접 입증되지는 않는다**(12초 간격·둘 다 `userId=1`이라는 정황뿐). → 조치: `tb_feed_attachment_file`에서 참조되지 않는 파일 ID를 기준일 이후로 조회해 고아 목록을 뽑고, 보존 기간 경과분만 정리. **데이터 손실은 없으므로 급하지 않다.**

**사용자 피드: 재작성으로 복구.** 서버가 보관한 요청 본문이 없어 서버 측 자동 재생성은 불가. 제보자에게 **동일 내용 재제출**을 안내하면 되고, 중복 해시태그를 제거해 보내면 성공 트레이스와 동일 경로로 처리된다.

### 재발 방지

1. **`FeedService.createFeed`(FeedService.java:223 부근)에서 해시태그 목록을 링크 삽입 전에 정규화 후 중복 제거**한다(정규화 키 기준 `LinkedHashSet`). 근본 지점은 "이름→id 해석"이 아니라 "(feed_id, hashtag_id) 링크 생성" 직전이다 — 서로 다른 입력 문자열이 같은 id로 접히는 경우(b)까지 한 곳에서 막힌다.
2. **DB 제약은 유지**한다. `uk_feed_hashtag`는 결함을 잡아준 안전망이므로 제거 대상이 아니다. 애플리케이션 방어를 추가하는 것이지 제약을 완화하는 것이 아니다.
3. **`DataIntegrityViolationException` 전용 핸들러 추가.** `handleAllException`이 삼키지 않도록 하고, 사용자 입력 기인 제약 위반은 4xx(409/400)로 매핑한다. 5xx 알람 노이즈가 줄고 원인 구분이 가능해진다.
4. **바인딩 파라미터 로깅**(운영에서는 개인정보 고려해 해시태그 필드 한정)을 켜서 (a)/(b) 구분을 다음 발생 시 즉시 가능하게 한다.
5. (범위 밖·별건) auth-service `FollowCondition.limit()`의 `size` null 역참조 — `FollowCondition.java:25`, `:45`에 기본값/널 가드. 창 내 2건의 500을 유발 중이며 피드 결함과 무관하게 상시 재현된다.

### 복구 확인

1. `SELECT COUNT(*) FROM tb_feed WHERE id=166` = 0, `SELECT COUNT(*) FROM tb_hashtags WHERE id=179` = 0 (또는 179가 다른 이름으로 재사용되지 않았음) 확인 → 롤백 완결성 검증.
2. **동일 해시태그를 의도적으로 2회 포함한 피드**를 스테이징에서 작성 → 응답이 200(중복 제거 후 링크 1건)인지, 트레이스에 `insert into tb_feed_hashtags`가 정확히 1회이고 `connection` span 이벤트가 `commit`인지 확인. 성공 기준선은 트레이스 `6a7bce094ee8b7a55835af4577155a8b`(200, 116.8ms, 링크 insert 1회, commit).
3. **현재 미수집 상태인 `http_server_requests_seconds_count{application="content-service",uri="/feeds",status="500"}` 시리즈를 수집·대시보드화**한 뒤, 배포 후 30분간 0을 유지하는지 확인. 지금은 이 지표가 없어 피해 규모도 복구 여부도 정량 확인이 불가능하다 — **이것이 가장 먼저 메워야 할 관측 공백이다.**
4. `SqlExceptionHelper`의 `SQL Error: 1062` 로그가 재발하지 않는지 Loki에서 모니터링(재발 시 정규화 규칙이 케이스를 덜 접은 것).

---

## 관측 증거 (Evidence)

> 이 조사가 실제로 본 값이다. 원본 응답 전체는 `reports/raw/scan-1786498260-*.json`에 있다.

### 호출 그래프 (트레이스에서 추출)

```
content-service --db--> redis  1회  최대 0.4ms  [INFO]
content-service --jdbc--> mysql/content (HikariPool-1)  50회  최대 108.0ms
    error: Duplicate entry '166-179' for key 'tb_feed_hashtags.uk_feed_hashtag'
    events: acquired, rollback, commit
```

### span (duration 상위 15 / 전체 74)

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
| 4.92 | content-service | `secured request` | 2026-08-12T01:31:08.239939Z |
| 3.69 | content-service | `query` | 2026-08-12T01:35:08.556827Z |

### 로그 원문 (60 / 전체 670줄)

전체가 상한을 넘어 ERROR/WARN 줄을 우선 발췌했다. 나머지는 원본 파일에 있다.

```
2026-08-12T01:31:56.206744805Z  [auth-service]  [2m2026-08-12 10:31:56[0;39m [2m[http-nio-8081-exec-5][0;39m [33m WARN [traceId=6a7bcd0988d2fa47b225dfd17cd82615,spanId=b65543a466233f59,userId=1][0;39m [36morg.hibernate.orm.query[0;39m [2m-[0;39m HHH90003004: firstResult/maxResults specified with collection fetch; applying in memory
2026-08-12T01:31:56.392608168Z  [auth-service]  [2m2026-08-12 10:31:56[0;39m [2m[http-nio-8081-exec-5][0;39m [33m WARN [traceId=6a7bcd0988d2fa47b225dfd17cd82615,spanId=b225dfd17cd82615,userId=NONE][0;39m [36mc.e.t.a.c.f.RequestLoggingFilter[0;39m [2m-[0;39m [HTTP-SLOW] GET /api/user/1/following 200 - 2983ms
2026-08-12T01:32:07.271078769Z  [auth-service]  [2m2026-08-12 10:32:07[0;39m [2m[http-nio-8081-exec-7][0;39m [31mERROR [traceId=6a7bcd177ec7dcbafab90613c89ef91a,spanId=da80a975e6b830c2,userId=1][0;39m [36mc.e.t.a.c.e.GlobalExceptionHandler[0;39m [2m-[0;39m [api-error] handleAllException
2026-08-12T01:32:07.271126789Z  [auth-service]  java.lang.NullPointerException: Cannot invoke "java.lang.Integer.intValue()" because "this.size" is null
2026-08-12T01:32:07.271130500Z  [auth-service]  at com.example.toyauth.app.user.controller.dto.FollowCondition$FollowingSearch.limit(FollowCondition.java:25)
2026-08-12T01:32:07.271133759Z  [auth-service]  at com.example.toyauth.app.follow.repository.querydsl.impl.FollowRepositoryCustomImpl.findFollowingsByUserId(FollowRepositoryCustomImpl.java:35)
2026-08-12T01:32:07.271147242Z  [auth-service]  at org.springframework.aop.support.AopUtils.invokeJoinpointUsingReflection(AopUtils.java:352)
2026-08-12T01:32:07.271150088Z  [auth-service]  at org.springframework.aop.framework.ReflectiveMethodInvocation.invokeJoinpoint(ReflectiveMethodInvocation.java:196)
2026-08-12T01:32:07.271152471Z  [auth-service]  at org.springframework.aop.framework.ReflectiveMethodInvocation.proceed(ReflectiveMethodInvocation.java:163)
2026-08-12T01:32:07.271155205Z  [auth-service]  at org.springframework.aop.framework.CglibAopProxy$CglibMethodInvocation.proceed(CglibAopProxy.java:765)
2026-08-12T01:32:07.271158867Z  [auth-service]  at org.springframework.dao.support.PersistenceExceptionTranslationInterceptor.invoke(PersistenceExceptionTranslationInterceptor.java:137)
2026-08-12T01:32:07.271161572Z  [auth-service]  at org.springframework.aop.framework.ReflectiveMethodInvocation.proceed(ReflectiveMethodInvocation.java:184)
2026-08-12T01:32:07.271163959Z  [auth-service]  at org.springframework.aop.framework.CglibAopProxy$CglibMethodInvocation.proceed(CglibAopProxy.java:765)
2026-08-12T01:32:07.271166422Z  [auth-service]  at org.springframework.aop.framework.CglibAopProxy$DynamicAdvisedInterceptor.intercept(CglibAopProxy.java:717)
2026-08-12T01:32:07.271182034Z  [auth-service]  at org.springframework.aop.support.AopUtils.invokeJoinpointUsingReflection(AopUtils.java:352)
2026-08-12T01:32:07.271184671Z  [auth-service]  at org.springframework.data.repository.core.support.RepositoryMethodInvoker$RepositoryFragmentMethodInvoker.lambda$new$0(RepositoryMethodInvoker.java:277)
2026-08-12T01:32:07.271202817Z  [auth-service]  at org.springframework.data.repository.core.support.RepositoryMethodInvoker.doInvoke(RepositoryMethodInvoker.java:170)
2026-08-12T01:32:07.271205694Z  [auth-service]  at org.springframework.data.repository.core.support.RepositoryMethodInvoker.invoke(RepositoryMethodInvoker.java:158)
2026-08-12T01:32:07.271219531Z  [auth-service]  at org.springframework.data.repository.core.support.RepositoryComposition$RepositoryFragments.invoke(RepositoryComposition.java:516)
2026-08-12T01:32:07.271222011Z  [auth-service]  at org.springframework.data.repository.core.support.RepositoryComposition.invoke(RepositoryComposition.java:285)
2026-08-12T01:32:07.271224480Z  [auth-service]  at org.springframework.data.repository.core.support.RepositoryFactorySupport$ImplementationMethodExecutionInterceptor.invoke(RepositoryFactorySupport.java:628)
2026-08-12T01:32:07.271226969Z  [auth-service]  at org.springframework.aop.framework.ReflectiveMethodInvocation.proceed(ReflectiveMethodInvocation.java:184)
2026-08-12T01:32:07.271263955Z  [auth-service]  at org.springframework.dao.support.PersistenceExceptionTranslationInterceptor.invoke(PersistenceExceptionTranslationInterceptor.java:137)
2026-08-12T01:32:07.271492425Z  [auth-service]  at org.springframework.security.web.access.ExceptionTranslationFilter.doFilter(ExceptionTranslationFilter.java:126)
2026-08-12T01:32:07.271494928Z  [auth-service]  at org.springframework.security.web.access.ExceptionTranslationFilter.doFilter(ExceptionTranslationFilter.java:120)
2026-08-12T01:32:07.271967378Z  [auth-service]  [2m2026-08-12 10:32:07[0;39m [2m[http-nio-8081-exec-7][0;39m [31mERROR [traceId=6a7bcd177ec7dcbafab90613c89ef91a,spanId=fab90613c89ef91a,userId=NONE][0;39m [36mc.e.t.a.c.f.RequestLoggingFilter[0;39m [2m-[0;39m [HTTP] GET /api/user/1/following 500 - 99ms
2026-08-12T01:32:07.393860880Z  [auth-service]  [2m2026-08-12 10:32:07[0;39m [2m[http-nio-8081-exec-10][0;39m [31mERROR [traceId=6a7bcd1719fe4d3b8126248de7af6455,spanId=f0ee77ae28a97fa6,userId=1][0;39m [36mc.e.t.a.c.e.GlobalExceptionHandler[0;39m [2m-[0;39m [api-error] handleAllException
2026-08-12T01:32:07.393911671Z  [auth-service]  java.lang.NullPointerException: Cannot invoke "java.lang.Integer.intValue()" because "this.size" is null
2026-08-12T01:32:07.393956235Z  [auth-service]  at org.springframework.dao.support.PersistenceExceptionTranslationInterceptor.invoke(PersistenceExceptionTranslationInterceptor.java:137)
2026-08-12T01:32:07.394077336Z  [auth-service]  at org.springframework.dao.support.PersistenceExceptionTranslationInterceptor.invoke(PersistenceExceptionTranslationInterceptor.java:137)
2026-08-12T01:32:07.394313423Z  [auth-service]  at org.springframework.security.web.access.ExceptionTranslationFilter.doFilter(ExceptionTranslationFilter.java:126)
2026-08-12T01:32:07.394315670Z  [auth-service]  at org.springframework.security.web.access.ExceptionTranslationFilter.doFilter(ExceptionTranslationFilter.java:120)
2026-08-12T01:32:07.394766271Z  [auth-service]  [2m2026-08-12 10:32:07[0;39m [2m[http-nio-8081-exec-10][0;39m [31mERROR [traceId=6a7bcd1719fe4d3b8126248de7af6455,spanId=8126248de7af6455,userId=NONE][0;39m [36mc.e.t.a.c.f.RequestLoggingFilter[0;39m [2m-[0;39m [HTTP] GET /api/user/1/followers 500 - 67ms
2026-08-12T01:33:07.758010027Z  [auth-service]  [2m2026-08-12 10:33:07[0;39m [2m[http-nio-8081-exec-1][0;39m [33m WARN [traceId=6a7bcd53867ffb8faf3127e898919e0b,spanId=cd32b3bfd186cfb5,userId=1][0;39m [36morg.hibernate.orm.query[0;39m [2m-[0;39m HHH90003004: firstResult/maxResults specified with collection fetch; applying in memory
2026-08-12T01:34:56.840661456Z  [auth-service]  [2m2026-08-12 10:34:56[0;39m [2m[http-nio-8081-exec-3][0;39m [33m WARN [traceId=6a7bcdbd052a495e0b6b5c4c9b4c3a61,spanId=0b6b5c4c9b4c3a61,userId=NONE][0;39m [36mc.e.t.a.c.f.RequestLoggingFilter[0;39m [2m-[0;39m [HTTP-SLOW] POST /api/files/upload 200 - 3063ms
2026-08-12T01:34:56.840661456Z  [auth-service]  [2m2026-08-12 10:34:56[0;39m [2m[http-nio-8081-exec-3][0;39m [33m WARN [traceId=6a7bcdbd052a495e0b6b5c4c9b4c3a61,spanId=0b6b5c4c9b4c3a61,userId=NONE][0;39m [36mc.e.t.a.c.f.RequestLoggingFilter[0;39m [2m-[0;39m [HTTP-SLOW] POST /api/files/upload 200 - 3063ms
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
2026-08-12T01:35:08.603808172Z  [content-service]  at org.springframework.dao.support.PersistenceExceptionTranslationInterceptor.invoke(PersistenceExceptionTranslationInterceptor.java:160)
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
| `hikaricp_connections_active` | `{__name__=hikaricp_connections_active, application=auth-service, cluster=yogurtte-k3s-prod, container=auth-service, instance=10.42.1.51:8090, job=auth-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=auth-service-5999bb9f5c-lzp4p, pool=HikariPool-1, service=auth-service}` | 37 | 0 | 0 | 0 | **2026-08-12T01:31:00Z ~ 2026-08-12T01:40:00Z** |
| `hikaricp_connections_active` | `{__name__=hikaricp_connections_active, application=chat-service, cluster=yogurtte-k3s-prod, container=chat-service, instance=10.42.1.55:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-fdcc7c776-kh2dl, pool=HikariPool-1}` | 37 | 0 | 0 | 0 | **2026-08-12T01:31:00Z ~ 2026-08-12T01:40:00Z** |
| `hikaricp_connections_active` | `{__name__=hikaricp_connections_active, application=content-service, cluster=yogurtte-k3s-prod, container=content-service, instance=10.42.1.53:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-85f648fcff-sp24n, pool=HikariPool-1}` | 37 | 0 | 0 | 0 | **2026-08-12T01:31:00Z ~ 2026-08-12T01:40:00Z** |
| `hikaricp_connections_active` | `{__name__=hikaricp_connections_active, application=content-service, cluster=yogurtte-k3s-prod, container=content-service, instance=10.42.3.46:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-85f648fcff-v2pw9, pool=HikariPool-1}` | 37 | 0 | 0 | 0 | **2026-08-12T01:31:00Z ~ 2026-08-12T01:40:00Z** |
| `hikaricp_connections_pending` | `{__name__=hikaricp_connections_pending, application=auth-service, cluster=yogurtte-k3s-prod, container=auth-service, instance=10.42.1.51:8090, job=auth-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=auth-service-5999bb9f5c-lzp4p, pool=HikariPool-1, service=auth-service}` | 37 | 0 | 0 | 0 | **2026-08-12T01:31:00Z ~ 2026-08-12T01:40:00Z** |
| `hikaricp_connections_pending` | `{__name__=hikaricp_connections_pending, application=chat-service, cluster=yogurtte-k3s-prod, container=chat-service, instance=10.42.1.55:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-fdcc7c776-kh2dl, pool=HikariPool-1}` | 37 | 0 | 0 | 0 | **2026-08-12T01:31:00Z ~ 2026-08-12T01:40:00Z** |
| `hikaricp_connections_pending` | `{__name__=hikaricp_connections_pending, application=content-service, cluster=yogurtte-k3s-prod, container=content-service, instance=10.42.1.53:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-85f648fcff-sp24n, pool=HikariPool-1}` | 37 | 0 | 0 | 0 | **2026-08-12T01:31:00Z ~ 2026-08-12T01:40:00Z** |
| `hikaricp_connections_pending` | `{__name__=hikaricp_connections_pending, application=content-service, cluster=yogurtte-k3s-prod, container=content-service, instance=10.42.3.46:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-85f648fcff-v2pw9, pool=HikariPool-1}` | 37 | 0 | 0 | 0 | **2026-08-12T01:31:00Z ~ 2026-08-12T01:40:00Z** |
| `rate(jvm_gc_pause_seconds_sum[5m])` | `{action=end of major GC, application=chat-service, cause=Allocation Failure, cluster=yogurtte-k3s-prod, container=chat-service, gc=MarkSweepCompact, instance=10.42.1.55:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-fdcc7c776-kh2dl}` | 37 | 0 | 0 | 0 | **2026-08-12T01:31:00Z ~ 2026-08-12T01:40:00Z** |
| `rate(jvm_gc_pause_seconds_sum[5m])` | `{action=end of minor GC, application=auth-service, cause=G1 Evacuation Pause, cluster=yogurtte-k3s-prod, container=auth-service, gc=G1 Young Generation, instance=10.42.1.51:8090, job=auth-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=auth-service-5999bb9f5c-lzp4p, service=auth-service}` | 37 | 0 | 0.000 | 0 | **2026-08-12T01:31:00Z ~ 2026-08-12T01:32:30Z, 2026-08-12T01:36:45Z ~ 2026-08-12T01:40:00Z** |
| `rate(jvm_gc_pause_seconds_sum[5m])` | `{action=end of minor GC, application=auth-service, cause=Metadata GC Threshold, cluster=yogurtte-k3s-prod, container=auth-service, gc=G1 Young Generation, instance=10.42.1.51:8090, job=auth-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=auth-service-5999bb9f5c-lzp4p, service=auth-service}` | 37 | 0 | 0 | 0 | **2026-08-12T01:31:00Z ~ 2026-08-12T01:40:00Z** |
| `rate(jvm_gc_pause_seconds_sum[5m])` | `{action=end of minor GC, application=chat-service, cause=Allocation Failure, cluster=yogurtte-k3s-prod, container=chat-service, gc=Copy, instance=10.42.1.55:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-fdcc7c776-kh2dl}` | 37 | 0.000 | 0.000 | 0.000 | — |
| `rate(jvm_gc_pause_seconds_sum[5m])` | `{action=end of minor GC, application=chat-service, cause=GCLocker Initiated GC, cluster=yogurtte-k3s-prod, container=chat-service, gc=Copy, instance=10.42.1.55:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-fdcc7c776-kh2dl}` | 37 | 0 | 0 | 0 | **2026-08-12T01:31:00Z ~ 2026-08-12T01:40:00Z** |
| `rate(jvm_gc_pause_seconds_sum[5m])` | `{action=end of minor GC, application=content-service, cause=Allocation Failure, cluster=yogurtte-k3s-prod, container=content-service, gc=Copy, instance=10.42.1.53:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-85f648fcff-sp24n}` | 37 | 0 | 0.000 | 0 | **2026-08-12T01:31:00Z ~ 2026-08-12T01:32:30Z, 2026-08-12T01:36:45Z ~ 2026-08-12T01:40:00Z** |
| `rate(jvm_gc_pause_seconds_sum[5m])` | `{action=end of minor GC, application=content-service, cause=Allocation Failure, cluster=yogurtte-k3s-prod, container=content-service, gc=Copy, instance=10.42.3.46:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-85f648fcff-v2pw9}` | 37 | 0 | 0.000 | 0 | **2026-08-12T01:32:00Z ~ 2026-08-12T01:40:00Z** |
| `up` | `{__name__=up, app=node-exporter, cluster=yogurtte-k3s-prod, component=metrics, container=node-exporter, instance=ip-172-31-40-241, job=integrations/node_exporter, k8s_cluster_name=yogurtte-k3s-prod, namespace=monitoring, pod=grafana-k8s-monitoring-node-exporter-cph5l, source=kubernetes, workload=DaemonSet/grafana-k8s-monitoring-node-exporter}` | 37 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, app=node-exporter, cluster=yogurtte-k3s-prod, component=metrics, container=node-exporter, instance=ip-172-31-45-39, job=integrations/node_exporter, k8s_cluster_name=yogurtte-k3s-prod, namespace=monitoring, pod=grafana-k8s-monitoring-node-exporter-9kvqt, source=kubernetes, workload=DaemonSet/grafana-k8s-monitoring-node-exporter}` | 37 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, cluster=yogurtte-k3s-prod, container=auth-service, instance=10.42.1.51:8090, job=auth-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=auth-service-5999bb9f5c-lzp4p}` | 37 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, cluster=yogurtte-k3s-prod, container=chat-service, instance=10.42.1.55:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-fdcc7c776-kh2dl}` | 37 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, cluster=yogurtte-k3s-prod, container=content-service, instance=10.42.1.53:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-85f648fcff-sp24n}` | 37 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, cluster=yogurtte-k3s-prod, container=content-service, instance=10.42.3.46:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-85f648fcff-v2pw9}` | 37 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, cluster=yogurtte-k3s-prod, instance=10.42.1.248:8080, job=integrations/kubernetes/kube-state-metrics, k8s_cluster_name=yogurtte-k3s-prod, source=kubernetes}` | 37 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, cluster=yogurtte-k3s-prod, instance=62bd8b254df94616e43279f35eed72d3, job=integrations/cloudwatch, k8s_cluster_name=yogurtte-k3s-prod}` | 37 | 1 | 1 | 1 | — |
| `mongodb_up` | `{__name__=mongodb_up, cluster=yogurtte-k3s-prod, instance=infra-server, job=mongodb, k8s_cluster_name=yogurtte-k3s-prod}` | 37 | 1 | 1 | 1 | — |
| `kafka_brokers` | `{__name__=kafka_brokers, cluster=yogurtte-k3s-prod, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod}` | 37 | 1 | 1 | 1 | — |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=chat-service-fcm-tokens, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=0, topic=user.fcm-tokens}` | 37 | 0 | 0 | 0 | **2026-08-12T01:31:00Z ~ 2026-08-12T01:40:00Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=chat-service-fcm-tokens, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=1, topic=user.fcm-tokens}` | 37 | 0 | 0 | 0 | **2026-08-12T01:31:00Z ~ 2026-08-12T01:40:00Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=chat-service-fcm-tokens, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=2, topic=user.fcm-tokens}` | 37 | 0 | 0 | 0 | **2026-08-12T01:31:00Z ~ 2026-08-12T01:40:00Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=chat-service-notification-settings, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=0, topic=user.notification-settings}` | 37 | 0 | 0 | 0 | **2026-08-12T01:31:00Z ~ 2026-08-12T01:40:00Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=chat-service-notification-settings, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=1, topic=user.notification-settings}` | 37 | 0 | 0 | 0 | **2026-08-12T01:31:00Z ~ 2026-08-12T01:40:00Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=chat-service-notification-settings, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=2, topic=user.notification-settings}` | 37 | 0 | 0 | 0 | **2026-08-12T01:31:00Z ~ 2026-08-12T01:40:00Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=db-writer, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=0, topic=chat.messages}` | 37 | 0 | 0 | 0 | **2026-08-12T01:31:00Z ~ 2026-08-12T01:40:00Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=db-writer, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=1, topic=chat.messages}` | 37 | 0 | 0 | 0 | **2026-08-12T01:31:00Z ~ 2026-08-12T01:40:00Z** |
| `websocket_active_users` | `{__name__=websocket_active_users, application=chat-service, cluster=yogurtte-k3s-prod, container=chat-service, instance=10.42.1.55:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-fdcc7c776-kh2dl}` | 37 | 0 | 0 | 0 | **2026-08-12T01:31:00Z ~ 2026-08-12T01:40:00Z** |

값이 0이던 구간은 굵게 표시했다 — 프로세스가 사라져 시계열이 꺾인 것이 유일한 신호인 장애가 있다.

