package com.yogurtte.rca.tools;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import com.yogurtte.rca.analyzer.MetricSummaryProbe;

/**
 * B-25가 <b>저장된 실제 응답</b>에서 얼마나 줄이는지 잰다. 조사를 다시 돌리지 않는다 —
 * {@code reports/raw/}에 원본이 남아 있어 재조사 없이 대조가 된다.
 *
 * <pre>./gradlew test --tests '*MetricSummarySizeTool*' -Drca.tools=true</pre>
 */
@EnabledIfSystemProperty(named = "rca.tools", matches = "true")
class MetricSummarySizeTool {

    @Test
    void 조사별_메트릭_원본과_요약_크기를_비교한다() throws IOException {
        Path raw = Path.of("reports/raw");
        LinkedHashMap<String, long[]> perScan = new LinkedHashMap<>();

        try (Stream<Path> files = Files.list(raw)) {
            files.filter(p -> p.getFileName().toString().contains("-mimir-"))
                    .sorted()
                    .forEach(p -> {
                        String name = p.getFileName().toString();
                        String scan = name.substring(0, name.indexOf("-mimir-"));
                        String query = name.substring(name.indexOf("-mimir-") + 7).replace(".json", "");
                        try {
                            String body = Files.readString(p, StandardCharsets.UTF_8);
                            String summary = MetricSummaryProbe.summarize(query, body);
                            long[] acc = perScan.computeIfAbsent(scan, k -> new long[2]);
                            acc[0] += body.getBytes(StandardCharsets.UTF_8).length;
                            acc[1] += summary.getBytes(StandardCharsets.UTF_8).length;
                        } catch (IOException e) {
                            throw new IllegalStateException(e);
                        }
                    });
        }

        System.out.println("\n=== B-25 메트릭 요약 절감 (저장 원본 실측) ===");
        System.out.printf("%-42s %12s %12s %8s%n", "조사", "원본", "요약", "절감");
        long totalRaw = 0;
        long totalSummary = 0;
        for (var entry : perScan.entrySet()) {
            long[] v = entry.getValue();
            totalRaw += v[0];
            totalSummary += v[1];
            System.out.printf("%-42s %,12d %,12d %7.1f%%%n",
                    entry.getKey(), v[0], v[1], 100.0 * (v[0] - v[1]) / v[0]);
        }
        System.out.printf("%-42s %,12d %,12d %7.1f%%%n", "합계", totalRaw, totalSummary,
                100.0 * (totalRaw - totalSummary) / totalRaw);
    }
}
