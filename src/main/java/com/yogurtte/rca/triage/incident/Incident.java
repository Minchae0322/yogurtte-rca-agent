package com.yogurtte.rca.triage.incident;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

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
        Channel channel,
        List<Signal> signals,
        List<String> traceIds,
        List<String> related) {

    public Incident {
        signals = signals == null ? List.of() : List.copyOf(signals);
        traceIds = traceIds == null ? List.of() : List.copyOf(traceIds);
        related = related == null ? List.of() : List.copyOf(related);
    }

    static Incident of(String id, List<Signal> signals) {
        Signal first = signals.get(0);
        return new Incident(id, first.resource(), first.signature(), first.channel(),
                List.copyOf(signals), tempoRefs(signals).distinct().toList(), List.of());
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
    public Precision precision() {
        return signals.stream().anyMatch(s -> s.precision() == Precision.BUCKET)
                ? Precision.BUCKET
                : Precision.EXACT;
    }

    /**
     * 조사 창을 <b>신호 시각과 정밀도에서 계산한다.</b>
     *
     * <p>여유를 임의로 정하지 않는다. 트레이스 span은 ms 단위로 정확하니 작게, 메트릭 샘플과
     * 로그 발생률 버킷은 애초에 집계 해상도만큼 흐리니 그만큼 준다. LLM이 만든 창 끝에 여유를
     * 덧대는 방식(추측을 추측으로 보정)은 이 계산이 대신한다.
     */
    public TimeWindow window(Duration exactPad, Duration bucketPad, TimeWindow sweep) {
        Duration pad = precision() == Precision.EXACT ? exactPad : bucketPad;
        Instant start = firstAt().minus(pad);
        Instant end = lastAt().plus(pad);
        if (sweep == null) {
            return new TimeWindow(start, end);
        }
        Instant clampedStart = start.isBefore(sweep.start()) ? sweep.start() : start;
        Instant clampedEnd = end.isAfter(sweep.end()) ? sweep.end() : end;
        return clampedStart.isBefore(clampedEnd) ? new TimeWindow(clampedStart, clampedEnd) : sweep;
    }

    /** 여러 후보를 함께 고른 경우의 창 — 합집합이다. 한 장애가 상·하류에 걸칠 수 있다. */
    public static TimeWindow unionWindow(List<Incident> chosen, Duration exactPad,
                                         Duration bucketPad, TimeWindow sweep) {
        List<TimeWindow> windows = chosen.stream().map(i -> i.window(exactPad, bucketPad, sweep)).toList();
        if (windows.isEmpty()) {
            return sweep;
        }
        Instant start = windows.stream().map(TimeWindow::start).min(Comparator.naturalOrder()).orElseThrow();
        Instant end = windows.stream().map(TimeWindow::end).max(Comparator.naturalOrder()).orElseThrow();
        return start.isBefore(end) ? new TimeWindow(start, end) : sweep;
    }

    /** 컨텍스트에 넣을 한 덩어리. 원본 JSON은 따로 그대로 실린다. */
    public String describe() {
        StringBuilder sb = new StringBuilder();
        sb.append("## ").append(id).append("  ").append(resource);
        if (!"?".equals(signature)) {
            sb.append("  |  ").append(signature);
        }
        sb.append('\n');
        sb.append("- 구간: ").append(firstAt()).append(" ~ ").append(lastAt())
                .append("  (").append(channel).append(" · ")
                .append(precision() == Precision.EXACT ? "시각 정확" : "집계 해상도만큼 흐림")
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

    public static List<String> idsOf(List<Incident> incidents) {
        return incidents.stream().map(Incident::id).toList();
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
        LinkedHashMap<String, List<Signal>> byKey = signals.stream().collect(Collectors.groupingBy(
                Signal::key, LinkedHashMap::new, Collectors.toList()));

        // ② 같은 키 안에서만 시간으로 끊는다.
        List<List<Signal>> groups = byKey.values().stream()
                .flatMap(group -> splitByGap(group, gap).stream())
                .toList();

        // ③ traceId를 공유하면 병합한다.
        List<List<Signal>> merged = mergeByTraceLink(groups);

        // 시각 순으로 번호를 매긴다.
        List<List<Signal>> ordered = merged.stream()
                .sorted(Comparator.comparing(Incident::startOf))
                .toList();
        List<Incident> incidents = IntStream.range(0, ordered.size())
                .mapToObj(i -> Incident.of("INC-" + (i + 1), ordered.get(i)))
                .toList();

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
        List<Signal> sorted = signals.stream().sorted(Comparator.comparing(Signal::from)).toList();
        ArrayList<List<Signal>> groups = new ArrayList<>();
        List<Signal> current = null;
        Instant reach = null;

        // 새 묶음을 만드는 즉시 groups에 넣는다 — 루프 끝에서 남은 것을 따로 흘려보낼 필요가 없다.
        for (Signal signal : sorted) {
            if (current == null || signal.from().isAfter(reach.plus(gap))) {
                current = new ArrayList<>();
                groups.add(current);
                reach = signal.to();
            }
            current.add(signal);
            reach = signal.to().isAfter(reach) ? signal.to() : reach;
        }
        return groups.stream().map(List::copyOf).toList();
    }

    /**
     * traceId를 공유하는 군집만 병합한다.
     *
     * <p><b>시간 근접만으로는 병합하지 않는다.</b> Mimir 신호와 Tempo 신호는 공유 식별자가 없어
     * 코드가 인과를 알 수 없고, 토폴로지를 병합 근거로 쓰면 <i>"auth가 죽으면 content가
     * 영향받는다"</i> 를 코드에 심는 것이 된다 — 그건 채점 대상인 정답 구조다.
     */
    private static List<List<Signal>> mergeByTraceLink(List<List<Signal>> groups) {
        ArrayList<Cluster> clusters = new ArrayList<>();
        for (List<Signal> group : groups) {
            Set<String> ids = tempoRefs(group).collect(Collectors.toSet());
            clusters.stream()
                    .filter(cluster -> cluster.sharesTraceWith(ids))
                    .findFirst()
                    .ifPresentOrElse(cluster -> cluster.absorb(group, ids),
                            () -> clusters.add(new Cluster(group, ids)));
        }
        return clusters.stream().map(Cluster::signals).toList();
    }

    /**
     * 병합 중인 군집 하나. traceId 집합을 신호와 <b>같은 객체가</b> 들고 있다 —
     * 신호 리스트와 id 리스트를 인덱스로 맞추던 구조에선 한쪽만 갱신하면 조용히 어긋난다.
     */
    private static final class Cluster {

        private final List<Signal> signals = new ArrayList<>();
        private final Set<String> traceIds = new HashSet<>();

        Cluster(List<Signal> signals, Set<String> traceIds) {
            absorb(signals, traceIds);
        }

        /** traceId가 없는 군집은 아무와도 이어지지 않는다 — 시간 근접만으로는 병합하지 않는다. */
        boolean sharesTraceWith(Set<String> ids) {
            return !ids.isEmpty() && !Collections.disjoint(traceIds, ids);
        }

        void absorb(List<Signal> more, Set<String> ids) {
            signals.addAll(more);
            traceIds.addAll(ids);
        }

        List<Signal> signals() {
            return List.copyOf(signals);
        }
    }

    /** 신호 묶음이 딸고 있는 traceId들. 빈 값·비-Tempo 채널은 여기서 걸러진다. */
    private static Stream<String> tempoRefs(List<Signal> signals) {
        return signals.stream()
                .filter(s -> s.channel() == Channel.TEMPO)
                .map(Signal::ref)
                .filter(ref -> ref != null && !ref.isBlank());
    }

    /** 신호 묶음의 시작 시각 — 후보 번호를 시각 순으로 매기는 기준. */
    private static Instant startOf(List<Signal> signals) {
        return signals.stream().map(Signal::from).min(Comparator.naturalOrder()).orElse(Instant.EPOCH);
    }

    /** 시간이 겹치는 다른 후보를 서로 가리키게 한다. 상·하류에 걸친 장애를 모델이 묶을 수 있게. */
    private static List<Incident> linkRelated(List<Incident> incidents) {
        return incidents.stream()
                .map(incident -> incident.withRelated(incidents.stream()
                        .filter(other -> !other.id().equals(incident.id()))
                        .filter(other -> overlaps(incident, other))
                        .map(Incident::id)
                        .toList()))
                .toList();
    }

    private static boolean overlaps(Incident a, Incident b) {
        return !a.firstAt().isAfter(b.lastAt()) && !b.firstAt().isAfter(a.lastAt());
    }
}
