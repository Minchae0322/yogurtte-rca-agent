# RCA Report — `6a696b6e4373bd66c1dc571d8ff6a03a`

| 항목 | 값 |
|---|---|
| 모드 | rca |
| 질문 | 최근 2시간 안에 피드에 작성자 이름이 이상하게 나온다는 제보가 있다. 원인을 조사해줘 |
| 시각 | 2026-07-29T04:03:20.108339900Z |
| provider | claude-cli |
| model | `claude-opus-5` · turns 1 |
| prompt | `.\prompts\system-prompt.md` |
| tokens | in 141810 (cacheRead 23,453 · cacheCreate 118,355) / out 7982 · cost $1.4855 |
| elapsed | total 128929ms (tempo 445 · loki 179 · mimir 638 · assemble 9 · llm 121220) |

## 탐색 (Triage)

| 항목 | 값 |
|---|---|
| 시간창 해석 | 상대 표현 '최근 2시간' |
| 스윕 창 | 2026-07-29T02:02:37.702809500Z ~ 2026-07-29T04:02:37.702809500Z |
| 좁힌 창 | 2026-07-29T02:40:00Z ~ 2026-07-29T03:05:00Z |
| 대상 | content-service, auth-service |
| traceId | 6a696b6e4373bd66c1dc571d8ff6a03a |
| 트레이스 후보 | 1건 |
| 계획 파싱 | 성공 |
| prompt | `.\prompts\triage-prompt.md` |
| tokens | in 56615 / out 2615 · cost $0.4596 |
| chars | 컨텍스트 52,718 + 프롬프트 1,231 = **53,949** |
| elapsed | survey 2507ms · llm 39876ms |

**선정 이유**: auth-service가 사라진 02:42~02:57 공백과 content-service /feeds/scroll 에러(02:54:38Z)가 정확히 겹치므로, 작성자 이름이 깨지는 피드 렌더링 경로를 이 구간의 content-service 응답과 auth-service pod 교체 원인 로그로 좁혀 본다.

**근거**

- up{job="auth-service", pod="auth-service-855c75679d-wf6c7"} 가 02:42:37Z 샘플을 마지막으로 소멸 — 이후 스크레이프 없음
- up{job="auth-service", pod="auth-service-855c75679d-pr892"} 가 02:57:37Z부터 새로 등장 (동일 ReplicaSet 해시 855c75679d = pod 교체/재기동)
- 02:42:37Z~02:57:37Z 약 15분간 auth-service 스크레이프 타깃 0개 — 인증/사용자 조회 대상이 부재한 공백 구간
- Tempo: content-service 'http get /feeds/scroll' 트레이스 6a696b6e...가 02:54:38Z(공백 구간 내부)에 error 스팬 1건, serviceStats content-service errorCount=1 / spanCount=28, duration 81ms
- Loki: content-service ERROR 1건 @02:55:00Z — 위 트레이스와 동일 시각대
- Loki: auth-service ERROR 3건 @03:00:00Z — 새 pod 기동 직후 발생, 재기동 원인 로그가 남아 있을 시점
- 대조군: kafka_brokers=1, mongodb_up=1, 전 노드 up=1이 전 구간 유지 — 인프라 광역 장애 아님
- 대조군: chat-service up=1 연속, websocket_active_users=0이 전 구간 상수 — 변화 없음이므로 이번 증상과 무관

**스윕이 찾은 트레이스** (고른 것은 6a696b6e4373bd66c1dc571d8ff6a03a)

| traceId | root service | root span | ms |
|---|---|---|---:|
| `6a696b6e4373bd66c1dc571d8ff6a03a` ←선택 | content-service | http get /feeds/scroll | 81 |

## 수집 범위 (Coverage)

- **window**: 2026-07-29T02:40:00Z ~ 2026-07-29T03:05:00Z (1500s)
- **trace**: 23,613B / 28 spans
- **logs**: errwarn=6,551B · traceId=4,639B
- **metrics**: 8 수집 / 167,780B, 누락 [sum(rate(http_server_requests_seconds_count{application="content-service", status="401"}[1m]))]
- **context**: 203,219 chars (+ 시스템 프롬프트 597 chars)

### 토큰 축 (개선 지표)

