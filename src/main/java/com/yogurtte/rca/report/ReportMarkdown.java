package com.yogurtte.rca.report;

/**
 * RcaReport를 사람이 읽는 Markdown 보고서로 렌더링한다. 분석 본문은 이미 마크다운이므로,
 * 앞에 조사 메타데이터·측정치·수집 범위를 붙여 완결된 보고서를 만든다.
 */
final class ReportMarkdown {

    private ReportMarkdown() {
    }

    static String render(RcaReport report) {
        var sb = new StringBuilder();

        sb.append("# RCA Report — `")
                .append(report.traceId() == null || report.traceId().isBlank() ? "traceId 없음" : report.traceId())
                .append("`\n\n");

        sb.append("| 항목 | 값 |\n|---|---|\n");
        sb.append("| 모드 | ").append(report.mode()).append(" |\n");
        sb.append("| 질문 | ").append(nz(report.question())).append(" |\n");
        sb.append("| 시각 | ").append(report.startedAt()).append(" |\n");
        sb.append("| provider | ").append(report.llmProvider()).append(" |\n");
        sb.append("| model | `").append(nz(report.llmModel())).append("`");
        if (report.llmTurns() >= 0) {
            sb.append(" · turns ").append(report.llmTurns());
            if (report.llmTurns() > 1) {
                sb.append(" ⚠ 단일 패스 아님");
            }
        }
        sb.append(" |\n");
        sb.append("| prompt | `").append(report.promptSource()).append("` |\n");
        sb.append("| tokens | in ").append(report.inputTokens());
        if (report.cacheReadTokens() >= 0 || report.cacheCreationTokens() >= 0) {
            sb.append(" (cacheRead %,d · cacheCreate %,d)"
                    .formatted(report.cacheReadTokens(), report.cacheCreationTokens()));
        }
        sb.append(" / out ").append(report.outputTokens());
        if (report.costUsd() >= 0) {
            sb.append(" · cost $%.4f".formatted(report.costUsd()));
        }
        sb.append(" |\n");
        var t = report.timings();
        sb.append("| elapsed | total ").append(report.totalElapsedMs()).append("ms")
                .append(" (tempo ").append(t.tempoMs()).append(" · loki ").append(t.lokiMs())
                .append(" · mimir ").append(t.mimirMs()).append(" · assemble ").append(t.assembleMs())
                .append(" · llm ").append(t.llmMs()).append(") |\n\n");

        renderTriage(sb, report.triage());

        sb.append("## 수집 범위 (Coverage)\n\n");
        var c = report.coverage();
        if (c == null) {
            sb.append("(없음)\n\n");
        } else {
            sb.append("- **window**: ").append(c.windowStart()).append(" ~ ").append(c.windowEnd())
                    .append(" (").append(c.windowSeconds()).append("s)\n");
            sb.append("- **trace**: %,dB / %d spans%s\n"
                    .formatted(c.traceBytes(), c.traceSpans(), c.traceTrimmed() ? " (상위 span만)" : ""));
            sb.append("- **logs**: errwarn=%,dB · traceId=%,dB\n"
                    .formatted(c.errorWarnLogBytes(), c.traceIdLogBytes()));
            sb.append("- **metrics**: %d 수집 / %,dB".formatted(c.metricsCollected().size(), c.metricsBytes()));
            if (!c.metricsMissing().isEmpty()) {
                sb.append(", 누락 ").append(c.metricsMissing());
            }
            sb.append("\n");
            sb.append("- **context**: %,d chars (+ 시스템 프롬프트 %,d chars)\n"
                    .formatted(c.contextChars(), c.promptChars()));
            renderTokenAxis(sb, report, c);
        }

        if (!report.collectionFailures().isEmpty()) {
            sb.append("## 수집 실패/누락\n\n");
            report.collectionFailures().forEach(f -> sb.append("- ").append(f).append("\n"));
            sb.append("\n");
        }

        sb.append("---\n\n");
        sb.append(nz(report.analysis())).append("\n");

        renderEvidence(sb, report.evidence());

        return sb.toString();
    }

