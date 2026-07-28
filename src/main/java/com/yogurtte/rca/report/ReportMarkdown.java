package com.yogurtte.rca.report;

/**
 * RcaReport를 사람이 읽는 Markdown 보고서로 렌더링한다. 분석 본문은 이미 마크다운이므로,
 * 앞에 조사 메타데이터·측정치·수집 범위를 붙여 완결된 보고서를 만든다.
 */
final class ReportMarkdown {

    private ReportMarkdown() {
    }

    static String render(RcaReport report) {
        var sb = new StringBuilder();

        sb.append("# RCA Report — `").append(report.traceId()).append("`\n\n");

        sb.append("| 항목 | 값 |\n|---|---|\n");
        sb.append("| 모드 | ").append(report.mode()).append(" |\n");
        sb.append("| 질문 | ").append(nz(report.question())).append(" |\n");
        sb.append("| 시각 | ").append(report.startedAt()).append(" |\n");
        sb.append("| provider | ").append(report.llmProvider()).append(" |\n");
        sb.append("| model | `").append(nz(report.llmModel())).append("`");
        if (report.llmTurns() >= 0) {
            sb.append(" · turns ").append(report.llmTurns());
            if (report.llmTurns() > 1) {
                sb.append(" ⚠ 단일 패스 아님");
            }
        }
        sb.append(" |\n");
        sb.append("| prompt | `").append(report.promptSource()).append("` |\n");
        sb.append("| tokens | in ").append(report.inputTokens());
        if (report.cacheReadTokens() >= 0 || report.cacheCreationTokens() >= 0) {
            sb.append(" (cacheRead %,d · cacheCreate %,d)"
                    .formatted(report.cacheReadTokens(), report.cacheCreationTokens()));
        }
        sb.append(" / out ").append(report.outputTokens());
        if (report.costUsd() >= 0) {
            sb.append(" · cost $%.4f".formatted(report.costUsd()));
        }
        sb.append(" |\n");
        var t = report.timings();
        sb.append("| elapsed | total ").append(report.totalElapsedMs()).append("ms")
                .append(" (tempo ").append(t.tempoMs()).append(" · loki ").append(t.lokiMs())
                .append(" · mimir ").append(t.mimirMs()).append(" · assemble ").append(t.assembleMs())
                .append(" · llm ").append(t.llmMs()).append(") |\n\n");

        sb.append("## 수집 범위 (Coverage)\n\n");
        var c = report.coverage();
        if (c == null) {
            sb.append("(없음)\n\n");
        } else {
            sb.append("- **window**: ").append(c.windowStart()).append(" ~ ").append(c.windowEnd())
                    .append(" (").append(c.windowSeconds()).append("s)\n");
            sb.append("- **trace**: %,dB / %d spans%s\n"
                    .formatted(c.traceBytes(), c.traceSpans(), c.traceTrimmed() ? " (상위 span만)" : ""));
            sb.append("- **logs**: errwarn=%,dB · traceId=%,dB\n"
                    .formatted(c.errorWarnLogBytes(), c.traceIdLogBytes()));
            sb.append("- **metrics**: %d 수집 / %,dB".formatted(c.metricsCollected().size(), c.metricsBytes()));
            if (!c.metricsMissing().isEmpty()) {
                sb.append(", 누락 ").append(c.metricsMissing());
            }
            sb.append("\n");
            sb.append("- **context**: %,d chars (+ 시스템 프롬프트 %,d chars)\n"
                    .formatted(c.contextChars(), c.promptChars()));
            sb.append("- **contextTokens**: %s  ← 개선 지표 (count_tokens 실측, CLI 오버헤드 제외)\n\n"
                    .formatted(c.contextTokens() < 0 ? "측정 안 됨" : "%,d tok".formatted(c.contextTokens())));
        }

        if (!report.collectionFailures().isEmpty()) {
            sb.append("## 수집 실패/누락\n\n");
            report.collectionFailures().forEach(f -> sb.append("- ").append(f).append("\n"));
            sb.append("\n");
        }

        sb.append("---\n\n");
        sb.append(nz(report.analysis())).append("\n");

        return sb.toString();
    }

    private static String nz(String s) {
        return (s == null || s.isBlank()) ? "(없음)" : s;
    }
}
