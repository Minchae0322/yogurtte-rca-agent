# 퀴즈 02 — 프로젝트 구조와 로직 (10문항)

> 이번엔 관측 이야기가 아니라 **코드가 어떻게 짜여 있고 왜 그렇게 짜였나**다.
> 답을 생각하고 `▶ 정답`을 펼친다. 전부 실제 파일·클래스 기준이다.

---

## 1

패키지가 이렇게 있다. **요청 하나가 지나가는 순서**로 나열하고, 각 패키지가 무엇을 하나?

```
api  ·  service  ·  triage  ·  collector  ·  client  ·  analyzer  ·  llm  ·  report  ·  notify  ·  time  ·  error
```

<details><summary>▶ 정답</summary>

```
api → (triage) → service → collector(→client) + analyzer → llm → report → notify
```

| 패키지 | 하는 일 |
|---|---|
| `api` | `RcaController` — 진입점 두 개 |
| `triage` | 자연어 조사의 앞단 (창 해석 · 스윕 · 후보 · 탐색 LLM) |
| `service` | `RcaService` — 수집→조립→분석→리포트의 직선 실행 |
| `collector` | 무엇을 어느 창으로 가져올지 (`Scope` · `Collector` · `CollectedData`) |
| `client` | Loki·Tempo·Mimir HTTP 호출 + `RawResponseStore` |
| `analyzer` | 컨텍스트 조립과 Evidence 정제 |
| `llm` | `LlmClient` 인터페이스와 구현체 |
| `report` | `RcaReport` · `ReportMarkdown` · `ReportStore` · `ServiceGraph` · `Evidence` |
| `notify` | `Notifier` 인터페이스와 구현체 |
| `time` | 시간 표현 후보 인식(`TimeCandidates`) |
| `error` | 예외·핸들러 |

**`triage`는 다시 넷으로 갈린다** — `window`(창 해석) · `survey`(스윕) · `incident`(신호·후보)
· `plan`(탐색 LLM 입출력). `TriageService`가 이 넷을 순서대로 부르고 마지막에
`RcaService.investigate(scope, ...)`로 넘긴다.
</details>

---

## 2

진입점이 `POST /investigate`와 `POST /diagnose` **둘**이다. 무엇이 다르고,
왜 옛 것을 안 지웠나?

<details><summary>▶ 정답</summary>

| | 입력 | 경로 |
|---|---|---|
| `/investigate` | **traceId** (v0) | 탐색을 건너뛰고 `RcaService`로 직행 |
| `/diagnose` | **자연어 질문** (v1) | `TriageService` → 탐색 LLM → `RcaService` |

**안 지운 이유는 측정이다.** 분석 능력만 따로 재려면 대상이 고정된 입력이 필요하고,
과거 회차와의 비교가 이 경로로만 성립한다. `/diagnose`로 재면 탐색 실패와 분석 실패가
섞여 원인을 못 가른다.

그래서 `/diagnose`로 들어와도 **뒷단은 같은 코드**를 탄다 — 분석 능력이 두 진입점에서
동일해야 점수를 비교할 수 있다.
</details>

---

## 3

인터페이스가 **딱 둘**이다. 무엇이고, 구현체는 어떻게 하나만 뜨나?

<details><summary>▶ 정답</summary>

`LlmClient`와 `Notifier` 둘뿐이다.

```java
@ConditionalOnProperty(name = "rca.llm.provider",   havingValue = "claude-cli", matchIfMissing = true)
@ConditionalOnProperty(name = "rca.llm.provider",   havingValue = "anthropic")
@ConditionalOnProperty(name = "rca.llm.provider",   havingValue = "openai")

@ConditionalOnProperty(name = "rca.notify.channel", havingValue = "console", matchIfMissing = true)
@ConditionalOnProperty(name = "rca.notify.channel", havingValue = "slack")
@ConditionalOnProperty(name = "rca.notify.channel", havingValue = "discord")
```

선택 결과는 기동 로그 한 줄로 확인한다 — `rca-agent ready: llm=... notifier=...`

**구현이 하나뿐인 곳에는 인터페이스를 두지 않는다.** `Collector` · `ContextAssembler` ·
`ServiceGraphExtractor`는 전부 구체 클래스다.
</details>

---

## 4

`Scope`가 `traceIds`를 **필수로 두지 않는다.** 비어 있어도 정상 입력인 이유는?

<details><summary>▶ 정답</summary>

**트레이스가 아예 생성되지 않는 장애가 있다.**

- 컨슈머가 전멸하면 consume span이 안 생긴다 (CH-2)
- 파드가 0이면 ingress가 끊겨 트레이스가 만들어지지 않는다 (AU-2)

필수로 두면 그런 장애는 넘길 대상이 없어 **파이프라인이 그 자리에서 끊긴다.**
대신 수집 실패 절에 이렇게 남긴다.

> *"이 조사에는 지목된 traceId가 없다 — 탐색이 트레이스를 찾지 못했거나 트레이스가
> 생성되지 않는 장애다. **트레이스 부재 자체를 근거로 쓸 것.**"*

CH-2는 트레이스 0건인 채로 **100점**을 받았다.
</details>

