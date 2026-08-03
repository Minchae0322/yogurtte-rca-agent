# RCA Report — `scan-1785764169`

| 항목 | 값 |
|---|---|
| 모드 | rca |
| 질문 | 최근 1시간 안에 댓글 알림이 안 왔다는 제보가 있어요. 확인해줘 |
| 시각 | 2026-08-03T14:36:55.773920Z |
| provider | claude-cli |
| model | `claude-opus-5` · turns 1 |
| prompt | `./prompts/system-prompt.md` |
| tokens | in 211000 (cacheRead 18,133 · cacheCreate 192,865) / out 7504 · cost $2.2683 |
| elapsed | total 123456ms (tempo 7504 · loki 563 · mimir 674 · assemble 50 · llm 111155) |

## 탐색 (Triage)

| 항목 | 값 |
|---|---|
| 시간창 해석 | 상대 표현 '최근 1시간' |
| 시간창 확신도 | EXACT |
| 스윕 창 | 2026-08-03T13:36:09.088445Z ~ 2026-08-03T14:36:09.088445Z |
| 좁힌 창 | 2026-08-03T13:36:09.088445Z ~ 2026-08-03T14:36:09.088445Z |
| 대상 | chat-service |
| traceId | 6a70a49244908ca8f15be0b4d7a168b5 |
| 트레이스 후보 | 21건 |
| 장애 후보 | 13건 · 선택 INC-1, INC-8, INC-9, INC-10, INC-11, INC-12, INC-13 |
| 계획 파싱 | 성공 |
| 스윕 컨텍스트 | 후보 + 원본 (A) |
| prompt | `./prompts/triage-prompt.md` |
| tokens | in 56823 / out 2551 · cost $0.5150 |
| chars | 컨텍스트 62,821 + 프롬프트 1,399 = **64,220** |
| elapsed | survey 1634ms · llm 45010ms |

**선정 이유**: user.notifications partition 3 랙 누적과 DLQ 유입이 '알림 미도착' 증상 그 자체이고, 같은 14:21~14:36 구간의 MongoDB 단절·chat-service 30초 타임아웃·에러 로그 급증이 그 상류 지문으로 보여 한 장애의 여러 조각으로 함께 고른다.

**근거**

- kafka_consumergroup_lag{consumergroup=notification-processors, topic=user.notifications, partition=3} 이 창 내내 0이다가 14:21:09에 1, 14:36:09에 23으로 누적 — 알림 소비 정체의 직접 증거 (INC-1)
- kafka_consumergroup_lag{consumergroup=notification-recovery, topic=user.notifications.dlq, partition=0} 0→1→0 (14:26~14:31) — 알림 메시지가 DLQ로 떨어졌다는 뜻 (INC-1)
- mongodb_up 1→0, min_over_time(mongodb_up[5m])이 1785767169(14:26:09)·1785767469(14:31:09) 두 지점에서 0 — 이 시각 MongoDB 단절 (INC-9)
- chat-service ERROR/WARN 14:20~14:25 4건 → 14:25~14:30 68건으로 17배 급증, 창 내 다른 서비스 로그(auth 1·4건, content 3·1건)와 규모가 다름 (INC-8)
- chat-service 'security filterchain before' 스팬이 14:24:28~14:29:21 사이 30,006~30,016ms로 12건 이상 — status=unset(에러 아님), 30초 정각에 몰린 전형적 타임아웃 컷오프 (INC-11, INC-12)
- root span 미수신 트레이스 7건이 모두 chat-service 스팬만 담은 채 30,006~30,013ms — 요청이 chat-service 진입 단계에서 멈춰 트레이스가 완성되지 못함 (INC-10, INC-13)
- 에러 채널 트레이스 6a70a4cbf41848fcfa14ba00fe4a02f8: serviceStats chat-service spanCount 9 / errorCount 4, 개별 스팬이 30,010~30,029ms — 지연 끝에 실패로 전환된 흔적 (duration 필드는 깨져 있으나 스팬 시각은 14:25:15·14:25:46로 유효)
- kafka_brokers·up 은 전 구간 1로 정상 — 브로커/파드 프로세스 문제가 아니라 컨슈머 처리 정체 쪽

**스윕이 찾은 트레이스** (고른 것은 6a70a49244908ca8f15be0b4d7a168b5)

| traceId | 채널 | root service | root span | ms |
|---|---|---|---|---:|
| `6a70a4cbf41848fcfa14ba00fe4a02f8` | error ⚠값 신뢰 불가 | <root span not yet received> | (없음) | 3355842068 |
| `6a70a115f09975daa14ec1a090053942` | error | content-service | http get /feeds/scroll | 71 |
| `6a70a5b526a20b1535898d2637ce2995` | slow | chat-service | security filterchain before | 11357 |
| `6a70a5abc0d5f1588f1b6b7d7e86d363` | slow | chat-service | security filterchain before | 21395 |
| `6a70a5a1e47e40ec1bb966d1b0fa49f8` | slow | chat-service | security filterchain before | 30007 |
| `6a70a59720997b64b727f68e773fbbb2` | slow | chat-service | security filterchain before | 30007 |
| `6a70a58d12625740177c550a54e46fce` | slow | <root span not yet received> | (없음) | 30006 |
| `6a70a583d233ac4d7b73a024bc8f7ffa` | slow | <root span not yet received> | (없음) | 30007 |
| `6a70a5790d2c33b1cb66264b4b99bc53` | slow | <root span not yet received> | (없음) | 30007 |
| `6a70a56fc2ba6a1c584c673655c624ac` | slow | chat-service | security filterchain before | 30009 |
| `6a70a565442f4eb529704e40fdc8e267` | slow | chat-service | security filterchain before | 30006 |
| `6a70a4ed193a2b5a1f1bed00113d8b29` | slow | chat-service | security filterchain before | 30009 |
| `6a70a4e31505a83978ab808d971228ea` | slow | chat-service | security filterchain before | 30008 |
| `6a70a4d900ac17f8b3eed1dff5a1f7cd` | slow | chat-service | security filterchain before | 30006 |
| `6a70a4cf6953872624c277253c4aae4b` | slow | <root span not yet received> | (없음) | 30007 |
| `6a70a4c409d0baac34a37e5a651c761d` | slow | <root span not yet received> | (없음) | 30008 |
| `6a70a4bac0013ba673497f1f78b893f8` | slow | <root span not yet received> | (없음) | 30007 |
| `6a70a4b01759197099d4eaaad1247c81` | slow | chat-service | security filterchain before | 30007 |
| `6a70a4a6b2c7c98a2f264630917bf154` | slow | chat-service | security filterchain before | 30016 |
| `6a70a49ca06ccf017d0f0d4e3795675c` | slow | chat-service | security filterchain before | 30009 |
| `6a70a49244908ca8f15be0b4d7a168b5` ←선택 | slow | <root span not yet received> | (없음) | 30013 |

**장애 후보** (코드가 신호를 묶은 것 · 창은 여기서 계산됨)

