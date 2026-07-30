package com.yogurtte.rca.triage;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;

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
}
