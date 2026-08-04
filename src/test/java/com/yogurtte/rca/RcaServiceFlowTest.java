package com.yogurtte.rca;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.yogurtte.rca.analyzer.ContextAssembler;
import com.yogurtte.rca.analyzer.EvidenceExtractor;
import com.yogurtte.rca.analyzer.LogFoldProperties;
import com.yogurtte.rca.analyzer.PromptProperties;
import com.yogurtte.rca.analyzer.ServiceGraphExtractor;
import com.yogurtte.rca.analyzer.SystemPromptLoader;
import com.yogurtte.rca.analyzer.TraceCompactProperties;
import com.yogurtte.rca.client.GrafanaConfig;
import com.yogurtte.rca.client.GrafanaProperties;
import com.yogurtte.rca.client.LokiClient;
import com.yogurtte.rca.client.MimirClient;
import com.yogurtte.rca.client.RawResponseStore;
import com.yogurtte.rca.client.TempoClient;
import com.yogurtte.rca.collector.CollectProperties;
import com.yogurtte.rca.collector.Collector;
import com.yogurtte.rca.llm.LlmConfig;
import com.yogurtte.rca.llm.LlmProperties;
import com.yogurtte.rca.llm.TokenCounter;
import com.yogurtte.rca.report.RcaReport;
import com.yogurtte.rca.report.ReportProperties;
import com.yogurtte.rca.service.RcaService;
import com.yogurtte.rca.support.FakeLlmClient;
import com.yogurtte.rca.support.RecordingNotifier;

/**
 * fake LlmClient로 v0 전체 흐름을 돈다: Tempo와 Mimir는 응답하고 Loki는 죽어 있다.
 * 그래도 실행은 중단 없이 완주하고, 그 사실을 결과에 남겨야 한다.
 */
class RcaServiceFlowTest {

    /** 이 흐름 테스트가 보는 것은 조립 순서지 접기가 아니다 — 접기는 LogStackFoldTest가 고정한다. */
    private static final LogFoldProperties NO_FOLD = LogFoldProperties.off();

    private WireMockServer server;
    private RcaService service;
    private FakeLlmClient llmClient;
    private RecordingNotifier notifier;

    @BeforeEach
    void setUp(@TempDir Path tempDir) {
        server = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        server.start();

        Instant base = Instant.parse("2026-07-20T10:00:00Z");
        server.stubFor(get(urlPathEqualTo("/api/traces/trace-1"))
                .willReturn(aResponse().withStatus(200).withBody("""
                        {"batches":[{"resource":{"attributes":[
                            {"key":"service.name","value":{"stringValue":"chat"}}]},
                          "scopeSpans":[{"spans":[
                            {"name":"notify","startTimeUnixNano":"%d","endTimeUnixNano":"%d"}]}]}]}
                        """.formatted(nanos(base), nanos(base.plusSeconds(3))))));

        // 이 실행에서 Loki는 죽어 있다.
        server.stubFor(get(urlPathEqualTo("/loki/api/v1/query_range"))
                .willReturn(aResponse().withStatus(503)));

        server.stubFor(get(urlPathEqualTo("/api/v1/query_range"))
                .willReturn(aResponse().withStatus(200).withBody(
                        "{\"status\":\"success\",\"data\":{\"result\":[{\"values\":[[1,\"7\"]]}]}}")));

        GrafanaProperties.Endpoint endpoint = new GrafanaProperties.Endpoint("http://localhost:" + server.port(), "1");
        GrafanaProperties grafana = new GrafanaProperties(endpoint, endpoint, endpoint, "tok", 3000, 10000);
        ReportProperties reportProperties = new ReportProperties(tempDir.toString());
        RawResponseStore rawStore = new GrafanaConfig().rawResponseStore(reportProperties);

        CollectProperties collectProperties = new CollectProperties(120, "content-service|auth-service|chat-service", "service_name",
                1000, "15s", List.of("hikaricp_connections_active"), 102400, 30, 3, true);

        Collector collector = new Collector(
                new GrafanaConfig().tempoClient(grafana, rawStore),
                new GrafanaConfig().lokiClient(grafana, rawStore),
                new GrafanaConfig().mimirClient(grafana, rawStore),
                collectProperties);

        llmClient = new FakeLlmClient();
        notifier = new RecordingNotifier();
        // 외부 프롬프트 경로 미설정 -> classpath 기본 프롬프트를 쓴다.
        SystemPromptLoader promptLoader = SystemPromptLoader.from(new PromptProperties(null));
        // API 키 없는 LlmProperties -> TokenCounter가 비활성이라 contextTokens는 -1이 된다.
        TokenCounter tokenCounter = new LlmConfig().tokenCounter(new LlmProperties("fake", null, null, null));
        ServiceGraphExtractor graphExtractor = new ServiceGraphExtractor();
        service = new RcaService(collector, new ContextAssembler(collectProperties, graphExtractor, NO_FOLD, TraceCompactProperties.off()),
                new EvidenceExtractor(), graphExtractor, promptLoader,
                collectProperties, llmClient, tokenCounter, notifier);
    }

