package com.yogurtte.rca.service;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

import com.yogurtte.rca.analyzer.ContextAssembler;
import com.yogurtte.rca.analyzer.SystemPromptLoader;
import com.yogurtte.rca.collector.CollectProperties;
import com.yogurtte.rca.collector.CollectedData;
import com.yogurtte.rca.collector.Collector;
import com.yogurtte.rca.collector.TraceSpans;
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
    private final CollectProperties collectProperties;
    private final LlmClient llmClient;
    private final Notifier notifier;

    public RcaService(Collector collector, ContextAssembler assembler, SystemPromptLoader promptLoader,
                      CollectProperties collectProperties, LlmClient llmClient, Notifier notifier) {
        this.collector = collector;
        this.assembler = assembler;
        this.promptLoader = promptLoader;
        this.collectProperties = collectProperties;
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
        log.info("llm answered: in={} out={} tokens, cost={} , {}ms",
                llmResult.inputTokens(), llmResult.outputTokens(),
                llmResult.costUsd() < 0 ? "n/a" : llmResult.costUsd(), llmResult.elapsedMs());

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
                llmResult.costUsd(),
                System.currentTimeMillis() - overallStart,
                timings,
                context.length(),
                coverage(data, context),
                data.failures());

        notifier.send(report);
        return report;
    }

    /** 이번 조사가 실제로 읽은 데이터 범위를 집계한다. */
    private RcaReport.Coverage coverage(CollectedData data, String context) {
        var window = data.window();
        var spans = TraceSpans.parse(data.traceJson()).size();
        var traceBytes = utf8Bytes(data.traceJson());

        var collected = new ArrayList<>(data.metricsJson().keySet());
        var missing = collectProperties.metricQueries().stream()
                .filter(query -> !data.metricsJson().containsKey(query))
                .toList();

        return new RcaReport.Coverage(
                window == null ? null : window.start(),
                window == null ? null : window.end(),
                window == null ? 0 : Duration.between(window.start(), window.end()).getSeconds(),
                traceBytes,
                spans,
                traceBytes > collectProperties.maxTraceBytes(),
                utf8Bytes(data.errorWarnLogsJson()),
                utf8Bytes(data.traceIdLogsJson()),
                collected,
                missing,
                context.length(),
                context.length() / 4L); // 대략 4자 ≈ 1토큰. provider usage와 대조하는 러프 추정치
    }

    private static int utf8Bytes(String s) {
        return (s == null || s.isBlank()) ? 0 : s.getBytes(StandardCharsets.UTF_8).length;
    }
}
