# 회차 4 변경 대기열

> **회차 3이 아직 적용 중이다.** 여기 있는 것은 회차 3 결과를 보고 착수할 것이고,
> 지금 코드에 넣지 않는다 — 이유는 ③·④에 있다.

---

## 관측 백엔드 교체 가능성 — 인터페이스를 어디에 두어야 진짜 seam인가

**변경군 B (조사 도구).** 단, Scouter 도입은 계측 교체를 동반하므로 **변경군 A도 걸린다** —
아래 "축이 하나 비어 있다" 참조.

### 상황

| | |
|---|---|
| 계기 | 관측 소스가 나중에 **서버 로그 디렉터리** 또는 **Scouter APM**으로 바뀔 수 있다는 요구 |
| 지금 구조 | `TempoClient` · `LokiClient` · `MimirClient` 구현 클래스를 `Collector`·`Surveyor`가 **직접 필드로** 들고 있다 |
| 쿼리가 사는 곳 | `CollectProperties.errorWarnQuery/traceIdQuery` · `SurveyProperties.traceQuery/slowTraceQueryFor/logQueryFor/metricQueries` — **호출부가 쿼리 문자열을 만들어 클라이언트에 넘긴다** |
| 응답이 쓰이는 곳 | 원본 JSON 문자열이 `CollectedData`·`SurveyResult`에 그대로 담겨 **LLM 컨텍스트까지 간다** |
| 현재 상태 | **미착수.** 인터페이스 없음, 구현 하나뿐 |

### 무엇이 일어났나

첫 판단은 "`LlmClient`·`Notifier`가 이미 인터페이스 + `@ConditionalOnProperty`로 갈아끼우게 돼
있으니 클라이언트 셋도 똑같이 하면 된다"였다. 확인해 보니 **그 자리는 seam이 아니다.**

현재 시그니처는 `queryRange(correlationId, label, logql, start, end, limit)` 다. 다른 소스에
`logql` 문자열을 넘기면 그쪽은 그것을 실행할 수 없다. 같은 자리에 인터페이스를 세우면 **구현을
갈아끼울 수 있는 것처럼 보이지만 계약은 여전히 LogQL**이다.

두 번째로 응답 형식이다. Loki/Tempo/Prometheus의 JSON 봉투를 푸는 코드가 클라이언트 패키지
**밖에 6곳** 있다 — `TraceSpans:61` · `Collector:186` · `SurveyResult:83` · `Signal:301` ·
`EvidenceExtractor:199` · `LokiLogDedup`. 즉 교체 비용은 클라이언트 클래스가 아니라
**쿼리 방언과 응답 봉투** 두 가지에 있고, 메서드 단위 인터페이스는 그중 어느 것도 못 건드린다.

**세 번째가 "지금 넣지 않는다"를 결정했다.** 진짜 seam(의도 단위)으로 가면 쿼리 생성이 구현체
안으로 들어가는데, 지금 실패 문구가 쿼리 원문을 품고 있다 —
`probe("Tempo 에러 검색 '" + surveyProperties.traceQuery() + "'", ...)` ·
`probe("Metric '" + query + "'", ...)`. 이 문구들은 `SurveyContextAssembler`의
`# 무신호/실패 목록`으로 **모델에게 그대로 실려 간다**
(`SurveyResult.failures` javadoc: *"지우지 않고 그대로 모델에게 보인다"*).
따라서 의도 단위로 옮기면 **모델이 보는 텍스트가 바뀌고** 그 회차 점수는 이전 회차와 비교할 수 없다.

### Scouter를 실제로 대보면 — 축이 하나 비어 있다

Scouter Web API 문서(v1) 기준 대응이다. **2026-08-03 문서 확인이며 실물 연동은 미실시.**

| 지금 축 | Scouter 대응 | 판정 |
|---|---|---|
| Tempo 트레이스 조회 (`fetchTrace`) | `GET /v1/xlog-data/{yyyymmdd}/gxid/{gxid}` + `GET /v1/profile-data/{yyyymmdd}/{txid}` | 대응됨. **`gxid`가 traceId 자리**에 온다 |
| Tempo 검색 (`search`) | `GET /v1/xlog-data/{yyyymmdd}` (기간·필터) | 대응됨. TraceQL은 없고 필터 파라미터다 |
| Mimir 메트릭 (`queryRange`) | `GET /v1/counter/{counter}/...` | **부분 대응.** PromQL 식이 아니라 **카운터 이름**이다 — `min_over_time(up[5m])` 같은 식은 성립하지 않는다 |
| Loki 로그 (`queryRange`) | **없음** | **애플리케이션 로그 엔드포인트가 없다.** XLog는 트랜잭션 기록이지 로그가 아니다 |

이것이 설계를 바꾼다.

1. **로그 축은 Scouter가 못 채운다.** Scouter를 쓰더라도 로그는 파일 디렉터리 등 **다른 소스**에서
   와야 한다. 따라서 `provider=scouter` 같은 **전역 스위치는 틀렸다** — 축마다 독립적으로
   갈아끼워야 한다(`rca.source.trace=scouter` · `rca.source.log=file` · `rca.source.metric=scouter`).
