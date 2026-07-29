# RCA Report — `6a69e01bc0116147bba4b75907e9d57e`

| 항목 | 값 |
|---|---|
| 모드 | rca |
| 질문 | 최근 1시간 안에 로그인이 안 된다는 문의가 몰렸다. 원인을 조사해줘 |
| 시각 | 2026-07-29T11:20:25.973550Z |
| provider | claude-cli |
| model | `claude-opus-5` · turns 1 |
| prompt | `./prompts/system-prompt.md` |
| tokens | in 108653 (cacheRead 18,133 · cacheCreate 90,518) / out 11192 · cost $1.2634 |
| elapsed | total 174724ms (tempo 515 · loki 181 · mimir 582 · assemble 1 · llm 170343) |

## 탐색 (Triage)

| 항목 | 값 |
|---|---|
| 시간창 해석 | 상대 표현 '최근 1시간' |
| 스윕 창 | 2026-07-29T10:19:43.244067Z ~ 2026-07-29T11:19:43.244067Z |
| 좁힌 창 | 2026-07-29T11:00:00Z ~ 2026-07-29T11:19:43Z |
| 대상 | auth-service, content-service |
| traceId | 6a69e01bc0116147bba4b75907e9d57e |
| 트레이스 후보 | 1건 |
| 계획 파싱 | 성공 |
| prompt | `./prompts/triage-prompt.md` |
| tokens | in 43166 / out 2928 · cost $0.3769 |
| chars | 컨텍스트 39,007 + 프롬프트 1,196 = **40,203** |
| elapsed | survey 1008ms · llm 41710ms |

**선정 이유**: auth-service의 up 시계열이 11:04:43Z~11:14:43Z 사이 완전히 끊기고 새 파드로 교체된 구간이 로그인 실패 문의 시각과 일치하므로, 그 앞뒤 여유를 둔 11:00~11:19:43Z에서 auth 파드의 종료 사유(OOM/크래시/프로브 실패)와 그 여파를 본 content-service 에러 트레이스를 함께 확인한다.

**근거**

- up{job="auth-service", pod="auth-service-855c75679d-pr892", instance="10.42.1.38:8090"} 시계열이 11:04:43Z(1785323083)를 마지막으로 종료 — 이후 샘플 없음
- 11:09:43Z(1785323383) 스크레이프 시점에 auth-service 시리즈가 0개 — 다른 모든 job(chat-service, content-service, kafka, mongodb, redis, kubelet)은 13개 샘플 전 구간 연속 up=1이라 수집 파이프라인 장애가 아님
- 11:14:43Z(1785323683)부터 파드 이름·IP가 바뀐 auth-service-855c75679d-glc4w(10.42.1.40:8090)로 up=1 재개 — 동일 ReplicaSet 내 파드 재생성(크래시/OOM/축출) 정황, 약 10분 공백
- auth 레플리카가 전 구간 1개뿐이라 이 공백 = 인증 100% 단절, '로그인 불가' 증상과 시각 일치
- Loki ERROR/WARN: auth-service가 11:10:00Z(1785323400)에 2건, 11:15:00Z(1785323700)에 1건 — 다운/재기동 구간에 정확히 몰려 있음
- Tempo 에러 트레이스는 auth-service가 아닌 content-service 1건뿐(11:12:27Z, http get /feeds/following, errorCount 3/4 span) — auth 프로세스 부재로 자기 트레이스는 생성 불가, 하위 호출자에서만 실패가 관측된 형태
- content-service ERROR/WARN도 11:15:00Z에 2건으로 같은 시각에만 등장 — auth 의존 호출 실패의 파급으로 의심
- kafka_brokers=1, mongodb_up=1, 노드 up 전부 1로 인프라 공통 장애 근거 없음 → auth 단일 워크로드 문제로 좁혀짐

**스윕이 찾은 트레이스** (고른 것은 6a69e01bc0116147bba4b75907e9d57e)

| traceId | root service | root span | ms |
|---|---|---|---:|
| `6a69e01bc0116147bba4b75907e9d57e` ←선택 | content-service | http get /feeds/following | 4 |

## 수집 범위 (Coverage)

- **window**: 2026-07-29T11:00:00Z ~ 2026-07-29T11:19:43Z (1183s)
- **trace**: 4,369B / 4 spans
- **logs**: errwarn=7,216B · traceId=4,636B
- **metrics**: 8 수집 / 138,272B, 누락 [sum(rate(http_server_requests_seconds_count{application="content-service", status="401"}[1m]))]
- **context**: 155,137 chars (+ 시스템 프롬프트 575 chars)

