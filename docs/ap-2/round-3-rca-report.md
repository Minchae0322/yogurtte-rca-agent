# RCA Report — `scan-1785759849`

| 항목 | 값 |
|---|---|
| 모드 | rca |
| 질문 | 최근 1시간 안에 팔로우 목록이 안 열린다는 제보가 있다. 원인을 조사해줘 |
| 시각 | 2026-08-03T13:24:54.832825Z |
| provider | claude-cli |
| model | `claude-opus-5` · turns 1 |
| prompt | `./prompts/system-prompt.md` |
| tokens | in 107588 (cacheRead 18,133 · cacheCreate 89,453) / out 4618 · cost $1.0842 |
| elapsed | total 83530ms (tempo 555 · loki 416 · mimir 708 · assemble 27 · llm 74522) |

## 탐색 (Triage)

| 항목 | 값 |
|---|---|
| 시간창 해석 | 상대 표현 '최근 1시간' |
| 시간창 확신도 | EXACT |
| 스윕 창 | 2026-08-03T12:24:09.825649Z ~ 2026-08-03T13:24:09.825649Z |
| 좁힌 창 | 2026-08-03T12:24:09.825649Z ~ 2026-08-03T13:24:09.825649Z |
| 대상 | content-service, auth-service |
| traceId | 6a7095b09dbabfa223fd8e4c12fda927 |
| 트레이스 후보 | 1건 |
| 장애 후보 | 7건 · 선택 INC-3, INC-6, INC-7 |
| 계획 파싱 | 성공 |
| 스윕 컨텍스트 | 후보 + 원본 (A) |
| prompt | `./prompts/triage-prompt.md` |
| tokens | in 47182 / out 2965 · cost $0.4216 |
| chars | 컨텍스트 44,345 + 프롬프트 1,399 = **45,744** |
| elapsed | survey 1053ms · llm 43922ms |

**선정 이유**: 팔로우 목록 조회 경로(content-service 피드/소셜 그래프 + auth-service 인증)에서 제보 시각과 가장 가까운 13:15~13:21 에러 클러스터가 잡히고, 같은 서비스의 앞선 로그 지문까지 묶어 조사 범위를 확보하기 위함이다.

**근거**

- INC-7: 이 창에서 유일한 에러 트레이스 — content-service http post /feeds, 2026-08-03T13:20:48.769546Z, 95ms, traceId 6a7095b09dbabfa223fd8e4c12fda927, serviceStats content-service errorCount=1/spanCount=23
- INC-6: auth-service ERROR/WARN 5건 (13:15:00Z~13:20:00Z) — INC-7보다 1~5분 앞선 상류 신호이며, 팔로우 목록은 토큰 검증·유저 조회로 auth를 경유한다
- INC-3: content-service ERROR/WARN 10건 (12:20:00Z~12:25:00Z) — 같은 서비스의 앞선 지문으로, 제보 구간 내 재발 여부 확인 필요
- Tempo 지연 검색 '{ duration > 3s && status != error }' 0건 — 임계값 초과 지연이 없었으므로 증상은 타임아웃형 지연이 아니라 에러/빈 응답 계열
- min_over_time(up[5m])가 content-service 2개 파드(10.42.1.43, 10.42.3.42)와 auth-service(10.42.1.44) 모두 전 구간 1 — 프로세스 다운이 아니라 애플리케이션 레벨 실패로 좁혀진다

**스윕이 찾은 트레이스** (고른 것은 6a7095b09dbabfa223fd8e4c12fda927)

| traceId | 채널 | root service | root span | ms |
|---|---|---|---|---:|
| `6a7095b09dbabfa223fd8e4c12fda927` ←선택 | error | content-service | http post /feeds | 95 |

**장애 후보** (코드가 신호를 묶은 것 · 창은 여기서 계산됨)

## INC-1  auth-service  |  ERROR/WARN
- 구간: 2026-08-03T12:20:00Z ~ 2026-08-03T12:25:00Z  (LOKI · 집계 해상도만큼 흐림)
- ERROR/WARN 5건 (2026-08-03T12:20:00Z ~ 2026-08-03T12:25:00Z)
- 같은 시각의 다른 후보: INC-2, INC-3, INC-4, INC-5  (인과 여부는 판단하지 않았다)

## INC-2  chat-service  |  ERROR/WARN
- 구간: 2026-08-03T12:20:00Z ~ 2026-08-03T12:25:00Z  (LOKI · 집계 해상도만큼 흐림)
- ERROR/WARN 10건 (2026-08-03T12:20:00Z ~ 2026-08-03T12:25:00Z)
- 같은 시각의 다른 후보: INC-1, INC-3, INC-4, INC-5  (인과 여부는 판단하지 않았다)

## INC-3  content-service  |  ERROR/WARN
- 구간: 2026-08-03T12:20:00Z ~ 2026-08-03T12:25:00Z  (LOKI · 집계 해상도만큼 흐림)
- ERROR/WARN 10건 (2026-08-03T12:20:00Z ~ 2026-08-03T12:25:00Z)
- 같은 시각의 다른 후보: INC-1, INC-2, INC-4, INC-5  (인과 여부는 판단하지 않았다)

