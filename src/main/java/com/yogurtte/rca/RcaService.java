package com.yogurtte.rca;

import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.yogurtte.rca.analyzer.ContextAssembler;
import com.yogurtte.rca.analyzer.LlmClient;
import com.yogurtte.rca.analyzer.SystemPrompt;
import com.yogurtte.rca.collector.Collector;
import com.yogurtte.rca.notify.Notifier;
import com.yogurtte.rca.report.RcaReport;
import com.yogurtte.rca.report.Timings;

/** collect -> assemble -> analyze -> notify. No loop, no tools: v0 is one straight pass. */
@Service
public class RcaService {

    private static final Logger log = LoggerFactory.getLogger(RcaService.class);

    private final Collector collector;
    private final ContextAssembler assembler;
    private final LlmClient llmClient;
    private final Notifier notifier;

    public RcaService(Collector collector, ContextAssembler assembler, LlmClient llmClient, Notifier notifier) {
        this.collector = collector;
        this.assembler = assembler;
        this.llmClient = llmClient;
        this.notifier = notifier;
    }

    public RcaReport investigate(String traceId, String question) {
        var startedAt = Instant.now();
        var overallStart = System.currentTimeMillis();
        log.info("investigating traceId={} question={}", traceId, question);

        var data = collector.collect(traceId);

        var assembleStart = System.currentTimeMillis();
        var context = assembler.assemble(data, question);
        var assembleMs = System.currentTimeMillis() - assembleStart;

        var llmResult = llmClient.analyze(SystemPrompt.TEXT, context);

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
