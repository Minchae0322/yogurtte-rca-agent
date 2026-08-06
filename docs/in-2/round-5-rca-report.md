# RCA Report — `scan-1785990300`

| 항목 | 값 |
|---|---|
| 모드 | rca |
| 질문 | 최근 1시간 안에 댓글 알림이 안 왔다는 제보가 있다. 원인을 조사해줘 |
| 시각 | 2026-08-06T05:05:57.860939100Z |
| provider | claude-cli |
| model | `claude-opus-5` · turns 1 |
| prompt | `.\prompts\system-prompt.md` |
| tokens | in 96532 (cacheRead 23,449 · cacheCreate 73,081) / out 7461 · cost $0.9291 |
| elapsed | total 136832ms (tempo 3869 · loki 518 · mimir 1091 · assemble 400 · llm 119222) |

## 탐색 (Triage)

| 항목 | 값 |
|---|---|
| 시간창 해석 | 상대 표현 '최근 1시간' |
| 시간창 확신도 | EXACT |
| 스윕 창 | 2026-08-06T04:04:55.249874400Z ~ 2026-08-06T05:04:55.249874400Z |
| 좁힌 창 | 2026-08-06T04:25:00Z ~ 2026-08-06T05:04:55Z |
| 대상 | content-service, chat-service |
| traceId | 6a740e7c2620ba4220a89df5f5dcffcd |
| 트레이스 후보 | 9건 |
| 장애 후보 | 8건 · 선택 INC-2, INC-4, INC-5, INC-6, INC-7, INC-8 |
| 계획 파싱 | 성공 |
| 스윕 컨텍스트 | **후보만 — 원본 제외 (B)** |
| prompt | `.\prompts\triage-prompt.md` |
| tokens | in 33264 / out 3070 · cost $0.2138 |
| chars | 컨텍스트 3,761 + 프롬프트 1,399 = **5,160** |
| elapsed | survey 3276ms · llm 59009ms |

**선정 이유**: 댓글 알림처럼 비동기 이벤트로 전달되는 기능이 끊긴 증상은 04:45~04:55 Kafka 브로커 단절과 그 창의 content-service 에러 폭증으로 가장 잘 설명되며, 04:33의 알림 스케줄러 2초 타임아웃도 같은 발행 경로 문제일 수 있어 함께 조사한다.

**근거**

- kafka up 1→0, 04:49:55~04:54:55 구간 up=0 (min_over_time(up[5m]) 이상 신호 3건) — 알림 발행/소비 경로의 브로커가 실제로 사라짐
- kafka_brokers 1→0, 04:54:55 시점 0 — INC-5와 동일 장애의 다른 지문이라 함께 선택
- content-service ERROR/WARN 370건 (04:45~04:50) + 51건 (04:50~04:55) — Kafka 단절 창과 정확히 겹치는 최대 규모 에러 폭증
- INC-7: 04:48:35~04:49:35, root span 미수신 상태의 60,021ms error 트레이스 — 요청이 완결되지 못하고 끊긴 흔적(트레이스가 '없다'는 것 자체가 신호)
- INC-4: battle-deadline-notification-scheduler.notify가 2,003/2,000/2,002/2,010/2,000/2,000ms로 6회 연속 error — 편차 없는 2초 정지는 알림 발행 다운스트림의 타임아웃 지문
- INC-2: 위 스케줄러 실패와 같은 시각(04:30~04:35) content-service ERROR/WARN 79건 — INC-4의 로그 측면이라 함께 선택
- kafka_consumergroup_lag 이상 0건은 정상 근거로 쓰지 않음 — 브로커가 죽은 구간에는 lag 메트릭 자체가 수집되지 않아 0으로 보일 수 있음
- websocket_active_users 이상 0건 — 사용자 접속 자체는 유지되었으므로 '접속 끊김'이 아니라 '알림이 안 온 것'이라는 제보와 부합

**스윕이 찾은 트레이스** (고른 것은 6a740e7c2620ba4220a89df5f5dcffcd)

| traceId | 채널 | root service | root span | ms |
|---|---|---|---|---:|
| `6a741223ef2158f305caa6eac0719bfd` | error | <root span not yet received> | (없음) | 60021 |
| `6a740ef437378b2ba59b137b2a6f7349` | error | content-service | task battle-deadline-notification-scheduler.notify | 2000 |
| `6a740ef40b0c7d258e82b43ce573b9c0` | error | content-service | task battle-deadline-notification-scheduler.notify | 2000 |
| `6a740eb8723600d08b6755dfc53d7d12` | error | content-service | task battle-deadline-notification-scheduler.notify | 2010 |
| `6a740eb80716747faf6aa754773de070` | error | content-service | task battle-deadline-notification-scheduler.notify | 2002 |
| `6a740e7ce4ae0784411add206af99250` | error | content-service | task battle-deadline-notification-scheduler.notify | 2000 |
| `6a740e7c2620ba4220a89df5f5dcffcd` ←선택 | error | content-service | task battle-deadline-notification-scheduler.notify | 2003 |
| `6a740e8a1120fa25d0b130c650332129` | slow | content-service | http get /feeds/scroll | 16147 |
| `6a740e7417e8b0b83aa26f5450e14d12` | slow | content-service | http get /feeds/scroll | 16409 |

**장애 후보** (코드가 신호를 묶은 것 · 창은 여기서 계산됨)

## INC-1  chat-service  |  ERROR/WARN
- 구간: 2026-08-06T04:30:00Z ~ 2026-08-06T04:40:00Z  (LOKI · 집계 해상도만큼 흐림)
- ERROR/WARN 28건 (2026-08-06T04:30:00Z ~ 2026-08-06T04:35:00Z)
- ERROR/WARN 7건 (2026-08-06T04:35:00Z ~ 2026-08-06T04:40:00Z)
- 같은 시각의 다른 후보: INC-2, INC-3, INC-4  (인과 여부는 판단하지 않았다)

## INC-2  content-service  |  ERROR/WARN
- 구간: 2026-08-06T04:30:00Z ~ 2026-08-06T04:40:00Z  (LOKI · 집계 해상도만큼 흐림)
- ERROR/WARN 79건 (2026-08-06T04:30:00Z ~ 2026-08-06T04:35:00Z)
- ERROR/WARN 17건 (2026-08-06T04:35:00Z ~ 2026-08-06T04:40:00Z)
- 같은 시각의 다른 후보: INC-1, INC-3, INC-4  (인과 여부는 판단하지 않았다)

