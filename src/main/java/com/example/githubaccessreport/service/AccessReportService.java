package com.example.githubaccessreport.service;

import com.example.githubaccessreport.config.ReportProperties;
import com.example.githubaccessreport.model.AccessReport;
import com.example.githubaccessreport.model.CollaboratorAccess;
import com.example.githubaccessreport.model.RepoAccessEntry;
import com.example.githubaccessreport.model.UserAccessEntry;
import com.example.githubaccessreport.model.UserRepoAccess;
import com.example.githubaccessreport.util.TtlCache;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Aggregates repo -> [collaborators] into user -> [repositories], and caches
 * the resulting report per org for a short TTL so repeated requests don't
 * re-walk every repo on GitHub each time.
 */
@Service
public class AccessReportService {

    private final GitHubDataService gitHubDataService;
    private final TtlCache<String, AccessReport> cache;

    public AccessReportService(GitHubDataService gitHubDataService, ReportProperties reportProperties) {
        this.gitHubDataService = gitHubDataService;
        this.cache = new TtlCache<>(reportProperties.getCacheTtlMs());
    }

    public AccessReport getAccessReport(String org, boolean forceRefresh) {
        if (!forceRefresh) {
            AccessReport cached = cache.get(org);
            if (cached != null) {
                return cached;
            }
        }

        List<RepoAccessEntry> repoEntries = gitHubDataService.fetchOrgAccessData(org);
        AccessReport report = aggregateByUser(org, repoEntries);

        cache.put(org, report);
        return report;
    }

    /**
     * Single pass over every (repo, collaborator) pair, keyed by login in a
     * {@link LinkedHashMap} for O(1) lookups — linear in the number of
     * access grants, not quadratic, so it stays cheap at 100+ repos and
     * 1000+ users.
     */
    private AccessReport aggregateByUser(String org, List<RepoAccessEntry> repoEntries) {
        Map<String, MutableUserEntry> byUser = new LinkedHashMap<>();
        List<String> unverified = new ArrayList<>();

        for (RepoAccessEntry repo : repoEntries) {
            if (repo.accessError() != null) {
                unverified.add(repo.name());
                continue;
            }
            for (CollaboratorAccess collaborator : repo.collaborators()) {
                MutableUserEntry entry = byUser.computeIfAbsent(
                        collaborator.login(),
                        login -> new MutableUserEntry(login, collaborator.userId()));
                entry.repositories.add(new UserRepoAccess(repo.name(), collaborator.permission(), collaborator.affiliation()));
            }
        }

        List<UserAccessEntry> users = byUser.values().stream()
                .sorted(Comparator.comparing(u -> u.login))
                .map(MutableUserEntry::toImmutable)
                .toList();

        return new AccessReport(
                org,
                Instant.now().toString(),
                new AccessReport.Summary(repoEntries.size(), users.size(), unverified.size()),
                users,
                unverified);
    }

    /** Mutable accumulator used only during aggregation; converted to the immutable {@link UserAccessEntry} record. */
    private static final class MutableUserEntry {
        private final String login;
        private final long userId;
        private final List<UserRepoAccess> repositories = new ArrayList<>();

        private MutableUserEntry(String login, long userId) {
            this.login = login;
            this.userId = userId;
        }

        private UserAccessEntry toImmutable() {
            List<UserRepoAccess> sorted = repositories.stream()
                    .sorted(Comparator.comparing(UserRepoAccess::repository))
                    .toList();
            return new UserAccessEntry(login, userId, sorted.size(), sorted);
        }
    }
}
