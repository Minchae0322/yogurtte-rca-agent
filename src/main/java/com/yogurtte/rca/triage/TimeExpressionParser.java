package com.yogurtte.rca.triage;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.yogurtte.rca.collector.TimeWindow;

/**
 * 자연어 시간 표현 → 조회 시간창.
 *
 * <p><b>일부러 결정적으로 만들었다.</b> 이 단계를 LLM에 맡기면 같은 질문이 회차마다 다른 창을
 * 만들어 재현이 깨지고, 탐색 채점의 "시간창을 맞게 잡았는가"를 분석 점수와 분리해서 잴 수 없다.
 * LLM은 창을 정하는 데가 아니라 <b>창 안에서 무엇이 이상한지 고르는 데</b>부터 들어온다.
 *
 * <p>표현을 못 찾으면 지어내지 않고 기본 조회 폭(최근 N시간)으로 떨어지며, 그 사실을
 * {@link Resolved#expression()}에 남긴다.
 */
@Component
public class TimeExpressionParser {

    /** "지난 3시간", "최근 30분", "last 2 hours", "2일 전" 같은 상대 표현. */
    private static final Pattern RELATIVE = Pattern.compile(
            "(?:지난|최근|past|last)?\\s*(\\d{1,3})\\s*(시간|분|일|시간전|h|hr|hours?|m|min|minutes?|d|days?)\\s*(?:전|ago)?",
            Pattern.CASE_INSENSITIVE);

    private final SurveyProperties properties;
    private final ZoneId zone;

    public TimeExpressionParser(SurveyProperties properties) {
        this.properties = properties;
        this.zone = ZoneId.of(properties.zone());
    }

    /**
     * @param window     확정된 조회 시간창
     * @param expression 어떻게 정해졌는지 (채점·재현용 기록)
     */
    public record Resolved(TimeWindow window, String expression) {
    }

    /** 요청에 명시적 from/to가 있으면 그것이 우선한다. 없으면 질문 문장에서 찾는다. */
    public Resolved resolve(String question, Instant from, Instant to, Instant now) {
        if (from != null && to != null) {
            return clamp(new TimeWindow(from, to), "명시적 from/to");
        }

        var text = question == null ? "" : question.trim();
        var today = ZonedDateTime.ofInstant(now, zone).toLocalDate();

        if (containsAny(text, "어젯밤", "어제밤", "지난밤", "간밤", "last night")) {
            // 어제 18:00 ~ 오늘 06:00 — "밤"의 통상 범위이자 야간 배치·저트래픽 구간을 덮는다.
            return clamp(new TimeWindow(
                    today.minusDays(1).atTime(18, 0).atZone(zone).toInstant(),
                    today.atTime(6, 0).atZone(zone).toInstant()), "어젯밤 (어제 18:00~오늘 06:00 " + zone + ")");
        }
        if (containsAny(text, "새벽", "dawn", "early morning")) {
            return clamp(new TimeWindow(
                    today.atStartOfDay(zone).toInstant(),
                    today.atTime(6, 0).atZone(zone).toInstant()), "오늘 새벽 (00:00~06:00 " + zone + ")");
        }
        if (containsAny(text, "그제", "그저께")) {
            return clamp(dayOf(today.minusDays(2).atStartOfDay(zone)), "그제 (하루 전체 " + zone + ")");
        }
        if (containsAny(text, "어제", "yesterday")) {
            return clamp(dayOf(today.minusDays(1).atStartOfDay(zone)), "어제 (하루 전체 " + zone + ")");
        }
        if (containsAny(text, "오늘", "today")) {
            return clamp(new TimeWindow(today.atStartOfDay(zone).toInstant(), now), "오늘 00:00~현재 " + zone);
        }

        var matcher = RELATIVE.matcher(text);
        if (matcher.find()) {
            var amount = Long.parseLong(matcher.group(1));
            var duration = durationOf(matcher.group(2), amount);
            if (duration != null) {
                return clamp(new TimeWindow(now.minus(duration), now), "상대 표현 '" + matcher.group().trim() + "'");
            }
        }

        var fallback = Duration.ofHours(properties.defaultLookbackHours());
        return clamp(new TimeWindow(now.minus(fallback), now),
                "시간 표현 없음 → 기본 최근 " + properties.defaultLookbackHours() + "시간");
    }

    private TimeWindow dayOf(ZonedDateTime startOfDay) {
        return new TimeWindow(startOfDay.toInstant(), startOfDay.plusDays(1).toInstant());
    }

    private static Duration durationOf(String unit, long amount) {
        var u = unit.toLowerCase();
        if (u.startsWith("시간") || u.equals("h") || u.equals("hr") || u.startsWith("hour")) {
            return Duration.ofHours(amount);
        }
        if (u.startsWith("분") || u.equals("m") || u.equals("min") || u.startsWith("minute")) {
            return Duration.ofMinutes(amount);
        }
        if (u.startsWith("일") || u.equals("d") || u.startsWith("day")) {
            return Duration.ofDays(amount);
        }
        return null;
    }

    /**
     * 상한을 넘는 창은 <b>끝을 기준으로</b> 자른다. 장애는 대개 창의 끝(최근)에 가깝고,
     * 앞을 남기면 정작 봐야 할 구간이 잘려나간다.
     */
    private Resolved clamp(TimeWindow window, String expression) {
        var max = Duration.ofHours(properties.maxWindowHours());
        var span = Duration.between(window.start(), window.end());
        if (span.compareTo(max) <= 0) {
            return new Resolved(window, expression);
        }
        return new Resolved(new TimeWindow(window.end().minus(max), window.end()),
                expression + " (상한 " + properties.maxWindowHours() + "시간으로 잘림)");
    }

    private static boolean containsAny(String text, String... needles) {
        var lower = text.toLowerCase();
        for (var needle : needles) {
            if (lower.contains(needle.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    /** 미리보기·테스트용. */
    public LocalTime nightStart() {
        return LocalTime.of(18, 0);
    }
}
