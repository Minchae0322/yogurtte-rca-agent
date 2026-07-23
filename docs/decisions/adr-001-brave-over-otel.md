# ADR-001. OTel Agent로 전환하지 않고 Brave 계측을 유지한다

- 날짜: 2026-07-20
- 상태: 채택
- 관련: [monitoring.md](../monitoring.md), README "Engineering Decision" 섹션

## 배경

AI 기반 RCA 시스템에서 LLM의 입력은 관측 데이터(Trace/Metric/Log)가 전부다. 즉 계측
커버리지가 분석 품질의 상한을 구조적으로 결정한다. 현재 계측은 Brave 기반 라이브러리
계측인데, 이 방식은 개발자가 명시적으로 추가하거나 라이브러리가 지원하는 구간만
수집된다. 실제로 Kafka observation 누락을 경험하면서 "계측 사각지대가 존재할 수 있다"는
것이 가설이 아니라 사실임을 확인했고, zero-code로 커버리지를 보장하는 OTel Java Agent
전환이 자연스러운 대안으로 올라왔다.

## 검토한 선택지

1. **OTel Agent 전환 + 48시간 A/B 실험**: 두 계측을 병행 배포해 커버리지·오버헤드를
   직접 비교. 가장 확실하지만 배포 변경 2회, 이중 계측 기간의 리소스 비용, 48시간의
   실험 관리가 든다.
2. **전환 전에 저비용 검증 먼저**: 지금의 Brave 계측이 "RCA에 필요한 데이터 요건"을
   이미 충족하는지 실측으로 확인. 충족한다면 전환 자체가 불필요해진다.
3. **검증 없이 유지**: 비용은 0이지만 Kafka 누락을 이미 겪은 상태에서 근거 없는 낙관.

## 결정 과정과 근거

선택지 2를 먼저 수행했다. 대표 사용자 흐름(댓글 작성)을 기준으로 세 가지 시나리오의
E2E 트레이스를 실측했다:

1. 정상 요청 흐름 (HTTP → Service → Kafka Producer → Consumer → DB)
2. 에러 발생 흐름 (예외 발생 및 retry 포함)
3. 비동기 이벤트 처리 흐름 (fan-out 포함)

실측 트레이스(`6a5dc9c1990469248cfea377e1d7b4a0`)에서 확인한 것:

- **규모**: 2 services, 30 spans, 총 1.26s. content의 동기 응답
  `http post /feeds/{feedId}/comments`는 129.78ms.
- **세부 계측 밀도**: security filterchain(1.21ms)부터 JDBC 쿼리(2~5ms 단위,
  query/result-set/generated-keys 분리), Redis GET(689μs)까지 실행 시간이 전부 잡힘.
- **비동기 경계 전파**: `notification-publish`(5.5ms) → Kafka(`user.notifications`) →
  chat-service `receive`(1.11s) → `push-dispatcher#dispatch`(996ms)까지 **하나의
  traceId로 연결**. trace context가 Kafka를 건너 유지된다.
- **에러 정보**: 예외/retry 흐름에서 error 표기와 로그 상관이 동작.

결론적으로 RCA가 요구하는 네 요소 — span 구조, trace context 전파, 실행 시간, 에러
정보 — 가 기존 계측만으로 전부 수집되고 있었다.

반면 OTel Agent 전환의 비용은 실재한다: Pod당 메모리 사용 증가, CPU 경합 환경에서의
오버헤드, 그리고 계측 스택 교체에 따르는 운영·전환 리스크. 얻을 추가 이점이 "이미
충족된 요건의 재확보" 수준이라면 이 비용을 지불할 이유가 없다.

## 결정

**Brave 계측을 유지한다.** 48시간 A/B는 수행하지 않는다 — 더 낮은 비용의 검증으로
의사결정에 필요한 정보가 이미 확보되었기 때문이다.

## 결과와 남긴 리스크

- 운영 가이드 2건을 함께 도입했다: 신규 기능 개발 시 Observation 체크리스트 적용,
  주요 사용자 흐름의 주기적 트레이스 샘플링 검증.
- **이 결정의 리스크는 이후 실제로 현실화되었다**: 실전 조사에서
  `push-dispatcher#dispatch` 996ms 중 994ms가 자식 span 없이 비어 있었고, 원인은
  계측되지 않은 FCM 동기 호출이었다 (monitoring.md "알려진 한계" 1번). 이는 결정이
  틀렸다는 뜻이 아니라 — zero-code 전환으로도 서드파티 SDK 내부 호출은 안 잡혔을
  가능성이 높다 — **라이브러리 계측의 잔여 리스크를 코드 인지 RCA(전략 Phase 4)로
  보완해야 한다는 근거**가 되었다. 결정과 리스크와 보완 계획이 한 줄로 이어진다.
