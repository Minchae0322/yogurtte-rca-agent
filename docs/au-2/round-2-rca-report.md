# RCA Report — `traceId 없음`

| 항목 | 값 |
|---|---|
| 모드 | rca |
| 질문 | 최근 1시간 안에 로그인이 안 된다는 문의가 몰렸다. 원인을 조사해줘 |
| 시각 | 2026-07-29T01:39:13.509755200Z |
| provider | claude-cli |
| model | `claude-opus-5` · turns 1 |
| prompt | `.\prompts\system-prompt.md` |
| tokens | in 89325 (cacheRead 23,453 · cacheCreate 65,870) / out 11775 · cost $1.0146 |
| elapsed | total 176960ms (tempo 0 · loki 202 · mimir 705 · assemble 15 · llm 169478) |

## 탐색 (Triage)

| 항목 | 값 |
|---|---|
| 시간창 해석 | 상대 표현 '최근 1시간' |
| 스윕 창 | 2026-07-29T00:38:33.412503900Z ~ 2026-07-29T01:38:33.412503900Z |
| 좁힌 창 | 2026-07-29T01:25:00Z ~ 2026-07-29T01:38:33Z |
| 대상 | auth-service |
| traceId | (없음) |
| 트레이스 후보 | 1건 |
| 계획 파싱 | 성공 |
| prompt | `.\prompts\triage-prompt.md` |
| tokens | in 48245 / out 2832 · cost $0.3746 |
| chars | 컨텍스트 38,607 + 프롬프트 1,231 = **39,838** |
| elapsed | survey 2147ms · llm 37885ms |

**선정 이유**: 로그인 문의가 몰린 시간대에 auth-service 파드만 01:28:33Z 이후 연속 교체되고 01:35:00Z에 auth 에러 로그가 집중되었으므로, 파드 종료 원인(재시작 사유·기동 실패 로그)을 확인하려면 그 직전 여유를 포함한 01:25~01:38 구간의 auth-service를 봐야 한다.

**근거**

- up{job="auth-service",pod="auth-service-855c75679d-45fxb",instance="10.42.1.34:8090"} 시계열이 1785288513(01:28:33Z)을 마지막으로 종료 — 이후 샘플 없음
- up{pod="auth-service-855c75679d-x7rtr",instance="10.42.1.36:8090"}가 1785288813(01:33:33Z)에 단 1샘플만 존재하고 사라짐
- up{pod="auth-service-855c75679d-wf6c7",instance="10.42.1.37:8090"}가 1785289113(01:38:33Z)에 새로 등장 — 10분 내 파드 IP 1.34→1.36→1.37로 2회 교체
- Loki ERROR/WARN: auth-service가 1785288900(01:35:00Z) 버킷에서만 5건 발생, 조회 1시간 중 auth의 유일한 에러 버킷이며 파드 교체 시점과 일치
- Tempo 에러 트레이스 1건은 content-service http post /feeds(00:56:32Z)뿐이고 auth-service 트레이스는 0건 — 로그인 실패 구간에 auth 트레이스가 아예 생성되지 않음
- mongodb_up=1, kafka_brokers=1, redis/node-infra/kubelet/cadvisor 모두 전 구간 up=1 — 인프라 공통 장애 아님
- chat-service·content-service의 up은 전 구간 연속, websocket_active_users는 0으로 평탄 — 파드 교체는 auth-service에만 발생

**스윕이 찾은 트레이스** (고른 것은 (없음))

| traceId | root service | root span | ms |
|---|---|---|---:|
| `6a694fc02926ea82d0bdbb4434237626` | content-service | http post /feeds | 74 |

## 수집 범위 (Coverage)

- **window**: 2026-07-29T01:25:00Z ~ 2026-07-29T01:38:33Z (813s)
- **trace**: 0B / 0 spans
- **logs**: errwarn=7,861B · traceId=0B
- **metrics**: 8 수집 / 103,094B, 누락 [sum(rate(http_server_requests_seconds_count{application="content-service", status="401"}[1m]))]
- **context**: 111,688 chars (+ 시스템 프롬프트 597 chars)

### 토큰 축 (개선 지표)

| 단계 | 총 in | chars | 컨텍스트 토큰 |
|---|---:|---:|---:|
| 탐색 | 48,245 | 39,838 | 21,669 |
| 분석 | 89,325 | 112,285 | 62,749 |
| **합계** | **137,570** | | **84,418** |

- **overheadTokens 26,576 tok — 이 회차에 실측**(1자 프롬프트 프로브, 같은 명령·모델·샌드박스).
  호출마다 한 번씩 얹히므로 단계별로 뺀다. **다른 날 상수를 쓰지 않았다 = `█ 실측`.**