| 단계 | 총 in | chars | 컨텍스트 토큰 |
|---|---:|---:|---:|
| 탐색 | 56,615 | 53,949 | 30,039 |
| 분석 | 141,810 | 203,816 | 115,234 |
| **합계** | **198,425** | | **145,273** |

- **overheadTokens 26,576 tok — 이 회차에 실측**(1자 프롬프트 프로브, 같은 명령·모델·샌드박스).
  호출마다 한 번씩 얹히므로 단계별로 뺀다. **다른 날 상수를 쓰지 않았다 = `█ 실측`.**
- contextTokens (count_tokens API): 측정 안 됨 — 구독 CLI 경로엔 API 키가 없다
- 비용은 **API 환산 추정치**다. 구독 계정으로 호출하므로 실제 청구액이 아니다.

## 수집 실패/누락

- Metric 'sum(rate(http_server_requests_seconds_count{application="content-service", status="401"}[1m]))' returned no series in this window; skipped.

---

## 1. 원인 후보 랭킹 (최대 3개)

1. **auth-service 파드 부재/재기동 구간에 content-service의 사용자 정보 조회가 TCP 레벨에서 실패** → 피드 응답에 작성자 이름이 정상 값으로 채워지지 못함
2. **content-service `ExternalUserApiClient`의 실패 처리(폴백) 방식** → 조회 실패에도 HTTP 200으로 응답을 내보내며 잘못된/대체 이름이 그대로 노출
3. **Redis 사용자 캐시에 비정상 값이 남아 장애 구간 이후까지 지속** (가설 수준, 데이터 부족)

---

## 2. 후보별 근거

### 후보 1 — auth-service 부재 구간의 사용자 조회 실패

**근거**
- 트레이스 `6a696b6e4373bd66c1dc571d8ff6a03a`의 CLIENT span `http get`:
  - `http.url = http://auth-service:8081/api/external/users?userIds=1,3,7,9`
  - `error = "finishConnect(..) failed: Connection refused: auth-service.default.svc.cluster.local/10.43.13.21:8081"`
  - `exception = WebClientRequestException`, `status.code = STATUS_CODE_ERROR`
  - 발생 시각 `1785293678477354000` = **2026-07-29 02:54:38 UTC**
- Loki(content-service, ERROR): `c.e.t.e.u.s.ExternalUserApiClient - 사용자 목록 조회 실패: userIds=[1, 3, 7, 9]` (11:54:38.498 KST = 02:54:38 UTC). 트레이스와 밀리초 단위로 일치.
- 메트릭 `up{job="auth-service"}` 공백 구간:
  - `auth-service-855c75679d-wf6c7`(10.42.1.37): 마지막 샘플 `1785293115` = **02:45:15 UTC**
  - `auth-service-855c75679d-pr892`(10.42.1.38): 첫 샘플 `1785293850` = **02:57:30 UTC**
  - → 약 **12분 15초 동안 auth-service 인스턴스가 하나도 관측되지 않음**. 장애 span(02:54:38)은 이 공백 한가운데.
  - `hikaricp_connections_active{application="auth-service"}`도 동일한 시각에 끊기고 동일한 시각에 재등장.
- auth-service WARN 로그 3건이 **02:55:20 / 02:55:39 / 02:56:18 UTC**에 `[main]` 스레드에서 발생 (`BeanPostProcessorChecker`, `HHH90000025 MySQLDialect`, `spring.jpa.open-in-view`). 모두 **Spring Boot 기동 시점**에만 나오는 로그 → 해당 시각에 auth-service가 새로 부팅 중이었음을 뒷받침.
- 에러 유형이 timeout이나 UnknownHost가 아니라 **Connection refused (ClusterIP 10.43.13.21까지 도달)** → DNS/네트워크 문제가 아니라 **Service 뒤에 Ready 상태 엔드포인트가 없었던 상태**와 일치.
- 파드 해시가 `855c75679d`로 동일 → 새 버전 배포가 아니라 **동일 ReplicaSet 내 파드 교체(재기동/재스케줄)**. 또한 auth-service는 시점별로 항상 파드 1개만 관측됨 → **단일 레플리카, 재기동 시 100% 단절**.

