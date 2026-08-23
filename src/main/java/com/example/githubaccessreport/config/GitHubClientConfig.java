package com.example.githubaccessreport.config;

import com.example.githubaccessreport.auth.GitHubAppAuthProvider;
import com.example.githubaccessreport.auth.GitHubAuthProvider;
import com.example.githubaccessreport.auth.TokenAuthProvider;
import com.example.githubaccessreport.github.GitHubApiClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the {@link GitHubAuthProvider} implementation selected by
 * {@code github.auth-mode}, and the {@link GitHubApiClient} that uses it.
 * Fails fast at startup with a clear message if required credentials for
 * the selected mode are missing.
 */
@Configuration
public class GitHubClientConfig {

    @Bean
    public GitHubAuthProvider gitHubAuthProvider(GitHubProperties properties) {
        String mode = properties.getAuthMode();
        if ("token".equalsIgnoreCase(mode)) {
            return new TokenAuthProvider(properties.getToken());
        }
        if ("app".equalsIgnoreCase(mode)) {
            GitHubProperties.App app = properties.getApp();
            return new GitHubAppAuthProvider(
                    app.getAppId(), app.getPrivateKeyPath(), app.getInstallationId(), properties.getApiBaseUrl());
        }
        throw new IllegalStateException(
                "Invalid github.auth-mode '" + mode + "'. Use 'token' or 'app'. See README for configuration details.");
    }

    @Bean
    public GitHubApiClient gitHubApiClient(GitHubAuthProvider authProvider, GitHubProperties properties) {
        return new GitHubApiClient(authProvider, properties);
    }
}
