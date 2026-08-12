# RCA Report — `scan-1786498260`

| 항목 | 값 |
|---|---|
| 모드 | rca |
| 질문 | 최근 1시간 안에 팔로우 목록이 안 열린다는 제보가 있다. 원인을 조사해줘 |
| 시각 | 2026-08-12T05:05:55.893658200Z |
| provider | claude-cli |
| model | `claude-opus-5` · turns 1 |
| prompt | `.\prompts\system-prompt.md` |
| tokens | in 76846 (cacheRead 23,796 · cacheCreate 53,048) / out 6042 · cost $0.6934 |
| elapsed | total 110018ms (tempo 787 · loki 294 · mimir 567 · assemble 108 · llm 98409) |

## 탐색 (Triage)

| 항목 | 값 |
|---|---|
| 시간창 해석 | 명시적 from/to |
| 시간창 확신도 | EXACT |
| 스윕 창 | 2026-08-12T00:36:21Z ~ 2026-08-12T01:36:21Z |
| 좁힌 창 | 2026-08-12T01:31:00Z ~ 2026-08-12T01:36:00Z |
| 대상 | auth-service |
| traceId | 6a7bcdbd052a495e0b6b5c4c9b4c3a61 |
| 트레이스 후보 | 3건 |
| 장애 후보 | 7건 · 선택 INC-4, INC-5 |
| 계획 파싱 | 성공 |
| 스윕 컨텍스트 | **후보만 — 원본 제외 (B)** |
| prompt | `.\prompts\triage-prompt.md` |
| tokens | in 33529 / out 2482 · cost $0.1985 |
| chars | 컨텍스트 3,084 + 프롬프트 1,399 = **4,483** |
| elapsed | survey 1802ms · llm 44295ms |

**선정 이유**: 팔로우 목록은 사용자 관계 조회 경로이고, 조회 창 후반 auth-service에서만 NPE가 60초 주기로 반복 재현되며 해당 엔드포인트 트레이스는 0건이라 로그에만 남는 읽기 경로 실패로 판단했다.

**근거**

- INC-4: auth-service java.lang.NullPointerException 2건이 2026-08-12T01:32:00Z~01:36:00Z 구간에서 평균 60초 간격으로 4회 반복 — 단발 사고가 아니라 조회할 때마다 재현되는 패턴
- INC-4: 같은 서비스 ERROR/WARN 2건도 01:31:00Z~01:35:00Z 4회 반복으로 로그 채널에서만 잡힘
- 무신호가 신호: Tempo 에러 2건·지연 1건 모두 /feeds, /feeds/{feedId}/comments, /files/upload뿐이고 팔로우/관계 조회 엔드포인트 트레이스는 에러·지연 양쪽 다 0건 — NPE로 핸들러가 죽거나 빈 목록을 정상 응답으로 내보내 트레이스에 이상이 남지 않는 형태로 의심
- INC-5: auth-service http post /files/upload 3,063ms (2026-08-12T01:34:53.777Z) — INC-4 반복 구간 한가운데 발생한 같은 서비스 지연 지문이라 스레드/커넥션 고갈 여부 확인용으로 동반 선택
- 인프라 채널은 전부 정상: up, mongodb_up, kafka_brokers, kafka_consumergroup_lag, websocket_active_users 모두 이상 0건 → 파드 다운·DB 단절·Kafka 지연이 아닌 애플리케이션 레벨 결함

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

- INC-1 — chat-service ERROR/WARN 1건 단발이고 팔로우 목록은 채팅 경로를 타지 않으며, websocket_active_users 이상 0건으로 chat 쪽 사용자 영향 징후가 없다.
- INC-2 — content-service DataException은 01:22~01:27 구간의 쓰기 데이터 형식 오류(컬럼 길이·타입 계열)로, 목록 조회가 안 열리는 증상과 지문이 맞지 않는다.
- INC-3 — POST /feeds/{feedId}/comments 208ms 에러 트레이스로 INC-2와 같은 댓글 작성 실패 사건이며 조회 경로가 아니다.
- INC-6 — SQLIntegrityConstraintViolationException은 중복 키·FK 위반으로 INSERT/UPDATE에서만 나는 예외라 조회 실패 원인이 될 수 없다.
- INC-7 — POST /feeds 86ms 에러 트레이스로 INC-6과 같은 피드 작성 실패 사건이고 팔로우 목록과 무관하다.

