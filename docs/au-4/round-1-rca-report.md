# RCA Report — `6a67077c87b8b863f15cc6ee1ac95fbb`

| 항목 | 값 |
|---|---|
| 모드 | rca |
| 질문 | 피드에 작성자가 '사용자123' 같은 익명으로 보인다는 제보가 있어 |
| 시각 | 2026-07-27T07:53:49.741572800Z |
| provider | claude-cli |
| prompt | `.\prompts\system-prompt.md` |
| tokens | in 61528 / out 10511 · cost $0.7089 |
| elapsed | total 165487ms (tempo 969 · loki 318 · mimir 330 · assemble 1 · llm 163835) |

## 수집 범위 (Coverage)

- **window**: 2026-07-27T07:21:40.304218Z ~ 2026-07-27T07:25:40.430825Z (240s)
- **trace**: 50,180B / 66 spans
- **logs**: errwarn=3,961B · traceId=3,962B
- **metrics**: 3 수집, 누락 [kafka_consumer_fetch_manager_records_lag]
- **context**: 65,366 chars (~16,341 tok 추정)

## 수집 실패/누락

- Metric 'kafka_consumer_fetch_manager_records_lag' returned no series in this window; skipped.

---

관측 데이터만으로 분석했습니다. 트레이스는 2026-07-27T07:23:40.304Z에 발생한 `GET /api/feeds/scroll` 1건(content-service-7bb7bffd56-vqf9f, 10.42.1.32)이며, 조회 윈도우는 이 트레이스 ±2분입니다.

## 1. 원인 후보 랭킹 (최대 3개)

1. **auth-service 연결 불가(Connection refused)로 작성자 프로필 배치 조회가 실패하고, content-service가 이를 삼킨 채 익명 폴백 값으로 피드를 렌더링**
2. **auth-service 파드/엔드포인트 자체가 윈도우 전체(07:21:40~07:25:40)에 부재 — 즉 순간 장애가 아니라 지속 다운 (후보 1의 상위 원인)**
3. **Redis 사용자 프로필 캐시에 익명/placeholder 값이 적재(negative caching)되어 auth 복구 후에도 익명 표기가 잔존** — 데이터 부족, 가설 수준

---

## 2. 후보별 근거

### 후보 1 — auth-service 프로필 조회 실패 + 폴백 익명 표기

**근거**
- span `http get` (kind=CLIENT, `client.name=auth-service`)가 유일한 `STATUS_CODE_ERROR` 스팬입니다.
  - `http.url`: `http://auth-service:8081/api/external/users?userIds=3,7,9,56`
  - `error`: `finishConnect(..) failed: Connection refused: auth-service.default.svc.cluster.local/10.43.13.21:8081`
  - `exception`: `WebClientRequestException`, `status`: `CLIENT_ERROR`, `outcome`: `UNKNOWN`
  - 구간 `.329514 ~ .353060` = **23.5ms 즉시 실패** (타임아웃이 아니라 TCP RST)
- 이 URL은 **피드 작성자 ID 배치 조회 전용 경로**(`userIds=3,7,9,56`)이며, 실패 직전 Redis `GET` 4건(`.326934`, `.327871`, `.328325`, `.328716`, 172.31.46.124:6379)이 선행합니다 → 캐시 미스 4건 → 원격 배치 조회 → 실패. 즉 **작성자 표시명을 채울 유일한 데이터 소스가 비었습니다.**
- 그럼에도 루트 스팬 `http get /feeds/scroll`은 `status=200`, `outcome=SUCCESS`, `exception=none`으로 종료(`.304218 ~ .430825`, 126.6ms). 실패 시점 `.353` 이후에도 `.425700`의 `commit`까지 77ms 동안 정상 후속 조회(카테고리/해시태그/상품)가 이어집니다 → **예외가 전파되지 않고 폴백 경로로 응답이 완성**되었음을 의미합니다.
- 피드 본문은 정상 조회됨: `tb_feed ... where deleted=? order by id desc limit ?` → `jdbc.row-count=11`. 즉 "글은 보이는데 작성자만 익명"이라는 제보 양상과 일치합니다.

