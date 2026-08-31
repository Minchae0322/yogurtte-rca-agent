# EC2 개선 - 같은 돈으로 vCPU 2배, 성능 +23%: 스팟·실측·다이어트의 하루

> 2026-08-22 하루의 토폴로지 재구성 기록. 모든 판단은 실측이 내렸고, 두 번의 큰 반전이 있었다 -
> **micro는 메모리가 아니라 CPU에서 기각**됐고, **replica 3의 진짜 벽은 노드가 아니라 RDS 커넥션 60**이었다.
>
> 결과: 워커 계층 $0.078 → **$0.066/hr (−15%)** · vCPU 4 → **8** · T2-B 195.6 → **241.2 rps (+23%)**
> · 배치는 cordon 곡예에서 역할 라벨 고정으로 · SSH는 전부 proxy 점프로 잠금.

전제가 된 발견들: [cpu개선.md](cpu개선.md) (CPU가 벽·메모리는 여유), [limit의 역설](cpu개선.md),
[네트워크 사건록](네트워크-사건록-vxlan-dns.md) (신규 노드 조인 체크리스트).

---

## 최종 구성

```
                     CloudFront (yogurtte.com)
                              │
                 worker-proxy · t3.nano od (ingress-nginx)
                              │
   ┌──────────────┬───────────┴───┬──────────────────┐
   ▼              ▼               ▼                  ▼
 core           content₁       content₂         content₃ + 관측
 45-39          40-241         32-115            42-158
 t3.small od    t3.small od    t3.small 스팟      t3.small 스팟
 auth·chat      (안정 replica)  $0.007/hr         alloy 스택 동거

 라벨: yogurtte.io/role=core|observability · yogurtte.io/content=true (독립 라벨 - 동거 가능)
 content: required podAntiAffinity → 노드당 정확히 1개 · Hikari 풀 12/파드
 [외부] master t3.micro(control-plane) · infra t3.small(Kafka·Redis·Mongo) · RDS MySQL(max_conn 60)
```

| 비용 (워커 계층) | 전 | 후 |
|---|---|---|
| 구성 | medium od + small od | **small od ×2 + small 스팟 ×2** |
| 시간당 / 월 | $0.078 / ~$57 | **$0.066 / ~$48** |
| vCPU / 메모리 | 4 / 6Gi | **8 / 8Gi** |
| content replica | 2 | **3** |

## 판단 과정 - 시간순, 반전 포함

### 1. 스팟 가격이 micro 논쟁을 끝냈다 (아침)

micro(온디맨드 $0.013)는 "content 워킹셋 666Mi vs 실효 가용 ~700Mi - 마진 10%"로 보류
상태였다. 그런데 스팟 실시간 조회가 판을 바꿨다:

| 서울 실측 | 온디맨드 | 스팟 |
|---|---|---|
| t3.small | $0.0260 | **$0.0070 (−73%)** |
| t3.micro | $0.0130 | $0.0037 |

**스팟 small($0.007)이 온디맨드 micro($0.013)의 절반 가격에 메모리 2배.** micro의 존재
이유(비용)를 스팟 small이 더 잘 달성하면서 마진 문제까지 없앤다. 스팟의 대가(2분 통지 회수)는
content가 정확히 흡수 가능한 워크로드다 - stateless·replica 다수·anti-affinity. 1개짜리
(auth·chat·관측 핵심)는 온디맨드 유지. `persistent+stop` 옵션으로 회수 시 종료가 아니라
정지→용량 복귀 시 자동 재시작 (iptables 등 디스크 설정 보존 확인됨).

### 2. micro 실물 실험 - 기각 사유가 뒤집혔다

"안 해보고 안 하는 것보다 해보고 안 하는 게"(사용자) - od micro를 실제로 만들어 content를
넣고 T2-B 500 VU를 걸었다. **예측(메모리 부족)이 틀렸고, 예상 못 한 벽이 나왔다:**

