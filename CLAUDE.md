# CLAUDE.md

AI Root Cause Analysis 에이전트(`rca-agent`) 레포. traceId 하나로 Tempo·Loki·Mimir를 모아
LLM이 원인을 랭킹하는 v0 baseline이며, **성능을 측정하는 것 자체가 이 프로젝트의 목적**이다.

## 가장 중요한 규칙 — 조사를 돌렸으면 채점까지 한다

`/investigate`를 실행했으면 **반드시** [`docs/scoring/README.md`](docs/scoring/README.md)에 회차를
추가한다. 리포트만 남기고 채점을 미루면 데이터가 아니라 일화가 쌓인다 — 실제로 AE-01·02·03이
"채점 대기" 상태로 방치돼 있었다.

채점 규칙은 toy-content `docs/chaos/RUNBOOK.md` §8·§8.1·§8.2에 있고, 요약하면:

- **앵커 기준 채점.** 문항별 `toy-content/docs/chaos/scenarios/<ID>/answer.md`의 "채점 앵커"
  표(만점/부분점/0점)와 대조한다. **정답지와의 자유 대조는 금지** — 앵커에 없는 잣대를
  끌어오면 채점자 재량이 되어 그 회차는 무효다.
- **앵커는 채록 전에 박제된 것만 유효.** 채록 후 앵커를 고치면 그 회차 채점은 무효이고,
  개정은 **다음 회차부터** 적용한다. 앵커가 실제 전개와 안 맞으면 점수를 만들지 말고
  "채점 불가"로 기록한 뒤 결함으로 남긴다.
- **N=1은 인용하지 않는다.** 문항당 최소 2회, 점수는 평균 ± 최대편차로 기록한다.
  편차 ±10 초과면 문항 불안정으로 보고 인용을 보류한다.
- 루브릭: 근본 원인 40 · 근거 시그널 경로 30 · 오귀인 없음 20 · 조치 타당성 10.

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

미해결 결함: `CollectProperties.errorWarnQuery()`의 `| logfmt | level=~"ERROR|WARN"`는
평문 Logback 로그(`logging.pattern.level`)에서 `level` 필드를 만들지 못한다 — 셀렉터를
고쳐도 이 쿼리는 빈 결과다. `traceIdQuery`는 라인 필터라 셀렉터 수정만으로 동작한다.
**두 쿼리의 처방이 다르다.**

## 문서

| 알고 싶은 것 | 문서 |
|---|---|
| **지금 어디까지 왔나** | [docs/STATUS.md](docs/STATUS.md) — 여기서 시작 |
| **회차별 §8 점수와 판정 근거** | [docs/scoring/](docs/scoring/README.md) — 채점 대장 |
| 전체 계획과 진입 게이트 | [docs/strategy.md](docs/strategy.md) |
| 관측 파이프라인 구성·한계 | [docs/monitoring.md](docs/monitoring.md) |
| 왜 그렇게 결정했나 | [docs/decisions/](docs/decisions/README.md) |
| 찾아낸 문제들 (정답지 겸용) | [docs/findings/](docs/findings/README.md) |
| 장애별 회차 기록 | [docs/ch-1/](docs/ch-1/README.md) · [docs/in-2/](docs/in-2/README.md) · [docs/au-2/](docs/au-2/README.md) |

문서 작성 원칙: **결론만이 아니라 판단 과정과 실측 수치를 남긴다.** 추측은 "가설"로 명시하고
확인 방법을 붙인다. 근거 없는 항목은 쓰지 않는다.

## 세션 종료 시

`docs/STATUS.md`를 갱신한다 — ① 지금 위치 체크박스, ② 다음 할 일 큐, ③ 활동 로그에 날짜 항목.
