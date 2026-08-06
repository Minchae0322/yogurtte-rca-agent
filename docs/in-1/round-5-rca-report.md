# RCA Report — `scan-1785990300`

| 항목 | 값 |
|---|---|
| 모드 | rca |
| 질문 | 최근 1시간 안에 문의가 몇 건 들어왔어요. ① 로그인이 느리다 ② 친구가 접속해 있는데 오프라인으로 보인다 ③ 피드에 작성자 이름이 이상하다 |
| 시각 | 2026-08-06T04:46:47.115634900Z |
| provider | claude-cli |
| model | `claude-opus-5` · turns 1 |
| prompt | `.\prompts\system-prompt.md` |
| tokens | in 88130 (cacheRead 23,449 · cacheCreate 64,679) / out 12268 · cost $0.9652 |
| elapsed | total 205905ms (tempo 3191 · loki 692 · mimir 1226 · assemble 189 · llm 192009) |

## 탐색 (Triage)

| 항목 | 값 |
|---|---|
| 시간창 해석 | 상대 표현 '최근 1시간' |
| 시간창 확신도 | EXACT |
| 스윕 창 | 2026-08-06T03:45:46.906009400Z ~ 2026-08-06T04:45:46.906009400Z |
| 좁힌 창 | 2026-08-06T04:25:00Z ~ 2026-08-06T04:45:00Z |
| 대상 | content-service, chat-service, auth-service |
| traceId | 6a740e7417e8b0b83aa26f5450e14d12 |
| 트레이스 후보 | 8건 |
| 장애 후보 | 4건 · 선택 INC-1, INC-2, INC-3 |
| 계획 파싱 | 성공 |
| 스윕 컨텍스트 | **후보만 — 원본 제외 (B)** |
| prompt | `.\prompts\triage-prompt.md` |
| tokens | in 33032 / out 2962 · cost $0.2088 |
| chars | 컨텍스트 2,739 + 프롬프트 1,399 = **4,138** |
| elapsed | survey 2977ms · llm 57125ms |

**선정 이유**: 질문의 세 증상 중 ②·③이 chat-service·content-service의 04:30~04:40 로그 급증 및 /feeds/scroll 16초 지연과 시각·리소스가 정확히 맞고, 이들이 같은 창에 동시 발생한 만큼 상류·하류를 함께 열어야 공통 원인을 볼 수 있다.

**근거**

- INC-2: content-service ERROR/WARN 79건(04:30~04:35) + 17건(04:35~04:40) — 증상 ③ 피드 작성자 이름 이상과 서비스·시각 일치
- INC-3: content-service http get /feeds/scroll 16,409ms·16,147ms (04:32:52~04:33:30, TEMPO 시각 정확) — 에러가 아니라 지연 채널에만 걸린 전형적 하류 타임아웃 대기
- INC-1: chat-service ERROR/WARN 28건(04:30~04:35) — 증상 ② '접속 중인데 오프라인' 발생 시각과 겹침
- 무신호도 근거로 씀: max_over_time(websocket_active_users[5m]) 이상 0건 — 실제 접속 세션은 살아 있는데 표시만 오프라인, 즉 presence 조회/조인 실패 쪽 가설이 강해짐
- 무신호도 근거로 씀: up·mongodb_up·kafka_brokers·kafka_consumergroup_lag 모두 이상 0건 — 인프라/브로커/DB 다운, 컨슈머 적체는 원인에서 밀림
- auth-service는 후보가 0건인데 증상 ①(로그인 느림)이 접수됨 — 세 증상이 04:30~04:40 한 창에 몰린 것과 합치면 auth 계열 사용자 조회가 공통 하류일 가능성이 있어 조사 대상에 포함

**스윕이 찾은 트레이스** (고른 것은 6a740e7417e8b0b83aa26f5450e14d12)

| traceId | 채널 | root service | root span | ms |
|---|---|---|---|---:|
| `6a740ef437378b2ba59b137b2a6f7349` | error | content-service | task battle-deadline-notification-scheduler.notify | 2000 |
| `6a740ef40b0c7d258e82b43ce573b9c0` | error | content-service | task battle-deadline-notification-scheduler.notify | 2000 |
| `6a740eb8723600d08b6755dfc53d7d12` | error | content-service | task battle-deadline-notification-scheduler.notify | 2010 |
| `6a740eb80716747faf6aa754773de070` | error | content-service | task battle-deadline-notification-scheduler.notify | 2002 |
| `6a740e7ce4ae0784411add206af99250` | error | content-service | task battle-deadline-notification-scheduler.notify | 2000 |
| `6a740e7c2620ba4220a89df5f5dcffcd` | error | content-service | task battle-deadline-notification-scheduler.notify | 2003 |
| `6a740e8a1120fa25d0b130c650332129` | slow | content-service | http get /feeds/scroll | 16147 |
| `6a740e7417e8b0b83aa26f5450e14d12` ←선택 | slow | content-service | http get /feeds/scroll | 16409 |

**장애 후보** (코드가 신호를 묶은 것 · 창은 여기서 계산됨)

## INC-1  chat-service  |  ERROR/WARN
- 구간: 2026-08-06T04:30:00Z ~ 2026-08-06T04:40:00Z  (LOKI · 집계 해상도만큼 흐림)
- ERROR/WARN 28건 (2026-08-06T04:30:00Z ~ 2026-08-06T04:35:00Z)
- ERROR/WARN 7건 (2026-08-06T04:35:00Z ~ 2026-08-06T04:40:00Z)
- 같은 시각의 다른 후보: INC-2, INC-3, INC-4  (인과 여부는 판단하지 않았다)

## INC-2  content-service  |  ERROR/WARN
- 구간: 2026-08-06T04:30:00Z ~ 2026-08-06T04:40:00Z  (LOKI · 집계 해상도만큼 흐림)
- ERROR/WARN 79건 (2026-08-06T04:30:00Z ~ 2026-08-06T04:35:00Z)
- ERROR/WARN 17건 (2026-08-06T04:35:00Z ~ 2026-08-06T04:40:00Z)
- 같은 시각의 다른 후보: INC-1, INC-3, INC-4  (인과 여부는 판단하지 않았다)

## INC-3  content-service  |  http get /feeds/scroll
- 구간: 2026-08-06T04:32:52.051686Z ~ 2026-08-06T04:33:30.176553Z  (TEMPO · 시각 정확)
- content-service http get /feeds/scroll 16,409ms (slow 채널)
- content-service http get /feeds/scroll 16,147ms (slow 채널)
- traceId: 6a740e7417e8b0b83aa26f5450e14d12, 6a740e8a1120fa25d0b130c650332129
- 같은 시각의 다른 후보: INC-1, INC-2, INC-4  (인과 여부는 판단하지 않았다)

