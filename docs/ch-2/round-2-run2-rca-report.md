# RCA Report — `6a69ea2063240c0918077d8cbd8c859c`

| 항목 | 값 |
|---|---|
| 모드 | rca |
| 질문 | 최근 1시간 안에 채팅 알림이 안 온다는 문의가 여러 건 들어왔다. 원인을 조사해줘 |
| 시각 | 2026-07-29T12:22:21.221085Z |
| provider | claude-cli |
| model | `claude-opus-5` · turns 1 |
| prompt | `./prompts/system-prompt.md` |
| tokens | in 182437 (cacheRead 18,133 · cacheCreate 164,302) / out 14262 · cost $2.1370 |
| elapsed | total 212811ms (tempo 928 · loki 285 · mimir 630 · assemble 1 · llm 206908) |

## 탐색 (Triage)

| 항목 | 값 |
|---|---|
| 시간창 해석 | 상대 표현 '최근 1시간' |
| 스윕 창 | 2026-07-29T11:21:11.720230Z ~ 2026-07-29T12:21:11.720230Z |
| 좁힌 창 | 2026-07-29T11:52:00Z ~ 2026-07-29T12:21:11Z |
| 대상 | chat-service, content-service |
| traceId | 6a69ea2063240c0918077d8cbd8c859c |
| 트레이스 후보 | 8건 |
| 계획 파싱 | 성공 |
| prompt | `./prompts/triage-prompt.md` |
| tokens | in 45955 / out 4712 · cost $0.4242 |
| chars | 컨텍스트 44,792 + 프롬프트 1,196 = **45,988** |
| elapsed | survey 1203ms · llm 68293ms |

**선정 이유**: chat-service의 30초 타임아웃 발생(11:55)부터 파드 소실 구간과 notification-processors lag 축적(12:11~12:16)을 거쳐 새 파드 기동으로 lag이 배수되는 12:21까지가 알림 미발송 증상과 시각·경로가 모두 일치하는 유일한 구간이며, 발행 측 영향 확인을 위해 content-service를 함께 포함했다.

**근거**

- chat-service 파드 chat-service-857c54dd97-w7bf7(10.42.1.39)의 up 시계열이 11:56:11에 한 번 결측, 12:06:11 이후 완전히 소멸 — 12:11:11·12:16:11 두 스크레이프 동안 chat-service 인스턴스가 0개
- 대체 파드 chat-service-857c54dd97-2nzgh(10.42.1.42)는 12:21:11 단 한 포인트에만 up=1 — 이 구간 직전 파드 재기동이 있었음을 시사
- kafka_consumergroup_lag{consumergroup="notification-processors", topic="user.notifications", partition="3"}: 12:06:11까지 0 → 12:11:11 15 → 12:16:11 25 → 12:21:11 0. 컨슈머 부재 구간과 정확히 겹치고 새 파드 기동 후 배수됨 (알림 미발송의 직접 증거)
- Loki ERROR/WARN: chat-service 11:55:00 2건 → 12:00:00 66건(33배 급증) → 12:20:00 4건. 같은 시각 content-service·auth-service는 각각 0건으로 조용함
- Tempo: content-service 루트 트레이스 6a69ea2063240c0918077d8cbd8c859c(11:56:45 시작, 31,115ms)에서 chat-service 스팬 16개 중 8개 error, 개별 스팬 30,014ms/30,281ms/30,002ms로 30초 타임아웃 패턴 — content→chat 발행 경로가 블로킹됨
- Tempo: chat-service 루트 트레이스 6a69ea9d5c9fd31ded195762282d7806(11:57:17, 30,097ms, connection) — 커넥션 획득 단계에서 30초 대기 후 error
- mongodb_up이 11:56:11 스크레이프에서 0 (그 외 전 구간 1). chat-service 30초 타임아웃 시작 시각과 일치 — 선행 트리거 후보
- 12:11:11~12:16:11 구간은 chat-service 에러 트레이스가 0건이지만 이는 정상이 아니라 프로세스 부재로 트레이스 자체가 생성되지 않은 것 (같은 시각 up 결측 + lag 증가가 이를 뒷받침)
- kafka_brokers는 전 구간 1로 유지되고 다른 컨슈머그룹(db-writer, notification, chat-service-fcm-tokens) lag은 전부 0 — 브로커/카프카 전반 문제가 아니라 chat-service 소비자 측 국소 장애

**스윕이 찾은 트레이스** (고른 것은 6a69ea2063240c0918077d8cbd8c859c)

| traceId | root service | root span | ms |
|---|---|---|---:|
| `6a69ea9d5c9fd31ded195762282d7806` | chat-service | connection | 30097 |
| `6a69ea2063240c0918077d8cbd8c859c` ←선택 | content-service | http post /battles/{battleId}/items/{itemId}/comments | 31115 |
| `6a69e7fe1224083c021ae372bcec4cc0` | content-service | http get /feeds/scroll | 58 |
| `6a69e7fb0835ef66de170f1278643577` | content-service | http get /feeds/{feedId} | 161 |
| `6a69e7f8ab06633ddcb6c22ae3c931e8` | content-service | http get /feeds/scroll | 88 |
| `6a69e7f6088fd6e26b28ab66596dc999` | content-service | http get /feeds/scroll | 111 |
| `6a69e7f00e63c377af1b51ebea9ca59d` | content-service | http get /feeds/scroll | 62 |
| `6a69e3aacb0cb1b86516674f2cd52e45` | content-service | http get /feeds/following | 3 |

## 수집 범위 (Coverage)

- **window**: 2026-07-29T11:52:00Z ~ 2026-07-29T12:21:11Z (1751s)
- **trace**: 30,369B / 28 spans
- **logs**: errwarn=44,516B · traceId=23,519B
- **metrics**: 8 수집 / 188,689B, 누락 [sum(rate(http_server_requests_seconds_count{application="content-service", status="401"}[1m]))]
- **context**: 287,295 chars (+ 시스템 프롬프트 575 chars)

### 토큰 축 (개선 지표)

| 단계 | 총 in | chars | 컨텍스트 토큰 |
|---|---:|---:|---:|
| 탐색 | 45,955 | 45,988 | 24,646 |
| 분석 | 182,437 | 287,870 | 161,128 |
| **합계** | **228,392** | | **185,774** |

- **overheadTokens 21,309 tok — 이 회차에 실측**(1자 프롬프트 프로브, 같은 명령·모델·샌드박스).
  호출마다 한 번씩 얹히므로 단계별로 뺀다. **다른 날 상수를 쓰지 않았다 = `█ 실측`.**
- contextTokens (count_tokens API): 측정 안 됨 — 구독 CLI 경로엔 API 키가 없다
- 비용은 **API 환산 추정치**다. 구독 계정으로 호출하므로 실제 청구액이 아니다.

## 수집 실패/누락

- Metric 'sum(rate(http_server_requests_seconds_count{application="content-service", status="401"}[1m]))' returned no series in this window; skipped.

---

## 1. 원인 후보 랭킹 (최대 3개)

1. **MongoDB(172.31.46.124:27017) 다운으로 chat-service의 알림 저장(`user_notifications` insert) 실패 → 재시도 소진 → DLQ 이관** (11:54:45~11:59:40 UTC / 20:54~20:59 KST)
2. **chat-service 컨슈머 인스턴스 부재 구간 발생 → `user.notifications` partition 3 컨슈머 랙 25 누적** (12:08:45~12:17:30 UTC / 21:08~21:17 KST) — "문의 여러 건"을 설명하는 건 이쪽이 더 큼
3. (연쇄 가설) **1번의 Mongo 장애가 chat-service 헬스체크/프로브 실패를 유발해 2번의 파드 교체로 이어졌을 가능성** — 확신도 낮음

---

## 2. 후보별 근거

### 후보 1 — MongoDB 다운 (조사 대상 traceId의 직접 원인)

**근거**
- 발행 측은 정상. content-service `http post /battles/{battleId}/items/{itemId}/comments` = `status 200`, 43ms, 그리고 `[Kafka] 알림 발행 성공: userId=7, type=BATTLE_ITEM_COMMENT, partition=3, offset=1004` (11:55:12.522 UTC).
- 소비 측에서만 실패. chat-service `receive` span(`messaging.source.name=user.notifications`, `partition=3`, `offset=1004`, group=`notification-processors`)이 **4회 모두 `STATUS_CODE_ERROR`**, 각 회차 소요 **약 30.0초** (11:55:12.524→11:55:42.805 / 11:55:43.948→11:56:13.970 / 11:56:14.975→11:56:44.989 / 11:56:45.993→11:57:16.011).
- 에러 원문(모든 실패 span·로그 동일):
  `Timed out while waiting for a server that matches WritableServerSelector ... {address=172.31.46.124:27017, type=UNKNOWN, state=CONNECTING, exception={com.mongodb.MongoSocketOpenException: Exception opening socket}, caused by {io.netty.channel.AbstractChannel$AnnotatedConnectException: Connection refused: /172.31.46.124:27017}, caused by {java.net.ConnectException: Connection refused}}`
  → **Connection refused = 포트 미리슨(프로세스 부재)**. 네트워크 지연/인증 실패가 아님.
