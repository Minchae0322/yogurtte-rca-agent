# RCA Report — `scan-1785895500`

| 항목 | 값 |
|---|---|
| 모드 | rca |
| 질문 | 최근 1시간 안에 팔로우 목록이 안 열린다는 제보가 있다. 원인을 조사해줘 |
| 시각 | 2026-08-05T02:40:40.511890400Z |
| provider | claude-cli |
| model | `claude-opus-5` · turns 1 |
| prompt | `.\prompts\system-prompt.md` |
| tokens | in 68544 (cacheRead 23,449 · cacheCreate 45,093) / out 7716 · cost $0.6556 |
| elapsed | total 137569ms (tempo 805 · loki 277 · mimir 1072 · assemble 76 · llm 120061) |

## 탐색 (Triage)

| 항목 | 값 |
|---|---|
| 시간창 해석 | 상대 표현 '최근 1시간' |
| 시간창 확신도 | EXACT |
| 스윕 창 | 2026-08-05T01:40:03.939080900Z ~ 2026-08-05T02:40:03.939080900Z |
| 좁힌 창 | 2026-08-05T02:05:00Z ~ 2026-08-05T02:20:00Z |
| 대상 | auth-service |
| traceId | 6a729bd0cf63e6ea421b273667e35a3f |
| 트레이스 후보 | 3건 |
| 장애 후보 | 8건 · 선택 INC-3, INC-5 |
| 계획 파싱 | 성공 |
| 스윕 컨텍스트 | **후보만 — 원본 제외 (B)** |
| prompt | `.\prompts\triage-prompt.md` |
| tokens | in 32790 / out 1483 · cost $0.1694 |
| chars | 컨텍스트 2,927 + 프롬프트 1,399 = **4,326** |
| elapsed | survey 2821ms · llm 33577ms |

**선정 이유**: 질문의 증상(팔로우 목록이 안 열림)과 엔드포인트·시각이 모두 맞는 유일한 신호가 INC-5이고, INC-3은 같은 서비스·같은 시각대의 로그 지문이라 같은 장애의 다른 면일 가능성이 높아 함께 고른다.

**근거**

- INC-5: auth-service http get /user/{userid}/following 3,231ms (slow 채널, 2026-08-05T02:11:28.128Z~02:11:31.359Z) — 질문의 '팔로우 목록'과 엔드포인트가 정확히 일치
- INC-5는 error 채널이 아닌 slow 채널에만 걸림 — 에러 없이 지연만으로 화면이 안 열리는 전형적 형태
- INC-3: auth-service ERROR/WARN 7건 (02:10:00Z~02:15:00Z) — 이 창 전체에서 로그 신호가 가장 많고, INC-5 지연 시각을 포함
- min_over_time(up[5m]) / mongodb_up / kafka_brokers / websocket_active_users 모두 이상 0건 — 프로세스·DB·브로커 다운은 배제되고 지연 원인이 auth-service 내부 또는 그 하류로 좁혀짐

**스윕이 찾은 트레이스** (고른 것은 6a729bd0cf63e6ea421b273667e35a3f)

| traceId | 채널 | root service | root span | ms |
|---|---|---|---|---:|
| `6a72a247a643690f6dd80b177c87c2a9` | error | content-service | http post /feeds | 162 |
| `6a729b55f741850507e3ddfd50cc4d65` | error | content-service | http post /feeds/{feedId}/comments | 119 |
| `6a729bd0cf63e6ea421b273667e35a3f` ←선택 | slow | auth-service | http get /user/{userid}/following | 3231 |

**장애 후보** (코드가 신호를 묶은 것 · 창은 여기서 계산됨)

## INC-1  content-service  |  ERROR/WARN
- 구간: 2026-08-05T02:05:00Z ~ 2026-08-05T02:10:00Z  (LOKI · 집계 해상도만큼 흐림)
- ERROR/WARN 4건 (2026-08-05T02:05:00Z ~ 2026-08-05T02:10:00Z)
- 같은 시각의 다른 후보: INC-2, INC-3  (인과 여부는 판단하지 않았다)

## INC-2  content-service  |  http post /feeds/{feedId}/comments
- 구간: 2026-08-05T02:09:25.627296Z ~ 2026-08-05T02:09:25.746296Z  (TEMPO · 시각 정확)
- content-service http post /feeds/{feedId}/comments 119ms (error 채널)
- traceId: 6a729b55f741850507e3ddfd50cc4d65
- 같은 시각의 다른 후보: INC-1  (인과 여부는 판단하지 않았다)

## INC-3  auth-service  |  ERROR/WARN
- 구간: 2026-08-05T02:10:00Z ~ 2026-08-05T02:15:00Z  (LOKI · 집계 해상도만큼 흐림)
- ERROR/WARN 7건 (2026-08-05T02:10:00Z ~ 2026-08-05T02:15:00Z)
- 같은 시각의 다른 후보: INC-1, INC-4, INC-5  (인과 여부는 판단하지 않았다)

## INC-4  kafka  |  kafka_consumergroup_lag
- 구간: 2026-08-05T02:10:03Z ~ 2026-08-05T02:20:03Z  (MIMIR · 집계 해상도만큼 흐림)
- kafka_consumergroup_lag{consumergroup=notification-processors, partition=3, topic=user.notifications} 0 → 1
- kafka_consumergroup_lag{consumergroup=notification-processors, partition=3, topic=user.notifications} 1 → 0
- 같은 시각의 다른 후보: INC-3, INC-5  (인과 여부는 판단하지 않았다)

