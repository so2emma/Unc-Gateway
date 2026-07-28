package com.unc.mock.upstream.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

@RestController
public class EchoController {

    @RequestMapping({"/echo/**", "/api/**", "/**"})
    public Mono<ResponseEntity<Map<String, Object>>> echo(ServerWebExchange exchange) {
        Map<String, Object> result = new HashMap<>();
        result.put("method", exchange.getRequest().getMethod().name());
        result.put("path", exchange.getRequest().getPath().value());

        Map<String, String> headersMap = exchange.getRequest().getHeaders().toSingleValueMap();
        result.put("headers", headersMap);

        Map<String, String> queryParams = exchange.getRequest().getQueryParams().toSingleValueMap();
        result.put("queryParams", queryParams);

        return exchange.getRequest().getBody()
                .map(dataBuffer -> {
                    byte[] bytes = new byte[dataBuffer.readableByteCount()];
                    dataBuffer.read(bytes);
                    return new String(bytes, StandardCharsets.UTF_8);
                })
                .reduce((s1, s2) -> s1 + s2)
                .defaultIfEmpty("")
                .map(body -> {
                    result.put("body", body);
                    return ResponseEntity.ok(result);
                });
    }
}
