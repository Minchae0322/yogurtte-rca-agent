# RCA Report — `scan-1785286200`

| 항목 | 값 |
|---|---|
| 모드 | rca |
| 질문 | 최근 1시간 안에 피드 작성이 실패했다는 제보가 있다. 원인을 조사해줘 |
| 시각 | 2026-08-03T13:30:45.632918Z |
| provider | claude-cli |
| model | `claude-opus-5` · turns 1 |
| prompt | `./prompts/system-prompt.md` |
| tokens | in 82260 (cacheRead 18,133 · cacheCreate 64,125) / out 8470 · cost $0.9083 |
| elapsed | total 132922ms (tempo 577 · loki 317 · mimir 612 · assemble 18 · llm 123608) |

## 탐색 (Triage)

| 항목 | 값 |
|---|---|
| 시간창 해석 | 명시적 from/to |
| 시간창 확신도 | EXACT |
| 스윕 창 | 2026-07-29T00:03:43Z ~ 2026-07-29T01:03:43Z |
| 좁힌 창 | 2026-07-29T00:50:00Z ~ 2026-07-29T01:03:43Z |
| 대상 | content-service |
| traceId | 6a694fc02926ea82d0bdbb4434237626 |
| 트레이스 후보 | 1건 |
| 장애 후보 | 4건 · 선택 INC-3, INC-4 |
| 계획 파싱 | 성공 |
| 스윕 컨텍스트 | 후보 + 원본 (A) |
| prompt | `./prompts/triage-prompt.md` |
| tokens | in 46772 / out 1525 · cost $0.3809 |
| chars | 컨텍스트 43,274 + 프롬프트 1,399 = **44,673** |
| elapsed | survey 766ms · llm 27889ms |

**선정 이유**: 질문의 증상(피드 작성 실패)과 엔드포인트·시각이 정확히 일치하는 유일한 신호가 content-service의 POST /feeds 에러 트레이스와 같은 5분 버킷의 ERROR/WARN 로그이며, 이 둘은 한 사건의 상·하류 지문이므로 함께 조사한다.

**근거**

- Tempo error 검색 1건 전부가 content-service 'http post /feeds' (2026-07-29T00:56:32.321Z, 74ms, traceId 6a694fc02926ea82d0bdbb4434237626) — 질문의 '피드 작성 실패'와 엔드포인트가 그대로 일치
- 해당 트레이스 serviceStats: content-service spanCount 23 / errorCount 1, 실패 span 10c8313bf6e4c25c는 요청 시작 +49ms 지점에서 6.2ms 만에 종료 — 타임아웃이 아니라 즉시 실패(예외·4xx/5xx 응답)의 지문
- Loki ERROR/WARN이 창 전체에서 content-service 4건(00:55:00~01:00:00Z)뿐이며, 트레이스 에러 시각 00:56:32Z를 포함하는 같은 버킷 — 로그와 트레이스가 같은 사건을 두 채널로 본 것
- Tempo 지연 검색 '{ duration > 3s && status != error }' 0건 → 에러 없는 지연 장애는 이 창에 없었고, 증상은 순수 실패다
- min_over_time(up[5m])에서 content-service pod 2개(10.42.1.35, 10.42.3.41)가 창 전체 1 유지 → 프로세스 사망·재기동으로 트레이스가 누락된 경우는 아니며, 살아 있는 상태에서 /feeds만 실패한 것

**스윕이 찾은 트레이스** (고른 것은 6a694fc02926ea82d0bdbb4434237626)

| traceId | 채널 | root service | root span | ms |
|---|---|---|---|---:|
| `6a694fc02926ea82d0bdbb4434237626` ←선택 | error | content-service | http post /feeds | 74 |

**장애 후보** (코드가 신호를 묶은 것 · 창은 여기서 계산됨)

