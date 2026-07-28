package com.yogurtte.rca.llm;

public interface LlmClient {

    LlmResult analyze(String systemPrompt, String context);

    /** 리포트에 기록되는 provider 식별자. */
    String provider();

    /**
     * 이 provider가 요청 하나마다 <b>항상 얹는 고정 오버헤드</b>(토큰). 못 재면 -1.
     *
     * <p><b>왜 회차마다 재는가.</b> 보고된 총 {@code in}에는 우리가 만든 입력과 provider 자신의
     * 몫이 섞여 있어, 개선 지표로 쓰려면 빼야 한다. 그런데 이 값은 <b>상수가 아니다</b> —
     * claude CLI에서 하루 만에 26,626 → 21,247로 20% 움직인 것이 실측됐다(툴 스키마 축소).
     * 문서에 박아둔 숫자를 나중에 빼는 방식은 <b>원리적으로 성립하지 않는다</b>: 지난 회차의
     * 오버헤드는 소급해서 알 수 없다.
     *
     * <p>그래서 조사할 때 같은 조건(같은 명령·모델·샌드박스)으로 한 번 재서 리포트에 함께
     * 남긴다. 그러면 {@code contextTokens = in − overheadTokens}가 <b>그 회차 안에서 닫힌
     * 계산</b>이 되고, 다른 날 상수에 기대지 않는다.
     */
    default long overheadTokens() {
        return -1L;
    }
}
