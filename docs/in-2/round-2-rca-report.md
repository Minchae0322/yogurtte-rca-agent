# RCA Report — `6a699dcb66c56835751eacb7dd98a7e2`

| 항목 | 값 |
|---|---|
| 모드 | rca |
| 질문 | 최근 1시간 안에 댓글 알림이 안 왔다는 제보가 있다. 원인을 조사해줘 |
| 시각 | 2026-07-29T06:35:49.963038300Z |
| provider | claude-cli |
| model | `claude-opus-5` · turns 1 |
| prompt | `.\prompts\system-prompt.md` |
| tokens | in 132347 (cacheRead 23,453 · cacheCreate 108,892) / out 9433 · cost $1.4211 |
| elapsed | total 153476ms (tempo 683 · loki 368 · mimir 945 · assemble 14 · llm 144709) |

## 탐색 (Triage)

| 항목 | 값 |
|---|---|
| 시간창 해석 | 상대 표현 '최근 1시간' |
| 스윕 창 | 2026-07-29T05:34:53.850597700Z ~ 2026-07-29T06:34:53.850597700Z |
| 좁힌 창 | 2026-07-29T06:27:30Z ~ 2026-07-29T06:34:53Z |
| 대상 | content-service, chat-service |
| traceId | 6a699dcb66c56835751eacb7dd98a7e2 |
| 트레이스 후보 | 1건 |
| 계획 파싱 | 성공 |
| prompt | `.\prompts\triage-prompt.md` |
| tokens | in 47697 / out 3389 · cost $0.3554 |
| chars | 컨텍스트 37,662 + 프롬프트 1,231 = **38,893** |
| elapsed | survey 1255ms · llm 54773ms |

**선정 이유**: 댓글 알림 미수신 제보와 시각이 맞는 유일한 이상 구간이 06:29~06:31Z이며, 이 구간에서 Kafka 타깃 다운과 content-service의 60초 타임아웃 에러 스팬·로그 253건이 동시에 발생해 content -> Kafka 발행 단계가 끊긴 것으로 보이므로, 그 앞뒤 여유를 포함한 06:27:30~06:34:53Z 구간의 content-service 발행 로그와 chat-service 소비/발송 로그의 공백을 함께 확인해야 한다.

**근거**

- up{job="kafka", instance="infra-server"} 가 2026-07-29T06:29:53Z 단일 스크레이프에서 1 -> 0, 06:34:53Z에 1로 복귀 (같은 시각 다른 모든 타깃은 1 유지)
- kafka_brokers 시리즈가 06:29:53Z 데이터포인트 자체가 결측 (06:24:53Z -> 06:34:53Z로 건너뜀) — 값이 0이 아니라 수집 자체가 끊김
- kafka_consumergroup_lag 전체 44개 시리즈가 동일하게 06:29:53Z 샘플 결측 — 브로커/익스포터 단위 장애임을 시사
- Tempo: trace 6a699dcb66c56835751eacb7dd98a7e2 가 06:29:31.727Z 시작, durationMs=60004, content-service 스팬 2개 전부 status=error, rootSpan 미수신 — 60초 정각은 Kafka producer max.block.ms/delivery.timeout 만료 패턴
- Loki: content-service ERROR/WARN 이 06:30:00Z 버킷에서 253건 (05:30·05:35 버킷 각 1건 대비 급증), 나머지 버킷은 0건
- chat-service 측 이상 신호 부재 — up=1 유지, notification/notification-processors 컨슈머 그룹 lag 0 고정, websocket_active_users 0 고정. 소비 지연이 아니라 '발행된 이벤트가 없음'에 부합
- auth-service, mongodb_up, node/kubelet/cadvisor 타깃은 전 구간 1 — 클러스터 전반 장애가 아닌 Kafka 경로 국소 장애

**스윕이 찾은 트레이스** (고른 것은 6a699dcb66c56835751eacb7dd98a7e2)

| traceId | root service | root span | ms |
|---|---|---|---:|
| `6a699dcb66c56835751eacb7dd98a7e2` ←선택 | <root span not yet received> | (없음) | 60004 |

## 수집 범위 (Coverage)

- **window**: 2026-07-29T06:27:30Z ~ 2026-07-29T06:34:53Z (443s)
- **trace**: 14,144B / 15 spans
- **logs**: errwarn=147,513B · traceId=8,102B
- **metrics**: 8 수집 / 55,303B, 누락 [sum(rate(http_server_requests_seconds_count{application="content-service", status="401"}[1m]))]
- **context**: 225,441 chars (+ 시스템 프롬프트 597 chars)

### 토큰 축 (개선 지표)

| 단계 | 총 in | chars | 컨텍스트 토큰 |
|---|---:|---:|---:|
| 탐색 | 47,697 | 38,893 | 21,121 |
| 분석 | 132,347 | 226,038 | 105,771 |
| **합계** | **180,044** | | **126,892** |

- **overheadTokens 26,576 tok — 이 회차에 실측**(1자 프롬프트 프로브, 같은 명령·모델·샌드박스).
  호출마다 한 번씩 얹히므로 단계별로 뺀다. **다른 날 상수를 쓰지 않았다 = `█ 실측`.**
- contextTokens (count_tokens API): 측정 안 됨 — 구독 CLI 경로엔 API 키가 없다
- 비용은 **API 환산 추정치**다. 구독 계정으로 호출하므로 실제 청구액이 아니다.

## 수집 실패/누락

- Metric 'sum(rate(http_server_requests_seconds_count{application="content-service", status="401"}[1m]))' returned no series in this window; skipped.

---

## 1. 원인 후보 랭킹 (최대 3개)

1. **Kafka 브로커(172.31.46.124:9092) 단일 노드 다운** → content-service 프로듀서가 `user.notifications` 토픽 메타데이터를 받지 못해 60초 블로킹 후 발행 실패 → 알림 이벤트 유실
2. **브로커 복구 직후 파티션 리더 미선출(LEADER_NOT_AVAILABLE)** 로 인한 발행 불가 구간 연장
3. **발행 실패에 대한 애플리케이션 측 복구 로직 부재**(재시도/아웃박스/DLQ 폴백 없음) → 브로커가 살아난 뒤에도 해당 알림이 영구 유실

---

## 2. 후보별 근거

### 후보 1 — Kafka 브로커 다운

**근거**
- 트레이스 `6a699dcb66c56835751eacb7dd98a7e2`의 producer span `publish user.notifications`(SPAN_KIND_PRODUCER, `messaging.system=kafka`, `messaging.destination.name=user.notifications`)가 `STATUS_CODE_ERROR`, 에러 원문: **"Topic user.notifications not present in metadata after 60000 ms."**
  - span 구간 `1785306571727906000 → 1785306631728902000` = **60.001초** (max.block.ms 기본값 60000ms 소진 패턴)
