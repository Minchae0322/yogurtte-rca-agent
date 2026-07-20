package com.yogurtte.rca.llm;

public interface LlmClient {

    LlmResult analyze(String systemPrompt, String context);

    /** 리포트에 기록되는 provider 식별자. */
    String provider();
}
