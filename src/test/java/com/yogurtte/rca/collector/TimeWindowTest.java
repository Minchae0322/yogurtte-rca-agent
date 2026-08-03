package com.yogurtte.rca.collector;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

class TimeWindowTest {

    /** 두 batch에 걸친 span 2개: 시간창은 가장 이른 시작 ~ 가장 늦은 종료를 padding 포함해 덮어야 한다. */
    @Test
    void derivesPaddedWindowFromEarliestStartAndLatestEnd() {
        Instant start = Instant.parse("2026-07-20T10:00:00Z");
        Instant end = Instant.parse("2026-07-20T10:00:03Z");

        String traceJson = """
                {"batches":[
                  {"resource":{"attributes":[{"key":"service.name","value":{"stringValue":"content"}}]},
                   "scopeSpans":[{"spans":[
                     {"name":"publish","startTimeUnixNano":"%d","endTimeUnixNano":"%d"}]}]},
                  {"resource":{"attributes":[{"key":"service.name","value":{"stringValue":"chat"}}]},
                   "scopeSpans":[{"spans":[
                     {"name":"consume","startTimeUnixNano":"%d","endTimeUnixNano":"%d"}]}]}
                ]}
                """.formatted(nanos(start), nanos(start.plusSeconds(1)),
                nanos(start.plusSeconds(2)), nanos(end));

        TimeWindow window = TimeWindow.fromTrace(traceJson, 120);

        assertThat(window).isNotNull();
        assertThat(window.start()).isEqualTo(start.minusSeconds(120));
        assertThat(window.end()).isEqualTo(end.plusSeconds(120));
    }

    @Test
    void returnsNullWhenTraceHasNoUsableSpans() {
        assertThat(TimeWindow.fromTrace(null, 120)).isNull();
        assertThat(TimeWindow.fromTrace("", 120)).isNull();
        assertThat(TimeWindow.fromTrace("{\"batches\":[]}", 120)).isNull();
        assertThat(TimeWindow.fromTrace("not json at all", 120)).isNull();
    }

    @Test
    void fallbackWindowIsCenteredOnTheReferenceInstant() {
        Instant now = Instant.parse("2026-07-20T10:30:00Z");

        TimeWindow window = TimeWindow.around(now, 120);

        assertThat(window.start()).isEqualTo(now.minusSeconds(120));
        assertThat(window.end()).isEqualTo(now.plusSeconds(120));
    }

    @Test
    void parsesServiceNameAndDurationForSpanRanking() {
        Instant base = Instant.parse("2026-07-20T10:00:00Z");
        String traceJson = """
                {"batches":[
                  {"resource":{"attributes":[{"key":"service.name","value":{"stringValue":"chat"}}]},
                   "scopeSpans":[{"spans":[
                     {"name":"fast","startTimeUnixNano":"%d","endTimeUnixNano":"%d"},
                     {"name":"slow","startTimeUnixNano":"%d","endTimeUnixNano":"%d"}]}]}
                ]}
                """.formatted(nanos(base), nanos(base.plusSeconds(1)),
                nanos(base), nanos(base.plusSeconds(5)));

        List<TraceSpans.Span> spans = TraceSpans.parse(traceJson);
        assertThat(spans).hasSize(2);
        assertThat(spans.get(0).service()).isEqualTo("chat");

        // 랭킹은 5초 span을 1초 span보다 위에 둔다.
        String top = TraceSpans.topByDuration(spans, 1);
        assertThat(top).contains("slow").doesNotContain("fast");
    }

    private static long nanos(Instant instant) {
        return instant.getEpochSecond() * 1_000_000_000L + instant.getNano();
    }
}
