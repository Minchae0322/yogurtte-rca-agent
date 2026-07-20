package com.yogurtte.rca.report;

import java.time.Instant;
import java.util.List;

/** promptSource는 이 조사에 쓰인 시스템 프롬프트의 출처(외부 파일 경로 또는 classpath) - 프롬프트 튜닝 비교용. */
public record RcaReport(
        String traceId,
        String question,
        Instant startedAt,
        String llmProvider,
        String promptSource,
        String analysis,
        long inputTokens,
        long outputTokens,
        long totalElapsedMs,
        Timings timings,
        int contextChars,
        List<String> collectionFailures) {
}
