# 후보(Incident)는 어떻게 만들어지는가 — 실제 응답으로 따라가기

> **이 문서는 설명서다.** 왜 이 구조인지는 [incident-clustering.md](incident-clustering.md),
> 규칙 원문은 [incident-clustering-spec.md](incident-clustering-spec.md)에 있다.
> 여기서는 **저장된 실제 응답 하나가 후보 목록이 될 때까지**를 단계별로 따라간다.
> 인용한 JSON·후보 줄은 전부 `reports/raw/` · `reports/rounds/`의 실측 기록이다.
>
> 맨 끝의 B-44·B-45는 요약만 두고 **원문은 [round-6/README.md](../round-6/README.md)** 에 있다.

---

## 0. 한 장으로

```
질문 "어젯밤 알림이 안 갔어"
   │
   ▼  TimeExpressionParser            시간 창 계산 (코드, 결정적)
   ▼  Surveyor                        고정 쿼리 4종을 창 전체에 던진다
   ▼  SignalExtractor                 응답에서 Signal 을 뽑는다        ← 1단계
   ▼  Incident.cluster()              Signal 을 후보로 묶는다          ← 2~5단계
   ▼  LLM ①                           후보를 고른다 (탐색)
   ▼  Collector                       고른 후보의 traceId·창으로 원문 수집
   ▼  LLM ②                           원인 분석
```

**탐색 LLM은 로그 원문도 span 원문도 받지 않는다.** 후보 목록과 채널별 도달 요약,
무신호 목록만 본다(`include-raw=false`, 2026-08-04 기본화).

---

## 1단계 — 응답에서 Signal 을 뽑는다

Signal 은 *"언제 무엇이 이상했나"* 한 건이다. 8필드이고, 앞의 세 개가 뒤에서 키가 된다.

```java
Signal(from, to, channel, precision, resource, signature, what, ref)
                 └────── key = channel|resource|signature ──────┘
```

### Tempo — 검색에 걸린 트레이스 한 건이 Signal 하나

```json
{ "traceID": "6a7037546e34…",
  "rootServiceName": "content-service",
  "rootTraceName": "http post /feeds/{feedId}/comments",
  "startTimeUnixNano": "1785739092870260000",
  "durationMs": 11643,
  "serviceStats": { "auth-service":    {"spanCount": 4},
                    "chat-service":    {"spanCount": 14},
                    "content-service": {"spanCount": 28} } }
```

| Signal 필드 | 값 |
|---|---|
| `from` / `to` | `startTimeUnixNano` / `from + durationMs` |
| `channel` / `precision` | `TEMPO` / **`EXACT`** (span은 ms 정확) |
| `resource` / `signature` | `content-service` / `http post /feeds/{feedId}/comments` |
| `ref` | `6a7037546e34…` ← **traceId를 갖는 유일한 채널** |

**span 은 안 온다.** 검색 응답은 트레이스당 루트 요약 한 줄이고, 폭포(span 전부)는
분석 단계에서 `/api/traces/{id}`로 따로 받는다.

### Loki — 0이 아닌 5분 버킷 하나가 Signal 하나

```json
[ {"metric": {"service_name": "chat-service"},    "values": [[1785990900,"28"], [1785991200,"7"]]},
  {"metric": {"service_name": "content-service"}, "values": [[1785990900,"79"], [1785991200,"17"],
                                                             [1785991800,"370"]]} ]
```

| Signal 필드 | 값 |
|---|---|
| `from` / `to` | **`버킷시각 − 5분` / `버킷시각`** |
| `channel` / `precision` | `LOKI` / **`BUCKET`** (집계 해상도만큼 흐림) |
| `resource` / `signature` | `chat-service` / **`ERROR/WARN` 고정** |
| `ref` | `"loki-rate"` ← traceId가 아니다 |

**`from`을 5분 당기는 것이 중요하다.** 쿼리가 `count_over_time(...[5m])`이므로 그 값은
*"직전 5분 사이에 28건"* 이라는 뜻이다. 점으로 읽으면 최대 5분 어긋나고,
실제로 회차 리포트가 버킷 시각을 사건 시각으로 읽은 사례가 있다.

### Mimir — 시리즈마다 검출기 셋을 돌린다

