package com.yogurtte.rca.collector;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

/**
 * 분석 단계 로그 라인 필터가 <b>스택트레이스까지</b> 잡는지 고정한다.
 *
 * <p>{@code ERROR|WARN} 만으로는 예외의 헤더 줄만 오고 스택은 통째로 빠졌다. 스택 줄에는
 * {@code ERROR} 도 없고 traceId도 붙지 않아 {@code |= "<traceId>"} 로도 도달하지 못한다.
 * NPE는 메시지가 비어 <b>정답이 스택에만</b> 있었다 ({@code FollowCondition.java:25}).
 */
class CollectPropertiesTest {

    private final CollectProperties properties = new CollectProperties(
            120, "content-service|auth-service|chat-service", "service_name",
            1000, "15s", List.of("up"), 102400, 30, 3);

    /** 실제로 잘렸던 로그 원문 모양. */
    private static final String HEADER =
            "ERROR [traceId=6a68b1] c.e.t.FollowService : 팔로우 목록 조회 실패";
    private static final String EXCEPTION_LINE =
            "java.lang.NullPointerException: Cannot invoke \"Integer.intValue()\"";
    private static final String STACK_FRAME =
            "\tat com.example.FollowCondition.getSize(FollowCondition.java:25)";
    private static final String CAUSED_BY =
            "Caused by: java.lang.IllegalStateException: size is null";
    private static final String ORDINARY_INFO =
            "INFO  [traceId=6a68b1] c.e.t.FollowController : GET /api/users/3/follows 200 - 12ms";

    @Test
    void 헤더와_예외줄과_스택프레임을_모두_잡는다() {
        var regex = Pattern.compile(CollectProperties.ERROR_LINE_PATTERN);

        assertThat(regex.matcher(HEADER).find()).isTrue();
        assertThat(regex.matcher(EXCEPTION_LINE).find()).isTrue();
        assertThat(regex.matcher(STACK_FRAME).find()).isTrue();      // 이게 정답이 있던 줄
        assertThat(regex.matcher(CAUSED_BY).find()).isTrue();
    }

    @Test
    void 평범한_INFO_로그는_잡지_않는다() {
        // 양이 예외 건수에 비례해야 한다. INFO를 오탐하면 1시간 2,300줄이 전부 들어온다.
        var regex = Pattern.compile(CollectProperties.ERROR_LINE_PATTERN);

        assertThat(regex.matcher(ORDINARY_INFO).find()).isFalse();
    }

    @Test
    void 패턴을_백틱_문자열로_넘긴다() {
        // LogQL의 큰따옴표 문자열은 이스케이프를 해석해 \. 를 두 번 겹쳐 써야 하고,
        // 그 자리에서 정규식이 조용히 깨진다.
        var query = properties.errorWarnQuery();

        assertThat(query).isEqualTo(
                "{service_name=~\"content-service|auth-service|chat-service\"} "
                        + "|~ `ERROR|WARN|Exception|Caused by|\\.java:[0-9]+\\)`");
    }

    @Test
    void 탐색이_좁혀준_서비스만_본다() {
        var query = properties.errorWarnQuery(List.of("auth-service"));

        assertThat(query).startsWith("{service_name=~\"auth-service\"}");
    }

    @Test
    void 설정에_없는_서비스명은_버린다() {
        // LLM이 지어낸 이름이 셀렉터에 들어가면 매칭 스트림이 0개가 되어 조용히 빈 결과가 된다.
        var query = properties.errorWarnQuery(List.of("payment-service"));

        assertThat(query).startsWith("{service_name=~\"content-service|auth-service|chat-service\"}");
    }
}
