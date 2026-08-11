package com.yogurtte.rca.collector;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * 창 분할의 목적은 후보 사이의 <b>빈 구간</b>을 안 긁는 것이다. 같은 구간을 나눠 긁으면
 * 목적과 정반대가 된다 — AP-1 회차 3에서 로그를 3번 조회해 38.6KB가 107KB가 됐다.
 */
class ScopeWindowsTest {

    private static TimeWindow at(String start, String end) {
        return new TimeWindow(Instant.parse(start), Instant.parse(end));
    }

    private static final TimeWindow UNION = at("2026-08-03T06:30:00Z", "2026-08-03T06:45:00Z");

    private static Scope scope(List<TimeWindow> windows) {
        return new Scope(UNION, List.of(), List.of("abc123")).withWindows(windows);
    }

    @Test
    void 같은_창이_여러_개면_하나로_접혀_분할하지_않는다() {
        // AP-1 회차 3의 실제 형태 — 후보 셋 중 둘이 같은 5분 버킷이라 창이 완전히 같아졌고
        // 셋째는 그 부분집합이었다.
        Scope scope = scope(List.of(
                at("2026-08-03T06:30:00Z", "2026-08-03T06:41:15Z"),
                at("2026-08-03T06:30:00Z", "2026-08-03T06:41:15Z"),
                at("2026-08-03T06:36:12Z", "2026-08-03T06:40:25Z")));

        assertThat(scope.windows()).isEmpty();
        assertThat(scope.logWindows(UNION)).containsExactly(UNION);
    }

    @Test
    void 겹치는_창은_이어붙인다() {
        Scope scope = scope(List.of(
                at("2026-08-03T06:30:00Z", "2026-08-03T06:36:00Z"),
                at("2026-08-03T06:35:00Z", "2026-08-03T06:40:00Z")));

        assertThat(scope.windows()).isEmpty();
    }

    @Test
    void 떨어진_창은_그대로_나눈다() {
        // 이것이 분할이 값을 하는 경우다 — 사이 8분은 신호가 없어 긁을 이유가 없다.
        Scope scope = scope(List.of(
                at("2026-08-03T06:30:00Z", "2026-08-03T06:32:00Z"),
                at("2026-08-03T06:40:00Z", "2026-08-03T06:42:00Z")));

        assertThat(scope.windows()).hasSize(2);
        assertThat(scope.logWindows(UNION)).hasSize(2);
    }

    @Test
    void 셋_중_둘만_겹치면_둘로_접힌다() {
        Scope scope = scope(List.of(
                at("2026-08-03T06:30:00Z", "2026-08-03T06:33:00Z"),
                at("2026-08-03T06:32:00Z", "2026-08-03T06:35:00Z"),
                at("2026-08-03T06:44:00Z", "2026-08-03T06:45:00Z")));

        assertThat(scope.windows()).containsExactly(
                at("2026-08-03T06:30:00Z", "2026-08-03T06:35:00Z"),
                at("2026-08-03T06:44:00Z", "2026-08-03T06:45:00Z"));
    }

    private static final TimeWindow CH3_SWEEP = at("2026-08-06T00:36:05Z", "2026-08-06T01:36:05Z");

    /**
     * CH-3(scan-1785976661 · 2026-08-11) 실제 후보 6건을 <b>전 채널 pad 2분</b>으로 계산했을 때.
     *
     * <p>두 사건 사이가 정확히 4분이고 pad가 양쪽에서 2분씩이라 <b>맞닿아 하나로 접힌다</b> —
     * 분할이 걸리지 않았고, 창은 22분 19초였다. 원본 응답에 {@code -w1}·{@code -w2} 접미가
     * 없는 것이 그 기록이다.
     */
    @Test
    void 옛_pad로는_두_사건이_맞닿아_하나로_접혔다() {
        Scope scope = new Scope(CH3_SWEEP, List.of(), List.of()).withWindows(List.of(
                at("2026-08-06T00:37:41.076260Z", "2026-08-06T00:50:16.226260Z"), // INC-2 트레이스
                at("2026-08-06T00:45:00Z", "2026-08-06T00:51:00Z"),               // INC-3 로그
                at("2026-08-06T00:45:44.632550Z", "2026-08-06T00:50:10.018900Z"), // INC-4 트레이스
                at("2026-08-06T00:51:00Z", "2026-08-06T01:00:00Z"),               // INC-6 로그
                at("2026-08-06T00:51:34.957148Z", "2026-08-06T00:55:59.030148Z"), // INC-8 트레이스 (정답)
                at("2026-08-06T00:51:42.241455Z", "2026-08-06T00:55:58.120455Z"))); // INC-9 트레이스

        assertThat(scope.windows()).isEmpty();
        assertThat(scope.logWindows(CH3_SWEEP)).containsExactly(CH3_SWEEP);
    }

    /**
     * 같은 후보 6건을 <b>채널별 pad</b>(TEMPO 0 · LOKI 0 · MIMIR만 여유)로 계산했을 때.
     *
     * <p>여기 여섯은 트레이스와 로그뿐이라 pad가 전부 0이고, 두 사건이 <b>4분 떨어진 채로
     * 남아 둘로 갈린다.</b> 실조회 구간이 22분 19초 → <b>14분 19초</b>가 된다.
     */
    @Test
    void 채널별_pad로는_두_사건이_둘로_갈린다() {
        Scope scope = new Scope(CH3_SWEEP, List.of(), List.of()).withWindows(List.of(
                at("2026-08-06T00:39:41.076260Z", "2026-08-06T00:48:16.226260Z"), // INC-2
                at("2026-08-06T00:47:00Z", "2026-08-06T00:49:00Z"),               // INC-3
                at("2026-08-06T00:47:44.632550Z", "2026-08-06T00:48:10.018900Z"), // INC-4
                at("2026-08-06T00:53:00Z", "2026-08-06T00:58:00Z"),               // INC-6
                at("2026-08-06T00:53:34.957148Z", "2026-08-06T00:53:59.030148Z"), // INC-8 (정답)
                at("2026-08-06T00:53:42.241455Z", "2026-08-06T00:53:58.120455Z"))); // INC-9

        assertThat(scope.windows()).containsExactly(
                at("2026-08-06T00:39:41.076260Z", "2026-08-06T00:49:00Z"),
                at("2026-08-06T00:53:00Z", "2026-08-06T00:58:00Z"));
    }

    @Test
    void 정렬되지_않은_입력도_접는다() {
        Scope scope = scope(List.of(
                at("2026-08-03T06:40:00Z", "2026-08-03T06:45:00Z"),
                at("2026-08-03T06:30:00Z", "2026-08-03T06:41:00Z")));

        assertThat(scope.windows()).isEmpty();
    }
}
