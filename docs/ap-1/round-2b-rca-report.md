# RCA Report — `6a69bf23e45c1e51c3475f5e5f3a1b04`

| 항목 | 값 |
|---|---|
| 모드 | rca |
| 질문 | 최근 1시간 안에 댓글 작성이 실패했다는 제보가 있다. 원인을 조사해줘 |
| 시각 | 2026-07-29T08:55:08.127816800Z |
| provider | claude-cli |
| model | `claude-opus-5` · turns 1 |
| prompt | `.\prompts\system-prompt.md` |
| tokens | in 66523 (cacheRead 23,453 · cacheCreate 43,068) / out 6472 · cost $0.6352 |
| elapsed | total 110016ms (tempo 415 · loki 194 · mimir 795 · assemble 1 · llm 100556) |

## 탐색 (Triage)

| 항목 | 값 |
|---|---|
| 시간창 해석 | 상대 표현 '최근 1시간' |
| 스윕 창 | 2026-07-29T07:54:14.432452700Z ~ 2026-07-29T08:54:14.432452700Z |
| 좁힌 창 | 2026-07-29T08:49:00Z ~ 2026-07-29T08:54:14Z |
| 대상 | content-service |
| traceId | 6a69bf23e45c1e51c3475f5e5f3a1b04 |
| 트레이스 후보 | 1건 |
| 계획 파싱 | 성공 |
| prompt | `.\prompts\triage-prompt.md` |
| tokens | in 47946 / out 3405 · cost $0.3857 |
| chars | 컨텍스트 37,992 + 프롬프트 1,231 = **39,223** |
| elapsed | survey 1484ms · llm 52190ms |

**선정 이유**: 창 안에서 관측된 유일한 이상 신호가 08:51:47Z content-service 댓글 작성 트레이스 1건이고 제보 증상과 정확히 일치하므로, 그 트레이스 앞뒤로 여유를 둔 5분 구간에서 content-service의 스팬 속성·비-ERROR 레벨 로그를 직접 봐야 한다.

**근거**

- Tempo: 창 전체에서 에러 트레이스가 정확히 1건 — root=content-service, rootTraceName='http post /feeds/{feedId}/comments', startTime 2026-07-29T08:51:47.463Z, durationMs=119. 질문의 '댓글 작성 실패'와 엔드포인트·시각이 일치
- 해당 트레이스 serviceStats: content-service spanCount=9, errorCount=1 — 실패가 content-service 내부에서만 발생, auth/chat 스팬은 트레이스에 아예 없음(인증·알림 경로 배제)
- 에러 스팬 0bd845bab5a501ef는 08:51:47.486Z(루트 시작 +22.9ms)에 시작해 46.18ms 지속 — 요청은 정상 진입했고 처리 도중 하위 호출 단계에서 실패한 형태
- Loki ERROR/WARN 발생률: result=[] (totalLinesProcessed 4714, totalPostFilterLines 4645, totalEntriesReturned 0) — 에러 트레이스가 존재함에도 ERROR/WARN 로그가 0건. '로그가 없는 것' 자체가 예외가 삼켜졌다는 신호
- 인프라: up=1이 07:54:14~08:54:14 13개 샘플 전부 유지(content-service 파드 10.42.1.35 / 10.42.3.41 포함), mongodb_up=1, kafka_brokers=1 — 시계열 끊김·결측 없음 → 프로세스 사망/재시작 원인 아님
- kafka_consumergroup_lag 전 컨슈머그룹 0 유지(일부 파티션 -1은 창 전체 상수 = 미할당, 변화 아님), websocket_active_users=0 상수 — 창 안에서 변화한 값이 없어 이상 후보가 되지 못함

**스윕이 찾은 트레이스** (고른 것은 6a69bf23e45c1e51c3475f5e5f3a1b04)

| traceId | root service | root span | ms |
|---|---|---|---:|
| `6a69bf23e45c1e51c3475f5e5f3a1b04` ←선택 | content-service | http post /feeds/{feedId}/comments | 119 |

## 수집 범위 (Coverage)

