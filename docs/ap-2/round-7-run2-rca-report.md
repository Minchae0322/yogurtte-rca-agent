# RCA Report — `scan-1786498260`

| 항목 | 값 |
|---|---|
| 모드 | rca |
| 질문 | 최근 1시간 안에 팔로우 목록이 안 열린다는 제보가 있다. 원인을 조사해줘 |
| 시각 | 2026-08-12T05:13:07.244060800Z |
| provider | claude-cli |
| model | `claude-opus-5` · turns 1 |
| prompt | `.\prompts\system-prompt.md` |
| tokens | in 76846 (cacheRead 23,796 · cacheCreate 53,048) / out 6642 · cost $0.7084 |
| elapsed | total 120357ms (tempo 836 · loki 1003 · mimir 775 · assemble 460 · llm 106717) |

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
| tokens | in 33529 / out 2317 · cost $0.1943 |
| chars | 컨텍스트 3,084 + 프롬프트 1,399 = **4,483** |
| elapsed | survey 1339ms · llm 41199ms |

**선정 이유**: 팔로우 목록은 auth-service의 사용자 관계 조회 경로이고, 창 끝 01:31~01:36Z에 auth-service에서만 NPE가 반복 발생하며 지연까지 동반돼 제보 시각·증상과 유일하게 맞는다.

**근거**

- INC-4: auth-service java.lang.NullPointerException 2건이 01:32:00Z~01:33:00Z에 발생, 01:32~01:36Z 구간에서 평균 60초 간격 x4회 반복 — 일시적 튐이 아니라 지속 중
- INC-4: 같은 서비스 ERROR/WARN 2건이 01:31:00Z부터 시작, 01:31~01:35Z x4회 — 창 끝까지 이어져 '최근 제보' 시각과 일치
- INC-5: auth-service http post /files/upload 3,063ms (slow 채널, 01:34:53.777Z) — 같은 시각 같은 서비스가 지연까지 동반, 특정 핸들러가 아닌 서비스 전반 저하 가능성
- 팔로우 관련 엔드포인트의 에러 트레이스 0건 — Tempo 에러 2건은 모두 content-service /feeds·/comments. 예외가 로그로만 남거나 요청이 트레이스 생성 전에 실패한 형태로 해석
- up / mongodb_up / kafka_brokers / consumergroup_lag / websocket_active_users 모두 이상 0건 — 파드 다운·인프라 장애가 아니라 애플리케이션 코드 경로(NPE) 쪽

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

- INC-1 — chat-service ERROR/WARN 1건 단발(01:22Z)로 팔로우 조회와 도메인이 무관하고 반복성도 없다.
- INC-2 — content-service DataException은 01:22~01:27Z로 창 중반에 끝났고, 피드·댓글 데이터 저장 실패라 목록 조회 실패 지문과 다르다.
- INC-3 — POST /feeds/{feedId}/comments 208ms 에러 트레이스로 댓글 쓰기 경로이며 INC-2의 하류 표현일 뿐 팔로우와 무관하다.
- INC-6 — SQLIntegrityConstraintViolation은 중복 키 등 쓰기 충돌로, 읽기만 하는 목록 조회를 막지 않는다.
- INC-7 — POST /feeds 86ms 에러 트레이스로 INC-6과 같은 피드 생성 실패이며 조회 실패와 경로가 다르다.

**보정 기록**

- 창을 후보 [INC-4, INC-5] 의 신호 시각에서 계산했다 (2026-08-12T01:31:00Z ~ 2026-08-12T01:36:00Z)

## 수집 범위 (Coverage)

- **window**: 2026-08-12T01:31:00Z ~ 2026-08-12T01:36:00Z (300s)
- **trace**: 55,865B / 64 spans
- **창 안 후보 트레이스**: 4건 / 55,865B
- **logs**: errwarn=66,223B · traceId=5,867B
- **metrics**: 8 수집 / 54,688B, 누락 [sum(rate(http_server_requests_seconds_count{application="content-service", status="401"}[1m]))]
- **context**: 84,444 chars (+ 시스템 프롬프트 805 chars)

### 토큰 축 (개선 지표)

