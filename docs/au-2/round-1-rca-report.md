# RCA Report — `6a66b2c439929c47d4c8f275d8cc6986`

| 항목 | 값 |
|---|---|
| 모드 | rca |
| 질문 | 왜 로그인이 안 돼? |
| 시각 | 2026-07-27T04:03:49.009136800Z |
| provider | claude-cli |
| prompt | `.\prompts\system-prompt.md` |
| tokens | in 65414 / out 14027 · cost $0.8357 |
| elapsed | total 209164ms (tempo 766 · loki 178 · mimir 317 · assemble 2 · llm 207858) |

## 수집 범위 (Coverage)

- **window**: 2026-07-27T01:20:12.970296Z ~ 2026-07-27T01:24:13.093532Z (240s)
- **trace**: 49,266B / 65 spans
- **logs**: errwarn=3,914B · traceId=3,914B
- **metrics**: 3 수집, 누락 [kafka_consumer_fetch_manager_records_lag]
- **context**: 65,958 chars (~16,489 tok 추정)

## 수집 실패/누락

- Metric 'kafka_consumer_fetch_manager_records_lag' returned no series in this window; skipped.

---

## 1. 원인 후보 랭킹 (최대 3개)

1. **auth-service 파드(단일 레플리카)가 01:21:57Z경 가용성을 상실했고 그 이후 복구되지 않음** — 인증 토큰 발급 경로 자체가 죽어 로그인 실패
2. **auth-service 타깃만의 스크레이프/관측 파이프라인 결함** (파드는 살아 있고 관측만 소실된 경우) — 이 경우 로그인 실패의 원인은 미관측
3. **auth 파드가 위치한 노드(파드 CIDR 10.42.3.0/24)의 부분 장애** — 아래 반증으로 대부분 배제됨

> ⚠️ 먼저 밝힐 것: **제공된 traceId는 로그인 요청이 아니다.** 이 트레이스는 content-service의 `http get /feeds/scroll`(`http.url=/api/feeds/scroll`)이고 `status=200`, `outcome=SUCCESS`, `exception=none`, 총 123.2ms(1785115332.970296→1785115333.093532)로 정상 종료했다. 트레이스에는 auth-service span이 **하나도 없다.** 따라서 아래 판단은 전적으로 메트릭 시계열의 단절 패턴에 근거하며, 로그인 요청 자체의 직접 관측 증거는 **0건**이다.

---

## 2. 후보별 근거

### 후보 1 — auth-service 파드 가용성 상실

**근거**

- **3개 메트릭 계열이 모두 동일 시각에 끊긴다.** `hikaricp_connections_active`, `hikaricp_connections_pending`, `rate(jvm_gc_pause_seconds_sum[5m])` 세 쿼리 모두에서 `application="auth-service"`, `pod="auth-service-855c75679d-pnp9j"`, `instance="10.42.3.38:8090"` 시계열의 마지막 샘플이 **1785115317 (= 2026-07-27T01:21:57Z)** 이다.
- **동일 창의 다른 서비스는 끊기지 않았다.** chat-service(10.42.1.31), content-service(10.42.1.32, 10.42.3.39)는 세 쿼리 모두 **1785115452 (= 01:24:12Z)** 까지 17개 샘플이 연속으로 존재한다. auth만 8개에서 멈춘다 → **15초 스텝 기준 9개 스크레이프 연속 결측, 최소 135초(2분15초) 공백**, 조회 창 끝까지 회복 없음.
- **후속 파드 시계열이 나타나지 않는다.** 롤링 재시작이었다면 창 안에 다른 `pod=` 라벨의 새 시계열이 등장해야 하는데, 135초 동안 auth-service 계열은 **어떤 pod 라벨로도 존재하지 않는다.** 빠른 재기동이 아니라 지속적 부재(CrashLoopBackOff 또는 미복구)를 시사한다.
- **auth-service는 레플리카 1개다.** 메트릭상 auth-service pod는 `auth-855c75679d-pnp9j` 단 하나(content-service는 2개). 이 파드가 죽으면 로그인은 우회 경로 없이 100% 실패한다.
- **장애 직전 자원 압박 징후는 없다.** 소실 전 8개 샘플 동안 auth의 `hikaricp_connections_active`=0, `pending`=0, GC pause rate=0으로 완전 평탄 → DB 풀 고갈이나 GC 스톨로 서서히 죽은 것이 아니라 **급작스러운 종료**에 가깝다.
- **왜 content 요청은 성공하는데 로그인만 안 되는가 (가설).** 트레이스의 `security filterchain before` 이벤트에 `before JwtAuthenticationFilter`/`after JwtAuthenticationFilter`가 찍혀 있고 필터체인 12/12를 통과했지만, 그 사이에 **auth-service로 나가는 CLIENT span이 없다**(이 트레이스는 JDBC 33개·Redis 4개·필터체인까지 잡을 만큼 계측이 촘촘한데도). 즉 기존 토큰 검증은 content-service 로컬에서 이뤄지고 auth-service는 **토큰 발급(로그인)에만** 필요한 구조로 보인다. 그래서 auth가 죽어도 이미 로그인된 사용자의 피드 조회(01:22:12Z, 200 OK)는 멀쩡하고 **신규 로그인만 실패**하는 관측과 일치한다.

