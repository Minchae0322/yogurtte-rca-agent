# RCA Report — `scan-1785803700`

| 항목 | 값 |
|---|---|
| 모드 | rca |
| 질문 | 최근 1시간 안에 댓글 알림이 안 왔다는 제보가 있다. 원인을 조사해줘 |
| 시각 | 2026-08-04T01:30:05.902321700Z |
| provider | claude-cli |
| model | `claude-opus-5` · turns 1 |
| prompt | `.\prompts\system-prompt.md` |
| tokens | in 211466 (cacheRead 23,447 · cacheCreate 188,017) / out 6702 · cost $2.0595 |
| elapsed | total 116523ms (tempo 3372 · loki 949 · mimir 1217 · assemble 21 · llm 102653) |

## 탐색 (Triage)

| 항목 | 값 |
|---|---|
| 시간창 해석 | 상대 표현 '최근 1시간' |
| 시간창 확신도 | EXACT |
| 스윕 창 | 2026-08-04T00:29:18.036036700Z ~ 2026-08-04T01:29:18.036036700Z |
| 좁힌 창 | 2026-08-04T00:35:00Z ~ 2026-08-04T01:29:18.036036700Z |
| 대상 | content-service, chat-service |
| traceId | 6a713715a33b3693add43f02dd87289b |
| 트레이스 후보 | 8건 |
| 장애 후보 | 8건 · 선택 INC-1, INC-2, INC-4, INC-5, INC-6, INC-7, INC-8 |
| 계획 파싱 | 성공 |
| 스윕 컨텍스트 | 후보 + 원본 (A) |
| prompt | `.\prompts\triage-prompt.md` |
| tokens | in 55439 / out 2625 · cost $0.3973 |
| chars | 컨텍스트 45,486 + 프롬프트 1,399 = **46,885** |
| elapsed | survey 955ms · llm 46899ms |

**선정 이유**: 댓글 알림 미도착 증상은 content-service의 댓글 작성 60초 타임아웃과 Kafka 브로커 단절이 같은 시각에 두 번(00:49, 01:24) 반복된 것으로 설명되며, 발행(content)·전달(kafka)·소비(chat) 세 단계가 모두 후보로 쪼개져 올라와 함께 봐야 한다.

**근거**

- kafka job up 1→0, 00:49:18~00:54:18 구간 up=0 (INC-4), 01:24:18 재차 up=0 (INC-6) — 한 시간 안에 브로커 스크랩 2회 단절
- content-service http post /battles/{battleId}/items/{itemId}/comments 60,083ms error, 00:49:25.706Z (traceId 6a713715a33b3693add43f02dd87289b) — 댓글 작성 경로가 정확히 60초 타임아웃
- 01:21:57.050Z 60,004ms error 트레이스(traceId 6a713eb5c566f210b35a9bd582a5f37a), serviceStats는 content-service — 루트 스팬이 도착조차 못했고 두 번째 Kafka 단절과 같은 시각
- Loki content-service ERROR/WARN 42→301→154건(00:40~00:55), 405건(01:20~01:25) — 두 Kafka 단절 구간과 정확히 겹침
- chat-service ERROR/WARN 20건+16건(00:40~00:50) — 알림 소비/FCM 발송 측도 같은 시각에 에러, 상·하류가 함께 흔들림
- max_over_time(kafka_consumergroup_lag[5m])가 user.notifications 전 파티션에서 내내 0 — 브로커가 죽은 구간에도 lag가 늘지 않았다는 것은 발행 자체가 실패했다는 신호(소비 지연이 아님)
- Tempo 지연 검색(duration>3s && status!=error) 0건 — 느려진 게 아니라 에러로 끊긴 장애
- mongodb_up·kafka_brokers·node_exporter·서비스 up 모두 1 유지 — 앱 프로세스와 DB는 살아 있었고 끊긴 것은 kafka job 스크랩뿐

**스윕이 찾은 트레이스** (고른 것은 6a713715a33b3693add43f02dd87289b)

| traceId | 채널 | root service | root span | ms |
|---|---|---|---|---:|
| `6a713eb5c566f210b35a9bd582a5f37a` | error | <root span not yet received> | (없음) | 60004 |
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

## INC-8  <root span not yet received>
- 구간: 2026-08-04T01:21:57.050238Z ~ 2026-08-04T01:22:57.054238Z  (TEMPO · 시각 정확)
- <root span not yet received>  60,004ms (error 채널)
- traceId: 6a713eb5c566f210b35a9bd582a5f37a
- 같은 시각의 다른 후보: INC-6, INC-7  (인과 여부는 판단하지 않았다)

