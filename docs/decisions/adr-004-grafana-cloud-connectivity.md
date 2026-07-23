# ADR-004. Grafana Cloud 연동 — 경로·테넌트·최소권한 토큰

- 날짜: 2026-07-22
- 상태: 채택 (연동 3/3 실측 검증 완료)
- 관련: `client/` 패키지, `.env.example`

## 배경

rca-agent는 Grafana Cloud의 세 백엔드를 읽기 API로 직접 조회한다. Grafana Cloud는
소스마다 **호스트도, 테넌트(숫자 User)도, API 경로 규칙도 다르다**. 연동 과정에서
발견한 문제 둘과 권한 결정 하나를 기록한다.

## 발견 1 — Mimir 쿼리 경로는 데이터소스 URL 규칙을 따라야 한다

Grafana Cloud의 Prometheus 데이터소스 URL은 `https://prometheus-prod-49-....grafana.net/api/prom`
처럼 **`/api/prom` 프리픽스로 끝난다**. 초기 클라이언트는 Mimir 네이티브 경로
`/prometheus/api/v1/query_range`를 하드코딩해 덧붙였는데, 이 조합은
`.../api/prom/prometheus/api/v1/...`가 되어 깨진다.

수정: 클라이언트는 표준 `/api/v1/query_range`만 덧붙이고, API 프리픽스는 `MIMIR_URL`이
데이터소스 URL 그대로 담는다. 수정 후 실쿼리 200 + `hikaricp_connections_active` 실데이터
수신 확인. 교훈 — **읽기 연동의 정답은 "데이터소스 설정 화면의 URL을 그대로"**이며,
경로 조립 규칙을 코드가 소유하면 안 된다.

## 발견 2 — Loki 401의 원인 격리: 스코프가 아니라 테넌트였다

Loki만 `401 "the token is not authorized to query this datasource"`가 났다.

진단 논리: **같은 토큰으로 Tempo 200, Mimir 200, Loki만 401**. 토큰 자체·리전·네트워크가
문제라면 셋 다 실패해야 하므로, 남는 변수는 Loki의 URL/User(테넌트)뿐이다. 스코프에
`logs:read`가 있음을 확인한 뒤에도 401이 유지되어 이 격리가 맞았고 — 실제로 Loki
URL/User가 데이터소스 실값이 아닌 추측값이었다:

| | 잘못 입력했던 값 | 데이터소스 실값 |
|---|---|---|
| URL | `insight-logs-prod-ap-northeast-0.grafana.net` | `logs-prod-030.grafana.net` |
| User | 1606854 | 1564643 |

교체 후 200. 교훈 — 소스가 3개면 **부분 성공 패턴 자체가 진단 도구**다 (2/3 성공 →
공유 변수 배제, 개별 변수로 격리).

## 결정 — 토큰은 read 3스코프 최소권한, 쓰기 스코프는 거부

Access Policy는 `traces:read` + `logs:read` + `metrics:read`만 부여했다. 향후 알림
연동을 대비해 `alerts:write`를 추가하자는 안이 있었으나 기각했다:

1. 현재 notifier는 Slack/Discord webhook에 직접 POST하므로 Grafana 알림 API를 쓰지
   않는다 — 부여해도 사용처가 없다.
2. 로드맵의 "Alertmanager webhook 수신"은 Grafana→에이전트 방향이라 에이전트 토큰
   스코프와 무관하다.
3. 이 토큰은 `.env` 평문으로 서버에 올라간다. read-only면 유출 시 피해가 "관측 데이터
   열람"에 그치지만, 쓰기가 붙으면 알림 규칙 조작까지 열린다. 쓰기가 실제로 필요해지는
   시점에 **별도 정책·별도 토큰**으로 발급해 읽기/쓰기 토큰을 분리 회수 가능하게 한다.

## 최종 상태 (실측)

Tempo 200(실트레이스 24,619B), Loki 200, Mimir 200(실메트릭). 세 소스 모두 실제 prod
데이터로 검증 완료.
