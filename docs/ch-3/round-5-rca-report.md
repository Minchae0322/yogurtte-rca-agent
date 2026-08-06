# RCA Report — `scan-1785976661`

| 항목 | 값 |
|---|---|
| 모드 | rca |
| 질문 | 최근 1시간 안에 앱이 잠깐 버벅였다는 얘기가 있어요. 뭔가 있었는지 봐줘 |
| 시각 | 2026-08-06T01:36:46.592745300Z |
| provider | claude-cli |
| model | `claude-opus-5` · turns 1 |
| prompt | `.\prompts\system-prompt.md` |
| tokens | in 90365 (cacheRead 23,449 · cacheCreate 66,914) / out 15130 · cost $1.0591 |
| elapsed | total 240428ms (tempo 4373 · loki 489 · mimir 852 · assemble 91 · llm 227514) |

## 탐색 (Triage)

| 항목 | 값 |
|---|---|
| 시간창 해석 | 상대 표현 '최근 1시간' |
| 시간창 확신도 | EXACT |
| 스윕 창 | 2026-08-06T00:36:05.494326900Z ~ 2026-08-06T01:36:05.494326900Z |
| 좁힌 창 | 2026-08-06T00:37:41.076260Z ~ 2026-08-06T01:00:00Z |
| 대상 | content-service, chat-service |
| traceId | 6a73d7cd82e6e12dd5c7a7ca51858e6e |
| 트레이스 후보 | 7건 |
| 장애 후보 | 7건 · 선택 INC-2, INC-3, INC-4, INC-6, INC-7 |
| 계획 파싱 | 성공 |
| 스윕 컨텍스트 | **후보만 — 원본 제외 (B)** |
| prompt | `.\prompts\triage-prompt.md` |
| tokens | in 33131 / out 1886 · cost $0.1829 |
| chars | 컨텍스트 3,660 + 프롬프트 1,399 = **5,059** |
| elapsed | survey 1944ms · llm 39016ms |

**선정 이유**: 약 5분 간격으로 두 번, content-service 댓글 쓰기 지연과 chat-service 인증 필터체인 지연이 초 단위로 동반 발생한 동일 지문이므로 상·하류를 함께 묶어 하나의 지연 장애로 조사한다.

**근거**

- Tempo 에러 검색 0건 · 지연 검색 7건 — 에러 없는 순수 지연 장애, '잠깐 버벅임' 증상과 일치
- INC-2: content-service POST /battles/{battleId}/items/{itemId}/comments 515,150ms, 트레이스 구간 00:39:41~00:48:16(=515s)과 정확히 일치하는 단일 장기 요청
- INC-4: chat-service security filterchain before 25,386ms / 17,613ms / 6,510ms, 00:47:44~00:48:10 — INC-2 종료 시각과 26초 이내로 겹침
- INC-6+INC-7: 00:53:34~00:53:59에 content-service 24,073ms와 chat-service filterchain 15,879ms/5,751ms가 8초 간격으로 동시 발생 — 1차와 동일한 지문의 재발
- INC-3: chat-service ERROR/WARN 4건(00:45~00:55)이 두 지연 클러스터를 모두 덮는 구간에 존재
- up / mongodb_up / kafka_brokers / websocket_active_users 이상 0건 — 파드·DB·브로커·접속자 급감은 배제되고 요청 경로 내부 지연만 남음

**스윕이 찾은 트레이스** (고른 것은 6a73d7cd82e6e12dd5c7a7ca51858e6e)

| traceId | 채널 | root service | root span | ms |
|---|---|---|---|---:|
| `6a73db20d7504a562163749f221b664b` | slow | chat-service | security filterchain before | 5751 |
| `6a73db166e5322a7df383f8412a96c4d` | slow | chat-service | security filterchain before | 15879 |
| `6a73db0e28f9ff3fc4cce81a89cfb39e` | slow | content-service | http post /battles/{battleId}/items/{itemId}/comments | 24073 |
| `6a73d9c39aaf7193897c86174a60f421` | slow | chat-service | security filterchain before | 6510 |
| `6a73d9b830a513e3385f8985bbc2c359` | slow | chat-service | security filterchain before | 17613 |
| `6a73d9b04dcd773a41a3a8263fde1b79` | slow | chat-service | security filterchain before | 25386 |
| `6a73d7cd82e6e12dd5c7a7ca51858e6e` ←선택 | slow | content-service | http post /battles/{battleId}/items/{itemId}/comments | 515150 |

**장애 후보** (코드가 신호를 묶은 것 · 창은 여기서 계산됨)

## INC-1  kafka  |  kafka_consumergroup_lag
- 구간: 2026-08-06T00:36:05Z ~ 2026-08-06T00:46:05Z  (MIMIR · 집계 해상도만큼 흐림)
- kafka_consumergroup_lag{consumergroup=notification-processors, partition=3, topic=user.notifications} 0 → 8
- kafka_consumergroup_lag{consumergroup=notification-processors, partition=3, topic=user.notifications} 8 → 25
- 같은 시각의 다른 후보: INC-2, INC-3  (인과 여부는 판단하지 않았다)

## INC-2  content-service  |  http post /battles/{battleId}/items/{itemId}/comments
- 구간: 2026-08-06T00:39:41.076260Z ~ 2026-08-06T00:48:16.226260Z  (TEMPO · 시각 정확)
- content-service http post /battles/{battleId}/items/{itemId}/comments 515,150ms (slow 채널)
- traceId: 6a73d7cd82e6e12dd5c7a7ca51858e6e
- 같은 시각의 다른 후보: INC-1, INC-3, INC-4  (인과 여부는 판단하지 않았다)

## INC-3  chat-service  |  ERROR/WARN
- 구간: 2026-08-06T00:45:00Z ~ 2026-08-06T00:55:00Z  (LOKI · 집계 해상도만큼 흐림)
- ERROR/WARN 3건 (2026-08-06T00:45:00Z ~ 2026-08-06T00:50:00Z)
- ERROR/WARN 1건 (2026-08-06T00:50:00Z ~ 2026-08-06T00:55:00Z)
- 같은 시각의 다른 후보: INC-1, INC-2, INC-4, INC-5, INC-6, INC-7  (인과 여부는 판단하지 않았다)

## INC-4  chat-service  |  security filterchain before
- 구간: 2026-08-06T00:47:44.632550Z ~ 2026-08-06T00:48:10.018900Z  (TEMPO · 시각 정확)
- chat-service security filterchain before 25,386ms (slow 채널)
- chat-service security filterchain before 17,613ms (slow 채널)
- chat-service security filterchain before 6,510ms (slow 채널)
- traceId: 6a73d9b04dcd773a41a3a8263fde1b79, 6a73d9b830a513e3385f8985bbc2c359, 6a73d9c39aaf7193897c86174a60f421
- 같은 시각의 다른 후보: INC-2, INC-3  (인과 여부는 판단하지 않았다)

## INC-5  kafka  |  kafka_consumergroup_lag
- 구간: 2026-08-06T00:51:05Z ~ 2026-08-06T01:01:05Z  (MIMIR · 집계 해상도만큼 흐림)
- kafka_consumergroup_lag{consumergroup=notification-processors, partition=3, topic=user.notifications} 25 → 1
- kafka_consumergroup_lag{consumergroup=notification-processors, partition=3, topic=user.notifications} 1 → 0
- 같은 시각의 다른 후보: INC-3, INC-6, INC-7  (인과 여부는 판단하지 않았다)

## INC-6  content-service  |  http post /battles/{battleId}/items/{itemId}/comments
- 구간: 2026-08-06T00:53:34.957148Z ~ 2026-08-06T00:53:59.030148Z  (TEMPO · 시각 정확)
- content-service http post /battles/{battleId}/items/{itemId}/comments 24,073ms (slow 채널)
- traceId: 6a73db0e28f9ff3fc4cce81a89cfb39e
- 같은 시각의 다른 후보: INC-3, INC-5, INC-7  (인과 여부는 판단하지 않았다)

## INC-7  chat-service  |  security filterchain before
- 구간: 2026-08-06T00:53:42.241455Z ~ 2026-08-06T00:53:58.120455Z  (TEMPO · 시각 정확)
- chat-service security filterchain before 15,879ms (slow 채널)
- chat-service security filterchain before 5,751ms (slow 채널)
- traceId: 6a73db166e5322a7df383f8412a96c4d, 6a73db20d7504a562163749f221b664b
- 같은 시각의 다른 후보: INC-3, INC-5, INC-6  (인과 여부는 판단하지 않았다)

