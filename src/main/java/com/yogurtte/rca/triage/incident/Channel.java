package com.yogurtte.rca.triage.incident;

/**
 * 신호가 어느 관측원에서 왔나. 군집 키의 1축이다 — 지문의 성격이 채널마다 달라
 * (Tempo는 엔드포인트, Loki는 예외 클래스, Mimir는 지표) 섞으면 같은 사건의
 * 신호끼리도 지문이 달라 갈라진다.
 */
public enum Channel { TEMPO, LOKI, MIMIR }
