# T2-B 콘텐츠 폭주 테스트 - CPU 병목을 자원 최적화로 풀어낸 과정

## 1. 한 줄 요약

500 VU 콘텐츠 읽기 부하에서 발생한 CPU 병목을 단순한 스케일아웃으로 해결하지 않고,

**애플리케이션 CPU 최적화 → JVM 메모리 축소 → CPU limit 검증 및 제거 → 노드 자원 분석 → 인스턴스 타입 검증 → Spot 전용 노드 분리 → Replica 확장**

순서로 단계적으로 최적화했다.

그 결과 서버 처리량을 **176.7 rps → 314.3 rps** 까지 높였고, 중간 단계에서는 **195.6 → 241.2 rps(+23%)** 의 성능 향상과 동시에 Spot 노드로 비용을 절감했다.

최종적으로 CPU가 아니라 **RDS Connection Pool이 다음 병목(59/60)** 으로 이동하는 것까지 확인했다.

---

## 2. 테스트 배경

콘텐츠 서비스에 핫 콘텐츠 읽기 요청이 집중되는 상황을 가정했다.

### 테스트 조건

| 항목 | 값 |
|---|---|
| 최대 부하 | 500 VU |
| 테스트 시간 | 6분 |
| 요청 경로 | `/feeds/scroll`, `/feeds/hot`, `/feeds/{id}` |
| 트래픽 경로 | CloudFront → Ingress → content-service |
| 초기 Replica | 2 |
| 초기 CPU limit | 1.5 core / pod |
| 초기 JVM Xmx | 1024Mi |
| DB | RDS |
| Connection Pool | HikariCP |

초기 테스트에서는 요청 실패는 발생하지 않았지만, 두 content replica가 CPU limit에 붙으면서 응답시간이 크게 증가했다.

---

## 3. 최초 결과 - CPU가 첫 번째 병목이었다

초기 baseline:

| 지표 | 결과 |
|---|---:|
| 서버 처리량 | **176.7 rps** |
| 요청 실패율 | **0%** |
| content CPU | **1.49 / 1.49 core** |
| CPU limit | 1.5 core |
| CFS throttling | **76.1% / 95.5%** |
| p99 | **4.5~4.9s** |
| Hikari pending | 0~1 |
| GC | 정상 |

처리량은 유지됐지만 CPU가 limit에 붙어 있었다.

특히 Hikari pending이 거의 없었고 GC도 문제가 없었기 때문에,

> "DB가 느려서 발생한 문제"

또는

> "JVM GC가 CPU를 잡아먹는 문제"

보다는 **애플리케이션 CPU 포화가 먼저 발생한 상황**으로 판단했다.

---

## 4. 먼저 서버를 늘리지 않고 애플리케이션 CPU부터 줄였다

CPU가 부족하다고 바로 Replica를 늘리면 같은 요청을 처리하기 위해 CPU를 추가로 구매하는 구조가 된다.

따라서 먼저 요청 하나를 처리하는 데 필요한 CPU 자체를 줄였다.

### 4.1 readOnly 적용

Feed 조회 경로에 `readOnly`를 적용하고 조회수 증가 로직을 별도 UPDATE로 분리했다.

기존에는 조회 요청에서 불필요한 JPA dirty checking 및 변경 감지가 발생할 수 있는 구조였다.

변경 후:

- 조회 트랜잭션 → 읽기 전용
- 조회수 변경 → 별도 단문 UPDATE

### 결과

| 구성 | 요청당 CPU |
|---|---:|
| 변경 전 | **20.9ms** |
| readOnly + 조회수 분리 | **18.6ms** |
| 웜업 후 | **15.6~16.5ms** |

서버 처리량도 **180.8 → 195.6 rps** 로 증가했다.

즉, Replica를 추가하기 전에 **요청 자체의 CPU 단가를 먼저 낮췄다.**

---

## 5. JVM Heap도 실제 사용량을 기준으로 줄였다

