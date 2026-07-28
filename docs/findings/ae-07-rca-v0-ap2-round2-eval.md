# AE-07. rca-agent × AP-2 회차 2(자연어 진입) — **도구 결함을 둘 더 찾았다**

- 상태: **유효 채점 53/100** (앵커 [anchors-v2.md AP-2](../../../toy-content/docs/chaos/anchors-v2.md) ·
  **루브릭 v3 첫 적용**). 회차 기록 [ap-2/round-2.md](../ap-2/round-2.md) ·
  [채점 대장](../scoring/README.md#ap-2-회차-2--53--100--자연어-진입--루브릭-v3-첫-적용-81-평균-제외-n1).
- 피조사 장애: AP-2 회차 2. `size` 미지정 팔로우 목록 조회 2건
  (07-28 **15:35:35~15:36:36Z**, 61초) → `FollowCondition.limit()`의 `Integer` 언박싱 NPE → 500 ×2.
  DB 미진입·원복 없음.
- **입력이 자연어다** — traceId 없음. "최근 1시간 안에 팔로우 목록이 안 열린다는 제보가 있다. 원인을 조사해줘"

## 실행 측정치

| 항목 | 값 |
|---|---|
| provider / model | claude-cli / `claude-opus-5` · `num_turns=1` · **라벨 정상** |
| 탐색 | in 42,735 / out 3,053 · $0.3754 · survey 927ms + llm 46.7s |
| 분석 | in 68,418 (cacheRead 18,133 · cacheCreate 50,283) / out 8,423 · $0.7599 · llm 126.5s |
| 합계 | in 111,153 / out 11,476 · **$1.1353**(API 환산 추정) · **E2E 175.0s** — 11회 중 최고 비용 |
| 컨텍스트 | **≈ 68,491 ▓추정** (`C` = **21,331**, 조사 당일 프로브 3회 실측) |
| coverage | **trace 0B / 0 spans** · logs errwarn 5,728B · metrics 8 수집/1 누락 |
| window | 스윕 14:43:16~15:43:16Z → 좁힘 **15:34:00~15:43:16Z** (556s) |
| 산출물 | `reports/scan-20260728T154611.md` |

## 잘한 것

- **traceId를 지어내지 않았다.** 이 문항은 에러 트레이스가 존재하지 않는다(`exception=none`).
  탐색이 `traceId=null`로 `(창 + auth-service)`만 넘겼다 — **strategy.md §4.5의 정정
  (*후단은 traceId가 아니라 `(창+서비스+신호 종류)`를 받아야 한다*)이 실전에서 처음 확인**됐다.
- **오답 트레이스를 자력 기각했다.** 같은 1시간 창에 **AP-1 회차 2의 주입**(15:05Z,
  `http post /feeds/{feedId}/comments`, errorCount 1)이 **유일한 에러 트레이스로** 남아 있었다.
  에이전트는 그것을 골라 들어가지 않고 *"제보 시각보다 30분 이상 앞서고 경로가 '팔로우 목록
  조회'가 아닌 쓰기 경로라 증상과 불일치"* 로 기각했다. **AP-1 회차 2보다 탐색 난도가 높았다.**
- **Loki 채널 판정을 통과했다.** 앵커 감사가 *"Loki 채널이 켜져 있는지를 이 문항 하나로
  판정할 수 있다"* 고 지정한 시험이다. 로그 채널로 갔고 로그를 받았다.
- **막힌 채널을 뚫는 정확한 다음 수를 알고 있었다** — 조치 1 = *"두 traceId로 **레벨 무관
  전체 로그 조회**"*. 채점자가 실행해 확인했다: **그 쿼리를 쓰면 NPE 원문이 나온다.**

## 🔴 이 회차가 찾아낸 도구 결함 2건

회차 1은 *"v0.1 델타 +25가 **전량 Loki 셀렉터 + `logfmt` 두 결함**에 걸려 있다"* 고 못박았다.
**두 결함은 고쳐졌고 로그도 들어왔는데 점수는 −10이었다.** 결함이 둘이 아니라 넷이었다.

### ③ 라인 필터 `|~ "ERROR|WARN"`가 스택트레이스를 배제한다

**채점자 실측** (`{service_name="auth-service"}`, 15:35:30~15:36:45Z):

```
# 라인 필터 없이 — 정답이 통째로 나온다
java.lang.NullPointerException: Cannot invoke "java.lang.Integer.intValue()" because "this.size" is null
    at com.example.toyauth.app.user.controller.dto.FollowCondition$FollowingSearch.limit(FollowCondition.java:25)
    at com.example.toyauth.app.user.controller.dto.FollowCondition$FollowerSearch.limit(FollowCondition.java:45)

# 에이전트가 실제로 쓴 쿼리
{service_name="auth-service"} |~ `ERROR|WARN` |= `NullPointer`   →   lines: 0
```

**원인**: Logback이 스택트레이스를 **줄마다 별개 Loki 엔트리**로 보내는데 그 줄에는
`ERROR`·`WARN` 문자열이 없다. 라인 필터는 헤더 줄만 통과시키고 **예외 타입·메시지·스택
전부를 버린다.** 그리고 이 앱의 `handleAllException` 헤더 줄에는 메서드명 외에 아무것도 없다.

에이전트는 이 공백을 **정확히 인지하고 보고했다** — *"예외 클래스/메시지/요청 URI/응답 코드가
로그에 전혀 남아있지 않아 5xx 단정이 불가합니다."*

> **AP-1 회차 2에서 안 드러난 이유**: 거기서는 예외 정보가 **헤더 줄 자체에** 있었다
> (`SQL Error: 1406, SQLState: 22001` / `Data truncation: Data too long...`).
> **"로그가 들어온다"와 "예외를 읽을 수 있다"는 다른 문제**인데 한 문항으로는 갈리지 않았다.

**수정**: traceId 단위 전량 조회(에이전트가 제안한 그것)가 정확하다.
스택 포함 정규식(`|~ "ERROR|WARN|Exception|\tat "`)은 무관한 스택까지 끌어오므로 차선.

### ④ traceId를 안 넘기면 Tempo 조회를 통째로 건너뛴다

에이전트의 후보 3은 *"로그에는 traceId가 찍히는데 Tempo 트레이스가 0건 → **샘플링 설정 또는
OTLP export 파이프라인 결함** 가능성"* 이라고 썼다. **채점자 실측으로 반증됐다.**

```
GET /api/traces/6a68cc475f93de92df7ee7e4f4819181
  span=http get /user/{userid}/following   svc=auth-service  status=UNSET
  span=security filterchain before / secured request / security filterchain after
  total spans: 4        ← 6a68cc47…3ec5 는 /followers, 동일 구조
```

**트레이스는 멀쩡히 있었다.** 못 본 이유는 유실이 아니라 도구 경로다:

1. 탐색 스윕이 Tempo를 **`{ status = error }`로만** 검색한다 → `status=UNSET`이라 안 잡힌다
2. `Scope`에 traceId가 없으니 심층 수집이 **Tempo를 통째로 skip** (`trace: 0B / 0 spans`, `tempo 0ms`)

**결과가 뼈아프다.** 에이전트가 *"판단 불가"* 로 명시한 첫 항목이
**"팔로우 목록 엔드포인트를 어느 서비스가 소유하는지 주어지지 않았습니다"** 인데,
그 답(`http get /user/{userid}/following`, `auth-service`)이 **Tempo에 있었다.**

> **AP-2만의 문제가 아니다.** 앵커 감사가 *"정답 신호가 트레이스에 아예 없다"* 로 분류한
> **CH-2 · AU-2 · AP-2 세 문항이 전부 이 경로**를 탄다. 셋 다 에러 트레이스가 없어 traceId가
> 안 잡히고, 그래서 **정상 트레이스까지 함께 버려진다.**
>
> **수정**: traceId가 없을 때 수집기가 **status 무관 · 서비스+창 기준으로 Tempo를 검색**해야
> 한다. `Scope`가 traceId를 선택값으로 설계된 취지가 수집 단계에서 지켜지지 않고 있다.

### 부수: 수집 실패 문구가 오귀인을 유도했다

현재 문구는 *"탐색이 트레이스를 찾지 못했거나 **트레이스가 생성되지 않는 장애다**.
트레이스 부재 자체를 근거로 쓸 것"* 이다. 실제로는 *"에러 트레이스만 검색했고 정상 트레이스는
조회하지 않았다"* 가 맞다. **에이전트는 이 문구를 근거로 인프라 결함 가설을 지어냈다.**
부재를 근거로 쓰라고 시키려면 **무엇의 부재인지**를 정확히 써야 한다.

### 부수: 분석 단계가 탐색 단계를 반박한다

리포트는 *"조회 창이 질문과 불일치합니다 … 실제 수집 창은 9분 16초뿐"* 이라며 창을 넓히라고
권고한다. **좁힌 것은 탐색이 의도적으로 한 일이고 결과도 옳았다**(주입 61초 구간을 정확히 포함).
어셈블 컨텍스트에 **탐색의 선정 이유**가 안 들어간다 — 지금은 리포트에만 있고 모델에는 안 간다.

## 채점 요약 (53/100)

| # | 항목 | 배점 | 점수 | 한 줄 |
|---|---|---|---|---|
| 1 | 원인 적중 | 40 | **20** | NPE·`size` 미도달. 앱 계층까지만 확정, 500도 유보 |
| 2 | 근거 | 25 | **10** | 스택 0줄. WARN 2줄 + 부재 신호뿐 |
| 3 | 탐색 | 15 | **15** | ⚠️ 채점자 판정 1건 (반대 판정 시 총점 46) |
| 4 | 영향 판정 | 10 | **5** | 과대 평가는 피했으나 범위 유보 |
| 5 | 오귀인 | 5 | **3** | DB 계열 후보 + 자력 반증. **12회 만의 첫 비만점** |
| 6 | 조치 | 5 | **0** | ⚠️ 앵커 재배치에서 갈래 ㄴ 소실의 산물 |

**델타**: 회차 1과 같은 자로 재면 v1 **60 → 50 (−10)**, v2 **65 → 60 (−5)**.
두 델타가 같은 방향·비슷한 크기라 앵커 개정의 산물이 아니다.
**단 입력 모델이 함께 바뀌어(트레이스 증거 전량 상실 = 결함 ④) 변경군 B 델타로 인용 불가.**
깨끗한 델타는 회차 1 traceId `6a680b56067d9e2387043740be2cb115`로 `POST /investigate` 1회면 나온다.
