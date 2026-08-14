# 부하테스트 (k6)

toy-user(auth 8081) · toy-content(content 8082) · toy-chat(chat 8084) — 셋 다 context-path `/api`, 액추에이터는 8090 별도.
엔드포인트·인증 규약·부하 지점은 전부 실제 코드에서 확인해 맞췄다.

목적은 "터지는지 보기"가 아니라 **무엇이 먼저 깨지는지를 하나씩 분리해서 재는 것**이다.
그래서 스모크 → 기준선 → 시나리오 → 극한 순서로 올라가고, 극한 시나리오에는 가능한 한 대조군을 붙였다.

## 사다리

| 단계 | 파일 | 무엇을 재나 | 예상 병목 |
|---|---|---|---|
| T0 스모크 | `smoke.js` | 경로가 살아있나 (1 VU 1회) | - |
| T1 기준선 | `baseline.js` | 경로별 정상 부하 10분 → 경로별 SLO | - |
| **T1.5 사용자 여정** | **`journey.js`** | **평소 사용자가 쓰는 그대로 (세션 단위·think time)** | **없어야 정상** |
| T2-A | `a-login-burst.js` | 로그인 폭주 300 VU | BCrypt CPU → 톰캣 스레드 |
| T2-B | `b-content-spike.js` | 콘텐츠 조회 500 VU | 커서 페이징 쿼리 · N+1 재발 |
| T2-C | `c-chat-ws.js` | WS 동시 200 세션 + 메시지 | Mongo 저장 · chat→auth 조회 |
| T3-D | `d-hotkey-reaction.js` | 같은 피드 1건에 리액션 집중 | **비관적 락 직렬화 → HikariCP** |
| T3-E | `e-guest-swipe-flood.js` | 무인증 쓰기 폭주 | 같은 행 update 경합 · deadlock |
| T3-F | `f-cascade.js` | auth 포화가 content로 번지나 | 3초 타임아웃 · 폴백 작동 여부 |
| T3-G/H/I | `stress.js` | breakpoint · spike · soak | 한계 RPS · 회복 시간 · 누수 |
| T3-J | `j-ws-storm.js` | 연결 수 자체가 한계인 지점 | 세션 레지스트리 · FD · presence 누수 |
| T3-K | `k-upload.js` | 요청 크기로 죽는 경로 | 업로드 중 스레드 점유 · 디스크 |

```
loadtest/
  lib/common.js   URL·계정 풀·로그인·시드 조회 헬퍼
  lib/stomp.js    STOMP 프레임 (WS 시나리오 공용)
  seed-users.js   계정 생성 (선행)
```

## 0. 선행 - 계정 시드

로그인은 실재 계정이 있어야 통과한다. 회원가입(`/user/auth/signup`)은 이메일 인증코드가 필요하므로 쓰지 않고,
인증 없이 열려 있는 `POST /api/user`로 만든다.

```bash
k6 run loadtest/seed-users.js -e USER_COUNT=300
k6 run loadtest/smoke.js          # 여기서 깨지면 아래는 볼 필요 없다
```

`load1@test.com ~ loadN@test.com` / 비밀번호 `test1234!` (`-e PASSWORD=`로 변경).
피드·배틀 데이터는 시드 스크립트가 없다. B·D·E 는 **기존 데이터가 있어야** 돌아가고, 없으면 setup 에서 멈춘다.

## 0.5. 평상시 부하 - 여기가 기본이다

극한 시나리오(T3)는 "언제 깨지나"를 보는 것이고, **평소에 멀쩡한지는 T1·T1.5에서 본다.**
둘 다 정상 부하지만 모델이 다르다.

| | `baseline.js` | `journey.js` |
|---|---|---|
| 단위 | 경로 (읽기·로그인·채팅을 따로) | **세션** (반복 1회 = 사용자 1명의 방문 전체) |
| 부하 모델 | VU 고정 | **도착률** (초당 N명이 새로 진입) |
| think time | 1~3초 고정 | **2~10초 랜덤** (화면 읽는 시간) |
| 쓰는 목적 | 경로별 SLO 기준선 | 실제 사용 패턴에서의 체감 성능 |

```bash
k6 run loadtest/journey.js              # 초당 2세션 (~100 동시 사용자)
k6 run loadtest/journey.js -e RATE=6    # 성수기 가정
```

여정: 앱 진입(10%만 실제 로그인) → 피드 무한스크롤 2~3장 → 상세 열람 → 일부만 리액션 →
배틀 목록 → 소수만 채팅방 접속. 사용자 구성은 **읽기만 70% · 반응 20% · 채팅 10%** 로 두었다(`MIX`).

**think time 이 왜 중요한가** - 빼면 VU 수와 실제 동시 사용자 수가 완전히 다른 값이 된다.
think time 없는 50 VU 는 사람 50명이 아니라 수백 명분의 요청을 만든다. 그 상태로 "50명에서 느려진다"고
말하면 용량 산정이 통째로 틀어진다. 그래서 여기서만 `iteration_duration` 을 임계값에 넣었다 —
세션 하나가 끝나는 시간이 늘어나는지가 사용자 체감에 가장 가깝다.

