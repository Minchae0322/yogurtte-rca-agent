# NF-08. DLQ 경계에서 trace가 끊긴다 — "유실"과 "복구 도착"을 관측으로 구분할 수 없음

- 심각도: **중간** | 상태: 확정 (로그 실측), 전파 지점은 가설
- 위치: toy-chat DLQ 발행(`DeadLetterPublishingRecoverer`) ↔ DLQ 재처리 리스너 사이

## 관측 (CH-1 회차 3, 2026-07-26)

- 원본 트레이스 `6a65c38bea0e08d50df7b169594a2844`는 `publish user.notifications.dlq` span(08:23:34.7)에서 **끝난다**.
- 그 뒤의 실제 사건 — DLQ 재처리 시도(08:23:35 INFO), 재처리 실패(08:24:05 ERROR), 복구 후 성공(08:25:05~07) — 은 모두 로그에 **`traceId=NONE`** 으로 남았다. 원본 trace와 이어지지 않은 별개 컨텍스트다.
- 결과: 트레이스만 보면 "재시도 소진 → DLQ 발행"이 마지막 장면이라 **미발송(유실)으로 읽힌다**. 실제로는 3분 36초 뒤 도착했다 ([NF-07](nf-07-notification-delay-loss-boundary.md) 실측 2).

## 실증된 영향 — RCA 오판

[AE-02](ae-02-rca-v0-ch1-round3-eval.md)에서 rca-agent는 원인(Mongo 다운)을 확신도 높음으로 맞히고도 영향 판정을 "**알림은 지연이 아니라 미발송(유실) 상태**", 조치를 "DLQ 재처리 컨슈머가 없다면 수동 재발행 필요"로 냈다 — 재처리 리스너가 존재하고 이미 성공했는데도. 에이전트가 볼 수 있는 데이터(원본 trace) 안에서는 합리적인 결론이라, 이건 모델이 아니라 **관측 구조의 문제**다.

## 메커니즘 (가설 + 확인 방법)

DLQ 발행 시 trace 컨텍스트 헤더가 전파되지 않거나, 재처리 리스너의 observation이 새 trace를 시작하는 것으로 보인다. 확인: DLQ 토픽 메시지의 헤더 덤프(`kafka-console-consumer --property print.headers=true`)에서 `traceparent`/`b3` 유무 → 있으면 리스너 쪽 컨텍스트 복원 문제, 없으면 발행 쪽 전파 문제.

## 검증 가능한 개선 예측

DLQ 발행→재처리가 원본 trace로 이어지면(또는 최소한 원본 traceId를 메시지 헤더·로그에 보존하면), CH-1 유형 장애에서 RCA가 "DLQ 이후" 체인을 따라가 **유실/복구를 데이터로 구분**할 수 있다. 검증: CH-1 재실행 후 원본 traceId 하나로 Tempo에서 재처리 span까지 조회되는지, AE 재조사에서 영향 판정이 바뀌는지.

## 연관

- [NF-07](nf-07-notification-delay-loss-boundary.md) — 이 단절 때문에 경계 바깥 시나리오의 결말(복구 도착)이 원본 trace에 안 보인다.
- [AE-02](ae-02-rca-v0-ch1-round3-eval.md) — 오판 실증.
