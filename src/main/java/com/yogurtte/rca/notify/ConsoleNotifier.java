package com.yogurtte.rca.notify;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.yogurtte.rca.report.RcaReport;
import com.yogurtte.rca.report.ReportStore;

@Component
@ConditionalOnProperty(name = "rca.notify.channel", havingValue = "console", matchIfMissing = true)
public class ConsoleNotifier implements Notifier {

    private static final Logger log = LoggerFactory.getLogger(ConsoleNotifier.class);

    private final ReportStore reportStore;

    public ConsoleNotifier(ReportStore reportStore) {
        this.reportStore = reportStore;
    }

    @Override
    public void send(RcaReport report) {
        log.info("""

                ==================== RCA REPORT ====================
                traceId : {}
                question: {}
                provider: {}
                prompt  : {}
                tokens  : in={} out={}
                elapsed : total={}ms (tempo={} loki={} mimir={} assemble={} llm={})
                context : {} chars
                failures: {}
                ----------------------------------------------------
                {}
                ====================================================
                """,
                report.traceId(), report.question(), report.llmProvider(), report.promptSource(),
                report.inputTokens(), report.outputTokens(),
                report.totalElapsedMs(), report.timings().tempoMs(), report.timings().lokiMs(),
                report.timings().mimirMs(), report.timings().assembleMs(), report.timings().llmMs(),
                report.contextChars(),
                report.collectionFailures().isEmpty() ? "none" : report.collectionFailures(),
                report.analysis());

        try {
            log.info("report saved to {}", reportStore.save(report));
        } catch (Exception e) {
            log.warn("failed to save report json: {}", e.getMessage());
        }
    }

    @Override
    public String channel() {
        return "console";
    }
}
