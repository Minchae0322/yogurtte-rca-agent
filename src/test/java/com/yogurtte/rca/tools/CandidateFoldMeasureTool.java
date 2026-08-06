package com.yogurtte.rca.tools;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import com.yogurtte.rca.collector.TimeWindow;
import com.yogurtte.rca.triage.incident.Incident;
import com.yogurtte.rca.triage.incident.Signal;
import com.yogurtte.rca.triage.incident.SignalExtractor;
import com.yogurtte.rca.triage.survey.SurveyResult;

/**
 * B-43 A/B — 상한을 올리고 접으면 후보 목록 글자 수가 정말 안 느는가만 잰다.
 *
 * <p>LLM을 호출하지 않는다. 같은 창에 네 구성으로 Tempo를 검색하고 실제 파이프라인
 * ({@code SignalExtractor → Incident.cluster → Incident.describe})을 태워
 * <b>후보 절의 글자 수</b>를 비교한다. 그 글자 수가 탐색 LLM 컨텍스트에서 후보가 차지하는 몫이다.
 *
 * <p><b>네 팔로 나눈 이유는 귀속이다</b> — 접기와 범위 확대를 한 번에 넣으면 어느 쪽이
 * 글자 수를 움직였는지 가릴 수 없다.
 *
 * <p>로그·메트릭 채널은 네 팔에서 동일하므로 넣지 않는다 — 트레이스 채널의 델타만 본다.
 *
 * <p>실행: {@code ./gradlew test --tests '*CandidateFoldMeasureTool*' -Drca.tools=true}
 * (TEMPO_URL · TEMPO_USER · GRAFANA_TOKEN 필요)
 */
@EnabledIfSystemProperty(named = "rca.tools", matches = "true")
@EnabledIfEnvironmentVariable(named = "TEMPO_URL", matches = ".+")
class CandidateFoldMeasureTool {

    private static final HttpClient HTTP = HttpClient.newHttpClient();
    private static final Duration LOOKBACK = Duration.ofMinutes(5);
    private static final Duration CLUSTER_GAP = Duration.ofSeconds(60);

    private static final String OLD_SLOW = "{ duration > 3s && status != error }";
    private static final String NEW_SLOW = "{ duration > 2s && status != error }";

    /** IN-1 회차 5의 좁힌 창. 시스템 프로퍼티로 덮어쓸 수 있다. */
    private static final String FROM = System.getProperty("rca.tools.from", "2026-08-06T04:25:00Z");
    private static final String TO = System.getProperty("rca.tools.to", "2026-08-06T04:45:00Z");

    @Test
    void measure() throws Exception {
        TimeWindow window = new TimeWindow(Instant.parse(FROM), Instant.parse(TO));
        System.out.println("window: " + FROM + " ~ " + TO);
        System.out.println();

        Arm base = arm(window, OLD_SLOW, 20, false, "A 현행       검색 20  / 3s / 접기 없음");
        Arm cut = arm(window, NEW_SLOW, 20, false, "  임계값만   검색 20  / 2s / 접기 없음  <- 상한에 잘린다");
        Arm fold = arm(window, OLD_SLOW, 20, true, "  접기만     검색 20  / 3s / 접기");
        Arm wide = arm(window, NEW_SLOW, 200, false, "  범위만     검색 200 / 2s / 접기 없음");
        Arm both = arm(window, NEW_SLOW, 200, true, "B 둘 다      검색 200 / 2s / 접기");

        System.out.println();
        System.out.println("=== 기여 분해 (후보 목록 글자 수) ===");
        line("현행", base.chars, base.chars);
        line("임계값만", base.chars, cut.chars);
        line("접기만", base.chars, fold.chars);
        line("범위만", base.chars, wide.chars);
        line("둘 다", base.chars, both.chars);

        int widened = wide.chars - base.chars;
        int absorbed = wide.chars - both.chars;
        System.out.printf("%n  범위 확대가 더한 것: %+,d자%n", widened);
        System.out.printf("  그중 접기가 흡수: %,d자 (%.0f%%)%n", absorbed,
                widened == 0 ? 0 : 100.0 * absorbed / widened);

        System.out.println();
        System.out.println("=== 후보 구성 ===");
        row("검색 트레이스", base.traces, both.traces);
        row("신호(Signal)", base.signals, both.signals);
        row("장애 후보(Incident)", base.incidents, both.incidents);

        int delta = both.chars - base.chars;
        System.out.printf("%n판정: %+,d자 (%+.1f%%) -> %s%n", delta, pct(base.chars, both.chars),
                delta <= 0 ? "전제 성립" : "전제 반증 — 토큰이 는다");
    }

    private record Arm(int traces, int signals, int incidents, int chars) {
    }

    private Arm arm(TimeWindow window, String slowQuery, int limit, boolean fold, String label)
            throws Exception {
        String errorJson = search("{ status = error }", window, limit);
        String slowJson = search(slowQuery, window, limit);
        int traces = countTraces(errorJson) + countTraces(slowJson);

        SurveyResult survey = new SurveyResult(window, "measure", errorJson, slowJson,
                null, Map.of(), new ArrayList<>(), Map.of());
        List<Signal> signals = SignalExtractor.extract(survey, LOOKBACK, Set.of());
        List<Incident> incidents = Incident.cluster(signals, CLUSTER_GAP);

        StringBuilder text = new StringBuilder();
        incidents.forEach(incident -> text.append(incident.describe(fold)));

        System.out.println("--- " + label);
        System.out.printf("    트레이스 %d건 -> 신호 %d개 -> 후보 %d개 -> %,d자%n",
                traces, signals.size(), incidents.size(), text.length());
        incidents.forEach(i -> System.out.printf("      %-7s %-16s | %-50s 신호 %d%n",
                i.id(), i.resource(), i.signature(), i.signals().size()));
        return new Arm(traces, signals.size(), incidents.size(), text.length());
    }

    private static void line(String name, int base, int value) {
        System.out.printf("  %-10s %,8d자   (%+,d · %+.1f%%)%n",
                name, value, value - base, pct(base, value));
    }

    private static void row(String name, long a, long b) {
        System.out.printf("  %-20s %9s -> %9s   (%+,d)%n", name,
                String.format("%,d", a), String.format("%,d", b), b - a);
    }

    private static double pct(long from, long to) {
        return from == 0 ? 0 : (to - from) * 100.0 / from;
    }

    private static String search(String query, TimeWindow window, int limit) throws Exception {
        String url = env("TEMPO_URL") + "/api/search"
                + "?q=" + URLEncoder.encode(query, StandardCharsets.UTF_8)
                + "&start=" + window.start().getEpochSecond()
                + "&end=" + window.end().getEpochSecond()
                + "&limit=" + limit;
        String auth = Base64.getEncoder().encodeToString(
                (env("TEMPO_USER") + ":" + env("GRAFANA_TOKEN")).getBytes(StandardCharsets.UTF_8));
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .header("Authorization", "Basic " + auth)
                .timeout(Duration.ofSeconds(90))
                .GET().build();
        HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IllegalStateException("Tempo " + response.statusCode() + ": " + response.body());
        }
        return response.body();
    }

    private static int countTraces(String json) {
        return json == null ? 0 : json.split("\"traceID\"", -1).length - 1;
    }

    private static String env(String key) {
        String value = System.getenv(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("환경변수 " + key + " 필요");
        }
        return value;
    }
}
