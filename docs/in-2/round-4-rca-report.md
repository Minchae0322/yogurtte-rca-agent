# RCA Report — `scan-1785803700`

| 항목 | 값 |
|---|---|
| 모드 | rca |
| 질문 | 최근 1시간 안에 댓글 알림이 안 왔다는 제보가 있다. 원인을 조사해줘 |
| 시각 | 2026-08-04T05:57:53.247080200Z |
| provider | claude-cli |
| model | `claude-opus-5` · turns 1 |
| prompt | `.\prompts\system-prompt.md` |
| tokens | in 212029 (cacheRead 23,449 · cacheCreate 188,578) / out 6144 · cost $2.0511 |
| elapsed | total 115149ms (tempo 3676 · loki 1397 · mimir 1846 · assemble 96 · llm 99388) |

## 탐색 (Triage)

| 항목 | 값 |
|---|---|
| 시간창 해석 | 명시적 from/to |
| 시간창 확신도 | EXACT |
| 스윕 창 | 2026-08-04T00:29:18Z ~ 2026-08-04T01:29:18Z |
| 좁힌 창 | 2026-08-04T00:35:00Z ~ 2026-08-04T01:29:18Z |
| 대상 | content-service, chat-service |
| traceId | 6a713715a33b3693add43f02dd87289b |
| 트레이스 후보 | 8건 |
| 장애 후보 | 8건 · 선택 INC-1, INC-2, INC-4, INC-5, INC-6, INC-7, INC-8 |
| 계획 파싱 | 성공 |
| 스윕 컨텍스트 | 후보 + 원본 (A) |
| prompt | `.\prompts\triage-prompt.md` |
| tokens | in 55119 / out 3704 · cost $0.4210 |
| chars | 컨텍스트 45,469 + 프롬프트 1,399 = **46,868** |
| elapsed | survey 2198ms · llm 59381ms |

**선정 이유**: 댓글 등록 트레이스가 kafka up=0 구간마다 정확히 60초 타임아웃으로 실패했고 같은 시각 content-service·chat-service 양쪽 로그가 함께 튀었으므로, '댓글 → 알림 이벤트 발행 → 소비·발송' 경로 전체를 두 발생 구간 모두 열어서 봐야 한다.

**근거**

- INC-8: content-service POST /battles/{battleId}/items/{itemId}/comments 트레이스가 60,050ms에서 error 종료 (01:21:57Z, traceId 6a713eb5c566f210b35a9bd582a5f37a) — 댓글 등록 경로가 정확히 60초 타임아웃, 알림 발행 단계 블로킹 의심
- INC-5: 같은 엔드포인트가 00:49:25Z에도 60,083ms error (traceId 6a713715a33b3693add43f02dd87289b) — 동일 지문 반복, 일회성 아님
- INC-6/INC-4: kafka job up 1→0 (01:24:18Z 0, 그리고 00:49:18Z~00:54:18Z 0). 두 댓글 타임아웃 시각이 각각 이 두 kafka 단절 구간 안에 정확히 들어감
- INC-7/INC-2: content-service ERROR/WARN 405건(01:20~01:25), 301건(00:45~00:50)·154건(00:50~00:55)로 kafka 단절과 동시 급증
- INC-1: chat-service ERROR/WARN 20건(00:40~00:45)+16건(00:45~00:50) — 알림 소비·FCM 발송 측에서도 같은 시각 에러. content-service만의 문제가 아님
- kafka_consumergroup_lag(notification, notification-processors/user.notifications)이 창 전체 0으로 평탄 — kafka up=0 구간에도 0인 것은 '밀린 게 없다'가 아니라 수집이 끊겨 lag이 관측되지 않았을 가능성. 실제 컨슈머 오프셋 확인 필요
- websocket_active_users가 00:29~01:29 전 구간 0 — 실시간 알림 채널에 접속 사용자가 한 명도 잡히지 않음(없는 값 자체가 신호)
- chat.messages / user.notifications의 일부 파티션 lag이 -1로 고정(partition 2,6,9,10 등) — 커밋 오프셋 없음/컨슈머 미할당 상태 확인 필요
- Tempo 지연 검색(duration>3s && status!=error) 0건 — 에러 없이 느려진 요청은 없었다. 즉 증상은 '지연'이 아니라 '실패·미발행'
- kafka_brokers는 창 전체 1로 유지 — 브로커 프로세스 자체보다 exporter/네트워크 단절 또는 짧은 재기동일 가능성. up=0과 brokers=1의 불일치를 먼저 갈라야 함

**스윕이 찾은 트레이스** (고른 것은 6a713715a33b3693add43f02dd87289b)

| traceId | 채널 | root service | root span | ms |
|---|---|---|---|---:|
| `6a713eb5c566f210b35a9bd582a5f37a` | error | content-service | http post /battles/{battleId}/items/{itemId}/comments | 60050 |
| `6a713715a33b3693add43f02dd87289b` ←선택 | error | content-service | http post /battles/{battleId}/items/{itemId}/comments | 60083 |
| `6a7136481fff5bf1773db56f29e4edcf` | error | content-service | task battle-deadline-notification-scheduler.notify | 2005 |
| `6a713648097813e37cf6b58215bd213c` | error | content-service | task battle-deadline-notification-scheduler.notify | 2001 |
| `6a71360ca3d494a88bc240b9f9408f8b` | error | content-service | task battle-deadline-notification-scheduler.notify | 2010 |
| `6a71360cf2f1f148dcea84d04579dc26` | error | content-service | task battle-deadline-notification-scheduler.notify | 2006 |
| `6a7135d0c0f0750ba06ca76935b8c95d` | error | content-service | task battle-deadline-notification-scheduler.notify | 2004 |
| `6a7135d008742cc31fa65e33ae09e2c9` | error | content-service | task battle-deadline-notification-scheduler.notify | 2018 |

**장애 후보** (코드가 신호를 묶은 것 · 창은 여기서 계산됨)

## INC-1  chat-service  |  ERROR/WARN
- 구간: 2026-08-04T00:40:00Z ~ 2026-08-04T00:50:00Z  (LOKI · 집계 해상도만큼 흐림)
- ERROR/WARN 20건 (2026-08-04T00:40:00Z ~ 2026-08-04T00:45:00Z)
- ERROR/WARN 16건 (2026-08-04T00:45:00Z ~ 2026-08-04T00:50:00Z)
- 같은 시각의 다른 후보: INC-2, INC-3, INC-4, INC-5  (인과 여부는 판단하지 않았다)

