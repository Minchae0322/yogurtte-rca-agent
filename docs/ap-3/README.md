# AP-3 — 중복 해시태그: dedup 부재 → uk_feed_hashtag 유니크 위반 → 500 (회차별 기록)

toy-content `docs/chaos/` RUNBOOK의 AP-3 문항. **인프라 무접촉** — 주입은 정규화 후 중복되는
해시태그(`["coffee","COFFEE"]`)를 담은 피드 생성 실요청 1건이고, 실패 INSERT는 롤백되므로
원복이 없다. AP-1(길이 초과)과 **같은 API·같은 `DataIntegrityViolationException`을 예외 원문으로
가르는 짝**이다. 폴더 구성 방식은 [`../ap-1/README.md`](../ap-1/README.md)와 동일.

> **문항 교체 (2026-07-28)**: 이 자리의 구(舊) AP-3(4바이트 이모지 → charset 불일치)은
> 2026-07-27 실행에서 이모지 댓글이 **HTTP 200**으로 통과해 **불성립**으로 판정됐다 —
> 테이블 charset이 utf8mb4라 이모지가 정상 저장됨(주입만으로 재현 불가한 degenerate 문항).
> Tempo 실측: 14:59~15:00 댓글 POST 3건(baseline-ascii / emoji / symptom-ascii) 전부 200,
> insert 깨끗, 에러 트레이스 0건 · Loki charset 로그 0건. AP-1의 짝 역할을 **실제 재현되는**
> 중복-해시태그 유니크 위반으로 대체했다. 구 이모지 실행 기록은 서버
> `~/chaos/scenarios/AP-3/`(불성립)에 보존.

## 실제 원인 (정답지 — 채점자용, 에이전트 블라인드)

`FeedService.findOrCreateHashtag`(`FeedService.java:353`)가 `name.trim().toLowerCase()`로 정규화해
`coffee`·`COFFEE`가 **같은 Hashtag 엔티티**를 반환한다. `createFeed`(`FeedService.java:213-218`)는
리스트 원소마다 `FeedHashtag.create(feed, hashtag)`를 만들어 **dedup 없이** `feed.getHashtags()`
(`Feed.java:165`, cascade=ALL)에 넣고, 커밋 시 같은 `(feed_id, hashtag_id)` 두 행이
`uk_feed_hashtag`(`FeedHashtag.java:30-32`)를 위반 → `DataIntegrityViolationException`.
`GlobalExceptionHandler`가 이를 매핑하지 않아 `handleAllException`으로 떨어져 **409 아닌 500**.
DB가 아니라 **앱 dedup 구멍**이 원인. (코드 4곳 직접 확인, 2026-07-28)

## 주입 스펙

`rca-agent/scripts/api-write-flow.sh`의 업로드→피드 생성 순서와 동일하고, `hashtags`만 충돌쌍으로 바꾼다:

```
POST /api/content/feeds   (Authorization: Bearer <TOKEN>)
{ "userId":1, "subCategoryId":<SUB_ID>, "productNameCustom":"chaos-AP3",
  "review":"chaos-AP3 중복 해시태그", "buyPlace":"chaos", "evaluation":"GOOD",
  "thumbnailAttachmentInfo":{"fileId":<FILE_ID>,"storedPath":"<STORED>","originName":"chaos.png"},
  "attachmentFileInfos":[{"fileId":<FILE_ID>,"storedPath":"<STORED>","originName":"chaos.png"}],
  "hashtags":["coffee","COFFEE"] }
→ 기대: HTTP 500, INSERT span error = "Duplicate entry '<feedId>-<hashtagId>' for key 'tb_feed_hashtags.uk_feed_hashtag'"
```

- 주입은 라이브 게이트웨이(yogurtte.com)에 요청 = 외부 행위 + 토큰 필요라 **서버 chaos 하네스로 실행**한다(AP-3 이전 회차들과 동일).
- 서버 `~/chaos/scripts`의 AP-3 주입 로직은 아직 구(舊) 이모지 댓글을 보낸다 — **RUNBOOK §6 AP-3(교체본) 기준으로 업데이트 후** 실행해야 한다.
- 성립 조건: 500이면 성립·채점 진행. 200이면 dedup이 이미 추가됐거나 제약 소멸 → 불성립 종료 후 그 사실 기록(구 AP-3와 같은 취급).

## 회차 인덱스

| 회차 | 일시(UTC) | 주입 | 결말 | RCA 조사 | 문서 |
|---|---|---|---|---|---|
| — | (대기) | `hashtags:["coffee","COFFEE"]` 피드 생성 1건 | (주입 대기) | 대기 | — |

> **현재 상태 (2026-07-28)**: 앵커 동결 완료(toy-content `scenarios/AP-3/answer.md` v1),
> RUNBOOK §6 교체 완료. **주입 대기** — 서버 하네스 AP-3 로직을 교체본으로 갱신하고 실행하면
> traceId 확보 → rca-agent 조사 → §8 채점 순으로 진행한다.

## 채점 앵커 요약 (동결본 — 전문은 toy-content `scenarios/AP-3/answer.md`)

| 항목 | 배점 | 만점 조건 (요약) |
|---|---|---|
| 근본 원인 | 40 | 앱 dedup 구멍 → 같은 (feed,hashtag) 두 행 → uk_feed_hashtag 유니크 위반 + 앱/DB 책임 분리 |
| 근거 경로 | 30 | INSERT span error의 `Duplicate entry ... uk_feed_hashtag` 지문으로 길이(AP-1)와 구별 |
| 오귀인 없음 | 20 | 길이·charset·인프라 오진 안 함 + DB는 제약을 정확히 지켰다는 인식 |
| 조치 타당성 | 10 | 리스트 dedup + DataIntegrityViolation→409 매핑 |

**오답 명시**: "길이 초과"(AP-1) / "charset" / "DB·인프라 장애" / "동시성·재시도 중복 요청"으로
귀인하면 근본 원인 0점 또는 부분점(하). 단일 트레이스 내부에서 중복이 만들어짐을 읽어야 한다.

## 스크린샷 공통 팁

- Grafana 시간대 KST. 주입 = 1건 즉발(창 없음), 실패 INSERT는 롤백.
- 이 문항의 그림: ① Tempo 에러 트레이스 — 피드 생성 워터폴에서 INSERT `query` span error 태그에
  `Duplicate entry ... uk_feed_hashtag` 자백 + 앞단 조회/insert는 정상, ② (셀렉터 수정 후) Loki
  `Duplicate entry` / `DataIntegrityViolationException` 로그.
