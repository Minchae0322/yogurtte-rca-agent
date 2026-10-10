package com.yogurtte.rca.mcp;

import lombok.extern.slf4j.Slf4j;

import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.yogurtte.rca.analyzer.ContextAssembler;
import com.yogurtte.rca.collector.CollectProperties;
import com.yogurtte.rca.collector.Collector;
import com.yogurtte.rca.triage.SurveyProperties;
import com.yogurtte.rca.triage.plan.SurveyContextAssembler;
import com.yogurtte.rca.triage.survey.Surveyor;
import com.yogurtte.rca.triage.window.TimeExpressionParser;

/**
 * MCP 노출 조립. <b>기본은 꺼져 있다</b>({@code rca.mcp.enabled}).
 *
 * <p><b>왜 기본 off인가.</b> 회차를 돌리는 구성과 대화형으로 쓰는 구성이 같은 기동에서 섞이면
 * 나중에 "그 회차에 MCP가 떠 있었나"를 확인할 수 없다. 끄면 {@link RcaMcpTools}가 아예
 * 인스턴스화되지 않으므로 툴 정의가 어디에도 생기지 않는다 - 그 사실은
 * {@code McpWiringTest}가 묶어 둔다.
 *
 * <p>조건을 <b>이 파일 한 곳</b>에만 둔다(§6 관례). 구현체에 같은 조건을 또 붙이면 둘이
 * 갈렸을 때 "서버는 떴는데 툴이 없다"가 조용히 성립한다.
 */
@Slf4j
@ConditionalOnProperty(name = "rca.mcp.enabled", havingValue = "true")
@Configuration
public class McpConfig {

    @Bean
    public SurveySessions surveySessions() {
        return new SurveySessions();
    }

    /** 측정 경로가 쓰는 빈을 <b>그대로 주입받는다</b> - 정제 로직이 갈리면 두 경로 비교가 무의미하다. */
    @Bean
    public RcaMcpTools rcaMcpTools(TimeExpressionParser timeParser,
                                   Surveyor surveyor,
                                   SurveyContextAssembler surveyAssembler,
                                   SurveyProperties surveyProperties,
                                   Collector collector,
                                   ContextAssembler assembler,
                                   CollectProperties collectProperties,
                                   SurveySessions sessions) {
        return new RcaMcpTools(timeParser, surveyor, surveyAssembler, surveyProperties,
                collector, assembler, collectProperties, sessions);
    }

    @Bean
    public ToolCallbackProvider rcaToolCallbackProvider(RcaMcpTools tools) {
        ToolCallback[] callbacks = ToolCallbacks.from(tools);
        log.info("rca-agent mcp tools exposed: {}",
                java.util.Arrays.stream(callbacks)
                        .map(c -> c.getToolDefinition().name()).toList());
        return MethodToolCallbackProvider.builder().toolObjects(tools).build();
    }
}
