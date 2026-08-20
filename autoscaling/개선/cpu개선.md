# CPU 개선 - "2 vCPU가 작아서"가 아니었다

> 부하테스트 11건 중 6건에서 벽은 CPU였다. 그런데 **같은 "CPU 99%"의 원인이 셋으로 갈리고,
> 서비스마다 처방이 정반대다.** 이 문서는 그 분해와 개선 순서를 담는다.
>
> 근거: [T2-B 콘텐츠 폭주](../loadtest/results/04-콘텐츠폭주/README.md) 소급 관측(2026-08-19) ·
> [혼합 한계](../loadtest/results/10-혼합한계/README.md) · [Spike](../loadtest/results/13-순간폭주/README.md) ·
> 결함 [NF-13](../../docs/findings/nf-13-hikari-pool-churn-under-cpu-throttle.md)

---

## 상황

| 항목 | 내용 |
|---|---|
| 부하 | 피드 읽기 500 VU · 6분 · 62,441 요청 (클라 173 rps · 서버 176.7 rps) |
| 증상 | 실패 0%인데 p99가 4.5~4.9초. 전 요청이 균등하게 느려지는 우아한 포화 |
| 첫 판정 | "Case A - 앱 CPU 단독 포화 → HPA 1순위" |
| 문제 | 그 시험은 **노드 지표를 안 남겼다.** 노드가 포화한 건지 파드 limit에 막힌 건지 구분 불가 |
| 결과 | 소급 관측으로 분해했고, **첫 판정의 처방이 실행 불가**임이 드러났다 |

## 무엇이 일어났나

**CPU 99%를 보고 처음 내린 결론은 "노드가 2 vCPU라 작다"였다.** t3.medium·t3.small에 2 vCPU씩이니
그럴싸했다. 하지만 그 결론이 맞으면 처방은 "EC2를 키운다"밖에 없고, 그건 돈으로 때운 것이 된다.

그래서 Mimir 히스토리로 창(23:09~23:16 KST)을 소급 조회해 **노드와 파드를 분리했다.**
그러자 두 노드에서 벽이 서로 다르게 나왔다.

| 노드 | 역할 | peak CPU | capacity | 벽 |
|---|---|---:|---:|---|
| worker1 (t3.medium) | auth·chat·content | 1.957 | 2.0 (**97.9%**) | **노드 capacity** - 여유 없음 |
| worker2 (t3.small) | content | 1.495 | 2.0 (**74.8%**) | **파드 limit** - 노드에 0.5코어 남았는데 막혔다 |
| master | control-plane | 0.071 | 2.0 | - |
| edge (proxy) | ingress | 0.298 | 2.0 | - |

파드를 보면 더 분명하다.

| 파드 | 노드 | peak | limit | requests | CFS 스로틀 |
|---|---|---:|---:|---:|---:|
| content sp24n | worker1 | 1.489 | 1.5 | 0.3 | 76.1% |
| content v2pw9 | worker2 | 1.494 | 1.5 | 0.3 | **95.5%** |
| auth | worker1 | 0.005 | 0.5 | 0.15 | - |
| chat | worker1 | 0.051 | 0.3 | 0.15 | - |

**worker2는 스펙 문제가 아니다.** 노드에 25%가 남았는데 파드가 자기 limit에 붙어 스로틀당했다.
반면 worker1은 노드가 이미 97.9%라 limit을 올려도 줄 CPU가 없다. **하나의 "CPU 문제"가 아니었다.**

같이 기각된 것도 있다.

- **GC 아님** - content GC pause 0.055 s/s(2파드 합) = 파드당 2.8%
- **이웃 서비스 간섭 아님** - 같은 창에서 auth 0.005 · chat 0.051 코어로 사실상 idle

그리고 남는 숫자가 하나 있다. **content가 176.7 rps에 2.98 코어를 태웠다 → 요청당 16.9ms CPU.**
캐시 없이 DB로 가는 단순 읽기치고 비싸다. 이게 진짜 개선 여지다.

