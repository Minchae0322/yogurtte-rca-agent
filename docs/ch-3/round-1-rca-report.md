# RCA Report — `6a6988a1539ec8bf5f46e52f9b611344`

| 항목 | 값 |
|---|---|
| 모드 | rca |
| 질문 | 최근 1시간 안에 앱이 잠깐 버벅였다는 얘기가 있어요. 뭔가 있었는지 봐줘 |
| 시각 | 2026-07-29T05:46:38.586310800Z |
| provider | claude-cli |
| model | `claude-opus-5` · turns 1 |
| prompt | `.\prompts\system-prompt.md` |
| tokens | in 166659 (cacheRead 23,453 · cacheCreate 143,204) / out 12868 · cost $1.8772 |
| elapsed | total 198028ms (tempo 528 · loki 320 · mimir 851 · assemble 1 · llm 190612) |

## 탐색 (Triage)

| 항목 | 값 |
|---|---|
| 시간창 해석 | 상대 표현 '최근 1시간' |
| 스윕 창 | 2026-07-29T04:45:40.734758900Z ~ 2026-07-29T05:45:40.734758900Z |
| 좁힌 창 | 2026-07-29T04:57:00Z ~ 2026-07-29T05:20:00Z |
| 대상 | chat-service, content-service |
| traceId | 6a6988a1539ec8bf5f46e52f9b611344 |
| 트레이스 후보 | 4건 |
| 계획 파싱 | 성공 |
| prompt | `.\prompts\triage-prompt.md` |
| tokens | in 49826 / out 3986 · cost $0.3932 |
| chars | 컨텍스트 41,907 + 프롬프트 1,231 = **43,138** |
| elapsed | survey 1438ms · llm 56409ms |

**선정 이유**: chat-service 파드 소실·교체(05:00~05:15)에 로그 급증·Mongo down 샘플·notification lag·30초 타임아웃 트레이스가 모두 정렬되므로, 앞뒤 여유를 둔 04:57~05:20 구간에서 chat-service를 중심으로 (호출측 content-service 포함) 파고들면 된다.

**근거**

- up{job="chat-service", pod="chat-service-857c54dd97-s5fbl"}: 05:00:40 샘플 결측, 05:05:40 이후 시계열 소멸 — 05:10:40에는 chat-service 스크레이프 대상이 0개
- up{pod="chat-service-857c54dd97-w7bf7"}: 05:15:40부터 신규 등장 (동일 ReplicaSet 857c54dd97 → 배포가 아닌 파드 재기동/재스케줄)
- Loki ERROR/WARN rate chat-service: 05:00:00 18건 → 05:05:00 54건(피크) → 05:15:00 2건으로 급감, content-service는 같은 구간 0건
- mongodb_up{instance="infra-server"} = 0 at 05:00:40 (전후 구간은 모두 1) — chat-service 로그 급증 시각과 일치
- kafka_consumergroup_lag{consumergroup="notification-processors", topic="user.notifications", partition="3"}: 05:00:40 = 1 → 05:10:40 = 24 → 05:15:40 = 0, 소비자 부재 구간과 정확히 겹침
- Tempo: content-service 루트 트레이스 6a6988a1... 가 05:00:15.2부터 30.0s 단위 타임아웃 스팬 연쇄(chat-service 17스팬 중 8개 error) — content → Kafka → chat 경로가 이 구간에 막힘
- Tempo: chat-service root 'connection' 트레이스 2건이 05:01:17.5, 05:02:47.6에 각각 30.0s 만료로 error 종료
- websocket_active_users 시계열도 동일하게 05:00:40 / 05:10:40 결측 — chat-service 프로세스 자체가 부재했음을 뒷받침

**스윕이 찾은 트레이스** (고른 것은 6a6988a1539ec8bf5f46e52f9b611344)

| traceId | root service | root span | ms |
|---|---|---|---:|
| `6a698fdc92f000a99d49a082e4722fb3` | content-service | http get /feeds/following | 15 |
| `6a6989774b7cb272fdddcf55fc3af6bd` | chat-service | connection | 30008 |
| `6a69891d13bd030e799c1ef73cc0090a` | chat-service | connection | 30086 |
| `6a6988a1539ec8bf5f46e52f9b611344` ←선택 | content-service | http post /battles/{battleId}/items/{itemId}/comments | 2889849755 |

