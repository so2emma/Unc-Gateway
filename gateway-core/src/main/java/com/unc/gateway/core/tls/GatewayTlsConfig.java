package com.unc.gateway.core.tls;

import io.netty.handler.ssl.ClientAuth;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.embedded.netty.NettyReactiveWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.TrustManagerFactory;
import java.io.InputStream;
import java.security.KeyStore;

@Configuration
public class GatewayTlsConfig {

    private static final Logger log = LoggerFactory.getLogger(GatewayTlsConfig.class);

    @Value("${gateway.tls.server.enabled:false}")
    private boolean serverTlsEnabled;

    @Value("${gateway.tls.server.keystore-path:}")
    private String serverKeystorePath;

    @Value("${gateway.tls.server.keystore-password:changeit}")
    private String serverKeystorePassword;

    @Value("${gateway.tls.server.keystore-type:PKCS12}")
    private String serverKeystoreType;

    @Value("${gateway.tls.server.client-auth:none}")
    private String serverClientAuth;

    @Value("${gateway.tls.server.truststore-path:}")
    private String serverTruststorePath;

    @Value("${gateway.tls.server.truststore-password:changeit}")
    private String serverTruststorePassword;

    // Upstream client TLS / mTLS configuration
    @Value("${gateway.tls.client.keystore-path:}")
    private String clientKeystorePath;

    @Value("${gateway.tls.client.keystore-password:changeit}")
    private String clientKeystorePassword;

    @Value("${gateway.tls.client.keystore-type:PKCS12}")
    private String clientKeystoreType;

    @Value("${gateway.tls.client.truststore-path:}")
    private String clientTruststorePath;

    @Value("${gateway.tls.client.truststore-password:changeit}")
    private String clientTruststorePassword;

    @Value("${gateway.tls.client.truststore-type:PKCS12}")
    private String clientTruststoreType;

    private final ResourceLoader resourceLoader;

