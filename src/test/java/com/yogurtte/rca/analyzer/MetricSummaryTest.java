package com.yogurtte.rca.analyzer;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * B-25 메트릭 요약 — <b>크기를 줄이는 것이 목적이지만 판단 재료를 잃으면 실패다.</b>
 *
 * <p>여기 박은 요건은 지금까지 회차들이 실제로 인용한 것들이다: 값이 0으로 꺾인 구간
 * (프로세스 사망이 유일한 신호였다) · 시계열 결측(auth 다운의 근거) · 전 구간 상수
 * (에이전트가 "변화 없으므로 무관"으로 기각하는 근거) · 최댓값 시각(lag 정점).
 */
class MetricSummaryTest {

    @Test
    void 값이_0으로_꺾인_구간이_남는다() {
        // mongodb_up이 두 스텝 동안 0 — CH-1·CH-3에서 이게 결정적 신호였다.
        String body = matrix("{\"__name__\":\"mongodb_up\",\"instance\":\"mongo-0\"}",
                "[1785251100,\"1\"],[1785251115,\"0\"],[1785251130,\"0\"],[1785251145,\"1\"]");

        String summary = EvidenceExtractor.metricSummary("mongodb_up", body);

        assertThat(summary).contains("0이던 구간: 2026-07-28T15:05:15Z ~ 2026-07-28T15:05:30Z");
        assertThat(summary).contains("min 0").contains("max 1");
        assertThat(summary).contains("instance=mongo-0");
    }

    @Test
    void 시계열이_끊긴_구간이_결측으로_남는다() {
        // auth 파드가 사라지면 스크레이프 대상이 없어 샘플이 통째로 빈다 — AU-2의 정답 근거.
        String body = matrix("{\"job\":\"auth-service\"}",
                "[1785251100,\"1\"],[1785251115,\"1\"],[1785251130,\"1\"],[1785251700,\"1\"]");

        String summary = EvidenceExtractor.metricSummary("up", body);

        assertThat(summary).contains("결측(샘플 없음): 2026-07-28T15:05:30Z ~ 2026-07-28T15:15:00Z");
    }

    @Test
    void 전_구간_상수는_한_줄로_접힌다() {
        // "hikaricp pending 전 구간 0" 같은 기각 근거. 표본을 늘어놓을 이유가 없다.
        String body = matrix("{\"application\":\"content-service\"}",
                "[1785251100,\"0\"],[1785251115,\"0\"],[1785251130,\"0\"],[1785251145,\"0\"]");

        String summary = EvidenceExtractor.metricSummary("hikaricp_connections_pending", body);

        assertThat(summary).contains("전 구간 0 (변화 없음)");
        assertThat(summary).doesNotContain("표본");
        assertThat(summary.lines().count()).isEqualTo(1);
    }

    @Test
    void 곡선의_정점과_모양이_남는다() {
        // lag가 쌓였다 회복하는 곡선 — 최댓값과 그 시각, 그리고 모양이 필요하다.
        String body = matrix("{\"topic\":\"user.notifications\"}",
                "[1785251100,\"0\"],[1785251115,\"12\"],[1785251130,\"25\"],[1785251145,\"7\"],[1785251160,\"0\"]");

        String summary = EvidenceExtractor.metricSummary("kafka_consumergroup_lag", body);

        assertThat(summary).contains("max 25 (2026-07-28T15:05:30Z)");
        assertThat(summary).contains("처음 0").contains("마지막 0");
        assertThat(summary).contains("=25");   // 정점이 표본에 살아 있다
    }

    @Test
    void 시리즈가_많아도_전부_남긴다() {
        // 어느 파티션만 밀렸는지가 신호다 — 시리즈를 자르면 그 구별이 사라진다.
        String body = "{\"status\":\"success\",\"data\":{\"resultType\":\"matrix\",\"result\":["
                + series("{\"partition\":\"0\"}", "[1785251100,\"0\"],[1785251115,\"1\"]") + ","
                + series("{\"partition\":\"1\"}", "[1785251100,\"0\"],[1785251115,\"9\"]") + "]}}";

        String summary = EvidenceExtractor.metricSummary("kafka_consumergroup_lag", body);

        assertThat(summary).contains("partition=0").contains("partition=1");
    }

    @Test
    void 깨진_응답이나_빈_결과에도_설명이_남는다() {
        assertThat(EvidenceExtractor.metricSummary("up", null)).contains("시리즈 없음");
        assertThat(EvidenceExtractor.metricSummary("up", "깨진 json {")).contains("시리즈 없음");
        assertThat(EvidenceExtractor.metricSummary("up",
                "{\"status\":\"success\",\"data\":{\"result\":[]}}")).contains("시리즈 없음");
    }

    @Test
    void 요약이_원본보다_확실히_작다() {
        StringBuilder points = new StringBuilder();
        for (int i = 0; i < 45; i++) {
            points.append(i > 0 ? "," : "").append("[").append(1785251100 + i * 15L).append(",\"").append(i).append("\"]");
        }
        String body = matrix("{\"job\":\"content-service\",\"instance\":\"10.42.0.15:8080\"}", points.toString());

        String summary = EvidenceExtractor.metricSummary("up", body);

        assertThat(summary.length()).isLessThan(body.length() / 2);
    }

    private static String matrix(String labels, String points) {
        return "{\"status\":\"success\",\"data\":{\"resultType\":\"matrix\",\"result\":["
                + series(labels, points) + "]}}";
    }

    private static String series(String labels, String points) {
        return "{\"metric\":" + labels + ",\"values\":[" + points + "]}";
    }
}
