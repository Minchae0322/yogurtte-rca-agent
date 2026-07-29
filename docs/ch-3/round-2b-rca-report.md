# RCA Report — `6a69ea2063240c0918077d8cbd8c859c`

| 항목 | 값 |
|---|---|
| 모드 | rca |
| 질문 | 최근 1시간 안에 앱이 잠깐 버벅였다는 얘기가 있어요. 뭔가 있었는지 봐줘 |
| 시각 | 2026-07-29T12:30:26.536373Z |
| provider | claude-cli |
| model | `claude-opus-5` · turns 1 |
| prompt | `./prompts/system-prompt.md` |
| tokens | in 185532 (cacheRead 18,133 · cacheCreate 167,397) / out 13468 · cost $2.1508 |
| elapsed | total 196302ms (tempo 596 · loki 269 · mimir 632 · assemble 2 · llm 191239) |

## 탐색 (Triage)

| 항목 | 값 |
|---|---|
| 시간창 해석 | 상대 표현 '최근 1시간' |
| 스윕 창 | 2026-07-29T11:29:20.218759Z ~ 2026-07-29T12:29:20.218759Z |
| 좁힌 창 | 2026-07-29T11:52:00Z ~ 2026-07-29T12:22:00Z |
| 대상 | chat-service, content-service |
| traceId | 6a69ea2063240c0918077d8cbd8c859c |
| 트레이스 후보 | 7건 |
| 계획 파싱 | 성공 |
| prompt | `./prompts/triage-prompt.md` |
| tokens | in 45652 / out 4808 · cost $0.4231 |
| chars | 컨텍스트 43,500 + 프롬프트 1,196 = **44,696** |
| elapsed | survey 1259ms · llm 65053ms |

**선정 이유**: chat-service의 30초 타임아웃 시작(11:55:12), mongodb_up 0(11:59:20), ERROR 66건 폭증(12:00), 파드 소멸 후 재기동(12:04:20~12:19:20), 컨슈머 랙 25 회수(12:19:20)가 모두 이 구간에 연쇄로 들어오고 앞뒤 3분씩 여유를 뒀기 때문.

**근거**

- trace 6a69ea2063240c0918077d8cbd8c859c: content-service POST /battles/{battleId}/items/{itemId}/comments 총 31,115ms, chat-service 스팬 16개 중 8개 error, 하위 스팬 3개가 11:55:12~11:55:43 시작에 각각 30.014s / 30.281s / 30.002s로 30초 정각 타임아웃
- trace 6a69ea9d5c9fd31ded195762282d7806: chat-service root 'connection' 11:57:17 시작, 30,097ms error — 연결 수립 자체가 30초 타임아웃
- Loki ERROR/WARN: chat-service 12:00:00 버킷 66건 (직전 11:55 버킷 2건, 이후 12:20 버킷 4건 대비 급증)
- Mimir up{job="chat-service", pod="chat-service-857c54dd97-w7bf7"}: 11:59:20 샘플 결측, 12:04:20을 마지막으로 시계열 소멸 → 12:09:20·12:14:20 두 스크랩 동안 chat-service 인스턴스 0개 (없는 것 자체가 신호)
- websocket_active_users도 같은 파드에서 11:59:20 결측·12:04:20 이후 소멸, 새 파드 chat-service-857c54dd97-2nzgh는 12:19:20부터 등장 → 파드 교체 발생
- mongodb_up: 11:59:20 샘플에서 0 (앞뒤 11:54:20·12:04:20은 1) — chat-service 30초 타임아웃과 동일 시각의 유일한 인프라 이탈
- kafka_consumergroup_lag{consumergroup="notification-processors", topic="user.notifications", partition="3"}: 0 → 3(12:09:20) → 25(12:14:20) → 0(12:19:20), chat-service 부재 구간과 정확히 겹침
- kafka_consumergroup_lag{consumergroup="notification-recovery", topic="user.notifications.dlq", partition="0"}: 11:59:20에 1 — 해당 시각 알림 1건이 DLQ로 밀림
- kafka_brokers는 전 구간 1로 유지, content-service 두 파드 up도 전 구간 1 → 브로커·발행측이 아니라 소비측(chat) 문제로 좁혀짐
- 11:45:52~11:46:06의 content-service /feeds 에러 트레이스는 4~10ms 단발성이라 증상 시각·지속시간과 무관한 상시 노이즈로 판단해 제외

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

