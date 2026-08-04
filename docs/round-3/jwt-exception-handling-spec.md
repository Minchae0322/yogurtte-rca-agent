# JWT 예외 처리 — 구현 스펙 (AU-3 게이트가 찾은 결함들)

AU-3(JWT 시크릿 드리프트)는 **조사가 한 번도 돌지 않았습니다.** 하네스가 장애를 주입하기
**전에** 죽었습니다.

그런데 그 전에 돌린 baseline 게이트가 **주입 0회로 결함 4건**을 잡아냈습니다.
`answer.md` 체크리스트의 *"주입 없이 확인된다"* 가 실제로 작동한 첫 사례입니다.

> **서술 버전은 [jwt-exception-handling.md](jwt-exception-handling.md)** — 무엇이 문제였고
> 무엇을 고쳤는지만 한 장으로. 이 문서는 **결함 4건 전체와 재실행 절차**를 다룹니다.
>
> 결함 대장·실행 순서는 [round-3/README.md](README.md)가 SoT입니다.
> 문항 기록은 [au-3/round-2.md](../au-3/round-2.md).

---

# 1. 무슨 일이 있었나

`chaos.sh`가 주입 직전에 크래시했습니다(`chaos.sh:463`). 원인 셋이었습니다.

- `local` 확장 순서
- 시크릿 Base64 처리
- Loki 조회 지연 오보고

**장애가 존재한 적이 없으므로 리포트도 없습니다.** 채점표의 AU-3 0점은 §8.1 평균·분산·인용
어디에도 넣지 않습니다.

> **AU-3의 0은 CH-3의 4와 성격이 다릅니다.** CH-3는 조사가 *돌았고* 탐색이 실패한 **관측값**입니다.
> AU-3는 **관측 자체가 없습니다**(주입 불성립). 에이전트를 잰 숫자가 아니므로 실행을 시도했고
> 실패한 사실만 남깁니다.

## 그런데 다른 문항의 점수를 깎았습니다

게이트가 만든 500 트레이스가 **AU-2 회차 2 실행 2의 조사 창에 섞여 들어갔고**, 에이전트가 그걸
traceId로 선정해 *"JWT 서명 키 변경"* 을 1순위로 올렸습니다. 정답은 후보 2·확신도 낮음으로
밀렸고 **−22점**이 됐습니다.

**채점자가 관측 대상 시스템에 요청을 보내면 그 흔적이 다음 조사의 입력이 됩니다.**
그래서 규칙이 생겼습니다 — 게이트·반증 실험은 **다음 조사 창 밖에서** 하거나, 불가피하면
**traceId와 시각을 회차 문서에 기록**합니다.

---

# 2. 찾은 결함 4건

| # | 무엇 | 상태 |
|---|---|---|
| 21 | `chaos.sh`가 AU-3를 실행하지 못한다 | **레포 수정 완료** · 서버 사본 동기화 미완 (`A-3`) |
| 20 | JWT 서명 예외를 못 잡아 401이 아니라 500이 나간다 | **코드 수정 완료 2026-07-30** (`A-0`) · **배포·검증 미완** |
| 18 | 예외 로그에 traceId가 없다 (`traceId=NONE`) | **일부러 안 고친다** (`A-1`) |
| 19 | 500 응답을 `200`으로 로깅한다 | **일부러 안 고친다** (`A-2`) |

> **정정 (2026-07-30).** 이 문서와 결함 대장이 한동안 `A-0 선적용 완료 · 3서비스 컴파일 통과`로
> 적혀 있었는데 **레포에는 반영돼 있지 않았습니다.** 세 파일 모두 원래 `catch` 절 그대로였고
> working tree도 깨끗했습니다. 같은 날 실제로 코드를 고치고 세 서비스 컴파일을 확인했습니다.
> **문서가 코드보다 앞서 나간 사례이므로, 앞으로 A군 항목은 `grep`으로 확인한 뒤 완료 표기합니다.**

여기에 하나 더, 게이트가 아니라 전 회차에서 나온 것:

| # | 무엇 | 상태 |
|---|---|---|
| 7 | `401 rate` 쿼리가 11회 연속 빈다 | 대기열 (`B-13`) — **변경군이 다르다** |

---

# 3. 고친 것 — JWT 서명 예외 (A-0)

## 무엇이 문제였나

세 서비스가 **문자 그대로 같은 코드**였습니다(`toy-content`·`toy-chat`의 `JwtParser`,
`toy-auth-user-region`의 `JwtProvider`, 전부 jjwt 0.11.5).

