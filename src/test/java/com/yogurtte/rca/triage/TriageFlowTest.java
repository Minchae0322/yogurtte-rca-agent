package com.yogurtte.rca.triage;

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
import com.yogurtte.rca.analyzer.PromptProperties;
import com.yogurtte.rca.analyzer.SystemPromptLoader;
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
import com.yogurtte.rca.report.Evidence;
import com.yogurtte.rca.report.RcaReport;
import com.yogurtte.rca.report.ReportProperties;
import com.yogurtte.rca.service.RcaService;
import com.yogurtte.rca.support.RecordingNotifier;
import com.yogurtte.rca.support.ScriptedLlmClient;
import com.yogurtte.rca.triage.plan.SurveyContextAssembler;
import com.yogurtte.rca.triage.survey.Surveyor;

/**
 * 자연어 한 줄로 시작하는 전체 흐름: 창 파싱 → 스윕 → 선정 → 심층 수집 → 분석.
 *
 * <p>핵심 검증 둘이다. ① 탐색이 좁힌 창이 실제로 심층 수집에 쓰이는가.
 * ② <b>traceId가 없어도 파이프라인이 끊기지 않는가</b> — 컨슈머 전멸·파드 부재처럼 이상
 * 트레이스가 아예 생성되지 않는 장애가 실재하므로, 여기서 끊기면 그 문항들은 원리적으로 못 푼다.
 */
class TriageFlowTest {

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

        Instant base = Instant.parse("2026-07-27T17:31:00Z");

        // 에러 채널과 지연 채널을 따로 던진다. 어느 채널로 도달했는지가 후보에 남아야 한다.
        // 에러 채널: 정상 행 하나 + 깨진 행 하나(durationMs 33일 · startTime 0) — Tempo 실측 사례.
        server.stubFor(get(urlPathEqualTo("/api/search"))
                .withQueryParam("q", com.github.tomakehurst.wiremock.client.WireMock
                        .containing("status = error"))
                .willReturn(aResponse().withStatus(200).withBody("""
                        {"traces":[
                          {"traceID":"abc123","rootServiceName":"chat-service",
                           "rootTraceName":"notification-consume","durationMs":30123,
                           "startTimeUnixNano":"%d"},
                          {"traceID":"broken1","rootServiceName":"chat-service",
                           "rootTraceName":"?","durationMs":2851200000,
                           "startTimeUnixNano":"0"}]}
                        """.formatted(nanos(base)))));
        // 지연 채널: 200 성공인데 느린 것 — 에러 검색에는 원리적으로 안 걸리는 형태다.
        server.stubFor(get(urlPathEqualTo("/api/search"))
                .withQueryParam("q", com.github.tomakehurst.wiremock.client.WireMock
                        .containing("duration >"))
                .willReturn(aResponse().withStatus(200).withBody("""
                        {"traces":[
                          {"traceID":"slow789","rootServiceName":"content-service",
                           "rootTraceName":"POST /comments","durationMs":23458,
                           "startTimeUnixNano":"%d"}]}
                        """.formatted(nanos(base.plusSeconds(600))))));
        // B-9 후보 채널(무조건 검색): 기본은 빈 결과 — 후보는 스윕이 넘긴 것만 남는다.
        server.stubFor(get(urlPathEqualTo("/api/search"))
                .withQueryParam("q", com.github.tomakehurst.wiremock.client.WireMock.equalTo("{}"))
                .willReturn(aResponse().withStatus(200).withBody("{\"traces\":[]}")));

        // 스윕은 집계(step 있음), 심층은 원본 라인(direction 있음) — 같은 엔드포인트지만 응답 모양이 다르다.
        server.stubFor(get(urlPathEqualTo("/loki/api/v1/query_range"))
                .withQueryParam("step", com.github.tomakehurst.wiremock.client.WireMock.matching(".+"))
                .willReturn(aResponse().withStatus(200).withBody(
                        // 버킷 시각은 창 안이어야 한다 — 신호 구간이 [ts-5m, ts] 로 잡히고
                        // 그것이 그대로 조사 창 계산에 쓰인다.
                        "{\"status\":\"success\",\"data\":{\"resultType\":\"matrix\",\"result\":"
                                + "[{\"metric\":{\"service_name\":\"chat-service\"},\"values\":[["
                                + base.getEpochSecond() + ",\"42\"]]}]}}")));
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

