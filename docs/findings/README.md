# 정상 상황 관측 Findings

장애가 아닌 **정상 동작 상태**의 시스템을 관측해 확보한 개선 포인트와 결함의 기록이다.
"정상인데 왜 기록하나"에 대한 답: 유일한 실전 조사가 정상 상황이었는데도 구조적 문제
4건이 나왔고, 실서비스 읽기 API 79회 순회에서 고정 결함 3군이 나왔다. 정상 상황은
개선점의 서식지이며, 여기 기록된 목록은 평가 전략(strategy.md Phase 2)의 **N-트랙
정답지(ground truth)** 로 그대로 쓰인다.

## 수록 기준 (시니어 필터)

세 조건을 모두 만족하는 것만 기록한다:

1. **실측 근거** — span 타이밍, 재현된 상태코드, 코드 위치(file:line) 중 최소 둘
2. **메커니즘 설명** — 지금 수치가 아니라 "부하가 오르면 무엇이 무너지는가"
3. **검증 가능한 개선 예측** — 고치면 어떤 지표가 어떻게 변해야 하는지

추측만 있는 항목은 "가설"로 명시하고 확인 방법을 붙이거나, 탈락시켰다.

## 관측 방법 (세 경로)

| 경로 | 도구 | 산출 |
|---|---|---|
| 실전 트레이스 정밀 분석 | 트레이스 `6a5dc9c1990469248cfea377e1d7b4a0` (댓글 작성→알림, 2 services / 30 spans / 1.26s) | NF-01~04의 타이밍 근거 |
| rca-agent 리뷰 모드 | `/investigate mode=review` ($1.23, out 12,100 tok) | 수동 분석 재현 + 신규 발견 (NF-04 갭·락, NF-06 가설 제기) |
| 실서비스 API 순회 | `scripts/api-sweep.sh` (GET 전용 79회, 2026-07-24) | DF-01 결함 3군, NF-05 라우팅 이상, 지연 baseline |

## Findings 인덱스

| ID | 제목 | 심각도 | 상태 |
|---|---|---|---|
| [NF-01](nf-01-consumer-holds-connection-during-dispatch.md) | Kafka 컨슈머가 DB 커넥션·트랜잭션을 잡은 채 ~1초 외부 호출 | 높음 | 확정 (트레이스+코드) |
| [NF-02](nf-02-fcm-call-no-timeout-no-span.md) | FCM 동기 호출 — 타임아웃 미설정, span 부재, 예외 삼킴 | 높음 | 확정 (트레이스+코드) |
| [NF-03](nf-03-redis-keys-on-hot-path.md) | 알림 hot path의 Redis `KEYS` — 코드베이스에 5개소 | 중간 | 확정 (트레이스+코드) |
| [NF-04](nf-04-comment-tx-coupling.md) | 댓글 트랜잭션에 경험치·비관적 락 결합, 미계측 갭 85ms | 중간 | 확정 (트레이스+코드) |
| [NF-05](nf-05-gateway-routing-anomaly.md) | 같은 리소스가 두 게이트웨이 경로에서 다른 응답 | 중간 | 관측 확정, 원인 미해명 |
| [NF-06](nf-06-shared-db-schema.md) | chat 서비스가 `content` DB 스키마 사용 (경계 공유 의심) | 중간 | 로컬 설정 확정, prod 미확정 |
| [DF-01](df-01-sweep-500-defects.md) | 읽기 API 순회에서 발견된 500 고정 결함 3군 | 결함 | 2회 재현 |

## 증거 원본

- [evidence-sweep-20260724.tsv](evidence-sweep-20260724.tsv) — API 순회 원본 (35건: 상태/시간/크기)
- [sample-review-report.md](../sample-review-report.md) — 리뷰 모드 리포트 전문 (NF-01~04·06 관측의 에이전트 산출)
- [sample-report.md](../sample-report.md) — rca 모드 리포트 전문

## 지연 baseline (읽기 API, 2026-07-24 실측)

개선 전후 비교의 기준선. 전체 원본은 위 evidence TSV.

| 엔드포인트 | 응답시간 | 비고 |
|---|---|---|
| `GET /api/content/feeds/{id}/comments` | **0.58s** | 읽기 중 최다 지연 — NF-04의 조회면 |
| `GET /api/chat/notifications` | 0.34s | |
| `GET /api/content/feeds/following` | 0.20~0.30s | |
| `GET /api/chat/v1/chat/rooms` | 0.23~0.25s | |
| `GET /api/content/feeds/scroll` | 0.18~0.21s | |
| 그 외 대부분 | 0.06~0.13s | |

교차 검증: 관측 기간 `kafka_consumergroup_lag` 전 파티션 0 — 알림 경로의 지연(NF-01·02)은
적체가 아니라 처리 내부 문제라는 결론과 정합.