## INC-1  kafka  |  kafka_consumergroup_lag
- 구간: 2026-08-03T13:36:09Z ~ 2026-08-03T14:36:09Z  (MIMIR · 집계 해상도만큼 흐림)
- kafka_consumergroup_lag{consumergroup=chat-service-fcm-tokens, partition=0, topic=user.fcm-tokens} 가 0이었다 (2026-08-03T13:36:09Z ~ 2026-08-03T14:36:09Z)
- kafka_consumergroup_lag{consumergroup=chat-service-fcm-tokens, partition=1, topic=user.fcm-tokens} 가 0이었다 (2026-08-03T13:36:09Z ~ 2026-08-03T14:36:09Z)
- kafka_consumergroup_lag{consumergroup=chat-service-fcm-tokens, partition=2, topic=user.fcm-tokens} 가 0이었다 (2026-08-03T13:36:09Z ~ 2026-08-03T14:36:09Z)
- kafka_consumergroup_lag{consumergroup=chat-service-notification-settings, partition=0, topic=user.notification-settings} 가 0이었다 (2026-08-03T13:36:09Z ~ 2026-08-03T14:36:09Z)
- kafka_consumergroup_lag{consumergroup=chat-service-notification-settings, partition=1, topic=user.notification-settings} 가 0이었다 (2026-08-03T13:36:09Z ~ 2026-08-03T14:36:09Z)
- kafka_consumergroup_lag{consumergroup=chat-service-notification-settings, partition=2, topic=user.notification-settings} 가 0이었다 (2026-08-03T13:36:09Z ~ 2026-08-03T14:36:09Z)
- kafka_consumergroup_lag{consumergroup=db-writer, partition=0, topic=chat.messages} 가 0이었다 (2026-08-03T13:36:09Z ~ 2026-08-03T14:36:09Z)
- kafka_consumergroup_lag{consumergroup=db-writer, partition=1, topic=chat.messages} 가 0이었다 (2026-08-03T13:36:09Z ~ 2026-08-03T14:36:09Z)
- kafka_consumergroup_lag{consumergroup=db-writer, partition=11, topic=chat.messages} 가 0이었다 (2026-08-03T13:36:09Z ~ 2026-08-03T14:36:09Z)
- kafka_consumergroup_lag{consumergroup=db-writer, partition=3, topic=chat.messages} 가 0이었다 (2026-08-03T13:36:09Z ~ 2026-08-03T14:36:09Z)
- kafka_consumergroup_lag{consumergroup=db-writer, partition=4, topic=chat.messages} 가 0이었다 (2026-08-03T13:36:09Z ~ 2026-08-03T14:36:09Z)
- kafka_consumergroup_lag{consumergroup=db-writer, partition=5, topic=chat.messages} 가 0이었다 (2026-08-03T13:36:09Z ~ 2026-08-03T14:36:09Z)
- kafka_consumergroup_lag{consumergroup=db-writer, partition=7, topic=chat.messages} 가 0이었다 (2026-08-03T13:36:09Z ~ 2026-08-03T14:36:09Z)
- kafka_consumergroup_lag{consumergroup=db-writer, partition=8, topic=chat.messages} 가 0이었다 (2026-08-03T13:36:09Z ~ 2026-08-03T14:36:09Z)
- kafka_consumergroup_lag{consumergroup=db-writer-retry-1000, partition=0, topic=chat.messages-retry-1000} 가 0이었다 (2026-08-03T13:36:09Z ~ 2026-08-03T14:36:09Z)
- kafka_consumergroup_lag{consumergroup=db-writer-retry-2000, partition=0, topic=chat.messages-retry-2000} 가 0이었다 (2026-08-03T13:36:09Z ~ 2026-08-03T14:36:09Z)
- kafka_consumergroup_lag{consumergroup=db-writer-retry-4000, partition=0, topic=chat.messages-retry-4000} 가 0이었다 (2026-08-03T13:36:09Z ~ 2026-08-03T14:36:09Z)
- kafka_consumergroup_lag{consumergroup=notification, partition=0, topic=chat.messages} 가 0이었다 (2026-08-03T13:36:09Z ~ 2026-08-03T14:36:09Z)
- kafka_consumergroup_lag{consumergroup=notification, partition=1, topic=chat.messages} 가 0이었다 (2026-08-03T13:36:09Z ~ 2026-08-03T14:36:09Z)
- kafka_consumergroup_lag{consumergroup=notification, partition=11, topic=chat.messages} 가 0이었다 (2026-08-03T13:36:09Z ~ 2026-08-03T14:36:09Z)
- kafka_consumergroup_lag{consumergroup=notification, partition=2, topic=chat.messages} 가 0이었다 (2026-08-03T13:36:09Z ~ 2026-08-03T14:36:09Z)
- kafka_consumergroup_lag{consumergroup=notification, partition=3, topic=chat.messages} 가 0이었다 (2026-08-03T13:36:09Z ~ 2026-08-03T14:36:09Z)
- kafka_consumergroup_lag{consumergroup=notification, partition=4, topic=chat.messages} 가 0이었다 (2026-08-03T13:36:09Z ~ 2026-08-03T14:36:09Z)
- kafka_consumergroup_lag{consumergroup=notification, partition=5, topic=chat.messages} 가 0이었다 (2026-08-03T13:36:09Z ~ 2026-08-03T14:36:09Z)
- kafka_consumergroup_lag{consumergroup=notification, partition=7, topic=chat.messages} 가 0이었다 (2026-08-03T13:36:09Z ~ 2026-08-03T14:36:09Z)
- kafka_consumergroup_lag{consumergroup=notification, partition=8, topic=chat.messages} 가 0이었다 (2026-08-03T13:36:09Z ~ 2026-08-03T14:36:09Z)
- kafka_consumergroup_lag{consumergroup=notification-processors, partition=0, topic=user.notifications} 가 0이었다 (2026-08-03T13:36:09Z ~ 2026-08-03T14:36:09Z)
- kafka_consumergroup_lag{consumergroup=notification-processors, partition=1, topic=user.notifications} 가 0이었다 (2026-08-03T13:36:09Z ~ 2026-08-03T14:36:09Z)
- kafka_consumergroup_lag{consumergroup=notification-processors, partition=2, topic=user.notifications} 가 0이었다 (2026-08-03T13:36:09Z ~ 2026-08-03T14:36:09Z)
- kafka_consumergroup_lag{consumergroup=notification-processors, partition=3, topic=user.notifications} 가 0이었다 (2026-08-03T13:36:09Z ~ 2026-08-03T14:21:09Z)
- kafka_consumergroup_lag{consumergroup=notification-processors, partition=4, topic=user.notifications} 가 0이었다 (2026-08-03T13:36:09Z ~ 2026-08-03T14:36:09Z)
- kafka_consumergroup_lag{consumergroup=notification-processors, partition=5, topic=user.notifications} 가 0이었다 (2026-08-03T13:36:09Z ~ 2026-08-03T14:36:09Z)
- kafka_consumergroup_lag{consumergroup=notification-recovery, partition=0, topic=user.notifications.dlq} 가 0이었다 (2026-08-03T13:36:09Z ~ 2026-08-03T14:26:09Z)
- kafka_consumergroup_lag{consumergroup=notification-recovery, partition=2, topic=user.notifications.dlq} 가 0이었다 (2026-08-03T13:36:09Z ~ 2026-08-03T14:36:09Z)
- kafka_consumergroup_lag{consumergroup=notification-retry-2000, partition=0, topic=chat.messages-retry-2000} 가 0이었다 (2026-08-03T13:36:09Z ~ 2026-08-03T14:36:09Z)
- kafka_consumergroup_lag{consumergroup=notification-retry-4000, partition=0, topic=chat.messages-retry-4000} 가 0이었다 (2026-08-03T13:36:09Z ~ 2026-08-03T14:36:09Z)
- kafka_consumergroup_lag{consumergroup=notification-processors, partition=3, topic=user.notifications} 0 → 1
- kafka_consumergroup_lag{consumergroup=notification-recovery, partition=0, topic=user.notifications.dlq} 0 → 1
- kafka_consumergroup_lag{consumergroup=notification-processors, partition=3, topic=user.notifications} 1 → 23
- kafka_consumergroup_lag{consumergroup=notification-recovery, partition=0, topic=user.notifications.dlq} 1 → 0
- kafka_consumergroup_lag{consumergroup=notification-recovery, partition=0, topic=user.notifications.dlq} 가 0이었다 (2026-08-03T14:36:09Z ~ 2026-08-03T14:36:09Z)
- 같은 시각의 다른 후보: INC-2, INC-3, INC-4, INC-5, INC-6, INC-7, INC-8, INC-9, INC-10, INC-11, INC-12, INC-13  (인과 여부는 판단하지 않았다)

## INC-2  chat-service  |  websocket_active_users
- 구간: 2026-08-03T13:36:09Z ~ 2026-08-03T14:36:09Z  (MIMIR · 집계 해상도만큼 흐림)
- websocket_active_users{container=chat-service, namespace=default, pod=chat-service-fdcc7c776-qrbc2} 가 0이었다 (2026-08-03T13:36:09Z ~ 2026-08-03T14:36:09Z)
- 같은 시각의 다른 후보: INC-1, INC-3, INC-4, INC-5, INC-6, INC-7, INC-8, INC-9, INC-10, INC-11, INC-12, INC-13  (인과 여부는 판단하지 않았다)

## INC-3  auth-service  |  ERROR/WARN
- 구간: 2026-08-03T13:45:00Z ~ 2026-08-03T13:50:00Z  (LOKI · 집계 해상도만큼 흐림)
- ERROR/WARN 1건 (2026-08-03T13:45:00Z ~ 2026-08-03T13:50:00Z)
- 같은 시각의 다른 후보: INC-1, INC-2, INC-4  (인과 여부는 판단하지 않았다)

## INC-4  content-service  |  ERROR/WARN
- 구간: 2026-08-03T13:45:00Z ~ 2026-08-03T13:50:00Z  (LOKI · 집계 해상도만큼 흐림)
- ERROR/WARN 3건 (2026-08-03T13:45:00Z ~ 2026-08-03T13:50:00Z)
- 같은 시각의 다른 후보: INC-1, INC-2, INC-3  (인과 여부는 판단하지 않았다)

## INC-5  content-service  |  ERROR/WARN
- 구간: 2026-08-03T14:05:00Z ~ 2026-08-03T14:10:00Z  (LOKI · 집계 해상도만큼 흐림)
- ERROR/WARN 1건 (2026-08-03T14:05:00Z ~ 2026-08-03T14:10:00Z)
- 같은 시각의 다른 후보: INC-1, INC-2, INC-6, INC-7  (인과 여부는 판단하지 않았다)

## INC-6  content-service  |  http get /feeds/scroll
- 구간: 2026-08-03T14:09:25.771400Z ~ 2026-08-03T14:09:25.842400Z  (TEMPO · 시각 정확)
- content-service http get /feeds/scroll 71ms (error 채널)
- traceId: 6a70a115f09975daa14ec1a090053942
- 같은 시각의 다른 후보: INC-1, INC-2, INC-5  (인과 여부는 판단하지 않았다)

## INC-7  auth-service  |  ERROR/WARN
- 구간: 2026-08-03T14:10:00Z ~ 2026-08-03T14:15:00Z  (LOKI · 집계 해상도만큼 흐림)
- ERROR/WARN 4건 (2026-08-03T14:10:00Z ~ 2026-08-03T14:15:00Z)
- 같은 시각의 다른 후보: INC-1, INC-2, INC-5  (인과 여부는 판단하지 않았다)

## INC-8  chat-service  |  ERROR/WARN
- 구간: 2026-08-03T14:20:00Z ~ 2026-08-03T14:30:00Z  (LOKI · 집계 해상도만큼 흐림)
- ERROR/WARN 4건 (2026-08-03T14:20:00Z ~ 2026-08-03T14:25:00Z)
- ERROR/WARN 68건 (2026-08-03T14:25:00Z ~ 2026-08-03T14:30:00Z)
- 같은 시각의 다른 후보: INC-1, INC-2, INC-9, INC-10, INC-11, INC-12, INC-13  (인과 여부는 판단하지 않았다)

## INC-9  mongodb  |  mongodb_up
- 구간: 2026-08-03T14:21:09Z ~ 2026-08-03T14:36:09Z  (MIMIR · 집계 해상도만큼 흐림)
- mongodb_up 1 → 0
- mongodb_up 가 0이었다 (2026-08-03T14:26:09Z ~ 2026-08-03T14:31:09Z)
- mongodb_up 0 → 1
- 같은 시각의 다른 후보: INC-1, INC-2, INC-8, INC-10, INC-11, INC-12, INC-13  (인과 여부는 판단하지 않았다)

## INC-10  <root span not yet received>
- 구간: 2026-08-03T14:24:18.746875Z ~ 2026-08-03T14:25:49.017226Z  (TEMPO · 시각 정확)
- <root span not yet received>  30,013ms (slow 채널)
- <root span not yet received>  30,007ms (slow 채널)
- <root span not yet received>  30,008ms (slow 채널)
- <root span not yet received>  30,007ms (slow 채널)
- traceId: 6a70a49244908ca8f15be0b4d7a168b5, 6a70a4bac0013ba673497f1f78b893f8, 6a70a4c409d0baac34a37e5a651c761d, 6a70a4cf6953872624c277253c4aae4b
- 같은 시각의 다른 후보: INC-1, INC-2, INC-8, INC-9, INC-11  (인과 여부는 판단하지 않았다)

## INC-11  chat-service  |  security filterchain before
- 구간: 2026-08-03T14:24:28.800690Z ~ 2026-08-03T14:26:19.143645Z  (TEMPO · 시각 정확)
- chat-service security filterchain before 30,009ms (slow 채널)
- chat-service security filterchain before 30,016ms (slow 채널)
- chat-service security filterchain before 30,007ms (slow 채널)
- chat-service security filterchain before 30,006ms (slow 채널)
- chat-service security filterchain before 30,008ms (slow 채널)
- chat-service security filterchain before 30,009ms (slow 채널)
- traceId: 6a70a49ca06ccf017d0f0d4e3795675c, 6a70a4a6b2c7c98a2f264630917bf154, 6a70a4b01759197099d4eaaad1247c81, 6a70a4d900ac17f8b3eed1dff5a1f7cd, 6a70a4e31505a83978ab808d971228ea, 6a70a4ed193a2b5a1f1bed00113d8b29
- 같은 시각의 다른 후보: INC-1, INC-2, INC-8, INC-9, INC-10  (인과 여부는 판단하지 않았다)

