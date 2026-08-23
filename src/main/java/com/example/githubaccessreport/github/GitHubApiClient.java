package com.example.githubaccessreport.github;

import com.example.githubaccessreport.auth.GitHubAuthProvider;
import com.example.githubaccessreport.config.GitHubProperties;
import com.example.githubaccessreport.github.dto.CollaboratorDto;
import com.example.githubaccessreport.github.dto.RepoDto;
import com.example.githubaccessreport.util.RetryExecutor;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Thin wrapper around GitHub's REST API for the two endpoints this service
 * needs: listing an org's repositories and listing a repo's collaborators.
 * <p>
 * Handles pagination (looping pages until a short page is returned) and
 * status-code translation into {@link GitHubApiException}, which
 * {@link RetryExecutor} then uses to decide whether/how long to back off.
 */
public class GitHubApiClient {

    private static final int PER_PAGE = 100;

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final GitHubAuthProvider authProvider;
    private final GitHubProperties properties;

    public GitHubApiClient(GitHubAuthProvider authProvider, GitHubProperties properties) {
        this.authProvider = authProvider;
        this.properties = properties;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    /** Lists every repository in the given organization ({@code type=all}: public, private, forks, etc). */
    public List<RepoDto> listOrgRepos(String org) {
        String pathTemplate = "/orgs/" + encode(org) + "/repos?type=all&per_page=" + PER_PAGE + "&page=%d";
        return paginate(pathTemplate, RepoDto[].class);
    }

    /**
     * Lists collaborators for a repo under a given affiliation scope.
     *
     * @param affiliation "direct" (explicitly added, including outside collaborators)
     *                    or "all" (direct + inherited via team/org default permissions)
     */
    public List<CollaboratorDto> listCollaborators(String org, String repo, String affiliation) {
        String pathTemplate = "/repos/" + encode(org) + "/" + encode(repo)
                + "/collaborators?affiliation=" + affiliation + "&per_page=" + PER_PAGE + "&page=%d";
        return paginate(pathTemplate, CollaboratorDto[].class);
    }

    private <T> List<T> paginate(String pathTemplateWithPagePlaceholder, Class<T[]> arrayType) {
        List<T> results = new ArrayList<>();
        int page = 1;
        while (true) {
            final int currentPage = page;
            T[] pageItems = RetryExecutor.executeWithRetry(
                    () -> fetchPage(String.format(pathTemplateWithPagePlaceholder, currentPage), arrayType),
                    properties.getMaxRetries(),
                    properties.getRetryBaseDelayMs());
            results.addAll(List.of(pageItems));
            if (pageItems.length < PER_PAGE) {
                break;
            }
            page++;
        }
        return results;
    }

    private <T> T[] fetchPage(String path, Class<T[]> arrayType) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(properties.getApiBaseUrl() + path))
                .timeout(Duration.ofSeconds(30))
                .header("Authorization", authProvider.getAuthorizationHeader())
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new GitHubApiException(response.statusCode(), extractMessage(response), lowercaseHeaders(response));
        }

        return objectMapper.readValue(response.body(), arrayType);
    }

    private String extractMessage(HttpResponse<String> response) {
        try {
            var node = objectMapper.readTree(response.body());
            if (node.has("message")) {
                return node.get("message").asText();
            }
        } catch (Exception ignored) {
            // fall through to generic message
        }
        return "GitHub API request failed with status " + response.statusCode();
    }

    private Map<String, String> lowercaseHeaders(HttpResponse<String> response) {
        Map<String, String> headers = new HashMap<>();
        response.headers().map().forEach((key, values) -> {
            if (!values.isEmpty()) {
                headers.put(key.toLowerCase(), values.get(0));
            }
        });
        return headers;
    }

    private static String encode(String value) {
        return java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8);
    }
}
