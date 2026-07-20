package com.yogurtte.rca.report;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("rca.report")
public record ReportProperties(String dir) {
}
