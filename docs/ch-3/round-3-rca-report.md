# RCA Report — `scan-1785767730`

| 항목 | 값 |
|---|---|
| 모드 | rca |
| 질문 | 최근 1시간 안에 앱이 잠깐 버벅였다는 얘기가 있어요. 뭔가 있었는지 봐줘 |
| 시각 | 2026-08-03T15:36:26.224934Z |
| provider | claude-cli |
| model | `claude-opus-5` · turns 1 |
| prompt | `./prompts/system-prompt.md` |
| tokens | in 131401 (cacheRead 18,133 · cacheCreate 113,266) / out 11490 · cost $1.5145 |
| elapsed | total 180997ms (tempo 4335 · loki 654 · mimir 682 · assemble 46 · llm 172123) |

## 탐색 (Triage)

| 항목 | 값 |
|---|---|
| 시간창 해석 | 상대 표현 '최근 1시간' |
| 시간창 확신도 | EXACT |
| 스윕 창 | 2026-08-03T14:35:30.170148Z ~ 2026-08-03T15:35:30.170148Z |
| 좁힌 창 | 2026-08-03T14:35:30.170148Z ~ 2026-08-03T15:35:30.170148Z |
| 대상 | chat-service, content-service |
| traceId | 6a70a6659bde7e3f0243b1162434c65e |
| 트레이스 후보 | 10건 |
| 장애 후보 | 7건 · 선택 INC-1, INC-2, INC-3, INC-4, INC-5, INC-7 |
| 계획 파싱 | 성공 |
| 스윕 컨텍스트 | 후보 + 원본 (A) |
| prompt | `./prompts/triage-prompt.md` |
| tokens | in 48881 / out 3535 · cost $0.4538 |
| chars | 컨텍스트 48,197 + 프롬프트 1,399 = **49,596** |
| elapsed | survey 1094ms · llm 54920ms |

**선정 이유**: 에러 없이 지연만 나타나는 두 구간이 있고, 14:35~14:45는 chat-service 파드 교체로 인한 시계열 단절 + 인증 필터 지연 + 상류 content API 지연 + 컨슈머 랙 적체가 한 사건으로 묶이며, 15:33 구간은 루트 스팬 미도달 형태의 chat-service 지연으로 별개 지문이라 어느 쪽이 제보된 버벅임인지 확정 불가하여 둘 다 남긴다.

**근거**

- up{job=chat-service, pod=chat-service-fdcc7c776-qrbc2, 10.42.3.43} 이 14:35:30 샘플 1개만 남기고 소멸, 후속 파드 chat-service-fdcc7c776-xf4sv(10.42.1.47)는 14:45:30부터 등장 — 약 10분간 chat-service 파드가 스크레이프되지 않은 공백
- websocket_active_users 도 동일하게 qrbc2는 14:35:30 한 점, xf4sv는 14:45:30부터 — 파드 교체와 시계열 단절이 일치
- chat-service 'security filterchain before' 지연 5건: 20,334ms / 17,971ms / 14,000ms / 7,704ms / 4,027ms (14:40:37.987 ~ 14:42:02.213) — 인증 필터 단계에서 막힌 지문
- content-service http post /battles/{battleId}/items/{itemId}/comments 542,294ms(14:32:05 시작) 및 38,887ms(14:41:25 시작), 두 트레이스 모두 serviceStats에 chat-service 스팬 14개 포함 — content→chat 하류 호출이 함께 늘어짐
- kafka_consumergroup_lag{consumergroup=notification-processors, partition=3, topic=user.notifications} 14:35:30=20 → 14:40:30=25 → 14:45:30=26 → 14:50:30=0, 새 chat 파드 기동 시점에 배수됨
- Tempo status=error 0건 vs 지연 트레이스 10건 — 실패가 아니라 지연만 발생한 장애 형태
- 15:33:42.861 ~ 15:34:06.633 트레이스 3건 23,772ms / 21,175ms / 11,056ms, rootServiceName이 '<root span not yet received>' — 루트 스팬 미도달, serviceStats는 chat-service 단독(한 건은 spanCount 14)
- Loki chat-service ERROR/WARN: 14:35~14:40 1건, 14:40~14:45 1건, 15:30~15:35 2건 — 두 지연 구간에만 로그 신호가 붙음

**스윕이 찾은 트레이스** (고른 것은 6a70a6659bde7e3f0243b1162434c65e)

| traceId | 채널 | root service | root span | ms |
|---|---|---|---|---:|
| `6a70b4e2511983007eb087a80330f9ba` | slow | <root span not yet received> | (없음) | 11056 |
| `6a70b4d8ad6302c8a55b085b698bac32` | slow | <root span not yet received> | (없음) | 21175 |
| `6a70b4d6fec0e31f0e1471884d9ab0a9` | slow | <root span not yet received> | (없음) | 23772 |
| `6a70a8b27dc2c42abb940c8bba63f107` | slow | chat-service | security filterchain before | 7704 |
| `6a70a8a8dd737de20646b20c1fa17b5d` | slow | chat-service | security filterchain before | 17971 |
| `6a70a895a7050d844888a6d1474d3a60` | slow | content-service | http post /battles/{battleId}/items/{itemId}/comments | 38887 |
| `6a70a876a54d5236b63c429a8c83048b` | slow | chat-service | security filterchain before | 4027 |
| `6a70a86cac4601d846a65e2f9f09cd66` | slow | chat-service | security filterchain before | 14000 |
| `6a70a86549448c3dbbee21d9d0ed5981` | slow | chat-service | security filterchain before | 20334 |
| `6a70a6659bde7e3f0243b1162434c65e` ←선택 | slow | content-service | http post /battles/{battleId}/items/{itemId}/comments | 542294 |

**장애 후보** (코드가 신호를 묶은 것 · 창은 여기서 계산됨)

## INC-1  content-service  |  http post /battles/{battleId}/items/{itemId}/comments
- 구간: 2026-08-03T14:32:05.519084Z ~ 2026-08-03T14:42:04.044883Z  (TEMPO · 시각 정확)
- content-service http post /battles/{battleId}/items/{itemId}/comments 542,294ms (slow 채널)
- content-service http post /battles/{battleId}/items/{itemId}/comments 38,887ms (slow 채널)
- traceId: 6a70a6659bde7e3f0243b1162434c65e, 6a70a895a7050d844888a6d1474d3a60
- 같은 시각의 다른 후보: INC-2, INC-3, INC-4  (인과 여부는 판단하지 않았다)

## INC-2  chat-service  |  ERROR/WARN
- 구간: 2026-08-03T14:35:00Z ~ 2026-08-03T14:45:00Z  (LOKI · 집계 해상도만큼 흐림)
- ERROR/WARN 1건 (2026-08-03T14:35:00Z ~ 2026-08-03T14:40:00Z)
- ERROR/WARN 1건 (2026-08-03T14:40:00Z ~ 2026-08-03T14:45:00Z)
- 같은 시각의 다른 후보: INC-1, INC-3, INC-4  (인과 여부는 판단하지 않았다)

## INC-3  kafka  |  kafka_consumergroup_lag
- 구간: 2026-08-03T14:35:30Z ~ 2026-08-03T14:50:30Z  (MIMIR · 집계 해상도만큼 흐림)
- kafka_consumergroup_lag{consumergroup=notification-processors, partition=3, topic=user.notifications} 20 → 25
- kafka_consumergroup_lag{consumergroup=notification-processors, partition=3, topic=user.notifications} 25 → 26
- kafka_consumergroup_lag{consumergroup=notification-processors, partition=3, topic=user.notifications} 26 → 0
- 같은 시각의 다른 후보: INC-1, INC-2, INC-4  (인과 여부는 판단하지 않았다)

## INC-4  chat-service  |  security filterchain before
- 구간: 2026-08-03T14:40:37.987943Z ~ 2026-08-03T14:42:02.213771Z  (TEMPO · 시각 정확)
- chat-service security filterchain before 20,334ms (slow 채널)
- chat-service security filterchain before 14,000ms (slow 채널)
- chat-service security filterchain before 4,027ms (slow 채널)
- chat-service security filterchain before 17,971ms (slow 채널)
- chat-service security filterchain before 7,704ms (slow 채널)
- traceId: 6a70a86549448c3dbbee21d9d0ed5981, 6a70a86cac4601d846a65e2f9f09cd66, 6a70a876a54d5236b63c429a8c83048b, 6a70a8a8dd737de20646b20c1fa17b5d, 6a70a8b27dc2c42abb940c8bba63f107
- 같은 시각의 다른 후보: INC-1, INC-2, INC-3  (인과 여부는 판단하지 않았다)

