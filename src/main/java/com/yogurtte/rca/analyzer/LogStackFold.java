package com.yogurtte.rca.analyzer;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * 로그를 세 방향으로 접는다 (B-34) — <b>세로(라이브러리 프레임) · 가로(반복 블록) · 줄 안(잡음)</b>.
 *
 * <p>버릴 것을 인상으로 고른 것이 아니라 <b>쓰인 것을 세고 나머지를 접었다.</b> 두 번 쟀다.
 * <ul>
 *   <li><b>공급</b> — {@code error-warn} 응답 1000줄에서 라이브러리 프레임이 903줄(바이트의 71%),
 *       앱 프레임은 50줄(3.7%)이었다. 고유 줄은 1000줄 중 191줄(중복률 81%).</li>
 *   <li><b>수요</b> — 리포트 44건의 분석 텍스트가 인용한 코드 위치 32회는 <b>전부 앱 코드</b>였고
 *       라이브러리 프레임 인용은 0회였다. 예외 클래스명은 29회 인용됐다.</li>
 * </ul>
 * 71%를 공급하는 쪽의 수요가 0이고 3.7%를 공급하는 쪽의 수요가 32회다 — 그 역전이 이 클래스의 전부다.
 *
 * <p>지키는 규칙은 다섯이다.
 * <ul>
 *   <li><b>앱 프레임은 전부 남긴다.</b> IN-1 회차 3이 {@code UserCacheStore:49 →
 *       ExternalUserInfoService:108 → FeedService:138} 세 줄로 원인 기전을 특정했다.</li>
 *   <li><b>앱 프레임 경계 ±1 프레임도 남긴다.</b> 인용 공동 2위 {@code RedisLockProvider}가
 *       {@code net.javacrumbs.shedlock…} 서드파티다 — 접두 목록만으로는 잘려나간다.</li>
 *   <li><b>메시지의 숫자를 지우거나 바꾸지 않는다.</b> <i>"Command timed out after 2 second(s)"</i> ·
 *       <i>"Remaining time: 29999 ms"</i> 같은 상수 위에 회차 4의 근거가 서 있다.
 *       숫자는 <b>반복을 판정할 때만</b> 무시하고, 실리는 텍스트는 언제나 원문이다.
 *       템플릿 마이닝(Drain류)이 하는 <b>숫자를 {@code <*>}로 치환해 싣는 것</b>은 여기서 독이다.</li>
 *   <li><b>근사 반복은 첫 벌과 끝 벌을 남긴다.</b> 숫자만 다른 반복(카프카 재시도 로그 405줄 등)에서
 *       가운데만 접으면 값의 양 끝(시작값·마지막값)이 그대로 남는다.</li>
 *   <li><b>접은 자리에 표식을 남긴다.</b> {@code … 14 frames (org.springframework, io.lettuce)} ·
 *       {@code [x47회 · … · 평균 2.0초 간격]}. 무엇이 접혔는지 모델이 알아야 하고,
 *       반복 횟수는 <b>정보를 빼는 게 아니라 더하는 것</b>이다 — 지금은 모델이 원문을 세어
 *       "2초 간격"을 알아낸다(IN-1 회차 3이 실제로 손으로 셌다).</li>
 * </ul>
 *
 * <p>접는 위치는 {@link LokiLogDedup}과 같다 — <b>어셈블 단계에서만</b>. {@code reports/raw/}는
 * 채점자가 "이 신호가 실제로 도달했는가"를 감사하는 자료라 손대지 않는다(B-10이 세운 제약).
 * 접을 것이 없거나 파싱이 실패하면 <b>원문 문자열을 그대로</b> 돌려준다 — 재직렬화만으로도
 * 바이트가 미묘하게 달라져 토큰 축 비교가 흔들린다.
 */
final class LogStackFold {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final DateTimeFormatter HMS =
            DateTimeFormatter.ofPattern("HH:mm:ss.SSS", Locale.ROOT).withZone(ZoneOffset.UTC);

    /** 표식에 나열할 패키지 수 상한. 넘으면 뒤는 생략한다. */
    private static final int MAX_PACKAGES_IN_MARKER = 3;

    /** 터미널 색코드. 로그 바이트의 4.0%인데 정보량은 0이다. */
    private static final Pattern ANSI = Pattern.compile("\\u001B\\[[0-9;]*m");

    /**
     * Logback 패턴이 한 줄에 두 번 찍는 traceId-spanId 중 <b>둘째</b>. 첫째는
     * {@code [traceId=…,spanId=…,userId=…]} 로 남으므로 값 자체는 잃지 않는다 (2.4%).
     */
    private static final Pattern DUPLICATE_IDS = Pattern.compile(" \\[([0-9a-f]{32})-([0-9a-f]{16})\\]");

