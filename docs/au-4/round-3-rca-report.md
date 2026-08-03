# RCA Report — `scan-1785764400`

| 항목 | 값 |
|---|---|
| 모드 | rca |
| 질문 | 최근 1시간 안에 피드에 작성자 이름이 이상하게 나온다는 제보가 있다. 원인을 조사해줘 |
| 시각 | 2026-08-03T14:18:11.688234Z |
| provider | claude-cli |
| model | `claude-opus-5` · turns 1 |
| prompt | `./prompts/system-prompt.md` |
| tokens | in 108110 (cacheRead 18,133 · cacheCreate 89,975) / out 6255 · cost $1.1302 |
| elapsed | total 106930ms (tempo 1567 · loki 281 · mimir 604 · assemble 25 · llm 98239) |

## 탐색 (Triage)

| 항목 | 값 |
|---|---|
| 시간창 해석 | 상대 표현 '최근 1시간' |
| 시간창 확신도 | EXACT |
| 스윕 창 | 2026-08-03T13:17:11.874303Z ~ 2026-08-03T14:17:11.874303Z |
| 좁힌 창 | 2026-08-03T13:40:00Z ~ 2026-08-03T14:17:11.874303Z |
| 대상 | content-service, auth-service |
| traceId | 6a709b5f358d68fc3c806907ad35c966 |
| 트레이스 후보 | 5건 |
| 장애 후보 | 13건 · 선택 INC-7, INC-8, INC-9, INC-10, INC-11, INC-12, INC-13 |
| 계획 파싱 | 성공 |
| 스윕 컨텍스트 | 후보 + 원본 (A) |
| prompt | `./prompts/triage-prompt.md` |
| tokens | in 50432 / out 3606 · cost $0.4728 |
| chars | 컨텍스트 50,377 + 프롬프트 1,399 = **51,776** |
| elapsed | survey 1387ms · llm 58394ms |

**선정 이유**: 증상이 '피드 화면의 작성자 이름'이므로 피드 조회 엔드포인트(/feeds/following, /feeds/scroll)와 그 트레이스 안에서 함께 호출된 auth-service를, 두 이상 클러스터(13:45, 14:05~14:15) 모두에서 상·하류를 묶어 본다.

**근거**

- INC-9: content-service http get /feeds/following 3,287ms (13:45:03Z, slow 채널) — 피드 조회 경로 그 자체가 느려졌다
- INC-9 트레이스 6a709b5f358d68fc3c806907ad35c966의 serviceStats에 auth-service spanCount 8이 포함 — content가 피드 응답을 만들며 auth를 호출하는 구간(작성자 이름 enrichment 후보)이 존재한다
- INC-9 내부 스팬 80fb75ca4f14b6a1(3,285ms)/1eca1fb4d7020d2a(3,275ms)가 루트 3,287ms를 거의 전부 차지 — 하나의 하위 호출이 응답 전체를 잡아먹었다
- INC-8/INC-7: 같은 5분(13:45~13:50)에 content-service ERROR/WARN 3건 + auth-service 1건 동시 발생 — 지연과 로그가 같은 시각에 겹친다
- INC-11: content-service http get /feeds/scroll 71ms error (14:09:25Z), 28스팬 중 errorCount 1, 실패 스팬 be415a34dcfa6059은 22ms — 전체 조회는 성공했는데 내부 부가 호출 1건만 깨졌다(이름이 비거나 폴백될 때의 지문)
- INC-10/INC-12: 14:05~14:15에 content 1건 + auth 4건 ERROR/WARN — auth 쪽 로그가 content보다 많다
- INC-13: auth-service http post /login 3,402ms (14:11:36Z) — 같은 구간에 auth 응답이 실제로 느려져 있었음을 뒷받침
- up 시계열에서 auth-service 파드가 qqrss(~13:27) → lbpf2(13:32~14:02) → hmgp9(14:08~)로 두 번 교체 — auth 인스턴스 전환 시점이 두 이상 클러스터(13:45, 14:05~14:15)와 인접
- min_over_time(up[5m]) / mongodb_up / kafka_brokers 전 구간 1, kafka_consumergroup_lag 전 파티션 0 — 인프라·비동기 파이프라인 정체는 배제된다

**스윕이 찾은 트레이스** (고른 것은 6a709b5f358d68fc3c806907ad35c966)

| traceId | 채널 | root service | root span | ms |
|---|---|---|---|---:|
| `6a70a115f09975daa14ec1a090053942` | error | content-service | http get /feeds/scroll | 71 |
| `6a7095b09dbabfa223fd8e4c12fda927` | error | content-service | http post /feeds | 95 |
| `6a70a19837babc73e8d2404b21bd15b2` | slow | auth-service | http post /login | 3402 |
| `6a709b5f358d68fc3c806907ad35c966` ←선택 | slow | content-service | http get /feeds/following | 3287 |
| `6a709737e51278eeebf347141cc5c3f2` | slow | auth-service | http post /login | 3509 |

**장애 후보** (코드가 신호를 묶은 것 · 창은 여기서 계산됨)

## INC-1  auth-service  |  ERROR/WARN
- 구간: 2026-08-03T13:15:00Z ~ 2026-08-03T13:30:00Z  (LOKI · 집계 해상도만큼 흐림)
- ERROR/WARN 5건 (2026-08-03T13:15:00Z ~ 2026-08-03T13:20:00Z)
- ERROR/WARN 1건 (2026-08-03T13:20:00Z ~ 2026-08-03T13:25:00Z)
- ERROR/WARN 4건 (2026-08-03T13:25:00Z ~ 2026-08-03T13:30:00Z)
- 같은 시각의 다른 후보: INC-2, INC-3, INC-4, INC-5, INC-6  (인과 여부는 판단하지 않았다)

