package com.yogurtte.rca.collector;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * B-55 — 후보 슬롯을 창별로 배분한다.
 *
 * <p>고치기 전에는 선착순이라 <b>첫 창이 예산을 다 먹으면 뒤 창은 검색조차 안 나갔다.</b>
 * AP-2 회차 6에서 실제로 그렇게 됐다 — w1(AP-1의 창)이 슬롯 4개를 다 채워서
 * w2(팔로우 NPE, 조사 대상 그 자체)의 트레이스가 하나도 안 실렸고, 리포트가
 * <i>"Tempo 수집 10건에 팔로우 traceId가 하나도 없다"</i> 고 적은 채 근거 항목에서 7점을 잃었다.
 */
class CandidateInterleaveTest {

    @Test
    void 첫_창이_예산을_다_먹지_않는다() {
        // AP-2 회차 6 재현: w1이 20건을 던지고 슬롯은 4개뿐이었다.
        List<String> w1 = List.of("n1", "n2", "n3", "n4", "n5", "n6");
        List<String> w2 = List.of("follow1", "follow2");

        var picked = Collector.interleave(List.of(w1, w2), 4);

        assertThat(picked).containsExactly("n1", "follow1", "n2", "follow2");
    }

    @Test
    void 짧은_창이_소진되면_남은_창이_슬롯을_채운다() {
        // 창마다 공평하게 주되 슬롯을 놀리지는 않는다.
        List<String> w1 = List.of("a1", "a2", "a3", "a4");
        List<String> w2 = List.of("b1");

        var picked = Collector.interleave(List.of(w1, w2), 4);

        assertThat(picked).containsExactly("a1", "b1", "a2", "a3");
    }

    @Test
    void 후보가_슬롯보다_적으면_있는_만큼만() {
        var picked = Collector.interleave(List.of(List.of("a"), List.of("b")), 10);

        assertThat(picked).containsExactly("a", "b");
    }

    @Test
    void 창이_하나면_순서를_그대로_둔다() {
        // 관련도 정렬은 별개 항목(B-38)이다. 여기서 같이 건드리면 델타를 못 가린다.
        var picked = Collector.interleave(List.of(List.of("a", "b", "c")), 2);

        assertThat(picked).containsExactly("a", "b");
    }

    @Test
    void 창이_전부_비어도_멈춘다() {
        assertThat(Collector.interleave(List.of(List.of(), List.of()), 5)).isEmpty();
        assertThat(Collector.interleave(List.of(), 5)).isEmpty();
    }

    @Test
    void 창을_가로질러_중복된_traceId는_한_번만() {
        var picked = Collector.interleave(List.of(List.of("dup", "a"), List.of("dup", "b")), 3);

        assertThat(picked).containsExactly("dup", "a", "b");
    }
}
