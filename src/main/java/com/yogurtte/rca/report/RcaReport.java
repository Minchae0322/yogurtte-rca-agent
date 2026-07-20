package com.yogurtte.rca.report;

import java.time.Instant;
import java.util.List;

public record RcaReport(
        String traceId,
        String question,
        Instant startedAt,
        String llmProvider,
        String analysis,
        long inputTokens,
        long outputTokens,
        long totalElapsedMs,
        Timings timings,
        int contextChars,
        List<String> collectionFailures) {
}
