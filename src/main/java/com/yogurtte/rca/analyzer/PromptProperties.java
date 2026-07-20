package com.yogurtte.rca.analyzer;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** path가 가리키는 외부 마크다운 파일이 있으면 클래스패스 기본 프롬프트 대신 그것을 쓴다. */
@ConfigurationProperties("rca.prompt")
public record PromptProperties(String path) {
}