- 부모 span `notification-publish`(`com.example.toycontent.app.notification.NotificationEventListener`, `error=Send failed`) 역시 **60.004초** 후 ERROR
- Loki WARN 원문(양쪽 파드 모두):
  `[Producer clientId=content-service-producer-1] Connection to node 1 (/172.31.46.124:9092) could not be established. Node may not be available.`
  - 발생 구간: **06:27:59Z ~ 06:31:16Z**(KST 15:27:59~15:31:16), 파드 `content-service-6c5fff897-qnxk6` **및** `-scw7k` 양쪽 → 특정 파드 문제 아님
- 메트릭 `up{job="kafka", instance="infra-server"}`: `1785306495(06:28:15Z)=1` → **`1785306510(06:28:30Z)~1785306675(06:31:15Z)=0`** → `1785306690(06:31:30Z)=1`. 약 **3분간 다운**
- `kafka_brokers`: 다운 구간에 **시계열 자체가 결손**(06:28:15Z 다음 샘플이 06:31:30Z), 값은 항상 **`1`** → **단일 브로커 구성(SPOF)**
- ERROR 로그 원문: `o.s.k.s.LoggingProducerListener - Exception thrown when sending a message with key='7' and payload='KafkaNotificationDto(userId=7, type=BATTLE_ITEM_COMMENT, title=새 댓글, ...)' to topic user.notifications:` (06:30:31Z)

**확신도**: **높음**

**반증 데이터**
- 프로듀서 연결 실패 첫 로그(06:27:59Z)가 `up{job="kafka"}=0` 전환(06:28:30Z)보다 **약 30초 빠름**. 06:28:00Z/06:28:15Z 스크레이프는 여전히 `up=1`. 스크레이프 간격(15s)과 exporter↔브로커 헬스 판정 시점 차이로 설명 가능하지만, 브로커 프로세스 다운 시각을 정확히 06:28:30Z로 단정할 수는 없음.
- 그 외 배치되는 관측값 없음(노드 `ip-172-31-45-39` kubelet/cadvisor `up=1`, content-service `up=1` 유지 → 클라이언트 측/노드 장애가 아님).

---

### 후보 2 — 복구 직후 LEADER_NOT_AVAILABLE

**근거**
- 06:31:17Z부터 로그 패턴이 연결 실패 → 메타데이터 실패로 전환:
  `[Producer clientId=content-service-producer-1] Error while fetching metadata with correlation id 976 : {user.notifications=LEADER_NOT_AVAILABLE}`
  (양쪽 파드, correlation id 976/978/979/980 및 993/994/995, **06:31:17.460Z ~ 06:31:18.261Z**)
- `up{job="kafka"}`가 06:31:30Z에 1로 복귀한 시점과 정합. `kafka_brokers=1`(RF 1 추정)이라 리더 재선출 중 해당 토픽은 발행 불가.

**확신도**: **중간**
- 관측된 LEADER_NOT_AVAILABLE 구간은 **1초 미만**(06:31:17~06:31:18)이고, 이후 조회창(06:34:53Z)까지 동일 에러가 없음 → 짧게 자체 해소된 것으로 보임. 후보 1의 후속 현상이지 독립 원인은 아님.

**반증 데이터**
- 문제의 트레이스(06:29:31Z 발생, 06:30:31Z 실패)는 LEADER_NOT_AVAILABLE 구간(06:31:17Z~) **이전**에 이미 실패했다. 즉 이번 제보 건의 직접 원인은 후보 1이며, 후보 2는 06:31:17Z 전후에 발생한 **다른** 알림에만 해당될 수 있다.

---

### 후보 3 — 발행 실패 후 복구 로직 부재로 유실 확정

**근거**
- 실패 처리 로그가 **단발성 ERROR 1회**로 종료: `c.e.t.a.n.NotificationEventListener - [Notification] 알림 발행 실패: userId=7, type=BATTLE_ITEM_COMMENT, error=Send failed` (06:30:31Z). 동일 traceId로 재시도/재발행/DLQ 적재 로그 없음
- 반면 댓글 본문은 정상 커밋됨: `insert into tb_battle_item_comment ...` `jdbc.row-affected=1`, `generated-keys=173`, `connection` span의 `commit` 이벤트(06:29:31.725Z), HTTP `200`/`60ms` → **본문은 저장, 알림만 유실**(사용자 관점: 댓글은 달렸는데 알림만 안 옴)
- `kafka_consumergroup_lag{topic="user.notifications", consumergroup="notification-processors", partition=0..5}` = 전 구간 **0**(복구 후 06:31:30Z~06:34:45Z 포함) → 브로커 복구 뒤에도 소비할 메시지가 새로 쌓이지 않음 = 해당 이벤트가 뒤늦게라도 발행되지 않았음
- DLQ 인프라는 존재(`user.notifications.dlq`, `notification-recovery` 그룹)하나 lag 전 구간 0
- 부가 리스크: 발행 스레드가 `notification-1` 단일 스레드이고 실패당 **60초** 블로킹 → 3분 장애 동안 최대 3건만 시도 가능. 나머지 이벤트의 처리(큐잉/드롭) 여부는 데이터에 없음

**확신도**: **중간**
- "재시도 로그 없음"과 "lag=0"은 미발행의 **정황 증거**다. lag=0은 "메시지 없음"과 "이미 모두 소비됨"을 구분하지 못하므로, 프로듀서 예외가 최종적이었다는 점(`Send failed` 이후 후속 로그 부재)에 의존한다.

**반증 데이터**
- `user.notifications.dlq` 및 `chat.messages-retry-1000/2000/4000` 토픽과 `notification-retry-*` 컨슈머 그룹이 존재 → **재시도/DLQ 체계가 일부 구현되어 있음**. 즉 "복구 로직 자체가 없다"가 아니라 "이 경로(content→user.notifications)에는 적용되지 않았거나, 브로커 다운이라 DLQ 발행도 동시에 실패했다"일 수 있다. 어느 쪽인지는 현재 데이터로 구분 불가.

---

### 배제된 후보(참고)

