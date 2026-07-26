# RCA Report — `6a65d82afd3e2638aa9c2020f0a1fbe9`

| 항목 | 값 |
|---|---|
| 모드 | rca |
| 질문 | 왜 알림이 안 왔어? |
| 시각 | 2026-07-26T09:54:44.665414Z |
| provider | claude-cli |
| prompt | `./prompts/system-prompt.md` |
| tokens | in 45935 / out 5083 · cost $0.9040 |
| elapsed | total 76082ms (tempo 310 · loki 176 · mimir 261 · assemble 1 · llm 75331) |

## 수집 범위 (Coverage)

- **window**: 2026-07-26T09:47:30.016367Z ~ 2026-07-26T09:55:32.406283Z (482s)
- **trace**: 25,769B / 29 spans
- **logs**: errwarn=3,957B · traceId=3,956B
- **metrics**: 3 수집, 누락 [kafka_consumer_fetch_manager_records_lag]
- **context**: 45,434 chars (~11,358 tok 추정)

## 수집 실패/누락

- Metric 'kafka_consumer_fetch_manager_records_lag' returned no series in this window; skipped.

---

관측 데이터만으로 분석한 결과입니다. 트레이스 자체가 핵심 증거를 담고 있어 추가 도구 조회 없이 판단 가능합니다.

## 1. 원인 후보 랭킹 (최대 3개)

1. **chat-service 소비자 파드 교체(재시작)로 인한 Kafka 메시지 소비 지연 (~4분)** — 알림이 "안 온" 것이 아니라 약 4분 늦게 처리됨
2. **최종 발송 단계(FCM push / WebSocket) 실패 또는 미발송** — 처리 완료 후에도 실제 단말 도달 여부 불명
3. **사용자 알림 설정에 의한 발송 차단** — 데이터 부족

## 2. 후보별 근거

### 후보 1: chat-service 파드 교체로 인한 소비 지연 (약 241초)

- **근거:**
  - content-service의 `publish user.notifications` (SPAN_KIND_PRODUCER) 종료: `1785059370082042000` ns = **09:47:30.082Z**
  - chat-service의 `receive` (SPAN_KIND_CONSUMER, partition 3, offset 945) 시작: `1785059611503066000` ns = **09:51:31.503Z**
  - → **발행과 소비 사이 241.4초(약 4분) 갭.** 정상 파이프라인이면 ms 단위여야 함.
  - 파드 교체 정황: 메트릭에서 chat-service 파드가 2개 관측됨.
    - 구 파드 `chat-service-857c54dd97-zcsh7` (10.42.1.30): `jvm_gc_pause` 시계열이 **1785059280 (09:46:00) 이후 소멸**
    - 신 파드 `chat-service-857c54dd97-s5fbl` (10.42.1.31, 트레이스를 소비한 파드): `hikaricp_connections_active`가 **1785059595 (09:51:15)부터 처음 등장**하고, 그 직후인 09:51:31에 메시지를 소비
  - 즉 메시지가 발행된 09:47:30 시점에 chat-service 소비자가 부재(구 파드 종료 ~ 신 파드 기동 사이)였고, 신 파드가 뜨자마자 밀린 메시지를 소비한 타임라인과 정확히 일치.
  - 소비 후 처리는 정상 완료: `user_notifications insert` (Mongo), `user-notification-service#process-notification`, `push-dispatcher#dispatch` 모두 에러 없이 종료 (`status:{}`, exception 없음).
- **확신도:** 중간 (지연 소비 자체는 **높음** — 타임스탬프로 확정. 다만 "파드 재시작이 원인"이라는 부분은 메트릭 시계열의 출현/소멸이라는 간접 증거뿐이고, K8s 이벤트·`kafka_consumer_fetch_manager_records_lag` 메트릭이 결측이라 한 단계 낮춤)
- **반증 데이터:** 없음. (ERROR/WARN 로그 0건은 crash 부재처럼 보일 수 있으나, 로그 결측으로 롤링 업데이트/OOMKill 모두 배제 불가)

### 후보 2: 최종 발송(FCM/WebSocket) 실패 또는 미발송 — 데이터 부족

