package com.yogurtte.rca;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.yogurtte.rca.analyzer.ContextAssembler;
import com.yogurtte.rca.analyzer.LlmClient;
import com.yogurtte.rca.analyzer.LlmResult;
import com.yogurtte.rca.client.GrafanaProperties;
import com.yogurtte.rca.client.LokiClient;
import com.yogurtte.rca.client.MimirClient;
import com.yogurtte.rca.client.RawResponseStore;
import com.yogurtte.rca.client.TempoClient;
import com.yogurtte.rca.collector.CollectProperties;
import com.yogurtte.rca.collector.Collector;
import com.yogurtte.rca.notify.Notifier;
import com.yogurtte.rca.report.RcaReport;
import com.yogurtte.rca.report.ReportProperties;

/**
 * Whole v0 pass with a fake LlmClient: Tempo and Mimir answer, Loki is down.
 * The run must still finish and say so rather than aborting.
 */
class RcaServiceFlowTest {

    /** Records the context it was handed so the test can assert on what the model would see. */
    static class FakeLlmClient implements LlmClient {
        String seenSystemPrompt;
        String seenContext;

        @Override
        public LlmResult analyze(String systemPrompt, String context) {
            this.seenSystemPrompt = systemPrompt;
            this.seenContext = context;
            return new LlmResult("원인 후보 1: Kafka consumer lag", 1234, 567, 42);
        }

        @Override
        public String provider() {
            return "fake";
        }
    }

    static class RecordingNotifier implements Notifier {
        final List<RcaReport> sent = new ArrayList<>();

        @Override
        public void send(RcaReport report) {
            sent.add(report);
        }
    }

    private WireMockServer server;
    private RcaService service;
    private FakeLlmClient llmClient;
    private RecordingNotifier notifier;

    @BeforeEach
    void setUp(@TempDir Path tempDir) {
        server = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        server.start();

        var base = Instant.parse("2026-07-20T10:00:00Z");
        server.stubFor(get(urlPathEqualTo("/api/traces/trace-1"))
                .willReturn(aResponse().withStatus(200).withBody("""
                        {"batches":[{"resource":{"attributes":[
                            {"key":"service.name","value":{"stringValue":"chat"}}]},
                          "scopeSpans":[{"spans":[
                            {"name":"notify","startTimeUnixNano":"%d","endTimeUnixNano":"%d"}]}]}]}
                        """.formatted(nanos(base), nanos(base.plusSeconds(3))))));

        // Loki is unavailable for this run.
        server.stubFor(get(urlPathEqualTo("/loki/api/v1/query_range"))
                .willReturn(aResponse().withStatus(503)));

        server.stubFor(get(urlPathEqualTo("/prometheus/api/v1/query_range"))
                .willReturn(aResponse().withStatus(200).withBody(
                        "{\"status\":\"success\",\"data\":{\"result\":[{\"values\":[[1,\"7\"]]}]}}")));

        var endpoint = new GrafanaProperties.Endpoint("http://localhost:" + server.port(), "1");
        var grafana = new GrafanaProperties(endpoint, endpoint, endpoint, "tok", 3000, 10000);
        var reportProperties = new ReportProperties(tempDir.toString());
        var rawStore = new RawResponseStore(reportProperties);

        var collectProperties = new CollectProperties(120, "content|auth|chat", "app", "level",
                1000, "15s", List.of("hikaricp_connections_active"), 102400, 30);

        var collector = new Collector(
                new TempoClient(grafana, rawStore),
                new LokiClient(grafana, rawStore),
                new MimirClient(grafana, rawStore),
                collectProperties);

        llmClient = new FakeLlmClient();
        notifier = new RecordingNotifier();
        service = new RcaService(collector, new ContextAssembler(collectProperties), llmClient, notifier);
    }

    @AfterEach
    void tearDown() {
        server.stop();
    }

    @Test
    void completesAndReportsTheFailedSourceWhenLokiIsDown() {
        var report = service.investigate("trace-1", "왜 알림이 늦었어?");

        assertThat(report.analysis()).isEqualTo("원인 후보 1: Kafka consumer lag");
        assertThat(report.llmProvider()).isEqualTo("fake");
        assertThat(report.inputTokens()).isEqualTo(1234);
        assertThat(report.outputTokens()).isEqualTo(567);
        assertThat(report.timings().llmMs()).isEqualTo(42);
        assertThat(report.totalElapsedMs()).isGreaterThanOrEqualTo(0);

        // Both Loki queries failed, and the context tells the model so instead of hiding it.
        assertThat(report.collectionFailures()).hasSize(2);
        assertThat(report.collectionFailures()).allMatch(failure -> failure.startsWith("Loki"));
        assertThat(llmClient.seenContext).contains("# 수집 실패/누락").contains("Loki");

        // Sources that did work are present.
        assertThat(llmClient.seenContext).contains("notify").contains("hikaricp_connections_active");
        assertThat(llmClient.seenSystemPrompt).contains("너는 SRE다");

        assertThat(notifier.sent).hasSize(1);
        assertThat(notifier.sent.get(0)).isSameAs(report);
    }

    @Test
    void trimsAnOversizedTraceToTheLongestSpans() {
        var base = Instant.parse("2026-07-20T10:00:00Z");
        var spans = new StringBuilder();
        for (int i = 0; i < 400; i++) {
            spans.append(i == 0 ? "" : ",");
            spans.append("{\"name\":\"span-with-a-deliberately-long-name-%d\",\"startTimeUnixNano\":\"%d\",\"endTimeUnixNano\":\"%d\"}"
                    .formatted(i, nanos(base), nanos(base.plusSeconds(i + 1))));
        }
        var bigTrace = """
                {"batches":[{"resource":{"attributes":[
                    {"key":"service.name","value":{"stringValue":"content"}}]},
                  "scopeSpans":[{"spans":[%s]}]}]}
                """.formatted(spans);

        var properties = new CollectProperties(120, "content|auth|chat", "app", "level",
                1000, "15s", List.of(), 100, 30);  // 100-byte cap forces the trim
        var data = new com.yogurtte.rca.collector.CollectedData(
                "trace-2", bigTrace, null, null, null, null, List.of(), null);

        var context = new ContextAssembler(properties).assemble(data, "q");

        assertThat(context).contains("duration 상위 30개 span만");
        // Top 30 by duration is spans 399 down to 370; everything shorter is dropped.
        assertThat(context).contains("span-with-a-deliberately-long-name-399");
        assertThat(context).contains("span-with-a-deliberately-long-name-370");
        assertThat(context).doesNotContain("span-with-a-deliberately-long-name-369");
        assertThat(context.length()).isLessThan(bigTrace.length());
    }

    private static long nanos(Instant instant) {
        return instant.getEpochSecond() * 1_000_000_000L + instant.getNano();
    }
}
