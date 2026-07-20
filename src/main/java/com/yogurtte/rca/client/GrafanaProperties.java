package com.yogurtte.rca.client;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@ConfigurationProperties("rca.grafana")
public record GrafanaProperties(
        Endpoint tempo,
        Endpoint loki,
        Endpoint mimir,
        String token,
        int connectTimeoutMs,
        int readTimeoutMs) {

    public record Endpoint(String url, String user) {
        public boolean configured() {
            return url != null && !url.isBlank();
        }
    }

    /** One RestClient per source: Basic auth (instanceId:token) plus connect/read timeouts. */
    public RestClient restClient(Endpoint endpoint) {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(connectTimeoutMs));
        factory.setReadTimeout(Duration.ofMillis(readTimeoutMs));

        var credentials = endpoint.user() + ":" + (token == null ? "" : token);
        var basic = "Basic " + Base64.getEncoder()
                .encodeToString(credentials.getBytes(StandardCharsets.UTF_8));

        return RestClient.builder()
                .requestFactory(factory)
                .baseUrl(endpoint.url() == null ? "" : endpoint.url())
                .defaultHeader(HttpHeaders.AUTHORIZATION, basic)
                .build();
    }
}
