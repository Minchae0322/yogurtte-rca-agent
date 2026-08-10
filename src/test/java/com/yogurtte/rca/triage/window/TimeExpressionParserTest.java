package com.yogurtte.rca.triage.window;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.yogurtte.rca.error.ErrorCode;
import com.yogurtte.rca.error.RestApiException;
import com.yogurtte.rca.time.Confidence;
import com.yogurtte.rca.triage.SurveyProperties;
import com.yogurtte.rca.triage.TriageConfig;

/**
 * 시간창 해석은 <b>결정적이어야 한다</b> — 같은 질문이 회차마다 다른 창을 만들면 재현이 깨지고,
 * 탐색 채점의 "시간창을 맞게 잡았는가"를 분석 점수와 분리해서 잴 수 없다.
 */
class TimeExpressionParserTest {

    /** 2026-07-28T05:00Z = 같은 날 14:00 KST. "어제"가 7/27이 되는 시각이다. */
    private static final Instant NOW = Instant.parse("2026-07-28T05:00:00Z");

    private final TimeExpressionParser parser = new TriageConfig().timeExpressionParser(
            new SurveyProperties("Asia/Seoul", 24, 48, "5m", null, null, null, 20, 15, null, null, List.of(),
                    null, null, null, null, true));

    @Test
    void 어젯밤은_어제_18시부터_오늘_6시까지다() {
        TimeExpressionParser.Resolved resolved = parser.resolve("어젯밤에 댓글 알림이 안 왔어요", null, null, NOW);

        // 어제 18:00 KST = 07-27T09:00Z, 오늘 06:00 KST = 07-27T21:00Z
        assertThat(resolved.window().start()).isEqualTo(Instant.parse("2026-07-27T09:00:00Z"));
        assertThat(resolved.window().end()).isEqualTo(Instant.parse("2026-07-27T21:00:00Z"));
        assertThat(resolved.expression()).contains("어젯밤");
    }

    @Test
    void 어제는_하루_전체다() {
        TimeExpressionParser.Resolved resolved = parser.resolve("어제 무슨 일 있었어?", null, null, NOW);

        assertThat(resolved.window().start()).isEqualTo(Instant.parse("2026-07-26T15:00:00Z"));
        assertThat(resolved.window().end()).isEqualTo(Instant.parse("2026-07-27T15:00:00Z"));
    }

    @Test
    void 상대_표현을_읽는다() {
        assertThat(Duration.between(
                parser.resolve("지난 3시간 오류 찾아줘", null, null, NOW).window().start(), NOW))
                .isEqualTo(Duration.ofHours(3));
        assertThat(Duration.between(
                parser.resolve("30분 전부터 이상해요", null, null, NOW).window().start(), NOW))
                .isEqualTo(Duration.ofMinutes(30));
        assertThat(Duration.between(
                parser.resolve("last 2 hours please", null, null, NOW).window().start(), NOW))
                .isEqualTo(Duration.ofHours(2));
    }

    @Test
    void 시간_표현이_없으면_지어내지_않고_기본_조회폭으로_떨어진다() {
        TimeExpressionParser.Resolved resolved = parser.resolve("댓글 알림이 안 와요", null, null, NOW);

        assertThat(resolved.window().end()).isEqualTo(NOW);
        assertThat(Duration.between(resolved.window().start(), NOW)).isEqualTo(Duration.ofHours(24));
        assertThat(resolved.expression()).contains("시간 표현 없음");
    }

    @Test
    void 명시적_from_to가_질문보다_우선한다() {
        Instant from = Instant.parse("2026-07-20T00:00:00Z");
        Instant to = Instant.parse("2026-07-20T01:00:00Z");

        TimeExpressionParser.Resolved resolved = parser.resolve("어젯밤에 안 왔어요", from, to, NOW);

        assertThat(resolved.window().start()).isEqualTo(from);
        assertThat(resolved.window().end()).isEqualTo(to);
        assertThat(resolved.expression()).isEqualTo("명시적 from/to");
    }

    @Test
    void 상한을_넘으면_끝을_기준으로_자른다() {
        // 장애는 대개 창의 끝(최근)에 가까우므로 앞을 버린다.
        TimeExpressionParser.Resolved resolved = parser.resolve("지난 100시간", null, null, NOW);

        assertThat(resolved.window().end()).isEqualTo(NOW);
        assertThat(Duration.between(resolved.window().start(), NOW)).isEqualTo(Duration.ofHours(48));
        assertThat(resolved.expression()).contains("상한");
    }

    // ---- B-26 재작성에서 잡은 오독들 (결함 22) ----

    @Test
    void 절대_시각을_최근N분으로_오독하지_않는다() {
        // 구 파서: "20분"이 상대 표현으로 매치 → 최근 20분. 상대성 표지가 없으면 시각이다.
        // 16:00 KST에 물었으므로 오늘 14:20 KST(05:20Z) ± 30분.
        Instant afternoon = Instant.parse("2026-07-28T07:00:00Z");

        TimeExpressionParser.Resolved resolved = parser.resolve("14시 20분쯤에 앱이 버벅였다는 제보가 있어요", null, null, afternoon);

        assertThat(resolved.window().start()).isEqualTo(Instant.parse("2026-07-28T04:50:00Z"));
        assertThat(resolved.window().end()).isEqualTo(Instant.parse("2026-07-28T05:50:00Z"));
        assertThat(resolved.confidence()).isEqualTo(Confidence.APPROX);
    }

