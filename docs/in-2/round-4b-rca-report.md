# RCA Report — `scan-1785803700`

| 항목 | 값 |
|---|---|
| 모드 | rca |
| 질문 | 최근 1시간 안에 댓글 알림이 안 왔다는 제보가 있다. 원인을 조사해줘 |
| 시각 | 2026-08-04T06:58:39.409967100Z |
| provider | claude-cli |
| model | `claude-opus-5` · turns 1 |
| prompt | `.\prompts\system-prompt.md` |
| tokens | in 423332 (cacheRead 235,113 · cacheCreate 188,215) / out 9377 · cost $2.2342 |
| elapsed | total 272397ms (tempo 3599 · loki 1386 · mimir 1730 · assemble 273 · llm 257287) |

## 탐색 (Triage)

| 항목 | 값 |
|---|---|
| 시간창 해석 | 명시적 from/to |
| 시간창 확신도 | EXACT |
| 스윕 창 | 2026-08-04T00:29:18Z ~ 2026-08-04T01:29:18Z |
| 좁힌 창 | 2026-08-04T00:35:00Z ~ 2026-08-04T01:29:18Z |
| 대상 | content-service, chat-service |
| traceId | 6a713715a33b3693add43f02dd87289b |
| 트레이스 후보 | 8건 |
| 장애 후보 | 8건 · 선택 INC-1, INC-2, INC-4, INC-5, INC-6, INC-7, INC-8 |
| 계획 파싱 | 성공 |
| 스윕 컨텍스트 | 후보 + 원본 (A) |
| prompt | `.\prompts\triage-prompt.md` |
| tokens | in 55439 / out 2976 · cost $0.4332 |
| chars | 컨텍스트 45,469 + 프롬프트 1,399 = **46,868** |
| elapsed | survey 2627ms · llm 52436ms |

**선정 이유**: kafka 브로커 다운 두 구간과 댓글 등록 API의 60초 타임아웃·content-service/chat-service 에러 급증이 시각·경로 모두 일치하므로, 알림 이벤트 발행·소비 구간을 두 회차 모두 함께 열어야 한다.

**근거**

- kafka job up이 1→0으로 두 번 떨어짐: 00:49:18Z~00:54:18Z, 01:24:18Z (min_over_time(up[5m]), instance=infra-server job=kafka) — 같은 창에서 mongodb/redis/node-infra up은 계속 1이라 인프라 전체 장애가 아니라 kafka 단독
- 댓글 등록 API가 kafka 다운 직후마다 60초 정각 타임아웃: trace 6a713715a33b3693add43f02dd87289b 60,083ms(00:49:25Z), 6a713eb5c566f210b35a9bd582a5f37a 60,050ms(01:21:57Z) — 두 trace 모두 root=content-service POST /battles/{battleId}/items/{itemId}/comments, 15 span 중 error 2개
- content-service ERROR/WARN 급증이 두 회차 모두 kafka 다운과 겹침: 301건(00:45~00:50Z), 154건(00:50~00:55Z), 405건(01:20~01:25Z)
- chat-service ERROR/WARN 20건(00:40~00:45Z), 16건(00:45~00:50Z) — 알림 소비/FCM 발송 측도 같은 시각에 실패 중
- Tempo 지연 검색(duration>3s && status!=error) 0건 — 에러 없이 느려진 요청은 없다. 즉 증상은 '느림'이 아니라 '60초 후 실패'다
- kafka_consumergroup_lag는 notification/notification-processors 포함 전 구간 0 — 다운 구간에도 0인 것은 정상 소비가 아니라 exporter가 브로커와 함께 죽어 관측이 끊긴 것으로 의심되므로, 랙 0을 근거로 소비 정상이라 결론내지 않는다
- websocket_active_users가 창 전체 0 — 실시간 푸시 경로가 아니라 Kafka user.notifications → FCM 경로가 유일한 알림 전달 통로임을 뒷받침

**스윕이 찾은 트레이스** (고른 것은 6a713715a33b3693add43f02dd87289b)

| traceId | 채널 | root service | root span | ms |
|---|---|---|---|---:|
| `6a713eb5c566f210b35a9bd582a5f37a` | error | content-service | http post /battles/{battleId}/items/{itemId}/comments | 60050 |
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

