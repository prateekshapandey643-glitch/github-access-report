package com.example.githubaccessreport.model;

/** A single user's access grant on a single repository. */
public record CollaboratorAccess(
        String login,
        long userId,
        PermissionLevel permission,
        /** "direct" (explicitly added, including outside collaborators) or "team_or_org" (inherited). */
        String affiliation
) {
}
