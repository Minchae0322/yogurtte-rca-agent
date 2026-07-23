# RCA Report — `6a5dc9c1990469248cfea377e1d7b4a0`

| 항목 | 값 |
|---|---|
| 모드 | review |
| 질문 | 이 요청의 성능과 구조를 리뷰해줘 |
| 시각 | 2026-07-23T15:01:14.217712Z |
| provider | claude-cli |
| prompt | `./prompts/review-prompt.md` |
| tokens | in 44607 / out 12100 · cost $1.2272 |
| elapsed | total 181454ms (tempo 2705 · loki 501 · mimir 674 · assemble 1 · llm 177555) |

## 수집 범위 (Coverage)

- **window**: 2026-07-20T07:07:53.235755Z ~ 2026-07-20T07:11:54.499548Z (241s)
- **trace**: 24,619B / 30 spans
- **logs**: errwarn=3,943B · traceId=3,959B
- **metrics**: 3 수집, 누락 [kafka_consumer_fetch_manager_records_lag]
- **context**: 41,058 chars (~10,264 tok 추정)

## 수집 실패/누락

- Metric 'kafka_consumer_fetch_manager_records_lag' returned no series in this window; skipped.

---

## 1. 개선 포인트 랭킹 (심각도 순)

1. **[높음] chat 컨슈머가 DB 커넥션·트랜잭션을 잡은 채 ~1초짜리 push dispatch를 수행 (996ms 미계측)**
2. **[높음] chat 서비스의 Redis `KEYS` 명령 사용 (한 메시지 처리에 2회)**
3. **[중간] content 댓글 트랜잭션의 미계측 갭 ~85ms — 커넥션 점유 116ms 중 실제 DB 작업은 ~30ms**
4. **[중간] 댓글 저장 + 경험치/보상 로직이 단일 트랜잭션에 결합, `FOR UPDATE` 락 44ms + hot-row 카운터 갱신**
5. **[낮음] chat 서비스 JDBC datasource 이름이 `content` — 서비스 경계 침범 의심 (가설)**

---

## 2. 포인트별 상세

### 1. chat 컨슈머의 커넥션 점유 + 996ms 미계측 dispatch — 높음

- **근거**: chat-service의 `connection` span(HikariPool-1)이 393.766ms → 1499.022ms로 **약 1,105ms 점유**되며, 이벤트가 `acquired`(395.7ms) → `commit`(1497.1ms)이다. 그 안의 `push-dispatcher#dispatch`(PushDispatcher.dispatch)가 498.7ms → 1494.9ms로 **996ms**를 차지하는데, 계측된 자식은 `KEYS 0` 단 하나(0.9ms)뿐이다. 이 connection span 아래에 **JDBC query span이 하나도 없다** — 쿼리 없이 커넥션과 트랜잭션만 1.1초 열어둔 채 무언가(외부 푸시 호출로 추정)를 기다린 것이다. Kafka `receive` span 전체(1,107.7ms) 중 push dispatch가 90%를 차지한다.
- **심각도: 높음** — 이 트레이스의 end-to-end 지연(HTTP 시작 → 푸시 완료 ≈ 1.26초) 대부분을 차지하고, 구조적 위험이 크다. Kafka는 파티션당 직렬 소비이므로 메시지당 1.1초면 파티션당 처리량이 초당 ~1건으로 제한되고, 트래픽이 늘면 lag이 즉시 쌓인다. 동시에 커넥션 1.1초 점유는 부하 시 Hikari 풀 고갈로 직결된다. 참고로 `hikaricp_connections_active`가 0으로 보이지만 스크레이프 간격이 15초라 1.1초 점유는 샘플 사이에 끝난다 — 이 메트릭이 안전의 근거가 되지 못한다.
- **개선 방향**: ① 트랜잭션/커넥션 범위에서 외부 발송을 분리한다 — DB 작업(있다면)을 먼저 커밋하고 push는 커밋 후 실행하거나, outbox 패턴으로 별도 워커에 넘긴다. `@Transactional`이 리스너 메서드 전체에 걸려 있는지 확인하는 것이 첫 단계다. ② push 호출(HTTP/gRPC) 구간에 span 계측을 추가해 996ms의 정체를 밝힌다.
- **전제/반증**: 996ms의 정체는 **가설**이다(계측 공백). FCM/APNs 같은 외부 호출일 수도, 재시도·타임아웃 대기일 수도 있다. 확인 방법: PushDispatcher 코드 확인 + HTTP 클라이언트 계측 추가. 또한 쿼리 span이 없는 것이 "실제로 쿼리가 없음"인지 "계측 누락"인지도 확인 필요 — 전자라면 애초에 트랜잭션이 왜 열리는지가 문제다. traceId 일치 로그가 0건이라 로그로 교차 검증할 수 없었던 점도 이 공백을 키운다.