**기각한 후보**

- INC-1 — notification-processors 파티션 3의 lag가 0→25로 오르는 비동기 알림 경로 신호이고, 최대 25라는 절대량이 사용자 체감 버벅임을 만들 규모가 아니다.
- INC-5 — 같은 lag가 25→1→0으로 자연 회복된 정상화 신호이며, 원인이 아니라 INC-1의 해소 구간이다.

**보정 기록**

- 창을 후보 [INC-2, INC-3, INC-4, INC-6, INC-7] 의 신호 시각에서 계산했다 (2026-08-06T00:37:41.076260Z ~ 2026-08-06T01:00:00Z)

**스윕 무신호/실패**

- Tempo 에러 검색 '{ status = error }'이 이 창에서 0건이다. 트레이스가 생성되지 않는 장애(컨슈머 전멸·파드 부재)이거나 에러가 아닌 형태의 장애(200 성공 + 지연)일 수 있으니 이 사실 자체를 근거로 쓸 것.

## 수집 범위 (Coverage)

- **window**: 2026-08-06T00:37:41.076260Z ~ 2026-08-06T01:00:00Z (1338s)
- **trace**: 76,307B / 82 spans
- **창 안 후보 트레이스**: 10건 / 76,307B
- **logs**: errwarn=26,097B · traceId=24,766B
- **metrics**: 8 수집 / 151,557B, 누락 [sum(rate(http_server_requests_seconds_count{application="content-service", status="401"}[1m]))]
- **context**: 108,747 chars (+ 시스템 프롬프트 805 chars)

### 토큰 축 (개선 지표)

| 단계 | 총 in | chars | 컨텍스트 토큰 |
|---|---:|---:|---:|
| 탐색 | 33,131 | 5,059 | 4,050 |
| 분석 | 90,365 | 109,552 | 61,284 |
| **합계** | **123,496** | | **65,334** |

- **overheadTokens 29,081 tok — 이 회차에 실측**(1자 프롬프트 프로브, 같은 명령·모델·샌드박스).
  호출마다 한 번씩 얹히므로 단계별로 뺀다. **다른 날 상수를 쓰지 않았다 = `█ 실측`.**
- contextTokens (count_tokens API): 측정 안 됨 — 구독 CLI 경로엔 API 키가 없다
- 비용은 **API 환산 추정치**다. 구독 계정으로 호출하므로 실제 청구액이 아니다.

## 수집 실패/누락

- Metric 'sum(rate(http_server_requests_seconds_count{application="content-service", status="401"}[1m]))' returned no series in this window; skipped.

---

관측 데이터만으로 정리했다. 두 개의 독립된 사건이 창 안에 있다.

## 1. 원인 후보 랭킹 (최대 3개)

1. **MongoDB(172.31.46.124:27017) 계획/비계획 셧다운·재기동 (00:53:34~00:53:57 UTC)** → chat-service 알림 처리 및 HTTP 요청이 최대 24초 블로킹
2. **chat-service 파드 교체 (dnbs9 → kh2dl, 00:40:11~00:47:09 UTC)** → `notification-processors` 컨슈머 공백으로 `user.notifications` p3 랙 25 누적, 알림 1건 494초 지연
3. **재기동 직후 chat-service의 Redis 경로 지연 (00:47:44~00:48:10 UTC)** → 헬스 컨트리뷰터 15.7초, 요청 24.9초. 원인 미확정

---

## 2. 후보별 근거

### 후보 1 — MongoDB 셧다운/재기동

**근거**
- 로그 원문: `com.mongodb.MongoNodeIsRecoveringException: Command failed with error 11600 (InterruptedAtShutdown): 'interrupted at shutdown' on server 172.31.46.124:27017` — 00:53:34.115~.116 UTC, **x2회**.
- 이어서 `com.mongodb.MongoSocketOpenException: Exception opening socket` / `Caused by: io.netty.channel.AbstractChannel$AnnotatedConnectException: Connection refused: /172.31.46.124:27017` — 00:53:35.118, 00:53:42.256 (**x2회**). 셧다운 → 포트 소멸 순서로, 프로세스 재기동이 맞다.
- `org.mongodb.driver.cluster : Exception in monitor thread while connecting to server 172.31.46.124:27017` **x4회 · 00:53:34.115 ~ 00:53:42.256**.
- 트레이스 `6a73db0e...`: `receive`(kafka) 24.008s, 그 아래 `user-notification-service#process-notification` 23.999s. 시작 00:53:35.027인데 **첫 Mongo span `insert toychat`이 00:53:57.738에야 시작** — 중간 22.71초가 통째로 서버 셀렉션 대기다.
- 같은 창의 정상 기준선(00:53:57 이후 동일 트레이스 내부): `insert` 15.3ms, `user_sync_status.find` 1.5~6.9ms, `KEYS` 1.0~1.7ms. 즉 Mongo 복귀 후에는 정상.
- 사용자 요청 경로도 물렸다: `6a73db16...` `secured request` **15.878초**(00:53:42.244 시작, `nio-8090-exec-2`), `6a73db20...` `secured request` **5.751초**(00:53:52.283 시작, `nio-8090-exec-5`). 둘 다 동일 traceId의 `Waiting for server to become available` 로그를 동반한다.

**대기·지연 판정**
| 대기 구간 | 실측 대기 | 상한(설정값) | 만료 여부 | 최종 상태 |
|---|---|---|---|---|
| `6a73db0e` 알림 처리의 Mongo 서버 셀렉션 | **22.71초** (00:53:35.027→00:53:57.738) | 30초 — 로그 `Remaining time: 29994 ms` (operation ID 383) | **만료 안 함** | **성공.** `insert` 완료, `[push] 멀티캐스트 결과: tokens=1, success=1, failure=0`(00:53:59.022), `[notify] 알림 처리 완료: userId=7, ... id=6a73db0fd2c54bf44544e29d`, `[kafka] 알림 처리 완료`(00:53:59.030). 재시도·DLQ 없음 |
| `6a73db16` HTTP 요청 (`nio-8090-exec-2`) | **15.878초** | 30초 — `Remaining time: 29999 ms` (ID 401) | **만료 안 함** | **완료.** Mongo 복귀 시점(00:53:57.7)에 풀림. 다만 응답 본문/상태코드 span 속성이 없어 **성공/실패는 판정 불가** (동일 traceId에 `mongo took 15774ms to respond` WARN만 존재) |
| `6a73db20` HTTP 요청 (`nio-8090-exec-5`) | **5.751초** | 30초 — `Remaining time: 29999 ms` (ID 444) | **만료 안 함** | **완료.** 상태코드 근거 없어 성공/실패 **판정 불가** |
| chat-service HikariPool-1 커넥션 점유 (`6a73db0e`) | **24.007초** 점유 (acquired 00:53:35.025 → commit 00:53:59.028) | 풀 획득 타임아웃 설정값 미수집 → **상한 대조 불가** (획득 자체는 2.2ms로 즉시 성공) | 해당 없음 | **성공 (commit).** 단, MySQL 커넥션을 잡은 채 Mongo·Redis·FCM 외부 호출을 24초간 수행 |

**확신도: 높음** (앱 측 예외 원문과 트레이스 공백이 초 단위로 일치)

**반증 데이터**
- `mongodb_up{instance=infra-server}` 및 `up{job=mongodb}`가 **전 구간 1, 변화 없음** (90점/22분 ≒ 15초 간격이므로 ~23초 중단은 잡혔어야 한다). 익스포터가 실제 mongod와 다른 경로로 보거나 값이 stale일 가능성. 앱 측 `Connection refused` 원문이 더 강한 증거라 판단했으나, **이 불일치 때문에 "몇 초간 완전 다운"의 정확한 구간 폭은 확정하지 못했다.**
- content-service는 무영향: 같은 시각 `POST /battles/{battleId}/items/{itemId}/comments` **46.857ms, status 200, outcome SUCCESS**. Mongo를 쓰지 않으므로 정합적이다.

---

### 후보 2 — chat-service 파드 교체로 인한 컨슈머 공백

