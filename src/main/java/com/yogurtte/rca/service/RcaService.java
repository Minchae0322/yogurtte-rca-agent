package com.yogurtte.rca.service;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;

import jakarta.annotation.PostConstruct;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.slf4j.MDC;
import org.springframework.stereotype.Service;

import com.yogurtte.rca.analyzer.ContextAssembler;
import com.yogurtte.rca.analyzer.EvidenceExtractor;
import com.yogurtte.rca.analyzer.ServiceGraphExtractor;
import com.yogurtte.rca.analyzer.SystemPromptLoader;
import com.yogurtte.rca.collector.CollectProperties;
import com.yogurtte.rca.collector.CollectedData;
import com.yogurtte.rca.collector.Collector;
import com.yogurtte.rca.collector.Scope;
import com.yogurtte.rca.collector.TraceSpans;
import com.yogurtte.rca.llm.LlmClient;
import com.yogurtte.rca.llm.TokenCounter;
import com.yogurtte.rca.notify.Notifier;
import com.yogurtte.rca.report.RcaReport;
import com.yogurtte.rca.report.Timings;

/** collect -> assemble -> analyze -> notify. 루프도 도구도 없다: v0는 한 번의 직선 실행이다. */
@Slf4j
@RequiredArgsConstructor
@Service
public class RcaService {

    private final Collector collector;
    private final ContextAssembler assembler;
    private final EvidenceExtractor evidenceExtractor;
    private final ServiceGraphExtractor graphExtractor;
    private final SystemPromptLoader promptLoader;
    private final CollectProperties collectProperties;
    private final LlmClient llmClient;
    private final TokenCounter tokenCounter;
    private final Notifier notifier;

    @PostConstruct
    void logReady() {
        log.info("rca-agent ready: llm={} notifier={}", llmClient.provider(), notifier.channel());
    }

    public RcaReport investigate(String traceId, String question) {
        return investigate(traceId, question, "rca");
    }

    /** traceId로 바로 들어오는 기존 v0 진입점. 탐색 단계를 거치지 않으므로 triage 기록이 없다. */
    public RcaReport investigate(String traceId, String question, String mode) {
        return investigate(Scope.ofTrace(traceId), question, mode, null);
    }

    /**
     * 범위 하나를 깊게 조사한다. 탐색을 거쳐 들어왔으면 그 선택의 기록({@code triage})이 함께 실린다.
     *
     * <p>traceId가 없는 범위도 정상 입력이다 — 이 경로가 traceId를 요구하면 이상 트레이스가
     * 생성되지 않는 장애에서 파이프라인이 끊긴다.
     */
    public RcaReport investigate(Scope scope, String question, String mode, RcaReport.Triage triage) {
        var normalizedMode = (mode == null || mode.isBlank()) ? "rca" : mode;
        MDC.put("traceId", scope.correlationId());
        try {
            return run(scope, question, normalizedMode, triage);
        } finally {
            MDC.remove("traceId");
        }
    }

    private RcaReport run(Scope scope, String question, String mode, RcaReport.Triage triage) {
        var startedAt = Instant.now();
        var overallStart = System.currentTimeMillis();
        log.info("investigating mode={} traceId={} services={} question={}",
                mode, scope.traceId(), scope.services(), question);

        var data = collector.collect(scope);

        var assembleStart = System.currentTimeMillis();
        var context = assembler.assemble(data, question);
        var assembleMs = System.currentTimeMillis() - assembleStart;
        log.info("context assembled: {} chars, {} collection failures", context.length(), data.failures().size());

        var prompt = promptLoader.load(mode);
        log.info("system prompt: {}", prompt.source());

        var llmResult = llmClient.analyze(prompt.text(), context);
        log.info("llm answered: model={} turns={} in={} (cacheRead={} cacheCreate={}) out={} cost={} {}ms",
                llmResult.model(), llmResult.numTurns(), llmResult.inputTokens(),
                llmResult.cacheReadTokens(), llmResult.cacheCreationTokens(), llmResult.outputTokens(),
                llmResult.costUsd() < 0 ? "n/a" : llmResult.costUsd(), llmResult.elapsedMs());
        if (llmResult.numTurns() > 1) {
            log.warn("LLM 왕복이 {}회다 — '도구 없는 단일 패스' 전제가 깨졌다. usage가 마지막 턴만 담고 "
                    + "비용은 합계일 수 있으므로 이 회차의 토큰·비용은 그대로 비교하면 안 된다", llmResult.numTurns());
        }

        // 개선 지표: CLI 오버헤드가 섞인 llmResult.inputTokens()가 아니라, 내가 만든 입력만 직접 잰다.
        var contextTokens = tokenCounter.count(llmResult.model(), prompt.text(), context);

        // 오버헤드는 상수가 아니다(하루 만에 20% 이동 실측) — 문서에 박힌 값을 소급해 빼면 틀린다.
        // 본 호출 직후 같은 조건으로 재서, contextTokens 계산이 이 회차 안에서 닫히게 한다.
        var overheadTokens = llmClient.overheadTokens();

        var timings = new Timings(
                data.stepMillis().getOrDefault("tempoMs", 0L),
                data.stepMillis().getOrDefault("lokiMs", 0L),
                data.stepMillis().getOrDefault("mimirMs", 0L),
                assembleMs,
                llmResult.elapsedMs());

        var report = new RcaReport(
                data.traceId(),
                question,
                mode,
                startedAt,
                llmClient.provider(),
                llmResult.model(),
                llmResult.numTurns(),
                prompt.source(),
                llmResult.text(),
                llmResult.inputTokens(),
                llmResult.outputTokens(),
                llmResult.cacheReadTokens(),
                llmResult.cacheCreationTokens(),
                llmResult.costUsd(),
                System.currentTimeMillis() - overallStart,
                timings,
                context.length(),
                coverage(data, context, prompt.text(), contextTokens, overheadTokens),
                triage,
                evidenceExtractor.extract(data),
                graphExtractor.extract(data),
                data.failures());

        notifier.send(report);
        return report;
    }

    /** 이번 조사가 실제로 읽은 데이터 범위를 집계한다. */
    private RcaReport.Coverage coverage(CollectedData data, String context, String systemPrompt,
                                        long contextTokens, long overheadTokens) {
        var window = data.window();
        var spans = TraceSpans.parse(data.traceJson()).size();
        var traceBytes = utf8Bytes(data.traceJson());

        var collected = new ArrayList<>(data.metricsJson().keySet());
        var missing = collectProperties.metricQueries().stream()
                .filter(query -> !data.metricsJson().containsKey(query))
                .toList();
        var metricsBytes = data.metricsJson().values().stream().mapToInt(RcaService::utf8Bytes).sum();

        return new RcaReport.Coverage(
                window == null ? null : window.start(),
                window == null ? null : window.end(),
                window == null ? 0 : Duration.between(window.start(), window.end()).getSeconds(),
                traceBytes,
                spans,
                traceBytes > collectProperties.maxTraceBytes(),
                utf8Bytes(data.errorWarnLogsJson()),
                utf8Bytes(data.traceIdLogsJson()),
                metricsBytes,
                collected,
                missing,
                systemPrompt == null ? 0 : systemPrompt.length(),
                context.length(),
                contextTokens,
                overheadTokens);
    }

    private static int utf8Bytes(String s) {
        return (s == null || s.isBlank()) ? 0 : s.getBytes(StandardCharsets.UTF_8).length;
    }
}