### 토큰 축 (개선 지표)

| 단계 | 총 in | chars | 컨텍스트 토큰 |
|---|---:|---:|---:|
| 탐색 | 43,166 | 40,203 | 21,857 |
| 분석 | 108,653 | 155,712 | 87,344 |
| **합계** | **151,819** | | **109,201** |

- **overheadTokens 21,309 tok — 이 회차에 실측**(1자 프롬프트 프로브, 같은 명령·모델·샌드박스).
  호출마다 한 번씩 얹히므로 단계별로 뺀다. **다른 날 상수를 쓰지 않았다 = `█ 실측`.**
- contextTokens (count_tokens API): 측정 안 됨 — 구독 CLI 경로엔 API 키가 없다
- 비용은 **API 환산 추정치**다. 구독 계정으로 호출하므로 실제 청구액이 아니다.

## 수집 실패/누락

- Metric 'sum(rate(http_server_requests_seconds_count{application="content-service", status="401"}[1m]))' returned no series in this window; skipped.

---

# 장애 분석 — traceId `6a69e01bc0116147bba4b75907e9d57e`

**먼저 짚을 관측 한계**: 문의는 "최근 1시간"인데 조회 창은 **11:00:00~11:19:43(19분 43초)**뿐이다. 또한 확보된 실패 트레이스는 **1건**이며, `status="401"` 메트릭은 수집 실패했다. 아래 확신도는 이 공백을 반영해 전반적으로 낮춰 잡았다.

**타임라인(UTC, 로그는 KST=UTC+9 → 환산)**
- 11:09:13 / 11:09:34 / 11:10:14 — `auth-service-855c75679d-glc4w` Spring Boot **기동 중** WARN 3건 (`[main]` 스레드, BeanPostProcessor / hibernate.dialect / open-in-view)
- 11:09:15 — 기존 파드 `auth-service-855c75679d-pr892`(10.42.1.38) 시계열 **마지막 샘플**
- 11:11:30 — 신규 파드 `glc4w`(10.42.1.40) 시계열 **최초 샘플**
- 11:12:27.030~.035 — 문제 트레이스: content-service에서 JWT 서명 검증 실패

## 1. 원인 후보 랭킹 (최대 3개)

1. **auth-service 파드 교체(11:09~11:11)로 JWT 서명 키가 바뀌어, content-service의 토큰 검증이 실패**
2. **auth-service 인스턴스 공백 구간(11:09:15~11:11:30, 약 2분 15초) 동안 로그인 요청 자체가 실패**
3. **JWT 검증 예외가 401이 아닌 500으로 새어나가, 클라이언트의 토큰 갱신/재로그인 플로우가 동작하지 않음 (영향 확대 + 관측 왜곡 요인)**

## 2. 후보별 근거

### 후보 1 — auth-service 재기동에 따른 JWT 서명 키 불일치

- **근거**
  - 문제 트레이스의 서버 span `http get /feeds/following`(content-service, 파드 `content-service-6c5fff897-qnxk6`, 11:12:27.030→.035, 4.9ms)에 다음 속성이 붙어 있다:
    - `exception = "SignatureException"`
    - `error = "JWT signature does not match locally computed signature. JWT validity cannot be asserted and should not be trusted."`
    - `outcome = "SERVER_ERROR"`, `status = "500"`
  - 자식 span `secured request`(11:12:27.031→.033)와 `security filterchain after`에도 동일한 `error` 문자열이 기록됨 → 인증 필터 체인 내부에서 발생한 서명 검증 실패다. `security filterchain before` span의 이벤트 목록에 `before JwtAuthenticationFilter`가 포함되어 있어, JWT 검증 지점이 이 요청 경로에 실재함이 확인된다.
  - "서명 불일치"는 만료/형식 오류가 아니라 **검증 키가 발급 키와 다르다**는 의미다. 발급 주체(auth-service)는 11:09~11:11 사이에 파드가 교체되었고(`pr892` 시계열 종료 11:09:15 → `glc4w` 시계열 시작 11:11:30), 검증 주체(content-service) 두 파드(`qnxk6`, `scw7k`)는 조회 창 전 구간에서 `up=1`로 **재기동 흔적이 없다**. 즉 검증 측 키는 그대로인데 발급 측만 바뀐 타이밍과, 그 직후(약 1~3분 뒤) 서명 불일치가 발생한 순서가 일치한다.
  - 두 파드의 ReplicaSet 해시가 `855c75679d`로 **동일**하다 → 이미지/설정 변경 배포가 아니라 동일 템플릿의 파드 재기동이다. 따라서 "설정에서 키를 바꿨다"보다 **"기동 시마다 서명 키가 새로 생성되는 구조"** 쪽이 데이터와 더 잘 맞는다. (단, 키 소스 자체를 확인한 데이터는 없음 → 아래 반증/부족 참조)
  - 사용자 체감: 로그인 후 발급받은 토큰이 content-service에서 거부되면 화면상 "로그인이 안 된다"로 인지된다.
