package com.yogurtte.rca.collector;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yogurtte.rca.client.LokiClient;
import com.yogurtte.rca.client.MimirClient;
import com.yogurtte.rca.client.TempoClient;

/**
 * traceId 하나에 대한 트레이스/로그/메트릭을 수집한다.
 * 한 소스가 실패해도 실행을 중단하지 않는다 - 실패 사유만 기록하고 수집을 계속한다.
 */
@Component
public class Collector {

    private static final Logger log = LoggerFactory.getLogger(Collector.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** B-9 후보 검색 TraceQL. 상태를 거르지 않는다 — 정답이 <b>정상 트레이스</b>인 문항이 있다
     *  (AU-2: "정상 요청에 auth 호출 span이 없다"가 요건이라 error/slow 채널 어디에도 안 걸린다). */
    private static final String ANY_TRACE_QUERY = "{}";

    private final TempoClient tempoClient;
    private final LokiClient lokiClient;
    private final MimirClient mimirClient;
    private final CollectProperties properties;

    public Collector(TempoClient tempoClient, LokiClient lokiClient, MimirClient mimirClient,
                     CollectProperties properties) {
        this.tempoClient = tempoClient;
        this.lokiClient = lokiClient;
        this.mimirClient = mimirClient;
        this.properties = properties;
    }

    /** traceId 하나만 주는 기존 v0 진입점. 동작은 {@link #collect(Scope)}와 동일하다. */
    public CollectedData collect(String traceId) {
        return collect(Scope.ofTrace(traceId));
    }

    /**
     * 범위 하나를 깊게 수집한다.
     *
     * <p>traceId가 없는 범위도 정상 입력이다 — 컨슈머 전멸·파드 부재처럼 이상 트레이스가
     * 생성되지 않는 장애가 실재하고(CH-2·AU-2), 그때는 트레이스 관련 조회 둘을 건너뛰고
     * 로그·메트릭만으로 수집한다. 건너뛴 사실도 실패 목록에 남겨 모델이 공백을 알게 한다.
     */
    public CollectedData collect(Scope scope) {
        var correlationId = scope.correlationId();
        var failures = new ArrayList<String>();
        var timings = new LinkedHashMap<String, Long>();
        var metrics = new LinkedHashMap<String, String>();

        // --- Tempo ---
        String traceJson = null;
        var started = System.currentTimeMillis();
        if (scope.hasTraceId()) {
            try {
                traceJson = tempoClient.fetchTrace(scope.traceId());
            } catch (Exception e) {
                failures.add("Tempo trace fetch failed: " + describe(e));
                log.warn("tempo fetch failed for {}: {}", scope.traceId(), e.toString());
            }
        } else {
            failures.add("이 조사에는 대표 traceId가 없다 — 탐색이 트레이스를 찾지 못했거나 "
                    + "트레이스가 생성되지 않는 장애다. 트레이스 부재 자체를 근거로 쓸 것.");
        }
        timings.put("tempoMs", System.currentTimeMillis() - started);

        var window = resolveWindow(scope, traceJson, failures);

        // --- B-9: 창 안 후보 트레이스 N건 ---
        started = System.currentTimeMillis();
        var candidateTraces = collectCandidates(scope, window, failures);
        timings.put("tempoCandidatesMs", System.currentTimeMillis() - started);

        // --- Loki: traceId가 있으면 2회, 없으면 1회 ---
        String errorWarnLogs = null;
        String traceIdLogs = null;
        started = System.currentTimeMillis();
        try {
            errorWarnLogs = lokiClient.queryRange(correlationId, "error-warn",
                    properties.errorWarnQuery(scope.services()),
                    window.start(), window.end(), properties.logLimit());
        } catch (Exception e) {
            failures.add("Loki ERROR/WARN log query failed: " + describe(e));
            log.warn("loki error/warn query failed for {}: {}", correlationId, e.toString());
        }
        if (scope.hasTraceId()) {
            try {
                traceIdLogs = lokiClient.queryRange(correlationId, "trace-id",
                        properties.traceIdQuery(scope.traceId(), scope.services()),
                        window.start(), window.end(), properties.logLimit());
            } catch (Exception e) {
                failures.add("Loki traceId log query failed: " + describe(e));
                log.warn("loki traceId query failed for {}: {}", correlationId, e.toString());
            }
        }
        timings.put("lokiMs", System.currentTimeMillis() - started);

        // --- Mimir: 설정된 식마다 query_range 1회 ---
        started = System.currentTimeMillis();
        for (var query : properties.metricQueries()) {
            try {
                var body = mimirClient.queryRange(correlationId, query, window.start(), window.end(),
                        properties.metricStep());
                if (hasSeries(body)) {
                    metrics.put(query, body);
                } else {
                    // 예: Kafka 메트릭이 노출되지 않았을 때의 kafka_consumer_fetch_manager_records_lag.
                    failures.add("Metric '" + query + "' returned no series in this window; skipped.");
                }
            } catch (Exception e) {
                failures.add("Metric '" + query + "' query failed: " + describe(e));
                log.warn("mimir query '{}' failed for {}: {}", query, correlationId, e.toString());
            }
        }
        timings.put("mimirMs", System.currentTimeMillis() - started);

        return new CollectedData(scope.traceId(), traceJson, window, errorWarnLogs, traceIdLogs,
                metrics, candidateTraces, failures, timings);
    }

    /**
     * 창 안의 다른 트레이스를 최대 {@code maxTraces - 1}건 딥 페치한다 (B-9).
     *
     * <p><b>탐색을 거친 조사(창이 명시된 Scope)에서만 동작한다.</b> traceId로 직접 들어오는
     * v0 경로는 후보 없이 기존과 동일하다 — baseline 경로를 바꾸면 두 진입점의 점수 비교가
     * 무너진다.
     *
     * <p>후보 출처는 둘이다: ① 탐색 스윕이 찾은 이상 트레이스(error·slow 채널, Scope에 실려 옴)
     * ② 자리가 남으면 창 기준 무조건 검색 — <b>정답이 정상 트레이스인 문항</b>은 ①로는 절대
     * 오지 않기 때문이다.
     */
    private LinkedHashMap<String, String> collectCandidates(Scope scope, TimeWindow window,
                                                            ArrayList<String> failures) {
        var candidates = new LinkedHashMap<String, String>();
        var slots = properties.maxTraces() - (scope.hasTraceId() ? 1 : 0);
        if (scope.window() == null || slots <= 0) {
            return candidates;
        }

        var ids = new LinkedHashSet<>(scope.candidateTraceIds());
        ids.remove(scope.traceId());
        if (ids.size() < slots) {
            try {
                var body = tempoClient.search(scope.correlationId() + "-candidates", ANY_TRACE_QUERY,
                        window.start(), window.end(), properties.maxTraces() * 2);
                ids.addAll(traceIdsOf(body));
                ids.remove(scope.traceId());
            } catch (Exception e) {
                failures.add("Tempo 후보 검색 실패 — 후보 트레이스는 탐색이 넘긴 것뿐이다: " + describe(e));
                log.warn("tempo candidate search failed for {}: {}", scope.correlationId(), e.toString());
            }
        }

        for (var id : ids) {
            if (candidates.size() >= slots) {
                break;
            }
            try {
                candidates.put(id, tempoClient.fetchTrace(id));
            } catch (Exception e) {
                failures.add("후보 트레이스 " + id + " 수집 실패: " + describe(e));
                log.warn("candidate trace fetch failed for {}: {}", id, e.toString());
            }
        }
        if (!candidates.isEmpty()) {
            log.info("candidate traces collected: {} of {} ids (slots={})",
                    candidates.size(), ids.size(), slots);
        }
        return candidates;
    }

    /** Tempo /api/search 응답의 {@code traces[].traceID}. */
    private static List<String> traceIdsOf(String searchBody) {
        var out = new ArrayList<String>();
        if (searchBody == null || searchBody.isBlank()) {
            return out;
        }
        try {
            for (var trace : MAPPER.readTree(searchBody).path("traces")) {
                var id = trace.path("traceID").asText("");
                if (!id.isEmpty()) {
                    out.add(id);
                }
            }
        } catch (Exception e) {
            // 검색 응답이 깨져도 조사는 계속한다 — 후보가 없을 뿐이다.
        }
        return out;
    }

    /**
     * 조회 시간창은 셋 중 하나로 정해진다: ① 탐색이 준 창 ② 트레이스에서 파생 ③ now ± padding.
     * ①이 있으면 트레이스보다 우선한다 — 탐색이 좁혀준 구간이 그 자체로 판단의 산물이기 때문이다.
     */
    private TimeWindow resolveWindow(Scope scope, String traceJson, ArrayList<String> failures) {
        if (scope.window() != null) {
            return scope.window();
        }
        var fromTrace = TimeWindow.fromTrace(traceJson, properties.windowPaddingSeconds());
        if (fromTrace != null) {
            return fromTrace;
        }
        failures.add("Time window could not be derived from the trace; "
                + "using now +/- " + properties.windowPaddingSeconds() + "s instead.");
        return TimeWindow.around(Instant.now(), properties.windowPaddingSeconds());
    }

    /** Prometheus는 매칭된 것이 없으면 "result" 배열을 비워서 응답한다. */
    private static boolean hasSeries(String body) {
        if (body == null || body.isBlank()) {
            return false;
        }
        var normalized = body.replaceAll("\\s+", "");
        return !normalized.contains("\"result\":[]");
    }

    private static String describe(Exception e) {
        var message = e.getMessage();
        return e.getClass().getSimpleName() + (message == null ? "" : ": " + message);
    }
}