**기각한 후보**

- INC-3 — battle-deadline-notification-scheduler.notify 2초 에러는 댓글이 아니라 배틀 마감 알림 경로이고, 같은 Kafka 단절의 부수 증상으로 보이며 조사 창은 INC-2·INC-4가 이미 덮는다 — 댓글 경로에서 아무것도 안 나오면 여기부터 다시 본다

**보정 기록**

- 창을 후보 [INC-1, INC-2, INC-4, INC-5, INC-6, INC-7, INC-8] 의 신호 시각에서 계산했다 (2026-08-04T00:35:00Z ~ 2026-08-04T01:29:18.036036700Z)

**스윕 무신호/실패**

- Tempo 지연 검색 '{ duration > 3s && status != error }'이 이 창에서 0건이다. 임계값보다 느린 요청이 없었다는 뜻이다.

## 수집 범위 (Coverage)

- **window**: 2026-08-04T00:35:00Z ~ 2026-08-04T01:29:18.036036700Z (3258s)
- **trace**: 40,680B / 39 spans
- **창 안 후보 트레이스**: 10건 / 40,680B
- **logs**: errwarn=291,407B · traceId=8,521B
- **metrics**: 8 수집 / 315,882B, 누락 [sum(rate(http_server_requests_seconds_count{application="content-service", status="401"}[1m]))]
- **context**: 373,588 chars (+ 시스템 프롬프트 598 chars)

### 토큰 축 (개선 지표)

| 단계 | 총 in | chars | 컨텍스트 토큰 |
|---|---:|---:|---:|
| 탐색 | 55,439 | 46,885 | 26,015 |
| 분석 | 211,466 | 374,186 | 182,042 |
| **합계** | **266,905** | | **208,057** |

- **overheadTokens 29,424 tok — 이 회차에 실측**(1자 프롬프트 프로브, 같은 명령·모델·샌드박스).
  호출마다 한 번씩 얹히므로 단계별로 뺀다. **다른 날 상수를 쓰지 않았다 = `█ 실측`.**
- contextTokens (count_tokens API): 측정 안 됨 — 구독 CLI 경로엔 API 키가 없다
- 비용은 **API 환산 추정치**다. 구독 계정으로 호출하므로 실제 청구액이 아니다.

## 수집 실패/누락

- Metric 'sum(rate(http_server_requests_seconds_count{application="content-service", status="401"}[1m]))' returned no series in this window; skipped.

---

## 1. 원인 후보 랭킹 (최대 3개)

1. **Kafka `user.notifications` 발행 실패** — 브로커(node 1 = 172.31.46.124:9092) 미가용 + 토픽 메타데이터 부재로 producer가 60초 타임아웃 후 유실. 댓글 저장은 성공하나 알림만 나가지 않음.
2. **공통 인프라 호스트 172.31.46.124의 반복적 단절** — 같은 IP의 Redis(6379)·Kafka(9092)가 시간차를 두고 동시에 끊김. 위 1번의 상위 원인 후보.
3. **소비/전달 단계(consumer→FCM) 문제** — 확신도 낮음, 오히려 반증이 많음.

---

## 2. 후보별 근거

### 후보 1. Kafka `user.notifications` 발행 실패

**근거**
- 댓글 트레이스 2건 모두 HTTP는 성공, 발행만 실패:
  - `6a713715a33b3693add43f02dd87289b` — span `http post /battles/{battleId}/items/{itemId}/comments` → `status=200`, `outcome=SUCCESS`, DB `commit` 완료, `insert into tb_battle_item_comment` row-affected=1, commentId=254. 그런데 자식 span `publish user.notifications`(SPAN_KIND_PRODUCER)이 09:49:25.769 → 09:50:25.783, **60,013ms** 만에 `STATUS_CODE_ERROR`, 속성 `error="Topic user.notifications not present in metadata after 60000 ms."`
  - `6a713eb5c566f210b35a9bd582a5f37a` — 동일 패턴, commentId=256, publish 10:21:57.050 → 10:22:57.051, 동일 에러 문구.
