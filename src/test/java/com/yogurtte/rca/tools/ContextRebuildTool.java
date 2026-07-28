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

    @Test
    void rebuildContextsFromRawResponses() throws IOException {
        Files.createDirectories(OUT);
        var systemPrompt = Files.readString(Path.of("prompts", "system-prompt.md"), StandardCharsets.UTF_8);

        // 셀렉터 값은 재구성에 영향이 없다(어셈블은 이미 받은 JSON을 붙일 뿐).
        // 조사 당시 설정과 같아야 하는 것은 maxTraceBytes·topSpans뿐이다.
        var assembler = new ContextAssembler(new CollectProperties(
                120, "content-service|auth-service|chat-service", "service_name", 1000, "15s",
                List.of(), MAX_TRACE_BYTES, TOP_SPANS));

        var reports = new ArrayList<Path>();
        try (var stream = Files.list(REPORTS)) {
            stream.filter(p -> p.getFileName().toString().endsWith(".json")).sorted().forEach(reports::add);
        }
        assertThat(reports).as("reports/*.json 이 있어야 한다").isNotEmpty();

        var rawFiles = new ArrayList<Path>();
        try (var stream = Files.list(RAW)) {
            stream.filter(p -> p.getFileName().toString().endsWith(".json")).forEach(rawFiles::add);
        }

        System.out.println();
        System.out.printf("%-34s %10s %10s %8s  %s%n", "traceId", "재구성", "기록됨", "일치", "출력");
        System.out.println("-".repeat(96));

        var matched = 0;
        for (var reportPath : reports) {
            var report = MAPPER.readTree(Files.readString(reportPath, StandardCharsets.UTF_8));
            var traceId = report.path("traceId").asText();
            var stamp = stampOf(reportPath.getFileName().toString(), traceId);

            var data = rebuild(traceId, stamp, report, rawFiles);
            if (data.isEmpty()) {
                System.out.printf("%-34s %10s%n", traceId, "원본 없음");
                continue;
            }

            var context = assembler.assemble(data.get(), report.path("question").asText());
            var recorded = report.path("coverage").path("contextChars").asInt(-1);
            var ok = context.length() == recorded;
            if (ok) {
                matched++;
            }

            var out = OUT.resolve(traceId + ".txt");
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

        var trace = latestBefore(rawFiles, traceId, "tempo-trace", reportStamp);
        if (trace.isEmpty()) {
            return Optional.empty();
        }

        var metrics = new LinkedHashMap<String, String>();
        for (var query : report.path("coverage").path("metricsCollected")) {
            var name = "mimir-" + query.asText().replaceAll("[^a-zA-Z0-9._-]", "_");
            latestBefore(rawFiles, traceId, name, reportStamp)
                    .ifPresent(body -> metrics.put(query.asText(), body));
        }

        var failures = new ArrayList<String>();
        report.path("collectionFailures").forEach(f -> failures.add(f.asText()));

        var coverage = report.path("coverage");
        TimeWindow window = null;
        if (!coverage.path("windowStart").isMissingNode() && !coverage.path("windowStart").isNull()) {
            window = new TimeWindow(
                    Instant.parse(coverage.path("windowStart").asText()),
                    Instant.parse(coverage.path("windowEnd").asText()));
        }

        return Optional.of(new CollectedData(
                traceId,
                trace.get(),
                window,
                latestBefore(rawFiles, traceId, "loki-error-warn", reportStamp).orElse(null),
                latestBefore(rawFiles, traceId, "loki-trace-id", reportStamp).orElse(null),
                metrics,
                failures,
                null));
    }

    private static Optional<String> latestBefore(
            List<Path> rawFiles, String traceId, String artifact, String reportStamp) throws IOException {

        var suffix = "-" + artifact + ".json";
        var best = rawFiles.stream()
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
