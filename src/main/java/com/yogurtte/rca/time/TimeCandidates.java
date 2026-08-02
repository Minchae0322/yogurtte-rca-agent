package com.yogurtte.rca.time;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 질문 문장에서 시간 후보를 전량 추출한다 — L1 정규화(수사·"N시 반")와 L2 추출.
 *
 * <p><b>"문장에 무엇이 보이는가"만 책임진다.</b> 후보를 창으로 결합하는 정책(우선순위 ·
 * 미래 컷 · 가장 가까운 과거 · ±여유 · 상한)은 {@code triage.TimeExpressionParser}의 몫이다.
 * 그래서 이 클래스는 시계도 표준시도 모른다 — 입력은 문장과 기준 날짜뿐이다.
 *
 * <p>패턴의 어휘 alternation은 이 패키지의 enum에서 {@link Aliased#alternation}으로 생성한다 —
 * 단어를 패턴에 직접 쓰지 않는다. 첫 매치를 취하지 않고 종류별 후보를 전부 뽑되,
 * 이미 소비된 구간의 글자는 다시 세지 않는다("어제밤"의 "어제", "14시"에 붙은 "오후").
 *
 * <p><b>상대 표현은 표지가 필수다.</b> {@code 지난·최근·last·past} 접두나
 * {@code 전·동안·안에·이내·ago} 접미가 없으면 상대 후보로 세지 않는다 — 그래야
 * {@code "14시 20분"}의 {@code 20분}이 "최근 20분"으로 오독되지 않는다.
 *
 * @param range null이면 그 종류의 후보가 없다는 뜻이다 (다른 필드도 같다)
 */
public record TimeCandidates(RangeMatch range, TimeMatch time, NightMatch night,
                             DateMatch date, DaypartMatch daypart, RelativeMatch relative) {

    /** 날짜가 문장에 명시됐는가 — 명시됐으면 "가장 가까운 과거" 보정을 하지 않는다. */
    public boolean hasExplicitDate() {
        return date != null || night != null;
    }

    public record RangeMatch(LocalTime start, LocalTime end, String text) {
    }

    public record TimeMatch(LocalTime time, String text) {
    }

    /** 자정을 걸치는 밤 표현("어젯밤") — 날짜(어제)와 시간대(밤)가 한 단어에 붙어 있다. */
    public record NightMatch(String text) {
    }

    public record DateMatch(LocalDate date, int daysAgo, String text) {
    }

    public record DaypartMatch(DayPart part, String text) {
    }

    public record RelativeMatch(Duration duration, String text) {
    }

    private static final List<String> NIGHT_WORDS = List.of("어젯밤", "어제밤", "지난밤", "간밤", "last night");
    private static final List<String> RELATIVE_PREFIXES = List.of("지난", "최근", "past", "last");
    private static final List<String> RELATIVE_SUFFIXES = List.of("전", "동안", "안에", "이내", "ago");

    /** 수사 → 숫자. 단위(시간·시·분·일) 직전에서만 바꾼다 — "한 일 없어"류 오폭을 막는 조건이다. */
    private static final Map<String, String> NUMERALS = Map.of("한", "1", "두", "2", "세", "3", "네", "4");

    /** 시각 하나: {@code 오후 2시} · {@code 14시 20분} · {@code 14:20}. {@code 시(?!간)}이 "1시간"의 오탐을 막는다. */
    private static final String TIME_SRC = "(" + Aliased.alternation(Meridiem.values()) + ")?"
            + "\\s*(\\d{1,2})(?::(\\d{2})|\\s*시(?!간)(?:\\s*(\\d{1,2})\\s*분)?)";
    /** {@link #TIME_SRC}가 만드는 그룹 수. RANGE에서 두 번째 시각의 그룹 오프셋이 된다. */
    private static final int TIME_GROUPS = 4;

    private static final Pattern ABS_TIME = Pattern.compile(TIME_SRC);
    private static final Pattern RANGE = Pattern.compile(TIME_SRC + "\\s*(?:~|부터)\\s*" + TIME_SRC + "(?:\\s*까지)?");
    private static final Pattern DATE_KR = Pattern.compile("(?:(\\d{4})\\s*년\\s*)?(\\d{1,2})\\s*월\\s*(\\d{1,2})\\s*일");
    private static final Pattern DATE_ISO = Pattern.compile("(\\d{4})-(\\d{2})-(\\d{2})");
    private static final Pattern NIGHT = Pattern.compile(Aliased.alternation(NIGHT_WORDS), Pattern.CASE_INSENSITIVE);
    private static final Pattern DATE_WORD = Pattern.compile(
            Aliased.alternation(NamedDay.values()), Pattern.CASE_INSENSITIVE);
    private static final Pattern DAYPART = Pattern.compile(
            Aliased.alternation(DayPart.values()), Pattern.CASE_INSENSITIVE);

    private static final String UNIT_ALT = Aliased.alternation(RelativeUnit.values());
    /** 접두형이 접미형보다 먼저다 — 둘 다 있으면 접두형 문언을 기록한다. */
    private static final Pattern REL_PREFIX = Pattern.compile(
            "(?:" + Aliased.alternation(RELATIVE_PREFIXES) + ")\\s*(\\d{1,3})\\s*(" + UNIT_ALT + ")",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern REL_SUFFIX = Pattern.compile(
            "(\\d{1,3})\\s*(" + UNIT_ALT + ")\\s*(" + Aliased.alternation(RELATIVE_SUFFIXES) + ")",
            Pattern.CASE_INSENSITIVE);

    /** @param today 상대적 날짜 말("어제")을 해석할 기준 날짜 — 호출자의 표준시로 계산해 넘긴다. */
    public static TimeCandidates parse(String question, LocalDate today) {
        var text = normalize(question == null ? "" : question.trim());
        var consumed = new ArrayList<int[]>();
        var range = findRange(text, consumed);
        var night = findNight(text, consumed);
        var time = range == null ? findTime(text, consumed) : null;
        var date = findDate(text, today, consumed);
        var daypart = findDaypart(text, consumed);
        return new TimeCandidates(range, time, night, date, daypart, findRelative(text));
    }

    // ---- L1 ----

    private static String normalize(String text) {
        for (var numeral : NUMERALS.entrySet()) {
            text = text.replaceAll(numeral.getKey() + "\\s*(?=시간|시(?!간)|분|일)", numeral.getValue());
        }
        return text.replaceAll("(\\d{1,2})\\s*시\\s*반", "$1시 30분");
    }

    // ---- L2 ----

    private static RangeMatch findRange(String text, List<int[]> consumed) {
        var m = RANGE.matcher(text);
        while (m.find()) {
            var start = timeAt(m, 0);
            var end = timeAt(m, TIME_GROUPS);
            if (start == null || end == null) {
                continue;
            }
            consumed.add(new int[] {m.start(), m.end()});
            return new RangeMatch(start, end, m.group().trim());
        }
        return null;
    }

    private static NightMatch findNight(String text, List<int[]> consumed) {
        var m = NIGHT.matcher(text);
        if (m.find()) {
            consumed.add(new int[] {m.start(), m.end()});
            return new NightMatch(m.group());
        }
        return null;
    }

    /** 복수 시각이 구간 연결자 없이 나오면 첫 후보를 쓴다 — 상한 원칙상 어느 쪽이든 ±여유가 덮는다. */
    private static TimeMatch findTime(String text, List<int[]> consumed) {
        var m = ABS_TIME.matcher(text);
        while (m.find()) {
            if (inside(consumed, m.start())) {
                continue;
            }
            var time = timeAt(m, 0);
            if (time == null) {
                continue;
            }
            consumed.add(new int[] {m.start(), m.end()});
            return new TimeMatch(time, m.group().trim());
        }
        return null;
    }

    private static DateMatch findDate(String text, LocalDate today, List<int[]> consumed) {
        var iso = DATE_ISO.matcher(text);
        if (iso.find()) {
            var date = dateOf(Integer.parseInt(iso.group(1)),
                    Integer.parseInt(iso.group(2)), Integer.parseInt(iso.group(3)));
            if (date != null) {
                consumed.add(new int[] {iso.start(), iso.end()});
                return new DateMatch(date, (int) (today.toEpochDay() - date.toEpochDay()), iso.group());
            }
        }
        var kr = DATE_KR.matcher(text);
        if (kr.find()) {
            var year = kr.group(1) != null ? Integer.parseInt(kr.group(1)) : today.getYear();
            var date = dateOf(year, Integer.parseInt(kr.group(2)), Integer.parseInt(kr.group(3)));
            if (date != null) {
                if (kr.group(1) == null && date.isAfter(today)) {
                    date = date.minusYears(1); // 연도 없는 미래 날짜는 가장 가까운 과거로.
                }
                consumed.add(new int[] {kr.start(), kr.end()});
                return new DateMatch(date, (int) (today.toEpochDay() - date.toEpochDay()), kr.group());
            }
        }
        var word = DATE_WORD.matcher(text);
        while (word.find()) {
            if (inside(consumed, word.start())) {
                continue; // "어제밤"의 "어제"를 다시 세지 않는다.
            }
            var day = NamedDay.of(word.group());
            return new DateMatch(today.minusDays(day.daysAgo()), day.daysAgo(), word.group());
        }
        return null;
    }

    private static DaypartMatch findDaypart(String text, List<int[]> consumed) {
        var m = DAYPART.matcher(text);
        while (m.find()) {
            if (inside(consumed, m.start())) {
                continue; // "14시"에 붙은 "오후", "어젯밤"의 "밤" 등 이미 소비된 표지.
            }
            return new DaypartMatch(DayPart.of(m.group()), m.group());
        }
        return null;
    }

    private static RelativeMatch findRelative(String text) {
        var prefix = REL_PREFIX.matcher(text);
        if (prefix.find()) {
            var duration = RelativeUnit.of(prefix.group(2)).duration(Long.parseLong(prefix.group(1)));
            return new RelativeMatch(duration, prefix.group().trim());
        }
        var suffix = REL_SUFFIX.matcher(text);
        if (suffix.find()) {
            var duration = RelativeUnit.of(suffix.group(2)).duration(Long.parseLong(suffix.group(1)));
            // 표기는 "N단위 전" 꼴만 남긴다 — 안에/동안/이내는 창 의미가 같아 생략한다.
            var amountAndUnit = text.substring(suffix.start(), suffix.end(2)).trim();
            var marker = "전".equals(suffix.group(3)) ? " 전" : "";
            return new RelativeMatch(duration, amountAndUnit + marker);
        }
        return null;
    }

    // ---- 헬퍼 ----

    /** {@link #TIME_SRC}의 그룹 배치(표지·시·콜론분·시분)를 아는 유일한 곳. */
    private static LocalTime timeAt(Matcher m, int base) {
        var hour = Integer.parseInt(m.group(base + 2));
        var minuteText = m.group(base + 3) != null ? m.group(base + 3) : m.group(base + 4);
        var minute = minuteText == null ? 0 : Integer.parseInt(minuteText);
        if (hour > 23 || minute > 59) {
            return null; // 달력에 없는 시각은 후보로 세지 않는다.
        }
        var meridiem = m.group(base + 1);
        if (meridiem != null) {
            hour = Meridiem.of(meridiem).adjust(hour);
        }
        return LocalTime.of(hour % 24, minute);
    }

    /** 달력에 없는 날짜(13월·2월 30일 등)는 후보로 세지 않는다. */
    private static LocalDate dateOf(int year, int month, int day) {
        try {
            return LocalDate.of(year, month, day);
        } catch (Exception e) {
            return null;
        }
    }

    private static boolean inside(List<int[]> consumed, int position) {
        for (var span : consumed) {
            if (position >= span[0] && position < span[1]) {
                return true;
            }
        }
        return false;
    }
}
