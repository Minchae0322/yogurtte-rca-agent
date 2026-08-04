package com.yogurtte.rca.analyzer;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * 트레이스를 <b>값 하나 버리지 않고</b> 줄인다 (B-35).
 *
 * <p>로그 접기({@link LogStackFold})와 성격이 다르다. 로그는 <b>인용된 적 없는 것</b>을 접었지만,
 * 트레이스는 <b>같은 값을 반복해서 적는 것</b>을 접는다. span을 고르거나 속성을 지우지 않는다 —
 * 회차 5 문서가 트레이스를 뒤로 미룬 이유가 <i>"AU-2류의 'span이 없다'가 요건인 문항에서 부재
 * 근거가 죽는다"</i> 였고, 그것은 <b>요약</b>의 위험이지 <b>재인코딩</b>의 위험이 아니다.
 *
 * <p>세 가지를 한다. 저장된 트레이스 238건(1,863,242B) 실측 기준 트레이스 JSON <b>-30.0%</b>,
 * 표기가 바뀐 것을 알리는 안내문까지 넣은 절 전체로는 <b>-21.2%</b>다.
 * <ol>
 *   <li><b>OTLP 속성 래퍼 평탄화</b> (-25.4%p · 빈 {@code status:{}} 생략 포함) — {@code {"key":"status","value":{"stringValue":"200"}}}
 *       45B가 {@code "status":"200"} 14B가 된다. <b>표기법만 바뀐다.</b></li>
 *   <li><b>끝 시각 → {@code durNs}</b> (-2.5%p) — {@code 시작 + 소요 = 끝}이라 정보량은 같다.
 *       <b>시작 시각은 절대값(나노초) 그대로 둔다</b> — 성능 회차 간 비교와 로그·메트릭 시각
 *       대조의 기준점이라, 상대 시각으로 바꾸면 그 기준이 사라진다.
 *       소요시간도 나노초 정수라 원본과 비트 단위로 같다.</li>
 *   <li><b>배치 공통 속성 호이스팅</b> (-2.2%p · 실측 237개 속성) — 그 배치의 <b>모든 span에서 값이 하나뿐인</b>
 *       속성만 {@code commonSpanAttributes}로 한 번 올린다.</li>
 * </ol>
 *
 * <p><b>③이 파드 IP 문제를 푸는 방식이 이 클래스의 핵심이다.</b> {@code net.host.ip}는 span
 * 1,992개 전부에 붙어 바이트의 13.7%를 먹고 리포트 인용은 0회지만, <b>지우면 안 된다</b> —
 * 레플리카가 여러 개인 순간 <i>"5개 파드 중 하나만 느리다"</i> 를 가르는 유일한 값이 된다.
 * 호이스팅은 그 판단을 데이터에 맡긴다.
 *
 * <pre>
 * 레플리카 1개 → 트레이스 안에서 IP가 하나뿐 → 위로 올라간다 (반복만 사라진다)
 * 레플리카 N개 → IP가 여러 개              → 조건에 안 걸려 span마다 그대로 남는다
 * </pre>
 *
 * 실측으로 확인한 현재 상태는 앞쪽이다 — 트레이스 238건 중 한 서비스가 두 개 이상 IP로 나타난
 * 트레이스는 <b>0건</b>이었다(파드명 해시가 다른 것은 동시 레플리카가 아니라 재배포다).
 * 그래도 고정 제외 목록을 쓰지 않는 이유는, 그 목록이 <b>"지금 파드가 1개"라는 조건에
 * 의존하는 절감</b>이기 때문이다.
 *
 * <p>{@link LogStackFold}와 같은 제약을 지킨다 — 어셈블 단계에서만 바꾸고 {@code reports/raw/}
 * 원본은 손대지 않으며, 파싱이 실패하면 원문 문자열을 그대로 돌려준다.
 */
final class TraceCompact {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private TraceCompact() {
    }

    /**
     * @param json     압축된 JSON. 압축하지 않았으면 원문 그대로다.
     * @param spans    다룬 span 수
     * @param hoisted  배치 공통으로 올린 속성 수 (배치마다 합산)
     */
    record Result(String json, int spans, int hoisted) {

        boolean compacted() {
            return spans > 0;
        }
    }