**확신도: 높음** (auth 호출 실패와 그 시각 auth-service 부재라는 인과는 3개 소스(trace/log/metric)가 교차 확인됨)
단, "화면에 이상한 이름이 보이는 것"까지 연결하는 마지막 고리(폴백 값의 실제 형태)는 코드/응답 페이로드 미확보 → 그 부분만 확신도 중간.

**반증 데이터**
- 동일 트레이스의 SERVER span `http get /feeds/scroll`은 `status=200`, `outcome=SUCCESS`, `exception=none`이고 Loki에도 `[HTTP] GET /api/feeds/scroll 200 - 79ms`로 남음 → **외형상 성공 응답**. 즉 이 후보만으로는 "에러 없이 이름만 이상"을 설명하려면 후보 2가 반드시 결합되어야 함.
- 제보 범위는 "최근 2시간"이나 조회 창은 **02:40~03:05 UTC(25분)**뿐이고, 실패 로그는 **1건**만 관측됨. 2시간 내내 지속되었다는 증거는 없음 (데이터 부족).
- auth-service 재기동 원인(OOMKill/eviction/노드 문제/수동 재시작)을 보여주는 데이터는 하나도 없음.

---

### 후보 2 — content-service의 실패 폴백이 오류를 삼키고 잘못된 이름을 노출

**근거**
- 하위 CLIENT span은 `STATUS_CODE_ERROR`인데 부모 SERVER span은 `outcome=SUCCESS`, `status=200`, `exception=none`, 79ms에 정상 종료. JDBC `connection` span에도 `commit` 이벤트가 정상 기록됨 → **예외를 상위로 전파하지 않고 삼킨 뒤 응답을 구성**했다는 뜻.
- 로그 레벨이 ERROR인데도(`사용자 목록 조회 실패`) 요청 결과는 200 → 부분 실패를 사용자/클라이언트에 알리지 않는 구조.
- 조회 실패 대상은 `userIds=[1, 3, 7, 9]` **4명**뿐이고, 같은 트레이스에서 피드는 11건(`tb_feed` result-set `jdbc.row-count=11`), 사용자 관련 쿼리 `tb_user_reward ... user_id in (?×11)`는 11명 기준 → **전체가 아니라 일부 작성자만 이름이 비정상**으로 보이는 제보 양상과 정합.

**확신도: 중간** (관측 데이터로 "예외를 삼키고 200을 반환했다"는 사실은 확정되나, 폴백 값이 무엇으로 채워지는지 — 빈 문자열/`null`/"알 수 없음"/userId 노출 — 는 트레이스·로그·메트릭 어디에도 없음. **데이터 부족**: 실제 응답 바디 또는 `ExternalUserApiClient` 폴백 코드 필요)

**반증 데이터**
- 없음. (단, 폴백 값의 형태를 직접 보여주는 관측값도 없으므로 "확인"이 아니라 "미확인" 상태임을 명시)

---

### 후보 3 — Redis 사용자 캐시에 비정상 값 잔존 (가설)

**근거**
- 같은 트레이스에서 auth 호출 **직전**에 Redis GET span 4개가 연속 실행됨: `1785293678474558`, `...475820`, `...476303`, `...476689` (모두 `db.system=redis`, `peer.service=redis`, `172.31.46.124:6379`). 직후(`...477354`)에 auth로 **4명**(`userIds=1,3,7,9`)을 조회 → **캐시 미스분만 원격 조회하는 read-through 패턴**으로 해석 가능.
- 이 패턴이 맞다면, 실패 시 폴백 값을 캐시에 써넣는 경로(negative caching)가 있을 경우 **auth 복구(02:57:30 UTC) 이후에도 TTL 만료 전까지 이상한 이름이 계속 노출**될 수 있음 → "최근 2시간" 제보 범위와 25분짜리 장애 구간의 불일치를 설명할 수 있는 유일한 가설.

**확신도: 낮음 — 데이터 부족**
Redis GET의 키/값, 히트·미스 여부, 캐시 쓰기(SET) span이 전혀 없음. 위 해석은 span 순서와 개수 정합만 근거로 한 추론임.

**반증 데이터**
- 관측된 Redis span은 **GET 4건뿐이고 SET/SETEX span은 없음** → 최소한 이 트레이스에서는 실패 결과를 캐시에 기록한 흔적이 없음.
- `up{job="redis"}`는 조회 창 전체에서 `1`, Redis 자체 이상 징후 없음.