## 수집 범위 (Coverage)

- **window**: 2026-07-29T04:57:00Z ~ 2026-07-29T05:20:00Z (1380s)
- **trace**: 29,684B / 28 spans
- **logs**: errwarn=45,752B · traceId=23,523B
- **metrics**: 8 수집 / 153,073B, 누락 [sum(rate(http_server_requests_seconds_count{application="content-service", status="401"}[1m]))]
- **context**: 252,193 chars (+ 시스템 프롬프트 597 chars)

### 토큰 축 (개선 지표)

| 단계 | 총 in | chars | 컨텍스트 토큰 |
|---|---:|---:|---:|
| 탐색 | 49,826 | 43,138 | 23,250 |
| 분석 | 166,659 | 252,790 | 140,083 |
| **합계** | **216,485** | | **163,333** |

- **overheadTokens 26,576 tok — 이 회차에 실측**(1자 프롬프트 프로브, 같은 명령·모델·샌드박스).
  호출마다 한 번씩 얹히므로 단계별로 뺀다. **다른 날 상수를 쓰지 않았다 = `█ 실측`.**
- contextTokens (count_tokens API): 측정 안 됨 — 구독 CLI 경로엔 API 키가 없다
- 비용은 **API 환산 추정치**다. 구독 계정으로 호출하므로 실제 청구액이 아니다.

## 수집 실패/누락

- Metric 'sum(rate(http_server_requests_seconds_count{application="content-service", status="401"}[1m]))' returned no series in this window; skipped.

---

# 장애 분석: traceId `6a6988a1539ec8bf5f46e52f9b611344`

**타임라인 요약 (KST / UTC)**
- 13:58:45 ~ 14:03:30 (04:58:45~05:03:30Z): `mongodb_up` = 0 (약 4분 45초)
- 13:59:13: content-service 댓글 API 정상 처리(200, 57ms) → Kafka `user.notifications` p3 offset=963 발행 성공
- 13:59:13 ~ 14:01:17: chat-service 컨슈머가 30초 타임아웃 × 4회 실패 → DLQ 발행 (총 2분 4초 소요)
- 14:04:15~14:04:30: DLQ(offset=12) lag 0으로 소진 → 뒤늦게 처리 완료 추정
- 14:06 ~ 14:15: chat-service 파드 메트릭 전면 부재, `user.notifications` p3 lag 0→25 누적 → 14:15:30 lag 0 복구

---

## 1. 원인 후보 랭킹 (최대 3개)

1. **MongoDB(mongod) 프로세스 다운 — 172.31.46.124:27017 접속 거부 (13:58:45~14:03:30)**
2. **chat-service 파드 교체/소실로 인한 컨슈머 공백 (14:06~14:15) → 알림 최대 ~9분 지연**
3. **증폭 요인: Mongo 드라이버 30초 서버선택 타임아웃 × 4회 재시도를 단일 리스너 스레드가 동기 블로킹 (JDBC 트랜잭션까지 30초 점유)**

---

## 2. 후보별 근거

### 후보 1. MongoDB 프로세스 다운

- **근거**
  - 메트릭 `mongodb_up`: `[1785301110,"1"]` → `[1785301125,"0"]` … `[1785301410,"0"]` → `[1785301425,"1"]`. 즉 **04:58:45Z ~ 05:03:30Z (KST 13:58:45~14:03:30), 약 4분 45초 동안 0**.
  - 예외 원문(트레이스 span `user-notification-service#process-notification`, 로그 `KafkaConsumerConfig` 공통): `com.mongodb.MongoTimeoutException: Timed out while waiting for a server that matches WritableServerSelector ... {com.mongodb.MongoSocketOpenException: Exception opening socket}, caused by {io.netty.channel.AbstractChannel$AnnotatedConnectException: Connection refused: /172.31.46.124:27017}, caused by {java.net.ConnectException: Connection refused}`
    → **timeout이 아니라 `Connection refused`** = 포트에서 리슨하는 프로세스가 없음(네트워크 차단·부하가 아니라 프로세스 다운 패턴).
  - `MongoReactiveHealthIndicator: Mongo health check failed` 이 13:58:42부터 10초 간격으로 반복, 동반 로그 `Health contributor ... (mongo) took 30004ms to respond`.
  - 영향 경로: `MongoTemplate: Inserting Document ... in collection: user_notifications` → 실패 → `[Kafka] 알림 처리 실패: userId=7, type=BATTLE_ITEM_COMMENT` (13:59:43 / 14:00:14 / 14:00:45 / 14:01:16) → `[KAFKA-DLQ] 발행: user.notifications -> user.notifications.dlq (partition=3 offset=963)`.
