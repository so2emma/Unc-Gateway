package com.unc.mock.upstream.controller;

import com.unc.mock.upstream.MockUpstreamApplication;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(classes = MockUpstreamApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class EchoControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @Test
    @DisplayName("POST /echo/anything - should echo request method, path, custom headers, and body payload")
    void testEchoPostWithBodyAndHeaders() {
        String jsonPayload = "{\"ping\":\"pong\"}";

        webTestClient.post()
                .uri("/echo/anything")
                .header("X-Custom-Header", "CustomValue")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(jsonPayload)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody()
                .jsonPath("$.method").isEqualTo("POST")
                .jsonPath("$.path").isEqualTo("/echo/anything")
                .jsonPath("$.headers['X-Custom-Header']").isEqualTo("CustomValue")
                .jsonPath("$.body").isEqualTo(jsonPayload);
    }

    @Test
    @DisplayName("GET /echo - should echo GET method with empty body field without error")
    void testEchoGetWithoutBody() {
        webTestClient.get()
                .uri("/echo")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody()
                .jsonPath("$.method").isEqualTo("GET")
                .jsonPath("$.path").isEqualTo("/echo")
                .jsonPath("$.body").isEqualTo("");
    }
}
