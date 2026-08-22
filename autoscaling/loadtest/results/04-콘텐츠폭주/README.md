# T2-B 콘텐츠 폭주 테스트

> **결론:** 500 VU에서도 요청 실패는 없었지만, 두 content replica의 CPU가 포화하며 p99가 4.5~4.9초까지 악화됐다. HPA의 우선 적용 대상이다.

| 구분 | 내용 |
|---|---|
| 상태 | 실측 완료 · 2026-08-17 |
| 상황 | 핫 콘텐츠에 읽기 요청 집중 |
| 부하 | 최대 500 VU · 6분 · 62,441 요청(173 rps) |
| 대상 | `feeds/scroll` · `feeds/hot` · `feeds/{id}` 혼합 |
| 판정 | **Case A** — 앱 CPU 단독 포화 |

## 핵심 결과

| 항목 | 실측 | 임계값 |
|---|---:|---:|
| http_req_failed | **0.00%** | <1% (통과) |
| GET /feeds/scroll p99 | 4.73s | <800ms |
| GET /feeds/hot p99 | 4.53s | <800ms |
| GET /feeds/{id} p99 | 4.85s | <500ms |
| 중앙값(전체) | 1.93s | - |

## 서버 관측

500 VU 유지 구간에서 레플리카 2개 모두 CPU 0.99 · 풀 active 19/18(상한 부근) ·
**pending 0~1** · 스레드 84.

## 해석과 다음 조치

auth(T2-A)와 무너지는 방식이 다르다. auth는 CPU 포화 + 풀 기아 + 실패 30%로
부서졌지만, content는 **CPU 포화 단독**이고 풀은 버텼으며 실패가 0이다. 같은 "CPU 100%"
라도 실패 없이 전 요청이 균등하게 느려지는 우아한 포화 - 읽기 경로에 락·외부 의존이
없고 레플리카가 2개인 구조 차이가 붕괴 형태 차이로 나타났다.

부수 발견: 피드 조회 본경로는 Redis 캐시 없이 매번 MySQL로 가는데도(코드 확인)
**DB가 아니라 앱 CPU가 먼저 포화했다** (풀 pending 0~1 = 쿼리는 빨랐음). 현재 데이터
규모에서는 "DB가 먼저 무너진다"는 추정이 반증됨 - 데이터가 커지면 달라질 수 있다.

**Case A**로 판정한다. CPU만이 병목이고 DB·풀은 정상이다. 따라서 Chapter 2의 HPA 적용
1순위이며, replica 증가가 처리량 증가로 이어질 것이라는 가설을 재시험으로 검증한다.

원본: [output](2026-08-17-t2b-output.txt) · [summary](2026-08-17-t2b-summary.json)

---

## 소급 관측 (2026-08-19) - "노드 스펙이냐"를 가른 결과

시험 당시 노드 지표를 안 남겨서 **노드 포화인지 파드 limit인지 구분할 수 없는 상태**로
기록돼 있었다. Mimir 히스토리에 `max_over_time`을 적용해 창(23:09~23:16 KST)을 소급 조회했다
(99-소급관측과 같은 방법). 아래 쿼리와 실측치는 그대로 재현 가능하다.

### 노드 (전 노드 capacity 2.0 코어)

```promql
max_over_time( (sum by(node)(rate(node_cpu_usage_seconds_total[5m])))[7m:1m] )
```

| 노드 | 역할 | peak 코어 | 사용률 |
|---|---|---:|---:|
| ip-172-31-45-39 (worker1 t3.medium) | auth·chat·content | 1.957 | **97.9%** |
| ip-172-31-40-241 (worker2 t3.small) | content | 1.495 | **74.8%** |
| ip-172-31-38-225 (master) | control-plane | 0.071 | 3.6% |
| worker-proxy | ingress | 0.298 | 14.9% |

주: kubelet resource 메트릭은 스크레이프 간격이 1분이라 `rate[1m]`이 빈 결과다 - `[5m]`을 썼다.
따라서 이 값은 5분 평균의 최대이며, 순간 정점은 이보다 높을 수 있다.

### 파드 - 벽이 노드마다 달랐다

```promql
max_over_time( (sum by(pod)(rate(container_cpu_usage_seconds_total{pod=~"(auth|content|chat)-service.*",container!=""}[5m])))[7m:1m] )
sum by(pod)(rate(container_cpu_cfs_throttled_periods_total{pod=~"content-service.*"}[5m]))
  / sum by(pod)(rate(container_cpu_cfs_periods_total{pod=~"content-service.*"}[5m]))
```

