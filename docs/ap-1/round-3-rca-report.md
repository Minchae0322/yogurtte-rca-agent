# RCA Report — `scan-1785738600`

| 항목 | 값 |
|---|---|
| 모드 | rca |
| 질문 | 최근 1시간 안에 댓글 작성이 실패했다는 제보가 있다. 원인을 조사해줘 |
| 시각 | 2026-08-03T13:14:15.978Z |
| provider | claude-cli |
| model | `claude-opus-5` · turns 1 |
| prompt | `./prompts/system-prompt.md` |
| tokens | in 98846 (cacheRead 18,133 · cacheCreate 80,711) / out 6540 · cost $1.0391 |
| elapsed | total 109563ms (tempo 2744 · loki 410 · mimir 625 · assemble 27 · llm 99344) |

## 탐색 (Triage)

| 항목 | 값 |
|---|---|
| 시간창 해석 | 명시적 from/to |
| 시간창 확신도 | EXACT |
| 스윕 창 | 2026-08-03T05:41:15Z ~ 2026-08-03T06:41:15Z |
| 좁힌 창 | 2026-08-03T06:30:00Z ~ 2026-08-03T06:41:15Z |
| 대상 | content-service, auth-service, chat-service |
| traceId | 6a7037546e3404569c632181052c8516 |
| 트레이스 후보 | 2건 |
| 장애 후보 | 5건 · 선택 INC-3, INC-4, INC-5 |
| 계획 파싱 | 성공 |
| 스윕 컨텍스트 | 후보 + 원본 (A) |
| prompt | `./prompts/triage-prompt.md` |
| tokens | in 47382 / out 2259 · cost $0.4060 |
| chars | 컨텍스트 45,221 + 프롬프트 1,399 = **46,620** |
| elapsed | survey 1249ms · llm 36907ms |

**선정 이유**: 질문의 증상(댓글 작성 실패)과 엔드포인트·시각이 정확히 일치하는 트레이스 지문(INC-5)을 축으로 잡고, 같은 5분 창에 찍힌 content-service·auth-service 로그 버스트(INC-4, INC-3)를 그 실패의 서버측 사유를 담고 있을 후보로 함께 묶었다.

**근거**

- INC-5: content-service http post /feeds/{feedId}/comments 가 error 채널에 308ms로 1건 (traceId 6a7037601a12bc2de1bbb08457bac9d2, serviceStats content-service errorCount=1) — 제보된 '댓글 작성 실패'와 엔드포인트가 정확히 일치
- INC-5: 같은 엔드포인트가 slow 채널에 11,643ms (traceId 6a7037546e3404569c632181052c8516), 내부에 9,805ms·9,597ms·9,346ms 중첩 스팬 3개가 status=unset으로 존재 — 에러 없이 타임아웃 직전까지 밀린 요청
- INC-5 지연 트레이스의 serviceStats: content-service 28스팬 / chat-service 14스팬 / auth-service 4스팬 — 지연이 단일 서비스가 아니라 호출 체인을 가로지름
- INC-4: content-service ERROR/WARN 4건 (06:35:00~06:40:00Z), 트레이스 발생 시각 06:38:12~06:38:25Z를 정확히 포함하는 유일한 로그 버스트
- INC-3: auth-service ERROR/WARN 1건 (06:35:00~06:40:00Z), 동일 5분 버킷이며 auth-service가 위 지연 트레이스의 참여 서비스이므로 토큰 검증 경로 확인 필요
- 인프라 배제 근거: min_over_time(up[5m])·mongodb_up·kafka_brokers 모두 창 전체에서 1로 평탄, content-service 파드 2개(10.42.1.43, 10.42.3.42) 모두 up 유지 — 프로세스 사망이나 스크레이프 단절은 아님

**스윕이 찾은 트레이스** (고른 것은 6a7037546e3404569c632181052c8516)

| traceId | 채널 | root service | root span | ms |
|---|---|---|---|---:|
| `6a7037601a12bc2de1bbb08457bac9d2` | error | content-service | http post /feeds/{feedId}/comments | 308 |
| `6a7037546e3404569c632181052c8516` ←선택 | slow | content-service | http post /feeds/{feedId}/comments | 11643 |

**장애 후보** (코드가 신호를 묶은 것 · 창은 여기서 계산됨)

