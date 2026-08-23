package com.example.githubaccessreport.github;

import java.util.Collections;
import java.util.Map;

/** Thrown when GitHub's API returns a non-2xx response. */
public class GitHubApiException extends RuntimeException {

    private final int status;
    private final Map<String, String> headers;

    public GitHubApiException(int status, String message, Map<String, String> headers) {
        super(message);
        this.status = status;
        this.headers = headers == null ? Collections.emptyMap() : headers;
    }

    public int getStatus() {
        return status;
    }

    public Map<String, String> getHeaders() {
        return headers;
    }
}