## INC-4  content-service  |  task battle-deadline-notification-scheduler.notify
- 구간: 2026-08-06T04:33:00.000283Z ~ 2026-08-06T04:35:02.015374Z  (TEMPO · 시각 정확)
- content-service task battle-deadline-notification-scheduler.notify 2,003ms (error 채널)
- content-service task battle-deadline-notification-scheduler.notify 2,000ms (error 채널)
- content-service task battle-deadline-notification-scheduler.notify 2,002ms (error 채널)
- content-service task battle-deadline-notification-scheduler.notify 2,010ms (error 채널)
- content-service task battle-deadline-notification-scheduler.notify 2,000ms (error 채널)
- content-service task battle-deadline-notification-scheduler.notify 2,000ms (error 채널)
- traceId: 6a740e7c2620ba4220a89df5f5dcffcd, 6a740e7ce4ae0784411add206af99250, 6a740eb80716747faf6aa754773de070, 6a740eb8723600d08b6755dfc53d7d12, 6a740ef40b0c7d258e82b43ce573b9c0, 6a740ef437378b2ba59b137b2a6f7349
- 같은 시각의 다른 후보: INC-1, INC-2, INC-3  (인과 여부는 판단하지 않았다)

**기각한 후보**

- INC-4 — battle-deadline-notification-scheduler는 사용자 요청 경로가 아닌 백그라운드 배치라 접수된 세 증상 중 어느 것도 설명하지 못하고, 2,000ms 전후로 고정된 지속시간은 명백한 타임아웃 지문이지만 그 구간(04:33~04:35)이 INC-2·INC-3 창에 이미 완전히 포함돼 별도로 고르지 않아도 증거를 잃지 않는다

**보정 기록**

- 창을 후보 [INC-1, INC-2, INC-3] 의 신호 시각에서 계산했다 (2026-08-06T04:25:00Z ~ 2026-08-06T04:45:00Z)

## 수집 범위 (Coverage)

- **window**: 2026-08-06T04:25:00Z ~ 2026-08-06T04:45:00Z (1200s)
- **trace**: 65,971B / 64 spans
- **창 안 후보 트레이스**: 10건 / 65,971B
- **logs**: errwarn=149,710B · traceId=11,183B
- **metrics**: 8 수집 / 139,415B, 누락 [sum(rate(http_server_requests_seconds_count{application="content-service", status="401"}[1m]))]
- **context**: 105,246 chars (+ 시스템 프롬프트 805 chars)

### 토큰 축 (개선 지표)

| 단계 | 총 in | chars | 컨텍스트 토큰 |
|---|---:|---:|---:|
| 탐색 | 33,032 | 4,138 | 3,631 |
| 분석 | 88,130 | 106,051 | 58,729 |
| **합계** | **121,162** | | **62,360** |

- **overheadTokens 29,401 tok — 이 회차에 실측**(1자 프롬프트 프로브, 같은 명령·모델·샌드박스).
  호출마다 한 번씩 얹히므로 단계별로 뺀다. **다른 날 상수를 쓰지 않았다 = `█ 실측`.**
- contextTokens (count_tokens API): 측정 안 됨 — 구독 CLI 경로엔 API 키가 없다
- 비용은 **API 환산 추정치**다. 구독 계정으로 호출하므로 실제 청구액이 아니다.

## 수집 실패/누락

- Metric 'sum(rate(http_server_requests_seconds_count{application="content-service", status="401"}[1m]))' returned no series in this window; skipped.

---

## 1. 원인 후보 랭킹 (최대 3개)

1. **Redis(172.31.46.124:6379) 다운 — 연결 거부 상태**. content·chat 양쪽에서 Lettuce 재연결이 전부 거부되고, 모든 Redis 커맨드가 2초 타임아웃으로 만료된다.
2. **캐시 실패 경로의 직렬 2초 타임아웃 누적 (증폭 요인)**. `/feeds/scroll` 한 건에서 사용자 4명 × (조회+저장) = 8회 × 2초 = 16초가 그대로 응답 시간이 되고, 그 전체가 하나의 JDBC 커넥션/트랜잭션 안에서 실행된다.
3. **battle-deadline-notification 스케줄러 전면 실패** (1의 직접 피해. 별도 원인이 아니라 별도 피해면이라 분리해 적는다).

문의 ①은 2번으로 부분 설명 가능(피드 로딩 16초), ②는 근거 약함, ③은 **데이터 부족**이다. 아래에 각각 적는다.

---

## 2. 후보별 근거

### 후보 1 — Redis 인스턴스 다운 (연결 거부)

**근거**
- content-service 양 파드 + chat-service 파드에서 동일 로그: `i.l.core.protocol.ConnectionWatchdog : Cannot reconnect to [172.31.46.124/<unresolved>:6379]: finishConnect(..) failed: Connection refused: /172.31.46.124:6379` — 파드별 **x4회, 04:32:51 ~ 04:33:00 UTC, 평균 3.0초 간격**.
- `o.s.b.a.d.r.RedisReactiveHealthIndicator - Redis health check failed` — content sp24n 04:32:54.269, content v2pw9 04:32:58.896, chat 04:32:54.322.
- 예외 원문: `Caused by: io.lettuce.core.RedisCommandTimeoutException: Command timed out after 2 second(s)` / chat 쪽은 `INFO. Command timed out after 2 second(s)` (헬스체크의 INFO 커맨드).
- 3개 서비스·3개 파드·2개 노드(ip-172-31-45-39, ip-172-31-40-241)에서 동시에 같은 IP로만 실패 → 클라이언트 측이 아니라 대상 인스턴스 문제.

**확신도: 높음**

**반증 데이터**
- `up{job=redis, instance=infra-server}` = **전 구간 1 (변화 없음)**. Redis 익스포터 타깃은 20분 내내 살아 있다. 즉 익스포터가 보는 Redis와 앱이 붙는 172.31.46.124:6379가 같은 인스턴스인지 확인되지 않았다(`redis_up`은 수집되지 않음). 이 모순 때문에 "Redis 프로세스 전체 다운"이 아니라 "앱이 붙는 엔드포인트가 거부 상태"까지만 확정된다.
- Kafka·MongoDB·MySQL 계열 지표는 전부 정상(`kafka_brokers`=1, `mongodb_up`=1, 모든 consumergroup lag 0/-1 고정) → 인프라 광역 장애는 아님.

**대기 구간 판정**
- Redis 커맨드 대기: 실측 2.000~2.010초 vs 상한 **2초**(`Command timed out after 2 second(s)`) → **만료**. 최종 상태 **실패**. 요청 내 자체 재시도 흔적 없음.
- Lettuce 재연결 대기: 3.0초 간격 4회 재시도, 전부 `Connection refused` → **실패, 재시도 지속 중**(수집 창 끝까지 성공 로그 없음).