    /** {@code --- [chat-service] } — 스트림 라벨 {@code service_name}과 같은 값이다 (1.1%). */
    private static final Pattern SERVICE_BLOCK = Pattern.compile("--- \\[[A-Za-z0-9._-]+\\] ");

    /** 로거명 정렬 공백. 3칸 이상만 줄인다 — 메시지 본문의 의도적 간격은 건드리지 않는다. */
    private static final Pattern PAD = Pattern.compile(" {3,}");

    /** 반복 판정용 정규화 — 숫자와 긴 16진수(traceId·spanId·offset)를 지문에서만 지운다. */
    private static final Pattern DIGITS = Pattern.compile("\\d+");
    private static final Pattern HEX_ID = Pattern.compile("\\b[0-9a-f]{16,32}\\b");

    private LogStackFold() {
    }

    /**
     * @param json          접힌 뒤의 JSON. 접힌 것이 없으면 원문 그대로다.
     * @param foldedFrames  요약 줄로 접힌 라이브러리 프레임 줄 수
     * @param foldedBlocks  반복이라 접힌 블록 수 (첫 벌·끝 벌은 남으므로 <b>제거된 벌 수</b>다)
     * @param strippedBytes 줄 안 잡음(ANSI·중복 id·서비스명 블록·정렬 공백)으로 뺀 바이트 수
     */
    record Result(String json, int foldedFrames, int foldedBlocks, long strippedBytes) {

        boolean folded() {
            return foldedFrames > 0 || foldedBlocks > 0 || strippedBytes > 0;
        }
    }

    static Result fold(String logJson, LogFoldProperties props) {
        if (logJson == null || logJson.isBlank() || props == null || !props.enabled()) {
            return new Result(logJson, 0, 0, 0);
        }
        try {
            JsonNode root = MAPPER.readTree(logJson);
            if (!(root.path("data").path("result") instanceof ArrayNode streams)
                    || !(root.get("data") instanceof ObjectNode dataNode)) {
                return new Result(logJson, 0, 0, 0);
            }
            // 로그(streams)에만 적용한다. matrix/vector는 같은 모양의 values 배열을 쓰지만
            // 내용이 [ts, "4"] 같은 집계값이라, 줄로 읽으면 같은 값이 반복이라며 접히고
            // 표식이 길어 오히려 커진다 — 스윕 발생률 응답에서 실제로 +9.8%가 났다.
            if (!"streams".equals(root.path("data").path("resultType").asText())) {
                return new Result(logJson, 0, 0, 0);
            }

            ArrayNode foldedStreams = MAPPER.createArrayNode();
            int frames = 0;
            int blocks = 0;
            long stripped = 0;
            for (JsonNode stream : streams) {
                if (!(stream instanceof ObjectNode streamNode)
                        || !(stream.get("values") instanceof ArrayNode values)) {
                    foldedStreams.add(stream);
                    continue;
                }
                StreamResult folded = foldStream(values, props);
                frames += folded.foldedFrames();
                blocks += folded.foldedBlocks();
                stripped += folded.strippedBytes();
                streamNode.set("values", folded.values());
                foldedStreams.add(streamNode);
            }

            if (frames == 0 && blocks == 0 && stripped == 0) {
                return new Result(logJson, 0, 0, 0);
            }
            dataNode.set("result", foldedStreams);
            return new Result(MAPPER.writeValueAsString(root), frames, blocks, stripped);
        } catch (Exception e) {
            // 접기는 최적화다 — 깨진 JSON 때문에 데이터를 잃는 쪽이 부풀어 있는 것보다 나쁘다.
            return new Result(logJson, 0, 0, 0);
        }
    }

    private record StreamResult(ArrayNode values, int foldedFrames, int foldedBlocks, long strippedBytes) {
    }

    /** Loki의 {@code values}는 {@code ["<unix ns>", "<줄 내용>"]} 쌍의 배열이다. */
    private record Entry(String ts, String line) {
    }

    /** 예외 한 벌 = 헤더 한 줄 + 뒤따르는 프레임·{@code Caused by}·{@code ...} 줄들. */
    private record Block(List<Entry> entries) {

        String text() {
            StringBuilder sb = new StringBuilder();
            entries.forEach(e -> sb.append(e.line()).append('\n'));
            return sb.toString();
        }

        /** 숫자·긴 16진수를 지운 지문. <b>판정에만</b> 쓰고 싣는 텍스트는 언제나 원문이다. */
        String signature() {
            return HEX_ID.matcher(DIGITS.matcher(text()).replaceAll("#")).replaceAll("#");
        }

