package com.yogurtte.rca.notify;

import com.yogurtte.rca.report.RcaReport;

public interface Notifier {

    void send(RcaReport report);
}
