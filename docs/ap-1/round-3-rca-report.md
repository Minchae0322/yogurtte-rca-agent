# RCA Report — `6a7037546e3404569c632181052c8516`

| 항목 | 값 |
|---|---|
| 모드 | rca |
| 질문 | 최근 1시간 안에 댓글 작성이 실패했다는 제보가 있다. 원인을 조사해줘 |
| 시각 | 2026-08-03T06:41:49.952354100Z |
| provider | claude-cli |
| model | `claude-opus-5` · turns 1 |
| prompt | `.\prompts\system-prompt.md` |
| tokens | in 173469 (cacheRead 23,453 · cacheCreate 150,014) / out 5523 · cost $1.6500 |
| elapsed | total 97512ms (tempo 469 · loki 839 · mimir 970 · assemble 182 · llm 87296) |

## 탐색 (Triage)

| 항목 | 값 |
|---|---|
| 시간창 해석 | 상대 표현 '최근 1시간' |
| 시간창 확신도 | EXACT |
| 스윕 창 | 2026-08-03T05:41:15.887073800Z ~ 2026-08-03T06:41:15.887073800Z |
| 좁힌 창 | 2026-08-03T06:30:00Z ~ 2026-08-03T06:41:15.887073800Z |
| 대상 | content-service, auth-service |
| traceId | 6a7037546e3404569c632181052c8516 |
| 트레이스 후보 | 2건 |
| 장애 후보 | 5건 · 선택 INC-3, INC-4, INC-5 |
| 계획 파싱 | 성공 |
| 스윕 컨텍스트 | 후보 + 원본 (A) |
| prompt | `.\prompts\triage-prompt.md` |
| tokens | in 55520 / out 1760 · cost $0.4036 |
| chars | 컨텍스트 45,261 + 프롬프트 1,399 = **46,660** |
| elapsed | survey 1502ms · llm 32474ms |

**선정 이유**: 질문의 증상(댓글 작성 실패)과 엔드포인트·시각이 그대로 맞는 것은 INC-5뿐이고, INC-4·INC-3은 같은 5분 창의 로그 지문이자 그 트레이스가 지나간 서비스라 같은 장애가 쪼개져 보인 것으로 보고 합쳐 연다.

**근거**

- INC-5: content-service http post /feeds/{feedId}/comments 11,643ms (slow 채널, 06:38:12.870Z) — 제보된 엔드포인트와 정확히 일치
- INC-5: 같은 엔드포인트 308ms error 채널 1건 (traceId 6a7037601a12bc2de1bbb08457bac9d2, serviceStats content-service errorCount=1) — 06:38:25Z
- 지연 트레이스 내부 중첩 span 9,805ms / 9,597ms / 9,346ms 이 모두 status=unset — 에러 없이 하류에서 붙잡힌 형태, 총 11.6s 중 약 9.8s가 한 갈래에 잠김
- 지연 트레이스 serviceStats에 auth-service 4 span, chat-service 14 span 포함 — 댓글 경로가 인증·알림 하류를 타고 있음
- INC-4: Loki content-service ERROR/WARN 4건 (1785739200 = 06:40:00Z 버킷, 창 06:35~06:40Z) — 트레이스 시각과 같은 5분 버킷
- INC-3: Loki auth-service ERROR/WARN 1건 (같은 06:35~06:40Z 버킷) — 지연 트레이스에 auth-service span이 걸려 있어 하류 후보로 함께 열어둠
- 전 구간 up=1, mongodb_up=1, kafka_brokers=1 — 프로세스·인프라 다운은 아니고 특정 경로만 느려진 것

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

- INC-1 — kafka_consumergroup_lag가 13개 샘플 전 구간 0(미할당 파티션은 -1)이고 유일한 변화가 notification-processors p3의 0→1 한 틱이라 지연이라 부를 수 없으며, 댓글 쓰기는 이 컨슈머 경로를 타지 않는다.
- INC-2 — websocket_active_users가 1시간 내내 0으로 평탄해 '떨어진' 변화가 없고, 06:38 사건과 시각 상관도 없으며 WebSocket 접속 수는 댓글 HTTP 쓰기 실패와 경로가 다르다 (다만 지연 트레이스에 chat-service span 14개가 있어 하류로 재부상하면 다시 볼 것).

