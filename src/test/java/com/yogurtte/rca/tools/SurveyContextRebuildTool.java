package com.yogurtte.rca.tools;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import com.yogurtte.rca.collector.TimeWindow;
import com.yogurtte.rca.triage.incident.Incident;
import com.yogurtte.rca.triage.incident.Signal;
import com.yogurtte.rca.triage.incident.SignalExtractor;
import com.yogurtte.rca.triage.plan.SurveyContextAssembler;
import com.yogurtte.rca.triage.survey.SurveyResult;

/**
 * 저장된 <b>스윕</b> 원본을 파이프라인에 그대로 태워 탐색 LLM이 실제로 받는 텍스트를 복원한다.
 *
 * <p>{@link ContextRebuildTool}의 탐색 단계 판이다. 그쪽이 분석 컨텍스트(단계 I)를 복원한다면
 * 이쪽은 탐색 컨텍스트(단계 E)이고, 복원 경로도 같다 — {@code reports/raw/}에 스윕 응답이
 * 그대로 남아 있어 {@code SignalExtractor → Incident.cluster → SurveyContextAssembler}를
 * 다시 돌릴 수 있다.
 *
 * <p><b>쓰임은 둘.</b> ① 문서·설명용으로 "모델이 뭘 보는가"를 실물로 꺼낸다
 * ② {@code include-raw} A/B의 크기 차를 저장 원본으로 잰다(주입 없이).
 *
 * <p>실행: {@code ./gradlew test --tests '*SurveyContextRebuildTool*' -Drca.tools=true}
 * 출력: {@code build/survey-context-{A,B}.txt}
 */
@EnabledIfSystemProperty(named = "rca.tools", matches = "true")
class SurveyContextRebuildTool {

    private static final Path RAW = Path.of("reports/raw");

    /** 복원 대상 스윕. 파일명 접두이고, 같은 run의 5채널이 이 접두를 공유한다. */
    private static final String RUN = "scan-1785804722-20260804T0852";

    /** 그 회차의 창·질문 — 리포트 {@code scan-1785807480-20260804T085653.json}에 박제된 값이다. */
    private static final TimeWindow WINDOW = new TimeWindow(
            Instant.parse("2026-08-04T00:52:02.949Z"), Instant.parse("2026-08-04T01:52:02.949Z"));
    private static final String QUESTION = "최근 1시간 안에 문의가 몇 건 들어왔어요. ① 로그인이 느리다 "
            + "② 친구가 접속해 있는데 오프라인으로 보인다 ③ 피드에 작성자 이름이 이상하다";

    @Test
    void 스윕_원본에서_탐색_컨텍스트를_복원한다() throws Exception {
        Map<String, String> metrics = new LinkedHashMap<>();
        try (var files = Files.list(RAW)) {
            for (Path file : files.filter(p -> p.getFileName().toString().startsWith(RUN)).toList()) {
                String name = file.getFileName().toString();
                if (name.contains("-mimir-")) {
                    metrics.put(name.replaceAll(".*-mimir-", "").replace(".json", ""), Files.readString(file));
                }
            }
        }

        SurveyResult survey = new SurveyResult(WINDOW, "명시적 from/to",
                read(RUN + "57-tempo-search.json"),
                read("scan-1785804722-slow-20260804T085257-tempo-search.json"),
                read(RUN + "57-loki-survey-error-rate.json"),
                metrics, List.of(), Map.of());

        List<Signal> signals = SignalExtractor.extract(survey, Duration.ofMinutes(1),
                Set.of("up", "mongodb_up", "kafka_brokers"));
        List<Incident> incidents = Incident.cluster(signals, Duration.ofSeconds(60));

        SurveyContextAssembler assembler = new SurveyContextAssembler();
        String withoutRaw = assembler.assemble(survey, QUESTION, incidents, false);
        String withRaw = assembler.assemble(survey, QUESTION, incidents, true);

        Files.createDirectories(Path.of("build"));
        Files.writeString(Path.of("build/survey-context-B.txt"), withoutRaw);
        Files.writeString(Path.of("build/survey-context-A.txt"), withRaw);

        System.out.printf("신호 %d · 후보 %d · B(원본 제외) %,d자 · A(원본 포함) %,d자 (%.1f%% 절감)%n",
                signals.size(), incidents.size(), withoutRaw.length(), withRaw.length(),
                (1 - withoutRaw.length() / (double) withRaw.length()) * 100);
    }

    private static String read(String name) throws Exception {
        Path file = RAW.resolve(name);
        return Files.exists(file) ? Files.readString(file) : null;
    }
}