## INC-2  content-service  |  ERROR/WARN
- 구간: 2026-08-04T00:40:00Z ~ 2026-08-04T00:55:00Z  (LOKI · 집계 해상도만큼 흐림)
- ERROR/WARN 42건 (2026-08-04T00:40:00Z ~ 2026-08-04T00:45:00Z)
- ERROR/WARN 301건 (2026-08-04T00:45:00Z ~ 2026-08-04T00:50:00Z)
- ERROR/WARN 154건 (2026-08-04T00:50:00Z ~ 2026-08-04T00:55:00Z)
- 같은 시각의 다른 후보: INC-1, INC-3, INC-4, INC-5  (인과 여부는 판단하지 않았다)

## INC-3  content-service  |  task battle-deadline-notification-scheduler.notify
- 구간: 2026-08-04T00:44:00.022091Z ~ 2026-08-04T00:46:02.020030Z  (TEMPO · 시각 정확)
- content-service task battle-deadline-notification-scheduler.notify 2,018ms (error 채널)
- content-service task battle-deadline-notification-scheduler.notify 2,004ms (error 채널)
- content-service task battle-deadline-notification-scheduler.notify 2,006ms (error 채널)
- content-service task battle-deadline-notification-scheduler.notify 2,010ms (error 채널)
- content-service task battle-deadline-notification-scheduler.notify 2,001ms (error 채널)
- content-service task battle-deadline-notification-scheduler.notify 2,005ms (error 채널)
- traceId: 6a7135d008742cc31fa65e33ae09e2c9, 6a7135d0c0f0750ba06ca76935b8c95d, 6a71360cf2f1f148dcea84d04579dc26, 6a71360ca3d494a88bc240b9f9408f8b, 6a713648097813e37cf6b58215bd213c, 6a7136481fff5bf1773db56f29e4edcf
- 같은 시각의 다른 후보: INC-1, INC-2, INC-4  (인과 여부는 판단하지 않았다)

## INC-4  kafka  |  up
- 구간: 2026-08-04T00:44:18Z ~ 2026-08-04T00:59:18Z  (MIMIR · 집계 해상도만큼 흐림)
- up 1 → 0
- up 가 0이었다 (2026-08-04T00:49:18Z ~ 2026-08-04T00:54:18Z)
- up 0 → 1
- 같은 시각의 다른 후보: INC-1, INC-2, INC-3, INC-5  (인과 여부는 판단하지 않았다)

## INC-5  content-service  |  http post /battles/{battleId}/items/{itemId}/comments
- 구간: 2026-08-04T00:49:25.706497Z ~ 2026-08-04T00:50:25.789497Z  (TEMPO · 시각 정확)
- content-service http post /battles/{battleId}/items/{itemId}/comments 60,083ms (error 채널)
- traceId: 6a713715a33b3693add43f02dd87289b
- 같은 시각의 다른 후보: INC-1, INC-2, INC-4  (인과 여부는 판단하지 않았다)

## INC-6  kafka  |  up
- 구간: 2026-08-04T01:19:18Z ~ 2026-08-04T01:29:18Z  (MIMIR · 집계 해상도만큼 흐림)
- up 1 → 0
- up 가 0이었다 (2026-08-04T01:24:18Z ~ 2026-08-04T01:24:18Z)
- up 0 → 1
- 같은 시각의 다른 후보: INC-7, INC-8  (인과 여부는 판단하지 않았다)

## INC-7  content-service  |  ERROR/WARN
- 구간: 2026-08-04T01:20:00Z ~ 2026-08-04T01:25:00Z  (LOKI · 집계 해상도만큼 흐림)
- ERROR/WARN 405건 (2026-08-04T01:20:00Z ~ 2026-08-04T01:25:00Z)
- 같은 시각의 다른 후보: INC-6, INC-8  (인과 여부는 판단하지 않았다)

## INC-8  content-service  |  http post /battles/{battleId}/items/{itemId}/comments
- 구간: 2026-08-04T01:21:57.004052Z ~ 2026-08-04T01:22:57.054052Z  (TEMPO · 시각 정확)
- content-service http post /battles/{battleId}/items/{itemId}/comments 60,050ms (error 채널)
- traceId: 6a713eb5c566f210b35a9bd582a5f37a
- 같은 시각의 다른 후보: INC-6, INC-7  (인과 여부는 판단하지 않았다)

**기각한 후보**

- INC-3 — battle-deadline-notification-scheduler.notify 2초 타임아웃은 댓글 알림이 아니라 마감 알림 스케줄러라 제보 증상과 직접 일치하지 않는다 — 다만 00:44~00:46Z에 6회 모두 정확히 2,001~2,018ms에서 끊긴 것은 Kafka 프로듀서 블로킹(max.block.ms 2s) 정황이므로 INC-4 조사 시 근인 방증으로 함께 참고할 것

**보정 기록**

- 창을 후보 [INC-1, INC-2, INC-4, INC-5, INC-6, INC-7, INC-8] 의 신호 시각에서 계산했다 (2026-08-04T00:35:00Z ~ 2026-08-04T01:29:18Z)

**스윕 무신호/실패**

- Tempo 지연 검색 '{ duration > 3s && status != error }'이 이 창에서 0건이다. 임계값보다 느린 요청이 없었다는 뜻이다.

## 수집 범위 (Coverage)

- **window**: 2026-08-04T00:35:00Z ~ 2026-08-04T01:29:18Z (3258s)
- **trace**: 41,206B / 41 spans
- **창 안 후보 트레이스**: 10건 / 41,206B
- **logs**: errwarn=291,409B · traceId=8,543B
- **metrics**: 8 수집 / 315,882B, 누락 [sum(rate(http_server_requests_seconds_count{application="content-service", status="401"}[1m]))]
- **context**: 374,128 chars (+ 시스템 프롬프트 853 chars)

### 토큰 축 (개선 지표)

| 단계 | 총 in | chars | 컨텍스트 토큰 |
|---|---:|---:|---:|
| 탐색 | 55,119 | 46,868 | 25,693 |
| 분석 | 212,029 | 374,981 | 182,603 |
| **합계** | **267,148** | | **208,296** |

- **overheadTokens 29,426 tok — 이 회차에 실측**(1자 프롬프트 프로브, 같은 명령·모델·샌드박스).
  호출마다 한 번씩 얹히므로 단계별로 뺀다. **다른 날 상수를 쓰지 않았다 = `█ 실측`.**
- contextTokens (count_tokens API): 측정 안 됨 — 구독 CLI 경로엔 API 키가 없다
- 비용은 **API 환산 추정치**다. 구독 계정으로 호출하므로 실제 청구액이 아니다.

