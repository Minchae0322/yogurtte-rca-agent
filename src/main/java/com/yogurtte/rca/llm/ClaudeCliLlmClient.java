package com.yogurtte.rca.llm;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 로컬에 설치된 `claude` CLI를 실행한다. API 키 없이 구독 계정으로 쓰기 위한 provider.
 *
 * <p>프롬프트는 argv가 아니라 stdin으로 넘긴다: 조립된 RCA 컨텍스트는 수십 KB라
 * OS 커맨드라인 길이 제한을 넘기기 때문이다.
 */
@Slf4j
@RequiredArgsConstructor
public class ClaudeCliLlmClient implements LlmClient {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** {@code rca.llm.claude-cli.model}이 비었을 때 쓰는 값. 절대 CLI 기본값에 맡기지 않는다. */
    static final String DEFAULT_MODEL = "claude-opus-5";

    /** 오버헤드 프로브에 쓰는 최소 프롬프트. 페이로드 몫이 오차에 묻힐 만큼 작아야 한다. */
    private static final String OVERHEAD_PROBE = ".";

    private final String command;
    private final String model;
    private final long timeoutSeconds;
    private final boolean probeOverhead;
    private final File sandbox;
    private final AtomicBoolean modelNotReportedWarned = new AtomicBoolean(false);

    /**
     * 1자 프롬프트를 <b>본 호출과 같은 조건</b>(같은 명령·모델·샌드박스)으로 한 번 던져
     * 이 회차의 고정 오버헤드를 실측한다.
     *
     * <p>측정된 값에는 프롬프트 자체(약 6 tok — {@code "."} + 구분자)가 포함되므로 오버헤드를
     * 그만큼 <b>과대</b>평가한다. 실측 편차(±320, 1.5%)의 2% 수준이라 보정하지 않는다.
     *
     * <p>실패해도 조사를 중단시키지 않는다 — -1로 기록하고, 그러면 그 회차는 다른 날 상수에
     * 기댄 추정으로 돌아간다(그 사실이 리포트에 드러난다).
     */
    @Override
    public long overheadTokens() {
        if (!probeOverhead) {
            return -1L;
        }
        try {
            var probe = analyze(OVERHEAD_PROBE, "");
            if (probe.numTurns() > 1) {
                log.warn("오버헤드 프로브가 {}턴을 돌았다 — usage가 턴 누적이라 값을 쓸 수 없다", probe.numTurns());
                return -1L;
            }
            log.info("CLI 고정 오버헤드 실측: {} tok (이 회차 기준)", probe.inputTokens());
            return probe.inputTokens();
        } catch (Exception e) {
            log.warn("오버헤드 프로브 실패 — contextTokens는 추정으로 남는다: {}", e.toString());
            return -1L;
        }
    }

    /**
     * CLI를 레포 밖 빈 디렉터리에서 띄우기 위한 작업 공간.
     *
     * <p>ProcessBuilder는 JVM의 cwd(= 레포 루트)를 물려주는데, claude CLI는 cwd의 {@code CLAUDE.md}와
     * {@code .claude/}를 컨텍스트로 자동 로드하고 그 아래 파일도 열 수 있다. 이 레포에는 문항별
     * 정답 문서({@code docs/<문항>/round-N.md})와 채점 대장이 있으므로, 격리하지 않으면 피험자가
     * 답안지를 든 채 시험을 보는 셈이 된다. 2026-07-27 AU-2 회차 1에서 실측 확인했다.
     * 자세한 경위는 {@code docs/v0.1-plan.md} 0절.
     *
     * <p>실패하면 예외를 던진다. 조용히 레포 cwd로 떨어지면 오염을 눈치채지 못한 채 측정이
     * 계속되는데, 그게 격리 실패보다 나쁘다.
     */
    static File createSandbox() {
        try {
            var dir = Files.createTempDirectory("rca-cli-sandbox-");
            dir.toFile().deleteOnExit();
            log.info("claude CLI sandbox (레포 컨텍스트 격리): {}", dir);
            return dir.toFile();
        } catch (IOException e) {
            throw new IllegalStateException(
                    "claude CLI 격리용 임시 디렉터리를 만들지 못했다 — 레포 cwd로 실행하면 "
                            + "CLAUDE.md·docs가 컨텍스트로 새어 블라인드 조사가 무효가 된다", e);
        }
    }

