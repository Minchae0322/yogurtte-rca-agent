package com.yogurtte.rca.notify;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.yogurtte.rca.report.ReportStore;

/**
 * 알림 채널 선택과 조립. <b>@ConditionalOnProperty로 하나만 뜬다</b>는 계약(§6)이 이 파일에
 * 모여 있고, webhook 설정 검증은 기동 시점에 여기서 실패한다 — 첫 알림에서 터지는 것보다 낫다.
 */
@Configuration(proxyBeanMethods = false)
public class NotifyConfig {

    @Bean
    @ConditionalOnProperty(name = "rca.notify.channel", havingValue = "console", matchIfMissing = true)
    public ConsoleNotifier consoleNotifier(ReportStore reportStore) {
        return new ConsoleNotifier(reportStore);
    }

    @Bean
    @ConditionalOnProperty(name = "rca.notify.channel", havingValue = "slack")
    public SlackNotifier slackNotifier(ReportStore reportStore, NotifyProperties properties) {
        var slack = properties.slack();
        if (slack == null || slack.webhookUrl() == null || slack.webhookUrl().isBlank()) {
            throw new IllegalStateException("rca.notify.channel=slack requires SLACK_WEBHOOK_URL");
        }
        return new SlackNotifier(reportStore, slack.webhookUrl());
    }

    @Bean
    @ConditionalOnProperty(name = "rca.notify.channel", havingValue = "discord")
    public DiscordNotifier discordNotifier(ReportStore reportStore, NotifyProperties properties) {
        var discord = properties.discord();
        if (discord == null || discord.webhookUrl() == null || discord.webhookUrl().isBlank()) {
            throw new IllegalStateException("rca.notify.channel=discord requires DISCORD_WEBHOOK_URL");
        }
        return new DiscordNotifier(reportStore, discord.webhookUrl());
    }
}