```java
import io.jsonwebtoken.*;                 // io.jsonwebtoken.security 는 안 들어온다

catch (SecurityException | MalformedJwtException e) { }   // <- java.lang.SecurityException
```

던져지는 것은 `io.jsonwebtoken.security.SignatureException` 입니다.
**미포착 → 필터 밖으로 전파 → 500.** 그리고 catch 블록이 **전부 비어 있어서** 401이 나가도
사유가 로그에 안 남았습니다.

**이게 앵커 ⓑ(만료와 서명 오류를 구별하는가)가 원리적으로 검증 불가였던 진짜 원인입니다.**

## 무엇을 했나 (2026-07-30 적용)

```java
import io.jsonwebtoken.security.SignatureException;   // 명시 import — 와일드카드로는 안 온다
@Slf4j

public boolean validateToken(String token) {
    try {
        Jwts.parserBuilder().setSigningKey(key).build().parseClaimsJws(token);
        return true;
    } catch (SignatureException e) {
        log.warn("JWT 서명 검증 실패 — 위조 또는 시크릿 불일치");
    } catch (ExpiredJwtException e) {
        log.warn("JWT 만료 exp={}", e.getClaims() == null ? "unknown" : e.getClaims().getExpiration());
    } catch (MalformedJwtException e) {
        log.warn("JWT 형식 오류");
    } catch (UnsupportedJwtException e) {
        log.warn("지원하지 않는 JWT");
    } catch (IllegalArgumentException e) {
        log.warn("JWT 값이 비어 있음");
    } catch (JwtException e) {
        log.warn("JWT 검증 실패 {}", e.getClass().getSimpleName());   // 최후 방어
    }
    return false;
}
```

| 서비스 | 파일 | 컴파일 |
|---|---|---|
| toy-content | `app/auth/token/JwtParser.java` | 통과 |
| toy-chat | `app/auth/token/JwtParser.java` | 통과 |
| toy-auth-user-region | `app/common/util/JwtProvider.java` | 통과 |
| **toy-user** | `app/common/util/JwtProvider.java` | 통과 (**2026-07-31 추가 반영**) |

> **추가 발견 (2026-07-31).** auth-service 레포가 **둘**이다 — `toy-auth-user-region`(위 기반영)과
> `toy-user`(같은 `ToyAuthApplication`·`spring.application.name: auth-service`, **A-0 미반영 상태였다**).
> 사용자 지시로 toy-user에 같은 코드를 반영했고, JwtFilter의 USER_NOT_FOUND 무로그 500 경로와
> ControllerAdvice(`RestApiException` 무로그 · `handleAllException` WARN→ERROR)도 함께 보강했다.
> **어느 레포가 배포본인지 확정 전에는 A-0 "코드 완료"를 배포본 기준으로 단정할 수 없다.**

**마지막 `catch (JwtException e)` 가 재발 방지 장치입니다.** `JwtException`은 jjwt 예외의 부모이고
`io.jsonwebtoken` 패키지라 **와일드카드에 잡힙니다** — 새 예외 타입이 생겨도 500으로 새지 않습니다.

## 이것만으로 401이 되는 이유

기존 구조가 이미 그렇게 되어 있었습니다.

```
validateToken() 이 false 반환
    → JwtFilter:39 조건 false → 인증 미설정
    → JwtFilter:77 response.sendError(401)
```

**catch에 걸리는 예외는 이미 401이 나가고 있었습니다.** 빠진 것은 `SignatureException`이 그
목록에 없다는 것 하나였습니다.

## 남은 구멍 — `parseClaims()`

같은 파일 아래쪽은 **여전히 던집니다.**

```java
public Claims parseClaims(String token) {
    try { ... }
    catch (ExpiredJwtException e) { return e.getClaims(); }
    catch (Exception e) { throw new RuntimeException("Invalid JWT Token", e); }   // ← 그대로
}
```

HTTP 경로는 `validateToken`이 먼저 false를 내므로 `getUserId()`가 호출되지 않아 무사합니다.
**문제는 WebSocket CONNECT** — `toy-chat`의 `StompConnectHandler`가 `validateToken`을 건너뛰고
`getUserId()` → `parseClaims()` 로 직행합니다. **그 경로는 여전히 500입니다.** → `A-4`(§8)

## 왜 "즉시 고치지 않는다"의 예외였나

원칙은 **결함을 발견해도 고치지 않는 것**입니다 — 회차 N을 baseline으로 고정해야 전후 델타가
성립하고, 고치면 문항이 소멸합니다.

AU-3는 예외였습니다.

