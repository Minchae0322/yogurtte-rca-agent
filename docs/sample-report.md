# RCA Report — `6a5dc9c1990469248cfea377e1d7b4a0`

| 항목 | 값 |
|---|---|
| 질문 | 이 요청에서 병목이나 이상 있어? |
| 시각 | 2026-07-22T01:43:45.280958Z |
| provider | claude-cli |
| prompt | `./prompts/system-prompt.md` |
| tokens | in 42651 / out 4950 · cost $0.4234 |
| elapsed | total 79749ms (tempo 1146 · loki 261 · mimir 355 · assemble 1 · llm 77969) |

## 수집 범위 (Coverage)

- **window**: 2026-07-20T07:07:53.235755Z ~ 2026-07-20T07:11:54.499548Z (241s)
- **trace**: 24,619B / 30 spans
- **logs**: errwarn=3,912B · traceId=3,913B
- **metrics**: 3 수집, 누락 [kafka_consumer_fetch_manager_records_lag]
- **context**: 40,981 chars (~10,245 tok 추정)

## 수집 실패/누락

- Metric 'kafka_consumer_fetch_manager_records_lag' returned no series in this window; skipped.

---

## 1. 원인 후보 랭킹 (최대 3개)

1. **chat-service `PushDispatcher.dispatch` 내부의 계측되지 않은 ~995ms 지연** (푸시 발송 구간이 실질 병목)
2. **알림 처리 전 구간(~1.1s) 동안 JDBC 커넥션을 붙잡은 채 외부 푸시 대기** (커넥션 점유 안티패턴 → 부하 시 풀 고갈 위험)
3. **chat-service의 Redis `KEYS` 명령 사용** (O(N) 블로킹 안티패턴, 이번 트레이스에선 잠재 리스크 수준)

---

## 2. 후보별 근거

### 후보 1 — PushDispatcher.dispatch 병목
- **근거:**
  - 사용자 요청 자체(`http post /feeds/{feedId}/comments`, content-service)는 **129.78ms에 200 SUCCESS**로 정상 종료. 병목은 이 동기 응답이 아니라 Kafka 이후 비동기 알림 파이프라인에 있음.
  - Kafka 구간은 건전함: producer `publish user.notifications` 22.57ms, 발행 시각(…363541000)과 chat 소비 `receive` 시작(…391896000) 간 격차 **~28ms**로 lag 징후 없음.
  - chat-service `receive`(CONSUMER) span: 시작 …391896000 → 종료 …499548000 = **약 1.108초**. 그 하위 `user-notification-service#process-notification` = **약 1.085초**.
  - 그중 `push-dispatcher#dispatch`(`com.example.toychat.app.notification.service.PushDispatcher.dispatch`) = 시작 …498749000 → 종료 …494880000(다음 초) = **약 996ms**.
  - dispatch 내부에 존재하는 유일한 자식 span인 Redis `KEYS 0`은 …499425000 → …500310000 = **0.885ms**에 불과. 즉 dispatch 996ms 중 **약 995ms가 자식 span·로그 없이 비어 있음** → 계측되지 않은 외부 푸시 전송(FCM/APNs 등) 또는 블로킹 대기로 추정.
- **확신도:** 병목 **위치**는 **높음**(span 타임스탬프상 명백). 병목 **원인 규명**은 **낮음~중간**(해당 구간을 설명할 자식 span·로그가 전무).
- **반증 데이터:** 없음. (다만 계측 공백 자체가 원인 특정을 막음.)

### 후보 2 — 처리 전 구간 커넥션 점유
- **근거:**
  - chat-service `connection`(HikariPool-1, datasource `content`) span: 시작 …393766000, `acquired` 이벤트 …395705000, `commit` 이벤트 **…494497117000 → 종료 …499022000**. 즉 커넥션을 **획득 직후부터 ~1.1초간 붙잡은 채**, 그 안에서 DB 작업이 아닌 ~996ms 푸시 dispatch가 실행됨. 외부 호출을 커넥션 홀딩 상태에서 수행하는 구조.
- **확신도:** 중간. (점유 사실은 span으로 확인되나, 이번 트레이스가 단건이라 실제 풀 고갈로 이어졌다는 증거는 없음.)
- **반증 데이터:** `hikaricp_connections_active`·`hikaricp_connections_pending` 모두 전 구간 **0**, chat-service GC pause도 정상 범위(~0.0006~0.001). 즉 이번 시점엔 풀 압박·자원 포화 신호 없음. (단 메트릭 scrape 간격이 15초라 1초짜리 스파이크는 원천적으로 포착 불가.)

### 후보 3 — Redis `KEYS` 사용
- **근거:** chat-service가 `db.operation=KEYS`, `db.namespace=0`을 두 번 호출(dispatch 내 …499425000, web-socket-sender 내 …443459000). `KEYS`는 키 전수 스캔 O(N) 블로킹 명령으로 알려진 안티패턴. `server.address`에 `timeout=2s`도 확인됨.
- **확신도:** 낮음.
- **반증 데이터:** 두 `KEYS` 호출 모두 **0.74~0.89ms**로 매우 빠름 → 이번 요청의 병목이 아님. 키가 적을 때만 빠른 것이므로 규모 확대 시 리스크로만 기록.

---

## 3. 권장 다음 조치
1. **`PushDispatcher.dispatch` 내부 계측 추가**: 외부 푸시 클라이언트(FCM/APNs 등) 호출·재시도·타임아웃을 별도 span으로 분리. 995ms의 정체가 (a)외부 API 응답 지연인지 (b)블로킹 대기/재시도인지부터 확정.
2. **동일 소비자·시간대 로그 재수집**: 이번 조회에서 ERROR/WARN·traceId 매칭 로그가 **0건**이었음(수집 실패가 아니라 실제 공백일 수 있음). `notification-processors` 컨슈머(`consumer-notification-processors-13`, partition 3, offset 902) 기준으로 INFO 레벨 포함 재조회하여 dispatch 지연의 로그 흔적 확인.
3. **Kafka consumer lag 재확인**: `kafka_consumer_fetch_manager_records_lag`가 이 창에서 **no series로 누락**됨. 지표 노출 여부(메트릭 이름/라벨) 점검 후 `notification-processors` 그룹의 lag를 재수집 — 단건 push 지연이 누적 lag로 번지는지 판단 필요.
4. **커넥션 홀딩 구조 검토**: 외부 푸시 발송을 DB 트랜잭션/커넥션 밖으로 분리(획득 지연 또는 커밋 선행). 부하 테스트에서 `hikaricp_connections_pending` 상승 여부로 위험도 검증.
5. **`KEYS` → `SCAN` 전환 검토**: 키 증가 대비 잠재 병목 제거(현 시점 영향은 미미).

**종합 판단:** 사용자 응답(130ms)은 정상이며, 병목은 **chat-service 비동기 알림 처리 내 push dispatch 구간(~1초)**에 명확히 집중됨. 다만 해당 구간을 설명할 자식 span·로그가 없고, kafka lag 메트릭이 누락되어 **"왜 느린가"의 확신도는 낮음** — 위 1·2·3 수집으로 보강 필요.
