package com.yogurtte.rca.triage;

import java.time.Duration;
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
 * @param slowTraceQuery       <b>지연 채널</b> TraceQL. {@code %s}에 임계값이 들어간다.
 *                             에러 쿼리와 <b>따로 던져 후보를 병합</b>한다 — 단일 쿼리
 *                             ({@code status = error || duration > Ns})로 합치면 후보 목록에서
 *                             <b>어느 채널로 도달했는지가 사라진다.</b> 200 성공 + 지연 장애에서는
 *                             그 사실 자체가 장애 성격이다(CH-3: 에러 검색 0건).
 * @param slowTraceThreshold   지연 판정 임계값. <b>잠정값이다</b> — 문항에서 역산하지 않고
 *                             정상 트래픽 분포(요청 p99 · span 최장)에서 정해야 하며 그 측정은
 *                             아직 하지 않았다. 자세한 절차는 round-3 incident-clustering-spec §7.4.
 * @param traceLimit           Tempo 검색 결과 상한. <b>채널마다 따로 적용된다</b> — 임계값을
 *                             낮추면 후보가 늘어 정답이 상한에 밀려날 수 있다.
 * @param logQuery             Loki 집계 LogQL. {@code %s}에 앱 셀렉터 값이 들어간다.
 * @param metricQueries        Mimir 집계 PromQL. <b>부재가 곧 신호</b>인 것들을 우선 넣는다 —
 *                             {@code up}이 0으로 꺾이는 것이 AU-2에서 유일한 도달 경로였다.
 * @param includeRaw           탐색 LLM에게 <b>스윕 원본 JSON까지</b> 보일지.
 *                             <b>기본 {@code false}</b> — 후보 목록·무신호 목록만 싣는다 (2026-08-04 전환).
 *                             <p>탐색 LLM의 산출은 사실상 후보 번호 하나이고 (창·서비스·traceId는
 *                             고른 후보에서 코드가 파생한다 — {@code TriagePlan.parse}), 원본은
 *                             스윕 컨텍스트의 약 94%를 차지했다.
 *                             <p><b>A/B 실측으로 전환했다.</b> 같은 창을 {@code from/to}로 고정해 두 팔을
 *                             돌린 결과 탐색 산출이 <b>완전히 일치</b>했다 — 선택 INC-7~15 · 기각 INC-1~6 ·
 *                             좁힌 창 · 대표 traceId · 대상 서비스, 그리고 분석 컨텍스트 431,206 vs
 *                             431,204자. 탐색 컨텍스트 토큰은 32,424 → 5,576 (<b>-82.8%</b>).
 *                             <p>우려했던 <i>"기각 품질이 원본에 기대고 있다"</i>는 반증됐다 —
 *                             {@code false} 팔이 원본 통계 없이 <i>"동반 지연 트레이스 0건"</i> ·
 *                             <i>"kafka_brokers·lag 이상 0건"</i>으로 같은 후보를 쳐냈다.
 *                             <p>⚠ N=1이고 창 하나다. 트레이스가 아예 없는 문항(CH-2·AU-2)에서는
 *                             미검증이다. 되돌림은 {@code RCA_SURVEY_INCLUDE_RAW=true} 한 줄이다.
 */
