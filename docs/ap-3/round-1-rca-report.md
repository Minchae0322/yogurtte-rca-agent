# RCA Report — `6a6804f072fa431c691e39138e8f5e36`

| 항목 | 값 |
|---|---|
| 모드 | rca |
| 질문 | 피드 등록이 실패했다는 제보가 있다. 원인을 조사해줘 |
| 시각 | 2026-07-28T01:31:09.434485600Z |
| provider | claude-cli |
| prompt | `.\prompts\system-prompt.md` |
| tokens | in 45885 / out 11070 · cost $0.5547 |
| elapsed | total 162937ms (tempo 466 · loki 306 · mimir 326 · assemble 1 · llm 161813) |

## 수집 범위 (Coverage)

- **window**: 2026-07-28T01:23:04.375270Z ~ 2026-07-28T01:27:04.593556Z (240s)
- **trace**: 18,907B / 23 spans
- **logs**: errwarn=3,956B · traceId=3,959B
- **metrics**: 3 수집, 누락 [kafka_consumer_fetch_manager_records_lag]
- **context**: 36,087 chars (~9,021 tok 추정)

## 수집 실패/누락

- Metric 'kafka_consumer_fetch_manager_records_lag' returned no series in this window; skipped.

---

관측 데이터만으로 트레이스를 재구성한 결과, 원인은 트레이스 안에 직접 기록되어 있습니다. 먼저 타임라인(모두 `2026-07-28T01:25:04Z` 기준 ms 오프셋, 전부 동일 트레이스·동일 `connection` span 하위):

| offset(ms) | span / 이벤트 | 결과 |
|---|---|---|
| 375.270 | `http post /feeds` (SERVER) 시작 | — |
| 405.602 → 407.297 | `connection` 시작 → `acquired` | 커넥션 획득 1.7ms |
| 409.7–413.9 | `select ... from categories` | row-count 1 |
| 422.3–425.2 | `insert into tb_feed ...` | row-affected 1 |
| 425.5–425.9 | `generated-keys` | **feed_id = 148** |
| 427.5–433.7 | `insert into tb_feed_attachment_file` ×2 | keys 225, 226 |
| 436.5–439.4 | `select ... from tb_hashtags where name=?` | **row-count 0** (없음) |
| 445.0–447.8 | `insert into tb_hashtags ...` | **hashtag id = 173** |
| 453.5–456.3 | `insert into tb_feed_hashtags` | 성공, key 212 → **(148,173)** |
| 464.4–466.4 | `update tb_hashtags set usage_count=? where id=?` | row-affected 1 |
| 469.7–473.1 | `select ... from tb_hashtags where name=?` | **row-count 1** (방금 만든 173을 찾음) |
| 479.1–545.1 | `insert into tb_feed_hashtags` | **ERROR** `Duplicate entry '148-173' for key 'tb_feed_hashtags.uk_feed_hashtag'` / `STATUS_CODE_ERROR` |
| 567.942 | `connection` 이벤트 | **rollback** |
| 593.556 | `http post /feeds` 종료 | **status 500, outcome SERVER_ERROR** |

## 1. 원인 후보 랭킹 (최대 3개)

1. **동일 피드(feed_id=148)에 같은 해시태그(id=173)가 두 번 저장되어 `uk_feed_hashtag` 유니크 제약을 위반 → 트랜잭션 전체 롤백 → HTTP 500** (애플리케이션 레벨 데이터 결함)
2. **제약 위반에 대한 방어 로직 부재**: 해시태그 중복 제거(distinct)·멱등 삽입(존재 확인 후 스킵 / `ON DUPLICATE KEY`)이 없고, `DataIntegrityViolationException`을 4xx가 아닌 500으로 매핑 — 1번을 장애로 승격시킨 층위
3. **(낮음) 동시 요청/다중 파드 경쟁 조건에 의한 중복 삽입** — 반증 데이터가 강함

## 2. 후보별 근거

### 후보 1 — 요청 처리 중 해시태그 중복 → 유니크 제약 위반
- **근거**
  - 실패 span: `query`, `jdbc.query[0] = "insert into tb_feed_hashtags (created_at,feed_id,hashtag_id,updated_at) values (?,?,?,?)"`, `error = "Duplicate entry '148-173' for key 'tb_feed_hashtags.uk_feed_hashtag'"`, `status.code = STATUS_CODE_ERROR`. 이 트레이스에서 **에러 상태를 가진 유일한 span**입니다.
  - 중복의 두 주체가 모두 같은 트레이스 안에 있습니다: 453.5ms의 성공 insert(`generated-keys = 212`)와 479.1ms의 실패 insert. 키 `'148-173'`의 148은 425.5ms `generated-keys`의 feed_id, 173은 447.3ms `generated-keys`의 hashtag id로 **둘 다 이 요청이 방금 만든 값**입니다.
  - 중복이 발생한 경로도 데이터에 남아 있습니다: 첫 조회 `select ... from tb_hashtags where name=?` → `result-set row-count = 0` (신규 생성), 두 번째 조회 → `result-set row-count = 1` (직전에 만든 173을 재사용) → 같은 (148,173) 재삽입.
  - 인과 종결: 실패 45ms 뒤 `connection` span의 `rollback` 이벤트(567.942ms) → 서버 span `status = "500"`, `outcome = "SERVER_ERROR"`, `uri = /feeds`, `http.url = /api/feeds`. 제보된 "피드 등록 실패"와 정확히 일치합니다.
