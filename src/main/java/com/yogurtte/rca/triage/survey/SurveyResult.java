package com.yogurtte.rca.triage.survey;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import com.fasterxml.jackson.databind.JsonNode;
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
        String slowTraceSearchJson,
        String logRatesJson,
        String logSignatureRatesJson,
        Map<String, String> metricsJson,
        List<String> failures,
        Map<String, Long> stepMillis) {

    /** 지문 쿼리를 안 켠 회차와 테스트가 쓰는 형태. */
    public SurveyResult(TimeWindow window, String timeExpression, String traceSearchJson,
                        String slowTraceSearchJson, String logRatesJson,
                        Map<String, String> metricsJson, List<String> failures, Map<String, Long> stepMillis) {
        this(window, timeExpression, traceSearchJson, slowTraceSearchJson, logRatesJson, null,
                metricsJson, failures, stepMillis);
    }

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
        return dedupeKeepingFirstChannel(Stream.concat(
                parseHits(traceSearchJson, Evidence.TraceHit.CHANNEL_ERROR).stream(),
                parseHits(slowTraceSearchJson, Evidence.TraceHit.CHANNEL_SLOW).stream()).toList());
    }

    /** 에러 채널이 먼저 담기므로, 같은 traceId가 양쪽에 있으면 <b>에러 쪽 기록을 남긴다.</b> */
    private static List<Evidence.TraceHit> dedupeKeepingFirstChannel(List<Evidence.TraceHit> hits) {
        LinkedHashMap<String, Evidence.TraceHit> seen = new LinkedHashMap<>();
        hits.forEach(hit -> seen.putIfAbsent(hit.traceId(), hit));
        return List.copyOf(seen.values());
    }

    /**
     * Tempo {@code /api/search} 응답을 후보 목록으로 읽는다.
     *
     * <p><b>깨진 행을 걸러내지 않고 표기한다.</b> Tempo가 {@code durationMs} 33일짜리 행이나
     * {@code startTimeUnixNano == 0}인 행을 그대로 내려준 사례가 있다(CH-2 실측 · 같은 트레이스를
     * {@code /api/traces/{id}}로 받으면 멀쩡하다). 버리면 그 트레이스에 도달할 길이 없어지므로
     * {@code trusted=false}로 남기고 <b>정렬과 창 계산에서만 뺀다.</b>
     */
    private List<Evidence.TraceHit> parseHits(String json, String channel) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            JsonNode traces = MAPPER.readTree(json).path("traces");
            if (!traces.isArray()) {
                return List.of();
            }
            long windowSeconds = window == null ? Long.MAX_VALUE
                    : Duration.between(window.start(), window.end()).getSeconds();
            return StreamSupport.stream(traces.spliterator(), false)
                    .map(node -> toHit(node, channel, windowSeconds))
                    .toList();
        } catch (Exception e) {
            // 파싱 실패는 후보 0건으로 떨어진다. 조사를 멈추지 않는다.
            return List.of();
        }
    }

    private static Evidence.TraceHit toHit(JsonNode node, String channel, long windowSeconds) {
        long durationMs = node.path("durationMs").asLong(0L);
        Long startNanos = parseLong(node.path("startTimeUnixNano").asText(null));
        Instant startedAt = (startNanos == null || startNanos <= 0L) ? null
                : Instant.ofEpochSecond(startNanos / 1_000_000_000L, startNanos % 1_000_000_000L);

        // 창보다 긴 duration은 물리적으로 불가능하다 — 창 안에서 검색한 결과이므로.
        boolean durationSane = durationMs >= 0 && durationMs / 1000 <= windowSeconds;

        return new Evidence.TraceHit(
                node.path("traceID").asText(""),
                node.path("rootServiceName").asText(""),
                node.path("rootTraceName").asText(""),
                durationMs,
                channel,
                startedAt,
                startedAt != null && durationSane,
                serviceStatsOf(node));
    }

    /**
     * {@code serviceStats: {"chat-service": {"spanCount":14, "errorCount":1}}} → 렌더된 줄 목록.
     *
     * <p><b>errorCount가 0이면 적지 않는다</b> — 어느 서비스에서 에러가 났는지가 신호이고,
     * 0을 다 적으면 그 하나가 묻힌다. Tempo가 주는 순서를 그대로 둔다.
     */
    private static List<String> serviceStatsOf(JsonNode node) {
        JsonNode stats = node.path("serviceStats");
        if (!stats.isObject()) {
            return List.of();
        }
        List<String> rendered = new java.util.ArrayList<>();
        stats.properties().forEach(entry -> {
            int spans = entry.getValue().path("spanCount").asInt(0);
            int errors = entry.getValue().path("errorCount").asInt(0);
            rendered.add(entry.getKey() + " " + spans + (errors > 0 ? " (err " + errors + ")" : ""));
        });
        return List.copyOf(rendered);
    }

    private static Long parseLong(String raw) {
        try {
            return raw == null ? null : Long.parseLong(raw);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
