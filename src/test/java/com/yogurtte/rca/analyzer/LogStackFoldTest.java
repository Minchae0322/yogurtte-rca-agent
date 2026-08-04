package com.yogurtte.rca.analyzer;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * 로그 접기(B-34)가 <b>무엇을 접고 무엇을 못 접게 하는지</b>를 고정한다.
 *
 * <p>이 테스트의 절반은 "접힌다"가 아니라 <b>"안 접힌다"</b>를 고정한다. 접기의 위험은 크기가
 * 아니라 근거 손실이고, 잃으면 안 되는 것 둘은 회차 실측으로 확정돼 있다 —
 * <b>앱 프레임</b>(IN-1 회차 3이 그 세 줄로 원인 기전을 특정했다)과 <b>예외 메시지의 수치</b>
 * (회차 4의 근거가 {@code Command timed out after 2 second(s)} 같은 상수 위에 서 있다).
 *
 * <p>픽스처는 실제 원본({@code reports/raw/*-loki-error-warn.json})에서 뽑은 줄들이다 —
 * NPE 스택(AP-2의 정답이 있던 자리) · shedlock 경계 프레임 · lettuce {@code Caused by}.
 */
class LogStackFoldTest {

    private static final LogFoldProperties FOLD =
            new LogFoldProperties(true, List.of("com.example"), 2, true, 3);
    private static final String NEAR = fixture("/loki/stackfold-near-repeat.json");
    private static final String FIXTURE = fixture("/loki/stackfold-repeat.json");

    @Test
    void 앱_프레임은_전부_남는다() {
        LogStackFold.Result folded = LogStackFold.fold(FIXTURE, FOLD);

        // 원인이 이 두 줄에 있다. 하나라도 접히면 접기 규칙이 잘못된 것이다.
        assertThat(folded.json()).contains("FollowCondition.java:25");
        assertThat(folded.json()).contains("FollowRepositoryCustomImpl.java:35");
        assertThat(folded.json()).contains("ExpGrantService.java:88");
    }

    @Test
    void 예외_클래스명과_메시지의_수치는_원문_그대로다() {
        LogStackFold.Result folded = LogStackFold.fold(FIXTURE, FOLD);

        assertThat(folded.json()).contains("java.lang.NullPointerException");
        assertThat(folded.json()).contains("io.lettuce.core.RedisCommandTimeoutException");
        // 숫자를 <*>로 치환하는 템플릿 마이닝이 여기서는 독이다 — 그 값이 근거다.
        assertThat(folded.json()).contains("Command timed out after 2 second(s)");
        assertThat(folded.json()).contains("Remaining time: 29999 ms");
    }

    @Test
    void 앱_프레임_경계_1프레임은_서드파티라도_남는다() {
        LogStackFold.Result folded = LogStackFold.fold(FIXTURE, FOLD);

        // 리포트 인용 공동 2위가 net.javacrumbs.shedlock의 RedisLockProvider였다 —
        // 접두 목록(com.example)만 쓰면 잘려나가는 자리다.
        assertThat(folded.json()).contains("MethodProxyScheduledLockAdvisor.java:74");
        // 앱 프레임 바로 뒤(AopUtils)와 바로 앞(CommandWrapper)도 경계라 남는다.
        assertThat(folded.json()).contains("AopUtils.java:352");
        assertThat(folded.json()).contains("CommandWrapper.java:65");
    }

    @Test
    void 라이브러리_프레임_연속_구간만_표식으로_접힌다() {
        LogStackFold.Result folded = LogStackFold.fold(FIXTURE, FOLD);

        // 경계가 아닌 spring·reactor 3줄과 lettuce 2줄, 블록당 5줄이 표식 2줄로 접힌다.
        // 세로 접기가 먼저라 3벌 전부에서 접히고(3줄 x 3벌), 그 다음 가로 접기가 2벌을 지운다.
        assertThat(folded.foldedFrames()).isEqualTo(9);
        assertThat(folded.json()).contains("… 3 frames (org.springframework, reactor.core)");
        assertThat(folded.json()).contains("… 2 frames (io.lettuce)");
        // 접힌 것은 사라지지 않는다 — 몇 줄이 어느 패키지에서 접혔는지가 남는다.
        assertThat(folded.json()).doesNotContain("CglibAopProxy.java:720");
    }

    @Test
    void 홀로_있는_라이브러리_프레임은_접지_않는다() {
        LogStackFold.Result folded = LogStackFold.fold(FIXTURE, FOLD);

        // 1줄을 접으면 표식 줄로 바뀔 뿐이라 이득 없이 문맥만 잃는다 (min-run=2).
        assertThat(folded.json()).contains("AbstractChannelHandlerContext.java:442");
    }

    @Test
    void 글자까지_같은_블록은_한_벌과_발생_통계로_접힌다() {
        LogStackFold.Result folded = LogStackFold.fold(FIXTURE, FOLD);

        assertThat(folded.foldedBlocks()).isEqualTo(2);          // 3벌 중 2벌 제거
        assertThat(folded.json()).contains("[x3회");
        assertThat(folded.json()).contains("12:19:18.000 ~ 12:19:22.000 UTC");
        // 횟수와 간격은 정보를 빼는 게 아니라 더한다 — 모델이 원문을 세지 않아도 된다.
        assertThat(folded.json()).contains("평균 2.0초 간격");
    }

    @Test
    void 다른_스트림의_줄과는_섞지_않는다() {
        LogStackFold.Result folded = LogStackFold.fold(FIXTURE, FOLD);

        // content-service의 WARN 한 줄은 auth-service 블록과 무관하게 그대로 남는다.
        assertThat(folded.json()).contains("캐시 조회 지연");
    }

    @Test
    void 끄면_원문_문자열_그대로다() {
        LogStackFold.Result off = LogStackFold.fold(FIXTURE, LogFoldProperties.off());

        // 대조군 팔이 바이트 단위로 같아야 토큰 축 비교가 성립한다.
        assertThat(off.json()).isSameAs(FIXTURE);
        assertThat(off.folded()).isFalse();
    }

    @Test
    void 접을_것이_없으면_재직렬화하지_않는다() {
        String noStack = """
                {"status":"success","data":{"resultType":"streams","result":[
                  {"stream":{"service_name":"content-service"},
                   "values":[["1785759558000000000","WARN 캐시 조회 지연"]]}]}}""";

        LogStackFold.Result folded = LogStackFold.fold(noStack, FOLD);

        assertThat(folded.json()).isSameAs(noStack);
    }

    @Test
    void 깨진_JSON이면_원문을_그대로_돌려준다() {
        // 접기는 최적화다 — 파싱 실패로 데이터를 잃는 쪽이 부풀어 있는 것보다 나쁘다.
        assertThat(LogStackFold.fold("깨진 json {", FOLD).json()).isEqualTo("깨진 json {");
        assertThat(LogStackFold.fold(null, FOLD).json()).isNull();
    }

    @Test
    void 접힌_바이트가_실제로_줄어든다() {
        LogStackFold.Result folded = LogStackFold.fold(FIXTURE, FOLD);

        int before = FIXTURE.getBytes(StandardCharsets.UTF_8).length;
        int after = folded.json().getBytes(StandardCharsets.UTF_8).length;
        assertThat(after).isLessThan(before / 2);
    }


    @Test
    void 줄_안_잡음은_빠지고_스레드명과_메시지는_남는다() {
        LogStackFold.Result folded = LogStackFold.fold(NEAR, FOLD);

        // ANSI 색코드는 정보량이 0이다 — 실측 로그 바이트의 4.0%였다.
        assertThat(folded.json()).doesNotContain("\u001B[");
        // 한 줄에 두 번 찍히는 traceId-spanId 중 둘째만 뺀다. 값 자체는 MDC 괄호에 남는다.
        assertThat(folded.json()).contains("traceId=6a70a4cbf41848fcfa14ba00fe4a02f8");
        assertThat(folded.json()).doesNotContain("[6a70a4cbf41848fcfa14ba00fe4a02f8-fcf6a78d070833c1]");
        // 스트림 라벨과 같은 값인 --- [chat-service] 는 빠지고, 스레드명은 남는다
        // (스레드가 곧 근거인 문항이 있다 · AU-4의 [reactor-http-epoll-1]).
        assertThat(folded.json()).doesNotContain("--- [chat-service]");
        assertThat(folded.json()).contains("[ntainer#5-1-C-1]");
        assertThat(folded.json()).contains("c.e.t.app.config.KafkaConsumerConfig");
        assertThat(folded.json()).contains("user-notification 처리 실패");
        assertThat(folded.strippedBytes()).isGreaterThan(0);
    }

    @Test
    void 숫자만_다른_반복은_첫_벌과_끝_벌을_원문으로_남긴다() {
        LogStackFold.Result folded = LogStackFold.fold(NEAR, FOLD);

        assertThat(folded.foldedBlocks()).isEqualTo(3);                 // 5벌 중 가운데 3벌
        assertThat(folded.json()).contains("[x5회");
        assertThat(folded.json()).contains("숫자만 다른 반복이라 첫 벌과 끝 벌만 싣는다");
        // 값의 양 끝은 원문 그대로 남는다 — 회차 4의 근거가 이런 수치 위에 서 있다.
        assertThat(folded.json()).contains("Remaining time: 29999 ms");
        assertThat(folded.json()).contains("Remaining time: 29995 ms");
        assertThat(folded.json()).contains("처리 실패 1회차");
        assertThat(folded.json()).contains("처리 실패 5회차");
        assertThat(folded.json()).doesNotContain("처리 실패 3회차");
    }

    @Test
    void 근사_반복_접기를_끄면_전부_남는다() {
        LogFoldProperties noNear = new LogFoldProperties(true, List.of("com.example"), 2, true, 0);

        LogStackFold.Result folded = LogStackFold.fold(NEAR, noNear);

        assertThat(folded.foldedBlocks()).isZero();
        assertThat(folded.json()).contains("처리 실패 3회차");
    }

    @Test
    void 메트릭_matrix_응답은_건드리지_않는다() {
        // 같은 values 배열 모양이지만 내용이 집계값이라, 줄로 읽고 접으면 오히려 커진다
        // (스윕 발생률 응답에서 실제로 +9.8%가 났다).
        String matrix = """
                {"status":"success","data":{"resultType":"matrix","result":[
                  {"metric":{"service_name":"chat-service"},
                   "values":[[1785759558,"4"],[1785759618,"4"],[1785759678,"4"]]}]}}""";

        assertThat(LogStackFold.fold(matrix, FOLD).json()).isSameAs(matrix);
    }

    private static String fixture(String path) {
        try (InputStream in = LogStackFoldTest.class.getResourceAsStream(path)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