## INC-1  kafka  |  kafka_consumergroup_lag
- 구간: 2026-07-29T00:03:43Z ~ 2026-07-29T01:03:43Z  (MIMIR · 집계 해상도만큼 흐림)
- kafka_consumergroup_lag{consumergroup=chat-service-fcm-tokens, partition=0, topic=user.fcm-tokens} 가 0이었다 (2026-07-29T00:03:43Z ~ 2026-07-29T01:03:43Z)
- kafka_consumergroup_lag{consumergroup=chat-service-fcm-tokens, partition=1, topic=user.fcm-tokens} 가 0이었다 (2026-07-29T00:03:43Z ~ 2026-07-29T01:03:43Z)
- kafka_consumergroup_lag{consumergroup=chat-service-fcm-tokens, partition=2, topic=user.fcm-tokens} 가 0이었다 (2026-07-29T00:03:43Z ~ 2026-07-29T01:03:43Z)
- kafka_consumergroup_lag{consumergroup=chat-service-notification-settings, partition=0, topic=user.notification-settings} 가 0이었다 (2026-07-29T00:03:43Z ~ 2026-07-29T01:03:43Z)
- kafka_consumergroup_lag{consumergroup=chat-service-notification-settings, partition=1, topic=user.notification-settings} 가 0이었다 (2026-07-29T00:03:43Z ~ 2026-07-29T01:03:43Z)
- kafka_consumergroup_lag{consumergroup=chat-service-notification-settings, partition=2, topic=user.notification-settings} 가 0이었다 (2026-07-29T00:03:43Z ~ 2026-07-29T01:03:43Z)
- kafka_consumergroup_lag{consumergroup=db-writer, partition=0, topic=chat.messages} 가 0이었다 (2026-07-29T00:03:43Z ~ 2026-07-29T01:03:43Z)
- kafka_consumergroup_lag{consumergroup=db-writer, partition=1, topic=chat.messages} 가 0이었다 (2026-07-29T00:03:43Z ~ 2026-07-29T01:03:43Z)
- kafka_consumergroup_lag{consumergroup=db-writer, partition=11, topic=chat.messages} 가 0이었다 (2026-07-29T00:03:43Z ~ 2026-07-29T01:03:43Z)
- kafka_consumergroup_lag{consumergroup=db-writer, partition=3, topic=chat.messages} 가 0이었다 (2026-07-29T00:03:43Z ~ 2026-07-29T01:03:43Z)
- kafka_consumergroup_lag{consumergroup=db-writer, partition=4, topic=chat.messages} 가 0이었다 (2026-07-29T00:03:43Z ~ 2026-07-29T01:03:43Z)
- kafka_consumergroup_lag{consumergroup=db-writer, partition=5, topic=chat.messages} 가 0이었다 (2026-07-29T00:03:43Z ~ 2026-07-29T01:03:43Z)
- kafka_consumergroup_lag{consumergroup=db-writer, partition=7, topic=chat.messages} 가 0이었다 (2026-07-29T00:03:43Z ~ 2026-07-29T01:03:43Z)
- kafka_consumergroup_lag{consumergroup=db-writer, partition=8, topic=chat.messages} 가 0이었다 (2026-07-29T00:03:43Z ~ 2026-07-29T01:03:43Z)
- kafka_consumergroup_lag{consumergroup=db-writer-retry-1000, partition=0, topic=chat.messages-retry-1000} 가 0이었다 (2026-07-29T00:03:43Z ~ 2026-07-29T01:03:43Z)
- kafka_consumergroup_lag{consumergroup=db-writer-retry-2000, partition=0, topic=chat.messages-retry-2000} 가 0이었다 (2026-07-29T00:03:43Z ~ 2026-07-29T01:03:43Z)
- kafka_consumergroup_lag{consumergroup=db-writer-retry-4000, partition=0, topic=chat.messages-retry-4000} 가 0이었다 (2026-07-29T00:03:43Z ~ 2026-07-29T01:03:43Z)
- kafka_consumergroup_lag{consumergroup=notification, partition=0, topic=chat.messages} 가 0이었다 (2026-07-29T00:03:43Z ~ 2026-07-29T01:03:43Z)
- kafka_consumergroup_lag{consumergroup=notification, partition=1, topic=chat.messages} 가 0이었다 (2026-07-29T00:03:43Z ~ 2026-07-29T01:03:43Z)
- kafka_consumergroup_lag{consumergroup=notification, partition=11, topic=chat.messages} 가 0이었다 (2026-07-29T00:03:43Z ~ 2026-07-29T01:03:43Z)
- kafka_consumergroup_lag{consumergroup=notification, partition=2, topic=chat.messages} 가 0이었다 (2026-07-29T00:03:43Z ~ 2026-07-29T01:03:43Z)
- kafka_consumergroup_lag{consumergroup=notification, partition=3, topic=chat.messages} 가 0이었다 (2026-07-29T00:03:43Z ~ 2026-07-29T01:03:43Z)
- kafka_consumergroup_lag{consumergroup=notification, partition=4, topic=chat.messages} 가 0이었다 (2026-07-29T00:03:43Z ~ 2026-07-29T01:03:43Z)
- kafka_consumergroup_lag{consumergroup=notification, partition=5, topic=chat.messages} 가 0이었다 (2026-07-29T00:03:43Z ~ 2026-07-29T01:03:43Z)
- kafka_consumergroup_lag{consumergroup=notification, partition=7, topic=chat.messages} 가 0이었다 (2026-07-29T00:03:43Z ~ 2026-07-29T01:03:43Z)
- kafka_consumergroup_lag{consumergroup=notification, partition=8, topic=chat.messages} 가 0이었다 (2026-07-29T00:03:43Z ~ 2026-07-29T01:03:43Z)
- kafka_consumergroup_lag{consumergroup=notification-processors, partition=0, topic=user.notifications} 가 0이었다 (2026-07-29T00:03:43Z ~ 2026-07-29T01:03:43Z)
- kafka_consumergroup_lag{consumergroup=notification-processors, partition=1, topic=user.notifications} 가 0이었다 (2026-07-29T00:03:43Z ~ 2026-07-29T01:03:43Z)
- kafka_consumergroup_lag{consumergroup=notification-processors, partition=2, topic=user.notifications} 가 0이었다 (2026-07-29T00:03:43Z ~ 2026-07-29T01:03:43Z)
- kafka_consumergroup_lag{consumergroup=notification-processors, partition=3, topic=user.notifications} 가 0이었다 (2026-07-29T00:03:43Z ~ 2026-07-29T01:03:43Z)
- kafka_consumergroup_lag{consumergroup=notification-processors, partition=4, topic=user.notifications} 가 0이었다 (2026-07-29T00:03:43Z ~ 2026-07-29T01:03:43Z)
- kafka_consumergroup_lag{consumergroup=notification-processors, partition=5, topic=user.notifications} 가 0이었다 (2026-07-29T00:03:43Z ~ 2026-07-29T01:03:43Z)
- kafka_consumergroup_lag{consumergroup=notification-recovery, partition=0, topic=user.notifications.dlq} 가 0이었다 (2026-07-29T00:03:43Z ~ 2026-07-29T01:03:43Z)
- kafka_consumergroup_lag{consumergroup=notification-recovery, partition=2, topic=user.notifications.dlq} 가 0이었다 (2026-07-29T00:03:43Z ~ 2026-07-29T01:03:43Z)
- kafka_consumergroup_lag{consumergroup=notification-retry-2000, partition=0, topic=chat.messages-retry-2000} 가 0이었다 (2026-07-29T00:03:43Z ~ 2026-07-29T01:03:43Z)
- kafka_consumergroup_lag{consumergroup=notification-retry-4000, partition=0, topic=chat.messages-retry-4000} 가 0이었다 (2026-07-29T00:03:43Z ~ 2026-07-29T01:03:43Z)
- 같은 시각의 다른 후보: INC-2, INC-3, INC-4  (인과 여부는 판단하지 않았다)

## INC-2  chat-service  |  websocket_active_users
- 구간: 2026-07-29T00:03:43Z ~ 2026-07-29T01:03:43Z  (MIMIR · 집계 해상도만큼 흐림)
- websocket_active_users{container=chat-service, namespace=default, pod=chat-service-857c54dd97-s5fbl} 가 0이었다 (2026-07-29T00:03:43Z ~ 2026-07-29T01:03:43Z)
- 같은 시각의 다른 후보: INC-1, INC-3, INC-4  (인과 여부는 판단하지 않았다)

## INC-3  content-service  |  ERROR/WARN
- 구간: 2026-07-29T00:55:00Z ~ 2026-07-29T01:00:00Z  (LOKI · 집계 해상도만큼 흐림)
- ERROR/WARN 4건 (2026-07-29T00:55:00Z ~ 2026-07-29T01:00:00Z)
- 같은 시각의 다른 후보: INC-1, INC-2, INC-4  (인과 여부는 판단하지 않았다)

## INC-4  content-service  |  http post /feeds
- 구간: 2026-07-29T00:56:32.321490Z ~ 2026-07-29T00:56:32.395490Z  (TEMPO · 시각 정확)
- content-service http post /feeds 74ms (error 채널)
- traceId: 6a694fc02926ea82d0bdbb4434237626
- 같은 시각의 다른 후보: INC-1, INC-2, INC-3  (인과 여부는 판단하지 않았다)

**기각한 후보**