- 실패 지점 특정: `user-notification-service#process-notification`(`UserNotificationService.processNotification`) span error + 직전 DEBUG `Inserting Document containing fields: [userId, type, ...] in collection: user_notifications`.
- 재시도·이관 로그: `[KAFKA-RETRY] user-notification 처리 실패 1~4회차: topic=user.notifications partition=3 offset=1004` → `[KAFKA-DLQ] 발행: user.notifications -> user.notifications.dlq (partition=3 offset=1004)` (11:57:16.011) → `재시도 소진 - recoverer 처리 완료` (11:57:17.108).
- DLQ 재처리도 실패: 11:57:47.109 `[Kafka] DLQ 알림 재처리 실패 (1분 후 재시도): userId=7` + `[KAFKA-RETRY] user-notification-dlq 처리 실패 1회차: topic=user.notifications.dlq partition=0 offset=13` (원인 동일).
- 인프라 메트릭 확증: **`mongodb_up` = 0 (11:54:45 ~ 11:59:30 UTC), 11:59:45부터 1** → 약 5분 다운. chat-service `MongoReactiveHealthIndicator: Mongo health check failed`가 10초 주기로 11:54:50~11:59:11 반복, health contributor(mongo) 응답 **30001ms** 반복.

**확신도: 높음**

**반증 데이터**
- 이 구간의 실제 사용자 영향은 **1건**뿐임. `kafka_consumergroup_lag{notification-processors, user.notifications, partition=3}`은 11:55:30~11:57:15 동안 1이었다가 11:57:30에 0. 즉 "문의 여러 건"을 이 구간만으로 설명하기는 어려움.
- 해당 알림은 유실이 아니라 **지연 회수됐을 가능성**: `kafka_consumergroup_lag{notification-recovery, user.notifications.dlq, partition=0}`이 11:57:30~11:59:15 동안 1 → **11:59:30에 0** (Mongo 복구 시점과 일치). 단, 성공 처리 로그는 확보되지 않음.
- `up{job="mongodb"}` (exporter)와 `up{job="node-infra"}`는 전 구간 1 → infra-server 자체 다운이 아니라 mongod 단일 프로세스 문제로 좁혀짐.
- 동일 인프라의 Redis(트레이스 내 `GET` 0.55ms), MySQL(content 쿼리 정상), Kafka(`kafka_brokers`=1, `up{job="kafka"}`=1) 전 구간 정상 → 인프라 전면 장애 아님.

### 후보 2 — chat-service 컨슈머 부재로 인한 랙 누적

**근거**
- `kafka_consumergroup_lag{consumergroup="notification-processors", topic="user.notifications", partition="3"}`: 12:08:45까지 0 → 12:09:00에 3 → 12:14:00에 **25** → 12:17:45까지 25 유지 → **12:18:00에 0**.
- 같은 시각 chat-service 인스턴스가 관측에서 사라짐: pod `chat-service-857c54dd97-w7bf7`의 `up`/`hikaricp_connections_active`/`websocket_active_users` 시계열이 **12:08:45에 종료**, 신규 pod `chat-service-857c54dd97-2nzgh`는 **12:17:30부터 등장**. 그 사이 chat-service 인스턴스 메트릭이 전무.
- 신규 pod 기동 로그: 12:15:57 UTC(21:15:57 KST) `JpaBaseConfiguration$JpaWebConfiguration ... open-in-view` WARN(main 스레드) = 애플리케이션 부팅.
- 즉 **약 9분간 알림 컨슈머가 없어 25건이 미처리 상태로 대기**했고, 신규 pod 기동 후 일괄 소진됨. 문의가 21:09~21:18 KST에 몰려 있다면 이 구간이 지배적 원인.

**확신도: 높음** (사실 자체는 메트릭으로 확증) / **파드가 사라진 원인 자체는 "데이터 부족"**

**반증 데이터**
- 파드 소실 사유를 판단할 데이터 없음(kube_state_metrics의 컨테이너 종료 사유, restart 카운트, k8s 이벤트, OOMKill/eviction 지표 미수집). 정상 재배포였을 가능성도 배제 불가.
- 이 구간에는 ERROR 로그가 전혀 없음(파드가 없으니 로그도 없음) → 애플리케이션 레벨 실패 근거는 없음. 순수 "컨슈머 부재"로만 설명됨.
- partition 3 외 파티션(0,1,2,4,5) 랙은 전 구간 0 → 트래픽이 p3에 집중된 건지, 다른 파티션에 메시지가 없었던 건지 구분 불가.

### 후보 3 — Mongo 장애가 파드 프로브 실패/교체를 유발했다는 연쇄 가설

**근거**
- Mongo 다운 구간에 chat-service의 `/actuator/health`가 **30001ms** 반복 응답, `Mongo health check failed` 지속 → 기본 프로브 타임아웃이면 liveness/readiness 실패로 이어질 수 있음.
- 같은 시각 w7bf7 pod의 Prometheus 스크랩이 **11:55:45~12:00:00 결측**(응답 불가 정황).
- 신규 pod에서도 12:16:54 UTC에 `Health contributor ... (redis) took 12399ms / 11054ms to respond` WARN → 헬스 엔드포인트가 외부 의존성에 취약한 구조임을 시사.

**확신도: 낮음**

**반증 데이터**
- 시점 불일치가 큼. Mongo는 11:59:40에 복구됐고, **11:59:45~12:08:45 동안 w7bf7의 `up`=1로 정상 수집**되다가 12:08:45에 사라짐 → Mongo 장애 직후 즉시 죽은 게 아님(약 9분 시차).
- 프로브 실패/재시작을 직접 증명하는 데이터(파드 재시작 카운트, 종료 사유, k8s 이벤트) 없음.

### 배제 / 확인 불가 항목
- **auth-service 원인: 근거 없음.** 트레이스가 `JwtAuthenticationFilter` 포함 12개 필터 체인을 통과해 `userId=1`로 200 응답. auth-service `up`=1, Hikari active/pending 0, GC 미미.
- **수집 실패 반영:** `sum(rate(http_server_requests_seconds_count{application="content-service", status="401"}[1m]))`가 시리즈 없음으로 스킵됨 → **401 급증 가설은 확인도 반증도 불가**. 위 트레이스 1건 외 전체 요청의 인증 실패 여부는 미확인이므로, "모든 문의가 위 두 원인으로 설명된다"는 결론의 확신도는 그만큼 낮춰야 함.
- **데이터 부족:** ① mongod가 왜 죽었는지(크래시/재시작/OOM/디스크) ② w7bf7 파드 종료 사유 ③ 알림 발송 최종 단계(FCM/푸시 전송) 성공 여부 — 관측된 건 "Mongo 저장 실패"까지이며 발송 단계 로그·메트릭은 없음 ④ 동일 시간대 다른 traceId의 실패 총건수(실패 카운터 메트릭 부재).

---

## 3. 권장 다음 조치

1. **MongoDB 다운 원인 확정** — infra-server(172.31.46.124)에서 11:54:45~11:59:40 UTC 구간 `journalctl -u mongod`, `mongod.log`, `dmesg | grep -i oom` 확인. `Connection refused`이므로 프로세스 종료/재시작 여부가 핵심.
2. **유실 여부 검증** — MongoDB `user_notifications`에서 `userId=7, type=BATTLE_ITEM_COMMENT, referenceId=댓글 175`(battleId 22 / itemId 125) 도큐먼트 존재 확인. `user.notifications.dlq` partition 0 offset 13 처리 결과 로그(11:58~12:00 UTC) 조회.
3. **12:08:45~12:17:30 파드 소실 원인 조사** — `kubectl get events --field-selector involvedObject.name=chat-service-857c54dd97-w7bf7`, `kube_pod_container_status_last_terminated_reason`, 노드 `ip-172-31-45-39` 상태/리소스 압박 확인.
4. **랙 25건 실제 발송 검증** — 12:18:00 UTC 이후 신규 pod(2nzgh)의 `UserNotificationConsumer` 처리 성공 로그 25건 대조. 미발송이면 재발행 필요.
5. **문의 시각 대조** — 접수된 문의의 발생 시각을 21:55~21:59 KST(구간 A) / 21:09~21:18 KST(구간 B)로 분류. 두 구간 밖의 문의가 있으면 추가 조사 필요(현재 데이터로는 설명 불가).
6. **재발 방지 (구성 변경)**
   - chat-service의 liveness/readiness 프로브에서 Mongo·Redis 헬스 인디케이터 분리(외부 의존성 장애가 파드 kill로 번지지 않도록).
   - Mongo `serverSelectionTimeout` 30초 축소 — 현재 30초 × 4회 재시도로 파티션 3이 **약 2분간 블로킹**됨.
   - 트레이스상 `connection`(HikariPool-1, `jdbc.datasource.name=content`, MySQL) span이 Mongo 호출 30초 내내 점유되다 `rollback`으로 종료됨. 트랜잭션 경계 안에서 외부 I/O를 호출하는 구조 점검 권장(12:08:00~12:08:45에 `hikaricp_connections_active=1` 지속도 같은 맥락).
