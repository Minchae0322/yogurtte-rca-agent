# RCA Report — `scan-1786497720`

| 항목 | 값 |
|---|---|
| 모드 | rca |
| 질문 | 최근 1시간 안에 팔로우 목록이 안 열린다는 제보가 있다. 원인을 조사해줘 |
| 시각 | 2026-08-12T01:37:03.570615600Z |
| provider | claude-cli |
| model | `claude-opus-5` · turns 1 |
| prompt | `.\prompts\system-prompt.md` |
| tokens | in 101592 (cacheRead 23,796 · cacheCreate 77,794) / out 6701 · cost $0.9574 |
| elapsed | total 122074ms (tempo 1009 · loki 528 · mimir 577 · assemble 93 · llm 108958) |

## 탐색 (Triage)

| 항목 | 값 |
|---|---|
| 시간창 해석 | 상대 표현 '최근 1시간' |
| 시간창 확신도 | EXACT |
| 스윕 창 | 2026-08-12T00:36:21.968752200Z ~ 2026-08-12T01:36:21.968752200Z |
| 좁힌 창 | 2026-08-12T01:22:00Z ~ 2026-08-12T01:36:00Z |
| 대상 | auth-service, content-service |
| traceId | 6a7bcdbd052a495e0b6b5c4c9b4c3a61 |
| 트레이스 후보 | 3건 |
| 장애 후보 | 7건 · 선택 INC-2, INC-4, INC-5 |
| 계획 파싱 | 성공 |
| 스윕 컨텍스트 | **후보만 — 원본 제외 (B)** |
| prompt | `.\prompts\triage-prompt.md` |
| tokens | in 33544 / out 2464 · cost $0.1982 |
| chars | 컨텍스트 3,107 + 프롬프트 1,399 = **4,506** |
| elapsed | survey 1752ms · llm 39654ms |

**선정 이유**: 팔로우 목록 조회에 해당하는 트레이스가 0건인 상태에서 로그에만 남은 반복 에러 클러스터는 auth-service NPE와 content-service DataException 둘뿐이고, 팔로우 도메인 소유 서비스를 확정할 근거가 없어 양쪽을 함께 열어둔다.

**근거**

- auth-service ERROR/WARN 2건 01:31~01:32 + java.lang.NullPointerException 2건 01:32~01:33, x4회 01:32~01:36 평균 60초 간격 — 창 끝까지 진행 중인 반복 지문
- auth-service http post /files/upload 3,063ms (slow 채널) 01:34:53 — 같은 서비스가 같은 시각대에 지연까지 동반, INC-4와 같은 열화의 다른 지문일 수 있음
- content-service org.hibernate.exception.DataException 1건 01:22~01:23, x5회 01:22~01:27 평균 60초 간격 — 팔로우 그래프가 content 소유일 경우의 대안 후보
- Tempo 에러 검색 2건·지연 검색 1건이 모두 POST 쓰기 경로(/feeds, /feeds/{feedId}/comments, /files/upload)뿐 — 팔로우 목록 조회에 해당하는 트레이스가 0건인 것 자체가 신호(요청이 진입 못 했거나 예외로 트레이스 미생성)
- up / mongodb_up / kafka_brokers / kafka_consumergroup_lag / websocket_active_users 모두 이상 0건 — 파드 다운·DB 다운·메시징 지연은 원인에서 배제

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
- 구간: 2026-08-12T01:31:00Z ~ 2026-08-12T01:36:00Z  (LOKI · 집계 해상도만큼 흐림)
- ERROR/WARN 2건 (2026-08-12T01:31:00Z ~ 2026-08-12T01:32:00Z)  [x4회 · 2026-08-12T01:31:00Z~2026-08-12T01:35:00Z · 평균 60초 간격]
- 예외 java.lang.NullPointerException 2건 (2026-08-12T01:32:00Z ~ 2026-08-12T01:33:00Z)  [x4회 · 2026-08-12T01:32:00Z~2026-08-12T01:36:00Z · 평균 60초 간격]
- 같은 시각의 다른 후보: INC-5, INC-6, INC-7  (인과 여부는 판단하지 않았다)

## INC-5  auth-service  |  http post /files/upload
- 구간: 2026-08-12T01:34:53.777306Z ~ 2026-08-12T01:34:56.840306Z  (TEMPO · 시각 정확)
- auth-service http post /files/upload 3,063ms (slow 채널)
- traceId: 6a7bcdbd052a495e0b6b5c4c9b4c3a61
- 같은 시각의 다른 후보: INC-4  (인과 여부는 판단하지 않았다)

