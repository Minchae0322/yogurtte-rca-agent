# RCA Report — `6a69e7fe1224083c021ae372bcec4cc0`

| 항목 | 값 |
|---|---|
| 모드 | rca |
| 질문 | 최근 2시간 안에 피드에 작성자 이름이 이상하게 나온다는 제보가 있다. 원인을 조사해줘 |
| 시각 | 2026-07-29T11:49:56.256469Z |
| provider | claude-cli |
| model | `claude-opus-5` · turns 1 |
| prompt | `./prompts/system-prompt.md` |
| tokens | in 108141 (cacheRead 18,133 · cacheCreate 90,006) / out 10342 · cost $1.2355 |
| elapsed | total 159815ms (tempo 489 · loki 192 · mimir 657 · assemble 1 · llm 152857) |

## 탐색 (Triage)

| 항목 | 값 |
|---|---|
| 시간창 해석 | 상대 표현 '최근 2시간' |
| 스윕 창 | 2026-07-29T09:48:43.542728Z ~ 2026-07-29T11:48:43.542728Z |
| 좁힌 창 | 2026-07-29T11:32:00Z ~ 2026-07-29T11:48:43Z |
| 대상 | content-service, auth-service |
| traceId | 6a69e7fe1224083c021ae372bcec4cc0 |
| 트레이스 후보 | 7건 |
| 계획 파싱 | 성공 |
| prompt | `./prompts/triage-prompt.md` |
| tokens | in 54310 / out 5326 · cost $0.5568 |
| chars | 컨텍스트 58,177 + 프롬프트 1,196 = **59,373** |
| elapsed | survey 1337ms · llm 71358ms |

**선정 이유**: auth-service up 타깃이 완전히 사라진 11:33:43Z~11:48:43Z 구간 안에서 content-service 피드 조회가 부분 실패(스팬 1개만 error)했고, 이는 작성자 프로필 조회 실패 시 대체값이 렌더링되는 시나리오와 시각·모양이 모두 맞기 때문.

**근거**

- content-service 피드 읽기 트레이스 5건이 11:45:52Z~11:46:06Z 15초 안에 몰려 error 스팬 발생 (/feeds/scroll 4건, /feeds/{feedId} 1건)
- 해당 트레이스들은 serviceStats spanCount 26~29 대비 errorCount=1 — 요청은 성공하고 팬아웃된 하위 호출 1개만 실패하는 부분 실패 패턴
- auth-service up 시계열 단절: pod pr892 마지막 샘플 11:08:43Z, glc4w 11:13:43Z~11:33:43Z, vpkqw는 11:48:43Z에 처음 등장 → 11:33:43Z~11:48:43Z 사이 auth-service up 타깃 0개
- 동일 ReplicaSet(855c75679d)에서 파드가 2시간 동안 3번 교체 — 재기동 반복. 반면 content-service(2개 파드)와 chat-service는 전 구간 up=1로 연속
- 11:45~11:46Z error 트레이스 버스트에 대응하는 Loki ERROR/WARN 로그가 0건 (로그는 11:30:00Z 버킷 content-service 1건이 마지막) → 예외가 catch되어 로그 없이 대체값으로 처리된 정황
- kafka_brokers=1, mongodb_up=1, 모든 consumergroup lag 0(할당 없는 파티션의 -1은 전 구간 상수), 노드·kubelet·cadvisor 전부 up=1 → 인프라/이벤트 파이프라인 원인 배제
- 증상이 알림이 아니라 피드 조회 화면의 표시값이므로 content->Kafka->chat 발송 경로는 대상에서 제외

**스윕이 찾은 트레이스** (고른 것은 6a69e7fe1224083c021ae372bcec4cc0)

| traceId | root service | root span | ms |
|---|---|---|---:|
| `6a69e7fe1224083c021ae372bcec4cc0` ←선택 | content-service | http get /feeds/scroll | 58 |
| `6a69e7fb0835ef66de170f1278643577` | content-service | http get /feeds/{feedId} | 161 |
| `6a69e7f8ab06633ddcb6c22ae3c931e8` | content-service | http get /feeds/scroll | 88 |
| `6a69e7f6088fd6e26b28ab66596dc999` | content-service | http get /feeds/scroll | 111 |
| `6a69e7f00e63c377af1b51ebea9ca59d` | content-service | http get /feeds/scroll | 62 |
| `6a69e3aacb0cb1b86516674f2cd52e45` | content-service | http get /feeds/following | 3 |
| `6a69e01bc0116147bba4b75907e9d57e` | content-service | http get /feeds/following | 4 |