## INC-5  chat-service  |  ERROR/WARN
- 구간: 2026-08-03T15:30:00Z ~ 2026-08-03T15:35:00Z  (LOKI · 집계 해상도만큼 흐림)
- ERROR/WARN 2건 (2026-08-03T15:30:00Z ~ 2026-08-03T15:35:00Z)
- 같은 시각의 다른 후보: INC-6, INC-7  (인과 여부는 판단하지 않았다)

## INC-6  kafka  |  kafka_consumergroup_lag
- 구간: 2026-08-03T15:30:30Z ~ 2026-08-03T15:35:30Z  (MIMIR · 집계 해상도만큼 흐림)
- kafka_consumergroup_lag{consumergroup=notification-processors, partition=3, topic=user.notifications} 0 → 1
- 같은 시각의 다른 후보: INC-5, INC-7  (인과 여부는 판단하지 않았다)

## INC-7  <root span not yet received>
- 구간: 2026-08-03T15:33:42.861685Z ~ 2026-08-03T15:34:06.633685Z  (TEMPO · 시각 정확)
- <root span not yet received>  23,772ms (slow 채널)
- <root span not yet received>  21,175ms (slow 채널)
- <root span not yet received>  11,056ms (slow 채널)
- traceId: 6a70b4d6fec0e31f0e1471884d9ab0a9, 6a70b4d8ad6302c8a55b085b698bac32, 6a70b4e2511983007eb087a80330f9ba
- 같은 시각의 다른 후보: INC-5, INC-6  (인과 여부는 판단하지 않았다)

**기각한 후보**

- INC-6 — notification-processors p3 랙이 0→1로 1건 증가한 것뿐이라 15:33 구간의 11~24초 지연을 설명할 크기가 아니고, 같은 창의 INC-5·INC-7만으로 조사 범위가 충분히 덮인다.

**보정 기록**

- 창을 후보 [INC-1, INC-2, INC-3, INC-4, INC-5, INC-7] 의 신호 시각에서 계산했다 (2026-08-03T14:35:30.170148Z ~ 2026-08-03T15:35:30.170148Z)

**스윕 무신호/실패**

- Tempo 에러 검색 '{ status = error }'이 이 창에서 0건이다. 트레이스가 생성되지 않는 장애(컨슈머 전멸·파드 부재)이거나 에러가 아닌 형태의 장애(200 성공 + 지연)일 수 있으니 이 사실 자체를 근거로 쓸 것.

## 수집 범위 (Coverage)

- **window**: 2026-08-03T14:35:30.170148Z ~ 2026-08-03T15:35:30.170148Z (3600s)
- **trace**: 104,679B / 115 spans (상위 span만)
- **창 안 후보 트레이스**: 10건 / 104,679B
- **logs**: errwarn=46,819B · traceId=35,158B
- **metrics**: 8 수집 / 370,034B, 누락 [sum(rate(http_server_requests_seconds_count{application="content-service", status="401"}[1m]))]
- **context**: 206,352 chars (+ 시스템 프롬프트 576 chars)

### 토큰 축 (개선 지표)

| 단계 | 총 in | chars | 컨텍스트 토큰 |
|---|---:|---:|---:|
| 탐색 | 48,881 | 49,596 | 27,572 |
| 분석 | 131,401 | 206,928 | 110,092 |
| **합계** | **180,282** | | **137,664** |

- **overheadTokens 21,309 tok — 이 회차에 실측**(1자 프롬프트 프로브, 같은 명령·모델·샌드박스).
  호출마다 한 번씩 얹히므로 단계별로 뺀다. **다른 날 상수를 쓰지 않았다 = `█ 실측`.**
- contextTokens (count_tokens API): 측정 안 됨 — 구독 CLI 경로엔 API 키가 없다
- 비용은 **API 환산 추정치**다. 구독 계정으로 호출하므로 실제 청구액이 아니다.

## 수집 실패/누락

- Metric 'sum(rate(http_server_requests_seconds_count{application="content-service", status="401"}[1m]))' returned no series in this window; skipped.

---

# 장애 분석 — scan-1785767730

시각은 UTC 기준으로 쓰고 괄호에 KST를 병기한다(로그 원문은 KST +09:00).

## 1. 원인 후보 랭킹 (최대 3개)

1. **MongoDB(172.31.46.124:27017) 순간 중단이 2회 발생 → chat-service 알림 처리가 매회 약 22~26초 정지**
2. **chat-service 파드 교체(…-qrbc2 → …-xf4sv) 직후 콜드스타트 구간에서 FCM 발송·스레드 지연** (14:40:38~14:41:07 구간)
3. **Mongo 대기가 JDBC 트랜잭션·톰캣 워커를 물고 있어 헬스체크/HTTP 응답까지 최대 21초로 번진 구조적 증폭** (독립 원인이라기보다 1·2를 사용자 체감으로 키운 요인)

---

## 2. 후보별 근거

### 후보 1 — MongoDB 순간 중단 2회

**근거**
- chat-service 로그 원문(ERROR): `com.mongodb.MongoNodeIsRecoveringException: Command failed with error 11600 (InterruptedAtShutdown): 'interrupted at shutdown' on server 172.31.46.124:27017` — 14:41:38(23:41:38), 15:33:42(00:33:42) 두 시점에 각각 발생.
- 직후 소켓 자체가 거부됨: `Caused by: io.netty.channel.AbstractChannel$AnnotatedConnectException: Connection refused: /172.31.46.124:27017` (14:41:38.688, 14:41:44.324, 15:33:42.869, 15:33:44.253). → mongod 프로세스가 종료 후 아직 안 뜬 상태.
- 드라이버가 서버 셀렉션 대기로 진입: `Waiting for server to become available for operation with ID 235. Remaining time: 29927 ms. Selector: WritableServerSelector, topology description: {type=UNKNOWN ...}` (traceId=6a70a895…).
- 트레이스로 정지 시간이 그대로 찍힘:
  - traceId `6a70a895a7050d844888a6d1474d3a60` — span `receive`(user.notifications, offset 1078) **25,750ms**(14:41:38.294→14:42:04.045). 하위 `user-notification-service#process-notification`은 14:41:38.298에 시작했는데 첫 `insert toychat`(user_notifications)은 **14:42:00.841**에야 실행 → 약 22.5초가 Mongo 대기.
  - traceId `6a70b4d6fec0e31f0e1471884d9ab0a9` — `receive` **23,772ms**(15:33:42.861→15:34:06.634), 첫 `insert toychat`은 15:34:05.524 → 약 22.6초 대기. 동일 패턴 재현.
- 호출 그래프의 `kafka/user.notifications --messaging--> chat-service 3회 최대 25750.6ms`, `chat-service --jdbc--> mysql/content 최대 25748.6ms`가 위 두 트레이스와 일치.
- 파급: `kafka_consumergroup_lag{consumergroup=notification-processors, partition=3, topic=user.notifications}`가 14:41:30에 **max 26**까지 올랐다가 14:42:30 이후 0으로 회복.
- 두 사건 간격이 약 52분으로 **재발성**이 있다.

**확신도: 높음** (단, 아래 반증 때문에 "Mongo 프로세스 재시작"이라는 세부 단정까지는 중간)

**반증 데이터**
- `mongodb_up{instance=infra-server}` 241점 **전 구간 1**, `up{job=mongodb}` 도 전 구간 1 — 익스포터 관점에서는 다운이 전혀 안 잡혔다. (스크랩 15초 간격 대비 중단이 짧았거나, 익스포터가 다른 인스턴스/캐시된 값을 본 가능성. 어느 쪽인지는 데이터로 판정 불가.)
- content-service는 같은 시각 정상: traceId `6a70a895…`의 `http post /battles/{battleId}/items/{itemId}/comments`가 `status=200`, 14:41:25.157→14:41:25.214 = **57ms**. 호출 그래프상 content-service는 mongodb 엣지가 없어 영향권 밖.

---

### 후보 2 — chat-service 파드 교체 후 콜드스타트

**근거**
- 파드 교체 흔적: `chat-service-fdcc7c776-qrbc2`의 GC 시리즈는 **14:35:45에서 끊기고**(2점뿐), `chat-service-fdcc7c776-xf4sv`의 `up`/`hikaricp_*`/GC 시리즈는 **14:41:15부터** 시작한다. 조회 구간 내내 존재하는 content-service(2파드)·auth-service와 대조적이다.
- 기동 로그: `2026-08-03T23:39:53.681+09:00 WARN ... [main] JpaBaseConfiguration$JpaWebConfiguration : spring.jpa.open-in-view is enabled by default` — xf4sv가 **14:39:53(23:39:53)에 부팅**.
- 부팅 ~50초 뒤 트레이스 `6a70a6659bde7e3f0243b1162434c65e`(14:40:43~14:41:07, offset 1053)에서 Mongo는 빠른데(insert 102ms, find 102ms/37ms) 스팬 사이 공백이 크다: `user-notification-web-socket-sender#send-notificat` 스팬이 14:40:52.620에 시작했는데 내부 `KEYS 0`는 14:40:57.018에야 실행(≈4.4초 공백).
- FCM 발송 시간의 워밍업 곡선(로그 원문):
  - 1회차 `[push] 발송 준비` 23:40:58.814 → `[push] 멀티캐스트 결과: tokens=1, success=1, failure=0` 23:41:07.613 = **8.8초**
  - 2회차 23:42:02.717 → 23:42:04.037 = **1.32초**
  - 3회차 00:34:05.924 → 00:34:06.628 = **0.70초**
