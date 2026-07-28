package com.yogurtte.rca.report;

import java.time.Instant;
import java.util.List;

/**
 * mode는 조사 과업 종류: "rca"(장애 원인 분석) 또는 "review"(정상 트레이스 성능 리뷰).
 * promptSource는 이 조사에 쓰인 시스템 프롬프트의 출처(외부 파일 경로 또는 classpath) - 프롬프트 튜닝 비교용.
 *
 * <p>측정 필드의 정의와 "어느 수치를 개선 지표로 쓰는가"는 {@code docs/measurement.md}에 있다.
 *
 * @param llmModel            응답을 만든 모델. 회차 간 점수 비교가 같은 조건이었음을 증명하는 근거다.
 * @param llmTurns            LLM 왕복 횟수. 1이 아니면 "도구 없는 단일 패스" 전제가 깨진 것이고,
 *                            usage가 마지막 턴만 담고 비용은 합계일 수 있어 토큰·비용 해석이 달라진다.
 * @param inputTokens         캐시 토큰까지 합산한 총 입력. <b>CLI 자체 오버헤드가 포함</b>되므로
 *                            "내가 만든 입력의 크기"가 아니다 - 개선 지표로는 coverage.contextTokens를 쓴다.
 * @param cacheReadTokens     inputTokens 중 캐시에서 읽은 몫(신규 입력의 약 1/10 값). 미보고 시 -1.
 * @param cacheCreationTokens inputTokens 중 캐시에 쓴 몫(5분 TTL 기준 1.25배). 미보고 시 -1.
 */
public record RcaReport(
        String traceId,
        String question,
        String mode,
        Instant startedAt,
        String llmProvider,
        String llmModel,
        int llmTurns,
        String promptSource,
        String analysis,
        long inputTokens,
        long outputTokens,
        long cacheReadTokens,
        long cacheCreationTokens,
        double costUsd,
        long totalElapsedMs,
        Timings timings,
        int contextChars,
        Coverage coverage,
        List<String> collectionFailures) {

    /**
     * 이번 조사가 실제로 "읽은 범위". 품질/토큰 개선을 측정하려면 모델이 무엇을 얼마나 봤는지가
     * 필요하므로 소스별 크기와 컨텍스트 규모를 함께 기록한다.
     *
     * @param traceTrimmed     트레이스가 크기 한도를 넘어 상위 span만 넣었는지
     * @param metricsBytes     메트릭 응답 JSON 크기. 이게 없으면 컨텍스트의 약 11%가 미분류로 남는다.
     * @param metricsCollected 시리즈가 잡힌 메트릭 쿼리
     * @param metricsMissing   비었거나 실패해 빠진 메트릭 쿼리
     * @param promptChars      시스템 프롬프트 길이. contextChars에 포함되지 않으므로 따로 센다.
     * @param contextTokens    <b>개선 지표.</b> 시스템 프롬프트 + 컨텍스트의 실측 토큰 수
     *                         ({@code /v1/messages/count_tokens}). CLI 오버헤드·캐시 상태·턴 수를
     *                         타지 않아 회차 간 비교가 성립하는 유일한 입력 수치다. 못 재면 -1.
     */
    public record Coverage(
            Instant windowStart,
            Instant windowEnd,
            long windowSeconds,
            int traceBytes,
            int traceSpans,
            boolean traceTrimmed,
            int errorWarnLogBytes,
            int traceIdLogBytes,
            int metricsBytes,
            List<String> metricsCollected,
            List<String> metricsMissing,
            int promptChars,
            int contextChars,
            long contextTokens) {
    }
}
