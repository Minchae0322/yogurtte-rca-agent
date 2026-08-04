# RCA Report — `scan-1785766500`

| 항목 | 값 |
|---|---|
| 모드 | rca |
| 질문 | 최근 1시간 안에 채팅 알림이 안 온다는 문의가 여러 건 들어왔다. 원인을 조사해줘 |
| 시각 | 2026-08-03T15:00:04.708766Z |
| provider | claude-cli |
| model | `claude-opus-5` · turns 1 |
| prompt | `./prompts/system-prompt.md` |
| tokens | in 230217 (cacheRead 18,133 · cacheCreate 212,082) / out 9261 · cost $2.5190 |
| elapsed | total 147925ms (tempo 7985 · loki 489 · mimir 605 · assemble 14 · llm 136802) |

## 탐색 (Triage)

| 항목 | 값 |
|---|---|
| 시간창 해석 | 상대 표현 '최근 1시간' |
| 시간창 확신도 | EXACT |
| 스윕 창 | 2026-08-03T13:59:11.381016Z ~ 2026-08-03T14:59:11.381016Z |
| 좁힌 창 | 2026-08-03T14:15:00Z ~ 2026-08-03T14:54:11Z |
| 대상 | chat-service, content-service |
| traceId | 6a70a49244908ca8f15be0b4d7a168b5 |
| 트레이스 후보 | 21건 |
| 장애 후보 | 10건 · 선택 INC-4, INC-5, INC-6, INC-7, INC-8, INC-9, INC-10 |
| 계획 파싱 | 성공 |
| 스윕 컨텍스트 | 후보 + 원본 (A) |
| prompt | `./prompts/triage-prompt.md` |
| tokens | in 53190 / out 3253 · cost $0.4661 |
| chars | 컨텍스트 57,581 + 프롬프트 1,399 = **58,980** |
| elapsed | survey 878ms · llm 52429ms |

**선정 이유**: 14:24~14:35 chat-service가 30초 타임아웃으로 전면 정지하고 mongodb_up=0과 user.notifications 컨슈머 랙 누적·DLQ 유입이 동시에 관측되어, '알림 미도달' 증상의 시각·경로와 정확히 일치하는 단일 사건의 여러 지문이기 때문(INC-10은 그 회복 구간의 잔여 로그로 함께 봄).

**근거**

- chat-service ERROR/WARN 14:20~14:25 4건 → 14:25~14:30 68건으로 17배 급증 (Loki, INC-4)
- chat-service 'security filterchain before' 16건이 모두 30,006~30,088ms — 30초 타임아웃 상한에 붙은 균일 분포, 에러가 아닌 '지연' 지문 (Tempo slow, 14:24:18~14:27:49, INC-7)
- <root span not yet received> 3건 × 30,007~30,016ms, chat-service spanCount 1 — 요청이 chat-service 진입 단계에서 끊겨 트레이스가 완성되지 못함 (INC-8)
- mongodb_up 1→0→1, 14:29:11 시점 min_over_time(mongodb_up[5m])=0 (Mimir, INC-5)
- kafka_consumergroup_lag{consumergroup=notification-processors, topic=user.notifications, partition=3} 0→1→11→25→26→0, 14:29~14:44 누적 후 해소 — 알림 소비 정체의 직접 증거 (INC-6)
- kafka_consumergroup_lag{consumergroup=notification-recovery, topic=user.notifications.dlq, partition=0} 0→1→0 — 알림 일부가 DLQ로 유입 (INC-6)
- traceId 6a70a4cbf41848fcfa14ba00fe4a02f8: content-service 루트 30,090ms, serviceStats에서 chat-service spanCount 20 / errorCount 10 — 하류 chat-service가 실패 지점 (INC-9)
- 결손 신호: chat-service 파드 qrbc2의 up 시계열이 14:29 결측 후 14:34에 종료, 새 파드 xf4sv가 14:44부터 등장 — 파드 교체/재시작 정황
- 결손 신호: websocket_active_users가 같은 구간에 결측되고 신규 파드에서는 0으로만 관측 — 웹소켓 세션 미복구 의심
- kafka_brokers, up(전체) 이상 신호 0건 — 브로커·노드·다른 서비스는 이 창에서 정상, 장애가 chat-service와 mongodb에 국한

**스윕이 찾은 트레이스** (고른 것은 6a70a49244908ca8f15be0b4d7a168b5)

| traceId | 채널 | root service | root span | ms |
|---|---|---|---|---:|
| `6a70a4cbf41848fcfa14ba00fe4a02f8` | error | content-service | http post /battles/{battleId}/items/{itemId}/comments | 30090 |
| `6a70a115f09975daa14ec1a090053942` | error | content-service | http get /feeds/scroll | 71 |
| `6a70a5474af907b3397e981d6c8f020e` | slow | chat-service | security filterchain before | 30084 |
| `6a70a53dd534045afcc703eddaf68e88` | slow | chat-service | security filterchain before | 30008 |
| `6a70a533981876b400fe1f1f63b23495` | slow | chat-service | security filterchain before | 30007 |
| `6a70a529d19d5bd161816e0bd391b391` | slow | chat-service | security filterchain before | 30031 |
| `6a70a51f4228124d58fda0f293b5718d` | slow | chat-service | security filterchain before | 30008 |
| `6a70a5159cd2ffa748a878c59a8d63fd` | slow | chat-service | security filterchain before | 30008 |
| `6a70a50be94507293e827c46c93bdb5b` | slow | chat-service | security filterchain before | 30007 |
| `6a70a501290c9d041c0620935eaa61db` | slow | chat-service | security filterchain before | 30088 |
| `6a70a4f70407c7e4c4cd7fd17a8ddd02` | slow | chat-service | security filterchain before | 30007 |
| `6a70a4ed193a2b5a1f1bed00113d8b29` | slow | chat-service | security filterchain before | 30009 |
| `6a70a4e31505a83978ab808d971228ea` | slow | chat-service | security filterchain before | 30008 |
| `6a70a4d900ac17f8b3eed1dff5a1f7cd` | slow | chat-service | security filterchain before | 30006 |
| `6a70a4cf6953872624c277253c4aae4b` | slow | chat-service | security filterchain before | 30008 |
| `6a70a4c409d0baac34a37e5a651c761d` | slow | chat-service | security filterchain before | 30008 |
| `6a70a4bac0013ba673497f1f78b893f8` | slow | chat-service | security filterchain before | 30007 |
| `6a70a4b01759197099d4eaaad1247c81` | slow | <root span not yet received> | (없음) | 30007 |
| `6a70a4a6b2c7c98a2f264630917bf154` | slow | <root span not yet received> | (없음) | 30016 |
| `6a70a49ca06ccf017d0f0d4e3795675c` | slow | <root span not yet received> | (없음) | 30008 |
| `6a70a49244908ca8f15be0b4d7a168b5` ←선택 | slow | chat-service | security filterchain before | 30013 |

**장애 후보** (코드가 신호를 묶은 것 · 창은 여기서 계산됨)

## INC-1  content-service  |  ERROR/WARN
- 구간: 2026-08-03T14:05:00Z ~ 2026-08-03T14:10:00Z  (LOKI · 집계 해상도만큼 흐림)
- ERROR/WARN 1건 (2026-08-03T14:05:00Z ~ 2026-08-03T14:10:00Z)
- 같은 시각의 다른 후보: INC-2, INC-3  (인과 여부는 판단하지 않았다)

## INC-2  content-service  |  http get /feeds/scroll
- 구간: 2026-08-03T14:09:25.771400Z ~ 2026-08-03T14:09:25.842400Z  (TEMPO · 시각 정확)
- content-service http get /feeds/scroll 71ms (error 채널)
- traceId: 6a70a115f09975daa14ec1a090053942
- 같은 시각의 다른 후보: INC-1  (인과 여부는 판단하지 않았다)

## INC-3  auth-service  |  ERROR/WARN
- 구간: 2026-08-03T14:10:00Z ~ 2026-08-03T14:15:00Z  (LOKI · 집계 해상도만큼 흐림)
- ERROR/WARN 4건 (2026-08-03T14:10:00Z ~ 2026-08-03T14:15:00Z)
- 같은 시각의 다른 후보: INC-1  (인과 여부는 판단하지 않았다)

## INC-4  chat-service  |  ERROR/WARN
- 구간: 2026-08-03T14:20:00Z ~ 2026-08-03T14:30:00Z  (LOKI · 집계 해상도만큼 흐림)
- ERROR/WARN 4건 (2026-08-03T14:20:00Z ~ 2026-08-03T14:25:00Z)
- ERROR/WARN 68건 (2026-08-03T14:25:00Z ~ 2026-08-03T14:30:00Z)
- 같은 시각의 다른 후보: INC-5, INC-6, INC-7, INC-8, INC-9  (인과 여부는 판단하지 않았다)

## INC-5  mongodb  |  mongodb_up
- 구간: 2026-08-03T14:24:11Z ~ 2026-08-03T14:34:11Z  (MIMIR · 집계 해상도만큼 흐림)
- mongodb_up 1 → 0
- mongodb_up 가 0이었다 (2026-08-03T14:29:11Z ~ 2026-08-03T14:29:11Z)
- mongodb_up 0 → 1
- 같은 시각의 다른 후보: INC-4, INC-6, INC-7, INC-8, INC-9  (인과 여부는 판단하지 않았다)

## INC-6  kafka  |  kafka_consumergroup_lag
- 구간: 2026-08-03T14:24:11Z ~ 2026-08-03T14:49:11Z  (MIMIR · 집계 해상도만큼 흐림)
- kafka_consumergroup_lag{consumergroup=notification-processors, partition=3, topic=user.notifications} 0 → 1
- kafka_consumergroup_lag{consumergroup=notification-recovery, partition=0, topic=user.notifications.dlq} 0 → 1
- kafka_consumergroup_lag{consumergroup=notification-processors, partition=3, topic=user.notifications} 1 → 11
- kafka_consumergroup_lag{consumergroup=notification-processors, partition=3, topic=user.notifications} 11 → 25
- kafka_consumergroup_lag{consumergroup=notification-recovery, partition=0, topic=user.notifications.dlq} 1 → 0
- kafka_consumergroup_lag{consumergroup=notification-processors, partition=3, topic=user.notifications} 25 → 26
- kafka_consumergroup_lag{consumergroup=notification-processors, partition=3, topic=user.notifications} 26 → 0
- 같은 시각의 다른 후보: INC-4, INC-5, INC-7, INC-8, INC-9, INC-10  (인과 여부는 판단하지 않았다)

