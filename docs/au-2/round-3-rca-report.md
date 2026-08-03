# RCA Report — `scan-1785762600`

| 항목 | 값 |
|---|---|
| 모드 | rca |
| 질문 | 최근 1시간 안에 로그인이 안 된다는 문의가 몰렸다. 원인을 조사해줘 |
| 시각 | 2026-08-03T13:51:33.395356Z |
| provider | claude-cli |
| model | `claude-opus-5` · turns 1 |
| prompt | `./prompts/system-prompt.md` |
| tokens | in 116627 (cacheRead 18,133 · cacheCreate 98,492) / out 6485 · cost $1.2276 |
| elapsed | total 110708ms (tempo 3085 · loki 375 · mimir 680 · assemble 27 · llm 100583) |

## 탐색 (Triage)

| 항목 | 값 |
|---|---|
| 시간창 해석 | 상대 표현 '최근 1시간' |
| 시간창 확신도 | EXACT |
| 스윕 창 | 2026-08-03T12:50:46.561719Z ~ 2026-08-03T13:50:46.561719Z |
| 좁힌 창 | 2026-08-03T13:10:00Z ~ 2026-08-03T13:50:46.561719Z |
| 대상 | auth-service |
| traceId | 6a709737e51278eeebf347141cc5c3f2 |
| 트레이스 후보 | 3건 |
| 장애 후보 | 9건 · 선택 INC-3, INC-6, INC-7 |
| 계획 파싱 | 성공 |
| 스윕 컨텍스트 | 후보 + 원본 (A) |
| prompt | `./prompts/triage-prompt.md` |
| tokens | in 48757 / out 2836 · cost $0.4354 |
| chars | 컨텍스트 47,413 + 프롬프트 1,399 = **48,812** |
| elapsed | survey 1408ms · llm 45390ms |

**선정 이유**: 로그인 증상과 시각·서비스가 모두 맞는 것은 auth-service뿐이며, 13:25~13:30의 파드 교체 공백 + 그 직전 3.5초 /login 지연 + 같은 구간 ERROR/WARN 집중이 하나의 장애를 세 후보로 쪼개 보여주고 있어 함께 조사해야 한다.

**근거**

- up{job=auth-service, pod=auth-service-5999bb9f5c-qqrss} 가 13:25:46(ts 1785763546) 샘플을 끝으로 사라지고, 신규 파드 auth-service-5999bb9f5c-lbpf2 는 13:30:46(ts 1785763846)부터 등장 — 그 사이 auth-service 인스턴스가 0개인 공백 구간이 존재한다 (다른 서비스는 전 구간 연속)
- auth-service replica가 창 전체에서 항상 1개뿐 — 파드 교체 중 로그인 요청을 받을 대체 인스턴스가 없었다 (content-service는 h2f6n/nq9l2 2개 유지)
- INC-6: auth-service http post /login 3,509ms (traceId 6a709737e51278eeebf347141cc5c3f2, 13:27:19.919Z), status=unset — 에러가 아니라 지연이 증상이고, 하위 span 2db9f23c20f832d2 가 3,410ms로 지연의 97%를 차지 (serviceStats 전부 auth-service, 외부 의존성 span 없음)
- INC-3: auth-service ERROR/WARN 13:15~13:20 5건, 13:20~13:25 1건, 13:25~13:30 4건 — 파드 소멸 직전·직후에 로그가 집중
- INC-7: auth-service ERROR/WARN 13:45~13:50 1건 — 신규 파드로 교체된 뒤에도 잔존, 재발 가능성 확인 필요
- INC-9 트레이스(content-service /feeds/following 3,287ms)의 serviceStats에 auth-service span 8개가 포함 — 13:45 시점 토큰 검증 경로 지연이 auth-service에서 유래했을 가능성
- 인프라는 배제 근거: mongodb_up=1, kafka_brokers=1, node/kubelet/cadvisor up=1 전 구간 유지, kafka_consumergroup_lag 전 파티션 0 (-1은 미할당 파티션)

**스윕이 찾은 트레이스** (고른 것은 6a709737e51278eeebf347141cc5c3f2)

| traceId | 채널 | root service | root span | ms |
|---|---|---|---|---:|
| `6a7095b09dbabfa223fd8e4c12fda927` | error | content-service | http post /feeds | 95 |
| `6a709b5f358d68fc3c806907ad35c966` | slow | content-service | http get /feeds/following | 3287 |
| `6a709737e51278eeebf347141cc5c3f2` ←선택 | slow | auth-service | http post /login | 3509 |

**장애 후보** (코드가 신호를 묶은 것 · 창은 여기서 계산됨)