- **회차 0회라 깨질 baseline이 없다**
- **이 결함을 두면 앵커가 성립하지 않는다** — 401 사유가 로그에 없으면 만료와 서명 오류를
  구별할 방법이 아예 없다

---

# 4. 일부러 안 고치는 것 둘

## 결함 19 — 500을 `200`으로 로깅한다 (A-2)

`RequestLoggingFilter`가 500 응답을 `200`으로 찍습니다. **없는 정도가 아니라 조사자를 적극적으로
오도합니다.**

고치면 관측이 정확해지지만, **"로그가 응답과 다른 값을 말하는" 문항은 그 자체로 가치가 있습니다.**
`오귀인` 항목이 12회 만점으로 변별력 0이었는데, 이걸 푸는 재료가 됩니다.

**단 A-0 배포 후 401로 바뀌면 증상이 달라지므로, 고치기 전에 재실측합니다.**

> **R9 상호작용 (2026-07-31).** 로그 통합 R9(성공 INFO 제거,
> [evidence-pipeline-improvements.md §3](evidence-pipeline-improvements.md))가 배포되면
> **이 `200` 오기 INFO 줄 자체가 사라진다** — "로그가 응답과 다른 값을 말하는" 증상이
> "로그가 아예 없는" 증상으로 바뀐다. **A-2를 문항 재료로 쓰려면 R9 배포 전에
> 재실측·결정이 필요하다.** 둘 다 미배포라 아직 충돌은 잠재 상태다.

## 결함 18 — 예외 로그에 traceId가 없다 (A-1)

`dispatcherServlet`이 찍는 ERROR가 `traceId=NONE` 입니다.
**예외가 난 요청일수록 traceId로 로그를 못 찾습니다.**

범위가 AU-3보다 큽니다 — 조사 도구가 traceId 단위 조회에 의존하는 한 **전 문항에 걸립니다.**
그래서 AU-3에 묶어 고치지 않고 별도 항목으로 잽니다.

> **`B-11`과 뿌리가 다릅니다.** `B-11`은 *필터*가 잘라내는 것이고, `A-1`은 *로그 자체에 키가 없는*
> 것입니다. **`B-11`을 고쳐도 `A-1`은 남습니다.**

### 채널 감사 실측

게이트가 만든 500 트레이스 `6a69e01bc0116147bba4b75907e9d57e`(11:12:27Z)를 채널별로 조회했습니다.

| 채널 | 쿼리 | 결과 |
|---|---|---|
| Tempo | `{ status = error }` | **Top-1.** `exception=SignatureException` · `status=500` · 중단 지점까지 |
| Loki | traceId 전량 | **실패** — `GET /api/feeds/following 200 - 4ms` INFO 한 줄뿐 (결함 18·19) |
| Loki | `\|~ "ERROR\|WARN"` | **부분** — `dispatcherServlet … threw exception` 헤더 줄만, JWT 언급 없음 |
| Loki | `\|~ "Signature\|Jwt"` | 성공 — 스택 전문 + `JwtParser.validateToken(JwtParser.java:31)` |

**같은 요청 하나가 채널마다 다른 이야기를 합니다** — Tempo는 원인을 말하고, traceId 로그는
*"200 성공"* 이라 말하고, 에러 로그는 JWT를 언급조차 안 합니다.

---

# 5. 재실행 전에 남은 것 셋

## ① 하네스 서버 사본 동기화 (A-3)

`chaos.sh` 수정은 **레포(`toy-content/docs/chaos/scripts/`)에만** 반영됐습니다.
**실제로 도는 것은 서버의 `~/chaos/scripts`** 입니다. 동기화 전에는 같은 크래시가 재발합니다.

## ② A-0 배포

`toy-content` · `toy-chat` · `toy-auth-user-region` 세 서비스.

## ③ A-0 검증 — 서명 훼손 토큰 1회 (주입 불필요)

| | 확인 | 수정 전 (실측) | 수정 후 (예측) |
|---|---|---|---|
| ⓐ | 응답 코드 | 500 | **401** — 앵커 v2 원 전제 복귀 |
| ⓑ | Loki에 `JWT 서명 검증 실패` WARN | 없음 (catch 비어 있음) | **뜬다** |
| ⓒ | 그 줄에 `traceId=`가 붙는가 | — | **미확인 전제** |
| ⓓ | 만료 토큰으로 한 번 더 → 문구 구별 | 구별 불가 (둘 다 무로그) | **다른 문구** → ⓑ 성립 |

**인과 주장이므로 신호 도달을 끝까지 따라갑니다** — AP-1·AP-2에서 같은 형태의 예측이 연속
반증됐습니다.

### ⓒ가 미확인 전제입니다