**마지막으로, 첫 판정의 처방이 실행 불가임이 드러났다.** content limit 1.5 × 2 replica = 3.0인데
워커 총 capacity는 4.0이고 시스템이 0.4~0.7을 쓴다. **3번째 replica가 앉을 CPU가 물리적으로 없다.**
게다가 requests 0.3 / limit 1.5는 **5배 오버커밋**이라 스케줄러는 태연히 배치하고 실제로는
셋이 서로 스로틀된다. HPA를 먼저 붙이면 이 함정에 걸린다.

---

## CPU를 세 층으로 분해하면

| 층 | 실측 | 고치면 얻는 것 | 비용 |
|---|---|---|---|
| **① 코드** - 요청당 16.9ms | 176.7 rps에 2.98 코어 | 8ms로 반감 시 같은 코어로 **~350 rps (2배)** | 개발 시간 |
| **② 설정** - 파드 limit·requests | content 1.5 / auth 0.5 / chat 0.3 · 오버커밋 5배 | worker2의 여유 0.5코어 회수 → **+17%** | 없음 (매니페스트) |
| **③ 스펙** - 노드 2 vCPU | worker1 97.9% | 약 2배 | **돈.** 설계가 금지한 답 |

**①이 압도적이고, ②는 공짜다. ③은 마지막이다.**

단 ①에는 단서가 붙는다. **쉬운 범인은 이미 없다** - [NF-11](../../docs/findings/nf-11-feed-scroll-n-plus-one.md)의
N+1 수정(`default_batch_fetch_size: 100`)이 `application.yml`에 있고 prod 프로필이 그 블록을
덮지 않는다(확인 완료). 즉 **16.9ms는 N+1을 고친 뒤의 값**이다. 남은 비용이 어디서 오는지는
직렬화 · 잔여 LAZY 접근 · 페이로드 크기 중 무엇인지 **미측정**이고, 짐작으로 잡을 수 없다.

## 서비스마다 처방이 정반대다

같은 "CPU 포화"에 답이 셋이다. 이 표가 실제 결론이다.

| 서비스 | CPU를 쓰는 성격 | 실측 근거 | 처방 |
|---|---|---|---|
| **auth** | BCrypt - **의도된 비용**. 줄이면 보안이 약해진다 | T2-A CPU 100% · 혼합에서 limit 500m에 정확히 붙음(노드는 62%) | 코드 최적화 아님. **limit 상향 + replica** |
| **content** | 읽기인데 요청당 16.9ms - 비싸다 | T2-B 2.98코어 / 176.7 rps · DB는 여유(pending 0) | **코드·쿼리 먼저**, replica는 그 다음 |
| **chat** | 메시지 팬아웃 | WS-B CPU 0.99(297~299m / limit 300m) · 연결 1,000개는 CPU 31%로 여유 | 연결 구조(Redis Pub/Sub 등). 연결 수는 병목 아님 |

## CPU를 고쳐도 안 사라지는 것 둘

- **핫키 락** - 분산 쓰기와의 대조군에서 처리량 3.1배 차이. 부하량이 아니라 락이다
- **Kafka 단일 파티션** - 파티션 하나 = 컨슈머 스레드 하나. CPU를 더 줘도 그 파티션 소비 속도는 안 오른다

CPU 트랙과 별개로 남는 항목이다. 여기에 CPU 예산을 쓰면 헛돈이다.

---

## 바꿀 것

변경군을 섞지 않기 위해 **측정 → 설정 → 코드 → 구조 → 스케일링** 순으로 쪼갰다.