    @Test
    void 어제_새벽은_오늘로_밀리지_않는다() {
        // 구 파서: "새벽" 분기가 "어제"보다 먼저라 하루가 밀렸다. 후보를 결합하면 어제의 새벽이다.
        TimeExpressionParser.Resolved resolved = parser.resolve("어제 새벽에 앱이 이상했대요", null, null, NOW);

        assertThat(resolved.window().start()).isEqualTo(Instant.parse("2026-07-26T15:00:00Z"));
        assertThat(resolved.window().end()).isEqualTo(Instant.parse("2026-07-26T21:00:00Z"));
        assertThat(resolved.confidence()).isEqualTo(Confidence.EXACT);
    }

    @Test
    void 날짜와_시각이_결합된다() {
        TimeExpressionParser.Resolved resolved = parser.resolve("어제 14시 20분쯤 앱이 버벅였어요", null, null, NOW);

        assertThat(resolved.window().start()).isEqualTo(Instant.parse("2026-07-27T04:50:00Z"));
        assertThat(resolved.window().end()).isEqualTo(Instant.parse("2026-07-27T05:50:00Z"));
    }

    @Test
    void 날짜_없는_미래_시각은_가장_가까운_과거로_해석한다() {
        // 14:00 KST에 "14시 20분"을 물으면 오늘 14:20은 미래다 → 어제 14:20.
        TimeExpressionParser.Resolved resolved = parser.resolve("14시 20분쯤 버벅였다는 얘기가 있어요", null, null, NOW);

        assertThat(resolved.window().start()).isEqualTo(Instant.parse("2026-07-27T04:50:00Z"));
        assertThat(resolved.window().end()).isEqualTo(Instant.parse("2026-07-27T05:50:00Z"));
    }

    @Test
    void 시각_구간을_읽는다() {
        Instant afternoon = Instant.parse("2026-07-28T07:00:00Z");

        TimeExpressionParser.Resolved resolved = parser.resolve("10시부터 11시까지 로그인이 느렸어요", null, null, afternoon);

        assertThat(resolved.window().start()).isEqualTo(Instant.parse("2026-07-28T01:00:00Z"));
        assertThat(resolved.window().end()).isEqualTo(Instant.parse("2026-07-28T02:00:00Z"));
        assertThat(resolved.confidence()).isEqualTo(Confidence.EXACT);
    }

    @Test
    void 시간대_단독이_미래면_어제로_해석한다() {
        // 14:00 KST에 "저녁"을 물으면 오늘 저녁은 미래다 → 어제 저녁 17~21시.
        TimeExpressionParser.Resolved resolved = parser.resolve("저녁에 앱이 이상했어요", null, null, NOW);

        assertThat(resolved.window().start()).isEqualTo(Instant.parse("2026-07-27T08:00:00Z"));
        assertThat(resolved.window().end()).isEqualTo(Instant.parse("2026-07-27T12:00:00Z"));
    }

    @Test
    void 접미_표지도_상대_표현이다() {
        TimeExpressionParser.Resolved resolved = parser.resolve("5분 전에 이상했어요", null, null, NOW);

        assertThat(Duration.between(resolved.window().start(), NOW)).isEqualTo(Duration.ofMinutes(5));
        assertThat(resolved.expression()).isEqualTo("상대 표현 '5분 전'");
    }

    @Test
    void 확신도가_값으로_남는다() {
        assertThat(parser.resolve("최근 1시간 안에 이상했어요", null, null, NOW).confidence())
                .isEqualTo(Confidence.EXACT);
        assertThat(parser.resolve("댓글 알림이 안 와요", null, null, NOW).confidence())
                .isEqualTo(Confidence.FALLBACK);
    }

    // ---- 입력 검증 — 재조사가 타는 from/to 경로 (결함 22) ----

    @Test
    void from만_주면_조용히_무시하지_않고_거부한다() {
        // 구 동작: 무시하고 질문 파싱 → "최근 1시간" → 오늘 창을 조사하고도 눈치채지 못한다.
        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> parser.resolve("아무 질문", Instant.parse("2026-07-27T00:00:00Z"), null, NOW))
                .isInstanceOf(RestApiException.class)
                .extracting(e -> ((RestApiException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_TIME_WINDOW_INCOMPLETE);
    }

    @Test
    void from이_to보다_늦으면_거부한다() {
        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> parser.resolve("아무 질문", Instant.parse("2026-07-27T02:00:00Z"),
                                Instant.parse("2026-07-27T01:00:00Z"), NOW))
                .isInstanceOf(RestApiException.class)
                .extracting(e -> ((RestApiException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_TIME_WINDOW_REVERSED);
    }

    @Test
    void 미래_to는_now로_잘린다() {
        TimeExpressionParser.Resolved resolved = parser.resolve("아무 질문", Instant.parse("2026-07-28T04:00:00Z"),
                Instant.parse("2026-07-29T00:00:00Z"), NOW);

        assertThat(resolved.window().end()).isEqualTo(NOW);
        assertThat(resolved.expression()).contains("잘림");
    }
}
