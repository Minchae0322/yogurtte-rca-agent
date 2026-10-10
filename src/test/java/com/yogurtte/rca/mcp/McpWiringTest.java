package com.yogurtte.rca.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

/**
 * MCP 노출 배선 스모크. <b>두 가지를 고정한다</b> - 켜면 툴 둘이 뜨고, <b>끄면 아무것도 안 뜬다</b>.
 *
 * <p>후자가 더 중요하다: 측정 경로({@code POST /diagnose})가 도는 기동에 MCP 툴 정의가 섞이면
 * 회차 구성이 조용히 달라진다. 기본값이 off라는 것을 테스트로 묶어 둔다.
 */
class McpWiringTest {

    @Nested
    @SpringBootTest(properties = {
            "rca.mcp.enabled=true",
            "spring.ai.mcp.server.enabled=true",
            "rca.llm.provider=claude-cli",
            "rca.notify.channel=console"})
    class 켠_경우 {

        @Autowired
        private ApplicationContext context;

        @Test
        void 툴이_둘_뜨고_모델이_넘길_수_있는_것은_질문과_후보_번호뿐이다() {
            ToolCallbackProvider provider = context.getBean(ToolCallbackProvider.class);
            List<String> names = Arrays.stream(provider.getToolCallbacks())
                    .map(c -> c.getToolDefinition().name()).sorted().toList();

            assertThat(names).containsExactly("analysisContext", "surveyContext");

            // §9 경계: 툴 스키마에 창·쿼리·서비스·traceId가 있으면 모델이 그것을 정할 수 있다.
            String schemas = Arrays.stream(provider.getToolCallbacks())
                    .map(ToolCallback::getToolDefinition)
                    .map(d -> d.name() + " " + d.inputSchema())
                    .reduce("", (a, b) -> a + "\n" + b);
            assertThat(schemas).contains("question", "surveyId", "incidentIds");
            assertThat(schemas).doesNotContain("\"from\"", "\"to\"", "\"window\"",
                    "\"query\"", "\"services\"", "\"traceId\"", "\"limit\"");
        }

        @Test
        void 측정_경로의_빈은_그대로_있다() {
            // 이 패키지가 측정 경로를 바꾸지 않는다는 것이 전환의 전제였다.
            assertThat(context.getBeanNamesForType(com.yogurtte.rca.service.RcaService.class)).hasSize(1);
            assertThat(context.getBeanNamesForType(com.yogurtte.rca.llm.LlmClient.class)).hasSize(1);
        }
    }

    @Nested
    @SpringBootTest(properties = {"rca.llm.provider=claude-cli", "rca.notify.channel=console"})
    class 기본값 {

        @Autowired
        private ApplicationContext context;

        @Test
        void 끄면_툴이_하나도_안_뜬다() {
            assertThat(context.getBeanNamesForType(RcaMcpTools.class)).isEmpty();
            assertThat(context.getBeanNamesForType(McpConfig.class)).isEmpty();
            assertThat(context.getBeanNamesForType(SurveySessions.class)).isEmpty();
            // 툴 정의가 어디에도 없어야 한다 - 떠 있으면 그 기동의 구성이 달라진다.
            assertThat(context.getBeanNamesForType(ToolCallbackProvider.class)).isEmpty();
        }
    }
}