## INC-3  content-service  |  http get /feeds/scroll
- 구간: 2026-08-06T04:32:52.051686Z ~ 2026-08-06T04:33:30.176553Z  (TEMPO · 시각 정확)
- content-service http get /feeds/scroll 16,409ms (slow 채널)
- content-service http get /feeds/scroll 16,147ms (slow 채널)
- traceId: 6a740e7417e8b0b83aa26f5450e14d12, 6a740e8a1120fa25d0b130c650332129
- 같은 시각의 다른 후보: INC-1, INC-2, INC-4  (인과 여부는 판단하지 않았다)

## INC-4  content-service  |  task battle-deadline-notification-scheduler.notify
- 구간: 2026-08-06T04:33:00.000283Z ~ 2026-08-06T04:35:02.015374Z  (TEMPO · 시각 정확)
- content-service task battle-deadline-notification-scheduler.notify 2,003ms (error 채널)
- content-service task battle-deadline-notification-scheduler.notify 2,000ms (error 채널)
- content-service task battle-deadline-notification-scheduler.notify 2,002ms (error 채널)
- content-service task battle-deadline-notification-scheduler.notify 2,010ms (error 채널)
- content-service task battle-deadline-notification-scheduler.notify 2,000ms (error 채널)
- content-service task battle-deadline-notification-scheduler.notify 2,000ms (error 채널)
- traceId: 6a740e7c2620ba4220a89df5f5dcffcd, 6a740e7ce4ae0784411add206af99250, 6a740eb80716747faf6aa754773de070, 6a740eb8723600d08b6755dfc53d7d12, 6a740ef40b0c7d258e82b43ce573b9c0, 6a740ef437378b2ba59b137b2a6f7349
- 같은 시각의 다른 후보: INC-1, INC-2, INC-3  (인과 여부는 판단하지 않았다)

## INC-5  kafka  |  up
- 구간: 2026-08-06T04:44:55Z ~ 2026-08-06T04:59:55Z  (MIMIR · 집계 해상도만큼 흐림)
- up 1 → 0
- up 가 0이었다 (2026-08-06T04:49:55Z ~ 2026-08-06T04:54:55Z)
- up 0 → 1
- 같은 시각의 다른 후보: INC-6, INC-7, INC-8  (인과 여부는 판단하지 않았다)

## INC-6  content-service  |  ERROR/WARN
- 구간: 2026-08-06T04:45:00Z ~ 2026-08-06T04:55:00Z  (LOKI · 집계 해상도만큼 흐림)
- ERROR/WARN 370건 (2026-08-06T04:45:00Z ~ 2026-08-06T04:50:00Z)
- ERROR/WARN 51건 (2026-08-06T04:50:00Z ~ 2026-08-06T04:55:00Z)
- 같은 시각의 다른 후보: INC-5, INC-7, INC-8  (인과 여부는 판단하지 않았다)

## INC-7  <root span not yet received>
- 구간: 2026-08-06T04:48:35.955349Z ~ 2026-08-06T04:49:35.976349Z  (TEMPO · 시각 정확)
- <root span not yet received>  60,021ms (error 채널)
- traceId: 6a741223ef2158f305caa6eac0719bfd
- 같은 시각의 다른 후보: INC-5, INC-6  (인과 여부는 판단하지 않았다)

## INC-8  kafka  |  kafka_brokers
- 구간: 2026-08-06T04:49:55Z ~ 2026-08-06T04:59:55Z  (MIMIR · 집계 해상도만큼 흐림)
- kafka_brokers 1 → 0
- kafka_brokers 가 0이었다 (2026-08-06T04:54:55Z ~ 2026-08-06T04:54:55Z)
- kafka_brokers 0 → 1
- 같은 시각의 다른 후보: INC-5, INC-6  (인과 여부는 판단하지 않았다)

**기각한 후보**

- INC-1 — chat-service ERROR/WARN 28건은 Kafka 단절 창이 아닌 04:30~04:40대이고 규모도 작아, 댓글 알림 미도달의 주 신호로 보기엔 시각과 크기가 모두 맞지 않는다.
- INC-3 — /feeds/scroll 16초 지연은 피드 조회 읽기 경로의 지연으로, 알림 발행·전달 경로와 엔드포인트가 다르고 제보 증상(알림 미도달)과 직접 연결되지 않는다.

**보정 기록**

- 창을 후보 [INC-2, INC-4, INC-5, INC-6, INC-7, INC-8] 의 신호 시각에서 계산했다 (2026-08-06T04:25:00Z ~ 2026-08-06T05:04:55Z)

## 수집 범위 (Coverage)

- **window**: 2026-08-06T04:25:00Z ~ 2026-08-06T05:04:55Z (2395s)
- **trace**: 77,179B / 78 spans
- **창 안 후보 트레이스**: 10건 / 77,179B
- **logs**: errwarn=149,739B · traceId=11,938B
- **metrics**: 8 수집 / 244,339B, 누락 [sum(rate(http_server_requests_seconds_count{application="content-service", status="401"}[1m]))]
- **context**: 119,031 chars (+ 시스템 프롬프트 805 chars)

### 토큰 축 (개선 지표)

| 단계 | 총 in | chars | 컨텍스트 토큰 |
|---|---:|---:|---:|
| 탐색 | 33,264 | 5,160 | 3,351 |
| 분석 | 96,532 | 119,836 | 66,619 |
| **합계** | **129,796** | | **69,970** |

- **overheadTokens 29,913 tok — 이 회차에 실측**(1자 프롬프트 프로브, 같은 명령·모델·샌드박스).
  호출마다 한 번씩 얹히므로 단계별로 뺀다. **다른 날 상수를 쓰지 않았다 = `█ 실측`.**
- contextTokens (count_tokens API): 측정 안 됨 — 구독 CLI 경로엔 API 키가 없다
- 비용은 **API 환산 추정치**다. 구독 계정으로 호출하므로 실제 청구액이 아니다.

## 수집 실패/누락

- Metric 'sum(rate(http_server_requests_seconds_count{application="content-service", status="401"}[1m]))' returned no series in this window; skipped.

---

## 1. 원인 후보 랭킹 (최대 3개)

1. **Kafka 브로커 다운(04:47:15~04:50:15 UTC) → content-service의 `user.notifications` 발행이 60초 블로킹 후 만료·폐기**
2. **Redis 다운(04:32:5x~04:33:0x UTC) → `battle-deadline-notification-scheduler.notify` 스케줄 알림 태스크 연속 실패** (댓글 알림과는 다른 알림 경로지만 같은 창의 알림 누락)
3. **컨슈머(`notification-processors`) 적체·처리 실패** — 데이터상 반증됨, 사실상 배제

---

## 2. 후보별 근거

### 후보 1 — Kafka 브로커 다운으로 댓글 알림 발행 실패

