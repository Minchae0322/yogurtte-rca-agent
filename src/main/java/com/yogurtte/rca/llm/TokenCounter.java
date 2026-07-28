package com.yogurtte.rca.llm;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * 조립된 프롬프트가 실제로 몇 토큰인지를 Anthropic {@code /v1/messages/count_tokens}로 <b>직접</b> 잰다.
 *
 * <p><b>왜 필요한가.</b> provider가 보고하는 {@code inputTokens}에는 claude CLI 자신의 짐
 * (시스템 프롬프트·툴 정의 스키마 등)이 섞여 있다. 컨텍스트를 하나도 주지 않은 프로브에서도
 * 3만 토큰 가까이 잡혔다. 그래서 그 수치는 "내가 만든 입력의 크기"가 아니고, 개선 전후 비교의
 * 분모로 쓸 수 없다.
 *
 * <p>대안으로 {@code in − C}(오버헤드 상수를 빼기)를 검토했으나 폐기했다. C를 6회 기록으로
 * 회귀 추정하면 95% 신뢰구간이 [965, 23,345]로 벌어지고, 한 점만 빼도 추정치가 2.6배 흔들린다.
 * 뺄셈은 피감수의 오차가 결과보다 커서 성립하지 않는다. <b>빼지 말고 직접 재는 것</b>이 답이다.
 *
 * <p>이 값은 CLI 오버헤드도, 캐시 상태도, 턴 수도 타지 않는 결정적 수치라 회차 간 비교가 성립한다.
 * 자세한 논거는 {@code docs/measurement.md}.
 *
 * <p>API 키가 없으면(구독 계정으로 claude CLI만 쓰는 구성) 조용히 -1을 반환한다. 측정이 안 되는
 * 것과 0인 것을 구별해야 하므로 0으로 채우지 않는다.
 */
@Component
public class TokenCounter {

    private static final Logger log = LoggerFactory.getLogger(TokenCounter.class);
    private static final String ENDPOINT = "/v1/messages/count_tokens";

    private final RestClient client;
    private final String fallbackModel;

    public TokenCounter(LlmProperties properties) {
        var anthropic = properties.anthropic();
        var apiKey = anthropic == null ? null : anthropic.apiKey();
        var cli = properties.claudeCli();
        this.fallbackModel = (cli == null || cli.model() == null || cli.model().isBlank())
                ? "claude-opus-5" : cli.model();

        if (apiKey == null || apiKey.isBlank()) {
            this.client = null;
            log.info("token counting 비활성 (ANTHROPIC_API_KEY 없음) — coverage.contextTokens는 -1로 기록된다");
        } else {
            this.client = RestClient.builder()
                    .baseUrl("https://api.anthropic.com")
                    .defaultHeader("x-api-key", apiKey)
                    .defaultHeader("anthropic-version", "2023-06-01")
                    .build();
        }
    }

    /**
     * 시스템 프롬프트 + 컨텍스트의 토큰 수. 못 재면 -1.
     *
     * @param model 응답을 만든 모델. 토크나이저가 모델마다 다르므로 조사에 쓴 모델로 재야 한다.
     */
    public long count(String model, String systemPrompt, String context) {
        if (client == null) {
            return -1L;
        }
        var target = (model == null || model.isBlank()) ? fallbackModel : model;
        try {
            var body = Map.of(
                    "model", target,
                    "system", systemPrompt == null ? "" : systemPrompt,
                    "messages", List.of(Map.of("role", "user", "content", context == null ? "" : context)));
            var response = client.post()
                    .uri(ENDPOINT)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);
            return response == null ? -1L : response.path("input_tokens").asLong(-1L);
        } catch (Exception e) {
            // 조사를 실패시키지 않는다: 이건 계측이지 조사 경로가 아니다.
            log.warn("count_tokens 실패 (model={}): {}", target, e.getMessage());
            return -1L;
        }
    }
}