## INC-4  kafka  |  kafka_consumergroup_lag
- 구간: 2026-08-03T12:24:09Z ~ 2026-08-03T13:24:09Z  (MIMIR · 집계 해상도만큼 흐림)
- kafka_consumergroup_lag{consumergroup=chat-service-fcm-tokens, partition=0, topic=user.fcm-tokens} 가 0이었다 (2026-08-03T12:24:09Z ~ 2026-08-03T13:24:09Z)
- kafka_consumergroup_lag{consumergroup=chat-service-fcm-tokens, partition=1, topic=user.fcm-tokens} 가 0이었다 (2026-08-03T12:24:09Z ~ 2026-08-03T13:24:09Z)
- kafka_consumergroup_lag{consumergroup=chat-service-fcm-tokens, partition=2, topic=user.fcm-tokens} 가 0이었다 (2026-08-03T12:24:09Z ~ 2026-08-03T13:24:09Z)
- kafka_consumergroup_lag{consumergroup=chat-service-notification-settings, partition=0, topic=user.notification-settings} 가 0이었다 (2026-08-03T12:24:09Z ~ 2026-08-03T13:24:09Z)
- kafka_consumergroup_lag{consumergroup=chat-service-notification-settings, partition=1, topic=user.notification-settings} 가 0이었다 (2026-08-03T12:24:09Z ~ 2026-08-03T13:24:09Z)
- kafka_consumergroup_lag{consumergroup=chat-service-notification-settings, partition=2, topic=user.notification-settings} 가 0이었다 (2026-08-03T12:24:09Z ~ 2026-08-03T13:24:09Z)
- kafka_consumergroup_lag{consumergroup=db-writer, partition=0, topic=chat.messages} 가 0이었다 (2026-08-03T12:24:09Z ~ 2026-08-03T13:24:09Z)
- kafka_consumergroup_lag{consumergroup=db-writer, partition=1, topic=chat.messages} 가 0이었다 (2026-08-03T12:24:09Z ~ 2026-08-03T13:24:09Z)
- kafka_consumergroup_lag{consumergroup=db-writer, partition=11, topic=chat.messages} 가 0이었다 (2026-08-03T12:24:09Z ~ 2026-08-03T13:24:09Z)
- kafka_consumergroup_lag{consumergroup=db-writer, partition=3, topic=chat.messages} 가 0이었다 (2026-08-03T12:24:09Z ~ 2026-08-03T13:24:09Z)
- kafka_consumergroup_lag{consumergroup=db-writer, partition=4, topic=chat.messages} 가 0이었다 (2026-08-03T12:24:09Z ~ 2026-08-03T13:24:09Z)
- kafka_consumergroup_lag{consumergroup=db-writer, partition=5, topic=chat.messages} 가 0이었다 (2026-08-03T12:24:09Z ~ 2026-08-03T13:24:09Z)
- kafka_consumergroup_lag{consumergroup=db-writer, partition=7, topic=chat.messages} 가 0이었다 (2026-08-03T12:24:09Z ~ 2026-08-03T13:24:09Z)
- kafka_consumergroup_lag{consumergroup=db-writer, partition=8, topic=chat.messages} 가 0이었다 (2026-08-03T12:24:09Z ~ 2026-08-03T13:24:09Z)
- kafka_consumergroup_lag{consumergroup=db-writer-retry-1000, partition=0, topic=chat.messages-retry-1000} 가 0이었다 (2026-08-03T12:24:09Z ~ 2026-08-03T13:24:09Z)
- kafka_consumergroup_lag{consumergroup=db-writer-retry-2000, partition=0, topic=chat.messages-retry-2000} 가 0이었다 (2026-08-03T12:24:09Z ~ 2026-08-03T13:24:09Z)
- kafka_consumergroup_lag{consumergroup=db-writer-retry-4000, partition=0, topic=chat.messages-retry-4000} 가 0이었다 (2026-08-03T12:24:09Z ~ 2026-08-03T13:24:09Z)
- kafka_consumergroup_lag{consumergroup=notification, partition=0, topic=chat.messages} 가 0이었다 (2026-08-03T12:24:09Z ~ 2026-08-03T13:24:09Z)
- kafka_consumergroup_lag{consumergroup=notification, partition=1, topic=chat.messages} 가 0이었다 (2026-08-03T12:24:09Z ~ 2026-08-03T13:24:09Z)
- kafka_consumergroup_lag{consumergroup=notification, partition=11, topic=chat.messages} 가 0이었다 (2026-08-03T12:24:09Z ~ 2026-08-03T13:24:09Z)
- kafka_consumergroup_lag{consumergroup=notification, partition=2, topic=chat.messages} 가 0이었다 (2026-08-03T12:24:09Z ~ 2026-08-03T13:24:09Z)
- kafka_consumergroup_lag{consumergroup=notification, partition=3, topic=chat.messages} 가 0이었다 (2026-08-03T12:24:09Z ~ 2026-08-03T13:24:09Z)
- kafka_consumergroup_lag{consumergroup=notification, partition=4, topic=chat.messages} 가 0이었다 (2026-08-03T12:24:09Z ~ 2026-08-03T13:24:09Z)
- kafka_consumergroup_lag{consumergroup=notification, partition=5, topic=chat.messages} 가 0이었다 (2026-08-03T12:24:09Z ~ 2026-08-03T13:24:09Z)
- kafka_consumergroup_lag{consumergroup=notification, partition=7, topic=chat.messages} 가 0이었다 (2026-08-03T12:24:09Z ~ 2026-08-03T13:24:09Z)
- kafka_consumergroup_lag{consumergroup=notification, partition=8, topic=chat.messages} 가 0이었다 (2026-08-03T12:24:09Z ~ 2026-08-03T13:24:09Z)
- kafka_consumergroup_lag{consumergroup=notification-processors, partition=0, topic=user.notifications} 가 0이었다 (2026-08-03T12:24:09Z ~ 2026-08-03T13:24:09Z)
- kafka_consumergroup_lag{consumergroup=notification-processors, partition=1, topic=user.notifications} 가 0이었다 (2026-08-03T12:24:09Z ~ 2026-08-03T13:24:09Z)
- kafka_consumergroup_lag{consumergroup=notification-processors, partition=2, topic=user.notifications} 가 0이었다 (2026-08-03T12:24:09Z ~ 2026-08-03T13:24:09Z)
- kafka_consumergroup_lag{consumergroup=notification-processors, partition=3, topic=user.notifications} 가 0이었다 (2026-08-03T12:24:09Z ~ 2026-08-03T13:24:09Z)
- kafka_consumergroup_lag{consumergroup=notification-processors, partition=4, topic=user.notifications} 가 0이었다 (2026-08-03T12:24:09Z ~ 2026-08-03T13:24:09Z)
- kafka_consumergroup_lag{consumergroup=notification-processors, partition=5, topic=user.notifications} 가 0이었다 (2026-08-03T12:24:09Z ~ 2026-08-03T13:24:09Z)
- kafka_consumergroup_lag{consumergroup=notification-recovery, partition=0, topic=user.notifications.dlq} 가 0이었다 (2026-08-03T12:24:09Z ~ 2026-08-03T13:24:09Z)
- kafka_consumergroup_lag{consumergroup=notification-recovery, partition=2, topic=user.notifications.dlq} 가 0이었다 (2026-08-03T12:24:09Z ~ 2026-08-03T13:24:09Z)
- kafka_consumergroup_lag{consumergroup=notification-retry-2000, partition=0, topic=chat.messages-retry-2000} 가 0이었다 (2026-08-03T12:24:09Z ~ 2026-08-03T13:24:09Z)
- kafka_consumergroup_lag{consumergroup=notification-retry-4000, partition=0, topic=chat.messages-retry-4000} 가 0이었다 (2026-08-03T12:24:09Z ~ 2026-08-03T13:24:09Z)
- 같은 시각의 다른 후보: INC-1, INC-2, INC-3, INC-5, INC-6, INC-7  (인과 여부는 판단하지 않았다)

## INC-5  chat-service  |  websocket_active_users
- 구간: 2026-08-03T12:24:09Z ~ 2026-08-03T13:24:09Z  (MIMIR · 집계 해상도만큼 흐림)
- websocket_active_users{container=chat-service, namespace=default, pod=chat-service-fdcc7c776-qrbc2} 가 0이었다 (2026-08-03T12:24:09Z ~ 2026-08-03T13:24:09Z)
- 같은 시각의 다른 후보: INC-1, INC-2, INC-3, INC-4, INC-6, INC-7  (인과 여부는 판단하지 않았다)

