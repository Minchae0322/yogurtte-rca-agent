package com.yogurtte.rca.client;

import java.time.Instant;
import java.util.Map;

import lombok.RequiredArgsConstructor;

import org.springframework.web.client.RestClient;

@RequiredArgsConstructor
public class LokiClient {

    private final RestClient restClient;
    private final RawResponseStore rawStore;

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

    /**
     * 같은 {@code query_range}이지만 <b>집계 쿼리</b>용이다 ({@code count_over_time} 등).
     *
     * <p>{@code limit}/{@code direction}을 안 붙이고 {@code step}을 붙인다 — 스윕은 창이
     * 몇 시간짜리라 원본 라인을 받으면 컨텍스트가 터지고, 필요한 것은 "언제 얼마나 늘었나"
     * 라는 곡선뿐이다. 창이 넓어져도 응답 크기가 스텝 수로만 결정된다.
     */
    public String queryRangeAggregate(String correlationId, String label, String logql,
                                      Instant start, Instant end, String step) {
        var body = restClient.get()
                .uri(builder -> builder.path("/loki/api/v1/query_range")
                        .queryParam("query", "{logql}")
                        .queryParam("start", nanos(start))
                        .queryParam("end", nanos(end))
                        .queryParam("step", step)
                        .build(Map.of("logql", logql)))
                .retrieve()
                .body(String.class);
        rawStore.save(correlationId, "loki-" + label, body);
        return body;
    }

    private static String nanos(Instant instant) {
        return Long.toString(instant.getEpochSecond() * 1_000_000_000L + instant.getNano());
    }
}
