# AE-01. rca-agent v0 × CH-1(Mongo 다운) — 첫 장애 주입 블라인드 조사 기록

- 상태: 실행 기록 확정. **§8 채점 완료(2026-07-27) → 결과: 채점 불가(앵커 부적합)** — [채점 대장](../scoring/README.md#ch-1-이관-x-구-회차-1--채점-불가-앵커-부적합). 여기는 사실 관계와 도구 결함만 기록한다.
- 피조사 장애: toy-content `docs/chaos/` CH-1, 2026-07-26 07:53:51~07:55:04Z Mongo 73초 정지. 실제 증상: 알림 24.7초 지연 도착, 유실 없음 ([NF-07](nf-07-notification-delay-loss-boundary.md)).

## 실행 측정치

| 항목 | 값 |
|---|---|
| 입력 | traceId `6a65bd43c41bfa6c5c18a89e1f855373` (증상 창 트리거 요청 자신의 trace — 중립 진입점) + 질문 "왜 알림이 늦었어?" |
| provider / prompt | claude-cli / `prompts/system-prompt.md` (v0 단발 호출) |
| tokens · cost | in **44,798** / out **9,162** · **$1.0845** |
| elapsed | **135.4s** (tempo 0.56 · loki 0.19 · mimir 0.29 · assemble 0.002 · llm **134.4**) |
| coverage | trace 25,768B/29 spans · logs errwarn 3,957B·traceId 3,957B(실질 0건, 아래 결함 1) · metrics 3 수집/1 누락 |
| 산출물 | `reports/6a65bd43c41bfa6c5c18a89e1f855373-20260726T080237.{md,json}` + `reports/raw/` |

## 에이전트가 해낸 것

- **지연 위치를 span 단위로 특정**: `process-notification` 시작 → 첫 Mongo insert 사이 **23.44초 무자식 공백** (후보 1, 확신도 높음). 업스트림 72.7ms·Kafka 핸드오프 1.45ms·JDBC acquire 1.7ms를 근거로 브로커/풀 계열을 정량 반증.
- **오귀인 회피**: consumer lag(핸드오프 1.45ms), HikariCP 고갈(pending 0), GC 스톨(0.0028s/s — "23초를 설명하기엔 두 자릿수 부족")을 수치로 배제. 확신도·반증 데이터 형식을 지킴.
- **정답 직전 도달**: 후보 2 "첫 Mongo 작업 직전 대기 — 커넥션/서버 셀렉션 지연 가설"은 실제 원인(Mongo 다운 중 드라이버 대기)과 일치. 단 **확신도 낮음에 머물렀고 "Mongo가 죽어 있었다"는 사실에는 도달 못 함**.

## 정답에 못 간 이유 = 도구 결함 3건 (모델 문제 아님)

1. **Loki 셀렉터 불일치 — 로그 수집 0건.** 기본값 `{app=~"content|auth|chat"}` (`CollectProperties.java:20-27`, `application.yml collect.app-label/apps`)인데 실제 Loki 라벨은 `service_name`, 값은 `content-service|auth-service|chat-service`다(2026-07-25 toy-content chaos 작업에서 동일 이슈 실측). `| logfmt | level=~"ERROR|WARN"` 파이프라인도 Spring 텍스트 포맷과 안 맞는다. 창 안에 traceId 매칭 DEBUG 로그(consume 흐름, `알림 처리 완료` 07:55:08 포함)가 실재했으나 에이전트는 "로그 0건"으로 조사했고, 스스로도 조치 2에서 "로그 파이프라인 점검"을 권했다 — 자기 결함을 가리킨 셈.
2. **원인 계열 메트릭 부재.** `collect.metric-queries`에 인프라 up 계열이 없다. `mongodb_up`은 같은 창에서 1→0→1로 실측됐다 — 목록에 있었으면 후보 2가 즉시 확정된다. `kafka_consumer_fetch_manager_records_lag`도 exporter가 노출하는 이름은 `..._lag_max`라 매 조사마다 누락으로 뜬다.
3. **리포트 절대 시각이 -120초 밀림.** 리포트의 모든 절대 시각이 실제보다 정확히 `window-padding-seconds`(120s)만큼 이르다 — 리포트 "receive 종료 07:53:08.353" ↔ 실제 `알림 처리 완료` 로그 07:55:08, traceId epoch 프리픽스 `0x6a65bd43`=07:54:43 ↔ 리포트 시작 07:52:43. duration·순서는 정확. 가설: 어셈블 컨텍스트가 시각을 조회창 시작 기준으로 표현. 확인 방법: `reports/raw/`의 어셈블 컨텍스트에서 span 시각 표기 대조.

## 다음 액션

- [ ] 결함 1·2는 설정 수준 수정(app-label/apps/metric-queries) — v0.1로 올리고 **같은 traceId 재조사**로 델타 측정 (튜닝 루프 데이터로 최적)
- [ ] 결함 3은 raw 컨텍스트로 발생 지점 확정 후 수정
- [ ] 정식 §8 채점: CH-1 answer.md 앵커 기준, 채록과 하루 분리 후 수행 (이 문서는 채점 입력이 아니라 실행 로그다)
