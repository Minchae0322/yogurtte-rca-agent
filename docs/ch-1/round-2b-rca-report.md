# RCA Report — `6a69ea2063240c0918077d8cbd8c859c`

| 항목 | 값 |
|---|---|
| 모드 | rca |
| 질문 | 최근 1시간 안에 댓글 알림이 안 왔다는 제보가 있어요. 확인해줘 |
| 시각 | 2026-07-29T12:05:39.009252Z |
| provider | claude-cli |
| model | `claude-opus-5` · turns 1 |
| prompt | `./prompts/system-prompt.md` |
| tokens | in 122236 (cacheRead 18,133 · cacheCreate 104,101) / out 11836 · cost $1.4262 |
| elapsed | total 173123ms (tempo 523 · loki 258 · mimir 746 · assemble 1 · llm 167797) |

## 탐색 (Triage)

| 항목 | 값 |
|---|---|
| 시간창 해석 | 상대 표현 '최근 1시간' |
| 스윕 창 | 2026-07-29T11:04:31.865893Z ~ 2026-07-29T12:04:31.865893Z |
| 좁힌 창 | 2026-07-29T11:53:00Z ~ 2026-07-29T12:04:31Z |
| 대상 | chat-service, content-service |
| traceId | 6a69ea2063240c0918077d8cbd8c859c |
| 트레이스 후보 | 9건 |
| 계획 파싱 | 성공 |
| prompt | `./prompts/triage-prompt.md` |
| tokens | in 46759 / out 4663 · cost $0.4586 |
| chars | 컨텍스트 45,790 + 프롬프트 1,196 = **46,986** |
| elapsed | survey 1275ms · llm 65852ms |

**선정 이유**: 댓글 알림 경로(content -> chat)에서 chat-service 스팬만 30초 타임아웃으로 실패하기 시작한 11:55:43Z부터, 로그 66건 급증과 스크레이프 결측(11:59:31Z)을 거쳐 복구되는 12:04:31Z까지가 제보된 증상과 시각이 일치하는 유일한 구간이다.

**근거**

- trace 6a69ea2063240c0918077d8cbd8c859c: root=content-service `http post /battles/{battleId}/items/{itemId}/comments`, serviceStats에서 content-service 15스팬 에러 0 / chat-service 8스팬 중 4건 에러 — 실패 지점이 chat-service 쪽
- 동 트레이스 에러 스팬 3건이 11:55:43.948Z(30.022s), 11:56:14.975Z(30.015s), 11:56:14.978Z(30.002s) — 전부 30초 고정 타임아웃 패턴
- trace 6a69ea9d5c9fd31ded195762282d7806: chat-service `connection` 11:57:17.0Z 시작, 30.097s 만에 error 종료
- Loki ERROR/WARN 카운트 chat-service: 11:55 버킷 2건 → 12:00 버킷 66건(33배 급증). 같은 시간대 auth 3건, content 5건으로 대조군은 평상 수준
- Mimir up{job="chat-service", pod="chat-service-857c54dd97-w7bf7"}: 11:59:31Z 샘플 결측(나머지 12개 스크레이프는 모두 1) — 에러가 아니라 스크레이프 자체가 사라짐
- websocket_active_users{pod="chat-service-857c54dd97-w7bf7"}도 11:59:31Z에 동일하게 결측, 전 구간 값은 0 유지
- mongodb_up = 0 @ 11:59:31Z (11:54:31Z=1, 12:04:31Z=1) — chat-service 결측 시각과 동일 스크레이프
- kafka_brokers=1 정상, consumergroup `notification`/`notification-processors`/`chat-service-fcm-tokens` lag 전 구간 0 — 적체형 지연이 아님을 배제하는 근거

**스윕이 찾은 트레이스** (고른 것은 6a69ea2063240c0918077d8cbd8c859c)

