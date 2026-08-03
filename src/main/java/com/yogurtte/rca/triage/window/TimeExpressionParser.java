package com.yogurtte.rca.triage.window;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

import lombok.RequiredArgsConstructor;

import com.yogurtte.rca.collector.TimeWindow;
import com.yogurtte.rca.error.ErrorCode;
import com.yogurtte.rca.error.RestApiException;
import com.yogurtte.rca.time.Confidence;
import com.yogurtte.rca.time.DayPart;
import com.yogurtte.rca.time.TimeCandidates;
import com.yogurtte.rca.triage.SurveyProperties;

/**
 * 자연어 시간 표현 → 조회 시간창 (B-26 · 결함 22에서 재작성).
 *
 * <p><b>일부러 결정적으로 만들었다.</b> 이 단계를 LLM에 맡기면 같은 질문이 회차마다 다른 창을
 * 만들어 재현이 깨지고, 탐색 채점의 "시간창을 맞게 잡았는가"를 분석 점수와 분리해서 잴 수 없다.
 * 여기가 낼 것은 정확한 시각이 아니라 <b>장애를 반드시 포함하는 가능한 한 좁은 상한</b>이다 —
 * 정밀한 좁히기는 탐색 단계(신호 기반 창 계산)가 맡는다.
 *
 * <p>역할이 갈려 있다 — <b>인식은 {@link TimeCandidates}, 결합 정책은 여기다.</b>
 * 문장에서 무엇이 보이는지(어휘·패턴·추출)는 {@code com.yogurtte.rca.time}이 책임지고,
 * 이 클래스는 그 후보로 창을 만드는 정책만 가진다: 우선순위(구간 > 시각 > 시간대 > 날짜 > 상대),
 * 미래는 {@code now}로 자르기, 날짜 없는 시각의 <b>가장 가까운 과거</b> 해석, ±여유, 상한.
 *
 * <p>표현을 못 찾으면 지어내지 않고 기본 조회 폭으로 떨어지며, 그 사실과 확신도를
 * {@link Resolved}에 남긴다 — {@link Confidence#FALLBACK} 회차는 채점에서 분리 집계된다.
 */
@RequiredArgsConstructor
public class TimeExpressionParser {

    /**
     * @param window     확정된 조회 시간창
     * @param expression 어떻게 정해졌는지 (채점·재현용 기록 — 탐색 LLM 컨텍스트에도 실린다)
     * @param confidence 창의 확신도
     */
    public record Resolved(TimeWindow window, String expression, Confidence confidence) {
    }

    /**
     * 시각만 있는 표현의 창 여유. 상한 원칙에 따라 "14시 20분쯤" → 14:20 ± 30분이면 충분하고,
     * 그 안의 정밀한 구간은 탐색이 신호에서 계산한다. 창 확대 실험(×2.26 → 점수 불변 · 결함 17)이
     * "확신 없으면 넓게"를 기각했으므로 상수로 좁게 둔다.
     */
    private static final Duration POINT_PADDING = Duration.ofMinutes(30);

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter MINUTE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final SurveyProperties properties;
    private final ZoneId zone;

    /**
     * 요청에 명시적 from/to가 있으면 그것이 우선한다. 없으면 질문 문장에서 찾는다.
     *
     * @throws RestApiException from/to가 절반만 오거나({@code INVALID_TIME_WINDOW_INCOMPLETE})
     *                          미래이거나({@code ..._FUTURE}) 순서가 뒤집힌({@code ..._REVERSED}) 경우.
     *                          거부 문구는 {@link ErrorCode}가 가지고 있다.
     */
    public Resolved resolve(String question, Instant from, Instant to, Instant now) {
        if (from != null || to != null) {
            return explicitWindow(from, to, now);
        }

        LocalDate today = ZonedDateTime.ofInstant(now, zone).toLocalDate();
        TimeCandidates found = TimeCandidates.parse(question, today);

        // 구체적인 것이 이긴다. 이 순서가 곧 우선순위다.
        if (found.range() != null) {
            return combineRange(found, today, now);
        }
        if (found.time() != null) {
            return combineTime(found, today, now);
        }
        if (found.night() != null) {
            return combineNight(found, today, now);
        }
        if (found.daypart() != null) {
            return combineDaypart(found, today, now);
        }
        if (found.date() != null) {
            return combineDate(found, today, now);
        }
        if (found.relative() != null) {
            return clamp(new TimeWindow(now.minus(found.relative().duration()), now),
                    "상대 표현 '" + found.relative().text() + "'", Confidence.EXACT);
        }
        return fallback(now, null);
    }