**확신도: 높음**
(단, "실패 → `사용자{id}` 문자열 생성"이라는 마지막 연결 고리는 **코드/로그로 확인되지 않은 추론**입니다. 이 부분만 따로 보면 중간.)

**반증 데이터**
- 루트 스팬이 `200 / SUCCESS / exception=none`이라 요청 자체는 "정상"으로 관측됩니다. 실패가 지표·로그 어디에도 표면화되지 않았습니다.
- Loki ERROR/WARN 쿼리, traceId 일치 쿼리 **둘 다 0건**(`totalEntriesReturned: 0`). 폴백이 실행됐다는 로그 증거가 전무합니다.
- 이 트레이스의 대상 ID는 `3,7,9,56`으로, 제보된 `사용자123`과 직접 일치하지 않습니다. 동일 패턴의 다른 요청일 가능성이 높으나, **제보 건 자체를 관측한 것은 아닙니다.**

### 후보 2 — auth-service 파드/엔드포인트 부재 (지속 다운)

**근거**
- 오류가 `Connection refused`이며 대상은 **ClusterIP `10.43.13.21:8081`**. 타임아웃/5xx가 아닌 RST는 통상 ① Service 뒤 Endpoint가 비었거나 ② 파드가 8081을 리슨하지 않는(기동 중/크래시) 상태를 가리킵니다. DNS는 정상 해석됐습니다(`auth-service.default.svc.cluster.local` → IP 확보).
- 제출된 3종 메트릭(`hikaricp_connections_active`, `hikaricp_connections_pending`, `rate(jvm_gc_pause_seconds_sum[5m])`) 전부에서 **auth-service 시리즈가 단 한 개도 없습니다.** 존재하는 시리즈는 `chat-service`(10.42.1.31) 1개 파드, `content-service`(10.42.1.32, 10.42.3.39) 2개 파드뿐이며, 07:21:40~07:25:40 전 구간 17개 데이터포인트가 모두 채워져 있습니다 → **동일 윈도우에서 auth-service만 스크레이프 대상에서 사라진 상태**입니다. 순간적 blip이 아니라 최소 4분간 지속됐다는 방증입니다.

**확신도: 중간**
(메트릭 시리즈 부재는 "파드 다운"의 증거일 수도, "메트릭 수집/label 누락"일 수도 있어 단독으로는 확정 불가. `kubectl` 확인 전까지 중간.)

**반증 데이터**
- 없음. 단, auth-service의 파드 상태·재시작 횟수·`up` 메트릭·로그를 하나도 확보하지 못해 **다운 사유(OOM/크래시/스케일0/롤아웃)는 판별 불가 — 데이터 부족.**

### 후보 3 — Redis 프로필 캐시 오염(negative caching)으로 인한 익명 표기 잔존

**근거**
- auth 호출 직전 Redis `GET` 4건이 관측됩니다(`db.system=redis`, `db.operation=GET`, `peer.service=redis`). 사용자 프로필 캐시 계층이 존재하며, 조회 대상 4명(`3,7,9,56`)과 개수가 일치합니다.
- 폴백 값이 이 캐시에 write-back된다면 auth-service 복구 이후에도 TTL 만료 전까지 익명 표기가 유지됩니다. 이는 "auth는 정상인데 아직도 익명"이라는 후속 제보를 설명할 수 있는 유일한 경로입니다.

**확신도: 낮음**
(캐시 키·값·TTL·SET 스팬이 전혀 관측되지 않았습니다. 트레이스에 Redis `SET`/`SETEX` 스팬은 **없으며**, GET 4건만 존재합니다.)

**반증 데이터**
- 이 트레이스 내에 Redis 쓰기 스팬이 없습니다 → 최소한 이 요청에서는 폴백 값을 캐시에 기록하지 않았을 가능성이 있습니다(계측 누락일 수도 있어 단정 불가).

---

### 원인에서 배제한 항목 (근거 있음)

- **DB/커넥션풀 포화 아님**: `hikaricp_connections_pending`이 전 파드·전 시점 `0`. 트레이스상 커넥션 획득도 `.307480 → .309117` (1.6ms). 개별 쿼리 최장 3.9ms(`tb_feed` 조회).
  단, `hikaricp_connections_active=0`은 15초 샘플링이라 122ms짜리 커넥션 점유를 놓친 값입니다 — "트래픽 없음"의 근거로 쓰면 안 됩니다.
