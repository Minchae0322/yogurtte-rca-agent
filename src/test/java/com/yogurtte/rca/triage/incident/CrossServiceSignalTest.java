package com.yogurtte.rca.triage.incident;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.yogurtte.rca.collector.TimeWindow;
import com.yogurtte.rca.triage.survey.SurveyResult;

/**
 * 트레이스가 지나간 서비스 전부를 후보에 싣는다.
 *
 * <p>검색 목록에는 <b>루트 하나만</b> 뜨므로 {@code content → kafka → chat} 트레이스도 탐색
 * 단계에서는 {@code content}로만 보였다. Tempo {@code /api/search} 응답의 {@code serviceStats}가
 * 그 정보를 이미 주고 있었고 파싱만 안 하고 있었다 — 픽스처는 저장 응답 원본이다.
 */
class CrossServiceSignalTest {

    private static final TimeWindow WINDOW = new TimeWindow(
            Instant.parse("2026-08-01T00:00:00Z"), Instant.parse("2026-08-01T01:00:00Z"));

    private static SurveyResult surveyOf(String searchJson) {
        return new SurveyResult(WINDOW, "테스트", searchJson, null, null, Map.of(), List.of(), Map.of());
    }

    private static String fixture(String name) throws Exception {
        return Files.readString(Path.of("src/test/resources/tempo", name));
    }

    @Test
    void 지나간_서비스가_신호_설명에_붙는다() throws Exception {
        List<Signal> signals = SignalExtractor.extract(
                surveyOf(fixture("search-cross-service.json")), Duration.ofMinutes(5));

        assertThat(signals).hasSize(1);
        assertThat(signals.get(0).what())
                .contains("content-service http post /feeds/{feedId}/comments")
                .contains("지나간 서비스: auth-service 4 · chat-service 14 · content-service 28");
    }

    @Test
    void 지문에는_넣지_않는다() throws Exception {
        // 키에 넣으면 같은 엔드포인트가 상류 조합마다 다른 후보로 흩어진다 (Mimir 라벨과 같은 이유).
        List<Signal> signals = SignalExtractor.extract(
                surveyOf(fixture("search-cross-service.json")), Duration.ofMinutes(5));

        assertThat(signals.get(0).signature()).isEqualTo("http post /feeds/{feedId}/comments");
        assertThat(signals.get(0).key()).isEqualTo("TEMPO|content-service|http post /feeds/{feedId}/comments");
    }

    @Test
    void 서비스가_하나뿐이면_붙이지_않는다() {
        String json = """
                {"traces":[{"traceID":"abc","rootServiceName":"content-service",
                 "rootTraceName":"http get /feeds","startTimeUnixNano":"1785739092870260000",
                 "durationMs":74,"serviceStats":{"content-service":{"spanCount":23,"errorCount":1}}}]}
                """;

        List<Signal> signals = SignalExtractor.extract(surveyOf(json), Duration.ofMinutes(5));

        assertThat(signals.get(0).what()).doesNotContain("지나간 서비스");
    }

    @Test
    void 에러가_난_서비스는_건수까지_적는다() {
        // 어느 서비스에서 에러가 났는지가 신호다. errorCount 0은 적지 않아 그 하나가 묻히지 않게 한다.
        String json = """
                {"traces":[{"traceID":"abc","rootServiceName":"content-service",
                 "rootTraceName":"http post /feeds","startTimeUnixNano":"1785739092870260000",
                 "durationMs":74,"serviceStats":{"content-service":{"spanCount":23},
                 "auth-service":{"spanCount":4,"errorCount":2}}}]}
                """;

        List<Signal> signals = SignalExtractor.extract(surveyOf(json), Duration.ofMinutes(5));

        assertThat(signals.get(0).what()).contains("content-service 23 · auth-service 4 (err 2)");
    }

    @Test
    void serviceStats가_없는_예전_응답도_그대로_돈다() {
        String json = """
                {"traces":[{"traceID":"abc","rootServiceName":"content-service",
                 "rootTraceName":"http get /feeds","startTimeUnixNano":"1785739092870260000",
                 "durationMs":74}]}
                """;

        List<Signal> signals = SignalExtractor.extract(surveyOf(json), Duration.ofMinutes(5));

        assertThat(signals).hasSize(1);
        assertThat(signals.get(0).what()).doesNotContain("지나간 서비스");
    }
}