## INC-2  kafka  |  kafka_consumergroup_lag
- 구간: 2026-08-03T13:17:11Z ~ 2026-08-03T14:17:11Z  (MIMIR · 집계 해상도만큼 흐림)
- kafka_consumergroup_lag{consumergroup=chat-service-fcm-tokens, partition=0, topic=user.fcm-tokens} 가 0이었다 (2026-08-03T13:17:11Z ~ 2026-08-03T14:17:11Z)
- kafka_consumergroup_lag{consumergroup=chat-service-fcm-tokens, partition=1, topic=user.fcm-tokens} 가 0이었다 (2026-08-03T13:17:11Z ~ 2026-08-03T14:17:11Z)
- kafka_consumergroup_lag{consumergroup=chat-service-fcm-tokens, partition=2, topic=user.fcm-tokens} 가 0이었다 (2026-08-03T13:17:11Z ~ 2026-08-03T14:17:11Z)
- kafka_consumergroup_lag{consumergroup=chat-service-notification-settings, partition=0, topic=user.notification-settings} 가 0이었다 (2026-08-03T13:17:11Z ~ 2026-08-03T14:17:11Z)
- kafka_consumergroup_lag{consumergroup=chat-service-notification-settings, partition=1, topic=user.notification-settings} 가 0이었다 (2026-08-03T13:17:11Z ~ 2026-08-03T14:17:11Z)
- kafka_consumergroup_lag{consumergroup=chat-service-notification-settings, partition=2, topic=user.notification-settings} 가 0이었다 (2026-08-03T13:17:11Z ~ 2026-08-03T14:17:11Z)
- kafka_consumergroup_lag{consumergroup=db-writer, partition=0, topic=chat.messages} 가 0이었다 (2026-08-03T13:17:11Z ~ 2026-08-03T14:17:11Z)
- kafka_consumergroup_lag{consumergroup=db-writer, partition=1, topic=chat.messages} 가 0이었다 (2026-08-03T13:17:11Z ~ 2026-08-03T14:17:11Z)
- kafka_consumergroup_lag{consumergroup=db-writer, partition=11, topic=chat.messages} 가 0이었다 (2026-08-03T13:17:11Z ~ 2026-08-03T14:17:11Z)
- kafka_consumergroup_lag{consumergroup=db-writer, partition=3, topic=chat.messages} 가 0이었다 (2026-08-03T13:17:11Z ~ 2026-08-03T14:17:11Z)
- kafka_consumergroup_lag{consumergroup=db-writer, partition=4, topic=chat.messages} 가 0이었다 (2026-08-03T13:17:11Z ~ 2026-08-03T14:17:11Z)
- kafka_consumergroup_lag{consumergroup=db-writer, partition=5, topic=chat.messages} 가 0이었다 (2026-08-03T13:17:11Z ~ 2026-08-03T14:17:11Z)
- kafka_consumergroup_lag{consumergroup=db-writer, partition=7, topic=chat.messages} 가 0이었다 (2026-08-03T13:17:11Z ~ 2026-08-03T14:17:11Z)
- kafka_consumergroup_lag{consumergroup=db-writer, partition=8, topic=chat.messages} 가 0이었다 (2026-08-03T13:17:11Z ~ 2026-08-03T14:17:11Z)
- kafka_consumergroup_lag{consumergroup=db-writer-retry-1000, partition=0, topic=chat.messages-retry-1000} 가 0이었다 (2026-08-03T13:17:11Z ~ 2026-08-03T14:17:11Z)
- kafka_consumergroup_lag{consumergroup=db-writer-retry-2000, partition=0, topic=chat.messages-retry-2000} 가 0이었다 (2026-08-03T13:17:11Z ~ 2026-08-03T14:17:11Z)
- kafka_consumergroup_lag{consumergroup=db-writer-retry-4000, partition=0, topic=chat.messages-retry-4000} 가 0이었다 (2026-08-03T13:17:11Z ~ 2026-08-03T14:17:11Z)
- kafka_consumergroup_lag{consumergroup=notification, partition=0, topic=chat.messages} 가 0이었다 (2026-08-03T13:17:11Z ~ 2026-08-03T14:17:11Z)
- kafka_consumergroup_lag{consumergroup=notification, partition=1, topic=chat.messages} 가 0이었다 (2026-08-03T13:17:11Z ~ 2026-08-03T14:17:11Z)
- kafka_consumergroup_lag{consumergroup=notification, partition=11, topic=chat.messages} 가 0이었다 (2026-08-03T13:17:11Z ~ 2026-08-03T14:17:11Z)
- kafka_consumergroup_lag{consumergroup=notification, partition=2, topic=chat.messages} 가 0이었다 (2026-08-03T13:17:11Z ~ 2026-08-03T14:17:11Z)
- kafka_consumergroup_lag{consumergroup=notification, partition=3, topic=chat.messages} 가 0이었다 (2026-08-03T13:17:11Z ~ 2026-08-03T14:17:11Z)
- kafka_consumergroup_lag{consumergroup=notification, partition=4, topic=chat.messages} 가 0이었다 (2026-08-03T13:17:11Z ~ 2026-08-03T14:17:11Z)
- kafka_consumergroup_lag{consumergroup=notification, partition=5, topic=chat.messages} 가 0이었다 (2026-08-03T13:17:11Z ~ 2026-08-03T14:17:11Z)
- kafka_consumergroup_lag{consumergroup=notification, partition=7, topic=chat.messages} 가 0이었다 (2026-08-03T13:17:11Z ~ 2026-08-03T14:17:11Z)
- kafka_consumergroup_lag{consumergroup=notification, partition=8, topic=chat.messages} 가 0이었다 (2026-08-03T13:17:11Z ~ 2026-08-03T14:17:11Z)
- kafka_consumergroup_lag{consumergroup=notification-processors, partition=0, topic=user.notifications} 가 0이었다 (2026-08-03T13:17:11Z ~ 2026-08-03T14:17:11Z)
- kafka_consumergroup_lag{consumergroup=notification-processors, partition=1, topic=user.notifications} 가 0이었다 (2026-08-03T13:17:11Z ~ 2026-08-03T14:17:11Z)
- kafka_consumergroup_lag{consumergroup=notification-processors, partition=2, topic=user.notifications} 가 0이었다 (2026-08-03T13:17:11Z ~ 2026-08-03T14:17:11Z)
- kafka_consumergroup_lag{consumergroup=notification-processors, partition=3, topic=user.notifications} 가 0이었다 (2026-08-03T13:17:11Z ~ 2026-08-03T14:17:11Z)
- kafka_consumergroup_lag{consumergroup=notification-processors, partition=4, topic=user.notifications} 가 0이었다 (2026-08-03T13:17:11Z ~ 2026-08-03T14:17:11Z)
- kafka_consumergroup_lag{consumergroup=notification-processors, partition=5, topic=user.notifications} 가 0이었다 (2026-08-03T13:17:11Z ~ 2026-08-03T14:17:11Z)
- kafka_consumergroup_lag{consumergroup=notification-recovery, partition=0, topic=user.notifications.dlq} 가 0이었다 (2026-08-03T13:17:11Z ~ 2026-08-03T14:17:11Z)
- kafka_consumergroup_lag{consumergroup=notification-recovery, partition=2, topic=user.notifications.dlq} 가 0이었다 (2026-08-03T13:17:11Z ~ 2026-08-03T14:17:11Z)
- kafka_consumergroup_lag{consumergroup=notification-retry-2000, partition=0, topic=chat.messages-retry-2000} 가 0이었다 (2026-08-03T13:17:11Z ~ 2026-08-03T14:17:11Z)
- kafka_consumergroup_lag{consumergroup=notification-retry-4000, partition=0, topic=chat.messages-retry-4000} 가 0이었다 (2026-08-03T13:17:11Z ~ 2026-08-03T14:17:11Z)
- 같은 시각의 다른 후보: INC-1, INC-3, INC-4, INC-5, INC-6, INC-7, INC-8, INC-9, INC-10, INC-11, INC-12, INC-13  (인과 여부는 판단하지 않았다)

## INC-3  chat-service  |  websocket_active_users
- 구간: 2026-08-03T13:17:11Z ~ 2026-08-03T14:17:11Z  (MIMIR · 집계 해상도만큼 흐림)
- websocket_active_users{container=chat-service, namespace=default, pod=chat-service-fdcc7c776-qrbc2} 가 0이었다 (2026-08-03T13:17:11Z ~ 2026-08-03T14:17:11Z)
- 같은 시각의 다른 후보: INC-1, INC-2, INC-4, INC-5, INC-6, INC-7, INC-8, INC-9, INC-10, INC-11, INC-12, INC-13  (인과 여부는 판단하지 않았다)

## INC-4  content-service  |  ERROR/WARN
- 구간: 2026-08-03T13:20:00Z ~ 2026-08-03T13:25:00Z  (LOKI · 집계 해상도만큼 흐림)
- ERROR/WARN 4건 (2026-08-03T13:20:00Z ~ 2026-08-03T13:25:00Z)
- 같은 시각의 다른 후보: INC-1, INC-2, INC-3, INC-5  (인과 여부는 판단하지 않았다)

## INC-5  content-service  |  http post /feeds
- 구간: 2026-08-03T13:20:48.769546Z ~ 2026-08-03T13:20:48.864546Z  (TEMPO · 시각 정확)
- content-service http post /feeds 95ms (error 채널)
- traceId: 6a7095b09dbabfa223fd8e4c12fda927
- 같은 시각의 다른 후보: INC-1, INC-2, INC-3, INC-4  (인과 여부는 판단하지 않았다)

## INC-6  auth-service  |  http post /login
- 구간: 2026-08-03T13:27:19.919322Z ~ 2026-08-03T13:27:23.428322Z  (TEMPO · 시각 정확)
- auth-service http post /login 3,509ms (slow 채널)
- traceId: 6a709737e51278eeebf347141cc5c3f2
- 같은 시각의 다른 후보: INC-1, INC-2, INC-3  (인과 여부는 판단하지 않았다)