## INC-1  kafka  |  kafka_consumergroup_lag
- 구간: 2026-08-03T05:41:15Z ~ 2026-08-03T06:41:15Z  (MIMIR · 집계 해상도만큼 흐림)
- kafka_consumergroup_lag{consumergroup=chat-service-fcm-tokens, partition=0, topic=user.fcm-tokens} 가 0이었다 (2026-08-03T05:41:15Z ~ 2026-08-03T06:41:15Z)
- kafka_consumergroup_lag{consumergroup=chat-service-fcm-tokens, partition=1, topic=user.fcm-tokens} 가 0이었다 (2026-08-03T05:41:15Z ~ 2026-08-03T06:41:15Z)
- kafka_consumergroup_lag{consumergroup=chat-service-fcm-tokens, partition=2, topic=user.fcm-tokens} 가 0이었다 (2026-08-03T05:41:15Z ~ 2026-08-03T06:41:15Z)
- kafka_consumergroup_lag{consumergroup=chat-service-notification-settings, partition=0, topic=user.notification-settings} 가 0이었다 (2026-08-03T05:41:15Z ~ 2026-08-03T06:41:15Z)
- kafka_consumergroup_lag{consumergroup=chat-service-notification-settings, partition=1, topic=user.notification-settings} 가 0이었다 (2026-08-03T05:41:15Z ~ 2026-08-03T06:41:15Z)
- kafka_consumergroup_lag{consumergroup=chat-service-notification-settings, partition=2, topic=user.notification-settings} 가 0이었다 (2026-08-03T05:41:15Z ~ 2026-08-03T06:41:15Z)
- kafka_consumergroup_lag{consumergroup=db-writer, partition=0, topic=chat.messages} 가 0이었다 (2026-08-03T05:41:15Z ~ 2026-08-03T06:41:15Z)
- kafka_consumergroup_lag{consumergroup=db-writer, partition=1, topic=chat.messages} 가 0이었다 (2026-08-03T05:41:15Z ~ 2026-08-03T06:41:15Z)
- kafka_consumergroup_lag{consumergroup=db-writer, partition=11, topic=chat.messages} 가 0이었다 (2026-08-03T05:41:15Z ~ 2026-08-03T06:41:15Z)
- kafka_consumergroup_lag{consumergroup=db-writer, partition=3, topic=chat.messages} 가 0이었다 (2026-08-03T05:41:15Z ~ 2026-08-03T06:41:15Z)
- kafka_consumergroup_lag{consumergroup=db-writer, partition=4, topic=chat.messages} 가 0이었다 (2026-08-03T05:41:15Z ~ 2026-08-03T06:41:15Z)
- kafka_consumergroup_lag{consumergroup=db-writer, partition=5, topic=chat.messages} 가 0이었다 (2026-08-03T05:41:15Z ~ 2026-08-03T06:41:15Z)
- kafka_consumergroup_lag{consumergroup=db-writer, partition=7, topic=chat.messages} 가 0이었다 (2026-08-03T05:41:15Z ~ 2026-08-03T06:41:15Z)
- kafka_consumergroup_lag{consumergroup=db-writer, partition=8, topic=chat.messages} 가 0이었다 (2026-08-03T05:41:15Z ~ 2026-08-03T06:41:15Z)
- kafka_consumergroup_lag{consumergroup=db-writer-retry-1000, partition=0, topic=chat.messages-retry-1000} 가 0이었다 (2026-08-03T05:41:15Z ~ 2026-08-03T06:41:15Z)
- kafka_consumergroup_lag{consumergroup=db-writer-retry-2000, partition=0, topic=chat.messages-retry-2000} 가 0이었다 (2026-08-03T05:41:15Z ~ 2026-08-03T06:41:15Z)
- kafka_consumergroup_lag{consumergroup=db-writer-retry-4000, partition=0, topic=chat.messages-retry-4000} 가 0이었다 (2026-08-03T05:41:15Z ~ 2026-08-03T06:41:15Z)
- kafka_consumergroup_lag{consumergroup=notification, partition=0, topic=chat.messages} 가 0이었다 (2026-08-03T05:41:15Z ~ 2026-08-03T06:41:15Z)
- kafka_consumergroup_lag{consumergroup=notification, partition=1, topic=chat.messages} 가 0이었다 (2026-08-03T05:41:15Z ~ 2026-08-03T06:41:15Z)
- kafka_consumergroup_lag{consumergroup=notification, partition=11, topic=chat.messages} 가 0이었다 (2026-08-03T05:41:15Z ~ 2026-08-03T06:41:15Z)
- kafka_consumergroup_lag{consumergroup=notification, partition=2, topic=chat.messages} 가 0이었다 (2026-08-03T05:41:15Z ~ 2026-08-03T06:41:15Z)
- kafka_consumergroup_lag{consumergroup=notification, partition=3, topic=chat.messages} 가 0이었다 (2026-08-03T05:41:15Z ~ 2026-08-03T06:41:15Z)
- kafka_consumergroup_lag{consumergroup=notification, partition=4, topic=chat.messages} 가 0이었다 (2026-08-03T05:41:15Z ~ 2026-08-03T06:41:15Z)
- kafka_consumergroup_lag{consumergroup=notification, partition=5, topic=chat.messages} 가 0이었다 (2026-08-03T05:41:15Z ~ 2026-08-03T06:41:15Z)
- kafka_consumergroup_lag{consumergroup=notification, partition=7, topic=chat.messages} 가 0이었다 (2026-08-03T05:41:15Z ~ 2026-08-03T06:41:15Z)
- kafka_consumergroup_lag{consumergroup=notification, partition=8, topic=chat.messages} 가 0이었다 (2026-08-03T05:41:15Z ~ 2026-08-03T06:41:15Z)
- kafka_consumergroup_lag{consumergroup=notification-processors, partition=0, topic=user.notifications} 가 0이었다 (2026-08-03T05:41:15Z ~ 2026-08-03T06:41:15Z)
- kafka_consumergroup_lag{consumergroup=notification-processors, partition=1, topic=user.notifications} 가 0이었다 (2026-08-03T05:41:15Z ~ 2026-08-03T06:41:15Z)
- kafka_consumergroup_lag{consumergroup=notification-processors, partition=2, topic=user.notifications} 가 0이었다 (2026-08-03T05:41:15Z ~ 2026-08-03T06:41:15Z)
- kafka_consumergroup_lag{consumergroup=notification-processors, partition=3, topic=user.notifications} 가 0이었다 (2026-08-03T05:41:15Z ~ 2026-08-03T06:36:15Z)
- kafka_consumergroup_lag{consumergroup=notification-processors, partition=4, topic=user.notifications} 가 0이었다 (2026-08-03T05:41:15Z ~ 2026-08-03T06:41:15Z)
- kafka_consumergroup_lag{consumergroup=notification-processors, partition=5, topic=user.notifications} 가 0이었다 (2026-08-03T05:41:15Z ~ 2026-08-03T06:41:15Z)
- kafka_consumergroup_lag{consumergroup=notification-recovery, partition=0, topic=user.notifications.dlq} 가 0이었다 (2026-08-03T05:41:15Z ~ 2026-08-03T06:41:15Z)
- kafka_consumergroup_lag{consumergroup=notification-recovery, partition=2, topic=user.notifications.dlq} 가 0이었다 (2026-08-03T05:41:15Z ~ 2026-08-03T06:41:15Z)
- kafka_consumergroup_lag{consumergroup=notification-retry-2000, partition=0, topic=chat.messages-retry-2000} 가 0이었다 (2026-08-03T05:41:15Z ~ 2026-08-03T06:41:15Z)
- kafka_consumergroup_lag{consumergroup=notification-retry-4000, partition=0, topic=chat.messages-retry-4000} 가 0이었다 (2026-08-03T05:41:15Z ~ 2026-08-03T06:41:15Z)
- kafka_consumergroup_lag{consumergroup=notification-processors, partition=3, topic=user.notifications} 0 → 1
- 같은 시각의 다른 후보: INC-2, INC-3, INC-4, INC-5  (인과 여부는 판단하지 않았다)

## INC-2  chat-service  |  websocket_active_users
- 구간: 2026-08-03T05:41:15Z ~ 2026-08-03T06:41:15Z  (MIMIR · 집계 해상도만큼 흐림)
- websocket_active_users{container=chat-service, namespace=default, pod=chat-service-fdcc7c776-qrbc2} 가 0이었다 (2026-08-03T05:41:15Z ~ 2026-08-03T06:41:15Z)
- 같은 시각의 다른 후보: INC-1, INC-3, INC-4, INC-5  (인과 여부는 판단하지 않았다)

## INC-3  auth-service  |  ERROR/WARN
- 구간: 2026-08-03T06:35:00Z ~ 2026-08-03T06:40:00Z  (LOKI · 집계 해상도만큼 흐림)
- ERROR/WARN 1건 (2026-08-03T06:35:00Z ~ 2026-08-03T06:40:00Z)
- 같은 시각의 다른 후보: INC-1, INC-2, INC-4, INC-5  (인과 여부는 판단하지 않았다)

## INC-4  content-service  |  ERROR/WARN
- 구간: 2026-08-03T06:35:00Z ~ 2026-08-03T06:40:00Z  (LOKI · 집계 해상도만큼 흐림)
- ERROR/WARN 4건 (2026-08-03T06:35:00Z ~ 2026-08-03T06:40:00Z)
- 같은 시각의 다른 후보: INC-1, INC-2, INC-3, INC-5  (인과 여부는 판단하지 않았다)

