package com.yogurtte.rca.triage;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yogurtte.rca.collector.TimeWindow;
import com.yogurtte.rca.report.Evidence;

/**
 * 1단계 스윕의 결과. 3채널을 <b>동시에</b> 훑은 집계값이고, 원인이 아니라 "어디를 볼지"의
 * 후보다.
 *
 * <p>세 채널을 다 거는 이유는 실측이다 — Tempo 에러 검색만으로는 정의된 12문항 중 6문항을
 * 못 찾는다. 컨슈머가 죽거나(CH-2) 파드가 0이면(AU-2) 트레이스가 생성되지 않고, 예외가
 * 로그에만 남는 문항(AP-2)은 trace에 {@code exception=none}으로 찍힌다.
 *
 * @param traceSearchJson Tempo 검색 결과. {@code null}이면 이 채널로는 아무것도 못 찾은 것이다.
 * @param logRatesJson    서비스별 ERROR/WARN 발생률 곡선
 * @param metricsJson     PromQL별 집계 시계열
 * @param failures        실패·무신호 사유. <b>지우지 않고 그대로 모델에게 보인다.</b>
 */
public record SurveyResult(
        TimeWindow window,
        String timeExpression,
        String traceSearchJson,
        String logRatesJson,
        Map<String, String> metricsJson,
        List<String> failures,
        Map<String, Long> stepMillis) {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public SurveyResult {
        metricsJson = metricsJson == null ? new LinkedHashMap<>() : metricsJson;
        failures = failures == null ? List.of() : List.copyOf(failures);
        stepMillis = stepMillis == null ? new LinkedHashMap<>() : stepMillis;
    }

    public long totalMillis() {
        return stepMillis.values().stream().mapToLong(Long::longValue).sum();
    }

    /**
     * Tempo 검색이 찾은 트레이스 목록.
     *
     * <p>리포트에 <b>고른 것만이 아니라 후보 전부</b>를 남긴다 — 회고에서 "다른 걸 골랐어야
     * 했나"를 판단하려면 그때 무엇이 보였는지가 있어야 한다.
     */
    public List<Evidence.TraceHit> traceHits() {
        if (traceSearchJson == null || traceSearchJson.isBlank()) {
            return List.of();
        }
        try {
            var traces = MAPPER.readTree(traceSearchJson).path("traces");
            if (!traces.isArray()) {
                return List.of();
            }
            var hits = new ArrayList<Evidence.TraceHit>();
            traces.forEach(node -> hits.add(new Evidence.TraceHit(
                    node.path("traceID").asText(""),
                    node.path("rootServiceName").asText(""),
                    node.path("rootTraceName").asText(""),
                    node.path("durationMs").asLong(0L))));
            return List.copyOf(hits);
        } catch (Exception e) {
            return List.of();
        }
    }
}