| 검출기 | 무엇을 | 조건 |
|---|---|---|
| ① 0 구간 | `mongodb_up 가 0이었다 (07:53:31~07:58:31)` | **`zero-is-abnormal` 목록에 있는 지표만** |
| ② 값 변화 | `lag 0 → 24` | 인접 샘플 비교 |
| ③ 결측 | 샘플이 끊긴 구간 | 응답에서 추론한 실제 step 기준 |

`resource`는 `job` → `application` → `instance` 폴백, `signature`는 **지표명만**,
`ref`는 쿼리 문자열이다. 시리즈 라벨(`topic`·`partition`·`consumergroup`)은
**설명(`what`)에만** 들어가고 키에는 안 들어간다.

> **①을 목록으로 제한한 이유** — `lag=0`은 "안 밀림"이라 정상인데 이것을 신호로 만들었더니
> 정상 파티션 36개가 각각 60분짜리 "0이었다" 신호가 됐고, 지표명이 키라서 41개가
> **후보 하나로 뭉쳐 조사 창이 스윕 창 전체로 벌어졌다**
> (CH-1 회차 3 실측: 컨텍스트 363,268자 · $2.78 · 정답 트레이스가 수집 상한에 밀림).

---

## 2단계 — 라벨 3축으로 통에 나눈다 (시간을 안 본다)

```
키 = 채널 | 리소스 | 지문
      ↑      ↑        ↑
      │      │        Tempo=엔드포인트 · Loki=ERROR/WARN · Mimir=지표명
      │      Tempo=루트 서비스 · Loki=service_name · Mimir=job/application/instance
      TEMPO / LOKI / MIMIR
```

**시간이 키에 없어서 교차에 면역이다.**

```
   14:20  chat  Mongo 거부   ┐
   14:21  auth  JWT 실패     │  시간순으로 섞여 들어와도
   14:22  chat  Mongo 거부   │  라벨이 다르면 애초에 다른 통이라
   14:23  auth  JWT 실패     ┘  섞일 일이 없다
```

### 축마다 무엇을 막나

```
▸ 리소스 축이 없으면 ────────────────────────────────
   CH-3 실측: lag 신호 하나가 05:00~05:20에 걸쳐 있었다
   시간만 보면  04:55 ──────────────── 05:25  전부 한 덩어리
   리소스 축     lag 는 resource=kafka  →  chat 신호와 연결이 끊긴다

▸ 지문 축이 없으면 ─────────────────────────────────
   chat-service  POST /notifications  30초
   chat-service  GET  /rooms          30초
   합치면 "알림만 느린가, 서비스 전체가 느린가"를 구별할 수 없다

▸ 채널 축이 없으면 ─────────────────────────────────
   chat-service 의 Loki 신호(ERROR 19건)  와  Tempo 신호(지연 4건)
   합치면 "에러로는 안 잡혔는데 지연으로 잡혔다"가 사라진다
   CH-3가 정확히 그 문항이다 — 에러 검색 0건, 지연 검색만 걸렸다
```

---

## 3단계 — 통 **안에서만** 60초로 끊는다

기준은 직전 신호의 시작이 아니라 **지금까지 나온 끝 중 가장 먼 것(`reach`)** 이다.
신호가 점이 아니라 구간이라, 시작 시각만 비교하면 긴 구간 신호를 잘라먹는다.

```
[S1 S2]  S1 끝 14:23:40 → S2 시작 14:23:50   간격 10초  → 안 끊김
         S2가 16:00이었다면                             → 끊긴다 (같은 엔드포인트라도 다른 사건)
```

---

## 4단계 — traceId를 공유하면 합친다

```java
boolean sharesTraceWith(Set<String> ids) {
    return !ids.isEmpty() && !Collections.disjoint(traceIds, ids);
}
```

```
[TEMPO chat POST /notifications]  traceId {6a68…, 91bd…}  ┐
[TEMPO content POST /posts]       traceId {6a68…}         ┘  겹친다 → 합친다
                                    ↑ 리소스도 지문도 다른데 합쳐졌다. traceId가 결정적 근거라서

[LOKI …]   traceId {}   ← ref 가 "loki-rate"
[MIMIR …]  traceId {}   ← ref 가 쿼리 문자열
             ↑ 빈 집합은 아무와도 안 이어진다
```

