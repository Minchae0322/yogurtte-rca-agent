# RCA Report — `scan-1785897000`

| 항목 | 값 |
|---|---|
| 모드 | rca |
| 질문 | 최근 1시간 안에 피드 작성이 실패했다는 제보가 있다. 원인을 조사해줘 |
| 시각 | 2026-08-05T02:48:00.169453700Z |
| provider | claude-cli |
| model | `claude-opus-5` · turns 1 |
| prompt | `.\prompts\system-prompt.md` |
| tokens | in 69162 (cacheRead 23,447 · cacheCreate 45,713) / out 8936 · cost $0.6923 |
| elapsed | total 152523ms (tempo 681 · loki 266 · mimir 777 · assemble 152 · llm 140979) |

## 탐색 (Triage)

| 항목 | 값 |
|---|---|
| 시간창 해석 | 상대 표현 '최근 1시간' |
| 시간창 확신도 | EXACT |
| 스윕 창 | 2026-08-05T01:47:28.045229700Z ~ 2026-08-05T02:47:28.045229700Z |
| 좁힌 창 | 2026-08-05T02:30:00Z ~ 2026-08-05T02:45:00Z |
| 대상 | content-service, auth-service |
| traceId | 6a72a247a643690f6dd80b177c87c2a9 |
| 트레이스 후보 | 3건 |
| 장애 후보 | 8건 · 선택 INC-6, INC-7, INC-8 |
| 계획 파싱 | 성공 |
| 스윕 컨텍스트 | **후보만 — 원본 제외 (B)** |
| prompt | `.\prompts\triage-prompt.md` |
| tokens | in 32805 / out 1520 · cost $0.1705 |
| chars | 컨텍스트 2,953 + 프롬프트 1,399 = **4,352** |
| elapsed | survey 1688ms · llm 30358ms |

**선정 이유**: 질문이 지목한 POST /feeds 실패 트레이스와 그와 같은 5분 버킷의 content/auth 에러 로그가 한 사건의 서로 다른 지문으로 보이므로 셋을 합집합으로 조사한다.

**근거**

- INC-8: content-service http post /feeds 162ms error 채널 트레이스 (traceId 6a72a247a643690f6dd80b177c87c2a9, 02:39:03.229865Z) — 질문의 '피드 작성' 엔드포인트와 정확히 일치
- INC-7: content-service ERROR/WARN 4건 (02:35:00Z~02:40:00Z) — INC-8 트레이스와 같은 5분 버킷, 실패 응답의 로그측 지문일 가능성
- INC-6: auth-service ERROR/WARN 1건 (02:35:00Z~02:40:00Z) — 피드 작성은 인증 경유 경로이므로 같은 시각 상류 후보로 함께 포함
- 162ms는 지연이 아닌 즉시 실패에 가까움 — 타임아웃이 아니라 검증/의존성 오류 계열로 좁혀짐
- min_over_time(up[5m]) 0건, mongodb_up 0건, kafka_brokers 0건 — 인프라·DB·브로커 가용성 문제는 이 창에서 배제됨

**스윕이 찾은 트레이스** (고른 것은 6a72a247a643690f6dd80b177c87c2a9)

| traceId | 채널 | root service | root span | ms |
|---|---|---|---|---:|
| `6a72a247a643690f6dd80b177c87c2a9` ←선택 | error | content-service | http post /feeds | 162 |
| `6a729b55f741850507e3ddfd50cc4d65` | error | content-service | http post /feeds/{feedId}/comments | 119 |
| `6a729bd0cf63e6ea421b273667e35a3f` | slow | auth-service | http get /user/{userid}/following | 3231 |

**장애 후보** (코드가 신호를 묶은 것 · 창은 여기서 계산됨)

## INC-1  content-service  |  ERROR/WARN
- 구간: 2026-08-05T02:05:00Z ~ 2026-08-05T02:10:00Z  (LOKI · 집계 해상도만큼 흐림)
- ERROR/WARN 4건 (2026-08-05T02:05:00Z ~ 2026-08-05T02:10:00Z)
- 같은 시각의 다른 후보: INC-2, INC-3, INC-4  (인과 여부는 판단하지 않았다)

## INC-2  kafka  |  kafka_consumergroup_lag
- 구간: 2026-08-05T02:07:28Z ~ 2026-08-05T02:17:28Z  (MIMIR · 집계 해상도만큼 흐림)
- kafka_consumergroup_lag{consumergroup=notification-processors, partition=3, topic=user.notifications} 0 → 1
- kafka_consumergroup_lag{consumergroup=notification-processors, partition=3, topic=user.notifications} 1 → 0
- 같은 시각의 다른 후보: INC-1, INC-3, INC-4, INC-5  (인과 여부는 판단하지 않았다)

## INC-3  content-service  |  http post /feeds/{feedId}/comments
- 구간: 2026-08-05T02:09:25.627296Z ~ 2026-08-05T02:09:25.746296Z  (TEMPO · 시각 정확)
- content-service http post /feeds/{feedId}/comments 119ms (error 채널)
- traceId: 6a729b55f741850507e3ddfd50cc4d65
- 같은 시각의 다른 후보: INC-1, INC-2  (인과 여부는 판단하지 않았다)

## INC-4  auth-service  |  ERROR/WARN
- 구간: 2026-08-05T02:10:00Z ~ 2026-08-05T02:15:00Z  (LOKI · 집계 해상도만큼 흐림)
- ERROR/WARN 7건 (2026-08-05T02:10:00Z ~ 2026-08-05T02:15:00Z)
- 같은 시각의 다른 후보: INC-1, INC-2, INC-5  (인과 여부는 판단하지 않았다)

## INC-5  auth-service  |  http get /user/{userid}/following
- 구간: 2026-08-05T02:11:28.128454Z ~ 2026-08-05T02:11:31.359454Z  (TEMPO · 시각 정확)
- auth-service http get /user/{userid}/following 3,231ms (slow 채널)
- traceId: 6a729bd0cf63e6ea421b273667e35a3f
- 같은 시각의 다른 후보: INC-2, INC-4  (인과 여부는 판단하지 않았다)

