package com.yogurtte.rca.analyzer;

public interface LlmClient {

    LlmResult analyze(String systemPrompt, String context);

    /** Provider id, recorded in the report. */
    String provider();
}
