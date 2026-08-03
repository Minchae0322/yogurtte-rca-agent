package com.yogurtte.rca.analyzer;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yogurtte.rca.collector.CollectedData;
import com.yogurtte.rca.collector.TraceSpans;
import com.yogurtte.rca.report.Evidence;

/**
 * 수집한 원본 JSON에서 <b>사람이 회고할 수 있는 형태</b>의 관측값을 뽑는다.
 *
 * <p>리포트에 LLM의 서술만 남으면 나중에 그 서술이 맞았는지 확인할 수 없다. 모델이 바꿔 쓴
 * 문장이 아니라 로그 원문·span 시각·메트릭 값이 함께 있어야 채점과 회고가 성립한다.
 *
 * <p>추출은 <b>가공이 아니라 발췌</b>다 — 값을 바꾸거나 해석을 붙이지 않고, 큰 것만 잘라낸다.
 */
@Component
public class EvidenceExtractor {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** 로그 원문 발췌 상한. 넘으면 ERROR/WARN 줄을 우선 남기고 전체 건수를 따로 기록한다. */
    private static final int MAX_LOG_SAMPLES = 60;
    private static final int MAX_TOP_SPANS = 15;
    private static final int MAX_SERIES_PER_QUERY = 8;

    public Evidence extract(CollectedData data) {
        // 수집한 트레이스 전부에서 뽑는다 — 대표를 세우지 않으므로 span도 한 트레이스에
        // 한정하지 않는다. duration 상위가 어느 트레이스에서 왔든 증거다.
        ArrayList<TraceSpans.Span> spans = new ArrayList<>();
        data.traceJsons().values().forEach(json -> spans.addAll(TraceSpans.parse(json)));
        ArrayList<Evidence.LogLine> logs = new ArrayList<>();
        collectLogLines(data.errorWarnLogsJson(), logs);
        collectLogLines(data.traceIdLogsJson(), logs);

        ArrayList<Evidence.MetricSeries> metrics = new ArrayList<>();
        data.metricsJson().forEach((query, body) -> collectSeries(query, body, metrics));

        return new Evidence(
                data.traceJsons().keySet().stream().findFirst().orElse(null),
                data.correlationId() == null || data.correlationId().isBlank()
                        ? "scan" : data.correlationId(),
                spans.size(),
                topSpans(spans),
                logs.size(),
                sampleLogs(logs),
                metrics);
    }

    private static List<Evidence.SpanRecord> topSpans(List<TraceSpans.Span> spans) {
        return spans.stream()
                .sorted(Comparator.comparingLong(TraceSpans.Span::durationNanos).reversed())
                .limit(MAX_TOP_SPANS)
                .map(span -> new Evidence.SpanRecord(
                        span.service().isBlank() ? "?" : span.service(),
                        span.name(),
                        span.durationMillis(),
                        instantOfNanos(span.startNanos())))
                .toList();
    }

    /**
     * 상한을 넘으면 <b>ERROR/WARN 줄을 먼저 남긴다.</b> 앞에서부터 자르면 장애가 창 뒤쪽에서
     * 시작했을 때 정작 필요한 줄이 통째로 사라진다.
     */
    private static List<Evidence.LogLine> sampleLogs(List<Evidence.LogLine> logs) {
        if (logs.size() <= MAX_LOG_SAMPLES) {
            return logs.stream().sorted(Comparator.comparing(Evidence.LogLine::at)).toList();
        }
        ArrayList<Evidence.LogLine> picked = new ArrayList<>();
        logs.stream().filter(l -> containsLevel(l.line())).limit(MAX_LOG_SAMPLES).forEach(picked::add);
        logs.stream().filter(l -> !containsLevel(l.line()))
                .limit(Math.max(0, MAX_LOG_SAMPLES - picked.size())).forEach(picked::add);
        return picked.stream().sorted(Comparator.comparing(Evidence.LogLine::at)).toList();
    }

    private static boolean containsLevel(String line) {
        return line.contains("ERROR") || line.contains("WARN") || line.contains("Exception");
    }

