# RCA Report — `scan-1785803700`

| 항목 | 값 |
|---|---|
| 모드 | rca |
| 질문 | 최근 1시간 안에 댓글 알림이 안 왔다는 제보가 있다. 원인을 조사해줘 |
| 시각 | 2026-08-04T01:56:44.696167200Z |
| provider | claude-cli |
| model | `claude-opus-5` · turns 1 |
| prompt | `.\prompts\system-prompt.md` |
| tokens | in 211767 (cacheRead 23,447 · cacheCreate 188,318) / out 7246 · cost $2.0761 |
| elapsed | total 121632ms (tempo 3056 · loki 1433 · mimir 1756 · assemble 57 · llm 107504) |

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
| tokens | in 55437 / out 3020 · cost $0.4071 |
| chars | 컨텍스트 45,469 + 프롬프트 1,399 = **46,868** |
| elapsed | survey 1449ms · llm 52855ms |

**선정 이유**: 댓글 알림 미수신 증상은 '댓글 POST 60초 타임아웃 + 같은 시각 kafka up=0 + content/chat 에러 폭증'이라는 하나의 지문이 두 번 반복된 것으로 보이며, 어느 회차가 제보 시각인지 확정할 수 없으므로 두 회차와 그 상·하류 후보를 함께 조사한다.

**근거**

- content-service POST /battles/{battleId}/items/{itemId}/comments 60,050ms error (traceId 6a713eb5c566f210b35a9bd582a5f37a, 01:21:57Z) — 댓글 등록 경로가 정확히 60초 타임아웃으로 실패
- 동일 엔드포인트 60,083ms error (traceId 6a713715a33b3693add43f02dd87289b, 00:49:25Z) — 같은 지문이 40분 전에도 발생
- kafka up 1→0, 00:49:18Z~00:54:18Z 및 01:24:18Z에 0 — 두 번 모두 댓글 타임아웃과 시각이 겹침
- content-service ERROR/WARN 42→301→154건(00:40~00:55), 405건(01:20~01:25) — kafka 다운 구간에 정확히 동기화된 폭증
- chat-service ERROR/WARN 20건+16건(00:40~00:50) — chat-service가 user.fcm-tokens·user.notification-settings·notification 컨슈머그룹 소유자이므로 알림 발송단 이상 후보
- kafka_consumergroup_lag이 전 구간 0인 것은 정상 근거가 아님 — lag을 노출하는 job="kafka" exporter 자체가 up=0이었으므로 해당 구간은 관측 공백
- Tempo 지연 검색(duration>3s && status!=error) 0건 — 에러 없이 느려진 요청은 없었고, 증상은 '지연'이 아니라 '60초 후 실패'다
- 모든 서비스 파드 up=1 유지, mongodb_up=1 — 애플리케이션·DB 다운이 아니라 메시징 계층 단절

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

- INC-3 — battle-deadline-notification-scheduler는 댓글이 아닌 마감 알림 경로이고, 2초 균일 타임아웃 6건은 INC-4의 kafka 단절로 이미 설명되는 부수 증상이라 조사 창을 넓히는 값이 낮다 — 다만 kafka 대기 타임아웃 가설이 무너지면 가장 먼저 되짚을 후보다.

**보정 기록**

- 창을 후보 [INC-1, INC-2, INC-4, INC-5, INC-6, INC-7, INC-8] 의 신호 시각에서 계산했다 (2026-08-04T00:35:00Z ~ 2026-08-04T01:29:18Z)

**스윕 무신호/실패**

- Tempo 지연 검색 '{ duration > 3s && status != error }'이 이 창에서 0건이다. 임계값보다 느린 요청이 없었다는 뜻이다.

## 수집 범위 (Coverage)

- **window**: 2026-08-04T00:35:00Z ~ 2026-08-04T01:29:18Z (3258s)
- **trace**: 41,206B / 41 spans
- **창 안 후보 트레이스**: 10건 / 41,206B
- **logs**: errwarn=291,407B · traceId=8,523B
- **metrics**: 8 수집 / 315,882B, 누락 [sum(rate(http_server_requests_seconds_count{application="content-service", status="401"}[1m]))]
- **context**: 374,106 chars (+ 시스템 프롬프트 598 chars)