## INC-5  auth-service  |  http get /user/{userid}/following
- 구간: 2026-08-05T02:11:28.128454Z ~ 2026-08-05T02:11:31.359454Z  (TEMPO · 시각 정확)
- auth-service http get /user/{userid}/following 3,231ms (slow 채널)
- traceId: 6a729bd0cf63e6ea421b273667e35a3f
- 같은 시각의 다른 후보: INC-3, INC-4  (인과 여부는 판단하지 않았다)

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

- INC-1 — content-service 로그(02:05~02:10Z)로, 팔로우 목록은 auth-service 경로라 서비스가 다르고 지연 시각보다 앞선다.
- INC-2 — POST /feeds/{feedId}/comments 댓글 쓰기 실패 119ms — 조회가 아닌 쓰기 경로이고 증상과 무관하다.
- INC-4 — kafka_consumergroup_lag가 notification-processors에서 0→1→0으로 1건 튀었다 돌아온 수준이라 팔로우 조회 지연을 설명할 크기가 아니다.
- INC-6 — auth-service ERROR/WARN 1건(02:35~02:40Z)으로 INC-5 지연보다 24분 뒤 단발 신호라 시각이 맞지 않는다.
- INC-7 — content-service 로그 4건(02:35~02:40Z)으로 서비스·시각 모두 증상과 어긋난다.
- INC-8 — POST /feeds 피드 생성 실패 162ms — 쓰기 경로이고 창 후반부라 증상과 맞지 않는다.

**보정 기록**

- 창을 후보 [INC-3, INC-5] 의 신호 시각에서 계산했다 (2026-08-05T02:05:00Z ~ 2026-08-05T02:20:00Z)

## 수집 범위 (Coverage)

- **window**: 2026-08-05T02:05:00Z ~ 2026-08-05T02:20:00Z (900s)
- **trace**: 32,868B / 24 spans
- **창 안 후보 트레이스**: 10건 / 32,868B
- **logs**: errwarn=65,841B · traceId=5,080B
- **metrics**: 8 수집 / 110,709B, 누락 [sum(rate(http_server_requests_seconds_count{application="content-service", status="401"}[1m]))]
- **context**: 70,652 chars (+ 시스템 프롬프트 805 chars)

### 토큰 축 (개선 지표)

| 단계 | 총 in | chars | 컨텍스트 토큰 |
|---|---:|---:|---:|
| 탐색 | 32,790 | 4,326 | 3,364 |
| 분석 | 68,544 | 71,457 | 39,118 |
| **합계** | **101,334** | | **42,482** |

- **overheadTokens 29,426 tok — 이 회차에 실측**(1자 프롬프트 프로브, 같은 명령·모델·샌드박스).
  호출마다 한 번씩 얹히므로 단계별로 뺀다. **다른 날 상수를 쓰지 않았다 = `█ 실측`.**
- contextTokens (count_tokens API): 측정 안 됨 — 구독 CLI 경로엔 API 키가 없다
- 비용은 **API 환산 추정치**다. 구독 계정으로 호출하므로 실제 청구액이 아니다.

## 수집 실패/누락

- Metric 'sum(rate(http_server_requests_seconds_count{application="content-service", status="401"}[1m]))' returned no series in this window; skipped.

---

## 1. 원인 후보 랭킹 (최대 3개)

1. **auth-service 팔로우 목록 API의 페이징 파라미터 NPE** — `FollowCondition.limit()`이 null인 `size`를 언박싱해 500 반환 (following·followers 양쪽 동일 결함)
2. **컬렉션 fetch join + 페이징의 인메모리 처리로 인한 응답 지연** — 같은 엔드포인트가 성공하더라도 3231ms 소요 (체감상 "안 열림")
3. **content-service 댓글 INSERT 컬럼 길이 초과 500** — 팔로우 증상과는 무관, 같은 창에서 함께 관측된 별개 결함

---

## 2. 후보별 근거

### 후보 1 — 페이징 파라미터 NPE

**근거**
- `2026-08-05 11:11:42 ... ERROR [traceId=6a729bde3d8a6466f84215b334f93753,spanId=d04f84a3c480e048,userId=1] c.e.t.a.c.e.GlobalExceptionHandler - [api-error] handleAllException`
- `java.lang.NullPointerException: Cannot invoke "java.lang.Integer.intValue()" because "this.size" is null`
- 스택 최상단 앱 프레임: `com.example.toyauth.app.user.controller.dto.FollowCondition$FollowingSearch.limit(FollowCondition.java:25)` ← `FollowRepositoryCustomImpl.findFollowingsByUserId(FollowRepositoryCustomImpl.java:35)` ← `UserService.getFollowingList(UserService.java:307)` ← `UserController.getFollowingList(UserController.java:132)`
- 결과: `[HTTP] GET /api/user/1/following 500 - 123ms`
- 동일 결함이 팔로워 경로에도 존재: `FollowCondition$FollowerSearch.limit(FollowCondition.java:45)` ← `findFollowersByUserId(...:65)` ← `UserService.getFollowerList(...:318)` ← `UserController.getFollowerList(...:142)` → `[HTTP] GET /api/user/1/followers 500 - 140ms` (traceId=6a729bde005188f77d0fe50a11cbb1fa)
- 두 건 모두 02:11:42Z(로그 표기 KST 11:11:42), 서로 다른 워커 스레드(exec-9 / exec-8)에서 발생. `size`가 null이라는 것은 요청에 페이징 파라미터가 없었거나 바인딩되지 않았고 DTO에 기본값이 없다는 뜻이다.

**확신도: 높음**