---

### 후보 2 — 캐시 실패 경로의 직렬 2초 타임아웃 누적 (지연 증폭)

**근거** — trace `6a740e7417e8b0b83aa26f5450e14d12` 전체 시간을 초 단위로 메울 수 있다.
- 루트 `http get /feeds/scroll` = **16409.58ms, status 200, outcome SUCCESS**. 로그 확증: `RequestLoggingFilter - [HTTP-SLOW] GET /api/feeds/scroll 200 - 16409ms`.
- `connection`(HikariPool-1) span = **16406.8ms**, events `acquired`(04:32:52.054) → `commit`(04:33:08.456). 즉 커넥션 점유 16.4초.
- 실제 JDBC 작업 총합은 ~0.4초뿐(개별 query 1.6~2.3ms, result-set 0.2~6.0ms).
- 빈 구간 2개가 정확히 Redis 타임아웃으로 채워진다:
  - 04:32:52.072 → 04:33:00.166 (**8.09초**) = `UserCacheStore - [user-cache] Redis 캐시 조회 실패: cacheKey=user:info:1` … `user:info:9` **x4회 · 04:32:54.076 ~ 04:33:00.164 · 평균 2.0초 간격**.
  - 04:33:00.392 → 04:33:08.425 (**8.03초**) = `Redis 캐시 저장 실패: userId=1`(04:33:02.403) → `userId=3`(04:33:04.412) → `userId=9`(04:33:08.420), **2.0초 간격 4회**.
  - 4명 × (조회 1 + 저장 1) × 2초 = **16초**.
- 스택 원문이 경로를 확정한다: `UserCacheStore.getCachedValue(UserCacheStore.java:49)` ← `ExternalUserInfoService.getUserInfos(:110)` ← `FeedService.toListView(FeedService.java:138)` ← `FeedService.getFeedsWithCursor(:86)`, 저장 쪽은 `UserCacheStore.cacheUserInfo(:75)` ← `ExternalUserApiClient.fetchAndCacheUserInfos(:50)` ← `ExternalUserInfoService.getUserInfos(:126)`.
- 두 번째 파드에서 동일 패턴 재현: trace `6a740e8a1120fa25d0b130c650332129`, 16147.67ms, 조회 실패 x4(04:33:16.054~04:33:22.063) + 저장 실패 x4(04:33:24.109~04:33:30.121), `[HTTP-SLOW] GET /api/feeds/scroll 200 - 16147ms`.

**확신도: 높음** (지연의 시간 배분이 로그 타임스탬프와 1:1로 맞는다)

**반증 데이터**
- `hikaricp_connections_active` = **전 구간 0**, `hikaricp_connections_pending` = **전 구간 0** (auth/chat/content 전 파드). 16.4초 커넥션 점유 span과 배치된다 — 스크레이프 해상도상 놓쳤거나 계측 대상이 다른 풀일 수 있다. 어느 쪽이든 **커넥션 풀 고갈(대기열) 발생 증거는 없다**. 단, content 두 파드는 04:33:30~04:36:45 / 04:33:45~04:37:00 구간이 **결측**이라 피크 직후는 판정 불가.
- auth-service는 정상: `/external/users` 서버 span 190.5ms / 36.3ms, status 200, `hikaricp_connections_active`=0, GC pause rate 최대 3.3e-5. → 하류 서비스가 원인은 아니다.

**대기 구간 판정**
- Redis 조회·저장 대기 8회: 각 2초 상한 **만료**, 각 **실패**. 조회 실패 후 폴백은 **성공**(auth-service `http get` 200, `UserExternalController - [external-api] 외부 사용자 목록 조회 - userIds: [1, 3, 7, 9]`). 저장 실패분은 **폐기**(캐시에 안 남음, 다음 요청도 같은 비용을 다시 지불).
- `/feeds/scroll` 전체 16.4초: 게이트웨이/클라이언트 타임아웃 설정값 미확보 → **만료 여부 판정 불가**. 서버 측 최종 상태는 **성공(200)**.
- JDBC 커넥션 16.4초 점유: HikariCP `connectionTimeout`/`maxLifetime` 설정값 미확보 → **만료 여부 판정 불가**. 트랜잭션 최종 상태는 **성공**(`commit` 이벤트 존재).

---

### 후보 3 — battle-deadline-notification 스케줄러 전면 실패

**근거**
- 동일 span이 60초 주기로 6건, 양 파드 모두 실패: `task battle-deadline-notification-scheduler.notify`, `code.namespace=com.example.toycontent.app.scheduler.BattleDeadlineNotificationScheduler`, `code.function=notifyEnd`, `exception=QueryTimeoutException`, `error=Redis command timed out`, `outcome=ERROR`, `status.code=STATUS_CODE_ERROR`.
- 시각/소요: 04:33:00.000(2003.6ms)·04:33:00.026(2000.9ms) / 04:34:00.000(2002.4ms)·04:34:00.012(2010.4ms) / 04:35:00.001(2000.9ms)·04:35:00.015(2000.8ms).
- 로그 확증: `o.s.s.s.TaskUtils$LoggingErrorHandler - Unexpected error occurred in scheduled task` (v2pw9 04:33:02.008, sp24n 04:33:02.053), 스택 `… 34 frames (org.springframework, net.javacrumbs, io.micrometer)` → **net.javacrumbs = ShedLock**, 즉 분산 락 획득 단계에서 Redis 타임아웃.

**확신도: 높음** (실패 사실 자체는 확정. "락 단계"라는 해석은 프레임 패키지명 기반이라 중간)

**반증 데이터**: 없음.

**대기 구간 판정**
- 실측 2000.8~2010.4ms vs Redis 상한 **2초** → **만료**. 각 실행의 최종 상태 **실패(ERROR) 및 폐기** — 해당 주기의 알림 발송은 완료되지 않았다. 60초 주기 재실행은 관측되나 **3주기 연속 전부 실패**했다.

---

### 문의별 대응 (①②③)

**① 로그인이 느리다 — 부분 설명, 확신도 낮음**
- 로그인 엔드포인트(auth-service 인증 경로)의 트레이스·슬로우 로그가 **수집되지 않았다**. 수집된 auth-service span은 `/external/users` 2건뿐이고 둘 다 정상(190ms/36ms).
- 요청한 `http_server_requests_seconds_count{application="content-service", status="401"}` 메트릭은 **수집 실패(no series)** — 인증 실패/재인증 폭증 여부를 확인할 수 없다.
- 설명 가능한 대안: 로그인 직후 첫 화면이 `/feeds/scroll`이면 사용자가 체감한 "로그인 16초"는 후보 2다. **추정이며 근거 없음** → 확정하려면 로그인 API의 트레이스가 필요.

