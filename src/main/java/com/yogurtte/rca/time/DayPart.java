package com.yogurtte.rca.time;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;

/**
 * 시간대의 통상 범위 — 계약이 아니라 도메인 결정이고, 그래서 코드에 표로 남긴다.
 * "밤"은 자정을 걸치므로 끝이 다음날이다.
 */
public enum DayPart implements Aliased {

    DAWN(0, 6, false, "새벽", "dawn", "early morning"),
    BREAKFAST(6, 10, false, "아침"),
    MORNING(6, 12, false, "오전", "morning"),
    LUNCH(11, 14, false, "점심"),
    AFTERNOON(12, 18, false, "오후", "afternoon"),
    EVENING(17, 21, false, "저녁", "evening"),
    NIGHT(18, 6, true, "밤", "night");

    private final LocalTime start;
    private final LocalTime end;
    private final boolean crossesMidnight;
    private final List<String> aliases;

    DayPart(int startHour, int endHour, boolean crossesMidnight, String... aliases) {
        this.start = LocalTime.of(startHour, 0);
        this.end = LocalTime.of(endHour, 0);
        this.crossesMidnight = crossesMidnight;
        this.aliases = List.of(aliases);
    }

    public Instant startOn(LocalDate date, ZoneId zone) {
        return date.atTime(start).atZone(zone).toInstant();
    }

    public Instant endOn(LocalDate date, ZoneId zone) {
        return (crossesMidnight ? date.plusDays(1) : date).atTime(end).atZone(zone).toInstant();
    }

    public String describe() {
        return start + "~" + (crossesMidnight ? "다음날 " : "") + end;
    }

    @Override
    public List<String> aliases() {
        return aliases;
    }

    public static DayPart of(String token) {
        return Aliased.byAlias(values(), token);
    }
}
