package com.yogurtte.rca.triage.plan;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import com.yogurtte.rca.collector.TimeWindow;

class TriagePlanTest {

    private static final TimeWindow SURVEY = new TimeWindow(
            Instant.parse("2026-07-27T09:00:00Z"), Instant.parse("2026-07-27T21:00:00Z"));

    @Test
    void 코드블록_안의_계획을_읽는다() {
        String text = """
                ## 1. 판단
                02:31 무렵 chat-service 에러가 급증했다.

                ## 2. 계획
                ```json
                {
                  "windowStart": "2026-07-27T17:29:00Z",
                  "windowEnd": "2026-07-27T17:40:00Z",
                  "services": ["chat-service"],
                  "traceId": "abc123",
                  "evidence": ["mongodb_up이 0으로 꺾임"],
                  "reason": "알림 저장 실패 구간"
                }
                ```
                """;

        TriagePlan plan = TriagePlan.parse(text, SURVEY);

        assertThat(plan.parsed()).isTrue();
        assertThat(plan.window().start()).isEqualTo(Instant.parse("2026-07-27T17:29:00Z"));
        assertThat(plan.window().end()).isEqualTo(Instant.parse("2026-07-27T17:40:00Z"));
        assertThat(plan.services()).containsExactly("chat-service");
        assertThat(plan.traceId()).isEqualTo("abc123");
        assertThat(plan.evidence()).containsExactly("mongodb_up이 0으로 꺾임");
        assertThat(plan.notes()).isEmpty();
    }

    @Test
    void traceId가_null이어도_계획은_유효하다() {
        // 컨슈머 전멸·파드 부재처럼 이상 트레이스가 생성되지 않는 장애가 실재한다.
        String text = """
                {"windowStart":"2026-07-27T17:00:00Z","windowEnd":"2026-07-27T18:00:00Z",
                 "services":[],"traceId":null,"evidence":[],"reason":"up이 끊긴 구간"}
                """;

        TriagePlan plan = TriagePlan.parse(text, SURVEY);

        assertThat(plan.parsed()).isTrue();
        assertThat(plan.traceId()).isNull();
        assertThat(plan.toScope().hasTraceIds()).isFalse();
        assertThat(plan.toScope().window()).isEqualTo(plan.window());
    }

    @Test
    void 스윕_창을_벗어난_구간은_잘라내고_기록을_남긴다() {
        String text = """
                {"windowStart":"2026-07-26T00:00:00Z","windowEnd":"2026-07-28T00:00:00Z"}
                """;

        TriagePlan plan = TriagePlan.parse(text, SURVEY);

        assertThat(plan.window()).isEqualTo(SURVEY);
        assertThat(plan.notes()).anyMatch(note -> note.contains("잘라냈다"));
    }

    @Test
    void 계획을_못_읽어도_조사를_멈추지_않는다() {
        TriagePlan plan = TriagePlan.parse("JSON을 안 내고 그냥 줄글로 답했다", SURVEY);

        assertThat(plan.parsed()).isFalse();
        assertThat(plan.window()).isEqualTo(SURVEY);
        assertThat(plan.services()).isEmpty();
        assertThat(plan.notes()).anyMatch(note -> note.contains("찾지 못해"));
    }

    @Test
    void 창이_뒤집혀_있으면_스윕_창을_쓴다() {
        String text = """
                {"windowStart":"2026-07-27T18:00:00Z","windowEnd":"2026-07-27T17:00:00Z"}
                """;

        TriagePlan plan = TriagePlan.parse(text, SURVEY);

        assertThat(plan.window()).isEqualTo(SURVEY);
        assertThat(plan.notes()).anyMatch(note -> note.contains("유효한 windowStart/windowEnd가 없어"));
    }

}
