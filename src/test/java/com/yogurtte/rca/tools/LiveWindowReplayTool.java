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
 * <b>라이브에서 방금 받아온</b> 스윕 응답으로 후보를 만들어 B-45 on/off를 대조한다.
 *
 * <p>저장 회차 재생({@link Ch1Round5bReplayTool})과 다른 점은 응답이 그때 것이 아니라
 * 지금 것이라는 것뿐이다 — 파이프라인은 같은 코드를 탄다.
 *
 * <p>입력은 {@code build/live/}에 curl로 받아 둔다 (파일명 고정):
 * {@code tempo-error.json · tempo-slow.json · loki-count.json · loki-exc.json · mimir-N.json(+ .q)}
 *
 * <p>실행: {@code ./gradlew test --tests '*LiveWindowReplayTool*' -Drca.tools=true -Drca.window=<from>/<to>}
 */
@EnabledIfSystemProperty(named = "rca.tools", matches = "true")
class LiveWindowReplayTool {

    private static final Path DIR = Path.of(System.getProperty("rca.live", "build/live"));
    private static final int INCIDENT_LIMIT = 30;

    @Test
    void 라이브_스윕으로_후보를_만든다() throws Exception {
        String[] range = System.getProperty("rca.window",
                "2026-08-06T03:30:00Z/2026-08-06T05:30:00Z").split("/");
        TimeWindow window = new TimeWindow(Instant.parse(range[0]), Instant.parse(range[1]));

        Map<String, String> metrics = new LinkedHashMap<>();
        try (var files = Files.list(DIR)) {
            for (Path file : files.filter(p -> p.getFileName().toString().matches("mimir-\\d+\\.json")).toList()) {
                Path q = Path.of(file.toString().replace(".json", ".q"));
                metrics.put(Files.readString(q).trim(), Files.readString(file));
            }
        }

        System.out.println("=====LIVE=====");
        System.out.println("창 " + window.start() + " ~ " + window.end() + "  (" + DIR + ")");
        run(System.getProperty("rca.arm", "arm"), window, metrics,
                java.nio.file.Files.exists(DIR.resolve("loki-exc.json")) ? read("loki-exc.json") : null);
        System.out.println("=====END=====");
    }

    private static void run(String arm, TimeWindow window, Map<String, String> metrics, String exc) throws Exception {
        SurveyResult survey = new SurveyResult(window, "라이브",
                read("tempo-error.json"), read("tempo-slow.json"), read("loki-count.json"), exc,
                metrics, List.of(), Map.of());

        List<Signal> signals = SignalExtractor.extract(survey, Duration.parse("PT" + System.getProperty("rca.step", "5") + "M"),
                Set.of("up", "mongodb_up", "kafka_brokers"));
        List<Incident> clustered = Incident.cluster(signals, Duration.ofSeconds(60));
        List<Incident> shown = clustered.size() <= INCIDENT_LIMIT
                ? clustered : clustered.subList(0, INCIDENT_LIMIT);
        String context = new SurveyContextAssembler().assemble(survey, "최근 오류를 확인해줘", shown, false);

        com.yogurtte.rca.collector.TimeWindow probe2 = com.yogurtte.rca.triage.incident.Incident.unionWindow(
                shown, Duration.ofMinutes(2), Duration.ofMinutes(5), window);
        long widthMin = java.time.Duration.between(probe2.start(), probe2.end()).toMinutes();
        System.out.printf("%n[%s] 신호 %d · 후보 %d%s · 컨텍스트 %,d자 · 전체 선택 시 창 %d분 (%s ~ %s)%n",
                arm, signals.size(), clustered.size(),
                clustered.size() > shown.size() ? " (상한 " + INCIDENT_LIMIT + "로 잘림)" : "", context.length(),
                widthMin, probe2.start(), probe2.end());
        shown.stream().filter(i -> i.channel() == com.yogurtte.rca.triage.incident.Channel.LOKI)
                .forEach(i -> System.out.println(i.describe()));
    }

    private static String read(String name) throws Exception {
        Path file = DIR.resolve(name);
        return Files.exists(file) ? Files.readString(file) : null;
    }
}
