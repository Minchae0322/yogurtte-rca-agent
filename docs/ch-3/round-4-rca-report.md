# RCA Report — `scan-1785767730`

| 항목 | 값 |
|---|---|
| 모드 | rca |
| 질문 | 최근 1시간 안에 앱이 잠깐 버벅였다는 얘기가 있어요. 뭔가 있었는지 봐줘 |
| 시각 | 2026-08-04T05:52:49.523451900Z |
| provider | claude-cli |
| model | `claude-opus-5` · turns 1 |
| prompt | `.\prompts\system-prompt.md` |
| tokens | in 130457 (cacheRead 23,449 · cacheCreate 107,006) / out 13090 · cost $1.4090 |
| elapsed | total 221271ms (tempo 4324 · loki 11154 · mimir 1530 · assemble 50 · llm 196194) |

## 탐색 (Triage)

| 항목 | 값 |
|---|---|
| 시간창 해석 | 명시적 from/to |
| 시간창 확신도 | EXACT |
| 스윕 창 | 2026-08-03T14:35:30Z ~ 2026-08-03T15:35:30Z |
| 좁힌 창 | 2026-08-03T14:35:30Z ~ 2026-08-03T15:35:30Z |
| 대상 | chat-service, content-service |
| traceId | 6a70a6659bde7e3f0243b1162434c65e |
| 트레이스 후보 | 10건 |
| 장애 후보 | 8건 · 선택 INC-1, INC-2, INC-3, INC-4, INC-5, INC-7, INC-8 |
| 계획 파싱 | 성공 |
| 스윕 컨텍스트 | 후보 + 원본 (A) |
| prompt | `.\prompts\triage-prompt.md` |
| tokens | in 56855 / out 3578 · cost $0.4624 |
| chars | 컨텍스트 48,506 + 프롬프트 1,399 = **49,905** |
| elapsed | survey 1964ms · llm 66679ms |

**선정 이유**: 질문이 시각을 특정하지 않았고 동일 지문(content 댓글 POST 지연 + chat security filterchain 지연 + chat ERROR/WARN)이 14:35대와 15:33대 두 번 나타나므로 두 클러스터를 함께 잡되, chat-service up 시계열이 10분간 끊긴 1차 구간을 중심으로 본다.

**근거**

- up{job=chat-service}: pod chat-service-fdcc7c776-qrbc2가 1785767730(14:35:30) 단일 샘플 뒤 소멸, 후속 pod xf4sv는 1785768330(14:45:30)부터 시작 — 14:35:30~14:45:30 사이 chat-service를 보고하는 시계열이 하나도 없다(파드 교체/재기동 정황)
- Tempo 에러 검색 '{status=error}' 0건인데 지연 검색은 10건 — 실패가 아니라 지연만 발생, 사용자 체감 '버벅임'과 일치
- content-service http post /battles/{battleId}/items/{itemId}/comments 542,294ms (traceId 6a70a6659bde7e3f0243b1162434c65e, 14:32:05 시작) 및 38,887ms — 내부 span 3개가 각 20~25초, 이 트레이스의 serviceStats에 chat-service span 14개가 함께 잡힘(content→chat 하류 대기 의심)
- chat-service security filterchain before 20,334 / 17,971 / 14,000 / 7,704 / 4,027ms — 14:40:37~14:42:02 사이 5건 연속, 인증 진입 지점에서 초 단위 정체
- kafka_consumergroup_lag{consumergroup=notification-processors,partition=3,topic=user.notifications} 14:35:30 20 → 14:40:30 25 → 14:45:30 26 → 14:50:30 0 — 파드 부재 구간에 적체 후 복귀 시 일괄 소화
- Loki chat-service ERROR/WARN: 1785768000(14:40)에 1건, 1785768300(14:45)에 1건, 1785771300(15:35)에 2건 — 두 지연 구간에만 로그가 뜨고 나머지 50분은 0건
- 2차 재발: content-service 댓글 POST 23,838ms(15:33:42, traceId 6a70b4d6fec0e31f0e1471884d9ab0a9) + chat-service filterchain 21,175/11,057ms(15:33:44~15:34:05) — 1차와 동일한 지문이 20초 단위로 재현
- up/mongodb_up/kafka_brokers는 전 구간 1 유지 — 인프라 다운이 아니라 애플리케이션 레벨 스톨
- websocket_active_users는 두 파드 모두 전 구간 0 — 채팅 실사용자 없는 상태에서도 filterchain이 20초 걸린 것이라 부하가 원인일 가능성은 낮다

**스윕이 찾은 트레이스** (고른 것은 6a70a6659bde7e3f0243b1162434c65e)

| traceId | 채널 | root service | root span | ms |
|---|---|---|---|---:|
| `6a70b4e2511983007eb087a80330f9ba` | slow | chat-service | security filterchain before | 11057 |
| `6a70b4d8ad6302c8a55b085b698bac32` | slow | chat-service | security filterchain before | 21175 |
| `6a70b4d6fec0e31f0e1471884d9ab0a9` | slow | content-service | http post /battles/{battleId}/items/{itemId}/comments | 23838 |
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
- 같은 시각의 다른 후보: INC-6, INC-7, INC-8  (인과 여부는 판단하지 않았다)