        GrafanaProperties.Endpoint endpoint = new GrafanaProperties.Endpoint("http://localhost:" + server.port(), "1");
        GrafanaProperties grafana = new GrafanaProperties(endpoint, endpoint, endpoint, "tok", 3000, 10000);
        RawResponseStore rawStore = new GrafanaConfig().rawResponseStore(new ReportProperties(tempDir.toString()));

        TempoClient tempoClient = new GrafanaConfig().tempoClient(grafana, rawStore);
        LokiClient lokiClient = new GrafanaConfig().lokiClient(grafana, rawStore);
        MimirClient mimirClient = new GrafanaConfig().mimirClient(grafana, rawStore);

        CollectProperties collectProperties = new CollectProperties(120, "content-service|auth-service|chat-service",
                "service_name", 1000, "15s", List.of("mongodb_up"), 102400, 30, 0);
        SurveyProperties surveyProperties = new SurveyProperties("Asia/Seoul", 24, 48, "5m",
                "{ status = error }", "{ duration > %s && status != error }", "3s",
                20, null, List.of("up", "mongodb_up"), "60s", "2m", "5m", true);
        // max-traces 0 = 상한 없음. 운영 기본값과 같은 조건으로 흐름을 검증한다.

        llmClient = new ScriptedLlmClient();
        notifier = new RecordingNotifier();
        SystemPromptLoader promptLoader = SystemPromptLoader.from(new PromptProperties(null));
        TokenCounter tokenCounter = new LlmConfig().tokenCounter(new LlmProperties("fake", null, null, null));

        com.yogurtte.rca.analyzer.ServiceGraphExtractor graphExtractor = new com.yogurtte.rca.analyzer.ServiceGraphExtractor();
        RcaService rcaService = new RcaService(
                new Collector(tempoClient, lokiClient, mimirClient, collectProperties),
                new ContextAssembler(collectProperties, graphExtractor), new EvidenceExtractor(),
                graphExtractor, promptLoader,
                collectProperties, llmClient, tokenCounter, notifier);

        triageService = new TriageService(
                new TriageConfig().timeExpressionParser(surveyProperties),
                new Surveyor(tempoClient, lokiClient, mimirClient, surveyProperties, collectProperties),
                new SurveyContextAssembler(),
                surveyProperties, promptLoader, llmClient, rcaService);
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

        RcaReport report = triageService.diagnose("어젯밤에 댓글 알림이 안 왔어요", null, null, "rca", NOW);

        // 탐색 기록이 분석과 분리되어 남는다.
        RcaReport.Triage triage = report.triage();
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

        // 트레이스 검색은 세 번 — 에러·지연 채널(스윕)에 후보 무조건 검색(B-9)이 더해진다.
        server.verify(3, com.github.tomakehurst.wiremock.client.WireMock
                .getRequestedFor(urlPathEqualTo("/api/search")));
        server.verify(1, com.github.tomakehurst.wiremock.client.WireMock
                .getRequestedFor(urlPathEqualTo("/api/traces/abc123")));

        assertThat(report.traceId()).isEqualTo("abc123");
        assertThat(report.analysis()).startsWith("원인 후보 1");
        assertThat(notifier.sent).hasSize(1);

