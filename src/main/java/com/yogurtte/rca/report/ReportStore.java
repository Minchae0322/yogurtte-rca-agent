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

/** Writes ./reports/{traceId}-{ts}.json. Every notifier persists the report before delivering it. */
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

    public Path save(RcaReport report) throws IOException {
        Files.createDirectories(dir);
        var file = dir.resolve("%s-%s.json".formatted(report.traceId(), TS.format(Instant.now())));
        Files.writeString(file, mapper.writeValueAsString(report), StandardCharsets.UTF_8);
        return file;
    }
}
