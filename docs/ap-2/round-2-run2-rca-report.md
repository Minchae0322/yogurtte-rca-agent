# RCA Report — `traceId 없음`

| 항목 | 값 |
|---|---|
| 모드 | rca |
| 질문 | 최근 1시간 안에 팔로우 목록이 안 열린다는 제보가 있다. 원인을 조사해줘 |
| 시각 | 2026-07-29T09:11:28.876340300Z |
| provider | claude-cli |
| model | `claude-opus-5` · turns 1 |
| prompt | `.\prompts\system-prompt.md` |
| tokens | in 90743 (cacheRead 23,453 · cacheCreate 67,288) / out 11435 · cost $1.0216 |
| elapsed | total 177391ms (tempo 0 · loki 157 · mimir 700 · assemble 3 · llm 169776) |

## 탐색 (Triage)

| 항목 | 값 |
|---|---|
| 시간창 해석 | 상대 표현 '최근 1시간' |
| 스윕 창 | 2026-07-29T08:10:24.265906300Z ~ 2026-07-29T09:10:24.265906300Z |
| 좁힌 창 | 2026-07-29T08:48:00Z ~ 2026-07-29T09:02:00Z |
| 대상 | auth-service, content-service |
| traceId | (없음) |
| 트레이스 후보 | 1건 |
| 계획 파싱 | 성공 |
| prompt | `.\prompts\triage-prompt.md` |
| tokens | in 48016 / out 4089 · cost $0.4036 |
| chars | 컨텍스트 38,143 + 프롬프트 1,231 = **39,374** |
| elapsed | survey 2005ms · llm 62566ms |

**선정 이유**: 1시간 전체에서 유일하게 정상 상태에서 벗어난 신호가 08:55:00Z의 auth+content 동시 로그 버스트이고, 팔로우 목록 조회가 통과하는 두 서비스와 정확히 겹치므로 그 앞뒤 여유를 둔 08:48~09:02 구간의 두 서비스 로그 원문을 확인해야 한다.

**근거**

- Loki ERROR/WARN 시계열에서 전 구간 유일한 비영 지점: ts=1785315300 (2026-07-29T08:55:00Z) 에 auth-service 4건, content-service 4건이 동시 발생. 나머지 55분은 두 서비스 모두 데이터포인트 자체가 없음(0건)
- auth와 content가 같은 버킷에서 함께 튄 점 — 팔로우 목록 조회는 인증(auth) + 팔로우 데이터(content) 두 경로를 모두 경유하므로 증상과 일치
- Tempo 에러 트레이스는 전 구간 1건뿐이며(6a69bf23e45c1e51c3475f5e5f3a1b04, content-service, POST /feeds/{feedId}/comments, 08:51:47Z 시작, 119ms, errorCount=1) 팔로우 목록과 엔드포인트가 다름 → 팔로우 목록 실패는 에러 트레이스가 아예 생성되지 않았다는 사실 자체가 근거(요청 미진입 또는 에러 미마킹)
- 인프라 이상 없음: up 메트릭이 auth(10.42.1.38)/chat(10.42.1.39)/content(10.42.1.35, 10.42.3.41) 전 파드 08:10~09:10 내내 1, 결측·재시작 흔적 없음. mongodb_up=1, kafka_brokers=1 유지
- kafka_consumergroup_lag 전 컨슈머그룹 0 유지(-1 값은 미할당 파티션으로 전 구간 상수) → 이벤트 파이프라인 지연 아님
- websocket_active_users=0 이 전 구간 변화 없는 상수 → chat-service 측 변화 신호 없음, 조사 대상에서 제외

**스윕이 찾은 트레이스** (고른 것은 (없음))

| traceId | root service | root span | ms |
|---|---|---|---:|
| `6a69bf23e45c1e51c3475f5e5f3a1b04` | content-service | http post /feeds/{feedId}/comments | 119 |

## 수집 범위 (Coverage)

- **window**: 2026-07-29T08:48:00Z ~ 2026-07-29T09:02:00Z (840s)
- **trace**: 0B / 0 spans
- **logs**: errwarn=7,428B · traceId=0B
- **metrics**: 8 수집 / 105,247B, 누락 [sum(rate(http_server_requests_seconds_count{application="content-service", status="401"}[1m]))]
- **context**: 113,411 chars (+ 시스템 프롬프트 597 chars)

