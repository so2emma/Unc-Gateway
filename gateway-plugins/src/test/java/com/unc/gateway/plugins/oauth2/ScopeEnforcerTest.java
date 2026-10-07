package com.unc.gateway.plugins.oauth2;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ScopeEnforcerTest {

    private final ScopeEnforcer scopeEnforcer = new ScopeEnforcer();

    @Test
    @DisplayName("ScopeEnforcer: token carrying 'api:read api:write' satisfies requiredScopes ['api:read']")
    void testTokenSatisfiesSubsetOfRequiredScopes() {
        Map<String, Object> claims = Map.of("scope", "api:read api:write");
        List<String> requiredScopes = List.of("api:read");

        ScopeEnforcer.ScopeEnforcementResult result = scopeEnforcer.enforce(requiredScopes, claims);

        assertThat(result.isGranted()).isTrue();
        assertThat(result.getFirstMissingScope()).isNull();
        assertThat(result.getTokenScopes()).containsExactlyInAnyOrder("api:read", "api:write");
    }

    @Test
    @DisplayName("ScopeEnforcer: same token does not satisfy requiredScopes ['api:admin'] and returns missing scope")
    void testTokenMissingRequiredScopeReturnsFirstMissingScope() {
        Map<String, Object> claims = Map.of("scope", "api:read api:write");
        List<String> requiredScopes = List.of("api:admin");

        ScopeEnforcer.ScopeEnforcementResult result = scopeEnforcer.enforce(requiredScopes, claims);

        assertThat(result.isGranted()).isFalse();
        assertThat(result.getFirstMissingScope()).isEqualTo("api:admin");
        assertThat(result.toWwwAuthenticateHeader()).isEqualTo("Bearer error=\"insufficient_scope\"");
        assertThat(result.toWwwAuthenticateHeaderWithScope()).isEqualTo("Bearer error=\"insufficient_scope\", scope=\"api:admin\"");
    }

    @Test
    @DisplayName("ScopeEnforcer: token carrying scp list claim satisfies requiredScopes")
    void testTokenWithScpListClaim() {
        Map<String, Object> claims = Map.of("scp", List.of("api:read", "api:write", "profile"));
        List<String> requiredScopes = List.of("api:read", "profile");

        ScopeEnforcer.ScopeEnforcementResult result = scopeEnforcer.enforce(requiredScopes, claims);

        assertThat(result.isGranted()).isTrue();
        assertThat(result.getFirstMissingScope()).isNull();
    }

    @Test
    @DisplayName("ScopeEnforcer: empty requiredScopes always passes")
    void testEmptyRequiredScopesAlwaysPasses() {
        Map<String, Object> claims = Map.of("sub", "user-123");

        ScopeEnforcer.ScopeEnforcementResult result = scopeEnforcer.enforce(List.of(), claims);

        assertThat(result.isGranted()).isTrue();
        assertThat(result.getFirstMissingScope()).isNull();
    }

    @Test
    @DisplayName("ScopeEnforcer: missing scopes claim when requiredScopes is non-empty fails")
    void testMissingScopeClaimWithRequiredScopesFails() {
        Map<String, Object> claims = Map.of("sub", "user-123");
        List<String> requiredScopes = List.of("api:read");

        ScopeEnforcer.ScopeEnforcementResult result = scopeEnforcer.enforce(requiredScopes, claims);

        assertThat(result.isGranted()).isFalse();
        assertThat(result.getFirstMissingScope()).isEqualTo("api:read");
    }
}
