package com.example.githubaccessreport.web;

import com.example.githubaccessreport.github.GitHubApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/**
 * Centralizes error translation so route handlers stay simple. GitHub
 * failures (auth, missing org, upstream outages) are mapped to sensible,
 * stable HTTP statuses rather than leaking raw exception details.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<Map<String, String>> handleApiException(ApiException e) {
        return ResponseEntity.status(e.getStatus()).body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(GitHubApiException.class)
    public ResponseEntity<Map<String, String>> handleGitHubApiException(GitHubApiException e) {
        log.error("GitHub API error (status {}): {}", e.getStatus(), e.getMessage());
        return switch (e.getStatus()) {
            case 401 -> respond(HttpStatus.BAD_GATEWAY,
                    "GitHub authentication failed. Check the configured token/App credentials.");
            case 403 -> respond(HttpStatus.BAD_GATEWAY,
                    "GitHub denied the request (insufficient permissions or rate limit exhausted after retries).");
            case 404 -> respond(HttpStatus.NOT_FOUND, "The requested GitHub organization or resource was not found.");
            default -> {
                if (e.getStatus() >= 500) {
                    yield respond(HttpStatus.BAD_GATEWAY, "GitHub API is currently unavailable. Please retry shortly.");
                }
                yield respond(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected error while communicating with GitHub.");
            }
        };
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleUnexpected(Exception e) {
        log.error("Unhandled exception", e);
        return respond(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred.");
    }

    private ResponseEntity<Map<String, String>> respond(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(Map.of("error", message));
    }
}