### 토큰 축 (개선 지표)

| 단계 | 총 in | chars | 컨텍스트 토큰 |
|---|---:|---:|---:|
| 탐색 | 48,016 | 39,374 | 21,440 |
| 분석 | 90,743 | 114,008 | 64,167 |
| **합계** | **138,759** | | **85,607** |

- **overheadTokens 26,576 tok — 이 회차에 실측**(1자 프롬프트 프로브, 같은 명령·모델·샌드박스).
  호출마다 한 번씩 얹히므로 단계별로 뺀다. **다른 날 상수를 쓰지 않았다 = `█ 실측`.**
- contextTokens (count_tokens API): 측정 안 됨 — 구독 CLI 경로엔 API 키가 없다
- 비용은 **API 환산 추정치**다. 구독 계정으로 호출하므로 실제 청구액이 아니다.

## 수집 실패/누락

- 이 조사에는 대표 traceId가 없다 — 탐색이 트레이스를 찾지 못했거나 트레이스가 생성되지 않는 장애다. 트레이스 부재 자체를 근거로 쓸 것.
- Metric 'sum(rate(http_server_requests_seconds_count{application="content-service", status="401"}[1m]))' returned no series in this window; skipped.

---

## 1. 원인 후보 랭킹 (최대 3개)

1. **auth-service에서 목록 조회 요청이 처리되지 않은 예외로 500 종료** (`GlobalExceptionHandler.handleAllException`)
2. **컬렉션 fetch join + 페이징 조합(HHH90003004)으로 인한 인메모리 페이징** — 후보 1의 선행 원인일 가능성
3. **판단 불가 (데이터 부족)** — 팔로우 목록 API의 소유 서비스·호출 경로 자체가 관측되지 않음

---

## 2. 후보별 근거

### 후보 1 — auth-service 미처리 예외 (500)

**근거**
- 08:53:20Z(로그 표기 `2026-07-29 17:53:20`, KST) `auth-service-855c75679d-pr892` pod에서 2건 연속 발생:
  - `WARN [traceId=6a69bf80068eadb430b349f4a5a18a81,spanId=53153fe31738ac10,userId=1] c.e.t.a.c.e.GlobalExceptionHandler - handleAllException` (`http-nio-8081-exec-3`)
  - `WARN [traceId=6a69bf80a5d164255cf31a17d14bce8a,spanId=45fd31525a93c32f,userId=1] c.e.t.a.c.e.GlobalExceptionHandler - handleAllException` (`http-nio-8081-exec-5`)
- 두 건은 **서로 다른 traceId·다른 톰캣 스레드**이며 나노초 타임스탬프 기준 `1785315200477320690` → `1785315200674791136`, **197ms 간격**. 동시에 들어온 별개 요청 2개가 같은 방식으로 실패했다는 뜻.
- `handleAllException`은 통상 catch-all 핸들러 → 5xx 응답. "목록이 안 열린다"는 증상과 정합.
- 같은 pod에서 **직전 08:53:09Z**, **직후 08:54:21Z**에 목록성 조회 경고(HHH90003004, userId=1)가 찍힘 → 해당 시간대에 동일 사용자의 목록 조회가 반복 시도되고 있었음.

**확신도: 중간**

**반증 데이터**
- 로그에 **예외 클래스·스택트레이스·요청 URI·HTTP 상태코드가 전혀 없음.** content-service에는 `RequestLoggingFilter - [HTTP] POST /api/feeds/145/comments 500 - 114ms` 같은 접근 로그가 있지만, **auth-service에는 대응하는 접근 로그가 수집되지 않음** → 이 예외가 팔로우 목록 API에서 난 것인지 확인 불가.
- 트레이스 0건이라 `6a69bf80...` 두 traceId의 span을 추적할 수 없음.
- `hikaricp_connections_active` = 0, `hikaricp_connections_pending` = 0 (auth-service 전 구간 57개 샘플 모두), `up{job="auth-service"}` = 1 전 구간, GC pause 최대 `0.000125` s/s → **리소스 고갈·인스턴스 다운은 배제됨.** 부하성 장애가 아니라 특정 요청 로직 오류로 보임.
- 발생 건수 **2건뿐**이고 모두 `userId=1` → 전면 장애의 증거는 없음. 단, 로그 쿼리가 ERROR/WARN만 8줄 반환했으므로 미검출 가능성 존재.