## 수집 실패/누락

- Metric 'sum(rate(http_server_requests_seconds_count{application="content-service", status="401"}[1m]))' returned no series in this window; skipped.

---

## 1. 원인 후보 랭킹 (최대 3개)

1. **Kafka `user.notifications` 토픽 부재 + 자동 생성 실패(INVALID_REPLICATION_FACTOR)** — content-service의 댓글 알림 publish가 60초 블로킹 후 만료·실패
2. **infra-server(172.31.46.124) Kafka 브로커 단절 구간(00:48:00~00:51:15Z, 01:20:30~01:23:45Z)** — 1의 선행 트리거이자, 그 구간 send 자체가 불가
3. **동일 호스트 Redis 단절(00:43~00:45Z)로 인한 스케줄러 알림 작업 실패** — 댓글 알림이 아닌 "배틀 마감 알림" 경로에 한정

---

## 2. 후보별 근거

### 후보 1 — `user.notifications` 토픽 부재 / 복제팩터 불일치

- **근거**
  - `content-service-6995bb7d94-nq9l2`, 10:23:39.590 KST(=01:23:39Z): `Error while fetching metadata with correlation id 484 : {user.notifications=INVALID_REPLICATION_FACTOR}`
  - `content-service-6995bb7d94-h2f6n`, 10:23:39.576: `... correlation id 480 : {user.notifications=UNKNOWN_TOPIC_OR_PARTITION}`
  - `kafka_brokers` = 전 구간 **1** (단일 브로커). 복제팩터 ≥2 요구 시 자동 생성이 구조적으로 실패.
  - producer span `publish user.notifications` (SPAN_KIND_PRODUCER) 2건 모두 `error: Topic user.notifications not present in metadata after 60000 ms.`
  - `kafka_consumergroup_lag{topic="user.notifications"}` 파티션 0~5 전 구간 **0** → 컨슈머 적체가 아니라 **메시지가 아예 생산되지 않음**.
- **대기·지연 판정**
  - trace `6a713715...`: `publish` span 00:49:25.769Z→00:50:25.783Z = **60013.4ms**, 상한 `max.block.ms` **60000ms** → **만료**. 부모 `notification-publish` span `STATUS_CODE_ERROR`, `error=Send failed`.
  - trace `6a713eb5...`: 01:21:57.050Z→01:22:57.051Z = **60001.2ms** → **만료**.
  - **최종 상태: 실패 후 폐기.** 근거 — `NotificationEventListener`가 `[notify] 알림 발행 실패: userId=7, type=BATTLE_ITEM_COMMENT, error=Send failed`를 찍고 종료(`KafkaNotificationProducer.send:27` → `onNotification:44`, 재시도 프레임 없음). 동일 traceId로 재발행 span·로그 없음. `user.notifications.dlq` 컨슈머 랙도 전 구간 0(파티션 1은 -1=미할당)이라 DLQ 적재 흔적 없음. 단, DLQ 토픽 오프셋 자체는 조회하지 않았으므로 "DLQ 미기록"은 **간접 근거**.
  - 반면 댓글 본문은 **커밋 성공**: `jdbc.row-affected=1` (`insert into tb_battle_item_comment`), connection span에 `commit` 이벤트, HTTP `outcome=SUCCESS status=200`, 로그 `댓글 작성 완료 - commentId: 254 / 256`. → **댓글은 저장, 알림만 유실**. 제보와 정확히 일치.
- **확신도: 높음**
- **반증 데이터**: 없음.

### 후보 2 — Kafka 브로커 단절

- **근거**
  - `up{job="kafka", instance="infra-server"}` 값 0: **00:48:00~00:51:15Z**, **01:20:30~01:23:45Z**. `kafka_brokers` 결측 구간도 동일(00:47:45~00:51:30, 01:20:15~01:24:00).
  - producer WARN이 10:20:25.746 KST부터 10:23:39까지 **초당 1회** 연속: `Connection to node 1 (/172.31.46.124:9092) could not be established. Node may not be available.`
  - 실패한 댓글 2건의 publish 시작 시각(00:49:25Z, 01:21:57Z)이 **두 단절 구간 안에 각각 정확히 포함**됨.
  - 브로커 복귀 직후(01:23:39Z) 나온 메타데이터 응답이 후보 1의 `UNKNOWN_TOPIC_OR_PARTITION` / `INVALID_REPLICATION_FACTOR` → 재기동으로 토픽 메타데이터가 사라졌고 재생성도 막힌 정황.
- **대기·지연 판정**: 위 60초 블로킹과 동일 사건. 상한 60000ms 대비 만료, 재시도 없음, 폐기.
- **확신도: 중간** (브로커 다운 사실은 확실. 다운의 원인 — 재기동/OOM/디스크 — 은 관측 데이터에 없어 **데이터 부족**)
- **반증 데이터**: `up{job=redis}`·`up{job=node-infra}`·`up{job=mongodb}`는 infra-server에서 전 구간 1 → 호스트 전체 다운은 아님(프로세스 단위 장애 시사). 단 Redis는 아래처럼 앱 관점에서는 거부됨.

### 후보 3 — Redis 단절로 인한 마감 알림 스케줄러 실패

- **근거**
  - content/chat 양쪽 WARN: `Cannot reconnect to [172.31.46.124/<unresolved>:6379]: finishConnect(..) failed: Connection refused` (00:43:30Z~00:44:34Z 다수).
  - trace 5건 `task battle-deadline-notification-scheduler.notify`: `exception=QueryTimeoutException`, `error=Redis command timed out`, `outcome=ERROR`, `code.function=notifyEnd`, 00:44:00Z / 00:45:00Z / 00:46:00Z 실행분, 두 파드 모두.
  - 스택: `RedisLockProvider.lock:109` → ShedLock 잠금 획득 자체가 실패 → 작업 본문 미실행.
- **대기·지연 판정**
  - 실측 대기 **2004~2018ms** (예: 00:44:00.015Z→00:44:02.020Z), 상한 Lettuce `Command timed out after 2 second(s)` → **만료**.
  - **최종 상태: 실패 후 폐기(해당 회차 스킵).** `TaskUtils$LoggingErrorHandler - Unexpected error occurred in scheduled task`로 로깅만 되고, 다음 60초 주기에 새로 실행됨(= 그 회차 재시도 아님).
