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
 * <p>예외가 셋이다. 호출 그래프 절은 요약을 <b>더하는</b> 것이지 원본 트레이스를 대체하는 것이
 * 아니고 — 코드가 놓친 엣지를 모델이 원본에서 직접 볼 여지를 남긴다 — 로그 중복 접기(B-10)는
 * 두 채널에 같은 레코드가 겹칠 때 <b>두 번째 등장만</b> 뺀다. 정보를 빼는 게 아니라 같은
 * 정보의 재등장을 빼는 것이고, 뺀 자리에는 표식이 남는다.
 *
 * <p>셋째가 로그 접기({@link LogStackFold} · B-34)다. 이쪽은 <b>인용된 적 없는 것</b>을 접는다 —
 * 라이브러리 프레임이 로그 바이트의 71%인데 리포트 44건에서 인용 0회였고, 인용된 32회는 전부
 * 앱 프레임(공급 3.7%)이었다. 접힌 자리에는 프레임 수와 패키지가, 반복에는 발생 횟수와
 * 시각 범위가 남는다.
 *
 * <p>넷째가 트레이스 압축({@link TraceCompact} · B-35)인데, 이쪽은 <b>아무것도 버리지 않는다</b> —
 * span도 속성도 그대로이고 OTLP 래퍼를 벗기고 같은 값의 반복만 위로 올린다. 로그와 달리
 * "인용 0회라 뺀다"를 쓰지 않은 이유는, 파드가 여러 개가 되는 순간 인용 0회이던 값이
 * <b>어느 인스턴스가 느린가</b>를 가르는 유일한 근거가 되기 때문이다.
 */
@RequiredArgsConstructor
@Component
public class ContextAssembler {

    private final CollectProperties properties;
    private final ServiceGraphExtractor graphExtractor;
    private final LogFoldProperties logFold;
    private final TraceCompactProperties traceCompact;

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
        appendLogs(sb, data.errorWarnLogsJson());

        LokiLogDedup.Result logs = LokiLogDedup.fold(data.errorWarnLogsJson(), data.traceIdLogsJson());
        sb.append("# 로그 - traceId 일치 (Loki)\n");
        if (logs.folded() > 0) {
            sb.append("(").append(logs.folded())
                    .append("줄은 위 ERROR/WARN 절과 동일한 레코드라 생략했다 — 이 채널로도 도달했다)\n");
        }
        appendLogs(sb, logs.json());

        sb.append("# 메트릭 (Mimir)\n");
        if (data.metricsJson().isEmpty()) {
            sb.append("(수집된 메트릭 없음)\n");
        } else if (properties.metricSummary()) {
            // B-25: 전 데이터 포인트 대신 요약. 시리즈마다 값 범위·0 구간·결측·균등 표본이 남는다.
            sb.append("시계열을 요약해 싣는다 — 값 범위와 0이던 구간·결측 구간, 그리고 곡선 모양을 "
                    + "보이는 균등 표본이다. 전 구간 상수인 시리즈는 그 사실만 적었다.\n\n");
            data.metricsJson().forEach((query, body) -> sb
                    .append("## ").append(query).append('\n')
                    .append(EvidenceExtractor.metricSummary(query, body)).append('\n'));
        } else {
            data.metricsJson().forEach((query, body) -> sb
                    .append("## ").append(query).append('\n')
                    .append(body).append("\n\n"));
        }

