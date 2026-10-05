package com.unc.gateway.plugins;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unc.gateway.plugins.api.GatewayFilter;
import com.unc.gateway.plugins.api.GatewayFilterChain;
import com.unc.gateway.plugins.api.PluginConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;
import reactor.util.context.ContextView;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * {@link GatewayFilter} implementation that asynchronously emits a structured log record
 * for every proxied request upon reactive completion, without introducing latency to
 * the request/response path.
 */
@Component
public class LoggingFilter implements GatewayFilter {

    public static final String PLUGIN_NAME = "logging";

    private static final Logger log = LoggerFactory.getLogger(LoggingFilter.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .setSerializationInclusion(JsonInclude.Include.NON_NULL);

    @FunctionalInterface
    public interface LogSink {
        void emit(StructuredLogRecord record);
    }

    private final LogSink logSink;
    private final Scheduler scheduler;

    public LoggingFilter() {
        this(defaultSink(), Schedulers.boundedElastic());
    }

    @Autowired(required = false)
    public LoggingFilter(LogSink logSink) {
        this(logSink, Schedulers.boundedElastic());
    }

    public LoggingFilter(LogSink logSink, Scheduler scheduler) {
        this.logSink = logSink != null ? logSink : defaultSink();
        this.scheduler = scheduler != null ? scheduler : Schedulers.boundedElastic();
    }

    @Override
    public String getName() {
        return PLUGIN_NAME;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        Map<String, Object> config = resolveConfig(exchange);
        String level = LoggingFilterConfig.extractLevel(config);
        boolean includeHeaders = LoggingFilterConfig.shouldIncludeHeaders(config);
        boolean includeReqHeaders = includeHeaders || LoggingFilterConfig.shouldIncludeRequestHeaders(config);
        boolean includeRespHeaders = includeHeaders || LoggingFilterConfig.shouldIncludeResponseHeaders(config);

        long startNano = System.nanoTime();

        return Mono.deferContextual(contextView ->
                chain.filter(exchange)
                        .doOnSuccess(ignored -> scheduleLogEmission(
                                exchange, startNano, null, level, includeReqHeaders, includeRespHeaders, contextView))
                        .doOnError(err -> scheduleLogEmission(
                                exchange, startNano, err, level, includeReqHeaders, includeRespHeaders, contextView))
        );
    }

    private void scheduleLogEmission(
            ServerWebExchange exchange,
            long startNano,
            Throwable err,
            String level,
            boolean includeReqHeaders,
            boolean includeRespHeaders,
            ContextView contextView
    ) {
        long latencyMs = Math.max(0, (System.nanoTime() - startNano) / 1_000_000L);
        StructuredLogRecord record = buildRecord(
                exchange,
                latencyMs,
                err,
                level,
                includeReqHeaders,
                includeRespHeaders,
                contextView
        );

        try {
            scheduler.schedule(() -> {
                try {
                    logSink.emit(record);
                } catch (Throwable t) {
                    log.error("Failed to emit structured log record: {}", t.getMessage(), t);
                }
            });
        } catch (Throwable t) {
            log.error("Failed to schedule structured log emission: {}", t.getMessage(), t);
        }
    }

    private StructuredLogRecord buildRecord(
            ServerWebExchange exchange,
            long latencyMs,
            Throwable err,
            String level,
            boolean includeReqHeaders,
            boolean includeRespHeaders,
            ContextView contextView
    ) {
        String timestamp = Instant.now().toString();
        String method = exchange.getRequest().getMethod() != null
                ? exchange.getRequest().getMethod().name()
                : "GET";
        String path = exchange.getRequest().getPath() != null
                ? exchange.getRequest().getPath().value()
                : "/";
        int statusCode = resolveStatusCode(exchange, err);
        String tenantId = resolveTenantId(exchange);
        String consumerId = resolveConsumerId(exchange);
        String traceId = resolveTraceId(exchange, contextView);
        String spanId = resolveSpanId(exchange, contextView);

        Map<String, String> reqHeaders = includeReqHeaders ? extractRequestHeaders(exchange) : null;
        Map<String, String> respHeaders = includeRespHeaders ? extractResponseHeaders(exchange) : null;
        String errorMessage = err != null ? err.getMessage() : null;

        return new StructuredLogRecord(
                timestamp,
                method,
                path,
                statusCode,
                latencyMs,
                tenantId,
                consumerId,
                traceId,
                spanId,
                level,
                reqHeaders,
                respHeaders,
                errorMessage
        );
    }

    private int resolveStatusCode(ServerWebExchange exchange, Throwable err) {
        if (err instanceof ResponseStatusException rse) {
            return rse.getStatusCode().value();
        }
        HttpStatusCode responseStatus = exchange.getResponse().getStatusCode();
        if (err != null) {
            if (responseStatus != null && responseStatus.value() >= 400) {
                return responseStatus.value();
            }
            return HttpStatus.INTERNAL_SERVER_ERROR.value();
        }
        if (responseStatus != null) {
            return responseStatus.value();
        }
        return HttpStatus.OK.value();
    }

    private String resolveTenantId(ServerWebExchange exchange) {
        Object identityObj = exchange.getAttribute(KeyAuthFilter.ATTR_CONSUMER_IDENTITY);
        if (identityObj instanceof ConsumerIdentity ci && ci.tenantId() != null) {
            return ci.tenantId().toString();
        }
        Object tenantObj = exchange.getAttribute(KeyAuthFilter.ATTR_TENANT_ID);
        if (tenantObj == null) {
            tenantObj = exchange.getAttribute("tenant_id");
        }
        if (tenantObj != null) {
            return tenantObj.toString();
        }
        String hdr = exchange.getRequest().getHeaders().getFirst("X-Tenant-Id");
        if (hdr != null && !hdr.isBlank()) {
            return hdr.trim();
        }
        return null;
    }

    private String resolveConsumerId(ServerWebExchange exchange) {
        Object identityObj = exchange.getAttribute(KeyAuthFilter.ATTR_CONSUMER_IDENTITY);
        if (identityObj instanceof ConsumerIdentity ci && ci.consumerId() != null) {
            return ci.consumerId().toString();
        }
        Object consumerObj = exchange.getAttribute(KeyAuthFilter.ATTR_CONSUMER_ID);
        if (consumerObj == null) {
            consumerObj = exchange.getAttribute("consumer_id");
        }
        if (consumerObj == null) {
            consumerObj = exchange.getAttribute(JwtAuthFilter.ATTR_JWT_SUBJECT);
        }
        if (consumerObj == null) {
            consumerObj = exchange.getAttribute("jwt_sub");
        }
        if (consumerObj != null) {
            return consumerObj.toString();
        }
        String hdr = exchange.getRequest().getHeaders().getFirst("X-Consumer-Id");
        if (hdr != null && !hdr.isBlank()) {
            return hdr.trim();
        }
        return null;
    }

    private String resolveTraceId(ServerWebExchange exchange, ContextView contextView) {
        if (contextView != null && contextView.hasKey("traceId")) {
            Object val = contextView.get("traceId");
            if (val != null) {
                return val.toString();
            }
        }
        Object attr = exchange.getAttribute("traceId");
        if (attr != null) {
            return attr.toString();
        }
        String hdr = exchange.getRequest().getHeaders().getFirst("traceparent");
        if (hdr != null && !hdr.isBlank()) {
            String[] parts = hdr.split("-");
            if (parts.length >= 3) {
                return parts[1];
            }
            return hdr.trim();
        }
        String b3 = exchange.getRequest().getHeaders().getFirst("X-B3-TraceId");
        if (b3 != null && !b3.isBlank()) {
            return b3.trim();
        }
        return null;
    }

    private String resolveSpanId(ServerWebExchange exchange, ContextView contextView) {
        if (contextView != null && contextView.hasKey("spanId")) {
            Object val = contextView.get("spanId");
            if (val != null) {
                return val.toString();
            }
        }
        Object attr = exchange.getAttribute("spanId");
        if (attr != null) {
            return attr.toString();
        }
        String hdr = exchange.getRequest().getHeaders().getFirst("traceparent");
        if (hdr != null && !hdr.isBlank()) {
            String[] parts = hdr.split("-");
            if (parts.length >= 3) {
                return parts[2];
            }
        }
        String b3 = exchange.getRequest().getHeaders().getFirst("X-B3-SpanId");
        if (b3 != null && !b3.isBlank()) {
            return b3.trim();
        }
        return null;
    }

    private Map<String, String> extractRequestHeaders(ServerWebExchange exchange) {
        try {
            Map<String, String> map = new LinkedHashMap<>();
            exchange.getRequest().getHeaders().forEach((name, values) -> {
                if (values != null && !values.isEmpty()) {
                    map.put(name, String.join(",", values));
                }
            });
            return Collections.unmodifiableMap(map);
        } catch (Exception e) {
            return Collections.emptyMap();
        }
    }

    private Map<String, String> extractResponseHeaders(ServerWebExchange exchange) {
        try {
            Map<String, String> map = new LinkedHashMap<>();
            exchange.getResponse().getHeaders().forEach((name, values) -> {
                if (values != null && !values.isEmpty()) {
                    map.put(name, String.join(",", values));
                }
            });
            return Collections.unmodifiableMap(map);
        } catch (Exception e) {
            return Collections.emptyMap();
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> resolveConfig(ServerWebExchange exchange) {
        Object direct = exchange.getAttribute("plugin_config_" + PLUGIN_NAME);
        if (direct instanceof PluginConfig pc) {
            return pc.getConfig();
        }
        if (direct instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return null;
    }

    private static LogSink defaultSink() {
        return record -> {
            try {
                String json = OBJECT_MAPPER.writeValueAsString(record);
                String lvl = record.level() != null ? record.level().toUpperCase() : "INFO";
                switch (lvl) {
                    case "DEBUG" -> log.debug("{}", json);
                    case "WARN" -> log.warn("{}", json);
                    case "ERROR" -> log.error("{}", json);
                    default -> log.info("{}", json);
                }
            } catch (Exception e) {
                log.info("{}", record);
            }
        };
    }
}
