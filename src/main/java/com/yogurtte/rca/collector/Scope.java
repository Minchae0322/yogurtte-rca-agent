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
 * @param window   조회 시간창. {@code null}이면 트레이스에서 파생한다(기존 v0 경로).
 * @param services 대상 서비스. 비어 있으면 설정된 전체 앱을 본다.
 * @param traceId  대표 트레이스. 없으면 트레이스 수집과 traceId 로그 조회를 건너뛴다.
 */
public record Scope(TimeWindow window, List<String> services, String traceId) {

    public Scope {
        services = services == null ? List.of() : List.copyOf(services);
    }

    /** traceId 하나만 주는 기존 v0 진입점. 시간창은 트레이스에서 파생된다. */
    public static Scope ofTrace(String traceId) {
        return new Scope(null, List.of(), traceId);
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
