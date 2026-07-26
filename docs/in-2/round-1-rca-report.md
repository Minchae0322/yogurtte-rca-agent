# RCA Report — `6a65d0391efd3125490830158dec0de4`

| 항목 | 값 |
|---|---|
| 모드 | rca |
| 질문 | 왜 알림이 안 왔어? |
| 시각 | 2026-07-26T09:34:19.028730Z |
| provider | claude-cli |
| prompt | `./prompts/system-prompt.md` |
| tokens | in 39202 / out 8266 · cost $0.9237 |
| elapsed | total 123386ms (tempo 482 · loki 174 · mimir 266 · assemble 1 · llm 122452) |

## 수집 범위 (Coverage)

- **window**: 2026-07-26T09:13:37.339541Z ~ 2026-07-26T09:18:37.453965Z (300s)
- **trace**: 14,147B / 15 spans
- **logs**: errwarn=3,962B · traceId=3,962B
- **metrics**: 3 수집, 누락 [kafka_consumer_fetch_manager_records_lag]
- **context**: 32,221 chars (~8,055 tok 추정)

## 수집 실패/누락

- Metric 'kafka_consumer_fetch_manager_records_lag' returned no series in this window; skipped.

---

## 1. 원인 후보 랭킹 (최대 3개)

1. **Kafka 토픽 `user.notifications` 부재(삭제/미생성 + auto-create 비활성)로 producer 발행 실패 → 이벤트 유실 → chat이 소비할 메시지 자체가 없었음**
2. **`user.notifications` 파티션 리더 불가용(브로커 일부 다운/리더 선출 실패)으로 메타데이터 미확보**
3. **content-service → Kafka 네트워크/클라이언트 설정 문제(bootstrap 주소 오류, NetworkPolicy 등)**

공통 전제(확실한 부분): 알림이 안 온 직접 원인은 **content-service의 Kafka 발행 실패**다. 댓글 저장은 성공했지만(HTTP 200, insert 커밋), 알림 이벤트가 Kafka에 도달하지 못해 chat-service가 소비·발송할 것이 없었다. 트레이스에 chat-service span이 전혀 없다는 점이 이를 뒷받침한다. 후보 1~3은 "왜 발행이 실패했는가"에 대한 하위 원인 감별이다.

## 2. 후보별 근거

### 후보 1: 토픽 `user.notifications` 부재
- **근거:**
  - Producer span `publish user.notifications`(SPAN_KIND_PRODUCER, messaging.system=kafka)가 `STATUS_CODE_ERROR`, 에러 원문: **"Topic user.notifications not present in metadata after 60000 ms."** — 시작 09:13:37.393Z, 종료 09:14:37.442Z, 정확히 60초(= KafkaProducer `max.block.ms` 기본값) 대기 후 타임아웃. 이 문구는 브로커가 해당 토픽의 메타데이터를 60초 내내 반환하지 못했다는 뜻으로, 토픽이 존재하지 않을 때의 전형적 에러다.
  - 부모 span `notification-publish`(`com.example.toycontent.app.notification.NotificationEventListener.onNotification`)도 `error="Send failed"`로 종료 — 발행이 최종 실패했고 재시도/아웃박스 흔적이 트레이스에 없다.
  - 반면 HTTP 서버 span `http post /battles/{battleId}/items/{itemId}/comments`는 status=200, outcome=SUCCESS, 54.8ms에 종료. insert 커밋 완료(`jdbc.generated-keys=93`, connection span의 `commit` 이벤트 09:13:37.391Z). 즉 "댓글은 달렸는데 알림만 안 온" 사용자 증상과 정확히 일치한다.
  - `peer.service="Apache Kafka: taPwALsEQRWSS9_C_Lyt3Q"` — 클러스터 ID가 기록되어 있어 브로커 연결 자체는 성립했을 가능성이 높고, 그렇다면 "연결은 되는데 토픽 메타데이터만 없음" = 토픽 부재 쪽에 무게가 실린다.
- **확신도:** 중간 (발행 실패가 원인이라는 것 자체는 높음. 다만 "토픽 부재 vs 리더 불가용"을 가를 브로커 측 데이터가 전무해 하위 원인으로서는 중간)
- **반증 데이터:** 없음. 단, Loki 로그 0건·consumer lag 메트릭 부재로 브로커/토픽 상태를 직접 확인한 데이터가 없다 — **데이터 부족**으로 토픽 목록/설정(`auto.create.topics.enable`) 확인이 필요하다.

