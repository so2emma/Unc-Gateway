package com.unc.gateway.plugins;

/**
 * Result of a sliding-window rate limit acquisition attempt.
 */
public record RateLimitResult(
        boolean allowed,
        long retryAfterSeconds,
        long remaining
) {
    public static RateLimitResult allowed(long remaining) {
        return new RateLimitResult(true, 0, Math.max(0, remaining));
    }

    public static RateLimitResult rejected(long retryAfterSeconds) {
        return new RateLimitResult(false, Math.max(1, retryAfterSeconds), 0);
    }
}