## 1. 원안(A·B·C)에서 바뀐 것과 이유

| 원안 | 실제 | 근거 |
|---|---|---|
| `POST /api/auth/login` | **`POST /api/login`** | `AuthController` 는 `@PostMapping("/login")`, context-path `/api` |
| `{email, password}` | 값은 그대로, **username 으로 조회됨** | `CustomUserDetailsService` → `findByUsernameAndProvider(username, COMMON)`. 시드에서 `username == email` 로 맞춰 해소 |
| `wss://<INGRESS>/ws` | **`/api/ws/websocket`** | `WebSocketConfig` 가 `/ws` + `.withSockJS()`. SockJS 의 raw WS transport 는 `/websocket` 서브패스 |
| `socket.send({type:'chat'})` | **STOMP 프레임** | `@MessageMapping("/chat/send")`, app prefix `/app`. 임의 JSON 은 브로커가 버린다 |
| WS 인증 없음 | **CONNECT 에 `Authorization: Bearer` + `X-Device-Id` 필수** | `StompConnectHandler` — 없으면 `Connection refused` |

스테이지·임계값 등 나머지는 원안 그대로 유지했다.

## 2. 시나리오별 노트

### A. 로그인 폭주 (auth)

```bash
k6 run loadtest/a-login-burst.js
```

요청 1건마다 **BCrypt 검증 + JWT 서명 + refresh_token upsert**. BCrypt 는 의도적으로 CPU 를 태우므로
300 VU 에서 p99<500ms 는 깨지는 것이 기본값이다. **톰캣 스레드 포화와 히카리 대기 중 무엇이 먼저인지**가 관측 목표다.

### B. 콘텐츠 조회 스파이크 (content)

```bash
k6 run loadtest/b-content-spike.js
```

`/feeds/scroll`(과거 N+1 실측 지점 — 피드 11건에 쿼리 23회, `default_batch_fetch_size` 주석·NF-11) ·
`/feeds/hot`(정렬) · `/feeds/{id}`(기준선) 세 경로를 섞는다.
토큰은 `setup()`에서 1회만 받는다 — VU 마다 로그인하면 auth 의 BCrypt 비용이 content 측정에 섞인다.

### C. 채팅 동시접속 (chat)

```bash
k6 run loadtest/c-chat-ws.js -e VUS=200
```

`CONNECT` → `SUBSCRIBE /topic/chatroom/{roomId}` → 3초마다 `SEND /app/chat/send`.
메시지 1건이 Mongo 저장 + `DirectChatMessageSender` 의 auth 호출(캐시 miss 시)을 유발한다.

- `-e CHAT_ROOM_ID=<roomId>` (기본 `loadtest-room`) — `sendMessage` 는 참여자 검증을 하지 않아 임의 문자열도 통한다
- `-e CHAT_ROOM_ENTER=1` — `Chat-Room-Id` 헤더로 입장·읽음처리 경로까지. **실재하는 방일 때만**
- `-e TOKEN_POOL=50` — 로그인 비용을 측정에서 뺀다

### D. 핫키 락 경합 (content) — 대조군 필수

```bash
k6 run loadtest/d-hotkey-reaction.js              # 실험군: 한 피드에 집중
k6 run loadtest/d-hotkey-reaction.js -e SPREAD=1  # 대조군: 여러 피드로 분산
```

`FeedReactionService.toggleReaction` 이 `findByIdWithPessimisticLock` 으로 **feed 행을 잠근다.**
같은 feedId 에 몰리면 DB 행 하나에서 직렬화돼 VU 를 올려도 처리량이 안 늘고 커넥션이 락 대기로 묶인다.
같은 VU·같은 시간에 두 모드의 처리량/p99 가 갈리면 원인은 부하량이 아니라 락이다.

볼 것: `hikaricp_connections_pending` · `hikaricp_connections_acquire_seconds` · lock wait timeout 로그.

### E. 게스트 스와이프 폭주 (content)

```bash
k6 run loadtest/e-guest-swipe-flood.js                  # 고정 gid: 멱등 덮어쓰기(update)
k6 run loadtest/e-guest-swipe-flood.js -e NEW_GUEST=1   # 매번 새 gid: insert 폭증
```

`POST /battles/{id}/swipe` 는 **로그인 없이 통한다**(비로그인은 `gid` 쿠키로 식별).
인증 게이트를 안 거치고 쓰기 경로를 그대로 때리며, 요청 1건이 swipe insert + BattleItem 카운터 + Battle 카운터를 건드린다.
D 가 명시적 락 경합이라면 이쪽은 **같은 행 update 경합**이다.

`NEW_GUEST=1` 은 데이터가 계속 쌓인다 — 회차 간 조건이 달라지므로 쓰고 나면 기록하거나 정리할 것.

### F. 서비스 간 캐스케이드 (auth → content)

```bash
k6 run loadtest/f-cascade.js
```

auth 를 먼저 포화시키고 30초 뒤 content 읽기를 얹는다. content 는 작성자 정보를 auth 에서 가져오는데
(`ExternalUserApiClient`, **타임아웃 3초**), 실패하면 예외 대신 익명 폴백 + `user.fallback` 카운터를 올린다.

