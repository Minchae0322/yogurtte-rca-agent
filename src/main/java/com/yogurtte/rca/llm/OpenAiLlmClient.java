package com.yogurtte.rca.llm;

import java.util.List;

import lombok.RequiredArgsConstructor;

import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;

/** 조립(설정 검증·ChatModel 생성)은 {@link LlmConfig}가 한다. */
@RequiredArgsConstructor
public class OpenAiLlmClient implements LlmClient {

    private final OpenAiChatModel chatModel;
    private final String model;

    @Override
    public LlmResult analyze(String systemPrompt, String context) {
        long started = System.currentTimeMillis();
        ChatResponse response = chatModel.call(new Prompt(List.of(new SystemMessage(systemPrompt), new UserMessage(context))));
        long elapsed = System.currentTimeMillis() - started;

        Usage usage = response.getMetadata().getUsage();
        return LlmResult.withoutCacheBreakdown(
                response.getResult().getOutput().getText(),
                usage == null || usage.getPromptTokens() == null ? -1 : usage.getPromptTokens(),
                usage == null || usage.getCompletionTokens() == null ? -1 : usage.getCompletionTokens(),
                model,
                elapsed,
                -1.0); // API 응답은 비용을 직접 주지 않는다
    }

    @Override
    public String provider() {
        return "openai";
    }
}