- **확신도: 높음**
- **반증 데이터**
  - `up{job="mongodb"}`는 구간 내내 1 → **exporter/호스트는 살아있었음**. 호스트 장애가 아니라 mongod 한정 장애임을 시사(후보를 약화시키기보다 범위를 좁힘).
  - 동일 호스트 IP `172.31.46.124`의 **Redis는 정상**: content-service span `GET`(`db.system=redis`, `net.sock.peer.addr=172.31.46.124:6379`) 0.6ms 성공 → 호스트/네트워크 전면 장애 아님.
  - 시작 시각 불일치: 13:58:42에 기록된 헬스체크는 "30004ms 소요"이므로 **실제 시작은 13:58:12경**일 수 있으나, `mongodb_up`은 13:58:30까지 1. 15초 스크랩 간격/헬스체크 타임아웃 특성상 **개시 시각은 메트릭보다 최대 30초 이를 수 있음**.

### 후보 2. chat-service 파드 공백 (14:06~14:15)

- **근거**
  - `up{pod="chat-service-857c54dd97-s5fbl"}` 시계열이 **1785301140(04:59:00Z) 이후 끊겼다가 1785301455~1785301560(05:04:15~05:06:00Z)만 존재하고 그 뒤 완전 소멸**. 신규 파드 `chat-service-857c54dd97-w7bf7`는 **1785302100(05:15:00Z=14:15:00)부터 등장**, 기동 로그는 `2026-07-29T14:13:18.845 ... [main] JpaBaseConfiguration$JpaWebConfiguration`.
  - `kafka_consumergroup_lag{consumergroup="notification-processors", topic="user.notifications", partition="3"}`: 1785301560(14:06)=2 → 1785301860(14:11)=25 → 1785302115(14:15:15)까지 25 유지 → **1785302130(14:15:30)에 0**. 즉 **약 9분간 소비 정지, 25건 지연 후 일괄 소비**.
  - 첫 번째 공백(04:59~05:04:15)은 Mongo 복구(05:03:45 `mongodb_up`=1) 직후 타깃이 되살아난 패턴 → **헬스체크 30초 지연에 의한 readiness 실패로 엔드포인트에서 탈락**했을 가능성과 정합.
- **확신도: 중간** (파드가 교체되었다는 *사실*은 높음, 교체 *원인*은 낮음)
- **반증 데이터**
  - **두 번째 소멸 시점(14:06)은 Mongo가 이미 복구된 뒤**다. 14:03:18 이후 s5fbl의 WARN/ERROR 로그도 사라졌다(=헬스체크 정상화). 따라서 "Mongo 때문에 파드가 죽었다"는 설명은 **타이밍이 맞지 않음**.
  - 파드 재시작 횟수·종료 사유(OOMKilled/probe 실패/롤아웃/eviction)를 볼 수 있는 kube_state_metrics·K8s Event를 수집하지 않았으므로 **종료 원인은 데이터 부족**.

### 후보 3. 30초 블로킹 재시도로 인한 증폭

- **근거**
  - 단일 리스너 스레드 `[ntainer#5-1-C-1]`, `spring.kafka.listener.id=KafkaListenerEndpointContainer#5-1`에서 4회 시도가 **순차 직렬**로 수행: 13:59:13.076→13:59:43.185, 13:59:44.215→14:00:14.230, 14:00:15.235→14:00:45.250, 14:00:46.256→14:01:16.272. **메시지 1건에 2분 4초 점유.**
  - 로그 `org.mongodb.driver.cluster: Waiting for server to become available for operation with ID 121236. Remaining time: 29999 ms` → **serverSelectionTimeout이 30초**로 설정되어 있음.
  - 트레이스의 `connection` span(`jdbc.datasource.pool=HikariPool-1`, `jdbc.datasource.name=content`)이 `acquired`(+2ms) 후 **30초 뒤 `rollback`** → Mongo 호출이 MySQL 트랜잭션 안에서 이루어져 **커넥션을 30초간 점유**.
  - DLQ 컨슈머도 동일 패턴 반복: `[KAFKA-RETRY] user-notification-dlq 처리 실패 1회차/2회차: topic=user.notifications.dlq partition=0 offset=12`, `[Kafka] DLQ 알림 재처리 실패 (1분 후 재시도)` (14:01:47, 14:03:17).