- **확신도: 중간** — 사실관계는 확실하나 **제보(댓글 알림)와는 다른 알림 경로**. 댓글 알림 경로는 `NotificationEventListener`→Kafka이며 Redis를 거치지 않음(호출 그래프상 content→redis는 `GET` 2회, user-cache HIT용).
- **반증 데이터**: `up{job=redis}`가 전 구간 **1**(exporter 관점 정상) — 앱의 connection refused와 배치됨. 또한 댓글 트레이스의 Redis `GET` span은 0.6ms로 정상 성공, 로그도 `[user-cache] 캐시 HIT - userId: 1, elapsed: 2ms`. → 댓글 경로의 Redis는 멀쩡했음.

### 배제된 후보

- **DB / 커넥션 풀 고갈**: `hikaricp_connections_active`·`pending` 전 파드 전 구간 0, JDBC span 최대 52.3ms, `commit` 정상, `row-affected=1`. 반증됨.
- **컨슈머(알림 처리기) 지연·정지**: `notification-processors` 그룹 `user.notifications` 파티션 0~5 랙 전 구간 0. 처리 지연이 아니라 미생산. 반증됨.
- **애플리케이션 다운 / GC**: `up{content,chat,auth}` 전 구간 1, GC pause rate 최대 1.1e-3 s/s 수준. 반증됨.
- **인증(401) 문제**: 메트릭 `http_server_requests_seconds_count{status="401"}` 시리즈 미수집(수집 실패). 다만 댓글 요청은 200 SUCCESS·`userId=1`로 인증 통과가 확인되므로 이 경로의 원인은 아님. **판정 근거 부족 항목으로만 남김.**

---

## 3. 권장 다음 조치

### 이미 발생한 피해 — 복구 가능

- **가능.** 댓글 본문은 MySQL에 커밋 완료(`commentId 254`, `256`, battleId 22 / itemId 125). 알림 페이로드는 DB의 댓글 레코드로 재구성 가능.
- 방법: ① 토픽 정상화 후 ② `tb_battle_item_comment`에서 장애 창(2026-08-04T00:35:00Z~01:29:18Z) `created_at` 범위 댓글을 조회해 `KafkaNotificationDto(userId, type=BATTLE_ITEM_COMMENT, ...)`를 재발행. 소비자 측 멱등 처리가 없다면 대상 userId를 수동 확인 후 발행할 것.
- **Kafka에서의 복구는 불가**: 메시지가 브로커에 도달한 적이 없고 DLQ 적재 흔적도 없음. 재생 소스는 DB뿐.
- 마감 알림(`notifyEnd`) 00:44~00:46Z 3회분은 **폐기됨**. 재발행 여부는 해당 배틀의 마감 시각 확인 후 판단 — 관측 데이터에 대상 batchId가 없어 **데이터 부족**.

### 재발 방지

1. `user.notifications` 토픽을 **replication.factor=1**로 명시 생성(브로커 1대). 자동 생성 의존을 끊고 선언적으로 관리 — `INVALID_REPLICATION_FACTOR`의 직접 원인.
2. 브로커 1대 구성이 전제라면 `min.insync.replicas=1` 확인, 아니면 브로커 증설. 현 구성은 단일 실패점.
3. `KafkaNotificationProducer.send`에 **로컬 아웃박스 또는 DLQ 폴백** 추가. 현재는 60초 블로킹 후 로그만 남기고 유실.
4. `max.block.ms` 60초는 알림 스레드 풀을 60초 점유함 → 5~10초로 축소 + 비동기 재시도.
5. 알람 추가: `up{job="kafka"} == 0` 1분 지속, `UNKNOWN_TOPIC_OR_PARTITION|INVALID_REPLICATION_FACTOR` 로그 패턴, `notification-publish` span error율.
6. Redis: `up{job=redis}`가 1인데 앱은 connection refused였음 → 익스포터가 실제 클라이언트 연결성을 반영하지 못함. 헬스체크 지표를 앱 관점(`RedisReactiveHealthIndicator`)으로 승격.

### 복구 확인

1. `kafka-topics --describe --topic user.notifications` → 파티션 6개(랙 지표상 partition 0~5), ReplicationFactor 1 확인.
2. content-service 로그에서 `Connection to node 1 ... could not be established` 및 `Error while fetching metadata` 소멸 확인.
3. 테스트 댓글 1건 작성 → trace에서 `publish user.notifications` span이 **에러 없이 수 ms 내 종료**, `notification-publish` span status OK 확인.
4. `kafka_consumergroup_lag{consumergroup="notification-processors", topic="user.notifications"}`가 0에서 **증가 후 다시 0으로 소진**되는지 확인(0 고정이면 여전히 미생산).
5. `up{job="kafka"}` 30분 연속 1 유지.

> 결론의 확신도 조정: 401 메트릭 미수집은 이 경로와 무관해 영향 없음. 다만 DLQ 오프셋·브로커 다운 원인(로그/이벤트)을 조회하지 못해, "폐기 확정"과 "브로커 다운 원인"은 각각 중간 확신도로 남긴다.

---

## 관측 증거 (Evidence)

> 이 조사가 실제로 본 값이다. 원본 응답 전체는 `reports/raw/scan-1785803700-*.json`에 있다.

### 호출 그래프 (트레이스에서 추출)

```
content-service --db--> redis  2회  최대 0.6ms  [GET]
content-service --jdbc--> mysql/content (HikariPool-1)  18회  최대 52.3ms
    events: acquired, commit
content-service --messaging--> kafka/user.notifications  2회  최대 60013.4ms  [publish]
    error: Topic user.notifications not present in metadata after 60000 ms.
```

### span (duration 상위 15 / 전체 41)

| ms | service | span | 시작 |
|---:|---|---|---|
| 60020.64 | content-service | `notification-publish` | 2026-08-04T00:49:25.769760Z |
| 60013.38 | content-service | `publish user.notifications` | 2026-08-04T00:49:25.769909Z |
| 60004.60 | content-service | `notification-publish` | 2026-08-04T01:21:57.050238Z |
| 60001.22 | content-service | `publish user.notifications` | 2026-08-04T01:21:57.050321Z |
| 2018.74 | content-service | `task battle-deadline-notification-scheduler.notify` | 2026-08-04T00:44:00.022091Z |
| 2010.38 | content-service | `task battle-deadline-notification-scheduler.notify` | 2026-08-04T00:45:00.018556Z |
| 2006.51 | content-service | `task battle-deadline-notification-scheduler.notify` | 2026-08-04T00:45:00.000310Z |
| 2005.13 | content-service | `task battle-deadline-notification-scheduler.notify` | 2026-08-04T00:46:00.015030Z |
| 2004.52 | content-service | `task battle-deadline-notification-scheduler.notify` | 2026-08-04T00:44:00.026130Z |
| 2001.01 | content-service | `task battle-deadline-notification-scheduler.notify` | 2026-08-04T00:46:00.014821Z |
| 63.94 | content-service | `http post /battles/{battleId}/items/{itemId}/comments` | 2026-08-04T00:49:25.706497Z |
| 62.57 | content-service | `secured request` | 2026-08-04T00:49:25.707025Z |
| 52.29 | content-service | `connection` | 2026-08-04T00:49:25.717189Z |
| 47.75 | content-service | `http post /battles/{battleId}/items/{itemId}/comments` | 2026-08-04T01:21:57.004052Z |
| 46.32 | content-service | `secured request` | 2026-08-04T01:21:57.004489Z |

