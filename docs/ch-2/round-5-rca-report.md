# RCA Report — `scan-1785976340`

| 항목 | 값 |
|---|---|
| 모드 | rca |
| 질문 | 최근 1시간 안에 채팅 알림이 안 온다는 문의가 여러 건 들어왔다. 원인을 조사해줘 |
| 시각 | 2026-08-06T00:53:01.095488600Z |
| provider | claude-cli |
| model | `claude-opus-5` · turns 1 |
| prompt | `.\prompts\system-prompt.md` |
| tokens | in 76101 (cacheRead 23,449 · cacheCreate 52,650) / out 11100 · cost $0.8157 |
| elapsed | total 181975ms (tempo 1351 · loki 500 · mimir 1158 · assemble 141 · llm 169647) |

## 탐색 (Triage)

| 항목 | 값 |
|---|---|
| 시간창 해석 | 상대 표현 '최근 1시간' |
| 시간창 확신도 | EXACT |
| 스윕 창 | 2026-08-05T23:52:20.579292Z ~ 2026-08-06T00:52:20.579292Z |
| 좁힌 창 | 2026-08-06T00:32:20Z ~ 2026-08-06T00:52:20.579292Z |
| 대상 | chat-service |
| traceId | 6a73d9b04dcd773a41a3a8263fde1b79 |
| 트레이스 후보 | 4건 |
| 장애 후보 | 4건 · 선택 INC-1, INC-2, INC-3 |
| 계획 파싱 | 성공 |
| 스윕 컨텍스트 | **후보만 — 원본 제외 (B)** |
| prompt | `.\prompts\triage-prompt.md` |
| tokens | in 32375 / out 1939 · cost $0.1766 |
| chars | 컨텍스트 2,335 + 프롬프트 1,399 = **3,734** |
| elapsed | survey 2122ms · llm 38296ms |

**선정 이유**: 알림 전달 경로(user.notifications 컨슈머 lag 적체)와 그 시각 chat-service의 무에러 지연·경고 로그가 같은 10분 창에 겹쳐, 알림 미도착 증상의 상류·하류 지문으로 함께 볼 필요가 있다.

**근거**

- kafka_consumergroup_lag{consumergroup=notification-processors, topic=user.notifications, partition=3} 0→14→25 (2026-08-06T00:37:20Z~00:47:20Z) — 알림 메시지가 소비되지 않고 적재만 되는 상태로, '알림 미도착' 증상과 경로가 직결된다
- chat-service security filterchain before 25,386ms / 17,613ms / 6,510ms (00:47:44Z~00:48:10Z, traceId 6a73d9b04dcd..., 6a73d9b830a5..., 6a73d9c39aaf...) — 요청 진입 직후 필터체인에서 수십 초 정체
- chat-service ERROR/WARN 3건 (00:45:00Z~00:50:00Z) — 지연 트레이스 및 lag 증가 구간과 시각이 겹친다
- Tempo 에러 검색 '{ status = error }' 0건 — 예외로 실패하는 장애가 아니라 200 성공 + 지연/정체 형태임을 뒷받침한다
- min_over_time(up[5m]) 이상 0건, kafka_brokers 이상 0건, mongodb_up 이상 0건 — 파드 다운·브로커 소실·DB 단절이 아니라 처리 정체 쪽으로 범위가 좁혀진다
- max_over_time(websocket_active_users[5m]) 이상 0건 — 사용자 연결 자체는 유지된 채 알림만 도달하지 않는 그림과 일치

**스윕이 찾은 트레이스** (고른 것은 6a73d9b04dcd773a41a3a8263fde1b79)

| traceId | 채널 | root service | root span | ms |
|---|---|---|---|---:|
| `6a73d9c39aaf7193897c86174a60f421` | slow | chat-service | security filterchain before | 6510 |
| `6a73d7cd82e6e12dd5c7a7ca51858e6e` | slow | <root span not yet received> | (없음) | 20504 |
| `6a73d9b830a513e3385f8985bbc2c359` | slow | chat-service | security filterchain before | 17613 |
| `6a73d9b04dcd773a41a3a8263fde1b79` ←선택 | slow | chat-service | security filterchain before | 25386 |

**장애 후보** (코드가 신호를 묶은 것 · 창은 여기서 계산됨)

## INC-1  kafka  |  kafka_consumergroup_lag
- 구간: 2026-08-06T00:37:20Z ~ 2026-08-06T00:47:20Z  (MIMIR · 집계 해상도만큼 흐림)
- kafka_consumergroup_lag{consumergroup=notification-processors, partition=3, topic=user.notifications} 0 → 14
- kafka_consumergroup_lag{consumergroup=notification-processors, partition=3, topic=user.notifications} 14 → 25
- 같은 시각의 다른 후보: INC-2  (인과 여부는 판단하지 않았다)

## INC-2  chat-service  |  ERROR/WARN
- 구간: 2026-08-06T00:45:00Z ~ 2026-08-06T00:50:00Z  (LOKI · 집계 해상도만큼 흐림)
- ERROR/WARN 3건 (2026-08-06T00:45:00Z ~ 2026-08-06T00:50:00Z)
- 같은 시각의 다른 후보: INC-1, INC-3, INC-4  (인과 여부는 판단하지 않았다)

## INC-3  chat-service  |  security filterchain before
- 구간: 2026-08-06T00:47:44.632550Z ~ 2026-08-06T00:48:10.018900Z  (TEMPO · 시각 정확)
- chat-service security filterchain before 25,386ms (slow 채널)
- chat-service security filterchain before 17,613ms (slow 채널)
- chat-service security filterchain before 6,510ms (slow 채널)
- traceId: 6a73d9b04dcd773a41a3a8263fde1b79, 6a73d9b830a513e3385f8985bbc2c359, 6a73d9c39aaf7193897c86174a60f421
- 같은 시각의 다른 후보: INC-2, INC-4  (인과 여부는 판단하지 않았다)

## INC-4  <root span not yet received>
- 구간: 2026-08-06T00:47:55.722081Z ~ 2026-08-06T00:48:16.226081Z  (TEMPO · 시각 정확)
- <root span not yet received>  20,504ms (slow 채널)
- traceId: 6a73d7cd82e6e12dd5c7a7ca51858e6e
- 같은 시각의 다른 후보: INC-2, INC-3  (인과 여부는 판단하지 않았다)

**기각한 후보**

