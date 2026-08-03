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
    /** 컨텍스트 메트릭 요약(B-25)에서 곡선 모양을 남길 균등 표본 수. */
    private static final int MAX_METRIC_SAMPLES = 10;

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

    /**
     * B-25 — 분석 컨텍스트에 실을 <b>메트릭 요약 텍스트</b>. 전 데이터 포인트를 JSON 그대로
     * 싣던 것을 대체한다.
     *
     * <p>AP-1 회차 3 실측에서 메트릭이 컨텍스트의 <b>32%</b>(89,649B)였다. 창 11분에 step 15s면
     * 시리즈당 45점이고, {@code kafka_consumergroup_lag}처럼 파티션마다 시리즈가 생기면 곱해진다.
     * 그런데 판단에 쓰인 것은 값의 범위·0이던 구간·회복 시점 정도였다.
     *
     * <p><b>요약이 곧 정보 손실이면 안 된다</b>(round-4 §"요약을 먼저 완성한다"). 그래서
     * 지금까지 회차들이 실제로 인용한 것을 전부 남긴다 — 값 범위(min/max/처음/마지막)와
     * 최댓값 시각(lag 정점), <b>0이던 구간</b>(프로세스 사망이 유일한 신호였던 회차가 있다),
     * <b>결측 구간</b>(시계열 소멸이 auth 다운의 근거였다), 그리고 곡선 모양을 위한
     * 균등 표본. 전 구간 상수면 그 사실 한 줄로 끝낸다 — 에이전트도 같은 기준을 쓴다
     * (<i>"전 구간 상수 — 변화가 없으므로 무관"</i>).
     *
     * <p>시리즈 수는 제한하지 않는다. 어느 파티션·어느 인스턴스만 이상한지가 신호이고,
     * 시리즈당 200바이트 남짓이라 잘라서 아낄 것이 없다.
     */
    static String metricSummary(String query, String body) {
        JsonNode result = readResult(body);
        if (result == null || result.isEmpty()) {
            return "(시리즈 없음)\n";
        }
        StringBuilder sb = new StringBuilder();
        for (JsonNode series : result) {
            ArrayList<Instant> times = new ArrayList<>();
            ArrayList<Double> values = new ArrayList<>();
            for (JsonNode point : series.path("values")) {
                if (!point.isArray() || point.size() < 2) {
                    continue;
                }
                Double value = parseDouble(point.get(1).asText(null));
                if (value != null) {
                    times.add(Instant.ofEpochSecond(point.get(0).asLong()));
                    values.add(value);
                }
            }
            sb.append(seriesSummary(labelsOf(series.path("metric")), times, values));
        }
        return sb.toString();
    }

    private static String seriesSummary(String labels, List<Instant> times, List<Double> values) {
        if (times.isEmpty()) {
            return labels + "  (점 없음 — 이 창에 시리즈가 존재하지 않았다)\n";
        }
        double min = values.stream().mapToDouble(Double::doubleValue).min().orElse(0);
        double max = values.stream().mapToDouble(Double::doubleValue).max().orElse(0);
        boolean constant = min == max;
        // 결측 — 샘플 간격이 갑자기 벌어진 곳. 시리즈 소멸이 곧 스크레이프 대상 부재다.
        // 값이 상수여도 이건 접으면 안 된다: auth 다운은 up이 1 → (사라짐) → 1 로 나타난다.
        String gaps = gapsOf(times);
        String head = "%s  %d점 · %s ~ %s".formatted(labels, times.size(), times.get(0), times.get(times.size() - 1));
        if (constant && gaps.isEmpty()) {
            return head + " · 전 구간 " + num(min) + " (변화 없음)\n";
        }

        StringBuilder sb = new StringBuilder(head).append('\n');
        if (constant) {
            sb.append("  값: 전 구간 ").append(num(min)).append('\n');
        } else {
            sb.append("  min %s · max %s (%s) · 처음 %s · 마지막 %s\n".formatted(
                    num(min), num(max), times.get(values.indexOf(max)),
                    num(values.get(0)), num(values.get(values.size() - 1))));
        }

        // 0이던 구간 — 프로세스가 죽어 값이 0으로 꺾인 것이 유일한 신호인 장애가 있다.
        String zeros = spansOf(times, values, value -> value == 0.0);
        if (!zeros.isEmpty()) {
            sb.append("  0이던 구간: ").append(zeros).append('\n');
        }
        if (!gaps.isEmpty()) {
            sb.append("  결측(샘플 없음): ").append(gaps).append('\n');
        }
        if (!constant) {
            sb.append("  표본: ").append(samplesOf(times, values)).append('\n');
        }
        return sb.toString();
    }

    /** 조건을 만족하는 연속 구간을 {@code from ~ to} 목록으로. */
    private static String spansOf(List<Instant> times, List<Double> values,
                                  java.util.function.DoublePredicate match) {
        ArrayList<String> spans = new ArrayList<>();
        Instant from = null;
        Instant to = null;
        for (int i = 0; i < values.size(); i++) {
            if (match.test(values.get(i))) {
                from = from == null ? times.get(i) : from;
                to = times.get(i);
            } else if (from != null) {
                spans.add(from + " ~ " + to);
                from = null;
            }
        }
        if (from != null) {
            spans.add(from + " ~ " + to);
        }
        return String.join(", ", spans);
    }

    /** 샘플 간격이 중앙값의 2배를 넘으면 그 사이는 시리즈가 없었던 것이다. */
    private static String gapsOf(List<Instant> times) {
        if (times.size() < 3) {
            return "";
        }
        List<Long> steps = new ArrayList<>();
        for (int i = 1; i < times.size(); i++) {
            steps.add(times.get(i).getEpochSecond() - times.get(i - 1).getEpochSecond());
        }
        long median = steps.stream().sorted().toList().get(steps.size() / 2);
        ArrayList<String> gaps = new ArrayList<>();
        for (int i = 1; i < times.size(); i++) {
            if (steps.get(i - 1) > median * 2) {
                gaps.add(times.get(i - 1) + " ~ " + times.get(i));
            }
        }
        return String.join(", ", gaps);
    }

    /** 곡선 모양을 남기는 균등 표본. 점이 적으면 전부. */
    private static String samplesOf(List<Instant> times, List<Double> values) {
        int size = times.size();
        int take = Math.min(size, MAX_METRIC_SAMPLES);
        ArrayList<String> out = new ArrayList<>();
        for (int i = 0; i < take; i++) {
            int index = take == 1 ? 0 : (int) Math.round((double) i * (size - 1) / (take - 1));
            // 시:분:초만 — 날짜는 헤더의 구간에 이미 있다. 표본 하나가 25자에서 12자로 준다.
            out.add(hhmmss(times.get(index)) + "=" + num(values.get(index)));
        }
        return String.join(" · ", out) + (take < size ? "  (%d점 중 %d점 균등 발췌)".formatted(size, take) : "");
    }

    /** {@code 2026-07-28T15:05:00Z} → {@code 15:05:00}. */
    private static String hhmmss(Instant at) {
        String text = at.toString();
        return text.length() >= 19 ? text.substring(11, 19) : text;
    }

    private static String num(double value) {
        return value == Math.rint(value) && Math.abs(value) < 1e15
                ? String.valueOf((long) value) : String.valueOf(value);
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