## INC-6  content-service  |  ERROR/WARN
- 구간: 2026-08-12T01:35:00Z ~ 2026-08-12T01:36:00Z  (LOKI · 집계 해상도만큼 흐림)
- ERROR/WARN 4건 (2026-08-12T01:35:00Z ~ 2026-08-12T01:36:00Z)
- 원인 예외 java.sql.SQLIntegrityConstraintViolationException 1건 (2026-08-12T01:35:00Z ~ 2026-08-12T01:36:00Z)
- 원인 예외 org.hibernate.exception.ConstraintViolationException 1건 (2026-08-12T01:35:00Z ~ 2026-08-12T01:36:00Z)
- 같은 시각의 다른 후보: INC-4, INC-7  (인과 여부는 판단하지 않았다)

## INC-7  content-service  |  http post /feeds
- 구간: 2026-08-12T01:35:08.522901Z ~ 2026-08-12T01:35:08.608901Z  (TEMPO · 시각 정확)
- content-service http post /feeds 86ms (error 채널)
- traceId: 6a7bcdccfb0b88f17f425d5e2fb9908f
- 같은 시각의 다른 후보: INC-4, INC-6  (인과 여부는 판단하지 않았다)

**기각한 후보**

- INC-1 — chat-service ERROR/WARN 1건 단발이고 websocket_active_users 이상 0건 — 팔로우 목록 조회 경로와 도메인이 무관하다.
- INC-3 — POST /feeds/{feedId}/comments 208ms 에러 트레이스로 댓글 쓰기 경로다. INC-2의 하류 지문일 수는 있으나 조사 창은 INC-2 선택으로 이미 덮인다.
- INC-6 — ConstraintViolationException / SQLIntegrityConstraintViolationException은 중복 키 등 쓰기 충돌 지문으로, 목록이 '안 열리는' 읽기 실패와 지문이 맞지 않는다.
- INC-7 — POST /feeds 86ms 에러 트레이스로 피드 생성 쓰기 실패이며 INC-6의 표현형이다. 읽기 실패 증상과 불일치한다.

**보정 기록**

- 창을 후보 [INC-2, INC-4, INC-5] 의 신호 시각에서 계산했다 (2026-08-12T01:22:00Z ~ 2026-08-12T01:36:00Z)

## 수집 범위 (Coverage)

- **window**: 2026-08-12T01:22:00Z ~ 2026-08-12T01:36:00Z (840s)
- **trace**: 105,520B / 117 spans (상위 span만)
- **창 안 후보 트레이스**: 10건 / 105,520B
- **logs**: errwarn=135,247B · traceId=9,869B
- **metrics**: 8 수집 / 108,754B, 누락 [sum(rate(http_server_requests_seconds_count{application="content-service", status="401"}[1m]))]
- **context**: 132,661 chars (+ 시스템 프롬프트 805 chars)

### 토큰 축 (개선 지표)

| 단계 | 총 in | chars | 컨텍스트 토큰 |
|---|---:|---:|---:|
| 탐색 | 33,544 | 4,506 | 3,534 |
| 분석 | 101,592 | 133,466 | 71,582 |
| **합계** | **135,136** | | **75,116** |

- **overheadTokens 30,010 tok — 이 회차에 실측**(1자 프롬프트 프로브, 같은 명령·모델·샌드박스).
  호출마다 한 번씩 얹히므로 단계별로 뺀다. **다른 날 상수를 쓰지 않았다 = `█ 실측`.**
- contextTokens (count_tokens API): 측정 안 됨 — 구독 CLI 경로엔 API 키가 없다
- 비용은 **API 환산 추정치**다. 구독 계정으로 호출하므로 실제 청구액이 아니다.

## 수집 실패/누락

- Metric 'sum(rate(http_server_requests_seconds_count{application="content-service", status="401"}[1m]))' returned no series in this window; skipped.

---

## 1. 원인 후보 랭킹 (최대 3개)

1. **auth-service 팔로우 조회 DTO의 `size` 널 참조 NPE → 500** (`FollowCondition$FollowingSearch.limit` / `FollowerSearch.limit`)
2. **팔로우 목록 쿼리의 컬렉션 페치 + 인메모리 페이징으로 인한 응답 지연(2983ms)** — 실패까지는 아니나 "안 열린다" 체감에 기여
3. **content-service의 500들(Data truncation / Duplicate entry)** — 팔로우 경로와 무관, 사실상 배제

