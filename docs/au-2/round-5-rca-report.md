# RCA Report — `scan-1785904800`

| 항목 | 값 |
|---|---|
| 모드 | rca |
| 질문 | 최근 1시간 안에 로그인이 안 된다는 문의가 몰렸다. 원인을 조사해줘 |
| 시각 | 2026-08-05T05:04:29.209684200Z |
| provider | claude-cli |
| model | `claude-opus-5` · turns 1 |
| prompt | `.\prompts\system-prompt.md` |
| tokens | in 87526 (cacheRead 23,449 · cacheCreate 64,075) / out 11100 · cost $0.9300 |
| elapsed | total 185980ms (tempo 929 · loki 405 · mimir 1049 · assemble 64 · llm 173100) |

## 탐색 (Triage)

| 항목 | 값 |
|---|---|
| 시간창 해석 | 상대 표현 '최근 1시간' |
| 시간창 확신도 | EXACT |
| 스윕 창 | 2026-08-05T04:03:56.979083800Z ~ 2026-08-05T05:03:56.979083800Z |
| 좁힌 창 | 2026-08-05T04:40:00Z ~ 2026-08-05T05:00:00Z |
| 대상 | auth-service |
| traceId | 6a72c1125884c1f3995f139ebaafc05e |
| 트레이스 후보 | 3건 |
| 장애 후보 | 5건 · 선택 INC-1, INC-3 |
| 계획 파싱 | 성공 |
| 스윕 컨텍스트 | **후보만 — 원본 제외 (B)** |
| prompt | `.\prompts\triage-prompt.md` |
| tokens | in 32463 / out 1512 · cost $0.1668 |
| chars | 컨텍스트 2,371 + 프롬프트 1,399 = **3,770** |
| elapsed | survey 1978ms · llm 30142ms |

**선정 이유**: 로그인 문의라는 증상과 리소스(auth-service)·엔드포인트(/login)·시각(04:45~04:55)이 전부 일치하는 유일한 후보 쌍이기 때문이다.

**근거**

- auth-service ERROR/WARN 3건(04:45~04:50) + 2건(04:50~04:55) — 질문의 '최근 1시간' 창 안, 로그인 담당 서비스에서만 발생
- INC-3: auth-service http post /login 4,586ms (traceId 6a72c1125884c1f3995f139ebaafc05e, 04:50:26.84) — 로그인 엔드포인트 자체가 지연 채널에 직접 걸림
- INC-3의 시각(04:50:26)이 INC-1의 ERROR/WARN 구간(04:45~04:55) 한가운데 — 동일 장애의 로그 지문과 트레이스 지문
- Tempo '{ status = error }' 0건 — 에러 없는 지연형 장애. 로그인이 실패 응답이 아니라 4.5초 지연 후 클라이언트 타임아웃으로 체감되는 형태와 일치
- up / mongodb_up / kafka_brokers / consumergroup_lag / websocket_active_users 모두 이상 신호 0건 — 인프라·DB·Kafka 전면 장애는 배제, auth-service 내부(예: 인증 처리 경로 블로킹)로 범위가 좁혀짐

**스윕이 찾은 트레이스** (고른 것은 6a72c1125884c1f3995f139ebaafc05e)

| traceId | 채널 | root service | root span | ms |
|---|---|---|---|---:|
| `6a72c3507df6aa550e82d18871008356` | slow | content-service | task battle-hot-score-scheduler.time-weight-update | 4023 |
| `6a72c240515fe3ce1bdd0c0fc9830077` | slow | content-service | connection | 3462 |
| `6a72c1125884c1f3995f139ebaafc05e` ←선택 | slow | auth-service | http post /login | 4586 |

**장애 후보** (코드가 신호를 묶은 것 · 창은 여기서 계산됨)

## INC-1  auth-service  |  ERROR/WARN
- 구간: 2026-08-05T04:45:00Z ~ 2026-08-05T04:55:00Z  (LOKI · 집계 해상도만큼 흐림)
- ERROR/WARN 3건 (2026-08-05T04:45:00Z ~ 2026-08-05T04:50:00Z)
- ERROR/WARN 2건 (2026-08-05T04:50:00Z ~ 2026-08-05T04:55:00Z)
- 같은 시각의 다른 후보: INC-2, INC-3  (인과 여부는 판단하지 않았다)

## INC-2  content-service  |  ERROR/WARN
- 구간: 2026-08-05T04:50:00Z ~ 2026-08-05T05:00:00Z  (LOKI · 집계 해상도만큼 흐림)
- ERROR/WARN 3건 (2026-08-05T04:50:00Z ~ 2026-08-05T04:55:00Z)
- ERROR/WARN 42건 (2026-08-05T04:55:00Z ~ 2026-08-05T05:00:00Z)
- 같은 시각의 다른 후보: INC-1, INC-3, INC-4  (인과 여부는 판단하지 않았다)

## INC-3  auth-service  |  http post /login
- 구간: 2026-08-05T04:50:26.839978Z ~ 2026-08-05T04:50:31.425978Z  (TEMPO · 시각 정확)
- auth-service http post /login 4,586ms (slow 채널)
- traceId: 6a72c1125884c1f3995f139ebaafc05e
- 같은 시각의 다른 후보: INC-1, INC-2  (인과 여부는 판단하지 않았다)

## INC-4  content-service  |  connection
- 구간: 2026-08-05T04:55:28.641523Z ~ 2026-08-05T04:55:32.103523Z  (TEMPO · 시각 정확)
- content-service connection 3,462ms (slow 채널)
- traceId: 6a72c240515fe3ce1bdd0c0fc9830077
- 같은 시각의 다른 후보: INC-2  (인과 여부는 판단하지 않았다)

