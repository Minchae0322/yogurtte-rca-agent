package com.yogurtte.rca.collector;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("rca.collect")
public record CollectProperties(
        int windowPaddingSeconds,
        String apps,
        String appLabel,
        String levelLabel,
        int logLimit,
        String metricStep,
        List<String> metricQueries,
        int maxTraceBytes,
        int topSpans) {

    /** {app=~"content|auth|chat"} | logfmt | level=~"ERROR|WARN" */
    public String errorWarnQuery() {
        return "{%s=~\"%s\"} | logfmt | %s=~\"ERROR|WARN\"".formatted(appLabel, apps, levelLabel);
    }

    /** {app=~"content|auth|chat"} |= "<traceId>" */
    public String traceIdQuery(String traceId) {
        return "{%s=~\"%s\"} |= \"%s\"".formatted(appLabel, apps, traceId);
    }
}
