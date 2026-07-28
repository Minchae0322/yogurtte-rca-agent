# RCA Report — `6a68c522cb16f0a29c2c4bd0a86df613`

| 항목 | 값 |
|---|---|
| 모드 | rca |
| 질문 | 최근 1시간 안에 댓글 작성이 실패했다는 제보가 있다. 원인을 조사해줘 |
| 시각 | 2026-07-28T15:11:28.260153Z |
| provider | claude-cli |
| model | `claude-haiku-4-5-20251001` · turns 1 |
| prompt | `./prompts/system-prompt.md` |
| tokens | in 71071 (cacheRead 18,133 · cacheCreate 52,936) / out 7159 · cost $0.7561 |
| elapsed | total 111092ms (tempo 479 · loki 190 · mimir 613 · assemble 2 · llm 109798) |

## 탐색 (Triage)

| 항목 | 값 |
|---|---|
| 시간창 해석 | 상대 표현 '최근 1시간' |
| 스윕 창 | 2026-07-28T14:10:53.005649Z ~ 2026-07-28T15:10:53.005649Z |
| 좁힌 창 | 2026-07-28T15:03:00Z ~ 2026-07-28T15:10:53Z |
| 대상 | content-service |
| traceId | 6a68c522cb16f0a29c2c4bd0a86df613 |
| 트레이스 후보 | 1건 |
| 계획 파싱 | 성공 |
| prompt | `./prompts/triage-prompt.md` |
| tokens | in 43025 / out 2264 · cost $0.3585 |
| elapsed | survey 1057ms · llm 34187ms |

**선정 이유**: 에러 트레이스 시각(15:05:06Z)과 유일한 ERROR 로그 버킷(15:05–15:10Z)이 겹치고 둘 다 content-service의 댓글 작성 엔드포인트를 가리키므로, 앞뒤 여유를 둔 15:03~조회 종료 구간의 content-service 로그·스팬을 깊게 봐야 한다.

**근거**

- Tempo 에러 트레이스 1건: content-service / 'http post /feeds/{feedId}/comments', 시작 2026-07-28T15:05:06.360Z, durationMs=82, serviceStats.content-service = {spanCount:9, errorCount:1} — 제보된 '댓글 작성 실패'와 엔드포인트·증상이 정확히 일치
- 에러 스팬 0a104241c83458c0가 15:05:06.405Z에 시작해 10.16ms 만에 실패 — 루트 시작 45ms 후 발생한 내부/하위 호출 실패로 보임
- Loki ERROR/WARN 발생률: 1시간 중 유일한 데이터 포인트가 timestamp 1785251400(=15:10:00Z) 버킷의 content-service 4건. 14:10~15:05 구간은 전 서비스 0건이라 에러 로그가 이 구간에만 집중됨
- 트레이스에 auth-service·chat-service 스팬이 전혀 없음(9개 스팬 모두 content-service) → 인증 실패나 Kafka 소비 지연이 아니라 content-service 내부 경로 문제 가능성
- 인프라 신호 전 구간 정상·끊김 없음: up=1(전 타깃 13개 포인트 모두), mongodb_up=1, kafka_brokers=1, 모든 consumergroup lag 0(-1은 미할당 파티션으로 전 구간 상수) → 인프라 레벨 원인 배제
- websocket_active_users=0 전 구간 상수 → chat 알림 경로에는 변화 신호 없음

**스윕이 찾은 트레이스** (고른 것은 6a68c522cb16f0a29c2c4bd0a86df613)

| traceId | root service | root span | ms |
|---|---|---|---:|
| `6a68c522cb16f0a29c2c4bd0a86df613` ←선택 | content-service | http post /feeds/{feedId}/comments | 82 |

## 수집 범위 (Coverage)

- **window**: 2026-07-28T15:03:00Z ~ 2026-07-28T15:10:53Z (473s)
- **trace**: 8,760B / 9 spans
- **logs**: errwarn=5,683B · traceId=6,321B
- **metrics**: 8 수집 / 68,809B, 누락 [sum(rate(http_server_requests_seconds_count{application="content-service", status="401"}[1m]))]
- **context**: 90,200 chars (+ 시스템 프롬프트 575 chars)
- **contextTokens**: 측정 안 됨  ← 개선 지표 (count_tokens 실측, CLI 오버헤드 제외)