- **확신도: 높음** (실패 사실·에러 원문·롤백·500이 단일 트레이스 안에서 인과로 이어짐)
- **반증 데이터**
  - 서버 span에 `exception = "none"` 속성이 있습니다. 다만 이는 Micrometer가 예외를 핸들러가 처리했을 때 기록하는 값이며, 같은 span의 `status=500`/`outcome=SERVER_ERROR`와 모순되지 않습니다 — 오히려 후보 2(전역 예외 핸들러가 500으로 매핑)를 뒷받침합니다.
  - Loki에서 해당 시간창 **ERROR/WARN 0건, traceId 일치 로그 0건**(`totalEntriesReturned: 0`). 통상 `DataIntegrityViolationException`이면 스택트레이스가 남아야 하므로 형식상 반증이지만, 정상 로그까지 0건이라 **로그 수집 파이프라인 공백**으로 보는 편이 자연스럽습니다. 이 때문에 "왜 중복이 들어왔는가"(요청 본문의 중복 태그인지, 파싱 로직의 중복 생성인지)는 확정할 수 없습니다 — **데이터 부족**.

### 후보 2 — 중복 제거/멱등 처리 및 예외 매핑 부재
- **근거**
  - 애플리케이션이 이미 조회로 해시태그 존재를 확인(`select ... where name=?` 2회)하면서도, 같은 요청 내에서 이미 연결한 (feed,hashtag) 쌍을 걸러내지 못하고 그대로 insert를 시도했습니다(479.1ms span).
  - 464.4ms의 `update tb_hashtags set updated_at=?,usage_count=? where id=?` (row-affected 1)까지 정상 수행된 뒤 실패 — 즉 실패 시점 이전에 이미 쓰기 작업(feed, 첨부 2건, hashtag, feed_hashtag, usage_count)이 누적되어 있었고, 전부 `rollback` 대상이 되었습니다. 부분 성공을 살리는 처리(saveAll 전 dedup, 개별 예외 흡수)가 없습니다.
  - `exception = "none"` + `status = "500"` 조합은 예외가 전역 핸들러에 잡혀 5xx로 매핑되었음을 시사합니다. 사용자 입력 중복은 의미상 409/400 또는 무시 처리 대상입니다.
- **확신도: 중간** (트레이스에서 관측되는 동작으로부터의 추론이며, 실제 코드/예외 핸들러는 확인하지 못함)
- **반증 데이터: 없음**

### 후보 3 — 동시 요청/경쟁 조건
- **근거**: 유니크 제약 위반은 일반적으로 동시 중복 제출에서도 발생합니다.
- **확신도: 낮음**
- **반증 데이터**
  - 중복 쌍 (148,173)의 성공 insert와 실패 insert가 **동일 traceId·동일 `connection` span(parentSpanId `jK4Y+yvEilI=`)의 자식**입니다. 단일 요청·단일 트랜잭션 내부 중복이므로 요청 간 경쟁이 아닙니다.
  - feed_id 148은 이 요청이 생성한 값이라 다른 요청이 같은 feed_id에 태그를 달 수 없습니다.
  - 다른 content-service 파드(`content-service-6c5fff897-scw7k`, 10.42.3.41)의 `hikaricp_connections_active`가 창 전체에서 **0** — 동시 DB 작업 흔적이 없습니다.

