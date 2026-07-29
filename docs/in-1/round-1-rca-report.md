# RCA Report — `6a69abfa731fcf7f04a6477ceae939f2`

| 항목 | 값 |
|---|---|
| 모드 | rca |
| 질문 | 최근 1시간 안에 문의가 몇 건 들어왔다. ① 로그인이 느리다 ② 친구가 접속해 있는데 오프라인으로 보인다 ③ 피드에 작성자 이름이 이상하다. 원인을 조사해줘 |
| 시각 | 2026-07-29T08:06:33.492171500Z |
| provider | claude-cli |
| model | `claude-opus-5` · turns 1 |
| prompt | `.\prompts\system-prompt.md` |
| tokens | in 120607 (cacheRead 23,453 · cacheCreate 97,152) / out 12365 · cost $1.3676 |
| elapsed | total 192955ms (tempo 505 · loki 303 · mimir 807 · assemble 2 · llm 185248) |

## 탐색 (Triage)

| 항목 | 값 |
|---|---|
| 시간창 해석 | 상대 표현 '최근 1시간' |
| 스윕 창 | 2026-07-29T07:05:17.922462500Z ~ 2026-07-29T08:05:17.922462500Z |
| 좁힌 창 | 2026-07-29T07:26:00Z ~ 2026-07-29T07:42:00Z |
| 대상 | content-service, chat-service |
| traceId | 6a69abfa731fcf7f04a6477ceae939f2 |
| 트레이스 후보 | 8건 |
| 계획 파싱 | 성공 |
| prompt | `.\prompts\triage-prompt.md` |
| tokens | in 50310 / out 4487 · cost $0.4382 |
| chars | 컨텍스트 42,937 + 프롬프트 1,231 = **44,168** |
| elapsed | survey 3243ms · llm 72166ms |

**선정 이유**: 세 증상이 모두 접수된 최근 1시간 중, content-service·chat-service에만 국한된 07:30:17Z 스크레이프 결손과 07:28~07:30의 ~2s 타임아웃 에러 트레이스·오류 로그 스파이크가 동시에 관측된 유일한 구간이므로, 그 앞뒤 여유를 둔 07:26~07:42를 두 서비스 대상으로 판다

**근거**

- Tempo 에러 트레이스 8건 전부 content-service, 07:28:00Z / 07:29:00Z / 07:30:00Z~07:30:02Z에 집중. durationMs가 2000·2002·2003·2005·2009·2010·2024로 모두 ~2000ms에 수렴 → 고정 타임아웃(2s) 만료 패턴
- Loki ERROR/WARN: 07:30:00Z 버킷 content-service 92건, chat-service 29건 → 07:35:00Z 버킷 16건, 6건으로 감쇠. 시간창(07:05~08:05) 내 오류가 잡힌 유일한 두 버킷이며 나머지 구간은 0건
- up 메트릭 결손: content-service 파드 10.42.1.35 / 10.42.3.41, chat-service 파드 10.42.1.39 세 개만 07:30:17Z 샘플이 없음(07:25:17Z 다음이 07:35:17Z). 프로세스 무응답 또는 재기동 정황
- 같은 07:30:17Z에 auth-service(10.42.1.38, 동일 /16 노드대역), kube-state-metrics, kubelet/cadvisor, node-exporter, kafka, mongodb, redis는 모두 샘플 정상 존재 → 노드·수집기 광역 장애가 아니라 두 서비스에 국한된 사건
- websocket_active_users{pod=chat-service-857c54dd97-w7bf7}가 07:30:17Z 샘플 결손, 그리고 전 구간 값 0 → 증상 ②(온라인인 친구가 오프라인 표시)와 정합
- kafka_brokers=1 고정, 전 컨슈머그룹(notification, db-writer, notification-processors, chat-service-fcm-tokens) lag 전 구간 0, mongodb_up=1 고정 → 메시징·DB 인프라는 이상 없음(파티션 -1은 컨슈머 미할당으로 전 구간 상수)
- auth-service는 up 연속·Loki 오류 0건·에러 트레이스 0건. 다만 지연은 오류를 남기지 않으므로 증상 ①(로그인 지연)에 대해 '정상 확정'이 아니라 '관측 신호 없음'으로 둔다

**스윕이 찾은 트레이스** (고른 것은 6a69abfa731fcf7f04a6477ceae939f2)

| traceId | root service | root span | ms |
|---|---|---|---:|
| `6a69abfa731fcf7f04a6477ceae939f2` ←선택 | content-service | task battle-deadline-notification-scheduler.notify | 2002 |
| `6a69abfa8370ac05811b44f849ed1e94` | content-service | task battle-deadline-notification-scheduler.notify | 2000 |
| `6a69abf8d32bc3512d76328f6d5dd7d1` | content-service | task product-popularity-scheduler.time-weight-upda | 2005 |
| `6a69abf8a0c14c4b517a3746909bb6a1` | content-service | task product-popularity-scheduler.time-weight-upda | 2024 |
| `6a69abbcf8361e505884c6630becfb49` | content-service | task battle-deadline-notification-scheduler.notify | 2010 |
| `6a69abbc36449e8c16ff08b3ab78f775` | content-service | task battle-deadline-notification-scheduler.notify | 2002 |
| `6a69ab80d02af753574a49de53f1759c` | content-service | task battle-deadline-notification-scheduler.notify | 2009 |
| `6a69ab80270df1b3e913a1da7dcf65bc` | content-service | task battle-deadline-notification-scheduler.notify | 2003 |

## 수집 범위 (Coverage)

- **window**: 2026-07-29T07:26:00Z ~ 2026-07-29T07:42:00Z (960s)
- **trace**: 1,386B / 1 spans
- **logs**: errwarn=46,854B · traceId=4,020B
- **metrics**: 8 수집 / 115,118B, 누락 [sum(rate(http_server_requests_seconds_count{application="content-service", status="401"}[1m]))]
- **context**: 167,760 chars (+ 시스템 프롬프트 597 chars)

### 토큰 축 (개선 지표)

| 단계 | 총 in | chars | 컨텍스트 토큰 |
|---|---:|---:|---:|
| 탐색 | 50,310 | 44,168 | 23,734 |
| 분석 | 120,607 | 168,357 | 94,031 |
| **합계** | **170,917** | | **117,765** |

- **overheadTokens 26,576 tok — 이 회차에 실측**(1자 프롬프트 프로브, 같은 명령·모델·샌드박스).
  호출마다 한 번씩 얹히므로 단계별로 뺀다. **다른 날 상수를 쓰지 않았다 = `█ 실측`.**
- contextTokens (count_tokens API): 측정 안 됨 — 구독 CLI 경로엔 API 키가 없다
- 비용은 **API 환산 추정치**다. 구독 계정으로 호출하므로 실제 청구액이 아니다.

## 수집 실패/누락

- Metric 'sum(rate(http_server_requests_seconds_count{application="content-service", status="401"}[1m]))' returned no series in this window; skipped.

---