`JwtFilter` 시점에 MDC에 traceId가 실려 있는지 모릅니다.
**ⓒ가 실패하면 `A-1`을 AU-3 재실행의 선행으로 승격합니다.**

---

# 6. 역설 — 앱을 고치면 탐색이 더 어려워집니다

| 채널 | 수정 전 | 수정 후 (예측) |
|---|---|---|
| Tempo | `status=error` + `exception=SignatureException` **축자 자백** | **에러 신호 소멸** → 부재 신호만 |
| Loki | 스택은 있으나 `traceId=NONE` · 라인 필터 밖 | **`WARN` + traceId 정상** → 양쪽 채널 도달 |

**401은 4xx라 span에 error 태그가 안 붙습니다.** 지금은 500이라 Tempo가 원인을 그대로 자백하는데,
고치면 그 신호가 사라지고 **Loki로 옮겨갑니다.**

두 가지 함의가 있습니다.

1. **탐색 신호 추출이 Tempo와 Loki를 동등하게 다뤄야 합니다** — 한쪽에 의존하면 계측을 고칠 때 깨집니다
2. **401을 메트릭으로도 봐야 합니다** → `B-13`

---

# 7. B-13 — 401 rate 쿼리 (변경군이 다릅니다)

`401 rate` 쿼리가 **11회 연속 빈 결과**입니다. AU-2에서는 **대상 서비스까지 틀렸습니다** —
`application="content-service"` 인데 로그인은 auth 소관입니다. 에이전트가 매번 교정 쿼리를 냅니다.

**AU-3 채점에 직접 걸립니다** — 401은 span에 error 태그가 안 붙으므로 **인증 실패의 '범위'를
아는 유일한 지표**입니다.

처방: `count by (status, uri) (http_server_requests_seconds_count{...})` 로 **실제 라벨과 status
값을 먼저 확인한 뒤** 확정하고, 대상 서비스도 함께 바로잡습니다.

> **A와 같은 회차에 넣지 않습니다.** `B-13`은 조사 도구(B)이고 `A-0`은 앱 계측(A)입니다.
> 섞으면 점수가 올라도 *"앱을 고쳐서인가 쿼리를 고쳐서인가"* 를 증명할 수 없습니다.

---

# 7-b. 검토 — 예외 로깅을 `@ControllerAdvice`로 통합할 수 있나 (A-5 · 신규)

## 이미 있고 이미 로깅한다

세 서비스 모두 `GlobalExceptionHandler`(`@RestControllerAdvice`)를 갖고 있고 `Exception.class`
핸들러에서 `log.warn("handleAllException", ex)` 를 찍습니다.

**그런데 JWT 예외는 여기 도달하지 않습니다.** 이유가 두 겹입니다.

## 왜 도달하지 않나

**① 필터는 `DispatcherServlet` 앞이다.** `JwtFilter extends OncePerRequestFilter` 이고 `@Component`라
서블릿 필터 체인에 등록됩니다. `@RestControllerAdvice`는 `DispatcherServlet`이 처리하는 것만
보므로 **필터에서 던진 예외는 구조적으로 못 잡습니다.**

증거는 채널 감사 로그입니다 — 찍힌 줄이 `handleAllException`이 **아니라**
`dispatcherServlet … threw exception` 이었습니다.

**② 예외가 나는 호출이 `try` 블록 밖이다.**

```java
// JwtFilter.java:36-39 — try 밖
String token = tokenProvider.resolveAccessToken(request);
if (StringUtils.hasText(token) && tokenProvider.validateToken(token)) {   // 39행
    Long userId = tokenProvider.getUserId(token);                        // 40~42행도 밖
    ...
}

try {                                       // 64행에서 시작
    ...
} finally {
    MDC.remove(MDC_USER_ID);                // catch 없음
}
```

## 통합하는 방법

필터에서 `HandlerExceptionResolver`에 위임하면 **진짜로 `GlobalExceptionHandler` 한 곳에 모입니다.**

```java
@Component
@RequiredArgsConstructor
public class ExceptionHandlingFilter extends OncePerRequestFilter {

    @Qualifier("handlerExceptionResolver")
    private final HandlerExceptionResolver resolver;

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res,
                                    FilterChain chain) throws ServletException, IOException {
        try {
            chain.doFilter(req, res);
        } catch (Exception e) {
            resolver.resolveException(req, res, null, e);
        }
    }
}
```

필터 체인 **맨 앞**에 두면 JWT뿐 아니라 **뒤따르는 모든 필터 예외**가 커버됩니다.