## INC-7  auth-service  |  ERROR/WARN
- 구간: 2026-08-03T13:45:00Z ~ 2026-08-03T13:50:00Z  (LOKI · 집계 해상도만큼 흐림)
- ERROR/WARN 1건 (2026-08-03T13:45:00Z ~ 2026-08-03T13:50:00Z)
- 같은 시각의 다른 후보: INC-2, INC-3, INC-8, INC-9  (인과 여부는 판단하지 않았다)

## INC-8  content-service  |  ERROR/WARN
- 구간: 2026-08-03T13:45:00Z ~ 2026-08-03T13:50:00Z  (LOKI · 집계 해상도만큼 흐림)
- ERROR/WARN 3건 (2026-08-03T13:45:00Z ~ 2026-08-03T13:50:00Z)
- 같은 시각의 다른 후보: INC-2, INC-3, INC-7, INC-9  (인과 여부는 판단하지 않았다)

## INC-9  content-service  |  http get /feeds/following
- 구간: 2026-08-03T13:45:03.385679Z ~ 2026-08-03T13:45:06.672679Z  (TEMPO · 시각 정확)
- content-service http get /feeds/following 3,287ms (slow 채널)
- traceId: 6a709b5f358d68fc3c806907ad35c966
- 같은 시각의 다른 후보: INC-2, INC-3, INC-7, INC-8  (인과 여부는 판단하지 않았다)

## INC-10  content-service  |  ERROR/WARN
- 구간: 2026-08-03T14:05:00Z ~ 2026-08-03T14:10:00Z  (LOKI · 집계 해상도만큼 흐림)
- ERROR/WARN 1건 (2026-08-03T14:05:00Z ~ 2026-08-03T14:10:00Z)
- 같은 시각의 다른 후보: INC-2, INC-3, INC-11, INC-12  (인과 여부는 판단하지 않았다)

## INC-11  content-service  |  http get /feeds/scroll
- 구간: 2026-08-03T14:09:25.771400Z ~ 2026-08-03T14:09:25.842400Z  (TEMPO · 시각 정확)
- content-service http get /feeds/scroll 71ms (error 채널)
- traceId: 6a70a115f09975daa14ec1a090053942
- 같은 시각의 다른 후보: INC-2, INC-3, INC-10  (인과 여부는 판단하지 않았다)

## INC-12  auth-service  |  ERROR/WARN
- 구간: 2026-08-03T14:10:00Z ~ 2026-08-03T14:15:00Z  (LOKI · 집계 해상도만큼 흐림)
- ERROR/WARN 4건 (2026-08-03T14:10:00Z ~ 2026-08-03T14:15:00Z)
- 같은 시각의 다른 후보: INC-2, INC-3, INC-10, INC-13  (인과 여부는 판단하지 않았다)

## INC-13  auth-service  |  http post /login
- 구간: 2026-08-03T14:11:36.548284Z ~ 2026-08-03T14:11:39.950284Z  (TEMPO · 시각 정확)
- auth-service http post /login 3,402ms (slow 채널)
- traceId: 6a70a19837babc73e8d2404b21bd15b2
- 같은 시각의 다른 후보: INC-2, INC-3, INC-12  (인과 여부는 판단하지 않았다)

**보정 기록**

- 창을 후보 [INC-7, INC-8, INC-9, INC-10, INC-11, INC-12, INC-13] 의 신호 시각에서 계산했다 (2026-08-03T13:40:00Z ~ 2026-08-03T14:17:11.874303Z)

## 수집 범위 (Coverage)

- **window**: 2026-08-03T13:40:00Z ~ 2026-08-03T14:17:11.874303Z (2231s)
- **trace**: 106,993B / 115 spans (상위 span만)
- **창 안 후보 트레이스**: 10건 / 106,993B
- **logs**: errwarn=21,641B · traceId=7,781B
- **metrics**: 8 수집 / 236,656B, 누락 [sum(rate(http_server_requests_seconds_count{application="content-service", status="401"}[1m]))]
- **context**: 161,622 chars (+ 시스템 프롬프트 576 chars)

### 토큰 축 (개선 지표)

| 단계 | 총 in | chars | 컨텍스트 토큰 |
|---|---:|---:|---:|
| 탐색 | 50,432 | 51,776 | 29,123 |
| 분석 | 108,110 | 162,198 | 86,801 |
| **합계** | **158,542** | | **115,924** |

- **overheadTokens 21,309 tok — 이 회차에 실측**(1자 프롬프트 프로브, 같은 명령·모델·샌드박스).
  호출마다 한 번씩 얹히므로 단계별로 뺀다. **다른 날 상수를 쓰지 않았다 = `█ 실측`.**
- contextTokens (count_tokens API): 측정 안 됨 — 구독 CLI 경로엔 API 키가 없다
- 비용은 **API 환산 추정치**다. 구독 계정으로 호출하므로 실제 청구액이 아니다.

## 수집 실패/누락

- Metric 'sum(rate(http_server_requests_seconds_count{application="content-service", status="401"}[1m]))' returned no series in this window; skipped.

---

## 1. 원인 후보 랭킹 (최대 3개)

1. **auth-service 파드 교체(재시작) 중 다운타임 → content-service의 사용자 조회 실패 → 작성자를 "익명"으로 대체 폴백**
2. **auth-service 정상 기동 구간에서도 응답이 초 단위로 느려(2.7~3.4s) 일부 요청이 폴백/지연으로 흐름** (1번의 선행 징후 또는 부분 기여)
3. **JWT 서명 검증 실패(시크릿 불일치)** — 작성자 이름과 직접 연결할 근거는 없으나 같은 창에서 관측된 인증 계열 이상

---

## 2. 후보별 근거

### 후보 1 — auth-service 다운타임으로 인한 작성자 익명 폴백

**근거**
- content-service ERROR 로그 원문 (14:09:25.8Z):
  `c.e.t.e.u.s.ExternalUserApiClient - [user-fallback] auth 목록 조회 실패 → 4명 익명 대체: userIds=[1, 3, 7, 9]`
  → "작성자 이름이 이상하게 보인다"는 제보와 직접 대응하는 유일한 관측값. 4명의 작성자 이름이 실제 값 대신 익명 값으로 치환됨.
- 같은 traceId `6a70a115f09975daa14ec1a090053942`의 span `http get` (SPAN_KIND_CLIENT, client.name=auth-service):
  - `http.url = http://auth-service:8081/api/external/users?userIds=1,3,7,9`
  - `error = finishConnect(..) failed: Connection refused: auth-service.default.svc.cluster.local/10.43.13.21:8081`
  - `exception = WebClientRequestException`, `status = CLIENT_ERROR`, span status `STATUS_CODE_ERROR`
  - 소요 22.6ms — 타임아웃이 아니라 **즉시 거절(Connection refused)**. 즉 Service VIP는 살아있으나 뒤에 Ready 상태의 파드가 없었다는 신호.
- 그런데도 상위 서버 span `http get /feeds/scroll`은 `status=200, outcome=SUCCESS`로 종료 → **에러가 사용자에게 노출되지 않고 잘못된(익명) 이름으로 200 응답이 나감**. 제보 양상(장애가 아니라 "이름이 이상함")과 일치.
- 다운타임 창이 메트릭으로 뒷받침됨:
  - `up{pod=auth-service-5999bb9f5c-lbpf2, instance=10.42.1.45:8090}` → 13:40:00Z ~ **13:59:45Z**까지만 시계열 존재
  - `up{pod=auth-service-5999bb9f5c-hmgp9, instance=10.42.1.46:8090}` → **14:11:45Z**부터 시계열 시작
  - 즉 **13:59:45Z ~ 14:11:45Z 약 12분간 auth-service 시리즈 결측**, 실패 시각 14:09:25.8Z가 이 구간 한가운데.
  - 파드 이름이 `lbpf2` → `hmgp9`로 바뀜(동일 ReplicaSet `5999bb9f5c`) = 파드 교체.