- INC-4 — root span 미수신 상태의 서비스 미귀속 지연 트레이스 1건(20,504ms)으로 INC-3와 시각이 거의 겹쳐 같은 정체의 파편일 가능성이 크지만, 독립 근거가 없고 INC-3 선택으로 조사 창에 이미 포함되므로 따로 고르지 않았다

**보정 기록**

- 창을 후보 [INC-1, INC-2, INC-3] 의 신호 시각에서 계산했다 (2026-08-06T00:32:20Z ~ 2026-08-06T00:52:20.579292Z)

**스윕 무신호/실패**

- Tempo 에러 검색 '{ status = error }'이 이 창에서 0건이다. 트레이스가 생성되지 않는 장애(컨슈머 전멸·파드 부재)이거나 에러가 아닌 형태의 장애(200 성공 + 지연)일 수 있으니 이 사실 자체를 근거로 쓸 것.

## 수집 범위 (Coverage)

- **window**: 2026-08-06T00:32:20Z ~ 2026-08-06T00:52:20.579292Z (1200s)
- **trace**: 54,013B / 52 spans
- **창 안 후보 트레이스**: 10건 / 54,013B
- **logs**: errwarn=6,070B · traceId=12,317B
- **metrics**: 8 수집 / 138,545B, 누락 [sum(rate(http_server_requests_seconds_count{application="content-service", status="401"}[1m]))]
- **context**: 83,077 chars (+ 시스템 프롬프트 805 chars)

### 토큰 축 (개선 지표)

| 단계 | 총 in | chars | 컨텍스트 토큰 |
|---|---:|---:|---:|
| 탐색 | 32,375 | 3,734 | 2,949 |
| 분석 | 76,101 | 83,882 | 46,675 |
| **합계** | **108,476** | | **49,624** |

- **overheadTokens 29,426 tok — 이 회차에 실측**(1자 프롬프트 프로브, 같은 명령·모델·샌드박스).
  호출마다 한 번씩 얹히므로 단계별로 뺀다. **다른 날 상수를 쓰지 않았다 = `█ 실측`.**
- contextTokens (count_tokens API): 측정 안 됨 — 구독 CLI 경로엔 API 키가 없다
- 비용은 **API 환산 추정치**다. 구독 계정으로 호출하므로 실제 청구액이 아니다.

## 수집 실패/누락

- Metric 'sum(rate(http_server_requests_seconds_count{application="content-service", status="401"}[1m]))' returned no series in this window; skipped.

---

## 1. 원인 후보 랭킹 (최대 3개)

1. **chat-service 파드 교체(재시작)로 `user.notifications` 컨슈머가 약 8분간 정지 → 알림 백로그 및 지연 전달**
2. **재시작으로 WebSocket 세션이 전부 끊겨 실시간(인앱) 알림 경로가 무력화, FCM 푸시만 남음**
3. **재기동 직후 chat-service의 알림 처리 자체가 비정상적으로 느림(1건 17.2초) → 백로그 소화 지연 가중**

---

## 2. 후보별 근거

### 후보 1 — chat-service 파드 교체로 컨슈머 정지 (8분 백로그)

**근거**
- 파드 세대 교체가 메트릭에 그대로 찍힘: `up{pod=chat-service-fdcc7c776-dnbs9, instance=10.42.1.54}` 는 **00:32:20 ~ 00:40:05** 까지만 존재(32점), 신규 `up{pod=chat-service-fdcc7c776-kh2dl, instance=10.42.1.55}` 는 **00:49:20 ~ 00:52:20** 만 존재(13점). `hikaricp_connections_active/pending` 도 동일한 시간 경계로 끊기고 시작함. 그 사이 auth/content-service의 `up` 은 전 구간 1 → chat-service 단독 사건.
- 신규 파드 기동 시각은 로그로 특정됨: `2026-08-06T09:47:09.623+09:00 WARN [traceId=NONE] 7 [ main] JpaBaseConfiguration$JpaWebConfiguration : spring.jpa.open-in-view is enabled by default...` — `[main]` 스레드 부팅 로그, pod=`chat-service-fdcc7c776-kh2dl` = **00:47:09Z 기동**.
- 정지 구간에 정확히 대응하는 Kafka 랙: `kafka_consumergroup_lag{consumergroup=notification-processors, topic=user.notifications, partition=3}` 이 **00:39:50 이후 상승 → 00:41:20=8, 00:43:20=20, 00:45:35=25, 00:47:50=25 → 00:49:05 이후 0**. 같은 컨슈머그룹의 나머지 파티션(0,1,2,4,5)은 전 구간 0.
- 지연이 트레이스에 실측됨(traceId `6a73d7cd82e6e12dd5c7a7ca51858e6e`): content-service `publish user.notifications` 시작 **00:39:41.153Z**(durNs 13.5ms, POST `/battles/22/items/125/comments` 는 `"status":"200"`, `"outcome":"SUCCESS"`, 77.7ms로 정상) → chat-service `receive` (kind=CONSUMER, `messaging.kafka.message.offset=1130`, partition=3) 시작 **00:47:55.722Z**. **큐 대기 494.6초 = 8분 14.6초.**
- 인프라는 무고함: `kafka_brokers`=1, `up{job=kafka|redis|mongodb|node-infra}`, `mongodb_up` 모두 전 구간 1. content-service 파드 2개 모두 전 구간 up.

**확신도**: 높음

**대기·지연 구간 판정**
| 대기 구간 | 실측 | 상한(설정값) | 만료 여부 | 최종 상태 |
|---|---|---|---|---|
| Kafka 브로커 내 메시지 대기 (offset 1130) | 494.6초 | 토픽 retention·`max.poll.interval.ms` **미수집** | **판정 불가**(단, 소비에 성공했으므로 폐기는 아님) | **성공(지연 후 처리 완료)** — `[kafka] 알림 처리 완료: userId=7, type=BATTLE_ITEM_COMMENT` (00:48:16.222Z), 랙 0 복귀, `user.notifications.dlq` 랙 전 구간 0 → **폐기·재시도 없음** |
| `receive` span 소비 처리 | 20.505초 | `max.poll.interval.ms` 미수집 | 판정 불가 | 성공 (리밸런스·재소비 로그 없음, 오프셋 중복 처리 흔적 없음) |

**반증 데이터**
- 알림이 **결국 전달됐다**: `[push] 멀티캐스트 결과: tokens=1, success=1, failure=0`, `[push] OK index=0, ... messageId=projects/toy-chat-30d47/messages/1785977295669348`. 즉 "안 온다"의 실체는 **영구 미전달이 아니라 8분+ 지연**(후보 2가 이 간극을 설명).
- 재시작 **원인** 자체를 뒷받침할 데이터는 없음 — OOMKilled/Evicted 여부, kube event, 컨테이너 메모리·CPU, 재시작 직전 chat-service 로그가 수집되지 않음. **재시작이 일어났다는 사실은 확정, 왜 일어났는지는 데이터 부족.**