    /**
     * 분석 본문 <b>뒤에</b> 관측값 원문을 붙인다. 리포트에 모델의 서술만 남으면 나중에 그 서술이
     * 맞았는지 확인할 방법이 없다 — 회고와 채점은 바꿔 쓴 문장이 아니라 원문 위에서만 성립한다.
     */
    private static void renderEvidence(StringBuilder sb, Evidence e) {
        if (e == null) {
            return;
        }
        sb.append("\n---\n\n## 관측 증거 (Evidence)\n\n");
        sb.append("> 이 조사가 실제로 본 값이다. 원본 응답 전체는 `reports/raw/")
                .append(e.rawPrefix()).append("-*.json`에 있다.\n\n");

        if (!e.topSpans().isEmpty()) {
            sb.append("### span (duration 상위 %d / 전체 %d)\n\n".formatted(e.topSpans().size(), e.spanCount()));
            sb.append("| ms | service | span | 시작 |\n|---:|---|---|---|\n");
            e.topSpans().forEach(s -> sb.append("| %.2f | %s | `%s` | %s |\n"
                    .formatted(s.durationMs(), s.service(), s.name(), s.startedAt())));
            sb.append('\n');
        }

        if (!e.logSamples().isEmpty()) {
            sb.append("### 로그 원문 (%d / 전체 %,d줄)\n\n".formatted(e.logSamples().size(), e.logLinesTotal()));
            if (e.logLinesTotal() > e.logSamples().size()) {
                sb.append("전체가 상한을 넘어 ERROR/WARN 줄을 우선 발췌했다. 나머지는 원본 파일에 있다.\n\n");
            }
            sb.append("```\n");
            e.logSamples().forEach(l -> sb.append(l.at()).append("  [").append(l.service()).append("]  ")
                    .append(l.line().strip()).append('\n'));
            sb.append("```\n\n");
        }

        if (!e.metrics().isEmpty()) {
            sb.append("### 메트릭 시계열\n\n");
            sb.append("| 쿼리 | series | 점 | min | max | last | 값이 0이던 구간 |\n");
            sb.append("|---|---|---:|---:|---:|---:|---|\n");
            e.metrics().forEach(m -> sb.append("| `%s` | `%s` | %d | %s | %s | %s | %s |\n".formatted(
                    m.query(), m.series(), m.points(), num(m.min()), num(m.max()), num(m.last()),
                    m.zeroSpans().isEmpty() ? "—" : "**" + String.join(", ", m.zeroSpans()) + "**")));
            sb.append("\n값이 0이던 구간은 굵게 표시했다 — 프로세스가 사라져 시계열이 꺾인 것이 "
                    + "유일한 신호인 장애가 있다.\n\n");
        }

        if (e.topSpans().isEmpty() && e.logSamples().isEmpty() && e.metrics().isEmpty()) {
            sb.append("수집된 관측값이 없다. **이 사실 자체가 신호다** — 위 수집 실패/누락 목록을 볼 것.\n\n");
        }
    }