| traceId | root service | root span | ms |
|---|---|---|---:|
| `6a69ea9d5c9fd31ded195762282d7806` | chat-service | connection | 30097 |
| `6a69e7fe1224083c021ae372bcec4cc0` | content-service | http get /feeds/scroll | 58 |
| `6a69e7fb0835ef66de170f1278643577` | content-service | http get /feeds/{feedId} | 161 |
| `6a69e7f8ab06633ddcb6c22ae3c931e8` | content-service | http get /feeds/scroll | 88 |
| `6a69e7f6088fd6e26b28ab66596dc999` | content-service | http get /feeds/scroll | 111 |
| `6a69e7f00e63c377af1b51ebea9ca59d` | content-service | http get /feeds/scroll | 62 |
| `6a69e3aacb0cb1b86516674f2cd52e45` | content-service | http get /feeds/following | 3 |
| `6a69e01bc0116147bba4b75907e9d57e` | content-service | http get /feeds/following | 4 |
| `6a69ea2063240c0918077d8cbd8c859c` ←선택 | content-service | http post /battles/{battleId}/items/{itemId}/comments | 2914809269 |

## 수집 범위 (Coverage)

- **window**: 2026-07-29T11:53:00Z ~ 2026-07-29T12:04:31Z (691s)
- **trace**: 30,369B / 28 spans
- **logs**: errwarn=41,142B · traceId=23,495B
- **metrics**: 8 수집 / 89,085B, 누락 [sum(rate(http_server_requests_seconds_count{application="content-service", status="401"}[1m]))]
- **context**: 184,283 chars (+ 시스템 프롬프트 575 chars)

### 토큰 축 (개선 지표)

| 단계 | 총 in | chars | 컨텍스트 토큰 |
|---|---:|---:|---:|
| 탐색 | 46,759 | 46,986 | 25,450 |
| 분석 | 122,236 | 184,858 | 100,927 |
| **합계** | **168,995** | | **126,377** |

- **overheadTokens 21,309 tok — 이 회차에 실측**(1자 프롬프트 프로브, 같은 명령·모델·샌드박스).
  호출마다 한 번씩 얹히므로 단계별로 뺀다. **다른 날 상수를 쓰지 않았다 = `█ 실측`.**
- contextTokens (count_tokens API): 측정 안 됨 — 구독 CLI 경로엔 API 키가 없다
- 비용은 **API 환산 추정치**다. 구독 계정으로 호출하므로 실제 청구액이 아니다.

## 수집 실패/누락

- Metric 'sum(rate(http_server_requests_seconds_count{application="content-service", status="401"}[1m]))' returned no series in this window; skipped.

---

# 장애 분석 — traceId `6a69ea2063240c0918077d8cbd8c859c`

**결론 요약:** 댓글 저장·이벤트 발행(content)까지는 정상이었고, chat-service가 알림을 MongoDB에 저장하는 단계에서 실패했다. MongoDB(`172.31.46.124:27017`)가 약 5분간 접속 거부 상태였다.
(로그 타임스탬프는 KST(+09:00), 아래 시각은 조회창과 맞추기 위해 UTC로 표기)

## 1. 원인 후보 랭킹 (최대 3개)

1. **MongoDB(mongod) 프로세스 다운 → chat-service의 알림 문서 저장 실패** (근본 원인)
2. **재시도 창(약 2분) < 장애 지속(약 5분) 불일치 → 알림이 본 토픽에서 처리되지 못하고 DLQ로 이관, DLQ 재처리마저 실패** (사용자 체감 미도달의 직접 경로)
3. **DLQ 재처리 이후 최종 발송(WS/푸시) 도달 여부 미확인** (데이터 부족)

## 2. 후보별 근거

### 후보 1. MongoDB(mongod) 프로세스 다운