**시간 근접만으로는 절대 병합하지 않는다.** Mimir 신호와 Tempo 신호는 공유 식별자가 없어
코드가 인과를 알 수 없고, 토폴로지(*"auth가 죽으면 content가 영향받는다"*)를 병합 근거로
쓰면 **채점 대상인 정답 구조를 코드에 심는 것**이 된다.

---

## 5단계 — 시각순 번호 + 겹치는 것 표시

```
- 같은 시각의 다른 후보: INC-1, INC-3, INC-5  (인과 여부는 판단하지 않았다)
```

**병합이 아니라 표시다.** 묶는 판단은 LLM 몫이다.

### 접기 — 지우는 것이 아니다

같은 문구가 3건 이상이면 한 줄로 접고, **횟수·시각 범위·평균 간격**을 붙인다.

```
- chat-service security filterchain before 30,013ms (slow 채널)   [x13회 · 07:52:44~07:55:14 · 평균 11초 간격]
```

반복 횟수 자체가 근거이기 때문이다 — 스케줄러 실패 6건은 "60초 주기 3회 × 2파드"였고
리포트가 그래서 *"3주기 연속 실패"* 로 결론냈다. 문구가 서로 다르면 접지 않는다.

---

## 전체 실례 — CH-1 회차 5b: 신호 42개 → 후보 13개

출처: `reports/rounds/ch-1-round5b.json`

### 신호 42개

```
TEMPO (20)   content 댓글 8,485ms slow · 31,370ms error
             chat filterchain 30,012~30,025ms ×13
             chat filterchain 21,996 / 14,584 / 4,511ms
             <root span not yet received> 20,202 / 5,710ms

LOKI  (12)   content 40·24·12·2·2건 (07:30~07:55) · 2건 (08:10~08:15)
             auth    4건 (07:40~07:45)
             chat    2·4·26·42건 (07:40~08:00) · 4건 (08:20~08:25)

MIMIR (10)   mongodb_up 1→0 · 0이었다 · 0→1
             lag{user.notifications p3} 0→1 · 1→0 · 0→24 · 24→25 · 25→0
             lag{user.notifications.dlq} 0→1 · 1→0
```

`up` · `kafka_brokers` · `websocket_active_users`는 **이상 0건**이라 신호가 안 나왔고,
그 사실은 "채널별 도달 요약"에 `이상 신호 0건`으로 남았다.

### 2단계 — 통 8개

```
LOKI  | content-service | ERROR/WARN                    6
LOKI  | auth-service    | ERROR/WARN                    1
LOKI  | chat-service    | ERROR/WARN                    5
MIMIR | mongodb         | mongodb_up                    3     ← 검출기가 달라도 한 통
MIMIR | kafka           | kafka_consumergroup_lag       7     ← 토픽 2종이 한 통
TEMPO | content-service | POST /battles/…/comments      2
TEMPO | chat-service    | security filterchain before   16
TEMPO | ?               | <root span not yet received>  2
```

### 3단계 — 시간으로 끊긴 자리

```
LOKI content   07:30 07:35 07:40 07:45 07:50 │·····40분·····│ 08:10
LOKI chat      07:40 07:45 07:50 07:55       │·····20분·····│ 08:20
MIMIR lag      07:48 ── 07:58 ─ 08:03        │·····10분·····│ 08:13 ─ 08:18 ─ 08:23
MIMIR mongodb  07:48 ─ 07:53 ─ 07:58 ─ 08:03        빈틈 없음 → 안 끊김
TEMPO chat FC  07:52:44 …13건… 07:55:14      │·····27분·····│ 08:22:07
TEMPO content  07:52:24 │··5분··│ 07:57:12                    60초 넘음 → 끊김
```

### 4단계 — 병합 0건

TEMPO 후보 다섯의 traceId 집합이 서로 겹치지 않았고, LOKI·MIMIR는 집합이 비어 있다.

### 결과

