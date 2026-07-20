package com.yogurtte.rca.collector;

import java.time.Instant;
import java.util.List;

/** Query window derived from a trace: earliest span start to latest span end, padded on both sides. */
public record TimeWindow(Instant start, Instant end) {

    public static TimeWindow fromTrace(String traceJson, int paddingSeconds) {
        return fromSpans(TraceSpans.parse(traceJson), paddingSeconds);
    }

    public static TimeWindow fromSpans(List<TraceSpans.Span> spans, int paddingSeconds) {
        if (spans.isEmpty()) {
            return null;
        }
        var minStart = spans.stream().mapToLong(TraceSpans.Span::startNanos).min().orElseThrow();
        var maxEnd = spans.stream().mapToLong(TraceSpans.Span::endNanos).max().orElseThrow();

        return new TimeWindow(
                toInstant(minStart).minusSeconds(paddingSeconds),
                toInstant(maxEnd).plusSeconds(paddingSeconds));
    }

    /** Fallback when the trace is unavailable: a window of the same width around now. */
    public static TimeWindow around(Instant reference, int paddingSeconds) {
        return new TimeWindow(reference.minusSeconds(paddingSeconds), reference.plusSeconds(paddingSeconds));
    }

    private static Instant toInstant(long unixNanos) {
        return Instant.ofEpochSecond(unixNanos / 1_000_000_000L, unixNanos % 1_000_000_000L);
    }
}