**보정 기록**

- 창을 후보 [INC-3, INC-4, INC-5] 의 신호 시각에서 계산했다 (2026-08-03T06:30:00Z ~ 2026-08-03T06:41:15.887073800Z)

## 수집 범위 (Coverage)

- **window**: 2026-08-03T06:30:00Z ~ 2026-08-03T06:41:15.887073800Z (675s)
- **trace**: 40,377B / 46 spans
- **창 안 후보 트레이스**: 9건 / 25,927B
- **logs**: errwarn=107,012B · traceId=18,220B
- **metrics**: 8 수집 / 89,649B, 누락 [sum(rate(http_server_requests_seconds_count{application="content-service", status="401"}[1m]))]
- **context**: 282,951 chars (+ 시스템 프롬프트 598 chars)

### 토큰 축 (개선 지표)

| 단계 | 총 in | chars | 컨텍스트 토큰 |
|---|---:|---:|---:|
| 탐색 | 55,520 | 46,660 | 26,090 |
| 분석 | 173,469 | 283,549 | 144,039 |
| **합계** | **228,989** | | **170,129** |

- **overheadTokens 29,430 tok — 이 회차에 실측**(1자 프롬프트 프로브, 같은 명령·모델·샌드박스).
  호출마다 한 번씩 얹히므로 단계별로 뺀다. **다른 날 상수를 쓰지 않았다 = `█ 실측`.**
- contextTokens (count_tokens API): 측정 안 됨 — 구독 CLI 경로엔 API 키가 없다
- 비용은 **API 환산 추정치**다. 구독 계정으로 호출하므로 실제 청구액이 아니다.

## 수집 실패/누락

- Metric 'sum(rate(http_server_requests_seconds_count{application="content-service", status="401"}[1m]))' returned no series in this window; skipped.

---

# 장애 분석: 댓글 작성 실패 (traceId 6a7037546e3404569c632181052c8516)

**선행 정정**: 지정된 traceId(`6a7037546e...`)는 **성공 트레이스**다 — `http post /feeds/{feedId}/comments` status=200, outcome=SUCCESS, connection span에 `commit` 이벤트. 실제 실패는 창 안 후보 트레이스 `6a7037601a12bc2de1bbb08457bac9d2`에 있고, 두 트레이스의 대조가 그대로 근거가 된다.

## 1. 원인 후보 랭킹

1. **댓글 본문이 `tb_feed_comment.content` 컬럼 길이 한도를 초과 → MySQL 1406(Data truncation)으로 INSERT 실패, 트랜잭션 롤백**
2. **애플리케이션 계층의 본문 길이 검증 부재 + 예외 매핑 결함 (400이어야 할 입력 오류가 500으로 반환)**
3. (실패 원인 아님 / 별건) **알림 파이프라인 지연 — chat-service 컨슈머가 9.8초 동안 JDBC 커넥션 점유**

## 2. 후보별 근거

### 후보 1 — content 컬럼 길이 초과

- **근거**
  - 실패 트레이스 루트 span: `http post /feeds/{feedId}/comments`, `status=500`, `outcome=SERVER_ERROR`, `http.url=/api/feeds/145/comments`, 총 308.7ms.
  - 자식 span `query` (`status.code=STATUS_CODE_ERROR`), 속성 `error: "Data truncation: Data too long for column 'content' at row 1"`, `jdbc.query[0] = insert into tb_feed_comment (content,created_at,creator_id,...)`.
  - 같은 트레이스 `connection` span 이벤트가 `acquired` → **`rollback`**. 성공 트레이스의 동일 span은 `acquired` → **`commit`**.
  - 로그 원문: `WARN ... SqlExceptionHelper - SQL Error: 1406, SQLState: 22001`, `ERROR ... Data truncation: Data too long for column 'content' at row 1`, `[HTTP] POST /api/feeds/145/comments 500 - 306ms`.
  - **성공/실패 대조**: 동일 엔드포인트·동일 피드(145)·동일 `userId=1`에 대해 15:38:12.870 요청은 200(insert row-affected=1, 10.4ms), 11초 뒤 15:38:24.876 요청은 500. 인프라 조건이 동일한 11초 간격에서 결과가 갈렸다 → 요청 페이로드 의존.