    private Resolved explicitWindow(Instant from, Instant to, Instant now) {
        if (from == null || to == null) {
            throw RestApiException.of(ErrorCode.INVALID_TIME_WINDOW_INCOMPLETE, from, to);
        }
        if (!from.isBefore(now)) {
            throw RestApiException.of(ErrorCode.INVALID_TIME_WINDOW_FUTURE, from, now);
        }
        boolean cut = to.isAfter(now);
        Instant end = cut ? now : to;
        if (!from.isBefore(end)) {
            throw RestApiException.of(ErrorCode.INVALID_TIME_WINDOW_REVERSED, from, to);
        }
        return clamp(new TimeWindow(from, end),
                "명시적 from/to" + (cut ? " (미래 end → now로 잘림)" : ""), Confidence.EXACT);
    }

    private Resolved combineRange(TimeCandidates found, LocalDate today, Instant now) {
        TimeCandidates.RangeMatch range = found.range();
        LocalDate base = baseDate(found, today);
        ZonedDateTime start = base.atTime(range.start()).atZone(zone);
        ZonedDateTime end = base.atTime(range.end()).atZone(zone);
        if (!end.isAfter(start)) {
            end = end.plusDays(1); // 23시~02시처럼 자정을 넘는 구간.
        }
        if (!found.hasExplicitDate() && start.toInstant().isAfter(now)) {
            start = start.minusDays(1); // 날짜가 없으면 가장 가까운 과거.
            end = end.minusDays(1);
        }
        if (!start.toInstant().isBefore(now)) {
            return fallback(now, "시각 구간 '" + range.text() + "'이 미래");
        }
        return clamp(new TimeWindow(start.toInstant(), cutFuture(end.toInstant(), start.toInstant(), now)),
                "시각 구간 '" + range.text() + "' → " + MINUTE.format(start) + "~"
                        + MINUTE.format(end) + " " + zone, Confidence.EXACT);
    }

    private Resolved combineTime(TimeCandidates found, LocalDate today, Instant now) {
        TimeCandidates.TimeMatch time = found.time();
        Instant point = baseDate(found, today).atTime(time.time()).atZone(zone).toInstant();
        if (!found.hasExplicitDate() && point.isAfter(now)) {
            point = point.minus(Duration.ofDays(1)); // 가장 가까운 과거의 그 시각.
        }
        Instant start = point.minus(POINT_PADDING);
        if (!start.isBefore(now)) {
            return fallback(now, "절대 시각 '" + time.text() + "'이 미래");
        }
        return clamp(new TimeWindow(start, cutFuture(point.plus(POINT_PADDING), start, now)),
                "절대 시각 '" + datePrefix(found) + time.text() + "' → "
                        + MINUTE.format(ZonedDateTime.ofInstant(point, zone)) + " " + zone
                        + " ±" + POINT_PADDING.toMinutes() + "분 (추정 창)", Confidence.APPROX);
    }

    private Resolved combineNight(TimeCandidates found, LocalDate today, Instant now) {
        Instant start = today.minusDays(1).atTime(18, 0).atZone(zone).toInstant();
        Instant end = today.atTime(6, 0).atZone(zone).toInstant();
        return clamp(new TimeWindow(start, cutFuture(end, start, now)),
                found.night().text() + " (어제 18:00~오늘 06:00 " + zone + ")", Confidence.EXACT);
    }

