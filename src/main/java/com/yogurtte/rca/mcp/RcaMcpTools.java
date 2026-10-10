package com.yogurtte.rca.mcp;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.slf4j.MDC;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import com.yogurtte.rca.analyzer.ContextAssembler;
import com.yogurtte.rca.collector.CollectProperties;
import com.yogurtte.rca.collector.CollectedData;
import com.yogurtte.rca.collector.Collector;
import com.yogurtte.rca.collector.Scope;
import com.yogurtte.rca.collector.TimeWindow;
import com.yogurtte.rca.report.Evidence;
import com.yogurtte.rca.triage.SurveyProperties;
import com.yogurtte.rca.triage.incident.Incident;
import com.yogurtte.rca.triage.incident.Signal;
import com.yogurtte.rca.triage.incident.SignalExtractor;
import com.yogurtte.rca.triage.plan.SurveyContextAssembler;
import com.yogurtte.rca.triage.plan.TriagePlan;
import com.yogurtte.rca.triage.survey.SurveyResult;
import com.yogurtte.rca.triage.survey.Surveyor;
import com.yogurtte.rca.triage.window.TimeExpressionParser;

/**
 * 수집·정제 계층을 MCP 툴로 노출한다. <b>LLM을 부르지 않는다</b> - 루프는 호출자(MCP
 * 클라이언트)가 돈다.
 *
 * <pre>
 *   지금 (측정 경로)   스프링이 LLM을 부른다   POST /diagnose -> 탐색 LLM -> 분석 LLM -> 리포트
 *   이 경로           LLM이 스프링을 부른다   surveyContext -> (모델이 고름) -> analysisContext
 * </pre>
 *
 * <p><b>데이터는 두 경로가 바이트 단위로 같다.</b> 같은 {@link Surveyor}·
 * {@link SignalExtractor}·{@link Incident#cluster}·{@link Collector}·{@link ContextAssembler}
 * 빈을 쓴다. 그래서 사건 단위 묶기(3축 + 60초)와 로그 접기(B-34 - 컨텍스트 -43.9%)가
 * 이 경로에서도 그대로 걸린다. 다른 것은 <b>그 데이터를 몇 번 얼마나 가져가는지</b>뿐이고,
 * 그래서 토큰 축만 깨진다({@code num_turns > 1} - CLAUDE.md §2.4).
 *
 * <p><b>경계를 툴 시그니처로 지킨다</b>(전체구조.md §9). 모델이 넘길 수 있는 것은
 * <b>질문 문안과 후보 번호</b>뿐이다 - 시간창·서비스·traceId·쿼리·상한은 전부 코드가 정한다.
 * 파라미터로 창이나 쿼리를 받으면 회차마다 다른 조회가 나가고, 모델에 관측 백엔드 임의
 * 쿼리를 허용하는 것이 된다.
 *
 * <p><b>측정 경로는 건드리지 않는다.</b> {@code RcaService}·{@code llm/}을 호출하지 않으므로
 * 이 클래스가 있어도 {@code POST /diagnose}의 동작·토큰·점수가 바뀌지 않는다.
 */
@Slf4j
@RequiredArgsConstructor
public class RcaMcpTools {

    private final TimeExpressionParser timeParser;
    private final Surveyor surveyor;
    private final SurveyContextAssembler surveyAssembler;
    private final SurveyProperties surveyProperties;
    private final Collector collector;
    private final ContextAssembler assembler;
    private final CollectProperties collectProperties;
    private final SurveySessions sessions;