- INC-1 — kafka_consumergroup_lag가 창 전체에서 0(미할당 파티션은 -1)으로 평탄 — 값이 0인 것은 컨슈머가 밀리지 않았다는 정상 상태이고, kafka_brokers도 계속 1이며 chat.messages·user.* 토픽은 피드 작성 경로와도 무관하다.
- INC-2 — websocket_active_users가 창 시작부터 끝까지 0으로 평탄해 '떨어진' 변화가 없고, chat-service up도 내내 1이라 접속 사용자 부재일 뿐 — 피드 작성(content-service HTTP 경로)과 연결되는 신호가 없다.

**보정 기록**

- 창을 후보 [INC-3, INC-4] 의 신호 시각에서 계산했다 (2026-07-29T00:50:00Z ~ 2026-07-29T01:03:43Z)

**스윕 무신호/실패**

- Tempo 지연 검색 '{ duration > 3s && status != error }'이 이 창에서 0건이다. 임계값보다 느린 요청이 없었다는 뜻이다.

## 수집 범위 (Coverage)

- **window**: 2026-07-29T00:50:00Z ~ 2026-07-29T01:03:43Z (823s)
- **trace**: 48,739B / 45 spans
- **창 안 후보 트레이스**: 10건 / 48,739B
- **logs**: errwarn=39,948B · traceId=5,730B
- **metrics**: 8 수집 / 101,958B, 누락 [sum(rate(http_server_requests_seconds_count{application="content-service", status="401"}[1m]))]
- **context**: 119,185 chars (+ 시스템 프롬프트 576 chars)

### 토큰 축 (개선 지표)

| 단계 | 총 in | chars | 컨텍스트 토큰 |
|---|---:|---:|---:|
| 탐색 | 46,772 | 44,673 | 25,143 |
| 분석 | 82,260 | 119,761 | 60,631 |
| **합계** | **129,032** | | **85,774** |

- **overheadTokens 21,629 tok — 이 회차에 실측**(1자 프롬프트 프로브, 같은 명령·모델·샌드박스).
  호출마다 한 번씩 얹히므로 단계별로 뺀다. **다른 날 상수를 쓰지 않았다 = `█ 실측`.**
- contextTokens (count_tokens API): 측정 안 됨 — 구독 CLI 경로엔 API 키가 없다
- 비용은 **API 환산 추정치**다. 구독 계정으로 호출하므로 실제 청구액이 아니다.

## 수집 실패/누락

- Metric 'sum(rate(http_server_requests_seconds_count{application="content-service", status="401"}[1m]))' returned no series in this window; skipped.

---

## 1. 원인 후보 랭킹 (최대 3개)

1. **content-service 피드 생성 로직에서 한 요청 안에 동일 해시태그가 두 번 연결되어 `uk_feed_hashtag` 유니크 제약 위반 → 트랜잭션 전체 롤백 → HTTP 500** (애플리케이션 버그, 입력 dedupe 부재)
2. **예외 처리·트랜잭션 경계 문제로 부분 실패가 요청 전체 실패로 확대**: 제약 위반이 `GlobalExceptionHandler.handleAllException`으로 떨어져 4xx가 아닌 500으로 반환되고, 이미 성공한 feed/첨부파일 INSERT까지 rollback
3. **영향 범위(실패율·영향 사용자 수) 판단은 데이터 부족** — 원인 자체는 1번으로 특정되지만, "얼마나 많이 실패했는가"를 뒷받침할 관측값이 없음

---

## 2. 후보별 근거

### 후보 1 — 동일 요청 내 해시태그 중복으로 인한 유니크 제약 위반

**근거**

trace `6a694fc02926ea82d0bdbb4434237626` (content-service, pod `content-service-6c5fff897-qnxk6`, `http post /feeds`, `status=500`, `outcome=SERVER_ERROR`, 74.3ms) 의 JDBC span을 시간순으로 재구성하면:

| 시각(ns 기준 상대) | span | 내용 |
|---|---|---|
| …592.330 | query | `select ... from categories c1_0 where c1_0.category_id=?` |
| …592.336 | query | `insert into tb_feed (...)` → `jdbc.row-affected=1` |
| …592.338 | generated-keys | **`jdbc.generated-keys=151`** (= feed_id 151) |
| …592.339 / .342 | query ×2 | `insert into tb_feed_attachment_file (...)` → 각 1건 (keys 231, 232) |
| …592.346 | query | `select ... from tb_hashtags h1_0 where h1_0.name=?` |
| …592.347 | result-set | **`jdbc.row-count=0`** (해시태그 없음) |
| …592.350 | query | `insert into tb_hashtags (...)` → 1건 |
| …592.352 | generated-keys | **`jdbc.generated-keys=174`** |
| …592.355 | query | `insert into tb_feed_hashtags (...)` → **`jdbc.row-affected=1`** (= 151-174 연결 **성공**) |
| …592.361 | query | `update tb_hashtags set updated_at=?,usage_count=? where id=?` |
| …592.364 | query | `select ... from tb_hashtags h1_0 where h1_0.name=?` (**같은 SQL 재실행**) |
| …592.366 | result-set | **`jdbc.row-count=1`** (이번엔 **찾음** — 방금 만든 174) |
| …592.370 | query | `insert into tb_feed_hashtags (...)` → **`error: Duplicate entry '151-174' for key 'tb_feed_hashtags.uk_feed_hashtag'`**, `status.code=STATUS_CODE_ERROR` |
| …592.383 | connection event | **`rollback`** |

즉 **같은 (feed 151, hashtag 174) 쌍을 한 트랜잭션 안에서 두 번 insert**했다. 첫 번째 이름 조회는 miss(0건) → 해시태그 생성, 두 번째 조회는 hit(1건) → 같은 id 174 반환 → 동일 연결 재삽입. 요청 페이로드의 해시태그 목록을 (정규화 후) distinct 처리하지 않는 것이 직접 원인이다. *(원문 데이터에는 파라미터 값이 없으므로, "같은 문자열 2회" 인지 "정규화 후 같은 이름으로 수렴한 서로 다른 2개(예: 대소문자·공백·이모지 차이)" 인지는 구분 불가.)*

로그 원문:
- `2026-07-29 09:56:32.376 [http-nio-8082-exec-4] WARN [traceId=6a694fc02926ea82d0bdbb4434237626,...,userId=1] o.h.e.jdbc.spi.SqlExceptionHelper - SQL Error: 1062, SQLState: 23000`
- `... ERROR ... o.h.e.jdbc.spi.SqlExceptionHelper - Duplicate entry '151-174' for key 'tb_feed_hashtags.uk_feed_hashtag'`
- `org.springframework.dao.DataIntegrityViolationException: could not execute statement [Duplicate entry '151-174' ...] [insert into tb_feed_hashtags (created_at,feed_id,hashtag_id,updated_at) values (?,?,?,?)]; constraint [tb_feed_hashtags.uk_feed_hashtag]`
- `Caused by: java.sql.SQLIntegrityConstraintViolationException: Duplicate entry '151-174' for key 'tb_feed_hashtags.uk_feed_hashtag'`
- 최종 응답: `c.e.t.a.c.f.RequestLoggingFilter - [HTTP] POST /api/feeds 500 - 73ms`