## INC-7  chat-service  |  security filterchain before
- 구간: 2026-08-03T14:24:18.746715Z ~ 2026-08-03T14:27:49.614132Z  (TEMPO · 시각 정확)
- chat-service security filterchain before 30,013ms (slow 채널)
- chat-service security filterchain before 30,007ms (slow 채널)
- chat-service security filterchain before 30,008ms (slow 채널)
- chat-service security filterchain before 30,008ms (slow 채널)
- chat-service security filterchain before 30,006ms (slow 채널)
- chat-service security filterchain before 30,008ms (slow 채널)
- chat-service security filterchain before 30,009ms (slow 채널)
- chat-service security filterchain before 30,007ms (slow 채널)
- chat-service security filterchain before 30,088ms (slow 채널)
- chat-service security filterchain before 30,007ms (slow 채널)
- chat-service security filterchain before 30,008ms (slow 채널)
- chat-service security filterchain before 30,008ms (slow 채널)
- chat-service security filterchain before 30,031ms (slow 채널)
- chat-service security filterchain before 30,007ms (slow 채널)
- chat-service security filterchain before 30,008ms (slow 채널)
- chat-service security filterchain before 30,084ms (slow 채널)
- traceId: 6a70a49244908ca8f15be0b4d7a168b5, 6a70a4bac0013ba673497f1f78b893f8, 6a70a4c409d0baac34a37e5a651c761d, 6a70a4cf6953872624c277253c4aae4b, 6a70a4d900ac17f8b3eed1dff5a1f7cd, 6a70a4e31505a83978ab808d971228ea, 6a70a4ed193a2b5a1f1bed00113d8b29, 6a70a4f70407c7e4c4cd7fd17a8ddd02, 6a70a501290c9d041c0620935eaa61db, 6a70a50be94507293e827c46c93bdb5b, 6a70a5159cd2ffa748a878c59a8d63fd, 6a70a51f4228124d58fda0f293b5718d, 6a70a529d19d5bd161816e0bd391b391, 6a70a533981876b400fe1f1f63b23495, 6a70a53dd534045afcc703eddaf68e88, 6a70a5474af907b3397e981d6c8f020e
- 같은 시각의 다른 후보: INC-4, INC-5, INC-6, INC-8, INC-9  (인과 여부는 판단하지 않았다)

## INC-8  <root span not yet received>
- 구간: 2026-08-03T14:24:28.800987Z ~ 2026-08-03T14:25:18.892186Z  (TEMPO · 시각 정확)
- <root span not yet received>  30,008ms (slow 채널)
- <root span not yet received>  30,016ms (slow 채널)
- <root span not yet received>  30,007ms (slow 채널)
- traceId: 6a70a49ca06ccf017d0f0d4e3795675c, 6a70a4a6b2c7c98a2f264630917bf154, 6a70a4b01759197099d4eaaad1247c81
- 같은 시각의 다른 후보: INC-4, INC-5, INC-6, INC-7  (인과 여부는 판단하지 않았다)

## INC-9  content-service  |  http post /battles/{battleId}/items/{itemId}/comments
- 구간: 2026-08-03T14:27:19.818007Z ~ 2026-08-03T14:27:49.908007Z  (TEMPO · 시각 정확)
- content-service http post /battles/{battleId}/items/{itemId}/comments 30,090ms (error 채널)
- traceId: 6a70a4cbf41848fcfa14ba00fe4a02f8
- 같은 시각의 다른 후보: INC-4, INC-5, INC-6, INC-7  (인과 여부는 판단하지 않았다)

## INC-10  chat-service  |  ERROR/WARN
- 구간: 2026-08-03T14:35:00Z ~ 2026-08-03T14:45:00Z  (LOKI · 집계 해상도만큼 흐림)
- ERROR/WARN 1건 (2026-08-03T14:35:00Z ~ 2026-08-03T14:40:00Z)
- ERROR/WARN 1건 (2026-08-03T14:40:00Z ~ 2026-08-03T14:45:00Z)
- 같은 시각의 다른 후보: INC-6  (인과 여부는 판단하지 않았다)

**기각한 후보**

- INC-1 — content-service ERROR/WARN 1건(14:05~14:10)으로 증상 시각보다 20분 이르고 건수도 배경 수준이며 알림 경로와 무관하다.
- INC-2 — content-service GET /feeds/scroll 71ms 단발 에러(14:09:25)로, 피드 조회 경로이고 지연도 없어 알림 미도달과 인과가 없다.
- INC-3 — auth-service ERROR/WARN 4건(14:10~14:15)은 chat-service 정지 시각보다 10분 이상 앞서고 이후 auth-service에 추가 신호가 전혀 없어 별개 잡음으로 본다.

**보정 기록**

- 창을 후보 [INC-4, INC-5, INC-6, INC-7, INC-8, INC-9, INC-10] 의 신호 시각에서 계산했다 (2026-08-03T14:15:00Z ~ 2026-08-03T14:54:11Z)

## 수집 범위 (Coverage)

- **window**: 2026-08-03T14:15:00Z ~ 2026-08-03T14:54:11Z (2351s)
- **trace**: 122,322B / 124 spans (상위 span만)
- **창 안 후보 트레이스**: 20건 / 122,322B
- **logs**: errwarn=216,549B · traceId=61,249B
- **metrics**: 8 수집 / 246,770B, 누락 [sum(rate(http_server_requests_seconds_count{application="content-service", status="401"}[1m]))]
- **context**: 400,115 chars (+ 시스템 프롬프트 576 chars)

### 토큰 축 (개선 지표)

| 단계 | 총 in | chars | 컨텍스트 토큰 |
|---|---:|---:|---:|
| 탐색 | 53,190 | 58,980 | 측정 안 됨 |
| 분석 | 230,217 | 400,691 | 측정 안 됨 |
| **합계** | **283,407** | | **측정 안 됨** |

- ⚠ **overheadTokens 측정 안 됨** — 컨텍스트 토큰이 비어 있다. 이 회차 수치를 쓰려면
  `docs/round-1-input-tokens.md`의 다른 날 상수로 **추정**해야 하고, 그건 `▓ 추정`이다
  (그 상수는 하루 만에 20% 움직인 적이 있다).
- contextTokens (count_tokens API): 측정 안 됨 — 구독 CLI 경로엔 API 키가 없다
- 비용은 **API 환산 추정치**다. 구독 계정으로 호출하므로 실제 청구액이 아니다.

## 수집 실패/누락

- Metric 'sum(rate(http_server_requests_seconds_count{application="content-service", status="401"}[1m]))' returned no series in this window; skipped.

---

# 장애 분석 — scan-1785766500

**핵심 결론:** `chat-service`가 의존하는 MongoDB(`172.31.46.124:27017`)가 14:24Z경 셧다운되어 알림 저장(`user_notifications.insert`)이 전면 실패했고, 그 여파로 Kafka 알림 컨슈머가 재시도→DLQ 경로로 밀리면서 알림이 최대 5분 지연·정체됐다. 이후 chat-service 파드가 교체되는 구간에서 컨슈머가 멈춰 2차 정체(lag 26)가 발생했다.

## 1. 원인 후보 랭킹

1. **MongoDB 인스턴스(172.31.46.124:27017) 다운 → chat-service 알림 저장 실패 및 Kafka 재시도/DLQ 적체**
2. **chat-service 파드 교체(14:32:45Z~14:41:15Z 관측 공백) 중 `notification-processors` 컨슈머 정지 → `user.notifications` p3 lag 26 적체**
3. **WebSocket 실시간 전달 경로 미도달 (수신자 오프라인 판정, `websocket_active_users=0`)**

---

## 2. 후보별 근거

### 후보 1 — MongoDB 다운 (근본 원인)

**근거**
- 셧다운 신호: 14:24:14Z(23:24:14 KST) chat-service 로그 — `com.mongodb.MongoNodeIsRecoveringException: Command failed with error 11600 (InterruptedAtShutdown): 'interrupted at shutdown' on server 172.31.46.124:27017`. 이후 곧바로 `MongoSocketOpenException` → `AnnotatedConnectException: Connection refused: /172.31.46.124:27017`로 전환.
- 인프라 메트릭 일치: `mongodb_up{instance=infra-server}` 이 **14:24:45Z ~ 14:29:30Z 구간 0**, 그 외 1.
- 알림 파이프라인 전 구간에 동일 예외:
  - `kafka/user.notifications --messaging--> chat-service` 4회, 최대 **30029.9ms**, error = `Timed out while waiting for a server that matches WritableServerSelector ... Connection refused: /172.31.46.124:27017`
  - `kafka/user.notifications.dlq --messaging--> chat-service` 3회, 최대 **30090.2ms**, 동일 에러
  - 스택트레이스 최하단이 `UserNotificationService.saveNotification(UserNotificationService.java:82)` → `SimpleMongoRepository.save` → `MongoTemplate.insertDocument` — 즉 **알림 문서 insert 지점에서 죽는다**.
- 단일 알림(traceId `6a70a4cbf41848fcfa14ba00fe4a02f8`)의 전체 타임라인:
  - 14:25:15Z content-service `[notify] 알림 발행 성공: userId=7, type=BATTLE_ITEM_COMMENT, partition=3, offset=1045`
  - 14:25:45 / 14:26:16 / 14:26:47 / 14:27:18Z — `user-notification 처리 실패 1~4회차` (각 30초 Mongo 타임아웃)
  - 14:27:18Z `[config] DLQ 발행: user.notifications -> user.notifications.dlq (partition=3 offset=1045)`
  - 14:27:49Z, 14:29:20Z `DLQ 알림 재처리 실패 (1분 후 재시도)`
  - **14:30:20Z `DLQ 알림 재처리 성공`** — `insert toychat`(3.6ms) 성공, FCM `멀티캐스트 결과: tokens=1, success=1, failure=0`
  - → **발행에서 실제 전달까지 5분 5초 지연.** 사용자 체감상 "알림이 안 온다".
