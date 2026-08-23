package com.example.githubaccessreport.service;

import com.example.githubaccessreport.config.GitHubProperties;
import com.example.githubaccessreport.github.GitHubApiClient;
import com.example.githubaccessreport.github.GitHubApiException;
import com.example.githubaccessreport.github.dto.CollaboratorDto;
import com.example.githubaccessreport.github.dto.RepoDto;
import com.example.githubaccessreport.model.CollaboratorAccess;
import com.example.githubaccessreport.model.PermissionLevel;
import com.example.githubaccessreport.model.RepoAccessEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/**
 * Fetches repositories + per-repo collaborator access for an org.
 * <p>
 * Repo-level fetches run on a bounded thread pool
 * ({@code github.max-concurrency}, default 10) so 100+ repos are processed
 * in parallel rather than one request chain at a time — the main lever for
 * staying fast at scale without tripping GitHub's rate limits. Each
 * individual HTTP call is retried with backoff (see {@link GitHubApiClient}
 * / {@code RetryExecutor}).
 */
@Service
public class GitHubDataService {

    private static final Logger log = LoggerFactory.getLogger(GitHubDataService.class);

    private final GitHubApiClient apiClient;
    private final GitHubProperties properties;

    public GitHubDataService(GitHubApiClient apiClient, GitHubProperties properties) {
        this.apiClient = apiClient;
        this.properties = properties;
    }

    public List<RepoAccessEntry> fetchOrgAccessData(String org) {
        List<RepoDto> repos = apiClient.listOrgRepos(org);
        log.info("Fetched {} repositories for org '{}'", repos.size(), org);

        ExecutorService executor = Executors.newFixedThreadPool(properties.getMaxConcurrency());
        try {
            List<CompletableFuture<RepoAccessEntry>> futures = repos.stream()
                    .map(repo -> CompletableFuture.supplyAsync(() -> fetchRepoAccessSafely(org, repo), executor))
                    .collect(Collectors.toList());

            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

            return futures.stream().map(CompletableFuture::join).collect(Collectors.toList());
        } finally {
            executor.shutdown();
        }
    }

    /**
     * Wraps {@link #fetchRepoAccess} so one failing repo degrades gracefully instead of failing the
     * whole report. Crucially, a failure here is recorded as {@code accessError} on the entry rather
     * than an empty collaborator list — an empty list must always mean "verified, nobody has access",
     * never "we couldn't check". GitHub requires push access just to list a repo's collaborators, so
     * 403s here are expected whenever the token doesn't have real access to that repo.
     */
    private RepoAccessEntry fetchRepoAccessSafely(String org, RepoDto repo) {
        try {
            return fetchRepoAccess(org, repo);
        } catch (GitHubApiException e) {
            String reason = (e.getStatus() == 403 || e.getStatus() == 404)
                    ? "insufficient permissions to view collaborators (requires push access to this repo)"
                    : "GitHub API error (status " + e.getStatus() + "): " + e.getMessage();
            log.warn("Could not verify access for {}/{}: {}", org, repo.getName(), reason);
            return new RepoAccessEntry(
                    repo.getName(), repo.getFullName(), repo.isPrivate(), repo.isArchived(),
                    repo.getVisibility(), List.of(), reason);
        } catch (Exception e) {
            log.error("Failed to fetch collaborators for {}/{}: {}", org, repo.getName(), e.getMessage());
            return new RepoAccessEntry(
                    repo.getName(), repo.getFullName(), repo.isPrivate(), repo.isArchived(),
                    repo.getVisibility(), List.of(), "unexpected error: " + e.getMessage());
        }
    }

    private RepoAccessEntry fetchRepoAccess(String org, RepoDto repo) {
        List<CollaboratorDto> direct = apiClient.listCollaborators(org, repo.getName(), "direct");
        List<CollaboratorDto> all = apiClient.listCollaborators(org, repo.getName(), "all");

        Set<String> directLogins = direct.stream().map(CollaboratorDto::getLogin).collect(Collectors.toCollection(HashSet::new));

        List<CollaboratorAccess> collaborators = all.stream()
                .map(c -> new CollaboratorAccess(
                        c.getLogin(),
                        c.getId(),
                        derivePermission(c),
                        directLogins.contains(c.getLogin()) ? "direct" : "team_or_org"))
                .collect(Collectors.toList());

        return new RepoAccessEntry(
                repo.getName(), repo.getFullName(), repo.isPrivate(), repo.isArchived(),
                repo.getVisibility(), collaborators);
    }

    private PermissionLevel derivePermission(CollaboratorDto collaborator) {
        if (collaborator.getRoleName() != null) {
            return PermissionLevel.fromValue(collaborator.getRoleName());
        }
        var perms = collaborator.getPermissions();
        if (perms == null) {
            return PermissionLevel.READ;
        }
        if (Boolean.TRUE.equals(perms.get("admin"))) return PermissionLevel.ADMIN;
        if (Boolean.TRUE.equals(perms.get("maintain"))) return PermissionLevel.MAINTAIN;
        if (Boolean.TRUE.equals(perms.get("push"))) return PermissionLevel.WRITE;
        if (Boolean.TRUE.equals(perms.get("triage"))) return PermissionLevel.TRIAGE;
        return PermissionLevel.READ;
    }
}