**확신도: 높음**

**반증 데이터**: 없음. 인프라 원인을 지지하는 관측값도 없다 — `hikaricp_connections_active`(content-service qnxk6)는 창 전체 최대 **1**, `hikaricp_connections_pending`은 **전 구간 0**, `up`은 모든 대상 전 구간 1(mysql exporter는 수집 항목에 없음), `rate(jvm_gc_pause_seconds_sum[5m])`는 content-service 최대 **3.75e-5** 수준, kafka lag 전 구간 0, `mongodb_up=1`. 요청 총 소요도 74ms로 지연 요소가 없다.

> 참고(오독 주의): 스택트레이스상 예외가 터진 지점은 `ExpGrantService.isDuplicate(ExpGrantService.java:225)` ← `grantWithCap:159` ← `grant:151` ← `grantFeedCreate:43` ← `FeedService.createFeed(FeedService.java:223)` 이지만, `Caused by` 체인이 `DefaultAutoFlushEventListener.onAutoPreFlush` → `Cascade.cascadeCollectionElements` → `EntityIdentityInsertAction` 을 지나므로 **경험치 부여 로직이 원인이 아니라, 그 안의 exists 쿼리가 유발한 auto-flush 시점에 밀려 있던 `tb_feed_hashtags` insert가 터진 것**이다. 실패 원인은 해시태그 연결이다.

### 후보 2 — 부분 실패의 전체 확대 (트랜잭션 경계·예외 매핑)

**근거**
- `connection` span(`vz4jgP0pks4=`, 592.327~592.393, 66.3ms)의 events가 `acquired` → **`rollback`** 이다. 이미 `row-affected=1`로 성공했던 `insert into tb_feed`(feed 151), `insert into tb_feed_attachment_file` 2건, `insert into tb_hashtags`(174), 정상 연결 1건이 **모두 되돌려졌다**. 사용자 입장에서는 "피드 작성 실패"로 관측된다.
- 클라이언트 입력에 기인한 제약 위반이 4xx가 아니라 500으로 나갔다: `GlobalExceptionHandler - handleAllException` (WARN) 직후 `POST /api/feeds 500`. 서버 span 속성도 `outcome=SERVER_ERROR`, `status=500`인데 `exception=none`이라 메트릭/알림 상관관계 추적도 어렵다.
- 실패가 **경험치 부여 단계의 auto-flush**까지 지연되어 드러났다(위 스택 체인). 즉 해시태그 저장 직후 검증·flush했다면 더 이르고 명확하게 실패했을 지점이 뒤로 밀렸다.

**확신도: 중간** (rollback과 500 매핑은 관측값으로 확정. 다만 `@Transactional` 전파 설정, ExpGrant의 트랜잭션 경계 의도가 데이터에 없어 "설계 결함"인지 "의도된 원자성"인지는 단정 불가)

**반증 데이터**: 트랜잭션 원자성 자체는 정상 동작일 수 있다 — 해시태그 연결 실패 시 피드만 남기는 것이 오히려 부정합일 수 있으므로, 이 항목은 후보 1의 *증폭 요인*이지 독립 원인이 아니다.

### 후보 3 — 영향 범위: 데이터 부족

**근거**
- 조회 창이 `00:50:00Z ~ 01:03:43Z`, 즉 **13분 43초**뿐이다. 제보의 "최근 1시간"을 커버하지 못한다.
- 수집된 10건의 트레이스 중 `POST /feeds`는 **1건**이며, 그 1건이 실패(500)다. 나머지는 auth-service의 `/login`·`/files/upload`(둘 다 200), chat/content의 필터체인·커넥션 span으로, **성공한 피드 작성 트레이스가 한 건도 없다.** 성공/실패 비율을 산출할 표본이 없다.
- 메트릭 `sum(rate(http_server_requests_seconds_count{application="content-service", status="401"}[1m]))`는 **no series로 수집 실패**했고, 5xx 카운터(`status="500"`)는 애초에 수집 목록에 없다. 따라서 실패율 시계열이 없다.
- 관측된 실패 사용자는 로그의 `userId=1` 하나뿐이다.
- content-service 파드는 2개(`...-qnxk6` 10.42.1.35, `...-scw7k` 10.42.3.41)인데, **scw7k는 창 전체에서 hikari active 0**이고 트레이스도 없다. 파드별 편차 유무를 판단할 수 없다.

**확신도: 낮음** (판단 불가 자체가 결론)

**반증 데이터**: 없음.

---

## 3. 권장 다음 조치

**즉시 확인 (코드)**
1. `FeedService.createFeed` (`FeedService.java:223` 부근)의 해시태그 처리 경로에서 요청 해시태그 목록에 **정규화(trim/소문자화/유니코드 정규화) 후 distinct**가 적용되는지 확인. 미적용이면 이것이 수정 지점이다.
2. 해시태그 연결 저장 시 **(feed_id, hashtag_id) 존재 여부 확인 또는 멱등 처리**(`INSERT ... ON DUPLICATE KEY UPDATE` / `insertIgnore` / Set 컬렉션) 도입 검토.
3. `tb_hashtags` get-or-create 경로: 같은 트랜잭션에서 이름 조회가 2회 실행됨(592.346 → 0건, 592.364 → 1건). **요청 스코프 캐시/맵으로 이름→엔티티 해석을 1회로** 줄이면 중복 자체가 구조적으로 제거된다.

**재현 검증**
4. 스테이징에서 `POST /api/feeds`에 **동일 해시태그를 2회 포함**한 페이로드로 요청 → 500과 `Duplicate entry` 재현 여부 확인. 재현되면 후보 1 확정.
5. 재현되지 않으면 대소문자/공백/이모지가 다른 두 해시태그(정규화 후 동일)로 재시도.

**데이터 확인**
6. MySQL: `SELECT * FROM tb_feed WHERE feed_id=151;`, `SELECT * FROM tb_hashtags WHERE id=174;`, `SELECT * FROM tb_feed_hashtags WHERE feed_id=151;` — rollback이 정상 수행됐다면 셋 다 없어야 한다. 잔존 행이 있으면 트랜잭션 경계 문제(후보 2)가 별도 이슈로 승격된다.
7. `SHOW CREATE TABLE tb_feed_hashtags;` 로 `uk_feed_hashtag` 컬럼 구성 확인.