**근거**
- 메트릭 시리즈 자체가 파드 교체를 보여준다: `up{pod=chat-service-fdcc7c776-dnbs9}` 11점 **00:37:41~00:40:11에서 종료**, `up{pod=chat-service-fdcc7c776-kh2dl}` 44점 **00:49:11부터 시작**. `hikaricp_*`, `websocket_active_users`, `jvm_gc_pause` 모두 같은 경계.
- 신규 파드 부팅 로그: `2026-08-06T09:47:09.623+09:00 WARN [traceId=NONE] 7 [ main] JpaBaseConfiguration$JpaWebConfiguration : spring.jpa.open-in-view is enabled by default...` (= 00:47:09 UTC, `main` 스레드 = 기동 시점).
- `kafka_consumergroup_lag{consumergroup=notification-processors, topic=user.notifications, partition=3}`: 00:37:41=0 → **00:40:11=2 → 00:42:41=17 → 00:45:11=25 → 00:47:41=25 → 00:49:11 이후 0**. 랙 상승 개시와 구 파드 소멸이 같은 스크랩(00:40:11)이다.
- 트레이스 `6a73d7cd...`: content-service가 **00:39:41.166**에 `[notify] 알림 발행 성공: userId=7, ..., partition=3, offset=1130`. 그런데 chat-service의 `receive` span 시작은 **00:47:55.722**.

**대기·지연 판정**
| 대기 구간 | 실측 대기 | 상한(설정값) | 만료 여부 | 최종 상태 |
|---|---|---|---|---|
| offset 1130 발행→소비 | **494.6초** (00:39:41.166 → 00:47:55.722) | 컨슈머 `max.poll.interval.ms`·토픽 retention 미수집 → **상한 대조 불가** | **판정 불가** | **성공.** 20.505초 처리 후 `[push] 멀티캐스트 결과: tokens=1, success=1, failure=0`(00:48:16.121), `[kafka] 알림 처리 완료`(00:48:16.222). 랙 0 복귀, `notification-recovery`/`user.notifications.dlq` 랙 **전 구간 0** → 폐기·DLQ 없음 |
| 같은 트레이스의 HikariPool-1 점유 | **17.596초** (acquired 00:47:58.630 → commit 00:48:16.135) | 미수집 → 대조 불가 | 해당 없음 | **성공 (commit).** 내부 지연은 FCM 호출이 지배적(00:48:10.222 발송 → 00:48:16.121 결과, ≒5.9초) |

**확신도: 높음** (파드 교체 사실과 랙 곡선, 소비 지연이 모두 같은 시각으로 수렴)

**반증 데이터**
- 파드가 왜 교체됐는지 근거가 없다 — 배포인지 OOMKill인지 프로브 실패인지 **데이터 부족**. kube event / 재시작 사유를 못 봤으므로 "장애"가 아니라 "정상 배포"일 수 있다.
- 사용자 체감 근거는 약하다: `websocket_active_users`가 **양 파드 모두 전 구간 0**이고, 로그도 `[notify] 사용자 오프라인 상태로 WebSocket 전송 스킵: userId=7`. 즉 이 구간 접속 사용자가 관측되지 않았다.
- 구 파드 `dnbs9`의 `hikaricp_connections_pending`/`active`도 소멸 직전까지 0이라, 종료 원인이 부하라는 흔적은 없다.

---

### 후보 3 — 재기동 직후 Redis 경로 지연 (원인 미확정)

**근거**
- WARN 원문 2건: `Health contributor ...AdaptedReactiveHealthContributors$1 (redis) took 15709ms to respond` (00:48:08.630, traceId `6a73d9b0...`), `... (redis) took 15503ms to respond` (00:48:08.719, traceId `6a73d9b8...`).
- 해당 traceId의 서버 span: `6a73d9b0` `secured request` **24.911초**, `6a73d9b8` **17.590초**, 인접한 `6a73d9c3` **6.111초**. 모두 chat-service, `nio-8090-exec-*`.
- 같은 창의 Redis 명령 실측은 그렇게 느리지 않다: `KEYS 0` **89.3ms / 96.6ms** (00:48:08.428, 00:48:09.420). Mongo도 91~111ms로 정상 기준선(1.5~15ms)의 10배 수준.
- 두 헬스체크가 00:48:08.63/.72로 거의 동시에 풀리고, 두 요청이 00:48:10.02에 함께 끝난다 → 개별 명령 지연이 아니라 **공유 자원(커넥션/스케줄러) 해제 시점에 동조**한 모양이다.

**대기·지연 판정**
| 대기 구간 | 실측 대기 | 상한(설정값) | 만료 여부 | 최종 상태 |
|---|---|---|---|---|
| Redis 헬스 컨트리뷰터 | **15.709초 / 15.503초** | Redis 클라이언트 타임아웃 **2초** (span 속성 `server.address: redis://172.31.46.124?timeout=2s`) | **개별 명령 기준이면 초과.** 다만 실측 `KEYS`는 89~97ms로 2초 미만 → 15.7초는 명령 실행이 아니라 큐잉/커넥션 획득/리액티브 스케줄러 대기로 **추정**. 헬스 컨트리뷰터 자체의 타임아웃 설정값은 미수집 → **만료 여부 판정 불가** | **완료.** WARN 문구가 `took ... to respond`이므로 응답은 반환됨. UP/DOWN 값 근거 없음 → 성공/실패 **판정 불가** |

**확신도: 낮음** (현상은 확실하나 15.7초가 어디서 소모됐는지 데이터가 없다)

**반증 데이터**
- `up{job=redis, instance=infra-server}` **전 구간 1**, Redis 명령 span은 모두 100ms 미만. Redis 서버 자체 장애는 반증된다.
- 같은 시각 content-service의 Redis `GET`은 **0.787ms / 0.807ms** — 동일 Redis(172.31.46.124)에 붙는 다른 서비스는 멀쩡했다. 즉 Redis 공용 인프라 문제가 아니라 **chat-service 신규 파드의 클라이언트 측(콜드스타트/커넥션 초기화) 문제** 쪽이 유력하다.
- `jvm_gc_pause`는 무죄: kh2dl의 minor GC rate 최대 **0.00103**(≒0.1%). GC 스톨로 15초를 설명할 수 없다.

---

### 공통 반증 / 데이터 신뢰도 경고
- `hikaricp_connections_active`·`hikaricp_connections_pending`이 **모든 파드에서 전 구간 0**인데, 트레이스는 chat-service가 커넥션을 **24.007초·17.596초 점유**했음을 `acquired`/`commit` 이벤트로 보여준다. 15초 스크랩 간격으로도 24초 점유는 최소 1회 잡혀야 한다. **이 두 게이지는 이번 분석에서 근거로 쓸 수 없다** — 특히 "커넥션 풀 고갈은 없었다"는 결론을 이 메트릭으로 확정하지 마라.
- 수집 실패: `http_server_requests_seconds_count{application="content-service", status="401"}` 시리즈 없음. 인증 실패 급증 가설은 검증 불가지만, 이번 증상과 연결할 다른 근거도 없다.
- chat-service의 `http_server_requests` 계열 메트릭이 아예 없어, **영향받은 요청 수·비율은 산출 불가**. 위 지연은 샘플 트레이스 5건에서만 확인된 것이다.

---

## 3. 권장 다음 조치

### 이미 발생한 피해 — 복구 **불필요** (이미 자연 복구됨)
- 알림 2건(offset 1130, 1156) 모두 **최종 전달 성공**: FCM `success=1, failure=0`, messageId 발급(`.../messages/1785977295669348`, `.../messages/1785977638482157`), Kafka 커밋 완료.
- 유실 없음: `user.notifications` 랙 0 복귀, `user.notifications.dlq`(`notification-recovery`) 랙 **전 구간 0**, `chat.messages` 계열 랙 전부 0.
- content-service 쓰기 경로는 무손상: 댓글 298·324 모두 `commit` + `status 200`.
- **재발송하지 마라.** 이미 전달된 건이라 중복 푸시만 만든다. 피해는 "지연"(494초 / 22.7초)이며 지연은 사후 복구 대상이 아니다.
- 단, `6a73db16`·`6a73db20`의 HTTP 응답 결과가 성공인지 실패인지 근거가 없다. 이 두 건이 사용자 요청이었다면 **실패 응답을 받았을 가능성은 배제 못 함 → 판정 불가**.

