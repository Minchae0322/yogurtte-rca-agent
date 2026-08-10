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

    /**
     * 탐색 단계 Tempo 검색이 찾은 트레이스.
     *
     * @param channel 어느 채널로 도달했는지. {@code error} 또는 {@code slow}.
     *                <b>이것 자체가 장애 성격이다</b> — "에러로는 안 잡히는데 지연으로 잡혔다"가
     *                200 성공 + 지연 장애(CH-3)의 지문이다. 그래서 두 채널을 단일 쿼리로 합치지 않는다.
     * @param startedAt 트레이스 시작 시각. Tempo가 {@code startTimeUnixNano == 0}을 내려주는
     *                  경우가 있어 {@code null}일 수 있다.
     * @param trusted 값을 믿을 수 있는지. Tempo {@code /api/search}가 {@code durationMs} 33일짜리
     *                행이나 시작 시각 0인 행을 그대로 내려준 사례가 있다(CH-2 실측). 후보를 여러 건
     *                모아 정렬하면 <b>그 행이 항상 1위</b>가 되므로 표기해 두고 정렬에서 뺀다.
     * @param serviceStats 트레이스가 지나간 <b>서비스 전부</b>와 서비스별 span 수 — 이미 렌더된
     *                문자열이다 (예: {@code "chat-service 14 (err 1)"}). <b>루트만으로는 상류가
     *                안 보인다</b>: {@code content → kafka → chat} 트레이스도 검색 목록에는
     *                루트 하나만 뜨고, 탐색 단계는 span 원문을 받지 않아 되짚을 방법이 없다.
     *                Tempo {@code /api/search} 응답의 {@code serviceStats}가 이 값을 이미 주고
     *                있었고 파싱만 안 하고 있었다 (저장 응답 645건 중 52건이 2개 이상).
     */
    public record TraceHit(
            String traceId,
            String rootServiceName,
            String rootTraceName,
            long durationMs,
            String channel,
            Instant startedAt,
            boolean trusted,
            List<String> serviceStats) {

        public static final String CHANNEL_ERROR = "error";
        public static final String CHANNEL_SLOW = "slow";

        public TraceHit {
            channel = (channel == null || channel.isBlank()) ? CHANNEL_ERROR : channel;
            serviceStats = serviceStats == null ? List.of() : List.copyOf(serviceStats);
        }

        public TraceHit(String traceId, String rootServiceName, String rootTraceName, long durationMs,
                        String channel, Instant startedAt, boolean trusted) {
            this(traceId, rootServiceName, rootTraceName, durationMs, channel, startedAt, trusted, List.of());
        }

        /** 서비스가 둘 이상일 때만 쓸 값. 하나뿐이면 루트 이름이 이미 말하고 있다. */
        public String crossServiceText() {
            return serviceStats.size() < 2 ? "" : String.join(" · ", serviceStats);
        }
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