        // 스윕이 찾은 후보는 고른 것 말고도 전부 남는다 — 회고에서 "다른 걸 골랐어야 했나"의 근거다.
        // 두 채널이 병합되고, 깨진 행도 버리지 않고 표기만 한다(버리면 그 트레이스에 도달할 길이 없다).
        assertThat(triage.traceCandidates()).extracting(Evidence.TraceHit::traceId)
                .containsExactly("abc123", "broken1", "slow789");
        assertThat(triage.traceCandidates()).extracting(Evidence.TraceHit::channel)
                .containsExactly("error", "error", "slow");
        assertThat(triage.traceCandidates()).extracting(Evidence.TraceHit::trusted)
                .containsExactly(true, false, true);
    }

    @Test
    void 후보를_고르면_창을_모델이_아니라_신호_시각에서_계산한다() {
        // 모델은 어느 후보인지만 고른다. windowStart/windowEnd 를 아예 쓰지 않는다.
        llmClient.triageAnswer = """
                ```json
                {"incidentIds":["INC-1"],"services":[],
                 "evidence":["mongodb_up이 0으로 꺾임"],"reason":"알림 저장 실패 구간",
                 "dismissed":[{"incidentId":"INC-2","why":"증상과 시각이 다르다"}]}
                ```
                """;

        RcaReport report = triageService.diagnose("어젯밤에 댓글 알림이 안 왔어요", null, null, "rca", NOW);
        RcaReport.Triage triage = report.triage();

        assertThat(triage.planParsed()).isTrue();
        assertThat(triage.chosenIncidentIds()).containsExactly("INC-1");
        assertThat(triage.dismissedIncidentIds()).anyMatch(d -> d.startsWith("INC-2"));
        assertThat(triage.incidentCandidates()).isNotEmpty();

        // 창이 스윕 창(12시간)보다 좁고, 후보의 신호 구간을 덮는다.
        assertThat(triage.chosenStart()).isAfter(triage.surveyStart());
        assertThat(triage.chosenEnd()).isBefore(triage.surveyEnd());
        assertThat(triage.notes()).anyMatch(n -> n.contains("신호 시각에서 계산했다"));

        assertThat(report.analysis()).startsWith("원인 후보 1");
    }

    @Test
    void 후보를_둘_고르면_로그는_창별로_나눠_조회하고_합쳐서_넘긴다() {
        // 합집합 창 하나로 긁으면 후보 사이의 빈 구간까지 딸려 온다. 로그는 점 사건이라
        // 그 구간에 정보가 없다 — 메트릭은 반대라 합집합 창을 그대로 쓴다.
        llmClient.triageAnswer = """
                ```json
                {"incidentIds":["INC-1","INC-2"],"services":[],
                 "evidence":["두 구간 모두 의심"],"reason":"확신이 없어 둘 다 고른다",
                 "dismissed":[]}
                ```
                """;

        RcaReport report = triageService.diagnose("어젯밤에 댓글 알림이 안 왔어요", null, null, "rca", NOW);

        assertThat(report.triage().chosenIncidentIds()).containsExactly("INC-1", "INC-2");

        // 심층 로그 조회(direction 있음)가 창마다 한 번씩 — 그리고 그 창들이 서로 달라야 한다.
        // 횟수만 세면 "한 창을 두 채널로 조회한 것"과 구별이 안 된다.
        List<String> deepLogRanges = server.findAll(com.github.tomakehurst.wiremock.client.WireMock
                        .getRequestedFor(urlPathEqualTo("/loki/api/v1/query_range"))
                        .withQueryParam("direction",
                                com.github.tomakehurst.wiremock.client.WireMock.equalTo("forward")))
                .stream()
                .map(r -> r.queryParameter("start").firstValue() + "~" + r.queryParameter("end").firstValue())
                .distinct()
                .toList();
        assertThat(deepLogRanges).hasSize(2);

        // 메트릭은 나누지 않는다 — 시계열이 조각나면 "그 사이에 회복했는가"를 잃는다.
        // 스윕 2회(up · mongodb_up) + 심층 1회 = 3. 창별로 나눴다면 심층이 2회가 되어 4다.
        server.verify(3, com.github.tomakehurst.wiremock.client.WireMock
                .getRequestedFor(urlPathEqualTo("/api/v1/query_range")));

        // 합쳐서 넘어가므로 하류(dedup·evidence·assembler)는 창을 몰라도 된다.
        assertThat(llmClient.analysisContext()).contains("MongoTimeoutException");
        assertThat(report.analysis()).startsWith("원인 후보 1");
    }

    @Test
    void 지연_채널만_걸린_장애도_후보로_올라온다() {
        // 200 성공 + 지연은 에러 검색에 원리적으로 안 걸린다. 그 형태가 후보에 남아야 한다.
        llmClient.triageAnswer = "이상 없는 것 같습니다.";

        triageService.diagnose("어젯밤에 앱이 잠깐 버벅였어요", null, null, "rca", NOW);

        assertThat(llmClient.triageContext()).contains("지연 트레이스 검색");
        assertThat(llmClient.triageContext()).contains("slow789");
        assertThat(llmClient.triageContext()).contains("장애 후보");
    }

    @Test
    void 리포트에_실제_관측값이_함께_남는다() {
        llmClient.triageAnswer = """
                ```json
                {"windowStart":"2026-07-27T17:29:00Z","windowEnd":"2026-07-27T17:40:00Z",
                 "services":["chat-service"],"traceId":"abc123","evidence":[],"reason":"x"}
                ```
                """;

        RcaReport report = triageService.diagnose("어젯밤에 댓글 알림이 안 왔어요", null, null, "rca", NOW);
        Evidence e = report.evidence();

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
    void 상한이_없으면_창_안_트레이스를_세_건_넘게_다_가져온다() {
        // 상한 3이던 시절에는 "대표 1 + 후보 2"가 전부라, 정답이 4번째면 실리지 않았다.
        // 순서에 의미가 없으므로(신호가 만들어진 순서일 뿐) 밀린 것이 정답일 수 있다.
        server.stubFor(get(urlPathEqualTo("/api/search"))
                .withQueryParam("q", com.github.tomakehurst.wiremock.client.WireMock.equalTo("{}"))
                .willReturn(aResponse().withStatus(200).withBody("""
                        {"traces":[{"traceID":"cand1"},{"traceID":"cand2"},{"traceID":"cand3"},
                                   {"traceID":"cand4"},{"traceID":"cand5"}]}
                        """)));
        Instant traceAt = Instant.parse("2026-07-27T17:31:00Z");
        for (String id : List.of("cand1", "cand2", "cand3", "cand4", "cand5")) {
            server.stubFor(get(urlPathEqualTo("/api/traces/" + id))
                    .willReturn(aResponse().withStatus(200).withBody("""
                            {"batches":[{"resource":{"attributes":[
                                {"key":"service.name","value":{"stringValue":"chat-service"}}]},
                              "scopeSpans":[{"spans":[{"name":"span-%s","startTimeUnixNano":"%d",
                                "endTimeUnixNano":"%d"}]}]}]}
                            """.formatted(id, nanos(traceAt), nanos(traceAt.plusSeconds(1))))));
        }

        RcaReport report = triageService.diagnose("어젯밤에 댓글 알림이 안 왔어요", null, null, "rca", NOW);

        assertThat(report.analysis()).startsWith("원인 후보 1");
        // 5건 전부 딥 페치된다 — 구 상한(3)이면 2건에서 끊겼다.
        for (String id : List.of("cand1", "cand2", "cand3", "cand4", "cand5")) {
            server.verify(1, com.github.tomakehurst.wiremock.client.WireMock
                    .getRequestedFor(urlPathEqualTo("/api/traces/" + id)));
            assertThat(llmClient.analysisContext()).contains("span-" + id);
        }
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

        RcaReport report = triageService.diagnose("어젯밤에 댓글 알림이 안 왔어요", null, null, "rca", NOW);

        assertThat(report.traceId()).isNull();
        assertThat(report.triage().traceId()).isNull();
        assertThat(report.analysis()).startsWith("원인 후보 1");

        // 대표 트레이스의 부재는 모델에게 명시하되, 창 안 후보(B-9)는 실린다 —
        // "트레이스가 안 만들어지는 장애"에서도 같은 창의 다른 서비스 트레이스가 근거가 된다 (CH-2·AU-2).
        server.verify(1, com.github.tomakehurst.wiremock.client.WireMock
                .getRequestedFor(urlPathEqualTo("/api/traces/abc123")));
        assertThat(report.collectionFailures()).anyMatch(f -> f.contains("대표 traceId가 없다"));
        assertThat(llmClient.analysisContext()).contains("대표 traceId가 없다");
        assertThat(llmClient.analysisContext()).contains("창 안 후보 트레이스").contains("notification-consume");
    }

    @Test
    void 계획을_못_읽으면_신호가_가장_많은_후보로_떨어지고_그_사실을_남긴다() {
        // 스윕 창 전체로 떨어지면 비싸다. 후보가 있으면 그중 가장 신호가 많은 것을 쓰고,
        // 나머지는 기록에 남아 되돌아갈 수 있다.
        llmClient.triageAnswer = "이상 없는 것 같습니다.";

        RcaReport report = triageService.diagnose("어젯밤에 댓글 알림이 안 왔어요", null, null, "rca", NOW);
        RcaReport.Triage triage = report.triage();

        assertThat(triage.planParsed()).isFalse();
        assertThat(triage.notes()).anyMatch(note -> note.contains("찾지 못해"));
        assertThat(triage.notes()).anyMatch(note -> note.contains("신호가 가장 많은 후보"));

        // 스윕 창 전체가 아니다.
        assertThat(triage.chosenStart()).isAfter(Instant.parse("2026-07-27T09:00:00Z"));
        assertThat(triage.incidentCandidates()).isNotEmpty();
        assertThat(report.analysis()).startsWith("원인 후보 1");
    }

    private static long nanos(Instant instant) {
        return instant.getEpochSecond() * 1_000_000_000L + instant.getNano();
    }
}