## 수집 범위 (Coverage)

- **window**: 2026-07-29T11:52:00Z ~ 2026-07-29T12:22:00Z (1800s)
- **trace**: 30,369B / 28 spans
- **logs**: errwarn=44,534B · traceId=23,535B
- **metrics**: 8 수집 / 194,545B, 누락 [sum(rate(http_server_requests_seconds_count{application="content-service", status="401"}[1m]))]
- **context**: 293,180 chars (+ 시스템 프롬프트 575 chars)

### 토큰 축 (개선 지표)

| 단계 | 총 in | chars | 컨텍스트 토큰 |
|---|---:|---:|---:|
| 탐색 | 45,652 | 44,696 | 24,343 |
| 분석 | 185,532 | 293,755 | 164,223 |
| **합계** | **231,184** | | **188,566** |

- **overheadTokens 21,309 tok — 이 회차에 실측**(1자 프롬프트 프로브, 같은 명령·모델·샌드박스).
  호출마다 한 번씩 얹히므로 단계별로 뺀다. **다른 날 상수를 쓰지 않았다 = `█ 실측`.**
- contextTokens (count_tokens API): 측정 안 됨 — 구독 CLI 경로엔 API 키가 없다
- 비용은 **API 환산 추정치**다. 구독 계정으로 호출하므로 실제 청구액이 아니다.

## 수집 실패/누락

- Metric 'sum(rate(http_server_requests_seconds_count{application="content-service", status="401"}[1m]))' returned no series in this window; skipped.

---

## 1. 원인 후보 랭킹 (최대 3개)

1. **MongoDB 인스턴스(172.31.46.124:27017) 중단** — 11:54:45~11:59:45 UTC(20:54:45~20:59:45 KST) 약 5분. chat-service의 알림 저장 실패 → 4회 재시도 소진 → DLQ의 직접 원인.
2. **chat-service 파드(w7bf7) 소실 후 신규 파드(2nzgh) 기동까지 약 9분간 컨슈머 공백** — 12:08:45~12:17:30 UTC. `user.notifications` partition 3 lag 최대 25건 적체. (Mongo 복구 이후에 발생한 **별개의 2차 사건**)
3. **Mongo 리액티브 헬스체크가 30초 블로킹하며 chat-service Tomcat 스레드를 점유** — 사용자 체감 "버벅임"의 유력 후보 메커니즘이나 직접 계측값 없음.

---

## 2. 후보별 근거

### 후보 1. MongoDB 인스턴스 중단

**근거**
- 메트릭 `mongodb_up{instance="infra-server"}`: `1785326070`(11:54:30 UTC)까지 `1` → `1785326085`(11:54:45 UTC)부터 `1785326370`(11:59:30 UTC)까지 **연속 `0`** → `1785326385`(11:59:45 UTC) `1`로 복구. 약 4분 45초~5분.
- 트레이스 `receive`(SPAN_KIND_CONSUMER, `user.notifications`, partition=3, offset=1004) 및 하위 `user-notification-service#process-notification` 스팬 4개 모두 `STATUS_CODE_ERROR`, error 속성 원문:
  `"Timed out while waiting for a server that matches WritableServerSelector ... {com.mongodb.MongoSocketOpenException: Exception opening socket}, caused by {io.netty...AnnotatedConnectException: Connection refused: /172.31.46.124:27017}, caused by {java.net.ConnectException: Connection refused}"`