## INC-1  kafka  |  kafka_consumergroup_lag
- 구간: 2026-08-03T12:50:46Z ~ 2026-08-03T13:50:46Z  (MIMIR · 집계 해상도만큼 흐림)
- kafka_consumergroup_lag{consumergroup=chat-service-fcm-tokens, partition=0, topic=user.fcm-tokens} 가 0이었다 (2026-08-03T12:50:46Z ~ 2026-08-03T13:50:46Z)
- kafka_consumergroup_lag{consumergroup=chat-service-fcm-tokens, partition=1, topic=user.fcm-tokens} 가 0이었다 (2026-08-03T12:50:46Z ~ 2026-08-03T13:50:46Z)
- kafka_consumergroup_lag{consumergroup=chat-service-fcm-tokens, partition=2, topic=user.fcm-tokens} 가 0이었다 (2026-08-03T12:50:46Z ~ 2026-08-03T13:50:46Z)
- kafka_consumergroup_lag{consumergroup=chat-service-notification-settings, partition=0, topic=user.notification-settings} 가 0이었다 (2026-08-03T12:50:46Z ~ 2026-08-03T13:50:46Z)
- kafka_consumergroup_lag{consumergroup=chat-service-notification-settings, partition=1, topic=user.notification-settings} 가 0이었다 (2026-08-03T12:50:46Z ~ 2026-08-03T13:50:46Z)
- kafka_consumergroup_lag{consumergroup=chat-service-notification-settings, partition=2, topic=user.notification-settings} 가 0이었다 (2026-08-03T12:50:46Z ~ 2026-08-03T13:50:46Z)
- kafka_consumergroup_lag{consumergroup=db-writer, partition=0, topic=chat.messages} 가 0이었다 (2026-08-03T12:50:46Z ~ 2026-08-03T13:50:46Z)
- kafka_consumergroup_lag{consumergroup=db-writer, partition=1, topic=chat.messages} 가 0이었다 (2026-08-03T12:50:46Z ~ 2026-08-03T13:50:46Z)
- kafka_consumergroup_lag{consumergroup=db-writer, partition=11, topic=chat.messages} 가 0이었다 (2026-08-03T12:50:46Z ~ 2026-08-03T13:50:46Z)
- kafka_consumergroup_lag{consumergroup=db-writer, partition=3, topic=chat.messages} 가 0이었다 (2026-08-03T12:50:46Z ~ 2026-08-03T13:50:46Z)
- kafka_consumergroup_lag{consumergroup=db-writer, partition=4, topic=chat.messages} 가 0이었다 (2026-08-03T12:50:46Z ~ 2026-08-03T13:50:46Z)
- kafka_consumergroup_lag{consumergroup=db-writer, partition=5, topic=chat.messages} 가 0이었다 (2026-08-03T12:50:46Z ~ 2026-08-03T13:50:46Z)
- kafka_consumergroup_lag{consumergroup=db-writer, partition=7, topic=chat.messages} 가 0이었다 (2026-08-03T12:50:46Z ~ 2026-08-03T13:50:46Z)
- kafka_consumergroup_lag{consumergroup=db-writer, partition=8, topic=chat.messages} 가 0이었다 (2026-08-03T12:50:46Z ~ 2026-08-03T13:50:46Z)
- kafka_consumergroup_lag{consumergroup=db-writer-retry-1000, partition=0, topic=chat.messages-retry-1000} 가 0이었다 (2026-08-03T12:50:46Z ~ 2026-08-03T13:50:46Z)
- kafka_consumergroup_lag{consumergroup=db-writer-retry-2000, partition=0, topic=chat.messages-retry-2000} 가 0이었다 (2026-08-03T12:50:46Z ~ 2026-08-03T13:50:46Z)
- kafka_consumergroup_lag{consumergroup=db-writer-retry-4000, partition=0, topic=chat.messages-retry-4000} 가 0이었다 (2026-08-03T12:50:46Z ~ 2026-08-03T13:50:46Z)
- kafka_consumergroup_lag{consumergroup=notification, partition=0, topic=chat.messages} 가 0이었다 (2026-08-03T12:50:46Z ~ 2026-08-03T13:50:46Z)
- kafka_consumergroup_lag{consumergroup=notification, partition=1, topic=chat.messages} 가 0이었다 (2026-08-03T12:50:46Z ~ 2026-08-03T13:50:46Z)
- kafka_consumergroup_lag{consumergroup=notification, partition=11, topic=chat.messages} 가 0이었다 (2026-08-03T12:50:46Z ~ 2026-08-03T13:50:46Z)
- kafka_consumergroup_lag{consumergroup=notification, partition=2, topic=chat.messages} 가 0이었다 (2026-08-03T12:50:46Z ~ 2026-08-03T13:50:46Z)
- kafka_consumergroup_lag{consumergroup=notification, partition=3, topic=chat.messages} 가 0이었다 (2026-08-03T12:50:46Z ~ 2026-08-03T13:50:46Z)
- kafka_consumergroup_lag{consumergroup=notification, partition=4, topic=chat.messages} 가 0이었다 (2026-08-03T12:50:46Z ~ 2026-08-03T13:50:46Z)
- kafka_consumergroup_lag{consumergroup=notification, partition=5, topic=chat.messages} 가 0이었다 (2026-08-03T12:50:46Z ~ 2026-08-03T13:50:46Z)
- kafka_consumergroup_lag{consumergroup=notification, partition=7, topic=chat.messages} 가 0이었다 (2026-08-03T12:50:46Z ~ 2026-08-03T13:50:46Z)
- kafka_consumergroup_lag{consumergroup=notification, partition=8, topic=chat.messages} 가 0이었다 (2026-08-03T12:50:46Z ~ 2026-08-03T13:50:46Z)
- kafka_consumergroup_lag{consumergroup=notification-processors, partition=0, topic=user.notifications} 가 0이었다 (2026-08-03T12:50:46Z ~ 2026-08-03T13:50:46Z)
- kafka_consumergroup_lag{consumergroup=notification-processors, partition=1, topic=user.notifications} 가 0이었다 (2026-08-03T12:50:46Z ~ 2026-08-03T13:50:46Z)
- kafka_consumergroup_lag{consumergroup=notification-processors, partition=2, topic=user.notifications} 가 0이었다 (2026-08-03T12:50:46Z ~ 2026-08-03T13:50:46Z)
- kafka_consumergroup_lag{consumergroup=notification-processors, partition=3, topic=user.notifications} 가 0이었다 (2026-08-03T12:50:46Z ~ 2026-08-03T13:50:46Z)
- kafka_consumergroup_lag{consumergroup=notification-processors, partition=4, topic=user.notifications} 가 0이었다 (2026-08-03T12:50:46Z ~ 2026-08-03T13:50:46Z)
- kafka_consumergroup_lag{consumergroup=notification-processors, partition=5, topic=user.notifications} 가 0이었다 (2026-08-03T12:50:46Z ~ 2026-08-03T13:50:46Z)
- kafka_consumergroup_lag{consumergroup=notification-recovery, partition=0, topic=user.notifications.dlq} 가 0이었다 (2026-08-03T12:50:46Z ~ 2026-08-03T13:50:46Z)
- kafka_consumergroup_lag{consumergroup=notification-recovery, partition=2, topic=user.notifications.dlq} 가 0이었다 (2026-08-03T12:50:46Z ~ 2026-08-03T13:50:46Z)
- kafka_consumergroup_lag{consumergroup=notification-retry-2000, partition=0, topic=chat.messages-retry-2000} 가 0이었다 (2026-08-03T12:50:46Z ~ 2026-08-03T13:50:46Z)
- kafka_consumergroup_lag{consumergroup=notification-retry-4000, partition=0, topic=chat.messages-retry-4000} 가 0이었다 (2026-08-03T12:50:46Z ~ 2026-08-03T13:50:46Z)
- 같은 시각의 다른 후보: INC-2, INC-3, INC-4, INC-5, INC-6, INC-7, INC-8, INC-9  (인과 여부는 판단하지 않았다)

## INC-2  chat-service  |  websocket_active_users
- 구간: 2026-08-03T12:50:46Z ~ 2026-08-03T13:50:46Z  (MIMIR · 집계 해상도만큼 흐림)
- websocket_active_users{container=chat-service, namespace=default, pod=chat-service-fdcc7c776-qrbc2} 가 0이었다 (2026-08-03T12:50:46Z ~ 2026-08-03T13:50:46Z)
- 같은 시각의 다른 후보: INC-1, INC-3, INC-4, INC-5, INC-6, INC-7, INC-8, INC-9  (인과 여부는 판단하지 않았다)

## INC-3  auth-service  |  ERROR/WARN
- 구간: 2026-08-03T13:15:00Z ~ 2026-08-03T13:30:00Z  (LOKI · 집계 해상도만큼 흐림)
- ERROR/WARN 5건 (2026-08-03T13:15:00Z ~ 2026-08-03T13:20:00Z)
- ERROR/WARN 1건 (2026-08-03T13:20:00Z ~ 2026-08-03T13:25:00Z)
- ERROR/WARN 4건 (2026-08-03T13:25:00Z ~ 2026-08-03T13:30:00Z)
- 같은 시각의 다른 후보: INC-1, INC-2, INC-4, INC-5, INC-6  (인과 여부는 판단하지 않았다)

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
- 같은 시각의 다른 후보: INC-1, INC-2, INC-8, INC-9  (인과 여부는 판단하지 않았다)

## INC-8  content-service  |  ERROR/WARN
- 구간: 2026-08-03T13:45:00Z ~ 2026-08-03T13:50:00Z  (LOKI · 집계 해상도만큼 흐림)
- ERROR/WARN 3건 (2026-08-03T13:45:00Z ~ 2026-08-03T13:50:00Z)
- 같은 시각의 다른 후보: INC-1, INC-2, INC-7, INC-9  (인과 여부는 판단하지 않았다)

## INC-9  content-service  |  http get /feeds/following
- 구간: 2026-08-03T13:45:03.385679Z ~ 2026-08-03T13:45:06.672679Z  (TEMPO · 시각 정확)
- content-service http get /feeds/following 3,287ms (slow 채널)
- traceId: 6a709b5f358d68fc3c806907ad35c966
- 같은 시각의 다른 후보: INC-1, INC-2, INC-7, INC-8  (인과 여부는 판단하지 않았다)

**기각한 후보**