        long startNanos() {
            return nanosOf(entries.get(0).ts());
        }
    }

    private static StreamResult foldStream(ArrayNode values, LogFoldProperties props) {
        List<Entry> entries = new ArrayList<>();
        long stripped = 0;
        for (JsonNode value : values) {
            if (!value.isArray() || value.size() < 2) {
                // 모양이 다르면 접지 않는다 — 원본을 그대로 통과시키기 위해 빈 결과로 돌린다.
                return new StreamResult(values, 0, 0, 0);
            }
            String line = value.get(1).asText();
            if (props.stripLineNoise()) {
                String clean = stripNoise(line);
                stripped += bytes(line) - bytes(clean);
                line = clean;
            }
            entries.add(new Entry(value.get(0).asText(), line));
        }

        // ① 세로 — 블록 안의 라이브러리 프레임 연속 구간을 요약 줄 하나로.
        int foldedFrames = 0;
        List<Block> blocks = new ArrayList<>();
        for (Block block : split(entries)) {
            Block collapsed = collapseFrames(block, props);
            foldedFrames += block.entries().size() - collapsed.entries().size();
            blocks.add(collapsed);
        }

        // ② 가로 — 지문이 같은 블록을 첫 벌(+숫자가 다르면 끝 벌) + 발생 통계로.
        LinkedHashMap<String, List<Block>> bySignature = new LinkedHashMap<>();
        for (Block block : blocks) {
            bySignature.computeIfAbsent(block.signature(), k -> new ArrayList<>()).add(block);
        }

        ArrayNode out = MAPPER.createArrayNode();
        int foldedBlocks = 0;
        for (List<Block> repeats : bySignature.values()) {
            Block first = repeats.get(0);
            Block last = repeats.get(repeats.size() - 1);
            boolean identical = repeats.stream().allMatch(b -> b.text().equals(first.text()));

            if (repeats.size() < 2 || (!identical && !nearFoldable(repeats.size(), props))) {
                repeats.forEach(block -> block.entries().forEach(e -> out.add(entryNode(e.ts(), e.line()))));
                continue;
            }

            // 글자까지 같으면 첫 벌만, 숫자만 다르면 첫 벌과 끝 벌을 원문 그대로 싣는다.
            int kept = identical ? 1 : 2;
            foldedBlocks += repeats.size() - kept;
            out.add(entryNode(first.entries().get(0).ts(), marker(repeats, identical)));
            first.entries().forEach(e -> out.add(entryNode(e.ts(), e.line())));
            if (!identical) {
                last.entries().forEach(e -> out.add(entryNode(e.ts(), e.line())));
            }
        }
        return new StreamResult(out, foldedFrames, foldedBlocks, stripped);
    }

    private static boolean nearFoldable(int size, LogFoldProperties props) {
        return props.nearRepeatMin() > 0 && size >= props.nearRepeatMin();
    }

    /**
     * 정보가 0인 것만 뺀다 — 색코드 · 한 줄에 두 번 찍힌 id · 스트림 라벨과 같은 서비스명 ·
     * 정렬 공백. <b>스레드명·로거명·메시지·숫자는 건드리지 않는다</b>
     * ({@code [reactor-http-epoll-1]}처럼 스레드가 곧 근거인 문항이 있다 · AU-4).
     */
    private static String stripNoise(String line) {
        String out = ANSI.matcher(line).replaceAll("");

        Matcher ids = DUPLICATE_IDS.matcher(out);
        if (ids.find() && out.contains("traceId=" + ids.group(1))) {
            out = out.substring(0, ids.start()) + out.substring(ids.end());
        }
        out = SERVICE_BLOCK.matcher(out).replaceAll("");
        return PAD.matcher(out).replaceAll(" ");
    }

    /**
     * 헤더 줄에서 블록을 끊는다. 프레임·{@code Caused by}·{@code ... N common frames omitted}는
     * 앞 블록에 붙는다. 헤더 없이 시작하면 그 줄 자체가 한 블록이다.
     */
    private static List<Block> split(List<Entry> entries) {
        List<Block> blocks = new ArrayList<>();
        List<Entry> current = null;
        for (Entry entry : entries) {
            if (isContinuation(entry.line()) && current != null) {
                current.add(entry);
                continue;
            }
            current = new ArrayList<>();
            current.add(entry);
            blocks.add(new Block(current));
        }
        return blocks;
    }

    private static boolean isContinuation(String line) {
        String s = line.stripLeading();
        return s.startsWith("at ") || s.startsWith("...") || s.startsWith("… ")
                || s.startsWith("Caused by:") || s.startsWith("Suppressed:");
    }