- 재시도 4회 각각의 소요시간이 MongoClient serverSelectionTimeout(30초)에 정확히 수렴: 30.281s / 30.022s / 30.015s / 30.017s.
- 로그 원문(KST):
  - `20:55:42.904 [KAFKA-RETRY] user-notification 처리 실패 1회차: topic=user.notifications partition=3 offset=1004 cause=com.mongodb.MongoTimeoutException...` (2·3·4회차 20:56:13 / 20:56:44 / 20:57:16 동일)
  - `20:57:16.011 [KAFKA-DLQ] 발행: user.notifications -> user.notifications.dlq (partition=3 offset=1004)`
  - `20:57:17.108 [KAFKA-RETRY] user-notification 재시도 소진 - recoverer 처리 완료`
  - `20:57:47.109 [Kafka] DLQ 알림 재처리 실패 (1분 후 재시도): userId=7, type=BATTLE_ITEM_COMMENT` + `[KAFKA-RETRY] user-notification-dlq 처리 실패 1회차: topic=user.notifications.dlq partition=0 offset=13`
- `MongoReactiveHealthIndicator: Mongo health check failed` 가 20:54:50.248부터 20:59:11.329까지 10초 간격으로 반복, 매회 `(mongo) took 30001ms to respond`. 20:59:16에 `14561ms`, `24637ms`로 떨어지며 복구 궤적과 일치.
- 발행 시각(`[Kafka] 알림 발행 성공: ... partition=3, offset=1004`, 20:55:12.522)부터 DLQ 완료(20:57:17.108)까지 **알림 1건이 약 124초 지연 후 실패**.

**확신도: 높음**

**반증 데이터**
- `up{job="mongodb"}`는 조회 구간 전체에서 `1` 유지 → 호스트/익스포터는 살아있었음. 즉 노드·네트워크 장애가 아니라 **mongod 프로세스 레벨 중단**으로 범위가 좁혀짐(후보 자체를 부정하지는 않음).
- 사용자 요청 경로는 정상이었음: `http post /battles/{battleId}/items/{itemId}/comments` 스팬 status=200, `[HTTP] POST /api/battles/22/items/125/comments 200 - 43ms`, MySQL 쿼리 4건 모두 수 ms, Redis GET 0.55ms. → **"앱 버벅임"을 Mongo 다운만으로는 설명할 수 없음.**
- Mongo 복구(11:59:45 UTC) 이후인 12:09~12:18 UTC에 lag 적체가 발생 → 2차 사건은 Mongo와 인과 없음.

---

### 후보 2. chat-service 파드 소실 → 컨슈머 공백 (약 9분)

**근거**
- 파드 `chat-service-857c54dd97-w7bf7`(10.42.1.39)의 모든 시리즈(`up`, `hikaricp_*`, `websocket_active_users`)가 `1785326925`(**12:08:45 UTC**) 샘플을 마지막으로 소멸.
- 신규 파드 `chat-service-857c54dd97-2nzgh`(10.42.1.42)의 첫 샘플은 `1785327450`(**12:17:30 UTC**). 기동 로그 `21:15:57.250 [main] JpaBaseConfiguration$JpaWebConfiguration : spring.jpa.open-in-view is enabled by default`(=12:15:57 UTC) 가 콜드 스타트를 뒷받침.
- `kafka_consumergroup_lag{consumergroup="notification-processors", topic="user.notifications", partition="3"}`: 12:08:45까지 `0` → **12:09:00에 `3`** → 30초마다 +3씩 증가 → 12:14:00부터 `25` 고정 → **12:18:00에 `0`**. 소실 시각과 적체 시작이 15초(1스크레이프) 이내로 일치하고, 신규 파드 기동 30초 내에 해소.
- ReplicaSet 해시(`857c54dd97`)가 동일 → 신규 배포가 아니라 **동일 세대 파드의 교체**.
- 참고: 1차 사건 때도 같은 파티션 lag가 `1785326130~1785326235`(11:55:30~11:57:15 UTC) 동안 `1`로 떠 있다가 DLQ 발행 시점에 `0`으로 복귀 → offset 1004 1건이 정확히 막혀 있었음을 교차 확인.