---

### 후보 2 — WebSocket 실시간 경로 무력화, FCM만 남음

**근거**
- `websocket_active_users` 가 **구 파드(dnbs9) 32점, 신 파드(kh2dl) 13점 모두 전 구간 0**. 장애 창 내내 WebSocket으로 받을 수 있는 사용자가 0명.
- 처리 로그가 이를 직접 확인: `[notify] 사용자 오프라인 상태로 WebSocket 전송 스킵: userId=7` (00:48:08.524Z) → 직후 `[notify] WebSocket 알림 전송 완료: userId=7` (실제 전송 없이 완료 처리), `[push] 시작: userId=7, onlineDevices=[] (제외 대상)`.
- 파드 교체가 원인 계열로 연결됨: 파드가 죽으면 그 파드에 붙어 있던 WS 세션은 전부 끊기고, 신규 파드 기동(00:47:09) 후에도 `websocket_active_users` 는 00:52:20까지 0으로 회복되지 않음.
- 결과적으로 사용자 체감 경로는 FCM 푸시 단 하나로 축소됨.

**확신도**: 중간 (관측된 알림 이벤트가 트레이스 1건뿐이라 "여러 건 문의"와의 1:1 대응은 미확인)

**대기·지연 구간 판정**
- WebSocket 전송: 대기 없이 **스킵**. `user-notification-web-socket-sender#send-notificat` span 1.104초 소요했으나 내부는 Redis `KEYS 0`(89.3ms) 조회뿐이고, 로그상 결론은 오프라인 스킵. **최종 상태: 전송 안 함(스킵) — 실패 아님, 설계상 폴백.** 타임아웃 상한 대조 대상 없음.
- FCM 발송: `[push] 발송 준비` 00:48:10.222Z → `멀티캐스트 결과` 00:48:16.121Z = **5.899초**. FCM 클라이언트 타임아웃 설정값 **미수집 → 만료 여부 판정 불가**. **최종 상태: 성공(success=1, failure=0).**

**반증 데이터**
- `websocket_active_users=0` 은 장애 이전 구간(00:32:20~)에도 0이었음 → WS 사용자 0은 이번 장애로 **새로 생긴 상태가 아닐 수 있음**(원래 접속자가 없던 시간대일 가능성). 이 후보를 "장애의 결과"로 단정하는 근거는 약함.
- `fcm_tokens.find` 결과 `activeRows=1`, 토큰 유효(`success=1`) → 토큰 만료/설정 차단 계열 원인은 배제됨.

---

### 후보 3 — 재기동 직후 chat-service 처리 지연 (백로그 소화 지연)

**근거**
- 알림 **1건 처리에 17.2초**: `user-notification-service#process-notification` durNs 17.212초 (00:47:58.918Z~). 내부 breakdown — `insert toychat`(user_notifications) 98.1ms, `find`(user_sync_status) 96.8ms / 101.3ms, `find`(user_notification_settings) 91.7ms / 95.4ms, `find`(fcm_tokens) 110.9ms, Redis `KEYS 0` 89.3ms / 96.6ms, `push-dispatcher#dispatch` 6.708초. 개별 I/O 합은 1초 미만인데 총 17.2초 → **span 사이 공백(스케줄링/스레드 대기)이 대부분**.
- 같은 시간대 액추에이터 헬스가 극단적으로 느림: `WARN ... HealthEndpointSupport : Health contributor ...AdaptedReactiveHealthContributors$1 (redis) took 15709ms to respond` (00:48:08.630Z, traceId `6a73d9b04dcd773a41a3a8263fde1b79`), 동일 WARN `took 15503ms` (00:48:08.719Z, traceId `6a73d9b830a513e3385f8985bbc2c359`).
- 해당 트레이스의 `secured request` span도 **24.911초 / 17.590초 / 6.111초** — 세 건 모두 재기동 직후(00:47:44~00:48:04Z) chat-service 요청.
- 동일 파드의 minor GC pause rate가 구 파드 대비 4~5배(kh2dl 6.3e-4~9.0e-4 vs dnbs9 1.6e-4~2.0e-4).
- Redis `KEYS 0` 사용(`db.operation=KEYS`, 2회): 온라인 디바이스 조회에 O(N) 전체 키 스캔.

**확신도**: 중간 (증상은 확정, 원인 층위는 미확정)

**대기·지연 구간 판정**
| 구간 | 실측 | 상한 | 만료 여부 | 최종 상태 |
|---|---|---|---|---|
| JDBC `connection` (chat-service, HikariPool-1) | **17.596초** | Hikari `connectionTimeout` **미수집**(기본 30s 가정 불가) | **만료 아님** — 이벤트 `acquired` 가 span 시작 +5.9ms(00:47:58.630Z)에 발생. **획득 대기가 아니라 보유 시간**이다 | **성공(커밋)** — `commit` @ 00:48:16.136Z |
| Redis 명령 (`KEYS 0`) | 89.3ms / 96.6ms | `server.address=redis://172.31.46.124?timeout=2s` → **2초** | **만료 아님** (상한의 5% 미만) | **성공** (예외·에러 span 없음) |
| Redis health contributor | 15.709초 / 15.503초 | 명령 타임아웃 2초 | **모순 구간** — 개별 명령은 2초 상한 내인데 컨트리뷰터 응답은 15.7초. 어느 대기가 상한을 넘겼는지 특정할 span 없음 → **만료 여부 판정 불가** | **판정 불가** — WARN만 있고 예외·실패 로그 없음, 해당 트레이스에 error span 없으나 HTTP 상태 속성도 없음 |

**반증 데이터**
- **자원 경합 계열은 배제됨**: `hikaricp_connections_active` / `hikaricp_connections_pending` 이 **모든 서비스·모든 파드에서 전 구간 0** → 커넥션 풀 고갈 아님.
- **백엔드 인프라 지연도 아님**: MongoDB·Redis 개별 호출 90~111ms, `mongodb_up`=1, `up{job=redis}`=1, content-service의 Redis `GET`은 0.787ms·`INFO` 0.557ms로 정상. 즉 Redis/Mongo 자체는 빠름 → 15.7초는 chat-service 파드 내부(스레드/CPU/워밍업) 문제로 보이나, **컨테이너 CPU throttling·메모리 메트릭이 수집되지 않아 확증 불가**.
- 이 지연은 8분 14초 중 **약 20초**만 설명함 → 주 원인이 될 수 없고 후보 1에 부수적.