## INC-5  content-service  |  http post /feeds/{feedId}/comments
- 구간: 2026-08-03T06:38:12.870260Z ~ 2026-08-03T06:38:25.184023Z  (TEMPO · 시각 정확)
- content-service http post /feeds/{feedId}/comments 11,643ms (slow 채널)
- content-service http post /feeds/{feedId}/comments 308ms (error 채널)
- traceId: 6a7037546e3404569c632181052c8516, 6a7037601a12bc2de1bbb08457bac9d2
- 같은 시각의 다른 후보: INC-1, INC-2, INC-3, INC-4  (인과 여부는 판단하지 않았다)

**기각한 후보**

- INC-1 — 37개 시계열 전부가 창 전체에서 lag=0으로 평탄하고 notification-processors partition=3의 0→1 한 칸만 변한 것이라 실제 적체가 아니며, 댓글 작성은 Kafka 소비 경로가 아니라 동기 HTTP 쓰기 경로다 (partition 2·6·9·10의 -1은 미할당 파티션 표기이지 이상 신호가 아님).
- INC-2 — websocket_active_users가 06:38 전후로 떨어진 것이 아니라 05:41부터 06:41까지 계속 0인 평탄한 값이라 증상 시각과의 상관이 없고, chat-service up도 내내 1이라 파드 사망 흔적이 없다 — 다만 chat-service가 지연 트레이스에 14스팬으로 참여하므로 서비스 자체는 조사 대상에 남겨 두었다.

**보정 기록**

- 창을 후보 [INC-3, INC-4, INC-5] 의 신호 시각에서 계산했다 (2026-08-03T06:30:00Z ~ 2026-08-03T06:41:15Z)

## 수집 범위 (Coverage)

- **window**: 2026-08-03T06:30:00Z ~ 2026-08-03T06:41:15Z (675s)
- **trace**: 68,692B / 70 spans
- **창 안 후보 트레이스**: 10건 / 68,692B
- **logs**: errwarn=38,617B · traceId=17,848B
- **metrics**: 8 수집 / 89,649B, 누락 [sum(rate(http_server_requests_seconds_count{application="content-service", status="401"}[1m]))]
- **context**: 150,157 chars (+ 시스템 프롬프트 576 chars)

### 토큰 축 (개선 지표)

| 단계 | 총 in | chars | 컨텍스트 토큰 |
|---|---:|---:|---:|
| 탐색 | 47,382 | 46,620 | 26,073 |
| 분석 | 98,846 | 150,733 | 77,537 |
| **합계** | **146,228** | | **103,610** |

- **overheadTokens 21,309 tok — 이 회차에 실측**(1자 프롬프트 프로브, 같은 명령·모델·샌드박스).
  호출마다 한 번씩 얹히므로 단계별로 뺀다. **다른 날 상수를 쓰지 않았다 = `█ 실측`.**
- contextTokens (count_tokens API): 측정 안 됨 — 구독 CLI 경로엔 API 키가 없다
- 비용은 **API 환산 추정치**다. 구독 계정으로 호출하므로 실제 청구액이 아니다.

## 수집 실패/누락

- Metric 'sum(rate(http_server_requests_seconds_count{application="content-service", status="401"}[1m]))' returned no series in this window; skipped.

---

## 1. 원인 후보 랭킹 (최대 3개)

1. **댓글 본문 길이가 `tb_feed_comment.content` 컬럼 정의를 초과 → MySQL 1406(Data truncation) → 트랜잭션 롤백 → HTTP 500** (애플리케이션 단 입력 길이 검증 부재 포함)
2. **알림/후처리 파이프라인 지연으로 인한 "실패로 보이는" 체감 지연** (댓글 자체는 200이지만 알림까지 ~11초)
3. **인증(401) 계열 실패** — 근거 없음 + 확인 메트릭 수집 실패 (데이터 부족)

---

## 2. 후보별 근거

### 후보 1. content 컬럼 길이 초과로 인한 INSERT 실패

**근거**
- 트레이스 `6a7037601a12bc2de1bbb08457bac9d2`: 서버 span `http post /feeds/{feedId}/comments`, `http.url=/api/feeds/145/comments`, `status=500`, `outcome=SERVER_ERROR`, 소요 308ms (06:38:24.876Z~06:38:25.184Z).
- 같은 트레이스의 JDBC span `query`(`STATUS_CODE_ERROR`):
  `error = "Data truncation: Data too long for column 'content' at row 1"`
  `jdbc.query[0] = "insert into tb_feed_comment (content,created_at,creator_id,...) values (?,?,...)"`
- 같은 트레이스의 `connection` span 이벤트가 `acquired → **rollback**` (정상 트레이스는 `acquired → commit`). 즉 댓글 트랜잭션 전체가 롤백됨.
- 로그 원문:
  - `WARN ... SqlExceptionHelper - SQL Error: 1406, SQLState: 22001` (22001 = string data right truncation)
  - `ERROR ... o.h.e.jdbc.spi.SqlExceptionHelper - Data truncation: Data too long for column 'content' at row 1`
  - `org.springframework.dao.DataIntegrityViolationException: could not execute statement [Data truncation: Data too long for column 'content' at row 1] ... at com.example.toycontent.app.feed.service.FeedCommentService.createComment(FeedCommentService.java:64)`
  - `Caused by: com.mysql.cj.jdbc.exceptions.MysqlDataTruncation`
  - `WARN ... GlobalExceptionHandler - [api-error] handleAllException` → 전용 핸들러가 없어 일반 예외 경로로 떨어져 **400이 아니라 500** 으로 응답
  - `ERROR ... RequestLoggingFilter - [HTTP] POST /api/feeds/145/comments 500 - 306ms`
- 스택트레이스상 `JwtAuthenticationFilter`, `JwtFilter`를 통과한 뒤 컨트롤러(`FeedCommentController.createComment:53`)까지 도달했고 로그에 `userId=1`이 찍힘 → 인증·인가는 정상, 순수 데이터 계층 실패.
- 인프라 측 이상 없음: `up`(content/auth/chat/mysql 노드/redis/mongodb/kafka) 전 구간 1, `hikaricp_connections_active`·`pending` 전 구간 0, `mongodb_up`=1, `kafka_brokers`=1.

**확신도: 높음** (실패 요청 1건에 대해 원인 예외가 트레이스·로그 양쪽에서 동일하게 확인됨)

**반증 데이터**
- 같은 엔드포인트·같은 피드(`/api/feeds/145/comments`)가 12초 전(06:38:12.870Z, 트레이스 `6a7037546e3404569c632181052c8516`)에는 `status=200`으로 성공했고, 동일한 `insert into tb_feed_comment` 가 `row-affected=1`로 커밋됨. → **전면 장애가 아니라 입력 페이로드 의존적 실패**라는 뜻이며, 후보 1 자체를 부정하진 않지만 "모든 댓글이 실패한다"는 해석은 반증됨.
- 실패한 pod(`content-service-6995bb7d94-nq9l2`)와 성공한 pod(`...-h2f6n`)는 **동일 ReplicaSet 해시(6995bb7d94)** → pod별 버전 스큐 가설은 반증됨.

