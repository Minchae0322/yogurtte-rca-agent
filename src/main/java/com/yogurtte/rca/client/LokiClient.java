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

    /** /loki/api/v1/query_range. Loki는 start/end를 unix 나노초로 받는다. */
    public String queryRange(String traceId, String label, String logql, Instant start, Instant end, int limit) {
        // LogQL 셀렉터에는 { }가 들어가는데, UriBuilder는 이를 템플릿 변수로 해석해 버린다.
        // 식을 바인딩 변수로 넘기면 리터럴로 유지되면서 인코딩도 된다.
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