관측 데이터만으로 분석했습니다. 결론부터: **세 증상 모두 07:27:34 UTC경 시작된 Redis(172.31.46.124:6379) 단절 하나로 설명 가능하며, 조사 대상 traceId의 스팬 자체도 그 결과물**입니다. 다만 ①(로그인 지연)은 직접 증거가 없습니다.

## 1. 원인 후보 랭킹 (최대 3개)

1. **Redis 인스턴스(172.31.46.124:6379) 연결 거부 — 공통 근본 원인** (content-service 2개 pod + chat-service 동시 영향)
2. **Redis 장애가 content-service 피드 API의 직렬 캐시 호출(사용자당 조회+저장)에 그대로 노출 — 2초 타임아웃 × N회 누적으로 16~20초 지연 및 작성자 정보(`user:info:*`) 조회 실패** (③ 및 ① 체감 지연)
3. **chat-service의 Redis 의존 경로 단절 → 접속 상태(presence) 조회 실패** (②)

## 2. 후보별 근거

### 후보 1. Redis 연결 거부 (공통 근본 원인)

- **근거**
  - 3개 pod에서 **동일 대상·동일 메시지**가 07:27:34 UTC(16:27:34 KST)에 거의 동시 시작:
    - content-service-6c5fff897-qnxk6 `16:27:34.185` / -scw7k `16:27:34.234` / chat-service-857c54dd97-w7bf7 `16:27:34.255`
    - 원문: `i.l.core.protocol.ConnectionWatchdog - Cannot reconnect to [172.31.46.124/<unresolved>:6379]: finishConnect(..) failed: Connection refused: /172.31.46.124:6379`
  - `Connection refused`(RST)는 타임아웃/네트워크 단절이 아니라 **대상 포트에 리스너가 없음**을 의미. 서로 다른 노드(`ip-172-31-45-39`의 qnxk6, `10.42.3.41`의 scw7k)의 pod가 동시에 실패 → pod/노드 측이 아닌 **Redis 서버 측 사건**.
  - `o.s.b.a.d.r.RedisReactiveHealthIndicator - Redis health check failed`가 수집 종료 시점(`16:30:34.284`)까지 약 10초 주기로 계속 반복 → 3분 이상 미복구.
  - 조사 대상 traceId(`6a69abfa731fcf7f04a6477ceae939f2`)의 **유일한 스팬**이 이 장애의 하위 증상:
    - `task battle-deadline-notification-scheduler.notify`, `outcome=ERROR`, `error=Redis command timed out`, `exception=QueryTimeoutException`, `code.function=notifyEnd`, `net.host.ip=10.42.1.35`(=qnxk6)
    - 스팬 구간 07:30:02.039→07:30:04.041 UTC = **2.002초** (Lettuce 커맨드 타임아웃 2초와 일치), 동시각 로그 `16:30:02.024`, `16:30:04.042` `TaskUtils$LoggingErrorHandler - Unexpected error occurred in scheduled task`.
  - 다른 인프라는 정상 → Redis 단독 이상: `kafka_brokers=1`, `mongodb_up=1`, 모든 `up`=1, 전 컨슈머그룹 lag 0, `hikaricp_connections_pending`=전 구간 0, `hikaricp_connections_active` 최대 1, GC pause rate 최대 `0.0003 s/s`(무시 가능).
  - 보조 근거: 앱 메트릭 스크레이프 결측이 **Redis 로그를 내는 3개 pod에만** 발생(07:29:00~07:31:00 UTC 전후 8샘플 누락). auth-service는 15초 간격 결측 없음. 헬스체크 블로킹이 actuator 스크레이프에 영향을 준 정황.
- **확신도: 높음**
- **반증 데이터**
  - `up{job="redis", instance="infra-server"}` = **전 구간 1**. 단, 이 값은 redis_exporter 스크레이프 성공만을 뜻하고 `redis_up`은 수집되지 않았으며, `instance="infra-server"`가 로그의 `172.31.46.124`와 동일 대상인지 데이터상 확인 불가.
  - 수집된 WARN/ERROR 로그가 `16:30:34.284`에서 끊김(조회 창은 16:42까지). 이후 무장애(복구)인지 쿼리 한계인지 **데이터 부족**.

### 후보 2. 직렬 캐시 호출 누적 → 피드 16~20초 지연 및 작성자 정보 실패 (③, ① 체감)

- **근거**
  - 실패 로그가 `c.e.t.e.user.service.UserCacheStore - Redis 캐시 조회 실패: cacheKey=user:info:{id}` / `Redis 캐시 저장 실패: userId={id}` 형태 → **피드 작성자 정보 캐시(`user:info:*`)가 정확히 실패 지점**.
  - 응답시간이 Redis 호출 횟수와 정확히 선형 일치 (호출당 ≈2.0초):
    | trace | 실패 로그 | 총 호출 | 관측 응답시간 |
    |---|---|---|---|
    | `6a69ab7c7577ec862faad60576a99891` | 조회 5(`1,3,7,9,56`) + 저장 5 | 10 | `[HTTP-SLOW] GET /api/feeds/scroll 200 - 20238ms` |
    | `6a69ab833572ba9c1869802635ff9819` | 조회 4(`1,3,7,9`) + 저장 4 | 8 | `[HTTP-SLOW] GET /api/feeds/scroll 200 - 16138ms` |
    | `6a69ab726d5518ab1196a2cdf52b7322` | 조회 4 + 저장 4 | 8 | `[HTTP-SLOW] GET /api/feeds/scroll 200 - 16268ms` |
  - 로그 간격 실측: `16:27:58.464 → 16:28:00.475 → 16:28:02.481 → 16:28:04.552`(각 ≈2.006~2.011초) → **실패한 커맨드마다 2초 블로킹, 재시도/차단(circuit break) 없이 사용자 수만큼 직렬 반복**.
  - ③(작성자 이름 이상)과의 연결: 이름 조회용 키가 조회·저장 모두 실패했음에도 응답은 `200`. 즉 **예외를 삼키고 fallback 값을 반환하는 경로가 존재**하며, 그 fallback이 사용자에게 노출됨.
  - ①(로그인 느림)과의 연결: 로그인 직후 피드가 16~20초 걸리므로 사용자 체감상 "로그인이 느리다"로 접수될 수 있음.
- **확신도: 중간** (지연 메커니즘 자체는 높음, "이름이 이상하게 보인다"와의 인과는 중간, ①과의 연결은 낮음)
- **반증 데이터**
  - 잘못된 이름이 실제 응답에 나갔다는 관측값은 **없음**. 응답 페이로드/오류율 데이터 미수집이며, 세 요청 모두 `200`.
  - ①에 대한 직접 증거 없음: auth-service는 조회 창 내 ERROR/WARN 로그 0건, `hikaricp_connections_active/pending`=0, GC pause rate 최대 `0.000146 s/s`, `up`=1(결측 없음) → **auth-service 자체 이상 징후는 관측되지 않음**. auth의 HTTP 레이턴시 메트릭과 Redis 사용 여부는 미수집 → **데이터 부족**.
  - `sum(rate(http_server_requests_seconds_count{application="content-service", status="401"}[1m]))` 수집 실패로 인증 관련 오류 경로는 확인 불가.

