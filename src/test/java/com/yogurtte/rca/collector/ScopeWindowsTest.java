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

    @Test
    void 정렬되지_않은_입력도_접는다() {
        Scope scope = scope(List.of(
                at("2026-08-03T06:40:00Z", "2026-08-03T06:45:00Z"),
                at("2026-08-03T06:30:00Z", "2026-08-03T06:41:00Z")));

        assertThat(scope.windows()).isEmpty();
    }
}
