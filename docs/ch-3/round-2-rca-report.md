# RCA Report — `6a6988a1539ec8bf5f46e52f9b611344`

| 항목 | 값 |
|---|---|
| 모드 | rca |
| 질문 | 최근 1시간 안에 앱이 잠깐 버벅였다는 얘기가 있어요. 뭔가 있었는지 봐줘 |
| 시각 | 2026-07-29T05:50:58.615230500Z |
| provider | claude-cli |
| model | `claude-opus-5` · turns 1 |
| prompt | `.\prompts\system-prompt.md` |
| tokens | in 173434 (cacheRead 23,453 · cacheCreate 149,979) / out 13948 · cost $1.9775 |
| elapsed | total 206951ms (tempo 470 · loki 307 · mimir 663 · assemble 2 · llm 200409) |

## 탐색 (Triage)

| 항목 | 값 |
|---|---|
| 시간창 해석 | 상대 표현 '최근 1시간' |
| 스윕 창 | 2026-07-29T04:50:09.648205100Z ~ 2026-07-29T05:50:09.648205100Z |
| 좁힌 창 | 2026-07-29T04:57:00Z ~ 2026-07-29T05:22:00Z |
| 대상 | chat-service, content-service |
| traceId | 6a6988a1539ec8bf5f46e52f9b611344 |
| 트레이스 후보 | 4건 |
| 계획 파싱 | 성공 |
| prompt | `.\prompts\triage-prompt.md` |
| tokens | in 49820 / out 3344 · cost $0.3771 |
| chars | 컨텍스트 41,901 + 프롬프트 1,231 = **43,132** |
| elapsed | survey 1054ms · llm 47913ms |

**선정 이유**: mongodb_up 하락(05:00:09)·chat-service 에러 급증(05:05 피크 54건)·chat-service 파드 up 시계열 단절(05:05:09~05:15:09)·30초 타임아웃 트레이스(05:00~05:03)·notification 컨슈머 lag 적체(05:10~05:15)가 모두 같은 10분 구간에 겹치고 05:20에 일제히 정상 복귀하므로, 앞뒤 여유를 둔 04:57~05:22 구간의 chat-service(및 그 호출자인 content-service)를 깊게 봐야 한다.

**근거**

- chat-service 파드 chat-service-857c54dd97-s5fbl의 up 시계열이 05:05:09 샘플을 마지막으로 소멸(05:00:09 샘플도 결측), 대체 파드 w7bf7의 up은 05:15:09에야 최초 등장 — 05:05~05:15 약 10분간 chat-service 스크레이프 대상 부재 (파드 재기동/교체 정황)
- Loki ERROR/WARN 발생률: chat-service 05:00:00 버킷 18건 → 05:05:00 버킷 54건(구간 최대), 05:15:00에 2건으로 급감. 동일 구간 auth-service는 0건
- mongodb_up이 05:00:09 샘플에서 1 → 0으로 하락 후 05:05:09에 1로 복구 (조회 1시간 중 유일한 0)
- Tempo: content-service 'http post /battles/{battleId}/items/{itemId}/comments' 트레이스(6a6988a1539ec8bf5f46e52f9b611344)가 05:00:15 시작, 하위 스팬 3개가 각각 30.015s / 30.002s / 30.016s로 타임아웃, serviceStats에서 chat-service 17스팬 중 8개 error, content-service 15스팬은 error 0 — 실패 지점이 chat-service 쪽에 몰림
- Tempo: chat-service rootTraceName='connection' 트레이스 2건이 05:01:17(30086ms), 05:02:47(30008ms)에 각각 30초 타임아웃 에러로 종료
- kafka_consumergroup_lag: consumergroup=notification-processors, topic=user.notifications, partition=3 이 05:00:09에 1 → 05:10:09에 24, 05:15:09에 25로 적체 후 05:20:09에 0으로 해소. 다른 파티션·컨슈머그룹은 전 구간 0 (-1은 미할당 마커)
- kafka_brokers=1, up{job=kafka|redis|auth-service|content-service} 전 구간 1로 유지 — 브로커·auth·content 프로세스 자체는 무중단, 이상은 chat-service에 국한