## INC-8  content-service  |  http post /battles/{battleId}/items/{itemId}/comments
- 구간: 2026-08-04T01:21:57.004052Z ~ 2026-08-04T01:22:57.054052Z  (TEMPO · 시각 정확)
- content-service http post /battles/{battleId}/items/{itemId}/comments 60,050ms (error 채널)
- traceId: 6a713eb5c566f210b35a9bd582a5f37a
- 같은 시각의 다른 후보: INC-6, INC-7  (인과 여부는 판단하지 않았다)

**기각한 후보**

- INC-3 — battle-deadline-notification-scheduler.notify 2초 타임아웃은 마감 알림 스케줄러라 제보된 '댓글 알림'과 기능이 다르고, 시각이 INC-2·INC-4 창 안이라 별도로 고르지 않아도 같은 조사 범위에 포함된다.

**보정 기록**

- 창을 후보 [INC-1, INC-2, INC-4, INC-5, INC-6, INC-7, INC-8] 의 신호 시각에서 계산했다 (2026-08-04T00:35:00Z ~ 2026-08-04T01:29:18Z)

**스윕 무신호/실패**

- Tempo 지연 검색 '{ duration > 3s && status != error }'이 이 창에서 0건이다. 임계값보다 느린 요청이 없었다는 뜻이다.

## 수집 범위 (Coverage)

- **window**: 2026-08-04T00:35:00Z ~ 2026-08-04T01:29:18Z (3258s)
- **trace**: 41,206B / 41 spans
- **창 안 후보 트레이스**: 10건 / 41,206B
- **logs**: errwarn=291,424B · traceId=8,544B
- **metrics**: 8 수집 / 315,882B, 누락 [sum(rate(http_server_requests_seconds_count{application="content-service", status="401"}[1m]))]
- **context**: 374,144 chars (+ 시스템 프롬프트 805 chars)

### 토큰 축 (개선 지표)

| 단계 | 총 in | chars | 컨텍스트 토큰 |
|---|---:|---:|---:|
| 탐색 | 55,439 | 46,868 | 26,013 |
| 분석 | 423,332 | 374,949 | 393,906 |
| **합계** | **478,771** | | **419,919** |

- **overheadTokens 29,426 tok — 이 회차에 실측**(1자 프롬프트 프로브, 같은 명령·모델·샌드박스).
  호출마다 한 번씩 얹히므로 단계별로 뺀다. **다른 날 상수를 쓰지 않았다 = `█ 실측`.**
- contextTokens (count_tokens API): 측정 안 됨 — 구독 CLI 경로엔 API 키가 없다
- 비용은 **API 환산 추정치**다. 구독 계정으로 호출하므로 실제 청구액이 아니다.

## 수집 실패/누락

- Metric 'sum(rate(http_server_requests_seconds_count{application="content-service", status="401"}[1m]))' returned no series in this window; skipped.

---

조사 데이터만으로 분석했다. 결론부터: **댓글은 DB에 정상 저장됐고, 알림 발행(Kafka publish)만 실패해서 폐기됐다.**

## 1. 원인 후보 랭킹 (최대 3개)

1. **Kafka 브로커(infra-server, 172.31.46.124:9092) 다운 → content-service의 `user.notifications` publish가 60초 타임아웃 후 실패·폐기**
2. **브로커 복귀 후에도 `user.notifications` 토픽 부재 + 자동생성 실패(INVALID_REPLICATION_FACTOR) → 알림 경로 지속 차단**
3. **Redis(172.31.46.124:6379) 단절 → ShedLock 획득 실패로 배틀 마감 알림 스케줄러 실행 실패** (댓글 알림과는 다른 경로, 부수 장애)

---

## 2. 후보별 근거

### 후보 1 — Kafka 브로커 다운으로 인한 publish 실패

