package com.yogurtte.rca.triage.survey;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Component;

import com.yogurtte.rca.client.LokiClient;
import com.yogurtte.rca.client.MimirClient;
import com.yogurtte.rca.client.TempoClient;
import com.yogurtte.rca.collector.CollectProperties;
import com.yogurtte.rca.collector.TimeWindow;
import com.yogurtte.rca.triage.SurveyProperties;

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
@Slf4j
@RequiredArgsConstructor
@Component
public class Surveyor {

    private final TempoClient tempoClient;
    private final LokiClient lokiClient;
    private final MimirClient mimirClient;
    private final SurveyProperties surveyProperties;
    private final CollectProperties collectProperties;

    /**
     * 채널마다 하는 일은 같다: <b>쿼리 한 방 → 결과가 없으면 없다고 적어 둔다.</b>
     * 실패도 0건도 {@code failures}에 남는다 — 이 조사에서 공백은 결측이 아니라 근거이고,
     * 조용히 비면 다음 단계가 그것을 "정상"으로 읽는다.
     */
    public SurveyResult survey(TimeWindow window, String timeExpression) {
        String correlationId = "scan-" + window.start().getEpochSecond();
        List<String> failures = new ArrayList<>();
        Map<String, Long> timings = new LinkedHashMap<>();

        long started = System.currentTimeMillis();
        String traceSearch = searchErrorTraces(correlationId, window, failures);
        String slowTraceSearch = searchSlowTraces(correlationId, window, failures);
        timings.put("tempoMs", System.currentTimeMillis() - started);

        started = System.currentTimeMillis();
        String logRates = queryLogRates(correlationId, window, failures);
        String logSignatureRates = queryLogSignatureRates(correlationId, window, failures);
        timings.put("lokiMs", System.currentTimeMillis() - started);

        started = System.currentTimeMillis();
        Map<String, String> metrics = queryMetrics(correlationId, window, failures);
        timings.put("mimirMs", System.currentTimeMillis() - started);

        log.info("survey done: window={}~{} error={} slow={} logRates={} metrics={}/{} failures={} timings={}",
                window.start(), window.end(), traceSearch != null, slowTraceSearch != null, logRates != null,
                metrics.size(), surveyProperties.metricQueries().size(), failures.size(), timings);

        return new SurveyResult(window, timeExpression, traceSearch, slowTraceSearch,
                logRates, logSignatureRates, metrics, failures, timings);
    }

    /**
     * Tempo 에러 트레이스. 지연 검색과 <b>따로</b> 던진다 — 한 쿼리로 합치면 후보 목록에서
     * "어느 채널로 도달했는지"가 사라지고, 200 성공 + 지연 장애에서는 그 사실 자체가
     * 장애 성격이다 (CH-3는 에러 검색이 0건이었다).
     */
    private String searchErrorTraces(String correlationId, TimeWindow window, List<String> failures) {
        String query = surveyProperties.traceQuery();
        try {
            String body = tempoClient.search(correlationId, query,
                    window.start(), window.end(), surveyProperties.traceLimit());
            if (isEmptyTraceSearch(body)) {
                failures.add("Tempo 에러 검색 '" + query + "'이 이 창에서 0건이다. "
                        + "트레이스가 생성되지 않는 장애(컨슈머 전멸·파드 부재)이거나 "
                        + "에러가 아닌 형태의 장애(200 성공 + 지연)일 수 있으니 이 사실 자체를 근거로 쓸 것.");
            }
            return body;
        } catch (Exception e) {
            return failed("Tempo 에러 검색", e, failures);
        }
    }

    private String searchSlowTraces(String correlationId, TimeWindow window, List<String> failures) {
        String query = surveyProperties.slowTraceQueryFor();
        try {
            String body = tempoClient.search(correlationId + "-slow", query,
                    window.start(), window.end(), surveyProperties.traceLimit());
            if (isEmptyTraceSearch(body)) {
                failures.add("Tempo 지연 검색 '" + query + "'이 이 창에서 0건이다. "
                        + "임계값보다 느린 요청이 없었다는 뜻이다.");
            }
            return body;
        } catch (Exception e) {
            return failed("Tempo 지연 검색", e, failures);
        }
    }

    /** 서비스별 ERROR/WARN 발생률 곡선. 빈 곡선은 정상이라 따로 적지 않는다. */
    private String queryLogRates(String correlationId, TimeWindow window, List<String> failures) {
        try {
            return lokiClient.queryRangeAggregate(correlationId, "survey-error-rate",
                    surveyProperties.logQueryFor(collectProperties.appsPattern(null)),
                    window.start(), window.end(), surveyProperties.step());
        } catch (Exception e) {
            return failed("Loki 집계 쿼리", e, failures);
        }
    }

    /**
     * 같은 곡선을 <b>예외 클래스로 갈라서</b> 한 번 더 센다. 설정이 비어 있으면 아예 안 던진다.
     *
     * <p>기존 곡선({@link #queryLogRates})은 건드리지 않는다 — 총 건수는 그 쿼리가 계속 책임진다.
     * 이 쿼리가 라인을 흘려도(정규식 불일치) 총 건수 신호가 남아 있어야 후보가 사라지지 않는다.
     */
    private String queryLogSignatureRates(String correlationId, TimeWindow window, List<String> failures) {
        if (!surveyProperties.hasLogSignatureQuery()) {
            return null;
        }
        try {
            return lokiClient.queryRangeAggregate(correlationId, "survey-log-signature",
                    surveyProperties.logSignatureQueryFor(collectProperties.appsPattern(null)),
                    window.start(), window.end(), surveyProperties.step());
        } catch (Exception e) {
            return failed("Loki 지문 집계 쿼리", e, failures);
        }
    }

    /** 부재가 곧 신호인 것들 — 시리즈 0건은 담지 않고 사유로만 남긴다. */
    private Map<String, String> queryMetrics(String correlationId, TimeWindow window, List<String> failures) {
        Map<String, String> metrics = new LinkedHashMap<>();
        for (String query : surveyProperties.metricQueries()) {
            try {
                String body = mimirClient.queryRange(correlationId, query,
                        window.start(), window.end(), surveyProperties.step());
                if (hasSeries(body)) {
                    metrics.put(query, body);
                } else {
                    failures.add("Metric '" + query + "'이 이 창에서 시리즈 0건이다.");
                }
            } catch (Exception e) {
                failed("Metric '" + query + "'", e, failures);
            }
        }
        return metrics;
    }

    /** 채널 하나가 통째로 실패한 경우. 삼키지 않고 사유를 남긴 뒤 {@code null}을 돌려준다. */
    private String failed(String label, Exception e, List<String> failures) {
        failures.add(label + " 실패: " + describe(e));
        log.warn("survey {} failed: {}", label, e.toString());
        return null;
    }

    /** Tempo는 매칭이 없으면 {@code {}} 또는 빈 traces 배열을 준다. */
    private static boolean isEmptyTraceSearch(String body) {
        if (body == null || body.isBlank()) {
            return true;
        }
        String normalized = body.replaceAll("\\s+", "");
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
        String message = e.getMessage();
        return e.getClass().getSimpleName() + (message == null ? "" : ": " + message);
    }
}
