# RCA Report — `scan-1785762900`

| 항목 | 값 |
|---|---|
| 모드 | rca |
| 질문 | 22시 45분쯤부터 앱이 자꾸 로그인 화면으로 튕긴다는 문의가 많아요 |
| 시각 | 2026-08-03T14:03:45.898306Z |
| provider | claude-cli |
| model | `claude-opus-5` · turns 1 |
| prompt | `./prompts/system-prompt.md` |
| tokens | in 133590 (cacheRead 18,133 · cacheCreate 115,455) / out 7791 · cost $1.4423 |
| elapsed | total 130233ms (tempo 1547 · loki 415 · mimir 647 · assemble 37 · llm 121346) |

## 탐색 (Triage)

| 항목 | 값 |
|---|---|
| 시간창 해석 | 절대 시각 '22시 45분' → 2026-08-03 22:45 Asia/Seoul ±30분 (추정 창) |
| 시간창 확신도 | APPROX |
| 스윕 창 | 2026-08-03T13:15:00Z ~ 2026-08-03T14:02:36.246028Z |
| 좁힌 창 | 2026-08-03T13:15:00Z ~ 2026-08-03T13:55:00Z |
| 대상 | auth-service, content-service |
| traceId | 6a709737e51278eeebf347141cc5c3f2 |
| 트레이스 후보 | 3건 |
| 장애 후보 | 9건 · 선택 INC-1, INC-6, INC-7, INC-8, INC-9 |
| 계획 파싱 | 성공 |
| 스윕 컨텍스트 | 후보 + 원본 (A) |
| prompt | `./prompts/triage-prompt.md` |
| tokens | in 46665 / out 4555 · cost $0.4558 |
| chars | 컨텍스트 43,854 + 프롬프트 1,399 = **45,253** |
| elapsed | survey 1374ms · llm 68234ms |

**선정 이유**: 증상 시각(13:45Z)에 정확히 걸리는 auth·content 신호(INC-7/8/9)를 축으로 하되, up 시계열에서 드러난 13:25Z auth 파드 교체와 그 직후의 로그인 지연·에러(INC-6/INC-1)가 같은 서비스의 선행 지문일 가능성이 커서 창을 합집합으로 넓혀 함께 본다.

**근거**

- INC-7: auth-service ERROR/WARN 1건 (13:45~13:50Z) — 문의 시각 22:45 KST와 정확히 일치하는 유일한 auth 신호
- INC-9: content-service http get /feeds/following 3,287ms (13:45:03.385679Z, slow 채널, traceId 6a709b5f358d68fc3c806907ad35c966) — serviceStats에 auth-service spanCount 8이 포함되어 토큰 검증 경로가 트레이스 안에 들어와 있음. 에러 status는 전부 unset이라 '실패'가 아니라 '지연'이 증상
- INC-8: content-service ERROR/WARN 3건 (13:45~13:50Z) — INC-9와 같은 5분 버킷·같은 서비스의 다른 지문이라 함께 봄
- min_over_time(up[5m]): auth-service-5999bb9f5c-qqrss(10.42.1.44)는 1785763800(13:25Z)까지만 존재하고, auth-service-5999bb9f5c-lbpf2(10.42.1.45)가 1785763800부터 등장 — 다운은 없지만 auth 파드가 교체됨. '평소 있던 시계열이 사라진 것'이 세션/JWT 서명키 무효화 후보의 근거
- INC-6: 파드 교체 직후 auth-service http post /login 3,509ms (13:27:19.919322Z, traceId 6a709737e51278eeebf347141cc5c3f2, auth-service 단독 4스팬) — 증상(로그인 화면) 그 자체의 지문
- INC-1: auth-service ERROR/WARN 13:15~13:20Z 5건 / 13:20~13:25Z 1건 / 13:25~13:30Z 4건 — 파드 교체 시각과 겹치는 auth 로그 급증
- Tempo 에러 검색은 전 구간 1건(content POST /feeds, 95ms)뿐 — 401/403 재로그인은 에러 트레이스로 안 잡히므로 트레이스 0건을 정상 근거로 쓰지 않음

**스윕이 찾은 트레이스** (고른 것은 6a709737e51278eeebf347141cc5c3f2)

| traceId | 채널 | root service | root span | ms |
|---|---|---|---|---:|
| `6a7095b09dbabfa223fd8e4c12fda927` | error | content-service | http post /feeds | 95 |
| `6a709b5f358d68fc3c806907ad35c966` | slow | content-service | http get /feeds/following | 3287 |
| `6a709737e51278eeebf347141cc5c3f2` ←선택 | slow | auth-service | http post /login | 3509 |

**장애 후보** (코드가 신호를 묶은 것 · 창은 여기서 계산됨)

## INC-1  auth-service  |  ERROR/WARN
- 구간: 2026-08-03T13:15:00Z ~ 2026-08-03T13:30:00Z  (LOKI · 집계 해상도만큼 흐림)
- ERROR/WARN 5건 (2026-08-03T13:15:00Z ~ 2026-08-03T13:20:00Z)
- ERROR/WARN 1건 (2026-08-03T13:20:00Z ~ 2026-08-03T13:25:00Z)
- ERROR/WARN 4건 (2026-08-03T13:25:00Z ~ 2026-08-03T13:30:00Z)
- 같은 시각의 다른 후보: INC-2, INC-3, INC-4, INC-5, INC-6  (인과 여부는 판단하지 않았다)

