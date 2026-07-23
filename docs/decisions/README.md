# 의사결정 기록 (ADR)

각 문서는 [배경 → 검토한 선택지 → 결정 → 근거(실측 수치) → 결과/남긴 리스크] 구조를
따른다. 결론만이 아니라 **판단 과정과 그 근거가 된 숫자**를 남기는 것이 목적이다.

| # | 제목 | 상태 | 한 줄 요약 |
|---|---|---|---|
| [001](adr-001-brave-over-otel.md) | OTel Agent 대신 Brave 유지 | 채택 | E2E 실측 3시나리오로 커버리지 검증, 전환 실익 < 비용 |
| [002](adr-002-single-pass-baseline.md) | v0는 단일 패스 | 채택 | 대조군 없는 개선은 증명 불가 — baseline 먼저 |
| [003](adr-003-llm-provider-claude-cli.md) | LLM은 claude-cli 구독 기본 | 채택 | 반복 평가(조사당 $0.42)의 한계비용 0으로 |
| [004](adr-004-grafana-cloud-connectivity.md) | Grafana Cloud 연동·최소권한 | 채택 | Mimir 경로/Loki 테넌트 격리 진단, read 3스코프 |
| [005](adr-005-kafka-lag-exporter-metric.md) | lag은 broker-side exporter로 | 적용 대기 | 클라이언트 메트릭은 컨슈머와 함께 죽는다 |
| [006](adr-006-measurement-harness.md) | 측정 하네스 + 토큰 회계 수정 | 채택 | in=2 버그 수정(→42,774), coverage 1급 데이터화 |

전체 로드맵과 각 결정의 위치는 [strategy.md](../strategy.md) 참조.