- **근거**
  - traceId `6a741223ef2158f305caa6eac0719bfd` (04:48:35 UTC): `http post /battles/{battleId}/items/{itemId}/comments` 는 `status=200`, `outcome=SUCCESS`, 59.9ms로 정상 종료. 로그도 `[battle] 댓글 작성 완료 - commentId: 327, battleId: 22, itemId: 125` — **댓글 자체는 DB 커밋됨**(`insert into tb_battle_item_comment`, `jdbc.row-affected=1`, `generated-keys=327`, connection span에 `commit` 이벤트).
  - 같은 트레이스의 비동기 알림 span: `notification-publish` (`NotificationEventListener.onNotification`) `durNs=60021881000` = **60.02초**, `error="Send failed"`, `STATUS_CODE_ERROR`.
  - 자식 span `publish user.notifications` (`SPAN_KIND_PRODUCER`, `messaging.system=kafka`) `durNs=60016108000` = **60.016초**, `error="Topic user.notifications not present in metadata after 60000 ms."`
  - 로그 원문: `o.s.k.s.LoggingProducerListener - Exception thrown when sending a message with key='7' and payload='KafkaNotificationDto(userId=7, type=BATTLE_ITEM_COMMENT, title=새 댓글, ...' to topic user.notifications:` 및 `c.e.t.a.n.NotificationEventListener - [notify] 알림 발행 실패: userId=7, type=BATTLE_ITEM_COMMENT, error=Send failed` (13:49:35.97 KST = 04:49:35 UTC).
  - 인프라 메트릭이 시각적으로 일치: `up{job=kafka, instance=infra-server}` **0인 구간 04:47:15Z~04:50:15Z**, `kafka_brokers` 결측 04:47:00Z~04:50:30Z 및 04:50:30Z에 값 0. 발행 시도 구간(04:48:35.955 → 04:49:35.971)이 **브로커 다운 구간에 완전히 포함**된다.
- **확신도: 높음**
- **대기·지연 구간 판정**
  - 실측 대기 60,016ms vs 상한(에러 메시지에 명시된 메타데이터 대기 한도 = Kafka 프로듀서 `max.block.ms` 60,000ms) → **만료됨**.
  - 최종 상태: **실패 후 폐기**. 재시도 증거 없음(동일 key='7' 재발행 로그·span 없음), DLQ로도 안 감 — `kafka_consumergroup_lag{consumergroup=notification-recovery, topic=user.notifications.dlq}`가 전 구간 0/-1로 유입 흔적 없음. 애플리케이션은 예외를 로깅만 하고 종료(`알림 발행 실패` 이후 후속 없음).
  - 사용자 영향 판정: 댓글은 저장(200 응답), **알림만 소실** → 제보 증상과 정확히 일치.
- **반증 데이터**
  - `kafka_consumergroup_lag{consumergroup=notification-processors, topic=user.notifications}` 는 전 파티션 전 구간 0 — "브로커 장애면 lag이 튀어야 하지 않나"로 읽힐 수 있으나, 발행 자체가 실패해 메시지가 들어가지 않았으므로 lag 0은 오히려 이 후보와 정합적이다.
  - 약한 반증: `up{job=kafka}`·`kafka_brokers`는 둘 다 `instance=infra-server`의 동일 exporter에서 나온다. **브로커 다운인지 exporter 다운인지 이 두 시리즈만으로는 분리되지 않는다.** 다만 클라이언트 측 `Topic ... not present in metadata after 60000 ms`는 브로커/메타데이터에 실제로 도달하지 못했음을 독립적으로 뒷받침한다.
  - 관측된 소실 알림은 **1건(userId=7)뿐**이다. 다운 구간 3분 동안의 총 소실 건수는 트레이스 10건 샘플만으로는 **판정 불가**.

### 후보 2 — Redis 다운으로 배틀 마감 알림 스케줄러 실패

- **근거**
  - `i.l.core.protocol.ConnectionWatchdog - Cannot reconnect to [172.31.46.124/<unresolved>:6379]: finishConnect(..) failed: Connection refused: /172.31.46.124:6379` — content-service 두 파드(sp24n, v2pw9)와 chat-service에서 각각 **x4회, 04:32:51.5~04:33:00.6 UTC, 평균 3.0초 간격**.
  - 스케줄러 트레이스 6건이 모두 실패: `task battle-deadline-notification-scheduler.notify`, `outcome=ERROR`, `error="Redis command timed out"`, `exception=QueryTimeoutException`, `code.function=notifyEnd` — 04:33:00(2003ms/2000ms), 04:34:00(2002ms/2010ms), 04:35:00(2000ms/2000ms), 두 파드에서 각각.
  - 로그: `o.s.s.s.TaskUtils$LoggingErrorHandler - Unexpected error occurred in scheduled task` (13:33:02.003 / 13:33:02.027 KST), `Caused by: io.lettuce.core.RedisCommandTimeoutException: Command timed out after 2 second(s)`.
  - 부수 피해: `[user-cache] Redis 캐시 조회/저장 실패` x다수, `/api/feeds/scroll` 16,409ms·16,147ms `[HTTP-SLOW]`(캐시 미스 시 2초 타임아웃 × 사용자 수 직렬 누적).
- **확신도: 중간** — 사실 자체는 확정적이나, **제보된 "댓글 알림"과는 다른 알림 경로**다(`BATTLE_ITEM_COMMENT` 발행 경로는 Kafka, 이쪽은 배틀 마감 알림 스케줄러). 제보 시간대(최근 1시간)에는 포함된다.
- **대기·지연 구간 판정**
  - 실측 2,003ms / 2,010ms / 2,000ms vs 상한 2,000ms(로그에 명시된 Lettuce command timeout `2 second(s)`) → **만료됨**.
  - 최종 상태: **실패**. 태스크는 예외로 종료되고 `LoggingErrorHandler`가 삼킴 → 해당 주기 알림은 **폐기**. 다만 스케줄러가 60초 주기로 재실행되어 04:33/04:34/04:35 세 주기 연속 같은 실패 — 이는 재시도가 아니라 다음 주기 실행이다.
- **반증 데이터**
  - `up{job=redis, instance=infra-server}` **전 구간 1** — Redis exporter는 정상으로 보고했는데 앱 파드에서는 `Connection refused`. 네트워크/파드 레벨 문제이거나 exporter 시야가 다를 가능성이 있어 "Redis 프로세스 다운" 단정은 못 한다.
  - 04:48:35 댓글 트레이스에서는 `[user-cache] 캐시 HIT - userId: 1, elapsed: 4ms`, redis `GET` span 0.628ms로 **정상** → 후보 2는 후보 1의 원인이 아니다(시간·경로 모두 분리).

