package com.yogurtte.rca.error;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import org.springframework.validation.FieldError;

/** 오류 응답 본문. {@code errors}는 바인딩 실패일 때만 실린다. */
public record ErrorResponse(
        String code,
        String message,
        @JsonInclude(JsonInclude.Include.NON_EMPTY) List<ValidationError> errors) {

    public record ValidationError(String field, String message) {

        static ValidationError of(FieldError fieldError) {
            return new ValidationError(fieldError.getField(), fieldError.getDefaultMessage());
        }
    }

    /**
     * 예외가 들고 온 문구를 싣는다 — 그 문구는 이미 {@link ErrorCode}의 템플릿이 채워진 것이다.
     *
     * <p>비어 있으면 코드의 문구로 떨어지는데, 이때는 값이 없으므로 자리표시자가 없는
     * 코드여야 한다 (값이 필요한 코드는 {@link RestApiException#of}를 거쳐 항상 채워져 온다).
     */
    public static ErrorResponse of(ErrorCode errorCode, String message) {
        return new ErrorResponse(errorCode.name(),
                (message == null || message.isBlank()) ? errorCode.getMessage() : message,
                List.of());
    }

    public static ErrorResponse of(ErrorCode errorCode, List<FieldError> fieldErrors) {
        return new ErrorResponse(errorCode.name(), errorCode.getMessage(),
                fieldErrors.stream().map(ValidationError::of).toList());
    }
}
