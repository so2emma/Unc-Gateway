package com.unc.gateway.core.listen;

import io.r2dbc.postgresql.api.Notification;
import io.r2dbc.postgresql.api.PostgresqlConnection;
import io.r2dbc.postgresql.api.PostgresqlStatement;
import io.r2dbc.spi.Connection;
import io.r2dbc.spi.ConnectionFactory;
import io.r2dbc.spi.Result;
import io.r2dbc.spi.Wrapped;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@DisplayName("Phase 12: RouteChangeListener Unit Tests")
class RouteChangeListenerTest {

    private ConnectionFactory connectionFactory;
    private PostgresqlConnection postgresqlConnection;
    private RouteChangeEventHandler eventHandler;
    private ListenerReconnectionPolicy reconnectionPolicy;
    private RouteChangeListener listener;

    private final List<String> channels = List.of("services_changed", "routes_changed", "plugin_configs_changed");

    @BeforeEach
    void setUp() {
        connectionFactory = mock(ConnectionFactory.class);
        postgresqlConnection = mock(PostgresqlConnection.class);
        eventHandler = mock(RouteChangeEventHandler.class);
        reconnectionPolicy = new ListenerReconnectionPolicy(Duration.ofMillis(10), Duration.ofMillis(50), 3);

        doReturn(Mono.just(postgresqlConnection)).when(connectionFactory).create();
        when(postgresqlConnection.close()).thenReturn(Mono.empty());

        PostgresqlStatement statement = mock(PostgresqlStatement.class);
        io.r2dbc.postgresql.api.PostgresqlResult result = mock(io.r2dbc.postgresql.api.PostgresqlResult.class);
        when(postgresqlConnection.createStatement(anyString())).thenReturn(statement);
        when(statement.execute()).thenReturn(Flux.just(result));
        when(result.getRowsUpdated()).thenReturn(Mono.just(0L));

        listener = new RouteChangeListener(connectionFactory, eventHandler, reconnectionPolicy, channels);
    }

    @Test
    @DisplayName("issues LISTEN on all configured channels when establishing connection")
    void testIssueListenCommands() {
        StepVerifier.create(listener.issueListenCommands(postgresqlConnection))
                .verifyComplete();

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(postgresqlConnection, times(3)).createStatement(sqlCaptor.capture());

        assertThat(sqlCaptor.getAllValues()).containsExactly(
                "LISTEN services_changed",
                "LISTEN routes_changed",
                "LISTEN plugin_configs_changed"
        );
    }

    @Test
    @DisplayName("correctly dispatches incoming notification to RouteChangeEventHandler")
    void testHandleNotification() {
        UUID routeId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        Notification notification = mock(Notification.class);
        when(notification.getName()).thenReturn("routes_changed");
        when(notification.getParameter()).thenReturn(String.format("""
                {"operation":"INSERT","table":"routes","id":"%s","tenant_id":"%s"}
                """, routeId, tenantId));

        when(eventHandler.handleEvent(any(RouteChangeEvent.class))).thenReturn(Mono.empty());

        StepVerifier.create(listener.handleNotification(notification))
                .verifyComplete();

        ArgumentCaptor<RouteChangeEvent> captor = ArgumentCaptor.forClass(RouteChangeEvent.class);
        verify(eventHandler, times(1)).handleEvent(captor.capture());

        RouteChangeEvent captured = captor.getValue();
        assertThat(captured.operation()).isEqualTo("INSERT");
        assertThat(captured.table()).isEqualTo("routes");
        assertThat(captured.id()).isEqualTo(routeId);
        assertThat(captured.tenantId()).isEqualTo(tenantId);
    }

    @Test
    @DisplayName("malformed NOTIFY payload is rejected without terminating notification stream")
    void testHandleMalformedNotificationDoesNotCrash() {
        Notification notification = mock(Notification.class);
        when(notification.getName()).thenReturn("routes_changed");
        when(notification.getParameter()).thenReturn("not valid json");

        StepVerifier.create(listener.handleNotification(notification))
                .verifyComplete();

        verifyNoInteractions(eventHandler);
    }

    @Test
    @DisplayName("unwraps Wrapped Connection to PostgresqlConnection successfully")
    @SuppressWarnings("unchecked")
    void testUnwrapWrappedConnection() {
        Connection wrappedConn = mock(Connection.class, withSettings().extraInterfaces(Wrapped.class));
        when(((Wrapped<Connection>) wrappedConn).unwrap()).thenReturn(postgresqlConnection);

        PostgresqlConnection unwrapped = RouteChangeListener.unwrapPostgresqlConnection(wrappedConn);
        assertThat(unwrapped).isSameAs(postgresqlConnection);
    }
}