CPU 문제를 해결하면서 JVM 메모리 할당도 함께 검증했다.

초기 설정은:

```text
Xmx = 1024Mi
```

였지만 실제 Heap 사용량은 이에 크게 미치지 못했다.

실측 결과:

| 구성 | Heap 사용량 | 결과 |
|---|---:|---|
| Xmx 1024Mi | 최대 약 596Mi | 기준 |
| Xmx 768Mi | 약 361Mi | 성능 유지 |
| Xmx 512Mi | 약 208~226Mi | 성능 유지 |
| Xmx 440Mi | 성능 저하 없음 | 추가 축소는 실익 부족 |

512Mi 설정에서도 GC가 처리량을 악화시킬 수준으로 증가하지 않았다.

### Xmx 512 검증

| 지표 | Xmx 768 | Xmx 512 |
|---|---:|---:|
| 서버 rps | 180.8 | 181.6 |
| 요청당 CPU | 20.9ms | 20.5ms |
| GC max | 0.10s | 0.45s |
| Working Set | 666Mi | 633Mi |

GC pause가 일부 증가했지만 처리량에는 유의미한 악화가 없었다.

따라서 실제 사용량보다 과하게 잡혀 있던 JVM Heap을 512Mi까지 축소했다.

---

## 6. CPU limit을 올리면 빨라질 것이라는 가설을 세웠다

초기에는 CPU limit이 1.5 core였기 때문에 다음과 같은 가설을 세웠다.

> CPU limit을 1.5 → 1.7 core로 높이면 파드가 더 많은 CPU를 사용할 수 있고 처리량이 증가할 것이다.

그러나 결과는 예상과 반대였다.

### CPU limit 1.5 → 1.7

| 지표 | 1.5 | 1.7 |
|---|---:|---:|
| 서버 rps | 176.7 | 148.8 |
| 요청당 CPU | 16.9ms | 22.6ms |
| p99 | 4.5~4.9s | 6.9~10s |
| 실패율 | 0% | 0.36% |
| 파드 CPU | 1.49 | 1.68 |
| CFS throttling | 76~95% | 83~96% |

CPU를 더 줬는데 오히려 처리량이 약 16% 감소했다.

---

## 7. 원인은 CPU 부족 자체가 아니라 CFS throttling이었다

limit을 1.7로 올렸을 때도 CPU limit에 도달하면 CFS quota가 적용됐다.

결국 파드는 CPU를 더 사용할 수 있게 되었지만,

```text
CPU quota 소진
      ↓
CFS throttling
      ↓
실행 중단
      ↓
요청 대기열 증가
      ↓
tail latency 증가
```

가 반복됐다.

특히 CFS throttling 비율이 약 95% 수준으로 관측됐다.

따라서 단순히 CPU limit을 높이는 것은 해결책이 아니었다.

---

## 8. CPU limit을 제거해 가설을 다시 검증했다

다음 실험에서는 content-service의 CPU limit만 제거했다.

requests는 유지해 다른 서비스와의 경합 시 CPU share를 확보하도록 했다.

결과:

| 지표 | limit 1.7 | limit 제거 |
|---|---:|---:|
| 서버 rps | 148.8 | 180.8 |
| 중앙값 | 373ms | 229ms |
| p95 | 6.05s | 4.87s |
| CFS throttling | 95% 수준 | 0 |
| 파드 CPU | 1.68 | 1.93 / 1.85 |
| 노드 CPU | 99.6% | 100% |

CPU limit을 제거하자 CFS throttling이 사라지고 처리량이 baseline 이상으로 회복됐다.

따라서 이 실험을 통해 다음을 확인했다.

> content-service는 CPU limit으로 인한 throttling보다 노드의 실제 CPU capacity까지 사용하는 것이 더 효율적이었다.

최종적으로 content-service는 CPU limit 없이 운용하고 requests를 통해 CPU 경합 시 우선순위를 확보하는 방향으로 결정했다.