### 로그 원문 (60 / 전체 1,440줄)

전체가 상한을 넘어 ERROR/WARN 줄을 우선 발췌했다. 나머지는 원본 파일에 있다.

```
2026-08-04T00:43:36.420201444Z  [chat-service]  org.springframework.dao.QueryTimeoutException: Redis command timed out
2026-08-04T00:43:36.420204418Z  [chat-service]  at org.springframework.data.redis.connection.lettuce.LettuceExceptionConverter.convert(LettuceExceptionConverter.java:68) ~[spring-data-redis-3.5.1.jar!/:3.5.1]
2026-08-04T00:43:36.420222675Z  [chat-service]  at org.springframework.data.redis.connection.lettuce.LettuceReactiveRedisConnection.lambda$translateException$0(LettuceReactiveRedisConnection.java:242) ~[spring-data-redis-3.5.1.jar!/:3.5.1]
2026-08-04T00:43:36.420254322Z  [chat-service]  at io.lettuce.core.protocol.CommandWrapper.completeExceptionally(CommandWrapper.java:132) ~[lettuce-core-6.6.0.RELEASE.jar!/:6.6.0.RELEASE/643bd47]
2026-08-04T00:43:36.420277895Z  [chat-service]  Caused by: io.lettuce.core.RedisCommandTimeoutException: INFO. Command timed out after 2 second(s)
2026-08-04T00:43:36.420280076Z  [chat-service]  at io.lettuce.core.internal.ExceptionFactory.createTimeoutException(ExceptionFactory.java:75) ~[lettuce-core-6.6.0.RELEASE.jar!/:6.6.0.RELEASE/643bd47]
2026-08-04T00:43:46.321152863Z  [chat-service]  org.springframework.dao.QueryTimeoutException: Redis command timed out
2026-08-04T00:43:46.321157053Z  [chat-service]  at org.springframework.data.redis.connection.lettuce.LettuceExceptionConverter.convert(LettuceExceptionConverter.java:68) ~[spring-data-redis-3.5.1.jar!/:3.5.1]
2026-08-04T00:43:46.321160664Z  [chat-service]  at org.springframework.data.redis.connection.lettuce.LettuceReactiveRedisConnection.lambda$translateException$0(LettuceReactiveRedisConnection.java:242) ~[spring-data-redis-3.5.1.jar!/:3.5.1]
2026-08-04T00:43:46.321218084Z  [chat-service]  at io.lettuce.core.protocol.CommandWrapper.completeExceptionally(CommandWrapper.java:132) ~[lettuce-core-6.6.0.RELEASE.jar!/:6.6.0.RELEASE/643bd47]
2026-08-04T00:43:46.321242133Z  [chat-service]  Caused by: io.lettuce.core.RedisCommandTimeoutException: INFO. Command timed out after 2 second(s)
2026-08-04T00:43:46.321246085Z  [chat-service]  at io.lettuce.core.internal.ExceptionFactory.createTimeoutException(ExceptionFactory.java:75) ~[lettuce-core-6.6.0.RELEASE.jar!/:6.6.0.RELEASE/643bd47]
2026-08-04T00:43:56.315756957Z  [chat-service]  org.springframework.dao.QueryTimeoutException: Redis command timed out
2026-08-04T00:43:56.315762407Z  [chat-service]  at org.springframework.data.redis.connection.lettuce.LettuceExceptionConverter.convert(LettuceExceptionConverter.java:68) ~[spring-data-redis-3.5.1.jar!/:3.5.1]
2026-08-04T00:43:56.315766702Z  [chat-service]  at org.springframework.data.redis.connection.lettuce.LettuceReactiveRedisConnection.lambda$translateException$0(LettuceReactiveRedisConnection.java:242) ~[spring-data-redis-3.5.1.jar!/:3.5.1]
2026-08-04T00:43:56.315804021Z  [chat-service]  at io.lettuce.core.protocol.CommandWrapper.completeExceptionally(CommandWrapper.java:132) ~[lettuce-core-6.6.0.RELEASE.jar!/:6.6.0.RELEASE/643bd47]
2026-08-04T00:43:56.315833215Z  [chat-service]  Caused by: io.lettuce.core.RedisCommandTimeoutException: INFO. Command timed out after 2 second(s)
2026-08-04T00:43:56.315835919Z  [chat-service]  at io.lettuce.core.internal.ExceptionFactory.createTimeoutException(ExceptionFactory.java:75) ~[lettuce-core-6.6.0.RELEASE.jar!/:6.6.0.RELEASE/643bd47]
2026-08-04T00:43:58.415727963Z  [chat-service]  org.springframework.dao.QueryTimeoutException: Redis command timed out
2026-08-04T00:43:58.415731121Z  [chat-service]  at org.springframework.data.redis.connection.lettuce.LettuceExceptionConverter.convert(LettuceExceptionConverter.java:68) ~[spring-data-redis-3.5.1.jar!/:3.5.1]
2026-08-04T00:43:58.415734268Z  [chat-service]  at org.springframework.data.redis.connection.lettuce.LettuceReactiveRedisConnection.lambda$translateException$0(LettuceReactiveRedisConnection.java:242) ~[spring-data-redis-3.5.1.jar!/:3.5.1]
2026-08-04T00:43:58.415766264Z  [chat-service]  at io.lettuce.core.protocol.CommandWrapper.completeExceptionally(CommandWrapper.java:132) ~[lettuce-core-6.6.0.RELEASE.jar!/:6.6.0.RELEASE/643bd47]
2026-08-04T00:43:58.415809673Z  [chat-service]  Caused by: io.lettuce.core.RedisCommandTimeoutException: INFO. Command timed out after 2 second(s)
2026-08-04T00:43:58.415811976Z  [chat-service]  at io.lettuce.core.internal.ExceptionFactory.createTimeoutException(ExceptionFactory.java:75) ~[lettuce-core-6.6.0.RELEASE.jar!/:6.6.0.RELEASE/643bd47]
2026-08-04T00:44:08.414659625Z  [chat-service]  org.springframework.dao.QueryTimeoutException: Redis command timed out
2026-08-04T00:44:08.414663085Z  [chat-service]  at org.springframework.data.redis.connection.lettuce.LettuceExceptionConverter.convert(LettuceExceptionConverter.java:68) ~[spring-data-redis-3.5.1.jar!/:3.5.1]
2026-08-04T00:44:08.414666098Z  [chat-service]  at org.springframework.data.redis.connection.lettuce.LettuceReactiveRedisConnection.lambda$translateException$0(LettuceReactiveRedisConnection.java:242) ~[spring-data-redis-3.5.1.jar!/:3.5.1]
2026-08-04T00:44:08.414713826Z  [chat-service]  at io.lettuce.core.protocol.CommandWrapper.completeExceptionally(CommandWrapper.java:132) ~[lettuce-core-6.6.0.RELEASE.jar!/:6.6.0.RELEASE/643bd47]
2026-08-04T00:44:08.414736999Z  [chat-service]  Caused by: io.lettuce.core.RedisCommandTimeoutException: INFO. Command timed out after 2 second(s)
2026-08-04T00:44:08.414739265Z  [chat-service]  at io.lettuce.core.internal.ExceptionFactory.createTimeoutException(ExceptionFactory.java:75) ~[lettuce-core-6.6.0.RELEASE.jar!/:6.6.0.RELEASE/643bd47]
2026-08-04T00:44:18.414857584Z  [chat-service]  org.springframework.dao.QueryTimeoutException: Redis command timed out
2026-08-04T00:44:18.414860620Z  [chat-service]  at org.springframework.data.redis.connection.lettuce.LettuceExceptionConverter.convert(LettuceExceptionConverter.java:68) ~[spring-data-redis-3.5.1.jar!/:3.5.1]
2026-08-04T00:44:18.414864040Z  [chat-service]  at org.springframework.data.redis.connection.lettuce.LettuceReactiveRedisConnection.lambda$translateException$0(LettuceReactiveRedisConnection.java:242) ~[spring-data-redis-3.5.1.jar!/:3.5.1]
2026-08-04T00:44:18.414912930Z  [chat-service]  at io.lettuce.core.protocol.CommandWrapper.completeExceptionally(CommandWrapper.java:132) ~[lettuce-core-6.6.0.RELEASE.jar!/:6.6.0.RELEASE/643bd47]
2026-08-04T00:44:18.414935249Z  [chat-service]  Caused by: io.lettuce.core.RedisCommandTimeoutException: INFO. Command timed out after 2 second(s)
2026-08-04T00:44:18.414937289Z  [chat-service]  at io.lettuce.core.internal.ExceptionFactory.createTimeoutException(ExceptionFactory.java:75) ~[lettuce-core-6.6.0.RELEASE.jar!/:6.6.0.RELEASE/643bd47]
2026-08-04T00:44:28.416030080Z  [chat-service]  org.springframework.dao.QueryTimeoutException: Redis command timed out
2026-08-04T00:44:28.416033184Z  [chat-service]  at org.springframework.data.redis.connection.lettuce.LettuceExceptionConverter.convert(LettuceExceptionConverter.java:68) ~[spring-data-redis-3.5.1.jar!/:3.5.1]
2026-08-04T00:44:28.416054924Z  [chat-service]  at org.springframework.data.redis.connection.lettuce.LettuceReactiveRedisConnection.lambda$translateException$0(LettuceReactiveRedisConnection.java:242) ~[spring-data-redis-3.5.1.jar!/:3.5.1]
2026-08-04T00:44:28.416085777Z  [chat-service]  at io.lettuce.core.protocol.CommandWrapper.completeExceptionally(CommandWrapper.java:132) ~[lettuce-core-6.6.0.RELEASE.jar!/:6.6.0.RELEASE/643bd47]
2026-08-04T00:44:28.416108255Z  [chat-service]  Caused by: io.lettuce.core.RedisCommandTimeoutException: INFO. Command timed out after 2 second(s)
2026-08-04T00:44:28.416110283Z  [chat-service]  at io.lettuce.core.internal.ExceptionFactory.createTimeoutException(ExceptionFactory.java:75) ~[lettuce-core-6.6.0.RELEASE.jar!/:6.6.0.RELEASE/643bd47]
2026-08-04T00:44:38.414831889Z  [chat-service]  org.springframework.dao.QueryTimeoutException: Redis command timed out
2026-08-04T00:44:38.414834591Z  [chat-service]  at org.springframework.data.redis.connection.lettuce.LettuceExceptionConverter.convert(LettuceExceptionConverter.java:68) ~[spring-data-redis-3.5.1.jar!/:3.5.1]
2026-08-04T00:44:38.414839133Z  [chat-service]  at org.springframework.data.redis.connection.lettuce.LettuceReactiveRedisConnection.lambda$translateException$0(LettuceReactiveRedisConnection.java:242) ~[spring-data-redis-3.5.1.jar!/:3.5.1]
2026-08-04T00:44:38.414889323Z  [chat-service]  at io.lettuce.core.protocol.CommandWrapper.completeExceptionally(CommandWrapper.java:132) ~[lettuce-core-6.6.0.RELEASE.jar!/:6.6.0.RELEASE/643bd47]
2026-08-04T00:44:38.414911178Z  [chat-service]  Caused by: io.lettuce.core.RedisCommandTimeoutException: INFO. Command timed out after 2 second(s)
2026-08-04T00:44:38.414913037Z  [chat-service]  at io.lettuce.core.internal.ExceptionFactory.createTimeoutException(ExceptionFactory.java:75) ~[lettuce-core-6.6.0.RELEASE.jar!/:6.6.0.RELEASE/643bd47]
2026-08-04T00:44:48.415099860Z  [chat-service]  org.springframework.dao.QueryTimeoutException: Redis command timed out
2026-08-04T00:44:48.415103530Z  [chat-service]  at org.springframework.data.redis.connection.lettuce.LettuceExceptionConverter.convert(LettuceExceptionConverter.java:68) ~[spring-data-redis-3.5.1.jar!/:3.5.1]
2026-08-04T00:44:48.415107097Z  [chat-service]  at org.springframework.data.redis.connection.lettuce.LettuceReactiveRedisConnection.lambda$translateException$0(LettuceReactiveRedisConnection.java:242) ~[spring-data-redis-3.5.1.jar!/:3.5.1]
2026-08-04T00:44:48.415305550Z  [chat-service]  at io.lettuce.core.protocol.CommandWrapper.completeExceptionally(CommandWrapper.java:132) ~[lettuce-core-6.6.0.RELEASE.jar!/:6.6.0.RELEASE/643bd47]
2026-08-04T00:44:48.415334037Z  [chat-service]  Caused by: io.lettuce.core.RedisCommandTimeoutException: INFO. Command timed out after 2 second(s)
2026-08-04T00:44:48.415336695Z  [chat-service]  at io.lettuce.core.internal.ExceptionFactory.createTimeoutException(ExceptionFactory.java:75) ~[lettuce-core-6.6.0.RELEASE.jar!/:6.6.0.RELEASE/643bd47]
2026-08-04T00:44:58.415591077Z  [chat-service]  org.springframework.dao.QueryTimeoutException: Redis command timed out
2026-08-04T00:44:58.415595693Z  [chat-service]  at org.springframework.data.redis.connection.lettuce.LettuceExceptionConverter.convert(LettuceExceptionConverter.java:68) ~[spring-data-redis-3.5.1.jar!/:3.5.1]
2026-08-04T00:44:58.415600405Z  [chat-service]  at org.springframework.data.redis.connection.lettuce.LettuceReactiveRedisConnection.lambda$translateException$0(LettuceReactiveRedisConnection.java:242) ~[spring-data-redis-3.5.1.jar!/:3.5.1]
2026-08-04T00:44:58.415935403Z  [chat-service]  at io.lettuce.core.protocol.CommandWrapper.completeExceptionally(CommandWrapper.java:132) ~[lettuce-core-6.6.0.RELEASE.jar!/:6.6.0.RELEASE/643bd47]
2026-08-04T00:44:58.415962655Z  [chat-service]  Caused by: io.lettuce.core.RedisCommandTimeoutException: INFO. Command timed out after 2 second(s)
2026-08-04T00:44:58.415970763Z  [chat-service]  at io.lettuce.core.internal.ExceptionFactory.createTimeoutException(ExceptionFactory.java:75) ~[lettuce-core-6.6.0.RELEASE.jar!/:6.6.0.RELEASE/643bd47]
```