**관측 보강 (현재 공백 메우기)**
8. 조회 창을 실제 **최근 1시간 전체**로 재조회 — 현재 13분 43초만 봤다.
9. content-service의 `http_server_requests_seconds_count{uri="/feeds", status="500"}` / 전체 대비 실패율 수집. 401 메트릭은 시리즈가 없으므로 라벨명·수집 설정부터 점검.
10. Loki에서 `Duplicate entry`·`uk_feed_hashtag`·`handleAllException` 키워드로 1시간(가능하면 24시간) 카운트 → 단발인지 상시 재발인지 판별. 동시에 `POST /api/feeds 500` 라인 수 대비 `200` 라인 수로 실제 실패율 산출.
11. 파드 `content-service-6c5fff897-scw7k`(10.42.3.41)에 트래픽이 도달하는지 확인 — 창 전체에서 활동이 0이라 LB/서비스 엔드포인트 상태를 별건으로 점검할 가치가 있다.

**응답 품질 개선 (원인 수정과 별개)**
12. `DataIntegrityViolationException` / `ConstraintViolationException`을 `GlobalExceptionHandler`에서 **400/409로 매핑**하고, 서버 span의 `exception=none` 대신 실제 예외 타입이 기록되도록 보정. 현재는 클라이언트 입력 오류가 500으로 집계되어 알림·SLO를 오염시킨다.

---

## 관측 증거 (Evidence)

> 이 조사가 실제로 본 값이다. 원본 응답 전체는 `reports/raw/scan-1785286200-*.json`에 있다.

### 호출 그래프 (트레이스에서 추출)

```
chat-service --db--> redis  1회  최대 0.4ms  [INFO]
chat-service --jdbc--> mysql/content (HikariPool-1)  2회  최대 1.5ms
    events: acquired
content-service --jdbc--> mysql/content (HikariPool-1)  21회  최대 66.3ms
    error: Duplicate entry '151-174' for key 'tb_feed_hashtags.uk_feed_hashtag'
    events: acquired, rollback
```

### span (duration 상위 15 / 전체 45)

| ms | service | span | 시작 |
|---:|---|---|---|
| 318.97 | auth-service | `http post /files/upload` | 2026-07-29T00:57:32.730258Z |
| 299.99 | auth-service | `secured request` | 2026-07-29T00:57:32.748751Z |
| 156.22 | auth-service | `http post /login` | 2026-07-29T00:57:32.524155Z |
| 155.15 | auth-service | `secured request` | 2026-07-29T00:57:32.524697Z |
| 74.31 | content-service | `http post /feeds` | 2026-07-29T00:56:32.321490Z |
| 71.93 | content-service | `secured request` | 2026-07-29T00:56:32.321929Z |
| 66.29 | content-service | `connection` | 2026-07-29T00:56:32.327455Z |
| 18.07 | auth-service | `security filterchain before` | 2026-07-29T00:57:32.730617Z |
| 7.30 | chat-service | `secured request` | 2026-07-29T00:57:32.241273Z |
| 6.95 | content-service | `secured request` | 2026-07-29T00:57:32.240330Z |
| 6.21 | content-service | `query` | 2026-07-29T00:56:32.370415Z |
| 2.55 | content-service | `query` | 2026-07-29T00:56:32.361400Z |
| 1.85 | content-service | `query` | 2026-07-29T00:56:32.336249Z |
| 1.81 | content-service | `connection` | 2026-07-29T00:57:32.241777Z |
| 1.77 | content-service | `query` | 2026-07-29T00:56:32.364451Z |

### 로그 원문 (60 / 전체 251줄)

전체가 상한을 넘어 ERROR/WARN 줄을 우선 발췌했다. 나머지는 원본 파일에 있다.