- 신규 파드 기동 로그가 그 직후 존재: `auth-service-5999bb9f5c-hmgp9`에서 14:10:03Z `BeanPostProcessorChecker` WARN, 14:10:24Z Hibernate WARN, 14:11:05~14:11:06Z `DefaultSecurityFilterChain - Will secure Or [Mvc [pattern='/api/external/**']] ...` → 부팅 시퀀스가 14:10~14:11에 진행됨.
- 복구 정황: 기동 직후 14:11:39.9Z `POST /api/login 200 - 3384ms` (콜드스타트성 지연), 이후 auth-service 호출 실패 로그 없음.

**확신도: 높음**

**반증 데이터**
- 폴백 로그는 **1건(userIds=[1,3,7,9])만** 확보됨. 12분 다운타임 동안 다수 발생했을 것으로 보이나, 수집된 로그로는 영향 범위를 수치화할 수 없음.
- `sum(rate(http_server_requests_seconds_count{application="content-service", status="401"}[1m]))` 메트릭이 **no series로 수집 실패** → content-service 측 에러율 곡선으로 창을 교차 검증하지 못함.
- `up` 시리즈의 결측이 파드 부재 때문인지 스크레이프 타깃 미등록 때문인지 구분할 kube_pod/컨테이너 재시작 지표는 수집되지 않음(다만 파드명 변경 + 부팅 로그가 파드 교체를 강하게 지지).

---

### 후보 2 — auth-service 응답 지연(초 단위)으로 인한 폴백/지연 기여

**근거**
- 13:45:03~13:45:06Z 트레이스 `6a709b5f358d68fc3c806907ad35c966`:
  - auth-service 서버 span `http get /external/users/{userid}/followings` = **2785.8ms** (2026-08-03 22:45:06 KST WARN 로그: `[HTTP-SLOW] GET /api/external/users/1/followings 200 - 2783ms`)
  - content-service 서버 span `http get /feeds/following` = **3287ms** (`[HTTP-SLOW] GET /api/feeds/following 200 - 3287ms`)
  - 같은 트레이스의 JDBC `connection` span은 3275.4ms인데 그 안의 개별 query 합은 수십 ms 수준 → **DB가 아니라 auth-service 대기 시간이 지배적**.
- 호출 그래프 집약값도 동일: `content-service --service--> auth-service 5회 최대 2785.8ms`.
- 폴백 코드 경로(`ExternalUserApiClient`)가 존재하는 이상, 클라이언트 타임아웃이 이 지연보다 짧으면 동일하게 익명 대체가 발생할 수 있음.

**확신도: 중간** (지연은 실측 확인, 지연→폴백 발생은 미확인)

**반증 데이터**
- 이 느린 호출들은 모두 `status=200, outcome=SUCCESS, exception=none`으로 **성공**했고, 해당 트레이스에는 `[user-fallback]` 로그가 없음.
- 실제 폴백이 찍힌 트레이스의 실패 원인은 지연이 아니라 22.6ms 만의 `Connection refused`였음 → 타임아웃 가설과 배치됨.
- WebClient 타임아웃 설정값을 확인할 데이터가 없음 → **데이터 부족**.

---

### 후보 3 — JWT 서명 검증 실패(시크릿 불일치)

**근거**
- content-service WARN 로그 (13:46:09.0Z, traceId=`6a709ba13e460c51fd7ef4e80a4c9a21`):
  `c.e.t.app.auth.token.JwtParser - [auth-jwt] JWT 서명 검증 실패 — 위조 또는 시크릿 불일치`
  이어서 `[HTTP] GET /api/feeds/following 401 - 6ms`
- auth-service가 재배포된 정황(후보 1)이 있어, 서명 시크릿 변경 가능성을 배제할 수 없음.

**확신도: 낮음**

**반증 데이터**
- 시각이 배치됨: JWT 실패는 **13:46:09Z**로, 파드 교체 창(13:59:45~14:11:45Z)보다 **약 14분 앞섬**. 재배포로 인한 시크릿 변경으로는 설명되지 않음.
- 결과가 **401**이므로 피드가 아예 안 나오는 증상이지, "이름이 이상하게 나온다"(200 + 잘못된 이름)와 증상이 다름.
- 해당 traceId의 트레이스는 수집 목록에 없어 교차 확인 불가. content-service 401 메트릭도 수집 실패(no series) → 이 401이 1건인지 지속적인지 **데이터 부족**.

---

### 참고: 배제한 방향 (반증이 명확한 것)

- **DB/커넥션 풀 포화**: `hikaricp_connections_active`, `hikaricp_connections_pending` 모두 전 서비스·전 구간 **0으로 상수**. 느린 `connection` span(3275.4ms)도 `acquired` 이벤트가 시작 1.7ms 뒤에 찍혀 풀 대기가 아님.
- **인프라 다운**: `up{job=redis|kafka|mongodb|node-infra}` 전 구간 1, `mongodb_up`=1, `kafka_brokers`=1, `kafka_consumergroup_lag` 전 구간 0/-1로 변화 없음. Redis 명령 span 최대 3.4ms.
- **GC/메모리 압박**: `rate(jvm_gc_pause_seconds_sum[5m])` 최대 3e-4초/초 수준, content-service는 대부분 0.

---

## 3. 권장 다음 조치

1. **auth-service 파드 교체 사유 확인 (최우선)**
   - `kubectl describe pod auth-service-5999bb9f5c-hmgp9` 및 `kubectl get events -n default --sort-by=.lastTimestamp | grep auth-service` → 13:59~14:11Z 구간의 Killing/OOMKilled/Evicted/Unhealthy 이벤트 확인.
   - `kubectl logs auth-service-5999bb9f5c-lbpf2 --previous` (남아있다면) 로 종료 직전 로그 확보.
   - `kubectl rollout history deploy/auth-service` 로 의도된 배포였는지 판별.
2. **가용성 갭 제거**
   - auth-service Deployment의 `replicas`(현재 관측상 사실상 1개만 Ready), `maxUnavailable=0` 롤링 전략, `readinessProbe`/`startupProbe`, PodDisruptionBudget 설정 확인. 기동에 ~60초(14:10:03→14:11:06 부팅 로그) 걸리므로 startupProbe 여유와 무중단 배포 설정이 필수.
3. **폴백 영향 범위 산정 (증거 보강)**
   - Loki: `{namespace="default", container="content-service"} |= "user-fallback"` 를 13:55~14:15Z로 조회해 발생 건수와 영향 userIds 집계.
   - Loki: `|= "Connection refused" |= "auth-service"` 로 실패 구간의 시작/종료 시각 확정.
4. **수집 실패한 메트릭 재수집**
   - content-service `http_server_requests_seconds_count`를 status 라벨 없이 먼저 조회해 라벨명/값(`status` vs `outcome`)을 확인한 뒤 401·5xx 곡선 재생성. 401 발생이 일회성인지 지속인지 판별해야 후보 3을 확정/기각 가능.
5. **폴백 동작 자체를 재검토 (재발 시 사용자 영향 차단)**
   - `ExternalUserApiClient`에서 auth 조회 실패 시 익명 대체 후 **200으로 응답**하는 정책을 검토: (a) 폴백 결과를 Redis에 캐싱하지 않는지 확인(캐싱 시 auth 복구 후에도 잘못된 이름이 TTL 동안 지속됨 — 실패 트레이스에는 `SET`이 없었으나 코드로 확정 필요), (b) 응답에 degraded 플래그를 실어 클라이언트가 이름을 숨기도록, (c) 실패 시 짧은 재시도.
6. **지연 대응**
   - `GET /api/external/users/{userId}/followings`가 2.78초 걸린 원인 확인(auth-service 내부 span/쿼리 추적). 아울러 content-service WebClient의 connect/response timeout과 서킷브레이커 설정값을 확인해 auth 장애가 피드 응답 시간(3.29초)으로 전파되지 않게 조정.

