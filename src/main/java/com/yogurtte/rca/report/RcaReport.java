package com.yogurtte.rca.report;

import java.time.Instant;
import java.util.List;

/**
 * mode는 조사 과업 종류: "rca"(장애 원인 분석) 또는 "review"(정상 트레이스 성능 리뷰).
 * promptSource는 이 조사에 쓰인 시스템 프롬프트의 출처(외부 파일 경로 또는 classpath) - 프롬프트 튜닝 비교용.
 */
public record RcaReport(
        String traceId,
        String question,
        String mode,
        Instant startedAt,
        String llmProvider,
        String promptSource,
        String analysis,
        long inputTokens,
        long outputTokens,
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
     * @param traceTrimmed        트레이스가 크기 한도를 넘어 상위 span만 넣었는지
     * @param metricsCollected    시리즈가 잡힌 메트릭 쿼리
     * @param metricsMissing      비었거나 실패해 빠진 메트릭 쿼리
     * @param estimatedContextTokens 컨텍스트 문자 수 기반 대략적 입력 토큰 추정치(provider usage와 대조용)
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
            List<String> metricsCollected,
            List<String> metricsMissing,
            int contextChars,
            long estimatedContextTokens) {
    }
}
