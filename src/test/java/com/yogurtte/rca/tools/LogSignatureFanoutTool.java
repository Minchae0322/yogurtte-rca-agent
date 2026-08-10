package com.yogurtte.rca.tools;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import com.yogurtte.rca.collector.TimeWindow;
import com.yogurtte.rca.triage.incident.Incident;
import com.yogurtte.rca.triage.incident.Signal;
import com.yogurtte.rca.triage.incident.SignalExtractor;
import com.yogurtte.rca.triage.survey.SurveyResult;

/**
 * 예외 클래스 곡선(B-45)을 켜면 <b>로그 후보가 몇 개로 불어나는가</b>를 창 7개에서 잰다.
 *
 * <p>로그 채널만 넣는다 — Tempo·Mimir는 이 변경과 무관해 넣으면 증가분이 희석된다.
 * 곡선은 라이브 Loki에서 같은 창·같은 쿼리로 받아 {@code -Drca.curves=<dir>} 에 둔 것을 읽는다.
 *
 * <p>실행: {@code ./gradlew test --tests '*LogSignatureFanoutTool*' -Drca.tools=true -Drca.curves=<dir>}
 */
@EnabledIfSystemProperty(named = "rca.tools", matches = "true")
class LogSignatureFanoutTool {

    /** 창은 {@code reports/rounds/*.json} 의 surveyStart~surveyEnd 그대로다. */
    private static final String[][] ROUNDS = {
            {"ch-1-round5", "2026-08-05T06:23:03Z", "2026-08-05T07:23:03Z"},
            {"ch-1-round5b", "2026-08-05T07:28:31Z", "2026-08-05T08:28:31Z"},
            {"ch-1-round6", "2026-08-05T07:09:23Z", "2026-08-05T08:09:23Z"},
            {"ch-2-round5", "2026-08-05T23:52:20Z", "2026-08-06T00:52:20Z"},
            {"ch-3-round5", "2026-08-06T00:36:05Z", "2026-08-06T01:36:05Z"},
            {"in-1-round5", "2026-08-06T03:45:46Z", "2026-08-06T04:45:46Z"},
            {"in-2-round5", "2026-08-06T04:04:55Z", "2026-08-06T05:04:55Z"},
    };

    @Test
    void 로그_후보가_몇_배로_불어나는가() throws Exception {
        Path dir = Path.of(System.getProperty("rca.curves", "build/curves"));
        System.out.println("=====FANOUT=====");
        System.out.printf("%-14s %6s %6s %6s   %s%n", "회차", "전", "후", "증가", "새로 생긴 지문");
        int beforeAll = 0, afterAll = 0;

        for (String[] round : ROUNDS) {
            TimeWindow window = new TimeWindow(Instant.parse(round[1]), Instant.parse(round[2]));
            String base = read(dir.resolve(round[0] + "-base.json"));
            String signature = read(dir.resolve(round[0] + "-sig.json"));

            int before = lokiIncidents(window, base, null).size();
            List<Incident> after = lokiIncidents(window, base, signature);
            beforeAll += before;
            afterAll += after.size();

            String added = after.stream()
                    .map(Incident::signature)
                    .filter(s -> !"ERROR/WARN".equals(s))
                    .distinct()
                    .map(LogSignatureFanoutTool::shorten)
                    .reduce((a, b) -> a + ", " + b).orElse("(없음)");
            System.out.printf("%-14s %6d %6d %6s   %s%n",
                    round[0], before, after.size(), "+" + (after.size() - before), added);
        }
        System.out.printf("%-14s %6d %6d %6s%n", "합계", beforeAll, afterAll, "+" + (afterAll - beforeAll));
        System.out.println("=====END=====");
    }

    private static List<Incident> lokiIncidents(TimeWindow window, String base, String signature) {
        SurveyResult survey = new SurveyResult(window, "fanout", null, null, base, signature,
                Map.of(), List.of(), Map.of());
        List<Signal> signals = SignalExtractor.extract(survey, Duration.ofMinutes(5));
        return Incident.cluster(signals, Duration.ofSeconds(60));
    }

    /** 패키지를 떼어 표가 넘치지 않게 한다. 원문은 후보 줄에 그대로 실린다. */
    private static String shorten(String signature) {
        int dot = signature.lastIndexOf('.');
        return dot < 0 ? signature : signature.substring(dot + 1);
    }

    private static String read(Path file) throws Exception {
        return Files.exists(file) ? Files.readString(file) : null;
    }
}
