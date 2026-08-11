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
 * @param step                 집계 해상도. <b>쿼리의 range와 반드시 같이 움직여야 한다</b> —
 *                             {@code step}만 줄이고 {@code [5m]}을 두면 값은 "직전 5분"인데
 *                             {@code Signal.from}은 그만큼 안 당겨져 구간이 실제보다 좁게 표시된다.
 *                             <p>5m → <b>1m</b> (2026-08-11). 5분 샘플이 3분짜리 Kafka 다운을
 *                             15분 신호로 부풀려 조사 창이 25분이 됐다(IN-2 실측). 1분 해상도로
 *                             다시 보니 로그도 04:48~04:51 <b>4분</b>에 몰려 있었다.
 *                             <p>토큰과 무관하다 — {@code include-raw=false}라 스윕 응답은
 *                             컨텍스트에 안 실린다(늘어나는 것은 조회 비용과 파싱뿐).
 *                             실측 응답 43,041B → 99,096B · 신호 21 → 76 · 후보 8 → 12.
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
        int incidentLimit,
        String logQuery,
        // 로그 후보에 원인 예외를 붙이는 쿼리(B-45). 기본은 켜짐이고, `off`를 주면 실행하지 않아
        // 기존 동작 그대로다(지문 = ERROR/WARN 하나) — 대조군 팔이 그 값을 쓴다.
        String logSignatureQuery,
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
        step = blankTo(step, "1m");
        traceQuery = blankTo(traceQuery, "{ status = error }");
        // status != error 를 붙여 에러 채널과 겹치지 않게 가른다 — 어느 채널로 도달했는지가 남아야 한다.
        slowTraceQuery = blankTo(slowTraceQuery, "{ duration > %s && status != error }");
        // B-43: 시스템의 최소 타임아웃 상수(Redis 2,000ms)와 같은 자리에 둔다. 3s는 그보다 커서
        // Redis 타임아웃이 만든 2,0xx ms 지연이 통째로 탈락했다(실측: chat 19건).
        // 정확히 2,000ms인 지문은 이 조건에서 빠지지만, 그런 것은 예외가 나므로 에러 채널이 잡는다.
        // 하한은 정상 트래픽 노이즈 바닥(실측 약 500ms)이라 2s는 오탐 0건 구간이다.
        slowTraceThreshold = blankTo(slowTraceThreshold, "2s");
        // B-43: Tempo 검색 상한. 원본은 include-raw=false라 LLM에 실리지 않으므로 토큰과 무관하다.
        // 컨텍스트를 정하는 상한은 이것이 아니라 incidentLimit이다 — 같은 지문은 후보 하나로 묶인다.
        // 200은 실측 근거다 — 회차 5 네 문항의 좁힌 창에서 임계값 초과가 7·10·61·61건이었고,
        // 스윕 창 1시간에서도 61건이다. 최대 실측치의 3배 여유.
        traceLimit = traceLimit <= 0 ? 200 : traceLimit;
        // B-43: LLM이 실제로 읽는 단위의 상한. 트레이스 건수와 비례하지 않는다.
        //
        // 15 유지 (2026-08-11). B-45를 켤 때 잠깐 20·30으로 올렸다가 되돌렸다 —
        // 예외 이름을 후보 <b>키</b>로 쓰던 판에서는 후보가 8 → 12 · CH-1 창 13 → 17로 불어
        // 상한에 걸렸는데(시각 순 절삭에 두 번째 에피소드의 Tempo 후보 둘이 날아갔다),
        // 이름을 키가 아니라 설명 줄로 내리자 후보 수가 불변이 됐다(라이브 8 → 8 · 재생 13 → 13).
        // 상한을 올리는 것은 증상 대응이었고, 원인은 키 설계였다.
        incidentLimit = incidentLimit <= 0 ? 15 : incidentLimit;
        logQuery = blankTo(logQuery,
                "sum by (service_name) (count_over_time({service_name=~\"%s\"} |~ \"ERROR|WARN\" [1m]))");
        // B-45: 로그 후보의 지문을 예외 클래스로 가른다. 기본 on (2026-08-11).
        //
        // 총 건수 곡선(logQuery)을 대체하지 않고 함께 실린다 — 예외가 안 딸린 ERROR/WARN이
        // 08-05 창 실측 78건 중 42건이라, 이 곡선만 쓰면 그만큼이 통째로 사라진다.
        //
        // 라인 필터가 logQuery와 다른 것이 핵심이다. 예외 클래스는 ERROR 헤더 줄이 아니라
        // **그다음 줄**에 있어서 "ERROR|WARN"으로는 닿지 못한다(실측 부착률 5.5%/0%).
        // 클래스명으로 시작하는 줄만 세면 예외 1건 = 1줄이고, "    at ..." 프레임은 공백으로,
        // "Caused by:"는 대문자로 시작해 자동으로 빠진다 — 스택 인플레이션이 없다.
        //
        // 끄려면 RCA_SURVEY_LOG_SIGNATURE_QUERY=off (대조군 팔).
        logSignatureQuery = blankTo(logSignatureQuery, DEFAULT_LOG_SIGNATURE_QUERY);
        metricQueries = metricQueries == null ? List.of() : List.copyOf(metricQueries);
        clusterGap = blankTo(clusterGap, "60s");
        incidentPadExact = blankTo(incidentPadExact, "2m");
        // incidentPadBucket은 일부러 채우지 않는다 - 미설정이어야 incidentPadBucketDuration()의
        // step × 2 유도가 돈다. 여기서 상수를 채우면 그 유도가 죽는다.
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
     *
     * <p><b>기본값을 상수에서 {@code step × 2}로 바꿨다 (2026-08-11).</b> 버킷 하나가 담는 폭이
     * 곧 시각의 불확실성이고, 앞뒤로 하나씩 덮으면 충분하다. 상수로 두면
     * {@code step}을 바꿀 때 pad만 옛 전제에 남는다 — 실제로 5m → 1m 전환 뒤 신호는
     * 15분에서 7분으로 좁혀졌는데 pad가 10분 그대로여서 <b>창의 59%가 여유</b>가 됐다.
     *
     * <p>pad가 컸던 원래 이유는 지금 구조에 없다. CH-3에서 창이 주입 1초 전에 끊겨 세 회차
     * 연속 4점이던 시절에는 <b>모델이 창을 직접 써냈다</b> — 어디서 끊길지 알 수 없어 여유를
     * 크게 줄 수밖에 없었다. 지금은 코드가 신호 시각에서 계산하므로 pad가 덮어야 하는 것은
     * <b>샘플 사이 불확실성 하나</b>이고 그것은 정확히 {@code step}만큼이다.
     *
     * <p>명시적으로 주면 그 값을 쓴다({@code rca.survey.incident-pad-bucket}).
     */
    public Duration incidentPadBucketDuration() {
        return parse(incidentPadBucket, stepDuration().multipliedBy(2));
    }

    /** 집계 해상도. pad·lookback이 여기서 파생되므로 한 곳에서 읽는다. */
    public Duration stepDuration() {
        return parse(step, Duration.ofMinutes(1));
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

    /**
     * 지문 곡선 기본값 — 예외 클래스가 <b>어디에 찍혔든</b> 센다.
     *
     * <p>라인 필터가 두 갈래인 이유는 예외 클래스가 두 자리에 나타나기 때문이다 (실측).
     * <pre>
     * ① 스택 첫 줄     org.springframework.dao.QueryTimeoutException: Redis command…   ← 줄 시작
     * ② 헤더 줄 인라인  ERROR [traceId=…] … com.mongodb.MongoTimeoutException: …        ← 줄 중간
     * </pre>
     * ①만 보면 08-05 창의 {@code MongoTimeoutException} 7건을 놓치고, ②만 보면
     * {@code DataAccessResourceFailureException} 32건을 놓친다. 그래서 합집합이다.
     *
     * <p><b>레벨(ERROR/WARN)은 ①에 걸지 않는다</b> — 스택 첫 줄에는 레벨 토큰이 없다.
     * 그래서 WARN으로 찍힌 예외도 ①이 잡는다. 대신 ②에서는 레벨을 요구해
     * {@code "    at …"} 프레임이 딸려 들어오는 것을 막는다 — <b>예외 1건 = 1줄</b>이 유지된다.
     *
     * <p>{@code | exc !~ `.*\.[a-z]\..*`} 는 Logback이 축약한 <b>로거 이름</b>을 걷어낸다.
     * 축약형은 중간 세그먼트가 한 글자다({@code mc.e.t.a.c.e.GlobalException}) — 이것이
     * 합집합에서 유일하게 들어오던 오탐이었고, 이 필터를 붙이자 두 창 모두 오탐 0이 됐다.
     */
    private static final String DEFAULT_LOG_SIGNATURE_QUERY =
            "sum by (service_name, exc, cause) (count_over_time({service_name=~\"%s\"} "
            + "|~ `^[a-z][\\w.]*\\.[A-Z][\\w$]*(Exception|Error|Throwable)"
            + "|^Caused by: [a-z][\\w.]*\\.[A-Z][\\w$]*(Exception|Error|Throwable)"
            + "|(ERROR|WARN).*[a-z][\\w.]*\\.[A-Z][\\w$]*(Exception|Error|Throwable)` "
            + "| regexp `(?P<cause>Caused by: )?(?P<exc>[a-z][\\w.]*\\.[A-Z][\\w$]*"
            + "(?:Exception|Error|Throwable))` "
            + "| exc !~ `.*\\.[a-z]\\..*` [5m]))";

    /** 대조군 팔로 끄는 값. 빈 문자열은 "설정 안 함"이라 기본값으로 채워지므로 따로 둔다. */
    private static final String OFF = "off";

    /** 지문 쿼리를 쓰는가. {@code RCA_SURVEY_LOG_SIGNATURE_QUERY=off} 면 안 던진다. */
    public boolean hasLogSignatureQuery() {
        return logSignatureQuery != null && !logSignatureQuery.isBlank()
                && !OFF.equalsIgnoreCase(logSignatureQuery.trim());
    }

    /** 앱 셀렉터를 채운 지문 LogQL. {@link #hasLogSignatureQuery()}가 참일 때만 부른다. */
    public String logSignatureQueryFor(String appsPattern) {
        return logSignatureQuery.contains("%s") ? logSignatureQuery.formatted(appsPattern) : logSignatureQuery;
    }

    private static String blankTo(String value, String fallback) {
        return (value == null || value.isBlank()) ? fallback : value;
    }
}