**② 친구가 오프라인으로 보인다 — 데이터 부족, 확신도 낮음**
- 사실: `websocket_active_users{chat-service}` = **전 구간 0**, 04:34:00~04:36:15 **결측**. chat-service는 Redis 연결 거부 + 헬스체크 실패 상태.
- 그러나 이 게이지는 장애 이전(04:25~04:32)에도 0이라 **변화가 없다** → 장애로 프레즌스가 깨졌다는 증거가 되지 못한다. 프레즌스 저장소가 Redis라는 관측 근거도 없다(호출 그래프에 chat 관련 엣지 없음).
- 보조 관측: chat/content 3개 파드 모두 `up` 메트릭이 **결측**되는 2~3분 구간이 인시던트 직후에 있다(chat 04:34:00~04:36:15, content sp24n 04:33:30~04:36:45, content v2pw9 04:33:45~04:37:00). `up`이 0으로 떨어진 게 아니라 **타깃 자체가 사라졌다** → 파드 재시작 가능성. 재시작이면 WebSocket 세션 유실로 ②가 설명되지만, **재시작 여부는 미확보 데이터**다.

**③ 피드 작성자 이름이 이상하다 — 데이터 부족**
- 캐시 조회는 전부 실패했지만 **폴백은 성공했다**: auth-service 응답 200, `userIds: [1, 3, 7, 9]` 조회 로그 존재. 즉 관측 데이터만 보면 이름 데이터는 정상적으로 확보됐다 — 이는 "Redis 장애 = 이름 오류"에 대한 **반증**이다.
- 다만 피드 11건(`tb_feed` row-count 11)에 대해 auth 조회는 4명뿐이고, `tb_user_reward` 조회도 파라미터 11개에 row-count 4다. 나머지 사용자의 이름을 어디서 채웠는지는 관측되지 않는다.
- **판정 불가**. 응답 본문 샘플과 `UserCacheStore`/`ExternalUserInfoService` 예외 폴백 코드 경로 없이는 결론 낼 수 없다.

---

## 3. 권장 다음 조치

### 이미 발생한 피해 — 복구 가능성

| 피해 | 복구 가능 여부 |
|---|---|
| `/feeds/scroll` 16.4s / 16.1s 응답 | **복구 대상 아님**. 이미 200으로 완료됐고 데이터 손실 없음. |
| battle-deadline 알림 04:33/04:34/04:35 UTC 3주기 미발송 (양 파드) | **부분 복구 가능**. Redis 복구 후 스케줄러가 다음 주기에 자동 재실행되며, `notifyEnd`가 "미발송 배틀"을 상태 기반으로 조회한다면 지연 발송으로 자동 회복된다. 시각 기반(“지금 마감된 것만”)이면 자동 회복 불가 → 해당 3분간 마감된 배틀을 DB에서 뽑아 수동 재발송 필요. **어느 쪽인지 판단할 코드/데이터 근거가 없어 판정 불가** — `BattleDeadlineNotificationScheduler.notifyEnd` 구현과 알림 발송 이력 테이블 확인이 선행되어야 한다. |
| 사용자 캐시 미저장(`userId=1,3,7,9`) | **자동 회복**. Redis 복구 후 첫 요청에서 재적재된다. 별도 조치 불필요. |
| Redis에만 있던 휘발성 데이터(세션/프레즌스 등) | **판정 불가**. 어떤 데이터가 Redis에만 존재했는지 관측 근거 없음. |

### 재발 방지
1. **Redis 엔드포인트 복구가 선행**. 172.31.46.124:6379가 왜 거부하는지 확인(프로세스 다운 / 포트 바인딩 / NetworkPolicy). `up{job=redis}`=1과 모순되므로 **익스포터가 실제로 이 인스턴스를 보는지** 먼저 검증 — 지금 모니터링은 앱이 쓰는 Redis의 다운을 감지하지 못했다.
2. **캐시 호출에 서킷 브레이커/실패 캐싱 도입**. 현 구조는 실패한 Redis에 요청당 8회 재접근해 16초를 소비한다. 캐시는 옵셔널 의존성이므로 연속 실패 시 즉시 우회해야 한다(2초 × N 직렬 누적이 핵심 증폭).
3. **Redis 커맨드 타임아웃 2초는 캐시 용도로 과도**. 200~300ms급으로 낮추면 최악 지연이 16초 → 2초 미만으로 떨어진다.
4. **캐시/외부 HTTP 호출을 DB 트랜잭션 밖으로 이동**. `connection` span이 16.4초 열려 있고 그 안에서 Redis·auth-service 호출이 일어난다. 동시 요청이 늘면 풀 고갈로 전면 장애가 된다(이번엔 트래픽이 낮아 표면화되지 않음).
5. **알림 스케줄러 실패 알람**. 3주기 연속 `STATUS_CODE_ERROR`가 났는데 인지 경로는 사용자 문의였다.

### 복구 확인
- `redis-cli -h 172.31.46.124 -p 6379 ping` 응답, 그리고 `ConnectionWatchdog` WARN이 **더 이상 발생하지 않을 것**.
- `RedisReactiveHealthIndicator - Redis health check failed`가 사라지고 `/actuator/health` Redis 컴포넌트 UP.
- `[HTTP-SLOW] GET /api/feeds/scroll` 로그 소멸, 해당 엔드포인트 p95가 1초 미만(정상 시 실제 작업량은 ~0.4초).
- `task battle-deadline-notification-scheduler.notify` span이 `outcome=SUCCESS`로 전환(다음 60초 주기에서 확인).
- content/chat 파드의 `up`·`hikaricp_*` 메트릭 결측 구간이 더 이상 생기지 않는지 확인(재시작 여부 판별 겸용).

### 추가 수집 필요 (현 결론의 확신도를 낮추는 공백)
- **로그인 엔드포인트 트레이스/latency** (①의 직접 근거 부재)
- **파드 restart count / 이벤트** — 3개 파드의 04:33:30~04:37:00 메트릭 결측 원인
- **피드 응답 본문 샘플 + `UserCacheStore` 예외 폴백 코드** (③ 판정 불가 해소)
- **chat-service 프레즌스 저장소 확인 + 장애 전 `websocket_active_users` 베이스라인(04:25 이전)** (② 판정 불가 해소)
- **`redis_up` 및 Redis 익스포터의 실제 타깃 주소** (`up`=1 모순 해소)
- **HikariCP `connectionTimeout`/`maxLifetime`, 게이트웨이 HTTP 타임아웃 설정값** (만료 판정 불가 구간 해소)
- 수집 실패한 `http_server_requests_seconds_count{application="content-service", status="401"}` 재조회

---

## 관측 증거 (Evidence)

> 이 조사가 실제로 본 값이다. 원본 응답 전체는 `reports/raw/scan-1785990300-*.json`에 있다.

