package com.yogurtte.rca.tools;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import com.yogurtte.rca.collector.TimeWindow;
import com.yogurtte.rca.triage.incident.Incident;
import com.yogurtte.rca.triage.incident.Signal;
import com.yogurtte.rca.triage.incident.SignalExtractor;
import com.yogurtte.rca.triage.plan.SurveyContextAssembler;
import com.yogurtte.rca.triage.survey.SurveyResult;

/**
 * B-45 A/B — 예외 클래스 곡선 없이(A) / 있게(B) 탐색 컨텍스트를 만들어 파일로 낸다.
 *
 * <p>탐색 LLM에 각각 물어 <b>고른 후보와 좁힌 창이 달라지는지</b>를 보려는 것이다.
 * 상한 절삭({@code incidentLimit})도 운영과 같게 적용한다 — B팔은 후보가 상한을 넘어
 * 잘리는데, 그 절삭 자체가 이 실험의 관찰 대상이다.
 *
 * <p>실행: {@code ./gradlew test --tests '*TriageArmContextTool*' -Drca.tools=true}
 * 출력: {@code build/triage-arm-{A,B}.txt}
 */
@EnabledIfSystemProperty(named = "rca.tools", matches = "true")
class TriageArmContextTool {

    private static final Path RAW = Path.of("reports/raw");
    private static final String RUN = "scan-1785914911";
    private static final int INCIDENT_LIMIT = 15;
    private static final TimeWindow WINDOW = new TimeWindow(
            Instant.parse("2026-08-05T07:28:31.430Z"), Instant.parse("2026-08-05T08:28:31.430Z"));
    private static final String QUESTION = "최근 1시간 안에 댓글 알림이 안 왔다는 제보가 있어요. 확인해줘";

    @Test
    void 두_팔의_탐색_컨텍스트를_낸다() throws Exception {
        Map<String, String> metrics = new LinkedHashMap<>();
        try (var files = Files.list(RAW)) {
            for (Path file : files.filter(p -> p.getFileName().toString().startsWith(RUN)).toList()) {
                String name = file.getFileName().toString();
                if (name.contains("-mimir-")) {
                    metrics.put(queryOf(name.replaceAll(".*-mimir-", "").replace(".json", "")),
                            Files.readString(file));
                }
            }
        }
        String errorTraces = read(RUN + "-20260805T082833-tempo-search.json");
        String slowTraces = read(RUN + "-slow-20260805T082833-tempo-search.json");
        String logRates = read(RUN + "-20260805T082834-loki-survey-error-rate.json");
        String logSignature = read(RUN + "-20260805T082834-loki-survey-log-signature.json");

        write("A", errorTraces, slowTraces, logRates, null, metrics);
        write("B", errorTraces, slowTraces, logRates, logSignature, metrics);
    }

    private static void write(String arm, String errorTraces, String slowTraces,
                              String logRates, String logSignature, Map<String, String> metrics) throws Exception {
        SurveyResult probe = new SurveyResult(WINDOW, "상대 표현 '최근 1시간'", errorTraces, slowTraces,
                logRates, logSignature, metrics, List.of(), Map.of());
        List<Signal> signals = SignalExtractor.extract(probe, Duration.ofMinutes(5),
                Set.of("up", "mongodb_up", "kafka_brokers"));
        List<Incident> clustered = Incident.cluster(signals, Duration.ofSeconds(60));
        List<Incident> incidents = clustered.size() <= INCIDENT_LIMIT
                ? clustered : clustered.subList(0, INCIDENT_LIMIT);

        // failures는 불변이라 절삭 문구를 넣은 채로 다시 만든다 — 운영에서는 스윕이 실패 목록을
        // 들고 오고 TriageService가 거기에 더한다.
        List<String> failures = clustered.size() > incidents.size()
                ? List.of("장애 후보가 " + clustered.size() + "건이라 상한 " + INCIDENT_LIMIT
                        + "건으로 잘랐다 — 창에 신호가 많다는 뜻이니 고른 후보 밖에도 장애가 있을 수 있다.")
                : List.of();
        SurveyResult survey = new SurveyResult(WINDOW, "상대 표현 '최근 1시간'", errorTraces, slowTraces,
                logRates, logSignature, metrics, failures, Map.of());

        String context = new SurveyContextAssembler().assemble(survey, QUESTION, incidents, false);
        Path out = Path.of("build/triage-arm-" + arm + ".txt");
        Files.createDirectories(out.getParent());
        Files.writeString(out, context);
        System.out.printf("팔 %s — 신호 %d · 군집 %d · 실린 후보 %d · 컨텍스트 %,d자 → %s%n",
                arm, signals.size(), clustered.size(), incidents.size(), context.length(), out);
    }

    private static String queryOf(String sanitized) {
        return sanitized
                .replaceAll("^(min_over_time|max_over_time|rate)_", "$1(")
                .replaceAll("_([0-9]+[smhd])__$", "[$1])");
    }

    private static String read(String name) throws Exception {
        Path file = RAW.resolve(name);
        return Files.exists(file) ? Files.readString(file) : null;
    }
}