---

## 9. 그런데 새로운 문제가 생겼다 - 노드 자체가 포화됐다

CPU limit을 제거하자 파드가 CPU를 더 사용할 수 있게 되었지만, 이번에는 노드 자체가 100%에 가까워졌다.

소급 관측 결과:

| 노드 | 스펙 | Peak CPU |
|---|---|---:|
| worker1 | t3.medium / 2 CPU | 1.957 core / 97.9% |
| worker2 | t3.small / 2 CPU | 1.495 core / 74.8% |

즉,

```text
worker1
2 CPU
├─ content
├─ auth
├─ chat
└─ 기타 시스템
      ↓
약 98% 사용
```

반면 worker2에는 CPU 여유가 있었다.

---

## 10. 파드와 노드의 병목을 분리해서 확인했다

같은 CPU 포화처럼 보여도 실제 원인은 달랐다.

### worker2

content 파드:

```text
CPU ≈ 1.494 core
limit = 1.5 core
CFS throttling = 95.5%
```

하지만 노드는:

```text
1.495 / 2.0 core
≈ 74.8%
```

즉 노드에 약 0.5 core가 남아 있었다.

따라서 worker2에서는:

> 노드 스펙이 아니라 파드 CPU limit이 병목

이었다.

### worker1

반면:

```text
node CPU ≈ 97.9%
```

였기 때문에 limit을 올려도 사용할 CPU가 부족했다.

즉:

> worker1에서는 실제 노드 capacity가 병목

이었다.

이 차이를 구분한 것이 이후 노드 구성 변경의 근거가 됐다.

---

## 11. 작은 인스턴스로 더 줄일 수 있는지 검증했다

여기서 비용을 더 낮출 수 있는지 확인하기 위해 t3.micro 계열까지 검증했다.

단순히 인스턴스 가격만 보고 판단하지 않고 다음을 같이 측정했다.

- 파드 CPU
- 노드 CPU
- Working Set
- JVM Heap
- GC
- 처리량

### Micro 실험

결과:

| 지표 | Spot Small | Micro |
|---|---:|---:|
| 처리량 | 241.2 rps | 192.6 rps |
| content 파드 CPU | 약 1.9 core | 0.95 core |
| 노드 CPU | 약 2.0 core | 약 1.9 core |
| Working Set | 약 630Mi | 529Mi |
| 결과 | 채택 | 기각 |

Micro에서는 메모리 사용량 자체는 약 529Mi 수준으로 관측돼 메모리 용량만 보면 동작 가능했다.

하지만 중요한 것은 메모리만 통과했다고 해당 인스턴스가 적합한 것은 아니었다는 점이다.

content 파드 자체 CPU는 약 0.95 core로 낮아졌지만 노드 전체 CPU는 약 1.9 core까지 올라갔다.

결과적으로 처리량이:

```text
241.2 rps
    ↓
192.6 rps
```

로 약 20% 감소했다.

따라서 micro는:

> 메모리 용량은 만족했지만 CPU 효율이 좋지 않아 전체 처리량 대비 경제성이 떨어지는 것으로 판단하여 기각했다.

단, "메모리가 부족해서 CPU가 증가했다"고 단정하지 않았다.

GC 등 추가 관측에서 메모리 부족이 직접적인 CPU 증가 원인이라고 확정할 근거가 없었기 때문이다.

---

## 12. 작은 노드 대신 Workload를 분리했다

Micro를 무작정 사용하는 대신 문제의 원인이었던 노드 경합을 제거하기로 했다.

기존:

```text
worker1
├─ auth
├─ chat
├─ content
└─ observability

worker2
└─ content
```

변경:

```text
core worker
├─ auth
├─ chat
└─ 기타 서비스

content 전용 노드
└─ content
```

그리고 content 전용 노드에는 Spot 인스턴스를 적용했다.

