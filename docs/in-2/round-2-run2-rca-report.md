# RCA Report — `6a69f6581e1b5ce5c855e106cca6260c`

| 항목 | 값 |
|---|---|
| 모드 | rca |
| 질문 | 최근 1시간 안에 댓글 알림이 안 왔다는 제보가 있다. 원인을 조사해줘 |
| 시각 | 2026-07-29T13:03:28.856263Z |
| provider | claude-cli |
| model | `claude-opus-5` · turns 1 |
| prompt | `./prompts/system-prompt.md` |
| tokens | in 141427 (cacheRead 18,133 · cacheCreate 123,292) / out 9112 · cost $1.5657 |
| elapsed | total 138693ms (tempo 556 · loki 374 · mimir 630 · assemble 1 · llm 133833) |

## 탐색 (Triage)

| 항목 | 값 |
|---|---|
| 시간창 해석 | 상대 표현 '최근 1시간' |
| 스윕 창 | 2026-07-29T12:02:25.885568Z ~ 2026-07-29T13:02:25.885568Z |
| 좁힌 창 | 2026-07-29T12:44:00Z ~ 2026-07-29T12:56:00Z |
| 대상 | content-service, chat-service |
| traceId | 6a69f6581e1b5ce5c855e106cca6260c |
| 트레이스 후보 | 1건 |
| 계획 파싱 | 성공 |
| prompt | `./prompts/triage-prompt.md` |
| tokens | in 43014 / out 4250 · cost $0.3808 |
| chars | 컨텍스트 38,225 + 프롬프트 1,196 = **39,421** |
| elapsed | survey 1496ms · llm 61471ms |

**선정 이유**: Kafka 접점 단절(12:47)과 content-service의 60초 발행 타임아웃 트레이스·414건 에러가 같은 시각에 겹치고, 복구 후에도 컨슈머 lag이 0이어서 댓글 이벤트가 Kafka에 진입조차 못 한 구간으로 판단되므로 그 앞뒤 여유를 둔 12:44~12:56을 발행자(content)와 소비자(chat) 양쪽으로 좁혔다.

**근거**

- up{job="kafka",instance="infra-server"} = 0 @ 2026-07-29T12:47:25Z (앞뒤 스크레이프는 모두 1)
- kafka_brokers 및 모든 kafka_consumergroup_lag 시계열에서 12:47:25 데이터포인트만 통째로 결측 — 브로커 메트릭 수집 자체가 끊김
- Tempo 에러 트레이스 6a69f6581e1b5ce5c855e106cca6260c: content-service 스팬 2개 모두 status=error, 12:47:20.564Z 시작, duration 60,003ms (Kafka producer max.block.ms 60s 만료 패턴), rootServiceName은 '<root span not yet received>'
- Loki ERROR/WARN 집계: content-service 12:50 버킷 414건 — 조회 1시간 중 content-service의 유일한 에러 버킷
- Kafka 복구(12:52:25) 이후 notification·notification-processors 컨슈머 그룹 lag이 전 파티션 0 — 밀렸다 처리된 백로그가 없음, 즉 발행 단계에서 이벤트 유실
- 동일 구간 chat-service ERROR/WARN 0건, websocket_active_users 0 유지 — 소비할 이벤트가 도착하지 않았음을 시사(부재 신호)

**스윕이 찾은 트레이스** (고른 것은 6a69f6581e1b5ce5c855e106cca6260c)

| traceId | root service | root span | ms |
|---|---|---|---:|
| `6a69f6581e1b5ce5c855e106cca6260c` ←선택 | <root span not yet received> | (없음) | 60003 |

## 수집 범위 (Coverage)

- **window**: 2026-07-29T12:44:00Z ~ 2026-07-29T12:56:00Z (720s)
- **trace**: 14,144B / 15 spans
- **logs**: errwarn=145,172B · traceId=8,132B
- **metrics**: 8 수집 / 81,084B, 누락 [sum(rate(http_server_requests_seconds_count{application="content-service", status="401"}[1m]))]
- **context**: 248,911 chars (+ 시스템 프롬프트 575 chars)

### 토큰 축 (개선 지표)

| 단계 | 총 in | chars | 컨텍스트 토큰 |
|---|---:|---:|---:|
| 탐색 | 43,014 | 39,421 | 21,705 |
| 분석 | 141,427 | 249,486 | 120,118 |
| **합계** | **184,441** | | **141,823** |

- **overheadTokens 21,309 tok — 이 회차에 실측**(1자 프롬프트 프로브, 같은 명령·모델·샌드박스).
  호출마다 한 번씩 얹히므로 단계별로 뺀다. **다른 날 상수를 쓰지 않았다 = `█ 실측`.**
- contextTokens (count_tokens API): 측정 안 됨 — 구독 CLI 경로엔 API 키가 없다
- 비용은 **API 환산 추정치**다. 구독 계정으로 호출하므로 실제 청구액이 아니다.

## 수집 실패/누락

- Metric 'sum(rate(http_server_requests_seconds_count{application="content-service", status="401"}[1m]))' returned no series in this window; skipped.

---

## 1. 원인 후보 랭킹 (최대 3개)

1. **Kafka 브로커(단일 노드, 172.31.46.124:9092) 다운으로 content-service의 알림 이벤트 발행이 60초 타임아웃 후 실패** — 댓글은 DB에 저장됐지만 `user.notifications` 이벤트가 Kafka에 들어가지 못함
2. **발행 실패 이벤트에 대한 재시도/아웃박스/DLQ 복구 경로 부재로 알림 이벤트가 영구 유실** — 브로커 복구(12:49) 후에도 알림이 재전송된 흔적 없음
3. **chat 서비스(소비 측) 장애 또는 소비 지연** — 관측값이 정면으로 배치됨, 사실상 배제

---

## 2. 후보별 근거

### 후보 1. Kafka 브로커 다운 → 프로듀서 메타데이터 조회 실패

**근거**
- 트레이스 span `publish user.notifications` (SPAN_KIND_PRODUCER, `messaging.destination.name=user.notifications`)
  - 시작 `1785329240564274000` = **12:47:20.564Z**, 종료 `1785329300565406000` = **12:48:20.565Z** → **정확히 60.001초**
  - `error = "Topic user.notifications not present in metadata after 60000 ms."`, `status.code = STATUS_CODE_ERROR`
  - 부모 span `notification-publish` (`com.example.toycontent.app.notification.NotificationEventListener#onNotification`) 역시 60.004초 소요 후 `error = "Send failed"`