### 후보 3. chat-service Redis 단절 → 접속 상태 오표시 (②)

- **근거**
  - chat-service pod(`chat-service-857c54dd97-w7bf7`)가 동일 Redis에 대해 `16:27:34.255`부터 `16:30:32.300`까지 재연결 실패 및 `Redis health check failed` 반복.
  - `websocket_active_users{pod="chat-service-857c54dd97-w7bf7"}` = **조회 창 전 구간 0**.
  - 소비 지연은 원인이 아님: `chat-service-fcm-tokens`, `chat-service-notification-settings`, `notification`, `db-writer` 컨슈머그룹 lag 전부 0.
- **확신도: 낮음**
- **반증 데이터**
  - `websocket_active_users`가 **장애 시작 전(07:26:00~07:27:34)에도 이미 0**이며 변화가 없음 → 장애로 인한 하락이라는 증거가 되지 못함(평시 baseline 미수집).
  - chat-service의 사용자 요청 실패를 보여주는 ERROR 로그는 0건(전부 WARN, Lettuce/헬스체크 계열).
  - 접속 상태(presence)를 Redis에 저장한다는 관측 근거는 데이터 내에 **없음** — 서비스 구성 정보로부터의 추정임. **데이터 부족**.
  - 별도 이상: `kafka_consumergroup_lag`가 `chat.messages` 파티션 2·6·9·10(`db-writer`, `notification` 양쪽)과 `user.notifications.dlq` 파티션 1에서 전 구간 `-1`. 커밋된 오프셋 부재를 시사하나, 전 구간 불변이라 이번 장애와의 인과는 확인 불가.

## 3. 권장 다음 조치

**즉시 (근본 원인 확정·복구)**
1. `172.31.46.124`의 Redis 프로세스/컨테이너 상태와 **재시작 시각** 확인 — OOM kill, 프로세스 종료, `bind`/`protected-mode`/`maxclients` 변경, 최근 배포·설정 반영 이력. `Connection refused`이므로 리스닝 소켓 자체를 먼저 볼 것(`ss -ltnp | grep 6379`, `redis-cli -h 172.31.46.124 ping`).
2. **복구 시각 확정**: Loki에서 `16:30:34` 이후 구간을 limit 없이 재조회. 수집분이 그 시점에서 끊긴 것이 실제 회복인지 쿼리 한계인지 판정.

**데이터 공백 보강 (현재 결론의 확신도를 낮추는 항목)**
3. `redis_up`, `redis_connected_clients`, `redis_rejected_connections_total`, `redis_uptime_in_seconds` 수집 — 현재 `up{job="redis"}=1`은 exporter 스크레이프 성공만 의미하므로 판단 근거가 될 수 없음. 아울러 `instance="infra-server"`가 `172.31.46.124`와 동일 호스트인지 확인.
4. **①의 진위 확인 (최우선 공백)**: auth-service의 `http_server_requests_seconds_bucket{uri=~"/api/auth.*"}` P95/P99, auth-service INFO 레벨 로그, 그리고 **auth-service가 Redis(세션/리프레시 토큰/블랙리스트)를 사용하는지 설정 확인**. 현재 데이터로는 로그인 경로 지연을 확인도 반증도 할 수 없음.
5. 수집 실패한 content-service 401 지표는 `sum by (status) (rate(http_server_requests_seconds_count{application="content-service"}[1m]))`로 대체 조회(해당 status 시리즈가 아예 없었을 가능성).
6. ②의 baseline: `websocket_active_users` 장애 전 24시간 추이 비교, presence 저장소가 Redis인지 확인.

**재발 방지 (관측된 증폭 요인)**
7. `UserCacheStore`의 **직렬 N+1 호출 제거**(`MGET`/파이프라인 일괄 조회)와 캐시 실패 시 **즉시 fallback + circuit breaker** 적용. 현재는 실패 1건당 2초를 그대로 사용자 응답에 전가(10회 → 20.2초).
8. Lettuce `command timeout`/`ConnectionPoolSupport` 설정 점검 — 사용자 요청 경로의 캐시 호출 타임아웃을 2초보다 짧게, 그리고 실패 시 재시도 금지.
9. ③ 확정: `user:info:{id}` 캐시 미스 시 fallback 반환 값(기본값/직전 값/타 사용자 값 여부) 코드 경로 점검 + 장애 시점 실제 응답 페이로드 확보.
10. 관측성 결함 2건 수정 — (a) 스케줄러/Lettuce 스레드에서 `traceId=NONE`으로 기록되어 traceId 기반 로그 조회가 0건이 됨(MDC 전파 누락), (b) 장애 중 앱 pod 3개의 메트릭 스크레이프가 약 2분 결측(헬스 인디케이터가 actuator 응답을 막지 않도록 Redis health check 분리 또는 타임아웃 설정).
11. `chat.messages` 파티션 2·6·9·10의 `kafka_consumergroup_lag=-1` 원인(컨슈머 미할당/오프셋 미커밋) 별건 확인.

---

## 관측 증거 (Evidence)

> 이 조사가 실제로 본 값이다. 원본 응답 전체는 `reports/raw/6a69abfa731fcf7f04a6477ceae939f2-*.json`에 있다.

### span (duration 상위 1 / 전체 1)

| ms | service | span | 시작 |
|---:|---|---|---|
| 2002.16 | content-service | `task battle-deadline-notification-scheduler.notify` | 2026-07-29T07:30:02.039787Z |

### 로그 원문 (60 / 전체 143줄)

전체가 상한을 넘어 ERROR/WARN 줄을 우선 발췌했다. 나머지는 원본 파일에 있다.

