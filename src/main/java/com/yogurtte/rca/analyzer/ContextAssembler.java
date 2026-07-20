package com.yogurtte.rca.analyzer;

import java.nio.charset.StandardCharsets;

import org.springframework.stereotype.Component;

import com.yogurtte.rca.collector.CollectProperties;
import com.yogurtte.rca.collector.CollectedData;
import com.yogurtte.rca.collector.TraceSpans;

/**
 * 수집한 모든 것을 하나의 텍스트 덩어리로 이어 붙인다. 가공은 의도적으로 최소화했다 -
 * v0는 모델에게 원본 데이터를 주고 추론을 맡긴다.
 */
@Component
public class ContextAssembler {

    private final CollectProperties properties;

    public ContextAssembler(CollectProperties properties) {
        this.properties = properties;
    }

    public String assemble(CollectedData data, String question) {
        var sb = new StringBuilder();

        sb.append("# 조사 대상\n");
        sb.append("traceId: ").append(data.traceId()).append('\n');
        sb.append("질문: ").append(question == null || question.isBlank() ? "(없음)" : question).append('\n');
        if (data.window() != null) {
            sb.append("조회 시간창: ").append(data.window().start()).append(" ~ ").append(data.window().end())
                    .append(" (UTC)\n");
        }
        sb.append('\n');

        if (!data.failures().isEmpty()) {
            sb.append("# 수집 실패/누락\n");
            sb.append("아래 데이터는 확보하지 못했다. 결론을 낼 때 이 공백을 감안하라.\n");
            data.failures().forEach(failure -> sb.append("- ").append(failure).append('\n'));
            sb.append('\n');
        }

        sb.append("# 트레이스 (Tempo)\n");
        sb.append(traceSection(data.traceJson())).append("\n\n");

        sb.append("# 로그 - ERROR/WARN (Loki)\n");
        sb.append(orMissing(data.errorWarnLogsJson())).append("\n\n");

        sb.append("# 로그 - traceId 일치 (Loki)\n");
        sb.append(orMissing(data.traceIdLogsJson())).append("\n\n");

        sb.append("# 메트릭 (Mimir)\n");
        if (data.metricsJson().isEmpty()) {
            sb.append("(수집된 메트릭 없음)\n");
        } else {
            data.metricsJson().forEach((query, body) -> sb
                    .append("## ").append(query).append('\n')
                    .append(body).append("\n\n"));
        }

        return sb.toString();
    }

    /** 크기 한도를 넘으면 원본 JSON 대신 가장 긴 span들로 대체한다. 지연 분석에는 그게 핵심이기 때문이다. */
    private String traceSection(String traceJson) {
        if (traceJson == null || traceJson.isBlank()) {
            return "(수집 실패 - 트레이스 없음)";
        }
        var bytes = traceJson.getBytes(StandardCharsets.UTF_8).length;
        if (bytes <= properties.maxTraceBytes()) {
            return traceJson;
        }
        var spans = TraceSpans.parse(traceJson);
        return "(원본 %d bytes로 %d bytes 한도를 초과하여 duration 상위 %d개 span만 포함. 전체 span 수: %d)\n%s"
                .formatted(bytes, properties.maxTraceBytes(), properties.topSpans(), spans.size(),
                        TraceSpans.topByDuration(spans, properties.topSpans()));
    }

    private static String orMissing(String body) {
        return (body == null || body.isBlank()) ? "(수집 실패 - 데이터 없음)" : body;
    }
}
