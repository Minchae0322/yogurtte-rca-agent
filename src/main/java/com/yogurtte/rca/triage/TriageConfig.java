package com.yogurtte.rca.triage;

import java.time.ZoneId;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class TriageConfig {

    @Bean
    public TimeExpressionParser timeExpressionParser(SurveyProperties properties) {
        return new TimeExpressionParser(properties, ZoneId.of(properties.zone()));
    }
}
