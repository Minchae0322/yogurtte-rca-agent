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
| CPU-2~5 적용 | **미적용** (baseline 고정 중) |
| CPU steal · t3 크레딧 | **미측정** - AWS CLI 필요 |
| worker1에서 limit 상향의 실효 | **미측정** - 노드가 97.9%라 효과 없을 것으로 예상, 확인 안 됨 |

**적용 시점:** 부하테스트 진단이 끝난 뒤. 지금 고치면 T2-B가 baseline으로 성립하지 않는다
(한 회차 한 변경군 원칙). CPU-1만 앱을 건드리지 않으므로 지금 할 수 있다.

---

## 참조

- 결과 문서: [T2-B 콘텐츠 폭주](../loadtest/results/04-콘텐츠폭주/README.md) (소급 관측 절) ·
  [혼합 한계](../loadtest/results/10-혼합한계/README.md) (auth limit 500m) ·
  [WS-B](../loadtest/results/12-메시지처리량/README.md) (chat 팬아웃)
- 시험 설계: [부하테스트-설계-1.md](../부하테스트-설계-1.md) Phase 0 · §3-1 · §3-2
- 서사: [서사.md](../서사.md) Chapter 2 (HPA와 Decision Gate)
- 로드맵: [README.md](../README.md) Phase 3-1(JVM 다이어트) · 3-2(requests/limits) · 3-3(HPA)
- 결함: [NF-13](../../docs/findings/nf-13-hikari-pool-churn-under-cpu-throttle.md) (커넥션 교체) ·
  [NF-11](../../docs/findings/nf-11-feed-scroll-n-plus-one.md) (N+1 · 수정 완료) ·
  [NF-10](../../docs/findings/nf-10-content-db-connection-held-during-external-call.md)