## INC-6  auth-service  |  ERROR/WARN
- 구간: 2026-08-05T02:35:00Z ~ 2026-08-05T02:40:00Z  (LOKI · 집계 해상도만큼 흐림)
- ERROR/WARN 1건 (2026-08-05T02:35:00Z ~ 2026-08-05T02:40:00Z)
- 같은 시각의 다른 후보: INC-7, INC-8  (인과 여부는 판단하지 않았다)

## INC-7  content-service  |  ERROR/WARN
- 구간: 2026-08-05T02:35:00Z ~ 2026-08-05T02:40:00Z  (LOKI · 집계 해상도만큼 흐림)
- ERROR/WARN 4건 (2026-08-05T02:35:00Z ~ 2026-08-05T02:40:00Z)
- 같은 시각의 다른 후보: INC-6, INC-8  (인과 여부는 판단하지 않았다)

## INC-8  content-service  |  http post /feeds
- 구간: 2026-08-05T02:39:03.229865Z ~ 2026-08-05T02:39:03.391865Z  (TEMPO · 시각 정확)
- content-service http post /feeds 162ms (error 채널)
- traceId: 6a72a247a643690f6dd80b177c87c2a9
- 같은 시각의 다른 후보: INC-6, INC-7  (인과 여부는 판단하지 않았다)

**기각한 후보**

- INC-1 — 02:05~02:10 content-service ERROR/WARN은 증상 시각(02:39)과 30분 이상 떨어진 별개 구간이다.
- INC-2 — kafka consumergroup lag가 0→1→1→0으로 즉시 회복된 단발 흔들림이고, notification-processors/user.notifications는 피드 작성 경로가 아니다.
- INC-3 — POST /feeds/{feedId}/comments(댓글 작성) 에러로, 피드 작성 실패와 엔드포인트가 다르고 시각도 02:09로 어긋난다.
- INC-4 — 02:10~02:15 auth-service ERROR/WARN 7건은 건수는 많지만 증상 시각대와 맞지 않는 앞선 구간이다.
- INC-5 — GET /user/{userid}/following 3,231ms 지연은 조회 경로의 느려짐이고 작성 실패 증상과 지문이 다르다.

**보정 기록**

- 창을 후보 [INC-6, INC-7, INC-8] 의 신호 시각에서 계산했다 (2026-08-05T02:30:00Z ~ 2026-08-05T02:45:00Z)

## 수집 범위 (Coverage)

- **window**: 2026-08-05T02:30:00Z ~ 2026-08-05T02:45:00Z (900s)
- **trace**: 37,923B / 34 spans
- **창 안 후보 트레이스**: 10건 / 37,923B
- **logs**: errwarn=40,627B · traceId=5,694B
- **metrics**: 8 수집 / 110,758B, 누락 [sum(rate(http_server_requests_seconds_count{application="content-service", status="401"}[1m]))]
- **context**: 70,627 chars (+ 시스템 프롬프트 805 chars)

### 토큰 축 (개선 지표)

| 단계 | 총 in | chars | 컨텍스트 토큰 |
|---|---:|---:|---:|
| 탐색 | 32,805 | 4,352 | 3,381 |
| 분석 | 69,162 | 71,432 | 39,738 |
| **합계** | **101,967** | | **43,119** |

- **overheadTokens 29,424 tok — 이 회차에 실측**(1자 프롬프트 프로브, 같은 명령·모델·샌드박스).
  호출마다 한 번씩 얹히므로 단계별로 뺀다. **다른 날 상수를 쓰지 않았다 = `█ 실측`.**
- contextTokens (count_tokens API): 측정 안 됨 — 구독 CLI 경로엔 API 키가 없다
- 비용은 **API 환산 추정치**다. 구독 계정으로 호출하므로 실제 청구액이 아니다.

## 수집 실패/누락

- Metric 'sum(rate(http_server_requests_seconds_count{application="content-service", status="401"}[1m]))' returned no series in this window; skipped.

---

## 1. 원인 후보 랭킹 (최대 3개)

1. **한 요청 안에서 같은 해시태그가 두 번 처리되어 `tb_feed_hashtags` 유니크 제약(`uk_feed_hashtag`)을 위반 → 트랜잭션 전체 롤백 → POST /api/feeds 500** (앱 레벨 해시태그 중복 제거 부재)
2. **중복 삽입이 `ExpGrantService` 경로의 Hibernate auto-flush 시점에 터져 `GlobalExceptionHandler`가 4xx로 변환하지 못하고 500으로 응답** (1의 표면화/처리 방식 문제, 독립 원인은 아님)
3. **auth-service 파일 업로드 지연(2844ms)** — 관련성 낮음, 아래 반증 참조

---

## 2. 후보별 근거

### 후보 1 — 요청 내 해시태그 중복으로 인한 유니크 제약 위반

**근거** (traceId `6a72a247a643690f6dd80b177c87c2a9`, pod `content-service-6995bb7d94-h2f6n`, thread `http-nio-8082-exec-5`, userId=1)

같은 커넥션 span(`connection`, HikariPool-1, 141.126ms) 안에서 시간순 JDBC 흐름:

| 시각(02:39:03.xxx) | span | 내용 |
|---|---|---|
| .270 | query | `insert into tb_feed ...` → row-affected 1 |
| .273 | generated-keys | **feed_id = 163** |
| .275/.282 | query | `insert into tb_feed_attachment_file` ×2 → keys 255, 256 |
| .296 | query | `select ... from tb_hashtags h1_0 where h1_0.name=?` |
| .299 | result-set | **row-count 0** (해당 이름 없음) |
| .309 | query | `insert into tb_hashtags (...)` → row-affected 1 |
| .313 | generated-keys | **hashtag id = 178** |
| .326 | query | `insert into tb_feed_hashtags (...)` → row-affected 1 |
| .328 | generated-keys | id 232 (**1회차 매핑 성공**) |
| .337 | query | `update tb_hashtags set updated_at=?,usage_count=? where id=?` |
| .340 | query | `select ... from tb_hashtags h1_0 where h1_0.name=?` (동일 쿼리 재실행) |
| .342 | result-set | **row-count 1** (이번엔 존재 — 방금 만든 그 행) |
| .352 | query | `insert into tb_feed_hashtags (...)` → **ERROR `Duplicate entry '163-178' for key 'tb_feed_hashtags.uk_feed_hashtag'`**, STATUS_CODE_ERROR |

