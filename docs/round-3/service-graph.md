# 트레이스를 갖고 있는데 누가 누구를 불렀는지 모른다

> 상세는 [service-graph-spec.md](service-graph-spec.md)
> (실측한 태그 키 · 판별 순서 전문 · 함정 둘 · 검증 조건 ①~⑤ · 반증 조건).

---

## 문제

에이전트가 트레이스를 받으면 **span이 평평한 목록으로** 옵니다. 부모-자식 관계가 트레이스
안에 있는데 **코드가 그 필드(`parentSpanId`)를 안 읽습니다.**

그래서 두 가지가 안 됩니다.

- **인과를 시각으로 추측해야 한다** — *"A가 B보다 0.7ms 먼저 시작했으니 A가 B를 불렀나"*
- **무엇이 무엇에 의존하는지 모른다** — 토폴로지 지식이 프롬프트에 복붙된 **한 문장**뿐이고,
  거기에 **Redis도 Mongo도 MySQL도 없다**

## 실제로 감점된 사례

AU-4 리포트 원문입니다.

> *"Redis GET 4건 **직후 0.7ms 뒤** `auth-service/api/external/users?userIds=1,3,7,9` —
> **정확히 그 4명**. 캐시 미스분만 원격 조회하는 read-through 패턴"*

**맞췄습니다.** 그런데 근거가 *"시각이 붙어 있고 사용자 ID가 같다"* 는 정황이라
**확신도 낮음(후보 3)** 에 뒀고, 1순위는 auth 부재만으로 채웠습니다. → **원인 −10**

`parentSpanId`를 읽으면 *"두 span이 같은 부모를 갖는다"* 가 **추측이 아니라 사실**이 됩니다.

그리고 IN-1(Redis 다운 → 세 서비스 다른 증상)은 *"세 서비스가 Redis를 공유한다"* 를
**알 수단이 아예 없습니다.**

---

## 데이터에는 이미 있다

저장된 트레이스를 열어 확인했습니다.

| 대상 | 읽을 것 |
|---|---|
| 부모-자식 | `spanId` · `parentSpanId` (base64 — **디코딩 없이 문자열로 비교**) |
| Redis | `db.system=redis` |
| MongoDB | `db.system=mongodb` |
| MySQL | `jdbc.datasource.driver` · `.name` |
| Kafka | `messaging.system=kafka` + 토픽 · 오프셋 · 파티션 · `messaging.operation` |

**코드가 안 읽고 있을 뿐입니다.**

---

## 무엇을 바꾸나 — 세 줄

### ① 표준 키를 먼저 보고 `peer.service`는 마지막에

```
messaging.system → db.system → jdbc.datasource.* → 부모의 service.name → (마지막) peer.service
```

### ② 이름을 시스템/대상으로 짓는다

```
mysql/content              redis              mongodb              kafka/user.notifications
```

**`content-service`(서비스)와 `mysql/content`(DB)가 확실히 구별됩니다.**
방향은 `messaging.operation`(publish/receive)으로 정하므로, 프롬프트에 손으로 적은
`content → Kafka → chat` 이 **관측에서 그대로 나옵니다.**

### ③ 거르지 말고 접는다

```
23 span  →  엣지 1줄

content-service ──jdbc──→ mysql/content (HikariPool-1)   15회  최대 4.3ms
    error: Duplicate entry '154-175' for key 'tb_feed_hashtags.uk_feed_hashtag'
    events: acquired, rollback
```

`query`·`result-set`·`generated-keys` 15개가 모두 같은 엣지입니다.

**노이즈를 제외하는 게 아니라 집약하는 것**이 핵심입니다 — 버리는 게 없고, blocklist가 없어
새 계측이 붙어도 안 깨지고, 엣지에 `error`를 붙이면 **그래프 한 줄이 AP-3의 정답 지문을 담습니다.**

---

## 함정 둘 (실측으로 확인)

**`peer.service`가 서비스 이름이 아니다.**

```json
{"key":"jdbc.datasource.name","value":{"stringValue":"content"}},
{"key":"peer.service",        "value":{"stringValue":"content"}}     ← MySQL DB 이름
```

288건이 이것이었습니다. 순진하게 매칭하면 **`content-service → content-service` 자기 참조
엣지**가 생깁니다. `-service` 접미를 떼서 비교하는 규칙도 이 함정에 빠집니다.

**필터 체인은 span이 2개뿐이다.** 12개 필터는 span이 아니라 `events` 배열입니다.
span 이름만 `grep`하면 events까지 잡혀 *"span 수백 개가 필터"* 로 오독합니다(실제로 그렇게
오독했습니다). **span의 대부분은 JDBC 세부 span이고 그건 노이즈가 아닙니다** — AP-1·AP-3의
정답이 거기 있습니다.

---

## 안 하는 것 — 정적 토폴로지 맵

`"Redis는 content·auth·chat이 공유한다"` 를 코드나 프롬프트에 박으면 **IN-1의 정답을 심는
것**입니다. 채록에 프로브를 심는 안을 철회한 것과 같은 계열입니다.

**관측에서 유도하는 것만** 합니다.

## 프롬프트 문장은 같은 회차에 뺀다

코드가 그래프를 주면 프롬프트의 토폴로지 한 문장을 **빼야** 합니다. 안 빼면
*"그래프 덕인가 문장 덕인가"* 를 못 가립니다.

---

## 한 트레이스로 되는 것과 안 되는 것

| 문항 | |
|---|---|
| **AU-4** (auth → content 캐시) | **된다** — 한 요청이 두 서비스를 지난다 |
| **IN-1** (Redis → 3서비스) | **안 된다** — 세 요청이 필요하다 → **`B-9` 선행** |

## 현재 상태

| | |
|---|---|
| 데이터 확인 | Redis · MySQL · Kafka **실측** · MongoDB 재확인 필요 |
| 함정 규명 | **둘 다 실측으로 확인** |
| 설계 | 판별 순서 · 이름 규칙 · 집약 방식 확정 |
| 구현 | **미착수** |
| 검증 | **미실행** — 저장된 원본으로 주입 없이 가능 |
| 효과 | **미측정** |

**주장 범위**: *"트레이스에 관계 정보가 있는데 읽지 않고 있다는 것을 실측으로 확인하고,
읽어내는 방식과 두 함정을 규명했다"* 까지입니다.
