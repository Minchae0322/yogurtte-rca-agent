# NF-13. CPU 스로틀 중 Hikari 커넥션이 847회 교체된다 — `pending` 지표로는 안 보인다

- 심각도: **중간** (요청 실패는 없음. 포화 구간에서 CPU를 되먹임으로 더 태운다)
- 상태: **관측 확정 · 기전 가설** (교체 횟수·생성 지연·설정은 실측, 폐기 사유는 미확인)
- 발견 경로: 부하테스트 T2-B(콘텐츠 폭주 500 VU)의 **소급 관측** — 2026-08-19, Mimir 히스토리
- 계열: [NF-10](nf-10-content-db-connection-held-during-external-call.md)·[NF-11](nf-11-feed-scroll-n-plus-one.md)과
  **같은 커넥션 점유 시간(W)** 위의 문제다. 둘이 W를 늘리고, 이 결함은 W가 늘어난 상태에서 발현한다

---

## 한 줄

부하 중 content 두 파드의 커넥션 풀 `pending`은 0이었는데, **CPU 스로틀이 더 심한 쪽 파드에서만
7분간 커넥션 847개가 새로 만들어졌다**(쌍둥이 파드는 18개). 생성 1건당 실측 100ms이므로 초당
0.4초어치를 커넥션 재수립에만 썼다. `pending`·`active`만 보는 관측 목록으로는 잡히지 않는다.

## 배경 — 커넥션 풀이 무엇을 하는가

앱이 DB에 질의하려면 커넥션(연결 1개)이 필요하고, 새로 맺는 비용은 이 시스템에서 **실측 100ms**다
(TCP + 인증 + TLS). 그래서 미리 몇 개 열어두고 빌려 쓰고 반납한다. 필요한 개수는 상수가 아니라
큐잉 관계로 정해진다:

```
필요 커넥션 L = 초당 대출 수 λ × 커넥션 점유 시간 W       (Little's law)
```

T2-B 창에서 이 관계가 성립하는지 먼저 확인했다.

| 항목 | content 파드당 실측 |
|---|---|
| λ (대출/s) | 88.0 / 89.1 |
| W (평균 점유, s) | 0.174 / 0.167 |
| **λ×W = 필요 커넥션** | **15.3 / 14.9** |
| 실제 `active` | 15~20 (풀 max 20) |
| 요청당 대출 횟수 | **1회** (88 대출/s ÷ 88 rps) |

공식이 맞는다. 그리고 요청당 대출이 1회이므로 **커넥션 재획득 자체가 요청 경로를 늘린 것은 아니다.**

## 무엇이 문제인가

### ① 같은 조건의 쌍둥이 파드에서 커넥션 교체 횟수가 47배 갈렸다

누적 카운터 원값(`hikaricp_connections_creation_seconds_count`, 60초 간격, 창 7분):

| 파드 | 노드 | CFS 스로틀 | 카운터 추이 | 신규 생성 |
|---|---|---:|---|---:|
| content **sp24n** | worker1 | 76.1% | 2726 → 2744 | **18개** |
| content **v2pw9** | worker2 | **95.5%** | 2696 → 3543 | **847개** |

같은 이미지 · 같은 설정 · 같은 부하(λ 88 vs 89)다. 분당 증가분은 v2pw9에서
`+1 → +14 → +31 → +199 → +236 → +232 → +134`로, 부하 상승과 함께 가속했다.

배제된 것:

| 의심 | 실측 | 판정 |
|---|---|---|
| 파드 재시작 | `process_uptime_seconds` 12일 연속 증가 | 아님 |
| acquire 타임아웃으로 인한 실패 | `increase(hikaricp_connections_timeout_total[6m])` = **0** | 아님 |
| 설정상 만료 | `idle-timeout: 300s` · `max-lifetime: 1200s` | **6분 창에서 847회 불가** |
| 평시에도 그러함 | 현재(무부하) 생성률 = **0/s** | 부하·스로틀이 만든 현상 |

### ② 비용