```
2026-07-29T00:56:32.377738423Z  [content-service]  2026-07-29 09:56:32.376 [http-nio-8082-exec-4]  WARN [traceId=6a694fc02926ea82d0bdbb4434237626,spanId=bf3e2380fd2992ce,userId=1] o.h.e.jdbc.spi.SqlExceptionHelper - SQL Error: 1062, SQLState: 23000
2026-07-29T00:56:32.377738423Z  [content-service]  2026-07-29 09:56:32.376 [http-nio-8082-exec-4]  WARN [traceId=6a694fc02926ea82d0bdbb4434237626,spanId=bf3e2380fd2992ce,userId=1] o.h.e.jdbc.spi.SqlExceptionHelper - SQL Error: 1062, SQLState: 23000
2026-07-29T00:56:32.377847074Z  [content-service]  2026-07-29 09:56:32.376 [http-nio-8082-exec-4] ERROR [traceId=6a694fc02926ea82d0bdbb4434237626,spanId=bf3e2380fd2992ce,userId=1] o.h.e.jdbc.spi.SqlExceptionHelper - Duplicate entry '151-174' for key 'tb_feed_hashtags.uk_feed_hashtag'
2026-07-29T00:56:32.377847074Z  [content-service]  2026-07-29 09:56:32.376 [http-nio-8082-exec-4] ERROR [traceId=6a694fc02926ea82d0bdbb4434237626,spanId=bf3e2380fd2992ce,userId=1] o.h.e.jdbc.spi.SqlExceptionHelper - Duplicate entry '151-174' for key 'tb_feed_hashtags.uk_feed_hashtag'
2026-07-29T00:56:32.392461078Z  [content-service]  2026-07-29 09:56:32.385 [http-nio-8082-exec-4]  WARN [traceId=6a694fc02926ea82d0bdbb4434237626,spanId=bf3e2380fd2992ce,userId=1] c.e.t.a.c.e.GlobalExceptionHandler - handleAllException
2026-07-29T00:56:32.392461078Z  [content-service]  2026-07-29 09:56:32.385 [http-nio-8082-exec-4]  WARN [traceId=6a694fc02926ea82d0bdbb4434237626,spanId=bf3e2380fd2992ce,userId=1] c.e.t.a.c.e.GlobalExceptionHandler - handleAllException
2026-07-29T00:56:32.392489104Z  [content-service]  org.springframework.dao.DataIntegrityViolationException: could not execute statement [Duplicate entry '151-174' for key 'tb_feed_hashtags.uk_feed_hashtag'] [insert into tb_feed_hashtags (created_at,feed_id,hashtag_id,updated_at) values (?,?,?,?)]; SQL [insert into tb_feed_hashtags (created_at,feed_id,hashtag_id,updated_at) values (?,?,?,?)]; constraint [tb_feed_hashtags.uk_feed_hashtag]
2026-07-29T00:56:32.392507947Z  [content-service]  at org.springframework.orm.jpa.vendor.HibernateJpaDialect.convertHibernateAccessException(HibernateJpaDialect.java:290)
2026-07-29T00:56:32.392511369Z  [content-service]  at org.springframework.orm.jpa.vendor.HibernateJpaDialect.translateExceptionIfPossible(HibernateJpaDialect.java:241)
2026-07-29T00:56:32.392514586Z  [content-service]  at org.springframework.orm.jpa.AbstractEntityManagerFactoryBean.translateExceptionIfPossible(AbstractEntityManagerFactoryBean.java:560)
2026-07-29T00:56:32.392518052Z  [content-service]  at org.springframework.dao.support.ChainedPersistenceExceptionTranslator.translateExceptionIfPossible(ChainedPersistenceExceptionTranslator.java:61)
2026-07-29T00:56:32.392521367Z  [content-service]  at org.springframework.dao.support.DataAccessUtils.translateIfNecessary(DataAccessUtils.java:343)
2026-07-29T00:56:32.392524340Z  [content-service]  at org.springframework.dao.support.PersistenceExceptionTranslationInterceptor.invoke(PersistenceExceptionTranslationInterceptor.java:160)
2026-07-29T00:56:32.392530724Z  [content-service]  at org.springframework.aop.framework.ReflectiveMethodInvocation.proceed(ReflectiveMethodInvocation.java:184)
2026-07-29T00:56:32.392533826Z  [content-service]  at org.springframework.data.jpa.repository.support.CrudMethodMetadataPostProcessor$CrudMethodMetadataPopulatingMethodInterceptor.invoke(CrudMethodMetadataPostProcessor.java:136)
2026-07-29T00:56:32.392536588Z  [content-service]  at org.springframework.aop.framework.ReflectiveMethodInvocation.proceed(ReflectiveMethodInvocation.java:184)
2026-07-29T00:56:32.392539871Z  [content-service]  at org.springframework.aop.framework.JdkDynamicAopProxy.invoke(JdkDynamicAopProxy.java:223)
2026-07-29T00:56:32.392545348Z  [content-service]  at com.example.toycontent.app.reward.exp.service.ExpGrantService.isDuplicate(ExpGrantService.java:225)
2026-07-29T00:56:32.392547837Z  [content-service]  at com.example.toycontent.app.reward.exp.service.ExpGrantService.grantWithCap(ExpGrantService.java:159)
2026-07-29T00:56:32.392551220Z  [content-service]  at com.example.toycontent.app.reward.exp.service.ExpGrantService.grant(ExpGrantService.java:151)
2026-07-29T00:56:32.392553901Z  [content-service]  at com.example.toycontent.app.reward.exp.service.ExpGrantService.grantFeedCreate(ExpGrantService.java:43)
2026-07-29T00:56:32.392570795Z  [content-service]  at org.springframework.aop.support.AopUtils.invokeJoinpointUsingReflection(AopUtils.java:359)
2026-07-29T00:56:32.392573797Z  [content-service]  at org.springframework.aop.framework.ReflectiveMethodInvocation.invokeJoinpoint(ReflectiveMethodInvocation.java:196)
2026-07-29T00:56:32.392576764Z  [content-service]  at org.springframework.aop.framework.ReflectiveMethodInvocation.proceed(ReflectiveMethodInvocation.java:163)
2026-07-29T00:56:32.392579636Z  [content-service]  at org.springframework.transaction.interceptor.TransactionAspectSupport.invokeWithinTransaction(TransactionAspectSupport.java:380)
2026-07-29T00:56:32.392582474Z  [content-service]  at org.springframework.transaction.interceptor.TransactionInterceptor.invoke(TransactionInterceptor.java:119)
2026-07-29T00:56:32.392585131Z  [content-service]  at org.springframework.aop.framework.ReflectiveMethodInvocation.proceed(ReflectiveMethodInvocation.java:184)
2026-07-29T00:56:32.392587854Z  [content-service]  at org.springframework.aop.framework.CglibAopProxy$DynamicAdvisedInterceptor.intercept(CglibAopProxy.java:727)
2026-07-29T00:56:32.392593098Z  [content-service]  at com.example.toycontent.app.feed.service.FeedService.createFeed(FeedService.java:223)
2026-07-29T00:56:32.392612332Z  [content-service]  at org.springframework.aop.support.AopUtils.invokeJoinpointUsingReflection(AopUtils.java:359)
2026-07-29T00:56:32.392619999Z  [content-service]  at org.springframework.aop.framework.ReflectiveMethodInvocation.invokeJoinpoint(ReflectiveMethodInvocation.java:196)
2026-07-29T00:56:32.392622804Z  [content-service]  at org.springframework.aop.framework.ReflectiveMethodInvocation.proceed(ReflectiveMethodInvocation.java:163)
2026-07-29T00:56:32.392625418Z  [content-service]  at org.springframework.transaction.interceptor.TransactionAspectSupport.invokeWithinTransaction(TransactionAspectSupport.java:380)
2026-07-29T00:56:32.392700550Z  [content-service]  at org.springframework.transaction.interceptor.TransactionInterceptor.invoke(TransactionInterceptor.java:119)
2026-07-29T00:56:32.392704263Z  [content-service]  at org.springframework.aop.framework.ReflectiveMethodInvocation.proceed(ReflectiveMethodInvocation.java:184)
2026-07-29T00:56:32.392706695Z  [content-service]  at org.springframework.aop.framework.CglibAopProxy$DynamicAdvisedInterceptor.intercept(CglibAopProxy.java:727)
2026-07-29T00:56:32.392712128Z  [content-service]  at com.example.toycontent.app.feed.controller.FeedController.createFeed(FeedController.java:114)
2026-07-29T00:56:32.392725848Z  [content-service]  at org.springframework.web.method.support.InvocableHandlerMethod.doInvoke(InvocableHandlerMethod.java:257)
2026-07-29T00:56:32.392728657Z  [content-service]  at org.springframework.web.method.support.InvocableHandlerMethod.invokeForRequest(InvocableHandlerMethod.java:190)
2026-07-29T00:56:32.392731807Z  [content-service]  at org.springframework.web.servlet.mvc.method.annotation.ServletInvocableHandlerMethod.invokeAndHandle(ServletInvocableHandlerMethod.java:118)
2026-07-29T00:56:32.392734384Z  [content-service]  at org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter.invokeHandlerMethod(RequestMappingHandlerAdapter.java:986)
2026-07-29T00:56:32.392737080Z  [content-service]  at org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter.handleInternal(RequestMappingHandlerAdapter.java:891)
2026-07-29T00:56:32.392739560Z  [content-service]  at org.springframework.web.servlet.mvc.method.AbstractHandlerMethodAdapter.handle(AbstractHandlerMethodAdapter.java:87)
2026-07-29T00:56:32.392741916Z  [content-service]  at org.springframework.web.servlet.DispatcherServlet.doDispatch(DispatcherServlet.java:1088)
2026-07-29T00:56:32.392744404Z  [content-service]  at org.springframework.web.servlet.DispatcherServlet.doService(DispatcherServlet.java:978)
2026-07-29T00:56:32.392747043Z  [content-service]  at org.springframework.web.servlet.FrameworkServlet.processRequest(FrameworkServlet.java:1014)
2026-07-29T00:56:32.392749573Z  [content-service]  at org.springframework.web.servlet.FrameworkServlet.doPost(FrameworkServlet.java:914)
2026-07-29T00:56:32.392771425Z  [content-service]  at jakarta.servlet.http.HttpServlet.service(HttpServlet.java:590)
2026-07-29T00:56:32.392857137Z  [content-service]  at org.springframework.security.web.access.ExceptionTranslationFilter.doFilter(ExceptionTranslationFilter.java:126)
2026-07-29T00:56:32.392859669Z  [content-service]  at org.springframework.security.web.access.ExceptionTranslationFilter.doFilter(ExceptionTranslationFilter.java:120)
2026-07-29T00:56:32.393273834Z  [content-service]  Caused by: org.hibernate.exception.ConstraintViolationException: could not execute statement [Duplicate entry '151-174' for key 'tb_feed_hashtags.uk_feed_hashtag'] [insert into tb_feed_hashtags (created_at,feed_id,hashtag_id,updated_at) values (?,?,?,?)]
2026-07-29T00:56:32.393276194Z  [content-service]  at org.hibernate.dialect.MySQLDialect.lambda$buildSQLExceptionConversionDelegate$3(MySQLDialect.java:1245)
2026-07-29T00:56:32.393278670Z  [content-service]  at org.hibernate.exception.internal.StandardSQLExceptionConverter.convert(StandardSQLExceptionConverter.java:58)
2026-07-29T00:56:32.393280958Z  [content-service]  at org.hibernate.engine.jdbc.spi.SqlExceptionHelper.convert(SqlExceptionHelper.java:108)
2026-07-29T00:56:32.393501955Z  [content-service]  at org.springframework.dao.support.PersistenceExceptionTranslationInterceptor.invoke(PersistenceExceptionTranslationInterceptor.java:138)
2026-07-29T00:56:32.393507462Z  [content-service]  Caused by: java.sql.SQLIntegrityConstraintViolationException: Duplicate entry '151-174' for key 'tb_feed_hashtags.uk_feed_hashtag'
2026-07-29T00:56:32.393510180Z  [content-service]  at com.mysql.cj.jdbc.exceptions.SQLError.createSQLException(SQLError.java:109)
2026-07-29T00:56:32.393517430Z  [content-service]  at com.mysql.cj.jdbc.exceptions.SQLExceptionsMapping.translateException(SQLExceptionsMapping.java:114)
2026-07-29T00:56:32.394234362Z  [content-service]  2026-07-29 09:56:32.394 [http-nio-8082-exec-4] ERROR [traceId=6a694fc02926ea82d0bdbb4434237626,spanId=d0bdbb4434237626,userId=NONE] c.e.t.a.c.f.RequestLoggingFilter - [HTTP] POST /api/feeds 500 - 73ms
2026-07-29T00:56:32.394234362Z  [content-service]  2026-07-29 09:56:32.394 [http-nio-8082-exec-4] ERROR [traceId=6a694fc02926ea82d0bdbb4434237626,spanId=d0bdbb4434237626,userId=NONE] c.e.t.a.c.f.RequestLoggingFilter - [HTTP] POST /api/feeds 500 - 73ms
```