| 후보 | 채널 | 구간 | 접힌 신호 |
|---|---|---|---:|
| INC-1 content ERROR/WARN | LOKI | 07:30~07:55 | 5 |
| INC-2 auth ERROR/WARN | LOKI | 07:40~07:45 | 1 |
| INC-3 chat ERROR/WARN | LOKI | 07:40~08:00 | 4 |
| INC-4 **mongodb_up** | MIMIR | 07:48~08:03 | 3 |
| INC-5 **lag** | MIMIR | 07:48~08:03 | 4 |
| INC-6 댓글 8,485ms slow | TEMPO | 07:52:24 | 1 |
| INC-7 **filterchain 30초** | TEMPO | 07:52~07:55 | **13** |
| INC-8 댓글 31,370ms error | TEMPO | 07:57:12 | 1 |
| INC-9 content ERROR/WARN | LOKI | 08:10~08:15 | 1 |
| INC-10 **lag 0→24→25→0** | MIMIR | 08:13~08:28 | 3 |
| INC-11 chat ERROR/WARN | LOKI | 08:20~08:25 | 1 |
| INC-12 filterchain 22/14/4초 | TEMPO | 08:22 | 3 |
| INC-13 `<root span not yet received>` | TEMPO | 08:22 | 2 |

### 이 목록에서 읽히는 것 넷

**① 접기가 지문을 만든다.** INC-7은 트레이스 13건이 한 줄로 접혔는데
`30,013 / 30,013 / 30,014 / … / 30,025ms` — **편차 12ms의 균일한 30초는 타임아웃 상한**이다.
13건이 흩어져 있었으면 안 보인다. 리포트가 *"알림 푸시 경로가 진입 단계에서 통째로 막힌 지문"* 의
근거로 그대로 썼다.

**② 시간축이 "재현"을 드러낸다.** 07:5x대와 08:2x대가 같은 지문으로 두 번 선다
(INC-7↔12, INC-5↔10, INC-3↔11). 합쳤으면 *"두 번 일어났다"* 가 사라진다.

**③ 같은 엔드포인트가 채널로 갈렸다.** INC-6(slow 8.5초)과 INC-8(error 31초)은 같은 댓글
API인데 5분 간격에 채널이 다르다. LLM이 INC-6을 *"INC-8 직전의 열화 선행 신호"* 로 읽었다.

**④ 코드는 끝까지 인과를 안 붙였다.** INC-4(Mongo 다운)와 INC-5(lag)는 구간이
**글자 그대로 동일한데** 안 합쳐졌다. 13개 중 10개를 골라 하나의 서사로 읽은 것은 LLM이다.

---

## 지금 아는 한계

| | 상태 |
|---|---|
| **Loki 지문이 하나** | `ERROR/WARN` 고정이라 한 서비스의 성격 다른 예외가 후보 하나로 뭉친다 → **B-45** |
| **Tempo는 루트만** | `content → kafka → chat` 도 루트 하나로 보였다 → **B-44 (적용)** |
| **채널 간 병합 근거 없음** | traceId가 Tempo 신호에만 있다. 로그 줄에는 있지만 집계 쿼리로는 못 가져온다 → **미착수** |
| **깨진 지문** | `<root span not yet received>` 가 그대로 군집 키가 된다 (INC-13) → **미등재** |

### B-44 — 트레이스가 지나간 서비스 (적용 · 재생 검증 완료 · 점수 효과 미측정)

#### 무엇이 문제였나

탐색 단계는 Tempo **검색 목록**만 받는다. 목록의 한 줄은 트레이스 하나의 **루트 요약**이고,
폭포(span 전부)는 분석 단계에서 `/api/traces/{id}`로 따로 받는다. 그래서 이런 트레이스도

```
content-service ─▶ kafka ─▶ chat-service   (30초, 에러는 chat 에서)
```

후보에는 `content-service` 하나로만 찍혔다. 상류·하류를 되짚을 경로가 탐색 단계에 없다.

#### 첫 판단이 틀렸다

*"상류를 보려면 span을 받아야 하고 그건 탐색 단계에 너무 비싸다"* 고 봤다.
**저장 응답을 열어 보니 이미 들어 있었다.**

```json
{ "traceID": "6a7037546e34…",
  "rootServiceName": "content-service",
  "rootTraceName": "http post /feeds/{feedId}/comments",
  "durationMs": 11643,
  "serviceStats": { "auth-service":    {"spanCount": 4},
                    "chat-service":    {"spanCount": 14},
                    "content-service": {"spanCount": 28} } }
```