- 로그 원문 (traceId 일치):
  - `21:48:20.564 [notification-1] ERROR ... o.s.k.s.LoggingProducerListener - Exception thrown when sending a message with key='7' and payload='KafkaNotificationDto(userId=7, type=BATTLE_ITEM_COMMENT, title=새 댓글, content=운영자님이 [인생 띵작 애니 베스트] 배틀...' to topic user.notifications:`
  - `21:48:20.565 [notification-1] ERROR ... NotificationEventListener - [Notification] 알림 발행 실패: userId=7, type=BATTLE_ITEM_COMMENT, error=Send failed`
- 프로듀서 WARN이 **12:45:49.166Z ~ 12:49:05.028Z (약 3분 16초)** 연속:
  `[Producer clientId=content-service-producer-1] Connection to node 1 (/172.31.46.124:9092) could not be established. Node may not be available.`
  → content-service **두 파드 모두**(`-qnxk6`, `-scw7k`)에서 동일 발생 = 파드 국소 문제 아님
- 메트릭 `up{job="kafka", instance="infra-server"}`: `1785329145`(12:45:45Z)까지 **1** → `1785329160`(12:46:00Z)부터 `1785329355`(12:49:15Z)까지 **0** → `1785329370`(12:49:30Z)부터 **1**. `kafka_brokers` 시계열도 같은 구간(12:46:00~12:49:15) 샘플 자체가 **결측**
- 복구 직전 로그: `21:49:05.889` / `21:49:06.016`
  `Error while fetching metadata with correlation id 1157 : {user.notifications=LEADER_NOT_AVAILABLE}`
  → 브로커 프로세스는 살아났으나 파티션 리더 선출 전 (TCP 연결은 성공했다는 뜻)
- `kafka_brokers = 1` (전 구간) → **단일 브로커, 복제본 없음**. 브로커 1대 다운 = 토픽 전면 불가
- 반대로 content-service 자체는 정상: `up=1` 전 구간, `hikaricp_connections_active` 최대 1 / `pending` 0, GC pause rate ~2.9e-5s/s, HTTP 응답 `POST /api/battles/22/items/125/comments 200 - 47ms`, DB `commit` 이벤트 `1785329240562252000` 정상

**확신도: 높음**

**반증 데이터**
- 프로듀서 첫 연결 실패(12:45:49.166Z)가 `up{job="kafka"}`가 0으로 떨어진 첫 스크랩(12:46:00Z)보다 **11초 빠름**. 다만 직전 스크랩이 12:45:45Z이므로 스크랩 간격(15s) 내 오차로 설명 가능하며, 상충이라기보다 실제 다운 시점이 12:45:45~12:45:49Z 사이임을 시사
- 그 외 이 후보와 배치되는 관측값 없음

---

### 후보 2. 발행 실패 이벤트의 재시도/복구 경로 부재 → 이벤트 영구 유실

**근거**
- 해당 traceId(`6a69f6581e1b5ce5c855e106cca6260c`)의 로그 흐름이 `1단계~5단계 통과` → `댓글 작성 완료 - commentId: 212` → **`알림 발행 실패`** 에서 **종료**. 조회 창(12:44~12:56Z) 내 재발행·재시도·DLQ 적재 로그가 **한 줄도 없음**
- 트레이스에도 재시도 span 없음. `notification-publish` span은 1회 실행 후 ERROR로 종료
- 발행 실패는 DB 커밋(`commit` @12:47:20.562) **이후** 별도 스레드(`notification-1`)에서 발생 → **댓글 데이터는 남고 알림만 소실**되는 구조. 제보(“댓글은 달렸는데 알림이 안 왔다”)와 정확히 일치
- `kafka_consumergroup_lag{consumergroup="notification-processors", topic="user.notifications"}` 파티션 0~5 전부 **0** (12:49:30Z 복구 이후 12:56까지도 0) → 브로커 복구 후에도 이 토픽에 **새로 유입된 메시지가 없음**
- `kafka_consumergroup_lag{consumergroup="notification-recovery", topic="user.notifications.dlq"}` 파티션 0/2 = 0, 파티션 1 = -1 → DLQ에도 처리 대기 메시지 없음
- 부가 리스크: `notification-1` 스레드가 `max.block.ms` 기본값 60초 동안 통째로 블로킹됨(span 60.004s). 장애 구간에 알림이 몰릴 경우 알림 스레드 풀 고갈 가능

**확신도: 중간**
(재시도 로직 부재를 코드로 확인한 게 아니라 "관측 창 내 재시도 흔적이 없다"는 부정 증거에 기반. 또한 DLQ lag=0은 "DLQ에 안 들어갔다"가 아니라 "consumer가 다 소비했다"로도 읽힐 수 있음 — 다만 같은 브로커가 죽어 있었으므로 장애 중 DLQ 발행도 불가능했음)

**반증 데이터**
- `user.notifications.dlq` 토픽과 `notification-recovery` 컨슈머 그룹이 **존재**한다는 것은 복구 경로가 설계상 있다는 뜻. 즉 "경로가 아예 없다"가 아니라 "이 케이스에서 동작하지 않았다"일 수 있음. 정확한 구분은 데이터 부족

---

### 후보 3. chat 서비스(소비 측) 장애/소비 지연

**근거 (지지 근거 약함, 배제 목적으로 기재)**
- 제보 증상(알림 미수신)만 놓고 보면 소비 측 실패도 이론상 가능

**확신도: 낮음**

**반증 데이터 (다수)**
- `up{job="chat-service", pod="chat-service-857c54dd97-2nzgh"}` = **1** (12:44:00~12:56:00Z 전 구간)
- `kafka_consumergroup_lag{consumergroup="notification-processors", topic="user.notifications"}` 파티션 0~5 **전부 0**, 브로커 복구 후에도 0 → 소비 적체 없음
- chat-service `hikaricp_connections_active = 0`, `pending = 0`; GC pause rate 2.2e-4 ~ 7.7e-4 s/s로 평시 수준
- 실패 지점이 트레이스상 **PRODUCER span**으로 명확히 특정됨. 브로커에 메시지가 들어가지 못했으므로 소비 측이 받을 것 자체가 없었음

---

## 3. 권장 다음 조치

