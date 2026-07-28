package com.yogurtte.rca.triage;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 1단계 스윕(SURVEY) 설정. 분석 단계(DETAIL)와 <b>쿼리 종류가 다르다</b> — 스윕은 창이
 * 몇 시간짜리라 원본을 그대로 받으면 컨텍스트가 터진다. 그래서 전부 <b>집계 쿼리</b>이고,
 * 창이 12시간이어도 응답 크기가 거의 늘지 않는다.
 *
 * @param zone                 자연어 시간 표현을 해석할 표준시. "어젯밤"이 언제인지가 여기 달렸다.
 * @param defaultLookbackHours 질문에서 시간 표현을 못 찾았을 때의 기본 조회 폭.
 * @param maxWindowHours       상한. 이보다 넓게 요청되면 끝(end) 기준으로 잘라낸다.
 * @param step                 집계 해상도. 창이 넓으므로 분석 단계(15s)보다 성기게 잡는다.
 * @param traceQuery           Tempo 검색 TraceQL. 기본은 에러 트레이스 전량.
 * @param traceLimit           Tempo 검색 결과 상한.
 * @param logQuery             Loki 집계 LogQL. {@code %s}에 앱 셀렉터 값이 들어간다.
 * @param metricQueries        Mimir 집계 PromQL. <b>부재가 곧 신호</b>인 것들을 우선 넣는다 —
 *                             {@code up}이 0으로 꺾이는 것이 AU-2에서 유일한 도달 경로였다.
 */
@ConfigurationProperties("rca.survey")
public record SurveyProperties(
        String zone,
        int defaultLookbackHours,
        int maxWindowHours,
        String step,
        String traceQuery,
        int traceLimit,
        String logQuery,
        List<String> metricQueries) {

    public SurveyProperties {
        zone = blankTo(zone, "Asia/Seoul");
        defaultLookbackHours = defaultLookbackHours <= 0 ? 24 : defaultLookbackHours;
        maxWindowHours = maxWindowHours <= 0 ? 48 : maxWindowHours;
        step = blankTo(step, "5m");
        traceQuery = blankTo(traceQuery, "{ status = error }");
        traceLimit = traceLimit <= 0 ? 20 : traceLimit;
        logQuery = blankTo(logQuery,
                "sum by (service_name) (count_over_time({service_name=~\"%s\"} |~ \"ERROR|WARN\" [5m]))");
        metricQueries = metricQueries == null ? List.of() : List.copyOf(metricQueries);
    }

    /** 앱 셀렉터를 채운 실제 LogQL. */
    public String logQueryFor(String appsPattern) {
        return logQuery.contains("%s") ? logQuery.formatted(appsPattern) : logQuery;
    }

    private static String blankTo(String value, String fallback) {
        return (value == null || value.isBlank()) ? fallback : value;
    }
}
