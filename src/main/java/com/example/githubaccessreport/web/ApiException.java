package com.example.githubaccessreport.web;

import org.springframework.http.HttpStatus;

/** An exception that should be surfaced to the caller as a specific HTTP status + message. */
public class ApiException extends RuntimeException {

    private final HttpStatus status;

    public ApiException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
