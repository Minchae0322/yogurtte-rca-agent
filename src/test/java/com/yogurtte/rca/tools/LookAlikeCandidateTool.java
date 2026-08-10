package com.yogurtte.rca.tools;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import com.yogurtte.rca.collector.TimeWindow;
import com.yogurtte.rca.triage.incident.Incident;
import com.yogurtte.rca.triage.incident.Signal;
import com.yogurtte.rca.triage.incident.SignalExtractor;
import com.yogurtte.rca.triage.plan.SurveyContextAssembler;
import com.yogurtte.rca.triage.survey.SurveyResult;

/**
 * <b>구별 불가능한 두 후보</b>를 만들어 지문의 값어치만 떼어 잰다.
 *
 * <pre>
 *   23:00~23:05  auth-service   ERROR/WARN 3건   ← 증상(댓글 알림)과 무관
 *   00:10~00:15  chat-service   ERROR/WARN 3건   ← 실제 장애
 * </pre>
 *
 * <p>지문이 없으면 둘은 <b>서비스 이름과 시각만</b> 다르다. 지문이 붙으면 각각
 * {@code RestApiException} · {@code MongoSocketOpenException}이 된다.
 * 예외 이름과 건수는 라이브 Loki에서 실제로 관측된 것을 쓴다(08-05 창).
 *
 * <p><b>곡선 JSON은 이 실험을 위해 손으로 만든 것이다.</b> 실측 회차가 아니라 설계된 대조이고,
 * 그래서 점수·회차 기록에 넣지 않는다.
 *
 * <p>실행: {@code ./gradlew test --tests '*LookAlikeCandidateTool*' -Drca.tools=true}
 * 출력: {@code build/lookalike-{A,B}.txt}
 */
@EnabledIfSystemProperty(named = "rca.tools", matches = "true")
class LookAlikeCandidateTool {

    private static final TimeWindow WINDOW = new TimeWindow(
            Instant.parse("2026-08-05T23:00:00Z"), Instant.parse("2026-08-06T00:30:00Z"));
    private static final String QUESTION = "최근에 댓글 알림이 안 왔다는 제보가 있어요. 확인해줘";

    @Test
    void 구별_불가능한_두_후보를_만든다() throws Exception {
        long authAt = Instant.parse("2026-08-05T23:05:00Z").getEpochSecond();
        long chatAt = Instant.parse("2026-08-06T00:15:00Z").getEpochSecond();

        String base = """
                {"data":{"result":[
                  {"metric":{"service_name":"chat-service"},"values":[[%d,"3"],[%d,"3"]]}]}}
                """.formatted(authAt, chatAt);

        String signature = """
                {"data":{"result":[
                  {"metric":{"service_name":"chat-service",
                             "exc":"org.springframework.security.authorization.AuthorizationDeniedException"},
                   "values":[[%d,"3"]]},
                  {"metric":{"service_name":"chat-service",
                             "exc":"com.mongodb.MongoSocketOpenException"},
                   "values":[[%d,"3"]]}]}}
                """.formatted(authAt, chatAt);

        write("A", base, null);
        write("B", base, signature);
    }

    private static void write(String arm, String base, String signature) throws Exception {
        SurveyResult survey = new SurveyResult(WINDOW, "명시적 from/to", null, null, base, signature,
                Map.of(), List.of(), Map.of());
        List<Signal> signals = SignalExtractor.extract(survey, Duration.ofMinutes(5));
        List<Incident> incidents = Incident.cluster(signals, Duration.ofSeconds(60));

        String context = new SurveyContextAssembler().assemble(survey, QUESTION, incidents, false);
        Path out = Path.of("build/lookalike-" + arm + ".txt");
        Files.createDirectories(out.getParent());
        Files.writeString(out, context);
        System.out.printf("팔 %s — 후보 %d개 · %,d자 → %s%n", arm, incidents.size(), context.length(), out);
        incidents.forEach(i -> System.out.println("    " + i.id() + "  " + i.resource() + " | " + i.signature()));
    }
}
