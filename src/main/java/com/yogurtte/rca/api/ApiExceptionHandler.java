package com.yogurtte.rca.api;

import lombok.extern.slf4j.Slf4j;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import com.yogurtte.rca.error.ErrorCode;
import com.yogurtte.rca.error.ErrorResponse;
import com.yogurtte.rca.error.RestApiException;

/**
 * 모든 응답은 {@link ErrorResponse}({@code code}·{@code message}·{@code errors}) 한 형식이다.
 * 프레임워크 예외(404/405/잘못된 JSON 등)는 부모 클래스가 처리하고,
 * 실제 조사 실패만 catch-all까지 내려와 스택트레이스와 함께 로깅된다.
 */
@Slf4j
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    /** 도메인이 코드를 정해 던진 예외 — 그 코드를 그대로 쓴다(예: INVALID_TIME_WINDOW). */
    @ExceptionHandler(RestApiException.class)
    ResponseEntity<Object> handleRestApiException(RestApiException e) {
        log.warn("rejected request: {}", e.getMessage());
        return respond(e.getErrorCode(), ErrorResponse.of(e.getErrorCode(), e.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<Object> handleIllegalArgument(IllegalArgumentException e) {
        log.warn("rejected request: {}", e.getMessage());
        return respond(ErrorCode.INVALID_PARAMETER, ErrorResponse.of(ErrorCode.INVALID_PARAMETER, e.getMessage()));
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException e,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        log.warn("rejected request: {}", e.getBindingResult().getFieldErrors());
        return respond(ErrorCode.INVALID_PARAMETER,
                ErrorResponse.of(ErrorCode.INVALID_PARAMETER, e.getBindingResult().getFieldErrors()));
    }

    /** 조사 자체가 실패한 경우. 메시지는 그대로 돌려준다 — 이 API의 호출자가 곧 운영자다. */
    @ExceptionHandler(Exception.class)
    ResponseEntity<Object> handleAllException(Exception e) {
        log.error("investigation failed", e);
        return respond(ErrorCode.INTERNAL_SERVER_ERROR,
                ErrorResponse.of(ErrorCode.INTERNAL_SERVER_ERROR,
                        e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
    }

    private ResponseEntity<Object> respond(ErrorCode errorCode, ErrorResponse body) {
        return ResponseEntity.status(errorCode.getHttpStatus()).body(body);
    }
}