| 단계 | 총 in | chars | 컨텍스트 토큰 |
|---|---:|---:|---:|
| 탐색 | 33,529 | 4,483 | 3,519 |
| 분석 | 76,846 | 85,249 | 46,836 |
| **합계** | **110,375** | | **50,355** |

- **overheadTokens 30,010 tok — 이 회차에 실측**(1자 프롬프트 프로브, 같은 명령·모델·샌드박스).
  호출마다 한 번씩 얹히므로 단계별로 뺀다. **다른 날 상수를 쓰지 않았다 = `█ 실측`.**
- contextTokens (count_tokens API): 측정 안 됨 — 구독 CLI 경로엔 API 키가 없다
- 비용은 **API 환산 추정치**다. 구독 계정으로 호출하므로 실제 청구액이 아니다.

## 수집 실패/누락

- Metric 'sum(rate(http_server_requests_seconds_count{application="content-service", status="401"}[1m]))' returned no series in this window; skipped.

---

관측 데이터만으로 정리한다. 결론부터: 팔로우 목록 장애는 auth-service 애플리케이션 코드의 NPE다.

## 1. 원인 후보 랭킹 (최대 3개)

1. **auth-service `FollowCondition`의 `size`가 null이라 페이징 계산에서 NPE → 팔로잉/팔로워 조회 500** (주원인)
2. **컬렉션 fetch join + 페이징의 인메모리 처리로 인한 팔로잉 조회 지연 (2983ms)** — 성공하더라도 "안 열린다"는 체감을 만드는 보조 요인
3. **content-service `tb_feed_hashtags` 중복키 500** — 같은 창에서 관측됐으나 팔로우 목록과는 무관한 별개 장애

## 2. 후보별 근거

### 후보 1 — `FollowCondition.size` null NPE (주원인)

- **근거**
  - `2026-08-12 10:32:07 ... ERROR [traceId=6a7bcd177ec7dcbafab90613c89ef91a, userId=1] GlobalExceptionHandler - [api-error] handleAllException` / `java.lang.NullPointerException: Cannot invoke "java.lang.Integer.intValue()" because "this.size" is null`
  - 스택 앱 프레임: `FollowCondition$FollowingSearch.limit(FollowCondition.java:25)` ← `FollowRepositoryCustomImpl.findFollowingsByUserId(FollowRepositoryCustomImpl.java:35)` ← `UserService.getFollowingList(UserService.java:307)` ← `UserController.getFollowingList(UserController.java:132)`
  - 결과: `RequestLoggingFilter - [HTTP] GET /api/user/1/following 500 - 99ms`
  - 팔로워 목록도 **동일 원인으로 동시에 깨져 있다**: `FollowCondition$FollowerSearch.limit(FollowCondition.java:45)` ← `findFollowersByUserId(...:65)` ← `getFollowerList(UserService.java:318)` ← `UserController.getFollowerList(...:142)` → `GET /api/user/1/followers 500 - 67ms` (10:32:07, traceId `6a7bcd1719fe4d3b8126248de7af6455`)
  - 인프라 요인 배제: 창 전체에서 `up`=1(모든 타깃), auth-service `hikaricp_connections_active`=0·`pending`=0 상수, GC pause rate 최대 4.58e-5초/초, Kafka·Mongo·Redis 정상. **99ms/67ms 만에 DB 접근 전 단계에서 터졌다** — 자원 고갈형이 아니라 결정론적 코드 결함.
  - 인증 문제 아님: 요청이 `JwtFilter` → `ExternalAuthenticationFilter`를 통과해 컨트롤러까지 도달했고 MDC `userId=1`이 찍혔다. (`RequestLoggingFilter` 줄의 `userId=NONE`은 응답 시점 MDC 정리 결과이지 인증 실패 근거가 아니다.)
- **확신도**: 높음 (예외 메시지·앱 스택·HTTP 500이 한 traceId 안에서 일치)
- **반증 데이터**: 같은 엔드포인트가 10:31:56에는 `GET /api/user/1/following 200 - 2983ms`로 **성공**했다 → 전면 장애가 아니라 요청 파라미터(`size` 누락) 의존적 조건부 장애다. 즉 "size를 붙여 보내는 클라이언트/화면은 열리고, 안 붙이는 쪽만 500"이라는 해석과 정합한다.
- **대기·지연 / 최종 상태**: 대기 구간 없음(예외 즉시 반환, 99ms·67ms). 타임아웃 설정값 데이터 없음 → **만료 여부 판정 불가**. 최종 상태는 **실패(HTTP 500)**, 재시도 흔적 없음.