## INC-2  kafka  |  kafka_consumergroup_lag
- 구간: 2026-08-03T13:15:00Z ~ 2026-08-03T14:00:00Z  (MIMIR · 집계 해상도만큼 흐림)
- kafka_consumergroup_lag{consumergroup=chat-service-fcm-tokens, partition=0, topic=user.fcm-tokens} 가 0이었다 (2026-08-03T13:15:00Z ~ 2026-08-03T14:00:00Z)
- kafka_consumergroup_lag{consumergroup=chat-service-fcm-tokens, partition=1, topic=user.fcm-tokens} 가 0이었다 (2026-08-03T13:15:00Z ~ 2026-08-03T14:00:00Z)
- kafka_consumergroup_lag{consumergroup=chat-service-fcm-tokens, partition=2, topic=user.fcm-tokens} 가 0이었다 (2026-08-03T13:15:00Z ~ 2026-08-03T14:00:00Z)
- kafka_consumergroup_lag{consumergroup=chat-service-notification-settings, partition=0, topic=user.notification-settings} 가 0이었다 (2026-08-03T13:15:00Z ~ 2026-08-03T14:00:00Z)
- kafka_consumergroup_lag{consumergroup=chat-service-notification-settings, partition=1, topic=user.notification-settings} 가 0이었다 (2026-08-03T13:15:00Z ~ 2026-08-03T14:00:00Z)
- kafka_consumergroup_lag{consumergroup=chat-service-notification-settings, partition=2, topic=user.notification-settings} 가 0이었다 (2026-08-03T13:15:00Z ~ 2026-08-03T14:00:00Z)
- kafka_consumergroup_lag{consumergroup=db-writer, partition=0, topic=chat.messages} 가 0이었다 (2026-08-03T13:15:00Z ~ 2026-08-03T14:00:00Z)
- kafka_consumergroup_lag{consumergroup=db-writer, partition=1, topic=chat.messages} 가 0이었다 (2026-08-03T13:15:00Z ~ 2026-08-03T14:00:00Z)
- kafka_consumergroup_lag{consumergroup=db-writer, partition=11, topic=chat.messages} 가 0이었다 (2026-08-03T13:15:00Z ~ 2026-08-03T14:00:00Z)
- kafka_consumergroup_lag{consumergroup=db-writer, partition=3, topic=chat.messages} 가 0이었다 (2026-08-03T13:15:00Z ~ 2026-08-03T14:00:00Z)
- kafka_consumergroup_lag{consumergroup=db-writer, partition=4, topic=chat.messages} 가 0이었다 (2026-08-03T13:15:00Z ~ 2026-08-03T14:00:00Z)
- kafka_consumergroup_lag{consumergroup=db-writer, partition=5, topic=chat.messages} 가 0이었다 (2026-08-03T13:15:00Z ~ 2026-08-03T14:00:00Z)
- kafka_consumergroup_lag{consumergroup=db-writer, partition=7, topic=chat.messages} 가 0이었다 (2026-08-03T13:15:00Z ~ 2026-08-03T14:00:00Z)
- kafka_consumergroup_lag{consumergroup=db-writer, partition=8, topic=chat.messages} 가 0이었다 (2026-08-03T13:15:00Z ~ 2026-08-03T14:00:00Z)
- kafka_consumergroup_lag{consumergroup=db-writer-retry-1000, partition=0, topic=chat.messages-retry-1000} 가 0이었다 (2026-08-03T13:15:00Z ~ 2026-08-03T14:00:00Z)
- kafka_consumergroup_lag{consumergroup=db-writer-retry-2000, partition=0, topic=chat.messages-retry-2000} 가 0이었다 (2026-08-03T13:15:00Z ~ 2026-08-03T14:00:00Z)
- kafka_consumergroup_lag{consumergroup=db-writer-retry-4000, partition=0, topic=chat.messages-retry-4000} 가 0이었다 (2026-08-03T13:15:00Z ~ 2026-08-03T14:00:00Z)
- kafka_consumergroup_lag{consumergroup=notification, partition=0, topic=chat.messages} 가 0이었다 (2026-08-03T13:15:00Z ~ 2026-08-03T14:00:00Z)
- kafka_consumergroup_lag{consumergroup=notification, partition=1, topic=chat.messages} 가 0이었다 (2026-08-03T13:15:00Z ~ 2026-08-03T14:00:00Z)
- kafka_consumergroup_lag{consumergroup=notification, partition=11, topic=chat.messages} 가 0이었다 (2026-08-03T13:15:00Z ~ 2026-08-03T14:00:00Z)
- kafka_consumergroup_lag{consumergroup=notification, partition=2, topic=chat.messages} 가 0이었다 (2026-08-03T13:15:00Z ~ 2026-08-03T14:00:00Z)
- kafka_consumergroup_lag{consumergroup=notification, partition=3, topic=chat.messages} 가 0이었다 (2026-08-03T13:15:00Z ~ 2026-08-03T14:00:00Z)
- kafka_consumergroup_lag{consumergroup=notification, partition=4, topic=chat.messages} 가 0이었다 (2026-08-03T13:15:00Z ~ 2026-08-03T14:00:00Z)
- kafka_consumergroup_lag{consumergroup=notification, partition=5, topic=chat.messages} 가 0이었다 (2026-08-03T13:15:00Z ~ 2026-08-03T14:00:00Z)
- kafka_consumergroup_lag{consumergroup=notification, partition=7, topic=chat.messages} 가 0이었다 (2026-08-03T13:15:00Z ~ 2026-08-03T14:00:00Z)
- kafka_consumergroup_lag{consumergroup=notification, partition=8, topic=chat.messages} 가 0이었다 (2026-08-03T13:15:00Z ~ 2026-08-03T14:00:00Z)
- kafka_consumergroup_lag{consumergroup=notification-processors, partition=0, topic=user.notifications} 가 0이었다 (2026-08-03T13:15:00Z ~ 2026-08-03T14:00:00Z)
- kafka_consumergroup_lag{consumergroup=notification-processors, partition=1, topic=user.notifications} 가 0이었다 (2026-08-03T13:15:00Z ~ 2026-08-03T14:00:00Z)
- kafka_consumergroup_lag{consumergroup=notification-processors, partition=2, topic=user.notifications} 가 0이었다 (2026-08-03T13:15:00Z ~ 2026-08-03T14:00:00Z)
- kafka_consumergroup_lag{consumergroup=notification-processors, partition=3, topic=user.notifications} 가 0이었다 (2026-08-03T13:15:00Z ~ 2026-08-03T14:00:00Z)
- kafka_consumergroup_lag{consumergroup=notification-processors, partition=4, topic=user.notifications} 가 0이었다 (2026-08-03T13:15:00Z ~ 2026-08-03T14:00:00Z)
- kafka_consumergroup_lag{consumergroup=notification-processors, partition=5, topic=user.notifications} 가 0이었다 (2026-08-03T13:15:00Z ~ 2026-08-03T14:00:00Z)
- kafka_consumergroup_lag{consumergroup=notification-recovery, partition=0, topic=user.notifications.dlq} 가 0이었다 (2026-08-03T13:15:00Z ~ 2026-08-03T14:00:00Z)
- kafka_consumergroup_lag{consumergroup=notification-recovery, partition=2, topic=user.notifications.dlq} 가 0이었다 (2026-08-03T13:15:00Z ~ 2026-08-03T14:00:00Z)
- kafka_consumergroup_lag{consumergroup=notification-retry-2000, partition=0, topic=chat.messages-retry-2000} 가 0이었다 (2026-08-03T13:15:00Z ~ 2026-08-03T14:00:00Z)
- kafka_consumergroup_lag{consumergroup=notification-retry-4000, partition=0, topic=chat.messages-retry-4000} 가 0이었다 (2026-08-03T13:15:00Z ~ 2026-08-03T14:00:00Z)
- 같은 시각의 다른 후보: INC-1, INC-3, INC-4, INC-5, INC-6, INC-7, INC-8, INC-9  (인과 여부는 판단하지 않았다)

## INC-3  chat-service  |  websocket_active_users
- 구간: 2026-08-03T13:15:00Z ~ 2026-08-03T14:00:00Z  (MIMIR · 집계 해상도만큼 흐림)
- websocket_active_users{container=chat-service, namespace=default, pod=chat-service-fdcc7c776-qrbc2} 가 0이었다 (2026-08-03T13:15:00Z ~ 2026-08-03T14:00:00Z)
- 같은 시각의 다른 후보: INC-1, INC-2, INC-4, INC-5, INC-6, INC-7, INC-8, INC-9  (인과 여부는 판단하지 않았다)

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

