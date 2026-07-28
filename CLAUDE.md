# CLAUDE.md

AI Root Cause Analysis 에이전트(`rca-agent`) 레포. traceId 하나로 Tempo·Loki·Mimir를 모아
LLM이 원인을 랭킹하는 v0 baseline이며, **성능을 측정하는 것 자체가 이 프로젝트의 목적**이다.

## 가장 중요한 규칙 — 조사를 돌렸으면 채점까지 한다

`/investigate`를 실행했으면 **반드시** [`docs/scoring/README.md`](docs/scoring/README.md)에 회차를
추가한다. 리포트만 남기고 채점을 미루면 데이터가 아니라 일화가 쌓인다 — 실제로 AE-01·02·03이
"채점 대기" 상태로 방치돼 있었다.

채점 규칙은 toy-content [`docs/chaos/RUNBOOK.md`](../toy-content/docs/chaos/RUNBOOK.md) §8·§8.1·§8.2에 있고, 요약하면:

- **앵커 기준 채점.** 문항별 `toy-content/docs/chaos/scenarios/<ID>/answer.md`(회차 1까지) 또는
  **[`chaos/anchors-v2.md`](../toy-content/docs/chaos/anchors-v2.md)(회차 2부터)**의 "채점 앵커" 표와 대조한다. **정답지와의 자유 대조는 금지** —
  앵커에 없는 잣대를 끌어오면 채점자 재량이 되어 그 회차는 무효다.
- **앵커는 채록 전에 박제된 것만 유효.** 채록 후 앵커를 고치면 그 회차 채점은 무효이고,
  개정은 **다음 회차부터** 적용한다. 앵커가 실제 전개와 안 맞으면 점수를 만들지 말고
  "채점 불가"로 기록한 뒤 결함으로 남긴다.
- **N=1은 인용하지 않는다.** 문항당 최소 2회, 점수는 평균 ± 최대편차로 기록한다.
  편차 ±10 초과면 문항 불안정으로 보고 인용을 보류한다.

### 🔴 회차 2부터 자가 바뀐다 — 입력 모델 v1 (자연어) · 앵커 v2

| | 회차 1까지 (v0) | **회차 2~ (v1)** |
|---|---|---|
| 입력 | 사람이 고른 **traceId 1개** | **자연어 질문 하나** (문안 박제) |
| 앵커 | `answer.md` · [anchors.md](docs/scoring/anchors.md) | **[anchors-v2.md](../toy-content/docs/chaos/anchors-v2.md)** (전 문항 SoT) |
| 루브릭 | v1 (근본40·근거30·오귀20·조치10) | **[v3](docs/scoring/rubric-v3.md)** — 원인 40 · 근거 25 · **탐색 15** · **영향 10** · 오귀인 5 · 조치 5 |

원칙은 **"어떻게든 원인을 맞히는 것이 첫째, 경로는 묻지 않는다."**

- **구 앵커와 v1 점수는 고치지 않는다** — §8.2가 구버전 삭제를 금하고, 그 자로 매겨진
  점수가 이미 있다. **두 모델의 총점을 나란히 두고 개선/악화라고 말하지 않는다.**
- **회차 2의 차단 요인은 앵커가 아니라 진입점이다** — `RcaController`가 아직
  `@NotBlank traceId`를 요구한다([strategy.md Phase 4.5](docs/strategy.md)).

### 토큰을 기록할 때는 [round-1-input-tokens.md](docs/round-1-input-tokens.md)를 먼저 읽는다

**적용 시점: 에이전트가 장애 분석 리포트를 낼 때** — 조사 1회 = 리포트 1건 = 이 규칙 1회.
`/investigate`든 `/diagnose`든 같고, 그 리포트의 수치를 회차 문서·채점 대장으로 옮길 때도 같다.
(대화 중 아무 때나 토큰을 세는 이야기가 아니다.)

**리포트의 총 `in`을 그대로 인용하면 틀린다.** 거기엔 CLI 고정 오버헤드가 섞여 있다.

```
컨텍스트 토큰 = in − overheadTokens     ← 개선 지표. 단계(탐색·분석)마다 각각 뺀다
```

**1. 값은 이 순서로 고른다.**

| 우선 | 출처 | 표기 |
|---|---|---|
| ① | 리포트 `coverage.overheadTokens` — **그 회차 프로브 실측** | **`█ 실측`** |
| ② | ①이 `-1`이면 `chars × 0.524`(범위 0.511~0.532) | **`▓ 추정`** |
| ③ | 그래도 없으면 **쓰지 않는다** | — |

`chars`는 **컨텍스트 + 시스템 프롬프트**다(둘 다 실려 간다). `chars/4`(0.250)는
**2.1배 과소**라 폐기됐다. **지어내지 말고, 추정을 `실측`이라 쓰지 않는다.**

