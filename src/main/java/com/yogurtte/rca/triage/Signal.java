package com.yogurtte.rca.triage;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 스윕 원본에서 코드가 뽑아낸 "언제 무엇이 이상했나" 한 건.
 * 스윕 JSON에서 신호를 뽑는 규칙도 이 파일이 갖는다 — {@link #extract(SurveyResult, Duration)}.
 *
 * <p><b>점이 아니라 구간이다.</b> 로그 발생률 응답의 {@code [[1785315300,"4"]]} 를 시각 하나로
 * 읽으면 틀린다 — 쿼리가 {@code count_over_time(...[5m])} 이므로 그 값은 <b>직전 5분 사이에
 * 4건</b>이라는 뜻이고, 점으로 읽으면 최대 5분 오차가 생긴다. 실제 회차 리포트가 버킷 시각을
 * 사건 시각으로 읽은 사례가 있다.
 *
 * <p><b>임계값으로 판정하지 않는다.</b> 변화·부재·존재만 신호로 센다. 이유 셋 —
 * ① 평시 baseline 데이터가 없다 ② 문항별 임계값을 코드에 박으면 정답을 심는 것이다
 * ③ 에이전트가 이미 같은 기준을 쓴다(<i>"전 구간 상수 — 변화가 없으므로 무관"</i>).
 * 값의 크기는 판정이 아니라 {@link #what}에 담아 모델이 보게 한다.
 *
 * @param from      구간 시작. 점 신호는 {@code from == to}
 * @param to        구간 끝
 * @param channel   어느 신호원인가
 * @param precision 시각을 얼마나 믿을 수 있나. 창 계산의 여유 폭이 여기서 나온다
 * @param resource  어디서 — 응답 라벨에서 뽑는다 (서비스명 · job)
 * @param signature 무엇이 — 엔드포인트 · 예외 클래스 · 쿼리 이름. <b>같은 리소스의 다른 사건을
 *                  가르는 축</b>이다
 * @param what      사람이 읽는 설명. 값의 크기가 여기 들어간다
 * @param ref       되짚을 단서 (traceId · 쿼리 · 스트림)
 */
public record Signal(
        Instant from,
        Instant to,
        Channel channel,
        Precision precision,
        String resource,
        String signature,
        String what,
        String ref) {

    public enum Channel { TEMPO, LOKI, MIMIR }

    /**
     * EXACT는 ms 단위로 정확한 시각(트레이스 span·로그 라인), BUCKET은 집계 해상도만큼
     * 흐릿한 시각(메트릭 샘플·로그 발생률 버킷)이다.
     */
    public enum Precision { EXACT, BUCKET }

    public Signal {
        resource = blankTo(resource, "?");
        signature = blankTo(signature, "?");
        if (to == null) {
            to = from;
        }
    }

    /**
     * 군집 키. <b>라벨 3축이고 시간은 들어가지 않는다</b> — 사건이 시간상 교차해도
     * (1번-A → 2번-A → 1번-B → 2번-B) 이 키로는 갈린다. 시간은 같은 키 안에서만 쓴다.
     *
     * <p>채널이 1축인 이유: 지문의 성격이 채널마다 다르다(Tempo는 엔드포인트, Loki는 예외
     * 클래스, Mimir는 지표). 섞으면 같은 사건의 Tempo 신호와 Loki 신호가 지문이 달라 갈라진다.
     */
    public String key() {
        return channel + "|" + resource + "|" + signature;
    }

    // ---- 태생: 스윕 원본 JSON → 신호 ----
    //
    // 채널별 규칙 — Tempo는 검색 결과의 존재(쿼리가 이미 이상을 정의한다), Loki 발생률은 버킷에
    // 값이 있다는 것(count_over_time은 0건 구간에 샘플을 만들지 않는다), Mimir는
    // 0 구간 · 값 변화 · 시리즈 결측 셋이다.
    //
    // EvidenceExtractor가 이미 메트릭의 0 구간을 뽑지만 그쪽은 창이 정해진 뒤(분석 단계)에
    // 돌아서, 정작 창을 정할 때는 쓸 수 없다. 그래서 탐색 단계용으로 여기 있다.

    /** {@code min_over_time(up[5m])} → {@code up}. 집계 함수를 벗겨 지표 이름만 남긴다. */
    private static final Pattern METRIC_NAME = Pattern.compile("([a-zA-Z_:][a-zA-Z0-9_:]*)\\s*[\\[{)]");
    /** 바깥을 감싼 함수 호출 — 특정 함수 목록이 아니라 "식별자 + 여는 괄호" 꼴이면 벗긴다. */
    private static final Pattern WRAPPER = Pattern.compile("^[a-z_][a-z0-9_]*\\s*\\(");

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static List<Signal> extract(SurveyResult survey, Duration lookback) {
        var signals = new ArrayList<Signal>();
        fromTraces(survey, signals);
        fromLogRates(survey.logRatesJson(), lookback, signals);
        survey.metricsJson().forEach((query, body) -> fromMetric(query, body, signals));
        return List.copyOf(signals);
    }

    private static void fromTraces(SurveyResult survey, List<Signal> out) {
        for (var hit : survey.traceHits()) {
            // 깨진 행은 시각을 믿을 수 없어 창 계산에 넣으면 안 된다. 후보 목록에는 남아 있다.
            if (!hit.trusted() || hit.startedAt() == null) {
                continue;
            }
            var end = hit.startedAt().plusMillis(Math.max(0, hit.durationMs()));
            var what = "%s %s %,dms (%s 채널)".formatted(
                    hit.rootServiceName(), hit.rootTraceName(), hit.durationMs(), hit.channel());
            out.add(new Signal(hit.startedAt(), end, Channel.TEMPO, Precision.EXACT,
                    hit.rootServiceName(), hit.rootTraceName(), what, hit.traceId()));
        }
    }

    /** 구간은 {@code [ts - lookback, ts]} 다. {@code ts} 하나로 두면 최대 lookback만큼 어긋난다. */
    private static void fromLogRates(String body, Duration lookback, List<Signal> out) {
        for (var series : parseMatrix(body)) {
            var service = firstNonBlank(series.label("service_name"), "?");
            var signature = firstNonBlank(series.label("exc"), "ERROR/WARN"); // exc는 B-29 적용 시 채워진다
            for (var point : series.points()) {
                if (point.value() == null || point.value() == 0.0) {
                    continue;
                }
                var what = "ERROR/WARN %s건 (%s ~ %s)".formatted(
                        point.raw(), point.at().minus(lookback), point.at());
                out.add(new Signal(point.at().minus(lookback), point.at(), Channel.LOKI,
                        Precision.BUCKET, service, signature, what, "loki-rate"));
            }
        }
    }

    private static void fromMetric(String query, String body, List<Signal> out) {
        var name = metricNameOf(query);
        for (var series : parseMatrix(body)) {
            if (series.points().size() < 2) {
                continue;
            }
            var resource = firstNonBlank(series.label("job"), series.label("application"),
                    series.label("instance"), "?");
            var points = series.points().stream().filter(p -> p.value() != null).toList();
            zeroRuns(points, name, resource, query, out);
            valueChanges(points, name, resource, query, out);
            gaps(points, series.stepHint(), name, resource, query, out);
        }
    }

    /** 0이 이어진 구간 하나가 신호 하나다. */
    private static void zeroRuns(List<Point> points, String name, String resource,
                                 String query, List<Signal> out) {
        Point runStart = null;
        Point runEnd = null;
        for (var point : points) {
            if (point.value() == 0.0) {
                runStart = runStart == null ? point : runStart;
                runEnd = point;
            } else if (runStart != null) {
                out.add(metricSignal(name, resource, runStart, runEnd, query,
                        "%s 가 0이었다 (%s ~ %s)".formatted(name, runStart.at(), runEnd.at())));
                runStart = null;
            }
        }
        if (runStart != null) {
            out.add(metricSignal(name, resource, runStart, runEnd, query,
                    "%s 가 0이었다 (%s ~ %s)".formatted(name, runStart.at(), runEnd.at())));
        }
    }

    /**
     * 인접 샘플의 값이 다르면 그 사이 어딘가에서 무언가 변했다.
     *
     * <p>원시값으로 비교한다 — 구현 첫 판은 {@code Double != Double}(참조 비교)이어서
     * 값이 같아도 매 쌍이 "변화"로 나가는 잠복 버그가 있었다(실전 조사 전에 발견).
     */
    private static void valueChanges(List<Point> points, String name, String resource,
                                     String query, List<Signal> out) {
        for (var i = 1; i < points.size(); i++) {
            var prev = points.get(i - 1);
            var point = points.get(i);
            if (prev.value().doubleValue() != point.value().doubleValue()) {
                out.add(metricSignal(name, resource, prev, point, query,
                        "%s %s → %s".formatted(name, trim(prev.value()), trim(point.value()))));
            }
        }
    }

    /** 샘플이 step의 2배 넘게 벌어지면 시리즈가 사라졌던 것이다 — 스크레이프 대상 부재. */
    private static void gaps(List<Point> points, Duration step, String name,
                             String resource, String query, List<Signal> out) {
        if (step == null) {
            return;
        }
        for (var i = 1; i < points.size(); i++) {
            var prev = points.get(i - 1);
            var point = points.get(i);
            if (Duration.between(prev.at(), point.at()).compareTo(step.multipliedBy(2)) > 0) {
                out.add(metricSignal(name, resource, prev, point, query,
                        "%s 시리즈가 %s ~ %s 구간에 없다 (스크레이프 대상 부재)"
                                .formatted(name, prev.at(), point.at())));
            }
        }
    }

    private static Signal metricSignal(String name, String resource, Point from, Point to,
                                       String query, String what) {
        return new Signal(from.at(), to.at(), Channel.MIMIR, Precision.BUCKET,
                resource, name, what, query);
    }

    /** 감싼 함수를 몇 겹이든 벗긴다 — {@code sum(rate(x[5m]))} → {@code x}. */
    private static String metricNameOf(String query) {
        var stripped = query == null ? "" : query.trim();
        while (true) {
            var next = WRAPPER.matcher(stripped).replaceFirst("");
            if (next.equals(stripped)) {
                break;
            }
            stripped = next;
        }
        var matcher = METRIC_NAME.matcher(stripped);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return stripped.isBlank() ? "metric" : stripped;
    }

    // ---- Prometheus matrix 응답 읽기 (Mimir 메트릭·Loki 집계가 같은 모양) ----
    // 모양 방어를 이 한 곳에 모은다 — 깨진 JSON·비배열·짧은 항목은 조용히 빈 결과가 된다.

    /**
     * @param raw   응답의 원문 값 문자열 — 사람이 읽는 설명에는 이것을 쓴다 ("4건"이 "4.0건"이 되지 않게)
     * @param value 숫자로 읽은 값. 못 읽으면 null이고, 판정 로직은 그 점을 건너뛴다
     */
    private record Point(Instant at, String raw, Double value) {
    }

    private record Series(JsonNode labels, List<Point> points) {

        /** 라벨 값. 없거나 비어 있으면 null — 폴백 체인은 소비처가 정한다. */
        String label(String name) {
            var value = labels.path(name).asText(null);
            return (value == null || value.isBlank()) ? null : value;
        }

        /** 샘플 간격을 응답에서 추론한다 — 설정된 step과 실제가 다를 수 있다. */
        Duration stepHint() {
            if (points.size() < 2) {
                return null;
            }
            var gap = Duration.between(points.get(0).at(), points.get(1).at());
            return gap.isPositive() ? gap : null;
        }
    }

    private static List<Series> parseMatrix(String body) {
        if (body == null || body.isBlank()) {
            return List.of();
        }
        JsonNode result;
        try {
            result = MAPPER.readTree(body).path("data").path("result");
        } catch (Exception e) {
            return List.of();
        }
        if (!result.isArray()) {
            return List.of();
        }
        var series = new ArrayList<Series>();
        for (var node : result) {
            var points = new ArrayList<Point>();
            for (var value : node.path("values")) {
                if (!value.isArray() || value.size() < 2) {
                    continue;
                }
                var raw = value.get(1).asText(null);
                points.add(new Point(Instant.ofEpochSecond(value.get(0).asLong()), raw, parseDouble(raw)));
            }
            series.add(new Series(node.path("metric"), List.copyOf(points)));
        }
        return List.copyOf(series);
    }

    private static Double parseDouble(String raw) {
        try {
            return raw == null ? null : Double.parseDouble(raw);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String trim(double value) {
        return value == Math.rint(value) ? String.valueOf((long) value) : String.valueOf(value);
    }

    private static String firstNonBlank(String... candidates) {
        for (var candidate : candidates) {
            if (candidate != null && !candidate.isBlank()) {
                return candidate;
            }
        }
        return "?";
    }

    private static String blankTo(String value, String fallback) {
        return (value == null || value.isBlank()) ? fallback : value;
    }
}