- **확신도: 높음**
- **반증 데이터**: 없음. 오히려 인프라 원인을 배제하는 관측값이 다수 — `hikaricp_connections_active`/`pending` 전 파드 0, `up`/`mongodb_up`/`kafka_brokers` 창 전체 1, content-service GC pause rate 최대 4.2e-5 s/s.

### 후보 2 — 입력 검증 부재 + 예외 매핑

- **근거**
  - 스택트레이스가 `FeedCommentController.createComment(FeedCommentController.java:53)` → `FeedCommentService.createComment(FeedCommentService.java:64)` → `SimpleJpaRepository.save` → `SessionImpl.persist` 로 **중간에 길이 검증 없이 DB까지 도달**함을 보여준다.
  - `org.springframework.dao.DataIntegrityViolationException`이 `GlobalExceptionHandler - [api-error] handleAllException`으로 처리되고 최종 응답이 500. 클라이언트 입력 오류가 서버 오류로 표기되어, 사용자에게는 "댓글이 안 써져요"로 보이고 알림/재시도 판단도 어려워진다.
- **확신도: 중간** — 검증 부재와 500 매핑은 스택트레이스·로그로 확인되나, 실제 컬럼 정의(길이·charset)와 DTO의 `@Size` 유무는 이 데이터셋으로 확인 불가.
- **반증 데이터**: 없음. 다만 후보 1의 하위 요인이지 독립 원인은 아니다.

### 후보 3 — 알림 파이프라인 지연 (실패의 원인 아님)

- **근거**
  - 성공 트레이스에서 `kafka/user.notifications --messaging--> chat-service` `receive` span이 **9805.4ms**, 그 자식 `connection`(HikariPool-1, datasource `content`)이 **9597.6ms** 동안 유지(`acquired` 09:34.915 → `commit` 09:44.506).
  - `user-notification-service#process-notification` 9.35초, 내부에 Redis `KEYS 0` 명령 2회(`server.address: redis://...?timeout=2s`).
  - `kafka_consumergroup_lag{consumergroup="notification-processors",partition="3"}`가 1785739170·1785739185 시점에 1로 상승 후 0 복귀.
- **확신도: 댓글 실패 원인으로서는 낮음 / 지연 현상 자체는 높음**
- **반증 데이터**: 실패 트레이스에는 Kafka·chat-service span이 **아예 없다**(커밋 전 롤백이라 발행 자체가 없음). 반대로 이 9.8초 지연이 발생한 트레이스는 200으로 성공했다. 즉 인과 방향이 성립하지 않는다.

### 데이터 부족 명시

- 질문은 "최근 1시간"이나 조회 창은 **11분 15초**(06:30–06:41:15Z)다. 1시간 발생 빈도·추세는 **데이터 부족**.
- 창 안에서 확보된 댓글 실패 샘플은 **1건**. "제보"가 다수 사용자·다수 요청인지, 동일 사용자의 반복 시도인지 판단 불가.
- 수집 실패: `http_server_requests_seconds_count{application="content-service", status="401"}` 시계열 없음 → 인증 만료로 인한 실패 경로는 **검증하지 못했다**. 이 공백을 반영해 "댓글 실패 = 전부 1406" 이라는 일반화는 하지 않는다(본 결론은 확인된 1건에 한정).
- 실패 요청의 실제 본문 길이가 로그에 없어 초과량 미상.

## 3. 권장 다음 조치

즉시 확인 (읽기 전용)

1. 컬럼 정의 확인 — `SELECT CHARACTER_MAXIMUM_LENGTH, DATA_TYPE, CHARACTER_SET_NAME FROM information_schema.COLUMNS WHERE TABLE_NAME='tb_feed_comment' AND COLUMN_NAME='content';` (utf8mb4면 한글 1자=3~4바이트이므로 체감 한도가 훨씬 짧다)
2. 발생 범위 확대 조회 — Loki: `count_over_time({container="content-service"} |= "SQL Error: 1406" [1h])`, 그리고 `{container="content-service"} |= "POST /api/feeds" |= " 500 "` 로 1시간 창 재조회
3. 누락 메트릭 재수집 — content-service 401/4xx 시계열이 왜 없는지(레이블 `application` vs `job` 불일치 가능성) 확인 후 재질의
4. 코드 확인 — `FeedCommentService.java:64` 및 요청 DTO에 `@Size` / 엔티티 `@Column(length=...)` 존재 여부

