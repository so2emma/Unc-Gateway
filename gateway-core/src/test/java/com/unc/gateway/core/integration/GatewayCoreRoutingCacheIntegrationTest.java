package com.unc.gateway.core.integration;

import com.unc.gateway.core.GatewayCoreApplication;
import com.unc.gateway.core.cache.RouteCache;
import com.unc.gateway.core.cache.RouteEntry;
import com.unc.gateway.core.routing.DynamicRouteResolver;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(classes = GatewayCoreApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@DisplayName("Phase 10: Gateway Core In-Memory Routing Cache Integration Test")
class GatewayCoreRoutingCacheIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("unc_db")
            .withUsername("postgres")
            .withPassword("postgrespassword");

    static final MockWebServer mockWebServer = new MockWebServer();

    static final UUID TENANT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    static final UUID SERVICE_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    static final UUID ROUTE_1_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    static final UUID ROUTE_2_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) throws IOException, SQLException {
        mockWebServer.start();
        String mockUpstreamUrl = mockWebServer.url("").toString().replaceAll("/$", "");

        // 1. Run Flyway migrations
        Flyway flyway = Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .load();
        flyway.migrate();

        // 2. Seed rows into tenants, services, and routes tables
        try (Connection conn = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())) {

            // Insert tenant
            try (PreparedStatement stmt = conn.prepareStatement(
                    "INSERT INTO tenants (id, name, tenant_id, api_key, status) VALUES (?, ?, ?, ?, ?)")) {
                stmt.setObject(1, TENANT_ID);
                stmt.setString(2, "tenant-a");
                stmt.setObject(3, TENANT_ID);
                stmt.setString(4, "api-key-tenant-a");
                stmt.setString(5, "ACTIVE");
                stmt.executeUpdate();
            }

            // Insert service pointing to MockWebServer
            try (PreparedStatement stmt = conn.prepareStatement(
                    "INSERT INTO services (id, tenant_id, name, url) VALUES (?, ?, ?, ?)")) {
                stmt.setObject(1, SERVICE_ID);
                stmt.setObject(2, TENANT_ID);
                stmt.setString(3, "demo-service");
                stmt.setString(4, mockUpstreamUrl);
                stmt.executeUpdate();
            }

            // Insert route 1 (/demo)
            try (PreparedStatement stmt = conn.prepareStatement(
                    "INSERT INTO routes (id, tenant_id, service_id, name, paths, strip_path) VALUES (?, ?, ?, ?, ?, ?)")) {
                stmt.setObject(1, ROUTE_1_ID);
                stmt.setObject(2, TENANT_ID);
                stmt.setObject(3, SERVICE_ID);
                stmt.setString(4, "demo-route");
                stmt.setString(5, "/demo");
                stmt.setBoolean(6, true);
                stmt.executeUpdate();
            }

            // Insert route 2 (/api/orders)
            try (PreparedStatement stmt = conn.prepareStatement(
                    "INSERT INTO routes (id, tenant_id, service_id, name, paths, strip_path) VALUES (?, ?, ?, ?, ?, ?)")) {
                stmt.setObject(1, ROUTE_2_ID);
                stmt.setObject(2, TENANT_ID);
                stmt.setObject(3, SERVICE_ID);
                stmt.setString(4, "orders-route");
                stmt.setString(5, "/api/orders");
                stmt.setBoolean(6, false);
                stmt.executeUpdate();
            }
        }

        // 3. Configure R2DBC properties for gateway-core
        String r2dbcUrl = String.format("r2dbc:postgresql://%s:%d/%s",
                POSTGRES.getHost(),
                POSTGRES.getFirstMappedPort(),
                POSTGRES.getDatabaseName());

        registry.add("spring.r2dbc.url", () -> r2dbcUrl);
        registry.add("spring.r2dbc.username", POSTGRES::getUsername);
        registry.add("spring.r2dbc.password", POSTGRES::getPassword);
    }

    @AfterAll
    static void tearDown() throws IOException {
        mockWebServer.shutdown();
    }

    @Autowired
    private RouteCache routeCache;

    @Autowired
    private DynamicRouteResolver dynamicRouteResolver;

    @Autowired
    private WebTestClient webTestClient;

    @Test
    @DisplayName("RouteCacheLoader loads every seeded row into RouteCache on startup")
    void testStartupCachePopulation() {
        assertThat(routeCache.size()).isGreaterThanOrEqualTo(2);

        Optional<RouteEntry> demoRoute = routeCache.lookup("/demo", TENANT_ID);
        assertThat(demoRoute).isPresent();
        assertThat(demoRoute.get().routeId()).isEqualTo(ROUTE_1_ID);
        assertThat(demoRoute.get().serviceId()).isEqualTo(SERVICE_ID);
        assertThat(demoRoute.get().path()).isEqualTo("/demo");
        assertThat(demoRoute.get().stripPath()).isTrue();

        Optional<RouteEntry> ordersRoute = routeCache.lookup("/api/orders", TENANT_ID);
        assertThat(ordersRoute).isPresent();
        assertThat(ordersRoute.get().routeId()).isEqualTo(ROUTE_2_ID);
        assertThat(ordersRoute.get().path()).isEqualTo("/api/orders");
        assertThat(ordersRoute.get().stripPath()).isFalse();
    }

    @Test
    @DisplayName("DynamicRouteResolver resolves a proxied request against a loaded route to mock-upstream")
    void testProxyRequestAgainstLoadedRoute() throws InterruptedException {
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"echo\":\"pong\"}"));

        webTestClient.post()
                .uri("/demo")
                .header("X-Tenant-Id", TENANT_ID.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"ping\":\"pong\"}")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody()
                .jsonPath("$.echo").isEqualTo("pong");

        RecordedRequest recordedRequest = mockWebServer.takeRequest();
        assertThat(recordedRequest.getMethod()).isEqualTo("POST");
        assertThat(recordedRequest.getBody().readUtf8()).isEqualTo("{\"ping\":\"pong\"}");
    }

    @Test
    @DisplayName("Unrouted path returns 404 Not Found without forwarding to upstream")
    void testUnroutedPathReturnsNotFound() {
        webTestClient.get()
                .uri("/unmatched/path")
                .header("X-Tenant-Id", TENANT_ID.toString())
                .exchange()
                .expectStatus().isNotFound();

        assertThat(mockWebServer.getRequestCount()).isEqualTo(0);
    }
}