## INC-12  chat-service  |  security filterchain before
- 구간: 2026-08-03T14:27:49.633343Z ~ 2026-08-03T14:29:21.322499Z  (TEMPO · 시각 정확)
- chat-service security filterchain before 30,006ms (slow 채널)
- chat-service security filterchain before 30,009ms (slow 채널)
- chat-service security filterchain before 30,007ms (slow 채널)
- chat-service security filterchain before 30,007ms (slow 채널)
- chat-service security filterchain before 21,395ms (slow 채널)
- chat-service security filterchain before 11,357ms (slow 채널)
- traceId: 6a70a565442f4eb529704e40fdc8e267, 6a70a56fc2ba6a1c584c673655c624ac, 6a70a59720997b64b727f68e773fbbb2, 6a70a5a1e47e40ec1bb966d1b0fa49f8, 6a70a5abc0d5f1588f1b6b7d7e86d363, 6a70a5b526a20b1535898d2637ce2995
- 같은 시각의 다른 후보: INC-1, INC-2, INC-8, INC-9, INC-13  (인과 여부는 판단하지 않았다)

## INC-13  <root span not yet received>
- 구간: 2026-08-03T14:28:09.716728Z ~ 2026-08-03T14:28:59.805885Z  (TEMPO · 시각 정확)
- <root span not yet received>  30,007ms (slow 채널)
- <root span not yet received>  30,007ms (slow 채널)
- <root span not yet received>  30,006ms (slow 채널)
- traceId: 6a70a5790d2c33b1cb66264b4b99bc53, 6a70a583d233ac4d7b73a024bc8f7ffa, 6a70a58d12625740177c550a54e46fce
- 같은 시각의 다른 후보: INC-1, INC-2, INC-8, INC-9, INC-12  (인과 여부는 판단하지 않았다)

**기각한 후보**

- INC-2 — websocket_active_users가 13:36부터 14:36까지 전 구간 0으로 변화가 전혀 없어 이번 시각에 발생한 이상이 아니라 상시 baseline으로 보인다(다만 실제 0이 정상값인지는 별도 확인 필요).
- INC-3 — auth-service ERROR/WARN 1건, 13:45~13:50으로 증상 시각(14:21 이후)보다 35분 이르고 알림 경로와 무관하다.
- INC-4 — content-service ERROR/WARN 3건, 13:45~13:50으로 증상 시각보다 이르고 피드 경로라 댓글 알림 파이프라인과 접점이 없다.
- INC-5 — content-service ERROR/WARN 1건, 14:05~14:10 단발이며 랙 상승 시작(14:21)보다 앞서고 서비스도 다르다.
- INC-6 — content-service /feeds/scroll 71ms 에러 1건으로 지연도 규모도 미미하고 알림 발행 경로가 아니다.
- INC-7 — auth-service ERROR/WARN 4건, 14:10~14:15로 랙 상승 이전이고 chat-service 타임아웃 구간(14:24~)과도 시각이 어긋난다.

**보정 기록**

- 창을 후보 [INC-1, INC-8, INC-9, INC-10, INC-11, INC-12, INC-13] 의 신호 시각에서 계산했다 (2026-08-03T13:36:09.088445Z ~ 2026-08-03T14:36:09.088445Z)

## 수집 범위 (Coverage)

- **window**: 2026-08-03T13:36:09.088445Z ~ 2026-08-03T14:36:09.088445Z (3600s)
- **trace**: 98,114B / 104 spans
- **창 안 후보 트레이스**: 20건 / 98,114B
- **logs**: errwarn=216,553B · traceId=33,232B
- **metrics**: 8 수집 / 368,010B, 누락 [sum(rate(http_server_requests_seconds_count{application="content-service", status="401"}[1m]))]
- **context**: 363,268 chars (+ 시스템 프롬프트 576 chars)

### 토큰 축 (개선 지표)

| 단계 | 총 in | chars | 컨텍스트 토큰 |
|---|---:|---:|---:|
| 탐색 | 56,823 | 64,220 | 35,194 |
| 분석 | 211,000 | 363,844 | 189,371 |
| **합계** | **267,823** | | **224,565** |

- **overheadTokens 21,629 tok — 이 회차에 실측**(1자 프롬프트 프로브, 같은 명령·모델·샌드박스).
  호출마다 한 번씩 얹히므로 단계별로 뺀다. **다른 날 상수를 쓰지 않았다 = `█ 실측`.**
- contextTokens (count_tokens API): 측정 안 됨 — 구독 CLI 경로엔 API 키가 없다
- 비용은 **API 환산 추정치**다. 구독 계정으로 호출하므로 실제 청구액이 아니다.

## 수집 실패/누락

- Metric 'sum(rate(http_server_requests_seconds_count{application="content-service", status="401"}[1m]))' returned no series in this window; skipped.

---

주어진 관측 데이터만으로 정리했습니다. 결론부터: **댓글 알림이 안 온 것은 chat-service가 알림을 저장하는 MongoDB(172.31.46.124:27017)가 14:24~14:29 UTC 동안 다운되면서 `user.notifications` 컨슈머가 저장에 실패 → 3회 재시도 후 DLQ로 빠졌기 때문**으로 보입니다.

## 1. 원인 후보 랭킹 (최대 3개)

1. **MongoDB(172.31.46.124:27017) 셧다운/거부로 chat-service의 알림 저장(save) 실패 → 재시도 3회 소진 후 DLQ 유입** (직접 원인)
2. **MongoDB 복구 후에도 `user.notifications` 파티션 3 컨슈머가 정지 — 랩이 조회 종료 시점까지 23으로 증가** (미전달이 계속되는 원인)
3. **content-service → auth-service 호출 거부(auth-service 파드 교체 구간)** — 댓글 알림 경로와는 무관해 보이는 별개 장애

## 2. 후보별 근거

### 후보 1. MongoDB 다운 → 알림 저장 실패 → DLQ

- **근거**
  - 시작점: `com.mongodb.MongoNodeIsRecoveringException: Command failed with error 11600 (InterruptedAtShutdown): 'interrupted at shutdown' on server 172.31.46.124:27017` (2026-08-03T23:24:14.376+09:00 = 14:24:14Z). 이후 전부 `AnnotatedConnectException: Connection refused: /172.31.46.124:27017` 으로 전환 — 셧다운 후 프로세스 부재.
  - 메트릭 `mongodb_up{instance=infra-server}`: **0이던 구간 14:24:39Z ~ 14:29:24Z** (그 외 구간 1).
  - 댓글 알림 실패 로그 원문(3회):
    `[kafka] 알림 처리 실패: userId=7, type=BATTLE_ITEM_COMMENT` — 23:25:45.833 / 23:26:16.960 / 23:26:47.978 (KST), 로거 `c.e.t.a.k.u.UserNotificationConsumer`.
  - 실패 원인과 위치가 스택에 명시: `UserNotificationService.saveNotification(UserNotificationService.java:82)` → `SimpleMongoRepository.save` → `MongoTemplate.insertDocument` → `com.mongodb.MongoTimeoutException: Timed out while waiting for a server that matches WritableServerSelector ... Connection refused: /172.31.46.124:27017`.
  - 재시도 소진: `[config] user-notification 처리 실패 1회차/2회차/3회차: topic=user.notifications partition=3 offset=1045 cause=com.mongodb.MongoTimeoutException...` (KafkaConsumerConfig).
  - DLQ 유입 정황: `kafka_consumergroup_lag{consumergroup=notification-recovery, topic=user.notifications.dlq, partition=0}` 이 **14:27:39Z ~ 14:30:24Z 구간에만 1**, 그 외 0.
  - 인프라 측 부수 증상: `MongoReactiveHealthIndicator: Mongo health check failed` 및 `(mongo) took 30001ms to respond` 가 10초 주기로 반복. Tempo 트레이스의 `secured request` span 이 전부 **정확히 30.00~30.01초**(예: traceId `6a70a4bac0013ba673497f1f78b893f8` 는 1785767098926448000→1785767128933945000 = 30.007s)로 헬스체크가 Mongo 서버 셀렉션 타임아웃 30초를 그대로 먹고 있음.
  - 복구 시점: 마지막 두 건이 `took 21389ms`, `took 11349ms` (23:29:21 KST = 14:29:21Z)로 짧아짐 → `mongodb_up` 0 구간 종료(14:29:24Z)와 일치.
- **확신도**: 높음
- **반증 데이터**:
  - Kafka 자체는 정상 — `up{job=kafka}=1`, `kafka_brokers=1` 전 구간 상수. 즉 "알림이 안 온" 원인이 메시지 발행/브로커 쪽은 아님.
  - Mongo는 14:29:24Z에 복구되었는데도 제보는 "최근 1시간" 이므로, 이 후보만으로는 복구 이후 구간이 설명되지 않음 → 후보 2 필요.
  - 실패가 확인된 건 `userId=7, offset=1045` **단 1건**뿐. 다수 사용자 미수신인지는 이 데이터로 확인 불가(데이터 부족).

### 후보 2. 복구 후 `user.notifications` 파티션 3 컨슈머 정지

- **근거**
  - `kafka_consumergroup_lag{consumergroup=notification-processors, topic=user.notifications, partition=3}`: 13:36:09Z~14:25:24Z 0 → 장애 중 상승 → 14:27:39Z~14:32:24Z 다시 0 → **마지막 값 23 (14:36:09Z), max 23**. 다른 파티션(0,1,2,4,5)은 전 구간 0.
  - 같은 시각 chat-service 메트릭이 **전부 14:32:39Z에서 끊김**: `up{container=chat-service, instance=10.42.3.43:8090}` 207점 · 13:36:09Z~**14:32:39Z**(값은 1), `hikaricp_connections_active/pending`, `websocket_active_users` 모두 동일하게 14:32:39Z까지만 존재. content-service·auth-service는 14:36:09Z까지 정상 수집됨.
  - 그 직전에도 chat-service만 **결측 구간 14:24:39Z ~ 14:29:54Z** 가 있음(Mongo 타임아웃으로 액추에이터 응답이 30초 지연된 구간과 일치).
  - 즉 "Mongo 복구 → 랩 0 → 14:32:39Z 이후 스크레이프 소실 → 랩 23까지 증가"라는 순서. 복구 이후에 발생한 댓글 알림은 아직 소비되지 않은 상태.
- **확신도**: 중간 (현상은 메트릭으로 확실하나, 파드 재시작·OOM·프로브 실패 등 **정지 사유를 뒷받침할 이벤트/파드 상태 데이터가 없음**)
- **반증 데이터**: 14:32:39Z 이후 chat-service 로그가 수집 범위에 없어, 컨슈머가 죽었는지 단순히 메트릭 엔드포인트만 못 긁힌 것인지 구분 불가. `up`이 0으로 기록된 게 아니라 **결측**이라는 점도 타깃 소실(파드 교체)과 스크레이프 타임아웃 양쪽 해석을 허용함.

### 후보 3. content-service → auth-service 호출 거부