## INC-6  kafka  |  kafka_consumergroup_lag
- 구간: 2026-08-03T15:30:30Z ~ 2026-08-03T15:35:30Z  (MIMIR · 집계 해상도만큼 흐림)
- kafka_consumergroup_lag{consumergroup=notification-processors, partition=3, topic=user.notifications} 0 → 1
- 같은 시각의 다른 후보: INC-5, INC-7, INC-8  (인과 여부는 판단하지 않았다)

## INC-7  content-service  |  http post /battles/{battleId}/items/{itemId}/comments
- 구간: 2026-08-03T15:33:42.795238Z ~ 2026-08-03T15:34:06.633238Z  (TEMPO · 시각 정확)
- content-service http post /battles/{battleId}/items/{itemId}/comments 23,838ms (slow 채널)
- traceId: 6a70b4d6fec0e31f0e1471884d9ab0a9
- 같은 시각의 다른 후보: INC-5, INC-6, INC-8  (인과 여부는 판단하지 않았다)

## INC-8  chat-service  |  security filterchain before
- 구간: 2026-08-03T15:33:44.240590Z ~ 2026-08-03T15:34:05.415590Z  (TEMPO · 시각 정확)
- chat-service security filterchain before 21,175ms (slow 채널)
- chat-service security filterchain before 11,057ms (slow 채널)
- traceId: 6a70b4d8ad6302c8a55b085b698bac32, 6a70b4e2511983007eb087a80330f9ba
- 같은 시각의 다른 후보: INC-5, INC-6, INC-7  (인과 여부는 판단하지 않았다)

**기각한 후보**

- INC-6 — kafka_consumergroup_lag가 0→1로 1건 증가한 것뿐이라 정상 처리 지연 수준의 잡음이고, 같은 시각대 조사 창은 INC-5/7/8 선택으로 이미 덮인다.

**보정 기록**

- 창을 후보 [INC-1, INC-2, INC-3, INC-4, INC-5, INC-7, INC-8] 의 신호 시각에서 계산했다 (2026-08-03T14:35:30Z ~ 2026-08-03T15:35:30Z)

**스윕 무신호/실패**

- Tempo 에러 검색 '{ status = error }'이 이 창에서 0건이다. 트레이스가 생성되지 않는 장애(컨슈머 전멸·파드 부재)이거나 에러가 아닌 형태의 장애(200 성공 + 지연)일 수 있으니 이 사실 자체를 근거로 쓸 것.

## 수집 범위 (Coverage)

- **window**: 2026-08-03T14:35:30Z ~ 2026-08-03T15:35:30Z (3600s)
- **trace**: 103,321B / 115 spans (상위 span만)
- **창 안 후보 트레이스**: 10건 / 103,321B
- **logs**: errwarn=25,903B · traceId=35,157B
- **metrics**: 8 수집 / 370,034B, 누락 [sum(rate(http_server_requests_seconds_count{application="content-service", status="401"}[1m]))]
- **context**: 188,051 chars (+ 시스템 프롬프트 853 chars)

### 토큰 축 (개선 지표)

| 단계 | 총 in | chars | 컨텍스트 토큰 |
|---|---:|---:|---:|
| 탐색 | 56,855 | 49,905 | 27,429 |
| 분석 | 130,457 | 188,904 | 101,031 |
| **합계** | **187,312** | | **128,460** |

- **overheadTokens 29,426 tok — 이 회차에 실측**(1자 프롬프트 프로브, 같은 명령·모델·샌드박스).
  호출마다 한 번씩 얹히므로 단계별로 뺀다. **다른 날 상수를 쓰지 않았다 = `█ 실측`.**
- contextTokens (count_tokens API): 측정 안 됨 — 구독 CLI 경로엔 API 키가 없다
- 비용은 **API 환산 추정치**다. 구독 계정으로 호출하므로 실제 청구액이 아니다.

## 수집 실패/누락

- Loki ERROR/WARN log query failed (2026-08-03T14:35:30Z~2026-08-03T14:55:30Z): ResourceAccessException: I/O error on GET request for "https://logs-prod-030.grafana.net/loki/api/v1/query_range": Read timed out
- Metric 'sum(rate(http_server_requests_seconds_count{application="content-service", status="401"}[1m]))' returned no series in this window; skipped.

---

관측 데이터만으로 정리했다. 로그 타임스탬프는 KST(+09:00), 아래는 전부 **UTC** 기준으로 환산했다.

먼저 재구성한 타임라인:

| 시각(UTC) | 관측 |
|---|---|
| 14:32:05 | content-service가 댓글 저장 후 `publish user.notifications` (trace `6a70a665…`) |
| 14:35:30 | 창 시작 시점에 이미 `notification-processors` partition=3 lag **20** |
| 14:35:45 | chat-service pod `…-qrbc2` 메트릭 마지막 관측 |
| 14:40:43 | chat-service pod `…-xf4sv`가 offset 1053 `receive` 시작 (발행 대비 **8분 38초** 지연) |
| 14:41:15 | `…-xf4sv`의 `up`/hikari/GC 시리즈 시작 |
| 14:41:30 | partition=3 lag 최대 **26** |
| 14:41:38 | `MongoNodeIsRecoveringException … InterruptedAtShutdown` (에피소드 A) |
| 14:41:54 | `Connection refused: /172.31.46.124:27017` |
| 14:42:01 | `Health contributor … (mongo) took 17332ms` |
| 14:42:30 | partition=3 lag 0 복귀 |
| 15:33:42 | `InterruptedAtShutdown` → `Connection refused` (에피소드 B) |
| 15:33:45~15:34:30 | partition=3 lag 다시 0이 아님 |
| 15:34:05.5 | mongo `insert user_notifications` 성공 = 복구 |
| 15:34:05 | `(mongo) took 21085ms` / `11045ms` |
| 15:34:06.6 | `[kafka] 알림 처리 완료` |

