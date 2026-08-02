package com.yogurtte.rca.client;

import java.time.Instant;
import java.util.Map;

import lombok.RequiredArgsConstructor;

import org.springframework.web.client.RestClient;

@RequiredArgsConstructor
public class MimirClient {

    private final RestClient restClient;
    private final RawResponseStore rawStore;

    /**
     * {MIMIR_URL}/api/v1/query_range. start/end는 unix 초 단위다.
     *
     * <p>Prometheus HTTP API 접두어(Grafana Cloud는 {@code /api/prom})는 MIMIR_URL에 포함시키고,
     * 여기서는 표준 {@code /api/v1/query_range}만 이어붙인다.
     */
    public String queryRange(String traceId, String promql, Instant start, Instant end, String step) {
        // LokiClient와 같은 이유: PromQL 레이블 매처의 { }가 URI 템플릿 문법과 겹친다.
        var body = restClient.get()
                .uri(builder -> builder.path("/api/v1/query_range")
                        .queryParam("query", "{promql}")
                        .queryParam("start", start.getEpochSecond())
                        .queryParam("end", end.getEpochSecond())
                        .queryParam("step", step)
                        .build(Map.of("promql", promql)))
                .retrieve()
                .body(String.class);
        rawStore.save(traceId, "mimir-" + promql, body);
        return body;
    }
}