핵심은 **같은 name 조회가 두 번 실행되고 첫 번째는 0건, 두 번째는 1건**이라는 점, 그리고 충돌 키가 `'163-178'` — 1회차에 만든 feed_id 163과 hashtag id 178의 조합 그대로라는 점이다. 즉 두 번째 반복이 조회한 해시태그는 첫 번째 반복이 방금 생성한 것과 동일하며, 같은 (feed_id, hashtag_id) 쌍을 다시 insert했다. **동일 요청 payload에 같은 해시태그 이름이 중복 포함되었고, 저장 로직이 이를 dedup하지 않는다**는 결론이 데이터에서 직접 따라 나온다.

로그 원문:
- `WARN ... o.h.e.jdbc.spi.SqlExceptionHelper - SQL Error: 1062, SQLState: 23000` (11:39:03.361)
- `ERROR ... Duplicate entry '163-178' for key 'tb_feed_hashtags.uk_feed_hashtag'` (11:39:03.361)
- `org.springframework.dao.DataIntegrityViolationException: could not execute statement [...]; constraint [tb_feed_hashtags.uk_feed_hashtag]`
- `ERROR ... RequestLoggingFilter - [HTTP] POST /api/feeds 500 - 161ms`

앱 코드 프레임: `FeedService.createFeed(FeedService.java:223)` → `ExpGrantService.grantFeedCreate:43` → `grant:151` → `grantWithCap:159` → `isDuplicate:225`.

**확신도: 높음**

**대기·지연 구간 판정**
- JDBC 커넥션 획득: span 시작 .249605 → `acquired` 이벤트 .252115 = **2.51ms**. 상한(`connectionTimeout` 설정값)은 수집 데이터에 없어 **수치 대조는 판정 불가**이나, `acquired` 이벤트가 존재하므로 **만료되지 않음(획득 성공)**. 보조 근거로 `hikaricp_connections_pending`이 4개 인스턴스 전 구간 0.
- 실패한 insert 자체: 9.121ms 만에 DB가 1062로 즉시 거절 — 대기 만료가 아니라 제약 위반.
- **해당 작업의 최종 상태: 실패 → 롤백 → 폐기.** 커넥션 span에 `rollback` 이벤트가 .379212에 기록되어 있고, 그 후 500 응답(.390). 같은 traceId·같은 payload의 재시도 트레이스나 로그는 수집 범위 내에 없으므로 **재시도 없음(폐기)**.

**반증 데이터**
- 없음. 인프라 측 반증은 오히려 이 후보를 강화한다 — `up` 전 시리즈 1, `hikaricp_connections_active/pending` 전 구간 0, GC pause rate 최대 2.5e-4초/초, kafka_brokers 1, kafka lag 전 파티션 0(또는 -1=미할당), mongodb_up 1, redis 호출 최대 1.7ms. 자원·의존성 포화나 장애 신호가 전무하므로 순수 애플리케이션 로직 결함이라는 해석과 배치되지 않는다.

---

### 후보 2 — 예외가 exp 적립 경로의 auto-flush에서 터져 500으로 새어나감

**근거**
스택트레이스에서 `DataIntegrityViolationException`이 던져진 지점은 해시태그 저장 코드가 아니라 `com.example.toycontent.app.reward.exp.service.ExpGrantService.isDuplicate(ExpGrantService.java:225)`다. 그 아래로 `grantWithCap:159` → `grant:151` → `grantFeedCreate:43` → `FeedService.createFeed:223`. `isDuplicate`가 조회를 수행할 때 Hibernate가 보류 중이던 `tb_feed_hashtags` insert를 flush했고, 그 시점에 제약 위반이 표면화됐다 — 트레이스에서도 실패 insert(.352)가 정상 매핑 insert(.326)보다 26ms 늦게, 별개 시점에 실행됐다.

결과적으로 `GlobalExceptionHandler`는 이를 도메인 예외로 인식하지 못하고 포괄 처리했다: `WARN ... c.e.t.a.c.e.GlobalExceptionHandler - [api-error] handleAllException` (11:39:03.382). `handleAllException`이라는 핸들러 이름 자체가 특정 매핑이 아닌 최종 fallback 경로임을 보여주며, 응답은 `outcome=SERVER_ERROR, status=500`이 됐다.

이는 후보 1과 별개의 근본 원인이 아니라 **피해를 키운 2차 요인**이다. 중복 태그라는 사용자 입력 수준의 문제가 (a) 피드·첨부·해시태그를 포함한 트랜잭션 전체 롤백, (b) 4xx가 아닌 500 응답으로 증폭됐다.

**확신도: 중간** (flush 시점 해석은 스택 프레임과 span 순서에서 유도한 것이며, `ExpGrantService` 소스나 트랜잭션 전파 설정은 수집되지 않았다)

**대기·지연 구간 판정: 해당 없음** — 이 후보에 결부된 대기 구간이 없다. 예외 표면화부터 롤백(.379), 500 응답(.390)까지 지연 없이 진행됐다.

**반증 데이터**
- `security filterchain before/after` span이 position 12/12까지 정상 통과했고 `exception:"none"`, JwtAuthenticationFilter도 정상 — 인증·필터 단계에서 끊긴 게 아니라 컨트롤러 안까지 들어가 실패했음을 보여준다. 이는 후보 2와 배치되지 않고 일치한다.
- 직접적 반증은 없음.

---

### 후보 3 — auth-service 파일 업로드 지연 (2844ms)

**근거**
`WARN [traceId=6a72a23802b161f356c27f7918f0d37b] c.e.t.a.c.f.RequestLoggingFilter - [HTTP-SLOW] POST /api/files/upload 200 - 2844ms` (11:38:50). 창 내 유일한 다른 이상 신호이므로 후보로 올린다.

**확신도: 낮음**

**대기·지연 구간 판정: 2844ms 소요, 상한(업로드 타임아웃 설정값)은 수집 데이터에 없어 만료 여부 판정 불가. 다만 최종 상태는 HTTP 200 — 성공적으로 완료.**

**반증 데이터** (이 후보를 사실상 기각한다)
- traceId가 완전히 다르고(`6a72a238...` vs `6a72a247...`), 시각도 13초 앞선다. 두 트레이스는 연결되지 않는다.
- 호출 그래프에 auth-service → content-service 엣지가 없다. 관측된 엣지는 `chat→redis`, `content→redis`, `content→mysql/content` 뿐이다.
- 상태 코드 200 — 실패하지 않았다.
- 실패 트레이스의 총 소요는 162ms이며 그중 141ms가 JDBC 커넥션 span이다. 외부 대기로 인한 지연이 원인일 여지가 없다.

