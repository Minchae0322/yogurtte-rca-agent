# AP-3 회차 1 — 중복 해시태그 1건: dedup 구멍이 500을 만든다

## 한눈 요약

| | |
|---|---|
| **실제 원인** | `findOrCreateHashtag`의 정규화(`trim().toLowerCase()`)로 `coffee`·`COFFEE`가 같은 Hashtag를 반환하는데 `createFeed`가 **dedup 없이** 두 `FeedHashtag`를 cascade insert → 같은 `(148,173)` 두 행이 `uk_feed_hashtag` 위반 → 롤백 → 500. DB가 아니라 **앱 dedup 구멍** (+미매핑으로 409 아닌 500) |
| **실제 영향** | 해당 요청만 500 — 직전 baseline 피드 생성 200, 직후 복구 요청도 200 (부분 장애). 롤백으로 데이터 부작용 없음 |
| **에이전트 파악 원인** | 1순위 "동일 피드(148)에 같은 해시태그(173)가 **두 번 저장**되어 `uk_feed_hashtag` 위반 → 전체 롤백 → 500 (**애플리케이션 레벨 데이터 결함**)". 2순위(확신도 중간)로 "**중복 제거(distinct)·멱등 삽입 부재** + `DataIntegrityViolationException`을 4xx 아닌 500으로 매핑 — 1번을 장애로 승격시킨 층위". 3순위 동시성은 **반증 3건으로 배제**. 인프라 4종 지표 인용 배제 |
| **§8 채점** | **100 / 100** — 근본 40 · 근거 30 · 오귀인 20 · 조치 10. **최초의 만점이자 앵커 부적합 0의 첫 회차**. 근본 40은 경계 판정(계층 분리 인정 — AU-4 파일럿 선례)이라 자기 일치도 검사 대상. 상세는 [채점 대장](../scoring/README.md#ap-3-회차-1--100--100) |
| **토큰·비용·시간** | in 45,885 / **out 11,070** tok · **$0.5547** · 162.9s — **8회 조사 중 out 최대**(장황본). 트레이스 23 span |
| **에이전트 보고서 전문** | [round-1-rca-report.md](round-1-rca-report.md) |

## §8 채점 근거 (항목별) — 몇 점을, 무슨 사실 때문에

앵커(동결본 — [README](README.md#채점-앵커-요약-동결본--전문은-toy-content-scenariosap-3answermd) 요약표, `536c007`로 **채록 10시간 전 커밋**)와 대조한 결과다. 판정의 authoritative 기록은
[채점 대장](../scoring/README.md#ap-3-회차-1--100--100)에 있다(숫자·근거 동일).

| 항목 | 배점 | 점수 | 무슨 사실 때문에 이 점수인가 |
|---|---|---|---|
| 근본 원인 | 40 | **40** | 앵커 만점 문언 "앱 dedup 구멍 → 같은 (feed,hashtag) 두 행 → uk_feed_hashtag 유니크 위반 + 앱/DB 책임 분리"의 **네 요소 전부 도달**. "같은 (148,173) 두 행" 명시, 유니크 위반 확정, 후보1 제목에 "**애플리케이션 레벨 데이터 결함**", 후보2에서 dedup 부재를 트레이스 관측으로 직접 논증. 오답 명시("길이"/"charset"/"DB·인프라"/"동시성")에 **하나도 귀인하지 않음** — 동시성은 후보로 세웠으나 반증 3건으로 배제. **경계 판정**: dedup 구멍이 1순위가 아닌 2순위라 AP-1 선례(후보2·확신 낮음 → 20)를 적용하면 30이나, 여기선 후보2가 **확신도 중간 + 계층 분리 명시**("1번을 장애로 승격시킨 층위")라 AU-4 파일럿 선례(직접/상위 원인 계층 분리 → 40)가 더 가깝다 |
| 근거 경로 | 30 | **30** | 앵커 만점 "INSERT span error의 `Duplicate entry ... uk_feed_hashtag` 지문으로 길이(AP-1)와 구별" — **문언 이상 충족**. 지문 원문 인용에 더해 ① 성공 insert(`generated-keys=212`)와 실패 insert를 **같은 `connection` span 하위**로 연결, ② 키 `'148-173'`의 148·173을 각각 `generated-keys` 스팬으로 역추적해 "이 요청이 방금 만든 값"임을 확정, ③ `select tb_hashtags` **row-count 0 → 1** 대조로 중복 생성 경로 재구성, ④ `rollback` 이벤트 → `status=500` 인과 종결 |
| 오귀인 | 20 | **20** | 앵커 만점 충족. 길이·charset 가설을 **세우지도 않음**. 커넥션 풀(pending 0·acquire 1.7ms)·GC(rate 0)·auth(필터 12/12 통과)·Kafka(span 부재)를 각각 지표 인용으로 배제. DB를 장애로 지목하지 않고 제약 작동을 정상으로 취급 |
| 조치 | 10 | **10** | 앵커 만점 "리스트 dedup + `DataIntegrityViolation`→409 매핑" **둘 다 명시**. 조치 3 "저장 전 **정규화 후 distinct**(대소문자·공백·`#` 제거 기준 통일)" — 실제 근본 원인인 `toLowerCase()` 정규화를 **관측만으로 처방**. 조치 5 "`DataIntegrityViolationException` 매핑 재검토 — 500이 아니라 **409/400**" |
| **합계** | **100** | **100** | 감점 없음 |

## 장애 상황

- 주입: 서버 `~/chaos`의 `./chaos.sh AP-3 run` — `hashtags:["coffee","COFFEE"]` 피드 생성 실요청 1건,
  `POST /api/feeds` → **HTTP 500** (01:25:04.375Z = KST 10:25:04). 인프라 무접촉, 원복 없음(롤백이 곧 원복)
- **정상 요청 공존 실측** — 같은 엔드포인트 3연속:

  | 시각(UTC) | traceId | 소요 | 결과 |
  |---|---|---|---|
  | 01:24:59.394 | `6a6804eb318ce5827cfe7b2ad3a3a22c` | 490ms | **200 SUCCESS** (baseline) |
  | **01:25:04.375** | **`6a6804f072fa431c691e39138e8f5e36`** | **218ms** | **500 SERVER_ERROR** (주입) |
  | 01:26:05.402 | `6a68052d3236bada84895abcec78672a` | 170ms | **200 SUCCESS** (복구 확인) |

- Loki(채점자 조회, `service_name` 셀렉터): `uk_feed_hashtag` 2건 — `SqlExceptionHelper` ERROR에
  `SQLIntegrityConstraintViolationException` → `ConstraintViolationException` →
  `DataIntegrityViolationException` 예외 체인이 **traceId와 함께** 남아 있다.
  **에이전트는 이걸 0건으로 봤다**(셀렉터 결함 — 아래 도구 관찰)

## 스크린샷용 traceId

| 용도 | traceId |
|---|---|
| **에러 트레이스** (INSERT 두 번 + Duplicate entry error 태그 + rollback) | `6a6804f072fa431c691e39138e8f5e36` |
| 대조용 정상 피드 생성 (200) | `6a6804eb318ce5827cfe7b2ad3a3a22c` |

## 실제 신호 발췌 (출제자 판독)

**Tempo — 에러 트레이스의 모양** (01:25:04.375Z, 218.29ms, 23 spans)

```
http post /feeds                      218.29ms  [status=500 outcome=SERVER_ERROR]
 └ secured request (215.35ms)
    └ connection (185.79ms) — events: acquired → rollback
       ├ select tb_hashtags where name=?     2.50ms   row-count 0   ← 미스: 신규 생성
       ├ insert tb_feed → generated-keys                feed_id 148
       ├ insert tb_feed_attachment_file ×2              keys 225,226
       ├ insert tb_hashtags → generated-keys            hashtag_id 173
       ├ insert tb_feed_hashtags → generated-keys 212   ← (148,173) 1행째 성공
       ├ update tb_hashtags set usage_count=?
       ├ select tb_hashtags where name=?     2.26ms   row-count 1   ← 히트: 방금 만든 173
       └ query 65.98ms [ERROR]                          ← (148,173) 2행째
            error = "Duplicate entry '148-173' for key 'tb_feed_hashtags.uk_feed_hashtag'"
```

판독 포인트: ① **중복을 만든 두 주체가 같은 트레이스 안에 다 있다** — 성공 insert와 실패
insert가 같은 `connection` span 하위라, 동시성·재시도 오귀인이 트레이스만으로 반증된다.
② `select` **row-count 0 → 1** 두 번이 정규화 재사용의 흔적 — 첫 조회는 미스라 173을 만들고,
둘째 조회는 **그 173을 찾아낸다**. ③ 루트 span은 `status=500`·`outcome=SERVER_ERROR`이지만
**span status는 ERROR가 아니다**(`exception=none`) — 에러 상태를 가진 span은 실패 INSERT 하나뿐이라
AP-1(루트도 ERROR)과 신호 모양이 다르다.

> 스크린샷 미첨부. Grafana 시간대 KST 기준 **10:25:04**의 `http post /feeds` 트레이스를
> 열면 위 워터폴이 그대로 보인다 (다른 회차 폴더처럼 `img.png`로 추가하면 됨).

## 앵커 노트

**이 회차는 앵커 부적합 0이다** — 만점 요건 4개가 전부 **에이전트 수집 범위 안의 trace 신호**로
구성돼 있었고, 실제로 전부 관측됐다. 07-27 신설한 [앵커 작성 체크리스트](../scoring/README.md#앵커-작성-체크리스트-신설)의
첫 통과 사례다.

다만 이 앵커는 **baseline 채록이 아니라 코드 4곳 독해**로 작성됐다(유형 C 위험). 이번엔
코드가 실제 전개와 일치해 문제가 없었지만, 절차상으로는 AU-4와 같은 경로였다는 점은
기록해 둔다 — 요건이 "예외 지문"이라는 **실행 결과 신호**였던 것이 유형 C를 피한 이유로 보인다.

**SoT 불일치 (절차 결함)**: 채점 기준으로 쓴 앵커는 이 레포 [`README.md`](README.md)의
요약표(`536c007`, 07-28 00:54 KST 커밋 — 채록 10:25 KST보다 **9시간 31분 앞섬**)다.
STATUS 활동 로그가 말하는 SoT(`toy-content/docs/chaos/scenarios/AP-3/answer.md`)는
**로컬 toy-content에 반영돼 있지 않다** — 해당 파일은 여전히 구 이모지 문항이고,
RUNBOOK §6·`chaos.sh`에도 중복 해시태그 로직이 없다(`uk_feed_hashtag` 검색 0건, 워킹트리 clean).
교체본은 서버 `~/chaos`에만 존재한다. **선박제 요건 자체는 충족**(요약표가 채록 전 커밋)이나,
SoT를 toy-content에 커밋해 두 기록을 일치시켜야 한다.

## 도구 관찰 (v0 조건 일관성)

- **Loki 0건 6회 연속 → 7회 연속.** `{app=~...}` 셀렉터 결함이 그대로 재현됐고, 에이전트는
  `totalEntriesReturned: 0`을 근거로 "정상 로그까지 0건이라 **로그 수집 파이프라인 공백**으로
  보는 편이 자연스럽다"고 스스로 진단한 뒤 조치 7로 수집 설정 점검을 제안했다.
  이번 문항은 **로그에 예외 체인 전문이 실재**했으므로(위 장애 상황), 셀렉터를 고쳤다면
  "왜 중복이 들어왔는가"에 한 발 더 갔을 가능성이 있다 — 단 **점수는 이미 만점이라
  v0.1 델타가 이 문항 점수를 올릴 자리는 없다** (AU-4와 같은 결론).
- **JDBC 바인드 파라미터 미기록을 에이전트가 관측 공백으로 지목**했다(조치 9) — 해시태그
  이름을 볼 수 없어 "같은 이름 두 번"과 "정규화로 같아진 다른 이름"을 가르지 못했다.
  실제는 후자(`coffee`/`COFFEE`)이고, 리포트 본문은 "같은 이름이 두 번 순회된 것이 확실"이라
  **단정했지만** 조치 3에서 대소문자 정규화를 처방해 결과적으로 메커니즘을 덮었다.
  앵커 문언에 정규화가 없어 감점 사유는 아니나, **rubric-v2 소항목화 시 변별 지점**이다.
- `kafka_consumer_fetch_manager_records_lag` 결측 지속(8회 연속).
- **장황본이 다시 나왔다** — out 11,070 tok으로 AU-4 파일럿(10,511)보다 길다. AU-4에서
  "간결본 75 vs 장황본 ~95"로 확인된 thoroughness 분산의 **장황본 쪽 표본**이다.
  같은 v0에서 out 2,657(IN-2)~11,070(이 회차)까지 **4.2배** 편차가 난다.
- CLI 샌드박스 격리(`rca-cli-sandbox-14208389450777104254`) 활성 상태에서 조사됨.
