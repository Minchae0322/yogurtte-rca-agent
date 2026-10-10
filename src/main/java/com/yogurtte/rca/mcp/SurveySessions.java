package com.yogurtte.rca.mcp;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 탐색 세션 보관소. <b>상한이 있는 LRU</b>다 - MCP 클라이언트가 세션을 닫아 준다는 보장이 없어서
 * 그냥 쌓아 두면 JVM이 샌다.
 *
 * <p>상한을 작게 잡는다({@value #MAX}). 한 조사가 끝나면 그 핸들은 다시 쓰이지 않고, 사람이
 * 동시에 여는 조사가 두 자리 수가 될 일은 없다. 넘치면 가장 오래된 것부터 버린다 -
 * 버려진 핸들로 호출하면 "다시 탐색하라"는 안내가 나가므로 조용한 오답이 되지 않는다.
 */
class SurveySessions {

    /** 동시 보관 상한. 넘으면 가장 오래 안 쓰인 것을 버린다. */
    private static final int MAX = 16;
    /** 이보다 오래된 세션은 돌려주지 않는다 - 창 밖의 낡은 후보로 수집하면 엉뚱한 구간을 본다. */
    private static final Duration TTL = Duration.ofHours(2);

    private final Map<String, SurveySession> sessions = new LinkedHashMap<>(MAX + 1, 1.0f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, SurveySession> eldest) {
            return size() > MAX;
        }
    };

    synchronized void put(SurveySession session) {
        sessions.put(session.id(), session);
    }

    synchronized Optional<SurveySession> get(String id, Instant now) {
        SurveySession found = sessions.get(id);
        if (found == null) {
            return Optional.empty();
        }
        if (found.createdAt().isBefore(now.minus(TTL))) {
            sessions.remove(id);
            return Optional.empty();
        }
        return Optional.of(found);
    }
}
