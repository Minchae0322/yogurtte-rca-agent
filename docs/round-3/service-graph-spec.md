# 트레이스를 갖고 있는데 누가 누구를 불렀는지 모른다

에이전트는 span 수백 개를 평평한 목록으로 받습니다. **부모-자식 관계가 트레이스 안에 있는데
코드가 그 필드를 안 읽습니다**(`TraceSpans`가 `parentSpanId`를 무시한다).

그래서 인과를 **시각으로 추측**해야 하고, 무엇이 무엇에 의존하는지는 **프롬프트에 복붙된
한 문장**이 전부입니다.

---

## 이것 때문에 깎인 것

### AU-4 — 맞췄는데 확신을 못 가졌다 (원인 −10)

리포트 원문입니다.

> *"Redis GET 4건(474558 / 475820 / 476303 / 476689 μs) **직후 0.7ms 뒤**
> `auth-service/api/external/users?userIds=1,3,7,9` — **정확히 그 4명**.
> 캐시 미스분만 원격 조회하는 read-through 패턴"*

**정답입니다.** 그런데 근거가 *"시각이 붙어 있고 사용자 ID가 같다"* 는 정황 추론이라
**확신도 낮음(후보 3)** 에 뒀고, 1순위는 auth 부재만으로 채웠습니다.

`parentSpanId`를 읽으면 *"Redis GET span과 auth 호출 span이 같은 부모를 갖는다"* 가
**추측이 아니라 사실**이 됩니다.

### IN-1 — 알 수단이 아예 없다

Redis 다운 → 세 서비스가 각각 다른 증상. 증상 셋을 원인 하나로 수렴시키는 게 문항의 전부인데,
*"세 서비스가 공통으로 Redis를 쓴다"* 를 알 방법이 없습니다.

프롬프트의 토폴로지 문장에 **Redis도 Mongo도 MySQL도 없습니다.**

```
content 서비스가 이벤트를 발행하면 Kafka를 거쳐 chat 서비스가 소비하여 알림을 발송한다.
auth는 인증을 담당한다.
```

---

## 데이터에는 이미 있다

`reports/raw/`의 저장된 트레이스를 열어 확인했습니다.

| 대상 | 읽을 키 | 근거 |
|---|---|---|
| 부모-자식 | `spanId` · `parentSpanId` (**base64** — `"HWIIZHdngb4="`) | **실측** — 있는데 안 읽는다 |
| 서비스 | `resource.attributes["service.name"]` | 실측 |
| **Redis** | `db.system=redis` · `db.operation` | **실측** |
| **MySQL** | `jdbc.datasource.driver` · `.name` · `.pool` | **실측** |
| **Kafka** | `messaging.system=kafka` · `destination.name` · `kafka.consumer.group` · `kafka.message.offset` · `kafka.source.partition` · `messaging.operation` | **실측** |
| **MongoDB** | `db.system=mongodb` | 구두 확인 — 저장된 트레이스로 **재확인할 것** |

`parentSpanId`는 base64인데 **디코딩할 필요가 없습니다.** 문자열 그대로 키로 쓰면 부모를 찾습니다.

## 태그 이름은 Brave가 아니라 계측 라이브러리가 정한다

```
Spring Data Redis / Spring Kafka / datasource-micrometer
        ↓  Micrometer Observation      ← 태그 이름을 여기서 정한다
micrometer-tracing-bridge-brave        ← Brave = 전파·span 생성만
        ↓  zipkin-reporter-brave
Alloy  →  OTLP 변환  →  Tempo
```

OTel Agent를 쓰지 않습니다(ADR-001). 같은 트레이스에 그 흔적이 있습니다 —
`otel.zipkin.absentField.startTime` · `original_span_name`은 **Zipkin 포맷으로 나가서 OTLP로
변환됐다는 표시**입니다.

**그래서 키는 계약이 아니라 관례입니다.** `jdbc.*`가 반례입니다 — OTel 컨벤션이면
`db.system=mysql`·`db.statement`인데 실제로는 `jdbc.datasource.driver`·`jdbc.query[0]`입니다.
**라이브러리마다 다르므로 버전이 바뀌면 이름도 바뀔 수 있습니다.**

---

## 무엇을 바꾸나

### ① `TraceSpans`에 필드 세 개 추가

```java
public record Span(String service, String name,
                   String spanId, String parentSpanId,     // 추가
                   Map<String,String> attributes,          // 추가
                   long startNanos, long endNanos) { }
```

`spanId → Span` 맵을 만들고 각 span의 `parentSpanId`로 부모를 찾습니다.

### ② 판별 순서 — 표준 키를 먼저, `peer.service`를 마지막으로

