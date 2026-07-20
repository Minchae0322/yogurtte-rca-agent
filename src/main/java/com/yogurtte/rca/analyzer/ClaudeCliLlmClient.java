package com.yogurtte.rca.analyzer;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Runs the locally installed `claude` CLI, for driving a subscription account without an API key.
 *
 * <p>The prompt goes in over stdin rather than as an argv value: an assembled RCA context runs to
 * tens of kilobytes and would blow past the OS command-line length limit.
 */
@Component
@ConditionalOnProperty(name = "rca.llm.provider", havingValue = "claude-cli", matchIfMissing = true)
public class ClaudeCliLlmClient implements LlmClient {

    private static final Logger log = LoggerFactory.getLogger(ClaudeCliLlmClient.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final String command;
    private final long timeoutSeconds;

    public ClaudeCliLlmClient(
            @Value("${rca.llm.claude-cli.command:claude}") String command,
            @Value("${rca.llm.claude-cli.timeout-seconds:120}") long timeoutSeconds) {
        this.command = (command == null || command.isBlank()) ? "claude" : command;
        this.timeoutSeconds = timeoutSeconds;
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
                // process teardown races the reader; stderr is diagnostic only
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

            // usage is absent on some CLI versions / output shapes - record -1 rather than guessing.
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
