# RCA Report — `scan-1785904800`

| 항목 | 값 |
|---|---|
| 모드 | rca |
| 질문 | 최근 1시간 안에 앱이 자꾸 로그인 화면으로 튕긴다는 문의가 많다. 원인을 조사해줘 |
| 시각 | 2026-08-05T05:19:51.264582400Z |
| provider | claude-cli |
| model | `claude-opus-5` · turns 1 |
| prompt | `.\prompts\system-prompt.md` |
| tokens | in 134771 (cacheRead 23,449 · cacheCreate 111,320) / out 12541 · cost $1.4385 |
| elapsed | total 215732ms (tempo 1636 · loki 355 · mimir 1342 · assemble 524 · llm 200645) |

## 탐색 (Triage)

| 항목 | 값 |
|---|---|
| 시간창 해석 | 상대 표현 '최근 1시간' |
| 시간창 확신도 | EXACT |
| 스윕 창 | 2026-08-05T04:19:11.433312100Z ~ 2026-08-05T05:19:11.433312100Z |
| 좁힌 창 | 2026-08-05T04:40:00Z ~ 2026-08-05T05:15:00Z |
| 대상 | auth-service, content-service |
| traceId | 6a72c1125884c1f3995f139ebaafc05e |
| 트레이스 후보 | 3건 |
| 장애 후보 | 5건 · 선택 INC-1, INC-2, INC-3, INC-4 |
| 계획 파싱 | 성공 |
| 스윕 컨텍스트 | **후보만 — 원본 제외 (B)** |
| prompt | `.\prompts\triage-prompt.md` |
| tokens | in 32613 / out 1983 · cost $0.1801 |
| chars | 컨텍스트 2,554 + 프롬프트 1,399 = **3,953** |
| elapsed | survey 1519ms · llm 38191ms |

**선정 이유**: 로그인 튕김 증상의 상류(auth-service 에러+/login 지연)와 그 직후 하류(content-service 에러 폭증)가 04:45~05:10에 연속으로 이어져 하나의 인증 장애가 여러 후보로 쪼개졌을 가능성이 높아 함께 조사한다.

**근거**

- INC-3: auth-service http post /login 4,586ms (2026-08-05T04:50:26.839978Z, TEMPO slow 채널) — 로그인/토큰 발급 경로 자체가 느려 클라이언트 타임아웃→재로그인 유발 가능
- INC-1: auth-service ERROR/WARN 3건(04:45~04:50) + 2건(04:50~04:55) — 증상 창에서 가장 이른 신호이며 INC-3와 같은 서비스·같은 시각
- INC-2: content-service ERROR/WARN 3→42→148→56건, 04:55~05:10에 걸쳐 auth 신호 직후 급증 — 토큰 검증 실패(401)가 하류에서 에러로 표출된 형태로 의심
- INC-4: content-service connection 3,462ms (04:55:28.641523Z) — INC-2 급증 시작 시각과 겹치는 같은 서비스의 다른 지문이라 분리하지 않음
- 무신호 근거: Tempo '{ status = error }' 0건인데 Loki ERROR/WARN은 6구간 발생 — 에러 span을 만들지 않는 인증 거부(401) 또는 200+지연 형태의 장애 패턴과 일치
- up/mongodb_up/kafka_brokers/consumergroup_lag/websocket_active_users 이상 0건 — 인프라·브로커·소켓 계층은 배제, 애플리케이션 인증 경로로 범위 축소

**스윕이 찾은 트레이스** (고른 것은 6a72c1125884c1f3995f139ebaafc05e)

| traceId | 채널 | root service | root span | ms |
|---|---|---|---|---:|
| `6a72c3507df6aa550e82d18871008356` | slow | content-service | task battle-hot-score-scheduler.time-weight-update | 4023 |
| `6a72c240515fe3ce1bdd0c0fc9830077` | slow | content-service | connection | 3462 |
| `6a72c1125884c1f3995f139ebaafc05e` ←선택 | slow | auth-service | http post /login | 4586 |

**장애 후보** (코드가 신호를 묶은 것 · 창은 여기서 계산됨)

## INC-1  auth-service  |  ERROR/WARN
- 구간: 2026-08-05T04:45:00Z ~ 2026-08-05T04:55:00Z  (LOKI · 집계 해상도만큼 흐림)
- ERROR/WARN 3건 (2026-08-05T04:45:00Z ~ 2026-08-05T04:50:00Z)
- ERROR/WARN 2건 (2026-08-05T04:50:00Z ~ 2026-08-05T04:55:00Z)
- 같은 시각의 다른 후보: INC-2, INC-3  (인과 여부는 판단하지 않았다)

## INC-2  content-service  |  ERROR/WARN
- 구간: 2026-08-05T04:50:00Z ~ 2026-08-05T05:10:00Z  (LOKI · 집계 해상도만큼 흐림)
- ERROR/WARN 3건 (2026-08-05T04:50:00Z ~ 2026-08-05T04:55:00Z)
- ERROR/WARN 42건 (2026-08-05T04:55:00Z ~ 2026-08-05T05:00:00Z)
- ERROR/WARN 148건 (2026-08-05T05:00:00Z ~ 2026-08-05T05:05:00Z)
- ERROR/WARN 56건 (2026-08-05T05:05:00Z ~ 2026-08-05T05:10:00Z)
- 같은 시각의 다른 후보: INC-1, INC-3, INC-4, INC-5  (인과 여부는 판단하지 않았다)

## INC-3  auth-service  |  http post /login
- 구간: 2026-08-05T04:50:26.839978Z ~ 2026-08-05T04:50:31.425978Z  (TEMPO · 시각 정확)
- auth-service http post /login 4,586ms (slow 채널)
- traceId: 6a72c1125884c1f3995f139ebaafc05e
- 같은 시각의 다른 후보: INC-1, INC-2  (인과 여부는 판단하지 않았다)

## INC-4  content-service  |  connection
- 구간: 2026-08-05T04:55:28.641523Z ~ 2026-08-05T04:55:32.103523Z  (TEMPO · 시각 정확)
- content-service connection 3,462ms (slow 채널)
- traceId: 6a72c240515fe3ce1bdd0c0fc9830077
- 같은 시각의 다른 후보: INC-2  (인과 여부는 판단하지 않았다)

## INC-5  content-service  |  task battle-hot-score-scheduler.time-weight-update
- 구간: 2026-08-05T05:00:00.132015Z ~ 2026-08-05T05:00:04.155015Z  (TEMPO · 시각 정확)
- content-service task battle-hot-score-scheduler.time-weight-update 4,023ms (slow 채널)
- traceId: 6a72c3507df6aa550e82d18871008356
- 같은 시각의 다른 후보: INC-2  (인과 여부는 판단하지 않았다)

**기각한 후보**

- INC-5 — battle-hot-score-scheduler.time-weight-update는 사용자 요청 경로가 아닌 배치 스케줄러 태스크라 로그인 세션 만료·튕김 증상과 직접 연결되지 않으며, 4,023ms는 스케줄러로선 흔한 지연폭이다(다만 INC-2 피크 시각과 겹치므로 content-service 조사 중 리소스 경합 요인으로 재확인할 여지는 남긴다).

**보정 기록**

- 창을 후보 [INC-1, INC-2, INC-3, INC-4] 의 신호 시각에서 계산했다 (2026-08-05T04:40:00Z ~ 2026-08-05T05:15:00Z)

**스윕 무신호/실패**

- Tempo 에러 검색 '{ status = error }'이 이 창에서 0건이다. 트레이스가 생성되지 않는 장애(컨슈머 전멸·파드 부재)이거나 에러가 아닌 형태의 장애(200 성공 + 지연)일 수 있으니 이 사실 자체를 근거로 쓸 것.

## 수집 범위 (Coverage)

- **window**: 2026-08-05T04:40:00Z ~ 2026-08-05T05:15:00Z (2100s)
- **trace**: 129,827B / 167 spans (상위 span만)
- **창 안 후보 트레이스**: 10건 / 129,827B
- **logs**: errwarn=75,158B · traceId=5,600B
- **metrics**: 8 수집 / 234,686B, 누락 [sum(rate(http_server_requests_seconds_count{application="content-service", status="401"}[1m]))]
- **context**: 183,110 chars (+ 시스템 프롬프트 805 chars)

### 토큰 축 (개선 지표)

| 단계 | 총 in | chars | 컨텍스트 토큰 |
|---|---:|---:|---:|
| 탐색 | 32,613 | 3,953 | 3,187 |
| 분석 | 134,771 | 183,915 | 105,345 |
| **합계** | **167,384** | | **108,532** |