**확신도: 중간** (현상=컨슈머 공백과 알림 적체는 확실. **종료 사유는 데이터 부족**)

**반증 데이터**
- 파드 종료 사유를 판별할 관측값이 없음: 재시작 카운터(`kube_pod_container_status_restarts_total`), K8s 이벤트, 이전 컨테이너 로그 모두 미수집. OOMKilled / liveness 실패 / eviction / 수동 재시작을 구분할 수 없음.
- 자원 고갈 징후 없음: `jvm_gc_pause_seconds` rate 최대 0.0003 s/s(무시 가능), `hikaricp_connections_active` 0~1, `hikaricp_connections_pending` 0, `websocket_active_users` 0.
- 노드 `ip-172-31-45-39`의 kubelet/cadvisor/node_exporter `up`은 전 구간 `1` → 노드 다운 아님.
- Kafka도 정상: `kafka_brokers=1`, `up{job="kafka"}=1` 전 구간 유지 → 브로커 측 원인 아님.

---

### 후보 3. 30초 블로킹 헬스체크로 인한 chat-service 응답 지연 (체감 "버벅임" 메커니즘)

**근거**
- `HealthEndpointSupport : Health contributor ...(mongo) took 30001ms to respond` 가 20:54:50 ~ 20:59:11 사이 **10초 간격으로 27회** 기록. 처리 스레드는 `nio-8090-exec-1` ~ `exec-10`로 매번 다름 → 헬스체크 1건이 30초간 Tomcat exec 스레드를 점유, 상시 3개 내외가 묶여 있었음. chat-service는 애플리케이션·액추에이터가 동일 8090 스레드 풀을 공유(로그 스레드명 `nio-8090-exec-N`, 스크레이프 대상 `10.42.1.39:8090`).
- 같은 구간에 **Prometheus 스크레이프 결손**: chat-service w7bf7의 모든 시리즈가 `1785326145`(11:55:45 UTC) → `1785326400`(12:00:00 UTC) 사이 4분 15초 공백. Mongo 다운 구간과 정확히 겹침 → 관리 포트가 실제로 응답 불가/지연 상태였음을 시사.
- 신규 파드에서도 유사 패턴: `21:16:54.003 ... (redis) took 12399ms to respond`, `21:16:54.006 ... (redis) took 11054ms`.

**확신도: 중간**

**반증 데이터**
- `websocket_active_users`는 두 파드 모두 전 구간 `0` → 해당 파드에 실사용 커넥션이 있었다는 증거 없음.
- chat-service의 `http_server_requests_seconds` 계열 메트릭이 수집되지 않아 **사용자 요청 지연을 정량화할 수 없음**.
- DB 풀 고갈은 근거 없음: 트레이스에 `connection`(HikariPool-1, `com.mysql.cj.jdbc.Driver`) 스팬이 30초씩 4회 잡히고 `acquired`→`rollback` 이벤트가 있으나, 같은 시각 `hikaricp_connections_active=0`, `pending=0`. 물리 커넥션은 실제로 점유되지 않은 것으로 보임(SQL 미실행).
- 신규 파드의 redis 지연은 기동 직후 1회성이고 `up{job="redis"}`=1, 트레이스의 Redis GET은 0.55ms → 상시 문제라고 볼 근거 없음.

**공통 확신도 하향 요인**
- `sum(rate(http_server_requests_seconds_count{application="content-service", status="401"}[1m]))` 시리즈 없음 → 인증(auth) 관련 원인은 **판단 불가**. 다만 auth-service `up`=1, hikari 0/0, GC 정상으로 auth 이상을 시사하는 다른 관측값도 없음.
- 사용자 체감 지연을 직접 보여주는 지표(API 레이턴시 히스토그램, 인그레스 로그)가 전혀 없어, "버벅임"과 위 사건들의 연결은 **정황 일치 수준**임.