---

### 수집 공백이 결론에 미친 영향
- `sum(rate(http_server_requests_seconds_count{application="content-service", status="401"}[1m]))` 결측 → **인증 실패 계열 가설은 평가 자체가 불가**. 다만 관측된 트레이스에서 content-service 댓글 POST가 `status=200`으로 성공했고 알림 발행까지 도달했으므로, 이번 사건의 주 경로와는 무관할 가능성이 높다.
- 파드 재시작 사유(kube events / OOMKilled / 컨테이너 리소스), Kafka·Hikari·FCM 타임아웃 **설정값**, 재시작 직전 chat-service 로그가 모두 부재 → 후보 1의 **"무엇이 재시작을 유발했는가"** 는 **데이터 부족**으로 남긴다. 이 공백 때문에 후보 3의 확신도를 "높음"으로 올리지 않았다.

---

## 3. 권장 다음 조치

### 이미 발생한 피해: 복구 가능한가
**대부분 이미 자동 복구됨 — 추가 재처리 불필요.**
- 근거: `notification-processors/partition=3` 랙이 25 → **0**(00:49:05 이후)으로 완전 소진, `user.notifications.dlq` 랙 전 구간 0(폐기 없음), 관측된 1건은 `success=1, failure=0`으로 FCM 전달 완료. 알림 본문도 MongoDB `user_notifications` 에 insert되어 인앱 목록 조회로 회수 가능.
- **다만 25건 전부가 성공했다는 근거는 없음(1건만 트레이스 확보).** 확인 방법: Loki에서 `{service_name="chat-service"} |= "[kafka] 알림 처리 완료"` 를 00:47:50~00:50:00Z로 조회해 건수가 25인지 대조하고, 부족하면 `user.notifications` 파티션 3의 오프셋 구간(1130 부근~랙 소진 지점)을 대상으로 재처리.
- **복구 불가한 부분**: 지연 그 자체(8분 14초)와 그 사이 WebSocket 실시간 알림은 소급 복구 대상이 아님. 사용자 고지가 필요하면 그 방향으로 처리.

### 재발 방지
1. **chat-service 단일 파드 구조 해소** — 파드 1개가 `user.notifications` 컨슈머를 독점하므로 재시작 = 전면 정지. `replicas≥2` + PodDisruptionBudget. (현 데이터상 chat-service 인스턴스는 시점별 항상 1개)
2. **재시작 근본 원인 규명** — `kubectl describe pod` / kube events / `container_memory_working_set_bytes` / OOMKilled 여부 확보. 원인 미상인 채로 replica만 늘리면 동시 재시작 위험이 남음.
3. **알림 컨슈머 랙 알람** — `kafka_consumergroup_lag{consumergroup="notification-processors"} > 0 for 2m`. 이번엔 25까지 쌓이는 8분 동안 아무 알람도 없었고 사용자 문의로 발견됐다.
4. **헬스체크와 Redis 분리** — Redis 헬스 컨트리뷰터가 15.7초 응답. readiness probe가 이를 포함하면 기동 후 트래픽 편입이 지연되거나 liveness 실패로 재시작 루프가 될 수 있음. 컨트리뷰터 타임아웃을 명령 타임아웃(2s) 수준으로 고정하거나 probe 그룹에서 제외.
5. **Redis `KEYS` 제거** — 온라인 디바이스 조회에 `KEYS 0` 사용(2회). 키 수 증가 시 선형 악화 + Redis 싱글 스레드 블로킹. `SCAN` 또는 온라인 세션 집합(SET/HASH) 조회로 교체.
6. **알림 리스너의 트랜잭션·커넥션 보유 시간 단축** — JDBC 커넥션을 17.6초 점유(획득은 5.9ms). FCM 외부 호출(5.9초)이 트랜잭션 경계 안에 있으면 밖으로 빼고 타임아웃을 명시. 부수적으로 `spring.jpa.open-in-view` 를 명시적으로 `false` 설정(WARN 해소).

### 복구 확인
- `kafka_consumergroup_lag{consumergroup="notification-processors", topic="user.notifications"}` 전 파티션 0 유지(최소 15분).
- `up{job="chat-service"}` 1 유지 + 파드 `RESTARTS` 카운터 증가 없음.
- 실제 댓글 1건을 발생시켜 e2e 확인: content-service `publish user.notifications` → chat-service `receive` 간격이 초 단위인지(정상 시 13.5ms 발행 → 즉시 소비), `[push] 멀티캐스트 결과: success=1` 도달 시간이 수 초 내인지.
- `websocket_active_users > 0` 회복 여부 확인 — 현재 0이라 실시간 경로가 살아있는지 검증 자체가 불가하므로, 클라이언트 1개를 붙여 WebSocket 수신까지 확인할 것.
- chat-service `secured request` / 헬스 응답이 재기동 직후 값(24.9초 / 15.7초)에서 정상(content-service 수준 수 ms~수십 ms)으로 내려왔는지 재측정.

---

## 관측 증거 (Evidence)

> 이 조사가 실제로 본 값이다. 원본 응답 전체는 `reports/raw/scan-1785976340-*.json`에 있다.

### 호출 그래프 (트레이스에서 추출)

```
chat-service --db--> mongodb  7회  최대 110.9ms  [insert, find]
chat-service --db--> redis  2회  최대 96.6ms  [KEYS]
content-service --db--> redis  2회  최대 0.8ms  [GET, INFO]
chat-service --jdbc--> mysql/content (HikariPool-1)  7회  최대 17595.8ms
    events: acquired, commit
content-service --jdbc--> mysql/content (HikariPool-1)  10회  최대 68.4ms
    events: acquired, commit
content-service --messaging--> kafka/user.notifications  1회  최대 13.5ms  [publish]
kafka/user.notifications --messaging--> chat-service  1회  최대 20505.0ms  [receive]
```

### span (duration 상위 15 / 전체 52)

