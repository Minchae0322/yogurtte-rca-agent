package com.yogurtte.rca.triage;

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
import com.yogurtte.rca.analyzer.EvidenceExtractor;
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
import com.yogurtte.rca.report.Evidence;
import com.yogurtte.rca.report.RcaReport;
import com.yogurtte.rca.report.ReportProperties;
import com.yogurtte.rca.service.RcaService;

/**
 * 자연어 한 줄로 시작하는 전체 흐름: 창 파싱 → 스윕 → 선정 → 심층 수집 → 분석.
 *
 * <p>핵심 검증 둘이다. ① 탐색이 좁힌 창이 실제로 심층 수집에 쓰이는가.
 * ② <b>traceId가 없어도 파이프라인이 끊기지 않는가</b> — 컨슈머 전멸·파드 부재처럼 이상
 * 트레이스가 아예 생성되지 않는 장애가 실재하므로, 여기서 끊기면 그 문항들은 원리적으로 못 푼다.
 */
class TriageFlowTest {

    /** 탐색과 분석 두 번 불린다. 시스템 프롬프트로 어느 단계인지 구별해 다른 답을 준다. */
    static class ScriptedLlmClient implements LlmClient {
        final List<String> seenPrompts = new ArrayList<>();
        final List<String> seenContexts = new ArrayList<>();
        String triageAnswer = "";

        @Override
        public LlmResult analyze(String systemPrompt, String context) {
            seenPrompts.add(systemPrompt);
            seenContexts.add(context);
            var isTriage = systemPrompt.contains("집계 데이터");
            var text = isTriage ? triageAnswer : "원인 후보 1: MongoDB 다운으로 알림 저장 실패";
            return new LlmResult(text, 100, 50, -1, -1, "fake-model", 1, 10, 0.001);
        }

        @Override
        public String provider() {
            return "fake";
        }

        String triageContext() {
            return seenContexts.get(0);
        }