**근거**
- 메트릭 `mongodb_up`: `1785326070`(11:54:30Z)까지 `1` → `1785326085`(11:54:45Z)부터 `1785326370`(11:59:30Z)까지 연속 `0` → `1785326385`(11:59:45Z) 복구. **약 5분 다운.**
- 에러 원문(모든 실패 span·로그에서 동일):
  `Timed out while waiting for a server that matches WritableServerSelector ... {address=172.31.46.124:27017, type=UNKNOWN, state=CONNECTING, exception={com.mongodb.MongoSocketOpenException...}, caused by {java.net.ConnectException: Connection refused}}`
  → `Connection refused`는 **포트에서 리스닝하는 프로세스가 없음**을 뜻한다. 방화벽 드롭·네트워크 지연·리소스 포화(그 경우 timeout/reset)가 아니라 mongod 자체가 죽어 있었다는 신호다.
- span 체인: `publish user.notifications`(content) → `receive`(chat, `messaging.source.name=user.notifications`, `partition=3`, `offset=1004`) → `user-notification-service#process-notification` (`class=...UserNotificationService`, `method=processNotification`) = `STATUS_CODE_ERROR`, 각 시도 **정확히 약 30.0초**(예: `1785326112.524 → 1785326142.805`) 소요 = Mongo 서버 선택 타임아웃 30초.
- 직전 DEBUG: `Inserting Document containing fields: [userId, type, title, ...] in collection: user_notifications` → 실패 지점이 알림 문서 insert임이 명확.
- 헬스체크가 10초 주기로 `Mongo health check failed` + `Health contributor ... (mongo) took 30001ms to respond`를 11:54:50Z부터 11:59:11Z까지 연속 기록.

**호스트 장애가 아니라 mongod 프로세스 장애라는 추가 근거**
- 동일 IP `172.31.46.124`의 Redis(6379)는 같은 시각 정상: content-service span `GET`(`db.system=redis`, `net.sock.peer.addr=172.31.46.124`)이 11:55:12.481Z에 0.5ms로 성공, `up{job="redis"}=1`, `up{job="node-infra"}=1` 전 구간 유지.

**확신도: 높음**

**반증 데이터**
- `up{job="mongodb"}`는 전 구간 `1`이다. 다만 이는 mongodb_exporter 프로세스의 스크랩 성공을 의미하고 mongod 자체 상태는 `mongodb_up`이 나타내므로, 실질적인 반증은 아니다.
- 미세한 시간 불일치: 앱 헬스체크는 11:54:20Z경부터 이미 블로킹된 것으로 보이는데(11:54:50Z 로그 = 30001ms 소요), `mongodb_up`은 11:54:30Z 샘플까지 `1`이었다. 장애 시작 시점은 **11:54:20Z~11:54:45Z 사이**로 폭을 두고 봐야 한다.

### 후보 2. 재시도 창 < 장애 지속 → DLQ 이관 및 DLQ 재처리 실패

**근거**
- 재시도 로그 4회, 각 30초 간격:
  - `처리 실패 1회차: topic=user.notifications partition=3 offset=1004` — 11:55:42Z
  - `2회차` — 11:56:13Z / `3회차` — 11:56:44Z / `4회차` — 11:57:16Z
- `[KAFKA-DLQ] 발행: user.notifications -> user.notifications.dlq (partition=3 offset=1004)` — 11:57:16.011Z, 이어서 `[KAFKA-RETRY] user-notification 재시도 소진 - recoverer 처리 완료: topic=user.notifications offset=1004` — 11:57:17.108Z. span `publish user.notifications.dlq`(SPAN_KIND_PRODUCER)도 11:57:16.030Z에 존재.
- **총 재시도 구간 11:55:12Z~11:57:17Z ≈ 2분 5초. 반면 Mongo 다운은 약 5분.** 즉 Mongo가 복구되기 전에 재시도가 소진됐다.
- DLQ 재처리도 같은 원인으로 실패: `[Kafka] DLQ 알림 재처리 실패 (1분 후 재시도): userId=7, type=BATTLE_ITEM_COMMENT` — 11:57:47Z, `[KAFKA-RETRY] user-notification-dlq 처리 실패 1회차: topic=user.notifications.dlq partition=0 offset=13 cause=com.mongodb.MongoTimeoutException...` — 11:57:47Z.
- 메트릭 정합: `kafka_consumergroup_lag{consumergroup="notification-processors", topic="user.notifications", partition="3"}`이 `1785326130`(11:55:30Z)~`1785326235`(11:57:15Z) 동안 `1`, 이후 `0`. 이어서 `notification-recovery`/`user.notifications.dlq` partition 0의 lag가 `1785326250`(11:57:30Z)~`1785326355`(11:59:15Z) `1`, `1785326370`(11:59:30Z)에 `0`.
- **확정 사실: 11:55:12Z 댓글 작성 시점부터 최소 11:57:17Z까지 userId=7에게 알림이 저장/발송되지 않았다.**

