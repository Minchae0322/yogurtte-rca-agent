# RCA Report — `traceId 없음`

| 항목 | 값 |
|---|---|
| 모드 | rca |
| 질문 | 최근 1시간 안에 팔로우 목록이 안 열린다는 제보가 있다. 원인을 조사해줘 |
| 시각 | 2026-07-28T15:44:03.849684Z |
| provider | claude-cli |
| model | `claude-opus-5` · turns 1 |
| prompt | `./prompts/system-prompt.md` |
| tokens | in 68418 (cacheRead 18,133 · cacheCreate 50,283) / out 8423 · cost $0.7599 |
| elapsed | total 127253ms (tempo 0 · loki 144 · mimir 607 · assemble 1 · llm 126495) |

## 탐색 (Triage)

| 항목 | 값 |
|---|---|
| 시간창 해석 | 상대 표현 '최근 1시간' |
| 스윕 창 | 2026-07-28T14:43:16.195012Z ~ 2026-07-28T15:43:16.195012Z |
| 좁힌 창 | 2026-07-28T15:34:00Z ~ 2026-07-28T15:43:16Z |
| 대상 | auth-service |
| traceId | (없음) |
| 트레이스 후보 | 1건 |
| 계획 파싱 | 성공 |
| prompt | `./prompts/triage-prompt.md` |
| tokens | in 42735 / out 3053 · cost $0.3754 |
| chars | 컨텍스트 38,103 + 프롬프트 1,196 = **39,299** |
| elapsed | survey 927ms · llm 46705ms |

**선정 이유**: 제보 시각 직전인 15:40:00Z 부근에서 auth-service만 로그 오류를 냈고 에러 트레이스는 0건이라, 인증 단계에서 목록 요청이 진입조차 못 했을 가능성을 확인하려면 이 구간의 auth-service 로그를 직접 봐야 한다.

**근거**

- Loki ERROR/WARN 발생률: auth-service가 1785253200(=2026-07-28T15:40:00Z) 버킷에서 4건. 조회 창 내 auth-service의 유일한 값이며, 창 후반 30분(15:10~15:43)에서 유일한 로그 이상 신호
- Tempo 에러 트레이스: auth-service 0건. 검색된 에러 트레이스는 content-service의 'http post /feeds/{feedId}/comments'(15:05:06Z, 82ms, errorCount 1) 하나뿐 — auth는 로그만 있고 트레이스가 없어, 스팬 생성 전 단계(인증/필터/게이트웨이 진입)에서 실패했을 가능성이 있음
- 인프라 정상 확인: up{job=auth-service, pod=auth-service-855c75679d-45fxb}=1 이 14:43:16~15:43:16 전 구간 유지, mongodb_up=1, kafka_brokers=1, 모든 consumergroup lag 0(-1은 유휴 파티션) → 노드/브로커/DB 레벨 장애가 아님
- chat 무관 판단 근거: websocket_active_users{chat-service}=0 이 전 구간 상수, chat-service 로그 ERROR/WARN 0건
- 제외한 후보: content-service 4건 @1785251400(=15:10:00Z) + 15:05:06Z 댓글 작성(POST comments) 에러 트레이스 — 제보 시각보다 30분 이상 앞서고 경로가 '팔로우 목록 조회'가 아닌 쓰기 경로라 증상과 불일치

**스윕이 찾은 트레이스** (고른 것은 (없음))

| traceId | root service | root span | ms |
|---|---|---|---:|
| `6a68c522cb16f0a29c2c4bd0a86df613` | content-service | http post /feeds/{feedId}/comments | 82 |

## 수집 범위 (Coverage)

- **window**: 2026-07-28T15:34:00Z ~ 2026-07-28T15:43:16Z (556s)
- **trace**: 0B / 0 spans
- **logs**: errwarn=5,728B · traceId=0B
- **metrics**: 8 수집 / 77,525B, 누락 [sum(rate(http_server_requests_seconds_count{application="content-service", status="401"}[1m]))]
- **context**: 83,989 chars (+ 시스템 프롬프트 575 chars)
- **contextTokens**: 측정 안 됨  ← 개선 지표 (count_tokens 실측, CLI 오버헤드 제외)