- 생성 1건 평균 **100ms** (`creation_seconds_sum/count`)
- 피크 3.9개/s → **초당 0.39초**어치가 커넥션 재수립에 묶인다
- `acquire` 평균이 두 파드에서 **6ms vs 12ms**로 갈린 것도 이것으로 설명된다

### ③ 풀 설정이 고정 크기가 아니다

`toy-content/src/main/resources/application-prod.yml`

```yaml
spring.datasource.hikari:
  maximum-pool-size: 20
  minimum-idle: 3          # ← 최대치와 17개 차이. 풀이 늘 오르내린다
  connection-timeout: 10000
  idle-timeout: 300000
  max-lifetime: 1200000
```

평소 3개만 유지하고 부하 시 20개까지 늘리는 구성이다. **늘릴 때마다 100ms 비용이 발생한다.**
설정 만료가 847회의 직접 원인은 아니지만(위 배제표), 이 구성이 교체가 일어날 수 있는
17개의 여지를 상시로 열어 둔다.

## 기전 — 가설과 확인 방법

**가설:** CPU를 95% 스로틀당하면 Hikari의 커넥션 검증·하우스키핑 작업도 CPU를 못 받는다.
검증이 지연되면 살아 있는 커넥션이 죽은 것으로 판정돼 폐기되고 즉시 재생성된다.

```
CPU 부족 → 커넥션 폐기·재생성 → 생성 비용(100ms · TLS 핸드셰이크 CPU) → CPU 더 부족 → …
```

**되먹임이라는 점이 핵심이다.** 스로틀이 심한 쪽에서만 발현했고 부하와 함께 가속한 것이 정황이다.

**확인 방법 (미실행):**

1. Hikari DEBUG 로그에서 폐기 사유 확인 (`Closing connection ... (reason)`)
2. 다른 창들에서 `container_cpu_cfs_throttled_periods_total` 비율과
   `rate(hikaricp_connections_creation_seconds_count)`의 상관 — 단조 관계가 나오는지
3. `validationTimeout` 기본값(= `connection-timeout` 10s 이하)에 걸리는지 로그로 확인

**반증 조건:** 스로틀이 낮은 창에서도 생성률이 같게 나오면 CPU 기전이 아니고,
설정·드라이버·RDS 측 원인을 봐야 한다.

## 왜 지금까지 안 보였나 — 관측 목록의 구멍

T2-B 결과 문서는 *"풀은 버텼다 (`pending` 0~1)"* 로 적혀 있다. **그 판정 자체는 맞다.**
대기 큐는 실제로 비어 있었다. 문제는 그 지표가 **교체를 볼 수 없다**는 것이다.

| 지표 | T2-B에서 | 이 결함이 보이나 |
|---|---|---|
| `hikaricp_connections_pending` | 0~1 | 안 보임 |
| `hikaricp_connections_active` | 15~20 | 안 보임 |
| `hikaricp_connections_timeout_total` | 0 | 안 보임 |
| **`hikaricp_connections_creation_seconds_count`** | **+847** | **보임** |

부하테스트 설계의 커넥션 풀 관측 목록에 마지막 항목이 없었다.

## 이 결함이 무엇을 무효화하는가 — "풀 상향"이라는 처방

부하테스트 설계 §3-3의 판정 표에는 `pending↑ · DB 여유 → 풀 상향`이라는 행이 있었다.
이 관측이 그 행을 두 군데서 깬다.

| 원래 처방 | 문제 |
|---|---|
| `maximum-pool-size` 상향 | `minimum-idle`을 그대로 두면 오르내림 폭만 커진다. 상한이 아니라 **흔들림**이 비용이다 |
| `pending↑`이면 풀 문제 | CPU 스로틀 중이면 풀 문제가 아니다. 늘려도 처리량은 안 오르고 교체만 늘어난다 |

두 번째의 선례가 이미 있다:

| 사례 | 겉보기 | 실제 원인 | 풀을 늘리면 |
|---|---|---|---|
| T2-A auth 풀 10에 `pending` 190 | 풀 부족 | BCrypt CPU 포화 (파드 limit 500m) | 처리량 불변. 대기가 풀에서 스레드로 옮겨갈 뿐 |
| T3-D 핫키 락 | 풀 고갈 | 행 락 대기로 W 폭증 | DB에 락 대기 커넥션만 쌓인다 |