| ID | 층 | 변경 | 근거 | 선행 |
|---|---|---|---|---|
| **CPU-1** | 측정 | 요청당 16.9ms의 정체 계측 - `/feeds/scroll` 1회의 쿼리 수 + CPU 프로파일 | ①이 가장 크지만 **줄어드는 비용인지 고정 비용인지 모른다** | 없음 (앱 변경 아님) |
| **CPU-2** | 설정 | requests/limits 재설정 - 오버커밋 5배 해소, worker2 여유 0.5코어 회수 | requests 0.3 vs 실사용 1.49 · worker2 노드 74.8% | 없음 |
| **CPU-3** | 코드 | 요청당 CPU 절감 (대상은 CPU-1 결과가 고른다) | 요청당 16.9ms · N+1은 이미 수정됨 | CPU-1 |
| **CPU-4** | 설정 | 커넥션 풀 고정 크기화 `minimum-idle = maximum-pool-size` | [NF-13](../../docs/findings/nf-13-hikari-pool-churn-under-cpu-throttle.md) - 스로틀 중 커넥션 847회 교체, 생성 1건 100ms | CPU-2 (근본이 스로틀) |
| **CPU-5** | 스케일링 | HPA 적용 | 지금은 **3번째 replica 자리가 없다** | CPU-2 · [README Phase 3-1 JVM 다이어트](../README.md) |

**CPU-1의 프로파일 대상 갱신 (2026-08-20, 실트래픽 확인).** 실서비스 최다 호출은 대시보드
화면이 한 번에 부르는 content 3종 — `/battles/hot?size=7` · `/products?status=APPROVED&size=20` ·
`/feeds/hot?size=6` (전부 hotScore/popularityScore 정렬). 부하 시나리오(B·journey)는 feeds
중심이라 이 축이 **과소 대표**돼 있다. 특기: 셋 다 **사용자 무관 동일 응답**(정렬 기준·페이지
고정, 스코어는 스케줄러가 갱신)이라 캐시 적중률이 구조적으로 100%에 가깝다 —
설계 3-4 Cache 효과 실험의 1순위 대상이고, CPU-1 프로파일링도 `/feeds/scroll`보다 이 3종이 먼저다.

**CPU-5를 먼저 하면 안 된다.** HPA는 자리가 있을 때만 동작하고, 지금은 없다.
Pending 이벤트를 증거물로 잡는 것이 목적이라면(README Phase 3-4) 그건 별개 실험으로
의도적으로 돌리는 것이며, 개선이 아니다.

### 신호 도달 확인 - 쓸 지표가 실제로 존재하는가

수정안을 세우기 전에 **관측 가능성부터 확인했다.** 아래는 T2-B 창에서 실제로 값이 나온 쿼리다.

| 확인할 것 | 쿼리 | T2-B 실측 |
|---|---|---|
| 노드 CPU (kubelet) | `max_over_time((sum by(node)(rate(node_cpu_usage_seconds_total[5m])))[7m:1m])` | worker1 1.957 · worker2 1.495 |
| 파드 CPU | 같은 형태로 `container_cpu_usage_seconds_total` | 1.489 / 1.494 |
| **스로틀** (limit 판정의 핵심) | `rate(container_cpu_cfs_throttled_periods_total[5m]) / rate(container_cpu_cfs_periods_total[5m])` | 76.1% / 95.5% |
| limit·requests | `kube_pod_container_resource_limits{resource="cpu"}` 및 `_requests` | 1.5 / 0.3 |
| 처리량 | `sum(rate(http_server_requests_seconds_count{application="content-service"}[5m]))` | 176.7 rps |
| GC 몫 | `rate(jvm_gc_pause_seconds_sum{application="content-service"}[5m])` | 0.055 s/s |

**주의 두 개.**

1. kubelet resource 메트릭은 스크레이프 간격이 1분이라 `rate[1m]`이 **빈 결과**다. `[5m]`을 써야 한다.
   따라서 값은 5분 평균의 최대이고 순간 정점은 이보다 높을 수 있다.
2. **CPU steal · CPUCreditBalance는 잴 수 없다.** k3s 노드에 node-exporter가 없고(`infra-server`만 있다)
   kubelet 메트릭에는 steal 모드가 없다. t3 크레딧 고갈을 한계로 착각하지 않으려면 **AWS CLI가 필요**하다.
   지금은 이 축이 공백임을 인지한 상태로 진행한다.

### 검증 설계 - 한 번에 하나만 바꾼다

