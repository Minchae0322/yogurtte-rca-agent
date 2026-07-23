# NF-01. Kafka 컨슈머가 DB 커넥션·트랜잭션을 잡은 채 ~1초짜리 외부 호출을 수행한다

- 심각도: **높음** | 상태: 확정 (트레이스 + 코드)
- 위치: toy-chat `UserNotificationService#processNotification` → `PushDispatcher.dispatch`

## 관측 (트레이스 `6a5dc9c1990469248cfea377e1d7b4a0`)

- chat의 `connection` span(HikariPool)이 **1,105ms** 지속: 이벤트 `acquired` @395.7ms →
  `commit` @1,497.1ms.
- 그 안의 `push-dispatcher#dispatch`가 **996ms**. 계측된 자식은 Redis `KEYS`(0.9ms) 하나뿐.
- **이 connection span 아래에 JDBC query span이 하나도 없다** — 쿼리 없이 커넥션과
  트랜잭션만 열어둔 채 외부 작업(FCM 발송, NF-02)을 기다린 것이다.
- Kafka `receive` 전체 1,107ms 중 dispatch가 **90%**.

## 코드 근거

`UserNotificationService.java:106` — 알림 처리 트랜잭션 흐름 안에서
`pushDispatcher.dispatch(...)`를 호출한다. dispatch는 Redis 조회 → 토큰 조회 →
`pushProvider.sendToTokens(...)`(외부 FCM 동기 호출)로 이어진다 (`PushDispatcher.java:27-41`).

## 메커니즘 — 왜 지금이 아니라 부하 때 무너지는가

1. **처리량 상한**: Kafka는 파티션당 직렬 소비다. 메시지당 1.1초면 파티션당 처리량이
   초당 ~1건으로 제한된다. 트래픽 스파이크 시 lag이 즉시 누적된다 (현재 lag=0인 것은
   트래픽이 낮아서다).
2. **풀 고갈 결합**: 커넥션을 1.1초 점유하므로, 소비 동시성이 커지면 HikariCP 풀이
   외부 API 지연에 인질로 잡힌다. FCM이 느려지는 순간 **DB 커넥션 풀이 같이 마른다** —
   장애 전파 경로가 생긴다.
3. **관측 함정**: `hikaricp_connections_active`는 15초 스크레이프라 1.1초 점유가 샘플
   사이에 끝난다. 메트릭이 안전해 보여도 문제가 없는 게 아니다.

## 개선 방향

1. 트랜잭션 경계 분리: DB 작업(알림 저장)을 먼저 커밋하고, dispatch는 트랜잭션·커넥션
   밖에서 수행 (`@TransactionalEventListener(AFTER_COMMIT)` 또는 커밋 후 큐잉).
2. 또는 발송을 별도 실행기(스레드풀/비동기)로 넘겨 컨슈머 스레드를 즉시 반환.

## 개선 검증 방법 (측정 가능한 예측)

- `connection` span 지속시간: 1,105ms → **50ms 이하** (실 DB 작업만)
- `receive` span: 1,107ms → dispatch 비동기화 시 ~100ms 수준
- C2(느린 컨슈머)·C3(FCM 지연) chaos 시나리오에서 FCM 지연 주입 시
  `hikaricp_connections_pending`이 **오르지 않아야** 함 (현재 구조면 오른다)
