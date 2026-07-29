# RCA Report — `6a65bd43c41bfa6c5c18a89e1f855373`

| 항목 | 값 |
|---|---|
| 모드 | rca |
| 질문 | 왜 알림이 늦었어? |
| 시각 | 2026-07-26T08:00:22.441950Z |
| provider | claude-cli |
| prompt | `./prompts/system-prompt.md` |
| tokens | in 44798 / out 9162 · cost $1.0845 |
| elapsed | total 135443ms (tempo 563 · loki 190 · mimir 290 · assemble 2 · llm 134383) |

## 수집 범위 (Coverage)

- **window**: 2026-07-26T07:52:43.565322Z ~ 2026-07-26T07:57:08.353020Z (264s)
- **trace**: 25,768B / 29 spans
- **logs**: errwarn=3,957B · traceId=3,957B
- **metrics**: 3 수집, 누락 [kafka_consumer_fetch_manager_records_lag]
- **context**: 43,387 chars (~10,846 tok 추정)

## 수집 실패/누락

- Metric 'kafka_consumer_fetch_manager_records_lag' returned no series in this window; skipped.

---

## 1. 원인 후보 랭킹 (최대 3개)

1. **chat-service `processNotification` 내부의 미계측(un-instrumented) 블로킹 구간** — 전체 지연 24.7초 중 23.4초가 이 구간에서 발생. 위치는 확정이나, 그 안에서 무엇을 했는지는 **데이터 부족**.
2. **첫 MongoDB 작업 직전의 대기 (Mongo 커넥션 확보/서버 셀렉션 지연 가설)** — 미계측 구간이 정확히 "첫 Mongo insert 직전"에서 끝난다는 위치 정황에 근거.
3. **chat pod 자체의 리소스 정체(GC/CPU 스톨)** — 메트릭 스크레이프 공백과 GC 상승이 정황 근거이나 설명력 부족.

## 2. 후보별 근거

### 후보 1: `processNotification` 내부 미계측 블로킹 구간

- 근거:
  - 업스트림은 전부 빠름: content의 `http post /battles/{battleId}/items/{itemId}/comments` 전체 72.7ms(07:52:43.565→.638), `publish user.notifications` 18ms.
  - Kafka 구간도 빠름: publish 종료 07:52:43.654027 → chat의 `receive` 시작 07:52:43.655475, **핸드오프 1.45ms**. 브로커 체류/컨슈머 랙에 의한 지연이 아님.
  - chat의 `receive` span 총 24.70초(07:52:43.655 → 07:53:08.353). 그 안에서 `user-notification-service#process-notification`이 07:52:43.661654에 시작했는데, **첫 자식 span인 `insert toychat`(`user_notifications.insert`)이 07:53:07.105914에야 시작 — 23.44초 공백**. 공백 구간에 자식 span이 하나도 없다.
  - 공백 이후의 작업은 전부 정상 속도: Mongo insert 21ms, `user_sync_status.find` 71ms, `user_notification_settings.find` 20ms, Redis `KEYS` ~1ms, `push-dispatcher#dispatch` 0.92초.
  - JDBC 대기도 아님: chat `connection` span에서 `acquired` 이벤트가 시작 1.7ms 후 발생, `hikaricp_connections_pending`은 전 서비스 전 구간 0.
- 확신도: **높음** (지연이 이 구간에서 발생했다는 위치 특정에 한함. 구간 내부에서 수행된 작업의 정체는 데이터 부족 — 트레이스에 span 없음, Loki 로그 0건).
- 반증 데이터: 없음.

### 후보 2: 첫 Mongo 작업 직전 대기 — 커넥션/서버 셀렉션 지연 가설

- 근거:
  - 23.44초 공백이 트레이스 내 **최초의 MongoDB 명령 직전**에서 끝난다. Mongo 드라이버의 커넥션 체크아웃/서버 셀렉션 대기는 명령 span(`insert`) 시작 전에 일어나므로 span에 잡히지 않는 위치와 일치한다.
  - 다만 이는 위치 정황일 뿐이며 직접 증거(드라이버 풀 메트릭, 타임아웃 로그)는 없음 — **데이터 부족**. Mongo 커넥션풀 메트릭과 chat 로그가 추가로 필요.