---

## 관측 증거 (Evidence)

> 이 조사가 실제로 본 값이다. 원본 응답 전체는 `reports/raw/scan-1785764400-*.json`에 있다.

### 호출 그래프 (트레이스에서 추출)

```
content-service --db--> redis  13회  최대 3.4ms  [LRANGE, GET, SET, PEXPIRE, DEL, RPUSH, INFO]
content-service --jdbc--> mysql/content (HikariPool-1)  67회  최대 3275.4ms
    events: acquired, commit
content-service --service--> auth-service  5회  최대 2785.8ms
    error: WebClientRequestException
    error: finishConnect(..) failed: Connection refused: auth-service.default.svc.cluster.local/10.43.13.21:8081
```

### span (duration 상위 15 / 전체 115)

| ms | service | span | 시작 |
|---:|---|---|---|
| 3402.16 | auth-service | `http post /login` | 2026-08-03T14:11:36.548284Z |
| 3295.33 | auth-service | `secured request` | 2026-08-03T14:11:36.639764Z |
| 3287.80 | content-service | `http get /feeds/following` | 2026-08-03T13:45:03.385679Z |
| 3285.45 | content-service | `secured request` | 2026-08-03T13:45:03.386031Z |
| 3275.41 | content-service | `connection` | 2026-08-03T13:45:03.395600Z |
| 2785.80 | auth-service | `http get /external/users/{userid}/followings` | 2026-08-03T13:45:03.420115Z |
| 2779.97 | auth-service | `secured request` | 2026-08-03T13:45:03.422723Z |
| 2718.79 | content-service | `http get` | 2026-08-03T13:45:03.411112Z |
| 343.96 | content-service | `http get` | 2026-08-03T13:45:06.266283Z |
| 308.40 | auth-service | `http get /external/users` | 2026-08-03T13:45:06.302482Z |
| 303.12 | auth-service | `secured request` | 2026-08-03T13:45:06.306325Z |
| 128.09 | content-service | `http get /feeds/following` | 2026-08-03T13:45:07.037929Z |
| 126.49 | content-service | `secured request` | 2026-08-03T13:45:07.038350Z |
| 116.62 | content-service | `connection` | 2026-08-03T13:45:07.047984Z |
| 71.67 | content-service | `http get /feeds/scroll` | 2026-08-03T14:09:25.771400Z |

### 로그 원문 (60 / 전체 91줄)

전체가 상한을 넘어 ERROR/WARN 줄을 우선 발췌했다. 나머지는 원본 파일에 있다.

