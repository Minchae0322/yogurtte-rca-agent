# AU-4 — auth 다운 × user 캐시 만료 (회차별 기록)

toy-content `docs/chaos/` RUNBOOK의 AU-4 문항을 실제 프로덕션 클러스터에 주입한 기록.
AU-2에서 파생 신설된 문항으로, **주입은 AU-2와 동일**(`auth-service --replicas=0`)하고 차이는
**10분+ 유지**(user 캐시 TTL 경과)와 판정이다.

문항 정의·정답지·앵커는 toy-content(`docs/chaos/RUNBOOK.md` §6 AU-4, `scenarios/AU-4/`).

## AU-2와의 대비 — 같은 주입, 정반대 트레이스

이 문항의 가치는 [AU-2](../au-2/README.md)와 나란히 놓을 때 나온다. 주입이 같은데
**캐시 상태 하나로 관측 가능성이 뒤집힌다.**

| | AU-2 (캐시 히트) | AU-4 (캐시 만료) |
|---|---|---|
| content → auth 호출 | **안 함** | 함 (배치 조회) |
| baseline vs symptom 트레이스 | **구조 동일** (65 spans, 구별 불가) | **74 → 66 spans**, client span 상태 상반 |
| 유일한 실시간 신호 | 메트릭 시계열 소멸 | **trace의 error span** |
| 사용자 증상 | 로그인만 실패 (content 무영향) | 로그인 실패 + **작성자 익명 저하** |
| v0 도달 난이도 | 높음 (부재 신호만) | 낮음 (trace가 자백) |

AU-2는 "trace 무신호 / 메트릭 단일 채널", AU-4는 "trace 단일 채널"이다.
[AP-1](../../../toy-content/docs/chaos/scenarios/AP-1/answer.md)까지 합치면 채널별 난이도
스펙트럼이 만들어진다.

## 이 폴더의 구성

| 파일 | 내용 |
|---|---|
| `round-N.md` | 회차 정리 — 맨 위 "한눈 요약"에 실제 원인 / 에이전트 파악 원인 / 판정 / §8 채점 / 토큰·비용 |
| `round-N-rca-report.md` | 에이전트가 생성한 RCA 보고서 원문 |

## 회차 인덱스

| 회차 | 일시(UTC) | 다운 | 갈래 | 결말 | RCA 조사 | 문서 |
|---|---|---|---|---|---|---|
| ~~1 (폐기)~~ | ~~07-27 07:00~~ | 22분 51초 | A | 앵커 사실 오류(유형 C) 상태에서 채록 — **회차 무효, 재실행으로 대체** | O | 경위는 [AE-06](../findings/ae-06-rca-v0-au4-blind-eval.md) |
| **1 (재실행)** | 07-28 14:29~ | timeline.log 참조 | **A (fallback 정상)** | 피드 200 유지 + 작성자 익명 — refused가 client span에 자백 (75/100) | O | [round-1.md](round-1.md) |

폐기된 구 회차의 교훈: **에이전트가 앵커보다 정확했다.** 앵커는 "3s timeout"을 만점 요건으로
요구했으나 실측은 **23.5ms connection refused**였고, 에이전트는 "타임아웃이 아니라 TCP RST"로
정확히 구별했다 ([AE-06](../findings/ae-06-rca-v0-au4-blind-eval.md)). 이 발견이 앵커 작성
체크리스트("코드 독해를 실측으로 착각 금지")의 근거가 됐고, 앵커 정정 후 재실행이 현 회차 1이다.
⚠️ 재실행 채점 전 확인: answer.md 정답지 1줄의 "3s timeout" 문구 잔존 여부.

## 스크린샷 공통 팁

- Grafana Explore 시간대는 로컬(KST=UTC+9). 아래 회차 문서 시간 범위는 UTC로 적었다.
- Tempo는 TraceQL 탭에 traceId를 붙여넣으면 워터폴이 뜬다.
- 이 문항의 핵심 스샷은 **baseline과 symptom 워터폴을 나란히** 놓은 것이다 —
  `http get /external/users` 서버 span의 존재/부재가 한눈에 보인다.
