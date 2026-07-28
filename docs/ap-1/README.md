# AP-1 — 댓글 201자: 검증 구멍 → varchar(200) 위반 → 500 (회차별 기록)

toy-content `docs/chaos/` RUNBOOK의 AP-1 문항. 인프라 무접촉 문항 — 주입은 250자 댓글
실요청 1건이고, 실패 INSERT는 롤백되므로 원복이 없다. CH·IN 계열(인프라 장애)과 달리
**애플리케이션 결함(검증 구멍)** 이 원인인 첫 문항. 폴더 구성 방식은
[`../ch-1/README.md`](../ch-1/README.md)와 동일.

## 회차 인덱스

| 회차 | 일시(UTC) | 주입 | 결말 | RCA 조사 | 점수 | 문서 |
|---|---|---|---|---|---|---|
| 1 | 07-27 14:13:26 | 250자 댓글 1건 (feed 145) | HTTP 500 (Data too long), 직후 정상 댓글 200 — 부분 장애 | O | **75** (앵커 v1) | [round-1.md](round-1.md) |
| 2 | 07-28 15:05:06 | 250자 댓글 1건 (feed 145) | 동일 | O · **자연어 진입** | **90** (앵커 v2) | [round-2.md](round-2.md) |

> **회차 2는 회차 1의 N=2 표본이 아니다.** 입력 모델(traceId → 자연어+탐색)·Loki 수집
> (0건 → 9줄)·출력 분량(2.6배)·앵커(v1 → v2)가 동시에 바뀌었다. **다른 구성의 첫 표본**으로
> 두고, §8.1 평균에 넣지 않는다 — 근거는 [round-2.md](round-2.md).

## 스크린샷 공통 팁

- Grafana 시간대 KST. 주입 = **KST 23:13:26** (1건 즉발, 창 없음).
- 이 문항의 그림 두 장: ① Tempo 에러 트레이스 — INSERT `query` span의 error 태그에
  MySQL 원문이 자백된 워터폴 + `notification-publish` 부재, ② Loki `Data too long` /
  `IntegrityViolation` 로그 (KST 23:13~23:15).