7. **알람 추가** — `mongodb_up == 0`, `kafka_consumergroup_lag{consumergroup="notification-processors"} > 0` 2분 지속, `absent(up{job="chat-service"})`, DLQ 발행 카운터.
8. **누락 메트릭 복구** — content-service의 `http_server_requests_seconds_count{status="401"}` 시리즈가 없는 원인(메트릭명/라벨 불일치 또는 401 자체가 0건) 확인 후 401 가설 재검증.

---

## 관측 증거 (Evidence)

> 이 조사가 실제로 본 값이다. 원본 응답 전체는 `reports/raw/6a69ea2063240c0918077d8cbd8c859c-*.json`에 있다.

### span (duration 상위 15 / 전체 28)

| ms | service | span | 시작 |
|---:|---|---|---|
| 30281.00 | chat-service | `receive` | 2026-07-29T11:55:12.524048Z |
| 30076.40 | chat-service | `connection` | 2026-07-29T11:55:12.524507Z |
| 30021.98 | chat-service | `receive` | 2026-07-29T11:55:43.948167Z |
| 30017.44 | chat-service | `receive` | 2026-07-29T11:56:45.993675Z |
| 30014.50 | chat-service | `receive` | 2026-07-29T11:56:14.975057Z |
| 30014.30 | chat-service | `user-notification-service#process-notification` | 2026-07-29T11:55:12.527542Z |
| 30009.11 | chat-service | `connection` | 2026-07-29T11:56:45.994126Z |
| 30008.44 | chat-service | `connection` | 2026-07-29T11:55:43.948784Z |
| 30008.34 | chat-service | `connection` | 2026-07-29T11:56:14.975476Z |
| 30001.69 | chat-service | `user-notification-service#process-notification` | 2026-07-29T11:55:43.951929Z |
| 30001.65 | chat-service | `user-notification-service#process-notification` | 2026-07-29T11:56:45.997791Z |
| 30001.55 | chat-service | `user-notification-service#process-notification` | 2026-07-29T11:56:14.978662Z |
| 1078.62 | chat-service | `publish user.notifications.dlq` | 2026-07-29T11:57:16.030545Z |
| 43.66 | content-service | `http post /battles/{battleId}/items/{itemId}/comments` | 2026-07-29T11:55:12.464150Z |
| 42.54 | content-service | `secured request` | 2026-07-29T11:55:12.464560Z |

### 로그 원문 (60 / 전체 103줄)

전체가 상한을 넘어 ERROR/WARN 줄을 우선 발췌했다. 나머지는 원본 파일에 있다.