**즉시 (원인 확정)**
1. `infra-server`의 Kafka 브로커가 **12:45:45Z~12:49:30Z(KST 21:45:45~21:49:30)** 에 왜 내려갔는지 확인 — 브로커 로그, `journalctl`/컨테이너 재시작 이력, OOM-Kill, 디스크. 현재 데이터로는 **다운 사실만 확인되고 원인은 판단 불가(데이터 부족)**
2. 같은 창에서 `node-infra` 메트릭(메모리/디스크/CPU)과 브로커 프로세스 시작 시각 대조

**즉시 (영향 범위 산정 및 복구)**
3. 유실 후보 이벤트 전수 파악:
   `count_over_time({job="default/content-service"} |= "알림 발행 실패" [1h])` 및 `|= "LoggingProducerListener"` — 이번 트레이스 외 실패 건수 확인
4. 확인된 유실 건 수동 재발행. 이번 건: `traceId=6a69f6581e1b5ce5c855e106cca6260c`, `userId=7`, `type=BATTLE_ITEM_COMMENT`, `commentId=212`, `battleId=22`, `itemId=125`
5. `tb_battle_item_comment`의 해당 구간 INSERT 건수와 `user.notifications` 실제 produce 건수를 대조해 누락분 산출

**구조 개선**
6. `kafka_brokers=1` — 단일 브로커 SPOF 해소(브로커 증설 + replication factor / min.insync.replicas 상향) 검토
7. 프로듀서 설정 조정: `max.block.ms` 60000ms는 알림 스레드를 60초 점유함. 짧게 낮추고 `delivery.timeout.ms`/`retries`와 함께 재설계
8. 발행 실패 시 **트랜잭셔널 아웃박스** 또는 로컬 재시도 큐 도입 여부 결정 — 현재 `NotificationEventListener.onNotification`은 실패를 로그만 남기고 종료함
9. 알람 신설: `up{job="kafka"} == 0`, content-service의 `알림 발행 실패` ERROR rate, 프로듀서 `record-error-rate`

**추가 데이터 수집 (현재 공백)**
10. **조회 창을 제보 범위인 1시간 전체로 확대** — 현 분석은 12:44~12:56Z(12분)만 커버하므로, 이 구간 밖의 다른 미수신 건은 판단 불가
11. **chat-service 애플리케이션 로그** — 이번 Loki 응답에 chat-service 스트림이 없어 소비 측은 메트릭으로만 배제했음. 로그로 교차 검증 필요
12. **Kafka 브로커 자체 로그 / K8s 이벤트** — 다운 원인 규명에 필수
13. 참고: 요청된 `sum(rate(http_server_requests_seconds_count{application="content-service", status="401"}[1m]))` 는 시리즈 없음으로 수집 실패. 다만 트레이스상 `JwtAuthenticationFilter` 통과, `userId=1`, `status=200`, `exception=none` 이 확인되므로 **auth 경로는 이번 결론에 영향 없음**

---

## 관측 증거 (Evidence)

> 이 조사가 실제로 본 값이다. 원본 응답 전체는 `reports/raw/6a69f6581e1b5ce5c855e106cca6260c-*.json`에 있다.

### span (duration 상위 15 / 전체 15)

| ms | service | span | 시작 |
|---:|---|---|---|
| 60003.92 | content-service | `notification-publish` | 2026-07-29T12:47:20.564199Z |
| 60001.13 | content-service | `publish user.notifications` | 2026-07-29T12:47:20.564274Z |
| 47.76 | content-service | `http post /battles/{battleId}/items/{itemId}/comments` | 2026-07-29T12:47:20.517827Z |
| 46.44 | content-service | `secured request` | 2026-07-29T12:47:20.518341Z |
| 41.76 | content-service | `connection` | 2026-07-29T12:47:20.522943Z |
| 2.93 | content-service | `query` | 2026-07-29T12:47:20.545930Z |
| 1.93 | content-service | `query` | 2026-07-29T12:47:20.532287Z |
| 1.84 | content-service | `query` | 2026-07-29T12:47:20.554908Z |
| 1.79 | content-service | `query` | 2026-07-29T12:47:20.526779Z |
| 0.60 | content-service | `GET` | 2026-07-29T12:47:20.529800Z |
| 0.34 | content-service | `generated-keys` | 2026-07-29T12:47:20.549090Z |
| 0.29 | content-service | `result-set` | 2026-07-29T12:47:20.534416Z |
| 0.24 | content-service | `result-set` | 2026-07-29T12:47:20.528816Z |
| 0.20 | content-service | `security filterchain before` | 2026-07-29T12:47:20.518123Z |
| 0.08 | content-service | `security filterchain after` | 2026-07-29T12:47:20.564799Z |

### 로그 원문 (60 / 전체 425줄)

전체가 상한을 넘어 ERROR/WARN 줄을 우선 발췌했다. 나머지는 원본 파일에 있다.

