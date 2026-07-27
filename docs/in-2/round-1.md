# IN-2 회차 1 — Kafka 5분 17초 다운: 발행측 유실, 복구 불가

## 한눈 요약

| | |
|---|---|
| **실제 원인** | Kafka 브로커 정지 5분 17초 — producer `send()`가 메타데이터 fetch에서 `max.block.ms`(60s) 동기 블로킹 후 실패, 발행 경로엔 재시도·DLQ·outbox가 없어 그 자리에서 영구 유실 |
| **실제 영향** | 트리거 댓글은 200(54ms), 알림 1건 영구 유실 (실사용자 피해 0건 실측) |
| **에이전트 파악 원인** | 직접 원인 "content의 Kafka 발행 실패 → chat은 도달조차 안 함" **확정** + "이미 유실, 재시도·아웃박스 없음" **정답**. 하위 원인 1위는 "토픽 부재"로 오선정 (실제 = 후보 2 "브로커 다운", 확신도 낮음~중간) |
| **판정** | 발행 실패·유실·소비측 배제 전부 정확 / 하위 원인 감별 실패 — `peer.service`의 클러스터 ID(죽기 전 캐시)를 "연결 성립" 증거로 오독 + 브로커 측 데이터 전무 |
| **§8 채점** | **80 / 100** (근본 20/40 · 근거 30/30 · 오귀인 20/20 · 조치 10/10) — 감점 전액이 하위 원인 감별. 근거·판정은 [채점 대장](../scoring/README.md#in-2-회차-1--80--100). **N=1이라 §8.1상 인용 불가** |
| **토큰·비용·시간** | in 39,202 / out 8,266 tok · **$0.9237** · 123.4s |
| **에이전트 보고서 전문** | [round-1-rca-report.md](round-1-rca-report.md) |

## 장애 상황

- 주입: 인프라 노드 `docker stop kafka` — **09:14:23 ~ 09:19:41 UTC (5분 17초)** = KST 18:14:23 ~ 18:19:41
- 트리거: 다운 중 댓글 1건(09:15:36Z, commentId 93) — 댓글 저장(MySQL)은 커밋 성공, 알림 이벤트만 Kafka로 발행 시도
- 결말: `send()`가 60초 동기 블로킹 후 `TimeoutException: Topic user.notifications not present in metadata after 60000 ms` — 리스너가 catch(설계상 유실 허용)하고 ERROR 로그 + span error만 남김. **CH-1과 달리 받아줄 재시도 경로가 없어 즉시 영구 유실.** E2E 알림 지연: 0.9s(정상) → ∞(유실) → 1.5s(복구 후)

## 스크린샷용 traceId

| 용도 | traceId |
|---|---|
| **장애 트레이스** (60,060ms error span, chat 부재) | `6a65d0391efd3125490830158dec0de4` |
| 정상 대조 트레이스 (주입 전 baseline, 2-서비스 완전체) | `6a65cf8832660d0e73c7567e2ee24dbb` |
| 복구 확인 트레이스 (원복 후) | `6a65d1869dbe9a78868ed683d1b67743` |

## 실제 신호 발췌

**Tempo — 장애 트레이스의 모양** (KST 18:15:37 시작)

- `http post /battles/{battleId}/items/{itemId}/comments` **200 · 54.8ms** — insert 커밋 완료(`jdbc.generated-keys=93`). 사용자는 아무 이상 못 느낌.
- `notification-publish` span: `error="Send failed"` / 그 자식 `publish user.notifications`: **정확히 60,060ms** 후 `STATUS_CODE_ERROR` + 예외 원문. `max.block.ms=60000` 기본값이 트레이스에 자수 — CH-1의 30.0s(serverSelectionTimeoutMS)와 같은 패턴.
- **chat-service span 0개** — CH-1 장애 트레이스(30s 에러 span 4개 + DLQ 발행)와의 결정적 차이. "소비자가 시끄러운 장애 vs 소비자가 애초에 등장하지 않는 장애".

**Loki — 발행 실패의 유일한 로그 흔적** (쿼리: `{service_name="content-service"} |= "알림 발행 실패"` · KST 18:14~18:20)

```
18:16:37.441 KST  ERROR LoggingProducerListener - Exception thrown when sending a message
18:16:37.445 KST  ERROR NotificationEventListener - [Notification] 알림 발행 실패  traceId=6a65d039...
                  TimeoutException: Topic user.notifications not present in metadata after 60000 ms
```

트리거(18:15:37) 정확히 +60초. 같은 창에서 producer NetworkClient WARN ~547건/15분 스팸(`|~ "NetworkClient|Connection to node"`) — 반면 **chat 로그는 0건**(org.apache.kafka=ERROR 억제): 발행자만 울고 소비자는 침묵.

**Mimir — 원인 메트릭** (쿼리: `kafka_brokers` · KST 18:10~18:25)

다운 구간에서 값이 0이 되는 게 아니라 **시계열이 끊긴다**(kafka-exporter가 브로커에 못 붙으면 메트릭 자체를 안 내보냄). 값 기반 알람이 안 걸리는 이유이자, `absent()` 계열 알람이 필요한 근거 — 그래프의 공백을 그대로 스샷.

## 원인 대조

| | 내용 |
|---|---|
| **실제 원인** | Kafka 브로커 전면 다운 (`docker stop kafka`, 5분 17초) |
| **에이전트 파악** | 상위: "발행 실패로 유실, 소비 단계 미도달" 확정 — 정답. 하위 랭킹: ① 토픽 부재(중간) ② 브로커 다운/리더 불가용(낮음~중간) ③ 네트워크/설정(낮음) — **실제는 ②인데 ①에 무게** |
| **오독 지점** | span의 `peer.service="Apache Kafka: <clusterId>"`를 "브로커 연결 성립" 증거로 읽고 "연결되는데 메타데이터만 없음 = 토픽 부재"로 추론 — 클러스터 ID는 죽기 전 연결에서 캐시된 값이라 브로커 전면 다운과 모순되지 않는다 |
| **판별 불가의 구조적 이유** | 브로커 측 데이터 전무: `kafka_brokers`가 수집 목록에 없고(있었어도 부재라 "no series; skipped"로 표기 — 그 부재 자체를 단서로 쓰는 프롬프트 필요), Loki 셀렉터 결함으로 producer WARN 547건도 못 봄 |
| **정확했던 것** | 유실 판정("이미 유실, 재시도·아웃박스 없음"), 소비측 배제(chat span 0개 근거), DB/풀/GC 배제, outbox·발행 DLQ 권고, 60s=max.block.ms 대조 |

- 평가 상세: [AE-03](../findings/ae-03-rca-v0-in2-blind-eval.md) · 대비쌍 실측: CH-1 [round-2](../ch-1/round-2.md)