    /**
     * 개선 지표(컨텍스트 토큰)를 <b>이 회차 안에서 닫힌 계산</b>으로 낸다.
     *
     * <p>총 {@code in}에는 provider 고정 오버헤드가 섞여 있어 그대로 인용하면 틀린다. 그 오버헤드는
     * 상수가 아니라서(하루 만에 20% 이동 실측) 문서에 박힌 값을 소급해 뺄 수도 없다. 그래서
     * 조사할 때 프로브로 잰 값을 쓰고, 그게 없을 때만 추정으로 떨어졌음을 <b>표시한다</b>.
     */
    private static void renderTokenAxis(StringBuilder sb, RcaReport report, RcaReport.Coverage c) {
        var overhead = c.overheadTokens();
        var stages = new StringBuilder();
        long total = 0;

        if (report.triage() != null) {
            total += report.triage().inputTokens();
            stages.append("| 탐색 | %,d | %,d | %s |\n".formatted(
                    report.triage().inputTokens(),
                    report.triage().contextChars() + report.triage().promptChars(),
                    minus(report.triage().inputTokens(), overhead)));
        }
        total += report.inputTokens();
        stages.append("| 분석 | %,d | %,d | %s |\n".formatted(
                report.inputTokens(), c.contextChars() + c.promptChars(),
                minus(report.inputTokens(), overhead)));

        sb.append("\n### 토큰 축 (개선 지표)\n\n");
        sb.append("| 단계 | 총 in | chars | 컨텍스트 토큰 |\n|---|---:|---:|---:|\n");
        sb.append(stages);
        if (report.triage() != null) {
            sb.append("| **합계** | **%,d** | | **%s** |\n".formatted(
                    total, minus(total, overhead < 0 ? -1 : overhead * 2)));
        }
        sb.append('\n');

        if (overhead >= 0) {
            sb.append("- **overheadTokens %,d tok — 이 회차에 실측**(1자 프롬프트 프로브, 같은 명령·모델·샌드박스).\n"
                    .formatted(overhead));
            sb.append("  호출마다 한 번씩 얹히므로 단계별로 뺀다. **다른 날 상수를 쓰지 않았다 = `█ 실측`.**\n");
        } else {
            sb.append("- ⚠ **overheadTokens 측정 안 됨** — 컨텍스트 토큰이 비어 있다. 이 회차 수치를 쓰려면\n");
            sb.append("  `docs/round-1-input-tokens.md`의 다른 날 상수로 **추정**해야 하고, 그건 `▓ 추정`이다\n");
            sb.append("  (그 상수는 하루 만에 20% 움직인 적이 있다).\n");
        }
        sb.append("- contextTokens (count_tokens API): %s\n"
                .formatted(c.contextTokens() < 0 ? "측정 안 됨 — 구독 CLI 경로엔 API 키가 없다"
                        : "%,d tok".formatted(c.contextTokens())));
        sb.append("- 비용은 **API 환산 추정치**다. 구독 계정으로 호출하므로 실제 청구액이 아니다.\n\n");
    }

    /**
     * {@code in − overhead}. 음수가 나오면 계산을 내놓지 않는다 — 오버헤드보다 작은 입력은
     * 있을 수 없으므로, 그건 프로브와 본 호출의 조건이 어긋났다는 신호이지 측정값이 아니다.
     */
    private static String minus(long in, long overhead) {
        if (overhead < 0) {
            return "측정 안 됨";
        }
        var value = in - overhead;
        return value < 0 ? "⚠ 이상값(오버헤드 초과)" : "%,d".formatted(value);
    }

    private static String num(double value) {
        return value == Math.rint(value) ? String.valueOf((long) value) : "%.3f".formatted(value);
    }