- **확신도: 중간**
- **반증 데이터**
  - 동일 traceId의 content-service 애플리케이션 로그는 **성공으로 기록**되어 있다: `[HTTP] GET /api/feeds/following 200 - 4ms` (11:12:27.034, `RequestLoggingFilter`). span의 `status=500`과 정면으로 배치된다.
  - auth-service 측에는 이 가설을 뒷받침할 **직접 증거가 없다**. 조회된 auth-service 로그는 11:09:13/11:09:34/11:10:14의 기동 WARN 3건뿐이고, 키 로딩·토큰 발급 관련 로그나 ERROR가 전혀 없다.
  - **표본 1건**이다. "문의가 몰렸다"에 상응하는 다건 발생을 관측으로 확인하지 못했다 → **데이터 부족**.
  - 실패한 URI가 `/api/feeds/following`(피드 조회)이며, 로그인 엔드포인트 자체의 실패 트레이스는 확보되지 않았다.

### 후보 2 — auth-service 단일 인스턴스 교체 구간의 로그인 실패

- **근거**
  - `up{job="auth-service"}` 시계열에서 **11:09:15(1785323355) 이후 11:11:30(1785323490) 이전 구간에 어떤 auth-service 인스턴스도 존재하지 않는다.** `pr892`는 11:09:15에 끊기고 `glc4w`는 11:11:30에 시작한다. 약 **135초**의 공백.
  - 조회 창 전체에서 auth-service는 **항상 1개 파드만** 관측된다(동시 존재 구간 없음) → 교체 중 무중단 여력이 없다.
  - `glc4w`의 기동 WARN이 11:09:13~11:10:14에 걸쳐 있어, 구 파드가 사라진 시점(11:09:15)에 신규 파드는 아직 **기동 중**이었다.
  - 이 구간의 로그인 요청은 전부 실패했을 것이고, 이는 문의 유입의 트리거 시점으로 자연스럽다.
- **확신도: 낮음**
- **반증 데이터**
  - 정작 조사 대상 실패(11:12:27)는 신규 파드가 이미 스크레이프되던(11:11:30~) **이후**에 발생했다. 이 후보만으로는 11:12:27의 서명 불일치를 설명할 수 없다.
  - `up` 시계열의 부재는 "스크레이프 타깃 부재"이지 "요청 실패"의 직접 증거가 아니다. 5xx/요청 실패 카운터, k8s 이벤트, `kube_pod_container_status_restarts_total`을 확보하지 못했다 → **데이터 부족**.
  - 공백은 2분 15초인데 문의는 1시간 규모로 몰렸다고 한다. 시간 규모가 맞지 않는다.

### 후보 3 — JWT 검증 예외가 401이 아닌 500으로 누수

- **근거**
  - span 속성이 `status="500"`, `outcome="SERVER_ERROR"`, `status.code=STATUS_CODE_ERROR`다. 인증 실패인데 401이 아니다.
  - `security filterchain after` span이 `spring.security.filterchain.position="0"`, `spring.security.filterchain.size="0"`, `spring.security.reached.filter.name="none"`으로 기록되었다 — 필터 체인을 정상 통과하지 못하고 예외로 빠져나간 형태다. `before` span에 `ExceptionTranslationFilter`(position 12/12)가 체인에 존재함에도 401 변환이 이루어지지 않았다.
  - content-service ERROR 로그(11:12:27.036, 파드 `qnxk6`): `o.a.c.c.C.[.[.[.[dispatcherServlet] - Servlet.service() for servlet [dispatcherServlet] in context with path [/api] threw exception` — 예외가 서블릿 레벨까지 전파되었다. 시각이 span 종료(11:12:27.0358)와 1~2ms 차이로 동일 요청으로 보인다(해당 로그의 traceId는 `NONE`으로 기록되어 있어 확정은 불가).
  - 수집 실패한 `status="401"` 메트릭이 **해당 창에 시계열 자체가 없었다**는 사실은, 401이 실제로 발생하지 않고 500으로 집계되었을 가능성과 정합적이다(쿼리/라벨 오류 가능성도 동일하게 남아 있음).
  - 영향: 클라이언트가 401 대신 500을 받으면 토큰 재발급/재로그인 유도 로직이 돌지 않아, 사용자는 계속 실패 상태에 갇힌다 → "로그인이 안 된다" 문의로 이어진다.