---

### 배제한 항목 (근거 없어 후보에서 제외)

- **DB/커넥션 풀**: content-service `hikaricp_connections_pending`은 전 구간 0, `active`는 최대 1. JDBC span 모두 정상 종료, `commit` 기록 있음.
- **Kafka / chat 경로**: `kafka_brokers=1` 유지, 모든 consumergroup lag 0 (`-1`은 컨슈머 미할당 파티션으로 lag 아님). 이번 증상은 `content -> Kafka -> chat` 알림 경로가 아니라 **content -> auth 동기 호출** 경로에서 발생.
- **GC / 노드**: content-service GC pause rate 최대 4.2e-5 s/s 수준, 모든 `up` 시계열(노드/kubelet/cadvisor/mongodb) `1` 유지.

### 수집 실패로 인한 확신도 하향

- `sum(rate(http_server_requests_seconds_count{application="content-service", status="401"}[1m]))` — **no series**. 인증 거절 계열 가설을 정량 배제하지 못함. 다만 이번 실패는 HTTP 응답 이전 **TCP connect 단계**에서 끝났으므로(`finishConnect ... Connection refused`) 이 공백이 후보 1의 판단을 크게 흔들지는 않음.
- 조회 창이 제보 범위(2시간)의 **약 1/5(25분)**만 커버. 영향 범위·지속 시간은 판단 불가 → **데이터 부족**.

---

## 3. 권장 다음 조치

**A. 후보 1 확정 (auth-service 재기동 원인)**
1. `kubectl -n default describe pod auth-service-855c75679d-pr892` 및 `kubectl get events -n default --sort-by=.lastTimestamp` → 02:45~02:58 UTC 구간의 Killing/OOMKilled/Evicted/Preempted 이벤트 확인.
2. `kubectl logs -n default auth-service-855c75679d-pr892 --previous` 및 `kube_pod_container_status_restarts_total{pod=~"auth-service.*"}` 조회 → 재기동 횟수·사유.
3. `kubectl get deploy auth-service -o yaml` → `replicas` 값, `readinessProbe`, `maxUnavailable`, PDB 유무 확인. **단일 레플리카면 재기동마다 동일 장애가 100% 재현됨.**

**B. 영향 범위·지속 시간 확정 (데이터 부족 해소)**
4. Loki를 **2시간 창**으로 재조회:
   `count_over_time({service_name="content-service"} |= "사용자 목록 조회 실패" [2h])` — 실패가 02:45~02:57 UTC 구간에만 몰렸는지, 그 이후에도 이어지는지 확인.
   - 장애 구간에만 몰림 → 후보 1+2 확정, 후보 3 기각.
   - 복구 이후에도 이상 이름 제보 지속 → **후보 3(캐시 잔존) 즉시 검증 필요**.
5. `up{job="auth-service"}`를 2시간 창으로 조회해 다른 공백 구간이 더 있는지 확인.

**C. 후보 2 확정 (폴백 동작)**
6. `ExternalUserApiClient`의 예외 처리 경로에서 실패 시 무엇을 반환하는지 확인(빈 이름 / `null` / 기본 문자열 / userId 원문). 이것이 "이상한 이름"의 실체.
7. 재현: auth-service 스케일을 0으로 내린 상태(스테이징)에서 `GET /api/feeds/scroll` 호출 → 응답 바디의 작성자 이름 필드 실제 값 확인.

**D. 후보 3 검증**
8. `redis-cli --scan --pattern '<user 캐시 키 prefix>*'` 후 `GET`/`TTL`로 문제 userId(1,3,7,9)의 캐시 값 확인. 폴백 값이 캐시에 저장되어 있으면 해당 키 삭제로 **즉시 완화** 가능.
9. 코드에서 조회 실패 시 캐시 write 경로가 있는지 확인 → 있으면 negative caching 제거.

**E. 재발 방지 (원인 확정 후)**
10. auth-service `replicas ≥ 2` + PDB 설정으로 재기동 중 단절 제거.
11. content-service: 사용자 조회 실패 시 **실패를 삼키지 말고** ① 재시도/서킷브레이커 적용, ② 해당 항목을 부분 실패로 명시하거나 캐시된 마지막 정상 값 사용, ③ 폴백 발동 시 별도 메트릭(카운터) 노출 → 다음엔 200 뒤에 숨지 않게.

