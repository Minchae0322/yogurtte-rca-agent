# CH-2 — chat 컨슈머 정지 × lag 누적 (회차별 기록)

toy-content `docs/chaos/` RUNBOOK의 CH-2 문항. IN-2와 대비쌍 — 같은 "알림 안 와요"인데 발행측은 무결하고 **소비자만 전멸**하면, 에러가 아니라 **lag 누적이라는 메트릭**만이 신호가 되고, 복구 시 밀린 알림이 몰아서 도착한다(유실 0). 폴더 구성 방식은 [`../ch-1/README.md`](../ch-1/README.md)와 동일.

## 회차 인덱스

| 회차 | 일시(UTC) | 다운 | 결말 | RCA 조사 | 문서 |
|---|---|---|---|---|---|
| 1 | 07-26 09:43:51~09:52:56 (9분 5초) | `kubectl scale deploy/chat-service --replicas=0` | lag 25건 누적 → 복구 후 일괄 소비, 지연 4~10분·유실 0 | O | [round-1.md](round-1.md) |

## 스크린샷 공통 팁

- Grafana 시간대 KST. 주입 창 = **KST 18:43:51 ~ 18:52:56**, 소진 완료 ≈ 18:54:05.
- 이 문항의 그림 세 장: ① Tempo 장애 트레이스 — publish와 consume 사이 **4분의 빈 구간**이 한 트레이스 안에 보이는 워터폴, ② `kafka_consumergroup_lag{consumergroup="notification-processors"}` — 계단식 상승 후 수직 낙하 (KST 18:43~18:56), ③ Loki 복구 직후 `알림 처리 완료` 몰림 (KST 18:53~18:55).
