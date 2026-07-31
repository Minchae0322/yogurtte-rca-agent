package com.yogurtte.rca.report;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * 리포트에 <b>모델의 서술만</b> 남으면 나중에 그 서술이 맞았는지 확인할 수 없다.
 * 회고가 되려면 탐색의 선택 근거와 관측값 원문이 같은 문서에 있어야 한다.
 */
class ReportMarkdownTest {

    @Test
    void 탐색_근거와_관측값_원문이_리포트에_함께_실린다() {
        var markdown = ReportMarkdown.render(sample());

        // 탐색: 무엇을 왜 골랐는지 + 고르지 않은 후보까지
        assertThat(markdown).contains("## 탐색 (Triage)");
        assertThat(markdown).contains("어젯밤 (어제 18:00~오늘 06:00 Asia/Seoul)");
        assertThat(markdown).contains("abc123` ←선택").contains("def456");

        // 관측 증거: 호출 그래프·span·로그 원문·메트릭
        assertThat(markdown).contains("## 관측 증거 (Evidence)");
        // 그래프가 리포트에 안 남으면 그래프를 준 효과를 채점에서 귀속시킬 수 없다.
        assertThat(markdown).contains("### 호출 그래프 (트레이스에서 추출)");
        assertThat(markdown).contains("kafka/user.notifications --messaging--> chat-service");
        assertThat(markdown).contains("notification-consume").contains("30000.00");
        assertThat(markdown).contains("MongoTimeoutException");
        assertThat(markdown).contains("mongodb_up");
        // 0으로 꺾인 구간은 굵게 — 부재가 결정적 신호인 장애가 있다.
        assertThat(markdown).contains("**2026-07-27T17:31:00Z ~ 2026-07-27T17:32:00Z**");

        // 원본으로 되짚어갈 경로
        assertThat(markdown).contains("reports/raw/abc123-*.json");

        // 토큰 축 검산의 입력 — 탐색 단계도 chars를 남겨야 chars × 비율 경로가 성립한다
        assertThat(markdown).contains("컨텍스트 31,500 + 프롬프트 2,200 = **33,700**");

        // 개선 지표는 이 회차 안에서 닫혀야 한다 — 다른 날 상수를 빌리지 않는다.
        assertThat(markdown).contains("### 토큰 축 (개선 지표)");
        assertThat(markdown).contains("overheadTokens 21,247 tok — 이 회차에 실측");
        assertThat(markdown).contains("| 탐색 | 43,025 | 33,700 | 21,778 |");   // 43,025 − 21,247
        assertThat(markdown).contains("| 분석 | 42,651 | 42,181 | 21,404 |");   // 42,651 − 21,247
        // API 키가 없는 경로임을 숨기지 않는다
        assertThat(markdown).contains("구독 CLI 경로엔 API 키가 없다");
    }

    @Test
    void 오버헤드를_못_재면_추정임을_드러낸다() {
        var base = sample();
        var c = base.coverage();
        var noProbe = new RcaReport.Coverage(
                c.windowStart(), c.windowEnd(), c.windowSeconds(), c.traceBytes(), c.traceSpans(),
                c.traceTrimmed(), c.errorWarnLogBytes(), c.traceIdLogBytes(), c.metricsBytes(),
                c.metricsCollected(), c.metricsMissing(), c.promptChars(), c.contextChars(),
                c.contextTokens(), -1);

        var markdown = ReportMarkdown.render(new RcaReport(
                base.traceId(), base.question(), base.mode(), base.startedAt(), base.llmProvider(),
                base.llmModel(), base.llmTurns(), base.promptSource(), base.analysis(),
                base.inputTokens(), base.outputTokens(), base.cacheReadTokens(),
                base.cacheCreationTokens(), base.costUsd(), base.totalElapsedMs(), base.timings(),
                base.contextChars(), noProbe, base.triage(), base.evidence(),
                base.serviceGraph(), base.collectionFailures()));

        assertThat(markdown).contains("overheadTokens 측정 안 됨");
        assertThat(markdown).contains("▓ 추정");
    }