| 안 | 커버 범위 | diff |
|---|---|---|
| 가 | 그 필터 안의 예외만 | 최소 — **A-0이 한 것** |
| 나 | 최외곽 필터에서 잡아 직접 처리 | 중간 |
| **다** | **필터 체인 전체 → ControllerAdvice로 통합** | 중간 (파일 1개) |

## 그리고 이것이 결함 18의 근본 해결이다

`traceId=NONE` 이 나는 이유는 **예외가 필터 체인을 벗어난 뒤 컨테이너 레벨에서 로그가 찍히기**
때문입니다. 그 시점엔 trace scope가 닫혀 있습니다.

**통합 필터 안에서 잡으면 MDC가 살아 있어 traceId가 붙습니다.**
(`JwtFilter`가 이미 `MDC.put(MDC_USER_ID, …)` 를 쓰므로 MDC 인프라는 있습니다.)

## 그런데 평가 결정과 충돌한다

| 기존 결정 | 통합을 넣으면 |
|---|---|
| **`A-1` 보존** (`traceId=NONE`을 전 문항 측정용으로 남김) | **자동으로 풀린다** — 보존 결정이 무효가 된다 |
| **`A-2` 보존** (500을 `200`으로 로깅하는 문항 가치) | 예외 응답 경로가 바뀌어 증상이 달라질 수 있다. **재실측 필요** |
| `A-0` 검증 (401이 나오는가) | **같이 넣으면 귀속 불가** — 401이 catch 절 덕인지 통합 덕인지 모른다 |
| 변경군 A는 주입 필요 | 별도 사이클 |

## 결론 — A-0 검증 결과가 필요성을 결정한다

1. **`A-0` 배포·검증** → 특히 **ⓒ**(WARN 줄에 `traceId=`가 붙는가)
2. **ⓒ 실패** → `A-5`(통합 필터)가 `A-1`의 처방이 된다. 그때 **A-1 보존 결정을 철회**한다
3. **ⓒ 성공** → traceId가 이미 붙으므로 A-5는 급하지 않다. **A-1 보존 유지**

**지금 넣으면 그 정보를 얻지 못합니다.** 통합은 옳은 방향이지만 순서가 A-0 뒤입니다.

---

# 8. 미해결 — A-4

`toy-chat`의 WS CONNECT(`StompConnectHandler`)는 `validateToken`을 거치지 않고
`getUserId()` → `parseClaims()` 로 직행합니다. **A-0의 보호 밖입니다.**

`IN-1` 재실행 전에 별도 확인이 필요합니다.

---

# 9. 앵커 쪽 — 개정을 미룬 이유

앵커 개정 항목 **ㅋ**(*"처방이 반대다: 앵커가 아니라 앱을 고쳤다"*)이 이 문항에 걸려 있습니다.

**A-0 검증 전에는 손대지 않습니다.** 앱을 고치면 증상이 401로 바뀌므로, 앵커를 지금 고치면
**어느 전제로 쓴 앵커인지** 알 수 없게 됩니다.

---

# 10. 현재 상태

| 항목 | 상태 |
|---|---|
| `chaos.sh` 크래시 (결함 21) | **레포 수정 완료** |
| 하네스 서버 사본 동기화 (`A-3`) | **미완** — 안 하면 같은 크래시 재발 |
| JWT 예외 포착 (`A-0`) | **코드 완료 2026-07-30** (3서비스 컴파일 통과) · **배포 미완** · **검증 미완** |
| `parseClaims()` RuntimeException | **그대로** — WS CONNECT 경로는 여전히 500 (`A-4`) |
| traceId 보존 (`A-1`) | **보존** — 전 문항에 걸리므로 별도 측정 |
| 상태 코드 오기 (`A-2`) | **보존** — 오귀인 변별력을 푸는 재료. A-0 배포 후 재실측 |
| WS CONNECT (`A-4`) | **미해결** — IN-1 재실행 전 확인 |
| 예외 로깅 통합 (`A-5`) | **검토 완료 · 보류** — A-0 검증 ⓒ 결과가 필요성을 결정 (§7-b) |
| `401 rate` 쿼리 (`B-13`) | 대기열 · **변경군 B라 별도 회차** |
| 앵커 ㅋ 개정 | **보류** — A-0 검증 후 |
| AU-3 조사 | **0회** — 위 셋을 끝낸 뒤 첫 회차 |

**주장 범위**: *"주입 0회로 앱 결함 4건을 찾아 하나를 고쳤고, 나머지 둘은 문항 가치를 위해
의도적으로 보존했다"* 까지입니다. **AU-3 점수는 아직 존재하지 않습니다.**
