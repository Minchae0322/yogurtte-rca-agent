# CH-1 — MongoDB 다운 × 알림 경로 (회차별 기록)

toy-content `docs/chaos/` RUNBOOK의 CH-1 문항을 실제 프로덕션 클러스터에 주입한 기록. 회차마다 **장애 상황 → 실제 신호(Loki·Tempo 발췌) → 파악 원인 vs 실제 원인** 순서로 정리하고, Grafana에서 직접 스크린샷을 찍을 수 있게 traceId·쿼리·시간 범위를 붙였다.

문항 정의·정답지·채록 원본은 toy-content 쪽(`docs/chaos/RUNBOOK.md`, `scenarios/CH-1/`), 문항 전체 결과 종합은 toy-content `docs/chaos/RESULTS.md`.

## 이 폴더의 구성

| 파일 | 내용 |
|---|---|
| `round-N.md` | 회차 정리 — **맨 위 "한눈 요약" 표에 실제 원인 / 에이전트 파악 원인 / 판정 / 토큰·비용**. 아래로 장애 상황, 스샷, 신호 발췌 |
| `round-N-rca-report.md` | 에이전트가 생성한 RCA 보고서 **원문** (토큰·비용은 문서 상단 표, 원인 후보 랭킹·근거는 본문) |
| `*.png` | Grafana 스크린샷 (round-N.md에 캡션과 함께 삽입됨) |

## 회차 인덱스

| 회차 | 일시(UTC) | 다운 | 결말 | RCA 조사 | 문서 |
|---|---|---|---|---|---|
| 0 | 07-25 13:59 | 16초 | 무효 — 측정 라벨 버그 + 트리거 미도달(CDN 404 마스킹) + 대기 부족 | — | (경위는 toy-content observability.md 07-25 절) |
| 1 | 07-26 07:53 | 73초 | 알림 24.7초 지연 도착, 유실 0 | O | [round-1.md](round-1.md) |
| 2 | 07-26 08:13 | 4분 59초 | 무효 — 트리거를 원복 3초 전 발사 | — | (교훈만: 대기는 트리거 **후**에) |
| 3 | 07-26 08:20 | 4분 31초 | 재시도 4회 → DLQ → 복구 후 재처리 성공, 3분 36초 지연·유실 0 | O | [round-3.md](round-3.md) |

## 스크린샷 공통 팁

- Grafana Explore의 시간대는 로컬(KST=UTC+9). 아래 회차 문서의 시간 범위는 KST로 적어뒀다.
- Tempo는 Explore → Tempo 데이터소스 → **TraceQL 탭에 traceId를 그대로 붙여넣으면** 워터폴이 뜬다.
- Loki 라벨은 `service_name`이다 (`application` 라벨은 존재하지 않음 — 메트릭 전용).
- traceId 앞 8자리는 unix epoch(초)의 16진수다. 예: `6a65c38b` = 08:21:31Z — 스샷의 시간축과 대조할 때 유용.
