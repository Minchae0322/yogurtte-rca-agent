# RCA Report — `6a65c38bea0e08d50df7b169594a2844`

| 항목 | 값 |
|---|---|
| 모드 | rca |
| 질문 | 왜 알림이 안 왔어? |
| 시각 | 2026-07-26T08:27:30.588759Z |
| provider | claude-cli |
| prompt | `./prompts/system-prompt.md` |
| tokens | in 47503 / out 6756 · cost $1.0200 |
| elapsed | total 101766ms (tempo 490 · loki 180 · mimir 269 · assemble 0 · llm 100822) |

## 수집 범위 (Coverage)

- **window**: 2026-07-26T08:19:31.366694Z ~ 2026-07-26T08:25:35.398590Z (364s)
- **trace**: 31,044B / 28 spans
- **logs**: errwarn=3,956B · traceId=3,956B
- **metrics**: 3 수집, 누락 [kafka_consumer_fetch_manager_records_lag]
- **context**: 49,404 chars (~12,351 tok 추정)

## 수집 실패/누락

- Metric 'kafka_consumer_fetch_manager_records_lag' returned no series in this window; skipped.

---

## 1. 원인 후보 랭킹 (최대 3개)

1. **MongoDB 인스턴스(172.31.46.124:27017) 다운 — mongod 프로세스가 리스닝하지 않아 Connection refused, chat-service가 알림 저장/발송 처리에 4회 실패 후 메시지를 DLQ로 보내 알림 유실**
2. **네트워크 정책/방화벽이 27017 포트만 차단 (REJECT)** — 1번과 증상은 동일하나 원인 계층이 다름
3. **chat-service의 MongoDB 접속 설정 오류 (잘못된 엔드포인트/포트)** — 데이터 부족으로 배제 불가한 수준

Kafka 자체 장애는 후보에서 제외한다. 발행(`publish user.notifications`, 08:21:31.410Z, 14ms 성공)과 소비(offset 910, partition 3을 4회 정상 수신) 모두 트레이스에 남아 있어 파이프라인 전달은 정상이었다.

## 2. 후보별 근거

### 후보 1: MongoDB 다운 (mongod 프로세스 미가동/미리스닝)

- **근거:**
  - chat-service의 `receive`(user.notifications, offset 910, partition 3) 및 `user-notification-service#process-notification` span 4개 모두 `STATUS_CODE_ERROR`이며 error 속성 원문:
    > `Timed out while waiting for a server that matches WritableServerSelector. Client view of cluster state is {type=UNKNOWN, servers=[{address=172.31.46.124:27017, type=UNKNOWN, state=CONNECTING, exception={com.mongodb.MongoSocketOpenException: Exception opening socket}, ... caused by {java.net.ConnectException: Connection refused}}]`
  - `Connection refused`는 타임아웃이 아니라 대상 포트가 닫혀 있어 즉시 거절(RST)됐다는 뜻 → 호스트는 살아 있고 27017에서 리스닝하는 프로세스가 없음을 시사.
  - 타임라인: 소비 시도 4회가 각각 정확히 **약 30.0초** 소요(08:21:31.425→08:22:01.498, 08:22:02.646→08:22:32.663, 08:22:33.667→08:23:03.681, 08:23:04.687→08:23:34.699). 30초는 MongoDB 드라이버 기본 `serverSelectionTimeoutMS=30000`과 일치 — 매 시도마다 서버 선택에 끝까지 실패했다는 의미.
  - 4회 실패 직후 `publish user.notifications.dlq` span(08:23:34.711→08:23:35.398)이 존재 → 재시도 소진 후 DLQ로 이동, **알림은 지연이 아니라 미발송(유실) 상태**.
  - 각 시도마다 JDBC `connection` span의 이벤트가 `acquired` → `rollback`으로 끝남 → Mongo 실패로 처리 트랜잭션 전체가 롤백됨.
  - 같은 호스트 172.31.46.124의 Redis(6379)는 content-service의 `GET` span(08:21:31.381, 0.5ms, 성공)으로 정상 응답 → **호스트/네트워크 전체 장애가 아니라 27017 포트(mongod)만의 문제**임을 뒷받침.
