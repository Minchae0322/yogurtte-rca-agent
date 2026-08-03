package com.yogurtte.rca.collector;

import java.time.Instant;
import java.util.List;

/** 트레이스에서 유도한 조회 시간창: 가장 이른 span 시작 ~ 가장 늦은 span 종료, 양쪽에 padding. */
public record TimeWindow(Instant start, Instant end) {

    public static TimeWindow fromTrace(String traceJson, int paddingSeconds) {
        return fromSpans(TraceSpans.parse(traceJson), paddingSeconds);
    }

    public static TimeWindow fromSpans(List<TraceSpans.Span> spans, int paddingSeconds) {
        if (spans.isEmpty()) {
            return null;
        }
        long minStart = spans.stream().mapToLong(TraceSpans.Span::startNanos).min().orElseThrow();
        long maxEnd = spans.stream().mapToLong(TraceSpans.Span::endNanos).max().orElseThrow();

        return new TimeWindow(
                toInstant(minStart).minusSeconds(paddingSeconds),
                toInstant(maxEnd).plusSeconds(paddingSeconds));
    }

    /** 트레이스를 못 구했을 때의 대체: 기준 시각을 중심으로 같은 폭의 시간창. */
    public static TimeWindow around(Instant reference, int paddingSeconds) {
        return new TimeWindow(reference.minusSeconds(paddingSeconds), reference.plusSeconds(paddingSeconds));
    }

    private static Instant toInstant(long unixNanos) {
        return Instant.ofEpochSecond(unixNanos / 1_000_000_000L, unixNanos % 1_000_000_000L);
    }
}