---

### 데이터 부족 항목

- **피해 규모(몇 명/몇 건이 실패했는가)**: 판정 불가. 실패 트레이스는 1건(userId=1)뿐이고, 수집 실패 목록에 `http_server_requests_seconds_count{application="content-service", status="401"}` 무시리즈가 있으며 status="500" 계열 카운터는 아예 조회되지 않았다. "제보가 있다"는 진술 대비 실제 실패율을 알 수 없다.
- **HikariCP 타임아웃 설정값**, **`ExpGrantService`·해시태그 저장 소스 코드**, **실패 요청의 원본 payload(해시태그 배열)**: 미수집. 후보 1의 메커니즘은 관측만으로 확정되나, "왜 중복 태그가 들어왔는가"(클라이언트 중복 전송 vs 서버 파싱 중복)는 구분 불가.
- 위 공백을 반영해, 후보 1의 **메커니즘**은 확신도 높음이지만 **영향 범위**는 판단을 유보한다.

---

## 3. 권장 다음 조치

### 이미 발생한 피해: 복구 가능한가

**DB 정합성 측면 — 복구할 것이 없다.** `connection` span에 `rollback` 이벤트(.379212)가 명시되어 있고, feed 163 / attachment 255·256 / hashtag 178 / feed_hashtag 232 / usage_count 갱신은 모두 같은 커넥션의 단일 트랜잭션 안에서 실행됐다. 즉 **고아 레코드나 부분 커밋은 발생하지 않았다.** 소비된 AUTO_INCREMENT 번호(163, 178, 232, 255, 256)에 결번이 생기지만 기능적 영향은 없다.

**사용자 데이터 측면 — 자동 복구 불가.** 피드 본문·첨부는 커밋되지 않았고 관측 데이터에 원본 payload가 없으므로 서버가 재구성해 재처리할 수 없다. 사용자가 재작성·재전송해야 한다. 다만 **동일 입력으로 재시도하면 결정적으로 같은 실패가 재현된다** (중복 태그가 그대로면 다시 1062). 따라서 수정 배포 전까지의 실무 우회는 "중복 해시태그를 제거하고 재작성"이다. 영향받은 사용자 목록은 위 데이터 부족 항목대로 산출 불가 — Loki에서 `POST /api/feeds 500` 전체를 최근 1시간으로 넓혀 userId를 뽑아야 통지 대상을 확정할 수 있다.

### 재발 방지

1. **해시태그 목록 dedup**(`FeedService.createFeed` 진입 지점, 정규화 후 distinct). 이것이 근본 수정이며 후보 1을 직접 제거한다.
2. **매핑 insert를 멱등화**: `tb_feed_hashtags` 저장을 `INSERT ... ON DUPLICATE KEY UPDATE`/`INSERT IGNORE` 또는 저장 전 존재 확인으로 처리. 유니크 제약 `uk_feed_hashtag`는 정상적인 안전장치이므로 **제약을 제거하는 방향은 금물** — 제약이 데이터 오염을 막아준 것이다.
3. **예외 매핑 보강**: `GlobalExceptionHandler`가 `DataIntegrityViolationException`을 `handleAllException` fallback이 아닌 명시적 4xx(409/400)로 매핑. 사용자 입력 문제가 500으로 나가는 것을 막는다.
4. **트랜잭션 경계 재검토**: 보상 성격의 exp 적립(`grantFeedCreate`)이 피드 생성과 동일 트랜잭션에 묶여 있어, 부수 로직의 flush가 본 기능 전체를 롤백시킨다. 적립을 별도 트랜잭션/이벤트로 분리하면 실패 폭발 반경이 줄어든다.
5. **관측 보강**: `http_server_requests_seconds_count{status="5xx"}` 알럿을 content-service에 추가. 이번에 5xx 카운터가 없어 영향 범위 산정이 막혔다.

### 복구 확인

1. **재현 테스트**: 동일 해시태그 이름을 2회 이상 포함한 payload로 POST /api/feeds → 200과 함께 `tb_feed_hashtags`에 (feed_id, hashtag_id) 쌍 1건만 생성되는지 확인.
2. **로그**: `Duplicate entry`, `SQL Error: 1062, SQLState: 23000`, `[api-error] handleAllException`이 content-service 스트림에서 0건인지 확인.
3. **트레이스**: 새 POST /feeds 트레이스에서 `connection` span의 이벤트가 `acquired` → `commit`(현재는 `rollback`)이고, 루트 span이 `outcome=SUCCESS, status=200`인지 확인.
4. **메트릭**: content-service 5xx rate 0, `hikaricp_connections_pending` 0 유지(현재도 0이므로 회귀 감시용 기준선).
5. **잔여 데이터 점검**: `SELECT feed_id, hashtag_id, COUNT(*) FROM tb_feed_hashtags GROUP BY 1,2 HAVING COUNT(*)>1` — 유니크 제약이 있으므로 0건이어야 하며, 0건 확인으로 "부분 커밋 없음" 결론을 실측 검증한다.

---

건너뛴 것: 5xx 카운터 미수집이라 영향 범위는 미산정 — Loki를 1시간으로 넓혀 `POST /api/feeds 500`의 userId를 뽑으면 확정된다.

---

## 관측 증거 (Evidence)

> 이 조사가 실제로 본 값이다. 원본 응답 전체는 `reports/raw/scan-1785897000-*.json`에 있다.

### 호출 그래프 (트레이스에서 추출)

```
chat-service --db--> redis  1회  최대 0.5ms  [INFO]
content-service --db--> redis  2회  최대 1.7ms  [INFO]
content-service --jdbc--> mysql/content (HikariPool-1)  19회  최대 141.1ms
    error: Duplicate entry '163-178' for key 'tb_feed_hashtags.uk_feed_hashtag'
    events: acquired, rollback
```

### span (duration 상위 15 / 전체 34)