    private static boolean isFrame(String line) {
        return line.stripLeading().startsWith("at ");
    }

    private static Block collapseFrames(Block block, LogFoldProperties props) {
        List<Entry> entries = block.entries();
        boolean[] app = new boolean[entries.size()];
        boolean anyFrame = false;
        for (int i = 0; i < entries.size(); i++) {
            boolean frame = isFrame(entries.get(i).line());
            anyFrame |= frame;
            app[i] = frame && isAppFrame(entries.get(i).line(), props.appPackages());
        }
        if (!anyFrame) {
            return block;
        }

        List<Entry> out = new ArrayList<>();
        int i = 0;
        while (i < entries.size()) {
            Entry entry = entries.get(i);
            // 앱 프레임과 그 경계 ±1은 접지 않는다. 헤더·Caused by·... 도 마찬가지다.
            if (!foldableFrame(entries, app, i)) {
                out.add(entry);
                i++;
                continue;
            }
            int run = i;
            while (run < entries.size() && foldableFrame(entries, app, run)) {
                run++;
            }
            if (run - i < props.minRun()) {
                for (int k = i; k < run; k++) {
                    out.add(entries.get(k));
                }
            } else {
                out.add(new Entry(entries.get(i).ts(), frameMarker(entries.subList(i, run))));
            }
            i = run;
        }
        return new Block(out);
    }

    private static boolean foldableFrame(List<Entry> entries, boolean[] app, int i) {
        return isFrame(entries.get(i).line()) && !app[i]
                && !(i > 0 && app[i - 1])
                && !(i + 1 < entries.size() && app[i + 1]);
    }

    private static boolean isAppFrame(String line, List<String> appPackages) {
        String fqcn = fqcnOf(line);
        return fqcn != null && appPackages.stream().anyMatch(fqcn::startsWith);
    }

    /** {@code \tat org.x.Y.z(Y.java:1) ~[jar]} → {@code org.x.Y.z} */
    private static String fqcnOf(String line) {
        String s = line.stripLeading();
        if (!s.startsWith("at ")) {
            return null;
        }
        int open = s.indexOf('(');
        return (open < 0 ? s.substring(3) : s.substring(3, open)).trim();
    }

    /** {@code \t… 14 frames (org.springframework, reactor.core)} */
    private static String frameMarker(List<Entry> run) {
        LinkedHashSet<String> packages = new LinkedHashSet<>();
        for (Entry entry : run) {
            String fqcn = fqcnOf(entry.line());
            if (fqcn == null) {
                continue;
            }
            String[] parts = fqcn.split("\\.");
            packages.add(parts.length >= 2 ? parts[0] + '.' + parts[1] : parts[0]);
        }
        List<String> named = packages.stream().limit(MAX_PACKAGES_IN_MARKER).toList();
        String suffix = packages.size() > named.size() ? ", …" : "";
        return "\t… %d frames (%s%s)".formatted(run.size(), String.join(", ", named), suffix);
    }

    /** {@code [x47회 · 01:43:53.163 ~ 01:46:18.503 UTC · 평균 2.0초 간격 · …]} */
    private static String marker(List<Block> repeats, boolean identical) {
        long first = repeats.get(0).startNanos();
        long last = repeats.get(repeats.size() - 1).startNanos();
        String range = (first < 0 || last < 0) ? ""
                : " · %s ~ %s UTC".formatted(HMS.format(instantOf(first)), HMS.format(instantOf(last)));
        String interval = (first < 0 || last < 0 || repeats.size() < 2 || last <= first) ? ""
                : " · 평균 %.1f초 간격".formatted((last - first) / 1_000_000_000.0 / (repeats.size() - 1));
        String kept = identical
                ? " · 글자까지 같아 한 벌만 싣는다"
                : " · 숫자만 다른 반복이라 첫 벌과 끝 벌만 싣는다";
        return "[x%d회%s%s%s]".formatted(repeats.size(), range, interval, kept);
    }

    private static Instant instantOf(long nanos) {
        return Instant.ofEpochSecond(Math.floorDiv(nanos, 1_000_000_000L), Math.floorMod(nanos, 1_000_000_000L));
    }

    private static long nanosOf(String ts) {
        try {
            return Long.parseLong(ts);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static int bytes(String s) {
        return s.getBytes(StandardCharsets.UTF_8).length;
    }

    private static ArrayNode entryNode(String ts, String line) {
        ArrayNode node = MAPPER.createArrayNode();
        node.add(ts);
        node.add(line);
        return node;
    }
}