- INC-1 — kafka_consumergroup_lag가 전 파티션 0으로 창 전체 평탄하며 값 -1은 컨슈머 미할당 파티션을 뜻하는 상수값 — 변화가 없으므로 신호가 아니고 로그인 경로와도 무관하다.
- INC-2 — websocket_active_users=0이 12:50~13:50 전 구간 동일한 상수이고 chat-service의 up은 한 번도 끊기지 않아, 이 창 안에서 발생한 변화가 아니며 로그인 증상과 경로가 다르다.
- INC-4 — content-service ERROR/WARN 13:20~13:25 4건은 피드 쓰기 경로이며 로그인 문의와 엔드포인트가 다르다 — auth 쪽 조사 후 연관이 드러나면 재소환한다.
- INC-5 — 유일한 에러 트레이스지만 content-service http post /feeds 95ms로 빠르게 끝났고(span 23개 중 error 1개, 3.9ms), 로그인 실패 규모를 설명하지 못한다.
- INC-8 — content-service ERROR/WARN 13:45~13:50 3건은 INC-9의 피드 조회 지연과 같은 지문으로 보이며, 로그인 증상이 아닌 콘텐츠 조회 증상이다.
- INC-9 — root가 content-service /feeds/following이라 로그인 경로가 아니지만 트레이스에 auth-service span 8개가 섞여 있어 완전 무해하지는 않다 — INC-7과 시각이 겹치므로 auth-service 조사 창에서 이 traceId(6a709b5f358d68fc3c806907ad35c966)의 auth span 지연을 함께 확인한다.

**보정 기록**

- 창을 후보 [INC-3, INC-6, INC-7] 의 신호 시각에서 계산했다 (2026-08-03T13:10:00Z ~ 2026-08-03T13:50:46.561719Z)

## 수집 범위 (Coverage)

- **window**: 2026-08-03T13:10:00Z ~ 2026-08-03T13:50:46.561719Z (2446s)
- **trace**: 82,057B / 87 spans
- **창 안 후보 트레이스**: 10건 / 82,057B
- **logs**: errwarn=71,750B · traceId=6,497B
- **metrics**: 8 수집 / 262,759B, 누락 [sum(rate(http_server_requests_seconds_count{application="content-service", status="401"}[1m]))]
- **context**: 187,251 chars (+ 시스템 프롬프트 576 chars)

### 토큰 축 (개선 지표)

| 단계 | 총 in | chars | 컨텍스트 토큰 |
|---|---:|---:|---:|
| 탐색 | 48,757 | 48,812 | 27,448 |
| 분석 | 116,627 | 187,827 | 95,318 |
| **합계** | **165,384** | | **122,766** |

- **overheadTokens 21,309 tok — 이 회차에 실측**(1자 프롬프트 프로브, 같은 명령·모델·샌드박스).
  호출마다 한 번씩 얹히므로 단계별로 뺀다. **다른 날 상수를 쓰지 않았다 = `█ 실측`.**
- contextTokens (count_tokens API): 측정 안 됨 — 구독 CLI 경로엔 API 키가 없다
- 비용은 **API 환산 추정치**다. 구독 계정으로 호출하므로 실제 청구액이 아니다.

## 수집 실패/누락

- Metric 'sum(rate(http_server_requests_seconds_count{application="content-service", status="401"}[1m]))' returned no series in this window; skipped.

---

## 1. 원인 후보 랭킹 (최대 3개)

1. **13:26~13:28 UTC 사이 auth-service 파드 교체(단일 인스턴스 중단)로 로그인 요청이 처리 불가였던 구간**
2. **재기동 직후 auth-service 콜드스타트 지연 — 로그인 응답 3.5초(클라이언트 타임아웃으로 "로그인 안 됨"으로 체감)**
3. **auth-service의 팔로잉/팔로워 조회 NPE 500 — 로그인 직후 화면 로딩 실패를 "로그인 실패"로 문의했을 가능성**

> 전제: **수집된 데이터 안에 "실패한 로그인" 자체는 한 건도 없다.** 유일한 `/login` 트레이스(6a709737…)는 `status=200, outcome=SUCCESS`다. 아래 후보는 모두 정황 증거이며, 그래서 최고 확신도가 "중간"을 넘지 않는다.

---

## 2. 후보별 근거

### 후보 1 — auth-service 파드 교체로 인한 로그인 중단 구간

- **근거**
  - `up{job="auth-service", pod=auth-service-5999bb9f5c-qqrss}` = 65점 · **13:10:00Z ~ 13:26:00Z**에서 끊김.
  - `up{job="auth-service", pod=auth-service-5999bb9f5c-lbpf2}` = 92점 · **13:28:00Z ~ 13:50:45Z**에서 시작.
    → 두 시리즈 사이 **약 2분간 auth-service 타깃이 하나도 up=1이 아니었다.**
  - 신규 파드 lbpf2의 부팅 로그가 그 구간에 그대로 찍혀 있다: `2026-08-03 22:26:00 WARN … BeanPostProcessorChecker`, `22:26:20 WARN … MySQLDialect`, `22:26:56 WARN … spring.jpa.open-in-view`, `22:27:02~22:27:03 INFO o.s.s.web.DefaultSecurityFilterChain - Will secure any request with […JwtFilter@27777afd…]` (KST 표기 = UTC 13:26:00~13:27:03). 즉 **13:27 직전까지 시큐리티 필터체인조차 등록되지 않은 상태**였다.
  - 두 파드의 ReplicaSet 해시가 `5999bb9f5c`로 **동일**하다 → 이미지/스펙 변경 배포가 아니라 **파드 단위 재생성(크래시·evict·노드 이슈 등)**이다.
  - 로그인은 auth-service 단독 처리다(호출 그래프상 `/login` 스팬은 auth-service 내부에서만 완결). 대체 인스턴스가 없으면 곧바로 전면 실패로 이어진다.
- **확신도**: **중간**
- **반증 데이터**:
  - `rate(jvm_gc_pause_seconds_sum[5m])`의 qqrss 시리즈는 **13:29:00Z까지** 존재하고 lbpf2 `up`은 13:28:00Z부터 시작 → 스크레이프 경계 기준으로는 **두 파드가 겹쳤을 가능성**도 있어 "완전 무중단 구간"이 확정되지 않는다.
  - 관측 창 안에서 파드 재시작 사유·횟수를 보여주는 데이터(kube_pod_container_status_restarts_total, last_terminated_reason)가 **없다**.
  - 사라지기 전 qqrss에서 OOM·크래시·SIGTERM 관련 ERROR/WARN 로그가 **한 줄도 없다**(마지막 로그는 13:20:18 Hibernate 경고).

### 후보 2 — 재기동 직후 콜드스타트 지연

- **근거**
  - `[HTTP-SLOW] POST /api/login 200 - 3497ms` (traceId `6a709737e51278eeebf347141cc5c3f2`, 13:27:23Z).
  - 해당 트레이스 내부: `http post /login` 서버 스팬 13:27:19.919 → 13:27:23.428(**3.509s**), 그중 `secured request` 스팬이 **3.410s**를 차지. 하위에 DB/Redis/외부 호출 스팬이 하나도 없다 → 대기·경합이 아니라 **최초 실행 경로 초기화(JIT/클래스 로딩/암호화 초기화) 성격**에 가깝다.
  - 이 요청은 신규 파드가 필터체인 등록을 마친(13:27:03) **직후 16초 시점**에 들어온 첫 로그인이다.
  - 같은 파드에서 `[HTTP-SLOW] GET /api/external/users/1/followings 200 - 2783ms` (13:45:06)도 관측 → 콜드스타트 이후에도 auth-service의 일부 경로가 초 단위다.