**확신도: 높음** (재시도 소진·DLQ 이관 자체는 로그로 직접 확인됨)

**반증 데이터**
- DLQ lag가 11:59:30Z에 `0`으로 복귀했다 → 메시지가 결국 소비/커밋됐을 가능성이 있으므로 **영구 유실이라고 단정할 수 없다.** 다만 오프셋 커밋 ≠ 알림 발송 성공이며, 성공 로그는 확보되지 않았다(후보 3).
- 그 시점 정황도 일치: 11:59:16Z 헬스 로그가 `took 14561ms`, `took 24637ms`로 30초 타임아웃이 아닌 실제 응답으로 바뀜 → Mongo가 11:59:1x경 재기동.

### 후보 3. DLQ 재처리 이후 최종 발송 도달 여부 미확인 — **데이터 부족**

**근거**
- 확보된 로그에는 DLQ 재처리 **성공** 기록이 없다. 마지막 관련 로그는 11:57:47Z의 실패이며, 그 이후 DLQ 소비자 로그는 `traceId=NONE`(스레드 `ntainer#6-0-C-1`)이라 이 traceId 조회로는 추적이 끊긴다.
- `websocket_active_users{application="chat-service"}`는 조회 가능한 전 구간 `0`이다 → 해당 시각 WebSocket 실시간 푸시 경로로 도달한 사용자는 없다(사용자가 오프라인이었을 수도 있으므로 단독 근거로는 약함).
- 관측 공백: chat-service 시계열(`up`, `hikaricp_*`, `websocket_active_users`)이 `1785326145`(11:55:45Z)~`1785326400`(12:00:00Z) 구간에서 **샘플 자체가 결측**이다. 장애 핵심 구간에 chat-service 메트릭이 없다. 원인 추정 근거는 `Health contributor (mongo) took 30001ms to respond`가 10초 주기로 반복된 점 — 액추에이터 헬스 엔드포인트가 30초 블로킹되어 스크랩이 타임아웃된 것과 시간대가 일치한다.
- 제보는 "최근 1시간"인데 조회창은 11:53:00Z~12:04:31Z(약 11분)뿐이다. 그 이전 구간의 다른 알림 실패 여부는 확인되지 않았다.
- 수집 실패한 메트릭(content-service 401 rate)은 이번 판단에 영향이 작다: 해당 요청은 span `http post /battles/{battleId}/items/{itemId}/comments`가 `status=200`, `outcome=SUCCESS`, 43ms로 성공했고 `userId=1`로 인증돼 있다. 다만 다른 사용자·다른 요청의 인증 실패 가능성은 배제하지 못한다.

**확신도: 낮음** (판단 불가 — 추가 수집 필요)

**반증 데이터**
- 없음. (배제된 후보로: Kafka는 `kafka_brokers=1`, `up{job="kafka"}=1` 전 구간 유지 + 발행 성공 로그 `[Kafka] 알림 발행 성공: userId=7, type=BATTLE_ITEM_COMMENT, partition=3, offset=1004`로 정상 확인됨. content-service·auth-service의 Hikari active/pending 모두 0, GC pause 최대 0.00027초 수준으로 리소스 포화 근거도 없음.)