- **chat 서비스(소비자) 문제**: `up{job="chat-service"}=1` 전 구간, `hikaricp_connections_pending=0`, GC pause rate ~0.0003s/s 수준으로 안정, `kafka_consumergroup_lag` 전 파티션 0. **소비 지연·소비자 다운 근거 없음**
- **auth/인증 문제**: 트레이스에서 JWT 필터 통과(`before/after JwtAuthenticationFilter`), `userId=1`, `status=200`, 사용자 정보는 `캐시 HIT`로 auth 호출조차 없음. `auth-service` `up=1`, hikari active 0. 단, 401 메트릭은 **수집 실패**라 정량 확인 불가
- **DB/커넥션풀/GC**: content-service `hikaricp_connections_pending=0`, active 최대 1, GC pause 미미. **병목 근거 없음**

---

### 데이터 부족 항목

- **제보 범위(최근 1시간) 대비 조회창이 7분 23초(06:27:30Z~06:34:53Z)에 불과.** 확인된 유실은 `userId=7`, `type=BATTLE_ITEM_COMMENT`, commentId 173 **1건**뿐이며, 나머지 약 52분 구간의 알림 유실 여부는 **데이터 부족**
- 브로커가 왜 다운됐는지(OOM/재시작/네트워크)는 Kafka 브로커 자체 로그가 없어 **판단 불가**
- `notification-processors` 컨슈머 그룹이 chat 서비스 소속인지 확인할 라벨이 없음(다른 그룹은 `chat-service-*` prefix 사용). 소비 주체 매핑 **미확인**
- 수집 실패한 401 메트릭으로 인해 인증 계열 가설은 **정량 반증 불가**(트레이스 단건 근거로만 배제)

---

## 3. 권장 다음 조치

**즉시 (원인 확정)**
1. Kafka 브로커 호스트 `infra-server`(172.31.46.124)에서 06:28:00Z~06:31:30Z 구간의 브로커 로그/재시작 이력 확인 — `journalctl -u kafka --since ...`, `server.log`의 OOM·shutdown·controller 재선출 기록, 프로세스 재시작 시각
2. 토픽 상태 확인: `kafka-topics.sh --describe --topic user.notifications` — 파티션 수(메트릭상 0~5, 6개), **replication factor**, leader/ISR. `kafka_brokers=1`이므로 RF=1이면 브로커 재기동 시마다 동일 장애 재발

**유실 범위 산정·복구**
3. 조회창을 제보 범위(최근 1시간, 예: 06:00Z~07:00Z)로 확장해 재조회:
   - `{job="default/content-service"} |= "알림 발행 실패"` → 유실된 `userId`/`type` 전수 목록화
   - `up{job="kafka"}`, `kafka_brokers` → 다운 구간이 이번 3분 외에 더 있었는지 확인
4. 위 목록 기반으로 알림 **수동 재발행** 또는 `user.notifications`로 재주입. 대상 사용자에게는 댓글 본문이 정상 저장돼 있으므로 데이터 정합성 문제는 없음
5. 반대 방향 검증: `user.notifications`의 파티션별 log-end-offset 증가 여부와 chat 측 발송 로그를 대조해 "발행은 됐는데 발송이 안 된" 경로가 병존하는지 배제

**재발 방지**
6. 프로듀서 설정 검토: `max.block.ms`(현재 60000ms로 추정) 축소 + `NotificationEventListener`의 `notification-1` 단일 스레드 블로킹 완화. 3분 장애 시 스레드당 3건만 시도 가능한 구조
7. content→`user.notifications` 경로에 **아웃박스 패턴 또는 로컬 재시도 큐** 적용(현재 `user.notifications.dlq`가 있으나 이번 실패는 DLQ 발행도 같은 브로커에 의존해 무력화됨)
8. 알럿 추가: `up{job="kafka"} == 0` (for 1m), `kafka_brokers < 1`, content-service의 알림 발행 실패 ERROR 발생률
9. 브로커 이중화(RF≥2, 브로커 2대 이상) 검토 — 현재 `kafka_brokers=1`은 알림 파이프라인 전체의 단일 장애점

**관측 공백 보완**
10. 누락된 메트릭 `sum(rate(http_server_requests_seconds_count{application="content-service", status="401"}[1m]))`의 시리즈 부재 원인 확인(라벨명 `application` vs `job`, 해당 창에 401이 실제로 0건이었는지) — 인증 계열 가설을 정량적으로 닫기 위해 필요

---

## 관측 증거 (Evidence)

> 이 조사가 실제로 본 값이다. 원본 응답 전체는 `reports/raw/6a699dcb66c56835751eacb7dd98a7e2-*.json`에 있다.

### span (duration 상위 15 / 전체 15)

| ms | service | span | 시작 |
|---:|---|---|---|
| 60004.36 | content-service | `notification-publish` | 2026-07-29T06:29:31.727830Z |
| 60001.00 | content-service | `publish user.notifications` | 2026-07-29T06:29:31.727906Z |
| 61.15 | content-service | `http post /battles/{battleId}/items/{itemId}/comments` | 2026-07-29T06:29:31.668069Z |
| 59.57 | content-service | `secured request` | 2026-07-29T06:29:31.668765Z |
| 50.40 | content-service | `connection` | 2026-07-29T06:29:31.677845Z |
| 7.26 | content-service | `query` | 2026-07-29T06:29:31.703697Z |
| 6.38 | content-service | `query` | 2026-07-29T06:29:31.683745Z |
| 3.31 | content-service | `query` | 2026-07-29T06:29:31.716882Z |
| 1.98 | content-service | `query` | 2026-07-29T06:29:31.695493Z |
| 0.69 | content-service | `GET` | 2026-07-29T06:29:31.693806Z |
| 0.47 | content-service | `result-set` | 2026-07-29T06:29:31.690276Z |
| 0.39 | content-service | `result-set` | 2026-07-29T06:29:31.697610Z |
| 0.23 | content-service | `generated-keys` | 2026-07-29T06:29:31.711227Z |
| 0.21 | content-service | `security filterchain before` | 2026-07-29T06:29:31.668535Z |
| 0.09 | content-service | `security filterchain after` | 2026-07-29T06:29:31.728362Z |

### 로그 원문 (60 / 전체 432줄)

전체가 상한을 넘어 ERROR/WARN 줄을 우선 발췌했다. 나머지는 원본 파일에 있다.

