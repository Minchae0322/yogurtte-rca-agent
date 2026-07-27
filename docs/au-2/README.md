# AU-2 — auth 전면 다운 × content decoupling (회차별 기록)

toy-content `docs/chaos/` RUNBOOK의 AU-2 문항을 실제 프로덕션 클러스터에 주입한 기록. 회차마다
**장애 상황 → 실제 신호(Loki·Tempo 발췌) → 파악 원인 vs 실제 원인** 순서로 정리하고, Grafana에서
직접 스크린샷을 찍을 수 있게 traceId·쿼리·시간 범위를 붙였다.

문항 정의·정답지·채록 원본은 toy-content 쪽(`docs/chaos/RUNBOOK.md` §AU-2, `scenarios/AU-2/`),
문항 전체 결과 종합은 toy-content `docs/chaos/RESULTS.md`.

## 이 문항의 성격 — trace 의존도가 낮다

AU-2는 앞선 4문항(CH-1·CH-2·IN-2)과 신호 구조가 다르다.

- **실패 경로(로그인)는 트레이스가 아예 없다.** auth pod이 0이라 ingress가 503으로 끊는다 —
  트레이스를 생성할 서비스 자체가 존재하지 않는다.
- **정상 경로(content 피드)는 트레이스가 멀쩡하다.** user 캐시(TTL 10분) 히트라 content가 auth를
  호출조차 하지 않는다.

즉 "traceId 하나로 시작한다"는 v0의 입력 전제가 이 문항에서는 가장 불리하게 걸린다. AU-3(JWT
드리프트)과 같은 계열 — 메트릭·로그로 도달해야 하는 문항이고, 그래서 **에이전트의 수집 채널
결함이 성능에 그대로 반영되는 문항**이다. 이 조사를 baseline에 넣는 이유가 그것이다.

## 이 폴더의 구성

| 파일 | 내용 |
|---|---|
| `round-N.md` | 회차 정리 — **맨 위 "한눈 요약" 표에 실제 원인 / 에이전트 파악 원인 / 판정 / 토큰·비용**. 아래로 장애 상황, 신호 발췌, 원인 대조 |
| `round-N-rca-report.md` | 에이전트가 생성한 RCA 보고서 **원문** |
| `*.png` | Grafana 스크린샷 (round-N.md에 캡션과 함께 삽입됨) |

## 회차 인덱스

| 회차 | 일시(UTC) | 다운 | 결말 | RCA 조사 | 문서 |
|---|---|---|---|---|---|
| 0 | 07-26 | — | 무효 — 프로브 오염(`/feeds/scroll` size 미지정 NPE 500을 t2가 조용히 통과). 원인은 auth가 아니라 content 잠복 실버그 | — | (경위는 STATUS.md 07-26 활동 로그 + toy-content `1e7df3f`) |
| 1 | 07-27 01:20 | 2분 24초 | 로그인 503 전면 불가 / content 200 유지·작성자 실명 — 캐시 히트로 흡수 | O | [round-1.md](round-1.md) |

회차 1 조사 결과: **트레이스가 무신호인데 메트릭 시계열 단절만으로 auth 다운에 도달**했다
(§8 채점은 앵커 부적합으로 산출 불가 — 오귀인·조치는 만점). 평가 상세는
[AE-05](../findings/ae-05-rca-v0-au2-blind-eval.md).

## 스크린샷 공통 팁

- Grafana Explore의 시간대는 로컬(KST=UTC+9). 아래 회차 문서의 시간 범위는 KST로 적어뒀다.
- Tempo는 Explore → Tempo 데이터소스 → **TraceQL 탭에 traceId를 그대로 붙여넣으면** 워터폴이 뜬다.
- Loki 라벨은 `service_name`이다 (`application` 라벨은 존재하지 않음 — 메트릭 전용).
- traceId 앞 8자리는 unix epoch(초)의 16진수다. 이 회차의 증상 창 `01:22:12Z` = `6a662b5c` 부근.
