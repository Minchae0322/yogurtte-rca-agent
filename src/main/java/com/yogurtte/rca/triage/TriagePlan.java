package com.yogurtte.rca.triage;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yogurtte.rca.collector.Scope;
import com.yogurtte.rca.collector.TimeWindow;

/**
 * 2단계 — 스윕 결과를 보고 <b>어디를 깊게 볼지</b> 고른 결과.
 *
 * <p>이것이 탐색이 분석에 넘기는 값이고, <b>traceId가 아니다.</b> traceId는 있으면 딸려가는
 * 필드일 뿐이며, 없어도 창과 대상만으로 분석이 성립해야 한다.
 *
 * @param parsed   LLM 응답에서 계획을 실제로 읽어냈는지. false면 스윕 창을 그대로 쓴 것이다.
 * @param notes    파싱 과정에서 보정한 내용 (창 클램프, 필드 누락 등). 채점 때 이게 근거가 된다.
 */
public record TriagePlan(
        TimeWindow window,
        List<String> services,
        String traceId,
        String reason,
        List<String> evidence,
        boolean parsed,
        List<String> notes) {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Pattern FENCED_JSON = Pattern.compile("```(?:json)?\\s*(\\{.*?})\\s*```", Pattern.DOTALL);

    public TriagePlan {
        services = services == null ? List.of() : List.copyOf(services);
        evidence = evidence == null ? List.of() : List.copyOf(evidence);
        notes = notes == null ? List.of() : List.copyOf(notes);
    }

    public Scope toScope() {
        return new Scope(window, services, traceId);
    }

    /**
     * LLM 응답에서 계획을 읽어낸다. <b>실패해도 조사를 멈추지 않는다</b> — 스윕 창 전체를
     * 그대로 분석 범위로 삼고, 그 사실을 {@code notes}에 남긴다. 탐색이 틀렸을 때도
     * "실제로 고른 대상"으로 분석 점수를 매길 수 있어야 하기 때문이다.
     */
    public static TriagePlan parse(String llmText, TimeWindow surveyWindow) {
        var notes = new ArrayList<String>();
        var json = extractJson(llmText);
        if (json == null) {
            notes.add("LLM 응답에서 JSON 계획을 찾지 못해 스윕 창 전체를 분석 범위로 사용했다.");
            return new TriagePlan(surveyWindow, List.of(), null, null, List.of(), false, notes);
        }

        try {
            var node = MAPPER.readTree(json);
            var window = readWindow(node, surveyWindow, notes);
            var services = readStrings(node.get("services"));
            var evidence = readStrings(node.get("evidence"));
            var traceId = readText(node.get("traceId"));
            var reason = readText(node.get("reason"));
            return new TriagePlan(window, services, traceId, reason, evidence, true, notes);
        } catch (Exception e) {
            notes.add("계획 JSON 파싱 실패(" + e.getClass().getSimpleName() + ") — 스윕 창 전체를 사용했다.");
            return new TriagePlan(surveyWindow, List.of(), null, null, List.of(), false, notes);
        }
    }

    /**
     * 좁힌 창은 <b>스윕 창 안으로 클램프</b>한다. 밖으로 나가면 탐색이 근거로 삼은 데이터와
     * 분석이 보는 데이터가 어긋나 판단 과정을 추적할 수 없게 된다.
     */
    private static TimeWindow readWindow(JsonNode node, TimeWindow surveyWindow, List<String> notes) {
        var start = readInstant(node.get("windowStart"));
        var end = readInstant(node.get("windowEnd"));
        if (start == null || end == null || !start.isBefore(end)) {
            notes.add("계획에 유효한 windowStart/windowEnd가 없어 스윕 창을 그대로 사용했다.");
            return surveyWindow;
        }
        var clampedStart = start.isBefore(surveyWindow.start()) ? surveyWindow.start() : start;
        var clampedEnd = end.isAfter(surveyWindow.end()) ? surveyWindow.end() : end;
        if (!clampedStart.equals(start) || !clampedEnd.equals(end)) {
            notes.add("좁힌 창이 스윕 창을 벗어나 잘라냈다: " + start + "~" + end
                    + " → " + clampedStart + "~" + clampedEnd);
        }
        if (!clampedStart.isBefore(clampedEnd)) {
            notes.add("클램프 후 창이 비어 스윕 창을 그대로 사용했다.");
            return surveyWindow;
        }
        return new TimeWindow(clampedStart, clampedEnd);
    }

    private static String extractJson(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        var fenced = FENCED_JSON.matcher(text);
        if (fenced.find()) {
            return fenced.group(1);
        }
        var open = text.indexOf('{');
        var close = text.lastIndexOf('}');
        return (open >= 0 && close > open) ? text.substring(open, close + 1) : null;
    }

    private static Instant readInstant(JsonNode node) {
        var raw = readText(node);
        try {
            return raw == null ? null : Instant.parse(raw);
        } catch (Exception e) {
            return null;
        }
    }

    private static String readText(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        var text = node.asText(null);
        return (text == null || text.isBlank() || "null".equals(text)) ? null : text;
    }

    private static List<String> readStrings(JsonNode node) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
        var values = new ArrayList<String>();
        node.forEach(child -> {
            var text = readText(child);
            if (text != null) {
                values.add(text);
            }
        });
        return values;
    }
}