- **GC 아님**: content-service 두 파드 모두 GC pause rate `0`. chat-service도 minor GC 0.00017~0.00025 s/s로 무시할 수준.
- **Kafka/chat 경로 무관**: 이 트레이스는 `content → auth` **동기 HTTP 조회** 경로이며 Kafka 프로듀스/컨슘 스팬이 없습니다. 따라서 수집 실패한 `kafka_consumer_fetch_manager_records_lag` 공백이 위 결론의 확신도를 떨어뜨리지는 않습니다. 다만 **chat 알림 발송 지연 여부는 이 데이터로 판단 불가 — 데이터 부족.**

---

## 3. 권장 다음 조치

**즉시 (auth-service 가용성 확인 — 5분 내)**
1. `kubectl get endpoints auth-service -n default` — Endpoint 목록이 비었는지 확인. 비었으면 Service 셀렉터/파드 부재 확정.
2. `kubectl get pods -l app=auth-service -n default -o wide` + `kubectl describe deploy auth-service -n default` — replicas, restartCount, CrashLoopBackOff/ImagePullBackOff, readinessProbe 실패 여부.
3. `kubectl get events -n default --sort-by=.lastTimestamp | grep auth` — 07:21~07:26 구간 이벤트(OOMKilled, 롤아웃, 스케일 이벤트).
4. content-service 파드에서 재현: `kubectl exec content-service-7bb7bffd56-vqf9f -- curl -sv "http://auth-service:8081/api/external/users?userIds=3,7,9,56"` — 성공하면 이미 복구된 것이며, 그 경우 후보 3(캐시 잔존)으로 초점 이동.

**영향 범위 산정**
5. Tempo TraceQL로 동일 오류의 시간 분포 확인: `{ span.client.name="auth-service" && status=error }` — 최초 발생 시각과 총 건수로 장애 시작점과 영향 사용자 규모 산정.
6. `up{job="auth-service"}`, `kube_pod_container_status_restarts_total{pod=~"auth-service.*"}` 를 넓은 윈도우(±1시간)로 재조회 — 후보 2의 확신도를 확정으로 올리기 위한 필수 수집 항목.

**캐시 잔존 확인 (auth 복구 후에도 익명이면)**
7. Redis에서 사용자 프로필 캐시 키 조회(`3,7,9,56` 해당 키)하여 값이 placeholder인지, TTL이 얼마인지 확인. 오염 확인 시 해당 키 패턴 삭제.

**관측성 공백 메우기 (이번 조사의 최대 제약)**
8. Loki가 이 traceId·이 시간대에 **로그 0건**입니다. content-service/auth-service의 로그 수집 파이프라인(Alloy label, 로그 레벨, stdout 포맷)이 살아있는지 먼저 확인하십시오. 원인 규명보다 이게 선행 과제입니다.
9. content-service의 auth 조회 폴백 지점에 **WARN 로그(traceId 포함) + 카운터 메트릭**을 추가. 현재는 외부 의존성이 완전히 죽어도 `200 SUCCESS`로만 관측되어 알림이 전혀 울리지 않습니다. 이번 건의 실질적 재발 방지 항목입니다.

**설계상 위험 (이번 원인은 아니나 데이터로 확인됨)**
10. 실패한 `http get` 스팬의 부모는 JDBC `connection` 스팬(`.307480 ~ .429418`)입니다. **DB 커넥션을 점유한 채 외부 HTTP를 호출**하는 구조로, auth-service가 refused가 아니라 **타임아웃**으로 느려졌다면 커넥션 풀 고갈로 번졌을 구조입니다. 원격 호출을 트랜잭션 밖으로 분리 + 타임아웃/서킷브레이커 설정 검토를 권합니다.
11. 동일 트레이스에 `categories where category_id=?` 11회, `tb_feed_hashtags where feed_id=?` 11회의 N+1이 있습니다(피드 11건 기준). 현재 총 126ms로 문제는 아니나 페이지 크기 증가 시 악화됩니다. — **이번 장애 원인 아님, 별건.**