## INC-5  content-service  |  task battle-hot-score-scheduler.time-weight-update
- 구간: 2026-08-05T05:00:00.132015Z ~ 2026-08-05T05:00:04.155015Z  (TEMPO · 시각 정확)
- content-service task battle-hot-score-scheduler.time-weight-update 4,023ms (slow 채널)
- traceId: 6a72c3507df6aa550e82d18871008356

**기각한 후보**

- INC-2 — content-service ERROR/WARN이고 폭증 구간(04:55~05:00, 42건)이 /login 지연 시각(04:50:26)보다 뒤라 로그인 증상의 원인으로 보기 어렵다 — 다만 auth 지연의 하류 파급일 가능성은 남으므로 auth 조사에서 실마리가 없으면 다음 후보로 올린다.
- INC-4 — content-service의 connection 지연(3,462ms)으로 인증 경로가 아니고, INC-2와 같은 시각대의 동일 장애 지문으로 보인다.
- INC-5 — content-service의 배치 스케줄러(battle-hot-score-scheduler) 지연으로 사용자 로그인 요청 경로와 무관하며, 05:00 정각 실행이라 주기 작업의 통상 소요일 가능성이 높다.

**보정 기록**

- 창을 후보 [INC-1, INC-3] 의 신호 시각에서 계산했다 (2026-08-05T04:40:00Z ~ 2026-08-05T05:00:00Z)

**스윕 무신호/실패**

- Tempo 에러 검색 '{ status = error }'이 이 창에서 0건이다. 트레이스가 생성되지 않는 장애(컨슈머 전멸·파드 부재)이거나 에러가 아닌 형태의 장애(200 성공 + 지연)일 수 있으니 이 사실 자체를 근거로 쓸 것.

## 수집 범위 (Coverage)

- **window**: 2026-08-05T04:40:00Z ~ 2026-08-05T05:00:00Z (1200s)
- **trace**: 112,551B / 144 spans (상위 span만)
- **창 안 후보 트레이스**: 10건 / 112,551B
- **logs**: errwarn=9,875B · traceId=4,690B
- **metrics**: 8 수집 / 142,127B, 누락 [sum(rate(http_server_requests_seconds_count{application="content-service", status="401"}[1m]))]
- **context**: 103,946 chars (+ 시스템 프롬프트 805 chars)

### 토큰 축 (개선 지표)

| 단계 | 총 in | chars | 컨텍스트 토큰 |
|---|---:|---:|---:|
| 탐색 | 32,463 | 3,770 | 3,037 |
| 분석 | 87,526 | 104,751 | 58,100 |
| **합계** | **119,989** | | **61,137** |

- **overheadTokens 29,426 tok — 이 회차에 실측**(1자 프롬프트 프로브, 같은 명령·모델·샌드박스).
  호출마다 한 번씩 얹히므로 단계별로 뺀다. **다른 날 상수를 쓰지 않았다 = `█ 실측`.**
- contextTokens (count_tokens API): 측정 안 됨 — 구독 CLI 경로엔 API 키가 없다
- 비용은 **API 환산 추정치**다. 구독 계정으로 호출하므로 실제 청구액이 아니다.

## 수집 실패/누락

- Metric 'sum(rate(http_server_requests_seconds_count{application="content-service", status="401"}[1m]))' returned no series in this window; skipped.

---

조사 결과입니다. 툴 조회 없이 제시된 관측 데이터만으로 판단했습니다.

## 1. 원인 후보 랭킹 (최대 3개)

1. **auth-service 파드 단일 인스턴스 재생성으로 인한 인증 처리 공백 (약 04:48:30 ~ 04:50:26 UTC, 약 2분)**
2. **재기동 직후 auth-service 콜드스타트 지연 — 로그인 4574ms, 후속 요청 2326ms (서버는 200 성공, 클라이언트 측 체감 실패 가능)**
3. **content-service 롤링 배포 + 기동 시 Hibernate DDL 실행 (04:55:28~) — 로그인 경로와의 연결 근거 없음**

---

## 2. 후보별 근거

### 후보 1 — auth-service 파드 재생성으로 인한 인증 공백

**근거**
- `up{job="auth-service", pod="auth-service-5999bb9f5c-hmgp9", instance="10.42.1.46:8090"}` = 04:40:00Z ~ **04:48:30Z**까지만 존재(전 구간 1).
- `up{job="auth-service", pod="auth-service-5999bb9f5c-jv2jn", instance="10.42.1.48:8090"}` = **04:51:30Z** ~ 05:00:00Z (전 구간 1).
- 두 시리즈가 **겹치는 구간이 없다** → 관측창 전체에서 auth-service 인스턴스는 항상 1개뿐이었고, 04:48:30 ~ 04:51:30 사이에는 스크레이프된 인스턴스가 **0개**다. `hikaricp_*`, `jvm_gc_pause_seconds_sum` 시리즈도 동일한 경계(hmgp9는 04:48:30/04:51:30에 끊기고 jv2jn는 04:51:30/04:52:30부터 시작)에서 갈린다.
- 그 공백 구간에 새 파드의 **JVM 콜드 부팅 로그**가 찍힌다(모두 `[main]` 스레드, `traceId=NONE`):
  `2026-08-05 13:48:47 [main] WARN ... PostProcessorRegistrationDelegate$BeanPostProcessorChecker` → `13:49:10 hibernate.orm.deprecation HHH90000025` → `13:49:48 spring.jpa.open-in-view is enabled by default` → `13:49:53 o.s.s.web.DefaultSecurityFilterChain - Will secure any request with [...]`. (로그 타임스탬프는 KST, UTC로는 04:48:47 ~ 04:49:53.)