---

## 2. 후보별 근거

### 후보 1. `size` 널 참조 NPE

- **근거:**
  - auth-service, 2026-08-12 10:32:07 (=01:32:07Z), traceId `6a7bcd177ec7dcbafab90613c89ef91a`:
    `java.lang.NullPointerException: Cannot invoke "java.lang.Integer.intValue()" because "this.size" is null`
    스택: `FollowCondition$FollowingSearch.limit(FollowCondition.java:25)` → `FollowRepositoryCustomImpl.findFollowingsByUserId(:35)` → `UserService.getFollowingList(:307)` → `UserController.getFollowingList(:132)`
    결과 라인: `[HTTP] GET /api/user/1/following 500 - 99ms`
  - 같은 초(10:32:07), traceId `6a7bcd1719fe4d3b8126248de7af6455`: 동일 NPE가 **팔로워** 경로에서도 발생 — `FollowCondition$FollowerSearch.limit(FollowCondition.java:45)` → `findFollowersByUserId(:65)` → `getFollowerList(:318)` → `UserController.getFollowerList(:142)`, `[HTTP] GET /api/user/1/followers 500 - 67ms`
  - `JwtFilter` 통과 후 컨트롤러 안에서 터졌고 로그 컨텍스트가 `userId=1` → 인증 실패가 아니라 애플리케이션 로직 결함.
  - 두 개의 별도 DTO(`FollowingSearch`, `FollowerSearch`)에서 같은 형태로 재현 → 페이징 파라미터 기본값 부재라는 공통 결함.
- **확신도: 높음**
- **반증 데이터:** 같은 엔드포인트가 **항상** 죽지는 않는다. 10:31:56 `GET /api/user/1/following 200 - 2983ms`(traceId `6a7bcd0988d2fa47b225dfd17cd82615`) 성공. 즉 전면 장애가 아니라 `size`가 없는 요청에서만 500이 나는 **파라미터 의존적 간헐 장애**다. 이 반증은 후보 1을 뒤집지 않고 조건을 좁힌다.
- **대기·지연 판정:** 99ms / 67ms 만에 500 종료 — 어떤 대기 상한에도 도달하지 않은 **즉시 실패**. auth-service `hikaricp_connections_pending`은 전 구간 0, `hikaricp_connections_active`도 전 구간 0 → 커넥션 획득 대기 없음. **최종 상태: 실패(HTTP 500), 재시도 흔적 없음, 요청 폐기.** 클라이언트 재시도 여부는 데이터에 없어 **판정 불가**.

### 후보 2. 컬렉션 페치 + 인메모리 페이징 지연

- **근거:**
  - 10:31:56 `WARN org.hibernate.orm.query - HHH90003004: firstResult/maxResults specified with collection fetch; applying in memory` (traceId `6a7bcd0988d2fa47b225dfd17cd82615`) 직후 같은 traceId로 `[HTTP-SLOW] GET /api/user/1/following 200 - 2983ms`.
  - 10:33:07 (traceId `6a7bcd53867ffb8faf3127e898919e0b`) 동일 `HHH90003004` 경고 재발 → 일회성 아님.
  - 2983ms는 이 창의 auth-service 요청 중 최장급이며, DB 자원 대기(pending=0)나 GC(auth-service `jvm_gc_pause_seconds_sum` rate 최대 4.58e-5)로 설명되지 않음 → 페이징을 DB가 아닌 애플리케이션 메모리에서 처리한 비용으로 설명하는 게 자연스럽다.
- **확신도: 중간** (지연의 존재는 확정, "안 열린다" 제보와의 인과는 미확정)
- **반증 데이터:** 해당 요청은 **200으로 성공**했다. 서버 측에서는 실패가 아니다. 또한 10:33:07 경고 건에는 대응하는 HTTP 결과 라인이 수집되지 않아 그 요청이 느렸는지조차 알 수 없다.
- **대기·지연 판정:** 실측 2983ms. **대조할 상한(클라이언트/게이트웨이 타임아웃 설정값)이 관측 데이터에 없다 → 만료 여부 판정 불가.** 서버 기준 **최종 상태: 성공(HTTP 200)**. 클라이언트가 먼저 끊었는지는 **판정 불가**.

