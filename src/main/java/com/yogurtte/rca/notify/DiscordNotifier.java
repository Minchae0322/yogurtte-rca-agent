package com.yogurtte.rca.notify;

import java.util.Map;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import com.yogurtte.rca.report.RcaReport;
import com.yogurtte.rca.report.ReportStore;

/** webhook POST 한 번. SDK 없음. Discord는 메시지를 2000자로 제한한다. */
@Slf4j
@RequiredArgsConstructor
public class DiscordNotifier implements Notifier {

    private static final int MAX_CHARS = 1900;

    private final RestClient restClient = RestClient.create();
    private final ReportStore reportStore;
    private final String webhookUrl;

    @Override
    public void send(RcaReport report) {
        try {
            var saved = reportStore.save(report);
            log.info("report saved: {} (json: {})", saved.markdown(), saved.json());
        } catch (Exception e) {
            log.warn("failed to save report: {}", e.getMessage());
        }

        restClient.post()
                .uri(webhookUrl)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("content", NotifierText.render(report, MAX_CHARS)))
                .retrieve()
                .toBodilessEntity();
        log.info("report for trace {} sent to discord", report.traceId());
    }

    @Override
    public String channel() {
        return "discord";
    }
}