**보정 기록**

- 창을 후보 [INC-4, INC-5] 의 신호 시각에서 계산했다 (2026-08-12T01:31:00Z ~ 2026-08-12T01:36:00Z)

## 수집 범위 (Coverage)

- **window**: 2026-08-12T01:31:00Z ~ 2026-08-12T01:36:00Z (300s)
- **trace**: 55,865B / 64 spans
- **창 안 후보 트레이스**: 4건 / 55,865B
- **logs**: errwarn=66,210B · traceId=5,869B
- **metrics**: 8 수집 / 54,688B, 누락 [sum(rate(http_server_requests_seconds_count{application="content-service", status="401"}[1m]))]
- **context**: 84,430 chars (+ 시스템 프롬프트 805 chars)

### 토큰 축 (개선 지표)

| 단계 | 총 in | chars | 컨텍스트 토큰 |
|---|---:|---:|---:|
| 탐색 | 33,529 | 4,483 | 3,519 |
| 분석 | 76,846 | 85,235 | 46,836 |
| **합계** | **110,375** | | **50,355** |

- **overheadTokens 30,010 tok — 이 회차에 실측**(1자 프롬프트 프로브, 같은 명령·모델·샌드박스).
  호출마다 한 번씩 얹히므로 단계별로 뺀다. **다른 날 상수를 쓰지 않았다 = `█ 실측`.**
- contextTokens (count_tokens API): 측정 안 됨 — 구독 CLI 경로엔 API 키가 없다
- 비용은 **API 환산 추정치**다. 구독 계정으로 호출하므로 실제 청구액이 아니다.

## 수집 실패/누락

- Metric 'sum(rate(http_server_requests_seconds_count{application="content-service", status="401"}[1m]))' returned no series in this window; skipped.

---

## 1. 원인 후보 랭킹 (최대 3개)

1. **auth-service `FollowCondition`의 `size`가 null인 채 `limit()` 호출 → NPE → 팔로우/팔로워 목록 API 500** (직접 원인)
2. **동일 API의 컬렉션 fetch + 페이징 인메모리 처리로 인한 지연(2983ms)** — 500과 별개로 "안 열린다" 제보를 만들 수 있는 2차 요인
3. **content-service `POST /feeds` 500 (Duplicate entry)** — 같은 창에 있었으나 팔로우 목록과 무관, 사실상 배제

---

## 2. 후보별 근거

### 후보 1 — `FollowCondition.size` null NPE

**근거**
- `2026-08-12 10:32:07 ... ERROR [traceId=6a7bcd177ec7dcbafab90613c89ef91a,userId=1] GlobalExceptionHandler - [api-error] handleAllException`
- `java.lang.NullPointerException: Cannot invoke "java.lang.Integer.intValue()" because "this.size" is null`
- 스택 최상단 앱 프레임: `FollowCondition$FollowingSearch.limit(FollowCondition.java:25)` → `FollowRepositoryCustomImpl.findFollowingsByUserId(FollowRepositoryCustomImpl.java:35)` → `UserService.getFollowingList(UserService.java:307)` → `UserController.getFollowingList(UserController.java:132)`
- 결과 라인: `[HTTP] GET /api/user/1/following 500 - 99ms`
- 팔로워 쪽도 **동일 결함의 쌍둥이 경로**: `FollowCondition$FollowerSearch.limit(FollowCondition.java:45)` → `findFollowersByUserId(...:65)` → `getFollowerList(UserService.java:318)` → `[HTTP] GET /api/user/1/followers 500 - 67ms` (traceId `6a7bcd1719fe4d3b8126248de7af6455`)
- 즉 `size` 미지정 요청이 들어오면 `following`·`followers` 두 엔드포인트가 모두 500. 서비스·인프라 장애가 아니라 **요청 파라미터 바인딩 결함**이다.