`reports/raw/*tempo-search*` 전수 주사 — **트레이스 645건 중 52건이 2개 이상**, 최대 3서비스.
새 쿼리도, 추가 호출도, 추가 토큰도 없다. **파싱만 안 하고 있었다.**

#### 무엇을 바꿨나

```java
// SurveyResult.toHit — 마지막 인자 하나 추가
new Evidence.TraceHit(..., startedAt, trusted, serviceStatsOf(node));
```

```java
// SignalExtractor.fromTraces — 설명 문자열 끝에만 붙인다
"%s %s %,dms (%s 채널)%s".formatted(root, name, durationMs, channel,
        hit.crossServiceText().isEmpty() ? "" : "  [지나간 서비스: " + hit.crossServiceText() + "]")
```

**지문(군집 키)에는 넣지 않는다.** 키에 넣으면 같은 엔드포인트가 상류 조합마다 다른 후보로
흩어진다 — `content+chat+auth`를 지난 요청과 `content+chat`만 지난 요청이 별개 후보가 된다.
Mimir 시리즈 라벨을 키에서 뺀 것과 같은 이유다(라벨을 키에 넣으면 44개 시리즈 → 44개 후보).

```
키 = TEMPO | content-service | http post /feeds/{feedId}/comments      ← 전·후 동일
```

**`errorCount`가 0이면 적지 않는다.** 어느 서비스에서 에러가 났는지가 신호인데,
0을 전부 적으면 그 하나가 묻힌다.

#### 실측 — 저장 스윕 재생 (주입·배포 불필요)

CH-1 회차 5b의 스윕 원본 8개(`reports/raw/scan-1785914911*`)를 지금 코드로 다시 태웠다.
그때 나온 후보 13개는 `reports/rounds/ch-1-round5b.json`에 박제돼 있다.

```
신호 42개 → 후보 13개      (박제된 회차 5b: 후보 13개)
```

| 검증 항목 | 기대 | 결과 |
|---|---|---|
| 후보 **개수** | 불변(13) — 변하면 지문에 샌 것 | **13 = 13 통과** |
| 신호 개수 | 불변(42) | **42 = 42 통과** |
| 설명 증가분 | 몇백 자 | **124자 / 후보 2건** (탐색 컨텍스트 7,700자대 → 약 +1.6%) |

새로 보이게 된 두 줄이다.

```
INC-6  content-service | http post /battles/{battleId}/items/{itemId}/comments
- 8,485ms (slow 채널)  [지나간 서비스: auth-service 4 · chat-service 14 · content-service 18]

INC-8  content-service | http post /battles/{battleId}/items/{itemId}/comments
- 31,370ms (error 채널)  [지나간 서비스: chat-service 35 (err 10) · content-service 15]
                                        ↑ 에러 10건이 chat 에서 났다
```

박제된 회차 5b에서는 두 줄 모두 `content-service`밖에 없었다. **31초짜리 실패의 에러가
실제로는 chat에서 났다는 사실이 탐색 단계에 처음 도달했다.** 그 회차 리포트는 INC-8을
*"댓글 쓰기 자체가 실패, 알림 이벤트가 발행되지 않았을 수 있음"* 으로 읽었는데,
chat 35 span·에러 10건이 보였다면 다르게 읽혔을 여지가 있다 — **그것은 미측정이다.**

> **재생 중에 잡은 하네스 함정 하나.** 첫 재생이 후보 **14개**를 냈다. B-44 때문이 아니었다.
> `reports/raw/`의 파일명은 쿼리를 파일명으로 쓸 수 있게 소독한 형태다.
>
> ```
> 파일명    min_over_time_mongodb_up_5m__
> 실제 쿼리 min_over_time(mongodb_up[5m])
> ```
>
> 소독된 이름을 그대로 쿼리 키로 넘기면 `metricNameOf()`가 지표명을
> `min_over_time_mongodb_up_5m__`로 읽어 `zero-is-abnormal` 목록과 안 맞고,
> **`mongodb_up 가 0이었다` 신호가 통째로 사라진다.** 그러면 07:53~07:58 구멍이 60초를 넘어
> mongodb 후보가 둘로 갈려 14개가 된다. **코드 회귀가 아니라 재생 도구 결함이었다** —
> 저장 원본으로 회차를 재생할 때는 쿼리 문자열을 반드시 복원해야 한다.