```
2026-07-29T11:54:50.249716931Z  [chat-service]  [2m2026-07-29T20:54:50.248+09:00[0;39m [33m WARN [traceId=NONE,spanId=NONE,userId=NONE][0;39m [35m7[0;39m [2m--- [chat-service] [478f610a9387efd] [                                                 ] [0;39m[36mo.s.b.a.d.m.MongoReactiveHealthIndicator[0;39m [2m:[0;39m Mongo health check failed
2026-07-29T11:54:50.250267816Z  [chat-service]  [2m2026-07-29T20:54:50.249+09:00[0;39m [33m WARN [traceId=6a69e9ec70a8e3a2317c6b38ebd60ec6,spanId=6cd2b3376c106ec6,userId=NONE][0;39m [35m7[0;39m [2m--- [chat-service] [nio-8090-exec-3] [6a69e9ec70a8e3a2317c6b38ebd60ec6-6cd2b3376c106ec6] [0;39m[36mo.s.b.a.health.HealthEndpointSupport    [0;39m [2m:[0;39m Health contributor org.springframework.boot.actuate.autoconfigure.health.HealthEndpointConfiguration$AdaptedReactiveHealthContributors$1 (mongo) took 30001ms to respond
2026-07-29T11:55:00.293865689Z  [chat-service]  [2m2026-07-29T20:55:00.291+09:00[0;39m [33m WARN [traceId=NONE,spanId=NONE,userId=NONE][0;39m [35m7[0;39m [2m--- [chat-service] [478f610a9387efd] [                                                 ] [0;39m[36mo.s.b.a.d.m.MongoReactiveHealthIndicator[0;39m [2m:[0;39m Mongo health check failed
2026-07-29T11:55:00.295179839Z  [chat-service]  [2m2026-07-29T20:55:00.294+09:00[0;39m [33m WARN [traceId=6a69e9f6ace468212e4d9629715efeea,spanId=f3586289885f15a2,userId=NONE][0;39m [35m7[0;39m [2m--- [chat-service] [nio-8090-exec-7] [6a69e9f6ace468212e4d9629715efeea-f3586289885f15a2] [0;39m[36mo.s.b.a.health.HealthEndpointSupport    [0;39m [2m:[0;39m Health contributor org.springframework.boot.actuate.autoconfigure.health.HealthEndpointConfiguration$AdaptedReactiveHealthContributors$1 (mongo) took 30005ms to respond
2026-07-29T11:55:10.332268723Z  [chat-service]  [2m2026-07-29T20:55:10.331+09:00[0;39m [33m WARN [traceId=NONE,spanId=NONE,userId=NONE][0;39m [35m7[0;39m [2m--- [chat-service] [478f610a9387efd] [                                                 ] [0;39m[36mo.s.b.a.d.m.MongoReactiveHealthIndicator[0;39m [2m:[0;39m Mongo health check failed
2026-07-29T11:55:10.332500137Z  [chat-service]  [2m2026-07-29T20:55:10.332+09:00[0;39m [33m WARN [traceId=6a69ea00cf8d9f0c5aa6ae41cd6a078f,spanId=8ade3dae0022edba,userId=NONE][0;39m [35m7[0;39m [2m--- [chat-service] [nio-8090-exec-6] [6a69ea00cf8d9f0c5aa6ae41cd6a078f-8ade3dae0022edba] [0;39m[36mo.s.b.a.health.HealthEndpointSupport    [0;39m [2m:[0;39m Health contributor org.springframework.boot.actuate.autoconfigure.health.HealthEndpointConfiguration$AdaptedReactiveHealthContributors$1 (mongo) took 30001ms to respond
2026-07-29T11:55:20.373327204Z  [chat-service]  [2m2026-07-29T20:55:20.372+09:00[0;39m [33m WARN [traceId=NONE,spanId=NONE,userId=NONE][0;39m [35m7[0;39m [2m--- [chat-service] [478f610a9387efd] [                                                 ] [0;39m[36mo.s.b.a.d.m.MongoReactiveHealthIndicator[0;39m [2m:[0;39m Mongo health check failed
2026-07-29T11:55:20.373706060Z  [chat-service]  [2m2026-07-29T20:55:20.373+09:00[0;39m [33m WARN [traceId=6a69ea0aeec44ba4b377b203b2d42326,spanId=004eddfe686fd92b,userId=NONE][0;39m [35m7[0;39m [2m--- [chat-service] [nio-8090-exec-5] [6a69ea0aeec44ba4b377b203b2d42326-004eddfe686fd92b] [0;39m[36mo.s.b.a.health.HealthEndpointSupport    [0;39m [2m:[0;39m Health contributor org.springframework.boot.actuate.autoconfigure.health.HealthEndpointConfiguration$AdaptedReactiveHealthContributors$1 (mongo) took 30001ms to respond
2026-07-29T11:55:30.417070699Z  [chat-service]  [2m2026-07-29T20:55:30.415+09:00[0;39m [33m WARN [traceId=NONE,spanId=NONE,userId=NONE][0;39m [35m7[0;39m [2m--- [chat-service] [478f610a9387efd] [                                                 ] [0;39m[36mo.s.b.a.d.m.MongoReactiveHealthIndicator[0;39m [2m:[0;39m Mongo health check failed
2026-07-29T11:55:30.417521636Z  [chat-service]  [2m2026-07-29T20:55:30.416+09:00[0;39m [33m WARN [traceId=6a69ea14a35f39bcfa52c9508fb8f11a,spanId=ce3addb4d97c09fb,userId=NONE][0;39m [35m7[0;39m [2m--- [chat-service] [nio-8090-exec-9] [6a69ea14a35f39bcfa52c9508fb8f11a-ce3addb4d97c09fb] [0;39m[36mo.s.b.a.health.HealthEndpointSupport    [0;39m [2m:[0;39m Health contributor org.springframework.boot.actuate.autoconfigure.health.HealthEndpointConfiguration$AdaptedReactiveHealthContributors$1 (mongo) took 30001ms to respond
2026-07-29T11:55:40.456563209Z  [chat-service]  [2m2026-07-29T20:55:40.455+09:00[0;39m [33m WARN [traceId=NONE,spanId=NONE,userId=NONE][0;39m [35m7[0;39m [2m--- [chat-service] [478f610a9387efd] [                                                 ] [0;39m[36mo.s.b.a.d.m.MongoReactiveHealthIndicator[0;39m [2m:[0;39m Mongo health check failed
2026-07-29T11:55:40.457083517Z  [chat-service]  [2m2026-07-29T20:55:40.456+09:00[0;39m [33m WARN [traceId=6a69ea1ea441916842ca69077941f3b8,spanId=416b455b017b098b,userId=NONE][0;39m [35m7[0;39m [2m--- [chat-service] [nio-8090-exec-4] [6a69ea1ea441916842ca69077941f3b8-416b455b017b098b] [0;39m[36mo.s.b.a.health.HealthEndpointSupport    [0;39m [2m:[0;39m Health contributor org.springframework.boot.actuate.autoconfigure.health.HealthEndpointConfiguration$AdaptedReactiveHealthContributors$1 (mongo) took 30001ms to respond
2026-07-29T11:55:42.701326704Z  [chat-service]  [2m2026-07-29T20:55:42.601+09:00[0;39m [31mERROR [traceId=6a69ea2063240c0918077d8cbd8c859c,spanId=4972da6eef5274ca,userId=NONE][0;39m [35m7[0;39m [2m--- [chat-service] [ntainer#5-1-C-1] [6a69ea2063240c0918077d8cbd8c859c-4972da6eef5274ca] [0;39m[36mc.e.t.a.k.u.UserNotificationConsumer    [0;39m [2m:[0;39m [Kafka] 알림 처리 실패: userId=7, type=BATTLE_ITEM_COMMENT
2026-07-29T11:55:42.904398230Z  [chat-service]  [2m2026-07-29T20:55:42.904+09:00[0;39m [33m WARN [traceId=6a69ea2063240c0918077d8cbd8c859c,spanId=4972da6eef5274ca,userId=NONE][0;39m [35m7[0;39m [2m--- [chat-service] [ntainer#5-1-C-1] [6a69ea2063240c0918077d8cbd8c859c-4972da6eef5274ca] [0;39m[36mc.e.t.app.config.KafkaConsumerConfig    [0;39m [2m:[0;39m [KAFKA-RETRY] user-notification 처리 실패 1회차: topic=user.notifications partition=3 offset=1004 cause=com.mongodb.MongoTimeoutException: Timed out while waiting for a server that matches WritableServerSelector. Client view of cluster state is {type=UNKNOWN, servers=[{address=172.31.46.124:27017, type=UNKNOWN, state=CONNECTING, exception={com.mongodb.MongoSocketOpenException: Exception opening socket}, caused by {io.netty.channel.AbstractChannel$AnnotatedConnectException: Connection refused: /172.31.46.124:27017}, caused by {java.net.ConnectException: Connection refused}}]
2026-07-29T11:55:50.497396364Z  [chat-service]  [2m2026-07-29T20:55:50.496+09:00[0;39m [33m WARN [traceId=NONE,spanId=NONE,userId=NONE][0;39m [35m7[0;39m [2m--- [chat-service] [478f610a9387efd] [                                                 ] [0;39m[36mo.s.b.a.d.m.MongoReactiveHealthIndicator[0;39m [2m:[0;39m Mongo health check failed
2026-07-29T11:55:50.497615256Z  [chat-service]  [2m2026-07-29T20:55:50.497+09:00[0;39m [33m WARN [traceId=6a69ea288fbeab2fdaea0e550e31b616,spanId=9509f26b1dddc73a,userId=NONE][0;39m [35m7[0;39m [2m--- [chat-service] [nio-8090-exec-2] [6a69ea288fbeab2fdaea0e550e31b616-9509f26b1dddc73a] [0;39m[36mo.s.b.a.health.HealthEndpointSupport    [0;39m [2m:[0;39m Health contributor org.springframework.boot.actuate.autoconfigure.health.HealthEndpointConfiguration$AdaptedReactiveHealthContributors$1 (mongo) took 30001ms to respond
2026-07-29T11:56:00.540528864Z  [chat-service]  [2m2026-07-29T20:56:00.538+09:00[0;39m [33m WARN [traceId=NONE,spanId=NONE,userId=NONE][0;39m [35m7[0;39m [2m--- [chat-service] [478f610a9387efd] [                                                 ] [0;39m[36mo.s.b.a.d.m.MongoReactiveHealthIndicator[0;39m [2m:[0;39m Mongo health check failed
2026-07-29T11:56:00.542087684Z  [chat-service]  [2m2026-07-29T20:56:00.541+09:00[0;39m [33m WARN [traceId=6a69ea32b4f9e796473a4e30708bf96d,spanId=deac39d54b54917f,userId=NONE][0;39m [35m7[0;39m [2m--- [chat-service] [nio-8090-exec-8] [6a69ea32b4f9e796473a4e30708bf96d-deac39d54b54917f] [0;39m[36mo.s.b.a.health.HealthEndpointSupport    [0;39m [2m:[0;39m Health contributor org.springframework.boot.actuate.autoconfigure.health.HealthEndpointConfiguration$AdaptedReactiveHealthContributors$1 (mongo) took 30004ms to respond
2026-07-29T11:56:10.580302444Z  [chat-service]  [2m2026-07-29T20:56:10.579+09:00[0;39m [33m WARN [traceId=NONE,spanId=NONE,userId=NONE][0;39m [35m7[0;39m [2m--- [chat-service] [478f610a9387efd] [                                                 ] [0;39m[36mo.s.b.a.d.m.MongoReactiveHealthIndicator[0;39m [2m:[0;39m Mongo health check failed
2026-07-29T11:56:10.580697782Z  [chat-service]  [2m2026-07-29T20:56:10.580+09:00[0;39m [33m WARN [traceId=6a69ea3c6400d390cfbbe50f2c943770,spanId=67aeb441d6a112cd,userId=NONE][0;39m [35m7[0;39m [2m--- [chat-service] [nio-8090-exec-3] [6a69ea3c6400d390cfbbe50f2c943770-67aeb441d6a112cd] [0;39m[36mo.s.b.a.health.HealthEndpointSupport    [0;39m [2m:[0;39m Health contributor org.springframework.boot.actuate.autoconfigure.health.HealthEndpointConfiguration$AdaptedReactiveHealthContributors$1 (mongo) took 30001ms to respond
2026-07-29T11:56:13.969969993Z  [chat-service]  [2m2026-07-29T20:56:13.957+09:00[0;39m [31mERROR [traceId=6a69ea2063240c0918077d8cbd8c859c,spanId=5caf9f91154d05ec,userId=NONE][0;39m [35m7[0;39m [2m--- [chat-service] [ntainer#5-1-C-1] [6a69ea2063240c0918077d8cbd8c859c-5caf9f91154d05ec] [0;39m[36mc.e.t.a.k.u.UserNotificationConsumer    [0;39m [2m:[0;39m [Kafka] 알림 처리 실패: userId=7, type=BATTLE_ITEM_COMMENT
2026-07-29T11:56:13.970689097Z  [chat-service]  [2m2026-07-29T20:56:13.970+09:00[0;39m [33m WARN [traceId=6a69ea2063240c0918077d8cbd8c859c,spanId=5caf9f91154d05ec,userId=NONE][0;39m [35m7[0;39m [2m--- [chat-service] [ntainer#5-1-C-1] [6a69ea2063240c0918077d8cbd8c859c-5caf9f91154d05ec] [0;39m[36mc.e.t.app.config.KafkaConsumerConfig    [0;39m [2m:[0;39m [KAFKA-RETRY] user-notification 처리 실패 2회차: topic=user.notifications partition=3 offset=1004 cause=com.mongodb.MongoTimeoutException: Timed out while waiting for a server that matches WritableServerSelector. Client view of cluster state is {type=UNKNOWN, servers=[{address=172.31.46.124:27017, type=UNKNOWN, state=CONNECTING, exception={com.mongodb.MongoSocketOpenException: Exception opening socket}, caused by {io.netty.channel.AbstractChannel$AnnotatedConnectException: Connection refused: /172.31.46.124:27017}, caused by {java.net.ConnectException: Connection refused}}]
2026-07-29T11:56:20.623144160Z  [chat-service]  [2m2026-07-29T20:56:20.622+09:00[0;39m [33m WARN [traceId=NONE,spanId=NONE,userId=NONE][0;39m [35m7[0;39m [2m--- [chat-service] [478f610a9387efd] [                                                 ] [0;39m[36mo.s.b.a.d.m.MongoReactiveHealthIndicator[0;39m [2m:[0;39m Mongo health check failed
2026-07-29T11:56:20.623804412Z  [chat-service]  [2m2026-07-29T20:56:20.623+09:00[0;39m [33m WARN [traceId=6a69ea46b47dfc5ec564b9f83dfdd63f,spanId=ba2e03312e26fe36,userId=NONE][0;39m [35m7[0;39m [2m--- [chat-service] [nio-8090-exec-7] [6a69ea46b47dfc5ec564b9f83dfdd63f-ba2e03312e26fe36] [0;39m[36mo.s.b.a.health.HealthEndpointSupport    [0;39m [2m:[0;39m Health contributor org.springframework.boot.actuate.autoconfigure.health.HealthEndpointConfiguration$AdaptedReactiveHealthContributors$1 (mongo) took 30001ms to respond
2026-07-29T11:56:30.664729679Z  [chat-service]  [2m2026-07-29T20:56:30.663+09:00[0;39m [33m WARN [traceId=NONE,spanId=NONE,userId=NONE][0;39m [35m7[0;39m [2m--- [chat-service] [478f610a9387efd] [                                                 ] [0;39m[36mo.s.b.a.d.m.MongoReactiveHealthIndicator[0;39m [2m:[0;39m Mongo health check failed
2026-07-29T11:56:30.665510141Z  [chat-service]  [2m2026-07-29T20:56:30.665+09:00[0;39m [33m WARN [traceId=6a69ea508c2bcfee5b22b8f9ee5d4ba9,spanId=d04d501319d3f8cb,userId=NONE][0;39m [35m7[0;39m [2m--- [chat-service] [nio-8090-exec-5] [6a69ea508c2bcfee5b22b8f9ee5d4ba9-d04d501319d3f8cb] [0;39m[36mo.s.b.a.health.HealthEndpointSupport    [0;39m [2m:[0;39m Health contributor org.springframework.boot.actuate.autoconfigure.health.HealthEndpointConfiguration$AdaptedReactiveHealthContributors$1 (mongo) took 30001ms to respond
2026-07-29T11:56:40.708885339Z  [chat-service]  [2m2026-07-29T20:56:40.707+09:00[0;39m [33m WARN [traceId=NONE,spanId=NONE,userId=NONE][0;39m [35m7[0;39m [2m--- [chat-service] [478f610a9387efd] [                                                 ] [0;39m[36mo.s.b.a.d.m.MongoReactiveHealthIndicator[0;39m [2m:[0;39m Mongo health check failed
2026-07-29T11:56:40.709079515Z  [chat-service]  [2m2026-07-29T20:56:40.708+09:00[0;39m [33m WARN [traceId=6a69ea5a0eddabc33f6a081150f08a5f,spanId=e55df2d75a0d571e,userId=NONE][0;39m [35m7[0;39m [2m--- [chat-service] [nio-8090-exec-1] [6a69ea5a0eddabc33f6a081150f08a5f-e55df2d75a0d571e] [0;39m[36mo.s.b.a.health.HealthEndpointSupport    [0;39m [2m:[0;39m Health contributor org.springframework.boot.actuate.autoconfigure.health.HealthEndpointConfiguration$AdaptedReactiveHealthContributors$1 (mongo) took 30001ms to respond
2026-07-29T11:56:44.989410682Z  [chat-service]  [2m2026-07-29T20:56:44.983+09:00[0;39m [31mERROR [traceId=6a69ea2063240c0918077d8cbd8c859c,spanId=03121938937488f4,userId=NONE][0;39m [35m7[0;39m [2m--- [chat-service] [ntainer#5-1-C-1] [6a69ea2063240c0918077d8cbd8c859c-03121938937488f4] [0;39m[36mc.e.t.a.k.u.UserNotificationConsumer    [0;39m [2m:[0;39m [Kafka] 알림 처리 실패: userId=7, type=BATTLE_ITEM_COMMENT
2026-07-29T11:56:44.990323049Z  [chat-service]  [2m2026-07-29T20:56:44.989+09:00[0;39m [33m WARN [traceId=6a69ea2063240c0918077d8cbd8c859c,spanId=03121938937488f4,userId=NONE][0;39m [35m7[0;39m [2m--- [chat-service] [ntainer#5-1-C-1] [6a69ea2063240c0918077d8cbd8c859c-03121938937488f4] [0;39m[36mc.e.t.app.config.KafkaConsumerConfig    [0;39m [2m:[0;39m [KAFKA-RETRY] user-notification 처리 실패 3회차: topic=user.notifications partition=3 offset=1004 cause=com.mongodb.MongoTimeoutException: Timed out while waiting for a server that matches WritableServerSelector. Client view of cluster state is {type=UNKNOWN, servers=[{address=172.31.46.124:27017, type=UNKNOWN, state=CONNECTING, exception={com.mongodb.MongoSocketOpenException: Exception opening socket}, caused by {io.netty.channel.AbstractChannel$AnnotatedConnectException: Connection refused: /172.31.46.124:27017}, caused by {java.net.ConnectException: Connection refused}}]
2026-07-29T11:56:50.746254967Z  [chat-service]  [2m2026-07-29T20:56:50.745+09:00[0;39m [33m WARN [traceId=NONE,spanId=NONE,userId=NONE][0;39m [35m7[0;39m [2m--- [chat-service] [478f610a9387efd] [                                                 ] [0;39m[36mo.s.b.a.d.m.MongoReactiveHealthIndicator[0;39m [2m:[0;39m Mongo health check failed
2026-07-29T11:56:50.746462Z  [chat-service]  [2m2026-07-29T20:56:50.746+09:00[0;39m [33m WARN [traceId=6a69ea647caf2d4071171f7136e58aaa,spanId=354895ade919ee94,userId=NONE][0;39m [35m7[0;39m [2m--- [chat-service] [nio-8090-exec-9] [6a69ea647caf2d4071171f7136e58aaa-354895ade919ee94] [0;39m[36mo.s.b.a.health.HealthEndpointSupport    [0;39m [2m:[0;39m Health contributor org.springframework.boot.actuate.autoconfigure.health.HealthEndpointConfiguration$AdaptedReactiveHealthContributors$1 (mongo) took 30001ms to respond
2026-07-29T11:57:00.795279081Z  [chat-service]  [2m2026-07-29T20:57:00.787+09:00[0;39m [33m WARN [traceId=NONE,spanId=NONE,userId=NONE][0;39m [35m7[0;39m [2m--- [chat-service] [478f610a9387efd] [                                                 ] [0;39m[36mo.s.b.a.d.m.MongoReactiveHealthIndicator[0;39m [2m:[0;39m Mongo health check failed
2026-07-29T11:57:00.795555173Z  [chat-service]  [2m2026-07-29T20:57:00.795+09:00[0;39m [33m WARN [traceId=6a69ea6e5cc51665ff9a3d06ad843ec7,spanId=0cc576bba1b065fa,userId=NONE][0;39m [35m7[0;39m [2m--- [chat-service] [io-8090-exec-10] [6a69ea6e5cc51665ff9a3d06ad843ec7-0cc576bba1b065fa] [0;39m[36mo.s.b.a.health.HealthEndpointSupport    [0;39m [2m:[0;39m Health contributor org.springframework.boot.actuate.autoconfigure.health.HealthEndpointConfiguration$AdaptedReactiveHealthContributors$1 (mongo) took 30007ms to respond
2026-07-29T11:57:10.830612573Z  [chat-service]  [2m2026-07-29T20:57:10.829+09:00[0;39m [33m WARN [traceId=NONE,spanId=NONE,userId=NONE][0;39m [35m7[0;39m [2m--- [chat-service] [478f610a9387efd] [                                                 ] [0;39m[36mo.s.b.a.d.m.MongoReactiveHealthIndicator[0;39m [2m:[0;39m Mongo health check failed
2026-07-29T11:57:10.831117411Z  [chat-service]  [2m2026-07-29T20:57:10.830+09:00[0;39m [33m WARN [traceId=6a69ea78bda965d81634deaafa56ee73,spanId=b315af107b977381,userId=NONE][0;39m [35m7[0;39m [2m--- [chat-service] [nio-8090-exec-2] [6a69ea78bda965d81634deaafa56ee73-b315af107b977381] [0;39m[36mo.s.b.a.health.HealthEndpointSupport    [0;39m [2m:[0;39m Health contributor org.springframework.boot.actuate.autoconfigure.health.HealthEndpointConfiguration$AdaptedReactiveHealthContributors$1 (mongo) took 30001ms to respond
2026-07-29T11:57:16.009702065Z  [chat-service]  [2m2026-07-29T20:57:16.003+09:00[0;39m [31mERROR [traceId=6a69ea2063240c0918077d8cbd8c859c,spanId=276b2bd4c78c0f22,userId=NONE][0;39m [35m7[0;39m [2m--- [chat-service] [ntainer#5-1-C-1] [6a69ea2063240c0918077d8cbd8c859c-276b2bd4c78c0f22] [0;39m[36mc.e.t.a.k.u.UserNotificationConsumer    [0;39m [2m:[0;39m [Kafka] 알림 처리 실패: userId=7, type=BATTLE_ITEM_COMMENT
2026-07-29T11:57:16.011693837Z  [chat-service]  [2m2026-07-29T20:57:16.011+09:00[0;39m [33m WARN [traceId=6a69ea2063240c0918077d8cbd8c859c,spanId=276b2bd4c78c0f22,userId=NONE][0;39m [35m7[0;39m [2m--- [chat-service] [ntainer#5-1-C-1] [6a69ea2063240c0918077d8cbd8c859c-276b2bd4c78c0f22] [0;39m[36mc.e.t.app.config.KafkaConsumerConfig    [0;39m [2m:[0;39m [KAFKA-RETRY] user-notification 처리 실패 4회차: topic=user.notifications partition=3 offset=1004 cause=com.mongodb.MongoTimeoutException: Timed out while waiting for a server that matches WritableServerSelector. Client view of cluster state is {type=UNKNOWN, servers=[{address=172.31.46.124:27017, type=UNKNOWN, state=CONNECTING, exception={com.mongodb.MongoSocketOpenException: Exception opening socket}, caused by {io.netty.channel.AbstractChannel$AnnotatedConnectException: Connection refused: /172.31.46.124:27017}, caused by {java.net.ConnectException: Connection refused}}]
2026-07-29T11:57:16.011726467Z  [chat-service]  [2m2026-07-29T20:57:16.011+09:00[0;39m [31mERROR [traceId=6a69ea2063240c0918077d8cbd8c859c,spanId=276b2bd4c78c0f22,userId=NONE][0;39m [35m7[0;39m [2m--- [chat-service] [ntainer#5-1-C-1] [6a69ea2063240c0918077d8cbd8c859c-276b2bd4c78c0f22] [0;39m[36mc.e.t.app.config.KafkaConsumerConfig    [0;39m [2m:[0;39m [KAFKA-DLQ] 발행: user.notifications -> user.notifications.dlq (partition=3 offset=1004) cause=com.mongodb.MongoTimeoutException: Timed out while waiting for a server that matches WritableServerSelector. Client view of cluster state is {type=UNKNOWN, servers=[{address=172.31.46.124:27017, type=UNKNOWN, state=CONNECTING, exception={com.mongodb.MongoSocketOpenException: Exception opening socket}, caused by {io.netty.channel.AbstractChannel$AnnotatedConnectException: Connection refused: /172.31.46.124:27017}, caused by {java.net.ConnectException: Connection refused}}]
2026-07-29T11:57:17.108572879Z  [chat-service]  [2m2026-07-29T20:57:17.108+09:00[0;39m [31mERROR [traceId=6a69ea2063240c0918077d8cbd8c859c,spanId=276b2bd4c78c0f22,userId=NONE][0;39m [35m7[0;39m [2m--- [chat-service] [ntainer#5-1-C-1] [6a69ea2063240c0918077d8cbd8c859c-276b2bd4c78c0f22] [0;39m[36mc.e.t.app.config.KafkaConsumerConfig    [0;39m [2m:[0;39m [KAFKA-RETRY] user-notification 재시도 소진 - recoverer 처리 완료: topic=user.notifications offset=1004 cause=com.mongodb.MongoTimeoutException: Timed out while waiting for a server that matches WritableServerSelector. Client view of cluster state is {type=UNKNOWN, servers=[{address=172.31.46.124:27017, type=UNKNOWN, state=CONNECTING, exception={com.mongodb.MongoSocketOpenException: Exception opening socket}, caused by {io.netty.channel.AbstractChannel$AnnotatedConnectException: Connection refused: /172.31.46.124:27017}, caused by {java.net.ConnectException: Connection refused}}]
2026-07-29T11:57:20.873265410Z  [chat-service]  [2m2026-07-29T20:57:20.871+09:00[0;39m [33m WARN [traceId=NONE,spanId=NONE,userId=NONE][0;39m [35m7[0;39m [2m--- [chat-service] [478f610a9387efd] [                                                 ] [0;39m[36mo.s.b.a.d.m.MongoReactiveHealthIndicator[0;39m [2m:[0;39m Mongo health check failed
2026-07-29T11:57:20.875504433Z  [chat-service]  [2m2026-07-29T20:57:20.873+09:00[0;39m [33m WARN [traceId=6a69ea8242435488e42105bf62ac7729,spanId=a0be8df1ebb8fc4d,userId=NONE][0;39m [35m7[0;39m [2m--- [chat-service] [nio-8090-exec-6] [6a69ea8242435488e42105bf62ac7729-a0be8df1ebb8fc4d] [0;39m[36mo.s.b.a.health.HealthEndpointSupport    [0;39m [2m:[0;39m Health contributor org.springframework.boot.actuate.autoconfigure.health.HealthEndpointConfiguration$AdaptedReactiveHealthContributors$1 (mongo) took 30003ms to respond
2026-07-29T11:57:30.915324908Z  [chat-service]  [2m2026-07-29T20:57:30.912+09:00[0;39m [33m WARN [traceId=NONE,spanId=NONE,userId=NONE][0;39m [35m7[0;39m [2m--- [chat-service] [478f610a9387efd] [                                                 ] [0;39m[36mo.s.b.a.d.m.MongoReactiveHealthIndicator[0;39m [2m:[0;39m Mongo health check failed
2026-07-29T11:57:30.915528482Z  [chat-service]  [2m2026-07-29T20:57:30.914+09:00[0;39m [33m WARN [traceId=6a69ea8c81530109923f5bc1002cc0b5,spanId=40e8c54c1628f437,userId=NONE][0;39m [35m7[0;39m [2m--- [chat-service] [nio-8090-exec-3] [6a69ea8c81530109923f5bc1002cc0b5-40e8c54c1628f437] [0;39m[36mo.s.b.a.health.HealthEndpointSupport    [0;39m [2m:[0;39m Health contributor org.springframework.boot.actuate.autoconfigure.health.HealthEndpointConfiguration$AdaptedReactiveHealthContributors$1 (mongo) took 30001ms to respond
2026-07-29T11:57:40.955178959Z  [chat-service]  [2m2026-07-29T20:57:40.954+09:00[0;39m [33m WARN [traceId=NONE,spanId=NONE,userId=NONE][0;39m [35m7[0;39m [2m--- [chat-service] [478f610a9387efd] [                                                 ] [0;39m[36mo.s.b.a.d.m.MongoReactiveHealthIndicator[0;39m [2m:[0;39m Mongo health check failed
2026-07-29T11:57:40.955527740Z  [chat-service]  [2m2026-07-29T20:57:40.954+09:00[0;39m [33m WARN [traceId=6a69ea964fe27ce378c8792da8184f1f,spanId=0769fd45760b7416,userId=NONE][0;39m [35m7[0;39m [2m--- [chat-service] [nio-8090-exec-7] [6a69ea964fe27ce378c8792da8184f1f-0769fd45760b7416] [0;39m[36mo.s.b.a.health.HealthEndpointSupport    [0;39m [2m:[0;39m Health contributor org.springframework.boot.actuate.autoconfigure.health.HealthEndpointConfiguration$AdaptedReactiveHealthContributors$1 (mongo) took 30001ms to respond
2026-07-29T11:57:47.114385250Z  [chat-service]  [2m2026-07-29T20:57:47.109+09:00[0;39m [31mERROR [traceId=NONE,spanId=NONE,userId=NONE][0;39m [35m7[0;39m [2m--- [chat-service] [ntainer#6-0-C-1] [                                                 ] [0;39m[36mc.e.t.a.k.u.UserNotificationConsumer    [0;39m [2m:[0;39m [Kafka] DLQ 알림 재처리 실패 (1분 후 재시도): userId=7, type=BATTLE_ITEM_COMMENT
2026-07-29T11:57:47.115346398Z  [chat-service]  [2m2026-07-29T20:57:47.115+09:00[0;39m [33m WARN [traceId=NONE,spanId=NONE,userId=NONE][0;39m [35m7[0;39m [2m--- [chat-service] [ntainer#6-0-C-1] [                                                 ] [0;39m[36mc.e.t.app.config.KafkaConsumerConfig    [0;39m [2m:[0;39m [KAFKA-RETRY] user-notification-dlq 처리 실패 1회차: topic=user.notifications.dlq partition=0 offset=13 cause=com.mongodb.MongoTimeoutException: Timed out while waiting for a server that matches WritableServerSelector. Client view of cluster state is {type=UNKNOWN, servers=[{address=172.31.46.124:27017, type=UNKNOWN, state=CONNECTING, exception={com.mongodb.MongoSocketOpenException: Exception opening socket}, caused by {io.netty.channel.AbstractChannel$AnnotatedConnectException: Connection refused: /172.31.46.124:27017}, caused by {java.net.ConnectException: Connection refused}}]
2026-07-29T11:57:50.996771439Z  [chat-service]  [2m2026-07-29T20:57:50.995+09:00[0;39m [33m WARN [traceId=NONE,spanId=NONE,userId=NONE][0;39m [35m7[0;39m [2m--- [chat-service] [478f610a9387efd] [                                                 ] [0;39m[36mo.s.b.a.d.m.MongoReactiveHealthIndicator[0;39m [2m:[0;39m Mongo health check failed
2026-07-29T11:57:50.997005324Z  [chat-service]  [2m2026-07-29T20:57:50.996+09:00[0;39m [33m WARN [traceId=6a69eaa08ea83e35fab61a2222f4892a,spanId=2bb2a003ef4b00fa,userId=NONE][0;39m [35m7[0;39m [2m--- [chat-service] [nio-8090-exec-4] [6a69eaa08ea83e35fab61a2222f4892a-2bb2a003ef4b00fa] [0;39m[36mo.s.b.a.health.HealthEndpointSupport    [0;39m [2m:[0;39m Health contributor org.springframework.boot.actuate.autoconfigure.health.HealthEndpointConfiguration$AdaptedReactiveHealthContributors$1 (mongo) took 30001ms to respond
2026-07-29T11:58:01.039563466Z  [chat-service]  [2m2026-07-29T20:58:01.038+09:00[0;39m [33m WARN [traceId=NONE,spanId=NONE,userId=NONE][0;39m [35m7[0;39m [2m--- [chat-service] [478f610a9387efd] [                                                 ] [0;39m[36mo.s.b.a.d.m.MongoReactiveHealthIndicator[0;39m [2m:[0;39m Mongo health check failed
2026-07-29T11:58:01.039928705Z  [chat-service]  [2m2026-07-29T20:58:01.039+09:00[0;39m [33m WARN [traceId=6a69eaab7218d96780feb17a59254754,spanId=a698df96d90b2556,userId=NONE][0;39m [35m7[0;39m [2m--- [chat-service] [nio-8090-exec-1] [6a69eaab7218d96780feb17a59254754-a698df96d90b2556] [0;39m[36mo.s.b.a.health.HealthEndpointSupport    [0;39m [2m:[0;39m Health contributor org.springframework.boot.actuate.autoconfigure.health.HealthEndpointConfiguration$AdaptedReactiveHealthContributors$1 (mongo) took 30001ms to respond
2026-07-29T11:58:11.082060119Z  [chat-service]  [2m2026-07-29T20:58:11.081+09:00[0;39m [33m WARN [traceId=NONE,spanId=NONE,userId=NONE][0;39m [35m7[0;39m [2m--- [chat-service] [478f610a9387efd] [                                                 ] [0;39m[36mo.s.b.a.d.m.MongoReactiveHealthIndicator[0;39m [2m:[0;39m Mongo health check failed
2026-07-29T11:58:11.082581669Z  [chat-service]  [2m2026-07-29T20:58:11.082+09:00[0;39m [33m WARN [traceId=6a69eab5fa3a92002eba5ca43876fb20,spanId=49d848410b1bc544,userId=NONE][0;39m [35m7[0;39m [2m--- [chat-service] [nio-8090-exec-9] [6a69eab5fa3a92002eba5ca43876fb20-49d848410b1bc544] [0;39m[36mo.s.b.a.health.HealthEndpointSupport    [0;39m [2m:[0;39m Health contributor org.springframework.boot.actuate.autoconfigure.health.HealthEndpointConfiguration$AdaptedReactiveHealthContributors$1 (mongo) took 30001ms to respond
2026-07-29T11:58:21.121407966Z  [chat-service]  [2m2026-07-29T20:58:21.120+09:00[0;39m [33m WARN [traceId=NONE,spanId=NONE,userId=NONE][0;39m [35m7[0;39m [2m--- [chat-service] [478f610a9387efd] [                                                 ] [0;39m[36mo.s.b.a.d.m.MongoReactiveHealthIndicator[0;39m [2m:[0;39m Mongo health check failed
2026-07-29T11:58:21.121847273Z  [chat-service]  [2m2026-07-29T20:58:21.121+09:00[0;39m [33m WARN [traceId=6a69eabfe5e7ec532b58da22979c26a8,spanId=9ad728114fa104ff,userId=NONE][0;39m [35m7[0;39m [2m--- [chat-service] [nio-8090-exec-8] [6a69eabfe5e7ec532b58da22979c26a8-9ad728114fa104ff] [0;39m[36mo.s.b.a.health.HealthEndpointSupport    [0;39m [2m:[0;39m Health contributor org.springframework.boot.actuate.autoconfigure.health.HealthEndpointConfiguration$AdaptedReactiveHealthContributors$1 (mongo) took 30001ms to respond
2026-07-29T12:15:57.251868811Z  [chat-service]  [2m2026-07-29T21:15:57.250+09:00[0;39m [33m WARN [traceId=NONE,spanId=NONE,userId=NONE][0;39m [35m7[0;39m [2m--- [chat-service] [           main] [                                                 ] [0;39m[36mJpaBaseConfiguration$JpaWebConfiguration[0;39m [2m:[0;39m spring.jpa.open-in-view is enabled by default. Therefore, database queries may be performed during view rendering. Explicitly configure spring.jpa.open-in-view to disable this warning
2026-07-29T12:16:54.003782557Z  [chat-service]  [2m2026-07-29T21:16:54.003+09:00[0;39m [33m WARN [traceId=6a69ef222b0ce40f604a318aefa962ef,spanId=147caa3212a91fe9,userId=NONE][0;39m [35m7[0;39m [2m--- [chat-service] [nio-8090-exec-1] [6a69ef222b0ce40f604a318aefa962ef-147caa3212a91fe9] [0;39m[36mo.s.b.a.health.HealthEndpointSupport    [0;39m [2m:[0;39m Health contributor org.springframework.boot.actuate.autoconfigure.health.HealthEndpointConfiguration$AdaptedReactiveHealthContributors$1 (redis) took 12399ms to respond
2026-07-29T12:16:54.007896921Z  [chat-service]  [2m2026-07-29T21:16:54.006+09:00[0;39m [33m WARN [traceId=6a69ef2a2d8988433e1ca658ec43d3ab,spanId=3d1ec8187141f0d7,userId=NONE][0;39m [35m7[0;39m [2m--- [chat-service] [nio-8090-exec-2] [6a69ef2a2d8988433e1ca658ec43d3ab-3d1ec8187141f0d7] [0;39m[36mo.s.b.a.health.HealthEndpointSupport    [0;39m [2m:[0;39m Health contributor org.springframework.boot.actuate.autoconfigure.health.HealthEndpointConfiguration$AdaptedReactiveHealthContributors$1 (redis) took 11054ms to respond
2026-07-29T12:17:05.453281035Z  [chat-service]  [2m2026-07-29T21:17:05.451+09:00[0;39m [33m WARN [traceId=6a69ed3b83dd485474fc9b28b6c86780,spanId=ee1596779860cc5f,userId=NONE][0;39m [35m7[0;39m [2m--- [chat-service] [ntainer#5-1-C-1] [6a69ed3b83dd485474fc9b28b6c86780-ee1596779860cc5f] [0;39m[36mi.m.p.PrometheusMeterRegistry           [0;39m [2m:[0;39m The meter (MeterId{name='spring.kafka.listener', tags=[tag(application=chat-service),tag(messaging.kafka.consumer.group=notification-processors),tag(messaging.operation=receive),tag(messaging.source.kind=topic),tag(messaging.source.name=user.notifications),tag(messaging.system=kafka),tag(spring.kafka.listener.id=org.springframework.kafka.KafkaListenerEndpointContainer#5-1)]}) registration has failed: Prometheus requires that all meters with the same name have the same set of tag keys. There is already an existing meter named 'spring_kafka_listener_seconds' containing tag keys [application, exception, name, result]. The meter you are attempting to register has keys [application, messaging_kafka_consumer_group, messaging_operation, messaging_source_kind, messaging_source_name, messaging_system, spring_kafka_listener_id]. Note that subsequent logs will be logged at debug level.
```