- 범위 한정 근거: 같은 호스트의 Redis(`172.31.46.124:6379`)는 정상(`KEYS` 0.6~1.5ms 성공), `up{instance=infra-server, job=node-infra/redis/kafka}` 전 구간 1. 노드/네트워크 전체 장애가 아니라 **mongod 프로세스 단위 문제**.

**확신도: 높음**

**반증 데이터**
- `mongodb_up`은 14:29:30Z에 1로 복귀했는데 chat-service는 14:28:50Z 시점에도 `Connection refused`를 기록했다. 다만 이는 30초 타임아웃이 만료되기 전의 대기 로그이며, 14:30:20Z 최초 성공과 모순되지 않는다.
- 그 외 이 후보와 배치되는 관측값: **없음.**

---

### 후보 2 — chat-service 파드 교체 구간의 컨슈머 정지 (2차 지연)

**근거**
- 파드 관측 단절: `chat-service-fdcc7c776-qrbc2`(10.42.3.43)의 모든 시리즈(`up`, `hikaricp_*`, `websocket_active_users`, `jvm_gc_*`)가 **14:32:45Z에서 끊기고**, 신규 파드 `chat-service-fdcc7c776-xf4sv`(10.42.1.47)가 **14:41:15Z부터** 등장한다. 그 사이 **8분 30초간 chat-service 시리즈가 전무**.
- 소비 정체가 정확히 그 구간에 일치: `kafka_consumergroup_lag{consumergroup=notification-processors, topic=user.notifications, partition=3}` 이 **14:32:15Z~14:42:30Z 동안 비0, 최대 26 (14:41:30Z)**, 신규 파드 기동 직후 14:42:30Z에 0으로 해소. → 이 구간에 발행된 약 26건의 알림이 최대 ~10분 늦게 전달됐다.
- 재시작을 유발했을 개연성 있는 메커니즘(관측된 사실만): Mongo 장애 동안 `/actuator/health`가 30초씩 블로킹됐다 — `Health contributor ... (mongo) took 30002ms to respond`가 10초 간격으로 반복, 트레이스의 `secured request` span 15건이 모두 **정확히 30.0초**(예: `6a70a49244908ca8f15be0b4d7a168b5`, 30013ms). 같은 시각 qrbc2의 메트릭 스크레이프도 **14:24:45Z~14:30:00Z 결측**.

**확신도: 중간** (컨슈머 정지와 lag 적체는 확정. 다만 **파드가 왜 사라졌는지는 데이터 부족** — k8s Event, 파드 재시작 카운트, 프로브 설정, OOMKill 여부를 확보하지 못했다.)

**반증 데이터**
- `up{pod=chat-service-fdcc7c776-qrbc2}`은 존재하는 구간 내내 값 1이며 0으로 떨어진 샘플이 없다. 즉 "프로세스가 죽는 순간"이 메트릭에 잡히지 않았고, 단절은 시리즈 소멸 형태다. 크래시가 아니라 정상 종료·재스케줄일 가능성도 배제할 수 없다.
- 두 파드의 ReplicaSet 해시가 `fdcc7c776`으로 동일 — 신규 배포(이미지 변경)로 인한 롤아웃이라는 증거는 없다.

---

### 후보 3 — WebSocket 실시간 전달 미도달 (수신자 오프라인)

**근거**
- 복구 후 성공한 알림조차 WebSocket으로는 나가지 않았다: 14:30:20Z `[notify] 사용자 오프라인 상태로 WebSocket 전송 스킵: userId=7`, 이어 `[push] 시작: userId=7, onlineDevices=[] (제외 대상)`.
- `websocket_active_users`가 **두 파드 모두 전 구간 0** (qrbc2 14:15:00~14:32:45Z, xf4sv 14:41:15~14:54:00Z).
- 즉 조회 시간창 동안 이 클러스터의 chat-service에 WebSocket 세션이 하나도 없었으므로, 인앱 실시간 알림 경로는 애초에 동작할 수 없는 상태였다.

**확신도: 낮음** (문의의 "알림이 안 온다"가 인앱 실시간을 뜻하는지 푸시를 뜻하는지 판별할 데이터가 없다. 또한 세션 0이 장애의 결과인지 원래 트래픽이 없었던 것인지 구분할 수 없다.)

**반증 데이터**
- FCM 푸시는 정상 발송됐다 — `[push] 멀티캐스트 결과: tokens=1, success=1, failure=0`, `messageId=projects/toy-chat-30d47/messages/1785767420477017`. 즉 최소한 푸시 채널로는 (지연되긴 했으나) 도달했다. 이 후보 단독으로는 "알림 미수신"을 설명하지 못한다.

---

### 검토 후 기각한 가설

- **MySQL / Hikari 커넥션 풀 고갈**: 트레이스상 `chat-service --jdbc--> mysql/content` 커넥션 span이 최대 **30084.4ms**로 길지만, `hikaricp_connections_active`·`hikaricp_connections_pending`은 chat/content/auth 전 파드에서 **전 구간 0**이다. 해당 span의 events가 `acquired`(즉시) → 30초 후 `rollback`인 것으로 보아, 트랜잭션 안에서 Mongo 호출이 블로킹되며 JDBC 커넥션을 붙잡고 있었을 뿐 MySQL 자체는 느리지 않았다. content-service의 동일 풀은 최대 102.7ms로 정상.
- **Kafka 브로커 장애**: `kafka_brokers` 전 구간 1, `up{job=kafka}` 전 구간 1, 발행(`publish user.notifications` 58.1ms)도 정상 성공.
- **인증(401) 문제**: `sum(rate(http_server_requests_seconds_count{application="content-service", status="401"}[1m]))` 수집 실패로 **데이터 부족**. 다만 content-service는 `POST /battles/{battleId}/items/{itemId}/comments`에서 `status=200, outcome=SUCCESS`로 처리되고 알림 발행까지 성공했으므로, 이 경로에서 인증이 원인이라는 정황은 없다.

---

## 3. 권장 다음 조치

**즉시 (원인 확정)**
1. mongod 종료 사유 확인: `172.31.46.124` 호스트에서 `journalctl -u mongod --since "2026-08-03 23:20" --until "23:32"` 및 mongod 로그의 shutdown 직전 라인. `InterruptedAtShutdown`은 **정상 셧다운 시그널**이므로 OOM-killer(`dmesg -T | grep -i oom`), systemd 재시작, 수동/자동 유지보수 중 무엇인지 판별할 것.
2. chat-service 파드 소멸 사유 확인: `kubectl get events -n default --field-selector involvedObject.name=chat-service-fdcc7c776-qrbc2`, `kubectl describe pod chat-service-fdcc7c776-xf4sv` (lastState / restartCount / Killing·Unhealthy 이벤트). 14:32~14:41Z 구간 확인.
3. 잔여 미처리 알림 확인: `user.notifications`(p3)와 `user.notifications.dlq`(p0) 현재 lag 및 DLQ 잔존 오프셋 점검 — 데이터상 offset 14는 14:30:20Z에 소진됐으나, 파드 교체 구간(14:32~14:42Z)에 DLQ로 넘어간 건이 있는지 확인.

**단기 (재발 시 영향 축소)**
4. **liveness/readiness 프로브에서 Mongo 헬스 인디케이터 분리**: Mongo 다운 시 `/actuator/health`가 30초 블로킹되어 프로브 타임아웃과 메트릭 스크레이프 결측을 동시에 유발했다. `management.endpoint.health.group.liveness.include`에서 mongo 제외 + `spring.data.mongodb.*` 서버 선택 타임아웃(현재 30초)을 3~5초로 하향.
5. MongoDB 단일 인스턴스 구성 확인: 클라이언트 로그가 `SingleServerCluster.selectServer`를 타고 있어 **레플리카셋 없이 단일 노드**로 보인다. 알림 저장이 이 단일 노드에 직결되므로 레플리카셋화 또는 저장 실패 시 degrade 경로(푸시 우선 발송 후 비동기 저장) 검토.

**모니터링/알람**
6. `mongodb_up == 0`, `kafka_consumergroup_lag{consumergroup="notification-processors"} > 0 for 2m`, chat-service 파드 부재(`absent(up{job="chat-service"})`)에 대한 알람 추가. 이번 건은 세 신호 모두 명확히 떴지만 사용자 문의로 인지됐다.
7. `websocket_active_users`가 전 구간 0인 것이 정상인지 확인 — 정상이 아니라면 WebSocket 연결 경로(인그레스/세션 저장소)를 별도 조사 대상으로 승격.

**남은 데이터 공백 (결론 확신도 제한 요인)**
- content-service 401 메트릭 수집 실패(시리즈 없음)
- k8s Event / 파드 재시작 원인 데이터 없음 → 후보 2의 인과는 미확정
- mongod 자체 로그·호스트 리소스 메트릭 없음 → 후보 1의 **트리거**는 미확정(장애 사실 자체는 확정)
- 관측된 알림 이벤트가 userId=7 1건뿐 → "여러 건 문의"와의 대응은 p3 lag 26이라는 간접 증거에 의존

---

## 관측 증거 (Evidence)

> 이 조사가 실제로 본 값이다. 원본 응답 전체는 `reports/raw/scan-1785766500-*.json`에 있다.

### 호출 그래프 (트레이스에서 추출)