인과가 성립하려면 셋이 같이 보여야 한다 — ① auth p99 상승 ② content→auth 호출이 3초 근처 ③ `user_fallback_total` 증가.
③ 없이 ①②만 오르면 폴백이 안 걸린 것이고, ③만 오르면 auth 가 아니라 캐시/네트워크 문제다.
content 쪽 임계값(p99<3000, 실패율<1%)이 깨지면 **격리가 안 된 것**이다.

### G/H/I. 한계 탐색 (`stress.js`)

```bash
k6 run loadtest/stress.js -e PROFILE=breakpoint -e TARGET=feeds-scroll -e RPS_MAX=2000
k6 run loadtest/stress.js -e PROFILE=spike      -e TARGET=login
k6 run loadtest/stress.js -e PROFILE=soak       -e TARGET=feeds-scroll -e DURATION=1h
```

모양만 다르고 대상은 같아야 비교가 된다. `TARGET`: `feeds-scroll`(기본) · `feed-detail` · `hot` · `login`.

- **breakpoint** — RPS 계단 상승, 임계값 깨지면 `abortOnFail` 로 스스로 중단 → 중단 시점 RPS 가 한계 처리량
- **spike** — 10초 만에 극대 → 급하강 후 3분 관측. 임계값 복귀까지 걸린 시간이 회복 시간
- **soak** — 저부하 1시간. `jvm_memory_used_bytes` 기울기와 `hikaricp_connections_active` 우상향 여부. 짧은 테스트로는 안 보인다

### J. WS 연결 폭주 (chat)

```bash
k6 run loadtest/j-ws-storm.js -e VUS=1000                # 연결 유지
k6 run loadtest/j-ws-storm.js -e VUS=500 -e RECONNECT=1  # 재접속 폭풍
```

C 가 메시지 처리 부하라면 이건 **연결 유지 부하**다. 메시지를 하나도 안 보내도
STOMP 세션 + `LocalSessionRegistry` 엔트리 + Redis presence 키 + 소켓 FD 가 쌓인다.
`RECONNECT=1` 은 서버 재시작 직후 클라이언트가 일제히 재접속하는 상황과 같은 모양이다.

마지막 1분(일제 해제)에서 **정리 누락**을 본다 — DISCONNECT 후에도 남는 세션/Redis 키가 있으면 누수다.
k6 쪽 `ulimit -n` 에 먼저 걸릴 수 있으니 확인하고 돌릴 것.

### K. 대용량 업로드 (content)

```bash
k6 run loadtest/k-upload.js -e SIZE_MB=10 -e VUS=20
```

`max-file-size` / `max-request-size` 가 **1GB** 로 열려 있다. 동시에 몇 개만 들어와도 톰캣 스레드가
업로드 시간 내내 점유되고 임시 파일이 디스크를 먹는다. 요청 수 기준 부하로는 안 잡히는 종류의 한계다.
k6 메모리 = `SIZE_MB × VUS` 이니 올리기 전에 계산할 것. **운영 환경에서는 돌리지 않는다.**

## 3. 실행 규칙

- **한 번에 하나만.** 동시에 돌리면 세 서비스 메트릭이 섞여 어느 쪽 포화인지 못 가른다.
  (F 만 예외 — 캐스케이드를 보는 것이 목적이라 의도적으로 겹친다.)
- 회차 사이 2~3분 여유. Loki/Mimir 에서 구간이 분리돼야 나중에 트레이스를 뽑을 수 있다.
- **쓰기 시나리오(D·E·K) 뒤에는 조건이 달라진다.** 테이블이 커진 상태로 읽기 시나리오를 재면 기준선과 비교가 안 된다.
  순서는 읽기 먼저, 쓰기 나중.
- 회차마다 남길 것: 프로파일·VU/RPS·시간 · k6 요약(p95/p99/RPS/실패율) · 서버측 메트릭 캡처 · **깨진 지점의 traceId**.

## 4. 실행 전 확인

- `CHAT_ROOM_ENTER=1` 로 실재 방을 쓰려면 방 생성이 필요한데, `ChatRoomController.createChatRoom` 이
  `@AuthenticationPrincipal Long userId` 로 받는다. 실제 principal 은 `CustomUserDetails` 라 **바인딩이 안 돼 null 이 된다**
  (`@NotNull creatorId`). 방 생성은 Mongo 에 직접 넣거나 이 결함을 먼저 고쳐야 한다. 기본값은 이 경로를 끄고 돈다.
- chat 의 `APP_KAFKA_ENABLED` 기본 true. 브로커 없이 띄우면 C 의 저장 경로가 달라진다 — 어느 쪽으로 쟀는지 회차에 기록.
- 이메일 발송(`/user/auth/email/send-code`)과 FCM 발송 경로는 **일부러 뺐다.** 외부 SMTP/푸시로 실제 발송이 나가고
  레이트리밋에 걸린다. 부하로 재려면 목(mock) 을 먼저 붙일 것.
- A 는 `refresh_token` 을 계속 쓴다. 부하 후 테이블이 커진 상태로 B/C 를 재면 조건이 달라진다.