- 즉 구 파드는 04:48:30 직후 사라졌고, 신 파드는 04:49:53에야 시큐리티 필터체인 구성을 마쳤으며, **첫 요청 span은 04:50:26.84** (`http post /login`, startTimeUnixNano=1785905426839978000)이다.
- 파드 해시가 **동일**하다(`5999bb9f5c`-hmgp9 → `5999bb9f5c`-jv2jn). 같은 ReplicaSet 안에서의 파드 재생성이므로 이미지·설정 변경 배포가 아니라 **비계획 재시작/재스케줄**(축출·OOM·노드 조치 등)로 보인다. 단, 재생성 사유를 직접 증명하는 데이터(kube_pod 이벤트, last_terminated_reason)는 수집되지 않았다.

**확신도: 중간**
("auth 인스턴스 부재 구간이 존재했다"는 근거는 강하나, 그 구간의 실제 로그인 실패를 보여주는 4xx/5xx 로그·메트릭이 하나도 수집되지 않아 문의와의 인과는 정황 추론이다.)

**대기·지연 구간 판정**
- 공백 구간(04:48:30~04:50:26)에 도달한 로그인 요청의 대기 시간·상한(게이트웨이/클라이언트 타임아웃) 값이 **둘 다 미수집**이고, 해당 구간 트레이스·에러 로그가 0건이다 → **만료 여부 판정 불가, 최종 상태 판정 불가**. (요청 자체가 파드에 닿지 못해 트레이스가 생성되지 않았을 가능성이 가장 자연스럽지만, 이를 확증하는 인그레스/게이트웨이 로그가 없다.)

**반증 데이터**
- 로그인 실패를 직접 보여주는 관측값이 **전무**하다. 수집된 유일한 로그인 트레이스(`6a72c1125884c1f3995f139ebaafc05e`)는 `"status":"200"`, `"outcome":"SUCCESS"`, `"exception":"none"`이다.
- `up` 시리즈의 결측이 "파드 부재"인지 "스크레이프 실패"인지 구분해줄 `kube_pod_status_phase` / `kube_pod_container_status_restarts_total`이 없다.
- 노드는 전 구간 정상이다: `up{node=ip-172-31-45-39, job=integrations/kubernetes/kubelet}` 등 모든 노드·인프라 타깃이 04:40~05:00 전 구간 1이며 `mongodb_up`=1, `kafka_brokers`=1이다.

---

### 후보 2 — 재기동 직후 콜드스타트 지연

**근거**
- `c.e.t.a.c.f.RequestLoggingFilter - [HTTP-SLOW] POST /api/login 200 - 4574ms` (04:50:31Z, traceId=6a72c1125884c1f3995f139ebaafc05e).
- 해당 트레이스: `http post /login` durNs=**4586409000 (4.586s)**, 내부 `secured request` durNs=**4501088000 (4.501s)** — 지연의 98%가 시큐리티 필터체인 통과 후 애플리케이션 처리 구간에 있다. `security filterchain before`는 65.8ms, `after`는 2.8ms로 무시할 수준.
- 이 트레이스는 신규 파드 기동(04:49:53 필터체인 구성 완료) **33초 뒤 첫 요청**이다.
- 51초 뒤 두 번째 느린 요청: `[HTTP-SLOW] GET /api/external/users/1/followings 200 - 2326ms` (04:51:20Z). 지연이 4574ms → 2326ms로 감소하는 형태는 클래스 로딩·JIT·커넥션 초기화 등 웜업 곡선과 일치한다.
- 로그인 처리를 지연시킬 만한 자원 경합 근거는 없다: auth-service `hikaricp_connections_active`·`hikaricp_connections_pending` 모두 **전 구간 0**, `rate(jvm_gc_pause_seconds_sum[5m])`도 auth-service 두 파드 모두 **전 구간 0**.

**확신도: 중간**

**대기·지연 구간 판정**
- 서버 측: 4586ms 소요 후 `status=200`, `outcome=SUCCESS`, `exception=none` → **서버 타임아웃 만료 없음, 최종 상태 = 성공**.
- 클라이언트/게이트웨이 측: 앱·인그레스의 응답 타임아웃 설정값이 미수집이므로 4574ms가 그 상한을 넘겼는지는 **판정 불가**. 재시도·폐기 여부를 보여줄 재전송 트레이스도 없다(로그인 트레이스는 1건뿐) → **재시도 여부 판정 불가**.
- 이 span 내부에 DB·Redis 자식 span이 전혀 없다(span 4개가 전부: server, filterchain before/after, secured request) → 지연 구간을 하위 의존성으로 더 쪼갤 근거 없음.

**반증 데이터**
- 서버 최종 상태가 성공(200)이므로, 이 후보만으로는 "로그인이 **안 된다**"는 문의를 설명하려면 클라이언트 타임아웃이라는 미관측 가정이 필요하다.
- 느린 요청은 관측창 내 2건뿐이고, 둘 다 파드 기동 직후에 몰려 있다 — 지속적 성능 저하 패턴이 아니다.

---

### 후보 3 — content-service 롤링 배포 + 기동 시 Hibernate DDL

