package com.yogurtte.rca.client;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.time.Instant;
import java.time.ZoneOffset;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** 외부 API 원본 응답을 전부 ./reports/raw/에 남겨, 나중에 실행을 재채점할 수 있게 한다. */
@Slf4j
@RequiredArgsConstructor
public class RawResponseStore {

    private static final DateTimeFormatter TS =
            DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss").withZone(ZoneOffset.UTC);

    private final Path rawDir;

    public void save(String traceId, String name, String body) {
        if (body == null) {
            return;
        }
        try {
            Files.createDirectories(rawDir);
            Path file = rawDir.resolve("%s-%s-%s.json".formatted(traceId, TS.format(Instant.now()), sanitize(name)));
            Files.writeString(file, body, StandardCharsets.UTF_8);
        } catch (IOException e) {
            // 원본 저장은 best-effort다; 이것 때문에 조사를 실패시키지 않는다.
            log.warn("failed to save raw response {} for trace {}: {}", name, traceId, e.getMessage());
        }
    }

    private static String sanitize(String name) {
        return name.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}
