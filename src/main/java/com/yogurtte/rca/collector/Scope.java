package com.yogurtte.rca.collector;

import java.util.List;

/**
 * "무엇을 볼 것인가" — 탐색(triage)과 분석(rca)이 주고받는 값.
 *
 * <p><b>traceId는 필수가 아니다.</b> 컨슈머가 전멸하면 consume span 자체가 생성되지 않고,
 * 파드가 0이면 ingress가 끊겨 트레이스가 아예 만들어지지 않는다 (CH-2·AU-2). traceId를
 * 필수로 두면 그런 장애는 넘길 대상이 없어 파이프라인이 그 자리에서 끊긴다. 그래서 후단
 * 인터페이스는 {@code (시간창 + 대상 서비스 + traceId?)}이고 traceId는 있으면 딸려가는 값이다.
 *
 * @param window            조회 시간창. {@code null}이면 트레이스에서 파생한다(기존 v0 경로).
 * @param services          대상 서비스. 비어 있으면 설정된 전체 앱을 본다.
 * @param traceId           대표 트레이스. 없으면 트레이스 수집과 traceId 로그 조회를 건너뛴다.
 * @param candidateTraceIds 창 안의 다른 후보 트레이스(B-9). 선정 트레이스 <b>하나만으로는 정답
 *                          근거가 범위 밖에 남는 문항이 실재한다</b> — AU-2의 근거는 탐색이 고른
 *                          auth가 아니라 같은 창의 content 트레이스에 있었다. 서비스로 거르지
 *                          않는다(정답 서비스로 정확히 좁힌 문항에서도 다른 서비스 것이 필요했다).
 * @param windows           후보를 여러 개 골랐을 때의 <b>후보별 창</b>. 비어 있으면 {@code window}
 *                          하나를 쓴다.
 *                          <p>{@code window}는 이들의 <b>합집합</b>이라 사이의 빈 구간까지 덮는다 —
 *                          08:51 후보와 09:10 후보를 함께 고르면 23분이 되고 그중 15분은 아무
 *                          신호도 없는 구간이다. 창 확대는 점수를 올리지 못하면서 컨텍스트만
 *                          늘린다는 것이 실측돼 있다(2.26배 확장: Context +72% · 점수 불변).
 *                          <p><b>로그·트레이스만 이 목록으로 나눠 조회한다.</b> 둘은 점 사건이라
 *                          사이 구간에 정보가 없다. <b>메트릭은 합집합 창을 쓴다</b> — 시계열이
 *                          조각나면 "그 사이에 회복했는가"를 잃는다.
 */
public record Scope(TimeWindow window, List<String> services, String traceId,
                    List<String> candidateTraceIds, List<TimeWindow> windows) {

    public Scope {
        services = services == null ? List.of() : List.copyOf(services);
        candidateTraceIds = candidateTraceIds == null ? List.of() : List.copyOf(candidateTraceIds);
        windows = windows == null ? List.of() : List.copyOf(windows);
    }

    public Scope(TimeWindow window, List<String> services, String traceId,
                 List<String> candidateTraceIds) {
        this(window, services, traceId, candidateTraceIds, List.of());
    }

    public Scope(TimeWindow window, List<String> services, String traceId) {
        this(window, services, traceId, List.of(), List.of());
    }

    /** traceId 하나만 주는 기존 v0 진입점. 시간창은 트레이스에서 파생된다. */
    public static Scope ofTrace(String traceId) {
        return new Scope(null, List.of(), traceId);
    }

    public Scope withCandidates(List<String> candidateTraceIds) {
        return new Scope(window, services, traceId, candidateTraceIds, windows);
    }

    /** 후보별 창을 실어 보낸다. 하나뿐이면 합집합과 같으므로 굳이 나누지 않는다. */
    public Scope withWindows(List<TimeWindow> windows) {
        return new Scope(window, services, traceId, candidateTraceIds,
                windows == null || windows.size() < 2 ? List.of() : windows);
    }

    /** 로그·트레이스가 실제로 훑을 창 목록. 후보별 창이 없으면 합집합 하나. */
    public List<TimeWindow> logWindows(TimeWindow fallback) {
        return windows.isEmpty() ? List.of(fallback) : windows;
    }

    public boolean hasTraceId() {
        return traceId != null && !traceId.isBlank();
    }

    /** 원본 응답 파일명·MDC에 쓸 식별자. traceId가 없는 조사도 추적 가능해야 한다. */
    public String correlationId() {
        if (hasTraceId()) {
            return traceId;
        }
        return window == null ? "scan" : "scan-" + window.start().getEpochSecond();
    }
}