---

## 관측 증거 (Evidence)

> 이 조사가 실제로 본 값이다. 원본 응답 전체는 `reports/raw/6a696b6e4373bd66c1dc571d8ff6a03a-*.json`에 있다.

### span (duration 상위 15 / 전체 28)

| ms | service | span | 시작 |
|---:|---|---|---|
| 81.44 | content-service | `http get /feeds/scroll` | 2026-07-29T02:54:38.461047Z |
| 78.96 | content-service | `secured request` | 2026-07-29T02:54:38.461503Z |
| 78.00 | content-service | `connection` | 2026-07-29T02:54:38.462317Z |
| 25.73 | content-service | `http get` | 2026-07-29T02:54:38.477354Z |
| 2.59 | content-service | `query` | 2026-07-29T02:54:38.531227Z |
| 2.16 | content-service | `query` | 2026-07-29T02:54:38.471302Z |
| 2.04 | content-service | `query` | 2026-07-29T02:54:38.514792Z |
| 2.03 | content-service | `query` | 2026-07-29T02:54:38.466208Z |
| 1.90 | content-service | `query` | 2026-07-29T02:54:38.520662Z |
| 1.90 | content-service | `query` | 2026-07-29T02:54:38.505497Z |
| 1.83 | content-service | `query` | 2026-07-29T02:54:38.527728Z |
| 1.82 | content-service | `query` | 2026-07-29T02:54:38.524467Z |
| 1.70 | content-service | `query` | 2026-07-29T02:54:38.509397Z |
| 1.03 | content-service | `result-set` | 2026-07-29T02:54:38.522769Z |
| 0.74 | content-service | `result-set` | 2026-07-29T02:54:38.511344Z |

### 로그 원문 (5 / 전체 5줄)

```
2026-07-29T02:54:38.500800819Z  [content-service]  2026-07-29 11:54:38.498 [reactor-http-epoll-1] ERROR [traceId=NONE,spanId=NONE,userId=NONE] c.e.t.e.u.s.ExternalUserApiClient - 사용자 목록 조회 실패: userIds=[1, 3, 7, 9]
2026-07-29T02:54:38.541948347Z  [content-service]  2026-07-29 11:54:38.541 [http-nio-8082-exec-1]  INFO [traceId=6a696b6e4373bd66c1dc571d8ff6a03a,spanId=c1dc571d8ff6a03a,userId=NONE] c.e.t.a.c.f.RequestLoggingFilter - [HTTP] GET /api/feeds/scroll 200 - 79ms
2026-07-29T02:55:20.104781850Z  [auth-service]  [2m2026-07-29 11:55:20[0;39m [2m[main][0;39m [33m WARN [traceId=NONE,spanId=NONE,userId=NONE][0;39m [36mo.s.c.s.PostProcessorRegistrationDelegate$BeanPostProcessorChecker[0;39m [2m-[0;39m Bean 'org.springframework.ws.config.annotation.DelegatingWsConfiguration' of type [org.springframework.ws.config.annotation.DelegatingWsConfiguration$$SpringCGLIB$$0] is not eligible for getting processed by all BeanPostProcessors (for example: not eligible for auto-proxying). The currently created BeanPostProcessor [annotationActionEndpointMapping] is declared through a non-static factory method on that class; consider declaring it as static instead.
2026-07-29T02:55:39.805390054Z  [auth-service]  [2m2026-07-29 11:55:39[0;39m [2m[main][0;39m [33m WARN [traceId=NONE,spanId=NONE,userId=NONE][0;39m [36morg.hibernate.orm.deprecation[0;39m [2m-[0;39m HHH90000025: MySQLDialect does not need to be specified explicitly using 'hibernate.dialect' (remove the property setting and it will be selected by default)
2026-07-29T02:56:18.965060683Z  [auth-service]  [2m2026-07-29 11:56:18[0;39m [2m[main][0;39m [33m WARN [traceId=NONE,spanId=NONE,userId=NONE][0;39m [36mo.s.b.a.o.j.JpaBaseConfiguration$JpaWebConfiguration[0;39m [2m-[0;39m spring.jpa.open-in-view is enabled by default. Therefore, database queries may be performed during view rendering. Explicitly configure spring.jpa.open-in-view to disable this warning
```