---

## 5

`Scope`에 창이 **두 개** 있다 — `window` 하나와 `windows` 목록. 왜 둘이고,
어느 채널이 어느 것을 쓰나?

<details><summary>▶ 정답</summary>

후보를 여러 개 골랐을 때 **채널마다 창이 달라야** 하기 때문이다.

| 채널 | 창 | 왜 |
|---|---|---|
| 로그 (ERROR/WARN · traceId) | `windows` **후보별 분할** | 점 사건이라 사이 구간에 정보가 없다 |
| 후보 트레이스 검색 | `windows` **분할** | 빈 구간의 무관한 트레이스가 상한을 차지하지 않게 |
| 메트릭 | `window` **합집합** | 시계열이 조각나면 *"그 사이에 회복했는가"* 를 잃는다 |

**나눠서 제일 많이 아낄 수 있는 채널(메트릭)이 나누면 제일 위험한 채널**이라는 것이
이 설계의 핵심이다. 절약은 로그·트레이스에서만 취한다.

구현 디테일 둘:
- 결과는 `mergeStreams`로 **합쳐서** 넘긴다 — 창별 목록으로 주면 `LokiLogDedup`·
  `ContextAssembler`가 전부 창을 알아야 하는데 **그들이 하는 일에 창은 필요 없다**
- **창이 하나면 원본 문자열을 그대로** 돌려준다 — 재직렬화하면 바이트가 미묘하게 달라져
  토큰 축 비교가 흔들린다
</details>

---

## 6

`Collector`가 `scope.traceIds()`를 돌며 전문을 받는데, **대표 하나를 뽑지 않는다.**
왜? 그리고 상한은?

<details><summary>▶ 정답</summary>

예전엔 첫 번째를 대표로 세웠는데 **그 "첫 번째"에 아무 근거가 없었다** — 신호가 만들어진
순서일 뿐 duration도 에러 유무도 안 봤다.

AP-1 회차 3에서 한 후보가 트레이스 둘(11.6초 지연 **200** · 308ms 실패 **500**)을 물었을 때
**성공 트레이스가 대표가 됐고**, 분석이 그 지연을 별건으로 다루느라 진짜 원인이
랭킹 2순위로 밀렸다.

*"어느 것이 원인인지는 전문을 봐야 안다. 고르는 일은 탐색 LLM이 후보 단계에서 이미 했다."*

상한은 `max-traces: 10`(0 이하 = 무제한). 지목분을 먼저 채우고 남는 자리를 **창 안 후보
트레이스**로 보충한다(B-9). 그래서 컨텍스트의 트레이스 절이 *"앞쪽이 탐색이 지목한 것,
뒤가 같은 창에서 함께 수집한 것"* 이 된다.

후보 검색 TraceQL이 `{}`인 것도 의도다 — **상태를 안 거른다.** 정답이 *정상 트레이스*인
문항이 실재한다(AU-2: *"정상 요청에 auth 호출 span이 없다"* 가 요건).
</details>

---

## 7

`ContextAssembler`가 분석 컨텍스트를 조립할 때 **네 가지 정제**를 한다.
각각 무엇을 접고, **무엇을 버리지 않나?**

<details><summary>▶ 정답</summary>

| | 클래스 | 무엇을 |
|---|---|---|
| ① 호출 그래프 | `ServiceGraphExtractor` → `ServiceGraph` | span 관계를 엣지로 **요약해서 앞에 얹는다** |
| ② 로그 중복 접기 | `LokiLogDedup` | 두 채널의 **교집합에서 두 번째 등장만** 제거 |
| ③ 로그 스택 접기 | `LogStackFold` | **인용된 적 없는 것**을 접는다 |
| ④ 트레이스 압축 | `TraceCompact` | **아무것도 버리지 않는다** — 재인코딩만 |

**넷 다 원본을 대체하지 않는다.**

- ①은 **추가**다. 원본 span 목록이 그대로 밑에 남는다 (그래서 컨텍스트는 오히려 커진다)
- ②는 제거하되 *"N줄은 위 절과 동일한 레코드라 생략했다 — 이 채널로도 도달했다"* 를 남긴다
- ④는 OTLP 속성 래퍼 평탄화 · 끝시각→`durNs` · 배치 공통 속성 호이스팅. **표기법만 바뀐다**

④가 요약이 아닌 이유가 중요하다 — **`"이 span이 없다"`가 요건인 문항(AU-2류)에서
부재 근거가 죽으면 안 된다.** 그건 요약의 위험이지 재인코딩의 위험이 아니다.

그리고 `reports/raw/` 원본은 어떤 경우에도 안 건드린다 — 채점자가 신호 도달을 감사하는
자료다.
</details>

---

## 8

`TraceCompact`의 **배치 공통 속성 호이스팅**은 "그 배치의 모든 span에서 값이 하나뿐인
속성만" 위로 올린다. `net.host.ip`는 span 전부에 붙어 바이트의 13.7%를 먹고 리포트 인용은
0회인데도 **고정 제외 목록으로 지우지 않았다.** 왜?

<details><summary>▶ 정답</summary>

