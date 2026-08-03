package com.yogurtte.rca.triage.incident;

import java.time.Instant;

/**
 * 스윕 원본에서 코드가 뽑아낸 "언제 무엇이 이상했나" 한 건.
 * 스윕 JSON에서 신호를 뽑는 규칙은 {@link SignalExtractor}에 있다.
 *
 * <p><b>점이 아니라 구간이다.</b> 로그 발생률 응답의 {@code [[1785315300,"4"]]} 를 시각 하나로
 * 읽으면 틀린다 — 쿼리가 {@code count_over_time(...[5m])} 이므로 그 값은 <b>직전 5분 사이에
 * 4건</b>이라는 뜻이고, 점으로 읽으면 최대 5분 오차가 생긴다. 실제 회차 리포트가 버킷 시각을
 * 사건 시각으로 읽은 사례가 있다.
 *
 * @param from      구간 시작. 점 신호는 {@code from == to}
 * @param to        구간 끝
 * @param channel   어느 신호원인가
 * @param precision 시각을 얼마나 믿을 수 있나. 창 계산의 여유 폭이 여기서 나온다
 * @param resource  어디서 — 응답 라벨에서 뽑는다 (서비스명 · job)
 * @param signature 무엇이 — 엔드포인트 · 예외 클래스 · 쿼리 이름. <b>같은 리소스의 다른 사건을
 *                  가르는 축</b>이다
 * @param what      사람이 읽는 설명. 값의 크기가 여기 들어간다
 * @param ref       되짚을 단서 (traceId · 쿼리 · 스트림)
 */
public record Signal(
        Instant from,
        Instant to,
        Channel channel,
        Precision precision,
        String resource,
        String signature,
        String what,
        String ref) {

    public Signal {
        resource = blankTo(resource, "?");
        signature = blankTo(signature, "?");
        if (to == null) {
            to = from;
        }
    }

    /**
     * 군집 키. <b>라벨 3축이고 시간은 들어가지 않는다</b> — 사건이 시간상 교차해도
     * (1번-A → 2번-A → 1번-B → 2번-B) 이 키로는 갈린다. 시간은 같은 키 안에서만 쓴다.
     */
    public String key() {
        return channel + "|" + resource + "|" + signature;
    }

    private static String blankTo(String value, String fallback) {
        return (value == null || value.isBlank()) ? fallback : value;
    }
}
