package com.yogurtte.rca.tools;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yogurtte.rca.analyzer.ContextAssembler;
import com.yogurtte.rca.collector.CollectProperties;
import com.yogurtte.rca.collector.CollectedData;
import com.yogurtte.rca.collector.TimeWindow;

/**
 * 과거 조사의 컨텍스트를 {@code reports/raw/} 원본 덤프에서 <b>재구성</b>해 파일로 떨군다.
 *
 * <p><b>왜.</b> 과거 회차의 리포트에는 {@code contextChars}(문자 수)만 있고 토큰 수가 없다.
 * 그런데 개선 지표는 토큰이다. 토큰을 사후에 재려면 그때 모델이 실제로 본 텍스트가 필요하고,
 * 다행히 {@code RawResponseStore}가 외부 API 원본을 전부 남겨둬서 복원이 가능하다.
 *
 * <p>복원이 정확한지는 <b>재구성한 길이가 리포트의 {@code contextChars}와 일치하는지</b>로
 * 검증한다. 한 글자라도 다르면 이 도구가 만든 텍스트는 그때 그 입력이 아니다.
 *
 * <p>출력물은 {@code build/contexts/<traceId>.txt}이고, 내용은 {@code ClaudeCliLlmClient}가
 * stdin으로 밀어넣는 것과 같은 형태({@code systemPrompt + "\n\n---\n\n" + context})다.
 * 이어서 {@code scripts/measure-contexts.ps1}이 이 파일들의 토큰을 잰다.
 *
 * <p><b>한계:</b> 시스템 프롬프트는 <b>현재 파일</b>을 쓴다(그때 버전이 보존돼 있지 않다).
 * 전체의 1% 미만이라 비교에는 영향이 없으나, 절대값을 인용할 때는 밝혀야 한다.
 *
 * <p>평소엔 건너뛴다. 실행: {@code ./gradlew test --tests '*ContextRebuildTool*' -Drca.tools=true}
 */
@EnabledIfSystemProperty(named = "rca.tools", matches = "true")
class ContextRebuildTool {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Path REPORTS = Path.of("reports");
    private static final Path RAW = REPORTS.resolve("raw");
    private static final Path OUT = Path.of("build", "contexts");

    /** 조사 당시 설정. application.yml 기본값과 같아야 재구성이 일치한다. */
    private static final int MAX_TRACE_BYTES = 102_400;
    private static final int TOP_SPANS = 30;
    /**
     * 과거 회차를 복원할 때는 <b>false</b>여야 한다 — 그 회차는 메트릭 원본을 그대로 실었고,
     * 요약(B-25)으로 재조립하면 {@code contextChars} 검증이 깨진다. B-25 적용 후 회차를
     * 복원할 때만 {@code true}로 바꾼다.
     */
    private static final boolean METRIC_SUMMARY = Boolean.getBoolean("rca.tools.metric-summary");