- contextTokens (count_tokens API): 측정 안 됨 — 구독 CLI 경로엔 API 키가 없다
- 비용은 **API 환산 추정치**다. 구독 계정으로 호출하므로 실제 청구액이 아니다.

## 수집 실패/누락

- 이 조사에는 대표 traceId가 없다 — 탐색이 트레이스를 찾지 못했거나 트레이스가 생성되지 않는 장애다. 트레이스 부재 자체를 근거로 쓸 것.
- Metric 'sum(rate(http_server_requests_seconds_count{application="content-service", status="401"}[1m]))' returned no series in this window; skipped.

---

주어진 데이터만으로 분석했습니다. 모든 타임스탬프는 UTC로 환산했습니다(로그의 `10:34:17`은 KST = `01:34:17Z`).

## 0. 재구성한 타임라인 (auth-service)

| 시각(UTC) | 관측값 |
|---|---|
| 01:25:00 ~ 01:30:00 | pod `auth-service-855c75679d-45fxb` (10.42.1.34) `up=1` — 이후 시계열 소멸 |
| 01:30:00 ~ 01:33:00 | **auth-service 스크레이프 대상 0개 (공백)** |
| 01:30:39 / 01:31:01 / 01:31:39 | pod `...-x7rtr` 기동 로그 (`[main]` BeanPostProcessorChecker → Hibernate dialect → JpaWebConfiguration) |
| 01:33:00 ~ 01:33:45 | `...-x7rtr` (10.42.1.36) `up=1` — 샘플 4개(45초)뿐, 이후 소멸 |
| 01:33:45 ~ 01:36:30 | **auth-service 스크레이프 대상 0개 (공백)** |
| 01:34:17 / 01:34:38 / 01:35:15 | pod `...-wf6c7` 기동 로그 (동일 3종 시퀀스) |
| 01:36:30 ~ 01:38:30 | `...-wf6c7` (10.42.1.37) `up=1` |

13.5분 창에서 auth-service pod가 **3개(45fxb → x7rtr → wf6c7)** 교체되었고, 세 시계열이 서로 겹치지 않습니다. 세 pod 모두 ReplicaSet 해시가 `855c75679d`로 동일 → **신규 배포(롤아웃)가 아니라 동일 파드 템플릿의 재시작/재스케줄**입니다. 반면 chat(`10.42.1.31`), content(`10.42.1.35`, `10.42.3.41`)는 창 전체에서 단일 pod로 연속 `up=1`입니다.

## 1. 원인 후보 랭킹 (최대 3개)

1. **auth-service 파드 재시작 루프로 인한 로그인 엔드포인트 간헐적 전면 중단** (동시 가동 인스턴스가 관측상 항상 1개 이하 → 재시작 중 100% 중단)
2. **재시작 트리거가 애플리케이션 예외가 아닌 컨테이너 외부 강제 종료(OOMKilled / liveness probe 실패 / eviction)** — 종료 시점 ERROR 로그가 전무
3. **auth 하위 의존성(DB 커넥션 풀·GC·Kafka·Mongo·Redis) 포화/장애** — 데이터상 사실상 배제됨

## 2. 후보별 근거

### 후보 1 — auth-service 재시작 루프 → 로그인 중단
- **근거**
  - `up{job="auth-service"}` 시계열이 pod별로 단절: `45fxb` 01:25:00~01:30:00, `x7rtr` 01:33:00~01:33:45, `wf6c7` 01:36:30~01:38:30. **01:30:00~01:33:00(3분), 01:33:45~01:36:30(2분45초) 동안 auth 인스턴스가 하나도 관측되지 않음** → 창 13.5분 중 약 5분45초(≈43%)가 공백.
  - 동일 창에서 `hikaricp_connections_active{application="auth-service"}`, `hikaricp_connections_pending`, `jvm_gc_pause` 모두 같은 시간대에만 존재 → 메트릭 리텐션 문제가 아니라 **프로세스 자체가 없었음**.
  - Loki에 동일한 Spring 기동 로그 3종 세트가 pod마다 반복 기록: `"Bean 'org.springframework.ws.config.annotation.DelegatingWsConfiguration' ... is not eligible for getting processed by all BeanPostProcessors"`, `"HHH90000025: MySQLDialect does not need to be specified explicitly"`, `"spring.jpa.open-in-view is enabled by default"` — 모두 `[main]` 스레드 = **부팅 시퀀스**. `x7rtr`은 01:30:39, `wf6c7`은 01:34:17에 각각 새로 부팅.
  - 부팅 첫 로그(01:34:17) → 첫 메트릭 스크레이프(01:36:30) 간격 약 2분 → **재시작 1회당 로그인 불가 시간이 2분 이상**. "문의가 몰렸다"는 증상과 시간 규모가 일치.
  - **traceId 부재 자체가 근거**: 로그인 트랜잭션의 트레이스가 하나도 수집되지 않았고, auth 로그의 모든 라인이 `[traceId=NONE,spanId=NONE,userId=NONE]`(부팅 로그라 당연)입니다. 즉 조회 창 안에 **정상 처리된 로그인 요청 스팬이 존재하지 않습니다.**
  - `x7rtr`은 기동 시작(01:30:39) 후 스크레이프가 잡힌 시간이 45초뿐이고 곧 사라짐 → **Ready 직후 사망**하는 전형적 패턴.