```
chat-service --db--> mongodb  7회  최대 3.6ms  [insert, find]
chat-service --db--> redis  2회  최대 1.5ms  [KEYS]
content-service --db--> redis  1회  최대 0.6ms  [GET]
chat-service --jdbc--> mysql/content (HikariPool-1)  45회  최대 30084.4ms
    events: acquired, rollback, commit
content-service --jdbc--> mysql/content (HikariPool-1)  8회  최대 102.7ms
    events: acquired, commit
chat-service --messaging--> kafka/user.notifications.dlq  1회  최대 894.0ms  [publish]
content-service --messaging--> kafka/user.notifications  1회  최대 58.1ms  [publish]
kafka/user.notifications --messaging--> chat-service  4회  최대 30029.9ms  [receive]
    error: Timed out while waiting for a server that matches WritableServerSelector. Client view of cluster state is {type=UNKNOWN, servers=[{address=172.31.46.124:27017, type=UNKNOWN, state=CONNECTING, exception={com.mongodb.MongoSocketOpenException: Exception opening socket}, caused by {io.netty.channel.Abst…
kafka/user.notifications.dlq --messaging--> chat-service  3회  최대 30090.2ms  [receive]
    error: Timed out while waiting for a server that matches WritableServerSelector. Client view of cluster state is {type=UNKNOWN, servers=[{address=172.31.46.124:27017, type=UNKNOWN, state=CONNECTING, exception={com.mongodb.MongoSocketOpenException: Exception opening socket}, caused by {io.netty.channel.Abst…
```

### span (duration 상위 15 / 전체 124)

| ms | service | span | 시작 |
|---:|---|---|---|
| 30090.16 | chat-service | `receive` | 2026-08-03T14:27:19.818007Z |
| 30088.01 | chat-service | `secured request` | 2026-08-03T14:26:09.218489Z |
| 30084.38 | chat-service | `connection` | 2026-08-03T14:27:19.819415Z |
| 30077.54 | chat-service | `secured request` | 2026-08-03T14:27:19.537312Z |
| 30074.72 | chat-service | `user-notification-service#process-notification` | 2026-08-03T14:27:19.825637Z |
| 30030.88 | chat-service | `secured request` | 2026-08-03T14:26:49.385232Z |
| 30029.94 | chat-service | `receive` | 2026-08-03T14:25:15.810769Z |
| 30021.34 | chat-service | `connection` | 2026-08-03T14:25:15.811294Z |
| 30016.13 | chat-service | `secured request` | 2026-08-03T14:24:38.843031Z |
| 30013.33 | chat-service | `secured request` | 2026-08-03T14:24:18.746875Z |
| 30012.56 | chat-service | `receive` | 2026-08-03T14:25:46.951072Z |
| 30012.47 | chat-service | `receive` | 2026-08-03T14:28:50.001304Z |
| 30011.43 | chat-service | `receive` | 2026-08-03T14:26:48.986521Z |
| 30010.90 | chat-service | `receive` | 2026-08-03T14:26:17.969109Z |
| 30010.21 | chat-service | `user-notification-service#process-notification` | 2026-08-03T14:25:15.814471Z |

### 로그 원문 (60 / 전체 1,083줄)

전체가 상한을 넘어 ERROR/WARN 줄을 우선 발췌했다. 나머지는 원본 파일에 있다.

