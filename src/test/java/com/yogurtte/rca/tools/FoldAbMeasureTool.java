package com.yogurtte.rca.tools;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yogurtte.rca.analyzer.ContextAssembler;
import com.yogurtte.rca.analyzer.LogFoldProperties;
import com.yogurtte.rca.analyzer.ServiceGraphExtractor;
import com.yogurtte.rca.analyzer.TraceCompactProperties;
import com.yogurtte.rca.collector.CollectProperties;
import com.yogurtte.rca.collector.CollectedData;

/**
 * 로그 접기(B-34)가 <b>분석 컨텍스트 전체</b>에서 몇 퍼센트를 줄이는지 잰다 —
 * round-5 §9가 요구한 두 팔 A/B를, 컨텍스트를 분모로 놓고 수행한다.
 *
 * <p><b>왜 {@link LogFoldMeasureTool}로는 부족한가.</b> 그 도구는 트레이스·메트릭을 비운
 * {@link CollectedData}로 어셈블해 <b>로그 절만</b> 잰다(-56.3% 전수). 그런데 토큰이 걸리는
 * 분모는 컨텍스트 전체다. 로그 절 절감률을 토큰 절감률로 인용하면 과대 계상이 된다.
 *
 * <p><b>왜 컨텍스트를 재조립하지 않는가.</b> {@link ContextRebuildTool}로 과거 컨텍스트를
 * 복원하면 지금은 <b>37건 전부 {@code contextChars}와 불일치</b>한다 - 그 회차 이후 어셈블러가
 * 바뀌었고(B-25 메트릭 요약 · B-44~46), 회차 2~({@code scan-*})는 트레이스 원본이
 * {@code tempo-search}뿐이라 후보 트레이스를 복원할 수도 없다. 재조립한 컨텍스트를 분모로
 * 쓰면 그 회차가 실제로 본 입력이 아니다.
 *
 * <p>그래서 <b>분자와 분모를 각각 실측에서 가져온다</b>.
 * <ul>
 *   <li><b>분모</b> = 리포트의 {@code coverage.contextChars} - 그 회차가 실제로 모델에
 *       보낸 컨텍스트 길이. 기록된 값이다</li>
 *   <li><b>분자</b> = 그 회차 <b>자기 로그 원본</b>({@code reports/raw/*-loki-*.json})에
 *       접기를 켜고/끄고 어셈블한 차이. 두 팔의 입력이 같은 파일이므로 차이는 접기 뿐이다</li>
 * </ul>
 *
 * <p>여기서 재지 <b>않는</b> 것: 트레이스 압축(B-35). 회차 2~의 후보 트레이스 원본이 리포트와
 * 묶이지 않아 같은 방식으로 분자를 만들 수 없다. 미측정으로 남긴다.
 *
 * <p><b>토큰은 추정이다.</b> 여기서 재는 것은 문자 수이고, 환산은 §2.1 ②의
 * {@code chars x 0.524}(범위 0.511~0.532)다. 실측이 아니므로 인용 시 {@code 추정}으로 적는다.
 *
 * <p>실행: {@code ./gradlew test --tests '*FoldAbMeasureTool*' -Drca.tools=true}
 */
@EnabledIfSystemProperty(named = "rca.tools", matches = "true")
class FoldAbMeasureTool {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Path REPORTS = Path.of("reports");
    private static final Path RAW = REPORTS.resolve("raw");

    private static final List<String> APP = List.of("com.example");
    /** 운영 기본값과 같은 구성 - 프레임·잡음·근사반복 전부 켠 상태. */
    private static final LogFoldProperties FOLD_ALL = new LogFoldProperties(true, APP, 2, true, 3);

    /** §2.1 ② 추정 계수. 실측이 아니다. */
    private static final double TOK_PER_CHAR = 0.524;