- **overheadTokens 29,426 tok — 이 회차에 실측**(1자 프롬프트 프로브, 같은 명령·모델·샌드박스).
  호출마다 한 번씩 얹히므로 단계별로 뺀다. **다른 날 상수를 쓰지 않았다 = `█ 실측`.**
- contextTokens (count_tokens API): 측정 안 됨 — 구독 CLI 경로엔 API 키가 없다
- 비용은 **API 환산 추정치**다. 구독 계정으로 호출하므로 실제 청구액이 아니다.

## 수집 실패/누락

- Metric 'sum(rate(http_server_requests_seconds_count{application="content-service", status="401"}[1m]))' returned no series in this window; skipped.

---

조사 결과입니다. 데이터로 확인된 사실만 정리했습니다.

## 1. 원인 후보 랭킹 (최대 3개)

1. **auth-service 파드 교체(04:48~04:51Z) 이후 발급된 JWT의 서명 키가 content-service 검증 키와 불일치 → 인증 요청이 401로 거부**
2. **content-service 측 JWT 검증 설정/코드 변경이 배포로 유입 (JwtFilter → JwtAuthenticationFilter 교체가 관측됨)**
3. **단일 자동화 클라이언트(폴러/모니터)가 구(舊) 시크릿으로 서명된 토큰을 반복 전송 — 즉 장애가 아니라 국소 현상**

시간 표기는 로그 원문이 KST(UTC+9)이므로, 아래에서는 `13:52:23(=04:52:23Z)` 형태로 병기합니다.

---

## 2. 후보별 근거

### 후보 1 — auth-service 재기동 후 서명 키 불일치

**근거**
- 검증 실패 로그 원문: `c.e.t.app.auth.token.JwtParser - [auth-jwt] JWT 서명 검증 실패 — 위조 또는 시크릿 불일치`. 이 로그는 **content-service에서만** 나오고 auth-service·chat-service에는 한 건도 없습니다.
- 실패는 전부 **동일 엔드포인트 `GET /api/feeds/following` 401** 한 종류입니다. 수집 로그에서만 100건 이상(파드별: `6995bb7d94-nq9l2` 1건, `64b7dfc78f-kjc8w` 약 44건, `64b7dfc78f-q8qnn` 약 47건, `5489b58cbc-r8r5j` 12건, `5489b58cbc-2ckp6` 12건).
- **전환 시점이 파드 재시작 없이 발생**: 같은 파드 `content-service-6995bb7d94-nq9l2`에서
  - `13:51:20.226 [HTTP-SLOW] GET /api/feeds/following 200 - 2462ms` (성공)
  - `13:52:23.159 [auth-jwt] JWT 서명 검증 실패` → `13:52:23.161 GET /api/feeds/following 401 - 4ms`
  검증자(content) 프로세스는 그대로인데 결과가 200→401로 뒤집혔으므로, **바뀐 쪽은 토큰(발급자)** 입니다.
- 그 직전 auth-service 파드가 교체되었습니다. `auth-service-5999bb9f5c-hmgp9` 메트릭 종료 04:48:30Z → `...-jv2jn` 기동 로그 `13:48:47`, `13:49:53 DefaultSecurityFilterChain`, 메트릭 시작 04:51:30Z. 새 파드의 첫 HTTP 요청이 `13:50:31 [http-nio-8081-exec-1] [HTTP-SLOW] POST /api/login 200 - 4574ms` (trace `6a72c1125884c1f3995f139ebaafc05e`, span `http post /login`, `outcome:SUCCESS`, `status:200`).
- 즉 **새 auth 파드에서 로그인 성공(04:50:31Z) → 약 2분 뒤 그 토큰류가 content에서 서명 검증 실패(04:52:23Z)** 라는 순서가 성립합니다.
- content-service는 이후 ReplicaSet이 두 번 바뀌었지만(`6995bb7d94` → `64b7dfc78f` → `5489b58cbc`) **세 세대 모두에서 동일 실패가 계속**됩니다. content 재배포로 해소되지 않는다는 점이 "발급자 측 키가 바뀌었다"는 해석과 일치합니다.
- 인프라 원인은 배제됩니다: `up` 전 구간 1(모든 앱·노드·redis·kafka·mongodb), `hikaricp_connections_pending` 사실상 전 구간 0, `kafka_consumergroup_lag` 0, GC pause 최대 9.8e-4 s/s 수준.

**확신도: 높음**

**반증 데이터**
- auth-service 두 파드의 ReplicaSet 해시가 `5999bb9f5c`로 **동일**합니다. 파드 스펙(이미지·env)이 바뀌지 않았다는 뜻이므로, "배포로 시크릿을 바꿨다"는 단순 설명과는 배치됩니다. 부팅 시 랜덤 생성이거나 외부 Secret/설정 소스가 파드 스펙 변경 없이 바뀐 경우여야 성립하는데, **시크릿의 출처를 확인할 데이터가 없습니다(데이터 부족)**.
- 같은 창에서 `GET /api/battles/hot 200`, `GET /api/products 200`, `GET /api/feeds/scroll 200`, `GET /api/feeds/hot 200` 이 정상 응답합니다. 다만 이들이 인증 필요 엔드포인트인지 확인할 데이터가 없어 반증으로 확정할 수는 없습니다.

**대기·지연 구간 판정**
- `POST /api/login` 4574ms(=span `http post /login` durNs 4586409000): **타임아웃 설정값 미수집 → 만료 여부 판정 불가.** 최종 상태는 `outcome:SUCCESS`, `status:200` → **성공**.
- 401 요청들의 소요는 7~167ms(예외적으로 `14:00:02.417 ... 401 - 971ms` 1건). 어떤 타임아웃에도 도달하지 않았고 **최종 상태는 실패(401)로 확정 종료**. 동일 엔드포인트가 초기 약 2.13초, 후반 약 10.6초 주기로 반복되지만 이것이 클라이언트 재시도인지 정기 폴링인지 구분할 근거가 없어 **재시도 여부는 판정 불가**.

---

### 후보 2 — content-service 배포로 유입된 JWT 검증 경로 변경

**근거**
- 필터 체인 구성이 세대별로 다릅니다. content-service `5489b58cbc-r8r5j` 트레이스(`6a72c6611480c3e4855b1bf7fe39e996`)의 `security filterchain after` 이벤트에는 `after JwtAuthenticationFilter`가 있고, chat-service(`6a72c65dcd2989298b2c5434886f9244`)와 auth-service(`6a72c65da4cd6481086e280db2ca2eed`)에는 `after JwtFilter`가 있습니다. 즉 **최신 content 배포에서 JWT 필터가 교체/개명**되었습니다 — 인증 코드가 그 시점에 실제로 손대졌다는 직접 증거입니다.
- 관측 창 35분 안에 content-service ReplicaSet이 3세대 돌았습니다(파드 기동 로그 `13:55:43`, `13:58:39`, `14:04:05`, `14:06:47`). 배포 빈도 자체가 설정 드리프트의 창구입니다.
- 부수 관측: 트레이스 `6a72c240515fe3ce1bdd0c0fc9830077`은 content-service 기동 시 `alter table ... modify column enum(...)` DDL 27건 + 메타데이터 조회를 3.46초간 수행합니다(Hibernate 스키마 자동 갱신). 401과 무관하지만 매 배포마다 운영 DB에 DDL이 나가는 상태입니다.

**확신도: 중간**

**반증 데이터**
- 첫 401은 `13:52:23`, **이 배포들보다 앞선 구세대 파드 `6995bb7d94-nq9l2`에서** 발생했습니다. 따라서 후보 2는 최초 발단을 설명하지 못합니다. 기껏해야 악화·고착 요인입니다.
- 구세대 `6995bb7d94-nq9l2`는 `13:51:20`에 같은 엔드포인트를 200으로 처리했으므로, 이 파드의 검증 설정 자체는 한때 정상이었습니다.

**대기·지연 구간 판정**
- 기동 DDL 구간: `connection` span durNs 3462950000(3.46초), 하위 `query` span들 최장 389.8ms. 타임아웃 설정값 미수집 → **만료 여부 판정 불가**. 오류 속성·예외 없이 하위 span이 모두 완료 → **성공**.

---

### 후보 3 — 구(舊) 토큰을 들고 반복 호출하는 단일 자동화 클라이언트