---

### 후보 2 — 컬렉션 fetch + 페이징의 인메모리 처리

**근거**
- auth-service, 08:53:09Z / 08:54:21Z 2회:
  `HHH90003004: firstResult/maxResults specified with collection fetch; applying in memory`
  (traceId=`6a69bf75104087178f847ca2bc07c3ea`, `6a69bfbd9a6193735d02de23abcfe8f7`, 둘 다 `userId=1`)
- 이 경고는 페이징이 DB `LIMIT`이 아니라 **전체 결과셋을 힙에 적재한 뒤 애플리케이션 메모리에서 잘린다**는 의미. 팔로우/팔로워 목록처럼 1:N 컬렉션을 fetch join + `Pageable`로 조회할 때 전형적으로 발생하며, 대상 계정의 연결 수가 커지면 지연·OOM으로 이어짐.
- auth-service의 `jvm_gc_pause_seconds` (G1 Young)는 **전체 조회창에서 유일하게** `1785315210`(08:53:30)부터 `1785315435`(08:57:15)까지만 `0.000125`이고 나머지는 0 → rate[5m] 특성상 **실제 GC 이벤트는 08:52:30 전후 1회**. 즉 사건 시각 근처가 auth-service의 유일한 유의미한 할당 구간.

**확신도: 낮음**

**반증 데이터**
- GC 누적 pause 약 **37.5ms(0.000125 × 300s)**, major GC 0건 → 사용자 체감 지연을 만들 수준이 전혀 아님. heap 사용량 지표는 미수집.
- `hikaricp_connections_active{application="auth-service"}` 가 전 구간 0 → 대용량 fetch로 커넥션을 오래 점유한 흔적 없음. (단 스크레이프 간격 15초라 짧은 스파이크는 놓칠 수 있음)
- 이 경고는 예외 **이전(08:53:09)과 이후(08:54:21) 모두** 찍혔는데 예외는 08:53:20의 2건뿐 → 경고가 난 요청이 반드시 실패한 것은 아님. 즉 이 경고는 상시 성능 경고일 뿐 이번 실패의 직접 원인이라는 증거는 없음.

---

### 후보 3 — 데이터 부족 (판단 불가)

**근거**
- **Tempo 트레이스 0건.** 반면 Loki 로그에는 `traceId=...,spanId=...`가 정상 주입되어 있음(예: `spanId=53153fe31738ac10`) → 애플리케이션 계측은 살아 있으나 Tempo에 저장된 트레이스가 없다. 샘플링 0 / OTLP export 실패 / 보존 정책 중 하나. 결과적으로 **서비스 간 호출 경로, 실패 span, 지연 분포를 하나도 확인할 수 없음.**
- `http_server_requests_seconds_count{application="content-service", status="401"}` 이 **no series** → 요청 수·에러율·p99 지표가 아예 수집되지 않는 것으로 보임. **영향 범위(몇 %의 사용자가 실패했는지)를 정량화할 수단이 없다.**
- 게이트웨이/프론트엔드 로그 없음, auth-service HTTP 접근 로그 없음 → **팔로우 목록 엔드포인트가 어느 서비스 소유인지조차 데이터로 확정 불가.** (후보 1·2가 auth-service를 지목하는 것은 "목록성 쿼리 경고 + 예외가 같은 pod·같은 시각에 있다"는 정황일 뿐)
- 조회 시간창이 **08:48–09:02, 14분**뿐인데 제보는 "최근 1시간" → **46분이 미조회 상태.**

**확신도: 해당 없음 (데이터 부족)**

**반증 데이터: 없음**

---

### 명시적으로 배제한 관측값

