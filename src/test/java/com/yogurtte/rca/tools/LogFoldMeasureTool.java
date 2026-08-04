package com.yogurtte.rca.tools;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import com.yogurtte.rca.analyzer.ContextAssembler;
import com.yogurtte.rca.analyzer.LogFoldProperties;
import com.yogurtte.rca.analyzer.ServiceGraphExtractor;
import com.yogurtte.rca.analyzer.TraceCompactProperties;
import com.yogurtte.rca.collector.CollectProperties;
import com.yogurtte.rca.collector.CollectedData;

/**
 * 로그 접기(B-34)가 <b>실제 원본</b>에서 몇 바이트를 줄이는지 잰다.
 *
 * <p><b>왜 도구인가.</b> 단위 테스트는 규칙을 고정할 뿐 크기를 말해 주지 않고,
 * {@code reports/raw/}는 gitignore라 커밋되는 테스트가 의존할 수 없다. 그래서 "얼마나
 * 줄었나"는 별도 도구로 재고, 그 수치를 회차 문서에 옮긴다.
 *
 * <p>재는 대상은 두 가지다.
 * <ul>
 *   <li><b>로그 절 단독</b> — {@code reports/raw/*-loki-error-warn*.json} 전수. 접기 규칙 자체의 효과</li>
 *   <li><b>분석 컨텍스트 전체</b> — 같은 실행의 로그·트레이스·메트릭을 어셈블한 결과.
 *       분모가 컨텍스트라 <b>토큰 절감률은 이쪽</b>이다</li>
 * </ul>
 *
 * <p>실행: {@code ./gradlew test --tests '*LogFoldMeasureTool*' -Drca.tools=true}
 */
@EnabledIfSystemProperty(named = "rca.tools", matches = "true")
class LogFoldMeasureTool {

    private static final Path RAW = Path.of("reports", "raw");
    private static final List<String> APP = List.of("com.example");
    /** 규칙을 하나씩 얹어 <b>어느 규칙이 얼마를 줄였는지</b> 가른다 — 한 번에 하나만 바꾼다. */
    private static final LogFoldProperties OFF = LogFoldProperties.off();
    private static final LogFoldProperties FRAMES = LogFoldProperties.framesOnly(APP);
    private static final LogFoldProperties FRAMES_NOISE = new LogFoldProperties(true, APP, 2, true, 0);
    private static final LogFoldProperties ALL = new LogFoldProperties(true, APP, 2, true, 3);

    @Test
    void measureLogFoldOnStoredRawResponses() throws IOException {
        List<Path> logs = new ArrayList<>();
        try (Stream<Path> stream = Files.list(RAW)) {
            stream.filter(p -> p.getFileName().toString().endsWith(".json"))
                    .filter(p -> p.getFileName().toString().contains("loki-"))
                    .sorted()
                    .forEach(logs::add);
        }
        assertThat(logs).as("reports/raw/*loki*.json 이 있어야 한다").isNotEmpty();

        System.out.println();
        System.out.printf("%-52s %9s %9s %9s %9s %8s%n",
                "원본", "접기 전", "+프레임", "+잡음", "+근사반복", "감소");
        System.out.println("-".repeat(104));

        long before = 0;
        long afterFrames = 0;
        long afterNoise = 0;
        long after = 0;
        for (Path path : logs) {
            String json = Files.readString(path, StandardCharsets.UTF_8);
            String folded = assembleLogSection(json, ALL);

            int b = bytes(assembleLogSection(json, OFF));
            int f = bytes(assembleLogSection(json, FRAMES));
            int n = bytes(assembleLogSection(json, FRAMES_NOISE));
            int a = bytes(folded);
            before += b;
            afterFrames += f;
            afterNoise += n;
            after += a;

            if (Boolean.getBoolean("rca.tools.dump")) {
                Path out = Path.of("build", "logfold");
                Files.createDirectories(out);
                Files.writeString(out.resolve(path.getFileName()), folded, StandardCharsets.UTF_8);
            }

            System.out.printf("%-52s %,9d %,9d %,9d %,9d %7.1f%%%n",
                    path.getFileName(), b, f, n, a, pct(b, a));
        }

        System.out.println("-".repeat(104));
        System.out.printf("합계 %d개 파일 %40s %,9d %,9d %,9d %,9d %7.1f%%%n",
                logs.size(), "", before, afterFrames, afterNoise, after, pct(before, after));
        System.out.printf("규칙별 기여: 프레임 %.1f%% · 잡음 %.1f%% · 근사반복 %.1f%%%n",
                pct(before, afterFrames), pct(afterFrames, afterNoise), pct(afterNoise, after));
        System.out.println();
    }

    /**
     * 로그 절만 떼어 재려고 트레이스·메트릭을 비운 {@link CollectedData}로 어셈블한다.
     * 접기 전후 차이 외에 다른 절이 섞이지 않는다.
     */
    private static String assembleLogSection(String logJson, LogFoldProperties fold) {
        ContextAssembler assembler = new ContextAssembler(
                new CollectProperties(120, "content-service|auth-service|chat-service", "service_name",
                        1000, "15s", List.of(), 102_400, 30, 10, true),
                new ServiceGraphExtractor(), fold, TraceCompactProperties.off());
        CollectedData data = new CollectedData("measure", null, logJson, null,
                new LinkedHashMap<>(), new LinkedHashMap<>(), List.of(), Map.of());
        return assembler.assemble(data, "(측정)");
    }

    private static int bytes(String s) {
        return s.getBytes(StandardCharsets.UTF_8).length;
    }

    private static double pct(long before, long after) {
        return before == 0 ? 0 : -100.0 * (before - after) / before;
    }
}