---

## 3. 권장 다음 조치

**즉시 확인 (유실 여부)**
1. `user.notifications.dlq` partition=0 **offset=13**(userId=7, type=BATTLE_ITEM_COMMENT) 최종 처리 여부 확인. 20:57:47 DLQ 재처리 1회차 실패 이후 해당 파드가 사라져 후속 로그가 없음 → **알림 1건 유실 가능성**. 신규 파드에서 재처리됐는지 `user_notifications` 컬렉션 조회로 검증.
2. 12:09~12:18 UTC 적체된 25건이 전부 소비·저장됐는지 확인(lag는 0으로 복귀했으나 성공/DLQ 여부는 별도 확인 필요).

**원인 확정**
3. MongoDB 호스트(`172.31.46.124`, infra-server)에서 `journalctl -u mongod --since "2026-07-29 20:50" --until "2026-07-29 21:05"` 및 mongod 로그 확인 — OOM kill / 수동 재시작 / 크래시 구분. `up`=1인데 `mongodb_up`=0이었으므로 **프로세스 레벨 중단**이 유력.
4. `kubectl get events --field-selector involvedObject.name=chat-service-857c54dd97-w7bf7`, `kubectl describe pod chat-service-857c54dd97-2nzgh`(lastState.terminated), 노드 dmesg — 파드 교체 사유(OOMKilled / liveness 실패 / eviction) 확정.

**재발 방지**
5. MongoDB 단일 인스턴스 구성 확인. 에러의 `servers=[{address=172.31.46.124:27017}]`는 단일 노드 토폴로지를 의미 → 레플리카셋 미구성이면 단일 장애점.
6. `spring.data.mongodb.uri`의 `serverSelectionTimeoutMS`를 30초 → 3~5초로 축소. 현재 4회 재시도 = 최소 120초 블로킹.
7. liveness/readiness probe가 `/actuator/health` 전체를 보고 있는지 확인. `management.endpoint.health.group.liveness.include`에서 mongo/redis 제외(외부 의존성 장애로 파드가 죽는 구조 차단) + `management.endpoint.health.probes.enabled=true`.
8. Kafka 리스너에 별도 스레드풀 분리 또는 액추에이터 관리 포트를 앱 포트와 분리(`management.server.port`) — 현재 chat-service는 8090 단일 풀 공유로 보임(content-service는 8082/8090 분리되어 있음).