## 수집 실패/누락

- 이 조사에는 대표 traceId가 없다 — 탐색이 트레이스를 찾지 못했거나 트레이스가 생성되지 않는 장애다. 트레이스 부재 자체를 근거로 쓸 것.
- Metric 'sum(rate(http_server_requests_seconds_count{application="content-service", status="401"}[1m]))' returned no series in this window; skipped.

---

관측 데이터만으로 분석했습니다. 먼저 시각 정합성 확인: 로그의 `2026-07-29 00:35:24`는 KST 표기이며 UTC로는 `2026-07-28T15:35:24Z`, 즉 조회 창(15:34:00~15:43:16Z) 안에 정확히 들어옵니다.

## 1. 원인 후보 랭킹 (최대 3개)

1. **auth-service에서 처리되지 않은 예외 발생 (`GlobalExceptionHandler.handleAllException`)** — 창 내 유일한 애플리케이션 이상 신호이며, 동일 사용자(userId=1)에서 짧은 간격으로 2회 반복
2. **페이징 + 컬렉션 fetch join 쿼리의 인메모리 페이징 (`HHH90003004`)** — 팔로우 목록 같은 1:N 목록 조회에서 전형적으로 발생하는 패턴
3. **요청이 백엔드에 도달하지 못했거나(앞단 차단) 관측 파이프라인 결손으로 증거가 소실** — 데이터 부족

---

## 2. 후보별 근거

### 후보 1. auth-service 미처리 예외 (`handleAllException`)

- **근거:**
  - `15:35:35.428Z` — `[http-nio-8081-exec-5] WARN [traceId=6a68cc475f93de92df7ee7e4f4819181,spanId=cb78e51a928aca3d,userId=1] c.e.t.a.c.e.GlobalExceptionHandler - handleAllException`
  - `15:35:35.509Z` — `[http-nio-8081-exec-6] WARN [traceId=6a68cc4773eb172d28cae176f5463ec5,spanId=9cc8eb3fb5f9df86,userId=1] c.e.t.a.c.e.GlobalExceptionHandler - handleAllException`
  - 두 건은 **81ms 간격, 서로 다른 톰캣 워커 스레드(exec-5/exec-6), 서로 다른 traceId, 동일 userId=1**. 재시도 또는 중복 요청이 연속 실패한 형태로, "목록이 안 열린다"는 증상(응답 실패 → 클라이언트 재시도)과 부합합니다.
  - `handleAllException`은 이름 그대로 catch-all 핸들러이므로, 개별 예외 타입에 매핑되지 않은 예외 = 5xx 응답일 개연성이 큽니다.
  - 조회 창 전체에서 ERROR 로그는 0건, WARN은 총 4건뿐이며 **모두 auth-service**입니다. content-service / chat-service의 이상 로그는 전무합니다.
- **확신도: 중간**
- **반증 데이터:**
  - 로그 레벨이 ERROR가 아닌 **WARN**입니다. 이 핸들러가 4xx(클라이언트 오류)까지 WARN으로 남기는 구현이라면 정상 처리된 요청일 수 있습니다. **예외 클래스/메시지/요청 URI/응답 코드가 로그에 전혀 남아있지 않아** 5xx 단정이 불가합니다.
  - 대표 traceId로 Tempo 조회가 0건이라 이 traceId가 어떤 엔드포인트였는지 확인 불가.
  - 인프라 지표는 전 구간 정상: `up=1` (auth/chat/content/kafka/mongodb/redis/노드 전부), `hikaricp_connections_active=0`, `hikaricp_connections_pending=0` (3개 서비스 4개 파드 전 시점), auth GC pause rate 최대 `2.9166e-5 s/s`(5분간 누적 약 8.7ms). 즉 자원 고갈성 장애 흔적은 없습니다.

