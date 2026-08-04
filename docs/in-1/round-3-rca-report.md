# RCA Report — `scan-1785807300`

| 항목 | 값 |
|---|---|
| 모드 | rca |
| 질문 | 최근 1시간 안에 문의가 몇 건 들어왔어요. ① 로그인이 느리다 ② 친구가 접속해 있는데 오프라인으로 보인다 ③ 피드에 작성자 이름이 이상하다 |
| 시각 | 2026-08-04T01:53:02.017622800Z |
| provider | claude-cli |
| model | `claude-opus-5` · turns 1 |
| prompt | `.\prompts\system-prompt.md` |
| tokens | in 246620 (cacheRead 23,447 · cacheCreate 223,171) / out 8912 · cost $2.4662 |
| elapsed | total 153781ms (tempo 6630 · loki 802 · mimir 871 · assemble 32 · llm 138055) |

## 탐색 (Triage)

| 항목 | 값 |
|---|---|
| 시간창 해석 | 상대 표현 '최근 1시간' |
| 시간창 확신도 | EXACT |
| 스윕 창 | 2026-08-04T00:52:02.949530700Z ~ 2026-08-04T01:52:02.949530700Z |
| 좁힌 창 | 2026-08-04T01:35:00Z ~ 2026-08-04T01:52:02.949530700Z |
| 대상 | auth-service, content-service, chat-service |
| traceId | 6a7143d774c29419e907f79330eb97ab |
| 트레이스 후보 | 17건 |
| 장애 후보 | 15건 · 선택 INC-7, INC-8, INC-9, INC-10, INC-11, INC-12, INC-13, INC-14, INC-15 |
| 계획 파싱 | 성공 |
| 스윕 컨텍스트 | 후보 + 원본 (A) |
| prompt | `.\prompts\triage-prompt.md` |
| tokens | in 62195 / out 2922 · cost $0.4722 |
| chars | 컨텍스트 58,705 + 프롬프트 1,399 = **60,104** |
| elapsed | survey 1311ms · llm 57737ms |

**선정 이유**: 질문이 접수한 세 증상이 전부 01:43~01:46Z 한 구간에 에러 없는 지연으로 동시에 찍혔고, 그 지연 트레이스들이 auth-service 스팬을 공통으로 포함하므로 세 서비스를 한 덩어리로 함께 파야 한다.

**근거**

- 01:40~01:50Z 한 구간에 content 88+40건, chat 22+18건, auth 1건 ERROR/WARN이 동시 발생 (Loki, 세 서비스 동시성이 이 창의 유일한 특징)
- 증상 ③ 피드 작성자 이름: content /feeds/scroll 12,132 / 20,273 / 12,167 / 4,100 / 20,210ms (01:43:51~01:44:33, slow 채널·status=unset) — 실패가 아니라 지연
- 증상 ① 로그인 지연: 위 /feeds/scroll·/battles 지연 트레이스 5건 모두 serviceStats에 auth-service spanCount 4가 포함 — auth 호출 구간이 공통으로 끼어 있음
- 증상 ② 접속 상태 오표시: chat /notifications 4,193ms, /notifications/unread/count 4,150ms, /v1/chat/rooms 4,426ms가 01:43:57에 거의 동시 발화 — 세 엔드포인트가 같은 4초대에 몰린 건 공통 하류 대기 지문
- content /battles 8,136 / 8,080ms (01:44:01~01:44:18) — 같은 창의 두 번째 읽기 경로도 동일하게 지연
- content task battle-deadline-notification-scheduler.notify가 01:44:00~01:46:02에 6회 모두 2,000~2,025ms 에러 — 편차 없는 2초 고정은 코드 오류가 아니라 하류 타임아웃 지문
- websocket_active_users가 창 전체에서 max 0 — chat이 살아 있는데(up=1) 활성 세션 지표가 0인 상태는 증상 ②(오프라인으로 보임)와 방향이 일치, 다만 창 전체 상수라 기저값일 가능성도 있어 확인 필요
- kafka up은 01:32:02에 이미 1로 복구, kafka_brokers·consumergroup_lag·mongodb_up은 이상 신호 0건 — 01:40대 클러스터를 인프라 다운으로 설명할 수 없음

**스윕이 찾은 트레이스** (고른 것은 6a7143d774c29419e907f79330eb97ab)

| traceId | 채널 | root service | root span | ms |
|---|---|---|---|---:|
| `6a71445815dd4f286f3ed3bb625a1617` | error | content-service | task battle-deadline-notification-scheduler.notify | 2025 |
| `6a714458378a5c308cfc34c8a059ea7f` | error | content-service | task battle-deadline-notification-scheduler.notify | 2004 |
| `6a71441c2f3060541a3d785fe3ea3384` | error | content-service | task battle-deadline-notification-scheduler.notify | 2000 |
| `6a71441cd5173bd8297a5421e350c724` | error | content-service | task battle-deadline-notification-scheduler.notify | 2000 |
| `6a7143e0a9942f382e852850fd29fb4c` | error | content-service | task battle-deadline-notification-scheduler.notify | 2014 |
| `6a7143e082c74b3152c8df3c197893d8` | error | content-service | task battle-deadline-notification-scheduler.notify | 2000 |
| `6a713eb5c566f210b35a9bd582a5f37a` | error | content-service | http post /battles/{battleId}/items/{itemId}/comments | 60050 |
| `6a7143eddbdaf842e9effecde235f6ad` | slow | content-service | http get /feeds/scroll | 20210 |
| `6a7143ea5c0cd0dc702e73b937fd214f` | slow | content-service | http get /battles | 8080 |
| `6a7143e704eb195a52270ee000837f32` | slow | content-service | http get /feeds/scroll | 4100 |
| `6a7143e4287a19a4e9a0d5de733b7034` | slow | content-service | http get /feeds/scroll | 12167 |
| `6a7143e1c6ffb37a1a1a3fe16847012a` | slow | content-service | http get /battles | 8136 |
| `6a7143dd79de693c7fdfe44e82044ae0` | slow | chat-service | http get /v1/chat/rooms | 4426 |
| `6a7143dd1167ffd7b5b9bfad7065552c` | slow | chat-service | http get /notifications/unread/count | 4150 |
| `6a7143dd99a9504b914d9d2963f94b11` | slow | chat-service | http get /notifications | 4193 |
| `6a7143d985f93b95b082c5fb020b7958` | slow | content-service | http get /feeds/scroll | 20273 |
| `6a7143d774c29419e907f79330eb97ab` ←선택 | slow | content-service | http get /feeds/scroll | 12132 |

**장애 후보** (코드가 신호를 묶은 것 · 창은 여기서 계산됨)

## INC-1  chat-service  |  ERROR/WARN
- 구간: 2026-08-04T00:45:00Z ~ 2026-08-04T00:50:00Z  (LOKI · 집계 해상도만큼 흐림)
- ERROR/WARN 16건 (2026-08-04T00:45:00Z ~ 2026-08-04T00:50:00Z)
- 같은 시각의 다른 후보: INC-2  (인과 여부는 판단하지 않았다)

## INC-2  content-service  |  ERROR/WARN
- 구간: 2026-08-04T00:45:00Z ~ 2026-08-04T00:55:00Z  (LOKI · 집계 해상도만큼 흐림)
- ERROR/WARN 301건 (2026-08-04T00:45:00Z ~ 2026-08-04T00:50:00Z)
- ERROR/WARN 154건 (2026-08-04T00:50:00Z ~ 2026-08-04T00:55:00Z)
- 같은 시각의 다른 후보: INC-1, INC-3  (인과 여부는 판단하지 않았다)

## INC-3  kafka  |  up
- 구간: 2026-08-04T00:52:02Z ~ 2026-08-04T00:57:02Z  (MIMIR · 집계 해상도만큼 흐림)
- up 가 0이었다 (2026-08-04T00:52:02Z ~ 2026-08-04T00:52:02Z)
- up 0 → 1
- 같은 시각의 다른 후보: INC-2  (인과 여부는 판단하지 않았다)

## INC-4  kafka  |  up
- 구간: 2026-08-04T01:17:02Z ~ 2026-08-04T01:32:02Z  (MIMIR · 집계 해상도만큼 흐림)
- up 1 → 0
- up 가 0이었다 (2026-08-04T01:22:02Z ~ 2026-08-04T01:27:02Z)
- up 0 → 1
- 같은 시각의 다른 후보: INC-5, INC-6  (인과 여부는 판단하지 않았다)

## INC-5  content-service  |  ERROR/WARN
- 구간: 2026-08-04T01:20:00Z ~ 2026-08-04T01:25:00Z  (LOKI · 집계 해상도만큼 흐림)
- ERROR/WARN 405건 (2026-08-04T01:20:00Z ~ 2026-08-04T01:25:00Z)
- 같은 시각의 다른 후보: INC-4, INC-6  (인과 여부는 판단하지 않았다)

## INC-6  content-service  |  http post /battles/{battleId}/items/{itemId}/comments
- 구간: 2026-08-04T01:21:57.004052Z ~ 2026-08-04T01:22:57.054052Z  (TEMPO · 시각 정확)
- content-service http post /battles/{battleId}/items/{itemId}/comments 60,050ms (error 채널)
- traceId: 6a713eb5c566f210b35a9bd582a5f37a
- 같은 시각의 다른 후보: INC-4, INC-5  (인과 여부는 판단하지 않았다)

## INC-7  auth-service  |  ERROR/WARN
- 구간: 2026-08-04T01:40:00Z ~ 2026-08-04T01:45:00Z  (LOKI · 집계 해상도만큼 흐림)
- ERROR/WARN 1건 (2026-08-04T01:40:00Z ~ 2026-08-04T01:45:00Z)
- 같은 시각의 다른 후보: INC-8, INC-9, INC-10, INC-11, INC-12, INC-13, INC-14, INC-15  (인과 여부는 판단하지 않았다)

## INC-8  chat-service  |  ERROR/WARN
- 구간: 2026-08-04T01:40:00Z ~ 2026-08-04T01:50:00Z  (LOKI · 집계 해상도만큼 흐림)
- ERROR/WARN 22건 (2026-08-04T01:40:00Z ~ 2026-08-04T01:45:00Z)
- ERROR/WARN 18건 (2026-08-04T01:45:00Z ~ 2026-08-04T01:50:00Z)
- 같은 시각의 다른 후보: INC-7, INC-9, INC-10, INC-11, INC-12, INC-13, INC-14, INC-15  (인과 여부는 판단하지 않았다)

## INC-9  content-service  |  ERROR/WARN
- 구간: 2026-08-04T01:40:00Z ~ 2026-08-04T01:50:00Z  (LOKI · 집계 해상도만큼 흐림)
- ERROR/WARN 88건 (2026-08-04T01:40:00Z ~ 2026-08-04T01:45:00Z)
- ERROR/WARN 40건 (2026-08-04T01:45:00Z ~ 2026-08-04T01:50:00Z)
- 같은 시각의 다른 후보: INC-7, INC-8, INC-10, INC-11, INC-12, INC-13, INC-14, INC-15  (인과 여부는 판단하지 않았다)