- **window**: 2026-07-29T08:49:00Z ~ 2026-07-29T08:54:14Z (314s)
- **trace**: 8,761B / 9 spans
- **logs**: errwarn=5,682B · traceId=6,323B
- **metrics**: 8 수집 / 52,516B, 누락 [sum(rate(http_server_requests_seconds_count{application="content-service", status="401"}[1m]))]
- **context**: 73,909 chars (+ 시스템 프롬프트 597 chars)

### 토큰 축 (개선 지표)

| 단계 | 총 in | chars | 컨텍스트 토큰 |
|---|---:|---:|---:|
| 탐색 | 47,946 | 39,223 | 21,370 |
| 분석 | 66,523 | 74,506 | 39,947 |
| **합계** | **114,469** | | **61,317** |

- **overheadTokens 26,576 tok — 이 회차에 실측**(1자 프롬프트 프로브, 같은 명령·모델·샌드박스).
  호출마다 한 번씩 얹히므로 단계별로 뺀다. **다른 날 상수를 쓰지 않았다 = `█ 실측`.**
- contextTokens (count_tokens API): 측정 안 됨 — 구독 CLI 경로엔 API 키가 없다
- 비용은 **API 환산 추정치**다. 구독 계정으로 호출하므로 실제 청구액이 아니다.

## 수집 실패/누락

- Metric 'sum(rate(http_server_requests_seconds_count{application="content-service", status="401"}[1m]))' returned no series in this window; skipped.

---

## 1. 원인 후보 랭킹 (최대 3개)

1. **댓글 본문 길이가 `tb_feed_comment.content` 컬럼 허용치를 초과 → MySQL 에러 1406(22001)으로 INSERT 거부 → 트랜잭션 롤백 → HTTP 500**
2. **애플리케이션 레벨 길이 검증 부재 + 예외 매핑 누락** (400으로 반환되어야 할 입력 오류가 catch-all 핸들러를 타고 500으로 나감)
3. **엔티티/DDL 길이 정의 불일치 또는 최근 클라이언트·스키마 변경으로 인한 허용 길이 축소** — 데이터 부족

---

## 2. 후보별 근거

### 후보 1 — content 컬럼 길이 초과로 INSERT 실패

- **근거:**
  - 트레이스 `6a69bf23e45c1e51c3475f5e5f3a1b04` 내 `query` span (spanId `C9hFurWlAe8=`, `STATUS_CODE_ERROR`):
    - `jdbc.query[0]` = `insert into tb_feed_comment (content,created_at,creator_id,...) values (?,?,...)`
    - `error` = `Data truncation: Data too long for column 'content' at row 1`
    - 구간 08:51:47.486 → 08:51:47.532 (46ms)
  - Loki WARN 원문: `SQL Error: 1406, SQLState: 22001` (17:51:47.532 KST = 08:51:47.532Z, `userId=1`)
  - Loki ERROR 원문: `o.h.e.jdbc.spi.SqlExceptionHelper - Data truncation: Data too long for column 'content' at row 1`
  - JDBC `connection` span 이벤트: `acquired` 08:51:47.473 → **`rollback` 08:51:47.558** → 댓글이 저장되지 않고 트랜잭션 전체 롤백됨
  - 루트 span `http post /feeds/{feedId}/comments`: `status=500`, `outcome=SERVER_ERROR`, `http.url=/api/feeds/145/comments`
  - Loki: `[HTTP] POST /api/feeds/145/comments 500 - 114ms`
  - 선행 단계는 모두 정상: 피드 조회 `select ... from tb_feed f1_0 where f1_0.id=?` → `jdbc.row-count=1` (5.5ms), Redis `GET` 0.6ms (`[외부사용자 조회] 캐시 HIT - userId: 1, elapsed: 1ms`). 즉 인증·피드 조회·사용자 조회를 모두 통과한 뒤 **INSERT 단계에서만** 실패.
- **확신도:** 높음
- **반증 데이터:** 없음. (단, 실패 요청의 실제 본문 길이와 컬럼 정의는 관측 데이터에 없어 "얼마나 초과했는지"는 미확인)

### 후보 2 — 입력 검증 부재 + 예외 매핑 결함으로 500 반환