```
2026-07-29T12:45:49.166622262Z  [content-service]  2026-07-29 21:45:49.166 [kafka-producer-network-thread | content-service-producer-1]  WARN [traceId=NONE,spanId=NONE,userId=NONE] o.apache.kafka.clients.NetworkClient - [Producer clientId=content-service-producer-1] Connection to node 1 (/172.31.46.124:9092) could not be established. Node may not be available.
2026-07-29T12:45:49.269712036Z  [content-service]  2026-07-29 21:45:49.269 [kafka-producer-network-thread | content-service-producer-1]  WARN [traceId=NONE,spanId=NONE,userId=NONE] o.apache.kafka.clients.NetworkClient - [Producer clientId=content-service-producer-1] Connection to node 1 (/172.31.46.124:9092) could not be established. Node may not be available.
2026-07-29T12:45:49.467984889Z  [content-service]  2026-07-29 21:45:49.467 [kafka-producer-network-thread | content-service-producer-1]  WARN [traceId=NONE,spanId=NONE,userId=NONE] o.apache.kafka.clients.NetworkClient - [Producer clientId=content-service-producer-1] Connection to node 1 (/172.31.46.124:9092) could not be established. Node may not be available.
2026-07-29T12:45:49.920225325Z  [content-service]  2026-07-29 21:45:49.920 [kafka-producer-network-thread | content-service-producer-1]  WARN [traceId=NONE,spanId=NONE,userId=NONE] o.apache.kafka.clients.NetworkClient - [Producer clientId=content-service-producer-1] Connection to node 1 (/172.31.46.124:9092) could not be established. Node may not be available.
2026-07-29T12:45:50.754364514Z  [content-service]  2026-07-29 21:45:50.754 [kafka-producer-network-thread | content-service-producer-1]  WARN [traceId=NONE,spanId=NONE,userId=NONE] o.apache.kafka.clients.NetworkClient - [Producer clientId=content-service-producer-1] Connection to node 1 (/172.31.46.124:9092) could not be established. Node may not be available.
2026-07-29T12:45:51.757572233Z  [content-service]  2026-07-29 21:45:51.757 [kafka-producer-network-thread | content-service-producer-1]  WARN [traceId=NONE,spanId=NONE,userId=NONE] o.apache.kafka.clients.NetworkClient - [Producer clientId=content-service-producer-1] Connection to node 1 (/172.31.46.124:9092) could not be established. Node may not be available.
2026-07-29T12:45:52.758875745Z  [content-service]  2026-07-29 21:45:52.758 [kafka-producer-network-thread | content-service-producer-1]  WARN [traceId=NONE,spanId=NONE,userId=NONE] o.apache.kafka.clients.NetworkClient - [Producer clientId=content-service-producer-1] Connection to node 1 (/172.31.46.124:9092) could not be established. Node may not be available.
2026-07-29T12:45:53.759327148Z  [content-service]  2026-07-29 21:45:53.759 [kafka-producer-network-thread | content-service-producer-1]  WARN [traceId=NONE,spanId=NONE,userId=NONE] o.apache.kafka.clients.NetworkClient - [Producer clientId=content-service-producer-1] Connection to node 1 (/172.31.46.124:9092) could not be established. Node may not be available.
2026-07-29T12:45:54.760413378Z  [content-service]  2026-07-29 21:45:54.760 [kafka-producer-network-thread | content-service-producer-1]  WARN [traceId=NONE,spanId=NONE,userId=NONE] o.apache.kafka.clients.NetworkClient - [Producer clientId=content-service-producer-1] Connection to node 1 (/172.31.46.124:9092) could not be established. Node may not be available.
2026-07-29T12:45:55.762597856Z  [content-service]  2026-07-29 21:45:55.762 [kafka-producer-network-thread | content-service-producer-1]  WARN [traceId=NONE,spanId=NONE,userId=NONE] o.apache.kafka.clients.NetworkClient - [Producer clientId=content-service-producer-1] Connection to node 1 (/172.31.46.124:9092) could not be established. Node may not be available.
2026-07-29T12:45:56.787145318Z  [content-service]  2026-07-29 21:45:56.787 [kafka-producer-network-thread | content-service-producer-1]  WARN [traceId=NONE,spanId=NONE,userId=NONE] o.apache.kafka.clients.NetworkClient - [Producer clientId=content-service-producer-1] Connection to node 1 (/172.31.46.124:9092) could not be established. Node may not be available.
2026-07-29T12:45:57.787940861Z  [content-service]  2026-07-29 21:45:57.787 [kafka-producer-network-thread | content-service-producer-1]  WARN [traceId=NONE,spanId=NONE,userId=NONE] o.apache.kafka.clients.NetworkClient - [Producer clientId=content-service-producer-1] Connection to node 1 (/172.31.46.124:9092) could not be established. Node may not be available.
2026-07-29T12:45:58.788326307Z  [content-service]  2026-07-29 21:45:58.788 [kafka-producer-network-thread | content-service-producer-1]  WARN [traceId=NONE,spanId=NONE,userId=NONE] o.apache.kafka.clients.NetworkClient - [Producer clientId=content-service-producer-1] Connection to node 1 (/172.31.46.124:9092) could not be established. Node may not be available.
2026-07-29T12:45:59.789478661Z  [content-service]  2026-07-29 21:45:59.789 [kafka-producer-network-thread | content-service-producer-1]  WARN [traceId=NONE,spanId=NONE,userId=NONE] o.apache.kafka.clients.NetworkClient - [Producer clientId=content-service-producer-1] Connection to node 1 (/172.31.46.124:9092) could not be established. Node may not be available.
2026-07-29T12:46:00.791544259Z  [content-service]  2026-07-29 21:46:00.791 [kafka-producer-network-thread | content-service-producer-1]  WARN [traceId=NONE,spanId=NONE,userId=NONE] o.apache.kafka.clients.NetworkClient - [Producer clientId=content-service-producer-1] Connection to node 1 (/172.31.46.124:9092) could not be established. Node may not be available.
2026-07-29T12:46:01.793765923Z  [content-service]  2026-07-29 21:46:01.793 [kafka-producer-network-thread | content-service-producer-1]  WARN [traceId=NONE,spanId=NONE,userId=NONE] o.apache.kafka.clients.NetworkClient - [Producer clientId=content-service-producer-1] Connection to node 1 (/172.31.46.124:9092) could not be established. Node may not be available.
2026-07-29T12:46:02.795131286Z  [content-service]  2026-07-29 21:46:02.794 [kafka-producer-network-thread | content-service-producer-1]  WARN [traceId=NONE,spanId=NONE,userId=NONE] o.apache.kafka.clients.NetworkClient - [Producer clientId=content-service-producer-1] Connection to node 1 (/172.31.46.124:9092) could not be established. Node may not be available.
2026-07-29T12:46:03.796820564Z  [content-service]  2026-07-29 21:46:03.796 [kafka-producer-network-thread | content-service-producer-1]  WARN [traceId=NONE,spanId=NONE,userId=NONE] o.apache.kafka.clients.NetworkClient - [Producer clientId=content-service-producer-1] Connection to node 1 (/172.31.46.124:9092) could not be established. Node may not be available.
2026-07-29T12:46:04.797372988Z  [content-service]  2026-07-29 21:46:04.797 [kafka-producer-network-thread | content-service-producer-1]  WARN [traceId=NONE,spanId=NONE,userId=NONE] o.apache.kafka.clients.NetworkClient - [Producer clientId=content-service-producer-1] Connection to node 1 (/172.31.46.124:9092) could not be established. Node may not be available.
2026-07-29T12:46:05.797346375Z  [content-service]  2026-07-29 21:46:05.797 [kafka-producer-network-thread | content-service-producer-1]  WARN [traceId=NONE,spanId=NONE,userId=NONE] o.apache.kafka.clients.NetworkClient - [Producer clientId=content-service-producer-1] Connection to node 1 (/172.31.46.124:9092) could not be established. Node may not be available.
2026-07-29T12:46:06.799443621Z  [content-service]  2026-07-29 21:46:06.799 [kafka-producer-network-thread | content-service-producer-1]  WARN [traceId=NONE,spanId=NONE,userId=NONE] o.apache.kafka.clients.NetworkClient - [Producer clientId=content-service-producer-1] Connection to node 1 (/172.31.46.124:9092) could not be established. Node may not be available.
2026-07-29T12:46:07.801010714Z  [content-service]  2026-07-29 21:46:07.800 [kafka-producer-network-thread | content-service-producer-1]  WARN [traceId=NONE,spanId=NONE,userId=NONE] o.apache.kafka.clients.NetworkClient - [Producer clientId=content-service-producer-1] Connection to node 1 (/172.31.46.124:9092) could not be established. Node may not be available.
2026-07-29T12:46:08.801051344Z  [content-service]  2026-07-29 21:46:08.800 [kafka-producer-network-thread | content-service-producer-1]  WARN [traceId=NONE,spanId=NONE,userId=NONE] o.apache.kafka.clients.NetworkClient - [Producer clientId=content-service-producer-1] Connection to node 1 (/172.31.46.124:9092) could not be established. Node may not be available.
2026-07-29T12:46:09.802101848Z  [content-service]  2026-07-29 21:46:09.801 [kafka-producer-network-thread | content-service-producer-1]  WARN [traceId=NONE,spanId=NONE,userId=NONE] o.apache.kafka.clients.NetworkClient - [Producer clientId=content-service-producer-1] Connection to node 1 (/172.31.46.124:9092) could not be established. Node may not be available.
2026-07-29T12:46:10.802991825Z  [content-service]  2026-07-29 21:46:10.802 [kafka-producer-network-thread | content-service-producer-1]  WARN [traceId=NONE,spanId=NONE,userId=NONE] o.apache.kafka.clients.NetworkClient - [Producer clientId=content-service-producer-1] Connection to node 1 (/172.31.46.124:9092) could not be established. Node may not be available.
2026-07-29T12:46:11.805082425Z  [content-service]  2026-07-29 21:46:11.804 [kafka-producer-network-thread | content-service-producer-1]  WARN [traceId=NONE,spanId=NONE,userId=NONE] o.apache.kafka.clients.NetworkClient - [Producer clientId=content-service-producer-1] Connection to node 1 (/172.31.46.124:9092) could not be established. Node may not be available.
2026-07-29T12:46:12.807049505Z  [content-service]  2026-07-29 21:46:12.806 [kafka-producer-network-thread | content-service-producer-1]  WARN [traceId=NONE,spanId=NONE,userId=NONE] o.apache.kafka.clients.NetworkClient - [Producer clientId=content-service-producer-1] Connection to node 1 (/172.31.46.124:9092) could not be established. Node may not be available.
2026-07-29T12:46:13.808762749Z  [content-service]  2026-07-29 21:46:13.808 [kafka-producer-network-thread | content-service-producer-1]  WARN [traceId=NONE,spanId=NONE,userId=NONE] o.apache.kafka.clients.NetworkClient - [Producer clientId=content-service-producer-1] Connection to node 1 (/172.31.46.124:9092) could not be established. Node may not be available.
2026-07-29T12:46:14.810021119Z  [content-service]  2026-07-29 21:46:14.809 [kafka-producer-network-thread | content-service-producer-1]  WARN [traceId=NONE,spanId=NONE,userId=NONE] o.apache.kafka.clients.NetworkClient - [Producer clientId=content-service-producer-1] Connection to node 1 (/172.31.46.124:9092) could not be established. Node may not be available.
2026-07-29T12:46:15.856843599Z  [content-service]  2026-07-29 21:46:15.856 [kafka-producer-network-thread | content-service-producer-1]  WARN [traceId=NONE,spanId=NONE,userId=NONE] o.apache.kafka.clients.NetworkClient - [Producer clientId=content-service-producer-1] Connection to node 1 (/172.31.46.124:9092) could not be established. Node may not be available.
2026-07-29T12:46:16.857412531Z  [content-service]  2026-07-29 21:46:16.857 [kafka-producer-network-thread | content-service-producer-1]  WARN [traceId=NONE,spanId=NONE,userId=NONE] o.apache.kafka.clients.NetworkClient - [Producer clientId=content-service-producer-1] Connection to node 1 (/172.31.46.124:9092) could not be established. Node may not be available.
2026-07-29T12:46:17.857395752Z  [content-service]  2026-07-29 21:46:17.857 [kafka-producer-network-thread | content-service-producer-1]  WARN [traceId=NONE,spanId=NONE,userId=NONE] o.apache.kafka.clients.NetworkClient - [Producer clientId=content-service-producer-1] Connection to node 1 (/172.31.46.124:9092) could not be established. Node may not be available.
2026-07-29T12:46:18.857695472Z  [content-service]  2026-07-29 21:46:18.857 [kafka-producer-network-thread | content-service-producer-1]  WARN [traceId=NONE,spanId=NONE,userId=NONE] o.apache.kafka.clients.NetworkClient - [Producer clientId=content-service-producer-1] Connection to node 1 (/172.31.46.124:9092) could not be established. Node may not be available.
2026-07-29T12:46:19.821724626Z  [content-service]  2026-07-29 21:46:19.821 [kafka-producer-network-thread | content-service-producer-1]  WARN [traceId=NONE,spanId=NONE,userId=NONE] o.apache.kafka.clients.NetworkClient - [Producer clientId=content-service-producer-1] Connection to node 1 (/172.31.46.124:9092) could not be established. Node may not be available.
2026-07-29T12:46:20.781964360Z  [content-service]  2026-07-29 21:46:20.781 [kafka-producer-network-thread | content-service-producer-1]  WARN [traceId=NONE,spanId=NONE,userId=NONE] o.apache.kafka.clients.NetworkClient - [Producer clientId=content-service-producer-1] Connection to node 1 (/172.31.46.124:9092) could not be established. Node may not be available.
2026-07-29T12:46:21.786608998Z  [content-service]  2026-07-29 21:46:21.786 [kafka-producer-network-thread | content-service-producer-1]  WARN [traceId=NONE,spanId=NONE,userId=NONE] o.apache.kafka.clients.NetworkClient - [Producer clientId=content-service-producer-1] Connection to node 1 (/172.31.46.124:9092) could not be established. Node may not be available.
2026-07-29T12:46:22.740923288Z  [content-service]  2026-07-29 21:46:22.740 [kafka-producer-network-thread | content-service-producer-1]  WARN [traceId=NONE,spanId=NONE,userId=NONE] o.apache.kafka.clients.NetworkClient - [Producer clientId=content-service-producer-1] Connection to node 1 (/172.31.46.124:9092) could not be established. Node may not be available.
2026-07-29T12:46:23.741306806Z  [content-service]  2026-07-29 21:46:23.740 [kafka-producer-network-thread | content-service-producer-1]  WARN [traceId=NONE,spanId=NONE,userId=NONE] o.apache.kafka.clients.NetworkClient - [Producer clientId=content-service-producer-1] Connection to node 1 (/172.31.46.124:9092) could not be established. Node may not be available.
2026-07-29T12:46:24.741446120Z  [content-service]  2026-07-29 21:46:24.741 [kafka-producer-network-thread | content-service-producer-1]  WARN [traceId=NONE,spanId=NONE,userId=NONE] o.apache.kafka.clients.NetworkClient - [Producer clientId=content-service-producer-1] Connection to node 1 (/172.31.46.124:9092) could not be established. Node may not be available.
2026-07-29T12:46:25.761627712Z  [content-service]  2026-07-29 21:46:25.761 [kafka-producer-network-thread | content-service-producer-1]  WARN [traceId=NONE,spanId=NONE,userId=NONE] o.apache.kafka.clients.NetworkClient - [Producer clientId=content-service-producer-1] Connection to node 1 (/172.31.46.124:9092) could not be established. Node may not be available.
2026-07-29T12:46:26.762916441Z  [content-service]  2026-07-29 21:46:26.762 [kafka-producer-network-thread | content-service-producer-1]  WARN [traceId=NONE,spanId=NONE,userId=NONE] o.apache.kafka.clients.NetworkClient - [Producer clientId=content-service-producer-1] Connection to node 1 (/172.31.46.124:9092) could not be established. Node may not be available.
2026-07-29T12:46:27.616243151Z  [content-service]  2026-07-29 21:46:27.616 [kafka-producer-network-thread | content-service-producer-1]  WARN [traceId=NONE,spanId=NONE,userId=NONE] o.apache.kafka.clients.NetworkClient - [Producer clientId=content-service-producer-1] Connection to node 1 (/172.31.46.124:9092) could not be established. Node may not be available.
2026-07-29T12:46:28.465809128Z  [content-service]  2026-07-29 21:46:28.465 [kafka-producer-network-thread | content-service-producer-1]  WARN [traceId=NONE,spanId=NONE,userId=NONE] o.apache.kafka.clients.NetworkClient - [Producer clientId=content-service-producer-1] Connection to node 1 (/172.31.46.124:9092) could not be established. Node may not be available.
2026-07-29T12:46:29.466923101Z  [content-service]  2026-07-29 21:46:29.466 [kafka-producer-network-thread | content-service-producer-1]  WARN [traceId=NONE,spanId=NONE,userId=NONE] o.apache.kafka.clients.NetworkClient - [Producer clientId=content-service-producer-1] Connection to node 1 (/172.31.46.124:9092) could not be established. Node may not be available.
2026-07-29T12:46:30.320528057Z  [content-service]  2026-07-29 21:46:30.320 [kafka-producer-network-thread | content-service-producer-1]  WARN [traceId=NONE,spanId=NONE,userId=NONE] o.apache.kafka.clients.NetworkClient - [Producer clientId=content-service-producer-1] Connection to node 1 (/172.31.46.124:9092) could not be established. Node may not be available.
2026-07-29T12:46:31.324621598Z  [content-service]  2026-07-29 21:46:31.324 [kafka-producer-network-thread | content-service-producer-1]  WARN [traceId=NONE,spanId=NONE,userId=NONE] o.apache.kafka.clients.NetworkClient - [Producer clientId=content-service-producer-1] Connection to node 1 (/172.31.46.124:9092) could not be established. Node may not be available.
2026-07-29T12:46:32.360396551Z  [content-service]  2026-07-29 21:46:32.360 [kafka-producer-network-thread | content-service-producer-1]  WARN [traceId=NONE,spanId=NONE,userId=NONE] o.apache.kafka.clients.NetworkClient - [Producer clientId=content-service-producer-1] Connection to node 1 (/172.31.46.124:9092) could not be established. Node may not be available.
2026-07-29T12:46:33.264445603Z  [content-service]  2026-07-29 21:46:33.263 [kafka-producer-network-thread | content-service-producer-1]  WARN [traceId=NONE,spanId=NONE,userId=NONE] o.apache.kafka.clients.NetworkClient - [Producer clientId=content-service-producer-1] Connection to node 1 (/172.31.46.124:9092) could not be established. Node may not be available.
2026-07-29T12:46:34.267497341Z  [content-service]  2026-07-29 21:46:34.267 [kafka-producer-network-thread | content-service-producer-1]  WARN [traceId=NONE,spanId=NONE,userId=NONE] o.apache.kafka.clients.NetworkClient - [Producer clientId=content-service-producer-1] Connection to node 1 (/172.31.46.124:9092) could not be established. Node may not be available.
2026-07-29T12:46:35.271572881Z  [content-service]  2026-07-29 21:46:35.271 [kafka-producer-network-thread | content-service-producer-1]  WARN [traceId=NONE,spanId=NONE,userId=NONE] o.apache.kafka.clients.NetworkClient - [Producer clientId=content-service-producer-1] Connection to node 1 (/172.31.46.124:9092) could not be established. Node may not be available.
2026-07-29T12:46:36.226392711Z  [content-service]  2026-07-29 21:46:36.226 [kafka-producer-network-thread | content-service-producer-1]  WARN [traceId=NONE,spanId=NONE,userId=NONE] o.apache.kafka.clients.NetworkClient - [Producer clientId=content-service-producer-1] Connection to node 1 (/172.31.46.124:9092) could not be established. Node may not be available.
2026-07-29T12:46:37.079848311Z  [content-service]  2026-07-29 21:46:37.079 [kafka-producer-network-thread | content-service-producer-1]  WARN [traceId=NONE,spanId=NONE,userId=NONE] o.apache.kafka.clients.NetworkClient - [Producer clientId=content-service-producer-1] Connection to node 1 (/172.31.46.124:9092) could not be established. Node may not be available.
2026-07-29T12:46:38.080794599Z  [content-service]  2026-07-29 21:46:38.080 [kafka-producer-network-thread | content-service-producer-1]  WARN [traceId=NONE,spanId=NONE,userId=NONE] o.apache.kafka.clients.NetworkClient - [Producer clientId=content-service-producer-1] Connection to node 1 (/172.31.46.124:9092) could not be established. Node may not be available.
2026-07-29T12:46:39.034468174Z  [content-service]  2026-07-29 21:46:39.034 [kafka-producer-network-thread | content-service-producer-1]  WARN [traceId=NONE,spanId=NONE,userId=NONE] o.apache.kafka.clients.NetworkClient - [Producer clientId=content-service-producer-1] Connection to node 1 (/172.31.46.124:9092) could not be established. Node may not be available.
2026-07-29T12:46:40.038575753Z  [content-service]  2026-07-29 21:46:40.038 [kafka-producer-network-thread | content-service-producer-1]  WARN [traceId=NONE,spanId=NONE,userId=NONE] o.apache.kafka.clients.NetworkClient - [Producer clientId=content-service-producer-1] Connection to node 1 (/172.31.46.124:9092) could not be established. Node may not be available.
2026-07-29T12:46:40.891993590Z  [content-service]  2026-07-29 21:46:40.891 [kafka-producer-network-thread | content-service-producer-1]  WARN [traceId=NONE,spanId=NONE,userId=NONE] o.apache.kafka.clients.NetworkClient - [Producer clientId=content-service-producer-1] Connection to node 1 (/172.31.46.124:9092) could not be established. Node may not be available.
2026-07-29T12:46:41.942109915Z  [content-service]  2026-07-29 21:46:41.941 [kafka-producer-network-thread | content-service-producer-1]  WARN [traceId=NONE,spanId=NONE,userId=NONE] o.apache.kafka.clients.NetworkClient - [Producer clientId=content-service-producer-1] Connection to node 1 (/172.31.46.124:9092) could not be established. Node may not be available.
2026-07-29T12:46:42.896299937Z  [content-service]  2026-07-29 21:46:42.896 [kafka-producer-network-thread | content-service-producer-1]  WARN [traceId=NONE,spanId=NONE,userId=NONE] o.apache.kafka.clients.NetworkClient - [Producer clientId=content-service-producer-1] Connection to node 1 (/172.31.46.124:9092) could not be established. Node may not be available.
2026-07-29T12:48:20.565287303Z  [content-service]  2026-07-29 21:48:20.564 [notification-1] ERROR [traceId=6a69f6581e1b5ce5c855e106cca6260c,spanId=2d10988313439b78,userId=NONE] o.s.k.s.LoggingProducerListener - Exception thrown when sending a message with key='7' and payload='KafkaNotificationDto(userId=7, type=BATTLE_ITEM_COMMENT, title=새 댓글, content=운영자님이 [인생 띵작 애니 베스트] 배틀...' to topic user.notifications:
2026-07-29T12:48:20.567917137Z  [content-service]  2026-07-29 21:48:20.565 [notification-1] ERROR [traceId=6a69f6581e1b5ce5c855e106cca6260c,spanId=7ff5d65ce2edb72f,userId=NONE] c.e.t.a.n.NotificationEventListener - [Notification] 알림 발행 실패: userId=7, type=BATTLE_ITEM_COMMENT, error=Send failed
```

