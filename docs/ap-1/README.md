# AP-1 — 댓글 201자: 검증 구멍 → varchar(200) 위반 → 500 (회차별 기록)

toy-content `docs/chaos/` RUNBOOK의 AP-1 문항. 인프라 무접촉 문항 — 주입은 250자 댓글
실요청 1건이고, 실패 INSERT는 롤백되므로 원복이 없다. CH·IN 계열(인프라 장애)과 달리
**애플리케이션 결함(검증 구멍)** 이 원인인 첫 문항. 폴더 구성 방식은
[`../ch-1/README.md`](../ch-1/README.md)와 동일.

## 회차 인덱스

| 회차 | 일시(UTC) | 주입 | 결말 | RCA 조사 | 문서 |
|---|---|---|---|---|---|
| 1 | 07-27 14:13:26 | 250자 댓글 1건 (feed 145) | HTTP 500 (Data too long), 직후 정상 댓글 200 — 부분 장애 | O | [round-1.md](round-1.md) |

## 스크린샷 공통 팁

- Grafana 시간대 KST. 주입 = **KST 23:13:26** (1건 즉발, 창 없음).
- 이 문항의 그림 두 장: ① Tempo 에러 트레이스 — INSERT `query` span의 error 태그에
  MySQL 원문이 자백된 워터폴 + `notification-publish` 부재, ② Loki `Data too long` /
  `IntegrityViolation` 로그 (KST 23:13~23:15).
