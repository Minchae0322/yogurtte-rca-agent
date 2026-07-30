package com.yogurtte.rca.triage;

import java.util.ArrayList;
import java.util.LinkedHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.yogurtte.rca.client.LokiClient;
import com.yogurtte.rca.client.MimirClient;
import com.yogurtte.rca.client.TempoClient;
import com.yogurtte.rca.collector.CollectProperties;
import com.yogurtte.rca.collector.TimeWindow;

/**
 * 1단계 — 창 전체에 LGTM 3채널을 <b>싹 날린다</b>.
 *
 * <p>무엇을 날릴지는 <b>코드가 정한다.</b> 12시간 창 앞에서 LLM은 애초에 무엇을 물어야 할지
 * 모르고, 물어보게 하면 회차마다 다른 것을 물어 재현이 깨진다. 여기서는 고정 목록을 병렬로
 * 돌리고, LLM은 그 결과를 보고 <b>어디를 볼지 고르는 데</b>부터 들어온다.
 *
 * <p>{@link com.yogurtte.rca.collector.Collector}와 같은 클라이언트를 쓰지만 쿼리가 다르다 —
 * 이쪽은 전부 집계라 창이 넓어도 응답이 작다.
 */
@Component
public class Surveyor {

    private static final Logger log = LoggerFactory.getLogger(Surveyor.class);

    private final TempoClient tempoClient;
    private final LokiClient lokiClient;
    private final MimirClient mimirClient;
    private final SurveyProperties surveyProperties;
    private final CollectProperties collectProperties;

    public Surveyor(TempoClient tempoClient, LokiClient lokiClient, MimirClient mimirClient,
                    SurveyProperties surveyProperties, CollectProperties collectProperties) {
        this.tempoClient = tempoClient;
        this.lokiClient = lokiClient;
        this.mimirClient = mimirClient;
        this.surveyProperties = surveyProperties;
        this.collectProperties = collectProperties;
    }

    public SurveyResult survey(TimeWindow window, String timeExpression) {
        var correlationId = "scan-" + window.start().getEpochSecond();
        var failures = new ArrayList<String>();
        var timings = new LinkedHashMap<String, Long>();
        var metrics = new LinkedHashMap<String, String>();

        // --- Tempo: 에러 채널과 지연 채널을 따로 던진다 ---
        // 단일 쿼리로 합치면 후보 목록에서 "어느 채널로 도달했는지"가 사라진다. 200 성공 + 지연
        // 장애에서는 그 사실 자체가 장애 성격이다 — CH-3는 에러 검색이 0건이었다.
        String traceSearch = null;
        String slowTraceSearch = null;
        var started = System.currentTimeMillis();
        try {
            traceSearch = tempoClient.search(correlationId, surveyProperties.traceQuery(),
                    window.start(), window.end(), surveyProperties.traceLimit());
            if (isEmptyTraceSearch(traceSearch)) {
                failures.add("Tempo 에러 검색 '" + surveyProperties.traceQuery()
                        + "'이 이 창에서 0건이다. 트레이스가 생성되지 않는 장애(컨슈머 전멸·파드 부재)이거나 "
                        + "에러가 아닌 형태의 장애(200 성공 + 지연)일 수 있으니 이 사실 자체를 근거로 쓸 것.");
            }
        } catch (Exception e) {
            failures.add("Tempo 에러 검색 실패: " + describe(e));
            log.warn("tempo error search failed for {}: {}", correlationId, e.toString());
        }
        try {
            slowTraceSearch = tempoClient.search(correlationId + "-slow", surveyProperties.slowTraceQueryFor(),
                    window.start(), window.end(), surveyProperties.traceLimit());
            if (isEmptyTraceSearch(slowTraceSearch)) {
                failures.add("Tempo 지연 검색 '" + surveyProperties.slowTraceQueryFor()
                        + "'이 이 창에서 0건이다. 임계값보다 느린 요청이 없었다는 뜻이다.");
            }
        } catch (Exception e) {
            failures.add("Tempo 지연 검색 실패: " + describe(e));
            log.warn("tempo slow search failed for {}: {}", correlationId, e.toString());
        }
        timings.put("tempoMs", System.currentTimeMillis() - started);

        // --- Loki: 서비스별 ERROR/WARN 발생률 곡선 ---
        String logRates = null;
        started = System.currentTimeMillis();
        var logql = surveyProperties.logQueryFor(collectProperties.appsPattern(null));
        try {
            logRates = lokiClient.queryRangeAggregate(correlationId, "survey-error-rate", logql,
                    window.start(), window.end(), surveyProperties.step());
        } catch (Exception e) {
            failures.add("Loki 집계 쿼리 실패: " + describe(e));
            log.warn("loki survey query failed for {}: {}", correlationId, e.toString());
        }
        timings.put("lokiMs", System.currentTimeMillis() - started);

        // --- Mimir: 부재가 곧 신호인 것들 ---
        started = System.currentTimeMillis();
        for (var query : surveyProperties.metricQueries()) {
            try {
                var body = mimirClient.queryRange(correlationId, query,
                        window.start(), window.end(), surveyProperties.step());
                if (hasSeries(body)) {
                    metrics.put(query, body);
                } else {
                    failures.add("Metric '" + query + "'이 이 창에서 시리즈 0건이다.");
                }
            } catch (Exception e) {
                failures.add("Metric '" + query + "' 실패: " + describe(e));
                log.warn("mimir survey query '{}' failed for {}: {}", query, correlationId, e.toString());
            }
        }
        timings.put("mimirMs", System.currentTimeMillis() - started);

        log.info("survey done: window={}~{} error={} slow={} logRates={} metrics={}/{} failures={}",
                window.start(), window.end(), traceSearch != null, slowTraceSearch != null, logRates != null,
                metrics.size(), surveyProperties.metricQueries().size(), failures.size());

        return new SurveyResult(window, timeExpression, traceSearch, slowTraceSearch,
                logRates, metrics, failures, timings);
    }

    /** Tempo는 매칭이 없으면 {@code {}} 또는 빈 traces 배열을 준다. */
    private static boolean isEmptyTraceSearch(String body) {
        if (body == null || body.isBlank()) {
            return true;
        }
        var normalized = body.replaceAll("\\s+", "");
        return normalized.equals("{}") || normalized.contains("\"traces\":[]");
    }

    /** Prometheus는 매칭된 것이 없으면 "result" 배열을 비워서 응답한다. */
    private static boolean hasSeries(String body) {
        if (body == null || body.isBlank()) {
            return false;
        }
        return !body.replaceAll("\\s+", "").contains("\"result\":[]");
    }

    private static String describe(Exception e) {
        var message = e.getMessage();
        return e.getClass().getSimpleName() + (message == null ? "" : ": " + message);
    }
}