- 같은 시각 톰캣 스레드도 눌림: traceId `6a70a86549448c3dbbee21d9d0ed5981`의 `security filterchain before` 스팬이 14:40:37.988→14:40:38.719 = **731ms**(필터 12개 통과 이벤트가 630ms에 걸쳐 흩어짐), `secured request`는 14:40:38.784→14:40:58.322 = **19.5초**.
- 이 구간 조회 시작 시점의 `kafka_consumergroup_lag{partition=3}` 초깃값이 **20**인 것도 컨슈머 부재/정지 이력과 정합적이다.

**확신도: 중간**

**반증 데이터**
- `up{pod=chat-service-fdcc7c776-xf4sv}`는 수집 시작(14:41:15) 이후 **전 구간 1** — 재시작 자체를 직접 증명하는 이벤트(kube-state-metrics의 restart count, k8s Event)는 수집되지 않았다.
- GC로는 설명 안 됨: `rate(jvm_gc_pause_seconds_sum[5m])` chat-service minor GC **max 0.00192(≈1.9ms/s)**, major GC는 전 구간 0. 즉 4.4초 공백을 GC 정지로 돌릴 수 없다.
- 14:40:38 구간의 지연은 후보 1의 Mongo 에러 로그(14:41:38)보다 **1분 앞서므로** Mongo 원인으로 설명되지 않는 반면, 콜드스타트만으로 19.5초를 설명하기에도 근거가 얇다.

---

### 후보 3 — Mongo 대기가 트랜잭션/워커 스레드로 전파 (증폭 요인)

**근거**
- 액추에이터 헬스가 Mongo에 물려 수십 초 블록됨(로그↔트레이스 traceId 정확 일치):
  - `Health contributor ... (mongo) took 17332ms to respond` [traceId=**6a70a8a8**dd737de20646b20c1fa17b5d] ↔ 같은 traceId의 `secured request` 14:41:44.244→14:42:02.215 = 17.97초
  - `... took 21085ms` [traceId=**6a70b4d8**ad6302c8a55b085b698bac32] ↔ `secured request` 15:33:44.241→15:34:05.416 = 21.18초
  - `... took 11045ms` [traceId=**6a70b4e2**511983007eb087a80330f9ba] ↔ `secured request` 15:33:54.283→15:34:05.340 = 11.06초
  - 각 트레이스는 톰캣 워커(`nio-8090-exec-2/9/10`)를 점유했다.
- Kafka 리스너가 **JDBC 트랜잭션을 연 채** Mongo/FCM 외부 호출을 수행: traceId `6a70a895…`의 `connection`(HikariPool-1) 스팬이 `acquired` 15:41:38.296 → `commit` 14:42:04.042, 즉 **25.7초 동안 MySQL 커넥션 1개를 점유**. 기동 로그의 `spring.jpa.open-in-view is enabled by default` 경고도 같은 구조를 가리킨다.
- 부수적으로 chat-service는 Redis에 `KEYS`를 사용한다(`db.operation=KEYS`, `server.address=redis://172.31.46.124?timeout=2s`, 최대 63.2ms) — 현재 데이터에선 병목이 아니지만 키가 늘면 같은 경로를 막는다.

**확신도: 중간**

**반증 데이터**
- 커넥션 풀 고갈은 **없었다**: `hikaricp_connections_active{chat-service}` min 0 / **max 1**, `hikaricp_connections_pending`은 chat/content/auth 전 파드 **전 구간 0**. 즉 "풀 포화로 인한 대기"는 관측되지 않았고, 영향은 스레드 점유 수준에 머물렀다.
- 사용자 요청 경로 자체는 정상 응답: content-service의 댓글 POST는 두 사건 시각 모두 `status=200`, 57ms / 50ms(traceId `6a70b4d6…`: 15:33:42.795→15:33:42.846).

---

### 데이터 부족 명시
- **사용자가 말한 "버벅임"이 어느 화면/API인지 특정 불가.** 관측된 지연은 (a) 알림/푸시 도착 22~26초 지연, (b) chat-service 8090 포트 요청 7~21초다. chat-service의 `http_server_requests_seconds_*` 시계열이 수집되지 않아 실제 사용자 요청 지연 분포를 확인할 수 없다.
- 수집 실패로 명시된 `content-service status=401` 메트릭은 시리즈 없음 — 인증 관련 가설은 **판정 불가**.
- auth-service는 `hikaricp_*`(전 구간 0)·GC·`up`만 있고 트레이스·로그가 한 건도 없어 사실상 **관측 공백**.
- MongoDB 중단의 원인(재시작 트리거, OOM/배포/백업 여부)과 chat-service 파드 교체 사유(OOMKilled/probe 실패/배포)는 이 데이터셋에 없다. 위 확신도는 이 공백을 반영해 한 단계씩 낮춰 잡았다.

---

## 3. 권장 다음 조치

**즉시 확인(원인 확정용)**
1. infra-server의 mongod 로그·`systemctl status mongod`·`journalctl -u mongod`를 **23:41:38, 00:33:42(KST)** 두 시각 기준으로 확인. shutdown 사유(수동 재시작/OOM killer/백업 스크립트/디스크)를 특정한다. 52분 간격 재발이므로 조회창을 6~24시간으로 넓혀 주기성 여부도 확인.
2. `kubectl describe pod chat-service-fdcc7c776-xf4sv` 및 `kubectl get events`로 qrbc2 종료 사유(restartCount, lastState.terminated.reason, OOMKilled 여부)와 14:36~14:40 사이 배포 여부 확인.
3. mongodb_exporter가 왜 `mongodb_up=1`을 유지했는지 점검(스크랩 주기, 커넥션 재사용/캐시). **다운을 놓치는 알람은 지금 신뢰할 수 없다.**

**추가 수집(공백 메우기)**
4. chat-service `http_server_requests_seconds_count/sum`, `container_memory_working_set_bytes`, `kube_pod_container_status_restarts_total`, MongoDB 연결/오퍼레이션 메트릭을 같은 창으로 재조회.
5. 사용자에게 "버벅임"의 화면·시각을 확인 — 알림 지연인지 화면 응답 지연인지에 따라 후보 1/3의 가중치가 갈린다.

**구성 개선(재발 시 피해 축소)**
6. Mongo 드라이버 `serverSelectionTimeoutMS`를 30초(로그의 `Remaining time: 29999 ms`)에서 수 초 수준으로 낮추고, 리스너의 재시도/DLQ 경로(`user.notifications.dlq`, 현재 lag 0) 동작을 확인.
7. Kafka 리스너에서 **JDBC 트랜잭션 안에 Mongo·FCM 외부 호출을 두지 않도록 분리**(현재 커넥션 25.7초 점유). 아울러 `spring.jpa.open-in-view=false` 명시.
8. actuator `health`의 mongo indicator를 readiness에서 분리하거나 타임아웃을 설정 — 헬스가 21초 걸리면 probe 실패로 파드가 재기동되어 후보 2와 같은 2차 사고를 만든다.
9. `UserNotificationWebSocketSender`/`PushDispatcher`의 Redis `KEYS 0` 호출을 `SCAN` 또는 명시적 키 집합 조회로 교체.

---

## 관측 증거 (Evidence)

> 이 조사가 실제로 본 값이다. 원본 응답 전체는 `reports/raw/scan-1785767730-*.json`에 있다.

### 호출 그래프 (트레이스에서 추출)

```
chat-service --db--> mongodb  21회  최대 241.5ms  [insert, find]
chat-service --db--> redis  6회  최대 63.2ms  [KEYS]
content-service --db--> redis  3회  최대 1.9ms  [GET]
chat-service --jdbc--> mysql/content (HikariPool-1)  17회  최대 25748.6ms
    events: acquired, commit
content-service --jdbc--> mysql/content (HikariPool-1)  24회  최대 47.0ms
    events: acquired, commit
content-service --messaging--> kafka/user.notifications  3회  최대 15.2ms  [publish]
kafka/user.notifications --messaging--> chat-service  3회  최대 25750.6ms  [receive]
```