```
① messaging.system 있다              → 메시징
② db.system 있다                     → 데이터베이스
③ jdbc.datasource.driver/.name 있다  → 데이터베이스
④ 부모 span 의 service.name 이 다르다 → 서비스 경계
⑤ peer.service 만 있다               → 미분류로 남긴다 (버리지 않는다)
```

**⑤가 마지막인 이유가 함정입니다** (아래). **그리고 버리지 않습니다** — 못 알아본 엣지를
버리면 새 인프라가 붙었을 때 조용히 사라집니다. 미분류로 남기고 원본 속성을 함께 실어
모델이 판단하게 합니다.

### ③ 이름 규칙

```
mysql/content                     ← jdbc.datasource.driver 에서 mysql, .name 에서 content
redis                             ← db.system
mongodb                           ← db.system
kafka/user.notifications          ← messaging.system + messaging.destination.name
```

두 가지가 해결됩니다.

- **`content-service`(서비스)와 `mysql/content`(DB)가 확실히 구별됩니다**
- `Apache Kafka: taPwALsEQRWSS9_C_Lyt3Q` 같은 **클러스터 ID가 이름에서 빠집니다.**
  토픽명이 훨씬 유용합니다

**방향은 `messaging.operation`으로 정합니다** — `publish`면 서비스 → 토픽, `receive`면
토픽 → 서비스. 프롬프트에 손으로 적은 `content → Kafka → chat` 이 **관측에서 그대로 나옵니다.**

### ④ 거르지 말고 접는다

노이즈를 제외하는 것이 아니라 **엣지 단위로 집약**합니다.

```
23 span  →  엣지 1줄

content-service ──jdbc──→ mysql/content (HikariPool-1)   15회  최대 4.3ms
    error: Duplicate entry '154-175' for key 'tb_feed_hashtags.uk_feed_hashtag'
    events: acquired, rollback
```

`query`·`result-set`·`generated-keys` 15개가 모두 같은 엣지이므로 한 줄이 됩니다.

| | |
|---|---|
| **필터링이 아니다** | 버리는 게 없으니 *"크기로 걸러내지 않는다"* 원칙과 맞다 |
| **blocklist가 없다** | *"무엇이 노이즈인가"* 를 코드에 박지 않는다. 새 계측이 붙어도 안 깨진다 |
| **정답 지문이 남는다** | 엣지에 `error`·`events`를 붙이면 그래프 한 줄이 AP-3의 정답을 담는다 |

### ⑤ 어디서 만들고 어디서 보나

```
Collector → CollectedData ─┬→ ContextAssembler      → 컨텍스트 → LLM ②
                           ├→ EvidenceExtractor     → Evidence → 리포트
                           └→ ServiceGraphExtractor → ServiceGraph   ← 신규
```

`EvidenceExtractor`가 이미 원본 JSON에서 회고 가능한 값(top span · 로그 원문 · 0 구간)을
뽑습니다. 그래프도 같은 성격이라 그 옆에 둡니다.

**두 곳에서 봅니다.**

- **LLM 컨텍스트** — `ContextAssembler`의 절 하나로 추가. **원본 트레이스 JSON은 그대로 둔다**
  (요약을 더하는 것이지 대체가 아니다 — 코드가 놓친 엣지를 모델이 직접 볼 여지를 남긴다)
- **리포트 관측 증거 절** — 안 남기면 그래프를 준 효과를 **채점에서 귀속시킬 수 없다**

**분석 단계(LLM ②)입니다.** 탐색 단계는 집계값만 받아 트레이스 원본이 없습니다.

---

## 함정 둘 — 실측으로 확인됐다

### `peer.service`가 서비스 이름이 아니다

```json
{"key":"jdbc.datasource.name","value":{"stringValue":"content"}},
{"key":"peer.service",        "value":{"stringValue":"content"}}     ← MySQL DB 이름이다
```

**`peer.service=content`는 `content-service`가 아니라 MySQL 데이터베이스 `content`입니다.**
288건이 이것이었습니다.

순진하게 매칭하면 **`content-service → content-service` 자기 참조 엣지**가 생깁니다.
`-service` 접미를 떼서 비교하는 규칙도 정확히 이 함정에 빠집니다.
→ **표준 키를 먼저 보고 `peer.service`는 마지막에.**

### 필터 체인은 span이 2개뿐이다

```json
{"name":"security filterchain before", "events":[
   {"name":"before CorsFilter"}, {"name":"before JwtAuthenticationFilter"}, ... 12개 ]}
```

12개 필터는 span이 아니라 **`events` 배열**입니다. span 이름만 `grep`하면 events까지 잡혀
*"span 수백 개가 필터"* 로 오독합니다(실제로 그렇게 오독했다).

**span의 대부분은 JDBC 세부 span이고 그건 노이즈가 아닙니다** — AP-1·AP-3의 정답이 거기 있습니다.
그래서 제외 규칙이 필요 없고 ④의 집약으로 충분합니다.