- **확신도: 높음** — 단, Loki 로그가 0건(ERROR/WARN, traceId 매칭 모두 빈 결과)이고 `kafka_consumer_fetch_manager_records_lag` 메트릭도 수집 실패라 애플리케이션 로그 차원의 교차 검증은 못 했다. 트레이스의 예외 원문이 4회 반복으로 일관되어 이 공백을 감안해도 높음을 유지한다.
- **반증 데이터: 없음.**

### 후보 2: 27017 포트에 대한 네트워크 차단 (Security Group / NetworkPolicy의 REJECT)

- **근거:** 관측된 증상(`ConnectException: Connection refused`)은 방화벽이 DROP이 아닌 REJECT로 응답할 때도 동일하게 나타난다. 트레이스만으로는 "프로세스 부재"와 "REJECT 규칙"을 구분할 수 없다.
- **확신도: 낮음**
- **반증 데이터:** 동일 호스트 172.31.46.124의 6379(Redis)는 클러스터 내 파드(content-service, 10.42.1.27)에서 정상 접근됨. 포트 단위 차단이 아니고서는 성립하지 않으며, 그런 규칙 변경이 있었다는 근거는 데이터에 없다. 또한 일반적인 SG/DROP 차단이라면 refused가 아닌 connect timeout으로 나타났을 가능성이 높다.

### 후보 3: chat-service의 MongoDB 접속 설정 오류

- **근거:** 드라이버가 바라보는 주소가 172.31.46.124:27017 단일 노드라는 사실만 트레이스에 있고, 이 주소가 "현재 올바른" MongoDB 엔드포인트인지 판단할 데이터가 없다(Mongo 서버 측 메트릭/로그 부재). Mongo가 다른 주소로 이전/재배포되었는데 chat 설정이 구버전일 가능성을 배제할 수 없다. **데이터 부족** — Mongo 서버 자체의 가동 상태 데이터(프로세스 상태, mongod 로그, 서버 측 메트릭)가 필요하다.
- **확신도: 낮음**
- **반증 데이터: 없음** (구분에 필요한 데이터 자체가 없음).

## 3. 권장 다음 조치

**즉시 확인 (원인 확정)**
1. 172.31.46.124 호스트 접속 후 mongod 상태 확인: `systemctl status mongod`(또는 컨테이너면 `docker ps`/`kubectl get pods`), `ss -lntp | grep 27017`, mongod 로그에서 08:21 이전의 종료/크래시/OOM 흔적 확인.
2. mongod가 떠 있다면 `bindIp` 설정과 호스트 방화벽/SG의 27017 인바운드 규칙 확인 (후보 2·3 판별).
3. Tempo에서 같은 시간대 chat-service의 다른 에러 트레이스를 조회해 장애 시작 시점(첫 Mongo 실패 발생 시각)과 영향 범위를 특정.

**복구 및 유실 데이터 처리**
4. mongod 복구 후 `user.notifications.dlq` 토픽에 쌓인 메시지(이 건은 partition 3, offset 910에서 유래)를 재처리해 미발송 알림을 발송. DLQ 재처리 컨슈머가 없다면 수동 재발행 필요.
5. DLQ 적체량을 확인해 이 트레이스 외 유실 건수를 산정.

**관측성 공백 보수 (이번 조사에서 확인된 결함)**
6. Loki에 chat-service ERROR 로그가 한 줄도 없음 — Mongo 예외가 4회 발생했는데 로그 0건은 수집 파이프라인(Alloy) 문제이거나 로그 레이블 불일치. 로그 수집 경로 점검.
7. `kafka_consumer_fetch_manager_records_lag` 메트릭 시리즈 부재 — 컨슈머 lag 계측 활성화.
8. chat-service의 Hikari 메트릭 시리즈가 08:21:46(1785054106) 이후 끊김(다른 서비스는 08:25:31까지 존재) — chat-service 파드의 메트릭 스크레이프 상태 점검.
9. 재발 대비 알림 추가: DLQ publish rate > 0 알람, MongoDB 27017 헬스체크(blackbox exporter 등) 알람.
