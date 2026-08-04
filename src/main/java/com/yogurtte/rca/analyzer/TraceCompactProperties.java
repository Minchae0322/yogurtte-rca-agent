package com.yogurtte.rca.analyzer;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 트레이스 압축(B-35) 설정.
 *
 * <p><b>속성을 지우는 스위치가 없다.</b> 이 압축은 표기법만 바꾸고 값은 하나도 버리지 않는다 —
 * 그래서 "무엇을 지울지"를 고를 설정 자체가 없고, 켜고 끄는 것만 있다.
 *
 * @param enabled 압축 사용 여부. {@code false}면 어셈블 결과가 <b>바이트 단위로</b> 압축 이전과
 *                같다 — 대조군 팔이 같아야 토큰 축 비교가 성립한다.
 */
@ConfigurationProperties("rca.collect.trace-compact")
public record TraceCompactProperties(boolean enabled) {

    public static TraceCompactProperties off() {
        return new TraceCompactProperties(false);
    }

    public static TraceCompactProperties on() {
        return new TraceCompactProperties(true);
    }
}
