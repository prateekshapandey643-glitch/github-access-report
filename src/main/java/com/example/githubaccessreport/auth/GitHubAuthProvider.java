package com.example.githubaccessreport.auth;

/**
 * Produces the {@code Authorization} header value to use on outbound GitHub
 * API requests. Implementations may cache/refresh credentials internally
 * (see {@link GitHubAppAuthProvider}).
 */
public interface GitHubAuthProvider {
    String getAuthorizationHeader();
}