### 2. Redis `KEYS` 사용 — 높음 (성장 리스크 기준)

- **근거**: chat-service에서 `KEYS 0` span이 2회 관측된다 — `user-notification-web-socket-sender#send-notification` 내부(443.5ms, 0.7ms)와 `push-dispatcher#dispatch` 내부(499.4ms, 0.9ms). `db.operation=KEYS`로 명시돼 있다.
- **심각도: 높음** — 현재 지연 기여는 1ms 미만으로 미미하지만, `KEYS`는 O(N) 싱글스레드 블로킹 명령이다. 키(예: WebSocket 세션) 수가 수만 건이 되면 Redis 전체가 멈추고, 이 Redis(172.31.46.124)는 content-service의 `GET`도 같이 쓰는 공유 인스턴스라 장애 반경이 두 서비스에 걸친다.
- **개선 방향**: ① 패턴 검색 자체를 없애도록 키 설계를 바꾼다 — 세션 목록을 Set/Hash로 인덱싱해 O(1) 조회로 전환. ② 당장 바꾸기 어렵다면 최소한 `SCAN`으로 대체. 부수적으로, 같은 메시지 처리에서 KEYS를 두 번(웹소켓 발송·푸시 발송 각각) 호출하고 있으니 조회 결과를 재사용하면 호출 자체가 1회로 준다.
- **전제/반증**: 현재 키 규모가 작고 앞으로도 작게 유지된다면 실측 위험은 낮다. `DBSIZE`와 키 증가 추이를 확인해 우선순위를 조정하라.

### 3. content 트랜잭션의 미계측 갭 ~85ms — 중간

- **근거**: `http post /feeds/{feedId}/comments` 전체가 129.8ms(235.8→365.5ms), 그중 `connection` span이 116.6ms(246.4→363.0ms, acquired 248.6 → commit 358.7)를 차지한다. 그런데 그 아래 계측된 작업(SELECT 4건, INSERT 2건, UPDATE 3건, Redis GET, result-set 등)의 합은 **약 30ms**뿐이다. 쿼리 사이사이에 267.2→278.0ms(10.8ms), 281.7→294.0ms(12.3ms), 297.3→314.7ms(17.4ms), 329.4→341.1ms(11.7ms) 같은 갭이 반복되어, 트랜잭션 내 ~85ms가 어떤 span으로도 설명되지 않는다.
- **심각도: 중간** — 요청 지연의 65%를 차지하지만 절대값(85ms)은 아직 작다. 다만 이 시간 내내 커넥션과 (아래 4번의) 행 락을 쥐고 있으므로, 동시성이 올라가면 락 유지 시간으로 증폭된다.
- **개선 방향**: ① 서비스/도메인 메서드 레벨 span(또는 JFR/프로파일링)을 추가해 갭의 정체를 확인한다. ② 갭이 애플리케이션 로직이라면 그 로직을 트랜잭션 바깥으로 빼서 커넥션 점유·락 유지 시간을 줄인다.
- **전제/반증**: **가설** — 갭은 JPA flush/dirty checking과 도메인 로직(경험치 계산 등)으로 추정되지만 계측이 없어 단정할 수 없다. GC는 원인이 아니다(content-service GC pause rate ~0.0001s/s로 미미). 프로파일링으로 확인하라.

### 4. 댓글 저장과 보상 로직의 단일 트랜잭션 결합 + 락 패턴 — 중간

