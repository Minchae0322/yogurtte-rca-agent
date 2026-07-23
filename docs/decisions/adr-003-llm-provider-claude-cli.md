# ADR-003. LLM provider는 claude-cli(구독)를 기본으로 한다

- 날짜: 2026-07-22
- 상태: 채택
- 관련: `llm/` 패키지, Dockerfile, docker-compose.yml

## 배경

LLM 호출 방식은 세 가지가 가능하다: Anthropic API, OpenAI API, 로컬 `claude` CLI
(Claude 구독 계정). API는 토큰 과금이고, 이 프로젝트의 개선 방법론(전략 문서)은
**같은 시나리오를 반복 실행해 평균을 내는 것**이라 호출 횟수가 많다. 예컨대 chaos
시나리오 7종 × 3회 × 개선 이터레이션마다 재평가 — 조사당 실측 비용이 $0.42이므로
API로는 평가 1라운드에 $9 안팎, 이터레이션이 쌓이면 수십 달러다.

## 결정

**기본 provider는 `claude-cli`로 한다.** 구독 계정의 한계비용이 0이므로 반복 실험
비용이 사라진다. 단 provider는 `@ConditionalOnProperty`로 교체 가능하게 유지한다
(`RCA_LLM_PROVIDER=anthropic|openai|claude-cli`) — 서버/운영 환경에서 API로 전환하는
길을 막지 않는다.

## 트레이드오프 (실측 기반)

- **프로세스 비용**: CLI는 조사마다 별도 Node 프로세스를 띄운다. rca-agent 자체는
  RSS ~200MB지만 CLI 프로세스가 수백 MB를 추가로 쓴다. 로컬/단일 서버에선 무방하나
  밀도 높은 배포에선 API가 낫다.
- **usage 회계의 함정**: CLI의 usage 포맷은 API와 달라 파싱을 잘못하면 측정이
  통째로 틀어진다 (ADR-006에서 실제로 발생: in=2로 기록되던 버그).
- **지연**: 실측 LLM 구간 75~81s. API 직접 호출 대비 CLI 기동 오버헤드가 있으나
  전체 지연의 지배 요인은 모델 추론 시간이라 유의미한 차이는 아니다.

## 컨테이너/서버에서의 인증 (부속 결정)

macOS 로컬은 Keychain의 로그인 세션을 쓰지만, **자격증명이 Keychain
("Claude Code-credentials" 항목)에만 있고 `~/.claude`에 파일로 존재하지 않음을
실측으로 확인**했다. 즉 `~/.claude` 볼륨 마운트로는 리눅스 컨테이너에 인증이 전달되지
않는다. 컨테이너 안에서 로그인해 두는 방식도 기각했다 — 로그인은 브라우저 OAuth라
헤드리스에서 불가하고, 컨테이너 재생성 시 증발한다.

채택한 방식: 호스트에서 `claude setup-token`으로 발급한 장기 토큰을
`CLAUDE_CODE_OAUTH_TOKEN` env로 주입한다. Dockerfile에 Node 20 + claude CLI를
설치해 컨테이너에서도 구독으로 동작하며, API만 쓸 배포에서는 해당 RUN 블록을 제거해
이미지를 가볍게 할 수 있다(Dockerfile 주석 참조). provider·notifier 선택은 compose가
강제하지 않고 전부 `.env`를 따른다.