- **확신도: 중간** (동작은 확정, 실제 사용자 체감 악화 기여도는 미검증)
- **반증 데이터**
  - `hikaricp_connections_active`/`pending`은 chat-service·content-service 모두 스크랩된 시점에서 **전부 0~1**, pending은 내내 0 → **이번 사건에서 풀 고갈은 발생하지 않음**(다만 장애 구간 스크랩 자체가 결측이라 관측 공백 있음).
  - `jvm_gc_pause` 최대 0.00075s, `kafka_brokers`=1 유지, `up{job=kafka|redis|node-infra}` 모두 1 → GC·Kafka·Redis 요인 아님.

### 기각/판단 불가한 후보

- **content-service 자체 지연**: 반증 명확. `http post /battles/{battleId}/items/{itemId}/comments` span 57.8ms, `outcome=SUCCESS`, `status=200`, 로그 `[HTTP] POST /api/battles/22/items/125/comments 200 - 57ms`, 캐시 `HIT - userId: 1, elapsed: 2ms`. **쓰기 경로는 정상.**
- **auth/인증 문제**: **데이터 부족**. 401 메트릭(`http_server_requests_seconds_count{application="content-service", status="401"}`) 수집 실패로 검증 불가. 다만 트레이스상 `JwtAuthenticationFilter` 포함 12단계 필터체인 통과, `userId=1` 정상 → **적극적 근거 없음**.
- **사용자 체감 "버벅임"의 직접 지표**: **데이터 부족**. 이번 수집에는 HTTP 레이턴시/에러율 시계열이 없다. 관측된 실제 영향은 **알림 지연·유실 위험**이며, `websocket_active_users`는 두 파드 모두 전 구간 0이라 **웹소켓 사용자 영향은 관측되지 않음**. 위 401 메트릭 결측을 감안해 "인증/HTTP 계층 원인 없음"이라는 결론은 내리지 않는다.

---

## 3. 권장 다음 조치

**즉시 확인 (원인 확정)**
1. `infra-server`의 mongod 상태·로그 확인 — 13:58:00~14:04:00 구간. `journalctl -u mongod --since "2026-07-29 13:55" --until "2026-07-29 14:06"`, `/var/log/mongodb/mongod.log`에서 shutdown/OOM/재시작 흔적. Redis는 같은 호스트에서 정상이었으므로 **mongod 프로세스 단위 조사**로 한정.
2. chat-service 파드 `s5fbl` 종료 사유 확인 — `kubectl describe pod`/`kubectl get events --field-selector involvedObject.name=chat-service-857c54dd97-s5fbl`, `kubectl logs --previous`, 메트릭 `kube_pod_container_status_restarts_total`·`kube_pod_status_ready`·`kube_pod_container_status_last_terminated_reason` 조회. (14:06 소멸은 Mongo 복구 이후라 별도 원인 가능성)
3. `user.notifications.dlq` 잔여 및 실제 발송 여부 검증 — offset=12(userId=7, BATTLE_ITEM_COMMENT)가 14:04경 성공 처리되었는지 MongoDB `user_notifications` 컬렉션에서 직접 확인. 14:06~14:15 지연된 25건의 중복 발송 여부도 함께 점검.

**재발 방지 (설정 변경)**
4. Mongo `serverSelectionTimeout`을 30초 → 3~5초로 축소. 현재 1건 실패에 2분+가 소요되어 파티션 처리가 정체됨.
5. readiness/liveness probe가 `/actuator/health`(mongo 포함)를 보는지 확인. 헬스체크가 30초 걸리면 probe timeout을 초과한다 → readiness 그룹에서 mongo 제외하거나 `management.endpoint.health.group.readiness`/타임아웃 분리.
6. Kafka 리스너의 JDBC 트랜잭션 경계 축소 — Mongo 호출이 MySQL 트랜잭션 내부에 있어 커넥션을 30초 점유(`acquired`→`rollback`). 이번엔 풀 고갈이 없었으나 트래픽이 있었다면 연쇄 장애 지점.
7. MongoDB 단일 인스턴스 여부 확인 — 예외의 `servers=[{address=172.31.46.124:27017}]`가 단일 주소. 복제셋 미구성이면 프로세스 다운이 곧 전면 장애.