```
2026-07-29T07:27:34.255737181Z  [chat-service]  [2m2026-07-29T16:27:34.255+09:00[0;39m [33m WARN [traceId=NONE,spanId=NONE,userId=NONE][0;39m [35m7[0;39m [2m--- [chat-service] [xecutorLoop-3-2] [                                                 ] [0;39m[36mi.l.core.protocol.ConnectionWatchdog    [0;39m [2m:[0;39m Cannot reconnect to [172.31.46.124/<unresolved>:6379]: finishConnect(..) failed: Connection refused: /172.31.46.124:6379
2026-07-29T07:27:34.299302360Z  [chat-service]  [2m2026-07-29T16:27:34.298+09:00[0;39m [33m WARN [traceId=NONE,spanId=NONE,userId=NONE][0;39m [35m7[0;39m [2m--- [chat-service] [xecutorLoop-3-1] [                                                 ] [0;39m[36mi.l.core.protocol.ConnectionWatchdog    [0;39m [2m:[0;39m Cannot reconnect to [172.31.46.124/<unresolved>:6379]: finishConnect(..) failed: Connection refused: /172.31.46.124:6379
2026-07-29T07:27:39.303490554Z  [chat-service]  [2m2026-07-29T16:27:39.303+09:00[0;39m [33m WARN [traceId=NONE,spanId=NONE,userId=NONE][0;39m [35m7[0;39m [2m--- [chat-service] [llEventLoop-6-1] [                                                 ] [0;39m[36mi.l.core.protocol.ConnectionWatchdog    [0;39m [2m:[0;39m Cannot reconnect to [172.31.46.124/<unresolved>:6379]: finishConnect(..) failed: Connection refused: /172.31.46.124:6379
2026-07-29T07:27:39.304258906Z  [chat-service]  [2m2026-07-29T16:27:39.304+09:00[0;39m [33m WARN [traceId=NONE,spanId=NONE,userId=NONE][0;39m [35m7[0;39m [2m--- [chat-service] [llEventLoop-6-2] [                                                 ] [0;39m[36mi.l.core.protocol.ConnectionWatchdog    [0;39m [2m:[0;39m Cannot reconnect to [172.31.46.124/<unresolved>:6379]: finishConnect(..) failed: Connection refused: /172.31.46.124:6379
2026-07-29T07:27:42.309866526Z  [chat-service]  [2m2026-07-29T16:27:42.307+09:00[0;39m [33m WARN [traceId=NONE,spanId=NONE,userId=NONE][0;39m [35m7[0;39m [2m--- [chat-service] [xecutorLoop-3-1] [                                                 ] [0;39m[36mo.s.b.a.d.r.RedisReactiveHealthIndicator[0;39m [2m:[0;39m Redis health check failed
2026-07-29T07:27:49.018338733Z  [content-service]  2026-07-29 16:27:49.011 [http-nio-8082-exec-3] ERROR [traceId=6a69ab726d5518ab1196a2cdf52b7322,spanId=abcde8f3cb1639b1,userId=NONE] c.e.t.e.user.service.UserCacheStore - Redis 캐시 조회 실패: cacheKey=user:info:1
2026-07-29T07:27:51.023041990Z  [content-service]  2026-07-29 16:27:51.018 [http-nio-8082-exec-3] ERROR [traceId=6a69ab726d5518ab1196a2cdf52b7322,spanId=abcde8f3cb1639b1,userId=NONE] c.e.t.e.user.service.UserCacheStore - Redis 캐시 조회 실패: cacheKey=user:info:3
2026-07-29T07:27:51.603296368Z  [chat-service]  [2m2026-07-29T16:27:51.602+09:00[0;39m [33m WARN [traceId=NONE,spanId=NONE,userId=NONE][0;39m [35m7[0;39m [2m--- [chat-service] [llEventLoop-6-1] [                                                 ] [0;39m[36mi.l.core.protocol.ConnectionWatchdog    [0;39m [2m:[0;39m Cannot reconnect to [172.31.46.124/<unresolved>:6379]: finishConnect(..) failed: Connection refused: /172.31.46.124:6379
2026-07-29T07:27:51.702719713Z  [chat-service]  [2m2026-07-29T16:27:51.702+09:00[0;39m [33m WARN [traceId=NONE,spanId=NONE,userId=NONE][0;39m [35m7[0;39m [2m--- [chat-service] [llEventLoop-6-2] [                                                 ] [0;39m[36mi.l.core.protocol.ConnectionWatchdog    [0;39m [2m:[0;39m Cannot reconnect to [172.31.46.124/<unresolved>:6379]: finishConnect(..) failed: Connection refused: /172.31.46.124:6379
2026-07-29T07:27:52.302365135Z  [chat-service]  [2m2026-07-29T16:27:52.301+09:00[0;39m [33m WARN [traceId=NONE,spanId=NONE,userId=NONE][0;39m [35m7[0;39m [2m--- [chat-service] [xecutorLoop-3-2] [                                                 ] [0;39m[36mo.s.b.a.d.r.RedisReactiveHealthIndicator[0;39m [2m:[0;39m Redis health check failed
2026-07-29T07:27:53.028841903Z  [content-service]  2026-07-29 16:27:53.023 [http-nio-8082-exec-3] ERROR [traceId=6a69ab726d5518ab1196a2cdf52b7322,spanId=abcde8f3cb1639b1,userId=NONE] c.e.t.e.user.service.UserCacheStore - Redis 캐시 조회 실패: cacheKey=user:info:7
2026-07-29T07:27:58.472454105Z  [content-service]  2026-07-29 16:27:58.464 [http-nio-8082-exec-2] ERROR [traceId=6a69ab7c7577ec862faad60576a99891,spanId=dfa97946a904e81d,userId=NONE] c.e.t.e.user.service.UserCacheStore - Redis 캐시 조회 실패: cacheKey=user:info:1
2026-07-29T07:28:00.480434611Z  [content-service]  2026-07-29 16:28:00.475 [http-nio-8082-exec-2] ERROR [traceId=6a69ab7c7577ec862faad60576a99891,spanId=dfa97946a904e81d,userId=NONE] c.e.t.e.user.service.UserCacheStore - Redis 캐시 조회 실패: cacheKey=user:info:3
2026-07-29T07:28:02.049049855Z  [content-service]  2026-07-29 16:28:02.039 [scheduling-1] ERROR [traceId=NONE,spanId=NONE,userId=NONE] o.s.s.s.TaskUtils$LoggingErrorHandler - Unexpected error occurred in scheduled task
2026-07-29T07:28:02.302784685Z  [chat-service]  [2m2026-07-29T16:28:02.301+09:00[0;39m [33m WARN [traceId=NONE,spanId=NONE,userId=NONE][0;39m [35m7[0;39m [2m--- [chat-service] [xecutorLoop-3-1] [                                                 ] [0;39m[36mo.s.b.a.d.r.RedisReactiveHealthIndicator[0;39m [2m:[0;39m Redis health check failed
2026-07-29T07:28:02.550332985Z  [content-service]  2026-07-29 16:28:02.481 [http-nio-8082-exec-2] ERROR [traceId=6a69ab7c7577ec862faad60576a99891,spanId=dfa97946a904e81d,userId=NONE] c.e.t.e.user.service.UserCacheStore - Redis 캐시 조회 실패: cacheKey=user:info:7
2026-07-29T07:28:04.407946844Z  [chat-service]  [2m2026-07-29T16:28:04.402+09:00[0;39m [33m WARN [traceId=NONE,spanId=NONE,userId=NONE][0;39m [35m7[0;39m [2m--- [chat-service] [xecutorLoop-3-2] [                                                 ] [0;39m[36mo.s.b.a.d.r.RedisReactiveHealthIndicator[0;39m [2m:[0;39m Redis health check failed
2026-07-29T07:28:04.557813717Z  [content-service]  2026-07-29 16:28:04.552 [http-nio-8082-exec-2] ERROR [traceId=6a69ab7c7577ec862faad60576a99891,spanId=dfa97946a904e81d,userId=NONE] c.e.t.e.user.service.UserCacheStore - Redis 캐시 조회 실패: cacheKey=user:info:9
2026-07-29T07:28:05.309984134Z  [content-service]  2026-07-29 16:28:05.304 [http-nio-8082-exec-4] ERROR [traceId=6a69ab833572ba9c1869802635ff9819,spanId=a388c1b852c6ef04,userId=NONE] c.e.t.e.user.service.UserCacheStore - Redis 캐시 조회 실패: cacheKey=user:info:1
2026-07-29T07:28:06.565796940Z  [content-service]  2026-07-29 16:28:06.558 [http-nio-8082-exec-2] ERROR [traceId=6a69ab7c7577ec862faad60576a99891,spanId=dfa97946a904e81d,userId=NONE] c.e.t.e.user.service.UserCacheStore - Redis 캐시 조회 실패: cacheKey=user:info:56
2026-07-29T07:28:07.313714515Z  [content-service]  2026-07-29 16:28:07.309 [http-nio-8082-exec-4] ERROR [traceId=6a69ab833572ba9c1869802635ff9819,spanId=a388c1b852c6ef04,userId=NONE] c.e.t.e.user.service.UserCacheStore - Redis 캐시 조회 실패: cacheKey=user:info:3
2026-07-29T07:28:08.003692866Z  [chat-service]  [2m2026-07-29T16:28:08.003+09:00[0;39m [33m WARN [traceId=NONE,spanId=NONE,userId=NONE][0;39m [35m7[0;39m [2m--- [chat-service] [llEventLoop-6-1] [                                                 ] [0;39m[36mi.l.core.protocol.ConnectionWatchdog    [0;39m [2m:[0;39m Cannot reconnect to [172.31.46.124/<unresolved>:6379]: finishConnect(..) failed: Connection refused: /172.31.46.124:6379
2026-07-29T07:28:08.103626722Z  [chat-service]  [2m2026-07-29T16:28:08.102+09:00[0;39m [33m WARN [traceId=NONE,spanId=NONE,userId=NONE][0;39m [35m7[0;39m [2m--- [chat-service] [llEventLoop-6-2] [                                                 ] [0;39m[36mi.l.core.protocol.ConnectionWatchdog    [0;39m [2m:[0;39m Cannot reconnect to [172.31.46.124/<unresolved>:6379]: finishConnect(..) failed: Connection refused: /172.31.46.124:6379
2026-07-29T07:28:08.621455733Z  [content-service]  2026-07-29 16:28:08.615 [http-nio-8082-exec-2] ERROR [traceId=6a69ab7c7577ec862faad60576a99891,spanId=dfa97946a904e81d,userId=NONE] c.e.t.e.user.service.UserCacheStore - Redis 캐시 저장 실패: userId=1
2026-07-29T07:28:09.319803002Z  [content-service]  2026-07-29 16:28:09.315 [http-nio-8082-exec-4] ERROR [traceId=6a69ab833572ba9c1869802635ff9819,spanId=a388c1b852c6ef04,userId=NONE] c.e.t.e.user.service.UserCacheStore - Redis 캐시 조회 실패: cacheKey=user:info:7
2026-07-29T07:28:10.632863136Z  [content-service]  2026-07-29 16:28:10.622 [http-nio-8082-exec-2] ERROR [traceId=6a69ab7c7577ec862faad60576a99891,spanId=dfa97946a904e81d,userId=NONE] c.e.t.e.user.service.UserCacheStore - Redis 캐시 저장 실패: userId=3
2026-07-29T07:28:11.323964410Z  [content-service]  2026-07-29 16:28:11.320 [http-nio-8082-exec-4] ERROR [traceId=6a69ab833572ba9c1869802635ff9819,spanId=a388c1b852c6ef04,userId=NONE] c.e.t.e.user.service.UserCacheStore - Redis 캐시 조회 실패: cacheKey=user:info:9
2026-07-29T07:28:12.639078447Z  [content-service]  2026-07-29 16:28:12.635 [http-nio-8082-exec-2] ERROR [traceId=6a69ab7c7577ec862faad60576a99891,spanId=dfa97946a904e81d,userId=NONE] c.e.t.e.user.service.UserCacheStore - Redis 캐시 저장 실패: userId=7
2026-07-29T07:28:13.371841067Z  [content-service]  2026-07-29 16:28:13.367 [http-nio-8082-exec-4] ERROR [traceId=6a69ab833572ba9c1869802635ff9819,spanId=a388c1b852c6ef04,userId=NONE] c.e.t.e.user.service.UserCacheStore - Redis 캐시 저장 실패: userId=1
2026-07-29T07:28:14.402430965Z  [chat-service]  [2m2026-07-29T16:28:14.401+09:00[0;39m [33m WARN [traceId=NONE,spanId=NONE,userId=NONE][0;39m [35m7[0;39m [2m--- [chat-service] [xecutorLoop-3-1] [                                                 ] [0;39m[36mo.s.b.a.d.r.RedisReactiveHealthIndicator[0;39m [2m:[0;39m Redis health check failed
2026-07-29T07:28:14.644545975Z  [content-service]  2026-07-29 16:28:14.640 [http-nio-8082-exec-2] ERROR [traceId=6a69ab7c7577ec862faad60576a99891,spanId=dfa97946a904e81d,userId=NONE] c.e.t.e.user.service.UserCacheStore - Redis 캐시 저장 실패: userId=9
2026-07-29T07:28:15.377098886Z  [content-service]  2026-07-29 16:28:15.373 [http-nio-8082-exec-4] ERROR [traceId=6a69ab833572ba9c1869802635ff9819,spanId=a388c1b852c6ef04,userId=NONE] c.e.t.e.user.service.UserCacheStore - Redis 캐시 저장 실패: userId=3
2026-07-29T07:28:16.649066510Z  [content-service]  2026-07-29 16:28:16.645 [http-nio-8082-exec-2] ERROR [traceId=6a69ab7c7577ec862faad60576a99891,spanId=dfa97946a904e81d,userId=NONE] c.e.t.e.user.service.UserCacheStore - Redis 캐시 저장 실패: userId=56
2026-07-29T07:28:17.386606252Z  [content-service]  2026-07-29 16:28:17.377 [http-nio-8082-exec-4] ERROR [traceId=6a69ab833572ba9c1869802635ff9819,spanId=a388c1b852c6ef04,userId=NONE] c.e.t.e.user.service.UserCacheStore - Redis 캐시 저장 실패: userId=7
2026-07-29T07:28:19.392299568Z  [content-service]  2026-07-29 16:28:19.389 [http-nio-8082-exec-4] ERROR [traceId=6a69ab833572ba9c1869802635ff9819,spanId=a388c1b852c6ef04,userId=NONE] c.e.t.e.user.service.UserCacheStore - Redis 캐시 저장 실패: userId=9
2026-07-29T07:28:24.405842862Z  [chat-service]  [2m2026-07-29T16:28:24.401+09:00[0;39m [33m WARN [traceId=NONE,spanId=NONE,userId=NONE][0;39m [35m7[0;39m [2m--- [chat-service] [xecutorLoop-3-2] [                                                 ] [0;39m[36mo.s.b.a.d.r.RedisReactiveHealthIndicator[0;39m [2m:[0;39m Redis health check failed
2026-07-29T07:28:34.402757642Z  [chat-service]  [2m2026-07-29T16:28:34.401+09:00[0;39m [33m WARN [traceId=NONE,spanId=NONE,userId=NONE][0;39m [35m7[0;39m [2m--- [chat-service] [xecutorLoop-3-1] [                                                 ] [0;39m[36mo.s.b.a.d.r.RedisReactiveHealthIndicator[0;39m [2m:[0;39m Redis health check failed
2026-07-29T07:28:38.103063481Z  [chat-service]  [2m2026-07-29T16:28:38.102+09:00[0;39m [33m WARN [traceId=NONE,spanId=NONE,userId=NONE][0;39m [35m7[0;39m [2m--- [chat-service] [llEventLoop-6-1] [                                                 ] [0;39m[36mi.l.core.protocol.ConnectionWatchdog    [0;39m [2m:[0;39m Cannot reconnect to [172.31.46.124/<unresolved>:6379]: finishConnect(..) failed: Connection refused: /172.31.46.124:6379
2026-07-29T07:28:38.203418041Z  [chat-service]  [2m2026-07-29T16:28:38.202+09:00[0;39m [33m WARN [traceId=NONE,spanId=NONE,userId=NONE][0;39m [35m7[0;39m [2m--- [chat-service] [llEventLoop-6-2] [                                                 ] [0;39m[36mi.l.core.protocol.ConnectionWatchdog    [0;39m [2m:[0;39m Cannot reconnect to [172.31.46.124/<unresolved>:6379]: finishConnect(..) failed: Connection refused: /172.31.46.124:6379
2026-07-29T07:28:44.402731961Z  [chat-service]  [2m2026-07-29T16:28:44.401+09:00[0;39m [33m WARN [traceId=NONE,spanId=NONE,userId=NONE][0;39m [35m7[0;39m [2m--- [chat-service] [xecutorLoop-3-2] [                                                 ] [0;39m[36mo.s.b.a.d.r.RedisReactiveHealthIndicator[0;39m [2m:[0;39m Redis health check failed
2026-07-29T07:28:54.402290250Z  [chat-service]  [2m2026-07-29T16:28:54.401+09:00[0;39m [33m WARN [traceId=NONE,spanId=NONE,userId=NONE][0;39m [35m7[0;39m [2m--- [chat-service] [xecutorLoop-3-1] [                                                 ] [0;39m[36mo.s.b.a.d.r.RedisReactiveHealthIndicator[0;39m [2m:[0;39m Redis health check failed
2026-07-29T07:29:02.038746964Z  [content-service]  2026-07-29 16:29:02.026 [scheduling-1] ERROR [traceId=NONE,spanId=NONE,userId=NONE] o.s.s.s.TaskUtils$LoggingErrorHandler - Unexpected error occurred in scheduled task
2026-07-29T07:29:04.402528157Z  [chat-service]  [2m2026-07-29T16:29:04.401+09:00[0;39m [33m WARN [traceId=NONE,spanId=NONE,userId=NONE][0;39m [35m7[0;39m [2m--- [chat-service] [xecutorLoop-3-2] [                                                 ] [0;39m[36mo.s.b.a.d.r.RedisReactiveHealthIndicator[0;39m [2m:[0;39m Redis health check failed
2026-07-29T07:29:08.202699752Z  [chat-service]  [2m2026-07-29T16:29:08.202+09:00[0;39m [33m WARN [traceId=NONE,spanId=NONE,userId=NONE][0;39m [35m7[0;39m [2m--- [chat-service] [llEventLoop-6-1] [                                                 ] [0;39m[36mi.l.core.protocol.ConnectionWatchdog    [0;39m [2m:[0;39m Cannot reconnect to [172.31.46.124/<unresolved>:6379]: finishConnect(..) failed: Connection refused: /172.31.46.124:6379
2026-07-29T07:29:08.303154416Z  [chat-service]  [2m2026-07-29T16:29:08.302+09:00[0;39m [33m WARN [traceId=NONE,spanId=NONE,userId=NONE][0;39m [35m7[0;39m [2m--- [chat-service] [llEventLoop-6-2] [                                                 ] [0;39m[36mi.l.core.protocol.ConnectionWatchdog    [0;39m [2m:[0;39m Cannot reconnect to [172.31.46.124/<unresolved>:6379]: finishConnect(..) failed: Connection refused: /172.31.46.124:6379
2026-07-29T07:29:14.402025422Z  [chat-service]  [2m2026-07-29T16:29:14.401+09:00[0;39m [33m WARN [traceId=NONE,spanId=NONE,userId=NONE][0;39m [35m7[0;39m [2m--- [chat-service] [xecutorLoop-3-1] [                                                 ] [0;39m[36mo.s.b.a.d.r.RedisReactiveHealthIndicator[0;39m [2m:[0;39m Redis health check failed
2026-07-29T07:29:22.302271698Z  [chat-service]  [2m2026-07-29T16:29:22.301+09:00[0;39m [33m WARN [traceId=NONE,spanId=NONE,userId=NONE][0;39m [35m7[0;39m [2m--- [chat-service] [xecutorLoop-3-2] [                                                 ] [0;39m[36mo.s.b.a.d.r.RedisReactiveHealthIndicator[0;39m [2m:[0;39m Redis health check failed
2026-07-29T07:29:32.301901202Z  [chat-service]  [2m2026-07-29T16:29:32.301+09:00[0;39m [33m WARN [traceId=NONE,spanId=NONE,userId=NONE][0;39m [35m7[0;39m [2m--- [chat-service] [xecutorLoop-3-1] [                                                 ] [0;39m[36mo.s.b.a.d.r.RedisReactiveHealthIndicator[0;39m [2m:[0;39m Redis health check failed
2026-07-29T07:29:38.303028224Z  [chat-service]  [2m2026-07-29T16:29:38.302+09:00[0;39m [33m WARN [traceId=NONE,spanId=NONE,userId=NONE][0;39m [35m7[0;39m [2m--- [chat-service] [llEventLoop-6-1] [                                                 ] [0;39m[36mi.l.core.protocol.ConnectionWatchdog    [0;39m [2m:[0;39m Cannot reconnect to [172.31.46.124/<unresolved>:6379]: finishConnect(..) failed: Connection refused: /172.31.46.124:6379
2026-07-29T07:29:38.403243044Z  [chat-service]  [2m2026-07-29T16:29:38.403+09:00[0;39m [33m WARN [traceId=NONE,spanId=NONE,userId=NONE][0;39m [35m7[0;39m [2m--- [chat-service] [llEventLoop-6-2] [                                                 ] [0;39m[36mi.l.core.protocol.ConnectionWatchdog    [0;39m [2m:[0;39m Cannot reconnect to [172.31.46.124/<unresolved>:6379]: finishConnect(..) failed: Connection refused: /172.31.46.124:6379
2026-07-29T07:29:42.301825178Z  [chat-service]  [2m2026-07-29T16:29:42.301+09:00[0;39m [33m WARN [traceId=NONE,spanId=NONE,userId=NONE][0;39m [35m7[0;39m [2m--- [chat-service] [xecutorLoop-3-2] [                                                 ] [0;39m[36mo.s.b.a.d.r.RedisReactiveHealthIndicator[0;39m [2m:[0;39m Redis health check failed
2026-07-29T07:29:52.302163338Z  [chat-service]  [2m2026-07-29T16:29:52.301+09:00[0;39m [33m WARN [traceId=NONE,spanId=NONE,userId=NONE][0;39m [35m7[0;39m [2m--- [chat-service] [xecutorLoop-3-1] [                                                 ] [0;39m[36mo.s.b.a.d.r.RedisReactiveHealthIndicator[0;39m [2m:[0;39m Redis health check failed
2026-07-29T07:30:02.039284655Z  [content-service]  2026-07-29 16:30:02.024 [scheduling-1] ERROR [traceId=NONE,spanId=NONE,userId=NONE] o.s.s.s.TaskUtils$LoggingErrorHandler - Unexpected error occurred in scheduled task
2026-07-29T07:30:02.427173177Z  [chat-service]  [2m2026-07-29T16:30:02.364+09:00[0;39m [33m WARN [traceId=NONE,spanId=NONE,userId=NONE][0;39m [35m7[0;39m [2m--- [chat-service] [xecutorLoop-3-2] [                                                 ] [0;39m[36mo.s.b.a.d.r.RedisReactiveHealthIndicator[0;39m [2m:[0;39m Redis health check failed
2026-07-29T07:30:04.053068272Z  [content-service]  2026-07-29 16:30:04.042 [scheduling-1] ERROR [traceId=NONE,spanId=NONE,userId=NONE] o.s.s.s.TaskUtils$LoggingErrorHandler - Unexpected error occurred in scheduled task
2026-07-29T07:30:08.402951063Z  [chat-service]  [2m2026-07-29T16:30:08.402+09:00[0;39m [33m WARN [traceId=NONE,spanId=NONE,userId=NONE][0;39m [35m7[0;39m [2m--- [chat-service] [llEventLoop-6-1] [                                                 ] [0;39m[36mi.l.core.protocol.ConnectionWatchdog    [0;39m [2m:[0;39m Cannot reconnect to [172.31.46.124/<unresolved>:6379]: finishConnect(..) failed: Connection refused: /172.31.46.124:6379
2026-07-29T07:30:08.503358546Z  [chat-service]  [2m2026-07-29T16:30:08.503+09:00[0;39m [33m WARN [traceId=NONE,spanId=NONE,userId=NONE][0;39m [35m7[0;39m [2m--- [chat-service] [llEventLoop-6-2] [                                                 ] [0;39m[36mi.l.core.protocol.ConnectionWatchdog    [0;39m [2m:[0;39m Cannot reconnect to [172.31.46.124/<unresolved>:6379]: finishConnect(..) failed: Connection refused: /172.31.46.124:6379
2026-07-29T07:30:12.302280443Z  [chat-service]  [2m2026-07-29T16:30:12.301+09:00[0;39m [33m WARN [traceId=NONE,spanId=NONE,userId=NONE][0;39m [35m7[0;39m [2m--- [chat-service] [xecutorLoop-3-1] [                                                 ] [0;39m[36mo.s.b.a.d.r.RedisReactiveHealthIndicator[0;39m [2m:[0;39m Redis health check failed
2026-07-29T07:30:22.302727235Z  [chat-service]  [2m2026-07-29T16:30:22.301+09:00[0;39m [33m WARN [traceId=NONE,spanId=NONE,userId=NONE][0;39m [35m7[0;39m [2m--- [chat-service] [xecutorLoop-3-2] [                                                 ] [0;39m[36mo.s.b.a.d.r.RedisReactiveHealthIndicator[0;39m [2m:[0;39m Redis health check failed
2026-07-29T07:30:32.302071836Z  [chat-service]  [2m2026-07-29T16:30:32.300+09:00[0;39m [33m WARN [traceId=NONE,spanId=NONE,userId=NONE][0;39m [35m7[0;39m [2m--- [chat-service] [xecutorLoop-3-1] [                                                 ] [0;39m[36mo.s.b.a.d.r.RedisReactiveHealthIndicator[0;39m [2m:[0;39m Redis health check failed
```

