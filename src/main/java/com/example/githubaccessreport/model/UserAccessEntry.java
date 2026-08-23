package com.example.githubaccessreport.model;

import java.util.List;

/** A single user and every repository they have access to. */
public record UserAccessEntry(
        String login,
        long userId,
        int repositoryCount,
        List<UserRepoAccess> repositories
) {
}