**근거**
- 401의 **간격이 기계적으로 균일**합니다. `13:59:39.58 / :42.09 / :44.24 / :46.39 / :48.52` → 약 2.13초 등간격, 후반 `14:07:39.62 / :50.47 / 14:08:01.09 / :11.80 / :22.40` → 약 10.6초 등간격. 사람 트래픽의 분포가 아닙니다.
- **대상이 `GET /api/feeds/following` 단 하나**입니다. 다른 인증 관련 엔드포인트의 401은 한 건도 없습니다.
- 모든 401 로그의 `userId=NONE` 이라 영향 사용자 수를 셀 수 없습니다.
- "위조 또는 시크릿 불일치"는 메시지 자체가 두 가능성을 구분하지 못합니다.

**확신도: 낮음**

**반증 데이터**
- 요청이 **5개 content 파드 전부**(3세대 ReplicaSet 걸쳐)에 분산되어 있고 워커 스레드도 `exec-1`~`exec-5`로 라운드로빈됩니다. 단일 클라이언트라기엔 로드밸런싱 폭이 넓습니다.
- 문의가 "많다"는 사용자 신고가 존재합니다(질문 전제). 다만 이를 뒷받침할 클라이언트 로그·사용자 식별자는 수집되지 않았습니다.

**대기·지연 구간 판정**
- 해당 요청들에 대기 구간 없음(전부 10ms 내외 즉시 401). **최종 상태: 실패(401), 폐기.** 클라이언트 재시도 정책은 근거 없음 → **판정 불가**.

---

### 배제한 것 (수치 근거)

- **DB/커넥션 풀 고갈 아님.** 호출 그래프의 `mysql/content 최대 3855.9ms`는 대기가 아니라 **점유** 시간입니다. 트레이스 `6a72c3507df6aa550e82d18871008356`의 `connection` span은 시작 `1785906000281293000`, `acquired` 이벤트 `1785906000284965000` → **획득 대기 3.67ms**, `commit` `1785906004133280000` → 3852ms 점유 후 **커밋 성공**. `hikaricp_connections_pending`은 전 파드 전 구간 0(단일 시점 1 한 번). 스케줄러 로그도 `[scheduler] 시간 가중치 업데이트 완료 - 20건, 2584ms` → **성공**.
- **인프라 다운 아님.** `up` 모든 시리즈 전 구간 1, `mongodb_up` 1, `kafka_brokers` 1, `kafka_consumergroup_lag` 0(또는 -1=미할당).
- **GC/메모리 아님.** `rate(jvm_gc_pause_seconds_sum[5m])` 최대 9.83e-4 (content `5489b58cbc-r8r5j`, 05:07:30Z). 초당 1ms 미만.
- **HTTP-SLOW 건들(4574ms, 2462ms, 2101ms 등)은 401의 원인이 아닙니다.** 전부 `200` 응답이고, 파드 콜드스타트 직후 구간에 몰려 있습니다.

---

### 데이터 부족 — 추가 수집 필요

1. **content-service 401 메트릭이 결측**입니다(`sum(rate(http_server_requests_seconds_count{application="content-service", status="401"}[1m]))` no series). 실제 401 비율·영향 규모를 정량화할 수 없어 **위 모든 후보의 확신도를 한 단계 낮춰** 기술했습니다.
2. auth-service와 content-service의 **JWT 서명 키 출처**(env / K8s Secret / 부팅 시 생성 / kid). 후보 1과 2를 최종 분리하는 결정적 데이터입니다.
3. **배포 이력**: `kubectl rollout history`, 각 ReplicaSet 생성 시각과 이미지 태그, auth-service 파드가 왜 04:48Z에 교체됐는지(OOMKill/eviction/수동 재시작).
4. **사용자 식별 로그**: 모든 401이 `userId=NONE`이라 영향 사용자 수·중복 사용자 여부를 셀 수 없습니다. 클라이언트(앱) 측 로그도 없어 **"401 → 로그인 화면 튕김"의 연결은 추론**이며 직접 관측된 바 없습니다.
5. auth-service의 토큰 발급 성공/실패 로그 및 발급 토큰의 `kid`/`iat`.

---

## 3. 권장 다음 조치

### 이미 발생한 피해: 복구 가능한가

**부분적으로 가능. 데이터 손실은 없음.**
- 거부된 요청은 전부 `GET /api/feeds/following` 읽기 호출이고 전부 401로 즉시 종료(7~167ms)되었습니다. 쓰기 유실·중복 커밋 근거 없음 → **복구할 데이터 자체가 없습니다.**
- 사용자 세션은 원칙적으로 재로그인으로 복구되지만, **후보 1이 맞다면 재로그인만으로는 복구되지 않습니다.** 새 auth-service가 발급하는 토큰도 content-service에서 같은 이유로 거부되기 때문입니다. **시크릿 동기화가 선행되어야** 재로그인이 유효합니다. 순서: ① auth/content 양측 서명 키 확인·일치 → ② content-service 롤아웃 → ③ 사용자 재로그인 유도.
- 로그인 자체는 살아 있습니다(`POST /api/login 200`). 인증 기능 전면 중단은 아닙니다.

### 재발 방지

1. **JWT 서명 키를 단일 K8s Secret에서 auth·content·chat에 동일 주입**하고, 부팅 시 랜덤 생성 경로가 있다면 제거. 키 부재 시 랜덤 폴백이 아니라 **기동 실패(fail-fast)** 로 전환.
2. **`kid` 기반 키 롤오버 + 이전 키 유예 검증**을 도입. 발급자만 먼저 교체돼도 검증자가 이전 키로 유예 기간 동안 통과시키면 이번 유형은 사용자에게 보이지 않습니다.
3. **auth와 검증 서비스의 롤아웃을 묶어** 배포(또는 키 변경 시 검증 측 선행 반영). 35분 동안 content ReplicaSet이 3세대 돈 배포 빈도도 함께 낮출 것.
4. **`http_server_requests{status="401"}` 메트릭이 아예 없는 상태를 먼저 고치고**(이번 조사에서 결측), 401 급증 알림을 건다. 지금은 이 유형 장애를 메트릭으로 탐지할 수단이 없습니다.
5. 부수: content-service 기동 시 운영 DB에 `alter table` DDL 27건이 나갑니다(trace `6a72c240...`). Hibernate `ddl-auto`를 `validate`로 내리고 스키마 변경은 마이그레이션 도구로 분리.

### 복구 확인

1. `c.e.t.app.auth.token.JwtParser - [auth-jwt] JWT 서명 검증 실패` 로그가 **모든 content-service 파드에서 0건**(최소 10분 연속). 현재는 초기 약 2.13초, 후반 약 10.6초 간격으로 계속 나옵니다.
2. `GET /api/feeds/following`이 **200**으로 응답. 마지막 정상 관측치는 `13:51:20 ... 200 - 2462ms`입니다.
3. 신규 로그인 → 발급 토큰으로 content-service 인증 엔드포인트 호출까지 **엔드투엔드 1회 수동 검증**. 로그인 200만으로는 불충분합니다(이번 장애가 정확히 그 사이에서 끊겼습니다).
4. 복구 판정 전에 **401 메트릭 시리즈가 실제로 수집되는지** 확인. 안 보이면 "0건"인지 "관측 불가"인지 구분할 수 없습니다.
5. 콜드스타트 기인 HTTP-SLOW(`4574ms` 등)는 파드 안정화 후 자연 해소 여부만 확인하면 됩니다 — 별도 조치 대상 아님.

---

## 관측 증거 (Evidence)

> 이 조사가 실제로 본 값이다. 원본 응답 전체는 `reports/raw/scan-1785904800-*.json`에 있다.

### 호출 그래프 (트레이스에서 추출)

```
content-service --db--> redis  3회  최대 47.5ms  [SET, INFO]
content-service --jdbc--> mysql/content (HikariPool-1)  149회  최대 3855.9ms
    events: acquired, commit
```

### span (duration 상위 15 / 전체 167)

| ms | service | span | 시작 |
|---:|---|---|---|
| 4586.41 | auth-service | `http post /login` | 2026-08-05T04:50:26.839978Z |
| 4501.09 | auth-service | `secured request` | 2026-08-05T04:50:26.912906Z |
| 4023.88 | content-service | `task battle-hot-score-scheduler.time-weight-update` | 2026-08-05T05:00:00.132015Z |
| 3855.90 | content-service | `connection` | 2026-08-05T05:00:00.281293Z |
| 3462.95 | content-service | `connection` | 2026-08-05T04:55:28.641523Z |
| 2603.96 | content-service | `battle-hot-score:time-weight` | 2026-08-05T05:00:00.292203Z |
| 1592.07 | content-service | `result-set` | 2026-08-05T05:00:01.286982Z |
| 389.82 | content-service | `query` | 2026-08-05T04:55:28.818557Z |
| 234.87 | content-service | `query` | 2026-08-05T04:55:29.270484Z |
| 177.09 | content-service | `query` | 2026-08-05T04:55:30.253380Z |
| 111.42 | content-service | `query` | 2026-08-05T04:55:29.995152Z |
| 99.62 | content-service | `query` | 2026-08-05T04:55:29.574384Z |
| 86.66 | content-service | `query` | 2026-08-05T04:55:29.677718Z |
| 65.80 | auth-service | `security filterchain before` | 2026-08-05T04:50:26.846668Z |
| 65.48 | content-service | `query` | 2026-08-05T04:55:29.507290Z |