### 후보 3. content-service 500 (팔로우와 무관)

- **근거(배제 근거):**
  - 10:22:30 `Data truncation: Data too long for column 'content' at row 1` (SQL Error 1406, SQLState 22001) → `POST /api/feeds/145/comments 500 - 208ms`, traceId `6a7bcad606c9ad16a434d244e431e33d`.
  - 10:35:08 `Duplicate entry '166-179' for key 'tb_feed_hashtags.uk_feed_hashtag'` (SQL Error 1062, SQLState 23000) → `POST /api/feeds 500 - 84ms`, traceId `6a7bcdccfb0b88f17f425d5e2fb9908f`.
  - 둘 다 **content-service**의 **피드/댓글 쓰기** 경로이고, 호출 그래프에 content-service ↔ auth-service 엣지가 **없다**. 팔로우 목록은 auth-service(`UserController.getFollowingList/getFollowerList`) 단독 경로.
- **확신도: 낮음** (팔로우 장애 원인일 가능성은 사실상 없음)
- **반증 데이터:** 위 호출 그래프 부재 자체가 반증. 추가로 팔로우 500 2건의 스택에는 content-service 프레임이 전혀 없다.
- **대기·지연 판정:** 두 트랜잭션 모두 `connection` span에 **`rollback` 이벤트**가 찍혔다(10:22:30.499, 10:35:08.596). 지연 대기는 없었고(각 193.1ms, 77.9ms, pending=0), **최종 상태: 실패 후 롤백 — 쓰기 폐기.** 재시도 흔적 없음.

### 인프라 배제 근거 (공통)
`up`은 auth/chat/content/kafka/mongodb/redis/노드 전부 전 구간 1, `mongodb_up`=1, `kafka_brokers`=1, `kafka_consumergroup_lag` 전 파티션 0(또는 -1=미할당), `websocket_active_users`=0 상수, Hikari active/pending 전 구간 0. → 파드 다운·DB 포화·컨슈머 지연은 원인이 아니다. Redis `server.address=redis://172.31.46.124?timeout=2s`에 대해 실측 KEYS/GET은 0.43~0.53ms → **타임아웃 상한 2s 대비 미만, 만료 아님, 최종 상태: 성공.**

### 데이터 공백 (확신도 하향 요인)
- `http_server_requests_seconds_count{application="content-service", status="401"}` 시리즈 없음 → 인증 실패율 축은 확인 불가. 다만 팔로우 장애는 auth-service 소관이고 로그상 `userId=1`로 인증 통과가 확인되어 영향은 제한적.
- Tempo 수집 10건에 **팔로우 traceId(`6a7bcd177e…`, `6a7bcd1719…`, `6a7bcd0988…`)가 하나도 없다.** 500 2건의 내부 span 분해와 2983ms의 구간별 소요를 트레이스로 교차검증하지 못했다 → 후보 2의 확신도를 "중간"에 묶은 이유.
- 실패 요청의 쿼리스트링(즉 `size` 파라미터가 실제로 누락됐는지)이 로그에 없다. NPE 메시지로 역추론했을 뿐이다.

---

## 3. 권장 다음 조치

### 이미 발생한 피해 — 복구 가능한가
- **팔로우 목록 500 (GET 2건): 복구 대상 없음.** 읽기 전용 요청이라 상태 변경·데이터 유실이 없다. 코드 수정 후 사용자가 다시 열면 그대로 복원된다. 별도 백필 불필요.
- **content-service 쓰기 2건: 복구 불가(자동으로는), 재입력 필요.** `connection` span의 `rollback` 이벤트로 두 트랜잭션 모두 원자적으로 폐기됨이 확인된다 — 부분 저장은 없다. 다만 사용자가 작성한 댓글(feed 145) 1건과 피드 생성 1건의 **입력 내용 자체는 어디에도 남아 있지 않아** 서버 측 재실행이 불가능하다. 해당 사용자(userId=1)에게 재작성 안내가 유일한 경로다. (팔로우 장애와는 별건)
- 그 외 유실: 없음. Kafka lag 0, DLQ(`user.notifications.dlq`) lag 0 → 비동기 알림 유실도 관측되지 않음.