- 로그 원문: `o.s.k.s.LoggingProducerListener - Exception thrown when sending a message with key='7' and payload='KafkaNotificationDto(userId=7, type=BATTLE_ITEM_COMMENT, title=새 댓글, content=운영자님이 [인생 띵작 애니 베스트] 배틀...' to topic user.notifications:` 직후 `c.e.t.a.n.NotificationEventListener - [notify] 알림 발행 실패: userId=7, type=BATTLE_ITEM_COMMENT, error=Send failed`
- 브로커 접속 자체가 안 됨: `[Producer clientId=content-service-producer-1] Connection to node 1 (/172.31.46.124:9092) could not be established. Node may not be available.` — 10:20:25.746부터 10:23:39까지 두 파드(h2f6n, nq9l2)에서 초당 1회로 연속.
- 토픽 자체가 없음: `Error while fetching metadata with correlation id 480 : {user.notifications=UNKNOWN_TOPIC_OR_PARTITION}` (10:23:39.576), 다른 파드에서는 `correlation id 484 : {user.notifications=INVALID_REPLICATION_FACTOR}` (10:23:39.590). `kafka_brokers=1`인데 요구 replication factor가 그보다 커서 자동 생성이 실패하는 형태.
- 메트릭 정합: `up{job=kafka}`가 **00:48:00~00:51:15Z**, **01:20:30~01:23:45Z** 두 구간에서 0. 실패한 두 트레이스의 publish 구간(00:49:25~00:50:25Z, 01:21:57~01:22:57Z)이 각각 이 두 구간에 **정확히 포함**됨. `kafka_brokers`도 동일 구간(00:47:45~00:51:30Z, 01:20:15~01:24:00Z) 결측.
- 유실 확정: 리스너가 예외를 잡아 로그만 남기고(`AsyncExecutionInterceptor` 스택) 재시도 흔적이 없으며, `kafka_consumergroup_lag{topic="user.notifications.dlq"}`도 전 구간 0/-1 → 재시도·DLQ 적재 없음. 따라서 "지연"이 아니라 "영구 미수신".

**확신도: 높음**

**반증 데이터**
- 없음. (다만 `up{job=kafka}=0` 구간 밖에서도 알림이 실패했는지는 이 창의 트레이스 2건만으로는 확인 불가.)

---

### 후보 2. 공통 인프라 호스트 172.31.46.124 단절

**근거**
- Kafka(9092)와 Redis(6379)가 **같은 IP**. Redis 쪽: `i.l.core.protocol.ConnectionWatchdog - Cannot reconnect to [172.31.46.124/<unresolved>:6379]: finishConnect(..) failed: Connection refused: /172.31.46.124:6379` — content-service 두 파드와 chat-service에서 09:43:30~09:44:34에 반복.
- 그 결과 `RedisReactiveHealthIndicator - Redis health check failed` + `io.lettuce.core.RedisCommandTimeoutException: Command timed out after 2 second(s)`가 09:43:34~09:45:03 사이 10초 주기로 계속.
- 파드 메트릭 스크레이프 결측이 같은 시간대에 몰림: content h2f6n `00:44:45~00:47:00Z`, content nq9l2 `00:44:30~00:46:45Z`, chat `00:44:00~00:47:15Z`. 이후 Kafka가 `00:48:00~00:51:15Z`에 down. 시간 순서가 Redis 단절 → 스크레이프 결측 → Kafka down으로 이어짐.
- Redis 장애의 2차 피해도 관측됨: `task battle-deadline-notification-scheduler.notify` span이 4건 모두 `exception=QueryTimeoutException`, `error="Redis command timed out"`, `outcome=ERROR`. 스택은 `net.javacrumbs.shedlock.provider.redis.spring.RedisLockProvider.lock` → ShedLock 락 획득 실패로 **배틀 마감 알림 스케줄러가 통째로 미실행**. 즉 댓글 알림 외 다른 알림 경로도 같은 시간대에 죽어 있었음.

**확신도: 중간**

**반증 데이터**
- `up{job=redis, instance=infra-server}`는 **전 구간 1**(변화 없음). `up{job=node-infra}`, `mongodb_up`도 전 구간 1. 호스트가 통째로 내려갔다면 이들도 0이 되어야 함 → "호스트 다운"보다는 **프로세스/컨테이너 단위 재시작 또는 네트워크 부분 단절**에 가깝다. exporter 스크레이프 간격과 실제 단절 구간이 어긋났을 가능성도 있어 이 반증만으로 후보를 배제할 수는 없음.
- Kafka가 두 번(00:48, 01:20) 내려간 반면 Redis 에러는 첫 구간에만 관측됨 → 완전히 동일한 사건은 아님.

---

### 후보 3. 소비/전달 단계(consumer → FCM) 문제

**근거**
- 알림 소비자 존재는 확인됨: `kafka_consumergroup_lag{consumergroup="notification-processors", topic="user.notifications"}` 파티션 0~5.
- 그러나 **모든 파티션 lag이 전 구간 0**, DLQ(`user.notifications.dlq`) lag도 0/-1. 소비가 밀린 흔적이 전혀 없음.