### 로그 원문 (60 / 전체 259줄)

전체가 상한을 넘어 ERROR/WARN 줄을 우선 발췌했다. 나머지는 원본 파일에 있다.

```
2026-08-05T04:48:47.192609560Z  [auth-service]  [2m2026-08-05 13:48:47[0;39m [2m[main][0;39m [33m WARN [traceId=NONE,spanId=NONE,userId=NONE][0;39m [36mo.s.c.s.PostProcessorRegistrationDelegate$BeanPostProcessorChecker[0;39m [2m-[0;39m Bean 'org.springframework.ws.config.annotation.DelegatingWsConfiguration' of type [org.springframework.ws.config.annotation.DelegatingWsConfiguration$$SpringCGLIB$$0] is not eligible for getting processed by all BeanPostProcessors (for example: not eligible for auto-proxying). The currently created BeanPostProcessor [annotationActionEndpointMapping] is declared through a non-static factory method on that class; consider declaring it as static instead.
2026-08-05T04:49:10.230757491Z  [auth-service]  [2m2026-08-05 13:49:10[0;39m [2m[main][0;39m [33m WARN [traceId=NONE,spanId=NONE,userId=NONE][0;39m [36morg.hibernate.orm.deprecation[0;39m [2m-[0;39m HHH90000025: MySQLDialect does not need to be specified explicitly using 'hibernate.dialect' (remove the property setting and it will be selected by default)
2026-08-05T04:49:48.420594088Z  [auth-service]  [2m2026-08-05 13:49:48[0;39m [2m[main][0;39m [33m WARN [traceId=NONE,spanId=NONE,userId=NONE][0;39m [36mo.s.b.a.o.j.JpaBaseConfiguration$JpaWebConfiguration[0;39m [2m-[0;39m spring.jpa.open-in-view is enabled by default. Therefore, database queries may be performed during view rendering. Explicitly configure spring.jpa.open-in-view to disable this warning
2026-08-05T04:49:53.494718104Z  [auth-service]  [2m2026-08-05 13:49:53[0;39m [2m[main][0;39m [32m INFO [traceId=NONE,spanId=NONE,userId=NONE][0;39m [36mo.s.s.web.DefaultSecurityFilterChain[0;39m [2m-[0;39m Will secure Or [Mvc [pattern='/api/external/**']] with [org.springframework.security.web.session.DisableEncodeUrlFilter@26156929, org.springframework.security.web.context.request.async.WebAsyncManagerIntegrationFilter@3291cfad, org.springframework.security.web.context.SecurityContextHolderFilter@60c88b78, org.springframework.security.web.header.HeaderWriterFilter@7382ec67, org.springframework.web.filter.CorsFilter@756974d8, org.springframework.security.web.authentication.logout.LogoutFilter@5bd9615c, com.example.toyauth.app.common.filter.ExternalAuthenticationFilter@75044df3, org.springframework.security.web.savedrequest.RequestCacheAwareFilter@7647eff0, org.springframework.security.web.servletapi.SecurityContextHolderAwareRequestFilter@7c47ae7d, org.springframework.security.web.authentication.AnonymousAuthenticationFilter@43b8d302, org.springframework.security.web.session.SessionManagementFilter@2dbc453a, org.springframework.security.web.access.ExceptionTranslationFilter@5a947fd5, org.springframework.security.web.access.intercept.AuthorizationFilter@263121b]
2026-08-05T04:49:53.798375553Z  [auth-service]  [2m2026-08-05 13:49:53[0;39m [2m[main][0;39m [32m INFO [traceId=NONE,spanId=NONE,userId=NONE][0;39m [36mo.s.s.web.DefaultSecurityFilterChain[0;39m [2m-[0;39m Will secure any request with [org.springframework.security.web.session.DisableEncodeUrlFilter@1baab1b1, org.springframework.security.web.context.request.async.WebAsyncManagerIntegrationFilter@1c833e78, org.springframework.security.web.context.SecurityContextHolderFilter@2587f18d, org.springframework.security.web.header.HeaderWriterFilter@20df25f8, org.springframework.web.filter.CorsFilter@7effdd04, org.springframework.security.web.authentication.logout.LogoutFilter@4effe36a, org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestRedirectFilter@33db58ce, org.springframework.security.oauth2.client.web.OAuth2LoginAuthenticationFilter@3a0f0552, com.example.toyauth.app.common.filter.JwtFilter@2ca6937, org.springframework.security.web.authentication.ui.DefaultLoginPageGeneratingFilter@24d642eb, org.springframework.security.web.authentication.ui.DefaultLogoutPageGeneratingFilter@604c8ed5, org.springframework.security.web.savedrequest.RequestCacheAwareFilter@4d17ce84, org.springframework.security.web.servletapi.SecurityContextHolderAwareRequestFilter@143ec23b, org.springframework.security.web.authentication.AnonymousAuthenticationFilter@2e259c54, org.springframework.security.web.session.SessionManagementFilter@7148320d, org.springframework.security.web.access.ExceptionTranslationFilter@74e3836a]
2026-08-05T04:50:31.418970341Z  [auth-service]  [2m2026-08-05 13:50:31[0;39m [2m[http-nio-8081-exec-1][0;39m [33m WARN [traceId=6a72c1125884c1f3995f139ebaafc05e,spanId=995f139ebaafc05e,userId=NONE][0;39m [36mc.e.t.a.c.f.RequestLoggingFilter[0;39m [2m-[0;39m [HTTP-SLOW] POST /api/login 200 - 4574ms
2026-08-05T04:51:20.125670952Z  [auth-service]  [2m2026-08-05 13:51:20[0;39m [2m[http-nio-8081-exec-3][0;39m [33m WARN [traceId=6a72c1450b2fc3e5175b7adf422ce9a4,spanId=43bdeb518d0cefae,userId=NONE][0;39m [36mc.e.t.a.c.f.RequestLoggingFilter[0;39m [2m-[0;39m [HTTP-SLOW] GET /api/external/users/1/followings 200 - 2326ms
2026-08-05T05:04:05.977537858Z  [content-service]  2026-08-05 14:04:05.977 [main]  WARN [traceId=NONE,spanId=NONE,userId=NONE] o.s.b.a.o.j.JpaBaseConfiguration$JpaWebConfiguration - spring.jpa.open-in-view is enabled by default. Therefore, database queries may be performed during view rendering. Explicitly configure spring.jpa.open-in-view to disable this warning
2026-08-05T05:04:10.720852669Z  [content-service]  2026-08-05 14:04:10.720 [kafka-admin-client-thread | content-service-admin-0]  WARN [traceId=NONE,spanId=NONE,userId=NONE] o.a.k.clients.admin.KafkaAdminClient - [AdminClient clientId=content-service-admin-0] The DescribeTopicPartitions API is not supported, using Metadata API to describe topics.
2026-08-05T05:05:45.770648891Z  [content-service]  2026-08-05 14:05:45.767 [http-nio-8082-exec-1]  WARN [traceId=6a72c4a86b74350c6f0d6a38701c87df,spanId=15a2040d84dd0f6f,userId=NONE] o.s.d.w.c.SpringDataJacksonConfiguration$PageModule$WarningLoggingModifier - Serializing PageImpl instances as-is is not supported, meaning that there is no guarantee about the stability of the resulting JSON structure!
2026-08-05T05:05:45.933463239Z  [content-service]  2026-08-05 14:05:45.931 [http-nio-8082-exec-1]  WARN [traceId=6a72c4a86b74350c6f0d6a38701c87df,spanId=6f0d6a38701c87df,userId=NONE] c.e.t.a.c.f.RequestLoggingFilter - [HTTP-SLOW] GET /api/battles/hot 200 - 1171ms
2026-08-05T05:05:45.934335187Z  [content-service]  2026-08-05 14:05:45.934 [http-nio-8082-exec-2]  WARN [traceId=6a72c4a8c5fdd93db3c7e3465179fbc9,spanId=b3c7e3465179fbc9,userId=NONE] c.e.t.a.c.f.RequestLoggingFilter - [HTTP-SLOW] GET /api/battles/hot 200 - 1169ms
2026-08-05T05:05:46.860600769Z  [content-service]  2026-08-05 14:05:46.858 [http-nio-8082-exec-3]  WARN [traceId=6a72c4a8d3927d5e6da50ca3f251aa1b,spanId=6da50ca3f251aa1b,userId=NONE] c.e.t.a.c.f.RequestLoggingFilter - [HTTP-SLOW] GET /api/products 200 - 2095ms
2026-08-05T05:05:46.951825739Z  [content-service]  2026-08-05 14:05:46.951 [http-nio-8082-exec-4]  WARN [traceId=6a72c4a8125d26b333b62dee58ec17fc,spanId=33b62dee58ec17fc,userId=NONE] c.e.t.a.c.f.RequestLoggingFilter - [HTTP-SLOW] GET /api/feeds/scroll 200 - 2101ms
2026-08-05T05:05:47.288433048Z  [content-service]  2026-08-05 14:05:47.288 [http-nio-8082-exec-5]  WARN [traceId=6a72c4aaa1a629cc2a3386781057d9e1,spanId=2a3386781057d9e1,userId=NONE] c.e.t.a.c.f.RequestLoggingFilter - [HTTP-SLOW] GET /api/feeds/hot 200 - 1136ms
2026-08-05T05:06:47.364733752Z  [content-service]  2026-08-05 14:06:47.364 [main]  WARN [traceId=NONE,spanId=NONE,userId=NONE] o.s.b.a.o.j.JpaBaseConfiguration$JpaWebConfiguration - spring.jpa.open-in-view is enabled by default. Therefore, database queries may be performed during view rendering. Explicitly configure spring.jpa.open-in-view to disable this warning
2026-08-05T05:06:52.302324413Z  [content-service]  2026-08-05 14:06:52.302 [kafka-admin-client-thread | content-service-admin-0]  WARN [traceId=NONE,spanId=NONE,userId=NONE] o.a.k.clients.admin.KafkaAdminClient - [AdminClient clientId=content-service-admin-0] The DescribeTopicPartitions API is not supported, using Metadata API to describe topics.
2026-08-05T05:07:39.630061076Z  [content-service]  2026-08-05 14:07:39.629 [http-nio-8082-exec-1]  WARN [traceId=6a72c51b7e51cef98071de891c9771d0,spanId=b4d2173ebf40471d,userId=NONE] c.e.t.app.auth.token.JwtParser - [auth-jwt] JWT 서명 검증 실패 — 위조 또는 시크릿 불일치
2026-08-05T05:07:39.688979703Z  [content-service]  2026-08-05 14:07:39.688 [http-nio-8082-exec-1]  WARN [traceId=6a72c51b7e51cef98071de891c9771d0,spanId=8071de891c9771d0,userId=NONE] c.e.t.a.c.f.RequestLoggingFilter - [HTTP] GET /api/feeds/following 401 - 167ms
2026-08-05T05:07:45.137462927Z  [content-service]  2026-08-05 14:07:45.135 [http-nio-8082-exec-4]  WARN [traceId=6a72c521440eacc712b14e09b1061d42,spanId=7bdcaefb2e1f4d95,userId=NONE] c.e.t.app.auth.token.JwtParser - [auth-jwt] JWT 서명 검증 실패 — 위조 또는 시크릿 불일치
2026-08-05T05:07:45.148877682Z  [content-service]  2026-08-05 14:07:45.148 [http-nio-8082-exec-4]  WARN [traceId=6a72c521440eacc712b14e09b1061d42,spanId=12b14e09b1061d42,userId=NONE] c.e.t.a.c.f.RequestLoggingFilter - [HTTP] GET /api/feeds/following 401 - 54ms
2026-08-05T05:07:50.472705139Z  [content-service]  2026-08-05 14:07:50.471 [http-nio-8082-exec-2]  WARN [traceId=6a72c526ffad591e357e8b647ea12797,spanId=ef7543eaad951f6c,userId=NONE] c.e.t.app.auth.token.JwtParser - [auth-jwt] JWT 서명 검증 실패 — 위조 또는 시크릿 불일치
2026-08-05T05:07:50.487005369Z  [content-service]  2026-08-05 14:07:50.486 [http-nio-8082-exec-2]  WARN [traceId=6a72c526ffad591e357e8b647ea12797,spanId=357e8b647ea12797,userId=NONE] c.e.t.a.c.f.RequestLoggingFilter - [HTTP] GET /api/feeds/following 401 - 21ms
2026-08-05T05:07:55.784496067Z  [content-service]  2026-08-05 14:07:55.782 [http-nio-8082-exec-2]  WARN [traceId=6a72c52b66e7070e0b74d9714a26eb81,spanId=243eac1ef92a35b0,userId=NONE] c.e.t.app.auth.token.JwtParser - [auth-jwt] JWT 서명 검증 실패 — 위조 또는 시크릿 불일치
2026-08-05T05:07:55.788208567Z  [content-service]  2026-08-05 14:07:55.787 [http-nio-8082-exec-2]  WARN [traceId=6a72c52b66e7070e0b74d9714a26eb81,spanId=0b74d9714a26eb81,userId=NONE] c.e.t.a.c.f.RequestLoggingFilter - [HTTP] GET /api/feeds/following 401 - 9ms
2026-08-05T05:08:01.095097394Z  [content-service]  2026-08-05 14:08:01.094 [http-nio-8082-exec-3]  WARN [traceId=6a72c531f0ff865c8b82be330ea003e8,spanId=0e78ec08219e18fd,userId=NONE] c.e.t.app.auth.token.JwtParser - [auth-jwt] JWT 서명 검증 실패 — 위조 또는 시크릿 불일치
2026-08-05T05:08:01.100885747Z  [content-service]  2026-08-05 14:08:01.100 [http-nio-8082-exec-3]  WARN [traceId=6a72c531f0ff865c8b82be330ea003e8,spanId=8b82be330ea003e8,userId=NONE] c.e.t.a.c.f.RequestLoggingFilter - [HTTP] GET /api/feeds/following 401 - 12ms
2026-08-05T05:08:06.499410084Z  [content-service]  2026-08-05 14:08:06.499 [http-nio-8082-exec-1]  WARN [traceId=6a72c5366f6f5f407dae5c1c20544857,spanId=ab335ec91d665f04,userId=NONE] c.e.t.app.auth.token.JwtParser - [auth-jwt] JWT 서명 검증 실패 — 위조 또는 시크릿 불일치
2026-08-05T05:08:06.503858494Z  [content-service]  2026-08-05 14:08:06.503 [http-nio-8082-exec-1]  WARN [traceId=6a72c5366f6f5f407dae5c1c20544857,spanId=7dae5c1c20544857,userId=NONE] c.e.t.a.c.f.RequestLoggingFilter - [HTTP] GET /api/feeds/following 401 - 9ms
2026-08-05T05:08:11.805707078Z  [content-service]  2026-08-05 14:08:11.803 [http-nio-8082-exec-4]  WARN [traceId=6a72c53b54a0cd8148c94e6d1492050e,spanId=ba05717847d53394,userId=NONE] c.e.t.app.auth.token.JwtParser - [auth-jwt] JWT 서명 검증 실패 — 위조 또는 시크릿 불일치
2026-08-05T05:08:11.816897037Z  [content-service]  2026-08-05 14:08:11.814 [http-nio-8082-exec-4]  WARN [traceId=6a72c53b54a0cd8148c94e6d1492050e,spanId=48c94e6d1492050e,userId=NONE] c.e.t.a.c.f.RequestLoggingFilter - [HTTP] GET /api/feeds/following 401 - 16ms
2026-08-05T05:08:17.093522976Z  [content-service]  2026-08-05 14:08:17.093 [http-nio-8082-exec-5]  WARN [traceId=6a72c541e99f03f07ca2eb617141cbba,spanId=e3368a9ae7fc0f0b,userId=NONE] c.e.t.app.auth.token.JwtParser - [auth-jwt] JWT 서명 검증 실패 — 위조 또는 시크릿 불일치
2026-08-05T05:08:17.097655775Z  [content-service]  2026-08-05 14:08:17.097 [http-nio-8082-exec-5]  WARN [traceId=6a72c541e99f03f07ca2eb617141cbba,spanId=7ca2eb617141cbba,userId=NONE] c.e.t.a.c.f.RequestLoggingFilter - [HTTP] GET /api/feeds/following 401 - 10ms
2026-08-05T05:08:22.405798105Z  [content-service]  2026-08-05 14:08:22.405 [http-nio-8082-exec-5]  WARN [traceId=6a72c546a36a7d8256eba27c48cb1edf,spanId=9024231e085f406c,userId=NONE] c.e.t.app.auth.token.JwtParser - [auth-jwt] JWT 서명 검증 실패 — 위조 또는 시크릿 불일치
2026-08-05T05:08:22.410654665Z  [content-service]  2026-08-05 14:08:22.410 [http-nio-8082-exec-5]  WARN [traceId=6a72c546a36a7d8256eba27c48cb1edf,spanId=56eba27c48cb1edf,userId=NONE] c.e.t.a.c.f.RequestLoggingFilter - [HTTP] GET /api/feeds/following 401 - 12ms
2026-08-05T05:08:27.708206770Z  [content-service]  2026-08-05 14:08:27.706 [http-nio-8082-exec-3]  WARN [traceId=6a72c54b5ad30b27563296afe1e7e33b,spanId=c539ebaa565c8388,userId=NONE] c.e.t.app.auth.token.JwtParser - [auth-jwt] JWT 서명 검증 실패 — 위조 또는 시크릿 불일치
2026-08-05T05:08:27.711751290Z  [content-service]  2026-08-05 14:08:27.711 [http-nio-8082-exec-3]  WARN [traceId=6a72c54b5ad30b27563296afe1e7e33b,spanId=563296afe1e7e33b,userId=NONE] c.e.t.a.c.f.RequestLoggingFilter - [HTTP] GET /api/feeds/following 401 - 9ms
2026-08-05T05:08:32.988789473Z  [content-service]  2026-08-05 14:08:32.988 [http-nio-8082-exec-1]  WARN [traceId=6a72c55085c357da820c4a9915cb5447,spanId=398bba71394311d2,userId=NONE] c.e.t.app.auth.token.JwtParser - [auth-jwt] JWT 서명 검증 실패 — 위조 또는 시크릿 불일치
2026-08-05T05:08:32.994147732Z  [content-service]  2026-08-05 14:08:32.993 [http-nio-8082-exec-1]  WARN [traceId=6a72c55085c357da820c4a9915cb5447,spanId=820c4a9915cb5447,userId=NONE] c.e.t.a.c.f.RequestLoggingFilter - [HTTP] GET /api/feeds/following 401 - 10ms
2026-08-05T05:08:38.286500255Z  [content-service]  2026-08-05 14:08:38.285 [http-nio-8082-exec-4]  WARN [traceId=6a72c556e21c932f34cf7ebb03efc23f,spanId=6d0093063c976749,userId=NONE] c.e.t.app.auth.token.JwtParser - [auth-jwt] JWT 서명 검증 실패 — 위조 또는 시크릿 불일치
2026-08-05T05:08:38.291697270Z  [content-service]  2026-08-05 14:08:38.291 [http-nio-8082-exec-4]  WARN [traceId=6a72c556e21c932f34cf7ebb03efc23f,spanId=34cf7ebb03efc23f,userId=NONE] c.e.t.a.c.f.RequestLoggingFilter - [HTTP] GET /api/feeds/following 401 - 9ms
2026-08-05T05:08:43.594117714Z  [content-service]  2026-08-05 14:08:43.592 [http-nio-8082-exec-2]  WARN [traceId=6a72c55bcea0bd585cc40f45d4b07f2d,spanId=0f4e2cc747f808f8,userId=NONE] c.e.t.app.auth.token.JwtParser - [auth-jwt] JWT 서명 검증 실패 — 위조 또는 시크릿 불일치
2026-08-05T05:08:43.597946777Z  [content-service]  2026-08-05 14:08:43.597 [http-nio-8082-exec-2]  WARN [traceId=6a72c55bcea0bd585cc40f45d4b07f2d,spanId=5cc40f45d4b07f2d,userId=NONE] c.e.t.a.c.f.RequestLoggingFilter - [HTTP] GET /api/feeds/following 401 - 11ms
2026-08-05T05:08:48.878831303Z  [content-service]  2026-08-05 14:08:48.878 [http-nio-8082-exec-2]  WARN [traceId=6a72c560be7ec3c9888a88e2ec519564,spanId=90aee23a733864a5,userId=NONE] c.e.t.app.auth.token.JwtParser - [auth-jwt] JWT 서명 검증 실패 — 위조 또는 시크릿 불일치
2026-08-05T05:08:48.883308295Z  [content-service]  2026-08-05 14:08:48.882 [http-nio-8082-exec-2]  WARN [traceId=6a72c560be7ec3c9888a88e2ec519564,spanId=888a88e2ec519564,userId=NONE] c.e.t.a.c.f.RequestLoggingFilter - [HTTP] GET /api/feeds/following 401 - 7ms
2026-08-05T05:08:54.188494936Z  [content-service]  2026-08-05 14:08:54.188 [http-nio-8082-exec-3]  WARN [traceId=6a72c566c485960f2f02d74a708afbe6,spanId=883993a912733784,userId=NONE] c.e.t.app.auth.token.JwtParser - [auth-jwt] JWT 서명 검증 실패 — 위조 또는 시크릿 불일치
2026-08-05T05:08:54.194152274Z  [content-service]  2026-08-05 14:08:54.193 [http-nio-8082-exec-3]  WARN [traceId=6a72c566c485960f2f02d74a708afbe6,spanId=2f02d74a708afbe6,userId=NONE] c.e.t.a.c.f.RequestLoggingFilter - [HTTP] GET /api/feeds/following 401 - 12ms
2026-08-05T05:08:59.484574633Z  [content-service]  2026-08-05 14:08:59.483 [http-nio-8082-exec-1]  WARN [traceId=6a72c56b470ef65161f77a8d7316e48c,spanId=6d404b9d2cf8c0e7,userId=NONE] c.e.t.app.auth.token.JwtParser - [auth-jwt] JWT 서명 검증 실패 — 위조 또는 시크릿 불일치
2026-08-05T05:08:59.493695823Z  [content-service]  2026-08-05 14:08:59.493 [http-nio-8082-exec-1]  WARN [traceId=6a72c56b470ef65161f77a8d7316e48c,spanId=61f77a8d7316e48c,userId=NONE] c.e.t.a.c.f.RequestLoggingFilter - [HTTP] GET /api/feeds/following 401 - 15ms
2026-08-05T05:09:04.805732846Z  [content-service]  2026-08-05 14:09:04.805 [http-nio-8082-exec-4]  WARN [traceId=6a72c570d95e8b081076a9cfe8137d53,spanId=1c6bff8a84aaa931,userId=NONE] c.e.t.app.auth.token.JwtParser - [auth-jwt] JWT 서명 검증 실패 — 위조 또는 시크릿 불일치
2026-08-05T05:09:04.811241327Z  [content-service]  2026-08-05 14:09:04.810 [http-nio-8082-exec-4]  WARN [traceId=6a72c570d95e8b081076a9cfe8137d53,spanId=1076a9cfe8137d53,userId=NONE] c.e.t.a.c.f.RequestLoggingFilter - [HTTP] GET /api/feeds/following 401 - 14ms
2026-08-05T05:09:10.086483179Z  [content-service]  2026-08-05 14:09:10.086 [http-nio-8082-exec-5]  WARN [traceId=6a72c57658ef4a4fbadd029ac7995141,spanId=2d5748736e283ae0,userId=NONE] c.e.t.app.auth.token.JwtParser - [auth-jwt] JWT 서명 검증 실패 — 위조 또는 시크릿 불일치
2026-08-05T05:09:10.090622954Z  [content-service]  2026-08-05 14:09:10.090 [http-nio-8082-exec-5]  WARN [traceId=6a72c57658ef4a4fbadd029ac7995141,spanId=badd029ac7995141,userId=NONE] c.e.t.a.c.f.RequestLoggingFilter - [HTTP] GET /api/feeds/following 401 - 9ms
2026-08-05T05:09:15.384065658Z  [content-service]  2026-08-05 14:09:15.383 [http-nio-8082-exec-5]  WARN [traceId=6a72c57b7e3829272d2de9cc031b526b,spanId=940bc0cc988251c3,userId=NONE] c.e.t.app.auth.token.JwtParser - [auth-jwt] JWT 서명 검증 실패 — 위조 또는 시크릿 불일치
2026-08-05T05:09:15.388509289Z  [content-service]  2026-08-05 14:09:15.388 [http-nio-8082-exec-5]  WARN [traceId=6a72c57b7e3829272d2de9cc031b526b,spanId=2d2de9cc031b526b,userId=NONE] c.e.t.a.c.f.RequestLoggingFilter - [HTTP] GET /api/feeds/following 401 - 10ms
2026-08-05T05:09:20.710253703Z  [content-service]  2026-08-05 14:09:20.709 [http-nio-8082-exec-3]  WARN [traceId=6a72c580c03efe3fcc709dea927fdf9a,spanId=862633bc12541afd,userId=NONE] c.e.t.app.auth.token.JwtParser - [auth-jwt] JWT 서명 검증 실패 — 위조 또는 시크릿 불일치
2026-08-05T05:09:25.994737555Z  [content-service]  2026-08-05 14:09:25.994 [http-nio-8082-exec-1]  WARN [traceId=6a72c585ac52d2cb0c79419c8ec1935c,spanId=6ffe5fc004a7b590,userId=NONE] c.e.t.app.auth.token.JwtParser - [auth-jwt] JWT 서명 검증 실패 — 위조 또는 시크릿 불일치
2026-08-05T05:09:26.000080028Z  [content-service]  2026-08-05 14:09:25.999 [http-nio-8082-exec-1]  WARN [traceId=6a72c585ac52d2cb0c79419c8ec1935c,spanId=0c79419c8ec1935c,userId=NONE] c.e.t.a.c.f.RequestLoggingFilter - [HTTP] GET /api/feeds/following 401 - 10ms
2026-08-05T05:09:36.586767886Z  [content-service]  2026-08-05 14:09:36.586 [http-nio-8082-exec-2]  WARN [traceId=6a72c590e4492822b4585ab3298124b1,spanId=e64008ffa0214e64,userId=NONE] c.e.t.app.auth.token.JwtParser - [auth-jwt] JWT 서명 검증 실패 — 위조 또는 시크릿 불일치
2026-08-05T05:09:36.591888799Z  [content-service]  2026-08-05 14:09:36.591 [http-nio-8082-exec-2]  WARN [traceId=6a72c590e4492822b4585ab3298124b1,spanId=b4585ab3298124b1,userId=NONE] c.e.t.a.c.f.RequestLoggingFilter - [HTTP] GET /api/feeds/following 401 - 10ms
```