## INC-10  content-service  |  http get /feeds/scroll
- 구간: 2026-08-04T01:43:51.145657Z ~ 2026-08-04T01:44:33.699895Z  (TEMPO · 시각 정확)
- content-service http get /feeds/scroll 12,132ms (slow 채널)
- content-service http get /feeds/scroll 20,273ms (slow 채널)
- content-service http get /feeds/scroll 12,167ms (slow 채널)
- content-service http get /feeds/scroll 4,100ms (slow 채널)
- content-service http get /feeds/scroll 20,210ms (slow 채널)
- traceId: 6a7143d774c29419e907f79330eb97ab, 6a7143d985f93b95b082c5fb020b7958, 6a7143e4287a19a4e9a0d5de733b7034, 6a7143e704eb195a52270ee000837f32, 6a7143eddbdaf842e9effecde235f6ad
- 같은 시각의 다른 후보: INC-7, INC-8, INC-9, INC-11, INC-12, INC-13, INC-14, INC-15  (인과 여부는 판단하지 않았다)

## INC-11  chat-service  |  http get /notifications
- 구간: 2026-08-04T01:43:57.524610Z ~ 2026-08-04T01:44:01.717610Z  (TEMPO · 시각 정확)
- chat-service http get /notifications 4,193ms (slow 채널)
- traceId: 6a7143dd99a9504b914d9d2963f94b11
- 같은 시각의 다른 후보: INC-7, INC-8, INC-9, INC-10, INC-12, INC-13, INC-14, INC-15  (인과 여부는 판단하지 않았다)

## INC-12  chat-service  |  http get /notifications/unread/count
- 구간: 2026-08-04T01:43:57.580483Z ~ 2026-08-04T01:44:01.730483Z  (TEMPO · 시각 정확)
- chat-service http get /notifications/unread/count 4,150ms (slow 채널)
- traceId: 6a7143dd1167ffd7b5b9bfad7065552c
- 같은 시각의 다른 후보: INC-7, INC-8, INC-9, INC-10, INC-11, INC-13, INC-14, INC-15  (인과 여부는 판단하지 않았다)

## INC-13  chat-service  |  http get /v1/chat/rooms
- 구간: 2026-08-04T01:43:57.586028Z ~ 2026-08-04T01:44:02.012028Z  (TEMPO · 시각 정확)
- chat-service http get /v1/chat/rooms 4,426ms (slow 채널)
- traceId: 6a7143dd79de693c7fdfe44e82044ae0
- 같은 시각의 다른 후보: INC-7, INC-8, INC-9, INC-10, INC-11, INC-12, INC-14, INC-15  (인과 여부는 판단하지 않았다)

## INC-14  content-service  |  task battle-deadline-notification-scheduler.notify
- 구간: 2026-08-04T01:44:00.000179Z ~ 2026-08-04T01:46:02.025886Z  (TEMPO · 시각 정확)
- content-service task battle-deadline-notification-scheduler.notify 2,000ms (error 채널)
- content-service task battle-deadline-notification-scheduler.notify 2,014ms (error 채널)
- content-service task battle-deadline-notification-scheduler.notify 2,000ms (error 채널)
- content-service task battle-deadline-notification-scheduler.notify 2,000ms (error 채널)
- content-service task battle-deadline-notification-scheduler.notify 2,004ms (error 채널)
- content-service task battle-deadline-notification-scheduler.notify 2,025ms (error 채널)
- traceId: 6a7143e082c74b3152c8df3c197893d8, 6a7143e0a9942f382e852850fd29fb4c, 6a71441cd5173bd8297a5421e350c724, 6a71441c2f3060541a3d785fe3ea3384, 6a714458378a5c308cfc34c8a059ea7f, 6a71445815dd4f286f3ed3bb625a1617
- 같은 시각의 다른 후보: INC-7, INC-8, INC-9, INC-10, INC-11, INC-12, INC-13, INC-15  (인과 여부는 판단하지 않았다)

## INC-15  content-service  |  http get /battles
- 구간: 2026-08-04T01:44:01.127503Z ~ 2026-08-04T01:44:18.234698Z  (TEMPO · 시각 정확)
- content-service http get /battles 8,136ms (slow 채널)
- content-service http get /battles 8,080ms (slow 채널)
- traceId: 6a7143e1c6ffb37a1a1a3fe16847012a, 6a7143ea5c0cd0dc702e73b937fd214f
- 같은 시각의 다른 후보: INC-7, INC-8, INC-9, INC-10, INC-11, INC-12, INC-13, INC-14  (인과 여부는 판단하지 않았다)

**기각한 후보**

- INC-1 — 00:45~00:50Z chat 16건은 증상 접수 시각보다 55분 이상 앞서고 같은 창의 content 폭주(INC-2)에 딸린 잡음으로 보여 이번 문의와 연결할 근거가 없다.
- INC-2 — 00:45~00:55Z content 301+154건은 kafka up=0(INC-3)과 같은 시각의 별개 에러 폭발이며, 01:00 이전에 종료되어 '최근 문의' 증상과 시각이 맞지 않는다.
- INC-3 — 00:52Z kafka up 0→1 순간 flap은 즉시 복구됐고 kafka_brokers·consumergroup lag 모두 정상이라 하류 증상으로 이어진 흔적이 없다.
- INC-4 — 01:22~01:27Z kafka 다운은 01:32:02에 up=1로 복구됐고, 증상 클러스터(01:43~01:46)까지 lag 증가가 전혀 없어(max lag 0 유지) 인과가 끊긴다 — 다만 재발성 flap이므로 별건으로 추적할 가치는 있다.
- INC-5 — 01:20~01:25Z content 405건은 INC-4 kafka 다운과 시각이 겹치는 앞선 별개 장애이고, 01:25 이후 로그가 0으로 떨어져 이번 증상 시점과 단절된다.
- INC-6 — 01:21:57Z 댓글 POST 60,050ms 타임아웃도 kafka 다운 구간의 쓰기 경로 실패로, 증상 ①②③ 어디에도 해당하지 않고 시각도 20분 이르다.

**보정 기록**

- 창을 후보 [INC-7, INC-8, INC-9, INC-10, INC-11, INC-12, INC-13, INC-14, INC-15] 의 신호 시각에서 계산했다 (2026-08-04T01:35:00Z ~ 2026-08-04T01:52:02.949530700Z)

## 수집 범위 (Coverage)

- **window**: 2026-08-04T01:35:00Z ~ 2026-08-04T01:52:02.949530700Z (1022s)
- **trace**: 204,177B / 214 spans (상위 span만)
- **창 안 후보 트레이스**: 16건 / 204,177B
- **logs**: errwarn=179,041B · traceId=22,956B
- **metrics**: 8 수집 / 120,535B, 누락 [sum(rate(http_server_requests_seconds_count{application="content-service", status="401"}[1m]))]
- **context**: 431,819 chars (+ 시스템 프롬프트 598 chars)

### 토큰 축 (개선 지표)

| 단계 | 총 in | chars | 컨텍스트 토큰 |
|---|---:|---:|---:|
| 탐색 | 62,195 | 60,104 | 32,771 |
| 분석 | 246,620 | 432,417 | 217,196 |
| **합계** | **308,815** | | **249,967** |

- **overheadTokens 29,424 tok — 이 회차에 실측**(1자 프롬프트 프로브, 같은 명령·모델·샌드박스).
  호출마다 한 번씩 얹히므로 단계별로 뺀다. **다른 날 상수를 쓰지 않았다 = `█ 실측`.**
- contextTokens (count_tokens API): 측정 안 됨 — 구독 CLI 경로엔 API 키가 없다
- 비용은 **API 환산 추정치**다. 구독 계정으로 호출하므로 실제 청구액이 아니다.

## 수집 실패/누락

- Metric 'sum(rate(http_server_requests_seconds_count{application="content-service", status="401"}[1m]))' returned no series in this window; skipped.

---

관측 데이터만으로 정리했다. 로그 타임스탬프는 KST(+09:00), 조회창은 UTC라 병기한다 (10:43:44 KST = 01:43:44 UTC).

## 1. 원인 후보 랭킹 (최대 3개)

1. **Redis 인스턴스(172.31.46.124:6379) 다운** — 세 증상 중 ①(느림)의 직접 원인, ②의 유력 원인
2. **content-service의 사용자 정보 조회가 Redis 2초 타임아웃을 유저 수만큼 직렬 반복 (지연 증폭)** — 20초짜리 요청을 만든 구조적 원인
3. **③ "피드 작성자 이름이 이상하다" — 데이터 부족**

---

## 2. 후보별 근거

### 후보 1. Redis 인스턴스 다운

**근거**
- 로그 원문 (content-service, chat-service 양쪽 pod 전부):
  `i.l.core.protocol.ConnectionWatchdog - Cannot reconnect to [172.31.46.124/<unresolved>:6379]: finishConnect(..) failed: Connection refused: /172.31.46.124:6379`
  최초 관측 10:43:44.206 (01:43:44 UTC), 10:43:53, 10:43:44(chat) 등 반복.
- `Caused by: io.lettuce.core.RedisCommandTimeoutException: Command timed out after 2 second(s)` → `org.springframework.dao.QueryTimeoutException: Redis command timed out`
- `o.s.b.a.d.r.RedisReactiveHealthIndicator - Redis health check failed` (content-service 10:43:49.402 / 10:43:55.806, chat-service 10:43:50.314)
- 스케줄러 span `task battle-deadline-notification-scheduler.notify`가 두 pod에서 60초 주기로 계속 실패: `error="Redis command timed out"`, `exception="QueryTimeoutException"`, `status=STATUS_CODE_ERROR`. 01:44:00, 01:45:00, 01:46:00 UTC 모두 동일 — **일회성이 아니라 지속 상태**.
- **호스트 장애가 아니라 Redis 프로세스 장애**: 동일 IP 172.31.46.124의 MongoDB(27017)는 같은 시각 정상 응답 (`find toychat` 92ms, `aggregate toychat` 86ms, chat-service 트레이스 6a7143dd99a9…). `mongodb_up=1` 전 구간, `up{job=node-infra}=1` 전 구간.
- 부수 관측: content/chat pod의 스크레이프 결측 구간(01:44:30~01:47:45 UTC)이 Redis 타임아웃 폭주 구간과 겹친다. actuator 헬스체크가 Redis에 물려 있는 점(`RedisReactiveHealthIndicator`)과 정합적.

**확신도: 높음**

**반증 데이터**
- `up{job=redis, instance=infra-server}` = 전 구간 1 (변화 없음). 단, 이 시리즈는 **redis_exporter 스크레이프 성공 여부**이지 Redis 서버 자체 생존이 아니다. `redis_up` 계열 메트릭은 수집되지 않았다. 애플리케이션 3개 pod가 모두 `Connection refused`를 받은 사실이 더 직접적인 증거라 판단했으나, 이 불일치는 조치 1번으로 확인이 필요하다.

---

### 후보 2. 캐시 실패 시 유저별 직렬 2초 타임아웃 (지연 증폭)

**근거**
- 스택트레이스가 경로를 특정한다:
  `UserCacheStore.getCachedValue(UserCacheStore.java:49)` → `UserCacheStore.getCachedUserInfos(:42)` → `ExternalUserInfoService.lambda$getUserInfos$2(:108)` / `getUserInfos(:110)` → `FeedService.toListView(FeedService.java:138)` → `FeedService.getFeedsWithCursor(:86)`
