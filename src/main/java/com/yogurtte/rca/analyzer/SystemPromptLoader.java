package com.yogurtte.rca.analyzer;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 모드별 시스템 프롬프트를 마크다운 파일에서 읽는다.
 *
 * <p>모드는 두 가지다: "rca"(장애 원인 분석)와 "review"(정상 트레이스 성능 리뷰).
 * rca는 rca.prompt.path(기본 ./prompts/system-prompt.md)를 쓰고, review는 같은 디렉토리의
 * review-prompt.md를 쓴다. 재시작 없이 프롬프트를 튜닝할 수 있도록 조사할 때마다 다시 읽고,
 * 외부 파일이 없거나 읽기에 실패하면 jar에 포함된 기본 프롬프트로 대체한다.
 * 어떤 프롬프트로 실행했는지는 {@link Loaded#source()}로 리포트에 기록된다.
 *
 * <p>인스턴스는 {@link #from(PromptProperties)}으로 만든다 — 경로 파생과 기본 프롬프트
 * 적재(classpath IO)는 거기서 끝나고, 생성자는 결과만 받는다.
 */
@Slf4j
@RequiredArgsConstructor
public class SystemPromptLoader {

    /** 모드 -> classpath 기본 리소스. */
    private static final Map<String, String> DEFAULT_RESOURCES = Map.of(
            "rca", "prompts/system-prompt.md",
            "review", "prompts/review-prompt.md",
            "triage", "prompts/triage-prompt.md");

    /** 프롬프트 본문과 그 출처(외부 파일 경로 또는 classpath). */
    public record Loaded(String text, String source) {
    }

    private final Map<String, Path> externalPaths;
    private final Map<String, String> defaults;

    public static SystemPromptLoader from(PromptProperties properties) {
        Path rcaPath = (properties.path() == null || properties.path().isBlank())
                ? null
                : Path.of(properties.path());
        LinkedHashMap<String, Path> externalPaths = new LinkedHashMap<>();
        externalPaths.put("rca", rcaPath);
        externalPaths.put("review", rcaPath == null ? null : rcaPath.resolveSibling("review-prompt.md"));
        externalPaths.put("triage", rcaPath == null ? null : rcaPath.resolveSibling("triage-prompt.md"));

        LinkedHashMap<String, String> defaults = new LinkedHashMap<>();
        DEFAULT_RESOURCES.forEach((mode, resource) -> {
            try (InputStream in = SystemPromptLoader.class.getClassLoader().getResourceAsStream(resource)) {
                if (in == null) {
                    throw new IllegalStateException("classpath:" + resource + " 리소스가 없다");
                }
                defaults.put(mode, new String(in.readAllBytes(), StandardCharsets.UTF_8));
            } catch (IOException e) {
                throw new UncheckedIOException("기본 시스템 프롬프트를 읽지 못했다: " + resource, e);
            }
        });

        externalPaths.forEach((mode, path) -> {
            if (path != null && Files.isRegularFile(path)) {
                log.info("system prompt [{}]: {}", mode, path.toAbsolutePath());
            } else {
                log.info("system prompt [{}]: classpath:{} (외부 파일 {} 없음)", mode,
                        DEFAULT_RESOURCES.get(mode), path == null ? "미설정" : path);
            }
        });
        return new SystemPromptLoader(externalPaths, defaults);
    }

    /** 기존 호출 호환용 - rca 모드 프롬프트. */
    public Loaded load() {
        return load("rca");
    }

    public Loaded load(String mode) {
        if (!defaults.containsKey(mode)) {
            throw new IllegalArgumentException("지원하지 않는 프롬프트 모드: " + mode);
        }
        Path externalPath = externalPaths.get(mode);
        if (externalPath != null && Files.isRegularFile(externalPath)) {
            try {
                return new Loaded(Files.readString(externalPath, StandardCharsets.UTF_8), externalPath.toString());
            } catch (IOException e) {
                log.warn("외부 프롬프트 {} 읽기 실패, 기본 프롬프트로 대체: {}", externalPath, e.getMessage());
            }
        }
        return new Loaded(defaults.get(mode), "classpath:" + DEFAULT_RESOURCES.get(mode));
    }
}