| 순서 | 하는 일 | 비교 대상 | 성공 판정 |
|---|---|---|---|
| 1 | **CPU-1** (계측만, 앱 변경 없음) | - | 요청당 쿼리 수와 CPU 내역이 나온다 |
| 2 | **CPU-2** 적용 후 T2-B 동일 재실행 | T2-B baseline | worker2 파드가 1.5를 넘고 스로틀이 내려간다. 처리량 **+10~20%** |
| 3 | **CPU-3** 적용 후 T2-B 동일 재실행 | 2번 결과 | 요청당 CPU가 내려간 만큼 rps가 오른다 |
| 4 | **CPU-4** 적용 후 T2-B 동일 재실행 | 3번 결과 | `creation_seconds_count` 증가분이 **847 → 20 근처** |
| 5 | **CPU-5** | 4번 결과 | replica 3이 Pending 없이 뜨고 rps가 선형에 가깝게 오른다 |

**부하 조건(500 VU · 6분 · 같은 엔드포인트 3종)을 고정한다.** 경로도 고정해야 한다 -
T2-B는 CloudFront 경유였으므로 재실행도 CloudFront 경유로 해야 비교가 성립한다.

### 반증 조건

| 변경 | 반증되면 |
|---|---|
| CPU-2 (limit 상향) | 스로틀이 내려갔는데 rps가 그대로면 → 벽이 파드 CPU가 아니라 다른 층(스레드·DB·네트워크)이다 |
| CPU-3 (코드 절감) | 쿼리 수·직렬화를 줄였는데 요청당 CPU가 그대로면 → 16.9ms는 프레임워크 고정 비용이고 ①은 개선 여지가 아니다. 그러면 ②·③만 남는다 |
| CPU-4 (고정 풀) | 고정 풀로 바꿨는데 생성률이 유지되면 → 원인이 풀 구성이 아니라 드라이버·RDS 측 커넥션 종료다 |
| 전체 | worker1에서 limit을 올렸는데 노드가 100%로 붙어버리면 → 거기서는 ③(스펙·노드 통합)이 유일한 답이다 |

### 현재 상태 - 어디까지가 완료이고 무엇이 미측정인가

| 항목 | 상태 |
|---|---|
| 노드 vs 파드 limit 분리 | **완료** (T2-B 소급 · 위 표) |
| 요청당 CPU 16.9ms 산출 | **완료** (2.98 코어 / 176.7 rps) |
| GC·이웃 간섭 기각 | **완료** |
| HPA 자리 부족 확인 | **완료** (limit 3.0 vs capacity 4.0 − 시스템 0.4~0.7) |
| **16.9ms의 내역** | **미측정** - CPU-1 |
| **CPU-2 적용** | **완료 (2026-08-20)** - 아래 적용값 표. **전후 재실행 완료 - limit 상향은 반증되고 limit 제거로 귀결** (아래 "limit의 역설" 절) |
| CPU-3~5 적용 | **미적용** |
| CPU steal · t3 크레딧 | ~~미측정~~ **해소 (2026-08-19)** - AWS CLI 연결, 4일 소급 **미소진·unlimited** ([99-소급관측](../loadtest/results/99-소급관측/README.md)) |
| worker1에서 limit 상향의 실효 | **미측정** - 노드가 97.9%라 효과 없을 것으로 예상, 확인 안 됨 |

**적용 시점:** 부하테스트 진단이 끝난 뒤. 지금 고치면 T2-B가 baseline으로 성립하지 않는다
(한 회차 한 변경군 원칙). CPU-1만 앱을 건드리지 않으므로 지금 할 수 있다.

### CPU-2 적용값 (2026-08-20 배포 완료)

부하테스트 1차 종결 다음 날 적용. 검증 재실행(T2-B·mixed·WS-B 동일 조건)은 미실시 - 위 검증 설계 2번이 다음 단계다.

| 서비스 | CPU req | CPU limit | Mem req | Mem limit | 근거 |
|---|---|---|---|---|---|
| auth | 150m → **300m** | 500m → **1500m** | 384Mi → **512Mi** | 640Mi → **896Mi** | 혼합: 노드 62% 여유에서 limit이 벽 · 메모리 정점 617Mi vs 구 limit 640Mi(여유 23Mi) |
| content ×2 | 300m → **700m** | 1500m → **1700m** | 1Gi 유지 | 1536Mi 유지 | 실사용 1.49코어(오버커밋 5배 해소) · worker2 여유 0.5코어 회수 |
| chat | 150m → **300m** | 300m → **1000m** | 512Mi → **640Mi** | 768Mi → **1Gi** | WS-B: 팬아웃·db-writer가 0.3코어에 질식 |

