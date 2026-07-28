package com.yogurtte.rca.analyzer;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

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
        var spans = TraceSpans.parse(data.traceJson());
        var logs = new ArrayList<Evidence.LogLine>();
        collectLogLines(data.errorWarnLogsJson(), logs);
        collectLogLines(data.traceIdLogsJson(), logs);

        var metrics = new ArrayList<Evidence.MetricSeries>();
        data.metricsJson().forEach((query, body) -> collectSeries(query, body, metrics));

        return new Evidence(
                data.traceId(),
                data.traceId() == null || data.traceId().isBlank() ? "scan" : data.traceId(),
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
        var picked = new ArrayList<Evidence.LogLine>();
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
        var result = readResult(body);
        if (result == null) {
            return;
        }
        for (var stream : result) {
            var service = stream.path("stream").path("service_name").asText("");
            var values = stream.get("values");
            if (values == null || !values.isArray()) {
                continue;
            }
            for (var value : values) {
                if (!value.isArray() || value.size() < 2) {
                    continue;
                }
                var nanos = parseLong(value.get(0).asText(null));
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
        var result = readResult(body);
        if (result == null) {
            return;
        }
        var seriesCount = 0;
        for (var series : result) {
            if (seriesCount++ >= MAX_SERIES_PER_QUERY) {
                break;
            }
            var values = series.get("values");
            if (values == null || !values.isArray() || values.isEmpty()) {
                continue;
            }

            Instant firstAt = null;
            Instant lastAt = null;
            var min = Double.MAX_VALUE;
            var max = -Double.MAX_VALUE;
            var last = 0.0;
            var zeroSpans = new ArrayList<String>();
            Instant zeroFrom = null;
            Instant zeroTo = null;

            for (var point : values) {
                if (!point.isArray() || point.size() < 2) {
                    continue;
                }
                var at = Instant.ofEpochSecond(point.get(0).asLong());
                var raw = parseDouble(point.get(1).asText(null));
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
        var sb = new StringBuilder("{");
        var first = true;
        var fields = metric.fields();
        while (fields.hasNext()) {
            var field = fields.next();
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
            var result = MAPPER.readTree(body).path("data").path("result");
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