**관측 보강 (이번에 못 본 것)**
8. 수집 실패한 `http_server_requests_seconds_count{application="content-service", status="401"}` 재조회 + `http_server_requests_seconds_{count,sum}` 전체(레이턴시/에러율)를 04:55~05:20Z로 조회 → 사용자 체감 "버벅임"과 직접 대응되는 지표 확보.
9. `infra-server` 노드 메트릭(CPU/메모리/디스크) 04:55~05:05Z 조회 → mongod 다운이 자원 고갈에 의한 것인지 확인.
10. 알람 신설: `mongodb_up == 0` (1분), `kafka_consumergroup_lag{topic="user.notifications"} > 10` (2분), `absent(up{job="chat-service"})`. 이번 건은 두 번 모두 사용자 제보로 인지됐다.

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

### 로그 원문 (60 / 전체 105줄)

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
| `hikaricp_connections_active` | `{__name__=hikaricp_connections_active, application=auth-service, cluster=yogurtte-k3s-prod, container=auth-service, instance=10.42.1.38:8090, job=auth-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=auth-service-855c75679d-pr892, pool=HikariPool-1, service=auth-service}` | 93 | 0 | 0 | 0 | **2026-07-29T04:57:00Z ~ 2026-07-29T05:20:00Z** |
| `hikaricp_connections_active` | `{__name__=hikaricp_connections_active, application=chat-service, cluster=yogurtte-k3s-prod, container=chat-service, instance=10.42.1.31:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-857c54dd97-s5fbl, pool=HikariPool-1}` | 17 | 0 | 0 | 0 | **2026-07-29T04:57:00Z ~ 2026-07-29T05:06:00Z** |
| `hikaricp_connections_active` | `{__name__=hikaricp_connections_active, application=chat-service, cluster=yogurtte-k3s-prod, container=chat-service, instance=10.42.1.39:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-857c54dd97-w7bf7, pool=HikariPool-1}` | 21 | 0 | 1 | 0 | **2026-07-29T05:16:00Z ~ 2026-07-29T05:20:00Z** |
| `hikaricp_connections_active` | `{__name__=hikaricp_connections_active, application=content-service, cluster=yogurtte-k3s-prod, container=content-service, instance=10.42.1.35:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-6c5fff897-qnxk6, pool=HikariPool-1}` | 93 | 0 | 1 | 0 | **2026-07-29T04:57:00Z ~ 2026-07-29T05:00:00Z, 2026-07-29T05:02:15Z ~ 2026-07-29T05:03:00Z, 2026-07-29T05:04:15Z ~ 2026-07-29T05:10:00Z, 2026-07-29T05:11:15Z ~ 2026-07-29T05:20:00Z** |
| `hikaricp_connections_active` | `{__name__=hikaricp_connections_active, application=content-service, cluster=yogurtte-k3s-prod, container=content-service, instance=10.42.3.41:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-6c5fff897-scw7k, pool=HikariPool-1}` | 93 | 0 | 0 | 0 | **2026-07-29T04:57:00Z ~ 2026-07-29T05:20:00Z** |
| `hikaricp_connections_pending` | `{__name__=hikaricp_connections_pending, application=auth-service, cluster=yogurtte-k3s-prod, container=auth-service, instance=10.42.1.38:8090, job=auth-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=auth-service-855c75679d-pr892, pool=HikariPool-1, service=auth-service}` | 93 | 0 | 0 | 0 | **2026-07-29T04:57:00Z ~ 2026-07-29T05:20:00Z** |
| `hikaricp_connections_pending` | `{__name__=hikaricp_connections_pending, application=chat-service, cluster=yogurtte-k3s-prod, container=chat-service, instance=10.42.1.31:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-857c54dd97-s5fbl, pool=HikariPool-1}` | 17 | 0 | 0 | 0 | **2026-07-29T04:57:00Z ~ 2026-07-29T05:06:00Z** |
| `hikaricp_connections_pending` | `{__name__=hikaricp_connections_pending, application=chat-service, cluster=yogurtte-k3s-prod, container=chat-service, instance=10.42.1.39:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-857c54dd97-w7bf7, pool=HikariPool-1}` | 21 | 0 | 0 | 0 | **2026-07-29T05:15:00Z ~ 2026-07-29T05:20:00Z** |
| `hikaricp_connections_pending` | `{__name__=hikaricp_connections_pending, application=content-service, cluster=yogurtte-k3s-prod, container=content-service, instance=10.42.1.35:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-6c5fff897-qnxk6, pool=HikariPool-1}` | 93 | 0 | 0 | 0 | **2026-07-29T04:57:00Z ~ 2026-07-29T05:20:00Z** |
| `hikaricp_connections_pending` | `{__name__=hikaricp_connections_pending, application=content-service, cluster=yogurtte-k3s-prod, container=content-service, instance=10.42.3.41:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-6c5fff897-scw7k, pool=HikariPool-1}` | 93 | 0 | 0 | 0 | **2026-07-29T04:57:00Z ~ 2026-07-29T05:20:00Z** |
| `rate(jvm_gc_pause_seconds_sum[5m])` | `{action=end of major GC, application=chat-service, cause=Allocation Failure, cluster=yogurtte-k3s-prod, container=chat-service, gc=MarkSweepCompact, instance=10.42.1.31:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-857c54dd97-s5fbl}` | 37 | 0 | 0 | 0 | **2026-07-29T04:57:00Z ~ 2026-07-29T05:09:00Z** |
| `rate(jvm_gc_pause_seconds_sum[5m])` | `{action=end of minor GC, application=auth-service, cause=G1 Evacuation Pause, cluster=yogurtte-k3s-prod, container=auth-service, gc=G1 Young Generation, instance=10.42.1.38:8090, job=auth-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=auth-service-855c75679d-pr892, service=auth-service}` | 93 | 0 | 0.000 | 0.000 | **2026-07-29T04:57:00Z ~ 2026-07-29T05:18:15Z** |
| `rate(jvm_gc_pause_seconds_sum[5m])` | `{action=end of minor GC, application=chat-service, cause=Allocation Failure, cluster=yogurtte-k3s-prod, container=chat-service, gc=Copy, instance=10.42.1.31:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-857c54dd97-s5fbl}` | 37 | 0.000 | 0.000 | 0.000 | — |
| `rate(jvm_gc_pause_seconds_sum[5m])` | `{action=end of minor GC, application=chat-service, cause=Allocation Failure, cluster=yogurtte-k3s-prod, container=chat-service, gc=Copy, instance=10.42.1.39:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-857c54dd97-w7bf7}` | 17 | 0.000 | 0.001 | 0.000 | — |
| `rate(jvm_gc_pause_seconds_sum[5m])` | `{action=end of minor GC, application=content-service, cause=Allocation Failure, cluster=yogurtte-k3s-prod, container=content-service, gc=Copy, instance=10.42.1.35:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-6c5fff897-qnxk6}` | 93 | 0 | 0.000 | 0 | **2026-07-29T04:57:00Z ~ 2026-07-29T04:57:00Z, 2026-07-29T05:01:15Z ~ 2026-07-29T05:07:00Z, 2026-07-29T05:11:15Z ~ 2026-07-29T05:15:00Z, 2026-07-29T05:19:15Z ~ 2026-07-29T05:20:00Z** |
| `rate(jvm_gc_pause_seconds_sum[5m])` | `{action=end of minor GC, application=content-service, cause=Allocation Failure, cluster=yogurtte-k3s-prod, container=content-service, gc=Copy, instance=10.42.3.41:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-6c5fff897-scw7k}` | 93 | 0 | 0.000 | 0.000 | **2026-07-29T05:01:00Z ~ 2026-07-29T05:06:45Z, 2026-07-29T05:11:00Z ~ 2026-07-29T05:17:45Z** |
| `up` | `{__name__=up, app=node-exporter, cluster=yogurtte-k3s-prod, component=metrics, container=node-exporter, instance=ip-172-31-40-241, job=integrations/node_exporter, k8s_cluster_name=yogurtte-k3s-prod, namespace=monitoring, pod=grafana-k8s-monitoring-node-exporter-cph5l, source=kubernetes, workload=DaemonSet/grafana-k8s-monitoring-node-exporter}` | 93 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, app=node-exporter, cluster=yogurtte-k3s-prod, component=metrics, container=node-exporter, instance=ip-172-31-45-39, job=integrations/node_exporter, k8s_cluster_name=yogurtte-k3s-prod, namespace=monitoring, pod=grafana-k8s-monitoring-node-exporter-9kvqt, source=kubernetes, workload=DaemonSet/grafana-k8s-monitoring-node-exporter}` | 93 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, cluster=yogurtte-k3s-prod, container=auth-service, instance=10.42.1.38:8090, job=auth-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=auth-service-855c75679d-pr892}` | 93 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, cluster=yogurtte-k3s-prod, container=chat-service, instance=10.42.1.31:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-857c54dd97-s5fbl}` | 17 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, cluster=yogurtte-k3s-prod, container=chat-service, instance=10.42.1.39:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-857c54dd97-w7bf7}` | 21 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, cluster=yogurtte-k3s-prod, container=content-service, instance=10.42.1.35:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-6c5fff897-qnxk6}` | 93 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, cluster=yogurtte-k3s-prod, container=content-service, instance=10.42.3.41:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-6c5fff897-scw7k}` | 93 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, cluster=yogurtte-k3s-prod, instance=10.42.1.248:8080, job=integrations/kubernetes/kube-state-metrics, k8s_cluster_name=yogurtte-k3s-prod, source=kubernetes}` | 93 | 1 | 1 | 1 | — |
| `mongodb_up` | `{__name__=mongodb_up, cluster=yogurtte-k3s-prod, instance=infra-server, job=mongodb, k8s_cluster_name=yogurtte-k3s-prod}` | 93 | 0 | 1 | 1 | **2026-07-29T04:58:45Z ~ 2026-07-29T05:03:30Z** |
| `kafka_brokers` | `{__name__=kafka_brokers, cluster=yogurtte-k3s-prod, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod}` | 93 | 1 | 1 | 1 | — |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=chat-service-fcm-tokens, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=0, topic=user.fcm-tokens}` | 93 | 0 | 0 | 0 | **2026-07-29T04:57:00Z ~ 2026-07-29T05:20:00Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=chat-service-fcm-tokens, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=1, topic=user.fcm-tokens}` | 93 | 0 | 0 | 0 | **2026-07-29T04:57:00Z ~ 2026-07-29T05:20:00Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=chat-service-fcm-tokens, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=2, topic=user.fcm-tokens}` | 93 | 0 | 0 | 0 | **2026-07-29T04:57:00Z ~ 2026-07-29T05:20:00Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=chat-service-notification-settings, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=0, topic=user.notification-settings}` | 93 | 0 | 0 | 0 | **2026-07-29T04:57:00Z ~ 2026-07-29T05:20:00Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=chat-service-notification-settings, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=1, topic=user.notification-settings}` | 93 | 0 | 0 | 0 | **2026-07-29T04:57:00Z ~ 2026-07-29T05:20:00Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=chat-service-notification-settings, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=2, topic=user.notification-settings}` | 93 | 0 | 0 | 0 | **2026-07-29T04:57:00Z ~ 2026-07-29T05:20:00Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=db-writer, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=0, topic=chat.messages}` | 93 | 0 | 0 | 0 | **2026-07-29T04:57:00Z ~ 2026-07-29T05:20:00Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=db-writer, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=1, topic=chat.messages}` | 93 | 0 | 0 | 0 | **2026-07-29T04:57:00Z ~ 2026-07-29T05:20:00Z** |
| `websocket_active_users` | `{__name__=websocket_active_users, application=chat-service, cluster=yogurtte-k3s-prod, container=chat-service, instance=10.42.1.31:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-857c54dd97-s5fbl}` | 17 | 0 | 0 | 0 | **2026-07-29T04:57:00Z ~ 2026-07-29T05:06:00Z** |
| `websocket_active_users` | `{__name__=websocket_active_users, application=chat-service, cluster=yogurtte-k3s-prod, container=chat-service, instance=10.42.1.39:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-857c54dd97-w7bf7}` | 21 | 0 | 0 | 0 | **2026-07-29T05:15:00Z ~ 2026-07-29T05:20:00Z** |

값이 0이던 구간은 굵게 표시했다 — 프로세스가 사라져 시계열이 꺾인 것이 유일한 신호인 장애가 있다.

