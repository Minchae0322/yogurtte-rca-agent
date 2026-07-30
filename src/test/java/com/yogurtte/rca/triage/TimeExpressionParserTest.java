package com.yogurtte.rca.triage;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * 시간창 해석은 <b>결정적이어야 한다</b> — 같은 질문이 회차마다 다른 창을 만들면 재현이 깨지고,
 * 탐색 채점의 "시간창을 맞게 잡았는가"를 분석 점수와 분리해서 잴 수 없다.
 */
class TimeExpressionParserTest {

    /** 2026-07-28T05:00Z = 같은 날 14:00 KST. "어제"가 7/27이 되는 시각이다. */
    private static final Instant NOW = Instant.parse("2026-07-28T05:00:00Z");

    private final TimeExpressionParser parser = new TimeExpressionParser(
            new SurveyProperties("Asia/Seoul", 24, 48, "5m", null, null, null, 20, null, List.of(),
                    null, null, null));

    @Test
    void 어젯밤은_어제_18시부터_오늘_6시까지다() {
        var resolved = parser.resolve("어젯밤에 댓글 알림이 안 왔어요", null, null, NOW);

        // 어제 18:00 KST = 07-27T09:00Z, 오늘 06:00 KST = 07-27T21:00Z
        assertThat(resolved.window().start()).isEqualTo(Instant.parse("2026-07-27T09:00:00Z"));
        assertThat(resolved.window().end()).isEqualTo(Instant.parse("2026-07-27T21:00:00Z"));
        assertThat(resolved.expression()).contains("어젯밤");
    }

    @Test
    void 어제는_하루_전체다() {
        var resolved = parser.resolve("어제 무슨 일 있었어?", null, null, NOW);

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
        var resolved = parser.resolve("댓글 알림이 안 와요", null, null, NOW);

        assertThat(resolved.window().end()).isEqualTo(NOW);
        assertThat(Duration.between(resolved.window().start(), NOW)).isEqualTo(Duration.ofHours(24));
        assertThat(resolved.expression()).contains("시간 표현 없음");
    }

    @Test
    void 명시적_from_to가_질문보다_우선한다() {
        var from = Instant.parse("2026-07-20T00:00:00Z");
        var to = Instant.parse("2026-07-20T01:00:00Z");

        var resolved = parser.resolve("어젯밤에 안 왔어요", from, to, NOW);

        assertThat(resolved.window().start()).isEqualTo(from);
        assertThat(resolved.window().end()).isEqualTo(to);
        assertThat(resolved.expression()).isEqualTo("명시적 from/to");
    }

    @Test
    void 상한을_넘으면_끝을_기준으로_자른다() {
        // 장애는 대개 창의 끝(최근)에 가까우므로 앞을 버린다.
        var resolved = parser.resolve("지난 100시간", null, null, NOW);

        assertThat(resolved.window().end()).isEqualTo(NOW);
        assertThat(Duration.between(resolved.window().start(), NOW)).isEqualTo(Duration.ofHours(48));
        assertThat(resolved.expression()).contains("상한");
    }
}