- **확신도**: **중간**
- **반증 데이터**:
  - 결과는 **200 SUCCESS**다. 서버 관점에서 실패가 아니다. 클라이언트 타임아웃 값이 무엇인지 보여주는 데이터가 없다.
  - `hikaricp_connections_active`/`pending`이 auth-service 두 파드 모두 **전 구간 0**, GC pause도 최대 1.04e-4초 수준 → DB 풀 고갈·GC 스톨은 아니다(즉 지속적 성능 문제로 볼 근거는 없음).
  - `/login` 샘플이 **1건뿐**이라 3.5초가 대표값인지 단발성인지 판단 불가.

### 후보 3 — 팔로잉/팔로워 조회 NPE 500

- **근거**
  - 구 파드 qqrss, 13:19:18Z, 2건:
    - `java.lang.NullPointerException: Cannot invoke "java.lang.Integer.intValue()" because "this.size" is null` → `FollowCondition$FollowingSearch.limit(FollowCondition.java:25)` → `FollowRepositoryCustomImpl.findFollowingsByUserId(:35)` → `UserService.getFollowingList(:307)` → 결과 `[HTTP] GET /api/user/1/following 500 - 54ms`
    - 동일 NPE가 `FollowCondition$FollowerSearch.limit(:45)` → `findFollowersByUserId(:65)` → `[HTTP] GET /api/user/1/followers 500 - 34ms`
  - 즉 **size 쿼리파라미터가 없을 때 기본값이 없어** 무조건 500이다. 로그인 직후 진입하는 화면이 이 API를 호출한다면 사용자는 "로그인 후 아무것도 안 뜬다 = 로그인 안 된다"로 문의할 수 있다.
- **확신도**: **낮음**
- **반증 데이터**:
  - 실패 엔드포인트는 `/api/user/{id}/following(s)`이지 `/login`이 아니다. **로그인 경로와의 연결은 데이터에 없다.**
  - 발생 2건 전부 `userId=1`, 시각도 13:19:18 한 시점에 집중 → "문의가 몰릴" 규모의 근거가 없다.
  - 같은 도메인의 외부용 API `GET /api/external/users/1/followings`는 13:45:06에 **200**으로 성공했다(size가 채워지는 경로로 보임).

---

### 데이터 부족 (결론 확신도를 낮추는 공백)

- **auth-service의 요청/상태코드 메트릭이 전혀 없다.** 실패한 수집 항목은 `http_server_requests_seconds_count{application="content-service", status="401"}` 인데, **이건 content-service를 겨냥한 쿼리라 로그인 분석에는 애초에 대상이 틀렸다.** 로그인 성패를 판단하려면 auth-service의 `/login` 상태코드별 카운트가 필요하고, 그 시리즈는 수집되지 않았다.
- 파드 재시작 사유/횟수, 컨테이너 메모리·OOMKill, 이벤트가 없다.
- 조회 창이 **13:10~13:50 UTC(40분)** 로, 문의가 몰린 "최근 1시간"의 앞 20분은 관측되지 않았다.
- 트레이스 10건 중 `/login`은 1건, 인증 실패(401/403) 트레이스는 0건이다.

---

## 3. 권장 다음 조치

1. **파드 교체 사실과 사유 확정** (후보 1 검증, 최우선)
   - `kubectl get pods -n default -l app=auth-service -o wide` / `kubectl describe pod auth-service-5999bb9f5c-lbpf2` → `Last State`, `Reason`(OOMKilled/Error/Evicted), `Restart Count`, 이벤트 타임스탬프 확인.
   - `kubectl get events -n default --sort-by=.lastTimestamp | grep auth-service` 로 13:25~13:29Z 구간 확인.
   - `kubectl get deploy auth-service -o yaml` 에서 **replicas 값과 readinessProbe / maxUnavailable** 확인 — replicas=1이면 재생성 = 전면 로그인 중단이 구조적으로 발생한다.
2. **로그인 실패 여부를 실제 수치로 확정**
   - Mimir: `sum by (status) (rate(http_server_requests_seconds_count{application="auth-service", uri="/login"}[1m]))` 를 13:10~13:50Z로 조회. 시리즈가 없으면 auth-service의 Actuator/Prometheus 스크레이프 설정부터 점검.
   - Loki: `{job="default/auth-service"} |= "/api/login"` 을 창 전체로 조회해 200/4xx/5xx 분포와 건수 확인(현재는 SLOW 1건만 표본에 들어옴).
3. **콜드스타트 완화** (후보 2)
   - readinessProbe가 필터체인 등록 완료(13:27:03) 이후에야 트래픽을 받도록 설정돼 있는지 확인. `initialDelaySeconds`/`failureThreshold` 점검.
   - replicas ≥ 2 + `maxUnavailable: 0` 으로 무중단 교체 보장.
4. **NPE 즉시 수정** (후보 3)
   - `FollowCondition.java:25`(FollowingSearch.limit), `:45`(FollowerSearch.limit) — `size`가 null일 때 기본값(예: 20) 반환하도록 방어. 현재는 파라미터 누락만으로 100% 500이다.
   - 수정 전이라도 `GET /api/user/{id}/following?size=N` 호출로 재현 여부를 즉시 확인 가능.
5. **관측 창 확장 재수집**
   - 12:50~13:50Z(문의 시간대 전체)로 다시 조회하고, `kube_pod_container_status_restarts_total{pod=~"auth-service.*"}`, `container_memory_working_set_bytes{pod=~"auth-service.*"}` 를 추가 수집한다.

---

## 관측 증거 (Evidence)

> 이 조사가 실제로 본 값이다. 원본 응답 전체는 `reports/raw/scan-1785762600-*.json`에 있다.

### 호출 그래프 (트레이스에서 추출)

```
chat-service --db--> redis  1회  최대 0.5ms  [INFO]
content-service --db--> redis  10회  최대 16.6ms  [LRANGE, GET, SET, PEXPIRE, DEL, RPUSH, INFO]
content-service --jdbc--> mysql/content (HikariPool-1)  47회  최대 3275.4ms
    error: Duplicate entry '160-177' for key 'tb_feed_hashtags.uk_feed_hashtag'
    events: acquired, rollback, commit
content-service --service--> auth-service  4회  최대 2785.8ms
```

### span (duration 상위 15 / 전체 87)

| ms | service | span | 시작 |
|---:|---|---|---|
| 3509.12 | auth-service | `http post /login` | 2026-08-03T13:27:19.919322Z |
| 3410.42 | auth-service | `secured request` | 2026-08-03T13:27:20.006305Z |
| 3287.80 | content-service | `http get /feeds/following` | 2026-08-03T13:45:03.385679Z |
| 3285.45 | content-service | `secured request` | 2026-08-03T13:45:03.386031Z |
| 3275.41 | content-service | `connection` | 2026-08-03T13:45:03.395600Z |
| 2785.80 | auth-service | `http get /external/users/{userid}/followings` | 2026-08-03T13:45:03.420115Z |
| 2779.97 | auth-service | `secured request` | 2026-08-03T13:45:03.422723Z |
| 2718.79 | content-service | `http get` | 2026-08-03T13:45:03.411112Z |
| 343.96 | content-service | `http get` | 2026-08-03T13:45:06.266283Z |
| 308.40 | auth-service | `http get /external/users` | 2026-08-03T13:45:06.302482Z |
| 303.12 | auth-service | `secured request` | 2026-08-03T13:45:06.306325Z |
| 137.25 | content-service | `task battle-deadline-notification-scheduler.notify` | 2026-08-03T13:16:00.019928Z |
| 111.68 | content-service | `connection` | 2026-08-03T13:16:00.037179Z |
| 95.14 | content-service | `http post /feeds` | 2026-08-03T13:20:48.769546Z |
| 92.97 | content-service | `secured request` | 2026-08-03T13:20:48.769927Z |

### 로그 원문 (60 / 전체 425줄)