## 수집 실패/누락

- Metric 'sum(rate(http_server_requests_seconds_count{application="content-service", status="401"}[1m]))' returned no series in this window; skipped.

---

## 1. 원인 후보 랭킹 (최대 3개)

1. **애플리케이션 입력 검증 부재 — 댓글 본문 길이가 `tb_feed_comment.content` 컬럼 정의를 초과하여 MySQL이 INSERT를 거부** (직접 원인)
2. **예외 매핑 결함 — 데이터 무결성 위반(1406/22001)이 4xx가 아닌 `handleAllException` 경로로 떨어져 500으로 반환** (장애 증상 악화 / 오분류 원인)
3. **`content` 컬럼 스키마 정의 자체가 서비스 요구사항보다 작음(스키마·charset 불일치)** — 데이터 부족

---

## 2. 후보별 근거

### 후보 1: 댓글 본문 길이 초과 (입력 검증 부재)

**근거**
- 트레이스 INSERT span (`name: query`, kind CLIENT, spanId `0a104241c83458c0`)에 `status.code = STATUS_CODE_ERROR` 및 속성
  `error = "Data truncation: Data too long for column 'content' at row 1"`
  SQL: `insert into tb_feed_comment (content,created_at,creator_id,creator_nickname,creator_profile_url,deleted,deleted_at,feed_id,parent_comment_id,updated_at) values (?,?,?,?,?,?,?,?,?,?)`
- Loki 로그 원문:
  `WARN ... o.h.e.jdbc.spi.SqlExceptionHelper - SQL Error: 1406, SQLState: 22001`
  `ERROR ... o.h.e.jdbc.spi.SqlExceptionHelper - Data truncation: Data too long for column 'content' at row 1`
  → MySQL 1406 / SQLSTATE 22001은 **string data right truncation**, 즉 클라이언트가 보낸 값이 컬럼 길이를 넘었다는 뜻으로 서버 자원/네트워크 문제가 아님.
- 요청 처리 순서가 정상적으로 진행되다가 INSERT에서만 실패:
  - `.383446~.385337` `select ... from tb_feed where f1_0.id=?` 성공, `result-set` `jdbc.row-count = 1` (피드 145 존재)
  - `.386867~.387563` Redis `GET` 성공, 로그 `[외부사용자 조회] 캐시 HIT - userId: 1, elapsed: 1ms`
  - `.405755~.415915` INSERT 실패 (10.16ms)
  - `.422485` connection span 이벤트 `rollback` → 트랜잭션 롤백, 댓글 미저장
- 인증은 정상 통과: 로그 MDC `userId=1`, 루트 span `exception = "none"`, `security filterchain before/after` 12개 필터 모두 통과(JwtAuthenticationFilter 포함).

**확신도: 높음** (트레이스 span 에러 속성과 로그 원문이 동일 traceId·동일 spanId(`515ad80d52040675`, connection span)로 일치)

**반증 데이터: 없음.**
단, 이 traceId 1건 외에 동일 오류의 재현/빈도를 보여주는 관측값이 없어 "제보된 실패 전체"가 같은 원인인지는 미확정(아래 3번 조치 참조).

---

### 후보 2: 예외 매핑 결함으로 500 반환

**근거**
- `WARN ... c.e.t.a.c.e.GlobalExceptionHandler - handleAllException` — 길이 초과라는 **클라이언트 입력 오류**가 전용 핸들러가 아닌 포괄(catch-all) 핸들러로 처리됨.
- 그 결과 루트 span 속성 `outcome = "SERVER_ERROR"`, `status = "500"`, 로그 `[HTTP] POST /api/feeds/145/comments 500 - 80ms`.
- 즉 사용자에게는 "서버 장애"로 보이지만 실제로는 400(Bad Request) 성격의 실패다. 제보가 "장애"로 접수된 것도 이 오분류의 영향으로 볼 수 있다.

**확신도: 높음** (핸들러 로그와 500 상태가 같은 traceId에서 직접 관측됨)

