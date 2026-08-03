package com.yogurtte.rca.triage.plan;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.yogurtte.rca.collector.TimeWindow;
import com.yogurtte.rca.triage.incident.Incident;
import com.yogurtte.rca.triage.incident.Channel;
import com.yogurtte.rca.triage.incident.Precision;
import com.yogurtte.rca.triage.incident.Signal;
import com.yogurtte.rca.triage.survey.SurveyResult;

/**
 * 대조군 A/B가 실제로 갈리는지만 본다 — 이 스위치가 조용히 무시되면 두 회차가 같은 입력으로
 * 돌고도 다른 팔로 기록된다.
 */
class SurveyContextAssemblerTest {

    private static final String TEMPO_RAW = "{\"traces\":[{\"traceID\":\"abc123\"}]}";
    private static final String LOKI_RAW = "{\"data\":{\"result\":[{\"metric\":{\"service_name\":\"chat\"}}]}}";
    private static final String MIMIR_RAW = "{\"data\":{\"result\":[{\"metric\":{\"topic\":\"user.fcm\"}}]}}";

    private final SurveyContextAssembler assembler = new SurveyContextAssembler();

    private SurveyResult survey() {
        Instant start = Instant.parse("2026-07-29T08:13:16Z");
        return new SurveyResult(new TimeWindow(start, start.plus(Duration.ofHours(1))), "최근 1시간",
                TEMPO_RAW, null, LOKI_RAW, Map.of("min_over_time(up[5m])", MIMIR_RAW),
                List.of("Metric 'up'이 이 창에서 시리즈 0건이다."), Map.of());
    }

    private List<Incident> incidents() {
        Instant at = Instant.parse("2026-07-29T08:51:47Z");
        Signal signal = new Signal(at, at.plusMillis(119), Channel.TEMPO, Precision.EXACT,
                "content-service", "http post /feeds", "content-service http post /feeds 119ms", "abc123");
        return Incident.cluster(List.of(signal), Duration.ofSeconds(60));
    }

    @Test
    void A_후보와_원본을_모두_싣는다() {
        String context = assembler.assemble(survey(), "댓글 알림이 안 와요", incidents(), true);

        assertThat(context).contains("# 장애 후보").contains("INC-1");
        assertThat(context).contains(TEMPO_RAW).contains(LOKI_RAW).contains(MIMIR_RAW);
    }

    @Test
    void B_원본을_빼면_후보와_무신호_목록만_남는다() {
        String context = assembler.assemble(survey(), "댓글 알림이 안 와요", incidents(), false);

        assertThat(context).contains("# 장애 후보").contains("INC-1");
        assertThat(context).contains("# 무신호/실패 목록").contains("시리즈 0건");
        assertThat(context).doesNotContain(TEMPO_RAW).doesNotContain(LOKI_RAW).doesNotContain(MIMIR_RAW);
        // 원본이 있다고 가정한 답("확인해 보니")을 막는 고지가 있어야 한다.
        assertThat(context).contains("원본 JSON을 싣지 않는다");
    }

    @Test
    void B가_A보다_짧다() {
        int withRaw = assembler.assemble(survey(), "질문", incidents(), true).length();
        int withoutRaw = assembler.assemble(survey(), "질문", incidents(), false).length();

        assertThat(withoutRaw).isLessThan(withRaw);
    }
}