## 3. 권장 다음 조치

**즉시 (사용자 영향 확정 및 복구)**
1. MongoDB `user_notifications` 컬렉션에서 `userId=7`, `type=BATTLE_ITEM_COMMENT`, `referenceId`=commentId `175`(battleId 22 / itemId 125), `createdAt >= 2026-07-29T11:55:12Z` 문서 존재 여부 조회 → **실제 유실인지 지연 배달인지 판정**.
2. 미존재 시 `user.notifications.dlq` partition=0 offset=13 메시지를 수동 재처리/재발행.
3. `consumergroup=notification-recovery` 컨슈머 로그를 11:57:47Z~12:01:00Z 범위(traceId 없이 시간+로거명 `UserNotificationConsumer`/`KafkaConsumerConfig`로) 조회해 DLQ 재처리 성공 로그 확인.

**원인 규명**
4. `172.31.46.124`(infra-server) 호스트에서 mongod 프로세스 종료 원인 확인 — `journalctl -u mongod`, mongod 로그, OOM killer(`dmesg`), 디스크 용량, 재기동 이력을 11:54:00Z~12:00:00Z 범위로. `Connection refused` + 동일 호스트 Redis 정상이므로 **호스트가 아닌 mongod 단일 프로세스 이슈**로 좁혀서 볼 것.
5. 조회창을 11:00Z~12:05Z로 확대해 동일 오류·다른 파티션/다른 사용자 알림 실패가 더 있는지 확인(현재 창에서는 `user.notifications` partition 3 offset 1004 1건만 lag 발생).

**재발 방지**
6. 재시도 정책 재검토: 서버 선택 타임아웃 30초 × 4회(약 2분)는 인프라 재기동 시간(약 5분)보다 짧다. `serverSelectionTimeout` 축소 + non-blocking retry 토픽/지수 백오프로 총 재시도 창을 5~10분 수준으로 늘리는 방향 검토.
7. DLQ 재처리 루프(현재 "1분 후 재시도")의 최대 시도 횟수·최종 실패 시 알람 여부 확인.
8. 알람 추가/점검: `mongodb_up == 0` 1분 지속, `kafka_consumergroup_lag{topic="user.notifications.dlq"} > 0` 지속.
9. 액추에이터 헬스의 reactive mongo contributor 타임아웃을 축소하거나 `/health/liveness`와 분리 — 30초 블로킹이 chat-service 메트릭 스크랩 결측(11:55:45Z~12:00:00Z)을 만들어 장애 구간 관측 공백을 유발했다.
10. chat-service Kafka 리스너의 트랜잭션 경계 점검: `connection` span(`jdbc.datasource.name=content`, `HikariPool-1`, MySQL 드라이버)이 Mongo 대기 30초 동안 열린 채 `rollback`으로 종료된다. 부하 시 커넥션 고갈 위험이며, chat-service가 `content` 데이터소스를 참조하는 설정 자체도 의도된 것인지 확인이 필요하다.

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