### 재발 방지
1. **MongoDB 단일 인스턴스 제거 또는 재기동 절차화.** 지금은 mongod 프로세스 재시작 = chat-service 알림 경로 전면 정지다. 레플리카셋 구성이 근본책이고, 최소한 재기동 사유부터 확인해야 한다(계획된 것인지 크래시인지 아직 모름).
2. **`processNotification`의 트랜잭션 경계 축소.** `HikariPool-1` MySQL 커넥션을 잡은 채 Mongo·Redis·FCM(외부 HTTP)을 호출해 24초를 점유했다. 외부 I/O를 트랜잭션 밖으로 빼라. 지금은 풀 크기 여유로 버텼을 뿐, 동시 알림이 몇 건만 겹쳐도 MySQL 풀 고갈로 번진다.
3. **Mongo `serverSelectionTimeout` 30초를 요청 스레드에 그대로 물리지 마라.** `nio-8090-exec-*`가 30초까지 잡히면 톰캣 스레드풀이 먼저 마른다. 요청 경로는 수 초로 낮추고, 컨슈머 경로만 길게 가져가는 편이 낫다.
4. **actuator 헬스 컨트리뷰터에 타임아웃을 걸어라.** redis 15.7초, mongo 15.8초가 헬스 응답을 잡았다. 이게 readiness/liveness 프로브에 물려 있으면 인프라 흔들림이 곧 파드 재시작으로 증폭된다(후보 2의 파드 교체 원인일 수 있으나 **미확인**).
5. **`KEYS` 제거.** `UserNotificationWebSocketSender`/`PushDispatcher`가 알림마다 `KEYS 0`을 친다. Redis 단일 스레드를 O(N)으로 막는 호출이고, 실측도 정상 구간 1ms 대비 이 창에서 89~97ms였다. `SCAN` 또는 세션 레지스트리 셋으로 교체.
6. **chat-service 단일 파드 해소.** 파드 1개라 교체 중 컨슈머 그룹 멤버가 0이 되고 곧장 랙이 쌓인다. replicas ≥ 2 + PodDisruptionBudget.
7. **알림 E2E 지연 알람 추가.** 494초 지연이 아무 알람도 울리지 않았다. `kafka_consumergroup_lag{topic="user.notifications"} > 0` 이 N분 지속 시 알람이 최소선이다.

### 복구 확인
- **이미 관측으로 확인된 것**: Mongo는 00:53:57.738부터 정상(`insert` 15.3ms, `find` 1.5~6.9ms). Kafka 랙 00:49:11 이후 전 파티션 0. FCM 발송 성공. chat-service `up=1` 유지(00:49:11~00:59:56). content-service 200 응답.
- **아직 확인 못 해 추가 수집이 필요한 것** (아래가 없어 위 확신도를 낮춰 잡았다):
  - `kubectl get events` / `kubectl describe pod chat-service-*` — **파드 교체 사유**(배포 vs OOMKilled vs 프로브 실패). 후보 2의 성격이 여기서 갈린다.
  - mongod 자체 로그 — 00:53:34 셧다운이 계획된 것인지, 그리고 `mongodb_up=1` 이 왜 떨어지지 않았는지(익스포터 신뢰성).
  - 172.31.46.124(= Mongo·Redis 동거 호스트) 노드 리소스 메트릭 — 00:47:55~00:48:16의 Mongo/Redis 동반 10~100배 지연이 호스트발인지 클라이언트발인지.
  - chat-service `http_server_requests_seconds_*` — 영향받은 요청 수와 상태코드 분포. 지금은 트레이스 5건이 전부다.
  - Hikari `connectionTimeout`, Mongo 클라이언트 옵션, readiness/liveness 프로브 정의 — 위 "만료 여부 판정 불가" 항목들을 닫으려면 필요.
  - `hikaricp_*` 게이지가 왜 24초 점유 중에도 0인지 — 메트릭 파이프라인 점검.

---

## 관측 증거 (Evidence)

> 이 조사가 실제로 본 값이다. 원본 응답 전체는 `reports/raw/scan-1785976661-*.json`에 있다.

### 호출 그래프 (트레이스에서 추출)

```
chat-service --db--> mongodb  14회  최대 110.9ms  [insert, find]
chat-service --db--> redis  4회  최대 96.6ms  [KEYS]
content-service --db--> redis  3회  최대 0.8ms  [GET, INFO]
chat-service --jdbc--> mysql/content (HikariPool-1)  12회  최대 24007.3ms
    events: acquired, commit
content-service --jdbc--> mysql/content (HikariPool-1)  16회  최대 68.4ms
    events: acquired, commit
content-service --messaging--> kafka/user.notifications  2회  최대 15.8ms  [publish]
kafka/user.notifications --messaging--> chat-service  2회  최대 24008.4ms  [receive]
```

### span (duration 상위 15 / 전체 82)

| ms | service | span | 시작 |
|---:|---|---|---|
| 24911.50 | chat-service | `secured request` | 2026-08-06T00:47:45.107119Z |
| 24008.41 | chat-service | `receive` | 2026-08-06T00:53:35.022278Z |
| 24007.33 | chat-service | `connection` | 2026-08-06T00:53:35.023007Z |
| 23999.55 | chat-service | `user-notification-service#process-notification` | 2026-08-06T00:53:35.027183Z |
| 20505.00 | chat-service | `receive` | 2026-08-06T00:47:55.722081Z |
| 17595.85 | chat-service | `connection` | 2026-08-06T00:47:58.624549Z |
| 17589.95 | chat-service | `secured request` | 2026-08-06T00:47:52.429445Z |
| 17212.14 | chat-service | `user-notification-service#process-notification` | 2026-08-06T00:47:58.917952Z |
| 15877.59 | chat-service | `secured request` | 2026-08-06T00:53:42.243707Z |
| 6708.25 | chat-service | `push-dispatcher#dispatch` | 2026-08-06T00:48:09.418255Z |
| 6111.16 | chat-service | `secured request` | 2026-08-06T00:48:03.819092Z |
| 5750.59 | chat-service | `secured request` | 2026-08-06T00:53:52.282535Z |
| 1181.45 | chat-service | `push-dispatcher#dispatch` | 2026-08-06T00:53:57.843555Z |
| 1104.06 | chat-service | `user-notification-web-socket-sender#send-notificat` | 2026-08-06T00:48:07.422961Z |
| 398.75 | chat-service | `security filterchain before` | 2026-08-06T00:48:03.420146Z |

### 로그 원문 (60 / 전체 135줄)

전체가 상한을 넘어 ERROR/WARN 줄을 우선 발췌했다. 나머지는 원본 파일에 있다.