### 후보 2: 파티션 리더 불가용(브로커 장애)
- **근거:** 동일 에러 "Topic ... not present in metadata after 60000 ms."는 토픽은 존재하되 파티션 리더가 없을 때(브로커 다운, ISR 부족)도 발생한다. 후보 1과 동일한 span 증거를 공유한다.
- **확신도:** 낮음~중간
- **반증 데이터:** `peer.service`에 클러스터 ID가 기록됨 — 최소 한 브로커와의 통신은 성립했음을 시사한다(전면 브로커 장애와는 배치). 브로커 메트릭·로그가 없어 판별 불가 — **데이터 부족**: UnderReplicatedPartitions/OfflinePartitionsCount, 브로커 파드 상태, controller 로그 수집 필요.

### 후보 3: content-service ↔ Kafka 네트워크/설정 문제
- **근거:** 메타데이터 타임아웃은 bootstrap.servers 오설정이나 NetworkPolicy 차단으로 메타데이터 요청이 도달하지 못할 때도 동일하게 나타난다.
- **확신도:** 낮음
- **반증 데이터:** `peer.service="Apache Kafka: taPwALsEQRWSS9_C_Lyt3Q"`로 클러스터 ID가 확보된 정황은 연결 실패 가설과 배치된다. 또한 리소스 측면 이상 없음 — hikaricp_connections_active/pending 전 서비스 0, GC pause rate 무시 가능 수준(최대 0.0005s/s) — 이라 파드 자체 이상 징후도 없다.

**배제된 것:** chat-service 소비 실패는 원인이 아니다. 메시지가 Kafka에 진입하지 못했으므로 소비 단계는 도달조차 안 했다(트레이스에 chat span 없음). DB/커넥션 풀/GC 문제도 메트릭상 배제.

**결론 확신도에 대한 주의:** Loki가 ERROR/WARN 0건, traceId 매칭 0건을 반환했다. 60초 블록 후 실패한 이벤트인데 로그가 한 줄도 없다는 것은 앱이 조용했다기보다 **로그 수집 파이프라인 자체의 공백**일 가능성이 높다. `kafka_consumer_fetch_manager_records_lag`도 시리즈가 없다. 브로커 측 관측 데이터가 전무하므로 하위 원인(1 vs 2 vs 3) 판별의 확신도를 한 단계 낮췄다.

## 3. 권장 다음 조치

1. **토픽 존재/리더 확인 (최우선):** Kafka 파드에서 `kafka-topics.sh --describe --topic user.notifications` 실행. 토픽이 없으면 정책에 맞는 파티션/RF로 생성하고, `auto.create.topics.enable` 설정과 최근 토픽 삭제 이력(클러스터 재배포, Helm 릴리스 등 09:13Z 이전 변경)을 확인.
2. **브로커 상태 점검:** 브로커 파드 Running 여부, controller 로그, OfflinePartitionsCount/UnderReplicatedPartitions 확인.
3. **연결성 검증:** content-service 파드(`content-service-649545dc7b-6chbr`)에서 bootstrap.servers 값 대조 후 브로커 포트로 접속 테스트.
4. **유실 이벤트 처리 결정:** battle 22 / item 125의 댓글(id 93) 알림 이벤트는 이미 유실됐다(재시도·아웃박스 없음). 토픽 복구 후 재발행할지 결정. 재발 방지로 outbox 패턴 또는 발행 실패 재시도+DLQ 도입 검토. 리스너가 `max.block.ms` 60초 동안 블록된 점도 개선 대상.
5. **관측성 공백 수리:** (a) Loki에 서비스 로그가 전혀 안 잡히는 문제(traceId 매칭조차 0건) — 수집 에이전트/라벨 셀렉터 점검. (b) chat-service consumer lag 메트릭 노출(현재 시리즈 없음) — 이 두 공백이 이번 판별을 막았다.
6. **복구 검증:** 토픽 정상화 후 동일 플로우(댓글 작성)로 재현하여 producer span 성공 + chat-service consumer span 생성 + 알림 발송까지 트레이스로 확인.
