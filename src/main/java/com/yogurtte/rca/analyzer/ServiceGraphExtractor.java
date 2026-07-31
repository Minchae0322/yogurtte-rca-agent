package com.yogurtte.rca.analyzer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.yogurtte.rca.collector.CollectedData;
import com.yogurtte.rca.collector.TraceSpans;
import com.yogurtte.rca.report.ServiceGraph;

/**
 * span의 부모-자식 관계와 속성에서 호출 그래프를 유도한다.
 *
 * <p>판별 순서가 결과를 좌우한다 — 표준 키를 먼저 보고 {@code peer.service}를 마지막에 본다.
 * {@code peer.service=content}는 content-service가 아니라 MySQL 데이터베이스 {@code content}라서
 * (실측), 순서를 바꾸면 자기 참조 엣지가 생긴다.
 *
 * <p>못 알아본 엣지는 버리지 않고 미분류로 남긴다 — 버리면 새 인프라가 붙었을 때 조용히 사라진다.
 * 근거와 함정 실측은 {@code docs/round-3/service-graph-spec.md}.
 */
@Component
public class ServiceGraphExtractor {

    private static final int MAX_ERRORS_PER_EDGE = 5;
    private static final int MAX_ERROR_LENGTH = 300;

    /** 선정 트레이스 + 창 안 후보(B-9) 전부에서 뽑는다. 같은 엣지는 트레이스를 넘어 누적되므로
     *  후보가 있으면 이것이 곧 그래프 merge다 — "여러 서비스가 같은 인프라를 쓴다"는 여기서 나온다. */
    public ServiceGraph extract(CollectedData data) {
        var spans = new ArrayList<>(TraceSpans.parse(data.traceJson()));
        data.candidateTraceJsons().values().forEach(json -> spans.addAll(TraceSpans.parse(json)));
        return fromSpans(spans);
    }

    public ServiceGraph fromSpans(List<TraceSpans.Span> spans) {
        var byId = new LinkedHashMap<String, TraceSpans.Span>();
        spans.forEach(span -> byId.put(span.spanId(), span));

        var builders = new LinkedHashMap<String, EdgeBuilder>();
        for (var span : spans) {
            var edge = classify(span, byId);
            if (edge == null) {
                continue;  // 내부 span — 엣지가 아니다. 원본 트레이스에는 그대로 남는다.
            }
            builders.computeIfAbsent(edge.key(), k -> edge).accumulate(span);
        }

        var edges = builders.values().stream()
                .map(EdgeBuilder::build)
                .sorted(Comparator.comparing(ServiceGraph.Edge::kind)
                        .thenComparing(ServiceGraph.Edge::source)
                        .thenComparing(ServiceGraph.Edge::target))
                .toList();
        return new ServiceGraph(edges);
    }

