# JWT 예외 처리 — 위조 토큰이 401이 아니라 500이었다

> 상세는 [jwt-exception-handling-spec.md](jwt-exception-handling-spec.md)
> (AU-3 게이트가 찾은 결함 4건 · 일부러 안 고치는 것 · 재실행 절차 · `A-5` 통합 검토).

---

## 무엇이 문제였나

서명이 훼손된 JWT로 요청하면 **401이 아니라 500**이 나갔습니다.
그리고 정상적으로 401이 나가는 경우에도 **왜 실패했는지가 로그에 남지 않았습니다.**

이 때문에 AU-3(JWT 시크릿 드리프트) 문항의 채점 기준 하나가 **원리적으로 검증 불가**였습니다 —
만료된 토큰과 서명이 틀린 토큰을 구별할 방법이 관측에 없었습니다.

## 원인 — import 한 줄

```java
import io.jsonwebtoken.*;                                    // .security 서브패키지는 포함되지 않는다

catch (SecurityException | MalformedJwtException e) {
    // 본문이 비어 있다
}
```

와일드카드 import는 **하위 패키지를 덮지 않습니다.** 그래서 `SecurityException`이
`java.lang.SecurityException`으로 해석됐습니다 — jjwt는 그걸 던지지 않습니다.

실제로 던지는 것은 `io.jsonwebtoken.security.SignatureException` 이고,
**catch 목록에 없으니 필터 밖으로 나가 500**이 됐습니다.

그리고 catch 블록이 **전부 비어 있어서** 잡힌 예외도 흔적을 남기지 않았습니다.

## `@ControllerAdvice`가 있는데 왜 안 잡혔나

세 서비스 모두 `GlobalExceptionHandler`(`@RestControllerAdvice`)를 갖고 있고,
`Exception.class` 핸들러에서 **이미 로깅합니다.**

```java
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {
    @ExceptionHandler({Exception.class})
    public ResponseEntity<Object> handleAllException(Exception ex) {
        log.warn("handleAllException", ex);      // 이미 있다
```

**그런데 여기 도달하지 않습니다.** `@ControllerAdvice`는 `DispatcherServlet`이 처리하는 범위만
덮는데, `JwtFilter`는 `OncePerRequestFilter` + `@Component` 라 **서블릿 필터 체인**에 등록됩니다 —
`DispatcherServlet` **앞단**입니다.

```
요청
  │
  ├─ 서블릿 필터 체인
  │     JwtFilter  ← 여기서 예외를 던진다
  │                  그대로 컨테이너로 올라가 500
  │
  └─ DispatcherServlet ──→ 컨트롤러 ──→ @ControllerAdvice 범위
                                          (여기까지 오지 못한다)
```

**증거가 로그에 있습니다.** 채널 감사에서 나온 줄이 `handleAllException`이 **아니라**
`dispatcherServlet … threw exception` 이었습니다. `@ExceptionHandler(Exception.class)` 가 잡았다면
전자가 찍혔을 것입니다.

그래서 이 결함은 **핸들러를 하나 더 추가해서 고칠 수 없었습니다.**
예외를 **던지는 자리에서** 잡아야 했습니다.

> 필터 예외까지 `@ControllerAdvice` 한 곳에 모으는 방법은 따로 있습니다(필터에서
> `HandlerExceptionResolver`에 위임). 다만 그러면 **일부러 보존해 둔 다른 결함이 같이 풀려서**
> 측정 기회가 사라집니다 — 판단은
> [스펙 문서 §7-b](jwt-exception-handling-spec.md)에 있습니다.

## 무엇을 고쳤나

```java
} catch (SignatureException e) {                 // io.jsonwebtoken.security 를 명시 import
    log.warn("JWT 서명 검증 실패 — 위조 또는 시크릿 불일치");
} catch (ExpiredJwtException e) {
    log.warn("JWT 만료 exp={}", ...);
} catch (MalformedJwtException e) {
    log.warn("JWT 형식 오류");
} catch (UnsupportedJwtException e) {
    log.warn("지원하지 않는 JWT");
} catch (IllegalArgumentException e) {
    log.warn("JWT 값이 비어 있음");
} catch (JwtException e) {                       // 최후 방어
    log.warn("JWT 검증 실패 {}", e.getClass().getSimpleName());
}
return false;
```

세 서비스가 문자 그대로 같은 코드였으므로 같은 수정을 세 번 했습니다.

| 서비스 | 파일 |
|---|---|
| toy-content | `app/auth/token/JwtParser.java` |
| toy-chat | `app/auth/token/JwtParser.java` |
| toy-auth-user-region | `app/common/util/JwtProvider.java` |

## 왜 이것만으로 401이 되나

기존 구조가 이미 그렇게 되어 있었습니다.

```
validateToken() 이 false 반환
    → JwtFilter 가 인증을 설정하지 않음
    → JwtFilter 가 response.sendError(401)
```

**즉 catch에 걸리는 예외는 이미 401이 나가고 있었습니다.** 빠진 것은
`SignatureException`이 그 목록에 없다는 것 하나였습니다.

## 마지막 `catch (JwtException e)` 를 넣은 이유

`JwtException`은 jjwt가 던지는 모든 예외의 부모이고 **`io.jsonwebtoken` 패키지라 와일드카드에
잡힙니다.** 앞으로 새 예외 타입이 생겨도 500으로 새지 않습니다.

같은 실수가 다시 나지 않게 하는 장치입니다.

## 왜 이 결함만 즉시 고쳤나

원칙은 **결함을 발견해도 고치지 않는 것**입니다. 회차 N을 baseline으로 고정해야 전후 델타가
성립하고, 고치면 문항이 소멸합니다.

이건 예외였습니다.

- AU-3는 **조사가 0회**라 깨질 baseline이 없다
- 이 결함을 두면 **채점 기준이 성립하지 않는다** — 401 사유가 없으면 만료와 서명 오류를
  구별할 수단이 아예 없다

같은 파일에서 발견한 다른 결함 둘(`traceId=NONE`, 500을 `200`으로 로깅)은 **일부러 고치지
않았습니다.** 전 문항 측정용이거나 그 자체가 문항 재료입니다.

## 고치면 탐색이 더 어려워집니다

| 채널 | 수정 전 | 수정 후 (예측) |
|---|---|---|
| Tempo | `status=error` + `exception=SignatureException` **자백** | **에러 신호 소멸** |
| Loki | 스택은 있으나 `traceId=NONE` · 라인 필터 밖 | **`WARN` + traceId** |

**401은 4xx라 span에 error 태그가 안 붙습니다.** 지금은 500이라 트레이스가 원인을 그대로
말해주는데, 고치면 그 신호가 사라지고 로그로 옮겨갑니다.

**관측을 정확하게 만드는 것과 탐색이 쉬운 것은 다른 문제입니다.**

## 현재 상태

| | |
|---|---|
| 코드 수정 | **완료** (2026-07-30 · 세 서비스 컴파일 통과) |
| 배포 | **미완** |
| 검증 | **미완** — 서명 훼손 토큰 1회로 401·WARN·traceId·문구 구별 넷을 확인한다 |
| 남은 구멍 | `parseClaims()` 가 `RuntimeException`을 던진다 → WebSocket CONNECT 경로는 여전히 500 |

**주장 범위**: *"코드를 고쳤고 컴파일을 확인했다"* 까지입니다.
401이 실제로 나가는지, WARN에 traceId가 붙는지는 **배포 후에 측정합니다.**