### 재발 방지
1. `FollowCondition$FollowingSearch`(FollowCondition.java:25)와 `FollowerSearch`(:45) **양쪽** `limit()`의 `size` 널 처리 — 기본 페이지 크기를 DTO 기본값 또는 컨트롤러 바인딩 단계에서 강제. 두 클래스에 같은 결함이 있으므로 한쪽만 고치면 절반이 남는다.
2. 컨트롤러 진입 시점 파라미터 검증(널이면 400 반환)으로 널이 리포지토리까지 흘러 500이 되는 경로 차단.
3. `HHH90003004`(컬렉션 페치 + 페이징 인메모리) 제거 — 팔로우 목록 조회에서 컬렉션 페치와 페이징을 분리. 2983ms의 직접 원인 후보다.
4. 알림: auth-service의 5xx 비율과 `[HTTP-SLOW]` 발생을 경보화. 현재 이 장애는 사용자 제보로만 발견됐다.
5. 수집 보강: content-service 401 메트릭 결측 원인 확인, auth-service 요청의 쿼리스트링 로깅(민감정보 제외), 팔로우 엔드포인트 트레이스 샘플링 상향.

### 복구 확인
- `size` 파라미터를 **생략한** `GET /api/user/{id}/following`과 `GET /api/user/{id}/followers`를 각각 호출해 200 및 정상 페이지 본문 확인 (현재는 이 조건에서 500).
- auth-service 로그에서 `Cannot invoke "java.lang.Integer.intValue()" because "this.size" is null` 0건, `FollowCondition.java:25`/`:45` 프레임 0건.
- `HHH90003004` 경고 0건, 해당 엔드포인트 `[HTTP-SLOW]` 0건, 응답 시간이 2983ms 대비 유의미하게 하락했는지 확인.
- 배포 후 최소 30분간 auth-service 5xx 0 유지, `hikaricp_connections_pending` 0 유지(수정이 새 대기를 만들지 않았는지).

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
content-service --jdbc--> mysql/content (HikariPool-1)  70회  최대 197.8ms
    error: Duplicate entry '166-179' for key 'tb_feed_hashtags.uk_feed_hashtag'
    error: Data truncation: Data too long for column 'content' at row 1
    events: acquired, rollback, commit
content-service --messaging--> kafka/user.notifications  1회  최대 16.8ms  [publish]
kafka/user.notifications --messaging--> chat-service  1회  최대 407.2ms  [receive]
```

### span (duration 상위 15 / 전체 117)

| ms | service | span | 시작 |
|---:|---|---|---|
| 3063.88 | auth-service | `http post /files/upload` | 2026-08-12T01:34:53.777306Z |
| 2738.75 | auth-service | `secured request` | 2026-08-12T01:34:54.101230Z |
| 407.21 | chat-service | `receive` | 2026-08-12T01:23:30.973498Z |
| 406.36 | chat-service | `connection` | 2026-08-12T01:23:30.974064Z |
| 399.97 | chat-service | `user-notification-service#process-notification` | 2026-08-12T01:23:30.977292Z |
| 380.10 | chat-service | `push-dispatcher#dispatch` | 2026-08-12T01:23:30.997051Z |
| 323.23 | auth-service | `security filterchain before` | 2026-08-12T01:34:53.777939Z |
| 247.74 | content-service | `http post /feeds` | 2026-08-12T01:34:57.012959Z |
| 246.44 | content-service | `secured request` | 2026-08-12T01:34:57.013327Z |
| 208.90 | content-service | `http post /feeds/{feedId}/comments` | 2026-08-12T01:22:30.309754Z |
| 207.05 | content-service | `secured request` | 2026-08-12T01:22:30.310174Z |
| 197.80 | content-service | `connection` | 2026-08-12T01:34:57.061783Z |
| 194.47 | auth-service | `http post /files/upload` | 2026-08-12T01:35:08.175930Z |
| 193.11 | content-service | `connection` | 2026-08-12T01:22:30.323947Z |
| 178.00 | auth-service | `secured request` | 2026-08-12T01:35:08.192003Z |