**반증 데이터**
- 같은 창의 02:11:31Z 요청(traceId=6a729bd0cf63e6ea421b273667e35a3f)은 동일 URI `/api/user/1/following`에서 **status 200**으로 성공했다. 즉 전면 장애가 아니라 `size` 파라미터가 빠진 호출에 한정된 조건부 실패다.
- 수집 창 안에서 확인된 NPE는 총 2건(following 1, followers 1)뿐이다. 제보의 "안 열린다"가 전 사용자 규모인지 판단할 요청량/5xx 비율 메트릭이 없다.

**대기·지연 판정**
- 두 요청 모두 대기 구간 없이 123ms / 140ms 만에 예외로 종료 — 타임아웃 만료 아님(애초에 대기가 존재하지 않음).
- 최종 상태: **실패(HTTP 500)**. 동일 traceId·URI의 재시도 로그나 후속 성공 span은 없음 → **재시도 없음, 요청 폐기**.

---

### 후보 2 — 컬렉션 fetch join 인메모리 페이징에 의한 지연

**근거**
- `2026-08-05 11:11:31 ... WARN [traceId=6a729bd0cf63e6ea421b273667e35a3f,spanId=9cae9498ea17e991,userId=1] org.hibernate.orm.query - HHH90003004: firstResult/maxResults specified with collection fetch; applying in memory`
- 같은 traceId: `WARN ... c.e.t.a.c.f.RequestLoggingFilter - [HTTP-SLOW] GET /api/user/1/following 200 - 3231ms`
- 트레이스 원문 일치: server span `http get /user/{userid}/following`, `durNs: 3231689000` (3.2317초), `status: 200`, `outcome: SUCCESS`. 그중 `secured request` span이 3211934000ns(3.212초)로 시간의 99.4%를 차지 → 필터 체인이 아니라 핸들러 내부 소요.
- 02:12:42Z에도 동일 WARN이 다른 traceId(6a729c1a7c70ab9d80efa1472d7e0337)로 재발 → 일회성이 아님.
- 인프라 병목 배제: `hikaricp_connections_active` / `hikaricp_connections_pending` 은 auth-service 포함 4개 인스턴스 모두 창 전체 0, `up{job="auth-service"}`는 전 구간 1, auth-service GC pause rate 최대 5.42e-5초/초로 무시 가능.

**확신도: 중간** (지연 사실과 인메모리 페이징 WARN의 동일 traceId 동시 발생은 확실하나, auth-service 쪽 JDBC/쿼리 span이 트레이스에 전혀 계측되지 않아 3.2초의 내부 분해가 불가능하다)

**반증 데이터**
- 이 요청의 최종 status는 200/SUCCESS다. 서버 측에서는 실패하지 않았으므로, 이 후보만으로 "안 열린다"는 제보를 설명하려면 클라이언트 측 타임아웃 절단을 가정해야 하는데 그 근거는 데이터에 없다.

**대기·지연 판정**
- 실측 3231ms. 대조할 상한(클라이언트 타임아웃, 인그레스/게이트웨이 타임아웃, Hibernate `jakarta.persistence.query.timeout`) 설정값이 수집 데이터에 없다 → **만료 여부 판정 불가**.
- 최종 상태: **성공(200)**. 재시도·폐기 흔적 없음.

---

### 후보 3 — content-service 댓글 INSERT 컬럼 길이 초과

**근거**
- traceId 6a729b55f741850507e3ddfd50cc4d65: server span `http post /feeds/{feedId}/comments`, `http.url: /api/feeds/145/comments`, `outcome: SERVER_ERROR`, `status: 500`, durNs 119485000(119.5ms).
- 자식 query span: `insert into tb_feed_comment (content,created_at,creator_id,...) values (?,?,...)`, `error: Data truncation: Data too long for column 'content' at row 1`, `status.code: STATUS_CODE_ERROR`, durNs 51335000(51.3ms).
- 호출 그래프에도 동일 집약: `content-service --jdbc--> mysql/content (HikariPool-1) 4회 최대 105.3ms / error: Data truncation... / events: acquired, rollback`.
- 사용자 입력 길이 검증 부재로 DB 제약이 500으로 새어나온 케이스. 입력 검증 실패는 400이어야 한다.

**확신도: 낮음** (결함 자체는 확실하나, 제보된 "팔로우 목록" 증상의 원인은 아니다)

**반증 데이터**
- 서비스도 엔드포인트도 다르다. 호출 그래프에 auth-service → content-service 엣지가 없으므로 팔로우 목록 경로가 이 실패에 의존한다고 볼 근거가 없다.
- 발생 시각 1785895765초 = 02:09:25Z로, NPE(02:11:42Z)·지연(02:11:31Z)과 시간적 상관도 없다.

**대기·지연 판정**
- connection span 105.3ms 중 `acquired` 이벤트가 시작 1.67ms 후 기록됨 → 커넥션 획득 대기는 정상 완료, 풀 획득 타임아웃 만료 아님. `hikaricp_connections_pending`도 content-service 두 인스턴스 모두 창 전체 0으로 일치.
- `acquired`(…641574000) → `rollback`(…730706000) = 89.1ms 후 롤백.
- 최종 상태: **실패 → 롤백**. 재시도 span·재시도 로그 없음 → **폐기(댓글 미저장)**.

---

## 3. 권장 다음 조치

### 이미 발생한 피해: 복구 가능한가

