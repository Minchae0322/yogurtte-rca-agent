package com.yogurtte.rca.llm;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** provider 선택과 provider별 설정. 선택된 provider의 블록만 읽는다. */
@ConfigurationProperties("rca.llm")
public record LlmProperties(
        String provider,
        Anthropic anthropic,
        OpenAi openai,
        ClaudeCli claudeCli) {

    public record Anthropic(String apiKey, String model, int maxTokens) {
    }

    public record OpenAi(String apiKey, String model) {
    }

    public record ClaudeCli(String command, long timeoutSeconds) {
    }
}
