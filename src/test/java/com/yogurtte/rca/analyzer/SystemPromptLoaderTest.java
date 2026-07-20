package com.yogurtte.rca.analyzer;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SystemPromptLoaderTest {

    @Test
    void fallsBackToClasspathDefaultWhenNoExternalFile(@TempDir Path dir) {
        var loader = new SystemPromptLoader(new PromptProperties(dir.resolve("없는파일.md").toString()));

        var prompt = loader.load();

        assertThat(prompt.text()).contains("너는 SRE다");
        assertThat(prompt.source()).isEqualTo("classpath:prompts/system-prompt.md");
    }

    /** 튜닝 루프의 핵심: 파일을 고치면 재시작 없이 다음 load()에 바로 반영된다. */
    @Test
    void reReadsExternalFileOnEveryLoad(@TempDir Path dir) throws Exception {
        var skill = dir.resolve("skill.md");
        Files.writeString(skill, "버전 1: 너는 SRE다.");
        var loader = new SystemPromptLoader(new PromptProperties(skill.toString()));

        assertThat(loader.load().text()).isEqualTo("버전 1: 너는 SRE다.");
        assertThat(loader.load().source()).isEqualTo(skill.toString());

        Files.writeString(skill, "버전 2: 반증 데이터를 먼저 찾아라.");

        assertThat(loader.load().text()).isEqualTo("버전 2: 반증 데이터를 먼저 찾아라.");
    }
}