**스윕이 찾은 트레이스** (고른 것은 6a6988a1539ec8bf5f46e52f9b611344)

| traceId | root service | root span | ms |
|---|---|---|---:|
| `6a698fdc92f000a99d49a082e4722fb3` | content-service | http get /feeds/following | 15 |
| `6a6989774b7cb272fdddcf55fc3af6bd` | chat-service | connection | 30008 |
| `6a69891d13bd030e799c1ef73cc0090a` | chat-service | connection | 30086 |
| `6a6988a1539ec8bf5f46e52f9b611344` ←선택 | content-service | http post /battles/{battleId}/items/{itemId}/comments | 2889849755 |

## 수집 범위 (Coverage)

- **window**: 2026-07-29T04:57:00Z ~ 2026-07-29T05:22:00Z (1500s)
- **trace**: 29,684B / 28 spans
- **logs**: errwarn=45,751B · traceId=23,524B
- **metrics**: 8 수집 / 164,667B, 누락 [sum(rate(http_server_requests_seconds_count{application="content-service", status="401"}[1m]))]
- **context**: 263,787 chars (+ 시스템 프롬프트 597 chars)

### 토큰 축 (개선 지표)

| 단계 | 총 in | chars | 컨텍스트 토큰 |
|---|---:|---:|---:|
| 탐색 | 49,820 | 43,132 | 23,244 |
| 분석 | 173,434 | 264,384 | 146,858 |
| **합계** | **223,254** | | **170,102** |

- **overheadTokens 26,576 tok — 이 회차에 실측**(1자 프롬프트 프로브, 같은 명령·모델·샌드박스).
  호출마다 한 번씩 얹히므로 단계별로 뺀다. **다른 날 상수를 쓰지 않았다 = `█ 실측`.**
- contextTokens (count_tokens API): 측정 안 됨 — 구독 CLI 경로엔 API 키가 없다
- 비용은 **API 환산 추정치**다. 구독 계정으로 호출하므로 실제 청구액이 아니다.

## 수집 실패/누락

- Metric 'sum(rate(http_server_requests_seconds_count{application="content-service", status="401"}[1m]))' returned no series in this window; skipped.

---

조사 결과 요약: 이 traceId에서 **사용자 요청(댓글 작성) 자체는 200/57ms로 정상 성공**했고, 깨진 것은 그 뒤의 **알림 발송 파이프라인**입니다. 원인은 MongoDB 인스턴스 다운입니다.

관측 타임라인 (KST, = UTC+9):

| 시각 | 관측값 |
|---|---|
| 13:58:12~13:58:45 | Mongo 연결 거부 시작. `mongodb_up` 1→0 (13:58:30=1, 13:58:45=0) |
| 13:59:13 | content-service `POST /api/battles/22/items/125/comments` → 200, 57ms / Kafka `user.notifications` p3 offset=963 발행 성공 |
| 13:59:13~14:01:16 | chat-service consumer 4회 실패 (각 ~30.0초) |
| 14:01:16 | `user.notifications.dlq` 발행 (partition=0 offset=12) |
| 14:01:47, 14:03:17 | DLQ 재처리도 실패 |
| 14:03:30~14:03:45 | `mongodb_up` 0→1 복구 (다운 ~4분 45초) |
| ~14:04:30 | `notification-recovery` DLQ lag 1→0 (DLQ 메시지 소진) |
| 14:06:00 | 파드 `chat-service-857c54dd97-s5fbl` 메트릭 시계열 종료 |
| 14:06~14:15 | `user.notifications` p3 lag 2→25 누적 (컨슈머 부재) |
| 14:13:18 | 신규 파드 `chat-service-857c54dd97-w7bf7` 기동 로그 |
| 14:15:30 | p3 lag 25→0 소진 |

## 1. 원인 후보 랭킹 (최대 3개)

