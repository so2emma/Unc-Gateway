package com.unc.gateway.core.proxy;

import com.unc.gateway.core.GatewayCoreApplication;
import com.unc.gateway.core.config.StaticRouteConfig;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@SpringBootTest(classes = GatewayCoreApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class ProxyHandlerTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockBean
    private StaticRouteConfig staticRouteConfig;

    private MockWebServer mockWebServer;

    @BeforeEach
    void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();

        given(staticRouteConfig.getRoutePrefix()).willReturn("/proxy");
        given(staticRouteConfig.getUpstreamBaseUrl()).willReturn(mockWebServer.url("").toString().replaceAll("/$", ""));
    }

    @AfterEach
    void tearDown() throws IOException {
        mockWebServer.shutdown();
    }

    @Test
    @DisplayName("GET /proxy/hello - should forward request to upstream and stream response status, headers, and body")
    void testProxyGetRequest() throws InterruptedException {
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setHeader("X-Upstream-Header", "UpstreamValue")
                .setBody("{\"status\":\"ok\"}"));

        webTestClient.get()
                .uri("/proxy/hello?query=test")
                .header("X-Client-Header", "ClientValue")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectHeader().valueEquals("X-Upstream-Header", "UpstreamValue")
                .expectBody()
                .jsonPath("$.status").isEqualTo("ok");

        RecordedRequest recordedRequest = mockWebServer.takeRequest();
        assertThat(recordedRequest.getMethod()).isEqualTo("GET");
        assertThat(recordedRequest.getPath()).isEqualTo("/echo/hello?query=test");
        assertThat(recordedRequest.getHeader("X-Client-Header")).isEqualTo("ClientValue");
    }

    @Test
    @DisplayName("POST /proxy/anything - should forward POST method and body payload")
    void testProxyPostRequest() throws InterruptedException {
        String payload = "{\"ping\":\"pong\"}";
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(201)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"created\":true}"));

        webTestClient.post()
                .uri("/proxy/anything")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(payload)
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.created").isEqualTo(true);

        RecordedRequest recordedRequest = mockWebServer.takeRequest();
        assertThat(recordedRequest.getMethod()).isEqualTo("POST");
        assertThat(recordedRequest.getPath()).isEqualTo("/echo/anything");
        assertThat(recordedRequest.getBody().readUtf8()).isEqualTo(payload);
    }

    @Test
    @DisplayName("GET /unknown/path - non-matching route prefix should return 404 Not Found")
    void testNonMatchingRouteReturns404() {
        webTestClient.get()
                .uri("/unknown/path")
                .exchange()
                .expectStatus().isNotFound();

        assertThat(mockWebServer.getRequestCount()).isEqualTo(0);
    }
}
