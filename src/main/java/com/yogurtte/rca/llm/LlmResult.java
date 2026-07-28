package com.yogurtte.rca.llm;

/**
 * provider가 usage를 주지 않으면 토큰 수는 -1이다.
 * costUsd는 provider가 비용을 보고할 때만 채워지고(예: claude CLI), 아니면 -1이다.
 *
 * <p>{@code inputTokens}는 캐시 토큰(cache read/creation)까지 합산한 실제 입력량이고,
 * {@code cacheReadTokens}·{@code cacheCreationTokens}는 그 <b>내역</b>이다. 합산만 남기면
 * 총량 회계는 맞지만 캐시 히트율이 보이지 않는다 — cache read는 신규 입력의 약 1/10 값이라
 * 같은 {@code inputTokens}라도 비용이 열 배 넘게 갈린다. 2026-07-28 회차 비교에서 실제로
 * "컨텍스트가 가장 큰 회차가 가장 쌌다"를 설명하지 못해 막혔다. 내역을 주지 않는 provider는 -1.
 *
 * <p>{@code model}·{@code numTurns}는 "무엇이 이 답을 만들었나"의 기록이다. claude CLI는
 * 모델을 고정하지 않으면 그날 기본값으로 돌고, 도구 루프가 돌면 턴이 여러 번이다. 둘 다
 * 기록하지 않으면 회차 간 점수 비교가 같은 조건이었는지를 <b>사후에 증명할 수 없다</b>.
 */
public record LlmResult(
        String text,
        long inputTokens,
        long outputTokens,
        long cacheReadTokens,
        long cacheCreationTokens,
        String model,
        int numTurns,
        long elapsedMs,
        double costUsd) {

    /**
     * 캐시 내역을 보고하지 않는 provider(Spring AI SDK 경로)용. 이 경로는 요청 1회 = 응답 1회라
     * 턴 수가 1로 확정되지만, 캐시 분해는 알 수 없어 -1로 둔다.
     */
    public static LlmResult withoutCacheBreakdown(
            String text, long inputTokens, long outputTokens, String model, long elapsedMs, double costUsd) {
        return new LlmResult(text, inputTokens, outputTokens, -1, -1, model, 1, elapsedMs, costUsd);
    }
}
