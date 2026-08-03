package com.yogurtte.rca.collector;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.yogurtte.rca.client.LokiClient;
import com.yogurtte.rca.client.MimirClient;
import com.yogurtte.rca.client.TempoClient;

/**
 * traceId 하나에 대한 트레이스/로그/메트릭을 수집한다.
 * 한 소스가 실패해도 실행을 중단하지 않는다 - 실패 사유만 기록하고 수집을 계속한다.
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class Collector {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** B-9 후보 검색 TraceQL. 상태를 거르지 않는다 — 정답이 <b>정상 트레이스</b>인 문항이 있다
     *  (AU-2: "정상 요청에 auth 호출 span이 없다"가 요건이라 error/slow 채널 어디에도 안 걸린다). */
    private static final String ANY_TRACE_QUERY = "{}";

    /** {@code max-traces <= 0}(상한 없음)일 때 창을 훑는 폭. Tempo 검색은 limit이 필수다. */
    private static final int SEARCH_SCAN_LIMIT = 200;

    private final TempoClient tempoClient;
    private final LokiClient lokiClient;
    private final MimirClient mimirClient;
    private final CollectProperties properties;

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
        String correlationId = scope.correlationId();
        ArrayList<String> failures = new ArrayList<>();
        LinkedHashMap<String, Long> timings = new LinkedHashMap<>();
        LinkedHashMap<String, String> metrics = new LinkedHashMap<>();

        // --- Tempo: 탐색이 지목한 트레이스 전부. 대표를 세우지 않는다 ---
        LinkedHashMap<String, String> traces = new LinkedHashMap<>();
        long started = System.currentTimeMillis();
        if (scope.hasTraceIds()) {
            for (String id : scope.traceIds()) {
                try {
                    traces.put(id, tempoClient.fetchTrace(id));
                } catch (Exception e) {
                    failures.add("Tempo trace fetch failed (" + id + "): " + describe(e));
                    log.warn("tempo fetch failed for {}: {}", id, e.toString());
                }
            }
        } else {
            failures.add("이 조사에는 지목된 traceId가 없다 — 탐색이 트레이스를 찾지 못했거나 "
                    + "트레이스가 생성되지 않는 장애다. 트레이스 부재 자체를 근거로 쓸 것.");
        }
        timings.put("tempoMs", System.currentTimeMillis() - started);

        TimeWindow window = resolveWindow(scope, traces.values().stream().findFirst().orElse(null), failures);

        // --- B-9: 창 안 후보 트레이스로 남은 자리를 채운다 ---
        started = System.currentTimeMillis();
        collectCandidates(scope, window, traces, failures);
        timings.put("tempoCandidatesMs", System.currentTimeMillis() - started);

        // --- Loki: 후보별 창마다 조회하고 스트림을 합친다 ---
        // 합집합 창으로 한 번에 긁으면 후보 사이의 빈 구간까지 딸려 온다. 로그는 점 사건이라
        // 그 구간에 정보가 없다 (메트릭은 다르다 — 아래에서 합집합 창을 그대로 쓴다).
        List<TimeWindow> logWindows = scope.logWindows(window);
        String errorWarnLogs = null;
        String traceIdLogs = null;
        started = System.currentTimeMillis();
        List<String> errorWarnParts = new ArrayList<>();
        List<String> traceIdParts = new ArrayList<>();
        for (int i = 0; i < logWindows.size(); i++) {
            TimeWindow w = logWindows.get(i);
            String suffix = logWindows.size() > 1 ? "-w" + (i + 1) : "";
            try {
                errorWarnParts.add(lokiClient.queryRange(correlationId, "error-warn" + suffix,
                        properties.errorWarnQuery(scope.services()),
                        w.start(), w.end(), properties.logLimit()));
            } catch (Exception e) {
                failures.add("Loki ERROR/WARN log query failed (" + w.start() + "~" + w.end() + "): "
                        + describe(e));
                log.warn("loki error/warn query failed for {}: {}", correlationId, e.toString());
            }
            if (scope.hasTraceIds()) {
                try {
                    traceIdParts.add(lokiClient.queryRange(correlationId, "trace-id" + suffix,
                            properties.traceIdQuery(scope.traceIds(), scope.services()),
                            w.start(), w.end(), properties.logLimit()));
                } catch (Exception e) {
                    failures.add("Loki traceId log query failed (" + w.start() + "~" + w.end() + "): "
                            + describe(e));
                    log.warn("loki traceId query failed for {}: {}", correlationId, e.toString());
                }
            }
        }
        errorWarnLogs = mergeStreams(errorWarnParts);
        traceIdLogs = mergeStreams(traceIdParts);
        timings.put("lokiMs", System.currentTimeMillis() - started);

        // --- Mimir: 설정된 식마다 query_range 1회 ---
        started = System.currentTimeMillis();
        for (String query : properties.metricQueries()) {
            try {
                String body = mimirClient.queryRange(correlationId, query, window.start(), window.end(),
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

        return new CollectedData(correlationId, window, errorWarnLogs, traceIdLogs,
                metrics, traces, failures, timings);
    }

    /**
     * 창 안의 다른 트레이스로 남은 자리를 채운다 (B-9). {@code traces}에 이어 붙인다.
     *
     * <p><b>탐색을 거친 조사(창이 명시된 Scope)에서만 동작한다.</b> traceId로 직접 들어오는
     * v0 경로는 후보 없이 기존과 동일하다 — baseline 경로를 바꾸면 두 진입점의 점수 비교가
     * 무너진다.
     *
     * <p>검색 TraceQL이 {@code {}} 인 것은 <b>정답이 정상 트레이스인 문항</b>이 실재하기
     * 때문이다 (AU-2: *"정상 요청에 auth 호출 span이 없다"* 가 요건이라 error/slow 채널
     * 어디에도 안 걸린다).
     */
    private void collectCandidates(Scope scope, TimeWindow window,
                                   LinkedHashMap<String, String> traces, ArrayList<String> failures) {
        if (scope.window() == null) {
            return;
        }
        // max-traces <= 0 이면 상한 없음. 창 안 트레이스를 전부 딥 페치한다.
        boolean unbounded = properties.maxTraces() <= 0;
        int slots = unbounded ? Integer.MAX_VALUE : properties.maxTraces() - traces.size();
        if (slots <= 0) {
            return;
        }

        LinkedHashSet<String> ids = new LinkedHashSet<>();
        // 로그와 같은 이유로 창별로 검색한다 — 합집합 창으로 훑으면 후보 사이 빈 구간의
        // 무관한 트레이스가 상한을 차지한다.
        List<TimeWindow> searchWindows = scope.logWindows(window);
        for (int i = 0; i < searchWindows.size() && ids.size() < slots; i++) {
            TimeWindow w = searchWindows.get(i);
            try {
                String body = tempoClient.search(scope.correlationId() + "-candidates"
                                + (searchWindows.size() > 1 ? "-w" + (i + 1) : ""),
                        ANY_TRACE_QUERY, w.start(), w.end(),
                        // Tempo /api/search는 limit이 필수라 "무제한"이어도 숫자를 줘야 한다.
                        // ponytail: 상한 없음 = 한 번에 SEARCH_SCAN_LIMIT까지. 이보다 트레이스가
                        // 많은 창이 실제로 나오면 페이지네이션을 붙인다.
                        unbounded ? SEARCH_SCAN_LIMIT : properties.maxTraces() * 2);
                traceIdsOf(body).stream().filter(id -> !traces.containsKey(id)).forEach(ids::add);
            } catch (Exception e) {
                failures.add("Tempo 후보 검색 실패 — 트레이스는 탐색이 지목한 것뿐이다: " + describe(e));
                log.warn("tempo candidate search failed for {}: {}", scope.correlationId(), e.toString());
            }
        }

        int added = 0;
        for (String id : ids) {
            if (added >= slots) {
                break;
            }
            try {
                traces.put(id, tempoClient.fetchTrace(id));
                added++;
            } catch (Exception e) {
                failures.add("후보 트레이스 " + id + " 수집 실패: " + describe(e));
                log.warn("candidate trace fetch failed for {}: {}", id, e.toString());
            }
        }
        log.info("traces collected: {} (지목 {} + 창 후보 {} · slots={})",
                traces.size(), traces.size() - added, added, slots);
    }

    /**
     * 창별로 나눠 받은 Loki 응답을 하나로 합친다 — {@code data.result} 배열만 이어붙인다.
     *
     * <p>합쳐서 넘기는 이유는 <b>하류가 창을 몰라도 되게</b> 하기 위해서다. 창별 목록으로
     * 바꾸면 {@code LokiLogDedup}·{@code EvidenceExtractor}·{@code ContextAssembler}가 전부
     * 창을 알아야 하는데, 그들이 하는 일에 창은 필요 없다.
     *
     * <p>창이 하나면 원본 문자열을 <b>그대로</b> 돌려준다 — 재직렬화하면 baseline 회차의
     * 바이트 수가 미묘하게 달라져 토큰 축 비교가 흔들린다.
     */
    private static String mergeStreams(List<String> bodies) {
        List<String> present = bodies.stream().filter(b -> b != null && !b.isBlank()).toList();
        if (present.size() <= 1) {
            return present.isEmpty() ? null : present.get(0);
        }
        try {
            JsonNode first = MAPPER.readTree(present.get(0));
            ArrayNode merged = MAPPER.createArrayNode();
            for (String body : present) {
                JsonNode result = MAPPER.readTree(body).path("data").path("result");
                if (result.isArray()) {
                    merged.addAll((ArrayNode) result);
                }
            }
            ((ObjectNode) first.path("data")).set("result", merged);
            return MAPPER.writeValueAsString(first);
        } catch (Exception e) {
            // 합치기에 실패하면 첫 창의 응답이라도 넘긴다 — 조사를 멈추지 않는다.
            return present.get(0);
        }
    }

    /** Tempo /api/search 응답의 {@code traces[].traceID}. */
    private static List<String> traceIdsOf(String searchBody) {
        ArrayList<String> out = new ArrayList<>();
        if (searchBody == null || searchBody.isBlank()) {
            return out;
        }
        try {
            for (JsonNode trace : MAPPER.readTree(searchBody).path("traces")) {
                String id = trace.path("traceID").asText("");
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
        TimeWindow fromTrace = TimeWindow.fromTrace(traceJson, properties.windowPaddingSeconds());
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
        String normalized = body.replaceAll("\\s+", "");
        return !normalized.contains("\"result\":[]");
    }

    private static String describe(Exception e) {
        String message = e.getMessage();
        return e.getClass().getSimpleName() + (message == null ? "" : ": " + message);
    }
}