### 메트릭 시계열

| 쿼리 | series | 점 | min | max | last | 값이 0이던 구간 |
|---|---|---:|---:|---:|---:|---|
| `hikaricp_connections_active` | `{__name__=hikaricp_connections_active, application=auth-service, cluster=yogurtte-k3s-prod, container=auth-service, instance=10.42.1.38:8090, job=auth-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=auth-service-855c75679d-pr892, pool=HikariPool-1, service=auth-service}` | 65 | 0 | 0 | 0 | **2026-07-29T07:26:00Z ~ 2026-07-29T07:42:00Z** |
| `hikaricp_connections_active` | `{__name__=hikaricp_connections_active, application=chat-service, cluster=yogurtte-k3s-prod, container=chat-service, instance=10.42.1.39:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-857c54dd97-w7bf7, pool=HikariPool-1}` | 57 | 0 | 0 | 0 | **2026-07-29T07:26:00Z ~ 2026-07-29T07:42:00Z** |
| `hikaricp_connections_active` | `{__name__=hikaricp_connections_active, application=content-service, cluster=yogurtte-k3s-prod, container=content-service, instance=10.42.1.35:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-6c5fff897-qnxk6, pool=HikariPool-1}` | 57 | 0 | 1 | 1 | **2026-07-29T07:26:00Z ~ 2026-07-29T07:28:00Z, 2026-07-29T07:31:15Z ~ 2026-07-29T07:35:00Z, 2026-07-29T07:36:15Z ~ 2026-07-29T07:38:00Z, 2026-07-29T07:39:15Z ~ 2026-07-29T07:41:00Z** |
| `hikaricp_connections_active` | `{__name__=hikaricp_connections_active, application=content-service, cluster=yogurtte-k3s-prod, container=content-service, instance=10.42.3.41:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-6c5fff897-scw7k, pool=HikariPool-1}` | 57 | 0 | 0 | 0 | **2026-07-29T07:26:00Z ~ 2026-07-29T07:42:00Z** |
| `hikaricp_connections_pending` | `{__name__=hikaricp_connections_pending, application=auth-service, cluster=yogurtte-k3s-prod, container=auth-service, instance=10.42.1.38:8090, job=auth-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=auth-service-855c75679d-pr892, pool=HikariPool-1, service=auth-service}` | 65 | 0 | 0 | 0 | **2026-07-29T07:26:00Z ~ 2026-07-29T07:42:00Z** |
| `hikaricp_connections_pending` | `{__name__=hikaricp_connections_pending, application=chat-service, cluster=yogurtte-k3s-prod, container=chat-service, instance=10.42.1.39:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-857c54dd97-w7bf7, pool=HikariPool-1}` | 57 | 0 | 0 | 0 | **2026-07-29T07:26:00Z ~ 2026-07-29T07:42:00Z** |
| `hikaricp_connections_pending` | `{__name__=hikaricp_connections_pending, application=content-service, cluster=yogurtte-k3s-prod, container=content-service, instance=10.42.1.35:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-6c5fff897-qnxk6, pool=HikariPool-1}` | 57 | 0 | 0 | 0 | **2026-07-29T07:26:00Z ~ 2026-07-29T07:42:00Z** |
| `hikaricp_connections_pending` | `{__name__=hikaricp_connections_pending, application=content-service, cluster=yogurtte-k3s-prod, container=content-service, instance=10.42.3.41:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-6c5fff897-scw7k, pool=HikariPool-1}` | 57 | 0 | 0 | 0 | **2026-07-29T07:26:00Z ~ 2026-07-29T07:42:00Z** |
| `rate(jvm_gc_pause_seconds_sum[5m])` | `{action=end of major GC, application=chat-service, cause=Allocation Failure, cluster=yogurtte-k3s-prod, container=chat-service, gc=MarkSweepCompact, instance=10.42.1.39:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-857c54dd97-w7bf7}` | 65 | 0 | 0 | 0 | **2026-07-29T07:26:00Z ~ 2026-07-29T07:42:00Z** |
| `rate(jvm_gc_pause_seconds_sum[5m])` | `{action=end of minor GC, application=auth-service, cause=G1 Evacuation Pause, cluster=yogurtte-k3s-prod, container=auth-service, gc=G1 Young Generation, instance=10.42.1.38:8090, job=auth-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=auth-service-855c75679d-pr892, service=auth-service}` | 65 | 0 | 0.000 | 0 | **2026-07-29T07:26:00Z ~ 2026-07-29T07:34:15Z, 2026-07-29T07:38:30Z ~ 2026-07-29T07:42:00Z** |
| `rate(jvm_gc_pause_seconds_sum[5m])` | `{action=end of minor GC, application=chat-service, cause=Allocation Failure, cluster=yogurtte-k3s-prod, container=chat-service, gc=Copy, instance=10.42.1.39:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-857c54dd97-w7bf7}` | 65 | 0.000 | 0.000 | 0.000 | — |
| `rate(jvm_gc_pause_seconds_sum[5m])` | `{action=end of minor GC, application=content-service, cause=Allocation Failure, cluster=yogurtte-k3s-prod, container=content-service, gc=Copy, instance=10.42.1.35:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-6c5fff897-qnxk6}` | 65 | 0 | 0.000 | 0.000 | **2026-07-29T07:26:00Z ~ 2026-07-29T07:31:00Z, 2026-07-29T07:33:15Z ~ 2026-07-29T07:39:00Z** |
| `rate(jvm_gc_pause_seconds_sum[5m])` | `{action=end of minor GC, application=content-service, cause=Allocation Failure, cluster=yogurtte-k3s-prod, container=content-service, gc=Copy, instance=10.42.3.41:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-6c5fff897-scw7k}` | 65 | 0 | 0.000 | 0 | **2026-07-29T07:26:00Z ~ 2026-07-29T07:31:45Z, 2026-07-29T07:36:00Z ~ 2026-07-29T07:42:00Z** |
| `up` | `{__name__=up, app=node-exporter, cluster=yogurtte-k3s-prod, component=metrics, container=node-exporter, instance=ip-172-31-40-241, job=integrations/node_exporter, k8s_cluster_name=yogurtte-k3s-prod, namespace=monitoring, pod=grafana-k8s-monitoring-node-exporter-cph5l, source=kubernetes, workload=DaemonSet/grafana-k8s-monitoring-node-exporter}` | 65 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, app=node-exporter, cluster=yogurtte-k3s-prod, component=metrics, container=node-exporter, instance=ip-172-31-45-39, job=integrations/node_exporter, k8s_cluster_name=yogurtte-k3s-prod, namespace=monitoring, pod=grafana-k8s-monitoring-node-exporter-9kvqt, source=kubernetes, workload=DaemonSet/grafana-k8s-monitoring-node-exporter}` | 65 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, cluster=yogurtte-k3s-prod, container=auth-service, instance=10.42.1.38:8090, job=auth-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=auth-service-855c75679d-pr892}` | 65 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, cluster=yogurtte-k3s-prod, container=chat-service, instance=10.42.1.39:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-857c54dd97-w7bf7}` | 57 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, cluster=yogurtte-k3s-prod, container=content-service, instance=10.42.1.35:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-6c5fff897-qnxk6}` | 57 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, cluster=yogurtte-k3s-prod, container=content-service, instance=10.42.3.41:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-6c5fff897-scw7k}` | 57 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, cluster=yogurtte-k3s-prod, instance=10.42.1.248:8080, job=integrations/kubernetes/kube-state-metrics, k8s_cluster_name=yogurtte-k3s-prod, source=kubernetes}` | 65 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, cluster=yogurtte-k3s-prod, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod}` | 65 | 1 | 1 | 1 | — |
| `mongodb_up` | `{__name__=mongodb_up, cluster=yogurtte-k3s-prod, instance=infra-server, job=mongodb, k8s_cluster_name=yogurtte-k3s-prod}` | 65 | 1 | 1 | 1 | — |
| `kafka_brokers` | `{__name__=kafka_brokers, cluster=yogurtte-k3s-prod, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod}` | 65 | 1 | 1 | 1 | — |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=chat-service-fcm-tokens, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=0, topic=user.fcm-tokens}` | 65 | 0 | 0 | 0 | **2026-07-29T07:26:00Z ~ 2026-07-29T07:42:00Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=chat-service-fcm-tokens, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=1, topic=user.fcm-tokens}` | 65 | 0 | 0 | 0 | **2026-07-29T07:26:00Z ~ 2026-07-29T07:42:00Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=chat-service-fcm-tokens, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=2, topic=user.fcm-tokens}` | 65 | 0 | 0 | 0 | **2026-07-29T07:26:00Z ~ 2026-07-29T07:42:00Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=chat-service-notification-settings, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=0, topic=user.notification-settings}` | 65 | 0 | 0 | 0 | **2026-07-29T07:26:00Z ~ 2026-07-29T07:42:00Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=chat-service-notification-settings, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=1, topic=user.notification-settings}` | 65 | 0 | 0 | 0 | **2026-07-29T07:26:00Z ~ 2026-07-29T07:42:00Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=chat-service-notification-settings, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=2, topic=user.notification-settings}` | 65 | 0 | 0 | 0 | **2026-07-29T07:26:00Z ~ 2026-07-29T07:42:00Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=db-writer, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=0, topic=chat.messages}` | 65 | 0 | 0 | 0 | **2026-07-29T07:26:00Z ~ 2026-07-29T07:42:00Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=db-writer, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=1, topic=chat.messages}` | 65 | 0 | 0 | 0 | **2026-07-29T07:26:00Z ~ 2026-07-29T07:42:00Z** |
| `websocket_active_users` | `{__name__=websocket_active_users, application=chat-service, cluster=yogurtte-k3s-prod, container=chat-service, instance=10.42.1.39:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-857c54dd97-w7bf7}` | 57 | 0 | 0 | 0 | **2026-07-29T07:26:00Z ~ 2026-07-29T07:42:00Z** |

값이 0이던 구간은 굵게 표시했다 — 프로세스가 사라져 시계열이 꺾인 것이 유일한 신호인 장애가 있다.