---

### 후보 2. 알림/후처리 지연으로 인한 체감 실패

**근거**
- 성공 트레이스 `6a7037546e3404569c632181052c8516`에서 HTTP 응답 자체는 747ms(06:38:12.870Z~06:38:13.617Z)로 정상 종료했으나, 후속 비동기 경로가 매우 김:
  - `kafka/user.notifications --messaging--> chat-service` `receive` span **9805.4ms** (06:38:14.708Z~06:38:24.514Z)
  - chat-service의 `connection`(HikariPool-1) span이 **9597.6ms** 동안 커넥션 점유(`acquired`→`commit`)
  - `push-dispatcher#dispatch` 5057ms, FCM 발송 구간 06:38:19.520 → `[push] 멀티캐스트 결과: tokens=1, success=1, failure=0` 06:38:24.457 (약 4.9초)
- auth-service WARN: `[HTTP-SLOW] POST /api/login 200 - 2013ms`, `[HTTP-SLOW] GET /api/user/1/following 200 - 2561ms`
- content-service 자체도 댓글 1건 처리에 EXP/캡 관련 쿼리를 다수 수행(`tb_exp_history`, `tb_daily_exp_cap`, `tb_user_reward ... for update`) — 호출 그래프상 `content-service --jdbc--> mysql` 26회, 최대 643.7ms.

**확신도: 낮음** (지연은 실재하지만, "댓글 작성 실패"라는 제보와 연결하는 관측 근거가 없음. 실패 상태코드가 찍힌 요청은 후보 1의 1건뿐)

**반증 데이터**
- 해당 경로의 최종 결과는 모두 성공: `[push] OK index=0 ... messageId=...`, `[kafka] 알림 처리 완료: userId=7, type=FEED_COMMENT`.
- `kafka_consumergroup_lag{consumergroup=notification-processors, topic=user.notifications}`는 partition 3에서 06:39:30Z에 잠시 1이 되었을 뿐 나머지 전 구간 0 → 소비 적체 없음.
- 타임아웃/에러 span 없음, `websocket_active_users`=0으로 WebSocket 폭주도 없음.

---

### 후보 3. 인증 실패(401) 계열 — 데이터 부족

**근거**
- 직접적 근거 **없음**. 오히려 실패 트레이스에서 `security filterchain`이 12개 필터를 모두 통과했고 로그에 `userId=1`이 기록됨.
- 다만 이 후보를 정량적으로 배제하려던 메트릭 `sum(rate(http_server_requests_seconds_count{application="content-service", status="401"}[1m]))` 이 **수집 실패(no series)** 했다. 시리즈 부재가 "401이 0건"인지 "메트릭 라벨/스크랩 자체가 없음"인지 구분할 수 없다.

**확신도: 낮음**

**반증 데이터**
- 위 필터체인 통과 + `userId=1` 로깅, 그리고 `content-service --service--> auth-service` 호출이 `status=200`/`outcome=SUCCESS`(`http get /external/users/{userId}`, 175.1ms).

---

### 판단의 한계 (수집 공백)
- 수집된 트레이스 10건 중 댓글 엔드포인트는 **2건(성공 1, 실패 1)** 뿐이다. **실패율·영향 사용자 수는 산출 불가 — 데이터 부족.**
- 조회 창이 06:30:00Z~06:41:15Z(약 11분)로, 제보의 "최근 1시간"을 덮지 못한다.
- `hikaricp_connections_active`가 전 구간 0인데 트레이스에는 커넥션 획득이 다수 존재 → 이 메트릭은 스크랩 타이밍상 신뢰도가 낮으니, 커넥션 풀 관련 가설의 배제 근거로는 약하게만 사용했다.
- 401 메트릭 결측 때문에 후보 3을 완전히 배제하지 않고 남겨두었다.

---

## 3. 권장 다음 조치

**즉시 확인 (원인 확정)**
1. 컬럼 정의 확인:
   `SELECT COLUMN_NAME, DATA_TYPE, CHARACTER_MAXIMUM_LENGTH FROM information_schema.COLUMNS WHERE TABLE_NAME='tb_feed_comment' AND COLUMN_NAME='content';`
   → `content` 실제 길이 제한과 charset(utf8mb4면 이모지 1자=4바이트)을 확인.
2. 최근 스키마 마이그레이션 이력 확인 — 컬럼 길이가 축소된 변경이 있었는지(있다면 "특정 입력에서만 실패"가 아니라 회귀 장애일 수 있음).
3. 실패 규모 산출 (현재 미확보):
   - Mimir: `sum(rate(http_server_requests_seconds_count{application="content-service", uri="/feeds/{feedId}/comments", status="500"}[5m]))` 를 최근 1시간으로
   - Loki: `{namespace="default", container="content-service"} |= "SQLState: 22001"` 최근 1시간 카운트, `userId` 별 집계

**조치**
4. 애플리케이션 단 길이 검증 추가: `FeedCommentController.createComment`(FeedCommentController.java:53) 요청 DTO에 `@Size(max=N)` + `FeedCommentService.createComment`(FeedCommentService.java:64) 저장 전 검증. N은 DB 컬럼 길이와 일치시킬 것.
5. `GlobalExceptionHandler`에 `DataIntegrityViolationException`/`DataException` 핸들러 추가 → **400(또는 422) + 사용자 메시지**로 매핑. 현재는 `handleAllException`으로 떨어져 500이 나가며, 클라이언트가 "서버 장애"로 인식한다.
6. 제품 요구 길이가 현재 컬럼보다 크다면 컬럼 확장(`ALTER TABLE tb_feed_comment MODIFY content ...`) — 단, 테이블 크기/락 영향 사전 검토 후 진행. 프론트엔드 입력 제한(maxlength)도 동일 값으로 맞출 것.

**별건 후속 (후보 2)**
7. chat-service 알림 소비 경로 조사: `user.notifications` consumer가 9.8초 동안 열려 있고 그 사이 HikariPool 커넥션을 9.6초 점유한다. FCM 호출(약 4.9초)이 트랜잭션 안에서 수행되는지 확인하고, 외부 호출을 트랜잭션 밖으로 빼거나 타임아웃을 설정할 것.
8. auth-service `POST /api/login` 2013ms, `GET /api/user/1/following` 2561ms의 원인 확인 (후자는 같은 트레이스에 `HHH90003004: firstResult/maxResults specified with collection fetch; applying in memory` WARN 동반 — 인메모리 페이징 의심).

