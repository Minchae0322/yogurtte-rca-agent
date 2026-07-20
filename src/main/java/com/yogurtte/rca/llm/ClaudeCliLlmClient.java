package com.yogurtte.rca.llm;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

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

    private final String command;
    private final long timeoutSeconds;

    public ClaudeCliLlmClient(LlmProperties properties) {
        var cli = properties.claudeCli();
        var command = cli == null ? null : cli.command();
        this.command = (command == null || command.isBlank()) ? "claude" : command;
        this.timeoutSeconds = cli == null ? 120 : cli.timeoutSeconds();
    }

    @Override
    public LlmResult analyze(String systemPrompt, String context) {
        var started = System.currentTimeMillis();
        var prompt = systemPrompt + "\n\n---\n\n" + context;

        Process process;
        try {
            process = new ProcessBuilder(command, "-p", "--output-format", "json").start();
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
            var inputTokens = usage.has("input_tokens") ? usage.get("input_tokens").asLong() : -1L;
            var outputTokens = usage.has("output_tokens") ? usage.get("output_tokens").asLong() : -1L;

            return new LlmResult(text, inputTokens, outputTokens, elapsed);
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            log.warn("could not parse claude CLI JSON output, using raw stdout: {}", e.getMessage());
            return new LlmResult(stdout, -1, -1, elapsed);
        }
    }

    @Override
    public String provider() {
        return "claude-cli";
    }
}