**확신도: 중간**
— "auth-service 타깃이 01:21:57Z부터 사라졌다"는 사실 자체는 확신도 높음이다. 다만 (a) 로그인 요청의 트레이스·로그를 하나도 확보하지 못했고, (b) Loki 두 쿼리가 모두 빈 결과이며, (c) 제공된 traceId가 무관한 요청이라, **"파드 소실 → 로그인 실패"라는 인과 연결은 정황 일치일 뿐 직접 관측되지 않았다.** 수집 공백을 감안해 한 단계 낮춘다.

**반증 데이터**
- 없음 — 관측값 중 이 후보와 배치되는 것은 없다.
- 단, 확신도를 낮추는 공백: Loki ERROR/WARN·traceId 쿼리 모두 결과 0이라 auth의 종료 직전 스택/에러 로그가 전무하고, `kafka_consumer_fetch_manager_records_lag`도 시계열 없음. 파드가 "왜" 죽었는지(OOMKilled / liveness 실패 / 노드 축출 / 배포)는 **판단 불가 = 데이터 부족.**

---

### 후보 2 — auth 타깃만의 스크레이프/관측 파이프라인 결함 (파드는 정상)

**근거**

- **이 환경의 관측 스택은 이미 한 축이 고장나 있음이 증명된다.** Loki 두 쿼리의 stats가 `totalLinesProcessed: 0`, `totalBytesProcessed: 0`, `totalStreams: 0`, `totalChunks: 0`, `execTime: 0.0003s` 이다. 이는 "라인을 스캔했지만 매칭이 없었다"가 아니라 **라벨 셀렉터가 어떤 스트림도 매치하지 못했다**는 뜻이다. 즉 로그 수집/쿼리 구성 자체에 결함이 있다. 로그 축이 이미 깨져 있으므로 메트릭 축의 결측도 실체 장애가 아닌 수집 문제일 가능성을 배제할 수 없다.
- auth-service 시계열만 다른 서비스에 없는 `service="auth-service"` 라벨을 추가로 갖고 있다 → **auth만 다른 경로/다른 디스커버리 규칙으로 스크레이프되고 있을 가능성.** 그 경로만 끊기면 지금과 똑같은 그림이 나온다.
- 파드 다운과 스크레이프 실패는 Prometheus 시계열만으로는 **원리적으로 구별 불가**하다(둘 다 series 종료로 나타남).

**확신도: 낮음**

**반증 데이터**
- content-service·chat-service는 **같은 시각까지 정상 스크레이프**되었다 → 스크레이프 전면 장애는 아니다.
- auth의 **3개 서로 다른 메트릭 패밀리가 정확히 같은 timestamp(1785115317)에서 동시에 끝난다** → 특정 쿼리/메트릭 이름의 문제가 아니라 타깃 단위 소실이며, 이는 파드 다운과 더 잘 맞는다.
- 무엇보다 **사용자 증상(로그인 불가)이 하필 소실된 그 서비스와 정확히 일치한다.** 순수 관측 결함이라면 증상과의 일치는 우연이어야 한다.

---

### 후보 3 — auth 파드가 있는 노드의 부분 장애

**근거**

- auth 파드 IP는 `10.42.3.38`. K3s/flannel은 노드당 /24를 할당하므로 `10.42.3.0/24`는 하나의 노드, `10.42.1.0/24`는 다른 노드다(트레이스 리소스 속성상 content-service-vqf9f=10.42.1.32는 `k8s.node.name=ip-172-31-45-39`). 노드 축출/NotReady였다면 그 위 파드가 함께 사라진다. — **이는 라벨에 `node`가 없어 IP 대역으로 추정한 가설이다.**

**확신도: 낮음**

**반증 데이터**
- **거의 배제됨.** 같은 `10.42.3.0/24` 대역의 `content-service-7bb7bffd56-jtvch` (10.42.3.39)가 **1785115452(01:24:12Z)까지 3개 메트릭 모두 정상 보고**했다. 같은 노드의 다른 파드가 멀쩡하므로 노드 단위 장애가 아니라 **auth 파드 개별 문제**로 좁혀진다.

---

### 데이터 부족으로 판단 불가한 것

