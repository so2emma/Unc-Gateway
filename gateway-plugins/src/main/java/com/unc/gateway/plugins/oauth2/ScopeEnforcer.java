package com.unc.gateway.plugins.oauth2;

import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Validates that token claims contain all required OAuth2/OIDC scopes.
 * <p>
 * Supports both RFC 6749 {@code scope} (space-delimited string or collection) and
 * OAuth2 / JWT {@code scp} (array or list) claims.
 */
@Component
public class ScopeEnforcer {

    /**
     * Structured result of scope enforcement.
     */
    public record ScopeEnforcementResult(
            boolean granted,
            String firstMissingScope,
            Set<String> tokenScopes
    ) {
        public boolean isGranted() {
            return granted;
        }

        public boolean isSatisfied() {
            return granted;
        }

        public String getFirstMissingScope() {
            return firstMissingScope;
        }

        public String getMissingScope() {
            return firstMissingScope;
        }

        public Set<String> getTokenScopes() {
            return tokenScopes != null ? tokenScopes : Collections.emptySet();
        }

        public String toWwwAuthenticateHeader() {
            return "Bearer error=\"insufficient_scope\"";
        }

        public String toWwwAuthenticateHeaderWithScope() {
            if (firstMissingScope != null && !firstMissingScope.isBlank()) {
                return "Bearer error=\"insufficient_scope\", scope=\"" + firstMissingScope + "\"";
            }
            return "Bearer error=\"insufficient_scope\"";
        }
    }

    /**
     * Enforces that the claims present in the token satisfy all required scopes.
     *
     * @param requiredScopes list of scopes required for the route/tenant
     * @param claims         decoded token claims map
     * @return structured enforcement result
     */
    public ScopeEnforcementResult enforce(List<String> requiredScopes, Map<String, Object> claims) {
        Set<String> tokenScopes = extractScopes(claims);

        if (requiredScopes == null || requiredScopes.isEmpty()) {
            return new ScopeEnforcementResult(true, null, tokenScopes);
        }

        for (String required : requiredScopes) {
            if (required != null && !required.isBlank()) {
                String trimmed = required.trim();
                if (!tokenScopes.contains(trimmed)) {
                    return new ScopeEnforcementResult(false, trimmed, tokenScopes);
                }
            }
        }

        return new ScopeEnforcementResult(true, null, tokenScopes);
    }

    /**
     * Static helper to perform scope enforcement directly.
     */
    public static ScopeEnforcementResult check(List<String> requiredScopes, Map<String, Object> claims) {
        return new ScopeEnforcer().enforce(requiredScopes, claims);
    }

    /**
     * Extracts all granted scopes from token claims, inspecting both {@code scope} and {@code scp} claims.
     *
     * @param claims decoded token claims map
     * @return unmodifiable set of granted scopes
     */
    public static Set<String> extractScopes(Map<String, Object> claims) {
        if (claims == null || claims.isEmpty()) {
            return Collections.emptySet();
        }

        Set<String> result = new LinkedHashSet<>();

        // 1. Inspect "scope" claim (RFC 6749)
        if (claims.containsKey("scope")) {
            parseScopeValue(claims.get("scope"), result);
        }

        // 2. Inspect "scp" claim (common in JWT tokens)
        if (claims.containsKey("scp")) {
            parseScopeValue(claims.get("scp"), result);
        }

        return Collections.unmodifiableSet(result);
    }

    private static void parseScopeValue(Object value, Set<String> target) {
        if (value == null) {
            return;
        }
        if (value instanceof String s) {
            String[] tokens = s.trim().split("\\s+");
            for (String t : tokens) {
                if (!t.isBlank()) {
                    target.add(t.trim());
                }
            }
        } else if (value instanceof Iterable<?> iter) {
            for (Object item : iter) {
                if (item != null) {
                    String itemStr = item.toString().trim();
                    if (!itemStr.isBlank()) {
                        for (String t : itemStr.split("\\s+")) {
                            if (!t.isBlank()) {
                                target.add(t.trim());
                            }
                        }
                    }
                }
            }
        } else if (value.getClass().isArray()) {
            Object[] array = (Object[]) value;
            for (Object item : array) {
                if (item != null) {
                    String itemStr = item.toString().trim();
                    if (!itemStr.isBlank()) {
                        for (String t : itemStr.split("\\s+")) {
                            if (!t.isBlank()) {
                                target.add(t.trim());
                            }
                        }
                    }
                }
            }
        }
    }
}