- 로그가 **유저 1명당 정확히 2초 간격**으로 찍힌다 (trace 6a7143d985f93b95b082c5fb020b7958):
  - 조회 실패: `user:info:1` 10:43:55.224 → `:3` 57.230 → `:7` 59.235 → `:9` 44:01.242 → `:56` 03.271 (5회 × 2s = 10s)
  - 그 사이 auth 호출 1회: `http get http://auth-service:8081/api/external/users?userIds=1,3,7,9,56` **119.3ms, status 200**
  - 저장 실패: `userId=1` 10:44:05.412 → `3` 07.417 → `7` 09.421 → `9` 11.426 → `56` 13.431 (5회 × 2s = 10s)
  - 결과: `[HTTP-SLOW] GET /api/feeds/scroll 200 - 20272ms`
- **지연 = 4초 × 고유 유저 수** 가 모든 느린 요청에서 성립한다:

| trace | 유저 수 | 예측 | 실측 |
|---|---|---|---|
| 6a7143d985f9… /feeds/scroll | 5 (1,3,7,9,56) | 20s | **20272ms** |
| 6a7143eddbda… /feeds/scroll | 5 (1,3,7,9,56) | 20s | **20208ms** |
| 6a7143d774c2… /feeds/scroll | 3 (1,3,7) | 12s | **12132ms** |
| 6a7143e4287a… /feeds/scroll | 3 (1,3,7) | 12s | **12167ms** |
| 6a7143e1c6ff… /battles | 2 (7,9) | 8s | **8136ms** |
| 6a7143ea5c0c… /battles | 2 (7,9) | 8s | **8080ms** |
| 6a7143e704eb… /feeds/scroll | 1 (1) | 4s | **4099ms** |

- 부수 위험: 이 20초 동안 JDBC 커넥션이 잡혀 있다. `connection` span (spanId zx7XFQ5b7m8) `acquired` 01:43:53.208 → `commit` 01:44:13.474, 총 **20269.6ms**. 즉 `@Transactional` 트랜잭션 안에서 Redis·HTTP 외부 호출을 하고 있다.
- chat-service도 같은 2초 벽에 걸린 것으로 보인다: `/api/notifications 200 - 3577ms`, `/api/v1/chat/rooms 200 - 4264ms`, `/api/notifications/unread/count 200 - 2263ms`. 내부 Mongo span 합은 90~180ms에 불과해 나머지 ~2초가 설명되지 않는다.

**확신도: 높음** (content-service), chat-service 부분은 중간

**반증 데이터**
- DB·auth 쪽은 정상이라 지연 원인이 아니다: auth-service `/external/users` 20~119ms, 모두 `status=200 outcome=SUCCESS`. MySQL 개별 query span 전부 2~15ms. `hikaricp_connections_active`/`pending` 전 구간 0 (커넥션 풀 고갈 아님). `jvm_gc_pause_seconds` 최대 5.1e-4 (GC 아님). `kafka_consumergroup_lag` 전 구간 0. — 즉 이들은 후보 2를 반증하지 않고, 오히려 다른 원인들을 배제한다.
- chat-service 경로에 대한 Redis 스택트레이스는 traceId가 `NONE`이라 특정 요청과 직접 결합되지 않았다. 이 부분은 추정이다.

---

### 후보 3. "피드 작성자 이름이 이상하다" — 데이터 부족

**근거**
- 이름이 잘못 매핑되었음을 보여주는 관측값이 **하나도 없다**. auth-service의 사용자 조회는 전부 성공했다: `[external-api] 외부 사용자 목록 조회 - userIds: [1, 3, 7, 9, 56]`, span `http get /external/users` `status=200`, `exception=none`.
- Redis 폴백 경로 자체는 동작 중이다 — 캐시 조회 실패 후 auth-service를 호출해 200을 받는 흐름이 모든 느린 트레이스에서 확인된다.

**확신도: 낮음** (원인 미상)

**반증 데이터 (캐시 오염 가설에 대한)**
- 캐시가 오래된/잘못된 값을 서빙해서 이름이 틀렸다는 가설은 성립하지 않는다. Redis는 **읽기도 쓰기도 전부 실패**하고 있다(`Redis 캐시 조회 실패` + `Redis 캐시 저장 실패` 양쪽 로그 존재). 캐시에서 나오는 값 자체가 없다.
- 수집 실패 항목 때문에 확신도를 더 낮춘다: `http_server_requests_seconds_count{application="content-service", status="401"}` 시리즈 없음 → 인증 실패로 인한 사용자 식별 오류 가능성을 검증할 수 없다.

**추가 수집 필요**
- 이름이 틀린 실제 피드 ID / 작성자 ID 사례 (문의 원문)
- `ExternalUserInfoService.getUserInfos` 반환값 로그 (요청 userId 리스트 ↔ 응답 매핑)
- auth-service `/api/external/users` 응답 본문 (요청 순서와 응답 순서 보장 여부)
- content-service 401/403 메트릭 재수집

---

### ① "로그인이 느리다"에 대한 별도 주석
조회창 안에 **로그인 엔드포인트 트레이스가 한 건도 없다**. 수집된 auth-service span은 전부 `/external/users`(서비스 간 호출)이며 20~119ms로 정상이다. 사용자가 체감한 "로그인 느림"이 로그인 API 자체인지, 로그인 직후 로딩되는 `/api/feeds/scroll`(최대 20272ms)인지 데이터로 구분할 수 없다. **후자일 가능성이 높다고 보지만 근거는 정황뿐이다.**

### ② "친구가 온라인인데 오프라인으로 보인다"
`websocket_active_users` = **전 구간 0** (chat-service pod 1개). chat-service가 Redis에 접속하지 못하는 상태와 정합적이나, 이 메트릭은 장애 이전 구간(01:35:00~01:43:44)에도 0이라 장애 전후를 구분하지 못한다. **확신도: 중간**, 프레즌스 저장소가 Redis인지는 주어진 데이터로 확인되지 않는다.

---

## 3. 권장 다음 조치

**즉시 (복구)**
1. `172.31.46.124`에서 Redis 프로세스 상태 확인 — `systemctl status redis` / `docker ps` / `redis-cli -h 172.31.46.124 ping`. 같은 호스트 27017(Mongo)은 정상이므로 호스트가 아니라 Redis 데몬만 죽었을 가능성이 높다. OOM kill 여부 확인 (`dmesg | grep -i oom`, Redis `maxmemory` 설정).
2. 재기동 후 `content-service`, `chat-service` 로그에서 `ConnectionWatchdog` 재연결 성공 및 `[HTTP-SLOW]` 소멸 확인.

**모니터링 공백 메우기**
3. `redis_up` / `redis_connected_clients` 메트릭 수집 추가. 현재 `up{job=redis}=1`은 exporter 생존만 나타내며 이번 장애를 전혀 잡아내지 못했다.
4. `battle-deadline-notification-scheduler.notify` 실패에 알림 연결 — 60초마다 실패 중인데 알림이 없었다.
5. content-service 401 메트릭이 왜 시리즈 자체가 없는지 확인.

**재발 방지 (코드)**
6. `UserCacheStore.getCachedUserInfos` — 유저별 개별 `GET`/`SET`을 `MGET`/파이프라인 배치로 전환. 현재 구조는 Redis 정상일 때도 N회 왕복이다.
7. Redis 호출에 서킷 브레이커 적용. 지금은 유저 1명당 2초씩 정직하게 대기해 20초 응답을 만든다. 장애 시 즉시 폴백해야 한다.
8. `FeedService.getFeedsWithCursor`(:86)의 트랜잭션 범위에서 Redis·HTTP 호출 분리. 현재 JDBC 커넥션을 20.3초 점유한다(span `connection` acquired→commit). 이번엔 트래픽이 낮아 `hikaricp_connections_pending`이 0이었지만, 부하가 있었으면 풀 고갈로 전면 장애가 됐다.
9. Lettuce 커맨드 타임아웃 2초가 유저별로 누적되는 구조 자체를 요청 단위 총 예산(deadline)으로 제한.

**미해결**
10. ③ 작성자 이름 문제는 위 "추가 수집 필요" 항목을 확보하기 전까지 원인 미상. Redis 복구 후에도 재현되는지 먼저 확인할 것 — 복구 후 사라지면 폴백 경로 버그, 남으면 별개 원인이다.

---

## 관측 증거 (Evidence)

> 이 조사가 실제로 본 값이다. 원본 응답 전체는 `reports/raw/scan-1785807300-*.json`에 있다.

### 호출 그래프 (트레이스에서 추출)

```
chat-service --db--> mongodb  5회  최대 95.2ms  [find, aggregate]
chat-service --jdbc--> mysql/content (HikariPool-1)  3회  최대 2863.1ms
    events: acquired, commit
content-service --jdbc--> mysql/content (HikariPool-1)  125회  최대 20269.6ms
    events: acquired, commit
content-service --service--> auth-service  14회  최대 119.3ms
```

### span (duration 상위 15 / 전체 214)

| ms | service | span | 시작 |
|---:|---|---|---|
| 20273.18 | content-service | `http get /feeds/scroll` | 2026-08-04T01:43:53.206252Z |
| 20271.16 | content-service | `secured request` | 2026-08-04T01:43:53.206712Z |
| 20269.55 | content-service | `connection` | 2026-08-04T01:43:53.208135Z |
| 20210.22 | content-service | `http get /feeds/scroll` | 2026-08-04T01:44:13.489895Z |
| 20206.61 | content-service | `secured request` | 2026-08-04T01:44:13.491742Z |
| 20181.24 | content-service | `connection` | 2026-08-04T01:44:13.516827Z |
| 12167.45 | content-service | `http get /feeds/scroll` | 2026-08-04T01:44:04.670366Z |
| 12166.21 | content-service | `secured request` | 2026-08-04T01:44:04.670805Z |
| 12159.42 | content-service | `connection` | 2026-08-04T01:44:04.677495Z |
| 12132.53 | content-service | `http get /feeds/scroll` | 2026-08-04T01:43:51.145657Z |
| 12131.33 | content-service | `secured request` | 2026-08-04T01:43:51.146027Z |
| 12130.21 | content-service | `connection` | 2026-08-04T01:43:51.147019Z |
| 8136.61 | content-service | `http get /battles` | 2026-08-04T01:44:01.127503Z |
| 8135.30 | content-service | `secured request` | 2026-08-04T01:44:01.128003Z |
| 8097.63 | content-service | `connection` | 2026-08-04T01:44:01.165582Z |

### 로그 원문 (60 / 전체 1,060줄)

전체가 상한을 넘어 ERROR/WARN 줄을 우선 발췌했다. 나머지는 원본 파일에 있다.