    @Test
    void rebuildContextsFromRawResponses() throws IOException {
        Files.createDirectories(OUT);
        String systemPrompt = Files.readString(Path.of("prompts", "system-prompt.md"), StandardCharsets.UTF_8);

        // 셀렉터 값은 재구성에 영향이 없다(어셈블은 이미 받은 JSON을 붙일 뿐).
        // 조사 당시 설정과 같아야 하는 것은 maxTraceBytes·topSpans뿐이다.
        // maxTraces=1: 과거 조사엔 후보 수집(B-9)이 없었다 — 재구성이 그때 입력과 같아야 한다.
        // 로그 접기(B-34)는 끈다 — 과거 회차는 접기 이전 텍스트를 봤고, 접어서 재조립하면
        // contextChars 일치 검증이 통째로 깨진다. 접기의 효과는 LogFoldMeasureTool이 따로 잰다.
        ContextAssembler assembler = new ContextAssembler(new CollectProperties(
                120, "content-service|auth-service|chat-service", "service_name", 1000, "15s",
                List.of(), MAX_TRACE_BYTES, TOP_SPANS, 1, METRIC_SUMMARY),
                new com.yogurtte.rca.analyzer.ServiceGraphExtractor(),
                com.yogurtte.rca.analyzer.LogFoldProperties.off(),
                com.yogurtte.rca.analyzer.TraceCompactProperties.off());

        ArrayList<Path> reports = new ArrayList<>();
        try (Stream<Path> stream = Files.list(REPORTS)) {
            stream.filter(p -> p.getFileName().toString().endsWith(".json")).sorted().forEach(reports::add);
        }
        assertThat(reports).as("reports/*.json 이 있어야 한다").isNotEmpty();

        ArrayList<Path> rawFiles = new ArrayList<>();
        try (Stream<Path> stream = Files.list(RAW)) {
            stream.filter(p -> p.getFileName().toString().endsWith(".json")).forEach(rawFiles::add);
        }

        System.out.println();
        System.out.printf("%-34s %10s %10s %8s  %s%n", "traceId", "재구성", "기록됨", "일치", "출력");
        System.out.println("-".repeat(96));

        int matched = 0;
        for (Path reportPath : reports) {
            JsonNode report = MAPPER.readTree(Files.readString(reportPath, StandardCharsets.UTF_8));
            String traceId = report.path("traceId").asText();
            String stamp = stampOf(reportPath.getFileName().toString(), traceId);

            Optional<CollectedData> data = rebuild(traceId, stamp, report, rawFiles);
            if (data.isEmpty()) {
                System.out.printf("%-34s %10s%n", traceId, "원본 없음");
                continue;
            }

            String context = assembler.assemble(data.get(), report.path("question").asText());
            int recorded = report.path("coverage").path("contextChars").asInt(-1);
            boolean ok = context.length() == recorded;
            if (ok) {
                matched++;
            }

            Path out = OUT.resolve(traceId + ".txt");
            Files.writeString(out, systemPrompt + "\n\n---\n\n" + context, StandardCharsets.UTF_8);

            System.out.printf("%-34s %,10d %,10d %8s  %s%n",
                    traceId, context.length(), recorded, ok ? "OK" : "불일치", out);
        }

        System.out.println("-".repeat(96));
        System.out.printf("복원 검증: %d/%d 일치%n", matched, reports.size());
        System.out.println("다음: .\\scripts\\measure-contexts.ps1  (차분 측정으로 토큰 산출)");
        System.out.println();
    }

    /** 리포트 파일명 {@code <traceId>-<stamp>.json}에서 채록 시각을 뽑는다. */
    private static String stampOf(String fileName, String traceId) {
        return fileName.substring(traceId.length() + 1, fileName.length() - ".json".length());
    }

    /**
     * 같은 traceId를 여러 번 조사했으면 원본 세트도 여러 벌이다. 각 아티팩트마다
     * <b>리포트 시각 이전의 가장 최근 덤프</b>를 고른다 — 그게 이 리포트를 만든 실행이다.
     */
    private static Optional<CollectedData> rebuild(
            String traceId, String reportStamp, JsonNode report, List<Path> rawFiles) throws IOException {

        Optional<String> trace = latestBefore(rawFiles, traceId, "tempo-trace", reportStamp);
        if (trace.isEmpty()) {
            return Optional.empty();
        }

        LinkedHashMap<String, String> metrics = new LinkedHashMap<>();
        for (JsonNode query : report.path("coverage").path("metricsCollected")) {
            String name = "mimir-" + query.asText().replaceAll("[^a-zA-Z0-9._-]", "_");
            latestBefore(rawFiles, traceId, name, reportStamp)
                    .ifPresent(body -> metrics.put(query.asText(), body));
        }

        ArrayList<String> failures = new ArrayList<>();
        report.path("collectionFailures").forEach(f -> failures.add(f.asText()));

        JsonNode coverage = report.path("coverage");
        TimeWindow window = null;
        if (!coverage.path("windowStart").isMissingNode() && !coverage.path("windowStart").isNull()) {
            window = new TimeWindow(
                    Instant.parse(coverage.path("windowStart").asText()),
                    Instant.parse(coverage.path("windowEnd").asText()));
        }

        return Optional.of(new CollectedData(
                traceId,
                window,
                latestBefore(rawFiles, traceId, "loki-error-warn", reportStamp).orElse(null),
                latestBefore(rawFiles, traceId, "loki-trace-id", reportStamp).orElse(null),
                metrics,
                new LinkedHashMap<>(java.util.Map.of(traceId, trace.get())),
                failures,
                null));
    }

    private static Optional<String> latestBefore(
            List<Path> rawFiles, String traceId, String artifact, String reportStamp) throws IOException {

        String suffix = "-" + artifact + ".json";
        Optional<String> best = rawFiles.stream()
                .map(p -> p.getFileName().toString())
                .filter(n -> n.startsWith(traceId + "-") && n.endsWith(suffix))
                .filter(n -> n.substring(traceId.length() + 1, n.length() - suffix.length())
                        .compareTo(reportStamp) <= 0)
                .max(Comparator.naturalOrder());

        if (best.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(Files.readString(RAW.resolve(best.get()), StandardCharsets.UTF_8));
    }
}