**반증 데이터: 없음.**
(다만 이는 근본 원인이라기보다 후보 1의 결과를 증폭시킨 결함이다.)

---

### 후보 3: `content` 컬럼 스키마 정의가 요구사항 대비 작음 / charset 문제

**근거**
- 관측 가능한 것은 "값이 컬럼보다 길다"는 사실뿐이다. 실제 컬럼 타입·길이(`VARCHAR(n)` / `TEXT` 여부), 엔티티 `@Column(length=...)`, 입력값의 실제 길이가 **데이터에 전혀 없다.**
- 특히 utf8mb4 환경에서 이모지·다국어가 포함되면 바이트 기준 제한 타입(`TINYTEXT` 등)에서 예상보다 일찍 1406이 발생할 수 있으나, 이를 뒷받침할 관측값이 현재 없다.

**확신도: 낮음 — 데이터 부족**
필요한 추가 수집: `SHOW CREATE TABLE tb_feed_comment`(또는 `information_schema.columns`의 `DATA_TYPE`/`CHARACTER_MAXIMUM_LENGTH`/`CHARACTER_OCTET_LENGTH`), 엔티티/DTO의 길이 제약, 실패 요청의 본문 길이, 최근 스키마 마이그레이션·배포 이력.

**반증 데이터: 없음** (검증도 반증도 할 수 없는 상태)

---

### 배제한 원인 (반증 근거 명시)

| 가설 | 반증 데이터 |
|---|---|
| DB 커넥션 풀 고갈 | `hikaricp_connections_active{content-service, 10.42.1.35}` 최대 **1**, `hikaricp_connections_pending` 전 구간 **0**. 트레이스에서도 커넥션 획득이 `.378933→.380938` (약 2ms)로 즉시 성공 |
| 인프라/파드 다운 | `up` 전 타깃 **1** 유지(content 2개 파드, auth, chat, kafka, mongodb, redis, 모든 노드), `mongodb_up=1`, `kafka_brokers=1` |
| GC / 메모리 압박 | content-service `qnxk6` GC pause rate는 장애 시각(1785251106)에 **0**, 이후에도 최대 7.08e-5초/초 수준. auth/chat도 1e-4초/초 미만 |
| Kafka·chat 소비 지연 | 모든 consumergroup lag **0** (일부 파티션의 `-1`은 커밋 오프셋 없음을 뜻하며 전 구간 값 변화 없음). 애초에 트랜잭션이 INSERT에서 롤백되어 **Kafka 발행 span 자체가 트레이스에 없음** → 알림 미발송은 결과이지 원인이 아님 |
| 인증(auth) 실패 / 401 | 로그 MDC `userId=1`, 필터체인 12/12 통과, 루트 span `exception=none`. 단 `status="401"` 메트릭은 **no series로 수집 실패**했으므로 "401이 0건"임을 메트릭으로 확증하진 못함(트레이스 근거로만 배제, 그만큼 확신도 하향) |
| 타임아웃/지연 | 전체 요청 80ms, INSERT 10ms. 지연 신호 없음 |

**결론 요약:** 피드 145에 대한 댓글 작성 요청이 `tb_feed_comment.content` 길이 초과로 DB에서 거부(1406/22001)되어 트랜잭션이 롤백되었고, 이 입력 오류가 포괄 예외 핸들러를 타면서 500으로 응답된 것이 관측된 실패의 전부다. 인프라·Kafka·인증·리소스 계열 원인은 모두 반증된다.

---

## 3. 권장 다음 조치

**즉시 확인 (원인 확정용)**
1. `SHOW CREATE TABLE tb_feed_comment;` 로 `content` 컬럼의 타입/길이/charset 확인 → 엔티티의 `@Column(length=...)`, 요청 DTO의 `@Size` 제약과 대조. 세 값이 불일치하면 그것이 근본 원인.
2. 실패 요청 본문 길이 확보(클라이언트 제보자 입력 또는 액세스 로그 body size) → 컬럼 한계와 비교. 이모지/멀티바이트 포함 여부도 함께 확인.
3. 최근 1시간 배포·스키마 마이그레이션 이력 확인 (컬럼 축소나 검증 로직 제거가 있었는지).

