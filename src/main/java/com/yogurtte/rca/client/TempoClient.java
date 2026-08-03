package com.yogurtte.rca.client;

import java.time.Instant;
import java.util.Map;

import lombok.RequiredArgsConstructor;

import org.springframework.web.client.RestClient;

@RequiredArgsConstructor
public class TempoClient {

    private final RestClient restClient;
    private final RawResponseStore rawStore;

    /** GET {TEMPO_URL}/api/traces/{traceId}로 받은 원본 트레이스 JSON. */
    public String fetchTrace(String traceId) {
        String body = restClient.get()
                .uri("/api/traces/{traceId}", traceId)
                .retrieve()
                .body(String.class);
        rawStore.save(traceId, "tempo-trace", body);
        return body;
    }

    /**
     * GET {TEMPO_URL}/api/search — TraceQL로 <b>창 안에서</b> 이상 트레이스를 찾는다.
     *
     * <p>{@link #fetchTrace}가 "traceId를 이미 아는" 조회라면 이쪽은 그 traceId를 만들어내는
     * 조회다. 다만 이 채널만으로는 절반을 못 찾는다 — 컨슈머 전멸·파드 부재처럼 트레이스가
     * 아예 생성되지 않는 장애가 있어, Loki·Mimir 스윕과 함께 걸어야 한다.
     */
    public String search(String correlationId, String traceQl, Instant start, Instant end, int limit) {
        // LokiClient와 같은 이유: TraceQL의 { }가 URI 템플릿 문법과 겹쳐 바인딩 변수로 넘긴다.
        String body = restClient.get()
                .uri(builder -> builder.path("/api/search")
                        .queryParam("q", "{traceql}")
                        .queryParam("start", start.getEpochSecond())
                        .queryParam("end", end.getEpochSecond())
                        .queryParam("limit", limit)
                        .build(Map.of("traceql", traceQl)))
                .retrieve()
                .body(String.class);
        rawStore.save(correlationId, "tempo-search", body);
        return body;
    }
}