| 예측 vs 실측 | 결과 |
|---|---|
| 메모리 (내 예측: 마진 34Mi로 OOM) | **통과** - 워킹셋 정점 529Mi, OOM·eviction·재시작 0 |
| CPU (예상 못 함) | **기각 사유** - 노드는 1.9코어 버스트(CloudWatch 95.6% 교차 확인)했는데 **content 파드에는 0.95코어만 도달** |
| 크레딧 | 무죄 - unlimited 서플러스 정상 작동 (신규 t3 잔고 0에서도) |
| 처리량 | 서버 192.6 rps - 스팟 small 쌍(241.2) 대비 **−20%** |

기전(유력, 커널 분해 계측은 미실시): 1GB 노드에서 메모리 압박이 CPU 도둑이 된다 -
커널 회수 스캔(kswapd) + 페이지 캐시 부재로 인한 반복 디스크 I/O + 시스템 데몬 자신의
페이지 폴트가 파드 밖에서 ~1코어를 소비하고, 앱 스레드는 페이지를 기다리며 잠들어 CPU를
쓸 기회를 잃는다. small 노드들의 파드 밖 소비는 0.2~0.3코어 - micro에서만 3~5배.

**교훈: 스펙표의 vCPU는 같아도 실효 CPU는 메모리 여유의 함수다.** micro의 자리는
메모리 압박이 없는 가벼운 상주 워크로드(관측·control-plane)뿐이다.

### 3. medium 축소 - 이주 대신 in-place resize (사용자 아이디어)

원계획은 "od small 신설 → auth·chat 이주 → 관측 3곳 재배치 → medium 드레인"의 다단계였다.
사용자 제안: **medium을 그 자리에서 small로 내리면?** - EC2는 정지→`modify-instance-attribute`
→시작으로 타입 변경이 되고, **인스턴스 ID·프라이빗 IP·디스크가 보존**되므로 k3s 노드 정체성이
유지된다 → nodeSelector·helm values·라벨·iptables 전부 무수정. 이주 작업이 통째로 소멸했다.

- 대가: auth·chat 다운 ~5분 (실측: 정지 1분 + 타입변경 + 시작 + 부팅 프로브 ~4분)
- 전제: 관측 스택(~440Mi)을 먼저 빼야 small(1.9Gi)에서 auth(636)+chat(700)+시스템이 안전
  → 관측을 spot2로 선행 이주 (아래 5). resize 후 실측 69% (1.33Gi) - 안정

### 4. 배치를 cordon 곡예에서 라벨 고정으로

이주 중 파드가 의도치 않은 노드로 가는 사고(스케줄링 타이밍)가 실제로 났다. 처방:

- `yogurtte.io/role=core|observability` + **content는 독립 불리언 라벨** `yogurtte.io/content=true`
  (role 하나로는 spot2의 "관측+content 동거"를 표현 못 함 - 라벨 설계가 배치 요구를 따라감)
- auth·chat의 hostname 고정(`ip-172-31-45-39`)도 role 라벨로 교체 - **노드 교체에도 생존**
- content는 라벨(어느 노드들) × required anti-affinity(노드당 1개) = "각 노드에 정해진 개수" 성립

### 5. 관측 이주 - 3곳 치환 + helm 한 번

helm values의 `kubernetes.io/hostname: ip-172-31-45-39` 3곳(alloy-metrics·receiver·ksm)을
`yogurtte.io/role: observability`로 치환하고 upgrade. 설정의 실체는 노드가 아니라
클러스터+values 파일에 있으므로 이동 자체는 수 분, 수집 공백 1~2분.
관측이 스팟에 있는 트레이드오프: 회수 시 몇 분 관측 공백 - 데이터는 Grafana Cloud에 있고
자동 재시작이라 수용 (핵심 판단이 필요한 순간에 관측이 없을 확률 = 회수율 × 그 순간일 확률).

