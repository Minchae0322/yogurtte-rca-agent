package com.yogurtte.rca.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.yogurtte.rca.error.ErrorCode;
import com.yogurtte.rca.error.ErrorResponse;
import com.yogurtte.rca.error.RestApiException;

/** 예외가 어느 code·status로 나가는지만 본다 — 매핑이 뒤집히면 여기서 깨진다. */
class ApiExceptionHandlerTest {

    private final ApiExceptionHandler handler = new ApiExceptionHandler();

    @Test
    void 시간창_결함은_조사_실패가_아니라_요청_거부다() {
        ResponseEntity<Object> response =
                handler.handleRestApiException(
                        new RestApiException(ErrorCode.INVALID_TIME_WINDOW_REVERSED, "from이 to보다 늦다"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat((ErrorResponse) response.getBody())
                .isEqualTo(new ErrorResponse(ErrorCode.INVALID_TIME_WINDOW_REVERSED.name(), "from이 to보다 늦다", java.util.List.of()));
    }

    @Test
    void 그밖의_실패는_500이고_메시지를_잃지_않는다() {
        ResponseEntity<Object> response = handler.handleAllException(new IllegalStateException("tempo 연결 실패"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(((ErrorResponse) response.getBody()).message()).isEqualTo("tempo 연결 실패");
    }

    @Test
    void 메시지가_없으면_예외_이름이라도_남긴다() {
        ResponseEntity<Object> response = handler.handleAllException(new NullPointerException());

        assertThat(((ErrorResponse) response.getBody()).message()).isEqualTo("NullPointerException");
    }
}
