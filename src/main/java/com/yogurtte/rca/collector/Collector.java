package com.yogurtte.rca.collector;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.yogurtte.rca.client.LokiClient;
import com.yogurtte.rca.client.MimirClient;
import com.yogurtte.rca.client.TempoClient;

/**
 * Fetches trace, logs and metrics for one traceId.
 * A failing source never aborts the run - it is recorded as a failure note and collection continues.
 */
@Component
public class Collector {

    private static final Logger log = LoggerFactory.getLogger(Collector.class);

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

    public CollectedData collect(String traceId) {
        var failures = new ArrayList<String>();
        var timings = new LinkedHashMap<String, Long>();
        var metrics = new LinkedHashMap<String, String>();

        // --- Tempo ---
        String traceJson = null;
        var started = System.currentTimeMillis();
        try {
            traceJson = tempoClient.fetchTrace(traceId);
        } catch (Exception e) {
            failures.add("Tempo trace fetch failed: " + describe(e));
            log.warn("tempo fetch failed for {}: {}", traceId, e.toString());
        }
        timings.put("tempoMs", System.currentTimeMillis() - started);

        // Without a trace there is no anchor for the window, so fall back to "now +/- padding".
        var window = TimeWindow.fromTrace(traceJson, properties.windowPaddingSeconds());
        if (window == null) {
            window = TimeWindow.around(Instant.now(), properties.windowPaddingSeconds());
            failures.add("Time window could not be derived from the trace; "
                    + "using now +/- " + properties.windowPaddingSeconds() + "s instead.");
        }

        // --- Loki: two separate queries ---
        String errorWarnLogs = null;
        String traceIdLogs = null;
        started = System.currentTimeMillis();
        try {
            errorWarnLogs = lokiClient.queryRange(traceId, "error-warn", properties.errorWarnQuery(),
                    window.start(), window.end(), properties.logLimit());
        } catch (Exception e) {
            failures.add("Loki ERROR/WARN log query failed: " + describe(e));
            log.warn("loki error/warn query failed for {}: {}", traceId, e.toString());
        }
        try {
            traceIdLogs = lokiClient.queryRange(traceId, "trace-id", properties.traceIdQuery(traceId),
                    window.start(), window.end(), properties.logLimit());
        } catch (Exception e) {
            failures.add("Loki traceId log query failed: " + describe(e));
            log.warn("loki traceId query failed for {}: {}", traceId, e.toString());
        }
        timings.put("lokiMs", System.currentTimeMillis() - started);

        // --- Mimir: one query_range per configured expression ---
        started = System.currentTimeMillis();
        for (var query : properties.metricQueries()) {
            try {
                var body = mimirClient.queryRange(traceId, query, window.start(), window.end(),
                        properties.metricStep());
                if (hasSeries(body)) {
                    metrics.put(query, body);
                } else {
                    // e.g. kafka_consumer_fetch_manager_records_lag when Kafka metrics aren't exported.
                    failures.add("Metric '" + query + "' returned no series in this window; skipped.");
                }
            } catch (Exception e) {
                failures.add("Metric '" + query + "' query failed: " + describe(e));
                log.warn("mimir query '{}' failed for {}: {}", query, traceId, e.toString());
            }
        }
        timings.put("mimirMs", System.currentTimeMillis() - started);

        return new CollectedData(traceId, traceJson, window, errorWarnLogs, traceIdLogs,
                metrics, failures, timings);
    }

    /** Prometheus answers with an empty "result" array when nothing matched. */
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