**영향 범위 측정 (현재 데이터로는 판단 불가 — 조회창이 실제로 15:03~15:10:53, 약 8분에 불과)**

4. 질문의 "최근 1시간" 전체로 창을 넓혀 재조회:
   - `sum(increase(http_server_requests_seconds_count{application="content-service", uri="/feeds/{feedId}/comments", status="500"}[1h]))`
   - Loki: `{job="default/content-service"} |= "Data truncation" |= "tb_feed_comment"` 를 1시간 창으로 집계 → 단발성인지 다수 사용자 영향인지 확정.
   - 다른 파드(`content-service-6c5fff897-scw7k`, 10.42.3.41)에서도 동일 오류가 있는지 확인(주어진 창에서는 해당 파드 오류 0건).
5. 수집 실패한 `status="401"` 메트릭 재조회(또는 `sum by (status) (rate(http_server_requests_seconds_count{application="content-service"}[5m]))` 로 상태코드 분포 일괄 확인) → 인증 계열 배제를 메트릭으로도 확정.

**수정 조치**

6. API 계층 길이 검증 추가: 요청 DTO에 `@Size(max=N)` (N = 실제 컬럼 한계와 동일) 적용 → DB까지 가기 전에 400으로 차단.
7. `GlobalExceptionHandler`에 `DataIntegrityViolationException` / SQLState `22001` 전용 핸들러 추가 → 500이 아닌 400 + 사용자 안내 메시지 반환. 현재처럼 `handleAllException`으로 떨어지면 알람·SLO가 실제 서버 장애와 구분되지 않는다.
8. 스키마 요구사항 재검토: 서비스가 허용하려는 댓글 길이가 컬럼보다 크다면 `TEXT` 등으로 확장하는 마이그레이션 검토(단 6·7번 검증은 확장과 무관하게 필요).
9. 알림 파이프라인 영향 확인: 이 요청은 롤백되어 Kafka 발행이 없었으므로 chat 알림도 미발송. 정상 케이스에서 발행 span이 트레이스에 남는지 확인해 "발행 누락"과 "발행 실패"를 구분할 수 있게 계측 점검.

---

## 관측 증거 (Evidence)

> 이 조사가 실제로 본 값이다. 원본 응답 전체는 `reports/raw/6a68c522cb16f0a29c2c4bd0a86df613-*.json`에 있다.

### span (duration 상위 9 / 전체 9)

| ms | service | span | 시작 |
|---:|---|---|---|
| 82.15 | content-service | `http post /feeds/{feedId}/comments` | 2026-07-28T15:05:06.360059Z |
| 79.24 | content-service | `secured request` | 2026-07-28T15:05:06.360653Z |
| 60.86 | content-service | `connection` | 2026-07-28T15:05:06.378933Z |
| 10.16 | content-service | `query` | 2026-07-28T15:05:06.405755Z |
| 1.89 | content-service | `query` | 2026-07-28T15:05:06.383446Z |
| 0.70 | content-service | `GET` | 2026-07-28T15:05:06.386867Z |
| 0.55 | content-service | `result-set` | 2026-07-28T15:05:06.385490Z |
| 0.22 | content-service | `security filterchain before` | 2026-07-28T15:05:06.360379Z |
| 0.09 | content-service | `security filterchain after` | 2026-07-28T15:05:06.439938Z |

### 로그 원문 (9 / 전체 9줄)

