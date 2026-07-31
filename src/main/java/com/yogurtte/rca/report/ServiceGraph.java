package com.yogurtte.rca.report;

import java.util.List;
import java.util.Map;

/**
 * 트레이스에서 추출한 호출 그래프. "누가 누구를 불렀나"를 span 관계·속성에서 유도해
 * 엣지 단위로 집약한 것이다 — 정적 토폴로지 맵을 심는 것이 아니라 <b>관측에서만</b> 나온다.
 *
 * <p>집약은 필터링이 아니다. span을 버리는 게 아니라 같은 엣지의 span 수십 개를 한 줄로
 * 접는 것이고, 그 줄에 error·events를 붙여 원인 지문이 사라지지 않게 한다.
 */
public record ServiceGraph(List<Edge> edges) {

    public ServiceGraph {
        edges = edges == null ? List.of() : List.copyOf(edges);
    }

    public static ServiceGraph empty() {
        return new ServiceGraph(List.of());
    }

    public boolean isEmpty() {
        return edges.isEmpty();
    }

    /**
     * @param kind       messaging / db / jdbc / service / unclassified
     * @param detail     엣지를 특정하는 부가 정보 (예: JDBC 커넥션 풀 이름). 없으면 빈 문자열.
     * @param operations 관측된 연산 (db.operation, messaging.operation 등)
     * @param errors     이 엣지의 span들에 붙어 있던 예외·에러 원문 (중복 제거, 발췌)
     * @param events     span events 이름 (예: rollback). 정답 지문이 여기 남는 장애가 있다.
     * @param attributes <b>미분류 엣지만</b> 원본 속성을 실어 모델이 직접 판단하게 한다.
     *                   분류된 엣지는 비워 크기를 아낀다.
     */
    public record Edge(String kind, String source, String target, String detail,
                       int calls, double maxMs,
                       List<String> operations, List<String> errors, List<String> events,
                       Map<String, String> attributes) {

        public Edge {
            operations = operations == null ? List.of() : List.copyOf(operations);
            errors = errors == null ? List.of() : List.copyOf(errors);
            events = events == null ? List.of() : List.copyOf(events);
            attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
        }
    }

    /** 컨텍스트와 리포트가 같은 서술을 보도록 렌더링을 여기 한 곳에 둔다. */
    public String toText() {
        if (edges.isEmpty()) {
            return "(추출된 엣지 없음)";
        }
        var sb = new StringBuilder();
        for (var edge : edges) {
            sb.append(edge.source()).append(" --").append(edge.kind()).append("--> ").append(edge.target());
            if (!edge.detail().isEmpty()) {
                sb.append(" (").append(edge.detail()).append(')');
            }
            sb.append("  ").append(edge.calls()).append("회");
            sb.append("  최대 ").append("%.1fms".formatted(edge.maxMs()));
            if (!edge.operations().isEmpty()) {
                sb.append("  [").append(String.join(", ", edge.operations())).append(']');
            }
            sb.append('\n');
            edge.errors().forEach(error -> sb.append("    error: ").append(error).append('\n'));
            if (!edge.events().isEmpty()) {
                sb.append("    events: ").append(String.join(", ", edge.events())).append('\n');
            }
            if (!edge.attributes().isEmpty()) {
                sb.append("    속성(미분류): ").append(edge.attributes()).append('\n');
            }
        }
        return sb.toString();
    }
}