### 로그 원문 (60 / 전체 896줄)

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
2026-08-12T01:22:30.514438766Z  [content-service]  at org.springframework.dao.support.PersistenceExceptionTranslationInterceptor.invoke(PersistenceExceptionTranslationInterceptor.java:160)
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
2026-08-12T01:31:56.206744805Z  [auth-service]  [2m2026-08-12 10:31:56[0;39m [2m[http-nio-8081-exec-5][0;39m [33m WARN [traceId=6a7bcd0988d2fa47b225dfd17cd82615,spanId=b65543a466233f59,userId=1][0;39m [36morg.hibernate.orm.query[0;39m [2m-[0;39m HHH90003004: firstResult/maxResults specified with collection fetch; applying in memory
2026-08-12T01:31:56.392608168Z  [auth-service]  [2m2026-08-12 10:31:56[0;39m [2m[http-nio-8081-exec-5][0;39m [33m WARN [traceId=6a7bcd0988d2fa47b225dfd17cd82615,spanId=b225dfd17cd82615,userId=NONE][0;39m [36mc.e.t.a.c.f.RequestLoggingFilter[0;39m [2m-[0;39m [HTTP-SLOW] GET /api/user/1/following 200 - 2983ms
2026-08-12T01:32:07.271078769Z  [auth-service]  [2m2026-08-12 10:32:07[0;39m [2m[http-nio-8081-exec-7][0;39m [31mERROR [traceId=6a7bcd177ec7dcbafab90613c89ef91a,spanId=da80a975e6b830c2,userId=1][0;39m [36mc.e.t.a.c.e.GlobalExceptionHandler[0;39m [2m-[0;39m [api-error] handleAllException
2026-08-12T01:32:07.271126789Z  [auth-service]  java.lang.NullPointerException: Cannot invoke "java.lang.Integer.intValue()" because "this.size" is null
2026-08-12T01:32:07.271158867Z  [auth-service]  at org.springframework.dao.support.PersistenceExceptionTranslationInterceptor.invoke(PersistenceExceptionTranslationInterceptor.java:137)
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
2026-08-12T01:35:08.587799840Z  [content-service]  2026-08-12 10:35:08.586 [http-nio-8082-exec-3]  WARN [traceId=6a7bcdccfb0b88f17f425d5e2fb9908f,spanId=b778311c0786ea65,userId=1] o.h.e.jdbc.spi.SqlExceptionHelper - SQL Error: 1062, SQLState: 23000
2026-08-12T01:35:08.587848820Z  [content-service]  2026-08-12 10:35:08.587 [http-nio-8082-exec-3] ERROR [traceId=6a7bcdccfb0b88f17f425d5e2fb9908f,spanId=b778311c0786ea65,userId=1] o.h.e.jdbc.spi.SqlExceptionHelper - Duplicate entry '166-179' for key 'tb_feed_hashtags.uk_feed_hashtag'
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
```

### 메트릭 시계열

| 쿼리 | series | 점 | min | max | last | 값이 0이던 구간 |
|---|---|---:|---:|---:|---:|---|
| `hikaricp_connections_active` | `{__name__=hikaricp_connections_active, application=auth-service, cluster=yogurtte-k3s-prod, container=auth-service, instance=10.42.1.51:8090, job=auth-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=auth-service-5999bb9f5c-lzp4p, pool=HikariPool-1, service=auth-service}` | 57 | 0 | 0 | 0 | **2026-08-12T01:22:00Z ~ 2026-08-12T01:36:00Z** |
| `hikaricp_connections_active` | `{__name__=hikaricp_connections_active, application=chat-service, cluster=yogurtte-k3s-prod, container=chat-service, instance=10.42.1.55:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-fdcc7c776-kh2dl, pool=HikariPool-1}` | 57 | 0 | 0 | 0 | **2026-08-12T01:22:00Z ~ 2026-08-12T01:36:00Z** |
| `hikaricp_connections_active` | `{__name__=hikaricp_connections_active, application=content-service, cluster=yogurtte-k3s-prod, container=content-service, instance=10.42.1.53:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-85f648fcff-sp24n, pool=HikariPool-1}` | 57 | 0 | 0 | 0 | **2026-08-12T01:22:00Z ~ 2026-08-12T01:36:00Z** |
| `hikaricp_connections_active` | `{__name__=hikaricp_connections_active, application=content-service, cluster=yogurtte-k3s-prod, container=content-service, instance=10.42.3.46:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-85f648fcff-v2pw9, pool=HikariPool-1}` | 57 | 0 | 0 | 0 | **2026-08-12T01:22:00Z ~ 2026-08-12T01:36:00Z** |
| `hikaricp_connections_pending` | `{__name__=hikaricp_connections_pending, application=auth-service, cluster=yogurtte-k3s-prod, container=auth-service, instance=10.42.1.51:8090, job=auth-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=auth-service-5999bb9f5c-lzp4p, pool=HikariPool-1, service=auth-service}` | 57 | 0 | 0 | 0 | **2026-08-12T01:22:00Z ~ 2026-08-12T01:36:00Z** |
| `hikaricp_connections_pending` | `{__name__=hikaricp_connections_pending, application=chat-service, cluster=yogurtte-k3s-prod, container=chat-service, instance=10.42.1.55:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-fdcc7c776-kh2dl, pool=HikariPool-1}` | 57 | 0 | 0 | 0 | **2026-08-12T01:22:00Z ~ 2026-08-12T01:36:00Z** |
| `hikaricp_connections_pending` | `{__name__=hikaricp_connections_pending, application=content-service, cluster=yogurtte-k3s-prod, container=content-service, instance=10.42.1.53:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-85f648fcff-sp24n, pool=HikariPool-1}` | 57 | 0 | 0 | 0 | **2026-08-12T01:22:00Z ~ 2026-08-12T01:36:00Z** |
| `hikaricp_connections_pending` | `{__name__=hikaricp_connections_pending, application=content-service, cluster=yogurtte-k3s-prod, container=content-service, instance=10.42.3.46:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-85f648fcff-v2pw9, pool=HikariPool-1}` | 57 | 0 | 0 | 0 | **2026-08-12T01:22:00Z ~ 2026-08-12T01:36:00Z** |
| `rate(jvm_gc_pause_seconds_sum[5m])` | `{action=end of major GC, application=chat-service, cause=Allocation Failure, cluster=yogurtte-k3s-prod, container=chat-service, gc=MarkSweepCompact, instance=10.42.1.55:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-fdcc7c776-kh2dl}` | 57 | 0 | 0 | 0 | **2026-08-12T01:22:00Z ~ 2026-08-12T01:36:00Z** |
| `rate(jvm_gc_pause_seconds_sum[5m])` | `{action=end of minor GC, application=auth-service, cause=G1 Evacuation Pause, cluster=yogurtte-k3s-prod, container=auth-service, gc=G1 Young Generation, instance=10.42.1.51:8090, job=auth-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=auth-service-5999bb9f5c-lzp4p, service=auth-service}` | 57 | 0 | 0.000 | 0.000 | **2026-08-12T01:22:00Z ~ 2026-08-12T01:32:30Z** |
| `rate(jvm_gc_pause_seconds_sum[5m])` | `{action=end of minor GC, application=auth-service, cause=Metadata GC Threshold, cluster=yogurtte-k3s-prod, container=auth-service, gc=G1 Young Generation, instance=10.42.1.51:8090, job=auth-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=auth-service-5999bb9f5c-lzp4p, service=auth-service}` | 57 | 0 | 0 | 0 | **2026-08-12T01:22:00Z ~ 2026-08-12T01:36:00Z** |
| `rate(jvm_gc_pause_seconds_sum[5m])` | `{action=end of minor GC, application=chat-service, cause=Allocation Failure, cluster=yogurtte-k3s-prod, container=chat-service, gc=Copy, instance=10.42.1.55:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-fdcc7c776-kh2dl}` | 57 | 0.000 | 0.000 | 0.000 | — |
| `rate(jvm_gc_pause_seconds_sum[5m])` | `{action=end of minor GC, application=chat-service, cause=GCLocker Initiated GC, cluster=yogurtte-k3s-prod, container=chat-service, gc=Copy, instance=10.42.1.55:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-fdcc7c776-kh2dl}` | 57 | 0 | 0 | 0 | **2026-08-12T01:22:00Z ~ 2026-08-12T01:36:00Z** |
| `rate(jvm_gc_pause_seconds_sum[5m])` | `{action=end of minor GC, application=content-service, cause=Allocation Failure, cluster=yogurtte-k3s-prod, container=content-service, gc=Copy, instance=10.42.1.53:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-85f648fcff-sp24n}` | 57 | 0 | 0.000 | 0.000 | **2026-08-12T01:23:45Z ~ 2026-08-12T01:32:30Z** |
| `rate(jvm_gc_pause_seconds_sum[5m])` | `{action=end of minor GC, application=content-service, cause=Allocation Failure, cluster=yogurtte-k3s-prod, container=content-service, gc=Copy, instance=10.42.3.46:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-85f648fcff-v2pw9}` | 57 | 0 | 0.000 | 0 | **2026-08-12T01:22:00Z ~ 2026-08-12T01:27:45Z, 2026-08-12T01:32:00Z ~ 2026-08-12T01:36:00Z** |
| `up` | `{__name__=up, app=node-exporter, cluster=yogurtte-k3s-prod, component=metrics, container=node-exporter, instance=ip-172-31-40-241, job=integrations/node_exporter, k8s_cluster_name=yogurtte-k3s-prod, namespace=monitoring, pod=grafana-k8s-monitoring-node-exporter-cph5l, source=kubernetes, workload=DaemonSet/grafana-k8s-monitoring-node-exporter}` | 57 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, app=node-exporter, cluster=yogurtte-k3s-prod, component=metrics, container=node-exporter, instance=ip-172-31-45-39, job=integrations/node_exporter, k8s_cluster_name=yogurtte-k3s-prod, namespace=monitoring, pod=grafana-k8s-monitoring-node-exporter-9kvqt, source=kubernetes, workload=DaemonSet/grafana-k8s-monitoring-node-exporter}` | 57 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, cluster=yogurtte-k3s-prod, container=auth-service, instance=10.42.1.51:8090, job=auth-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=auth-service-5999bb9f5c-lzp4p}` | 57 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, cluster=yogurtte-k3s-prod, container=chat-service, instance=10.42.1.55:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-fdcc7c776-kh2dl}` | 57 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, cluster=yogurtte-k3s-prod, container=content-service, instance=10.42.1.53:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-85f648fcff-sp24n}` | 57 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, cluster=yogurtte-k3s-prod, container=content-service, instance=10.42.3.46:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-85f648fcff-v2pw9}` | 57 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, cluster=yogurtte-k3s-prod, instance=10.42.1.248:8080, job=integrations/kubernetes/kube-state-metrics, k8s_cluster_name=yogurtte-k3s-prod, source=kubernetes}` | 57 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, cluster=yogurtte-k3s-prod, instance=62bd8b254df94616e43279f35eed72d3, job=integrations/cloudwatch, k8s_cluster_name=yogurtte-k3s-prod}` | 57 | 1 | 1 | 1 | — |
| `mongodb_up` | `{__name__=mongodb_up, cluster=yogurtte-k3s-prod, instance=infra-server, job=mongodb, k8s_cluster_name=yogurtte-k3s-prod}` | 57 | 1 | 1 | 1 | — |
| `kafka_brokers` | `{__name__=kafka_brokers, cluster=yogurtte-k3s-prod, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod}` | 57 | 1 | 1 | 1 | — |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=chat-service-fcm-tokens, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=0, topic=user.fcm-tokens}` | 57 | 0 | 0 | 0 | **2026-08-12T01:22:00Z ~ 2026-08-12T01:36:00Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=chat-service-fcm-tokens, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=1, topic=user.fcm-tokens}` | 57 | 0 | 0 | 0 | **2026-08-12T01:22:00Z ~ 2026-08-12T01:36:00Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=chat-service-fcm-tokens, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=2, topic=user.fcm-tokens}` | 57 | 0 | 0 | 0 | **2026-08-12T01:22:00Z ~ 2026-08-12T01:36:00Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=chat-service-notification-settings, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=0, topic=user.notification-settings}` | 57 | 0 | 0 | 0 | **2026-08-12T01:22:00Z ~ 2026-08-12T01:36:00Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=chat-service-notification-settings, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=1, topic=user.notification-settings}` | 57 | 0 | 0 | 0 | **2026-08-12T01:22:00Z ~ 2026-08-12T01:36:00Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=chat-service-notification-settings, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=2, topic=user.notification-settings}` | 57 | 0 | 0 | 0 | **2026-08-12T01:22:00Z ~ 2026-08-12T01:36:00Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=db-writer, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=0, topic=chat.messages}` | 57 | 0 | 0 | 0 | **2026-08-12T01:22:00Z ~ 2026-08-12T01:36:00Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=db-writer, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=1, topic=chat.messages}` | 57 | 0 | 0 | 0 | **2026-08-12T01:22:00Z ~ 2026-08-12T01:36:00Z** |
| `websocket_active_users` | `{__name__=websocket_active_users, application=chat-service, cluster=yogurtte-k3s-prod, container=chat-service, instance=10.42.1.55:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-fdcc7c776-kh2dl}` | 57 | 0 | 0 | 0 | **2026-08-12T01:22:00Z ~ 2026-08-12T01:36:00Z** |

값이 0이던 구간은 굵게 표시했다 — 프로세스가 사라져 시계열이 꺾인 것이 유일한 신호인 장애가 있다.