| ms | service | span | 시작 |
|---:|---|---|---|
| 24911.50 | chat-service | `secured request` | 2026-08-06T00:47:45.107119Z |
| 20505.00 | chat-service | `receive` | 2026-08-06T00:47:55.722081Z |
| 17595.85 | chat-service | `connection` | 2026-08-06T00:47:58.624549Z |
| 17589.95 | chat-service | `secured request` | 2026-08-06T00:47:52.429445Z |
| 17212.14 | chat-service | `user-notification-service#process-notification` | 2026-08-06T00:47:58.917952Z |
| 6708.25 | chat-service | `push-dispatcher#dispatch` | 2026-08-06T00:48:09.418255Z |
| 6111.16 | chat-service | `secured request` | 2026-08-06T00:48:03.819092Z |
| 1104.06 | chat-service | `user-notification-web-socket-sender#send-notificat` | 2026-08-06T00:48:07.422961Z |
| 398.75 | chat-service | `security filterchain before` | 2026-08-06T00:48:03.420146Z |
| 397.76 | chat-service | `security filterchain before` | 2026-08-06T00:47:44.632550Z |
| 110.93 | chat-service | `find toychat` | 2026-08-06T00:48:09.919608Z |
| 101.32 | chat-service | `find toychat` | 2026-08-06T00:48:08.722948Z |
| 100.35 | chat-service | `connection` | 2026-08-06T00:48:04.030947Z |
| 98.09 | chat-service | `insert toychat` | 2026-08-06T00:48:04.927750Z |
| 97.95 | chat-service | `find toychat` | 2026-08-06T00:48:09.622818Z |

### 로그 원문 (18 / 전체 18줄)