**기각한 후보**

- INC-2 — kafka_consumergroup_lag가 전 파티션 0(또는 미할당 -1)으로 전 구간 평탄하고 kafka_brokers도 1 유지 — 소비 정체가 아니라 정상이며 로그인 세션 경로와 무관하다.
- INC-3 — websocket_active_users는 조회 창 시작 이전인 1785762900(13:10Z)부터 이미 0으로 평탄해 증상 시각에 변화가 없다 — 대량 로그아웃의 결과였다면 13:45Z 부근에 하강 전이가 보여야 한다.
- INC-4 — content-service ERROR/WARN 4건이 13:20~13:25Z에 국한되고 증상 시각과 20분 이상 벌어져 있으며, 인증 경로가 아닌 피드 쓰기 쪽 신호다.
- INC-5 — content-service POST /feeds 95ms짜리 단발 에러 트레이스(13:20:48Z)로, 지속 시간도 짧고 시각도 증상과 맞지 않아 로그인 튕김을 설명하지 못한다.

**보정 기록**

- 창을 후보 [INC-1, INC-6, INC-7, INC-8, INC-9] 의 신호 시각에서 계산했다 (2026-08-03T13:15:00Z ~ 2026-08-03T13:55:00Z)

## 수집 범위 (Coverage)

- **window**: 2026-08-03T13:15:00Z ~ 2026-08-03T13:55:00Z (2400s)
- **trace**: 82,057B / 87 spans
- **창 안 후보 트레이스**: 10건 / 82,057B
- **logs**: errwarn=108,802B · traceId=8,833B
- **metrics**: 8 수집 / 258,019B, 누락 [sum(rate(http_server_requests_seconds_count{application="content-service", status="401"}[1m]))]
- **context**: 224,231 chars (+ 시스템 프롬프트 576 chars)

### 토큰 축 (개선 지표)

| 단계 | 총 in | chars | 컨텍스트 토큰 |
|---|---:|---:|---:|
| 탐색 | 46,665 | 45,253 | 25,036 |
| 분석 | 133,590 | 224,807 | 111,961 |
| **합계** | **180,255** | | **136,997** |

- **overheadTokens 21,629 tok — 이 회차에 실측**(1자 프롬프트 프로브, 같은 명령·모델·샌드박스).
  호출마다 한 번씩 얹히므로 단계별로 뺀다. **다른 날 상수를 쓰지 않았다 = `█ 실측`.**
- contextTokens (count_tokens API): 측정 안 됨 — 구독 CLI 경로엔 API 키가 없다
- 비용은 **API 환산 추정치**다. 구독 계정으로 호출하므로 실제 청구액이 아니다.

## 수집 실패/누락

- Metric 'sum(rate(http_server_requests_seconds_count{application="content-service", status="401"}[1m]))' returned no series in this window; skipped.

---

## 1. 원인 후보 랭킹 (최대 3개)

1. **auth-service 재시작(22:26~22:28) 이후 JWT 서명 키 불일치** → content-service가 토큰 서명 검증에 실패해 401 반환 → 앱이 로그인 화면으로 튕김
2. **auth-service ↔ content-service 동기 호출 경로의 2.7~3.5초 지연** → 클라이언트 타임아웃/재인증 유발 (증상 설명은 가능하나 관측상 응답은 200)
3. **auth-service 팔로우 목록 NPE 500 (FollowCondition.limit)** — 시간·증상 모두 어긋나 사실상 배제

---

## 2. 후보별 근거

### 후보 1 — JWT 서명 검증 실패 (auth-service 재시작 후 시크릿 불일치)

**근거**
- 증상과 직접 일치하는 유일한 관측: content-service 로그
  `2026-08-03 22:46:09.005 ... c.e.t.app.auth.token.JwtParser - [auth-jwt] JWT 서명 검증 실패 — 위조 또는 시크릿 불일치`
  바로 다음 줄 `[HTTP] GET /api/feeds/following 401 - 6ms` (traceId=6a709ba13e460c51fd7ef4e80a4c9a21, pod content-service-6995bb7d94-h2f6n). 6ms에 끝난 401 = 인증 필터 단계에서 즉시 거부. 401이면 앱이 로그인 화면으로 보내는 전형적 흐름이고, 민원 시각(22:45~)과 1분 차이.
- auth-service가 사건 직전 교체됨: 메트릭상 `auth-service-5999bb9f5c-qqrss`는 13:15~13:26Z(22:15~22:26 KST)까지만, `auth-service-5999bb9f5c-lbpf2`는 13:28Z(22:28)부터 등장. 그 사이 스크레이프 공백.
- 새 pod의 부팅 로그가 그 구간에 그대로 남아 있음: 22:26:00 BeanPostProcessorChecker WARN, 22:26:20 hibernate.dialect WARN, 22:26:56 open-in-view WARN, 22:27:02~03 `DefaultSecurityFilterChain - Will secure ...` → 22:26~22:27 사이 애플리케이션 콜드 스타트 확정.
- 반면 content-service 두 pod(h2f6n, nq9l2)는 13:15~13:55Z 전 구간 161점 연속 → **재시작 없음**. 즉 검증 측 시크릿은 그대로, 발급 측만 새로 뜬 상태에서 서명 불일치가 관측됨.

**확신도: 중간**
(서명 실패 로그는 명확하지만 1건뿐이고, 재시작→민원 사이 18분 공백을 설명할 데이터가 없음. 401 메트릭 수집도 실패해 규모 확인 불가.)

**반증 데이터**
- pod 이름의 ReplicaSet 해시가 재시작 전후 모두 `5999bb9f5c`로 동일 → Deployment 파드 템플릿(=env로 주입된 시크릿)은 바뀌지 않았다는 뜻. 시크릿이 Deployment env 값이라면 이 후보와 배치된다(마운트된 Secret/ConfigMap 갱신 또는 런타임 생성이면 배치되지 않음).
- 재시작 직후 22:27:23 `POST /api/login 200`(3497ms) 성공 — 로그인 자체는 됨.
- 같은 시각대 22:45:03~06 `GET /api/feeds/following 200`(traceId 6a709b5f…)이 정상 통과 → 전면적 401이 아니라 일부만 실패.
- 수집 실패: `http_server_requests_seconds_count{application="content-service", status="401"}` 시리즈 없음 → 401이 언제부터 몇 건인지 확증 불가.
- 재시작 시각(22:26~28)과 민원 시작(22:45)의 18분 차이를 메우는 관측값(토큰 TTL, 갱신 주기)이 데이터에 없음 — **데이터 부족**.

---

### 후보 2 — auth-service 경유 호출의 초 단위 지연