### 메트릭 시계열

| 쿼리 | series | 점 | min | max | last | 값이 0이던 구간 |
|---|---|---:|---:|---:|---:|---|
| `hikaricp_connections_active` | `{__name__=hikaricp_connections_active, application=auth-service, cluster=yogurtte-k3s-prod, container=auth-service, instance=10.42.1.41:8090, job=auth-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=auth-service-855c75679d-vpkqw, pool=HikariPool-1, service=auth-service}` | 49 | 0 | 0 | 0 | **2026-07-29T12:44:00Z ~ 2026-07-29T12:56:00Z** |
| `hikaricp_connections_active` | `{__name__=hikaricp_connections_active, application=chat-service, cluster=yogurtte-k3s-prod, container=chat-service, instance=10.42.1.42:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-857c54dd97-2nzgh, pool=HikariPool-1}` | 49 | 0 | 0 | 0 | **2026-07-29T12:44:00Z ~ 2026-07-29T12:56:00Z** |
| `hikaricp_connections_active` | `{__name__=hikaricp_connections_active, application=content-service, cluster=yogurtte-k3s-prod, container=content-service, instance=10.42.1.35:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-6c5fff897-qnxk6, pool=HikariPool-1}` | 49 | 0 | 1 | 0 | **2026-07-29T12:44:00Z ~ 2026-07-29T12:50:00Z, 2026-07-29T12:51:15Z ~ 2026-07-29T12:53:00Z, 2026-07-29T12:54:15Z ~ 2026-07-29T12:56:00Z** |
| `hikaricp_connections_active` | `{__name__=hikaricp_connections_active, application=content-service, cluster=yogurtte-k3s-prod, container=content-service, instance=10.42.3.41:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-6c5fff897-scw7k, pool=HikariPool-1}` | 49 | 0 | 0 | 0 | **2026-07-29T12:44:00Z ~ 2026-07-29T12:56:00Z** |
| `hikaricp_connections_pending` | `{__name__=hikaricp_connections_pending, application=auth-service, cluster=yogurtte-k3s-prod, container=auth-service, instance=10.42.1.41:8090, job=auth-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=auth-service-855c75679d-vpkqw, pool=HikariPool-1, service=auth-service}` | 49 | 0 | 0 | 0 | **2026-07-29T12:44:00Z ~ 2026-07-29T12:56:00Z** |
| `hikaricp_connections_pending` | `{__name__=hikaricp_connections_pending, application=chat-service, cluster=yogurtte-k3s-prod, container=chat-service, instance=10.42.1.42:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-857c54dd97-2nzgh, pool=HikariPool-1}` | 49 | 0 | 0 | 0 | **2026-07-29T12:44:00Z ~ 2026-07-29T12:56:00Z** |
| `hikaricp_connections_pending` | `{__name__=hikaricp_connections_pending, application=content-service, cluster=yogurtte-k3s-prod, container=content-service, instance=10.42.1.35:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-6c5fff897-qnxk6, pool=HikariPool-1}` | 49 | 0 | 0 | 0 | **2026-07-29T12:44:00Z ~ 2026-07-29T12:56:00Z** |
| `hikaricp_connections_pending` | `{__name__=hikaricp_connections_pending, application=content-service, cluster=yogurtte-k3s-prod, container=content-service, instance=10.42.3.41:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-6c5fff897-scw7k, pool=HikariPool-1}` | 49 | 0 | 0 | 0 | **2026-07-29T12:44:00Z ~ 2026-07-29T12:56:00Z** |
| `rate(jvm_gc_pause_seconds_sum[5m])` | `{action=end of minor GC, application=auth-service, cause=G1 Evacuation Pause, cluster=yogurtte-k3s-prod, container=auth-service, gc=G1 Young Generation, instance=10.42.1.41:8090, job=auth-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=auth-service-855c75679d-vpkqw, service=auth-service}` | 49 | 0 | 0 | 0 | **2026-07-29T12:44:00Z ~ 2026-07-29T12:56:00Z** |
| `rate(jvm_gc_pause_seconds_sum[5m])` | `{action=end of minor GC, application=chat-service, cause=Allocation Failure, cluster=yogurtte-k3s-prod, container=chat-service, gc=Copy, instance=10.42.1.42:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-857c54dd97-2nzgh}` | 49 | 0.000 | 0.001 | 0.000 | — |
| `rate(jvm_gc_pause_seconds_sum[5m])` | `{action=end of minor GC, application=content-service, cause=Allocation Failure, cluster=yogurtte-k3s-prod, container=content-service, gc=Copy, instance=10.42.1.35:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-6c5fff897-qnxk6}` | 49 | 0 | 0.000 | 0 | **2026-07-29T12:44:15Z ~ 2026-07-29T12:49:00Z, 2026-07-29T12:53:15Z ~ 2026-07-29T12:56:00Z** |
| `rate(jvm_gc_pause_seconds_sum[5m])` | `{action=end of minor GC, application=content-service, cause=Allocation Failure, cluster=yogurtte-k3s-prod, container=content-service, gc=Copy, instance=10.42.3.41:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-6c5fff897-scw7k}` | 49 | 0 | 0.000 | 0 | **2026-07-29T12:45:00Z ~ 2026-07-29T12:50:45Z, 2026-07-29T12:55:00Z ~ 2026-07-29T12:56:00Z** |
| `up` | `{__name__=up, app=node-exporter, cluster=yogurtte-k3s-prod, component=metrics, container=node-exporter, instance=ip-172-31-40-241, job=integrations/node_exporter, k8s_cluster_name=yogurtte-k3s-prod, namespace=monitoring, pod=grafana-k8s-monitoring-node-exporter-cph5l, source=kubernetes, workload=DaemonSet/grafana-k8s-monitoring-node-exporter}` | 49 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, app=node-exporter, cluster=yogurtte-k3s-prod, component=metrics, container=node-exporter, instance=ip-172-31-45-39, job=integrations/node_exporter, k8s_cluster_name=yogurtte-k3s-prod, namespace=monitoring, pod=grafana-k8s-monitoring-node-exporter-9kvqt, source=kubernetes, workload=DaemonSet/grafana-k8s-monitoring-node-exporter}` | 49 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, cluster=yogurtte-k3s-prod, container=auth-service, instance=10.42.1.41:8090, job=auth-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=auth-service-855c75679d-vpkqw}` | 49 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, cluster=yogurtte-k3s-prod, container=chat-service, instance=10.42.1.42:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-857c54dd97-2nzgh}` | 49 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, cluster=yogurtte-k3s-prod, container=content-service, instance=10.42.1.35:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-6c5fff897-qnxk6}` | 49 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, cluster=yogurtte-k3s-prod, container=content-service, instance=10.42.3.41:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-6c5fff897-scw7k}` | 49 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, cluster=yogurtte-k3s-prod, instance=10.42.1.248:8080, job=integrations/kubernetes/kube-state-metrics, k8s_cluster_name=yogurtte-k3s-prod, source=kubernetes}` | 49 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, cluster=yogurtte-k3s-prod, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod}` | 49 | 0 | 1 | 1 | **2026-07-29T12:46:00Z ~ 2026-07-29T12:49:15Z** |
| `mongodb_up` | `{__name__=mongodb_up, cluster=yogurtte-k3s-prod, instance=infra-server, job=mongodb, k8s_cluster_name=yogurtte-k3s-prod}` | 49 | 1 | 1 | 1 | — |
| `kafka_brokers` | `{__name__=kafka_brokers, cluster=yogurtte-k3s-prod, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod}` | 35 | 1 | 1 | 1 | — |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=chat-service-fcm-tokens, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=0, topic=user.fcm-tokens}` | 35 | 0 | 0 | 0 | **2026-07-29T12:44:00Z ~ 2026-07-29T12:56:00Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=chat-service-fcm-tokens, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=1, topic=user.fcm-tokens}` | 35 | 0 | 0 | 0 | **2026-07-29T12:44:00Z ~ 2026-07-29T12:56:00Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=chat-service-fcm-tokens, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=2, topic=user.fcm-tokens}` | 35 | 0 | 0 | 0 | **2026-07-29T12:44:00Z ~ 2026-07-29T12:56:00Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=chat-service-notification-settings, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=0, topic=user.notification-settings}` | 35 | 0 | 0 | 0 | **2026-07-29T12:44:00Z ~ 2026-07-29T12:56:00Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=chat-service-notification-settings, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=1, topic=user.notification-settings}` | 35 | 0 | 0 | 0 | **2026-07-29T12:44:00Z ~ 2026-07-29T12:56:00Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=chat-service-notification-settings, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=2, topic=user.notification-settings}` | 35 | 0 | 0 | 0 | **2026-07-29T12:44:00Z ~ 2026-07-29T12:56:00Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=db-writer, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=0, topic=chat.messages}` | 35 | 0 | 0 | 0 | **2026-07-29T12:44:00Z ~ 2026-07-29T12:56:00Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=db-writer, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=1, topic=chat.messages}` | 35 | 0 | 0 | 0 | **2026-07-29T12:44:00Z ~ 2026-07-29T12:56:00Z** |
| `websocket_active_users` | `{__name__=websocket_active_users, application=chat-service, cluster=yogurtte-k3s-prod, container=chat-service, instance=10.42.1.42:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-857c54dd97-2nzgh}` | 49 | 0 | 0 | 0 | **2026-07-29T12:44:00Z ~ 2026-07-29T12:56:00Z** |

값이 0이던 구간은 굵게 표시했다 — 프로세스가 사라져 시계열이 꺾인 것이 유일한 신호인 장애가 있다.