```
2026-08-04T01:43:44.215838156Z  [chat-service]  [2m2026-08-04T10:43:44.215+09:00[0;39m [33m WARN [traceId=NONE,spanId=NONE,userId=NONE][0;39m [35m7[0;39m [2m--- [chat-service] [llEventLoop-6-1] [                                                 ] [0;39m[36mi.l.core.protocol.ConnectionWatchdog    [0;39m [2m:[0;39m Cannot reconnect to [172.31.46.124/<unresolved>:6379]: finishConnect(..) failed: Connection refused: /172.31.46.124:6379
2026-08-04T01:43:44.216163667Z  [chat-service]  [2m2026-08-04T10:43:44.215+09:00[0;39m [33m WARN [traceId=NONE,spanId=NONE,userId=NONE][0;39m [35m7[0;39m [2m--- [chat-service] [llEventLoop-6-2] [                                                 ] [0;39m[36mi.l.core.protocol.ConnectionWatchdog    [0;39m [2m:[0;39m Cannot reconnect to [172.31.46.124/<unresolved>:6379]: finishConnect(..) failed: Connection refused: /172.31.46.124:6379
2026-08-04T01:43:49.403848450Z  [content-service]  org.springframework.dao.QueryTimeoutException: Redis command timed out
2026-08-04T01:43:49.403853794Z  [content-service]  at org.springframework.data.redis.connection.lettuce.LettuceExceptionConverter.convert(LettuceExceptionConverter.java:68)
2026-08-04T01:43:49.403857808Z  [content-service]  at org.springframework.data.redis.connection.lettuce.LettuceReactiveRedisConnection.lambda$translateException$0(LettuceReactiveRedisConnection.java:242)
2026-08-04T01:43:49.403914394Z  [content-service]  at io.lettuce.core.protocol.CommandWrapper.completeExceptionally(CommandWrapper.java:132)
2026-08-04T01:43:49.403949172Z  [content-service]  Caused by: io.lettuce.core.RedisCommandTimeoutException: Command timed out after 2 second(s)
2026-08-04T01:43:49.403951260Z  [content-service]  at io.lettuce.core.internal.ExceptionFactory.createTimeoutException(ExceptionFactory.java:63)
2026-08-04T01:43:50.315620200Z  [chat-service]  [2m2026-08-04T10:43:50.314+09:00[0;39m [33m WARN [traceId=NONE,spanId=NONE,userId=NONE][0;39m [35m7[0;39m [2m--- [chat-service] [xecutorLoop-3-1] [                                                 ] [0;39m[36mo.s.b.a.d.r.RedisReactiveHealthIndicator[0;39m [2m:[0;39m Redis health check failed
2026-08-04T01:43:50.315670380Z  [chat-service]  org.springframework.dao.QueryTimeoutException: Redis command timed out
2026-08-04T01:43:50.315673846Z  [chat-service]  at org.springframework.data.redis.connection.lettuce.LettuceExceptionConverter.convert(LettuceExceptionConverter.java:68) ~[spring-data-redis-3.5.1.jar!/:3.5.1]
2026-08-04T01:43:50.315678384Z  [chat-service]  at org.springframework.data.redis.connection.lettuce.LettuceReactiveRedisConnection.lambda$translateException$0(LettuceReactiveRedisConnection.java:242) ~[spring-data-redis-3.5.1.jar!/:3.5.1]
2026-08-04T01:43:50.315715607Z  [chat-service]  at io.lettuce.core.protocol.CommandWrapper.completeExceptionally(CommandWrapper.java:132) ~[lettuce-core-6.6.0.RELEASE.jar!/:6.6.0.RELEASE/643bd47]
2026-08-04T01:43:50.315755320Z  [chat-service]  Caused by: io.lettuce.core.RedisCommandTimeoutException: INFO. Command timed out after 2 second(s)
2026-08-04T01:43:50.315757091Z  [chat-service]  at io.lettuce.core.internal.ExceptionFactory.createTimeoutException(ExceptionFactory.java:75) ~[lettuce-core-6.6.0.RELEASE.jar!/:6.6.0.RELEASE/643bd47]
2026-08-04T01:43:53.163325524Z  [content-service]  2026-08-04 10:43:53.158 [http-nio-8082-exec-5] ERROR [traceId=6a7143d774c29419e907f79330eb97ab,spanId=3e8d497bf8dbbccc,userId=NONE] c.e.t.e.user.service.UserCacheStore - [user-cache] Redis 캐시 조회 실패: cacheKey=user:info:1
2026-08-04T01:43:53.163374964Z  [content-service]  org.springframework.dao.QueryTimeoutException: Redis command timed out
2026-08-04T01:43:53.163382549Z  [content-service]  at org.springframework.data.redis.connection.lettuce.LettuceExceptionConverter.convert(LettuceExceptionConverter.java:68)
2026-08-04T01:43:53.163386940Z  [content-service]  at org.springframework.data.redis.connection.lettuce.LettuceExceptionConverter.convert(LettuceExceptionConverter.java:41)
2026-08-04T01:43:53.163391293Z  [content-service]  at org.springframework.data.redis.PassThroughExceptionTranslationStrategy.translate(PassThroughExceptionTranslationStrategy.java:40)
2026-08-04T01:43:53.163395001Z  [content-service]  at org.springframework.data.redis.FallbackExceptionTranslationStrategy.translate(FallbackExceptionTranslationStrategy.java:38)
2026-08-04T01:43:53.163397374Z  [content-service]  at org.springframework.data.redis.connection.lettuce.LettuceConnection.convertLettuceAccessException(LettuceConnection.java:310)
2026-08-04T01:43:53.163722021Z  [content-service]  at org.springframework.security.web.access.ExceptionTranslationFilter.doFilter(ExceptionTranslationFilter.java:126)
2026-08-04T01:43:53.163724383Z  [content-service]  at org.springframework.security.web.access.ExceptionTranslationFilter.doFilter(ExceptionTranslationFilter.java:120)
2026-08-04T01:43:53.163966114Z  [content-service]  Caused by: io.lettuce.core.RedisCommandTimeoutException: Command timed out after 2 second(s)
2026-08-04T01:43:53.218334242Z  [chat-service]  [2m2026-08-04T10:43:53.218+09:00[0;39m [33m WARN [traceId=NONE,spanId=NONE,userId=NONE][0;39m [35m7[0;39m [2m--- [chat-service] [llEventLoop-6-2] [                                                 ] [0;39m[36mi.l.core.protocol.ConnectionWatchdog    [0;39m [2m:[0;39m Cannot reconnect to [172.31.46.124/<unresolved>:6379]: finishConnect(..) failed: Connection refused: /172.31.46.124:6379
2026-08-04T01:43:53.220019367Z  [chat-service]  [2m2026-08-04T10:43:53.219+09:00[0;39m [33m WARN [traceId=NONE,spanId=NONE,userId=NONE][0;39m [35m7[0;39m [2m--- [chat-service] [llEventLoop-6-1] [                                                 ] [0;39m[36mi.l.core.protocol.ConnectionWatchdog    [0;39m [2m:[0;39m Cannot reconnect to [172.31.46.124/<unresolved>:6379]: finishConnect(..) failed: Connection refused: /172.31.46.124:6379
2026-08-04T01:43:55.163248188Z  [content-service]  2026-08-04 10:43:55.161 [http-nio-8082-exec-5] ERROR [traceId=6a7143d774c29419e907f79330eb97ab,spanId=3e8d497bf8dbbccc,userId=NONE] c.e.t.e.user.service.UserCacheStore - [user-cache] Redis 캐시 조회 실패: cacheKey=user:info:3
2026-08-04T01:43:55.229820356Z  [content-service]  2026-08-04 10:43:55.224 [http-nio-8082-exec-3] ERROR [traceId=6a7143d985f93b95b082c5fb020b7958,spanId=cf1ed7150e5bee6f,userId=NONE] c.e.t.e.user.service.UserCacheStore - [user-cache] Redis 캐시 조회 실패: cacheKey=user:info:1
2026-08-04T01:43:55.229842591Z  [content-service]  org.springframework.dao.QueryTimeoutException: Redis command timed out
2026-08-04T01:43:55.229846490Z  [content-service]  at org.springframework.data.redis.connection.lettuce.LettuceExceptionConverter.convert(LettuceExceptionConverter.java:68)
2026-08-04T01:43:55.229849658Z  [content-service]  at org.springframework.data.redis.connection.lettuce.LettuceExceptionConverter.convert(LettuceExceptionConverter.java:41)
2026-08-04T01:43:55.229852900Z  [content-service]  at org.springframework.data.redis.PassThroughExceptionTranslationStrategy.translate(PassThroughExceptionTranslationStrategy.java:40)
2026-08-04T01:43:55.229864941Z  [content-service]  at org.springframework.data.redis.FallbackExceptionTranslationStrategy.translate(FallbackExceptionTranslationStrategy.java:38)
2026-08-04T01:43:55.229867956Z  [content-service]  at org.springframework.data.redis.connection.lettuce.LettuceConnection.convertLettuceAccessException(LettuceConnection.java:310)
2026-08-04T01:43:55.230071359Z  [content-service]  at org.springframework.security.web.access.ExceptionTranslationFilter.doFilter(ExceptionTranslationFilter.java:126)
2026-08-04T01:43:55.230073235Z  [content-service]  at org.springframework.security.web.access.ExceptionTranslationFilter.doFilter(ExceptionTranslationFilter.java:120)
2026-08-04T01:43:55.230336931Z  [content-service]  Caused by: io.lettuce.core.RedisCommandTimeoutException: Command timed out after 2 second(s)
2026-08-04T01:43:55.230338422Z  [content-service]  at io.lettuce.core.internal.ExceptionFactory.createTimeoutException(ExceptionFactory.java:63)
2026-08-04T01:43:57.166333051Z  [content-service]  2026-08-04 10:43:57.164 [http-nio-8082-exec-5] ERROR [traceId=6a7143d774c29419e907f79330eb97ab,spanId=3e8d497bf8dbbccc,userId=NONE] c.e.t.e.user.service.UserCacheStore - [user-cache] Redis 캐시 조회 실패: cacheKey=user:info:7
2026-08-04T01:43:57.234762765Z  [content-service]  2026-08-04 10:43:57.230 [http-nio-8082-exec-3] ERROR [traceId=6a7143d985f93b95b082c5fb020b7958,spanId=cf1ed7150e5bee6f,userId=NONE] c.e.t.e.user.service.UserCacheStore - [user-cache] Redis 캐시 조회 실패: cacheKey=user:info:3
2026-08-04T01:43:57.234784233Z  [content-service]  org.springframework.dao.QueryTimeoutException: Redis command timed out
2026-08-04T01:43:57.234788101Z  [content-service]  at org.springframework.data.redis.connection.lettuce.LettuceExceptionConverter.convert(LettuceExceptionConverter.java:68)
2026-08-04T01:43:57.234790597Z  [content-service]  at org.springframework.data.redis.connection.lettuce.LettuceExceptionConverter.convert(LettuceExceptionConverter.java:41)
2026-08-04T01:43:57.234804214Z  [content-service]  at org.springframework.data.redis.PassThroughExceptionTranslationStrategy.translate(PassThroughExceptionTranslationStrategy.java:40)
2026-08-04T01:43:57.234807025Z  [content-service]  at org.springframework.data.redis.FallbackExceptionTranslationStrategy.translate(FallbackExceptionTranslationStrategy.java:38)
2026-08-04T01:43:57.234809874Z  [content-service]  at org.springframework.data.redis.connection.lettuce.LettuceConnection.convertLettuceAccessException(LettuceConnection.java:310)
2026-08-04T01:43:57.235045634Z  [content-service]  at org.springframework.security.web.access.ExceptionTranslationFilter.doFilter(ExceptionTranslationFilter.java:126)
2026-08-04T01:43:57.235047434Z  [content-service]  at org.springframework.security.web.access.ExceptionTranslationFilter.doFilter(ExceptionTranslationFilter.java:120)
2026-08-04T01:43:57.235418426Z  [content-service]  Caused by: io.lettuce.core.RedisCommandTimeoutException: Command timed out after 2 second(s)
2026-08-04T01:43:57.235420149Z  [content-service]  at io.lettuce.core.internal.ExceptionFactory.createTimeoutException(ExceptionFactory.java:63)
2026-08-04T01:43:58.920079224Z  [chat-service]  [2m2026-08-04T10:43:58.919+09:00[0;39m [32mDEBUG [traceId=6a7143dd99a9504b914d9d2963f94b11,spanId=f037424afcf59b08,userId=1][0;39m [35m7[0;39m [2m--- [chat-service] [nio-8084-exec-1] [6a7143dd99a9504b914d9d2963f94b11-f037424afcf59b08] [0;39m[36m.s.d.m.o.MongoObservationCommandListener[0;39m [2m:[0;39m Found a observation in Mongo context [{name=jdbc.connection(connection), error=null, context=name='jdbc.connection', contextualName='connection', error='null', lowCardinalityKeyValues=[jdbc.datasource.name='content'], highCardinalityKeyValues=[], map=[class io.micrometer.core.instrument.LongTaskTimer$Sample='io.micrometer.core.instrument.noop.NoopLongTaskTimer$NoopSample@291bdc5b', class io.micrometer.core.instrument.Timer$Sample='io.micrometer.core.instrument.Timer$Sample@77572896', class io.micrometer.tracing.handler.TracingObservationHandler$TracingContext='TracingContext{span=6a7143dd99a9504b914d9d2963f94b11/f037424afcf59b08}'], parentObservation={name=spring.security.http.secured.requests(secured request), error=null, context=name='spring.security.http.secured.requests', contextualName='secured request', error='null', lowCardinalityKeyValues=[], highCardinalityKeyValues=[], map=[class io.micrometer.core.instrument.LongTaskTimer$Sample='SampleImpl{duration(seconds)=0.536839263, duration(nanos)=5.36839263E8, startTimeNanos=6719453845235566}', class io.micrometer.core.instrument.Timer$Sample='io.micrometer.core.instrument.Timer$Sample@7210a9bd', class io.micrometer.tracing.handler.TracingObservationHandler$TracingContext='TracingContext{span=6a7143dd99a9504b914d9d2963f94b11/5f018f530586928f}'], parentObservation={name=spring.security.filterchains(security filterchain before), error=null, context=name='spring.security.filterchains', contextualName='security filterchain before', error='null', lowCardinalityKeyValues=[spring.security.filterchain.position='12', spring.security.filterchain.size='12', spring.security.reached.filter.name='ExceptionTranslationFilter', spring.security.reached.filter.section='before'], highCardinalityKeyValues=[], map=[class io.micrometer.core.instrument.LongTaskTimer$Sample='SampleImpl{duration(seconds)=-1.0E-9, duration(nanos)=-1.0, startTimeNanos=6719453084236443}', class io.micrometer.core.instrument.Timer$Sample='io.micrometer.core.instrument.Timer$Sample@2566f4f2', class io.micrometer.tracing.handler.TracingObservationHandler$TracingContext='TracingContext{span=6a7143dd99a9504b914d9d2963f94b11/37f5f7923464b93d}'], parentObservation={name=http.server.requests(null), error=null, context=name='http.server.requests', contextualName='null', error='null', lowCardinalityKeyValues=[exception='none', method='GET', outcome='SUCCESS', status='200', uri='UNKNOWN'], highCardinalityKeyValues=[http.url='/api/notifications'], map=[class io.micrometer.core.instrument.LongTaskTimer$Sample='SampleImpl{duration(seconds)=1.302779582, duration(nanos)=1.302779582E9, startTimeNanos=6719453079443396}', class io.micrometer.core.instrument.Timer$Sample='io.micrometer.core.instrument.Timer$Sample@52681d01', class io.micrometer.tracing.handler.TracingObservationHandler$TracingContext='TracingContext{span=6a7143dd99a9504b914d9d2963f94b11/914d9d2963f94b11}'], parentObservation=null}}}}]
2026-08-04T01:43:58.921553354Z  [chat-service]  [2m2026-08-04T10:43:58.921+09:00[0;39m [32mDEBUG [traceId=6a7143dd99a9504b914d9d2963f94b11,spanId=f037424afcf59b08,userId=1][0;39m [35m7[0;39m [2m--- [chat-service] [nio-8084-exec-1] [6a7143dd99a9504b914d9d2963f94b11-f037424afcf59b08] [0;39m[36m.s.d.m.o.MongoObservationCommandListener[0;39m [2m:[0;39m Found the following observation passed from the mongo context [{name=jdbc.connection(connection), error=null, context=name='jdbc.connection', contextualName='connection', error='null', lowCardinalityKeyValues=[jdbc.datasource.name='content'], highCardinalityKeyValues=[], map=[class io.micrometer.core.instrument.LongTaskTimer$Sample='io.micrometer.core.instrument.noop.NoopLongTaskTimer$NoopSample@291bdc5b', class io.micrometer.core.instrument.Timer$Sample='io.micrometer.core.instrument.Timer$Sample@77572896', class io.micrometer.tracing.handler.TracingObservationHandler$TracingContext='TracingContext{span=6a7143dd99a9504b914d9d2963f94b11/f037424afcf59b08}'], parentObservation={name=spring.security.http.secured.requests(secured request), error=null, context=name='spring.security.http.secured.requests', contextualName='secured request', error='null', lowCardinalityKeyValues=[], highCardinalityKeyValues=[], map=[class io.micrometer.core.instrument.LongTaskTimer$Sample='SampleImpl{duration(seconds)=0.538412408, duration(nanos)=5.38412408E8, startTimeNanos=6719453845235566}', class io.micrometer.core.instrument.Timer$Sample='io.micrometer.core.instrument.Timer$Sample@7210a9bd', class io.micrometer.tracing.handler.TracingObservationHandler$TracingContext='TracingContext{span=6a7143dd99a9504b914d9d2963f94b11/5f018f530586928f}'], parentObservation={name=spring.security.filterchains(security filterchain before), error=null, context=name='spring.security.filterchains', contextualName='security filterchain before', error='null', lowCardinalityKeyValues=[spring.security.filterchain.position='12', spring.security.filterchain.size='12', spring.security.reached.filter.name='ExceptionTranslationFilter', spring.security.reached.filter.section='before'], highCardinalityKeyValues=[], map=[class io.micrometer.core.instrument.LongTaskTimer$Sample='SampleImpl{duration(seconds)=-1.0E-9, duration(nanos)=-1.0, startTimeNanos=6719453084236443}', class io.micrometer.core.instrument.Timer$Sample='io.micrometer.core.instrument.Timer$Sample@2566f4f2', class io.micrometer.tracing.handler.TracingObservationHandler$TracingContext='TracingContext{span=6a7143dd99a9504b914d9d2963f94b11/37f5f7923464b93d}'], parentObservation={name=http.server.requests(null), error=null, context=name='http.server.requests', contextualName='null', error='null', lowCardinalityKeyValues=[exception='none', method='GET', outcome='SUCCESS', status='200', uri='UNKNOWN'], highCardinalityKeyValues=[http.url='/api/notifications'], map=[class io.micrometer.core.instrument.LongTaskTimer$Sample='SampleImpl{duration(seconds)=1.304295002, duration(nanos)=1.304295002E9, startTimeNanos=6719453079443396}', class io.micrometer.core.instrument.Timer$Sample='io.micrometer.core.instrument.Timer$Sample@52681d01', class io.micrometer.tracing.handler.TracingObservationHandler$TracingContext='TracingContext{span=6a7143dd99a9504b914d9d2963f94b11/914d9d2963f94b11}'], parentObservation=null}}}}]
2026-08-04T01:43:58.924188708Z  [chat-service]  [2m2026-08-04T10:43:58.923+09:00[0;39m [32mDEBUG [traceId=6a7143dd99a9504b914d9d2963f94b11,spanId=f037424afcf59b08,userId=1][0;39m [35m7[0;39m [2m--- [chat-service] [nio-8084-exec-1] [6a7143dd99a9504b914d9d2963f94b11-f037424afcf59b08] [0;39m[36m.s.d.m.o.MongoObservationCommandListener[0;39m [2m:[0;39m Created a child observation  [{name=spring.data.mongodb.command(null), error=null, context=name='spring.data.mongodb.command', contextualName='null', error='null', lowCardinalityKeyValues=[db.mongodb.collection='user_notifications', db.name='toychat', db.operation='find', db.system='mongodb', net.peer.name='172.31.46.124', net.peer.port='27017', net.transport='IP.TCP', spring.data.mongodb.cluster_id='6a70a81aaa665b94bab207e6'], highCardinalityKeyValues=[], map=[class io.micrometer.core.instrument.LongTaskTimer$Sample='SampleImpl{duration(seconds)=3.8268E-5, duration(nanos)=38268.0, startTimeNanos=6719454386212469}', class io.micrometer.core.instrument.Timer$Sample='io.micrometer.core.instrument.Timer$Sample@1c359c42', class io.micrometer.tracing.handler.TracingObservationHandler$TracingContext='TracingContext{span=6a7143dd99a9504b914d9d2963f94b11/e94ae677f0abde1e}'], parentObservation={name=jdbc.connection(connection), error=null, context=name='jdbc.connection', contextualName='connection', error='null', lowCardinalityKeyValues=[jdbc.datasource.name='content'], highCardinalityKeyValues=[], map=[class io.micrometer.core.instrument.LongTaskTimer$Sample='io.micrometer.core.instrument.noop.NoopLongTaskTimer$NoopSample@291bdc5b', class io.micrometer.core.instrument.Timer$Sample='io.micrometer.core.instrument.Timer$Sample@77572896', class io.micrometer.tracing.handler.TracingObservationHandler$TracingContext='TracingContext{span=6a7143dd99a9504b914d9d2963f94b11/f037424afcf59b08}'], parentObservation={name=spring.security.http.secured.requests(secured request), error=null, context=name='spring.security.http.secured.requests', contextualName='secured request', error='null', lowCardinalityKeyValues=[], highCardinalityKeyValues=[], map=[class io.micrometer.core.instrument.LongTaskTimer$Sample='SampleImpl{duration(seconds)=0.541048226, duration(nanos)=5.41048226E8, startTimeNanos=6719453845235566}', class io.micrometer.core.instrument.Timer$Sample='io.micrometer.core.instrument.Timer$Sample@7210a9bd', class io.micrometer.tracing.handler.TracingObservationHandler$TracingContext='TracingContext{span=6a7143dd99a9504b914d9d2963f94b11/5f018f530586928f}'], parentObservation={name=spring.security.filterchains(security filterchain before), error=null, context=name='spring.security.filterchains', contextualName='security filterchain before', error='null', lowCardinalityKeyValues=[spring.security.filterchain.position='12', spring.security.filterchain.size='12', spring.security.reached.filter.name='ExceptionTranslationFilter', spring.security.reached.filter.section='before'], highCardinalityKeyValues=[], map=[class io.micrometer.core.instrument.LongTaskTimer$Sample='SampleImpl{duration(seconds)=-1.0E-9, duration(nanos)=-1.0, startTimeNanos=6719453084236443}', class io.micrometer.core.instrument.Timer$Sample='io.micrometer.core.instrument.Timer$Sample@2566f4f2', class io.micrometer.tracing.handler.TracingObservationHandler$TracingContext='TracingContext{span=6a7143dd99a9504b914d9d2963f94b11/37f5f7923464b93d}'], parentObservation={name=http.server.requests(null), error=null, context=name='http.server.requests', contextualName='null', error='null', lowCardinalityKeyValues=[exception='none', method='GET', outcome='SUCCESS', status='200', uri='UNKNOWN'], highCardinalityKeyValues=[http.url='/api/notifications'], map=[class io.micrometer.core.instrument.LongTaskTimer$Sample='SampleImpl{duration(seconds)=1.306883536, duration(nanos)=1.306883536E9, startTimeNanos=6719453079443396}', class io.micrometer.core.instrument.Timer$Sample='io.micrometer.core.instrument.Timer$Sample@52681d01', class io.micrometer.tracing.handler.TracingObservationHandler$TracingContext='TracingContext{span=6a7143dd99a9504b914d9d2963f94b11/914d9d2963f94b11}'], parentObservation=null}}}}}] for Mongo instrumentation and put it in Mongo context
2026-08-04T01:43:59.014237658Z  [chat-service]  [2m2026-08-04T10:43:59.013+09:00[0;39m [32mDEBUG [traceId=6a7143dd99a9504b914d9d2963f94b11,spanId=f037424afcf59b08,userId=1][0;39m [35m7[0;39m [2m--- [chat-service] [nio-8084-exec-1] [6a7143dd99a9504b914d9d2963f94b11-f037424afcf59b08] [0;39m[36m.s.d.m.o.MongoObservationCommandListener[0;39m [2m:[0;39m Command succeeded - will stop observation [{name=spring.data.mongodb.command(null), error=null, context=name='spring.data.mongodb.command', contextualName='null', error='null', lowCardinalityKeyValues=[db.mongodb.collection='user_notifications', db.name='toychat', db.operation='find', db.system='mongodb', net.peer.name='172.31.46.124', net.peer.port='27017', net.transport='IP.TCP', spring.data.mongodb.cluster_id='6a70a81aaa665b94bab207e6'], highCardinalityKeyValues=[], map=[class io.micrometer.core.instrument.LongTaskTimer$Sample='SampleImpl{duration(seconds)=0.089815928, duration(nanos)=8.9815928E7, startTimeNanos=6719454386212469}', class io.micrometer.core.instrument.Timer$Sample='io.micrometer.core.instrument.Timer$Sample@1c359c42', class io.micrometer.tracing.handler.TracingObservationHandler$TracingContext='TracingContext{span=6a7143dd99a9504b914d9d2963f94b11/e94ae677f0abde1e}'], parentObservation={name=jdbc.connection(connection), error=null, context=name='jdbc.connection', contextualName='connection', error='null', lowCardinalityKeyValues=[jdbc.datasource.name='content'], highCardinalityKeyValues=[], map=[class io.micrometer.core.instrument.LongTaskTimer$Sample='io.micrometer.core.instrument.noop.NoopLongTaskTimer$NoopSample@291bdc5b', class io.micrometer.core.instrument.Timer$Sample='io.micrometer.core.instrument.Timer$Sample@77572896', class io.micrometer.tracing.handler.TracingObservationHandler$TracingContext='TracingContext{span=6a7143dd99a9504b914d9d2963f94b11/f037424afcf59b08}'], parentObservation={name=spring.security.http.secured.requests(secured request), error=null, context=name='spring.security.http.secured.requests', contextualName='secured request', error='null', lowCardinalityKeyValues=[], highCardinalityKeyValues=[], map=[class io.micrometer.core.instrument.LongTaskTimer$Sample='SampleImpl{duration(seconds)=0.63086255, duration(nanos)=6.3086255E8, startTimeNanos=6719453845235566}', class io.micrometer.core.instrument.Timer$Sample='io.micrometer.core.instrument.Timer$Sample@7210a9bd', class io.micrometer.tracing.handler.TracingObservationHandler$TracingContext='TracingContext{span=6a7143dd99a9504b914d9d2963f94b11/5f018f530586928f}'], parentObservation={name=spring.security.filterchains(security filterchain before), error=null, context=name='spring.security.filterchains', contextualName='security filterchain before', error='null', lowCardinalityKeyValues=[spring.security.filterchain.position='12', spring.security.filterchain.size='12', spring.security.reached.filter.name='ExceptionTranslationFilter', spring.security.reached.filter.section='before'], highCardinalityKeyValues=[], map=[class io.micrometer.core.instrument.LongTaskTimer$Sample='SampleImpl{duration(seconds)=-1.0E-9, duration(nanos)=-1.0, startTimeNanos=6719453084236443}', class io.micrometer.core.instrument.Timer$Sample='io.micrometer.core.instrument.Timer$Sample@2566f4f2', class io.micrometer.tracing.handler.TracingObservationHandler$TracingContext='TracingContext{span=6a7143dd99a9504b914d9d2963f94b11/37f5f7923464b93d}'], parentObservation={name=http.server.requests(null), error=null, context=name='http.server.requests', contextualName='null', error='null', lowCardinalityKeyValues=[exception='none', method='GET', outcome='SUCCESS', status='200', uri='UNKNOWN'], highCardinalityKeyValues=[http.url='/api/notifications'], map=[class io.micrometer.core.instrument.LongTaskTimer$Sample='SampleImpl{duration(seconds)=1.396724709, duration(nanos)=1.396724709E9, startTimeNanos=6719453079443396}', class io.micrometer.core.instrument.Timer$Sample='io.micrometer.core.instrument.Timer$Sample@52681d01', class io.micrometer.tracing.handler.TracingObservationHandler$TracingContext='TracingContext{span=6a7143dd99a9504b914d9d2963f94b11/914d9d2963f94b11}'], parentObservation=null}}}}}]
2026-08-04T01:43:59.121047255Z  [chat-service]  [2m2026-08-04T10:43:59.120+09:00[0;39m [32mDEBUG [traceId=6a7143dd79de693c7fdfe44e82044ae0,spanId=5265b85c91d88f76,userId=1][0;39m [35m7[0;39m [2m--- [chat-service] [nio-8084-exec-3] [6a7143dd79de693c7fdfe44e82044ae0-5265b85c91d88f76] [0;39m[36m.s.d.m.o.MongoObservationCommandListener[0;39m [2m:[0;39m Found a observation in Mongo context [{name=jdbc.connection(connection), error=null, context=name='jdbc.connection', contextualName='connection', error='null', lowCardinalityKeyValues=[jdbc.datasource.name='content'], highCardinalityKeyValues=[], map=[class io.micrometer.core.instrument.LongTaskTimer$Sample='io.micrometer.core.instrument.noop.NoopLongTaskTimer$NoopSample@6f23f1f0', class io.micrometer.core.instrument.Timer$Sample='io.micrometer.core.instrument.Timer$Sample@5d16158b', class io.micrometer.tracing.handler.TracingObservationHandler$TracingContext='TracingContext{span=6a7143dd79de693c7fdfe44e82044ae0/5265b85c91d88f76}'], parentObservation={name=spring.security.http.secured.requests(secured request), error=null, context=name='spring.security.http.secured.requests', contextualName='secured request', error='null', lowCardinalityKeyValues=[], highCardinalityKeyValues=[], map=[class io.micrometer.core.instrument.LongTaskTimer$Sample='SampleImpl{duration(seconds)=0.735986629, duration(nanos)=7.35986629E8, startTimeNanos=6719453847112214}', class io.micrometer.core.instrument.Timer$Sample='io.micrometer.core.instrument.Timer$Sample@24f95d48', class io.micrometer.tracing.handler.TracingObservationHandler$TracingContext='TracingContext{span=6a7143dd79de693c7fdfe44e82044ae0/b9f92e6537fb3e4c}'], parentObservation={name=spring.security.filterchains(security filterchain before), error=null, context=name='spring.security.filterchains', contextualName='security filterchain before', error='null', lowCardinalityKeyValues=[spring.security.filterchain.position='12', spring.security.filterchain.size='12', spring.security.reached.filter.name='ExceptionTranslationFilter', spring.security.reached.filter.section='before'], highCardinalityKeyValues=[], map=[class io.micrometer.core.instrument.LongTaskTimer$Sample='SampleImpl{duration(seconds)=-1.0E-9, duration(nanos)=-1.0, startTimeNanos=6719453085569882}', class io.micrometer.core.instrument.Timer$Sample='io.micrometer.core.instrument.Timer$Sample@3639f327', class io.micrometer.tracing.handler.TracingObservationHandler$TracingContext='TracingContext{span=6a7143dd79de693c7fdfe44e82044ae0/d6561a3044c276b8}'], parentObservation={name=http.server.requests(null), error=null, context=name='http.server.requests', contextualName='null', error='null', lowCardinalityKeyValues=[exception='none', method='GET', outcome='SUCCESS', status='200', uri='UNKNOWN'], highCardinalityKeyValues=[http.url='/api/v1/chat/rooms'], map=[class io.micrometer.core.instrument.LongTaskTimer$Sample='SampleImpl{duration(seconds)=1.503705303, duration(nanos)=1.503705303E9, startTimeNanos=6719453079484478}', class io.micrometer.core.instrument.Timer$Sample='io.micrometer.core.instrument.Timer$Sample@46652861', class io.micrometer.tracing.handler.TracingObservationHandler$TracingContext='TracingContext{span=6a7143dd79de693c7fdfe44e82044ae0/7fdfe44e82044ae0}'], parentObservation=null}}}}]
2026-08-04T01:43:59.122926100Z  [chat-service]  [2m2026-08-04T10:43:59.122+09:00[0;39m [32mDEBUG [traceId=6a7143dd79de693c7fdfe44e82044ae0,spanId=5265b85c91d88f76,userId=1][0;39m [35m7[0;39m [2m--- [chat-service] [nio-8084-exec-3] [6a7143dd79de693c7fdfe44e82044ae0-5265b85c91d88f76] [0;39m[36m.s.d.m.o.MongoObservationCommandListener[0;39m [2m:[0;39m Found the following observation passed from the mongo context [{name=jdbc.connection(connection), error=null, context=name='jdbc.connection', contextualName='connection', error='null', lowCardinalityKeyValues=[jdbc.datasource.name='content'], highCardinalityKeyValues=[], map=[class io.micrometer.core.instrument.LongTaskTimer$Sample='io.micrometer.core.instrument.noop.NoopLongTaskTimer$NoopSample@6f23f1f0', class io.micrometer.core.instrument.Timer$Sample='io.micrometer.core.instrument.Timer$Sample@5d16158b', class io.micrometer.tracing.handler.TracingObservationHandler$TracingContext='TracingContext{span=6a7143dd79de693c7fdfe44e82044ae0/5265b85c91d88f76}'], parentObservation={name=spring.security.http.secured.requests(secured request), error=null, context=name='spring.security.http.secured.requests', contextualName='secured request', error='null', lowCardinalityKeyValues=[], highCardinalityKeyValues=[], map=[class io.micrometer.core.instrument.LongTaskTimer$Sample='SampleImpl{duration(seconds)=0.737941341, duration(nanos)=7.37941341E8, startTimeNanos=6719453847112214}', class io.micrometer.core.instrument.Timer$Sample='io.micrometer.core.instrument.Timer$Sample@24f95d48', class io.micrometer.tracing.handler.TracingObservationHandler$TracingContext='TracingContext{span=6a7143dd79de693c7fdfe44e82044ae0/b9f92e6537fb3e4c}'], parentObservation={name=spring.security.filterchains(security filterchain before), error=null, context=name='spring.security.filterchains', contextualName='security filterchain before', error='null', lowCardinalityKeyValues=[spring.security.filterchain.position='12', spring.security.filterchain.size='12', spring.security.reached.filter.name='ExceptionTranslationFilter', spring.security.reached.filter.section='before'], highCardinalityKeyValues=[], map=[class io.micrometer.core.instrument.LongTaskTimer$Sample='SampleImpl{duration(seconds)=-1.0E-9, duration(nanos)=-1.0, startTimeNanos=6719453085569882}', class io.micrometer.core.instrument.Timer$Sample='io.micrometer.core.instrument.Timer$Sample@3639f327', class io.micrometer.tracing.handler.TracingObservationHandler$TracingContext='TracingContext{span=6a7143dd79de693c7fdfe44e82044ae0/d6561a3044c276b8}'], parentObservation={name=http.server.requests(null), error=null, context=name='http.server.requests', contextualName='null', error='null', lowCardinalityKeyValues=[exception='none', method='GET', outcome='SUCCESS', status='200', uri='UNKNOWN'], highCardinalityKeyValues=[http.url='/api/v1/chat/rooms'], map=[class io.micrometer.core.instrument.LongTaskTimer$Sample='SampleImpl{duration(seconds)=1.50564186, duration(nanos)=1.50564186E9, startTimeNanos=6719453079484478}', class io.micrometer.core.instrument.Timer$Sample='io.micrometer.core.instrument.Timer$Sample@46652861', class io.micrometer.tracing.handler.TracingObservationHandler$TracingContext='TracingContext{span=6a7143dd79de693c7fdfe44e82044ae0/7fdfe44e82044ae0}'], parentObservation=null}}}}]
2026-08-04T01:43:59.123536514Z  [chat-service]  [2m2026-08-04T10:43:59.123+09:00[0;39m [32mDEBUG [traceId=6a7143dd79de693c7fdfe44e82044ae0,spanId=5265b85c91d88f76,userId=1][0;39m [35m7[0;39m [2m--- [chat-service] [nio-8084-exec-3] [6a7143dd79de693c7fdfe44e82044ae0-5265b85c91d88f76] [0;39m[36m.s.d.m.o.MongoObservationCommandListener[0;39m [2m:[0;39m Created a child observation  [{name=spring.data.mongodb.command(null), error=null, context=name='spring.data.mongodb.command', contextualName='null', error='null', lowCardinalityKeyValues=[db.mongodb.collection='chat_participants', db.name='toychat', db.operation='find', db.system='mongodb', net.peer.name='172.31.46.124', net.peer.port='27017', net.transport='IP.TCP', spring.data.mongodb.cluster_id='6a70a81aaa665b94bab207e6'], highCardinalityKeyValues=[], map=[class io.micrometer.core.instrument.LongTaskTimer$Sample='SampleImpl{duration(seconds)=4.689E-5, duration(nanos)=46890.0, startTimeNanos=6719454585640898}', class io.micrometer.core.instrument.Timer$Sample='io.micrometer.core.instrument.Timer$Sample@2b32c76b', class io.micrometer.tracing.handler.TracingObservationHandler$TracingContext='TracingContext{span=6a7143dd79de693c7fdfe44e82044ae0/b2f2ac0d17e3c818}'], parentObservation={name=jdbc.connection(connection), error=null, context=name='jdbc.connection', contextualName='connection', error='null', lowCardinalityKeyValues=[jdbc.datasource.name='content'], highCardinalityKeyValues=[], map=[class io.micrometer.core.instrument.LongTaskTimer$Sample='io.micrometer.core.instrument.noop.NoopLongTaskTimer$NoopSample@6f23f1f0', class io.micrometer.core.instrument.Timer$Sample='io.micrometer.core.instrument.Timer$Sample@5d16158b', class io.micrometer.tracing.handler.TracingObservationHandler$TracingContext='TracingContext{span=6a7143dd79de693c7fdfe44e82044ae0/5265b85c91d88f76}'], parentObservation={name=spring.security.http.secured.requests(secured request), error=null, context=name='spring.security.http.secured.requests', contextualName='secured request', error='null', lowCardinalityKeyValues=[], highCardinalityKeyValues=[], map=[class io.micrometer.core.instrument.LongTaskTimer$Sample='SampleImpl{duration(seconds)=0.738612954, duration(nanos)=7.38612954E8, startTimeNanos=6719453847112214}', class io.micrometer.core.instrument.Timer$Sample='io.micrometer.core.instrument.Timer$Sample@24f95d48', class io.micrometer.tracing.handler.TracingObservationHandler$TracingContext='TracingContext{span=6a7143dd79de693c7fdfe44e82044ae0/b9f92e6537fb3e4c}'], parentObservation={name=spring.security.filterchains(security filterchain before), error=null, context=name='spring.security.filterchains', contextualName='security filterchain before', error='null', lowCardinalityKeyValues=[spring.security.filterchain.position='12', spring.security.filterchain.size='12', spring.security.reached.filter.name='ExceptionTranslationFilter', spring.security.reached.filter.section='before'], highCardinalityKeyValues=[], map=[class io.micrometer.core.instrument.LongTaskTimer$Sample='SampleImpl{duration(seconds)=-1.0E-9, duration(nanos)=-1.0, startTimeNanos=6719453085569882}', class io.micrometer.core.instrument.Timer$Sample='io.micrometer.core.instrument.Timer$Sample@3639f327', class io.micrometer.tracing.handler.TracingObservationHandler$TracingContext='TracingContext{span=6a7143dd79de693c7fdfe44e82044ae0/d6561a3044c276b8}'], parentObservation={name=http.server.requests(null), error=null, context=name='http.server.requests', contextualName='null', error='null', lowCardinalityKeyValues=[exception='none', method='GET', outcome='SUCCESS', status='200', uri='UNKNOWN'], highCardinalityKeyValues=[http.url='/api/v1/chat/rooms'], map=[class io.micrometer.core.instrument.LongTaskTimer$Sample='SampleImpl{duration(seconds)=1.50627112, duration(nanos)=1.50627112E9, startTimeNanos=6719453079484478}', class io.micrometer.core.instrument.Timer$Sample='io.micrometer.core.instrument.Timer$Sample@46652861', class io.micrometer.tracing.handler.TracingObservationHandler$TracingContext='TracingContext{span=6a7143dd79de693c7fdfe44e82044ae0/7fdfe44e82044ae0}'], parentObservation=null}}}}}] for Mongo instrumentation and put it in Mongo context
2026-08-04T01:43:59.214255312Z  [chat-service]  [2m2026-08-04T10:43:59.212+09:00[0;39m [32mDEBUG [traceId=6a7143dd79de693c7fdfe44e82044ae0,spanId=5265b85c91d88f76,userId=1][0;39m [35m7[0;39m [2m--- [chat-service] [nio-8084-exec-3] [6a7143dd79de693c7fdfe44e82044ae0-5265b85c91d88f76] [0;39m[36m.s.d.m.o.MongoObservationCommandListener[0;39m [2m:[0;39m Command succeeded - will stop observation [{name=spring.data.mongodb.command(null), error=null, context=name='spring.data.mongodb.command', contextualName='null', error='null', lowCardinalityKeyValues=[db.mongodb.collection='chat_participants', db.name='toychat', db.operation='find', db.system='mongodb', net.peer.name='172.31.46.124', net.peer.port='27017', net.transport='IP.TCP', spring.data.mongodb.cluster_id='6a70a81aaa665b94bab207e6'], highCardinalityKeyValues=[], map=[class io.micrometer.core.instrument.LongTaskTimer$Sample='SampleImpl{duration(seconds)=0.089331184, duration(nanos)=8.9331184E7, startTimeNanos=6719454585640898}', class io.micrometer.core.instrument.Timer$Sample='io.micrometer.core.instrument.Timer$Sample@2b32c76b', class io.micrometer.tracing.handler.TracingObservationHandler$TracingContext='TracingContext{span=6a7143dd79de693c7fdfe44e82044ae0/b2f2ac0d17e3c818}'], parentObservation={name=jdbc.connection(connection), error=null, context=name='jdbc.connection', contextualName='connection', error='null', lowCardinalityKeyValues=[jdbc.datasource.name='content'], highCardinalityKeyValues=[], map=[class io.micrometer.core.instrument.LongTaskTimer$Sample='io.micrometer.core.instrument.noop.NoopLongTaskTimer$NoopSample@6f23f1f0', class io.micrometer.core.instrument.Timer$Sample='io.micrometer.core.instrument.Timer$Sample@5d16158b', class io.micrometer.tracing.handler.TracingObservationHandler$TracingContext='TracingContext{span=6a7143dd79de693c7fdfe44e82044ae0/5265b85c91d88f76}'], parentObservation={name=spring.security.http.secured.requests(secured request), error=null, context=name='spring.security.http.secured.requests', contextualName='secured request', error='null', lowCardinalityKeyValues=[], highCardinalityKeyValues=[], map=[class io.micrometer.core.instrument.LongTaskTimer$Sample='SampleImpl{duration(seconds)=0.827981708, duration(nanos)=8.27981708E8, startTimeNanos=6719453847112214}', class io.micrometer.core.instrument.Timer$Sample='io.micrometer.core.instrument.Timer$Sample@24f95d48', class io.micrometer.tracing.handler.TracingObservationHandler$TracingContext='TracingContext{span=6a7143dd79de693c7fdfe44e82044ae0/b9f92e6537fb3e4c}'], parentObservation={name=spring.security.filterchains(security filterchain before), error=null, context=name='spring.security.filterchains', contextualName='security filterchain before', error='null', lowCardinalityKeyValues=[spring.security.filterchain.position='12', spring.security.filterchain.size='12', spring.security.reached.filter.name='ExceptionTranslationFilter', spring.security.reached.filter.section='before'], highCardinalityKeyValues=[], map=[class io.micrometer.core.instrument.LongTaskTimer$Sample='SampleImpl{duration(seconds)=-1.0E-9, duration(nanos)=-1.0, startTimeNanos=6719453085569882}', class io.micrometer.core.instrument.Timer$Sample='io.micrometer.core.instrument.Timer$Sample@3639f327', class io.micrometer.tracing.handler.TracingObservationHandler$TracingContext='TracingContext{span=6a7143dd79de693c7fdfe44e82044ae0/d6561a3044c276b8}'], parentObservation={name=http.server.requests(null), error=null, context=name='http.server.requests', contextualName='null', error='null', lowCardinalityKeyValues=[exception='none', method='GET', outcome='SUCCESS', status='200', uri='UNKNOWN'], highCardinalityKeyValues=[http.url='/api/v1/chat/rooms'], map=[class io.micrometer.core.instrument.LongTaskTimer$Sample='SampleImpl{duration(seconds)=1.59566524, duration(nanos)=1.59566524E9, startTimeNanos=6719453079484478}', class io.micrometer.core.instrument.Timer$Sample='io.micrometer.core.instrument.Timer$Sample@46652861', class io.micrometer.tracing.handler.TracingObservationHandler$TracingContext='TracingContext{span=6a7143dd79de693c7fdfe44e82044ae0/7fdfe44e82044ae0}'], parentObservation=null}}}}}]
2026-08-04T01:43:59.215330174Z  [content-service]  2026-08-04 10:43:59.212 [http-nio-8082-exec-5] ERROR [traceId=6a7143d774c29419e907f79330eb97ab,spanId=3e8d497bf8dbbccc,userId=NONE] c.e.t.e.user.service.UserCacheStore - [user-cache] Redis 캐시 저장 실패: userId=1
```