    /**
     * 판별 순서: ① messaging ② db.system ③ jdbc ④ client.name ⑤ 부모 서비스 상이 ⑥ peer.service만.
     *
     * <p>④가 ⑤보다 먼저인 이유: 죽은 서비스는 트레이스에 합류하지 못한다. 호출이 Connection refused로
     * 끝나면 상대 span이 트레이스에 없어 ⑤로는 엣지가 안 나온다(AU-4 실측 — 66개 span 전부 호출한 쪽).
     * 아웃바운드 HTTP CLIENT span의 {@code client.name}이 그때도 남는 유일한 단서다.
     */
    private static EdgeBuilder classify(TraceSpans.Span span, Map<String, TraceSpans.Span> byId) {
        var a = span.attributes();
        var service = span.service().isBlank() ? "?" : span.service();

        var messagingSystem = a.get("messaging.system");
        if (messagingSystem != null) {
            var operation = a.getOrDefault("messaging.operation", "");
            // receive의 토픽은 destination이 아니라 messaging.source.name에 온다 (실측 — CH-1).
            var topic = "receive".equals(operation)
                    ? a.getOrDefault("messaging.source.name", "?")
                    : a.getOrDefault("messaging.destination.name", a.getOrDefault("messaging.destination", "?"));
            var node = messagingSystem + "/" + topic;
            return "receive".equals(operation)
                    ? new EdgeBuilder("messaging", node, service, "", operation)
                    : new EdgeBuilder("messaging", service, node, "", operation);
        }

        var dbSystem = a.get("db.system");
        if (dbSystem != null) {
            return new EdgeBuilder("db", service, dbSystem, "", a.getOrDefault("db.operation", ""));
        }

        // jdbc.* 는 OTel 컨벤션이 아니라 datasource-micrometer의 관례다. datasource.name은
        // 스키마명이 아니라 앱이 지은 풀 설정 이름이다 (chat의 datasource 이름이 "content"인 실측).
        if (a.containsKey("jdbc.datasource.driver") || a.containsKey("jdbc.datasource.name")) {
            var driver = a.getOrDefault("jdbc.datasource.driver", "");
            var kind = driver.toLowerCase().contains("mysql") ? "mysql"
                    : driver.isEmpty() ? "jdbc" : driver;
            var target = kind + "/" + a.getOrDefault("jdbc.datasource.name", "?");
            return new EdgeBuilder("jdbc", service, target, a.getOrDefault("jdbc.datasource.pool", ""), "");
        }

        var clientName = a.get("client.name");
        if (clientName != null) {
            return new EdgeBuilder("service", service, clientName, "", "");
        }

        var parent = byId.get(span.parentSpanId());
        if (parent != null && !parent.service().equals(span.service())) {
            return new EdgeBuilder("service", parent.service(), service, "", "");
        }

        var peerService = a.get("peer.service");
        if (peerService != null) {
            // 여기 도달한 peer.service는 정체를 모르는 값이다. 버리지 않고 원본 속성째 넘긴다.
            return new EdgeBuilder("unclassified", service, "peer:" + peerService, "", "").withAttributes(a);
        }

        return null;
    }

    private static final class EdgeBuilder {
        private final String kind;
        private final String source;
        private final String target;
        private final String detail;
        private final LinkedHashSet<String> operations = new LinkedHashSet<>();
        private final LinkedHashSet<String> errors = new LinkedHashSet<>();
        private final LinkedHashSet<String> events = new LinkedHashSet<>();
        private Map<String, String> attributes = Map.of();
        private int calls;
        private double maxMs;

        EdgeBuilder(String kind, String source, String target, String detail, String operation) {
            this.kind = kind;
            this.source = source;
            this.target = target;
            this.detail = detail;
            if (!operation.isEmpty()) {
                operations.add(operation);
            }
        }

        EdgeBuilder withAttributes(Map<String, String> attributes) {
            this.attributes = attributes;
            return this;
        }

        String key() {
            return kind + '|' + source + '|' + target;
        }

        void accumulate(TraceSpans.Span span) {
            calls++;
            maxMs = Math.max(maxMs, span.durationMillis());
            errorsOf(span).forEach(error -> {
                if (errors.size() < MAX_ERRORS_PER_EDGE) {
                    errors.add(error);
                }
            });
            events.addAll(span.eventNames());
            var operation = span.attributes().getOrDefault("messaging.operation",
                    span.attributes().getOrDefault("db.operation", ""));
            if (!operation.isEmpty()) {
                operations.add(operation);
            }
        }

        ServiceGraph.Edge build() {
            return new ServiceGraph.Edge(kind, source, target, detail, calls, maxMs,
                    List.copyOf(operations), List.copyOf(errors), List.copyOf(events), attributes);
        }

        private static List<String> errorsOf(TraceSpans.Span span) {
            var a = span.attributes();
            var out = new ArrayList<String>();
            var exception = a.getOrDefault("exception", "");
            if (!exception.isEmpty() && !"none".equals(exception)) {
                out.add(truncate(exception));
            }
            var error = a.getOrDefault("error", "");
            if (!error.isEmpty()) {
                out.add(truncate(error));
            }
            if (a.getOrDefault("status", "").startsWith("5")) {
                out.add("status=" + a.get("status"));
            }
            if ("SERVER_ERROR".equals(a.get("outcome"))) {
                out.add("outcome=SERVER_ERROR");
            }
            return out;
        }

        private static String truncate(String s) {
            return s.length() <= MAX_ERROR_LENGTH ? s : s.substring(0, MAX_ERROR_LENGTH) + "…";
        }
    }
}