2. **PromQL이 카운터 이름으로 바뀐다.** `SurveyProperties.metricQueries()`가 식 목록인 전제가
   깨진다 — 의도 단위 인터페이스가 필수인 근거가 하나 더 늘었다.
3. **날짜 파티션.** 경로에 `{yyyymmdd}`가 있어 **자정을 넘는 창은 호출을 쪼개야 한다.**
   현재 인터페이스가 `start`/`end`를 그대로 넘기므로 구현체가 흡수할 수 있다 — 인터페이스 변경 불필요.
4. **봉투가 완전히 다르다.** `{"status":200,"resultCode":0,"message":"success","result":[...]}`.
   Loki/Prometheus 봉투로 변환하는 어댑터가 필요하다.
5. **인증이 다르다.** IP 허용목록 · Bearer 토큰이고 Grafana 토큰 헤더가 아니다 —
   `GrafanaProperties.restClient()` 파생이 그대로는 안 맞는다.
6. **Scouter는 자바 에이전트다.** 도입하면 앱 계측(Brave/OTel)이 교체된다 —
   그건 **변경군 A**라 이 항목(B)과 같은 회차에 넣으면 안 된다.

### 회차 4에서 바꿀 것

**① 변경 표**

| 층위 | 무엇을 |
|---|---|
| 구조 개선 | `TraceSource` · `LogSource` · `MetricSource`를 **의도 단위**로 정의. `queryRange(logql, ...)`이 아니라 `errorWarnLogs(services, window, limit)` · `logRateCurve(services, window, step)` · `logsForTrace(traceId, ...)` · `searchErrorTraces(window, limit)` |
| 구조 개선 | 쿼리 생성을 각 구현체로. `LokiLogSource`가 LogQL 템플릿을 읽고, `ScouterTraceSource`는 `{yyyymmdd}` 분할과 gxid 매핑을 안에서 처리한다 |
| 배선 | **축별** `@ConditionalOnProperty` — 전역 provider 스위치가 아니다(로그 축을 Scouter가 못 채우므로) |
| 계약 명시 | **응답 형식을 인터페이스 계약으로 문서화.** 새 구현체는 기존 봉투로 **변환해서** 돌려준다 |
| 관측 보존 | 실패 문구가 쿼리 원문 대신 소스가 노출하는 `label()`을 쓰도록. 문구가 바뀌므로 **단독 회차**여야 한다 |

**왜 봉투를 유지하나** — 파서 6곳을 아끼려는 것이 아니라 **모델이 보는 것을 고정**하려는 것이다.
컨텍스트 형식이 바뀌면 이전 회차 점수 전부가 비교 불가가 된다. 새 소스가 자기 형식을 내놓게
하려면 그건 별도 회차의 별도 변경군이다.

**② 신호 도달 확인** — **부분 실시.** Scouter Web API에 트레이스·카운터 엔드포인트가 있고
**로그 엔드포인트가 없다**는 것까지 문서로 확인했다(위 표). 실물 서버 대조는 미실시 —
Scouter 인스턴스가 생기면 `GET /v1/xlog-data/{yyyymmdd}`가 서비스명·시각·에러·elapsed를
실제로 채워 주는지, 그 값으로 `Signal`을 만들 수 있는지를 먼저 잰다.

**③ 검증 설계** — 한 번에 하나만 바꾼다.
1. 인터페이스만 추출하고 구현은 Loki/Tempo/Mimir 하나씩 그대로 (행동 불변, 테스트로 고정)
2. 쿼리 생성을 구현체로 이관 (실패 문구 변경 — **여기서 회차를 끊는다**)
3. 두 번째 구현체는 실제 대상이 정해진 뒤에. 로그 축과 트레이스/메트릭 축은 **다른 소스**가 되므로 따로 붙인다

**④ 반증 조건** — 1단계 후 전 테스트가 통과하는데도 신호 산출이 달라지면 추출 위치가 틀린 것이다.
2단계 후 같은 문항 점수가 ±10을 넘게 움직이면 실패 문구가 채점에 영향을 준 것이고, 그 자체가 발견이다.
Scouter를 붙였을 때 `gxid` 하나로 서비스 간 연결이 복원되지 않으면 — 즉 상·하류가 다른 gxid를
갖는다면 — 이 프로젝트의 traceId 전제가 깨지므로 수집 단위부터 다시 설계해야 한다.

**⑤ 현재 상태** — **미착수·미측정.** 인터페이스는 없고 구현도 하나뿐이다.
완료된 것은 조사뿐이다: 쿼리 방언·응답 봉투·실패 문구의 모델 노출을 코드에서 확인했고,
Scouter 대응 축을 문서로 대조해 **로그 축이 비어 있다**는 것을 확인했다.
그 결과가 "지금 넣지 않는다"와 "전역 스위치가 아니라 축별 스위치"의 근거다.