---

## 1. 원인 후보 랭킹 (최대 3개)

1. **MongoDB(172.31.46.124:27017) 인스턴스의 일시 중단 — 창 안에서 2회(14:41:38 전후, 15:33:42 전후)**
2. **chat-service pod 교체(`…-qrbc2` → `…-xf4sv`)로 인한 `notification-processors` 컨슈머 공백 — 알림 최대 8분 38초 지연**
3. **chat-service 알림 처리 트랜잭션이 외부 I/O(Mongo·FCM)를 감싸고 있어 위 두 사건을 증폭 — MySQL 커넥션 25.7초 점유, 파티션 처리 직렬 정지** (근본 원인이 아닌 증폭 요인)

---

## 2. 후보별 근거

### 후보 1 — MongoDB 일시 중단 (2회)

**근거**
- 로그 원문(에피소드 B, 15:33:42):
  `com.mongodb.MongoNodeIsRecoveringException: Command failed with error 11600 (InterruptedAtShutdown): 'interrupted at shutdown' on server 172.31.46.124:27017`
  이어서 `Caused by: io.netty.channel.AbstractChannel$AnnotatedConnectException: Connection refused: /172.31.46.124:27017`.
  `InterruptedAtShutdown` → `Connection refused` 순서는 mongod 프로세스가 종료 절차에 들어간 뒤 리스너가 사라진 상태와 일치한다.
- 에피소드 A(14:41:38, traceId `6a70a895…`): 동일한 `InterruptedAtShutdown` 문구, 14:41:54(traceId `6a70a8b2…`)에는 `Connection refused`.
- 체감 지연: `Health contributor … (mongo) took 17332ms` (14:42:01), `21085ms` / `11045ms` (15:34:05).
- span 근거: trace `6a70a895…`의 `receive`(kafka) 25,750.6ms, 그 아래 `user-notification-service#process-notification`이 14:41:38.298에 시작했는데 첫 `insert toychat`은 14:42:00.84 → 약 **22.5초** 아무 자식 span 없이 대기. trace `6a70b4d6…`도 동일 패턴: `receive` 23,772ms, process 시작 15:33:42.865 → 첫 `insert toychat` 15:34:05.52 = 약 **22.6초** 대기.

**확신도**: 높음 (클라이언트 관측 기준). 단, "mongod 프로세스 재시작"이라는 원인 규정까지는 **중간**.

**반증 데이터**
- `mongodb_up{instance=infra-server}` 241점 **전 구간 1**, `up{job=mongodb}`도 전 구간 1. 15초 간격 스크레이프인데 20초 이상의 중단이 한 점도 잡히지 않은 것은 드라이버 로그와 배치된다. → 익스포터가 별도 경로/캐시로 보고했거나, 중단이 특정 인터페이스에만 국한됐을 가능성. 이 불일치 때문에 "인프라 서버 전체의 mongod 다운"으로 단정하지 않는다.
- content-service는 Mongo를 호출하지 않아(호출 그래프상 엣지 없음) 영향 없음 — 같은 시각 `http post /battles/…/comments`는 `status=200, outcome=SUCCESS`, 50.5ms.

**대기 구간 만료 판정 / 최종 상태**
- Mongo 서버 선택 대기: 상한 **30,000ms** (로그 `Remaining time: 29999 ms` / `29927 ms`). 실측 대기 **약 22.5s(A)**, **약 22.6s(B)** → **만료되지 않음**.
- 최종 상태: **성공**. `[notify] 알림 처리 완료: userId=7, … id=6a70a8a2aa665b94bab20801` / `id=6a70b4d6aa665b94bab20805`, FCM `멀티캐스트 결과: tokens=1, success=1, failure=0`, `[kafka] 알림 처리 완료`. 재시도·폐기 없음(`user.notifications.dlq` lag 전 구간 0).
- Redis 대기: 상한 **2s** (`server.address = redis://172.31.46.124?timeout=2s`), 실측 최대 **63.2ms** → 만료 없음, 성공.

---

### 후보 2 — chat-service pod 교체로 인한 컨슈머 공백

**근거**
- 메트릭 시리즈 경계: `chat-service-fdcc7c776-qrbc2`의 `jvm_gc_pause` 시리즈는 **2점(14:35:30~14:35:45)**뿐이고, `chat-service-fdcc7c776-xf4sv`의 `up`·`hikaricp_connections_active`·`websocket_active_users`는 모두 **14:41:15부터 218점**. 같은 ReplicaSet(`fdcc7c776`)의 다른 pod로 교체된 형태.
- `kafka_consumergroup_lag{consumergroup=notification-processors, partition=3}`: 창 시작 시 **20**, 14:41:30에 최대 **26**, 14:42:30에 0 복귀. 나머지 파티션(0,1,2,4,5)은 전 구간 0.
- span 근거: trace `6a70a665…`의 producer span은 **14:32:05.562**, 대응 consumer `receive` span 시작은 **14:40:43.080** → **518초** 공백. 이 공백은 브로커가 아니라 컨슈머 부재로만 설명된다(`kafka_brokers=1`, `up{job=kafka}=1` 전 구간).
- 콜드스타트 흔적: 재개 직후 첫 메시지에서 span 사이 공백이 크고(process 시작 14:40:46.98 → 첫 insert 14:40:49.58), FCM 왕복이 **8.8초**(`발송 준비` 23:40:58.814 → `멀티캐스트 결과` 23:41:07.613). 같은 호출이 이후 트레이스에서는 1.3초·0.7초.