---

## 13. Spot 전용 노드 이주 결과

기존 readOnly 코드 최적화 상태:

```text
195.6 rps
```

content를 전용 Spot 노드로 이주:

```text
241.2 rps
```

### 결과

**+23% 처리량 증가**

동시에 auth/chat/observability와 CPU를 공유하지 않게 되면서 노드 경합도 제거됐다.

중요한 점은 단순히:

> "더 큰 인스턴스로 교체했다"

가 아니라,

> 현재 workload의 CPU 사용 패턴을 측정한 뒤 CPU를 많이 사용하는 content를 별도 노드로 분리하고, 해당 workload에 Spot을 적용해 성능과 비용을 동시에 최적화했다.

는 것이다.

---

## 14. Replica를 늘리기 전에 DB Connection 한계를 확인했다

content replica를 늘리면 CPU 처리량은 증가할 수 있지만 DB Connection도 함께 증가한다.

따라서 RDS를 먼저 확인했다.

```text
max_connections = 60
Max_used = 62
```

이미 Connection 상한에 접촉한 이력이 있었다.

기존 Hikari Pool 20을 그대로 유지하면 replica 증가 시:

```text
20 × 3
= 60
```

content만으로 RDS Connection을 모두 사용하게 된다.

따라서 Pool을:

```text
20 → 12
```

로 줄인 뒤 Replica를 3개로 늘렸다.

---

## 15. Replica 3 검증

최종 구성:

```text
content replica = 3
Hikari pool = 12 / pod
CPU limit = 없음
Xmx = 512Mi
readOnly + 조회수 분리
content 전용 Spot 노드
```

결과:

| 지표 | 결과 |
|---|---:|
| 서버 처리량 | 314.3 rps |
| 중앙값 | 135ms |
| p95 | 3.98s |
| 실패율 | 0.15% |
| content CPU | 1.89 / 1.92 / 1.68 core |
| 노드 CPU | 약 2.0 / 2.0 / 2.0 core |
| Hikari active | 11~12 / pod |
| Hikari pending | 최대 25 |
| Hikari acquire max | 0.99s |
| RDS Threads_connected | 59 / 60 |

---

## 16. Replica 확장의 새로운 한계 - RDS Connection

Replica 3까지 확장하면서 CPU 병목은 충분히 분산됐다.

하지만 이번에는 RDS Connection이 거의 한계에 도달했다.

```text
RDS
max_connections = 60
Threads_connected = 59
```

Connection 사용량을 분석하면:

```text
content
12 × 3 = 36

auth
≈ 10

chat
≈ 10

기타 관리 connection
≈ 수 개
----------------
≈ 59
```

즉 부하가 없어도 Connection Pool의 minimum idle 때문에 상당한 Connection이 상주한다.

따라서 Replica 4를 무작정 추가하면 DB Connection 상한을 초과할 가능성이 높다.

이 시점에서 병목은:

```text
CPU
 ↓
노드 CPU
 ↓
RDS Connection
```

으로 이동했다.

---

## 17. 전체 성능 개선 Journey

| 단계 | 구성 | 서버 rps |
|---|---|---:|
| Baseline | Replica 2 / CPU limit 1.5 | 176.7 |
| CPU limit 1.7 | limit 상향 | 148.8 |
| CPU limit 제거 | CFS throttling 제거 | 180.8 |
| Xmx 512 | JVM 메모리 축소 | 181.6 |
| 코드 개선 | readOnly + 조회수 분리 | 195.6 |
| Spot 전용 노드 | 노드 경합 제거 | 241.2 |
| Replica 3 + Pool 12 | 수평 확장 | 314.3 |

### 주요 구간

```text
195.6
  │
  │ +23%
  ▼
241.2
  │
  │ +30%
  ▼
314.3 rps
```

초기 baseline 대비 최종 서버 처리량은:

```text
176.7 → 314.3 rps
```