- **근거**
  - 호출 그래프: `content-service --service--> auth-service  1회  최대 22.6ms  error: WebClientRequestException / finishConnect(..) failed: Connection refused: auth-service.default.svc.cluster.local/10.43.13.21:8081`.
  - 트레이스 `6a70a115f09975daa14ec1a090053942`(14:09:25Z, `http get /feeds/scroll`) 내 `http get` span: `http.url=http://auth-service:8081/api/external/users?userIds=1,3,7,9`, `status=CLIENT_ERROR`, `STATUS_CODE_ERROR`. 다만 루트 span 은 `status=200`, `outcome=SUCCESS`로 종료.
  - 파드 교체 정황: `auth-service-5999bb9f5c-lbpf2` 메트릭이 13:59:54Z에 끝나고 `auth-service-5999bb9f5c-hmgp9` 가 14:11:39Z부터 시작 → 그 사이 공백에 호출 거부 발생.
- **확신도**: 낮음
- **반증 데이터**:
  - 호출 그래프 어디에도 **chat-service → auth-service 엣지가 없음**. 알림 소비 경로(Kafka → chat-service → Mongo)와 연결되지 않음.
  - 발생 1회, 14:09:25Z 단발이며 알림 실패 시각(14:25~14:26Z)과 어긋남.
  - 수집 실패로 `content-service` 401 메트릭이 없어 인증 실패 확산 여부는 **데이터 부족**.

### 참고로 판단 불가한 항목 (데이터 부족)

- **최종 사용자 전달 단계**: 저장 이후 FCM/푸시 발송이나 WebSocket 푸시에 해당하는 로그·스팬·메트릭이 하나도 없음. `websocket_active_users` 는 **전 구간 0** 이라 장애 전후 비교가 불가능(장애 전에도 0이므로 이번 장애의 근거로 쓸 수 없음).
- **DLQ 처리 결과**: `notification-recovery` 랩이 1→0으로 돌아온 것만 보이고, 재처리 성공/사용자 전달 여부를 확인할 로그가 없음.
- **영향 범위**: 실패가 확인된 사용자는 `userId=7` 1명뿐. 제보자와 동일인인지, 그 외 몇 건인지 판단 불가.
- 수집 실패한 `content-service status=401` 시리즈 공백을 감안해 후보 3의 확신도를 낮춤.

## 3. 권장 다음 조치

1. **MongoDB 상태·재시작 원인 확인**: `172.31.46.124:27017` 의 mongod 로그에서 14:24:14Z 전후 shutdown 사유(수동 재시작/OOM/디스크) 확인. `InterruptedAtShutdown` 직후 `Connection refused` 로 바뀐 구간(약 5분)의 프로세스 상태 확보.
2. **chat-service 파드 상태 확인 (최우선, 현재진행형 영향)**: `kubectl get pod chat-service-fdcc7c776-qrbc2 -o wide`, `kubectl describe pod`(재시작 횟수·OOMKilled·liveness 실패), `kubectl logs --previous`. 14:32:39Z 이후 메트릭 결측과 파티션 3 랩 23의 원인을 특정.
3. **랩 해소 확인**: `kafka-consumer-groups --describe --group notification-processors` 로 `user.notifications` partition 3 의 current-offset/lag 이 줄고 있는지 확인. 줄지 않으면 컨슈머 재기동.
4. **누락 알림 복구**: `user.notifications.dlq` 의 offset 1045 (userId=7, BATTLE_ITEM_COMMENT) 메시지가 `notification-recovery` 로 재처리되어 실제 저장·전달됐는지 Mongo 알림 컬렉션에서 직접 조회. 미저장이면 DLQ 재처리 트리거.
5. **재발 방지(설정)**: Mongo 헬스체크가 30초를 그대로 소모해 액추에이터·스크레이프까지 막았음. `spring.data.mongodb.uri` 의 `serverSelectionTimeoutMS`/`connectTimeoutMS` 를 프로브 타임아웃보다 짧게, liveness/readiness 프로브가 Mongo 의존 헬스에 묶이지 않도록 분리 검토.
6. **auth-service 건은 별건으로 분리 추적**: 13:59:54Z~14:11:39Z 파드 교체 중 무중단 여부(readiness/PDB) 확인. 필요 시 `http_server_requests{application="content-service", status="401"}` 메트릭이 왜 수집되지 않는지도 함께 점검(이번 조사에서 수집 실패).

---

## 관측 증거 (Evidence)

> 이 조사가 실제로 본 값이다. 원본 응답 전체는 `reports/raw/scan-1785764169-*.json`에 있다.

### 호출 그래프 (트레이스에서 추출)

```
content-service --db--> redis  4회  최대 0.5ms  [GET]
chat-service --jdbc--> mysql/content (HikariPool-1)  38회  최대 3.2ms
    events: acquired
content-service --jdbc--> mysql/content (HikariPool-1)  19회  최대 68.3ms
    events: acquired, commit
content-service --service--> auth-service  1회  최대 22.6ms
    error: WebClientRequestException
    error: finishConnect(..) failed: Connection refused: auth-service.default.svc.cluster.local/10.43.13.21:8081
```

### span (duration 상위 15 / 전체 104)

| ms | service | span | 시작 |
|---:|---|---|---|
| 30016.13 | chat-service | `secured request` | 2026-08-03T14:24:38.843031Z |
| 30013.33 | chat-service | `secured request` | 2026-08-03T14:24:18.746875Z |
| 30009.28 | chat-service | `secured request` | 2026-08-03T14:25:49.134897Z |
| 30009.12 | chat-service | `secured request` | 2026-08-03T14:27:59.674928Z |
| 30008.75 | chat-service | `secured request` | 2026-08-03T14:24:28.800987Z |
| 30008.35 | chat-service | `secured request` | 2026-08-03T14:25:08.967957Z |
| 30007.95 | chat-service | `secured request` | 2026-08-03T14:25:39.093661Z |
| 30007.80 | chat-service | `secured request` | 2026-08-03T14:25:19.010226Z |
| 30007.50 | chat-service | `secured request` | 2026-08-03T14:24:58.926448Z |
| 30007.37 | chat-service | `secured request` | 2026-08-03T14:28:19.758791Z |
| 30007.29 | chat-service | `secured request` | 2026-08-03T14:24:48.885186Z |
| 30007.15 | chat-service | `secured request` | 2026-08-03T14:28:39.841176Z |
| 30007.10 | chat-service | `secured request` | 2026-08-03T14:28:09.716728Z |
| 30007.09 | chat-service | `secured request` | 2026-08-03T14:28:49.882648Z |
| 30006.73 | chat-service | `secured request` | 2026-08-03T14:28:29.799885Z |

### 로그 원문 (60 / 전체 1,038줄)

전체가 상한을 넘어 ERROR/WARN 줄을 우선 발췌했다. 나머지는 원본 파일에 있다.