### 6. replica 3의 진짜 벽 - RDS max_connections 60 (오늘의 두 번째 반전)

spot2에 content 3호를 넣기 전 커넥션 검산을 위해 RDS를 실측했다:

```
max_connections: 60 · Threads_connected: 29 · Max_used_connections: 62
```

**Max_used 62 - 이미 과거 부하시험에서 천장을 쳐본 이력이 있었다** (풀 20×2 + auth 10 + chat).
replica 3(+20)이면 ~80으로 명백 초과. CPU·메모리·노드를 다 풀어놨더니 다음 벽이 DB 커넥션
- "한 벽을 치우면 다음 벽이 나타난다"의 네 번째 사례(풀→CPU→노드→DB 커넥션).

처방: **풀 다이어트 20→12** (env `SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE=12`, 실측 확인).
근거는 readOnly 전환(코드 C 1차) 이후 커넥션 점유 시간 급감 - 평시 active 한 자릿수.
12×3=36 + auth 10 + chat ≈ 56/60. **500 VU 극한에서 pending 재발 가능성은 열려 있음**
(과거 극한에서 active 18까지 실측) - 다음 T2-B가 검증한다. 장기적으로는 캐시(코드 C 2차)가
커넥션 수요 자체를 줄이는 근본 해소.

### 7. 보안 - dev SG가 전부 공개였다

새 노드에 붙이려던 기존 SG를 열어보니 **전 프로토콜 0.0.0.0/0 허용**(3306 포함)이었다.
신설 `yogurtte-worker-internal`: 인바운드는 VPC(172.31.0.0/16)만 - SSH는 proxy 점프 경유만
가능(퍼블릭 직접 SSH 타임아웃 실측). **퍼블릭 IP는 유지** - 이 VPC엔 NAT가 없어 퍼블릭 IP가
없으면 아웃바운드(ghcr pull·Grafana Cloud 전송)가 끊긴다. "퍼블릭 IP + 인바운드 0"이
NAT($45/월) 없이 도달 가능한 최적점.

## 트레이드오프 대장

| 선택 | 얻은 것 | 지불한 것 / 남은 리스크 |
|---|---|---|
| content·관측 노드 스팟화 | −73% 비용 | 회수 시 일시 축소(replica가 흡수)·관측 공백 몇 분. **회수 실사례는 아직 미경험** |
| micro 기각·small 통일 | CPU 실효 확보(+20%p) | micro 대비 월 +$2.4 |
| in-place resize | 이주 작업 제로·설정 보존 | auth·chat 다운 5분 (1회성) |
| 풀 12 | replica 3 성립 (RDS 예산 내) | 극한 부하에서 pending 재발 가능 - **재실측 대기** |
| spot2 동거(content+관측) | 노드 1대 절약 | 부하 시 content₃와 alloy가 CPU 경합 (~0.15코어) |
| 퍼블릭 IP 유지 | NAT 비용 회피 | 인바운드는 SG가 0이라 실질 노출 없음 |

## 현재 상태 - 완료 / 미측정

| 항목 | 상태 |
|---|---|
| 스팟 2대·라벨 배치·resize·관측 이주·풀 12·replica 3 | **적용·검증 완료** (파드 배치, 풀 12, 스모크 3서비스 200 실측) |
| 새 토폴로지 T2-B baseline | **검증 완료 (08-23)** - replica 3 + 풀 12에서 **서버 314.3 rps (+30%, 누적 +61%)** · med 135ms. 풀 12 대가 부분 발현(pending 25·acquire 0.99s·타임아웃 0). **RDS Threads_connected 59/60 - 종착 벽 확정** (상주 점유: 풀 minIdle 36+auth 10+chat 10). 상세: [04 재실행 8](../loadtest/results/04-콘텐츠폭주/README.md) |
| 스팟 회수 대응 | 미경험 - persistent+stop 설정만. 첫 회수가 실전 시험 |
| micro의 파드 밖 CPU ~1코어 정밀 분해 | 미측정 (node-exporter 부재) - 기각 결론에는 영향 없음 |
| RDS 커넥션 천장 이력(Max_used 62) | 발견·기록 - 어느 시험에서 쳤는지 소급 특정은 미실시 |
| master ephemeral-storage 압박 | 이주 중 발견, 태스크 #9 - **미조치** |

