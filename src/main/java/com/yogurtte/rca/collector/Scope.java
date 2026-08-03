package com.yogurtte.rca.collector;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * "무엇을 볼 것인가" — 탐색(triage)과 분석(rca)이 주고받는 값.
 *
 * <p><b>traceId는 필수가 아니다.</b> 컨슈머가 전멸하면 consume span 자체가 생성되지 않고,
 * 파드가 0이면 ingress가 끊겨 트레이스가 아예 만들어지지 않는다 (CH-2·AU-2). traceId를
 * 필수로 두면 그런 장애는 넘길 대상이 없어 파이프라인이 그 자리에서 끊긴다.
 *
 * @param window   조회 시간창. {@code null}이면 트레이스에서 파생한다(기존 v0 경로).
 * @param services 대상 서비스. 비어 있으면 설정된 전체 앱을 본다.
 * @param traceIds 조사할 트레이스 <b>목록</b>. 앞쪽이 탐색이 지목한 것, 뒤가 창 안 후보다.
 *                 <p><b>대표를 뽑지 않는다.</b> 예전에는 첫 번째를 대표로 세워
 *                 {@code # 트레이스 (Tempo)} 절을 혼자 차지하게 했는데, <b>그 "첫 번째"에는
 *                 아무 근거가 없었다</b> — 신호가 만들어진 순서일 뿐이라 duration도 에러 유무도
 *                 보지 않았다. AP-1 회차 3에서 한 후보가 트레이스 둘(11.6초 지연 200 ·
 *                 308ms 실패 500)을 물었을 때 <b>성공 트레이스가 대표가 됐고</b>, 분석이 그
 *                 지연을 별건으로 따로 다루느라 진짜 원인이 랭킹 2순위로 밀렸다.
 *                 <p>어느 것이 원인인지는 <b>전문을 봐야 안다.</b> 그러니 고르지 말고 다 준다 —
 *                 고르는 일은 탐색 LLM이 후보 단계에서 이미 했다.
 * @param windows  후보별 창. 비어 있으면 {@code window} 하나를 쓴다. 겹치는 것은 접힌다.
 *                 <p><b>로그·트레이스만 이 목록으로 나눠 조회한다.</b> 둘은 점 사건이라 사이
 *                 구간에 정보가 없다. <b>메트릭은 합집합 창</b> — 시계열이 조각나면
 *                 "그 사이에 회복했는가"를 잃는다.
 */
public record Scope(TimeWindow window, List<String> services, List<String> traceIds,
                    List<TimeWindow> windows) {

    public Scope {
        services = services == null ? List.of() : List.copyOf(services);
        traceIds = traceIds == null ? List.of()
                : List.copyOf(new LinkedHashSet<>(traceIds.stream()
                        .filter(id -> id != null && !id.isBlank()).toList()));
        windows = windows == null ? List.of() : List.copyOf(windows);
    }

    public Scope(TimeWindow window, List<String> services, List<String> traceIds) {
        this(window, services, traceIds, List.of());
    }

    /** traceId 하나만 주는 기존 v0 진입점. 시간창은 트레이스에서 파생된다. */
    public static Scope ofTrace(String traceId) {
        return new Scope(null, List.of(), List.of(traceId));
    }

    /** 탐색이 지목한 것 뒤에 창 안 후보를 잇는다. 앞쪽이 우선순위다. */
    public Scope withCandidates(List<String> candidateTraceIds) {
        ArrayList<String> merged = new ArrayList<>(traceIds);
        if (candidateTraceIds != null) {
            merged.addAll(candidateTraceIds);
        }
        return new Scope(window, services, merged, windows);
    }

    /** 후보별 창을 실어 보낸다. 겹치는 것을 접고, 남은 것이 하나면 나누지 않는다. */
    public Scope withWindows(List<TimeWindow> windows) {
        return new Scope(window, services, traceIds, mergeOverlapping(windows));
    }

    public boolean hasTraceIds() {
        return !traceIds.isEmpty();
    }

    /**
     * 원본 응답 파일명·MDC·리포트에 쓸 식별자.
     *
     * <p>창이 없는 것은 <b>traceId로 직접 들어온 v0 경로</b>뿐이고 그때는 그 traceId가 곧
     * 조사 이름이다. 탐색을 거친 조사는 트레이스가 몇 건이든 <b>창 기준 이름</b>을 쓴다 —
     * 그 조사는 트레이스 하나에 관한 것이 아니다.
     */
    public String correlationId() {
        if (window == null) {
            return traceIds.isEmpty() ? "scan" : traceIds.get(0);
        }
        return "scan-" + window.start().getEpochSecond();
    }

    /** 로그·트레이스가 실제로 훑을 창 목록. 후보별 창이 없으면 합집합 하나. */
    public List<TimeWindow> logWindows(TimeWindow fallback) {
        return windows.isEmpty() ? List.of(fallback) : windows;
    }

    /**
     * 겹치거나 맞닿은 창을 하나로 접는다. <b>남은 것이 하나뿐이면 빈 목록</b>이라 분할하지 않는다.
     *
     * <p><b>접지 않으면 같은 구간을 여러 번 긁는다.</b> AP-1 회차 3에서 실제로 그랬다 — 후보
     * 셋 중 둘이 같은 5분 버킷이라 padding 후 창이 완전히 같아졌고, 셋째는 그 부분집합이었다.
     * 로그를 3번 조회해 38.6KB면 될 것이 <b>107KB</b>가 됐다. 창 분할의 목적은 후보 사이의
     * <b>빈 구간</b>을 안 긁는 것이지 같은 구간을 나눠 긁는 것이 아니다.
     */
    private static List<TimeWindow> mergeOverlapping(List<TimeWindow> windows) {
        if (windows == null || windows.size() < 2) {
            return List.of();
        }
        List<TimeWindow> sorted = windows.stream()
                .sorted(Comparator.comparing(TimeWindow::start)).toList();
        ArrayList<TimeWindow> merged = new ArrayList<>();
        for (TimeWindow next : sorted) {
            TimeWindow last = merged.isEmpty() ? null : merged.get(merged.size() - 1);
            if (last != null && !next.start().isAfter(last.end())) {
                if (next.end().isAfter(last.end())) {
                    merged.set(merged.size() - 1, new TimeWindow(last.start(), next.end()));
                }
            } else {
                merged.add(next);
            }
        }
        return merged.size() < 2 ? List.of() : List.copyOf(merged);
    }
}
