package com.yogurtte.rca.report;

/** 단계별 소요 시간(밀리초). */
public record Timings(long tempoMs, long lokiMs, long mimirMs, long assembleMs, long llmMs) {
}