**근거**
- 새 ReplicaSet으로의 **실제 배포**다: `content-service-6995bb7d94-h2f6n`(04:40:00~04:54:45), `content-service-6995bb7d94-nq9l2`(04:40:00~04:58:30) → `content-service-**64b7dfc78f**-kjc8w`(04:57:15~05:00:00). 해시가 바뀌었다(auth-service와 대조적).
- 신규 파드 기동 트레이스 `6a72c240515fe3ce1bdd0c0fc9830077`: 단일 `connection` span durNs=**3462950000 (3.463s)** 아래 123개 자식 span. 내용은 스키마 변경 DDL과 메타데이터 조회다 — 예: `alter table categories modify column type enum ('FEED','PRODUCT') default 'PRODUCT' not null ...` (389.8ms), `alter table tb_battle modify column item_add_permission_type enum (...)` (234.9ms), 이어서 `select * from 'content'.'categories' where 1=0` 류 40여 건(모두 `jdbc.row-count":"0"`). 이는 Hibernate `ddl-auto`가 프로덕션에서 활성 상태임을 시사한다(같은 파드 로그 계열에서 `spring.jpa.open-in-view is enabled by default` 경고도 확인됨).
- 다만 **호출 그래프에 auth-service ↔ content-service 엣지가 없다.** 관측된 엣지는 `content-service --db--> redis`, `content-service --jdbc--> mysql/content`, `chat-service --jdbc--> mysql/content` 뿐이다. 로그인 경로가 content-service를 경유한다는 근거가 데이터에 없다.
- content-service는 배포 중에도 최소 1개 인스턴스가 살아 있었다(h2f6n 04:54:45 종료 시점에 nq9l2 가동 중, nq9l2 04:58:30 종료 시점에 kjc8w 가동 중) → 서비스 전면 중단 구간 없음.

**확신도: 낮음** (로그인 문의와의 연결 근거 없음. 다만 프로덕션 자동 DDL 자체는 별개 위험으로 보고한다.)

**대기·지연 구간 판정**
- 커넥션 획득: 요청 04:55:28.641523 → `acquired` 이벤트 04:55:28.644334 = **2.811ms**. Hikari `connection-timeout` 설정값은 미수집이나, `acquired` 이벤트가 존재하므로 **만료되지 않았음이 확정, 최종 상태 = 성공**. 3.463s는 대기가 아니라 DDL 실행 동안의 **커넥션 점유 시간**이다.
- 123개 자식 span 중 error 속성이나 예외 이벤트가 있는 span은 **0개**, 결과셋도 정상 반환(`jdbc.row-count":"0"`) → DDL **최종 상태 = 성공**(폐기·재시도 없음).
- chat-service `connection` span 2건(1.728ms/1.341ms)도 각각 `acquired` 이벤트 존재 → **성공**.

**반증 데이터**
- 호출 그래프상 로그인 경로와 무관.
- 풀 포화 없음: 모든 서비스 `hikaricp_connections_pending`·`active` 전 구간 0. 단, 신규 파드 kjc8w의 메트릭은 **04:57:15부터** 시작하므로 DDL이 돌던 04:55:28~04:55:32 구간의 풀 상태는 **관측 공백**이다(이 구간에 대해서는 데이터 부족).
- 시간대도 어긋난다: content 배포(04:55~04:58)는 auth 공백(04:48:30~04:50:26)보다 **뒤**다.

---

### 데이터 부족 항목 (결론 확신도 하향 사유)
- 수집 실패: `sum(rate(http_server_requests_seconds_count{application="content-service", status="401"}[1m]))` — 시리즈 없음.
- 애초에 **auth-service의 상태코드별 요청 메트릭이 쿼리되지 않았다.** 로그인 실패의 직접 증거(401/5xx 카운트)가 전무한 것이 이번 조사의 최대 공백이다.
- 추가 수집 필요: ① `http_server_requests_seconds_count{application="auth-service"}` status별 04:40~05:00, ② `kube_pod_container_status_restarts_total` / `kube_pod_container_status_last_terminated_reason{pod=~"auth-service-.*"}` 및 `kubectl describe pod` 이벤트, ③ auth-service Deployment `replicas` 값, ④ 인그레스/게이트웨이 액세스 로그의 502/504 및 upstream timeout 설정값, ⑤ auth-service `spring.datasource.hikari.connection-timeout` 등 타임아웃 설정, ⑥ 사용자 문의 접수 시각 분포(04:48~04:51에 몰렸는지).

---

## 3. 권장 다음 조치

### 이미 발생한 피해: 복구 가능한가
- **로그인 실패 자체는 복구 가능**하다. 인증 요청은 서버 상태를 변경하지 않으므로 사용자 재시도로 회복된다. 실제로 04:50:26 이후 로그인은 200으로 성공했고, `up{pod=auth-service-...-jv2jn}`은 04:51:30~05:00:00 전 구간 1이다.
- **다만 피해 규모(실패 건수·영향 사용자)는 현재 데이터로 산정 불가.** 공백 구간의 요청 카운트·에러 로그가 없다 → 위 추가 수집 ①④가 선행돼야 한다.
- **content-service의 DDL(`alter table ... modify column enum`)은 이미 커밋된 스키마 변경**이며 자동 롤백되지 않는다. 다만 전 span에서 에러가 없고 후속 조회가 정상이므로 **현재 복구 조치는 불필요**하다. 배포를 되돌릴 경우 구 버전 코드와 신 스키마의 enum 값 불일치 가능성만 확인하면 된다.

### 재발 방지
1. **auth-service 다중화**: 관측창 내내 auth 인스턴스가 항상 1개였다. `replicas ≥ 2` + `PodDisruptionBudget(minAvailable: 1)` + anti-affinity. 단일 파드 재생성이 곧 인증 전면 중단이 되는 구조가 이번 사건의 구조적 원인이다.
2. **파드 재생성 사유 규명**: 같은 ReplicaSet 내 교체이므로 배포가 아니다. `last_terminated_reason`(OOMKilled 여부), 노드 축출 이벤트, 리소스 limits를 확인한다.
3. **기동 지연 흡수**: 신규 파드가 04:48:47 부팅 → 04:49:53 필터체인 구성 → 첫 요청 4574ms. `startupProbe`/`readinessProbe`로 웜업 완료 전 트래픽 유입을 막고, 게이트웨이 타임아웃이 콜드스타트 최댓값(현재 관측 4.6s)보다 큰지 대조한다.
4. **프로덕션 자동 DDL 차단**: `spring.jpa.hibernate.ddl-auto`를 `validate`/`none`으로 두고 스키마 변경은 마이그레이션 도구로 분리한다. 현재는 파드 기동마다 24개 `alter table`이 공유 DB에 실행된다(3.46s 커넥션 점유).
5. **알림 추가**: `absent(up{job="auth-service"}) == 1` (2분 지속), 로그인 5xx/401 비율, `[HTTP-SLOW]` 로그 빈도.