### 토큰 축 (개선 지표)

| 단계 | 총 in | chars | 컨텍스트 토큰 |
|---|---:|---:|---:|
| 탐색 | 55,437 | 46,868 | 26,013 |
| 분석 | 211,767 | 374,704 | 182,343 |
| **합계** | **267,204** | | **208,356** |

- **overheadTokens 29,424 tok — 이 회차에 실측**(1자 프롬프트 프로브, 같은 명령·모델·샌드박스).
  호출마다 한 번씩 얹히므로 단계별로 뺀다. **다른 날 상수를 쓰지 않았다 = `█ 실측`.**
- contextTokens (count_tokens API): 측정 안 됨 — 구독 CLI 경로엔 API 키가 없다
- 비용은 **API 환산 추정치**다. 구독 계정으로 호출하므로 실제 청구액이 아니다.

## 수집 실패/누락

- Metric 'sum(rate(http_server_requests_seconds_count{application="content-service", status="401"}[1m]))' returned no series in this window; skipped.

---

## 1. 원인 후보 랭킹 (최대 3개)

1. **Kafka 브로커(infra-server 172.31.46.124:9092) 다운 → content-service의 `user.notifications` 발행 실패 (60초 타임아웃)** — 댓글 알림 미수신의 직접 원인
2. **`user.notifications` 토픽 메타데이터 자체가 비정상 (UNKNOWN_TOPIC_OR_PARTITION / INVALID_REPLICATION_FACTOR)** — 브로커가 살아나도 발행이 안 되는 별도 요인일 가능성
3. **Redis(172.31.46.124:6379) 단절 → ShedLock 실패로 배틀 마감 알림 스케줄러 중단** — 같은 시간대의 별개 알림 장애 (댓글 알림과는 무관)

---

## 2. 후보별 근거

### 후보 1 — Kafka 브로커 다운으로 인한 알림 발행 실패

- **근거:**
  - 댓글 작성 트레이스 2건 모두 span `publish user.notifications` (SPAN_KIND_PRODUCER)가 `STATUS_CODE_ERROR`, 속성 `error="Topic user.notifications not present in metadata after 60000 ms."`, duration 최대 **60013.4ms**. 부모 span `notification-publish` (`com.example.toycontent.app.notification.NotificationEventListener`)도 `error="Send failed"`.
  - 로그 원문: `[notify] 알림 발행 실패: userId=7, type=BATTLE_ITEM_COMMENT, error=Send failed`, 그리고 `Exception thrown when sending a message with key='7' and payload='KafkaNotificationDto(userId=7, type=BATTLE_ITEM_COMMENT, title=새 댓글, ...)' to topic user.notifications:` → **댓글 알림 메시지가 Kafka에 아예 들어가지 못했다.**
  - 시각 정합: 발행 실패 2건은 09:49:25→09:50:25 KST(=00:49:25→00:50:25 UTC), 10:21:57→10:22:57 KST(=01:21:57→01:22:57 UTC). 메트릭 `up{job=kafka, instance=infra-server}`가 **0이던 구간 = 00:48:00~00:51:15, 01:20:30~01:23:45 (UTC)** — 두 실패가 각각 두 다운 구간 안에 정확히 들어간다. `kafka_brokers`도 같은 구간(00:47:45~00:51:30, 01:20:15~01:24:00) 결측.
  - 로그 원문: `[Producer clientId=content-service-producer-1] Connection to node 1 (/172.31.46.124:9092) could not be established. Node may not be available.` — 10:20:25~10:23:38 KST 동안 두 pod(h2f6n, nq9l2)에서 초당 1회씩 연속 반복.
  - 댓글 자체는 정상: `http post /battles/{battleId}/items/{itemId}/comments` status 200, `insert into tb_battle_item_comment` row-affected=1, `[battle] 댓글 작성 완료 - commentId: 254 / 256`. → **댓글은 저장됐는데 알림만 유실**이라는 제보와 정확히 일치.
  - 실패가 삼켜짐: `notification-1`/`notification-2` 스레드에서 비동기 실행 후 ERROR 로그만 남고 재시도/보상 흔적 없음. `kafka_consumergroup_lag{topic=user.notifications}` 파티션 0~5 전 구간 0, `user.notifications.dlq` lag도 0 → 재처리 경로로도 흘러가지 않아 **알림은 영구 유실**.
