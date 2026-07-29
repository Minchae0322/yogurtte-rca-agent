> ## 📌 채점자 주석 (2026-07-29 · 아래 리포트 본문은 무수정 원본이다)
>
> **이 리포트가 고른 트레이스는 CH-2 주입 이전 사건의 것이다.**
>
> | | traceId | 시각 | 무엇인가 |
> |---|---|---|---|
> | **선택됨** | `6a6988a1539ec8bf5f46e52f9b611344` | 04:59:13Z | **CH-1**(Mongo 다운) 트리거 — CH-2 주입(05:05:15Z)보다 **6분 앞선다** |
> | **정답이었어야 할 것** | **`6a698a29d0b984ff79dd98467fffe1c0`** | 05:05:45Z | CH-2 한복판. 발행 성공 → **8분 19.6초 공백** → 05:14:05 백로그 소비. **29 spans · 에러 span 0건** |
>
> **에이전트 탓이 아니다.** 스윕의 `trace-query`가 `{ status = error }` 단독이라,
> CH-2 구간(05:05:15~05:15:15Z) 검색 결과가 **0건**이다(채점자 실측). 후보 목록에
> CH-1 트레이스밖에 올라올 수 없었다 → [결함 12 · B-17](../round-3/README.md).
>
> 점수(**100/100**)는 그대로다 — 탐색은 메트릭 채널(`lag` 0→16→25→0 · `websocket_active_users`
> 계열 단절)로 CH-2에 정확히 도달했다. 다만 **"에러 0건 상황의 탐색 능력" 증거로는 인용하지 않는다**:
> 창에 CH-1의 에러 트레이스가 있어 지름길이 존재했고, 그것이 없었어도 같았을지는 이 회차로 확인되지 않는다.
>
> 아래 본문에서 `6a6988a1…`은 전부 위 표의 "선택됨"을 가리킨다. **원문은 고치지 않았다**(§8.2).

---

# RCA Report — `6a6988a1539ec8bf5f46e52f9b611344`

| 항목 | 값 |
|---|---|
| 모드 | rca |
| 질문 | 최근 1시간 안에 채팅 알림이 안 온다는 문의가 여러 건 들어왔다. 원인을 조사해줘 |
| 시각 | 2026-07-29T05:24:33.497165900Z |
| provider | claude-cli |
| model | `claude-opus-5` · turns 1 |
| prompt | `.\prompts\system-prompt.md` |
| tokens | in 168953 (cacheRead 23,453 · cacheCreate 145,498) / out 13501 · cost $1.9176 |
| elapsed | total 203508ms (tempo 514 · loki 368 · mimir 817 · assemble 0 · llm 197374) |

## 탐색 (Triage)

| 항목 | 값 |
|---|---|
| 시간창 해석 | 상대 표현 '최근 1시간' |
| 스윕 창 | 2026-07-29T04:23:30.975874900Z ~ 2026-07-29T05:23:30.975874900Z |
| 좁힌 창 | 2026-07-29T04:57:00Z ~ 2026-07-29T05:21:00Z |
| 대상 | chat-service |
| traceId | 6a6988a1539ec8bf5f46e52f9b611344 |
| 트레이스 후보 | 3건 |
| 계획 파싱 | 성공 |
| prompt | `.\prompts\triage-prompt.md` |
| tokens | in 49157 / out 4321 · cost $0.4216 |
| chars | 컨텍스트 40,542 + 프롬프트 1,231 = **41,773** |
| elapsed | survey 1934ms · llm 60423ms |

**선정 이유**: chat-service 파드가 04:59경 30초 타임아웃과 로그 급증 후 사라져 05:18:30 신규 파드로 교체되기까지 user.notifications 컨슈머 랙이 25까지 쌓였으므로, 그 소실 구간 앞뒤로 여유를 둔 04:57~05:21을 chat-service 중심으로 파고들 필요가 있다.

**근거**

- up{job="chat-service", pod="chat-service-857c54dd97-s5fbl"}가 04:58:30 스크레이프를 마지막으로 소멸, 05:03:30/05:08:30/05:13:30 세 스크레이프 동안 chat-service 타깃 0개 (다른 job은 전부 up=1 유지)
- 05:18:30부터 신규 파드 chat-service-857c54dd97-w7bf7(10.42.1.39)로 up=1 재등장 → 파드 재생성/교체 발생
- websocket_active_users도 동일하게 04:58:30 이후 계열 단절, 05:18:30에 신규 파드로만 재개
- kafka_consumergroup_lag{consumergroup="notification-processors", topic="user.notifications", partition="3"} 0 → 16(05:08:30) → 25(05:13:30) → 0(05:18:30): 발행은 되는데 소비가 멈춤 = 알림 미발송의 직접 지표
- kafka_consumergroup_lag{consumergroup="notification-recovery", topic="user.notifications.dlq", partition="0"}가 05:03:30에 1로 상승 → 처리 실패 메시지 DLQ 적재
- Loki chat-service ERROR/WARN: 05:00:00 18건 → 05:05:00 54건 급증, 05:10:00 버킷 결측(로그 0건), 05:15:00 2건으로 회복
- 에러 트레이스가 모두 30초 정각 타임아웃: content-service→chat-service 스팬 30.015s(05:00:15), 30.002s(05:00:15), 30.016s(05:00:46) / chat-service root 'connection' 30086ms(05:01:17), 30008ms(05:02:47)
- mongodb_up이 05:03:30 스크레이프에서 0(그 외 전 구간 1) — chat-service 정지 시점과 겹치는 의존성 신호
- kafka_brokers=1 및 auth/content-service up=1 전 구간 유지 → 브로커·타 서비스 장애 아님

**스윕이 찾은 트레이스** (고른 것은 6a6988a1539ec8bf5f46e52f9b611344)

| traceId | root service | root span | ms |
|---|---|---|---:|
| `6a6989774b7cb272fdddcf55fc3af6bd` | chat-service | connection | 30008 |
| `6a69891d13bd030e799c1ef73cc0090a` | chat-service | connection | 30086 |
| `6a6988a1539ec8bf5f46e52f9b611344` ←선택 | content-service | http post /battles/{battleId}/items/{itemId}/comments | 2889849755 |

## 수집 범위 (Coverage)

- **window**: 2026-07-29T04:57:00Z ~ 2026-07-29T05:21:00Z (1440s)
- **trace**: 31,044B / 28 spans
- **logs**: errwarn=45,707B · traceId=20,155B
- **metrics**: 8 수집 / 158,881B, 누락 [sum(rate(http_server_requests_seconds_count{application="content-service", status="401"}[1m]))]
- **context**: 256,131 chars (+ 시스템 프롬프트 597 chars)

### 토큰 축 (개선 지표)

| 단계 | 총 in | chars | 컨텍스트 토큰 |
|---|---:|---:|---:|
| 탐색 | 49,157 | 41,773 | 22,581 |
| 분석 | 168,953 | 256,728 | 142,377 |
| **합계** | **218,110** | | **164,958** |

- **overheadTokens 26,576 tok — 이 회차에 실측**(1자 프롬프트 프로브, 같은 명령·모델·샌드박스).
  호출마다 한 번씩 얹히므로 단계별로 뺀다. **다른 날 상수를 쓰지 않았다 = `█ 실측`.**
- contextTokens (count_tokens API): 측정 안 됨 — 구독 CLI 경로엔 API 키가 없다
- 비용은 **API 환산 추정치**다. 구독 계정으로 호출하므로 실제 청구액이 아니다.

## 수집 실패/누락

- Metric 'sum(rate(http_server_requests_seconds_count{application="content-service", status="401"}[1m]))' returned no series in this window; skipped.

---

## 1. 원인 후보 랭킹 (최대 3개)

1. **MongoDB(172.31.46.124:27017) 프로세스 다운** → chat-service의 알림 문서 저장(`user_notifications`) 실패 → Kafka 재시도 소진 후 DLQ 이동 (근본 원인)
2. **mongo 장애에 동반된 chat-service 파드 교체(s5fbl → w7bf7)로 인한 소비 공백** (05:06~05:15 UTC) → `user.notifications` partition 3에 25건 적체 (피해 확대 요인)
3. **Mongo 저장 실패가 알림 발송 경로 전체를 차단하고, DLQ 복구 경로도 동일 Mongo에 의존하는 처리 구조** (장애 지속 시간 증폭 요인)

---

## 2. 후보별 근거

### 후보 1 — MongoDB 프로세스 다운

**근거**

타임라인 (UTC 기준, 괄호는 로그의 KST):

| 시각(UTC) | 관측값 |
|---|---|
| 04:58:12경 | `MongoReactiveHealthIndicator : Mongo health check failed` + `(mongo) took 30004ms to respond` (13:58:42 KST) — 이후 10초 간격으로 반복 |
| 04:58:45 | `mongodb_up` = 1 → **0** (1785301125) |
| 04:59:13.001 | content-service `http post /battles/{battleId}/items/{itemId}/comments` **status=200, outcome=SUCCESS**, `insert into tb_battle_item_comment` 성공(generated-keys=136) |
| 04:59:13.057 | content-service `publish user.notifications` (SPAN_KIND_PRODUCER) **정상 종료** |
| 04:59:13.076 ~ 05:01:16 | chat-service `receive` (partition=3, offset=963) **4회 × 30.01초** 모두 `STATUS_CODE_ERROR` |
| 05:01:16.294 | `publish user.notifications.dlq` + `[KAFKA-DLQ] 발행: user.notifications -> user.notifications.dlq (partition=3 offset=963)` |
| 05:01:47 / 05:03:17 | `[Kafka] DLQ 알림 재처리 실패 (1분 후 재시도): userId=7, type=BATTLE_ITEM_COMMENT` |
| 05:03:45 | `mongodb_up` = **1** 복구 (1785301425) |
| 05:04:30 | `user.notifications.dlq` / `notification-recovery` lag 1 → **0** |

