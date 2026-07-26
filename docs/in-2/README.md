# IN-2 — Kafka 다운 × 알림 발행 경로 (회차별 기록)

toy-content `docs/chaos/` RUNBOOK의 IN-2 문항. CH-1(소비측 장애)과 대비쌍 — 같은 "알림 안 와요" 증상이 발행측에서 나면 **재시도·DLQ가 받아줄 수 없어 조용한 영구 유실**이 된다는 걸 실측하는 문항이다. 문항 전체 결과 종합은 toy-content `docs/chaos/RESULTS.md`, 폴더 구성 방식은 [`../ch-1/README.md`](../ch-1/README.md)와 동일.

## 회차 인덱스

| 회차 | 일시(UTC) | 다운 | 결말 | RCA 조사 | 문서 |
|---|---|---|---|---|---|
| 1 | 07-26 09:14:23~09:19:41 (5분 17초) | Kafka `docker stop` | 트리거 API 200, 알림 1건 영구 유실 (재시도·outbox 없음) | O | [round-1.md](round-1.md) |

## 스크린샷 공통 팁

- Grafana 시간대는 KST(UTC+9). 주입 창 = **KST 18:14:23 ~ 18:19:41**.
- 이 문항의 그림 세 장: ① Tempo 장애 트레이스(60,060ms error span + chat 부재), ② Loki `알림 발행 실패` ERROR 1건 + producer WARN 스팸, ③ `kafka_brokers` 그래프의 **선이 끊기는 구간**(0이 아니라 부재 — 그 공백 자체가 스샷 포인트).