### 로그 원문 (60 / 전체 99줄)

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
2026-07-29T11:58:31.169268334Z  [chat-service]  [2m2026-07-29T20:58:31.168+09:00[0;39m [33m WARN [traceId=NONE,spanId=NONE,userId=NONE][0;39m [35m7[0;39m [2m--- [chat-service] [478f610a9387efd] [                                                 ] [0;39m[36mo.s.b.a.d.m.MongoReactiveHealthIndicator[0;39m [2m:[0;39m Mongo health check failed
2026-07-29T11:58:31.170710913Z  [chat-service]  [2m2026-07-29T20:58:31.169+09:00[0;39m [33m WARN [traceId=6a69eac9ba440caaf2fa14f9581ee39f,spanId=8ac7de16ba267709,userId=NONE][0;39m [35m7[0;39m [2m--- [chat-service] [nio-8090-exec-2] [6a69eac9ba440caaf2fa14f9581ee39f-8ac7de16ba267709] [0;39m[36mo.s.b.a.health.HealthEndpointSupport    [0;39m [2m:[0;39m Health contributor org.springframework.boot.actuate.autoconfigure.health.HealthEndpointConfiguration$AdaptedReactiveHealthContributors$1 (mongo) took 30001ms to respond
2026-07-29T11:58:41.206841708Z  [chat-service]  [2m2026-07-29T20:58:41.206+09:00[0;39m [33m WARN [traceId=NONE,spanId=NONE,userId=NONE][0;39m [35m7[0;39m [2m--- [chat-service] [478f610a9387efd] [                                                 ] [0;39m[36mo.s.b.a.d.m.MongoReactiveHealthIndicator[0;39m [2m:[0;39m Mongo health check failed
2026-07-29T11:58:41.207612169Z  [chat-service]  [2m2026-07-29T20:58:41.206+09:00[0;39m [33m WARN [traceId=6a69ead398e8e292aeecd89eedb17aba,spanId=bd73ce37b86180c2,userId=NONE][0;39m [35m7[0;39m [2m--- [chat-service] [nio-8090-exec-6] [6a69ead398e8e292aeecd89eedb17aba-bd73ce37b86180c2] [0;39m[36mo.s.b.a.health.HealthEndpointSupport    [0;39m [2m:[0;39m Health contributor org.springframework.boot.actuate.autoconfigure.health.HealthEndpointConfiguration$AdaptedReactiveHealthContributors$1 (mongo) took 30001ms to respond
```

### 메트릭 시계열

| 쿼리 | series | 점 | min | max | last | 값이 0이던 구간 |
|---|---|---:|---:|---:|---:|---|
| `hikaricp_connections_active` | `{__name__=hikaricp_connections_active, application=auth-service, cluster=yogurtte-k3s-prod, container=auth-service, instance=10.42.1.41:8090, job=auth-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=auth-service-855c75679d-vpkqw, pool=HikariPool-1, service=auth-service}` | 47 | 0 | 0 | 0 | **2026-07-29T11:53:00Z ~ 2026-07-29T12:04:30Z** |
| `hikaricp_connections_active` | `{__name__=hikaricp_connections_active, application=chat-service, cluster=yogurtte-k3s-prod, container=chat-service, instance=10.42.1.39:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-857c54dd97-w7bf7, pool=HikariPool-1}` | 31 | 0 | 0 | 0 | **2026-07-29T11:53:00Z ~ 2026-07-29T12:04:30Z** |
| `hikaricp_connections_active` | `{__name__=hikaricp_connections_active, application=content-service, cluster=yogurtte-k3s-prod, container=content-service, instance=10.42.1.35:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-6c5fff897-qnxk6, pool=HikariPool-1}` | 47 | 0 | 1 | 0 | **2026-07-29T11:53:00Z ~ 2026-07-29T12:00:00Z, 2026-07-29T12:01:15Z ~ 2026-07-29T12:04:30Z** |
| `hikaricp_connections_active` | `{__name__=hikaricp_connections_active, application=content-service, cluster=yogurtte-k3s-prod, container=content-service, instance=10.42.3.41:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-6c5fff897-scw7k, pool=HikariPool-1}` | 47 | 0 | 0 | 0 | **2026-07-29T11:53:00Z ~ 2026-07-29T12:04:30Z** |
| `hikaricp_connections_pending` | `{__name__=hikaricp_connections_pending, application=auth-service, cluster=yogurtte-k3s-prod, container=auth-service, instance=10.42.1.41:8090, job=auth-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=auth-service-855c75679d-vpkqw, pool=HikariPool-1, service=auth-service}` | 47 | 0 | 0 | 0 | **2026-07-29T11:53:00Z ~ 2026-07-29T12:04:30Z** |
| `hikaricp_connections_pending` | `{__name__=hikaricp_connections_pending, application=chat-service, cluster=yogurtte-k3s-prod, container=chat-service, instance=10.42.1.39:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-857c54dd97-w7bf7, pool=HikariPool-1}` | 31 | 0 | 0 | 0 | **2026-07-29T11:53:00Z ~ 2026-07-29T12:04:30Z** |
| `hikaricp_connections_pending` | `{__name__=hikaricp_connections_pending, application=content-service, cluster=yogurtte-k3s-prod, container=content-service, instance=10.42.1.35:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-6c5fff897-qnxk6, pool=HikariPool-1}` | 47 | 0 | 0 | 0 | **2026-07-29T11:53:00Z ~ 2026-07-29T12:04:30Z** |
| `hikaricp_connections_pending` | `{__name__=hikaricp_connections_pending, application=content-service, cluster=yogurtte-k3s-prod, container=content-service, instance=10.42.3.41:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-6c5fff897-scw7k, pool=HikariPool-1}` | 47 | 0 | 0 | 0 | **2026-07-29T11:53:00Z ~ 2026-07-29T12:04:30Z** |
| `rate(jvm_gc_pause_seconds_sum[5m])` | `{action=end of major GC, application=chat-service, cause=Allocation Failure, cluster=yogurtte-k3s-prod, container=chat-service, gc=MarkSweepCompact, instance=10.42.1.39:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-857c54dd97-w7bf7}` | 39 | 0 | 0 | 0 | **2026-07-29T11:53:00Z ~ 2026-07-29T12:04:30Z** |
| `rate(jvm_gc_pause_seconds_sum[5m])` | `{action=end of minor GC, application=auth-service, cause=G1 Evacuation Pause, cluster=yogurtte-k3s-prod, container=auth-service, gc=G1 Young Generation, instance=10.42.1.41:8090, job=auth-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=auth-service-855c75679d-vpkqw, service=auth-service}` | 47 | 0 | 0.000 | 0 | **2026-07-29T11:53:00Z ~ 2026-07-29T11:57:45Z, 2026-07-29T12:02:00Z ~ 2026-07-29T12:04:30Z** |
| `rate(jvm_gc_pause_seconds_sum[5m])` | `{action=end of minor GC, application=chat-service, cause=Allocation Failure, cluster=yogurtte-k3s-prod, container=chat-service, gc=Copy, instance=10.42.1.39:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-857c54dd97-w7bf7}` | 39 | 0.000 | 0.000 | 0.000 | — |
| `rate(jvm_gc_pause_seconds_sum[5m])` | `{action=end of minor GC, application=content-service, cause=Allocation Failure, cluster=yogurtte-k3s-prod, container=content-service, gc=Copy, instance=10.42.1.35:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-6c5fff897-qnxk6}` | 47 | 0 | 0.000 | 0 | **2026-07-29T11:53:00Z ~ 2026-07-29T11:55:00Z, 2026-07-29T11:59:15Z ~ 2026-07-29T12:04:30Z** |
| `rate(jvm_gc_pause_seconds_sum[5m])` | `{action=end of minor GC, application=content-service, cause=Allocation Failure, cluster=yogurtte-k3s-prod, container=content-service, gc=Copy, instance=10.42.3.41:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-6c5fff897-scw7k}` | 47 | 0 | 0.000 | 0.000 | **2026-07-29T11:57:00Z ~ 2026-07-29T12:02:45Z** |
| `up` | `{__name__=up, app=node-exporter, cluster=yogurtte-k3s-prod, component=metrics, container=node-exporter, instance=ip-172-31-40-241, job=integrations/node_exporter, k8s_cluster_name=yogurtte-k3s-prod, namespace=monitoring, pod=grafana-k8s-monitoring-node-exporter-cph5l, source=kubernetes, workload=DaemonSet/grafana-k8s-monitoring-node-exporter}` | 47 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, app=node-exporter, cluster=yogurtte-k3s-prod, component=metrics, container=node-exporter, instance=ip-172-31-45-39, job=integrations/node_exporter, k8s_cluster_name=yogurtte-k3s-prod, namespace=monitoring, pod=grafana-k8s-monitoring-node-exporter-9kvqt, source=kubernetes, workload=DaemonSet/grafana-k8s-monitoring-node-exporter}` | 47 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, cluster=yogurtte-k3s-prod, container=auth-service, instance=10.42.1.41:8090, job=auth-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=auth-service-855c75679d-vpkqw}` | 47 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, cluster=yogurtte-k3s-prod, container=chat-service, instance=10.42.1.39:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-857c54dd97-w7bf7}` | 31 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, cluster=yogurtte-k3s-prod, container=content-service, instance=10.42.1.35:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-6c5fff897-qnxk6}` | 47 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, cluster=yogurtte-k3s-prod, container=content-service, instance=10.42.3.41:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-6c5fff897-scw7k}` | 47 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, cluster=yogurtte-k3s-prod, instance=10.42.1.248:8080, job=integrations/kubernetes/kube-state-metrics, k8s_cluster_name=yogurtte-k3s-prod, source=kubernetes}` | 47 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, cluster=yogurtte-k3s-prod, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod}` | 47 | 1 | 1 | 1 | — |
| `mongodb_up` | `{__name__=mongodb_up, cluster=yogurtte-k3s-prod, instance=infra-server, job=mongodb, k8s_cluster_name=yogurtte-k3s-prod}` | 47 | 0 | 1 | 1 | **2026-07-29T11:54:45Z ~ 2026-07-29T11:59:30Z** |
| `kafka_brokers` | `{__name__=kafka_brokers, cluster=yogurtte-k3s-prod, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod}` | 47 | 1 | 1 | 1 | — |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=chat-service-fcm-tokens, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=0, topic=user.fcm-tokens}` | 47 | 0 | 0 | 0 | **2026-07-29T11:53:00Z ~ 2026-07-29T12:04:30Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=chat-service-fcm-tokens, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=1, topic=user.fcm-tokens}` | 47 | 0 | 0 | 0 | **2026-07-29T11:53:00Z ~ 2026-07-29T12:04:30Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=chat-service-fcm-tokens, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=2, topic=user.fcm-tokens}` | 47 | 0 | 0 | 0 | **2026-07-29T11:53:00Z ~ 2026-07-29T12:04:30Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=chat-service-notification-settings, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=0, topic=user.notification-settings}` | 47 | 0 | 0 | 0 | **2026-07-29T11:53:00Z ~ 2026-07-29T12:04:30Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=chat-service-notification-settings, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=1, topic=user.notification-settings}` | 47 | 0 | 0 | 0 | **2026-07-29T11:53:00Z ~ 2026-07-29T12:04:30Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=chat-service-notification-settings, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=2, topic=user.notification-settings}` | 47 | 0 | 0 | 0 | **2026-07-29T11:53:00Z ~ 2026-07-29T12:04:30Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=db-writer, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=0, topic=chat.messages}` | 47 | 0 | 0 | 0 | **2026-07-29T11:53:00Z ~ 2026-07-29T12:04:30Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=db-writer, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=1, topic=chat.messages}` | 47 | 0 | 0 | 0 | **2026-07-29T11:53:00Z ~ 2026-07-29T12:04:30Z** |
| `websocket_active_users` | `{__name__=websocket_active_users, application=chat-service, cluster=yogurtte-k3s-prod, container=chat-service, instance=10.42.1.39:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-857c54dd97-w7bf7}` | 31 | 0 | 0 | 0 | **2026-07-29T11:53:00Z ~ 2026-07-29T12:04:30Z** |

값이 0이던 구간은 굵게 표시했다 — 프로세스가 사라져 시계열이 꺾인 것이 유일한 신호인 장애가 있다.

