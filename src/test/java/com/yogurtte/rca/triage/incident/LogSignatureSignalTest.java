package com.yogurtte.rca.triage.incident;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.yogurtte.rca.collector.TimeWindow;
import com.yogurtte.rca.triage.survey.SurveyResult;

/**
 * 로그 후보의 지문을 예외 클래스로 가른다.
 *
 * <p>군집 키가 {@code 채널|리소스|지문}인데 Loki만 지문이 항상 {@code ERROR/WARN}이라 판별력이
 * 0이었다 — 한 서비스에서 성격이 다른 예외가 동시에 나면 후보 하나로 뭉친다. 지문 곡선
 * ({@code exc} 라벨을 함께 세는 쿼리)이 있으면 그것을 쓰고, 없으면 총 건수 곡선으로 되돌아간다.
 *
 * <p><b>쿼리 자체는 미검증이다</b> — 기본값이 빈 문자열이라 켜지 않으면 아래 두 번째 테스트의
 * 경로로만 돈다. 여기서 박제하는 것은 "라벨이 오면 어떻게 갈리는가"다.
 */
class LogSignatureSignalTest {

    private static final TimeWindow WINDOW = new TimeWindow(
            Instant.parse("2026-08-01T00:00:00Z"), Instant.parse("2026-08-01T01:00:00Z"));
    private static final Duration LOOKBACK = Duration.ofMinutes(5);

    /** exc 라벨이 함께 오는 지문 곡선. 같은 서비스·같은 시각에 예외 두 종류. */
    private static final String WITH_EXC = """
            {"data":{"result":[
              {"metric":{"service_name":"chat-service","exc":"QueryTimeoutException"},
               "values":[[1785283500,"19"]]},
              {"metric":{"service_name":"chat-service","exc":"MongoSocketOpenException"},
               "values":[[1785283500,"4"]]}]}}
            """;

    /** 기존 총 건수 곡선. 같은 창의 같은 서비스가 한 줄이다. */
    private static final String COUNT_ONLY = """
            {"data":{"result":[
              {"metric":{"service_name":"chat-service"},"values":[[1785283500,"23"]]}]}}
            """;

    private static SurveyResult survey(String logRates, String signatureRates) {
        return new SurveyResult(WINDOW, "테스트", null, null, logRates, signatureRates,
                Map.of(), List.of(), Map.of());
    }

    @Test
    void 예외_클래스가_지문이_되어_후보가_갈린다() {
        List<Signal> signals = SignalExtractor.extract(survey(COUNT_ONLY, WITH_EXC), LOOKBACK);

        // 대체가 아니라 병렬이다 — 총 건수 곡선(규모)과 예외 곡선(성격)을 함께 싣는다.
        assertThat(signals).extracting(Signal::key).containsExactlyInAnyOrder(
                "LOKI|chat-service|ERROR/WARN",
                "LOKI|chat-service|QueryTimeoutException",
                "LOKI|chat-service|MongoSocketOpenException");

        List<Incident> incidents = Incident.cluster(signals, Duration.ofSeconds(60));
        assertThat(incidents).hasSize(3);
    }

    @Test
    void 예외가_안_딸린_ERROR_WARN이_사라지지_않는다() {
        // 예외 곡선만 쓰면 08-05 창 실측 기준 78건 중 42건(WARN 등)이 통째로 사라진다.
        List<Signal> signals = SignalExtractor.extract(survey(COUNT_ONLY, WITH_EXC), LOOKBACK);

        assertThat(signals).anySatisfy(s -> {
            assertThat(s.signature()).isEqualTo("ERROR/WARN");
            assertThat(s.what()).contains("ERROR/WARN 23건");
        });
    }

    @Test
    void 지문_곡선이_없으면_기존_동작_그대로다() {
        List<Signal> signals = SignalExtractor.extract(survey(COUNT_ONLY, null), LOOKBACK);

        assertThat(signals).extracting(Signal::key).containsExactly("LOKI|chat-service|ERROR/WARN");
        assertThat(Incident.cluster(signals, Duration.ofSeconds(60))).hasSize(1);
    }

    @Test
    void 예외_곡선의_건수는_줄_수가_아니라_예외_횟수다() {
        // 쿼리가 예외 클래스 줄만 세므로 스택 30줄이 30건이 되지 않는다. 문구로 구별하지 않으면
        // 읽는 쪽이 총 건수와 더한다.
        List<Signal> signals = SignalExtractor.extract(survey(COUNT_ONLY, WITH_EXC), LOOKBACK);

        assertThat(signals).filteredOn(s -> !"ERROR/WARN".equals(s.signature()))
                .allSatisfy(s -> assertThat(s.what()).startsWith("예외 "));
    }

    @Test
    void 지문_쿼리가_비면_총_건수로_되돌아간다() {
        // Loki가 `| regexp` 매칭 실패로 시리즈를 통째로 비울 수 있다. 그때 후보가 사라지면 안 된다.
        String empty = "{\"data\":{\"result\":[]}}";

        List<Signal> signals = SignalExtractor.extract(survey(COUNT_ONLY, empty), LOOKBACK);

        assertThat(signals).extracting(Signal::key).containsExactly("LOKI|chat-service|ERROR/WARN");
    }

    @Test
    void 갈린_후보도_건수는_각자_유지한다() {
        List<Signal> signals = SignalExtractor.extract(survey(COUNT_ONLY, WITH_EXC), LOOKBACK);

        assertThat(signals).anySatisfy(s -> assertThat(s.what()).contains("예외 19건"));
        assertThat(signals).anySatisfy(s -> assertThat(s.what()).contains("예외 4건"));
    }
}