- **확신도:** 높음
- **반증 데이터:** `up{job=redis}`, `up{job=mongodb}`, `up{job=node-infra}` (모두 instance=infra-server)는 전 구간 1이라, 브로커 다운이 호스트 전체 장애로는 설명되지 않는다(Kafka 프로세스 단위 이슈로 좁혀짐). 또한 브로커 다운 구간 밖의 댓글 발행 성공 사례가 수집 데이터에 **없어**, "다운 구간에서만 실패한다"는 것은 확인되지 않았다.

### 후보 2 — `user.notifications` 토픽 메타데이터 이상

- **근거:**
  - 로그 원문(h2f6n, 10:23:39.576 KST): `Error while fetching metadata with correlation id 480 : {user.notifications=UNKNOWN_TOPIC_OR_PARTITION}`
  - 로그 원문(nq9l2, 10:23:39.590 KST): `Error while fetching metadata with correlation id 484 : {user.notifications=INVALID_REPLICATION_FACTOR}`
  - `kafka_brokers`는 전 구간 **1**. INVALID_REPLICATION_FACTOR는 요구 복제 계수 > 가용 브로커 수일 때 나오는 오류로, 단일 브로커에서 RF≥2로 토픽 자동 생성이 시도되면 영구 실패한다. 즉 브로커가 복구돼도 발행이 계속 실패할 수 있다.
  - 이 두 로그는 브로커 복구 직전(다운 구간 01:20:30~01:23:45 UTC의 끝자락)에 찍혔다 — 브로커가 토픽 없이 올라온 정황.
- **확신도:** 중간
- **반증 데이터:** `kafka_consumergroup_lag{consumergroup=notification-processors, topic=user.notifications}`가 파티션 **0~5까지 6개 시리즈로, 브로커 다운 구간을 제외한 전 구간 lag 0**으로 존재한다. 토픽이 실제로 소멸했다면 이 시리즈가 사라져야 하므로, 토픽 부재보다는 브로커 재기동 중 일시적 메타데이터 불일치일 가능성도 동등하게 남는다.

### 후보 3 — Redis 단절 → 마감 알림 스케줄러 중단 (별개 장애)

- **근거:**
  - 로그 원문: `Cannot reconnect to [172.31.46.124/<unresolved>:6379]: finishConnect(..) failed: Connection refused: /172.31.46.124:6379` — content-service 양쪽 pod와 chat-service에서 09:43:30~09:44:34 KST 반복.
  - `io.lettuce.core.RedisCommandTimeoutException: Command timed out after 2 second(s)` → `org.springframework.dao.QueryTimeoutException` 이 09:43:34~09:45:03 KST 동안 지속.
  - 스택 원문에 `net.javacrumbs.shedlock.provider.redis.spring.RedisLockProvider.lock(RedisLockProvider.java:109)` → **ShedLock이 Redis 락 획득에 실패해 스케줄 작업이 그대로 죽음**: `task battle-deadline-notification-scheduler.notify` span 5건 전부 `outcome=ERROR`, `exception=QueryTimeoutException`, `code.function=notifyEnd` (00:44:00, 00:45:00, 00:46:00 UTC 등, 두 pod 모두). 로그: `o.s.s.s.TaskUtils$LoggingErrorHandler - Unexpected error occurred in scheduled task`.
  - 같은 시간대 pod 메트릭 결측(content h2f6n 00:44:45~00:47:00, nq9l2 00:44:30~00:46:45, chat 00:44:00~00:47:15)도 이 구간에 겹친다.
