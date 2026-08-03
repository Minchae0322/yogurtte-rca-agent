package com.yogurtte.rca.notify;

import java.util.Map;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import com.yogurtte.rca.report.RcaReport;
import com.yogurtte.rca.report.ReportStore;

/** webhook POST 한 번. SDK 없음. */
@Slf4j
@RequiredArgsConstructor
public class SlackNotifier implements Notifier {

    private static final int MAX_CHARS = 3500;

    private final RestClient restClient = RestClient.create();
    private final ReportStore reportStore;
    private final String webhookUrl;

    @Override
    public void send(RcaReport report) {
        try {
            ReportStore.Saved saved = reportStore.save(report);
            log.info("report saved: {} (json: {})", saved.markdown(), saved.json());
        } catch (Exception e) {
            log.warn("failed to save report: {}", e.getMessage());
        }

        restClient.post()
                .uri(webhookUrl)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("text", NotifierText.render(report, MAX_CHARS)))
                .retrieve()
                .toBodilessEntity();
        log.info("report for trace {} sent to slack", report.traceId());
    }

    @Override
    public String channel() {
        return "slack";
    }
}
