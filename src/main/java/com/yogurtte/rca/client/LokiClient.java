package com.yogurtte.rca.client;

import java.time.Instant;
import java.util.Map;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class LokiClient {

    private final RestClient restClient;
    private final RawResponseStore rawStore;

    public LokiClient(GrafanaProperties properties, RawResponseStore rawStore) {
        this.restClient = properties.restClient(properties.loki());
        this.rawStore = rawStore;
    }

    /** /loki/api/v1/query_range. Loki wants start/end as unix nanoseconds. */
    public String queryRange(String traceId, String label, String logql, Instant start, Instant end, int limit) {
        // LogQL selectors contain { and }, which UriBuilder would otherwise read as template
        // variables. Passing the expression as a bound variable keeps it literal and encoded.
        var body = restClient.get()
                .uri(builder -> builder.path("/loki/api/v1/query_range")
                        .queryParam("query", "{logql}")
                        .queryParam("start", nanos(start))
                        .queryParam("end", nanos(end))
                        .queryParam("limit", limit)
                        .queryParam("direction", "forward")
                        .build(Map.of("logql", logql)))
                .retrieve()
                .body(String.class);
        rawStore.save(traceId, "loki-" + label, body);
        return body;
    }

    private static String nanos(Instant instant) {
        return Long.toString(instant.getEpochSecond() * 1_000_000_000L + instant.getNano());
    }
}