- **확신도:** 높음 (단, **댓글 알림의 원인은 아님**. 영향받은 것은 배틀 마감 알림 `notifyEnd`이며, 댓글 알림 실패 2건은 이 구간 밖인 00:49/01:21에 발생했다.)
- **반증 데이터:** `up{job=redis, instance=infra-server}`는 전 구간 1 — 익스포터 관점에서는 Redis가 죽지 않았다. 클라이언트 측 Connection refused와 배치되므로, 짧은 재기동/네트워크 순단이 스크레이프 간격에 잡히지 않았을 가능성이 있다.

### 데이터 부족 명시

- **알림 소비/전달 단계가 통째로 비어 있다.** `user.notifications`를 소비하는 서비스(consumergroup `notification-processors`)의 로그·트레이스, FCM/푸시 전송 결과가 수집되지 않았다. 따라서 "브로커 정상 구간에 발행된 댓글 알림은 사용자에게 도달했는가"는 **판단 불가**.
- 수집 실패: `http_server_requests_seconds_count{application="content-service", status="401"}` 시리즈 없음 — 인증 관련 가설은 검증 자체가 불가(단, 관측된 댓글 요청은 200이라 이 공백이 결론을 바꾸진 않는다).
- 댓글 트레이스가 2건뿐이고 **둘 다 실패**라, 실패율의 분모를 알 수 없다. 위 결론들의 확신도는 이 공백만큼 낮춰서 읽어야 한다.

---

## 3. 권장 다음 조치

1. `kafka-topics.sh --describe --topic user.notifications --bootstrap-server 172.31.46.124:9092` — 토픽 존재 여부, 파티션 수, **ReplicationFactor 값** 확인. RF > 1이면 브로커 1대 환경과 불일치이므로 RF=1로 재생성/조정.
2. 브로커 설정 확인: `auto.create.topics.enable`, `default.replication.factor`, `offsets.topic.replication.factor`. 단일 브로커면 전부 1이어야 한다.
3. infra-server(172.31.46.124)에서 Kafka 프로세스 재기동 이력 확인 — `up{job=kafka}`가 0이던 **00:48:00~00:51:15, 01:20:30~01:23:45 (UTC)** 두 구간의 브로커 로그/systemd·컨테이너 재시작 사유. 데이터 디렉터리가 휘발성이면 토픽 소멸 여부까지 확인.
4. 같은 호스트의 Redis 재기동 이력 확인 — 09:43:30 KST(00:43:30 UTC) `Connection refused` 시작 시점. Kafka·Redis 두 다운이 같은 근본 원인인지 분리 판단.
5. 유실된 알림 복구: 실패한 2건(`userId=7`, `BATTLE_ITEM_COMMENT`, commentId 254·256)은 Kafka에 진입하지 못해 DLQ에도 없다. `tb_battle_item_comment`에서 다운 구간(00:48~00:51, 01:20~01:24 UTC)에 생성된 댓글을 뽑아 수동 재발행 필요.
6. 마감 알림 재실행: ShedLock 실패로 건너뛴 `battle-deadline-notification-scheduler.notify` 회차(00:44~00:46 UTC)의 미발송 대상 확인 후 보정.
7. 재발 방지(코드): `NotificationEventListener.onNotification`이 발행 실패를 로그만 남기고 삼킨다. 발행 실패분을 로컬 outbox 테이블이나 DLQ로 적재해 재시도 가능하게 할 것. 부수적으로 `delivery.timeout.ms`(현재 60초)만큼 `notification-*` 스레드가 점유되는 것도 검토.
8. 알림(모니터링): `up{job=kafka} == 0`, 그리고 content-service의 `Send failed` / `not present in metadata` 로그 발생에 대한 알람 추가. 이번 건은 사용자 제보로 발견됐다.

**결론:** 댓글 알림 미수신의 직접 원인은 Kafka 브로커 다운 구간(00:48~00:51, 01:20~01:24 UTC)에 `user.notifications` 발행이 60초 타임아웃으로 실패하고, 실패가 재시도 없이 로그로만 삼켜져 알림이 유실된 것. 소비 측 데이터가 없어 브로커 정상 구간까지 포함한 전체 영향 범위는 아직 확정할 수 없다.

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