    @Tool(description = """
            장애 조사를 시작한다. 자연어 질문에서 시간창을 해석하고 관측 채널 세 개(트레이스·로그·
            메트릭)를 집계 쿼리로 훑어, 이상 신호를 '장애 후보'로 묶어 돌려준다.
            시간창·쿼리·묶는 규칙은 서버가 정한다 - 질문 문안만 주면 된다.
            돌려주는 surveyId와 후보 번호(INC-N)를 analysisContext에 넘겨 심층 조사를 이어간다.""")
    public String surveyContext(
            @ToolParam(description = "사용자가 말한 그대로의 질문. 시간 표현을 포함해도 된다 "
                    + "(예: '어젯밤 11시쯤 채팅 알림이 안 왔대요'). 시간 표현이 없으면 최근 24시간을 본다.")
            String question) {

        TimeExpressionParser.Resolved resolved = timeParser.resolve(question, null, null, Instant.now());
        TimeWindow window = resolved.window();
        // TriageService와 같은 규칙 - 로그·리포트에서 같은 조사를 따라갈 수 있어야 한다.
        String surveyId = "scan-" + window.start().getEpochSecond();

        MDC.put("traceId", surveyId);
        try {
            log.info("mcp survey start: window={}~{} ({})",
                    window.start(), window.end(), resolved.expression());

            SurveyResult survey = surveyor.survey(window, resolved.expression());

            Duration lookback = SurveyProperties.parse(surveyProperties.step(), Duration.ofMinutes(5));
            List<Signal> signals = SignalExtractor.extract(survey, lookback,
                    surveyProperties.zeroIsAbnormalSet());
            List<Incident> clustered = Incident.cluster(signals, surveyProperties.clusterGapDuration());
            // B-43: 상한은 묶은 뒤에 건다. 검색 단계에서 자르면 같은 지문이 슬롯을 독차지한다.
            List<Incident> incidents = clustered.size() <= surveyProperties.incidentLimit()
                    ? clustered
                    : clustered.subList(0, surveyProperties.incidentLimit());
            if (clustered.size() > incidents.size()) {
                survey.failures().add("장애 후보가 " + clustered.size() + "건이라 상한 "
                        + surveyProperties.incidentLimit() + "건으로 잘랐다 - 창에 신호가 많다는 뜻이니 "
                        + "고른 후보 밖에도 장애가 있을 수 있다.");
            }
            log.info("mcp survey: signals={} incidents={} {}",
                    signals.size(), incidents.size(), Incident.idsOf(incidents));

            sessions.put(new SurveySession(surveyId, question, window, incidents,
                    survey.traceHits(), Instant.now()));

            String context = surveyAssembler.assemble(
                    survey, question, incidents, surveyProperties.includeRaw());
            return header(surveyId, resolved, incidents) + context;
        } finally {
            MDC.remove("traceId");
        }
    }