**근거**
- auth-service: `[HTTP-SLOW] GET /api/external/users/1/followings 200 - 2783ms` (22:45:06, traceId 6a709b5f358d68fc3c806907ad35c966)
- content-service: `[HTTP-SLOW] GET /api/feeds/following 200 - 3287ms` (같은 트레이스, 22:45:06)
- 트레이스 전문상 content-service `connection`(HikariPool-1) span이 3275ms이고, 그 대부분이 자식 `http get → http://auth-service:8081/api/external/users/1/followings`(2719ms)에 소모됨. **DB 커넥션을 쥔 채 원격 호출**하는 구조.
- auth-service 쪽 `secured request` span은 2.78초인데 **자식 span이 하나도 없음** → 어디서 시간을 쓰는지 트레이스로 안 보임.
- 호출 그래프상 `content-service --service--> auth-service 4회 최대 2785.8ms`, `content-service --jdbc--> mysql 최대 3275.4ms`로 집약값도 일치.
- 로그인도 3497ms(`[HTTP-SLOW] POST /api/login 200 - 3497ms`, 22:27:23)로 느림.

**확신도: 낮음**
(지연은 확실하지만, 이것이 "로그인 화면으로 튕김"으로 이어졌다는 연결고리 — 클라이언트 타임아웃 설정·클라이언트 로그 — 가 데이터에 전혀 없음.)

**반증 데이터**
- 느린 요청 3건 모두 `status=200`, `outcome=SUCCESS`, `exception=none` — 서버가 인증 오류를 낸 적 없음.
- 자원 포화 근거 없음: `hikaricp_connections_active`·`hikaricp_connections_pending` 전 pod 전 구간 0, GC pause rate는 content 최대 2.9e-5, auth 최대 1.04e-4 수준, `up`은 전 대상 전 구간 1, Kafka lag 0, redis/mongodb up=1.
- 지연 자체는 22:27(로그인)에도 이미 있었음 → 22:45 시점의 변곡점으로 보기 어려움.

---

### 후보 3 — auth-service 팔로우 조회 NullPointerException

**근거**
- `java.lang.NullPointerException: Cannot invoke "java.lang.Integer.intValue()" because "this.size" is null`
  `at com.example.toyauth.app.user.controller.dto.FollowCondition$FollowingSearch.limit(FollowCondition.java:25)` → `[HTTP] GET /api/user/1/following 500 - 54ms` (22:19:18)
- 동일 패턴 `FollowCondition$FollowerSearch.limit(FollowCondition.java:45)` → `GET /api/user/1/followers 500 - 34ms` (22:19:18)

**확신도: 낮음** (실재하는 버그이나 이번 증상의 원인은 아님)

**반증 데이터**
- 발생 시각 22:19:18로 민원 시작(22:45)보다 26분 앞섬.
- 발생 pod가 이미 교체된 `auth-service-5999bb9f5c-qqrss`.
- HTTP 500이며 401이 아님 → 로그인 화면 리다이렉트를 유발하는 코드가 아님.
- 경로가 `/api/user/{id}/following(s)`로, 22:45 트래픽의 `/api/external/users/{userId}/followings`와 다른 엔드포인트.

**참고(원인 후보에서 제외):** content-service의 `Duplicate entry '160-177' for key 'tb_feed_hashtags.uk_feed_hashtag'` → `POST /api/feeds 500`(22:20:48, ExpGrantService.isDuplicate → FeedService.createFeed)는 피드 작성 실패 버그로, 시각(22:20)·증상(500) 모두 로그인 튕김과 무관하다.

---

## 3. 권장 다음 조치

**즉시 (후보 1 확증/기각)**
1. 두 서비스의 JWT 시크릿 실제 값 비교 — `kubectl exec` 로 auth-service/content-service 각 pod의 서명 키(env 또는 마운트된 Secret/ConfigMap) 해시를 뽑아 일치 여부 확인. 시크릿이 Deployment env인지, 마운트인지, 런타임 생성인지도 함께 확인(ReplicaSet 해시가 동일했다는 반증을 해소해야 함).
2. Loki 범위를 22:20~23:10으로 넓혀 `{container=~"content-service|auth-service"} |= "JWT 서명 검증 실패"` 및 `|= " 401 "` 을 조회 → 401의 **최초 발생 시각과 건수 곡선**을 뽑아 민원 시작 시각과 대조. (지금은 401 표본이 1건뿐이라 판단 근거가 얇다.)
3. 실패한 메트릭 재수집: `http_server_requests_seconds_count` 의 실제 라벨셋 확인(`status` vs `outcome`, `application` vs `job`) 후 auth·content 양쪽 401/500 rate를 다시 조회.

**원인 규명**
4. auth-service 재시작 사유 확인 — `kubectl describe pod auth-service-5999bb9f5c-lbpf2`, `kubectl get events --field-selector involvedObject.name=auth-service` (13:20~13:30Z), 이전 pod qqrss의 종료 사유(OOMKilled/Evicted/수동 재시작), `kubectl rollout history deployment/auth-service`.
5. 액세스 토큰 TTL·리프레시 정책 확인 → 재시작(22:27)과 민원 시작(22:45) 사이 18분이 토큰 만료 주기로 설명되는지 검증. 설명되면 후보 1의 확신도가 "높음"으로 올라간다.

**후보 2 관련**
6. auth-service `/api/external/users/{userId}/followings` 핸들러 내부 계측 추가 또는 지연 재현 중 스레드 덤프 확보 — 현재 `secured request` 2.78초 구간에 자식 span이 없어 병목 지점을 특정할 수 없다.
7. 클라이언트(앱)의 HTTP 타임아웃 값과 401/타임아웃 시 로그아웃 처리 로직 확인 — 3.3초 응답이 튕김으로 이어질 수 있는지 판단하려면 이 값이 필요하다.

**완화 조치**
8. 시크릿 불일치가 확인되면 양 서비스 시크릿을 통일한 뒤 content-service까지 함께 롤링 재배포하고, 기존 토큰 무효화 안내(재로그인 유도)를 병행.
9. 별건이지만 재발 중인 버그 2건 티켓화: `FollowCondition.limit()` NPE(size null 기본값 처리), `tb_feed_hashtags.uk_feed_hashtag` 중복 삽입(피드 생성 500).

**전제 명시:** 본 분석은 401 메트릭 수집 실패로 401의 발생 시점·규모를 확인하지 못한 상태에서, 로그 1건과 pod 교체 타이밍의 정합성에 근거한 것이다. 위 2번·3번 조회로 401 곡선이 22:45 전후에 상승하는 것이 확인되기 전까지는 후보 1도 확정으로 취급하지 않는 것이 맞다.

---

## 관측 증거 (Evidence)

> 이 조사가 실제로 본 값이다. 원본 응답 전체는 `reports/raw/scan-1785762900-*.json`에 있다.

### 호출 그래프 (트레이스에서 추출)