에러 원문(모든 실패 span·로그 동일):

> `com.mongodb.MongoTimeoutException: Timed out while waiting for a server that matches WritableServerSelector. ... {address=172.31.46.124:27017, type=UNKNOWN, state=CONNECTING, exception={com.mongodb.MongoSocketOpenException: Exception opening socket}, caused by {io.netty.channel.AbstractChannel$AnnotatedConnectException: Connection refused: /172.31.46.124:27017}, caused by {java.net.ConnectException: Connection refused}}`

- 실패 직전 span/로그가 정확히 저장 시도임이 확인됨: `o.s.d.mongodb.core.MongoTemplate : Inserting Document containing fields: [userId, type, title, ...] in collection: user_notifications` → 30초 뒤 `user-notification-service#process-notification` (class=`com.example.toychat.app.userNotification.service.UserNotificationService`, method=`processNotification`) 에러.
- **Connection refused**(RST 즉시 반환)는 네트워크 차단이 아니라 **대상 포트에 리스너가 없음**을 의미. 동일 호스트 IP 172.31.46.124의 **redis:6379는 정상**(content-service의 `GET` redis span 0.6ms 성공), `up{job="redis"}`/`up{job="kafka"}`/`up{job="node-infra"}` 모두 1 → 호스트/네트워크 장애가 아닌 **mongod 프로세스 단독 다운**.
- 30.01초 반복은 Mongo 드라이버 `serverSelectionTimeout` 기본 30초와 일치. 1건 처리에 30초×4회 ≈ 124초 동안 partition 3 소비가 정지.

**확신도: 높음**

**반증 데이터**
- `up{job="mongodb"}`(exporter 자체)는 전 구간 1 → mongod만 죽고 exporter 프로세스는 살아있었음. 후보 1과 배치되는 것이 아니라 "호스트 전체 장애" 해석을 반증하는 값.
- 사용자 문의는 "여러 건"인데, 관측 데이터에서 **직접 확인되는 실패 건은 userId=7 / BATTLE_ITEM_COMMENT 1건(offset 963)뿐**. 나머지 문의 건은 후보 2의 적체(25건)로 설명해야 하며, 현재 데이터만으로 총 영향 건수는 확정 불가(**데이터 부족**).

---

### 후보 2 — chat-service 파드 교체로 인한 소비 공백 (05:06~05:15 UTC)

**근거**
- `kafka_consumergroup_lag{consumergroup="notification-processors", topic="user.notifications", partition="3"}`: 05:06:00에 0→2로 상승 후 05:11:00에 **25**까지 증가, 05:15:15까지 25 유지, **05:15:30에 0**으로 급락. 다른 파티션(0,1,2,4,5)은 전 구간 0.
- 동일 시각 chat-service 파드 지표 소실: `up{pod="chat-service-857c54dd97-s5fbl"}`의 **마지막 샘플이 정확히 1785301560(05:06:00)**, 이후 샘플 없음. 신규 파드 `chat-service-857c54dd97-w7bf7`는 05:13:18에 기동 로그(`JpaBaseConfiguration$JpaWebConfiguration : spring.jpa.open-in-view is enabled...`, 14:13:18 KST), `up`은 05:15:00부터 등장.
- 즉 **05:06~05:15 약 9분간 `user.notifications` 소비자가 부재**했고, 그 사이 발행된 25건이 05:15:30에 일괄 소비됨 → 이 구간 사용자에게는 알림이 최대 9분 지연 도착.
- 파드 교체의 유력한 메커니즘(추정): `up{pod=s5fbl}` 시리즈가 **1785301155~1785301440 구간에서 통째로 결손**되는데, 이는 mongo 다운 구간(04:58:45~05:03:30)과 정확히 겹침. 같은 구간 `HealthEndpointSupport : Health contributor ... (mongo) took 30001ms to respond` 가 10초 주기로 반복 → **actuator health가 30초 블로킹되어 readiness/liveness probe 실패 → 파드가 Ready에서 이탈(서비스 디스커버리에서 제외) → 이후 재기동**이라는 흐름과 부합. mongo 복구(05:03:45) 직후인 05:04:15부터 지표가 잠시 복귀한 점도 일치.

**확신도: 중간** (파드 교체·소비 공백이라는 *사실*은 확신도 높음. 그 *원인이 probe 실패*라는 부분은 추정 — pod 이벤트·restart 카운트·probe 설정을 수집하지 못했음)

**반증 데이터**
- 05:06~05:15 구간에는 chat-service의 ERROR/WARN 로그가 전혀 없음(Loki 결과에서 s5fbl의 마지막 로그는 05:03:18, 다음은 신규 파드의 05:13:18). 재기동 사유를 직접 지목하는 로그가 없어, OOMKilled·수동 재배포·노드 스케줄링 등 다른 원인 가능성을 배제하지 못함.
- `jvm_gc_pause`(s5fbl, minor GC ~0.0001s/s), `hikaricp_connections_active/pending`(전 구간 0)에는 자원 포화 징후가 없음 → 부하로 인한 재기동은 지지되지 않음.

---

### 후보 3 — Mongo 저장 실패가 발송 전체를 차단 + DLQ 복구 경로도 동일 Mongo 의존

**근거**
- 트레이스 상 `process-notification` 실패 이후 **FCM/WebSocket 등 발송을 나타내는 span이 하나도 존재하지 않음** → 저장이 발송의 선행 단계이며, 저장 실패 시 발송이 시도조차 되지 않음.
- DLQ 복구 컨슈머도 같은 Mongo에 의존: `[KAFKA-RETRY] user-notification-dlq 처리 실패 1회차/2회차: topic=user.notifications.dlq partition=0 offset=12 cause=com.mongodb.MongoTimeoutException: ... Connection refused: /172.31.46.124:27017` (05:01:47, 05:03:17) → **Mongo가 복구될 때까지 DLQ 재처리도 무한 실패**. 실제로 DLQ lag가 0으로 떨어진 시점(05:04:30)은 mongo 복구(05:03:45) 이후.
- 부수 관측: chat-service 컨슈머가 MySQL 트랜잭션(`HikariPool-1`, `jdbc.datasource.name=content`)을 연 채로 Mongo 호출을 수행 — `connection` span에 `acquired`(04:59:13.078) → **30초 후 `rollback`**(04:59:43.094). 알림 처리 1건마다 MySQL 커넥션을 30초간 점유.
- `websocket_active_users`는 전 구간 0이므로, 실시간 푸시 대상 세션 유무는 이 데이터로 판단 불가.

**확신도: 낮음~중간** (관측된 span/로그 구조에서 도출한 해석이며, 소스 코드·설정을 확인하지 못함)

**반증 데이터**
- 없음. 다만 "저장 성공 후 발송" 순서가 의도된 설계(알림 이력 보존 우선)일 수 있어, 이는 결함이 아니라 트레이드오프일 가능성이 있음.

---

### 배제된 후보 (참고)

- **Kafka 장애**: `kafka_brokers`=1, `up{job="kafka"}`=1 전 구간 유지, content-service의 `publish user.notifications` span 정상, 다른 컨슈머 그룹(`db-writer`, `notification`, `chat-service-fcm-tokens` 등) lag 전부 0 → 배제.
- **MySQL/커넥션 풀 포화**: `hikaricp_connections_active` 최대 1, `hikaricp_connections_pending` 전 서비스·전 구간 0 → 배제.
- **auth/인증(401) 문제**: 요청한 401 메트릭이 **수집 실패(no series)** 하여 직접 검증 불가 → **데이터 부족**. 다만 대상 트레이스의 content-service 요청은 `JwtAuthenticationFilter` 통과 후 200 SUCCESS이고 `up{job="auth-service"}`=1이므로, 이 장애 경로와의 연관은 지지되지 않음.

---

## 3. 권장 다음 조치

**즉시 (원인 확정 + 재발 확인)**
1. `infra-server`(172.31.46.124)에서 mongod 프로세스 상태와 04:58~05:04 UTC 구간 로그 확인 — `journalctl -u mongod --since ...`, `mongod.log`의 shutdown/OOM/스토리지 오류, `dmesg`의 OOM killer 흔적. 재기동이 자동인지 수동인지 확인.
2. `kubectl describe pod chat-service-857c54dd97-s5fbl` / `kubectl get events --sort-by=.lastTimestamp` 로 파드 종료 사유(Probe failed / OOMKilled / Evicted / 재배포) 확정.

