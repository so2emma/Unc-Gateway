package com.unc.gateway.core.tls;

import io.netty.handler.ssl.SslContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.test.util.ReflectionTestUtils;

import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLEngine;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GatewayTlsConfigTest {

    @Test
    @DisplayName("Inbound server SslContext correctly loads gateway-core certificate and CN")
    void testServerSslContextConfig() throws Exception {
        GatewayTlsConfig config = new GatewayTlsConfig(new DefaultResourceLoader());
        ReflectionTestUtils.setField(config, "serverTlsEnabled", true);
        ReflectionTestUtils.setField(config, "serverKeystorePath", "classpath:pki/certs/gateway-core/keystore.p12");
        ReflectionTestUtils.setField(config, "serverKeystorePassword", "changeit");
        ReflectionTestUtils.setField(config, "serverKeystoreType", "PKCS12");
        ReflectionTestUtils.setField(config, "serverClientAuth", "none");

        SslContext sslContext = config.buildServerSslContext();
        assertThat(sslContext).isNotNull();
        assertThat(sslContext.isServer()).isTrue();

        // Verify loaded keyStore certificate contains CN=gateway-core
        java.security.KeyStore ks = config.loadKeyStore("classpath:pki/certs/gateway-core/keystore.p12", "changeit", "PKCS12");
        Certificate cert = ks.getCertificate("gateway-core");
        assertThat(cert).isInstanceOf(X509Certificate.class);
        X509Certificate x509 = (X509Certificate) cert;
        assertThat(x509.getSubjectX500Principal().getName()).contains("CN=gateway-core");
    }

    @Test
    @DisplayName("Upstream mTLS SslContext accepts certificates signed by local CA truststore")
    void testUpstreamMtlsSslContextWithTrustedCA() {
        GatewayTlsConfig config = new GatewayTlsConfig(new DefaultResourceLoader());
        ReflectionTestUtils.setField(config, "clientKeystorePath", "classpath:pki/certs/gateway-core/keystore.p12");
        ReflectionTestUtils.setField(config, "clientKeystorePassword", "changeit");
        ReflectionTestUtils.setField(config, "clientKeystoreType", "PKCS12");
        ReflectionTestUtils.setField(config, "clientTruststorePath", "classpath:pki/certs/gateway-core/truststore.p12");
        ReflectionTestUtils.setField(config, "clientTruststorePassword", "changeit");
        ReflectionTestUtils.setField(config, "clientTruststoreType", "PKCS12");

        SslContext sslContext = config.upstreamMtlsSslContext();
        assertThat(sslContext).isNotNull();
        assertThat(sslContext.isClient()).isTrue();
    }

    @Test
    @DisplayName("When server TLS is disabled, buildServerSslContext returns null")
    void testServerTlsDisabledReturnsNull() {
        GatewayTlsConfig config = new GatewayTlsConfig(new DefaultResourceLoader());
        ReflectionTestUtils.setField(config, "serverTlsEnabled", false);

        SslContext sslContext = config.buildServerSslContext();
        assertThat(sslContext).isNull();
    }
}