    @AfterEach
    void tearDown() {
        server.stop();
    }

    @Test
    void completesAndReportsTheFailedSourceWhenLokiIsDown() {
        RcaReport report = service.investigate("trace-1", "왜 알림이 늦었어?");

        assertThat(report.analysis()).isEqualTo("원인 후보 1: Kafka consumer lag");
        assertThat(report.llmProvider()).isEqualTo("fake");
        assertThat(report.llmModel()).isEqualTo("fake-model");
        assertThat(report.llmTurns()).isEqualTo(1);
        assertThat(report.inputTokens()).isEqualTo(1234);
        assertThat(report.outputTokens()).isEqualTo(567);
        // 캐시 내역이 합산에 묻히지 않고 그대로 리포트에 실린다.
        assertThat(report.cacheReadTokens()).isEqualTo(900);
        assertThat(report.cacheCreationTokens()).isEqualTo(300);
        assertThat(report.timings().llmMs()).isEqualTo(42);
        assertThat(report.totalElapsedMs()).isGreaterThanOrEqualTo(0);

        // Loki 쿼리 2개가 모두 실패했고, 컨텍스트는 이를 숨기지 않고 모델에게 알린다.
        assertThat(report.collectionFailures()).hasSize(2);
        assertThat(report.collectionFailures()).allMatch(failure -> failure.startsWith("Loki"));
        assertThat(llmClient.seenContext).contains("# 수집 실패/누락").contains("Loki");

        // 성공한 소스는 컨텍스트에 들어 있다.
        assertThat(llmClient.seenContext).contains("notify").contains("hikaricp_connections_active");
        assertThat(llmClient.seenSystemPrompt).contains("너는 SRE다");

        // 비용은 fake가 보고한 값 그대로 실린다.
        assertThat(report.costUsd()).isEqualTo(0.0123);

        // 수집 범위: 트레이스 1 span, 메트릭은 1개 수집(누락 0), 컨텍스트 규모가 기록된다.
        RcaReport.Coverage cov = report.coverage();
        assertThat(cov).isNotNull();
        assertThat(cov.traceSpans()).isEqualTo(1);
        assertThat(cov.traceBytes()).isGreaterThan(0);
        assertThat(cov.metricsCollected()).containsExactly("hikaricp_connections_active");
        assertThat(cov.metricsMissing()).isEmpty();
        assertThat(cov.contextChars()).isEqualTo(report.contextChars());
        assertThat(cov.metricsBytes()).isGreaterThan(0);
        assertThat(cov.promptChars()).isGreaterThan(0);
        // API 키가 없으면 추정치를 지어내지 않고 "측정 안 됨"(-1)으로 남긴다.
        assertThat(cov.contextTokens()).isEqualTo(-1L);

        assertThat(notifier.sent).hasSize(1);
        assertThat(notifier.sent.get(0)).isSameAs(report);
    }