- **확신도: 중간** (500 처리 사실은 근거가 명확. 다만 이것이 "원인"인지 "증상 확대 요인"인지는 후보 1의 진위에 종속)
- **반증 데이터**
  - 같은 요청에 대해 `RequestLoggingFilter`는 `200`을 기록했다. 최종 응답이 실제로 무엇이었는지(500인지 200인지) 관측값이 서로 모순되며, 클라이언트가 무엇을 받았는지는 확정할 수 없다 → **데이터 부족**.
  - dispatcherServlet ERROR 로그의 traceId가 `NONE`이라 동일 요청 귀속은 시각 근접에 의한 추정이다.

### 이번 데이터로 **배제**되는 방향 (모두 정상)

- 인프라/자원: `up`은 node-exporter, kubelet, cadvisor, kube-state-metrics, kafka, mongodb, redis, node-infra 전부 전 구간 `1`. `mongodb_up=1`, `kafka_brokers=1` 유지.
- DB 커넥션 고갈: `hikaricp_connections_pending`이 auth/chat/content **모든 파드에서 전 구간 0**. `hikaricp_connections_active`는 최대 1.
- GC: 최대치가 auth `glc4w`의 `0.00117 s/s`(약 0.1%), 나머지는 1e-5~1e-4 수준. STW 지연 근거 없음.
- Kafka 소비 지연: 모든 컨슈머 그룹 lag 0 (`-1`은 미할당 파티션 표기). content→Kafka→chat 알림 경로는 이번 건과 무관.
- 참고로 content-service에 `[HTTP-SLOW] GET /api/feeds/following 200 - 2617ms`(11:12:21, traceId `6a69e012bbcec3537d988d4f0a9ddefd`)가 있으나, **다른 트레이스이고 상태 200**이라 로그인 실패와의 연결 근거가 없다.

## 3. 권장 다음 조치

**A. 후보 1 확정/기각 (최우선)**
1. auth-service와 content-service의 JWT 서명 키 출처 비교 — Secret/ConfigMap 마운트인지, 기동 시 생성인지 확인.
   `kubectl get deploy auth-service content-service -o yaml | grep -iA3 -e jwt -e secret`, `kubectl exec <auth-pod> -- env | grep -i jwt`
2. 양쪽 키의 **해시만** 비교(값 노출 금지): 두 파드에서 키 문자열의 SHA-256을 찍어 일치 여부 확인.
3. 재현: (a) 지금 로그인해 새 토큰 발급 → `GET /api/feeds/following` 호출 결과, (b) 11:09 이전 발급 토큰으로 동일 호출 → `SignatureException` 재현 여부. (a)만 성공하면 "재기동으로 구 토큰 전량 무효화"가 확정된다.
4. auth-service 파드를 한 번 더 재기동한 뒤 직전 발급 토큰이 깨지는지 확인 → "기동 시 키 재생성" 여부 판정.

**B. 규모 확인 (현재 표본 1건 → 반드시 채울 것)**
5. Tempo에서 `11:00~12:00 전체(및 문의 유입 시각 포함)` 범위로 `service.name=content-service && exception=SignatureException` 검색 → 발생 건수/시작 시각 집계. 11:09~11:11을 기점으로 급증하는지 확인.
6. Loki: `{namespace="default"} |= "JWT signature does not match"` 를 1시간 전체로 조회, 서비스별 분포 확인.
7. 실패한 401 메트릭 대체 조회: `sum by (status,uri) (rate(http_server_requests_seconds_count{application="content-service"}[1m]))` — 401이 정말 없는지, 500으로 계상되는지 확인. 라벨명이 `application`이 맞는지도 함께 검증.

