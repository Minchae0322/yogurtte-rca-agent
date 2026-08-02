package com.yogurtte.rca.triage;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import com.yogurtte.rca.collector.TimeWindow;

/**
 * 시각·리소스·지문이 같은 신호들을 묶은 <b>장애 후보 하나</b>.
 *
 * <p>이 타입이 존재하는 이유는 하나다 — <b>창을 LLM이 숫자로 만들지 않게 하는 것.</b>
 * 기존에는 모델이 원본 JSON을 보고 {@code windowStart}/{@code windowEnd}를 직접 써냈고,
 * 그 숫자는 어떤 관측에서도 유도되지 않은 값이었다. CH-3에서 그 추측이 주입 <b>1초 전</b>에서
 * 끊겨 세 회차 연속 4/100이 됐다. 이제 모델은 <b>어느 후보인지만 고르고</b> 창은
 * {@link #window}가 신호 시각에서 계산한다.
 *
 * @param signals 이 후보를 만든 신호들
 * @param traceIds 딸린 트레이스. 없을 수 있다 — 컨슈머 전멸·파드 부재면 트레이스가 생성되지 않는다
 * @param related 시간이 겹치는 <b>다른</b> 후보들. <b>병합이 아니라 표시</b>다 —
 *                Mimir 신호와 Tempo 신호는 공유 식별자가 없어 코드가 인과를 알 수 없고,
 *                토폴로지를 병합 근거로 쓰면 정답 구조를 코드에 심는 것이 된다. 묶는 판단은 LLM에 남긴다
 */