```
2026-08-03T13:45:06.205745656Z  [auth-service]  [2m2026-08-03 22:45:06[0;39m [2m[http-nio-8081-exec-4][0;39m [33m WARN [traceId=6a709b5f358d68fc3c806907ad35c966,spanId=e322665cb1916ba2,userId=NONE][0;39m [36mc.e.t.a.c.f.RequestLoggingFilter[0;39m [2m-[0;39m [HTTP-SLOW] GET /api/external/users/1/followings 200 - 2783ms
2026-08-03T13:45:06.205745656Z  [auth-service]  [2m2026-08-03 22:45:06[0;39m [2m[http-nio-8081-exec-4][0;39m [33m WARN [traceId=6a709b5f358d68fc3c806907ad35c966,spanId=e322665cb1916ba2,userId=NONE][0;39m [36mc.e.t.a.c.f.RequestLoggingFilter[0;39m [2m-[0;39m [HTTP-SLOW] GET /api/external/users/1/followings 200 - 2783ms
2026-08-03T13:45:06.672266371Z  [content-service]  2026-08-03 22:45:06.672 [http-nio-8082-exec-3]  WARN [traceId=6a709b5f358d68fc3c806907ad35c966,spanId=3c806907ad35c966,userId=NONE] c.e.t.a.c.f.RequestLoggingFilter - [HTTP-SLOW] GET /api/feeds/following 200 - 3287ms
2026-08-03T13:45:06.672266371Z  [content-service]  2026-08-03 22:45:06.672 [http-nio-8082-exec-3]  WARN [traceId=6a709b5f358d68fc3c806907ad35c966,spanId=3c806907ad35c966,userId=NONE] c.e.t.a.c.f.RequestLoggingFilter - [HTTP-SLOW] GET /api/feeds/following 200 - 3287ms
2026-08-03T13:46:09.005784816Z  [content-service]  2026-08-03 22:46:09.005 [http-nio-8082-exec-1]  WARN [traceId=6a709ba13e460c51fd7ef4e80a4c9a21,spanId=23ac3cbe583b1fc7,userId=NONE] c.e.t.app.auth.token.JwtParser - [auth-jwt] JWT 서명 검증 실패 — 위조 또는 시크릿 불일치
2026-08-03T13:46:09.009200602Z  [content-service]  2026-08-03 22:46:09.008 [http-nio-8082-exec-1]  WARN [traceId=6a709ba13e460c51fd7ef4e80a4c9a21,spanId=fd7ef4e80a4c9a21,userId=NONE] c.e.t.a.c.f.RequestLoggingFilter - [HTTP] GET /api/feeds/following 401 - 6ms
2026-08-03T14:09:25.811679551Z  [content-service]  2026-08-03 23:09:25.801 [reactor-http-epoll-3] ERROR [traceId=6a70a115f09975daa14ec1a090053942,spanId=e480fa065ecc7242,userId=NONE] c.e.t.e.u.s.ExternalUserApiClient - [user-fallback] auth 목록 조회 실패 → 4명 익명 대체: userIds=[1, 3, 7, 9]
2026-08-03T14:09:25.811679551Z  [content-service]  2026-08-03 23:09:25.801 [reactor-http-epoll-3] ERROR [traceId=6a70a115f09975daa14ec1a090053942,spanId=e480fa065ecc7242,userId=NONE] c.e.t.e.u.s.ExternalUserApiClient - [user-fallback] auth 목록 조회 실패 → 4명 익명 대체: userIds=[1, 3, 7, 9]
2026-08-03T14:09:25.811728871Z  [content-service]  org.springframework.web.reactive.function.client.WebClientRequestException: finishConnect(..) failed: Connection refused: auth-service.default.svc.cluster.local/10.43.13.21:8081
2026-08-03T14:09:25.811734864Z  [content-service]  at org.springframework.web.reactive.function.client.ExchangeFunctions$DefaultExchangeFunction.lambda$wrapException$9(ExchangeFunctions.java:137)
2026-08-03T14:09:25.811740045Z  [content-service]  Suppressed: reactor.core.publisher.FluxOnAssembly$OnAssemblyException:
2026-08-03T14:09:25.811755131Z  [content-service]  at org.springframework.web.reactive.function.client.ExchangeFunctions$DefaultExchangeFunction.lambda$wrapException$9(ExchangeFunctions.java:137)
2026-08-03T14:09:25.811758905Z  [content-service]  at reactor.core.publisher.MonoErrorSupplied.subscribe(MonoErrorSupplied.java:55)
2026-08-03T14:09:25.811762751Z  [content-service]  at reactor.core.publisher.Mono.subscribe(Mono.java:4576)
2026-08-03T14:09:25.811766834Z  [content-service]  at reactor.core.publisher.FluxOnErrorResume$ResumeSubscriber.onError(FluxOnErrorResume.java:103)
2026-08-03T14:09:25.811770533Z  [content-service]  at reactor.core.publisher.FluxPeek$PeekSubscriber.onError(FluxPeek.java:222)
2026-08-03T14:09:25.811774535Z  [content-service]  at reactor.core.publisher.FluxPeek$PeekSubscriber.onError(FluxPeek.java:222)
2026-08-03T14:09:25.811778080Z  [content-service]  at reactor.core.publisher.FluxPeek$PeekSubscriber.onError(FluxPeek.java:222)
2026-08-03T14:09:25.811781767Z  [content-service]  at reactor.core.publisher.MonoNext$NextSubscriber.onError(MonoNext.java:93)
2026-08-03T14:09:25.811785369Z  [content-service]  at reactor.core.publisher.MonoFlatMapMany$FlatMapManyMain.onError(MonoFlatMapMany.java:205)
2026-08-03T14:09:25.811789021Z  [content-service]  at reactor.core.publisher.SerializedSubscriber.onError(SerializedSubscriber.java:124)
2026-08-03T14:09:25.811792754Z  [content-service]  at reactor.core.publisher.FluxRetryWhen$RetryWhenMainSubscriber.whenError(FluxRetryWhen.java:229)
2026-08-03T14:09:25.811796301Z  [content-service]  at reactor.core.publisher.FluxRetryWhen$RetryWhenOtherSubscriber.onError(FluxRetryWhen.java:279)
2026-08-03T14:09:25.811799898Z  [content-service]  at reactor.core.publisher.FluxContextWrite$ContextWriteSubscriber.onError(FluxContextWrite.java:121)
2026-08-03T14:09:25.811803572Z  [content-service]  at reactor.core.publisher.FluxConcatMapNoPrefetch$FluxConcatMapNoPrefetchSubscriber.maybeOnError(FluxConcatMapNoPrefetch.java:327)
2026-08-03T14:09:25.811807617Z  [content-service]  at reactor.core.publisher.FluxConcatMapNoPrefetch$FluxConcatMapNoPrefetchSubscriber.onNext(FluxConcatMapNoPrefetch.java:212)
2026-08-03T14:09:25.811811380Z  [content-service]  at reactor.core.publisher.FluxContextWrite$ContextWriteSubscriber.onNext(FluxContextWrite.java:107)
2026-08-03T14:09:25.811815325Z  [content-service]  at reactor.core.publisher.SinkManyEmitterProcessor.drain(SinkManyEmitterProcessor.java:476)
2026-08-03T14:09:25.811818501Z  [content-service]  at reactor.core.publisher.SinkManyEmitterProcessor$EmitterInner.drainParent(SinkManyEmitterProcessor.java:620)
2026-08-03T14:09:25.811821988Z  [content-service]  at reactor.core.publisher.FluxPublish$PubSubInner.request(FluxPublish.java:874)
2026-08-03T14:09:25.811840630Z  [content-service]  at reactor.core.publisher.FluxContextWrite$ContextWriteSubscriber.request(FluxContextWrite.java:136)
2026-08-03T14:09:25.811843689Z  [content-service]  at reactor.core.publisher.FluxConcatMapNoPrefetch$FluxConcatMapNoPrefetchSubscriber.request(FluxConcatMapNoPrefetch.java:337)
2026-08-03T14:09:25.811846121Z  [content-service]  at reactor.core.publisher.FluxContextWrite$ContextWriteSubscriber.request(FluxContextWrite.java:136)
2026-08-03T14:09:25.811848529Z  [content-service]  at reactor.core.publisher.Operators$DeferredSubscription.request(Operators.java:1743)
2026-08-03T14:09:25.811850871Z  [content-service]  at reactor.core.publisher.FluxRetryWhen$RetryWhenMainSubscriber.onError(FluxRetryWhen.java:196)
2026-08-03T14:09:25.811853322Z  [content-service]  at reactor.core.publisher.MonoCreate$DefaultMonoSink.error(MonoCreate.java:205)
2026-08-03T14:09:25.811855816Z  [content-service]  at reactor.netty.http.client.HttpClientConnect$MonoHttpConnect$ClientTransportSubscriber.onError(HttpClientConnect.java:318)
2026-08-03T14:09:25.811858371Z  [content-service]  at reactor.core.publisher.MonoCreate$DefaultMonoSink.error(MonoCreate.java:205)
2026-08-03T14:09:25.811860832Z  [content-service]  at reactor.netty.resources.DefaultPooledConnectionProvider$DisposableAcquire.onError(DefaultPooledConnectionProvider.java:174)
2026-08-03T14:09:25.811866661Z  [content-service]  at reactor.netty.internal.shaded.reactor.pool.AbstractPool$Borrower.fail(AbstractPool.java:479)
2026-08-03T14:09:25.811868930Z  [content-service]  at reactor.netty.internal.shaded.reactor.pool.SimpleDequePool.lambda$drainLoop$9(SimpleDequePool.java:436)
2026-08-03T14:09:25.811871102Z  [content-service]  at reactor.core.publisher.FluxDoOnEach$DoOnEachSubscriber.onError(FluxDoOnEach.java:186)
2026-08-03T14:09:25.811873139Z  [content-service]  at reactor.core.publisher.MonoCreate$DefaultMonoSink.error(MonoCreate.java:205)
2026-08-03T14:09:25.811893344Z  [content-service]  at reactor.netty.resources.DefaultPooledConnectionProvider$PooledConnectionAllocator$PooledConnectionInitializer.onError(DefaultPooledConnectionProvider.java:593)
2026-08-03T14:09:25.811896091Z  [content-service]  at reactor.core.publisher.MonoFlatMap$FlatMapMain.secondError(MonoFlatMap.java:241)
2026-08-03T14:09:25.811898234Z  [content-service]  at reactor.core.publisher.MonoFlatMap$FlatMapInner.onError(MonoFlatMap.java:315)
2026-08-03T14:09:25.811900234Z  [content-service]  at reactor.core.publisher.FluxOnErrorResume$ResumeSubscriber.onError(FluxOnErrorResume.java:106)
2026-08-03T14:09:25.811902519Z  [content-service]  at reactor.core.publisher.Operators.error(Operators.java:198)
2026-08-03T14:09:25.811904613Z  [content-service]  at reactor.core.publisher.MonoError.subscribe(MonoError.java:53)
2026-08-03T14:09:25.811907023Z  [content-service]  at reactor.core.publisher.Mono.subscribe(Mono.java:4576)
2026-08-03T14:09:25.811968440Z  [content-service]  Caused by: io.netty.channel.AbstractChannel$AnnotatedConnectException: finishConnect(..) failed: Connection refused: auth-service.default.svc.cluster.local/10.43.13.21:8081
2026-08-03T14:09:25.811971116Z  [content-service]  Caused by: java.net.ConnectException: finishConnect(..) failed: Connection refused
2026-08-03T14:09:25.811973604Z  [content-service]  at io.netty.channel.unix.Errors.newConnectException0(Errors.java:166)
2026-08-03T14:10:03.942128320Z  [auth-service]  [2m2026-08-03 23:10:03[0;39m [2m[main][0;39m [33m WARN [traceId=NONE,spanId=NONE,userId=NONE][0;39m [36mo.s.c.s.PostProcessorRegistrationDelegate$BeanPostProcessorChecker[0;39m [2m-[0;39m Bean 'org.springframework.ws.config.annotation.DelegatingWsConfiguration' of type [org.springframework.ws.config.annotation.DelegatingWsConfiguration$$SpringCGLIB$$0] is not eligible for getting processed by all BeanPostProcessors (for example: not eligible for auto-proxying). The currently created BeanPostProcessor [annotationActionEndpointMapping] is declared through a non-static factory method on that class; consider declaring it as static instead.
2026-08-03T14:10:24.005173154Z  [auth-service]  [2m2026-08-03 23:10:24[0;39m [2m[main][0;39m [33m WARN [traceId=NONE,spanId=NONE,userId=NONE][0;39m [36morg.hibernate.orm.deprecation[0;39m [2m-[0;39m HHH90000025: MySQLDialect does not need to be specified explicitly using 'hibernate.dialect' (remove the property setting and it will be selected by default)
2026-08-03T14:10:59.341741853Z  [auth-service]  [2m2026-08-03 23:10:59[0;39m [2m[main][0;39m [33m WARN [traceId=NONE,spanId=NONE,userId=NONE][0;39m [36mo.s.b.a.o.j.JpaBaseConfiguration$JpaWebConfiguration[0;39m [2m-[0;39m spring.jpa.open-in-view is enabled by default. Therefore, database queries may be performed during view rendering. Explicitly configure spring.jpa.open-in-view to disable this warning
2026-08-03T14:11:05.894764645Z  [auth-service]  [2m2026-08-03 23:11:05[0;39m [2m[main][0;39m [32m INFO [traceId=NONE,spanId=NONE,userId=NONE][0;39m [36mo.s.s.web.DefaultSecurityFilterChain[0;39m [2m-[0;39m Will secure Or [Mvc [pattern='/api/external/**']] with [org.springframework.security.web.session.DisableEncodeUrlFilter@2c0b29cf, org.springframework.security.web.context.request.async.WebAsyncManagerIntegrationFilter@374d09fa, org.springframework.security.web.context.SecurityContextHolderFilter@5f5d5739, org.springframework.security.web.header.HeaderWriterFilter@2300d6fd, org.springframework.web.filter.CorsFilter@4edea211, org.springframework.security.web.authentication.logout.LogoutFilter@4fe256c0, com.example.toyauth.app.common.filter.ExternalAuthenticationFilter@75044df3, org.springframework.security.web.savedrequest.RequestCacheAwareFilter@1b2af5ed, org.springframework.security.web.servletapi.SecurityContextHolderAwareRequestFilter@13e4acf6, org.springframework.security.web.authentication.AnonymousAuthenticationFilter@7dca6126, org.springframework.security.web.session.SessionManagementFilter@3f6edaf8, org.springframework.security.web.access.ExceptionTranslationFilter@2481db83, org.springframework.security.web.access.intercept.AuthorizationFilter@469f6527]
2026-08-03T14:11:06.202313376Z  [auth-service]  [2m2026-08-03 23:11:06[0;39m [2m[main][0;39m [32m INFO [traceId=NONE,spanId=NONE,userId=NONE][0;39m [36mo.s.s.web.DefaultSecurityFilterChain[0;39m [2m-[0;39m Will secure any request with [org.springframework.security.web.session.DisableEncodeUrlFilter@4ac73165, org.springframework.security.web.context.request.async.WebAsyncManagerIntegrationFilter@25cc6b0, org.springframework.security.web.context.SecurityContextHolderFilter@4237d6e4, org.springframework.security.web.header.HeaderWriterFilter@396feeb5, org.springframework.web.filter.CorsFilter@756d28cb, org.springframework.security.web.authentication.logout.LogoutFilter@7d8e1c87, org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestRedirectFilter@139be871, org.springframework.security.oauth2.client.web.OAuth2LoginAuthenticationFilter@c14943c, com.example.toyauth.app.common.filter.JwtFilter@2ca6937, org.springframework.security.web.authentication.ui.DefaultLoginPageGeneratingFilter@37eb27a2, org.springframework.security.web.authentication.ui.DefaultLogoutPageGeneratingFilter@8466428, org.springframework.security.web.savedrequest.RequestCacheAwareFilter@754f951a, org.springframework.security.web.servletapi.SecurityContextHolderAwareRequestFilter@3393d740, org.springframework.security.web.authentication.AnonymousAuthenticationFilter@7b79305c, org.springframework.security.web.session.SessionManagementFilter@4228da9a, org.springframework.security.web.access.ExceptionTranslationFilter@64ea74ac]
2026-08-03T14:11:39.940714180Z  [auth-service]  [2m2026-08-03 23:11:39[0;39m [2m[http-nio-8081-exec-1][0;39m [33m WARN [traceId=6a70a19837babc73e8d2404b21bd15b2,spanId=e8d2404b21bd15b2,userId=NONE][0;39m [36mc.e.t.a.c.f.RequestLoggingFilter[0;39m [2m-[0;39m [HTTP-SLOW] POST /api/login 200 - 3384ms
2026-08-03T14:11:39.940714180Z  [auth-service]  [2m2026-08-03 23:11:39[0;39m [2m[http-nio-8081-exec-1][0;39m [33m WARN [traceId=6a70a19837babc73e8d2404b21bd15b2,spanId=e8d2404b21bd15b2,userId=NONE][0;39m [36mc.e.t.a.c.f.RequestLoggingFilter[0;39m [2m-[0;39m [HTTP-SLOW] POST /api/login 200 - 3384ms
```

