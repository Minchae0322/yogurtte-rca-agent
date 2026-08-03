package com.yogurtte.rca.analyzer;

import java.nio.charset.StandardCharsets;
import java.util.List;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

import com.yogurtte.rca.collector.CollectProperties;
import com.yogurtte.rca.collector.CollectedData;
import com.yogurtte.rca.collector.TraceSpans;
import com.yogurtte.rca.report.ServiceGraph;

/**
 * 수집한 모든 것을 하나의 텍스트 덩어리로 이어 붙인다. 가공은 의도적으로 최소화했다 -
 * v0는 모델에게 원본 데이터를 주고 추론을 맡긴다.
 *
 * <p>예외가 둘이다. 호출 그래프 절은 요약을 <b>더하는</b> 것이지 원본 트레이스를 대체하는 것이
 * 아니고 — 코드가 놓친 엣지를 모델이 원본에서 직접 볼 여지를 남긴다 — 로그 중복 접기(B-10)는
 * 두 채널에 같은 레코드가 겹칠 때 <b>두 번째 등장만</b> 뺀다. 정보를 빼는 게 아니라 같은
 * 정보의 재등장을 빼는 것이고, 뺀 자리에는 표식이 남는다.
 */
@RequiredArgsConstructor
@Component
public class ContextAssembler {

    private final CollectProperties properties;
    private final ServiceGraphExtractor graphExtractor;

    public String assemble(CollectedData data, String question) {
        StringBuilder sb = new StringBuilder();

        sb.append("# 조사 대상\n");
        sb.append("조사 ID: ").append(data.correlationId()).append('\n');
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

        ServiceGraph graph = graphExtractor.extract(data);
        if (!graph.isEmpty()) {
            sb.append("# 호출 그래프 (트레이스에서 추출)\n");
            sb.append("span의 부모-자식 관계와 속성에서 유도한 호출 관계다. 같은 엣지의 span들은 한 줄로 "
                    + "집약했고 error·events를 붙였다. 원본 트레이스는 아래 절에 그대로 있다.\n");
            sb.append(graph.toText()).append('\n');
        }

        // 대표를 세우지 않는다 — 어느 것이 원인인지는 전문을 봐야 알고, 하나를 앞세우면
        // 그 선택이 분석의 초점을 끌어간다(AP-1 회차 3 실측).
        sb.append("# 트레이스 (Tempo · ").append(data.traceJsons().size()).append("건)\n");
        if (data.traceJsons().isEmpty()) {
            sb.append("(수집 실패 - 트레이스 없음)\n\n");
        } else {
            sb.append("앞쪽이 탐색이 지목한 것, 뒤가 같은 창에서 함께 수집한 것이다. "
                    + "**순서는 우선순위가 아니라 수집 순서다** — 어느 것이 원인인지는 전문을 보고 판단하라. "
                    + "정상 트레이스와의 차이 자체가 근거가 될 수 있다.\n");
            data.traceJsons().forEach((id, json) -> sb
                    .append("## traceId ").append(id).append('\n')
                    .append(traceSection(json)).append("\n\n"));
        }

        sb.append("# 로그 - ERROR/WARN (Loki)\n");
        sb.append(orMissing(data.errorWarnLogsJson())).append("\n\n");

        LokiLogDedup.Result logs = LokiLogDedup.fold(data.errorWarnLogsJson(), data.traceIdLogsJson());
        sb.append("# 로그 - traceId 일치 (Loki)\n");
        if (logs.folded() > 0) {
            sb.append("(").append(logs.folded())
                    .append("줄은 위 ERROR/WARN 절과 동일한 레코드라 생략했다 — 이 채널로도 도달했다)\n");
        }
        sb.append(orMissing(logs.json())).append("\n\n");

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
        int bytes = traceJson.getBytes(StandardCharsets.UTF_8).length;
        if (bytes <= properties.maxTraceBytes()) {
            return traceJson;
        }
        List<TraceSpans.Span> spans = TraceSpans.parse(traceJson);
        return "(원본 %d bytes로 %d bytes 한도를 초과하여 duration 상위 %d개 span만 포함. 전체 span 수: %d)\n%s"
                .formatted(bytes, properties.maxTraceBytes(), properties.topSpans(), spans.size(),
                        TraceSpans.topByDuration(spans, properties.topSpans()));
    }

    private static String orMissing(String body) {
        return (body == null || body.isBlank()) ? "(수집 실패 - 데이터 없음)" : body;
    }
}