**확신도**: 중간. 교체가 있었다는 것은 시리즈 경계·lag 곡선·span 공백 3개 채널이 일치하지만, **교체 사유(배포/OOMKill/축출/노드 이동)는 데이터에 없다.**

**반증 데이터**
- OOM 근거는 없음: chat-service `end of major GC (MarkSweepCompact)` rate 전 구간 **0**, minor GC도 최대 0.0019 s/s.
- 노드 장애도 아님: `up{job=integrations/node_exporter}` 두 노드 모두 전 구간 1, kubelet/cadvisor도 전 구간 1.
- pod 재시작 카운터(`kube_pod_container_status_restarts_total`)를 수집하지 않아 교체 자체를 직접 확증하는 시리즈는 없음.

**대기 구간 만료 판정 / 최종 상태**
- Kafka 재처리 대기(518초): 상한(`max.poll.interval.ms` 등 컨슈머 설정)을 **수집하지 못해 만료 여부 판정 불가**. 다만 이는 컨슈머 부재 구간이라 폴 타임아웃과는 무관하다.
- 최종 상태: **성공(지연 후 전량 처리)**. offset 1053·1078·1082 모두 `[kafka] 알림 처리 완료` 로그 존재, lag 0 복귀, DLQ·retry 토픽(`chat.messages-retry-*`, `user.notifications.dlq`) lag 전 구간 0 → **유실·폐기 없음**.

---

### 후보 3 — 알림 처리 트랜잭션이 외부 I/O를 감싸고 있음 (증폭 요인)

**근거**
- 호출 그래프: `chat-service --jdbc--> mysql/content (HikariPool-1) 17회 최대 25748.6ms`, events `acquired, commit`.
- span 근거: trace `6a70a895…`의 `connection` span은 `acquired` 14:41:38.296 → `commit` 14:42:04.042, 즉 **25.7초 동안 MySQL 커넥션 점유**. 그 안에 Mongo 대기 22.5초, FCM 호출, Redis `KEYS`가 전부 들어 있다. trace `6a70b4d6…`도 `acquired` 15:33:42.864 → `commit` 15:34:06.63 = **23.8초**.
- 컨슈머 스레드는 `ntainer#5-1-C-1` 단일. 파티션 3의 메시지 1건이 25초를 잡으면 그 뒤 메시지는 전부 뒤로 밀린다 — 15:33:45~15:34:30에 lag가 다시 0이 아니게 된 것과 정합.
- Redis 사용 패턴도 같은 경로에 있다: `db.operation=KEYS`, span 이름 `KEYS 0` (`chat-service --db--> redis 6회 최대 63.2ms`). KEYS는 O(N) 블로킹 명령이다.

**확신도**: 중간 (사실 관측은 확실하나, 이것 자체가 장애를 "일으켰다"는 근거는 없고 피해를 키우는 구조라는 판단).

**반증 데이터**
- 실제로 커넥션 고갈은 일어나지 않았다: `hikaricp_connections_pending` 4개 인스턴스 **전 구간 0**, `hikaricp_connections_active`는 chat-service 최대 **1**, content-service·auth-service 전 구간 0. 즉 현재 트래픽에서는 잠재 리스크에 그쳤다.
- Redis도 실제 지연 없음: 최대 63.2ms(상한 2s 대비 3%).

---

### 배제된 후보

| 후보 | 배제 사유 |
|---|---|
| JVM GC (STW) | chat-service major GC rate 전 구간 0, minor GC 최대 1.9ms/s. content·auth는 최대 8.7e-5. 20초대 정지를 설명 못 함 |
| HikariCP 커넥션 풀 고갈 | `hikaricp_connections_pending` 전 인스턴스 전 구간 0, active 최대 1 |
| Kafka 브로커 장애 | `kafka_brokers=1`·`up{job=kafka}=1` 전 구간, partition=3 외 모든 파티션·컨슈머그룹 lag 0 |
| Redis 장애 | `up{job=redis}=1` 전 구간, 호출 최대 63.2ms ≪ timeout 2s |
| content-service / auth-service 결함 | content-service 서버 span 3건 모두 `status=200, outcome=SUCCESS`, 44.4ms·56.4ms·50.5ms. 두 pod `up=1` 전 구간. auth-service는 호출 그래프에 엣지가 없어 이번 경로에 관여하지 않음 |
| 노드 장애 | node-exporter·kubelet·cadvisor·kube-state-metrics `up` 전 구간 1 |
| 인증(401) 급증 | **판정 불가** — 해당 메트릭이 no series로 수집 실패. 배제가 아니라 미확인 |

---

## 3. 권장 다음 조치