```
2026-07-28T15:05:06.388117930Z  [content-service]  2026-07-29 00:05:06.387 [http-nio-8082-exec-1]  INFO [traceId=6a68c522cb16f0a29c2c4bd0a86df613,spanId=515ad80d52040675,userId=1] c.e.t.e.u.s.ExternalUserInfoService - [외부사용자 조회] 캐시 HIT - userId: 1, elapsed: 1ms
2026-07-28T15:05:06.416327901Z  [content-service]  2026-07-29 00:05:06.416 [http-nio-8082-exec-1]  WARN [traceId=6a68c522cb16f0a29c2c4bd0a86df613,spanId=515ad80d52040675,userId=1] o.h.e.jdbc.spi.SqlExceptionHelper - SQL Error: 1406, SQLState: 22001
2026-07-28T15:05:06.416327901Z  [content-service]  2026-07-29 00:05:06.416 [http-nio-8082-exec-1]  WARN [traceId=6a68c522cb16f0a29c2c4bd0a86df613,spanId=515ad80d52040675,userId=1] o.h.e.jdbc.spi.SqlExceptionHelper - SQL Error: 1406, SQLState: 22001
2026-07-28T15:05:06.416473682Z  [content-service]  2026-07-29 00:05:06.416 [http-nio-8082-exec-1] ERROR [traceId=6a68c522cb16f0a29c2c4bd0a86df613,spanId=515ad80d52040675,userId=1] o.h.e.jdbc.spi.SqlExceptionHelper - Data truncation: Data too long for column 'content' at row 1
2026-07-28T15:05:06.416473682Z  [content-service]  2026-07-29 00:05:06.416 [http-nio-8082-exec-1] ERROR [traceId=6a68c522cb16f0a29c2c4bd0a86df613,spanId=515ad80d52040675,userId=1] o.h.e.jdbc.spi.SqlExceptionHelper - Data truncation: Data too long for column 'content' at row 1
2026-07-28T15:05:06.438324478Z  [content-service]  2026-07-29 00:05:06.425 [http-nio-8082-exec-1]  WARN [traceId=6a68c522cb16f0a29c2c4bd0a86df613,spanId=515ad80d52040675,userId=1] c.e.t.a.c.e.GlobalExceptionHandler - handleAllException
2026-07-28T15:05:06.438324478Z  [content-service]  2026-07-29 00:05:06.425 [http-nio-8082-exec-1]  WARN [traceId=6a68c522cb16f0a29c2c4bd0a86df613,spanId=515ad80d52040675,userId=1] c.e.t.a.c.e.GlobalExceptionHandler - handleAllException
2026-07-28T15:05:06.440266895Z  [content-service]  2026-07-29 00:05:06.440 [http-nio-8082-exec-1] ERROR [traceId=6a68c522cb16f0a29c2c4bd0a86df613,spanId=9c2c4bd0a86df613,userId=NONE] c.e.t.a.c.f.RequestLoggingFilter - [HTTP] POST /api/feeds/145/comments 500 - 80ms
2026-07-28T15:05:06.440266895Z  [content-service]  2026-07-29 00:05:06.440 [http-nio-8082-exec-1] ERROR [traceId=6a68c522cb16f0a29c2c4bd0a86df613,spanId=9c2c4bd0a86df613,userId=NONE] c.e.t.a.c.f.RequestLoggingFilter - [HTTP] POST /api/feeds/145/comments 500 - 80ms
```

### 메트릭 시계열

