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
        int topSpans) {

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
     */
    public String errorWarnQuery() {
        return "{%s=~\"%s\"} |~ \"ERROR|WARN\"".formatted(appLabel, apps);
    }

    /** 해당 traceId가 찍힌 모든 줄. {@code {service_name=~"..."} |= "<traceId>"} */
    public String traceIdQuery(String traceId) {
        return "{%s=~\"%s\"} |= \"%s\"".formatted(appLabel, apps, traceId);
    }
}
