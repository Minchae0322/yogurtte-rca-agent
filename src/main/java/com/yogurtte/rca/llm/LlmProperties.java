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

    /**
     * {@code model}을 반드시 고정한다. 지정하지 않으면 CLI가 그날의 기본 모델로 돌아서
     * 회차마다 다른 모델이 채점될 수 있고, 그러면 §8 점수의 회차 간 비교가 성립하지 않는다.
     * 토크나이저도 모델마다 달라 토큰 수 비교까지 함께 무너진다.
     */
    public record ClaudeCli(String command, String model, long timeoutSeconds) {
    }
}
