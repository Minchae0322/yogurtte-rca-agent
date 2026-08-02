package com.yogurtte.rca.triage;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * B-26 투입 게이트 — 박제 문안 12개의 창이 파서 재작성 전후로 바뀌지 않음을 증명한다.
 *
 * <p>기대값은 회차 2에서 <b>실제 사용된 파서가 실제로 만든 창</b>이다 — `reports/*.json`의
 * {@code triage.timeExpression}이 전부 {@code 상대 표현 '최근 N시간'} 임을 실측 확인했다
 * (2026-08-02, 자연어 조사 8건). 문안 원문은 toy-content {@code chaos/anchors-v2.md} §질문 문안,
 * {@code <시각>} 자리는 회차 2의 채움 관례({@code 최근 N시간 안에/부터})를 따랐다.
 *
 * <p><b>여기 하나라도 깨지면 B-26을 그 회차에 넣으면 안 된다</b> — 창이 바뀌면 회차 2와의
 * 비교가 성립하지 않는다(round-3 README §B-26 게이트). expression 문자열까지 고정하는 이유:
 * 이 문자열은 탐색 LLM 컨텍스트("시간창 해석 근거")에 실려 가므로, 바뀌면 프롬프트 변수가 된다.
 */
class TimeExpressionParserGateTest {

    private static final Instant NOW = Instant.parse("2026-07-28T05:00:00Z");

    private final TimeExpressionParser parser = new TriageConfig().timeExpressionParser(
            new SurveyProperties("Asia/Seoul", 24, 48, "5m", null, null, null, 20, null, List.of(),
                    null, null, null));

    private record Pinned(String id, String question, int hours) {
    }

    private static final List<Pinned> PINNED = List.of(
            new Pinned("CH-1", "최근 1시간 안에 댓글 알림이 안 왔다는 제보가 있어요. 확인해줘", 1),
            new Pinned("CH-2", "최근 1시간 안에 채팅 알림이 안 온다는 문의가 여러 건 들어왔다. 원인을 조사해줘", 1),
            new Pinned("CH-3", "최근 1시간 안에 앱이 잠깐 버벅였다는 얘기가 있어요. 뭔가 있었는지 봐줘", 1),
            new Pinned("AU-2", "최근 1시간 안에 로그인이 안 된다는 문의가 몰렸다. 원인을 조사해줘", 1),
            new Pinned("AU-3", "최근 1시간부터 앱이 자꾸 로그인 화면으로 튕긴다는 문의가 많아요", 1),
            new Pinned("AU-4", "최근 2시간 안에 피드에 작성자 이름이 이상하게 나온다는 제보가 있다. 원인을 조사해줘", 2),
            new Pinned("IN-1", "최근 1시간 안에 문의가 몇 건 들어왔어요. ① 로그인이 느리다 ② 친구가 접속해 있는데"
                    + " 오프라인으로 보인다 ③ 피드에 작성자 이름이 이상하다", 1),
            new Pinned("IN-2", "최근 1시간 안에 댓글 알림이 안 왔다는 제보가 있다. 원인을 조사해줘", 1),
            new Pinned("IN-3", "최근 1시간 안에 앱 전체가 느렸어요", 1),
            new Pinned("AP-1", "최근 1시간 안에 댓글 작성이 실패했다는 제보가 있다. 원인을 조사해줘", 1),
            new Pinned("AP-2", "최근 1시간 안에 팔로우 목록이 안 열린다는 제보가 있다. 원인을 조사해줘", 1),
            new Pinned("AP-3", "최근 1시간 안에 피드 작성이 실패했다는 제보가 있다. 원인을 조사해줘", 1));

    @Test
    void 박제_문안_12개의_창과_해석_문자열이_불변이다() {
        for (var pinned : PINNED) {
            var resolved = parser.resolve(pinned.question(), null, null, NOW);

            assertThat(resolved.window().end()).as("%s 창 끝", pinned.id()).isEqualTo(NOW);
            assertThat(Duration.between(resolved.window().start(), NOW))
                    .as("%s 창 폭", pinned.id())
                    .isEqualTo(Duration.ofHours(pinned.hours()));
            assertThat(resolved.expression())
                    .as("%s 해석 문자열 — 탐색 컨텍스트에 실리므로 창만이 아니라 문자열도 불변이어야 한다", pinned.id())
                    .isEqualTo("상대 표현 '최근 " + pinned.hours() + "시간'");
        }
    }
}