## 개선 예측 (미적용 — baseline 고정 중)

**변경안:** `minimum-idle: 20` (= `maximum-pool-size`). HikariCP 공식 권고인 고정 크기 풀이다.
풀 크기 자체는 λ×W 산정치(≈15)에 여유를 둔 현재 20을 유지한다.

| 지표 | 현재 (T2-B 실측) | 변경 후 예측 |
|---|---|---|
| `creation_seconds_count` 증가분 (7분) | 847 / 18 | **20 근처 (기동 시 1회)** |
| `acquire` 평균 | 12ms / 6ms | **두 파드 모두 6ms 이하로 수렴** |
| p99 | 4.5~4.9s | **거의 불변** (병목은 CPU이므로) |
| content 파드 CPU | 0.99 (limit 포화) | **미미하게 감소** |

**반증 조건:** 고정 풀로 바꿨는데도 생성률이 유지되면 원인은 풀 구성이 아니라
드라이버·RDS 측 커넥션 종료다.

**적용 시점:** 부하테스트 진단이 끝난 뒤. 지금 고치면 T2-B가 baseline으로 성립하지 않는다
(RUNBOOK §6 · 변경군 분리). 그리고 CPU 스로틀이 근본 원인이라면 **파드 limit·requests 재설정이
선행**이고, 이 변경은 그 다음이다.

## 상위 결론 — 적정 커넥션 수는 상수가 아니다

이 건이 드러낸 더 큰 문제는, 부하테스트 §3-3의 목표가 *"적정 풀 크기 N을 찾는다"* 였다는 것이다.
N은 API마다 다르고(W가 다르므로), 같은 API도 CPU 상태에 따라 다르다(스로틀이 W에 섞이므로).
경로 하나에서 그린 곡선은 재사용되지 않는다.

**시험 목표를 이렇게 바꾸는 것이 맞다:**

- 나쁨: 적정 풀 크기 N을 찾는다 → 경로 한정 곡선, 재사용 불가
- 좋음: **λ×W 관계가 성립함을 검증하고, API별 W를 표로 만든다** → 새 API도 W만 재면 계산으로 산정

필요한 메트릭은 이미 전부 수집되고 있다(`usage_seconds_sum/count` 파드별).

## 재현 쿼리 (Mimir · 창 2026-08-17 23:09~23:16 KST)

```promql
# 커넥션 교체 — 누적 카운터 원값으로 본다 (increase는 외삽이 섞인다)
hikaricp_connections_creation_seconds_count{application="content-service"}

# 생성 1건당 소요시간
rate(hikaricp_connections_creation_seconds_sum{application="content-service"}[5m])
  / rate(hikaricp_connections_creation_seconds_count{application="content-service"}[5m])

# λ (대출률) 과 W (점유시간)
rate(hikaricp_connections_usage_seconds_count{application="content-service"}[5m])
rate(hikaricp_connections_usage_seconds_sum{application="content-service"}[5m])
  / rate(hikaricp_connections_usage_seconds_count{application="content-service"}[5m])

# 스로틀 비율 (원인 축)
rate(container_cpu_cfs_throttled_periods_total{pod=~"content-service.*"}[5m])
  / rate(container_cpu_cfs_periods_total{pod=~"content-service.*"}[5m])

# 배제 확인
increase(hikaricp_connections_timeout_total{application="content-service"}[6m])
process_uptime_seconds{application="content-service"}
```

## 참조

- 부하테스트 결과: [T2-B 콘텐츠 폭주](../../autoscaling/loadtest/results/04-콘텐츠폭주/README.md) — 소급 관측 절
- 설계 문서: [부하테스트-설계-1.md](../../autoscaling/부하테스트-설계-1.md) §3-3
- 같은 W 위의 결함: [NF-10](nf-10-content-db-connection-held-during-external-call.md) (커넥션 쥔 채 외부 호출) ·
  [NF-11](nf-11-feed-scroll-n-plus-one.md) (N+1)
- 설정 원본: `toy-content/src/main/resources/application-prod.yml` 18~24행