## 수집 범위 (Coverage)

- **window**: 2026-07-29T11:32:00Z ~ 2026-07-29T11:48:43Z (1003s)
- **trace**: 24,381B / 29 spans
- **logs**: errwarn=7,719B · traceId=4,605B
- **metrics**: 8 수집 / 117,458B, 누락 [sum(rate(http_server_requests_seconds_count{application="content-service", status="401"}[1m]))]
- **context**: 154,727 chars (+ 시스템 프롬프트 575 chars)

### 토큰 축 (개선 지표)

| 단계 | 총 in | chars | 컨텍스트 토큰 |
|---|---:|---:|---:|
| 탐색 | 54,310 | 59,373 | 33,001 |
| 분석 | 108,141 | 155,302 | 86,832 |
| **합계** | **162,451** | | **119,833** |

- **overheadTokens 21,309 tok — 이 회차에 실측**(1자 프롬프트 프로브, 같은 명령·모델·샌드박스).
  호출마다 한 번씩 얹히므로 단계별로 뺀다. **다른 날 상수를 쓰지 않았다 = `█ 실측`.**
- contextTokens (count_tokens API): 측정 안 됨 — 구독 CLI 경로엔 API 키가 없다
- 비용은 **API 환산 추정치**다. 구독 계정으로 호출하므로 실제 청구액이 아니다.

## 수집 실패/누락

- Metric 'sum(rate(http_server_requests_seconds_count{application="content-service", status="401"}[1m]))' returned no series in this window; skipped.

---

# 장애 분석: 피드 작성자 이름 이상 표시

## 1. 원인 후보 랭킹 (최대 3개)

1. **auth-service 인스턴스 부재/재기동으로 인한 `GET /api/external/users` 커넥션 거부** → content-service가 작성자 이름을 조회하지 못함
2. **content-service의 실패 폴백 처리가 HTTP 200 + 대체 작성자 값으로 응답** → 사용자에게 "에러"가 아닌 "이상한 이름"으로 노출되는 경로
3. **조회 실패/폴백 값이 Redis 사용자 캐시에 잔존** → auth 복구 후에도 일부 피드에서 이름이 계속 이상하게 보임

---

## 2. 후보별 근거

### 후보 1 — auth-service 부재로 인한 사용자 조회 실패

**근거**
- 문제 trace(`6a69e7fe1224083c021ae372bcec4cc0`)의 `http get` span (kind=CLIENT, `status.code=STATUS_CODE_ERROR`):
  - `http.url` = `http://auth-service:8081/api/external/users?userIds=1,7,9,56`
  - `error` = `"finishConnect(..) failed: Connection refused: auth-service.default.svc.cluster.local/10.43.13.21:8081"`
  - `exception` = `WebClientRequestException`, `client.name` = `auth-service`
  - 구간: 11:46:06.8147Z ~ 11:46:06.8225Z (**7.76ms 만에 즉시 실패**, 타임아웃이 아니라 TCP 레벨 거부)
- 동일 시각 content-service 로그: `2026-07-29 20:46:06.818 [reactor-http-epoll-3] ERROR ... c.e.t.e.u.s.ExternalUserApiClient - 사용자 목록 조회 실패: userIds=[1, 7, 9, 56]`
- 단발이 아님 — **content-service 두 파드 모두**에서 5건 연속 발생:
  - `qnxk6`: 20:45:52 `[1, 3, 7, 9]`, 20:45:58 `[1, 3, 7]`, 20:46:00 `[1, 3, 7, 9, 56]`
  - `scw7k`: 20:46:03 `사용자 정보 조회 실패: userId=3`, 20:46:06 `[1, 7, 9, 56]`
- `up` 메트릭상 auth-service 인스턴스 공백:
  - `auth-service-855c75679d-glc4w` (10.42.1.40): 11:32:00Z ~ **11:36:15Z까지만** `up=1`, 이후 시계열 소멸
  - `auth-service-855c75679d-vpkqw` (10.42.1.41): **11:48:00Z부터** `up=1` (3포인트뿐)
  - → 약 **11분 45초간 스크레이프 대상 자체가 없음**
