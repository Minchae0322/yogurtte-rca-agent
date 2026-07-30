package com.yogurtte.rca.triage;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;

import org.springframework.stereotype.Component;

/**
 * 신호를 <b>장애 후보 여러 개</b>로 묶는다. 하나로 접지 않는다.
 *
 * <p>지금 구조는 창 하나 · traceId 하나만 표현할 수 있어서, 창 안에 장애가 둘이면
 * <b>가장 센 것만 남고 나머지는 분석 단계에 아예 오지 않는다.</b> 실증이 있다 —
 * 서로 다른 장애를 묻는 두 질문(CH-1·CH-2)이 <b>같은 traceId</b>를 골랐고, CH-2 리포트의
 * <b>2순위가 정답</b>이었는데 수집은 1순위 것만 가져왔다.
 *
 * <h2>축의 순서가 중요하다</h2>
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
@Component
public class IncidentClusterer {

    public List<Incident> cluster(List<Signal> signals, Duration gap) {
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
    private List<List<Signal>> splitByGap(List<Signal> signals, Duration gap) {
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
    private List<List<Signal>> mergeByTraceLink(List<List<Signal>> groups) {
        var out = new ArrayList<List<Signal>>();
        for (var group : groups) {
            var ids = traceIdsOf(group);
            var mergedInto = -1;
            if (!ids.isEmpty()) {
                for (var i = 0; i < out.size(); i++) {
                    if (traceIdsOf(out.get(i)).stream().anyMatch(ids::contains)) {
                        mergedInto = i;
                        break;
                    }
                }
            }
            if (mergedInto >= 0) {
                var combined = new ArrayList<>(out.get(mergedInto));
                combined.addAll(group);
                out.set(mergedInto, List.copyOf(combined));
            } else {
                out.add(group);
            }
        }
        return out;
    }

    private List<String> traceIdsOf(List<Signal> signals) {
        return signals.stream()
                .filter(s -> s.channel() == Signal.Channel.TEMPO)
                .map(Signal::ref)
                .filter(ref -> ref != null && !ref.isBlank())
                .toList();
    }

    /** 시간이 겹치는 다른 후보를 서로 가리키게 한다. 상·하류에 걸친 장애를 모델이 묶을 수 있게. */
    private List<Incident> linkRelated(List<Incident> incidents) {
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

    private boolean overlaps(Incident a, Incident b) {
        return !a.firstAt().isAfter(b.lastAt()) && !b.firstAt().isAfter(a.lastAt());
    }
}
