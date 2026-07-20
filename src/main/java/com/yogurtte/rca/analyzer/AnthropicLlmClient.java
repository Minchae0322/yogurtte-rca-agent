package com.yogurtte.rca.analyzer;

import java.util.List;

import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.anthropic.api.AnthropicApi;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "rca.llm.provider", havingValue = "anthropic")
public class AnthropicLlmClient implements LlmClient {

    private final AnthropicChatModel chatModel;

    public AnthropicLlmClient(
            @Value("${spring.ai.anthropic.api-key:}") String apiKey,
            @Value("${spring.ai.anthropic.chat.options.model:claude-opus-4-8}") String model,
            @Value("${spring.ai.anthropic.chat.options.max-tokens:8192}") int maxTokens) {

        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("rca.llm.provider=anthropic requires ANTHROPIC_API_KEY");
        }
        this.chatModel = AnthropicChatModel.builder()
                .anthropicApi(AnthropicApi.builder().apiKey(apiKey).build())
                .defaultOptions(AnthropicChatOptions.builder().model(model).maxTokens(maxTokens).build())
                .build();
    }

    @Override
    public LlmResult analyze(String systemPrompt, String context) {
        var started = System.currentTimeMillis();
        var response = chatModel.call(new Prompt(List.of(new SystemMessage(systemPrompt), new UserMessage(context))));
        var elapsed = System.currentTimeMillis() - started;

        var usage = response.getMetadata().getUsage();
        return new LlmResult(
                response.getResult().getOutput().getText(),
                usage == null || usage.getPromptTokens() == null ? -1 : usage.getPromptTokens(),
                usage == null || usage.getCompletionTokens() == null ? -1 : usage.getCompletionTokens(),
                elapsed);
    }

    @Override
    public String provider() {
        return "anthropic";
    }
}
