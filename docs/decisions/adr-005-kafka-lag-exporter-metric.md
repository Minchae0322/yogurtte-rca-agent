# ADR-005. Consumer lag은 broker-side exporter 메트릭으로 관측한다

- 날짜: 2026-07-22
- 상태: 결정됨, 적용 대기 (application.yml 교체는 전략 Phase 3 E3에서 평가와 함께 수행)
- 관련: [monitoring.md](../monitoring.md) "알려진 한계" 2번

## 배경

조사 리포트마다 같은 수집 실패가 기록되고 있었다:

```
Metric 'kafka_consumer_fetch_manager_records_lag' returned no series in this window; skipped.
```

이 메트릭은 Spring(Micrometer)이 Kafka consumer 클라이언트에서 노출하는 이름이다.
"이 시간창에 데이터가 없다"가 아니라 **메트릭 계열 자체가 존재하지 않을** 가능성을
Mimir label API로 실측했다.

## 실측

`{__name__=~"kafka.*"}` 조회 결과, 클러스터에 존재하는 kafka 메트릭은 4개뿐이다:

```
kafka_brokers
kafka_consumergroup_current_offset
kafka_consumergroup_lag
kafka_topic_partitions
```

`kafka_consumer_fetch_manager_*` 계열은 없다 — 앱들이 Kafka 클라이언트 메트릭 바인딩을
노출하지 않는다. 대신 위 4개는 `job=kafka, instance=infra-server`, 즉 **kafka_exporter**
(브로커에 직접 질의하는 별도 프로세스)가 내는 것이다. `kafka_consumergroup_lag`는
consumergroup/topic/partition 라벨로 나오며, 실측 시점 기준 `chat-service-fcm-tokens`,
`chat-service-notification-settings`, `db-writer` 전 파티션 lag=0이었다.

## 두 메트릭은 관점이 다르다 — 장애 분석엔 broker-side가 우위

| | Spring 클라이언트 메트릭 | kafka_exporter (broker-side) |
|---|---|---|
| 측정 주체 | 컨슈머 앱 자신 | 별도 exporter가 브로커에 질의 |
| 기준 | 클라이언트 fetch position | 브로커 커밋 오프셋 (log-end − committed) |
| 라벨 축 | client-id/app 중심 | consumergroup/topic/partition 중심 |
| **컨슈머가 죽으면** | **메트릭도 함께 소멸** | **계속 관측됨 — lag 상승이 그대로 보임** |

결정적 차이는 마지막 행이다. "알림이 안 온다 → 컨슈머가 죽었나?"가 전형적 장애
시나리오인데, 정확히 그 순간 클라이언트 메트릭은 앱과 함께 사라진다. 장애 분석 도구가
의존할 신호로는 실격이다. broker-side는 관측 주체가 장애 대상과 분리되어 있어 생존한다
— 이는 ADR-001에서 확인한 교훈("앱 내 계측은 앱과 운명을 같이한다")의 메트릭 버전이다.

## 결정

RCA 수집 쿼리를 `kafka_consumergroup_lag` 기반으로 교체한다. 예:

```promql
sum by (consumergroup, topic) (kafka_consumergroup_lag{consumergroup=~"chat-service.*|db-writer"})
```

적용은 평가 하네스(전략 Phase 2)가 준비된 뒤 **Phase 3 E3에서 before/after 적중률과
함께** 수행한다 — "고쳤더니 좋아졌다"를 숫자 없이 주장하지 않기 위해서다.

## 교차 검증

실측 lag=0은 실전 조사의 결론과 정합한다: 조사에서 알림 지연의 병목은 소비 적체(lag)가
아니라 `PushDispatcher.dispatch` 내부 994ms였다. lag 데이터가 있었다면 에이전트는
"적체 없음"을 근거로 dispatch 가설의 확신도를 더 올릴 수 있었을 것이다 — 이 결정이
분석 품질에 기여할 것이라는 구체적 예측이며, Phase 3에서 검증된다.
