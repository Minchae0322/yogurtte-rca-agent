package com.yogurtte.rca.collector;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 한 번의 조사를 위해 수집한 전부. 소스가 실패한 필드는 null일 수 있다 -
 * 그 사유는 {@code failures}에 기록되어 대신 모델에게 보여진다.
 *
 * @param correlationId 원본 파일·리포트 식별자. 트레이스가 하나면 그 traceId, 여럿이면 {@code scan-…}
 * @param traceJsons    조사한 트레이스 원본. traceId → JSON, <b>순서가 곧 우선순위</b>
 *                      (탐색이 지목한 것 → 창 안 후보). <b>대표를 따로 두지 않는다</b> —
 *                      어느 것이 원인인지는 전문을 봐야 알고, 하나를 앞세우면 그 선택이
 *                      분석의 초점을 끌어간다(AP-1 회차 3 실측).
 */
public record CollectedData(
        String correlationId,
        TimeWindow window,
        String errorWarnLogsJson,
        String traceIdLogsJson,
        Map<String, String> metricsJson,
        Map<String, String> traceJsons,
        List<String> failures,
        Map<String, Long> stepMillis) {

    public CollectedData {
        metricsJson = metricsJson == null ? new LinkedHashMap<>() : metricsJson;
        traceJsons = traceJsons == null ? new LinkedHashMap<>() : traceJsons;
        failures = failures == null ? List.of() : List.copyOf(failures);
        stepMillis = stepMillis == null ? new LinkedHashMap<>() : stepMillis;
    }
}
