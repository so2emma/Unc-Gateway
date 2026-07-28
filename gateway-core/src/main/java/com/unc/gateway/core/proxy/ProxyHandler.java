package com.unc.gateway.core.proxy;

import com.unc.gateway.core.config.StaticRouteConfig;
import com.unc.gateway.core.plugin.PluginChainHook;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.URI;

@RestController
public class ProxyHandler {

    private final StaticRouteConfig staticRouteConfig;
    private final WebClient webClient;
    private final PluginChainHook pluginChainHook;

    public ProxyHandler(StaticRouteConfig staticRouteConfig, WebClient webClient, PluginChainHook pluginChainHook) {
        this.staticRouteConfig = staticRouteConfig;
        this.webClient = webClient;
        this.pluginChainHook = pluginChainHook;
    }

    @RequestMapping("/**")
    public Mono<ResponseEntity<byte[]>> handleProxy(ServerWebExchange exchange) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getPath().value();
        String prefix = staticRouteConfig.getRoutePrefix(); // e.g. "/proxy"

        if (!path.startsWith(prefix)) {
            return Mono.just(ResponseEntity.status(HttpStatus.NOT_FOUND).build());
        }

        String subPath = path.substring(prefix.length());
        if (subPath.isEmpty()) {
            subPath = "";
        }
        String upstreamPath = "/echo" + subPath;

        String query = request.getURI().getRawQuery();
        String targetUrl = staticRouteConfig.getUpstreamBaseUrl() + upstreamPath + (query != null && !query.isEmpty() ? "?" + query : "");

        HttpMethod method = request.getMethod();

        return pluginChainHook.executeChain(exchange, java.util.Collections.emptyList(), () -> {
            WebClient.RequestBodySpec spec = webClient
                    .method(method)
                    .uri(URI.create(targetUrl))
                    .headers(httpHeaders -> {
                        httpHeaders.addAll(request.getHeaders());
                        httpHeaders.remove(HttpHeaders.HOST);
                    });

            return spec.body(request.getBody(), DataBuffer.class)
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
    }
}