- **근거:**
  - `push-dispatcher#dispatch` (09:51:51.878~09:51:52.398, 520ms) 내부의 마지막 자식 span은 `fcm_tokens.find` (Mongo, 84ms)이며, **그 이후 FCM 등 외부 호출 span이 전혀 없음.** dispatch가 이후 ~406ms 더 실행되고 종료 — 이 구간에서 무엇을 했는지 관측 불가 (미계측 또는 토큰 0건으로 발송 스킵).
  - `user-notification-web-socket-sender#send-notification` 내부는 Redis `KEYS 0` 조회 1건뿐 — 세션 조회 결과(사용자 온라인 여부)와 실제 WS 전송 여부를 알 수 없음.
  - Mongo 쿼리 결과(row 수 등)가 계측되지 않아 `fcm_tokens.find`가 토큰을 반환했는지 확인 불가.
- **확신도:** 낮음 (**데이터 부족** — FCM 호출 계측, Mongo 결과 계측, traceId 매칭 로그가 모두 없음)
- **반증 데이터:** dispatch/sendNotification span이 exception 없이 정상 종료함 (발송 실패라면 보통 에러가 남을 것으로 기대되나, 계측 공백으로 단정 불가).

### 후보 3: 사용자 알림 설정으로 인한 차단 — 데이터 부족

- **근거:** `user_notification_settings.find`가 두 번 조회됨 (09:51:51.699, 09:51:51.803). 설정 값이 off였다면 발송이 조용히 스킵될 수 있으나, 조회 결과가 관측되지 않음.
- **확신도:** 낮음 (**데이터 부족**)
- **반증 데이터:** 설정 조회 이후에도 `push-dispatcher#dispatch`까지 파이프라인이 계속 진행됨 — 설정에서 차단됐다면 dispatch까지 가지 않았을 가능성이 높음.

### 공백으로 인한 전반적 확신도 하향

- `kafka_consumer_fetch_manager_records_lag` 결측 → 지연이 이 파티션/그룹 전반의 lag였는지 단발성인지 확인 불가.
- Loki에서 ERROR/WARN 및 traceId 매칭 로그가 **0건** — 로그 파이프라인 자체의 공백 가능성이 있어 "에러 없음"을 강한 증거로 쓰지 않음.

## 3. 권장 다음 조치

1. **chat-service 파드 이력 확인:** `kubectl get events --field-selector involvedObject.name=chat-service-857c54dd97-zcsh7` 및 `kubectl describe pod chat-service-857c54dd97-s5fbl` — 09:46~09:51 사이 종료 사유(OOMKilled / 롤링 업데이트 / 노드 이슈) 확정.
2. **소비자 그룹 리밸런스 확인:** `notification-processors` 그룹의 09:46~09:52 리밸런스 로그, `kafka-consumer-groups --describe`로 partition 3의 현재 lag 확인 (lag 메트릭 결측 보완).
3. **최종 발송 여부 확정:** Mongo `toychat.user_notifications`에서 이 알림 문서(09:51:51 insert) 존재·상태 확인, `fcm_tokens`에 해당 사용자 토큰 존재 여부 확인.
4. **계측 공백 보완:** PushDispatcher의 FCM 호출에 span/로그 추가, Loki에 chat-service 로그가 실제 적재되는지 파이프라인 점검 (traceId 매칭 0건은 비정상 가능성).
5. **재발 감시:** `kafka_consumer_fetch_manager_records_lag` 수집 복구 및 발행→소비 지연(p99) 알람 설정.

**요약:** 이 트레이스에서 알림 이벤트는 정상 발행됐고, chat-service가 **4분 늦게**(09:47:30 발행 → 09:51:31 소비) 소비한 뒤 에러 없이 처리를 마쳤습니다. 지연 구간이 chat-service 파드 교체 시점(구 파드 메트릭 소멸 09:46, 신 파드 등장 09:51:15)과 일치하므로 소비자 부재가 가장 유력한 원인입니다. 다만 최종 FCM/WS 발송 성공 여부는 계측 공백으로 확인할 수 없어, "늦게 왔는지" vs "끝내 안 왔는지"는 위 3번 조치로 확정해야 합니다.
