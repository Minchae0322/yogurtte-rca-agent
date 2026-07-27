# RCA Report — `6a677e9905de505f67b409e2d5a97ca3`

| 항목 | 값 |
|---|---|
| 모드 | rca |
| 질문 | 왜 알림이 안 왔어? |
| 시각 | 2026-07-27T15:54:50.069692Z |
| provider | claude-cli |
| prompt | `./prompts/system-prompt.md` |
| tokens | in 37932 / out 2657 · cost $0.2976 |
| elapsed | total 49430ms (tempo 652 · loki 193 · mimir 305 · assemble 1 · llm 48273) |

## 수집 범위 (Coverage)

- **window**: 2026-07-27T15:49:53.715132Z ~ 2026-07-27T15:54:53.817800Z (300s)
- **trace**: 14,144B / 15 spans
- **logs**: errwarn=3,958B · traceId=3,958B
- **metrics**: 3 수집, 누락 [kafka_consumer_fetch_manager_records_lag]
- **context**: 32,612 chars (~8,153 tok 추정)

## 수집 실패/누락

- Metric 'kafka_consumer_fetch_manager_records_lag' returned no series in this window; skipped.

---

## 1. 원인 후보 랭킹 (최대 3개)

1. **Kafka 토픽 `user.notifications` 부재(또는 브로커 메타데이터 미확보)로 content 서비스의 이벤트 발행 자체가 실패**
2. **Kafka 브로커 연결 불가 / 브로커 다운으로 메타데이터를 못 받음** (1번과 동일 증상의 다른 근본 원인)
3. **chat 서비스(소비자 측) 문제** — 데이터 부족

---

## 2. 후보별 근거

### 후보 1: 토픽 `user.notifications` 부재로 발행 실패
- **근거:**
  - producer span `publish user.notifications` (kind=PRODUCER) 가 error 속성으로 원문 그대로:
    `"Topic user.notifications not present in metadata after 60000 ms."` (`status.code=STATUS_CODE_ERROR`)
  - 상위 span `notification-publish` (`com.example.toycontent.app.notification.NotificationEventListener`, method `onNotification`) 도 `error="Send failed"`, `STATUS_CODE_ERROR`.
  - 타이밍: producer span start `1785167513797049000`(15:51:53.797) → end `1785167573812739000`(15:52:53.812), 정확히 **60,000ms 블로킹 후 실패**. `max.block.ms` 기본값(60초) 만료와 일치.
  - 반면 본문 트랜잭션은 정상: 댓글 INSERT(`jdbc.generated-keys=134`, row-affected 1), `tb_battle` update, HikariPool `commit` 이벤트까지 성공. HTTP span은 `status=200 / outcome=SUCCESS`. → **API는 성공했지만 알림 발행만 실패**한 구조.
  - 결론적으로 이벤트가 Kafka에 들어가지 못했으므로 chat 서비스는 소비할 것 자체가 없었고, 알림이 발송되지 않음.
- **확신도:** **높음** (트레이스가 발행 지점의 실패를 명시적으로 지목).
- **반증 데이터:** 없음. (단, chat 소비자 측 트레이스/로그가 없어 "발행만 실패"라는 인과의 소비자 측 확인은 미완.)

### 후보 2: 브로커 연결/가용성 문제로 메타데이터 미확보
- **근거:** "not present in metadata after 60000 ms" 에러는 토픽 미생성뿐 아니라 **브로커 접속 불가 / 컨트롤러 이상**으로 메타데이터를 갱신 못 할 때도 동일하게 발생. peer.service `"Apache Kafka: taPwALsEQRWSS9_C_Lyt3Q"` 로 클러스터 지정은 되어 있으나, 브로커 up/down이나 연결 성공 여부를 보여주는 데이터는 없음.
- **확신도:** **낮음~중간** (후보 1과 증상이 겹쳐 이 데이터만으로 토픽 부재와 구분 불가).
- **반증 데이터:** 없음(브로커 상태 지표 자체가 없음).

### 후보 3: chat 서비스(소비자) 측 문제
- **근거:** consumer lag 지표 `kafka_consumer_fetch_manager_records_lag` 는 **수집 실패로 skip**. chat 서비스의 JVM GC pause rate는 극히 낮고(≈0.00018s), HikariCP active/pending 모두 0 → chat이 과부하/멈춤 상태라는 근거는 **없음**. chat 관련 span·로그도 이 trace에 없음.
- **확신도:** **낮음** (오히려 소비자 이상 징후는 관측되지 않음).
- **반증 데이터:** 발행이 애초에 60초 만에 실패했으므로 메시지가 브로커에 도달하지 못함 → 소비자가 원인일 여지가 구조적으로 작음. chat GC/커넥션 지표 정상.

---

## 데이터 공백에 따른 확신도 보정
- Loki ERROR/WARN 및 traceId 매칭 로그 **둘 다 0건** (`totalEntriesReturned:0`) → 트레이스 외 로그 교차검증 불가.
- `kafka_consumer_fetch_manager_records_lag` **수집 실패** → 소비자 지연/정지 여부 확인 불가.
- 따라서 "토픽 부재냐 브로커 불가냐"의 **근본 원인 구분은 데이터 부족**. 발행 실패라는 사실 자체(→ 알림 미발송의 직접 원인)는 확신도 높음, 그 아래 근본 원인은 확신도 낮춤.

---

## 3. 권장 다음 조치
1. **토픽 존재 확인:** `kafka-topics.sh --bootstrap-server <broker> --describe --topic user.notifications` — 없으면 생성(또는 `auto.create.topics.enable` 정책 확인). content/chat 양쪽이 기대하는 토픽명이 정확히 `user.notifications`인지 대조.
2. **브로커 가용성 확인:** Kafka 브로커/컨트롤러 파드 상태(`kubectl get pods`), 브로커 로그, content-service에서 브로커로의 네트워크 도달성(방화벽/DNS/NetworkPolicy) 점검. 메타데이터 미확보가 토픽 부재인지 연결 불가인지 판별.
3. **누락 지표 복구:** `kafka_consumer_fetch_manager_records_lag` 스크레이프 실패 원인 조사(exporter/ServiceMonitor). 복구 후 chat 소비자 lag 확인.
4. **로그 파이프라인 점검:** 해당 시간창 Loki에 content-service 로그가 0건인 이유 확인(로그 수집 누락 가능성) — 발행 실패 스택트레이스를 로그로도 확보.
5. **애플리케이션 관점:** `NotificationEventListener.onNotification`의 발행 실패 시 재시도/아웃박스(outbox) 유무 확인 — 60초 블로킹 후 실패하면 해당 알림은 유실되므로, 재처리 메커니즘 설계 검토.
6. **재현/영향범위:** 같은 에러("Topic ... not present in metadata")가 이 trace 한 건인지 시간창 전반의 다수 요청에서 발생하는지 producer 에러율로 확인.
