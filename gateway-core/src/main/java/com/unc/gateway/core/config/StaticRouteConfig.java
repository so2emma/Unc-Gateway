package com.unc.gateway.core.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class StaticRouteConfig {

    private final String routePrefix;
    private final String upstreamBaseUrl;

    public StaticRouteConfig(
            @Value("${upstream.route-prefix:/proxy}") String routePrefix,
            @Value("${upstream.base-url:http://mock-upstream:9090}") String upstreamBaseUrl
    ) {
        this.routePrefix = routePrefix;
        this.upstreamBaseUrl = upstreamBaseUrl;
    }

    public String getRoutePrefix() {
        return routePrefix;
    }

    public String getUpstreamBaseUrl() {
        return upstreamBaseUrl;
    }
}