- **근거:**
  - DB까지 도달해서야 거부됨 — 컨트롤러/DTO 레벨에서 길이 검증이 있었다면 INSERT span 자체가 생성되지 않았어야 함. 트레이스상 `insert into tb_feed_comment` span이 실제 실행되어 DB에서 1406으로 튕김.
  - Loki WARN: `c.e.t.a.c.e.GlobalExceptionHandler - handleAllException` (17:51:47.560) — 전용 핸들러가 아닌 **catch-all 핸들러**가 처리 → `outcome=SERVER_ERROR`, `status=500`.
  - 클라이언트 입력 오류(4xx)가 서버 오류(5xx)로 집계되므로, 사용자에게는 "원인 불명 실패"로 보이고 에러율 지표도 오염됨.
- **확신도:** 중간 (증상은 데이터로 확정되나, 검증 코드 존재 여부·핸들러 구현은 코드 확인 필요)
- **반증 데이터:** 없음. 다만 `security filterchain` span이 12/12 필터를 정상 통과했고 `exception=none`이므로, 필터 체인 자체의 결함은 아님.

### 후보 3 — 엔티티/DDL 길이 정의 불일치 또는 최근 변경

- **근거:** 관측 데이터에는 `content` 컬럼의 타입·길이·charset, JPA 엔티티의 `@Column(length=...)`, 배포/마이그레이션 이력이 **전혀 없음**. 컬럼 정의가 원래부터 짧았던 것인지, 최근 스키마 변경이나 프런트 maxlength 회귀로 긴 입력이 통과하게 된 것인지 구분 불가.
- **확신도:** 낮음 — **데이터 부족**
- **반증 데이터:** 없음 (판단 근거 자체가 없음)
- **추가 수집 필요:** `SHOW CREATE TABLE tb_feed_comment`, JPA 엔티티 정의, Flyway/Liquibase 마이그레이션 이력, content-service(`6c5fff897`) 및 클라이언트 최근 배포 시각.

### 배제된 후보 (관측값으로 반증됨)

| 후보 | 반증 데이터 |
|---|---|
| Kafka / chat 소비 지연 | `kafka_brokers=1` 유지, 전 구간 `up{job="kafka"}=1`. `chat.messages`·`user.notifications` 등 모든 컨슈머그룹 lag `0` (일부 `-1`은 미할당 파티션). 애초에 트레이스에 Kafka producer span이 없음 — INSERT에서 롤백되어 발행 단계에 도달조차 못함 |
| auth / 인증 실패 | `secured request` span 정상 종료, 12개 필터 전부 통과, `exception=none`, 내부 로그 `userId=1`. `up{job="auth-service"}=1`, auth GC·커넥션풀 전 구간 0 |
| DB 커넥션 풀 고갈 | 해당 pod(`10.42.3.41`) `hikaricp_connections_active`·`pending` 전 구간 `0`. 커넥션 `acquired`까지 1.8ms |
| GC / 메모리 압박 | content-service `scw7k` GC pause rate 최대 `0.000025 s/s`. major GC 0 |
| 인프라 다운 | 전체 `up=1` (노드 4대, 서비스 3종, kafka/redis/mongodb 모두). `mongodb_up=1` |
| Redis 장애 | `GET` span 0.598ms, 캐시 HIT 로그 `elapsed: 1ms` |

### 결론의 한계 (수집 실패 반영)

- 메트릭 `sum(rate(http_server_requests_seconds_count{application="content-service", status="401"}[1m]))`이 **no series**로 실패했다. 인증 실패가 0건이어서인지 메트릭/레이블 자체가 없어서인지 구분 불가하므로, auth 배제는 트레이스·로그 근거에만 의존한다(다만 그 근거는 직접적이고 강함).
- 조회 시간창이 **5분 14초(08:49:00~08:54:14Z)**뿐이라 제보의 "최근 1시간" 전체를 커버하지 못한다. 이 창의 ERROR/WARN 쿼리 결과는 **전부 이 traceId 1건**이므로, 현재 확인된 실패는 `userId=1` / `feed 145`에 대한 **단일 요청**이다. 광범위 장애인지 개별 사용자 이슈인지는 **데이터 부족**.

---

## 3. 권장 다음 조치