- **확신도**: **높음** (auth-service가 반복 재시작했고 그 사이 로그인이 불가능했다는 사실 자체)
- **반증 데이터**
  - `up{job="auth-service"}`가 `0`으로 기록된 샘플은 **한 건도 없습니다.** 스크레이프 실패가 아니라 타깃 자체가 사라지는 형태라, 이론적으로는 서비스 디스커버리/메트릭 파이프라인 공백일 여지가 남습니다(단, pod 이름 3종 교체 + 반복 부팅 로그가 이를 강하게 반박).
  - `websocket_active_users{chat-service}`가 창 전체에서 **계속 0**, auth/content/chat의 `hikaricp_connections_active`도 거의 항상 0 → 이 환경의 실제 트래픽이 매우 낮을 가능성. "문의 폭주" 규모의 사용자 트래픽을 뒷받침하는 관측값은 없습니다.
  - auth 레플리카 수가 실제로 1인지 확인할 데이터(`kube_deployment_spec_replicas`)가 없습니다. 동시 가동 시계열이 없다는 점에서 추정한 값입니다.

### 후보 2 — 외부 강제 종료(OOMKilled / probe 실패 / eviction)
- **근거**
  - 조회 창에서 auth-service의 **ERROR 로그가 0건**입니다. Loki가 반환한 6줄은 전부 `WARN`이며 전부 기동 시 나오는 정보성 경고(`BeanPostProcessorChecker`, `HHH90000025`, `open-in-view`)입니다. 예외로 죽는 프로세스라면 종료 직전 ERROR/스택트레이스가 남는 것이 일반적인데 그 흔적이 없습니다 → **JVM이 로그를 남길 틈 없이 외부에서 종료**됐을 가능성.
  - 재시작이 동일 ReplicaSet(`855c75679d`) 내에서 발생 → 배포/설정 롤아웃이 아님.
  - 노드 단위 문제는 아닙니다: 세 auth pod IP(`10.42.1.34/.36/.37`)와 같은 대역의 chat(`10.42.1.31`), content(`10.42.1.35`)는 무중단이며, 전체 노드(`ip-172-31-38-225`, `-40-241`, `-45-39`, `worker-proxy`)의 kubelet/cadvisor `up=1`이 창 내내 유지됩니다.
  - 기동에 약 2분이 걸리는데(01:34:17 → 01:36:30 Ready), liveness probe의 `initialDelaySeconds`가 이보다 짧으면 기동 중 kill → 무한 재시작이 성립합니다. **이는 데이터로 확인된 사실이 아니라 관측된 기동 소요시간에서 나온 가설입니다.**
- **확신도**: **중간** — 종료 원인을 직접 가리키는 관측값(재시작 카운트, 컨테이너 메모리, K8s 이벤트)이 **하나도 수집되지 않았습니다.**
- **반증 데이터**
  - Loki 쿼리가 `ERROR/WARN`으로 필터링되어 있어 **INFO 레벨(정상 종료 시 남는 `Shutting down`, `Closing JPA EntityManagerFactory` 등)을 볼 수 없습니다.** 따라서 "정상 종료 로그가 없다 = SIGKILL"이라고 단정할 수 없습니다. 이 후보의 핵심 구분은 아직 미검증입니다.
  - OOM을 뒷받침할 메모리 압박 신호도 없습니다: auth의 `jvm_gc_pause` rate가 관측 구간 내내 `0`이고, 전체 노드 exporter `up=1`. 단, auth pod 수명이 짧아 GC 메트릭 표본 자체가 극히 적습니다(45fxb 33개, wf6c7 5개).