### span (duration 상위 15 / 전체 115)

| ms | service | span | 시작 |
|---:|---|---|---|
| 25750.56 | chat-service | `receive` | 2026-08-03T14:41:38.294882Z |
| 25748.57 | chat-service | `connection` | 2026-08-03T14:41:38.295927Z |
| 25741.52 | chat-service | `user-notification-service#process-notification` | 2026-08-03T14:41:38.298085Z |
| 24733.60 | chat-service | `receive` | 2026-08-03T14:40:43.080326Z |
| 23772.13 | chat-service | `receive` | 2026-08-03T15:33:42.861685Z |
| 23771.06 | chat-service | `connection` | 2026-08-03T15:33:42.862177Z |
| 23762.89 | chat-service | `user-notification-service#process-notification` | 2026-08-03T15:33:42.865686Z |
| 21267.38 | chat-service | `connection` | 2026-08-03T14:40:46.517797Z |
| 21175.06 | chat-service | `secured request` | 2026-08-03T15:33:44.241312Z |
| 20702.64 | chat-service | `user-notification-service#process-notification` | 2026-08-03T14:40:46.981578Z |
| 19538.35 | chat-service | `secured request` | 2026-08-03T14:40:38.783849Z |
| 17970.53 | chat-service | `secured request` | 2026-08-03T14:41:44.244002Z |
| 13995.62 | chat-service | `secured request` | 2026-08-03T14:40:44.319149Z |
| 11056.95 | chat-service | `secured request` | 2026-08-03T15:33:54.282731Z |
| 9435.47 | chat-service | `push-dispatcher#dispatch` | 2026-08-03T14:40:58.183525Z |

### 로그 원문 (60 / 전체 243줄)

전체가 상한을 넘어 ERROR/WARN 줄을 우선 발췌했다. 나머지는 원본 파일에 있다.