- 신규 파드 기동 로그(모두 `[main]` 스레드 = 부팅 중):
  - 11:46:29Z `BeanPostProcessorChecker ...`, 11:46:48Z `HHH90000025: MySQLDialect ...`, 11:47:25Z `spring.jpa.open-in-view is enabled by default`
  - → 커넥션 거부 시각(11:46:06Z)은 **auth-service가 아직 8081을 리스닝하기 전** 구간과 정합
- ReplicaSet 해시가 `855c75679d`로 **동일** → 신규 배포(이미지 교체)가 아니라 **동일 템플릿의 파드 교체**(evict/OOM/재스케줄 등)
- DNS는 정상 해석됨(`auth-service.default.svc.cluster.local/10.43.13.21`) → DNS 문제 아님, ClusterIP에 Ready 엔드포인트가 없어 REJECT된 형태
- 작성자 이름의 유일한 출처가 auth임이 trace에서 확인됨: content DB 쿼리는 `tb_feed`(21행), `tb_feed_hashtags`, `tb_user_reward`(user_id in 21개), `tb_product`, `categories` 등 **이름 컬럼을 가져오는 쿼리가 없음**

**확신도: 높음** (단, 조회창이 16분뿐이라 "최근 2시간 제보 전체의 원인"이라는 점까지는 **중간**)

**반증 데이터**
- 최상위 span `http get /feeds/scroll`은 `status=200`, `outcome=SUCCESS`, `exception=none`, 총 58ms로 **정상 종료** → 사용자 요청 자체는 실패하지 않음
- auth-service `glc4w`의 `hikaricp_connections_active=0`, `hikaricp_connections_pending=0`, G1 Young GC pause rate `0` → **자원 고갈/과부하로 죽은 정황은 전혀 없음** (즉 "auth 과부하" 가설은 기각)
- auth 인스턴스 공백 시작(11:36:15Z)과 content-service 첫 에러 로그(11:45:52Z) 사이 **약 9분 30초 동안 관련 에러 로그가 없음** → 공백 구간 전체가 실제 장애였는지 불확실 (Loki 응답이 8건만 반환되어 절단 여부 판별 불가)

---

### 후보 2 — 실패를 삼키는 폴백이 대체 작성자 값을 노출

**근거**
- 외부 호출 span은 `STATUS_CODE_ERROR`인데 서버 span은 `status=200 / outcome=SUCCESS / exception=none` → **예외가 상위로 전파되지 않고 처리됨**
- traceId 매칭 로그 1건: `[HTTP] GET /api/feeds/scroll 200 - 58ms` (`RequestLoggingFilter`) → 응답은 정상 반환
- 로그 레벨이 ERROR인데도 요청은 성공 → 조회 실패 시 **대체 값으로 채워 응답하는 폴백 경로가 존재**함을 시사
- 이는 제보 형태("에러가 난다"가 아니라 "이름이 이상하게 나온다")와 정확히 일치

**확신도: 중간**

**반증 데이터**
- **폴백이 실제로 어떤 값을 넣는지 관측 데이터에 없음** — null / 빈 문자열 / "알 수 없음" / userId 노출 / 다른 사용자 이름 혼입 중 무엇인지 판별 불가. 응답 본문 로그도, 폴백 카운터 메트릭도 없음 → 이 지점은 **데이터 부족**

---

### 후보 3 — 폴백/빈 값의 Redis 캐시 잔존

**근거**
- trace상 auth 호출 **직전**에 Redis `GET` span 5개가 연속 실행됨 (11:46:06.8114 / .8122 / .8129 / .8136 / .8139Z, `peer.service=redis`, `172.31.46.124:6379`) → **cache-aside 패턴 정황**
- 요청마다 조회 대상 userIds가 달라짐: `[1,3,7,9]` → `[1,3,7]` → `[1,3,7,9,56]` → `userId=3` 단건 → `[1,7,9,56]` → 캐시 히트/미스에 따라 미스분만 auth로 나가는 형태와 정합
- Redis 자체는 정상: `up{job="redis"}=1` 전 구간, GET span 모두 오류 없음, 0.2~0.5ms
- 캐시에 폴백 값이 기록된다면 auth 복구(11:48Z) 이후에도 TTL 동안 증상이 지속되어 "최근 2시간" 제보와 부합

