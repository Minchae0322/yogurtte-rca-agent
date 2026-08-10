package com.yogurtte.rca.triage.incident;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yogurtte.rca.triage.survey.SurveyResult;

/**
 * 스윕 원본 JSON에서 {@link Signal}을 뽑는 규칙 전부.
 *
 * <p>채널별 규칙 — Tempo는 검색 결과의 존재(쿼리가 이미 이상을 정의한다), Loki 발생률은 버킷에
 * 값이 있다는 것(count_over_time은 0건 구간에 샘플을 만들지 않는다), Mimir는
 * 0 구간 · 값 변화 · 시리즈 결측 셋이다.
 *
 * <p><b>임계값으로 판정하지 않는다.</b> 변화·부재·존재만 신호로 센다. 이유 셋 —
 * ① 평시 baseline 데이터가 없다 ② 문항별 임계값을 코드에 박으면 정답을 심는 것이다
 * ③ 에이전트가 이미 같은 기준을 쓴다(<i>"전 구간 상수 — 변화가 없으므로 무관"</i>).
 * 값의 크기는 판정이 아니라 {@link Signal#what}에 담아 모델이 보게 한다.
 *
 * <p>EvidenceExtractor가 이미 메트릭의 0 구간을 뽑지만 그쪽은 창이 정해진 뒤(분석 단계)에
 * 돌아서, 정작 창을 정할 때는 쓸 수 없다. 그래서 탐색 단계용으로 이 클래스가 있다.
 */
public final class SignalExtractor {

    /** {@code min_over_time(up[5m])} → {@code up}. 집계 함수를 벗겨 지표 이름만 남긴다. */
    private static final Pattern METRIC_NAME = Pattern.compile("([a-zA-Z_:][a-zA-Z0-9_:]*)\\s*[\\[{)]");
    /** 바깥을 감싼 함수 호출 — 특정 함수 목록이 아니라 "식별자 + 여는 괄호" 꼴이면 벗긴다. */
    private static final Pattern WRAPPER = Pattern.compile("^[a-z_][a-z0-9_]*\\s*\\(");

    /** 시리즈를 가르지 않는 라벨 — 지표 이름과 리소스 축, 그리고 배포 전체에 공통인 것들. */
    private static final Set<String> META_LABELS = Set.of(
            "__name__", "job", "instance", "application", "service_name", "cluster", "k8s_cluster_name");

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private SignalExtractor() {
    }

    public static List<Signal> extract(SurveyResult survey, Duration lookback) {
        return extract(survey, lookback, Set.of());
    }

