package com.yogurtte.rca.analyzer;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.yogurtte.rca.collector.CollectProperties;
import com.yogurtte.rca.collector.CollectedData;

class ContextAssemblerTest {

    private static final CollectProperties COLLECT = new CollectProperties(
            120, "content-service|auth-service|chat-service", "service_name",
            200, "5m", List.of(), 60_000, 20, 3, true);

    private final ContextAssembler assembler = new ContextAssembler(
            COLLECT, new ServiceGraphExtractor(), LogFoldProperties.off(), TraceCompactProperties.off());

    @Test
    void 두_로그_절에_겹친_레코드는_한_번만_실리고_표식이_남는다() {
        CollectedData data = new CollectedData("6a68c522cb16f0a29c2c4bd0a86df613", null,
                fixture("/loki/varchar-errwarn.json"), fixture("/loki/varchar-traceid.json"),
                Map.of(), Map.of(), List.of(), Map.of());

        String context = assembler.assemble(data, "댓글이 안 써져요");

        // 겹친 ERROR 줄은 ERROR/WARN 절에 한 번만 — 접기 전에는 두 번 실렸다(결함 5).
        assertThat(countOf(context, "Data too long for column")).isEqualTo(1);
        // 표식이 채널 도달 사실을 보존한다.
        assertThat(context).contains("(4줄은 위 ERROR/WARN 절과 동일한 레코드라 생략했다");
        // traceId 절 고유 정보(INFO 줄, 00:05:06.387)는 남는다 — 이 시각은 errwarn 채널에 없다.
        assertThat(context).contains("00:05:06.387");
    }

    private static int countOf(String text, String needle) {
        int count = 0;
        for (int i = text.indexOf(needle); i >= 0; i = text.indexOf(needle, i + 1)) {
            count++;
        }
        return count;
    }

    private static String fixture(String path) {
        try (InputStream in = ContextAssemblerTest.class.getResourceAsStream(path)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
