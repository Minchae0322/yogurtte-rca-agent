# NF-12. 파일 업로드 경로에 Windows 구분자 `"\\"` 하드코딩 — Linux에서 전 업로드 500

- 심각도: **중간** (실재 기능 결함이나 **비대표 레거시 경로** — 아래 "아키텍처 맥락" 참조)
- 상태: **확정 · 미수정(수정 되돌림)** — 실서비스의 파일 업로드는 toy-auth의 **presigned URL + S3
  직접** 방식이고, 이 `content` 로컬 디스크 업로드(`POST /content/attachment-file/upload`)는
  **프로덕션 비대표 경로**다. 결함 자체는 실재하나 우선순위가 낮아 즉시 수정 대상에서 뺐다
  (한 번 적용했다가 되돌림). 구 AP-2(대용량 업로드) 게이트에서 처음 실증됐으나,
  **AP-2는 팔로우 목록 NPE로 재설계**되어 이 결함과 분리됐다 — 독립 finding으로 남긴다.
- 근거: 코드 위치(file:line) + 재현된 상태코드(HTTP 500) — 실측 2종
- 발견 경로: **구 AP-2 게이트** (2026-07-27 14:51:22Z, `./chaos.sh AP-2 run` — 당시 대용량 업로드 문항).
  `① 정상 측정(게이트)`의 1KB 업로드가 HTTP 500 → §3.3에 따라 주입 금지·중단. 이후 AP-2
  자체가 폐기·재설계되어 이 결함은 문항과 무관한 독립 결함으로 남는다.
- 계열: [DF-01](df-01-sweep-500-defects.md)(고정 500 결함)과 같은 "코드 자체가 항상 실패"
  계열. 단 DF-01은 읽기 API, 이건 쓰기(업로드) 경로.

## 아키텍처 맥락 — 이 경로는 비대표다

실서비스의 파일 저장은 **toy-auth**가 담당한다: `GET /files/presigned/upload`로 presigned URL을
발급하고 **클라이언트가 S3에 직접 PUT**한 뒤 `POST /files/presigned/complete`로 메타데이터·크기
검증만 서버가 처리한다(`S3PresignedUrlService`·`S3Config`, `storage.type` 기본값 S3, 버킷 `yogurtte`).
즉 대용량 바이트는 **앱을 통과하지 않는다.** 반면 이 NF-12의 `content` 로컬 업로드는 multipart
바이트를 파드 로컬 디스크에 `transferTo`하는 **별개·구식 경로**로, S3 의존성이 전혀 없다.
그래서 이 결함은 "실재하는 버그"이되 "프로덕션 업로드 경로의 버그"는 아니다. 인접 설계 이슈:
로컬 디스크 저장은 파드/노드 로컬 임시 스토리지라 리플리카·재기동 간 파일이 보이지 않는다.

## 무엇이 문제인가

