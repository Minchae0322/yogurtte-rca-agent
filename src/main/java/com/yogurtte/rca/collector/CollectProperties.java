package com.yogurtte.rca.collector;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param apps     Loki 셀렉터 값 (정규식 대안). <b>Alloy가 붙이는 실제 값</b>이어야 한다 —
 *                 {@code content-service|auth-service|chat-service}.
 * @param appLabel Loki 셀렉터의 라벨명. 실측 확인된 값은 {@code service_name}이다.
 *                 {@code app} 라벨은 <b>Loki에 존재하지 않고</b>, {@code application}은
 *                 Micrometer common tag라 메트릭 전용이다 — 둘 중 하나를 쓰면 매칭 스트림이
 *                 0개가 되어 조용히 빈 결과가 나온다 (2026-07-26~27 조사 6회가 그 상태였다).
 */
@ConfigurationProperties("rca.collect")
public record CollectProperties(
        int windowPaddingSeconds,
        String apps,
        String appLabel,
        int logLimit,
        String metricStep,
        List<String> metricQueries,
        int maxTraceBytes,
        int topSpans,
        int maxTraces) {

    /**
     * ERROR/WARN 로그. {@code {service_name=~"..."} |~ "ERROR|WARN"}
     *
     * <p><b>라인 필터를 쓴다 — {@code | logfmt | level=~} 가 아니다.</b> 로그가 ANSI 색코드가
     *섞인 평문 Logback({@code logging.pattern.level})이라 {@code logfmt} 파서가 {@code level}
     * 필드를 만들지 못하고, 뒤의 라벨 필터가 전부 걸러낸다. 셀렉터를 고쳐도 이 쿼리만 빈 결과가
     * 나오던 원인이며, 셀렉터 결함과는 <b>독립적인 두 번째 결함</b>이었다.
     *
     * <p>라인 필터는 본문에 ERROR/WARN이 들어간 줄까지 잡는 과대 매칭이 있으나, 0건보다 낫고
     * 레벨 위치를 정규식으로 고정하면 ANSI 이스케이프 때문에 다시 깨진다.
     *
     * <p><b>스택트레이스 줄까지 잡는다.</b> {@code ERROR|WARN} 만으로는 예외의 <b>헤더 줄만</b>
     * 오고 스택은 통째로 빠졌다 — 스택 줄에는 {@code ERROR} 도 없고 <b>traceId도 붙지 않는다</b>
     * (Logback 패턴이 로그 이벤트의 첫 줄에만 적용되고, 수집기가 줄 단위로 받으면 스택은
     * traceId 없는 별개 엔트리가 된다). 그래서 레벨 필터를 뺀 {@code |= "<traceId>"} 로도
     * 도달하지 못한다 — 실측으로 확인됐다(traceId 전량 조회는 INFO 한 줄만, 문자열 검색으로만
     * 스택 전문이 나왔다).
     *
     * <p>예외 메시지가 헤더 줄에 있는 문항(중복 키·varchar 위반)에서는 이 결함이 드러나지 않았다.
     * NPE는 메시지가 비어 <b>정보가 전부 스택에 있다</b> — 정답이 {@code FollowCondition.java:25} 였다.
     *
     * <ul>
     *   <li>{@code ERROR|WARN} — 헤더 줄</li>
     *   <li>{@code Exception} — {@code java.lang.NullPointerException: ...} 줄</li>
     *   <li>{@code \.java:[0-9]+\)} — {@code at ...(FollowCondition.java:25)} 스택 프레임</li>
     *   <li>{@code Caused by} — 중첩 예외</li>
     * </ul>
     *
     * <p>{@code (Foo.java:25)} 형태는 자바 스택 프레임에만 나오는 모양이라 일반 로그를 오탐하지
     * 않는다. 양은 <b>예외가 난 만큼만</b> 늘어난다 — INFO 전량 수집(1시간 2,300줄 중
     * ERROR/WARN 8줄, 약 287배)과는 성질이 다르다.
     *
     * <p><b>스윕 단계 집계 쿼리에는 이 패턴을 넣지 않는다</b>({@code rca.survey.log-query}).
     * 그쪽은 {@code count_over_time} 발생률이라 스택 줄이 건수를 수십 배로 부풀려 버킷 간
     * 비교가 무의미해진다.
     */
    static final String ERROR_LINE_PATTERN = "ERROR|WARN|Exception|Caused by|\\.java:[0-9]+\\)";

    public String errorWarnQuery() {
        return errorWarnQuery(List.of());
    }

    /**
     * 탐색이 대상을 좁혀준 경우 그 서비스들만 본다. 비어 있으면 설정된 전체 앱.
     *
     * <p>패턴을 <b>백틱 문자열</b>로 넘긴다 — LogQL의 큰따옴표 문자열은 이스케이프를 해석해서
     * {@code \.} 를 두 번 겹쳐 써야 하고, 그 자리에서 정규식이 조용히 깨진다.
     */
    public String errorWarnQuery(List<String> services) {
        return "{%s=~\"%s\"} |~ `%s`".formatted(appLabel, appsPattern(services), ERROR_LINE_PATTERN);
    }

    /** 해당 traceId가 찍힌 모든 줄. {@code {service_name=~"..."} |= "<traceId>"} */
    public String traceIdQuery(String traceId) {
        return traceIdQuery(traceId, List.of());
    }

    public String traceIdQuery(String traceId, List<String> services) {
        return "{%s=~\"%s\"} |= \"%s\"".formatted(appLabel, appsPattern(services), traceId);
    }

    /**
     * 셀렉터에 넣을 앱 정규식. 탐색이 준 서비스 목록이 있으면 그것으로 좁히되,
     * <b>설정에 없는 값은 버린다</b> — LLM이 지어낸 이름이 셀렉터에 들어가면 매칭 스트림이
     * 0개가 되어 조용히 빈 결과가 나온다(과거 조사 6회를 로그 0건으로 만든 것과 같은 실패).
     */
    public String appsPattern(List<String> services) {
        if (services == null || services.isEmpty()) {
            return apps;
        }
        var known = List.of(apps.split("\\|"));
        var filtered = services.stream().filter(known::contains).distinct().toList();
        return filtered.isEmpty() ? apps : String.join("|", filtered);
    }
}
