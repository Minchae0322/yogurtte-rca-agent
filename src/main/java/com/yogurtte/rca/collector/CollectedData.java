package com.yogurtte.rca.collector;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Everything gathered for one investigation. Any field may be null when that source failed -
 * the matching reason is recorded in {@code failures} and shown to the model instead.
 */
public record CollectedData(
        String traceId,
        String traceJson,
        TimeWindow window,
        String errorWarnLogsJson,
        String traceIdLogsJson,
        Map<String, String> metricsJson,
        List<String> failures,
        Map<String, Long> stepMillis) {

    public CollectedData {
        metricsJson = metricsJson == null ? new LinkedHashMap<>() : metricsJson;
        failures = failures == null ? List.of() : List.copyOf(failures);
        stepMillis = stepMillis == null ? new LinkedHashMap<>() : stepMillis;
    }
}
