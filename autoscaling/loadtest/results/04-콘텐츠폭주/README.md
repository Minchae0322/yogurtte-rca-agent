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
