# NF-07. 알림 경로의 지연↔유실 경계 — Mongo 드라이버 대기 실측과 예외 삼킴 수정 검증

- 심각도: **높음**(수정 전) → 수정 배포·검증 완료 | 상태: 확정 (장애 주입 실측 ×2 + 트레이스 + 코드)
- 위치: toy-chat `UserNotificationConsumer` / MongoDB 드라이버 `serverSelectionTimeoutMS`
- 출처: CH-1 장애 주입 (toy-content `docs/chaos/` RUNBOOK) 2026-07-26 회차 1·3

## 배경 — 수정 전 코드의 구조적 유실

수정 전 `UserNotificationConsumer`는 원본·DLQ 리스너 모두 예외를 catch 후 `ack.acknowledge()` 해서 `DefaultErrorHandler`(FixedBackOff 1s×3 + DeadLetterPublishingRecoverer)에 예외가 도달할 수 없었다 — 재시도·DLQ 발행이 데드코드였고, Mongo 장애가 드라이버 대기(기본 30s)를 넘기면 ERROR 로그 1줄 남기고 **조용한 유실**. toy-chat `5eecb0a`(2026-07-25)가 양쪽 리스너를 rethrow로 수정, 같은 날 배포됐다. 아래 실측은 모두 수정 후 코드다.

## 실측 1 — 경계 안쪽: 다운 73초 → 지연 24.7초로 흡수 (회차 1, 07:53~07:55Z)

- 트레이스 `6a65bd43c41bfa6c5c18a89e1f855373`: `process-notification` 시작 후 첫 `user_notifications.insert`까지 **23.44초 무자식 공백** — 드라이버가 서버 셀렉션을 기다리는 동안 span 없는 블로킹. 공백의 끝(07:55:07)이 Mongo 복구(07:55:04) 직후.
- 예외 없음 → 재시도·DLQ 미발동, `알림 처리 완료` 07:55:08. **다운 < 드라이버 대기면 장애가 지연으로만 나타난다.**

## 실측 2 — 경계 바깥: 다운 4.5분 → DLQ 경유 3분 36초 지연 도착, 유실 0 (회차 3, 08:20~08:25Z)

트레이스 `6a65c38bea0e08d50df7b169594a2844` + Loki 로그로 재구성한 전체 체인:

| 시각(Z) | 사건 |
|---|---|
| 08:20:33 | Mongo 정지 (`mongodb_up` 1→0) |
| 08:21:31 | 트리거 댓글 → `publish user.notifications` 14ms 성공 |
| 08:22:01 / 08:22:32 / 08:23:03 / 08:23:34 | 소비 4회 실패 — 각각 정확히 **30.0초**(`serverSelectionTimeoutMS` 기본값), span `STATUS_CODE_ERROR` + `Connection refused` 원문, JDBC `acquired→rollback` |
| 08:23:34.7 | `publish user.notifications.dlq` span — 재시도 소진, DLQ 발행 |
| 08:23:35 | DLQ 재처리 리스너 1차 시도 → 08:24:05 실패 (Mongo 여전히 다운) |
| 08:25:04 | Mongo 복구 |
| 08:25:05 → 08:25:07 | 1분 백오프 후 재처리 재시도 → **성공** (이후 실패 로그 0건) |

수신자 관점 총 지연 **약 3분 36초, 유실 없음** — 수정 전 코드였다면 08:22:01 첫 실패에서 ack되어 영구 유실됐을 트래픽이다.

## 코드 근거

- toy-chat `UserNotificationConsumer.java` (5eecb0a 이후) — 원본 리스너 catch에서 `throw e` (`// DefaultErrorHandler 에게 위임 → 재시도(1s×3) → DLQ`), DLQ 리스너 catch에서 `throw e` (`notificationDlqListenerFactory`의 1분 백오프로 위임).
- 드라이버 대기 구간은 여전히 span이 없다(커맨드 리스너는 명령 시작 후부터 계측) — 실측 1의 "무자식 공백"이 그 형태.

## 남는 관측 과제

1. 30초 대기 구간이 계측 사각 — Mongo 드라이버 풀/서버셀렉션 메트릭(`mongodb.driver.*`) 또는 checkout span이 있어야 실측 1 유형(경계 안 지연)을 RCA가 확정할 수 있다 ([AE-01](ae-01-rca-v0-ch1-blind-eval.md) 후보 2가 가설에 머문 이유).
2. DLQ 발행-재처리 사이 trace 단절 → [NF-08](nf-08-dlq-trace-discontinuity.md). RCA가 "유실"과 "DLQ 복구 도착"을 구분 못 하는 원인.
3. DLQ 재처리 백오프가 1분 고정 — Mongo 장기 다운 시 재처리 실패 로그가 1분마다 누적된다. 알람 룰(`DLQ publish rate > 0`)과 함께 상한/지수 백오프 검토.

## 연관

- [NF-01](nf-01-consumer-holds-connection-during-dispatch.md) — 소비 4회 실패 동안 매번 JDBC 커넥션을 잡고 30초 블로킹 (acquired→rollback ×4). 컨슈머 동시성이 높아지면 이 대기가 커넥션 풀을 갉아먹는다.
- [NF-02](nf-02-fcm-call-no-timeout-no-span.md) — 동일한 "예외 삼킴" 패턴의 다른 인스턴스 (이쪽은 아직 미수정).
- [AE-01](ae-01-rca-v0-ch1-blind-eval.md) / [AE-02](ae-02-rca-v0-ch1-round3-eval.md) — 두 회차를 입력으로 한 rca-agent 블라인드 조사 기록.