    @Test
    void collectsCandidateTracesWithinAnExplicitWindow() {
        Instant base = Instant.parse("2026-07-20T10:00:00Z");
        // B-9: 창 기준 무조건 검색이 후보를 찾는다 — 선정 트레이스(trace-1)는 후보에서 빠져야 한다.
        server.stubFor(get(urlPathEqualTo("/api/search")).willReturn(aResponse().withStatus(200).withBody(
                "{\"traces\":[{\"traceID\":\"trace-2\"},{\"traceID\":\"trace-1\"},{\"traceID\":\"trace-3\"}]}")));
        for (String id : new String[] {"trace-2", "trace-3"}) {
            server.stubFor(get(urlPathEqualTo("/api/traces/" + id))
                    .willReturn(aResponse().withStatus(200).withBody("""
                            {"batches":[{"resource":{"attributes":[
                                {"key":"service.name","value":{"stringValue":"content"}}]},
                              "scopeSpans":[{"spans":[
                                {"name":"candidate-span-%s","spanId":"%s","startTimeUnixNano":"%d","endTimeUnixNano":"%d"}]}]}]}
                            """.formatted(id, id, nanos(base), nanos(base.plusSeconds(1))))));
        }

        com.yogurtte.rca.collector.TimeWindow window = new com.yogurtte.rca.collector.TimeWindow(base.minusSeconds(60), base.plusSeconds(60));
        com.yogurtte.rca.collector.Scope scope = new com.yogurtte.rca.collector.Scope(window, List.of(), List.of("trace-1"));
        service.investigate(scope, "q", "rca", null);

        // 지목 1건 + 창 후보 2건(maxTraces=3)이 한 절에 동등하게 실린다 — 대표가 없다.
        assertThat(llmClient.seenContext).contains("# 트레이스 (Tempo · 3건)");
        assertThat(llmClient.seenContext).contains("candidate-span-trace-2").contains("candidate-span-trace-3");
        assertThat(llmClient.seenContext).contains("notify");
    }

    @Test
    void trimsAnOversizedTraceToTheLongestSpans() {
        Instant base = Instant.parse("2026-07-20T10:00:00Z");
        StringBuilder spans = new StringBuilder();
        for (int i = 0; i < 400; i++) {
            spans.append(i == 0 ? "" : ",");
            spans.append("{\"name\":\"span-with-a-deliberately-long-name-%d\",\"startTimeUnixNano\":\"%d\",\"endTimeUnixNano\":\"%d\"}"
                    .formatted(i, nanos(base), nanos(base.plusSeconds(i + 1))));
        }
        String bigTrace = """
                {"batches":[{"resource":{"attributes":[
                    {"key":"service.name","value":{"stringValue":"content"}}]},
                  "scopeSpans":[{"spans":[%s]}]}]}
                """.formatted(spans);

        CollectProperties properties = new CollectProperties(120, "content-service|auth-service|chat-service", "service_name",
                1000, "15s", List.of(), 100, 30, 3, true);  // 100 바이트 한도로 트리밍을 강제한다
        com.yogurtte.rca.collector.CollectedData data = new com.yogurtte.rca.collector.CollectedData(
                "trace-2", null, null, null, null, java.util.Map.of("trace-2", bigTrace), List.of(), null);

        String context = new ContextAssembler(properties, new ServiceGraphExtractor(), NO_FOLD, TraceCompactProperties.off()).assemble(data, "q");

        assertThat(context).contains("duration 상위 30개 span만");
        // duration 상위 30개는 399번부터 370번까지; 그보다 짧은 span은 전부 버려진다.
        assertThat(context).contains("span-with-a-deliberately-long-name-399");
        assertThat(context).contains("span-with-a-deliberately-long-name-370");
        assertThat(context).doesNotContain("span-with-a-deliberately-long-name-369");
        assertThat(context.length()).isLessThan(bigTrace.length());
    }

    private static long nanos(Instant instant) {
        return instant.getEpochSecond() * 1_000_000_000L + instant.getNano();
    }
}
