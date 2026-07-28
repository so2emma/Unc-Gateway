package com.unc.gateway.core;

import com.unc.gateway.plugins.api.PluginRegistry;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.web.reactive.function.client.WebClient;

@SpringBootApplication
public class GatewayCoreApplication {

    public static void main(String[] args) {
        SpringApplication.run(GatewayCoreApplication.class, args);
    }

    @Bean
    public PluginRegistry pluginRegistry() {
        return new PluginRegistry();
    }

    @Bean
    public WebClient webClient(WebClient.Builder builder) {
        return builder.build();
    }
}