전체가 상한을 넘어 ERROR/WARN 줄을 우선 발췌했다. 나머지는 원본 파일에 있다.

```
2026-08-03T13:19:07.677361620Z  [auth-service]  [2m2026-08-03 22:19:07[0;39m [2m[http-nio-8081-exec-1][0;39m [33m WARN [traceId=6a70954b2f8786863663471e9ab9402e,spanId=e9c884e23b79c8bd,userId=1][0;39m [36morg.hibernate.orm.query[0;39m [2m-[0;39m HHH90003004: firstResult/maxResults specified with collection fetch; applying in memory
2026-08-03T13:19:18.452681926Z  [auth-service]  [2m2026-08-03 22:19:18[0;39m [2m[http-nio-8081-exec-3][0;39m [31mERROR [traceId=6a709556a7b651ba4628e9d2ac58f500,spanId=701c4504f1b14ba8,userId=1][0;39m [36mc.e.t.a.c.e.GlobalExceptionHandler[0;39m [2m-[0;39m [api-error] handleAllException
2026-08-03T13:19:18.452711128Z  [auth-service]  java.lang.NullPointerException: Cannot invoke "java.lang.Integer.intValue()" because "this.size" is null
2026-08-03T13:19:18.452715880Z  [auth-service]  at com.example.toyauth.app.user.controller.dto.FollowCondition$FollowingSearch.limit(FollowCondition.java:25)
2026-08-03T13:19:18.452720337Z  [auth-service]  at com.example.toyauth.app.follow.repository.querydsl.impl.FollowRepositoryCustomImpl.findFollowingsByUserId(FollowRepositoryCustomImpl.java:35)
2026-08-03T13:19:18.452749778Z  [auth-service]  at org.springframework.aop.support.AopUtils.invokeJoinpointUsingReflection(AopUtils.java:352)
2026-08-03T13:19:18.452753187Z  [auth-service]  at org.springframework.aop.framework.ReflectiveMethodInvocation.invokeJoinpoint(ReflectiveMethodInvocation.java:196)
2026-08-03T13:19:18.452755840Z  [auth-service]  at org.springframework.aop.framework.ReflectiveMethodInvocation.proceed(ReflectiveMethodInvocation.java:163)
2026-08-03T13:19:18.452758729Z  [auth-service]  at org.springframework.aop.framework.CglibAopProxy$CglibMethodInvocation.proceed(CglibAopProxy.java:765)
2026-08-03T13:19:18.452761711Z  [auth-service]  at org.springframework.dao.support.PersistenceExceptionTranslationInterceptor.invoke(PersistenceExceptionTranslationInterceptor.java:137)
2026-08-03T13:19:18.452764508Z  [auth-service]  at org.springframework.aop.framework.ReflectiveMethodInvocation.proceed(ReflectiveMethodInvocation.java:184)
2026-08-03T13:19:18.452767092Z  [auth-service]  at org.springframework.aop.framework.CglibAopProxy$CglibMethodInvocation.proceed(CglibAopProxy.java:765)
2026-08-03T13:19:18.452769692Z  [auth-service]  at org.springframework.aop.framework.CglibAopProxy$DynamicAdvisedInterceptor.intercept(CglibAopProxy.java:717)
2026-08-03T13:19:18.452786955Z  [auth-service]  at org.springframework.aop.support.AopUtils.invokeJoinpointUsingReflection(AopUtils.java:352)
2026-08-03T13:19:18.452790137Z  [auth-service]  at org.springframework.data.repository.core.support.RepositoryMethodInvoker$RepositoryFragmentMethodInvoker.lambda$new$0(RepositoryMethodInvoker.java:277)
2026-08-03T13:19:18.452792855Z  [auth-service]  at org.springframework.data.repository.core.support.RepositoryMethodInvoker.doInvoke(RepositoryMethodInvoker.java:170)
2026-08-03T13:19:18.452795468Z  [auth-service]  at org.springframework.data.repository.core.support.RepositoryMethodInvoker.invoke(RepositoryMethodInvoker.java:158)
2026-08-03T13:19:18.452798344Z  [auth-service]  at org.springframework.data.repository.core.support.RepositoryComposition$RepositoryFragments.invoke(RepositoryComposition.java:516)
2026-08-03T13:19:18.452800948Z  [auth-service]  at org.springframework.data.repository.core.support.RepositoryComposition.invoke(RepositoryComposition.java:285)
2026-08-03T13:19:18.452803899Z  [auth-service]  at org.springframework.data.repository.core.support.RepositoryFactorySupport$ImplementationMethodExecutionInterceptor.invoke(RepositoryFactorySupport.java:628)
2026-08-03T13:19:18.452806690Z  [auth-service]  at org.springframework.aop.framework.ReflectiveMethodInvocation.proceed(ReflectiveMethodInvocation.java:184)
2026-08-03T13:19:18.452809712Z  [auth-service]  at org.springframework.data.repository.core.support.QueryExecutorMethodInterceptor.doInvoke(QueryExecutorMethodInterceptor.java:168)
2026-08-03T13:19:18.452812648Z  [auth-service]  at org.springframework.data.repository.core.support.QueryExecutorMethodInterceptor.invoke(QueryExecutorMethodInterceptor.java:143)
2026-08-03T13:19:18.452815537Z  [auth-service]  at org.springframework.aop.framework.ReflectiveMethodInvocation.proceed(ReflectiveMethodInvocation.java:184)
2026-08-03T13:19:18.452825392Z  [auth-service]  at org.springframework.data.projection.DefaultMethodInvokingMethodInterceptor.invoke(DefaultMethodInvokingMethodInterceptor.java:70)
2026-08-03T13:19:18.452828696Z  [auth-service]  at org.springframework.aop.framework.ReflectiveMethodInvocation.proceed(ReflectiveMethodInvocation.java:184)
2026-08-03T13:19:18.452831186Z  [auth-service]  at org.springframework.transaction.interceptor.TransactionInterceptor$1.proceedWithInvocation(TransactionInterceptor.java:123)
2026-08-03T13:19:18.452838846Z  [auth-service]  at org.springframework.transaction.interceptor.TransactionAspectSupport.invokeWithinTransaction(TransactionAspectSupport.java:385)
2026-08-03T13:19:18.452841756Z  [auth-service]  at org.springframework.transaction.interceptor.TransactionInterceptor.invoke(TransactionInterceptor.java:119)
2026-08-03T13:19:18.452844409Z  [auth-service]  at org.springframework.aop.framework.ReflectiveMethodInvocation.proceed(ReflectiveMethodInvocation.java:184)
2026-08-03T13:19:18.452847036Z  [auth-service]  at org.springframework.dao.support.PersistenceExceptionTranslationInterceptor.invoke(PersistenceExceptionTranslationInterceptor.java:137)
2026-08-03T13:19:18.452849981Z  [auth-service]  at org.springframework.aop.framework.ReflectiveMethodInvocation.proceed(ReflectiveMethodInvocation.java:184)
2026-08-03T13:19:18.452854056Z  [auth-service]  at org.springframework.data.jpa.repository.support.CrudMethodMetadataPostProcessor$CrudMethodMetadataPopulatingMethodInterceptor.invoke(CrudMethodMetadataPostProcessor.java:164)
2026-08-03T13:19:18.452856964Z  [auth-service]  at org.springframework.aop.framework.ReflectiveMethodInvocation.proceed(ReflectiveMethodInvocation.java:184)
2026-08-03T13:19:18.452859922Z  [auth-service]  at org.springframework.aop.interceptor.ExposeInvocationInterceptor.invoke(ExposeInvocationInterceptor.java:97)
2026-08-03T13:19:18.452862797Z  [auth-service]  at org.springframework.aop.framework.ReflectiveMethodInvocation.proceed(ReflectiveMethodInvocation.java:184)
2026-08-03T13:19:18.452865308Z  [auth-service]  at org.springframework.aop.framework.JdkDynamicAopProxy.invoke(JdkDynamicAopProxy.java:249)
2026-08-03T13:19:18.452871207Z  [auth-service]  at com.example.toyauth.app.user.service.UserService.getFollowingList(UserService.java:307)
2026-08-03T13:19:18.452885102Z  [auth-service]  at org.springframework.aop.support.AopUtils.invokeJoinpointUsingReflection(AopUtils.java:352)
2026-08-03T13:19:18.452887748Z  [auth-service]  at org.springframework.aop.framework.ReflectiveMethodInvocation.invokeJoinpoint(ReflectiveMethodInvocation.java:196)
2026-08-03T13:19:18.453030300Z  [auth-service]  at org.springframework.security.web.access.ExceptionTranslationFilter.doFilter(ExceptionTranslationFilter.java:126)
2026-08-03T13:19:18.453033339Z  [auth-service]  at org.springframework.security.web.access.ExceptionTranslationFilter.doFilter(ExceptionTranslationFilter.java:120)
2026-08-03T13:19:18.459699388Z  [auth-service]  [2m2026-08-03 22:19:18[0;39m [2m[http-nio-8081-exec-3][0;39m [31mERROR [traceId=6a709556a7b651ba4628e9d2ac58f500,spanId=4628e9d2ac58f500,userId=NONE][0;39m [36mc.e.t.a.c.f.RequestLoggingFilter[0;39m [2m-[0;39m [HTTP] GET /api/user/1/following 500 - 54ms
2026-08-03T13:19:18.547777032Z  [auth-service]  [2m2026-08-03 22:19:18[0;39m [2m[http-nio-8081-exec-10][0;39m [31mERROR [traceId=6a709556f6e16f65ec4dc5deefad718c,spanId=1ca310ee1c13d395,userId=1][0;39m [36mc.e.t.a.c.e.GlobalExceptionHandler[0;39m [2m-[0;39m [api-error] handleAllException
2026-08-03T13:19:18.547794362Z  [auth-service]  java.lang.NullPointerException: Cannot invoke "java.lang.Integer.intValue()" because "this.size" is null
2026-08-03T13:19:18.547833453Z  [auth-service]  at org.springframework.dao.support.PersistenceExceptionTranslationInterceptor.invoke(PersistenceExceptionTranslationInterceptor.java:137)
2026-08-03T13:19:18.547924514Z  [auth-service]  at org.springframework.dao.support.PersistenceExceptionTranslationInterceptor.invoke(PersistenceExceptionTranslationInterceptor.java:137)
2026-08-03T13:19:18.548117711Z  [auth-service]  at org.springframework.security.web.access.ExceptionTranslationFilter.doFilter(ExceptionTranslationFilter.java:126)
2026-08-03T13:19:18.548120076Z  [auth-service]  at org.springframework.security.web.access.ExceptionTranslationFilter.doFilter(ExceptionTranslationFilter.java:120)
2026-08-03T13:19:18.549420548Z  [auth-service]  [2m2026-08-03 22:19:18[0;39m [2m[http-nio-8081-exec-10][0;39m [31mERROR [traceId=6a709556f6e16f65ec4dc5deefad718c,spanId=ec4dc5deefad718c,userId=NONE][0;39m [36mc.e.t.a.c.f.RequestLoggingFilter[0;39m [2m-[0;39m [HTTP] GET /api/user/1/followers 500 - 34ms
2026-08-03T13:20:18.969257876Z  [auth-service]  [2m2026-08-03 22:20:18[0;39m [2m[http-nio-8081-exec-5][0;39m [33m WARN [traceId=6a709592fc9bba07669166205071d1bb,spanId=980b5f7f4498854a,userId=1][0;39m [36morg.hibernate.orm.query[0;39m [2m-[0;39m HHH90003004: firstResult/maxResults specified with collection fetch; applying in memory
2026-08-03T13:26:00.630761787Z  [auth-service]  [2m2026-08-03 22:26:00[0;39m [2m[main][0;39m [33m WARN [traceId=NONE,spanId=NONE,userId=NONE][0;39m [36mo.s.c.s.PostProcessorRegistrationDelegate$BeanPostProcessorChecker[0;39m [2m-[0;39m Bean 'org.springframework.ws.config.annotation.DelegatingWsConfiguration' of type [org.springframework.ws.config.annotation.DelegatingWsConfiguration$$SpringCGLIB$$0] is not eligible for getting processed by all BeanPostProcessors (for example: not eligible for auto-proxying). The currently created BeanPostProcessor [annotationActionEndpointMapping] is declared through a non-static factory method on that class; consider declaring it as static instead.
2026-08-03T13:26:20.523951518Z  [auth-service]  [2m2026-08-03 22:26:20[0;39m [2m[main][0;39m [33m WARN [traceId=NONE,spanId=NONE,userId=NONE][0;39m [36morg.hibernate.orm.deprecation[0;39m [2m-[0;39m HHH90000025: MySQLDialect does not need to be specified explicitly using 'hibernate.dialect' (remove the property setting and it will be selected by default)
2026-08-03T13:26:56.112813998Z  [auth-service]  [2m2026-08-03 22:26:56[0;39m [2m[main][0;39m [33m WARN [traceId=NONE,spanId=NONE,userId=NONE][0;39m [36mo.s.b.a.o.j.JpaBaseConfiguration$JpaWebConfiguration[0;39m [2m-[0;39m spring.jpa.open-in-view is enabled by default. Therefore, database queries may be performed during view rendering. Explicitly configure spring.jpa.open-in-view to disable this warning
2026-08-03T13:27:02.909559949Z  [auth-service]  [2m2026-08-03 22:27:02[0;39m [2m[main][0;39m [32m INFO [traceId=NONE,spanId=NONE,userId=NONE][0;39m [36mo.s.s.web.DefaultSecurityFilterChain[0;39m [2m-[0;39m Will secure Or [Mvc [pattern='/api/external/**']] with [org.springframework.security.web.session.DisableEncodeUrlFilter@b11d2ad, org.springframework.security.web.context.request.async.WebAsyncManagerIntegrationFilter@5ce1ed2f, org.springframework.security.web.context.SecurityContextHolderFilter@5ef5455c, org.springframework.security.web.header.HeaderWriterFilter@4e01cba8, org.springframework.web.filter.CorsFilter@612a0d49, org.springframework.security.web.authentication.logout.LogoutFilter@31d29626, com.example.toyauth.app.common.filter.ExternalAuthenticationFilter@2c66be14, org.springframework.security.web.savedrequest.RequestCacheAwareFilter@579325f2, org.springframework.security.web.servletapi.SecurityContextHolderAwareRequestFilter@4ddb882, org.springframework.security.web.authentication.AnonymousAuthenticationFilter@268fca0a, org.springframework.security.web.session.SessionManagementFilter@683ba3c1, org.springframework.security.web.access.ExceptionTranslationFilter@37d4b676, org.springframework.security.web.access.intercept.AuthorizationFilter@7c47ae7d]
2026-08-03T13:27:03.227490147Z  [auth-service]  [2m2026-08-03 22:27:03[0;39m [2m[main][0;39m [32m INFO [traceId=NONE,spanId=NONE,userId=NONE][0;39m [36mo.s.s.web.DefaultSecurityFilterChain[0;39m [2m-[0;39m Will secure any request with [org.springframework.security.web.session.DisableEncodeUrlFilter@6c81bd6d, org.springframework.security.web.context.request.async.WebAsyncManagerIntegrationFilter@21eeb0ce, org.springframework.security.web.context.SecurityContextHolderFilter@37e5111b, org.springframework.security.web.header.HeaderWriterFilter@1d2414a1, org.springframework.web.filter.CorsFilter@29af0d3b, org.springframework.security.web.authentication.logout.LogoutFilter@7b79305c, org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestRedirectFilter@57d354fc, org.springframework.security.oauth2.client.web.OAuth2LoginAuthenticationFilter@76700647, com.example.toyauth.app.common.filter.JwtFilter@27777afd, org.springframework.security.web.authentication.ui.DefaultLoginPageGeneratingFilter@44067808, org.springframework.security.web.authentication.ui.DefaultLogoutPageGeneratingFilter@15682e7f, org.springframework.security.web.savedrequest.RequestCacheAwareFilter@2006249d, org.springframework.security.web.servletapi.SecurityContextHolderAwareRequestFilter@1d3aad20, org.springframework.security.web.authentication.AnonymousAuthenticationFilter@74e3836a, org.springframework.security.web.session.SessionManagementFilter@1f78d3bc, org.springframework.security.web.access.ExceptionTranslationFilter@7a0daf3d]
2026-08-03T13:27:23.421814622Z  [auth-service]  [2m2026-08-03 22:27:23[0;39m [2m[http-nio-8081-exec-1][0;39m [33m WARN [traceId=6a709737e51278eeebf347141cc5c3f2,spanId=ebf347141cc5c3f2,userId=NONE][0;39m [36mc.e.t.a.c.f.RequestLoggingFilter[0;39m [2m-[0;39m [HTTP-SLOW] POST /api/login 200 - 3497ms
2026-08-03T13:27:23.421814622Z  [auth-service]  [2m2026-08-03 22:27:23[0;39m [2m[http-nio-8081-exec-1][0;39m [33m WARN [traceId=6a709737e51278eeebf347141cc5c3f2,spanId=ebf347141cc5c3f2,userId=NONE][0;39m [36mc.e.t.a.c.f.RequestLoggingFilter[0;39m [2m-[0;39m [HTTP-SLOW] POST /api/login 200 - 3497ms
2026-08-03T13:45:06.205745656Z  [auth-service]  [2m2026-08-03 22:45:06[0;39m [2m[http-nio-8081-exec-4][0;39m [33m WARN [traceId=6a709b5f358d68fc3c806907ad35c966,spanId=e322665cb1916ba2,userId=NONE][0;39m [36mc.e.t.a.c.f.RequestLoggingFilter[0;39m [2m-[0;39m [HTTP-SLOW] GET /api/external/users/1/followings 200 - 2783ms
2026-08-03T13:45:06.205745656Z  [auth-service]  [2m2026-08-03 22:45:06[0;39m [2m[http-nio-8081-exec-4][0;39m [33m WARN [traceId=6a709b5f358d68fc3c806907ad35c966,spanId=e322665cb1916ba2,userId=NONE][0;39m [36mc.e.t.a.c.f.RequestLoggingFilter[0;39m [2m-[0;39m [HTTP-SLOW] GET /api/external/users/1/followings 200 - 2783ms
```