    /**
     * @param zeroIsAbnormal <b>0이 이상 신호인 지표명</b>. 여기 없는 지표는 0 구간을 신호로 만들지 않는다.
     *                       가용성 게이지(<code>up</code>·<code>mongodb_up</code>)는 0이 곧 다운이지만
     *                       <code>kafka_consumergroup_lag</code>·<code>websocket_active_users</code>는
     *                       <b>0이 정상</b>이다. 구분하지 않으면 멀쩡한 시리즈마다 창 전체를 덮는
     *                       "0이었다" 신호가 생기고, 군집 키가 지표명이라 그것들이 후보 하나로 뭉쳐
     *                       <b>조사 창이 스윕 창 전체로 벌어진다</b>(CH-1 회차 3 실측: 41개 신호 중
     *                       36개가 창 전체 · 컨텍스트 363,268자 · 정답 트레이스가 수집 상한에 밀림).
     */
    public static List<Signal> extract(SurveyResult survey, Duration lookback, Set<String> zeroIsAbnormal) {
        // 두 로그 곡선을 <b>함께</b> 싣는다. 대체가 아니다 —
        //   총 건수 곡선  : 규모. WARN 등 예외가 안 딸린 줄까지 전부 센다
        //   예외 클래스 곡선: 성격. 예외 클래스 줄만 세므로 스택 30줄이 30건이 되지 않는다
        // 한쪽만 쓰면 잃는 것이 있다. 총 건수만 쓰면 무엇이 났는지 모르고(회차 1~6 상태),
        // 예외만 쓰면 예외 없는 ERROR/WARN이 통째로 사라진다(08-05 창 실측: 78건 중 42건).
        // 지문이 다르므로 군집 키가 갈려 서로 섞이지 않는다.
        return Stream.of(fromTraces(survey),
                        fromLogRates(survey.logRatesJson(), lookback),
                        fromLogRates(survey.logSignatureRatesJson(), lookback, true),
                        fromMetrics(survey.metricsJson(), zeroIsAbnormal))
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
                        // 지나간 서비스는 설명에만 붙이고 지문(군집 키)에는 넣지 않는다 — 키에 넣으면
                        // 같은 엔드포인트가 상류 조합마다 다른 후보로 흩어진다(Mimir 라벨과 같은 이유).
                        "%s %s %,dms (%s 채널)%s".formatted(hit.rootServiceName(), hit.rootTraceName(),
                                hit.durationMs(), hit.channel(),
                                hit.crossServiceText().isEmpty() ? "" : "  [지나간 서비스: " + hit.crossServiceText() + "]"),
                        hit.traceId()))
                .toList();
    }

    /** 구간은 {@code [ts - lookback, ts]} 다. {@code ts} 하나로 두면 최대 lookback만큼 어긋난다. */
    private static List<Signal> fromLogRates(String body, Duration lookback) {
        return fromLogRates(body, lookback, false);
    }

    /**
     * @param exceptionLines 이 곡선이 <b>예외 클래스 줄</b>을 센 것인가. 그러면 값의 뜻이
     *                       "ERROR/WARN 줄 수"가 아니라 <b>"예외 발생 횟수"</b>다 — 두 곡선을
     *                       함께 실으므로 문구로 구별하지 않으면 읽는 쪽이 더한다.
     */
    private static List<Signal> fromLogRates(String body, Duration lookback, boolean exceptionLines) {
        return parseMatrix(body).stream()
                .flatMap(series -> series.valued().stream()
                        .filter(point -> point.value() != 0.0)
                        .map(point -> new Signal(
                                point.at().minus(lookback), point.at(),
                                Channel.LOKI, Precision.BUCKET,
                                series.service(),
                                series.logSignature(),
                                (exceptionLines ? "예외 %s건 (%s ~ %s)" : "ERROR/WARN %s건 (%s ~ %s)").formatted(
                                        point.raw(), point.at().minus(lookback), point.at()),
                                "loki-rate")))
                .toList();
    }

    private static List<Signal> fromMetrics(Map<String, String> metricsJson, Set<String> zeroIsAbnormal) {
        return metricsJson.entrySet().stream()
                .flatMap(entry -> fromMetric(entry.getKey(), entry.getValue(), zeroIsAbnormal))
                .toList();
    }

    /**
     * 시리즈 하나에서 세 가지 이상을 본다 — 0 구간 · 값 변화 · 결측.
     *
     * <p>판정은 {@link Finding}(어느 구간이 왜)까지만 하고, 라벨(지표명·리소스·쿼리)은 여기서
     * 한 번에 붙인다. 판정 메서드마다 라벨 셋을 끌고 다니면 인자만 다섯이 된다.
     */
    private static Stream<Signal> fromMetric(String query, String body, Set<String> zeroIsAbnormal) {
        String name = metricNameOf(query);
        // 0이 정상인 지표(lag·활성 사용자 수)는 0 구간을 신호로 만들지 않는다 — extract() javadoc 참조.
        boolean zeroMatters = zeroIsAbnormal.contains(name);
        return parseMatrix(body).stream()
                .filter(series -> series.points().size() >= 2)
                .flatMap(series -> {
                    List<Point> points = series.valued();
                    // 설명에는 시리즈를 가르는 라벨까지 붙이고(어느 topic인지), 지문(군집 키)에는
                    // 지표명만 쓴다 — 라벨을 키에 넣으면 44개 시리즈가 44개 후보로 흩어진다.
                    String label = name + series.identity();
                    return Stream.of(zeroMatters ? zeroRuns(points, label) : Stream.<Finding>of(),
                                    valueChanges(points, label),
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
}
