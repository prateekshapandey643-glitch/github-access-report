package com.example.githubaccessreport.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Binds the {@code report.*} configuration namespace. */
@ConfigurationProperties(prefix = "report")
public class ReportProperties {

    /** How long a generated access report is cached in memory before being recomputed. */
    private long cacheTtlMs = 300_000L;

    public long getCacheTtlMs() {
        return cacheTtlMs;
    }

    public void setCacheTtlMs(long cacheTtlMs) {
        this.cacheTtlMs = cacheTtlMs;
    }
}
