package com.yogurtte.rca.analyzer;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 트레이스 압축(B-35)이 <b>아무것도 잃지 않는지</b>를 고정한다.
 *
 * <p>로그 접기 테스트가 "무엇이 안 접히는지"를 고정했다면, 이쪽은 더 강한 것을 고정한다 —
 * <b>span 수·속성 수·시각·소요시간이 원본과 일치</b>해야 한다. 표기법만 바꾸는 변환이므로
 * 하나라도 어긋나면 그것은 버그다.
 *
 * <p>픽스처는 저장된 실제 트레이스 3건이다(jdbc rollback · kafka DLQ · 연결 거부).
 */
class TraceCompactTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String JDBC = fixture("/traces/trace-jdbc-rollback.json");
    private static final String KAFKA = fixture("/traces/trace-kafka-publish-receive-dlq.json");

    @Test
    void span은_하나도_사라지지_않는다() throws IOException {
        // "span이 없다"가 요건인 문항(AU-2)이 실재한다 — 개수가 곧 근거다.
        for (String trace : new String[] {JDBC, KAFKA}) {
            TraceCompact.Result compacted = TraceCompact.compact(trace, TraceCompactProperties.on());

            assertThat(compacted.spans()).isEqualTo(countSpans(MAPPER.readTree(trace)));
            assertThat(countCompactSpans(MAPPER.readTree(compacted.json()))).isEqualTo(compacted.spans());
        }
    }

    @Test
    void 시작_시각은_절대값_그대로이고_소요시간은_끝빼기시작이다() throws IOException {
        JsonNode original = MAPPER.readTree(JDBC);
        JsonNode compacted = MAPPER.readTree(TraceCompact.compact(JDBC, TraceCompactProperties.on()).json());

        JsonNode before = firstSpan(original);
        JsonNode after = firstCompactSpan(compacted);

        // 성능 회차 간 비교와 로그·메트릭 시각 대조의 기준점이라 상대 시각으로 바꾸지 않는다.
        assertThat(after.path("startTimeUnixNano").asText())
                .isEqualTo(before.path("startTimeUnixNano").asText());
        // 나노초 정수라 원본과 비트 단위로 같다 — 반올림이 없다.
        assertThat(after.path("durNs").asLong()).isEqualTo(
                Long.parseLong(before.path("endTimeUnixNano").asText())
                        - Long.parseLong(before.path("startTimeUnixNano").asText()));
    }

    @Test
    void 속성은_이름도_값도_그대로이고_래퍼만_벗겨진다() throws IOException {
        JsonNode before = firstSpan(MAPPER.readTree(JDBC));
        JsonNode compacted = MAPPER.readTree(TraceCompact.compact(JDBC, TraceCompactProperties.on()).json());
        JsonNode batch = compacted.path("batches").get(0);
        JsonNode after = firstCompactSpan(compacted);

        for (JsonNode attribute : before.path("attributes")) {
            String key = attribute.path("key").asText();
            String value = attribute.path("value").fields().next().getValue().asText();
            // span에 남았거나 배치 공통으로 올라갔거나 — 둘 중 하나에는 반드시 있다.
            JsonNode found = after.path("attributes").hasNonNull(key)
                    ? after.path("attributes").get(key)
                    : batch.path("commonSpanAttributes").get(key);
            assertThat(found).as("속성 %s", key).isNotNull();
            assertThat(found.asText()).isEqualTo(value);
        }
    }

    @Test
    void 값이_갈리는_속성은_호이스팅하지_않는다() throws IOException {
        // 이 규칙이 파드 IP 문제를 푼다 — 레플리카가 여러 개면 net.host.ip가 span마다 갈리고,
        // 그때는 위로 올라가지 않고 span에 그대로 남아야 "어느 인스턴스가 느린가"를 짚을 수 있다.
        String twoPods = """
                {"batches":[{"resource":{"attributes":[
                    {"key":"service.name","value":{"stringValue":"content-service"}}]},
                  "scopeSpans":[{"spans":[
                    {"spanId":"a","name":"http get /feeds","startTimeUnixNano":"100","endTimeUnixNano":"200",
                     "attributes":[{"key":"net.host.ip","value":{"stringValue":"10.42.1.43"}},
                                   {"key":"jdbc.datasource.pool","value":{"stringValue":"HikariPool-1"}}]},
                    {"spanId":"b","name":"http get /feeds","startTimeUnixNano":"300","endTimeUnixNano":"900",
                     "attributes":[{"key":"net.host.ip","value":{"stringValue":"10.42.3.11"}},
                                   {"key":"jdbc.datasource.pool","value":{"stringValue":"HikariPool-1"}}]}]}]}]}""";

        JsonNode compacted = MAPPER.readTree(TraceCompact.compact(twoPods, TraceCompactProperties.on()).json());
        JsonNode batch = compacted.path("batches").get(0);

        // 값이 갈린 IP는 span마다 남는다.
        assertThat(batch.path("commonSpanAttributes").has("net.host.ip")).isFalse();
        assertThat(batch.path("scopeSpans").get(0).path("spans").get(0).path("attributes").path("net.host.ip")
                .asText()).isEqualTo("10.42.1.43");
        assertThat(batch.path("scopeSpans").get(0).path("spans").get(1).path("attributes").path("net.host.ip")
                .asText()).isEqualTo("10.42.3.11");
        // 값이 하나뿐인 것만 올라간다.
        assertThat(batch.path("commonSpanAttributes").path("jdbc.datasource.pool").asText())
                .isEqualTo("HikariPool-1");
    }

    @Test
    void 이벤트와_상태는_원문_그대로_남는다() throws IOException {
        JsonNode compacted = MAPPER.readTree(TraceCompact.compact(JDBC, TraceCompactProperties.on()).json());

        // 커넥션 acquired·rollback 같은 이벤트가 호출 그래프의 근거다 — 손대지 않는다.
        assertThat(compacted.toString()).contains("events");
        assertThat(countEvents(compacted)).isEqualTo(countEvents(MAPPER.readTree(JDBC)));
    }

    @Test
    void 끄면_원문_문자열_그대로다() {
        TraceCompact.Result off = TraceCompact.compact(JDBC, TraceCompactProperties.off());

        assertThat(off.json()).isSameAs(JDBC);
        assertThat(off.compacted()).isFalse();
    }

    @Test
    void 깨진_JSON이면_원문을_그대로_돌려준다() {
        assertThat(TraceCompact.compact("깨진 json {", TraceCompactProperties.on()).json()).isEqualTo("깨진 json {");
        assertThat(TraceCompact.compact(null, TraceCompactProperties.on()).json()).isNull();
        assertThat(TraceCompact.compact("{\"batches\":[]}", TraceCompactProperties.on()).spans()).isZero();
    }

    @Test
    void 실제로_작아진다() {
        for (String trace : new String[] {JDBC, KAFKA}) {
            TraceCompact.Result compacted = TraceCompact.compact(trace, TraceCompactProperties.on());

            assertThat(bytes(compacted.json())).isLessThan((int) (bytes(trace) * 0.8));
        }
    }

    private static int countSpans(JsonNode root) {
        int n = 0;
        for (JsonNode batch : root.path("batches")) {
            for (JsonNode scope : batch.path("scopeSpans")) {
                n += scope.path("spans").size();
            }
        }
        return n;
    }

    private static int countCompactSpans(JsonNode root) {
        return countSpans(root);
    }

    private static int countEvents(JsonNode root) {
        int n = 0;
        for (JsonNode batch : root.path("batches")) {
            for (JsonNode scope : batch.path("scopeSpans")) {
                for (JsonNode span : scope.path("spans")) {
                    n += span.path("events").size();
                }
            }
        }
        return n;
    }

    private static JsonNode firstSpan(JsonNode root) {
        return root.path("batches").get(0).path("scopeSpans").get(0).path("spans").get(0);
    }

    private static JsonNode firstCompactSpan(JsonNode root) {
        return firstSpan(root);
    }

    private static int bytes(String s) {
        return s.getBytes(StandardCharsets.UTF_8).length;
    }

    private static String fixture(String path) {
        try (InputStream in = TraceCompactTest.class.getResourceAsStream(path)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
