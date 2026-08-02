package com.yogurtte.rca.time;

import java.time.Duration;
import java.util.List;

/** 상대 표현("최근 2시간")의 단위. */
public enum RelativeUnit implements Aliased {

    HOURS("시간", "hours", "hour", "hrs", "hr", "h") {
        public Duration duration(long amount) {
            return Duration.ofHours(amount);
        }
    },
    MINUTES("분", "minutes", "minute", "mins", "min", "m") {
        public Duration duration(long amount) {
            return Duration.ofMinutes(amount);
        }
    },
    DAYS("일", "days", "day", "d") {
        public Duration duration(long amount) {
            return Duration.ofDays(amount);
        }
    };

    private final List<String> aliases;

    RelativeUnit(String... aliases) {
        this.aliases = List.of(aliases);
    }

    public abstract Duration duration(long amount);

    @Override
    public List<String> aliases() {
        return aliases;
    }

    public static RelativeUnit of(String token) {
        return Aliased.byAlias(values(), token);
    }
}
