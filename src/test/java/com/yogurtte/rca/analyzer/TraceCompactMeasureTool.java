package com.yogurtte.rca.analyzer;

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

import com.yogurtte.rca.collector.CollectProperties;
import com.yogurtte.rca.collector.CollectedData;

/**
 * 트레이스 압축(B-35)이 <b>실제 원본</b>에서 몇 바이트를 줄이는지 잰다.
 * {@code reports/raw/}는 gitignore라 커밋되는 테스트가 의존할 수 없어서 도구로 뺐다.
 *
 * <p><b>축이 둘이고 섞으면 안 된다.</b>
 * <ul>
 *   <li><b>트레이스 JSON</b> — 압축 규칙 자체의 효과</li>
 *   <li><b>절 전체</b> — 표기가 바뀐 것을 알리는 안내문까지 포함한, <b>모델이 실제로 보는</b> 크기.
 *       회차 문서에 쓰는 값은 이쪽이다</li>
 * </ul>
 * 이 도구가 {@code tools}가 아니라 {@code analyzer} 패키지에 있는 이유는 {@link TraceCompact}가
 * package-private이라서다 — 절 크기만으로는 두 축을 가를 수 없다.
 *
 * <p>실행: {@code ./gradlew test --tests '*TraceCompactMeasureTool*' -Drca.tools=true}
 */
@EnabledIfSystemProperty(named = "rca.tools", matches = "true")
class TraceCompactMeasureTool {

    private static final Path RAW = Path.of("reports", "raw");
    /** 절삭이 섞이면 압축 효과가 아니라 절삭 효과를 재게 된다 — 한도를 크게 잡아 무력화한다. */
    private static final int NO_TRUNCATION = 100_000_000;

    @Test
    void measureTraceCompactOnStoredRawResponses() throws IOException {
        List<Path> traces = new ArrayList<>();
        try (Stream<Path> stream = Files.list(RAW)) {
            stream.filter(p -> p.getFileName().toString().endsWith(".json"))
                    .filter(p -> p.getFileName().toString().contains("tempo-trace"))
                    .sorted()
                    .forEach(traces::add);
        }
        assertThat(traces).as("reports/raw/*tempo-trace*.json 이 있어야 한다").isNotEmpty();

        long jsonBefore = 0;
        long jsonAfter = 0;
        long sectionBefore = 0;
        long sectionAfter = 0;
        long spans = 0;
        long hoisted = 0;
        int grew = 0;
        for (Path path : traces) {
            String json = Files.readString(path, StandardCharsets.UTF_8);
            TraceCompact.Result compacted = TraceCompact.compact(json, TraceCompactProperties.on());

            jsonBefore += bytes(json);
            jsonAfter += bytes(compacted.json());
            spans += compacted.spans();
            hoisted += compacted.hoisted();

            int b = bytes(assemble(json, TraceCompactProperties.off()));
            int a = bytes(assemble(json, TraceCompactProperties.on()));
            sectionBefore += b;
            sectionAfter += a;
            if (a > b) {
                grew++;
                System.out.printf("커진 절: %-58s %,9d → %,9d%n", path.getFileName(), b, a);
            }
        }

        System.out.println();
        System.out.printf("트레이스 %d건 · span %,d개 · 호이스팅된 속성 %,d개%n", traces.size(), spans, hoisted);
        System.out.printf("  트레이스 JSON   %,10d B → %,10d B  %6.1f%%   (압축 규칙 자체)%n",
                jsonBefore, jsonAfter, pct(jsonBefore, jsonAfter));
        System.out.printf("  절 전체        %,10d B → %,10d B  %6.1f%%   (안내문 포함 · 모델이 보는 것)%n",
                sectionBefore, sectionAfter, pct(sectionBefore, sectionAfter));
        System.out.printf("커진 절: %d건%n%n", grew);
    }

    private static String assemble(String traceJson, TraceCompactProperties compact) {
        ContextAssembler assembler = new ContextAssembler(
                new CollectProperties(120, "content-service|auth-service|chat-service", "service_name",
                        1000, "15s", List.of(), NO_TRUNCATION, 30, 10, true),
                new ServiceGraphExtractor(), LogFoldProperties.off(), compact);
        LinkedHashMap<String, String> trace = new LinkedHashMap<>();
        trace.put("measure", traceJson);
        CollectedData data = new CollectedData("measure", null, null, null,
                new LinkedHashMap<>(), trace, List.of(), Map.of());
        return assembler.assemble(data, "(측정)");
    }

    private static int bytes(String s) {
        return s.getBytes(StandardCharsets.UTF_8).length;
    }

    private static double pct(long before, long after) {
        return before == 0 ? 0 : -100.0 * (before - after) / before;
    }
}