| 항목 | 상태 |
|---|---|
| 실제 로그인 요청의 트레이스 | **없음** — 제공된 traceId는 `/feeds/scroll`(200 OK), auth span 0개 |
| 로그인 실패 시 클라이언트가 받은 HTTP 상태/에러 | **없음** |
| auth-service 종료 사유(OOMKilled/exitCode/liveness) | **없음** — Loki 0건, k8s 이벤트 미수집 |
| auth-service의 `up` / 재시작 카운트 / 메모리 사용량 | **미수집** |
| Kafka consumer lag | 시계열 없음(수집 실패) — 이번 질문과는 무관, 별건으로 확인 필요 |

---

## 3. 권장 다음 조치

**A. 후보 1 vs 후보 2를 가르는 결정적 확인 (최우선, 수 분 내)**

1. ```bash
   kubectl get pod -n default -l app=auth-service -o wide
   kubectl describe pod auth-service-855c75679d-pnp9j -n default
   ```
   → `restartCount`, `lastState.terminated.reason`(OOMKilled/Error), `exitCode`, `Ready` 확인. 파드가 없거나 CrashLoopBackOff면 후보 1 확정.
2. ```bash
   kubectl get events -n default --sort-by=.lastTimestamp | grep -i auth
   ```
   → **01:21:40~01:22:10Z** 구간의 `Killing`/`BackOff`/`Unhealthy`/`Evicted`/`FailedScheduling` 이벤트.
3. ```bash
   kubectl logs auth-service-855c75679d-pnp9j -n default --previous --tail=200
   ```
   → 종료 직전 스택트레이스.
4. Mimir에서 아래 쿼리를 **01:19~01:25Z** 구간으로 조회 — 파드 다운과 스크레이프 실패를 구별한다:
   - `up{job="auth-service"}` — 0이면 스크레이프 실패(타깃은 존재), 계열 소멸이면 타깃 자체 소멸
   - `kube_pod_container_status_restarts_total{pod=~"auth-service.*"}`
   - `container_memory_working_set_bytes{pod=~"auth-service.*"}` vs `kube_pod_container_resource_limits{resource="memory"}` — OOMKill 여부
   - `scrape_samples_scraped{job="auth-service"}`

**B. 증상과의 인과 연결 확보 (현재 완전히 비어 있는 축)**

5. Tempo에서 **로그인 요청 트레이스를 직접 검색**한다: `{service.name="auth-service"}` 또는 `{span.http.url=~".*login.*|.*token.*"}`, 시간 01:20~01:26Z. 실패한 로그인의 상태 코드와 실패 지점(게이트웨이 5xx/타임아웃 vs auth 내부 예외)을 확인해야 후보 1이 정황에서 근거로 승격된다.
6. Loki 쿼리를 고친다. 반환 stats가 `totalStreams: 0`이므로 **라벨 셀렉터가 틀렸다.** `GET /loki/api/v1/labels` 및 각 라벨의 `/values`로 실제 서비스 식별 라벨 이름·값을 먼저 확인한 뒤 쿼리를 재작성하고, 재조회할 것. 지금 상태에서는 "로그에 에러가 없다"를 **어떤 근거로도 사용할 수 없다.**

**C. 복구 및 재발 방지**

7. auth 파드가 죽어 있으면 즉시 `kubectl rollout restart deploy/auth-service` 후 `up{job="auth-service"}` 회복 확인. 단, 재시작 전 **`--previous` 로그와 describe 출력을 먼저 확보**할 것(증거 소실 방지).
8. **auth-service replicas 1 → 2 이상 + PodDisruptionBudget 설정.** 인증은 전 서비스의 진입점인데 현재 단일 파드 SPOF다(content-service는 이미 2개). 이번 사고의 영향 범위를 100%로 만든 구조적 요인이다.
9. `up{job="auth-service"} == 0 for 1m` 알림 규칙 추가. 지금은 2분 15초간 인증이 죽어 있었는데도 사용자 문의로 인지했다.
10. auth만 GC가 `G1 Young Generation`이고 chat/content는 `Copy`(SerialGC)다 → JVM ergonomics상 **auth의 컨테이너 메모리/CPU 할당이 다르다.** OOMKill 조사 시 auth의 `resources.limits`와 `-Xmx`/`MaxRAMPercentage`를 함께 볼 것.

**D. 별건 (이번 로그인 장애와 무관, 놓치지 말 것)**

11. `kafka_consumer_fetch_manager_records_lag` 시계열 부재 — chat-service 컨슈머 미기동 또는 Kafka 클라이언트 메트릭 미노출 가능성. content→Kafka→chat 알림 경로 상태를 별도로 확인.
12. (성능 부수 관찰) 이번 트레이스의 `/feeds/scroll`은 피드 11건에 대해 `tb_feed_hashtags where feed_id=?` 11회 + `categories where category_id=?` 11회를 개별 실행하는 **명백한 N+1**이다. 총 123ms라 지금은 문제가 아니지만 부하 시 위험. **이번 장애의 원인은 아니다.**
