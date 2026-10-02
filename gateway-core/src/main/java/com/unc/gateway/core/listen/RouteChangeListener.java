package com.unc.gateway.core.listen;

import io.r2dbc.postgresql.api.Notification;
import io.r2dbc.postgresql.api.PostgresqlConnection;
import io.r2dbc.spi.Connection;
import io.r2dbc.spi.ConnectionFactory;
import io.r2dbc.spi.Result;
import io.r2dbc.spi.Wrapped;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Objects;

/**
 * R2DBC {@code LISTEN} stream consumer that opens a dedicated PostgreSQL connection,
 * issues {@code LISTEN <channel>} for all configured row-change channels,
 * and streams notifications into {@link RouteChangeEventHandler}.
 * <p>
 * If the connection drops, {@link ListenerReconnectionPolicy} automatically reconnects
 * and re-issues {@code LISTEN} on all subscribed channels.
 */
@Component
@ConditionalOnProperty(name = "gateway.listener.enabled", havingValue = "true", matchIfMissing = true)
public class RouteChangeListener implements ApplicationRunner, DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(RouteChangeListener.class);

    private final ConnectionFactory connectionFactory;
    private final RouteChangeEventHandler eventHandler;
    private final ListenerReconnectionPolicy reconnectionPolicy;
    private final List<String> channels;

    private Disposable subscription;
    private volatile boolean running = false;

    public RouteChangeListener(
            ConnectionFactory connectionFactory,
            RouteChangeEventHandler eventHandler,
            ListenerReconnectionPolicy reconnectionPolicy,
            @Value("${gateway.listener.channels:services_changed,routes_changed,plugin_configs_changed}") List<String> channels
    ) {
        this.connectionFactory = connectionFactory;
        this.eventHandler = Objects.requireNonNull(eventHandler, "eventHandler must not be null");
        this.reconnectionPolicy = Objects.requireNonNull(reconnectionPolicy, "reconnectionPolicy must not be null");
        this.channels = channels != null && !channels.isEmpty()
                ? channels
                : List.of("services_changed", "routes_changed", "plugin_configs_changed");
    }

    @Override
    public void run(ApplicationArguments args) {
        start();
    }

    /**
     * Starts the LISTEN stream consumer.
     */
    public synchronized void start() {
        if (running) {
            return;
        }
        if (connectionFactory == null) {
            log.warn("ConnectionFactory is null; RouteChangeListener will not start.");
            return;
        }

        running = true;
        log.info("Starting RouteChangeListener on channels: {}", channels);

        this.subscription = reconnectionPolicy.resilientStream(this::createListeningStream)
                .subscribe(
                        null,
                        error -> log.error("Unhandled error in RouteChangeListener stream: {}", error.getMessage(), error),
                        () -> log.info("RouteChangeListener stream closed")
                );
    }

    /**
     * Creates a reactive stream that opens a dedicated PostgreSQL connection,
     * issues LISTEN statements for all configured channels, and dispatches notifications.
     */
    Flux<Void> createListeningStream() {
        return Flux.usingWhen(
                openDedicatedConnection(),
                conn -> issueListenCommands(conn)
                        .thenMany(conn.getNotifications().onBackpressureBuffer())
                        .flatMap(this::handleNotification),
                conn -> Mono.from(conn.close()).onErrorResume(e -> Mono.empty())
        );
    }

    /**
     * Obtains a dedicated unpooled PostgresqlConnection from the underlying ConnectionFactory.
     */
    Mono<PostgresqlConnection> openDedicatedConnection() {
        ConnectionFactory factory = unwrapConnectionFactory(connectionFactory);
        return Mono.from(factory.create())
                .map(RouteChangeListener::unwrapPostgresqlConnection);
    }

    /**
     * Issues LISTEN &lt;channel&gt; commands on the dedicated connection for all subscribed channels.
     */
    Mono<Void> issueListenCommands(PostgresqlConnection conn) {
        return Flux.fromIterable(channels)
                .concatMap(channel -> {
                    log.info("Subscribing to channel: LISTEN {}", channel);
                    return conn.createStatement("LISTEN " + channel)
                            .execute()
                            .flatMap(Result::getRowsUpdated);
                })
                .then()
                .doOnSuccess(v -> log.info("Successfully established LISTEN on all channels: {}", channels));
    }

    /**
     * Handles an incoming notification by parsing it into a {@link RouteChangeEvent}
     * and delegating to {@link RouteChangeEventHandler}. Malformed payloads are rejected
     * without failing the stream.
     */
    Mono<Void> handleNotification(Notification notification) {
        if (notification == null) {
            return Mono.empty();
        }

        String channel = notification.getName();
        String payload = notification.getParameter();
        log.debug("Received PostgreSQL notification on channel [{}]: {}", channel, payload);

        try {
            RouteChangeEvent event = RouteChangeEvent.fromJson(payload);
            return eventHandler.handleEvent(event)
                    .onErrorResume(e -> {
                        log.error("Error processing RouteChangeEvent for payload [{}]: {}", payload, e.getMessage(), e);
                        return Mono.empty();
                    });
        } catch (Exception e) {
            log.warn("Malformed NOTIFY payload ignored on channel [{}]: {}. Reason: {}", channel, payload, e.getMessage());
            return Mono.empty();
        }
    }

    /**
     * Stops the listener and cancels the subscription.
     */
    public synchronized void stop() {
        if (!running) {
            return;
        }
        running = false;
        if (subscription != null && !subscription.isDisposed()) {
            subscription.dispose();
            log.info("RouteChangeListener stopped successfully");
        }
    }

    @Override
    public void destroy() {
        stop();
    }

    public boolean isRunning() {
        return running;
    }

    public List<String> getChannels() {
        return channels;
    }

    /**
     * Unwraps a Connection into a native PostgresqlConnection.
     */
    public static PostgresqlConnection unwrapPostgresqlConnection(Connection connection) {
        if (connection instanceof PostgresqlConnection pgConn) {
            return pgConn;
        }
        if (connection instanceof Wrapped<?> wrapped) {
            Object inner = wrapped.unwrap();
            if (inner instanceof Connection innerConn) {
                return unwrapPostgresqlConnection(innerConn);
            }
        }
        throw new IllegalStateException("Connection of type " + connection.getClass().getName() +
                " cannot be unwrapped to PostgresqlConnection");
    }

    /**
     * Unwraps any ConnectionPool or wrapper down to the root ConnectionFactory.
     */
    public static ConnectionFactory unwrapConnectionFactory(ConnectionFactory factory) {
        ConnectionFactory current = factory;
        while (current instanceof Wrapped<?> wrapped) {
            Object inner = wrapped.unwrap();
            if (inner instanceof ConnectionFactory cf) {
                current = cf;
            } else {
                break;
            }
        }
        return current;
    }
}