**확신도**: 높음 (단, 아래 공백 때문에 "전 사용자 상시 실패"로까지는 단정 못 함 — 후술)

**반증 데이터**
- 같은 창 **10:31:56에는 `GET /api/user/1/following 200`** 이 존재한다. 즉 이 엔드포인트가 100% 죽은 게 아니라, `size` 파라미터가 실린 요청은 성공한다. → 결함은 상시가 아니라 **`size` 누락 요청에 조건부**로 발동.
- auth-service `up=1` 전 구간, `hikaricp_connections_active/pending = 0` 전 구간, GC pause 4.58e-5 s/s 수준 → 자원 고갈·재시작 계열 원인은 반증됨. (active=0은 15초 스크레이프가 99ms 요청을 놓친 것으로 보이며, 이 후보를 반증하지 않는다.)

**대기·지연 구간 판정**
- 실측: 99ms(following), 67ms(followers). 대조할 타임아웃 설정값이 수집 데이터에 **없음** → **만료 여부 판정 불가**. 다만 두 값 모두 초 단위에 한참 못 미치므로 타임아웃 만료가 아닌 **즉시 예외 반환**이다.
- 최종 상태: **실패(HTTP 500)**. `GlobalExceptionHandler`가 잡아 응답했고, **재시도 흔적 없음 / 폐기됨**. 읽기 요청이므로 DB 상태 변경 없음.

---

### 후보 2 — 컬렉션 fetch + 인메모리 페이징 지연

**근거**
- `10:31:56 WARN [traceId=6a7bcd0988d2fa47b225dfd17cd82615,userId=1] org.hibernate.orm.query - HHH90003004: firstResult/maxResults specified with collection fetch; applying in memory`
- 같은 traceId 결과: `WARN ... [HTTP-SLOW] GET /api/user/1/following 200 - 2983ms`
- `10:33:07`에 동일 WARN 1건 더 (traceId `6a7bcd53867ffb8faf3127e898919e0b`) → 산발이 아니라 **이 엔드포인트의 구조적 특성**
- 의미: 페이징이 SQL LIMIT으로 내려가지 않고 전체 결과를 메모리로 올린 뒤 자른다. 대상 사용자의 팔로우 행 수에 비례해 응답이 늘어나며, 3초는 클라이언트 체감상 "안 열린다"와 구분되지 않는다.

**확신도**: 중간 (지연 사실은 확정. 이것이 제보의 원인인지 후보 1이 원인인지 구분할 클라이언트 측 데이터가 없음)

**반증 데이터**
- 해당 요청은 **200으로 성공**했다. 즉 이것만으로는 "안 열린다"의 완전한 설명이 못 되고, 실패를 만든 건 후보 1이다.

**대기·지연 구간 판정**
- 실측 2983ms. 대조할 타임아웃 설정값(서버 응답 타임아웃·게이트웨이·클라이언트) **수집 안 됨** → **만료 여부 판정 불가**.
- 최종 상태: **성공(HTTP 200)**. 재시도·폐기 없음.
- `10:33:07` WARN 건은 대응하는 `[HTTP]` 결과 라인이 수집 범위에 없다 → 해당 요청의 **최종 상태 판정 불가**.

---

### 후보 3 — content-service `POST /feeds` 500 (배제 대상)

**근거**
- traceId `6a7bcdccfb0b88f17f425d5e2fb9908f`: `insert into tb_feed_hashtags ...` span이 `error: Duplicate entry '166-179' for key 'tb_feed_hashtags.uk_feed_hashtag'`, `STATUS_CODE_ERROR`
- `connection` span events: `acquired`(+189µs) → **`rollback`** (67.5ms 후), 루트 `http post /feeds` = `outcome: SERVER_ERROR, status: 500`
- 정상 대조군 traceId `6a7bcdc1f9b3ee917fcaafd6360e485b`은 동일 경로에서 `commit`, `200`