- **후보 1·2 (팔로우 목록)**: 복구 필요 없음. 실패한 것은 읽기 전용 GET이며 쓰기·상태 변경이 없다. 저장된 팔로우 데이터가 손상됐다는 관측값은 전무하다. 코드 수정 후 클라이언트 재요청만으로 복원된다.
- **후보 3 (댓글)**: **서버 측 복구 불가**. traceId 6a729b55f741850507e3ddfd50cc4d65의 `/api/feeds/145/comments` 댓글은 `rollback`으로 폐기됐고 재시도 흔적이 없다. 본문은 요청 바디에만 존재했으며 로그에 원문이 남아 있지 않다 → 작성자에게 재작성을 안내하는 것 외의 복구 경로 없음. 같은 오류가 창 밖에서 몇 건 더 발생했는지는 **데이터 부족**(수집 창이 15분뿐이고 요청 카운터 메트릭이 없음).

### 재발 방지

1. `FollowCondition$FollowingSearch`(FollowCondition.java:25)와 `$FollowerSearch`(:45)의 `size`에 기본값을 부여한다. 두 곳 모두 동일 결함이므로 한쪽만 고치면 나머지 경로가 그대로 남는다. `Integer` 대신 primitive + 기본값, 또는 컨트롤러(`UserController.java:132`, `:142`) 바인딩 단계에서 `defaultValue` 지정.
2. 컬렉션 fetch join과 페이징 병용을 제거한다(HHH90003004). ID 조회 → 컬렉션 로딩 2단계 쿼리 또는 배치 페치로 전환하면 3231ms 인메모리 정렬이 사라진다.
3. `tb_feed_comment.content` 길이 검증을 애플리케이션 계층에 추가해 500이 아닌 400으로 응답하게 한다.
4. auth-service에 JDBC/쿼리 span 계측을 추가한다. 현재 호출 그래프에 auth-service의 DB 엣지가 하나도 없어 3.2초의 내부 분해가 불가능했다.
5. 알람: `/api/user/*/following|followers` 5xx 비율과 p95 지연에 임계 알람. 이번 건은 사용자 제보가 최초 탐지 경로였다.

### 복구 확인

1. `GET /api/user/{id}/following`, `GET /api/user/{id}/followers`를 **size 파라미터 없이** 그리고 있는 상태로 각각 호출해 200 확인 (파라미터 누락이 트리거이므로 이 케이스가 검증의 핵심).
2. Loki에서 `NullPointerException ... this.size is null`, `HHH90003004`, `[HTTP-SLOW]` 세 패턴이 30분간 0건인지 확인.
3. 해당 엔드포인트 p95 지연이 3231ms 대비 유의미하게 하락했는지 확인.
4. 이번 조사에서 결측이던 `sum(rate(http_server_requests_seconds_count{application="content-service", status="401"}[1m]))` 시리즈 존재 여부를 재확인 — 시리즈 자체가 없어 인증 실패 가설을 검증도 배제도 하지 못했다.

---

### 데이터 부족 / 신뢰도 제약

- 질문은 "최근 1시간"이나 실제 조회 창은 15분(02:05~02:20Z)뿐 → 발생 시점·시작 시각·총 영향 건수 미상.
- content-service 401 메트릭 결측 → 인증 계열 원인 가설을 검증 불가. 위 결론은 이 공백을 감안해 후보 3의 확신도를 낮춤에 반영했다.
- 요청 총량·5xx 비율 메트릭 부재 → "몇 %의 사용자가 못 열었는가" 산정 불가. NPE 관측치는 2건이 전부.
- 클라이언트/게이트웨이 타임아웃 설정값 부재 → 3231ms의 만료 여부 판정 불가.
- 배포·변경 이력 부재 → 언제 유입된 결함인지 판정 불가.

추가 수집 권장: 1시간 이상 창의 `http_server_requests_seconds_count{uri=~"/user/.*/following|followers"}` 상태코드별 시계열, auth-service 배포 이력, 인그레스/클라이언트 타임아웃 설정, content-service 401 시리즈 존재 확인.

---

## 관측 증거 (Evidence)

> 이 조사가 실제로 본 값이다. 원본 응답 전체는 `reports/raw/scan-1785895500-*.json`에 있다.

### 호출 그래프 (트레이스에서 추출)

```
content-service --db--> redis  2회  최대 0.6ms  [GET, INFO]
content-service --jdbc--> mysql/content (HikariPool-1)  4회  최대 105.3ms
    error: Data truncation: Data too long for column 'content' at row 1
    events: acquired, rollback
```

### span (duration 상위 15 / 전체 24)

| ms | service | span | 시작 |
|---:|---|---|---|
| 3231.69 | auth-service | `http get /user/{userid}/following` | 2026-08-05T02:11:28.128454Z |
| 3211.93 | auth-service | `secured request` | 2026-08-05T02:11:28.146913Z |
| 119.49 | content-service | `http post /feeds/{feedId}/comments` | 2026-08-05T02:09:25.627296Z |
| 117.35 | content-service | `secured request` | 2026-08-05T02:09:25.627879Z |
| 105.26 | content-service | `connection` | 2026-08-05T02:09:25.639905Z |
| 51.34 | content-service | `query` | 2026-08-05T02:09:25.652279Z |
| 18.03 | auth-service | `security filterchain before` | 2026-08-05T02:11:28.128797Z |
| 2.45 | content-service | `query` | 2026-08-05T02:09:25.643996Z |
| 0.70 | auth-service | `secured request` | 2026-08-05T02:07:10.640415Z |
| 0.61 | content-service | `INFO` | 2026-08-05T02:07:14.751117Z |
| 0.57 | chat-service | `secured request` | 2026-08-05T02:07:11.914506Z |
| 0.56 | content-service | `GET` | 2026-08-05T02:09:25.647544Z |
| 0.50 | content-service | `result-set` | 2026-08-05T02:09:25.646584Z |
| 0.47 | content-service | `secured request` | 2026-08-05T02:07:11.126495Z |
| 0.30 | auth-service | `security filterchain before` | 2026-08-05T02:07:10.640067Z |

