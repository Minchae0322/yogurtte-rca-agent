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
import com.yogurtte.rca.analyzer.PromptProperties;
import com.yogurtte.rca.analyzer.SystemPromptLoader;
import com.yogurtte.rca.client.GrafanaProperties;
import com.yogurtte.rca.client.LokiClient;
import com.yogurtte.rca.client.MimirClient;
import com.yogurtte.rca.client.RawResponseStore;
import com.yogurtte.rca.client.TempoClient;
import com.yogurtte.rca.collector.CollectProperties;
import com.yogurtte.rca.collector.Collector;
import com.yogurtte.rca.llm.LlmClient;
import com.yogurtte.rca.llm.LlmProperties;
import com.yogurtte.rca.llm.LlmResult;
import com.yogurtte.rca.llm.TokenCounter;
import com.yogurtte.rca.notify.Notifier;
import com.yogurtte.rca.report.RcaReport;
import com.yogurtte.rca.report.ReportProperties;
import com.yogurtte.rca.service.RcaService;

/**
 * fake LlmClient로 v0 전체 흐름을 돈다: Tempo와 Mimir는 응답하고 Loki는 죽어 있다.
 * 그래도 실행은 중단 없이 완주하고, 그 사실을 결과에 남겨야 한다.
 */
class RcaServiceFlowTest {

    /** 건네받은 컨텍스트를 기록해서, 모델이 보게 될 내용을 테스트가 검증할 수 있게 한다. */
    static class FakeLlmClient implements LlmClient {
        String seenSystemPrompt;
        String seenContext;

        @Override
        public LlmResult analyze(String systemPrompt, String context) {
            this.seenSystemPrompt = systemPrompt;
            this.seenContext = context;
            return new LlmResult("원인 후보 1: Kafka consumer lag", 1234, 567, 900, 300,
                    "fake-model", 1, 42, 0.0123);
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

        @Override
        public String channel() {
            return "recording";
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

        // 이 실행에서 Loki는 죽어 있다.
        server.stubFor(get(urlPathEqualTo("/loki/api/v1/query_range"))
                .willReturn(aResponse().withStatus(503)));

        server.stubFor(get(urlPathEqualTo("/api/v1/query_range"))
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
        // 외부 프롬프트 경로 미설정 -> classpath 기본 프롬프트를 쓴다.
        var promptLoader = new SystemPromptLoader(new PromptProperties(null));
        // API 키 없는 LlmProperties -> TokenCounter가 비활성이라 contextTokens는 -1이 된다.
        var tokenCounter = new TokenCounter(new LlmProperties("fake", null, null, null));
        service = new RcaService(collector, new ContextAssembler(collectProperties), promptLoader,
                collectProperties, llmClient, tokenCounter, notifier);
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
        var cov = report.coverage();
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
                1000, "15s", List.of(), 100, 30);  // 100 바이트 한도로 트리밍을 강제한다
        var data = new com.yogurtte.rca.collector.CollectedData(
                "trace-2", bigTrace, null, null, null, null, List.of(), null);

        var context = new ContextAssembler(properties).assemble(data, "q");

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
