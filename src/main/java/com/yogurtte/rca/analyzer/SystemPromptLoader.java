package com.yogurtte.rca.analyzer;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 시스템 프롬프트를 마크다운 파일에서 읽는다.
 *
 * <p>rca.prompt.path의 외부 파일이 있으면 그것을 쓰고, 재시작 없이 프롬프트를 튜닝할 수 있도록
 * 조사할 때마다 다시 읽는다. 외부 파일이 없거나 읽기에 실패하면 jar에 포함된 기본 프롬프트
 * (classpath:prompts/system-prompt.md)로 대체한다. 어떤 프롬프트로 실행했는지는
 * {@link Loaded#source()}로 리포트에 기록된다.
 */
@Component
public class SystemPromptLoader {

    private static final Logger log = LoggerFactory.getLogger(SystemPromptLoader.class);
    private static final String DEFAULT_RESOURCE = "prompts/system-prompt.md";

    /** 프롬프트 본문과 그 출처(외부 파일 경로 또는 classpath). */
    public record Loaded(String text, String source) {
    }

    private final Path externalPath;
    private final String defaultPrompt;

    public SystemPromptLoader(PromptProperties properties) {
        this.externalPath = (properties.path() == null || properties.path().isBlank())
                ? null
                : Path.of(properties.path());

        try (var in = getClass().getClassLoader().getResourceAsStream(DEFAULT_RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("classpath:" + DEFAULT_RESOURCE + " 리소스가 없다");
            }
            this.defaultPrompt = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("기본 시스템 프롬프트를 읽지 못했다", e);
        }

        if (externalPath != null && Files.isRegularFile(externalPath)) {
            log.info("system prompt: {}", externalPath.toAbsolutePath());
        } else {
            log.info("system prompt: classpath:{} (외부 파일 {} 없음)", DEFAULT_RESOURCE,
                    externalPath == null ? "미설정" : externalPath);
        }
    }

    public Loaded load() {
        if (externalPath != null && Files.isRegularFile(externalPath)) {
            try {
                return new Loaded(Files.readString(externalPath, StandardCharsets.UTF_8), externalPath.toString());
            } catch (IOException e) {
                log.warn("외부 프롬프트 {} 읽기 실패, 기본 프롬프트로 대체: {}", externalPath, e.getMessage());
            }
        }
        return new Loaded(defaultPrompt, "classpath:" + DEFAULT_RESOURCE);
    }
}
