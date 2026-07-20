package com.yogurtte.rca.client;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.yogurtte.rca.collector.CollectProperties;
import com.yogurtte.rca.report.ReportProperties;

class LokiClientTest {

    private WireMockServer server;
    private LokiClient client;

    @BeforeEach
    void startServer(@TempDir java.nio.file.Path tempDir) {
        server = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        server.start();

        var endpoint = new GrafanaProperties.Endpoint("http://localhost:" + server.port(), "999");
        var properties = new GrafanaProperties(endpoint, endpoint, endpoint, "tok", 3000, 10000);

        client = new LokiClient(properties, new RawResponseStore(new ReportProperties(tempDir.toString())));
    }

    @AfterEach
    void stopServer() {
        server.stop();
    }

    @Test
    void sendsQueryRangeWithNanosecondBounds() {
        server.stubFor(get(urlPathEqualTo("/loki/api/v1/query_range"))
                .willReturn(aResponse().withStatus(200).withBody("{\"status\":\"success\"}")));

        var start = Instant.parse("2026-07-20T10:00:00Z");
        var end = Instant.parse("2026-07-20T10:05:00Z");

        var body = client.queryRange("t1", "error-warn", "{app=~\"content|auth|chat\"}", start, end, 1000);

        assertThat(body).contains("success");
        server.verify(getRequestedFor(urlPathEqualTo("/loki/api/v1/query_range"))
                .withQueryParam("query", equalTo("{app=~\"content|auth|chat\"}"))
                .withQueryParam("start", equalTo("1784541600000000000"))
                .withQueryParam("end", equalTo("1784541900000000000"))
                .withQueryParam("limit", equalTo("1000")));
    }

    @Test
    void buildsTheTwoConfiguredLogQueries() {
        var properties = new CollectProperties(120, "content|auth|chat", "app", "level",
                1000, "15s", java.util.List.of(), 102400, 30);

        assertThat(properties.errorWarnQuery())
                .isEqualTo("{app=~\"content|auth|chat\"} | logfmt | level=~\"ERROR|WARN\"");
        assertThat(properties.traceIdQuery("abc123"))
                .isEqualTo("{app=~\"content|auth|chat\"} |= \"abc123\"");
    }
}
