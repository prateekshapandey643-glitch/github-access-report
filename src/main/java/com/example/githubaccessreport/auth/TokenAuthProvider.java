package com.example.githubaccessreport.auth;

/**
 * Auth provider backed by a static Personal Access Token (classic or
 * fine-grained). Simplest option — good for local use or a single org.
 */
public class TokenAuthProvider implements GitHubAuthProvider {

    private final String token;

    public TokenAuthProvider(String token) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException(
                    "github.token must be set when github.auth-mode=token. See README for setup.");
        }
        this.token = token;
    }

    @Override
    public String getAuthorizationHeader() {
        return "Bearer " + token;
    }
}
