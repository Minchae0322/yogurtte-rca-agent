package com.yogurtte.rca.report;

import java.nio.file.Path;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class ReportConfig {

    @Bean
    public ReportStore reportStore(ReportProperties properties) {
        return new ReportStore(Path.of(properties.dir()));
    }
}