- **content-service의 500 에러는 무관.** `Data truncation: Data too long for column 'content' at row 1` (SQL Error 1406, SQLState 22001) → `[HTTP] POST /api/feeds/145/comments 500 - 114ms` (08:51:47Z). 댓글 **작성(POST)** 요청이고 원인이 입력 길이 초과로 명확하다. 팔로우 목록 **조회**와 연결할 근거 없음.
- **인프라 전 구간 정상.** `up` = 1 (auth/chat/content 2 pod/kafka/mongodb/redis/node-exporter/kubelet/cadvisor 전부, 57개 샘플 모두), `kafka_brokers` = 1, `mongodb_up` = 1.
- **Kafka 정상.** `kafka_consumergroup_lag` 전 컨슈머그룹·전 토픽 0. `chat.messages` 파티션 2·6·9·10과 `user.notifications.dlq` 파티션 1의 `-1`은 해당 그룹에 커밋 오프셋이 없다는 표시이지 랙 누적이 아니다. → `content -> Kafka -> chat` 알림 경로는 이번 증상과 무관.
- `websocket_active_users` = 0 전 구간, 로그의 사용자가 전부 `userId=1` → 이 시간대 트래픽이 극히 적었음. **단일 사용자 제보일 가능성**을 배제할 수 없다.

---

## 3. 권장 다음 조치

**즉시 (근본 원인 확정용)**
1. `traceId=6a69bf80068eadb430b349f4a5a18a81` 과 `6a69bf80a5d164255cf31a17d14bce8a` 로 **레벨 필터 없이** Loki 재조회:
   `{service_name="auth-service"} |= "6a69bf80068eadb430b349f4a5a18a81"`
   → `handleAllException`이 삼킨 예외 클래스·스택트레이스·요청 URI 확보. 현재 이 한 줄만으로는 근본 원인 추적 불가.
2. **팔로우 목록 엔드포인트의 소유 서비스와 경로를 코드/게이트웨이 라우팅에서 확정.** auth-service가 맞는지가 후보 1·2 전체의 전제다.
3. **조회 시간창을 08:02–09:02Z 전체로 재조회.** 현재 14분만 봤고 46분이 비어 있다.
4. **영향 범위 확인:** `userId=1` 외 다른 사용자 제보가 있는지, 팔로우 수가 많은 특정 계정에서만 재현되는지 수동 테스트.

**관측성 공백 복구 (이게 막힌 상태로는 다음 장애도 동일하게 못 본다)**
5. auth-service의 트레이스 미생성 원인 확인: `management.tracing.sampling.probability` 값, OTLP exporter endpoint 설정, Tempo distributor 수신 여부.
6. `http_server_requests_seconds_count` 스크레이프 복구: actuator prometheus 노출 여부와 ServiceMonitor/scrape config 확인. 5xx 비율 없이는 영향 범위를 영원히 추정만 하게 된다.
7. auth-service에 HTTP 접근 로그 추가 (content-service의 `RequestLoggingFilter`와 동등 — 메서드·URI·상태코드·latency).
8. `GlobalExceptionHandler`가 WARN 시 스택트레이스를 함께 남기도록 수정.

**후속 (재발 방지)**
9. auth-service에서 `Pageable` + fetch join 조합 리포지토리 메서드를 식별해 HHH90003004 대상 쿼리 특정. 팔로우/팔로워 목록이면 **2단계 조회(ID만 페이징 → `IN` 절로 컬렉션 fetch)** 또는 `@BatchSize`로 전환.
10. `chat.messages` 파티션 2·6·9·10, `user.notifications.dlq` 파티션 1의 커밋 오프셋 부재 확인 — 이번 건과는 무관하나 컨슈머 할당 위생 점검 필요.

---

## 관측 증거 (Evidence)

> 이 조사가 실제로 본 값이다. 원본 응답 전체는 `reports/raw/scan-*.json`에 있다.

### 로그 원문 (8 / 전체 8줄)

