package com.yogurtte.rca.triage;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 스윕 원본 JSON에서 <b>"언제 무엇이 이상했나"</b>를 뽑는다.
 *
 * <p>새로 만드는 로직이 아니다 — {@code EvidenceExtractor}가 이미 메트릭의 0 구간을 뽑는다.
 * <b>위치가 틀렸을 뿐이다.</b> 그쪽은 창이 정해진 <i>뒤</i>(분석 단계)에 돌아서, 정작 창을
 * 정할 때는 쓸 수 없다. 여기로 옮기고 결측 구간 검출을 더했다.
 *
 * <p><b>임계값을 하나도 쓰지 않는다.</b> 변화·부재·존재만 센다. 전 구간 상수는 신호가 아니고,
 * 절대 크기로 판정하지 않는다.
 */
@Component
public class SignalExtractor {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** {@code min_over_time(up[5m])} → {@code up}. 집계 함수를 벗겨 지표 이름만 남긴다. */
    private static final Pattern METRIC_NAME = Pattern.compile("([a-zA-Z_:][a-zA-Z0-9_:]*)\\s*[\\[{)]");
    private static final Pattern WRAPPER = Pattern.compile(
            "^(min_over_time|max_over_time|avg_over_time|rate|increase|sum|avg|count)\\s*\\(");

    public List<Signal> extract(SurveyResult survey, Duration lookback) {
        var signals = new ArrayList<Signal>();
        fromTraces(survey, signals);
        fromLogRates(survey.logRatesJson(), lookback, signals);
        survey.metricsJson().forEach((query, body) -> fromMetric(query, body, signals));
        return List.copyOf(signals);
    }

    // --- Tempo: 검색 결과의 존재 자체가 신호다 (쿼리가 이미 이상을 정의한다) ---

    private void fromTraces(SurveyResult survey, List<Signal> out) {
        for (var hit : survey.traceHits()) {
            // 깨진 행은 시각을 믿을 수 없어 창 계산에 넣으면 안 된다. 후보 목록에는 남아 있다.
            if (!hit.trusted() || hit.startedAt() == null) {
                continue;
            }
            var end = hit.startedAt().plusMillis(Math.max(0, hit.durationMs()));
            var what = "%s %s %,dms (%s 채널)".formatted(
                    hit.rootServiceName(), hit.rootTraceName(), hit.durationMs(), hit.channel());
            out.add(new Signal(hit.startedAt(), end, Signal.Channel.TEMPO, Signal.Precision.EXACT,
                    hit.rootServiceName(), hit.rootTraceName(), what, hit.traceId()));
        }
    }

    // --- Loki: 버킷에 값이 있는 것 자체가 신호다 ---

    /**
     * {@code count_over_time(...[5m])} 은 <b>0건 구간에 샘플을 만들지 않는다.</b> 그래서 값이
     * 존재하는 것이 곧 "그 5분에 에러가 있었다"이고, 임계값이 필요 없다.
     *
     * <p>구간은 {@code [ts - lookback, ts]} 다. {@code ts} 하나로 두면 최대 lookback만큼 어긋난다.
     */
    private void fromLogRates(String body, Duration lookback, List<Signal> out) {
        var result = readResult(body);
        if (result == null) {
            return;
        }
        for (var series : result) {
            var metric = series.path("metric");
            var service = firstNonBlank(metric.path("service_name").asText(null), "?");
            var exception = metric.path("exc").asText(null);   // B-29 적용 시 채워진다
            var values = series.get("values");
            if (values == null || !values.isArray()) {
                continue;
            }
            for (var point : values) {
                if (!point.isArray() || point.size() < 2) {
                    continue;
                }
                var at = Instant.ofEpochSecond(point.get(0).asLong());
                var raw = point.get(1).asText("0");
                if (isZero(raw)) {
                    continue;
                }
                var what = "ERROR/WARN %s건 (%s ~ %s)".formatted(raw, at.minus(lookback), at);
                out.add(new Signal(at.minus(lookback), at, Signal.Channel.LOKI,
                        Signal.Precision.BUCKET, service,
                        firstNonBlank(exception, "ERROR/WARN"), what, "loki-rate"));
            }
        }
    }

    // --- Mimir: 변화·0 구간·결측만 신호다 ---

