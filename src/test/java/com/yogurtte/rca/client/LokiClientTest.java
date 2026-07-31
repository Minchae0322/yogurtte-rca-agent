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

    /**
     * 이 단언은 조사 6회가 로그 0건이던 두 결함을 막는 자리다.
     * <ul>
     *   <li>셀렉터: 라벨명은 {@code service_name}, 값은 {@code *-service}.
     *       {@code app} 라벨은 Loki에 존재하지 않는다.</li>
     *   <li>파싱: 라인 필터({@code |~})를 쓴다. 평문 Logback이라 {@code | logfmt}로는
     *       {@code level} 필드가 안 생겨 뒤의 필터가 전부 걸러냈다.</li>
     *   <li>스택: {@code ERROR|WARN} 만으로는 예외 <b>헤더 줄만</b> 왔다. 스택 줄에는
     *       {@code ERROR} 도 traceId도 없어 어느 쿼리로도 도달하지 못했다 —
     *       패턴 상세는 {@code CollectPropertiesTest}.</li>
     * </ul>
     */
    @Test
    void buildsTheTwoConfiguredLogQueries() {
        var properties = new CollectProperties(120, "content-service|auth-service|chat-service", "service_name",
                1000, "15s", java.util.List.of(), 102400, 30, 3);

        assertThat(properties.errorWarnQuery())
                .isEqualTo("{service_name=~\"content-service|auth-service|chat-service\"} "
                        + "|~ `ERROR|WARN|Exception|Caused by|\\.java:[0-9]+\\)`");
        assertThat(properties.traceIdQuery("abc123"))
                .isEqualTo("{service_name=~\"content-service|auth-service|chat-service\"} |= \"abc123\"");
    }
}