- **근거**
  - 트레이스 `6a713715a33b3693add43f02dd87289b`: `http post /battles/{battleId}/items/{itemId}/comments` → `outcome=SUCCESS, status=200`, 소요 63.9ms. JDBC `connection` span에 `acquired`/`commit` 이벤트 존재, `insert into tb_battle_item_comment` `jdbc.row-affected=1`, `jdbc.generated-keys=254`. 로그: `[battle] 댓글 작성 완료 - commentId: 254, battleId: 22, itemId: 125`.
  - 같은 트레이스의 자식 span `publish user.notifications` (`SPAN_KIND_PRODUCER`)만 `STATUS_CODE_ERROR`, `error="Topic user.notifications not present in metadata after 60000 ms."`. 부모 `notification-publish`(`NotificationEventListener.onNotification`)도 `error="Send failed"`.
  - 동일 패턴 2건: 트레이스 `6a713eb5c566f210b35a9bd582a5f37a` (commentId 256, 01:21:57.014Z 작성 → 01:22:57.051Z 발행 실패). 수집된 댓글 트레이스 2건이 **모두** 실패.
  - 메트릭 `up{job=kafka}`: 0인 구간 `00:48:00Z~00:51:15Z`, `01:20:30Z~01:23:45Z`. `kafka_brokers` 시리즈도 같은 두 구간 결측.
  - 두 publish 시도 시각(`00:49:25.769Z~00:50:25.783Z`, `01:21:57.050Z~01:22:57.051Z`)이 **각각 브로커 다운 구간 안에 완전히 포함**된다.
  - 로그 원문: `[Producer clientId=content-service-producer-1] Connection to node 1 (/172.31.46.124:9092) could not be established. Node may not be available.` — 10:20:25.746 KST부터 초당 1회꼴로 두 파드 모두에서 연속 출력.
- **확신도**: 높음
- **반증 데이터**
  - `kafka_consumergroup_lag{topic="user.notifications", consumergroup="notification-processors", partition=0..5}`가 다운 구간을 제외하면 전 구간 존재하고 값 0 — 토픽 메타데이터가 exporter에는 보였다는 뜻이라, 프로듀서가 받은 `UNKNOWN_TOPIC_OR_PARTITION`과 상충한다. 즉 "브로커 다운" 단일 설명으로는 후보 2의 토픽 부재 신호를 못 덮는다.
  - `up{job=node-infra}`, `up{job=redis}`는 전 구간 1 — 호스트 자체가 죽은 흔적은 없다.
- **대기·지연 판정**
  - 실측 대기 60013.4ms / 60001.2ms vs 상한 60000ms(`max.block.ms`, 에러 메시지의 "after 60000 ms") → **만료됨**.
  - 최종 상태: **실패 후 폐기**. `LoggingProducerListener`가 예외를 로깅하고 `NotificationEventListener`가 `[notify] 알림 발행 실패: userId=7, type=BATTLE_ITEM_COMMENT, error=Send failed`로 끝난다. 재시도·DLQ 적재 span이나 로그가 전혀 없다.
  - HTTP 응답은 200으로 이미 반환된 뒤(비동기 `notification-1`/`notification-2` 스레드) 실패했으므로 사용자에게는 성공으로 보인다.

### 후보 2 — 토픽 부재 및 복제계수 불일치로 인한 지속 차단

- **근거**
  - `2026-08-04 10:23:39.576` (파드 h2f6n): `Error while fetching metadata with correlation id 480 : {user.notifications=UNKNOWN_TOPIC_OR_PARTITION}`
  - `2026-08-04 10:23:39.590` (파드 nq9l2): `Error while fetching metadata with correlation id 484 : {user.notifications=INVALID_REPLICATION_FACTOR}`
  - 이 두 줄은 `up{job=kafka}`가 1로 복귀한 `01:23:45Z` 직전, 즉 **브로커가 살아난 직후**의 응답이다. 브로커는 떴는데 토픽이 없고, 자동생성이 복제계수 때문에 거부됐다는 뜻이다. `kafka_brokers`는 전 구간 값 1 — 브로커 1대인데 토픽 기본 RF가 1보다 크게 설정돼 있으면 정확히 이 에러가 난다.
