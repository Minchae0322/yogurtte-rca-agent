package com.yogurtte.rca.client;

import java.time.Instant;
import java.util.Map;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class MimirClient {

    private final RestClient restClient;
    private final RawResponseStore rawStore;

    public MimirClient(GrafanaProperties properties, RawResponseStore rawStore) {
        this.restClient = properties.restClient(properties.mimir());
        this.rawStore = rawStore;
    }

    /** /prometheus/api/v1/query_range. start/end are unix seconds. */
    public String queryRange(String traceId, String promql, Instant start, Instant end, String step) {
        // Same reason as LokiClient: PromQL label matchers use { and }, which are URI template syntax.
        var body = restClient.get()
                .uri(builder -> builder.path("/prometheus/api/v1/query_range")
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
