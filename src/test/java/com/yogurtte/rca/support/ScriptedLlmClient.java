package com.yogurtte.rca.support;

import java.util.ArrayList;
import java.util.List;

import com.yogurtte.rca.llm.LlmClient;
import com.yogurtte.rca.llm.LlmResult;

/**
 * 탐색과 분석 두 번 불리는 모델. 시스템 프롬프트로 어느 단계인지 구별해 다른 답을 준다.
 *
 * <p>단계를 프롬프트 내용으로 가르는 이유는 {@link LlmClient}가 단계를 인자로 받지 않기
 * 때문이다 — 두 진입점이 <b>같은 클라이언트</b>를 쓰는 것이 측정 전제라 그 계약을 테스트용으로
 * 바꿀 수 없다.
 */
public class ScriptedLlmClient implements LlmClient {

    public final List<String> seenContexts = new ArrayList<>();

    /** 탐색 단계에서 돌려줄 답. 테스트가 시나리오마다 갈아끼운다. */
    public String triageAnswer = "";

    @Override
    public LlmResult analyze(String systemPrompt, String context) {
        seenContexts.add(context);
        boolean isTriage = systemPrompt.contains("집계 데이터");
        String text = isTriage ? triageAnswer : "원인 후보 1: MongoDB 다운으로 알림 저장 실패";
        return new LlmResult(text, 100, 50, -1, -1, "fake-model", 1, 10, 0.001);
    }

    @Override
    public String provider() {
        return "fake";
    }

    public String triageContext() {
        return seenContexts.get(0);
    }

    public String analysisContext() {
        return seenContexts.get(1);
    }
}