### 호출 그래프 (트레이스에서 추출)

```
content-service --jdbc--> mysql/content (HikariPool-1)  38회  최대 16406.8ms
    events: acquired, commit
content-service --service--> auth-service  4회  최대 226.0ms
```

### span (duration 상위 15 / 전체 64)

| ms | service | span | 시작 |
|---:|---|---|---|
| 16409.58 | content-service | `http get /feeds/scroll` | 2026-08-06T04:32:52.051686Z |
| 16407.83 | content-service | `secured request` | 2026-08-06T04:32:52.052099Z |
| 16406.80 | content-service | `connection` | 2026-08-06T04:32:52.052909Z |
| 16147.67 | content-service | `http get /feeds/scroll` | 2026-08-06T04:33:14.029553Z |
| 16145.87 | content-service | `secured request` | 2026-08-06T04:33:14.029983Z |
| 16144.26 | content-service | `connection` | 2026-08-06T04:33:14.031404Z |
| 2010.39 | content-service | `task battle-deadline-notification-scheduler.notify` | 2026-08-06T04:34:00.012779Z |
| 2003.62 | content-service | `task battle-deadline-notification-scheduler.notify` | 2026-08-06T04:33:00.000283Z |
| 2002.42 | content-service | `task battle-deadline-notification-scheduler.notify` | 2026-08-06T04:34:00.000730Z |
| 2000.96 | content-service | `task battle-deadline-notification-scheduler.notify` | 2026-08-06T04:33:00.026871Z |
| 2000.93 | content-service | `task battle-deadline-notification-scheduler.notify` | 2026-08-06T04:35:00.001161Z |
| 2000.83 | content-service | `task battle-deadline-notification-scheduler.notify` | 2026-08-06T04:35:00.015374Z |
| 226.00 | content-service | `http get` | 2026-08-06T04:33:00.166294Z |
| 190.55 | auth-service | `http get /external/users` | 2026-08-06T04:33:00.201065Z |
| 189.38 | auth-service | `secured request` | 2026-08-06T04:33:00.201795Z |

### 로그 원문 (60 / 전체 1,020줄)

전체가 상한을 넘어 ERROR/WARN 줄을 우선 발췌했다. 나머지는 원본 파일에 있다.