### 로그 원문 (60 / 전체 417줄)

전체가 상한을 넘어 ERROR/WARN 줄을 우선 발췌했다. 나머지는 원본 파일에 있다.

```
2026-08-05T02:11:31.238471783Z  [auth-service]  [2m2026-08-05 11:11:31[0;39m [2m[http-nio-8081-exec-6][0;39m [33m WARN [traceId=6a729bd0cf63e6ea421b273667e35a3f,spanId=9cae9498ea17e991,userId=1][0;39m [36morg.hibernate.orm.query[0;39m [2m-[0;39m HHH90003004: firstResult/maxResults specified with collection fetch; applying in memory
2026-08-05T02:11:31.238471783Z  [auth-service]  [2m2026-08-05 11:11:31[0;39m [2m[http-nio-8081-exec-6][0;39m [33m WARN [traceId=6a729bd0cf63e6ea421b273667e35a3f,spanId=9cae9498ea17e991,userId=1][0;39m [36morg.hibernate.orm.query[0;39m [2m-[0;39m HHH90003004: firstResult/maxResults specified with collection fetch; applying in memory
2026-08-05T02:11:31.359609536Z  [auth-service]  [2m2026-08-05 11:11:31[0;39m [2m[http-nio-8081-exec-6][0;39m [33m WARN [traceId=6a729bd0cf63e6ea421b273667e35a3f,spanId=421b273667e35a3f,userId=NONE][0;39m [36mc.e.t.a.c.f.RequestLoggingFilter[0;39m [2m-[0;39m [HTTP-SLOW] GET /api/user/1/following 200 - 3231ms
2026-08-05T02:11:31.359609536Z  [auth-service]  [2m2026-08-05 11:11:31[0;39m [2m[http-nio-8081-exec-6][0;39m [33m WARN [traceId=6a729bd0cf63e6ea421b273667e35a3f,spanId=421b273667e35a3f,userId=NONE][0;39m [36mc.e.t.a.c.f.RequestLoggingFilter[0;39m [2m-[0;39m [HTTP-SLOW] GET /api/user/1/following 200 - 3231ms
2026-08-05T02:11:42.293675403Z  [auth-service]  [2m2026-08-05 11:11:42[0;39m [2m[http-nio-8081-exec-9][0;39m [31mERROR [traceId=6a729bde3d8a6466f84215b334f93753,spanId=d04f84a3c480e048,userId=1][0;39m [36mc.e.t.a.c.e.GlobalExceptionHandler[0;39m [2m-[0;39m [api-error] handleAllException
2026-08-05T02:11:42.293697619Z  [auth-service]  java.lang.NullPointerException: Cannot invoke "java.lang.Integer.intValue()" because "this.size" is null
2026-08-05T02:11:42.293702202Z  [auth-service]  at com.example.toyauth.app.user.controller.dto.FollowCondition$FollowingSearch.limit(FollowCondition.java:25)
2026-08-05T02:11:42.293707068Z  [auth-service]  at com.example.toyauth.app.follow.repository.querydsl.impl.FollowRepositoryCustomImpl.findFollowingsByUserId(FollowRepositoryCustomImpl.java:35)
2026-08-05T02:11:42.293731596Z  [auth-service]  at org.springframework.aop.support.AopUtils.invokeJoinpointUsingReflection(AopUtils.java:352)
2026-08-05T02:11:42.293736069Z  [auth-service]  at org.springframework.aop.framework.ReflectiveMethodInvocation.invokeJoinpoint(ReflectiveMethodInvocation.java:196)
2026-08-05T02:11:42.293742258Z  [auth-service]  at org.springframework.aop.framework.ReflectiveMethodInvocation.proceed(ReflectiveMethodInvocation.java:163)
2026-08-05T02:11:42.293756441Z  [auth-service]  at org.springframework.aop.framework.CglibAopProxy$CglibMethodInvocation.proceed(CglibAopProxy.java:765)
2026-08-05T02:11:42.293759699Z  [auth-service]  at org.springframework.dao.support.PersistenceExceptionTranslationInterceptor.invoke(PersistenceExceptionTranslationInterceptor.java:137)
2026-08-05T02:11:42.293762443Z  [auth-service]  at org.springframework.aop.framework.ReflectiveMethodInvocation.proceed(ReflectiveMethodInvocation.java:184)
2026-08-05T02:11:42.293765226Z  [auth-service]  at org.springframework.aop.framework.CglibAopProxy$CglibMethodInvocation.proceed(CglibAopProxy.java:765)
2026-08-05T02:11:42.293767998Z  [auth-service]  at org.springframework.aop.framework.CglibAopProxy$DynamicAdvisedInterceptor.intercept(CglibAopProxy.java:717)
2026-08-05T02:11:42.293794084Z  [auth-service]  at org.springframework.aop.support.AopUtils.invokeJoinpointUsingReflection(AopUtils.java:352)
2026-08-05T02:11:42.293796594Z  [auth-service]  at org.springframework.data.repository.core.support.RepositoryMethodInvoker$RepositoryFragmentMethodInvoker.lambda$new$0(RepositoryMethodInvoker.java:277)
2026-08-05T02:11:42.293799022Z  [auth-service]  at org.springframework.data.repository.core.support.RepositoryMethodInvoker.doInvoke(RepositoryMethodInvoker.java:170)
2026-08-05T02:11:42.293801526Z  [auth-service]  at org.springframework.data.repository.core.support.RepositoryMethodInvoker.invoke(RepositoryMethodInvoker.java:158)
2026-08-05T02:11:42.293804038Z  [auth-service]  at org.springframework.data.repository.core.support.RepositoryComposition$RepositoryFragments.invoke(RepositoryComposition.java:516)
2026-08-05T02:11:42.293806336Z  [auth-service]  at org.springframework.data.repository.core.support.RepositoryComposition.invoke(RepositoryComposition.java:285)
2026-08-05T02:11:42.293809326Z  [auth-service]  at org.springframework.data.repository.core.support.RepositoryFactorySupport$ImplementationMethodExecutionInterceptor.invoke(RepositoryFactorySupport.java:628)
2026-08-05T02:11:42.293811778Z  [auth-service]  at org.springframework.aop.framework.ReflectiveMethodInvocation.proceed(ReflectiveMethodInvocation.java:184)
2026-08-05T02:11:42.293814590Z  [auth-service]  at org.springframework.data.repository.core.support.QueryExecutorMethodInterceptor.doInvoke(QueryExecutorMethodInterceptor.java:168)
2026-08-05T02:11:42.293817157Z  [auth-service]  at org.springframework.data.repository.core.support.QueryExecutorMethodInterceptor.invoke(QueryExecutorMethodInterceptor.java:143)
2026-08-05T02:11:42.293819603Z  [auth-service]  at org.springframework.aop.framework.ReflectiveMethodInvocation.proceed(ReflectiveMethodInvocation.java:184)
2026-08-05T02:11:42.293822013Z  [auth-service]  at org.springframework.data.projection.DefaultMethodInvokingMethodInterceptor.invoke(DefaultMethodInvokingMethodInterceptor.java:70)
2026-08-05T02:11:42.293824359Z  [auth-service]  at org.springframework.aop.framework.ReflectiveMethodInvocation.proceed(ReflectiveMethodInvocation.java:184)
2026-08-05T02:11:42.293826643Z  [auth-service]  at org.springframework.transaction.interceptor.TransactionInterceptor$1.proceedWithInvocation(TransactionInterceptor.java:123)
2026-08-05T02:11:42.293832946Z  [auth-service]  at org.springframework.transaction.interceptor.TransactionAspectSupport.invokeWithinTransaction(TransactionAspectSupport.java:385)
2026-08-05T02:11:42.293835972Z  [auth-service]  at org.springframework.transaction.interceptor.TransactionInterceptor.invoke(TransactionInterceptor.java:119)
2026-08-05T02:11:42.293855235Z  [auth-service]  at org.springframework.aop.framework.ReflectiveMethodInvocation.proceed(ReflectiveMethodInvocation.java:184)
2026-08-05T02:11:42.293857895Z  [auth-service]  at org.springframework.dao.support.PersistenceExceptionTranslationInterceptor.invoke(PersistenceExceptionTranslationInterceptor.java:137)
2026-08-05T02:11:42.293860336Z  [auth-service]  at org.springframework.aop.framework.ReflectiveMethodInvocation.proceed(ReflectiveMethodInvocation.java:184)
2026-08-05T02:11:42.293863311Z  [auth-service]  at org.springframework.data.jpa.repository.support.CrudMethodMetadataPostProcessor$CrudMethodMetadataPopulatingMethodInterceptor.invoke(CrudMethodMetadataPostProcessor.java:164)
2026-08-05T02:11:42.293866151Z  [auth-service]  at org.springframework.aop.framework.ReflectiveMethodInvocation.proceed(ReflectiveMethodInvocation.java:184)
2026-08-05T02:11:42.293868640Z  [auth-service]  at org.springframework.aop.interceptor.ExposeInvocationInterceptor.invoke(ExposeInvocationInterceptor.java:97)
2026-08-05T02:11:42.293871031Z  [auth-service]  at org.springframework.aop.framework.ReflectiveMethodInvocation.proceed(ReflectiveMethodInvocation.java:184)
2026-08-05T02:11:42.293873471Z  [auth-service]  at org.springframework.aop.framework.JdkDynamicAopProxy.invoke(JdkDynamicAopProxy.java:249)
2026-08-05T02:11:42.293878628Z  [auth-service]  at com.example.toyauth.app.user.service.UserService.getFollowingList(UserService.java:307)
2026-08-05T02:11:42.293891255Z  [auth-service]  at org.springframework.aop.support.AopUtils.invokeJoinpointUsingReflection(AopUtils.java:352)
2026-08-05T02:11:42.293893706Z  [auth-service]  at org.springframework.aop.framework.ReflectiveMethodInvocation.invokeJoinpoint(ReflectiveMethodInvocation.java:196)
2026-08-05T02:11:42.293896134Z  [auth-service]  at org.springframework.aop.framework.ReflectiveMethodInvocation.proceed(ReflectiveMethodInvocation.java:163)
2026-08-05T02:11:42.293898775Z  [auth-service]  at org.springframework.aop.framework.CglibAopProxy$CglibMethodInvocation.proceed(CglibAopProxy.java:765)
2026-08-05T02:11:42.293901209Z  [auth-service]  at org.springframework.transaction.interceptor.TransactionInterceptor$1.proceedWithInvocation(TransactionInterceptor.java:123)
2026-08-05T02:11:42.293903730Z  [auth-service]  at org.springframework.transaction.interceptor.TransactionAspectSupport.invokeWithinTransaction(TransactionAspectSupport.java:385)
2026-08-05T02:11:42.293906267Z  [auth-service]  at org.springframework.transaction.interceptor.TransactionInterceptor.invoke(TransactionInterceptor.java:119)
2026-08-05T02:11:42.293908656Z  [auth-service]  at org.springframework.aop.framework.ReflectiveMethodInvocation.proceed(ReflectiveMethodInvocation.java:184)
2026-08-05T02:11:42.294024333Z  [auth-service]  at org.springframework.security.web.access.ExceptionTranslationFilter.doFilter(ExceptionTranslationFilter.java:126)
2026-08-05T02:11:42.294027055Z  [auth-service]  at org.springframework.security.web.access.ExceptionTranslationFilter.doFilter(ExceptionTranslationFilter.java:120)
2026-08-05T02:11:42.297476073Z  [auth-service]  [2m2026-08-05 11:11:42[0;39m [2m[http-nio-8081-exec-9][0;39m [31mERROR [traceId=6a729bde3d8a6466f84215b334f93753,spanId=f84215b334f93753,userId=NONE][0;39m [36mc.e.t.a.c.f.RequestLoggingFilter[0;39m [2m-[0;39m [HTTP] GET /api/user/1/following 500 - 123ms
2026-08-05T02:11:42.508380641Z  [auth-service]  [2m2026-08-05 11:11:42[0;39m [2m[http-nio-8081-exec-8][0;39m [31mERROR [traceId=6a729bde005188f77d0fe50a11cbb1fa,spanId=d915dd3e98880a06,userId=1][0;39m [36mc.e.t.a.c.e.GlobalExceptionHandler[0;39m [2m-[0;39m [api-error] handleAllException
2026-08-05T02:11:42.508413531Z  [auth-service]  java.lang.NullPointerException: Cannot invoke "java.lang.Integer.intValue()" because "this.size" is null
2026-08-05T02:11:42.508447768Z  [auth-service]  at org.springframework.dao.support.PersistenceExceptionTranslationInterceptor.invoke(PersistenceExceptionTranslationInterceptor.java:137)
2026-08-05T02:11:42.508546855Z  [auth-service]  at org.springframework.dao.support.PersistenceExceptionTranslationInterceptor.invoke(PersistenceExceptionTranslationInterceptor.java:137)
2026-08-05T02:11:42.508790204Z  [auth-service]  at org.springframework.security.web.access.ExceptionTranslationFilter.doFilter(ExceptionTranslationFilter.java:126)
2026-08-05T02:11:42.508808435Z  [auth-service]  at org.springframework.security.web.access.ExceptionTranslationFilter.doFilter(ExceptionTranslationFilter.java:120)
2026-08-05T02:11:42.533776019Z  [auth-service]  [2m2026-08-05 11:11:42[0;39m [2m[http-nio-8081-exec-8][0;39m [31mERROR [traceId=6a729bde005188f77d0fe50a11cbb1fa,spanId=7d0fe50a11cbb1fa,userId=NONE][0;39m [36mc.e.t.a.c.f.RequestLoggingFilter[0;39m [2m-[0;39m [HTTP] GET /api/user/1/followers 500 - 140ms
2026-08-05T02:12:42.949789158Z  [auth-service]  [2m2026-08-05 11:12:42[0;39m [2m[http-nio-8081-exec-1][0;39m [33m WARN [traceId=6a729c1a7c70ab9d80efa1472d7e0337,spanId=561a2224d528885f,userId=1][0;39m [36morg.hibernate.orm.query[0;39m [2m-[0;39m HHH90003004: firstResult/maxResults specified with collection fetch; applying in memory
```