**관측 보강**
9. `http_server_requests_seconds_count{application="content-service"}`의 `status` 라벨 노출 여부와 스크랩 상태 점검 (401 쿼리 결측 원인).
10. `hikaricp_connections_active`가 트레이스와 불일치하는 원인(스크랩 주기 vs 짧은 점유 시간) 점검 — 현재로선 풀 포화 판단에 쓸 수 없다.
11. 재조사 시 조회 창을 제보대로 최근 1시간 전체로 넓혀 실패 트레이스 표본을 늘릴 것.

---

## 관측 증거 (Evidence)

> 이 조사가 실제로 본 값이다. 원본 응답 전체는 `reports/raw/scan-1785738600-*.json`에 있다.

### 호출 그래프 (트레이스에서 추출)

```
chat-service --db--> mongodb  7회  최대 100.1ms  [insert, find]
chat-service --db--> redis  2회  최대 5.0ms  [KEYS]
content-service --db--> redis  6회  최대 1.8ms  [GET, SET, INFO]
chat-service --jdbc--> mysql/content (HikariPool-1)  1회  최대 9597.6ms
    events: acquired, commit
content-service --jdbc--> mysql/content (HikariPool-1)  26회  최대 643.7ms
    error: Data truncation: Data too long for column 'content' at row 1
    events: acquired, commit, rollback
content-service --messaging--> kafka/user.notifications  1회  최대 574.8ms  [publish]
kafka/user.notifications --messaging--> chat-service  1회  최대 9805.4ms  [receive]
content-service --service--> auth-service  2회  최대 175.1ms
```

### span (duration 상위 15 / 전체 70)

| ms | service | span | 시작 |
|---:|---|---|---|
| 9805.43 | chat-service | `receive` | 2026-08-03T06:38:14.708754Z |
| 9597.56 | chat-service | `connection` | 2026-08-03T06:38:14.915210Z |
| 9346.91 | chat-service | `user-notification-service#process-notification` | 2026-08-03T06:38:15.111940Z |
| 5057.20 | chat-service | `push-dispatcher#dispatch` | 2026-08-03T06:38:19.401255Z |
| 747.44 | content-service | `http post /feeds/{feedId}/comments` | 2026-08-03T06:38:12.870260Z |
| 741.02 | content-service | `secured request` | 2026-08-03T06:38:12.872379Z |
| 643.66 | content-service | `connection` | 2026-08-03T06:38:12.969504Z |
| 574.76 | content-service | `publish user.notifications` | 2026-08-03T06:38:13.631254Z |
| 469.10 | content-service | `notification-publish` | 2026-08-03T06:38:13.564377Z |
| 406.22 | chat-service | `user-notification-web-socket-sender#send-notificat` | 2026-08-03T06:38:18.810393Z |
| 308.70 | content-service | `http post /feeds/{feedId}/comments` | 2026-08-03T06:38:24.876023Z |
| 304.73 | content-service | `secured request` | 2026-08-03T06:38:24.877354Z |
| 189.28 | content-service | `connection` | 2026-08-03T06:38:24.992503Z |
| 175.06 | content-service | `http get` | 2026-08-03T06:38:12.983390Z |
| 163.08 | auth-service | `http get /external/users/{userid}` | 2026-08-03T06:38:12.996443Z |

### 로그 원문 (60 / 전체 258줄)

전체가 상한을 넘어 ERROR/WARN 줄을 우선 발췌했다. 나머지는 원본 파일에 있다.