    @Override
    public LlmResult analyze(String systemPrompt, String context) {
        var started = System.currentTimeMillis();
        var prompt = systemPrompt + "\n\n---\n\n" + context;

        Process process;
        try {
            // --model을 반드시 넘긴다: 생략하면 그날의 CLI 기본 모델로 돌아 회차마다 다른 모델이
            // 채점될 수 있고, 토크나이저도 달라져 토큰 비교까지 무너진다.
            process = new ProcessBuilder(command, "-p", "--model", model, "--output-format", "json")
                    .directory(sandbox)
                    .start();
        } catch (Exception e) {
            throw new IllegalStateException(
                    "failed to start claude CLI '" + command + "': " + e.getMessage(), e);
        }

        var stderr = new StringBuilder();
        var stderrReader = new Thread(() -> {
            try (var reader = new BufferedReader(
                    new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8))) {
                reader.lines().forEach(line -> stderr.append(line).append('\n'));
            } catch (Exception ignored) {
                // 프로세스 종료와 reader가 경합할 수 있다; stderr는 어차피 진단용이다
            }
        });
        stderrReader.setDaemon(true);
        stderrReader.start();

        String stdout;
        int exitCode;
        try {
            try (var writer = new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8)) {
                writer.write(prompt);
            }
            try (var reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                stdout = reader.lines().reduce(new StringBuilder(), StringBuilder::append, StringBuilder::append)
                        .toString();
            }
            if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new IllegalStateException(
                        "claude CLI timed out after " + timeoutSeconds + "s");
            }
            exitCode = process.exitValue();
            stderrReader.join(1000);
        } catch (InterruptedException e) {
            process.destroyForcibly();
            Thread.currentThread().interrupt();
            throw new IllegalStateException("claude CLI interrupted", e);
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            process.destroyForcibly();
            throw new IllegalStateException("claude CLI failed: " + e.getMessage(), e);
        }

        if (exitCode != 0) {
            throw new IllegalStateException(
                    "claude CLI exited with code " + exitCode + "; stderr: " + stderr.toString().trim());
        }

        var elapsed = System.currentTimeMillis() - started;
        return parse(stdout, elapsed);
    }

    private LlmResult parse(String stdout, long elapsed) {
        try {
            var root = MAPPER.readTree(stdout);
            if (root.path("is_error").asBoolean(false)) {
                throw new IllegalStateException("claude CLI reported an error: " + root.path("result").asText());
            }
            var text = root.has("result") ? root.get("result").asText() : stdout;

            // 일부 CLI 버전/출력 형태에는 usage가 없다 - 추측하지 말고 -1로 기록한다.
            var usage = root.path("usage");
            var outputTokens = usage.has("output_tokens") ? usage.get("output_tokens").asLong() : -1L;

            // 큰 컨텍스트는 프롬프트 캐시로 들어가 input_tokens가 아니라 cache_* 필드에 잡힌다.
            // 셋을 합쳐야 실제 입력 토큰이 나온다(안 그러면 in=2처럼 실제보다 훨씬 작게 보인다).
            // 합산과 별개로 내역도 보존한다 - cache read는 신규 입력의 약 1/10 값이라, 합산만
            // 남기면 비용 편차가 컨텍스트 크기 탓인지 캐시 히트율 탓인지 사후에 가릴 수 없다.
            var cacheRead = usage.has("cache_read_input_tokens")
                    ? usage.get("cache_read_input_tokens").asLong() : -1L;
            var cacheCreation = usage.has("cache_creation_input_tokens")
                    ? usage.get("cache_creation_input_tokens").asLong() : -1L;
            long inputTokens = -1L;
            if (usage.has("input_tokens")) {
                inputTokens = usage.get("input_tokens").asLong()
                        + Math.max(cacheRead, 0)
                        + Math.max(cacheCreation, 0);
            }

            // CLI는 이번 호출 비용을 달러로 알려준다. 없으면 -1.
            var costUsd = root.has("total_cost_usd") ? root.get("total_cost_usd").asDouble() : -1.0;

            // 턴 수: 1이 아니면 CLI가 자체 도구 루프를 돌았다는 뜻이고, 그러면 usage가 마지막
            // 턴만 담고 비용은 전 턴 합계일 수 있다("단일 패스" 전제가 깨진 것). 반드시 기록한다.
            var numTurns = root.has("num_turns") ? root.get("num_turns").asInt() : -1;

            return new LlmResult(text, inputTokens, outputTokens, cacheRead, cacheCreation,
                    reportedModel(root), numTurns, elapsed, costUsd);
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            log.warn("could not parse claude CLI JSON output, using raw stdout: {}", e.getMessage());
            return new LlmResult(stdout, -1, -1, -1, -1, model, -1, elapsed, -1.0);
        }
    }

    /**
     * 응답이 실제로 어떤 모델로 처리됐는지.
     *
     * <p><b>{@code modelUsage}에는 모델이 여러 개 들어온다.</b> CLI가 본답변과 별개로 보조
     * 작업에 작은 모델을 함께 쓰기 때문이다. 첫 키를 그냥 집으면 <b>요청하지도 않은 모델이
     * 리포트에 기록된다</b> — AP-1 회차 2가 실제로 {@code claude-opus-5}로 돌았는데
     * {@code claude-haiku-4-5}로 기록됐고, 하마터면 "모델 고정이 깨졌다"는 오진으로
     * 회차를 폐기할 뻔했다. 그래서 <b>요청한 모델이 목록에 있으면 그것을 정답으로</b> 삼고,
     * 없을 때만 실제 대체가 일어난 것으로 보고 경고한다.
     *
     * <p>토큰·비용은 이 메서드와 무관하다 — 최상위 {@code usage}가 요청 모델의 실측이고,
     * {@code total_cost_usd}만 보조 모델 몫을 포함한다(실측상 본답변의 0.1% 미만).
     */
    private String reportedModel(com.fasterxml.jackson.databind.JsonNode root) {
        var direct = root.path("model").asText(null);
        if (direct != null && !direct.isBlank()) {
            return direct;
        }
        var modelUsage = root.path("modelUsage");
        if (modelUsage.isObject() && modelUsage.fieldNames().hasNext()) {
            var used = new ArrayList<String>();
            modelUsage.fieldNames().forEachRemaining(used::add);
            var matched = used.stream().filter(name -> name.startsWith(model)).findFirst();
            if (matched.isPresent()) {
                if (used.size() > 1) {
                    log.debug("claude CLI가 보조 모델을 함께 사용했다: {} (본답변 {})", used, matched.get());
                }
                return matched.get();
            }
            // 요청한 모델이 아예 없다 = 진짜 대체가 일어났다. 이건 회차를 무효로 볼 사유다.
            log.warn("요청 모델 '{}'이 응답의 modelUsage에 없다 — 실제 사용 {}. "
                    + "회차 간 점수·토큰 비교가 성립하지 않으니 이 회차는 별도 구성으로 기록할 것", model, used);
            return used.get(0);
        }
        if (modelNotReportedWarned.compareAndSet(false, true)) {
            log.warn("claude CLI 응답에 모델 식별자가 없다 - 요청값 '{}'으로 기록한다", model);
        }
        return model;
    }

    @Override
    public String provider() {
        return "claude-cli";
    }
}