1. **MongoDB(172.31.46.124:27017) 프로세스 다운 (13:58:45~14:03:30)** — 알림 처리 실패의 직접 원인
2. **chat-service 파드 소실/교체(14:06~14:15)로 인한 컨슈머 공백** — 후반부 알림 지연(25건)의 원인. 파드가 왜 사라졌는지는 데이터 부족
3. **30초 서버선택 타임아웃 × 4회 재시도 + 단일 리스너 스레드 설정에 의한 장애 증폭** — 근본 원인이 아닌 기여 요인

## 2. 후보별 근거

### 후보 1. MongoDB 인스턴스 다운
- **근거**
  - `mongodb_up`: 13:58:30 `1` → 13:58:45 `0`, 14:03:30까지 `0` 유지, 14:03:45 `1` 복귀. 총 약 4분 45초.
  - 예외 원문(트레이스 `receive`, `user-notification-service#process-notification` span의 `error` 속성, 그리고 Loki 로그 동일 문자열): `com.mongodb.MongoTimeoutException: Timed out while waiting for a server that matches WritableServerSelector ... {com.mongodb.MongoSocketOpenException: Exception opening socket}, caused by {io.netty.channel.AbstractChannel$AnnotatedConnectException: Connection refused: /172.31.46.124:27017}, caused by {java.net.ConnectException: Connection refused}`
  - `Connection refused`(RST)는 호스트 도달 불가가 아니라 **27017 리스너 부재**를 의미. 실제로 같은 호스트의 `up{job="redis"}`, `up{job="kafka"}`, `up{job="node-infra"}`, `up{job="mongodb"}`(exporter)는 전 구간 `1` → 호스트·네트워크는 정상, **mongod 프로세스만 죽음**.
  - 부수 증상: `MongoReactiveHealthIndicator : Mongo health check failed`가 13:58:42부터 10초 간격 반복, `Health contributor ... (mongo) took 30004ms to respond`로 `/actuator/health`가 매번 30초 소요. 복구 직후 14:03:18에 `24893ms`, `14847ms`로 감소하며 정상화.
  - 정확한 시작 시점은 13:58:12(30초 걸린 첫 health check의 시작 추정)~13:58:45(첫 `mongodb_up=0` 스크레이프) 사이.
- **확신도**: 높음
- **반증 데이터**
  - 사용자 요청 경로는 무손상: content-service span `http post /battles/{battleId}/items/{itemId}/comments` = `status 200`, `outcome SUCCESS`, 57ms. `[HTTP] POST /api/battles/22/items/125/comments 200 - 57ms`. → 체감 "버벅임"이 **API 응답 지연**이었다면 이 후보로 설명되지 않음.
  - 시간 범위 불일치: Mongo는 14:03:45에 복구됐는데 `user.notifications` p3 lag는 14:06~14:11에 오히려 0→25로 증가. **후반부 지연은 Mongo 다운으로 설명 불가** (→ 후보 2).
  - `up{job="mongodb"}`(exporter)와 동일 호스트 Redis는 계속 `1` → "인프라 서버 전체 장애" 가설은 반증됨.

### 후보 2. chat-service 파드 소실/교체에 따른 컨슈머 공백
- **근거**
  - 파드 `chat-service-857c54dd97-s5fbl`(10.42.1.31)의 `up`/`hikaricp_*`/`websocket_active_users` 시계열이 **14:06:00 샘플을 끝으로 완전히 소멸**.
  - 신규 파드 `chat-service-857c54dd97-w7bf7`(10.42.1.39) 로그 첫 등장 14:13:18 (`JpaBaseConfiguration$JpaWebConfiguration : spring.jpa.open-in-view is enabled by default`), `up` 첫 샘플 14:15:00.
  - 그 공백과 정확히 겹쳐 `kafka_consumergroup_lag{consumergroup="notification-processors", topic="user.notifications", partition="3"}`가 14:06:00 `2` → 14:11:00 `25`로 증가, 14:15:15까지 `25` 유지, 14:15:30 `0`으로 급감(신규 파드가 일괄 소비).
  - ReplicaSet 해시가 `857c54dd97`로 **동일** → 이미지/설정 롤아웃이 아니라 파드 레벨 재기동(축출·프로브 실패·노드 스케줄링 등).
  - 정황상 유력한 트리거: `/actuator/health`가 mongo 인디케이터 때문에 30초씩 걸림 → liveness probe 타임아웃 시 kubelet이 컨테이너를 죽임. 다만 아래와 같이 직접 증거는 없음.
