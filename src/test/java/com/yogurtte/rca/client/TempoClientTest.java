package com.yogurtte.rca.client;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.yogurtte.rca.report.ReportProperties;

class TempoClientTest {

    private WireMockServer server;
    private TempoClient client;

    @BeforeEach
    void startServer(@org.junit.jupiter.api.io.TempDir java.nio.file.Path tempDir) {
        server = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        server.start();
        WireMock.configureFor("localhost", server.port());

        GrafanaProperties properties = new GrafanaProperties(
                new GrafanaProperties.Endpoint("http://localhost:" + server.port(), "12345"),
                new GrafanaProperties.Endpoint("http://localhost:" + server.port(), "12345"),
                new GrafanaProperties.Endpoint("http://localhost:" + server.port(), "12345"),
                "secret-token", 3000, 10000);

        client = new GrafanaConfig().tempoClient(properties, new GrafanaConfig().rawResponseStore(new ReportProperties(tempDir.toString())));
    }

    @AfterEach
    void stopServer() {
        server.stop();
    }

    @Test
    void fetchesTraceByIdWithBasicAuth() {
        String body = "{\"batches\":[]}";
        server.stubFor(get(urlPathEqualTo("/api/traces/abc123"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(body)));

        assertThat(client.fetchTrace("abc123")).isEqualTo(body);

        String expected = "Basic " + Base64.getEncoder()
                .encodeToString("12345:secret-token".getBytes(StandardCharsets.UTF_8));
        server.verify(getRequestedFor(urlPathEqualTo("/api/traces/abc123"))
                .withHeader("Authorization", equalTo(expected)));
    }

    @Test
    void propagatesServerErrorSoTheCollectorCanRecordTheFailure() {
        server.stubFor(get(urlPathEqualTo("/api/traces/boom"))
                .willReturn(aResponse().withStatus(500)));

        assertThatThrownBy(() -> client.fetchTrace("boom")).isInstanceOf(Exception.class);
    }
}
