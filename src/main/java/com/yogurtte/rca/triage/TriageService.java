package com.yogurtte.rca.triage;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.stream.Stream;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.slf4j.MDC;
import org.springframework.stereotype.Service;

import com.yogurtte.rca.analyzer.SystemPromptLoader;
import com.yogurtte.rca.collector.Scope;
import com.yogurtte.rca.collector.TimeWindow;
import com.yogurtte.rca.llm.LlmClient;
import com.yogurtte.rca.llm.LlmResult;
import com.yogurtte.rca.report.RcaReport;
import com.yogurtte.rca.service.RcaService;
import com.yogurtte.rca.triage.incident.Incident;
import com.yogurtte.rca.triage.incident.Signal;
import com.yogurtte.rca.triage.incident.SignalExtractor;
import com.yogurtte.rca.triage.plan.SurveyContextAssembler;
import com.yogurtte.rca.triage.plan.TriagePlan;
import com.yogurtte.rca.triage.survey.SurveyResult;
import com.yogurtte.rca.triage.survey.Surveyor;
import com.yogurtte.rca.triage.window.TimeExpressionParser;

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
@Slf4j
@RequiredArgsConstructor
@Service
public class TriageService {

    private final TimeExpressionParser timeParser;
    private final Surveyor surveyor;
    private final SurveyContextAssembler surveyAssembler;
    private final SurveyProperties surveyProperties;
    private final SystemPromptLoader promptLoader;
    private final LlmClient llmClient;
    private final RcaService rcaService;

    public RcaReport diagnose(String question, Instant from, Instant to, String mode) {
        return diagnose(question, from, to, mode, Instant.now());
    }

    /** {@code now}를 인자로 받는다 — "어젯밤"이 언제인지가 테스트에서 고정되어야 한다. */
    public RcaReport diagnose(String question, Instant from, Instant to, String mode, Instant now) {
        TimeExpressionParser.Resolved resolved = timeParser.resolve(question, from, to, now);
        TimeWindow window = resolved.window();
        String correlationId = "scan-" + window.start().getEpochSecond();

        MDC.put("traceId", correlationId);
        try {
            log.info("triage start: window={}~{} ({})", window.start(), window.end(), resolved.expression());

            long surveyStart = System.currentTimeMillis();
            SurveyResult survey = surveyor.survey(window, resolved.expression());
            long surveyMs = System.currentTimeMillis() - surveyStart;

            // 코드가 신호를 뽑아 후보를 만든다. 모델은 "어느 후보인가"만 고르고 창은 계산된다.
            Duration lookback = SurveyProperties.parse(surveyProperties.step(), Duration.ofMinutes(5));
            List<Signal> signals = SignalExtractor.extract(survey, lookback,
                    surveyProperties.zeroIsAbnormalSet());
            List<Incident> incidents = Incident.cluster(signals, surveyProperties.clusterGapDuration());
            log.info("signals={} incidents={} {}", signals.size(), incidents.size(),
                    Incident.idsOf(incidents));

            boolean includeRaw = surveyProperties.includeRaw();
            String context = surveyAssembler.assemble(survey, question, incidents, includeRaw);
            log.info("triage context: {} chars (원본 {})", context.length(), includeRaw ? "포함" : "제외");
            SystemPromptLoader.Loaded prompt = promptLoader.load("triage");
            LlmResult llmResult = llmClient.analyze(prompt.text(), context);
            log.info("triage llm answered: in={} out={} {}ms",
                    llmResult.inputTokens(), llmResult.outputTokens(), llmResult.elapsedMs());

            TriagePlan.Padding padding = new TriagePlan.Padding(
                    surveyProperties.incidentPadExactDuration(),
                    surveyProperties.incidentPadBucketDuration());
            TriagePlan plan = TriagePlan.parse(llmResult.text(), window, incidents, padding);
            if (!plan.parsed()) {
                log.warn("triage plan을 읽지 못했다: {}", plan.notes());
            }
            log.info("triage plan: window={}~{} services={} traceId={} chosen={} dismissed={}",
                    plan.window().start(), plan.window().end(), plan.services(), plan.traceId(),
                    plan.chosenIncidentIds(), plan.dismissedIncidentIds());

            RcaReport.Triage record = new RcaReport.Triage(
                    resolved.expression(),
                    resolved.confidence().name(),
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
                    plan.dismissedIncidentIds(),
                    // 어느 팔로 돌았는지가 리포트에 없으면 두 회차를 나중에 구분할 수 없다.
                    includeRaw);

            // B-9: 스윕이 찾은 이상 트레이스 중 좁힌 창 안의 것을 후보로 넘긴다. 신뢰 불가 행은
            // 뺀다(B-15 — 정렬하면 그 행이 항상 1위가 된다). 창 기준 무조건 검색은 수집기가 한다.
            //
            // 고른 후보가 물고 있는 traceId는 <b>전부</b> 앞에 놓는다 — 대표 하나만 전문 수집하고
            // 나머지를 창 검색과 같은 줄에 세우면, 모델이 지목한 것이 상한에 밀릴 수 있다.
            TimeWindow chosen = plan.window();
            List<Incident> chosenIncidents = incidents.stream()
                    .filter(i -> plan.chosenIncidentIds().contains(i.id())).toList();
            List<String> chosenTraceIds = TriagePlan.traceIdsOf(chosenIncidents);

            // 후보별 창. plan.window()는 이들의 합집합이라 사이의 빈 구간까지 덮는다 —
            // 로그·트레이스는 점 사건이라 그 구간에 정보가 없으므로 나눠서 조회한다.
            // 메트릭은 합집합 창을 그대로 쓴다(시계열이 조각나면 회복 시점을 잃는다).
            List<TimeWindow> candidateWindows = chosenIncidents.stream()
                    .map(i -> i.window(padding.exact(), padding.bucket(), window))
                    .toList();
            List<String> candidates = Stream.concat(chosenTraceIds.stream(), survey.traceHits().stream()
                    .filter(com.yogurtte.rca.report.Evidence.TraceHit::trusted)
                    .filter(hit -> hit.startedAt() != null
                            && !hit.startedAt().isBefore(chosen.start())
                            && !hit.startedAt().isAfter(chosen.end()))
                    .map(com.yogurtte.rca.report.Evidence.TraceHit::traceId))
                    .distinct()
                    .toList();

            Scope scope = plan.toScope()
                    .withCandidates(candidates)
                    .withWindows(candidateWindows);
            return rcaService.investigate(scope, question, mode, record);
        } finally {
            MDC.remove("traceId");
        }
    }
}
