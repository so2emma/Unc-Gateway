package com.unc.gateway.plugins.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unc.gateway.plugins.api.GatewayFilterChain;
import com.unc.gateway.plugins.api.PluginConfig;
import com.unc.gateway.plugins.oauth2.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.MountableFile;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
class KeycloakOidcIntegrationTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Container
    static final GenericContainer<?> KEYCLOAK = new GenericContainer<>("quay.io/keycloak/keycloak:24")
            .withCommand("start-dev", "--import-realm")
            .withEnv("KEYCLOAK_ADMIN", "admin")
            .withEnv("KEYCLOAK_ADMIN_PASSWORD", "admin")
            .withEnv("KC_HEALTH_ENABLED", "true")
            .withCopyFileToContainer(MountableFile.forClasspathResource("dev-realm.json"), "/opt/keycloak/data/import/dev-realm.json")
            .withExposedPorts(8080)
            .waitingFor(Wait.forHttp("/health/ready").forPort(8080).forStatusCode(200).withStartupTimeout(Duration.ofMinutes(3)));

    private static String keycloakBaseUrl;
    private static String tokenEndpoint;
    private static String jwksUri;
    private static String introspectionEndpoint;
    private static String issuer;

    private static WebClient webClient;
    private static JwksKeyCache jwksKeyCache;
    private static JwksJwtValidator jwksJwtValidator;
    private static TokenIntrospectionClient tokenIntrospectionClient;
    private static ScopeEnforcer scopeEnforcer;
    private static OAuth2OidcFilter filter;

    @BeforeAll
    static void setUp() {
        String host = KEYCLOAK.getHost();
        int port = KEYCLOAK.getFirstMappedPort();
        keycloakBaseUrl = "http://" + host + ":" + port;
        tokenEndpoint = keycloakBaseUrl + "/realms/unc-dev/protocol/openid-connect/token";
        jwksUri = keycloakBaseUrl + "/realms/unc-dev/protocol/openid-connect/certs";
        introspectionEndpoint = keycloakBaseUrl + "/realms/unc-dev/protocol/openid-connect/token/introspect";
        issuer = keycloakBaseUrl + "/realms/unc-dev";

        webClient = WebClient.builder().build();
        jwksKeyCache = new JwksKeyCache(webClient);
        jwksJwtValidator = new JwksJwtValidator(jwksKeyCache);
        tokenIntrospectionClient = new TokenIntrospectionClient(webClient);
        scopeEnforcer = new ScopeEnforcer();
        filter = new OAuth2OidcFilter(jwksJwtValidator, tokenIntrospectionClient, scopeEnforcer);
    }

    private String obtainClientCredentialsToken() {
        String response = webClient.post()
                .uri(tokenEndpoint)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(BodyInserters.fromFormData("grant_type", "client_credentials")
                        .with("client_id", "gateway-core-client")
                        .with("client_secret", "changeme"))
                .retrieve()
                .bodyToMono(String.class)
                .block(Duration.ofSeconds(10));

        try {
            JsonNode jsonNode = OBJECT_MAPPER.readTree(response);
            return jsonNode.get("access_token").asText();
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse token response", e);
        }
    }

    @Test
    @DisplayName("Integration: JWKS-mode validates Keycloak client credentials token and attaches claims to exchange")
    void testJwksModeWithKeycloakToken() {
        String token = obtainClientCredentialsToken();
        assertThat(token).isNotBlank();

        Map<String, Object> config = Map.of(
                "mode", "jwks",
                "jwks_uri", jwksUri,
                "issuer", issuer,
                "required_scopes", List.of("api:read")
        );

        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/echo/hello")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        PluginConfig pluginConfig = new PluginConfig();
        pluginConfig.setName("oauth2-oidc");
        pluginConfig.setConfig(config);
        exchange.getAttributes().put("plugin_config_oauth2-oidc", pluginConfig);

        AtomicBoolean downstreamCalled = new AtomicBoolean(false);
        GatewayFilterChain chain = mutatedExchange -> {
            downstreamCalled.set(true);

            assertThat(mutatedExchange.<Map<String, Object>>getAttribute(OAuth2OidcFilter.ATTR_OAUTH2_CLAIMS)).isNotNull();
            assertThat(mutatedExchange.<String>getAttribute(OAuth2OidcFilter.ATTR_OAUTH2_SUBJECT)).isNotNull();
            assertThat(mutatedExchange.getRequest().getHeaders().getFirst("X-OAuth-Scope")).contains("api:read");

            return Mono.empty();
        };

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        assertThat(downstreamCalled.get()).isTrue();
        assertThat(exchange.getResponse().getStatusCode()).isNull();
    }

    @Test
    @DisplayName("Integration: Introspection mode validates Keycloak token and rejects invalid token with 401")
    void testIntrospectionModeWithKeycloakToken() {
        String token = obtainClientCredentialsToken();
        assertThat(token).isNotBlank();

        Map<String, Object> config = Map.of(
                "mode", "introspect",
                "introspection_endpoint", introspectionEndpoint,
                "client_id", "gateway-core-client",
                "client_secret", "changeme",
                "required_scopes", List.of("api:read")
        );

        // 1. Valid token passes
        MockServerHttpRequest validReq = MockServerHttpRequest.get("/api/v1/echo/hello")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .build();
        MockServerWebExchange validExchange = MockServerWebExchange.from(validReq);

        PluginConfig pluginConfig = new PluginConfig();
        pluginConfig.setName("oauth2-oidc");
        pluginConfig.setConfig(config);
        validExchange.getAttributes().put("plugin_config_oauth2-oidc", pluginConfig);

        AtomicBoolean downstreamCalled = new AtomicBoolean(false);
        StepVerifier.create(filter.filter(validExchange, ex -> {
            downstreamCalled.set(true);
            return Mono.empty();
        })).verifyComplete();

        assertThat(downstreamCalled.get()).isTrue();

        // 2. Invalid / bogus token rejected with 401
        MockServerHttpRequest invalidReq = MockServerHttpRequest.get("/api/v1/echo/hello")
                .header(HttpHeaders.AUTHORIZATION, "Bearer invalid-token-xyz")
                .build();
        MockServerWebExchange invalidExchange = MockServerWebExchange.from(invalidReq);
        invalidExchange.getAttributes().put("plugin_config_oauth2-oidc", pluginConfig);

        AtomicBoolean invalidDownstreamCalled = new AtomicBoolean(false);
        StepVerifier.create(filter.filter(invalidExchange, ex -> {
            invalidDownstreamCalled.set(true);
            return Mono.empty();
        })).verifyComplete();

        assertThat(invalidDownstreamCalled.get()).isFalse();
        assertThat(invalidExchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