#### 반증 조건

- 후보 **개수**가 변하면 지문에 샌 것이다 → 테스트 `지문에는_넣지_않는다`가 회귀를 잡는다
- 상류가 보이는데도 탐색 선택이 안 바뀌면 문제는 정보량이 아니라 프롬프트다
- `serviceStats`가 없는 옛 응답에서 깨지면 안 된다 → 테스트 `serviceStats가_없는_예전_응답도_그대로_돈다`

#### 남는 한계

`serviceStats`는 **서비스별 span 수와 에러 수만** 준다. 호출 **방향**(`content → chat`인지
`chat → content`인지)은 없다. 방향은 `parentSpanId`가 필요하고 그건 span 원문이라
분석 단계의 호출 그래프(B-28) 몫이다. 탐색에서는 *"이 서비스들이 관여했다"* 까지다.

---

### B-45 — 로그 후보의 지문 (코드 적용 · **기본 꺼짐** · 도달 확인 결과 **효과 없음**)

#### 무엇이 문제였나

군집 키가 `채널 | 리소스 | 지문`인데 **Loki만 지문이 항상 `ERROR/WARN`** 이다.
그래서 한 서비스에서 성격이 다른 예외가 동시에 나면 후보 하나로 뭉친다.

실제 응답이 이렇다.

```json
[ {"metric": {"service_name": "chat-service"},    "values": [[1785990900,"28"], [1785991200,"7"]]},
  {"metric": {"service_name": "content-service"}, "values": [[1785990900,"79"], [1785991200,"17"],
                                                             [1785991800,"370"]]} ]
```

라벨이 `service_name` 하나뿐이다. 같은 창의 실제 로그 줄에는 두 종류가 섞여 있다.

```
org.springframework.dao.QueryTimeoutException: Redis command timed out
    at …LettuceExceptionConverter.convert(LettuceExceptionConverter.java:68)
[2m…[33m WARN [traceId=NONE,…] … [chat-service] [llEventLoop-6-2]
```

**`28건`이라는 숫자만으로는 무엇이 28건인지 알 방법이 없다.**

#### 원본을 줘도 안 되는 것이 실측됐다

첫 판단은 *"후보가 부실하면 원본 JSON이 메운다"* 였다. 채널별로 재 봤더니 원본이 후보보다
더 주는 것은 **Mimir 시리즈 라벨**과 **파손된 트레이스 행** 둘뿐이었고 **Loki는 0**이었다.

| 채널 | 원본에만 있는 것 | 크기 |
|---|---|---|
| Mimir | `topic`·`consumergroup`·`partition` 라벨 · 전체 값 곡선 | 32 KB |
| Tempo | `trusted=false` 행 · `serviceStats` · span 속성 | 1.4 KB |
| **Loki** | **없음** | **0** |

Loki 원본 4,223 B 중 실제 데이터는 **146 B**이고 그 146 B가 곧 후보에 적힌 건수다.
나머지 4,077 B는 Loki가 붙이는 쿼리 성능 통계(`stats`)다.

즉 **후보 포맷 문제가 아니라 스윕 쿼리의 `sum by`가 내용을 지운 결과**이고, 구조적이다.

#### 코드는 원래 할 줄 알았다

```java
String logSignature() {
    String exc = label("exc");
    return exc == null ? "ERROR/WARN" : exc;   // ← 라벨이 오면 그것을 지문으로 쓴다
}
```

**라벨을 만드는 쿼리가 없었을 뿐이다.** 그래서 바꾼 것은 쿼리를 하나 더 던지는 것뿐이다.

```
기존 곡선 (총 건수)   sum by (service_name) (count_over_time({...} |~ "ERROR|WARN" [5m]))
                      ─── 계속 던진다. 총량은 이쪽이 계속 책임진다

지문 곡선 (exc 별)    sum by (service_name, exc) (count_over_time(
                        {...} |~ "ERROR|WARN"
                        | regexp "(?P<exc>[a-z][\w.]*\.[A-Z]\w*(?:Exception|Error))" [5m]))
                      ─── rca.survey.log-signature-query 가 비어 있으면 아예 안 던진다
```

