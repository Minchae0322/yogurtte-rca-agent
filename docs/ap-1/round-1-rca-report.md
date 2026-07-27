# RCA Report — `6a67678730d34d2319861c21cf6a84b4`

| 항목 | 값 |
|---|---|
| 모드 | rca |
| 질문 | 댓글 작성이 실패했다는 제보가 있다. 원인을 조사해줘 |
| 시각 | 2026-07-27T14:17:33.032440Z |
| provider | claude-cli |
| prompt | `./prompts/system-prompt.md` |
| tokens | in 34935 / out 2719 · cost $0.2849 |
| elapsed | total 51165ms (tempo 592 · loki 308 · mimir 319 · assemble 0 · llm 49935) |

## 수집 범위 (Coverage)

- **window**: 2026-07-27T14:11:27.020857Z ~ 2026-07-27T14:15:27.258923Z (240s)
- **trace**: 8,761B / 9 spans
- **logs**: errwarn=3,960B · traceId=3,956B
- **metrics**: 3 수집, 누락 [kafka_consumer_fetch_manager_records_lag]
- **context**: 26,205 chars (~6,551 tok 추정)

## 수집 실패/누락

- Metric 'kafka_consumer_fetch_manager_records_lag' returned no series in this window; skipped.

---

## 1. 원인 후보 랭킹 (최대 3개)

1. **댓글 본문(content)이 DB 컬럼 최대 길이를 초과 → INSERT 시 데이터 절단 오류로 트랜잭션 롤백, HTTP 500 반환**
2. (데이터 부족) content-service 애플리케이션 레벨의 입력 검증 부재 — 근거 간접적
3. 그 외 인프라/의존성(DB 커넥션 풀 고갈, GC, Kafka, auth) 원인 — **반증됨**

---

## 2. 후보별 근거

### 후보 1 — content 컬럼 데이터 절단(Data too long)

- **근거:**
  - 루트 span `http post /feeds/{feedId}/comments` (SPAN_KIND_SERVER)에서 `outcome=SERVER_ERROR`, `status=500`, `http.url=/api/feeds/145/comments`. 요청 소요 약 238ms (start 1785161607020857000 → end 1785161607258923000).
  - 자식 `query` span (spanId `+a3pPwvEgKM=`)에서 상태 `STATUS_CODE_ERROR`, 오류 원문:
    - `error = "Data truncation: Data too long for column 'content' at row 1"`
    - `jdbc.query[0] = "insert into tb_feed_comment (content,created_at,creator_id,...) values (?,?,...)"`
    - `peer.service = content`, `jdbc.datasource.name = content`, `HikariPool-1`
  - 부모 `connection` span (spanId `K+/3Ago+HUc=`)의 events에 `acquired`(…097069000) 이후 **`rollback`(…237187000)** — INSERT 실패로 트랜잭션이 정상 롤백됨. 즉 댓글이 저장되지 않고 실패 응답.
  - 인과 체인이 단일 트레이스 내에서 완결: feed 조회(select tb_feed) → comment insert 실패(data truncation) → rollback → 500.
- **확신도: 높음** (단, 아래 수집 실패로 소폭 감점 — 애플리케이션 스택트레이스/입력값 원문 로그 미확보)
- **반증 데이터:** 없음. (루트 span의 `exception=none` 속성이 있으나, 자식 query span이 `STATUS_CODE_ERROR` + 명시적 error 메시지를 가지므로 실질 오류는 확정. `exception=none`은 Micrometer가 최상위에서 예외를 잡지 못했음을 나타낼 뿐 반증이 되지 않음.)

### 후보 2 — 애플리케이션 계층 입력 길이 검증 부재 (파생 가설)

- **근거:** 후보 1이 성립한다는 것은, 과도하게 긴 본문이 애플리케이션 검증을 통과해 DB까지 도달했음을 의미한다. 즉 `@Size`/컬럼 제약과 DB 스키마 불일치 또는 검증 누락이 있을 수 있다. 단, 이를 직접 뒷받침하는 코드/설정/검증 로그는 **이 데이터에 없음**.
- **확신도: 낮음** (데이터 부족 — 정황 추론)
- **반증 데이터:** 없음.

### 후보 3 — 인프라/의존성 원인 (커넥션 풀·GC·Kafka·auth)

- **근거(반증 목적):**
  - `hikaricp_connections_active`, `hikaricp_connections_pending`: content-service(및 auth/chat) 전 구간 **0** → 커넥션 풀 고갈 아님. 커넥션도 `acquired` event로 정상 획득됨.
  - `jvm_gc_pause`: content-service Copy GC rate 최대 ~0.00005s/s 수준, 무시 가능 → GC 원인 아님.
  - 보안 필터체인 span(before/after 12/12 정상 통과), JwtAuthenticationFilter 정상 → **auth/인증 실패 아님**.
  - 이 장애는 **댓글 작성(content → DB)** 경로에서 발생하며 발행 이전 DB INSERT에서 이미 실패 → Kafka/chat(알림 발송) 경로는 도달조차 안 함. Kafka·chat은 이번 실패와 무관.
  - `kafka_consumer_fetch_manager_records_lag`는 수집 실패했으나, 실패 지점이 그 이전(DB)이므로 결론에 영향 없음.
- **확신도: 높음(배제)**
- **반증 데이터:** 없음 (모든 인프라 지표가 정상이므로 원인 후보에서 제외).

---

## 3. 권장 다음 조치

1. **DB 스키마 확인:** `tb_feed_comment.content` 컬럼 타입/길이 확인 (예: `VARCHAR(255)` vs `TEXT`). 서비스 요구 최대 길이와 비교.
2. **실패 입력 재현:** `/api/feeds/145/comments` 요청 본문 길이 확인. 사용자가 붙여넣은 장문/이모지(멀티바이트)로 인해 바이트 길이가 컬럼 한도를 초과했는지 점검 (UTF-8 3~4바이트 문자 주의).
3. **애플리케이션 검증 추가:** 컨트롤러/DTO에 `@Size(max=...)` 등 입력 길이 검증을 넣어 DB 도달 전 400으로 반환하고, 500 대신 명확한 클라이언트 오류 메시지 제공.
4. **스키마-검증 정합:** 컬럼 확장(예: `TEXT`/길이 상향)이 제품 요구에 맞는지 결정 후 마이그레이션 or 검증 상한 통일.
5. **로그 파이프라인 점검:** Loki에서 이 traceId 및 ERROR/WARN 로그가 **0건**이었다. content-service 예외 로그가 Loki로 수집되는지(로그 레벨/포맷/trace_id 라벨링) 확인 — 향후 동일 장애의 근거 확보를 위해 필요.
6. (선택) 동일 오류 빈도 파악: `tb_feed_comment` INSERT의 data truncation 오류가 다른 traceId에서도 발생하는지 집계해 산발적 사용자 입력 문제인지 광범위 스키마 문제인지 구분.

---

**요약:** 관측 데이터상 근본 원인은 **댓글 본문이 `tb_feed_comment.content` 컬럼 한도를 초과하여 발생한 MySQL "Data too long" 절단 오류**이며, 이로 인해 트랜잭션이 롤백되고 500이 반환되었다. 인증·커넥션 풀·GC·Kafka/chat 경로는 지표상 정상으로 배제된다. 확신도는 **높음**이나, 애플리케이션 예외 로그(Loki 0건)와 실제 입력값 원문을 확보하지 못한 공백이 있어 재현·스키마 확인으로 최종 확정할 것을 권한다.
