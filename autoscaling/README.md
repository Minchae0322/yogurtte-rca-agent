# yogurtte 오토스케일링 개선 로드맵

> 목표: 부하테스트로 문제를 발굴하고, 오토스케일링(HPA/KEDA)으로 해결하며, 그 과정에서 노드 구조를 동일 비용으로 재편한다.
> 원칙: 각 단계의 "발견된 문제"가 다음 단계의 "동기"가 된다. 모든 단계는 before/after 수치를 남긴다.
> **포트폴리오 서사는 [서사.md](서사.md)가 SoT다** - 의사결정 트리 + Plan A/B + Decision Gate 구조. 이 로드맵은 실행 절차·명령어 쪽이다.
> **시험 설계는 [부하테스트-설계-1.md](부하테스트-설계-1.md)가 SoT다** - 4 Phase · 17개 시험 · 축별 진단과 결과별 대응.
> 실측 기반 보정과 실행에 필요한 접속 정보는 [부록](#부록---현재-인프라-실측-2026-08-17)에 있다.

---

## 전체 흐름 한눈에

| 단계 | 하는 일 | 발견/해결하는 문제 | 포트폴리오 증거물 |
|---|---|---|---|
| 1 | Prometheus + Grafana 구축 | (전제 조건) | 대시보드 스크린샷 |
| 2 | k6 baseline 부하테스트 | p99 폭증, OOM, 단일 replica 병목 | before 그래프 |
| 3 | requests/limits + HPA 적용 | 파드 Pending = 노드 파편화 발견 | Pending 이벤트 캡처 |
| 4 | 노드 통합 (5대 → 3대) | 파편화 해결, 동일 비용, auth 이중화 | 비용표 + 노드 구성도 |
| 5 | 재검증 + KEDA + chat 스케일아웃 | Kafka lag, WebSocket 브로드캐스트 | after 그래프, 2→N 파드 증가 그래프 |

---

## Phase 1 - 관측 기반 구축

### 스택 선택
kube-prometheus-stack(헬름 풀세트)은 이 클러스터엔 무겁다. 최소 구성으로:

- **Prometheus** (retention 3~7일, 리소스 제한 필수)
- **Grafana**
- **node-exporter** (DaemonSet, 노드당 ~30MB)
- **kube-state-metrics**
- 알림: Grafana Alerting → Slack webhook (Alertmanager 생략 가능)

### 배치안
현재 노드 중 여유가 가장 큰 곳(worker 1 추정)에 배치하되, 반드시 limits를 걸 것:

```yaml
# prometheus 예시
resources:
  requests: { memory: "300Mi", cpu: "100m" }
  limits:   { memory: "500Mi", cpu: "500m" }
# grafana 예시
resources:
  requests: { memory: "100Mi", cpu: "50m" }
  limits:   { memory: "200Mi", cpu: "200m" }
```

주의: 모니터링 스택 자체가 부하테스트 중 OOM으로 죽으면 증거가 날아간다. limits와 retention을 보수적으로.

### Spring 앱 메트릭 노출
각 서비스에 actuator + micrometer 추가:

```
management.endpoints.web.exposure.include=health,prometheus
```

핵심 커스텀 지표: HTTP p99 (자동), WebSocket 세션 수 (chat, Gauge 직접 등록), Kafka consumer lag (kafka_consumer_fetch_manager_records_lag 또는 exporter).

### 완료 기준
- [ ] 노드별 CPU/메모리, 파드별 메모리, HTTP p99, WS 세션 수가 Grafana에 보인다
- [ ] `kubectl top nodes` / `top pods -A` 결과를 기록해둔다 (실측 데이터 = 이후 모든 판단의 근거)

---

## Phase 2 - Baseline 부하테스트 (아무것도 고치지 말 것)

### 주의사항
- 부하는 CloudFront를 거치지 말고 **ingress에 직접** 쏜다 (CDN 전송비 + 측정 오염 방지)
- k6 실행은 클러스터 밖(로컬 PC 또는 별도 무료 인스턴스)에서. 클러스터 안에서 돌리면 부하 도구가 측정 대상의 자원을 뺏는다
- 각 시나리오 사이에 시스템이 안정될 시간을 둔다

### 시나리오 A - 로그인 폭주 (auth)

```javascript
// login-burst.js
import http from 'k6/http';
import { check } from 'k6';

export const options = {
  stages: [
    { duration: '1m', target: 50 },
    { duration: '2m', target: 300 },   // 스파이크
    { duration: '2m', target: 300 },
    { duration: '1m', target: 0 },
  ],
  thresholds: {
    http_req_duration: ['p(99)<500'],  // 통과 기준: p99 500ms
    http_req_failed: ['rate<0.01'],
  },
};

export default function () {
  const res = http.post('https://<INGRESS>/api/auth/login',
    JSON.stringify({ email: `user${__VU}@test.com`, password: 'test1234' }),
    { headers: { 'Content-Type': 'application/json' } });
  check(res, { 'status 200': (r) => r.status === 200 });
}
```

### 시나리오 B - 콘텐츠 조회 스파이크 (content)
같은 형태로 GET 요청, target을 500까지. 읽기 경로라 더 세게 걸어도 된다.

### 시나리오 C - 채팅 동시접속 (chat, WebSocket)

```javascript
// chat-ws.js
import ws from 'k6/ws';
import { check } from 'k6';

export const options = {
  stages: [
    { duration: '2m', target: 200 },   // 동시 WS 연결 200
    { duration: '3m', target: 200 },
    { duration: '1m', target: 0 },
  ],
};

export default function () {
  const res = ws.connect('wss://<INGRESS>/ws', {}, (socket) => {
    socket.on('open', () => {
      socket.setInterval(() => socket.send(JSON.stringify({ type: 'chat', msg: 'hello' })), 3000);
    });
    socket.setTimeout(() => socket.close(), 60000);
  });
  check(res, { 'ws status 101': (r) => r && r.status === 101 });
}
```

### 기록할 것 (before 증거물)
- k6 요약 출력 (p95/p99, 실패율, RPS)
- Grafana: 파드별 메모리 곡선, 노드 CPU, OOMKilled 이벤트 (`kubectl get events`)
- 무너진 지점 메모: "몇 VU에서 무엇이 먼저 부러졌는가" - infra EC2(Kafka/Redis)가 앱보다 먼저 무너지면 그것도 발견으로 기록

---

## Phase 3 - HPA 적용 → 의도된 실패 관찰

### 3-1. JVM 다이어트 (HPA 전에)

```yaml
env:
  - name: JAVA_TOOL_OPTIONS
    value: "-XX:MaxRAMPercentage=60 -Xss512k"
```

```
spring.main.lazy-initialization=true
```

적용 전후 파드 메모리를 기록 (예: 480MB → 300MB). 이 절감분이 곧 replica 들어갈 자리다.

### 3-2. requests/limits 실측 기반 설정
Phase 1에서 측정한 실사용량 × 1.2~1.3을 requests로:

```yaml
resources:
  requests: { memory: "300Mi", cpu: "150m" }
  limits:   { memory: "450Mi", cpu: "500m" }
```

### 3-3. HPA

```yaml
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: content-hpa
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: content
  minReplicas: 2
  maxReplicas: 5
  metrics:
    - type: Resource
      resource:
        name: cpu
        target: { type: Utilization, averageUtilization: 70 }
  behavior:
    scaleDown:
      stabilizationWindowSeconds: 120   # 부하 빠진 뒤 축소를 2분 지연 (플래핑 방지)
```

### 3-4. 같은 부하 재실행 → 예상되는 발견
HPA가 replica를 늘리려 하지만 노드에 자리가 없어 **Pending** 발생:

```bash
kubectl get pods -w                      # Pending 상태 확인
kubectl describe pod <pending-pod>       # "Insufficient memory" 이벤트 캡처 ← 핵심 증거물
kubectl get hpa -w                       # DESIRED vs READY 불일치 캡처
```

이 캡처가 Phase 4의 명분이다. "스케일링 로직은 동작하나 물리 자원이 파편화되어 실행 불가" - 여기까지가 문제 정의.

---

## Phase 4 - 노드 통합 (발견된 문제의 해결책)

### 목표 구성

| | 현재 (5 EC2) | 변경 후 (3 EC2) |
|---|---|---|
| master | t3.small (전용) | **노드 A** t3.small: 컨트롤플레인 + Ingress + 앱 파드 |
| proxy | t3.micro (NGINX 전용) | (해지) |
| worker 1 | t3.small | **노드 B** t3.small: Ingress + 앱 파드 |
| worker 2 | t3.micro | (해지 → 요금 상쇄) |
| infra | t3.small | **노드 C** t3.small: Kafka/Redis/MongoDB (유지) |
| 월 비용 | micro 8대분 상당 | micro 6대분 상당 = **약 25% 절감** |

- 배치: auth×2 (A, B), content×2 (A, B), chat×1→N (B 우선), 모니터링 (A 또는 B)
- 진입점: NGINX Ingress DaemonSet (A, B) + CloudFront origin group failover (primary A, secondary B)
- chat replica 확장 대비 sticky 설정: `nginx.ingress.kubernetes.io/affinity: "cookie"`

주: 계획했던 "worker 2 micro→small 업그레이드" 대신, master를 겸용 노드로 전환하면 노드 수를 하나 더 줄일 수 있다. master 겸용이 부담스러우면 worker 2 업그레이드안(4노드)으로 - 두 안 모두 비용 중립. 실측(top) 결과로 최종 결정.

### 무중단 이사 순서
1. NGINX Ingress를 DaemonSet으로 전환, A/B 노드에 배포
2. CloudFront origin group 설정 (A primary / B secondary), `/ws/*` behavior 추가: CachingDisabled + AllViewer origin request policy → **wss도 CloudFront 경유로 전환** (직결 제거)
3. 트래픽이 새 경로로 정상 흐르는지 확인 (모니터링으로 검증)
4. proxy(micro) 해지
5. worker 2의 content를 A/B로 이전 후 worker 2(micro) 해지
6. auth replica 2 배치, PriorityClass 설정 (auth > content: 노드 장애 시 auth 우선 생존)
7. Phase 3의 부하 재실행 → HPA가 Pending 없이 동작하는지 확인

### 완료 기준
- [ ] 어떤 앱 노드(A 또는 B) 하나를 꺼도 로그인/조회/채팅이 유지된다 (장애 주입으로 실증)
- [ ] 청구서 비교표 작성 (before/after 월 비용)

---

## Phase 5 - 재검증 + 심화 (KEDA, chat 스케일아웃)

### 5-1. After 부하테스트
Phase 2와 **동일한 시나리오**를 재실행. 뽑아야 할 그래프:
- p99 before/after 비교 (같은 축, 같은 부하)
- replica 수 시계열 (2 → N → 2 로 증감하는 곡선)
- 노드 메모리 여유율 비교

### 5-2. KEDA - Kafka lag 기반 스케일링

```yaml
apiVersion: keda.sh/v1alpha1
kind: ScaledObject
metadata:
  name: chat-consumer-scaler
spec:
  scaleTargetRef: { name: chat }
  minReplicaCount: 1
  maxReplicaCount: 3
  triggers:
    - type: kafka
      metadata:
        bootstrapServers: <INFRA_IP>:9092
        consumerGroup: chat-group
        topic: chat-messages
        lagThreshold: "100"    # 파드당 허용 lag - 부하테스트로 튜닝
```

스토리 포인트: "CPU는 낮은데 lag이 쌓이는 구간"을 baseline 그래프에서 찾아 HPA의 한계를 먼저 보여주고, KEDA 적용 후 같은 구간에서 lag이 해소되는 그래프를 붙인다.

### 5-3. chat WebSocket 스케일아웃
replica 2개 이상이 되는 순간 "다른 파드에 접속한 유저에게 메시지 전달" 문제 발생. 해결: **Redis pub/sub**

```
[유저1] ─ ws ─ [chat pod A] ─┐
                             ├─ Redis pub/sub 채널 ─ 모든 chat pod가 구독
[유저2] ─ ws ─ [chat pod B] ─┘
```

Spring 구현 요점:
- 메시지 수신 시: 로컬 세션에 브로드캐스트 + Redis 채널에 publish
- Redis 구독 리스너: 수신한 메시지를 자기 파드의 로컬 WS 세션에만 전달
- 중복 방지: 메시지에 발행 파드 ID를 실어 자기 발행분은 스킵

검증: 유저 2명을 의도적으로 다른 파드에 붙이고(파드 로그로 확인) 메시지가 교차 전달되는지 확인. 이 검증 로그 자체가 증거물.

### 5-4. (선택) 마무리 비용 항목
- worker류 노드 Spot 전환 (진입점 노드 1개는 on-demand 유지)
- NAT Gateway 청구액 확인 → 과하면 fck-nat 등 대체 검토
- t3 → t4g(Graviton) 전환 + GitHub Actions buildx 멀티아치 빌드

---

## 포트폴리오 챕터 구성 (최종 산출물)

1. **문제 발굴**: baseline 부하에서 p99 X초, OOM, N VU에서 붕괴 (그래프)
2. **1차 해결과 새 문제**: HPA 적용 → Pending 발생, 원인 = 노드 파편화 (이벤트 캡처)
3. **구조 재편**: 노드당 오버헤드 계산 → 5대를 3대로, 동일 비용, auth 이중화 (비용표, 구성도)
4. **한계 돌파**: CPU 기반 HPA가 못 잡는 Kafka lag → KEDA (lag 그래프)
5. **상태 문제 해결**: WebSocket 스케일아웃 브로드캐스트 → Redis pub/sub (교차 전달 검증 로그)
6. **결과**: 동일 부하에서 p99 X → Y, 파드 자동 증감 곡선, 월 비용 25% 절감, 노드 장애 주입 생존 실증

각 단계마다 캡처를 남기는 것을 잊지 말 것. 그래프 없는 개선기는 주장이고, 그래프 있는 개선기는 증명이다.

---

# 부록 - 현재 인프라 실측 (2026-08-17)

로드맵의 가정과 실제 환경이 다른 지점, 그리고 실행에 필요한 접속 정보 전부.
아래는 전부 이 날짜의 실측이다.

## A. 노드 실태 - 로드맵의 "5 EC2" 가정과 다르다

`kubectl get nodes` 결과 k3s 클러스터는 **4노드**이고, proxy는 별도 EC2가 아니라
**클러스터에 편입된 edge 노드**다 (해지하려면 drain + `kubectl delete node` 선행).

| 노드 | 내부 IP | 역할 | CPU 사용 | 메모리 사용 | 비고 |
|---|---|---|---:|---:|---|
| ip-172-31-38-225 | 172.31.38.225 | control-plane | 2% | 679Mi (**74%**) | SSH alias `master` |
| ip-172-31-40-241 | 172.31.40.241 | 앱 워커 | 2% | 1370Mi (**71%**) | ingress-nginx가 여기. proxy nginx의 프록시 대상 |
| ip-172-31-45-39 | 172.31.45.39 | 워커 | 4% | 3000Mi (**78%**) | 최대 메모리 노드. SSH alias `worker` |
| worker-proxy | 172.31.14.96 | **edge (k3s 노드)** | 1% | 250Mi (60%) | 외부 IP 3.36.114.36 = SSH alias `proxy`. nginx 80→172.31.40.241 |

- CPU는 유휴 시 전 노드 한 자릿수 %, **제약은 메모리다** (세 노드가 70%대).
- Kafka/Redis/MongoDB의 위치는 이 목록에서 미확인 - Redis는 172.31.46.124(공유 인스턴스,
  sample-review-report 실측). Phase 4의 "infra 노드" 실체는 착수 전에 확정할 것.

## B. 앱 현황 - JVM 다이어트·이중화의 출발점 수치

| 서비스 | replica | 유휴 메모리(파드당) | HikariCP 풀 | 비고 |
|---|---:|---:|---:|---|
| auth-service | **1** | 590Mi | **10** | 단일 장애점 + T2-A에서 풀 기아(pending 190) 실측 |
| content-service | 2 | 926Mi / 855Mi | 20 | T2-B에서 CPU 단독 포화, 풀은 버팀 |
| chat-service | **1** | 607Mi | (미측정) | T1·T2-C에서 CPU 0.99 포화 실측 |

Phase 3-1 예시의 "480MB → 300MB"보다 출발점이 높다(590~926Mi). 절감 여지가 크다는 뜻.

## C. Phase 1은 대부분 이미 구축돼 있다 - Grafana Cloud

로드맵은 인클러스터 Prometheus+Grafana 신규 구축을 전제하지만, 실제로는
`monitoring` 네임스페이스에 **Grafana Cloud k8s-monitoring 스택이 이미 돌고 있다**
(alloy-metrics/logs/receiver, kube-state-metrics, node-exporter - 114일째).
백엔드는 Grafana Cloud(Mimir·Loki·Tempo)이고, 이 레포 `.env`의
`MIMIR_URL`/`MIMIR_USER`/`GRAFANA_TOKEN`으로 직접 쿼리 가능
(부하테스트 1회차의 서버측 실측 전부가 이 경로였다).

따라서 Phase 1에서 실제 남는 일:
1. **WS 세션 수 Gauge 재구현** - 기존 `websocket_active_users`는 **실연결 200개에서도 0**
   (부하시험으로 구현 결함 확정, [실행 1회차](loadtest/results/2026-08-16-실행-1회차.md) 소급 관측 절).
   SimpUserRegistry 기반이 Bearer 인증 세션을 못 세는 것으로 보이며, 세션 카운트 방식으로 교체.
2. Kafka consumer lag 지표 확인 - `kafka_consumergroup_lag`는 Mimir에 이미 존재(RCA 트랙 사용 중).
3. MySQL 익스포터 부재 - T3-D에서 `innodb_row_lock_*` 조회 불가였다. 넣으면 락 실측이 3중 근거가 된다.
4. 인클러스터 Prometheus를 따로 세울지는 선택 - Grafana Cloud 무료 티어 한도와 retention을 확인하고 결정.

## D. Baseline(Phase 2)의 상당 부분이 이미 실측돼 있다 - 단, 경로가 다르다

[부하테스트 실행 1회차](loadtest/results/2026-08-16-실행-1회차.md)에서 로드맵 시나리오
A(=T2-A)·B(=T2-B)·C(=T2-C)를 이미 실행했다. **before 수치로 쓸 수 있는 것들:**

| 로드맵 시나리오 | 실측 (1회차) | 무너진 지점 |
|---|---|---|
| A 로그인 300 VU | p99 49.4s · 실패 29.75% | auth CPU 100% + HikariCP pending 190 (이중 병목) |
| B 콘텐츠 500 VU | p99 4.5~4.9s · 실패 0% | content 2파드 CPU 99% (우아한 포화) |
| C 채팅 WS 200 | 연결은 통과, 팬아웃 이론치의 7% | chat CPU 0.999 천장 (브로커 포화 가설) |

**주의 - 전부 CloudFront 경유 측정이다.** 로드맵 Phase 2 원칙(ingress 직결)과 다르므로,
before/after 축을 맞추려면 ① after도 CloudFront 경유로 재거나 ② before를 직결로 재측정.
직결하려면 proxy(3.36.114.36) 경유가 필요한데 SG에서 443을 막았고 80은 CloudFront IP만
허용이므로, **부하 소스 IP를 SG 80에 한시 허용**하거나 별도 진입을 뚫어야 한다.

추가로 확보된 before 증거 (로드맵이 예상 못 한 것):
- **T3-D 락 대조군 차분**: 같은 200 VU에서 분산 시 처리량 3.1배 - "락 직렬화" 실증. 챕터 1에 쓸 수 있다.
- **회복 곡선**: auth는 부하 종료 후 약 3.5분 잔류 큐 소화 (재시작 0회).
- chat 단일 replica CPU 포화 → Phase 5-3(chat 스케일아웃 + Redis pub/sub)의 동기가 이미 실측돼 있다.

## E. CloudFront/WS 전환은 이미 완료됐다 - Phase 4 이사 순서 2번의 절반

2026-08-16에 완료된 것 (이 세션 실측·실행 기록):
- 클라이언트 WS URL을 `wss://ws-yogurtte.com` → **`wss://yogurtte.com/api/chat/ws` (CloudFront 경유)** 전환.
  웹(S3 배포)·모바일(Capgo OTA 1.1.39) 반영 완료. CloudFront가 WS 업그레이드(101)를 통과시키는 것 실측 확인.
- 구 직결 경로 폐쇄: `chat-ws-ingress`·`certificate/ws-yogurtte-tls`·`secret` 삭제,
  proxy SG 인바운드 443 차단. (백업 YAML은 세션 스크래치패드에 있었음 - 복원 필요시 git 히스토리의 없음 주의)
- 따라서 Phase 4의 "wss도 CloudFront 경유로 전환 (직결 제거)"는 **이미 끝난 항목**이고,
  남는 것은 origin group failover와 `/ws/*` behavior 정리다.

## F. 실행에 필요한 접속 정보·명령어 모음

| 항목 | 값/방법 |
|---|---|
| SSH | `ssh master`(control-plane, proxy 경유 점프) · `ssh proxy`(3.36.114.36) · `ssh worker` - `~/.ssh/config` alias |
| kubectl | master에서 실행 (`ssh master "kubectl ..."`) |
| Grafana Cloud 쿼리 | 이 레포 `.env`의 `MIMIR_URL`·`MIMIR_USER`·`GRAFANA_TOKEN` (basic auth). Loki·Tempo도 동일 파일 |
| 서비스 도메인 | `https://yogurtte.com/api/{auth,content,chat}` (CloudFront) · WS `wss://yogurtte.com/api/chat/ws/websocket` |
| ingress 리밋 (평시) | `api-ingress`: conn 20 · rps 30 · rpm 600 (IP당) |
| 리밋 시험용 상향 | `kubectl annotate ingress api-ingress nginx.ingress.kubernetes.io/limit-connections=2000 nginx.ingress.kubernetes.io/limit-rps=2000 nginx.ingress.kubernetes.io/limit-rpm=120000 --overwrite` |
| 리밋 원복 | 같은 명령에 `20`/`30`/`600` |
| k6 스크립트 | `loadtest/*.js` - 실 코드 규약(SockJS+STOMP·Bearer·X-Device-Id) 반영본. 로드맵 예시 대신 이걸 쓴다 |
| 시드 계정 | `load1~200@test.com` / `test1234!` (생성 완료, userId 67~261 비연속). 정리: toy-auth-user-region `scripts/cleanup-k6-users.sql` |
| 시험용 채팅방 | roomId `6a8183c7d2c54bf44544e516` (NORMAL, load1-load2) - 실제 방이어야 메시지 경로가 측정됨 |
| k8s 매니페스트 | `~/sources/k8s-manifests/yogurtte-k8s-manifests` (apps/{auth,chat,content,user} kustomize + argocd) - HPA·requests는 여기로 |
| 앱 배포 | GitHub Actions (각 서비스 레포) · 프론트 toy-client `ci-deploy.yml`(master push) · 모바일 OTA `npm run ota:deploy` |

## G. 로드맵 실행 시 주의 (이 세션에서 배운 것)

- **한 회차에 변경군을 섞지 않는다** - JVM 다이어트(3-1)와 HPA(3-3)를 같이 넣고 재면
  개선분이 어느 쪽 것인지 증명 불가. 로드맵의 단계 분리를 지킬 것.
- **k6 VU와 ingress 리밋** - IP당 리밋이 걸려 있어 단일 머신 부하는 상향 없이 리미터를 잰다.
  상향-시험-원복 절차를 매 회 반복 (F의 명령어).
- **정점 서버 스냅샷** - 시험 도중이 아니어도 Mimir `max_over_time(...[창])` 소급 조회로
  복원 가능 (1회차 소급 관측 절이 그 방법). OOMKilled은 `kube_pod_container_status_restarts_total`과
  `kubectl get events`로 잡는다.
- **Pending 캡처는 이벤트가 빨리 사라진다** - `kubectl get events -w`를 시험 전에 미리 걸어둘 것.