**데이터 정합성 복구 확인**
3. Mongo `user_notifications` 컬렉션에서 `userId=7, type=BATTLE_ITEM_COMMENT`(referenceId = battle 22 / item 125 / comment 136) 문서 존재 여부 확인 → DLQ offset 12 재처리가 실제 성공했는지 검증.
4. 05:06~05:15 UTC에 적체됐던 partition 3의 25건이 저장·발송까지 완료됐는지 확인(해당 구간 생성 알림 문서 수 대조). 미발송분이 있으면 재발송 대상 산정.

**재발 방지 (설정)**
5. readiness/liveness probe에서 mongo 헬스 인디케이터 분리 — `management.endpoint.health.group.readiness.include`에서 mongo 제외하거나 probe 경로를 `/actuator/health/liveness`로 분리. 현재는 외부 DB 다운이 곧 파드 축출로 이어지는 구조(후보 2).
6. Mongo 클라이언트 `serverSelectionTimeoutMS`(현재 30초 추정) 단축 + 재시도 백오프 조정. 현행은 1메시지당 최대 124초 파티션 소비 정지.
7. chat-service 알림 컨슈머의 MySQL 트랜잭션 경계 축소 — 외부 호출(Mongo) 구간을 트랜잭션 밖으로 분리(30초 커넥션 점유 → rollback 관측됨).

**관측성 보완 (이번 조사에서 결손된 항목)**
8. 알람 추가: `mongodb_up == 0`, `kafka_consumergroup_lag{topic="user.notifications"} > 0 for 2m`, `kafka_consumergroup_lag{topic="user.notifications.dlq"} > 0`.
9. chat-service Prometheus 미터 등록 실패 수정 — `The meter (spring.kafka.listener ...) registration has failed: ... existing meter named 'spring_kafka_listener_seconds' containing tag keys [application, exception, name, result]` (05:14:28 UTC). 현재 컨슈머 처리 지표가 유실되고 있어 소비 중단 탐지가 지연됨.
10. `kube_pod_container_status_restarts_total`, probe 실패 지표, `http_server_requests_seconds_count{application="content-service"}` 계열(이번에 401 시리즈 미수집) 수집 상태 점검.

> 결론 확신도에 대한 단서: 401 메트릭 미수집과 파드 이벤트/restart 지표 부재로 인해, **후보 1은 높음**으로 유지하되 **후보 2의 재기동 원인**과 **총 영향 건수(문의 "여러 건"의 규모)**는 위 3·4·2번 확인 전까지 확정하지 않았습니다.

---

## 관측 증거 (Evidence)

> 이 조사가 실제로 본 값이다. 원본 응답 전체는 `reports/raw/6a6988a1539ec8bf5f46e52f9b611344-*.json`에 있다.

### span (duration 상위 15 / 전체 28)

| ms | service | span | 시작 |
|---:|---|---|---|
| 30108.17 | chat-service | `receive` | 2026-07-29T04:59:13.076883Z |
| 30021.49 | chat-service | `connection` | 2026-07-29T04:59:13.077489Z |
| 30016.40 | chat-service | `receive` | 2026-07-29T05:00:46.255911Z |
| 30015.27 | chat-service | `receive` | 2026-07-29T04:59:44.214826Z |
| 30015.10 | chat-service | `receive` | 2026-07-29T05:00:15.235167Z |
| 30011.06 | chat-service | `user-notification-service#process-notification` | 2026-07-29T04:59:13.080492Z |
| 30009.10 | chat-service | `connection` | 2026-07-29T05:00:15.235787Z |
| 30008.62 | chat-service | `connection` | 2026-07-29T05:00:46.256337Z |
| 30008.34 | chat-service | `connection` | 2026-07-29T04:59:44.215356Z |
| 30001.92 | chat-service | `user-notification-service#process-notification` | 2026-07-29T05:00:15.239535Z |
| 30001.52 | chat-service | `user-notification-service#process-notification` | 2026-07-29T05:00:46.259864Z |
| 30001.48 | chat-service | `user-notification-service#process-notification` | 2026-07-29T04:59:44.218714Z |
| 1300.55 | chat-service | `publish user.notifications.dlq` | 2026-07-29T05:01:16.294876Z |
| 57.75 | content-service | `http post /battles/{battleId}/items/{itemId}/comments` | 2026-07-29T04:59:13.001604Z |
| 56.22 | content-service | `secured request` | 2026-07-29T04:59:13.002178Z |

### 로그 원문 (60 / 전체 95줄)

전체가 상한을 넘어 ERROR/WARN 줄을 우선 발췌했다. 나머지는 원본 파일에 있다.

