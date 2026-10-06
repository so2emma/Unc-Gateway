package com.unc.gateway.core.proxy;

import com.unc.gateway.core.cache.RouteEntry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class UpstreamTlsSelectorTest {

    private WebClient plainWebClient;
    private WebClient tlsWebClient;
    private WebClient mtlsWebClient;
    private UpstreamTlsSelector selector;

    @BeforeEach
    void setUp() {
        plainWebClient = mock(WebClient.class);
        tlsWebClient = mock(WebClient.class);
        mtlsWebClient = mock(WebClient.class);
        selector = new UpstreamTlsSelector(plainWebClient, tlsWebClient, mtlsWebClient);
    }

    @Test
    @DisplayName("Route with tlsEnabled = false, mtlsEnabled = false selects plain WebClient")
    void testSelectPlainClient() {
        RouteEntry route = new RouteEntry(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "/api/test", "http://backend:8080", true, false, false);

        WebClient selected = selector.selectClient(route);
        assertThat(selected).isSameAs(plainWebClient);
    }

    @Test
    @DisplayName("Route with tlsEnabled = true, mtlsEnabled = false selects TLS-only WebClient")
    void testSelectTlsOnlyClient() {
        RouteEntry route = new RouteEntry(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "/api/secure", "https://backend:8443", true, true, false);

        WebClient selected = selector.selectClient(route);
        assertThat(selected).isSameAs(tlsWebClient);
    }

    @Test
    @DisplayName("Route with mtlsEnabled = true selects mTLS WebClient regardless of tlsEnabled (false)")
    void testSelectMtlsClientWhenTlsFalse() {
        RouteEntry route = new RouteEntry(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "/api/mtls", "https://backend:8443", true, false, true);

        WebClient selected = selector.selectClient(route);
        assertThat(selected).isSameAs(mtlsWebClient);
    }

    @Test
    @DisplayName("Route with mtlsEnabled = true selects mTLS WebClient when tlsEnabled = true")
    void testSelectMtlsClientWhenTlsTrue() {
        RouteEntry route = new RouteEntry(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "/api/mtls-full", "https://backend:8443", true, true, true);

        WebClient selected = selector.selectClient(route);
        assertThat(selected).isSameAs(mtlsWebClient);
    }

    @Test
    @DisplayName("Null route safely defaults to plain WebClient")
    void testNullRouteDefaultsToPlainClient() {
        WebClient selected = selector.selectClient(null);
        assertThat(selected).isSameAs(plainWebClient);
    }
}