    /**
     * 탐색 단계를 <b>분석과 분리해서</b> 렌더한다. 합쳐 놓으면 결과가 나빴을 때 *못 찾은 것*인지
     * *찾고도 못 푼 것*인지 구별할 수 없어 무엇을 고칠지 정할 수 없다.
     */
    private static void renderTriage(StringBuilder sb, RcaReport.Triage t) {
        if (t == null) {
            return;
        }
        sb.append("## 탐색 (Triage)\n\n");
        sb.append("| 항목 | 값 |\n|---|---|\n");
        sb.append("| 시간창 해석 | ").append(nz(t.timeExpression())).append(" |\n");
        sb.append("| 스윕 창 | ").append(t.surveyStart()).append(" ~ ").append(t.surveyEnd()).append(" |\n");
        sb.append("| 좁힌 창 | ").append(t.chosenStart()).append(" ~ ").append(t.chosenEnd()).append(" |\n");
        sb.append("| 대상 | ").append(t.services().isEmpty() ? "(전체)" : String.join(", ", t.services())).append(" |\n");
        sb.append("| traceId | ").append(nz(t.traceId())).append(" |\n");
        sb.append("| 트레이스 후보 | ").append(t.traceCandidates().size()).append("건 |\n");
        if (!t.incidentCandidates().isEmpty()) {
            sb.append("| 장애 후보 | ").append(t.incidentCandidates().size()).append("건 · 선택 ")
                    .append(t.chosenIncidentIds().isEmpty() ? "없음" : String.join(", ", t.chosenIncidentIds()))
                    .append(" |\n");
        }
        sb.append("| 계획 파싱 | ").append(t.planParsed() ? "성공" : "**실패 — fallback 적용**").append(" |\n");
        sb.append("| prompt | `").append(nz(t.promptSource())).append("` |\n");
        sb.append("| tokens | in ").append(t.inputTokens()).append(" / out ").append(t.outputTokens());
        if (t.costUsd() >= 0) {
            sb.append(" · cost $%.4f".formatted(t.costUsd()));
        }
        sb.append(" |\n");
        // 토큰 축 검산(chars × 실측 비율)의 입력. 없으면 오버헤드 뺄셈 추정 하나만 남는다.
        sb.append("| chars | 컨텍스트 %,d + 프롬프트 %,d = **%,d** |\n"
                .formatted(t.contextChars(), t.promptChars(), t.contextChars() + t.promptChars()));
        sb.append("| elapsed | survey ").append(t.surveyMs()).append("ms · llm ").append(t.llmMs()).append("ms |\n\n");

        if (t.reason() != null) {
            sb.append("**선정 이유**: ").append(t.reason()).append("\n\n");
        }
        if (!t.evidence().isEmpty()) {
            sb.append("**근거**\n\n");
            t.evidence().forEach(e -> sb.append("- ").append(e).append('\n'));
            sb.append('\n');
        }
        if (!t.traceCandidates().isEmpty()) {
            // 고른 것만이 아니라 후보 전부를 남긴다 — "다른 걸 골랐어야 했나"는 이게 있어야 판단된다.
            sb.append("**스윕이 찾은 트레이스** (고른 것은 ").append(nz(t.traceId())).append(")\n\n");
            sb.append("| traceId | 채널 | root service | root span | ms |\n|---|---|---|---|---:|\n");
            t.traceCandidates().forEach(hit -> sb.append("| `%s`%s | %s%s | %s | %s | %d |\n".formatted(
                    hit.traceId(),
                    hit.traceId().equals(t.traceId()) ? " ←선택" : "",
                    hit.channel(),
                    // 값을 못 믿는 행은 표기해 둔다 — 정렬하면 이 행이 1위가 된다(CH-2 실측).
                    hit.trusted() ? "" : " ⚠값 신뢰 불가",
                    nz(hit.rootServiceName()), nz(hit.rootTraceName()), hit.durationMs())));
            sb.append('\n');
        }
        if (!t.incidentCandidates().isEmpty()) {
            // 창이 이 후보들의 신호 시각에서 계산된다 — 모델이 쓴 숫자가 아니다.
            sb.append("**장애 후보** (코드가 신호를 묶은 것 · 창은 여기서 계산됨)\n\n");
            t.incidentCandidates().forEach(c -> sb.append(c).append('\n'));
        }
        if (!t.dismissedIncidentIds().isEmpty()) {
            sb.append("**기각한 후보**\n\n");
            t.dismissedIncidentIds().forEach(d -> sb.append("- ").append(d).append('\n'));
            sb.append('\n');
        }
        if (!t.notes().isEmpty()) {
            sb.append("**보정 기록**\n\n");
            t.notes().forEach(n -> sb.append("- ").append(n).append('\n'));
            sb.append('\n');
        }
        if (!t.surveyFailures().isEmpty()) {
            sb.append("**스윕 무신호/실패**\n\n");
            t.surveyFailures().forEach(f -> sb.append("- ").append(f).append('\n'));
            sb.append('\n');
        }
    }

    private static String nz(String s) {
        return (s == null || s.isBlank()) ? "(없음)" : s;
    }
}