### 이미 발생한 피해 — 복구 가능한가
**복구 조치 불필요(이미 자체 복구됨).**
- 알림 3건(offset 1053/1078/1082) 모두 최종 처리 완료 로그와 `FCM success=1`이 있고, `notification-processors` lag는 0으로, `user.notifications.dlq` lag는 전 구간 0. **유실·폐기 0건**.
- 남은 피해는 되돌릴 수 없는 종류다: **푸시 알림이 최대 8분 38초 늦게 도착**(14:32:05 발행 → 14:41:07 전송 완료). 이미 전송됐으므로 재발송하면 중복이 된다.
- **사용자 체감 경로에 대한 데이터는 부족하다.** `websocket_active_users`가 전 구간 0이고 로그도 `사용자 오프라인 상태로 WebSocket 전송 스킵: userId=7`이라, 창 안에서 확인되는 사용자 영향은 "푸시 지연" 하나뿐이다. chat-service의 HTTP 서버 span은 수집분에 없고(관측된 `secured request` 21.2초짜리들은 WARN 로그상 actuator health 경로), `http_server_requests` 메트릭도 없다.

### 재발 방지
1. **Mongo 중단의 원인 규명이 먼저다.** 14:41과 15:33 두 번, 각각 20초대. 계획된 재시작인지 아닌지가 나머지 판단을 바꾼다. mongod 자체 로그와 infra-server의 systemd/OOM 기록 확인.
2. **`mongodb_up`이 1을 유지한 것**은 모니터링 결함일 가능성이 크다. 드라이버가 `Connection refused`를 받는 동안 익스포터가 1을 보고했다면 알람이 절대 울리지 않는다. 익스포터 스크레이프 경로 점검.
3. **트랜잭션 경계를 외부 I/O 밖으로 뺀다.** MySQL 커넥션을 25.7초 잡은 채 Mongo 22.5초 + FCM 8.8초를 기다리는 구조는, 지금은 동시성이 낮아 무사했을 뿐이다(pending 0). MongoDB `serverSelectionTimeout` 30초도 단일 컨슈머 스레드 기준으로는 과도하다.
4. **Redis `KEYS`를 `SCAN`으로 교체.** 현재는 63ms지만 키가 늘면 그대로 블로킹 지연이 된다.
5. **컨슈머 이중화 / 파티션 3 편중 완화.** 알림이 전부 partition=3으로만 가고(로그의 `partition=3, offset=…` 3건 모두), 소비 스레드는 `…#5-1-C-1` 하나다. pod 하나가 빠지면 그 파티션은 그대로 멈춘다.

### 복구 확인
- 이미 정상 복귀 신호가 다 나와 있다: `notification-processors` partition=3 lag 15:34:30 이후 0, `up{chat-service}` 218점 전 구간 1, 마지막 트레이스 `6a70b4d6…`의 mongo `insert` 88.9ms / `find` 2.4~85.8ms 정상 범위, FCM `success=1`.
- 다만 **재확인이 필요한 지표**: `Health contributor (mongo)` 응답 시간이 현재 정상(수백 ms 이하)인지, Mongo 드라이버 예외가 15:34:06 이후 0인지.

### 추가 수집이 필요한 것 (데이터 부족)
- **Loki 14:35:30~15:55:30 ERROR/WARN 재조회** — 이 구간 쿼리가 `Read timed out`으로 실패해 에피소드 A와 pod 교체 전후 로그가 통째로 비어 있다. 후보 2의 확신도를 중간에 묶어둔 주된 이유다.
- `kube_pod_container_status_restarts_total`, `kube_pod_status_phase` — pod 교체 여부·사유 확증용.
- mongod 자체 로그 / infra-server dmesg·systemd journal — 두 차례 shutdown의 원인.
- chat-service `http_server_requests_seconds_*` 및 content-service의 401 메트릭 — 사용자 체감 실패가 실제로 있었는지 확인용(현재 401 쿼리는 no series로 스킵됨).
- chat-service의 `spring.kafka` 컨슈머 설정(`max.poll.interval.ms`) — 25.75초 처리가 상한 대비 어디쯤인지 판정하려면 필요하다. 현재는 **판정 불가**.

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

### 로그 원문 (60 / 전체 150줄)

전체가 상한을 넘어 ERROR/WARN 줄을 우선 발췌했다. 나머지는 원본 파일에 있다.

