package com.yogurtte.rca.error;

import lombok.Getter;

/** 도메인이 스스로 응답 코드를 정해서 던지는 예외. API 경계에서 그 코드 그대로 매핑된다. */
@Getter
public class RestApiException extends RuntimeException {

    private final ErrorCode errorCode;

    public RestApiException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    /**
     * 코드가 가진 템플릿에 값을 채워 던진다 — 던지는 쪽은 <b>값만</b> 넘기고 문구는 모른다.
     *
     * <p>자리표시자가 없는 코드는 인자 없이 부른다: {@code RestApiException.of(INVALID_PARAMETER)}.
     */
    public static RestApiException of(ErrorCode errorCode, Object... args) {
        return new RestApiException(errorCode, errorCode.format(args));
    }
}