## 신규 노드를 추가할 때 (재사용 절차)

1. [네트워크 사건록](네트워크-사건록-vxlan-dns.md) 체크리스트 - iptables INPUT 확인이 1번
2. SG는 `yogurtte-worker-internal` · 키 toyProjectServer · AMI ami-0dc44556af6f78a7b (INPUT ACCEPT 실측됨)
3. k3s 조인은 클러스터 버전 고정(`INSTALL_K3S_VERSION`) - 마스터에서 토큰
4. 역할 라벨 부여 → 디플로이먼트는 손대지 않아도 라벨 따라 배치됨
5. content 노드 추가 시 **RDS 커넥션 예산부터 검산** (풀 12 × replica 수 + auth 10 + chat)

## 관측 중앙부의 micro spot 분리 계획 - 동거 절약이 병목 제조로 판명됐다 (08-23 수립, 미실행)

### 상황

| 항목 | 내용 |
|---|---|
| 발단 | T2-B 재실행 10 원인 해부 - **obs 동거 노드(42-158)가 최악 파드의 제조처**. 노드-파드 CPU 갭 0.29코어 · MemAvailable 최저(avg 255Mi) · 같은 92 rps에서 p95 3.19s (전용 노드 파드는 0.36s) |
| 기존 트레이드 | "spot2 동거(content+관측) = 노드 1대 절약, 경합 ~0.15코어" - **이번 실측(0.29코어+대기열 쏠림)으로 지불 비용이 상향 판정**. 이 계획이 그 행을 대체한다 |
| 결정 | 관측 **중앙부 4개를 t3.micro spot 전용 노드로 전부 이주** (~$3/월). receiver 분리 권고가 있었으나 단순화 우선으로 포함 - 리스크는 반증 조건으로 편입 |
| 이동 대상 (실측) | alloy-metrics-0 **259Mi** · alloy-receiver **99Mi·CPU max 0.36(시험 중)** · operator 55Mi · kube-state-metrics 36Mi ≈ **합 450Mi · 평시 ~0.1코어** |
| 이동 아님 | alloy-logs·node-exporter는 **DaemonSet - 노드마다 상주가 원리**. micro에도 자기 몫(~80Mi)이 뜬다 |
| micro 수용 검산 | 450 + 시스템/데몬셋 ~250 ≈ **700Mi/1GB (헤드룸 ~300Mi)** · CPU baseline 0.2코어 vs 평시 0.1 - 평시는 성립, **시험 중 receiver가 초과 주범** |

### 실행 순서 (변경군 격리 - obs 이주 단독, 앱 변경 미포함)