- **배치 고정 동반**: auth·chat에 `nodeSelector: ip-172-31-45-39`(worker1). 적용 중 롤링이 auth를
  worker2(1.9Gi)로 옮겨 메모리 requests 90%가 됐던 사고를 되돌린 것 - 기존 토폴로지가
  스케줄러 우연에 기대고 있었음이 드러났다.
- worker1 requests 합 1300m + 모니터링 ~330m = 1630m/2000m (81%). limits 합 4200m은
  천장(비예약)이라 정상 - 경합 시 배분은 requests 비율 3:7:3.
- 적용 확인: 파드 재기동 후 3서비스 스모크 200 · 노션 서버설정파일 Deployment 절 동기화.

### MEM-1 적용값 (2026-08-20 배포 완료 · CPU-2와 같은 날)

메모리 4일 소급(워킹셋 정점 · JVM 힙 사용 최대)이 근거다. **1회차에서 메모리 기인 실패는 0건**이었고
(GC 몫 2.8%/파드 · OOM 없음), 이것은 병목 해소가 아니라 **과대 할당 회수 + 힙 설정의 명시화**다.
CPU-2와 같은 날 적용이라 다음 재실행의 델타에는 두 변경군이 섞여 있음을 기록해 둔다.

| 서비스 | JVM 힙 | Mem req/limit | 근거 (4일 실측) |
|---|---|---|---|
| content ×2 | -Xms768m -Xmx1024m → **-Xms512m -Xmx768m** | 1Gi/1536Mi → **768Mi/1152Mi** | 힙 사용 최대 596Mi(Xmx의 58%) · 워킹셋 정점 1071Mi. limit은 Xmx의 함수라 힙부터 줄여야 내려감 |
| chat | 미설정(기본 25% of limit ≈256Mi) → **-Xms256m -Xmx512m 명시** | 640Mi/1Gi 유지 | 힙 사용 최대 182Mi vs 기본 캡 256Mi - 여유 28%뿐. 팬아웃 부하에서 GC 압박 위험 |
| auth | (무변경 - 512m 고정 확인) | 512Mi/896Mi 유지 | **죽은 설정 제거**: JAVA_TOOL_OPTIONS(MaxRAMPercentage 70)는 entrypoint의 `${JAVA_OPTS}` -Xmx512m(커맨드라인)에 항상 졌다. 실행 인자 실측으로 확인 후 제거 - 동작 무변경 |

- 확보한 것: content 파드당 limit −384Mi·request −256Mi ×2 → **worker2(1.9Gi)에 여유 복원** -
  CPU-5(HPA)의 "3번째 replica 자리" 전제 작업.
- 검증 대기: T2-B 동일 재실행에서 GC pause 비율(baseline 2.8%)·힙 곡선·p99. GC가 튀면 Xmx 768 반증.
- 적용 확인: 3파드 실행 인자에서 새 힙 플래그 실측(`/proc/<java>/cmdline`) · 스모크 200 ·
  원본은 `deployment.yaml.bak-mem1-20260820` · 노션 동기화 완료.
- 4일 워킹셋 우상향(chat 545→655Mi 등)의 누수 여부는 **soak가 답할 몫**으로 남아 있다.

---

## limit의 역설 - CPU limit을 올렸더니 느려졌고, 제거했더니 빨라졌다 (2026-08-20)

### 상황

| 항목 | 내용 |
|---|---|
| 하려던 것 | CPU-2(limit 상향) 검증 - T2-B 동일 조건 재실행, 예측 "+10~20%" |
| 부하 | 500 VU · 6분 · CloudFront 경유 (하루에 같은 시험 3회, 변수 하나씩) |
| 결과 | 예측 반증 → 원인 소거 → 판별 실험 → **limit 제거가 정답으로 확정** |