- **확신도**: 중간 (토픽 목록·브로커 설정을 직접 확인하지 못했다)
- **반증 데이터**: 위와 동일 — `kafka_consumergroup_lag{topic="user.notifications"}` 파티션 0~5가 조회 창 전체에 걸쳐 시리즈로 존재한다. 토픽이 처음부터 없었다면 나올 수 없는 시리즈다. 따라서 "토픽이 원래 없었다"가 아니라 "브로커 재기동 과정에서 토픽/메타데이터가 유실됐다"는 해석이 더 맞고, 유실 시점을 특정할 데이터는 없다.
- **대기·지연 판정**: 이 메타데이터 응답 자체는 즉시 반환된 에러라 대기 구간이 아니다. 다만 이 상태가 지속되면 후보 1과 동일하게 매 발행 시도가 60초 대기 후 만료된다. `01:23:39Z` 이후의 발행 시도 데이터가 없어 **현재도 차단 중인지는 판정 불가**.

### 후보 3 — Redis 단절로 인한 마감 알림 스케줄러 실패 (다른 경로)

- **근거**
  - `Cannot reconnect to [172.31.46.124/<unresolved>:6379]: finishConnect(..) failed: Connection refused: /172.31.46.124:6379` — content-service 두 파드와 chat-service에서 09:43:30.516 KST(=00:43:30Z)부터.
  - 트레이스 4건 `task battle-deadline-notification-scheduler.notify` (`code.function=notifyEnd`) 모두 `outcome=ERROR, exception=QueryTimeoutException, error="Redis command timed out"`. 발생 시각 00:44:00Z, 00:45:00Z, 00:46:00Z, 두 파드(10.42.1.43 / 10.42.3.42) 각각.
  - 스택트레이스가 원인을 특정한다: `net.javacrumbs.shedlock.provider.redis.spring.RedisLockProvider.lock(RedisLockProvider.java:109)` → `tryToSetExpiration` → `LettuceStringCommands.set` 에서 타임아웃. 즉 락 획득 단계에서 실패해 **작업 본체가 실행되지 않았다.**
  - 로그: `o.s.s.s.TaskUtils$LoggingErrorHandler - Unexpected error occurred in scheduled task` (00:44:02Z, 00:45:02Z, 양 파드).
  - 이 경로는 `BattleDeadlineNotificationScheduler` = **배틀 마감 알림**이다. 제보된 **댓글 알림**(`BATTLE_ITEM_COMMENT`)과는 별개 경로이며, 댓글 트레이스의 Redis `GET` span은 0.6ms 이내 정상 완료(`[user-cache] 캐시 HIT - userId: 1, elapsed: 2ms`)다.
- **확신도**: 높음(장애 사실 자체) / **댓글 알림 원인으로서는 낮음**
- **반증 데이터**: `up{job=redis}`는 조회 창 전 구간 1. Redis 익스포터는 정상 응답했는데 애플리케이션 연결은 거부됐다 — 익스포터가 별도 경로/로컬 접속이거나 Redis가 잠시 재기동했을 수 있고, 어느 쪽인지 가릴 데이터가 없다.
- **대기·지연 판정**
  - 실측 명령 대기 2005~2018ms vs 상한 2000ms(`Command timed out after 2 second(s)`) → **만료됨**.
  - 최종 상태: 해당 실행분 **실패**. 재시도는 스케줄 주기(60초)로 이루어져 00:44/00:45/00:46 세 차례 연속 실패했고, 00:46:00Z 이후 실패 트레이스가 없다. 마감 알림이 이후 주기에 재대상화되어 발송됐는지는 **판정 불가**(발송 성공 로그·메트릭 없음).

---

## 3. 권장 다음 조치

### 이미 발생한 피해: 복구 가능한가

**부분적으로 가능하다.**

- 복구 가능: 댓글 본문은 MySQL에 커밋 완료(`commit` 이벤트, `row-affected=1`, commentId 254·256). 알림만 유실됐으므로 **DB에서 재발행이 가능하다.** 방법:
  1. 먼저 후보 2를 해소(토픽을 RF=1로 명시 생성)한다. 안 하면 재발행도 똑같이 60초 타임아웃난다.
  2. `select ... from tb_battle_item_comment where created_at between` 브로커 다운 두 구간(`00:48:00Z~00:51:15Z`, `01:20:30Z~01:23:45Z`) 대상으로 조회 후 `user.notifications`에 재발행. 확인된 최소 대상은 commentId 254, 256 (둘 다 `userId=7`, `type=BATTLE_ITEM_COMMENT`).
  3. 재발행 시 소비자 측 멱등 처리가 없으면 중복 알림이 나간다 — 소비자 구현 데이터가 없으므로 재발행 전 확인 필요.