```
2026-08-03T14:24:14.410532179Z  [chat-service]  [2m2026-08-03T23:24:14.376+09:00[0;39m [32m INFO [traceId=NONE,spanId=NONE,userId=NONE][0;39m [35m7[0;39m [2m--- [chat-service] [31.46.124:27017] [                                                 ] [0;39m[36morg.mongodb.driver.cluster              [0;39m [2m:[0;39m Exception in monitor thread while connecting to server 172.31.46.124:27017
2026-08-03T14:24:14.410564521Z  [chat-service]  com.mongodb.MongoNodeIsRecoveringException: Command failed with error 11600 (InterruptedAtShutdown): 'interrupted at shutdown' on server 172.31.46.124:27017. The full response is {"ok": 0.0, "errmsg": "interrupted at shutdown", "code": 11600, "codeName": "InterruptedAtShutdown"}
2026-08-03T14:24:14.410569149Z  [chat-service]  at com.mongodb.internal.connection.ProtocolHelper.createSpecialException(ProtocolHelper.java:264) ~[mongodb-driver-core-5.5.1.jar!/:na]
2026-08-03T14:24:14.410571954Z  [chat-service]  at com.mongodb.internal.connection.ProtocolHelper.getCommandFailureException(ProtocolHelper.java:206) ~[mongodb-driver-core-5.5.1.jar!/:na]
2026-08-03T14:24:14.411138659Z  [chat-service]  [2m2026-08-03T23:24:14.375+09:00[0;39m [32m INFO [traceId=NONE,spanId=NONE,userId=NONE][0;39m [35m7[0;39m [2m--- [chat-service] [31.46.124:27017] [                                                 ] [0;39m[36morg.mongodb.driver.cluster              [0;39m [2m:[0;39m Exception in monitor thread while connecting to server 172.31.46.124:27017
2026-08-03T14:24:14.411150943Z  [chat-service]  com.mongodb.MongoNodeIsRecoveringException: Command failed with error 11600 (InterruptedAtShutdown): 'interrupted at shutdown' on server 172.31.46.124:27017. The full response is {"ok": 0.0, "errmsg": "interrupted at shutdown", "code": 11600, "codeName": "InterruptedAtShutdown"}
2026-08-03T14:24:14.411153880Z  [chat-service]  at com.mongodb.internal.connection.ProtocolHelper.createSpecialException(ProtocolHelper.java:264) ~[mongodb-driver-core-5.5.1.jar!/:na]
2026-08-03T14:24:14.411156388Z  [chat-service]  at com.mongodb.internal.connection.ProtocolHelper.getCommandFailureException(ProtocolHelper.java:206) ~[mongodb-driver-core-5.5.1.jar!/:na]
2026-08-03T14:24:18.760578677Z  [chat-service]  [2m2026-08-03T23:24:18.760+09:00[0;39m [32m INFO [traceId=6a70a49244908ca8f15be0b4d7a168b5,spanId=3f466452dbb6fed6,userId=NONE][0;39m [35m7[0;39m [2m--- [chat-service] [nio-8090-exec-7] [6a70a49244908ca8f15be0b4d7a168b5-3f466452dbb6fed6] [0;39m[36morg.mongodb.driver.cluster              [0;39m [2m:[0;39m Waiting for server to become available for operation with ID 43999. Remaining time: 29992 ms. Selector: ReadPreferenceServerSelector{readPreference=primary}, topology description: {type=UNKNOWN, servers=[{address=172.31.46.124:27017, type=UNKNOWN, state=CONNECTING, exception={com.mongodb.MongoNodeIsRecoveringException: Command failed with error 11600 (InterruptedAtShutdown): 'interrupted at shutdown' on server 172.31.46.124:27017. The full response is {"ok": 0.0, "errmsg": "interrupted at shutdown", "code": 11600, "codeName": "InterruptedAtShutdown"}}}].
2026-08-03T14:24:18.807356522Z  [chat-service]  [2m2026-08-03T23:24:18.784+09:00[0;39m [32m INFO [traceId=NONE,spanId=NONE,userId=NONE][0;39m [35m7[0;39m [2m--- [chat-service] [31.46.124:27017] [                                                 ] [0;39m[36morg.mongodb.driver.cluster              [0;39m [2m:[0;39m Exception in monitor thread while connecting to server 172.31.46.124:27017
2026-08-03T14:24:18.807405527Z  [chat-service]  com.mongodb.MongoSocketOpenException: Exception opening socket
2026-08-03T14:24:18.807472669Z  [chat-service]  Caused by: io.netty.channel.AbstractChannel$AnnotatedConnectException: Connection refused: /172.31.46.124:27017
2026-08-03T14:24:18.807474455Z  [chat-service]  Caused by: java.net.ConnectException: Connection refused
2026-08-03T14:24:24.416471475Z  [chat-service]  [2m2026-08-03T23:24:24.415+09:00[0;39m [32m INFO [traceId=NONE,spanId=NONE,userId=NONE][0;39m [35m7[0;39m [2m--- [chat-service] [31.46.124:27017] [                                                 ] [0;39m[36morg.mongodb.driver.cluster              [0;39m [2m:[0;39m Exception in monitor thread while connecting to server 172.31.46.124:27017
2026-08-03T14:24:24.416507090Z  [chat-service]  com.mongodb.MongoSocketOpenException: Exception opening socket
2026-08-03T14:24:24.416585863Z  [chat-service]  Caused by: io.netty.channel.AbstractChannel$AnnotatedConnectException: Connection refused: /172.31.46.124:27017
2026-08-03T14:24:24.416587643Z  [chat-service]  Caused by: java.net.ConnectException: Connection refused
2026-08-03T14:24:28.805788349Z  [chat-service]  [2m2026-08-03T23:24:28.805+09:00[0;39m [32m INFO [traceId=6a70a49ca06ccf017d0f0d4e3795675c,spanId=12ba7af26a7286c7,userId=NONE][0;39m [35m7[0;39m [2m--- [chat-service] [nio-8090-exec-8] [6a70a49ca06ccf017d0f0d4e3795675c-12ba7af26a7286c7] [0;39m[36morg.mongodb.driver.cluster              [0;39m [2m:[0;39m Waiting for server to become available for operation with ID 44023. Remaining time: 29999 ms. Selector: ReadPreferenceServerSelector{readPreference=primary}, topology description: {type=UNKNOWN, servers=[{address=172.31.46.124:27017, type=UNKNOWN, state=CONNECTING, exception={com.mongodb.MongoSocketOpenException: Exception opening socket}, caused by {io.netty.channel.AbstractChannel$AnnotatedConnectException: Connection refused: /172.31.46.124:27017}, caused by {java.net.ConnectException: Connection refused}}].
2026-08-03T14:24:38.847344974Z  [chat-service]  [2m2026-08-03T23:24:38.847+09:00[0;39m [32m INFO [traceId=6a70a4a6b2c7c98a2f264630917bf154,spanId=3c258ce7810d3917,userId=NONE][0;39m [35m7[0;39m [2m--- [chat-service] [nio-8090-exec-9] [6a70a4a6b2c7c98a2f264630917bf154-3c258ce7810d3917] [0;39m[36morg.mongodb.driver.cluster              [0;39m [2m:[0;39m Waiting for server to become available for operation with ID 44048. Remaining time: 29999 ms. Selector: ReadPreferenceServerSelector{readPreference=primary}, topology description: {type=UNKNOWN, servers=[{address=172.31.46.124:27017, type=UNKNOWN, state=CONNECTING, exception={com.mongodb.MongoSocketOpenException: Exception opening socket}, caused by {io.netty.channel.AbstractChannel$AnnotatedConnectException: Connection refused: /172.31.46.124:27017}, caused by {java.net.ConnectException: Connection refused}}].
2026-08-03T14:24:48.753300999Z  [chat-service]  org.springframework.dao.DataAccessResourceFailureException: Timed out while waiting for a server that matches ReadPreferenceServerSelector{readPreference=primary}. Client view of cluster state is {type=UNKNOWN, servers=[{address=172.31.46.124:27017, type=UNKNOWN, state=CONNECTING, exception={com.mongodb.MongoSocketOpenException: Exception opening socket}, caused by {io.netty.channel.AbstractChannel$AnnotatedConnectException: Connection refused: /172.31.46.124:27017}, caused by {java.net.ConnectException: Connection refused}}]
2026-08-03T14:24:48.753304890Z  [chat-service]  at org.springframework.data.mongodb.core.MongoExceptionTranslator.doTranslateException(MongoExceptionTranslator.java:97) ~[spring-data-mongodb-4.5.1.jar!/:4.5.1]
2026-08-03T14:24:48.753308492Z  [chat-service]  at org.springframework.data.mongodb.core.MongoExceptionTranslator.translateExceptionIfPossible(MongoExceptionTranslator.java:74) ~[spring-data-mongodb-4.5.1.jar!/:4.5.1]
2026-08-03T14:24:48.753312275Z  [chat-service]  at org.springframework.data.mongodb.core.ReactiveMongoTemplate.potentiallyConvertRuntimeException(ReactiveMongoTemplate.java:2768) ~[spring-data-mongodb-4.5.1.jar!/:4.5.1]
2026-08-03T14:24:48.753315499Z  [chat-service]  at org.springframework.data.mongodb.core.ReactiveMongoTemplate.lambda$translateException$100(ReactiveMongoTemplate.java:2751) ~[spring-data-mongodb-4.5.1.jar!/:4.5.1]
2026-08-03T14:24:48.753434942Z  [chat-service]  Caused by: com.mongodb.MongoTimeoutException: Timed out while waiting for a server that matches ReadPreferenceServerSelector{readPreference=primary}. Client view of cluster state is {type=UNKNOWN, servers=[{address=172.31.46.124:27017, type=UNKNOWN, state=CONNECTING, exception={com.mongodb.MongoSocketOpenException: Exception opening socket}, caused by {io.netty.channel.AbstractChannel$AnnotatedConnectException: Connection refused: /172.31.46.124:27017}, caused by {java.net.ConnectException: Connection refused}}]
2026-08-03T14:24:48.753437660Z  [chat-service]  at com.mongodb.internal.connection.BaseCluster.logAndThrowTimeoutException(BaseCluster.java:427) ~[mongodb-driver-core-5.5.1.jar!/:na]
2026-08-03T14:24:48.889705610Z  [chat-service]  [2m2026-08-03T23:24:48.889+09:00[0;39m [32m INFO [traceId=6a70a4b01759197099d4eaaad1247c81,spanId=09069237ac9110ae,userId=NONE][0;39m [35m7[0;39m [2m--- [chat-service] [nio-8090-exec-1] [6a70a4b01759197099d4eaaad1247c81-09069237ac9110ae] [0;39m[36morg.mongodb.driver.cluster              [0;39m [2m:[0;39m Waiting for server to become available for operation with ID 44072. Remaining time: 29999 ms. Selector: ReadPreferenceServerSelector{readPreference=primary}, topology description: {type=UNKNOWN, servers=[{address=172.31.46.124:27017, type=UNKNOWN, state=CONNECTING, exception={com.mongodb.MongoSocketOpenException: Exception opening socket}, caused by {io.netty.channel.AbstractChannel$AnnotatedConnectException: Connection refused: /172.31.46.124:27017}, caused by {java.net.ConnectException: Connection refused}}].
2026-08-03T14:24:58.806492515Z  [chat-service]  org.springframework.dao.DataAccessResourceFailureException: Timed out while waiting for a server that matches ReadPreferenceServerSelector{readPreference=primary}. Client view of cluster state is {type=UNKNOWN, servers=[{address=172.31.46.124:27017, type=UNKNOWN, state=CONNECTING, exception={com.mongodb.MongoSocketOpenException: Exception opening socket}, caused by {io.netty.channel.AbstractChannel$AnnotatedConnectException: Connection refused: /172.31.46.124:27017}, caused by {java.net.ConnectException: Connection refused}}]
2026-08-03T14:24:58.806496239Z  [chat-service]  at org.springframework.data.mongodb.core.MongoExceptionTranslator.doTranslateException(MongoExceptionTranslator.java:97) ~[spring-data-mongodb-4.5.1.jar!/:4.5.1]
2026-08-03T14:24:58.806499171Z  [chat-service]  at org.springframework.data.mongodb.core.MongoExceptionTranslator.translateExceptionIfPossible(MongoExceptionTranslator.java:74) ~[spring-data-mongodb-4.5.1.jar!/:4.5.1]
2026-08-03T14:24:58.806524653Z  [chat-service]  at org.springframework.data.mongodb.core.ReactiveMongoTemplate.potentiallyConvertRuntimeException(ReactiveMongoTemplate.java:2768) ~[spring-data-mongodb-4.5.1.jar!/:4.5.1]
2026-08-03T14:24:58.807306671Z  [chat-service]  at org.springframework.data.mongodb.core.ReactiveMongoTemplate.lambda$translateException$100(ReactiveMongoTemplate.java:2751) ~[spring-data-mongodb-4.5.1.jar!/:4.5.1]
2026-08-03T14:24:58.807434895Z  [chat-service]  Caused by: com.mongodb.MongoTimeoutException: Timed out while waiting for a server that matches ReadPreferenceServerSelector{readPreference=primary}. Client view of cluster state is {type=UNKNOWN, servers=[{address=172.31.46.124:27017, type=UNKNOWN, state=CONNECTING, exception={com.mongodb.MongoSocketOpenException: Exception opening socket}, caused by {io.netty.channel.AbstractChannel$AnnotatedConnectException: Connection refused: /172.31.46.124:27017}, caused by {java.net.ConnectException: Connection refused}}]
2026-08-03T14:24:58.807437399Z  [chat-service]  at com.mongodb.internal.connection.BaseCluster.logAndThrowTimeoutException(BaseCluster.java:427) ~[mongodb-driver-core-5.5.1.jar!/:na]
2026-08-03T14:24:58.930289675Z  [chat-service]  [2m2026-08-03T23:24:58.930+09:00[0;39m [32m INFO [traceId=6a70a4bac0013ba673497f1f78b893f8,spanId=81abfa95851e3142,userId=NONE][0;39m [35m7[0;39m [2m--- [chat-service] [nio-8090-exec-3] [6a70a4bac0013ba673497f1f78b893f8-81abfa95851e3142] [0;39m[36morg.mongodb.driver.cluster              [0;39m [2m:[0;39m Waiting for server to become available for operation with ID 44096. Remaining time: 29999 ms. Selector: ReadPreferenceServerSelector{readPreference=primary}, topology description: {type=UNKNOWN, servers=[{address=172.31.46.124:27017, type=UNKNOWN, state=CONNECTING, exception={com.mongodb.MongoSocketOpenException: Exception opening socket}, caused by {io.netty.channel.AbstractChannel$AnnotatedConnectException: Connection refused: /172.31.46.124:27017}, caused by {java.net.ConnectException: Connection refused}}].
2026-08-03T14:25:08.852599120Z  [chat-service]  org.springframework.dao.DataAccessResourceFailureException: Timed out while waiting for a server that matches ReadPreferenceServerSelector{readPreference=primary}. Client view of cluster state is {type=UNKNOWN, servers=[{address=172.31.46.124:27017, type=UNKNOWN, state=CONNECTING, exception={com.mongodb.MongoSocketOpenException: Exception opening socket}, caused by {io.netty.channel.AbstractChannel$AnnotatedConnectException: Connection refused: /172.31.46.124:27017}, caused by {java.net.ConnectException: Connection refused}}]
2026-08-03T14:25:08.852602935Z  [chat-service]  at org.springframework.data.mongodb.core.MongoExceptionTranslator.doTranslateException(MongoExceptionTranslator.java:97) ~[spring-data-mongodb-4.5.1.jar!/:4.5.1]
2026-08-03T14:25:08.852605258Z  [chat-service]  at org.springframework.data.mongodb.core.MongoExceptionTranslator.translateExceptionIfPossible(MongoExceptionTranslator.java:74) ~[spring-data-mongodb-4.5.1.jar!/:4.5.1]
2026-08-03T14:25:08.852607581Z  [chat-service]  at org.springframework.data.mongodb.core.ReactiveMongoTemplate.potentiallyConvertRuntimeException(ReactiveMongoTemplate.java:2768) ~[spring-data-mongodb-4.5.1.jar!/:4.5.1]
2026-08-03T14:25:08.852610141Z  [chat-service]  at org.springframework.data.mongodb.core.ReactiveMongoTemplate.lambda$translateException$100(ReactiveMongoTemplate.java:2751) ~[spring-data-mongodb-4.5.1.jar!/:4.5.1]
2026-08-03T14:25:08.853434273Z  [chat-service]  Caused by: com.mongodb.MongoTimeoutException: Timed out while waiting for a server that matches ReadPreferenceServerSelector{readPreference=primary}. Client view of cluster state is {type=UNKNOWN, servers=[{address=172.31.46.124:27017, type=UNKNOWN, state=CONNECTING, exception={com.mongodb.MongoSocketOpenException: Exception opening socket}, caused by {io.netty.channel.AbstractChannel$AnnotatedConnectException: Connection refused: /172.31.46.124:27017}, caused by {java.net.ConnectException: Connection refused}}]
2026-08-03T14:25:08.853437293Z  [chat-service]  at com.mongodb.internal.connection.BaseCluster.logAndThrowTimeoutException(BaseCluster.java:427) ~[mongodb-driver-core-5.5.1.jar!/:na]
2026-08-03T14:25:08.972009720Z  [chat-service]  [2m2026-08-03T23:25:08.971+09:00[0;39m [32m INFO [traceId=6a70a4c409d0baac34a37e5a651c761d,spanId=1e7c504e1fc88027,userId=NONE][0;39m [35m7[0;39m [2m--- [chat-service] [nio-8090-exec-4] [6a70a4c409d0baac34a37e5a651c761d-1e7c504e1fc88027] [0;39m[36morg.mongodb.driver.cluster              [0;39m [2m:[0;39m Waiting for server to become available for operation with ID 44120. Remaining time: 29999 ms. Selector: ReadPreferenceServerSelector{readPreference=primary}, topology description: {type=UNKNOWN, servers=[{address=172.31.46.124:27017, type=UNKNOWN, state=CONNECTING, exception={com.mongodb.MongoSocketOpenException: Exception opening socket}, caused by {io.netty.channel.AbstractChannel$AnnotatedConnectException: Connection refused: /172.31.46.124:27017}, caused by {java.net.ConnectException: Connection refused}}].
2026-08-03T14:25:15.816120105Z  [chat-service]  [2m2026-08-03T23:25:15.815+09:00[0;39m [32m INFO [traceId=6a70a4cbf41848fcfa14ba00fe4a02f8,spanId=9309bc69b2a7c73a,userId=NONE][0;39m [35m7[0;39m [2m--- [chat-service] [ntainer#5-1-C-1] [6a70a4cbf41848fcfa14ba00fe4a02f8-9309bc69b2a7c73a] [0;39m[36morg.mongodb.driver.cluster              [0;39m [2m:[0;39m Waiting for server to become available for operation with ID 44135. Remaining time: 29999 ms. Selector: WritableServerSelector, topology description: {type=UNKNOWN, servers=[{address=172.31.46.124:27017, type=UNKNOWN, state=CONNECTING, exception={com.mongodb.MongoSocketOpenException: Exception opening socket}, caused by {io.netty.channel.AbstractChannel$AnnotatedConnectException: Connection refused: /172.31.46.124:27017}, caused by {java.net.ConnectException: Connection refused}}].
2026-08-03T14:25:18.890720165Z  [chat-service]  org.springframework.dao.DataAccessResourceFailureException: Timed out while waiting for a server that matches ReadPreferenceServerSelector{readPreference=primary}. Client view of cluster state is {type=UNKNOWN, servers=[{address=172.31.46.124:27017, type=UNKNOWN, state=CONNECTING, exception={com.mongodb.MongoSocketOpenException: Exception opening socket}, caused by {io.netty.channel.AbstractChannel$AnnotatedConnectException: Connection refused: /172.31.46.124:27017}, caused by {java.net.ConnectException: Connection refused}}]
2026-08-03T14:25:18.890756249Z  [chat-service]  at org.springframework.data.mongodb.core.MongoExceptionTranslator.doTranslateException(MongoExceptionTranslator.java:97) ~[spring-data-mongodb-4.5.1.jar!/:4.5.1]
2026-08-03T14:25:19.014409970Z  [chat-service]  [2m2026-08-03T23:25:19.014+09:00[0;39m [32m INFO [traceId=6a70a4cf6953872624c277253c4aae4b,spanId=95e6e6a2284f941e,userId=NONE][0;39m [35m7[0;39m [2m--- [chat-service] [nio-8090-exec-6] [6a70a4cf6953872624c277253c4aae4b-95e6e6a2284f941e] [0;39m[36morg.mongodb.driver.cluster              [0;39m [2m:[0;39m Waiting for server to become available for operation with ID 44152. Remaining time: 29999 ms. Selector: ReadPreferenceServerSelector{readPreference=primary}, topology description: {type=UNKNOWN, servers=[{address=172.31.46.124:27017, type=UNKNOWN, state=CONNECTING, exception={com.mongodb.MongoSocketOpenException: Exception opening socket}, caused by {io.netty.channel.AbstractChannel$AnnotatedConnectException: Connection refused: /172.31.46.124:27017}, caused by {java.net.ConnectException: Connection refused}}].
2026-08-03T14:25:29.055820295Z  [chat-service]  [2m2026-08-03T23:25:29.055+09:00[0;39m [32m INFO [traceId=6a70a4d900ac17f8b3eed1dff5a1f7cd,spanId=432f586a31bebee8,userId=NONE][0;39m [35m7[0;39m [2m--- [chat-service] [io-8090-exec-10] [6a70a4d900ac17f8b3eed1dff5a1f7cd-432f586a31bebee8] [0;39m[36morg.mongodb.driver.cluster              [0;39m [2m:[0;39m Waiting for server to become available for operation with ID 44195. Remaining time: 29999 ms. Selector: ReadPreferenceServerSelector{readPreference=primary}, topology description: {type=UNKNOWN, servers=[{address=172.31.46.124:27017, type=UNKNOWN, state=CONNECTING, exception={com.mongodb.MongoSocketOpenException: Exception opening socket}, caused by {io.netty.channel.AbstractChannel$AnnotatedConnectException: Connection refused: /172.31.46.124:27017}, caused by {java.net.ConnectException: Connection refused}}].
2026-08-03T14:25:39.097624003Z  [chat-service]  [2m2026-08-03T23:25:39.097+09:00[0;39m [32m INFO [traceId=6a70a4e31505a83978ab808d971228ea,spanId=173ce8b7621b845f,userId=NONE][0;39m [35m7[0;39m [2m--- [chat-service] [nio-8090-exec-7] [6a70a4e31505a83978ab808d971228ea-173ce8b7621b845f] [0;39m[36morg.mongodb.driver.cluster              [0;39m [2m:[0;39m Waiting for server to become available for operation with ID 44238. Remaining time: 29999 ms. Selector: ReadPreferenceServerSelector{readPreference=primary}, topology description: {type=UNKNOWN, servers=[{address=172.31.46.124:27017, type=UNKNOWN, state=CONNECTING, exception={com.mongodb.MongoSocketOpenException: Exception opening socket}, caused by {io.netty.channel.AbstractChannel$AnnotatedConnectException: Connection refused: /172.31.46.124:27017}, caused by {java.net.ConnectException: Connection refused}}].
2026-08-03T14:25:46.956177297Z  [chat-service]  [2m2026-08-03T23:25:46.956+09:00[0;39m [32m INFO [traceId=6a70a4cbf41848fcfa14ba00fe4a02f8,spanId=29e869f0fa39901f,userId=NONE][0;39m [35m7[0;39m [2m--- [chat-service] [ntainer#5-1-C-1] [6a70a4cbf41848fcfa14ba00fe4a02f8-29e869f0fa39901f] [0;39m[36morg.mongodb.driver.cluster              [0;39m [2m:[0;39m Waiting for server to become available for operation with ID 44270. Remaining time: 29999 ms. Selector: WritableServerSelector, topology description: {type=UNKNOWN, servers=[{address=172.31.46.124:27017, type=UNKNOWN, state=CONNECTING, exception={com.mongodb.MongoSocketOpenException: Exception opening socket}, caused by {io.netty.channel.AbstractChannel$AnnotatedConnectException: Connection refused: /172.31.46.124:27017}, caused by {java.net.ConnectException: Connection refused}}].
2026-08-03T14:25:49.141034368Z  [chat-service]  [2m2026-08-03T23:25:49.140+09:00[0;39m [32m INFO [traceId=6a70a4ed193a2b5a1f1bed00113d8b29,spanId=bd5d752803a53897,userId=NONE][0;39m [35m7[0;39m [2m--- [chat-service] [nio-8090-exec-8] [6a70a4ed193a2b5a1f1bed00113d8b29-bd5d752803a53897] [0;39m[36morg.mongodb.driver.cluster              [0;39m [2m:[0;39m Waiting for server to become available for operation with ID 44281. Remaining time: 29999 ms. Selector: ReadPreferenceServerSelector{readPreference=primary}, topology description: {type=UNKNOWN, servers=[{address=172.31.46.124:27017, type=UNKNOWN, state=CONNECTING, exception={com.mongodb.MongoSocketOpenException: Exception opening socket}, caused by {io.netty.channel.AbstractChannel$AnnotatedConnectException: Connection refused: /172.31.46.124:27017}, caused by {java.net.ConnectException: Connection refused}}].
2026-08-03T14:25:59.180275109Z  [chat-service]  [2m2026-08-03T23:25:59.180+09:00[0;39m [32m INFO [traceId=6a70a4f70407c7e4c4cd7fd17a8ddd02,spanId=903331fc601337ad,userId=NONE][0;39m [35m7[0;39m [2m--- [chat-service] [nio-8090-exec-9] [6a70a4f70407c7e4c4cd7fd17a8ddd02-903331fc601337ad] [0;39m[36morg.mongodb.driver.cluster              [0;39m [2m:[0;39m Waiting for server to become available for operation with ID 44324. Remaining time: 29999 ms. Selector: ReadPreferenceServerSelector{readPreference=primary}, topology description: {type=UNKNOWN, servers=[{address=172.31.46.124:27017, type=UNKNOWN, state=CONNECTING, exception={com.mongodb.MongoSocketOpenException: Exception opening socket}, caused by {io.netty.channel.AbstractChannel$AnnotatedConnectException: Connection refused: /172.31.46.124:27017}, caused by {java.net.ConnectException: Connection refused}}].
2026-08-03T14:26:09.303212407Z  [chat-service]  [2m2026-08-03T23:26:09.302+09:00[0;39m [32m INFO [traceId=6a70a501290c9d041c0620935eaa61db,spanId=c63bbfb0d891015f,userId=NONE][0;39m [35m7[0;39m [2m--- [chat-service] [nio-8090-exec-5] [6a70a501290c9d041c0620935eaa61db-c63bbfb0d891015f] [0;39m[36morg.mongodb.driver.cluster              [0;39m [2m:[0;39m Waiting for server to become available for operation with ID 44367. Remaining time: 29999 ms. Selector: ReadPreferenceServerSelector{readPreference=primary}, topology description: {type=UNKNOWN, servers=[{address=172.31.46.124:27017, type=UNKNOWN, state=CONNECTING, exception={com.mongodb.MongoSocketOpenException: Exception opening socket}, caused by {io.netty.channel.AbstractChannel$AnnotatedConnectException: Connection refused: /172.31.46.124:27017}, caused by {java.net.ConnectException: Connection refused}}].
2026-08-03T14:26:17.974405473Z  [chat-service]  [2m2026-08-03T23:26:17.974+09:00[0;39m [32m INFO [traceId=6a70a4cbf41848fcfa14ba00fe4a02f8,spanId=7d21df984b172d8b,userId=NONE][0;39m [35m7[0;39m [2m--- [chat-service] [ntainer#5-1-C-1] [6a70a4cbf41848fcfa14ba00fe4a02f8-7d21df984b172d8b] [0;39m[36morg.mongodb.driver.cluster              [0;39m [2m:[0;39m Waiting for server to become available for operation with ID 44403. Remaining time: 29999 ms. Selector: WritableServerSelector, topology description: {type=UNKNOWN, servers=[{address=172.31.46.124:27017, type=UNKNOWN, state=CONNECTING, exception={com.mongodb.MongoSocketOpenException: Exception opening socket}, caused by {io.netty.channel.AbstractChannel$AnnotatedConnectException: Connection refused: /172.31.46.124:27017}, caused by {java.net.ConnectException: Connection refused}}].
2026-08-03T14:26:19.264680180Z  [chat-service]  [2m2026-08-03T23:26:19.264+09:00[0;39m [32m INFO [traceId=6a70a50be94507293e827c46c93bdb5b,spanId=8e79ddefc575903b,userId=NONE][0;39m [35m7[0;39m [2m--- [chat-service] [nio-8090-exec-3] [6a70a50be94507293e827c46c93bdb5b-8e79ddefc575903b] [0;39m[36morg.mongodb.driver.cluster              [0;39m [2m:[0;39m Waiting for server to become available for operation with ID 44410. Remaining time: 29999 ms. Selector: ReadPreferenceServerSelector{readPreference=primary}, topology description: {type=UNKNOWN, servers=[{address=172.31.46.124:27017, type=UNKNOWN, state=CONNECTING, exception={com.mongodb.MongoSocketOpenException: Exception opening socket}, caused by {io.netty.channel.AbstractChannel$AnnotatedConnectException: Connection refused: /172.31.46.124:27017}, caused by {java.net.ConnectException: Connection refused}}].
2026-08-03T14:26:29.306188299Z  [chat-service]  [2m2026-08-03T23:26:29.305+09:00[0;39m [32m INFO [traceId=6a70a5159cd2ffa748a878c59a8d63fd,spanId=7945e328aab9897f,userId=NONE][0;39m [35m7[0;39m [2m--- [chat-service] [nio-8090-exec-4] [6a70a5159cd2ffa748a878c59a8d63fd-7945e328aab9897f] [0;39m[36morg.mongodb.driver.cluster              [0;39m [2m:[0;39m Waiting for server to become available for operation with ID 44453. Remaining time: 29999 ms. Selector: ReadPreferenceServerSelector{readPreference=primary}, topology description: {type=UNKNOWN, servers=[{address=172.31.46.124:27017, type=UNKNOWN, state=CONNECTING, exception={com.mongodb.MongoSocketOpenException: Exception opening socket}, caused by {io.netty.channel.AbstractChannel$AnnotatedConnectException: Connection refused: /172.31.46.124:27017}, caused by {java.net.ConnectException: Connection refused}}].
2026-08-03T14:26:39.347017289Z  [chat-service]  [2m2026-08-03T23:26:39.346+09:00[0;39m [32m INFO [traceId=6a70a51f4228124d58fda0f293b5718d,spanId=67f4d6bc8d247231,userId=NONE][0;39m [35m7[0;39m [2m--- [chat-service] [nio-8090-exec-2] [6a70a51f4228124d58fda0f293b5718d-67f4d6bc8d247231] [0;39m[36morg.mongodb.driver.cluster              [0;39m [2m:[0;39m Waiting for server to become available for operation with ID 44496. Remaining time: 29999 ms. Selector: ReadPreferenceServerSelector{readPreference=primary}, topology description: {type=UNKNOWN, servers=[{address=172.31.46.124:27017, type=UNKNOWN, state=CONNECTING, exception={com.mongodb.MongoSocketOpenException: Exception opening socket}, caused by {io.netty.channel.AbstractChannel$AnnotatedConnectException: Connection refused: /172.31.46.124:27017}, caused by {java.net.ConnectException: Connection refused}}].
2026-08-03T14:26:48.992408653Z  [chat-service]  [2m2026-08-03T23:26:48.992+09:00[0;39m [32m INFO [traceId=6a70a4cbf41848fcfa14ba00fe4a02f8,spanId=6af3ac65efeef36c,userId=NONE][0;39m [35m7[0;39m [2m--- [chat-service] [ntainer#5-1-C-1] [6a70a4cbf41848fcfa14ba00fe4a02f8-6af3ac65efeef36c] [0;39m[36morg.mongodb.driver.cluster              [0;39m [2m:[0;39m Waiting for server to become available for operation with ID 44536. Remaining time: 29999 ms. Selector: WritableServerSelector, topology description: {type=UNKNOWN, servers=[{address=172.31.46.124:27017, type=UNKNOWN, state=CONNECTING, exception={com.mongodb.MongoSocketOpenException: Exception opening socket}, caused by {io.netty.channel.AbstractChannel$AnnotatedConnectException: Connection refused: /172.31.46.124:27017}, caused by {java.net.ConnectException: Connection refused}}].
2026-08-03T14:26:49.389618911Z  [chat-service]  [2m2026-08-03T23:26:49.389+09:00[0;39m [32m INFO [traceId=6a70a529d19d5bd161816e0bd391b391,spanId=c166ead3e82fffd3,userId=NONE][0;39m [35m7[0;39m [2m--- [chat-service] [io-8090-exec-10] [6a70a529d19d5bd161816e0bd391b391-c166ead3e82fffd3] [0;39m[36morg.mongodb.driver.cluster              [0;39m [2m:[0;39m Waiting for server to become available for operation with ID 44539. Remaining time: 29999 ms. Selector: ReadPreferenceServerSelector{readPreference=primary}, topology description: {type=UNKNOWN, servers=[{address=172.31.46.124:27017, type=UNKNOWN, state=CONNECTING, exception={com.mongodb.MongoSocketOpenException: Exception opening socket}, caused by {io.netty.channel.AbstractChannel$AnnotatedConnectException: Connection refused: /172.31.46.124:27017}, caused by {java.net.ConnectException: Connection refused}}].
2026-08-03T14:26:59.431688645Z  [chat-service]  [2m2026-08-03T23:26:59.431+09:00[0;39m [32m INFO [traceId=6a70a533981876b400fe1f1f63b23495,spanId=00412e368ed41b9b,userId=NONE][0;39m [35m7[0;39m [2m--- [chat-service] [nio-8090-exec-7] [6a70a533981876b400fe1f1f63b23495-00412e368ed41b9b] [0;39m[36morg.mongodb.driver.cluster              [0;39m [2m:[0;39m Waiting for server to become available for operation with ID 44582. Remaining time: 29999 ms. Selector: ReadPreferenceServerSelector{readPreference=primary}, topology description: {type=UNKNOWN, servers=[{address=172.31.46.124:27017, type=UNKNOWN, state=CONNECTING, exception={com.mongodb.MongoSocketOpenException: Exception opening socket}, caused by {io.netty.channel.AbstractChannel$AnnotatedConnectException: Connection refused: /172.31.46.124:27017}, caused by {java.net.ConnectException: Connection refused}}].
```

