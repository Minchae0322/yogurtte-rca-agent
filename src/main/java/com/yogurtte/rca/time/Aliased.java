package com.yogurtte.rca.time;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 자연어 별칭 목록을 가진 어휘. 정규식 alternation 생성({@link #alternation})과 매치 토큰
 * 역참조({@link #byAlias})가 <b>같은 목록</b>을 쓰게 하는 계약이다 — 패턴과 해석이 따로 살면
 * 한쪽만 고치는 사고가 난다.
 */
public interface Aliased {

    List<String> aliases();

    /** 별칭이 긴 것부터 — "그저께"가 "그제"보다, "early morning"이 "morning"보다 먼저 매치되어야 한다. */
    static String alternation(Aliased[] values) {
        return alternation(Arrays.stream(values).flatMap(v -> v.aliases().stream()).toList());
    }

    static String alternation(List<String> aliases) {
        return aliases.stream()
                .sorted(Comparator.comparingInt(String::length).reversed())
                .map(alias -> alias.replace(" ", "\\s*"))
                .collect(Collectors.joining("|"));
    }

    /** 패턴이 매치한 토큰 → 어휘. 패턴을 어휘에서 생성하므로 실패는 불변식 위반이고, 그래서 던진다. */
    static <E extends Aliased> E byAlias(E[] values, String token) {
        String normalized = token.toLowerCase().replaceAll("\\s+", " ");
        for (E value : values) {
            if (value.aliases().contains(normalized)) {
                return value;
            }
        }
        throw new IllegalStateException("패턴이 매치한 토큰이 어휘에 없다: " + token);
    }
}