로 약 78% 증가했다.

코드 개선 이후의 새로운 baseline을 기준으로 보면:

```text
195.6 → 314.3 rps
```

로 약 61% 증가했다.

---

## 18. 최종 자원 구성

```text
                    CloudFront
                         │
                      Ingress
                         │
              ┌──────────┴──────────┐
              │                     │
        content × 3              기타 서비스
              │
      ┌───────┼───────┐
      │       │       │
   Spot-1  Spot-2  Spot-3
      │       │       │
    1 pod    1 pod    1 pod
```

각 content pod:

```text
├─ CPU limit : 없음
├─ CPU request : 700m
├─ Xms : 256Mi
├─ Xmx : 512Mi
└─ Hikari : 12
```

---

## 19. 왜 CPU limit을 제거했는가?

CPU limit 제거는 "limit이 필요 없다"는 일반적인 결론이 아니다.

이번 서비스에서 실제로:

1. CPU limit 1.5 → 1.7 실험
2. CFS throttling 약 95%
3. 처리량 176.7 → 148.8 rps
4. limit 제거
5. CFS throttling 소멸
6. 처리량 180.8 rps 회복

이라는 실험 결과가 있었기 때문에 content-service에 한정해 제거했다.

다른 서비스의 limit은 동일한 문제가 관측되지 않았기 때문에 유지했다.

즉,

> 전체 클러스터의 정책을 일괄 변경한 것이 아니라 workload별 측정 결과에 따라 CPU 정책을 다르게 적용했다.

---

## 20. 왜 Xmx를 512Mi로 정했는가?

단순히 "메모리가 적으니까 512로 줄였다"가 아니다.

실제 사용량을 측정하고 단계적으로 축소했다.

```text
Xmx 1024
   ↓
실사용 최대 약 596Mi
   ↓
Xmx 768 검증
   ↓
성능 영향 없음
   ↓
Xmx 512 검증
   ↓
성능 영향 없음
   ↓
Xmx 440 검증
   ↓
추가적인 실익 부족
   ↓
Xmx 512 채택
```

512에서는:

- Heap 사용량 약 199~226Mi
- Working Set 약 639~665Mi
- GC pause 약 0.28~0.41s
- 처리량 영향 없음

을 확인했다.

따라서 512Mi를 실측 기반의 안전한 상한으로 선택했다.

---

## 21. 왜 micro를 선택하지 않았는가?

Micro는 단순 가격만 보면 매력적일 수 있다.

하지만 실제 workload에서는:

```text
Spot Small
241.2 rps

Micro
192.6 rps
```

로 처리량이 약 20% 감소했다.

또한 micro에서는:

```text
content pod CPU ≈ 0.95 core
node CPU ≈ 1.9 core
```

로 나타났다.

따라서 단순히 메모리 사용량이 529Mi라고 해서 micro가 적합하다고 판단하지 않았다.

CPU + 메모리 + 시스템 오버헤드 + 처리량을 함께 평가한 결과 기각했다.

---

## 22. 왜 Replica를 더 늘리지 않았는가?

CPU만 보면 Replica를 더 늘릴 수 있다.

하지만 Replica가 늘면:

```text
Replica 증가
    ↓
Connection Pool 증가
    ↓
RDS Connection 증가
```

가 발생한다.

현재:

```text
max_connections = 60
Threads_connected = 59
```

이므로 단순 Replica 증가로 해결할 수 있는 단계가 아니다.

따라서 다음 최적화 방향은 Scale-out이 아니라:

> DB에 전달되는 요청 자체를 줄이는 캐시

로 변경했다.

---

## 23. 기각한 가설

### CPU limit 상향

**가설**
CPU limit을 높이면 처리량이 증가할 것이다.

**결과**
176.7 → 148.8 rps

**판정**
기각. CFS throttling이 오히려 tail latency를 악화시켰다.

---

### JVM Heap 축소

