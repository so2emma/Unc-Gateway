package com.unc.gateway.plugins.oauth2;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.PublicKey;
import java.security.Signature;
import java.security.interfaces.ECPublicKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Clock;
import java.time.Duration;
import java.util.*;

/**
 * Validates JWT tokens using public keys retrieved and cached from an external JWKS endpoint.
 * <p>
 * Verifies the JWT signature, token expiration ({@code exp}), not-before ({@code nbf}),
 * issuer ({@code iss}), and audience ({@code aud}) claims against configuration.
 */
@Component
public class JwksJwtValidator {

    private static final Logger log = LoggerFactory.getLogger(JwksJwtValidator.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE_REF = new TypeReference<>() {};

    private final JwksKeyCache jwksKeyCache;
    private final Clock clock;

    @Autowired
    public JwksJwtValidator(JwksKeyCache jwksKeyCache) {
        this(jwksKeyCache, Clock.systemUTC());
    }

    public JwksJwtValidator(JwksKeyCache jwksKeyCache, Clock clock) {
        this.jwksKeyCache = jwksKeyCache != null ? jwksKeyCache : new JwksKeyCache();
        this.clock = clock != null ? clock : Clock.systemUTC();
    }

    /**
     * Validates a JWT token using configuration provided in the tenant plugin config map.
     *
     * @param token  compact serialized JWT string
     * @param config plugin configuration map
     * @return Mono emitting decoded claims if validation succeeds, or empty Mono if invalid
     */
    public Mono<Map<String, Object>> validate(String token, Map<String, Object> config) {
        if (config == null || config.isEmpty()) {
            return Mono.empty();
        }
        String jwksUri = OAuth2OidcConfigSchema.extractJwksUri(config);
        String expectedIssuer = OAuth2OidcConfigSchema.extractIssuer(config);
        Object expectedAudience = OAuth2OidcConfigSchema.extractAudience(config);
        long refreshSeconds = OAuth2OidcConfigSchema.extractJwksRefreshIntervalSeconds(config);
        Duration refreshInterval = Duration.ofSeconds(refreshSeconds);

        return validate(token, jwksUri, expectedIssuer, expectedAudience, refreshInterval);
    }

    /**
     * Validates a JWT token with explicit parameters.
     *
     * @param token            compact serialized JWT string
     * @param jwksUri          JWKS endpoint URI
     * @param expectedIssuer   expected issuer string (optional)
     * @param expectedAudience expected audience (String or Collection, optional)
     * @param refreshInterval  cache refresh interval for keys
     * @return Mono emitting decoded claims if valid, or empty Mono if invalid
     */
    public Mono<Map<String, Object>> validate(
            String token,
            String jwksUri,
            String expectedIssuer,
            Object expectedAudience,
            Duration refreshInterval
    ) {
        if (token == null || token.isBlank() || jwksUri == null || jwksUri.isBlank()) {
            return Mono.empty();
        }

        String[] parts = token.trim().split("\\.", -1);
        if (parts.length != 3) {
            log.debug("Invalid JWT format: expected 3 dot-delimited parts, got {}", parts.length);
            return Mono.empty();
        }

        Map<String, Object> header;
        try {
            byte[] headerBytes = Base64.getUrlDecoder().decode(parts[0]);
            header = OBJECT_MAPPER.readValue(headerBytes, MAP_TYPE_REF);
        } catch (Exception ex) {
            log.debug("Failed to decode JWT header: {}", ex.getMessage());
            return Mono.empty();
        }

        String alg = (String) header.get("alg");
        if (alg == null || "none".equalsIgnoreCase(alg)) {
            log.debug("Unsigned or missing algorithm in JWT header");
            return Mono.empty();
        }

        String kid = (String) header.get("kid");

        // Fetch key by kid from JWKS cache
        return jwksKeyCache.getKey(jwksUri, kid, refreshInterval)
                .flatMap(publicKey -> {
                    // Verify signature
                    String signingInput = parts[0] + "." + parts[1];
                    byte[] signatureBytes;
                    try {
                        signatureBytes = Base64.getUrlDecoder().decode(parts[2]);
                    } catch (IllegalArgumentException ex) {
                        log.debug("Failed to decode signature: {}", ex.getMessage());
                        return Mono.empty();
                    }

                    boolean sigValid = verifySignature(alg, publicKey, signingInput, signatureBytes);
                    if (!sigValid) {
                        log.debug("JWT signature verification failed with key kid '{}'", kid);
                        return Mono.empty();
                    }

                    // Decode and validate payload claims
                    Map<String, Object> claims;
                    try {
                        byte[] payloadBytes = Base64.getUrlDecoder().decode(parts[1]);
                        claims = OBJECT_MAPPER.readValue(payloadBytes, MAP_TYPE_REF);
                    } catch (Exception ex) {
                        log.debug("Failed to decode JWT payload: {}", ex.getMessage());
                        return Mono.empty();
                    }

                    long now = clock.instant().getEpochSecond();

                    // 1. exp claim
                    if (claims.containsKey("exp")) {
                        long exp = toEpochSeconds(claims.get("exp"));
                        if (now >= exp) {
                            log.debug("JWT token expired: exp={}, now={}", exp, now);
                            return Mono.empty();
                        }
                    }

                    // 2. nbf claim
                    if (claims.containsKey("nbf")) {
                        long nbf = toEpochSeconds(claims.get("nbf"));
                        if (now < nbf) {
                            log.debug("JWT token not yet valid: nbf={}, now={}", nbf, now);
                            return Mono.empty();
                        }
                    }

                    // 3. iss claim
                    if (expectedIssuer != null && !expectedIssuer.isBlank()) {
                        Object iss = claims.get("iss");
                        if (iss == null || !expectedIssuer.equals(iss.toString())) {
                            log.debug("JWT issuer mismatch: expected '{}', got '{}'", expectedIssuer, iss);
                            return Mono.empty();
                        }
                    }

                    // 4. aud claim
                    if (expectedAudience != null) {
                        Object aud = claims.get("aud");
                        if (!matchesAudience(expectedAudience, aud)) {
                            log.debug("JWT audience mismatch: expected '{}', got '{}'", expectedAudience, aud);
                            return Mono.empty();
                        }
                    }

                    return Mono.just(Collections.unmodifiableMap(claims));
                });
    }

    private boolean verifySignature(String alg, PublicKey publicKey, String signingInput, byte[] signatureBytes) {
        try {
            String normAlg = alg.toUpperCase(Locale.ROOT);
            if (normAlg.startsWith("RS") || publicKey instanceof RSAPublicKey) {
                String sigAlg = switch (normAlg) {
                    case "RS256" -> "SHA256withRSA";
                    case "RS384" -> "SHA384withRSA";
                    case "RS512" -> "SHA512withRSA";
                    default -> "SHA256withRSA";
                };
                Signature signature = Signature.getInstance(sigAlg);
                signature.initVerify(publicKey);
                signature.update(signingInput.getBytes(StandardCharsets.UTF_8));
                return signature.verify(signatureBytes);
            } else if (normAlg.startsWith("ES") || publicKey instanceof ECPublicKey) {
                String sigAlg = switch (normAlg) {
                    case "ES256" -> "SHA256withECDSA";
                    case "ES384" -> "SHA384withECDSA";
                    case "ES512" -> "SHA512withECDSA";
                    default -> "SHA256withECDSA";
                };
                Signature signature = Signature.getInstance(sigAlg);
                signature.initVerify(publicKey);
                signature.update(signingInput.getBytes(StandardCharsets.UTF_8));
                try {
                    return signature.verify(signatureBytes);
                } catch (Exception e) {
                    // Try DER conversion if raw IEEE P1363 / JWS format was provided
                    byte[] derSig = convertP1363ToDer(signatureBytes);
                    if (derSig != null) {
                        return signature.verify(derSig);
                    }
                    return false;
                }
            }
            return false;
        } catch (Exception ex) {
            log.debug("Signature verification exception for algorithm {}: {}", alg, ex.getMessage());
            return false;
        }
    }

    private byte[] convertP1363ToDer(byte[] rawSig) {
        if (rawSig == null || rawSig.length % 2 != 0) {
            return null;
        }
        int half = rawSig.length / 2;
        byte[] r = Arrays.copyOfRange(rawSig, 0, half);
        byte[] s = Arrays.copyOfRange(rawSig, half, rawSig.length);
        BigInteger rBig = new BigInteger(1, r);
        BigInteger sBig = new BigInteger(1, s);

        byte[] rDer = rBig.toByteArray();
        byte[] sDer = sBig.toByteArray();

        int totalLen = 2 + rDer.length + 2 + sDer.length;
        byte[] der = new byte[2 + totalLen];
        der[0] = 0x30; // SEQUENCE
        der[1] = (byte) totalLen;
        der[2] = 0x02; // INTEGER
        der[3] = (byte) rDer.length;
        System.arraycopy(rDer, 0, der, 4, rDer.length);
        int sOffset = 4 + rDer.length;
        der[sOffset] = 0x02; // INTEGER
        der[sOffset + 1] = (byte) sDer.length;
        System.arraycopy(sDer, 0, der, sOffset + 2, sDer.length);
        return der;
    }

    private boolean matchesAudience(Object expected, Object actual) {
        if (actual == null) {
            return false;
        }
        if (expected instanceof Iterable<?> expIter) {
            for (Object expItem : expIter) {
                if (matchesAudience(expItem, actual)) {
                    return true;
                }
            }
            return false;
        }

        String expectedStr = expected.toString();
        if (actual instanceof Iterable<?> actIter) {
            for (Object actItem : actIter) {
                if (actItem != null && expectedStr.equals(actItem.toString())) {
                    return true;
                }
            }
            return false;
        } else if (actual instanceof Object[] actArr) {
            for (Object actItem : actArr) {
                if (actItem != null && expectedStr.equals(actItem.toString())) {
                    return true;
                }
            }
            return false;
        }

        return expectedStr.equals(actual.toString());
    }

    private long toEpochSeconds(Object val) {
        if (val instanceof Number n) {
            return n.longValue();
        }
        return Long.parseLong(String.valueOf(val));
    }
}