```
2026-07-29T08:51:47.533044970Z  [content-service]  2026-07-29 17:51:47.532 [http-nio-8082-exec-4]  WARN [traceId=6a69bf23e45c1e51c3475f5e5f3a1b04,spanId=502823a48367b328,userId=1] o.h.e.jdbc.spi.SqlExceptionHelper - SQL Error: 1406, SQLState: 22001
2026-07-29T08:51:47.533241986Z  [content-service]  2026-07-29 17:51:47.533 [http-nio-8082-exec-4] ERROR [traceId=6a69bf23e45c1e51c3475f5e5f3a1b04,spanId=502823a48367b328,userId=1] o.h.e.jdbc.spi.SqlExceptionHelper - Data truncation: Data too long for column 'content' at row 1
2026-07-29T08:51:47.574871281Z  [content-service]  2026-07-29 17:51:47.560 [http-nio-8082-exec-4]  WARN [traceId=6a69bf23e45c1e51c3475f5e5f3a1b04,spanId=502823a48367b328,userId=1] c.e.t.a.c.e.GlobalExceptionHandler - handleAllException
2026-07-29T08:51:47.577916733Z  [content-service]  2026-07-29 17:51:47.577 [http-nio-8082-exec-4] ERROR [traceId=6a69bf23e45c1e51c3475f5e5f3a1b04,spanId=c3475f5e5f3a1b04,userId=NONE] c.e.t.a.c.f.RequestLoggingFilter - [HTTP] POST /api/feeds/145/comments 500 - 114ms
2026-07-29T08:53:09.504294986Z  [auth-service]  [2m2026-07-29 17:53:09[0;39m [2m[http-nio-8081-exec-10][0;39m [33m WARN [traceId=6a69bf75104087178f847ca2bc07c3ea,spanId=f766a498be4bc53d,userId=1][0;39m [36morg.hibernate.orm.query[0;39m [2m-[0;39m HHH90003004: firstResult/maxResults specified with collection fetch; applying in memory
2026-07-29T08:53:20.477320690Z  [auth-service]  [2m2026-07-29 17:53:20[0;39m [2m[http-nio-8081-exec-3][0;39m [33m WARN [traceId=6a69bf80068eadb430b349f4a5a18a81,spanId=53153fe31738ac10,userId=1][0;39m [36mc.e.t.a.c.e.GlobalExceptionHandler[0;39m [2m-[0;39m handleAllException
2026-07-29T08:53:20.674791136Z  [auth-service]  [2m2026-07-29 17:53:20[0;39m [2m[http-nio-8081-exec-5][0;39m [33m WARN [traceId=6a69bf80a5d164255cf31a17d14bce8a,spanId=45fd31525a93c32f,userId=1][0;39m [36mc.e.t.a.c.e.GlobalExceptionHandler[0;39m [2m-[0;39m handleAllException
2026-07-29T08:54:21.080088851Z  [auth-service]  [2m2026-07-29 17:54:21[0;39m [2m[http-nio-8081-exec-7][0;39m [33m WARN [traceId=6a69bfbd9a6193735d02de23abcfe8f7,spanId=fc00f8faed47b0da,userId=1][0;39m [36morg.hibernate.orm.query[0;39m [2m-[0;39m HHH90003004: firstResult/maxResults specified with collection fetch; applying in memory
```

### 메트릭 시계열

