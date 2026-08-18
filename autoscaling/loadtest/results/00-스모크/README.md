# T0 스모크 테스트

> **결론:** 모든 HTTP·WebSocket 경로가 정상 동작했다. 이후 부하 시험을 진행할 수 있다.

| 구분 | 내용 |
|---|---|
| 상태 | 실측 완료 · 2026-08-16 |
| 목적 | 배포 직후 주요 경로의 연결 가능 여부 확인 |
| 시나리오 | 1 VU · 1회 · CloudFront 경유 |
| 판정 | **통과** |

## 시험 범위

`POST /api/auth/login` → `GET /feeds/scroll` · `/feeds/hot` · `/battles` →
`GET /v1/chat/rooms` → WebSocket STOMP CONNECT(`/api/chat/ws/websocket`)

## 결과

| 지표 | 실측값 | 판정 |
|---|---:|---|
| 전체 체크 | 100% | 통과 |
| HTTP p95 | 209ms | 정상 |
| WebSocket 연결 | 69ms | 정상 |

CloudFront를 경유한 WebSocket 연결이 성립함을 처음으로 확인했다. `ws-yogurtte.com` 직결
경로를 폐쇄한 직후의 측정이므로, 이후 시험의 전제 조건도 함께 검증됐다.

## 다음 단계

기준선 시험(T1)으로 진행한다. 서사상 위치는 [Chapter 1](../../../서사.md)의 선행 조건이다.

공통 실행 환경·시험 명세는 [인덱스](2026-08-16-실행-1회차.md) 참고.
