package com.unc.gateway.core.tls;

import com.unc.gateway.core.GatewayCoreApplication;
import com.unc.gateway.core.cache.RouteCache;
import com.unc.gateway.core.cache.RouteCacheLoader;
import com.unc.gateway.core.cache.RouteEntry;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import javax.net.ssl.*;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        classes = GatewayCoreApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "gateway.tls.server.enabled=true",
                "gateway.tls.server.keystore-path=classpath:pki/certs/gateway-core/keystore.p12",
                "gateway.tls.server.keystore-password=changeit",
                "gateway.tls.server.keystore-type=PKCS12",
                "gateway.tls.client.keystore-path=classpath:pki/certs/gateway-core/keystore.p12",
                "gateway.tls.client.keystore-password=changeit",
                "gateway.tls.client.keystore-type=PKCS12",
                "gateway.tls.client.truststore-path=classpath:pki/certs/gateway-core/truststore.p12",
                "gateway.tls.client.truststore-password=changeit",
                "gateway.tls.client.truststore-type=PKCS12"
        }
)
@AutoConfigureWebTestClient
@DisplayName("Gateway Core TLS & mTLS End-to-End Integration Test")
class GatewayTlsIntegrationTest {

    private static MockWebServer mockUpstream;
    private static SSLSocketFactory mockUpstreamSslSocketFactory;
    private static X509TrustManager mockUpstreamTrustManager;

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private RouteCache routeCache;

    @MockBean
    private RouteCacheLoader routeCacheLoader;

    @MockBean
    private com.unc.gateway.plugins.PluginConfigLoader pluginConfigLoader;

    private static final UUID TENANT_ID = UUID.randomUUID();

    @BeforeAll
    static void setUpAll() throws Exception {
        // Load mock-upstream keystore and dev truststore
        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        try (InputStream in = new DefaultResourceLoader().getResource("classpath:pki/certs/mock-upstream/keystore.p12").getInputStream()) {
            keyStore.load(in, "changeit".toCharArray());
        }

        KeyStore trustStore = KeyStore.getInstance("PKCS12");
        try (InputStream in = new DefaultResourceLoader().getResource("classpath:pki/certs/mock-upstream/truststore.p12").getInputStream()) {
            trustStore.load(in, "changeit".toCharArray());
        }

        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(keyStore, "changeit".toCharArray());

        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(trustStore);

        mockUpstreamTrustManager = (X509TrustManager) tmf.getTrustManagers()[0];

        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(kmf.getKeyManagers(), tmf.getTrustManagers(), new SecureRandom());
        mockUpstreamSslSocketFactory = sslContext.getSocketFactory();

        mockUpstream = new MockWebServer();
        mockUpstream.useHttps(mockUpstreamSslSocketFactory, false);
        mockUpstream.start();
    }

    @AfterAll
    static void tearDownAll() throws Exception {
        if (mockUpstream != null) {
            mockUpstream.shutdown();
        }
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        // dynamic properties if needed
    }

    @Test
    @DisplayName("Inbound HTTPS health probe succeeds on configured port")
    void testInboundHealthProbe() {
        webTestClient.get()
                .uri("/health")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("UP")
                .jsonPath("$.service").isEqualTo("gateway-core");
    }

    @Test
    @DisplayName("Outbound mTLS proxy request to TLS mock upstream succeeds when mTLS is enabled")
    void testOutboundMtlsProxyRequestSuccess() {
        String upstreamUrl = mockUpstream.url("/echo").toString().replaceAll("/$", "");
        UUID routeId = UUID.randomUUID();
        UUID serviceId = UUID.randomUUID();

        // Route with mtlsEnabled = true
        RouteEntry mtlsRoute = new RouteEntry(
                routeId,
                serviceId,
                TENANT_ID,
                "/secure-proxy",
                upstreamUrl,
                true,
                true,
                true
        );

        routeCache.bulkReplace(List.of(mtlsRoute));

        mockUpstream.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"mtls\":\"success\"}"));

        webTestClient.get()
                .uri("/secure-proxy/hello")
                .header("X-Tenant-Id", TENANT_ID.toString())
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.mtls").isEqualTo("success");
    }
}
