package com.yogurtte.rca.analyzer;

/** Token counts are -1 when the provider does not report usage. */
public record LlmResult(String text, long inputTokens, long outputTokens, long elapsedMs) {
}
