package com.example.githubaccessreport.service;

import com.example.githubaccessreport.config.ReportProperties;
import com.example.githubaccessreport.model.AccessReport;
import com.example.githubaccessreport.model.CollaboratorAccess;
import com.example.githubaccessreport.model.PermissionLevel;
import com.example.githubaccessreport.model.RepoAccessEntry;
import com.example.githubaccessreport.model.UserAccessEntry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AccessReportServiceTest {

    private GitHubDataService gitHubDataService;
    private AccessReportService accessReportService;

    @BeforeEach
    void setUp() {
        gitHubDataService = Mockito.mock(GitHubDataService.class);
        ReportProperties reportProperties = new ReportProperties();
        reportProperties.setCacheTtlMs(300_000L);
        accessReportService = new AccessReportService(gitHubDataService, reportProperties);
    }

    @Test
    void aggregatesRepoCentricDataIntoUserCentricReport() {
        List<RepoAccessEntry> repoData = List.of(
                new RepoAccessEntry("repo-a", "acme/repo-a", true, false, "private", List.of(
                        new CollaboratorAccess("alice", 1L, PermissionLevel.ADMIN, "direct"),
                        new CollaboratorAccess("bob", 2L, PermissionLevel.WRITE, "team_or_org")
                )),
                new RepoAccessEntry("repo-b", "acme/repo-b", false, false, "public", List.of(
                        new CollaboratorAccess("alice", 1L, PermissionLevel.READ, "direct")
                ))
        );
        when(gitHubDataService.fetchOrgAccessData("acme")).thenReturn(repoData);

        AccessReport report = accessReportService.getAccessReport("acme", true);

        assertEquals("acme", report.organization());
        assertEquals(2, report.summary().repositoryCount());
        assertEquals(2, report.summary().userCount());

        Optional<UserAccessEntry> alice = report.users().stream().filter(u -> u.login().equals("alice")).findFirst();
        assertTrue(alice.isPresent());
        assertEquals(2, alice.get().repositoryCount());
        assertEquals("repo-a", alice.get().repositories().get(0).repository());
        assertEquals(PermissionLevel.ADMIN, alice.get().repositories().get(0).permission());

        Optional<UserAccessEntry> bob = report.users().stream().filter(u -> u.login().equals("bob")).findFirst();
        assertTrue(bob.isPresent());
        assertEquals(1, bob.get().repositoryCount());
    }

    @Test
    void repoWithAccessErrorIsExcludedFromUsersAndReportedAsUnverified() {
        List<RepoAccessEntry> repoData = List.of(
                new RepoAccessEntry("repo-a", "acme/repo-a", true, false, "private", List.of(
                        new CollaboratorAccess("alice", 1L, PermissionLevel.ADMIN, "direct")
                )),
                new RepoAccessEntry("repo-b", "acme/repo-b", true, false, "private", List.of(),
                        "insufficient permissions to view collaborators (requires push access to this repo)")
        );
        when(gitHubDataService.fetchOrgAccessData("acme")).thenReturn(repoData);

        AccessReport report = accessReportService.getAccessReport("acme", true);

        // repo-b couldn't be verified, so it must not silently count as "zero collaborators"
        assertEquals(2, report.summary().repositoryCount());
        assertEquals(1, report.summary().userCount());
        assertEquals(1, report.summary().unverifiedRepositoryCount());
        assertEquals(List.of("repo-b"), report.unverifiedRepositories());

        Optional<UserAccessEntry> alice = report.users().stream().filter(u -> u.login().equals("alice")).findFirst();
        assertTrue(alice.isPresent());
    }

    @Test
    void servesCachedResultOnSubsequentCallsWithoutRefetching() {
        when(gitHubDataService.fetchOrgAccessData(anyString())).thenReturn(List.of());

        accessReportService.getAccessReport("acme", true);
        accessReportService.getAccessReport("acme", false);

        verify(gitHubDataService, times(1)).fetchOrgAccessData("acme");
    }

    @Test
    void bypassesCacheWhenForceRefreshIsSet() {
        when(gitHubDataService.fetchOrgAccessData(anyString())).thenReturn(List.of());

        accessReportService.getAccessReport("acme", true);
        accessReportService.getAccessReport("acme", true);

        verify(gitHubDataService, times(2)).fetchOrgAccessData("acme");
    }
}
