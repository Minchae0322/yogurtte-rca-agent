package com.yogurtte.rca;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import com.yogurtte.rca.llm.ClaudeCliLlmClient;
import com.yogurtte.rca.llm.LlmClient;
import com.yogurtte.rca.notify.ConsoleNotifier;
import com.yogurtte.rca.notify.Notifier;

/**
 * 빈 배선 스모크 — 조립 로직을 @Configuration으로 옮긴 뒤로 배선 실수는 컴파일이 아니라
 * 기동에서만 드러난다. 그 기동을 여기서 한 번 한다. 다른 테스트들은 전부 수동 조립이라
 * 이 경로를 지나지 않는다.
 */
@SpringBootTest(properties = {"rca.llm.provider=claude-cli", "rca.notify.channel=console"})
class ContextWiringTest {

    @Autowired
    private ApplicationContext context;

    @Test
    void 컨텍스트가_뜨고_조건부_구현체가_하나씩만_선택된다() {
        // §6 계약: @ConditionalOnProperty로 LlmClient·Notifier 구현체 하나만 뜬다.
        assertThat(context.getBean(LlmClient.class)).isInstanceOf(ClaudeCliLlmClient.class);
        assertThat(context.getBean(Notifier.class)).isInstanceOf(ConsoleNotifier.class);
        assertThat(context.getBeanNamesForType(LlmClient.class)).hasSize(1);
        assertThat(context.getBeanNamesForType(Notifier.class)).hasSize(1);
    }
}
