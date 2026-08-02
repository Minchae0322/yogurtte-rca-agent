package com.yogurtte.rca.api;

import lombok.extern.slf4j.Slf4j;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import com.yogurtte.rca.triage.InvalidTimeWindowException;

/**
 * 프레임워크 예외(404/405/잘못된 JSON 등)는 부모 클래스가 Spring 표준 ProblemDetail로 처리한다.
 * 실제 조사 실패만 catch-all까지 내려와 스택트레이스와 함께 로깅된다.
 */
@Slf4j
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException e,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        var detail = e.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + " " + error.getDefaultMessage())
                .reduce((a, b) -> a + "; " + b)
                .orElse("invalid request");
        log.warn("rejected request: {}", detail);
        return ResponseEntity.badRequest().body(ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail));
    }

    /** 시간창 입력 결함은 조사 실패(500)가 아니라 요청 거부(400)다 — 조용히 오독하던 것을 B-26에서 거부로 바꿨다. */
    @ExceptionHandler(InvalidTimeWindowException.class)
    ProblemDetail invalidTimeWindow(InvalidTimeWindowException e) {
        log.warn("rejected time window: {}", e.getMessage());
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail investigationFailed(Exception e) {
        log.error("investigation failed", e);
        return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR,
                e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
    }
}
