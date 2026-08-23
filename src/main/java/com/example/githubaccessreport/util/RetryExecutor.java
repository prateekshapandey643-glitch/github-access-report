package com.example.githubaccessreport.util;

import com.example.githubaccessreport.github.GitHubApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Callable;

/**
 * Executes a GitHub API call, retrying on rate limits and transient server
 * errors with an appropriate delay, and re-throwing immediately on anything
 * that isn't retryable (bad credentials, validation errors, 404s, etc).
 */
public final class RetryExecutor {

    private static final Logger log = LoggerFactory.getLogger(RetryExecutor.class);
    private static final long MAX_DELAY_MS = 5 * 60 * 1000L; // never sleep more than 5 minutes

    private RetryExecutor() {
    }

    public static <T> T executeWithRetry(Callable<T> action, int maxRetries, long baseDelayMs) {
        int attempt = 0;
        while (true) {
            attempt++;
            try {
                return action.call();
            } catch (GitHubApiException e) {
                Long delayMs = attempt <= maxRetries ? computeDelayMs(e, attempt, baseDelayMs) : null;
                if (delayMs == null) {
                    throw e;
                }
                log.warn("Retryable GitHub API error (status {}), attempt {}/{}, backing off {}ms: {}",
                        e.getStatus(), attempt, maxRetries, delayMs, e.getMessage());
                sleep(delayMs);
            } catch (Exception e) {
                if (attempt > maxRetries) {
                    throw new RuntimeException("GitHub API call failed after " + maxRetries + " retries", e);
                }
                long delayMs = Math.min(baseDelayMs * (1L << (attempt - 1)), MAX_DELAY_MS);
                log.warn("Transient error calling GitHub API, attempt {}/{}, backing off {}ms: {}",
                        attempt, maxRetries, delayMs, e.getMessage());
                sleep(delayMs);
            }
        }
    }

    /**
     * Returns the delay (ms) to wait before retrying, or {@code null} if the
     * error is not retryable and should propagate immediately.
     */
    private static Long computeDelayMs(GitHubApiException e, int attempt, long baseDelayMs) {
        int status = e.getStatus();

        // Primary rate limit exhausted — GitHub tells us exactly when to resume.
        String remaining = e.getHeaders().get("x-ratelimit-remaining");
        String reset = e.getHeaders().get("x-ratelimit-reset");
        if (status == 403 && "0".equals(remaining) && reset != null) {
            long resetAtMs = Long.parseLong(reset) * 1000L;
            long waitMs = Math.max(resetAtMs - System.currentTimeMillis(), 1000L);
            return Math.min(waitMs, MAX_DELAY_MS);
        }

        // Secondary rate limit (abuse detection) — honor Retry-After if present.
        String retryAfter = e.getHeaders().get("retry-after");
        if (status == 403 && retryAfter != null) {
            return Long.parseLong(retryAfter) * 1000L;
        }

        // Transient server-side errors: exponential backoff.
        if (status == 502 || status == 503 || status == 504) {
            return Math.min(baseDelayMs * (1L << (attempt - 1)), MAX_DELAY_MS);
        }

        return null; // 401, 403 (non-rate-limit), 404, 422, etc. — not retryable
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted during retry backoff", ie);
        }
    }
}
