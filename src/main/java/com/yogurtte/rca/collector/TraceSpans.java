package com.yogurtte.rca.collector;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Tempo의 OTLP 형태 트레이스 JSON을 평평한 span 리스트로 펼친다.
 * TimeWindow와 컨텍스트 조립기가 둘 다 쓰므로 static 헬퍼로 여기에 둔다.
 */
public final class  TraceSpans {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private TraceSpans() {
    }

    /**
     * @param spanId       base64 그대로다. 디코딩하지 않는다 — 문자열 그대로 키로 쓰면 부모를 찾는다.
     * @param parentSpanId 루트 span이면 빈 문자열.
     * @param attributes   span 태그. 키 이름은 계약이 아니라 계측 라이브러리의 관례다
     *                     (OTel 컨벤션이면 {@code db.statement}일 것이 실제로는 {@code jdbc.query[0]}이다).
     * @param eventNames   span events의 이름만. 12개 시큐리티 필터가 span이 아니라 여기 들어 있다 —
     *                     span 이름만 세면 필터 span 수를 오독한다(실측).
     */
    public record Span(String service, String name, String spanId, String parentSpanId,
                       Map<String, String> attributes, List<String> eventNames,
                       long startNanos, long endNanos) {

        public Span {
            attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
            eventNames = eventNames == null ? List.of() : List.copyOf(eventNames);
        }

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
                    spans.add(new Span(service, span.path("name").asText(""),
                            span.path("spanId").asText(""), span.path("parentSpanId").asText(""),
                            attributesOf(span), eventNamesOf(span),
                            start, Math.max(end, start)));
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

    private static Map<String, String> attributesOf(JsonNode span) {
        var map = new LinkedHashMap<String, String>();
        var attributes = span.path("attributes");
        if (!attributes.isArray()) {
            return map;
        }
        for (var attribute : attributes) {
            var key = attribute.path("key").asText("");
            if (key.isEmpty()) {
                continue;
            }
            var value = attribute.path("value");
            if (value.hasNonNull("stringValue")) {
                map.put(key, value.get("stringValue").asText(""));
            } else if (value.hasNonNull("intValue")) {
                map.put(key, value.get("intValue").asText(""));
            } else if (value.hasNonNull("boolValue")) {
                map.put(key, value.get("boolValue").asText(""));
            } else if (value.hasNonNull("doubleValue")) {
                map.put(key, value.get("doubleValue").asText(""));
            }
        }
        return map;
    }

    private static List<String> eventNamesOf(JsonNode span) {
        var events = span.path("events");
        if (!events.isArray() || events.isEmpty()) {
            return List.of();
        }
        var names = new ArrayList<String>();
        for (var event : events) {
            var name = event.path("name").asText("");
            if (!name.isEmpty()) {
                names.add(name);
            }
        }
        return names;
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
