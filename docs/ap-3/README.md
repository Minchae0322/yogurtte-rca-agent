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
- `chaos.sh`의 AP-3 로직은 **중복-해시태그 피드 생성으로 교체 완료**(`measure_AP_3`/`inject_AP_3`/`feed_create` — 업로드→카테고리→피드생성). 서버 `~/chaos`는 `toy-content/docs/chaos/`의 배포본이므로 **동기화(git pull/rsync)만** 하면 `./chaos.sh AP-3 run`이 새 로직으로 돈다.
- 성립 조건: 500이면 성립·채점 진행. 200이면 dedup이 이미 추가됐거나 제약 소멸 → 불성립 종료 후 그 사실 기록(구 AP-3와 같은 취급).

## 회차 인덱스

| 회차 | 일시(UTC) | 입력 | 주입 | 결말 | RCA 조사 | 문서 |
|---|---|---|---|---|---|---|
| 1 | 07-28 01:25:04 | traceId 직접 | `hashtags:["coffee","COFFEE"]` 피드 생성 1건 | HTTP 500 (`Duplicate entry '148-173'`), 앞뒤 정상 피드 생성 200 — 부분 장애 | O | [round-1.md](round-1.md) |
| 2 (실행1) | 07-29 00:56:32 | **자연어** | 동일 | HTTP 500 (`Duplicate entry '151-174'`), 앞뒤 정상 피드 생성 200 — 부분 장애 | O | [round-2.md](round-2.md) |
| 2 (**실행2**) | 07-29 09:10:23 | **자연어**(문안 동일) | 동일 | HTTP 500 (`Duplicate entry '154-175'`), 앞뒤 정상 피드 생성 200 — 부분 장애 | O | [round-2b.md](round-2b.md) |
| **3** | 08-03 13:30:45 (조사) | **자연어**(문안 동일) + **창 명시** | **재주입 없음** — 회차 2 주입(07-29 00:56:32Z) 데이터 재조회 | 동일 (`Duplicate entry '151-174'`) | O | [round-3.md](round-3.md) |

> **회차 1 결과 (2026-07-28)**: 문항 **성립**(500 재현). §8 채점 **100/100** — 최초의 만점이자
> **앵커 부적합 0의 첫 회차**다. 상세는 [round-1.md](round-1.md) · [채점 대장](../scoring/README.md#ap-3-회차-1--100--100).
>
> **회차 2 결과 (2026-07-29)**: 문항 **재성립**. 입력이 **자연어 한 줄**로 바뀌었고 탐색이
> traceId를 **자력 선정**해 주입 트레이스와 일치시켰다. 루브릭 **v3 6항목**·앵커 **anchors-v2.md**로
> **100/100**(채점자 판정 1건 — 영향 판정, 반대 판정 시 95). 상세는 [round-2.md](round-2.md).
>
> **회차 2 실행 2 결과 (2026-07-29)**: 문항 **재성립**. 같은 문안·같은 앵커·같은 도구로 다시 돌려
> **95/100** — **§8.1 N=2 성립(평균 97.5 ± 최대편차 2.5)**. 다섯 항목은 편차 0이고
> **4) 영향 하나만 10 ↔ 5로 갈렸는데, 두 실행 다 그 항목에서 채점자 판정을 강요당했다.**
> 계획에 없던 소득 하나 — **AP-1 주입이 같은 창에 섞여 들어와 쌍 변별이 처음으로 실측됐고**
> 에이전트가 URI·예외 지문으로 갈랐다. 상세는 [round-2b.md](round-2b.md).
>
> **회차 3 결과 (2026-08-03)**: **주입 없이 회차 2 데이터를 자연어로 재조회**해 **95/100**.
> 자(앵커 `add5aff`)가 회차 2와 **같으므로 도구 델타로 읽을 수 있는 첫 AP-3 회차**다
> (AP-1 회차 3은 앵커도 함께 바뀌어 막혔다). **여섯 항목이 실행 2와 완전히 일치**했고 —
> 도구가 4종 늘었는데 점수가 그대로인 것은 **AP-3가 이미 천장에 닿아 있기 때문**이다.
> 도구가 바꾼 것은 비용이다: **분석 컨텍스트 −39.9%(199,241 → 119,761자) · 총 비용 −31.8%**.
> 새 관측 2건 — 분석이 **`ExpGrantService` 스택 오독 함정을 자력 회피**했고,
> **결함 ⑥(탐색 이유 미전달)이 처음으로 감점 경로에 닿았다**(4) 영향 5점의 첫째 근거).
> ⚠️ 창을 명시 지정했으므로 **T(시간 표현 파싱)는 이 회차에서 미시험**이고 N=1이라 인용 보류다.
> 상세는 [round-3.md](round-3.md).
>
> ⚠️ **두 회차의 100을 같은 숫자로 읽지 않는다.** 자가 다르다 — 회차 1은 v1 4항목, 회차 2는
> v3 6항목(탐색 15 · 영향 10이 **새로 있다**). 총점을 나란히 두고 "변화 없음"이라고 말하면 오독이다.
>
> ✅ **회차 1의 SoT 불일치는 해소됐다.** 중복-해시태그 교체본이 `add5aff`
> (2026-07-28T16:09:54Z)로 toy-content에 커밋됐고, 회차 2 채점은 그 **[anchors-v2.md](../../../toy-content/docs/chaos/anchors-v2.md)**
> 를 SoT로 썼다(채록 **8시간 47분 전** — 선박제 충족).

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
