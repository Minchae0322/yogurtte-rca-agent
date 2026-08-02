package com.yogurtte.rca.llm;

import lombok.extern.slf4j.Slf4j;

import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.anthropic.api.AnthropicApi;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * LLM 구현체 선택과 조립. <b>@ConditionalOnProperty로 하나만 뜬다</b>는 계약(§6)이 이 파일에
 * 모여 있고, 구현 클래스들은 조립된 값만 받는 순수 로직이다 — 설정 해석·검증·클라이언트 생성이
 * 생성자에 흩어져 있던 것을 여기로 올렸다.
 */
@Slf4j
@Configuration(proxyBeanMethods = false)
public class LlmConfig {

    @Bean
    @ConditionalOnProperty(name = "rca.llm.provider", havingValue = "claude-cli", matchIfMissing = true)
    public ClaudeCliLlmClient claudeCliLlmClient(LlmProperties properties) {
        var cli = properties.claudeCli();
        var command = cli == null || isBlank(cli.command()) ? "claude" : cli.command();
        var model = cli == null || isBlank(cli.model()) ? ClaudeCliLlmClient.DEFAULT_MODEL : cli.model();
        var timeoutSeconds = cli == null ? 120 : cli.timeoutSeconds();
        var probeOverhead = cli == null || cli.probeOverhead();

        log.info("claude CLI model pinned: {} · overhead probe: {}", model, probeOverhead ? "on" : "off");
        return new ClaudeCliLlmClient(command, model, timeoutSeconds, probeOverhead,
                ClaudeCliLlmClient.createSandbox());
    }

    @Bean
    @ConditionalOnProperty(name = "rca.llm.provider", havingValue = "anthropic")
    public AnthropicLlmClient anthropicLlmClient(LlmProperties properties) {
        var anthropic = properties.anthropic();
        if (anthropic == null || isBlank(anthropic.apiKey())) {
            throw new IllegalStateException("rca.llm.provider=anthropic requires ANTHROPIC_API_KEY");
        }
        var chatModel = AnthropicChatModel.builder()
                .anthropicApi(AnthropicApi.builder().apiKey(anthropic.apiKey()).build())
                .defaultOptions(AnthropicChatOptions.builder()
                        .model(anthropic.model())
                        .maxTokens(anthropic.maxTokens())
                        .build())
                .build();
        return new AnthropicLlmClient(chatModel, anthropic.model());
    }

    @Bean
    @ConditionalOnProperty(name = "rca.llm.provider", havingValue = "openai")
    public OpenAiLlmClient openAiLlmClient(LlmProperties properties) {
        var openai = properties.openai();
        if (openai == null || isBlank(openai.apiKey())) {
            throw new IllegalStateException("rca.llm.provider=openai requires OPENAI_API_KEY");
        }
        var chatModel = OpenAiChatModel.builder()
                .openAiApi(OpenAiApi.builder().apiKey(openai.apiKey()).build())
                .defaultOptions(OpenAiChatOptions.builder().model(openai.model()).build())
                .build();
        return new OpenAiLlmClient(chatModel, openai.model());
    }

    /** API 키가 없으면 카운팅 비활성으로 조립된다 — {@code coverage.contextTokens}는 -1로 남는다. */
    @Bean
    public TokenCounter tokenCounter(LlmProperties properties) {
        var anthropic = properties.anthropic();
        var apiKey = anthropic == null ? null : anthropic.apiKey();
        var cli = properties.claudeCli();
        var fallbackModel = cli == null || isBlank(cli.model()) ? ClaudeCliLlmClient.DEFAULT_MODEL : cli.model();

        if (isBlank(apiKey)) {
            log.info("token counting 비활성 (ANTHROPIC_API_KEY 없음) — coverage.contextTokens는 -1로 기록된다");
            return new TokenCounter(null, fallbackModel);
        }
        var client = RestClient.builder()
                .baseUrl("https://api.anthropic.com")
                .defaultHeader("x-api-key", apiKey)
                .defaultHeader("anthropic-version", "2023-06-01")
                .build();
        return new TokenCounter(client, fallbackModel);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
