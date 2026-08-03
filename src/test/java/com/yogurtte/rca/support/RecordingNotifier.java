package com.yogurtte.rca.support;

import java.util.ArrayList;
import java.util.List;

import com.yogurtte.rca.notify.Notifier;
import com.yogurtte.rca.report.RcaReport;

/**
 * 보낸 리포트를 그대로 쌓아두는 통보자. 흐름 테스트가 <b>무엇이 통보됐는지</b>를 볼 때 쓴다.
 *
 * <p>{@code RcaServiceFlowTest}와 {@code TriageFlowTest}에 같은 구현이 글자 그대로 두 벌
 * 있었다. 두 진입점(traceId · 자연어)이 같은 분석 경로를 쓰는지 비교하는 것이 이 레포의
 * 측정 목적이라, 더블이 갈라지면 그 비교가 조용히 무너진다.
 */
public class RecordingNotifier implements Notifier {

    public final List<RcaReport> sent = new ArrayList<>();

    @Override
    public void send(RcaReport report) {
        sent.add(report);
    }

    @Override
    public String channel() {
        return "recording";
    }
}