- 확신도: **낮음**.
- 반증 데이터: 공백 직후의 Mongo 작업들이 21~71ms로 즉시 정상 수행됨(서버 자체가 느렸다면 명령 span도 길었을 가능성이 높음). Loki ERROR/WARN 0건이라 타임아웃/재연결 로그 흔적도 없음(단, traceId 매칭 로그도 0건이라 로그 파이프라인 자체가 비어 있어 반증력은 제한적).

### 후보 3: chat pod 리소스 정체 (GC/CPU 스톨)

- 근거:
  - `hikaricp_connections_active`/`pending`의 chat-service(pod `chat-service-857c54dd97-zcsh7`) 샘플이 07:52:58(1785052498) 이후 07:54:13(1785052573)까지 **4회 연속(약 60~75초) 누락** — 같은 시간대 auth/content pod는 15초 간격으로 연속 수집됨. 스크레이프 무응답은 pod 정체의 정황.
  - `rate(jvm_gc_pause_seconds_sum[5m])`에서 chat의 minor GC(`gc="Copy"`, `cause="Allocation Failure"`)가 07:52:33경 0.00014→0.0015로, 07:54:13경 0.0028로 상승. 수집기가 Copy/MarkSweepCompact(Serial 계열)인 점은 작은 힙 환경을 시사.
- 확신도: **낮음**.
- 반증 데이터: GC pause 총량이 최대 0.0028s/s ≈ 5분당 0.85초 수준으로 23초 공백을 설명하기엔 두 자릿수 부족. major GC(`MarkSweepCompact`)는 전 구간 0. 또한 공백 전후의 span들(JDBC acquire 1.7ms, Mongo/Redis ms 단위)이 정상 속도로 실행되어 장시간 전면 스톨과 배치됨. 스크레이프 공백(07:53:13~)도 지연 구간(07:52:43~07:53:07)보다 뒤에 시작.

**배제된 후보**: Kafka 컨슈머 랙(핸드오프 1.45ms로 반증), HikariCP 풀 고갈(pending 전 구간 0, acquire 1.7ms). 단, `kafka_consumer_fetch_manager_records_lag` 수집 실패로 랙 배제는 트레이스 단건 근거에만 의존함.

## 3. 권장 다음 조치

1. **코드 확인(최우선)**: `com.example.toychat.app.userNotification.service.UserNotificationService#processNotification`에서 첫 `user_notifications` insert **이전에** 실행되는 로직 확인 — 외부 API 호출, 재시도 루프, 락 대기, sleep, 동기 초기화 등. 해당 구간에 span/타이머 계측 추가.
2. **로그 파이프라인 점검**: Loki에서 ERROR/WARN도, traceId 매칭 로그도 0건. chat-service 로그가 수집되고 있는지, 로그에 traceId가 주입되는지 확인 후 07:52:43~07:53:10 구간 raw 로그 재조회.
3. **Mongo 드라이버 지표 수집**: `mongodb_driver_pool_*` (checkout 대기, 커넥션 수) 메트릭과 드라이버 로그로 후보 2 검증. `serverSelectionTimeoutMS`/커넥션풀 설정 확인.
4. **pod 리소스 확인**: 해당 시각 chat pod의 `container_cpu_cfs_throttled_seconds_total`, 메모리 사용량, K8s 이벤트 조회. 07:53:13~07:54:13 스크레이프 공백의 원인 규명.
5. **랙 메트릭 재수집**: `kafka_consumer_fetch_manager_records_lag`가 빈 시리즈였으므로 실제 메트릭명(예: `kafka_consumer_fetch_manager_records_lag_max` 또는 exporter 라벨) 확인 후 재조회.
6. **재현성 확인**: 같은 컨슈머 그룹(`notification-processors`)의 다른 트레이스에서 동일한 "processNotification 시작 ~ 첫 Mongo op" 공백이 반복되는지 비교 — 상시 문제인지 단발성인지 판별.