**확신도: 낮음**

**반증 데이터**
- trace에 Redis `SET`/`SETEX` span이 **하나도 없음** → 조회 실패 결과를 캐시에 쓴다는 직접 증거 없음
- 캐시 키 구조·TTL·저장 값 자체를 관측할 수 있는 데이터가 전혀 없음 → **데이터 부족**

---

### 조사 범위에서 배제한 항목 (관측상 정상)

- **Kafka / chat 경로**: `kafka_brokers=1`, `up{job="kafka"}=1` 전 구간, 모든 컨슈머그룹 lag `0` (`-1`은 커밋 오프셋 미존재 표시일 뿐 지연 아님). 피드 조회는 동기 HTTP 경로이며 이 트레이스에 Kafka span 없음 → **무관**
- **DB / 커넥션풀**: content-service `hikaricp_connections_active` 최대 1, `pending` 전 구간 0, `connection` span 내 `acquired`(+1.67ms) / `commit` 이벤트 정상, 전체 쿼리 2ms 내외 → **무관**
- **인프라**: 모든 노드·kubelet·cadvisor·mongodb `up=1`, GC pause rate 최대 0.0002s/s → **무관**
- **인증 거부(401) 가설**: 이번 실패는 TCP `Connection refused`로 **HTTP 계층에 도달조차 못했으므로** 최소한 이 trace에서는 401이 원인이 아님. 다만 401 메트릭이 수집 실패해 **다른 시간대의 401 여부는 판단 불가**

---

## 3. 권장 다음 조치

**즉시 (원인 확정)**
1. auth-service 파드 교체 원인 확인
   `kubectl describe pod auth-service-855c75679d-glc4w` / `kubectl get events -n default --sort-by=.lastTimestamp | grep auth-service`
   → OOMKilled / Evicted / node NotReady / 수동 재시작 중 무엇인지. ReplicaSet 해시가 동일하므로 **배포가 아닌 파드 소실**임을 함께 확인
2. 현재 재현 여부 확인 — content-service 파드에서
   `curl -s -o /dev/null -w '%{http_code}' 'http://auth-service:8081/api/external/users?userIds=1,7,9,56'`
   및 `kubectl get endpoints auth-service` (Ready 엔드포인트 개수)
3. 사용자에게 보이는 실제 값 확인 — `/api/feeds/scroll` 응답 본문에서 작성자 이름 필드가 어떤 형태로 나오는지 직접 확인 (후보 2의 미확인 지점)

**단기 (증상 제거)**
4. `ExternalUserApiClient` 폴백 코드 리뷰: 실패 시 반환 값과, **그 값을 Redis에 캐싱하는지** 확인. 캐싱한다면 해당 키 TTL 확인 후 무효화(`SCAN`으로 사용자 캐시 키 확인 → 오염 키 삭제) 하고 재조회로 정상화 검증
5. 실패 결과는 캐시 금지 + 실패 시 "마지막 정상값 유지" 또는 명시적 로딩/에러 표시로 변경 검토 (7.76ms 즉시 실패에 재시도 없이 폴백되고 있음 → 짧은 백오프 재시도 추가 검토)

**재발 방지**
6. auth-service **replicas ≥ 2 + PodDisruptionBudget** — 현재 관측상 단일 파드가 SPOF이며 재기동 전체 구간이 곧 장애 구간
7. auth-service 부재 구간 자체를 알림화: `up{job="auth-service"}` 결측/0, 또는 content-service의 외부 호출 실패율 메트릭 추가 (지금은 ERROR 로그로만 드러남)
8. 로그 상관관계 복구: 실패 로그가 모두 `reactor-http-epoll-*` 스레드에서 `traceId=NONE`으로 기록됨 → Reactor Context 전파(Micrometer context-propagation) 설정 필요. 현재는 에러 로그를 trace로 이어붙일 수 없음