단기 조치

5. 요청 DTO에 `@Size(max=<컬럼 한도>)` 추가, 초과 시 **400 + 명확한 메시지** 반환
6. `GlobalExceptionHandler`에서 `DataIntegrityViolationException`을 `handleAllException`(500)이 아닌 400으로 분리 매핑
7. `RequestLoggingFilter`에 본문 **길이만**(내용 제외) 로깅 추가 — 재발 시 초과량 즉시 판별
8. 프론트엔드 입력창 `maxlength` 동기화

중기 조치

9. 한도 자체가 비즈니스 요구에 못 미치면 `content` 컬럼 확장 마이그레이션(VARCHAR 확대 또는 TEXT) 검토
10. 별건 티켓: chat-service `processNotification`이 Hikari 커넥션을 9.6초 점유하는 문제 — Redis `KEYS` 사용(O(N) 블로킹, `SCAN`으로 대체)과 트랜잭션 경계 축소 검토. 현재 pending=0이라 장애는 아니나 부하 시 커넥션 고갈 위험
11. 알림 발행 경로에서 매 요청마다 Kafka `AdminClientConfig`/`ProducerConfig` 초기화 로그가 찍히는 점(성공 트레이스 15:38:13.572~13.910, 발행까지 574.8ms) — 프로듀서 재생성 여부 확인

---

## 관측 증거 (Evidence)

> 이 조사가 실제로 본 값이다. 원본 응답 전체는 `reports/raw/6a7037546e3404569c632181052c8516-*.json`에 있다.

### 호출 그래프 (트레이스에서 추출)

```
chat-service --db--> mongodb  7회  최대 100.1ms  [insert, find]
chat-service --db--> redis  5회  최대 5.0ms  [KEYS, INFO, CLIENT]
content-service --db--> redis  5회  최대 1.8ms  [GET, SET, INFO]
chat-service --jdbc--> mysql/content (HikariPool-1)  1회  최대 9597.6ms
    events: acquired, commit
content-service --jdbc--> mysql/content (HikariPool-1)  24회  최대 643.7ms
    error: Data truncation: Data too long for column 'content' at row 1
    events: acquired, commit, rollback
content-service --messaging--> kafka/user.notifications  1회  최대 574.8ms  [publish]
kafka/user.notifications --messaging--> chat-service  1회  최대 9805.4ms  [receive]
content-service --service--> auth-service  2회  최대 175.1ms
```

### span (duration 상위 15 / 전체 46)

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
| 175.06 | content-service | `http get` | 2026-08-03T06:38:12.983390Z |
| 163.08 | auth-service | `http get /external/users/{userid}` | 2026-08-03T06:38:12.996443Z |
| 158.72 | auth-service | `secured request` | 2026-08-03T06:38:12.998726Z |
| 100.09 | chat-service | `find toychat` | 2026-08-03T06:38:18.598952Z |
| 22.29 | content-service | `query` | 2026-08-03T06:38:13.301560Z |

### 로그 원문 (60 / 전체 718줄)

전체가 상한을 넘어 ERROR/WARN 줄을 우선 발췌했다. 나머지는 원본 파일에 있다.