## INC-6  auth-service  |  ERROR/WARN
- 구간: 2026-08-03T13:15:00Z ~ 2026-08-03T13:20:00Z  (LOKI · 집계 해상도만큼 흐림)
- ERROR/WARN 5건 (2026-08-03T13:15:00Z ~ 2026-08-03T13:20:00Z)
- 같은 시각의 다른 후보: INC-4, INC-5  (인과 여부는 판단하지 않았다)

## INC-7  content-service  |  http post /feeds
- 구간: 2026-08-03T13:20:48.769546Z ~ 2026-08-03T13:20:48.864546Z  (TEMPO · 시각 정확)
- content-service http post /feeds 95ms (error 채널)
- traceId: 6a7095b09dbabfa223fd8e4c12fda927
- 같은 시각의 다른 후보: INC-4, INC-5  (인과 여부는 판단하지 않았다)

**기각한 후보**

- INC-1 — auth-service ERROR/WARN 12:20~12:25는 content·chat과 동시에 터진 3개 서비스 동시 버스트이고 조회 창 시작(12:24:09) 직전 버킷이라 배경 잡음일 가능성이 높으며, 같은 서비스의 더 최근 지문인 INC-6로 대표시켰다.
- INC-2 — chat-service ERROR/WARN은 메시징 경로 신호로, 팔로우 목록 조회 API와 호출 경로가 겹치지 않는다.
- INC-4 — kafka_consumergroup_lag가 전 구간 0이고 kafka_brokers도 1로 정상이라 소비 지연이 없다는 뜻이며, -1로 나오는 파티션(db-writer/notification의 2·6·9·10 등)은 chat.messages 계열이라 팔로우 목록과 무관하다.
- INC-5 — websocket_active_users가 chat-service 단일 파드에서 1시간 내내 평탄한 0이라 이번 창에 시작된 변화가 아니고, WebSocket 세션은 팔로우 목록 HTTP 조회 경로와 무관하다.

**보정 기록**

- 창을 후보 [INC-3, INC-6, INC-7] 의 신호 시각에서 계산했다 (2026-08-03T12:24:09.825649Z ~ 2026-08-03T13:24:09.825649Z)

**스윕 무신호/실패**

- Tempo 지연 검색 '{ duration > 3s && status != error }'이 이 창에서 0건이다. 임계값보다 느린 요청이 없었다는 뜻이다.

## 수집 범위 (Coverage)

- **window**: 2026-08-03T12:24:09.825649Z ~ 2026-08-03T13:24:09.825649Z (3600s)
- **trace**: 42,910B / 43 spans
- **창 안 후보 트레이스**: 10건 / 42,910B
- **logs**: errwarn=101,427B · traceId=5,712B
- **metrics**: 8 수집 / 376,809B, 누락 [sum(rate(http_server_requests_seconds_count{application="content-service", status="401"}[1m]))]
- **context**: 175,317 chars (+ 시스템 프롬프트 576 chars)

### 토큰 축 (개선 지표)

| 단계 | 총 in | chars | 컨텍스트 토큰 |
|---|---:|---:|---:|
| 탐색 | 47,182 | 45,744 | 25,553 |
| 분석 | 107,588 | 175,893 | 85,959 |
| **합계** | **154,770** | | **111,512** |

- **overheadTokens 21,629 tok — 이 회차에 실측**(1자 프롬프트 프로브, 같은 명령·모델·샌드박스).
  호출마다 한 번씩 얹히므로 단계별로 뺀다. **다른 날 상수를 쓰지 않았다 = `█ 실측`.**
- contextTokens (count_tokens API): 측정 안 됨 — 구독 CLI 경로엔 API 키가 없다
- 비용은 **API 환산 추정치**다. 구독 계정으로 호출하므로 실제 청구액이 아니다.

## 수집 실패/누락

- Metric 'sum(rate(http_server_requests_seconds_count{application="content-service", status="401"}[1m]))' returned no series in this window; skipped.

---

## 1. 원인 후보 랭킹 (최대 3개)

1. **auth-service `FollowCondition`의 `size`가 null인 상태로 `limit()` 호출 → NPE → 팔로잉/팔로워 목록 API 500** (가장 유력)
2. **`size`가 null이 되는 경로 = 요청에 페이징 파라미터가 없거나 바인딩되지 않는 케이스** (1번의 하위 원인, 요청 조건에 따라 간헐 발생)
3. content-service `tb_feed_hashtags` Duplicate entry 500 — **팔로우 목록과 무관한 별개 장애**

---

## 2. 후보별 근거

### 후보 1 — FollowCondition.size NPE로 팔로우 목록 API가 500

- **근거**
  - auth-service(pod `auth-service-5999bb9f5c-qqrss`) 로그, 2026-08-03 22:19:18 (=13:19:18 UTC, 조회 창 내):
    - `java.lang.NullPointerException: Cannot invoke "java.lang.Integer.intValue()" because "this.size" is null`
    - `at com.example.toyauth.app.user.controller.dto.FollowCondition$FollowingSearch.limit(FollowCondition.java:25)`
    - `at ...FollowRepositoryCustomImpl.findFollowingsByUserId(FollowRepositoryCustomImpl.java:35)` → `UserService.getFollowingList(UserService.java:307)` → `UserController.getFollowingList(UserController.java:132)`
    - 결과 로그: `[HTTP] GET /api/user/1/following 500 - 54ms` (traceId=`6a709556a7b651ba4628e9d2ac58f500`)
  - 동일 증상이 팔로워 목록에서도 재현: `FollowCondition$FollowerSearch.limit(FollowCondition.java:45)` → `findFollowersByUserId(...:65)` → `UserService.getFollowerList(UserService.java:318)` → `[HTTP] GET /api/user/1/followers 500 - 34ms` (traceId=`6a709556f6e16f65ec4dc5deefad718c`)
  - 즉 제보된 "팔로우 목록이 안 열린다"와 URI(`/api/user/{id}/following`, `/api/user/{id}/followers`)·시각·상태코드(500)가 정확히 일치한다. 예외가 `GlobalExceptionHandler.handleAllException`까지 올라가 500으로 응답됐다.
  - 인프라 요인은 관측값상 배제된다: `up`은 auth/content/chat·redis·kafka·mongodb 모두 전 구간 1, `hikaricp_connections_active`·`hikaricp_connections_pending`은 전 서비스 전 구간 0, auth-service GC pause는 최대 7.08e-5 s/s로 사실상 0.
- **확신도**: 높음
- **반증 데이터**: 없음. (다만 표본이 2건뿐이라 "모든 사용자/모든 요청이 실패한다"는 범위 주장은 이 데이터로 뒷받침되지 않는다 — 아래 후보 2 참조)

### 후보 2 — `size` 미전달/미바인딩 케이스에서만 터지는 간헐 실패