### 메트릭 시계열

| 쿼리 | series | 점 | min | max | last | 값이 0이던 구간 |
|---|---|---:|---:|---:|---:|---|
| `hikaricp_connections_active` | `{__name__=hikaricp_connections_active, application=auth-service, cluster=yogurtte-k3s-prod, container=auth-service, instance=10.42.1.45:8090, job=auth-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=auth-service-5999bb9f5c-lbpf2, pool=HikariPool-1, service=auth-service}` | 96 | 0 | 0 | 0 | **2026-08-03T13:36:09Z ~ 2026-08-03T13:59:54Z** |
| `hikaricp_connections_active` | `{__name__=hikaricp_connections_active, application=auth-service, cluster=yogurtte-k3s-prod, container=auth-service, instance=10.42.1.46:8090, job=auth-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=auth-service-5999bb9f5c-hmgp9, pool=HikariPool-1, service=auth-service}` | 99 | 0 | 0 | 0 | **2026-08-03T14:11:39Z ~ 2026-08-03T14:36:09Z** |
| `hikaricp_connections_active` | `{__name__=hikaricp_connections_active, application=chat-service, cluster=yogurtte-k3s-prod, container=chat-service, instance=10.42.3.43:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-fdcc7c776-qrbc2, pool=HikariPool-1}` | 207 | 0 | 0 | 0 | **2026-08-03T13:36:09Z ~ 2026-08-03T14:32:39Z** |
| `hikaricp_connections_active` | `{__name__=hikaricp_connections_active, application=content-service, cluster=yogurtte-k3s-prod, container=content-service, instance=10.42.1.43:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-6995bb7d94-h2f6n, pool=HikariPool-1}` | 241 | 0 | 0 | 0 | **2026-08-03T13:36:09Z ~ 2026-08-03T14:36:09Z** |
| `hikaricp_connections_active` | `{__name__=hikaricp_connections_active, application=content-service, cluster=yogurtte-k3s-prod, container=content-service, instance=10.42.3.42:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-6995bb7d94-nq9l2, pool=HikariPool-1}` | 241 | 0 | 0 | 0 | **2026-08-03T13:36:09Z ~ 2026-08-03T14:36:09Z** |
| `hikaricp_connections_pending` | `{__name__=hikaricp_connections_pending, application=auth-service, cluster=yogurtte-k3s-prod, container=auth-service, instance=10.42.1.45:8090, job=auth-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=auth-service-5999bb9f5c-lbpf2, pool=HikariPool-1, service=auth-service}` | 96 | 0 | 0 | 0 | **2026-08-03T13:36:09Z ~ 2026-08-03T13:59:54Z** |
| `hikaricp_connections_pending` | `{__name__=hikaricp_connections_pending, application=auth-service, cluster=yogurtte-k3s-prod, container=auth-service, instance=10.42.1.46:8090, job=auth-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=auth-service-5999bb9f5c-hmgp9, pool=HikariPool-1, service=auth-service}` | 99 | 0 | 0 | 0 | **2026-08-03T14:11:39Z ~ 2026-08-03T14:36:09Z** |
| `hikaricp_connections_pending` | `{__name__=hikaricp_connections_pending, application=chat-service, cluster=yogurtte-k3s-prod, container=chat-service, instance=10.42.3.43:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-fdcc7c776-qrbc2, pool=HikariPool-1}` | 207 | 0 | 0 | 0 | **2026-08-03T13:36:09Z ~ 2026-08-03T14:32:39Z** |
| `hikaricp_connections_pending` | `{__name__=hikaricp_connections_pending, application=content-service, cluster=yogurtte-k3s-prod, container=content-service, instance=10.42.1.43:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-6995bb7d94-h2f6n, pool=HikariPool-1}` | 241 | 0 | 0 | 0 | **2026-08-03T13:36:09Z ~ 2026-08-03T14:36:09Z** |
| `hikaricp_connections_pending` | `{__name__=hikaricp_connections_pending, application=content-service, cluster=yogurtte-k3s-prod, container=content-service, instance=10.42.3.42:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-6995bb7d94-nq9l2, pool=HikariPool-1}` | 241 | 0 | 0 | 0 | **2026-08-03T13:36:09Z ~ 2026-08-03T14:36:09Z** |
| `rate(jvm_gc_pause_seconds_sum[5m])` | `{action=end of major GC, application=chat-service, cause=Allocation Failure, cluster=yogurtte-k3s-prod, container=chat-service, gc=MarkSweepCompact, instance=10.42.3.43:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-fdcc7c776-qrbc2}` | 227 | 0 | 0 | 0 | **2026-08-03T13:36:09Z ~ 2026-08-03T14:35:39Z** |
| `rate(jvm_gc_pause_seconds_sum[5m])` | `{action=end of minor GC, application=auth-service, cause=G1 Evacuation Pause, cluster=yogurtte-k3s-prod, container=auth-service, gc=G1 Young Generation, instance=10.42.1.45:8090, job=auth-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=auth-service-5999bb9f5c-lbpf2, service=auth-service}` | 108 | 0 | 0.000 | 0 | **2026-08-03T13:36:09Z ~ 2026-08-03T13:36:54Z, 2026-08-03T13:41:09Z ~ 2026-08-03T13:55:54Z, 2026-08-03T14:00:09Z ~ 2026-08-03T14:02:54Z** |
| `rate(jvm_gc_pause_seconds_sum[5m])` | `{action=end of minor GC, application=auth-service, cause=G1 Evacuation Pause, cluster=yogurtte-k3s-prod, container=auth-service, gc=G1 Young Generation, instance=10.42.1.46:8090, job=auth-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=auth-service-5999bb9f5c-hmgp9, service=auth-service}` | 95 | 0 | 0.001 | 0 | **2026-08-03T14:12:39Z ~ 2026-08-03T14:24:24Z, 2026-08-03T14:28:39Z ~ 2026-08-03T14:36:09Z** |
| `rate(jvm_gc_pause_seconds_sum[5m])` | `{action=end of minor GC, application=chat-service, cause=Allocation Failure, cluster=yogurtte-k3s-prod, container=chat-service, gc=Copy, instance=10.42.3.43:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-fdcc7c776-qrbc2}` | 227 | 0.000 | 0.000 | 0.000 | — |
| `rate(jvm_gc_pause_seconds_sum[5m])` | `{action=end of minor GC, application=content-service, cause=Allocation Failure, cluster=yogurtte-k3s-prod, container=content-service, gc=Copy, instance=10.42.1.43:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-6995bb7d94-h2f6n}` | 241 | 0 | 0.000 | 0 | **2026-08-03T13:36:09Z ~ 2026-08-03T13:44:54Z, 2026-08-03T13:49:09Z ~ 2026-08-03T13:58:54Z, 2026-08-03T14:03:09Z ~ 2026-08-03T14:13:54Z, 2026-08-03T14:18:09Z ~ 2026-08-03T14:27:54Z, 2026-08-03T14:32:09Z ~ 2026-08-03T14:36:09Z** |
| `rate(jvm_gc_pause_seconds_sum[5m])` | `{action=end of minor GC, application=content-service, cause=Allocation Failure, cluster=yogurtte-k3s-prod, container=content-service, gc=Copy, instance=10.42.3.42:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-6995bb7d94-nq9l2}` | 241 | 0 | 0.000 | 0.000 | **2026-08-03T13:36:09Z ~ 2026-08-03T13:36:39Z, 2026-08-03T13:40:54Z ~ 2026-08-03T13:50:39Z, 2026-08-03T13:54:54Z ~ 2026-08-03T14:05:39Z, 2026-08-03T14:09:54Z ~ 2026-08-03T14:19:39Z, 2026-08-03T14:23:54Z ~ 2026-08-03T14:32:39Z** |
| `up` | `{__name__=up, app=node-exporter, cluster=yogurtte-k3s-prod, component=metrics, container=node-exporter, instance=ip-172-31-40-241, job=integrations/node_exporter, k8s_cluster_name=yogurtte-k3s-prod, namespace=monitoring, pod=grafana-k8s-monitoring-node-exporter-cph5l, source=kubernetes, workload=DaemonSet/grafana-k8s-monitoring-node-exporter}` | 241 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, app=node-exporter, cluster=yogurtte-k3s-prod, component=metrics, container=node-exporter, instance=ip-172-31-45-39, job=integrations/node_exporter, k8s_cluster_name=yogurtte-k3s-prod, namespace=monitoring, pod=grafana-k8s-monitoring-node-exporter-9kvqt, source=kubernetes, workload=DaemonSet/grafana-k8s-monitoring-node-exporter}` | 241 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, cluster=yogurtte-k3s-prod, container=auth-service, instance=10.42.1.45:8090, job=auth-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=auth-service-5999bb9f5c-lbpf2}` | 96 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, cluster=yogurtte-k3s-prod, container=auth-service, instance=10.42.1.46:8090, job=auth-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=auth-service-5999bb9f5c-hmgp9}` | 99 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, cluster=yogurtte-k3s-prod, container=chat-service, instance=10.42.3.43:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-fdcc7c776-qrbc2}` | 207 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, cluster=yogurtte-k3s-prod, container=content-service, instance=10.42.1.43:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-6995bb7d94-h2f6n}` | 241 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, cluster=yogurtte-k3s-prod, container=content-service, instance=10.42.3.42:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-6995bb7d94-nq9l2}` | 241 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, cluster=yogurtte-k3s-prod, instance=10.42.1.248:8080, job=integrations/kubernetes/kube-state-metrics, k8s_cluster_name=yogurtte-k3s-prod, source=kubernetes}` | 241 | 1 | 1 | 1 | — |
| `mongodb_up` | `{__name__=mongodb_up, cluster=yogurtte-k3s-prod, instance=infra-server, job=mongodb, k8s_cluster_name=yogurtte-k3s-prod}` | 241 | 0 | 1 | 1 | **2026-08-03T14:24:39Z ~ 2026-08-03T14:29:24Z** |
| `kafka_brokers` | `{__name__=kafka_brokers, cluster=yogurtte-k3s-prod, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod}` | 241 | 1 | 1 | 1 | — |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=chat-service-fcm-tokens, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=0, topic=user.fcm-tokens}` | 241 | 0 | 0 | 0 | **2026-08-03T13:36:09Z ~ 2026-08-03T14:36:09Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=chat-service-fcm-tokens, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=1, topic=user.fcm-tokens}` | 241 | 0 | 0 | 0 | **2026-08-03T13:36:09Z ~ 2026-08-03T14:36:09Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=chat-service-fcm-tokens, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=2, topic=user.fcm-tokens}` | 241 | 0 | 0 | 0 | **2026-08-03T13:36:09Z ~ 2026-08-03T14:36:09Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=chat-service-notification-settings, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=0, topic=user.notification-settings}` | 241 | 0 | 0 | 0 | **2026-08-03T13:36:09Z ~ 2026-08-03T14:36:09Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=chat-service-notification-settings, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=1, topic=user.notification-settings}` | 241 | 0 | 0 | 0 | **2026-08-03T13:36:09Z ~ 2026-08-03T14:36:09Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=chat-service-notification-settings, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=2, topic=user.notification-settings}` | 241 | 0 | 0 | 0 | **2026-08-03T13:36:09Z ~ 2026-08-03T14:36:09Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=db-writer, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=0, topic=chat.messages}` | 241 | 0 | 0 | 0 | **2026-08-03T13:36:09Z ~ 2026-08-03T14:36:09Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=db-writer, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=1, topic=chat.messages}` | 241 | 0 | 0 | 0 | **2026-08-03T13:36:09Z ~ 2026-08-03T14:36:09Z** |
| `websocket_active_users` | `{__name__=websocket_active_users, application=chat-service, cluster=yogurtte-k3s-prod, container=chat-service, instance=10.42.3.43:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-fdcc7c776-qrbc2}` | 207 | 0 | 0 | 0 | **2026-08-03T13:36:09Z ~ 2026-08-03T14:32:39Z** |

값이 0이던 구간은 굵게 표시했다 — 프로세스가 사라져 시계열이 꺾인 것이 유일한 신호인 장애가 있다.