**즉시 확인 (원인 확정)**
1. 컬럼 정의 확인: `SHOW CREATE TABLE tb_feed_comment;` 또는
   `SELECT COLUMN_NAME, DATA_TYPE, CHARACTER_MAXIMUM_LENGTH, CHARACTER_SET_NAME FROM information_schema.COLUMNS WHERE TABLE_NAME='tb_feed_comment' AND COLUMN_NAME='content';`
2. JPA 엔티티의 `@Column(length=...)` 값과 위 DDL 길이를 대조 (불일치 시 `ddl-auto=validate` 미적용 여부도 확인).
3. 영향 범위 산정 — Loki를 **1시간 전체**로 재조회:
   - `{job="default/content-service"} |= "1406"`
   - `{job="default/content-service"} |= "POST /api/feeds" |= " 500 "`
   → 발생 건수·고유 userId 수로 개별 이슈/광범위 회귀 판별.
4. content-service(`6c5fff897`) 최근 배포 및 DB 마이그레이션 이력과 최초 발생 시각 대조.

**단기 조치**
5. `GlobalExceptionHandler`에 `DataIntegrityViolationException` / `org.hibernate.exception.DataException` 전용 핸들러 추가 → **400 + 명확한 메시지** 반환 (현재 `handleAllException`이 500으로 처리).
6. 댓글 DTO에 `@Size(max=<DDL 길이>)` 검증 추가 — DB까지 가기 전에 차단.
7. 클라이언트 입력창 maxlength를 서버/DDL 값과 일치시키고, 정책상 필요하면 컬럼을 `TEXT`/충분한 `VARCHAR`로 확장 (마이그레이션 필요).

**관측성 보강**
8. 실패 요청의 입력 길이를 알 수 있도록 검증 실패 로그에 `content.length()`를 남길 것 (원문은 남기지 말 것 — PII).
9. `http_server_requests_seconds_count{application="content-service"}`를 status 필터 없이 조회해 메트릭·레이블 존재 여부를 검증 (이번 401 쿼리 no-series 원인 규명).
10. 부가 관찰: 응답 로그 `RequestLoggingFilter`가 `userId=NONE`인 반면 내부 로그는 `userId=1` — 응답 시점 MDC 소실로 보이며 이번 장애와 인과관계는 없으나, 향후 디버깅을 위해 MDC 정리 시점 점검 권장.

---

## 관측 증거 (Evidence)

> 이 조사가 실제로 본 값이다. 원본 응답 전체는 `reports/raw/6a69bf23e45c1e51c3475f5e5f3a1b04-*.json`에 있다.

### span (duration 상위 9 / 전체 9)

| ms | service | span | 시작 |
|---:|---|---|---|
| 119.22 | content-service | `http post /feeds/{feedId}/comments` | 2026-07-29T08:51:47.463241Z |
| 113.75 | content-service | `secured request` | 2026-07-29T08:51:47.463696Z |
| 105.72 | content-service | `connection` | 2026-07-29T08:51:47.471538Z |
| 46.18 | content-service | `query` | 2026-07-29T08:51:47.486170Z |
| 5.53 | content-service | `query` | 2026-07-29T08:51:47.475533Z |
| 0.60 | content-service | `GET` | 2026-07-29T08:51:47.482031Z |
| 0.52 | content-service | `result-set` | 2026-07-29T08:51:47.481220Z |
| 0.17 | content-service | `security filterchain after` | 2026-07-29T08:51:47.577479Z |
| 0.15 | content-service | `security filterchain before` | 2026-07-29T08:51:47.463532Z |

### 로그 원문 (9 / 전체 9줄)

