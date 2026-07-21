package com.yogurtte.rca.llm;

import java.util.List;

import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.anthropic.api.AnthropicApi;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "rca.llm.provider", havingValue = "anthropic")
public class AnthropicLlmClient implements LlmClient {

    private final AnthropicChatModel chatModel;

    public AnthropicLlmClient(LlmProperties properties) {
        var anthropic = properties.anthropic();
        if (anthropic == null || anthropic.apiKey() == null || anthropic.apiKey().isBlank()) {
            throw new IllegalStateException("rca.llm.provider=anthropic requires ANTHROPIC_API_KEY");
        }
        this.chatModel = AnthropicChatModel.builder()
                .anthropicApi(AnthropicApi.builder().apiKey(anthropic.apiKey()).build())
                .defaultOptions(AnthropicChatOptions.builder()
                        .model(anthropic.model())
                        .maxTokens(anthropic.maxTokens())
                        .build())
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
                elapsed,
                -1.0); // API 응답은 비용을 직접 주지 않는다
    }

    @Override
    public String provider() {
        return "anthropic";
    }
}
