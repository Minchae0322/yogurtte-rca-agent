package com.yogurtte.rca.error;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;

/**
 * 응답 코드 · HTTP 상태 · 문구를 한 곳에 묶는다. 코드는 enum 이름이 그대로 나간다.
 *
 * <p><b>문구는 여기에만 둔다.</b> 값이 들어가야 하면 {@code %s} 자리표시자를 가진 템플릿으로
 * 적고 {@link RestApiException#of(ErrorCode, Object...)} 가 채운다 — 던지는 자리마다 문구를
 * 손으로 쓰면 같은 코드가 자리마다 다른 말을 하게 되고, 무엇이 나가는지 한눈에 볼 수 없다.
 */
@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    INVALID_PARAMETER(HttpStatus.BAD_REQUEST, "Invalid parameter included"),

    // ---- 시간창 (B-26) — 조사 실패(500)가 아니라 요청 거부(400)다 ----
    //
    // 사유마다 코드를 나눈 이유: 셋 다 400이지만 호출자가 고쳐야 할 것이 다르다
    // (빠뜨린 값을 채운다 / 과거로 옮긴다 / 순서를 바꾼다).

    /**
     * from/to를 하나만 준 요청. 구 파서는 조용히 무시하고 질문 파싱으로 떨어져
     * <b>오늘 창을 조사하고도 눈치채지 못했다</b> (결함 22 — 과거 데이터 재조사가 이 경로를 탄다).
     */
    INVALID_TIME_WINDOW_INCOMPLETE(HttpStatus.BAD_REQUEST,
            "from/to는 함께 지정해야 한다 — 하나만 주면 무시된 채 질문 파싱으로 떨어져 "
                    + "의도치 않은 창을 조사하게 된다 (받은 값: from=%s, to=%s)"),

    INVALID_TIME_WINDOW_FUTURE(HttpStatus.BAD_REQUEST, "from이 현재 이후다: from=%s, now=%s"),

    INVALID_TIME_WINDOW_REVERSED(HttpStatus.BAD_REQUEST, "from이 to보다 늦거나 같다: from=%s, to=%s"),

    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error"),
    ;

    private final HttpStatus httpStatus;
    private final String message;

    /** 템플릿에 값을 채운다. 자리표시자가 없는 코드는 인자 없이 불려 문구가 그대로 나간다. */
    public String format(Object... args) {
        return args.length == 0 ? message : message.formatted(args);
    }
}