    public GatewayTlsConfig(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    /**
     * Inbound HTTPS listener customizer for Netty Reactive Web Server.
     */
    @Bean
    public WebServerFactoryCustomizer<NettyReactiveWebServerFactory> nettyServerTlsCustomizer() {
        return factory -> {
            if (!serverTlsEnabled) {
                log.info("Gateway server TLS is disabled.");
                return;
            }
            try {
                SslContext sslContext = buildServerSslContext();
                if (sslContext != null) {
                    factory.addServerCustomizers(httpServer ->
                            httpServer.secure(sslContextSpec -> sslContextSpec.sslContext(sslContext))
                    );
                    log.info("Gateway server TLS successfully configured on Netty server factory.");
                }
            } catch (Exception e) {
                log.error("Failed to configure Netty server TLS: {}", e.getMessage(), e);
                throw new IllegalStateException("Could not configure gateway server TLS", e);
            }
        };
    }

    /**
     * Builds SslContext for the inbound server listener.
     */
    public SslContext buildServerSslContext() {
        if (!serverTlsEnabled || serverKeystorePath == null || serverKeystorePath.isBlank()) {
            return null;
        }
        try {
            KeyStore keyStore = loadKeyStore(serverKeystorePath, serverKeystorePassword, serverKeystoreType);
            KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            kmf.init(keyStore, serverKeystorePassword.toCharArray());

            SslContextBuilder builder = SslContextBuilder.forServer(kmf);

            if ("need".equalsIgnoreCase(serverClientAuth) || "want".equalsIgnoreCase(serverClientAuth)) {
                if ("need".equalsIgnoreCase(serverClientAuth)) {
                    builder.clientAuth(ClientAuth.REQUIRE);
                } else {
                    builder.clientAuth(ClientAuth.OPTIONAL);
                }
                if (serverTruststorePath != null && !serverTruststorePath.isBlank()) {
                    KeyStore trustStore = loadKeyStore(serverTruststorePath, serverTruststorePassword, serverKeystoreType);
                    TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
                    tmf.init(trustStore);
                    builder.trustManager(tmf);
                }
            } else {
                builder.clientAuth(ClientAuth.NONE);
            }

            return builder.build();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to build server SslContext", e);
        }
    }

    /**
     * Upstream SslContext for TLS-only (client verifies server certificate against truststore).
     */
    @Bean(name = "upstreamTlsSslContext")
    public SslContext upstreamTlsSslContext() {
        try {
            SslContextBuilder builder = SslContextBuilder.forClient();
            if (clientTruststorePath != null && !clientTruststorePath.isBlank()) {
                KeyStore trustStore = loadKeyStore(clientTruststorePath, clientTruststorePassword, clientTruststoreType);
                TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
                tmf.init(trustStore);
                builder.trustManager(tmf);
            }
            return builder.build();
        } catch (Exception e) {
            log.warn("Could not create upstreamTlsSslContext with configured truststore, falling back to default client SSL: {}", e.getMessage());
            try {
                return SslContextBuilder.forClient().build();
            } catch (Exception ex) {
                throw new IllegalStateException("Failed to build default client SslContext", ex);
            }
        }
    }

    /**
     * Upstream SslContext for mTLS (client presents certificate AND verifies server certificate).
     */
    @Bean(name = "upstreamMtlsSslContext")
    public SslContext upstreamMtlsSslContext() {
        try {
            SslContextBuilder builder = SslContextBuilder.forClient();

            // Client certificate / private key
            if (clientKeystorePath != null && !clientKeystorePath.isBlank()) {
                KeyStore keyStore = loadKeyStore(clientKeystorePath, clientKeystorePassword, clientKeystoreType);
                KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
                kmf.init(keyStore, clientKeystorePassword.toCharArray());
                builder.keyManager(kmf);
            }

            // Truststore to verify upstream
            if (clientTruststorePath != null && !clientTruststorePath.isBlank()) {
                KeyStore trustStore = loadKeyStore(clientTruststorePath, clientTruststorePassword, clientTruststoreType);
                TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
                tmf.init(trustStore);
                builder.trustManager(tmf);
            }

            return builder.build();
        } catch (Exception e) {
            log.warn("Could not create upstreamMtlsSslContext, falling back to default client SSL: {}", e.getMessage());
            try {
                return SslContextBuilder.forClient().build();
            } catch (Exception ex) {
                throw new IllegalStateException("Failed to build fallback client SslContext", ex);
            }
        }
    }

    /**
     * Standard non-TLS WebClient.
     */
    @Bean(name = "plainWebClient")
    @org.springframework.context.annotation.Primary
    public WebClient plainWebClient(WebClient.Builder builder) {
        return builder.build();
    }

    /**
     * TLS-only upstream WebClient (one-way TLS).
     */
    @Bean(name = "tlsWebClient")
    public WebClient tlsWebClient(WebClient.Builder builder,
                                  @org.springframework.beans.factory.annotation.Qualifier("upstreamTlsSslContext") SslContext upstreamTlsSslContext) {
        HttpClient httpClient = HttpClient.create()
                .secure(sslContextSpec -> sslContextSpec.sslContext(upstreamTlsSslContext));
        return builder.clientConnector(new ReactorClientHttpConnector(httpClient)).build();
    }

    /**
     * mTLS upstream WebClient (mutual TLS).
     */
    @Bean(name = "mtlsWebClient")
    public WebClient mtlsWebClient(WebClient.Builder builder,
                                   @org.springframework.beans.factory.annotation.Qualifier("upstreamMtlsSslContext") SslContext upstreamMtlsSslContext) {
        HttpClient httpClient = HttpClient.create()
                .secure(sslContextSpec -> sslContextSpec.sslContext(upstreamMtlsSslContext));
        return builder.clientConnector(new ReactorClientHttpConnector(httpClient)).build();
    }

    public KeyStore loadKeyStore(String path, String password, String type) throws Exception {
        KeyStore keyStore = KeyStore.getInstance(type != null && !type.isBlank() ? type : "PKCS12");
        Resource resource = resolveResource(path);
        try (InputStream in = resource.getInputStream()) {
            keyStore.load(in, password != null ? password.toCharArray() : null);
        }
        return keyStore;
    }

    private Resource resolveResource(String path) {
        if (path.startsWith("classpath:") || path.startsWith("file:")) {
            return resourceLoader.getResource(path);
        }
        return resourceLoader.getResource("file:" + path);
    }
}