    static Result compact(String traceJson, TraceCompactProperties props) {
        if (traceJson == null || traceJson.isBlank() || props == null || !props.enabled()) {
            return new Result(traceJson, 0, 0);
        }
        try {
            JsonNode root = MAPPER.readTree(traceJson);
            if (!(root.path("batches") instanceof ArrayNode batches) || batches.isEmpty()) {
                return new Result(traceJson, 0, 0);
            }

            ArrayNode out = MAPPER.createArrayNode();
            int spanCount = 0;
            int hoistedCount = 0;
            for (JsonNode batch : batches) {
                ObjectNode compactBatch = MAPPER.createObjectNode();
                compactBatch.set("resource", flatten(batch.path("resource").path("attributes")));

                // span을 먼저 모아 둔다 — 공통 속성 판정에 배치 전체가 필요하다.
                List<ObjectNode> spans = new ArrayList<>();
                ArrayNode scopeSpans = MAPPER.createArrayNode();
                for (JsonNode scopeSpan : batch.path("scopeSpans")) {
                    ObjectNode compactScope = MAPPER.createObjectNode();
                    if (scopeSpan.has("scope")) {
                        compactScope.set("scope", scopeSpan.get("scope"));
                    }
                    ArrayNode compactSpans = MAPPER.createArrayNode();
                    for (JsonNode span : scopeSpan.path("spans")) {
                        ObjectNode compactSpan = compactSpan(span);
                        spans.add(compactSpan);
                        compactSpans.add(compactSpan);
                        spanCount++;
                    }
                    compactScope.set("spans", compactSpans);
                    scopeSpans.add(compactScope);
                }

                Map<String, JsonNode> common = commonAttributes(spans);
                if (!common.isEmpty()) {
                    ObjectNode commonNode = MAPPER.createObjectNode();
                    common.forEach(commonNode::set);
                    compactBatch.set("commonSpanAttributes", commonNode);
                    for (ObjectNode span : spans) {
                        ObjectNode attributes = (ObjectNode) span.get("attributes");
                        common.keySet().forEach(attributes::remove);
                        if (attributes.isEmpty()) {
                            span.remove("attributes");
                        }
                    }
                    hoistedCount += common.size();
                }

                compactBatch.set("scopeSpans", scopeSpans);
                out.add(compactBatch);
            }

            if (spanCount == 0) {
                return new Result(traceJson, 0, 0);
            }
            ObjectNode compacted = MAPPER.createObjectNode();
            compacted.set("batches", out);
            return new Result(MAPPER.writeValueAsString(compacted), spanCount, hoistedCount);
        } catch (Exception e) {
            // 압축은 최적화다 — 깨진 JSON 때문에 트레이스를 잃는 쪽이 부풀어 있는 것보다 나쁘다.
            return new Result(traceJson, 0, 0);
        }
    }

    /** span 하나. 필드 이름은 OTLP 그대로 두고 <b>끝 시각만</b> 소요시간으로 바꾼다. */
    private static ObjectNode compactSpan(JsonNode span) {
        ObjectNode out = MAPPER.createObjectNode();
        copyIfPresent(span, out, "traceId", "spanId", "parentSpanId", "name", "kind");

        String start = span.path("startTimeUnixNano").asText(null);
        if (start != null) {
            out.put("startTimeUnixNano", start);
            long durNs = durationNanos(start, span.path("endTimeUnixNano").asText(null));
            if (durNs >= 0) {
                out.put("durNs", durNs);
            } else {
                copyIfPresent(span, out, "endTimeUnixNano");
            }
        }

        out.set("attributes", flatten(span.path("attributes")));
        copyIfPresent(span, out, "status", "events", "links");
        return out;
    }

    /** {@code 끝 - 시작}. 값이 없거나 파싱이 안 되면 -1을 돌려 끝 시각을 원문대로 남긴다. */
    private static long durationNanos(String start, String end) {
        if (end == null) {
            return -1;
        }
        try {
            long duration = Long.parseLong(end) - Long.parseLong(start);
            return duration >= 0 ? duration : -1;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /** {@code [{"key":"a","value":{"stringValue":"b"}}]} → {@code {"a":"b"}} */
    private static ObjectNode flatten(JsonNode attributes) {
        ObjectNode out = MAPPER.createObjectNode();
        for (JsonNode attribute : attributes) {
            String key = attribute.path("key").asText(null);
            if (key == null) {
                continue;
            }
            JsonNode value = attribute.path("value");
            // value는 타입 이름 하나를 감싼 객체다(stringValue·intValue·boolValue…).
            // 껍데기만 벗기고 값은 타입 그대로 넘긴다 — 문자열로 뭉개면 숫자·불리언이 바뀐다.
            if (value.isObject() && value.size() == 1) {
                out.set(key, value.properties().iterator().next().getValue());
            } else if (!value.isMissingNode()) {
                out.set(key, value);
            }
        }
        return out;
    }

    /**
     * 배치의 <b>모든 span에 있고 값이 하나뿐인</b> 속성. 하나라도 값이 다르면 올리지 않는다 —
     * 그 다름이 곧 근거이기 때문이다(파드 IP가 갈리는 순간이 "어느 인스턴스가 느린가"다).
     */
    private static Map<String, JsonNode> commonAttributes(List<ObjectNode> spans) {
        if (spans.size() < 2) {
            return Map.of();
        }
        LinkedHashMap<String, JsonNode> candidates = new LinkedHashMap<>();
        JsonNode first = spans.get(0).path("attributes");
        first.properties().forEach(entry -> candidates.put(entry.getKey(), entry.getValue()));

        for (ObjectNode span : spans.subList(1, spans.size())) {
            JsonNode attributes = span.path("attributes");
            Set<String> drop = new HashSet<>();
            for (Map.Entry<String, JsonNode> candidate : candidates.entrySet()) {
                JsonNode value = attributes.get(candidate.getKey());
                if (value == null || !value.equals(candidate.getValue())) {
                    drop.add(candidate.getKey());
                }
            }
            drop.forEach(candidates::remove);
            if (candidates.isEmpty()) {
                break;
            }
        }
        return candidates;
    }

    /**
     * <b>빈 컨테이너는 옮기지 않는다.</b> OTLP는 상태가 없는 span에도 {@code "status":{}}를 적는데,
     * 필드가 없는 것과 빈 것은 읽는 쪽에서 같은 뜻이라 정보가 아니다 — span 1,992개 중 105개만
     * 실제 상태값을 갖고 있었다.
     */
    private static void copyIfPresent(JsonNode from, ObjectNode to, String... fields) {
        for (String field : fields) {
            JsonNode value = from.get(field);
            if (value == null || (value.isContainerNode() && value.isEmpty())) {
                continue;
            }
            to.set(field, value);
        }
    }
}
