package com.unc.gateway.plugins.logging;

import reactor.core.scheduler.Scheduler;

/**
 * Subpackage subclass of {@link com.unc.gateway.plugins.LoggingFilter}.
 */
public class LoggingFilter extends com.unc.gateway.plugins.LoggingFilter {

    public LoggingFilter() {
        super();
    }

    public LoggingFilter(LogSink logSink) {
        super(logSink);
    }

    public LoggingFilter(LogSink logSink, Scheduler scheduler) {
        super(logSink, scheduler);
    }
}