1. t3.micro spot 기동 - [§신규 노드 재사용 절차](#신규-노드를-추가할-때-재사용-절차) 그대로 (SG `yogurtte-worker-internal` · k3s 버전 고정 조인). **기동 시 크레딧 모드(spot의 unlimited 지원 여부) 콘솔에서 확인·기록**
2. 역할 라벨(예: `role=obs`) + taint `obs=true:NoSchedule` - content·타 워크로드 유입 차단
3. 중앙부 4개에 nodeSelector/toleration 부여 - **주의: operator/helm 관리 리소스라 디플로이 직접 패치는 되돌려질 수 있다.** grafana-k8s-monitoring 릴리스 values 경유가 정도 (릴리스 위치 확인 필요)
4. 42-158에서 중앙부 퇴거 확인 → content 전용화
5. 같은 작업 창에 별건 처리: **40-241 swapoff + fstab 정리** (스왑 3.8GB 설정 비대칭, 재실행 10 감사에서 발견)
6. T2-B 재실행으로 델타 측정 (다른 변수 동결)

### 예측과 반증 조건

| 예측 | 수치 | 반증 시 |
|---|---|---|
| T2-B에서 42-158 파드 정상화 | p95 3.19s → 전용 노드 수준(~1s 이하) · 파드 편차 축소 | 편차 잔존 시 원인은 동거가 아니었던 것 - 분배·steal 재조사 |
| 처리량 | 회수 0.2~0.3코어 ≈ **+12~18 rps** | 무변화면 CPU 외 상한(풀 12) 재지목 |
| micro 크레딧 | 평시 0.1/0.2로 흑자 | **시험 중 스로틀(steal 급증·수집 랙·스크레이프 갭) → 플랜 B: receiver만 타 노드 분리** |
| micro 메모리 | 워킹셋 ~700Mi 유지 | 상습 초과/OOM → small spot 승격 (+$3/월) |

### 현재 상태 - 실행 완료 (08-23 16:2x, 같은 날 즉시 실행)

**이주 완료·검증 통과.** AWS CLI가 로컬에 설치돼 있어(문서의 "미설치"는 구정보) 세션에서
전 과정 실행했다. 실측 기록:

| 단계 | 실측 |
|---|---|
| 기동 | `i-06a29c2d7dbc52fda` (`yogurtte-obs-spot`) · **172.31.33.159** · 기존 스팟 설정 클론(AMI·SG·subnet 2c·persistent+stop) |
| **크레딧** | 기동 시 unlimited(계정 기본)로 붙음 → **08-23 저녁 전 노드 standard로 통일** (종량 과금 회피 - 사용자 결정). 근거: 잔고 실측 562~576/576 만땅 + obs 평시 수요 ~0.1코어 < baseline 0.2로 적립 흑자. 반증 조건: 시험 중 잔고 급락·steal 급증 시 재검토 |
| 조인 | k3s v1.34.3+k3s1 고정 조인, 31초 만에 Ready · iptables INPUT ACCEPT 확인(사건록 1번) |
| 이주 방식 | **helm 수정 없이 라벨 이전**으로 3개 이동(receiver는 DS라 자동, metrics-0·ksm은 파드 삭제로 재스케줄) - 중앙부가 이미 `yogurtte.io/role=observability` 셀렉터였다. operator만 nodeSelector가 없어 helm rev 19로 추가 |
| 42-158 | `role=content`로 강등(content 라벨은 별도 키 `yogurtte.io/content`라 무영향 확인 후 실행) - 잔류는 DaemonSet 2개뿐, **content 전용화 완료** |
| 새 노드 안착 | 중앙부 4개 전부 Running: metrics-0 210Mi·receiver 63·operator 37·ksm 20 · 노드 680Mi/914 (74%, 헤드룸 ~230Mi - 예측 700Mi 적중) |
| 수집 연속성 | 새 노드 지표 Mimir 유입 + content 앱 메트릭 최신 스크레이프 18.8s 전 - 공백 없음 |
| 40-241 스왑 | swapoff + swapfile 3.5G 삭제 + fstab 주석(백업 `fstab.bak-20260823`) - **fstab에 스왑 항목이 2줄 중복**돼 있었다 |

**미측정:** 이주 후 T2-B 델타(42-158 전용화 효과 +12~18 rps 예측·파드 편차 축소)는 다음
T2-B 실행이 검증한다. micro의 시험 중 크레딧 소모 실측도 그때 함께.

## 사건 기록 - obs micro의 크레딧 나선 (08-29~30, 8일 관측 중단 후 복구)

standard 통일(08-23)의 반증 조건("잔고 급락 시 재검토")이 실제로 발동한 기록이다.

| 시각 | 사건 |
|---|---|
| 08-23 | obs micro spot 생성(기동 크레딧 0) → 밤에 전 노드 standard 통일 (잔고 5.4) |
| ~08-29 | 일주일 내내 잔고 한 자릿수 - **"평시 0.1코어 < baseline 0.2라 적립 흑자" 예측이 반증됨** (실수요 ≥ 0.2) |
| 08-29 20:09 | 잔고 소진 → 0.2코어 스로틀 나선(밀린 일이 적립분을 즉시 소진) → kubelet 응답 중단 → **NotReady** |
| 08-30 19:29 | 리부트(소프트 불응 → AWS 하드 리부트) - **unlimited 전환 없이** (사용자 결정) |
| 08-30 19:33 | Ready 복귀 · 4분 뒤 수집 재개 실측(스크레이프 4.4s ago) |

- 스팟 회수 아님(running+impaired) · 하드웨어 아님(system check ok) - 순수 크레딧 스로틀.
- 영향: 08-29 20:09~08-30 19:33 **메트릭·트레이스 수집 전면 중단** (로그는 데몬셋이라 생존).
- **재발 조건은 그대로 남아 있다**: 잔고 ~0에서 재출발 + standard + 상시 수요 ≥ baseline.
  standard를 유지하는 해소 후보: ① 트레이스 샘플링 1.0 → 0.1~0.2 (receiver 소비 축소로
  baseline 안 진입 - 단 트레이스 표본 감소 트레이드), ② t3.small spot 승격(+$3/월,
  baseline 0.4). 리부트만으로는 나선 조건이 해소되지 않음을 명시해 둔다.

### 정정 (2026-08-31) - 크레딧 나선 진단은 반증됐다, 진짜 원인은 메모리

08-31 저녁 unlimited로 전환한 뒤 **같은 증상이 재발**하면서 위 진단이 뒤집혔다.
NotReady 시점의 실측:

| 지표 | 값 | 판독 |
|---|---|---|
| 크레딧 잔고 | **96 → 96.9 (상승 중)** · 모드 unlimited | 크레딧 고갈 아님 - 반증 확정 |
| CPU 사용률 | 4~12% | CPU 굶주림 아님 |
| EC2 상태검사 | system ok · instance ok | 하드웨어 정상 |
| **EBS ReadOps** | **5분당 ~8만 = 지속 270 IOPS** | **메모리 스래싱** (스왑 없음 → 실행 페이지 축출·재적재 반복) |
| SSH | banner exchange 타임아웃 | OS 응답 불가 |

**t3.micro 1GB에 obs 스택 ~700Mi + 시스템이면 헤드룸 ~230Mi**뿐이고, 시험 트래픽의
트레이스(샘플링 1.0)가 receiver를 밀어 올리는 순간 노드 메모리가 바닥난다. 이는 이 문서
§2(micro 실물 실험)에서 이미 기록한 **"메모리 압박이 CPU 도둑이 된다"와 같은 기전**이다 -
같은 함정에 관측 노드로 다시 빠진 것이고, 그때 남긴 교훈("micro의 자리는 메모리 압박이
없는 가벼운 상주 워크로드뿐")을 관측 스택에는 적용하지 않은 것이 실수였다.

처방은 크레딧이 아니라 **용량**이다: ① t3.small(2GB) 승격 +$3/월(권장) 또는
② 트레이스 샘플링 1.0 → 0.1(비용 0, 표본 손실 트레이드). unlimited 전환(월 ~$1~2)은
이 원인에 무효이므로 small 승격 시 standard로 되돌린다.

교훈: **"잔고가 0이었다"는 상관이지 인과가 아니었다.** 스래싱으로 CPU가 iowait에 묶이면
크레딧이 회복되고(지금 96), 반대로 초기 압박 구간에선 kswapd가 CPU를 태워 잔고가 준다 -
같은 뿌리(메모리)의 두 얼굴을 나는 두 개의 원인으로 읽었다.
