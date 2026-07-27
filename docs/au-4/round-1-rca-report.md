# RCA Report — `6a676edbd07a4b966d03330e318ef61d`

| 항목 | 값 |
|---|---|
| 모드 | rca |
| 질문 | 피드에 작성자가 '사용자123' 같은 익명으로 보인다는 제보가 있어 |
| 시각 | 2026-07-27T14:50:19.136244Z |
| provider | claude-cli |
| prompt | `./prompts/system-prompt.md` |
| tokens | in 56325 / out 2980 · cost $0.5209 |
| elapsed | total 58178ms (tempo 683 · loki 192 · mimir 272 · assemble 0 · llm 57014) |

## 수집 범위 (Coverage)

- **window**: 2026-07-27T14:42:43.856936Z ~ 2026-07-27T14:46:44.031205Z (240s)
- **trace**: 50,180B / 66 spans
- **logs**: errwarn=3,957B · traceId=3,958B
- **metrics**: 3 수집, 누락 [kafka_consumer_fetch_manager_records_lag]
- **context**: 65,353 chars (~16,338 tok 추정)

## 수집 실패/누락

- Metric 'kafka_consumer_fetch_manager_records_lag' returned no series in this window; skipped.

---

## 1. 원인 후보 랭킹 (최대 3개)

1. **auth-service 연결 불가로 작성자 정보 조회 실패 → 익명 폴백 렌더링** (가장 유력)
2. content 서비스의 익명 폴백 로직 자체가 auth 실패 시 '사용자{id}' 형태로 이름을 대체 (1의 하위/연계 원인)
3. auth-service 미기동/스케일다운/포트 오구성 등 인프라 레벨 원인 (1의 근본 원인 후보, 데이터 부족)

---

## 2. 후보별 근거

### 후보 1. auth-service 연결 불가로 작성자 정보 조회 실패

- **근거:**
  - content-service span `http get` (spanId `Ho1rOnZLD6A=`, kind CLIENT)가 명시적 에러:
    - `status = "CLIENT_ERROR"`, `outcome = "UNKNOWN"`, `exception = "WebClientRequestException"`
    - `error = "finishConnect(..) failed: Connection refused: auth-service.default.svc.cluster.local/10.43.13.21:8081"`
    - `http.url = "http://auth-service:8081/api/external/users?userIds=3,7,9,56"`
    - span status `code = "STATUS_CODE_ERROR"`
  - 이 호출은 피드 목록 조회(`tb_feed`에서 11건, `jdbc.row-count=11`) 직후 작성자(user) 정보를 auth에서 벌크로 채우려는 호출이며, `userIds=3,7,9,56`으로 실제 작성자 ID를 넘김. **이 호출만 유일하게 실패**했고 나머지 DB/redis span은 모두 정상.
  - 즉 피드 본문·이미지·카테고리·해시태그·리워드(레벨)는 content DB에서 정상 조회됐으나, **작성자 표시명(닉네임/프로필)만 auth 의존이라 비어버림** → 제보된 "작성자가 익명으로 보임" 증상과 정확히 일치.
- **확신도:** 중간~높음
  - 트레이스 상 실패 지점과 증상의 인과가 매우 명확해 원래 "높음"이나, **Loki 로그가 ERROR/WARN·traceId 모두 0건 반환**하여 폴백 처리 로직의 로그 근거를 교차검증하지 못함. 규칙에 따라 한 단계 낮춤.
- **반증 데이터:** 최상위 span `http get /feeds/scroll`가 `status=200`, `outcome=SUCCESS`, `exception=none`로 종료됨. 이는 원인을 반증한다기보다 **auth 실패를 삼키고 익명으로 우회(graceful degradation)했음**을 뒷받침함. 그 외 배치되는 관측값 없음.

### 후보 2. content 서비스의 익명 폴백 로직이 '사용자{id}'로 대체

- **근거:**
  - 후보 1의 auth 호출이 4개 ID(`3,7,9,56`)에 대해 응답 0건인데도 요청 전체는 200으로 성공 종료 → 코드에 **auth 실패 시 예외를 던지지 않고 기본값으로 채우는 폴백 경로**가 존재함을 시사.
  - 제보의 표기 '사용자123'은 전형적인 `"사용자" + userId` 형태 기본 닉네임으로, 이 폴백 문자열 생성 위치가 실제 증상의 직접 원인.
- **확신도:** 낮음
  - 폴백 문자열을 생성하는 **소스 코드/로그가 관측 데이터에 없음.** 트레이스가 200으로 끝났다는 정황 추론일 뿐, `사용자123` 문자열의 출처를 직접 확인하지 못함.
- **반증 데이터:** 없음 (직접 확인·반증 모두 불가, 데이터 부족).

### 후보 3. auth-service 미기동/스케일다운/포트 오구성 등 근본 인프라 원인

- **근거:**
  - 에러가 타임아웃이 아니라 **"Connection refused"** → 대상 IP(10.43.13.21:8081, ClusterIP Service)까지는 라우팅되나 **백엔드 Pod가 없거나 8081 리슨 미개방**. Endpoints 부재(Pod 0개/CrashLoop/Readiness fail) 또는 포트 불일치를 시사.
- **확신도:** 낮음
  - 이번 조회 창에 **auth-service의 Pod 상태·readiness·리소스 메트릭·로그가 전혀 수집되지 않음.** 제공된 메트릭은 chat/content의 HikariCP·GC뿐이며 모두 정상(active=0, pending=0, GC pause 정상)이라 auth 근본 원인을 규명할 근거가 없음.
- **반증 데이터:** 없음.

---

## 3. 권장 다음 조치

1. **auth-service 가용성 즉시 확인** (근본 원인 규명, 최우선)
   - `kubectl get pods -n default -l app=auth-service -o wide` / `kubectl get endpoints auth-service -n default` → Endpoints가 비어있는지(=Pod 없음/Readiness 실패) 확인
   - `kubectl get svc auth-service -n default` → targetPort가 실제 컨테이너 리슨 포트(8081)와 일치하는지 확인
   - auth-service Pod의 최근 재시작/CrashLoop, 리소스(OOMKilled) 여부 확인

2. **누락된 관측 소스 보강** (결론 확신도 상향을 위해 필수)
   - Loki에서 **content-service Pod(`content-service-7bb7bffd56-vqf9f`)의 해당 시각 로그**를 traceId 필터 없이(app/pod 라벨로) 재조회 — 폴백 처리 로그와 auth 예외 스택 확보
   - auth-service의 로그/메트릭이 애초에 수집되고 있는지(스크레이프 타깃 등록 여부) 점검 — 이번 데이터셋에 auth 시리즈가 전무함

3. **폴백 동작 검증** (사용자 영향 범위 확정)
   - content 코드에서 auth 조회 실패 시 작성자명을 `"사용자"+userId`로 대체하는 경로 확인 및, 실패 시 502/부분실패 노출 대신 익명 표기하는 정책이 의도된 것인지 검토
   - auth 복구 후 동일 피드 재조회 시 정상 작성자명이 표시되는지 회귀 확인

4. **영향 범위 스코핑**
   - 동일 창에서 `error=~"Connection refused.*auth-service"` 조건으로 다른 traceId 다수 검색 → 단발성인지 광범위 장애인지 판별 (Kafka·chat 경로는 이 읽기 요청과 무관하므로 이번 증상 조사에서는 제외)

> 참고: 누락된 `kafka_consumer_fetch_manager_records_lag`는 알림 발송(chat) 경로 지표로, 이번 "피드 작성자 익명 표시" 증상과는 인과 경로가 다르므로 결론에 영향을 주지 않음.