        String analysisContext() {
            return seenContexts.get(1);
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

    /** 2026-07-28T05:00Z = 14:00 KST → "어젯밤"은 07-27T09:00Z ~ 07-27T21:00Z. */
    private static final Instant NOW = Instant.parse("2026-07-28T05:00:00Z");

    private WireMockServer server;
    private TriageService triageService;
    private ScriptedLlmClient llmClient;
    private RecordingNotifier notifier;

    @BeforeEach
    void setUp(@TempDir Path tempDir) {
        server = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        server.start();

        var base = Instant.parse("2026-07-27T17:31:00Z");

        server.stubFor(get(urlPathEqualTo("/api/search"))
                .willReturn(aResponse().withStatus(200).withBody(
                        "{\"traces\":[{\"traceID\":\"abc123\",\"rootServiceName\":\"chat-service\","
                                + "\"rootTraceName\":\"notification-consume\",\"durationMs\":30123},"
                                + "{\"traceID\":\"def456\",\"rootServiceName\":\"content-service\","
                                + "\"rootTraceName\":\"POST /comments\",\"durationMs\":140}]}")));

        // 스윕은 집계(step 있음), 심층은 원본 라인(direction 있음) — 같은 엔드포인트지만 응답 모양이 다르다.
        server.stubFor(get(urlPathEqualTo("/loki/api/v1/query_range"))
                .withQueryParam("step", com.github.tomakehurst.wiremock.client.WireMock.matching(".+"))
                .willReturn(aResponse().withStatus(200).withBody(
                        "{\"status\":\"success\",\"data\":{\"resultType\":\"matrix\",\"result\":"
                                + "[{\"metric\":{\"service_name\":\"chat-service\"},\"values\":[[1785000000,\"42\"]]}]}}")));
        server.stubFor(get(urlPathEqualTo("/loki/api/v1/query_range"))
                .withQueryParam("direction", com.github.tomakehurst.wiremock.client.WireMock.equalTo("forward"))
                .willReturn(aResponse().withStatus(200).withBody("""
                        {"status":"success","data":{"resultType":"streams","result":[
                          {"stream":{"service_name":"chat-service"},
                           "values":[["%d","ERROR MongoTimeoutException: server selection timed out after 30000 ms"]]}]}}
                        """.formatted(nanos(base)))));

        // mongodb_up이 17:31~17:32 두 스텝 동안 0으로 꺾인다 — 부재가 신호인 경우의 대표형.
        server.stubFor(get(urlPathEqualTo("/api/v1/query_range"))
                .willReturn(aResponse().withStatus(200).withBody("""
                        {"status":"success","data":{"resultType":"matrix","result":[
                          {"metric":{"__name__":"mongodb_up","instance":"mongo-0"},
                           "values":[[%d,"1"],[%d,"0"],[%d,"0"],[%d,"1"]]}]}}
                        """.formatted(
                        base.minusSeconds(60).getEpochSecond(), base.getEpochSecond(),
                        base.plusSeconds(60).getEpochSecond(), base.plusSeconds(120).getEpochSecond()))));

        server.stubFor(get(urlPathEqualTo("/api/traces/abc123"))
                .willReturn(aResponse().withStatus(200).withBody("""
                        {"batches":[{"resource":{"attributes":[
                            {"key":"service.name","value":{"stringValue":"chat-service"}}]},
                          "scopeSpans":[{"spans":[
                            {"name":"notification-consume","startTimeUnixNano":"%d","endTimeUnixNano":"%d"}]}]}]}
                        """.formatted(nanos(base), nanos(base.plusSeconds(30))))));

        var endpoint = new GrafanaProperties.Endpoint("http://localhost:" + server.port(), "1");
        var grafana = new GrafanaProperties(endpoint, endpoint, endpoint, "tok", 3000, 10000);
        var rawStore = new RawResponseStore(new ReportProperties(tempDir.toString()));

        var tempoClient = new TempoClient(grafana, rawStore);
        var lokiClient = new LokiClient(grafana, rawStore);
        var mimirClient = new MimirClient(grafana, rawStore);

        var collectProperties = new CollectProperties(120, "content-service|auth-service|chat-service",
                "service_name", 1000, "15s", List.of("mongodb_up"), 102400, 30);
        var surveyProperties = new SurveyProperties("Asia/Seoul", 24, 48, "5m",
                "{ status = error }", 20, null, List.of("up", "mongodb_up"));

        llmClient = new ScriptedLlmClient();
        notifier = new RecordingNotifier();
        var promptLoader = new SystemPromptLoader(new PromptProperties(null));
        var tokenCounter = new TokenCounter(new LlmProperties("fake", null, null, null));

        var rcaService = new RcaService(
                new Collector(tempoClient, lokiClient, mimirClient, collectProperties),
                new ContextAssembler(collectProperties), new EvidenceExtractor(), promptLoader,
                collectProperties, llmClient, tokenCounter, notifier);

        triageService = new TriageService(
                new TimeExpressionParser(surveyProperties),
                new Surveyor(tempoClient, lokiClient, mimirClient, surveyProperties, collectProperties),
                new SurveyContextAssembler(), promptLoader, llmClient, rcaService);
    }

    @AfterEach
    void tearDown() {
        server.stop();
    }

    @Test
    void 자연어_한_줄로_스윕부터_분석까지_완주한다() {
        llmClient.triageAnswer = """
                ## 2. 계획
                ```json
                {"windowStart":"2026-07-27T17:29:00Z","windowEnd":"2026-07-27T17:40:00Z",
                 "services":["chat-service"],"traceId":"abc123",
                 "evidence":["mongodb_up이 0으로 꺾임"],"reason":"알림 저장 실패 구간"}
                ```
                """;

        var report = triageService.diagnose("어젯밤에 댓글 알림이 안 왔어요", null, null, "rca", NOW);

        // 탐색 기록이 분석과 분리되어 남는다.
        var triage = report.triage();
        assertThat(triage).isNotNull();
        assertThat(triage.timeExpression()).contains("어젯밤");
        assertThat(triage.surveyStart()).isEqualTo(Instant.parse("2026-07-27T09:00:00Z"));
        assertThat(triage.surveyEnd()).isEqualTo(Instant.parse("2026-07-27T21:00:00Z"));
        assertThat(triage.planParsed()).isTrue();
        assertThat(triage.services()).containsExactly("chat-service");
        assertThat(triage.evidence()).containsExactly("mongodb_up이 0으로 꺾임");

        // 스윕은 12시간 창을 봤고, 분석은 좁힌 11분 창을 봤다.
        assertThat(llmClient.triageContext()).contains("2026-07-27T09:00:00Z");
        assertThat(llmClient.analysisContext()).contains("2026-07-27T17:29:00Z");
        assertThat(llmClient.analysisContext()).contains("notification-consume");

        // 스윕은 집계, 분석은 원본 — 서로 다른 쿼리를 쓴다.
        server.verify(1, com.github.tomakehurst.wiremock.client.WireMock
                .getRequestedFor(urlPathEqualTo("/api/search")));
        server.verify(1, com.github.tomakehurst.wiremock.client.WireMock
                .getRequestedFor(urlPathEqualTo("/api/traces/abc123")));

        assertThat(report.traceId()).isEqualTo("abc123");
        assertThat(report.analysis()).startsWith("원인 후보 1");
        assertThat(notifier.sent).hasSize(1);

        // 스윕이 찾은 후보는 고른 것 말고도 전부 남는다 — 회고에서 "다른 걸 골랐어야 했나"의 근거다.
        assertThat(triage.traceCandidates()).hasSize(2);
        assertThat(triage.traceCandidates()).extracting(Evidence.TraceHit::traceId)
                .containsExactly("abc123", "def456");
    }

    @Test
    void 리포트에_실제_관측값이_함께_남는다() {
        llmClient.triageAnswer = """
                ```json
                {"windowStart":"2026-07-27T17:29:00Z","windowEnd":"2026-07-27T17:40:00Z",
                 "services":["chat-service"],"traceId":"abc123","evidence":[],"reason":"x"}
                ```
                """;

        var report = triageService.diagnose("어젯밤에 댓글 알림이 안 왔어요", null, null, "rca", NOW);
        var e = report.evidence();

        assertThat(e).isNotNull();
        assertThat(e.investigatedTraceId()).isEqualTo("abc123");

        // span: 이름·소요·시작 시각이 그대로 남는다.
        assertThat(e.spanCount()).isEqualTo(1);
        assertThat(e.topSpans()).singleElement().satisfies(span -> {
            assertThat(span.name()).isEqualTo("notification-consume");
            assertThat(span.service()).isEqualTo("chat-service");
            assertThat(span.durationMs()).isEqualTo(30_000.0);
            assertThat(span.startedAt()).isEqualTo(Instant.parse("2026-07-27T17:31:00Z"));
        });

        // 로그: 요약이 아니라 원문 그대로.
        assertThat(e.logSamples()).isNotEmpty();
        assertThat(e.logSamples()).anyMatch(l -> l.line().contains("MongoTimeoutException")
                && l.service().equals("chat-service"));

        // 메트릭: 값의 범위와 0이던 구간이 남는다 — 부재가 결정적 신호인 장애가 있다.
        assertThat(e.metrics()).singleElement().satisfies(m -> {
            assertThat(m.query()).isEqualTo("mongodb_up");
            assertThat(m.series()).contains("instance=mongo-0");
            assertThat(m.points()).isEqualTo(4);
            assertThat(m.min()).isEqualTo(0.0);
            assertThat(m.max()).isEqualTo(1.0);
            assertThat(m.last()).isEqualTo(1.0);
            assertThat(m.zeroSpans()).containsExactly(
                    "2026-07-27T17:31:00Z ~ 2026-07-27T17:32:00Z");
        });

        // 원본 응답으로 되짚어갈 경로가 함께 남는다.
        assertThat(e.rawPrefix()).isEqualTo("abc123");
    }

    @Test
    void traceId가_없어도_창과_대상만으로_분석까지_간다() {
        // 컨슈머가 죽거나 파드가 0이면 이상 트레이스 자체가 만들어지지 않는다 (CH-2·AU-2).
        llmClient.triageAnswer = """
                ```json
                {"windowStart":"2026-07-27T17:29:00Z","windowEnd":"2026-07-27T17:40:00Z",
                 "services":["chat-service"],"traceId":null,
                 "evidence":["up이 0으로 끊김"],"reason":"파드 부재 구간"}
                ```
                """;

        var report = triageService.diagnose("어젯밤에 댓글 알림이 안 왔어요", null, null, "rca", NOW);

        assertThat(report.traceId()).isNull();
        assertThat(report.triage().traceId()).isNull();
        assertThat(report.analysis()).startsWith("원인 후보 1");

        // 트레이스 조회는 아예 하지 않고, 그 부재를 모델에게 명시한다.
        server.verify(0, com.github.tomakehurst.wiremock.client.WireMock
                .getRequestedFor(urlPathEqualTo("/api/traces/abc123")));
        assertThat(report.collectionFailures()).anyMatch(f -> f.contains("대표 traceId가 없다"));
        assertThat(llmClient.analysisContext()).contains("대표 traceId가 없다");
    }

    @Test
    void 계획을_못_읽으면_스윕_창_전체로_분석하고_그_사실을_남긴다() {
        llmClient.triageAnswer = "이상 없는 것 같습니다.";

        var report = triageService.diagnose("어젯밤에 댓글 알림이 안 왔어요", null, null, "rca", NOW);

        assertThat(report.triage().planParsed()).isFalse();
        assertThat(report.triage().chosenStart()).isEqualTo(Instant.parse("2026-07-27T09:00:00Z"));
        assertThat(report.triage().notes()).anyMatch(note -> note.contains("찾지 못해"));
        assertThat(report.analysis()).startsWith("원인 후보 1");
    }

    private static long nanos(Instant instant) {
        return instant.getEpochSecond() * 1_000_000_000L + instant.getNano();
    }
}