```
2026-07-29T06:27:59.654132232Z  [content-service]  2026-07-29 15:27:59.653 [kafka-producer-network-thread | content-service-producer-1]  WARN [traceId=NONE,spanId=NONE,userId=NONE] o.apache.kafka.clients.NetworkClient - [Producer clientId=content-service-producer-1] Connection to node 1 (/172.31.46.124:9092) could not be established. Node may not be available.
2026-07-29T06:27:59.799269543Z  [content-service]  2026-07-29 15:27:59.799 [kafka-producer-network-thread | content-service-producer-1]  WARN [traceId=NONE,spanId=NONE,userId=NONE] o.apache.kafka.clients.NetworkClient - [Producer clientId=content-service-producer-1] Connection to node 1 (/172.31.46.124:9092) could not be established. Node may not be available.
2026-07-29T06:28:00.030706893Z  [content-service]  2026-07-29 15:28:00.030 [kafka-producer-network-thread | content-service-producer-1]  WARN [traceId=NONE,spanId=NONE,userId=NONE] o.apache.kafka.clients.NetworkClient - [Producer clientId=content-service-producer-1] Connection to node 1 (/172.31.46.124:9092) could not be established. Node may not be available.
2026-07-29T06:28:00.428526591Z  [content-service]  2026-07-29 15:28:00.428 [kafka-producer-network-thread | content-service-producer-1]  WARN [traceId=NONE,spanId=NONE,userId=NONE] o.apache.kafka.clients.NetworkClient - [Producer clientId=content-service-producer-1] Connection to node 1 (/172.31.46.124:9092) could not be established. Node may not be available.
2026-07-29T06:28:01.220821358Z  [content-service]  2026-07-29 15:28:01.220 [kafka-producer-network-thread | content-service-producer-1]  WARN [traceId=NONE,spanId=NONE,userId=NONE] o.apache.kafka.clients.NetworkClient - [Producer clientId=content-service-producer-1] Connection to node 1 (/172.31.46.124:9092) could not be established. Node may not be available.
2026-07-29T06:28:02.232455580Z  [content-service]  2026-07-29 15:28:02.232 [kafka-producer-network-thread | content-service-producer-1]  WARN [traceId=NONE,spanId=NONE,userId=NONE] o.apache.kafka.clients.NetworkClient - [Producer clientId=content-service-producer-1] Connection to node 1 (/172.31.46.124:9092) could not be established. Node may not be available.
2026-07-29T06:28:03.234786983Z  [content-service]  2026-07-29 15:28:03.234 [kafka-producer-network-thread | content-service-producer-1]  WARN [traceId=NONE,spanId=NONE,userId=NONE] o.apache.kafka.clients.NetworkClient - [Producer clientId=content-service-producer-1] Connection to node 1 (/172.31.46.124:9092) could not be established. Node may not be available.
2026-07-29T06:28:04.236845595Z  [content-service]  2026-07-29 15:28:04.236 [kafka-producer-network-thread | content-service-producer-1]  WARN [traceId=NONE,spanId=NONE,userId=NONE] o.apache.kafka.clients.NetworkClient - [Producer clientId=content-service-producer-1] Connection to node 1 (/172.31.46.124:9092) could not be established. Node may not be available.
2026-07-29T06:28:05.238040756Z  [content-service]  2026-07-29 15:28:05.237 [kafka-producer-network-thread | content-service-producer-1]  WARN [traceId=NONE,spanId=NONE,userId=NONE] o.apache.kafka.clients.NetworkClient - [Producer clientId=content-service-producer-1] Connection to node 1 (/172.31.46.124:9092) could not be established. Node may not be available.
2026-07-29T06:28:06.238663646Z  [content-service]  2026-07-29 15:28:06.238 [kafka-producer-network-thread | content-service-producer-1]  WARN [traceId=NONE,spanId=NONE,userId=NONE] o.apache.kafka.clients.NetworkClient - [Producer clientId=content-service-producer-1] Connection to node 1 (/172.31.46.124:9092) could not be established. Node may not be available.
2026-07-29T06:28:07.240793591Z  [content-service]  2026-07-29 15:28:07.240 [kafka-producer-network-thread | content-service-producer-1]  WARN [traceId=NONE,spanId=NONE,userId=NONE] o.apache.kafka.clients.NetworkClient - [Producer clientId=content-service-producer-1] Connection to node 1 (/172.31.46.124:9092) could not be established. Node may not be available.
2026-07-29T06:28:08.274210423Z  [content-service]  2026-07-29 15:28:08.274 [kafka-producer-network-thread | content-service-producer-1]  WARN [traceId=NONE,spanId=NONE,userId=NONE] o.apache.kafka.clients.NetworkClient - [Producer clientId=content-service-producer-1] Connection to node 1 (/172.31.46.124:9092) could not be established. Node may not be available.
2026-07-29T06:28:09.274379668Z  [content-service]  2026-07-29 15:28:09.274 [kafka-producer-network-thread | content-service-producer-1]  WARN [traceId=NONE,spanId=NONE,userId=NONE] o.apache.kafka.clients.NetworkClient - [Producer clientId=content-service-producer-1] Connection to node 1 (/172.31.46.124:9092) could not be established. Node may not be available.
2026-07-29T06:28:10.275244519Z  [content-service]  2026-07-29 15:28:10.275 [kafka-producer-network-thread | content-service-producer-1]  WARN [traceId=NONE,spanId=NONE,userId=NONE] o.apache.kafka.clients.NetworkClient - [Producer clientId=content-service-producer-1] Connection to node 1 (/172.31.46.124:9092) could not be established. Node may not be available.
2026-07-29T06:28:11.276039231Z  [content-service]  2026-07-29 15:28:11.275 [kafka-producer-network-thread | content-service-producer-1]  WARN [traceId=NONE,spanId=NONE,userId=NONE] o.apache.kafka.clients.NetworkClient - [Producer clientId=content-service-producer-1] Connection to node 1 (/172.31.46.124:9092) could not be established. Node may not be available.
2026-07-29T06:28:12.276768173Z  [content-service]  2026-07-29 15:28:12.276 [kafka-producer-network-thread | content-service-producer-1]  WARN [traceId=NONE,spanId=NONE,userId=NONE] o.apache.kafka.clients.NetworkClient - [Producer clientId=content-service-producer-1] Connection to node 1 (/172.31.46.124:9092) could not be established. Node may not be available.
2026-07-29T06:28:13.278787901Z  [content-service]  2026-07-29 15:28:13.278 [kafka-producer-network-thread | content-service-producer-1]  WARN [traceId=NONE,spanId=NONE,userId=NONE] o.apache.kafka.clients.NetworkClient - [Producer clientId=content-service-producer-1] Connection to node 1 (/172.31.46.124:9092) could not be established. Node may not be available.
2026-07-29T06:28:14.223774976Z  [content-service]  2026-07-29 15:28:14.223 [kafka-producer-network-thread | content-service-producer-1]  WARN [traceId=NONE,spanId=NONE,userId=NONE] o.apache.kafka.clients.NetworkClient - [Producer clientId=content-service-producer-1] Connection to node 1 (/172.31.46.124:9092) could not be established. Node may not be available.
2026-07-29T06:28:15.225406556Z  [content-service]  2026-07-29 15:28:15.225 [kafka-producer-network-thread | content-service-producer-1]  WARN [traceId=NONE,spanId=NONE,userId=NONE] o.apache.kafka.clients.NetworkClient - [Producer clientId=content-service-producer-1] Connection to node 1 (/172.31.46.124:9092) could not be established. Node may not be available.
2026-07-29T06:28:16.226942353Z  [content-service]  2026-07-29 15:28:16.226 [kafka-producer-network-thread | content-service-producer-1]  WARN [traceId=NONE,spanId=NONE,userId=NONE] o.apache.kafka.clients.NetworkClient - [Producer clientId=content-service-producer-1] Connection to node 1 (/172.31.46.124:9092) could not be established. Node may not be available.
2026-07-29T06:28:17.227273524Z  [content-service]  2026-07-29 15:28:17.227 [kafka-producer-network-thread | content-service-producer-1]  WARN [traceId=NONE,spanId=NONE,userId=NONE] o.apache.kafka.clients.NetworkClient - [Producer clientId=content-service-producer-1] Connection to node 1 (/172.31.46.124:9092) could not be established. Node may not be available.
2026-07-29T06:28:18.227843259Z  [content-service]  2026-07-29 15:28:18.227 [kafka-producer-network-thread | content-service-producer-1]  WARN [traceId=NONE,spanId=NONE,userId=NONE] o.apache.kafka.clients.NetworkClient - [Producer clientId=content-service-producer-1] Connection to node 1 (/172.31.46.124:9092) could not be established. Node may not be available.
2026-07-29T06:28:19.228792286Z  [content-service]  2026-07-29 15:28:19.228 [kafka-producer-network-thread | content-service-producer-1]  WARN [traceId=NONE,spanId=NONE,userId=NONE] o.apache.kafka.clients.NetworkClient - [Producer clientId=content-service-producer-1] Connection to node 1 (/172.31.46.124:9092) could not be established. Node may not be available.
2026-07-29T06:28:20.230005507Z  [content-service]  2026-07-29 15:28:20.229 [kafka-producer-network-thread | content-service-producer-1]  WARN [traceId=NONE,spanId=NONE,userId=NONE] o.apache.kafka.clients.NetworkClient - [Producer clientId=content-service-producer-1] Connection to node 1 (/172.31.46.124:9092) could not be established. Node may not be available.
2026-07-29T06:28:21.232728500Z  [content-service]  2026-07-29 15:28:21.232 [kafka-producer-network-thread | content-service-producer-1]  WARN [traceId=NONE,spanId=NONE,userId=NONE] o.apache.kafka.clients.NetworkClient - [Producer clientId=content-service-producer-1] Connection to node 1 (/172.31.46.124:9092) could not be established. Node may not be available.
2026-07-29T06:28:22.233802941Z  [content-service]  2026-07-29 15:28:22.233 [kafka-producer-network-thread | content-service-producer-1]  WARN [traceId=NONE,spanId=NONE,userId=NONE] o.apache.kafka.clients.NetworkClient - [Producer clientId=content-service-producer-1] Connection to node 1 (/172.31.46.124:9092) could not be established. Node may not be available.
2026-07-29T06:28:23.234923920Z  [content-service]  2026-07-29 15:28:23.234 [kafka-producer-network-thread | content-service-producer-1]  WARN [traceId=NONE,spanId=NONE,userId=NONE] o.apache.kafka.clients.NetworkClient - [Producer clientId=content-service-producer-1] Connection to node 1 (/172.31.46.124:9092) could not be established. Node may not be available.
2026-07-29T06:28:24.236804085Z  [content-service]  2026-07-29 15:28:24.236 [kafka-producer-network-thread | content-service-producer-1]  WARN [traceId=NONE,spanId=NONE,userId=NONE] o.apache.kafka.clients.NetworkClient - [Producer clientId=content-service-producer-1] Connection to node 1 (/172.31.46.124:9092) could not be established. Node may not be available.
2026-07-29T06:28:25.238352050Z  [content-service]  2026-07-29 15:28:25.238 [kafka-producer-network-thread | content-service-producer-1]  WARN [traceId=NONE,spanId=NONE,userId=NONE] o.apache.kafka.clients.NetworkClient - [Producer clientId=content-service-producer-1] Connection to node 1 (/172.31.46.124:9092) could not be established. Node may not be available.
2026-07-29T06:28:26.239049003Z  [content-service]  2026-07-29 15:28:26.238 [kafka-producer-network-thread | content-service-producer-1]  WARN [traceId=NONE,spanId=NONE,userId=NONE] o.apache.kafka.clients.NetworkClient - [Producer clientId=content-service-producer-1] Connection to node 1 (/172.31.46.124:9092) could not be established. Node may not be available.
2026-07-29T06:28:27.241369029Z  [content-service]  2026-07-29 15:28:27.241 [kafka-producer-network-thread | content-service-producer-1]  WARN [traceId=NONE,spanId=NONE,userId=NONE] o.apache.kafka.clients.NetworkClient - [Producer clientId=content-service-producer-1] Connection to node 1 (/172.31.46.124:9092) could not be established. Node may not be available.
2026-07-29T06:28:28.241576040Z  [content-service]  2026-07-29 15:28:28.241 [kafka-producer-network-thread | content-service-producer-1]  WARN [traceId=NONE,spanId=NONE,userId=NONE] o.apache.kafka.clients.NetworkClient - [Producer clientId=content-service-producer-1] Connection to node 1 (/172.31.46.124:9092) could not be established. Node may not be available.
2026-07-29T06:28:29.242913894Z  [content-service]  2026-07-29 15:28:29.242 [kafka-producer-network-thread | content-service-producer-1]  WARN [traceId=NONE,spanId=NONE,userId=NONE] o.apache.kafka.clients.NetworkClient - [Producer clientId=content-service-producer-1] Connection to node 1 (/172.31.46.124:9092) could not be established. Node may not be available.
2026-07-29T06:28:30.205727936Z  [content-service]  2026-07-29 15:28:30.205 [kafka-producer-network-thread | content-service-producer-1]  WARN [traceId=NONE,spanId=NONE,userId=NONE] o.apache.kafka.clients.NetworkClient - [Producer clientId=content-service-producer-1] Connection to node 1 (/172.31.46.124:9092) could not be established. Node may not be available.
2026-07-29T06:28:31.207998312Z  [content-service]  2026-07-29 15:28:31.207 [kafka-producer-network-thread | content-service-producer-1]  WARN [traceId=NONE,spanId=NONE,userId=NONE] o.apache.kafka.clients.NetworkClient - [Producer clientId=content-service-producer-1] Connection to node 1 (/172.31.46.124:9092) could not be established. Node may not be available.
2026-07-29T06:28:32.209991175Z  [content-service]  2026-07-29 15:28:32.209 [kafka-producer-network-thread | content-service-producer-1]  WARN [traceId=NONE,spanId=NONE,userId=NONE] o.apache.kafka.clients.NetworkClient - [Producer clientId=content-service-producer-1] Connection to node 1 (/172.31.46.124:9092) could not be established. Node may not be available.
2026-07-29T06:28:33.211282600Z  [content-service]  2026-07-29 15:28:33.211 [kafka-producer-network-thread | content-service-producer-1]  WARN [traceId=NONE,spanId=NONE,userId=NONE] o.apache.kafka.clients.NetworkClient - [Producer clientId=content-service-producer-1] Connection to node 1 (/172.31.46.124:9092) could not be established. Node may not be available.
2026-07-29T06:28:34.211427484Z  [content-service]  2026-07-29 15:28:34.211 [kafka-producer-network-thread | content-service-producer-1]  WARN [traceId=NONE,spanId=NONE,userId=NONE] o.apache.kafka.clients.NetworkClient - [Producer clientId=content-service-producer-1] Connection to node 1 (/172.31.46.124:9092) could not be established. Node may not be available.
2026-07-29T06:28:35.212513153Z  [content-service]  2026-07-29 15:28:35.212 [kafka-producer-network-thread | content-service-producer-1]  WARN [traceId=NONE,spanId=NONE,userId=NONE] o.apache.kafka.clients.NetworkClient - [Producer clientId=content-service-producer-1] Connection to node 1 (/172.31.46.124:9092) could not be established. Node may not be available.
2026-07-29T06:28:36.213965164Z  [content-service]  2026-07-29 15:28:36.213 [kafka-producer-network-thread | content-service-producer-1]  WARN [traceId=NONE,spanId=NONE,userId=NONE] o.apache.kafka.clients.NetworkClient - [Producer clientId=content-service-producer-1] Connection to node 1 (/172.31.46.124:9092) could not be established. Node may not be available.
2026-07-29T06:28:37.215707895Z  [content-service]  2026-07-29 15:28:37.215 [kafka-producer-network-thread | content-service-producer-1]  WARN [traceId=NONE,spanId=NONE,userId=NONE] o.apache.kafka.clients.NetworkClient - [Producer clientId=content-service-producer-1] Connection to node 1 (/172.31.46.124:9092) could not be established. Node may not be available.
2026-07-29T06:28:38.217892471Z  [content-service]  2026-07-29 15:28:38.217 [kafka-producer-network-thread | content-service-producer-1]  WARN [traceId=NONE,spanId=NONE,userId=NONE] o.apache.kafka.clients.NetworkClient - [Producer clientId=content-service-producer-1] Connection to node 1 (/172.31.46.124:9092) could not be established. Node may not be available.
2026-07-29T06:28:39.204916198Z  [content-service]  2026-07-29 15:28:39.204 [kafka-producer-network-thread | content-service-producer-1]  WARN [traceId=NONE,spanId=NONE,userId=NONE] o.apache.kafka.clients.NetworkClient - [Producer clientId=content-service-producer-1] Connection to node 1 (/172.31.46.124:9092) could not be established. Node may not be available.
2026-07-29T06:28:40.058545695Z  [content-service]  2026-07-29 15:28:40.058 [kafka-producer-network-thread | content-service-producer-1]  WARN [traceId=NONE,spanId=NONE,userId=NONE] o.apache.kafka.clients.NetworkClient - [Producer clientId=content-service-producer-1] Connection to node 1 (/172.31.46.124:9092) could not be established. Node may not be available.
2026-07-29T06:28:41.060008267Z  [content-service]  2026-07-29 15:28:41.059 [kafka-producer-network-thread | content-service-producer-1]  WARN [traceId=NONE,spanId=NONE,userId=NONE] o.apache.kafka.clients.NetworkClient - [Producer clientId=content-service-producer-1] Connection to node 1 (/172.31.46.124:9092) could not be established. Node may not be available.
2026-07-29T06:28:42.060990385Z  [content-service]  2026-07-29 15:28:42.060 [kafka-producer-network-thread | content-service-producer-1]  WARN [traceId=NONE,spanId=NONE,userId=NONE] o.apache.kafka.clients.NetworkClient - [Producer clientId=content-service-producer-1] Connection to node 1 (/172.31.46.124:9092) could not be established. Node may not be available.
2026-07-29T06:28:43.014839505Z  [content-service]  2026-07-29 15:28:43.014 [kafka-producer-network-thread | content-service-producer-1]  WARN [traceId=NONE,spanId=NONE,userId=NONE] o.apache.kafka.clients.NetworkClient - [Producer clientId=content-service-producer-1] Connection to node 1 (/172.31.46.124:9092) could not be established. Node may not be available.
2026-07-29T06:28:44.064943823Z  [content-service]  2026-07-29 15:28:44.064 [kafka-producer-network-thread | content-service-producer-1]  WARN [traceId=NONE,spanId=NONE,userId=NONE] o.apache.kafka.clients.NetworkClient - [Producer clientId=content-service-producer-1] Connection to node 1 (/172.31.46.124:9092) could not be established. Node may not be available.
2026-07-29T06:28:45.065991136Z  [content-service]  2026-07-29 15:28:45.065 [kafka-producer-network-thread | content-service-producer-1]  WARN [traceId=NONE,spanId=NONE,userId=NONE] o.apache.kafka.clients.NetworkClient - [Producer clientId=content-service-producer-1] Connection to node 1 (/172.31.46.124:9092) could not be established. Node may not be available.
2026-07-29T06:28:46.083967863Z  [content-service]  2026-07-29 15:28:46.083 [kafka-producer-network-thread | content-service-producer-1]  WARN [traceId=NONE,spanId=NONE,userId=NONE] o.apache.kafka.clients.NetworkClient - [Producer clientId=content-service-producer-1] Connection to node 1 (/172.31.46.124:9092) could not be established. Node may not be available.
2026-07-29T06:28:46.937264849Z  [content-service]  2026-07-29 15:28:46.937 [kafka-producer-network-thread | content-service-producer-1]  WARN [traceId=NONE,spanId=NONE,userId=NONE] o.apache.kafka.clients.NetworkClient - [Producer clientId=content-service-producer-1] Connection to node 1 (/172.31.46.124:9092) could not be established. Node may not be available.
2026-07-29T06:28:47.877516291Z  [content-service]  2026-07-29 15:28:47.877 [kafka-producer-network-thread | content-service-producer-1]  WARN [traceId=NONE,spanId=NONE,userId=NONE] o.apache.kafka.clients.NetworkClient - [Producer clientId=content-service-producer-1] Connection to node 1 (/172.31.46.124:9092) could not be established. Node may not be available.
2026-07-29T06:28:48.877312935Z  [content-service]  2026-07-29 15:28:48.877 [kafka-producer-network-thread | content-service-producer-1]  WARN [traceId=NONE,spanId=NONE,userId=NONE] o.apache.kafka.clients.NetworkClient - [Producer clientId=content-service-producer-1] Connection to node 1 (/172.31.46.124:9092) could not be established. Node may not be available.
2026-07-29T06:28:49.881455498Z  [content-service]  2026-07-29 15:28:49.881 [kafka-producer-network-thread | content-service-producer-1]  WARN [traceId=NONE,spanId=NONE,userId=NONE] o.apache.kafka.clients.NetworkClient - [Producer clientId=content-service-producer-1] Connection to node 1 (/172.31.46.124:9092) could not be established. Node may not be available.
2026-07-29T06:28:50.881335121Z  [content-service]  2026-07-29 15:28:50.881 [kafka-producer-network-thread | content-service-producer-1]  WARN [traceId=NONE,spanId=NONE,userId=NONE] o.apache.kafka.clients.NetworkClient - [Producer clientId=content-service-producer-1] Connection to node 1 (/172.31.46.124:9092) could not be established. Node may not be available.
2026-07-29T06:28:51.881357346Z  [content-service]  2026-07-29 15:28:51.881 [kafka-producer-network-thread | content-service-producer-1]  WARN [traceId=NONE,spanId=NONE,userId=NONE] o.apache.kafka.clients.NetworkClient - [Producer clientId=content-service-producer-1] Connection to node 1 (/172.31.46.124:9092) could not be established. Node may not be available.
2026-07-29T06:28:52.734549197Z  [content-service]  2026-07-29 15:28:52.734 [kafka-producer-network-thread | content-service-producer-1]  WARN [traceId=NONE,spanId=NONE,userId=NONE] o.apache.kafka.clients.NetworkClient - [Producer clientId=content-service-producer-1] Connection to node 1 (/172.31.46.124:9092) could not be established. Node may not be available.
2026-07-29T06:28:53.784788549Z  [content-service]  2026-07-29 15:28:53.784 [kafka-producer-network-thread | content-service-producer-1]  WARN [traceId=NONE,spanId=NONE,userId=NONE] o.apache.kafka.clients.NetworkClient - [Producer clientId=content-service-producer-1] Connection to node 1 (/172.31.46.124:9092) could not be established. Node may not be available.
2026-07-29T06:30:31.728868837Z  [content-service]  2026-07-29 15:30:31.728 [notification-1] ERROR [traceId=6a699dcb66c56835751eacb7dd98a7e2,spanId=cbe77d7ab22df571,userId=NONE] o.s.k.s.LoggingProducerListener - Exception thrown when sending a message with key='7' and payload='KafkaNotificationDto(userId=7, type=BATTLE_ITEM_COMMENT, title=새 댓글, content=운영자님이 [인생 띵작 애니 베스트] 배틀...' to topic user.notifications:
2026-07-29T06:30:31.732258141Z  [content-service]  2026-07-29 15:30:31.729 [notification-1] ERROR [traceId=6a699dcb66c56835751eacb7dd98a7e2,spanId=e09c1a6664b3e6e4,userId=NONE] c.e.t.a.n.NotificationEventListener - [Notification] 알림 발행 실패: userId=7, type=BATTLE_ITEM_COMMENT, error=Send failed
```