```
2026-08-03T14:24:14.410532179Z  [chat-service]  [2m2026-08-03T23:24:14.376+09:00[0;39m [32m INFO [traceId=NONE,spanId=NONE,userId=NONE][0;39m [35m7[0;39m [2m--- [chat-service] [31.46.124:27017] [                                                 ] [0;39m[36morg.mongodb.driver.cluster              [0;39m [2m:[0;39m Exception in monitor thread while connecting to server 172.31.46.124:27017
2026-08-03T14:24:14.410564521Z  [chat-service]  com.mongodb.MongoNodeIsRecoveringException: Command failed with error 11600 (InterruptedAtShutdown): 'interrupted at shutdown' on server 172.31.46.124:27017. The full response is {"ok": 0.0, "errmsg": "interrupted at shutdown", "code": 11600, "codeName": "InterruptedAtShutdown"}
2026-08-03T14:24:14.410569149Z  [chat-service]  at com.mongodb.internal.connection.ProtocolHelper.createSpecialException(ProtocolHelper.java:264) ~[mongodb-driver-core-5.5.1.jar!/:na]
2026-08-03T14:24:14.410571954Z  [chat-service]  at com.mongodb.internal.connection.ProtocolHelper.getCommandFailureException(ProtocolHelper.java:206) ~[mongodb-driver-core-5.5.1.jar!/:na]
2026-08-03T14:24:14.411138659Z  [chat-service]  [2m2026-08-03T23:24:14.375+09:00[0;39m [32m INFO [traceId=NONE,spanId=NONE,userId=NONE][0;39m [35m7[0;39m [2m--- [chat-service] [31.46.124:27017] [                                                 ] [0;39m[36morg.mongodb.driver.cluster              [0;39m [2m:[0;39m Exception in monitor thread while connecting to server 172.31.46.124:27017
2026-08-03T14:24:14.411150943Z  [chat-service]  com.mongodb.MongoNodeIsRecoveringException: Command failed with error 11600 (InterruptedAtShutdown): 'interrupted at shutdown' on server 172.31.46.124:27017. The full response is {"ok": 0.0, "errmsg": "interrupted at shutdown", "code": 11600, "codeName": "InterruptedAtShutdown"}
2026-08-03T14:24:14.411153880Z  [chat-service]  at com.mongodb.internal.connection.ProtocolHelper.createSpecialException(ProtocolHelper.java:264) ~[mongodb-driver-core-5.5.1.jar!/:na]
2026-08-03T14:24:14.411156388Z  [chat-service]  at com.mongodb.internal.connection.ProtocolHelper.getCommandFailureException(ProtocolHelper.java:206) ~[mongodb-driver-core-5.5.1.jar!/:na]
2026-08-03T14:24:18.760578677Z  [chat-service]  [2m2026-08-03T23:24:18.760+09:00[0;39m [32m INFO [traceId=6a70a49244908ca8f15be0b4d7a168b5,spanId=3f466452dbb6fed6,userId=NONE][0;39m [35m7[0;39m [2m--- [chat-service] [nio-8090-exec-7] [6a70a49244908ca8f15be0b4d7a168b5-3f466452dbb6fed6] [0;39m[36morg.mongodb.driver.cluster              [0;39m [2m:[0;39m Waiting for server to become available for operation with ID 43999. Remaining time: 29992 ms. Selector: ReadPreferenceServerSelector{readPreference=primary}, topology description: {type=UNKNOWN, servers=[{address=172.31.46.124:27017, type=UNKNOWN, state=CONNECTING, exception={com.mongodb.MongoNodeIsRecoveringException: Command failed with error 11600 (InterruptedAtShutdown): 'interrupted at shutdown' on server 172.31.46.124:27017. The full response is {"ok": 0.0, "errmsg": "interrupted at shutdown", "code": 11600, "codeName": "InterruptedAtShutdown"}}}].
2026-08-03T14:24:18.807356522Z  [chat-service]  [2m2026-08-03T23:24:18.784+09:00[0;39m [32m INFO [traceId=NONE,spanId=NONE,userId=NONE][0;39m [35m7[0;39m [2m--- [chat-service] [31.46.124:27017] [                                                 ] [0;39m[36morg.mongodb.driver.cluster              [0;39m [2m:[0;39m Exception in monitor thread while connecting to server 172.31.46.124:27017
2026-08-03T14:24:18.807405527Z  [chat-service]  com.mongodb.MongoSocketOpenException: Exception opening socket
2026-08-03T14:24:18.807472669Z  [chat-service]  Caused by: io.netty.channel.AbstractChannel$AnnotatedConnectException: Connection refused: /172.31.46.124:27017
2026-08-03T14:24:18.807474455Z  [chat-service]  Caused by: java.net.ConnectException: Connection refused
2026-08-03T14:24:24.416471475Z  [chat-service]  [2m2026-08-03T23:24:24.415+09:00[0;39m [32m INFO [traceId=NONE,spanId=NONE,userId=NONE][0;39m [35m7[0;39m [2m--- [chat-service] [31.46.124:27017] [                                                 ] [0;39m[36morg.mongodb.driver.cluster              [0;39m [2m:[0;39m Exception in monitor thread while connecting to server 172.31.46.124:27017
2026-08-03T14:24:24.416507090Z  [chat-service]  com.mongodb.MongoSocketOpenException: Exception opening socket
2026-08-03T14:24:24.416585863Z  [chat-service]  Caused by: io.netty.channel.AbstractChannel$AnnotatedConnectException: Connection refused: /172.31.46.124:27017
2026-08-03T14:24:24.416587643Z  [chat-service]  Caused by: java.net.ConnectException: Connection refused
2026-08-03T14:24:28.805788349Z  [chat-service]  [2m2026-08-03T23:24:28.805+09:00[0;39m [32m INFO [traceId=6a70a49ca06ccf017d0f0d4e3795675c,spanId=12ba7af26a7286c7,userId=NONE][0;39m [35m7[0;39m [2m--- [chat-service] [nio-8090-exec-8] [6a70a49ca06ccf017d0f0d4e3795675c-12ba7af26a7286c7] [0;39m[36morg.mongodb.driver.cluster              [0;39m [2m:[0;39m Waiting for server to become available for operation with ID 44023. Remaining time: 29999 ms. Selector: ReadPreferenceServerSelector{readPreference=primary}, topology description: {type=UNKNOWN, servers=[{address=172.31.46.124:27017, type=UNKNOWN, state=CONNECTING, exception={com.mongodb.MongoSocketOpenException: Exception opening socket}, caused by {io.netty.channel.AbstractChannel$AnnotatedConnectException: Connection refused: /172.31.46.124:27017}, caused by {java.net.ConnectException: Connection refused}}].
2026-08-03T14:24:38.847344974Z  [chat-service]  [2m2026-08-03T23:24:38.847+09:00[0;39m [32m INFO [traceId=6a70a4a6b2c7c98a2f264630917bf154,spanId=3c258ce7810d3917,userId=NONE][0;39m [35m7[0;39m [2m--- [chat-service] [nio-8090-exec-9] [6a70a4a6b2c7c98a2f264630917bf154-3c258ce7810d3917] [0;39m[36morg.mongodb.driver.cluster              [0;39m [2m:[0;39m Waiting for server to become available for operation with ID 44048. Remaining time: 29999 ms. Selector: ReadPreferenceServerSelector{readPreference=primary}, topology description: {type=UNKNOWN, servers=[{address=172.31.46.124:27017, type=UNKNOWN, state=CONNECTING, exception={com.mongodb.MongoSocketOpenException: Exception opening socket}, caused by {io.netty.channel.AbstractChannel$AnnotatedConnectException: Connection refused: /172.31.46.124:27017}, caused by {java.net.ConnectException: Connection refused}}].
2026-08-03T14:24:48.753300999Z  [chat-service]  org.springframework.dao.DataAccessResourceFailureException: Timed out while waiting for a server that matches ReadPreferenceServerSelector{readPreference=primary}. Client view of cluster state is {type=UNKNOWN, servers=[{address=172.31.46.124:27017, type=UNKNOWN, state=CONNECTING, exception={com.mongodb.MongoSocketOpenException: Exception opening socket}, caused by {io.netty.channel.AbstractChannel$AnnotatedConnectException: Connection refused: /172.31.46.124:27017}, caused by {java.net.ConnectException: Connection refused}}]
2026-08-03T14:24:48.753304890Z  [chat-service]  at org.springframework.data.mongodb.core.MongoExceptionTranslator.doTranslateException(MongoExceptionTranslator.java:97) ~[spring-data-mongodb-4.5.1.jar!/:4.5.1]
2026-08-03T14:24:48.753308492Z  [chat-service]  at org.springframework.data.mongodb.core.MongoExceptionTranslator.translateExceptionIfPossible(MongoExceptionTranslator.java:74) ~[spring-data-mongodb-4.5.1.jar!/:4.5.1]
2026-08-03T14:24:48.753312275Z  [chat-service]  at org.springframework.data.mongodb.core.ReactiveMongoTemplate.potentiallyConvertRuntimeException(ReactiveMongoTemplate.java:2768) ~[spring-data-mongodb-4.5.1.jar!/:4.5.1]
2026-08-03T14:24:48.753315499Z  [chat-service]  at org.springframework.data.mongodb.core.ReactiveMongoTemplate.lambda$translateException$100(ReactiveMongoTemplate.java:2751) ~[spring-data-mongodb-4.5.1.jar!/:4.5.1]
2026-08-03T14:24:48.753434942Z  [chat-service]  Caused by: com.mongodb.MongoTimeoutException: Timed out while waiting for a server that matches ReadPreferenceServerSelector{readPreference=primary}. Client view of cluster state is {type=UNKNOWN, servers=[{address=172.31.46.124:27017, type=UNKNOWN, state=CONNECTING, exception={com.mongodb.MongoSocketOpenException: Exception opening socket}, caused by {io.netty.channel.AbstractChannel$AnnotatedConnectException: Connection refused: /172.31.46.124:27017}, caused by {java.net.ConnectException: Connection refused}}]
2026-08-03T14:24:48.753437660Z  [chat-service]  at com.mongodb.internal.connection.BaseCluster.logAndThrowTimeoutException(BaseCluster.java:427) ~[mongodb-driver-core-5.5.1.jar!/:na]
2026-08-03T14:24:48.889705610Z  [chat-service]  [2m2026-08-03T23:24:48.889+09:00[0;39m [32m INFO [traceId=6a70a4b01759197099d4eaaad1247c81,spanId=09069237ac9110ae,userId=NONE][0;39m [35m7[0;39m [2m--- [chat-service] [nio-8090-exec-1] [6a70a4b01759197099d4eaaad1247c81-09069237ac9110ae] [0;39m[36morg.mongodb.driver.cluster              [0;39m [2m:[0;39m Waiting for server to become available for operation with ID 44072. Remaining time: 29999 ms. Selector: ReadPreferenceServerSelector{readPreference=primary}, topology description: {type=UNKNOWN, servers=[{address=172.31.46.124:27017, type=UNKNOWN, state=CONNECTING, exception={com.mongodb.MongoSocketOpenException: Exception opening socket}, caused by {io.netty.channel.AbstractChannel$AnnotatedConnectException: Connection refused: /172.31.46.124:27017}, caused by {java.net.ConnectException: Connection refused}}].
2026-08-03T14:24:58.806492515Z  [chat-service]  org.springframework.dao.DataAccessResourceFailureException: Timed out while waiting for a server that matches ReadPreferenceServerSelector{readPreference=primary}. Client view of cluster state is {type=UNKNOWN, servers=[{address=172.31.46.124:27017, type=UNKNOWN, state=CONNECTING, exception={com.mongodb.MongoSocketOpenException: Exception opening socket}, caused by {io.netty.channel.AbstractChannel$AnnotatedConnectException: Connection refused: /172.31.46.124:27017}, caused by {java.net.ConnectException: Connection refused}}]
2026-08-03T14:24:58.806496239Z  [chat-service]  at org.springframework.data.mongodb.core.MongoExceptionTranslator.doTranslateException(MongoExceptionTranslator.java:97) ~[spring-data-mongodb-4.5.1.jar!/:4.5.1]
2026-08-03T14:24:58.806499171Z  [chat-service]  at org.springframework.data.mongodb.core.MongoExceptionTranslator.translateExceptionIfPossible(MongoExceptionTranslator.java:74) ~[spring-data-mongodb-4.5.1.jar!/:4.5.1]
2026-08-03T14:24:58.806524653Z  [chat-service]  at org.springframework.data.mongodb.core.ReactiveMongoTemplate.potentiallyConvertRuntimeException(ReactiveMongoTemplate.java:2768) ~[spring-data-mongodb-4.5.1.jar!/:4.5.1]
2026-08-03T14:24:58.807306671Z  [chat-service]  at org.springframework.data.mongodb.core.ReactiveMongoTemplate.lambda$translateException$100(ReactiveMongoTemplate.java:2751) ~[spring-data-mongodb-4.5.1.jar!/:4.5.1]
2026-08-03T14:24:58.807434895Z  [chat-service]  Caused by: com.mongodb.MongoTimeoutException: Timed out while waiting for a server that matches ReadPreferenceServerSelector{readPreference=primary}. Client view of cluster state is {type=UNKNOWN, servers=[{address=172.31.46.124:27017, type=UNKNOWN, state=CONNECTING, exception={com.mongodb.MongoSocketOpenException: Exception opening socket}, caused by {io.netty.channel.AbstractChannel$AnnotatedConnectException: Connection refused: /172.31.46.124:27017}, caused by {java.net.ConnectException: Connection refused}}]
2026-08-03T14:24:58.807437399Z  [chat-service]  at com.mongodb.internal.connection.BaseCluster.logAndThrowTimeoutException(BaseCluster.java:427) ~[mongodb-driver-core-5.5.1.jar!/:na]
2026-08-03T14:24:58.930289675Z  [chat-service]  [2m2026-08-03T23:24:58.930+09:00[0;39m [32m INFO [traceId=6a70a4bac0013ba673497f1f78b893f8,spanId=81abfa95851e3142,userId=NONE][0;39m [35m7[0;39m [2m--- [chat-service] [nio-8090-exec-3] [6a70a4bac0013ba673497f1f78b893f8-81abfa95851e3142] [0;39m[36morg.mongodb.driver.cluster              [0;39m [2m:[0;39m Waiting for server to become available for operation with ID 44096. Remaining time: 29999 ms. Selector: ReadPreferenceServerSelector{readPreference=primary}, topology description: {type=UNKNOWN, servers=[{address=172.31.46.124:27017, type=UNKNOWN, state=CONNECTING, exception={com.mongodb.MongoSocketOpenException: Exception opening socket}, caused by {io.netty.channel.AbstractChannel$AnnotatedConnectException: Connection refused: /172.31.46.124:27017}, caused by {java.net.ConnectException: Connection refused}}].
2026-08-03T14:25:08.852599120Z  [chat-service]  org.springframework.dao.DataAccessResourceFailureException: Timed out while waiting for a server that matches ReadPreferenceServerSelector{readPreference=primary}. Client view of cluster state is {type=UNKNOWN, servers=[{address=172.31.46.124:27017, type=UNKNOWN, state=CONNECTING, exception={com.mongodb.MongoSocketOpenException: Exception opening socket}, caused by {io.netty.channel.AbstractChannel$AnnotatedConnectException: Connection refused: /172.31.46.124:27017}, caused by {java.net.ConnectException: Connection refused}}]
2026-08-03T14:25:08.852602935Z  [chat-service]  at org.springframework.data.mongodb.core.MongoExceptionTranslator.doTranslateException(MongoExceptionTranslator.java:97) ~[spring-data-mongodb-4.5.1.jar!/:4.5.1]
2026-08-03T14:25:08.852605258Z  [chat-service]  at org.springframework.data.mongodb.core.MongoExceptionTranslator.translateExceptionIfPossible(MongoExceptionTranslator.java:74) ~[spring-data-mongodb-4.5.1.jar!/:4.5.1]
2026-08-03T14:25:08.852607581Z  [chat-service]  at org.springframework.data.mongodb.core.ReactiveMongoTemplate.potentiallyConvertRuntimeException(ReactiveMongoTemplate.java:2768) ~[spring-data-mongodb-4.5.1.jar!/:4.5.1]
2026-08-03T14:25:08.852610141Z  [chat-service]  at org.springframework.data.mongodb.core.ReactiveMongoTemplate.lambda$translateException$100(ReactiveMongoTemplate.java:2751) ~[spring-data-mongodb-4.5.1.jar!/:4.5.1]
2026-08-03T14:25:08.853434273Z  [chat-service]  Caused by: com.mongodb.MongoTimeoutException: Timed out while waiting for a server that matches ReadPreferenceServerSelector{readPreference=primary}. Client view of cluster state is {type=UNKNOWN, servers=[{address=172.31.46.124:27017, type=UNKNOWN, state=CONNECTING, exception={com.mongodb.MongoSocketOpenException: Exception opening socket}, caused by {io.netty.channel.AbstractChannel$AnnotatedConnectException: Connection refused: /172.31.46.124:27017}, caused by {java.net.ConnectException: Connection refused}}]
2026-08-03T14:25:08.853437293Z  [chat-service]  at com.mongodb.internal.connection.BaseCluster.logAndThrowTimeoutException(BaseCluster.java:427) ~[mongodb-driver-core-5.5.1.jar!/:na]
2026-08-03T14:25:08.972009720Z  [chat-service]  [2m2026-08-03T23:25:08.971+09:00[0;39m [32m INFO [traceId=6a70a4c409d0baac34a37e5a651c761d,spanId=1e7c504e1fc88027,userId=NONE][0;39m [35m7[0;39m [2m--- [chat-service] [nio-8090-exec-4] [6a70a4c409d0baac34a37e5a651c761d-1e7c504e1fc88027] [0;39m[36morg.mongodb.driver.cluster              [0;39m [2m:[0;39m Waiting for server to become available for operation with ID 44120. Remaining time: 29999 ms. Selector: ReadPreferenceServerSelector{readPreference=primary}, topology description: {type=UNKNOWN, servers=[{address=172.31.46.124:27017, type=UNKNOWN, state=CONNECTING, exception={com.mongodb.MongoSocketOpenException: Exception opening socket}, caused by {io.netty.channel.AbstractChannel$AnnotatedConnectException: Connection refused: /172.31.46.124:27017}, caused by {java.net.ConnectException: Connection refused}}].
2026-08-03T14:25:15.816120105Z  [chat-service]  [2m2026-08-03T23:25:15.815+09:00[0;39m [32m INFO [traceId=6a70a4cbf41848fcfa14ba00fe4a02f8,spanId=9309bc69b2a7c73a,userId=NONE][0;39m [35m7[0;39m [2m--- [chat-service] [ntainer#5-1-C-1] [6a70a4cbf41848fcfa14ba00fe4a02f8-9309bc69b2a7c73a] [0;39m[36morg.mongodb.driver.cluster              [0;39m [2m:[0;39m Waiting for server to become available for operation with ID 44135. Remaining time: 29999 ms. Selector: WritableServerSelector, topology description: {type=UNKNOWN, servers=[{address=172.31.46.124:27017, type=UNKNOWN, state=CONNECTING, exception={com.mongodb.MongoSocketOpenException: Exception opening socket}, caused by {io.netty.channel.AbstractChannel$AnnotatedConnectException: Connection refused: /172.31.46.124:27017}, caused by {java.net.ConnectException: Connection refused}}].
2026-08-03T14:25:18.890720165Z  [chat-service]  org.springframework.dao.DataAccessResourceFailureException: Timed out while waiting for a server that matches ReadPreferenceServerSelector{readPreference=primary}. Client view of cluster state is {type=UNKNOWN, servers=[{address=172.31.46.124:27017, type=UNKNOWN, state=CONNECTING, exception={com.mongodb.MongoSocketOpenException: Exception opening socket}, caused by {io.netty.channel.AbstractChannel$AnnotatedConnectException: Connection refused: /172.31.46.124:27017}, caused by {java.net.ConnectException: Connection refused}}]
2026-08-03T14:25:18.890756249Z  [chat-service]  at org.springframework.data.mongodb.core.MongoExceptionTranslator.doTranslateException(MongoExceptionTranslator.java:97) ~[spring-data-mongodb-4.5.1.jar!/:4.5.1]
2026-08-03T14:25:19.014409970Z  [chat-service]  [2m2026-08-03T23:25:19.014+09:00[0;39m [32m INFO [traceId=6a70a4cf6953872624c277253c4aae4b,spanId=95e6e6a2284f941e,userId=NONE][0;39m [35m7[0;39m [2m--- [chat-service] [nio-8090-exec-6] [6a70a4cf6953872624c277253c4aae4b-95e6e6a2284f941e] [0;39m[36morg.mongodb.driver.cluster              [0;39m [2m:[0;39m Waiting for server to become available for operation with ID 44152. Remaining time: 29999 ms. Selector: ReadPreferenceServerSelector{readPreference=primary}, topology description: {type=UNKNOWN, servers=[{address=172.31.46.124:27017, type=UNKNOWN, state=CONNECTING, exception={com.mongodb.MongoSocketOpenException: Exception opening socket}, caused by {io.netty.channel.AbstractChannel$AnnotatedConnectException: Connection refused: /172.31.46.124:27017}, caused by {java.net.ConnectException: Connection refused}}].
2026-08-03T14:25:29.055820295Z  [chat-service]  [2m2026-08-03T23:25:29.055+09:00[0;39m [32m INFO [traceId=6a70a4d900ac17f8b3eed1dff5a1f7cd,spanId=432f586a31bebee8,userId=NONE][0;39m [35m7[0;39m [2m--- [chat-service] [io-8090-exec-10] [6a70a4d900ac17f8b3eed1dff5a1f7cd-432f586a31bebee8] [0;39m[36morg.mongodb.driver.cluster              [0;39m [2m:[0;39m Waiting for server to become available for operation with ID 44195. Remaining time: 29999 ms. Selector: ReadPreferenceServerSelector{readPreference=primary}, topology description: {type=UNKNOWN, servers=[{address=172.31.46.124:27017, type=UNKNOWN, state=CONNECTING, exception={com.mongodb.MongoSocketOpenException: Exception opening socket}, caused by {io.netty.channel.AbstractChannel$AnnotatedConnectException: Connection refused: /172.31.46.124:27017}, caused by {java.net.ConnectException: Connection refused}}].
2026-08-03T14:25:39.097624003Z  [chat-service]  [2m2026-08-03T23:25:39.097+09:00[0;39m [32m INFO [traceId=6a70a4e31505a83978ab808d971228ea,spanId=173ce8b7621b845f,userId=NONE][0;39m [35m7[0;39m [2m--- [chat-service] [nio-8090-exec-7] [6a70a4e31505a83978ab808d971228ea-173ce8b7621b845f] [0;39m[36morg.mongodb.driver.cluster              [0;39m [2m:[0;39m Waiting for server to become available for operation with ID 44238. Remaining time: 29999 ms. Selector: ReadPreferenceServerSelector{readPreference=primary}, topology description: {type=UNKNOWN, servers=[{address=172.31.46.124:27017, type=UNKNOWN, state=CONNECTING, exception={com.mongodb.MongoSocketOpenException: Exception opening socket}, caused by {io.netty.channel.AbstractChannel$AnnotatedConnectException: Connection refused: /172.31.46.124:27017}, caused by {java.net.ConnectException: Connection refused}}].
2026-08-03T14:25:46.956177297Z  [chat-service]  [2m2026-08-03T23:25:46.956+09:00[0;39m [32m INFO [traceId=6a70a4cbf41848fcfa14ba00fe4a02f8,spanId=29e869f0fa39901f,userId=NONE][0;39m [35m7[0;39m [2m--- [chat-service] [ntainer#5-1-C-1] [6a70a4cbf41848fcfa14ba00fe4a02f8-29e869f0fa39901f] [0;39m[36morg.mongodb.driver.cluster              [0;39m [2m:[0;39m Waiting for server to become available for operation with ID 44270. Remaining time: 29999 ms. Selector: WritableServerSelector, topology description: {type=UNKNOWN, servers=[{address=172.31.46.124:27017, type=UNKNOWN, state=CONNECTING, exception={com.mongodb.MongoSocketOpenException: Exception opening socket}, caused by {io.netty.channel.AbstractChannel$AnnotatedConnectException: Connection refused: /172.31.46.124:27017}, caused by {java.net.ConnectException: Connection refused}}].
2026-08-03T14:25:49.141034368Z  [chat-service]  [2m2026-08-03T23:25:49.140+09:00[0;39m [32m INFO [traceId=6a70a4ed193a2b5a1f1bed00113d8b29,spanId=bd5d752803a53897,userId=NONE][0;39m [35m7[0;39m [2m--- [chat-service] [nio-8090-exec-8] [6a70a4ed193a2b5a1f1bed00113d8b29-bd5d752803a53897] [0;39m[36morg.mongodb.driver.cluster              [0;39m [2m:[0;39m Waiting for server to become available for operation with ID 44281. Remaining time: 29999 ms. Selector: ReadPreferenceServerSelector{readPreference=primary}, topology description: {type=UNKNOWN, servers=[{address=172.31.46.124:27017, type=UNKNOWN, state=CONNECTING, exception={com.mongodb.MongoSocketOpenException: Exception opening socket}, caused by {io.netty.channel.AbstractChannel$AnnotatedConnectException: Connection refused: /172.31.46.124:27017}, caused by {java.net.ConnectException: Connection refused}}].
2026-08-03T14:25:59.180275109Z  [chat-service]  [2m2026-08-03T23:25:59.180+09:00[0;39m [32m INFO [traceId=6a70a4f70407c7e4c4cd7fd17a8ddd02,spanId=903331fc601337ad,userId=NONE][0;39m [35m7[0;39m [2m--- [chat-service] [nio-8090-exec-9] [6a70a4f70407c7e4c4cd7fd17a8ddd02-903331fc601337ad] [0;39m[36morg.mongodb.driver.cluster              [0;39m [2m:[0;39m Waiting for server to become available for operation with ID 44324. Remaining time: 29999 ms. Selector: ReadPreferenceServerSelector{readPreference=primary}, topology description: {type=UNKNOWN, servers=[{address=172.31.46.124:27017, type=UNKNOWN, state=CONNECTING, exception={com.mongodb.MongoSocketOpenException: Exception opening socket}, caused by {io.netty.channel.AbstractChannel$AnnotatedConnectException: Connection refused: /172.31.46.124:27017}, caused by {java.net.ConnectException: Connection refused}}].
2026-08-03T14:26:09.303212407Z  [chat-service]  [2m2026-08-03T23:26:09.302+09:00[0;39m [32m INFO [traceId=6a70a501290c9d041c0620935eaa61db,spanId=c63bbfb0d891015f,userId=NONE][0;39m [35m7[0;39m [2m--- [chat-service] [nio-8090-exec-5] [6a70a501290c9d041c0620935eaa61db-c63bbfb0d891015f] [0;39m[36morg.mongodb.driver.cluster              [0;39m [2m:[0;39m Waiting for server to become available for operation with ID 44367. Remaining time: 29999 ms. Selector: ReadPreferenceServerSelector{readPreference=primary}, topology description: {type=UNKNOWN, servers=[{address=172.31.46.124:27017, type=UNKNOWN, state=CONNECTING, exception={com.mongodb.MongoSocketOpenException: Exception opening socket}, caused by {io.netty.channel.AbstractChannel$AnnotatedConnectException: Connection refused: /172.31.46.124:27017}, caused by {java.net.ConnectException: Connection refused}}].
2026-08-03T14:26:17.974405473Z  [chat-service]  [2m2026-08-03T23:26:17.974+09:00[0;39m [32m INFO [traceId=6a70a4cbf41848fcfa14ba00fe4a02f8,spanId=7d21df984b172d8b,userId=NONE][0;39m [35m7[0;39m [2m--- [chat-service] [ntainer#5-1-C-1] [6a70a4cbf41848fcfa14ba00fe4a02f8-7d21df984b172d8b] [0;39m[36morg.mongodb.driver.cluster              [0;39m [2m:[0;39m Waiting for server to become available for operation with ID 44403. Remaining time: 29999 ms. Selector: WritableServerSelector, topology description: {type=UNKNOWN, servers=[{address=172.31.46.124:27017, type=UNKNOWN, state=CONNECTING, exception={com.mongodb.MongoSocketOpenException: Exception opening socket}, caused by {io.netty.channel.AbstractChannel$AnnotatedConnectException: Connection refused: /172.31.46.124:27017}, caused by {java.net.ConnectException: Connection refused}}].
2026-08-03T14:26:19.264680180Z  [chat-service]  [2m2026-08-03T23:26:19.264+09:00[0;39m [32m INFO [traceId=6a70a50be94507293e827c46c93bdb5b,spanId=8e79ddefc575903b,userId=NONE][0;39m [35m7[0;39m [2m--- [chat-service] [nio-8090-exec-3] [6a70a50be94507293e827c46c93bdb5b-8e79ddefc575903b] [0;39m[36morg.mongodb.driver.cluster              [0;39m [2m:[0;39m Waiting for server to become available for operation with ID 44410. Remaining time: 29999 ms. Selector: ReadPreferenceServerSelector{readPreference=primary}, topology description: {type=UNKNOWN, servers=[{address=172.31.46.124:27017, type=UNKNOWN, state=CONNECTING, exception={com.mongodb.MongoSocketOpenException: Exception opening socket}, caused by {io.netty.channel.AbstractChannel$AnnotatedConnectException: Connection refused: /172.31.46.124:27017}, caused by {java.net.ConnectException: Connection refused}}].
2026-08-03T14:26:29.306188299Z  [chat-service]  [2m2026-08-03T23:26:29.305+09:00[0;39m [32m INFO [traceId=6a70a5159cd2ffa748a878c59a8d63fd,spanId=7945e328aab9897f,userId=NONE][0;39m [35m7[0;39m [2m--- [chat-service] [nio-8090-exec-4] [6a70a5159cd2ffa748a878c59a8d63fd-7945e328aab9897f] [0;39m[36morg.mongodb.driver.cluster              [0;39m [2m:[0;39m Waiting for server to become available for operation with ID 44453. Remaining time: 29999 ms. Selector: ReadPreferenceServerSelector{readPreference=primary}, topology description: {type=UNKNOWN, servers=[{address=172.31.46.124:27017, type=UNKNOWN, state=CONNECTING, exception={com.mongodb.MongoSocketOpenException: Exception opening socket}, caused by {io.netty.channel.AbstractChannel$AnnotatedConnectException: Connection refused: /172.31.46.124:27017}, caused by {java.net.ConnectException: Connection refused}}].
2026-08-03T14:26:39.347017289Z  [chat-service]  [2m2026-08-03T23:26:39.346+09:00[0;39m [32m INFO [traceId=6a70a51f4228124d58fda0f293b5718d,spanId=67f4d6bc8d247231,userId=NONE][0;39m [35m7[0;39m [2m--- [chat-service] [nio-8090-exec-2] [6a70a51f4228124d58fda0f293b5718d-67f4d6bc8d247231] [0;39m[36morg.mongodb.driver.cluster              [0;39m [2m:[0;39m Waiting for server to become available for operation with ID 44496. Remaining time: 29999 ms. Selector: ReadPreferenceServerSelector{readPreference=primary}, topology description: {type=UNKNOWN, servers=[{address=172.31.46.124:27017, type=UNKNOWN, state=CONNECTING, exception={com.mongodb.MongoSocketOpenException: Exception opening socket}, caused by {io.netty.channel.AbstractChannel$AnnotatedConnectException: Connection refused: /172.31.46.124:27017}, caused by {java.net.ConnectException: Connection refused}}].
2026-08-03T14:26:48.992408653Z  [chat-service]  [2m2026-08-03T23:26:48.992+09:00[0;39m [32m INFO [traceId=6a70a4cbf41848fcfa14ba00fe4a02f8,spanId=6af3ac65efeef36c,userId=NONE][0;39m [35m7[0;39m [2m--- [chat-service] [ntainer#5-1-C-1] [6a70a4cbf41848fcfa14ba00fe4a02f8-6af3ac65efeef36c] [0;39m[36morg.mongodb.driver.cluster              [0;39m [2m:[0;39m Waiting for server to become available for operation with ID 44536. Remaining time: 29999 ms. Selector: WritableServerSelector, topology description: {type=UNKNOWN, servers=[{address=172.31.46.124:27017, type=UNKNOWN, state=CONNECTING, exception={com.mongodb.MongoSocketOpenException: Exception opening socket}, caused by {io.netty.channel.AbstractChannel$AnnotatedConnectException: Connection refused: /172.31.46.124:27017}, caused by {java.net.ConnectException: Connection refused}}].
2026-08-03T14:26:49.389618911Z  [chat-service]  [2m2026-08-03T23:26:49.389+09:00[0;39m [32m INFO [traceId=6a70a529d19d5bd161816e0bd391b391,spanId=c166ead3e82fffd3,userId=NONE][0;39m [35m7[0;39m [2m--- [chat-service] [io-8090-exec-10] [6a70a529d19d5bd161816e0bd391b391-c166ead3e82fffd3] [0;39m[36morg.mongodb.driver.cluster              [0;39m [2m:[0;39m Waiting for server to become available for operation with ID 44539. Remaining time: 29999 ms. Selector: ReadPreferenceServerSelector{readPreference=primary}, topology description: {type=UNKNOWN, servers=[{address=172.31.46.124:27017, type=UNKNOWN, state=CONNECTING, exception={com.mongodb.MongoSocketOpenException: Exception opening socket}, caused by {io.netty.channel.AbstractChannel$AnnotatedConnectException: Connection refused: /172.31.46.124:27017}, caused by {java.net.ConnectException: Connection refused}}].
2026-08-03T14:26:59.431688645Z  [chat-service]  [2m2026-08-03T23:26:59.431+09:00[0;39m [32m INFO [traceId=6a70a533981876b400fe1f1f63b23495,spanId=00412e368ed41b9b,userId=NONE][0;39m [35m7[0;39m [2m--- [chat-service] [nio-8090-exec-7] [6a70a533981876b400fe1f1f63b23495-00412e368ed41b9b] [0;39m[36morg.mongodb.driver.cluster              [0;39m [2m:[0;39m Waiting for server to become available for operation with ID 44582. Remaining time: 29999 ms. Selector: ReadPreferenceServerSelector{readPreference=primary}, topology description: {type=UNKNOWN, servers=[{address=172.31.46.124:27017, type=UNKNOWN, state=CONNECTING, exception={com.mongodb.MongoSocketOpenException: Exception opening socket}, caused by {io.netty.channel.AbstractChannel$AnnotatedConnectException: Connection refused: /172.31.46.124:27017}, caused by {java.net.ConnectException: Connection refused}}].
```