**레플리카가 여러 개인 순간 그 값이 "5개 파드 중 하나만 느리다"를 가르는 유일한 값이
되기 때문이다.**

호이스팅은 그 판단을 **데이터에 맡긴다.**

```
레플리카 1개 → 트레이스 안에서 IP가 하나뿐 → 위로 올라간다 (반복만 사라진다)
레플리카 N개 → IP가 여러 개              → 조건에 안 걸려 span마다 그대로 남는다
```

실측으로 지금은 앞쪽이다(트레이스 238건 중 한 서비스가 두 IP로 나타난 것 0건).
그래도 고정 목록을 안 쓰는 이유는 그 목록이 **"지금 파드가 1개"라는 조건에 의존하는
절감**이기 때문이다.

같은 원칙이 하나 더 있다 — `startTimeUnixNano`는 **절대값 그대로** 둔다. 성능 회차 간
비교와 로그·메트릭 시각 대조의 기준점이라 상대 시각으로 바꾸면 그 기준이 사라진다.
</details>

---

## 9

탐색 LLM의 응답을 `TriagePlan.parse(...)`가 읽는다. 모델이 `windowStart`를 적어 냈으면
어떻게 되나? 그리고 파싱에 실패하면?

<details><summary>▶ 정답</summary>

**후보를 지목했으면 모델이 쓴 시각은 무시한다.** 창은 코드가 파생한다.

```java
window   = Incident.unionWindow(chosen, exactPad, bucketPad, sweep)
services = resourcesOf(chosen)
traceIds = TriagePlan.traceIdsOf(chosen)
```

여유 폭은 **신호의 정밀도**에서 나온다 — `EXACT`(Tempo span, ms 정확)는 **±2분**,
`BUCKET`(Loki 5분 버킷·Mimir 샘플)은 **±5분**. 임의로 정한 값이 아니라 그 신호의
해상도만큼 준다.

이 구조가 생긴 이유가 CH-3다 — 모델이 창을 직접 써내던 시절 그 숫자는 **어떤 관측에서도
유도되지 않은 값**이었고, 주입 1초 전에서 끊겨 세 회차 연속 4점이었다.

**파싱에 실패하면** 스윕 창 전체가 아니라 **신호가 가장 많은 후보**로 떨어지고,
그 사실이 `notes`에 남는다. 고르지 않은 후보(`dismissedIncidentIds`)와 후보 전문
(`incidentCandidates`)도 리포트에 실린다 — 회고에서 *"다른 걸 골랐어야 했나"* 를
판단하려면 그때 무엇이 보였는지가 있어야 한다.
</details>

---

## 10

`ClaudeCliLlmClient`는 CLI를 **중립 임시 디렉터리에서** 띄운다. 왜?
그리고 `overheadTokens()`는 무엇을 하나?

<details><summary>▶ 정답</summary>

**블라인드 오염 차단이다.**

`ProcessBuilder`가 JVM의 cwd(레포 루트)를 물려주면 Claude Code가 그 디렉터리의
`CLAUDE.md`와 `.claude/skills/`를 **자동 로드한다.** 이 레포의 `CLAUDE.md`에는
관측 라벨·문항 관련 사실이 들어 있어 **피험자에게 정답을 흘리는 경로**가 된다.
실측으로 확인됐고 AU-2 회차 1이 그 상태로 실행됐다.

그래서 개선 수단은 **`prompts/system-prompt.md` 하나로만** 들어가야 측정이 성립한다.

**`overheadTokens()`** — 1자 프롬프트를 CLI에 던져 **CLI 고정 오버헤드를 그 자리에서
실측**한다.

```
컨텍스트 토큰 = in − overheadTokens
```

상수로 두면 안 되는 이유: 07-26~27 약 22,000 → 07-28 **26,626** → 07-29 **21,247**로
**하루 만에 −20%** 움직였고 감소분 대부분이 툴 정의였다(23,201 → 10,762).
**소급 추정이 불가능**하다. 남는 오차는 프로브 자체의 ±320 tok(1.5%).

보너스 — 시스템 프롬프트는 코드가 아니라 파일이고 **조사할 때마다 다시 읽는다**
(`SystemPromptLoader`). 재시작 없이 튜닝하고 리포트의 `promptSource`로 버전을 기록한다.
</details>

---

## 채점

| 맞은 수 | |
|---|---|
| 8~10 | 구조를 설명할 수 있다. *"왜 인터페이스가 둘뿐입니까"* 같은 질문도 받을 수 있다 |
| 5~7 | 흐름은 아는데 **경계의 이유**가 약하다. 5·7·9번을 다시 |
| 0~4 | [CLAUDE.md §6](../../CLAUDE.md) → [workflow.md](../workflow.md) 순으로 |

**이 세트를 관통하는 한 문장**: 거의 모든 설계 결정이 *"측정이 성립하려면"* 에서 나왔다.
진입점을 둘 둔 것(2), 대표를 안 뽑는 것(6), 원본을 안 버리는 것(7·8),
창을 코드가 파생하는 것(9), cwd를 격리하는 것(10)이 전부 같은 이유다.
