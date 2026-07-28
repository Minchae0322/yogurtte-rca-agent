# RCA Report — `6a680b56067d9e2387043740be2cb115`

| 항목 | 값 |
|---|---|
| 모드 | rca |
| 질문 | 팔로우 목록 조회가 실패했다는 제보가 있다. 원인을 조사해줘 |
| 시각 | 2026-07-28T01:58:51.746690300Z |
| provider | claude-cli |
| prompt | `.\prompts\system-prompt.md` |
| tokens | in 38662 / out 8773 · cost $0.4199 |
| elapsed | total 131144ms (tempo 850 · loki 183 · mimir 358 · assemble 1 · llm 129732) |

## 수집 범위 (Coverage)

- **window**: 2026-07-28T01:50:22.547345Z ~ 2026-07-28T01:54:22.685094Z (240s)
- **trace**: 5,475B / 4 spans
- **logs**: errwarn=3,957B · traceId=3,956B
- **metrics**: 3 수집, 누락 [kafka_consumer_fetch_manager_records_lag]
- **context**: 22,665 chars (~5,666 tok 추정)

## 수집 실패/누락

- Metric 'kafka_consumer_fetch_manager_records_lag' returned no series in this window; skipped.

---

관측 데이터만으로 분석했습니다. 결론부터: **이 트레이스의 실패는 auth-service 내부에서 발생했고, content→Kafka→chat 경로와는 무관합니다.** 다만 로그가 한 줄도 수집되지 않아 근본 원인(어떤 예외/어떤 의존성)은 특정할 수 없습니다.

## 1. 원인 후보 랭킹 (최대 3개)

1. **auth-service 애플리케이션 로직 자체의 처리 실패** — 인증 통과 후 `secured request` 구간(컨트롤러/서비스 계층)에서 500 응답 생성
2. **계측되지 않은 다운스트림 의존성(DB 쿼리 또는 외부 호출) 실패** — `secured request` 93.8ms 구간에 자식 span이 0개라 확인 불가
3. **JWT 검증 경로(JwtFilter) 관련 이상** — 필터 체인 내 ~43ms 공백 존재. 단, 실패 증거는 없음

## 2. 후보별 근거

### 후보 1. auth-service 내부 로직 실패

- **근거:**
  - 루트 span `http get /user/{userid}/following` (SPAN_KIND_SERVER, parentSpanId 없음)이 `"outcome":"SERVER_ERROR"`, `"status":"500"`, `"http.url":"/api/user/1/following"`. 즉 500을 만들어낸 주체는 auth-service(`auth-service-855c75679d-45fxb`, 10.42.1.34)이고, 이 트레이스에 다른 서비스 span은 **하나도 없음**.
  - 인증/인가는 통과했음: `security filterchain before`가 `filterchain.position=16 / size=16`으로 체인 끝까지 도달했고, 그 뒤 `secured request` span(590433000→684191000, **93.76ms**)이 정상 생성·종료됨. `security filterchain after`도 16/16으로 정상 완료(684241000→684357000, 0.116ms). → 401/403이 아니라 보안 통과 후의 500.
  - 전체 소요 **137.7ms**(547345000→685094000). 타임아웃·자원 고갈 패턴이 아닌 **빠른 실패**.
  - `"exception":"none"` — Spring의 `http.server.requests` 관측에 예외가 기록되지 않은 채 500. 서블릿 컨테이너까지 예외가 전파되지 않았다는 의미로, `@ControllerAdvice`/`@ExceptionHandler`가 예외를 잡아 500으로 변환했거나 코드가 직접 500을 반환한 경로일 가능성이 큼(계측 설정에 따라 달라질 수 있어 확정은 아님).
- **확신도:** **중간** — "실패 지점이 auth-service 내부"라는 위치 특정은 근거가 확실하나, **왜** 실패했는지는 로그 0건이라 판정 불가. 수집 공백을 감안해 한 단계 낮춤.
- **반증 데이터:** 없음. (오히려 자원 계열 원인을 배제하는 관측치가 이 후보를 지지: 01:52:22 시점 `hikaricp_connections_active{auth-service}=0`, `pending=0`, 창 전체 동일. auth-service `rate(jvm_gc_pause_seconds_sum[5m])`는 요청 시점까지 `0`, 이후에도 최대 `0.000425`로 무시 가능.)

### 후보 2. 계측 누락된 다운스트림(DB/외부 호출) 실패

- **근거:** `secured request`(93.76ms)가 루트 span 137.7ms의 68%를 차지하는데 그 안에 **자식 span이 전혀 없음**. 팔로우 목록 조회가 DB 쿼리나 타 서비스 호출을 수반한다면 JDBC/HTTP 클라이언트 계측이 꺼져 있어 실패 지점이 가려졌을 수 있음.
- **확신도:** **낮음** — 부재의 증거(span 없음)에 기댄 추론이며, 호출이 실제로 있었는지조차 확인되지 않음.
- **반증 데이터:**
  - `hikaricp_connections_active{application="auth-service"}` = 01:50:22~01:54:22 전 구간 **0**, `hikaricp_connections_pending` = 전 구간 **0** → 커넥션 풀 고갈/대기 없음. (단 스크레이프 간격 15s로 93ms짜리 커넥션 사용은 놓칠 수 있어 "DB 접근 자체가 없었다"의 증거는 아님.)
  - 137.7ms의 빠른 실패는 DB/네트워크 타임아웃 패턴과 배치됨.