```
2026-08-06T00:47:09.624127059Z  [chat-service]  [2m2026-08-06T09:47:09.623+09:00[0;39m [33m WARN [traceId=NONE,spanId=NONE,userId=NONE][0;39m [35m7[0;39m [2m--- [chat-service] [           main] [                                                 ] [0;39m[36mJpaBaseConfiguration$JpaWebConfiguration[0;39m [2m:[0;39m spring.jpa.open-in-view is enabled by default. Therefore, database queries may be performed during view rendering. Explicitly configure spring.jpa.open-in-view to disable this warning
2026-08-06T00:48:08.524608213Z  [chat-service]  [2m2026-08-06T09:48:08.524+09:00[0;39m [32m INFO [traceId=6a73d7cd82e6e12dd5c7a7ca51858e6e,spanId=01bbbfca240c8de2,userId=NONE][0;39m [35m7[0;39m [2m--- [chat-service] [ntainer#5-1-C-1] [6a73d7cd82e6e12dd5c7a7ca51858e6e-01bbbfca240c8de2] [0;39m[36m.t.a.u.s.UserNotificationWebSocketSender[0;39m [2m:[0;39m [notify] 사용자 오프라인 상태로 WebSocket 전송 스킵: userId=7
2026-08-06T00:48:08.529810845Z  [chat-service]  [2m2026-08-06T09:48:08.527+09:00[0;39m [32m INFO [traceId=6a73d7cd82e6e12dd5c7a7ca51858e6e,spanId=b508a9569ea6bd13,userId=NONE][0;39m [35m7[0;39m [2m--- [chat-service] [ntainer#5-1-C-1] [6a73d7cd82e6e12dd5c7a7ca51858e6e-b508a9569ea6bd13] [0;39m[36mc.e.t.a.u.s.UserNotificationService     [0;39m [2m:[0;39m [notify] WebSocket 알림 전송 완료: userId=7
2026-08-06T00:48:08.719080655Z  [chat-service]  [2m2026-08-06T09:48:08.630+09:00[0;39m [33m WARN [traceId=6a73d9b04dcd773a41a3a8263fde1b79,spanId=36b2ebc37468686b,userId=NONE][0;39m [35m7[0;39m [2m--- [chat-service] [nio-8090-exec-1] [6a73d9b04dcd773a41a3a8263fde1b79-36b2ebc37468686b] [0;39m[36mo.s.b.a.health.HealthEndpointSupport    [0;39m [2m:[0;39m Health contributor org.springframework.boot.actuate.autoconfigure.health.HealthEndpointConfiguration$AdaptedReactiveHealthContributors$1 (redis) took 15709ms to respond
2026-08-06T00:48:08.719080655Z  [chat-service]  [2m2026-08-06T09:48:08.630+09:00[0;39m [33m WARN [traceId=6a73d9b04dcd773a41a3a8263fde1b79,spanId=36b2ebc37468686b,userId=NONE][0;39m [35m7[0;39m [2m--- [chat-service] [nio-8090-exec-1] [6a73d9b04dcd773a41a3a8263fde1b79-36b2ebc37468686b] [0;39m[36mo.s.b.a.health.HealthEndpointSupport    [0;39m [2m:[0;39m Health contributor org.springframework.boot.actuate.autoconfigure.health.HealthEndpointConfiguration$AdaptedReactiveHealthContributors$1 (redis) took 15709ms to respond
2026-08-06T00:48:08.721481543Z  [chat-service]  [2m2026-08-06T09:48:08.719+09:00[0;39m [33m WARN [traceId=6a73d9b830a513e3385f8985bbc2c359,spanId=bdc3b5811970a66c,userId=NONE][0;39m [35m7[0;39m [2m--- [chat-service] [nio-8090-exec-2] [6a73d9b830a513e3385f8985bbc2c359-bdc3b5811970a66c] [0;39m[36mo.s.b.a.health.HealthEndpointSupport    [0;39m [2m:[0;39m Health contributor org.springframework.boot.actuate.autoconfigure.health.HealthEndpointConfiguration$AdaptedReactiveHealthContributors$1 (redis) took 15503ms to respond
2026-08-06T00:48:08.721481543Z  [chat-service]  [2m2026-08-06T09:48:08.719+09:00[0;39m [33m WARN [traceId=6a73d9b830a513e3385f8985bbc2c359,spanId=bdc3b5811970a66c,userId=NONE][0;39m [35m7[0;39m [2m--- [chat-service] [nio-8090-exec-2] [6a73d9b830a513e3385f8985bbc2c359-bdc3b5811970a66c] [0;39m[36mo.s.b.a.health.HealthEndpointSupport    [0;39m [2m:[0;39m Health contributor org.springframework.boot.actuate.autoconfigure.health.HealthEndpointConfiguration$AdaptedReactiveHealthContributors$1 (redis) took 15503ms to respond
2026-08-06T00:48:09.524146941Z  [chat-service]  [2m2026-08-06T09:48:09.523+09:00[0;39m [32m INFO [traceId=6a73d7cd82e6e12dd5c7a7ca51858e6e,spanId=1c0c607ed9c6dfa2,userId=NONE][0;39m [35m7[0;39m [2m--- [chat-service] [ntainer#5-1-C-1] [6a73d7cd82e6e12dd5c7a7ca51858e6e-1c0c607ed9c6dfa2] [0;39m[36mc.e.t.a.n.service.PushDispatcher        [0;39m [2m:[0;39m [push] 시작: userId=7, onlineDevices=[] (제외 대상)
2026-08-06T00:48:10.217960974Z  [chat-service]  [2m2026-08-06T09:48:10.130+09:00[0;39m [32m INFO [traceId=6a73d7cd82e6e12dd5c7a7ca51858e6e,spanId=1c0c607ed9c6dfa2,userId=NONE][0;39m [35m7[0;39m [2m--- [chat-service] [ntainer#5-1-C-1] [6a73d7cd82e6e12dd5c7a7ca51858e6e-1c0c607ed9c6dfa2] [0;39m[36mc.e.t.a.u.service.FcmTokenCopyService   [0;39m [2m:[0;39m [notify] userId=7, activeRows=1, excludeDeviceIds=[]
2026-08-06T00:48:10.219619727Z  [chat-service]  [2m2026-08-06T09:48:10.217+09:00[0;39m [32m INFO [traceId=6a73d7cd82e6e12dd5c7a7ca51858e6e,spanId=1c0c607ed9c6dfa2,userId=NONE][0;39m [35m7[0;39m [2m--- [chat-service] [ntainer#5-1-C-1] [6a73d7cd82e6e12dd5c7a7ca51858e6e-1c0c607ed9c6dfa2] [0;39m[36mc.e.t.a.u.service.FcmTokenCopyService   [0;39m [2m:[0;39m [notify] row: deviceId=web-93i0tuszk-1778982201034, platform=IOS, tokenLen=142, tokenPrefix=fdTflj2F00j8gwSCa9mj..., excluded=false, updatedAt=2026-06-18T21:14:29.830
2026-08-06T00:48:10.221153441Z  [chat-service]  [2m2026-08-06T09:48:10.220+09:00[0;39m [32m INFO [traceId=6a73d7cd82e6e12dd5c7a7ca51858e6e,spanId=1c0c607ed9c6dfa2,userId=NONE][0;39m [35m7[0;39m [2m--- [chat-service] [ntainer#5-1-C-1] [6a73d7cd82e6e12dd5c7a7ca51858e6e-1c0c607ed9c6dfa2] [0;39m[36mc.e.t.a.u.service.FcmTokenCopyService   [0;39m [2m:[0;39m [notify] userId=7, 발송 대상 토큰 수=1
2026-08-06T00:48:10.221733269Z  [chat-service]  [2m2026-08-06T09:48:10.221+09:00[0;39m [32m INFO [traceId=6a73d7cd82e6e12dd5c7a7ca51858e6e,spanId=1c0c607ed9c6dfa2,userId=NONE][0;39m [35m7[0;39m [2m--- [chat-service] [ntainer#5-1-C-1] [6a73d7cd82e6e12dd5c7a7ca51858e6e-1c0c607ed9c6dfa2] [0;39m[36mc.e.t.a.n.service.PushDispatcher        [0;39m [2m:[0;39m [push] 발송 시도: userId=7, tokens=1, skippedDevices=0, providerImpl=FcmPushNotificationProvider
2026-08-06T00:48:10.222195553Z  [chat-service]  [2m2026-08-06T09:48:10.221+09:00[0;39m [32m INFO [traceId=6a73d7cd82e6e12dd5c7a7ca51858e6e,spanId=1c0c607ed9c6dfa2,userId=NONE][0;39m [35m7[0;39m [2m--- [chat-service] [ntainer#5-1-C-1] [6a73d7cd82e6e12dd5c7a7ca51858e6e-1c0c607ed9c6dfa2] [0;39m[36m.e.t.a.n.s.i.FcmPushNotificationProvider[0;39m [2m:[0;39m [push] 발송 준비: tokens=1, title='새 댓글', body='운영자님이 [인생 띵작 애니 베스트] 배틀의 [귀멸의 칼날] 아이템에 댓글을 남겼습니다.', dataKeys=[actionUrl, referenceType, type, referenceId]
2026-08-06T00:48:10.222592697Z  [chat-service]  [2m2026-08-06T09:48:10.222+09:00[0;39m [32m INFO [traceId=6a73d7cd82e6e12dd5c7a7ca51858e6e,spanId=1c0c607ed9c6dfa2,userId=NONE][0;39m [35m7[0;39m [2m--- [chat-service] [ntainer#5-1-C-1] [6a73d7cd82e6e12dd5c7a7ca51858e6e-1c0c607ed9c6dfa2] [0;39m[36m.e.t.a.n.s.i.FcmPushNotificationProvider[0;39m [2m:[0;39m [push] token[0]: prefix=fdTflj2F00j8gwSCa9mj..., len=142
2026-08-06T00:48:16.123417334Z  [chat-service]  [2m2026-08-06T09:48:16.121+09:00[0;39m [32m INFO [traceId=6a73d7cd82e6e12dd5c7a7ca51858e6e,spanId=1c0c607ed9c6dfa2,userId=NONE][0;39m [35m7[0;39m [2m--- [chat-service] [ntainer#5-1-C-1] [6a73d7cd82e6e12dd5c7a7ca51858e6e-1c0c607ed9c6dfa2] [0;39m[36m.e.t.a.n.s.i.FcmPushNotificationProvider[0;39m [2m:[0;39m [push] 멀티캐스트 결과: tokens=1, success=1, failure=0
2026-08-06T00:48:16.123809240Z  [chat-service]  [2m2026-08-06T09:48:16.123+09:00[0;39m [32m INFO [traceId=6a73d7cd82e6e12dd5c7a7ca51858e6e,spanId=1c0c607ed9c6dfa2,userId=NONE][0;39m [35m7[0;39m [2m--- [chat-service] [ntainer#5-1-C-1] [6a73d7cd82e6e12dd5c7a7ca51858e6e-1c0c607ed9c6dfa2] [0;39m[36m.e.t.a.n.s.i.FcmPushNotificationProvider[0;39m [2m:[0;39m [push] OK index=0, token=fdTflj2F00j8gwSCa9mj..., messageId=projects/toy-chat-30d47/messages/1785977295669348
2026-08-06T00:48:16.127692103Z  [chat-service]  [2m2026-08-06T09:48:16.126+09:00[0;39m [32m INFO [traceId=6a73d7cd82e6e12dd5c7a7ca51858e6e,spanId=b508a9569ea6bd13,userId=NONE][0;39m [35m7[0;39m [2m--- [chat-service] [ntainer#5-1-C-1] [6a73d7cd82e6e12dd5c7a7ca51858e6e-b508a9569ea6bd13] [0;39m[36mc.e.t.a.u.s.UserNotificationService     [0;39m [2m:[0;39m [notify] 알림 처리 완료: userId=7, type=BATTLE_ITEM_COMMENT, id=6a73d9c2d2c54bf44544e283
2026-08-06T00:48:16.224825404Z  [chat-service]  [2m2026-08-06T09:48:16.222+09:00[0;39m [32m INFO [traceId=6a73d7cd82e6e12dd5c7a7ca51858e6e,spanId=21f127a4fe211b6d,userId=NONE][0;39m [35m7[0;39m [2m--- [chat-service] [ntainer#5-1-C-1] [6a73d7cd82e6e12dd5c7a7ca51858e6e-21f127a4fe211b6d] [0;39m[36mc.e.t.a.k.u.UserNotificationConsumer    [0;39m [2m:[0;39m [kafka] 알림 처리 완료: userId=7, type=BATTLE_ITEM_COMMENT
```