    private Resolved combineDaypart(TimeCandidates found, LocalDate today, Instant now) {
        DayPart part = found.daypart().part();
        LocalDate base = found.date() != null ? found.date().date() : today;
        Instant start = part.startOn(base, zone);
        Instant end = part.endOn(base, zone);
        if (found.date() == null && start.isAfter(now)) {
            base = base.minusDays(1); // "저녁에"를 낮에 물으면 어제 저녁이다.
            start = part.startOn(base, zone);
            end = part.endOn(base, zone);
        }
        if (!start.isBefore(now)) {
            return fallback(now, "'" + label(found) + "'이 미래");
        }
        return clamp(new TimeWindow(start, cutFuture(end, start, now)),
                "'" + label(found) + "' → " + DAY.format(base) + " 시간대 " + part.describe() + " " + zone,
                Confidence.EXACT);
    }

    private Resolved combineDate(TimeCandidates found, LocalDate today, Instant now) {
        TimeCandidates.DateMatch date = found.date();
        if (date.daysAgo() == 0) {
            return clamp(new TimeWindow(today.atStartOfDay(zone).toInstant(), now),
                    "오늘 00:00~현재 " + zone, Confidence.EXACT);
        }
        ZonedDateTime start = date.date().atStartOfDay(zone);
        return clamp(new TimeWindow(start.toInstant(), start.plusDays(1).toInstant()),
                date.text() + " (하루 전체 " + zone + ")", Confidence.EXACT);
    }

    // ---- 공통 ----

    /** 시각 앞에 붙는 날짜 수식어. "어젯밤 11시"의 "어젯밤"이 "어제"보다 구체적이라 먼저다. */
    private static String datePrefix(TimeCandidates found) {
        if (found.night() != null) {
            return found.night().text() + " ";
        }
        if (found.date() != null) {
            return found.date().text() + " ";
        }
        return "";
    }

    private LocalDate baseDate(TimeCandidates found, LocalDate today) {
        if (found.date() != null) {
            return found.date().date();
        }
        return found.night() != null ? today.minusDays(1) : today;
    }

    private static String label(TimeCandidates found) {
        return (found.date() != null ? found.date().text() + " " : "") + found.daypart().text();
    }

    /** 미래로 뻗은 끝만 자른다 — 시작까지 과거인 창은 그대로 둔다. */
    private static Instant cutFuture(Instant end, Instant start, Instant now) {
        return end.isAfter(now) && start.isBefore(now) ? now : end;
    }

    private Resolved fallback(Instant now, String note) {
        Duration lookback = Duration.ofHours(properties.defaultLookbackHours());
        String reason = note == null
                ? "시간 표현 없음 → 기본 최근 " + properties.defaultLookbackHours() + "시간"
                : note + " → 기본 최근 " + properties.defaultLookbackHours() + "시간";
        return clamp(new TimeWindow(now.minus(lookback), now), reason, Confidence.FALLBACK);
    }

    /**
     * 상한을 넘는 창은 <b>끝을 기준으로</b> 자른다. 장애는 대개 창의 끝(최근)에 가깝고,
     * 앞을 남기면 정작 봐야 할 구간이 잘려나간다.
     */
    private Resolved clamp(TimeWindow window, String expression, Confidence confidence) {
        Duration max = Duration.ofHours(properties.maxWindowHours());
        Duration span = Duration.between(window.start(), window.end());
        if (span.compareTo(max) <= 0) {
            return new Resolved(window, expression, confidence);
        }
        return new Resolved(new TimeWindow(window.end().minus(max), window.end()),
                expression + " (상한 " + properties.maxWindowHours() + "시간으로 잘림)", confidence);
    }
}