```java
// SignalExtractor.extract — 지문 곡선이 있으면 그걸 쓰고, 비면 총 건수로 되돌아간다
List<Signal> logSignals = fromLogRates(survey.logSignatureRatesJson(), lookback);
if (logSignals.isEmpty()) {
    logSignals = fromLogRates(survey.logRatesJson(), lookback);
}
```

#### 전 · 후

```
전   ## INC-3  chat-service | ERROR/WARN              23건
                                                       ↑ Redis 타임아웃 19 + Mongo 거부 4가 뭉쳐 있다

후   ## INC-3  chat-service | QueryTimeoutException    19건
     ## INC-4  chat-service | MongoSocketOpenException  4건
```

#### 실측 — 쿼리는 도는데 라벨이 거의 안 붙는다 (2026-08-10)

라이브 Loki에 그대로 던져 두 창에서 쟀다.

```
창 2026-08-05T07:28~08:28Z  (CH-1 회차 5b와 같은 창)
  {exc: com.mongodb.MongoTimeoutException, service_name: chat-service}  → 2, 5
  {exc: mc.e.t.a.c.e.GlobalException,      service_name: auth-service}  → 2
  {service_name: auth-service}                                          → 2
  {service_name: chat-service}                                          → 2, 4, 24, 37, 4
  {service_name: content-service}                                       → 40, 24, 12, 2, 2, 2

창 2026-08-06T03:30~05:30Z
  (exc 없음)  chat-service     합 35
  (exc 없음)  content-service  합 517
```

| 확인 항목 | 결과 |
|---|---|
| ① 시리즈가 0이 아닌가 | **통과.** 매칭 실패 라인은 떨어지지 않고 `exc` **없는 시리즈로 남아** 총 건수가 보존된다 (chat 2·4·24·37·4 + 2·5 = 원래 2·4·26·42·4) |
| ② 예외 클래스로 뽑히는가 | 🔴 **부분 실패.** 164건 중 **9건(5.5%)**, 진짜 예외 클래스는 **7건(4.3%)**. 다른 창은 552건 전부 **0%** |
| ③ 과분할하는가 | **안 한다.** chat 2개 · auth 2개 · content 1개 |

**②가 실패한 이유가 구조적이다.**

```
Loki 저장 모습
  14:23:11 ERROR [traceId=…] MongoRepository - 알림 저장 실패      ← 헤더 줄. "ERROR" 있음
  org.springframework.dao.QueryTimeoutException: Redis command…   ← 클래스는 여기. "ERROR" 없음
      at …LettuceExceptionConverter.convert(…)

스윕 필터 |~ "ERROR|WARN"  →  헤더 줄만 통과  →  클래스 줄은 regexp 에 닿지도 못한다
```

**§1(AP-2 스택트레이스)에서 고친 그 결함이 탐색 집계에 그대로 남아 있다.** 분석 단계는
라인 필터를 넓혀 해결했지만 탐색 집계는 **건수 왜곡 때문에 일부러 안 넓혔다** — 예외 하나가
스택 30줄로 세어지면 발생량 곡선이 31배로 튄다. 그래서 예외 클래스는 지금 구조에서
**구조적으로 못 얻는다.**

잡힌 9건도 갈린다.

| exc | 정체 | 판정 |
|---|---|---|
| `com.mongodb.MongoTimeoutException` (7건) | 헤더 줄에 클래스가 함께 찍힌 경우 | 진짜 |
| `mc.e.t.a.c.e.GlobalException` (2건) | Logback이 축약한 **로거 이름** | **오탐** |

**그래서 켜지 않는다.** 판별력이 안 생기는데 후보 구조만 흔든다. 남은 갈래는 셋이고
어느 것도 착수하지 않았다 — ⓐ 앱이 헤더 줄에 예외 클래스를 찍게 한다(**변경군 A**)
ⓑ Alloy multiline 스티칭(관측 파이프라인 · 과거 데이터로 검증 불가)
ⓒ 스윕에 라인 조회를 따로 건다(응답 크기 미측정).

#### 켜기 전에 무엇을 확인해야 했나 (원래 계획)