### 메트릭 시계열

| 쿼리 | series | 점 | min | max | last | 값이 0이던 구간 |
|---|---|---:|---:|---:|---:|---|
| `hikaricp_connections_active` | `{__name__=hikaricp_connections_active, application=auth-service, cluster=yogurtte-k3s-prod, container=auth-service, instance=10.42.1.51:8090, job=auth-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=auth-service-5999bb9f5c-lzp4p, pool=HikariPool-1, service=auth-service}` | 81 | 0 | 0 | 0 | **2026-08-06T00:32:20Z ~ 2026-08-06T00:52:20Z** |
| `hikaricp_connections_active` | `{__name__=hikaricp_connections_active, application=chat-service, cluster=yogurtte-k3s-prod, container=chat-service, instance=10.42.1.54:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-fdcc7c776-dnbs9, pool=HikariPool-1}` | 32 | 0 | 0 | 0 | **2026-08-06T00:32:20Z ~ 2026-08-06T00:40:05Z** |
| `hikaricp_connections_active` | `{__name__=hikaricp_connections_active, application=chat-service, cluster=yogurtte-k3s-prod, container=chat-service, instance=10.42.1.55:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-fdcc7c776-kh2dl, pool=HikariPool-1}` | 13 | 0 | 0 | 0 | **2026-08-06T00:49:20Z ~ 2026-08-06T00:52:20Z** |
| `hikaricp_connections_active` | `{__name__=hikaricp_connections_active, application=content-service, cluster=yogurtte-k3s-prod, container=content-service, instance=10.42.1.53:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-85f648fcff-sp24n, pool=HikariPool-1}` | 81 | 0 | 0 | 0 | **2026-08-06T00:32:20Z ~ 2026-08-06T00:52:20Z** |
| `hikaricp_connections_active` | `{__name__=hikaricp_connections_active, application=content-service, cluster=yogurtte-k3s-prod, container=content-service, instance=10.42.3.46:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-85f648fcff-v2pw9, pool=HikariPool-1}` | 81 | 0 | 0 | 0 | **2026-08-06T00:32:20Z ~ 2026-08-06T00:52:20Z** |
| `hikaricp_connections_pending` | `{__name__=hikaricp_connections_pending, application=auth-service, cluster=yogurtte-k3s-prod, container=auth-service, instance=10.42.1.51:8090, job=auth-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=auth-service-5999bb9f5c-lzp4p, pool=HikariPool-1, service=auth-service}` | 81 | 0 | 0 | 0 | **2026-08-06T00:32:20Z ~ 2026-08-06T00:52:20Z** |
| `hikaricp_connections_pending` | `{__name__=hikaricp_connections_pending, application=chat-service, cluster=yogurtte-k3s-prod, container=chat-service, instance=10.42.1.54:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-fdcc7c776-dnbs9, pool=HikariPool-1}` | 32 | 0 | 0 | 0 | **2026-08-06T00:32:20Z ~ 2026-08-06T00:40:05Z** |
| `hikaricp_connections_pending` | `{__name__=hikaricp_connections_pending, application=chat-service, cluster=yogurtte-k3s-prod, container=chat-service, instance=10.42.1.55:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-fdcc7c776-kh2dl, pool=HikariPool-1}` | 13 | 0 | 0 | 0 | **2026-08-06T00:49:20Z ~ 2026-08-06T00:52:20Z** |
| `hikaricp_connections_pending` | `{__name__=hikaricp_connections_pending, application=content-service, cluster=yogurtte-k3s-prod, container=content-service, instance=10.42.1.53:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-85f648fcff-sp24n, pool=HikariPool-1}` | 81 | 0 | 0 | 0 | **2026-08-06T00:32:20Z ~ 2026-08-06T00:52:20Z** |
| `hikaricp_connections_pending` | `{__name__=hikaricp_connections_pending, application=content-service, cluster=yogurtte-k3s-prod, container=content-service, instance=10.42.3.46:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-85f648fcff-v2pw9, pool=HikariPool-1}` | 81 | 0 | 0 | 0 | **2026-08-06T00:32:20Z ~ 2026-08-06T00:52:20Z** |
| `rate(jvm_gc_pause_seconds_sum[5m])` | `{action=end of major GC, application=chat-service, cause=Allocation Failure, cluster=yogurtte-k3s-prod, container=chat-service, gc=MarkSweepCompact, instance=10.42.1.54:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-fdcc7c776-dnbs9}` | 44 | 0 | 0 | 0 | **2026-08-06T00:32:20Z ~ 2026-08-06T00:43:05Z** |
| `rate(jvm_gc_pause_seconds_sum[5m])` | `{action=end of minor GC, application=auth-service, cause=G1 Evacuation Pause, cluster=yogurtte-k3s-prod, container=auth-service, gc=G1 Young Generation, instance=10.42.1.51:8090, job=auth-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=auth-service-5999bb9f5c-lzp4p, service=auth-service}` | 81 | 0 | 0 | 0 | **2026-08-06T00:32:20Z ~ 2026-08-06T00:52:20Z** |
| `rate(jvm_gc_pause_seconds_sum[5m])` | `{action=end of minor GC, application=auth-service, cause=Metadata GC Threshold, cluster=yogurtte-k3s-prod, container=auth-service, gc=G1 Young Generation, instance=10.42.1.51:8090, job=auth-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=auth-service-5999bb9f5c-lzp4p, service=auth-service}` | 81 | 0 | 0 | 0 | **2026-08-06T00:32:20Z ~ 2026-08-06T00:52:20Z** |
| `rate(jvm_gc_pause_seconds_sum[5m])` | `{action=end of minor GC, application=chat-service, cause=Allocation Failure, cluster=yogurtte-k3s-prod, container=chat-service, gc=Copy, instance=10.42.1.54:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-fdcc7c776-dnbs9}` | 44 | 0.000 | 0.000 | 0.000 | — |
| `rate(jvm_gc_pause_seconds_sum[5m])` | `{action=end of minor GC, application=chat-service, cause=Allocation Failure, cluster=yogurtte-k3s-prod, container=chat-service, gc=Copy, instance=10.42.1.55:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-fdcc7c776-kh2dl}` | 9 | 0.001 | 0.001 | 0.001 | — |
| `rate(jvm_gc_pause_seconds_sum[5m])` | `{action=end of minor GC, application=content-service, cause=Allocation Failure, cluster=yogurtte-k3s-prod, container=content-service, gc=Copy, instance=10.42.1.53:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-85f648fcff-sp24n}` | 81 | 0 | 0.000 | 0 | **2026-08-06T00:32:20Z ~ 2026-08-06T00:38:35Z, 2026-08-06T00:42:50Z ~ 2026-08-06T00:52:20Z** |
| `rate(jvm_gc_pause_seconds_sum[5m])` | `{action=end of minor GC, application=content-service, cause=Allocation Failure, cluster=yogurtte-k3s-prod, container=content-service, gc=Copy, instance=10.42.3.46:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-85f648fcff-v2pw9}` | 81 | 0 | 0.000 | 0 | **2026-08-06T00:36:05Z ~ 2026-08-06T00:47:50Z, 2026-08-06T00:52:05Z ~ 2026-08-06T00:52:20Z** |
| `up` | `{__name__=up, app=node-exporter, cluster=yogurtte-k3s-prod, component=metrics, container=node-exporter, instance=ip-172-31-40-241, job=integrations/node_exporter, k8s_cluster_name=yogurtte-k3s-prod, namespace=monitoring, pod=grafana-k8s-monitoring-node-exporter-cph5l, source=kubernetes, workload=DaemonSet/grafana-k8s-monitoring-node-exporter}` | 81 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, app=node-exporter, cluster=yogurtte-k3s-prod, component=metrics, container=node-exporter, instance=ip-172-31-45-39, job=integrations/node_exporter, k8s_cluster_name=yogurtte-k3s-prod, namespace=monitoring, pod=grafana-k8s-monitoring-node-exporter-9kvqt, source=kubernetes, workload=DaemonSet/grafana-k8s-monitoring-node-exporter}` | 81 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, cluster=yogurtte-k3s-prod, container=auth-service, instance=10.42.1.51:8090, job=auth-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=auth-service-5999bb9f5c-lzp4p}` | 81 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, cluster=yogurtte-k3s-prod, container=chat-service, instance=10.42.1.54:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-fdcc7c776-dnbs9}` | 32 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, cluster=yogurtte-k3s-prod, container=chat-service, instance=10.42.1.55:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-fdcc7c776-kh2dl}` | 13 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, cluster=yogurtte-k3s-prod, container=content-service, instance=10.42.1.53:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-85f648fcff-sp24n}` | 81 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, cluster=yogurtte-k3s-prod, container=content-service, instance=10.42.3.46:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-85f648fcff-v2pw9}` | 81 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, cluster=yogurtte-k3s-prod, instance=10.42.1.248:8080, job=integrations/kubernetes/kube-state-metrics, k8s_cluster_name=yogurtte-k3s-prod, source=kubernetes}` | 81 | 1 | 1 | 1 | — |
| `mongodb_up` | `{__name__=mongodb_up, cluster=yogurtte-k3s-prod, instance=infra-server, job=mongodb, k8s_cluster_name=yogurtte-k3s-prod}` | 81 | 1 | 1 | 1 | — |
| `kafka_brokers` | `{__name__=kafka_brokers, cluster=yogurtte-k3s-prod, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod}` | 81 | 1 | 1 | 1 | — |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=chat-service-fcm-tokens, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=0, topic=user.fcm-tokens}` | 81 | 0 | 0 | 0 | **2026-08-06T00:32:20Z ~ 2026-08-06T00:52:20Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=chat-service-fcm-tokens, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=1, topic=user.fcm-tokens}` | 81 | 0 | 0 | 0 | **2026-08-06T00:32:20Z ~ 2026-08-06T00:52:20Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=chat-service-fcm-tokens, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=2, topic=user.fcm-tokens}` | 81 | 0 | 0 | 0 | **2026-08-06T00:32:20Z ~ 2026-08-06T00:52:20Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=chat-service-notification-settings, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=0, topic=user.notification-settings}` | 81 | 0 | 0 | 0 | **2026-08-06T00:32:20Z ~ 2026-08-06T00:52:20Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=chat-service-notification-settings, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=1, topic=user.notification-settings}` | 81 | 0 | 0 | 0 | **2026-08-06T00:32:20Z ~ 2026-08-06T00:52:20Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=chat-service-notification-settings, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=2, topic=user.notification-settings}` | 81 | 0 | 0 | 0 | **2026-08-06T00:32:20Z ~ 2026-08-06T00:52:20Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=db-writer, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=0, topic=chat.messages}` | 81 | 0 | 0 | 0 | **2026-08-06T00:32:20Z ~ 2026-08-06T00:52:20Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=db-writer, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=1, topic=chat.messages}` | 81 | 0 | 0 | 0 | **2026-08-06T00:32:20Z ~ 2026-08-06T00:52:20Z** |
| `websocket_active_users` | `{__name__=websocket_active_users, application=chat-service, cluster=yogurtte-k3s-prod, container=chat-service, instance=10.42.1.54:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-fdcc7c776-dnbs9}` | 32 | 0 | 0 | 0 | **2026-08-06T00:32:20Z ~ 2026-08-06T00:40:05Z** |
| `websocket_active_users` | `{__name__=websocket_active_users, application=chat-service, cluster=yogurtte-k3s-prod, container=chat-service, instance=10.42.1.55:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-fdcc7c776-kh2dl}` | 13 | 0 | 0 | 0 | **2026-08-06T00:49:20Z ~ 2026-08-06T00:52:20Z** |

값이 0이던 구간은 굵게 표시했다 — 프로세스가 사라져 시계열이 꺾인 것이 유일한 신호인 장애가 있다.