```
2026-07-29T08:51:47.483466913Z  [content-service]  2026-07-29 17:51:47.482 [http-nio-8082-exec-4]  INFO [traceId=6a69bf23e45c1e51c3475f5e5f3a1b04,spanId=502823a48367b328,userId=1] c.e.t.e.u.s.ExternalUserInfoService - [외부사용자 조회] 캐시 HIT - userId: 1, elapsed: 1ms
2026-07-29T08:51:47.533044970Z  [content-service]  2026-07-29 17:51:47.532 [http-nio-8082-exec-4]  WARN [traceId=6a69bf23e45c1e51c3475f5e5f3a1b04,spanId=502823a48367b328,userId=1] o.h.e.jdbc.spi.SqlExceptionHelper - SQL Error: 1406, SQLState: 22001
2026-07-29T08:51:47.533044970Z  [content-service]  2026-07-29 17:51:47.532 [http-nio-8082-exec-4]  WARN [traceId=6a69bf23e45c1e51c3475f5e5f3a1b04,spanId=502823a48367b328,userId=1] o.h.e.jdbc.spi.SqlExceptionHelper - SQL Error: 1406, SQLState: 22001
2026-07-29T08:51:47.533241986Z  [content-service]  2026-07-29 17:51:47.533 [http-nio-8082-exec-4] ERROR [traceId=6a69bf23e45c1e51c3475f5e5f3a1b04,spanId=502823a48367b328,userId=1] o.h.e.jdbc.spi.SqlExceptionHelper - Data truncation: Data too long for column 'content' at row 1
2026-07-29T08:51:47.533241986Z  [content-service]  2026-07-29 17:51:47.533 [http-nio-8082-exec-4] ERROR [traceId=6a69bf23e45c1e51c3475f5e5f3a1b04,spanId=502823a48367b328,userId=1] o.h.e.jdbc.spi.SqlExceptionHelper - Data truncation: Data too long for column 'content' at row 1
2026-07-29T08:51:47.574871281Z  [content-service]  2026-07-29 17:51:47.560 [http-nio-8082-exec-4]  WARN [traceId=6a69bf23e45c1e51c3475f5e5f3a1b04,spanId=502823a48367b328,userId=1] c.e.t.a.c.e.GlobalExceptionHandler - handleAllException
2026-07-29T08:51:47.574871281Z  [content-service]  2026-07-29 17:51:47.560 [http-nio-8082-exec-4]  WARN [traceId=6a69bf23e45c1e51c3475f5e5f3a1b04,spanId=502823a48367b328,userId=1] c.e.t.a.c.e.GlobalExceptionHandler - handleAllException
2026-07-29T08:51:47.577916733Z  [content-service]  2026-07-29 17:51:47.577 [http-nio-8082-exec-4] ERROR [traceId=6a69bf23e45c1e51c3475f5e5f3a1b04,spanId=c3475f5e5f3a1b04,userId=NONE] c.e.t.a.c.f.RequestLoggingFilter - [HTTP] POST /api/feeds/145/comments 500 - 114ms
2026-07-29T08:51:47.577916733Z  [content-service]  2026-07-29 17:51:47.577 [http-nio-8082-exec-4] ERROR [traceId=6a69bf23e45c1e51c3475f5e5f3a1b04,spanId=c3475f5e5f3a1b04,userId=NONE] c.e.t.a.c.f.RequestLoggingFilter - [HTTP] POST /api/feeds/145/comments 500 - 114ms
```

### 메트릭 시계열