**2. 오버헤드 `C`를 문서에서 가져와 빼지 마라.** 상수가 아니다 —
07-26~27 ~22,000 → 07-28 **26,626** → 07-29 **21,247**(CLI 2.1.220). 하루 만에 −20%
움직였고 감소분 대부분이 툴 정의다(23,201 → 10,762). **소급 추정은 불가능하다.**
그래서 조사마다 1자 프롬프트로 직접 잰다(`LlmClient.overheadTokens()` · 기본 on ·
`RCA_CLI_PROBE_OVERHEAD`). 남는 오차는 프로브 자체의 **±320 tok(1.5%)**.
측정법·표본·한계 → [§2.1](docs/round-1-input-tokens.md#21-재측정-2026-07-29--오버헤드는-상수가-아니다)
· [§2.2](docs/round-1-input-tokens.md#22-회차-자체-측정-2026-07-29-구현--다음-회차부터-가-이-된다)

**3. 구독 CLI에서 되는 것과 안 되는 것.**

| | 구독 CLI | 어디서 |
|---|---|---|
| 그 호출이 쓴 토큰 | ✅ **실측** | 응답 `usage`(input/output/cacheRead/cacheCreate) · `modelUsage` |
| 비용 | ⚠️ **API 환산 추정치** | `total_cost_usd` — 정액제라 청구액이 아니다. 그렇게 표기한다 |
| **보내기 전** 예측 | ❌ | `count_tokens`는 별도 API 엔드포인트(키 필요) → `TokenCounter`가 `-1` |

**"구독이라 토큰을 못 잰다"는 부정확하다 — "미리 못 잰다"가 맞다.**
`coverage.contextTokens`가 항상 `-1`인 것도 이 한 줄 때문이고, ①의 프로브가 그 자리를 메운다.

**4. 회차마다 `llmModel`·`num_turns`를 함께 남긴다.** `num_turns > 1`이면 단일 패스 전제가
깨진 것이고 `usage`가 턴 누적이라 **토큰을 쓰면 안 된다.**
- **모델은 `RCA_CLAUDE_CLI_MODEL=claude-opus-5`로 고정한다.** 모델이 다르면 토크나이저가 달라
  토큰 축 비교가 성립하지 않는다.

> ⚠️ CLI 응답의 `modelUsage`에는 **모델이 여러 개** 들어온다 — 본답변 외에 CLI가 보조 작업에
> 작은 모델을 함께 쓴다. 첫 키를 집으면 요청하지 않은 모델이 기록되므로(AP-1 회차 2에서
> 실제로 발생), **요청한 모델이 목록에 있으면 그것이 본답변 모델**이다. 목록에 아예 없을
> 때만 진짜 대체이고, 그때는 회차를 별도 구성으로 기록한다.

### 블라인드를 깨지 말 것

**에이전트는 피험자다.** 리포트 생성 경로(`RcaService` → `ReportStore`)에 앵커·정답지를
넣지 않는다. 리포트 안에 점수를 생성하게 만드는 것도 금지 — 자기 채점은 §8상 무효다.
채점은 리포트가 나온 **뒤에, 별도 단계로** 붙는다.

프롬프트(`prompts/system-prompt.md`)를 수정할 때도 특정 문항의 정답을 암시하는 문장이
들어가지 않는지 확인한다.

> ⚠️ **이 파일도 오염원이다 (2026-07-27 확인).** `RCA_LLM_PROVIDER=claude-cli`이면
> `ClaudeCliLlmClient`가 `ProcessBuilder`로 CLI를 띄우면서 JVM의 cwd(레포 루트)를 물려주고,
> Claude Code는 cwd의 `CLAUDE.md`와 `.claude/skills/`를 **자동 로드한다.** 실측으로 확인됐고
> AU-2 회차 1이 이 상태로 실행됐다.
>
> **차단 전까지 이 파일에 관측 라벨·쿼리 결함·문항 관련 사실을 추가하지 말 것.**
> 차단책(서브프로세스 cwd 격리)은 [docs/v0.1-plan.md §0](docs/v0.1-plan.md).
> 개선 수단은 **`prompts/system-prompt.md` 하나로만** 들어가야 측정이 성립한다 —
> 레포에 스킬 파일을 두는 방식은 평가용으로 부적합하다.

## 빌드 · 실행

```bash
./gradlew build                # 빌드 + 테스트
./gradlew test                 # WireMock 클라이언트 테스트 + fake LLM 전체 흐름
.\scripts\run-local.ps1        # .env 로드 후 bootRun (Windows)
```

- **JDK 21 필수이고, 전역 JAVA_HOME이 그보다 낮으면 빌드가 아예 안 된다.** `build.gradle`의
  `toolchain`은 컴파일 대상만 정할 뿐 Gradle 데몬 JVM은 바꾸지 못해서, Spring Boot 3.5
  플러그인(17+)이 해석되지 않는다. 레포 전용으로 `gradle.properties`의 `org.gradle.java.home`을
  쓴다 (머신별 경로라 gitignore 대상).
- **`bootRun`은 `.env`를 자동으로 읽지 않는다.** Spring Boot에 dotenv 기능이 없고, `.env`는
  `docker-compose.yml`의 `env_file`만 읽는다. 로컬 실행은 `scripts/run-local.ps1`이 메운다.
  README의 `cp .env.example .env && ./gradlew bootRun`은 도커 경로에만 해당한다.

## 구조

```
api → service → collector(client) + analyzer → llm → notify → report
```

인터페이스는 `LlmClient`와 `Notifier` 둘뿐이고, `@ConditionalOnProperty`로 구현체 하나만 뜬다.
설정 record는 각 기능 패키지에 함께 둔다. 기동 로그 `rca-agent ready: llm=... notifier=...`로
선택 결과를 확인한다.

시스템 프롬프트는 코드가 아니라 `prompts/system-prompt.md` 파일이고 **조사할 때마다 다시
읽는다** — 재시작·리빌드 없이 튜닝하고 `reports/`의 `promptSource`로 버전별 비교한다.

## 관측 데이터 라벨 (실측 확정)

시그널 종류마다 라벨 체계가 다르다. 틀리면 조용히 빈 결과가 나온다.

| 시그널 | 라벨 | 값 |
|---|---|---|
| Loki (로그) | `service_name` | `content-service` / `auth-service` / `chat-service` |
| Prometheus (메트릭) | `application` | 위와 동일 |

`application` 라벨은 **Loki에 존재하지 않는다** (Micrometer common tag는 메트릭 전용).
근거: toy-content `docs/observability/observability.md` 2026-07-25 절.

**해소됨 (2026-07-28 · 회차 2부터 적용).** 조사 6회가 로그 0건이던 원인은 **독립된 두 결함**이었다.

1. **셀렉터** — `{app=~"content|auth|chat"}`. `app` 라벨은 Loki에 없고 값도 `-service` 접미가
   빠져 매칭 스트림이 0개였다. 기본값을 `service_name` / `*-service`로 바로잡았다.
2. **파싱** — `errorWarnQuery`의 `| logfmt | level=~`는 평문 Logback에서 `level` 필드를 만들지
   못해, **셀렉터를 고쳐도 이 쿼리만** 빈 결과였다. 라인 필터 `|~ "ERROR|WARN"`로 교체.

**회차 1은 이 결함을 안은 채 baseline이 됐고 점수는 그대로 둔다** — 델타 예측과 대조군 설계는
[docs/round-2/](docs/round-2/README.md) B-1·B-2.

## 문서

| 알고 싶은 것 | 문서 |
|---|---|
| **지금 어디까지 왔나** | [docs/STATUS.md](docs/STATUS.md) — 여기서 시작 |
| **회차별 §8 점수와 판정 근거** | [docs/scoring/](docs/scoring/README.md) — 채점 대장 |
| **장애별 항목 점수** | [scoring/summary.md](docs/scoring/summary.md) |
| **앵커 요건 (회차 2~ · 자연어)** | [toy-content `chaos/anchors-v2.md`](../toy-content/docs/chaos/anchors-v2.md) — 질문 문안 · 탐색 요건 · 채널 감사 |
| 앵커 요건 (회차 1까지 · 보존) | [scoring/anchors.md](docs/scoring/anchors.md) |
| **채점 항목을 어떻게 정했나** | [scoring/rubric-v3.md](docs/scoring/rubric-v3.md) — 탐색부터 원인 분석까지 |
| **대외용 종합 보고서** | [scoring/report.md](docs/scoring/report.md) — 장애 상황 + 채점 항목 + 결과 |
| **어떤 숫자를 개선 근거로 쓰나** | [docs/measurement.md](docs/measurement.md) — 토큰·비용 측정 기준 |
| 전체 계획과 진입 게이트 | [docs/strategy.md](docs/strategy.md) |
| 관측 파이프라인 구성·한계 | [docs/monitoring.md](docs/monitoring.md) |
| 왜 그렇게 결정했나 | [docs/decisions/](docs/decisions/README.md) |
| 찾아낸 문제들 (정답지 겸용) | [docs/findings/](docs/findings/README.md) |
| 장애별 회차 기록 | [docs/ch-1/](docs/ch-1/README.md) · [docs/in-2/](docs/in-2/README.md) · [docs/au-2/](docs/au-2/README.md) |

문서 작성 원칙: **결론만이 아니라 판단 과정과 실측 수치를 남긴다.** 추측은 "가설"로 명시하고
확인 방법을 붙인다. 근거 없는 항목은 쓰지 않는다.

## 세션 종료 시

`docs/STATUS.md`를 갱신한다 — ① 지금 위치 체크박스, ② 다음 할 일 큐, ③ 활동 로그에 날짜 항목.