```
2026-08-06T04:32:51.624990403Z  [chat-service]  [2m2026-08-06T13:32:51.624+09:00[0;39m [33m WARN [traceId=NONE,spanId=NONE,userId=NONE][0;39m [35m7[0;39m [2m--- [chat-service] [llEventLoop-6-2] [                                                 ] [0;39m[36mi.l.core.protocol.ConnectionWatchdog    [0;39m [2m:[0;39m Cannot reconnect to [172.31.46.124/<unresolved>:6379]: finishConnect(..) failed: Connection refused: /172.31.46.124:6379
2026-08-06T04:32:51.625665622Z  [chat-service]  [2m2026-08-06T13:32:51.625+09:00[0;39m [33m WARN [traceId=NONE,spanId=NONE,userId=NONE][0;39m [35m7[0;39m [2m--- [chat-service] [llEventLoop-6-1] [                                                 ] [0;39m[36mi.l.core.protocol.ConnectionWatchdog    [0;39m [2m:[0;39m Cannot reconnect to [172.31.46.124/<unresolved>:6379]: finishConnect(..) failed: Connection refused: /172.31.46.124:6379
2026-08-06T04:32:54.076768228Z  [content-service]  2026-08-06 13:32:54.072 [http-nio-8082-exec-5] ERROR [traceId=6a740e7417e8b0b83aa26f5450e14d12,spanId=f8c1f3b4bf5554c8,userId=NONE] c.e.t.e.user.service.UserCacheStore - [user-cache] Redis 캐시 조회 실패: cacheKey=user:info:1
2026-08-06T04:32:54.076790904Z  [content-service]  org.springframework.dao.QueryTimeoutException: Redis command timed out
2026-08-06T04:32:54.076795452Z  [content-service]  at org.springframework.data.redis.connection.lettuce.LettuceExceptionConverter.convert(LettuceExceptionConverter.java:68)
2026-08-06T04:32:54.076799261Z  [content-service]  at org.springframework.data.redis.connection.lettuce.LettuceExceptionConverter.convert(LettuceExceptionConverter.java:41)
2026-08-06T04:32:54.076803703Z  [content-service]  at org.springframework.data.redis.PassThroughExceptionTranslationStrategy.translate(PassThroughExceptionTranslationStrategy.java:40)
2026-08-06T04:32:54.076807423Z  [content-service]  at org.springframework.data.redis.FallbackExceptionTranslationStrategy.translate(FallbackExceptionTranslationStrategy.java:38)
2026-08-06T04:32:54.076810987Z  [content-service]  at org.springframework.data.redis.connection.lettuce.LettuceConnection.convertLettuceAccessException(LettuceConnection.java:310)
2026-08-06T04:32:54.077053943Z  [content-service]  at org.springframework.security.web.access.ExceptionTranslationFilter.doFilter(ExceptionTranslationFilter.java:126)
2026-08-06T04:32:54.077055999Z  [content-service]  at org.springframework.security.web.access.ExceptionTranslationFilter.doFilter(ExceptionTranslationFilter.java:120)
2026-08-06T04:32:54.077341011Z  [content-service]  Caused by: io.lettuce.core.RedisCommandTimeoutException: Command timed out after 2 second(s)
2026-08-06T04:32:54.077343427Z  [content-service]  at io.lettuce.core.internal.ExceptionFactory.createTimeoutException(ExceptionFactory.java:63)
2026-08-06T04:32:54.269738674Z  [content-service]  org.springframework.dao.QueryTimeoutException: Redis command timed out
2026-08-06T04:32:54.269742119Z  [content-service]  at org.springframework.data.redis.connection.lettuce.LettuceExceptionConverter.convert(LettuceExceptionConverter.java:68)
2026-08-06T04:32:54.269745196Z  [content-service]  at org.springframework.data.redis.connection.lettuce.LettuceReactiveRedisConnection.lambda$translateException$0(LettuceReactiveRedisConnection.java:242)
2026-08-06T04:32:54.269782628Z  [content-service]  at io.lettuce.core.protocol.CommandWrapper.completeExceptionally(CommandWrapper.java:132)
2026-08-06T04:32:54.269806594Z  [content-service]  Caused by: io.lettuce.core.RedisCommandTimeoutException: Command timed out after 2 second(s)
2026-08-06T04:32:54.269808991Z  [content-service]  at io.lettuce.core.internal.ExceptionFactory.createTimeoutException(ExceptionFactory.java:63)
2026-08-06T04:32:54.323880577Z  [chat-service]  [2m2026-08-06T13:32:54.322+09:00[0;39m [33m WARN [traceId=NONE,spanId=NONE,userId=NONE][0;39m [35m7[0;39m [2m--- [chat-service] [xecutorLoop-3-2] [                                                 ] [0;39m[36mo.s.b.a.d.r.RedisReactiveHealthIndicator[0;39m [2m:[0;39m Redis health check failed
2026-08-06T04:32:54.323910718Z  [chat-service]  org.springframework.dao.QueryTimeoutException: Redis command timed out
2026-08-06T04:32:54.323913921Z  [chat-service]  at org.springframework.data.redis.connection.lettuce.LettuceExceptionConverter.convert(LettuceExceptionConverter.java:68) ~[spring-data-redis-3.5.1.jar!/:3.5.1]
2026-08-06T04:32:54.323916869Z  [chat-service]  at org.springframework.data.redis.connection.lettuce.LettuceReactiveRedisConnection.lambda$translateException$0(LettuceReactiveRedisConnection.java:242) ~[spring-data-redis-3.5.1.jar!/:3.5.1]
2026-08-06T04:32:54.323965610Z  [chat-service]  at io.lettuce.core.protocol.CommandWrapper.completeExceptionally(CommandWrapper.java:132) ~[lettuce-core-6.6.0.RELEASE.jar!/:6.6.0.RELEASE/643bd47]
2026-08-06T04:32:54.323987961Z  [chat-service]  Caused by: io.lettuce.core.RedisCommandTimeoutException: INFO. Command timed out after 2 second(s)
2026-08-06T04:32:54.323990130Z  [chat-service]  at io.lettuce.core.internal.ExceptionFactory.createTimeoutException(ExceptionFactory.java:75) ~[lettuce-core-6.6.0.RELEASE.jar!/:6.6.0.RELEASE/643bd47]
2026-08-06T04:32:56.077391159Z  [content-service]  2026-08-06 13:32:56.075 [http-nio-8082-exec-5] ERROR [traceId=6a740e7417e8b0b83aa26f5450e14d12,spanId=f8c1f3b4bf5554c8,userId=NONE] c.e.t.e.user.service.UserCacheStore - [user-cache] Redis 캐시 조회 실패: cacheKey=user:info:3
2026-08-06T04:32:56.077422485Z  [content-service]  org.springframework.dao.QueryTimeoutException: Redis command timed out
2026-08-06T04:32:56.077426259Z  [content-service]  at org.springframework.data.redis.connection.lettuce.LettuceExceptionConverter.convert(LettuceExceptionConverter.java:68)
2026-08-06T04:32:56.077429103Z  [content-service]  at org.springframework.data.redis.connection.lettuce.LettuceExceptionConverter.convert(LettuceExceptionConverter.java:41)
2026-08-06T04:32:56.077432421Z  [content-service]  at org.springframework.data.redis.PassThroughExceptionTranslationStrategy.translate(PassThroughExceptionTranslationStrategy.java:40)
2026-08-06T04:32:56.077435439Z  [content-service]  at org.springframework.data.redis.FallbackExceptionTranslationStrategy.translate(FallbackExceptionTranslationStrategy.java:38)
2026-08-06T04:32:56.077438552Z  [content-service]  at org.springframework.data.redis.connection.lettuce.LettuceConnection.convertLettuceAccessException(LettuceConnection.java:310)
2026-08-06T04:32:56.077731602Z  [content-service]  at org.springframework.security.web.access.ExceptionTranslationFilter.doFilter(ExceptionTranslationFilter.java:126)
2026-08-06T04:32:56.077734291Z  [content-service]  at org.springframework.security.web.access.ExceptionTranslationFilter.doFilter(ExceptionTranslationFilter.java:120)
2026-08-06T04:32:56.078067490Z  [content-service]  Caused by: io.lettuce.core.RedisCommandTimeoutException: Command timed out after 2 second(s)
2026-08-06T04:32:56.078070205Z  [content-service]  at io.lettuce.core.internal.ExceptionFactory.createTimeoutException(ExceptionFactory.java:63)
2026-08-06T04:32:58.080823016Z  [content-service]  2026-08-06 13:32:58.079 [http-nio-8082-exec-5] ERROR [traceId=6a740e7417e8b0b83aa26f5450e14d12,spanId=f8c1f3b4bf5554c8,userId=NONE] c.e.t.e.user.service.UserCacheStore - [user-cache] Redis 캐시 조회 실패: cacheKey=user:info:7
2026-08-06T04:32:58.080851649Z  [content-service]  org.springframework.dao.QueryTimeoutException: Redis command timed out
2026-08-06T04:32:58.080856055Z  [content-service]  at org.springframework.data.redis.connection.lettuce.LettuceExceptionConverter.convert(LettuceExceptionConverter.java:68)
2026-08-06T04:32:58.080858889Z  [content-service]  at org.springframework.data.redis.connection.lettuce.LettuceExceptionConverter.convert(LettuceExceptionConverter.java:41)
2026-08-06T04:32:58.080862494Z  [content-service]  at org.springframework.data.redis.PassThroughExceptionTranslationStrategy.translate(PassThroughExceptionTranslationStrategy.java:40)
2026-08-06T04:32:58.080865422Z  [content-service]  at org.springframework.data.redis.FallbackExceptionTranslationStrategy.translate(FallbackExceptionTranslationStrategy.java:38)
2026-08-06T04:32:58.080868130Z  [content-service]  at org.springframework.data.redis.connection.lettuce.LettuceConnection.convertLettuceAccessException(LettuceConnection.java:310)
2026-08-06T04:32:58.081114876Z  [content-service]  at org.springframework.security.web.access.ExceptionTranslationFilter.doFilter(ExceptionTranslationFilter.java:126)
2026-08-06T04:32:58.081117312Z  [content-service]  at org.springframework.security.web.access.ExceptionTranslationFilter.doFilter(ExceptionTranslationFilter.java:120)
2026-08-06T04:32:58.081439429Z  [content-service]  Caused by: io.lettuce.core.RedisCommandTimeoutException: Command timed out after 2 second(s)
2026-08-06T04:32:58.081442419Z  [content-service]  at io.lettuce.core.internal.ExceptionFactory.createTimeoutException(ExceptionFactory.java:63)
2026-08-06T04:33:00.164766092Z  [content-service]  2026-08-06 13:33:00.088 [http-nio-8082-exec-5] ERROR [traceId=6a740e7417e8b0b83aa26f5450e14d12,spanId=f8c1f3b4bf5554c8,userId=NONE] c.e.t.e.user.service.UserCacheStore - [user-cache] Redis 캐시 조회 실패: cacheKey=user:info:9
2026-08-06T04:33:00.164818848Z  [content-service]  org.springframework.dao.QueryTimeoutException: Redis command timed out
2026-08-06T04:33:00.164823639Z  [content-service]  at org.springframework.data.redis.connection.lettuce.LettuceExceptionConverter.convert(LettuceExceptionConverter.java:68)
2026-08-06T04:33:00.164826534Z  [content-service]  at org.springframework.data.redis.connection.lettuce.LettuceExceptionConverter.convert(LettuceExceptionConverter.java:41)
2026-08-06T04:33:00.164829643Z  [content-service]  at org.springframework.data.redis.PassThroughExceptionTranslationStrategy.translate(PassThroughExceptionTranslationStrategy.java:40)
2026-08-06T04:33:00.164832543Z  [content-service]  at org.springframework.data.redis.FallbackExceptionTranslationStrategy.translate(FallbackExceptionTranslationStrategy.java:38)
2026-08-06T04:33:00.164835318Z  [content-service]  at org.springframework.data.redis.connection.lettuce.LettuceConnection.convertLettuceAccessException(LettuceConnection.java:310)
2026-08-06T04:33:00.625012262Z  [chat-service]  [2m2026-08-06T13:33:00.624+09:00[0;39m [33m WARN [traceId=NONE,spanId=NONE,userId=NONE][0;39m [35m7[0;39m [2m--- [chat-service] [llEventLoop-6-2] [                                                 ] [0;39m[36mi.l.core.protocol.ConnectionWatchdog    [0;39m [2m:[0;39m Cannot reconnect to [172.31.46.124/<unresolved>:6379]: finishConnect(..) failed: Connection refused: /172.31.46.124:6379
2026-08-06T04:33:00.625233699Z  [chat-service]  [2m2026-08-06T13:33:00.625+09:00[0;39m [33m WARN [traceId=NONE,spanId=NONE,userId=NONE][0;39m [35m7[0;39m [2m--- [chat-service] [llEventLoop-6-1] [                                                 ] [0;39m[36mi.l.core.protocol.ConnectionWatchdog    [0;39m [2m:[0;39m Cannot reconnect to [172.31.46.124/<unresolved>:6379]: finishConnect(..) failed: Connection refused: /172.31.46.124:6379
2026-08-06T04:33:02.008070881Z  [content-service]  2026-08-06 13:33:02.003 [scheduling-1] ERROR [traceId=NONE,spanId=NONE,userId=NONE] o.s.s.s.TaskUtils$LoggingErrorHandler - Unexpected error occurred in scheduled task
2026-08-06T04:33:02.053382659Z  [content-service]  2026-08-06 13:33:02.027 [scheduling-1] ERROR [traceId=NONE,spanId=NONE,userId=NONE] o.s.s.s.TaskUtils$LoggingErrorHandler - Unexpected error occurred in scheduled task
2026-08-06T04:33:02.407966466Z  [content-service]  2026-08-06 13:33:02.403 [http-nio-8082-exec-5] ERROR [traceId=6a740e7417e8b0b83aa26f5450e14d12,spanId=f8c1f3b4bf5554c8,userId=NONE] c.e.t.e.user.service.UserCacheStore - [user-cache] Redis 캐시 저장 실패: userId=1
```