    @Tool(description = """
            고른 장애 후보를 심층 조사해 분석 컨텍스트를 돌려준다. 후보에서 조사 창·대상 서비스·
            traceId를 서버가 파생해 트레이스 원문·로그·메트릭을 모으고, 라이브러리 스택 프레임과
            반복 로그를 접어 정제한 뒤 돌려준다.
            여러 후보를 한 번에 넘기면 합집합 창으로 조사한다. 후보가 애매하면 하나씩 나눠 불러
            비교하는 편이 낫다.""")
    public String analysisContext(
            @ToolParam(description = "surveyContext가 돌려준 surveyId")
            String surveyId,
            @ToolParam(description = "조사할 후보 번호 목록 (예: [\"INC-1\", \"INC-3\"])")
            List<String> incidentIds) {

        Optional<SurveySession> found = sessions.get(surveyId, Instant.now());
        if (found.isEmpty()) {
            // 조용히 빈 결과를 주면 모델이 "장애 없음"으로 읽는다. 무엇을 하라는지까지 말한다.
            return "surveyId '" + surveyId + "'를 찾을 수 없다 (만료되거나 서버가 재시작됐다). "
                    + "surveyContext를 다시 호출해 후보를 받은 뒤 그 surveyId로 호출할 것.";
        }
        SurveySession session = found.get();

        List<Incident> chosen = session.chosen(incidentIds == null ? List.of() : incidentIds);
        if (chosen.isEmpty()) {
            return "후보 번호 " + incidentIds + "가 이 탐색에 없다. 이 surveyId의 후보는 "
                    + session.knownIds() + "이다.";
        }

        MDC.put("traceId", surveyId);
        try {
            Duration exact = surveyProperties.incidentPadExactDuration();
            Duration bucket = surveyProperties.incidentPadBucketDuration();

            // 창·서비스·traceId는 전부 여기서 파생된다. 모델이 준 것은 후보 번호뿐이다.
            TimeWindow unionWindow = Incident.unionWindow(chosen, exact, bucket, session.sweepWindow());
            List<TimeWindow> candidateWindows = chosen.stream()
                    .map(i -> i.window(exact, bucket, session.sweepWindow()))
                    .toList();
            List<String> services = servicesOf(chosen);
            List<String> chosenTraceIds = TriagePlan.traceIdsOf(chosen);

            // 고른 후보가 물고 있는 traceId를 전부 앞에 둔다 - 대표 하나만 전문 수집하고 나머지를
            // 창 검색과 같은 줄에 세우면 모델이 지목한 것이 상한에 밀린다.
            List<String> candidates = Stream.concat(
                            chosenTraceIds.stream(),
                            session.traceHits().stream()
                                    .filter(Evidence.TraceHit::trusted)
                                    .filter(hit -> hit.startedAt() != null
                                            && !hit.startedAt().isBefore(unionWindow.start())
                                            && !hit.startedAt().isAfter(unionWindow.end()))
                                    .map(Evidence.TraceHit::traceId))
                    .distinct()
                    .toList();

            Scope scope = new Scope(unionWindow, services, List.of())
                    .withCandidates(candidates)
                    .withWindows(candidateWindows);

            log.info("mcp analysis: chosen={} window={}~{} services={} candidates={}",
                    Incident.idsOf(chosen), unionWindow.start(), unionWindow.end(),
                    services, candidates.size());

            CollectedData data = collector.collect(scope);
            String context = assembler.assemble(data, session.question());
            log.info("mcp analysis context: {} chars, {} collection failures",
                    context.length(), data.failures().size());
            return context;
        } finally {
            MDC.remove("traceId");
        }
    }

    /**
     * 고른 후보의 리소스에서 대상 서비스를 뽑는다. <b>모델이 서비스 이름을 쓰게 하지 않는다</b> -
     * 오타 하나로 셀렉터가 빈 결과를 돌려주고, 그것은 "장애 없음"처럼 보인다(§7의 실제 사고).
     *
     * <p>설정된 서비스 목록에 있는 값만 통과시킨다. 후보 리소스는 채널마다 형태가 달라
     * (파드명·스트림 라벨·메트릭 라벨) 그대로 셀렉터에 넣으면 매칭되지 않는 것이 섞인다.
     */
    private List<String> servicesOf(List<Incident> chosen) {
        List<String> known = List.of(collectProperties.apps().split("\\|"));
        return chosen.stream()
                .map(Incident::resource)
                .filter(r -> r != null && !r.isBlank())
                .flatMap(r -> known.stream().filter(r::contains))
                .distinct()
                .toList();
    }

    /** 모델이 다음에 무엇을 해야 하는지가 컨텍스트 맨 위에 있어야 한다. */
    private static String header(String surveyId, TimeExpressionParser.Resolved resolved,
                                List<Incident> incidents) {
        return """
                surveyId: %s
                조사 창: %s ~ %s (해석: %s · 확신도 %s)
                후보: %d건 %s

                다음: 가장 유력한 후보를 골라 analysisContext(surveyId, [후보번호])를 호출한다.
                애매하면 하나씩 나눠 불러 비교할 것. 창·서비스·traceId는 서버가 후보에서 파생한다.

                """.formatted(surveyId, resolved.window().start(), resolved.window().end(),
                resolved.expression(), resolved.confidence().name(),
                incidents.size(), Incident.idsOf(incidents));
    }
}