### 메트릭 시계열

| 쿼리 | series | 점 | min | max | last | 값이 0이던 구간 |
|---|---|---:|---:|---:|---:|---|
| `hikaricp_connections_active` | `{__name__=hikaricp_connections_active, application=auth-service, cluster=yogurtte-k3s-prod, container=auth-service, instance=10.42.1.46:8090, job=auth-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=auth-service-5999bb9f5c-hmgp9, pool=HikariPool-1, service=auth-service}` | 61 | 0 | 0 | 0 | **2026-08-05T02:05:00Z ~ 2026-08-05T02:20:00Z** |
| `hikaricp_connections_active` | `{__name__=hikaricp_connections_active, application=chat-service, cluster=yogurtte-k3s-prod, container=chat-service, instance=10.42.1.47:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-fdcc7c776-xf4sv, pool=HikariPool-1}` | 61 | 0 | 0 | 0 | **2026-08-05T02:05:00Z ~ 2026-08-05T02:20:00Z** |
| `hikaricp_connections_active` | `{__name__=hikaricp_connections_active, application=content-service, cluster=yogurtte-k3s-prod, container=content-service, instance=10.42.1.43:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-6995bb7d94-h2f6n, pool=HikariPool-1}` | 61 | 0 | 0 | 0 | **2026-08-05T02:05:00Z ~ 2026-08-05T02:20:00Z** |
| `hikaricp_connections_active` | `{__name__=hikaricp_connections_active, application=content-service, cluster=yogurtte-k3s-prod, container=content-service, instance=10.42.3.42:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-6995bb7d94-nq9l2, pool=HikariPool-1}` | 61 | 0 | 0 | 0 | **2026-08-05T02:05:00Z ~ 2026-08-05T02:20:00Z** |
| `hikaricp_connections_pending` | `{__name__=hikaricp_connections_pending, application=auth-service, cluster=yogurtte-k3s-prod, container=auth-service, instance=10.42.1.46:8090, job=auth-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=auth-service-5999bb9f5c-hmgp9, pool=HikariPool-1, service=auth-service}` | 61 | 0 | 0 | 0 | **2026-08-05T02:05:00Z ~ 2026-08-05T02:20:00Z** |
| `hikaricp_connections_pending` | `{__name__=hikaricp_connections_pending, application=chat-service, cluster=yogurtte-k3s-prod, container=chat-service, instance=10.42.1.47:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-fdcc7c776-xf4sv, pool=HikariPool-1}` | 61 | 0 | 0 | 0 | **2026-08-05T02:05:00Z ~ 2026-08-05T02:20:00Z** |
| `hikaricp_connections_pending` | `{__name__=hikaricp_connections_pending, application=content-service, cluster=yogurtte-k3s-prod, container=content-service, instance=10.42.1.43:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-6995bb7d94-h2f6n, pool=HikariPool-1}` | 61 | 0 | 0 | 0 | **2026-08-05T02:05:00Z ~ 2026-08-05T02:20:00Z** |
| `hikaricp_connections_pending` | `{__name__=hikaricp_connections_pending, application=content-service, cluster=yogurtte-k3s-prod, container=content-service, instance=10.42.3.42:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-6995bb7d94-nq9l2, pool=HikariPool-1}` | 61 | 0 | 0 | 0 | **2026-08-05T02:05:00Z ~ 2026-08-05T02:20:00Z** |
| `rate(jvm_gc_pause_seconds_sum[5m])` | `{action=end of major GC, application=chat-service, cause=Allocation Failure, cluster=yogurtte-k3s-prod, container=chat-service, gc=MarkSweepCompact, instance=10.42.1.47:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-fdcc7c776-xf4sv}` | 61 | 0 | 0 | 0 | **2026-08-05T02:05:00Z ~ 2026-08-05T02:20:00Z** |
| `rate(jvm_gc_pause_seconds_sum[5m])` | `{action=end of minor GC, application=auth-service, cause=G1 Evacuation Pause, cluster=yogurtte-k3s-prod, container=auth-service, gc=G1 Young Generation, instance=10.42.1.46:8090, job=auth-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=auth-service-5999bb9f5c-hmgp9, service=auth-service}` | 61 | 0 | 0.000 | 0 | **2026-08-05T02:05:00Z ~ 2026-08-05T02:11:30Z, 2026-08-05T02:15:45Z ~ 2026-08-05T02:20:00Z** |
| `rate(jvm_gc_pause_seconds_sum[5m])` | `{action=end of minor GC, application=chat-service, cause=Allocation Failure, cluster=yogurtte-k3s-prod, container=chat-service, gc=Copy, instance=10.42.1.47:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-fdcc7c776-xf4sv}` | 61 | 0.000 | 0.001 | 0.000 | — |
| `rate(jvm_gc_pause_seconds_sum[5m])` | `{action=end of minor GC, application=content-service, cause=Allocation Failure, cluster=yogurtte-k3s-prod, container=content-service, gc=Copy, instance=10.42.1.43:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-6995bb7d94-h2f6n}` | 61 | 0 | 0.000 | 0 | **2026-08-05T02:06:00Z ~ 2026-08-05T02:12:45Z, 2026-08-05T02:17:00Z ~ 2026-08-05T02:20:00Z** |
| `rate(jvm_gc_pause_seconds_sum[5m])` | `{action=end of minor GC, application=content-service, cause=Allocation Failure, cluster=yogurtte-k3s-prod, container=content-service, gc=Copy, instance=10.42.3.42:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-6995bb7d94-nq9l2}` | 61 | 0 | 0.000 | 0 | **2026-08-05T02:05:00Z ~ 2026-08-05T02:10:30Z, 2026-08-05T02:14:45Z ~ 2026-08-05T02:20:00Z** |
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
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=chat-service-fcm-tokens, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=0, topic=user.fcm-tokens}` | 61 | 0 | 0 | 0 | **2026-08-05T02:05:00Z ~ 2026-08-05T02:20:00Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=chat-service-fcm-tokens, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=1, topic=user.fcm-tokens}` | 61 | 0 | 0 | 0 | **2026-08-05T02:05:00Z ~ 2026-08-05T02:20:00Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=chat-service-fcm-tokens, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=2, topic=user.fcm-tokens}` | 61 | 0 | 0 | 0 | **2026-08-05T02:05:00Z ~ 2026-08-05T02:20:00Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=chat-service-notification-settings, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=0, topic=user.notification-settings}` | 61 | 0 | 0 | 0 | **2026-08-05T02:05:00Z ~ 2026-08-05T02:20:00Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=chat-service-notification-settings, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=1, topic=user.notification-settings}` | 61 | 0 | 0 | 0 | **2026-08-05T02:05:00Z ~ 2026-08-05T02:20:00Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=chat-service-notification-settings, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=2, topic=user.notification-settings}` | 61 | 0 | 0 | 0 | **2026-08-05T02:05:00Z ~ 2026-08-05T02:20:00Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=db-writer, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=0, topic=chat.messages}` | 61 | 0 | 0 | 0 | **2026-08-05T02:05:00Z ~ 2026-08-05T02:20:00Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=db-writer, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=1, topic=chat.messages}` | 61 | 0 | 0 | 0 | **2026-08-05T02:05:00Z ~ 2026-08-05T02:20:00Z** |
| `websocket_active_users` | `{__name__=websocket_active_users, application=chat-service, cluster=yogurtte-k3s-prod, container=chat-service, instance=10.42.1.47:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-fdcc7c776-xf4sv}` | 61 | 0 | 0 | 0 | **2026-08-05T02:05:00Z ~ 2026-08-05T02:20:00Z** |

값이 0이던 구간은 굵게 표시했다 — 프로세스가 사라져 시계열이 꺾인 것이 유일한 신호인 장애가 있다.

