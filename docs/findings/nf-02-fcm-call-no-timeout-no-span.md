# NF-02. FCM 동기 호출 — 타임아웃 미설정, span 부재, 예외 삼킴

- 심각도: **높음** | 상태: 확정 (트레이스 + 코드)
- 위치: toy-chat `FcmPushNotificationProvider.sendToTokens` (34행~)

## 관측 (트레이스 `6a5dc9c1990469248cfea377e1d7b4a0`)

`push-dispatcher#dispatch` 996ms 중 **994ms가 어떤 자식 span으로도 설명되지 않는다**
(유일한 자식 KEYS는 0.9ms). 이 트레이스에서 traceId 매칭 로그 0건, ERROR/WARN 0건 —
1초 가까운 구간이 트레이스에도 로그에도 완전히 비어 있다.

## 코드 근거 (`FcmPushNotificationProvider.java`)

```java
BatchResponse response = firebaseMessaging.sendEachForMulticast(message); // 동기, 타임아웃 미설정
...
} catch (FirebaseMessagingException e) {
    log.error("[FCM-SEND] ...", e);   // 예외를 삼키고 로그만 남김 - 호출자는 성공과 구분 불가
}
```

세 가지가 겹쳐 있다:

1. **동기 네트워크 호출에 타임아웃이 없다** — FCM이 느려지면 컨슈머 스레드가 무기한
   블로킹되고, NF-01의 커넥션 점유와 곱해져 장애 전파가 된다.
2. **span이 없다** — 라이브러리(Brave) 계측이 Firebase SDK 내부 HTTP를 안 잡는다.
   994ms 갭의 정체를 관측만으로는 확정할 수 없었던 이유이며, ADR-001에서 수용한
   "라이브러리 계측 잔여 리스크"가 실측으로 현실화된 사례다.
3. **예외를 삼킨다** — 발송 실패가 리턴값·예외 어느 쪽으로도 전파되지 않아, 재시도도
   실패율 메트릭도 만들 수 없다.

## 심각도 판단

이 요청의 end-to-end 지연(1.26s)의 **79%**가 이 호출이다. 그리고 실패 모드가 조용하다
— 느려져도(무한 대기) 실패해도(예외 삼킴) 신호가 없다. 알림은 유일한 사용자 접점
비동기 경로다.

## 개선 방향

1. FCM 호출에 타임아웃 설정 (Firebase SDK HTTP transport 옵션), 실패 시 재시도 정책.
2. 발송 결과를 메트릭으로: 성공/실패 카운터, 소요시간 히스토그램.
3. 커스텀 span(`@NewSpan` 또는 수동 계측)으로 외부 호출 구간을 관측 가능하게.

## 개선 검증 방법

- 개선 후 같은 흐름의 트레이스에서 dispatch 아래 **fcm-send span이 보여야** 하고
  미계측 갭이 소멸해야 한다 (994ms → 0).
- C3(FCM 지연 주입) 시나리오에서 지연이 타임아웃 값에서 절단되는지 확인.
- rca-agent 리뷰 모드가 이 흐름에서 "미계측 갭"을 더 이상 1순위로 올리지 않아야 한다
  — 에이전트 평가(N1 정답지)와 계측 개선이 맞물리는 지점.