### 메트릭 시계열

| 쿼리 | series | 점 | min | max | last | 값이 0이던 구간 |
|---|---|---:|---:|---:|---:|---|
| `hikaricp_connections_active` | `{__name__=hikaricp_connections_active, application=auth-service, cluster=yogurtte-k3s-prod, container=auth-service, instance=10.42.1.46:8090, job=auth-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=auth-service-5999bb9f5c-hmgp9, pool=HikariPool-1, service=auth-service}` | 218 | 0 | 0 | 0 | **2026-08-04T00:35:00Z ~ 2026-08-04T01:29:15Z** |
| `hikaricp_connections_active` | `{__name__=hikaricp_connections_active, application=chat-service, cluster=yogurtte-k3s-prod, container=chat-service, instance=10.42.1.47:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-fdcc7c776-xf4sv, pool=HikariPool-1}` | 206 | 0 | 0 | 0 | **2026-08-04T00:35:00Z ~ 2026-08-04T01:29:15Z** |
| `hikaricp_connections_active` | `{__name__=hikaricp_connections_active, application=content-service, cluster=yogurtte-k3s-prod, container=content-service, instance=10.42.1.43:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-6995bb7d94-h2f6n, pool=HikariPool-1}` | 210 | 0 | 0 | 0 | **2026-08-04T00:35:00Z ~ 2026-08-04T01:29:15Z** |
| `hikaricp_connections_active` | `{__name__=hikaricp_connections_active, application=content-service, cluster=yogurtte-k3s-prod, container=content-service, instance=10.42.3.42:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-6995bb7d94-nq9l2, pool=HikariPool-1}` | 210 | 0 | 0 | 0 | **2026-08-04T00:35:00Z ~ 2026-08-04T01:29:15Z** |
| `hikaricp_connections_pending` | `{__name__=hikaricp_connections_pending, application=auth-service, cluster=yogurtte-k3s-prod, container=auth-service, instance=10.42.1.46:8090, job=auth-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=auth-service-5999bb9f5c-hmgp9, pool=HikariPool-1, service=auth-service}` | 218 | 0 | 0 | 0 | **2026-08-04T00:35:00Z ~ 2026-08-04T01:29:15Z** |
| `hikaricp_connections_pending` | `{__name__=hikaricp_connections_pending, application=chat-service, cluster=yogurtte-k3s-prod, container=chat-service, instance=10.42.1.47:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-fdcc7c776-xf4sv, pool=HikariPool-1}` | 206 | 0 | 0 | 0 | **2026-08-04T00:35:00Z ~ 2026-08-04T01:29:15Z** |
| `hikaricp_connections_pending` | `{__name__=hikaricp_connections_pending, application=content-service, cluster=yogurtte-k3s-prod, container=content-service, instance=10.42.1.43:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-6995bb7d94-h2f6n, pool=HikariPool-1}` | 210 | 0 | 0 | 0 | **2026-08-04T00:35:00Z ~ 2026-08-04T01:29:15Z** |
| `hikaricp_connections_pending` | `{__name__=hikaricp_connections_pending, application=content-service, cluster=yogurtte-k3s-prod, container=content-service, instance=10.42.3.42:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-6995bb7d94-nq9l2, pool=HikariPool-1}` | 210 | 0 | 0 | 0 | **2026-08-04T00:35:00Z ~ 2026-08-04T01:29:15Z** |
| `rate(jvm_gc_pause_seconds_sum[5m])` | `{action=end of major GC, application=chat-service, cause=Allocation Failure, cluster=yogurtte-k3s-prod, container=chat-service, gc=MarkSweepCompact, instance=10.42.1.47:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-fdcc7c776-xf4sv}` | 218 | 0 | 0 | 0 | **2026-08-04T00:35:00Z ~ 2026-08-04T01:29:15Z** |
| `rate(jvm_gc_pause_seconds_sum[5m])` | `{action=end of minor GC, application=auth-service, cause=G1 Evacuation Pause, cluster=yogurtte-k3s-prod, container=auth-service, gc=G1 Young Generation, instance=10.42.1.46:8090, job=auth-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=auth-service-5999bb9f5c-hmgp9, service=auth-service}` | 218 | 0 | 0.000 | 0 | **2026-08-04T00:35:00Z ~ 2026-08-04T00:35:30Z, 2026-08-04T00:39:45Z ~ 2026-08-04T01:14:30Z, 2026-08-04T01:18:45Z ~ 2026-08-04T01:29:15Z** |
| `rate(jvm_gc_pause_seconds_sum[5m])` | `{action=end of minor GC, application=chat-service, cause=Allocation Failure, cluster=yogurtte-k3s-prod, container=chat-service, gc=Copy, instance=10.42.1.47:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-fdcc7c776-xf4sv}` | 218 | 0.000 | 0.001 | 0.000 | — |
| `rate(jvm_gc_pause_seconds_sum[5m])` | `{action=end of minor GC, application=content-service, cause=Allocation Failure, cluster=yogurtte-k3s-prod, container=content-service, gc=Copy, instance=10.42.1.43:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-6995bb7d94-h2f6n}` | 218 | 0 | 0.000 | 0.000 | **2026-08-04T00:39:00Z ~ 2026-08-04T00:48:45Z, 2026-08-04T00:53:00Z ~ 2026-08-04T01:01:45Z, 2026-08-04T01:06:00Z ~ 2026-08-04T01:14:45Z, 2026-08-04T01:19:00Z ~ 2026-08-04T01:27:45Z** |
| `rate(jvm_gc_pause_seconds_sum[5m])` | `{action=end of minor GC, application=content-service, cause=Allocation Failure, cluster=yogurtte-k3s-prod, container=content-service, gc=Copy, instance=10.42.3.42:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-6995bb7d94-nq9l2}` | 218 | 0 | 0.000 | 0 | **2026-08-04T00:35:00Z ~ 2026-08-04T00:39:30Z, 2026-08-04T00:43:45Z ~ 2026-08-04T00:52:30Z, 2026-08-04T00:56:45Z ~ 2026-08-04T01:06:30Z, 2026-08-04T01:10:45Z ~ 2026-08-04T01:20:30Z, 2026-08-04T01:24:45Z ~ 2026-08-04T01:29:15Z** |
| `up` | `{__name__=up, app=node-exporter, cluster=yogurtte-k3s-prod, component=metrics, container=node-exporter, instance=ip-172-31-40-241, job=integrations/node_exporter, k8s_cluster_name=yogurtte-k3s-prod, namespace=monitoring, pod=grafana-k8s-monitoring-node-exporter-cph5l, source=kubernetes, workload=DaemonSet/grafana-k8s-monitoring-node-exporter}` | 218 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, app=node-exporter, cluster=yogurtte-k3s-prod, component=metrics, container=node-exporter, instance=ip-172-31-45-39, job=integrations/node_exporter, k8s_cluster_name=yogurtte-k3s-prod, namespace=monitoring, pod=grafana-k8s-monitoring-node-exporter-9kvqt, source=kubernetes, workload=DaemonSet/grafana-k8s-monitoring-node-exporter}` | 218 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, cluster=yogurtte-k3s-prod, container=auth-service, instance=10.42.1.46:8090, job=auth-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=auth-service-5999bb9f5c-hmgp9}` | 218 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, cluster=yogurtte-k3s-prod, container=chat-service, instance=10.42.1.47:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-fdcc7c776-xf4sv}` | 206 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, cluster=yogurtte-k3s-prod, container=content-service, instance=10.42.1.43:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-6995bb7d94-h2f6n}` | 210 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, cluster=yogurtte-k3s-prod, container=content-service, instance=10.42.3.42:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-6995bb7d94-nq9l2}` | 210 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, cluster=yogurtte-k3s-prod, instance=10.42.1.248:8080, job=integrations/kubernetes/kube-state-metrics, k8s_cluster_name=yogurtte-k3s-prod, source=kubernetes}` | 218 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, cluster=yogurtte-k3s-prod, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod}` | 218 | 0 | 1 | 1 | **2026-08-04T00:48:00Z ~ 2026-08-04T00:51:15Z, 2026-08-04T01:20:30Z ~ 2026-08-04T01:23:45Z** |
| `mongodb_up` | `{__name__=mongodb_up, cluster=yogurtte-k3s-prod, instance=infra-server, job=mongodb, k8s_cluster_name=yogurtte-k3s-prod}` | 218 | 1 | 1 | 1 | — |
| `kafka_brokers` | `{__name__=kafka_brokers, cluster=yogurtte-k3s-prod, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod}` | 190 | 1 | 1 | 1 | — |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=chat-service-fcm-tokens, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=0, topic=user.fcm-tokens}` | 190 | 0 | 0 | 0 | **2026-08-04T00:35:00Z ~ 2026-08-04T01:29:15Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=chat-service-fcm-tokens, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=1, topic=user.fcm-tokens}` | 190 | 0 | 0 | 0 | **2026-08-04T00:35:00Z ~ 2026-08-04T01:29:15Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=chat-service-fcm-tokens, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=2, topic=user.fcm-tokens}` | 190 | 0 | 0 | 0 | **2026-08-04T00:35:00Z ~ 2026-08-04T01:29:15Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=chat-service-notification-settings, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=0, topic=user.notification-settings}` | 190 | 0 | 0 | 0 | **2026-08-04T00:35:00Z ~ 2026-08-04T01:29:15Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=chat-service-notification-settings, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=1, topic=user.notification-settings}` | 190 | 0 | 0 | 0 | **2026-08-04T00:35:00Z ~ 2026-08-04T01:29:15Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=chat-service-notification-settings, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=2, topic=user.notification-settings}` | 190 | 0 | 0 | 0 | **2026-08-04T00:35:00Z ~ 2026-08-04T01:29:15Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=db-writer, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=0, topic=chat.messages}` | 190 | 0 | 0 | 0 | **2026-08-04T00:35:00Z ~ 2026-08-04T01:29:15Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=db-writer, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=1, topic=chat.messages}` | 190 | 0 | 0 | 0 | **2026-08-04T00:35:00Z ~ 2026-08-04T01:29:15Z** |
| `websocket_active_users` | `{__name__=websocket_active_users, application=chat-service, cluster=yogurtte-k3s-prod, container=chat-service, instance=10.42.1.47:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-fdcc7c776-xf4sv}` | 206 | 0 | 0 | 0 | **2026-08-04T00:35:00Z ~ 2026-08-04T01:29:15Z** |

값이 0이던 구간은 굵게 표시했다 — 프로세스가 사라져 시계열이 꺾인 것이 유일한 신호인 장애가 있다.