### 후보 3 — 하위 의존성/리소스 포화 (DB 풀·GC·Kafka·Mongo·Redis)
- **근거**: 로그인 지연/실패의 흔한 원인이라 검토했으나, 이를 지지하는 관측값이 없습니다.
- **확신도**: **낮음** (사실상 배제)
- **반증 데이터**
  - `hikaricp_connections_pending`이 auth/chat/content **전 pod·전 시점 0** → 커넥션 풀 대기 없음. `hikaricp_connections_active`도 content-service가 01:30:15~01:31:00에 잠깐 `1`을 기록한 것을 제외하면 전부 `0`.
  - `rate(jvm_gc_pause_seconds_sum[5m])` 최대값이 chat-service의 `0.000687`(초/초, ≈0.07% 정지율), auth는 `0`. GC 병목 없음.
  - `mongodb_up=1`, `kafka_brokers=1`, `up{job="redis"}=1`, `up{job="mongodb"}=1`, `up{job="kafka"}=1` — 창 전체 안정.
  - `kafka_consumergroup_lag`은 모든 그룹(`notification`, `db-writer`, `chat-service-fcm-tokens`, `notification-processors`, `notification-recovery`, 각 retry/DLQ)에서 `0`입니다. 일부 파티션(chat.messages 2/6/9/10, user.notifications.dlq 1)의 `-1`은 창 전체에서 값이 변하지 않는 상수로, 커밋 오프셋 미존재를 뜻하는 exporter 센티널이며 이번 장애와 상관 변화가 없습니다.
  - 애초에 content → Kafka → chat 경로는 알림 발송 경로이지 **로그인 경로가 아닙니다.**

### 데이터 부족 (판단 불가 영역)
- **로그인 실패 자체를 직접 증명하는 데이터가 없습니다.** 수집된 메트릭 쿼리는 `http_server_requests_seconds_count{application="content-service", status="401"}`였고 시리즈가 없어 스킵됐는데, 이는 **대상 서비스가 잘못 지정된 쿼리**입니다(로그인 담당은 auth-service). auth-service의 HTTP 상태코드별 요청 수 데이터는 전혀 없습니다.
- **재시작 원인을 특정할 데이터가 전부 없습니다**: `kube_pod_container_status_restarts_total`, `kube_pod_container_status_last_terminated_reason`, `container_memory_working_set_bytes` 대 메모리 limit, K8s 이벤트(`Killing`/`Unhealthy`/`OOMKilling`), auth-service의 INFO 레벨 로그.
- **조회 창이 문의 기간을 덮지 못합니다.** 사용자 문의는 "최근 1시간"인데 데이터는 01:25:00~01:38:33의 **13.5분**뿐입니다. 01:25 이전의 재시작 이력은 확인 불가입니다.
- 트레이스가 0건이라 요청 경로(게이트웨이 → auth → DB) 어디에서 끊겼는지 span 단위 확인이 불가능합니다.

> 위 세 가지 공백(트레이스 전무, auth HTTP 메트릭 전무, K8s 상태 메트릭 전무)을 감안해, **"auth가 재시작으로 중단됐다"는 높음이지만 "왜 재시작했는가"는 중간 이하**로 확신도를 낮춰 잡았습니다.

## 3. 권장 다음 조치

**즉시 (원인 확정)**
1. 종료 사유 확인 — 후보 2를 판별하는 단일 결정적 증거:
   ```
   kubectl -n default get pods -l app=auth-service -o wide
   kubectl -n default describe pod auth-service-855c75679d-wf6c7 | grep -A5 "Last State"
   kubectl -n default get events --sort-by=.lastTimestamp | grep auth-service
   ```
   `Last State: Terminated, Reason: OOMKilled / Error, Exit Code`(137=SIGKILL, 143=SIGTERM)와 `Killing`/`Unhealthy` 이벤트를 확인합니다.
2. Loki 재조회 시 **레벨 필터를 제거**하고 `{service_name="auth-service"}` 전체 로그를 01:20~01:40Z로 조회 → 종료 직전 라인에 graceful shutdown(INFO)이 있는지로 SIGTERM/SIGKILL을 구분합니다.
3. Mimir 추가 쿼리:
   - `kube_pod_container_status_restarts_total{pod=~"auth-service.*"}` (재시작 횟수·주기)
   - `container_memory_working_set_bytes{pod=~"auth-service.*"}` 와 `kube_pod_container_resource_limits{resource="memory", pod=~"auth-service.*"}` 비교
   - `sum by (status) (rate(http_server_requests_seconds_count{application="auth-service"}[1m]))` — **실패한 쿼리의 교정본**. 401/5xx 실제 로그인 실패율 확인.
   - `kube_deployment_spec_replicas{deployment="auth-service"}` — 레플리카 1개 가정 검증.