**관측 공백 보완**
9. 알람 추가: `mongodb_up == 0` 1분 지속, `kafka_consumergroup_lag{consumergroup="notification-processors"} > 0` 5분 지속, `up{job="chat-service"}` 시리즈 소멸.
10. `21:17:05 PrometheusMeterRegistry ... 'spring_kafka_listener_seconds' ... registration has failed` 해소 — 태그 키 불일치로 **Kafka 리스너 처리시간/실패 메트릭이 아예 수집되지 않고 있음**. 이번 분석에서 재시도 지연을 로그·트레이스로만 확인해야 했던 원인.
11. `http_server_requests_seconds`(chat-service 포함) 및 `kube_pod_container_status_restarts_total` 수집 활성화. 이번 건처럼 "체감 지연"을 검증할 수단이 없음.
12. content-service 401 메트릭 시리즈 부재 원인 확인(해당 구간에 401이 0건이라 시리즈가 없는 것인지, 수집 자체가 안 되는 것인지).

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
| `hikaricp_connections_active` | `{__name__=hikaricp_connections_active, application=auth-service, cluster=yogurtte-k3s-prod, container=auth-service, instance=10.42.1.41:8090, job=auth-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=auth-service-855c75679d-vpkqw, pool=HikariPool-1, service=auth-service}` | 121 | 0 | 0 | 0 | **2026-07-29T11:52:00Z ~ 2026-07-29T12:22:00Z** |
| `hikaricp_connections_active` | `{__name__=hikaricp_connections_active, application=chat-service, cluster=yogurtte-k3s-prod, container=chat-service, instance=10.42.1.39:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-857c54dd97-w7bf7, pool=HikariPool-1}` | 52 | 0 | 1 | 1 | **2026-07-29T11:52:00Z ~ 2026-07-29T12:07:45Z** |
| `hikaricp_connections_active` | `{__name__=hikaricp_connections_active, application=chat-service, cluster=yogurtte-k3s-prod, container=chat-service, instance=10.42.1.42:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-857c54dd97-2nzgh, pool=HikariPool-1}` | 19 | 0 | 1 | 0 | **2026-07-29T12:18:30Z ~ 2026-07-29T12:22:00Z** |
| `hikaricp_connections_active` | `{__name__=hikaricp_connections_active, application=content-service, cluster=yogurtte-k3s-prod, container=content-service, instance=10.42.1.35:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-6c5fff897-qnxk6, pool=HikariPool-1}` | 121 | 0 | 1 | 0 | **2026-07-29T11:52:15Z ~ 2026-07-29T12:00:00Z, 2026-07-29T12:01:15Z ~ 2026-07-29T12:05:00Z, 2026-07-29T12:06:15Z ~ 2026-07-29T12:10:00Z, 2026-07-29T12:11:15Z ~ 2026-07-29T12:14:00Z, 2026-07-29T12:15:15Z ~ 2026-07-29T12:22:00Z** |
| `hikaricp_connections_active` | `{__name__=hikaricp_connections_active, application=content-service, cluster=yogurtte-k3s-prod, container=content-service, instance=10.42.3.41:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-6c5fff897-scw7k, pool=HikariPool-1}` | 121 | 0 | 0 | 0 | **2026-07-29T11:52:00Z ~ 2026-07-29T12:22:00Z** |
| `hikaricp_connections_pending` | `{__name__=hikaricp_connections_pending, application=auth-service, cluster=yogurtte-k3s-prod, container=auth-service, instance=10.42.1.41:8090, job=auth-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=auth-service-855c75679d-vpkqw, pool=HikariPool-1, service=auth-service}` | 121 | 0 | 0 | 0 | **2026-07-29T11:52:00Z ~ 2026-07-29T12:22:00Z** |
| `hikaricp_connections_pending` | `{__name__=hikaricp_connections_pending, application=chat-service, cluster=yogurtte-k3s-prod, container=chat-service, instance=10.42.1.39:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-857c54dd97-w7bf7, pool=HikariPool-1}` | 52 | 0 | 0 | 0 | **2026-07-29T11:52:00Z ~ 2026-07-29T12:08:45Z** |
| `hikaricp_connections_pending` | `{__name__=hikaricp_connections_pending, application=chat-service, cluster=yogurtte-k3s-prod, container=chat-service, instance=10.42.1.42:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-857c54dd97-2nzgh, pool=HikariPool-1}` | 19 | 0 | 0 | 0 | **2026-07-29T12:17:30Z ~ 2026-07-29T12:22:00Z** |
| `hikaricp_connections_pending` | `{__name__=hikaricp_connections_pending, application=content-service, cluster=yogurtte-k3s-prod, container=content-service, instance=10.42.1.35:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-6c5fff897-qnxk6, pool=HikariPool-1}` | 121 | 0 | 0 | 0 | **2026-07-29T11:52:00Z ~ 2026-07-29T12:22:00Z** |
| `hikaricp_connections_pending` | `{__name__=hikaricp_connections_pending, application=content-service, cluster=yogurtte-k3s-prod, container=content-service, instance=10.42.3.41:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-6c5fff897-scw7k, pool=HikariPool-1}` | 121 | 0 | 0 | 0 | **2026-07-29T11:52:00Z ~ 2026-07-29T12:22:00Z** |
| `rate(jvm_gc_pause_seconds_sum[5m])` | `{action=end of major GC, application=chat-service, cause=Allocation Failure, cluster=yogurtte-k3s-prod, container=chat-service, gc=MarkSweepCompact, instance=10.42.1.39:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-857c54dd97-w7bf7}` | 72 | 0 | 0 | 0 | **2026-07-29T11:52:00Z ~ 2026-07-29T12:11:45Z** |
| `rate(jvm_gc_pause_seconds_sum[5m])` | `{action=end of minor GC, application=auth-service, cause=G1 Evacuation Pause, cluster=yogurtte-k3s-prod, container=auth-service, gc=G1 Young Generation, instance=10.42.1.41:8090, job=auth-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=auth-service-855c75679d-vpkqw, service=auth-service}` | 121 | 0 | 0.000 | 0 | **2026-07-29T11:52:00Z ~ 2026-07-29T11:57:45Z, 2026-07-29T12:02:00Z ~ 2026-07-29T12:22:00Z** |
| `rate(jvm_gc_pause_seconds_sum[5m])` | `{action=end of minor GC, application=chat-service, cause=Allocation Failure, cluster=yogurtte-k3s-prod, container=chat-service, gc=Copy, instance=10.42.1.39:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-857c54dd97-w7bf7}` | 72 | 0.000 | 0.000 | 0.000 | — |
| `rate(jvm_gc_pause_seconds_sum[5m])` | `{action=end of minor GC, application=chat-service, cause=Allocation Failure, cluster=yogurtte-k3s-prod, container=chat-service, gc=Copy, instance=10.42.1.42:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-857c54dd97-2nzgh}` | 15 | 0.001 | 0.001 | 0.001 | — |
| `rate(jvm_gc_pause_seconds_sum[5m])` | `{action=end of minor GC, application=content-service, cause=Allocation Failure, cluster=yogurtte-k3s-prod, container=content-service, gc=Copy, instance=10.42.1.35:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-6c5fff897-qnxk6}` | 121 | 0 | 0.000 | 0.000 | **2026-07-29T11:52:00Z ~ 2026-07-29T11:55:00Z, 2026-07-29T11:59:15Z ~ 2026-07-29T12:05:00Z, 2026-07-29T12:09:15Z ~ 2026-07-29T12:12:00Z, 2026-07-29T12:16:15Z ~ 2026-07-29T12:21:00Z** |
| `rate(jvm_gc_pause_seconds_sum[5m])` | `{action=end of minor GC, application=content-service, cause=Allocation Failure, cluster=yogurtte-k3s-prod, container=content-service, gc=Copy, instance=10.42.3.41:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-6c5fff897-scw7k}` | 121 | 0 | 0.000 | 0.000 | **2026-07-29T11:52:00Z ~ 2026-07-29T11:52:45Z, 2026-07-29T11:57:00Z ~ 2026-07-29T12:02:45Z, 2026-07-29T12:07:00Z ~ 2026-07-29T12:11:45Z, 2026-07-29T12:16:00Z ~ 2026-07-29T12:20:45Z** |
| `up` | `{__name__=up, app=node-exporter, cluster=yogurtte-k3s-prod, component=metrics, container=node-exporter, instance=ip-172-31-40-241, job=integrations/node_exporter, k8s_cluster_name=yogurtte-k3s-prod, namespace=monitoring, pod=grafana-k8s-monitoring-node-exporter-cph5l, source=kubernetes, workload=DaemonSet/grafana-k8s-monitoring-node-exporter}` | 121 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, app=node-exporter, cluster=yogurtte-k3s-prod, component=metrics, container=node-exporter, instance=ip-172-31-45-39, job=integrations/node_exporter, k8s_cluster_name=yogurtte-k3s-prod, namespace=monitoring, pod=grafana-k8s-monitoring-node-exporter-9kvqt, source=kubernetes, workload=DaemonSet/grafana-k8s-monitoring-node-exporter}` | 121 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, cluster=yogurtte-k3s-prod, container=auth-service, instance=10.42.1.41:8090, job=auth-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=auth-service-855c75679d-vpkqw}` | 121 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, cluster=yogurtte-k3s-prod, container=chat-service, instance=10.42.1.39:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-857c54dd97-w7bf7}` | 52 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, cluster=yogurtte-k3s-prod, container=chat-service, instance=10.42.1.42:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-857c54dd97-2nzgh}` | 19 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, cluster=yogurtte-k3s-prod, container=content-service, instance=10.42.1.35:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-6c5fff897-qnxk6}` | 121 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, cluster=yogurtte-k3s-prod, container=content-service, instance=10.42.3.41:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-6c5fff897-scw7k}` | 121 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, cluster=yogurtte-k3s-prod, instance=10.42.1.248:8080, job=integrations/kubernetes/kube-state-metrics, k8s_cluster_name=yogurtte-k3s-prod, source=kubernetes}` | 121 | 1 | 1 | 1 | — |
| `mongodb_up` | `{__name__=mongodb_up, cluster=yogurtte-k3s-prod, instance=infra-server, job=mongodb, k8s_cluster_name=yogurtte-k3s-prod}` | 121 | 0 | 1 | 1 | **2026-07-29T11:54:45Z ~ 2026-07-29T11:59:30Z** |
| `kafka_brokers` | `{__name__=kafka_brokers, cluster=yogurtte-k3s-prod, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod}` | 121 | 1 | 1 | 1 | — |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=chat-service-fcm-tokens, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=0, topic=user.fcm-tokens}` | 121 | 0 | 0 | 0 | **2026-07-29T11:52:00Z ~ 2026-07-29T12:22:00Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=chat-service-fcm-tokens, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=1, topic=user.fcm-tokens}` | 121 | 0 | 0 | 0 | **2026-07-29T11:52:00Z ~ 2026-07-29T12:22:00Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=chat-service-fcm-tokens, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=2, topic=user.fcm-tokens}` | 121 | 0 | 0 | 0 | **2026-07-29T11:52:00Z ~ 2026-07-29T12:22:00Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=chat-service-notification-settings, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=0, topic=user.notification-settings}` | 121 | 0 | 0 | 0 | **2026-07-29T11:52:00Z ~ 2026-07-29T12:22:00Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=chat-service-notification-settings, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=1, topic=user.notification-settings}` | 121 | 0 | 0 | 0 | **2026-07-29T11:52:00Z ~ 2026-07-29T12:22:00Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=chat-service-notification-settings, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=2, topic=user.notification-settings}` | 121 | 0 | 0 | 0 | **2026-07-29T11:52:00Z ~ 2026-07-29T12:22:00Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=db-writer, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=0, topic=chat.messages}` | 121 | 0 | 0 | 0 | **2026-07-29T11:52:00Z ~ 2026-07-29T12:22:00Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=db-writer, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=1, topic=chat.messages}` | 121 | 0 | 0 | 0 | **2026-07-29T11:52:00Z ~ 2026-07-29T12:22:00Z** |
| `websocket_active_users` | `{__name__=websocket_active_users, application=chat-service, cluster=yogurtte-k3s-prod, container=chat-service, instance=10.42.1.39:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-857c54dd97-w7bf7}` | 52 | 0 | 0 | 0 | **2026-07-29T11:52:00Z ~ 2026-07-29T12:08:45Z** |
| `websocket_active_users` | `{__name__=websocket_active_users, application=chat-service, cluster=yogurtte-k3s-prod, container=chat-service, instance=10.42.1.42:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-857c54dd97-2nzgh}` | 19 | 0 | 0 | 0 | **2026-07-29T12:17:30Z ~ 2026-07-29T12:22:00Z** |

값이 0이던 구간은 굵게 표시했다 — 프로세스가 사라져 시계열이 꺾인 것이 유일한 신호인 장애가 있다.

