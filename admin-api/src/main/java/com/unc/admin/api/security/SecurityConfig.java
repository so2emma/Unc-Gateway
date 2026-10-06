package com.unc.admin.api.security;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final ObjectProvider<TenantHeaderFilter> tenantHeaderFilterProvider;
    private final ObjectProvider<JwtAuthenticationFilter> jwtAuthFilterProvider;
    private final ObjectProvider<ApiKeyAuthenticationFilter> apiKeyAuthFilterProvider;

    public SecurityConfig(
            ObjectProvider<TenantHeaderFilter> tenantHeaderFilterProvider,
            ObjectProvider<JwtAuthenticationFilter> jwtAuthFilterProvider,
            ObjectProvider<ApiKeyAuthenticationFilter> apiKeyAuthFilterProvider) {
        this.tenantHeaderFilterProvider = tenantHeaderFilterProvider;
        this.jwtAuthFilterProvider = jwtAuthFilterProvider;
        this.apiKeyAuthFilterProvider = apiKeyAuthFilterProvider;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, "/api/admin/auth/login", "/api/admin/auth/refresh").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/admin/tenants").permitAll()
                        .requestMatchers("/health", "/actuator/**").permitAll()
                        .anyRequest().authenticated()
                )
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, authException) -> {
                            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            response.getWriter().write("{\"error\":\"Unauthorized\",\"message\":\"" + authException.getMessage() + "\"}");
                        })
                        .accessDeniedHandler((request, response, accessDeniedException) -> {
                            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            response.getWriter().write("{\"error\":\"Forbidden\",\"message\":\"" + accessDeniedException.getMessage() + "\"}");
                        })
                );

        TenantHeaderFilter tenantHeaderFilter = tenantHeaderFilterProvider.getIfAvailable();
        if (tenantHeaderFilter != null) {
            http.addFilterBefore(tenantHeaderFilter, UsernamePasswordAuthenticationFilter.class);
        }

        JwtAuthenticationFilter jwtAuthFilter = jwtAuthFilterProvider.getIfAvailable();
        if (jwtAuthFilter != null) {
            http.addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);
        }

        ApiKeyAuthenticationFilter apiKeyAuthFilter = apiKeyAuthFilterProvider.getIfAvailable();
        if (apiKeyAuthFilter != null) {
            http.addFilterBefore(apiKeyAuthFilter, UsernamePasswordAuthenticationFilter.class);
        }

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }
}