public record Incident(
        String id,
        String resource,
        String signature,
        Signal.Channel channel,
        List<Signal> signals,
        List<String> traceIds,
        List<String> related) {

    public Incident {
        signals = signals == null ? List.of() : List.copyOf(signals);
        traceIds = traceIds == null ? List.of() : List.copyOf(traceIds);
        related = related == null ? List.of() : List.copyOf(related);
    }

    static Incident of(String id, List<Signal> signals) {
        var first = signals.get(0);
        var traceIds = new LinkedHashSet<String>();
        signals.stream()
                .filter(s -> s.channel() == Signal.Channel.TEMPO)
                .map(Signal::ref)
                .filter(ref -> ref != null && !ref.isBlank())
                .forEach(traceIds::add);
        return new Incident(id, first.resource(), first.signature(), first.channel(),
                List.copyOf(signals), List.copyOf(traceIds), List.of());
    }

    Incident withRelated(List<String> ids) {
        return new Incident(id, resource, signature, channel, signals, traceIds, ids);
    }

    public Instant firstAt() {
        return signals.stream().map(Signal::from).min(Comparator.naturalOrder()).orElse(null);
    }

    public Instant lastAt() {
        return signals.stream().map(Signal::to).max(Comparator.naturalOrder()).orElse(null);
    }

    /** 가장 <b>거친</b> 신호를 기준으로 한다. 하나라도 흐릿하면 창의 여유도 그만큼 필요하다. */
    public Signal.Precision precision() {
        return signals.stream().anyMatch(s -> s.precision() == Signal.Precision.BUCKET)
                ? Signal.Precision.BUCKET
                : Signal.Precision.EXACT;
    }

    /**
     * 조사 창을 <b>신호 시각과 정밀도에서 계산한다.</b>
     *
     * <p>여유를 임의로 정하지 않는다. 트레이스 span은 ms 단위로 정확하니 작게, 메트릭 샘플과
     * 로그 발생률 버킷은 애초에 집계 해상도만큼 흐리니 그만큼 준다. LLM이 만든 창 끝에 여유를
     * 덧대는 방식(추측을 추측으로 보정)은 이 계산이 대신한다.
     */
    public TimeWindow window(Duration exactPad, Duration bucketPad, TimeWindow sweep) {
        var pad = precision() == Signal.Precision.EXACT ? exactPad : bucketPad;
        var start = firstAt().minus(pad);
        var end = lastAt().plus(pad);
        if (sweep == null) {
            return new TimeWindow(start, end);
        }
        var clampedStart = start.isBefore(sweep.start()) ? sweep.start() : start;
        var clampedEnd = end.isAfter(sweep.end()) ? sweep.end() : end;
        return clampedStart.isBefore(clampedEnd) ? new TimeWindow(clampedStart, clampedEnd) : sweep;
    }

    /** 여러 후보를 함께 고른 경우의 창 — 합집합이다. 한 장애가 상·하류에 걸칠 수 있다. */
    public static TimeWindow unionWindow(List<Incident> chosen, Duration exactPad,
                                         Duration bucketPad, TimeWindow sweep) {
        Instant start = null;
        Instant end = null;
        for (var incident : chosen) {
            var w = incident.window(exactPad, bucketPad, sweep);
            start = (start == null || w.start().isBefore(start)) ? w.start() : start;
            end = (end == null || w.end().isAfter(end)) ? w.end() : end;
        }
        return (start == null || !start.isBefore(end)) ? sweep : new TimeWindow(start, end);
    }

    /** 컨텍스트에 넣을 한 덩어리. 원본 JSON은 따로 그대로 실린다. */
    public String describe() {
        var sb = new StringBuilder();
        sb.append("## ").append(id).append("  ").append(resource);
        if (!"?".equals(signature)) {
            sb.append("  |  ").append(signature);
        }
        sb.append('\n');
        sb.append("- 구간: ").append(firstAt()).append(" ~ ").append(lastAt())
                .append("  (").append(channel).append(" · ")
                .append(precision() == Signal.Precision.EXACT ? "시각 정확" : "집계 해상도만큼 흐림")
                .append(")\n");
        signals.forEach(s -> sb.append("- ").append(s.what()).append('\n'));
        if (!traceIds.isEmpty()) {
            sb.append("- traceId: ").append(String.join(", ", traceIds)).append('\n');
        }
        if (!related.isEmpty()) {
            sb.append("- 같은 시각의 다른 후보: ").append(String.join(", ", related))
                    .append("  (인과 여부는 판단하지 않았다)\n");
        }
        return sb.toString();
    }

    static List<String> idsOf(List<Incident> incidents) {
        var ids = new ArrayList<String>();
        incidents.forEach(i -> ids.add(i.id()));
        return ids;
    }

    // ---- 태생: 신호 → 장애 후보 여러 개. 하나로 접지 않는다 ----
    //
    // 실증: 서로 다른 장애를 묻는 두 질문(CH-1·CH-2)이 같은 traceId를 골랐고, CH-2 리포트의
    // 2순위가 정답이었는데 수집은 1순위 것만 가져왔다 — 창 하나·traceId 하나만 표현하는 구조에선
    // 가장 센 것만 남는다(결함 14·15).

    /**
     * 축의 순서가 중요하다.
     * <pre>
     *   ① 라벨 3축 (채널 · 리소스 · 지문)   사건이 시간상 교차해도 갈린다
     *   ② 같은 키 안에서 시간 간격          여기서만 시간을 쓴다
     *   ③ traceId 공유면 병합               결정적 근거가 있을 때만
     *   ④ 시간 겹침은 related 표시          병합하지 않는다
     * </pre>
     *
     * <p><b>시간만으로 묶으면 실패한다.</b> CH-3 회차 2에서 {@code lag} 신호 하나가 05:00부터
     * 05:20까지 걸쳐 있어, 시간 간격만으로는 04:55~05:25가 통째로 한 덩어리가 되고 정답이
     * CH-1·CH-2에 흡수된다. {@code lag}는 {@code resource=kafka}이므로 리소스 축이 그 연결을 끊는다.
     *
     * <p><b>애매하면 나눈다.</b> 합쳐서 잘못되면 되돌릴 수 없고(창이 넓어지고 구별이 사라진다),
     * 나눠서 잘못되면 모델이 고르지 않은 것이 후보로 남아 되돌아갈 수 있다.
     * <b>크기로 걸러내지도 않는다</b> — CH-3의 결정적 로그 신호는 1건이고 옆 버킷이 54건이었다.
     */
    public static List<Incident> cluster(List<Signal> signals, Duration gap) {
        if (signals.isEmpty()) {
            return List.of();
        }

        // ① 라벨로 먼저 가른다 — 시간이 들어가지 않으므로 인터리빙에 면역이다.
        var byKey = new LinkedHashMap<String, List<Signal>>();
        signals.forEach(s -> byKey.computeIfAbsent(s.key(), k -> new ArrayList<>()).add(s));

        // ② 같은 키 안에서만 시간으로 끊는다.
        var groups = new ArrayList<List<Signal>>();
        byKey.values().forEach(group -> groups.addAll(splitByGap(group, gap)));

        // ③ traceId를 공유하면 병합한다.
        var merged = mergeByTraceLink(groups);

        // 시각 순으로 번호를 매긴다.
        merged.sort(Comparator.comparing(group -> group.stream()
                .map(Signal::from).min(Comparator.naturalOrder()).orElse(Instant.EPOCH)));
        var incidents = new ArrayList<Incident>();
        for (var i = 0; i < merged.size(); i++) {
            incidents.add(Incident.of("INC-" + (i + 1), merged.get(i)));
        }

        // ④ 시간이 겹치는 다른 후보를 표시한다. 병합이 아니다.
        return linkRelated(incidents);
    }

    /**
     * 구간이 겹치거나 {@code gap} 이내로 이어지면 같은 후보다.
     *
     * <p>{@code reach}가 핵심이다 — <b>끝이 가장 먼 신호</b>를 기준으로 이어붙인다. 신호가
     * 점이 아니라 구간이므로, 시작 시각만 비교하면 긴 구간 신호를 잘라먹는다.
     */
    private static List<List<Signal>> splitByGap(List<Signal> signals, Duration gap) {
        var sorted = new ArrayList<>(signals);
        sorted.sort(Comparator.comparing(Signal::from));

        var out = new ArrayList<List<Signal>>();
        var current = new ArrayList<Signal>();
        Instant reach = null;

        for (var s : sorted) {
            if (reach != null && s.from().isAfter(reach.plus(gap))) {
                out.add(List.copyOf(current));
                current = new ArrayList<>();
                reach = null;
            }
            current.add(s);
            reach = (reach == null || s.to().isAfter(reach)) ? s.to() : reach;
        }
        if (!current.isEmpty()) {
            out.add(List.copyOf(current));
        }
        return out;
    }

    /**
     * traceId를 공유하는 군집만 병합한다.
     *
     * <p><b>시간 근접만으로는 병합하지 않는다.</b> Mimir 신호와 Tempo 신호는 공유 식별자가 없어
     * 코드가 인과를 알 수 없고, 토폴로지를 병합 근거로 쓰면 <i>"auth가 죽으면 content가
     * 영향받는다"</i> 를 코드에 심는 것이 된다 — 그건 채점 대상인 정답 구조다.
     */
    private static List<List<Signal>> mergeByTraceLink(List<List<Signal>> groups) {
        var out = new ArrayList<List<Signal>>();
        var outIds = new ArrayList<Set<String>>();
        for (var group : groups) {
            var ids = traceIdsOf(group);
            var mergedInto = -1;
            if (!ids.isEmpty()) {
                for (var i = 0; i < out.size(); i++) {
                    if (!Collections.disjoint(outIds.get(i), ids)) {
                        mergedInto = i;
                        break;
                    }
                }
            }
            if (mergedInto >= 0) {
                var combined = new ArrayList<>(out.get(mergedInto));
                combined.addAll(group);
                out.set(mergedInto, List.copyOf(combined));
                var combinedIds = new HashSet<>(outIds.get(mergedInto));
                combinedIds.addAll(ids);
                outIds.set(mergedInto, combinedIds);
            } else {
                out.add(group);
                outIds.add(ids);
            }
        }
        return out;
    }

    private static Set<String> traceIdsOf(List<Signal> signals) {
        return signals.stream()
                .filter(s -> s.channel() == Signal.Channel.TEMPO)
                .map(Signal::ref)
                .filter(ref -> ref != null && !ref.isBlank())
                .collect(Collectors.toSet());
    }

    /** 시간이 겹치는 다른 후보를 서로 가리키게 한다. 상·하류에 걸친 장애를 모델이 묶을 수 있게. */
    private static List<Incident> linkRelated(List<Incident> incidents) {
        var out = new ArrayList<Incident>();
        for (var incident : incidents) {
            var related = new ArrayList<String>();
            for (var other : incidents) {
                if (!other.id().equals(incident.id()) && overlaps(incident, other)) {
                    related.add(other.id());
                }
            }
            out.add(incident.withRelated(related));
        }
        return List.copyOf(out);
    }

    private static boolean overlaps(Incident a, Incident b) {
        return !a.firstAt().isAfter(b.lastAt()) && !b.firstAt().isAfter(a.lastAt());
    }
}
