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
import com.yogurtte.rca.triage.survey.SurveyResult;

/**
 * CH-1 회차 5b의 저장 스윕을 <b>지금 코드로</b> 다시 태운다 (B-44 전후 대조).
 *
 * <p>주입도 배포도 필요 없다 — 그때의 Tempo·Loki·Mimir 응답이 {@code reports/raw/}에 그대로
 * 있고, 그때 나온 후보 13개는 {@code reports/rounds/ch-1-round5b.json}에 박제돼 있다.
 * <b>후보 개수가 변하면 B-44가 지문에 새어 들어간 것이다.</b>
 *
 * <p>실행: {@code ./gradlew test --tests '*Ch1Round5bReplayTool*' -Drca.tools=true}
 */
@EnabledIfSystemProperty(named = "rca.tools", matches = "true")
class Ch1Round5bReplayTool {

    private static final Path RAW = Path.of("reports/raw");
    private static final String RUN = "scan-1785914911";
    private static final TimeWindow WINDOW = new TimeWindow(
            Instant.parse("2026-08-05T07:28:31.430Z"), Instant.parse("2026-08-05T08:28:31.430Z"));

    @Test
    void 저장_스윕으로_후보를_다시_만든다() throws Exception {
        Map<String, String> metrics = new LinkedHashMap<>();
        try (var files = Files.list(RAW)) {
            for (Path file : files.filter(p -> p.getFileName().toString().startsWith(RUN)).toList()) {
                String name = file.getFileName().toString();
                if (name.contains("-mimir-")) {
                    // 파일명은 쿼리를 파일명으로 쓸 수 있게 "소독"한 형태다. 그대로 키로 쓰면
                    // metricNameOf()가 min_over_time_mongodb_up_5m__ 를 지표명으로 읽어
                    // zero-is-abnormal 목록(up·mongodb_up·kafka_brokers)과 안 맞고
                    // 0 구간 신호가 통째로 사라진다 — 실제로 후보가 13개에서 14개로 늘었다.
                    metrics.put(queryOf(name.replaceAll(".*-mimir-", "").replace(".json", "")),
                            Files.readString(file));
                }
            }
        }

        SurveyResult survey = new SurveyResult(WINDOW, "상대 표현 '최근 1시간'",
                read(RUN + "-20260805T082833-tempo-search.json"),
                read(RUN + "-slow-20260805T082833-tempo-search.json"),
                read(RUN + "-20260805T082834-loki-survey-error-rate.json"),
                // 이 회차에는 없던 채널이다 — 같은 창·같은 쿼리로 나중에 받아 저장한 것이고,
                // 파일이 없으면 null이 되어 예전 동작 그대로 돈다.
                read(RUN + "-20260805T082834-loki-survey-log-signature.json"),
                metrics, List.of(), Map.of());

        List<Signal> signals = SignalExtractor.extract(survey, Duration.ofMinutes(5),
                Set.of("up", "mongodb_up", "kafka_brokers"));
        List<Incident> incidents = Incident.cluster(signals, Duration.ofSeconds(60));

        System.out.println("=====REPLAY=====");
        System.out.println("신호 " + signals.size() + "개 → 후보 " + incidents.size() + "개");
        System.out.println("(박제된 회차 5b: 후보 13개)");
        incidents.forEach(incident -> System.out.println(incident.describe()));
        System.out.println("=====END=====");
    }

    /** 소독된 파일명을 원래 PromQL로 되돌린다 — {@code min_over_time_up_5m__} → {@code min_over_time(up[5m])}. */
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