### 메트릭 시계열

| 쿼리 | series | 점 | min | max | last | 값이 0이던 구간 |
|---|---|---:|---:|---:|---:|---|
| `hikaricp_connections_active` | `{__name__=hikaricp_connections_active, application=auth-service, cluster=yogurtte-k3s-prod, container=auth-service, instance=10.42.1.46:8090, job=auth-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=auth-service-5999bb9f5c-hmgp9, pool=HikariPool-1, service=auth-service}` | 35 | 0 | 0 | 0 | **2026-08-05T04:40:00Z ~ 2026-08-05T04:48:30Z** |
| `hikaricp_connections_active` | `{__name__=hikaricp_connections_active, application=auth-service, cluster=yogurtte-k3s-prod, container=auth-service, instance=10.42.1.48:8090, job=auth-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=auth-service-5999bb9f5c-jv2jn, pool=HikariPool-1, service=auth-service}` | 95 | 0 | 0 | 0 | **2026-08-05T04:51:30Z ~ 2026-08-05T05:15:00Z** |
| `hikaricp_connections_active` | `{__name__=hikaricp_connections_active, application=chat-service, cluster=yogurtte-k3s-prod, container=chat-service, instance=10.42.1.47:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-fdcc7c776-xf4sv, pool=HikariPool-1}` | 141 | 0 | 0 | 0 | **2026-08-05T04:40:00Z ~ 2026-08-05T05:15:00Z** |
| `hikaricp_connections_active` | `{__name__=hikaricp_connections_active, application=content-service, cluster=yogurtte-k3s-prod, container=content-service, instance=10.42.1.43:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-6995bb7d94-h2f6n, pool=HikariPool-1}` | 60 | 0 | 0 | 0 | **2026-08-05T04:40:00Z ~ 2026-08-05T04:54:45Z** |
| `hikaricp_connections_active` | `{__name__=hikaricp_connections_active, application=content-service, cluster=yogurtte-k3s-prod, container=content-service, instance=10.42.1.49:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-64b7dfc78f-kjc8w, pool=HikariPool-1}` | 40 | 0 | 0 | 0 | **2026-08-05T04:57:15Z ~ 2026-08-05T05:07:00Z** |
| `hikaricp_connections_active` | `{__name__=hikaricp_connections_active, application=content-service, cluster=yogurtte-k3s-prod, container=content-service, instance=10.42.1.50:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-5489b58cbc-2ckp6, pool=HikariPool-1}` | 29 | 0 | 0 | 0 | **2026-08-05T05:08:00Z ~ 2026-08-05T05:15:00Z** |
| `hikaricp_connections_active` | `{__name__=hikaricp_connections_active, application=content-service, cluster=yogurtte-k3s-prod, container=content-service, instance=10.42.3.42:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-6995bb7d94-nq9l2, pool=HikariPool-1}` | 75 | 0 | 0 | 0 | **2026-08-05T04:40:00Z ~ 2026-08-05T04:58:30Z** |
| `hikaricp_connections_active` | `{__name__=hikaricp_connections_active, application=content-service, cluster=yogurtte-k3s-prod, container=content-service, instance=10.42.3.44:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-64b7dfc78f-q8qnn, pool=HikariPool-1}` | 16 | 0 | 1 | 0 | **2026-08-05T05:00:15Z ~ 2026-08-05T05:01:00Z, 2026-08-05T05:02:15Z ~ 2026-08-05T05:04:00Z** |
| `hikaricp_connections_pending` | `{__name__=hikaricp_connections_pending, application=auth-service, cluster=yogurtte-k3s-prod, container=auth-service, instance=10.42.1.46:8090, job=auth-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=auth-service-5999bb9f5c-hmgp9, pool=HikariPool-1, service=auth-service}` | 35 | 0 | 0 | 0 | **2026-08-05T04:40:00Z ~ 2026-08-05T04:48:30Z** |
| `hikaricp_connections_pending` | `{__name__=hikaricp_connections_pending, application=auth-service, cluster=yogurtte-k3s-prod, container=auth-service, instance=10.42.1.48:8090, job=auth-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=auth-service-5999bb9f5c-jv2jn, pool=HikariPool-1, service=auth-service}` | 95 | 0 | 0 | 0 | **2026-08-05T04:51:30Z ~ 2026-08-05T05:15:00Z** |
| `hikaricp_connections_pending` | `{__name__=hikaricp_connections_pending, application=chat-service, cluster=yogurtte-k3s-prod, container=chat-service, instance=10.42.1.47:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-fdcc7c776-xf4sv, pool=HikariPool-1}` | 141 | 0 | 0 | 0 | **2026-08-05T04:40:00Z ~ 2026-08-05T05:15:00Z** |
| `hikaricp_connections_pending` | `{__name__=hikaricp_connections_pending, application=content-service, cluster=yogurtte-k3s-prod, container=content-service, instance=10.42.1.43:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-6995bb7d94-h2f6n, pool=HikariPool-1}` | 60 | 0 | 0 | 0 | **2026-08-05T04:40:00Z ~ 2026-08-05T04:54:45Z** |
| `hikaricp_connections_pending` | `{__name__=hikaricp_connections_pending, application=content-service, cluster=yogurtte-k3s-prod, container=content-service, instance=10.42.1.49:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-64b7dfc78f-kjc8w, pool=HikariPool-1}` | 40 | 0 | 0 | 0 | **2026-08-05T04:57:15Z ~ 2026-08-05T05:07:00Z** |
| `hikaricp_connections_pending` | `{__name__=hikaricp_connections_pending, application=content-service, cluster=yogurtte-k3s-prod, container=content-service, instance=10.42.1.50:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-5489b58cbc-2ckp6, pool=HikariPool-1}` | 29 | 0 | 0 | 0 | **2026-08-05T05:08:00Z ~ 2026-08-05T05:15:00Z** |
| `hikaricp_connections_pending` | `{__name__=hikaricp_connections_pending, application=content-service, cluster=yogurtte-k3s-prod, container=content-service, instance=10.42.3.42:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-6995bb7d94-nq9l2, pool=HikariPool-1}` | 75 | 0 | 0 | 0 | **2026-08-05T04:40:00Z ~ 2026-08-05T04:58:30Z** |
| `hikaricp_connections_pending` | `{__name__=hikaricp_connections_pending, application=content-service, cluster=yogurtte-k3s-prod, container=content-service, instance=10.42.3.44:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-64b7dfc78f-q8qnn, pool=HikariPool-1}` | 16 | 0 | 0 | 0 | **2026-08-05T05:00:15Z ~ 2026-08-05T05:04:00Z** |
| `rate(jvm_gc_pause_seconds_sum[5m])` | `{action=end of major GC, application=chat-service, cause=Allocation Failure, cluster=yogurtte-k3s-prod, container=chat-service, gc=MarkSweepCompact, instance=10.42.1.47:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-fdcc7c776-xf4sv}` | 141 | 0 | 0 | 0 | **2026-08-05T04:40:00Z ~ 2026-08-05T05:15:00Z** |
| `rate(jvm_gc_pause_seconds_sum[5m])` | `{action=end of minor GC, application=auth-service, cause=G1 Evacuation Pause, cluster=yogurtte-k3s-prod, container=auth-service, gc=G1 Young Generation, instance=10.42.1.46:8090, job=auth-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=auth-service-5999bb9f5c-hmgp9, service=auth-service}` | 47 | 0 | 0 | 0 | **2026-08-05T04:40:00Z ~ 2026-08-05T04:51:30Z** |
| `rate(jvm_gc_pause_seconds_sum[5m])` | `{action=end of minor GC, application=auth-service, cause=G1 Evacuation Pause, cluster=yogurtte-k3s-prod, container=auth-service, gc=G1 Young Generation, instance=10.42.1.48:8090, job=auth-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=auth-service-5999bb9f5c-jv2jn, service=auth-service}` | 91 | 0 | 0.000 | 0 | **2026-08-05T04:52:30Z ~ 2026-08-05T05:00:15Z, 2026-08-05T05:05:30Z ~ 2026-08-05T05:15:00Z** |
| `rate(jvm_gc_pause_seconds_sum[5m])` | `{action=end of minor GC, application=auth-service, cause=Metadata GC Threshold, cluster=yogurtte-k3s-prod, container=auth-service, gc=G1 Young Generation, instance=10.42.1.48:8090, job=auth-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=auth-service-5999bb9f5c-jv2jn, service=auth-service}` | 91 | 0 | 0 | 0 | **2026-08-05T04:52:30Z ~ 2026-08-05T05:15:00Z** |
| `rate(jvm_gc_pause_seconds_sum[5m])` | `{action=end of minor GC, application=chat-service, cause=Allocation Failure, cluster=yogurtte-k3s-prod, container=chat-service, gc=Copy, instance=10.42.1.47:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-fdcc7c776-xf4sv}` | 141 | 0.000 | 0.000 | 0.000 | — |
| `rate(jvm_gc_pause_seconds_sum[5m])` | `{action=end of minor GC, application=content-service, cause=Allocation Failure, cluster=yogurtte-k3s-prod, container=content-service, gc=Copy, instance=10.42.1.43:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-6995bb7d94-h2f6n}` | 72 | 0 | 0.000 | 0.000 | **2026-08-05T04:43:00Z ~ 2026-08-05T04:48:45Z, 2026-08-05T04:53:00Z ~ 2026-08-05T04:53:45Z** |
| `rate(jvm_gc_pause_seconds_sum[5m])` | `{action=end of minor GC, application=content-service, cause=Allocation Failure, cluster=yogurtte-k3s-prod, container=content-service, gc=Copy, instance=10.42.1.49:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-64b7dfc78f-kjc8w}` | 48 | 0 | 0.000 | 0 | **2026-08-05T04:58:15Z ~ 2026-08-05T05:02:00Z, 2026-08-05T05:06:15Z ~ 2026-08-05T05:10:00Z** |
| `rate(jvm_gc_pause_seconds_sum[5m])` | `{action=end of minor GC, application=content-service, cause=Allocation Failure, cluster=yogurtte-k3s-prod, container=content-service, gc=Copy, instance=10.42.1.50:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-5489b58cbc-2ckp6}` | 25 | 0 | 0 | 0 | **2026-08-05T05:09:00Z ~ 2026-08-05T05:15:00Z** |
| `up` | `{__name__=up, app=node-exporter, cluster=yogurtte-k3s-prod, component=metrics, container=node-exporter, instance=ip-172-31-40-241, job=integrations/node_exporter, k8s_cluster_name=yogurtte-k3s-prod, namespace=monitoring, pod=grafana-k8s-monitoring-node-exporter-cph5l, source=kubernetes, workload=DaemonSet/grafana-k8s-monitoring-node-exporter}` | 141 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, app=node-exporter, cluster=yogurtte-k3s-prod, component=metrics, container=node-exporter, instance=ip-172-31-45-39, job=integrations/node_exporter, k8s_cluster_name=yogurtte-k3s-prod, namespace=monitoring, pod=grafana-k8s-monitoring-node-exporter-9kvqt, source=kubernetes, workload=DaemonSet/grafana-k8s-monitoring-node-exporter}` | 141 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, cluster=yogurtte-k3s-prod, container=auth-service, instance=10.42.1.46:8090, job=auth-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=auth-service-5999bb9f5c-hmgp9}` | 35 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, cluster=yogurtte-k3s-prod, container=auth-service, instance=10.42.1.48:8090, job=auth-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=auth-service-5999bb9f5c-jv2jn}` | 95 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, cluster=yogurtte-k3s-prod, container=chat-service, instance=10.42.1.47:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-fdcc7c776-xf4sv}` | 141 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, cluster=yogurtte-k3s-prod, container=content-service, instance=10.42.1.43:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-6995bb7d94-h2f6n}` | 60 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, cluster=yogurtte-k3s-prod, container=content-service, instance=10.42.1.49:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-64b7dfc78f-kjc8w}` | 40 | 1 | 1 | 1 | — |
| `up` | `{__name__=up, cluster=yogurtte-k3s-prod, container=content-service, instance=10.42.1.50:8090, job=content-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=content-service-5489b58cbc-2ckp6}` | 29 | 1 | 1 | 1 | — |
| `mongodb_up` | `{__name__=mongodb_up, cluster=yogurtte-k3s-prod, instance=infra-server, job=mongodb, k8s_cluster_name=yogurtte-k3s-prod}` | 141 | 1 | 1 | 1 | — |
| `kafka_brokers` | `{__name__=kafka_brokers, cluster=yogurtte-k3s-prod, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod}` | 141 | 1 | 1 | 1 | — |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=chat-service-fcm-tokens, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=0, topic=user.fcm-tokens}` | 141 | 0 | 0 | 0 | **2026-08-05T04:40:00Z ~ 2026-08-05T05:15:00Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=chat-service-fcm-tokens, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=1, topic=user.fcm-tokens}` | 141 | 0 | 0 | 0 | **2026-08-05T04:40:00Z ~ 2026-08-05T05:15:00Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=chat-service-fcm-tokens, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=2, topic=user.fcm-tokens}` | 141 | 0 | 0 | 0 | **2026-08-05T04:40:00Z ~ 2026-08-05T05:15:00Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=chat-service-notification-settings, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=0, topic=user.notification-settings}` | 141 | 0 | 0 | 0 | **2026-08-05T04:40:00Z ~ 2026-08-05T05:15:00Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=chat-service-notification-settings, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=1, topic=user.notification-settings}` | 141 | 0 | 0 | 0 | **2026-08-05T04:40:00Z ~ 2026-08-05T05:15:00Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=chat-service-notification-settings, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=2, topic=user.notification-settings}` | 141 | 0 | 0 | 0 | **2026-08-05T04:40:00Z ~ 2026-08-05T05:15:00Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=db-writer, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=0, topic=chat.messages}` | 141 | 0 | 0 | 0 | **2026-08-05T04:40:00Z ~ 2026-08-05T05:15:00Z** |
| `kafka_consumergroup_lag` | `{__name__=kafka_consumergroup_lag, cluster=yogurtte-k3s-prod, consumergroup=db-writer, instance=infra-server, job=kafka, k8s_cluster_name=yogurtte-k3s-prod, partition=1, topic=chat.messages}` | 141 | 0 | 0 | 0 | **2026-08-05T04:40:00Z ~ 2026-08-05T05:15:00Z** |
| `websocket_active_users` | `{__name__=websocket_active_users, application=chat-service, cluster=yogurtte-k3s-prod, container=chat-service, instance=10.42.1.47:8090, job=chat-service, k8s_cluster_name=yogurtte-k3s-prod, namespace=default, pod=chat-service-fdcc7c776-xf4sv}` | 141 | 0 | 0 | 0 | **2026-08-05T04:40:00Z ~ 2026-08-05T05:15:00Z** |

값이 0이던 구간은 굵게 표시했다 — 프로세스가 사라져 시계열이 꺾인 것이 유일한 신호인 장애가 있다.