### 후보 3. JWT 검증 경로(JwtFilter) 이상

- **근거:** `security filterchain before` 이벤트 타임스탬프가 두 덩어리로 갈림. `1785203542547000000`에 9개(`before DisableEncodeUrlFilter`, `before WebAsyncManagerIntegrationFilter`, `before SecurityContextHolderFilter`, `before HeaderWriterFilter`, `before CorsFilter`, `before LogoutFilter`, `before OAuth2AuthorizationRequestRedirectFilter`, `before OAuth2LoginAuthenticationFilter`, **`before JwtFilter`**), 이후 `1785203542590000000`에 나머지 7개(`before RequestCacheAwareFilter`, `before SecurityContextHolderAwareRequestFilter`, `before AnonymousAuthenticationFilter`, `before SessionManagementFilter`, `before ExceptionTranslationFilter` 등). → JwtFilter 진입 이후 **약 43ms 공백**, 필터 체인 전체가 42.6ms 소요.
- **확신도:** **낮음** — 지연은 관측되나 실패를 시사하는 값은 없음.
- **반증 데이터:** 필터 체인이 16/16으로 완주했고 `secured request` span이 생성됨 → 인증이 차단된 것이 아님. 응답 코드도 401/403이 아닌 **500**.

### 명시적으로 배제한 것

- **content → Kafka → chat 경로:** 이 트레이스에 content/chat/Kafka 관련 span이 전무하며, 실패한 엔드포인트는 auth-service가 서빙함. `content-service-6c5fff897-qnxk6`의 `hikaricp_connections_active=1`(01:51:07~01:52:52)은 별개 작업이며 이 traceId와 연결되는 관측치가 없음. 또한 `kafka_consumer_fetch_manager_records_lag`는 **수집 실패**라 컨슈머 지연은 판단 불가 — 다만 이번 500과의 인과는 어차피 데이터상 성립하지 않음.

### 데이터 부족 (판단 불가 영역)

**500의 실제 예외 종류·스택트레이스·실패 메시지는 판정 불가.** Loki 두 쿼리 모두 `"result":[]`이고 `totalLinesProcessed: 0`, `totalEntriesReturned: 0` — 즉 **필터링으로 0건이 아니라 스캔 대상 라인 자체가 0건**. 이는 "에러 로그가 없다"의 증거가 아니라 **로그 수집/라벨링 파이프라인 공백**의 증거로 보아야 함. 이 공백 때문에 후보 1의 확신도를 높음이 아닌 중간으로 둠.

## 3. 권장 다음 조치

**즉시 (근본 원인 확보)**
1. 파드 로그 직접 확인 — Loki 우회:
   `kubectl logs auth-service-855c75679d-45fxb -n default --since-time=2026-07-28T01:50:00Z | grep -i -A30 "6a680b56067d9e2387043740be2cb115\|following"`
2. 재현 시도: `GET /api/user/1/following`을 동일 토큰으로 호출해 응답 바디/에러코드 확인, 다른 `userId`와 비교(특정 데이터 이슈 vs 엔드포인트 전면 장애 구분).
3. 500 발생 범위 확인:
   `sum by (status) (rate(http_server_requests_seconds_count{job="auth-service", uri="/user/{userId}/following"}[5m]))` — 단발성인지 지속성인지, 그리고 배포 시각과의 상관.

**관측 공백 복구 (재발 시 즉시 진단 가능하게)**
4. 로그 수집 라인 점검: Alloy가 `auth-service` 파드 stdout을 수집 중인지, Loki 라벨(`job`/`app`/`namespace`) 이름이 쿼리와 일치하는지 확인. 창 내 전체 라인이 0건이라 라벨 불일치 또는 수집 누락이 유력.
5. 로그-트레이스 상관 활성화: logging pattern에 `%mdc{trace_id}`/`%mdc{span_id}` 주입 여부 확인(미주입 시 traceId 검색이 영구히 0건).
6. JDBC/HTTP 클라이언트 계측 추가(`datasource-micrometer`, RestTemplate/WebClient Observation) — `secured request` 93.8ms 내부가 완전 블랙박스인 상태 해소.

**코드 확인**
7. `exception="none"` + 500 조합의 출처 추적: `@ControllerAdvice`가 예외를 로깅 없이 500으로 변환하고 있는지 확인. 있다면 에러 로깅 추가가 최우선 수정 항목.
8. JwtFilter 내부 ~43ms 소요 구간에 원격 호출(JWKS 조회, 토큰 introspection, DB 조회)이 있는지 확인 후 타임아웃·캐시 설정 점검. (이번 실패의 직접 원인은 아니지만 잠재 위험)

**부수**
9. `kafka_consumer_fetch_manager_records_lag` 시리즈 부재 원인 확인(컨슈머 메트릭 미노출 또는 스크레이프 누락) — 이번 건과 무관하나 Kafka 경로 장애 시 진단 불가 상태.
