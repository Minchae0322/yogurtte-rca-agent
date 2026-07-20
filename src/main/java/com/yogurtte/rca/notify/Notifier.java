package com.yogurtte.rca.notify;

import com.yogurtte.rca.report.RcaReport;

public interface Notifier {

    void send(RcaReport report);

    /** 채널 식별자. 기동 시 로그에 남겨 어떤 notifier가 선택됐는지 보여준다. */
    String channel();
}
