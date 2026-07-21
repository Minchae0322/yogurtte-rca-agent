package com.yogurtte.rca.llm;

/**
 * provider가 usage를 주지 않으면 토큰 수는 -1이다.
 *
 * <p>inputTokens는 캐시 토큰(cache read/creation)까지 합산한 실제 입력량이다.
 * costUsd는 provider가 비용을 보고할 때만 채워지고(예: claude CLI), 아니면 -1이다.
 */
public record LlmResult(String text, long inputTokens, long outputTokens, long elapsedMs, double costUsd) {
}