**가설**
Heap을 줄이면 GC 때문에 성능이 떨어질 것이다.

**결과**
Xmx 768 → 512에서도 처리량 변화가 없었다.

**판정**
512Mi 채택.

---

### Micro 인스턴스

**가설**
메모리 요구량을 줄였으므로 더 작은 인스턴스로 비용을 줄일 수 있을 것이다.

**결과**
241.2 → 192.6 rps

**판정**
기각. 메모리 용량은 통과했지만 CPU 효율과 처리량 측면에서 불리했다.

---

### Replica 무한 확장

**가설**
Replica를 늘리면 CPU 처리량이 계속 증가할 것이다.

**결과**
Replica 3까지는 +30% 증가했지만 RDS가 59 / 60 까지 도달했다.

**판정**
Scale-out의 다음 한계가 DB Connection임을 확인.

---

## 24. 이 실험에서 가장 중요한 엔지니어링 포인트

이 테스트의 핵심은 특정 설정값을 찾아낸 것이 아니다.

**병목이 이동할 때마다 원인을 다시 측정하고 다음 계층을 최적화했다는 것이다.**

```text
애플리케이션 CPU
       ↓
JVM Resource
       ↓
Kubernetes CPU 정책
       ↓
Node Capacity
       ↓
Instance Type
       ↓
Node Topology
       ↓
Replica
       ↓
DB Connection
```

각 단계에서 "더 많은 자원을 주는 것"을 먼저 선택하지 않았다.

가능한 경우 먼저:

- CPU 단가 감소
- 메모리 요구량 감소
- 불필요한 throttling 제거
- 노드 경합 제거
- 저비용 Spot 활용

을 수행한 뒤 마지막으로 Scale-out했다.

---

## 25. 최종 결과

### 성능

```text
176.7 rps
      ↓
314.3 rps
약 +78%
```

코드 최적화 이후 기준:

```text
195.6 rps
      ↓
314.3 rps
약 +61%
```

### CPU

요청당 CPU:

```text
20.9ms
  ↓
18.6ms
  ↓
최종 약 16.2ms
```

### JVM

Xmx:

```text
1024Mi
  ↓
768Mi
  ↓
512Mi
```

실측으로 성능 저하 없이 축소했다.

### 인프라

```text
공유 노드
  ↓
content 전용 노드
  ↓
Spot 적용
  ↓
replica 3
```

성능과 비용을 동시에 최적화했다.

### 최종 병목

```text
CPU 병목
   ↓
노드 CPU 병목
   ↓
RDS Connection 병목
   ↓
다음 해결책 = 캐시
```

---

## 26. 결론

이번 테스트에서 단순히 "CPU가 부족해서 서버를 늘렸다"는 방식으로 접근하지 않았다.

먼저 요청당 CPU 사용량을 줄이고, 실제 JVM 메모리 사용량을 측정해 Xmx를 축소했다. 이후 CPU limit 상향이 오히려 CFS throttling으로 성능을 악화시키는 것을 실험으로 확인하고 limit을 제거했다.

그 결과 노드 자체가 새로운 병목으로 드러났고, 노드별 CPU 사용량을 비교해 workload를 분리했다. 더 작은 micro 인스턴스도 직접 검증했지만 처리량 대비 CPU 효율이 떨어지는 것을 확인해 기각했다.

이후 content를 전용 Spot 노드로 이동해 노드 경합을 제거하면서 비용도 절감했고, DB Connection 여유를 고려해 Hikari Pool을 조정한 뒤 Replica를 3개로 확장했다.

그 결과 최종적으로:

```text
176.7 → 314.3 rps
```

까지 처리량을 높였고, 최종적으로 CPU가 아니라 RDS Connection이 새로운 시스템 한계가 되는 것까지 확인했다.

따라서 다음 최적화는 단순한 Scale-out이 아니라 캐시를 통해 DB 요청 수 자체를 줄이는 방향으로 전환한다.