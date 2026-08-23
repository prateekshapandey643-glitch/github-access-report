package com.example.githubaccessreport.auth;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;

/**
 * Hand-rolled RS256 JWT signing, just enough for GitHub App authentication
 * (see https://docs.github.com/en/apps/creating-github-apps/authenticating-with-a-github-app/authenticating-as-a-github-app),
 * so the project doesn't need to pull in a full JWT library for one call site.
 */
public final class JwtUtil {

    private JwtUtil() {
    }

    /**
     * Loads a PKCS#8 PEM-encoded RSA private key.
     * <p>
     * GitHub App private keys are downloaded in PKCS#1 format
     * ("-----BEGIN RSA PRIVATE KEY-----"). Convert once with:
     * <pre>
     * openssl pkcs8 -topk8 -inform PEM -outform PEM -nocrypt \
     *   -in original-app-key.pem -out private-key-pkcs8.pem
     * </pre>
     * and point {@code github.app.private-key-path} at the converted file.
     */
    public static PrivateKey loadPrivateKey(String pemPath) {
        try {
            String pem = Files.readString(Path.of(pemPath), StandardCharsets.UTF_8);
            String base64 = pem
                    .replace("-----BEGIN PRIVATE KEY-----", "")
                    .replace("-----END PRIVATE KEY-----", "")
                    .replaceAll("\\s", "");
            byte[] keyBytes = Base64.getDecoder().decode(base64);
            PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(keyBytes);
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            return keyFactory.generatePrivate(spec);
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Failed to load GitHub App private key from " + pemPath
                            + ". Ensure it is PKCS#8 PEM-encoded (see JwtUtil javadoc for the openssl conversion command).",
                    e);
        }
    }

    /** Builds and signs a short-lived App JWT used to mint an installation access token. */
    public static String createAppJwt(String appId, PrivateKey privateKey) {
        try {
            long now = Instant.now().getEpochSecond();
            String header = base64UrlEncode("{\"alg\":\"RS256\",\"typ\":\"JWT\"}");
            // iat set 60s in the past to tolerate clock drift, per GitHub's docs.
            String payload = base64UrlEncode(String.format(
                    "{\"iat\":%d,\"exp\":%d,\"iss\":\"%s\"}", now - 60, now + 540, appId));
            String signingInput = header + "." + payload;

            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initSign(privateKey);
            signature.update(signingInput.getBytes(StandardCharsets.UTF_8));
            String signedPart = base64UrlEncode(signature.sign());

            return signingInput + "." + signedPart;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to sign GitHub App JWT", e);
        }
    }

    private static String base64UrlEncode(String value) {
        return base64UrlEncode(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String base64UrlEncode(byte[] value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }
}
