package com.yogurtte.rca.triage;

/**
 * 명시적 from/to가 절반만 오거나 순서가 뒤집힌 요청. API 경계에서 400으로 매핑된다.
 *
 * <p>거부가 곧 수정이다 — 구 파서는 이 입력을 조용히 무시하고 질문 파싱으로 떨어져,
 * 과거 데이터 재조사가 <b>오늘 창을 조사하고도 눈치채지 못하는</b> 오염을 만들 자리였다
 * (결함 22 · round-3 README §B-26).
 */
public class InvalidTimeWindowException extends IllegalArgumentException {

    public InvalidTimeWindowException(String message) {
        super(message);
    }
}