**확신도: 낮음**

**반증 데이터**
- lag 전 구간 0 = 큐에 쌓인 메시지 자체가 없음. 발행이 안 됐으니 소비할 것도 없다는 후보 1과 정합. 소비자 병목이라면 lag이 증가해야 함.
- **데이터 부족**: consumer(chat-service) 측 알림 처리 로그, FCM 발송 결과, `user.notifications` 토픽의 파티션/replication 설정 실측치가 수집되지 않음. 발행이 정상이었을 때 실제 단말까지 도달하는지는 이 데이터로 판단 불가.

---

**수집 실패로 인한 확신도 보정**
- 메트릭 `sum(rate(http_server_requests_seconds_count{application="content-service", status="401"}[1m]))` 무결과 — 인증 실패 가설은 검증 불가하나, 문제의 두 요청이 모두 200/userId=1로 성공했고 `secured request` 스팬에 예외가 없어 이 공백이 결론에 주는 영향은 작음.
- chat-service 트레이스는 `security filterchain` 스팬 2건뿐이라 알림 소비 경로가 트레이스로 커버되지 않음. → 후보 3의 확신도를 낮음으로 유지하는 근거.

---

## 3. 권장 다음 조치

**즉시 확인 (읽기 전용)**
1. 토픽 존재/설정 확인: `kafka-topics.sh --bootstrap-server 172.31.46.124:9092 --describe --topic user.notifications`. `UNKNOWN_TOPIC_OR_PARTITION`과 `INVALID_REPLICATION_FACTOR`가 같이 나온 것으로 보아 토픽이 없고 자동 생성도 실패 중일 가능성이 높음.
2. 브로커 상태와 재시작 이력: `172.31.46.124`에서 Kafka 프로세스/컨테이너의 uptime과 재시작 로그 확인. `up{job=kafka}`가 0이던 `00:48:00~00:51:15Z`, `01:20:30~01:23:45Z`(KST 09:48, 10:20)에 무슨 일이 있었는지.
3. 같은 호스트의 Redis(6379) 재시작 이력 확인 — KST 09:43:30 전후. `up{job=redis}`가 1로 유지된 것과 앱의 `Connection refused`가 왜 어긋나는지(exporter가 다른 인스턴스를 보고 있는지) 대조.
4. 브로커 설정 `default.replication.factor`, `offsets.topic.replication.factor`, `auto.create.topics.enable`과 프로듀서/컨슈머 측 기대 replication factor 비교. 브로커가 1대(`kafka_brokers=1`)인데 RF>1을 요구하면 토픽 생성이 계속 실패함.

**조치**
5. 토픽이 없으면 `partitions=6`(consumer group이 파티션 0~5를 참조 중), `replication-factor=1`로 명시 생성. 자동 생성에 의존하지 말 것.
6. 발행 실패 시 유실 방지: 현재 `NotificationEventListener.onNotification`이 예외를 로그만 하고 끝남. 재시도 또는 DLQ 적재 경로 추가. 최소한 `max.block.ms`를 60초에서 낮춰 요청 스레드(`notification-1/2`)가 1분씩 점유되는 것부터 막을 것.
7. 알림 스케줄러 복구 확인: ShedLock이 Redis에 의존하므로 Redis 단절 = 마감 알림 전면 중단. KST 09:43~09:45 구간에 누락된 마감 알림 재발송 필요 여부 판단.

**모니터링 보강**
8. `up{job=kafka} == 0`, `kafka_brokers < 1`에 알람 추가. 현재 두 번 down 했는데 제보로만 발견됨.
9. 알림 발행 실패 카운터(`[notify] 알림 발행 실패` 로그 기반 또는 커스텀 메트릭)를 노출하고 알람 연결. lag=0은 "정상"과 "아무것도 발행되지 않음"을 구분하지 못함.
10. 후보 3 검증용으로 chat-service의 `notification-processors` 소비 로그와 FCM 발송 결과를 수집 대상에 추가.

---

## 관측 증거 (Evidence)

> 이 조사가 실제로 본 값이다. 원본 응답 전체는 `reports/raw/scan-1785803700-*.json`에 있다.

### 호출 그래프 (트레이스에서 추출)

```
content-service --db--> redis  2회  최대 0.6ms  [GET]
content-service --jdbc--> mysql/content (HikariPool-1)  16회  최대 52.3ms
    events: acquired, commit
content-service --messaging--> kafka/user.notifications  2회  최대 60013.4ms  [publish]
    error: Topic user.notifications not present in metadata after 60000 ms.
```

### span (duration 상위 15 / 전체 39)

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