### 후보 2. 인메모리 페이징 (`HHH90003004`)

- **근거:**
  - `15:35:24.656Z` — `[http-nio-8081-exec-3] WARN [traceId=6a68cc3cab27948dfa49cdc595cd4862,spanId=9ae37752fca9feaa,userId=1] org.hibernate.orm.query - HHH90003004: firstResult/maxResults specified with collection fetch; applying in memory`
  - `15:36:36.152Z` — `[http-nio-8081-exec-7] ... userId=1 ... 동일 메시지 HHH90003004`
  - 이 경고는 `Pageable`과 컬렉션 fetch join을 함께 쓴 쿼리에서 Hibernate가 **전체 결과를 메모리에 적재한 뒤 페이징**할 때 발생합니다. 팔로우 목록처럼 사용자↔팔로우 관계(1:N)를 페이징 조회하는 경로의 전형적 시그니처이며, 데이터가 커지면 응답 지연·메모리 급증으로 목록 로딩 실패로 이어질 수 있습니다.
  - 시퀀스가 증상과 맞물립니다: `35:24` 문제 쿼리 → `35:35` 예외 2건 → `36:36` 문제 쿼리 재발생(재시도 추정). 모두 **동일 userId=1**.
- **확신도: 낮음**
- **반증 데이터:**
  - **대량 로딩의 흔적이 지표에 없습니다.** auth-service `hikaricp_connections_active`는 15:34~15:43 전 스크랩(15초 간격) 값이 모두 `0`이고, `hikaricp_connections_pending`도 전부 `0`입니다. 무거운 쿼리가 돌았다면 최소 일부 스크랩에서 active≥1이 잡혀야 합니다(단, 15초 스크랩 간격보다 짧은 쿼리는 포착 실패 가능).
  - auth-service GC pause rate는 `15:36:15Z` 이후 `0`으로 떨어집니다. 힙 압박 정황이 없습니다.
  - `HHH90003004`는 그 자체로 실패가 아니라 성능 경고입니다. `35:24` 경고와 `35:35` 예외는 **traceId가 서로 다르고 11초 떨어져 있어**, 이 둘의 인과관계를 데이터로 입증할 수 없습니다.

### 후보 3. 요청 미도달 / 관측 결손 — 데이터 부족

- **근거:**
  - **Tempo 트레이스 0건.** 그런데 auth-service 로그에는 `traceId=...,spanId=...`가 정상적으로 찍혀 있습니다. 즉 애플리케이션 내에서는 트레이스가 **생성되고 있으나 Tempo에 저장/조회되지 않는** 상태입니다 → 샘플링 설정 또는 OTLP export 파이프라인 결함 가능성. 이 경우 실제 실패 요청의 트레이스가 통째로 유실되어 "증거 없음 ≠ 문제 없음"입니다.
  - 트래픽 자체가 거의 없습니다: `websocket_active_users=0` (전 구간), 전 서비스 DB 커넥션 active=0, Loki가 창 전체에서 스캔한 로그가 444줄에 불과. content-service의 401 메트릭은 **시리즈 자체가 없음**(no series) — 창 내 401 응답이 없었거나 해당 HTTP 메트릭이 수집되지 않는다는 뜻이며, 두 해석을 구분할 데이터가 없습니다.
  - Kafka는 무관합니다: `kafka_brokers=1`, 모든 컨슈머그룹 lag `0` (일부 파티션의 `-1`은 커밋된 오프셋 없음을 의미하는 값이지 지연이 아님). 팔로우 목록은 읽기 경로이므로 content→Kafka→chat 파이프라인과 연결 근거도 없습니다.
- **확신도: 낮음**
- **반증 데이터:** auth-service에 `handleAllException` 2건이 실제로 기록되었으므로, 최소한 일부 요청은 백엔드에 도달해 서버 측에서 실패했습니다. "앞단에서 전부 차단" 가설과는 배치됩니다.

### 판단 불가로 명시할 항목 (데이터 부족)

