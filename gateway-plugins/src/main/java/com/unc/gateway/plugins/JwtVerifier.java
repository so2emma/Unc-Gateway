package com.unc.gateway.plugins;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.time.Clock;
import java.util.*;

/**
 * Verification component for JWT tokens:
 * parses compact JWT strings, verifies signature against the configured signing secret/algorithm,
 * and checks the {@code exp} and {@code nbf} claims against the current time.
 */
@Component
public class JwtVerifier {

    private static final Logger log = LoggerFactory.getLogger(JwtVerifier.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE_REF = new TypeReference<>() {};

    private final Clock clock;

    public JwtVerifier() {
        this(Clock.systemUTC());
    }

    public JwtVerifier(Clock clock) {
        this.clock = clock != null ? clock : Clock.systemUTC();
    }

    /**
     * Verifies a compact JWT string against the given secret/public key and algorithm.
     *
     * @param token     compact JWT string
     * @param secretKey secret key string (for HMAC) or public key PEM/Base64 (for RSA)
     * @param algorithm expected signing algorithm (e.g. HS256, RS256)
     * @return {@link Optional} containing decoded claims if valid, or empty if verification fails
     */
    public Optional<Map<String, Object>> verify(String token, String secretKey, String algorithm) {
        return verify(token, secretKey, algorithm, this.clock);
    }

    /**
     * Verifies a compact JWT string using tenant's {@code jwt-auth} config map.
     *
     * @param token  compact JWT string
     * @param config plugin configuration map
     * @return {@link Optional} containing decoded claims if valid, or empty if verification fails
     */
    public Optional<Map<String, Object>> verify(String token, Map<String, Object> config) {
        if (config == null || config.isEmpty()) {
            return Optional.empty();
        }
        String secret = JwtAuthConfigSchema.extractSecret(config);
        if (secret == null || secret.isBlank()) {
            secret = JwtAuthConfigSchema.extractPublicKey(config);
        }
        String algorithm = JwtAuthConfigSchema.extractAlgorithm(config);
        return verify(token, secret, algorithm, this.clock);
    }

    /**
     * Verifies a compact JWT string with a specific clock instance.
     */
    public Optional<Map<String, Object>> verify(String token, String secretKey, String algorithm, Clock clockToUse) {
        if (token == null || token.isBlank() || secretKey == null || secretKey.isBlank()) {
            return Optional.empty();
        }

        String[] parts = token.trim().split("\\.", -1);
        if (parts.length != 3) {
            return Optional.empty();
        }

        try {
            // 1. Decode and validate header
            byte[] headerBytes = Base64.getUrlDecoder().decode(parts[0]);
            Map<String, Object> header = OBJECT_MAPPER.readValue(headerBytes, MAP_TYPE_REF);
            String tokenAlg = (String) header.get("alg");
            if (tokenAlg == null || "none".equalsIgnoreCase(tokenAlg)) {
                return Optional.empty();
            }

            String expectedAlg = (algorithm != null && !algorithm.isBlank())
                    ? algorithm.trim().toUpperCase(Locale.ROOT)
                    : JwtAuthConfigSchema.DEFAULT_ALGORITHM;

            if (!expectedAlg.equalsIgnoreCase(tokenAlg)) {
                log.debug("Token algorithm '{}' does not match expected algorithm '{}'", tokenAlg, expectedAlg);
                return Optional.empty();
            }

            // 2. Verify signature
            String signingInput = parts[0] + "." + parts[1];
            byte[] signatureBytes = Base64.getUrlDecoder().decode(parts[2]);
            if (signatureBytes.length == 0) {
                return Optional.empty();
            }

            boolean signatureValid = verifySignature(expectedAlg, secretKey, signingInput, signatureBytes);
            if (!signatureValid) {
                log.debug("JWT signature verification failed for algorithm {}", expectedAlg);
                return Optional.empty();
            }

            // 3. Decode and validate payload claims
            byte[] payloadBytes = Base64.getUrlDecoder().decode(parts[1]);
            Map<String, Object> claims = OBJECT_MAPPER.readValue(payloadBytes, MAP_TYPE_REF);

            long nowSeconds = (clockToUse != null ? clockToUse : this.clock).instant().getEpochSecond();

            // Check exp claim
            if (claims.containsKey("exp")) {
                Object expVal = claims.get("exp");
                long expSeconds = toEpochSeconds(expVal);
                if (nowSeconds >= expSeconds) {
                    log.debug("JWT expired: current time {}, exp {}", nowSeconds, expSeconds);
                    return Optional.empty();
                }
            }

            // Check nbf claim
            if (claims.containsKey("nbf")) {
                Object nbfVal = claims.get("nbf");
                long nbfSeconds = toEpochSeconds(nbfVal);
                if (nowSeconds < nbfSeconds) {
                    log.debug("JWT not yet valid: current time {}, nbf {}", nowSeconds, nbfSeconds);
                    return Optional.empty();
                }
            }

            return Optional.of(Collections.unmodifiableMap(claims));
        } catch (Exception ex) {
            log.debug("JWT verification exception: {}", ex.getMessage());
            return Optional.empty();
        }
    }

    private boolean verifySignature(String alg, String keyMaterial, String signingInput, byte[] actualSig) {
        try {
            if (alg.startsWith("HS")) {
                String macAlg = switch (alg) {
                    case "HS256" -> "HmacSHA256";
                    case "HS384" -> "HmacSHA384";
                    case "HS512" -> "HmacSHA512";
                    default -> null;
                };
                if (macAlg == null) {
                    return false;
                }
                Mac mac = Mac.getInstance(macAlg);
                mac.init(new SecretKeySpec(keyMaterial.getBytes(StandardCharsets.UTF_8), macAlg));
                byte[] expectedSig = mac.doFinal(signingInput.getBytes(StandardCharsets.UTF_8));
                return MessageDigest.isEqual(expectedSig, actualSig);
            } else if (alg.startsWith("RS")) {
                String sigAlg = switch (alg) {
                    case "RS256" -> "SHA256withRSA";
                    case "RS384" -> "SHA384withRSA";
                    case "RS512" -> "SHA512withRSA";
                    default -> null;
                };
                if (sigAlg == null) {
                    return false;
                }
                PublicKey publicKey = parsePublicKey(keyMaterial);
                Signature signature = Signature.getInstance(sigAlg);
                signature.initVerify(publicKey);
                signature.update(signingInput.getBytes(StandardCharsets.UTF_8));
                return signature.verify(actualSig);
            }
            return false;
        } catch (Exception ex) {
            log.debug("Signature check error: {}", ex.getMessage());
            return false;
        }
    }

    private PublicKey parsePublicKey(String keyMaterial) throws Exception {
        String cleaned = keyMaterial
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replaceAll("\\s+", "");
        byte[] encoded = Base64.getDecoder().decode(cleaned);
        X509EncodedKeySpec keySpec = new X509EncodedKeySpec(encoded);
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        return keyFactory.generatePublic(keySpec);
    }

    private long toEpochSeconds(Object value) {
        if (value instanceof Number n) {
            return n.longValue();
        }
        return Long.parseLong(String.valueOf(value));
    }

    /**
     * Helper to mint a compact HMAC-SHA JWT for testing or token minting.
     */
    public static String createHmacToken(Map<String, Object> claims, String secret, String alg) {
        try {
            String normAlg = (alg != null && !alg.isBlank()) ? alg.toUpperCase(Locale.ROOT) : "HS256";
            Map<String, Object> header = Map.of("alg", normAlg, "typ", "JWT");
            String headerJson = OBJECT_MAPPER.writeValueAsString(header);
            String payloadJson = OBJECT_MAPPER.writeValueAsString(claims);

            String encodedHeader = Base64.getUrlEncoder().withoutPadding().encodeToString(headerJson.getBytes(StandardCharsets.UTF_8));
            String encodedPayload = Base64.getUrlEncoder().withoutPadding().encodeToString(payloadJson.getBytes(StandardCharsets.UTF_8));
            String signingInput = encodedHeader + "." + encodedPayload;

            String macAlg = switch (normAlg) {
                case "HS256" -> "HmacSHA256";
                case "HS384" -> "HmacSHA384";
                case "HS512" -> "HmacSHA512";
                default -> throw new IllegalArgumentException("Unsupported algorithm: " + normAlg);
            };

            Mac mac = Mac.getInstance(macAlg);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), macAlg));
            byte[] sigBytes = mac.doFinal(signingInput.getBytes(StandardCharsets.UTF_8));
            String encodedSig = Base64.getUrlEncoder().withoutPadding().encodeToString(sigBytes);

            return signingInput + "." + encodedSig;
        } catch (Exception e) {
            throw new RuntimeException("Failed to create JWT token", e);
        }
    }
}
