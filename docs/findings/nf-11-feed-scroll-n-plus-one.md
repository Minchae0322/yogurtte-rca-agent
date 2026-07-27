# NF-11. `/feeds/scroll`의 N+1 — 피드 건수만큼 쿼리가 곱해진다

- 심각도: **중간** (현재 무해, 페이지 크기·부하 증가 시 악화)
- 상태: **확정 → 수정 완료 (2026-07-27), 회차 2에서 검증 예정**
- 발견 경로: **에이전트가 AU-2·AU-4 두 회차에서 독립적으로 지적** — 둘 다 "이번 장애의
  원인은 아니다"라고 명시하며 별건으로 분리해 제시했다
- 계열: [NF-10](nf-10-content-db-connection-held-during-external-call.md)과 **같은 커넥션 위에서**
  일어난다 — 두 결함이 곱해진다

## 무엇이 문제인가

`GET /feeds/scroll?size=10`이 피드 목록을 가져온 뒤, **피드 1건마다 개별 쿼리를 두 번씩** 더 쏜다.

```
tb_feed ... where deleted=? order by id desc limit ?      ← 목록 1회 (row-count=11)
  ├─ tb_feed_hashtags where feed_id=?   × 11
  └─ categories where category_id=?     × 11
```

11건 조회에 **1 + 22 = 23회**. 페이지 크기에 비례해 선형 증가한다.

## 실측 근거

**① 트레이스 워터폴** — [au-4/round-1.md](../au-4/round-1.md)의 `img_2.png`

`contentquery` → `contentresult-set` 쌍이 20회 넘게 연속으로 반복된다. 개별 쿼리는
1.4~1.6ms로 빠르지만 **횟수가 문제**다.

**② 에이전트가 두 회차에서 독립 지적** (재현성)

| 회차 | 지적 내용 |
|---|---|
| AU-2 (07-27 01:22) | "피드 11건에 대해 `tb_feed_hashtags where feed_id=?` 11회 + `categories where category_id=?` 11회를 개별 실행하는 **명백한 N+1**. 총 123ms라 지금은 문제가 아니지만 부하 시 위험. **이번 장애의 원인은 아니다.**" |
| AU-4 (07-27 07:23) | 같은 내용을 조치 11로 재지적 — "현재 총 126ms로 문제는 아니나 페이지 크기 증가 시 악화됩니다. **이번 장애 원인 아님, 별건.**" |

**두 번 다 스스로 "원인 아님"을 명시**하고 별건으로 분리했다. 오귀인이 아니라 부수 발견이다.

**③ 전부 같은 JDBC `connection` span 안이다**

워터폴에서 `contentquery` 반복이 전부 `connection`(baseline 227.4ms / symptom 121.94ms)의
자식으로 들어가 있다. 즉 **커넥션 점유 시간이 피드 건수에 비례한다.**

## 메커니즘 — 부하가 오르면 무엇이 무너지나

이 결함 단독으로는 지금 무해하다(개별 1.5ms × 22 ≈ 33ms). 위험한 건
[NF-10](nf-10-content-db-connection-held-during-external-call.md)과 **곱해질 때**다:

```
커넥션 점유 시간 ≈ (N+1 쿼리 × 피드 건수)  +  외부 auth 호출 대기
```

| 조건 | N+1 몫 | auth 대기 | 커넥션 점유 |
|---|---|---|---|
| 현재 (size=10, auth 정상) | ~33ms | ~100ms | ~227ms |
| size=50, auth 정상 | ~165ms | ~100ms | ~265ms |
| size=50, **auth 느림**(3s timeout) | ~165ms | **3,000ms** | **3,165ms** |

세 번째가 AU-1 시나리오다. 동시 요청이 커넥션 풀 크기를 넘는 순간 **content 읽기 경로 전체가
멈춘다.** N+1은 그 임계점을 앞당긴다.

## 수정 (2026-07-27 적용)

`toy-content/src/main/resources/application.yml`

```yaml
spring.jpa.properties.hibernate:
  default_batch_fetch_size: 100     # 신규
  jdbc:
    batch_size: 50                  # 기존 - 쓰기 배치라 읽기 N+1과 무관
```