```
2026-08-03T14:39:53.682757251Z  [chat-service]  [2m2026-08-03T23:39:53.681+09:00[0;39m [33m WARN [traceId=NONE,spanId=NONE,userId=NONE][0;39m [35m7[0;39m [2m--- [chat-service] [           main] [                                                 ] [0;39m[36mJpaBaseConfiguration$JpaWebConfiguration[0;39m [2m:[0;39m spring.jpa.open-in-view is enabled by default. Therefore, database queries may be performed during view rendering. Explicitly configure spring.jpa.open-in-view to disable this warning
2026-08-03T14:41:38.095947844Z  [chat-service]  [2m2026-08-03T23:41:37.993+09:00[0;39m [32m INFO [traceId=NONE,spanId=NONE,userId=NONE][0;39m [35m7[0;39m [2m--- [chat-service] [31.46.124:27017] [                                                 ] [0;39m[36morg.mongodb.driver.cluster              [0;39m [2m:[0;39m Exception in monitor thread while connecting to server 172.31.46.124:27017
2026-08-03T14:41:38.095978934Z  [chat-service]  com.mongodb.MongoNodeIsRecoveringException: Command failed with error 11600 (InterruptedAtShutdown): 'interrupted at shutdown' on server 172.31.46.124:27017. The full response is {"ok": 0.0, "errmsg": "interrupted at shutdown", "code": 11600, "codeName": "InterruptedAtShutdown"}
2026-08-03T14:41:38.095981348Z  [chat-service]  at com.mongodb.internal.connection.ProtocolHelper.createSpecialException(ProtocolHelper.java:264) ~[mongodb-driver-core-5.5.1.jar!/:na]
2026-08-03T14:41:38.095983282Z  [chat-service]  at com.mongodb.internal.connection.ProtocolHelper.getCommandFailureException(ProtocolHelper.java:206) ~[mongodb-driver-core-5.5.1.jar!/:na]
2026-08-03T14:41:38.095985299Z  [chat-service]  at com.mongodb.internal.connection.InternalStreamConnection.receiveCommandMessageResponse(InternalStreamConnection.java:520) ~[mongodb-driver-core-5.5.1.jar!/:na]
2026-08-03T14:41:38.095987195Z  [chat-service]  at com.mongodb.internal.connection.InternalStreamConnection.receive(InternalStreamConnection.java:469) ~[mongodb-driver-core-5.5.1.jar!/:na]
2026-08-03T14:41:38.095989273Z  [chat-service]  at com.mongodb.internal.connection.DefaultServerMonitor$ServerMonitor.lookupServerDescription(DefaultServerMonitor.java:249) ~[mongodb-driver-core-5.5.1.jar!/:na]
2026-08-03T14:41:38.095991252Z  [chat-service]  at com.mongodb.internal.connection.DefaultServerMonitor$ServerMonitor.run(DefaultServerMonitor.java:176) ~[mongodb-driver-core-5.5.1.jar!/:na]
2026-08-03T14:41:38.113732196Z  [chat-service]  [2m2026-08-03T23:41:37.993+09:00[0;39m [32m INFO [traceId=NONE,spanId=NONE,userId=NONE][0;39m [35m7[0;39m [2m--- [chat-service] [31.46.124:27017] [                                                 ] [0;39m[36morg.mongodb.driver.cluster              [0;39m [2m:[0;39m Exception in monitor thread while connecting to server 172.31.46.124:27017
2026-08-03T14:41:38.113788756Z  [chat-service]  com.mongodb.MongoNodeIsRecoveringException: Command failed with error 11600 (InterruptedAtShutdown): 'interrupted at shutdown' on server 172.31.46.124:27017. The full response is {"ok": 0.0, "errmsg": "interrupted at shutdown", "code": 11600, "codeName": "InterruptedAtShutdown"}
2026-08-03T14:41:38.113792593Z  [chat-service]  at com.mongodb.internal.connection.ProtocolHelper.createSpecialException(ProtocolHelper.java:264) ~[mongodb-driver-core-5.5.1.jar!/:na]
2026-08-03T14:41:38.113795231Z  [chat-service]  at com.mongodb.internal.connection.ProtocolHelper.getCommandFailureException(ProtocolHelper.java:206) ~[mongodb-driver-core-5.5.1.jar!/:na]
2026-08-03T14:41:38.113818242Z  [chat-service]  at com.mongodb.internal.connection.InternalStreamConnection.receiveCommandMessageResponse(InternalStreamConnection.java:520) ~[mongodb-driver-core-5.5.1.jar!/:na]
2026-08-03T14:41:38.113820702Z  [chat-service]  at com.mongodb.internal.connection.InternalStreamConnection.receive(InternalStreamConnection.java:469) ~[mongodb-driver-core-5.5.1.jar!/:na]
2026-08-03T14:41:38.113823178Z  [chat-service]  at com.mongodb.internal.connection.DefaultServerMonitor$ServerMonitor.lookupServerDescription(DefaultServerMonitor.java:249) ~[mongodb-driver-core-5.5.1.jar!/:na]
2026-08-03T14:41:38.113825466Z  [chat-service]  at com.mongodb.internal.connection.DefaultServerMonitor$ServerMonitor.run(DefaultServerMonitor.java:176) ~[mongodb-driver-core-5.5.1.jar!/:na]
2026-08-03T14:41:38.389229615Z  [chat-service]  [2m2026-08-03T23:41:38.388+09:00[0;39m [32m INFO [traceId=6a70a895a7050d844888a6d1474d3a60,spanId=d7feb099e1a9883c,userId=NONE][0;39m [35m7[0;39m [2m--- [chat-service] [ntainer#5-1-C-1] [6a70a895a7050d844888a6d1474d3a60-d7feb099e1a9883c] [0;39m[36morg.mongodb.driver.cluster              [0;39m [2m:[0;39m Waiting for server to become available for operation with ID 235. Remaining time: 29927 ms. Selector: WritableServerSelector, topology description: {type=UNKNOWN, servers=[{address=172.31.46.124:27017, type=UNKNOWN, state=CONNECTING, exception={com.mongodb.MongoNodeIsRecoveringException: Command failed with error 11600 (InterruptedAtShutdown): 'interrupted at shutdown' on server 172.31.46.124:27017. The full response is {"ok": 0.0, "errmsg": "interrupted at shutdown", "code": 11600, "codeName": "InterruptedAtShutdown"}}}].
2026-08-03T14:41:38.389229615Z  [chat-service]  [2m2026-08-03T23:41:38.388+09:00[0;39m [32m INFO [traceId=6a70a895a7050d844888a6d1474d3a60,spanId=d7feb099e1a9883c,userId=NONE][0;39m [35m7[0;39m [2m--- [chat-service] [ntainer#5-1-C-1] [6a70a895a7050d844888a6d1474d3a60-d7feb099e1a9883c] [0;39m[36morg.mongodb.driver.cluster              [0;39m [2m:[0;39m Waiting for server to become available for operation with ID 235. Remaining time: 29927 ms. Selector: WritableServerSelector, topology description: {type=UNKNOWN, servers=[{address=172.31.46.124:27017, type=UNKNOWN, state=CONNECTING, exception={com.mongodb.MongoNodeIsRecoveringException: Command failed with error 11600 (InterruptedAtShutdown): 'interrupted at shutdown' on server 172.31.46.124:27017. The full response is {"ok": 0.0, "errmsg": "interrupted at shutdown", "code": 11600, "codeName": "InterruptedAtShutdown"}}}].
2026-08-03T14:41:38.688664110Z  [chat-service]  [2m2026-08-03T23:41:38.687+09:00[0;39m [32m INFO [traceId=NONE,spanId=NONE,userId=NONE][0;39m [35m7[0;39m [2m--- [chat-service] [31.46.124:27017] [                                                 ] [0;39m[36morg.mongodb.driver.cluster              [0;39m [2m:[0;39m Exception in monitor thread while connecting to server 172.31.46.124:27017
2026-08-03T14:41:38.688726501Z  [chat-service]  com.mongodb.MongoSocketOpenException: Exception opening socket
2026-08-03T14:41:38.688841584Z  [chat-service]  Caused by: io.netty.channel.AbstractChannel$AnnotatedConnectException: Connection refused: /172.31.46.124:27017
2026-08-03T14:41:38.688844559Z  [chat-service]  Caused by: java.net.ConnectException: Connection refused
2026-08-03T14:41:44.316763019Z  [chat-service]  [2m2026-08-03T23:41:44.315+09:00[0;39m [32m INFO [traceId=6a70a8a8dd737de20646b20c1fa17b5d,spanId=8db391db0b0cd5ca,userId=NONE][0;39m [35m7[0;39m [2m--- [chat-service] [nio-8090-exec-2] [6a70a8a8dd737de20646b20c1fa17b5d-8db391db0b0cd5ca] [0;39m[36morg.mongodb.driver.cluster              [0;39m [2m:[0;39m Waiting for server to become available for operation with ID 249. Remaining time: 29999 ms. Selector: ReadPreferenceServerSelector{readPreference=primary}, topology description: {type=UNKNOWN, servers=[{address=172.31.46.124:27017, type=UNKNOWN, state=CONNECTING, exception={com.mongodb.MongoNodeIsRecoveringException: Command failed with error 11600 (InterruptedAtShutdown): 'interrupted at shutdown' on server 172.31.46.124:27017. The full response is {"ok": 0.0, "errmsg": "interrupted at shutdown", "code": 11600, "codeName": "InterruptedAtShutdown"}}}].
2026-08-03T14:41:44.316763019Z  [chat-service]  [2m2026-08-03T23:41:44.315+09:00[0;39m [32m INFO [traceId=6a70a8a8dd737de20646b20c1fa17b5d,spanId=8db391db0b0cd5ca,userId=NONE][0;39m [35m7[0;39m [2m--- [chat-service] [nio-8090-exec-2] [6a70a8a8dd737de20646b20c1fa17b5d-8db391db0b0cd5ca] [0;39m[36morg.mongodb.driver.cluster              [0;39m [2m:[0;39m Waiting for server to become available for operation with ID 249. Remaining time: 29999 ms. Selector: ReadPreferenceServerSelector{readPreference=primary}, topology description: {type=UNKNOWN, servers=[{address=172.31.46.124:27017, type=UNKNOWN, state=CONNECTING, exception={com.mongodb.MongoNodeIsRecoveringException: Command failed with error 11600 (InterruptedAtShutdown): 'interrupted at shutdown' on server 172.31.46.124:27017. The full response is {"ok": 0.0, "errmsg": "interrupted at shutdown", "code": 11600, "codeName": "InterruptedAtShutdown"}}}].
2026-08-03T14:41:44.324441139Z  [chat-service]  [2m2026-08-03T23:41:44.323+09:00[0;39m [32m INFO [traceId=NONE,spanId=NONE,userId=NONE][0;39m [35m7[0;39m [2m--- [chat-service] [31.46.124:27017] [                                                 ] [0;39m[36morg.mongodb.driver.cluster              [0;39m [2m:[0;39m Exception in monitor thread while connecting to server 172.31.46.124:27017
2026-08-03T14:41:44.324488130Z  [chat-service]  com.mongodb.MongoSocketOpenException: Exception opening socket
2026-08-03T14:41:44.324583015Z  [chat-service]  Caused by: io.netty.channel.AbstractChannel$AnnotatedConnectException: Connection refused: /172.31.46.124:27017
2026-08-03T14:41:44.324585899Z  [chat-service]  Caused by: java.net.ConnectException: Connection refused
2026-08-03T14:41:54.328161012Z  [chat-service]  [2m2026-08-03T23:41:54.327+09:00[0;39m [32m INFO [traceId=6a70a8b27dc2c42abb940c8bba63f107,spanId=367619fc456dff08,userId=NONE][0;39m [35m7[0;39m [2m--- [chat-service] [nio-8090-exec-3] [6a70a8b27dc2c42abb940c8bba63f107-367619fc456dff08] [0;39m[36morg.mongodb.driver.cluster              [0;39m [2m:[0;39m Waiting for server to become available for operation with ID 292. Remaining time: 29999 ms. Selector: ReadPreferenceServerSelector{readPreference=primary}, topology description: {type=UNKNOWN, servers=[{address=172.31.46.124:27017, type=UNKNOWN, state=CONNECTING, exception={com.mongodb.MongoSocketOpenException: Exception opening socket}, caused by {io.netty.channel.AbstractChannel$AnnotatedConnectException: Connection refused: /172.31.46.124:27017}, caused by {java.net.ConnectException: Connection refused}}].
2026-08-03T14:41:54.328161012Z  [chat-service]  [2m2026-08-03T23:41:54.327+09:00[0;39m [32m INFO [traceId=6a70a8b27dc2c42abb940c8bba63f107,spanId=367619fc456dff08,userId=NONE][0;39m [35m7[0;39m [2m--- [chat-service] [nio-8090-exec-3] [6a70a8b27dc2c42abb940c8bba63f107-367619fc456dff08] [0;39m[36morg.mongodb.driver.cluster              [0;39m [2m:[0;39m Waiting for server to become available for operation with ID 292. Remaining time: 29999 ms. Selector: ReadPreferenceServerSelector{readPreference=primary}, topology description: {type=UNKNOWN, servers=[{address=172.31.46.124:27017, type=UNKNOWN, state=CONNECTING, exception={com.mongodb.MongoSocketOpenException: Exception opening socket}, caused by {io.netty.channel.AbstractChannel$AnnotatedConnectException: Connection refused: /172.31.46.124:27017}, caused by {java.net.ConnectException: Connection refused}}].
2026-08-03T14:42:01.717335888Z  [chat-service]  [2m2026-08-03T23:42:01.717+09:00[0;39m [33m WARN [traceId=6a70a8a8dd737de20646b20c1fa17b5d,spanId=8db391db0b0cd5ca,userId=NONE][0;39m [35m7[0;39m [2m--- [chat-service] [nio-8090-exec-2] [6a70a8a8dd737de20646b20c1fa17b5d-8db391db0b0cd5ca] [0;39m[36mo.s.b.a.health.HealthEndpointSupport    [0;39m [2m:[0;39m Health contributor org.springframework.boot.actuate.autoconfigure.health.HealthEndpointConfiguration$AdaptedReactiveHealthContributors$1 (mongo) took 17332ms to respond
2026-08-03T14:42:01.717335888Z  [chat-service]  [2m2026-08-03T23:42:01.717+09:00[0;39m [33m WARN [traceId=6a70a8a8dd737de20646b20c1fa17b5d,spanId=8db391db0b0cd5ca,userId=NONE][0;39m [35m7[0;39m [2m--- [chat-service] [nio-8090-exec-2] [6a70a8a8dd737de20646b20c1fa17b5d-8db391db0b0cd5ca] [0;39m[36mo.s.b.a.health.HealthEndpointSupport    [0;39m [2m:[0;39m Health contributor org.springframework.boot.actuate.autoconfigure.health.HealthEndpointConfiguration$AdaptedReactiveHealthContributors$1 (mongo) took 17332ms to respond
2026-08-03T15:33:42.126946122Z  [chat-service]  [2m2026-08-04T00:33:42.126+09:00[0;39m [32m INFO [traceId=NONE,spanId=NONE,userId=NONE][0;39m [35m7[0;39m [2m--- [chat-service] [31.46.124:27017] [                                                 ] [0;39m[36morg.mongodb.driver.cluster              [0;39m [2m:[0;39m Exception in monitor thread while connecting to server 172.31.46.124:27017
2026-08-03T15:33:42.126993998Z  [chat-service]  com.mongodb.MongoNodeIsRecoveringException: Command failed with error 11600 (InterruptedAtShutdown): 'interrupted at shutdown' on server 172.31.46.124:27017. The full response is {"ok": 0.0, "errmsg": "interrupted at shutdown", "code": 11600, "codeName": "InterruptedAtShutdown"}
2026-08-03T15:33:42.126996902Z  [chat-service]  at com.mongodb.internal.connection.ProtocolHelper.createSpecialException(ProtocolHelper.java:264) ~[mongodb-driver-core-5.5.1.jar!/:na]
2026-08-03T15:33:42.126999599Z  [chat-service]  at com.mongodb.internal.connection.ProtocolHelper.getCommandFailureException(ProtocolHelper.java:206) ~[mongodb-driver-core-5.5.1.jar!/:na]
2026-08-03T15:33:42.127317336Z  [chat-service]  [2m2026-08-04T00:33:42.126+09:00[0;39m [32m INFO [traceId=NONE,spanId=NONE,userId=NONE][0;39m [35m7[0;39m [2m--- [chat-service] [31.46.124:27017] [                                                 ] [0;39m[36morg.mongodb.driver.cluster              [0;39m [2m:[0;39m Exception in monitor thread while connecting to server 172.31.46.124:27017
2026-08-03T15:33:42.127327469Z  [chat-service]  com.mongodb.MongoNodeIsRecoveringException: Command failed with error 11600 (InterruptedAtShutdown): 'interrupted at shutdown' on server 172.31.46.124:27017. The full response is {"ok": 0.0, "errmsg": "interrupted at shutdown", "code": 11600, "codeName": "InterruptedAtShutdown"}
2026-08-03T15:33:42.127330680Z  [chat-service]  at com.mongodb.internal.connection.ProtocolHelper.createSpecialException(ProtocolHelper.java:264) ~[mongodb-driver-core-5.5.1.jar!/:na]
2026-08-03T15:33:42.127333393Z  [chat-service]  at com.mongodb.internal.connection.ProtocolHelper.getCommandFailureException(ProtocolHelper.java:206) ~[mongodb-driver-core-5.5.1.jar!/:na]
2026-08-03T15:33:42.867465874Z  [chat-service]  [2m2026-08-04T00:33:42.866+09:00[0;39m [32m INFO [traceId=6a70b4d6fec0e31f0e1471884d9ab0a9,spanId=6061456a9ac0d52d,userId=NONE][0;39m [35m7[0;39m [2m--- [chat-service] [ntainer#5-1-C-1] [6a70b4d6fec0e31f0e1471884d9ab0a9-6061456a9ac0d52d] [0;39m[36morg.mongodb.driver.cluster              [0;39m [2m:[0;39m Waiting for server to become available for operation with ID 1899. Remaining time: 29999 ms. Selector: WritableServerSelector, topology description: {type=UNKNOWN, servers=[{address=172.31.46.124:27017, type=UNKNOWN, state=CONNECTING, exception={com.mongodb.MongoNodeIsRecoveringException: Command failed with error 11600 (InterruptedAtShutdown): 'interrupted at shutdown' on server 172.31.46.124:27017. The full response is {"ok": 0.0, "errmsg": "interrupted at shutdown", "code": 11600, "codeName": "InterruptedAtShutdown"}}}].
2026-08-03T15:33:42.867465874Z  [chat-service]  [2m2026-08-04T00:33:42.866+09:00[0;39m [32m INFO [traceId=6a70b4d6fec0e31f0e1471884d9ab0a9,spanId=6061456a9ac0d52d,userId=NONE][0;39m [35m7[0;39m [2m--- [chat-service] [ntainer#5-1-C-1] [6a70b4d6fec0e31f0e1471884d9ab0a9-6061456a9ac0d52d] [0;39m[36morg.mongodb.driver.cluster              [0;39m [2m:[0;39m Waiting for server to become available for operation with ID 1899. Remaining time: 29999 ms. Selector: WritableServerSelector, topology description: {type=UNKNOWN, servers=[{address=172.31.46.124:27017, type=UNKNOWN, state=CONNECTING, exception={com.mongodb.MongoNodeIsRecoveringException: Command failed with error 11600 (InterruptedAtShutdown): 'interrupted at shutdown' on server 172.31.46.124:27017. The full response is {"ok": 0.0, "errmsg": "interrupted at shutdown", "code": 11600, "codeName": "InterruptedAtShutdown"}}}].
2026-08-03T15:33:42.869323611Z  [chat-service]  [2m2026-08-04T00:33:42.868+09:00[0;39m [32m INFO [traceId=NONE,spanId=NONE,userId=NONE][0;39m [35m7[0;39m [2m--- [chat-service] [31.46.124:27017] [                                                 ] [0;39m[36morg.mongodb.driver.cluster              [0;39m [2m:[0;39m Exception in monitor thread while connecting to server 172.31.46.124:27017
2026-08-03T15:33:42.869523420Z  [chat-service]  com.mongodb.MongoSocketOpenException: Exception opening socket
2026-08-03T15:33:42.869657841Z  [chat-service]  Caused by: io.netty.channel.AbstractChannel$AnnotatedConnectException: Connection refused: /172.31.46.124:27017
2026-08-03T15:33:42.869660683Z  [chat-service]  Caused by: java.net.ConnectException: Connection refused
2026-08-03T15:33:44.253355793Z  [chat-service]  [2m2026-08-04T00:33:44.249+09:00[0;39m [32m INFO [traceId=6a70b4d8ad6302c8a55b085b698bac32,spanId=b9fd02410833596e,userId=NONE][0;39m [35m7[0;39m [2m--- [chat-service] [nio-8090-exec-9] [6a70b4d8ad6302c8a55b085b698bac32-b9fd02410833596e] [0;39m[36morg.mongodb.driver.cluster              [0;39m [2m:[0;39m Waiting for server to become available for operation with ID 1903. Remaining time: 29999 ms. Selector: ReadPreferenceServerSelector{readPreference=primary}, topology description: {type=UNKNOWN, servers=[{address=172.31.46.124:27017, type=UNKNOWN, state=CONNECTING, exception={com.mongodb.MongoNodeIsRecoveringException: Command failed with error 11600 (InterruptedAtShutdown): 'interrupted at shutdown' on server 172.31.46.124:27017. The full response is {"ok": 0.0, "errmsg": "interrupted at shutdown", "code": 11600, "codeName": "InterruptedAtShutdown"}}}].
2026-08-03T15:33:44.253355793Z  [chat-service]  [2m2026-08-04T00:33:44.249+09:00[0;39m [32m INFO [traceId=6a70b4d8ad6302c8a55b085b698bac32,spanId=b9fd02410833596e,userId=NONE][0;39m [35m7[0;39m [2m--- [chat-service] [nio-8090-exec-9] [6a70b4d8ad6302c8a55b085b698bac32-b9fd02410833596e] [0;39m[36morg.mongodb.driver.cluster              [0;39m [2m:[0;39m Waiting for server to become available for operation with ID 1903. Remaining time: 29999 ms. Selector: ReadPreferenceServerSelector{readPreference=primary}, topology description: {type=UNKNOWN, servers=[{address=172.31.46.124:27017, type=UNKNOWN, state=CONNECTING, exception={com.mongodb.MongoNodeIsRecoveringException: Command failed with error 11600 (InterruptedAtShutdown): 'interrupted at shutdown' on server 172.31.46.124:27017. The full response is {"ok": 0.0, "errmsg": "interrupted at shutdown", "code": 11600, "codeName": "InterruptedAtShutdown"}}}].
2026-08-03T15:33:44.253401616Z  [chat-service]  [2m2026-08-04T00:33:44.252+09:00[0;39m [32m INFO [traceId=NONE,spanId=NONE,userId=NONE][0;39m [35m7[0;39m [2m--- [chat-service] [31.46.124:27017] [                                                 ] [0;39m[36morg.mongodb.driver.cluster              [0;39m [2m:[0;39m Exception in monitor thread while connecting to server 172.31.46.124:27017
2026-08-03T15:33:44.253409054Z  [chat-service]  com.mongodb.MongoSocketOpenException: Exception opening socket
2026-08-03T15:33:44.253491872Z  [chat-service]  Caused by: io.netty.channel.AbstractChannel$AnnotatedConnectException: Connection refused: /172.31.46.124:27017
2026-08-03T15:33:44.253494474Z  [chat-service]  Caused by: java.net.ConnectException: Connection refused
2026-08-03T15:33:54.288442445Z  [chat-service]  [2m2026-08-04T00:33:54.288+09:00[0;39m [32m INFO [traceId=6a70b4e2511983007eb087a80330f9ba,spanId=801566d8c10da131,userId=NONE][0;39m [35m7[0;39m [2m--- [chat-service] [io-8090-exec-10] [6a70b4e2511983007eb087a80330f9ba-801566d8c10da131] [0;39m[36morg.mongodb.driver.cluster              [0;39m [2m:[0;39m Waiting for server to become available for operation with ID 1947. Remaining time: 29999 ms. Selector: ReadPreferenceServerSelector{readPreference=primary}, topology description: {type=UNKNOWN, servers=[{address=172.31.46.124:27017, type=UNKNOWN, state=CONNECTING, exception={com.mongodb.MongoSocketOpenException: Exception opening socket}, caused by {io.netty.channel.AbstractChannel$AnnotatedConnectException: Connection refused: /172.31.46.124:27017}, caused by {java.net.ConnectException: Connection refused}}].
2026-08-03T15:33:54.288442445Z  [chat-service]  [2m2026-08-04T00:33:54.288+09:00[0;39m [32m INFO [traceId=6a70b4e2511983007eb087a80330f9ba,spanId=801566d8c10da131,userId=NONE][0;39m [35m7[0;39m [2m--- [chat-service] [io-8090-exec-10] [6a70b4e2511983007eb087a80330f9ba-801566d8c10da131] [0;39m[36morg.mongodb.driver.cluster              [0;39m [2m:[0;39m Waiting for server to become available for operation with ID 1947. Remaining time: 29999 ms. Selector: ReadPreferenceServerSelector{readPreference=primary}, topology description: {type=UNKNOWN, servers=[{address=172.31.46.124:27017, type=UNKNOWN, state=CONNECTING, exception={com.mongodb.MongoSocketOpenException: Exception opening socket}, caused by {io.netty.channel.AbstractChannel$AnnotatedConnectException: Connection refused: /172.31.46.124:27017}, caused by {java.net.ConnectException: Connection refused}}].
2026-08-03T15:34:04.330358607Z  [chat-service]  [2m2026-08-04T00:34:04.330+09:00[0;39m [32m INFO [traceId=6a70b4ec88e8a8bfd32303f9c7d82a06,spanId=48e6a69c88846701,userId=NONE][0;39m [35m7[0;39m [2m--- [chat-service] [nio-8090-exec-3] [6a70b4ec88e8a8bfd32303f9c7d82a06-48e6a69c88846701] [0;39m[36morg.mongodb.driver.cluster              [0;39m [2m:[0;39m Waiting for server to become available for operation with ID 1990. Remaining time: 29999 ms. Selector: ReadPreferenceServerSelector{readPreference=primary}, topology description: {type=UNKNOWN, servers=[{address=172.31.46.124:27017, type=UNKNOWN, state=CONNECTING, exception={com.mongodb.MongoSocketOpenException: Exception opening socket}, caused by {io.netty.channel.AbstractChannel$AnnotatedConnectException: Connection refused: /172.31.46.124:27017}, caused by {java.net.ConnectException: Connection refused}}].
2026-08-03T15:34:05.333461544Z  [chat-service]  [2m2026-08-04T00:34:05.332+09:00[0;39m [33m WARN [traceId=6a70b4e2511983007eb087a80330f9ba,spanId=801566d8c10da131,userId=NONE][0;39m [35m7[0;39m [2m--- [chat-service] [io-8090-exec-10] [6a70b4e2511983007eb087a80330f9ba-801566d8c10da131] [0;39m[36mo.s.b.a.health.HealthEndpointSupport    [0;39m [2m:[0;39m Health contributor org.springframework.boot.actuate.autoconfigure.health.HealthEndpointConfiguration$AdaptedReactiveHealthContributors$1 (mongo) took 11045ms to respond
2026-08-03T15:34:05.333461544Z  [chat-service]  [2m2026-08-04T00:34:05.332+09:00[0;39m [33m WARN [traceId=6a70b4e2511983007eb087a80330f9ba,spanId=801566d8c10da131,userId=NONE][0;39m [35m7[0;39m [2m--- [chat-service] [io-8090-exec-10] [6a70b4e2511983007eb087a80330f9ba-801566d8c10da131] [0;39m[36mo.s.b.a.health.HealthEndpointSupport    [0;39m [2m:[0;39m Health contributor org.springframework.boot.actuate.autoconfigure.health.HealthEndpointConfiguration$AdaptedReactiveHealthContributors$1 (mongo) took 11045ms to respond
2026-08-03T15:34:05.334221406Z  [chat-service]  [2m2026-08-04T00:34:05.334+09:00[0;39m [33m WARN [traceId=6a70b4d8ad6302c8a55b085b698bac32,spanId=b9fd02410833596e,userId=NONE][0;39m [35m7[0;39m [2m--- [chat-service] [nio-8090-exec-9] [6a70b4d8ad6302c8a55b085b698bac32-b9fd02410833596e] [0;39m[36mo.s.b.a.health.HealthEndpointSupport    [0;39m [2m:[0;39m Health contributor org.springframework.boot.actuate.autoconfigure.health.HealthEndpointConfiguration$AdaptedReactiveHealthContributors$1 (mongo) took 21085ms to respond
2026-08-03T15:34:05.334221406Z  [chat-service]  [2m2026-08-04T00:34:05.334+09:00[0;39m [33m WARN [traceId=6a70b4d8ad6302c8a55b085b698bac32,spanId=b9fd02410833596e,userId=NONE][0;39m [35m7[0;39m [2m--- [chat-service] [nio-8090-exec-9] [6a70b4d8ad6302c8a55b085b698bac32-b9fd02410833596e] [0;39m[36mo.s.b.a.health.HealthEndpointSupport    [0;39m [2m:[0;39m Health contributor org.springframework.boot.actuate.autoconfigure.health.HealthEndpointConfiguration$AdaptedReactiveHealthContributors$1 (mongo) took 21085ms to respond
```

