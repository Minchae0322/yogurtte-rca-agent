package com.yogurtte.rca.report;

import java.time.Instant;
import java.util.List;

/**
 * 이 조사가 <b>실제로 본 것</b>. 회고를 위해 존재한다.
 *
 * <p>{@link RcaReport.Coverage}가 "얼마나 봤나"(바이트·건수)라면 이쪽은 "무엇을 봤나"다.
 * 리포트에 LLM의 서술만 남으면 나중에 그 서술이 맞았는지 확인할 방법이 없다 — 모델이
 * 바꿔 쓴 문장이 아니라 <b>관측값 원문</b>이 함께 있어야 채점도 회고도 성립한다.
 *
 * @param rawPrefix        원본 응답 파일 접두사. {@code reports/raw/{rawPrefix}-*.json}에 전부 있다.
 * @param logLinesTotal    수집된 전체 줄 수. {@code logSamples}는 그중 일부다.
 * @param logSamples       실제 로그 원문. 정규화하지 않고 그대로 싣는다.
 * @param metrics          시계열 요약. 값의 범위와 <b>0으로 꺾인 구간</b>을 함께 남긴다 —
 *                         부재가 결정적 신호인 장애가 실재하기 때문이다.
 */
public record Evidence(
        String investigatedTraceId,
        String rawPrefix,
        int spanCount,
        List<SpanRecord> topSpans,
        int logLinesTotal,
        List<LogLine> logSamples,
        List<MetricSeries> metrics) {

    public Evidence {
        topSpans = topSpans == null ? List.of() : List.copyOf(topSpans);
        logSamples = logSamples == null ? List.of() : List.copyOf(logSamples);
        metrics = metrics == null ? List.of() : List.copyOf(metrics);
    }

    /** 탐색 단계 Tempo 검색이 찾은 트레이스. */
    public record TraceHit(String traceId, String rootServiceName, String rootTraceName, long durationMs) {
    }

    public record SpanRecord(String service, String name, double durationMs, Instant startedAt) {
    }

    public record LogLine(Instant at, String service, String line) {
    }

    /**
     * @param series    라벨 집합 (예: {@code {job="chat-service"}})
     * @param zeroSpans 값이 0이었던 구간. {@code up}이 0으로 꺾인 것이 트레이스 무신호 장애에서
     *                  유일한 도달 경로였던 회차가 있다.
     */
    public record MetricSeries(
            String query,
            String series,
            int points,
            Instant firstAt,
            Instant lastAt,
            double min,
            double max,
            double last,
            List<String> zeroSpans) {

        public MetricSeries {
            zeroSpans = zeroSpans == null ? List.of() : List.copyOf(zeroSpans);
        }
    }
}