| 쿼리 | series | 점 | min | max | last | 값이 0이던 구간 |
|---|---|---:|---:|---:|---:|---|
| `hikaricp_connections_active` | `{__name__=hikaricp_connections_active, application=auth-service, cluster=yogurtte-k3s-prod, container=auth-service, instance=10.42.1.38:8090, job=auth-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=auth-service-855c75679d-pr892, pool=HikariPool-1, service=auth-service}` | 57 | 0 | 0 | 0 | **2026-07-29T08:48:00Z ~ 2026-07-29T09:02:00Z** |
| `hikaricp_connections_active` | `{__name__=hikaricp_connections_active, application=chat-service, cluster=yogurtte-k3s-prod, container=chat-service, instance=10.42.1.39:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-857c54dd97-w7bf7, pool=HikariPool-1}` | 57 | 0 | 0 | 0 | **2026-07-29T08:48:00Z ~ 2026-07-29T09:02:00Z** |
| `hikaricp_connections_active` | `{__name__=hikaricp_connections_active, application=content-service, cluster=yogurtte-k3s-prod, container=content-service, instance=10.42.1.35:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-6c5fff897-qnxk6, pool=HikariPool-1}` | 57 | 0 | 1 | 1 | **2026-07-29T08:48:00Z ~ 2026-07-29T08:50:00Z, 2026-07-29T08:51:15Z ~ 2026-07-29T09:00:00Z** |
| `hikaricp_connections_active` | `{__name__=hikaricp_connections_active, application=content-service, cluster=yogurtte-k3s-prod, container=content-service, instance=10.42.3.41:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-6c5fff897-scw7k, pool=HikariPool-1}` | 57 | 0 | 0 | 0 | **2026-07-29T08:48:00Z ~ 2026-07-29T09:02:00Z** |
| `hikaricp_connections_pending` | `{__name__=hikaricp_connections_pending, application=auth-service, cluster=yogurtte-k3s-prod, container=auth-service, instance=10.42.1.38:8090, job=auth-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=auth-service-855c75679d-pr892, pool=HikariPool-1, service=auth-service}` | 57 | 0 | 0 | 0 | **2026-07-29T08:48:00Z ~ 2026-07-29T09:02:00Z** |
| `hikaricp_connections_pending` | `{__name__=hikaricp_connections_pending, application=chat-service, cluster=yogurtte-k3s-prod, container=chat-service, instance=10.42.1.39:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-857c54dd97-w7bf7, pool=HikariPool-1}` | 57 | 0 | 0 | 0 | **2026-07-29T08:48:00Z ~ 2026-07-29T09:02:00Z** |
| `hikaricp_connections_pending` | `{__name__=hikaricp_connections_pending, application=content-service, cluster=yogurtte-k3s-prod, container=content-service, instance=10.42.1.35:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-6c5fff897-qnxk6, pool=HikariPool-1}` | 57 | 0 | 0 | 0 | **2026-07-29T08:48:00Z ~ 2026-07-29T09:02:00Z** |
| `hikaricp_connections_pending` | `{__name__=hikaricp_connections_pending, application=content-service, cluster=yogurtte-k3s-prod, container=content-service, instance=10.42.3.41:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-6c5fff897-scw7k, pool=HikariPool-1}` | 57 | 0 | 0 | 0 | **2026-07-29T08:48:00Z ~ 2026-07-29T09:02:00Z** |
| `rate(jvm_gc_pause_seconds_sum[5m])` | `{action=end of major GC, application=chat-service, cause=Allocation Failure, cluster=yogurtte-k3s-prod, container=chat-service, gc=MarkSweepCompact, instance=10.42.1.39:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-857c54dd97-w7bf7}` | 57 | 0 | 0 | 0 | **2026-07-29T08:48:00Z ~ 2026-07-29T09:02:00Z** |
| `rate(jvm_gc_pause_seconds_sum[5m])` | `{action=end of minor GC, application=auth-service, cause=G1 Evacuation Pause, cluster=yogurtte-k3s-prod, container=auth-service, gc=G1 Young Generation, instance=10.42.1.38:8090, job=auth-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=auth-service-855c75679d-pr892, service=auth-service}` | 57 | 0 | 0.000 | 0 | **2026-07-29T08:48:00Z ~ 2026-07-29T08:53:15Z, 2026-07-29T08:57:30Z ~ 2026-07-29T09:02:00Z** |
| `rate(jvm_gc_pause_seconds_sum[5m])` | `{action=end of minor GC, application=chat-service, cause=Allocation Failure, cluster=yogurtte-k3s-prod, container=chat-service, gc=Copy, instance=10.42.1.39:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-857c54dd97-w7bf7}` | 57 | 0.000 | 0.000 | 0.000 | — |
| `rate(jvm_gc_pause_seconds_sum[5m])` | `{action=end of minor GC, application=content-service, cause=Allocation Failure, cluster=yogurtte-k3s-prod, container=content-service, gc=Copy, instance=10.42.1.35:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-6c5fff897-qnxk6}` | 57 | 0 | 0.000 | 0 | **2026-07-29T08:50:15Z ~ 2026-07-29T08:54:00Z, 2026-07-29T08:58:15Z ~ 2026-07-29T09:02:00Z** |
| `rate(jvm_gc_pause_seconds_sum[5m])` | `{action=end of minor GC, application=content-service, cause=Allocation Failure, cluster=yogurtte-k3s-prod, container=content-service, gc=Copy, instance=10.42.3.41:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-6c5fff897-scw7k}` | 57 | 0 | 0.000 | 0.000 | **2026-07-29T08:48:00Z ~ 2026-07-29T08:49:45Z, 2026-07-29T08:54:00Z ~ 2026-07-29T08:59:45Z** |
| `up` | `{__name__=up, app=node-exporter, cluster=yogurtte-k3s-prod, component=metrics, container=node-exporter, instance=ip-172-31-40-241, job=integrations/node_exporter, k8s_cluster_name=yogurtte-k3s-prod, namespace=monitoring, pod=grafana-k8s-monitoring-node-exporter-cph5l, source=kubernetes, workload=DaemonSet/grafana-k8s-monitoring-node-exporter}` | 57 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, app=node-exporter, cluster=yogurtte-k3s-prod, component=metrics, container=node-exporter, instance=ip-172-31-45-39, job=integrations/node_exporter, k8s_cluster_name=yogurtte-k3s-prod, namespace=monitoring, pod=grafana-k8s-monitoring-node-exporter-9kvqt, source=kubernetes, workload=DaemonSet/grafana-k8s-monitoring-node-exporter}` | 57 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, cluster=yogurtte-k3s-prod, container=auth-service, instance=10.42.1.38:8090, job=auth-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=auth-service-855c75679d-pr892}` | 57 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, cluster=yogurtte-k3s-prod, container=chat-service, instance=10.42.1.39:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-857c54dd97-w7bf7}` | 57 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, cluster=yogurtte-k3s-prod, container=content-service, instance=10.42.1.35:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-6c5fff897-qnxk6}` | 57 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, cluster=yogurtte-k3s-prod, container=content-service, instance=10.42.3.41:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-6c5fff897-scw7k}` | 57 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, cluster=yogurtte-k3s-prod, instance=10.42.1.248:8080, job=integrations/kubernetes/kube-state-metrics, k8s_cluster_name=yogurtte-k3s-prod, source=kubernetes}` | 57 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, cluster=yogurtte-k3s-prod, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod}` | 57 | 1 | 1 | 1 | — |
| `mongodb_up` | `{__name__=mongodb_up, cluster=yogurtte-k3s-prod, instance=infra-server, job=mongodb, k8s_cluster_name=yogurtte-k3s-prod}` | 57 | 1 | 1 | 1 | — |
| `kafka_brokers` | `{__name__=kafka_brokers, cluster=yogurtte-k3s-prod, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod}` | 57 | 1 | 1 | 1 | — |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=chat-service-fcm-tokens, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=0, topic=user.fcm-tokens}` | 57 | 0 | 0 | 0 | **2026-07-29T08:48:00Z ~ 2026-07-29T09:02:00Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=chat-service-fcm-tokens, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=1, topic=user.fcm-tokens}` | 57 | 0 | 0 | 0 | **2026-07-29T08:48:00Z ~ 2026-07-29T09:02:00Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=chat-service-fcm-tokens, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=2, topic=user.fcm-tokens}` | 57 | 0 | 0 | 0 | **2026-07-29T08:48:00Z ~ 2026-07-29T09:02:00Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=chat-service-notification-settings, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=0, topic=user.notification-settings}` | 57 | 0 | 0 | 0 | **2026-07-29T08:48:00Z ~ 2026-07-29T09:02:00Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=chat-service-notification-settings, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=1, topic=user.notification-settings}` | 57 | 0 | 0 | 0 | **2026-07-29T08:48:00Z ~ 2026-07-29T09:02:00Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=chat-service-notification-settings, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=2, topic=user.notification-settings}` | 57 | 0 | 0 | 0 | **2026-07-29T08:48:00Z ~ 2026-07-29T09:02:00Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=db-writer, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=0, topic=chat.messages}` | 57 | 0 | 0 | 0 | **2026-07-29T08:48:00Z ~ 2026-07-29T09:02:00Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=db-writer, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=1, topic=chat.messages}` | 57 | 0 | 0 | 0 | **2026-07-29T08:48:00Z ~ 2026-07-29T09:02:00Z** |
| `websocket_active_users` | `{__name__=websocket_active_users, application=chat-service, cluster=yogurtte-k3s-prod, container=chat-service, instance=10.42.1.39:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-857c54dd97-w7bf7}` | 57 | 0 | 0 | 0 | **2026-07-29T08:48:00Z ~ 2026-07-29T09:02:00Z** |

값이 0이던 구간은 굵게 표시했다 — 프로세스가 사라져 시계열이 꺾인 것이 유일한 신호인 장애가 있다.

