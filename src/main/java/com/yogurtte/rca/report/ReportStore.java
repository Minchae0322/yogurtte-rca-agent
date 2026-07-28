package com.yogurtte.rca.report;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/**
 * 조사마다 ./reports/{traceId}-{ts}.json(기계 분석용)과 .md(사람이 읽는 보고서)를 함께 쓴다.
 * 어떤 notifier든 전달 전에 리포트를 먼저 저장한다.
 */
@Component
public class ReportStore {

    private static final DateTimeFormatter TS =
            DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss").withZone(ZoneOffset.UTC);

    private final ObjectMapper mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .enable(SerializationFeature.INDENT_OUTPUT);

    private final Path dir;

    public ReportStore(ReportProperties properties) {
        this.dir = Path.of(properties.dir());
    }

    /** 저장된 두 파일 경로. */
    public record Saved(Path json, Path markdown) {
    }

    public Saved save(RcaReport report) throws IOException {
        Files.createDirectories(dir);
        // 탐색으로 시작한 조사는 traceId가 없을 수 있다 — 트레이스가 생성되지 않는 장애가 실재한다.
        var key = (report.traceId() == null || report.traceId().isBlank()) ? "scan" : report.traceId();
        var base = "%s-%s".formatted(key, TS.format(Instant.now()));

        var json = dir.resolve(base + ".json");
        Files.writeString(json, mapper.writeValueAsString(report), StandardCharsets.UTF_8);

        var markdown = dir.resolve(base + ".md");
        Files.writeString(markdown, ReportMarkdown.render(report), StandardCharsets.UTF_8);

        return new Saved(json, markdown);
    }
}
