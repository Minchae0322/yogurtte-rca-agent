package com.yogurtte.rca.triage;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 버킷 신호의 창 여유는 {@code step × 2}에서 나온다.
 *
 * <p>이 테스트가 있는 이유 - 압축 생성자에서 {@code incidentPadBucket}에 상수를 채워 두면
 * {@code incidentPadBucketDuration()}의 유도가 조용히 죽는다(2026-08-11 실제로 그 상태였다).
 * 값이 아니라 <b>step을 따라 움직이는가</b>를 본다.
 */
class SurveyPropertiesPadTest {

    private static SurveyProperties with(String step, String padBucket) {
        return new SurveyProperties("Asia/Seoul", 24, 48, step,
                "{ status = error }", "{ duration > %s && status != error }", "3s",
                20, 15, null, null, List.of(), "60s", null, padBucket, List.of(), true);
    }

    @Test
    @DisplayName("pad를 안 주면 step을 따라간다")
    void 미설정이면_step의_두_배() {
        assertThat(with("1m", null).incidentPadBucketDuration()).isEqualTo(Duration.ofMinutes(2));
        assertThat(with("5m", null).incidentPadBucketDuration()).isEqualTo(Duration.ofMinutes(10));
    }

    @Test
    @DisplayName("명시하면 그 값을 쓴다")
    void 설정하면_그대로() {
        assertThat(with("1m", "5m").incidentPadBucketDuration()).isEqualTo(Duration.ofMinutes(5));
    }

    @Test
    @DisplayName("TEMPO 여유는 0이다 — span 시각에 불확실성이 없다")
    void exact_pad는_0() {
        assertThat(with("1m", null).incidentPadExactDuration()).isEqualTo(Duration.ZERO);
    }
}
