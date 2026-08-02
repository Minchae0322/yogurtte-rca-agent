package com.yogurtte.rca.client;

import java.nio.file.Path;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.yogurtte.rca.report.ReportProperties;

/** Grafana Cloud 클라이언트 조립 — 엔드포인트별 RestClient 파생이 생성자에서 여기로 올라왔다. */
@Configuration(proxyBeanMethods = false)
public class GrafanaConfig {

    @Bean
    public RawResponseStore rawResponseStore(ReportProperties properties) {
        return new RawResponseStore(Path.of(properties.dir(), "raw"));
    }

    @Bean
    public TempoClient tempoClient(GrafanaProperties properties, RawResponseStore rawStore) {
        return new TempoClient(properties.restClient(properties.tempo()), rawStore);
    }

    @Bean
    public LokiClient lokiClient(GrafanaProperties properties, RawResponseStore rawStore) {
        return new LokiClient(properties.restClient(properties.loki()), rawStore);
    }

    @Bean
    public MimirClient mimirClient(GrafanaProperties properties, RawResponseStore rawStore) {
        return new MimirClient(properties.restClient(properties.mimir()), rawStore);
    }
}
