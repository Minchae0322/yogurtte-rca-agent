package com.yogurtte.rca.notify;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import com.yogurtte.rca.report.RcaReport;
import com.yogurtte.rca.report.ReportStore;

@Slf4j
@RequiredArgsConstructor
public class ConsoleNotifier implements Notifier {

    private final ReportStore reportStore;

    @Override
    public void send(RcaReport report) {
        log.info("""

                ==================== RCA REPORT ====================
                traceId : {}
                question: {}
                mode    : {}
                provider: {} (model={} turns={})
                prompt  : {}
                tokens  : in={} (cacheRead={} cacheCreate={}) out={}{}
                elapsed : total={}ms (tempo={} loki={} mimir={} assemble={} llm={})
                scope   :
                {}
                failures: {}
                ----------------------------------------------------
                {}
                ====================================================
                """,
                report.traceId(), report.question(), report.mode(), report.llmProvider(),
                report.llmModel(), report.llmTurns(), report.promptSource(),
                report.inputTokens(), report.cacheReadTokens(), report.cacheCreationTokens(),
                report.outputTokens(), costSuffix(report.costUsd()),
                report.totalElapsedMs(), report.timings().tempoMs(), report.timings().lokiMs(),
                report.timings().mimirMs(), report.timings().assembleMs(), report.timings().llmMs(),
                formatCoverage(report.coverage()),
                report.collectionFailures().isEmpty() ? "none" : report.collectionFailures(),
                report.analysis());

        try {
            var saved = reportStore.save(report);
            log.info("report saved: {} (json: {})", saved.markdown(), saved.json());
        } catch (Exception e) {
            log.warn("failed to save report: {}", e.getMessage());
        }
    }

    private static String costSuffix(double costUsd) {
        return costUsd < 0 ? "" : " cost=$%.4f".formatted(costUsd);
    }

    /** 이번 조사가 읽은 소스별 범위를 사람이 읽기 좋게 들여쓴 블록으로 만든다. */
    private static String formatCoverage(RcaReport.Coverage c) {
        if (c == null) {
            return "  (없음)";
        }
        var metrics = "%d 수집 / %,dB".formatted(c.metricsCollected().size(), c.metricsBytes());
        if (!c.metricsMissing().isEmpty()) {
            metrics += ", 누락 " + c.metricsMissing();
        }
        return """
                  window : %s ~ %s (%ds)
                  trace  : %,dB / %d spans%s
                  logs   : errwarn=%,dB traceId=%,dB
                  metrics: %s
                  context: %,d chars (+ prompt %,d chars)
                  TOKENS : %s  <- 개선 지표 (count_tokens 실측, CLI 오버헤드 제외)"""
                .formatted(
                        c.windowStart(), c.windowEnd(), c.windowSeconds(),
                        c.traceBytes(), c.traceSpans(), c.traceTrimmed() ? " (상위 span만)" : "",
                        c.errorWarnLogBytes(), c.traceIdLogBytes(),
                        metrics,
                        c.contextChars(), c.promptChars(),
                        c.contextTokens() < 0 ? "측정 안 됨 (ANTHROPIC_API_KEY 없음)"
                                : "%,d tok".formatted(c.contextTokens()));
    }

    @Override
    public String channel() {
        return "console";
    }
}