### 복구 확인
- 현재 상태는 이미 정상 신호를 보인다: `up{job="auth-service", pod=...jv2jn}` 04:51:30~05:00:00 전 구간 1, `hikaricp_connections_pending` 0, GC pause rate 0, 마지막 관측 로그인 200 SUCCESS.
- 추가로 확인할 것:
  1. `count(up{job="auth-service"} == 1)` 이 **2 이상**으로 유지되는지(다중화 적용 후).
  2. 05:00 이후 `[HTTP-SLOW] POST /api/login` 로그가 더 이상 나오지 않는지 — 04:50:31·04:51:20 두 건이 기동 직후 웜업이었다면 재발하지 않아야 한다.
  3. 로그인 성공률(200 / 전체) 시계열을 사건 이전 구간과 대조. 이 지표가 이번 조사에서 빠져 있었으므로 대시보드에 상시 노출시킨다.
  4. content-service 신규 파드(`64b7dfc78f-kjc8w`) 기동 후 `hikaricp_connections_pending`이 0을 유지하는지 — 04:55:28~04:55:32 DDL 구간은 스크레이프 이전이라 관측 공백이므로, 다음 재기동 시 이 구간을 반드시 확보한다.

---

## 관측 증거 (Evidence)

> 이 조사가 실제로 본 값이다. 원본 응답 전체는 `reports/raw/scan-1785904800-*.json`에 있다.

### 호출 그래프 (트레이스에서 추출)

```
content-service --db--> redis  1회  최대 0.5ms  [INFO]
chat-service --jdbc--> mysql/content (HikariPool-1)  2회  최대 1.7ms
    events: acquired
content-service --jdbc--> mysql/content (HikariPool-1)  126회  최대 3463.0ms
    events: acquired
```

### span (duration 상위 15 / 전체 144)

| ms | service | span | 시작 |
|---:|---|---|---|
| 4586.41 | auth-service | `http post /login` | 2026-08-05T04:50:26.839978Z |
| 4501.09 | auth-service | `secured request` | 2026-08-05T04:50:26.912906Z |
| 3462.95 | content-service | `connection` | 2026-08-05T04:55:28.641523Z |
| 389.82 | content-service | `query` | 2026-08-05T04:55:28.818557Z |
| 234.87 | content-service | `query` | 2026-08-05T04:55:29.270484Z |
| 177.09 | content-service | `query` | 2026-08-05T04:55:30.253380Z |
| 111.42 | content-service | `query` | 2026-08-05T04:55:29.995152Z |
| 99.62 | content-service | `query` | 2026-08-05T04:55:29.574384Z |
| 86.66 | content-service | `query` | 2026-08-05T04:55:29.677718Z |
| 65.80 | auth-service | `security filterchain before` | 2026-08-05T04:50:26.846668Z |
| 65.48 | content-service | `query` | 2026-08-05T04:55:29.507290Z |
| 53.58 | content-service | `query` | 2026-08-05T04:55:29.766883Z |
| 51.66 | content-service | `query` | 2026-08-05T04:55:30.615643Z |
| 49.39 | content-service | `query` | 2026-08-05T04:55:29.213281Z |
| 43.08 | content-service | `query` | 2026-08-05T04:55:30.162934Z |

### 로그 원문 (8 / 전체 8줄)