- **확신도**: 중간 (컨슈머 공백이라는 *현상*은 확신도 높음 / 파드가 사라진 *원인*은 **데이터 부족**)
- **반증 데이터**
  - `kube_pod_container_status_restarts_total`, `last_terminated_reason`, OOM/노드 리소스 지표를 **수집하지 못함**. `up`이 `0`으로 기록된 게 아니라 시계열 자체가 사라진 것이라, 순수 스크레이프 유실 가능성을 완전히 배제할 수 없음(13:59:15~14:03:45 구간에도 동일한 샘플 공백이 있고 그때는 파드가 살아 있었음 — 로그가 계속 나옴).
  - 노드 장애 반증: `up{job="integrations/node_exporter"}`, cadvisor/kubelet 모두 전 구간 `1`.
  - 프로브 가설의 반증: 파드 소멸 추정 시점(14:06 이후)에는 Mongo가 이미 복구(14:03:45)되어 health check가 정상화된 뒤였음 → 30초 health 지연이 직접 트리거였다고 보기엔 3분 이상의 시차가 있음.

### 후보 3. 재시도·타임아웃 설정에 의한 장애 증폭
- **근거**
  - `receive`(SPAN_KIND_CONSUMER) span 4개가 각각 30.108s / 30.015s / 30.015s / 30.016s 소요(startTime/endTime 기준), 동일 메시지 `partition=3 offset=963`을 13:59:13~14:01:16 동안 **123초간 점유**.
  - 로그: `[KAFKA-RETRY] user-notification 처리 실패 1회차 … 4회차`, 이어 `[KAFKA-DLQ] 발행: user.notifications -> user.notifications.dlq (partition=3 offset=963)`, `[KAFKA-RETRY] user-notification 재시도 소진 - recoverer 처리 완료`.
  - `Waiting for server to become available for operation with ID 121236. Remaining time: 29999 ms` → serverSelectionTimeout이 30초로 설정되어 있고, 다운된 서버에 대해 매 시도마다 30초를 소진.
  - 리스너 스레드는 `[ntainer#5-1-C-1]` 단일 → 파티션 3에서 head-of-line blocking. 실제로 p3 lag가 13:59:30~14:01:15에 `1`로 고착.
  - 부가 리스크: 각 실패 시도마다 JDBC `connection` span(`HikariPool-1`, `com.mysql.cj.jdbc.Driver`, datasource `content`)이 `acquired`→30초 후 `rollback`으로 **MySQL 커넥션을 30초간 점유**. Mongo 호출이 RDB 트랜잭션 경계 안에 있음.
- **확신도**: 중간 (설정값과 지속시간은 확정적이나, 이것이 사용자 체감에 기여했는지는 미확인)
- **반증 데이터**
  - 자원 고갈은 발생하지 않음: `hikaricp_connections_pending`이 chat/content/auth 전 파드에서 전 구간 `0`, `hikaricp_connections_active` 최대 `1`. `rate(jvm_gc_pause_seconds_sum[5m])`도 chat-service 약 `1.5e-4`, major GC(`MarkSweepCompact`)는 `0`. → 커넥션/스레드 고갈로 인한 **전역 지연 증거 없음**. 증폭 효과는 알림 파이프라인 내부에 국한됨.

