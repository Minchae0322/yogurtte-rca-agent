package com.yogurtte.rca.time;

import java.util.List;

/** 날짜를 이름으로 부르는 말. */
public enum NamedDay implements Aliased {

    TODAY(0, "오늘", "today"),
    YESTERDAY(1, "어제", "yesterday"),
    TWO_DAYS_AGO(2, "그저께", "그제");

    private final int daysAgo;
    private final List<String> aliases;

    NamedDay(int daysAgo, String... aliases) {
        this.daysAgo = daysAgo;
        this.aliases = List.of(aliases);
    }

    public int daysAgo() {
        return daysAgo;
    }

    @Override
    public List<String> aliases() {
        return aliases;
    }

    public static NamedDay of(String token) {
        return Aliased.byAlias(values(), token);
    }
}
