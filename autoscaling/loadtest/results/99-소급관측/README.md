# 소급 관측: T1·T2-C 서버 지표 복원 + CPU 크레딧

> **결론:** T1과 T2-C에서 chat CPU는 각각 0.99와 0.999까지 포화했다. 또한 실제 연결 중에도 WebSocket 사용자 지표는 0이어서, 해당 메트릭의 구현 결함도 확정했다. 시험 4일 전체의 CPU 크레딧은 CloudWatch 소급으로 **미소진 확정** - 관측된 한계는 전부 크레딧 스로틀이 아니라 실제 capacity다.

| 구분 | 내용 |
|---|---|
| 상태 | 실측 완료 · 2026-08-17 |
| 목적 | 서버 동시 관측이 없던 T1·T2-C의 정점 지표 복원 |
| 방법 | Mimir 히스토리에 `max_over_time` 적용 |
| 핵심 판정 | chat CPU 포화 · WebSocket 세션 Gauge 구현 결함 |

## 관측 배경

T0~T2-C는 서버측 동시 관측 없이 실행했다(관측 절차는 T2-A부터 도입). Mimir 히스토리로
두 시험 창을 소급 조회해, 정점 스냅샷을 놓친 경우에도 지표를 복원할 수 있음을 확인했다.

## 복원 결과

| 창 | chat CPU max | chat 풀 pending | content CPU max | websocket_active_users |
|---|---:|---:|---:|---:|
| T1 (17:40~17:51 KST) | **0.99** | 0 | 0.52~0.58 | **0** |
| T2-C (18:33~18:41) | **0.999** | 0 | ~0.01 | **0** |

## 확정된 사실과 후속 조치

1. **T1의 `GET /chat/rooms` p95 1.8s는 chat 단일 레플리카 CPU 포화였다.** 같은 창에서
   content는 CPU 52~58%로 여유. [T1.5와의 차분](../02-사용자여정/README.md) 판정("동시성에서 온다")에
   기전이 붙었다.
2. **T2-C 팬아웃 미달은 브로커 아웃바운드 포화 가설(①)이 유력.** 메시지가 돌던 창 내내
   chat CPU 0.999 - 내보낼 CPU 자체가 없었다. ②(참여자 선별 전송) 기각에는 참여자
   세션만의 재시험 필요.
3. **`websocket_active_users`는 실연결 200개에서도 0 - 메트릭 구현 결함 확정.**
   RCA 앵커에서 세 회차 불성립한 그 메트릭이 STOMP CONNECTED 세션 약 200개가 실존하는
   창에서도 0. "트래픽이 없어서 0" 해석 기각. `WebSocketMetricsConfig`(SimpUserRegistry
   기반)가 Bearer 인증 세션을 못 세는 것으로 보임(가설 - Principal 미설정 의심).
   RCA 트랙의 A(앱 계측) 변경군 결함으로 이관 - **서사 Chapter 4 전에 WS 세션 Gauge
   재구현이 선행 과제**인 이유.

## CPU 크레딧 소급 (2026-08-19 · CloudWatch)

설계 전제의 *"크레딧 고갈을 한계로 착각하지 않는다"* 를 실측으로 닫았다.
AWS CLI(`CloudWatchReadOnlyAccess`+`AmazonEC2ReadOnlyAccess`)로 시험 기간 전체
(08-16 00시 ~ 08-19 21시 KST)의 `CPUCreditBalance` 시간당 최솟값을 소급 조회했다.

| 노드 (인스턴스) | 타입 | 상한 | 기간 최저 | 최저 시각 (KST) |
|---|---|---:|---:|---|
| worker1 (`yogurtte-worker-01`) | t3.medium | 576 | **547.5 (95%)** | 08-18 20시 - spike·mixed 창 |
| worker2 (`yogurtte-dev`) | t3.small | 576 | 565.8 | 08-17 23시 - T2-B 창 |
| master (`yogurtte-master`) | t3.micro | 288 | 287.8 | (사실상 무변동) |
| edge (`yogurtte-proxy-01`) | t3.nano | 144 | 135.8 | 08-18 19시 |
| infra (`yogurtte-infra`) | t3.small | 576 | 576.0 | (무변동) |

확정된 사실:

1. **크레딧 소진 없음.** 4일 최대 소모가 worker1의 28크레딧(t3.medium 풀버스트 약 17분치)이다.
   spike의 실패 64.8%·노드 CPU 99%, mixed의 auth 붕괴는 **크레딧 스로틀이 아니라 실제
   baseline capacity 포화**로 확정.
2. **전 노드가 `unlimited` 모드다** (`describe-instance-credit-specifications` 실측).
   이 모드는 크레딧 0에서도 성능 제한 대신 초과분 과금으로 넘어가므로 스로틀 자체가
   구조적으로 불가능하고, 잔량이 95% 아래로 내려간 적이 없어 초과 과금도 없었다.
3. 설계의 t3 우려 시나리오(`Credit 고갈 → CPU 성능 제한 → 응답 급증`)는 이 클러스터
   구성에서는 발현 경로가 없다 - 단 soak(1시간)급 장시간 시험에서는 소모 누적을 재확인할 것.