```
2026-08-03T14:41:38.389229615Z  [chat-service]  [2m2026-08-03T23:41:38.388+09:00[0;39m [32m INFO [traceId=6a70a895a7050d844888a6d1474d3a60,spanId=d7feb099e1a9883c,userId=NONE][0;39m [35m7[0;39m [2m--- [chat-service] [ntainer#5-1-C-1] [6a70a895a7050d844888a6d1474d3a60-d7feb099e1a9883c] [0;39m[36morg.mongodb.driver.cluster              [0;39m [2m:[0;39m Waiting for server to become available for operation with ID 235. Remaining time: 29927 ms. Selector: WritableServerSelector, topology description: {type=UNKNOWN, servers=[{address=172.31.46.124:27017, type=UNKNOWN, state=CONNECTING, exception={com.mongodb.MongoNodeIsRecoveringException: Command failed with error 11600 (InterruptedAtShutdown): 'interrupted at shutdown' on server 172.31.46.124:27017. The full response is {"ok": 0.0, "errmsg": "interrupted at shutdown", "code": 11600, "codeName": "InterruptedAtShutdown"}}}].
2026-08-03T14:41:44.316763019Z  [chat-service]  [2m2026-08-03T23:41:44.315+09:00[0;39m [32m INFO [traceId=6a70a8a8dd737de20646b20c1fa17b5d,spanId=8db391db0b0cd5ca,userId=NONE][0;39m [35m7[0;39m [2m--- [chat-service] [nio-8090-exec-2] [6a70a8a8dd737de20646b20c1fa17b5d-8db391db0b0cd5ca] [0;39m[36morg.mongodb.driver.cluster              [0;39m [2m:[0;39m Waiting for server to become available for operation with ID 249. Remaining time: 29999 ms. Selector: ReadPreferenceServerSelector{readPreference=primary}, topology description: {type=UNKNOWN, servers=[{address=172.31.46.124:27017, type=UNKNOWN, state=CONNECTING, exception={com.mongodb.MongoNodeIsRecoveringException: Command failed with error 11600 (InterruptedAtShutdown): 'interrupted at shutdown' on server 172.31.46.124:27017. The full response is {"ok": 0.0, "errmsg": "interrupted at shutdown", "code": 11600, "codeName": "InterruptedAtShutdown"}}}].
2026-08-03T14:41:54.328161012Z  [chat-service]  [2m2026-08-03T23:41:54.327+09:00[0;39m [32m INFO [traceId=6a70a8b27dc2c42abb940c8bba63f107,spanId=367619fc456dff08,userId=NONE][0;39m [35m7[0;39m [2m--- [chat-service] [nio-8090-exec-3] [6a70a8b27dc2c42abb940c8bba63f107-367619fc456dff08] [0;39m[36morg.mongodb.driver.cluster              [0;39m [2m:[0;39m Waiting for server to become available for operation with ID 292. Remaining time: 29999 ms. Selector: ReadPreferenceServerSelector{readPreference=primary}, topology description: {type=UNKNOWN, servers=[{address=172.31.46.124:27017, type=UNKNOWN, state=CONNECTING, exception={com.mongodb.MongoSocketOpenException: Exception opening socket}, caused by {io.netty.channel.AbstractChannel$AnnotatedConnectException: Connection refused: /172.31.46.124:27017}, caused by {java.net.ConnectException: Connection refused}}].
2026-08-03T14:42:01.717335888Z  [chat-service]  [2m2026-08-03T23:42:01.717+09:00[0;39m [33m WARN [traceId=6a70a8a8dd737de20646b20c1fa17b5d,spanId=8db391db0b0cd5ca,userId=NONE][0;39m [35m7[0;39m [2m--- [chat-service] [nio-8090-exec-2] [6a70a8a8dd737de20646b20c1fa17b5d-8db391db0b0cd5ca] [0;39m[36mo.s.b.a.health.HealthEndpointSupport    [0;39m [2m:[0;39m Health contributor org.springframework.boot.actuate.autoconfigure.health.HealthEndpointConfiguration$AdaptedReactiveHealthContributors$1 (mongo) took 17332ms to respond
2026-08-03T15:33:42.126946122Z  [chat-service]  [2m2026-08-04T00:33:42.126+09:00[0;39m [32m INFO [traceId=NONE,spanId=NONE,userId=NONE][0;39m [35m7[0;39m [2m--- [chat-service] [31.46.124:27017] [                                                 ] [0;39m[36morg.mongodb.driver.cluster              [0;39m [2m:[0;39m Exception in monitor thread while connecting to server 172.31.46.124:27017
2026-08-03T15:33:42.126993998Z  [chat-service]  com.mongodb.MongoNodeIsRecoveringException: Command failed with error 11600 (InterruptedAtShutdown): 'interrupted at shutdown' on server 172.31.46.124:27017. The full response is {"ok": 0.0, "errmsg": "interrupted at shutdown", "code": 11600, "codeName": "InterruptedAtShutdown"}
2026-08-03T15:33:42.126996902Z  [chat-service]  at com.mongodb.internal.connection.ProtocolHelper.createSpecialException(ProtocolHelper.java:264) ~[mongodb-driver-core-5.5.1.jar!/:na]
2026-08-03T15:33:42.126999599Z  [chat-service]  at com.mongodb.internal.connection.ProtocolHelper.getCommandFailureException(ProtocolHelper.java:206) ~[mongodb-driver-core-5.5.1.jar!/:na]
2026-08-03T15:33:42.127002675Z  [chat-service]  at com.mongodb.internal.connection.InternalStreamConnection.receiveCommandMessageResponse(InternalStreamConnection.java:520) ~[mongodb-driver-core-5.5.1.jar!/:na]
2026-08-03T15:33:42.127005016Z  [chat-service]  at com.mongodb.internal.connection.InternalStreamConnection.receive(InternalStreamConnection.java:469) ~[mongodb-driver-core-5.5.1.jar!/:na]
2026-08-03T15:33:42.127007581Z  [chat-service]  at com.mongodb.internal.connection.DefaultServerMonitor$ServerMonitor.lookupServerDescription(DefaultServerMonitor.java:249) ~[mongodb-driver-core-5.5.1.jar!/:na]
2026-08-03T15:33:42.127009894Z  [chat-service]  at com.mongodb.internal.connection.DefaultServerMonitor$ServerMonitor.run(DefaultServerMonitor.java:176) ~[mongodb-driver-core-5.5.1.jar!/:na]
2026-08-03T15:33:42.127317336Z  [chat-service]  [2m2026-08-04T00:33:42.126+09:00[0;39m [32m INFO [traceId=NONE,spanId=NONE,userId=NONE][0;39m [35m7[0;39m [2m--- [chat-service] [31.46.124:27017] [                                                 ] [0;39m[36morg.mongodb.driver.cluster              [0;39m [2m:[0;39m Exception in monitor thread while connecting to server 172.31.46.124:27017
2026-08-03T15:33:42.127327469Z  [chat-service]  com.mongodb.MongoNodeIsRecoveringException: Command failed with error 11600 (InterruptedAtShutdown): 'interrupted at shutdown' on server 172.31.46.124:27017. The full response is {"ok": 0.0, "errmsg": "interrupted at shutdown", "code": 11600, "codeName": "InterruptedAtShutdown"}
2026-08-03T15:33:42.127330680Z  [chat-service]  at com.mongodb.internal.connection.ProtocolHelper.createSpecialException(ProtocolHelper.java:264) ~[mongodb-driver-core-5.5.1.jar!/:na]
2026-08-03T15:33:42.127333393Z  [chat-service]  at com.mongodb.internal.connection.ProtocolHelper.getCommandFailureException(ProtocolHelper.java:206) ~[mongodb-driver-core-5.5.1.jar!/:na]
2026-08-03T15:33:42.127336255Z  [chat-service]  at com.mongodb.internal.connection.InternalStreamConnection.receiveCommandMessageResponse(InternalStreamConnection.java:520) ~[mongodb-driver-core-5.5.1.jar!/:na]
2026-08-03T15:33:42.127338410Z  [chat-service]  at com.mongodb.internal.connection.InternalStreamConnection.receive(InternalStreamConnection.java:469) ~[mongodb-driver-core-5.5.1.jar!/:na]
2026-08-03T15:33:42.127340730Z  [chat-service]  at com.mongodb.internal.connection.DefaultServerMonitor$ServerMonitor.lookupServerDescription(DefaultServerMonitor.java:249) ~[mongodb-driver-core-5.5.1.jar!/:na]
2026-08-03T15:33:42.127343165Z  [chat-service]  at com.mongodb.internal.connection.DefaultServerMonitor$ServerMonitor.run(DefaultServerMonitor.java:176) ~[mongodb-driver-core-5.5.1.jar!/:na]
2026-08-03T15:33:42.867465874Z  [chat-service]  [2m2026-08-04T00:33:42.866+09:00[0;39m [32m INFO [traceId=6a70b4d6fec0e31f0e1471884d9ab0a9,spanId=6061456a9ac0d52d,userId=NONE][0;39m [35m7[0;39m [2m--- [chat-service] [ntainer#5-1-C-1] [6a70b4d6fec0e31f0e1471884d9ab0a9-6061456a9ac0d52d] [0;39m[36morg.mongodb.driver.cluster              [0;39m [2m:[0;39m Waiting for server to become available for operation with ID 1899. Remaining time: 29999 ms. Selector: WritableServerSelector, topology description: {type=UNKNOWN, servers=[{address=172.31.46.124:27017, type=UNKNOWN, state=CONNECTING, exception={com.mongodb.MongoNodeIsRecoveringException: Command failed with error 11600 (InterruptedAtShutdown): 'interrupted at shutdown' on server 172.31.46.124:27017. The full response is {"ok": 0.0, "errmsg": "interrupted at shutdown", "code": 11600, "codeName": "InterruptedAtShutdown"}}}].
2026-08-03T15:33:42.867465874Z  [chat-service]  [2m2026-08-04T00:33:42.866+09:00[0;39m [32m INFO [traceId=6a70b4d6fec0e31f0e1471884d9ab0a9,spanId=6061456a9ac0d52d,userId=NONE][0;39m [35m7[0;39m [2m--- [chat-service] [ntainer#5-1-C-1] [6a70b4d6fec0e31f0e1471884d9ab0a9-6061456a9ac0d52d] [0;39m[36morg.mongodb.driver.cluster              [0;39m [2m:[0;39m Waiting for server to become available for operation with ID 1899. Remaining time: 29999 ms. Selector: WritableServerSelector, topology description: {type=UNKNOWN, servers=[{address=172.31.46.124:27017, type=UNKNOWN, state=CONNECTING, exception={com.mongodb.MongoNodeIsRecoveringException: Command failed with error 11600 (InterruptedAtShutdown): 'interrupted at shutdown' on server 172.31.46.124:27017. The full response is {"ok": 0.0, "errmsg": "interrupted at shutdown", "code": 11600, "codeName": "InterruptedAtShutdown"}}}].
2026-08-03T15:33:42.869323611Z  [chat-service]  [2m2026-08-04T00:33:42.868+09:00[0;39m [32m INFO [traceId=NONE,spanId=NONE,userId=NONE][0;39m [35m7[0;39m [2m--- [chat-service] [31.46.124:27017] [                                                 ] [0;39m[36morg.mongodb.driver.cluster              [0;39m [2m:[0;39m Exception in monitor thread while connecting to server 172.31.46.124:27017
2026-08-03T15:33:42.869523420Z  [chat-service]  com.mongodb.MongoSocketOpenException: Exception opening socket
2026-08-03T15:33:42.869527244Z  [chat-service]  at com.mongodb.internal.connection.netty.NettyStream$OpenChannelFutureListener.lambda$operationComplete$1(NettyStream.java:534) ~[mongodb-driver-core-5.5.1.jar!/:na]
2026-08-03T15:33:42.869530694Z  [chat-service]  at com.mongodb.internal.Locks.lambda$withLock$0(Locks.java:34) ~[mongodb-driver-core-5.5.1.jar!/:na]
2026-08-03T15:33:42.869533591Z  [chat-service]  at com.mongodb.internal.Locks.checkedWithLock(Locks.java:61) ~[mongodb-driver-core-5.5.1.jar!/:na]
2026-08-03T15:33:42.869536489Z  [chat-service]  at com.mongodb.internal.Locks.withLock(Locks.java:55) ~[mongodb-driver-core-5.5.1.jar!/:na]
2026-08-03T15:33:42.869539238Z  [chat-service]  at com.mongodb.internal.Locks.withLock(Locks.java:33) ~[mongodb-driver-core-5.5.1.jar!/:na]
2026-08-03T15:33:42.869542510Z  [chat-service]  at com.mongodb.internal.connection.netty.NettyStream$OpenChannelFutureListener.operationComplete(NettyStream.java:521) ~[mongodb-driver-core-5.5.1.jar!/:na]
2026-08-03T15:33:42.869545349Z  [chat-service]  at com.mongodb.internal.connection.netty.NettyStream$OpenChannelFutureListener.operationComplete(NettyStream.java:504) ~[mongodb-driver-core-5.5.1.jar!/:na]
2026-08-03T15:33:42.869548377Z  [chat-service]  at io.netty.util.concurrent.DefaultPromise.notifyListener0(DefaultPromise.java:603) ~[netty-common-4.1.122.Final.jar!/:4.1.122.Final]
2026-08-03T15:33:42.869551321Z  [chat-service]  at io.netty.util.concurrent.DefaultPromise.notifyListeners0(DefaultPromise.java:596) ~[netty-common-4.1.122.Final.jar!/:4.1.122.Final]
2026-08-03T15:33:42.869554129Z  [chat-service]  at io.netty.util.concurrent.DefaultPromise.notifyListenersNow(DefaultPromise.java:572) ~[netty-common-4.1.122.Final.jar!/:4.1.122.Final]
2026-08-03T15:33:42.869557035Z  [chat-service]  at io.netty.util.concurrent.DefaultPromise.notifyListeners(DefaultPromise.java:505) ~[netty-common-4.1.122.Final.jar!/:4.1.122.Final]
2026-08-03T15:33:42.869560045Z  [chat-service]  at io.netty.util.concurrent.DefaultPromise.setValue0(DefaultPromise.java:649) ~[netty-common-4.1.122.Final.jar!/:4.1.122.Final]
2026-08-03T15:33:42.869562606Z  [chat-service]  at io.netty.util.concurrent.DefaultPromise.setFailure0(DefaultPromise.java:642) ~[netty-common-4.1.122.Final.jar!/:4.1.122.Final]
2026-08-03T15:33:42.869565388Z  [chat-service]  at io.netty.util.concurrent.DefaultPromise.tryFailure(DefaultPromise.java:131) ~[netty-common-4.1.122.Final.jar!/:4.1.122.Final]
2026-08-03T15:33:42.869568272Z  [chat-service]  at io.netty.channel.nio.AbstractNioChannel$AbstractNioUnsafe.fulfillConnectPromise(AbstractNioChannel.java:326) ~[netty-transport-4.1.122.Final.jar!/:4.1.122.Final]
2026-08-03T15:33:42.869571077Z  [chat-service]  at io.netty.channel.nio.AbstractNioChannel$AbstractNioUnsafe.finishConnect(AbstractNioChannel.java:342) ~[netty-transport-4.1.122.Final.jar!/:4.1.122.Final]
2026-08-03T15:33:42.869609323Z  [chat-service]  at io.netty.channel.nio.NioEventLoop.processSelectedKey(NioEventLoop.java:784) ~[netty-transport-4.1.122.Final.jar!/:4.1.122.Final]
2026-08-03T15:33:42.869612215Z  [chat-service]  at io.netty.channel.nio.NioEventLoop.processSelectedKeysOptimized(NioEventLoop.java:732) ~[netty-transport-4.1.122.Final.jar!/:4.1.122.Final]
2026-08-03T15:33:42.869615283Z  [chat-service]  at io.netty.channel.nio.NioEventLoop.processSelectedKeys(NioEventLoop.java:658) ~[netty-transport-4.1.122.Final.jar!/:4.1.122.Final]
2026-08-03T15:33:42.869617967Z  [chat-service]  at io.netty.channel.nio.NioEventLoop.run(NioEventLoop.java:562) ~[netty-transport-4.1.122.Final.jar!/:4.1.122.Final]
2026-08-03T15:33:42.869620655Z  [chat-service]  at io.netty.util.concurrent.SingleThreadEventExecutor$4.run(SingleThreadEventExecutor.java:998) ~[netty-common-4.1.122.Final.jar!/:4.1.122.Final]
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

