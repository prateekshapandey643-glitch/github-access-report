# GitHub Access Report (Java / Spring Boot)

A service that connects to GitHub, inventories a GitHub organization's repositories and collaborators, and exposes an API endpoint returning a structured **user → repositories access report** in JSON.

## Table of contents

- [How it works](#how-it-works)
- [Running the project](#running-the-project)
- [Authentication configuration](#authentication-configuration)
- [Calling the API](#calling-the-api)
- [Scaling & efficiency notes](#scaling--efficiency-notes)
- [Error handling](#error-handling)
- [Assumptions & design decisions](#assumptions--design-decisions)
- [Testing](#testing)
- [Project structure](#project-structure)

## How it works

1. Authenticate against the GitHub REST API (PAT or GitHub App — see below).
2. List all repositories in the target org (`GET /orgs/{org}/repos`, paginated).
3. For each repository, list its collaborators and their permission levels (`GET /repos/{owner}/{repo}/collaborators`, paginated) — fetched **in parallel** across repos via a bounded thread pool.
4. Aggregate the repo-centric data into a user-centric map: for every user, which repos they can access and at what permission level.
5. Serve the result as JSON from `GET /api/orgs/{org}/access-report`, with a short-lived in-memory cache so repeated calls don't re-hit GitHub every time.

## Running the project

Requirements: JDK 17+, Maven 3.9+.

```bash
git clone https://github.com/prateekshapandey643-glitch/github-access-report.git
cd github-access-report
cp .env.example .env
# edit .env — at minimum set GITHUB_TOKEN (see Authentication section), then:
export $(grep -v '^#' .env | xargs)

mvn spring-boot:run
# or build a jar and run it directly:
mvn clean package
java -jar target/github-access-report-1.0.0.jar
```

The server listens on `PORT` (default `8080`).

## Authentication configuration

Two auth mechanisms are supported, selected via `GITHUB_AUTH_MODE`:

### Option A — Personal Access Token (`GITHUB_AUTH_MODE=token`, default)

Simplest setup, good for local use or a single organization.

1. Create a token at GitHub → Settings → Developer settings → Personal access tokens.
   - **Classic token** scopes needed: `read:org`, `repo` (to read collaborators on private repos).
   - **Fine-grained token**: grant it access to the target org, with **Organization permissions → Members: Read-only** and **Repository permissions → Administration: Read-only** (collaborator/permission listing requires Administration read) plus **Metadata: Read-only**.
2. Set `GITHUB_TOKEN=<your token>`.

### Option B — GitHub App (`GITHUB_AUTH_MODE=app`)

Recommended for production/org-wide deployments: access is scoped to an app installation rather than a personal account, and tokens auto-refresh.

1. Register a GitHub App on the organization with the same permissions as above (Organization: Members read; Repository: Administration + Metadata read).
2. Install the app on the organization and note the **Installation ID**.
3. Download the app's private key. GitHub gives it to you in **PKCS#1** format (`-----BEGIN RSA PRIVATE KEY-----`); convert it once to PKCS#8, which this service expects:
   ```bash
   openssl pkcs8 -topk8 -inform PEM -outform PEM -nocrypt \
     -in original-app-key.pem -out private-key-pkcs8.pem
   ```
4. Set:
   ```
   GITHUB_AUTH_MODE=app
   GITHUB_APP_ID=<app id>
   GITHUB_APP_PRIVATE_KEY_PATH=./private-key-pkcs8.pem
   GITHUB_APP_INSTALLATION_ID=<installation id>
   ```

The app JWT is signed manually with `java.security.Signature` (RS256) — no external JWT library — and exchanged for a short-lived installation access token that's cached and auto-refreshed a minute before expiry (`GitHubAppAuthProvider`).

The service fails fast at startup with a descriptive error if required credentials for the selected mode are missing (`TokenAuthProvider` / `GitHubAppAuthProvider` / `GitHubClientConfig`).

## Calling the API

### `GET /api/orgs/{org}/access-report`

Returns the full access report for the given organization login.

```bash
curl http://localhost:8080/api/orgs/my-org/access-report
```

Query params:

| Param     | Description                                                        |
|-----------|----------------------------------------------------------------------|
| `refresh` | `refresh=true` forces a fresh pull from GitHub, bypassing the cache. |

Example response (truncated):

```json
{
  "organization": "my-org",
  "generatedAt": "2026-08-23T10:15:00Z",
  "summary": {
    "repositoryCount": 128,
    "userCount": 1043,
    "unverifiedRepositoryCount": 0
  },
  "users": [
    {
      "login": "alice",
      "userId": 123456,
      "repositoryCount": 3,
      "repositories": [
        { "repository": "backend-api", "permission": "admin", "affiliation": "direct" },
        { "repository": "infra-tools", "permission": "write", "affiliation": "team_or_org" },
        { "repository": "public-docs", "permission": "read", "affiliation": "direct" }
      ]
    }
  ],
  "unverifiedRepositories": []
}
```

Field notes:
- `permission` is GitHub's standard access level: `admin`, `maintain`, `write`, `triage`, or `read`.
- `affiliation` is `direct` (explicitly added to that repo, including outside collaborators) or `team_or_org` (access inherited via a team or an organization-wide default permission).
- `unverifiedRepositoryCount` / `unverifiedRepositories` — repos where collaborator access could **not** be checked (e.g. the token lacks sufficient permission on that specific repo — GitHub requires push access just to list a repo's collaborators). These are excluded from `userCount`/`users`, so a low `userCount` alongside a nonzero `unverifiedRepositoryCount` means "access unknown for those repos," not "nobody has access."

### `GET /api/health`

Basic liveness check, returns `{"status": "ok"}`.

## Scaling & efficiency notes

Designed against the stated target of 100+ repositories and 1000+ users with access:

- **Pagination everywhere.** Both the repo list and each repo's collaborator list are paginated (`GitHubApiClient.paginate`), looping `per_page=100` pages until a short page comes back — `ceil(n/100)` requests, not one request per item.
- **Bounded parallelism, not sequential scanning.** `GitHubDataService` fetches all repos' collaborators through a fixed-size thread pool (`github.max-concurrency`, default 10) via `CompletableFuture.supplyAsync`, so up to 10 repos are being processed at once instead of one-at-a-time — the main lever for staying fast at 100+ repos while not overwhelming GitHub's rate limiter.
- **Rate-limit-aware retries.** `RetryExecutor` inspects `x-ratelimit-remaining` / `x-ratelimit-reset` and `Retry-After` response headers to back off exactly as long as GitHub asks (both primary and secondary/abuse rate limits), instead of guessing or hammering the API.
- **Per-organization response cache.** Building a report costs `O(repos)` API calls; a short TTL cache (`report.cache-ttl-ms`, default 5 minutes, `TtlCache`) avoids repeating that work for back-to-back requests. Pass `?refresh=true` for up-to-the-second data.
- **Linear aggregation.** The repo → user pivot (`AccessReportService.aggregateByUser`) does a single pass with a `HashMap` keyed by login, so aggregating 1000+ users across 100+ repos is linear in the number of (user, repo) access pairs, not quadratic.
- **Partial-failure resilience, without hiding what failed.** If one repo's collaborator fetch fails after retries, `GitHubDataService.fetchRepoAccessSafely` marks that repo as unverified (rather than silently reporting an empty collaborator list) and logs a warning, so a single flaky or inaccessible repo among hundreds doesn't block visibility into the rest — and doesn't get misread as "verified zero access" either.

## Error handling

- Startup fails fast with a descriptive error if required auth configuration is missing or invalid (`GitHubClientConfig`, `TokenAuthProvider`, `GitHubAppAuthProvider`).
- All outbound GitHub calls go through `RetryExecutor`, which retries transient (5xx / network) and rate-limit errors, and re-throws anything else (bad credentials, 404s) immediately.
- `GitHubApiException` carries the HTTP status and response headers needed to make that retry decision.
- `ApiExceptionHandler` (`@RestControllerAdvice`) centrally maps failures to HTTP responses: `404` for an unknown org, `502` for auth failures or GitHub outages/rate-limit exhaustion, `400` for a malformed org name, `500` as a generic fallback — so controller code stays a simple one-liner.
- **Per-repo access failures are surfaced, not swallowed.** A `403`/`404` when listing a repo's collaborators (typically: the token lacks push access to that repo) is caught, logged with the reason, and recorded on that repo as `accessError` rather than being treated as "this repo has zero collaborators." The aggregation step then reports it under `unverifiedRepositoryCount`/`unverifiedRepositories` in the API response — so a caller can tell "verified: nobody has access" apart from "we don't know."

## Assumptions & design decisions

- **"Access" = repository collaborators, including inherited access.** The report includes anyone who can access a repo whether added directly, as an outside collaborator, or via team/organization-wide permissions — from `GET /repos/{owner}/{repo}/collaborators?affiliation=all`. It doesn't separately enumerate *which team* granted access; `affiliation: "team_or_org"` signals "not a direct grant" without naming the team. Extending to per-team breakdowns would mean also calling the Teams API, and is a natural next step rather than something implemented here, to keep request volume proportional to repos rather than repos × teams.
- **Archived repositories are included** (flagged via `archived: true`) since access still exists even if the repo isn't actively developed. Filtering them out is a one-line change in `GitHubApiClient.listOrgRepos`.
- **"Unverified" is a distinct outcome from "verified, zero access."** GitHub requires push access to a repo just to list its collaborators, so a token that's valid for the org but lacks that specific permission will get `403`s on some repos. Rather than let those look identical to "confirmed, nobody has access" (which a security-focused access report should never conflate), such repos are excluded from the user aggregation and called out separately (see `unverifiedRepositoryCount` above).
- **The report is user-centric** (`user → repos`) per the "aggregated view mapping users to the repositories they can access" requirement; the intermediate repo-centric data (`RepoAccessEntry`, `repo → collaborators`) is computed first in `GitHubDataService` and is easy to expose as its own endpoint if a repo-centric view is also wanted.
- **Caching is in-memory and per-process** (`TtlCache`). Fine for a single-instance deployment. If deployed with multiple replicas, swap it for a shared store (Redis, etc.) behind the same `get/put/invalidate` interface without touching calling code.
- **Auth defaults to a PAT** for simplicity of local setup/evaluation, with GitHub App support included since it's the better fit for a real production deployment (no dependency on one person's token, explicit installation scope, auto-refreshing credentials).
- **Organization login is a path parameter**, not a query param (`/api/orgs/{org}/access-report`), consistent with GitHub's own API shape (`/orgs/{org}/...`).
- **Java's built-in `java.net.http.HttpClient`** is used for outbound calls rather than a GitHub SDK, keeping the dependency surface small and every HTTP/retry/pagination decision explicit and auditable in `GitHubApiClient` / `RetryExecutor`.

## Testing

```bash
mvn test
```

`AccessReportServiceTest` covers the aggregation logic (repo-centric → user-centric pivot, cache hit vs. forced refresh) with `GitHubDataService` mocked out via Mockito, so it runs without any network access or real GitHub credentials.

## Project structure

```
src/main/java/com/example/githubaccessreport/
  GithubAccessReportApplication.java   # entrypoint
  config/
    GitHubProperties.java              # github.* config binding
    ReportProperties.java              # report.* config binding
    GitHubClientConfig.java            # wires auth provider + API client beans
  auth/
    GitHubAuthProvider.java            # interface
    TokenAuthProvider.java             # PAT auth
    GitHubAppAuthProvider.java         # GitHub App installation-token auth (auto-refreshing)
    JwtUtil.java                       # RS256 JWT signing for GitHub App auth
  github/
    GitHubApiClient.java               # HTTP calls, pagination, status handling
    GitHubApiException.java
    dto/RepoDto.java, CollaboratorDto.java
  model/
    AccessReport.java, UserAccessEntry.java, UserRepoAccess.java,
    RepoAccessEntry.java, CollaboratorAccess.java, PermissionLevel.java
  service/
    GitHubDataService.java             # concurrent repo + collaborator fetching
    AccessReportService.java           # aggregation + caching
  web/
    AccessReportController.java        # GET /api/orgs/{org}/access-report, /api/health
    ApiException.java, ApiExceptionHandler.java
  util/
    RetryExecutor.java                 # rate-limit-aware exponential backoff
    TtlCache.java                      # in-memory TTL cache
src/test/java/.../AccessReportServiceTest.java
```