| 파드 | 노드 | peak 코어 | limit | requests | CFS throttled 비율 |
|---|---|---:|---:|---:|---:|
| content sp24n | worker1 | 1.489 | 1.5 | 0.3 | **76.1%** |
| content v2pw9 | worker2 | 1.494 | 1.5 | 0.3 | **95.5%** |
| auth | worker1 | 0.005 | 0.5 | 0.15 | - |
| chat | worker1 | 0.051 | 0.3 | 0.15 | - |

### 판정 - "그냥 서버 스펙"이 아니다. 두 노드에서 벽이 다르다

| 노드 | 관측 | 벽 |
|---|---|---|
| worker2 | 노드에 0.5 코어(25%) 남았는데 파드가 스로틀 95.5% | **파드 limit**. 스펙 아님 - limit 상향만으로 그 0.5 코어가 나온다 |
| worker1 | 노드 97.9%로 여유 없음 | **노드 capacity**. limit을 올려도 줄 CPU가 없다 |

기각된 것:

- **GC 아님.** `rate(jvm_gc_pause_seconds_sum{application="content-service"}[5m])` = 0.055 s/s
  (2파드 합) → 파드당 2.8%.
- **이웃 서비스 간섭 아님.** 같은 창에서 auth 0.005 · chat 0.051 코어로 사실상 idle.

### 남는 진짜 숫자 - 요청당 CPU 16.9ms

```promql
sum(rate(http_server_requests_seconds_count{application="content-service"}[5m]))   # 176.7 rps
sort_desc(sum by(uri)(rate(http_server_requests_seconds_count{application="content-service"}[5m])))
```

서버측 176.7 rps(클라 173 rps와 일치, `/feeds/{feedId}` 59.2 · `/feeds/hot` 58.9 ·
`/feeds/scroll` 58.7로 균등)에 content가 **2.98 코어**를 태웠다 → **요청당 약 16.9ms CPU.**
캐시 없는 단순 읽기 경로치고 비싸다. **이 값을 안 줄이면 replica를 늘려도 코어를 선형으로
사가는 것이지 개선이 아니다.** 정체(쿼리 수 · 직렬화 · JDBC 매핑)는 미측정이며,
요청당 쿼리 수 계측이 다음 조치다.

### 이 관측이 흔드는 것 - HPA 1순위 결론의 전제

content limit 1.5 × 2 replica = **3.0**인데 워커 총 capacity는 **4.0**이고 시스템이
0.4~0.7을 쓴다. **3번째 replica가 앉을 CPU가 물리적으로 없다.** requests 0.3 / limit 1.5는
**5배 오버커밋**이라 스케줄러는 태연히 배치하고 실제로는 셋이 서로 스로틀된다.
따라서 위 "HPA 적용 1순위"는 **JVM 다이어트 + requests/limit 재설정이 선행되지 않으면
실행 불가**다 (서사 Ch2의 Pending Gate와 같은 지점).

### 여전히 미측정

**CPU steal · CPUCreditBalance.** k3s 노드에는 node-exporter가 없고(`infra-server`만 있다)
kubelet 메트릭에는 steal 모드가 없다. t3 크레딧 축은 그대로 공백이며 AWS CLI가 필요하다
(설계 Phase 0).

---

## 재실행 (2026-08-20 · CPU-2/MEM-1 적용 후) - 예측 반증: 더 느려졌다

> **결론:** limit 상향은 기계적으로 작동했다(파드 CPU 1.49 → 1.68, 새 limit 1.7에 붙음). GC도
> 정상이라 MEM-1은 반증되지 않았다. 그런데 **처리량이 173 → ~136 rps(−21%)로 떨어졌다** -
> 요청당 CPU가 16.9ms → **22.6ms(+34%)** 로 올랐기 때문이다. "CPU를 더 주면 +10~20%"라는
> 검증 설계 2번의 예측은 반증. 요청 단가가 왜 올랐는지가 새 질문이다.

| 지표 | baseline (08-17) | 재실행 (08-20) | 델타 |
|---|---|---|---|
| 요청 수 / 처리량 | 62,441 (173 rps) | 48,870 (~136 rps) | **−21%** |
| 서버 rps (2m rate max) | 176.7 | 148.8 | −16% |
| p99 | 4.5~4.9s | 6.9~10s (max 60s 타임아웃) | 악화 |
| 실패율 | 0% | 0.36% (177건) | 악화 |
| 파드 CPU (max) | 1.489 / 1.494 (limit 1.5) | **1.681 / 1.687 (limit 1.7)** | CPU-2 작동 |
| CFS 스로틀 | 76 / 95.5% | 83 / 95.8% | 여전 (새 limit에서) |
| 노드 CPU | w1 97.9% / w2 74.8% | **w1 99.6%** / w2 86.6% | w2 회수분 사용 |
| **요청당 CPU** | **16.9ms** | **22.6ms** (3.368코어/148.8rps) | **+34%** |
| GC pause | 0.055 s/s (2파드 합) | 0.047+0.025 s/s | 동급 - **MEM-1 반증 안 됨** |
| 힙 사용 max | 596Mi (Xmx 1024) | 361/294Mi (Xmx 768) | 여유 |
| Hikari pending | 0 | ≤2 | 여유 |