**즉시 (완화)**
4. auth-service 레플리카를 2개 이상으로 증설. 현재 관측대로 동시 가동 인스턴스가 1개라면 **재시작 때마다 로그인이 100% 중단**됩니다.
5. probe 설정 점검: 실측 기동 시간이 약 2분(01:34:17 부팅 → 01:36:30 Ready)이므로, `livenessProbe.initialDelaySeconds`가 이보다 짧으면 기동 중 kill → 재시작 루프가 성립합니다. `startupProbe`(`failureThreshold` × `periodSeconds` ≥ 180초)를 도입해 liveness와 분리하십시오.
6. 메모리 limit 대비 JVM heap 설정(`-Xmx` 또는 `MaxRAMPercentage`) 확인 — heap + metaspace + 네이티브가 limit을 넘으면 OOMKill로 이어집니다.

**관측성 복구 (다음 장애 대비)**
7. 조회 창을 01:25 이전(최소 00:38~)으로 확장해 `45fxb` 이전 재시작 이력과 문의 시작 시점의 정합성을 확인.
8. auth-service의 OTel 트레이스가 Tempo로 전송되지 않고 있습니다. exporter 엔드포인트/샘플링 설정과 Tempo 수신을 점검하십시오 — 이번 조사에서 요청 경로 분석이 불가능했던 직접적 원인입니다.
9. `absent(up{job="auth-service"}) == 1` 또는 `changes(kube_pod_container_status_restarts_total{pod=~"auth-service.*"}[10m]) > 0` 알람 추가. 이번 건은 `up=0`이 아니라 **타깃 소멸** 형태여서 일반적인 `up==0` 알람으로는 감지되지 않습니다.

---

## 관측 증거 (Evidence)

> 이 조사가 실제로 본 값이다. 원본 응답 전체는 `reports/raw/scan-*.json`에 있다.

### 로그 원문 (6 / 전체 6줄)

```
2026-07-29T01:30:39.684470751Z  [auth-service]  [2m2026-07-29 10:30:39[0;39m [2m[main][0;39m [33m WARN [traceId=NONE,spanId=NONE,userId=NONE][0;39m [36mo.s.c.s.PostProcessorRegistrationDelegate$BeanPostProcessorChecker[0;39m [2m-[0;39m Bean 'org.springframework.ws.config.annotation.DelegatingWsConfiguration' of type [org.springframework.ws.config.annotation.DelegatingWsConfiguration$$SpringCGLIB$$0] is not eligible for getting processed by all BeanPostProcessors (for example: not eligible for auto-proxying). The currently created BeanPostProcessor [annotationActionEndpointMapping] is declared through a non-static factory method on that class; consider declaring it as static instead.
2026-07-29T01:31:01.118207181Z  [auth-service]  [2m2026-07-29 10:31:01[0;39m [2m[main][0;39m [33m WARN [traceId=NONE,spanId=NONE,userId=NONE][0;39m [36morg.hibernate.orm.deprecation[0;39m [2m-[0;39m HHH90000025: MySQLDialect does not need to be specified explicitly using 'hibernate.dialect' (remove the property setting and it will be selected by default)
2026-07-29T01:31:39.214751449Z  [auth-service]  [2m2026-07-29 10:31:39[0;39m [2m[main][0;39m [33m WARN [traceId=NONE,spanId=NONE,userId=NONE][0;39m [36mo.s.b.a.o.j.JpaBaseConfiguration$JpaWebConfiguration[0;39m [2m-[0;39m spring.jpa.open-in-view is enabled by default. Therefore, database queries may be performed during view rendering. Explicitly configure spring.jpa.open-in-view to disable this warning
2026-07-29T01:34:17.543609563Z  [auth-service]  [2m2026-07-29 10:34:17[0;39m [2m[main][0;39m [33m WARN [traceId=NONE,spanId=NONE,userId=NONE][0;39m [36mo.s.c.s.PostProcessorRegistrationDelegate$BeanPostProcessorChecker[0;39m [2m-[0;39m Bean 'org.springframework.ws.config.annotation.DelegatingWsConfiguration' of type [org.springframework.ws.config.annotation.DelegatingWsConfiguration$$SpringCGLIB$$0] is not eligible for getting processed by all BeanPostProcessors (for example: not eligible for auto-proxying). The currently created BeanPostProcessor [annotationActionEndpointMapping] is declared through a non-static factory method on that class; consider declaring it as static instead.
2026-07-29T01:34:38.101759635Z  [auth-service]  [2m2026-07-29 10:34:38[0;39m [2m[main][0;39m [33m WARN [traceId=NONE,spanId=NONE,userId=NONE][0;39m [36morg.hibernate.orm.deprecation[0;39m [2m-[0;39m HHH90000025: MySQLDialect does not need to be specified explicitly using 'hibernate.dialect' (remove the property setting and it will be selected by default)
2026-07-29T01:35:15.343707572Z  [auth-service]  [2m2026-07-29 10:35:15[0;39m [2m[main][0;39m [33m WARN [traceId=NONE,spanId=NONE,userId=NONE][0;39m [36mo.s.b.a.o.j.JpaBaseConfiguration$JpaWebConfiguration[0;39m [2m-[0;39m spring.jpa.open-in-view is enabled by default. Therefore, database queries may be performed during view rendering. Explicitly configure spring.jpa.open-in-view to disable this warning
```