| ms | service | span | 시작 |
|---:|---|---|---|
| 162.23 | content-service | `http post /feeds` | 2026-08-05T02:39:03.229865Z |
| 160.52 | content-service | `secured request` | 2026-08-05T02:39:03.230290Z |
| 141.13 | content-service | `connection` | 2026-08-05T02:39:03.249605Z |
| 15.33 | chat-service | `secured request` | 2026-08-05T02:43:01.878790Z |
| 9.12 | content-service | `query` | 2026-08-05T02:39:03.352091Z |
| 3.89 | content-service | `query` | 2026-08-05T02:39:03.309179Z |
| 3.24 | content-service | `query` | 2026-08-05T02:39:03.282547Z |
| 2.85 | chat-service | `security filterchain before` | 2026-08-05T02:43:01.873184Z |
| 2.73 | content-service | `query` | 2026-08-05T02:39:03.270176Z |
| 2.57 | content-service | `query` | 2026-08-05T02:39:03.337028Z |
| 2.19 | content-service | `query` | 2026-08-05T02:39:03.275586Z |
| 1.98 | content-service | `query` | 2026-08-05T02:39:03.296995Z |
| 1.96 | content-service | `query` | 2026-08-05T02:39:03.326084Z |
| 1.85 | content-service | `query` | 2026-08-05T02:39:03.254890Z |
| 1.79 | content-service | `query` | 2026-08-05T02:39:03.340663Z |

### 로그 원문 (60 / 전체 252줄)

전체가 상한을 넘어 ERROR/WARN 줄을 우선 발췌했다. 나머지는 원본 파일에 있다.

