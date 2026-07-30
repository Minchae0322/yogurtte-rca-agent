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
        return assemble(survey, question, java.util.List.of());
    }

    public String assemble(SurveyResult survey, String question, java.util.List<Incident> incidents) {
        var sb = new StringBuilder();

        sb.append("# 탐색 대상\n");
        sb.append("질문: ").append(blankTo(question, "(없음)")).append('\n');
        sb.append("조회 시간창: ").append(survey.window().start()).append(" ~ ")
                .append(survey.window().end()).append(" (UTC)\n");
        sb.append("시간창 해석 근거: ").append(blankTo(survey.timeExpression(), "(없음)")).append("\n\n");

        // 코드가 원본에서 뽑아 묶은 후보. 시각·구간은 관측에서 그대로 나온 값이다.
        if (!incidents.isEmpty()) {
            sb.append("# 장애 후보 (코드가 신호를 시각·리소스·지문으로 묶은 것)\n");
            sb.append("이 중에서 **질문의 증상과 맞는 것**을 고른다. 시각을 직접 쓰지 말 것 — "
                    + "조사 창은 고른 후보의 신호 시각에서 자동으로 계산된다.\n");
            sb.append("고르지 않은 것은 사라지지 않고 기록에 남으므로, 판단이 애매하면 "
                    + "함께 고르거나 제외 이유를 적는다.\n\n");
            incidents.forEach(incident -> sb.append(incident.describe()).append('\n'));
        }

        if (!survey.failures().isEmpty()) {
            sb.append("# 무신호/실패 목록\n");
            sb.append("아래는 조회했으나 데이터가 없었거나 실패한 것이다. "
                    + "**없다는 사실 자체가 신호일 수 있으니** 그냥 넘기지 말 것.\n");
            survey.failures().forEach(failure -> sb.append("- ").append(failure).append('\n'));
            sb.append('\n');
        }

        // 두 채널을 따로 보여준다 — "에러로는 안 잡혔는데 지연으로 잡혔다"가 장애 성격이다.
        sb.append("# 에러 트레이스 검색 (Tempo · status = error)\n");
        sb.append(orMissing(survey.traceSearchJson())).append("\n\n");

        sb.append("# 지연 트레이스 검색 (Tempo · duration 기준 · 에러 제외)\n");
        sb.append("에러 없이 느려진 요청이다. 여기만 걸렸다면 실패가 아니라 지연이 증상이다.\n");
        sb.append(orMissing(survey.slowTraceSearchJson())).append("\n\n");

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
