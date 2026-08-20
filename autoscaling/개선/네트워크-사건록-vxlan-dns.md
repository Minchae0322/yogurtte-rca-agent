# 네트워크 사건록 - "DB 연결 실패"로 위장했던 iptables INPUT DROP (신규 노드의 함정)

> **한 줄 요약:** 나중에 추가한 워커(40-241)의 content 파드가 부팅 중 죽었다. 표면 증상은
> "DB 연결 실패"였지만, 실제로는 **노드의 iptables INPUT 기본 정책이 DROP인데 VXLAN
> 포트(UDP 8472) 허용 규칙이 없어** 크로스노드 overlay 패킷(그중 DNS 응답)이 전부 버려진
> 것이었다. `iptables -I INPUT 1 -p udp --dport 8472 -j ACCEPT` 한 줄로 해소.
>
> **이 문서가 지금 중요한 이유 (2026-08-21):** 토폴로지 재구성으로 **새 워커를 또 추가할
> 예정**인데, 이 사건의 미해결 질문이 "왜 그 노드만 INPUT DROP이었나"(AMI 의심)다.
> 같은 AMI로 만드는 새 노드가 같은 함정을 들고 나올 수 있다 - 맨 아래 체크리스트가 결론.

원문은 당시 디버깅 세션의 두 차례 정리(1차: DNS 크로스노드 유실로 특정 / 2차: iptables
INPUT DROP으로 근본 확정)이고, 여기 요지만 보존한다.

## 사건 전개 - 증상이 세 겹으로 위장돼 있었다

```
UDP 8472 INPUT DROP                     (진짜 원인 - 노드 OS 방화벽)
  → 크로스노드 overlay 패킷 차단
  → 241 파드가 CoreDNS 응답을 못 받음    (CoreDNS가 당시 45-39에 1개뿐)
  → RDS 도메인 해석 실패 (UnknownHostException)
  → Hibernate "Unable to determine Dialect"
  → "Communications link failure"        (로그 맨 위 - 사람이 처음 보는 것)
```

- 로그 맨 위의 에러는 전부 결과였고, 진짜 원인은 스택 맨 아래 `Caused by:
  UnknownHostException ... Temporary failure in name resolution`에 있었다.
- **"45-39는 되고 241만 안 되는" 이유**: 45-39 파드는 DNS를 같은 노드의 CoreDNS에서
  해결(노드 경계 안 넘음), 241 파드는 VXLAN(UDP 8472)으로 노드를 건너야 했다.
- **"tcpdump엔 응답이 오는데 앱은 못 받는" 모순**: tcpdump는 물리 NIC에서 잡는다.
  패킷은 노드까지 왔지만 INPUT 체인에서 DROP - flannel.1(디캡슐)에는 아무것도 안 보였다.
  질의 간 정확히 5초 간격(리눅스 resolver 기본 타임아웃)이 결정적 단서.
- **디버그 파드(netshoot)는 됐던 이유**: nslookup 1회는 질의 1~2개라 통과. 앱 부팅은
  수십 개 DNS 질의 폭주라 걸렸다. "가벼운 도구로 재현 안 되는 간헐 문제"의 전형 -
  이것 때문에 "네트워크는 정상"으로 오판하고 여러 번 헛다리를 짚었다.

## 배제한 가설들 (헛다리 목록 - 다음에 같은 증상이 오면 이 순서로 자르지 말 것)

| 의심 | 왜 아니었나 |
|---|---|
| RDS 보안그룹 / DB 인증 / 3306 차단 | IP 직박으로 정상 기동 - DB 축 전체 무죄 |
| 노드 자체 DNS / 파드 dnsPolicy | 노드 nslookup 정상 · 양쪽 파드 동일 설정 |
| flannel 인터페이스·라우트 / VXLAN 차단(SG) | 인터페이스·라우트 정상 · SG는 8472 허용 |
| MTU / conntrack 포화 / NIC 오프로드 / rp_filter / stale VTEP | 각각 실측으로 기각 (8900B ping 통과 · dmesg 무 · ethtool off 무효 · loose · FDB 일치) |

확정 실험 둘: ① `DB_HOST`를 IP로 직박 → 같은 노드에서 정상 기동 (원인 = DNS 확정)
② 8472 ACCEPT 삽입 → overlay 즉시 복구 (원인 = INPUT DROP 확정).

## 후속 조치 검증 (2026-08-21 실측 - 전부 완료 확인)

당시 "가장 급한 마무리"로 남겼던 항목들을 라이브로 재확인했다:

| 당시 남긴 숙제 | 2026-08-21 실측 |
|---|---|
| 8472 규칙 영구 저장 (재부팅 시 소실 → 재발 이력 있음) | **완료** - `/etc/iptables/rules.v4`에 8472 존재 · `netfilter-persistent` enabled |
| INPUT 기본 정책 | **DROP → ACCEPT로 변경돼 있음** (45-39와 동일) |
| CoreDNS 단일(45-39 1개) SPOF | **완료** - replicas 2, 241·45-39 각 1개 분산 |
| 왜 241만 INPUT DROP이었나 | **여전히 미해결** - AMI 초기 설정 의심. 아래 체크리스트의 존재 이유 |

## 신규 노드 조인 체크리스트 (토폴로지 재구성에 적용)

새 워커(특히 같은 AMI `ami-0dc44556af6f78a7b`)를 붙일 때, k3s 조인 **전에**:

1. `sudo iptables -S INPUT | head -3` - 기본 정책이 DROP이면:
   `iptables -I INPUT 1 -p udp --dport 8472 -j ACCEPT` + kubelet(10250/tcp) 등 허용 후
   **`netfilter-persistent save`까지** (메모리에만 넣으면 재부팅 재발 - 실제 전례)
2. 조인 후 크로스노드 검증은 **가볍게 하지 말 것**: nslookup 1회는 간헐 유실을 못 잡는다.
   DNS 질의 50회 연속 + 실제 앱 파드 기동(부팅 시 질의 폭주가 진짜 시험)으로 확인
3. medium(45-39) 드레인 시 그 위의 CoreDNS 1개가 재스케줄된다 -
   **드레인 후 CoreDNS 2개가 서로 다른 노드에 있는지** 확인 (한 노드에 몰리면 SPOF 재발)
4. tcpdump로 판단할 때는 물리 NIC과 `flannel.1` **양쪽을 같이** 뜬다 -
   "노드까지 왔다"와 "파드까지 갔다"는 다른 명제다

## 교훈 (RCA 관점)

- 로그 맨 위 에러는 결과다 - `Caused by` 맨 아래부터 읽는다
- "디버그 도구로는 정상"은 무죄 증거가 아니다 - 부하 패턴(폭주)까지 재현해야 한다
- 클라우드 방화벽(SG)이 열려 있어도 **노드 OS 방화벽(iptables)** 이라는 층이 하나 더 있다
- 확정은 언제나 단일 변수 실험으로 - IP 직박, 포트 하나 열기