### 메트릭 시계열

| 쿼리 | series | 점 | min | max | last | 값이 0이던 구간 |
|---|---|---:|---:|---:|---:|---|
| `hikaricp_connections_active` | `{__name__=hikaricp_connections_active, application=auth-service, cluster=yogurtte-k3s-prod, container=auth-service, instance=10.42.1.34:8090, job=auth-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=auth-service-855c75679d-45fxb, pool=HikariPool-1, service=auth-service}` | 21 | 0 | 0 | 0 | **2026-07-29T01:25:00Z ~ 2026-07-29T01:30:00Z** |
| `hikaricp_connections_active` | `{__name__=hikaricp_connections_active, application=auth-service, cluster=yogurtte-k3s-prod, container=auth-service, instance=10.42.1.36:8090, job=auth-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=auth-service-855c75679d-x7rtr, pool=HikariPool-1, service=auth-service}` | 4 | 0 | 0 | 0 | **2026-07-29T01:33:00Z ~ 2026-07-29T01:33:45Z** |
| `hikaricp_connections_active` | `{__name__=hikaricp_connections_active, application=auth-service, cluster=yogurtte-k3s-prod, container=auth-service, instance=10.42.1.37:8090, job=auth-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=auth-service-855c75679d-wf6c7, pool=HikariPool-1, service=auth-service}` | 9 | 0 | 0 | 0 | **2026-07-29T01:36:30Z ~ 2026-07-29T01:38:30Z** |
| `hikaricp_connections_active` | `{__name__=hikaricp_connections_active, application=chat-service, cluster=yogurtte-k3s-prod, container=chat-service, instance=10.42.1.31:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-857c54dd97-s5fbl, pool=HikariPool-1}` | 55 | 0 | 0 | 0 | **2026-07-29T01:25:00Z ~ 2026-07-29T01:38:30Z** |
| `hikaricp_connections_active` | `{__name__=hikaricp_connections_active, application=content-service, cluster=yogurtte-k3s-prod, container=content-service, instance=10.42.1.35:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-6c5fff897-qnxk6, pool=HikariPool-1}` | 55 | 0 | 1 | 0 | **2026-07-29T01:25:00Z ~ 2026-07-29T01:30:00Z, 2026-07-29T01:31:15Z ~ 2026-07-29T01:38:30Z** |
| `hikaricp_connections_active` | `{__name__=hikaricp_connections_active, application=content-service, cluster=yogurtte-k3s-prod, container=content-service, instance=10.42.3.41:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-6c5fff897-scw7k, pool=HikariPool-1}` | 55 | 0 | 0 | 0 | **2026-07-29T01:25:00Z ~ 2026-07-29T01:38:30Z** |
| `hikaricp_connections_pending` | `{__name__=hikaricp_connections_pending, application=auth-service, cluster=yogurtte-k3s-prod, container=auth-service, instance=10.42.1.34:8090, job=auth-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=auth-service-855c75679d-45fxb, pool=HikariPool-1, service=auth-service}` | 21 | 0 | 0 | 0 | **2026-07-29T01:25:00Z ~ 2026-07-29T01:30:00Z** |
| `hikaricp_connections_pending` | `{__name__=hikaricp_connections_pending, application=auth-service, cluster=yogurtte-k3s-prod, container=auth-service, instance=10.42.1.36:8090, job=auth-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=auth-service-855c75679d-x7rtr, pool=HikariPool-1, service=auth-service}` | 4 | 0 | 0 | 0 | **2026-07-29T01:33:00Z ~ 2026-07-29T01:33:45Z** |
| `hikaricp_connections_pending` | `{__name__=hikaricp_connections_pending, application=auth-service, cluster=yogurtte-k3s-prod, container=auth-service, instance=10.42.1.37:8090, job=auth-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=auth-service-855c75679d-wf6c7, pool=HikariPool-1, service=auth-service}` | 9 | 0 | 0 | 0 | **2026-07-29T01:36:30Z ~ 2026-07-29T01:38:30Z** |
| `hikaricp_connections_pending` | `{__name__=hikaricp_connections_pending, application=chat-service, cluster=yogurtte-k3s-prod, container=chat-service, instance=10.42.1.31:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-857c54dd97-s5fbl, pool=HikariPool-1}` | 55 | 0 | 0 | 0 | **2026-07-29T01:25:00Z ~ 2026-07-29T01:38:30Z** |
| `hikaricp_connections_pending` | `{__name__=hikaricp_connections_pending, application=content-service, cluster=yogurtte-k3s-prod, container=content-service, instance=10.42.1.35:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-6c5fff897-qnxk6, pool=HikariPool-1}` | 55 | 0 | 0 | 0 | **2026-07-29T01:25:00Z ~ 2026-07-29T01:38:30Z** |
| `hikaricp_connections_pending` | `{__name__=hikaricp_connections_pending, application=content-service, cluster=yogurtte-k3s-prod, container=content-service, instance=10.42.3.41:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-6c5fff897-scw7k, pool=HikariPool-1}` | 55 | 0 | 0 | 0 | **2026-07-29T01:25:00Z ~ 2026-07-29T01:38:30Z** |
| `rate(jvm_gc_pause_seconds_sum[5m])` | `{action=end of major GC, application=chat-service, cause=Allocation Failure, cluster=yogurtte-k3s-prod, container=chat-service, gc=MarkSweepCompact, instance=10.42.1.31:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-857c54dd97-s5fbl}` | 55 | 0 | 0 | 0 | **2026-07-29T01:25:00Z ~ 2026-07-29T01:38:30Z** |
| `rate(jvm_gc_pause_seconds_sum[5m])` | `{action=end of minor GC, application=auth-service, cause=G1 Evacuation Pause, cluster=yogurtte-k3s-prod, container=auth-service, gc=G1 Young Generation, instance=10.42.1.34:8090, job=auth-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=auth-service-855c75679d-45fxb, service=auth-service}` | 33 | 0 | 0 | 0 | **2026-07-29T01:25:00Z ~ 2026-07-29T01:33:00Z** |
| `rate(jvm_gc_pause_seconds_sum[5m])` | `{action=end of minor GC, application=auth-service, cause=G1 Evacuation Pause, cluster=yogurtte-k3s-prod, container=auth-service, gc=G1 Young Generation, instance=10.42.1.37:8090, job=auth-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=auth-service-855c75679d-wf6c7, service=auth-service}` | 5 | 0 | 0 | 0 | **2026-07-29T01:37:30Z ~ 2026-07-29T01:38:30Z** |
| `rate(jvm_gc_pause_seconds_sum[5m])` | `{action=end of minor GC, application=auth-service, cause=Metadata GC Threshold, cluster=yogurtte-k3s-prod, container=auth-service, gc=G1 Young Generation, instance=10.42.1.37:8090, job=auth-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=auth-service-855c75679d-wf6c7, service=auth-service}` | 5 | 0 | 0 | 0 | **2026-07-29T01:37:30Z ~ 2026-07-29T01:38:30Z** |
| `rate(jvm_gc_pause_seconds_sum[5m])` | `{action=end of minor GC, application=chat-service, cause=Allocation Failure, cluster=yogurtte-k3s-prod, container=chat-service, gc=Copy, instance=10.42.1.31:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-857c54dd97-s5fbl}` | 55 | 0.000 | 0.001 | 0.001 | — |
| `rate(jvm_gc_pause_seconds_sum[5m])` | `{action=end of minor GC, application=content-service, cause=Allocation Failure, cluster=yogurtte-k3s-prod, container=content-service, gc=Copy, instance=10.42.1.35:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-6c5fff897-qnxk6}` | 55 | 0 | 0.000 | 0 | **2026-07-29T01:25:15Z ~ 2026-07-29T01:31:00Z, 2026-07-29T01:35:15Z ~ 2026-07-29T01:38:30Z** |
| `rate(jvm_gc_pause_seconds_sum[5m])` | `{action=end of minor GC, application=content-service, cause=Allocation Failure, cluster=yogurtte-k3s-prod, container=content-service, gc=Copy, instance=10.42.3.41:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-6c5fff897-scw7k}` | 55 | 0 | 0.000 | 0.000 | **2026-07-29T01:25:00Z ~ 2026-07-29T01:25:45Z, 2026-07-29T01:30:00Z ~ 2026-07-29T01:37:45Z** |
| `up` | `{__name__=up, app=node-exporter, cluster=yogurtte-k3s-prod, component=metrics, container=node-exporter, instance=ip-172-31-40-241, job=integrations/node_exporter, k8s_cluster_name=yogurtte-k3s-prod, namespace=monitoring, pod=grafana-k8s-monitoring-node-exporter-cph5l, source=kubernetes, workload=DaemonSet/grafana-k8s-monitoring-node-exporter}` | 55 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, app=node-exporter, cluster=yogurtte-k3s-prod, component=metrics, container=node-exporter, instance=ip-172-31-45-39, job=integrations/node_exporter, k8s_cluster_name=yogurtte-k3s-prod, namespace=monitoring, pod=grafana-k8s-monitoring-node-exporter-9kvqt, source=kubernetes, workload=DaemonSet/grafana-k8s-monitoring-node-exporter}` | 55 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, cluster=yogurtte-k3s-prod, container=auth-service, instance=10.42.1.34:8090, job=auth-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=auth-service-855c75679d-45fxb}` | 21 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, cluster=yogurtte-k3s-prod, container=auth-service, instance=10.42.1.36:8090, job=auth-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=auth-service-855c75679d-x7rtr}` | 4 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, cluster=yogurtte-k3s-prod, container=auth-service, instance=10.42.1.37:8090, job=auth-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=auth-service-855c75679d-wf6c7}` | 9 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, cluster=yogurtte-k3s-prod, container=chat-service, instance=10.42.1.31:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-857c54dd97-s5fbl}` | 55 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, cluster=yogurtte-k3s-prod, container=content-service, instance=10.42.1.35:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-6c5fff897-qnxk6}` | 55 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, cluster=yogurtte-k3s-prod, container=content-service, instance=10.42.3.41:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-6c5fff897-scw7k}` | 55 | 1 | 1 | 1 | — |
| `mongodb_up` | `{__name__=mongodb_up, cluster=yogurtte-k3s-prod, instance=infra-server, job=mongodb, k8s_cluster_name=yogurtte-k3s-prod}` | 55 | 1 | 1 | 1 | — |
| `kafka_brokers` | `{__name__=kafka_brokers, cluster=yogurtte-k3s-prod, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod}` | 55 | 1 | 1 | 1 | — |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=chat-service-fcm-tokens, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=0, topic=user.fcm-tokens}` | 55 | 0 | 0 | 0 | **2026-07-29T01:25:00Z ~ 2026-07-29T01:38:30Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=chat-service-fcm-tokens, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=1, topic=user.fcm-tokens}` | 55 | 0 | 0 | 0 | **2026-07-29T01:25:00Z ~ 2026-07-29T01:38:30Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=chat-service-fcm-tokens, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=2, topic=user.fcm-tokens}` | 55 | 0 | 0 | 0 | **2026-07-29T01:25:00Z ~ 2026-07-29T01:38:30Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=chat-service-notification-settings, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=0, topic=user.notification-settings}` | 55 | 0 | 0 | 0 | **2026-07-29T01:25:00Z ~ 2026-07-29T01:38:30Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=chat-service-notification-settings, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=1, topic=user.notification-settings}` | 55 | 0 | 0 | 0 | **2026-07-29T01:25:00Z ~ 2026-07-29T01:38:30Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=chat-service-notification-settings, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=2, topic=user.notification-settings}` | 55 | 0 | 0 | 0 | **2026-07-29T01:25:00Z ~ 2026-07-29T01:38:30Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=db-writer, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=0, topic=chat.messages}` | 55 | 0 | 0 | 0 | **2026-07-29T01:25:00Z ~ 2026-07-29T01:38:30Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=db-writer, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=1, topic=chat.messages}` | 55 | 0 | 0 | 0 | **2026-07-29T01:25:00Z ~ 2026-07-29T01:38:30Z** |
| `websocket_active_users` | `{__name__=websocket_active_users, application=chat-service, cluster=yogurtte-k3s-prod, container=chat-service, instance=10.42.1.31:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-857c54dd97-s5fbl}` | 55 | 0 | 0 | 0 | **2026-07-29T01:25:00Z ~ 2026-07-29T01:38:30Z** |

값이 0이던 구간은 굵게 표시했다 — 프로세스가 사라져 시계열이 꺾인 것이 유일한 신호인 장애가 있다.