### 메트릭 시계열

| 쿼리 | series | 점 | min | max | last | 값이 0이던 구간 |
|---|---|---:|---:|---:|---:|---|
| `hikaricp_connections_active` | `{__name__=hikaricp_connections_active, application=auth-service, cluster=yogurtte-k3s-prod, container=auth-service, instance=10.42.1.44:8090, job=auth-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=auth-service-5999bb9f5c-qqrss, pool=HikariPool-1, service=auth-service}` | 65 | 0 | 0 | 0 | **2026-08-03T13:10:00Z ~ 2026-08-03T13:26:00Z** |
| `hikaricp_connections_active` | `{__name__=hikaricp_connections_active, application=auth-service, cluster=yogurtte-k3s-prod, container=auth-service, instance=10.42.1.45:8090, job=auth-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=auth-service-5999bb9f5c-lbpf2, pool=HikariPool-1, service=auth-service}` | 92 | 0 | 0 | 0 | **2026-08-03T13:28:00Z ~ 2026-08-03T13:50:45Z** |
| `hikaricp_connections_active` | `{__name__=hikaricp_connections_active, application=chat-service, cluster=yogurtte-k3s-prod, container=chat-service, instance=10.42.3.43:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-fdcc7c776-qrbc2, pool=HikariPool-1}` | 164 | 0 | 0 | 0 | **2026-08-03T13:10:00Z ~ 2026-08-03T13:50:45Z** |
| `hikaricp_connections_active` | `{__name__=hikaricp_connections_active, application=content-service, cluster=yogurtte-k3s-prod, container=content-service, instance=10.42.1.43:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-6995bb7d94-h2f6n, pool=HikariPool-1}` | 164 | 0 | 0 | 0 | **2026-08-03T13:10:00Z ~ 2026-08-03T13:50:45Z** |
| `hikaricp_connections_active` | `{__name__=hikaricp_connections_active, application=content-service, cluster=yogurtte-k3s-prod, container=content-service, instance=10.42.3.42:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-6995bb7d94-nq9l2, pool=HikariPool-1}` | 164 | 0 | 0 | 0 | **2026-08-03T13:10:00Z ~ 2026-08-03T13:50:45Z** |
| `hikaricp_connections_pending` | `{__name__=hikaricp_connections_pending, application=auth-service, cluster=yogurtte-k3s-prod, container=auth-service, instance=10.42.1.44:8090, job=auth-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=auth-service-5999bb9f5c-qqrss, pool=HikariPool-1, service=auth-service}` | 65 | 0 | 0 | 0 | **2026-08-03T13:10:00Z ~ 2026-08-03T13:26:00Z** |
| `hikaricp_connections_pending` | `{__name__=hikaricp_connections_pending, application=auth-service, cluster=yogurtte-k3s-prod, container=auth-service, instance=10.42.1.45:8090, job=auth-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=auth-service-5999bb9f5c-lbpf2, pool=HikariPool-1, service=auth-service}` | 92 | 0 | 0 | 0 | **2026-08-03T13:28:00Z ~ 2026-08-03T13:50:45Z** |
| `hikaricp_connections_pending` | `{__name__=hikaricp_connections_pending, application=chat-service, cluster=yogurtte-k3s-prod, container=chat-service, instance=10.42.3.43:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-fdcc7c776-qrbc2, pool=HikariPool-1}` | 164 | 0 | 0 | 0 | **2026-08-03T13:10:00Z ~ 2026-08-03T13:50:45Z** |
| `hikaricp_connections_pending` | `{__name__=hikaricp_connections_pending, application=content-service, cluster=yogurtte-k3s-prod, container=content-service, instance=10.42.1.43:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-6995bb7d94-h2f6n, pool=HikariPool-1}` | 164 | 0 | 0 | 0 | **2026-08-03T13:10:00Z ~ 2026-08-03T13:50:45Z** |
| `hikaricp_connections_pending` | `{__name__=hikaricp_connections_pending, application=content-service, cluster=yogurtte-k3s-prod, container=content-service, instance=10.42.3.42:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-6995bb7d94-nq9l2, pool=HikariPool-1}` | 164 | 0 | 0 | 0 | **2026-08-03T13:10:00Z ~ 2026-08-03T13:50:45Z** |
| `rate(jvm_gc_pause_seconds_sum[5m])` | `{action=end of major GC, application=chat-service, cause=Allocation Failure, cluster=yogurtte-k3s-prod, container=chat-service, gc=MarkSweepCompact, instance=10.42.3.43:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-fdcc7c776-qrbc2}` | 164 | 0 | 0 | 0 | **2026-08-03T13:10:00Z ~ 2026-08-03T13:50:45Z** |
| `rate(jvm_gc_pause_seconds_sum[5m])` | `{action=end of minor GC, application=auth-service, cause=G1 Evacuation Pause, cluster=yogurtte-k3s-prod, container=auth-service, gc=G1 Young Generation, instance=10.42.1.44:8090, job=auth-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=auth-service-5999bb9f5c-qqrss, service=auth-service}` | 77 | 0 | 0 | 0 | **2026-08-03T13:10:00Z ~ 2026-08-03T13:29:00Z** |
| `rate(jvm_gc_pause_seconds_sum[5m])` | `{action=end of minor GC, application=auth-service, cause=G1 Evacuation Pause, cluster=yogurtte-k3s-prod, container=auth-service, gc=G1 Young Generation, instance=10.42.1.45:8090, job=auth-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=auth-service-5999bb9f5c-lbpf2, service=auth-service}` | 88 | 0 | 0.000 | 0 | **2026-08-03T13:29:00Z ~ 2026-08-03T13:36:45Z, 2026-08-03T13:41:00Z ~ 2026-08-03T13:50:45Z** |
| `rate(jvm_gc_pause_seconds_sum[5m])` | `{action=end of minor GC, application=auth-service, cause=Metadata GC Threshold, cluster=yogurtte-k3s-prod, container=auth-service, gc=G1 Young Generation, instance=10.42.1.44:8090, job=auth-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=auth-service-5999bb9f5c-qqrss, service=auth-service}` | 77 | 0 | 0 | 0 | **2026-08-03T13:10:00Z ~ 2026-08-03T13:29:00Z** |
| `rate(jvm_gc_pause_seconds_sum[5m])` | `{action=end of minor GC, application=chat-service, cause=Allocation Failure, cluster=yogurtte-k3s-prod, container=chat-service, gc=Copy, instance=10.42.3.43:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-fdcc7c776-qrbc2}` | 164 | 0.000 | 0.000 | 0.000 | — |
| `rate(jvm_gc_pause_seconds_sum[5m])` | `{action=end of minor GC, application=content-service, cause=Allocation Failure, cluster=yogurtte-k3s-prod, container=content-service, gc=Copy, instance=10.42.1.43:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-6995bb7d94-h2f6n}` | 164 | 0 | 0.000 | 0 | **2026-08-03T13:10:00Z ~ 2026-08-03T13:11:45Z, 2026-08-03T13:16:00Z ~ 2026-08-03T13:27:45Z, 2026-08-03T13:32:00Z ~ 2026-08-03T13:44:45Z, 2026-08-03T13:49:00Z ~ 2026-08-03T13:50:45Z** |
| `rate(jvm_gc_pause_seconds_sum[5m])` | `{action=end of minor GC, application=content-service, cause=Allocation Failure, cluster=yogurtte-k3s-prod, container=content-service, gc=Copy, instance=10.42.3.42:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-6995bb7d94-nq9l2}` | 164 | 0 | 0.000 | 0.000 | **2026-08-03T13:11:45Z ~ 2026-08-03T13:21:30Z, 2026-08-03T13:25:45Z ~ 2026-08-03T13:36:30Z, 2026-08-03T13:40:45Z ~ 2026-08-03T13:50:30Z** |
| `up` | `{__name__=up, app=node-exporter, cluster=yogurtte-k3s-prod, component=metrics, container=node-exporter, instance=ip-172-31-40-241, job=integrations/node_exporter, k8s_cluster_name=yogurtte-k3s-prod, namespace=monitoring, pod=grafana-k8s-monitoring-node-exporter-cph5l, source=kubernetes, workload=DaemonSet/grafana-k8s-monitoring-node-exporter}` | 164 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, app=node-exporter, cluster=yogurtte-k3s-prod, component=metrics, container=node-exporter, instance=ip-172-31-45-39, job=integrations/node_exporter, k8s_cluster_name=yogurtte-k3s-prod, namespace=monitoring, pod=grafana-k8s-monitoring-node-exporter-9kvqt, source=kubernetes, workload=DaemonSet/grafana-k8s-monitoring-node-exporter}` | 164 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, cluster=yogurtte-k3s-prod, container=auth-service, instance=10.42.1.44:8090, job=auth-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=auth-service-5999bb9f5c-qqrss}` | 65 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, cluster=yogurtte-k3s-prod, container=auth-service, instance=10.42.1.45:8090, job=auth-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=auth-service-5999bb9f5c-lbpf2}` | 92 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, cluster=yogurtte-k3s-prod, container=chat-service, instance=10.42.3.43:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-fdcc7c776-qrbc2}` | 164 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, cluster=yogurtte-k3s-prod, container=content-service, instance=10.42.1.43:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-6995bb7d94-h2f6n}` | 164 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, cluster=yogurtte-k3s-prod, container=content-service, instance=10.42.3.42:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-6995bb7d94-nq9l2}` | 164 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, cluster=yogurtte-k3s-prod, instance=10.42.1.248:8080, job=integrations/kubernetes/kube-state-metrics, k8s_cluster_name=yogurtte-k3s-prod, source=kubernetes}` | 164 | 1 | 1 | 1 | — |
| `mongodb_up` | `{__name__=mongodb_up, cluster=yogurtte-k3s-prod, instance=infra-server, job=mongodb, k8s_cluster_name=yogurtte-k3s-prod}` | 164 | 1 | 1 | 1 | — |
| `kafka_brokers` | `{__name__=kafka_brokers, cluster=yogurtte-k3s-prod, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod}` | 164 | 1 | 1 | 1 | — |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=chat-service-fcm-tokens, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=0, topic=user.fcm-tokens}` | 164 | 0 | 0 | 0 | **2026-08-03T13:10:00Z ~ 2026-08-03T13:50:45Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=chat-service-fcm-tokens, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=1, topic=user.fcm-tokens}` | 164 | 0 | 0 | 0 | **2026-08-03T13:10:00Z ~ 2026-08-03T13:50:45Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=chat-service-fcm-tokens, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=2, topic=user.fcm-tokens}` | 164 | 0 | 0 | 0 | **2026-08-03T13:10:00Z ~ 2026-08-03T13:50:45Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=chat-service-notification-settings, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=0, topic=user.notification-settings}` | 164 | 0 | 0 | 0 | **2026-08-03T13:10:00Z ~ 2026-08-03T13:50:45Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=chat-service-notification-settings, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=1, topic=user.notification-settings}` | 164 | 0 | 0 | 0 | **2026-08-03T13:10:00Z ~ 2026-08-03T13:50:45Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=chat-service-notification-settings, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=2, topic=user.notification-settings}` | 164 | 0 | 0 | 0 | **2026-08-03T13:10:00Z ~ 2026-08-03T13:50:45Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=db-writer, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=0, topic=chat.messages}` | 164 | 0 | 0 | 0 | **2026-08-03T13:10:00Z ~ 2026-08-03T13:50:45Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=db-writer, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=1, topic=chat.messages}` | 164 | 0 | 0 | 0 | **2026-08-03T13:10:00Z ~ 2026-08-03T13:50:45Z** |
| `websocket_active_users` | `{__name__=websocket_active_users, application=chat-service, cluster=yogurtte-k3s-prod, container=chat-service, instance=10.42.3.43:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-fdcc7c776-qrbc2}` | 164 | 0 | 0 | 0 | **2026-08-03T13:10:00Z ~ 2026-08-03T13:50:45Z** |

값이 0이던 구간은 굵게 표시했다 — 프로세스가 사라져 시계열이 꺾인 것이 유일한 신호인 장애가 있다.

