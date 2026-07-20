package com.yogurtte.rca.report;

/** Per-stage wall clock, in milliseconds. */
public record Timings(long tempoMs, long lokiMs, long mimirMs, long assembleMs, long llmMs) {
}
