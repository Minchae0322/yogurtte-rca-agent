package com.yogurtte.rca.service;

import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

import com.yogurtte.rca.analyzer.ContextAssembler;
import com.yogurtte.rca.analyzer.SystemPromptLoader;
import com.yogurtte.rca.collector.Collector;
import com.yogurtte.rca.llm.LlmClient;
import com.yogurtte.rca.notify.Notifier;
import com.yogurtte.rca.report.RcaReport;
import com.yogurtte.rca.report.Timings;

/** collect -> assemble -> analyze -> notify. 루프도 도구도 없다: v0는 한 번의 직선 실행이다. */
@Service
public class RcaService {

    private static final Logger log = LoggerFactory.getLogger(RcaService.class);

    private final Collector collector;
    private final ContextAssembler assembler;
    private final SystemPromptLoader promptLoader;
    private final LlmClient llmClient;
    private final Notifier notifier;

    public RcaService(Collector collector, ContextAssembler assembler, SystemPromptLoader promptLoader,
                      LlmClient llmClient, Notifier notifier) {
        this.collector = collector;
        this.assembler = assembler;
        this.promptLoader = promptLoader;
        this.llmClient = llmClient;
        this.notifier = notifier;
        log.info("rca-agent ready: llm={} notifier={}", llmClient.provider(), notifier.channel());
    }

    public RcaReport investigate(String traceId, String question) {
        MDC.put("traceId", traceId);
        try {
            return run(traceId, question);
        } finally {
            MDC.remove("traceId");
        }
    }

    private RcaReport run(String traceId, String question) {
        var startedAt = Instant.now();
        var overallStart = System.currentTimeMillis();
        log.info("investigating question={}", question);

        var data = collector.collect(traceId);

        var assembleStart = System.currentTimeMillis();
        var context = assembler.assemble(data, question);
        var assembleMs = System.currentTimeMillis() - assembleStart;
        log.info("context assembled: {} chars, {} collection failures", context.length(), data.failures().size());

        var prompt = promptLoader.load();
        log.info("system prompt: {}", prompt.source());

        var llmResult = llmClient.analyze(prompt.text(), context);
        log.info("llm answered: in={} out={} tokens, {}ms",
                llmResult.inputTokens(), llmResult.outputTokens(), llmResult.elapsedMs());

        var timings = new Timings(
                data.stepMillis().getOrDefault("tempoMs", 0L),
                data.stepMillis().getOrDefault("lokiMs", 0L),
                data.stepMillis().getOrDefault("mimirMs", 0L),
                assembleMs,
                llmResult.elapsedMs());

        var report = new RcaReport(
                traceId,
                question,
                startedAt,
                llmClient.provider(),
                prompt.source(),
                llmResult.text(),
                llmResult.inputTokens(),
                llmResult.outputTokens(),
                System.currentTimeMillis() - overallStart,
                timings,
                context.length(),
                data.failures());

        notifier.send(report);
        return report;
    }
}