```
2026-07-29T04:58:42.249882319Z  [chat-service]  [2m2026-07-29T13:58:42.247+09:00[0;39m [33m WARN [traceId=NONE,spanId=NONE,userId=NONE][0;39m [35m8[0;39m [2m--- [chat-service] [6a379592136967e] [                                                 ] [0;39m[36mo.s.b.a.d.m.MongoReactiveHealthIndicator[0;39m [2m:[0;39m Mongo health check failed
2026-07-29T04:58:42.251600408Z  [chat-service]  [2m2026-07-29T13:58:42.251+09:00[0;39m [33m WARN [traceId=6a69886422402d42f778a1ddd78b4a07,spanId=83e910d75afbbc94,userId=NONE][0;39m [35m8[0;39m [2m--- [chat-service] [nio-8090-exec-3] [6a69886422402d42f778a1ddd78b4a07-83e910d75afbbc94] [0;39m[36mo.s.b.a.health.HealthEndpointSupport    [0;39m [2m:[0;39m Health contributor org.springframework.boot.actuate.autoconfigure.health.HealthEndpointConfiguration$AdaptedReactiveHealthContributors$1 (mongo) took 30004ms to respond
2026-07-29T04:58:52.288748236Z  [chat-service]  [2m2026-07-29T13:58:52.287+09:00[0;39m [33m WARN [traceId=NONE,spanId=NONE,userId=NONE][0;39m [35m8[0;39m [2m--- [chat-service] [6a379592136967e] [                                                 ] [0;39m[36mo.s.b.a.d.m.MongoReactiveHealthIndicator[0;39m [2m:[0;39m Mongo health check failed
2026-07-29T04:58:52.289700974Z  [chat-service]  [2m2026-07-29T13:58:52.288+09:00[0;39m [33m WARN [traceId=6a69886e53c6298605c70d6d8c037090,spanId=2ed4e4b763dca5e1,userId=NONE][0;39m [35m8[0;39m [2m--- [chat-service] [nio-8090-exec-1] [6a69886e53c6298605c70d6d8c037090-2ed4e4b763dca5e1] [0;39m[36mo.s.b.a.health.HealthEndpointSupport    [0;39m [2m:[0;39m Health contributor org.springframework.boot.actuate.autoconfigure.health.HealthEndpointConfiguration$AdaptedReactiveHealthContributors$1 (mongo) took 30001ms to respond
2026-07-29T04:59:02.331098547Z  [chat-service]  [2m2026-07-29T13:59:02.329+09:00[0;39m [33m WARN [traceId=NONE,spanId=NONE,userId=NONE][0;39m [35m8[0;39m [2m--- [chat-service] [6a379592136967e] [                                                 ] [0;39m[36mo.s.b.a.d.m.MongoReactiveHealthIndicator[0;39m [2m:[0;39m Mongo health check failed
2026-07-29T04:59:02.334886998Z  [chat-service]  [2m2026-07-29T13:59:02.331+09:00[0;39m [33m WARN [traceId=6a69887852c032074745b5304b1b3daf,spanId=5c08cccdcd4a2bbf,userId=NONE][0;39m [35m8[0;39m [2m--- [chat-service] [nio-8090-exec-4] [6a69887852c032074745b5304b1b3daf-5c08cccdcd4a2bbf] [0;39m[36mo.s.b.a.health.HealthEndpointSupport    [0;39m [2m:[0;39m Health contributor org.springframework.boot.actuate.autoconfigure.health.HealthEndpointConfiguration$AdaptedReactiveHealthContributors$1 (mongo) took 30002ms to respond
2026-07-29T04:59:12.372577576Z  [chat-service]  [2m2026-07-29T13:59:12.371+09:00[0;39m [33m WARN [traceId=NONE,spanId=NONE,userId=NONE][0;39m [35m8[0;39m [2m--- [chat-service] [6a379592136967e] [                                                 ] [0;39m[36mo.s.b.a.d.m.MongoReactiveHealthIndicator[0;39m [2m:[0;39m Mongo health check failed
2026-07-29T04:59:12.373241076Z  [chat-service]  [2m2026-07-29T13:59:12.372+09:00[0;39m [33m WARN [traceId=6a698882aefaad65506bdeffeeaf1b6a,spanId=69a6f378e61d6b65,userId=NONE][0;39m [35m8[0;39m [2m--- [chat-service] [nio-8090-exec-6] [6a698882aefaad65506bdeffeeaf1b6a-69a6f378e61d6b65] [0;39m[36mo.s.b.a.health.HealthEndpointSupport    [0;39m [2m:[0;39m Health contributor org.springframework.boot.actuate.autoconfigure.health.HealthEndpointConfiguration$AdaptedReactiveHealthContributors$1 (mongo) took 30001ms to respond
2026-07-29T04:59:22.414722877Z  [chat-service]  [2m2026-07-29T13:59:22.412+09:00[0;39m [33m WARN [traceId=NONE,spanId=NONE,userId=NONE][0;39m [35m8[0;39m [2m--- [chat-service] [6a379592136967e] [                                                 ] [0;39m[36mo.s.b.a.d.m.MongoReactiveHealthIndicator[0;39m [2m:[0;39m Mongo health check failed
2026-07-29T04:59:22.415182414Z  [chat-service]  [2m2026-07-29T13:59:22.415+09:00[0;39m [33m WARN [traceId=6a69888c1dfa0354ca597166218ad099,spanId=eeae8a79d891b576,userId=NONE][0;39m [35m8[0;39m [2m--- [chat-service] [nio-8090-exec-7] [6a69888c1dfa0354ca597166218ad099-eeae8a79d891b576] [0;39m[36mo.s.b.a.health.HealthEndpointSupport    [0;39m [2m:[0;39m Health contributor org.springframework.boot.actuate.autoconfigure.health.HealthEndpointConfiguration$AdaptedReactiveHealthContributors$1 (mongo) took 30002ms to respond
2026-07-29T04:59:32.462690705Z  [chat-service]  [2m2026-07-29T13:59:32.460+09:00[0;39m [33m WARN [traceId=NONE,spanId=NONE,userId=NONE][0;39m [35m8[0;39m [2m--- [chat-service] [6a379592136967e] [                                                 ] [0;39m[36mo.s.b.a.d.m.MongoReactiveHealthIndicator[0;39m [2m:[0;39m Mongo health check failed
2026-07-29T04:59:32.462987358Z  [chat-service]  [2m2026-07-29T13:59:32.462+09:00[0;39m [33m WARN [traceId=6a698896ffc8379a518f258d65ec23e7,spanId=d14948e3479bc0ac,userId=NONE][0;39m [35m8[0;39m [2m--- [chat-service] [nio-8090-exec-8] [6a698896ffc8379a518f258d65ec23e7-d14948e3479bc0ac] [0;39m[36mo.s.b.a.health.HealthEndpointSupport    [0;39m [2m:[0;39m Health contributor org.springframework.boot.actuate.autoconfigure.health.HealthEndpointConfiguration$AdaptedReactiveHealthContributors$1 (mongo) took 30001ms to respond
2026-07-29T04:59:42.498177794Z  [chat-service]  [2m2026-07-29T13:59:42.497+09:00[0;39m [33m WARN [traceId=NONE,spanId=NONE,userId=NONE][0;39m [35m8[0;39m [2m--- [chat-service] [6a379592136967e] [                                                 ] [0;39m[36mo.s.b.a.d.m.MongoReactiveHealthIndicator[0;39m [2m:[0;39m Mongo health check failed
2026-07-29T04:59:42.498950209Z  [chat-service]  [2m2026-07-29T13:59:42.498+09:00[0;39m [33m WARN [traceId=6a6988a04b7582bfd8cb9971a3875517,spanId=66632aa7849b0936,userId=NONE][0;39m [35m8[0;39m [2m--- [chat-service] [io-8090-exec-10] [6a6988a04b7582bfd8cb9971a3875517-66632aa7849b0936] [0;39m[36mo.s.b.a.health.HealthEndpointSupport    [0;39m [2m:[0;39m Health contributor org.springframework.boot.actuate.autoconfigure.health.HealthEndpointConfiguration$AdaptedReactiveHealthContributors$1 (mongo) took 30001ms to respond
2026-07-29T04:59:43.180221600Z  [chat-service]  [2m2026-07-29T13:59:43.099+09:00[0;39m [31mERROR [traceId=6a6988a1539ec8bf5f46e52f9b611344,spanId=514d15550aa882e5,userId=NONE][0;39m [35m8[0;39m [2m--- [chat-service] [ntainer#5-1-C-1] [6a6988a1539ec8bf5f46e52f9b611344-514d15550aa882e5] [0;39m[36mc.e.t.a.k.u.UserNotificationConsumer    [0;39m [2m:[0;39m [Kafka] 알림 처리 실패: userId=7, type=BATTLE_ITEM_COMMENT
2026-07-29T04:59:43.200490853Z  [chat-service]  [2m2026-07-29T13:59:43.200+09:00[0;39m [33m WARN [traceId=6a6988a1539ec8bf5f46e52f9b611344,spanId=514d15550aa882e5,userId=NONE][0;39m [35m8[0;39m [2m--- [chat-service] [ntainer#5-1-C-1] [6a6988a1539ec8bf5f46e52f9b611344-514d15550aa882e5] [0;39m[36mc.e.t.app.config.KafkaConsumerConfig    [0;39m [2m:[0;39m [KAFKA-RETRY] user-notification 처리 실패 1회차: topic=user.notifications partition=3 offset=963 cause=com.mongodb.MongoTimeoutException: Timed out while waiting for a server that matches WritableServerSelector. Client view of cluster state is {type=UNKNOWN, servers=[{address=172.31.46.124:27017, type=UNKNOWN, state=CONNECTING, exception={com.mongodb.MongoSocketOpenException: Exception opening socket}, caused by {io.netty.channel.AbstractChannel$AnnotatedConnectException: Connection refused: /172.31.46.124:27017}, caused by {java.net.ConnectException: Connection refused}}]
2026-07-29T04:59:52.542049078Z  [chat-service]  [2m2026-07-29T13:59:52.541+09:00[0;39m [33m WARN [traceId=NONE,spanId=NONE,userId=NONE][0;39m [35m8[0;39m [2m--- [chat-service] [6a379592136967e] [                                                 ] [0;39m[36mo.s.b.a.d.m.MongoReactiveHealthIndicator[0;39m [2m:[0;39m Mongo health check failed
2026-07-29T04:59:52.542465093Z  [chat-service]  [2m2026-07-29T13:59:52.542+09:00[0;39m [33m WARN [traceId=6a6988aaf2db7bc03e1ee4cedf550faa,spanId=55ec5c10c182b669,userId=NONE][0;39m [35m8[0;39m [2m--- [chat-service] [nio-8090-exec-5] [6a6988aaf2db7bc03e1ee4cedf550faa-55ec5c10c182b669] [0;39m[36mo.s.b.a.health.HealthEndpointSupport    [0;39m [2m:[0;39m Health contributor org.springframework.boot.actuate.autoconfigure.health.HealthEndpointConfiguration$AdaptedReactiveHealthContributors$1 (mongo) took 30001ms to respond
2026-07-29T05:00:02.583256787Z  [chat-service]  [2m2026-07-29T14:00:02.581+09:00[0;39m [33m WARN [traceId=NONE,spanId=NONE,userId=NONE][0;39m [35m8[0;39m [2m--- [chat-service] [6a379592136967e] [                                                 ] [0;39m[36mo.s.b.a.d.m.MongoReactiveHealthIndicator[0;39m [2m:[0;39m Mongo health check failed
2026-07-29T05:00:02.585095147Z  [chat-service]  [2m2026-07-29T14:00:02.583+09:00[0;39m [33m WARN [traceId=6a6988b4658056753eda1a742343cd73,spanId=b662cf914726592e,userId=NONE][0;39m [35m8[0;39m [2m--- [chat-service] [nio-8090-exec-3] [6a6988b4658056753eda1a742343cd73-b662cf914726592e] [0;39m[36mo.s.b.a.health.HealthEndpointSupport    [0;39m [2m:[0;39m Health contributor org.springframework.boot.actuate.autoconfigure.health.HealthEndpointConfiguration$AdaptedReactiveHealthContributors$1 (mongo) took 30002ms to respond
2026-07-29T05:00:12.623269808Z  [chat-service]  [2m2026-07-29T14:00:12.622+09:00[0;39m [33m WARN [traceId=NONE,spanId=NONE,userId=NONE][0;39m [35m8[0;39m [2m--- [chat-service] [6a379592136967e] [                                                 ] [0;39m[36mo.s.b.a.d.m.MongoReactiveHealthIndicator[0;39m [2m:[0;39m Mongo health check failed
2026-07-29T05:00:12.623562576Z  [chat-service]  [2m2026-07-29T14:00:12.623+09:00[0;39m [33m WARN [traceId=6a6988befcb321a99b3a1ed366d48a57,spanId=fb4c897887059725,userId=NONE][0;39m [35m8[0;39m [2m--- [chat-service] [nio-8090-exec-4] [6a6988befcb321a99b3a1ed366d48a57-fb4c897887059725] [0;39m[36mo.s.b.a.health.HealthEndpointSupport    [0;39m [2m:[0;39m Health contributor org.springframework.boot.actuate.autoconfigure.health.HealthEndpointConfiguration$AdaptedReactiveHealthContributors$1 (mongo) took 30001ms to respond
2026-07-29T05:00:14.230722714Z  [chat-service]  [2m2026-07-29T14:00:14.223+09:00[0;39m [31mERROR [traceId=6a6988a1539ec8bf5f46e52f9b611344,spanId=390cd2d072a5fe3f,userId=NONE][0;39m [35m8[0;39m [2m--- [chat-service] [ntainer#5-1-C-1] [6a6988a1539ec8bf5f46e52f9b611344-390cd2d072a5fe3f] [0;39m[36mc.e.t.a.k.u.UserNotificationConsumer    [0;39m [2m:[0;39m [Kafka] 알림 처리 실패: userId=7, type=BATTLE_ITEM_COMMENT
2026-07-29T05:00:14.231268635Z  [chat-service]  [2m2026-07-29T14:00:14.230+09:00[0;39m [33m WARN [traceId=6a6988a1539ec8bf5f46e52f9b611344,spanId=390cd2d072a5fe3f,userId=NONE][0;39m [35m8[0;39m [2m--- [chat-service] [ntainer#5-1-C-1] [6a6988a1539ec8bf5f46e52f9b611344-390cd2d072a5fe3f] [0;39m[36mc.e.t.app.config.KafkaConsumerConfig    [0;39m [2m:[0;39m [KAFKA-RETRY] user-notification 처리 실패 2회차: topic=user.notifications partition=3 offset=963 cause=com.mongodb.MongoTimeoutException: Timed out while waiting for a server that matches WritableServerSelector. Client view of cluster state is {type=UNKNOWN, servers=[{address=172.31.46.124:27017, type=UNKNOWN, state=CONNECTING, exception={com.mongodb.MongoSocketOpenException: Exception opening socket}, caused by {io.netty.channel.AbstractChannel$AnnotatedConnectException: Connection refused: /172.31.46.124:27017}, caused by {java.net.ConnectException: Connection refused}}]
2026-07-29T05:00:22.664305738Z  [chat-service]  [2m2026-07-29T14:00:22.663+09:00[0;39m [33m WARN [traceId=NONE,spanId=NONE,userId=NONE][0;39m [35m8[0;39m [2m--- [chat-service] [6a379592136967e] [                                                 ] [0;39m[36mo.s.b.a.d.m.MongoReactiveHealthIndicator[0;39m [2m:[0;39m Mongo health check failed
2026-07-29T05:00:22.664514278Z  [chat-service]  [2m2026-07-29T14:00:22.664+09:00[0;39m [33m WARN [traceId=6a6988c81a359c18526bd367406241a1,spanId=c5546ab06b2d32c2,userId=NONE][0;39m [35m8[0;39m [2m--- [chat-service] [nio-8090-exec-9] [6a6988c81a359c18526bd367406241a1-c5546ab06b2d32c2] [0;39m[36mo.s.b.a.health.HealthEndpointSupport    [0;39m [2m:[0;39m Health contributor org.springframework.boot.actuate.autoconfigure.health.HealthEndpointConfiguration$AdaptedReactiveHealthContributors$1 (mongo) took 30001ms to respond
2026-07-29T05:00:32.714992909Z  [chat-service]  [2m2026-07-29T14:00:32.714+09:00[0;39m [33m WARN [traceId=NONE,spanId=NONE,userId=NONE][0;39m [35m8[0;39m [2m--- [chat-service] [6a379592136967e] [                                                 ] [0;39m[36mo.s.b.a.d.m.MongoReactiveHealthIndicator[0;39m [2m:[0;39m Mongo health check failed
2026-07-29T05:00:32.715456769Z  [chat-service]  [2m2026-07-29T14:00:32.714+09:00[0;39m [33m WARN [traceId=6a6988d2dbdca9a663ad019debe27ce2,spanId=affbc17f2b04a36c,userId=NONE][0;39m [35m8[0;39m [2m--- [chat-service] [nio-8090-exec-6] [6a6988d2dbdca9a663ad019debe27ce2-affbc17f2b04a36c] [0;39m[36mo.s.b.a.health.HealthEndpointSupport    [0;39m [2m:[0;39m Health contributor org.springframework.boot.actuate.autoconfigure.health.HealthEndpointConfiguration$AdaptedReactiveHealthContributors$1 (mongo) took 30001ms to respond
2026-07-29T05:00:42.753019154Z  [chat-service]  [2m2026-07-29T14:00:42.752+09:00[0;39m [33m WARN [traceId=NONE,spanId=NONE,userId=NONE][0;39m [35m8[0;39m [2m--- [chat-service] [6a379592136967e] [                                                 ] [0;39m[36mo.s.b.a.d.m.MongoReactiveHealthIndicator[0;39m [2m:[0;39m Mongo health check failed
2026-07-29T05:00:42.753690217Z  [chat-service]  [2m2026-07-29T14:00:42.752+09:00[0;39m [33m WARN [traceId=6a6988dc97f2e606ef0d5e31615f7248,spanId=910d7fd634926400,userId=NONE][0;39m [35m8[0;39m [2m--- [chat-service] [nio-8090-exec-7] [6a6988dc97f2e606ef0d5e31615f7248-910d7fd634926400] [0;39m[36mo.s.b.a.health.HealthEndpointSupport    [0;39m [2m:[0;39m Health contributor org.springframework.boot.actuate.autoconfigure.health.HealthEndpointConfiguration$AdaptedReactiveHealthContributors$1 (mongo) took 30001ms to respond
2026-07-29T05:00:45.250227896Z  [chat-service]  [2m2026-07-29T14:00:45.244+09:00[0;39m [31mERROR [traceId=6a6988a1539ec8bf5f46e52f9b611344,spanId=11e6b75049064209,userId=NONE][0;39m [35m8[0;39m [2m--- [chat-service] [ntainer#5-1-C-1] [6a6988a1539ec8bf5f46e52f9b611344-11e6b75049064209] [0;39m[36mc.e.t.a.k.u.UserNotificationConsumer    [0;39m [2m:[0;39m [Kafka] 알림 처리 실패: userId=7, type=BATTLE_ITEM_COMMENT
2026-07-29T05:00:45.251038861Z  [chat-service]  [2m2026-07-29T14:00:45.250+09:00[0;39m [33m WARN [traceId=6a6988a1539ec8bf5f46e52f9b611344,spanId=11e6b75049064209,userId=NONE][0;39m [35m8[0;39m [2m--- [chat-service] [ntainer#5-1-C-1] [6a6988a1539ec8bf5f46e52f9b611344-11e6b75049064209] [0;39m[36mc.e.t.app.config.KafkaConsumerConfig    [0;39m [2m:[0;39m [KAFKA-RETRY] user-notification 처리 실패 3회차: topic=user.notifications partition=3 offset=963 cause=com.mongodb.MongoTimeoutException: Timed out while waiting for a server that matches WritableServerSelector. Client view of cluster state is {type=UNKNOWN, servers=[{address=172.31.46.124:27017, type=UNKNOWN, state=CONNECTING, exception={com.mongodb.MongoSocketOpenException: Exception opening socket}, caused by {io.netty.channel.AbstractChannel$AnnotatedConnectException: Connection refused: /172.31.46.124:27017}, caused by {java.net.ConnectException: Connection refused}}]
2026-07-29T05:00:52.794951809Z  [chat-service]  [2m2026-07-29T14:00:52.794+09:00[0;39m [33m WARN [traceId=NONE,spanId=NONE,userId=NONE][0;39m [35m8[0;39m [2m--- [chat-service] [6a379592136967e] [                                                 ] [0;39m[36mo.s.b.a.d.m.MongoReactiveHealthIndicator[0;39m [2m:[0;39m Mongo health check failed
2026-07-29T05:00:52.795113632Z  [chat-service]  [2m2026-07-29T14:00:52.794+09:00[0;39m [33m WARN [traceId=6a6988e6a81de37b25d82ba3b7fe14d9,spanId=4f081a6c0d0f375c,userId=NONE][0;39m [35m8[0;39m [2m--- [chat-service] [nio-8090-exec-8] [6a6988e6a81de37b25d82ba3b7fe14d9-4f081a6c0d0f375c] [0;39m[36mo.s.b.a.health.HealthEndpointSupport    [0;39m [2m:[0;39m Health contributor org.springframework.boot.actuate.autoconfigure.health.HealthEndpointConfiguration$AdaptedReactiveHealthContributors$1 (mongo) took 30000ms to respond
2026-07-29T05:01:02.836609589Z  [chat-service]  [2m2026-07-29T14:01:02.835+09:00[0;39m [33m WARN [traceId=NONE,spanId=NONE,userId=NONE][0;39m [35m8[0;39m [2m--- [chat-service] [6a379592136967e] [                                                 ] [0;39m[36mo.s.b.a.d.m.MongoReactiveHealthIndicator[0;39m [2m:[0;39m Mongo health check failed
2026-07-29T05:01:02.837733129Z  [chat-service]  [2m2026-07-29T14:01:02.836+09:00[0;39m [33m WARN [traceId=6a6988f0836d5882f7b32aea67a9952f,spanId=363a0791e447fc0a,userId=NONE][0;39m [35m8[0;39m [2m--- [chat-service] [nio-8090-exec-1] [6a6988f0836d5882f7b32aea67a9952f-363a0791e447fc0a] [0;39m[36mo.s.b.a.health.HealthEndpointSupport    [0;39m [2m:[0;39m Health contributor org.springframework.boot.actuate.autoconfigure.health.HealthEndpointConfiguration$AdaptedReactiveHealthContributors$1 (mongo) took 30001ms to respond
2026-07-29T05:01:12.876927321Z  [chat-service]  [2m2026-07-29T14:01:12.876+09:00[0;39m [33m WARN [traceId=NONE,spanId=NONE,userId=NONE][0;39m [35m8[0;39m [2m--- [chat-service] [6a379592136967e] [                                                 ] [0;39m[36mo.s.b.a.d.m.MongoReactiveHealthIndicator[0;39m [2m:[0;39m Mongo health check failed
2026-07-29T05:01:12.877450696Z  [chat-service]  [2m2026-07-29T14:01:12.876+09:00[0;39m [33m WARN [traceId=6a6988fa7a0225617b07b911313122dc,spanId=d91c1fecd08133ff,userId=NONE][0;39m [35m8[0;39m [2m--- [chat-service] [nio-8090-exec-5] [6a6988fa7a0225617b07b911313122dc-d91c1fecd08133ff] [0;39m[36mo.s.b.a.health.HealthEndpointSupport    [0;39m [2m:[0;39m Health contributor org.springframework.boot.actuate.autoconfigure.health.HealthEndpointConfiguration$AdaptedReactiveHealthContributors$1 (mongo) took 30001ms to respond
2026-07-29T05:01:16.272133670Z  [chat-service]  [2m2026-07-29T14:01:16.265+09:00[0;39m [31mERROR [traceId=6a6988a1539ec8bf5f46e52f9b611344,spanId=0bcfa126752c485e,userId=NONE][0;39m [35m8[0;39m [2m--- [chat-service] [ntainer#5-1-C-1] [6a6988a1539ec8bf5f46e52f9b611344-0bcfa126752c485e] [0;39m[36mc.e.t.a.k.u.UserNotificationConsumer    [0;39m [2m:[0;39m [Kafka] 알림 처리 실패: userId=7, type=BATTLE_ITEM_COMMENT
2026-07-29T05:01:16.272775919Z  [chat-service]  [2m2026-07-29T14:01:16.272+09:00[0;39m [33m WARN [traceId=6a6988a1539ec8bf5f46e52f9b611344,spanId=0bcfa126752c485e,userId=NONE][0;39m [35m8[0;39m [2m--- [chat-service] [ntainer#5-1-C-1] [6a6988a1539ec8bf5f46e52f9b611344-0bcfa126752c485e] [0;39m[36mc.e.t.app.config.KafkaConsumerConfig    [0;39m [2m:[0;39m [KAFKA-RETRY] user-notification 처리 실패 4회차: topic=user.notifications partition=3 offset=963 cause=com.mongodb.MongoTimeoutException: Timed out while waiting for a server that matches WritableServerSelector. Client view of cluster state is {type=UNKNOWN, servers=[{address=172.31.46.124:27017, type=UNKNOWN, state=CONNECTING, exception={com.mongodb.MongoSocketOpenException: Exception opening socket}, caused by {io.netty.channel.AbstractChannel$AnnotatedConnectException: Connection refused: /172.31.46.124:27017}, caused by {java.net.ConnectException: Connection refused}}]
2026-07-29T05:01:16.273057088Z  [chat-service]  [2m2026-07-29T14:01:16.272+09:00[0;39m [31mERROR [traceId=6a6988a1539ec8bf5f46e52f9b611344,spanId=0bcfa126752c485e,userId=NONE][0;39m [35m8[0;39m [2m--- [chat-service] [ntainer#5-1-C-1] [6a6988a1539ec8bf5f46e52f9b611344-0bcfa126752c485e] [0;39m[36mc.e.t.app.config.KafkaConsumerConfig    [0;39m [2m:[0;39m [KAFKA-DLQ] 발행: user.notifications -> user.notifications.dlq (partition=3 offset=963) cause=com.mongodb.MongoTimeoutException: Timed out while waiting for a server that matches WritableServerSelector. Client view of cluster state is {type=UNKNOWN, servers=[{address=172.31.46.124:27017, type=UNKNOWN, state=CONNECTING, exception={com.mongodb.MongoSocketOpenException: Exception opening socket}, caused by {io.netty.channel.AbstractChannel$AnnotatedConnectException: Connection refused: /172.31.46.124:27017}, caused by {java.net.ConnectException: Connection refused}}]
2026-07-29T05:01:17.596694965Z  [chat-service]  [2m2026-07-29T14:01:17.595+09:00[0;39m [31mERROR [traceId=6a6988a1539ec8bf5f46e52f9b611344,spanId=0bcfa126752c485e,userId=NONE][0;39m [35m8[0;39m [2m--- [chat-service] [ntainer#5-1-C-1] [6a6988a1539ec8bf5f46e52f9b611344-0bcfa126752c485e] [0;39m[36mc.e.t.app.config.KafkaConsumerConfig    [0;39m [2m:[0;39m [KAFKA-RETRY] user-notification 재시도 소진 - recoverer 처리 완료: topic=user.notifications offset=963 cause=com.mongodb.MongoTimeoutException: Timed out while waiting for a server that matches WritableServerSelector. Client view of cluster state is {type=UNKNOWN, servers=[{address=172.31.46.124:27017, type=UNKNOWN, state=CONNECTING, exception={com.mongodb.MongoSocketOpenException: Exception opening socket}, caused by {io.netty.channel.AbstractChannel$AnnotatedConnectException: Connection refused: /172.31.46.124:27017}, caused by {java.net.ConnectException: Connection refused}}]
2026-07-29T05:01:22.920422136Z  [chat-service]  [2m2026-07-29T14:01:22.919+09:00[0;39m [33m WARN [traceId=NONE,spanId=NONE,userId=NONE][0;39m [35m8[0;39m [2m--- [chat-service] [6a379592136967e] [                                                 ] [0;39m[36mo.s.b.a.d.m.MongoReactiveHealthIndicator[0;39m [2m:[0;39m Mongo health check failed
2026-07-29T05:01:22.920986699Z  [chat-service]  [2m2026-07-29T14:01:22.920+09:00[0;39m [33m WARN [traceId=6a698904b50c615215837411fa57718f,spanId=97c1cd9d1922c6b9,userId=NONE][0;39m [35m8[0;39m [2m--- [chat-service] [nio-8090-exec-3] [6a698904b50c615215837411fa57718f-97c1cd9d1922c6b9] [0;39m[36mo.s.b.a.health.HealthEndpointSupport    [0;39m [2m:[0;39m Health contributor org.springframework.boot.actuate.autoconfigure.health.HealthEndpointConfiguration$AdaptedReactiveHealthContributors$1 (mongo) took 30001ms to respond
2026-07-29T05:01:32.963559792Z  [chat-service]  [2m2026-07-29T14:01:32.962+09:00[0;39m [33m WARN [traceId=NONE,spanId=NONE,userId=NONE][0;39m [35m8[0;39m [2m--- [chat-service] [6a379592136967e] [                                                 ] [0;39m[36mo.s.b.a.d.m.MongoReactiveHealthIndicator[0;39m [2m:[0;39m Mongo health check failed
2026-07-29T05:01:32.963784248Z  [chat-service]  [2m2026-07-29T14:01:32.963+09:00[0;39m [33m WARN [traceId=6a69890e47c26c8c92deee32c04de421,spanId=1eb90045a0f646e2,userId=NONE][0;39m [35m8[0;39m [2m--- [chat-service] [nio-8090-exec-2] [6a69890e47c26c8c92deee32c04de421-1eb90045a0f646e2] [0;39m[36mo.s.b.a.health.HealthEndpointSupport    [0;39m [2m:[0;39m Health contributor org.springframework.boot.actuate.autoconfigure.health.HealthEndpointConfiguration$AdaptedReactiveHealthContributors$1 (mongo) took 30001ms to respond
2026-07-29T05:01:43.007606773Z  [chat-service]  [2m2026-07-29T14:01:43.004+09:00[0;39m [33m WARN [traceId=NONE,spanId=NONE,userId=NONE][0;39m [35m8[0;39m [2m--- [chat-service] [6a379592136967e] [                                                 ] [0;39m[36mo.s.b.a.d.m.MongoReactiveHealthIndicator[0;39m [2m:[0;39m Mongo health check failed
2026-07-29T05:01:43.009032271Z  [chat-service]  [2m2026-07-29T14:01:43.008+09:00[0;39m [33m WARN [traceId=6a698918e391c38a634e77532c087b2e,spanId=7139cd1f833d121b,userId=NONE][0;39m [35m8[0;39m [2m--- [chat-service] [nio-8090-exec-9] [6a698918e391c38a634e77532c087b2e-7139cd1f833d121b] [0;39m[36mo.s.b.a.health.HealthEndpointSupport    [0;39m [2m:[0;39m Health contributor org.springframework.boot.actuate.autoconfigure.health.HealthEndpointConfiguration$AdaptedReactiveHealthContributors$1 (mongo) took 30004ms to respond
2026-07-29T05:01:47.595765953Z  [chat-service]  [2m2026-07-29T14:01:47.588+09:00[0;39m [31mERROR [traceId=NONE,spanId=NONE,userId=NONE][0;39m [35m8[0;39m [2m--- [chat-service] [ntainer#6-0-C-1] [                                                 ] [0;39m[36mc.e.t.a.k.u.UserNotificationConsumer    [0;39m [2m:[0;39m [Kafka] DLQ 알림 재처리 실패 (1분 후 재시도): userId=7, type=BATTLE_ITEM_COMMENT
2026-07-29T05:01:47.596569839Z  [chat-service]  [2m2026-07-29T14:01:47.596+09:00[0;39m [33m WARN [traceId=NONE,spanId=NONE,userId=NONE][0;39m [35m8[0;39m [2m--- [chat-service] [ntainer#6-0-C-1] [                                                 ] [0;39m[36mc.e.t.app.config.KafkaConsumerConfig    [0;39m [2m:[0;39m [KAFKA-RETRY] user-notification-dlq 처리 실패 1회차: topic=user.notifications.dlq partition=0 offset=12 cause=com.mongodb.MongoTimeoutException: Timed out while waiting for a server that matches WritableServerSelector. Client view of cluster state is {type=UNKNOWN, servers=[{address=172.31.46.124:27017, type=UNKNOWN, state=CONNECTING, exception={com.mongodb.MongoSocketOpenException: Exception opening socket}, caused by {io.netty.channel.AbstractChannel$AnnotatedConnectException: Connection refused: /172.31.46.124:27017}, caused by {java.net.ConnectException: Connection refused}}]
2026-07-29T05:01:53.045494238Z  [chat-service]  [2m2026-07-29T14:01:53.044+09:00[0;39m [33m WARN [traceId=NONE,spanId=NONE,userId=NONE][0;39m [35m8[0;39m [2m--- [chat-service] [6a379592136967e] [                                                 ] [0;39m[36mo.s.b.a.d.m.MongoReactiveHealthIndicator[0;39m [2m:[0;39m Mongo health check failed
2026-07-29T05:01:53.045816493Z  [chat-service]  [2m2026-07-29T14:01:53.045+09:00[0;39m [33m WARN [traceId=6a698923ad33bb1a42d49b69836028c7,spanId=a9bd8ed8b84b721e,userId=NONE][0;39m [35m8[0;39m [2m--- [chat-service] [nio-8090-exec-6] [6a698923ad33bb1a42d49b69836028c7-a9bd8ed8b84b721e] [0;39m[36mo.s.b.a.health.HealthEndpointSupport    [0;39m [2m:[0;39m Health contributor org.springframework.boot.actuate.autoconfigure.health.HealthEndpointConfiguration$AdaptedReactiveHealthContributors$1 (mongo) took 30001ms to respond
2026-07-29T05:02:03.090354418Z  [chat-service]  [2m2026-07-29T14:02:03.088+09:00[0;39m [33m WARN [traceId=NONE,spanId=NONE,userId=NONE][0;39m [35m8[0;39m [2m--- [chat-service] [6a379592136967e] [                                                 ] [0;39m[36mo.s.b.a.d.m.MongoReactiveHealthIndicator[0;39m [2m:[0;39m Mongo health check failed
2026-07-29T05:02:03.090522092Z  [chat-service]  [2m2026-07-29T14:02:03.090+09:00[0;39m [33m WARN [traceId=6a69892da4952e8cfd38b3bae2cfddfe,spanId=a5a62a0c31d66083,userId=NONE][0;39m [35m8[0;39m [2m--- [chat-service] [io-8090-exec-10] [6a69892da4952e8cfd38b3bae2cfddfe-a5a62a0c31d66083] [0;39m[36mo.s.b.a.health.HealthEndpointSupport    [0;39m [2m:[0;39m Health contributor org.springframework.boot.actuate.autoconfigure.health.HealthEndpointConfiguration$AdaptedReactiveHealthContributors$1 (mongo) took 30001ms to respond
2026-07-29T05:02:13.130532153Z  [chat-service]  [2m2026-07-29T14:02:13.128+09:00[0;39m [33m WARN [traceId=NONE,spanId=NONE,userId=NONE][0;39m [35m8[0;39m [2m--- [chat-service] [6a379592136967e] [                                                 ] [0;39m[36mo.s.b.a.d.m.MongoReactiveHealthIndicator[0;39m [2m:[0;39m Mongo health check failed
2026-07-29T05:02:13.130731573Z  [chat-service]  [2m2026-07-29T14:02:13.130+09:00[0;39m [33m WARN [traceId=6a6989376c26a9ebcd7e4581d330c831,spanId=3a2b85817d6624e6,userId=NONE][0;39m [35m8[0;39m [2m--- [chat-service] [nio-8090-exec-8] [6a6989376c26a9ebcd7e4581d330c831-3a2b85817d6624e6] [0;39m[36mo.s.b.a.health.HealthEndpointSupport    [0;39m [2m:[0;39m Health contributor org.springframework.boot.actuate.autoconfigure.health.HealthEndpointConfiguration$AdaptedReactiveHealthContributors$1 (mongo) took 30001ms to respond
2026-07-29T05:02:23.172091168Z  [chat-service]  [2m2026-07-29T14:02:23.171+09:00[0;39m [33m WARN [traceId=NONE,spanId=NONE,userId=NONE][0;39m [35m8[0;39m [2m--- [chat-service] [6a379592136967e] [                                                 ] [0;39m[36mo.s.b.a.d.m.MongoReactiveHealthIndicator[0;39m [2m:[0;39m Mongo health check failed
2026-07-29T05:02:23.172407794Z  [chat-service]  [2m2026-07-29T14:02:23.172+09:00[0;39m [33m WARN [traceId=6a69894182a2faee1fad2195fd1da1a9,spanId=76f65977965f2e8a,userId=NONE][0;39m [35m8[0;39m [2m--- [chat-service] [nio-8090-exec-1] [6a69894182a2faee1fad2195fd1da1a9-76f65977965f2e8a] [0;39m[36mo.s.b.a.health.HealthEndpointSupport    [0;39m [2m:[0;39m Health contributor org.springframework.boot.actuate.autoconfigure.health.HealthEndpointConfiguration$AdaptedReactiveHealthContributors$1 (mongo) took 30001ms to respond
2026-07-29T05:02:33.214738887Z  [chat-service]  [2m2026-07-29T14:02:33.214+09:00[0;39m [33m WARN [traceId=NONE,spanId=NONE,userId=NONE][0;39m [35m8[0;39m [2m--- [chat-service] [6a379592136967e] [                                                 ] [0;39m[36mo.s.b.a.d.m.MongoReactiveHealthIndicator[0;39m [2m:[0;39m Mongo health check failed
2026-07-29T05:03:17.625481714Z  [chat-service]  [2m2026-07-29T14:03:17.620+09:00[0;39m [31mERROR [traceId=NONE,spanId=NONE,userId=NONE][0;39m [35m8[0;39m [2m--- [chat-service] [ntainer#6-0-C-1] [                                                 ] [0;39m[36mc.e.t.a.k.u.UserNotificationConsumer    [0;39m [2m:[0;39m [Kafka] DLQ 알림 재처리 실패 (1분 후 재시도): userId=7, type=BATTLE_ITEM_COMMENT
```