- **근거**: 하나의 트랜잭션(248.6ms acquired → 358.7ms commit) 안에서 댓글 INSERT(`tb_feed_comment`) 외에 경험치 중복 체크(`tb_exp_history` select), 일일 캡 조회·갱신(`tb_daily_exp_cap`), **`select ... from tb_user_reward ... for update`**(314.7ms), 보상 이력 INSERT, `update tb_feed set comment_count=...`(341.1ms)까지 총 9개의 SQL이 실행된다. `tb_user_reward` 행 락은 314.7ms부터 커밋(358.7ms)까지 **~44ms**, `tb_feed` 행 락은 ~17.6ms 유지된다.
- **심각도: 중간** — 이 트레이스 단독으로는 문제가 없지만(전 쿼리 2~5ms), 인기 피드에 댓글이 몰리면 `tb_feed.comment_count` 갱신이 hot-row가 되어 댓글 쓰기가 직렬화되고, 한 사용자가 연속 행동하면 `tb_user_reward` FOR UPDATE에서 대기한다. 3번의 미계측 갭이 락 유지 시간을 그대로 늘리고 있다.
- **개선 방향**: ① 보상(경험치) 적립을 댓글 저장 트랜잭션에서 분리 — 커밋 후 이벤트로 비동기 처리(이미 알림에 쓰는 after-commit 이벤트 패턴을 재사용 가능). ② `comment_count`는 `update ... set comment_count = comment_count + 1` 형태의 원자 증가로 바꾸거나(현재는 읽어온 값으로 set), 락 경합이 실측되면 카운터를 분리한다. ③ FOR UPDATE 구간은 트랜잭션 후반부로 몰아 락 유지 시간을 최소화한다.
- **전제/반증**: 댓글과 보상의 강한 정합성(원자성)이 비즈니스 요구라면 분리는 부적절하다 — 보상 유실/중복 허용 여부를 먼저 확인하라. `comment_count=?`가 실제로 read-then-set인지는 코드 확인이 필요하다(**가설**, Hibernate dirty checking 특성상 그럴 가능성이 높음).

### 5. chat 서비스의 datasource 이름이 `content` — 낮음 (가설)

- **근거**: chat-service의 `connection` span 속성이 `jdbc.datasource.name=content`, `pool=HikariPool-1`이다.
- **심각도: 낮음** — 성능 문제는 아니지만, chat이 content의 DB를 직접 바라보는 것이라면 MSA 경계 침범(공유 DB 안티패턴)으로, 스키마 변경 결합·장애 전파의 구조적 리스크다.
- **개선 방향**: 설정 확인 후, 단순 네이밍 복사라면 이름만 바로잡고(관측 혼선 방지), 실제 공유 DB라면 chat 전용 스키마/DB 분리를 로드맵에 올린다.
- **전제/반증**: **가설** — `spring.datasource` 설정을 복사하면서 이름만 남았을 가능성이 충분하다. chat의 DB 접속 문자열을 확인하면 즉시 판별된다.

---

## 3. 정상적인 것들

1. **Kafka 발행 시점이 올바르다**: `notification-publish`(363.4ms)가 DB `commit`(358.7ms) **이후**에 실행된다 — after-commit 이벤트 패턴으로, 트랜잭션 안에서 브로커를 호출하는 흔한 실수를 피했다. producer send(363.5→386.1ms)도 서버 응답(365.5ms 종료)을 블로킹하지 않는 비동기 발행이다. (단, 응답 후 발행이 실패하면 알림이 유실될 수 있으니 acks/에러 핸들링은 한번 점검할 가치가 있다.)
2. **개별 SQL은 전부 건강하다**: 9개 쿼리 모두 2~5ms로, 인덱스 문제나 슬로우 쿼리 징후가 없다. N+1 패턴도 관측되지 않았다 — 반복되는 `query` span들은 각기 다른 테이블·목적이다.
3. **런타임 상태가 깨끗하다**: 시간창 내 ERROR/WARN 로그 0건, Hikari pending 0, GC pause rate는 chat ~1ms/s·content ~0.1ms/s 수준으로 무시 가능하다. Security filterchain(JWT 포함 12개 필터)도 1.2ms로 저렴하다.

**데이터 공백의 영향**: `kafka_consumer_fetch_manager_records_lag` 메트릭이 없어, 포인트 1의 "메시지당 1.1초"가 실제로 컨슈머 lag을 만들고 있는지, 이 트레이스가 평균적인지 이상치인지 판별할 수 없다. traceId 일치 로그도 0건이라 996ms 갭의 정체를 로그로 교차 검증하지 못했다. 두 공백 모두 포인트 1의 "원인" 규명을 가설 수준에 머물게 하지만, 커넥션 점유 시간과 span 구조 자체는 트레이스만으로 확정된 사실이다.
