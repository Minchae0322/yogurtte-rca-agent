package com.yogurtte.rca.notify;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 채널 선택과 채널별 설정. 선택된 채널의 블록만 읽는다. */
@ConfigurationProperties("rca.notify")
public record NotifyProperties(
        String channel,
        Webhook slack,
        Webhook discord) {

    public record Webhook(String webhookUrl) {
    }
}
