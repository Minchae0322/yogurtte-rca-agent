# NF-03. 알림 hot path의 Redis `KEYS` — 코드베이스에 5개소

- 심각도: **중간** (현재 저지연, 데이터 증가 시 위험) | 상태: 확정 (트레이스 + 코드)
- 위치: toy-chat connection/redis 패키지

## 관측 (트레이스 `6a5dc9c...`)

한 건의 알림 처리에서 `KEYS` 명령이 **2회** 관측됐다 (`redisKEYS 0` span, 739μs / 885μs).
호출 경로: `PushDispatcher.dispatch` → `ConnectionService.getOnlineDeviceIds` →
`ClusterPresenceStore`의 `redisTemplate.keys(DEVICE_ONLINE_PREFIX + userId + ":*")`.

## 코드 근거 — 사용처는 트레이스에 보인 것보다 많다

```
ClusterPresenceStore.java:243  keys(DEVICE_ONLINE_PREFIX + userId + ":*")   ← 알림 hot path
ClusterPresenceStore.java:259  keys(CHATROOM_ONLINE_PREFIX + "*")           ← 전역 패턴
PresenceSynchronizer.java:28   keys("room_connected:*")                     ← 주기 동기화
UserInfoCacheService.java:229  keys(searchPattern)
UserInfoCacheService.java:296  keys(CACHE_KEY_PREFIX + "*")                 ← 전역 패턴
```

트레이스는 hot path의 2회만 보여줬지만, 정적 확인 결과 **전역 `*` 패턴 3개**를 포함해
5개소다. 관측(동적)과 코드(정적)를 겹쳐야 전체 그림이 나온 사례.

## 메커니즘

`KEYS`는 keyspace 전체를 스캔하는 O(N) **블로킹** 명령이다. Redis는 단일 스레드이므로
키가 수십만 개가 되면 이 명령 하나가 모든 요청(채팅 presence, 캐시)을 수 밀리초~수십
밀리초 멈춰 세운다. 특히 알림 발송마다 실행되는 hot path의 KEYS는 알림 트래픽 × 키
규모의 곱으로 악화된다. 지금 0.9ms인 것은 키가 적어서일 뿐이다.

## 개선 방향

1. hot path(디바이스 조회): 패턴 스캔 대신 **역인덱스 자료구조** — 사용자별 디바이스를
   `SET user_devices:{userId}`로 유지하고 `SMEMBERS`로 조회 (O(멤버 수)).
2. 주기 동기화·캐시 정리 등 비-hot path: `SCAN` 커서로 대체.

## 개선 검증 방법

- 트레이스에서 `KEYS` span 소멸, `SMEMBERS`로 대체 확인.
- redis_exporter의 명령 통계에서 `keys` 콜 수 0 수렴.
- 키 10만 개 규모를 만들어 두 방식의 조회 지연 비교 (현재 방식은 키 수에 비례해
  증가해야 하고, SET 방식은 평탄해야 한다).