    /** Loki streams 응답: {@code data.result[].stream{라벨} + values[][ns, line]}. */
    private static void collectLogLines(String body, List<Evidence.LogLine> out) {
        JsonNode result = readResult(body);
        if (result == null) {
            return;
        }
        for (JsonNode stream : result) {
            String service = stream.path("stream").path("service_name").asText("");
            JsonNode values = stream.get("values");
            if (values == null || !values.isArray()) {
                continue;
            }
            for (JsonNode value : values) {
                if (!value.isArray() || value.size() < 2) {
                    continue;
                }
                Long nanos = parseLong(value.get(0).asText(null));
                out.add(new Evidence.LogLine(
                        nanos == null ? null : instantOfNanos(nanos),
                        service.isBlank() ? "?" : service,
                        value.get(1).asText("")));
            }
        }
    }

    /**
     * Prometheus matrix 응답. 값의 범위와 <b>0이었던 구간</b>을 함께 남긴다 — 프로세스가 죽어
     * {@code up}이 0으로 꺾인 것이 유일한 신호였던 회차가 있고, 그 사실은 숫자로 남아야 한다.
     */
    private static void collectSeries(String query, String body, List<Evidence.MetricSeries> out) {
        JsonNode result = readResult(body);
        if (result == null) {
            return;
        }
        int seriesCount = 0;
        for (JsonNode series : result) {
            if (seriesCount++ >= MAX_SERIES_PER_QUERY) {
                break;
            }
            JsonNode values = series.get("values");
            if (values == null || !values.isArray() || values.isEmpty()) {
                continue;
            }

            Instant firstAt = null;
            Instant lastAt = null;
            double min = Double.MAX_VALUE;
            double max = -Double.MAX_VALUE;
            double last = 0.0;
            ArrayList<String> zeroSpans = new ArrayList<>();
            Instant zeroFrom = null;
            Instant zeroTo = null;

            for (JsonNode point : values) {
                if (!point.isArray() || point.size() < 2) {
                    continue;
                }
                Instant at = Instant.ofEpochSecond(point.get(0).asLong());
                Double raw = parseDouble(point.get(1).asText(null));
                if (raw == null) {
                    continue;
                }
                if (firstAt == null) {
                    firstAt = at;
                }
                lastAt = at;
                min = Math.min(min, raw);
                max = Math.max(max, raw);
                last = raw;

                if (raw == 0.0) {
                    zeroFrom = zeroFrom == null ? at : zeroFrom;
                    zeroTo = at;
                } else if (zeroFrom != null) {
                    zeroSpans.add(zeroFrom + " ~ " + zeroTo);
                    zeroFrom = null;
                }
            }
            if (zeroFrom != null) {
                zeroSpans.add(zeroFrom + " ~ " + zeroTo);
            }
            if (firstAt == null) {
                continue;
            }

            out.add(new Evidence.MetricSeries(query, labelsOf(series.path("metric")),
                    values.size(), firstAt, lastAt, min, max, last, zeroSpans));
        }
    }

    private static String labelsOf(JsonNode metric) {
        if (metric == null || !metric.isObject() || metric.isEmpty()) {
            return "{}";
        }
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        Iterator<Map.Entry<String, JsonNode>> fields = metric.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            if (!first) {
                sb.append(", ");
            }
            sb.append(field.getKey()).append('=').append(field.getValue().asText(""));
            first = false;
        }
        return sb.append('}').toString();
    }

    private static JsonNode readResult(String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
        try {
            JsonNode result = MAPPER.readTree(body).path("data").path("result");
            return result.isArray() ? result : null;
        } catch (Exception e) {
            return null;
        }
    }

    private static Instant instantOfNanos(long nanos) {
        return Instant.ofEpochSecond(nanos / 1_000_000_000L, nanos % 1_000_000_000L);
    }

    private static Long parseLong(String raw) {
        try {
            return raw == null ? null : Long.parseLong(raw);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Double parseDouble(String raw) {
        try {
            return raw == null ? null : Double.parseDouble(raw);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