```
2026-08-05T02:38:50.911959406Z  [auth-service]  [2m2026-08-05 11:38:50[0;39m [2m[http-nio-8081-exec-5][0;39m [33m WARN [traceId=6a72a23802b161f356c27f7918f0d37b,spanId=56c27f7918f0d37b,userId=NONE][0;39m [36mc.e.t.a.c.f.RequestLoggingFilter[0;39m [2m-[0;39m [HTTP-SLOW] POST /api/files/upload 200 - 2844ms
2026-08-05T02:39:03.361523899Z  [content-service]  2026-08-05 11:39:03.361 [http-nio-8082-exec-5]  WARN [traceId=6a72a247a643690f6dd80b177c87c2a9,spanId=fcd5c08f00e49296,userId=1] o.h.e.jdbc.spi.SqlExceptionHelper - SQL Error: 1062, SQLState: 23000
2026-08-05T02:39:03.361523899Z  [content-service]  2026-08-05 11:39:03.361 [http-nio-8082-exec-5]  WARN [traceId=6a72a247a643690f6dd80b177c87c2a9,spanId=fcd5c08f00e49296,userId=1] o.h.e.jdbc.spi.SqlExceptionHelper - SQL Error: 1062, SQLState: 23000
2026-08-05T02:39:03.361699676Z  [content-service]  2026-08-05 11:39:03.361 [http-nio-8082-exec-5] ERROR [traceId=6a72a247a643690f6dd80b177c87c2a9,spanId=fcd5c08f00e49296,userId=1] o.h.e.jdbc.spi.SqlExceptionHelper - Duplicate entry '163-178' for key 'tb_feed_hashtags.uk_feed_hashtag'
2026-08-05T02:39:03.361699676Z  [content-service]  2026-08-05 11:39:03.361 [http-nio-8082-exec-5] ERROR [traceId=6a72a247a643690f6dd80b177c87c2a9,spanId=fcd5c08f00e49296,userId=1] o.h.e.jdbc.spi.SqlExceptionHelper - Duplicate entry '163-178' for key 'tb_feed_hashtags.uk_feed_hashtag'
2026-08-05T02:39:03.388955354Z  [content-service]  2026-08-05 11:39:03.382 [http-nio-8082-exec-5]  WARN [traceId=6a72a247a643690f6dd80b177c87c2a9,spanId=fcd5c08f00e49296,userId=1] c.e.t.a.c.e.GlobalExceptionHandler - [api-error] handleAllException
2026-08-05T02:39:03.388955354Z  [content-service]  2026-08-05 11:39:03.382 [http-nio-8082-exec-5]  WARN [traceId=6a72a247a643690f6dd80b177c87c2a9,spanId=fcd5c08f00e49296,userId=1] c.e.t.a.c.e.GlobalExceptionHandler - [api-error] handleAllException
2026-08-05T02:39:03.388978296Z  [content-service]  org.springframework.dao.DataIntegrityViolationException: could not execute statement [Duplicate entry '163-178' for key 'tb_feed_hashtags.uk_feed_hashtag'] [insert into tb_feed_hashtags (created_at,feed_id,hashtag_id,updated_at) values (?,?,?,?)]; SQL [insert into tb_feed_hashtags (created_at,feed_id,hashtag_id,updated_at) values (?,?,?,?)]; constraint [tb_feed_hashtags.uk_feed_hashtag]
2026-08-05T02:39:03.388982190Z  [content-service]  at org.springframework.orm.jpa.vendor.HibernateJpaDialect.convertHibernateAccessException(HibernateJpaDialect.java:290)
2026-08-05T02:39:03.388985050Z  [content-service]  at org.springframework.orm.jpa.vendor.HibernateJpaDialect.translateExceptionIfPossible(HibernateJpaDialect.java:241)
2026-08-05T02:39:03.388999260Z  [content-service]  at org.springframework.orm.jpa.AbstractEntityManagerFactoryBean.translateExceptionIfPossible(AbstractEntityManagerFactoryBean.java:560)
2026-08-05T02:39:03.389004133Z  [content-service]  at org.springframework.dao.support.ChainedPersistenceExceptionTranslator.translateExceptionIfPossible(ChainedPersistenceExceptionTranslator.java:61)
2026-08-05T02:39:03.389008634Z  [content-service]  at org.springframework.dao.support.DataAccessUtils.translateIfNecessary(DataAccessUtils.java:343)
2026-08-05T02:39:03.389012390Z  [content-service]  at org.springframework.dao.support.PersistenceExceptionTranslationInterceptor.invoke(PersistenceExceptionTranslationInterceptor.java:160)
2026-08-05T02:39:03.389016047Z  [content-service]  at org.springframework.aop.framework.ReflectiveMethodInvocation.proceed(ReflectiveMethodInvocation.java:184)
2026-08-05T02:39:03.389021079Z  [content-service]  at org.springframework.data.jpa.repository.support.CrudMethodMetadataPostProcessor$CrudMethodMetadataPopulatingMethodInterceptor.invoke(CrudMethodMetadataPostProcessor.java:136)
2026-08-05T02:39:03.389024778Z  [content-service]  at org.springframework.aop.framework.ReflectiveMethodInvocation.proceed(ReflectiveMethodInvocation.java:184)
2026-08-05T02:39:03.389029323Z  [content-service]  at org.springframework.aop.framework.JdkDynamicAopProxy.invoke(JdkDynamicAopProxy.java:223)
2026-08-05T02:39:03.389048704Z  [content-service]  at com.example.toycontent.app.reward.exp.service.ExpGrantService.isDuplicate(ExpGrantService.java:225)
2026-08-05T02:39:03.389050664Z  [content-service]  at com.example.toycontent.app.reward.exp.service.ExpGrantService.grantWithCap(ExpGrantService.java:159)
2026-08-05T02:39:03.389052758Z  [content-service]  at com.example.toycontent.app.reward.exp.service.ExpGrantService.grant(ExpGrantService.java:151)
2026-08-05T02:39:03.389054714Z  [content-service]  at com.example.toycontent.app.reward.exp.service.ExpGrantService.grantFeedCreate(ExpGrantService.java:43)
2026-08-05T02:39:03.389065524Z  [content-service]  at org.springframework.aop.support.AopUtils.invokeJoinpointUsingReflection(AopUtils.java:359)
2026-08-05T02:39:03.389067680Z  [content-service]  at org.springframework.aop.framework.ReflectiveMethodInvocation.invokeJoinpoint(ReflectiveMethodInvocation.java:196)
2026-08-05T02:39:03.389069832Z  [content-service]  at org.springframework.aop.framework.ReflectiveMethodInvocation.proceed(ReflectiveMethodInvocation.java:163)
2026-08-05T02:39:03.389071836Z  [content-service]  at org.springframework.transaction.interceptor.TransactionAspectSupport.invokeWithinTransaction(TransactionAspectSupport.java:380)
2026-08-05T02:39:03.389073881Z  [content-service]  at org.springframework.transaction.interceptor.TransactionInterceptor.invoke(TransactionInterceptor.java:119)
2026-08-05T02:39:03.389075777Z  [content-service]  at org.springframework.aop.framework.ReflectiveMethodInvocation.proceed(ReflectiveMethodInvocation.java:184)
2026-08-05T02:39:03.389077690Z  [content-service]  at org.springframework.aop.framework.CglibAopProxy$DynamicAdvisedInterceptor.intercept(CglibAopProxy.java:727)
2026-08-05T02:39:03.389081693Z  [content-service]  at com.example.toycontent.app.feed.service.FeedService.createFeed(FeedService.java:223)
2026-08-05T02:39:03.389093308Z  [content-service]  at org.springframework.aop.support.AopUtils.invokeJoinpointUsingReflection(AopUtils.java:359)
2026-08-05T02:39:03.389100616Z  [content-service]  at org.springframework.aop.framework.ReflectiveMethodInvocation.invokeJoinpoint(ReflectiveMethodInvocation.java:196)
2026-08-05T02:39:03.389103272Z  [content-service]  at org.springframework.aop.framework.ReflectiveMethodInvocation.proceed(ReflectiveMethodInvocation.java:163)
2026-08-05T02:39:03.389105761Z  [content-service]  at org.springframework.transaction.interceptor.TransactionAspectSupport.invokeWithinTransaction(TransactionAspectSupport.java:380)
2026-08-05T02:39:03.389108177Z  [content-service]  at org.springframework.transaction.interceptor.TransactionInterceptor.invoke(TransactionInterceptor.java:119)
2026-08-05T02:39:03.389110734Z  [content-service]  at org.springframework.aop.framework.ReflectiveMethodInvocation.proceed(ReflectiveMethodInvocation.java:184)
2026-08-05T02:39:03.389113072Z  [content-service]  at org.springframework.aop.framework.CglibAopProxy$DynamicAdvisedInterceptor.intercept(CglibAopProxy.java:727)
2026-08-05T02:39:03.389122170Z  [content-service]  at com.example.toycontent.app.feed.controller.FeedController.createFeed(FeedController.java:114)
2026-08-05T02:39:03.389370324Z  [content-service]  at org.springframework.web.method.support.InvocableHandlerMethod.doInvoke(InvocableHandlerMethod.java:257)
2026-08-05T02:39:03.389372870Z  [content-service]  at org.springframework.web.method.support.InvocableHandlerMethod.invokeForRequest(InvocableHandlerMethod.java:190)
2026-08-05T02:39:03.389374844Z  [content-service]  at org.springframework.web.servlet.mvc.method.annotation.ServletInvocableHandlerMethod.invokeAndHandle(ServletInvocableHandlerMethod.java:118)
2026-08-05T02:39:03.389376754Z  [content-service]  at org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter.invokeHandlerMethod(RequestMappingHandlerAdapter.java:986)
2026-08-05T02:39:03.389378490Z  [content-service]  at org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter.handleInternal(RequestMappingHandlerAdapter.java:891)
2026-08-05T02:39:03.389380090Z  [content-service]  at org.springframework.web.servlet.mvc.method.AbstractHandlerMethodAdapter.handle(AbstractHandlerMethodAdapter.java:87)
2026-08-05T02:39:03.389381834Z  [content-service]  at org.springframework.web.servlet.DispatcherServlet.doDispatch(DispatcherServlet.java:1088)
2026-08-05T02:39:03.389383473Z  [content-service]  at org.springframework.web.servlet.DispatcherServlet.doService(DispatcherServlet.java:978)
2026-08-05T02:39:03.389385056Z  [content-service]  at org.springframework.web.servlet.FrameworkServlet.processRequest(FrameworkServlet.java:1014)
2026-08-05T02:39:03.389386973Z  [content-service]  at org.springframework.web.servlet.FrameworkServlet.doPost(FrameworkServlet.java:914)
2026-08-05T02:39:03.389541404Z  [content-service]  at org.springframework.security.web.access.ExceptionTranslationFilter.doFilter(ExceptionTranslationFilter.java:126)
2026-08-05T02:39:03.389544112Z  [content-service]  at org.springframework.security.web.access.ExceptionTranslationFilter.doFilter(ExceptionTranslationFilter.java:120)
2026-08-05T02:39:03.390059102Z  [content-service]  Caused by: org.hibernate.exception.ConstraintViolationException: could not execute statement [Duplicate entry '163-178' for key 'tb_feed_hashtags.uk_feed_hashtag'] [insert into tb_feed_hashtags (created_at,feed_id,hashtag_id,updated_at) values (?,?,?,?)]
2026-08-05T02:39:03.390062431Z  [content-service]  at org.hibernate.dialect.MySQLDialect.lambda$buildSQLExceptionConversionDelegate$3(MySQLDialect.java:1245)
2026-08-05T02:39:03.390064973Z  [content-service]  at org.hibernate.exception.internal.StandardSQLExceptionConverter.convert(StandardSQLExceptionConverter.java:58)
2026-08-05T02:39:03.390067451Z  [content-service]  at org.hibernate.engine.jdbc.spi.SqlExceptionHelper.convert(SqlExceptionHelper.java:108)
2026-08-05T02:39:03.390243611Z  [content-service]  at org.springframework.dao.support.PersistenceExceptionTranslationInterceptor.invoke(PersistenceExceptionTranslationInterceptor.java:138)
2026-08-05T02:39:03.390248834Z  [content-service]  Caused by: java.sql.SQLIntegrityConstraintViolationException: Duplicate entry '163-178' for key 'tb_feed_hashtags.uk_feed_hashtag'
2026-08-05T02:39:03.390251351Z  [content-service]  at com.mysql.cj.jdbc.exceptions.SQLError.createSQLException(SQLError.java:109)
2026-08-05T02:39:03.390254139Z  [content-service]  at com.mysql.cj.jdbc.exceptions.SQLExceptionsMapping.translateException(SQLExceptionsMapping.java:114)
2026-08-05T02:39:03.391106045Z  [content-service]  2026-08-05 11:39:03.390 [http-nio-8082-exec-5] ERROR [traceId=6a72a247a643690f6dd80b177c87c2a9,spanId=6dd80b177c87c2a9,userId=NONE] c.e.t.a.c.f.RequestLoggingFilter - [HTTP] POST /api/feeds 500 - 161ms
2026-08-05T02:39:03.391106045Z  [content-service]  2026-08-05 11:39:03.390 [http-nio-8082-exec-5] ERROR [traceId=6a72a247a643690f6dd80b177c87c2a9,spanId=6dd80b177c87c2a9,userId=NONE] c.e.t.a.c.f.RequestLoggingFilter - [HTTP] POST /api/feeds 500 - 161ms
```

