package com.yogurtte.rca.triage.plan;

import org.springframework.stereotype.Component;
import com.yogurtte.rca.report.Evidence;
import com.yogurtte.rca.triage.incident.Incident;
import com.yogurtte.rca.triage.incident.Channel;
import com.yogurtte.rca.triage.incident.SignalExtractor;
import com.yogurtte.rca.triage.survey.SurveyResult;

/**
 * 스윕 결과를 탐색 LLM에게 보여줄 하나의 텍스트로 잇는다.
 *
 * <p>{@link com.yogurtte.rca.analyzer.ContextAssembler}와 같은 원칙 — 가공하지 않는다.
 * 다만 넣는 것이 원본이 아니라 집계값이라, 창이 몇 시간이어도 크기가 스텝 수로만 결정된다.
 */
@Component
public class SurveyContextAssembler {

    public String assemble(SurveyResult survey, String question) {
        return assemble(survey, question, java.util.List.of(), true);
    }

    /**
     * @param includeRaw 스윕 원본 JSON 절을 실을지. 대조군 스위치이며 기본은 실는 쪽이다 —
     *                   {@code rca.survey.include-raw} 참조.
     */
    public String assemble(SurveyResult survey, String question,
                           java.util.List<Incident> incidents, boolean includeRaw) {
        StringBuilder sb = new StringBuilder();

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

        appendUntrustedHits(sb, survey);
        appendChannelSummary(sb, survey, incidents);

        if (!survey.failures().isEmpty()) {
            sb.append("# 무신호/실패 목록\n");
            sb.append("아래는 조회했으나 데이터가 없었거나 실패한 것이다. "
                    + "**없다는 사실 자체가 신호일 수 있으니** 그냥 넘기지 말 것.\n");
            survey.failures().forEach(failure -> sb.append("- ").append(failure).append('\n'));
            sb.append('\n');
        }

        if (!includeRaw) {
            // 원본을 빼면 후보 목록이 유일한 판단 재료가 된다. 그 사실을 모델에게 알린다 —
            // 원본이 있다고 가정하고 "확인해 보니"라고 쓰는 답을 막는다.
            sb.append("# 원본 관측 데이터\n");
            sb.append("이 회차는 원본 JSON을 싣지 않는다. 위 후보 목록과 무신호 목록만으로 고를 것.\n");
            return sb.toString();
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

    /**
     * 시각이 깨져 후보가 되지 못한 트레이스. <b>버리지 않고 여기서 보인다</b> — 같은 트레이스를
     * {@code /api/traces/{id}}로 받으면 멀쩡한 사례가 있어(CH-2 실측), 후보에서 뺐다고 도달
     * 경로까지 없애면 안 된다. 창 계산에는 여전히 쓰지 않는다.
     */
    private static void appendUntrustedHits(StringBuilder sb, SurveyResult survey) {
        var broken = survey.traceHits().stream()
                .filter(hit -> !hit.trusted() || hit.startedAt() == null)
                .toList();
        if (broken.isEmpty()) {
            return;
        }
        sb.append("# 후보가 되지 못한 트레이스 (검색 결과의 시각·duration이 깨져 있다)\n");
        sb.append("창 계산에는 쓰지 않았지만 **트레이스 자체는 정상일 수 있다** — "
                + "이 traceId를 지목하면 원본을 다시 받아 조사한다.\n");
        broken.forEach(hit -> sb.append("- ").append(hit.traceId())
                .append("  ").append(hit.rootServiceName()).append(' ').append(hit.rootTraceName())
                .append("  (").append(hit.channel()).append(" 채널")
                .append(hit.startedAt() == null ? " · 시작 시각 없음" : " · duration %,dms 신뢰 불가".formatted(hit.durationMs()))
                .append(")\n"));
        sb.append('\n');
    }

    /**
     * 채널마다 무엇이 도달했는지 한 줄씩. <b>"신호 0"도 사실이다</b> — 이것이 없으면 후보에 없는
     * 대상이 "정상이었다"인지 "조회하지 않았다"인지 구별되지 않아, 배제 근거를 세울 수 없다.
     */
    private static void appendChannelSummary(StringBuilder sb, SurveyResult survey,
                                             java.util.List<Incident> incidents) {
        sb.append("# 채널별 도달 요약\n");
        sb.append("조회는 했는데 신호가 0이면 **그 대상은 이 창에서 정상이었다**는 뜻이다.\n");

        long errorHits = survey.traceHits().stream()
                .filter(hit -> Evidence.TraceHit.CHANNEL_ERROR.equals(hit.channel())).count();
        long slowHits = survey.traceHits().stream()
                .filter(hit -> Evidence.TraceHit.CHANNEL_SLOW.equals(hit.channel())).count();
        sb.append("- Tempo 에러 검색: ").append(errorHits).append("건\n");
        sb.append("- Tempo 지연 검색: ").append(slowHits).append("건\n");
        sb.append("- Loki ERROR/WARN: 신호 ").append(countSignals(incidents, Channel.LOKI)).append("건\n");

        survey.metricsJson().keySet().forEach(query -> {
            String name = SignalExtractor.metricNameOf(query);
            long signals = incidents.stream()
                    .filter(incident -> incident.channel() == Channel.MIMIR)
                    .filter(incident -> name.equals(incident.signature()))
                    .mapToLong(incident -> incident.signals().size())
                    .sum();
            sb.append("- ").append(query).append(": 수집됨 · 이상 신호 ").append(signals).append("건\n");
        });
        sb.append('\n');
    }

    private static long countSignals(java.util.List<Incident> incidents, Channel channel) {
        return incidents.stream()
                .filter(incident -> incident.channel() == channel)
                .mapToLong(incident -> incident.signals().size())
                .sum();
    }

    private static String orMissing(String body) {
        return (body == null || body.isBlank()) ? "(수집 실패 - 데이터 없음)" : body;
    }

    private static String blankTo(String value, String fallback) {
        return (value == null || value.isBlank()) ? fallback : value;
    }
}
