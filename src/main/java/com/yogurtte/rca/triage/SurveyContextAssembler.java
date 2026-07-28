package com.yogurtte.rca.triage;

import org.springframework.stereotype.Component;

/**
 * 스윕 결과를 탐색 LLM에게 보여줄 하나의 텍스트로 잇는다.
 *
 * <p>{@link com.yogurtte.rca.analyzer.ContextAssembler}와 같은 원칙 — 가공하지 않는다.
 * 다만 넣는 것이 원본이 아니라 집계값이라, 창이 몇 시간이어도 크기가 스텝 수로만 결정된다.
 */
@Component
public class SurveyContextAssembler {

    public String assemble(SurveyResult survey, String question) {
        var sb = new StringBuilder();

        sb.append("# 탐색 대상\n");
        sb.append("질문: ").append(blankTo(question, "(없음)")).append('\n');
        sb.append("조회 시간창: ").append(survey.window().start()).append(" ~ ")
                .append(survey.window().end()).append(" (UTC)\n");
        sb.append("시간창 해석 근거: ").append(blankTo(survey.timeExpression(), "(없음)")).append("\n\n");

        if (!survey.failures().isEmpty()) {
            sb.append("# 무신호/실패 목록\n");
            sb.append("아래는 조회했으나 데이터가 없었거나 실패한 것이다. "
                    + "**없다는 사실 자체가 신호일 수 있으니** 그냥 넘기지 말 것.\n");
            survey.failures().forEach(failure -> sb.append("- ").append(failure).append('\n'));
            sb.append('\n');
        }

        sb.append("# 에러 트레이스 검색 (Tempo)\n");
        sb.append(orMissing(survey.traceSearchJson())).append("\n\n");

        sb.append("# 서비스별 ERROR/WARN 발생률 (Loki)\n");
        sb.append(orMissing(survey.logRatesJson())).append("\n\n");

        sb.append("# 인프라 신호 (Mimir)\n");
        if (survey.metricsJson().isEmpty()) {
            sb.append("(수집된 메트릭 없음)\n");
        } else {
            survey.metricsJson().forEach((query, body) -> sb
                    .append("## ").append(query).append('\n')
                    .append(body).append("\n\n"));
        }

        return sb.toString();
    }

    private static String orMissing(String body) {
        return (body == null || body.isBlank()) ? "(수집 실패 - 데이터 없음)" : body;
    }

    private static String blankTo(String value, String fallback) {
        return (value == null || value.isBlank()) ? fallback : value;
    }
}