| 쿼리 | series | 점 | min | max | last | 값이 0이던 구간 |
|---|---|---:|---:|---:|---:|---|
| `hikaricp_connections_active` | `{__name__=hikaricp_connections_active, application=auth-service, cluster=yogurtte-k3s-prod, container=auth-service, instance=10.42.1.34:8090, job=auth-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=auth-service-855c75679d-45fxb, pool=HikariPool-1, service=auth-service}` | 32 | 0 | 0 | 0 | **2026-07-28T15:03:00Z ~ 2026-07-28T15:10:45Z** |
| `hikaricp_connections_active` | `{__name__=hikaricp_connections_active, application=chat-service, cluster=yogurtte-k3s-prod, container=chat-service, instance=10.42.1.31:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-857c54dd97-s5fbl, pool=HikariPool-1}` | 32 | 0 | 0 | 0 | **2026-07-28T15:03:00Z ~ 2026-07-28T15:10:45Z** |
| `hikaricp_connections_active` | `{__name__=hikaricp_connections_active, application=content-service, cluster=yogurtte-k3s-prod, container=content-service, instance=10.42.1.35:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-6c5fff897-qnxk6, pool=HikariPool-1}` | 32 | 0 | 1 | 0 | **2026-07-28T15:03:00Z ~ 2026-07-28T15:05:00Z, 2026-07-28T15:06:15Z ~ 2026-07-28T15:10:45Z** |
| `hikaricp_connections_active` | `{__name__=hikaricp_connections_active, application=content-service, cluster=yogurtte-k3s-prod, container=content-service, instance=10.42.3.41:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-6c5fff897-scw7k, pool=HikariPool-1}` | 32 | 0 | 0 | 0 | **2026-07-28T15:03:00Z ~ 2026-07-28T15:10:45Z** |
| `hikaricp_connections_pending` | `{__name__=hikaricp_connections_pending, application=auth-service, cluster=yogurtte-k3s-prod, container=auth-service, instance=10.42.1.34:8090, job=auth-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=auth-service-855c75679d-45fxb, pool=HikariPool-1, service=auth-service}` | 32 | 0 | 0 | 0 | **2026-07-28T15:03:00Z ~ 2026-07-28T15:10:45Z** |
| `hikaricp_connections_pending` | `{__name__=hikaricp_connections_pending, application=chat-service, cluster=yogurtte-k3s-prod, container=chat-service, instance=10.42.1.31:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-857c54dd97-s5fbl, pool=HikariPool-1}` | 32 | 0 | 0 | 0 | **2026-07-28T15:03:00Z ~ 2026-07-28T15:10:45Z** |
| `hikaricp_connections_pending` | `{__name__=hikaricp_connections_pending, application=content-service, cluster=yogurtte-k3s-prod, container=content-service, instance=10.42.1.35:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-6c5fff897-qnxk6, pool=HikariPool-1}` | 32 | 0 | 0 | 0 | **2026-07-28T15:03:00Z ~ 2026-07-28T15:10:45Z** |
| `hikaricp_connections_pending` | `{__name__=hikaricp_connections_pending, application=content-service, cluster=yogurtte-k3s-prod, container=content-service, instance=10.42.3.41:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-6c5fff897-scw7k, pool=HikariPool-1}` | 32 | 0 | 0 | 0 | **2026-07-28T15:03:00Z ~ 2026-07-28T15:10:45Z** |
| `rate(jvm_gc_pause_seconds_sum[5m])` | `{action=end of major GC, application=chat-service, cause=Allocation Failure, cluster=yogurtte-k3s-prod, container=chat-service, gc=MarkSweepCompact, instance=10.42.1.31:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-857c54dd97-s5fbl}` | 32 | 0 | 0 | 0 | **2026-07-28T15:03:00Z ~ 2026-07-28T15:10:45Z** |
| `rate(jvm_gc_pause_seconds_sum[5m])` | `{action=end of minor GC, application=auth-service, cause=G1 Evacuation Pause, cluster=yogurtte-k3s-prod, container=auth-service, gc=G1 Young Generation, instance=10.42.1.34:8090, job=auth-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=auth-service-855c75679d-45fxb, service=auth-service}` | 32 | 0 | 0.000 | 0 | **2026-07-28T15:06:15Z ~ 2026-07-28T15:10:45Z** |
| `rate(jvm_gc_pause_seconds_sum[5m])` | `{action=end of minor GC, application=chat-service, cause=Allocation Failure, cluster=yogurtte-k3s-prod, container=chat-service, gc=Copy, instance=10.42.1.31:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-857c54dd97-s5fbl}` | 32 | 0.000 | 0.000 | 0.000 | — |
| `rate(jvm_gc_pause_seconds_sum[5m])` | `{action=end of minor GC, application=content-service, cause=Allocation Failure, cluster=yogurtte-k3s-prod, container=content-service, gc=Copy, instance=10.42.1.35:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-6c5fff897-qnxk6}` | 32 | 0 | 0.000 | 0 | **2026-07-28T15:03:00Z ~ 2026-07-28T15:06:00Z, 2026-07-28T15:10:15Z ~ 2026-07-28T15:10:45Z** |
| `rate(jvm_gc_pause_seconds_sum[5m])` | `{action=end of minor GC, application=content-service, cause=Allocation Failure, cluster=yogurtte-k3s-prod, container=content-service, gc=Copy, instance=10.42.3.41:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-6c5fff897-scw7k}` | 32 | 0 | 0.000 | 0 | **2026-07-28T15:07:00Z ~ 2026-07-28T15:10:45Z** |
| `up` | `{__name__=up, app=node-exporter, cluster=yogurtte-k3s-prod, component=metrics, container=node-exporter, instance=ip-172-31-40-241, job=integrations/node_exporter, k8s_cluster_name=yogurtte-k3s-prod, namespace=monitoring, pod=grafana-k8s-monitoring-node-exporter-cph5l, source=kubernetes, workload=DaemonSet/grafana-k8s-monitoring-node-exporter}` | 32 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, app=node-exporter, cluster=yogurtte-k3s-prod, component=metrics, container=node-exporter, instance=ip-172-31-45-39, job=integrations/node_exporter, k8s_cluster_name=yogurtte-k3s-prod, namespace=monitoring, pod=grafana-k8s-monitoring-node-exporter-9kvqt, source=kubernetes, workload=DaemonSet/grafana-k8s-monitoring-node-exporter}` | 32 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, cluster=yogurtte-k3s-prod, container=auth-service, instance=10.42.1.34:8090, job=auth-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=auth-service-855c75679d-45fxb}` | 32 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, cluster=yogurtte-k3s-prod, container=chat-service, instance=10.42.1.31:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-857c54dd97-s5fbl}` | 32 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, cluster=yogurtte-k3s-prod, container=content-service, instance=10.42.1.35:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-6c5fff897-qnxk6}` | 32 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, cluster=yogurtte-k3s-prod, container=content-service, instance=10.42.3.41:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-6c5fff897-scw7k}` | 32 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, cluster=yogurtte-k3s-prod, instance=10.42.1.248:8080, job=integrations/kubernetes/kube-state-metrics, k8s_cluster_name=yogurtte-k3s-prod, source=kubernetes}` | 32 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, cluster=yogurtte-k3s-prod, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod}` | 32 | 1 | 1 | 1 | — |
| `mongodb_up` | `{__name__=mongodb_up, cluster=yogurtte-k3s-prod, instance=infra-server, job=mongodb, k8s_cluster_name=yogurtte-k3s-prod}` | 32 | 1 | 1 | 1 | — |
| `kafka_brokers` | `{__name__=kafka_brokers, cluster=yogurtte-k3s-prod, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod}` | 32 | 1 | 1 | 1 | — |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=chat-service-fcm-tokens, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=0, topic=user.fcm-tokens}` | 32 | 0 | 0 | 0 | **2026-07-28T15:03:00Z ~ 2026-07-28T15:10:45Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=chat-service-fcm-tokens, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=1, topic=user.fcm-tokens}` | 32 | 0 | 0 | 0 | **2026-07-28T15:03:00Z ~ 2026-07-28T15:10:45Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=chat-service-fcm-tokens, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=2, topic=user.fcm-tokens}` | 32 | 0 | 0 | 0 | **2026-07-28T15:03:00Z ~ 2026-07-28T15:10:45Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=chat-service-notification-settings, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=0, topic=user.notification-settings}` | 32 | 0 | 0 | 0 | **2026-07-28T15:03:00Z ~ 2026-07-28T15:10:45Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=chat-service-notification-settings, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=1, topic=user.notification-settings}` | 32 | 0 | 0 | 0 | **2026-07-28T15:03:00Z ~ 2026-07-28T15:10:45Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=chat-service-notification-settings, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=2, topic=user.notification-settings}` | 32 | 0 | 0 | 0 | **2026-07-28T15:03:00Z ~ 2026-07-28T15:10:45Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=db-writer, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=0, topic=chat.messages}` | 32 | 0 | 0 | 0 | **2026-07-28T15:03:00Z ~ 2026-07-28T15:10:45Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=db-writer, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=1, topic=chat.messages}` | 32 | 0 | 0 | 0 | **2026-07-28T15:03:00Z ~ 2026-07-28T15:10:45Z** |
| `websocket_active_users` | `{__name__=websocket_active_users, application=chat-service, cluster=yogurtte-k3s-prod, container=chat-service, instance=10.42.1.31:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-857c54dd97-s5fbl}` | 32 | 0 | 0 | 0 | **2026-07-28T15:03:00Z ~ 2026-07-28T15:10:45Z** |

값이 0이던 구간은 굵게 표시했다 — 프로세스가 사라져 시계열이 꺾인 것이 유일한 신호인 장애가 있다.