### 데이터상 근거가 없어 후보에서 제외한 것 (명시)
- **Kafka / chat 알림 경로**: 이 트레이스에 Kafka producer span이나 chat-service span이 **하나도 없습니다**. 실패는 DB 롤백(567.9ms)으로 종결되며, 이벤트 발행은 그 이전에 도달하지 못한 것으로 보입니다. 다만 발행 지점이 계측되어 있는지 자체를 확인할 수 없어, "발행을 시도했다가 실패했는지"는 **데이터 부족**. `kafka_consumer_fetch_manager_records_lag` 시리즈도 미확보라 컨슈머 상태는 판단 불가입니다. 어느 쪽이든 피드 등록 500의 원인은 아닙니다.
- **auth-service / 인증**: `security filterchain before` span에서 `JwtAuthenticationFilter` 포함 12/12 필터를 모두 통과했고, 인증 실패 없이 비즈니스 로직까지 진입했습니다. auth-service 호출 span도 없습니다.
- **DB 커넥션 풀 고갈**: `connection` span 시작 405.602ms → `acquired` 407.297ms로 **대기 1.7ms**. `hikaricp_connections_pending`은 전 파드·전 구간 **0**, `active`는 최대 **1**입니다. (주: `active=1`이 1904~1949 4개 스텝에 걸쳐 보이는 것은 스크레이프 간격이 쿼리 step 15s보다 길어 같은 샘플이 반복된 것으로 보입니다 — 45초간 점유가 아닙니다. 반대로 이 낮은 해상도 때문에 짧은 스파이크는 놓칠 수 있으나, 트레이스의 획득 시간이 이를 직접 반증합니다.)
- **GC/메모리**: content-service 두 파드 모두 `rate(jvm_gc_pause_seconds_sum[5m]) = 0`. 지연 요인 아님.
- **한 가지 미해명 관측**: 실패한 insert span만 **66ms**(479.1→545.1)로 다른 쿼리(~2ms)보다 깁니다. 예외 생성·언랩 비용으로 설명 가능한 범위이며 락 대기(초 단위)로 보기는 어렵습니다. 원인 확정은 **데이터 부족**이나, 500의 원인과는 무관합니다.

## 3. 권장 다음 조치

**즉시 확인 (데이터 정합성)**
1. 롤백이 완전했는지 검증 — 고아 데이터가 남았다면 별도 이슈입니다.
   ```sql
   SELECT * FROM tb_feed WHERE id = 148;
   SELECT * FROM tb_feed_attachment_file WHERE attch_file_id IN (225, 226);
   SELECT * FROM tb_hashtags WHERE id = 173;      -- usage_count 증가분 포함 확인
   SELECT * FROM tb_feed_hashtags WHERE id = 212 OR (feed_id = 148 AND hashtag_id = 173);
   ```
2. 영향 범위 집계 — 이 트레이스가 단발인지 패턴인지 확인:
   TraceQL `{ resource.service.name = "content-service" && span.error =~ ".*uk_feed_hashtag.*" }` 를 최근 24시간으로, 그리고 `{ span.uri = "/feeds" && span.status = "500" }` 로 500 발생률을 확인.

**코드 수정 (근본 원인)**

3. 피드 생성 서비스의 해시태그 처리 경로에서 **태그 목록을 저장 전에 정규화 후 distinct** 적용(대소문자·공백·`#` 제거 기준 통일). 트레이스상 첫 조회는 미스, 두 번째 조회는 히트였으므로 같은 이름이 두 번 순회된 것이 확실합니다.
4. `tb_feed_hashtags` 삽입을 **멱등하게** 변경 — (feed_id, hashtag_id) 기존 존재 여부 확인 후 스킵, 또는 `INSERT ... ON DUPLICATE KEY UPDATE` / `INSERT IGNORE`.
5. `DataIntegrityViolationException` 매핑 재검토 — 사용자 입력 중복으로 인한 제약 위반은 **500이 아니라 409/400**, 또는 dedup 후 정상 처리. 현재는 첨부파일까지 포함한 전체 트랜잭션이 롤백되어 사용자 작업이 통째로 유실됩니다.
6. 재현 테스트: 동일 해시태그를 2개 포함한 `POST /api/feeds` 요청으로 재현 → 수정 후 회귀 테스트로 고정.

**관측성 공백 복구 (이번 조사를 실제로 방해한 항목)**

7. **Loki에 content-service 로그가 시간창 내 0건**입니다(ERROR/WARN, traceId 매칭 모두). Alloy/Promtail의 content-service 파드 로그 수집 대상 설정과, MDC `trace_id` 라벨이 구조화 로그에 실려 나가는지 확인하십시오. 로그가 있었다면 어떤 요청 필드에서 중복 태그가 들어왔는지 즉시 특정 가능했습니다.
8. `kafka_consumer_fetch_manager_records_lag` 시리즈 미노출 — chat-service의 Kafka 컨슈머 메트릭 바인딩(`micrometer` KafkaClientMetrics) 등록 여부를 확인하십시오. 이번 건과는 무관하지만, content→Kafka→chat 경로 장애 시 진단이 불가능한 상태입니다.
9. JDBC 계측에 **바인드 파라미터 미기록** — 어떤 해시태그 이름이 중복이었는지 확인할 수 없었습니다. 운영 환경에서 PII 위험을 감안하되, 최소한 예외 발생 시 요청 컨텍스트(태그 목록 크기·중복 여부)를 로그에 남기는 것을 권합니다.
