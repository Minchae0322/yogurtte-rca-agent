package com.yogurtte.rca.triage.incident;

/**
 * 신호의 시각을 얼마나 믿을 수 있나. 조사 창의 여유 폭이 여기서 나온다.
 *
 * <p>EXACT는 ms 단위로 정확한 시각(트레이스 span·로그 라인), BUCKET은 집계 해상도만큼
 * 흐릿한 시각(메트릭 샘플·로그 발생률 버킷)이다.
 */
public enum Precision { EXACT, BUCKET }
