# AU-2 회차 1 — auth 전면 다운 2분 24초: 캐시 히트로 흡수

## 한눈 요약

| | |
|---|---|
| **실제 원인** | `kubectl scale deploy/auth-service --replicas=0` — auth 전면 다운 2분 24초 |
| **실제 영향** | 로그인 503(직접 경로 전면 불가) / content 피드 200 유지·작성자 실명·지연 +32.8ms — 영향이 로그인에 국한 |
| **에이전트 파악 원인** | "auth-service 파드(단일 레플리카)가 가용성을 상실 — 인증 토큰 발급 경로가 죽어 로그인 실패" **확신도 중간 — 정답**. 트레이스가 무신호인데 **메트릭 시계열 단절만으로** 도달 |
| **판정** | 원인 정답 / **복구 판정 오판**("이후 복구되지 않음" — 실제는 01:22:24 원복). 원인은 수집 창(01:20:12~01:24:13)이 신 파드 등장(01:25:28)보다 **1분 15초 짧았던 것** |
| **§8 채점** | **채점 불가 (앵커 부적합)** — 오귀인 **20/20** · 조치 **10/10**은 만점이나, 근본원인·근거경로는 v1 앵커가 갈래 B(미스 혼재)를 가정해 이 회차에 **발생하지 않은 신호**를 요구한다. [채점 대장](../scoring/README.md#au-2-회차-1--채점-불가-앵커-부적합) |
| **토큰·비용·시간** | in 65,414 / out 14,027 tok · **$0.8357** · 209.2s (5회 중 최장·최다 출력) |
| **에이전트 보고서 전문** | [round-1-rca-report.md](round-1-rca-report.md) |

## 장애 상황

- 주입: master 셸에서 `kubectl -n $NS scale deploy/auth-service --replicas=0`
  — **01:20:00 ~ 01:22:24 UTC (2분 24초)** = KST 10:20:00 ~ 10:22:24
- 트리거: 별도 트리거 없음. 채록 자체가 트리거를 겸한다(로그인 직접 호출 + T2 피드 스크롤).
- 채록 시점: **01:22:12Z — 주입 후 2분 12초**. user 캐시 TTL 10분의 22% 지점이라
  **캐시 만료 전**이 보장된다 (만료 후는 별도 문항 AU-4).
- 결말: 원복(`--replicas=1`) 후 rollout + 로그인 200 복귀 폴링.

실행: `./chaos.sh AU-2 run` (수동 단계 모드가 아닌 `run` — 주입 시점부터 Ctrl-C 자동 원복 trap 적용)

증거 원본 (서버 `~/chaos/`):
- baseline `scenarios/AU-2/evidence/baseline/20260727T011957Z/`
- symptom &nbsp;`scenarios/AU-2/evidence/symptom/20260727T011957Z/`

## 실제 신호 발췌

**하네스 실측 — baseline(01:19:57Z) vs symptom(01:22:12Z)**

| 프로브 | baseline | symptom | 해석 |
|---|---|---|---|
| 로그인 `POST /auth/login` | **200** | **503** | 직접 경로만 죽음. ingress에 ready 엔드포인트가 없어 503 (502 아님) |
| T2 `GET /content/feeds/scroll?size=10` | **200** / 0.207877s | **200** / 0.240686s | 5xx 무변화, **+32.8ms** |
| T2 작성자 익명 수 | 0 | **0** | `t2()`의 익명 fallback note 미출력 = 작성자 전원 실명 |

**+32.8ms가 이 회차의 핵심 수치다.** connection refused조차 왕복 비용이 붙고, 3s timeout이면
말할 것도 없다. 이 정도 델타는 노이즈 수준이고 — content가 auth를 **호출조차 하지 않았다**는
뜻이다. 즉 이 창에서 user 캐시는 100% 히트했다.

**로그인 503에는 트레이스가 없다.** auth pod이 0이므로 요청이 애플리케이션에 도달하지 못하고
ingress에서 끊긴다. 사용자에게 가장 큰 영향을 준 경로가 관측 데이터에 트레이스로는 남지
않는다 — 이 문항의 구조적 특징.

## 스크린샷용 traceId

| 용도 | traceId | spans | duration |
|---|---|---|---|
| **장애 창 트레이스** (symptom T2, 01:22:12Z) | `6a66b2c439929c47d4c8f275d8cc6986` | 65 | 123.24ms |
| **정상 대조 트레이스** (baseline T2, 01:19:58Z) | `6a66b23e67012d810f0c1ed88d681523` | 65 | 165.75ms |
| (참고) 주입 13초 후 T2 | `6a66b24de69eda4be36799bfa0bff872` | — | 103ms |

`measure_AU_2`가 `tempo_search`를 호출하지 않아 자동 수집되지 않았다. 2026-07-27 Tempo API로
소급 확보했다 (`{resource.service.name="content-service" && name=~"http get /feeds/scroll.*"}`,
01:15~01:23:20Z).

## 소급 채록 — 관측 3채널 ground truth

**`measure_AU_2`는 `loki_count`·`prom`·`tempo_search`를 하나도 호출하지 않는다.** 형제 문항
`measure_AU_4`(fallback 로그 카운트 + client p99)보다 빈약해, 하네스가 남긴 증거는 HTTP
프로브 2건뿐이었다. 아래는 채점의 정답 근거를 확보하기 위해 **RCA 조사 전에** Grafana API로
직접 조회한 결과다 (조사 후에 정답을 만들면 §8 블라인드가 깨진다).

### ① Trace — **완전 무신호**

두 트레이스가 **구조적으로 동일하다**:

| | baseline (01:19:58) | symptom (01:22:12) |
|---|---|---|
| span 수 | **65** | **65** |
| 구성 | `http get /feeds/scroll` → `secured request` → `connection` → `query`×N | **동일** |
| `user-service` client span | **없음** | **없음** |
| duration | 165.75ms | **123.24ms** (오히려 빠름) |

**auth가 살아 있을 때도 호출하지 않는다** — user 캐시 TTL 10분 히트라 baseline에서도
`GET user-service` span이 없다. 따라서 **트레이스만으로는 auth 다운을 판별할 수단이 원리적으로
없다.** v0의 입력 전제("traceId 하나로 시작")가 이 문항에서 가장 불리하게 걸리는 지점이다.

### ② Metrics — **유일한 실시간 신호** (`application` 레이블)

`jvm_memory_used_bytes{application="auth-service"}` 원시 스크레이프(60초 간격):

```
구 파드 10.42.3.38:8090   ... 01:19:00 · 01:20:00 · 01:21:00   ← 마지막
                          ─────  공백 4분 28초  ─────
신 파드 10.42.3.40:8090                          01:25:28 · 01:26:28   ← 재등장
```

- content-service는 같은 창에서 **16 시리즈 연속** — 대조군이 성립한다.
- 주입(01:20:00) 이후에도 01:21:00 샘플이 한 번 더 찍혔다 = graceful shutdown 잔여.
  원복(01:22:24) 후 신 파드 첫 스크레이프까지 3분 = Spring Boot 기동 지연.
  **관측되는 공백(01:21:00~01:25:28)은 실제 주입 창(01:20:00~01:22:24)보다 뒤로 밀려 있다.**
- **같은 공백이 에이전트의 `metric-queries`에도 그대로 잡힌다** — `hikaricp_connections_active`,
  `jvm_gc_pause_seconds_sum` 모두 `application="auth-service"`로 01:21:00에 끊기고 01:25:28에
  **다른 instance로** 부활한다. 즉 **에이전트는 CH-2에서 성공했던 "메트릭 고고학"과 똑같은
  경로로 도달할 수 있다.** 데이터는 있다.

### ③ Logs — 침묵이 아니라 **재기동 버스트**

`count_over_time({service_name="auth-service"}[1m])`:

```
01:15 ~ 01:22   0건          ← 주입 전에도 0건
01:23:04~       118줄 버스트  ← [main] 스레드 Spring Boot 스타트업 로그
```

**"로그 침묵"은 이 문항의 신호가 아니다.** auth-service는 평시에도 로그가 0건이라 침묵에
변별력이 없다. 실제 신호는 **01:23:04부터의 재기동 로그**이고, 이건 원복 이후 사건이다.
(→ 앵커 v2 갈래 A의 "로그 침묵" 요건은 이 실측으로 폐기해야 한다. 아래 참조)

부수 확인: 로그 라인이 ANSI 색코드가 섞인 평문 Logback 포맷(` INFO [traceId=NONE,...]`)이라
`level`이 key=value가 아니다 — rca-agent `errorWarnQuery`의 `| logfmt | level=~` 파이프라인이
셀렉터와 무관하게 빈 결과를 내는 이유가 실물로 확인됐다.

## 원인 대조

| | 내용 |
|---|---|
| **실제 원인** | auth-service replicas=0 (전면 다운). content는 user 캐시(TTL 10분) 히트로 auth 의존이 드러나지 않아 무영향 |
| **정답지** (RUNBOOK:351) | "auth 전면 다운. content는 캐시 히트분 정상 + 미스분 익명 — fallback 설계 검증." 이번 회차는 미스분이 0이라 **앞 절반만 발현**했다 |
| **에이전트 파악 원인** | "auth-service 파드가 가용성 상실 → 인증 토큰 발급 경로 사망 → 로그인 실패" **정답**. 근거는 전적으로 메트릭: `hikaricp_connections_active`·`hikaricp_connections_pending`·`rate(jvm_gc_pause_seconds_sum[5m])` **3개 계열이 동일 timestamp에 동시 종료**, 같은 창의 content·chat은 끝까지 연속, 후속 파드 시계열 미등장 |
| **근거 경로 백미** | ① **같은 노드 배제** — auth 파드 IP `10.42.3.38`과 같은 `/24` 대역의 `content-service` 파드(`10.42.3.39`)가 정상 보고함을 근거로 노드 장애를 배제. CH-1 회차2에서 "같은 호스트 Redis 정상"으로 호스트 장애를 배제한 것과 **같은 추론 패턴이 재현**됐다. ② **자기 결함 진단** — Loki 응답의 `totalStreams: 0`을 근거로 "매칭이 없었던 게 아니라 **라벨 셀렉터가 어떤 스트림도 매치하지 못했다**"고 특정하고, "지금 상태에서는 '로그에 에러가 없다'를 어떤 근거로도 사용할 수 없다"고 못박았다 |
| **오판 1건 — 복구 판정** | "그 이후 복구되지 않음", "지속적 부재(CrashLoopBackOff 또는 미복구)". 실제로는 01:22:24에 원복됐다. **에이전트 잘못이 아니다** — 수집 창이 01:20:12~01:24:13이고 신 파드 첫 스크레이프는 **01:25:28**이라 복구 증거가 창 **밖**이다. `RCA_WINDOW_PADDING_SECONDS=120`이 이 문항의 복구 관측에 부족하다 |
| **시각 정확도** | **-120s 밀림 미재발**. 리포트가 `1785115317 = 01:21:57Z`, `1785115452 = 01:24:12Z` 등 raw ns 변환을 전부 정확히 했다. 5회 중 3회 재발·2회 미재발 |
| **판정** | 원인 1줄 정답, 근거 경로 우수, 오귀인 없음. 복구 판정만 오판이고 그 원인은 수집 창 설정이다 |

## 회차 0(무효)과의 차이

지난 회차는 `chaos.sh`의 t2 프로브가 `/feeds/scroll`을 `size` 없이 호출해 content 잠복 버그
(`FeedService` NPE)로 500이 떴고, HTTP 코드를 검증하지 않아 **그 500이 조용히 통과**했다.
"auth를 내렸더니 content가 500"으로 읽힐 뻔한 오염이었다.

이번 회차는 그 두 가지가 모두 닫힌 상태에서 실행됐다:
- content `1e7df3f` — `/feeds/scroll` size 미지정 NPE 수정
- chaos `f473eea` — t2가 `?size=10` 명시 + `json_or_gate`로 HTTP 코드 검증

실제로 baseline·symptom 모두 T2가 HTTP 200으로 게이트를 통과했다 — 프로브 수정이 제 역할을
했음이 이 회차 자체로 확인된다.
