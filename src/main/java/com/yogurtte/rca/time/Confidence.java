package com.yogurtte.rca.time;

/**
 * 시간창을 어떤 확신으로 정했는지. FALLBACK 회차는 채점 집계에서 분리한다 (B-26) —
 * 리포트 {@code triage.timeConfidence}로 적재된다.
 */
public enum Confidence {

    /** 표현이 구간을 온전히 결정했다 (상대 표현 · 날짜 · 시간대 · 구간 · 명시적 from/to). */
    EXACT,

    /** 시각 하나에서 ±여유로 추정한 창이다. */
    APPROX,

    /** 시간 단서가 없어 기본 조회 폭으로 떨어졌다. */
    FALLBACK
}
