package com.yogurtte.rca.collector;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Tempo의 OTLP 형태 트레이스 JSON을 평평한 span 리스트로 펼친다.
 * TimeWindow와 컨텍스트 조립기가 둘 다 쓰므로 static 헬퍼로 여기에 둔다.
 */
public final class TraceSpans {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private TraceSpans() {
    }

    public record Span(String service, String name, long startNanos, long endNanos) {
        public long durationNanos() {
            return endNanos - startNanos;
        }

        public double durationMillis() {
            return durationNanos() / 1_000_000.0;
        }
    }

    public static List<Span> parse(String traceJson) {
        var spans = new ArrayList<Span>();
        if (traceJson == null || traceJson.isBlank()) {
            return spans;
        }
        JsonNode root;
        try {
            root = MAPPER.readTree(traceJson);
        } catch (Exception e) {
            return spans;
        }

        // Tempo는 "batches"로 응답한다; 일부 버전/내보내기는 OTLP 이름인 "resourceSpans"를 쓴다.
        var batches = root.has("batches") ? root.get("batches") : root.get("resourceSpans");
        if (batches == null || !batches.isArray()) {
            return spans;
        }

        for (var batch : batches) {
            var service = serviceName(batch);
            var scopeSpans = batch.has("scopeSpans")
                    ? batch.get("scopeSpans")
                    : batch.get("instrumentationLibrarySpans");
            if (scopeSpans == null || !scopeSpans.isArray()) {
                continue;
            }
            for (var scope : scopeSpans) {
                var spanArray = scope.get("spans");
                if (spanArray == null || !spanArray.isArray()) {
                    continue;
                }
                for (var span : spanArray) {
                    var start = span.path("startTimeUnixNano").asLong(0L);
                    var end = span.path("endTimeUnixNano").asLong(0L);
                    if (start <= 0) {
                        continue;
                    }
                    spans.add(new Span(service, span.path("name").asText(""), start, Math.max(end, start)));
                }
            }
        }
        return spans;
    }

    /** 가장 긴 span N개를 압축된 텍스트로 렌더링한다. 원본 트레이스가 너무 커서 통째로 못 넣을 때 사용. */
    public static String topByDuration(List<Span> spans, int limit) {
        var sorted = new ArrayList<>(spans);
        sorted.sort((a, b) -> Long.compare(b.durationNanos(), a.durationNanos()));

        var sb = new StringBuilder();
        sorted.stream().limit(limit).forEach(span -> sb
                .append(String.format("%.2fms", span.durationMillis()))
                .append("  ")
                .append(span.service().isBlank() ? "?" : span.service())
                .append("  ")
                .append(span.name())
                .append('\n'));
        return sb.toString();
    }

    private static String serviceName(JsonNode batch) {
        var attributes = batch.path("resource").path("attributes");
        if (attributes.isArray()) {
            for (var attribute : attributes) {
                if ("service.name".equals(attribute.path("key").asText())) {
                    return attribute.path("value").path("stringValue").asText("");
                }
            }
        }
        return "";
    }
}