```
chat-service --db--> redis  1회  최대 0.5ms  [INFO]
content-service --db--> redis  10회  최대 16.6ms  [LRANGE, DEL, RPUSH, PEXPIRE, GET, SET, INFO]
content-service --jdbc--> mysql/content (HikariPool-1)  47회  최대 3275.4ms
    error: Duplicate entry '160-177' for key 'tb_feed_hashtags.uk_feed_hashtag'
    events: acquired, commit, rollback
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

### 로그 원문 (60 / 전체 680줄)

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
2026-08-03T13:19:18.452847036Z  [auth-service]  at org.springframework.dao.support.PersistenceExceptionTranslationInterceptor.invoke(PersistenceExceptionTranslationInterceptor.java:137)
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
2026-08-03T13:20:48.844594457Z  [content-service]  2026-08-03 22:20:48.844 [http-nio-8082-exec-5]  WARN [traceId=6a7095b09dbabfa223fd8e4c12fda927,spanId=1b97a1bfc4ad9320,userId=1] o.h.e.jdbc.spi.SqlExceptionHelper - SQL Error: 1062, SQLState: 23000
2026-08-03T13:20:48.844594457Z  [content-service]  2026-08-03 22:20:48.844 [http-nio-8082-exec-5]  WARN [traceId=6a7095b09dbabfa223fd8e4c12fda927,spanId=1b97a1bfc4ad9320,userId=1] o.h.e.jdbc.spi.SqlExceptionHelper - SQL Error: 1062, SQLState: 23000
2026-08-03T13:20:48.844612326Z  [content-service]  2026-08-03 22:20:48.844 [http-nio-8082-exec-5] ERROR [traceId=6a7095b09dbabfa223fd8e4c12fda927,spanId=1b97a1bfc4ad9320,userId=1] o.h.e.jdbc.spi.SqlExceptionHelper - Duplicate entry '160-177' for key 'tb_feed_hashtags.uk_feed_hashtag'
2026-08-03T13:20:48.844612326Z  [content-service]  2026-08-03 22:20:48.844 [http-nio-8082-exec-5] ERROR [traceId=6a7095b09dbabfa223fd8e4c12fda927,spanId=1b97a1bfc4ad9320,userId=1] o.h.e.jdbc.spi.SqlExceptionHelper - Duplicate entry '160-177' for key 'tb_feed_hashtags.uk_feed_hashtag'
2026-08-03T13:20:48.866047276Z  [content-service]  2026-08-03 22:20:48.850 [http-nio-8082-exec-5]  WARN [traceId=6a7095b09dbabfa223fd8e4c12fda927,spanId=1b97a1bfc4ad9320,userId=1] c.e.t.a.c.e.GlobalExceptionHandler - [api-error] handleAllException
2026-08-03T13:20:48.866047276Z  [content-service]  2026-08-03 22:20:48.850 [http-nio-8082-exec-5]  WARN [traceId=6a7095b09dbabfa223fd8e4c12fda927,spanId=1b97a1bfc4ad9320,userId=1] c.e.t.a.c.e.GlobalExceptionHandler - [api-error] handleAllException
2026-08-03T13:20:48.866068861Z  [content-service]  org.springframework.dao.DataIntegrityViolationException: could not execute statement [Duplicate entry '160-177' for key 'tb_feed_hashtags.uk_feed_hashtag'] [insert into tb_feed_hashtags (created_at,feed_id,hashtag_id,updated_at) values (?,?,?,?)]; SQL [insert into tb_feed_hashtags (created_at,feed_id,hashtag_id,updated_at) values (?,?,?,?)]; constraint [tb_feed_hashtags.uk_feed_hashtag]
2026-08-03T13:20:48.866072218Z  [content-service]  at org.springframework.orm.jpa.vendor.HibernateJpaDialect.convertHibernateAccessException(HibernateJpaDialect.java:290)
2026-08-03T13:20:48.866074118Z  [content-service]  at org.springframework.orm.jpa.vendor.HibernateJpaDialect.translateExceptionIfPossible(HibernateJpaDialect.java:241)
2026-08-03T13:20:48.866076205Z  [content-service]  at org.springframework.orm.jpa.AbstractEntityManagerFactoryBean.translateExceptionIfPossible(AbstractEntityManagerFactoryBean.java:560)
2026-08-03T13:20:48.866078347Z  [content-service]  at org.springframework.dao.support.ChainedPersistenceExceptionTranslator.translateExceptionIfPossible(ChainedPersistenceExceptionTranslator.java:61)
2026-08-03T13:20:48.866082458Z  [content-service]  at org.springframework.dao.support.PersistenceExceptionTranslationInterceptor.invoke(PersistenceExceptionTranslationInterceptor.java:160)
2026-08-03T13:20:48.866244439Z  [content-service]  at org.springframework.security.web.access.ExceptionTranslationFilter.doFilter(ExceptionTranslationFilter.java:126)
2026-08-03T13:20:48.866246025Z  [content-service]  at org.springframework.security.web.access.ExceptionTranslationFilter.doFilter(ExceptionTranslationFilter.java:120)
2026-08-03T13:20:48.866470782Z  [content-service]  Caused by: org.hibernate.exception.ConstraintViolationException: could not execute statement [Duplicate entry '160-177' for key 'tb_feed_hashtags.uk_feed_hashtag'] [insert into tb_feed_hashtags (created_at,feed_id,hashtag_id,updated_at) values (?,?,?,?)]
2026-08-03T13:20:48.866472404Z  [content-service]  at org.hibernate.dialect.MySQLDialect.lambda$buildSQLExceptionConversionDelegate$3(MySQLDialect.java:1245)
2026-08-03T13:20:48.866473933Z  [content-service]  at org.hibernate.exception.internal.StandardSQLExceptionConverter.convert(StandardSQLExceptionConverter.java:58)
2026-08-03T13:20:48.866475458Z  [content-service]  at org.hibernate.engine.jdbc.spi.SqlExceptionHelper.convert(SqlExceptionHelper.java:108)
2026-08-03T13:20:48.866578695Z  [content-service]  at org.springframework.dao.support.PersistenceExceptionTranslationInterceptor.invoke(PersistenceExceptionTranslationInterceptor.java:138)
2026-08-03T13:20:48.866582196Z  [content-service]  Caused by: java.sql.SQLIntegrityConstraintViolationException: Duplicate entry '160-177' for key 'tb_feed_hashtags.uk_feed_hashtag'
2026-08-03T13:20:48.866583828Z  [content-service]  at com.mysql.cj.jdbc.exceptions.SQLError.createSQLException(SQLError.java:109)
2026-08-03T13:20:48.866585400Z  [content-service]  at com.mysql.cj.jdbc.exceptions.SQLExceptionsMapping.translateException(SQLExceptionsMapping.java:114)
2026-08-03T13:20:48.866617691Z  [content-service]  2026-08-03 22:20:48.863 [http-nio-8082-exec-5] ERROR [traceId=6a7095b09dbabfa223fd8e4c12fda927,spanId=23fd8e4c12fda927,userId=NONE] c.e.t.a.c.f.RequestLoggingFilter - [HTTP] POST /api/feeds 500 - 94ms
2026-08-03T13:20:48.866617691Z  [content-service]  2026-08-03 22:20:48.863 [http-nio-8082-exec-5] ERROR [traceId=6a7095b09dbabfa223fd8e4c12fda927,spanId=23fd8e4c12fda927,userId=NONE] c.e.t.a.c.f.RequestLoggingFilter - [HTTP] POST /api/feeds 500 - 94ms
2026-08-03T13:26:00.630761787Z  [auth-service]  [2m2026-08-03 22:26:00[0;39m [2m[main][0;39m [33m WARN [traceId=NONE,spanId=NONE,userId=NONE][0;39m [36mo.s.c.s.PostProcessorRegistrationDelegate$BeanPostProcessorChecker[0;39m [2m-[0;39m Bean 'org.springframework.ws.config.annotation.DelegatingWsConfiguration' of type [org.springframework.ws.config.annotation.DelegatingWsConfiguration$$SpringCGLIB$$0] is not eligible for getting processed by all BeanPostProcessors (for example: not eligible for auto-proxying). The currently created BeanPostProcessor [annotationActionEndpointMapping] is declared through a non-static factory method on that class; consider declaring it as static instead.
2026-08-03T13:26:20.523951518Z  [auth-service]  [2m2026-08-03 22:26:20[0;39m [2m[main][0;39m [33m WARN [traceId=NONE,spanId=NONE,userId=NONE][0;39m [36morg.hibernate.orm.deprecation[0;39m [2m-[0;39m HHH90000025: MySQLDialect does not need to be specified explicitly using 'hibernate.dialect' (remove the property setting and it will be selected by default)
2026-08-03T13:26:56.112813998Z  [auth-service]  [2m2026-08-03 22:26:56[0;39m [2m[main][0;39m [33m WARN [traceId=NONE,spanId=NONE,userId=NONE][0;39m [36mo.s.b.a.o.j.JpaBaseConfiguration$JpaWebConfiguration[0;39m [2m-[0;39m spring.jpa.open-in-view is enabled by default. Therefore, database queries may be performed during view rendering. Explicitly configure spring.jpa.open-in-view to disable this warning
2026-08-03T13:27:02.909559949Z  [auth-service]  [2m2026-08-03 22:27:02[0;39m [2m[main][0;39m [32m INFO [traceId=NONE,spanId=NONE,userId=NONE][0;39m [36mo.s.s.web.DefaultSecurityFilterChain[0;39m [2m-[0;39m Will secure Or [Mvc [pattern='/api/external/**']] with [org.springframework.security.web.session.DisableEncodeUrlFilter@b11d2ad, org.springframework.security.web.context.request.async.WebAsyncManagerIntegrationFilter@5ce1ed2f, org.springframework.security.web.context.SecurityContextHolderFilter@5ef5455c, org.springframework.security.web.header.HeaderWriterFilter@4e01cba8, org.springframework.web.filter.CorsFilter@612a0d49, org.springframework.security.web.authentication.logout.LogoutFilter@31d29626, com.example.toyauth.app.common.filter.ExternalAuthenticationFilter@2c66be14, org.springframework.security.web.savedrequest.RequestCacheAwareFilter@579325f2, org.springframework.security.web.servletapi.SecurityContextHolderAwareRequestFilter@4ddb882, org.springframework.security.web.authentication.AnonymousAuthenticationFilter@268fca0a, org.springframework.security.web.session.SessionManagementFilter@683ba3c1, org.springframework.security.web.access.ExceptionTranslationFilter@37d4b676, org.springframework.security.web.access.intercept.AuthorizationFilter@7c47ae7d]
2026-08-03T13:27:03.227490147Z  [auth-service]  [2m2026-08-03 22:27:03[0;39m [2m[main][0;39m [32m INFO [traceId=NONE,spanId=NONE,userId=NONE][0;39m [36mo.s.s.web.DefaultSecurityFilterChain[0;39m [2m-[0;39m Will secure any request with [org.springframework.security.web.session.DisableEncodeUrlFilter@6c81bd6d, org.springframework.security.web.context.request.async.WebAsyncManagerIntegrationFilter@21eeb0ce, org.springframework.security.web.context.SecurityContextHolderFilter@37e5111b, org.springframework.security.web.header.HeaderWriterFilter@1d2414a1, org.springframework.web.filter.CorsFilter@29af0d3b, org.springframework.security.web.authentication.logout.LogoutFilter@7b79305c, org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestRedirectFilter@57d354fc, org.springframework.security.oauth2.client.web.OAuth2LoginAuthenticationFilter@76700647, com.example.toyauth.app.common.filter.JwtFilter@27777afd, org.springframework.security.web.authentication.ui.DefaultLoginPageGeneratingFilter@44067808, org.springframework.security.web.authentication.ui.DefaultLogoutPageGeneratingFilter@15682e7f, org.springframework.security.web.savedrequest.RequestCacheAwareFilter@2006249d, org.springframework.security.web.servletapi.SecurityContextHolderAwareRequestFilter@1d3aad20, org.springframework.security.web.authentication.AnonymousAuthenticationFilter@74e3836a, org.springframework.security.web.session.SessionManagementFilter@1f78d3bc, org.springframework.security.web.access.ExceptionTranslationFilter@7a0daf3d]
2026-08-03T13:27:23.421814622Z  [auth-service]  [2m2026-08-03 22:27:23[0;39m [2m[http-nio-8081-exec-1][0;39m [33m WARN [traceId=6a709737e51278eeebf347141cc5c3f2,spanId=ebf347141cc5c3f2,userId=NONE][0;39m [36mc.e.t.a.c.f.RequestLoggingFilter[0;39m [2m-[0;39m [HTTP-SLOW] POST /api/login 200 - 3497ms
2026-08-03T13:27:23.421814622Z  [auth-service]  [2m2026-08-03 22:27:23[0;39m [2m[http-nio-8081-exec-1][0;39m [33m WARN [traceId=6a709737e51278eeebf347141cc5c3f2,spanId=ebf347141cc5c3f2,userId=NONE][0;39m [36mc.e.t.a.c.f.RequestLoggingFilter[0;39m [2m-[0;39m [HTTP-SLOW] POST /api/login 200 - 3497ms
2026-08-03T13:45:06.205745656Z  [auth-service]  [2m2026-08-03 22:45:06[0;39m [2m[http-nio-8081-exec-4][0;39m [33m WARN [traceId=6a709b5f358d68fc3c806907ad35c966,spanId=e322665cb1916ba2,userId=NONE][0;39m [36mc.e.t.a.c.f.RequestLoggingFilter[0;39m [2m-[0;39m [HTTP-SLOW] GET /api/external/users/1/followings 200 - 2783ms
2026-08-03T13:45:06.205745656Z  [auth-service]  [2m2026-08-03 22:45:06[0;39m [2m[http-nio-8081-exec-4][0;39m [33m WARN [traceId=6a709b5f358d68fc3c806907ad35c966,spanId=e322665cb1916ba2,userId=NONE][0;39m [36mc.e.t.a.c.f.RequestLoggingFilter[0;39m [2m-[0;39m [HTTP-SLOW] GET /api/external/users/1/followings 200 - 2783ms
2026-08-03T13:45:06.672266371Z  [content-service]  2026-08-03 22:45:06.672 [http-nio-8082-exec-3]  WARN [traceId=6a709b5f358d68fc3c806907ad35c966,spanId=3c806907ad35c966,userId=NONE] c.e.t.a.c.f.RequestLoggingFilter - [HTTP-SLOW] GET /api/feeds/following 200 - 3287ms
2026-08-03T13:45:06.672266371Z  [content-service]  2026-08-03 22:45:06.672 [http-nio-8082-exec-3]  WARN [traceId=6a709b5f358d68fc3c806907ad35c966,spanId=3c806907ad35c966,userId=NONE] c.e.t.a.c.f.RequestLoggingFilter - [HTTP-SLOW] GET /api/feeds/following 200 - 3287ms
2026-08-03T13:46:09.005784816Z  [content-service]  2026-08-03 22:46:09.005 [http-nio-8082-exec-1]  WARN [traceId=6a709ba13e460c51fd7ef4e80a4c9a21,spanId=23ac3cbe583b1fc7,userId=NONE] c.e.t.app.auth.token.JwtParser - [auth-jwt] JWT 서명 검증 실패 — 위조 또는 시크릿 불일치
2026-08-03T13:46:09.009200602Z  [content-service]  2026-08-03 22:46:09.008 [http-nio-8082-exec-1]  WARN [traceId=6a709ba13e460c51fd7ef4e80a4c9a21,spanId=fd7ef4e80a4c9a21,userId=NONE] c.e.t.a.c.f.RequestLoggingFilter - [HTTP] GET /api/feeds/following 401 - 6ms
```