```
2026-08-03T06:38:12.772761348Z  [auth-service]  [2m2026-08-03 15:38:12[0;39m [2m[http-nio-8081-exec-6][0;39m [33m WARN [traceId=6a7037525a9a598fc4faf932a17b3dc4,spanId=c4faf932a17b3dc4,userId=NONE][0;39m [36mc.e.t.a.c.f.RequestLoggingFilter[0;39m [2m-[0;39m [HTTP-SLOW] POST /api/login 200 - 2013ms
2026-08-03T06:38:12.772761348Z  [auth-service]  [2m2026-08-03 15:38:12[0;39m [2m[http-nio-8081-exec-6][0;39m [33m WARN [traceId=6a7037525a9a598fc4faf932a17b3dc4,spanId=c4faf932a17b3dc4,userId=NONE][0;39m [36mc.e.t.a.c.f.RequestLoggingFilter[0;39m [2m-[0;39m [HTTP-SLOW] POST /api/login 200 - 2013ms
2026-08-03T06:38:12.772761348Z  [auth-service]  [2m2026-08-03 15:38:12[0;39m [2m[http-nio-8081-exec-6][0;39m [33m WARN [traceId=6a7037525a9a598fc4faf932a17b3dc4,spanId=c4faf932a17b3dc4,userId=NONE][0;39m [36mc.e.t.a.c.f.RequestLoggingFilter[0;39m [2m-[0;39m [HTTP-SLOW] POST /api/login 200 - 2013ms
2026-08-03T06:38:25.122215521Z  [content-service]  2026-08-03 15:38:25.121 [http-nio-8082-exec-2]  WARN [traceId=6a7037601a12bc2de1bbb08457bac9d2,spanId=aa6a9b00b92c1d5f,userId=1] o.h.e.jdbc.spi.SqlExceptionHelper - SQL Error: 1406, SQLState: 22001
2026-08-03T06:38:25.122215521Z  [content-service]  2026-08-03 15:38:25.121 [http-nio-8082-exec-2]  WARN [traceId=6a7037601a12bc2de1bbb08457bac9d2,spanId=aa6a9b00b92c1d5f,userId=1] o.h.e.jdbc.spi.SqlExceptionHelper - SQL Error: 1406, SQLState: 22001
2026-08-03T06:38:25.122215521Z  [content-service]  2026-08-03 15:38:25.121 [http-nio-8082-exec-2]  WARN [traceId=6a7037601a12bc2de1bbb08457bac9d2,spanId=aa6a9b00b92c1d5f,userId=1] o.h.e.jdbc.spi.SqlExceptionHelper - SQL Error: 1406, SQLState: 22001
2026-08-03T06:38:25.122234185Z  [content-service]  2026-08-03 15:38:25.121 [http-nio-8082-exec-2] ERROR [traceId=6a7037601a12bc2de1bbb08457bac9d2,spanId=aa6a9b00b92c1d5f,userId=1] o.h.e.jdbc.spi.SqlExceptionHelper - Data truncation: Data too long for column 'content' at row 1
2026-08-03T06:38:25.122234185Z  [content-service]  2026-08-03 15:38:25.121 [http-nio-8082-exec-2] ERROR [traceId=6a7037601a12bc2de1bbb08457bac9d2,spanId=aa6a9b00b92c1d5f,userId=1] o.h.e.jdbc.spi.SqlExceptionHelper - Data truncation: Data too long for column 'content' at row 1
2026-08-03T06:38:25.122234185Z  [content-service]  2026-08-03 15:38:25.121 [http-nio-8082-exec-2] ERROR [traceId=6a7037601a12bc2de1bbb08457bac9d2,spanId=aa6a9b00b92c1d5f,userId=1] o.h.e.jdbc.spi.SqlExceptionHelper - Data truncation: Data too long for column 'content' at row 1
2026-08-03T06:38:25.176330204Z  [content-service]  2026-08-03 15:38:25.151 [http-nio-8082-exec-2]  WARN [traceId=6a7037601a12bc2de1bbb08457bac9d2,spanId=aa6a9b00b92c1d5f,userId=1] c.e.t.a.c.e.GlobalExceptionHandler - [api-error] handleAllException
2026-08-03T06:38:25.176330204Z  [content-service]  2026-08-03 15:38:25.151 [http-nio-8082-exec-2]  WARN [traceId=6a7037601a12bc2de1bbb08457bac9d2,spanId=aa6a9b00b92c1d5f,userId=1] c.e.t.a.c.e.GlobalExceptionHandler - [api-error] handleAllException
2026-08-03T06:38:25.176389674Z  [content-service]  org.springframework.dao.DataIntegrityViolationException: could not execute statement [Data truncation: Data too long for column 'content' at row 1] [insert into tb_feed_comment (content,created_at,creator_id,creator_nickname,creator_profile_url,deleted,deleted_at,feed_id,parent_comment_id,updated_at) values (?,?,?,?,?,?,?,?,?,?)]; SQL [insert into tb_feed_comment (content,created_at,creator_id,creator_nickname,creator_profile_url,deleted,deleted_at,feed_id,parent_comment_id,updated_at) values (?,?,?,?,?,?,?,?,?,?)]
2026-08-03T06:38:25.176389674Z  [content-service]  org.springframework.dao.DataIntegrityViolationException: could not execute statement [Data truncation: Data too long for column 'content' at row 1] [insert into tb_feed_comment (content,created_at,creator_id,creator_nickname,creator_profile_url,deleted,deleted_at,feed_id,parent_comment_id,updated_at) values (?,?,?,?,?,?,?,?,?,?)]; SQL [insert into tb_feed_comment (content,created_at,creator_id,creator_nickname,creator_profile_url,deleted,deleted_at,feed_id,parent_comment_id,updated_at) values (?,?,?,?,?,?,?,?,?,?)]
2026-08-03T06:38:25.176389674Z  [content-service]  org.springframework.dao.DataIntegrityViolationException: could not execute statement [Data truncation: Data too long for column 'content' at row 1] [insert into tb_feed_comment (content,created_at,creator_id,creator_nickname,creator_profile_url,deleted,deleted_at,feed_id,parent_comment_id,updated_at) values (?,?,?,?,?,?,?,?,?,?)]; SQL [insert into tb_feed_comment (content,created_at,creator_id,creator_nickname,creator_profile_url,deleted,deleted_at,feed_id,parent_comment_id,updated_at) values (?,?,?,?,?,?,?,?,?,?)]
2026-08-03T06:38:25.176411834Z  [content-service]  at org.springframework.orm.jpa.vendor.HibernateJpaDialect.convertHibernateAccessException(HibernateJpaDialect.java:293)
2026-08-03T06:38:25.176411834Z  [content-service]  at org.springframework.orm.jpa.vendor.HibernateJpaDialect.convertHibernateAccessException(HibernateJpaDialect.java:293)
2026-08-03T06:38:25.176411834Z  [content-service]  at org.springframework.orm.jpa.vendor.HibernateJpaDialect.convertHibernateAccessException(HibernateJpaDialect.java:293)
2026-08-03T06:38:25.176415091Z  [content-service]  at org.springframework.orm.jpa.vendor.HibernateJpaDialect.translateExceptionIfPossible(HibernateJpaDialect.java:241)
2026-08-03T06:38:25.176415091Z  [content-service]  at org.springframework.orm.jpa.vendor.HibernateJpaDialect.translateExceptionIfPossible(HibernateJpaDialect.java:241)
2026-08-03T06:38:25.176415091Z  [content-service]  at org.springframework.orm.jpa.vendor.HibernateJpaDialect.translateExceptionIfPossible(HibernateJpaDialect.java:241)
2026-08-03T06:38:25.176418448Z  [content-service]  at org.springframework.orm.jpa.AbstractEntityManagerFactoryBean.translateExceptionIfPossible(AbstractEntityManagerFactoryBean.java:560)
2026-08-03T06:38:25.176418448Z  [content-service]  at org.springframework.orm.jpa.AbstractEntityManagerFactoryBean.translateExceptionIfPossible(AbstractEntityManagerFactoryBean.java:560)
2026-08-03T06:38:25.176418448Z  [content-service]  at org.springframework.orm.jpa.AbstractEntityManagerFactoryBean.translateExceptionIfPossible(AbstractEntityManagerFactoryBean.java:560)
2026-08-03T06:38:25.176421464Z  [content-service]  at org.springframework.dao.support.ChainedPersistenceExceptionTranslator.translateExceptionIfPossible(ChainedPersistenceExceptionTranslator.java:61)
2026-08-03T06:38:25.176421464Z  [content-service]  at org.springframework.dao.support.ChainedPersistenceExceptionTranslator.translateExceptionIfPossible(ChainedPersistenceExceptionTranslator.java:61)
2026-08-03T06:38:25.176421464Z  [content-service]  at org.springframework.dao.support.ChainedPersistenceExceptionTranslator.translateExceptionIfPossible(ChainedPersistenceExceptionTranslator.java:61)
2026-08-03T06:38:25.176427161Z  [content-service]  at org.springframework.dao.support.PersistenceExceptionTranslationInterceptor.invoke(PersistenceExceptionTranslationInterceptor.java:160)
2026-08-03T06:38:25.176427161Z  [content-service]  at org.springframework.dao.support.PersistenceExceptionTranslationInterceptor.invoke(PersistenceExceptionTranslationInterceptor.java:160)
2026-08-03T06:38:25.176427161Z  [content-service]  at org.springframework.dao.support.PersistenceExceptionTranslationInterceptor.invoke(PersistenceExceptionTranslationInterceptor.java:160)
2026-08-03T06:38:25.176687243Z  [content-service]  at org.springframework.security.web.access.ExceptionTranslationFilter.doFilter(ExceptionTranslationFilter.java:126)
2026-08-03T06:38:25.176687243Z  [content-service]  at org.springframework.security.web.access.ExceptionTranslationFilter.doFilter(ExceptionTranslationFilter.java:126)
2026-08-03T06:38:25.176687243Z  [content-service]  at org.springframework.security.web.access.ExceptionTranslationFilter.doFilter(ExceptionTranslationFilter.java:126)
2026-08-03T06:38:25.176690048Z  [content-service]  at org.springframework.security.web.access.ExceptionTranslationFilter.doFilter(ExceptionTranslationFilter.java:120)
2026-08-03T06:38:25.176690048Z  [content-service]  at org.springframework.security.web.access.ExceptionTranslationFilter.doFilter(ExceptionTranslationFilter.java:120)
2026-08-03T06:38:25.176690048Z  [content-service]  at org.springframework.security.web.access.ExceptionTranslationFilter.doFilter(ExceptionTranslationFilter.java:120)
2026-08-03T06:38:25.177081412Z  [content-service]  Caused by: org.hibernate.exception.DataException: could not execute statement [Data truncation: Data too long for column 'content' at row 1] [insert into tb_feed_comment (content,created_at,creator_id,creator_nickname,creator_profile_url,deleted,deleted_at,feed_id,parent_comment_id,updated_at) values (?,?,?,?,?,?,?,?,?,?)]
2026-08-03T06:38:25.177081412Z  [content-service]  Caused by: org.hibernate.exception.DataException: could not execute statement [Data truncation: Data too long for column 'content' at row 1] [insert into tb_feed_comment (content,created_at,creator_id,creator_nickname,creator_profile_url,deleted,deleted_at,feed_id,parent_comment_id,updated_at) values (?,?,?,?,?,?,?,?,?,?)]
2026-08-03T06:38:25.177081412Z  [content-service]  Caused by: org.hibernate.exception.DataException: could not execute statement [Data truncation: Data too long for column 'content' at row 1] [insert into tb_feed_comment (content,created_at,creator_id,creator_nickname,creator_profile_url,deleted,deleted_at,feed_id,parent_comment_id,updated_at) values (?,?,?,?,?,?,?,?,?,?)]
2026-08-03T06:38:25.177083959Z  [content-service]  at org.hibernate.exception.internal.SQLExceptionTypeDelegate.convert(SQLExceptionTypeDelegate.java:55)
2026-08-03T06:38:25.177083959Z  [content-service]  at org.hibernate.exception.internal.SQLExceptionTypeDelegate.convert(SQLExceptionTypeDelegate.java:55)
2026-08-03T06:38:25.177083959Z  [content-service]  at org.hibernate.exception.internal.SQLExceptionTypeDelegate.convert(SQLExceptionTypeDelegate.java:55)
2026-08-03T06:38:25.177086516Z  [content-service]  at org.hibernate.exception.internal.StandardSQLExceptionConverter.convert(StandardSQLExceptionConverter.java:58)
2026-08-03T06:38:25.177086516Z  [content-service]  at org.hibernate.exception.internal.StandardSQLExceptionConverter.convert(StandardSQLExceptionConverter.java:58)
2026-08-03T06:38:25.177086516Z  [content-service]  at org.hibernate.exception.internal.StandardSQLExceptionConverter.convert(StandardSQLExceptionConverter.java:58)
2026-08-03T06:38:25.177088947Z  [content-service]  at org.hibernate.engine.jdbc.spi.SqlExceptionHelper.convert(SqlExceptionHelper.java:108)
2026-08-03T06:38:25.177088947Z  [content-service]  at org.hibernate.engine.jdbc.spi.SqlExceptionHelper.convert(SqlExceptionHelper.java:108)
2026-08-03T06:38:25.177088947Z  [content-service]  at org.hibernate.engine.jdbc.spi.SqlExceptionHelper.convert(SqlExceptionHelper.java:108)
2026-08-03T06:38:25.177294466Z  [content-service]  at org.springframework.dao.support.PersistenceExceptionTranslationInterceptor.invoke(PersistenceExceptionTranslationInterceptor.java:138)
2026-08-03T06:38:25.177294466Z  [content-service]  at org.springframework.dao.support.PersistenceExceptionTranslationInterceptor.invoke(PersistenceExceptionTranslationInterceptor.java:138)
2026-08-03T06:38:25.177294466Z  [content-service]  at org.springframework.dao.support.PersistenceExceptionTranslationInterceptor.invoke(PersistenceExceptionTranslationInterceptor.java:138)
2026-08-03T06:38:25.177315988Z  [content-service]  at com.mysql.cj.jdbc.exceptions.SQLExceptionsMapping.translateException(SQLExceptionsMapping.java:96)
2026-08-03T06:38:25.177315988Z  [content-service]  at com.mysql.cj.jdbc.exceptions.SQLExceptionsMapping.translateException(SQLExceptionsMapping.java:96)
2026-08-03T06:38:25.177315988Z  [content-service]  at com.mysql.cj.jdbc.exceptions.SQLExceptionsMapping.translateException(SQLExceptionsMapping.java:96)
2026-08-03T06:38:25.182547266Z  [content-service]  2026-08-03 15:38:25.182 [http-nio-8082-exec-2] ERROR [traceId=6a7037601a12bc2de1bbb08457bac9d2,spanId=e1bbb08457bac9d2,userId=NONE] c.e.t.a.c.f.RequestLoggingFilter - [HTTP] POST /api/feeds/145/comments 500 - 306ms
2026-08-03T06:38:25.182547266Z  [content-service]  2026-08-03 15:38:25.182 [http-nio-8082-exec-2] ERROR [traceId=6a7037601a12bc2de1bbb08457bac9d2,spanId=e1bbb08457bac9d2,userId=NONE] c.e.t.a.c.f.RequestLoggingFilter - [HTTP] POST /api/feeds/145/comments 500 - 306ms
2026-08-03T06:38:25.182547266Z  [content-service]  2026-08-03 15:38:25.182 [http-nio-8082-exec-2] ERROR [traceId=6a7037601a12bc2de1bbb08457bac9d2,spanId=e1bbb08457bac9d2,userId=NONE] c.e.t.a.c.f.RequestLoggingFilter - [HTTP] POST /api/feeds/145/comments 500 - 306ms
2026-08-03T06:41:09.247558202Z  [auth-service]  [2m2026-08-03 15:41:09[0;39m [2m[http-nio-8081-exec-10][0;39m [33m WARN [traceId=6a703802f17ebda461e26f109a5d5a30,spanId=1c72fc7b3300ce1c,userId=1][0;39m [36morg.hibernate.orm.query[0;39m [2m-[0;39m HHH90003004: firstResult/maxResults specified with collection fetch; applying in memory
2026-08-03T06:41:09.247558202Z  [auth-service]  [2m2026-08-03 15:41:09[0;39m [2m[http-nio-8081-exec-10][0;39m [33m WARN [traceId=6a703802f17ebda461e26f109a5d5a30,spanId=1c72fc7b3300ce1c,userId=1][0;39m [36morg.hibernate.orm.query[0;39m [2m-[0;39m HHH90003004: firstResult/maxResults specified with collection fetch; applying in memory
2026-08-03T06:41:09.366268004Z  [auth-service]  [2m2026-08-03 15:41:09[0;39m [2m[http-nio-8081-exec-10][0;39m [33m WARN [traceId=6a703802f17ebda461e26f109a5d5a30,spanId=61e26f109a5d5a30,userId=NONE][0;39m [36mc.e.t.a.c.f.RequestLoggingFilter[0;39m [2m-[0;39m [HTTP-SLOW] GET /api/user/1/following 200 - 2561ms
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