### 후보 2 — 컬렉션 fetch join + 페이징 인메모리 처리로 인한 지연

- **근거**
  - `10:31:56 WARN [traceId=6a7bcd0988d2fa47b225dfd17cd82615, userId=1] org.hibernate.orm.query - HHH90003004: firstResult/maxResults specified with collection fetch; applying in memory`
  - 같은 traceId 결과: `WARN ... [HTTP-SLOW] GET /api/user/1/following 200 - 2983ms`
  - 동일 경고가 `10:33:07`(traceId `6a7bcd53867ffb8faf3127e898919e0b`)에도 재발 — 일회성 아님.
- **확신도**: 중간 (경고와 3초 응답이 같은 traceId로 묶이지만, 이 창에 성공 샘플 1건뿐이라 상시 3초인지 확인 불가)
- **반증 데이터**: DB 측 압박 지표가 전부 정상(auth-service Hikari active/pending 0 상수)이라 지연 원인을 인프라로 돌릴 근거는 없다. 이는 후보 2를 **부정하지 않고 오히려 앱 로직(인메모리 페이징) 쪽으로 좁힌다**.
- **대기·지연 / 최종 상태**: 실측 2983ms. 클라이언트/게이트웨이 타임아웃 설정값 미수집 → **만료 여부 판정 불가**. 최종 상태는 **성공(200)**. 다만 프론트 타임아웃이 3초 이하라면 사용자에겐 실패로 보인다 — 그 값 확인 필요.
- (참고) `10:34:56 [HTTP-SLOW] POST /api/files/upload 200 - 3063ms`, 트레이스 `6a7bcdbd...`에서 `security filterchain before` 323ms·`secured request` 2739ms. 성공(200)이고 팔로우 경로와 무관하지만, auth-service 파드에 초 단위 작업이 상존한다는 배경 정보다.

### 후보 3 — content-service 피드 해시태그 중복키 500 (팔로우와 무관, 별건)

- **근거**
  - 트레이스 `6a7bcdccfb0b88f17f425d5e2fb9908f`: `http post /feeds` `outcome=SERVER_ERROR, status=500`, 86.4ms. 하위 query span에 `error: Duplicate entry '166-179' for key 'tb_feed_hashtags.uk_feed_hashtag'` (`insert into tb_feed_hashtags ...`), `connection` span 이벤트가 `acquired`(508528878) → **`rollback`**(508596343).
  - 호출 그래프 집약에도 동일 에러 표기: `content-service --jdbc--> mysql/content (HikariPool-1) 48회 최대 197.8ms`.
  - 대조군: 트레이스 `6a7bcdc1f9b3ee917fcaafd6360e485b`의 `http post /feeds`는 `status=200`, `commit` — 즉 상시 장애가 아니라 같은 피드에 같은 해시태그가 두 번 들어온 요청에서만 발생.
- **확신도**: 높음(현상 자체는 확정) / **팔로우 목록 원인일 확률은 낮음**
- **반증 데이터**: 호출 그래프에 auth-service ↔ content-service 엣지가 없고, 팔로우 NPE는 content-service 호출 이전에 auth-service 내부에서 종결됐다(스택에 원격 호출 프레임 없음). 서비스·엔드포인트·시각(01:35:08 vs 01:32:07) 모두 불일치.
- **대기·지연 / 최종 상태**: DB 커넥션 획득 0.19ms(대기 사실상 없음), 트랜잭션 총 77.9ms. 타임아웃 설정값 미수집 → **만료 여부 판정 불가**. 최종 상태는 **실패 후 롤백(폐기)** — `rollback` 이벤트 확인. 재시도 흔적 없음.

### 데이터 부족 항목 (결론 확신도 하향 요인)

