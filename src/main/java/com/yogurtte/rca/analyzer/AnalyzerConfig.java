package com.yogurtte.rca.analyzer;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class AnalyzerConfig {

    @Bean
    public SystemPromptLoader systemPromptLoader(PromptProperties properties) {
        return SystemPromptLoader.from(properties);
    }
}
