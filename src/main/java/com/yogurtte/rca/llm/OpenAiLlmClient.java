package com.yogurtte.rca.llm;

import java.util.List;

import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "rca.llm.provider", havingValue = "openai")
public class OpenAiLlmClient implements LlmClient {

    private final OpenAiChatModel chatModel;

    public OpenAiLlmClient(LlmProperties properties) {
        var openai = properties.openai();
        if (openai == null || openai.apiKey() == null || openai.apiKey().isBlank()) {
            throw new IllegalStateException("rca.llm.provider=openai requires OPENAI_API_KEY");
        }
        this.chatModel = OpenAiChatModel.builder()
                .openAiApi(OpenAiApi.builder().apiKey(openai.apiKey()).build())
                .defaultOptions(OpenAiChatOptions.builder().model(openai.model()).build())
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
        return "openai";
    }
}
