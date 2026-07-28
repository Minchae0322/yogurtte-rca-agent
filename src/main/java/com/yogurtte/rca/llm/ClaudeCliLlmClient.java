package com.yogurtte.rca.llm;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 로컬에 설치된 `claude` CLI를 실행한다. API 키 없이 구독 계정으로 쓰기 위한 provider.
 *
 * <p>프롬프트는 argv가 아니라 stdin으로 넘긴다: 조립된 RCA 컨텍스트는 수십 KB라
 * OS 커맨드라인 길이 제한을 넘기기 때문이다.
 */
@Component
@ConditionalOnProperty(name = "rca.llm.provider", havingValue = "claude-cli", matchIfMissing = true)
public class ClaudeCliLlmClient implements LlmClient {

    private static final Logger log = LoggerFactory.getLogger(ClaudeCliLlmClient.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** {@code rca.llm.claude-cli.model}이 비었을 때 쓰는 값. 절대 CLI 기본값에 맡기지 않는다. */
    private static final String DEFAULT_MODEL = "claude-opus-5";

    private final String command;
    private final String model;
    private final long timeoutSeconds;
    private final File sandbox;
    private final AtomicBoolean modelNotReportedWarned = new AtomicBoolean(false);

    public ClaudeCliLlmClient(LlmProperties properties) {
        var cli = properties.claudeCli();
        var command = cli == null ? null : cli.command();
        var configuredModel = cli == null ? null : cli.model();
        this.command = (command == null || command.isBlank()) ? "claude" : command;
        this.model = (configuredModel == null || configuredModel.isBlank()) ? DEFAULT_MODEL : configuredModel;
        this.timeoutSeconds = cli == null ? 120 : cli.timeoutSeconds();
        this.sandbox = createSandbox();
        log.info("claude CLI model pinned: {}", this.model);
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
    private static File createSandbox() {
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
     * 응답이 실제로 어떤 모델로 처리됐는지. CLI 출력 형태가 버전마다 달라 두 자리를 본 뒤,
     * 없으면 우리가 {@code --model}로 지정한 값으로 떨어진다(지정이 거부되면 CLI가 실패하므로
     * 그 경우 요청값 = 실제값으로 봐도 된다). 보고가 없으면 한 번 경고를 남긴다.
     */
    private String reportedModel(com.fasterxml.jackson.databind.JsonNode root) {
        var direct = root.path("model").asText(null);
        if (direct != null && !direct.isBlank()) {
            return direct;
        }
        var modelUsage = root.path("modelUsage");
        if (modelUsage.isObject() && modelUsage.fieldNames().hasNext()) {
            return modelUsage.fieldNames().next();
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