@ConfigurationProperties("rca.survey")
public record SurveyProperties(
        String zone,
        int defaultLookbackHours,
        int maxWindowHours,
        String step,
        String traceQuery,
        String slowTraceQuery,
        String slowTraceThreshold,
        int traceLimit,
        String logQuery,
        List<String> metricQueries,
        String clusterGap,
        String incidentPadExact,
        String incidentPadBucket,
        // B-30: 0이 이상 신호인 지표명. 여기 없는 지표는 0 구간을 신호로 만들지 않는다.
        // 지표마다 0의 의미가 반대다 — up·mongodb_up은 0이 곧 다운이지만
        // kafka_consumergroup_lag·websocket_active_users는 0이 정상(안 밀림/접속 없음)이다.
        List<String> zeroIsAbnormal,
        // Boolean으로 남긴다 — 기본이 false가 된 뒤에도 "설정을 안 준 것"과 "false를 준 것"을
        // 구분해야 팔을 바꿔 돌린 회차를 나중에 식별할 수 있다. 리포트의 rawIncluded가 그 기록이다.
        Boolean includeRaw) {

    public SurveyProperties {
        includeRaw = includeRaw != null && includeRaw;
        zone = blankTo(zone, "Asia/Seoul");
        defaultLookbackHours = defaultLookbackHours <= 0 ? 24 : defaultLookbackHours;
        maxWindowHours = maxWindowHours <= 0 ? 48 : maxWindowHours;
        step = blankTo(step, "5m");
        traceQuery = blankTo(traceQuery, "{ status = error }");
        // status != error 를 붙여 에러 채널과 겹치지 않게 가른다 — 어느 채널로 도달했는지가 남아야 한다.
        slowTraceQuery = blankTo(slowTraceQuery, "{ duration > %s && status != error }");
        slowTraceThreshold = blankTo(slowTraceThreshold, "3s");
        traceLimit = traceLimit <= 0 ? 20 : traceLimit;
        logQuery = blankTo(logQuery,
                "sum by (service_name) (count_over_time({service_name=~\"%s\"} |~ \"ERROR|WARN\" [5m]))");
        metricQueries = metricQueries == null ? List.of() : List.copyOf(metricQueries);
        clusterGap = blankTo(clusterGap, "60s");
        incidentPadExact = blankTo(incidentPadExact, "2m");
        incidentPadBucket = blankTo(incidentPadBucket, "5m");
        // 빈 목록이면 0 구간 신호가 전부 사라진다 — 설정 누락과 "일부러 껐다"를 구별할 수 없으므로
        // null(미설정)일 때만 기본값을 채운다. 가용성 게이지 셋만 0이 이상이다.
        zeroIsAbnormal = zeroIsAbnormal == null
                ? List.of("up", "mongodb_up", "kafka_brokers")
                : List.copyOf(zeroIsAbnormal);
    }

    /** {@code SignalExtractor}에 넘길 형태. 조회가 시리즈마다 일어나므로 Set으로 준다. */
    public java.util.Set<String> zeroIsAbnormalSet() {
        return java.util.Set.copyOf(zeroIsAbnormal);
    }

    /** 임계값을 채운 실제 지연 TraceQL. */
    public String slowTraceQueryFor() {
        return slowTraceQuery.contains("%s") ? slowTraceQuery.formatted(slowTraceThreshold) : slowTraceQuery;
    }

    /**
     * 같은 키의 신호를 다른 사건으로 가르는 간격.
     *
     * <p>실측에서 유도된 값이다 — CH-1이 끝나고(05:03:30) CH-2가 시작하기까지(05:05:09)
     * <b>99초</b>이므로 이보다 작아야 둘이 갈리고, CH-1의 30초 재시도 4회(연속 약 2분)는
     * 묶여야 하며, CH-3 후보가 독립하려면 <b>4분 51초</b>보다 작아야 한다.
     * 기본 60초는 안전 여유가 4배 이상이다.
     */
    public Duration clusterGapDuration() {
        return parse(clusterGap, Duration.ofSeconds(60));
    }

    /** 시각이 ms 단위로 정확한 신호(트레이스 span)의 창 여유. */
    public Duration incidentPadExactDuration() {
        return parse(incidentPadExact, Duration.ofMinutes(2));
    }

    /**
     * 집계 해상도만큼 흐린 신호(메트릭 샘플 · 로그 버킷)의 창 여유.
     * <b>기본이 step과 같다</b> — 버킷 하나가 담는 폭이 곧 시각의 불확실성이다.
     */
    public Duration incidentPadBucketDuration() {
        return parse(incidentPadBucket, Duration.ofMinutes(5));
    }

    /** {@code 5m} · {@code 60s} · {@code 90} (초) 형태를 읽는다. */
    static Duration parse(String raw, Duration fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        String text = raw.trim().toLowerCase();
        try {
            if (text.endsWith("ms")) {
                return Duration.ofMillis(Long.parseLong(text.substring(0, text.length() - 2)));
            }
            if (text.endsWith("h")) {
                return Duration.ofHours(Long.parseLong(text.substring(0, text.length() - 1)));
            }
            if (text.endsWith("m")) {
                return Duration.ofMinutes(Long.parseLong(text.substring(0, text.length() - 1)));
            }
            if (text.endsWith("s")) {
                return Duration.ofSeconds(Long.parseLong(text.substring(0, text.length() - 1)));
            }
            return Duration.ofSeconds(Long.parseLong(text));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    /** 앱 셀렉터를 채운 실제 LogQL. */
    public String logQueryFor(String appsPattern) {
        return logQuery.contains("%s") ? logQuery.formatted(appsPattern) : logQuery;
    }

    private static String blankTo(String value, String fallback) {
        return (value == null || value.isBlank()) ? fallback : value;
    }
}