- **근거**
  - 같은 사용자(`userId=1`)의 같은 계열 조회가 **11초 전에는 성공 경로를 탔다**: 22:19:07 `WARN ... HHH90003004: firstResult/maxResults specified with collection fetch; applying in memory` (traceId=`6a70954b2f8786863663471e9ab9402e`), 22:20:18에도 동일 WARN (traceId=`6a709592fc9bba07669166205071d1bb`). 이 두 건에는 대응하는 500 로그가 없다.
  - 이 WARN은 `maxResults`가 **지정된** 경우에만 나온다. 즉 22:19:07 / 22:20:18 요청에는 size가 있었고, 22:19:18의 두 요청에는 없었다 → `size` 필드에 기본값이 없어(`Integer`, null 허용) 요청 파라미터 유무에 따라 갈리는 구조로 보인다.
  - 실패 2건은 모두 22:19:18에 몰려 있고(`exec-3`, `exec-10` 서로 다른 스레드), 그 앞뒤로 성공 요청이 존재한다.
- **확신도**: 중간 (코드/요청 원문을 보지 못했으므로 "파라미터 누락"인지 "바인딩 실패"인지는 미확정)
- **반증 데이터**: 성공/실패 요청의 쿼리스트링이 로그에 남아 있지 않아, size 유무가 실제 분기 조건이라는 직접 증거는 없다. HHH90003004 WARN이 반드시 팔로우 목록 API에서 났다는 확증도 없다(URI 미기록, traceId도 500 건과 다름).

### 후보 3 — content-service Duplicate entry 500 (팔로우 장애와 무관)

- **근거**
  - `Duplicate entry '160-177' for key 'tb_feed_hashtags.uk_feed_hashtag'` / `SQL Error: 1062, SQLState: 23000`, 스택은 `FeedService.createFeed(FeedService.java:223)` → `ExpGrantService.grantFeedCreate/grant/grantWithCap/isDuplicate`, 결과는 `[HTTP] POST /api/feeds 500 - 94ms` (traceId=`6a7095b09dbabfa223fd8e4c12fda927`). 트레이스에서도 `http post /feeds`가 `status=500`, JDBC connection span에 `rollback` 이벤트.
  - 서비스(content-service)·엔드포인트(POST /api/feeds)·기능(피드 생성/경험치 부여) 모두 팔로우 목록과 다르다. 호출 그래프상 content-service는 redis와 mysql/content만 호출하며 **auth-service를 호출하는 엣지가 없다**.
- **확신도**: 팔로우 장애의 원인일 가능성 — 낮음 (별개 버그로는 확신도 높음)
- **반증 데이터**: 위 호출 그래프(content→auth 엣지 부재)와, 팔로우 실패 트레이스가 auth-service 단독에서 완결된다는 점.

### 데이터 부족 사항

- 수집 실패: `http_server_requests_seconds_count{application="content-service", status="401"}` 시리즈 없음 → 인증 계열 가설은 판단 불가.
- 더 중요한 공백: **auth-service의 http_server_requests / status별 요청 수 메트릭이 전혀 없다.** 따라서 500의 발생 빈도·영향 사용자 수·시작 시각을 정량화할 수 없다. 위 결론은 "이 시간창에서 관측된 팔로우 목록 500 2건의 직접 원인"까지만 확정된다.
- Tempo에서 수집된 10건 트레이스에 문제 traceId(`6a709556...`)가 포함되지 않았고 auth-service 트레이스는 필터체인 span뿐이다 → 요청 파라미터/응답 본문 확인 불가.
- auth-service 배포 이력(이미지 태그/롤아웃 시각)이 없어 "최근 1시간"과 코드 변경의 인과를 확인할 수 없다.

---

## 3. 권장 다음 조치

1. **코드 확인 (최우선)**: `FollowCondition.java` 25행(`FollowingSearch.limit()`)·45행(`FollowerSearch.limit()`)에서 `Integer size` 언박싱 지점. `size`/`page`에 기본값(예: `size == null ? DEFAULT_SIZE : size`, 또는 `@Builder.Default`) 부여하고, `UserController` 132·142행의 파라미터 바인딩(`@ModelAttribute`/`@RequestParam(defaultValue=...)`)을 함께 점검.
2. **재현**: `GET /api/user/1/following` 을 (a) `?page=0&size=20` 포함, (b) 파라미터 없이 각각 호출해 500 재현 여부 확인 → 후보 2 확정/기각.
3. **영향 범위 정량화**: Loki에서
   `{job="default/auth-service"} |= "/api/user/" |= "500"` 및 `|= "this.size is null"` 을 최근 24h로 집계해 건수·시작 시각·영향 userId 분포 확인. 첫 발생 시각을 auth-service 배포 시각과 대조(`kubectl rollout history deploy/auth-service`, `kubectl describe pod auth-service-5999bb9f5c-qqrss`).
4. **메트릭 공백 해소**: auth-service에 `http_server_requests_seconds_count`(status/uri 라벨) 스크레이프가 되는지 확인하고, `sum by (uri,status) (rate(...{application="auth-service"}[5m]))` 대시보드/알람(5xx>0) 추가.
5. **완화**: 핫픽스 전까지 게이트웨이/클라이언트에서 팔로우 목록 호출 시 `page`·`size`를 항상 붙이도록 강제하거나, 서버에 기본값 패치 후 롤아웃.
6. **별건 처리**: content-service `POST /api/feeds`의 `uk_feed_hashtag` 1062 중복 삽입은 별도 티켓으로 분리. `ExpGrantService.isDuplicate` 호출 시점의 auto-flush가 미완료 `tb_feed_hashtags` insert를 밀어내며 터지는 형태이므로, 해시태그 연결 저장 시 중복 체크/`insert ignore`·`saveAll` 전 dedup 검토.

---

## 관측 증거 (Evidence)

> 이 조사가 실제로 본 값이다. 원본 응답 전체는 `reports/raw/scan-1785759849-*.json`에 있다.

### 호출 그래프 (트레이스에서 추출)

```
content-service --db--> redis  5회  최대 14.9ms  [INFO, SET]
content-service --jdbc--> mysql/content (HikariPool-1)  24회  최대 99.5ms
    error: Duplicate entry '160-177' for key 'tb_feed_hashtags.uk_feed_hashtag'
    events: acquired, rollback, commit
```

### span (duration 상위 15 / 전체 43)