### 후보 3 — 컨슈머 측 적체/처리 실패

- **근거(반대 방향)**: `kafka_consumergroup_lag{consumergroup=notification-processors, topic=user.notifications}` 파티션 0~5 전부 **전 구간 0**. DLQ(`user.notifications.dlq`) lag도 0/-1. 소비 지연이나 적체 흔적 전무.
- **확신도: 낮음** (사실상 배제)
- **대기·지연 구간 판정**: **판정 불가** — 컨슈머 서비스가 무엇인지 호출 그래프에 엣지가 없고(`user.notifications`는 publish 엣지만 존재), 컨슈머 span·로그가 수집되지 않았다. 처리 성공/실패 최종 상태 근거 없음.
- **반증 데이터**: 위 lag 0 자체가 반증. 단, lag 0은 "컨슈머가 잘 따라잡았다"와 "메시지가 애초에 없었다"를 구분하지 못한다 — **데이터 부족**.

### 데이터 부족 항목 (결론 확신도에 반영)

- `http_server_requests_seconds_count{application="content-service", status="401"}` 시리즈 **수집 실패** → 인증 실패로 인한 알림 누락 가설은 평가 불가(다만 이를 지지하는 다른 근거도 없음).
- 04:47:00~04:50:45 구간에 **모든 Kafka exporter 시리즈가 동시 결측** → 브로커 다운 vs exporter 다운 분리 불가.
- content-service 두 파드의 `up`/`hikaricp_*`가 04:33:30~04:37:00 **결측**(값은 결측 전후 모두 1, 0으로 떨어진 적 없음) → 재시작 여부 판정 불가. `hikaricp_connections_active/pending`은 전 서비스 전 구간 0이라 커넥션 풀 고갈은 근거 없음(16.4초 `connection` span은 풀 대기가 아니라 트랜잭션 내부 Redis 타임아웃 누적).
- 소실 알림 총 건수, 영향 사용자 목록: **판정 불가**(트레이스 10건 샘플).

---

## 3. 권장 다음 조치

### 이미 발생한 피해: 복구 가능한가

**가능(수동)**. 근거: 댓글 원본은 MySQL에 남아 있다 — `tb_battle_item_comment`에 `commentId=327`이 커밋 완료. 알림 페이로드(`userId=7, type=BATTLE_ITEM_COMMENT, battleId=22, itemId=125`)를 DB에서 그대로 재구성할 수 있다.

- 복구 방법: `tb_battle_item_comment`에서 `created_at BETWEEN 2026-08-06T04:47:15Z AND 2026-08-06T04:50:15Z`(Kafka 다운 구간) 범위를 조회 → 각 댓글의 수신 대상에게 `user.notifications` 재발행. 후보 2 관련 배틀 마감 알림은 04:33:00~04:35:00 세 주기분을 스케줄러 수동 재실행으로 커버.
- **자동 복구는 불가**: DLQ(`user.notifications.dlq`) lag이 전 구간 0/-1로 실패 메시지가 적재되지 않았다. 예외가 `NotificationEventListener`에서 로깅만 되고 소실됐다.
- 복구 범위 확정 전 선행 필요: 위 "데이터 부족" 항목대로 04:47~04:50 구간의 전체 발행 실패 건수를 로그(`알림 발행 실패` 카운트)로 먼저 집계할 것. 관측된 건 1건뿐이다.

### 재발 방지

1. **발행 실패를 삼키지 말 것** — `NotificationEventListener.onNotification`이 `Send failed`를 로깅만 하고 종료한다. 실패 시 아웃박스 테이블 적재 또는 `user.notifications.dlq` 강제 produce로 전환(DLQ 토픽과 `notification-recovery` 컨슈머 그룹은 이미 존재하는데 사용되지 않고 있다).
2. **`max.block.ms` 60초는 과다** — 브로커 다운 시 알림 스레드가 요청당 60초 점유된다. 수 초 수준으로 낮추고 즉시 아웃박스 폴백.
3. **알람 추가**: `up{job=kafka} == 0` 및 `absent(kafka_brokers)` 1분 지속 시 페이지. 이번 3분 다운은 사용자 제보로만 발견됐다.
4. **Redis 경로(후보 2)**: `UserCacheStore` 캐시 조회/저장 실패는 이미 예외 전파를 막고 있지 않아 `/feeds/scroll`이 16초까지 늘어졌다. 캐시는 fail-open 처리하고, 명령 타임아웃 2초 × N사용자 직렬 호출을 배치/병렬화. 스케줄러 태스크는 Redis 예외 시 재시도 백오프.

### 복구 확인

1. `up{job=kafka}` / `kafka_brokers` 가 1로 회복 유지되는지 확인 — 04:50:30 이후 샘플(`04:52:30=1 … 05:04:45=1`)은 이미 회복을 보여준다.
2. 재발행 후 `kafka_consumergroup_lag{consumergroup=notification-processors, topic=user.notifications}`가 일시 상승 후 0으로 수렴하는지 확인 → 발행·소비 양방향이 살아있음을 증명.
3. 신규 댓글 1건을 테스트로 작성해 트레이스에서 `publish user.notifications` span이 `STATUS_CODE_OK` / 수십 ms 내 종료되는지 확인(장애 시 60,016ms·ERROR였다).
4. 대상 사용자(userId=7 포함, 재발행 목록 전체)에게 알림이 실제 도달했는지 최종 확인 — **단, 발행 이후 단말 전달까지의 경로는 이번 관측 데이터에 엣지·span이 없어 검증 근거가 없다. 해당 구간은 별도 수집 필요.**

---

## 관측 증거 (Evidence)

> 이 조사가 실제로 본 값이다. 원본 응답 전체는 `reports/raw/scan-1785990300-*.json`에 있다.

### 호출 그래프 (트레이스에서 추출)

```
content-service --db--> redis  1회  최대 0.6ms  [GET]
content-service --jdbc--> mysql/content (HikariPool-1)  46회  최대 16406.8ms
    events: acquired, commit
content-service --messaging--> kafka/user.notifications  1회  최대 60016.1ms  [publish]
    error: Topic user.notifications not present in metadata after 60000 ms.
content-service --service--> auth-service  4회  최대 226.0ms
```

### span (duration 상위 15 / 전체 78)

