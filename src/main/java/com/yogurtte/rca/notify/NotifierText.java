package com.yogurtte.rca.notify;

import com.yogurtte.rca.report.RcaReport;

/** webhook notifier들이 공유하는 메시지 본문. */
final class NotifierText {

    private NotifierText() {
    }

    static String render(RcaReport report, int maxChars) {
        String cost = report.costUsd() < 0 ? "" : " | cost $%.4f".formatted(report.costUsd());
        String header = """
                *RCA* `%s` (%s)
                q: %s
                provider: %s (%s, turns %d) | tokens in/out: %d/%d%s | total: %dms
                scope: %s
                failures: %s

                """.formatted(
                report.traceId(),
                report.mode(),
                report.question(),
                report.llmProvider(),
                report.llmModel(),
                report.llmTurns(),
                report.inputTokens(),
                report.outputTokens(),
                cost,
                report.totalElapsedMs(),
                scope(report.coverage()),
                report.collectionFailures().isEmpty() ? "none" : String.join("; ", report.collectionFailures()));

        int budget = maxChars - header.length();
        String analysis = report.analysis() == null ? "" : report.analysis();
        if (budget > 0 && analysis.length() > budget) {
            analysis = analysis.substring(0, budget - 20) + "\n... (truncated)";
        }
        return header + analysis;
    }

    /** webhook 한 줄용 압축 범위 표기. */
    private static String scope(RcaReport.Coverage c) {
        if (c == null) {
            return "n/a";
        }
        return "trace %d spans/%,dB · logs %,d+%,dB · metrics %d/%d %,dB · ctx %,dc / %s".formatted(
                c.traceSpans(), c.traceBytes(),
                c.errorWarnLogBytes(), c.traceIdLogBytes(),
                c.metricsCollected().size(), c.metricsCollected().size() + c.metricsMissing().size(),
                c.metricsBytes(),
                c.contextChars(),
                c.contextTokens() < 0 ? "tok n/a" : "%,d tok".formatted(c.contextTokens()));
    }
}