### 데이터 부족 (결론 확신도를 낮추는 공백)
- **사용자 체감 "버벅임"을 직접 확인할 지표가 없음**: `http_server_requests_seconds_*`(latency/error rate)를 수집하지 못했고, 확보된 유일한 사용자 요청은 200/57ms 정상. 즉 **"앱이 버벅였다"는 증상과 이 트레이스의 인과는 관측 데이터로 확정할 수 없음**. 가장 정합적인 해석은 "API 느림"이 아니라 **알림 미수신/지연**(1차: 13:59~14:04 userId=7 1건, 2차: 14:06~14:15 25건).
- 수집 실패: `sum(rate(http_server_requests_seconds_count{application="content-service", status="401"}[1m]))` no series → 인증 관련 가설은 검증 불가(다만 auth-service의 `up`/hikari/GC는 전 구간 정상이고 401 관련 로그도 없음).
- 미수집: 파드 재시작 횟수·종료 사유·OOM, 컨테이너 CPU/메모리, MongoDB 서버 자체 로그, chat-service replica 수, liveness/readiness probe 설정.
- 관측 결함: 신규 파드에서 `PrometheusMeterRegistry : The meter (MeterId{name='spring.kafka.listener' …}) registration has failed: Prometheus requires that all meters with the same name have the same set of tag keys` → **Kafka 리스너 메트릭이 유실 중**이라 컨슈머 처리 지연을 메트릭으로 볼 수 없음.

## 3. 권장 다음 조치

**즉시 확인 (근본 원인 확정)**
1. `172.31.46.124` 호스트에서 13:56~14:05 구간 mongod 종료 사유 확인: `journalctl -u mongod --since "2026-07-29 13:55" --until "2026-07-29 14:10"`, `/var/log/mongodb/mongod.log`, `dmesg -T | grep -i oom`. (재시작이었는지, OOM Kill이었는지, 디스크 풀이었는지)
2. `kubectl get events --field-selector involvedObject.name=chat-service-857c54dd97-s5fbl` 및 `kubectl describe pod chat-service-857c54dd97-w7bf7` → 이전 파드의 `lastState.terminated.reason`(OOMKilled / Error / Completed)과 종료 시각 확인. 14:06~14:13 공백의 원인 규명.
3. chat-service의 liveness/readiness probe 타임아웃·failureThreshold 값과 `/actuator/health` 응답 시간(장애 시 30초) 비교.

**데이터 정합성 확인 (실제 사용자 영향 범위)**
4. `user_notifications` 컬렉션에서 `userId=7, type=BATTLE_ITEM_COMMENT, referenceId=commentId 136` 문서 존재 여부 확인. DLQ lag는 14:04:30에 0이 됐지만 **성공 로그가 없어 최종 전달 여부 미확인**.
5. 14:06~14:15 사이 p3에 적체된 25건이 14:15:30에 전부 정상 처리됐는지(중복 발송 포함) 확인.
6. 사용자 제보 시각을 재확인 — 13:59~14:04(알림 실패)인지 14:06~14:15(알림 지연)인지에 따라 대응 우선순위가 갈림.

**재발 방지 (설정)**
7. mongo serverSelectionTimeout 30초 → 3~5초로 축소하고 재시도 백오프를 지수형으로 조정. 현재는 실패 1건이 리스너 스레드를 123초 점유.
8. liveness health group에서 mongo 인디케이터 제외(readiness에만 반영) → 인프라 다운이 파드 재기동으로 번지는 경로 차단.
9. Mongo 저장을 MySQL 트랜잭션 경계 밖으로 분리 (현재 `connection` span이 `acquired`→30초→`rollback`으로 커넥션 점유).
10. chat-service replica 수 확인 후 컨슈머 이중화 — 현재 파드 1개 소실로 알림 소비가 9분간 완전 중단됨.