**데이터 보강 (아래 공백 때문에 위 결론의 확신도를 낮춤)**
9. **조회창이 11:32:00Z ~ 11:48:43Z(약 16분)뿐인데 제보는 "최근 2시간"** — 09:48Z~11:48Z 전체로 재조회 필요. 특히 이번 auth 재기동 이전에도 동일 패턴이 있었는지
10. 수집 실패한 `http_server_requests_seconds_count{application="content-service", status="401"}` 재조회 — 인증 거부성 실패가 별도로 있었는지 확인
11. content-service ERROR 로그 전량 재조회 (이번 응답은 8건만 반환되어 절단 여부 불명) → auth 공백 시작(11:36:15Z)과 첫 에러(11:45:52Z) 사이 9분 30초의 공백이 실제인지 로그 limit 때문인지 판별

---

## 관측 증거 (Evidence)

> 이 조사가 실제로 본 값이다. 원본 응답 전체는 `reports/raw/6a69e7fe1224083c021ae372bcec4cc0-*.json`에 있다.

### span (duration 상위 15 / 전체 29)

| ms | service | span | 시작 |
|---:|---|---|---|
| 58.62 | content-service | `http get /feeds/scroll` | 2026-07-29T11:46:06.795920Z |
| 57.24 | content-service | `secured request` | 2026-07-29T11:46:06.796528Z |
| 56.12 | content-service | `connection` | 2026-07-29T11:46:06.797478Z |
| 7.76 | content-service | `http get` | 2026-07-29T11:46:06.814740Z |
| 2.75 | content-service | `query` | 2026-07-29T11:46:06.806470Z |
| 2.69 | content-service | `query` | 2026-07-29T11:46:06.822172Z |
| 2.22 | content-service | `query` | 2026-07-29T11:46:06.832502Z |
| 2.11 | content-service | `query` | 2026-07-29T11:46:06.801191Z |
| 2.03 | content-service | `query` | 2026-07-29T11:46:06.836304Z |
| 2.03 | content-service | `query` | 2026-07-29T11:46:06.843012Z |
| 1.93 | content-service | `query` | 2026-07-29T11:46:06.827527Z |
| 1.92 | content-service | `query` | 2026-07-29T11:46:06.846058Z |
| 1.80 | content-service | `query` | 2026-07-29T11:46:06.839453Z |
| 1.25 | content-service | `result-set` | 2026-07-29T11:46:06.803429Z |
| 0.68 | content-service | `result-set` | 2026-07-29T11:46:06.834968Z |

### 로그 원문 (9 / 전체 9줄)

```
2026-07-29T11:45:52.627791520Z  [content-service]  2026-07-29 20:45:52.626 [reactor-http-epoll-2] ERROR [traceId=NONE,spanId=NONE,userId=NONE] c.e.t.e.u.s.ExternalUserApiClient - 사용자 목록 조회 실패: userIds=[1, 3, 7, 9]
2026-07-29T11:45:58.387602444Z  [content-service]  2026-07-29 20:45:58.386 [reactor-http-epoll-3] ERROR [traceId=NONE,spanId=NONE,userId=NONE] c.e.t.e.u.s.ExternalUserApiClient - 사용자 목록 조회 실패: userIds=[1, 3, 7]
2026-07-29T11:46:00.381750441Z  [content-service]  2026-07-29 20:46:00.380 [reactor-http-epoll-4] ERROR [traceId=NONE,spanId=NONE,userId=NONE] c.e.t.e.u.s.ExternalUserApiClient - 사용자 목록 조회 실패: userIds=[1, 3, 7, 9, 56]
2026-07-29T11:46:03.685018540Z  [content-service]  2026-07-29 20:46:03.682 [reactor-http-epoll-2] ERROR [traceId=NONE,spanId=NONE,userId=NONE] c.e.t.e.u.s.ExternalUserApiClient - 사용자 정보 조회 실패: userId=3
2026-07-29T11:46:06.820147764Z  [content-service]  2026-07-29 20:46:06.818 [reactor-http-epoll-3] ERROR [traceId=NONE,spanId=NONE,userId=NONE] c.e.t.e.u.s.ExternalUserApiClient - 사용자 목록 조회 실패: userIds=[1, 7, 9, 56]
2026-07-29T11:46:06.854079558Z  [content-service]  2026-07-29 20:46:06.853 [http-nio-8082-exec-2]  INFO [traceId=6a69e7fe1224083c021ae372bcec4cc0,spanId=021ae372bcec4cc0,userId=NONE] c.e.t.a.c.f.RequestLoggingFilter - [HTTP] GET /api/feeds/scroll 200 - 58ms
2026-07-29T11:46:29.864896270Z  [auth-service]  [2m2026-07-29 20:46:29[0;39m [2m[main][0;39m [33m WARN [traceId=NONE,spanId=NONE,userId=NONE][0;39m [36mo.s.c.s.PostProcessorRegistrationDelegate$BeanPostProcessorChecker[0;39m [2m-[0;39m Bean 'org.springframework.ws.config.annotation.DelegatingWsConfiguration' of type [org.springframework.ws.config.annotation.DelegatingWsConfiguration$$SpringCGLIB$$0] is not eligible for getting processed by all BeanPostProcessors (for example: not eligible for auto-proxying). The currently created BeanPostProcessor [annotationActionEndpointMapping] is declared through a non-static factory method on that class; consider declaring it as static instead.
2026-07-29T11:46:48.574148488Z  [auth-service]  [2m2026-07-29 20:46:48[0;39m [2m[main][0;39m [33m WARN [traceId=NONE,spanId=NONE,userId=NONE][0;39m [36morg.hibernate.orm.deprecation[0;39m [2m-[0;39m HHH90000025: MySQLDialect does not need to be specified explicitly using 'hibernate.dialect' (remove the property setting and it will be selected by default)
2026-07-29T11:47:25.707230465Z  [auth-service]  [2m2026-07-29 20:47:25[0;39m [2m[main][0;39m [33m WARN [traceId=NONE,spanId=NONE,userId=NONE][0;39m [36mo.s.b.a.o.j.JpaBaseConfiguration$JpaWebConfiguration[0;39m [2m-[0;39m spring.jpa.open-in-view is enabled by default. Therefore, database queries may be performed during view rendering. Explicitly configure spring.jpa.open-in-view to disable this warning
```