### 메트릭 시계열

| 쿼리 | series | 점 | min | max | last | 값이 0이던 구간 |
|---|---|---:|---:|---:|---:|---|
| `hikaricp_connections_active` | `{__name__=hikaricp_connections_active, application=auth-service, cluster=yogurtte-k3s-prod, container=auth-service, instance=10.42.1.37:8090, job=auth-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=auth-service-855c75679d-wf6c7, pool=HikariPool-1, service=auth-service}` | 22 | 0 | 0 | 0 | **2026-07-29T02:40:00Z ~ 2026-07-29T02:45:15Z** |
| `hikaricp_connections_active` | `{__name__=hikaricp_connections_active, application=auth-service, cluster=yogurtte-k3s-prod, container=auth-service, instance=10.42.1.38:8090, job=auth-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=auth-service-855c75679d-pr892, pool=HikariPool-1, service=auth-service}` | 31 | 0 | 0 | 0 | **2026-07-29T02:57:30Z ~ 2026-07-29T03:05:00Z** |
| `hikaricp_connections_active` | `{__name__=hikaricp_connections_active, application=chat-service, cluster=yogurtte-k3s-prod, container=chat-service, instance=10.42.1.31:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-857c54dd97-s5fbl, pool=HikariPool-1}` | 101 | 0 | 0 | 0 | **2026-07-29T02:40:00Z ~ 2026-07-29T03:05:00Z** |
| `hikaricp_connections_active` | `{__name__=hikaricp_connections_active, application=content-service, cluster=yogurtte-k3s-prod, container=content-service, instance=10.42.1.35:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-6c5fff897-qnxk6, pool=HikariPool-1}` | 101 | 0 | 1 | 0 | **2026-07-29T02:40:00Z ~ 2026-07-29T02:45:00Z, 2026-07-29T02:46:15Z ~ 2026-07-29T02:51:00Z, 2026-07-29T02:52:15Z ~ 2026-07-29T02:53:00Z, 2026-07-29T02:54:15Z ~ 2026-07-29T02:57:00Z, 2026-07-29T02:58:15Z ~ 2026-07-29T03:00:00Z, 2026-07-29T03:02:15Z ~ 2026-07-29T03:05:00Z** |
| `hikaricp_connections_active` | `{__name__=hikaricp_connections_active, application=content-service, cluster=yogurtte-k3s-prod, container=content-service, instance=10.42.3.41:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-6c5fff897-scw7k, pool=HikariPool-1}` | 101 | 0 | 0 | 0 | **2026-07-29T02:40:00Z ~ 2026-07-29T03:05:00Z** |
| `hikaricp_connections_pending` | `{__name__=hikaricp_connections_pending, application=auth-service, cluster=yogurtte-k3s-prod, container=auth-service, instance=10.42.1.37:8090, job=auth-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=auth-service-855c75679d-wf6c7, pool=HikariPool-1, service=auth-service}` | 22 | 0 | 0 | 0 | **2026-07-29T02:40:00Z ~ 2026-07-29T02:45:15Z** |
| `hikaricp_connections_pending` | `{__name__=hikaricp_connections_pending, application=auth-service, cluster=yogurtte-k3s-prod, container=auth-service, instance=10.42.1.38:8090, job=auth-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=auth-service-855c75679d-pr892, pool=HikariPool-1, service=auth-service}` | 31 | 0 | 0 | 0 | **2026-07-29T02:57:30Z ~ 2026-07-29T03:05:00Z** |
| `hikaricp_connections_pending` | `{__name__=hikaricp_connections_pending, application=chat-service, cluster=yogurtte-k3s-prod, container=chat-service, instance=10.42.1.31:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-857c54dd97-s5fbl, pool=HikariPool-1}` | 101 | 0 | 0 | 0 | **2026-07-29T02:40:00Z ~ 2026-07-29T03:05:00Z** |
| `hikaricp_connections_pending` | `{__name__=hikaricp_connections_pending, application=content-service, cluster=yogurtte-k3s-prod, container=content-service, instance=10.42.1.35:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-6c5fff897-qnxk6, pool=HikariPool-1}` | 101 | 0 | 0 | 0 | **2026-07-29T02:40:00Z ~ 2026-07-29T03:05:00Z** |
| `hikaricp_connections_pending` | `{__name__=hikaricp_connections_pending, application=content-service, cluster=yogurtte-k3s-prod, container=content-service, instance=10.42.3.41:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-6c5fff897-scw7k, pool=HikariPool-1}` | 101 | 0 | 0 | 0 | **2026-07-29T02:40:00Z ~ 2026-07-29T03:05:00Z** |
| `rate(jvm_gc_pause_seconds_sum[5m])` | `{action=end of major GC, application=chat-service, cause=Allocation Failure, cluster=yogurtte-k3s-prod, container=chat-service, gc=MarkSweepCompact, instance=10.42.1.31:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-857c54dd97-s5fbl}` | 101 | 0 | 0 | 0 | **2026-07-29T02:40:00Z ~ 2026-07-29T03:05:00Z** |
| `rate(jvm_gc_pause_seconds_sum[5m])` | `{action=end of minor GC, application=auth-service, cause=G1 Evacuation Pause, cluster=yogurtte-k3s-prod, container=auth-service, gc=G1 Young Generation, instance=10.42.1.37:8090, job=auth-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=auth-service-855c75679d-wf6c7, service=auth-service}` | 34 | 0 | 0 | 0 | **2026-07-29T02:40:00Z ~ 2026-07-29T02:48:15Z** |
| `rate(jvm_gc_pause_seconds_sum[5m])` | `{action=end of minor GC, application=auth-service, cause=G1 Evacuation Pause, cluster=yogurtte-k3s-prod, container=auth-service, gc=G1 Young Generation, instance=10.42.1.38:8090, job=auth-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=auth-service-855c75679d-pr892, service=auth-service}` | 27 | 0 | 0 | 0 | **2026-07-29T02:58:30Z ~ 2026-07-29T03:05:00Z** |
| `rate(jvm_gc_pause_seconds_sum[5m])` | `{action=end of minor GC, application=auth-service, cause=Metadata GC Threshold, cluster=yogurtte-k3s-prod, container=auth-service, gc=G1 Young Generation, instance=10.42.1.37:8090, job=auth-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=auth-service-855c75679d-wf6c7, service=auth-service}` | 34 | 0 | 0 | 0 | **2026-07-29T02:40:00Z ~ 2026-07-29T02:48:15Z** |
| `rate(jvm_gc_pause_seconds_sum[5m])` | `{action=end of minor GC, application=chat-service, cause=Allocation Failure, cluster=yogurtte-k3s-prod, container=chat-service, gc=Copy, instance=10.42.1.31:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-857c54dd97-s5fbl}` | 101 | 0.000 | 0.000 | 0.000 | — |
| `rate(jvm_gc_pause_seconds_sum[5m])` | `{action=end of minor GC, application=content-service, cause=Allocation Failure, cluster=yogurtte-k3s-prod, container=content-service, gc=Copy, instance=10.42.1.35:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-6c5fff897-qnxk6}` | 101 | 0 | 0.000 | 0.000 | **2026-07-29T02:40:00Z ~ 2026-07-29T02:44:00Z, 2026-07-29T02:48:15Z ~ 2026-07-29T02:54:00Z, 2026-07-29T02:58:15Z ~ 2026-07-29T03:04:00Z** |
| `rate(jvm_gc_pause_seconds_sum[5m])` | `{action=end of minor GC, application=content-service, cause=Allocation Failure, cluster=yogurtte-k3s-prod, container=content-service, gc=Copy, instance=10.42.3.41:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-6c5fff897-scw7k}` | 101 | 0 | 0.000 | 0 | **2026-07-29T02:40:00Z ~ 2026-07-29T02:47:45Z, 2026-07-29T02:52:00Z ~ 2026-07-29T02:59:45Z, 2026-07-29T03:04:00Z ~ 2026-07-29T03:05:00Z** |
| `up` | `{__name__=up, app=node-exporter, cluster=yogurtte-k3s-prod, component=metrics, container=node-exporter, instance=ip-172-31-40-241, job=integrations/node_exporter, k8s_cluster_name=yogurtte-k3s-prod, namespace=monitoring, pod=grafana-k8s-monitoring-node-exporter-cph5l, source=kubernetes, workload=DaemonSet/grafana-k8s-monitoring-node-exporter}` | 101 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, app=node-exporter, cluster=yogurtte-k3s-prod, component=metrics, container=node-exporter, instance=ip-172-31-45-39, job=integrations/node_exporter, k8s_cluster_name=yogurtte-k3s-prod, namespace=monitoring, pod=grafana-k8s-monitoring-node-exporter-9kvqt, source=kubernetes, workload=DaemonSet/grafana-k8s-monitoring-node-exporter}` | 101 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, cluster=yogurtte-k3s-prod, container=auth-service, instance=10.42.1.37:8090, job=auth-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=auth-service-855c75679d-wf6c7}` | 22 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, cluster=yogurtte-k3s-prod, container=auth-service, instance=10.42.1.38:8090, job=auth-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=auth-service-855c75679d-pr892}` | 31 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, cluster=yogurtte-k3s-prod, container=chat-service, instance=10.42.1.31:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-857c54dd97-s5fbl}` | 101 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, cluster=yogurtte-k3s-prod, container=content-service, instance=10.42.1.35:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-6c5fff897-qnxk6}` | 101 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, cluster=yogurtte-k3s-prod, container=content-service, instance=10.42.3.41:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-6c5fff897-scw7k}` | 101 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, cluster=yogurtte-k3s-prod, instance=10.42.1.248:8080, job=integrations/kubernetes/kube-state-metrics, k8s_cluster_name=yogurtte-k3s-prod, source=kubernetes}` | 101 | 1 | 1 | 1 | — |
| `mongodb_up` | `{__name__=mongodb_up, cluster=yogurtte-k3s-prod, instance=infra-server, job=mongodb, k8s_cluster_name=yogurtte-k3s-prod}` | 101 | 1 | 1 | 1 | — |
| `kafka_brokers` | `{__name__=kafka_brokers, cluster=yogurtte-k3s-prod, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod}` | 101 | 1 | 1 | 1 | — |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=chat-service-fcm-tokens, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=0, topic=user.fcm-tokens}` | 101 | 0 | 0 | 0 | **2026-07-29T02:40:00Z ~ 2026-07-29T03:05:00Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=chat-service-fcm-tokens, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=1, topic=user.fcm-tokens}` | 101 | 0 | 0 | 0 | **2026-07-29T02:40:00Z ~ 2026-07-29T03:05:00Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=chat-service-fcm-tokens, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=2, topic=user.fcm-tokens}` | 101 | 0 | 0 | 0 | **2026-07-29T02:40:00Z ~ 2026-07-29T03:05:00Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=chat-service-notification-settings, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=0, topic=user.notification-settings}` | 101 | 0 | 0 | 0 | **2026-07-29T02:40:00Z ~ 2026-07-29T03:05:00Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=chat-service-notification-settings, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=1, topic=user.notification-settings}` | 101 | 0 | 0 | 0 | **2026-07-29T02:40:00Z ~ 2026-07-29T03:05:00Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=chat-service-notification-settings, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=2, topic=user.notification-settings}` | 101 | 0 | 0 | 0 | **2026-07-29T02:40:00Z ~ 2026-07-29T03:05:00Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=db-writer, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=0, topic=chat.messages}` | 101 | 0 | 0 | 0 | **2026-07-29T02:40:00Z ~ 2026-07-29T03:05:00Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=db-writer, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=1, topic=chat.messages}` | 101 | 0 | 0 | 0 | **2026-07-29T02:40:00Z ~ 2026-07-29T03:05:00Z** |
| `websocket_active_users` | `{__name__=websocket_active_users, application=chat-service, cluster=yogurtte-k3s-prod, container=chat-service, instance=10.42.1.31:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-857c54dd97-s5fbl}` | 101 | 0 | 0 | 0 | **2026-07-29T02:40:00Z ~ 2026-07-29T03:05:00Z** |

값이 0이던 구간은 굵게 표시했다 — 프로세스가 사라져 시계열이 꺾인 것이 유일한 신호인 장애가 있다.