```
2026-08-05T04:48:47.192609560Z  [auth-service]  [2m2026-08-05 13:48:47[0;39m [2m[main][0;39m [33m WARN [traceId=NONE,spanId=NONE,userId=NONE][0;39m [36mo.s.c.s.PostProcessorRegistrationDelegate$BeanPostProcessorChecker[0;39m [2m-[0;39m Bean 'org.springframework.ws.config.annotation.DelegatingWsConfiguration' of type [org.springframework.ws.config.annotation.DelegatingWsConfiguration$$SpringCGLIB$$0] is not eligible for getting processed by all BeanPostProcessors (for example: not eligible for auto-proxying). The currently created BeanPostProcessor [annotationActionEndpointMapping] is declared through a non-static factory method on that class; consider declaring it as static instead.
2026-08-05T04:49:10.230757491Z  [auth-service]  [2m2026-08-05 13:49:10[0;39m [2m[main][0;39m [33m WARN [traceId=NONE,spanId=NONE,userId=NONE][0;39m [36morg.hibernate.orm.deprecation[0;39m [2m-[0;39m HHH90000025: MySQLDialect does not need to be specified explicitly using 'hibernate.dialect' (remove the property setting and it will be selected by default)
2026-08-05T04:49:48.420594088Z  [auth-service]  [2m2026-08-05 13:49:48[0;39m [2m[main][0;39m [33m WARN [traceId=NONE,spanId=NONE,userId=NONE][0;39m [36mo.s.b.a.o.j.JpaBaseConfiguration$JpaWebConfiguration[0;39m [2m-[0;39m spring.jpa.open-in-view is enabled by default. Therefore, database queries may be performed during view rendering. Explicitly configure spring.jpa.open-in-view to disable this warning
2026-08-05T04:49:53.494718104Z  [auth-service]  [2m2026-08-05 13:49:53[0;39m [2m[main][0;39m [32m INFO [traceId=NONE,spanId=NONE,userId=NONE][0;39m [36mo.s.s.web.DefaultSecurityFilterChain[0;39m [2m-[0;39m Will secure Or [Mvc [pattern='/api/external/**']] with [org.springframework.security.web.session.DisableEncodeUrlFilter@26156929, org.springframework.security.web.context.request.async.WebAsyncManagerIntegrationFilter@3291cfad, org.springframework.security.web.context.SecurityContextHolderFilter@60c88b78, org.springframework.security.web.header.HeaderWriterFilter@7382ec67, org.springframework.web.filter.CorsFilter@756974d8, org.springframework.security.web.authentication.logout.LogoutFilter@5bd9615c, com.example.toyauth.app.common.filter.ExternalAuthenticationFilter@75044df3, org.springframework.security.web.savedrequest.RequestCacheAwareFilter@7647eff0, org.springframework.security.web.servletapi.SecurityContextHolderAwareRequestFilter@7c47ae7d, org.springframework.security.web.authentication.AnonymousAuthenticationFilter@43b8d302, org.springframework.security.web.session.SessionManagementFilter@2dbc453a, org.springframework.security.web.access.ExceptionTranslationFilter@5a947fd5, org.springframework.security.web.access.intercept.AuthorizationFilter@263121b]
2026-08-05T04:49:53.798375553Z  [auth-service]  [2m2026-08-05 13:49:53[0;39m [2m[main][0;39m [32m INFO [traceId=NONE,spanId=NONE,userId=NONE][0;39m [36mo.s.s.web.DefaultSecurityFilterChain[0;39m [2m-[0;39m Will secure any request with [org.springframework.security.web.session.DisableEncodeUrlFilter@1baab1b1, org.springframework.security.web.context.request.async.WebAsyncManagerIntegrationFilter@1c833e78, org.springframework.security.web.context.SecurityContextHolderFilter@2587f18d, org.springframework.security.web.header.HeaderWriterFilter@20df25f8, org.springframework.web.filter.CorsFilter@7effdd04, org.springframework.security.web.authentication.logout.LogoutFilter@4effe36a, org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestRedirectFilter@33db58ce, org.springframework.security.oauth2.client.web.OAuth2LoginAuthenticationFilter@3a0f0552, com.example.toyauth.app.common.filter.JwtFilter@2ca6937, org.springframework.security.web.authentication.ui.DefaultLoginPageGeneratingFilter@24d642eb, org.springframework.security.web.authentication.ui.DefaultLogoutPageGeneratingFilter@604c8ed5, org.springframework.security.web.savedrequest.RequestCacheAwareFilter@4d17ce84, org.springframework.security.web.servletapi.SecurityContextHolderAwareRequestFilter@143ec23b, org.springframework.security.web.authentication.AnonymousAuthenticationFilter@2e259c54, org.springframework.security.web.session.SessionManagementFilter@7148320d, org.springframework.security.web.access.ExceptionTranslationFilter@74e3836a]
2026-08-05T04:50:31.418970341Z  [auth-service]  [2m2026-08-05 13:50:31[0;39m [2m[http-nio-8081-exec-1][0;39m [33m WARN [traceId=6a72c1125884c1f3995f139ebaafc05e,spanId=995f139ebaafc05e,userId=NONE][0;39m [36mc.e.t.a.c.f.RequestLoggingFilter[0;39m [2m-[0;39m [HTTP-SLOW] POST /api/login 200 - 4574ms
2026-08-05T04:50:31.418970341Z  [auth-service]  [2m2026-08-05 13:50:31[0;39m [2m[http-nio-8081-exec-1][0;39m [33m WARN [traceId=6a72c1125884c1f3995f139ebaafc05e,spanId=995f139ebaafc05e,userId=NONE][0;39m [36mc.e.t.a.c.f.RequestLoggingFilter[0;39m [2m-[0;39m [HTTP-SLOW] POST /api/login 200 - 4574ms
2026-08-05T04:51:20.125670952Z  [auth-service]  [2m2026-08-05 13:51:20[0;39m [2m[http-nio-8081-exec-3][0;39m [33m WARN [traceId=6a72c1450b2fc3e5175b7adf422ce9a4,spanId=43bdeb518d0cefae,userId=NONE][0;39m [36mc.e.t.a.c.f.RequestLoggingFilter[0;39m [2m-[0;39m [HTTP-SLOW] GET /api/external/users/1/followings 200 - 2326ms
```

### 메트릭 시계열