- 복구 불가: 마감 알림(후보 3)은 어떤 배틀이 대상이었는지 식별할 데이터(대상 batch 로그, 발송 성공 카운터)가 없다. **데이터 부족** — 스케줄러가 미발송 배틀을 다시 집는 구조인지 코드/로그 확인이 선행돼야 한다.

### 재발 방지

- **발행 실패 시 폐기하지 않기.** 현재 `NotificationEventListener`는 예외를 로그만 남기고 끝난다(`error=Send failed`). 트랜잭션과 같은 DB에 outbox 테이블로 적재하고 별도 릴레이가 재발행하도록 바꾸면 브로커 다운 시간만큼 지연될 뿐 유실되지 않는다. `user.notifications.dlq` 토픽과 `notification-recovery` 컨슈머그룹이 이미 있는데 이 실패 경로가 거기로 가지 않는다.
- **토픽 RF 정합성 고정.** 브로커 1대(`kafka_brokers=1`)인데 `INVALID_REPLICATION_FACTOR`가 났다. 토픽을 RF=1로 명시 생성하고 자동생성 의존을 끊는다.
- **`max.block.ms` 축소.** 60초 동안 `notification-*` 스레드가 점유된다. 브로커 다운이 길어지면 알림 스레드풀이 고갈된다. 5~10초로 줄이고 outbox 재시도에 맡긴다.
- **단일 장애점 분리.** Kafka(9092)와 Redis(6379)가 같은 호스트 `172.31.46.124`다. 오늘 창 안에서 Redis 단절(00:43~00:45Z)과 Kafka 다운(00:48~00:51Z)이 5분 간격으로 발생했다.
- **알람 추가.** `up{job=kafka} == 0`, `kafka_brokers` 결측, `Send failed` 로그율에 알람이 없어 사용자 제보로 발견됐다.

### 복구 확인

1. `up{job=kafka} == 1` 및 `kafka_brokers == 1`이 결측 없이 유지되는지.
2. 토픽 존재·RF 확인 후, `Error while fetching metadata ... {user.notifications=...}` 및 `Connection to node 1 ... Node may not be available` WARN이 양 파드에서 소멸하는지.
3. 테스트 댓글 1건 작성 → 트레이스에서 `publish user.notifications` span이 `STATUS_CODE_ERROR` 없이 수 ms 내 종료되는지 (현재는 60000ms 만료).
4. `kafka_consumergroup_lag{consumergroup="notification-processors", topic="user.notifications"}`가 재발행 직후 상승했다가 0으로 회수되는지 — 상승이 안 보이면 발행이 아직 안 되는 것이고, 0으로 안 내려오면 소비자 쪽 문제다.
5. 실제 단말 수신 확인은 **데이터 부족** — chat-service 측 알림 소비/FCM 발송 로그가 이번 수집에 없다.

---

**수집 공백 반영**: `http_server_requests_seconds_count{status="401"}` 결측은 이번 인증 무관 경로라 결론에 영향 없다. 다만 ① Kafka 브로커 자체 로그, ② 토픽 목록·RF 설정, ③ chat-service 알림 소비자 로그, ④ `01:23:39Z` 이후 상태가 모두 없어서 — 후보 2의 확신도를 중간으로, 후보 3의 최종 상태를 판정 불가로 낮췄다. ①~④를 추가 수집하면 후보 2를 확정하거나 기각할 수 있다.

---

## 관측 증거 (Evidence)

> 이 조사가 실제로 본 값이다. 원본 응답 전체는 `reports/raw/scan-1785803700-*.json`에 있다.

### 호출 그래프 (트레이스에서 추출)

```
content-service --db--> redis  2회  최대 0.6ms  [GET]
content-service --jdbc--> mysql/content (HikariPool-1)  18회  최대 52.3ms
    events: acquired, commit
content-service --messaging--> kafka/user.notifications  2회  최대 60013.4ms  [publish]
    error: Topic user.notifications not present in metadata after 60000 ms.
```

### span (duration 상위 15 / 전체 41)

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

