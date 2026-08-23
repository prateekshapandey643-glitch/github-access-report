package com.example.githubaccessreport.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Binds the {@code github.*} configuration namespace (application.yml / env vars).
 * <p>
 * Two auth modes are supported, selected via {@code github.auth-mode}:
 * <ul>
 *     <li>{@code token} — a Personal Access Token supplied via {@code github.token}</li>
 *     <li>{@code app} — a GitHub App installation, configured via {@code github.app.*}</li>
 * </ul>
 */
@ConfigurationProperties(prefix = "github")
public class GitHubProperties {

    /** "token" or "app". Defaults to "token". */
    private String authMode = "token";

    /** Personal access token, required when authMode = "token". */
    private String token;

    /** GitHub App credentials, required when authMode = "app". */
    private App app = new App();

    /** Base URL for the GitHub REST API. Override for GitHub Enterprise Server. */
    private String apiBaseUrl = "https://api.github.com";

    /** Optional default org, used only for convenience/documentation purposes. */
    private String defaultOrg;

    /** Max number of repositories processed concurrently. */
    private int maxConcurrency = 10;

    /** Max retry attempts for a single GitHub API call before giving up. */
    private int maxRetries = 5;

    /** Base delay (ms) used for exponential backoff on transient failures. */
    private long retryBaseDelayMs = 1000L;

    public String getAuthMode() {
        return authMode;
    }

    public void setAuthMode(String authMode) {
        this.authMode = authMode;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public App getApp() {
        return app;
    }

    public void setApp(App app) {
        this.app = app;
    }

    public String getApiBaseUrl() {
        return apiBaseUrl;
    }

    public void setApiBaseUrl(String apiBaseUrl) {
        this.apiBaseUrl = apiBaseUrl;
    }

    public String getDefaultOrg() {
        return defaultOrg;
    }

    public void setDefaultOrg(String defaultOrg) {
        this.defaultOrg = defaultOrg;
    }

    public int getMaxConcurrency() {
        return maxConcurrency;
    }

    public void setMaxConcurrency(int maxConcurrency) {
        this.maxConcurrency = maxConcurrency;
    }

    public int getMaxRetries() {
        return maxRetries;
    }

    public void setMaxRetries(int maxRetries) {
        this.maxRetries = maxRetries;
    }

    public long getRetryBaseDelayMs() {
        return retryBaseDelayMs;
    }

    public void setRetryBaseDelayMs(long retryBaseDelayMs) {
        this.retryBaseDelayMs = retryBaseDelayMs;
    }

    public static class App {
        private String appId;
        private String privateKeyPath;
        private String installationId;

        public String getAppId() {
            return appId;
        }

        public void setAppId(String appId) {
            this.appId = appId;
        }

        public String getPrivateKeyPath() {
            return privateKeyPath;
        }

        public void setPrivateKeyPath(String privateKeyPath) {
            this.privateKeyPath = privateKeyPath;
        }

        public String getInstallationId() {
            return installationId;
        }

        public void setInstallationId(String installationId) {
            this.installationId = installationId;
        }
    }
}
