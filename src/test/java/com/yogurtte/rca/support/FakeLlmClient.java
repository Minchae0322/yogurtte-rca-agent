package com.yogurtte.rca.support;

import com.yogurtte.rca.llm.LlmClient;
import com.yogurtte.rca.llm.LlmResult;

/** 건네받은 컨텍스트를 기록해서, 모델이 보게 될 내용을 테스트가 검증할 수 있게 한다. */
public class FakeLlmClient implements LlmClient {

    public String seenSystemPrompt;
    public String seenContext;

    @Override
    public LlmResult analyze(String systemPrompt, String context) {
        this.seenSystemPrompt = systemPrompt;
        this.seenContext = context;
        return new LlmResult("원인 후보 1: Kafka consumer lag", 1234, 567, 900, 300,
                "fake-model", 1, 42, 0.0123);
    }

    @Override
    public String provider() {
        return "fake";
    }
}
