package com.yogurtte.rca.analyzer;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

/**
 * 픽스처는 실제 조사에서 저장된 Loki 원본이다 — varchar 위반 조사
 * ({@code reports/raw/6a68c522…-20260728T151128})에서 박제. 실측 모양이 곧 기대값이다:
 * ERROR/WARN 채널 4줄과 traceId 채널 5줄 중 <b>4줄이 완전히 동일</b>하고,
 * traceId 채널에만 INFO 1줄이 있다.
 */
class LokiLogDedupTest {

    private final String errWarn = fixture("/loki/varchar-errwarn.json");
    private final String traceId = fixture("/loki/varchar-traceid.json");

    @Test
    void 두_채널에_겹친_레코드만_접히고_고유_줄은_남는다() {
        var result = LokiLogDedup.fold(errWarn, traceId);

        assertThat(result.folded()).isEqualTo(4);
        // 겹친 ERROR 줄(varchar 위반 지문)은 빠지고, traceId 채널에만 있던 INFO 줄은 남는다.
        assertThat(result.json()).doesNotContain("Data too long for column");
        assertThat(result.json()).contains("INFO");
        assertThat(result.json()).isNotEqualTo(traceId);
    }

    @Test
    void 겹침이_없으면_원문_문자열_그대로_반환한다() {
        var other = lokiJson("999", "다른 줄");

        var result = LokiLogDedup.fold(errWarn, other);

        assertThat(result.folded()).isZero();
        assertThat(result.json()).isSameAs(other);
    }

    @Test
    void 같은_줄이라도_timestamp가_다르면_별개_레코드로_보고_접지_않는다() {
        // 재시도로 같은 문구가 다른 시각에 찍히면 정당한 두 레코드다.
        var first = lokiJson("100", "connection refused");
        var retry = lokiJson("200", "connection refused");

        var result = LokiLogDedup.fold(first, retry);

        assertThat(result.folded()).isZero();
        assertThat(result.json()).isSameAs(retry);
    }

    @Test
    void 한쪽이_없거나_깨진_JSON이면_원문을_그대로_반환한다() {
        assertThat(LokiLogDedup.fold(null, traceId).json()).isSameAs(traceId);
        assertThat(LokiLogDedup.fold(errWarn, null).json()).isNull();
        assertThat(LokiLogDedup.fold("깨진 json {", traceId).json()).isSameAs(traceId);
        assertThat(LokiLogDedup.fold(errWarn, "깨진 json {").json()).isEqualTo("깨진 json {");
    }

    @Test
    void 전부_겹치면_빈_result가_되고_스트림도_남지_않는다() {
        // errwarn ⊇ traceId인 경우 — traceId 채널의 전 줄이 접힌다.
        var result = LokiLogDedup.fold(traceId, errWarn);

        assertThat(result.folded()).isEqualTo(4);
        assertThat(result.json()).contains("\"result\":[]");
    }

    private static String lokiJson(String ts, String line) {
        return """
                {"status":"success","data":{"resultType":"streams","result":[\
                {"stream":{"service_name":"content-service"},"values":[["%s","%s"]]}]}}"""
                .formatted(ts, line);
    }

    private static String fixture(String path) {
        try (var in = LokiLogDedupTest.class.getResourceAsStream(path)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