- content-service 401 메트릭 쿼리가 시리즈 0으로 실패 — 다만 팔로우 경로는 auth-service 소관이라 이 공백이 후보 1을 흔들지 않는다.
- **Tempo에서 `/api/user/*/following|followers` 트레이스가 한 건도 수집되지 않았다.** 수집된 4건은 전부 `/feeds`·`/files/upload`다. 따라서 **영향 범위(500 비율, 영향 사용자 수)를 정량화할 수 없다** — 관측된 실패는 userId=1, 파드 `auth-service-5999bb9f5c-lzp4p` 단일 샘플 2건뿐.
- auth-service의 `http_server_requests_seconds_count{uri="/api/user/{id}/following"}` 계열 메트릭 미수집 → 에러율 추이 불명.

## 3. 권장 다음 조치

### 이미 발생한 피해: 복구 가능한가

**복구 가능 — 별도 데이터 복구 작업 불필요.**
- 팔로우 목록은 **조회(GET)** 이고 NPE가 DB 접근 전에 발생해 쓰기 부작용이 없다. 코드 수정 배포 후 재요청하면 그대로 복구된다. 유실 데이터 없음.
- 별건인 피드 등록 500은 `rollback` 이벤트로 부분 저장이 배제된다(`tb_feed` id 261/262, `tb_feed_attachment_file`, `tb_hashtags` 모두 롤백). 다만 **해당 사용자의 피드는 저장되지 않았으므로 재등록이 필요**하다 — 자동 복구 수단은 관측 데이터에 없다. 영향 요청 목록은 `outcome=SERVER_ERROR`인 `http post /feeds` 트레이스로 추출 가능.

### 재발 방지

1. **(P0) `FollowCondition`의 `size`/`page` null 방어** — `FollowCondition.java:25`(FollowingSearch)와 `:45`(FollowerSearch) 양쪽에 동일 결함. 호출부마다 막지 말고 `FollowCondition` 한 곳에서 기본값을 채워라(`size == null ? DEFAULT_SIZE : size`). `findFollowingsByUserId`·`findFollowersByUserId` 두 경로가 같은 클래스로 수렴하므로 수정 1곳이면 둘 다 닫힌다.
2. **(P1) 컬렉션 fetch join + 페이징 제거** — `HHH90003004`는 전체 로딩 후 메모리 페이징을 뜻하고, 팔로워 수 증가에 따라 2983ms가 선형으로 악화된다. fetch join 제거 후 `@BatchSize`/2단계 조회로 전환.
3. **(P2) 알림 추가** — auth-service 5xx 비율과 `[HTTP-SLOW]` 발생률에 경보. 현재는 사용자 제보로만 인지됐다.
4. **(P2) 관측 공백 메우기** — auth-service의 `/api/user/{id}/following|followers`가 Tempo 샘플링에서 잡히지 않는다. 5xx 요청 강제 샘플링을 켜야 다음 장애 때 영향 범위를 즉시 산출할 수 있다.

### 복구 확인

1. `size` 파라미터 **없이** `GET /api/user/{id}/following`, `GET /api/user/{id}/followers`를 호출해 200과 기본 페이지 크기 응답 확인 (수정 전 재현 → 수정 후 해소를 같은 요청으로 대조).
2. Loki에서 30분간 `NullPointerException` + `FollowCondition` 동시 매칭 0건 확인, 그리고 `[HTTP] GET /api/user/.*/following 500` 0건 확인.
3. `HHH90003004` 경고 소멸 및 `[HTTP-SLOW] GET /api/user/.*/following` 0건 확인. 2번 조치를 아직 안 했다면 이 항목은 미해소로 남는 것이 정상이며, 응답시간이 2983ms 근방에 머무는지만 기록해 둘 것.
4. 별건 피드 오류: `http post /feeds`의 `outcome=SERVER_ERROR` 비율과 `Duplicate entry ... uk_feed_hashtag` 로그 0건 확인.

**보류 판단**: 조치 1·2는 코드 원인이 확정적이라 바로 진행 가능하지만, **영향 범위(몇 명이 몇 건 실패했는지)는 현재 데이터로 산출 불가**다. 롤백/스케일 같은 인프라 대응은 근거가 없으므로 권하지 않는다 — `up`, Hikari, GC, Kafka 지표가 모두 정상이다.

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