    @Test
    void measureLogFoldAgainstFullContext() throws IOException {
        ContextAssembler off = assembler(LogFoldProperties.off());
        ContextAssembler on = assembler(FOLD_ALL);

        List<Path> reports = new ArrayList<>();
        try (Stream<Path> stream = Files.list(REPORTS)) {
            stream.filter(p -> p.getFileName().toString().endsWith(".json")).sorted().forEach(reports::add);
        }
        assertThat(reports).as("reports/*.json 이 있어야 한다").isNotEmpty();

        List<String> rawNames = new ArrayList<>();
        try (Stream<Path> stream = Files.list(RAW)) {
            stream.map(p -> p.getFileName().toString())
                    .filter(n -> n.endsWith(".json")).forEach(rawNames::add);
        }

        System.out.println();
        System.out.printf("%-32s %10s %10s %10s %10s %8s%n",
                "회차", "컨텍스트", "로그 접기전", "로그 접은후", "절감", "컨텍스트");
        System.out.println("-".repeat(88));

        long contextTotal = 0;
        long logBefore = 0;
        long logAfter = 0;
        int armed = 0;
        // 입력 모델 v0(traceId)와 v1(scan-* 자연어)은 다른 자다 - §1.2가 섞어서 말하지 못하게 한다.
        long ctxV0 = 0;
        long savedV0 = 0;
        int armedV0 = 0;
        long ctxV1 = 0;
        long savedV1 = 0;
        int armedV1 = 0;
        int skippedNoCoverage = 0;
        int skippedNoLogs = 0;

        for (Path reportPath : reports) {
            JsonNode report = MAPPER.readTree(Files.readString(reportPath, StandardCharsets.UTF_8));
            String id = report.path("traceId").asText(null);
            if (id == null || id.isBlank()) {
                skippedNoCoverage++;
                continue;
            }
            int contextChars = report.path("coverage").path("contextChars").asInt(-1);
            if (contextChars <= 0) {
                // 회차 1 이전 리포트에는 coverage가 없다 - 분모가 없으면 쓰지 않는다(§2.1 ③).
                skippedNoCoverage++;
                continue;
            }

            String stamp = stampOf(reportPath.getFileName().toString(), id);
            String errorWarn = latestBefore(rawNames, id, "loki-error-warn", stamp).orElse(null);
            String traceIdLog = latestBefore(rawNames, id, "loki-trace-id", stamp).orElse(null);
            if (errorWarn == null && traceIdLog == null) {
                skippedNoLogs++;
                continue;
            }

            String question = report.path("question").asText("(측정)");
            CollectedData logsOnly = logsOnly(id, errorWarn, traceIdLog);
            int before = off.assemble(logsOnly, question).length();
            int after = on.assemble(logsOnly, question).length();
            int saved = before - after;

            contextTotal += contextChars;
            logBefore += before;
            logAfter += after;
            armed++;
            if (id.startsWith("scan-")) {
                ctxV1 += contextChars;
                savedV1 += saved;
                armedV1++;
            } else {
                ctxV0 += contextChars;
                savedV0 += saved;
                armedV0++;
            }

            System.out.printf("%-32s %,10d %,10d %,10d %,10d %7.1f%%%n",
                    id, contextChars, before, after, saved, -100.0 * saved / contextChars);
        }

        System.out.println("-".repeat(88));
        assertThat(armed).as("coverage와 로그 원본이 둘 다 있는 회차가 있어야 한다").isPositive();

        long saved = logBefore - logAfter;
        System.out.printf("합계 %d개 회차 (coverage 없음 %d · 로그 원본 없음 %d)%n",
                armed, skippedNoCoverage, skippedNoLogs);
        System.out.printf("컨텍스트 %,d자 · 로그 절 %,d -> %,d자 (절감 %,d자)%n",
                contextTotal, logBefore, logAfter, saved);
        System.out.println();
        System.out.printf("로그 절 기준 절감률     %7.1f%%%n", -100.0 * saved / logBefore);
        System.out.printf("컨텍스트 기준 절감률   %7.1f%%   <- 섞인 값이다. 인용하지 말 것(아래 분리 집계를 쓴다)%n",
                -100.0 * saved / contextTotal);
        System.out.printf("추정 토큰 절감(chars x %.3f, 실측 아님): %,d tok%n", TOK_PER_CHAR, tok(saved));
        System.out.printf("회차당 평균: 컨텍스트 %,d자 · 절감 %,d자%n", contextTotal / armed, saved / armed);

        // 전체 합계는 자가 다른 두 모델을 섞은 값이라 인용하지 않는다. 나눠서 본다.
        System.out.println();
        System.out.printf("%-28s %6s %12s %12s %8s%n", "입력 모델", "회차", "컨텍스트", "절감", "절감률");
        System.out.println("-".repeat(70));
        if (armedV0 > 0) {
            System.out.printf("%-28s %6d %,12d %,12d %7.1f%%%n",
                    "v0 (traceId · 회차 1)", armedV0, ctxV0, savedV0, -100.0 * savedV0 / ctxV0);
        }
        if (armedV1 > 0) {
            System.out.printf("%-28s %6d %,12d %,12d %7.1f%%%n",
                    "v1 (자연어 · 회차 2~)", armedV1, ctxV1, savedV1, -100.0 * savedV1 / ctxV1);
        }
        System.out.println("-".repeat(70));
        System.out.println("인용할 값은 v1이다 - 현재 구성(자연어 입력 · 셀렉터 수정 후)에서 들어오는 로그량이 기준이다.");
        System.out.println();
    }

    private static ContextAssembler assembler(LogFoldProperties fold) {
        return new ContextAssembler(
                new CollectProperties(120, "content-service|auth-service|chat-service", "service_name",
                        1000, "15s", List.of(), 102_400, 30, 10, true),
                new ServiceGraphExtractor(), fold, TraceCompactProperties.off());
    }

    /** 로그 절만 떼어 재려고 트레이스·메트릭을 비운다 - 두 팔의 차이가 접기 외에 없게 한다. */
    private static CollectedData logsOnly(String id, String errorWarn, String traceIdLog) {
        return new CollectedData(id, null, errorWarn, traceIdLog,
                new LinkedHashMap<>(), new LinkedHashMap<>(), List.of(), Map.of());
    }

    private static String stampOf(String fileName, String id) {
        return fileName.substring(id.length() + 1, fileName.length() - ".json".length());
    }

    /** 같은 회차를 여러 번 조사했으면 <b>리포트 시각 이전의 가장 최근 덤프</b>가 그 실행이다. */
    private static Optional<String> latestBefore(
            List<String> rawNames, String id, String artifact, String reportStamp) throws IOException {

        String suffix = "-" + artifact + ".json";
        Optional<String> best = rawNames.stream()
                .filter(n -> n.startsWith(id + "-") && n.endsWith(suffix))
                .filter(n -> n.substring(id.length() + 1, n.length() - suffix.length())
                        .compareTo(reportStamp) <= 0)
                .max(Comparator.naturalOrder());

        if (best.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(Files.readString(RAW.resolve(best.get()), StandardCharsets.UTF_8));
    }

    private static long tok(long chars) {
        return Math.round(chars * TOK_PER_CHAR);
    }
}