### 메트릭 시계열

| 쿼리 | series | 점 | min | max | last | 값이 0이던 구간 |
|---|---|---:|---:|---:|---:|---|
| `hikaricp_connections_active` | `{__name__=hikaricp_connections_active, application=auth-service, cluster=yogurtte-k3s-prod, container=auth-service, instance=10.42.1.46:8090, job=auth-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=auth-service-5999bb9f5c-hmgp9, pool=HikariPool-1, service=auth-service}` | 61 | 0 | 0 | 0 | **2026-08-05T02:30:00Z ~ 2026-08-05T02:45:00Z** |
| `hikaricp_connections_active` | `{__name__=hikaricp_connections_active, application=chat-service, cluster=yogurtte-k3s-prod, container=chat-service, instance=10.42.1.47:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-fdcc7c776-xf4sv, pool=HikariPool-1}` | 61 | 0 | 0 | 0 | **2026-08-05T02:30:00Z ~ 2026-08-05T02:45:00Z** |
| `hikaricp_connections_active` | `{__name__=hikaricp_connections_active, application=content-service, cluster=yogurtte-k3s-prod, container=content-service, instance=10.42.1.43:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-6995bb7d94-h2f6n, pool=HikariPool-1}` | 61 | 0 | 0 | 0 | **2026-08-05T02:30:00Z ~ 2026-08-05T02:45:00Z** |
| `hikaricp_connections_active` | `{__name__=hikaricp_connections_active, application=content-service, cluster=yogurtte-k3s-prod, container=content-service, instance=10.42.3.42:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-6995bb7d94-nq9l2, pool=HikariPool-1}` | 61 | 0 | 0 | 0 | **2026-08-05T02:30:00Z ~ 2026-08-05T02:45:00Z** |
| `hikaricp_connections_pending` | `{__name__=hikaricp_connections_pending, application=auth-service, cluster=yogurtte-k3s-prod, container=auth-service, instance=10.42.1.46:8090, job=auth-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=auth-service-5999bb9f5c-hmgp9, pool=HikariPool-1, service=auth-service}` | 61 | 0 | 0 | 0 | **2026-08-05T02:30:00Z ~ 2026-08-05T02:45:00Z** |
| `hikaricp_connections_pending` | `{__name__=hikaricp_connections_pending, application=chat-service, cluster=yogurtte-k3s-prod, container=chat-service, instance=10.42.1.47:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-fdcc7c776-xf4sv, pool=HikariPool-1}` | 61 | 0 | 0 | 0 | **2026-08-05T02:30:00Z ~ 2026-08-05T02:45:00Z** |
| `hikaricp_connections_pending` | `{__name__=hikaricp_connections_pending, application=content-service, cluster=yogurtte-k3s-prod, container=content-service, instance=10.42.1.43:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-6995bb7d94-h2f6n, pool=HikariPool-1}` | 61 | 0 | 0 | 0 | **2026-08-05T02:30:00Z ~ 2026-08-05T02:45:00Z** |
| `hikaricp_connections_pending` | `{__name__=hikaricp_connections_pending, application=content-service, cluster=yogurtte-k3s-prod, container=content-service, instance=10.42.3.42:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-6995bb7d94-nq9l2, pool=HikariPool-1}` | 61 | 0 | 0 | 0 | **2026-08-05T02:30:00Z ~ 2026-08-05T02:45:00Z** |
| `rate(jvm_gc_pause_seconds_sum[5m])` | `{action=end of major GC, application=chat-service, cause=Allocation Failure, cluster=yogurtte-k3s-prod, container=chat-service, gc=MarkSweepCompact, instance=10.42.1.47:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-fdcc7c776-xf4sv}` | 61 | 0 | 0 | 0 | **2026-08-05T02:30:00Z ~ 2026-08-05T02:45:00Z** |
| `rate(jvm_gc_pause_seconds_sum[5m])` | `{action=end of minor GC, application=auth-service, cause=G1 Evacuation Pause, cluster=yogurtte-k3s-prod, container=auth-service, gc=G1 Young Generation, instance=10.42.1.46:8090, job=auth-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=auth-service-5999bb9f5c-hmgp9, service=auth-service}` | 61 | 0 | 0.000 | 0 | **2026-08-05T02:30:00Z ~ 2026-08-05T02:39:30Z, 2026-08-05T02:43:45Z ~ 2026-08-05T02:45:00Z** |
| `rate(jvm_gc_pause_seconds_sum[5m])` | `{action=end of minor GC, application=chat-service, cause=Allocation Failure, cluster=yogurtte-k3s-prod, container=chat-service, gc=Copy, instance=10.42.1.47:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-fdcc7c776-xf4sv}` | 61 | 0.000 | 0.000 | 0.000 | — |
| `rate(jvm_gc_pause_seconds_sum[5m])` | `{action=end of minor GC, application=content-service, cause=Allocation Failure, cluster=yogurtte-k3s-prod, container=content-service, gc=Copy, instance=10.42.1.43:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-6995bb7d94-h2f6n}` | 61 | 0 | 0.000 | 0.000 | **2026-08-05T02:30:00Z ~ 2026-08-05T02:34:45Z, 2026-08-05T02:39:00Z ~ 2026-08-05T02:43:45Z** |
| `rate(jvm_gc_pause_seconds_sum[5m])` | `{action=end of minor GC, application=content-service, cause=Allocation Failure, cluster=yogurtte-k3s-prod, container=content-service, gc=Copy, instance=10.42.3.42:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-6995bb7d94-nq9l2}` | 61 | 0 | 0.000 | 0.000 | **2026-08-05T02:30:00Z ~ 2026-08-05T02:33:30Z, 2026-08-05T02:37:45Z ~ 2026-08-05T02:44:30Z** |
| `up` | `{__name__=up, app=node-exporter, cluster=yogurtte-k3s-prod, component=metrics, container=node-exporter, instance=ip-172-31-40-241, job=integrations/node_exporter, k8s_cluster_name=yogurtte-k3s-prod, namespace=monitoring, pod=grafana-k8s-monitoring-node-exporter-cph5l, source=kubernetes, workload=DaemonSet/grafana-k8s-monitoring-node-exporter}` | 61 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, app=node-exporter, cluster=yogurtte-k3s-prod, component=metrics, container=node-exporter, instance=ip-172-31-45-39, job=integrations/node_exporter, k8s_cluster_name=yogurtte-k3s-prod, namespace=monitoring, pod=grafana-k8s-monitoring-node-exporter-9kvqt, source=kubernetes, workload=DaemonSet/grafana-k8s-monitoring-node-exporter}` | 61 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, cluster=yogurtte-k3s-prod, container=auth-service, instance=10.42.1.46:8090, job=auth-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=auth-service-5999bb9f5c-hmgp9}` | 61 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, cluster=yogurtte-k3s-prod, container=chat-service, instance=10.42.1.47:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-fdcc7c776-xf4sv}` | 61 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, cluster=yogurtte-k3s-prod, container=content-service, instance=10.42.1.43:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-6995bb7d94-h2f6n}` | 61 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, cluster=yogurtte-k3s-prod, container=content-service, instance=10.42.3.42:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-6995bb7d94-nq9l2}` | 61 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, cluster=yogurtte-k3s-prod, instance=10.42.1.248:8080, job=integrations/kubernetes/kube-state-metrics, k8s_cluster_name=yogurtte-k3s-prod, source=kubernetes}` | 61 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, cluster=yogurtte-k3s-prod, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod}` | 61 | 1 | 1 | 1 | — |
| `mongodb_up` | `{__name__=mongodb_up, cluster=yogurtte-k3s-prod, instance=infra-server, job=mongodb, k8s_cluster_name=yogurtte-k3s-prod}` | 61 | 1 | 1 | 1 | — |
| `kafka_brokers` | `{__name__=kafka_brokers, cluster=yogurtte-k3s-prod, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod}` | 61 | 1 | 1 | 1 | — |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=chat-service-fcm-tokens, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=0, topic=user.fcm-tokens}` | 61 | 0 | 0 | 0 | **2026-08-05T02:30:00Z ~ 2026-08-05T02:45:00Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=chat-service-fcm-tokens, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=1, topic=user.fcm-tokens}` | 61 | 0 | 0 | 0 | **2026-08-05T02:30:00Z ~ 2026-08-05T02:45:00Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=chat-service-fcm-tokens, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=2, topic=user.fcm-tokens}` | 61 | 0 | 0 | 0 | **2026-08-05T02:30:00Z ~ 2026-08-05T02:45:00Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=chat-service-notification-settings, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=0, topic=user.notification-settings}` | 61 | 0 | 0 | 0 | **2026-08-05T02:30:00Z ~ 2026-08-05T02:45:00Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=chat-service-notification-settings, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=1, topic=user.notification-settings}` | 61 | 0 | 0 | 0 | **2026-08-05T02:30:00Z ~ 2026-08-05T02:45:00Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=chat-service-notification-settings, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=2, topic=user.notification-settings}` | 61 | 0 | 0 | 0 | **2026-08-05T02:30:00Z ~ 2026-08-05T02:45:00Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=db-writer, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=0, topic=chat.messages}` | 61 | 0 | 0 | 0 | **2026-08-05T02:30:00Z ~ 2026-08-05T02:45:00Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=db-writer, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=1, topic=chat.messages}` | 61 | 0 | 0 | 0 | **2026-08-05T02:30:00Z ~ 2026-08-05T02:45:00Z** |
| `websocket_active_users` | `{__name__=websocket_active_users, application=chat-service, cluster=yogurtte-k3s-prod, container=chat-service, instance=10.42.1.47:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-fdcc7c776-xf4sv}` | 61 | 0 | 0 | 0 | **2026-08-05T02:30:00Z ~ 2026-08-05T02:45:00Z** |

값이 0이던 구간은 굵게 표시했다 — 프로세스가 사라져 시계열이 꺾인 것이 유일한 신호인 장애가 있다.