**확신도**: 낮음 (팔로우 목록 원인으로서는 사실상 0. 별개 결함으로는 확신도 높음)

**반증 데이터**
- 서비스가 다르고(content-service vs auth-service), 엔드포인트가 다르며(`POST /api/feeds` vs `GET /api/user/{id}/following`), 호출 그래프상 **content-service ↔ auth-service 엣지가 존재하지 않는다**. 팔로우 목록 실패와 인과 경로가 없다.

**대기·지연 구간 판정**
- 커넥션 획득 대기 실측 189µs (`connection` 시작 1786498508528689000 → `acquired` 1786498508528878000), `hikaricp_connections_pending` 전 구간 0. Hikari `connectionTimeout` 설정값은 수집 데이터에 없으나, 대기 자체가 마이크로초 단위라 **만료 아님**.
- 최종 상태: **실패 → 트랜잭션 롤백 → 폐기**. 재시도 흔적 없음. 해당 피드는 생성되지 않았다.

---

### 수집 공백이 확신도에 미치는 영향
- `content-service` 401 메트릭 시리즈 없음 → 인증 계열 원인 배제를 메트릭으로 확인 못 함. 다만 실패 로그의 예외가 NPE로 명확해 후보 1 판정에 영향은 작다.
- **팔로우 API에 대한 트레이스가 0건**이다. 수집된 4건은 `/files/upload` 2건, `/feeds` 2건뿐이라 호출 그래프에 auth-service→DB 엣지조차 없다. 실패 요청의 span 단위 분해가 불가능해, 결론은 **전적으로 Loki 로그에 의존**한다 — 그만큼 후보 1의 확신도를 "높음"에 두되 "확정"으로는 올리지 않았다.
- 영향 범위(실패 요청 수, 영향 사용자 수)를 셀 메트릭이 없다. 관측된 실패는 **userId=1, 2건**뿐이다.

---

## 3. 권장 다음 조치

### 이미 발생한 피해: 복구 가능한가
- **팔로우 목록 500 (2건)**: **복구 가능**. 읽기 전용 조회이며 롤백·부분 쓰기 흔적이 없다. 수정 배포 후 클라이언트 재요청만으로 완전 복구된다. 별도 데이터 보정 불필요.
- **`POST /feeds` 500 1건 (별개 건)**: **자동 복구 불가**. `rollback` 이벤트로 트랜잭션이 통째로 되돌아가 피드가 생성되지 않았고, 재시도 로그가 없다. 사용자가 재게시해야 한다. 단, 트레이스의 `S3 파일 업로드 성공: uploads/2026/08/12/20260812103454_170bceb9.png` 로그로 보아 **업로드된 S3 객체는 DB 롤백과 무관하게 남는다** → 고아 객체 정리 대상.

### 재발 방지
1. `FollowCondition$FollowingSearch` / `FollowerSearch` **양쪽 모두**에 `size`(및 동일 위험이 있는 `page`) 기본값을 넣는다. `limit()` 호출 지점(FollowCondition.java:25, :45)이 아니라 **필드 기본값 한 곳**에서 막아야 두 경로가 한 번에 닫힌다.
2. 트레이스 수집 대상에 `/api/user/*/following`·`/followers`를 포함시킨다 — 이번 조사에서 이 엔드포인트 트레이스가 0건이라 로그 외 교차검증이 불가능했다.
3. 후보 2: `HHH90003004`가 나는 쿼리를 컬렉션 fetch join + 페이징 조합에서 분리한다(ID 페이징 후 fetch). 데이터 증가에 따라 3초가 계속 늘어난다.
4. `POST /feeds`의 `tb_feed_hashtags` 중복 삽입: 요청 내 해시태그 중복 제거 또는 upsert. 현재는 사용자 게시가 통째로 실패한다.