### 메트릭 시계열

| 쿼리 | series | 점 | min | max | last | 값이 0이던 구간 |
|---|---|---:|---:|---:|---:|---|
| `hikaricp_connections_active` | `{__name__=hikaricp_connections_active, application=auth-service, cluster=yogurtte-k3s-prod, container=auth-service, instance=10.42.1.41:8090, job=auth-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=auth-service-855c75679d-vpkqw, pool=HikariPool-1, service=auth-service}` | 117 | 0 | 0 | 0 | **2026-07-29T11:52:00Z ~ 2026-07-29T12:21:00Z** |
| `hikaricp_connections_active` | `{__name__=hikaricp_connections_active, application=chat-service, cluster=yogurtte-k3s-prod, container=chat-service, instance=10.42.1.39:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-857c54dd97-w7bf7, pool=HikariPool-1}` | 52 | 0 | 1 | 1 | **2026-07-29T11:52:00Z ~ 2026-07-29T12:07:45Z** |
| `hikaricp_connections_active` | `{__name__=hikaricp_connections_active, application=chat-service, cluster=yogurtte-k3s-prod, container=chat-service, instance=10.42.1.42:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-857c54dd97-2nzgh, pool=HikariPool-1}` | 15 | 0 | 1 | 0 | **2026-07-29T12:18:30Z ~ 2026-07-29T12:21:00Z** |
| `hikaricp_connections_active` | `{__name__=hikaricp_connections_active, application=content-service, cluster=yogurtte-k3s-prod, container=content-service, instance=10.42.1.35:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-6c5fff897-qnxk6, pool=HikariPool-1}` | 117 | 0 | 1 | 0 | **2026-07-29T11:52:15Z ~ 2026-07-29T12:00:00Z, 2026-07-29T12:01:15Z ~ 2026-07-29T12:05:00Z, 2026-07-29T12:06:15Z ~ 2026-07-29T12:10:00Z, 2026-07-29T12:11:15Z ~ 2026-07-29T12:14:00Z, 2026-07-29T12:15:15Z ~ 2026-07-29T12:21:00Z** |
| `hikaricp_connections_active` | `{__name__=hikaricp_connections_active, application=content-service, cluster=yogurtte-k3s-prod, container=content-service, instance=10.42.3.41:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-6c5fff897-scw7k, pool=HikariPool-1}` | 117 | 0 | 0 | 0 | **2026-07-29T11:52:00Z ~ 2026-07-29T12:21:00Z** |
| `hikaricp_connections_pending` | `{__name__=hikaricp_connections_pending, application=auth-service, cluster=yogurtte-k3s-prod, container=auth-service, instance=10.42.1.41:8090, job=auth-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=auth-service-855c75679d-vpkqw, pool=HikariPool-1, service=auth-service}` | 117 | 0 | 0 | 0 | **2026-07-29T11:52:00Z ~ 2026-07-29T12:21:00Z** |
| `hikaricp_connections_pending` | `{__name__=hikaricp_connections_pending, application=chat-service, cluster=yogurtte-k3s-prod, container=chat-service, instance=10.42.1.39:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-857c54dd97-w7bf7, pool=HikariPool-1}` | 52 | 0 | 0 | 0 | **2026-07-29T11:52:00Z ~ 2026-07-29T12:08:45Z** |
| `hikaricp_connections_pending` | `{__name__=hikaricp_connections_pending, application=chat-service, cluster=yogurtte-k3s-prod, container=chat-service, instance=10.42.1.42:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-857c54dd97-2nzgh, pool=HikariPool-1}` | 15 | 0 | 0 | 0 | **2026-07-29T12:17:30Z ~ 2026-07-29T12:21:00Z** |
| `hikaricp_connections_pending` | `{__name__=hikaricp_connections_pending, application=content-service, cluster=yogurtte-k3s-prod, container=content-service, instance=10.42.1.35:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-6c5fff897-qnxk6, pool=HikariPool-1}` | 117 | 0 | 0 | 0 | **2026-07-29T11:52:00Z ~ 2026-07-29T12:21:00Z** |
| `hikaricp_connections_pending` | `{__name__=hikaricp_connections_pending, application=content-service, cluster=yogurtte-k3s-prod, container=content-service, instance=10.42.3.41:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-6c5fff897-scw7k, pool=HikariPool-1}` | 117 | 0 | 0 | 0 | **2026-07-29T11:52:00Z ~ 2026-07-29T12:21:00Z** |
| `rate(jvm_gc_pause_seconds_sum[5m])` | `{action=end of major GC, application=chat-service, cause=Allocation Failure, cluster=yogurtte-k3s-prod, container=chat-service, gc=MarkSweepCompact, instance=10.42.1.39:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-857c54dd97-w7bf7}` | 72 | 0 | 0 | 0 | **2026-07-29T11:52:00Z ~ 2026-07-29T12:11:45Z** |
| `rate(jvm_gc_pause_seconds_sum[5m])` | `{action=end of minor GC, application=auth-service, cause=G1 Evacuation Pause, cluster=yogurtte-k3s-prod, container=auth-service, gc=G1 Young Generation, instance=10.42.1.41:8090, job=auth-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=auth-service-855c75679d-vpkqw, service=auth-service}` | 117 | 0 | 0.000 | 0 | **2026-07-29T11:52:00Z ~ 2026-07-29T11:57:45Z, 2026-07-29T12:02:00Z ~ 2026-07-29T12:21:00Z** |
| `rate(jvm_gc_pause_seconds_sum[5m])` | `{action=end of minor GC, application=chat-service, cause=Allocation Failure, cluster=yogurtte-k3s-prod, container=chat-service, gc=Copy, instance=10.42.1.39:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-857c54dd97-w7bf7}` | 72 | 0.000 | 0.000 | 0.000 | — |
| `rate(jvm_gc_pause_seconds_sum[5m])` | `{action=end of minor GC, application=chat-service, cause=Allocation Failure, cluster=yogurtte-k3s-prod, container=chat-service, gc=Copy, instance=10.42.1.42:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-857c54dd97-2nzgh}` | 11 | 0.001 | 0.001 | 0.001 | — |
| `rate(jvm_gc_pause_seconds_sum[5m])` | `{action=end of minor GC, application=content-service, cause=Allocation Failure, cluster=yogurtte-k3s-prod, container=content-service, gc=Copy, instance=10.42.1.35:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-6c5fff897-qnxk6}` | 117 | 0 | 0.000 | 0 | **2026-07-29T11:52:00Z ~ 2026-07-29T11:55:00Z, 2026-07-29T11:59:15Z ~ 2026-07-29T12:05:00Z, 2026-07-29T12:09:15Z ~ 2026-07-29T12:12:00Z, 2026-07-29T12:16:15Z ~ 2026-07-29T12:21:00Z** |
| `rate(jvm_gc_pause_seconds_sum[5m])` | `{action=end of minor GC, application=content-service, cause=Allocation Failure, cluster=yogurtte-k3s-prod, container=content-service, gc=Copy, instance=10.42.3.41:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-6c5fff897-scw7k}` | 117 | 0 | 0.000 | 0.000 | **2026-07-29T11:52:00Z ~ 2026-07-29T11:52:45Z, 2026-07-29T11:57:00Z ~ 2026-07-29T12:02:45Z, 2026-07-29T12:07:00Z ~ 2026-07-29T12:11:45Z, 2026-07-29T12:16:00Z ~ 2026-07-29T12:20:45Z** |
| `up` | `{__name__=up, app=node-exporter, cluster=yogurtte-k3s-prod, component=metrics, container=node-exporter, instance=ip-172-31-40-241, job=integrations/node_exporter, k8s_cluster_name=yogurtte-k3s-prod, namespace=monitoring, pod=grafana-k8s-monitoring-node-exporter-cph5l, source=kubernetes, workload=DaemonSet/grafana-k8s-monitoring-node-exporter}` | 117 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, app=node-exporter, cluster=yogurtte-k3s-prod, component=metrics, container=node-exporter, instance=ip-172-31-45-39, job=integrations/node_exporter, k8s_cluster_name=yogurtte-k3s-prod, namespace=monitoring, pod=grafana-k8s-monitoring-node-exporter-9kvqt, source=kubernetes, workload=DaemonSet/grafana-k8s-monitoring-node-exporter}` | 117 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, cluster=yogurtte-k3s-prod, container=auth-service, instance=10.42.1.41:8090, job=auth-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=auth-service-855c75679d-vpkqw}` | 117 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, cluster=yogurtte-k3s-prod, container=chat-service, instance=10.42.1.39:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-857c54dd97-w7bf7}` | 52 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, cluster=yogurtte-k3s-prod, container=chat-service, instance=10.42.1.42:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-857c54dd97-2nzgh}` | 15 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, cluster=yogurtte-k3s-prod, container=content-service, instance=10.42.1.35:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-6c5fff897-qnxk6}` | 117 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, cluster=yogurtte-k3s-prod, container=content-service, instance=10.42.3.41:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-6c5fff897-scw7k}` | 117 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, cluster=yogurtte-k3s-prod, instance=10.42.1.248:8080, job=integrations/kubernetes/kube-state-metrics, k8s_cluster_name=yogurtte-k3s-prod, source=kubernetes}` | 117 | 1 | 1 | 1 | — |
| `mongodb_up` | `{__name__=mongodb_up, cluster=yogurtte-k3s-prod, instance=infra-server, job=mongodb, k8s_cluster_name=yogurtte-k3s-prod}` | 117 | 0 | 1 | 1 | **2026-07-29T11:54:45Z ~ 2026-07-29T11:59:30Z** |
| `kafka_brokers` | `{__name__=kafka_brokers, cluster=yogurtte-k3s-prod, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod}` | 117 | 1 | 1 | 1 | — |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=chat-service-fcm-tokens, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=0, topic=user.fcm-tokens}` | 117 | 0 | 0 | 0 | **2026-07-29T11:52:00Z ~ 2026-07-29T12:21:00Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=chat-service-fcm-tokens, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=1, topic=user.fcm-tokens}` | 117 | 0 | 0 | 0 | **2026-07-29T11:52:00Z ~ 2026-07-29T12:21:00Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=chat-service-fcm-tokens, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=2, topic=user.fcm-tokens}` | 117 | 0 | 0 | 0 | **2026-07-29T11:52:00Z ~ 2026-07-29T12:21:00Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=chat-service-notification-settings, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=0, topic=user.notification-settings}` | 117 | 0 | 0 | 0 | **2026-07-29T11:52:00Z ~ 2026-07-29T12:21:00Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=chat-service-notification-settings, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=1, topic=user.notification-settings}` | 117 | 0 | 0 | 0 | **2026-07-29T11:52:00Z ~ 2026-07-29T12:21:00Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=chat-service-notification-settings, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=2, topic=user.notification-settings}` | 117 | 0 | 0 | 0 | **2026-07-29T11:52:00Z ~ 2026-07-29T12:21:00Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=db-writer, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=0, topic=chat.messages}` | 117 | 0 | 0 | 0 | **2026-07-29T11:52:00Z ~ 2026-07-29T12:21:00Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=db-writer, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=1, topic=chat.messages}` | 117 | 0 | 0 | 0 | **2026-07-29T11:52:00Z ~ 2026-07-29T12:21:00Z** |
| `websocket_active_users` | `{__name__=websocket_active_users, application=chat-service, cluster=yogurtte-k3s-prod, container=chat-service, instance=10.42.1.39:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-857c54dd97-w7bf7}` | 52 | 0 | 0 | 0 | **2026-07-29T11:52:00Z ~ 2026-07-29T12:08:45Z** |
| `websocket_active_users` | `{__name__=websocket_active_users, application=chat-service, cluster=yogurtte-k3s-prod, container=chat-service, instance=10.42.1.42:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-857c54dd97-2nzgh}` | 15 | 0 | 0 | 0 | **2026-07-29T12:17:30Z ~ 2026-07-29T12:21:00Z** |

값이 0이던 구간은 굵게 표시했다 — 프로세스가 사라져 시계열이 꺾인 것이 유일한 신호인 장애가 있다.

