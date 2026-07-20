package com.yogurtte.rca.client;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class TempoClient {

    private final RestClient restClient;
    private final RawResponseStore rawStore;

    public TempoClient(GrafanaProperties properties, RawResponseStore rawStore) {
        this.restClient = properties.restClient(properties.tempo());
        this.rawStore = rawStore;
    }

    /** Raw trace JSON from GET {TEMPO_URL}/api/traces/{traceId}. */
    public String fetchTrace(String traceId) {
        var body = restClient.get()
                .uri("/api/traces/{traceId}", traceId)
                .retrieve()
                .body(String.class);
        rawStore.save(traceId, "tempo-trace", body);
        return body;
    }
}