원인 후보 (N=1이라 미확정):

1. **데이터 상태 드리프트 (유력)** - baseline은 08-17, 그 뒤 커넥션풀 계단(리액션 토글 8.3만) ·
   T3-E 스와이프 · 댓글 쓰기가 쌓였다. 실행 규칙 *"쓰기 시나리오 뒤에 읽기를 재면 기준선과
   비교가 안 된다"* 의 실증. **델타 실험에는 데이터 상태 통제(시드 리셋 또는 쓰기 직후 재기준선)가
   필요하다** - 다음 재실행 전 절차로 승격할 것.
2. worker1 노드 완전 포화(99.6%) - limit을 올린 만큼 노드 여유가 사라져 시스템/이웃과의 경합 증가.
3. CloudFront 경유 변동.

의미: **설정 층(CPU-2)만으로는 안 된다는 것이 실측됐다.** 요청 단가(22.6ms)가 지배 변수이고,
그것은 코드 층(CPU-1 프로파일링 → CPU-3)과 캐시(실트래픽 최다인 대시보드 3종이 사용자 무관
응답 - cpu개선.md CPU-1 갱신 절)의 몫이다.

원본: [output](2026-08-20-t2b-rerun-output.txt) · [summary](2026-08-20-t2b-rerun-summary.json) · [timestamps](2026-08-20-t2b-rerun-timestamps.txt)

## 재실행 2 (2026-08-20 · CPU limit 제거) - 꼬리의 주범은 CFS 동결이었다

> **결론:** content의 CPU limit만 제거하자(requests·메모리 limit 유지) 처리량이 baseline 수준으로
> 회복되고(서버 180.8 rps - 오히려 +2%) 중앙값은 baseline의 9배(229ms), limit 1.7 대비 p99가
> 6.9~10s → 5.4~5.7s로 돌아왔다. **limit 1.7 실행의 회귀는 CFS 스로틀 동결이 만든 것**이고,
> 경합 방어는 requests(shares)로 충분했다. 남은 꼬리(p95 4.9s · 60s 타임아웃 0.32%)는
> 노드 완전 포화(2.000/1.974코어)의 몫 - 여기부터는 설정이 아니라 단가(캐시·코드)다.

| 지표 | baseline (limit 1.5) | limit 1.7 | **limit 제거** |
|---|---|---|---|
| 요청 수 (클라) | 62,441 (173 rps) | 48,870 (~136) | **61,437 (170 rps)** |
| 서버 rps (2m max) | 176.7 | 148.8 | **180.8** |
| med | ~2.0s | 373ms | **229ms** |
| p95 | ~4.0s | 6.05s | 4.87s |
| p99 (경로별) | 4.5~4.9s | 6.9~10s | 5.4~5.7s |
| 실패율 | 0% | 0.36% | 0.32% (60s 타임아웃 잔존) |
| 파드 CPU max | 1.489/1.494 | 1.681/1.687 | **1.925/1.848** |
| CFS 스로틀 | 76/95.5% | 83/95.8% | **0 (지표 소멸 확인)** |
| 노드 CPU | w1 97.9% | w1 99.6% | **w1 100.0% / w2 98.7%** |
| 서버측 최대 지연 | 3.4s | 2.3s | 2.4s |
| GC pause max | 0.20/0.17s | 0.09/0.14s | 0.08/0.10s |
| 요청당 CPU | 16.9ms | 22.6ms | 20.9ms |

판정 (전 절의 원인 후보 최종 정리):

1. **limit 1.7 회귀의 주범 = CFS 동결 (확정).** 단일 변경(limit 제거)으로 처리량 +21%p 회복.
   95.8%의 주기에서 "quota 소진 → 100ms 동결"이 반복되며 큐잉을 증폭시켰던 것.