| ms | service | span | 시작 |
|---:|---|---|---|
| 60021.88 | content-service | `notification-publish` | 2026-08-06T04:48:35.955349Z |
| 60016.11 | content-service | `publish user.notifications` | 2026-08-06T04:48:35.955475Z |
| 16409.58 | content-service | `http get /feeds/scroll` | 2026-08-06T04:32:52.051686Z |
| 16407.83 | content-service | `secured request` | 2026-08-06T04:32:52.052099Z |
| 16406.80 | content-service | `connection` | 2026-08-06T04:32:52.052909Z |
| 16147.67 | content-service | `http get /feeds/scroll` | 2026-08-06T04:33:14.029553Z |
| 16145.87 | content-service | `secured request` | 2026-08-06T04:33:14.029983Z |
| 16144.26 | content-service | `connection` | 2026-08-06T04:33:14.031404Z |
| 2010.39 | content-service | `task battle-deadline-notification-scheduler.notify` | 2026-08-06T04:34:00.012779Z |
| 2003.62 | content-service | `task battle-deadline-notification-scheduler.notify` | 2026-08-06T04:33:00.000283Z |
| 2002.42 | content-service | `task battle-deadline-notification-scheduler.notify` | 2026-08-06T04:34:00.000730Z |
| 2000.96 | content-service | `task battle-deadline-notification-scheduler.notify` | 2026-08-06T04:33:00.026871Z |
| 2000.93 | content-service | `task battle-deadline-notification-scheduler.notify` | 2026-08-06T04:35:00.001161Z |
| 2000.83 | content-service | `task battle-deadline-notification-scheduler.notify` | 2026-08-06T04:35:00.015374Z |
| 226.00 | content-service | `http get` | 2026-08-06T04:33:00.166294Z |

### 로그 원문 (60 / 전체 1,023줄)

전체가 상한을 넘어 ERROR/WARN 줄을 우선 발췌했다. 나머지는 원본 파일에 있다.

