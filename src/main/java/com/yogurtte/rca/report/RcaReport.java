package com.yogurtte.rca.report;

import java.time.Instant;
import java.util.List;

/**
 * mode는 조사 과업 종류: "rca"(장애 원인 분석) 또는 "review"(정상 트레이스 성능 리뷰).
 * promptSource는 이 조사에 쓰인 시스템 프롬프트의 출처(외부 파일 경로 또는 classpath) - 프롬프트 튜닝 비교용.
 *
 * <p>측정 필드의 정의와 "어느 수치를 개선 지표로 쓰는가"는 {@code docs/measurement.md}에 있다.
 *
 * @param llmModel            응답을 만든 모델. 회차 간 점수 비교가 같은 조건이었음을 증명하는 근거다.
 * @param llmTurns            LLM 왕복 횟수. 1이 아니면 "도구 없는 단일 패스" 전제가 깨진 것이고,
 *                            usage가 마지막 턴만 담고 비용은 합계일 수 있어 토큰·비용 해석이 달라진다.
 * @param inputTokens         캐시 토큰까지 합산한 총 입력. <b>CLI 자체 오버헤드가 포함</b>되므로
 *                            "내가 만든 입력의 크기"가 아니다 - 개선 지표로는 coverage.contextTokens를 쓴다.
 * @param cacheReadTokens     inputTokens 중 캐시에서 읽은 몫(신규 입력의 약 1/10 값). 미보고 시 -1.
 * @param cacheCreationTokens inputTokens 중 캐시에 쓴 몫(5분 TTL 기준 1.25배). 미보고 시 -1.
 */
public record RcaReport(
        String traceId,
        String question,
        String mode,
        Instant startedAt,
        String llmProvider,
        String llmModel,
        int llmTurns,
        String promptSource,
        String analysis,
        long inputTokens,
        long outputTokens,
        long cacheReadTokens,
        long cacheCreationTokens,
        double costUsd,
        long totalElapsedMs,
        Timings timings,
        int contextChars,
        Coverage coverage,
        Triage triage,
        Evidence evidence,
        ServiceGraph serviceGraph,
        List<String> collectionFailures) {

    /**
     * 1~2단계(스윕·선정)의 기록. traceId로 바로 들어온 조사는 {@code null}이다.
     *
     * <p><b>분석 점수와 분리해서 남긴다.</b> 한 점수에 합치면 결과가 나빴을 때 *못 찾은 것*인지
     * *찾고도 못 푼 것*인지 알 수 없어 무엇을 고칠지 정할 수 없다. 탐색이 틀렸을 때도 분석은
     * "에이전트가 실제로 고른 범위"로 채점되어야 하므로, 여기에 그 선택의 근거를 통째로 남긴다.
     *
     * @param timeExpression   자연어 시간 표현을 어떻게 창으로 바꿨는지 (결정적 파싱의 근거)
     * @param timeConfidence   창 확신도(EXACT/APPROX/FALLBACK) — FALLBACK으로 떨어진 회차를
     *                         분리 집계하기 위한 구조화 값. 문자열 매칭으로는 집계가 안 됐다(B-26).
     * @param traceCandidates  스윕이 찾은 트레이스 전부. 고른 것 말고 <b>무엇이 더 있었는지</b>가
     *                         회고에서 "다른 걸 골랐어야 했나"를 판단하는 근거가 된다.
     * @param planParsed       LLM이 낸 계획을 실제로 읽어냈는지. false면 스윕 창을 그대로 쓴 것이다.
     * @param analysis         탐색 단계 LLM 응답 원문
     */
    public record Triage(
            String timeExpression,
            String timeConfidence,
            Instant surveyStart,
            Instant surveyEnd,
            Instant chosenStart,
            Instant chosenEnd,
            List<String> services,
            String traceId,
            List<Evidence.TraceHit> traceCandidates,
            String reason,
            List<String> evidence,
            boolean planParsed,
            List<String> notes,
            String promptSource,
            String analysis,
            long inputTokens,
            long outputTokens,
            double costUsd,
            int contextChars,
            int promptChars,
            long surveyMs,
            long llmMs,
            List<String> surveyFailures,
            List<String> incidentCandidates,
            List<String> chosenIncidentIds,
            List<String> dismissedIncidentIds) {

        public Triage {
            incidentCandidates = incidentCandidates == null ? List.of() : List.copyOf(incidentCandidates);
            chosenIncidentIds = chosenIncidentIds == null ? List.of() : List.copyOf(chosenIncidentIds);
            dismissedIncidentIds = dismissedIncidentIds == null ? List.of() : List.copyOf(dismissedIncidentIds);
        }
    }

    /**
     * 이번 조사가 실제로 "읽은 범위". 품질/토큰 개선을 측정하려면 모델이 무엇을 얼마나 봤는지가
     * 필요하므로 소스별 크기와 컨텍스트 규모를 함께 기록한다.
     *
     * @param traceTrimmed     트레이스가 크기 한도를 넘어 상위 span만 넣었는지
     * @param metricsBytes     메트릭 응답 JSON 크기. 이게 없으면 컨텍스트의 약 11%가 미분류로 남는다.
     * @param metricsCollected 시리즈가 잡힌 메트릭 쿼리
     * @param metricsMissing   비었거나 실패해 빠진 메트릭 쿼리
     * @param promptChars      시스템 프롬프트 길이. contextChars에 포함되지 않으므로 따로 센다.
     * @param contextTokens    시스템 프롬프트 + 컨텍스트의 실측 토큰 수
     *                         ({@code /v1/messages/count_tokens}). 못 재면 -1 —
     *                         <b>구독 CLI 경로에서는 API 키가 없어 항상 -1이다.</b>
     * @param overheadTokens   <b>이 회차의</b> provider 고정 오버헤드 실측(1자 프롬프트 프로브). 못 재면 -1.
     *                         이 값이 있으면 {@code inputTokens − overheadTokens}가 개선 지표가 되고,
     *                         <b>다른 날 상수에 기대지 않는 유일한 경로</b>다 — 오버헤드는 하루 만에
     *                         20% 움직인 적이 있어 문서에 박아둔 값을 소급해 빼면 틀린다.
     */
    public record Coverage(
            Instant windowStart,
            Instant windowEnd,
            long windowSeconds,
            int traceBytes,
            int traceSpans,
            boolean traceTrimmed,
            int errorWarnLogBytes,
            int traceIdLogBytes,
            int metricsBytes,
            List<String> metricsCollected,
            List<String> metricsMissing,
            int promptChars,
            int contextChars,
            long contextTokens,
            long overheadTokens) {
    }
}