### 메트릭 시계열

| 쿼리 | series | 점 | min | max | last | 값이 0이던 구간 |
|---|---|---:|---:|---:|---:|---|
| `hikaricp_connections_active` | `{__name__=hikaricp_connections_active, application=auth-service, cluster=yogurtte-k3s-prod, container=auth-service, instance=10.42.1.51:8090, job=auth-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=auth-service-5999bb9f5c-lzp4p, pool=HikariPool-1, service=auth-service}` | 81 | 0 | 0 | 0 | **2026-08-06T04:25:00Z ~ 2026-08-06T04:45:00Z** |
| `hikaricp_connections_active` | `{__name__=hikaricp_connections_active, application=chat-service, cluster=yogurtte-k3s-prod, container=chat-service, instance=10.42.1.55:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-fdcc7c776-kh2dl, pool=HikariPool-1}` | 73 | 0 | 0 | 0 | **2026-08-06T04:25:00Z ~ 2026-08-06T04:45:00Z** |
| `hikaricp_connections_active` | `{__name__=hikaricp_connections_active, application=content-service, cluster=yogurtte-k3s-prod, container=content-service, instance=10.42.1.53:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-85f648fcff-sp24n, pool=HikariPool-1}` | 69 | 0 | 0 | 0 | **2026-08-06T04:25:00Z ~ 2026-08-06T04:45:00Z** |
| `hikaricp_connections_active` | `{__name__=hikaricp_connections_active, application=content-service, cluster=yogurtte-k3s-prod, container=content-service, instance=10.42.3.46:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-85f648fcff-v2pw9, pool=HikariPool-1}` | 69 | 0 | 0 | 0 | **2026-08-06T04:25:00Z ~ 2026-08-06T04:45:00Z** |
| `hikaricp_connections_pending` | `{__name__=hikaricp_connections_pending, application=auth-service, cluster=yogurtte-k3s-prod, container=auth-service, instance=10.42.1.51:8090, job=auth-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=auth-service-5999bb9f5c-lzp4p, pool=HikariPool-1, service=auth-service}` | 81 | 0 | 0 | 0 | **2026-08-06T04:25:00Z ~ 2026-08-06T04:45:00Z** |
| `hikaricp_connections_pending` | `{__name__=hikaricp_connections_pending, application=chat-service, cluster=yogurtte-k3s-prod, container=chat-service, instance=10.42.1.55:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-fdcc7c776-kh2dl, pool=HikariPool-1}` | 73 | 0 | 0 | 0 | **2026-08-06T04:25:00Z ~ 2026-08-06T04:45:00Z** |
| `hikaricp_connections_pending` | `{__name__=hikaricp_connections_pending, application=content-service, cluster=yogurtte-k3s-prod, container=content-service, instance=10.42.1.53:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-85f648fcff-sp24n, pool=HikariPool-1}` | 69 | 0 | 0 | 0 | **2026-08-06T04:25:00Z ~ 2026-08-06T04:45:00Z** |
| `hikaricp_connections_pending` | `{__name__=hikaricp_connections_pending, application=content-service, cluster=yogurtte-k3s-prod, container=content-service, instance=10.42.3.46:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-85f648fcff-v2pw9, pool=HikariPool-1}` | 69 | 0 | 0 | 0 | **2026-08-06T04:25:00Z ~ 2026-08-06T04:45:00Z** |
| `rate(jvm_gc_pause_seconds_sum[5m])` | `{action=end of major GC, application=chat-service, cause=Allocation Failure, cluster=yogurtte-k3s-prod, container=chat-service, gc=MarkSweepCompact, instance=10.42.1.55:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-fdcc7c776-kh2dl}` | 81 | 0 | 0 | 0 | **2026-08-06T04:25:00Z ~ 2026-08-06T04:45:00Z** |
| `rate(jvm_gc_pause_seconds_sum[5m])` | `{action=end of minor GC, application=auth-service, cause=G1 Evacuation Pause, cluster=yogurtte-k3s-prod, container=auth-service, gc=G1 Young Generation, instance=10.42.1.51:8090, job=auth-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=auth-service-5999bb9f5c-lzp4p, service=auth-service}` | 81 | 0 | 0.000 | 0 | **2026-08-06T04:25:00Z ~ 2026-08-06T04:35:30Z, 2026-08-06T04:39:45Z ~ 2026-08-06T04:45:00Z** |
| `rate(jvm_gc_pause_seconds_sum[5m])` | `{action=end of minor GC, application=auth-service, cause=Metadata GC Threshold, cluster=yogurtte-k3s-prod, container=auth-service, gc=G1 Young Generation, instance=10.42.1.51:8090, job=auth-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=auth-service-5999bb9f5c-lzp4p, service=auth-service}` | 81 | 0 | 0 | 0 | **2026-08-06T04:25:00Z ~ 2026-08-06T04:45:00Z** |
| `rate(jvm_gc_pause_seconds_sum[5m])` | `{action=end of minor GC, application=chat-service, cause=Allocation Failure, cluster=yogurtte-k3s-prod, container=chat-service, gc=Copy, instance=10.42.1.55:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-fdcc7c776-kh2dl}` | 81 | 0.000 | 0.001 | 0.000 | — |
| `rate(jvm_gc_pause_seconds_sum[5m])` | `{action=end of minor GC, application=content-service, cause=Allocation Failure, cluster=yogurtte-k3s-prod, container=content-service, gc=Copy, instance=10.42.1.53:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-85f648fcff-sp24n}` | 81 | 0 | 0.000 | 0 | **2026-08-06T04:25:00Z ~ 2026-08-06T04:28:30Z, 2026-08-06T04:32:45Z ~ 2026-08-06T04:45:00Z** |
| `rate(jvm_gc_pause_seconds_sum[5m])` | `{action=end of minor GC, application=content-service, cause=Allocation Failure, cluster=yogurtte-k3s-prod, container=content-service, gc=Copy, instance=10.42.3.46:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-85f648fcff-v2pw9}` | 81 | 0 | 0.000 | 0 | **2026-08-06T04:25:00Z ~ 2026-08-06T04:29:45Z, 2026-08-06T04:34:00Z ~ 2026-08-06T04:45:00Z** |
| `up` | `{__name__=up, app=node-exporter, cluster=yogurtte-k3s-prod, component=metrics, container=node-exporter, instance=ip-172-31-40-241, job=integrations/node_exporter, k8s_cluster_name=yogurtte-k3s-prod, namespace=monitoring, pod=grafana-k8s-monitoring-node-exporter-cph5l, source=kubernetes, workload=DaemonSet/grafana-k8s-monitoring-node-exporter}` | 81 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, app=node-exporter, cluster=yogurtte-k3s-prod, component=metrics, container=node-exporter, instance=ip-172-31-45-39, job=integrations/node_exporter, k8s_cluster_name=yogurtte-k3s-prod, namespace=monitoring, pod=grafana-k8s-monitoring-node-exporter-9kvqt, source=kubernetes, workload=DaemonSet/grafana-k8s-monitoring-node-exporter}` | 81 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, cluster=yogurtte-k3s-prod, container=auth-service, instance=10.42.1.51:8090, job=auth-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=auth-service-5999bb9f5c-lzp4p}` | 81 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, cluster=yogurtte-k3s-prod, container=chat-service, instance=10.42.1.55:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-fdcc7c776-kh2dl}` | 73 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, cluster=yogurtte-k3s-prod, container=content-service, instance=10.42.1.53:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-85f648fcff-sp24n}` | 69 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, cluster=yogurtte-k3s-prod, container=content-service, instance=10.42.3.46:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-85f648fcff-v2pw9}` | 69 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, cluster=yogurtte-k3s-prod, instance=10.42.1.248:8080, job=integrations/kubernetes/kube-state-metrics, k8s_cluster_name=yogurtte-k3s-prod, source=kubernetes}` | 81 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, cluster=yogurtte-k3s-prod, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod}` | 81 | 1 | 1 | 1 | — |
| `mongodb_up` | `{__name__=mongodb_up, cluster=yogurtte-k3s-prod, instance=infra-server, job=mongodb, k8s_cluster_name=yogurtte-k3s-prod}` | 81 | 1 | 1 | 1 | — |
| `kafka_brokers` | `{__name__=kafka_brokers, cluster=yogurtte-k3s-prod, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod}` | 81 | 1 | 1 | 1 | — |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=chat-service-fcm-tokens, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=0, topic=user.fcm-tokens}` | 81 | 0 | 0 | 0 | **2026-08-06T04:25:00Z ~ 2026-08-06T04:45:00Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=chat-service-fcm-tokens, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=1, topic=user.fcm-tokens}` | 81 | 0 | 0 | 0 | **2026-08-06T04:25:00Z ~ 2026-08-06T04:45:00Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=chat-service-fcm-tokens, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=2, topic=user.fcm-tokens}` | 81 | 0 | 0 | 0 | **2026-08-06T04:25:00Z ~ 2026-08-06T04:45:00Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=chat-service-notification-settings, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=0, topic=user.notification-settings}` | 81 | 0 | 0 | 0 | **2026-08-06T04:25:00Z ~ 2026-08-06T04:45:00Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=chat-service-notification-settings, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=1, topic=user.notification-settings}` | 81 | 0 | 0 | 0 | **2026-08-06T04:25:00Z ~ 2026-08-06T04:45:00Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=chat-service-notification-settings, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=2, topic=user.notification-settings}` | 81 | 0 | 0 | 0 | **2026-08-06T04:25:00Z ~ 2026-08-06T04:45:00Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=db-writer, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=0, topic=chat.messages}` | 81 | 0 | 0 | 0 | **2026-08-06T04:25:00Z ~ 2026-08-06T04:45:00Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=db-writer, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=1, topic=chat.messages}` | 81 | 0 | 0 | 0 | **2026-08-06T04:25:00Z ~ 2026-08-06T04:45:00Z** |
| `websocket_active_users` | `{__name__=websocket_active_users, application=chat-service, cluster=yogurtte-k3s-prod, container=chat-service, instance=10.42.1.55:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-fdcc7c776-kh2dl}` | 73 | 0 | 0 | 0 | **2026-08-06T04:25:00Z ~ 2026-08-06T04:45:00Z** |

값이 0이던 구간은 굵게 표시했다 — 프로세스가 사라져 시계열이 꺾인 것이 유일한 신호인 장애가 있다.

