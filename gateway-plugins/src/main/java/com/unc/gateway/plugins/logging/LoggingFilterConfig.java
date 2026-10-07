package com.unc.gateway.plugins.logging;

/**
 * Subpackage subclass of {@link com.unc.gateway.plugins.LoggingFilterConfig}.
 */
public class LoggingFilterConfig extends com.unc.gateway.plugins.LoggingFilterConfig {

    public LoggingFilterConfig() {
        super();
    }

    public LoggingFilterConfig(
            String level,
            boolean includeHeaders,
            boolean includeRequestHeaders,
            boolean includeResponseHeaders,
            boolean includeBody
    ) {
        super(level, includeHeaders, includeRequestHeaders, includeResponseHeaders, includeBody);
    }
}