```
2026-08-06T00:47:09.624127059Z  [chat-service]  [2m2026-08-06T09:47:09.623+09:00[0;39m [33m WARN [traceId=NONE,spanId=NONE,userId=NONE][0;39m [35m7[0;39m [2m--- [chat-service] [           main] [                                                 ] [0;39m[36mJpaBaseConfiguration$JpaWebConfiguration[0;39m [2m:[0;39m spring.jpa.open-in-view is enabled by default. Therefore, database queries may be performed during view rendering. Explicitly configure spring.jpa.open-in-view to disable this warning
2026-08-06T00:48:08.719080655Z  [chat-service]  [2m2026-08-06T09:48:08.630+09:00[0;39m [33m WARN [traceId=6a73d9b04dcd773a41a3a8263fde1b79,spanId=36b2ebc37468686b,userId=NONE][0;39m [35m7[0;39m [2m--- [chat-service] [nio-8090-exec-1] [6a73d9b04dcd773a41a3a8263fde1b79-36b2ebc37468686b] [0;39m[36mo.s.b.a.health.HealthEndpointSupport    [0;39m [2m:[0;39m Health contributor org.springframework.boot.actuate.autoconfigure.health.HealthEndpointConfiguration$AdaptedReactiveHealthContributors$1 (redis) took 15709ms to respond
2026-08-06T00:48:08.719080655Z  [chat-service]  [2m2026-08-06T09:48:08.630+09:00[0;39m [33m WARN [traceId=6a73d9b04dcd773a41a3a8263fde1b79,spanId=36b2ebc37468686b,userId=NONE][0;39m [35m7[0;39m [2m--- [chat-service] [nio-8090-exec-1] [6a73d9b04dcd773a41a3a8263fde1b79-36b2ebc37468686b] [0;39m[36mo.s.b.a.health.HealthEndpointSupport    [0;39m [2m:[0;39m Health contributor org.springframework.boot.actuate.autoconfigure.health.HealthEndpointConfiguration$AdaptedReactiveHealthContributors$1 (redis) took 15709ms to respond
2026-08-06T00:48:08.721481543Z  [chat-service]  [2m2026-08-06T09:48:08.719+09:00[0;39m [33m WARN [traceId=6a73d9b830a513e3385f8985bbc2c359,spanId=bdc3b5811970a66c,userId=NONE][0;39m [35m7[0;39m [2m--- [chat-service] [nio-8090-exec-2] [6a73d9b830a513e3385f8985bbc2c359-bdc3b5811970a66c] [0;39m[36mo.s.b.a.health.HealthEndpointSupport    [0;39m [2m:[0;39m Health contributor org.springframework.boot.actuate.autoconfigure.health.HealthEndpointConfiguration$AdaptedReactiveHealthContributors$1 (redis) took 15503ms to respond
2026-08-06T00:48:08.721481543Z  [chat-service]  [2m2026-08-06T09:48:08.719+09:00[0;39m [33m WARN [traceId=6a73d9b830a513e3385f8985bbc2c359,spanId=bdc3b5811970a66c,userId=NONE][0;39m [35m7[0;39m [2m--- [chat-service] [nio-8090-exec-2] [6a73d9b830a513e3385f8985bbc2c359-bdc3b5811970a66c] [0;39m[36mo.s.b.a.health.HealthEndpointSupport    [0;39m [2m:[0;39m Health contributor org.springframework.boot.actuate.autoconfigure.health.HealthEndpointConfiguration$AdaptedReactiveHealthContributors$1 (redis) took 15503ms to respond
2026-08-06T00:53:34.115753955Z  [chat-service]  [2m2026-08-06T09:53:34.106+09:00[0;39m [32m INFO [traceId=NONE,spanId=NONE,userId=NONE][0;39m [35m7[0;39m [2m--- [chat-service] [31.46.124:27017] [                                                 ] [0;39m[36morg.mongodb.driver.cluster              [0;39m [2m:[0;39m Exception in monitor thread while connecting to server 172.31.46.124:27017
2026-08-06T00:53:34.115783511Z  [chat-service]  com.mongodb.MongoNodeIsRecoveringException: Command failed with error 11600 (InterruptedAtShutdown): 'interrupted at shutdown' on server 172.31.46.124:27017. The full response is {"ok": 0.0, "errmsg": "interrupted at shutdown", "code": 11600, "codeName": "InterruptedAtShutdown"}
2026-08-06T00:53:34.115786802Z  [chat-service]  at com.mongodb.internal.connection.ProtocolHelper.createSpecialException(ProtocolHelper.java:264) ~[mongodb-driver-core-5.5.1.jar!/:na]
2026-08-06T00:53:34.115806014Z  [chat-service]  at com.mongodb.internal.connection.ProtocolHelper.getCommandFailureException(ProtocolHelper.java:206) ~[mongodb-driver-core-5.5.1.jar!/:na]
2026-08-06T00:53:34.115811614Z  [chat-service]  at com.mongodb.internal.connection.InternalStreamConnection.receiveCommandMessageResponse(InternalStreamConnection.java:520) ~[mongodb-driver-core-5.5.1.jar!/:na]
2026-08-06T00:53:34.115814804Z  [chat-service]  at com.mongodb.internal.connection.InternalStreamConnection.receive(InternalStreamConnection.java:469) ~[mongodb-driver-core-5.5.1.jar!/:na]
2026-08-06T00:53:34.115818189Z  [chat-service]  at com.mongodb.internal.connection.DefaultServerMonitor$ServerMonitor.lookupServerDescription(DefaultServerMonitor.java:249) ~[mongodb-driver-core-5.5.1.jar!/:na]
2026-08-06T00:53:34.115821087Z  [chat-service]  at com.mongodb.internal.connection.DefaultServerMonitor$ServerMonitor.run(DefaultServerMonitor.java:176) ~[mongodb-driver-core-5.5.1.jar!/:na]
2026-08-06T00:53:34.116350422Z  [chat-service]  [2m2026-08-06T09:53:34.106+09:00[0;39m [32m INFO [traceId=NONE,spanId=NONE,userId=NONE][0;39m [35m7[0;39m [2m--- [chat-service] [31.46.124:27017] [                                                 ] [0;39m[36morg.mongodb.driver.cluster              [0;39m [2m:[0;39m Exception in monitor thread while connecting to server 172.31.46.124:27017
2026-08-06T00:53:34.116364364Z  [chat-service]  com.mongodb.MongoNodeIsRecoveringException: Command failed with error 11600 (InterruptedAtShutdown): 'interrupted at shutdown' on server 172.31.46.124:27017. The full response is {"ok": 0.0, "errmsg": "interrupted at shutdown", "code": 11600, "codeName": "InterruptedAtShutdown"}
2026-08-06T00:53:34.116367792Z  [chat-service]  at com.mongodb.internal.connection.ProtocolHelper.createSpecialException(ProtocolHelper.java:264) ~[mongodb-driver-core-5.5.1.jar!/:na]
2026-08-06T00:53:34.116370633Z  [chat-service]  at com.mongodb.internal.connection.ProtocolHelper.getCommandFailureException(ProtocolHelper.java:206) ~[mongodb-driver-core-5.5.1.jar!/:na]
2026-08-06T00:53:34.116373767Z  [chat-service]  at com.mongodb.internal.connection.InternalStreamConnection.receiveCommandMessageResponse(InternalStreamConnection.java:520) ~[mongodb-driver-core-5.5.1.jar!/:na]
2026-08-06T00:53:34.116376414Z  [chat-service]  at com.mongodb.internal.connection.InternalStreamConnection.receive(InternalStreamConnection.java:469) ~[mongodb-driver-core-5.5.1.jar!/:na]
2026-08-06T00:53:34.116379174Z  [chat-service]  at com.mongodb.internal.connection.DefaultServerMonitor$ServerMonitor.lookupServerDescription(DefaultServerMonitor.java:249) ~[mongodb-driver-core-5.5.1.jar!/:na]
2026-08-06T00:53:34.116381936Z  [chat-service]  at com.mongodb.internal.connection.DefaultServerMonitor$ServerMonitor.run(DefaultServerMonitor.java:176) ~[mongodb-driver-core-5.5.1.jar!/:na]
2026-08-06T00:53:35.038136733Z  [chat-service]  [2m2026-08-06T09:53:35.037+09:00[0;39m [32m INFO [traceId=6a73db0e28f9ff3fc4cce81a89cfb39e,spanId=dd1cc7ec2d7c08ad,userId=NONE][0;39m [35m7[0;39m [2m--- [chat-service] [ntainer#5-1-C-1] [6a73db0e28f9ff3fc4cce81a89cfb39e-dd1cc7ec2d7c08ad] [0;39m[36morg.mongodb.driver.cluster              [0;39m [2m:[0;39m Waiting for server to become available for operation with ID 383. Remaining time: 29994 ms. Selector: WritableServerSelector, topology description: {type=UNKNOWN, servers=[{address=172.31.46.124:27017, type=UNKNOWN, state=CONNECTING, exception={com.mongodb.MongoNodeIsRecoveringException: Command failed with error 11600 (InterruptedAtShutdown): 'interrupted at shutdown' on server 172.31.46.124:27017. The full response is {"ok": 0.0, "errmsg": "interrupted at shutdown", "code": 11600, "codeName": "InterruptedAtShutdown"}}}].
2026-08-06T00:53:35.038136733Z  [chat-service]  [2m2026-08-06T09:53:35.037+09:00[0;39m [32m INFO [traceId=6a73db0e28f9ff3fc4cce81a89cfb39e,spanId=dd1cc7ec2d7c08ad,userId=NONE][0;39m [35m7[0;39m [2m--- [chat-service] [ntainer#5-1-C-1] [6a73db0e28f9ff3fc4cce81a89cfb39e-dd1cc7ec2d7c08ad] [0;39m[36morg.mongodb.driver.cluster              [0;39m [2m:[0;39m Waiting for server to become available for operation with ID 383. Remaining time: 29994 ms. Selector: WritableServerSelector, topology description: {type=UNKNOWN, servers=[{address=172.31.46.124:27017, type=UNKNOWN, state=CONNECTING, exception={com.mongodb.MongoNodeIsRecoveringException: Command failed with error 11600 (InterruptedAtShutdown): 'interrupted at shutdown' on server 172.31.46.124:27017. The full response is {"ok": 0.0, "errmsg": "interrupted at shutdown", "code": 11600, "codeName": "InterruptedAtShutdown"}}}].
2026-08-06T00:53:35.118559762Z  [chat-service]  [2m2026-08-06T09:53:35.117+09:00[0;39m [32m INFO [traceId=NONE,spanId=NONE,userId=NONE][0;39m [35m7[0;39m [2m--- [chat-service] [31.46.124:27017] [                                                 ] [0;39m[36morg.mongodb.driver.cluster              [0;39m [2m:[0;39m Exception in monitor thread while connecting to server 172.31.46.124:27017
2026-08-06T00:53:35.118590979Z  [chat-service]  com.mongodb.MongoSocketOpenException: Exception opening socket
2026-08-06T00:53:35.118594596Z  [chat-service]  at com.mongodb.internal.connection.netty.NettyStream$OpenChannelFutureListener.lambda$operationComplete$1(NettyStream.java:534) ~[mongodb-driver-core-5.5.1.jar!/:na]
2026-08-06T00:53:35.118597961Z  [chat-service]  at com.mongodb.internal.Locks.lambda$withLock$0(Locks.java:34) ~[mongodb-driver-core-5.5.1.jar!/:na]
2026-08-06T00:53:35.118601296Z  [chat-service]  at com.mongodb.internal.Locks.checkedWithLock(Locks.java:61) ~[mongodb-driver-core-5.5.1.jar!/:na]
2026-08-06T00:53:35.118604807Z  [chat-service]  at com.mongodb.internal.Locks.withLock(Locks.java:55) ~[mongodb-driver-core-5.5.1.jar!/:na]
2026-08-06T00:53:35.118607441Z  [chat-service]  at com.mongodb.internal.Locks.withLock(Locks.java:33) ~[mongodb-driver-core-5.5.1.jar!/:na]
2026-08-06T00:53:35.118610575Z  [chat-service]  at com.mongodb.internal.connection.netty.NettyStream$OpenChannelFutureListener.operationComplete(NettyStream.java:521) ~[mongodb-driver-core-5.5.1.jar!/:na]
2026-08-06T00:53:35.118613501Z  [chat-service]  at com.mongodb.internal.connection.netty.NettyStream$OpenChannelFutureListener.operationComplete(NettyStream.java:504) ~[mongodb-driver-core-5.5.1.jar!/:na]
2026-08-06T00:53:35.118620515Z  [chat-service]  at io.netty.util.concurrent.DefaultPromise.notifyListener0(DefaultPromise.java:603) ~[netty-common-4.1.122.Final.jar!/:4.1.122.Final]
2026-08-06T00:53:35.118623728Z  [chat-service]  at io.netty.util.concurrent.DefaultPromise.notifyListeners0(DefaultPromise.java:596) ~[netty-common-4.1.122.Final.jar!/:4.1.122.Final]
2026-08-06T00:53:35.118643793Z  [chat-service]  at io.netty.util.concurrent.DefaultPromise.notifyListenersNow(DefaultPromise.java:572) ~[netty-common-4.1.122.Final.jar!/:4.1.122.Final]
2026-08-06T00:53:35.118650052Z  [chat-service]  at io.netty.util.concurrent.DefaultPromise.notifyListeners(DefaultPromise.java:505) ~[netty-common-4.1.122.Final.jar!/:4.1.122.Final]
2026-08-06T00:53:35.118653248Z  [chat-service]  at io.netty.util.concurrent.DefaultPromise.setValue0(DefaultPromise.java:649) ~[netty-common-4.1.122.Final.jar!/:4.1.122.Final]
2026-08-06T00:53:35.118655913Z  [chat-service]  at io.netty.util.concurrent.DefaultPromise.setFailure0(DefaultPromise.java:642) ~[netty-common-4.1.122.Final.jar!/:4.1.122.Final]
2026-08-06T00:53:35.118658225Z  [chat-service]  at io.netty.util.concurrent.DefaultPromise.tryFailure(DefaultPromise.java:131) ~[netty-common-4.1.122.Final.jar!/:4.1.122.Final]
2026-08-06T00:53:35.118661071Z  [chat-service]  at io.netty.channel.nio.AbstractNioChannel$AbstractNioUnsafe.fulfillConnectPromise(AbstractNioChannel.java:326) ~[netty-transport-4.1.122.Final.jar!/:4.1.122.Final]
2026-08-06T00:53:35.118663533Z  [chat-service]  at io.netty.channel.nio.AbstractNioChannel$AbstractNioUnsafe.finishConnect(AbstractNioChannel.java:342) ~[netty-transport-4.1.122.Final.jar!/:4.1.122.Final]
2026-08-06T00:53:35.118665996Z  [chat-service]  at io.netty.channel.nio.NioEventLoop.processSelectedKey(NioEventLoop.java:784) ~[netty-transport-4.1.122.Final.jar!/:4.1.122.Final]
2026-08-06T00:53:35.118668363Z  [chat-service]  at io.netty.channel.nio.NioEventLoop.processSelectedKeysOptimized(NioEventLoop.java:732) ~[netty-transport-4.1.122.Final.jar!/:4.1.122.Final]
2026-08-06T00:53:35.118670802Z  [chat-service]  at io.netty.channel.nio.NioEventLoop.processSelectedKeys(NioEventLoop.java:658) ~[netty-transport-4.1.122.Final.jar!/:4.1.122.Final]
2026-08-06T00:53:35.118673485Z  [chat-service]  at io.netty.channel.nio.NioEventLoop.run(NioEventLoop.java:562) ~[netty-transport-4.1.122.Final.jar!/:4.1.122.Final]
2026-08-06T00:53:35.118694956Z  [chat-service]  at io.netty.util.concurrent.SingleThreadEventExecutor$4.run(SingleThreadEventExecutor.java:998) ~[netty-common-4.1.122.Final.jar!/:4.1.122.Final]
2026-08-06T00:53:35.118697898Z  [chat-service]  at io.netty.util.internal.ThreadExecutorMap$2.run(ThreadExecutorMap.java:74) ~[netty-common-4.1.122.Final.jar!/:4.1.122.Final]
2026-08-06T00:53:35.118700637Z  [chat-service]  at io.netty.util.concurrent.FastThreadLocalRunnable.run(FastThreadLocalRunnable.java:30) ~[netty-common-4.1.122.Final.jar!/:4.1.122.Final]
2026-08-06T00:53:35.118706207Z  [chat-service]  Caused by: io.netty.channel.AbstractChannel$AnnotatedConnectException: Connection refused: /172.31.46.124:27017
2026-08-06T00:53:35.118708856Z  [chat-service]  Caused by: java.net.ConnectException: Connection refused
2026-08-06T00:53:42.251529860Z  [chat-service]  [2m2026-08-06T09:53:42.251+09:00[0;39m [32m INFO [traceId=6a73db166e5322a7df383f8412a96c4d,spanId=735e828dd0ed8557,userId=NONE][0;39m [35m7[0;39m [2m--- [chat-service] [nio-8090-exec-2] [6a73db166e5322a7df383f8412a96c4d-735e828dd0ed8557] [0;39m[36morg.mongodb.driver.cluster              [0;39m [2m:[0;39m Waiting for server to become available for operation with ID 401. Remaining time: 29999 ms. Selector: ReadPreferenceServerSelector{readPreference=primary}, topology description: {type=UNKNOWN, servers=[{address=172.31.46.124:27017, type=UNKNOWN, state=CONNECTING, exception={com.mongodb.MongoNodeIsRecoveringException: Command failed with error 11600 (InterruptedAtShutdown): 'interrupted at shutdown' on server 172.31.46.124:27017. The full response is {"ok": 0.0, "errmsg": "interrupted at shutdown", "code": 11600, "codeName": "InterruptedAtShutdown"}}}].
2026-08-06T00:53:42.251529860Z  [chat-service]  [2m2026-08-06T09:53:42.251+09:00[0;39m [32m INFO [traceId=6a73db166e5322a7df383f8412a96c4d,spanId=735e828dd0ed8557,userId=NONE][0;39m [35m7[0;39m [2m--- [chat-service] [nio-8090-exec-2] [6a73db166e5322a7df383f8412a96c4d-735e828dd0ed8557] [0;39m[36morg.mongodb.driver.cluster              [0;39m [2m:[0;39m Waiting for server to become available for operation with ID 401. Remaining time: 29999 ms. Selector: ReadPreferenceServerSelector{readPreference=primary}, topology description: {type=UNKNOWN, servers=[{address=172.31.46.124:27017, type=UNKNOWN, state=CONNECTING, exception={com.mongodb.MongoNodeIsRecoveringException: Command failed with error 11600 (InterruptedAtShutdown): 'interrupted at shutdown' on server 172.31.46.124:27017. The full response is {"ok": 0.0, "errmsg": "interrupted at shutdown", "code": 11600, "codeName": "InterruptedAtShutdown"}}}].
2026-08-06T00:53:42.256550022Z  [chat-service]  [2m2026-08-06T09:53:42.255+09:00[0;39m [32m INFO [traceId=NONE,spanId=NONE,userId=NONE][0;39m [35m7[0;39m [2m--- [chat-service] [31.46.124:27017] [                                                 ] [0;39m[36morg.mongodb.driver.cluster              [0;39m [2m:[0;39m Exception in monitor thread while connecting to server 172.31.46.124:27017
2026-08-06T00:53:42.256594806Z  [chat-service]  com.mongodb.MongoSocketOpenException: Exception opening socket
2026-08-06T00:53:42.256694209Z  [chat-service]  Caused by: io.netty.channel.AbstractChannel$AnnotatedConnectException: Connection refused: /172.31.46.124:27017
2026-08-06T00:53:42.256697133Z  [chat-service]  Caused by: java.net.ConnectException: Connection refused
2026-08-06T00:53:52.291757163Z  [chat-service]  [2m2026-08-06T09:53:52.291+09:00[0;39m [32m INFO [traceId=6a73db20d7504a562163749f221b664b,spanId=a9067f727dfe0126,userId=NONE][0;39m [35m7[0;39m [2m--- [chat-service] [nio-8090-exec-5] [6a73db20d7504a562163749f221b664b-a9067f727dfe0126] [0;39m[36morg.mongodb.driver.cluster              [0;39m [2m:[0;39m Waiting for server to become available for operation with ID 444. Remaining time: 29999 ms. Selector: ReadPreferenceServerSelector{readPreference=primary}, topology description: {type=UNKNOWN, servers=[{address=172.31.46.124:27017, type=UNKNOWN, state=CONNECTING, exception={com.mongodb.MongoSocketOpenException: Exception opening socket}, caused by {io.netty.channel.AbstractChannel$AnnotatedConnectException: Connection refused: /172.31.46.124:27017}, caused by {java.net.ConnectException: Connection refused}}].
2026-08-06T00:53:52.291757163Z  [chat-service]  [2m2026-08-06T09:53:52.291+09:00[0;39m [32m INFO [traceId=6a73db20d7504a562163749f221b664b,spanId=a9067f727dfe0126,userId=NONE][0;39m [35m7[0;39m [2m--- [chat-service] [nio-8090-exec-5] [6a73db20d7504a562163749f221b664b-a9067f727dfe0126] [0;39m[36morg.mongodb.driver.cluster              [0;39m [2m:[0;39m Waiting for server to become available for operation with ID 444. Remaining time: 29999 ms. Selector: ReadPreferenceServerSelector{readPreference=primary}, topology description: {type=UNKNOWN, servers=[{address=172.31.46.124:27017, type=UNKNOWN, state=CONNECTING, exception={com.mongodb.MongoSocketOpenException: Exception opening socket}, caused by {io.netty.channel.AbstractChannel$AnnotatedConnectException: Connection refused: /172.31.46.124:27017}, caused by {java.net.ConnectException: Connection refused}}].
2026-08-06T00:53:58.024916087Z  [chat-service]  [2m2026-08-06T09:53:58.024+09:00[0;39m [33m WARN [traceId=6a73db166e5322a7df383f8412a96c4d,spanId=735e828dd0ed8557,userId=NONE][0;39m [35m7[0;39m [2m--- [chat-service] [nio-8090-exec-2] [6a73db166e5322a7df383f8412a96c4d-735e828dd0ed8557] [0;39m[36mo.s.b.a.health.HealthEndpointSupport    [0;39m [2m:[0;39m Health contributor org.springframework.boot.actuate.autoconfigure.health.HealthEndpointConfiguration$AdaptedReactiveHealthContributors$1 (mongo) took 15774ms to respond
2026-08-06T00:53:58.024916087Z  [chat-service]  [2m2026-08-06T09:53:58.024+09:00[0;39m [33m WARN [traceId=6a73db166e5322a7df383f8412a96c4d,spanId=735e828dd0ed8557,userId=NONE][0;39m [35m7[0;39m [2m--- [chat-service] [nio-8090-exec-2] [6a73db166e5322a7df383f8412a96c4d-735e828dd0ed8557] [0;39m[36mo.s.b.a.health.HealthEndpointSupport    [0;39m [2m:[0;39m Health contributor org.springframework.boot.actuate.autoconfigure.health.HealthEndpointConfiguration$AdaptedReactiveHealthContributors$1 (mongo) took 15774ms to respond
```

