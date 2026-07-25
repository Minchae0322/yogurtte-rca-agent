# 프로젝트 현황판 (STATUS)

> **여기서 시작하세요.** 뭘 봐야 할지 기억 안 나면 이 파일 하나만 열면 됩니다.
> 유지 규칙: 작업 세션이 끝날 때마다 이 파일을 갱신한다 (① 지금 위치 체크박스,
> ② 다음 할 일 큐, ③ 활동 로그에 날짜 항목 추가).

## ① 지금 어디까지 왔나 (전략 Phase 기준)

전체 계획은 [strategy.md](strategy.md) — 아래는 그 실행 현황이다.

- [x] **Phase 0 — 관측성 기반**: Grafana Cloud + exporter 구축, Brave 유지 결정(ADR-001)
  - [x] (보강) Mongo/Redis/JDBC/@Observed 계측 추가 — toy-chat 배포됨 (2026-07-25)
  - [x] (보강) 컨슈머 예외 삼킴 제거 → 재시도/DLQ 경로 활성화 + 관측 로그
  - [x] (보강) 하트비트 span 노이즈 필터 (`0ba282b`) — **배포 확인 대기**
  - [ ] `service.version=$GIT_SHA` 트레이스 태깅 (Phase 4c에서)
- [x] **Phase 1 — v0 에이전트 + 측정 하네스**: rca/review 2모드, 토큰·비용·coverage 기록,
  실전 조사 1건 (dispatch 995ms 특정)
- [ ] **Phase 2 — 평가 하네스** ← **지금 여기**
  - [x] 서버에 chaos 하네스 구축 (`~/chaos`, baseline→주입→증상→원복 + 블라인드 채점)
  - [x] CH-1(MongoDB 장애) 1차 실행 — 증상 미발생. 원인 규명: 주입 16초 < 타임아웃 체인
    + **컨슈머가 예외를 삼키고 ack** (근본 원인, 수정 배포됨)
  - [ ] CH-1 재실행 (3분 주입, T1 후 2분 대기) — 이번엔 Mongo span + `[KAFKA-RETRY]` 로그
    + DLQ 오프셋 3중 관측 예상
  - [ ] N1 정답지로 리뷰 모드 재현율 측정 (정답지는 [findings/](findings/README.md) 확정 완료)
  - [ ] C-트랙 나머지 시나리오 + baseline 결과표 (C 7종 + N 3종 × 3회)
- [ ] **Phase 3 — 프롬프트·컨텍스트 최적화** (baseline 후)
- [ ] **Phase 4 — 코드 인지 RCA** (게이트: (c)유형 실패 실측 — 이미 1건 확보)

## ② 바로 다음 할 일

즉시 (다음 세션에서):

1. **하트비트 필터 배포 확인** — `hello`/`INFO` 고아 트레이스가 새 배포 후 멈췄는지 Tempo 검색
2. **댓글 1건 달고 Mongo span 부모 연결 검증** — `insert user_notifications`가 댓글
   트레이스의 자식으로 붙는지 (contextProvider 검증). 앱에서 댓글 또는
   `scripts/api-write-flow.sh` (새 토큰 필요)
3. **CH-1 재실험** — 주입 ≥3분, T1은 주입 직후, 증상 채록은 T1 후 2분+
4. **DF-01의 500 트레이스로 rca 모드 실전 분석** — 자연 발생 결함 (traceId는 Grafana에서)

백로그 (전략 순서대로):

- N2(피드 목록)/N3(채팅 fan-out) 정답지 큐레이션
- Phase 3 E3: `kafka_consumergroup_lag` 교체 + `up{job=~"kafka|redis|mongodb"}` 수집 추가
  (CH-1에서 원인 신호가 에이전트 수집 범위 밖이었음)
- Phase 3 E3: rca-agent Loki 셀렉터 라벨 검증 — toy-content 문서(07-25)에 따르면 Alloy는
  `service_name`을 붙임. rca-agent는 `{app=~...}`로 수집 중인데 과거 조사에서 로그가
  잡히긴 했음 — 두 라벨 공존 여부 실측 후 정합화
- 컨슈머 예외 삼킴을 findings에 기록 (NF-02와 같은 계열)
- api-sweep 재실행으로 500 결함 3군 회귀 확인 (수정되면 200 전환)

## ③ 문서 지도 (뭘 보려면 어디로)

| 알고 싶은 것 | 문서 |
|---|---|
| 전체 계획과 왜 이 순서인가 | [strategy.md](strategy.md) |
| 관측 파이프라인 구성·한계 (실측) | [monitoring.md](monitoring.md) |
| 왜 그렇게 결정했나 (수치 포함) | [decisions/](decisions/README.md) — ADR 6건 |
| 찾아낸 문제들 (정답지 겸용) | [findings/](findings/README.md) — NF 6건 + DF 1건 |
| 에이전트 리포트 실물 | [sample-report.md](sample-report.md) (rca) · [sample-review-report.md](sample-review-report.md) (review) |
| 실행 도구 | `scripts/api-sweep.sh` (읽기 순회) · `scripts/api-write-flow.sh` (업로드→피드→댓글) |
| chaos 하네스 | 서버 `~/chaos` (이 레포 밖) — 시나리오·evidence·채점 |

## ④ 활동 로그 (최신이 위)

- **2026-07-26**: 하트비트 span 필터 추가·푸시(`0ba282b`, Brave SpanHandler drop). Mongo
  계측 동작 실측 확인(`hello` span 배포 시점부터 생성 — 단 고아 트레이스 노이즈 발견이
  필터의 계기). 댓글 흐름 부모-연결 검증은 배포 후 댓글 부재로 대기. STATUS.md 신설.
- **2026-07-25**: CH-1 1차 실행 분석 — 증상 0의 원인 규명(주입 16s < 타임아웃 체인, 그리고
  **컨슈머 예외 삼킴**이 근본 원인). toy-chat 4커밋: Kafka 재시도/DLQ 관측 로그, 예외 삼킴
  제거+DLQ 무한 재시도 팩토리, Mongo 계측 중복 제거+contextProvider 보완. DLQ 과거 적재
  11+2건 발견. Mongo 미계측을 monitoring.md 한계 4번으로 기록.
- **2026-07-24**: 리뷰 모드 구현·실검증($1.23, 신규 발견 3건). 실서비스 GET 순회 79회 →
  **500 고정 결함 3군**(DF-01) + 게이트웨이 라우팅 이상(NF-05) 발견. write-flow 스크립트
  (업로드→피드→댓글 = N1 트레이스 생성기) + sweep-assets/ 폴더.
- **2026-07-23**: strategy를 2트랙 평가(C-트랙 chaos / N-트랙 정상)로 재설계. findings
  체계 신설(NF-01~06, DF-01) — N1 정답지 확정. docs 구조화(ADR 6건, monitoring.md).
- **2026-07-22**: Grafana Cloud 연동 3/3 검증(Loki 테넌트 오류 격리, Mimir /api/prom 경로
  수정). claude-cli 토큰 회계 수정(in=2→42,774). 리포트 .md+.json 이중 저장.
  kafka_consumergroup_lag 실존 확인(ADR-005).
- **2026-07-21**: 실전 조사 1호 성공 — dispatch 996ms 중 994ms 미계측 갭 특정, 사람이
  코드로 FCM 동기 호출 확정. 앱 메모리 실측(RSS ~200MB).
- **2026-07-20**: v0 에이전트 완성(수집→조립→LLM 1회→통보). 패키지 구조화, 측정 하네스,
  프롬프트 파일화, Docker. OTel vs Brave 결정 문서화.