업로드 저장 경로를 문자열 연결로 만들면서 **경로 구분자를 Windows 전용 `"\\"`로 하드코딩**
했다. Linux 배포 호스트(카오스 서버 `ubuntu@ip-172-31-38-225`)에서는 `\`가 일반 파일명
문자라, 존재하지 않는 디렉터리 컴포넌트가 만들어져 저장이 항상 실패한다. **파일 크기와
무관하게 모든 업로드가 500**이므로 1KB도 500이다.

같은 클래스의 **다운로드 경로는 올바르게** 작성돼 있어 둘이 갈라진 것이 핵심 증거다.

## 실측 근거

**① 코드 위치** — `toy-content/src/main/java/com/example/toycontent/app/file/service/FileService.java`

| 행 | 코드 | 문제 |
|---|---|---|
| **45** | `multipartFile.transferTo(Path.of(rootPath + "\\" + uploadPath + storeFilename));` | 구분자 `"\\"` 하드코딩 + `uploadPath`와 `storeFilename` 사이 구분자 없음 — 문자열 접합으로 경로를 만듦 |
| 37 | `Path rootPath = Paths.get(System.getProperty("user.dir"));` | rootPath를 문자열로 이어붙이려고 꺼냄 |
| 64 (대조군) | `Path.of(System.getProperty("user.dir"), uploadPath, attachmentFile.getStoreFileNm())` | **다운로드는 가변인자로 OS 무관하게 올바르게 작성** — 업로드만 갈라졌다 |

`git log` 상 `6721aae fix: fix file path` 커밋이 있으나 45행은 여전히 `"\\"` — 회귀했거나
부분 수정에 그쳤다.

**② 재현된 상태코드** — 구 AP-2 게이트 (2026-07-27 14:51:22Z)

```
1KB 업로드: HTTP 500 → evidence/baseline/20260727T145122Z/upload-small.json
[GATE] 정상 업로드가 HTTP 500 — 잠복 버그(FileService 경로 구분자 하드코딩) 실증
게이트 실패 — 주입 금지(§3.3).
```

**③ 설정 실측** (`application-*.yml`)

| 프로필 | `file.upload.path` |
|---|---|
| dev/prod | `${FILE_UPLOAD_PATH:/app/uploads/}` (절대·후행 슬래시) |
| local | `uploads/` (상대) |

## 메커니즘 — 왜 항상 실패하나

`rootPath`(예: `/app`) + `"\\"` + `uploadPath` + `storeFilename`을 문자열로 이으면:

```
"/app" + "\" + "/app/uploads/" + "uuid.png"  →  "/app\/app/uploads/uuid.png"
```

`Path.of`는 `/`로만 컴포넌트를 나누므로 첫 컴포넌트가 **`app\`(리터럴 백슬래시 포함)** 인
존재하지 않는 디렉터리가 된다. `transferTo`가 부모 디렉터리를 찾지 못해
`NoSuchFileException`(→ `IOException`) → 46행에서 `RuntimeException`으로 재던짐 →
컨트롤러에 예외 매핑이 없어 **HTTP 500**. local 프로필(`uploads/`)에서도
`/app\uploads/uuid.png`로 같은 방식으로 깨진다.

**부하가 오르면?** — 이 결함은 부하와 무관하다. **첫 요청부터 100% 실패**한다.

## 개선안과 검증 가능한 예측

**수정 — 다운로드 경로와 동일하게 `Path.of` 가변인자로** (지금은 되돌린 상태로 보관)

```java
Path target = Path.of(System.getProperty("user.dir"), uploadPath, storeFilename);
try {
    Files.createDirectories(target.getParent());  // 쓰기 경로이므로 디렉터리 보장
    multipartFile.transferTo(target);
} catch (IOException e) {
    throw new RuntimeException(e);
}
```

- OS별 구분자를 JVM이 처리(`File.separator`) → Windows/Linux 모두 정상.
- `uploadPath`와 `storeFilename` 사이 구분자 누락도 함께 해소.
- 64행 다운로드와 **저장 위치가 일치**하게 되어 업로드→다운로드 왕복이 성립.

> **예측**: 수정 후 이 엔드포인트에 1KB를 올리면 Linux에서도 `HTTP 200 + fileId`가 나온다
> (지금은 크기 무관 500). **반증 조건**: 구분자를 고쳤는데도 500이면 원인은 구분자가 아니라
> 다른 곳(권한·디스크·`FILE_UPLOAD_PATH` 미설정)이다.
>
> 단, 이 경로는 비대표라 **되돌린 상태로 둔다.** 실제 수정은 이 로컬 업로드를 실서비스
> 경로(toy-auth presigned+S3)로 일원화할지/폐기할지 결정할 때 함께 다루는 편이 낫다.

**부수 관찰(별건)**: `application.yml`의 multipart 한도가 1GB(사실상 무제한)이고, 초과 시
`MaxUploadSizeExceededException`이 413이 아니라 500으로 미매핑될 소지가 있다. 구 AP-2가
겨냥하던 갈래이나 AP-2 재설계로 문항에서는 빠졌다 — 이 경로를 살릴 경우의 개선 항목으로만 남긴다.

## 왜 이걸 기록하는가

**로컬(Windows) 개발에서는 절대 안 드러나고 Linux 배포에서만 터지는 이식성 결함**이다.
코드 리뷰로도, 개발자 로컬 테스트로도 통과한다 — 실제 Linux 호스트에 요청을 쏴봐야 나온다.
카오스 하네스의 baseline 게이트가 주입 **전에** 이걸 잡아냈다는 점에서, "정상 상태를 먼저
실측하라"는 절차(STATUS ①-b 앵커 개정 원칙)가 문항 결함이 아니라 **제품 결함**까지 잡는
사례다. (비대표 경로라 우선순위는 낮지만, 게이트가 제 역할을 했다는 근거로 남긴다.)

## 참조

- 발견 계기: 구 AP-2(대용량 업로드) 게이트 — 현재 [AP-2](../../../toy-content/docs/chaos/scenarios/AP-2/answer.md)는 팔로우 목록 NPE로 재설계됨
- 대조 코드: 같은 클래스 `downloadFileAsBytes`(64행)가 올바른 작성의 레퍼런스
- 정식 업로드 설계: toy-auth `S3PresignedUrlService`(presigned URL + S3 직접)
- 계열: [DF-01](df-01-sweep-500-defects.md)(항상 실패하는 고정 500 결함)