```
2026-08-06T04:32:51.624990403Z  [chat-service]  [2m2026-08-06T13:32:51.624+09:00[0;39m [33m WARN [traceId=NONE,spanId=NONE,userId=NONE][0;39m [35m7[0;39m [2m--- [chat-service] [llEventLoop-6-2] [                                                 ] [0;39m[36mi.l.core.protocol.ConnectionWatchdog    [0;39m [2m:[0;39m Cannot reconnect to [172.31.46.124/<unresolved>:6379]: finishConnect(..) failed: Connection refused: /172.31.46.124:6379
2026-08-06T04:32:51.625665622Z  [chat-service]  [2m2026-08-06T13:32:51.625+09:00[0;39m [33m WARN [traceId=NONE,spanId=NONE,userId=NONE][0;39m [35m7[0;39m [2m--- [chat-service] [llEventLoop-6-1] [                                                 ] [0;39m[36mi.l.core.protocol.ConnectionWatchdog    [0;39m [2m:[0;39m Cannot reconnect to [172.31.46.124/<unresolved>:6379]: finishConnect(..) failed: Connection refused: /172.31.46.124:6379
2026-08-06T04:32:54.076768228Z  [content-service]  2026-08-06 13:32:54.072 [http-nio-8082-exec-5] ERROR [traceId=6a740e7417e8b0b83aa26f5450e14d12,spanId=f8c1f3b4bf5554c8,userId=NONE] c.e.t.e.user.service.UserCacheStore - [user-cache] Redis 캐시 조회 실패: cacheKey=user:info:1
2026-08-06T04:32:54.076790904Z  [content-service]  org.springframework.dao.QueryTimeoutException: Redis command timed out
2026-08-06T04:32:54.076795452Z  [content-service]  at org.springframework.data.redis.connection.lettuce.LettuceExceptionConverter.convert(LettuceExceptionConverter.java:68)
2026-08-06T04:32:54.076799261Z  [content-service]  at org.springframework.data.redis.connection.lettuce.LettuceExceptionConverter.convert(LettuceExceptionConverter.java:41)
2026-08-06T04:32:54.076803703Z  [content-service]  at org.springframework.data.redis.PassThroughExceptionTranslationStrategy.translate(PassThroughExceptionTranslationStrategy.java:40)
2026-08-06T04:32:54.076807423Z  [content-service]  at org.springframework.data.redis.FallbackExceptionTranslationStrategy.translate(FallbackExceptionTranslationStrategy.java:38)
2026-08-06T04:32:54.076810987Z  [content-service]  at org.springframework.data.redis.connection.lettuce.LettuceConnection.convertLettuceAccessException(LettuceConnection.java:310)
2026-08-06T04:32:54.077053943Z  [content-service]  at org.springframework.security.web.access.ExceptionTranslationFilter.doFilter(ExceptionTranslationFilter.java:126)
2026-08-06T04:32:54.077055999Z  [content-service]  at org.springframework.security.web.access.ExceptionTranslationFilter.doFilter(ExceptionTranslationFilter.java:120)
2026-08-06T04:32:54.077341011Z  [content-service]  Caused by: io.lettuce.core.RedisCommandTimeoutException: Command timed out after 2 second(s)
2026-08-06T04:32:54.077343427Z  [content-service]  at io.lettuce.core.internal.ExceptionFactory.createTimeoutException(ExceptionFactory.java:63)
2026-08-06T04:32:54.269738674Z  [content-service]  org.springframework.dao.QueryTimeoutException: Redis command timed out
2026-08-06T04:32:54.269742119Z  [content-service]  at org.springframework.data.redis.connection.lettuce.LettuceExceptionConverter.convert(LettuceExceptionConverter.java:68)
2026-08-06T04:32:54.269745196Z  [content-service]  at org.springframework.data.redis.connection.lettuce.LettuceReactiveRedisConnection.lambda$translateException$0(LettuceReactiveRedisConnection.java:242)
2026-08-06T04:32:54.269782628Z  [content-service]  at io.lettuce.core.protocol.CommandWrapper.completeExceptionally(CommandWrapper.java:132)
2026-08-06T04:32:54.269806594Z  [content-service]  Caused by: io.lettuce.core.RedisCommandTimeoutException: Command timed out after 2 second(s)
2026-08-06T04:32:54.269808991Z  [content-service]  at io.lettuce.core.internal.ExceptionFactory.createTimeoutException(ExceptionFactory.java:63)
2026-08-06T04:32:54.323880577Z  [chat-service]  [2m2026-08-06T13:32:54.322+09:00[0;39m [33m WARN [traceId=NONE,spanId=NONE,userId=NONE][0;39m [35m7[0;39m [2m--- [chat-service] [xecutorLoop-3-2] [                                                 ] [0;39m[36mo.s.b.a.d.r.RedisReactiveHealthIndicator[0;39m [2m:[0;39m Redis health check failed
2026-08-06T04:32:54.323910718Z  [chat-service]  org.springframework.dao.QueryTimeoutException: Redis command timed out
2026-08-06T04:32:54.323913921Z  [chat-service]  at org.springframework.data.redis.connection.lettuce.LettuceExceptionConverter.convert(LettuceExceptionConverter.java:68) ~[spring-data-redis-3.5.1.jar!/:3.5.1]
2026-08-06T04:32:54.323916869Z  [chat-service]  at org.springframework.data.redis.connection.lettuce.LettuceReactiveRedisConnection.lambda$translateException$0(LettuceReactiveRedisConnection.java:242) ~[spring-data-redis-3.5.1.jar!/:3.5.1]
2026-08-06T04:32:54.323965610Z  [chat-service]  at io.lettuce.core.protocol.CommandWrapper.completeExceptionally(CommandWrapper.java:132) ~[lettuce-core-6.6.0.RELEASE.jar!/:6.6.0.RELEASE/643bd47]
2026-08-06T04:32:54.323987961Z  [chat-service]  Caused by: io.lettuce.core.RedisCommandTimeoutException: INFO. Command timed out after 2 second(s)
2026-08-06T04:32:54.323990130Z  [chat-service]  at io.lettuce.core.internal.ExceptionFactory.createTimeoutException(ExceptionFactory.java:75) ~[lettuce-core-6.6.0.RELEASE.jar!/:6.6.0.RELEASE/643bd47]
2026-08-06T04:32:56.077391159Z  [content-service]  2026-08-06 13:32:56.075 [http-nio-8082-exec-5] ERROR [traceId=6a740e7417e8b0b83aa26f5450e14d12,spanId=f8c1f3b4bf5554c8,userId=NONE] c.e.t.e.user.service.UserCacheStore - [user-cache] Redis 캐시 조회 실패: cacheKey=user:info:3
2026-08-06T04:32:56.077422485Z  [content-service]  org.springframework.dao.QueryTimeoutException: Redis command timed out
2026-08-06T04:32:56.077426259Z  [content-service]  at org.springframework.data.redis.connection.lettuce.LettuceExceptionConverter.convert(LettuceExceptionConverter.java:68)
2026-08-06T04:32:56.077429103Z  [content-service]  at org.springframework.data.redis.connection.lettuce.LettuceExceptionConverter.convert(LettuceExceptionConverter.java:41)
2026-08-06T04:32:56.077432421Z  [content-service]  at org.springframework.data.redis.PassThroughExceptionTranslationStrategy.translate(PassThroughExceptionTranslationStrategy.java:40)
2026-08-06T04:32:56.077435439Z  [content-service]  at org.springframework.data.redis.FallbackExceptionTranslationStrategy.translate(FallbackExceptionTranslationStrategy.java:38)
2026-08-06T04:32:56.077438552Z  [content-service]  at org.springframework.data.redis.connection.lettuce.LettuceConnection.convertLettuceAccessException(LettuceConnection.java:310)
2026-08-06T04:32:56.077731602Z  [content-service]  at org.springframework.security.web.access.ExceptionTranslationFilter.doFilter(ExceptionTranslationFilter.java:126)
2026-08-06T04:32:56.077734291Z  [content-service]  at org.springframework.security.web.access.ExceptionTranslationFilter.doFilter(ExceptionTranslationFilter.java:120)
2026-08-06T04:32:56.078067490Z  [content-service]  Caused by: io.lettuce.core.RedisCommandTimeoutException: Command timed out after 2 second(s)
2026-08-06T04:32:56.078070205Z  [content-service]  at io.lettuce.core.internal.ExceptionFactory.createTimeoutException(ExceptionFactory.java:63)
2026-08-06T04:32:58.080823016Z  [content-service]  2026-08-06 13:32:58.079 [http-nio-8082-exec-5] ERROR [traceId=6a740e7417e8b0b83aa26f5450e14d12,spanId=f8c1f3b4bf5554c8,userId=NONE] c.e.t.e.user.service.UserCacheStore - [user-cache] Redis 캐시 조회 실패: cacheKey=user:info:7
2026-08-06T04:32:58.080851649Z  [content-service]  org.springframework.dao.QueryTimeoutException: Redis command timed out
2026-08-06T04:32:58.080856055Z  [content-service]  at org.springframework.data.redis.connection.lettuce.LettuceExceptionConverter.convert(LettuceExceptionConverter.java:68)
2026-08-06T04:32:58.080858889Z  [content-service]  at org.springframework.data.redis.connection.lettuce.LettuceExceptionConverter.convert(LettuceExceptionConverter.java:41)
2026-08-06T04:32:58.080862494Z  [content-service]  at org.springframework.data.redis.PassThroughExceptionTranslationStrategy.translate(PassThroughExceptionTranslationStrategy.java:40)
2026-08-06T04:32:58.080865422Z  [content-service]  at org.springframework.data.redis.FallbackExceptionTranslationStrategy.translate(FallbackExceptionTranslationStrategy.java:38)
2026-08-06T04:32:58.080868130Z  [content-service]  at org.springframework.data.redis.connection.lettuce.LettuceConnection.convertLettuceAccessException(LettuceConnection.java:310)
2026-08-06T04:32:58.081114876Z  [content-service]  at org.springframework.security.web.access.ExceptionTranslationFilter.doFilter(ExceptionTranslationFilter.java:126)
2026-08-06T04:32:58.081117312Z  [content-service]  at org.springframework.security.web.access.ExceptionTranslationFilter.doFilter(ExceptionTranslationFilter.java:120)
2026-08-06T04:32:58.081439429Z  [content-service]  Caused by: io.lettuce.core.RedisCommandTimeoutException: Command timed out after 2 second(s)
2026-08-06T04:32:58.081442419Z  [content-service]  at io.lettuce.core.internal.ExceptionFactory.createTimeoutException(ExceptionFactory.java:63)
2026-08-06T04:33:00.164766092Z  [content-service]  2026-08-06 13:33:00.088 [http-nio-8082-exec-5] ERROR [traceId=6a740e7417e8b0b83aa26f5450e14d12,spanId=f8c1f3b4bf5554c8,userId=NONE] c.e.t.e.user.service.UserCacheStore - [user-cache] Redis 캐시 조회 실패: cacheKey=user:info:9
2026-08-06T04:33:00.164818848Z  [content-service]  org.springframework.dao.QueryTimeoutException: Redis command timed out
2026-08-06T04:33:00.164823639Z  [content-service]  at org.springframework.data.redis.connection.lettuce.LettuceExceptionConverter.convert(LettuceExceptionConverter.java:68)
2026-08-06T04:33:00.164826534Z  [content-service]  at org.springframework.data.redis.connection.lettuce.LettuceExceptionConverter.convert(LettuceExceptionConverter.java:41)
2026-08-06T04:33:00.164829643Z  [content-service]  at org.springframework.data.redis.PassThroughExceptionTranslationStrategy.translate(PassThroughExceptionTranslationStrategy.java:40)
2026-08-06T04:33:00.164832543Z  [content-service]  at org.springframework.data.redis.FallbackExceptionTranslationStrategy.translate(FallbackExceptionTranslationStrategy.java:38)
2026-08-06T04:33:00.164835318Z  [content-service]  at org.springframework.data.redis.connection.lettuce.LettuceConnection.convertLettuceAccessException(LettuceConnection.java:310)
2026-08-06T04:33:00.625012262Z  [chat-service]  [2m2026-08-06T13:33:00.624+09:00[0;39m [33m WARN [traceId=NONE,spanId=NONE,userId=NONE][0;39m [35m7[0;39m [2m--- [chat-service] [llEventLoop-6-2] [                                                 ] [0;39m[36mi.l.core.protocol.ConnectionWatchdog    [0;39m [2m:[0;39m Cannot reconnect to [172.31.46.124/<unresolved>:6379]: finishConnect(..) failed: Connection refused: /172.31.46.124:6379
2026-08-06T04:33:00.625233699Z  [chat-service]  [2m2026-08-06T13:33:00.625+09:00[0;39m [33m WARN [traceId=NONE,spanId=NONE,userId=NONE][0;39m [35m7[0;39m [2m--- [chat-service] [llEventLoop-6-1] [                                                 ] [0;39m[36mi.l.core.protocol.ConnectionWatchdog    [0;39m [2m:[0;39m Cannot reconnect to [172.31.46.124/<unresolved>:6379]: finishConnect(..) failed: Connection refused: /172.31.46.124:6379
2026-08-06T04:33:02.008070881Z  [content-service]  2026-08-06 13:33:02.003 [scheduling-1] ERROR [traceId=NONE,spanId=NONE,userId=NONE] o.s.s.s.TaskUtils$LoggingErrorHandler - Unexpected error occurred in scheduled task
2026-08-06T04:33:02.053382659Z  [content-service]  2026-08-06 13:33:02.027 [scheduling-1] ERROR [traceId=NONE,spanId=NONE,userId=NONE] o.s.s.s.TaskUtils$LoggingErrorHandler - Unexpected error occurred in scheduled task
2026-08-06T04:33:02.407966466Z  [content-service]  2026-08-06 13:33:02.403 [http-nio-8082-exec-5] ERROR [traceId=6a740e7417e8b0b83aa26f5450e14d12,spanId=f8c1f3b4bf5554c8,userId=NONE] c.e.t.e.user.service.UserCacheStore - [user-cache] Redis 캐시 저장 실패: userId=1
```