### 복구 확인
- `GET /api/user/{id}/following`·`/followers`를 **`size` 파라미터 없이** 호출해 200을 확인한다(이것이 이번 실패의 정확한 트리거 조건이다). `size` 지정 호출은 수정 전에도 200이었으므로 검증용으로 부적합하다.
- Loki에서 `NullPointerException ... this.size is null` 0건, `[HTTP] GET /api/user/*/following 500` 0건을 확인.
- `[HTTP-SLOW] ... /following` 및 `HHH90003004` 발생 건수를 배포 전후로 비교(후보 2 조치 시).
- 판정 기준이 되는 타임아웃 설정값(서버·게이트웨이·Hikari `connectionTimeout`)을 함께 수집해 둔다 — 이번엔 없어서 지연 구간 만료 여부를 판정 불가로 남겼다.

---

## 관측 증거 (Evidence)

> 이 조사가 실제로 본 값이다. 원본 응답 전체는 `reports/raw/scan-1786498260-*.json`에 있다.

### 호출 그래프 (트레이스에서 추출)

```
content-service --jdbc--> mysql/content (HikariPool-1)  48회  최대 197.8ms
    error: Duplicate entry '166-179' for key 'tb_feed_hashtags.uk_feed_hashtag'
    events: acquired, rollback, commit
```

### span (duration 상위 15 / 전체 64)

| ms | service | span | 시작 |
|---:|---|---|---|
| 3063.88 | auth-service | `http post /files/upload` | 2026-08-12T01:34:53.777306Z |
| 2738.75 | auth-service | `secured request` | 2026-08-12T01:34:54.101230Z |
| 323.23 | auth-service | `security filterchain before` | 2026-08-12T01:34:53.777939Z |
| 247.74 | content-service | `http post /feeds` | 2026-08-12T01:34:57.012959Z |
| 246.44 | content-service | `secured request` | 2026-08-12T01:34:57.013327Z |
| 197.80 | content-service | `connection` | 2026-08-12T01:34:57.061783Z |
| 194.47 | auth-service | `http post /files/upload` | 2026-08-12T01:35:08.175930Z |
| 178.00 | auth-service | `secured request` | 2026-08-12T01:35:08.192003Z |
| 86.40 | content-service | `http post /feeds` | 2026-08-12T01:35:08.522901Z |
| 83.38 | content-service | `secured request` | 2026-08-12T01:35:08.523268Z |
| 77.87 | content-service | `connection` | 2026-08-12T01:35:08.528689Z |
| 15.45 | auth-service | `security filterchain before` | 2026-08-12T01:35:08.176495Z |
| 10.30 | content-service | `query` | 2026-08-12T01:34:57.079847Z |
| 10.10 | content-service | `query` | 2026-08-12T01:34:57.110716Z |
| 8.94 | content-service | `query` | 2026-08-12T01:34:57.093383Z |

