package com.yogurtte.rca.llm;

/** provider가 usage를 주지 않으면 토큰 수는 -1이다. */
public record LlmResult(String text, long inputTokens, long outputTokens, long elapsedMs) {
}