    /**
     * 세 가지를 뽑는다 — <b>0이었던 구간</b> · <b>값이 변한 지점</b> · <b>시리즈가 사라진 구간</b>.
     * 전 구간 상수는 신호가 아니다(에이전트도 같은 기준을 쓴다:
     * <i>"websocket_active_users=0이 전 구간 상수 — 변화가 없으므로 무관"</i>).
     */
    private void fromMetric(String query, String body, List<Signal> out) {
        var result = readResult(body);
        if (result == null) {
            return;
        }
        var name = metricNameOf(query);
        for (var series : result) {
            var metric = series.path("metric");
            var resource = firstNonBlank(
                    metric.path("job").asText(null),
                    firstNonBlank(metric.path("application").asText(null),
                            firstNonBlank(metric.path("instance").asText(null), "?")));
            var values = series.get("values");
            if (values == null || !values.isArray() || values.size() < 2) {
                continue;
            }

            Instant zeroFrom = null;
            Instant zeroTo = null;
            Instant prevAt = null;
            Double prev = null;
            var step = inferStep(values);

            for (var point : values) {
                if (!point.isArray() || point.size() < 2) {
                    continue;
                }
                var at = Instant.ofEpochSecond(point.get(0).asLong());
                var value = parseDouble(point.get(1).asText(null));
                if (value == null) {
                    continue;
                }

                // 0 구간
                if (value == 0.0) {
                    zeroFrom = zeroFrom == null ? at : zeroFrom;
                    zeroTo = at;
                } else if (zeroFrom != null) {
                    out.add(metricSignal(name, resource, zeroFrom, zeroTo,
                            "%s 가 0이었다 (%s ~ %s)".formatted(name, zeroFrom, zeroTo), query));
                    zeroFrom = null;
                }

                // 값 변화
                if (prev != null && prev != value) {
                    out.add(metricSignal(name, resource, prevAt, at,
                            "%s %s → %s".formatted(name, trim(prev), trim(value)), query));
                }

                // 결측 — 샘플이 step보다 크게 벌어졌다
                if (prevAt != null && step != null
                        && Duration.between(prevAt, at).compareTo(step.multipliedBy(2)) > 0) {
                    out.add(metricSignal(name, resource, prevAt, at,
                            "%s 시리즈가 %s ~ %s 구간에 없다 (스크레이프 대상 부재)".formatted(name, prevAt, at),
                            query));
                }

                prevAt = at;
                prev = value;
            }
            if (zeroFrom != null) {
                out.add(metricSignal(name, resource, zeroFrom, zeroTo,
                        "%s 가 0이었다 (%s ~ %s)".formatted(name, zeroFrom, zeroTo), query));
            }
        }
    }

    private static Signal metricSignal(String name, String resource, Instant from, Instant to,
                                       String what, String query) {
        return new Signal(from, to, Signal.Channel.MIMIR, Signal.Precision.BUCKET,
                resource, name, what, query);
    }

    /** 샘플 간격은 응답에서 추론한다 — 설정된 step과 실제가 다를 수 있다. */
    private static Duration inferStep(JsonNode values) {
        if (values.size() < 2) {
            return null;
        }
        var a = values.get(0).get(0).asLong();
        var b = values.get(1).get(0).asLong();
        return b > a ? Duration.ofSeconds(b - a) : null;
    }

    static String metricNameOf(String query) {
        var q = query == null ? "" : query.trim();
        var stripped = WRAPPER.matcher(q).replaceFirst("");
        var matcher = METRIC_NAME.matcher(stripped);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return stripped.isBlank() ? "metric" : stripped;
    }

    private static boolean isZero(String raw) {
        var value = parseDouble(raw);
        return value == null || value == 0.0;
    }

    private static String trim(double value) {
        return value == Math.rint(value) ? String.valueOf((long) value) : String.valueOf(value);
    }

    private static JsonNode readResult(String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
        try {
            var result = MAPPER.readTree(body).path("data").path("result");
            return result.isArray() ? result : null;
        } catch (Exception e) {
            return null;
        }
    }

    private static Double parseDouble(String raw) {
        try {
            return raw == null ? null : Double.parseDouble(raw);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String firstNonBlank(String value, String fallback) {
        return (value == null || value.isBlank()) ? fallback : value;
    }
}
