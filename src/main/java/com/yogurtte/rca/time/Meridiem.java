package com.yogurtte.rca.time;

import java.util.List;

/**
 * 시각 앞에 붙는 오전오후 표지 — "오후 2시"의 "오후". 시(hour)를 24시간제로 보정한다.
 *
 * <p>같은 단어(새벽·밤·저녁·아침)가 단독으로 쓰이면 {@link DayPart}(시간대)다.
 * 역할이 달라 어휘를 일부러 두 곳에 둔다 — 시각 앞에서는 보정 규칙, 단독으로는 구간.
 */
public enum Meridiem implements Aliased {

    AM("오전", "아침", "새벽") {
        public int adjust(int hour) {
            return hour % 12;
        }
    },
    PM("오후", "저녁", "밤") {
        public int adjust(int hour) {
            return hour % 12 + 12;
        }
    },
    /** "점심 1시"는 13시이고 "점심 12시"는 12시다 — 정오 근방만 오후로 끌어올린다. */
    LUNCH("점심") {
        public int adjust(int hour) {
            return hour <= 6 ? hour + 12 : hour;
        }
    };

    private final List<String> aliases;

    Meridiem(String... aliases) {
        this.aliases = List.of(aliases);
    }

    public abstract int adjust(int hour);

    @Override
    public List<String> aliases() {
        return aliases;
    }

    public static Meridiem of(String token) {
        return Aliased.byAlias(values(), token);
    }
}
