package com.unc.gateway.core.proxy;

import com.unc.gateway.core.cache.RouteEntry;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Objects;

/**
 * Component called by {@link ProxyHandler} to select between the plain, TLS-only,
 * and mTLS {@link WebClient} instances based on the flags in {@link RouteEntry}.
 * Routes with {@code mtlsEnabled = true} always use the mTLS client regardless of {@code tlsEnabled}.
 */
@Component
public class UpstreamTlsSelector {

    private final WebClient plainWebClient;
    private final WebClient tlsWebClient;
    private final WebClient mtlsWebClient;

    public UpstreamTlsSelector(
            @Qualifier("plainWebClient") WebClient plainWebClient,
            @Qualifier("tlsWebClient") WebClient tlsWebClient,
            @Qualifier("mtlsWebClient") WebClient mtlsWebClient) {
        this.plainWebClient = Objects.requireNonNull(plainWebClient, "plainWebClient must not be null");
        this.tlsWebClient = Objects.requireNonNull(tlsWebClient, "tlsWebClient must not be null");
        this.mtlsWebClient = Objects.requireNonNull(mtlsWebClient, "mtlsWebClient must not be null");
    }

    /**
     * Selects the appropriate {@link WebClient} for a given {@link RouteEntry}.
     *
     * @param route the matched route entry
     * @return the selected WebClient
     */
    public WebClient selectClient(RouteEntry route) {
        if (route == null) {
            return plainWebClient;
        }

        // Routes with mtlsEnabled = true always use the mTLS client regardless of tlsEnabled
        if (route.mtlsEnabled()) {
            return mtlsWebClient;
        }

        // Routes with tlsEnabled = true and mtlsEnabled = false select the TLS-only client
        if (route.tlsEnabled()) {
            return tlsWebClient;
        }

        // Otherwise (tlsEnabled = false, mtlsEnabled = false) plain WebClient
        return plainWebClient;
    }

    public WebClient getPlainWebClient() {
        return plainWebClient;
    }

    public WebClient getTlsWebClient() {
        return tlsWebClient;
    }

    public WebClient getMtlsWebClient() {
        return mtlsWebClient;
    }
}
