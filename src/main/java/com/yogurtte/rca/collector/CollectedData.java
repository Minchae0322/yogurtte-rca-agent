package com.yogurtte.rca.collector;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 한 번의 조사를 위해 수집한 전부. 소스가 실패한 필드는 null일 수 있다 -
 * 그 사유는 {@code failures}에 기록되어 대신 모델에게 보여진다.
 *
 * @param candidateTraceJsons 창 안 후보 트레이스 원본(B-9). traceId → JSON, 선정 트레이스는
 *                            제외. 탐색을 거친 조사(창이 명시된 Scope)에서만 채워진다.
 */
public record CollectedData(
        String traceId,
        String traceJson,
        TimeWindow window,
        String errorWarnLogsJson,
        String traceIdLogsJson,
        Map<String, String> metricsJson,
        Map<String, String> candidateTraceJsons,
        List<String> failures,
        Map<String, Long> stepMillis) {

    public CollectedData {
        metricsJson = metricsJson == null ? new LinkedHashMap<>() : metricsJson;
        candidateTraceJsons = candidateTraceJsons == null ? new LinkedHashMap<>() : candidateTraceJsons;
        failures = failures == null ? List.of() : List.copyOf(failures);
        stepMillis = stepMillis == null ? new LinkedHashMap<>() : stepMillis;
    }
}