**C. 후보 2 확정 (파드 교체 원인)**
8. `kubectl get events --sort-by=.lastTimestamp | grep auth-service` 및 `kube_pod_container_status_restarts_total{pod=~"auth-service.*"}`, `kube_pod_status_phase` 조회 → `pr892` 종료 사유(OOMKilled / Evicted / 노드 드레인 / 수동 롤아웃) 확정.
9. auth-service `replicas`, `strategy.rollingUpdate.maxUnavailable`, readiness/liveness probe 설정 확인. 단일 레플리카 + `maxUnavailable>0`이면 교체 시 로그인 전면 중단이 구조적으로 발생한다.

**D. 즉시 완화**
10. 확인 결과가 "기동 시 키 재생성"이면 → 서명 키를 고정 Secret으로 외부화하고 auth/content 양쪽이 동일 Secret을 참조하도록 변경(비대칭 키라면 JWKS 엔드포인트 방식 검토).
11. auth-service `replicas ≥ 2` + `maxUnavailable: 0`으로 조정.
12. content-service의 JWT 검증 예외를 500이 아닌 **401**로 변환(`JwtAuthenticationFilter`에서 `SignatureException`/`JwtException` 포착 → `AuthenticationEntryPoint` 위임). 클라이언트 토큰 갱신 플로우가 살아난다.

**E. 관측성 결함 (원인 조사와 별개로 반드시 수정)**
13. 동일 요청에 대해 span은 `500`, `RequestLoggingFilter`는 `200 - 4ms`로 기록되는 모순을 조사 — 필터가 응답 상태를 읽는 시점이 커밋 이전일 가능성. 이 상태로는 향후 장애의 성공/실패 판정을 로그로 신뢰할 수 없다.
14. content-service의 `dispatcherServlet` ERROR 로그에 traceId가 `NONE`으로 남는 문제(MDC 전파 누락) 수정 — 이번에도 예외 스택을 요청에 귀속시키지 못했다.

---

## 관측 증거 (Evidence)

> 이 조사가 실제로 본 값이다. 원본 응답 전체는 `reports/raw/6a69e01bc0116147bba4b75907e9d57e-*.json`에 있다.

### span (duration 상위 4 / 전체 4)

| ms | service | span | 시작 |
|---:|---|---|---|
| 4.93 | content-service | `http get /feeds/following` | 2026-07-29T11:12:27.030890Z |
| 2.64 | content-service | `secured request` | 2026-07-29T11:12:27.031268Z |
| 0.19 | content-service | `security filterchain after` | 2026-07-29T11:12:27.033952Z |
| 0.14 | content-service | `security filterchain before` | 2026-07-29T11:12:27.031101Z |

### 로그 원문 (6 / 전체 6줄)

```
2026-07-29T11:09:13.276751279Z  [auth-service]  [2m2026-07-29 20:09:13[0;39m [2m[main][0;39m [33m WARN [traceId=NONE,spanId=NONE,userId=NONE][0;39m [36mo.s.c.s.PostProcessorRegistrationDelegate$BeanPostProcessorChecker[0;39m [2m-[0;39m Bean 'org.springframework.ws.config.annotation.DelegatingWsConfiguration' of type [org.springframework.ws.config.annotation.DelegatingWsConfiguration$$SpringCGLIB$$0] is not eligible for getting processed by all BeanPostProcessors (for example: not eligible for auto-proxying). The currently created BeanPostProcessor [annotationActionEndpointMapping] is declared through a non-static factory method on that class; consider declaring it as static instead.
2026-07-29T11:09:34.613785842Z  [auth-service]  [2m2026-07-29 20:09:34[0;39m [2m[main][0;39m [33m WARN [traceId=NONE,spanId=NONE,userId=NONE][0;39m [36morg.hibernate.orm.deprecation[0;39m [2m-[0;39m HHH90000025: MySQLDialect does not need to be specified explicitly using 'hibernate.dialect' (remove the property setting and it will be selected by default)
2026-07-29T11:10:14.975874812Z  [auth-service]  [2m2026-07-29 20:10:14[0;39m [2m[main][0;39m [33m WARN [traceId=NONE,spanId=NONE,userId=NONE][0;39m [36mo.s.b.a.o.j.JpaBaseConfiguration$JpaWebConfiguration[0;39m [2m-[0;39m spring.jpa.open-in-view is enabled by default. Therefore, database queries may be performed during view rendering. Explicitly configure spring.jpa.open-in-view to disable this warning
2026-07-29T11:12:21.267580890Z  [content-service]  2026-07-29 20:12:21.267 [http-nio-8082-exec-3]  WARN [traceId=6a69e012bbcec3537d988d4f0a9ddefd,spanId=7d988d4f0a9ddefd,userId=NONE] c.e.t.a.c.f.RequestLoggingFilter - [HTTP-SLOW] GET /api/feeds/following 200 - 2617ms
2026-07-29T11:12:27.034807237Z  [content-service]  2026-07-29 20:12:27.034 [http-nio-8082-exec-2]  INFO [traceId=6a69e01bc0116147bba4b75907e9d57e,spanId=bba4b75907e9d57e,userId=NONE] c.e.t.a.c.f.RequestLoggingFilter - [HTTP] GET /api/feeds/following 200 - 4ms
2026-07-29T11:12:27.039009175Z  [content-service]  2026-07-29 20:12:27.036 [http-nio-8082-exec-2] ERROR [traceId=NONE,spanId=NONE,userId=NONE] o.a.c.c.C.[.[.[.[dispatcherServlet] - Servlet.service() for servlet [dispatcherServlet] in context with path [/api] threw exception
```