| ms | service | span | 시작 |
|---:|---|---|---|
| 129.17 | content-service | `task battle-deadline-notification-scheduler.notify` | 2026-08-03T12:28:00.016232Z |
| 99.46 | content-service | `connection` | 2026-08-03T12:28:00.040586Z |
| 95.14 | content-service | `http post /feeds` | 2026-08-03T13:20:48.769546Z |
| 92.97 | content-service | `secured request` | 2026-08-03T13:20:48.769927Z |
| 86.15 | content-service | `connection` | 2026-08-03T13:20:48.776662Z |
| 81.25 | content-service | `battle-deadline:end` | 2026-08-03T12:28:00.049308Z |
| 76.54 | content-service | `query` | 2026-08-03T12:28:00.050137Z |
| 17.57 | content-service | `task battle-deadline-notification-scheduler.notify` | 2026-08-03T12:28:00.030493Z |
| 14.91 | content-service | `SET` | 2026-08-03T12:28:00.025400Z |
| 11.35 | content-service | `query` | 2026-08-03T13:20:48.801725Z |
| 5.32 | content-service | `secured request` | 2026-08-03T12:27:59.241082Z |
| 3.91 | content-service | `query` | 2026-08-03T13:20:48.840220Z |
| 3.23 | content-service | `query` | 2026-08-03T13:20:48.829008Z |
| 3.16 | content-service | `SET` | 2026-08-03T12:28:00.142075Z |
| 2.86 | content-service | `SET` | 2026-08-03T12:28:00.045087Z |

### 로그 원문 (60 / 전체 665줄)

전체가 상한을 넘어 ERROR/WARN 줄을 우선 발췌했다. 나머지는 원본 파일에 있다.

