package com.yogurtte.rca.triage.incident;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yogurtte.rca.triage.survey.SurveyResult;

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

    /** 시리즈를 가르지 않는 라벨 — 지표 이름과 리소스 축, 그리고 배포 전체에 공통인 것들. */
    private static final java.util.Set<String> META_LABELS = java.util.Set.of(
            "__name__", "job", "instance", "application", "service_name", "cluster", "k8s_cluster_name");

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static List<Signal> extract(SurveyResult survey, Duration lookback) {
        return Stream.of(
                        fromTraces(survey),
                        fromLogRates(survey.logRatesJson(), lookback),
                        fromMetrics(survey.metricsJson()))
                .flatMap(List::stream)
                .toList();
    }

    private static List<Signal> fromTraces(SurveyResult survey) {
        return survey.traceHits().stream()
                // 깨진 행은 시각을 믿을 수 없어 창 계산에 넣으면 안 된다. 후보 목록에는 남아 있다.
                .filter(hit -> hit.trusted() && hit.startedAt() != null)
                .map(hit -> new Signal(
                        hit.startedAt(),
                        hit.startedAt().plusMillis(Math.max(0, hit.durationMs())),
                        Channel.TEMPO, Precision.EXACT,
                        hit.rootServiceName(), hit.rootTraceName(),
                        "%s %s %,dms (%s 채널)".formatted(hit.rootServiceName(), hit.rootTraceName(),
                                hit.durationMs(), hit.channel()),
                        hit.traceId()))
                .toList();
    }

    /** 구간은 {@code [ts - lookback, ts]} 다. {@code ts} 하나로 두면 최대 lookback만큼 어긋난다. */
    private static List<Signal> fromLogRates(String body, Duration lookback) {
        return parseMatrix(body).stream()
                .flatMap(series -> series.valued().stream()
                        .filter(point -> point.value() != 0.0)
                        .map(point -> new Signal(
                                point.at().minus(lookback), point.at(),
                                Channel.LOKI, Precision.BUCKET,
                                series.service(),
                                series.logSignature(),
                                "ERROR/WARN %s건 (%s ~ %s)".formatted(
                                        point.raw(), point.at().minus(lookback), point.at()),
                                "loki-rate")))
                .toList();
    }

    private static List<Signal> fromMetrics(Map<String, String> metricsJson) {
        return metricsJson.entrySet().stream()
                .flatMap(entry -> fromMetric(entry.getKey(), entry.getValue()))
                .toList();
    }

    /**
     * 시리즈 하나에서 세 가지 이상을 본다 — 0 구간 · 값 변화 · 결측.
     *
     * <p>판정은 {@link Finding}(어느 구간이 왜)까지만 하고, 라벨(지표명·리소스·쿼리)은 여기서
     * 한 번에 붙인다. 판정 메서드마다 라벨 셋을 끌고 다니면 인자만 다섯이 된다.
     */
    private static Stream<Signal> fromMetric(String query, String body) {
        String name = metricNameOf(query);
        return parseMatrix(body).stream()
                .filter(series -> series.points().size() >= 2)
                .flatMap(series -> {
                    List<Point> points = series.valued();
                    // 설명에는 시리즈를 가르는 라벨까지 붙이고(어느 topic인지), 지문(군집 키)에는
                    // 지표명만 쓴다 — 라벨을 키에 넣으면 44개 시리즈가 44개 후보로 흩어진다.
                    String label = name + series.identity();
                    return Stream.of(zeroRuns(points, label), valueChanges(points, label),
                                    gaps(points, series.stepHint(), label))
                            .flatMap(findings -> findings)
                            .map(finding -> new Signal(
                                    finding.from().at(), finding.to().at(),
                                    Channel.MIMIR, Precision.BUCKET,
                                    series.resource(), name, finding.what(), query));
                });
    }

    /** 신호가 될 구간 하나. 라벨이 붙기 전 단계라 "어디서"가 없다. */
    private record Finding(Point from, Point to, String what) {
    }

    /** 0이 이어진 구간 하나가 신호 하나다. */
    private static Stream<Finding> zeroRuns(List<Point> points, String name) {
        return runsOfZero(points).stream().map(run -> {
            Point first = run.get(0);
            Point last = run.get(run.size() - 1);
            return new Finding(first, last,
                    "%s 가 0이었다 (%s ~ %s)".formatted(name, first.at(), last.at()));
        });
    }

    /** 새 구간을 만드는 즉시 목록에 넣는다 — 루프가 끝난 뒤 마지막 구간을 따로 흘려보낼 필요가 없다. */
    private static List<List<Point>> runsOfZero(List<Point> points) {
        ArrayList<List<Point>> runs = new ArrayList<>();
        List<Point> current = null;
        for (Point point : points) {
            if (point.value() != 0.0) {
                current = null;
                continue;
            }
            if (current == null) {
                current = new ArrayList<>();
                runs.add(current);
            }
            current.add(point);
        }
        return runs;
    }

    /**
     * 인접 샘플의 값이 다르면 그 사이 어딘가에서 무언가 변했다.
     *
     * <p>원시값으로 비교한다 — 구현 첫 판은 {@code Double != Double}(참조 비교)이어서
     * 값이 같아도 매 쌍이 "변화"로 나가는 잠복 버그가 있었다(실전 조사 전에 발견).
     */
    private static Stream<Finding> valueChanges(List<Point> points, String name) {
        return adjacentPairs(points)
                .filter(pair -> pair.prev().value().doubleValue() != pair.next().value().doubleValue())
                .map(pair -> new Finding(pair.prev(), pair.next(),
                        "%s %s → %s".formatted(name, trim(pair.prev().value()), trim(pair.next().value()))));
    }

    /** 샘플이 step의 2배 넘게 벌어지면 시리즈가 사라졌던 것이다 — 스크레이프 대상 부재. */
    private static Stream<Finding> gaps(List<Point> points, Duration step, String name) {
        if (step == null) {
            return Stream.of();
        }
        Duration limit = step.multipliedBy(2);
        return adjacentPairs(points)
                .filter(pair -> Duration.between(pair.prev().at(), pair.next().at()).compareTo(limit) > 0)
                .map(pair -> new Finding(pair.prev(), pair.next(),
                        "%s 시리즈가 %s ~ %s 구간에 없다 (스크레이프 대상 부재)"
                                .formatted(name, pair.prev().at(), pair.next().at())));
    }

    /** 값 변화와 결측은 둘 다 "이웃한 두 샘플"만 본다. 그 순회를 한 곳에 둔다. */
    private record Pair(Point prev, Point next) {
    }

    private static Stream<Pair> adjacentPairs(List<Point> points) {
        return IntStream.range(1, points.size())
                .mapToObj(i -> new Pair(points.get(i - 1), points.get(i)));
    }

    /** 감싼 함수를 몇 겹이든 벗긴다 — {@code sum(rate(x[5m]))} → {@code x}. */
    public static String metricNameOf(String query) {
        String stripped = query == null ? "" : query.trim();
        while (true) {
            String next = WRAPPER.matcher(stripped).replaceFirst("");
            if (next.equals(stripped)) {
                break;
            }
            stripped = next;
        }
        Matcher matcher = METRIC_NAME.matcher(stripped);
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
            String value = labels.path(name).asText(null);
            return (value == null || value.isBlank()) ? null : value;
        }

        /** 값을 숫자로 읽은 샘플만. 못 읽은 점은 어떤 판정에도 쓰지 않는다. */
        List<Point> valued() {
            return points.stream().filter(point -> point.value() != null).toList();
        }

        /** 메트릭이 어디서 나왔나. 라벨 이름이 배포마다 달라 폴백 체인을 둔다. */
        String resource() {
            return firstNonBlank(label("job"), label("application"), label("instance"));
        }

        /**
         * 같은 지표의 시리즈를 서로 가르는 라벨 — {@code {consumergroup=…, topic=…, partition=…}}.
         *
         * <p>이게 없으면 후보 목록에서 44개 시리즈가 <b>글자 그대로 같은 줄</b>이 되어, 어느
         * 토픽이 밀렸는지 알 길이 원본 JSON밖에 없다. 리소스 축과 인프라 메타 라벨은 뺀다 —
         * 그건 {@link #resource()}가 이미 말하고 있고, 시리즈를 가르지도 않는다.
         */
        String identity() {
            List<String> parts = new ArrayList<>();
            labels.fieldNames().forEachRemaining(key -> {
                if (!META_LABELS.contains(key)) {
                    parts.add(key + "=" + labels.path(key).asText(""));
                }
            });
            return parts.isEmpty() ? "" : "{" + String.join(", ", parts) + "}";
        }

        String service() {
            return firstNonBlank(label("service_name"));
        }

        /** exc는 B-29 적용 시 채워진다. 그전까지는 채널 전체가 한 지문이다. */
        String logSignature() {
            String exc = label("exc");
            return exc == null ? "ERROR/WARN" : exc;
        }

        /** 샘플 간격을 응답에서 추론한다 — 설정된 step과 실제가 다를 수 있다. */
        Duration stepHint() {
            if (points.size() < 2) {
                return null;
            }
            Duration gap = Duration.between(points.get(0).at(), points.get(1).at());
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
        return nodes(result)
                .map(node -> new Series(node.path("metric"), pointsOf(node.path("values"))))
                .toList();
    }

    private static List<Point> pointsOf(JsonNode values) {
        return nodes(values)
                .filter(value -> value.isArray() && value.size() >= 2)
                .map(value -> {
                    String raw = value.get(1).asText(null);
                    return new Point(Instant.ofEpochSecond(value.get(0).asLong()), raw, parseDouble(raw));
                })
                .toList();
    }

    /** 배열이 아니면 빈 스트림 — 깨진 모양을 여기서 흡수해 호출부에 모양 검사를 퍼뜨리지 않는다. */
    private static Stream<JsonNode> nodes(JsonNode node) {
        return node.isArray() ? StreamSupport.stream(node.spliterator(), false) : Stream.of();
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
        return Stream.of(candidates)
                .filter(candidate -> candidate != null && !candidate.isBlank())
                .findFirst()
                .orElse("?");
    }

    private static String blankTo(String value, String fallback) {
        return (value == null || value.isBlank()) ? fallback : value;
    }
}