### 메트릭 시계열

| 쿼리 | series | 점 | min | max | last | 값이 0이던 구간 |
|---|---|---:|---:|---:|---:|---|
| `hikaricp_connections_active` | `{__name__=hikaricp_connections_active, application=auth-service, cluster=yogurtte-k3s-prod, container=auth-service, instance=10.42.1.34:8090, job=auth-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=auth-service-855c75679d-45fxb, pool=HikariPool-1, service=auth-service}` | 55 | 0 | 0 | 0 | **2026-07-29T00:50:00Z ~ 2026-07-29T01:03:30Z** |
| `hikaricp_connections_active` | `{__name__=hikaricp_connections_active, application=chat-service, cluster=yogurtte-k3s-prod, container=chat-service, instance=10.42.1.31:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-857c54dd97-s5fbl, pool=HikariPool-1}` | 55 | 0 | 0 | 0 | **2026-07-29T00:50:00Z ~ 2026-07-29T01:03:30Z** |
| `hikaricp_connections_active` | `{__name__=hikaricp_connections_active, application=content-service, cluster=yogurtte-k3s-prod, container=content-service, instance=10.42.1.35:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-6c5fff897-qnxk6, pool=HikariPool-1}` | 55 | 0 | 1 | 0 | **2026-07-29T00:50:00Z ~ 2026-07-29T00:56:00Z, 2026-07-29T00:57:15Z ~ 2026-07-29T01:00:00Z, 2026-07-29T01:01:15Z ~ 2026-07-29T01:02:00Z, 2026-07-29T01:03:15Z ~ 2026-07-29T01:03:30Z** |
| `hikaricp_connections_active` | `{__name__=hikaricp_connections_active, application=content-service, cluster=yogurtte-k3s-prod, container=content-service, instance=10.42.3.41:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-6c5fff897-scw7k, pool=HikariPool-1}` | 55 | 0 | 0 | 0 | **2026-07-29T00:50:00Z ~ 2026-07-29T01:03:30Z** |
| `hikaricp_connections_pending` | `{__name__=hikaricp_connections_pending, application=auth-service, cluster=yogurtte-k3s-prod, container=auth-service, instance=10.42.1.34:8090, job=auth-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=auth-service-855c75679d-45fxb, pool=HikariPool-1, service=auth-service}` | 55 | 0 | 0 | 0 | **2026-07-29T00:50:00Z ~ 2026-07-29T01:03:30Z** |
| `hikaricp_connections_pending` | `{__name__=hikaricp_connections_pending, application=chat-service, cluster=yogurtte-k3s-prod, container=chat-service, instance=10.42.1.31:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-857c54dd97-s5fbl, pool=HikariPool-1}` | 55 | 0 | 0 | 0 | **2026-07-29T00:50:00Z ~ 2026-07-29T01:03:30Z** |
| `hikaricp_connections_pending` | `{__name__=hikaricp_connections_pending, application=content-service, cluster=yogurtte-k3s-prod, container=content-service, instance=10.42.1.35:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-6c5fff897-qnxk6, pool=HikariPool-1}` | 55 | 0 | 0 | 0 | **2026-07-29T00:50:00Z ~ 2026-07-29T01:03:30Z** |
| `hikaricp_connections_pending` | `{__name__=hikaricp_connections_pending, application=content-service, cluster=yogurtte-k3s-prod, container=content-service, instance=10.42.3.41:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-6c5fff897-scw7k, pool=HikariPool-1}` | 55 | 0 | 0 | 0 | **2026-07-29T00:50:00Z ~ 2026-07-29T01:03:30Z** |
| `rate(jvm_gc_pause_seconds_sum[5m])` | `{action=end of major GC, application=chat-service, cause=Allocation Failure, cluster=yogurtte-k3s-prod, container=chat-service, gc=MarkSweepCompact, instance=10.42.1.31:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-857c54dd97-s5fbl}` | 55 | 0 | 0 | 0 | **2026-07-29T00:50:00Z ~ 2026-07-29T01:03:30Z** |
| `rate(jvm_gc_pause_seconds_sum[5m])` | `{action=end of minor GC, application=auth-service, cause=G1 Evacuation Pause, cluster=yogurtte-k3s-prod, container=auth-service, gc=G1 Young Generation, instance=10.42.1.34:8090, job=auth-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=auth-service-855c75679d-45fxb, service=auth-service}` | 55 | 0 | 0 | 0 | **2026-07-29T00:50:00Z ~ 2026-07-29T01:03:30Z** |
| `rate(jvm_gc_pause_seconds_sum[5m])` | `{action=end of minor GC, application=chat-service, cause=Allocation Failure, cluster=yogurtte-k3s-prod, container=chat-service, gc=Copy, instance=10.42.1.31:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-857c54dd97-s5fbl}` | 55 | 0.000 | 0.000 | 0.000 | — |
| `rate(jvm_gc_pause_seconds_sum[5m])` | `{action=end of minor GC, application=content-service, cause=Allocation Failure, cluster=yogurtte-k3s-prod, container=content-service, gc=Copy, instance=10.42.1.35:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-6c5fff897-qnxk6}` | 55 | 0 | 0.000 | 0.000 | **2026-07-29T00:50:00Z ~ 2026-07-29T00:51:00Z, 2026-07-29T00:55:15Z ~ 2026-07-29T01:01:00Z** |
| `rate(jvm_gc_pause_seconds_sum[5m])` | `{action=end of minor GC, application=content-service, cause=Allocation Failure, cluster=yogurtte-k3s-prod, container=content-service, gc=Copy, instance=10.42.3.41:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-6c5fff897-scw7k}` | 55 | 0 | 0.000 | 0.000 | **2026-07-29T00:50:00Z ~ 2026-07-29T00:50:45Z, 2026-07-29T00:55:00Z ~ 2026-07-29T01:02:45Z** |
| `up` | `{__name__=up, app=node-exporter, cluster=yogurtte-k3s-prod, component=metrics, container=node-exporter, instance=ip-172-31-40-241, job=integrations/node_exporter, k8s_cluster_name=yogurtte-k3s-prod, namespace=monitoring, pod=grafana-k8s-monitoring-node-exporter-cph5l, source=kubernetes, workload=DaemonSet/grafana-k8s-monitoring-node-exporter}` | 55 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, app=node-exporter, cluster=yogurtte-k3s-prod, component=metrics, container=node-exporter, instance=ip-172-31-45-39, job=integrations/node_exporter, k8s_cluster_name=yogurtte-k3s-prod, namespace=monitoring, pod=grafana-k8s-monitoring-node-exporter-9kvqt, source=kubernetes, workload=DaemonSet/grafana-k8s-monitoring-node-exporter}` | 55 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, cluster=yogurtte-k3s-prod, container=auth-service, instance=10.42.1.34:8090, job=auth-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=auth-service-855c75679d-45fxb}` | 55 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, cluster=yogurtte-k3s-prod, container=chat-service, instance=10.42.1.31:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-857c54dd97-s5fbl}` | 55 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, cluster=yogurtte-k3s-prod, container=content-service, instance=10.42.1.35:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-6c5fff897-qnxk6}` | 55 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, cluster=yogurtte-k3s-prod, container=content-service, instance=10.42.3.41:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-6c5fff897-scw7k}` | 55 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, cluster=yogurtte-k3s-prod, instance=10.42.1.248:8080, job=integrations/kubernetes/kube-state-metrics, k8s_cluster_name=yogurtte-k3s-prod, source=kubernetes}` | 55 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, cluster=yogurtte-k3s-prod, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod}` | 55 | 1 | 1 | 1 | — |
| `mongodb_up` | `{__name__=mongodb_up, cluster=yogurtte-k3s-prod, instance=infra-server, job=mongodb, k8s_cluster_name=yogurtte-k3s-prod}` | 55 | 1 | 1 | 1 | — |
| `kafka_brokers` | `{__name__=kafka_brokers, cluster=yogurtte-k3s-prod, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod}` | 55 | 1 | 1 | 1 | — |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=chat-service-fcm-tokens, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=0, topic=user.fcm-tokens}` | 55 | 0 | 0 | 0 | **2026-07-29T00:50:00Z ~ 2026-07-29T01:03:30Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=chat-service-fcm-tokens, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=1, topic=user.fcm-tokens}` | 55 | 0 | 0 | 0 | **2026-07-29T00:50:00Z ~ 2026-07-29T01:03:30Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=chat-service-fcm-tokens, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=2, topic=user.fcm-tokens}` | 55 | 0 | 0 | 0 | **2026-07-29T00:50:00Z ~ 2026-07-29T01:03:30Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=chat-service-notification-settings, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=0, topic=user.notification-settings}` | 55 | 0 | 0 | 0 | **2026-07-29T00:50:00Z ~ 2026-07-29T01:03:30Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=chat-service-notification-settings, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=1, topic=user.notification-settings}` | 55 | 0 | 0 | 0 | **2026-07-29T00:50:00Z ~ 2026-07-29T01:03:30Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=chat-service-notification-settings, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=2, topic=user.notification-settings}` | 55 | 0 | 0 | 0 | **2026-07-29T00:50:00Z ~ 2026-07-29T01:03:30Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=db-writer, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=0, topic=chat.messages}` | 55 | 0 | 0 | 0 | **2026-07-29T00:50:00Z ~ 2026-07-29T01:03:30Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=db-writer, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=1, topic=chat.messages}` | 55 | 0 | 0 | 0 | **2026-07-29T00:50:00Z ~ 2026-07-29T01:03:30Z** |
| `websocket_active_users` | `{__name__=websocket_active_users, application=chat-service, cluster=yogurtte-k3s-prod, container=chat-service, instance=10.42.1.31:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-857c54dd97-s5fbl}` | 55 | 0 | 0 | 0 | **2026-07-29T00:50:00Z ~ 2026-07-29T01:03:30Z** |

값이 0이던 구간은 굵게 표시했다 — 프로세스가 사라져 시계열이 꺾인 것이 유일한 신호인 장애가 있다.