```
2026-08-03T13:19:07.677361620Z  [auth-service]  [2m2026-08-03 22:19:07[0;39m [2m[http-nio-8081-exec-1][0;39m [33m WARN [traceId=6a70954b2f8786863663471e9ab9402e,spanId=e9c884e23b79c8bd,userId=1][0;39m [36morg.hibernate.orm.query[0;39m [2m-[0;39m HHH90003004: firstResult/maxResults specified with collection fetch; applying in memory
2026-08-03T13:19:18.452681926Z  [auth-service]  [2m2026-08-03 22:19:18[0;39m [2m[http-nio-8081-exec-3][0;39m [31mERROR [traceId=6a709556a7b651ba4628e9d2ac58f500,spanId=701c4504f1b14ba8,userId=1][0;39m [36mc.e.t.a.c.e.GlobalExceptionHandler[0;39m [2m-[0;39m [api-error] handleAllException
2026-08-03T13:19:18.452711128Z  [auth-service]  java.lang.NullPointerException: Cannot invoke "java.lang.Integer.intValue()" because "this.size" is null
2026-08-03T13:19:18.452715880Z  [auth-service]  at com.example.toyauth.app.user.controller.dto.FollowCondition$FollowingSearch.limit(FollowCondition.java:25)
2026-08-03T13:19:18.452720337Z  [auth-service]  at com.example.toyauth.app.follow.repository.querydsl.impl.FollowRepositoryCustomImpl.findFollowingsByUserId(FollowRepositoryCustomImpl.java:35)
2026-08-03T13:19:18.452749778Z  [auth-service]  at org.springframework.aop.support.AopUtils.invokeJoinpointUsingReflection(AopUtils.java:352)
2026-08-03T13:19:18.452753187Z  [auth-service]  at org.springframework.aop.framework.ReflectiveMethodInvocation.invokeJoinpoint(ReflectiveMethodInvocation.java:196)
2026-08-03T13:19:18.452755840Z  [auth-service]  at org.springframework.aop.framework.ReflectiveMethodInvocation.proceed(ReflectiveMethodInvocation.java:163)
2026-08-03T13:19:18.452758729Z  [auth-service]  at org.springframework.aop.framework.CglibAopProxy$CglibMethodInvocation.proceed(CglibAopProxy.java:765)
2026-08-03T13:19:18.452761711Z  [auth-service]  at org.springframework.dao.support.PersistenceExceptionTranslationInterceptor.invoke(PersistenceExceptionTranslationInterceptor.java:137)
2026-08-03T13:19:18.452764508Z  [auth-service]  at org.springframework.aop.framework.ReflectiveMethodInvocation.proceed(ReflectiveMethodInvocation.java:184)
2026-08-03T13:19:18.452767092Z  [auth-service]  at org.springframework.aop.framework.CglibAopProxy$CglibMethodInvocation.proceed(CglibAopProxy.java:765)
2026-08-03T13:19:18.452769692Z  [auth-service]  at org.springframework.aop.framework.CglibAopProxy$DynamicAdvisedInterceptor.intercept(CglibAopProxy.java:717)
2026-08-03T13:19:18.452786955Z  [auth-service]  at org.springframework.aop.support.AopUtils.invokeJoinpointUsingReflection(AopUtils.java:352)
2026-08-03T13:19:18.452790137Z  [auth-service]  at org.springframework.data.repository.core.support.RepositoryMethodInvoker$RepositoryFragmentMethodInvoker.lambda$new$0(RepositoryMethodInvoker.java:277)
2026-08-03T13:19:18.452792855Z  [auth-service]  at org.springframework.data.repository.core.support.RepositoryMethodInvoker.doInvoke(RepositoryMethodInvoker.java:170)
2026-08-03T13:19:18.452795468Z  [auth-service]  at org.springframework.data.repository.core.support.RepositoryMethodInvoker.invoke(RepositoryMethodInvoker.java:158)
2026-08-03T13:19:18.452798344Z  [auth-service]  at org.springframework.data.repository.core.support.RepositoryComposition$RepositoryFragments.invoke(RepositoryComposition.java:516)
2026-08-03T13:19:18.452800948Z  [auth-service]  at org.springframework.data.repository.core.support.RepositoryComposition.invoke(RepositoryComposition.java:285)
2026-08-03T13:19:18.452803899Z  [auth-service]  at org.springframework.data.repository.core.support.RepositoryFactorySupport$ImplementationMethodExecutionInterceptor.invoke(RepositoryFactorySupport.java:628)
2026-08-03T13:19:18.452806690Z  [auth-service]  at org.springframework.aop.framework.ReflectiveMethodInvocation.proceed(ReflectiveMethodInvocation.java:184)
2026-08-03T13:19:18.452809712Z  [auth-service]  at org.springframework.data.repository.core.support.QueryExecutorMethodInterceptor.doInvoke(QueryExecutorMethodInterceptor.java:168)
2026-08-03T13:19:18.452812648Z  [auth-service]  at org.springframework.data.repository.core.support.QueryExecutorMethodInterceptor.invoke(QueryExecutorMethodInterceptor.java:143)
2026-08-03T13:19:18.452815537Z  [auth-service]  at org.springframework.aop.framework.ReflectiveMethodInvocation.proceed(ReflectiveMethodInvocation.java:184)
2026-08-03T13:19:18.452847036Z  [auth-service]  at org.springframework.dao.support.PersistenceExceptionTranslationInterceptor.invoke(PersistenceExceptionTranslationInterceptor.java:137)
2026-08-03T13:19:18.453030300Z  [auth-service]  at org.springframework.security.web.access.ExceptionTranslationFilter.doFilter(ExceptionTranslationFilter.java:126)
2026-08-03T13:19:18.453033339Z  [auth-service]  at org.springframework.security.web.access.ExceptionTranslationFilter.doFilter(ExceptionTranslationFilter.java:120)
2026-08-03T13:19:18.459699388Z  [auth-service]  [2m2026-08-03 22:19:18[0;39m [2m[http-nio-8081-exec-3][0;39m [31mERROR [traceId=6a709556a7b651ba4628e9d2ac58f500,spanId=4628e9d2ac58f500,userId=NONE][0;39m [36mc.e.t.a.c.f.RequestLoggingFilter[0;39m [2m-[0;39m [HTTP] GET /api/user/1/following 500 - 54ms
2026-08-03T13:19:18.547777032Z  [auth-service]  [2m2026-08-03 22:19:18[0;39m [2m[http-nio-8081-exec-10][0;39m [31mERROR [traceId=6a709556f6e16f65ec4dc5deefad718c,spanId=1ca310ee1c13d395,userId=1][0;39m [36mc.e.t.a.c.e.GlobalExceptionHandler[0;39m [2m-[0;39m [api-error] handleAllException
2026-08-03T13:19:18.547794362Z  [auth-service]  java.lang.NullPointerException: Cannot invoke "java.lang.Integer.intValue()" because "this.size" is null
2026-08-03T13:19:18.547833453Z  [auth-service]  at org.springframework.dao.support.PersistenceExceptionTranslationInterceptor.invoke(PersistenceExceptionTranslationInterceptor.java:137)
2026-08-03T13:19:18.547924514Z  [auth-service]  at org.springframework.dao.support.PersistenceExceptionTranslationInterceptor.invoke(PersistenceExceptionTranslationInterceptor.java:137)
2026-08-03T13:19:18.548117711Z  [auth-service]  at org.springframework.security.web.access.ExceptionTranslationFilter.doFilter(ExceptionTranslationFilter.java:126)
2026-08-03T13:19:18.548120076Z  [auth-service]  at org.springframework.security.web.access.ExceptionTranslationFilter.doFilter(ExceptionTranslationFilter.java:120)
2026-08-03T13:19:18.549420548Z  [auth-service]  [2m2026-08-03 22:19:18[0;39m [2m[http-nio-8081-exec-10][0;39m [31mERROR [traceId=6a709556f6e16f65ec4dc5deefad718c,spanId=ec4dc5deefad718c,userId=NONE][0;39m [36mc.e.t.a.c.f.RequestLoggingFilter[0;39m [2m-[0;39m [HTTP] GET /api/user/1/followers 500 - 34ms
2026-08-03T13:20:18.969257876Z  [auth-service]  [2m2026-08-03 22:20:18[0;39m [2m[http-nio-8081-exec-5][0;39m [33m WARN [traceId=6a709592fc9bba07669166205071d1bb,spanId=980b5f7f4498854a,userId=1][0;39m [36morg.hibernate.orm.query[0;39m [2m-[0;39m HHH90003004: firstResult/maxResults specified with collection fetch; applying in memory
2026-08-03T13:20:48.844594457Z  [content-service]  2026-08-03 22:20:48.844 [http-nio-8082-exec-5]  WARN [traceId=6a7095b09dbabfa223fd8e4c12fda927,spanId=1b97a1bfc4ad9320,userId=1] o.h.e.jdbc.spi.SqlExceptionHelper - SQL Error: 1062, SQLState: 23000
2026-08-03T13:20:48.844594457Z  [content-service]  2026-08-03 22:20:48.844 [http-nio-8082-exec-5]  WARN [traceId=6a7095b09dbabfa223fd8e4c12fda927,spanId=1b97a1bfc4ad9320,userId=1] o.h.e.jdbc.spi.SqlExceptionHelper - SQL Error: 1062, SQLState: 23000
2026-08-03T13:20:48.844612326Z  [content-service]  2026-08-03 22:20:48.844 [http-nio-8082-exec-5] ERROR [traceId=6a7095b09dbabfa223fd8e4c12fda927,spanId=1b97a1bfc4ad9320,userId=1] o.h.e.jdbc.spi.SqlExceptionHelper - Duplicate entry '160-177' for key 'tb_feed_hashtags.uk_feed_hashtag'
2026-08-03T13:20:48.844612326Z  [content-service]  2026-08-03 22:20:48.844 [http-nio-8082-exec-5] ERROR [traceId=6a7095b09dbabfa223fd8e4c12fda927,spanId=1b97a1bfc4ad9320,userId=1] o.h.e.jdbc.spi.SqlExceptionHelper - Duplicate entry '160-177' for key 'tb_feed_hashtags.uk_feed_hashtag'
2026-08-03T13:20:48.866047276Z  [content-service]  2026-08-03 22:20:48.850 [http-nio-8082-exec-5]  WARN [traceId=6a7095b09dbabfa223fd8e4c12fda927,spanId=1b97a1bfc4ad9320,userId=1] c.e.t.a.c.e.GlobalExceptionHandler - [api-error] handleAllException
2026-08-03T13:20:48.866047276Z  [content-service]  2026-08-03 22:20:48.850 [http-nio-8082-exec-5]  WARN [traceId=6a7095b09dbabfa223fd8e4c12fda927,spanId=1b97a1bfc4ad9320,userId=1] c.e.t.a.c.e.GlobalExceptionHandler - [api-error] handleAllException
2026-08-03T13:20:48.866068861Z  [content-service]  org.springframework.dao.DataIntegrityViolationException: could not execute statement [Duplicate entry '160-177' for key 'tb_feed_hashtags.uk_feed_hashtag'] [insert into tb_feed_hashtags (created_at,feed_id,hashtag_id,updated_at) values (?,?,?,?)]; SQL [insert into tb_feed_hashtags (created_at,feed_id,hashtag_id,updated_at) values (?,?,?,?)]; constraint [tb_feed_hashtags.uk_feed_hashtag]
2026-08-03T13:20:48.866072218Z  [content-service]  at org.springframework.orm.jpa.vendor.HibernateJpaDialect.convertHibernateAccessException(HibernateJpaDialect.java:290)
2026-08-03T13:20:48.866074118Z  [content-service]  at org.springframework.orm.jpa.vendor.HibernateJpaDialect.translateExceptionIfPossible(HibernateJpaDialect.java:241)
2026-08-03T13:20:48.866076205Z  [content-service]  at org.springframework.orm.jpa.AbstractEntityManagerFactoryBean.translateExceptionIfPossible(AbstractEntityManagerFactoryBean.java:560)
2026-08-03T13:20:48.866078347Z  [content-service]  at org.springframework.dao.support.ChainedPersistenceExceptionTranslator.translateExceptionIfPossible(ChainedPersistenceExceptionTranslator.java:61)
2026-08-03T13:20:48.866082458Z  [content-service]  at org.springframework.dao.support.PersistenceExceptionTranslationInterceptor.invoke(PersistenceExceptionTranslationInterceptor.java:160)
2026-08-03T13:20:48.866244439Z  [content-service]  at org.springframework.security.web.access.ExceptionTranslationFilter.doFilter(ExceptionTranslationFilter.java:126)
2026-08-03T13:20:48.866246025Z  [content-service]  at org.springframework.security.web.access.ExceptionTranslationFilter.doFilter(ExceptionTranslationFilter.java:120)
2026-08-03T13:20:48.866470782Z  [content-service]  Caused by: org.hibernate.exception.ConstraintViolationException: could not execute statement [Duplicate entry '160-177' for key 'tb_feed_hashtags.uk_feed_hashtag'] [insert into tb_feed_hashtags (created_at,feed_id,hashtag_id,updated_at) values (?,?,?,?)]
2026-08-03T13:20:48.866472404Z  [content-service]  at org.hibernate.dialect.MySQLDialect.lambda$buildSQLExceptionConversionDelegate$3(MySQLDialect.java:1245)
2026-08-03T13:20:48.866473933Z  [content-service]  at org.hibernate.exception.internal.StandardSQLExceptionConverter.convert(StandardSQLExceptionConverter.java:58)
2026-08-03T13:20:48.866475458Z  [content-service]  at org.hibernate.engine.jdbc.spi.SqlExceptionHelper.convert(SqlExceptionHelper.java:108)
2026-08-03T13:20:48.866578695Z  [content-service]  at org.springframework.dao.support.PersistenceExceptionTranslationInterceptor.invoke(PersistenceExceptionTranslationInterceptor.java:138)
2026-08-03T13:20:48.866582196Z  [content-service]  Caused by: java.sql.SQLIntegrityConstraintViolationException: Duplicate entry '160-177' for key 'tb_feed_hashtags.uk_feed_hashtag'
2026-08-03T13:20:48.866583828Z  [content-service]  at com.mysql.cj.jdbc.exceptions.SQLError.createSQLException(SQLError.java:109)
2026-08-03T13:20:48.866585400Z  [content-service]  at com.mysql.cj.jdbc.exceptions.SQLExceptionsMapping.translateException(SQLExceptionsMapping.java:114)
2026-08-03T13:20:48.866617691Z  [content-service]  2026-08-03 22:20:48.863 [http-nio-8082-exec-5] ERROR [traceId=6a7095b09dbabfa223fd8e4c12fda927,spanId=23fd8e4c12fda927,userId=NONE] c.e.t.a.c.f.RequestLoggingFilter - [HTTP] POST /api/feeds 500 - 94ms
2026-08-03T13:20:48.866617691Z  [content-service]  2026-08-03 22:20:48.863 [http-nio-8082-exec-5] ERROR [traceId=6a7095b09dbabfa223fd8e4c12fda927,spanId=23fd8e4c12fda927,userId=NONE] c.e.t.a.c.f.RequestLoggingFilter - [HTTP] POST /api/feeds 500 - 94ms
```

