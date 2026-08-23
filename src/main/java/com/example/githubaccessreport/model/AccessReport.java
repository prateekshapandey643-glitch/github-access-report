package com.example.githubaccessreport.model;

import java.util.List;

/** The full user-centric access report for an organization. */
public record AccessReport(
        String organization,
        String generatedAt,
        Summary summary,
        List<UserAccessEntry> users,
        List<String> unverifiedRepositories
) {
    /**
     * @param repositoryCount        total repos found in the org
     * @param userCount              distinct users with VERIFIED access across all repos
     * @param unverifiedRepositoryCount repos where access could NOT be checked (e.g. token lacks
     *                                  push access) — these are excluded from userCount and users,
     *                                  so a low/zero userCount alongside a nonzero value here means
     *                                  "access unknown", not "no access".
     */
    public record Summary(int repositoryCount, int userCount, int unverifiedRepositoryCount) {
    }
}
