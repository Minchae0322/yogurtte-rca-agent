package com.yogurtte.rca.notify;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.yogurtte.rca.report.RcaReport;
import com.yogurtte.rca.report.ReportStore;

/** One webhook POST. No SDK. Discord caps a message at 2000 characters. */
@Component
@ConditionalOnProperty(name = "rca.notifier", havingValue = "discord")
public class DiscordNotifier implements Notifier {

    private static final Logger log = LoggerFactory.getLogger(DiscordNotifier.class);
    private static final int MAX_CHARS = 1900;

    private final RestClient restClient = RestClient.create();
    private final ReportStore reportStore;
    private final String webhookUrl;

    public DiscordNotifier(ReportStore reportStore, @Value("${rca.discord.webhook-url:}") String webhookUrl) {
        if (webhookUrl == null || webhookUrl.isBlank()) {
            throw new IllegalStateException("rca.notifier=discord requires DISCORD_WEBHOOK_URL");
        }
        this.reportStore = reportStore;
        this.webhookUrl = webhookUrl;
    }

    @Override
    public void send(RcaReport report) {
        try {
            log.info("report saved to {}", reportStore.save(report));
        } catch (Exception e) {
            log.warn("failed to save report json: {}", e.getMessage());
        }

        restClient.post()
                .uri(webhookUrl)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("content", NotifierText.render(report, MAX_CHARS)))
                .retrieve()
                .toBodilessEntity();
    }
}
