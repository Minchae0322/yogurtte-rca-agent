package com.yogurtte.rca.notify;

import com.yogurtte.rca.report.RcaReport;

/** Shared message body for the webhook notifiers. */
final class NotifierText {

    private NotifierText() {
    }

    static String render(RcaReport report, int maxChars) {
        var header = """
                *RCA* `%s`
                q: %s
                provider: %s | tokens in/out: %d/%d | total: %dms
                failures: %s

                """.formatted(
                report.traceId(),
                report.question(),
                report.llmProvider(),
                report.inputTokens(),
                report.outputTokens(),
                report.totalElapsedMs(),
                report.collectionFailures().isEmpty() ? "none" : String.join("; ", report.collectionFailures()));

        var budget = maxChars - header.length();
        var analysis = report.analysis() == null ? "" : report.analysis();
        if (budget > 0 && analysis.length() > budget) {
            analysis = analysis.substring(0, budget - 20) + "\n... (truncated)";
        }
        return header + analysis;
    }
}