### 메트릭 시계열

| 쿼리 | series | 점 | min | max | last | 값이 0이던 구간 |
|---|---|---:|---:|---:|---:|---|
| `hikaricp_connections_active` | `{__name__=hikaricp_connections_active, application=auth-service, cluster=yogurtte-k3s-prod, container=auth-service, instance=10.42.1.44:8090, job=auth-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=auth-service-5999bb9f5c-qqrss, pool=HikariPool-1, service=auth-service}` | 241 | 0 | 0 | 0 | **2026-08-03T12:24:09Z ~ 2026-08-03T13:24:09Z** |
| `hikaricp_connections_active` | `{__name__=hikaricp_connections_active, application=chat-service, cluster=yogurtte-k3s-prod, container=chat-service, instance=10.42.3.43:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-fdcc7c776-qrbc2, pool=HikariPool-1}` | 241 | 0 | 0 | 0 | **2026-08-03T12:24:09Z ~ 2026-08-03T13:24:09Z** |
| `hikaricp_connections_active` | `{__name__=hikaricp_connections_active, application=content-service, cluster=yogurtte-k3s-prod, container=content-service, instance=10.42.1.43:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-6995bb7d94-h2f6n, pool=HikariPool-1}` | 241 | 0 | 0 | 0 | **2026-08-03T12:24:09Z ~ 2026-08-03T13:24:09Z** |
| `hikaricp_connections_active` | `{__name__=hikaricp_connections_active, application=content-service, cluster=yogurtte-k3s-prod, container=content-service, instance=10.42.3.42:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-6995bb7d94-nq9l2, pool=HikariPool-1}` | 241 | 0 | 0 | 0 | **2026-08-03T12:24:09Z ~ 2026-08-03T13:24:09Z** |
| `hikaricp_connections_pending` | `{__name__=hikaricp_connections_pending, application=auth-service, cluster=yogurtte-k3s-prod, container=auth-service, instance=10.42.1.44:8090, job=auth-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=auth-service-5999bb9f5c-qqrss, pool=HikariPool-1, service=auth-service}` | 241 | 0 | 0 | 0 | **2026-08-03T12:24:09Z ~ 2026-08-03T13:24:09Z** |
| `hikaricp_connections_pending` | `{__name__=hikaricp_connections_pending, application=chat-service, cluster=yogurtte-k3s-prod, container=chat-service, instance=10.42.3.43:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-fdcc7c776-qrbc2, pool=HikariPool-1}` | 241 | 0 | 0 | 0 | **2026-08-03T12:24:09Z ~ 2026-08-03T13:24:09Z** |
| `hikaricp_connections_pending` | `{__name__=hikaricp_connections_pending, application=content-service, cluster=yogurtte-k3s-prod, container=content-service, instance=10.42.1.43:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-6995bb7d94-h2f6n, pool=HikariPool-1}` | 241 | 0 | 0 | 0 | **2026-08-03T12:24:09Z ~ 2026-08-03T13:24:09Z** |
| `hikaricp_connections_pending` | `{__name__=hikaricp_connections_pending, application=content-service, cluster=yogurtte-k3s-prod, container=content-service, instance=10.42.3.42:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-6995bb7d94-nq9l2, pool=HikariPool-1}` | 241 | 0 | 0 | 0 | **2026-08-03T12:24:09Z ~ 2026-08-03T13:24:09Z** |
| `rate(jvm_gc_pause_seconds_sum[5m])` | `{action=end of major GC, application=chat-service, cause=Allocation Failure, cluster=yogurtte-k3s-prod, container=chat-service, gc=MarkSweepCompact, instance=10.42.3.43:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-fdcc7c776-qrbc2}` | 241 | 0 | 0 | 0 | **2026-08-03T12:24:09Z ~ 2026-08-03T13:24:09Z** |
| `rate(jvm_gc_pause_seconds_sum[5m])` | `{action=end of minor GC, application=auth-service, cause=G1 Evacuation Pause, cluster=yogurtte-k3s-prod, container=auth-service, gc=G1 Young Generation, instance=10.42.1.44:8090, job=auth-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=auth-service-5999bb9f5c-qqrss, service=auth-service}` | 241 | 0 | 0.000 | 0 | **2026-08-03T12:24:09Z ~ 2026-08-03T12:25:09Z, 2026-08-03T12:29:24Z ~ 2026-08-03T13:05:09Z, 2026-08-03T13:09:24Z ~ 2026-08-03T13:24:09Z** |
| `rate(jvm_gc_pause_seconds_sum[5m])` | `{action=end of minor GC, application=auth-service, cause=Metadata GC Threshold, cluster=yogurtte-k3s-prod, container=auth-service, gc=G1 Young Generation, instance=10.42.1.44:8090, job=auth-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=auth-service-5999bb9f5c-qqrss, service=auth-service}` | 241 | 0 | 0 | 0 | **2026-08-03T12:24:09Z ~ 2026-08-03T13:24:09Z** |
| `rate(jvm_gc_pause_seconds_sum[5m])` | `{action=end of minor GC, application=chat-service, cause=Allocation Failure, cluster=yogurtte-k3s-prod, container=chat-service, gc=Copy, instance=10.42.3.43:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-fdcc7c776-qrbc2}` | 241 | 0.000 | 0.000 | 0.000 | — |
| `rate(jvm_gc_pause_seconds_sum[5m])` | `{action=end of minor GC, application=content-service, cause=Allocation Failure, cluster=yogurtte-k3s-prod, container=content-service, gc=Copy, instance=10.42.1.43:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-6995bb7d94-h2f6n}` | 241 | 0 | 0.000 | 0 | **2026-08-03T12:28:09Z ~ 2026-08-03T12:39:54Z, 2026-08-03T12:44:09Z ~ 2026-08-03T12:55:54Z, 2026-08-03T13:00:09Z ~ 2026-08-03T13:11:54Z, 2026-08-03T13:16:09Z ~ 2026-08-03T13:24:09Z** |
| `rate(jvm_gc_pause_seconds_sum[5m])` | `{action=end of minor GC, application=content-service, cause=Allocation Failure, cluster=yogurtte-k3s-prod, container=content-service, gc=Copy, instance=10.42.3.42:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-6995bb7d94-nq9l2}` | 241 | 0 | 0.001 | 0.000 | **2026-08-03T12:24:09Z ~ 2026-08-03T12:24:39Z, 2026-08-03T12:28:54Z ~ 2026-08-03T12:37:39Z, 2026-08-03T12:41:54Z ~ 2026-08-03T12:52:39Z, 2026-08-03T12:56:54Z ~ 2026-08-03T13:07:39Z, 2026-08-03T13:11:54Z ~ 2026-08-03T13:21:39Z** |
| `up` | `{__name__=up, app=node-exporter, cluster=yogurtte-k3s-prod, component=metrics, container=node-exporter, instance=ip-172-31-40-241, job=integrations/node_exporter, k8s_cluster_name=yogurtte-k3s-prod, namespace=monitoring, pod=grafana-k8s-monitoring-node-exporter-cph5l, source=kubernetes, workload=DaemonSet/grafana-k8s-monitoring-node-exporter}` | 241 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, app=node-exporter, cluster=yogurtte-k3s-prod, component=metrics, container=node-exporter, instance=ip-172-31-45-39, job=integrations/node_exporter, k8s_cluster_name=yogurtte-k3s-prod, namespace=monitoring, pod=grafana-k8s-monitoring-node-exporter-9kvqt, source=kubernetes, workload=DaemonSet/grafana-k8s-monitoring-node-exporter}` | 241 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, cluster=yogurtte-k3s-prod, container=auth-service, instance=10.42.1.44:8090, job=auth-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=auth-service-5999bb9f5c-qqrss}` | 241 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, cluster=yogurtte-k3s-prod, container=chat-service, instance=10.42.3.43:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-fdcc7c776-qrbc2}` | 241 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, cluster=yogurtte-k3s-prod, container=content-service, instance=10.42.1.43:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-6995bb7d94-h2f6n}` | 241 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, cluster=yogurtte-k3s-prod, container=content-service, instance=10.42.3.42:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-6995bb7d94-nq9l2}` | 241 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, cluster=yogurtte-k3s-prod, instance=10.42.1.248:8080, job=integrations/kubernetes/kube-state-metrics, k8s_cluster_name=yogurtte-k3s-prod, source=kubernetes}` | 241 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, cluster=yogurtte-k3s-prod, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod}` | 241 | 1 | 1 | 1 | — |
| `mongodb_up` | `{__name__=mongodb_up, cluster=yogurtte-k3s-prod, instance=infra-server, job=mongodb, k8s_cluster_name=yogurtte-k3s-prod}` | 241 | 1 | 1 | 1 | — |
| `kafka_brokers` | `{__name__=kafka_brokers, cluster=yogurtte-k3s-prod, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod}` | 241 | 1 | 1 | 1 | — |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=chat-service-fcm-tokens, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=0, topic=user.fcm-tokens}` | 241 | 0 | 0 | 0 | **2026-08-03T12:24:09Z ~ 2026-08-03T13:24:09Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=chat-service-fcm-tokens, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=1, topic=user.fcm-tokens}` | 241 | 0 | 0 | 0 | **2026-08-03T12:24:09Z ~ 2026-08-03T13:24:09Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=chat-service-fcm-tokens, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=2, topic=user.fcm-tokens}` | 241 | 0 | 0 | 0 | **2026-08-03T12:24:09Z ~ 2026-08-03T13:24:09Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=chat-service-notification-settings, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=0, topic=user.notification-settings}` | 241 | 0 | 0 | 0 | **2026-08-03T12:24:09Z ~ 2026-08-03T13:24:09Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=chat-service-notification-settings, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=1, topic=user.notification-settings}` | 241 | 0 | 0 | 0 | **2026-08-03T12:24:09Z ~ 2026-08-03T13:24:09Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=chat-service-notification-settings, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=2, topic=user.notification-settings}` | 241 | 0 | 0 | 0 | **2026-08-03T12:24:09Z ~ 2026-08-03T13:24:09Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=db-writer, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=0, topic=chat.messages}` | 241 | 0 | 0 | 0 | **2026-08-03T12:24:09Z ~ 2026-08-03T13:24:09Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=db-writer, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=1, topic=chat.messages}` | 241 | 0 | 0 | 0 | **2026-08-03T12:24:09Z ~ 2026-08-03T13:24:09Z** |
| `websocket_active_users` | `{__name__=websocket_active_users, application=chat-service, cluster=yogurtte-k3s-prod, container=chat-service, instance=10.42.3.43:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-fdcc7c776-qrbc2}` | 241 | 0 | 0 | 0 | **2026-08-03T12:24:09Z ~ 2026-08-03T13:24:09Z** |

값이 0이던 구간은 굵게 표시했다 — 프로세스가 사라져 시계열이 꺾인 것이 유일한 신호인 장애가 있다.