### 메트릭 시계열

| 쿼리 | series | 점 | min | max | last | 값이 0이던 구간 |
|---|---|---:|---:|---:|---:|---|
| `hikaricp_connections_active` | `{__name__=hikaricp_connections_active, application=auth-service, cluster=yogurtte-k3s-prod, container=auth-service, instance=10.42.1.38:8090, job=auth-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=auth-service-855c75679d-pr892, pool=HikariPool-1, service=auth-service}` | 30 | 0 | 0 | 0 | **2026-07-29T06:27:30Z ~ 2026-07-29T06:34:45Z** |
| `hikaricp_connections_active` | `{__name__=hikaricp_connections_active, application=chat-service, cluster=yogurtte-k3s-prod, container=chat-service, instance=10.42.1.39:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-857c54dd97-w7bf7, pool=HikariPool-1}` | 30 | 0 | 1 | 0 | **2026-07-29T06:27:30Z ~ 2026-07-29T06:27:45Z, 2026-07-29T06:29:00Z ~ 2026-07-29T06:34:45Z** |
| `hikaricp_connections_active` | `{__name__=hikaricp_connections_active, application=content-service, cluster=yogurtte-k3s-prod, container=content-service, instance=10.42.1.35:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-6c5fff897-qnxk6, pool=HikariPool-1}` | 30 | 0 | 1 | 0 | **2026-07-29T06:27:30Z ~ 2026-07-29T06:28:00Z, 2026-07-29T06:29:15Z ~ 2026-07-29T06:30:00Z, 2026-07-29T06:31:15Z ~ 2026-07-29T06:32:00Z, 2026-07-29T06:33:15Z ~ 2026-07-29T06:34:45Z** |
| `hikaricp_connections_active` | `{__name__=hikaricp_connections_active, application=content-service, cluster=yogurtte-k3s-prod, container=content-service, instance=10.42.3.41:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-6c5fff897-scw7k, pool=HikariPool-1}` | 30 | 0 | 0 | 0 | **2026-07-29T06:27:30Z ~ 2026-07-29T06:34:45Z** |
| `hikaricp_connections_pending` | `{__name__=hikaricp_connections_pending, application=auth-service, cluster=yogurtte-k3s-prod, container=auth-service, instance=10.42.1.38:8090, job=auth-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=auth-service-855c75679d-pr892, pool=HikariPool-1, service=auth-service}` | 30 | 0 | 0 | 0 | **2026-07-29T06:27:30Z ~ 2026-07-29T06:34:45Z** |
| `hikaricp_connections_pending` | `{__name__=hikaricp_connections_pending, application=chat-service, cluster=yogurtte-k3s-prod, container=chat-service, instance=10.42.1.39:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-857c54dd97-w7bf7, pool=HikariPool-1}` | 30 | 0 | 0 | 0 | **2026-07-29T06:27:30Z ~ 2026-07-29T06:34:45Z** |
| `hikaricp_connections_pending` | `{__name__=hikaricp_connections_pending, application=content-service, cluster=yogurtte-k3s-prod, container=content-service, instance=10.42.1.35:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-6c5fff897-qnxk6, pool=HikariPool-1}` | 30 | 0 | 0 | 0 | **2026-07-29T06:27:30Z ~ 2026-07-29T06:34:45Z** |
| `hikaricp_connections_pending` | `{__name__=hikaricp_connections_pending, application=content-service, cluster=yogurtte-k3s-prod, container=content-service, instance=10.42.3.41:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-6c5fff897-scw7k, pool=HikariPool-1}` | 30 | 0 | 0 | 0 | **2026-07-29T06:27:30Z ~ 2026-07-29T06:34:45Z** |
| `rate(jvm_gc_pause_seconds_sum[5m])` | `{action=end of minor GC, application=auth-service, cause=G1 Evacuation Pause, cluster=yogurtte-k3s-prod, container=auth-service, gc=G1 Young Generation, instance=10.42.1.38:8090, job=auth-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=auth-service-855c75679d-pr892, service=auth-service}` | 30 | 0 | 0 | 0 | **2026-07-29T06:27:30Z ~ 2026-07-29T06:34:45Z** |
| `rate(jvm_gc_pause_seconds_sum[5m])` | `{action=end of minor GC, application=chat-service, cause=Allocation Failure, cluster=yogurtte-k3s-prod, container=chat-service, gc=Copy, instance=10.42.1.39:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-857c54dd97-w7bf7}` | 30 | 0.000 | 0.000 | 0.000 | — |
| `rate(jvm_gc_pause_seconds_sum[5m])` | `{action=end of minor GC, application=content-service, cause=Allocation Failure, cluster=yogurtte-k3s-prod, container=content-service, gc=Copy, instance=10.42.1.35:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-6c5fff897-qnxk6}` | 30 | 0 | 0.000 | 0.000 | **2026-07-29T06:27:30Z ~ 2026-07-29T06:32:00Z** |
| `rate(jvm_gc_pause_seconds_sum[5m])` | `{action=end of minor GC, application=content-service, cause=Allocation Failure, cluster=yogurtte-k3s-prod, container=content-service, gc=Copy, instance=10.42.3.41:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-6c5fff897-scw7k}` | 30 | 0 | 0.000 | 0 | **2026-07-29T06:30:00Z ~ 2026-07-29T06:34:45Z** |
| `up` | `{__name__=up, app=node-exporter, cluster=yogurtte-k3s-prod, component=metrics, container=node-exporter, instance=ip-172-31-40-241, job=integrations/node_exporter, k8s_cluster_name=yogurtte-k3s-prod, namespace=monitoring, pod=grafana-k8s-monitoring-node-exporter-cph5l, source=kubernetes, workload=DaemonSet/grafana-k8s-monitoring-node-exporter}` | 30 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, app=node-exporter, cluster=yogurtte-k3s-prod, component=metrics, container=node-exporter, instance=ip-172-31-45-39, job=integrations/node_exporter, k8s_cluster_name=yogurtte-k3s-prod, namespace=monitoring, pod=grafana-k8s-monitoring-node-exporter-9kvqt, source=kubernetes, workload=DaemonSet/grafana-k8s-monitoring-node-exporter}` | 30 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, cluster=yogurtte-k3s-prod, container=auth-service, instance=10.42.1.38:8090, job=auth-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=auth-service-855c75679d-pr892}` | 30 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, cluster=yogurtte-k3s-prod, container=chat-service, instance=10.42.1.39:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-857c54dd97-w7bf7}` | 30 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, cluster=yogurtte-k3s-prod, container=content-service, instance=10.42.1.35:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-6c5fff897-qnxk6}` | 30 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, cluster=yogurtte-k3s-prod, container=content-service, instance=10.42.3.41:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-6c5fff897-scw7k}` | 30 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, cluster=yogurtte-k3s-prod, instance=10.42.1.248:8080, job=integrations/kubernetes/kube-state-metrics, k8s_cluster_name=yogurtte-k3s-prod, source=kubernetes}` | 30 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, cluster=yogurtte-k3s-prod, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod}` | 30 | 0 | 1 | 1 | **2026-07-29T06:28:30Z ~ 2026-07-29T06:31:15Z** |
| `mongodb_up` | `{__name__=mongodb_up, cluster=yogurtte-k3s-prod, instance=infra-server, job=mongodb, k8s_cluster_name=yogurtte-k3s-prod}` | 30 | 1 | 1 | 1 | — |
| `kafka_brokers` | `{__name__=kafka_brokers, cluster=yogurtte-k3s-prod, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod}` | 18 | 1 | 1 | 1 | — |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=chat-service-fcm-tokens, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=0, topic=user.fcm-tokens}` | 18 | 0 | 0 | 0 | **2026-07-29T06:27:30Z ~ 2026-07-29T06:34:45Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=chat-service-fcm-tokens, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=1, topic=user.fcm-tokens}` | 18 | 0 | 0 | 0 | **2026-07-29T06:27:30Z ~ 2026-07-29T06:34:45Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=chat-service-fcm-tokens, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=2, topic=user.fcm-tokens}` | 18 | 0 | 0 | 0 | **2026-07-29T06:27:30Z ~ 2026-07-29T06:34:45Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=chat-service-notification-settings, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=0, topic=user.notification-settings}` | 18 | 0 | 0 | 0 | **2026-07-29T06:27:30Z ~ 2026-07-29T06:34:45Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=chat-service-notification-settings, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=1, topic=user.notification-settings}` | 18 | 0 | 0 | 0 | **2026-07-29T06:27:30Z ~ 2026-07-29T06:34:45Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=chat-service-notification-settings, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=2, topic=user.notification-settings}` | 18 | 0 | 0 | 0 | **2026-07-29T06:27:30Z ~ 2026-07-29T06:34:45Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=db-writer, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=0, topic=chat.messages}` | 18 | 0 | 0 | 0 | **2026-07-29T06:27:30Z ~ 2026-07-29T06:34:45Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=db-writer, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=1, topic=chat.messages}` | 18 | 0 | 0 | 0 | **2026-07-29T06:27:30Z ~ 2026-07-29T06:34:45Z** |
| `websocket_active_users` | `{__name__=websocket_active_users, application=chat-service, cluster=yogurtte-k3s-prod, container=chat-service, instance=10.42.1.39:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-857c54dd97-w7bf7}` | 30 | 0 | 0 | 0 | **2026-07-29T06:27:30Z ~ 2026-07-29T06:34:45Z** |

값이 0이던 구간은 굵게 표시했다 — 프로세스가 사라져 시계열이 꺾인 것이 유일한 신호인 장애가 있다.

