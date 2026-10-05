package com.unc.gateway.core.proxy;

import com.unc.gateway.core.plugin.PluginChainHook;
import com.unc.gateway.core.routing.DynamicRouteResolver;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.Collections;

@RestController
public class ProxyHandler {

    private final DynamicRouteResolver dynamicRouteResolver;
    private final WebClient webClient;
    private final PluginChainHook pluginChainHook;

    public ProxyHandler(DynamicRouteResolver dynamicRouteResolver, WebClient webClient, PluginChainHook pluginChainHook) {
        this.dynamicRouteResolver = dynamicRouteResolver;
        this.webClient = webClient;
        this.pluginChainHook = pluginChainHook;
    }

    @RequestMapping("/**")
    public Mono<ResponseEntity<byte[]>> handleProxy(ServerWebExchange exchange) {
        return dynamicRouteResolver.resolveTarget(exchange)
                .flatMap(targetUrl -> {
                    ServerHttpRequest request = exchange.getRequest();
                    HttpMethod method = request.getMethod();

                    return pluginChainHook.executeChain(exchange, Collections.emptyList(), () -> {
                        String resolvedUri = targetUrl;
                        try {
                            URI parsedUri = URI.create(targetUrl);
                            if (parsedUri.getPath() == null || parsedUri.getPath().isEmpty()) {
                                resolvedUri = targetUrl.contains("?")
                                        ? targetUrl.replace("?", "/?")
                                        : targetUrl + "/";
                            }
                        } catch (Exception ignored) {
                        }

                        WebClient.RequestBodySpec spec = webClient
                                .method(method)
                                .uri(URI.create(resolvedUri))
                                .headers(httpHeaders -> {
                                    httpHeaders.addAll(request.getHeaders());
                                    httpHeaders.remove(HttpHeaders.HOST);
                                });

                        WebClient.RequestHeadersSpec<?> headersSpec = spec;
                        if (method != HttpMethod.GET && method != HttpMethod.HEAD) {
                            headersSpec = spec.body(request.getBody(), DataBuffer.class);
                        } else if (request.getHeaders().getContentLength() > 0) {
                            headersSpec = spec.body(request.getBody(), DataBuffer.class);
                        }

                        return headersSpec
                                .exchangeToMono(clientResponse ->
                                        clientResponse.bodyToMono(byte[].class)
                                                .defaultIfEmpty(new byte[0])
                                                .map(bodyBytes -> {
                                                    ResponseEntity.BodyBuilder builder = ResponseEntity.status(clientResponse.statusCode());
                                                    clientResponse.headers().asHttpHeaders().forEach((key, values) -> {
                                                        if (!HttpHeaders.TRANSFER_ENCODING.equalsIgnoreCase(key)) {
                                                            builder.header(key, values.toArray(new String[0]));
                                                        }
                                                    });
                                                    return builder.body(bodyBytes);
                                                })
                                );
                    });
                })
                .flatMap(responseEntity -> {
                    if (exchange.getResponse().isCommitted()) {
                        return Mono.empty();
                    }
                    return Mono.just(responseEntity);
                })
                .onErrorResume(ResponseStatusException.class, ex ->
                        Mono.just(ResponseEntity.status(ex.getStatusCode()).build())
                );
    }
}