- **팔로우 목록 엔드포인트를 어느 서비스가 소유하는지 주어지지 않았습니다.** 제시된 구성상 auth는 "인증 담당"인데, 유일한 오류 신호는 auth-service에만 있고 content-service에는 오류·트레이스·HTTP 지표가 전무합니다. 팔로우 목록이 content-service 소관이라면 **현재 수집분에는 원인 근거가 아예 없습니다.**
- **조회 창이 질문과 불일치합니다.** 질문은 "최근 1시간"이나 실제 수집 창은 `15:34:00Z~15:43:16Z`로 **약 9분 16초**뿐입니다. 나머지 약 50분은 미관측 구간입니다.
- HTTP 요청 수/상태코드/지연(`http_server_requests_seconds_*`) 데이터가 401 쿼리 1건(시리즈 없음)을 빼면 전혀 없어, **에러율·지연 상승 여부를 판정할 수 없습니다.**

> 종합: 트레이스 전면 부재 + HTTP 지표 부재 + 9분짜리 창이라는 세 가지 공백 때문에, 위 후보 중 어느 것도 확신도 "높음"으로 올릴 수 없습니다.

---

## 3. 권장 다음 조치

**즉시 (증거 확보)**

1. 예외 정체 확인 — 두 traceId로 레벨 무관 전체 로그 조회:
   `{job="default/auth-service"} |= "6a68cc475f93de92df7ee7e4f4819181"`, `{job="default/auth-service"} |= "6a68cc4773eb172d28cae176f5463ec5"`
   HHH90003004 건도 동일하게: `6a68cc3cab27948dfa49cdc595cd4862`, `6a68cc84b087dd795036cf7362eb02b9`
2. 조회 창을 **실제 1시간(15:00~16:00Z 이상)** 으로 넓혀 Loki ERROR/WARN 재수집. 현재 9분 창은 제보 시각을 담고 있다는 보장이 없습니다.
3. HTTP 상태코드/지연 메트릭 조회 (401 대신 전 상태·전 서비스):
   `sum by (application, uri, status) (rate(http_server_requests_seconds_count{status=~"4..|5.."}[5m]))`
   `histogram_quantile(0.99, sum by (le, application, uri) (rate(http_server_requests_seconds_bucket[5m])))`
   → 시리즈가 없으면 "장애 없음"이 아니라 **메트릭 미수집**이므로, actuator `management.metrics.web.server` 노출 여부부터 확인.
4. 팔로우 목록 API의 소유 서비스와 URI를 확정. content-service 소관이면 해당 파드 2개(`content-service-6c5fff897-qnxk6`, `-scw7k`) 로그를 별도 조회.

**단기 (관측 복구 — 이후 조사의 전제)**

5. Tempo 유실 원인 점검: 로그에 traceId는 찍히는데 트레이스가 0건 → 샘플링 확률(`management.tracing.sampling.probability`), OTLP exporter endpoint 설정, Tempo distributor 수신 여부를 순서대로 확인.
6. `GlobalExceptionHandler`의 로깅 개선: 예외 클래스·메시지·스택·요청 URI·응답 상태를 함께 남기고, 5xx는 ERROR 레벨로 승격. 현재 `handleAllException`이라는 메서드명만 남아 원인 규명이 불가능합니다.

**확인 후 조치**

7. 제보자 계정 ID를 확보해 로그의 `userId=1`과 동일인인지 대조하고, 해당 계정으로 팔로우 목록 조회를 재현.
8. `HHH90003004`를 유발하는 JPA 쿼리 특정(fetch join + `Pageable` 조합). 재현되면 ID 조회(페이징) → 상세 조회(fetch join) 2단계 분리 또는 `@BatchSize` 적용으로 수정.
9. Ingress/게이트웨이 액세스 로그로 해당 시각 팔로우 목록 요청의 도달 여부와 응답 코드를 확인 — 후보 3의 확정/기각용.

---

## 관측 증거 (Evidence)

