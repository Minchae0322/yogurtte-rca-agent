package com.yogurtte.rca.triage;

import java.time.Duration;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

import com.yogurtte.rca.analyzer.SystemPromptLoader;
import com.yogurtte.rca.llm.LlmClient;
import com.yogurtte.rca.report.RcaReport;
import com.yogurtte.rca.service.RcaService;

/**
 * 자연어 질문 하나로 시작하는 조사. traceId를 받지 않는다.
 *
 * <pre>
 *   질문 → [창 파싱] → [스윕] → [선정] → [심층 수집 + 분석] → 리포트
 *           결정적      코드가    LLM      기존 v0 경로 그대로
 * </pre>
 *
 * <p>앞의 세 단계만 이 클래스가 맡고, 마지막은 {@link RcaService}에 그대로 넘긴다 —
 * 분석 능력은 traceId로 직접 들어오는 기존 경로와 <b>완전히 같은 코드</b>여야 두 진입점의
 * 점수를 비교할 수 있다.
 */
@Service
public class TriageService {

    private static final Logger log = LoggerFactory.getLogger(TriageService.class);

    private final TimeExpressionParser timeParser;
    private final Surveyor surveyor;
    private final SurveyContextAssembler surveyAssembler;
    private final SignalExtractor signalExtractor;
    private final IncidentClusterer clusterer;
    private final SurveyProperties surveyProperties;
    private final SystemPromptLoader promptLoader;
    private final LlmClient llmClient;
    private final RcaService rcaService;

    public TriageService(TimeExpressionParser timeParser, Surveyor surveyor,
                         SurveyContextAssembler surveyAssembler, SignalExtractor signalExtractor,
                         IncidentClusterer clusterer, SurveyProperties surveyProperties,
                         SystemPromptLoader promptLoader, LlmClient llmClient, RcaService rcaService) {
        this.timeParser = timeParser;
        this.surveyor = surveyor;
        this.surveyAssembler = surveyAssembler;
        this.signalExtractor = signalExtractor;
        this.clusterer = clusterer;
        this.surveyProperties = surveyProperties;
        this.promptLoader = promptLoader;
        this.rcaService = rcaService;
        this.llmClient = llmClient;
    }

    public RcaReport diagnose(String question, Instant from, Instant to, String mode) {
        return diagnose(question, from, to, mode, Instant.now());
    }

    /** {@code now}를 인자로 받는다 — "어젯밤"이 언제인지가 테스트에서 고정되어야 한다. */
    public RcaReport diagnose(String question, Instant from, Instant to, String mode, Instant now) {
        var resolved = timeParser.resolve(question, from, to, now);
        var window = resolved.window();
        var correlationId = "scan-" + window.start().getEpochSecond();

        MDC.put("traceId", correlationId);
        try {
            log.info("triage start: window={}~{} ({})", window.start(), window.end(), resolved.expression());

            var surveyStart = System.currentTimeMillis();
            var survey = surveyor.survey(window, resolved.expression());
            var surveyMs = System.currentTimeMillis() - surveyStart;

            // 코드가 신호를 뽑아 후보를 만든다. 모델은 "어느 후보인가"만 고르고 창은 계산된다.
            var lookback = SurveyProperties.parse(surveyProperties.step(), Duration.ofMinutes(5));
            var signals = signalExtractor.extract(survey, lookback);
            var incidents = clusterer.cluster(signals, surveyProperties.clusterGapDuration());
            log.info("signals={} incidents={} {}", signals.size(), incidents.size(),
                    Incident.idsOf(incidents));

            var context = surveyAssembler.assemble(survey, question, incidents);
            var prompt = promptLoader.load("triage");
            var llmResult = llmClient.analyze(prompt.text(), context);
            log.info("triage llm answered: in={} out={} {}ms",
                    llmResult.inputTokens(), llmResult.outputTokens(), llmResult.elapsedMs());

            var padding = new TriagePlan.Padding(
                    surveyProperties.incidentPadExactDuration(),
                    surveyProperties.incidentPadBucketDuration());
            var plan = TriagePlan.parse(llmResult.text(), window, incidents, padding);
            if (!plan.parsed()) {
                log.warn("triage plan을 읽지 못했다: {}", plan.notes());
            }
            log.info("triage plan: window={}~{} services={} traceId={} chosen={} dismissed={}",
                    plan.window().start(), plan.window().end(), plan.services(), plan.traceId(),
                    plan.chosenIncidentIds(), plan.dismissedIncidentIds());

            var record = new RcaReport.Triage(
                    resolved.expression(),
                    window.start(),
                    window.end(),
                    plan.window().start(),
                    plan.window().end(),
                    plan.services(),
                    plan.traceId(),
                    survey.traceHits(),
                    plan.reason(),
                    plan.evidence(),
                    plan.parsed(),
                    plan.notes(),
                    prompt.source(),
                    llmResult.text(),
                    llmResult.inputTokens(),
                    llmResult.outputTokens(),
                    llmResult.costUsd(),
                    // 토큰 축 검산(chars × 실측 비율)에 필요하다 — 이게 없으면 총 in에서
                    // CLI 오버헤드를 뺀 추정 하나만 남고 교차 확인이 불가능하다.
                    context.length(),
                    prompt.text().length(),
                    surveyMs,
                    llmResult.elapsedMs(),
                    survey.failures(),
                    // 후보 전부를 남긴다 — "다른 걸 골랐어야 했나"는 그때 무엇이 보였는지가 있어야 판단된다.
                    incidents.stream().map(Incident::describe).toList(),
                    plan.chosenIncidentIds(),
                    plan.dismissedIncidentIds());

            return rcaService.investigate(plan.toScope(), question, mode, record);
        } finally {
            MDC.remove("traceId");
        }
    }
}