### 메트릭 시계열

| 쿼리 | series | 점 | min | max | last | 값이 0이던 구간 |
|---|---|---:|---:|---:|---:|---|
| `hikaricp_connections_active` | `{__name__=hikaricp_connections_active, application=auth-service, cluster=yogurtte-k3s-prod, container=auth-service, instance=10.42.1.46:8090, job=auth-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=auth-service-5999bb9f5c-hmgp9, pool=HikariPool-1, service=auth-service}` | 157 | 0 | 0 | 0 | **2026-08-03T14:15:00Z ~ 2026-08-03T14:54:00Z** |
| `hikaricp_connections_active` | `{__name__=hikaricp_connections_active, application=chat-service, cluster=yogurtte-k3s-prod, container=chat-service, instance=10.42.1.47:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-fdcc7c776-xf4sv, pool=HikariPool-1}` | 52 | 0 | 1 | 0 | **2026-08-03T14:43:15Z ~ 2026-08-03T14:54:00Z** |
| `hikaricp_connections_active` | `{__name__=hikaricp_connections_active, application=chat-service, cluster=yogurtte-k3s-prod, container=chat-service, instance=10.42.3.43:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-fdcc7c776-qrbc2, pool=HikariPool-1}` | 52 | 0 | 0 | 0 | **2026-08-03T14:15:00Z ~ 2026-08-03T14:32:45Z** |
| `hikaricp_connections_active` | `{__name__=hikaricp_connections_active, application=content-service, cluster=yogurtte-k3s-prod, container=content-service, instance=10.42.1.43:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-6995bb7d94-h2f6n, pool=HikariPool-1}` | 157 | 0 | 0 | 0 | **2026-08-03T14:15:00Z ~ 2026-08-03T14:54:00Z** |
| `hikaricp_connections_active` | `{__name__=hikaricp_connections_active, application=content-service, cluster=yogurtte-k3s-prod, container=content-service, instance=10.42.3.42:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-6995bb7d94-nq9l2, pool=HikariPool-1}` | 157 | 0 | 0 | 0 | **2026-08-03T14:15:00Z ~ 2026-08-03T14:54:00Z** |
| `hikaricp_connections_pending` | `{__name__=hikaricp_connections_pending, application=auth-service, cluster=yogurtte-k3s-prod, container=auth-service, instance=10.42.1.46:8090, job=auth-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=auth-service-5999bb9f5c-hmgp9, pool=HikariPool-1, service=auth-service}` | 157 | 0 | 0 | 0 | **2026-08-03T14:15:00Z ~ 2026-08-03T14:54:00Z** |
| `hikaricp_connections_pending` | `{__name__=hikaricp_connections_pending, application=chat-service, cluster=yogurtte-k3s-prod, container=chat-service, instance=10.42.1.47:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-fdcc7c776-xf4sv, pool=HikariPool-1}` | 52 | 0 | 0 | 0 | **2026-08-03T14:41:15Z ~ 2026-08-03T14:54:00Z** |
| `hikaricp_connections_pending` | `{__name__=hikaricp_connections_pending, application=chat-service, cluster=yogurtte-k3s-prod, container=chat-service, instance=10.42.3.43:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-fdcc7c776-qrbc2, pool=HikariPool-1}` | 52 | 0 | 0 | 0 | **2026-08-03T14:15:00Z ~ 2026-08-03T14:32:45Z** |
| `hikaricp_connections_pending` | `{__name__=hikaricp_connections_pending, application=content-service, cluster=yogurtte-k3s-prod, container=content-service, instance=10.42.1.43:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-6995bb7d94-h2f6n, pool=HikariPool-1}` | 157 | 0 | 0 | 0 | **2026-08-03T14:15:00Z ~ 2026-08-03T14:54:00Z** |
| `hikaricp_connections_pending` | `{__name__=hikaricp_connections_pending, application=content-service, cluster=yogurtte-k3s-prod, container=content-service, instance=10.42.3.42:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-6995bb7d94-nq9l2, pool=HikariPool-1}` | 157 | 0 | 0 | 0 | **2026-08-03T14:15:00Z ~ 2026-08-03T14:54:00Z** |
| `rate(jvm_gc_pause_seconds_sum[5m])` | `{action=end of major GC, application=chat-service, cause=Allocation Failure, cluster=yogurtte-k3s-prod, container=chat-service, gc=MarkSweepCompact, instance=10.42.1.47:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-fdcc7c776-xf4sv}` | 48 | 0 | 0 | 0 | **2026-08-03T14:42:15Z ~ 2026-08-03T14:54:00Z** |
| `rate(jvm_gc_pause_seconds_sum[5m])` | `{action=end of major GC, application=chat-service, cause=Allocation Failure, cluster=yogurtte-k3s-prod, container=chat-service, gc=MarkSweepCompact, instance=10.42.3.43:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-fdcc7c776-qrbc2}` | 72 | 0 | 0 | 0 | **2026-08-03T14:15:00Z ~ 2026-08-03T14:35:45Z** |
| `rate(jvm_gc_pause_seconds_sum[5m])` | `{action=end of minor GC, application=auth-service, cause=G1 Evacuation Pause, cluster=yogurtte-k3s-prod, container=auth-service, gc=G1 Young Generation, instance=10.42.1.46:8090, job=auth-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=auth-service-5999bb9f5c-hmgp9, service=auth-service}` | 157 | 0 | 0.001 | 0 | **2026-08-03T14:15:00Z ~ 2026-08-03T14:24:30Z, 2026-08-03T14:28:45Z ~ 2026-08-03T14:45:30Z, 2026-08-03T14:49:45Z ~ 2026-08-03T14:54:00Z** |
| `rate(jvm_gc_pause_seconds_sum[5m])` | `{action=end of minor GC, application=chat-service, cause=Allocation Failure, cluster=yogurtte-k3s-prod, container=chat-service, gc=Copy, instance=10.42.1.47:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-fdcc7c776-xf4sv}` | 48 | 0.000 | 0.002 | 0.000 | — |
| `rate(jvm_gc_pause_seconds_sum[5m])` | `{action=end of minor GC, application=chat-service, cause=Allocation Failure, cluster=yogurtte-k3s-prod, container=chat-service, gc=Copy, instance=10.42.3.43:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-fdcc7c776-qrbc2}` | 72 | 0.000 | 0.000 | 0.000 | — |
| `rate(jvm_gc_pause_seconds_sum[5m])` | `{action=end of minor GC, application=content-service, cause=Allocation Failure, cluster=yogurtte-k3s-prod, container=content-service, gc=Copy, instance=10.42.1.43:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-6995bb7d94-h2f6n}` | 157 | 0 | 0.000 | 0.000 | **2026-08-03T14:18:00Z ~ 2026-08-03T14:27:45Z, 2026-08-03T14:32:00Z ~ 2026-08-03T14:38:45Z, 2026-08-03T14:43:00Z ~ 2026-08-03T14:52:45Z** |
| `rate(jvm_gc_pause_seconds_sum[5m])` | `{action=end of minor GC, application=content-service, cause=Allocation Failure, cluster=yogurtte-k3s-prod, container=content-service, gc=Copy, instance=10.42.3.42:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-6995bb7d94-nq9l2}` | 157 | 0 | 0.000 | 0 | **2026-08-03T14:15:00Z ~ 2026-08-03T14:19:30Z, 2026-08-03T14:23:45Z ~ 2026-08-03T14:32:30Z, 2026-08-03T14:36:45Z ~ 2026-08-03T14:43:30Z, 2026-08-03T14:47:45Z ~ 2026-08-03T14:54:00Z** |
| `up` | `{__name__=up, app=node-exporter, cluster=yogurtte-k3s-prod, component=metrics, container=node-exporter, instance=ip-172-31-40-241, job=integrations/node_exporter, k8s_cluster_name=yogurtte-k3s-prod, namespace=monitoring, pod=grafana-k8s-monitoring-node-exporter-cph5l, source=kubernetes, workload=DaemonSet/grafana-k8s-monitoring-node-exporter}` | 157 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, app=node-exporter, cluster=yogurtte-k3s-prod, component=metrics, container=node-exporter, instance=ip-172-31-45-39, job=integrations/node_exporter, k8s_cluster_name=yogurtte-k3s-prod, namespace=monitoring, pod=grafana-k8s-monitoring-node-exporter-9kvqt, source=kubernetes, workload=DaemonSet/grafana-k8s-monitoring-node-exporter}` | 157 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, cluster=yogurtte-k3s-prod, container=auth-service, instance=10.42.1.46:8090, job=auth-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=auth-service-5999bb9f5c-hmgp9}` | 157 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, cluster=yogurtte-k3s-prod, container=chat-service, instance=10.42.1.47:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-fdcc7c776-xf4sv}` | 52 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, cluster=yogurtte-k3s-prod, container=chat-service, instance=10.42.3.43:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-fdcc7c776-qrbc2}` | 52 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, cluster=yogurtte-k3s-prod, container=content-service, instance=10.42.1.43:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-6995bb7d94-h2f6n}` | 157 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, cluster=yogurtte-k3s-prod, container=content-service, instance=10.42.3.42:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-6995bb7d94-nq9l2}` | 157 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, cluster=yogurtte-k3s-prod, instance=10.42.1.248:8080, job=integrations/kubernetes/kube-state-metrics, k8s_cluster_name=yogurtte-k3s-prod, source=kubernetes}` | 157 | 1 | 1 | 1 | — |
| `mongodb_up` | `{__name__=mongodb_up, cluster=yogurtte-k3s-prod, instance=infra-server, job=mongodb, k8s_cluster_name=yogurtte-k3s-prod}` | 157 | 0 | 1 | 1 | **2026-08-03T14:24:45Z ~ 2026-08-03T14:29:30Z** |
| `kafka_brokers` | `{__name__=kafka_brokers, cluster=yogurtte-k3s-prod, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod}` | 157 | 1 | 1 | 1 | — |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=chat-service-fcm-tokens, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=0, topic=user.fcm-tokens}` | 157 | 0 | 0 | 0 | **2026-08-03T14:15:00Z ~ 2026-08-03T14:54:00Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=chat-service-fcm-tokens, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=1, topic=user.fcm-tokens}` | 157 | 0 | 0 | 0 | **2026-08-03T14:15:00Z ~ 2026-08-03T14:54:00Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=chat-service-fcm-tokens, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=2, topic=user.fcm-tokens}` | 157 | 0 | 0 | 0 | **2026-08-03T14:15:00Z ~ 2026-08-03T14:54:00Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=chat-service-notification-settings, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=0, topic=user.notification-settings}` | 157 | 0 | 0 | 0 | **2026-08-03T14:15:00Z ~ 2026-08-03T14:54:00Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=chat-service-notification-settings, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=1, topic=user.notification-settings}` | 157 | 0 | 0 | 0 | **2026-08-03T14:15:00Z ~ 2026-08-03T14:54:00Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=chat-service-notification-settings, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=2, topic=user.notification-settings}` | 157 | 0 | 0 | 0 | **2026-08-03T14:15:00Z ~ 2026-08-03T14:54:00Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=db-writer, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=0, topic=chat.messages}` | 157 | 0 | 0 | 0 | **2026-08-03T14:15:00Z ~ 2026-08-03T14:54:00Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=db-writer, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=1, topic=chat.messages}` | 157 | 0 | 0 | 0 | **2026-08-03T14:15:00Z ~ 2026-08-03T14:54:00Z** |
| `websocket_active_users` | `{__name__=websocket_active_users, application=chat-service, cluster=yogurtte-k3s-prod, container=chat-service, instance=10.42.1.47:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-fdcc7c776-xf4sv}` | 52 | 0 | 0 | 0 | **2026-08-03T14:41:15Z ~ 2026-08-03T14:54:00Z** |
| `websocket_active_users` | `{__name__=websocket_active_users, application=chat-service, cluster=yogurtte-k3s-prod, container=chat-service, instance=10.42.3.43:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-fdcc7c776-qrbc2}` | 52 | 0 | 0 | 0 | **2026-08-03T14:15:00Z ~ 2026-08-03T14:32:45Z** |

값이 0이던 구간은 굵게 표시했다 — 프로세스가 사라져 시계열이 꺾인 것이 유일한 신호인 장애가 있다.

