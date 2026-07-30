package com.yogurtte.rca.triage;

import java.time.Instant;

/**
 * 스윕 원본에서 코드가 뽑아낸 "언제 무엇이 이상했나" 한 건.
 *
 * <p><b>점이 아니라 구간이다.</b> 로그 발생률 응답의 {@code [[1785315300,"4"]]} 를 시각 하나로
 * 읽으면 틀린다 — 쿼리가 {@code count_over_time(...[5m])} 이므로 그 값은 <b>직전 5분 사이에
 * 4건</b>이라는 뜻이고, 점으로 읽으면 최대 5분 오차가 생긴다. 실제 회차 리포트가 버킷 시각을
 * 사건 시각으로 읽은 사례가 있다.
 *
 * <p><b>임계값으로 판정하지 않는다.</b> 변화·부재·존재만 신호로 센다. 이유 셋 —
 * ① 평시 baseline 데이터가 없다 ② 문항별 임계값을 코드에 박으면 정답을 심는 것이다
 * ③ 에이전트가 이미 같은 기준을 쓴다(<i>"전 구간 상수 — 변화가 없으므로 무관"</i>).
 * 값의 크기는 판정이 아니라 {@link #what}에 담아 모델이 보게 한다.
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

    public enum Channel { TEMPO, LOKI, MIMIR }

    /**
     * EXACT는 ms 단위로 정확한 시각(트레이스 span·로그 라인), BUCKET은 집계 해상도만큼
     * 흐릿한 시각(메트릭 샘플·로그 발생률 버킷)이다.
     */
    public enum Precision { EXACT, BUCKET }

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
     *
     * <p>채널이 1축인 이유: 지문의 성격이 채널마다 다르다(Tempo는 엔드포인트, Loki는 예외
     * 클래스, Mimir는 지표). 섞으면 같은 사건의 Tempo 신호와 Loki 신호가 지문이 달라 갈라진다.
     */
    public String key() {
        return channel + "|" + resource + "|" + signature;
    }

    private static String blankTo(String value, String fallback) {
        return (value == null || value.isBlank()) ? fallback : value;
    }
}