```
2026-08-03T06:38:12.772761348Z  [auth-service]  [2m2026-08-03 15:38:12[0;39m [2m[http-nio-8081-exec-6][0;39m [33m WARN [traceId=6a7037525a9a598fc4faf932a17b3dc4,spanId=c4faf932a17b3dc4,userId=NONE][0;39m [36mc.e.t.a.c.f.RequestLoggingFilter[0;39m [2m-[0;39m [HTTP-SLOW] POST /api/login 200 - 2013ms
2026-08-03T06:38:25.122215521Z  [content-service]  2026-08-03 15:38:25.121 [http-nio-8082-exec-2]  WARN [traceId=6a7037601a12bc2de1bbb08457bac9d2,spanId=aa6a9b00b92c1d5f,userId=1] o.h.e.jdbc.spi.SqlExceptionHelper - SQL Error: 1406, SQLState: 22001
2026-08-03T06:38:25.122215521Z  [content-service]  2026-08-03 15:38:25.121 [http-nio-8082-exec-2]  WARN [traceId=6a7037601a12bc2de1bbb08457bac9d2,spanId=aa6a9b00b92c1d5f,userId=1] o.h.e.jdbc.spi.SqlExceptionHelper - SQL Error: 1406, SQLState: 22001
2026-08-03T06:38:25.122234185Z  [content-service]  2026-08-03 15:38:25.121 [http-nio-8082-exec-2] ERROR [traceId=6a7037601a12bc2de1bbb08457bac9d2,spanId=aa6a9b00b92c1d5f,userId=1] o.h.e.jdbc.spi.SqlExceptionHelper - Data truncation: Data too long for column 'content' at row 1
2026-08-03T06:38:25.122234185Z  [content-service]  2026-08-03 15:38:25.121 [http-nio-8082-exec-2] ERROR [traceId=6a7037601a12bc2de1bbb08457bac9d2,spanId=aa6a9b00b92c1d5f,userId=1] o.h.e.jdbc.spi.SqlExceptionHelper - Data truncation: Data too long for column 'content' at row 1
2026-08-03T06:38:25.176330204Z  [content-service]  2026-08-03 15:38:25.151 [http-nio-8082-exec-2]  WARN [traceId=6a7037601a12bc2de1bbb08457bac9d2,spanId=aa6a9b00b92c1d5f,userId=1] c.e.t.a.c.e.GlobalExceptionHandler - [api-error] handleAllException
2026-08-03T06:38:25.176330204Z  [content-service]  2026-08-03 15:38:25.151 [http-nio-8082-exec-2]  WARN [traceId=6a7037601a12bc2de1bbb08457bac9d2,spanId=aa6a9b00b92c1d5f,userId=1] c.e.t.a.c.e.GlobalExceptionHandler - [api-error] handleAllException
2026-08-03T06:38:25.176389674Z  [content-service]  org.springframework.dao.DataIntegrityViolationException: could not execute statement [Data truncation: Data too long for column 'content' at row 1] [insert into tb_feed_comment (content,created_at,creator_id,creator_nickname,creator_profile_url,deleted,deleted_at,feed_id,parent_comment_id,updated_at) values (?,?,?,?,?,?,?,?,?,?)]; SQL [insert into tb_feed_comment (content,created_at,creator_id,creator_nickname,creator_profile_url,deleted,deleted_at,feed_id,parent_comment_id,updated_at) values (?,?,?,?,?,?,?,?,?,?)]
2026-08-03T06:38:25.176411834Z  [content-service]  at org.springframework.orm.jpa.vendor.HibernateJpaDialect.convertHibernateAccessException(HibernateJpaDialect.java:293)
2026-08-03T06:38:25.176415091Z  [content-service]  at org.springframework.orm.jpa.vendor.HibernateJpaDialect.translateExceptionIfPossible(HibernateJpaDialect.java:241)
2026-08-03T06:38:25.176418448Z  [content-service]  at org.springframework.orm.jpa.AbstractEntityManagerFactoryBean.translateExceptionIfPossible(AbstractEntityManagerFactoryBean.java:560)
2026-08-03T06:38:25.176421464Z  [content-service]  at org.springframework.dao.support.ChainedPersistenceExceptionTranslator.translateExceptionIfPossible(ChainedPersistenceExceptionTranslator.java:61)
2026-08-03T06:38:25.176424475Z  [content-service]  at org.springframework.dao.support.DataAccessUtils.translateIfNecessary(DataAccessUtils.java:343)
2026-08-03T06:38:25.176427161Z  [content-service]  at org.springframework.dao.support.PersistenceExceptionTranslationInterceptor.invoke(PersistenceExceptionTranslationInterceptor.java:160)
2026-08-03T06:38:25.176429802Z  [content-service]  at org.springframework.aop.framework.ReflectiveMethodInvocation.proceed(ReflectiveMethodInvocation.java:184)
2026-08-03T06:38:25.176458577Z  [content-service]  at org.springframework.data.jpa.repository.support.CrudMethodMetadataPostProcessor$CrudMethodMetadataPopulatingMethodInterceptor.invoke(CrudMethodMetadataPostProcessor.java:165)
2026-08-03T06:38:25.176462069Z  [content-service]  at org.springframework.aop.framework.ReflectiveMethodInvocation.proceed(ReflectiveMethodInvocation.java:184)
2026-08-03T06:38:25.176464962Z  [content-service]  at org.springframework.aop.framework.JdkDynamicAopProxy.invoke(JdkDynamicAopProxy.java:223)
2026-08-03T06:38:25.176471472Z  [content-service]  at com.example.toycontent.app.feed.service.FeedCommentService.createComment(FeedCommentService.java:64)
2026-08-03T06:38:25.176484917Z  [content-service]  at org.springframework.aop.support.AopUtils.invokeJoinpointUsingReflection(AopUtils.java:359)
2026-08-03T06:38:25.176488270Z  [content-service]  at org.springframework.aop.framework.ReflectiveMethodInvocation.invokeJoinpoint(ReflectiveMethodInvocation.java:196)
2026-08-03T06:38:25.176491170Z  [content-service]  at org.springframework.aop.framework.ReflectiveMethodInvocation.proceed(ReflectiveMethodInvocation.java:163)
2026-08-03T06:38:25.176494208Z  [content-service]  at org.springframework.transaction.interceptor.TransactionAspectSupport.invokeWithinTransaction(TransactionAspectSupport.java:380)
2026-08-03T06:38:25.176497133Z  [content-service]  at org.springframework.transaction.interceptor.TransactionInterceptor.invoke(TransactionInterceptor.java:119)
2026-08-03T06:38:25.176499801Z  [content-service]  at org.springframework.aop.framework.ReflectiveMethodInvocation.proceed(ReflectiveMethodInvocation.java:184)
2026-08-03T06:38:25.176502473Z  [content-service]  at org.springframework.aop.framework.CglibAopProxy$DynamicAdvisedInterceptor.intercept(CglibAopProxy.java:727)
2026-08-03T06:38:25.176507946Z  [content-service]  at com.example.toycontent.app.feed.controller.FeedCommentController.createComment(FeedCommentController.java:53)
2026-08-03T06:38:25.176527378Z  [content-service]  at org.springframework.web.method.support.InvocableHandlerMethod.doInvoke(InvocableHandlerMethod.java:257)
2026-08-03T06:38:25.176530158Z  [content-service]  at org.springframework.web.method.support.InvocableHandlerMethod.invokeForRequest(InvocableHandlerMethod.java:190)
2026-08-03T06:38:25.176579532Z  [content-service]  at org.springframework.web.servlet.mvc.method.annotation.ServletInvocableHandlerMethod.invokeAndHandle(ServletInvocableHandlerMethod.java:118)
2026-08-03T06:38:25.176583973Z  [content-service]  at org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter.invokeHandlerMethod(RequestMappingHandlerAdapter.java:986)
2026-08-03T06:38:25.176586918Z  [content-service]  at org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter.handleInternal(RequestMappingHandlerAdapter.java:891)
2026-08-03T06:38:25.176589621Z  [content-service]  at org.springframework.web.servlet.mvc.method.AbstractHandlerMethodAdapter.handle(AbstractHandlerMethodAdapter.java:87)
2026-08-03T06:38:25.176592362Z  [content-service]  at org.springframework.web.servlet.DispatcherServlet.doDispatch(DispatcherServlet.java:1088)
2026-08-03T06:38:25.176595248Z  [content-service]  at org.springframework.web.servlet.DispatcherServlet.doService(DispatcherServlet.java:978)
2026-08-03T06:38:25.176597932Z  [content-service]  at org.springframework.web.servlet.FrameworkServlet.processRequest(FrameworkServlet.java:1014)
2026-08-03T06:38:25.176600675Z  [content-service]  at org.springframework.web.servlet.FrameworkServlet.doPost(FrameworkServlet.java:914)
2026-08-03T06:38:25.176603920Z  [content-service]  at jakarta.servlet.http.HttpServlet.service(HttpServlet.java:590)
2026-08-03T06:38:25.176606823Z  [content-service]  at org.springframework.web.servlet.FrameworkServlet.service(FrameworkServlet.java:885)
2026-08-03T06:38:25.176610091Z  [content-service]  at jakarta.servlet.http.HttpServlet.service(HttpServlet.java:658)
2026-08-03T06:38:25.176612757Z  [content-service]  at org.apache.catalina.core.ApplicationFilterChain.internalDoFilter(ApplicationFilterChain.java:195)
2026-08-03T06:38:25.176615531Z  [content-service]  at org.apache.catalina.core.ApplicationFilterChain.doFilter(ApplicationFilterChain.java:140)
2026-08-03T06:38:25.176636186Z  [content-service]  at org.apache.tomcat.websocket.server.WsFilter.doFilter(WsFilter.java:51)
2026-08-03T06:38:25.176639083Z  [content-service]  at org.apache.catalina.core.ApplicationFilterChain.internalDoFilter(ApplicationFilterChain.java:164)
2026-08-03T06:38:25.176641850Z  [content-service]  at org.apache.catalina.core.ApplicationFilterChain.doFilter(ApplicationFilterChain.java:140)
2026-08-03T06:38:25.176644706Z  [content-service]  at org.springframework.web.filter.OncePerRequestFilter.doFilter(OncePerRequestFilter.java:110)
2026-08-03T06:38:25.176647337Z  [content-service]  at org.apache.catalina.core.ApplicationFilterChain.internalDoFilter(ApplicationFilterChain.java:164)
2026-08-03T06:38:25.176650200Z  [content-service]  at org.apache.catalina.core.ApplicationFilterChain.doFilter(ApplicationFilterChain.java:140)
2026-08-03T06:38:25.176687243Z  [content-service]  at org.springframework.security.web.access.ExceptionTranslationFilter.doFilter(ExceptionTranslationFilter.java:126)
2026-08-03T06:38:25.176690048Z  [content-service]  at org.springframework.security.web.access.ExceptionTranslationFilter.doFilter(ExceptionTranslationFilter.java:120)
2026-08-03T06:38:25.177081412Z  [content-service]  Caused by: org.hibernate.exception.DataException: could not execute statement [Data truncation: Data too long for column 'content' at row 1] [insert into tb_feed_comment (content,created_at,creator_id,creator_nickname,creator_profile_url,deleted,deleted_at,feed_id,parent_comment_id,updated_at) values (?,?,?,?,?,?,?,?,?,?)]
2026-08-03T06:38:25.177083959Z  [content-service]  at org.hibernate.exception.internal.SQLExceptionTypeDelegate.convert(SQLExceptionTypeDelegate.java:55)
2026-08-03T06:38:25.177086516Z  [content-service]  at org.hibernate.exception.internal.StandardSQLExceptionConverter.convert(StandardSQLExceptionConverter.java:58)
2026-08-03T06:38:25.177088947Z  [content-service]  at org.hibernate.engine.jdbc.spi.SqlExceptionHelper.convert(SqlExceptionHelper.java:108)
2026-08-03T06:38:25.177294466Z  [content-service]  at org.springframework.dao.support.PersistenceExceptionTranslationInterceptor.invoke(PersistenceExceptionTranslationInterceptor.java:138)
2026-08-03T06:38:25.177315988Z  [content-service]  at com.mysql.cj.jdbc.exceptions.SQLExceptionsMapping.translateException(SQLExceptionsMapping.java:96)
2026-08-03T06:38:25.182547266Z  [content-service]  2026-08-03 15:38:25.182 [http-nio-8082-exec-2] ERROR [traceId=6a7037601a12bc2de1bbb08457bac9d2,spanId=e1bbb08457bac9d2,userId=NONE] c.e.t.a.c.f.RequestLoggingFilter - [HTTP] POST /api/feeds/145/comments 500 - 306ms
2026-08-03T06:38:25.182547266Z  [content-service]  2026-08-03 15:38:25.182 [http-nio-8082-exec-2] ERROR [traceId=6a7037601a12bc2de1bbb08457bac9d2,spanId=e1bbb08457bac9d2,userId=NONE] c.e.t.a.c.f.RequestLoggingFilter - [HTTP] POST /api/feeds/145/comments 500 - 306ms
2026-08-03T06:41:09.247558202Z  [auth-service]  [2m2026-08-03 15:41:09[0;39m [2m[http-nio-8081-exec-10][0;39m [33m WARN [traceId=6a703802f17ebda461e26f109a5d5a30,spanId=1c72fc7b3300ce1c,userId=1][0;39m [36morg.hibernate.orm.query[0;39m [2m-[0;39m HHH90003004: firstResult/maxResults specified with collection fetch; applying in memory
2026-08-03T06:41:09.366268004Z  [auth-service]  [2m2026-08-03 15:41:09[0;39m [2m[http-nio-8081-exec-10][0;39m [33m WARN [traceId=6a703802f17ebda461e26f109a5d5a30,spanId=61e26f109a5d5a30,userId=NONE][0;39m [36mc.e.t.a.c.f.RequestLoggingFilter[0;39m [2m-[0;39m [HTTP-SLOW] GET /api/user/1/following 200 - 2561ms
```