> 이 조사가 실제로 본 값이다. 원본 응답 전체는 `reports/raw/scan-*.json`에 있다.

### 로그 원문 (4 / 전체 4줄)

```
2026-07-28T15:35:24.656259236Z  [auth-service]  [2m2026-07-29 00:35:24[0;39m [2m[http-nio-8081-exec-3][0;39m [33m WARN [traceId=6a68cc3cab27948dfa49cdc595cd4862,spanId=9ae37752fca9feaa,userId=1][0;39m [36morg.hibernate.orm.query[0;39m [2m-[0;39m HHH90003004: firstResult/maxResults specified with collection fetch; applying in memory
2026-07-28T15:35:35.428730891Z  [auth-service]  [2m2026-07-29 00:35:35[0;39m [2m[http-nio-8081-exec-5][0;39m [33m WARN [traceId=6a68cc475f93de92df7ee7e4f4819181,spanId=cb78e51a928aca3d,userId=1][0;39m [36mc.e.t.a.c.e.GlobalExceptionHandler[0;39m [2m-[0;39m handleAllException
2026-07-28T15:35:35.509824882Z  [auth-service]  [2m2026-07-29 00:35:35[0;39m [2m[http-nio-8081-exec-6][0;39m [33m WARN [traceId=6a68cc4773eb172d28cae176f5463ec5,spanId=9cc8eb3fb5f9df86,userId=1][0;39m [36mc.e.t.a.c.e.GlobalExceptionHandler[0;39m [2m-[0;39m handleAllException
2026-07-28T15:36:36.152338394Z  [auth-service]  [2m2026-07-29 00:36:36[0;39m [2m[http-nio-8081-exec-7][0;39m [33m WARN [traceId=6a68cc84b087dd795036cf7362eb02b9,spanId=347443c738b7f0d4,userId=1][0;39m [36morg.hibernate.orm.query[0;39m [2m-[0;39m HHH90003004: firstResult/maxResults specified with collection fetch; applying in memory
```

### 메트릭 시계열