### 메트릭 시계열

| 쿼리 | series | 점 | min | max | last | 값이 0이던 구간 |
|---|---|---:|---:|---:|---:|---|
| `hikaricp_connections_active` | `{__name__=hikaricp_connections_active, application=auth-service, cluster=yogurtte-k3s-prod, container=auth-service, instance=10.42.1.45:8090, job=auth-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=auth-service-5999bb9f5c-lbpf2, pool=HikariPool-1, service=auth-service}` | 80 | 0 | 0 | 0 | **2026-08-03T13:40:00Z ~ 2026-08-03T13:59:45Z** |
| `hikaricp_connections_active` | `{__name__=hikaricp_connections_active, application=auth-service, cluster=yogurtte-k3s-prod, container=auth-service, instance=10.42.1.46:8090, job=auth-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=auth-service-5999bb9f5c-hmgp9, pool=HikariPool-1, service=auth-service}` | 22 | 0 | 0 | 0 | **2026-08-03T14:11:45Z ~ 2026-08-03T14:17:00Z** |
| `hikaricp_connections_active` | `{__name__=hikaricp_connections_active, application=chat-service, cluster=yogurtte-k3s-prod, container=chat-service, instance=10.42.3.43:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-fdcc7c776-qrbc2, pool=HikariPool-1}` | 149 | 0 | 0 | 0 | **2026-08-03T13:40:00Z ~ 2026-08-03T14:17:00Z** |
| `hikaricp_connections_active` | `{__name__=hikaricp_connections_active, application=content-service, cluster=yogurtte-k3s-prod, container=content-service, instance=10.42.1.43:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-6995bb7d94-h2f6n, pool=HikariPool-1}` | 149 | 0 | 0 | 0 | **2026-08-03T13:40:00Z ~ 2026-08-03T14:17:00Z** |
| `hikaricp_connections_active` | `{__name__=hikaricp_connections_active, application=content-service, cluster=yogurtte-k3s-prod, container=content-service, instance=10.42.3.42:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-6995bb7d94-nq9l2, pool=HikariPool-1}` | 149 | 0 | 0 | 0 | **2026-08-03T13:40:00Z ~ 2026-08-03T14:17:00Z** |
| `hikaricp_connections_pending` | `{__name__=hikaricp_connections_pending, application=auth-service, cluster=yogurtte-k3s-prod, container=auth-service, instance=10.42.1.45:8090, job=auth-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=auth-service-5999bb9f5c-lbpf2, pool=HikariPool-1, service=auth-service}` | 80 | 0 | 0 | 0 | **2026-08-03T13:40:00Z ~ 2026-08-03T13:59:45Z** |
| `hikaricp_connections_pending` | `{__name__=hikaricp_connections_pending, application=auth-service, cluster=yogurtte-k3s-prod, container=auth-service, instance=10.42.1.46:8090, job=auth-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=auth-service-5999bb9f5c-hmgp9, pool=HikariPool-1, service=auth-service}` | 22 | 0 | 0 | 0 | **2026-08-03T14:11:45Z ~ 2026-08-03T14:17:00Z** |
| `hikaricp_connections_pending` | `{__name__=hikaricp_connections_pending, application=chat-service, cluster=yogurtte-k3s-prod, container=chat-service, instance=10.42.3.43:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-fdcc7c776-qrbc2, pool=HikariPool-1}` | 149 | 0 | 0 | 0 | **2026-08-03T13:40:00Z ~ 2026-08-03T14:17:00Z** |
| `hikaricp_connections_pending` | `{__name__=hikaricp_connections_pending, application=content-service, cluster=yogurtte-k3s-prod, container=content-service, instance=10.42.1.43:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-6995bb7d94-h2f6n, pool=HikariPool-1}` | 149 | 0 | 0 | 0 | **2026-08-03T13:40:00Z ~ 2026-08-03T14:17:00Z** |
| `hikaricp_connections_pending` | `{__name__=hikaricp_connections_pending, application=content-service, cluster=yogurtte-k3s-prod, container=content-service, instance=10.42.3.42:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-6995bb7d94-nq9l2, pool=HikariPool-1}` | 149 | 0 | 0 | 0 | **2026-08-03T13:40:00Z ~ 2026-08-03T14:17:00Z** |
| `rate(jvm_gc_pause_seconds_sum[5m])` | `{action=end of major GC, application=chat-service, cause=Allocation Failure, cluster=yogurtte-k3s-prod, container=chat-service, gc=MarkSweepCompact, instance=10.42.3.43:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-fdcc7c776-qrbc2}` | 149 | 0 | 0 | 0 | **2026-08-03T13:40:00Z ~ 2026-08-03T14:17:00Z** |
| `rate(jvm_gc_pause_seconds_sum[5m])` | `{action=end of minor GC, application=auth-service, cause=G1 Evacuation Pause, cluster=yogurtte-k3s-prod, container=auth-service, gc=G1 Young Generation, instance=10.42.1.45:8090, job=auth-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=auth-service-5999bb9f5c-lbpf2, service=auth-service}` | 92 | 0 | 0.000 | 0 | **2026-08-03T13:41:00Z ~ 2026-08-03T13:55:45Z, 2026-08-03T14:00:00Z ~ 2026-08-03T14:02:45Z** |
| `rate(jvm_gc_pause_seconds_sum[5m])` | `{action=end of minor GC, application=auth-service, cause=G1 Evacuation Pause, cluster=yogurtte-k3s-prod, container=auth-service, gc=G1 Young Generation, instance=10.42.1.46:8090, job=auth-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=auth-service-5999bb9f5c-hmgp9, service=auth-service}` | 18 | 0 | 0 | 0 | **2026-08-03T14:12:45Z ~ 2026-08-03T14:17:00Z** |
| `rate(jvm_gc_pause_seconds_sum[5m])` | `{action=end of minor GC, application=chat-service, cause=Allocation Failure, cluster=yogurtte-k3s-prod, container=chat-service, gc=Copy, instance=10.42.3.43:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-fdcc7c776-qrbc2}` | 149 | 0.000 | 0.000 | 0.000 | — |
| `rate(jvm_gc_pause_seconds_sum[5m])` | `{action=end of minor GC, application=content-service, cause=Allocation Failure, cluster=yogurtte-k3s-prod, container=content-service, gc=Copy, instance=10.42.1.43:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-6995bb7d94-h2f6n}` | 149 | 0 | 0.000 | 0.000 | **2026-08-03T13:40:00Z ~ 2026-08-03T13:44:45Z, 2026-08-03T13:49:00Z ~ 2026-08-03T13:58:45Z, 2026-08-03T14:03:00Z ~ 2026-08-03T14:13:45Z** |
| `rate(jvm_gc_pause_seconds_sum[5m])` | `{action=end of minor GC, application=content-service, cause=Allocation Failure, cluster=yogurtte-k3s-prod, container=content-service, gc=Copy, instance=10.42.3.42:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-6995bb7d94-nq9l2}` | 149 | 0 | 0.000 | 0 | **2026-08-03T13:40:45Z ~ 2026-08-03T13:50:30Z, 2026-08-03T13:54:45Z ~ 2026-08-03T14:05:30Z, 2026-08-03T14:09:45Z ~ 2026-08-03T14:17:00Z** |
| `up` | `{__name__=up, app=node-exporter, cluster=yogurtte-k3s-prod, component=metrics, container=node-exporter, instance=ip-172-31-40-241, job=integrations/node_exporter, k8s_cluster_name=yogurtte-k3s-prod, namespace=monitoring, pod=grafana-k8s-monitoring-node-exporter-cph5l, source=kubernetes, workload=DaemonSet/grafana-k8s-monitoring-node-exporter}` | 149 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, app=node-exporter, cluster=yogurtte-k3s-prod, component=metrics, container=node-exporter, instance=ip-172-31-45-39, job=integrations/node_exporter, k8s_cluster_name=yogurtte-k3s-prod, namespace=monitoring, pod=grafana-k8s-monitoring-node-exporter-9kvqt, source=kubernetes, workload=DaemonSet/grafana-k8s-monitoring-node-exporter}` | 149 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, cluster=yogurtte-k3s-prod, container=auth-service, instance=10.42.1.45:8090, job=auth-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=auth-service-5999bb9f5c-lbpf2}` | 80 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, cluster=yogurtte-k3s-prod, container=auth-service, instance=10.42.1.46:8090, job=auth-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=auth-service-5999bb9f5c-hmgp9}` | 22 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, cluster=yogurtte-k3s-prod, container=chat-service, instance=10.42.3.43:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-fdcc7c776-qrbc2}` | 149 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, cluster=yogurtte-k3s-prod, container=content-service, instance=10.42.1.43:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-6995bb7d94-h2f6n}` | 149 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, cluster=yogurtte-k3s-prod, container=content-service, instance=10.42.3.42:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-6995bb7d94-nq9l2}` | 149 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, cluster=yogurtte-k3s-prod, instance=10.42.1.248:8080, job=integrations/kubernetes/kube-state-metrics, k8s_cluster_name=yogurtte-k3s-prod, source=kubernetes}` | 149 | 1 | 1 | 1 | — |
| `mongodb_up` | `{__name__=mongodb_up, cluster=yogurtte-k3s-prod, instance=infra-server, job=mongodb, k8s_cluster_name=yogurtte-k3s-prod}` | 149 | 1 | 1 | 1 | — |
| `kafka_brokers` | `{__name__=kafka_brokers, cluster=yogurtte-k3s-prod, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod}` | 149 | 1 | 1 | 1 | — |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=chat-service-fcm-tokens, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=0, topic=user.fcm-tokens}` | 149 | 0 | 0 | 0 | **2026-08-03T13:40:00Z ~ 2026-08-03T14:17:00Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=chat-service-fcm-tokens, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=1, topic=user.fcm-tokens}` | 149 | 0 | 0 | 0 | **2026-08-03T13:40:00Z ~ 2026-08-03T14:17:00Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=chat-service-fcm-tokens, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=2, topic=user.fcm-tokens}` | 149 | 0 | 0 | 0 | **2026-08-03T13:40:00Z ~ 2026-08-03T14:17:00Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=chat-service-notification-settings, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=0, topic=user.notification-settings}` | 149 | 0 | 0 | 0 | **2026-08-03T13:40:00Z ~ 2026-08-03T14:17:00Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=chat-service-notification-settings, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=1, topic=user.notification-settings}` | 149 | 0 | 0 | 0 | **2026-08-03T13:40:00Z ~ 2026-08-03T14:17:00Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=chat-service-notification-settings, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=2, topic=user.notification-settings}` | 149 | 0 | 0 | 0 | **2026-08-03T13:40:00Z ~ 2026-08-03T14:17:00Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=db-writer, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=0, topic=chat.messages}` | 149 | 0 | 0 | 0 | **2026-08-03T13:40:00Z ~ 2026-08-03T14:17:00Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=db-writer, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=1, topic=chat.messages}` | 149 | 0 | 0 | 0 | **2026-08-03T13:40:00Z ~ 2026-08-03T14:17:00Z** |
| `websocket_active_users` | `{__name__=websocket_active_users, application=chat-service, cluster=yogurtte-k3s-prod, container=chat-service, instance=10.42.3.43:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-fdcc7c776-qrbc2}` | 149 | 0 | 0 | 0 | **2026-08-03T13:40:00Z ~ 2026-08-03T14:17:00Z** |

값이 0이던 구간은 굵게 표시했다 — 프로세스가 사라져 시계열이 꺾인 것이 유일한 신호인 장애가 있다.

