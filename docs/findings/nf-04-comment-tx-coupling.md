# NF-04. 댓글 트랜잭션에 경험치·비관적 락이 결합, 미계측 갭 85ms

- 심각도: **중간** | 상태: 확정 (트레이스 + 코드)
- 위치: toy-content `FeedCommentService.createComment`

## 관측 (트레이스 `6a5dc9c1990469248cfea377e1d7b4a0`, `POST /feeds/{feedId}/comments` 129.8ms)

- `connection` span 116.6ms 동안 JDBC 쿼리 span 합계는 **~30ms** (query 9회, 각 2~5ms)
  — 나머지 **~85ms가 트랜잭션 안의 미계측 구간** (앱 로직·락 대기 추정).
- 쿼리 중 `FOR UPDATE`(비관적 락) 구간 ~44ms 관측.
- 댓글 "한 건" 저장에 SELECT/INSERT/UPDATE 합계 9회.

읽기 면에서도 같은 흐름이 보인다: API 순회에서
`GET /feeds/{id}/comments`가 **0.58s로 읽기 API 중 최다 지연**이었다.

## 코드 근거

- `FeedCommentService.java:51` — `@Transactional createComment(...)`가 댓글 저장과
  `expGrantService.grantCommentCreate(creatorId, ...)` (88행, 경험치 부여)를 **한
  트랜잭션에** 묶는다.
- `FeedRepository.java:22`, `UserRewardRepository.java:16` — 둘 다
  `@Lock(PESSIMISTIC_WRITE)`. 댓글 작성이 feed 행과 user_reward 행을 모두 잠근다.

## 메커니즘

1. **hot-row 직렬화**: 인기 피드에 댓글이 몰리면 같은 feed 행의 `FOR UPDATE`를 두고
   모든 작성 요청이 줄을 선다. 같은 유저의 연속 액션은 user_reward 행에서 또 줄을 선다.
   처리량이 "가장 인기 있는 행"의 락 처리 속도로 상한된다.
2. **트랜잭션 폭 증가**: 경험치 로직(부가 기능)이 댓글 저장(핵심 기능)과 운명을 같이
   한다 — 경험치 쪽 지연·실패가 댓글 작성 지연·실패가 된다.
3. **미계측 85ms**: 락 대기인지 앱 로직인지 트레이스만으로 구분 불가. NF-02와 같은
   "관측 공백" 계열이며, 코드 인지 RCA(전략 Phase 4)가 메울 대상.

## 개선 방향

1. 경험치 부여를 트랜잭션 밖으로: 커밋 후 이벤트(`AFTER_COMMIT`) 또는 기존 Kafka
   경로로 비동기화. 댓글 트랜잭션은 댓글만.
2. 카운터류(hot-row) 갱신은 락 대신 원자적 UPDATE 또는 지연 집계 검토.
3. 계측 보강: 락 대기 시간을 span attribute로 노출해 85ms 갭을 분해.

## 개선 검증 방법

- `POST comments` p95: 트랜잭션 분리 후 129.8ms → 예측 60~70ms대 (경험치 구간 제거분).
- 동시 댓글 부하(k6, 같은 feedId)에서 처리량이 락 직렬화로 평탄해지는 현재 곡선 대비
  개선 후 선형 구간이 길어져야 한다.
- `GET comments` 0.58s는 별도 조회 최적화 대상으로 분리 추적.