### 메트릭 시계열

| 쿼리 | series | 점 | min | max | last | 값이 0이던 구간 |
|---|---|---:|---:|---:|---:|---|
| `hikaricp_connections_active` | `{__name__=hikaricp_connections_active, application=auth-service, cluster=yogurtte-k3s-prod, container=auth-service, instance=10.42.1.46:8090, job=auth-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=auth-service-5999bb9f5c-hmgp9, pool=HikariPool-1, service=auth-service}` | 241 | 0 | 0 | 0 | **2026-08-03T14:35:30Z ~ 2026-08-03T15:35:30Z** |
| `hikaricp_connections_active` | `{__name__=hikaricp_connections_active, application=chat-service, cluster=yogurtte-k3s-prod, container=chat-service, instance=10.42.1.47:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-fdcc7c776-xf4sv, pool=HikariPool-1}` | 218 | 0 | 1 | 0 | **2026-08-03T14:43:15Z ~ 2026-08-03T15:34:00Z, 2026-08-03T15:35:15Z ~ 2026-08-03T15:35:30Z** |
| `hikaricp_connections_active` | `{__name__=hikaricp_connections_active, application=content-service, cluster=yogurtte-k3s-prod, container=content-service, instance=10.42.1.43:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-6995bb7d94-h2f6n, pool=HikariPool-1}` | 241 | 0 | 0 | 0 | **2026-08-03T14:35:30Z ~ 2026-08-03T15:35:30Z** |
| `hikaricp_connections_active` | `{__name__=hikaricp_connections_active, application=content-service, cluster=yogurtte-k3s-prod, container=content-service, instance=10.42.3.42:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-6995bb7d94-nq9l2, pool=HikariPool-1}` | 241 | 0 | 0 | 0 | **2026-08-03T14:35:30Z ~ 2026-08-03T15:35:30Z** |
| `hikaricp_connections_pending` | `{__name__=hikaricp_connections_pending, application=auth-service, cluster=yogurtte-k3s-prod, container=auth-service, instance=10.42.1.46:8090, job=auth-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=auth-service-5999bb9f5c-hmgp9, pool=HikariPool-1, service=auth-service}` | 241 | 0 | 0 | 0 | **2026-08-03T14:35:30Z ~ 2026-08-03T15:35:30Z** |
| `hikaricp_connections_pending` | `{__name__=hikaricp_connections_pending, application=chat-service, cluster=yogurtte-k3s-prod, container=chat-service, instance=10.42.1.47:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-fdcc7c776-xf4sv, pool=HikariPool-1}` | 218 | 0 | 0 | 0 | **2026-08-03T14:41:15Z ~ 2026-08-03T15:35:30Z** |
| `hikaricp_connections_pending` | `{__name__=hikaricp_connections_pending, application=content-service, cluster=yogurtte-k3s-prod, container=content-service, instance=10.42.1.43:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-6995bb7d94-h2f6n, pool=HikariPool-1}` | 241 | 0 | 0 | 0 | **2026-08-03T14:35:30Z ~ 2026-08-03T15:35:30Z** |
| `hikaricp_connections_pending` | `{__name__=hikaricp_connections_pending, application=content-service, cluster=yogurtte-k3s-prod, container=content-service, instance=10.42.3.42:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-6995bb7d94-nq9l2, pool=HikariPool-1}` | 241 | 0 | 0 | 0 | **2026-08-03T14:35:30Z ~ 2026-08-03T15:35:30Z** |
| `rate(jvm_gc_pause_seconds_sum[5m])` | `{action=end of major GC, application=chat-service, cause=Allocation Failure, cluster=yogurtte-k3s-prod, container=chat-service, gc=MarkSweepCompact, instance=10.42.1.47:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-fdcc7c776-xf4sv}` | 214 | 0 | 0 | 0 | **2026-08-03T14:42:15Z ~ 2026-08-03T15:35:30Z** |
| `rate(jvm_gc_pause_seconds_sum[5m])` | `{action=end of major GC, application=chat-service, cause=Allocation Failure, cluster=yogurtte-k3s-prod, container=chat-service, gc=MarkSweepCompact, instance=10.42.3.43:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-fdcc7c776-qrbc2}` | 2 | 0 | 0 | 0 | **2026-08-03T14:35:30Z ~ 2026-08-03T14:35:45Z** |
| `rate(jvm_gc_pause_seconds_sum[5m])` | `{action=end of minor GC, application=auth-service, cause=G1 Evacuation Pause, cluster=yogurtte-k3s-prod, container=auth-service, gc=G1 Young Generation, instance=10.42.1.46:8090, job=auth-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=auth-service-5999bb9f5c-hmgp9, service=auth-service}` | 241 | 0 | 0.000 | 0 | **2026-08-03T14:35:30Z ~ 2026-08-03T14:45:30Z, 2026-08-03T14:49:45Z ~ 2026-08-03T15:26:30Z, 2026-08-03T15:30:45Z ~ 2026-08-03T15:35:30Z** |
| `rate(jvm_gc_pause_seconds_sum[5m])` | `{action=end of minor GC, application=chat-service, cause=Allocation Failure, cluster=yogurtte-k3s-prod, container=chat-service, gc=Copy, instance=10.42.1.47:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-fdcc7c776-xf4sv}` | 214 | 0.000 | 0.002 | 0.000 | — |
| `rate(jvm_gc_pause_seconds_sum[5m])` | `{action=end of minor GC, application=chat-service, cause=Allocation Failure, cluster=yogurtte-k3s-prod, container=chat-service, gc=Copy, instance=10.42.3.43:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-fdcc7c776-qrbc2}` | 2 | 0.000 | 0.000 | 0.000 | — |
| `rate(jvm_gc_pause_seconds_sum[5m])` | `{action=end of minor GC, application=content-service, cause=Allocation Failure, cluster=yogurtte-k3s-prod, container=content-service, gc=Copy, instance=10.42.1.43:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-6995bb7d94-h2f6n}` | 241 | 0 | 0.000 | 0.000 | **2026-08-03T14:35:30Z ~ 2026-08-03T14:38:45Z, 2026-08-03T14:43:00Z ~ 2026-08-03T14:52:45Z, 2026-08-03T14:57:00Z ~ 2026-08-03T15:06:45Z, 2026-08-03T15:11:00Z ~ 2026-08-03T15:20:45Z, 2026-08-03T15:25:00Z ~ 2026-08-03T15:34:45Z** |
| `rate(jvm_gc_pause_seconds_sum[5m])` | `{action=end of minor GC, application=content-service, cause=Allocation Failure, cluster=yogurtte-k3s-prod, container=content-service, gc=Copy, instance=10.42.3.42:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-6995bb7d94-nq9l2}` | 241 | 0 | 0.000 | 0 | **2026-08-03T14:36:45Z ~ 2026-08-03T14:43:30Z, 2026-08-03T14:47:45Z ~ 2026-08-03T14:57:30Z, 2026-08-03T15:01:45Z ~ 2026-08-03T15:11:30Z, 2026-08-03T15:15:45Z ~ 2026-08-03T15:25:30Z, 2026-08-03T15:29:45Z ~ 2026-08-03T15:35:30Z** |
| `up` | `{__name__=up, app=node-exporter, cluster=yogurtte-k3s-prod, component=metrics, container=node-exporter, instance=ip-172-31-40-241, job=integrations/node_exporter, k8s_cluster_name=yogurtte-k3s-prod, namespace=monitoring, pod=grafana-k8s-monitoring-node-exporter-cph5l, source=kubernetes, workload=DaemonSet/grafana-k8s-monitoring-node-exporter}` | 241 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, app=node-exporter, cluster=yogurtte-k3s-prod, component=metrics, container=node-exporter, instance=ip-172-31-45-39, job=integrations/node_exporter, k8s_cluster_name=yogurtte-k3s-prod, namespace=monitoring, pod=grafana-k8s-monitoring-node-exporter-9kvqt, source=kubernetes, workload=DaemonSet/grafana-k8s-monitoring-node-exporter}` | 241 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, cluster=yogurtte-k3s-prod, container=auth-service, instance=10.42.1.46:8090, job=auth-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=auth-service-5999bb9f5c-hmgp9}` | 241 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, cluster=yogurtte-k3s-prod, container=chat-service, instance=10.42.1.47:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-fdcc7c776-xf4sv}` | 218 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, cluster=yogurtte-k3s-prod, container=content-service, instance=10.42.1.43:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-6995bb7d94-h2f6n}` | 241 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, cluster=yogurtte-k3s-prod, container=content-service, instance=10.42.3.42:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-6995bb7d94-nq9l2}` | 241 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, cluster=yogurtte-k3s-prod, instance=10.42.1.248:8080, job=integrations/kubernetes/kube-state-metrics, k8s_cluster_name=yogurtte-k3s-prod, source=kubernetes}` | 241 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, cluster=yogurtte-k3s-prod, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod}` | 241 | 1 | 1 | 1 | — |
| `mongodb_up` | `{__name__=mongodb_up, cluster=yogurtte-k3s-prod, instance=infra-server, job=mongodb, k8s_cluster_name=yogurtte-k3s-prod}` | 241 | 1 | 1 | 1 | — |
| `kafka_brokers` | `{__name__=kafka_brokers, cluster=yogurtte-k3s-prod, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod}` | 241 | 1 | 1 | 1 | — |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=chat-service-fcm-tokens, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=0, topic=user.fcm-tokens}` | 241 | 0 | 0 | 0 | **2026-08-03T14:35:30Z ~ 2026-08-03T15:35:30Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=chat-service-fcm-tokens, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=1, topic=user.fcm-tokens}` | 241 | 0 | 0 | 0 | **2026-08-03T14:35:30Z ~ 2026-08-03T15:35:30Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=chat-service-fcm-tokens, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=2, topic=user.fcm-tokens}` | 241 | 0 | 0 | 0 | **2026-08-03T14:35:30Z ~ 2026-08-03T15:35:30Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=chat-service-notification-settings, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=0, topic=user.notification-settings}` | 241 | 0 | 0 | 0 | **2026-08-03T14:35:30Z ~ 2026-08-03T15:35:30Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=chat-service-notification-settings, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=1, topic=user.notification-settings}` | 241 | 0 | 0 | 0 | **2026-08-03T14:35:30Z ~ 2026-08-03T15:35:30Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=chat-service-notification-settings, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=2, topic=user.notification-settings}` | 241 | 0 | 0 | 0 | **2026-08-03T14:35:30Z ~ 2026-08-03T15:35:30Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=db-writer, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=0, topic=chat.messages}` | 241 | 0 | 0 | 0 | **2026-08-03T14:35:30Z ~ 2026-08-03T15:35:30Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=db-writer, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=1, topic=chat.messages}` | 241 | 0 | 0 | 0 | **2026-08-03T14:35:30Z ~ 2026-08-03T15:35:30Z** |
| `websocket_active_users` | `{__name__=websocket_active_users, application=chat-service, cluster=yogurtte-k3s-prod, container=chat-service, instance=10.42.1.47:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-fdcc7c776-xf4sv}` | 218 | 0 | 0 | 0 | **2026-08-03T14:41:15Z ~ 2026-08-03T15:35:30Z** |

값이 0이던 구간은 굵게 표시했다 — 프로세스가 사라져 시계열이 꺾인 것이 유일한 신호인 장애가 있다.