| 쿼리 | series | 점 | min | max | last | 값이 0이던 구간 |
|---|---|---:|---:|---:|---:|---|
| `hikaricp_connections_active` | `{__name__=hikaricp_connections_active, application=auth-service, cluster=yogurtte-k3s-prod, container=auth-service, instance=10.42.1.46:8090, job=auth-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=auth-service-5999bb9f5c-hmgp9, pool=HikariPool-1, service=auth-service}` | 35 | 0 | 0 | 0 | **2026-08-05T04:40:00Z ~ 2026-08-05T04:48:30Z** |
| `hikaricp_connections_active` | `{__name__=hikaricp_connections_active, application=auth-service, cluster=yogurtte-k3s-prod, container=auth-service, instance=10.42.1.48:8090, job=auth-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=auth-service-5999bb9f5c-jv2jn, pool=HikariPool-1, service=auth-service}` | 35 | 0 | 0 | 0 | **2026-08-05T04:51:30Z ~ 2026-08-05T05:00:00Z** |
| `hikaricp_connections_active` | `{__name__=hikaricp_connections_active, application=chat-service, cluster=yogurtte-k3s-prod, container=chat-service, instance=10.42.1.47:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-fdcc7c776-xf4sv, pool=HikariPool-1}` | 81 | 0 | 0 | 0 | **2026-08-05T04:40:00Z ~ 2026-08-05T05:00:00Z** |
| `hikaricp_connections_active` | `{__name__=hikaricp_connections_active, application=content-service, cluster=yogurtte-k3s-prod, container=content-service, instance=10.42.1.43:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-6995bb7d94-h2f6n, pool=HikariPool-1}` | 60 | 0 | 0 | 0 | **2026-08-05T04:40:00Z ~ 2026-08-05T04:54:45Z** |
| `hikaricp_connections_active` | `{__name__=hikaricp_connections_active, application=content-service, cluster=yogurtte-k3s-prod, container=content-service, instance=10.42.1.49:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-64b7dfc78f-kjc8w, pool=HikariPool-1}` | 12 | 0 | 0 | 0 | **2026-08-05T04:57:15Z ~ 2026-08-05T05:00:00Z** |
| `hikaricp_connections_active` | `{__name__=hikaricp_connections_active, application=content-service, cluster=yogurtte-k3s-prod, container=content-service, instance=10.42.3.42:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-6995bb7d94-nq9l2, pool=HikariPool-1}` | 75 | 0 | 0 | 0 | **2026-08-05T04:40:00Z ~ 2026-08-05T04:58:30Z** |
| `hikaricp_connections_pending` | `{__name__=hikaricp_connections_pending, application=auth-service, cluster=yogurtte-k3s-prod, container=auth-service, instance=10.42.1.46:8090, job=auth-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=auth-service-5999bb9f5c-hmgp9, pool=HikariPool-1, service=auth-service}` | 35 | 0 | 0 | 0 | **2026-08-05T04:40:00Z ~ 2026-08-05T04:48:30Z** |
| `hikaricp_connections_pending` | `{__name__=hikaricp_connections_pending, application=auth-service, cluster=yogurtte-k3s-prod, container=auth-service, instance=10.42.1.48:8090, job=auth-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=auth-service-5999bb9f5c-jv2jn, pool=HikariPool-1, service=auth-service}` | 35 | 0 | 0 | 0 | **2026-08-05T04:51:30Z ~ 2026-08-05T05:00:00Z** |
| `hikaricp_connections_pending` | `{__name__=hikaricp_connections_pending, application=chat-service, cluster=yogurtte-k3s-prod, container=chat-service, instance=10.42.1.47:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-fdcc7c776-xf4sv, pool=HikariPool-1}` | 81 | 0 | 0 | 0 | **2026-08-05T04:40:00Z ~ 2026-08-05T05:00:00Z** |
| `hikaricp_connections_pending` | `{__name__=hikaricp_connections_pending, application=content-service, cluster=yogurtte-k3s-prod, container=content-service, instance=10.42.1.43:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-6995bb7d94-h2f6n, pool=HikariPool-1}` | 60 | 0 | 0 | 0 | **2026-08-05T04:40:00Z ~ 2026-08-05T04:54:45Z** |
| `hikaricp_connections_pending` | `{__name__=hikaricp_connections_pending, application=content-service, cluster=yogurtte-k3s-prod, container=content-service, instance=10.42.1.49:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-64b7dfc78f-kjc8w, pool=HikariPool-1}` | 12 | 0 | 0 | 0 | **2026-08-05T04:57:15Z ~ 2026-08-05T05:00:00Z** |
| `hikaricp_connections_pending` | `{__name__=hikaricp_connections_pending, application=content-service, cluster=yogurtte-k3s-prod, container=content-service, instance=10.42.3.42:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-6995bb7d94-nq9l2, pool=HikariPool-1}` | 75 | 0 | 0 | 0 | **2026-08-05T04:40:00Z ~ 2026-08-05T04:58:30Z** |
| `rate(jvm_gc_pause_seconds_sum[5m])` | `{action=end of major GC, application=chat-service, cause=Allocation Failure, cluster=yogurtte-k3s-prod, container=chat-service, gc=MarkSweepCompact, instance=10.42.1.47:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-fdcc7c776-xf4sv}` | 81 | 0 | 0 | 0 | **2026-08-05T04:40:00Z ~ 2026-08-05T05:00:00Z** |
| `rate(jvm_gc_pause_seconds_sum[5m])` | `{action=end of minor GC, application=auth-service, cause=G1 Evacuation Pause, cluster=yogurtte-k3s-prod, container=auth-service, gc=G1 Young Generation, instance=10.42.1.46:8090, job=auth-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=auth-service-5999bb9f5c-hmgp9, service=auth-service}` | 47 | 0 | 0 | 0 | **2026-08-05T04:40:00Z ~ 2026-08-05T04:51:30Z** |
| `rate(jvm_gc_pause_seconds_sum[5m])` | `{action=end of minor GC, application=auth-service, cause=G1 Evacuation Pause, cluster=yogurtte-k3s-prod, container=auth-service, gc=G1 Young Generation, instance=10.42.1.48:8090, job=auth-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=auth-service-5999bb9f5c-jv2jn, service=auth-service}` | 31 | 0 | 0 | 0 | **2026-08-05T04:52:30Z ~ 2026-08-05T05:00:00Z** |
| `rate(jvm_gc_pause_seconds_sum[5m])` | `{action=end of minor GC, application=auth-service, cause=Metadata GC Threshold, cluster=yogurtte-k3s-prod, container=auth-service, gc=G1 Young Generation, instance=10.42.1.48:8090, job=auth-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=auth-service-5999bb9f5c-jv2jn, service=auth-service}` | 31 | 0 | 0 | 0 | **2026-08-05T04:52:30Z ~ 2026-08-05T05:00:00Z** |
| `rate(jvm_gc_pause_seconds_sum[5m])` | `{action=end of minor GC, application=chat-service, cause=Allocation Failure, cluster=yogurtte-k3s-prod, container=chat-service, gc=Copy, instance=10.42.1.47:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-fdcc7c776-xf4sv}` | 81 | 0.000 | 0.000 | 0.000 | — |
| `rate(jvm_gc_pause_seconds_sum[5m])` | `{action=end of minor GC, application=content-service, cause=Allocation Failure, cluster=yogurtte-k3s-prod, container=content-service, gc=Copy, instance=10.42.1.43:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-6995bb7d94-h2f6n}` | 72 | 0 | 0.000 | 0.000 | **2026-08-05T04:43:00Z ~ 2026-08-05T04:48:45Z, 2026-08-05T04:53:00Z ~ 2026-08-05T04:53:45Z** |
| `rate(jvm_gc_pause_seconds_sum[5m])` | `{action=end of minor GC, application=content-service, cause=Allocation Failure, cluster=yogurtte-k3s-prod, container=content-service, gc=Copy, instance=10.42.1.49:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-64b7dfc78f-kjc8w}` | 8 | 0 | 0 | 0 | **2026-08-05T04:58:15Z ~ 2026-08-05T05:00:00Z** |
| `rate(jvm_gc_pause_seconds_sum[5m])` | `{action=end of minor GC, application=content-service, cause=Allocation Failure, cluster=yogurtte-k3s-prod, container=content-service, gc=Copy, instance=10.42.3.42:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-6995bb7d94-nq9l2}` | 81 | 0 | 0.000 | 0 | **2026-08-05T04:40:00Z ~ 2026-08-05T04:45:30Z, 2026-08-05T04:49:45Z ~ 2026-08-05T04:53:30Z, 2026-08-05T04:57:45Z ~ 2026-08-05T05:00:00Z** |
| `up` | `{__name__=up, app=node-exporter, cluster=yogurtte-k3s-prod, component=metrics, container=node-exporter, instance=ip-172-31-40-241, job=integrations/node_exporter, k8s_cluster_name=yogurtte-k3s-prod, namespace=monitoring, pod=grafana-k8s-monitoring-node-exporter-cph5l, source=kubernetes, workload=DaemonSet/grafana-k8s-monitoring-node-exporter}` | 81 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, app=node-exporter, cluster=yogurtte-k3s-prod, component=metrics, container=node-exporter, instance=ip-172-31-45-39, job=integrations/node_exporter, k8s_cluster_name=yogurtte-k3s-prod, namespace=monitoring, pod=grafana-k8s-monitoring-node-exporter-9kvqt, source=kubernetes, workload=DaemonSet/grafana-k8s-monitoring-node-exporter}` | 81 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, cluster=yogurtte-k3s-prod, container=auth-service, instance=10.42.1.46:8090, job=auth-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=auth-service-5999bb9f5c-hmgp9}` | 35 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, cluster=yogurtte-k3s-prod, container=auth-service, instance=10.42.1.48:8090, job=auth-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=auth-service-5999bb9f5c-jv2jn}` | 35 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, cluster=yogurtte-k3s-prod, container=chat-service, instance=10.42.1.47:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-fdcc7c776-xf4sv}` | 81 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, cluster=yogurtte-k3s-prod, container=content-service, instance=10.42.1.43:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-6995bb7d94-h2f6n}` | 60 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, cluster=yogurtte-k3s-prod, container=content-service, instance=10.42.1.49:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-64b7dfc78f-kjc8w}` | 12 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, cluster=yogurtte-k3s-prod, container=content-service, instance=10.42.3.42:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-6995bb7d94-nq9l2}` | 75 | 1 | 1 | 1 | — |
| `mongodb_up` | `{__name__=mongodb_up, cluster=yogurtte-k3s-prod, instance=infra-server, job=mongodb, k8s_cluster_name=yogurtte-k3s-prod}` | 81 | 1 | 1 | 1 | — |
| `kafka_brokers` | `{__name__=kafka_brokers, cluster=yogurtte-k3s-prod, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod}` | 81 | 1 | 1 | 1 | — |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=chat-service-fcm-tokens, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=0, topic=user.fcm-tokens}` | 81 | 0 | 0 | 0 | **2026-08-05T04:40:00Z ~ 2026-08-05T05:00:00Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=chat-service-fcm-tokens, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=1, topic=user.fcm-tokens}` | 81 | 0 | 0 | 0 | **2026-08-05T04:40:00Z ~ 2026-08-05T05:00:00Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=chat-service-fcm-tokens, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=2, topic=user.fcm-tokens}` | 81 | 0 | 0 | 0 | **2026-08-05T04:40:00Z ~ 2026-08-05T05:00:00Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=chat-service-notification-settings, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=0, topic=user.notification-settings}` | 81 | 0 | 0 | 0 | **2026-08-05T04:40:00Z ~ 2026-08-05T05:00:00Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=chat-service-notification-settings, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=1, topic=user.notification-settings}` | 81 | 0 | 0 | 0 | **2026-08-05T04:40:00Z ~ 2026-08-05T05:00:00Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=chat-service-notification-settings, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=2, topic=user.notification-settings}` | 81 | 0 | 0 | 0 | **2026-08-05T04:40:00Z ~ 2026-08-05T05:00:00Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=db-writer, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=0, topic=chat.messages}` | 81 | 0 | 0 | 0 | **2026-08-05T04:40:00Z ~ 2026-08-05T05:00:00Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=db-writer, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=1, topic=chat.messages}` | 81 | 0 | 0 | 0 | **2026-08-05T04:40:00Z ~ 2026-08-05T05:00:00Z** |
| `websocket_active_users` | `{__name__=websocket_active_users, application=chat-service, cluster=yogurtte-k3s-prod, container=chat-service, instance=10.42.1.47:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-fdcc7c776-xf4sv}` | 81 | 0 | 0 | 0 | **2026-08-05T04:40:00Z ~ 2026-08-05T05:00:00Z** |

값이 0이던 구간은 굵게 표시했다 — 프로세스가 사라져 시계열이 꺾인 것이 유일한 신호인 장애가 있다.

