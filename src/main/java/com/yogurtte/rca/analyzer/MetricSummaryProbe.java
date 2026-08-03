package com.yogurtte.rca.analyzer;

/**
 * 패키지 밖(분석 도구)에서 메트릭 요약을 재보기 위한 창구.
 *
 * <p>{@code EvidenceExtractor.metricSummary}는 패키지 전용이다 — 컨텍스트 조립 외의 곳에서
 * 쓰이면 요약 형식이 여러 소비자에 물려 바꾸기 어려워진다. 크기 측정 도구만 이 창구로 연다.
 */
public final class MetricSummaryProbe {

    private MetricSummaryProbe() {
    }

    public static String summarize(String query, String body) {
        return EvidenceExtractor.metricSummary(query, body);
    }
}