### 메트릭 시계열

| 쿼리 | series | 점 | min | max | last | 값이 0이던 구간 |
|---|---|---:|---:|---:|---:|---|
| `hikaricp_connections_active` | `{__name__=hikaricp_connections_active, application=auth-service, cluster=yogurtte-k3s-prod, container=auth-service, instance=10.42.1.44:8090, job=auth-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=auth-service-5999bb9f5c-qqrss, pool=HikariPool-1, service=auth-service}` | 46 | 0 | 0 | 0 | **2026-08-03T06:30:00Z ~ 2026-08-03T06:41:15Z** |
| `hikaricp_connections_active` | `{__name__=hikaricp_connections_active, application=chat-service, cluster=yogurtte-k3s-prod, container=chat-service, instance=10.42.3.43:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-fdcc7c776-qrbc2, pool=HikariPool-1}` | 46 | 0 | 0 | 0 | **2026-08-03T06:30:00Z ~ 2026-08-03T06:41:15Z** |
| `hikaricp_connections_active` | `{__name__=hikaricp_connections_active, application=content-service, cluster=yogurtte-k3s-prod, container=content-service, instance=10.42.1.43:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-6995bb7d94-h2f6n, pool=HikariPool-1}` | 46 | 0 | 0 | 0 | **2026-08-03T06:30:00Z ~ 2026-08-03T06:41:15Z** |
| `hikaricp_connections_active` | `{__name__=hikaricp_connections_active, application=content-service, cluster=yogurtte-k3s-prod, container=content-service, instance=10.42.3.42:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-6995bb7d94-nq9l2, pool=HikariPool-1}` | 46 | 0 | 0 | 0 | **2026-08-03T06:30:00Z ~ 2026-08-03T06:41:15Z** |
| `hikaricp_connections_pending` | `{__name__=hikaricp_connections_pending, application=auth-service, cluster=yogurtte-k3s-prod, container=auth-service, instance=10.42.1.44:8090, job=auth-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=auth-service-5999bb9f5c-qqrss, pool=HikariPool-1, service=auth-service}` | 46 | 0 | 0 | 0 | **2026-08-03T06:30:00Z ~ 2026-08-03T06:41:15Z** |
| `hikaricp_connections_pending` | `{__name__=hikaricp_connections_pending, application=chat-service, cluster=yogurtte-k3s-prod, container=chat-service, instance=10.42.3.43:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-fdcc7c776-qrbc2, pool=HikariPool-1}` | 46 | 0 | 0 | 0 | **2026-08-03T06:30:00Z ~ 2026-08-03T06:41:15Z** |
| `hikaricp_connections_pending` | `{__name__=hikaricp_connections_pending, application=content-service, cluster=yogurtte-k3s-prod, container=content-service, instance=10.42.1.43:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-6995bb7d94-h2f6n, pool=HikariPool-1}` | 46 | 0 | 0 | 0 | **2026-08-03T06:30:00Z ~ 2026-08-03T06:41:15Z** |
| `hikaricp_connections_pending` | `{__name__=hikaricp_connections_pending, application=content-service, cluster=yogurtte-k3s-prod, container=content-service, instance=10.42.3.42:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-6995bb7d94-nq9l2, pool=HikariPool-1}` | 46 | 0 | 0 | 0 | **2026-08-03T06:30:00Z ~ 2026-08-03T06:41:15Z** |
| `rate(jvm_gc_pause_seconds_sum[5m])` | `{action=end of major GC, application=chat-service, cause=Allocation Failure, cluster=yogurtte-k3s-prod, container=chat-service, gc=MarkSweepCompact, instance=10.42.3.43:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-fdcc7c776-qrbc2}` | 46 | 0 | 0 | 0 | **2026-08-03T06:30:00Z ~ 2026-08-03T06:41:15Z** |
| `rate(jvm_gc_pause_seconds_sum[5m])` | `{action=end of minor GC, application=auth-service, cause=G1 Evacuation Pause, cluster=yogurtte-k3s-prod, container=auth-service, gc=G1 Young Generation, instance=10.42.1.44:8090, job=auth-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=auth-service-5999bb9f5c-qqrss, service=auth-service}` | 46 | 0 | 0 | 0 | **2026-08-03T06:30:00Z ~ 2026-08-03T06:41:15Z** |
| `rate(jvm_gc_pause_seconds_sum[5m])` | `{action=end of minor GC, application=auth-service, cause=Metadata GC Threshold, cluster=yogurtte-k3s-prod, container=auth-service, gc=G1 Young Generation, instance=10.42.1.44:8090, job=auth-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=auth-service-5999bb9f5c-qqrss, service=auth-service}` | 46 | 0 | 0 | 0 | **2026-08-03T06:30:00Z ~ 2026-08-03T06:41:15Z** |
| `rate(jvm_gc_pause_seconds_sum[5m])` | `{action=end of minor GC, application=chat-service, cause=Allocation Failure, cluster=yogurtte-k3s-prod, container=chat-service, gc=Copy, instance=10.42.3.43:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-fdcc7c776-qrbc2}` | 46 | 0.000 | 0.001 | 0.001 | — |
| `rate(jvm_gc_pause_seconds_sum[5m])` | `{action=end of minor GC, application=content-service, cause=Allocation Failure, cluster=yogurtte-k3s-prod, container=content-service, gc=Copy, instance=10.42.1.43:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-6995bb7d94-h2f6n}` | 46 | 0 | 0.000 | 0 | **2026-08-03T06:30:00Z ~ 2026-08-03T06:32:45Z, 2026-08-03T06:37:00Z ~ 2026-08-03T06:41:15Z** |
| `rate(jvm_gc_pause_seconds_sum[5m])` | `{action=end of minor GC, application=content-service, cause=Allocation Failure, cluster=yogurtte-k3s-prod, container=content-service, gc=Copy, instance=10.42.3.42:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-6995bb7d94-nq9l2}` | 46 | 0 | 0.000 | 0.000 | **2026-08-03T06:30:00Z ~ 2026-08-03T06:39:30Z** |
| `up` | `{__name__=up, app=node-exporter, cluster=yogurtte-k3s-prod, component=metrics, container=node-exporter, instance=ip-172-31-40-241, job=integrations/node_exporter, k8s_cluster_name=yogurtte-k3s-prod, namespace=monitoring, pod=grafana-k8s-monitoring-node-exporter-cph5l, source=kubernetes, workload=DaemonSet/grafana-k8s-monitoring-node-exporter}` | 46 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, app=node-exporter, cluster=yogurtte-k3s-prod, component=metrics, container=node-exporter, instance=ip-172-31-45-39, job=integrations/node_exporter, k8s_cluster_name=yogurtte-k3s-prod, namespace=monitoring, pod=grafana-k8s-monitoring-node-exporter-9kvqt, source=kubernetes, workload=DaemonSet/grafana-k8s-monitoring-node-exporter}` | 46 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, cluster=yogurtte-k3s-prod, container=auth-service, instance=10.42.1.44:8090, job=auth-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=auth-service-5999bb9f5c-qqrss}` | 46 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, cluster=yogurtte-k3s-prod, container=chat-service, instance=10.42.3.43:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-fdcc7c776-qrbc2}` | 46 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, cluster=yogurtte-k3s-prod, container=content-service, instance=10.42.1.43:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-6995bb7d94-h2f6n}` | 46 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, cluster=yogurtte-k3s-prod, container=content-service, instance=10.42.3.42:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-6995bb7d94-nq9l2}` | 46 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, cluster=yogurtte-k3s-prod, instance=10.42.1.248:8080, job=integrations/kubernetes/kube-state-metrics, k8s_cluster_name=yogurtte-k3s-prod, source=kubernetes}` | 46 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, cluster=yogurtte-k3s-prod, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod}` | 46 | 1 | 1 | 1 | — |
| `mongodb_up` | `{__name__=mongodb_up, cluster=yogurtte-k3s-prod, instance=infra-server, job=mongodb, k8s_cluster_name=yogurtte-k3s-prod}` | 46 | 1 | 1 | 1 | — |
| `kafka_brokers` | `{__name__=kafka_brokers, cluster=yogurtte-k3s-prod, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod}` | 46 | 1 | 1 | 1 | — |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=chat-service-fcm-tokens, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=0, topic=user.fcm-tokens}` | 46 | 0 | 0 | 0 | **2026-08-03T06:30:00Z ~ 2026-08-03T06:41:15Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=chat-service-fcm-tokens, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=1, topic=user.fcm-tokens}` | 46 | 0 | 0 | 0 | **2026-08-03T06:30:00Z ~ 2026-08-03T06:41:15Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=chat-service-fcm-tokens, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=2, topic=user.fcm-tokens}` | 46 | 0 | 0 | 0 | **2026-08-03T06:30:00Z ~ 2026-08-03T06:41:15Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=chat-service-notification-settings, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=0, topic=user.notification-settings}` | 46 | 0 | 0 | 0 | **2026-08-03T06:30:00Z ~ 2026-08-03T06:41:15Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=chat-service-notification-settings, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=1, topic=user.notification-settings}` | 46 | 0 | 0 | 0 | **2026-08-03T06:30:00Z ~ 2026-08-03T06:41:15Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=chat-service-notification-settings, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=2, topic=user.notification-settings}` | 46 | 0 | 0 | 0 | **2026-08-03T06:30:00Z ~ 2026-08-03T06:41:15Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=db-writer, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=0, topic=chat.messages}` | 46 | 0 | 0 | 0 | **2026-08-03T06:30:00Z ~ 2026-08-03T06:41:15Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=db-writer, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=1, topic=chat.messages}` | 46 | 0 | 0 | 0 | **2026-08-03T06:30:00Z ~ 2026-08-03T06:41:15Z** |
| `websocket_active_users` | `{__name__=websocket_active_users, application=chat-service, cluster=yogurtte-k3s-prod, container=chat-service, instance=10.42.3.43:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-fdcc7c776-qrbc2}` | 46 | 0 | 0 | 0 | **2026-08-03T06:30:00Z ~ 2026-08-03T06:41:15Z** |

값이 0이던 구간은 굵게 표시했다 — 프로세스가 사라져 시계열이 꺾인 것이 유일한 신호인 장애가 있다.

