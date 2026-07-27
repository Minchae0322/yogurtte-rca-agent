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
| 실전 트레이스 정밀 분석 | 트레이스 `6a5dc9c1990469248cfea377e1d7b4a0` — 2026-07-20 16:09:53 KST 시작, `content-service: http post /feeds/{feedId}/comments` (댓글 작성→알림, 2 services / 30 spans / 1.26s) | NF-01~04의 타이밍 근거 |
| rca-agent 리뷰 모드 | `/investigate mode=review` ($1.23, out 12,100 tok) | 수동 분석 재현 + 신규 발견 (NF-04 갭·락, NF-06 가설 제기) |
| 실서비스 API 순회 | `scripts/api-sweep.sh` (GET 전용 79회, 2026-07-24) | DF-01 결함 3군, NF-05 라우팅 이상, 지연 baseline |
| 장애 주입 블라인드 조사 | toy-content chaos CH-1(Mongo 73s 정지) → `/investigate` (2026-07-26, $1.08, in 44,798/out 9,162 tok, 135s) | NF-07 실측 확정, AE-01 도구 결함 3건 |

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
| [NF-07](nf-07-notification-delay-loss-boundary.md) | 알림 지연↔유실 경계(드라이버 대기 30s) 실측 — 예외 삼킴 수정으로 경계 밖도 DLQ 경유 도착 | 높음 | 확정·개선 검증 완료 (주입 ×2) |
| [NF-08](nf-08-dlq-trace-discontinuity.md) | DLQ 경계에서 trace 단절 — RCA가 유실/복구 도착을 구분 불가 (오판 실증) | 중간 | 로그 확정, 전파 지점 가설 |
| [NF-09](nf-09-user-fallback-no-traceid.md) | user fallback 실패 로그가 `traceId=NONE` — 로그는 있는데 조사에 연결 불가 + 집계 지표 부재 | 높음 | 확정 (로그 실측 + 코드 위치 + 상태코드) |
| [NF-10](nf-10-content-db-connection-held-during-external-call.md) | content 읽기 경로가 DB 커넥션을 쥔 채 외부 HTTP 호출 (NF-01 계열) | 중간 | 확정 (트레이스 부모-자식 + 타이밍) |
| [NF-11](nf-11-feed-scroll-n-plus-one.md) | `/feeds/scroll` N+1 — 피드 11건에 쿼리 23회. NF-10과 같은 커넥션 위에서 곱해진다 | 중간 | 확정 (워터폴 + **에이전트 2회 독립 지적**) |
| [AE-01](ae-01-rca-v0-ch1-blind-eval.md) | rca-agent v0 × CH-1 회차 1 — 위치 특정 성공, 원인 확정 실패(도구 결함 3건) | 도구 | **§8 채점 불가**(앵커 부적합) |
| [AE-02](ae-02-rca-v0-ch1-round2-eval.md) | rca-agent v0 × CH-1 회차 2 — 원인 확정 성공(계측 보강 효과), 영향 판정 오판(NF-08) | 도구 | **§8 채점 80/100** (N=1, 인용 불가) |
| [AE-03](ae-03-rca-v0-in2-blind-eval.md) | rca-agent v0 × IN-2 — 유실 판정 정답, 하위 원인 감별 실패(브로커 측 데이터 전무) | 도구 | **§8 채점 80/100** (N=1, 인용 불가) |
| [AE-04](ae-04-rca-v0-ch2-blind-eval.md) | rca-agent v0 × CH-2 — 메트릭 고고학으로 본질 정답 (※ "-120s는 어셈블러 버그" 결론은 AE-05에서 정정됨) | 도구 | **§8 채점 불가**(앵커 부적합 — 근본원인·오귀인은 만점) |
| [AE-05](ae-05-rca-v0-au2-blind-eval.md) | rca-agent v0 × AU-2 — **트레이스 무신호에서 메트릭 단절만으로 정답**, 복구 판정은 수집 창 밖이라 오판 | 도구 | **§8 채점 불가**(앵커 부적합 — 오귀인·조치는 만점) |
| [AE-06](ae-06-rca-v0-au4-blind-eval.md) | rca-agent v0 × AU-4 — **앵커가 틀리고 에이전트가 맞았다**(3s timeout이 아니라 23.5ms RST). 원인을 직접/상위로 계층 분리 | 도구 | **§8 채점 불가**(앵커 사실 오류 — 채점된 3항목 **70/70 만점**) |

## 장애 주입 회차별 기록

장애 상황, Loki·Tempo 실제 신호 발췌, 파악 원인 vs 실제 원인, 스크린샷용 traceId·쿼리를 문항별 디렉토리에 회차 단위로 기록한다: [`../ch-1/`](../ch-1/README.md) (Mongo 다운, 2회) · [`../ch-2/`](../ch-2/README.md) (컨슈머 정지, 1회) · [`../in-2/`](../in-2/README.md) (Kafka 다운, 1회) · [`../au-2/`](../au-2/README.md) (auth 다운·캐시 히트, 1회) · [`../au-4/`](../au-4/README.md) (auth 다운·캐시 만료, 1회)

회차별 §8 점수와 판정 근거는 [`../scoring/`](../scoring/README.md)에 모은다. **조사를 돌렸으면 채점까지 하는 것이 규칙이다.**

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