### 메트릭 시계열

| 쿼리 | series | 점 | min | max | last | 값이 0이던 구간 |
|---|---|---:|---:|---:|---:|---|
| `hikaricp_connections_active` | `{__name__=hikaricp_connections_active, application=auth-service, cluster=yogurtte-k3s-prod, container=auth-service, instance=10.42.1.38:8090, job=auth-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=auth-service-855c75679d-pr892, pool=HikariPool-1, service=auth-service}` | 38 | 0 | 0 | 0 | **2026-07-29T11:00:00Z ~ 2026-07-29T11:09:15Z** |
| `hikaricp_connections_active` | `{__name__=hikaricp_connections_active, application=auth-service, cluster=yogurtte-k3s-prod, container=auth-service, instance=10.42.1.40:8090, job=auth-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=auth-service-855c75679d-glc4w, pool=HikariPool-1, service=auth-service}` | 33 | 0 | 1 | 0 | **2026-07-29T11:11:30Z ~ 2026-07-29T11:12:15Z, 2026-07-29T11:13:30Z ~ 2026-07-29T11:19:30Z** |
| `hikaricp_connections_active` | `{__name__=hikaricp_connections_active, application=chat-service, cluster=yogurtte-k3s-prod, container=chat-service, instance=10.42.1.39:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-857c54dd97-w7bf7, pool=HikariPool-1}` | 79 | 0 | 0 | 0 | **2026-07-29T11:00:00Z ~ 2026-07-29T11:19:30Z** |
| `hikaricp_connections_active` | `{__name__=hikaricp_connections_active, application=content-service, cluster=yogurtte-k3s-prod, container=content-service, instance=10.42.1.35:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-6c5fff897-qnxk6, pool=HikariPool-1}` | 79 | 0 | 1 | 0 | **2026-07-29T11:00:00Z ~ 2026-07-29T11:00:00Z, 2026-07-29T11:02:15Z ~ 2026-07-29T11:10:00Z, 2026-07-29T11:11:15Z ~ 2026-07-29T11:14:00Z, 2026-07-29T11:16:15Z ~ 2026-07-29T11:19:30Z** |
| `hikaricp_connections_active` | `{__name__=hikaricp_connections_active, application=content-service, cluster=yogurtte-k3s-prod, container=content-service, instance=10.42.3.41:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-6c5fff897-scw7k, pool=HikariPool-1}` | 79 | 0 | 0 | 0 | **2026-07-29T11:00:00Z ~ 2026-07-29T11:19:30Z** |
| `hikaricp_connections_pending` | `{__name__=hikaricp_connections_pending, application=auth-service, cluster=yogurtte-k3s-prod, container=auth-service, instance=10.42.1.38:8090, job=auth-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=auth-service-855c75679d-pr892, pool=HikariPool-1, service=auth-service}` | 38 | 0 | 0 | 0 | **2026-07-29T11:00:00Z ~ 2026-07-29T11:09:15Z** |
| `hikaricp_connections_pending` | `{__name__=hikaricp_connections_pending, application=auth-service, cluster=yogurtte-k3s-prod, container=auth-service, instance=10.42.1.40:8090, job=auth-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=auth-service-855c75679d-glc4w, pool=HikariPool-1, service=auth-service}` | 33 | 0 | 0 | 0 | **2026-07-29T11:11:30Z ~ 2026-07-29T11:19:30Z** |
| `hikaricp_connections_pending` | `{__name__=hikaricp_connections_pending, application=chat-service, cluster=yogurtte-k3s-prod, container=chat-service, instance=10.42.1.39:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-857c54dd97-w7bf7, pool=HikariPool-1}` | 79 | 0 | 0 | 0 | **2026-07-29T11:00:00Z ~ 2026-07-29T11:19:30Z** |
| `hikaricp_connections_pending` | `{__name__=hikaricp_connections_pending, application=content-service, cluster=yogurtte-k3s-prod, container=content-service, instance=10.42.1.35:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-6c5fff897-qnxk6, pool=HikariPool-1}` | 79 | 0 | 0 | 0 | **2026-07-29T11:00:00Z ~ 2026-07-29T11:19:30Z** |
| `hikaricp_connections_pending` | `{__name__=hikaricp_connections_pending, application=content-service, cluster=yogurtte-k3s-prod, container=content-service, instance=10.42.3.41:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-6c5fff897-scw7k, pool=HikariPool-1}` | 79 | 0 | 0 | 0 | **2026-07-29T11:00:00Z ~ 2026-07-29T11:19:30Z** |
| `rate(jvm_gc_pause_seconds_sum[5m])` | `{action=end of major GC, application=chat-service, cause=Allocation Failure, cluster=yogurtte-k3s-prod, container=chat-service, gc=MarkSweepCompact, instance=10.42.1.39:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-857c54dd97-w7bf7}` | 79 | 0 | 0 | 0 | **2026-07-29T11:00:00Z ~ 2026-07-29T11:19:30Z** |
| `rate(jvm_gc_pause_seconds_sum[5m])` | `{action=end of minor GC, application=auth-service, cause=G1 Evacuation Pause, cluster=yogurtte-k3s-prod, container=auth-service, gc=G1 Young Generation, instance=10.42.1.38:8090, job=auth-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=auth-service-855c75679d-pr892, service=auth-service}` | 50 | 0 | 0 | 0 | **2026-07-29T11:00:00Z ~ 2026-07-29T11:12:15Z** |
| `rate(jvm_gc_pause_seconds_sum[5m])` | `{action=end of minor GC, application=auth-service, cause=G1 Evacuation Pause, cluster=yogurtte-k3s-prod, container=auth-service, gc=G1 Young Generation, instance=10.42.1.40:8090, job=auth-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=auth-service-855c75679d-glc4w, service=auth-service}` | 29 | 0 | 0.001 | 0 | **2026-07-29T11:17:30Z ~ 2026-07-29T11:19:30Z** |
| `rate(jvm_gc_pause_seconds_sum[5m])` | `{action=end of minor GC, application=chat-service, cause=Allocation Failure, cluster=yogurtte-k3s-prod, container=chat-service, gc=Copy, instance=10.42.1.39:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-857c54dd97-w7bf7}` | 79 | 0.000 | 0.000 | 0.000 | — |
| `rate(jvm_gc_pause_seconds_sum[5m])` | `{action=end of minor GC, application=content-service, cause=Allocation Failure, cluster=yogurtte-k3s-prod, container=content-service, gc=Copy, instance=10.42.1.35:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-6c5fff897-qnxk6}` | 79 | 0 | 0.000 | 0.000 | **2026-07-29T11:03:15Z ~ 2026-07-29T11:08:00Z, 2026-07-29T11:12:15Z ~ 2026-07-29T11:18:00Z** |
| `rate(jvm_gc_pause_seconds_sum[5m])` | `{action=end of minor GC, application=content-service, cause=Allocation Failure, cluster=yogurtte-k3s-prod, container=content-service, gc=Copy, instance=10.42.3.41:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-6c5fff897-scw7k}` | 79 | 0 | 0.000 | 0 | **2026-07-29T11:00:00Z ~ 2026-07-29T11:02:45Z, 2026-07-29T11:07:00Z ~ 2026-07-29T11:12:45Z, 2026-07-29T11:17:00Z ~ 2026-07-29T11:19:30Z** |
| `up` | `{__name__=up, app=node-exporter, cluster=yogurtte-k3s-prod, component=metrics, container=node-exporter, instance=ip-172-31-40-241, job=integrations/node_exporter, k8s_cluster_name=yogurtte-k3s-prod, namespace=monitoring, pod=grafana-k8s-monitoring-node-exporter-cph5l, source=kubernetes, workload=DaemonSet/grafana-k8s-monitoring-node-exporter}` | 79 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, app=node-exporter, cluster=yogurtte-k3s-prod, component=metrics, container=node-exporter, instance=ip-172-31-45-39, job=integrations/node_exporter, k8s_cluster_name=yogurtte-k3s-prod, namespace=monitoring, pod=grafana-k8s-monitoring-node-exporter-9kvqt, source=kubernetes, workload=DaemonSet/grafana-k8s-monitoring-node-exporter}` | 79 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, cluster=yogurtte-k3s-prod, container=auth-service, instance=10.42.1.38:8090, job=auth-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=auth-service-855c75679d-pr892}` | 38 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, cluster=yogurtte-k3s-prod, container=auth-service, instance=10.42.1.40:8090, job=auth-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=auth-service-855c75679d-glc4w}` | 33 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, cluster=yogurtte-k3s-prod, container=chat-service, instance=10.42.1.39:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-857c54dd97-w7bf7}` | 79 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, cluster=yogurtte-k3s-prod, container=content-service, instance=10.42.1.35:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-6c5fff897-qnxk6}` | 79 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, cluster=yogurtte-k3s-prod, container=content-service, instance=10.42.3.41:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-6c5fff897-scw7k}` | 79 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, cluster=yogurtte-k3s-prod, instance=10.42.1.248:8080, job=integrations/kubernetes/kube-state-metrics, k8s_cluster_name=yogurtte-k3s-prod, source=kubernetes}` | 79 | 1 | 1 | 1 | — |
| `mongodb_up` | `{__name__=mongodb_up, cluster=yogurtte-k3s-prod, instance=infra-server, job=mongodb, k8s_cluster_name=yogurtte-k3s-prod}` | 79 | 1 | 1 | 1 | — |
| `kafka_brokers` | `{__name__=kafka_brokers, cluster=yogurtte-k3s-prod, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod}` | 79 | 1 | 1 | 1 | — |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=chat-service-fcm-tokens, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=0, topic=user.fcm-tokens}` | 79 | 0 | 0 | 0 | **2026-07-29T11:00:00Z ~ 2026-07-29T11:19:30Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=chat-service-fcm-tokens, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=1, topic=user.fcm-tokens}` | 79 | 0 | 0 | 0 | **2026-07-29T11:00:00Z ~ 2026-07-29T11:19:30Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=chat-service-fcm-tokens, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=2, topic=user.fcm-tokens}` | 79 | 0 | 0 | 0 | **2026-07-29T11:00:00Z ~ 2026-07-29T11:19:30Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=chat-service-notification-settings, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=0, topic=user.notification-settings}` | 79 | 0 | 0 | 0 | **2026-07-29T11:00:00Z ~ 2026-07-29T11:19:30Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=chat-service-notification-settings, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=1, topic=user.notification-settings}` | 79 | 0 | 0 | 0 | **2026-07-29T11:00:00Z ~ 2026-07-29T11:19:30Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=chat-service-notification-settings, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=2, topic=user.notification-settings}` | 79 | 0 | 0 | 0 | **2026-07-29T11:00:00Z ~ 2026-07-29T11:19:30Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=db-writer, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=0, topic=chat.messages}` | 79 | 0 | 0 | 0 | **2026-07-29T11:00:00Z ~ 2026-07-29T11:19:30Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=db-writer, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=1, topic=chat.messages}` | 79 | 0 | 0 | 0 | **2026-07-29T11:00:00Z ~ 2026-07-29T11:19:30Z** |
| `websocket_active_users` | `{__name__=websocket_active_users, application=chat-service, cluster=yogurtte-k3s-prod, container=chat-service, instance=10.42.1.39:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-857c54dd97-w7bf7}` | 79 | 0 | 0 | 0 | **2026-07-29T11:00:00Z ~ 2026-07-29T11:19:30Z** |

값이 0이던 구간은 굵게 표시했다 — 프로세스가 사라져 시계열이 꺾인 것이 유일한 신호인 장애가 있다.

