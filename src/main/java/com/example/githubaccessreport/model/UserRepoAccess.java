package com.example.githubaccessreport.model;

/** One repository a given user has access to, and how. */
public record UserRepoAccess(
        String repository,
        PermissionLevel permission,
        String affiliation
) {
}