    @Test
    void traceId가_없어도_렌더링이_깨지지_않는다() {
        var markdown = ReportMarkdown.render(new RcaReport(
                null, "어젯밤에 알림이 안 왔어요", "rca", Instant.parse("2026-07-28T05:00:00Z"),
                "fake", "m", 1, "p", "분석 본문", 1, 1, -1, -1, 0.0, 10,
                new Timings(1, 1, 1, 1, 1), 100, null, null,
                new Evidence(null, "scan", 0, List.of(), 0, List.of(), List.of()),
                null, List.of("Tempo 검색 0건")));

        assertThat(markdown).contains("traceId 없음");
        assertThat(markdown).contains("수집된 관측값이 없다");
    }

    private static RcaReport sample() {
        var base = Instant.parse("2026-07-27T17:31:00Z");

        var triage = new RcaReport.Triage(
                "어젯밤 (어제 18:00~오늘 06:00 Asia/Seoul)",
                Instant.parse("2026-07-27T09:00:00Z"), Instant.parse("2026-07-27T21:00:00Z"),
                Instant.parse("2026-07-27T17:29:00Z"), Instant.parse("2026-07-27T17:40:00Z"),
                List.of("chat-service"), "abc123",
                List.of(new Evidence.TraceHit("abc123", "chat-service", "notification-consume", 30123,
                                Evidence.TraceHit.CHANNEL_ERROR, base, true),
                        new Evidence.TraceHit("def456", "content-service", "POST /comments", 140,
                                Evidence.TraceHit.CHANNEL_SLOW, base, true)),
                "알림 저장 실패 구간", List.of("mongodb_up이 0으로 꺾임"), true, List.of(),
                "prompts/triage-prompt.md", "## 1. 판단 ...", 43_025, 2_264, 0.3585, 31_500, 2_200, 1200, 4300,
                List.of("Metric 'up'이 이 창에서 시리즈 0건이다."),
                List.of("## INC-1  chat-service\n- 구간: … \n"), List.of("INC-1"),
                List.of("INC-2 — 시각이 증상과 불일치"));

        var evidence = new Evidence("abc123", "abc123", 30,
                List.of(new Evidence.SpanRecord("chat-service", "notification-consume", 30000.0, base)),
                412,
                List.of(new Evidence.LogLine(base, "chat-service",
                        "ERROR MongoTimeoutException: server selection timed out after 30000 ms")),
                List.of(new Evidence.MetricSeries("mongodb_up", "{instance=mongo-0}", 4,
                        base.minusSeconds(60), base.plusSeconds(120), 0, 1, 1,
                        List.of("2026-07-27T17:31:00Z ~ 2026-07-27T17:32:00Z"))));

        var coverage = new RcaReport.Coverage(
                Instant.parse("2026-07-27T17:29:00Z"), Instant.parse("2026-07-27T17:40:00Z"), 660,
                24_619, 30, false, 3_912, 3_913, 8_100,
                List.of("mongodb_up"), List.of("up"), 1_200, 40_981, -1, 21_247);

        // 검증(2026-07-31)에서 실측한 형태 그대로의 엣지 — receive는 토픽 → 서비스 방향이다.
        var graph = new ServiceGraph(List.of(new ServiceGraph.Edge(
                "messaging", "kafka/user.notifications", "chat-service", "", 4, 30108.2,
                List.of("receive"), List.of("MongoSocketOpenException: Connection refused"), List.of(), Map.of())));

        return new RcaReport("abc123", "어젯밤에 댓글 알림이 안 왔어요", "rca",
                Instant.parse("2026-07-28T05:00:00Z"), "claude-cli", "claude-opus-5", 1,
                "prompts/system-prompt.md", "## 1. 원인 후보 랭킹\n1. MongoDB 다운", 42_651, 4_950,
                900, 300, 0.4234, 79_749, new Timings(1146, 261, 355, 1, 77_969), 40_981,
                coverage, triage, evidence, graph,
                List.of("Metric 'up' returned no series in this window; skipped."));
    }
}
