package com.example.githubaccessreport.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.PrivateKey;
import java.time.Instant;
import java.time.format.DateTimeFormatter;

/**
 * Auth provider backed by a GitHub App installation.
 * <p>
 * Installation access tokens expire after ~1 hour. This provider lazily
 * mints one, caches it, and transparently refreshes it (a minute before
 * expiry) on subsequent calls — callers just call
 * {@link #getAuthorizationHeader()} and never think about token lifecycle.
 * <p>
 * Preferred for production/org-wide deployments: access is scoped to the
 * app's installation rather than a personal account, and credentials rotate
 * automatically.
 */
public class GitHubAppAuthProvider implements GitHubAuthProvider {

    private static final Logger log = LoggerFactory.getLogger(GitHubAppAuthProvider.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final String appId;
    private final PrivateKey privateKey;
    private final String installationId;
    private final String apiBaseUrl;
    private final HttpClient httpClient;

    private volatile String cachedToken;
    private volatile Instant cachedTokenExpiresAt = Instant.EPOCH;
    private final Object refreshLock = new Object();

    public GitHubAppAuthProvider(String appId, String privateKeyPath, String installationId, String apiBaseUrl) {
        if (appId == null || appId.isBlank() || installationId == null || installationId.isBlank()) {
            throw new IllegalArgumentException(
                    "github.app.app-id and github.app.installation-id must be set when github.auth-mode=app.");
        }
        this.appId = appId;
        this.installationId = installationId;
        this.apiBaseUrl = apiBaseUrl;
        this.privateKey = JwtUtil.loadPrivateKey(privateKeyPath);
        this.httpClient = HttpClient.newHttpClient();
    }

    @Override
    public String getAuthorizationHeader() {
        if (isTokenValid()) {
            return "Bearer " + cachedToken;
        }
        synchronized (refreshLock) {
            if (isTokenValid()) {
                return "Bearer " + cachedToken;
            }
            refreshInstallationToken();
            return "Bearer " + cachedToken;
        }
    }

    private boolean isTokenValid() {
        return cachedToken != null && Instant.now().isBefore(cachedTokenExpiresAt.minusSeconds(60));
    }

    private void refreshInstallationToken() {
        String appJwt = JwtUtil.createAppJwt(appId, privateKey);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(apiBaseUrl + "/app/installations/" + installationId + "/access_tokens"))
                .header("Authorization", "Bearer " + appJwt)
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 201) {
                throw new IllegalStateException(
                        "GitHub App installation token request failed with status " + response.statusCode()
                                + ": " + response.body());
            }
            JsonNode body = MAPPER.readTree(response.body());
            cachedToken = body.get("token").asText();
            cachedTokenExpiresAt = Instant.from(DateTimeFormatter.ISO_INSTANT.parse(body.get("expires_at").asText()));
            log.info("Refreshed GitHub App installation token, expires at {}", cachedTokenExpiresAt);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to mint GitHub App installation access token", e);
        }
    }
}