**왜 fetch join이 아니라 batch fetch인가**: `hashtags`는 `@OneToMany` 컬렉션이라 커서
페이징과 fetch join을 함께 쓰면 Hibernate가 `HHH000104`(firstResult/maxResults specified
with collection fetch)로 **메모리 페이징**에 빠진다. `default_batch_fetch_size`는 LAZY
초기화를 `IN` 절로 묶어 같은 효과를 내면서 페이징을 깨지 않는다. 쿼리 수는 **늘지 않고
줄기만 한다**(never worse).

**왜 리포지토리가 아니라 설정인가**: 원인이 리포지토리 쿼리가 아니라 `ListView.from`의
LAZY 접근 3곳(`getCategory`·`getHashtags`·`getProduct`)이다. 설정 한 줄이 셋을 동시에
덮고, 같은 패턴이 있는 다른 조회 경로(`/feeds/following` 등)에도 함께 적용된다.

빌드·테스트 통과 확인.

> **예측 (회차 2에서 검증)**: `/feeds/scroll?size=10` 트레이스의 `contentquery` span이
> **23회 → 3회 이내**로 줄고, `connection` span 점유가 짧아진다. 개선 폭은 피드 건수에
> 비례하므로 `size`가 클수록 커진다.
> **반증 조건**: span 수는 줄었는데 `connection` 점유가 그대로면 병목이 쿼리 횟수가 아니라
> 다른 곳(직렬화·외부 호출)이다.

## 회차 2 대조 시 주의

수정으로 **트레이스 span 수가 바뀐다.** 회차 1의 절대값과 직접 비교하면 안 되는 항목:

| 항목 | 회차 1 | 회차 2에서 |
|---|---|---|
| span 총수 (baseline / symptom) | 74 / 66 | **둘 다 줄어듦** |
| T2 응답 절대값 | 0.2749s / 0.1703s | **둘 다 줄어듦** |

**단, AU-4의 핵심 판정은 그대로 유효하다.** N+1 쿼리는 baseline·symptom 양쪽에서 똑같이
줄어들므로:

- **차이 8개**(auth 서버 4 + `redisSET` 4)는 유지된다
- **"장애 중에 오히려 빨라진다"**도 유지된다 — 원인이 N+1이 아니라 auth 왕복 100ms의
  유무이기 때문

즉 회차 2는 **탐지 판정과 성능 개선을 동시에** 볼 수 있다. 다만 성능 델타를 인용할 때는
"같은 문항 두 회차 사이에 N+1 수정이 들어갔다"를 함께 밝힌다.

## 남은 것 — NF-10은 부하 테스트 트랙에서

[NF-10](nf-10-content-db-connection-held-during-external-call.md)(DB 커넥션 점유 중 외부 HTTP)은
구조 리팩터링이고 **AU-4로는 검증되지 않는다** — refused(23.5ms)가 timeout(3s)보다 빨라
커넥션 점유가 오히려 짧아지기 때문이다. **content·chat·auth 통합 부하 테스트(AU-1 · IN-3)**
전후로 묶어 `hikaricp_connections_pending` 곡선으로 검증한다 (STATUS ①-d).

**이 수정(NF-11)이 그 트랙의 조건을 바꾼다.** N+1 몫(~33ms)이 이미 빠졌으므로, 부하 테스트
때 관측되는 커넥션 점유는 **NF-10 단독 기여분에 더 가깝다.** 두 결함이 섞여 있던 상태보다
귀속이 깨끗해진 셈이다.

## 참조

- 워터폴: [au-4/round-1.md](../au-4/round-1.md) `img_2.png`
- 에이전트 원문: [au-4/round-1-rca-report.md](../au-4/round-1-rca-report.md) 조치 11 ·
  [au-2/round-1-rca-report.md](../au-2/round-1-rca-report.md) 조치 12
- 곱해지는 결함: [NF-10](nf-10-content-db-connection-held-during-external-call.md)
- 같은 계열(목록 조회 N+1): [NF-04](nf-04-comment-tx-coupling.md)