### 메트릭 시계열

| 쿼리 | series | 점 | min | max | last | 값이 0이던 구간 |
|---|---|---:|---:|---:|---:|---|
| `hikaricp_connections_active` | `{__name__=hikaricp_connections_active, application=auth-service, cluster=yogurtte-k3s-prod, container=auth-service, instance=10.42.1.38:8090, job=auth-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=auth-service-855c75679d-pr892, pool=HikariPool-1, service=auth-service}` | 97 | 0 | 0 | 0 | **2026-07-29T04:57:00Z ~ 2026-07-29T05:21:00Z** |
| `hikaricp_connections_active` | `{__name__=hikaricp_connections_active, application=chat-service, cluster=yogurtte-k3s-prod, container=chat-service, instance=10.42.1.31:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-857c54dd97-s5fbl, pool=HikariPool-1}` | 17 | 0 | 0 | 0 | **2026-07-29T04:57:00Z ~ 2026-07-29T05:06:00Z** |
| `hikaricp_connections_active` | `{__name__=hikaricp_connections_active, application=chat-service, cluster=yogurtte-k3s-prod, container=chat-service, instance=10.42.1.39:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-857c54dd97-w7bf7, pool=HikariPool-1}` | 25 | 0 | 1 | 0 | **2026-07-29T05:16:00Z ~ 2026-07-29T05:21:00Z** |
| `hikaricp_connections_active` | `{__name__=hikaricp_connections_active, application=content-service, cluster=yogurtte-k3s-prod, container=content-service, instance=10.42.1.35:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-6c5fff897-qnxk6, pool=HikariPool-1}` | 97 | 0 | 1 | 0 | **2026-07-29T04:57:00Z ~ 2026-07-29T05:00:00Z, 2026-07-29T05:02:15Z ~ 2026-07-29T05:03:00Z, 2026-07-29T05:04:15Z ~ 2026-07-29T05:10:00Z, 2026-07-29T05:11:15Z ~ 2026-07-29T05:21:00Z** |
| `hikaricp_connections_active` | `{__name__=hikaricp_connections_active, application=content-service, cluster=yogurtte-k3s-prod, container=content-service, instance=10.42.3.41:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-6c5fff897-scw7k, pool=HikariPool-1}` | 97 | 0 | 0 | 0 | **2026-07-29T04:57:00Z ~ 2026-07-29T05:21:00Z** |
| `hikaricp_connections_pending` | `{__name__=hikaricp_connections_pending, application=auth-service, cluster=yogurtte-k3s-prod, container=auth-service, instance=10.42.1.38:8090, job=auth-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=auth-service-855c75679d-pr892, pool=HikariPool-1, service=auth-service}` | 97 | 0 | 0 | 0 | **2026-07-29T04:57:00Z ~ 2026-07-29T05:21:00Z** |
| `hikaricp_connections_pending` | `{__name__=hikaricp_connections_pending, application=chat-service, cluster=yogurtte-k3s-prod, container=chat-service, instance=10.42.1.31:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-857c54dd97-s5fbl, pool=HikariPool-1}` | 17 | 0 | 0 | 0 | **2026-07-29T04:57:00Z ~ 2026-07-29T05:06:00Z** |
| `hikaricp_connections_pending` | `{__name__=hikaricp_connections_pending, application=chat-service, cluster=yogurtte-k3s-prod, container=chat-service, instance=10.42.1.39:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-857c54dd97-w7bf7, pool=HikariPool-1}` | 25 | 0 | 0 | 0 | **2026-07-29T05:15:00Z ~ 2026-07-29T05:21:00Z** |
| `hikaricp_connections_pending` | `{__name__=hikaricp_connections_pending, application=content-service, cluster=yogurtte-k3s-prod, container=content-service, instance=10.42.1.35:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-6c5fff897-qnxk6, pool=HikariPool-1}` | 97 | 0 | 0 | 0 | **2026-07-29T04:57:00Z ~ 2026-07-29T05:21:00Z** |
| `hikaricp_connections_pending` | `{__name__=hikaricp_connections_pending, application=content-service, cluster=yogurtte-k3s-prod, container=content-service, instance=10.42.3.41:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-6c5fff897-scw7k, pool=HikariPool-1}` | 97 | 0 | 0 | 0 | **2026-07-29T04:57:00Z ~ 2026-07-29T05:21:00Z** |
| `rate(jvm_gc_pause_seconds_sum[5m])` | `{action=end of major GC, application=chat-service, cause=Allocation Failure, cluster=yogurtte-k3s-prod, container=chat-service, gc=MarkSweepCompact, instance=10.42.1.31:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-857c54dd97-s5fbl}` | 37 | 0 | 0 | 0 | **2026-07-29T04:57:00Z ~ 2026-07-29T05:09:00Z** |
| `rate(jvm_gc_pause_seconds_sum[5m])` | `{action=end of minor GC, application=auth-service, cause=G1 Evacuation Pause, cluster=yogurtte-k3s-prod, container=auth-service, gc=G1 Young Generation, instance=10.42.1.38:8090, job=auth-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=auth-service-855c75679d-pr892, service=auth-service}` | 97 | 0 | 0.000 | 0.000 | **2026-07-29T04:57:00Z ~ 2026-07-29T05:18:15Z** |
| `rate(jvm_gc_pause_seconds_sum[5m])` | `{action=end of minor GC, application=chat-service, cause=Allocation Failure, cluster=yogurtte-k3s-prod, container=chat-service, gc=Copy, instance=10.42.1.31:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-857c54dd97-s5fbl}` | 37 | 0.000 | 0.000 | 0.000 | — |
| `rate(jvm_gc_pause_seconds_sum[5m])` | `{action=end of minor GC, application=chat-service, cause=Allocation Failure, cluster=yogurtte-k3s-prod, container=chat-service, gc=Copy, instance=10.42.1.39:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-857c54dd97-w7bf7}` | 21 | 0.000 | 0.001 | 0.000 | — |
| `rate(jvm_gc_pause_seconds_sum[5m])` | `{action=end of minor GC, application=content-service, cause=Allocation Failure, cluster=yogurtte-k3s-prod, container=content-service, gc=Copy, instance=10.42.1.35:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-6c5fff897-qnxk6}` | 97 | 0 | 0.000 | 0 | **2026-07-29T04:57:00Z ~ 2026-07-29T04:57:00Z, 2026-07-29T05:01:15Z ~ 2026-07-29T05:07:00Z, 2026-07-29T05:11:15Z ~ 2026-07-29T05:15:00Z, 2026-07-29T05:19:15Z ~ 2026-07-29T05:21:00Z** |
| `rate(jvm_gc_pause_seconds_sum[5m])` | `{action=end of minor GC, application=content-service, cause=Allocation Failure, cluster=yogurtte-k3s-prod, container=content-service, gc=Copy, instance=10.42.3.41:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-6c5fff897-scw7k}` | 97 | 0 | 0.000 | 0.000 | **2026-07-29T05:01:00Z ~ 2026-07-29T05:06:45Z, 2026-07-29T05:11:00Z ~ 2026-07-29T05:17:45Z** |
| `up` | `{__name__=up, app=node-exporter, cluster=yogurtte-k3s-prod, component=metrics, container=node-exporter, instance=ip-172-31-40-241, job=integrations/node_exporter, k8s_cluster_name=yogurtte-k3s-prod, namespace=monitoring, pod=grafana-k8s-monitoring-node-exporter-cph5l, source=kubernetes, workload=DaemonSet/grafana-k8s-monitoring-node-exporter}` | 97 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, app=node-exporter, cluster=yogurtte-k3s-prod, component=metrics, container=node-exporter, instance=ip-172-31-45-39, job=integrations/node_exporter, k8s_cluster_name=yogurtte-k3s-prod, namespace=monitoring, pod=grafana-k8s-monitoring-node-exporter-9kvqt, source=kubernetes, workload=DaemonSet/grafana-k8s-monitoring-node-exporter}` | 97 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, cluster=yogurtte-k3s-prod, container=auth-service, instance=10.42.1.38:8090, job=auth-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=auth-service-855c75679d-pr892}` | 97 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, cluster=yogurtte-k3s-prod, container=chat-service, instance=10.42.1.31:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-857c54dd97-s5fbl}` | 17 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, cluster=yogurtte-k3s-prod, container=chat-service, instance=10.42.1.39:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-857c54dd97-w7bf7}` | 25 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, cluster=yogurtte-k3s-prod, container=content-service, instance=10.42.1.35:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-6c5fff897-qnxk6}` | 97 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, cluster=yogurtte-k3s-prod, container=content-service, instance=10.42.3.41:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-6c5fff897-scw7k}` | 97 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, cluster=yogurtte-k3s-prod, instance=10.42.1.248:8080, job=integrations/kubernetes/kube-state-metrics, k8s_cluster_name=yogurtte-k3s-prod, source=kubernetes}` | 97 | 1 | 1 | 1 | — |
| `mongodb_up` | `{__name__=mongodb_up, cluster=yogurtte-k3s-prod, instance=infra-server, job=mongodb, k8s_cluster_name=yogurtte-k3s-prod}` | 97 | 0 | 1 | 1 | **2026-07-29T04:58:45Z ~ 2026-07-29T05:03:30Z** |
| `kafka_brokers` | `{__name__=kafka_brokers, cluster=yogurtte-k3s-prod, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod}` | 97 | 1 | 1 | 1 | — |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=chat-service-fcm-tokens, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=0, topic=user.fcm-tokens}` | 97 | 0 | 0 | 0 | **2026-07-29T04:57:00Z ~ 2026-07-29T05:21:00Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=chat-service-fcm-tokens, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=1, topic=user.fcm-tokens}` | 97 | 0 | 0 | 0 | **2026-07-29T04:57:00Z ~ 2026-07-29T05:21:00Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=chat-service-fcm-tokens, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=2, topic=user.fcm-tokens}` | 97 | 0 | 0 | 0 | **2026-07-29T04:57:00Z ~ 2026-07-29T05:21:00Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=chat-service-notification-settings, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=0, topic=user.notification-settings}` | 97 | 0 | 0 | 0 | **2026-07-29T04:57:00Z ~ 2026-07-29T05:21:00Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=chat-service-notification-settings, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=1, topic=user.notification-settings}` | 97 | 0 | 0 | 0 | **2026-07-29T04:57:00Z ~ 2026-07-29T05:21:00Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=chat-service-notification-settings, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=2, topic=user.notification-settings}` | 97 | 0 | 0 | 0 | **2026-07-29T04:57:00Z ~ 2026-07-29T05:21:00Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=db-writer, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=0, topic=chat.messages}` | 97 | 0 | 0 | 0 | **2026-07-29T04:57:00Z ~ 2026-07-29T05:21:00Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=db-writer, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=1, topic=chat.messages}` | 97 | 0 | 0 | 0 | **2026-07-29T04:57:00Z ~ 2026-07-29T05:21:00Z** |
| `websocket_active_users` | `{__name__=websocket_active_users, application=chat-service, cluster=yogurtte-k3s-prod, container=chat-service, instance=10.42.1.31:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-857c54dd97-s5fbl}` | 17 | 0 | 0 | 0 | **2026-07-29T04:57:00Z ~ 2026-07-29T05:06:00Z** |
| `websocket_active_users` | `{__name__=websocket_active_users, application=chat-service, cluster=yogurtte-k3s-prod, container=chat-service, instance=10.42.1.39:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-857c54dd97-w7bf7}` | 25 | 0 | 0 | 0 | **2026-07-29T05:15:00Z ~ 2026-07-29T05:21:00Z** |

값이 0이던 구간은 굵게 표시했다 — 프로세스가 사라져 시계열이 꺾인 것이 유일한 신호인 장애가 있다.