### 메트릭 시계열

| 쿼리 | series | 점 | min | max | last | 값이 0이던 구간 |
|---|---|---:|---:|---:|---:|---|
| `hikaricp_connections_active` | `{__name__=hikaricp_connections_active, application=auth-service, cluster=yogurtte-k3s-prod, container=auth-service, instance=10.42.1.51:8090, job=auth-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=auth-service-5999bb9f5c-lzp4p, pool=HikariPool-1, service=auth-service}` | 160 | 0 | 0 | 0 | **2026-08-06T04:25:00Z ~ 2026-08-06T05:04:45Z** |
| `hikaricp_connections_active` | `{__name__=hikaricp_connections_active, application=chat-service, cluster=yogurtte-k3s-prod, container=chat-service, instance=10.42.1.55:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-fdcc7c776-kh2dl, pool=HikariPool-1}` | 152 | 0 | 0 | 0 | **2026-08-06T04:25:00Z ~ 2026-08-06T05:04:45Z** |
| `hikaricp_connections_active` | `{__name__=hikaricp_connections_active, application=content-service, cluster=yogurtte-k3s-prod, container=content-service, instance=10.42.1.53:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-85f648fcff-sp24n, pool=HikariPool-1}` | 148 | 0 | 0 | 0 | **2026-08-06T04:25:00Z ~ 2026-08-06T05:04:45Z** |
| `hikaricp_connections_active` | `{__name__=hikaricp_connections_active, application=content-service, cluster=yogurtte-k3s-prod, container=content-service, instance=10.42.3.46:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-85f648fcff-v2pw9, pool=HikariPool-1}` | 148 | 0 | 0 | 0 | **2026-08-06T04:25:00Z ~ 2026-08-06T05:04:45Z** |
| `hikaricp_connections_pending` | `{__name__=hikaricp_connections_pending, application=auth-service, cluster=yogurtte-k3s-prod, container=auth-service, instance=10.42.1.51:8090, job=auth-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=auth-service-5999bb9f5c-lzp4p, pool=HikariPool-1, service=auth-service}` | 160 | 0 | 0 | 0 | **2026-08-06T04:25:00Z ~ 2026-08-06T05:04:45Z** |
| `hikaricp_connections_pending` | `{__name__=hikaricp_connections_pending, application=chat-service, cluster=yogurtte-k3s-prod, container=chat-service, instance=10.42.1.55:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-fdcc7c776-kh2dl, pool=HikariPool-1}` | 152 | 0 | 0 | 0 | **2026-08-06T04:25:00Z ~ 2026-08-06T05:04:45Z** |
| `hikaricp_connections_pending` | `{__name__=hikaricp_connections_pending, application=content-service, cluster=yogurtte-k3s-prod, container=content-service, instance=10.42.1.53:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-85f648fcff-sp24n, pool=HikariPool-1}` | 148 | 0 | 0 | 0 | **2026-08-06T04:25:00Z ~ 2026-08-06T05:04:45Z** |
| `hikaricp_connections_pending` | `{__name__=hikaricp_connections_pending, application=content-service, cluster=yogurtte-k3s-prod, container=content-service, instance=10.42.3.46:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-85f648fcff-v2pw9, pool=HikariPool-1}` | 148 | 0 | 0 | 0 | **2026-08-06T04:25:00Z ~ 2026-08-06T05:04:45Z** |
| `rate(jvm_gc_pause_seconds_sum[5m])` | `{action=end of major GC, application=chat-service, cause=Allocation Failure, cluster=yogurtte-k3s-prod, container=chat-service, gc=MarkSweepCompact, instance=10.42.1.55:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-fdcc7c776-kh2dl}` | 160 | 0 | 0 | 0 | **2026-08-06T04:25:00Z ~ 2026-08-06T05:04:45Z** |
| `rate(jvm_gc_pause_seconds_sum[5m])` | `{action=end of minor GC, application=auth-service, cause=G1 Evacuation Pause, cluster=yogurtte-k3s-prod, container=auth-service, gc=G1 Young Generation, instance=10.42.1.51:8090, job=auth-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=auth-service-5999bb9f5c-lzp4p, service=auth-service}` | 160 | 0 | 0.000 | 0 | **2026-08-06T04:25:00Z ~ 2026-08-06T04:35:30Z, 2026-08-06T04:39:45Z ~ 2026-08-06T05:04:45Z** |
| `rate(jvm_gc_pause_seconds_sum[5m])` | `{action=end of minor GC, application=auth-service, cause=Metadata GC Threshold, cluster=yogurtte-k3s-prod, container=auth-service, gc=G1 Young Generation, instance=10.42.1.51:8090, job=auth-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=auth-service-5999bb9f5c-lzp4p, service=auth-service}` | 160 | 0 | 0 | 0 | **2026-08-06T04:25:00Z ~ 2026-08-06T05:04:45Z** |
| `rate(jvm_gc_pause_seconds_sum[5m])` | `{action=end of minor GC, application=chat-service, cause=Allocation Failure, cluster=yogurtte-k3s-prod, container=chat-service, gc=Copy, instance=10.42.1.55:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-fdcc7c776-kh2dl}` | 160 | 0.000 | 0.001 | 0.000 | — |
| `rate(jvm_gc_pause_seconds_sum[5m])` | `{action=end of minor GC, application=content-service, cause=Allocation Failure, cluster=yogurtte-k3s-prod, container=content-service, gc=Copy, instance=10.42.1.53:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-85f648fcff-sp24n}` | 160 | 0 | 0.000 | 0 | **2026-08-06T04:25:00Z ~ 2026-08-06T04:28:30Z, 2026-08-06T04:32:45Z ~ 2026-08-06T04:48:30Z, 2026-08-06T04:52:45Z ~ 2026-08-06T05:04:45Z** |
| `rate(jvm_gc_pause_seconds_sum[5m])` | `{action=end of minor GC, application=content-service, cause=Allocation Failure, cluster=yogurtte-k3s-prod, container=content-service, gc=Copy, instance=10.42.3.46:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-85f648fcff-v2pw9}` | 160 | 0 | 0.000 | 0 | **2026-08-06T04:25:00Z ~ 2026-08-06T04:29:45Z, 2026-08-06T04:34:00Z ~ 2026-08-06T04:48:45Z, 2026-08-06T04:53:00Z ~ 2026-08-06T05:04:45Z** |
| `up` | `{__name__=up, app=node-exporter, cluster=yogurtte-k3s-prod, component=metrics, container=node-exporter, instance=ip-172-31-40-241, job=integrations/node_exporter, k8s_cluster_name=yogurtte-k3s-prod, namespace=monitoring, pod=grafana-k8s-monitoring-node-exporter-cph5l, source=kubernetes, workload=DaemonSet/grafana-k8s-monitoring-node-exporter}` | 160 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, app=node-exporter, cluster=yogurtte-k3s-prod, component=metrics, container=node-exporter, instance=ip-172-31-45-39, job=integrations/node_exporter, k8s_cluster_name=yogurtte-k3s-prod, namespace=monitoring, pod=grafana-k8s-monitoring-node-exporter-9kvqt, source=kubernetes, workload=DaemonSet/grafana-k8s-monitoring-node-exporter}` | 160 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, cluster=yogurtte-k3s-prod, container=auth-service, instance=10.42.1.51:8090, job=auth-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=auth-service-5999bb9f5c-lzp4p}` | 160 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, cluster=yogurtte-k3s-prod, container=chat-service, instance=10.42.1.55:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-fdcc7c776-kh2dl}` | 152 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, cluster=yogurtte-k3s-prod, container=content-service, instance=10.42.1.53:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-85f648fcff-sp24n}` | 148 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, cluster=yogurtte-k3s-prod, container=content-service, instance=10.42.3.46:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-85f648fcff-v2pw9}` | 148 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, cluster=yogurtte-k3s-prod, instance=10.42.1.248:8080, job=integrations/kubernetes/kube-state-metrics, k8s_cluster_name=yogurtte-k3s-prod, source=kubernetes}` | 160 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, cluster=yogurtte-k3s-prod, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod}` | 160 | 0 | 1 | 1 | **2026-08-06T04:47:15Z ~ 2026-08-06T04:50:15Z** |
| `mongodb_up` | `{__name__=mongodb_up, cluster=yogurtte-k3s-prod, instance=infra-server, job=mongodb, k8s_cluster_name=yogurtte-k3s-prod}` | 160 | 1 | 1 | 1 | — |
| `kafka_brokers` | `{__name__=kafka_brokers, cluster=yogurtte-k3s-prod, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod}` | 147 | 0 | 1 | 1 | **2026-08-06T04:50:30Z ~ 2026-08-06T04:50:30Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=chat-service-fcm-tokens, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=0, topic=user.fcm-tokens}` | 146 | 0 | 0 | 0 | **2026-08-06T04:25:00Z ~ 2026-08-06T05:04:45Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=chat-service-fcm-tokens, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=1, topic=user.fcm-tokens}` | 146 | 0 | 0 | 0 | **2026-08-06T04:25:00Z ~ 2026-08-06T05:04:45Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=chat-service-fcm-tokens, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=2, topic=user.fcm-tokens}` | 146 | 0 | 0 | 0 | **2026-08-06T04:25:00Z ~ 2026-08-06T05:04:45Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=chat-service-notification-settings, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=0, topic=user.notification-settings}` | 146 | 0 | 0 | 0 | **2026-08-06T04:25:00Z ~ 2026-08-06T05:04:45Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=chat-service-notification-settings, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=1, topic=user.notification-settings}` | 146 | 0 | 0 | 0 | **2026-08-06T04:25:00Z ~ 2026-08-06T05:04:45Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=chat-service-notification-settings, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=2, topic=user.notification-settings}` | 146 | 0 | 0 | 0 | **2026-08-06T04:25:00Z ~ 2026-08-06T05:04:45Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=db-writer, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=0, topic=chat.messages}` | 146 | 0 | 0 | 0 | **2026-08-06T04:25:00Z ~ 2026-08-06T05:04:45Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=db-writer, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=1, topic=chat.messages}` | 146 | 0 | 0 | 0 | **2026-08-06T04:25:00Z ~ 2026-08-06T05:04:45Z** |
| `websocket_active_users` | `{__name__=websocket_active_users, application=chat-service, cluster=yogurtte-k3s-prod, container=chat-service, instance=10.42.1.55:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-fdcc7c776-kh2dl}` | 152 | 0 | 0 | 0 | **2026-08-06T04:25:00Z ~ 2026-08-06T05:04:45Z** |

값이 0이던 구간은 굵게 표시했다 — 프로세스가 사라져 시계열이 꺾인 것이 유일한 신호인 장애가 있다.

