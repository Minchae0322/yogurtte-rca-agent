package com.yogurtte.rca.client;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.time.Instant;
import java.time.ZoneOffset;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.yogurtte.rca.report.ReportProperties;

/** Dumps every raw external response to ./reports/raw/ so runs can be re-scored later. */
@Component
public class RawResponseStore {

    private static final Logger log = LoggerFactory.getLogger(RawResponseStore.class);
    private static final DateTimeFormatter TS =
            DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss").withZone(ZoneOffset.UTC);

    private final Path rawDir;

    public RawResponseStore(ReportProperties properties) {
        this.rawDir = Path.of(properties.dir(), "raw");
    }

    public void save(String traceId, String name, String body) {
        if (body == null) {
            return;
        }
        try {
            Files.createDirectories(rawDir);
            var file = rawDir.resolve("%s-%s-%s.json".formatted(traceId, TS.format(Instant.now()), sanitize(name)));
            Files.writeString(file, body, StandardCharsets.UTF_8);
        } catch (IOException e) {
            // Persisting raw payloads is best-effort; never fail an investigation over it.
            log.warn("failed to save raw response {} for trace {}: {}", name, traceId, e.getMessage());
        }
    }

    private static String sanitize(String name) {
        return name.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}
