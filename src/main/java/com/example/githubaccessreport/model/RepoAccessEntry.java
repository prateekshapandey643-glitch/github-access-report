package com.example.githubaccessreport.model;

import java.util.List;

/**
 * A repository plus everyone who can access it. Intermediate representation before user-centric aggregation.
 * <p>
 * {@code accessError}, when non-null, means the collaborator list could NOT be verified (e.g. the
 * token lacks push access to this repo, which GitHub requires just to list collaborators) —
 * as opposed to a verified, genuinely-empty {@code collaborators} list. Callers should treat repos
 * with an {@code accessError} as "unknown access", not "no access".
 */
public record RepoAccessEntry(
        String name,
        String fullName,
        boolean isPrivate,
        boolean archived,
        String visibility,
        List<CollaboratorAccess> collaborators,
        String accessError
) {
    /** Convenience constructor for the common case: access was checked successfully. */
    public RepoAccessEntry(String name, String fullName, boolean isPrivate, boolean archived,
                            String visibility, List<CollaboratorAccess> collaborators) {
        this(name, fullName, isPrivate, archived, visibility, collaborators, null);
    }
}