이 레포는 *"이 신호가 실제로 도달하는가"* 를 확인하지 않고 세운 인과 예측이
**AP-1·AP-2에서 연속 반증된** 전례가 있고, 그래서 확인 결과를 수정안에 함께 적는 규칙이 있다.
아래 셋은 **배포 환경에서만** 잴 수 있고 저장 응답으로는 검증되지 않는다.

| # | 확인할 것 | 왜 |
|---|---|---|
| 1 | 이 쿼리가 시리즈를 **0개 넘게** 돌려주는가 | Loki 버전에 따라 `\| regexp`가 매칭 실패 라인을 `__error__`로 떨궈 시리즈가 통째로 빌 수 있다 |
| 2 | `exc` 값이 **예외 클래스로** 뽑히는가 | 로그가 ANSI 이스케이프 섞인 평문 Logback이라 `logfmt`는 이미 실패한 전례가 있다(B-2). 정규식은 그와 무관하지만 확인은 별개다 |
| 3 | 한 서비스의 후보가 **5개를 넘지 않는가** | 넘으면 과분할 |

쟤 볼 쿼리는 그대로 던져 보면 된다.

```bash
curl -s -u "$LOKI_USER:$GRAFANA_TOKEN" --get "$LOKI_URL/loki/api/v1/query_range" \
  --data-urlencode 'query=sum by (service_name, exc) (count_over_time({service_name=~"content-service|auth-service|chat-service"} |~ "ERROR|WARN" | regexp "(?P<exc>[a-z][\\w.]*\\.[A-Z]\\w*(?:Exception|Error))" [5m]))' \
  --data-urlencode "start=<ns>" --data-urlencode "end=<ns>" --data-urlencode "step=5m"
```

`data.result`가 비면 ①이 걸린 것이고, `metric.exc`가 클래스명이 아니면 ②가 걸린 것이다.

#### 검증 순서 — 한 번에 하나만

1. **B-44만** 켠 채 재조사 → 후보 개수 **불변** 확인, 컨텍스트 증가분 측정 → **완료** (위 재생)
2. **B-45를 켠다** → 후보 개수가 늘어나므로 **군집 과분할** 확인
3. 로그에 답이 있는 문항(AP-2 계열)에서 **탐색 선택이 바뀌는지** 본다

#### 반증 조건

- 지문 곡선이 비어도 **후보가 사라지면 안 된다** → 총 건수 곡선으로 자동 복귀.
  테스트 `지문_쿼리가_비면_총_건수로_되돌아간다`가 박제
- 한 서비스 후보가 **5개 초과**면 과분할이다. 예외 클래스가 아니라 **최상위 프레임**으로 축약한다
- 지문이 갈렸는데도 탐색 선택이 안 바뀌면 문제는 정보량이 아니라 프롬프트다
- 컨텍스트 증가분이 **10 KB를 넘으면** 되돌린다 (`include-raw=false`로 -83%를 만든 직후다)

#### 테스트 (4건)

| 테스트 | 무엇을 박제하나 |
|---|---|
| `예외_클래스가_지문이_되어_후보가_갈린다` | `exc` 라벨이 오면 후보가 2개로 갈린다 |
| `지문_곡선이_없으면_기존_동작_그대로다` | 설정이 비면 `ERROR/WARN` 하나 |
| `지문_쿼리가_비면_총_건수로_되돌아간다` | 빈 응답에도 후보가 살아 있다 |
| `갈린_후보도_건수는_각자_유지한다` | 19건·4건이 각자 남는다 |

**여기서 박제하는 것은 "라벨이 오면 어떻게 갈리는가"이지 "쿼리가 라벨을 만드는가"가 아니다.**
후자는 미측정이다.

---
## 관련 코드

| 무엇 | 파일 |
|---|---|
| 쿼리 4종 | `triage/SurveyProperties.java` · `triage/survey/Surveyor.java` |
| 응답 → Signal | `triage/incident/SignalExtractor.java` |
| Signal → 후보 | `triage/incident/Incident.java` (`cluster` · `splitByGap` · `mergeByTraceLink`) |
| 후보 → 컨텍스트 | `triage/plan/SurveyContextAssembler.java` |
| 테스트 | `triage/incident/CrossServiceSignalTest.java` · `LogSignatureSignalTest.java` |