### 메트릭 시계열

| 쿼리 | series | 점 | min | max | last | 값이 0이던 구간 |
|---|---|---:|---:|---:|---:|---|
| `hikaricp_connections_active` | `{__name__=hikaricp_connections_active, application=auth-service, cluster=yogurtte-k3s-prod, container=auth-service, instance=10.42.1.51:8090, job=auth-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=auth-service-5999bb9f5c-lzp4p, pool=HikariPool-1, service=auth-service}` | 90 | 0 | 0 | 0 | **2026-08-06T00:37:41Z ~ 2026-08-06T00:59:56Z** |
| `hikaricp_connections_active` | `{__name__=hikaricp_connections_active, application=chat-service, cluster=yogurtte-k3s-prod, container=chat-service, instance=10.42.1.54:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-fdcc7c776-dnbs9, pool=HikariPool-1}` | 11 | 0 | 0 | 0 | **2026-08-06T00:37:41Z ~ 2026-08-06T00:40:11Z** |
| `hikaricp_connections_active` | `{__name__=hikaricp_connections_active, application=chat-service, cluster=yogurtte-k3s-prod, container=chat-service, instance=10.42.1.55:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-fdcc7c776-kh2dl, pool=HikariPool-1}` | 44 | 0 | 0 | 0 | **2026-08-06T00:49:11Z ~ 2026-08-06T00:59:56Z** |
| `hikaricp_connections_active` | `{__name__=hikaricp_connections_active, application=content-service, cluster=yogurtte-k3s-prod, container=content-service, instance=10.42.1.53:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-85f648fcff-sp24n, pool=HikariPool-1}` | 90 | 0 | 0 | 0 | **2026-08-06T00:37:41Z ~ 2026-08-06T00:59:56Z** |
| `hikaricp_connections_active` | `{__name__=hikaricp_connections_active, application=content-service, cluster=yogurtte-k3s-prod, container=content-service, instance=10.42.3.46:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-85f648fcff-v2pw9, pool=HikariPool-1}` | 90 | 0 | 0 | 0 | **2026-08-06T00:37:41Z ~ 2026-08-06T00:59:56Z** |
| `hikaricp_connections_pending` | `{__name__=hikaricp_connections_pending, application=auth-service, cluster=yogurtte-k3s-prod, container=auth-service, instance=10.42.1.51:8090, job=auth-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=auth-service-5999bb9f5c-lzp4p, pool=HikariPool-1, service=auth-service}` | 90 | 0 | 0 | 0 | **2026-08-06T00:37:41Z ~ 2026-08-06T00:59:56Z** |
| `hikaricp_connections_pending` | `{__name__=hikaricp_connections_pending, application=chat-service, cluster=yogurtte-k3s-prod, container=chat-service, instance=10.42.1.54:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-fdcc7c776-dnbs9, pool=HikariPool-1}` | 11 | 0 | 0 | 0 | **2026-08-06T00:37:41Z ~ 2026-08-06T00:40:11Z** |
| `hikaricp_connections_pending` | `{__name__=hikaricp_connections_pending, application=chat-service, cluster=yogurtte-k3s-prod, container=chat-service, instance=10.42.1.55:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-fdcc7c776-kh2dl, pool=HikariPool-1}` | 44 | 0 | 0 | 0 | **2026-08-06T00:49:11Z ~ 2026-08-06T00:59:56Z** |
| `hikaricp_connections_pending` | `{__name__=hikaricp_connections_pending, application=content-service, cluster=yogurtte-k3s-prod, container=content-service, instance=10.42.1.53:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-85f648fcff-sp24n, pool=HikariPool-1}` | 90 | 0 | 0 | 0 | **2026-08-06T00:37:41Z ~ 2026-08-06T00:59:56Z** |
| `hikaricp_connections_pending` | `{__name__=hikaricp_connections_pending, application=content-service, cluster=yogurtte-k3s-prod, container=content-service, instance=10.42.3.46:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-85f648fcff-v2pw9, pool=HikariPool-1}` | 90 | 0 | 0 | 0 | **2026-08-06T00:37:41Z ~ 2026-08-06T00:59:56Z** |
| `rate(jvm_gc_pause_seconds_sum[5m])` | `{action=end of major GC, application=chat-service, cause=Allocation Failure, cluster=yogurtte-k3s-prod, container=chat-service, gc=MarkSweepCompact, instance=10.42.1.54:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-fdcc7c776-dnbs9}` | 23 | 0 | 0 | 0 | **2026-08-06T00:37:41Z ~ 2026-08-06T00:43:11Z** |
| `rate(jvm_gc_pause_seconds_sum[5m])` | `{action=end of minor GC, application=auth-service, cause=G1 Evacuation Pause, cluster=yogurtte-k3s-prod, container=auth-service, gc=G1 Young Generation, instance=10.42.1.51:8090, job=auth-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=auth-service-5999bb9f5c-lzp4p, service=auth-service}` | 90 | 0 | 0.000 | 0.000 | **2026-08-06T00:37:41Z ~ 2026-08-06T00:56:26Z** |
| `rate(jvm_gc_pause_seconds_sum[5m])` | `{action=end of minor GC, application=auth-service, cause=Metadata GC Threshold, cluster=yogurtte-k3s-prod, container=auth-service, gc=G1 Young Generation, instance=10.42.1.51:8090, job=auth-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=auth-service-5999bb9f5c-lzp4p, service=auth-service}` | 90 | 0 | 0 | 0 | **2026-08-06T00:37:41Z ~ 2026-08-06T00:59:56Z** |
| `rate(jvm_gc_pause_seconds_sum[5m])` | `{action=end of minor GC, application=chat-service, cause=Allocation Failure, cluster=yogurtte-k3s-prod, container=chat-service, gc=Copy, instance=10.42.1.54:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-fdcc7c776-dnbs9}` | 23 | 0.000 | 0.000 | 0.000 | — |
| `rate(jvm_gc_pause_seconds_sum[5m])` | `{action=end of minor GC, application=chat-service, cause=Allocation Failure, cluster=yogurtte-k3s-prod, container=chat-service, gc=Copy, instance=10.42.1.55:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-fdcc7c776-kh2dl}` | 40 | 0.000 | 0.001 | 0.000 | — |
| `rate(jvm_gc_pause_seconds_sum[5m])` | `{action=end of minor GC, application=content-service, cause=Allocation Failure, cluster=yogurtte-k3s-prod, container=content-service, gc=Copy, instance=10.42.1.53:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-85f648fcff-sp24n}` | 90 | 0 | 0.000 | 0 | **2026-08-06T00:37:41Z ~ 2026-08-06T00:38:26Z, 2026-08-06T00:42:41Z ~ 2026-08-06T00:54:26Z, 2026-08-06T00:58:41Z ~ 2026-08-06T00:59:56Z** |
| `rate(jvm_gc_pause_seconds_sum[5m])` | `{action=end of minor GC, application=content-service, cause=Allocation Failure, cluster=yogurtte-k3s-prod, container=content-service, gc=Copy, instance=10.42.3.46:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-85f648fcff-v2pw9}` | 90 | 0 | 0.000 | 0 | **2026-08-06T00:37:41Z ~ 2026-08-06T00:47:41Z, 2026-08-06T00:51:56Z ~ 2026-08-06T00:59:56Z** |
| `up` | `{__name__=up, app=node-exporter, cluster=yogurtte-k3s-prod, component=metrics, container=node-exporter, instance=ip-172-31-40-241, job=integrations/node_exporter, k8s_cluster_name=yogurtte-k3s-prod, namespace=monitoring, pod=grafana-k8s-monitoring-node-exporter-cph5l, source=kubernetes, workload=DaemonSet/grafana-k8s-monitoring-node-exporter}` | 90 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, app=node-exporter, cluster=yogurtte-k3s-prod, component=metrics, container=node-exporter, instance=ip-172-31-45-39, job=integrations/node_exporter, k8s_cluster_name=yogurtte-k3s-prod, namespace=monitoring, pod=grafana-k8s-monitoring-node-exporter-9kvqt, source=kubernetes, workload=DaemonSet/grafana-k8s-monitoring-node-exporter}` | 90 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, cluster=yogurtte-k3s-prod, container=auth-service, instance=10.42.1.51:8090, job=auth-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=auth-service-5999bb9f5c-lzp4p}` | 90 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, cluster=yogurtte-k3s-prod, container=chat-service, instance=10.42.1.54:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-fdcc7c776-dnbs9}` | 11 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, cluster=yogurtte-k3s-prod, container=chat-service, instance=10.42.1.55:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-fdcc7c776-kh2dl}` | 44 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, cluster=yogurtte-k3s-prod, container=content-service, instance=10.42.1.53:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-85f648fcff-sp24n}` | 90 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, cluster=yogurtte-k3s-prod, container=content-service, instance=10.42.3.46:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-85f648fcff-v2pw9}` | 90 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, cluster=yogurtte-k3s-prod, instance=10.42.1.248:8080, job=integrations/kubernetes/kube-state-metrics, k8s_cluster_name=yogurtte-k3s-prod, source=kubernetes}` | 90 | 1 | 1 | 1 | — |
| `mongodb_up` | `{__name__=mongodb_up, cluster=yogurtte-k3s-prod, instance=infra-server, job=mongodb, k8s_cluster_name=yogurtte-k3s-prod}` | 90 | 1 | 1 | 1 | — |
| `kafka_brokers` | `{__name__=kafka_brokers, cluster=yogurtte-k3s-prod, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod}` | 90 | 1 | 1 | 1 | — |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=chat-service-fcm-tokens, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=0, topic=user.fcm-tokens}` | 90 | 0 | 0 | 0 | **2026-08-06T00:37:41Z ~ 2026-08-06T00:59:56Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=chat-service-fcm-tokens, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=1, topic=user.fcm-tokens}` | 90 | 0 | 0 | 0 | **2026-08-06T00:37:41Z ~ 2026-08-06T00:59:56Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=chat-service-fcm-tokens, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=2, topic=user.fcm-tokens}` | 90 | 0 | 0 | 0 | **2026-08-06T00:37:41Z ~ 2026-08-06T00:59:56Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=chat-service-notification-settings, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=0, topic=user.notification-settings}` | 90 | 0 | 0 | 0 | **2026-08-06T00:37:41Z ~ 2026-08-06T00:59:56Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=chat-service-notification-settings, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=1, topic=user.notification-settings}` | 90 | 0 | 0 | 0 | **2026-08-06T00:37:41Z ~ 2026-08-06T00:59:56Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=chat-service-notification-settings, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=2, topic=user.notification-settings}` | 90 | 0 | 0 | 0 | **2026-08-06T00:37:41Z ~ 2026-08-06T00:59:56Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=db-writer, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=0, topic=chat.messages}` | 90 | 0 | 0 | 0 | **2026-08-06T00:37:41Z ~ 2026-08-06T00:59:56Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=db-writer, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=1, topic=chat.messages}` | 90 | 0 | 0 | 0 | **2026-08-06T00:37:41Z ~ 2026-08-06T00:59:56Z** |
| `websocket_active_users` | `{__name__=websocket_active_users, application=chat-service, cluster=yogurtte-k3s-prod, container=chat-service, instance=10.42.1.54:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-fdcc7c776-dnbs9}` | 11 | 0 | 0 | 0 | **2026-08-06T00:37:41Z ~ 2026-08-06T00:40:11Z** |
| `websocket_active_users` | `{__name__=websocket_active_users, application=chat-service, cluster=yogurtte-k3s-prod, container=chat-service, instance=10.42.1.55:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-fdcc7c776-kh2dl}` | 44 | 0 | 0 | 0 | **2026-08-06T00:49:11Z ~ 2026-08-06T00:59:56Z** |

값이 0이던 구간은 굵게 표시했다 — 프로세스가 사라져 시계열이 꺾인 것이 유일한 신호인 장애가 있다.

