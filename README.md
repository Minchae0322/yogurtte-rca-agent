# rca-agent (v0)

K3s 위 Spring MSA(content / auth / chat)의 장애 원인을 분석하는 RCA 에이전트입니다.

**v0 = baseline.** LLM에 도구를 주지 않습니다. 코드가 Tempo/Loki/Mimir에서 데이터를 모아
통째로 컨텍스트에 넣고 한 번 호출합니다. 에이전틱 루프도, 도구 호출도, MCP도 없습니다.

```
POST /investigate → collect(Tempo/Loki/Mimir) → assemble → LLM 1회 → notify
```

## 요구사항

- JDK 21
- (provider가 `claude-cli`인 경우) 로컬에 설치된 `claude` CLI

## 실행

```bash
cp .env.example .env      # 값 채우기
./gradlew bootRun
```

```bash
curl -X POST http://localhost:8080/investigate \
  -H 'Content-Type: application/json' \
  -d '{"traceId":"4bf92f3577b34da6a3ce929d0e0e4736","question":"왜 알림이 늦었어?"}'
```

응답과 동일한 리포트가 콘솔에 출력되고 `./reports/{traceId}-{ts}.json`에 저장됩니다.

## 환경변수

값은 전부 `.env`(gitignore됨)에서 주입합니다. `application.yml`에는 placeholder만 있습니다.

### Grafana Cloud

| 변수 | 값을 얻는 위치 |
|---|---|
| `TEMPO_URL` / `TEMPO_USER` | Grafana Cloud 콘솔 → 해당 Stack → **Tempo "Send Traces"** 카드의 URL과 User(숫자 인스턴스 ID) |
| `LOKI_URL` / `LOKI_USER` | 같은 Stack 화면의 **Loki "Send Logs"** 카드 |
| `MIMIR_URL` / `MIMIR_USER` | 같은 Stack 화면의 **Prometheus "Send Metrics"** 카드 |
| `GRAFANA_TOKEN` | 콘솔 → **Security → Access Policies** → `traces:read` `logs:read` `metrics:read` 스코프로 토큰 생성 |

세 소스는 인스턴스 ID가 서로 다르므로 URL/USER를 따로 설정하고, 토큰 하나를 공유합니다.
인증은 Basic Auth(`인스턴스ID:토큰`), 타임아웃은 연결 3s / 읽기 10s입니다.

### LLM

| 변수 | 값을 얻는 위치 |
|---|---|
| `RCA_LLM_PROVIDER` | `anthropic` \| `openai` \| `claude-cli` (기본값 `claude-cli`) |
| `ANTHROPIC_API_KEY` | console.anthropic.com → Settings → API Keys |
| `OPENAI_API_KEY` | platform.openai.com → API keys |
| `CLAUDE_CLI_PATH` | 로컬 `claude` 바이너리 절대경로. 비우면 PATH에서 찾습니다 |

`claude-cli`는 API 키 없이 구독 계정으로 로컬 실행할 때 씁니다.
`claude -p --output-format json`을 실행하고 stdout JSON에서 `result`와 `usage`를 뽑습니다.
프롬프트는 argv가 아니라 **stdin**으로 넘깁니다 — 조립된 컨텍스트가 수십 KB라 커맨드라인
길이 제한을 넘기기 때문입니다. `usage`가 없으면 토큰 수는 `-1`로 기록합니다.
프로세스 타임아웃은 120초이고, 비정상 종료 시 stderr가 에러 메시지에 포함됩니다.

### Notifier

| 변수 | 값을 얻는 위치 |
|---|---|
| `RCA_NOTIFIER` | `console` \| `slack` \| `discord` (기본값 `console`) |
| `SLACK_WEBHOOK_URL` | Slack 앱 → Incoming Webhooks → Add New Webhook to Workspace |
| `DISCORD_WEBHOOK_URL` | Discord 채널 → 채널 편집 → 연동 → 웹후크 → 새 웹후크 |

Slack/Discord는 SDK 없이 webhook POST 한 번입니다. 어느 notifier든 리포트 JSON은 항상 저장합니다.

## 수집 내용

`traceId` 하나로 시작합니다.

1. **Tempo** — `GET /api/traces/{traceId}`
2. **시간창** — 트레이스의 가장 이른 span 시작 ~ 가장 늦은 span 종료, 양쪽 ±2분
   (`rca.collect.window-padding-seconds`)
3. **Loki** — `/loki/api/v1/query_range` 2회
   - `{app=~"content|auth|chat"} | logfmt | level=~"ERROR|WARN"`
   - `{app=~"content|auth|chat"} |= "<traceId>"`
   - 레이블명은 `rca.collect.app-label` / `level-label`로 변경 가능
4. **Mimir** — `/prometheus/api/v1/query_range`
   - `hikaricp_connections_active`, `hikaricp_connections_pending`,
     `rate(jvm_gc_pause_seconds_sum[5m])`, `kafka_consumer_fetch_manager_records_lag`
   - 시리즈가 없으면 스킵하고 그 사실을 컨텍스트에 남깁니다

**한 소스가 실패해도 전체를 중단하지 않습니다.** 실패 사유를 컨텍스트의 `# 수집 실패/누락`
섹션에 적고 나머지로 진행하며, 모델에게 그 공백만큼 확신도를 낮추라고 지시합니다.
트레이스가 없어 시간창을 못 구하면 `now ± 2분`으로 대체합니다.

트레이스 JSON이 100KB(`rca.collect.max-trace-bytes`)를 넘으면 duration 상위 30개 span만 넣습니다.

## 측정

조사마다 리포트 JSON에 기록됩니다.

- 입력/출력 토큰 수 (provider가 usage를 안 주면 `-1`)
- 총 소요시간, 그리고 단계별 소요 — `tempoMs` / `lokiMs` / `mimirMs` / `assembleMs` / `llmMs`
- 컨텍스트 문자 수, 수집 실패 목록

외부 API 원본 응답은 재채점용으로 `./reports/raw/`에 전부 저장됩니다.

## 구조

인터페이스는 딱 2개입니다. 그 외에는 추상화하지 않았습니다.

```
client/     TempoClient, LokiClient, MimirClient, RawResponseStore
collector/  Collector, TimeWindow, TraceSpans, CollectedData
analyzer/   LlmClient(interface), LlmResult, ContextAssembler, SystemPrompt
            AnthropicLlmClient / OpenAiLlmClient / ClaudeCliLlmClient
notify/     Notifier(interface), ConsoleNotifier / SlackNotifier / DiscordNotifier
report/     RcaReport, Timings, ReportStore
```

`LlmClient`와 `Notifier` 구현체는 `@ConditionalOnProperty`로 `rca.llm.provider`,
`rca.notifier` 값에 따라 하나만 생성됩니다.

## 테스트

```bash
./gradlew test
```

- `TempoClientTest` / `LokiClientTest` — WireMock. Basic Auth 헤더, 쿼리 파라미터, 에러 전파
- `TimeWindowTest` — 시간창 계산, span 파싱, span 랭킹
- `RcaServiceFlowTest` — fake `LlmClient`로 전체 흐름. Loki가 죽은 상태에서도 완주하는지,
  트레이스 100KB 초과 시 상위 span만 남는지

## 범위 밖

v0에 넣지 않았습니다: 도구 호출·에이전트 루프(v2), 요약·병목 추출(v1),
webhook 수신·스케줄러(v0.5).