### 메트릭 시계열

| 쿼리 | series | 점 | min | max | last | 값이 0이던 구간 |
|---|---|---:|---:|---:|---:|---|
| `hikaricp_connections_active` | `{__name__=hikaricp_connections_active, application=auth-service, cluster=yogurtte-k3s-prod, container=auth-service, instance=10.42.1.46:8090, job=auth-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=auth-service-5999bb9f5c-hmgp9, pool=HikariPool-1, service=auth-service}` | 69 | 0 | 0 | 0 | **2026-08-04T01:35:00Z ~ 2026-08-04T01:52:00Z** |
| `hikaricp_connections_active` | `{__name__=hikaricp_connections_active, application=chat-service, cluster=yogurtte-k3s-prod, container=chat-service, instance=10.42.1.47:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-fdcc7c776-xf4sv, pool=HikariPool-1}` | 61 | 0 | 0 | 0 | **2026-08-04T01:35:00Z ~ 2026-08-04T01:52:00Z** |
| `hikaricp_connections_active` | `{__name__=hikaricp_connections_active, application=content-service, cluster=yogurtte-k3s-prod, container=content-service, instance=10.42.1.43:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-6995bb7d94-h2f6n, pool=HikariPool-1}` | 61 | 0 | 1 | 0 | **2026-08-04T01:35:00Z ~ 2026-08-04T01:43:45Z, 2026-08-04T01:47:00Z ~ 2026-08-04T01:52:00Z** |
| `hikaricp_connections_active` | `{__name__=hikaricp_connections_active, application=content-service, cluster=yogurtte-k3s-prod, container=content-service, instance=10.42.3.42:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-6995bb7d94-nq9l2, pool=HikariPool-1}` | 57 | 0 | 0 | 0 | **2026-08-04T01:35:00Z ~ 2026-08-04T01:52:00Z** |
| `hikaricp_connections_pending` | `{__name__=hikaricp_connections_pending, application=auth-service, cluster=yogurtte-k3s-prod, container=auth-service, instance=10.42.1.46:8090, job=auth-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=auth-service-5999bb9f5c-hmgp9, pool=HikariPool-1, service=auth-service}` | 69 | 0 | 0 | 0 | **2026-08-04T01:35:00Z ~ 2026-08-04T01:52:00Z** |
| `hikaricp_connections_pending` | `{__name__=hikaricp_connections_pending, application=chat-service, cluster=yogurtte-k3s-prod, container=chat-service, instance=10.42.1.47:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-fdcc7c776-xf4sv, pool=HikariPool-1}` | 61 | 0 | 0 | 0 | **2026-08-04T01:35:00Z ~ 2026-08-04T01:52:00Z** |
| `hikaricp_connections_pending` | `{__name__=hikaricp_connections_pending, application=content-service, cluster=yogurtte-k3s-prod, container=content-service, instance=10.42.1.43:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-6995bb7d94-h2f6n, pool=HikariPool-1}` | 61 | 0 | 0 | 0 | **2026-08-04T01:35:00Z ~ 2026-08-04T01:52:00Z** |
| `hikaricp_connections_pending` | `{__name__=hikaricp_connections_pending, application=content-service, cluster=yogurtte-k3s-prod, container=content-service, instance=10.42.3.42:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-6995bb7d94-nq9l2, pool=HikariPool-1}` | 57 | 0 | 0 | 0 | **2026-08-04T01:35:00Z ~ 2026-08-04T01:52:00Z** |
| `rate(jvm_gc_pause_seconds_sum[5m])` | `{action=end of major GC, application=chat-service, cause=Allocation Failure, cluster=yogurtte-k3s-prod, container=chat-service, gc=MarkSweepCompact, instance=10.42.1.47:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-fdcc7c776-xf4sv}` | 69 | 0 | 0 | 0 | **2026-08-04T01:35:00Z ~ 2026-08-04T01:52:00Z** |
| `rate(jvm_gc_pause_seconds_sum[5m])` | `{action=end of minor GC, application=auth-service, cause=G1 Evacuation Pause, cluster=yogurtte-k3s-prod, container=auth-service, gc=G1 Young Generation, instance=10.42.1.46:8090, job=auth-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=auth-service-5999bb9f5c-hmgp9, service=auth-service}` | 69 | 0 | 0.000 | 0.000 | **2026-08-04T01:35:00Z ~ 2026-08-04T01:49:30Z** |
| `rate(jvm_gc_pause_seconds_sum[5m])` | `{action=end of minor GC, application=chat-service, cause=Allocation Failure, cluster=yogurtte-k3s-prod, container=chat-service, gc=Copy, instance=10.42.1.47:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-fdcc7c776-xf4sv}` | 69 | 0.000 | 0.001 | 0.000 | — |
| `rate(jvm_gc_pause_seconds_sum[5m])` | `{action=end of minor GC, application=content-service, cause=Allocation Failure, cluster=yogurtte-k3s-prod, container=content-service, gc=Copy, instance=10.42.1.43:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-6995bb7d94-h2f6n}` | 69 | 0 | 0.000 | 0.000 | **2026-08-04T01:35:00Z ~ 2026-08-04T01:41:45Z, 2026-08-04T01:46:00Z ~ 2026-08-04T01:50:45Z** |
| `rate(jvm_gc_pause_seconds_sum[5m])` | `{action=end of minor GC, application=content-service, cause=Allocation Failure, cluster=yogurtte-k3s-prod, container=content-service, gc=Copy, instance=10.42.3.42:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-6995bb7d94-nq9l2}` | 69 | 0 | 0.000 | 0 | **2026-08-04T01:37:45Z ~ 2026-08-04T01:47:30Z, 2026-08-04T01:48:45Z ~ 2026-08-04T01:52:00Z** |
| `up` | `{__name__=up, app=node-exporter, cluster=yogurtte-k3s-prod, component=metrics, container=node-exporter, instance=ip-172-31-40-241, job=integrations/node_exporter, k8s_cluster_name=yogurtte-k3s-prod, namespace=monitoring, pod=grafana-k8s-monitoring-node-exporter-cph5l, source=kubernetes, workload=DaemonSet/grafana-k8s-monitoring-node-exporter}` | 69 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, app=node-exporter, cluster=yogurtte-k3s-prod, component=metrics, container=node-exporter, instance=ip-172-31-45-39, job=integrations/node_exporter, k8s_cluster_name=yogurtte-k3s-prod, namespace=monitoring, pod=grafana-k8s-monitoring-node-exporter-9kvqt, source=kubernetes, workload=DaemonSet/grafana-k8s-monitoring-node-exporter}` | 69 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, cluster=yogurtte-k3s-prod, container=auth-service, instance=10.42.1.46:8090, job=auth-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=auth-service-5999bb9f5c-hmgp9}` | 69 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, cluster=yogurtte-k3s-prod, container=chat-service, instance=10.42.1.47:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-fdcc7c776-xf4sv}` | 61 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, cluster=yogurtte-k3s-prod, container=content-service, instance=10.42.1.43:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-6995bb7d94-h2f6n}` | 61 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, cluster=yogurtte-k3s-prod, container=content-service, instance=10.42.3.42:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-6995bb7d94-nq9l2}` | 57 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, cluster=yogurtte-k3s-prod, instance=10.42.1.248:8080, job=integrations/kubernetes/kube-state-metrics, k8s_cluster_name=yogurtte-k3s-prod, source=kubernetes}` | 69 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, cluster=yogurtte-k3s-prod, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod}` | 69 | 1 | 1 | 1 | — |
| `mongodb_up` | `{__name__=mongodb_up, cluster=yogurtte-k3s-prod, instance=infra-server, job=mongodb, k8s_cluster_name=yogurtte-k3s-prod}` | 69 | 1 | 1 | 1 | — |
| `kafka_brokers` | `{__name__=kafka_brokers, cluster=yogurtte-k3s-prod, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod}` | 69 | 1 | 1 | 1 | — |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=chat-service-fcm-tokens, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=0, topic=user.fcm-tokens}` | 69 | 0 | 0 | 0 | **2026-08-04T01:35:00Z ~ 2026-08-04T01:52:00Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=chat-service-fcm-tokens, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=1, topic=user.fcm-tokens}` | 69 | 0 | 0 | 0 | **2026-08-04T01:35:00Z ~ 2026-08-04T01:52:00Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=chat-service-fcm-tokens, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=2, topic=user.fcm-tokens}` | 69 | 0 | 0 | 0 | **2026-08-04T01:35:00Z ~ 2026-08-04T01:52:00Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=chat-service-notification-settings, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=0, topic=user.notification-settings}` | 69 | 0 | 0 | 0 | **2026-08-04T01:35:00Z ~ 2026-08-04T01:52:00Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=chat-service-notification-settings, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=1, topic=user.notification-settings}` | 69 | 0 | 0 | 0 | **2026-08-04T01:35:00Z ~ 2026-08-04T01:52:00Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=chat-service-notification-settings, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=2, topic=user.notification-settings}` | 69 | 0 | 0 | 0 | **2026-08-04T01:35:00Z ~ 2026-08-04T01:52:00Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=db-writer, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=0, topic=chat.messages}` | 69 | 0 | 0 | 0 | **2026-08-04T01:35:00Z ~ 2026-08-04T01:52:00Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=db-writer, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=1, topic=chat.messages}` | 69 | 0 | 0 | 0 | **2026-08-04T01:35:00Z ~ 2026-08-04T01:52:00Z** |
| `websocket_active_users` | `{__name__=websocket_active_users, application=chat-service, cluster=yogurtte-k3s-prod, container=chat-service, instance=10.42.1.47:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-fdcc7c776-xf4sv}` | 61 | 0 | 0 | 0 | **2026-08-04T01:35:00Z ~ 2026-08-04T01:52:00Z** |

값이 0이던 구간은 굵게 표시했다 — 프로세스가 사라져 시계열이 꺾인 것이 유일한 신호인 장애가 있다.