### 메트릭 시계열

| 쿼리 | series | 점 | min | max | last | 값이 0이던 구간 |
|---|---|---:|---:|---:|---:|---|
| `hikaricp_connections_active` | `{__name__=hikaricp_connections_active, application=auth-service, cluster=yogurtte-k3s-prod, container=auth-service, instance=10.42.1.40:8090, job=auth-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=auth-service-855c75679d-glc4w, pool=HikariPool-1, service=auth-service}` | 18 | 0 | 0 | 0 | **2026-07-29T11:32:00Z ~ 2026-07-29T11:36:15Z** |
| `hikaricp_connections_active` | `{__name__=hikaricp_connections_active, application=auth-service, cluster=yogurtte-k3s-prod, container=auth-service, instance=10.42.1.41:8090, job=auth-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=auth-service-855c75679d-vpkqw, pool=HikariPool-1, service=auth-service}` | 3 | 0 | 0 | 0 | **2026-07-29T11:48:00Z ~ 2026-07-29T11:48:30Z** |
| `hikaricp_connections_active` | `{__name__=hikaricp_connections_active, application=chat-service, cluster=yogurtte-k3s-prod, container=chat-service, instance=10.42.1.39:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-857c54dd97-w7bf7, pool=HikariPool-1}` | 67 | 0 | 0 | 0 | **2026-07-29T11:32:00Z ~ 2026-07-29T11:48:30Z** |
| `hikaricp_connections_active` | `{__name__=hikaricp_connections_active, application=content-service, cluster=yogurtte-k3s-prod, container=content-service, instance=10.42.1.35:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-6c5fff897-qnxk6, pool=HikariPool-1}` | 67 | 0 | 1 | 0 | **2026-07-29T11:32:00Z ~ 2026-07-29T11:34:00Z, 2026-07-29T11:35:15Z ~ 2026-07-29T11:45:00Z, 2026-07-29T11:46:15Z ~ 2026-07-29T11:48:30Z** |
| `hikaricp_connections_active` | `{__name__=hikaricp_connections_active, application=content-service, cluster=yogurtte-k3s-prod, container=content-service, instance=10.42.3.41:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-6c5fff897-scw7k, pool=HikariPool-1}` | 67 | 0 | 0 | 0 | **2026-07-29T11:32:00Z ~ 2026-07-29T11:48:30Z** |
| `hikaricp_connections_pending` | `{__name__=hikaricp_connections_pending, application=auth-service, cluster=yogurtte-k3s-prod, container=auth-service, instance=10.42.1.40:8090, job=auth-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=auth-service-855c75679d-glc4w, pool=HikariPool-1, service=auth-service}` | 18 | 0 | 0 | 0 | **2026-07-29T11:32:00Z ~ 2026-07-29T11:36:15Z** |
| `hikaricp_connections_pending` | `{__name__=hikaricp_connections_pending, application=auth-service, cluster=yogurtte-k3s-prod, container=auth-service, instance=10.42.1.41:8090, job=auth-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=auth-service-855c75679d-vpkqw, pool=HikariPool-1, service=auth-service}` | 3 | 0 | 0 | 0 | **2026-07-29T11:48:00Z ~ 2026-07-29T11:48:30Z** |
| `hikaricp_connections_pending` | `{__name__=hikaricp_connections_pending, application=chat-service, cluster=yogurtte-k3s-prod, container=chat-service, instance=10.42.1.39:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-857c54dd97-w7bf7, pool=HikariPool-1}` | 67 | 0 | 0 | 0 | **2026-07-29T11:32:00Z ~ 2026-07-29T11:48:30Z** |
| `hikaricp_connections_pending` | `{__name__=hikaricp_connections_pending, application=content-service, cluster=yogurtte-k3s-prod, container=content-service, instance=10.42.1.35:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-6c5fff897-qnxk6, pool=HikariPool-1}` | 67 | 0 | 0 | 0 | **2026-07-29T11:32:00Z ~ 2026-07-29T11:48:30Z** |
| `hikaricp_connections_pending` | `{__name__=hikaricp_connections_pending, application=content-service, cluster=yogurtte-k3s-prod, container=content-service, instance=10.42.3.41:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-6c5fff897-scw7k, pool=HikariPool-1}` | 67 | 0 | 0 | 0 | **2026-07-29T11:32:00Z ~ 2026-07-29T11:48:30Z** |
| `rate(jvm_gc_pause_seconds_sum[5m])` | `{action=end of major GC, application=chat-service, cause=Allocation Failure, cluster=yogurtte-k3s-prod, container=chat-service, gc=MarkSweepCompact, instance=10.42.1.39:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-857c54dd97-w7bf7}` | 67 | 0 | 0 | 0 | **2026-07-29T11:32:00Z ~ 2026-07-29T11:48:30Z** |
| `rate(jvm_gc_pause_seconds_sum[5m])` | `{action=end of minor GC, application=auth-service, cause=G1 Evacuation Pause, cluster=yogurtte-k3s-prod, container=auth-service, gc=G1 Young Generation, instance=10.42.1.40:8090, job=auth-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=auth-service-855c75679d-glc4w, service=auth-service}` | 30 | 0 | 0 | 0 | **2026-07-29T11:32:00Z ~ 2026-07-29T11:39:15Z** |
| `rate(jvm_gc_pause_seconds_sum[5m])` | `{action=end of minor GC, application=chat-service, cause=Allocation Failure, cluster=yogurtte-k3s-prod, container=chat-service, gc=Copy, instance=10.42.1.39:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-857c54dd97-w7bf7}` | 67 | 0.000 | 0.000 | 0.000 | — |
| `rate(jvm_gc_pause_seconds_sum[5m])` | `{action=end of minor GC, application=content-service, cause=Allocation Failure, cluster=yogurtte-k3s-prod, container=content-service, gc=Copy, instance=10.42.1.35:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-6c5fff897-qnxk6}` | 67 | 0 | 0.000 | 0.000 | **2026-07-29T11:32:00Z ~ 2026-07-29T11:37:00Z, 2026-07-29T11:41:15Z ~ 2026-07-29T11:46:00Z** |
| `rate(jvm_gc_pause_seconds_sum[5m])` | `{action=end of minor GC, application=content-service, cause=Allocation Failure, cluster=yogurtte-k3s-prod, container=content-service, gc=Copy, instance=10.42.3.41:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-6c5fff897-scw7k}` | 67 | 0 | 0.000 | 0 | **2026-07-29T11:32:00Z ~ 2026-07-29T11:32:45Z, 2026-07-29T11:37:00Z ~ 2026-07-29T11:42:45Z, 2026-07-29T11:47:00Z ~ 2026-07-29T11:48:30Z** |
| `up` | `{__name__=up, app=node-exporter, cluster=yogurtte-k3s-prod, component=metrics, container=node-exporter, instance=ip-172-31-40-241, job=integrations/node_exporter, k8s_cluster_name=yogurtte-k3s-prod, namespace=monitoring, pod=grafana-k8s-monitoring-node-exporter-cph5l, source=kubernetes, workload=DaemonSet/grafana-k8s-monitoring-node-exporter}` | 67 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, app=node-exporter, cluster=yogurtte-k3s-prod, component=metrics, container=node-exporter, instance=ip-172-31-45-39, job=integrations/node_exporter, k8s_cluster_name=yogurtte-k3s-prod, namespace=monitoring, pod=grafana-k8s-monitoring-node-exporter-9kvqt, source=kubernetes, workload=DaemonSet/grafana-k8s-monitoring-node-exporter}` | 67 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, cluster=yogurtte-k3s-prod, container=auth-service, instance=10.42.1.40:8090, job=auth-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=auth-service-855c75679d-glc4w}` | 18 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, cluster=yogurtte-k3s-prod, container=auth-service, instance=10.42.1.41:8090, job=auth-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=auth-service-855c75679d-vpkqw}` | 3 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, cluster=yogurtte-k3s-prod, container=chat-service, instance=10.42.1.39:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-857c54dd97-w7bf7}` | 67 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, cluster=yogurtte-k3s-prod, container=content-service, instance=10.42.1.35:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-6c5fff897-qnxk6}` | 67 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, cluster=yogurtte-k3s-prod, container=content-service, instance=10.42.3.41:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-6c5fff897-scw7k}` | 67 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, cluster=yogurtte-k3s-prod, instance=10.42.1.248:8080, job=integrations/kubernetes/kube-state-metrics, k8s_cluster_name=yogurtte-k3s-prod, source=kubernetes}` | 67 | 1 | 1 | 1 | — |
| `mongodb_up` | `{__name__=mongodb_up, cluster=yogurtte-k3s-prod, instance=infra-server, job=mongodb, k8s_cluster_name=yogurtte-k3s-prod}` | 67 | 1 | 1 | 1 | — |
| `kafka_brokers` | `{__name__=kafka_brokers, cluster=yogurtte-k3s-prod, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod}` | 67 | 1 | 1 | 1 | — |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=chat-service-fcm-tokens, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=0, topic=user.fcm-tokens}` | 67 | 0 | 0 | 0 | **2026-07-29T11:32:00Z ~ 2026-07-29T11:48:30Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=chat-service-fcm-tokens, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=1, topic=user.fcm-tokens}` | 67 | 0 | 0 | 0 | **2026-07-29T11:32:00Z ~ 2026-07-29T11:48:30Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=chat-service-fcm-tokens, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=2, topic=user.fcm-tokens}` | 67 | 0 | 0 | 0 | **2026-07-29T11:32:00Z ~ 2026-07-29T11:48:30Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=chat-service-notification-settings, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=0, topic=user.notification-settings}` | 67 | 0 | 0 | 0 | **2026-07-29T11:32:00Z ~ 2026-07-29T11:48:30Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=chat-service-notification-settings, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=1, topic=user.notification-settings}` | 67 | 0 | 0 | 0 | **2026-07-29T11:32:00Z ~ 2026-07-29T11:48:30Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=chat-service-notification-settings, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=2, topic=user.notification-settings}` | 67 | 0 | 0 | 0 | **2026-07-29T11:32:00Z ~ 2026-07-29T11:48:30Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=db-writer, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=0, topic=chat.messages}` | 67 | 0 | 0 | 0 | **2026-07-29T11:32:00Z ~ 2026-07-29T11:48:30Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=db-writer, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=1, topic=chat.messages}` | 67 | 0 | 0 | 0 | **2026-07-29T11:32:00Z ~ 2026-07-29T11:48:30Z** |
| `websocket_active_users` | `{__name__=websocket_active_users, application=chat-service, cluster=yogurtte-k3s-prod, container=chat-service, instance=10.42.1.39:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-857c54dd97-w7bf7}` | 67 | 0 | 0 | 0 | **2026-07-29T11:32:00Z ~ 2026-07-29T11:48:30Z** |

값이 0이던 구간은 굵게 표시했다 — 프로세스가 사라져 시계열이 꺾인 것이 유일한 신호인 장애가 있다.