2. **메모리(MEM-1) 무죄 재확인** - GC max 0.08/0.10s로 세 실행 중 최선.
3. **데이터 드리프트는 부차** - med가 baseline보다 9배 빠르므로 쿼리 자체는 안 무거워졌다.
   요청당 CPU 20.9ms(baseline 16.9)의 잔여 증가분은 노드 100% 포화의 스케줄링 오버헤드로 추정.
4. **남은 한계는 노드 2코어 그 자체** - 두 노드 다 ~100%. 60s 타임아웃 소수(0.32%)는
   시스템 데몬 기아 의심(이 클러스터는 kubeReserved 미설정). 완화 후보: 시스템 몫 예약.
   근본 해소는 요청 단가 - 캐시(대시보드 3종)·CPU-1~3.

처방 확정: **content는 CPU limit 없이 운용한다** (requests 700m이 경합 방어 · 메모리 limit은 유지).
auth(1500m)·chat(1000m)의 limit은 여유 천장이라 유지 - 스로틀이 관측되면 그때 재검토.
비고: 이 실행은 파드 콜드스타트 직후라 시작 전 웜업 897요청을 넣었다(JIT 공정성).

원본: [output](2026-08-20-t2b-nolimit-output.txt) · [summary](2026-08-20-t2b-nolimit-summary.json) · [timestamps](2026-08-20-t2b-nolimit-timestamps.txt)

## 재실행 3·4 (2026-08-20 밤 ~ 08-21) - 힙 512 검증 · 코드 변경군 C 1차

같은 조건(500 VU·CloudFront·limit 제거 구성) 연속 실행 둘. 원본:
[힙512 output](2026-08-20-t2b-heap512-output.txt) · [readOnly output](2026-08-21-t2b-readonly-output.txt)

| | limit 제거 (힙 768) | +힙 512 (MEM-2) | +readOnly·조회수 분리 (코드 C) |
|---|---|---|---|
| 처리량 (클라 / 서버 max) | 170.4/s / 180.8 | 168.3/s / 181.6 | **181.1/s / 195.6 (+7.7%)** |
| 요청당 CPU (부하 구간) | 20.9ms | 20.5ms | **18.6ms · 후반 안정 15.6~16.5ms** |
| p95 / p99 | 4.87 / 5.4~5.7s | 4.89 / 5.1~5.4s | **4.53 / 4.8~5.1s** |
| GC max | 0.10s | 0.45s (처리량 무영향 - 힙 512 통과) | 0.42/0.28s |

- **힙 512**: 성능 등가로 통과 → 워킹셋 666→633Mi, resources 640Mi/896Mi로 축소 근거.
- **코드 C 1차** (toy-content `7906ef1` - FeedService 읽기 readOnly + 조회수 단문 UPDATE 분리):
  단가 −10%(평균)~−20%(웜업 후), 처리량 +7.7%. **유의미하나 크지 않다** - dirty checking과
  조회수 트랜잭션의 몫이 요청당 ~4ms였다는 실측. 남은 ~16ms가 쿼리+직렬화 본체이고,
  다음 수는 핫리스트 캐시(사용자 무관 응답 - 대시보드 3종)다.
- 주의: 중간 실측에서 "9.7ms"로 오독한 기록 있음 - 인스턴트 쿼리가 파드 한쪽만 잡은
  측정 오류였고, 시각 정렬 곡선으로 정정했다.

## 재실행 5~7 (2026-08-22) - 토폴로지 실험 3연: 스팟 이주 · 힙 440 · micro 판정

판단 전체 기록은 [개선/ec2개선.md](../../../개선/ec2개선.md). 여기는 실행 색인만.

| 실행 | 구성 | 결과 (서버 rps) | 판정 |
|---|---|---|---|
| [스팟 이주](2026-08-22-t2b-spotnode-output.txt) | content = 스팟 small + 241 (worker1에서 분리) | **241.2 (+23%)** · p95 3.39s · 실패 0.03% | 전용 노드 효과 - 새 baseline |
| [힙 440](2026-08-22-t2b-heap440-output.txt) | 위 + content Xmx 512→440 | 200.3 · GC 등가 | 워킹셋 666→630뿐(논힙 바닥) - micro 목표 미달, 512 원복 |
| [micro 판정](2026-08-22-t2b-micro-output.txt) | content 1개를 od micro에 | 192.6 (−20%) · **파드 CPU 0.95/노드 1.9** | micro 기각 - 메모리(529Mi)는 통과, 시스템이 CPU ~1코어 잠식 |

부수 실측: RDS `max_connections=60 · Max_used=62`(천장 접촉 이력) → 풀 20→12 · replica 3.
**replica 3 + 풀 12 구성의 T2-B는 미측정** - 다음 실행이 새 baseline.