### 메트릭 시계열

| 쿼리 | series | 점 | min | max | last | 값이 0이던 구간 |
|---|---|---:|---:|---:|---:|---|
| `hikaricp_connections_active` | `{__name__=hikaricp_connections_active, application=auth-service, cluster=yogurtte-k3s-prod, container=auth-service, instance=10.42.1.44:8090, job=auth-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=auth-service-5999bb9f5c-qqrss, pool=HikariPool-1, service=auth-service}` | 45 | 0 | 0 | 0 | **2026-08-03T13:15:00Z ~ 2026-08-03T13:26:00Z** |
| `hikaricp_connections_active` | `{__name__=hikaricp_connections_active, application=auth-service, cluster=yogurtte-k3s-prod, container=auth-service, instance=10.42.1.45:8090, job=auth-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=auth-service-5999bb9f5c-lbpf2, pool=HikariPool-1, service=auth-service}` | 109 | 0 | 0 | 0 | **2026-08-03T13:28:00Z ~ 2026-08-03T13:55:00Z** |
| `hikaricp_connections_active` | `{__name__=hikaricp_connections_active, application=chat-service, cluster=yogurtte-k3s-prod, container=chat-service, instance=10.42.3.43:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-fdcc7c776-qrbc2, pool=HikariPool-1}` | 161 | 0 | 0 | 0 | **2026-08-03T13:15:00Z ~ 2026-08-03T13:55:00Z** |
| `hikaricp_connections_active` | `{__name__=hikaricp_connections_active, application=content-service, cluster=yogurtte-k3s-prod, container=content-service, instance=10.42.1.43:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-6995bb7d94-h2f6n, pool=HikariPool-1}` | 161 | 0 | 0 | 0 | **2026-08-03T13:15:00Z ~ 2026-08-03T13:55:00Z** |
| `hikaricp_connections_active` | `{__name__=hikaricp_connections_active, application=content-service, cluster=yogurtte-k3s-prod, container=content-service, instance=10.42.3.42:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-6995bb7d94-nq9l2, pool=HikariPool-1}` | 161 | 0 | 0 | 0 | **2026-08-03T13:15:00Z ~ 2026-08-03T13:55:00Z** |
| `hikaricp_connections_pending` | `{__name__=hikaricp_connections_pending, application=auth-service, cluster=yogurtte-k3s-prod, container=auth-service, instance=10.42.1.44:8090, job=auth-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=auth-service-5999bb9f5c-qqrss, pool=HikariPool-1, service=auth-service}` | 45 | 0 | 0 | 0 | **2026-08-03T13:15:00Z ~ 2026-08-03T13:26:00Z** |
| `hikaricp_connections_pending` | `{__name__=hikaricp_connections_pending, application=auth-service, cluster=yogurtte-k3s-prod, container=auth-service, instance=10.42.1.45:8090, job=auth-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=auth-service-5999bb9f5c-lbpf2, pool=HikariPool-1, service=auth-service}` | 109 | 0 | 0 | 0 | **2026-08-03T13:28:00Z ~ 2026-08-03T13:55:00Z** |
| `hikaricp_connections_pending` | `{__name__=hikaricp_connections_pending, application=chat-service, cluster=yogurtte-k3s-prod, container=chat-service, instance=10.42.3.43:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-fdcc7c776-qrbc2, pool=HikariPool-1}` | 161 | 0 | 0 | 0 | **2026-08-03T13:15:00Z ~ 2026-08-03T13:55:00Z** |
| `hikaricp_connections_pending` | `{__name__=hikaricp_connections_pending, application=content-service, cluster=yogurtte-k3s-prod, container=content-service, instance=10.42.1.43:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-6995bb7d94-h2f6n, pool=HikariPool-1}` | 161 | 0 | 0 | 0 | **2026-08-03T13:15:00Z ~ 2026-08-03T13:55:00Z** |
| `hikaricp_connections_pending` | `{__name__=hikaricp_connections_pending, application=content-service, cluster=yogurtte-k3s-prod, container=content-service, instance=10.42.3.42:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-6995bb7d94-nq9l2, pool=HikariPool-1}` | 161 | 0 | 0 | 0 | **2026-08-03T13:15:00Z ~ 2026-08-03T13:55:00Z** |
| `rate(jvm_gc_pause_seconds_sum[5m])` | `{action=end of major GC, application=chat-service, cause=Allocation Failure, cluster=yogurtte-k3s-prod, container=chat-service, gc=MarkSweepCompact, instance=10.42.3.43:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-fdcc7c776-qrbc2}` | 161 | 0 | 0 | 0 | **2026-08-03T13:15:00Z ~ 2026-08-03T13:55:00Z** |
| `rate(jvm_gc_pause_seconds_sum[5m])` | `{action=end of minor GC, application=auth-service, cause=G1 Evacuation Pause, cluster=yogurtte-k3s-prod, container=auth-service, gc=G1 Young Generation, instance=10.42.1.44:8090, job=auth-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=auth-service-5999bb9f5c-qqrss, service=auth-service}` | 57 | 0 | 0 | 0 | **2026-08-03T13:15:00Z ~ 2026-08-03T13:29:00Z** |
| `rate(jvm_gc_pause_seconds_sum[5m])` | `{action=end of minor GC, application=auth-service, cause=G1 Evacuation Pause, cluster=yogurtte-k3s-prod, container=auth-service, gc=G1 Young Generation, instance=10.42.1.45:8090, job=auth-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=auth-service-5999bb9f5c-lbpf2, service=auth-service}` | 105 | 0 | 0.000 | 0 | **2026-08-03T13:29:00Z ~ 2026-08-03T13:36:45Z, 2026-08-03T13:41:00Z ~ 2026-08-03T13:55:00Z** |
| `rate(jvm_gc_pause_seconds_sum[5m])` | `{action=end of minor GC, application=auth-service, cause=Metadata GC Threshold, cluster=yogurtte-k3s-prod, container=auth-service, gc=G1 Young Generation, instance=10.42.1.44:8090, job=auth-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=auth-service-5999bb9f5c-qqrss, service=auth-service}` | 57 | 0 | 0 | 0 | **2026-08-03T13:15:00Z ~ 2026-08-03T13:29:00Z** |
| `rate(jvm_gc_pause_seconds_sum[5m])` | `{action=end of minor GC, application=chat-service, cause=Allocation Failure, cluster=yogurtte-k3s-prod, container=chat-service, gc=Copy, instance=10.42.3.43:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-fdcc7c776-qrbc2}` | 161 | 0.000 | 0.000 | 0.000 | — |
| `rate(jvm_gc_pause_seconds_sum[5m])` | `{action=end of minor GC, application=content-service, cause=Allocation Failure, cluster=yogurtte-k3s-prod, container=content-service, gc=Copy, instance=10.42.1.43:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-6995bb7d94-h2f6n}` | 161 | 0 | 0.000 | 0 | **2026-08-03T13:16:00Z ~ 2026-08-03T13:27:45Z, 2026-08-03T13:32:00Z ~ 2026-08-03T13:44:45Z, 2026-08-03T13:49:00Z ~ 2026-08-03T13:55:00Z** |
| `rate(jvm_gc_pause_seconds_sum[5m])` | `{action=end of minor GC, application=content-service, cause=Allocation Failure, cluster=yogurtte-k3s-prod, container=content-service, gc=Copy, instance=10.42.3.42:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-6995bb7d94-nq9l2}` | 161 | 0 | 0.000 | 0 | **2026-08-03T13:15:00Z ~ 2026-08-03T13:21:30Z, 2026-08-03T13:25:45Z ~ 2026-08-03T13:36:30Z, 2026-08-03T13:40:45Z ~ 2026-08-03T13:50:30Z, 2026-08-03T13:54:45Z ~ 2026-08-03T13:55:00Z** |
| `up` | `{__name__=up, app=node-exporter, cluster=yogurtte-k3s-prod, component=metrics, container=node-exporter, instance=ip-172-31-40-241, job=integrations/node_exporter, k8s_cluster_name=yogurtte-k3s-prod, namespace=monitoring, pod=grafana-k8s-monitoring-node-exporter-cph5l, source=kubernetes, workload=DaemonSet/grafana-k8s-monitoring-node-exporter}` | 161 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, app=node-exporter, cluster=yogurtte-k3s-prod, component=metrics, container=node-exporter, instance=ip-172-31-45-39, job=integrations/node_exporter, k8s_cluster_name=yogurtte-k3s-prod, namespace=monitoring, pod=grafana-k8s-monitoring-node-exporter-9kvqt, source=kubernetes, workload=DaemonSet/grafana-k8s-monitoring-node-exporter}` | 161 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, cluster=yogurtte-k3s-prod, container=auth-service, instance=10.42.1.44:8090, job=auth-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=auth-service-5999bb9f5c-qqrss}` | 45 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, cluster=yogurtte-k3s-prod, container=auth-service, instance=10.42.1.45:8090, job=auth-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=auth-service-5999bb9f5c-lbpf2}` | 109 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, cluster=yogurtte-k3s-prod, container=chat-service, instance=10.42.3.43:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-fdcc7c776-qrbc2}` | 161 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, cluster=yogurtte-k3s-prod, container=content-service, instance=10.42.1.43:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-6995bb7d94-h2f6n}` | 161 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, cluster=yogurtte-k3s-prod, container=content-service, instance=10.42.3.42:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-6995bb7d94-nq9l2}` | 161 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, cluster=yogurtte-k3s-prod, instance=10.42.1.248:8080, job=integrations/kubernetes/kube-state-metrics, k8s_cluster_name=yogurtte-k3s-prod, source=kubernetes}` | 161 | 1 | 1 | 1 | — |
| `mongodb_up` | `{__name__=mongodb_up, cluster=yogurtte-k3s-prod, instance=infra-server, job=mongodb, k8s_cluster_name=yogurtte-k3s-prod}` | 161 | 1 | 1 | 1 | — |
| `kafka_brokers` | `{__name__=kafka_brokers, cluster=yogurtte-k3s-prod, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod}` | 161 | 1 | 1 | 1 | — |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=chat-service-fcm-tokens, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=0, topic=user.fcm-tokens}` | 161 | 0 | 0 | 0 | **2026-08-03T13:15:00Z ~ 2026-08-03T13:55:00Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=chat-service-fcm-tokens, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=1, topic=user.fcm-tokens}` | 161 | 0 | 0 | 0 | **2026-08-03T13:15:00Z ~ 2026-08-03T13:55:00Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=chat-service-fcm-tokens, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=2, topic=user.fcm-tokens}` | 161 | 0 | 0 | 0 | **2026-08-03T13:15:00Z ~ 2026-08-03T13:55:00Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=chat-service-notification-settings, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=0, topic=user.notification-settings}` | 161 | 0 | 0 | 0 | **2026-08-03T13:15:00Z ~ 2026-08-03T13:55:00Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=chat-service-notification-settings, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=1, topic=user.notification-settings}` | 161 | 0 | 0 | 0 | **2026-08-03T13:15:00Z ~ 2026-08-03T13:55:00Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=chat-service-notification-settings, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=2, topic=user.notification-settings}` | 161 | 0 | 0 | 0 | **2026-08-03T13:15:00Z ~ 2026-08-03T13:55:00Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=db-writer, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=0, topic=chat.messages}` | 161 | 0 | 0 | 0 | **2026-08-03T13:15:00Z ~ 2026-08-03T13:55:00Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=db-writer, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=1, topic=chat.messages}` | 161 | 0 | 0 | 0 | **2026-08-03T13:15:00Z ~ 2026-08-03T13:55:00Z** |
| `websocket_active_users` | `{__name__=websocket_active_users, application=chat-service, cluster=yogurtte-k3s-prod, container=chat-service, instance=10.42.3.43:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-fdcc7c776-qrbc2}` | 161 | 0 | 0 | 0 | **2026-08-03T13:15:00Z ~ 2026-08-03T13:55:00Z** |

값이 0이던 구간은 굵게 표시했다 — 프로세스가 사라져 시계열이 꺾인 것이 유일한 신호인 장애가 있다.