### 로그 원문 (60 / 전체 419줄)

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
2026-08-12T01:32:07.271229530Z  [auth-service]  at org.springframework.data.repository.core.support.QueryExecutorMethodInterceptor.doInvoke(QueryExecutorMethodInterceptor.java:168)
2026-08-12T01:32:07.271231967Z  [auth-service]  at org.springframework.data.repository.core.support.QueryExecutorMethodInterceptor.invoke(QueryExecutorMethodInterceptor.java:143)
2026-08-12T01:32:07.271234291Z  [auth-service]  at org.springframework.aop.framework.ReflectiveMethodInvocation.proceed(ReflectiveMethodInvocation.java:184)
2026-08-12T01:32:07.271236507Z  [auth-service]  at org.springframework.data.projection.DefaultMethodInvokingMethodInterceptor.invoke(DefaultMethodInvokingMethodInterceptor.java:70)
2026-08-12T01:32:07.271238635Z  [auth-service]  at org.springframework.aop.framework.ReflectiveMethodInvocation.proceed(ReflectiveMethodInvocation.java:184)
2026-08-12T01:32:07.271241031Z  [auth-service]  at org.springframework.transaction.interceptor.TransactionInterceptor$1.proceedWithInvocation(TransactionInterceptor.java:123)
2026-08-12T01:32:07.271256227Z  [auth-service]  at org.springframework.transaction.interceptor.TransactionAspectSupport.invokeWithinTransaction(TransactionAspectSupport.java:385)
2026-08-12T01:32:07.271259115Z  [auth-service]  at org.springframework.transaction.interceptor.TransactionInterceptor.invoke(TransactionInterceptor.java:119)
2026-08-12T01:32:07.271261568Z  [auth-service]  at org.springframework.aop.framework.ReflectiveMethodInvocation.proceed(ReflectiveMethodInvocation.java:184)
2026-08-12T01:32:07.271263955Z  [auth-service]  at org.springframework.dao.support.PersistenceExceptionTranslationInterceptor.invoke(PersistenceExceptionTranslationInterceptor.java:137)
2026-08-12T01:32:07.271281691Z  [auth-service]  at org.springframework.aop.framework.ReflectiveMethodInvocation.proceed(ReflectiveMethodInvocation.java:184)
2026-08-12T01:32:07.271286597Z  [auth-service]  at org.springframework.data.jpa.repository.support.CrudMethodMetadataPostProcessor$CrudMethodMetadataPopulatingMethodInterceptor.invoke(CrudMethodMetadataPostProcessor.java:164)
2026-08-12T01:32:07.271289138Z  [auth-service]  at org.springframework.aop.framework.ReflectiveMethodInvocation.proceed(ReflectiveMethodInvocation.java:184)
2026-08-12T01:32:07.271291788Z  [auth-service]  at org.springframework.aop.interceptor.ExposeInvocationInterceptor.invoke(ExposeInvocationInterceptor.java:97)
2026-08-12T01:32:07.271294183Z  [auth-service]  at org.springframework.aop.framework.ReflectiveMethodInvocation.proceed(ReflectiveMethodInvocation.java:184)
2026-08-12T01:32:07.271296906Z  [auth-service]  at org.springframework.aop.framework.JdkDynamicAopProxy.invoke(JdkDynamicAopProxy.java:249)
2026-08-12T01:32:07.271302271Z  [auth-service]  at com.example.toyauth.app.user.service.UserService.getFollowingList(UserService.java:307)
2026-08-12T01:32:07.271314519Z  [auth-service]  at org.springframework.aop.support.AopUtils.invokeJoinpointUsingReflection(AopUtils.java:352)
2026-08-12T01:32:07.271322445Z  [auth-service]  at org.springframework.aop.framework.ReflectiveMethodInvocation.invokeJoinpoint(ReflectiveMethodInvocation.java:196)
2026-08-12T01:32:07.271325083Z  [auth-service]  at org.springframework.aop.framework.ReflectiveMethodInvocation.proceed(ReflectiveMethodInvocation.java:163)
2026-08-12T01:32:07.271327595Z  [auth-service]  at org.springframework.aop.framework.CglibAopProxy$CglibMethodInvocation.proceed(CglibAopProxy.java:765)
2026-08-12T01:32:07.271330140Z  [auth-service]  at org.springframework.transaction.interceptor.TransactionInterceptor$1.proceedWithInvocation(TransactionInterceptor.java:123)
2026-08-12T01:32:07.271332394Z  [auth-service]  at org.springframework.transaction.interceptor.TransactionAspectSupport.invokeWithinTransaction(TransactionAspectSupport.java:385)
2026-08-12T01:32:07.271334687Z  [auth-service]  at org.springframework.transaction.interceptor.TransactionInterceptor.invoke(TransactionInterceptor.java:119)
2026-08-12T01:32:07.271337162Z  [auth-service]  at org.springframework.aop.framework.ReflectiveMethodInvocation.proceed(ReflectiveMethodInvocation.java:184)
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
```

### 메트릭 시계열

| 쿼리 | series | 점 | min | max | last | 값이 0이던 구간 |
|---|---|---:|---:|---:|---:|---|
| `hikaricp_connections_active` | `{__name__=hikaricp_connections_active, application=auth-service, cluster=yogurtte-k3s-prod, container=auth-service, instance=10.42.1.51:8090, job=auth-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=auth-service-5999bb9f5c-lzp4p, pool=HikariPool-1, service=auth-service}` | 21 | 0 | 0 | 0 | **2026-08-12T01:31:00Z ~ 2026-08-12T01:36:00Z** |
| `hikaricp_connections_active` | `{__name__=hikaricp_connections_active, application=chat-service, cluster=yogurtte-k3s-prod, container=chat-service, instance=10.42.1.55:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-fdcc7c776-kh2dl, pool=HikariPool-1}` | 21 | 0 | 0 | 0 | **2026-08-12T01:31:00Z ~ 2026-08-12T01:36:00Z** |
| `hikaricp_connections_active` | `{__name__=hikaricp_connections_active, application=content-service, cluster=yogurtte-k3s-prod, container=content-service, instance=10.42.1.53:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-85f648fcff-sp24n, pool=HikariPool-1}` | 21 | 0 | 0 | 0 | **2026-08-12T01:31:00Z ~ 2026-08-12T01:36:00Z** |
| `hikaricp_connections_active` | `{__name__=hikaricp_connections_active, application=content-service, cluster=yogurtte-k3s-prod, container=content-service, instance=10.42.3.46:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-85f648fcff-v2pw9, pool=HikariPool-1}` | 21 | 0 | 0 | 0 | **2026-08-12T01:31:00Z ~ 2026-08-12T01:36:00Z** |
| `hikaricp_connections_pending` | `{__name__=hikaricp_connections_pending, application=auth-service, cluster=yogurtte-k3s-prod, container=auth-service, instance=10.42.1.51:8090, job=auth-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=auth-service-5999bb9f5c-lzp4p, pool=HikariPool-1, service=auth-service}` | 21 | 0 | 0 | 0 | **2026-08-12T01:31:00Z ~ 2026-08-12T01:36:00Z** |
| `hikaricp_connections_pending` | `{__name__=hikaricp_connections_pending, application=chat-service, cluster=yogurtte-k3s-prod, container=chat-service, instance=10.42.1.55:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-fdcc7c776-kh2dl, pool=HikariPool-1}` | 21 | 0 | 0 | 0 | **2026-08-12T01:31:00Z ~ 2026-08-12T01:36:00Z** |
| `hikaricp_connections_pending` | `{__name__=hikaricp_connections_pending, application=content-service, cluster=yogurtte-k3s-prod, container=content-service, instance=10.42.1.53:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-85f648fcff-sp24n, pool=HikariPool-1}` | 21 | 0 | 0 | 0 | **2026-08-12T01:31:00Z ~ 2026-08-12T01:36:00Z** |
| `hikaricp_connections_pending` | `{__name__=hikaricp_connections_pending, application=content-service, cluster=yogurtte-k3s-prod, container=content-service, instance=10.42.3.46:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-85f648fcff-v2pw9, pool=HikariPool-1}` | 21 | 0 | 0 | 0 | **2026-08-12T01:31:00Z ~ 2026-08-12T01:36:00Z** |
| `rate(jvm_gc_pause_seconds_sum[5m])` | `{action=end of major GC, application=chat-service, cause=Allocation Failure, cluster=yogurtte-k3s-prod, container=chat-service, gc=MarkSweepCompact, instance=10.42.1.55:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-fdcc7c776-kh2dl}` | 21 | 0 | 0 | 0 | **2026-08-12T01:31:00Z ~ 2026-08-12T01:36:00Z** |
| `rate(jvm_gc_pause_seconds_sum[5m])` | `{action=end of minor GC, application=auth-service, cause=G1 Evacuation Pause, cluster=yogurtte-k3s-prod, container=auth-service, gc=G1 Young Generation, instance=10.42.1.51:8090, job=auth-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=auth-service-5999bb9f5c-lzp4p, service=auth-service}` | 21 | 0 | 0.000 | 0.000 | **2026-08-12T01:31:00Z ~ 2026-08-12T01:32:30Z** |
| `rate(jvm_gc_pause_seconds_sum[5m])` | `{action=end of minor GC, application=auth-service, cause=Metadata GC Threshold, cluster=yogurtte-k3s-prod, container=auth-service, gc=G1 Young Generation, instance=10.42.1.51:8090, job=auth-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=auth-service-5999bb9f5c-lzp4p, service=auth-service}` | 21 | 0 | 0 | 0 | **2026-08-12T01:31:00Z ~ 2026-08-12T01:36:00Z** |
| `rate(jvm_gc_pause_seconds_sum[5m])` | `{action=end of minor GC, application=chat-service, cause=Allocation Failure, cluster=yogurtte-k3s-prod, container=chat-service, gc=Copy, instance=10.42.1.55:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-fdcc7c776-kh2dl}` | 21 | 0.000 | 0.000 | 0.000 | — |
| `rate(jvm_gc_pause_seconds_sum[5m])` | `{action=end of minor GC, application=chat-service, cause=GCLocker Initiated GC, cluster=yogurtte-k3s-prod, container=chat-service, gc=Copy, instance=10.42.1.55:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-fdcc7c776-kh2dl}` | 21 | 0 | 0 | 0 | **2026-08-12T01:31:00Z ~ 2026-08-12T01:36:00Z** |
| `rate(jvm_gc_pause_seconds_sum[5m])` | `{action=end of minor GC, application=content-service, cause=Allocation Failure, cluster=yogurtte-k3s-prod, container=content-service, gc=Copy, instance=10.42.1.53:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-85f648fcff-sp24n}` | 21 | 0 | 0.000 | 0.000 | **2026-08-12T01:31:00Z ~ 2026-08-12T01:32:30Z** |
| `rate(jvm_gc_pause_seconds_sum[5m])` | `{action=end of minor GC, application=content-service, cause=Allocation Failure, cluster=yogurtte-k3s-prod, container=content-service, gc=Copy, instance=10.42.3.46:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-85f648fcff-v2pw9}` | 21 | 0 | 0.000 | 0 | **2026-08-12T01:32:00Z ~ 2026-08-12T01:36:00Z** |
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
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=chat-service-fcm-tokens, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=0, topic=user.fcm-tokens}` | 21 | 0 | 0 | 0 | **2026-08-12T01:31:00Z ~ 2026-08-12T01:36:00Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=chat-service-fcm-tokens, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=1, topic=user.fcm-tokens}` | 21 | 0 | 0 | 0 | **2026-08-12T01:31:00Z ~ 2026-08-12T01:36:00Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=chat-service-fcm-tokens, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=2, topic=user.fcm-tokens}` | 21 | 0 | 0 | 0 | **2026-08-12T01:31:00Z ~ 2026-08-12T01:36:00Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=chat-service-notification-settings, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=0, topic=user.notification-settings}` | 21 | 0 | 0 | 0 | **2026-08-12T01:31:00Z ~ 2026-08-12T01:36:00Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=chat-service-notification-settings, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=1, topic=user.notification-settings}` | 21 | 0 | 0 | 0 | **2026-08-12T01:31:00Z ~ 2026-08-12T01:36:00Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=chat-service-notification-settings, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=2, topic=user.notification-settings}` | 21 | 0 | 0 | 0 | **2026-08-12T01:31:00Z ~ 2026-08-12T01:36:00Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=db-writer, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=0, topic=chat.messages}` | 21 | 0 | 0 | 0 | **2026-08-12T01:31:00Z ~ 2026-08-12T01:36:00Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=db-writer, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=1, topic=chat.messages}` | 21 | 0 | 0 | 0 | **2026-08-12T01:31:00Z ~ 2026-08-12T01:36:00Z** |
| `websocket_active_users` | `{__name__=websocket_active_users, application=chat-service, cluster=yogurtte-k3s-prod, container=chat-service, instance=10.42.1.55:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-fdcc7c776-kh2dl}` | 21 | 0 | 0 | 0 | **2026-08-12T01:31:00Z ~ 2026-08-12T01:36:00Z** |

값이 0이던 구간은 굵게 표시했다 — 프로세스가 사라져 시계열이 꺾인 것이 유일한 신호인 장애가 있다.

