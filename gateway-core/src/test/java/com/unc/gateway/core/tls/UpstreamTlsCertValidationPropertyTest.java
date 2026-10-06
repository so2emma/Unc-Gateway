package com.unc.gateway.core.tls;

import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import net.jqwik.api.*;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import reactor.netty.http.client.HttpClient;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.TrustManagerFactory;
import java.io.InputStream;
import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.Security;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.Date;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-based test: TLS_UPSTREAM_CERT_VALIDATION
 * For any generated self-signed upstream certificate that is NOT signed by the
 * configured trusted CA, UpstreamTlsSelector's mTLS / TLS WebClient rejects the TLS handshake.
 */
class UpstreamTlsCertValidationPropertyTest {

    static {
        Security.addProvider(new org.bouncycastle.jce.provider.BouncyCastleProvider());
    }

    @Property(tries = 50)
    @Label("TLS_UPSTREAM_CERT_VALIDATION: untrusted self-signed certificate causes TLS handshake failure")
    boolean upstreamRejectsUntrustedCertificates(@ForAll("randomCommonNames") String randomCN) throws Exception {
        // 1. Generate an independent untrusted self-signed keypair and certificate with random CN
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        KeyPair untrustedPair = kpg.generateKeyPair();

        long now = System.currentTimeMillis();
        Date notBefore = new Date(now - 1000L * 60);
        Date notAfter = new Date(now + 1000L * 3600);
        BigInteger serial = BigInteger.valueOf(Math.abs(UUID.randomUUID().getMostSignificantBits()));

        X500Name subject = new X500Name("CN=" + randomCN + ", O=UntrustedOrg");
        ContentSigner signer = new JcaContentSignerBuilder("SHA256WithRSA").build(untrustedPair.getPrivate());
        JcaX509v3CertificateBuilder certBuilder = new JcaX509v3CertificateBuilder(
                subject, serial, notBefore, notAfter, subject, untrustedPair.getPublic()
        );
        X509Certificate untrustedCert = new JcaX509CertificateConverter()
                .setProvider("BC")
                .getCertificate(certBuilder.build(signer));

        // Create an untrusted Netty SslContext for MockWebServer
        KeyStore untrustedKs = KeyStore.getInstance("PKCS12");
        untrustedKs.load(null, null);
        untrustedKs.setKeyEntry("untrusted", untrustedPair.getPrivate(), "password".toCharArray(), new Certificate[]{untrustedCert});
        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(untrustedKs, "password".toCharArray());
        SslContext serverSslContext = SslContextBuilder.forServer(kmf).build();

        // 2. Start mock upstream with this untrusted certificate
        MockWebServer server = new MockWebServer();
        server.useHttps(serverSslContext.newEngine(io.netty.buffer.ByteBufAllocator.DEFAULT).getSession().getPacketBufferSize() > 0
                ? createJdkSslSocketFactory(untrustedKs, "password")
                : null, false);
        server.enqueue(new MockResponse().setResponseCode(200).setBody("OK"));
        server.start();

        try {
            // 3. Configure Gateway client with the legitimate trusted CA (which will NOT trust the random self-signed cert)
            GatewayTlsConfig config = new GatewayTlsConfig(new DefaultResourceLoader());
            ReflectionTestUtils.setField(config, "clientKeystorePath", "classpath:pki/certs/gateway-core/keystore.p12");
            ReflectionTestUtils.setField(config, "clientKeystorePassword", "changeit");
            ReflectionTestUtils.setField(config, "clientKeystoreType", "PKCS12");
            ReflectionTestUtils.setField(config, "clientTruststorePath", "classpath:pki/certs/gateway-core/truststore.p12");
            ReflectionTestUtils.setField(config, "clientTruststorePassword", "changeit");
            ReflectionTestUtils.setField(config, "clientTruststoreType", "PKCS12");

            SslContext clientMtlsContext = config.upstreamMtlsSslContext();
            HttpClient httpClient = HttpClient.create()
                    .secure(spec -> spec.sslContext(clientMtlsContext))
                    .responseTimeout(Duration.ofSeconds(2));

            WebClient mtlsClient = WebClient.builder()
                    .clientConnector(new ReactorClientHttpConnector(httpClient))
                    .build();

            // 4. Issue request to the untrusted server - expect handshake failure
            boolean handshakeFailed = false;
            try {
                mtlsClient.get()
                        .uri(server.url("/test").toString())
                        .retrieve()
                        .toBodilessEntity()
                        .block(Duration.ofSeconds(3));
            } catch (Exception ex) {
                // Verify the root cause is TLS / SSL handshake failure
                Throwable cause = ex;
                while (cause != null) {
                    if (cause instanceof SSLHandshakeException
                            || cause instanceof WebClientRequestException
                            || (cause.getMessage() != null && cause.getMessage().toLowerCase().contains("certificate"))) {
                        handshakeFailed = true;
                        break;
                    }
                    cause = cause.getCause();
                }
            }

            return handshakeFailed;
        } finally {
            server.shutdown();
        }
    }

    @Provide
    Arbitrary<String> randomCommonNames() {
        return Arbitraries.strings()
                .alpha()
                .ofMinLength(4)
                .ofMaxLength(16)
                .map(s -> "untrusted-" + s + ".local");
    }

    private static javax.net.ssl.SSLSocketFactory createJdkSslSocketFactory(KeyStore keyStore, String password) throws Exception {
        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(keyStore, password.toCharArray());
        javax.net.ssl.SSLContext sslContext = javax.net.ssl.SSLContext.getInstance("TLS");
        sslContext.init(kmf.getKeyManagers(), null, null);
        return sslContext.getSocketFactory();
    }
}