---

## 한 트레이스의 한계

**한 트레이스는 그 요청이 지난 경로만** 보여줍니다.

| 문항 | 한 트레이스로 되나 |
|---|---|
| **AU-4** (auth → content 캐시 경로) | **된다** — 한 요청이 두 서비스를 지난다 |
| **IN-1** (Redis → 3서비스 각각 다른 증상) | **안 된다** — 세 요청이 필요하다 |

IN-1은 **`B-9`(창 안 후보 N건 수집)이 선행**입니다. N건에서 엣지를 누적하면
*"content·auth·chat 셋이 모두 redis로 엣지를 갖는다"* 가 **관측에서** 나옵니다 —
정적 맵을 심지 않고 도달 경로가 생깁니다.

```java
ServiceGraph merge(List<ServiceGraph> perTrace)   // calls 합산, maxMs 최대
```

## 정적 토폴로지 맵은 만들지 않는다

`"Redis는 content·auth·chat이 공유한다"` 를 코드나 프롬프트에 박으면 **IN-1의 정답을 심는
것**입니다. 채록에 프로브를 심는 안을 철회한 것과 같은 계열입니다.

**관측에서 유도하는 것만** 합니다.

## 프롬프트를 같은 회차에 바꿔야 한다

지금 세 프롬프트에 토폴로지 한 문장이 복붙돼 있습니다. 코드가 그래프를 주면 **그 문장을 빼야**
하고, 안 빼면 *"그래프 덕인가 문장 덕인가"* 를 못 가립니다.

문서가 이미 경계를 그어놨습니다 — *"토폴로지 추가는 변경군으로 측정할 대상이지 슬쩍 끼울
것이 아니다."*

---

## 검증 — 저장된 원본으로 지금 가능하다

주입도 배포도 필요 없습니다. `reports/raw/`에 여러 문항의 트레이스가 쌓여 있습니다.

| | 확인할 것 |
|---|---|
| ① | 23 span이 엣지 1줄로 접히는가 |
| ② | `peer.service=content` 가 **DB 엣지로** 분류되는가 (자기 참조가 안 생기는가) |
| ③ | AU-4 트레이스에서 `content → redis` 와 `content → auth-service` 가 **둘 다** 나오는가 |
| ④ | CH-1 트레이스에서 Kafka 엣지가 **방향까지** 나오는가 (publish / receive) |
| ⑤ | **MongoDB가 `db.system=mongodb` 로 오는가** — 아직 확인 안 됨 |

**③④가 결정적입니다** — 서비스 엣지와 인프라 엣지가 함께 나와야 프롬프트 문장을 대체할 수 있습니다.

확인 결과를 그대로 **테스트 픽스처로 고정**하면 구현이 회귀에 안전해집니다.

## 반증 조건

| 변경 | 개선이 없으면 |
|---|---|
| 그래프 제공 | AU-4의 원인 점수가 안 오르면 **병목은 관계 정보가 아니라 배치**다(능력 결함 나) |
| 여러 트레이스 누적 | IN-1이 안 오르면 **수렴에 필요한 건 그래프가 아니다** — 다른 것을 찾아야 한다 |
| 프롬프트 문장 제거 | 점수가 **떨어지면** 그래프가 그 문장만큼도 못 한 것이다 |

---

## 순서

| 순 | 무엇 | 주입 |
|---:|---|---|
| 1 | 저장된 트레이스로 **엣지 추출 검증**(①~⑤) | 불필요 |
| 2 | `TraceSpans` 파싱 확장 + `ServiceGraphExtractor` | 불필요 |
| 3 | 컨텍스트·리포트에 절 추가 | 불필요 |
| 4 | 프롬프트의 토폴로지 문장 제거 | 3과 **같은 회차** |
| 5 | `B-9` 후 여러 트레이스 누적 | `B-9` 이후 |
| 6 | AU-4 재조사 → IN-1 재조사 | IN-1은 주입 필요 |

**1~3은 지금 바로 됩니다.** 한 트레이스 그래프만으로도 AU-4에 실효가 있고, IN-1은 5번 이후입니다.

## 현재 상태

| | |
|---|---|
| 데이터 확인 | Redis · MySQL · Kafka **실측 완료** · MongoDB **미확인** |
| 함정 확인 | `peer.service` DB 이름 문제 · 필터 span 오독 **둘 다 실측으로 규명** |
| 설계 | 판별 순서 · 이름 규칙 · 집약 방식 확정 |
| 구현 | **미착수** |
| 검증 | **미실행** (저장된 원본으로 가능) |
| 효과 | **미측정** |

**주장 범위**: *"트레이스에 관계 정보가 있는데 읽지 않고 있다는 것을 실측으로 확인하고,
읽어내는 방식과 두 함정을 규명했다"* 까지입니다.
