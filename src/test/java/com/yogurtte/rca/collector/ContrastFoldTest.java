package com.yogurtte.rca.collector;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * B-47 — 대조군은 루트 서비스 구간만 싣는다.
 *
 * <p>대조군은 성공 트레이스라 실패보다 크다. AP-1 실측으로 정답 트레이스 8,761B 대
 * 대조군 31,272B였고, 커진 몫이 커밋 이후의 비동기 팬아웃(수신·디스패치)이었다.
 * 대조에 필요한 것은 루트 경로의 차분이므로 그 하류를 뺀다.
 */
class ContrastFoldTest {

    /** content-service가 루트이고 chat-service가 하류인 트레이스. AP-1 대조군의 축약형. */
    private static final String TWO_SERVICES = """
            {"batches":[
              {"resource":{"attributes":[
                 {"key":"service.name","value":{"stringValue":"content-service"}}]},
               "scopeSpans":[{"spans":[
                 {"name":"http post /feeds/{feedId}/comments","spanId":"a1"},
                 {"name":"publish user.notifications","spanId":"a2","parentSpanId":"a1"}]}]},
              {"resource":{"attributes":[
                 {"key":"service.name","value":{"stringValue":"chat-service"}}]},
               "scopeSpans":[{"spans":[
                 {"name":"receive","spanId":"b1","parentSpanId":"a2"},
                 {"name":"push-dispatcher#dispatch","spanId":"b2","parentSpanId":"b1"}]}]}
            ]}
            """;

    @Test
    void 하류_서비스를_뺀다() {
        String folded = Collector.foldToRootService(TWO_SERVICES);

        assertThat(folded).contains("content-service", "http post /feeds/{feedId}/comments");
        assertThat(folded).doesNotContain("chat-service", "push-dispatcher#dispatch");
        assertThat(folded.length()).isLessThan(TWO_SERVICES.length());
    }

    @Test
    void 발행_span은_남는다() {
        // AP-1이 근거로 쓴 "성공 트레이스에는 publish span이 있다"가 성립해야 한다.
        // 발행은 발행자(루트 서비스) 쪽에 찍히므로 접어도 남는다.
        assertThat(Collector.foldToRootService(TWO_SERVICES)).contains("publish user.notifications");
    }

    @Test
    void 단일_서비스면_원본_그대로() {
        String one = """
                {"batches":[
                  {"resource":{"attributes":[
                     {"key":"service.name","value":{"stringValue":"auth-service"}}]},
                   "scopeSpans":[{"spans":[{"name":"http get /user/1/following","spanId":"a1"}]}]}
                ]}
                """;

        assertThat(Collector.foldToRootService(one)).isEqualTo(one);
    }

    @Test
    void 루트_span을_못_찾으면_원본_그대로() {
        // 자를 기준이 없으면 근거를 지키는 쪽을 택한다 — 크기보다 보존이 먼저다.
        String noRoot = """
                {"batches":[
                  {"resource":{"attributes":[
                     {"key":"service.name","value":{"stringValue":"a-service"}}]},
                   "scopeSpans":[{"spans":[{"name":"x","spanId":"a1","parentSpanId":"zz"}]}]},
                  {"resource":{"attributes":[
                     {"key":"service.name","value":{"stringValue":"b-service"}}]},
                   "scopeSpans":[{"spans":[{"name":"y","spanId":"b1","parentSpanId":"a1"}]}]}
                ]}
                """;

        assertThat(Collector.foldToRootService(noRoot)).isEqualTo(noRoot);
    }

    @Test
    void 깨진_JSON이면_원본_그대로() {
        assertThat(Collector.foldToRootService("{not json")).isEqualTo("{not json");
    }
}