**관측 보강**
11. `spring.kafka.listener` 미터 태그 충돌 수정(`KafkaConsumerConfig`/ObservationRegistry 설정) — 현재 리스너 메트릭 유실 중.
12. 알람 신설: `mongodb_up == 0` (1분), `kafka_consumergroup_lag{topic="user.notifications"} > 0` (5분 지속), `absent(up{job="chat-service"})`.
13. `http_server_requests_seconds_*`를 content/chat/auth 전부에서 수집하도록 스크레이프 설정 점검 — 이번 분석에서 사용자 체감 지연을 검증할 수 없었던 핵심 공백.

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
| `hikaricp_connections_active` | `{__name__=hikaricp_connections_active, application=auth-service, cluster=yogurtte-k3s-prod, container=auth-service, instance=10.42.1.38:8090, job=auth-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=auth-service-855c75679d-pr892, pool=HikariPool-1, service=auth-service}` | 101 | 0 | 0 | 0 | **2026-07-29T04:57:00Z ~ 2026-07-29T05:22:00Z** |
| `hikaricp_connections_active` | `{__name__=hikaricp_connections_active, application=chat-service, cluster=yogurtte-k3s-prod, container=chat-service, instance=10.42.1.31:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-857c54dd97-s5fbl, pool=HikariPool-1}` | 17 | 0 | 0 | 0 | **2026-07-29T04:57:00Z ~ 2026-07-29T05:06:00Z** |
| `hikaricp_connections_active` | `{__name__=hikaricp_connections_active, application=chat-service, cluster=yogurtte-k3s-prod, container=chat-service, instance=10.42.1.39:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-857c54dd97-w7bf7, pool=HikariPool-1}` | 29 | 0 | 1 | 0 | **2026-07-29T05:16:00Z ~ 2026-07-29T05:22:00Z** |
| `hikaricp_connections_active` | `{__name__=hikaricp_connections_active, application=content-service, cluster=yogurtte-k3s-prod, container=content-service, instance=10.42.1.35:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-6c5fff897-qnxk6, pool=HikariPool-1}` | 101 | 0 | 1 | 0 | **2026-07-29T04:57:00Z ~ 2026-07-29T05:00:00Z, 2026-07-29T05:02:15Z ~ 2026-07-29T05:03:00Z, 2026-07-29T05:04:15Z ~ 2026-07-29T05:10:00Z, 2026-07-29T05:11:15Z ~ 2026-07-29T05:22:00Z** |
| `hikaricp_connections_active` | `{__name__=hikaricp_connections_active, application=content-service, cluster=yogurtte-k3s-prod, container=content-service, instance=10.42.3.41:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-6c5fff897-scw7k, pool=HikariPool-1}` | 101 | 0 | 0 | 0 | **2026-07-29T04:57:00Z ~ 2026-07-29T05:22:00Z** |
| `hikaricp_connections_pending` | `{__name__=hikaricp_connections_pending, application=auth-service, cluster=yogurtte-k3s-prod, container=auth-service, instance=10.42.1.38:8090, job=auth-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=auth-service-855c75679d-pr892, pool=HikariPool-1, service=auth-service}` | 101 | 0 | 0 | 0 | **2026-07-29T04:57:00Z ~ 2026-07-29T05:22:00Z** |
| `hikaricp_connections_pending` | `{__name__=hikaricp_connections_pending, application=chat-service, cluster=yogurtte-k3s-prod, container=chat-service, instance=10.42.1.31:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-857c54dd97-s5fbl, pool=HikariPool-1}` | 17 | 0 | 0 | 0 | **2026-07-29T04:57:00Z ~ 2026-07-29T05:06:00Z** |
| `hikaricp_connections_pending` | `{__name__=hikaricp_connections_pending, application=chat-service, cluster=yogurtte-k3s-prod, container=chat-service, instance=10.42.1.39:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-857c54dd97-w7bf7, pool=HikariPool-1}` | 29 | 0 | 0 | 0 | **2026-07-29T05:15:00Z ~ 2026-07-29T05:22:00Z** |
| `hikaricp_connections_pending` | `{__name__=hikaricp_connections_pending, application=content-service, cluster=yogurtte-k3s-prod, container=content-service, instance=10.42.1.35:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-6c5fff897-qnxk6, pool=HikariPool-1}` | 101 | 0 | 0 | 0 | **2026-07-29T04:57:00Z ~ 2026-07-29T05:22:00Z** |
| `hikaricp_connections_pending` | `{__name__=hikaricp_connections_pending, application=content-service, cluster=yogurtte-k3s-prod, container=content-service, instance=10.42.3.41:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-6c5fff897-scw7k, pool=HikariPool-1}` | 101 | 0 | 0 | 0 | **2026-07-29T04:57:00Z ~ 2026-07-29T05:22:00Z** |
| `rate(jvm_gc_pause_seconds_sum[5m])` | `{action=end of major GC, application=chat-service, cause=Allocation Failure, cluster=yogurtte-k3s-prod, container=chat-service, gc=MarkSweepCompact, instance=10.42.1.31:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-857c54dd97-s5fbl}` | 37 | 0 | 0 | 0 | **2026-07-29T04:57:00Z ~ 2026-07-29T05:09:00Z** |
| `rate(jvm_gc_pause_seconds_sum[5m])` | `{action=end of minor GC, application=auth-service, cause=G1 Evacuation Pause, cluster=yogurtte-k3s-prod, container=auth-service, gc=G1 Young Generation, instance=10.42.1.38:8090, job=auth-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=auth-service-855c75679d-pr892, service=auth-service}` | 101 | 0 | 0.000 | 0.000 | **2026-07-29T04:57:00Z ~ 2026-07-29T05:18:15Z** |
| `rate(jvm_gc_pause_seconds_sum[5m])` | `{action=end of minor GC, application=chat-service, cause=Allocation Failure, cluster=yogurtte-k3s-prod, container=chat-service, gc=Copy, instance=10.42.1.31:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-857c54dd97-s5fbl}` | 37 | 0.000 | 0.000 | 0.000 | — |
| `rate(jvm_gc_pause_seconds_sum[5m])` | `{action=end of minor GC, application=chat-service, cause=Allocation Failure, cluster=yogurtte-k3s-prod, container=chat-service, gc=Copy, instance=10.42.1.39:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-857c54dd97-w7bf7}` | 25 | 0.000 | 0.001 | 0.000 | — |
| `rate(jvm_gc_pause_seconds_sum[5m])` | `{action=end of minor GC, application=content-service, cause=Allocation Failure, cluster=yogurtte-k3s-prod, container=content-service, gc=Copy, instance=10.42.1.35:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-6c5fff897-qnxk6}` | 101 | 0 | 0.000 | 0 | **2026-07-29T04:57:00Z ~ 2026-07-29T04:57:00Z, 2026-07-29T05:01:15Z ~ 2026-07-29T05:07:00Z, 2026-07-29T05:11:15Z ~ 2026-07-29T05:15:00Z, 2026-07-29T05:19:15Z ~ 2026-07-29T05:22:00Z** |
| `rate(jvm_gc_pause_seconds_sum[5m])` | `{action=end of minor GC, application=content-service, cause=Allocation Failure, cluster=yogurtte-k3s-prod, container=content-service, gc=Copy, instance=10.42.3.41:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-6c5fff897-scw7k}` | 101 | 0 | 0.000 | 0 | **2026-07-29T05:01:00Z ~ 2026-07-29T05:06:45Z, 2026-07-29T05:11:00Z ~ 2026-07-29T05:17:45Z, 2026-07-29T05:22:00Z ~ 2026-07-29T05:22:00Z** |
| `up` | `{__name__=up, app=node-exporter, cluster=yogurtte-k3s-prod, component=metrics, container=node-exporter, instance=ip-172-31-40-241, job=integrations/node_exporter, k8s_cluster_name=yogurtte-k3s-prod, namespace=monitoring, pod=grafana-k8s-monitoring-node-exporter-cph5l, source=kubernetes, workload=DaemonSet/grafana-k8s-monitoring-node-exporter}` | 101 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, app=node-exporter, cluster=yogurtte-k3s-prod, component=metrics, container=node-exporter, instance=ip-172-31-45-39, job=integrations/node_exporter, k8s_cluster_name=yogurtte-k3s-prod, namespace=monitoring, pod=grafana-k8s-monitoring-node-exporter-9kvqt, source=kubernetes, workload=DaemonSet/grafana-k8s-monitoring-node-exporter}` | 101 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, cluster=yogurtte-k3s-prod, container=auth-service, instance=10.42.1.38:8090, job=auth-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=auth-service-855c75679d-pr892}` | 101 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, cluster=yogurtte-k3s-prod, container=chat-service, instance=10.42.1.31:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-857c54dd97-s5fbl}` | 17 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, cluster=yogurtte-k3s-prod, container=chat-service, instance=10.42.1.39:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-857c54dd97-w7bf7}` | 29 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, cluster=yogurtte-k3s-prod, container=content-service, instance=10.42.1.35:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-6c5fff897-qnxk6}` | 101 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, cluster=yogurtte-k3s-prod, container=content-service, instance=10.42.3.41:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-6c5fff897-scw7k}` | 101 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, cluster=yogurtte-k3s-prod, instance=10.42.1.248:8080, job=integrations/kubernetes/kube-state-metrics, k8s_cluster_name=yogurtte-k3s-prod, source=kubernetes}` | 101 | 1 | 1 | 1 | — |
| `mongodb_up` | `{__name__=mongodb_up, cluster=yogurtte-k3s-prod, instance=infra-server, job=mongodb, k8s_cluster_name=yogurtte-k3s-prod}` | 101 | 0 | 1 | 1 | **2026-07-29T04:58:45Z ~ 2026-07-29T05:03:30Z** |
| `kafka_brokers` | `{__name__=kafka_brokers, cluster=yogurtte-k3s-prod, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod}` | 101 | 1 | 1 | 1 | — |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=chat-service-fcm-tokens, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=0, topic=user.fcm-tokens}` | 101 | 0 | 0 | 0 | **2026-07-29T04:57:00Z ~ 2026-07-29T05:22:00Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=chat-service-fcm-tokens, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=1, topic=user.fcm-tokens}` | 101 | 0 | 0 | 0 | **2026-07-29T04:57:00Z ~ 2026-07-29T05:22:00Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=chat-service-fcm-tokens, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=2, topic=user.fcm-tokens}` | 101 | 0 | 0 | 0 | **2026-07-29T04:57:00Z ~ 2026-07-29T05:22:00Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=chat-service-notification-settings, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=0, topic=user.notification-settings}` | 101 | 0 | 0 | 0 | **2026-07-29T04:57:00Z ~ 2026-07-29T05:22:00Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=chat-service-notification-settings, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=1, topic=user.notification-settings}` | 101 | 0 | 0 | 0 | **2026-07-29T04:57:00Z ~ 2026-07-29T05:22:00Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=chat-service-notification-settings, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=2, topic=user.notification-settings}` | 101 | 0 | 0 | 0 | **2026-07-29T04:57:00Z ~ 2026-07-29T05:22:00Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=db-writer, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=0, topic=chat.messages}` | 101 | 0 | 0 | 0 | **2026-07-29T04:57:00Z ~ 2026-07-29T05:22:00Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=db-writer, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=1, topic=chat.messages}` | 101 | 0 | 0 | 0 | **2026-07-29T04:57:00Z ~ 2026-07-29T05:22:00Z** |
| `websocket_active_users` | `{__name__=websocket_active_users, application=chat-service, cluster=yogurtte-k3s-prod, container=chat-service, instance=10.42.1.31:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-857c54dd97-s5fbl}` | 17 | 0 | 0 | 0 | **2026-07-29T04:57:00Z ~ 2026-07-29T05:06:00Z** |
| `websocket_active_users` | `{__name__=websocket_active_users, application=chat-service, cluster=yogurtte-k3s-prod, container=chat-service, instance=10.42.1.39:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-857c54dd97-w7bf7}` | 29 | 0 | 0 | 0 | **2026-07-29T05:15:00Z ~ 2026-07-29T05:22:00Z** |

값이 0이던 구간은 굵게 표시했다 — 프로세스가 사라져 시계열이 꺾인 것이 유일한 신호인 장애가 있다.