| 쿼리 | series | 점 | min | max | last | 값이 0이던 구간 |
|---|---|---:|---:|---:|---:|---|
| `hikaricp_connections_active` | `{__name__=hikaricp_connections_active, application=auth-service, cluster=yogurtte-k3s-prod, container=auth-service, instance=10.42.1.38:8090, job=auth-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=auth-service-855c75679d-pr892, pool=HikariPool-1, service=auth-service}` | 21 | 0 | 0 | 0 | **2026-07-29T08:49:00Z ~ 2026-07-29T08:54:00Z** |
| `hikaricp_connections_active` | `{__name__=hikaricp_connections_active, application=chat-service, cluster=yogurtte-k3s-prod, container=chat-service, instance=10.42.1.39:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-857c54dd97-w7bf7, pool=HikariPool-1}` | 21 | 0 | 0 | 0 | **2026-07-29T08:49:00Z ~ 2026-07-29T08:54:00Z** |
| `hikaricp_connections_active` | `{__name__=hikaricp_connections_active, application=content-service, cluster=yogurtte-k3s-prod, container=content-service, instance=10.42.1.35:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-6c5fff897-qnxk6, pool=HikariPool-1}` | 21 | 0 | 1 | 0 | **2026-07-29T08:49:00Z ~ 2026-07-29T08:50:00Z, 2026-07-29T08:51:15Z ~ 2026-07-29T08:54:00Z** |
| `hikaricp_connections_active` | `{__name__=hikaricp_connections_active, application=content-service, cluster=yogurtte-k3s-prod, container=content-service, instance=10.42.3.41:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-6c5fff897-scw7k, pool=HikariPool-1}` | 21 | 0 | 0 | 0 | **2026-07-29T08:49:00Z ~ 2026-07-29T08:54:00Z** |
| `hikaricp_connections_pending` | `{__name__=hikaricp_connections_pending, application=auth-service, cluster=yogurtte-k3s-prod, container=auth-service, instance=10.42.1.38:8090, job=auth-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=auth-service-855c75679d-pr892, pool=HikariPool-1, service=auth-service}` | 21 | 0 | 0 | 0 | **2026-07-29T08:49:00Z ~ 2026-07-29T08:54:00Z** |
| `hikaricp_connections_pending` | `{__name__=hikaricp_connections_pending, application=chat-service, cluster=yogurtte-k3s-prod, container=chat-service, instance=10.42.1.39:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-857c54dd97-w7bf7, pool=HikariPool-1}` | 21 | 0 | 0 | 0 | **2026-07-29T08:49:00Z ~ 2026-07-29T08:54:00Z** |
| `hikaricp_connections_pending` | `{__name__=hikaricp_connections_pending, application=content-service, cluster=yogurtte-k3s-prod, container=content-service, instance=10.42.1.35:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-6c5fff897-qnxk6, pool=HikariPool-1}` | 21 | 0 | 0 | 0 | **2026-07-29T08:49:00Z ~ 2026-07-29T08:54:00Z** |
| `hikaricp_connections_pending` | `{__name__=hikaricp_connections_pending, application=content-service, cluster=yogurtte-k3s-prod, container=content-service, instance=10.42.3.41:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-6c5fff897-scw7k, pool=HikariPool-1}` | 21 | 0 | 0 | 0 | **2026-07-29T08:49:00Z ~ 2026-07-29T08:54:00Z** |
| `rate(jvm_gc_pause_seconds_sum[5m])` | `{action=end of major GC, application=chat-service, cause=Allocation Failure, cluster=yogurtte-k3s-prod, container=chat-service, gc=MarkSweepCompact, instance=10.42.1.39:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-857c54dd97-w7bf7}` | 21 | 0 | 0 | 0 | **2026-07-29T08:49:00Z ~ 2026-07-29T08:54:00Z** |
| `rate(jvm_gc_pause_seconds_sum[5m])` | `{action=end of minor GC, application=auth-service, cause=G1 Evacuation Pause, cluster=yogurtte-k3s-prod, container=auth-service, gc=G1 Young Generation, instance=10.42.1.38:8090, job=auth-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=auth-service-855c75679d-pr892, service=auth-service}` | 21 | 0 | 0.000 | 0.000 | **2026-07-29T08:49:00Z ~ 2026-07-29T08:53:15Z** |
| `rate(jvm_gc_pause_seconds_sum[5m])` | `{action=end of minor GC, application=chat-service, cause=Allocation Failure, cluster=yogurtte-k3s-prod, container=chat-service, gc=Copy, instance=10.42.1.39:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-857c54dd97-w7bf7}` | 21 | 0.000 | 0.000 | 0.000 | — |
| `rate(jvm_gc_pause_seconds_sum[5m])` | `{action=end of minor GC, application=content-service, cause=Allocation Failure, cluster=yogurtte-k3s-prod, container=content-service, gc=Copy, instance=10.42.1.35:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-6c5fff897-qnxk6}` | 21 | 0 | 0.000 | 0 | **2026-07-29T08:50:15Z ~ 2026-07-29T08:54:00Z** |
| `rate(jvm_gc_pause_seconds_sum[5m])` | `{action=end of minor GC, application=content-service, cause=Allocation Failure, cluster=yogurtte-k3s-prod, container=content-service, gc=Copy, instance=10.42.3.41:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-6c5fff897-scw7k}` | 21 | 0 | 0.000 | 0 | **2026-07-29T08:49:00Z ~ 2026-07-29T08:49:45Z, 2026-07-29T08:54:00Z ~ 2026-07-29T08:54:00Z** |
| `up` | `{__name__=up, app=node-exporter, cluster=yogurtte-k3s-prod, component=metrics, container=node-exporter, instance=ip-172-31-40-241, job=integrations/node_exporter, k8s_cluster_name=yogurtte-k3s-prod, namespace=monitoring, pod=grafana-k8s-monitoring-node-exporter-cph5l, source=kubernetes, workload=DaemonSet/grafana-k8s-monitoring-node-exporter}` | 21 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, app=node-exporter, cluster=yogurtte-k3s-prod, component=metrics, container=node-exporter, instance=ip-172-31-45-39, job=integrations/node_exporter, k8s_cluster_name=yogurtte-k3s-prod, namespace=monitoring, pod=grafana-k8s-monitoring-node-exporter-9kvqt, source=kubernetes, workload=DaemonSet/grafana-k8s-monitoring-node-exporter}` | 21 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, cluster=yogurtte-k3s-prod, container=auth-service, instance=10.42.1.38:8090, job=auth-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=auth-service-855c75679d-pr892}` | 21 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, cluster=yogurtte-k3s-prod, container=chat-service, instance=10.42.1.39:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-857c54dd97-w7bf7}` | 21 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, cluster=yogurtte-k3s-prod, container=content-service, instance=10.42.1.35:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-6c5fff897-qnxk6}` | 21 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, cluster=yogurtte-k3s-prod, container=content-service, instance=10.42.3.41:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-6c5fff897-scw7k}` | 21 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, cluster=yogurtte-k3s-prod, instance=10.42.1.248:8080, job=integrations/kubernetes/kube-state-metrics, k8s_cluster_name=yogurtte-k3s-prod, source=kubernetes}` | 21 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, cluster=yogurtte-k3s-prod, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod}` | 21 | 1 | 1 | 1 | — |
| `mongodb_up` | `{__name__=mongodb_up, cluster=yogurtte-k3s-prod, instance=infra-server, job=mongodb, k8s_cluster_name=yogurtte-k3s-prod}` | 21 | 1 | 1 | 1 | — |
| `kafka_brokers` | `{__name__=kafka_brokers, cluster=yogurtte-k3s-prod, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod}` | 21 | 1 | 1 | 1 | — |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=chat-service-fcm-tokens, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=0, topic=user.fcm-tokens}` | 21 | 0 | 0 | 0 | **2026-07-29T08:49:00Z ~ 2026-07-29T08:54:00Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=chat-service-fcm-tokens, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=1, topic=user.fcm-tokens}` | 21 | 0 | 0 | 0 | **2026-07-29T08:49:00Z ~ 2026-07-29T08:54:00Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=chat-service-fcm-tokens, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=2, topic=user.fcm-tokens}` | 21 | 0 | 0 | 0 | **2026-07-29T08:49:00Z ~ 2026-07-29T08:54:00Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=chat-service-notification-settings, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=0, topic=user.notification-settings}` | 21 | 0 | 0 | 0 | **2026-07-29T08:49:00Z ~ 2026-07-29T08:54:00Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=chat-service-notification-settings, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=1, topic=user.notification-settings}` | 21 | 0 | 0 | 0 | **2026-07-29T08:49:00Z ~ 2026-07-29T08:54:00Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=chat-service-notification-settings, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=2, topic=user.notification-settings}` | 21 | 0 | 0 | 0 | **2026-07-29T08:49:00Z ~ 2026-07-29T08:54:00Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=db-writer, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=0, topic=chat.messages}` | 21 | 0 | 0 | 0 | **2026-07-29T08:49:00Z ~ 2026-07-29T08:54:00Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=db-writer, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=1, topic=chat.messages}` | 21 | 0 | 0 | 0 | **2026-07-29T08:49:00Z ~ 2026-07-29T08:54:00Z** |
| `websocket_active_users` | `{__name__=websocket_active_users, application=chat-service, cluster=yogurtte-k3s-prod, container=chat-service, instance=10.42.1.39:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-857c54dd97-w7bf7}` | 21 | 0 | 0 | 0 | **2026-07-29T08:49:00Z ~ 2026-07-29T08:54:00Z** |

값이 0이던 구간은 굵게 표시했다 — 프로세스가 사라져 시계열이 꺾인 것이 유일한 신호인 장애가 있다.