| 쿼리 | series | 점 | min | max | last | 값이 0이던 구간 |
|---|---|---:|---:|---:|---:|---|
| `hikaricp_connections_active` | `{__name__=hikaricp_connections_active, application=auth-service, cluster=yogurtte-k3s-prod, container=auth-service, instance=10.42.1.34:8090, job=auth-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=auth-service-855c75679d-45fxb, pool=HikariPool-1, service=auth-service}` | 38 | 0 | 0 | 0 | **2026-07-28T15:34:00Z ~ 2026-07-28T15:43:15Z** |
| `hikaricp_connections_active` | `{__name__=hikaricp_connections_active, application=chat-service, cluster=yogurtte-k3s-prod, container=chat-service, instance=10.42.1.31:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-857c54dd97-s5fbl, pool=HikariPool-1}` | 38 | 0 | 0 | 0 | **2026-07-28T15:34:00Z ~ 2026-07-28T15:43:15Z** |
| `hikaricp_connections_active` | `{__name__=hikaricp_connections_active, application=content-service, cluster=yogurtte-k3s-prod, container=content-service, instance=10.42.1.35:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-6c5fff897-qnxk6, pool=HikariPool-1}` | 38 | 0 | 0 | 0 | **2026-07-28T15:34:00Z ~ 2026-07-28T15:43:15Z** |
| `hikaricp_connections_active` | `{__name__=hikaricp_connections_active, application=content-service, cluster=yogurtte-k3s-prod, container=content-service, instance=10.42.3.41:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-6c5fff897-scw7k, pool=HikariPool-1}` | 38 | 0 | 0 | 0 | **2026-07-28T15:34:00Z ~ 2026-07-28T15:43:15Z** |
| `hikaricp_connections_pending` | `{__name__=hikaricp_connections_pending, application=auth-service, cluster=yogurtte-k3s-prod, container=auth-service, instance=10.42.1.34:8090, job=auth-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=auth-service-855c75679d-45fxb, pool=HikariPool-1, service=auth-service}` | 38 | 0 | 0 | 0 | **2026-07-28T15:34:00Z ~ 2026-07-28T15:43:15Z** |
| `hikaricp_connections_pending` | `{__name__=hikaricp_connections_pending, application=chat-service, cluster=yogurtte-k3s-prod, container=chat-service, instance=10.42.1.31:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-857c54dd97-s5fbl, pool=HikariPool-1}` | 38 | 0 | 0 | 0 | **2026-07-28T15:34:00Z ~ 2026-07-28T15:43:15Z** |
| `hikaricp_connections_pending` | `{__name__=hikaricp_connections_pending, application=content-service, cluster=yogurtte-k3s-prod, container=content-service, instance=10.42.1.35:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-6c5fff897-qnxk6, pool=HikariPool-1}` | 38 | 0 | 0 | 0 | **2026-07-28T15:34:00Z ~ 2026-07-28T15:43:15Z** |
| `hikaricp_connections_pending` | `{__name__=hikaricp_connections_pending, application=content-service, cluster=yogurtte-k3s-prod, container=content-service, instance=10.42.3.41:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-6c5fff897-scw7k, pool=HikariPool-1}` | 38 | 0 | 0 | 0 | **2026-07-28T15:34:00Z ~ 2026-07-28T15:43:15Z** |
| `rate(jvm_gc_pause_seconds_sum[5m])` | `{action=end of major GC, application=chat-service, cause=Allocation Failure, cluster=yogurtte-k3s-prod, container=chat-service, gc=MarkSweepCompact, instance=10.42.1.31:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-857c54dd97-s5fbl}` | 38 | 0 | 0 | 0 | **2026-07-28T15:34:00Z ~ 2026-07-28T15:43:15Z** |
| `rate(jvm_gc_pause_seconds_sum[5m])` | `{action=end of minor GC, application=auth-service, cause=G1 Evacuation Pause, cluster=yogurtte-k3s-prod, container=auth-service, gc=G1 Young Generation, instance=10.42.1.34:8090, job=auth-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=auth-service-855c75679d-45fxb, service=auth-service}` | 38 | 0 | 0.000 | 0 | **2026-07-28T15:34:00Z ~ 2026-07-28T15:36:00Z, 2026-07-28T15:40:15Z ~ 2026-07-28T15:43:15Z** |
| `rate(jvm_gc_pause_seconds_sum[5m])` | `{action=end of minor GC, application=chat-service, cause=Allocation Failure, cluster=yogurtte-k3s-prod, container=chat-service, gc=Copy, instance=10.42.1.31:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-857c54dd97-s5fbl}` | 38 | 0.000 | 0.000 | 0.000 | — |
| `rate(jvm_gc_pause_seconds_sum[5m])` | `{action=end of minor GC, application=content-service, cause=Allocation Failure, cluster=yogurtte-k3s-prod, container=content-service, gc=Copy, instance=10.42.1.35:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-6c5fff897-qnxk6}` | 38 | 0 | 0.000 | 0 | **2026-07-28T15:34:00Z ~ 2026-07-28T15:37:00Z, 2026-07-28T15:41:15Z ~ 2026-07-28T15:43:15Z** |
| `rate(jvm_gc_pause_seconds_sum[5m])` | `{action=end of minor GC, application=content-service, cause=Allocation Failure, cluster=yogurtte-k3s-prod, container=content-service, gc=Copy, instance=10.42.3.41:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-6c5fff897-scw7k}` | 38 | 0 | 0.000 | 0 | **2026-07-28T15:34:00Z ~ 2026-07-28T15:37:45Z, 2026-07-28T15:42:00Z ~ 2026-07-28T15:43:15Z** |
| `up` | `{__name__=up, app=node-exporter, cluster=yogurtte-k3s-prod, component=metrics, container=node-exporter, instance=ip-172-31-40-241, job=integrations/node_exporter, k8s_cluster_name=yogurtte-k3s-prod, namespace=monitoring, pod=grafana-k8s-monitoring-node-exporter-cph5l, source=kubernetes, workload=DaemonSet/grafana-k8s-monitoring-node-exporter}` | 38 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, app=node-exporter, cluster=yogurtte-k3s-prod, component=metrics, container=node-exporter, instance=ip-172-31-45-39, job=integrations/node_exporter, k8s_cluster_name=yogurtte-k3s-prod, namespace=monitoring, pod=grafana-k8s-monitoring-node-exporter-9kvqt, source=kubernetes, workload=DaemonSet/grafana-k8s-monitoring-node-exporter}` | 38 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, cluster=yogurtte-k3s-prod, container=auth-service, instance=10.42.1.34:8090, job=auth-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=auth-service-855c75679d-45fxb}` | 38 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, cluster=yogurtte-k3s-prod, container=chat-service, instance=10.42.1.31:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-857c54dd97-s5fbl}` | 38 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, cluster=yogurtte-k3s-prod, container=content-service, instance=10.42.1.35:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-6c5fff897-qnxk6}` | 38 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, cluster=yogurtte-k3s-prod, container=content-service, instance=10.42.3.41:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-6c5fff897-scw7k}` | 38 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, cluster=yogurtte-k3s-prod, instance=10.42.1.248:8080, job=integrations/kubernetes/kube-state-metrics, k8s_cluster_name=yogurtte-k3s-prod, source=kubernetes}` | 38 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, cluster=yogurtte-k3s-prod, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod}` | 38 | 1 | 1 | 1 | — |
| `mongodb_up` | `{__name__=mongodb_up, cluster=yogurtte-k3s-prod, instance=infra-server, job=mongodb, k8s_cluster_name=yogurtte-k3s-prod}` | 38 | 1 | 1 | 1 | — |
| `kafka_brokers` | `{__name__=kafka_brokers, cluster=yogurtte-k3s-prod, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod}` | 38 | 1 | 1 | 1 | — |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=chat-service-fcm-tokens, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=0, topic=user.fcm-tokens}` | 38 | 0 | 0 | 0 | **2026-07-28T15:34:00Z ~ 2026-07-28T15:43:15Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=chat-service-fcm-tokens, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=1, topic=user.fcm-tokens}` | 38 | 0 | 0 | 0 | **2026-07-28T15:34:00Z ~ 2026-07-28T15:43:15Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=chat-service-fcm-tokens, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=2, topic=user.fcm-tokens}` | 38 | 0 | 0 | 0 | **2026-07-28T15:34:00Z ~ 2026-07-28T15:43:15Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=chat-service-notification-settings, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=0, topic=user.notification-settings}` | 38 | 0 | 0 | 0 | **2026-07-28T15:34:00Z ~ 2026-07-28T15:43:15Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=chat-service-notification-settings, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=1, topic=user.notification-settings}` | 38 | 0 | 0 | 0 | **2026-07-28T15:34:00Z ~ 2026-07-28T15:43:15Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=chat-service-notification-settings, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=2, topic=user.notification-settings}` | 38 | 0 | 0 | 0 | **2026-07-28T15:34:00Z ~ 2026-07-28T15:43:15Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=db-writer, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=0, topic=chat.messages}` | 38 | 0 | 0 | 0 | **2026-07-28T15:34:00Z ~ 2026-07-28T15:43:15Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=db-writer, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=1, topic=chat.messages}` | 38 | 0 | 0 | 0 | **2026-07-28T15:34:00Z ~ 2026-07-28T15:43:15Z** |
| `websocket_active_users` | `{__name__=websocket_active_users, application=chat-service, cluster=yogurtte-k3s-prod, container=chat-service, instance=10.42.1.31:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-857c54dd97-s5fbl}` | 38 | 0 | 0 | 0 | **2026-07-28T15:34:00Z ~ 2026-07-28T15:43:15Z** |

값이 0이던 구간은 굵게 표시했다 — 프로세스가 사라져 시계열이 꺾인 것이 유일한 신호인 장애가 있다.