        return sb.toString();
    }

    /**
     * 로그 절을 접어서 싣되, <b>접은 쪽이 실제로 더 짧을 때만</b> 접는다.
     *
     * <p>표식과 안내문에도 값이 든다 — 접을 것이 거의 없는 작은 응답에서는 안내문 한 줄이
     * 절약분보다 커서 <b>절이 되레 커졌다</b>(실측: 6,104B → 6,468B, +6.0%). 그래서 두 텍스트를
     * 실제로 재보고 짧은 쪽을 싣는다. 이 판정은 절 단위라, 큰 응답의 절감은 그대로 남는다.
     */
    private void appendLogs(StringBuilder sb, String rawJson) {
        LogStackFold.Result folded = LogStackFold.fold(rawJson, logFold);
        if (folded.folded()) {
            String note = foldNote(folded);
            // 바이트로 잰다 — 안내문은 한글이라 글자당 3바이트다. 글자 수로 재면 줄었다고
            // 판정하고도 실제 컨텍스트는 커진다(실측 +2.3%).
            if (utf8(note) + utf8(folded.json()) < utf8(rawJson)) {
                sb.append(note).append(folded.json()).append("\n\n");
                return;
            }
        }
        sb.append(orMissing(rawJson)).append("\n\n");
    }

    /**
     * 접었다는 사실을 절 머리에 밝힌다. <b>모델이 접힌 줄을 "없던 줄"로 읽으면 안 된다</b> —
     * 반복 횟수는 근거로 쓰라고 준 값이고, 접힌 프레임은 필요하면 되짚을 자리다.
     */
    private static String foldNote(LogStackFold.Result folded) {
        return ("(로그 접기: 라이브러리 스택 프레임 %d줄을 `… N frames (패키지)` 표식으로 접었고, "
                + "지문이 같은 블록 %d벌을 `[xN회 …]`로 접었다 — 글자까지 같으면 한 벌, 숫자만 다르면 "
                + "첫 벌과 끝 벌을 싣는다. 색코드·중복 traceId 등 잡음 %dB를 줄에서 뺐다. "
                + "**앱 코드 프레임과 예외 메시지·수치는 원문 그대로다.** "
                + "`xN회`와 시각 범위는 실제 발생 횟수이므로 근거로 써도 된다)\n")
                .formatted(folded.foldedFrames(), folded.foldedBlocks(), folded.strippedBytes());
    }

    /**
     * 압축해서 싣되, 크기 한도를 넘으면 원본 JSON 대신 가장 긴 span들로 대체한다.
     * 지연 분석에는 그게 핵심이기 때문이다.
     *
     * <p><b>압축이 한도 판정보다 먼저다.</b> 같은 트레이스가 압축 후에는 한도 안에 들어와
     * 절삭을 면할 수 있고, 그러면 <b>더 많은 span이 전문으로</b> 실린다 — 압축의 이득은
     * 크기만이 아니라 절삭 회피이기도 하다.
     *
     * <p>절삭 경로는 <b>원본</b>으로 파싱한다({@link TraceSpans}가 OTLP 모양을 안다).
     */
    private String traceSection(String traceJson) {
        if (traceJson == null || traceJson.isBlank()) {
            return "(수집 실패 - 트레이스 없음)";
        }
        TraceCompact.Result compacted = TraceCompact.compact(traceJson, traceCompact);
        String body = traceJson;
        String note = "";
        if (compacted.compacted()) {
            String candidate = compactNote(compacted);
            // 안내문까지 더해서 잰다 — span이 두어 개뿐인 트레이스는 압축분보다 안내문이 커서
            // 절이 되레 늘었다(실측 24건이 1~3B씩). 로그 절과 같은 판정이다.
            if (utf8(candidate) + utf8(compacted.json()) < utf8(traceJson)) {
                body = compacted.json();
                note = candidate;
            }
        }

        int bytes = utf8(body);
        if (bytes <= properties.maxTraceBytes()) {
            return note + body;
        }
        List<TraceSpans.Span> spans = TraceSpans.parse(traceJson);
        return "(원본 %d bytes로 %d bytes 한도를 초과하여 duration 상위 %d개 span만 포함. 전체 span 수: %d)\n%s"
                .formatted(bytes, properties.maxTraceBytes(), properties.topSpans(), spans.size(),
                        TraceSpans.topByDuration(spans, properties.topSpans()));
    }

    /**
     * 표기법이 바뀐 것을 밝힌다. <b>모델이 {@code durNs}를 못 읽으면 지연 분석이 통째로 흔들리므로</b>
     * 무엇이 무엇으로 바뀌었는지 한 줄로 적는다.
     */
    private static String compactNote(TraceCompact.Result compacted) {
        return ("(트레이스 표기 압축: OTLP 속성 래퍼를 벗겨 `\"key\":\"value\"`로 폈고, 끝 시각 대신 "
                + "`durNs`(나노초 소요시간)를 넣었다 — **시작 시각은 절대값 그대로다**. "
                + "이 배치의 모든 span에서 값이 같은 속성 %d개는 `commonSpanAttributes`로 한 번만 적었다 "
                + "(값이 하나라도 다르면 올리지 않으므로, span마다 남아 있는 속성은 실제로 값이 갈린 것이다). "
                + "**span %d개와 속성은 하나도 빠지지 않았다.**)\n")
                .formatted(compacted.hoisted(), compacted.spans());
    }

    private static int utf8(String s) {
        return s.getBytes(StandardCharsets.UTF_8).length;
    }

    private static String orMissing(String body) {
        return (body == null || body.isBlank()) ? "(수집 실패 - 데이터 없음)" : body;
    }
}