**같은 날 T2-B 3연속 실행 (각각 단일 변수):**

| | baseline 08-17 (limit 1.5) | limit 1.7 | **limit 제거** | +힙 512 |
|---|---|---|---|---|
| 처리량 (서버 rps) | 176.7 | **148.8 (−16%)** | **180.8 (+2%)** | 181.6 |
| med | ~2.0s | 373ms | **229ms (9배 개선)** | 264ms |
| p99 | 4.5~4.9s | **6.9~10s** | 5.4~5.7s | 5.1~5.4s |
| 파드 CPU max | 1.49 (limit에 붙음) | 1.68 (limit에 붙음) | **1.93/1.85** | 1.92/1.83 |
| CFS 스로틀 | 95.5% | 95.8% | **0 (지표 소멸)** | 0 |
| GC pause max | 0.20s | 0.14s | 0.10s | 0.45s (처리량 영향 없음) |

### 무엇이 일어났나

1. **limit 1.7 재실행이 예측을 반증했다** - 처리량 −16%, p99 악화, 요청당 CPU 16.9→22.6ms.
2. **가설을 실측으로 소거했다.** 메모리(MEM-1) 기각 - GC max가 오히려 개선. 데이터 드리프트
   기각 - med가 4~5배 빨라짐(쿼리가 무거워졌다면 불가능). 앱 내부 기각 - **서버가 본 최대
   지연은 2.3s인데 클라이언트는 60s를 봤다** → 병목은 앱 도달 전 대기열.
3. **기전: CFS quota는 속도 제한이 아니라 주기적 동결이다.** limit 1700m = 100ms마다 170ms
   예산. 스레드 수십 개가 동시에 돌면 벽시계 ~4ms에 소진 → 나머지 ~96ms **파드 전체 동결**
   (커넥션 문 채로, 요청 반쯤 처리한 채로). 스로틀 95.8% = 주기 100번 중 96번 동결.
   동결 중에도 500 VU가 계속 쏘니 대기열이 눈덩이 → 60s 타임아웃 꼬리.
4. **판별 실험 - limit만 제거**(requests·메모리 limit 유지). 처리량 baseline 초과 회복,
   중앙값 9배 개선. **경합 방어는 limit이 아니라 requests(shares)의 몫**임이 실증됐다.
   limit이 유용한 곳은 멀티테넌트 격리이고, 이 클러스터(단일 서비스·정직한 requests)에서는
   동결 비용만 내고 있었다.

### 그 뒤 - 메모리는 여유인데 CPU가 벽이다 → 같은 비용 토폴로지 재구성

limit 제거 후 남은 그림: **노드 두 대가 진짜 100%**(2.000/1.974코어), p95 ~4.9s는 순수
capacity 부족. 반면 메모리는 계속 여유여서 이어서 다이어트를 3단 적용했다 (각각 T2-B 또는
평시 실측으로 검증):

| | 힙 | Mem req/limit | 근거 실측 |
|---|---|---|---|
| **MEM-2** content | 768→**512m** | 768→**640Mi** / 1152→**896Mi** | 힙 사용 max 361Mi · 500 VU 재실행에서 처리량 동일(181.6 rps)·GC max 0.45s 무해 확인 · 워킹셋 666→633Mi |
| **MEM-3** auth | 512→**384m** (secret의 JAVA_OPTS를 deployment env로 override) | 512→**448Mi** / 896→**768Mi** | 힙 사용 4일 max 227Mi · **폭주(T2-A급) 검증 대기** |
| **MEM-3** chat | 512→**384m** | 640→**512Mi** / 1Gi→**896Mi** | 힙 사용 max 182Mi (단 구 힙캡 192Mi 시절 측정치 - 폭주 검증 대기) |

이 다이어트가 연 것: content 워킹셋 ~633Mi → **t3.micro(실효 ~770Mi)에 들어가는 산수**가
처음으로 성립. CPU는 부족하고 메모리는 남는 워크로드이므로, **같은 비용으로 vCPU를 늘리는
토폴로지 재구성**이 다음 수가 된다:

```
현재:  medium(auth·chat·content₁) + small(content₂)                = $0.078/hr · 4 vCPU
목표:  small(auth·chat) + micro×3(content 전용) + micro(관측 전용)  = $0.078/hr · 10 vCPU
```

- micro×4에 content만 넣는 안은 **관측 스택(alloy ~360Mi)의 자리가 없어** 관측 전용 micro가 필요
- 리스크 셋: content 노드 마진 ~110Mi(데이터 성장에 얇음) · micro 크레딧(지속 고부하 2.7h 후
  surplus 과금 - 평시 저트래픽 전제) · RDS max_connections(풀 20×3 + auth·chat ≈ 75)
- **신규 노드 조인 전 필수**: [네트워크 사건록](네트워크-사건록-vxlan-dns.md)의 체크리스트 -
  과거 신규 워커(241)가 iptables INPUT DROP(원인 미상 - 같은 AMI 의심)으로 크로스노드
  DNS가 유실돼 "DB 연결 실패"로 위장된 장애를 낸 전례가 있다
- **순서: micro 1대 실험**(content 파드 1개 이주 → T2-B + 24h 관찰) **→ 통과 시 전면 전환.**
  auth·chat 분리는 "auth·chat 트래픽이 실재할 때"만 가치가 생김이 확인됨 - content 단독
  부하에서는 지금도 content가 ~3.8코어를 다 쓴다(분리해도 동일)

### 현재 상태 (2026-08-20 밤)

| 항목 | 상태 |
|---|---|
| CPU limit 제거 (content) | **적용·검증 완료** (T2-B 181.6 rps) |
| MEM-2 (content 힙 512·640/896Mi) | **적용·검증 완료** (500 VU) |
| MEM-3 (auth·chat 힙 384·resources 축소) | **적용 - 폭주 검증 대기** (T2-A·WS-B급 재실행 시 GC 확인) |
| 토폴로지 재구성 | **미착수** - micro 1대 실험부터, EC2 생성 대기 |
| 코드 C 1차 (readOnly·조회수 단문 UPDATE 분리) | **적용·검증 완료 (08-21)** - toy-content `7906ef1`. 단가 20.5→18.6ms(웜업 후 ~16ms), 처리량 +7.7%(서버 195.6 rps), p95 4.89→4.53s. **유의미하나 크지 않음** - dirty checking+조회수 트랜잭션 몫이 요청당 ~4ms였다는 실측. 중간 "9.7ms" 판독은 측정 오류(파드 한쪽만 합산)로 정정 |
| 핫리스트 캐시 (코드 C 2차) | **미착수** - 남은 단가 ~16ms의 최대 지분. 실트래픽 최다(대시보드 3종·사용자 무관 응답)라 총합 효과 1순위 |
| 토폴로지 재구성 (EC2 분리) | **착수 결정 (08-21)** - 코드 1차 이득이 제한적이라 CPU capacity 확보 병행. micro 1대 실험부터 |

- 결과 문서: [T2-B 콘텐츠 폭주](../loadtest/results/04-콘텐츠폭주/README.md) (소급 관측 절) ·
  [혼합 한계](../loadtest/results/10-혼합한계/README.md) (auth limit 500m) ·
  [WS-B](../loadtest/results/12-메시지처리량/README.md) (chat 팬아웃)
- 시험 설계: [부하테스트-설계-1.md](../부하테스트-설계-1.md) Phase 0 · §3-1 · §3-2
- 서사: [서사.md](../서사.md) Chapter 2 (HPA와 Decision Gate)
- 로드맵: [README.md](../README.md) Phase 3-1(JVM 다이어트) · 3-2(requests/limits) · 3-3(HPA)
- 결함: [NF-13](../../docs/findings/nf-13-hikari-pool-churn-under-cpu-throttle.md) (커넥션 교체) ·
  [NF-11](../../docs/findings/nf-11-feed-scroll-n-plus-one.md) (N+1 · 수정 완료) ·
  [NF-10](../../docs/findings/nf-10-content-db-connection-held-during-external-call.md)
