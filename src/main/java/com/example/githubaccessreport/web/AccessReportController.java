package com.example.githubaccessreport.web;

import com.example.githubaccessreport.model.AccessReport;
import com.example.githubaccessreport.service.AccessReportService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/api")
public class AccessReportController {

    private static final Pattern VALID_ORG_LOGIN = Pattern.compile("^[a-zA-Z0-9-_.]+$");

    private final AccessReportService accessReportService;

    public AccessReportController(AccessReportService accessReportService) {
        this.accessReportService = accessReportService;
    }

    /**
     * Returns the user-centric access report for a GitHub organization:
     * every user with access to at least one repo, which repos, and at
     * what permission level.
     *
     * @param refresh set {@code true} to bypass the in-memory cache and pull fresh data from GitHub
     */
    @GetMapping("/orgs/{org}/access-report")
    public AccessReport getAccessReport(
            @PathVariable String org,
            @RequestParam(name = "refresh", defaultValue = "false") boolean refresh) {

        if (!VALID_ORG_LOGIN.matcher(org).matches()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "A valid GitHub organization login must be provided in the path.");
        }

        return accessReportService.getAccessReport(org, refresh);
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "ok");
    }
}
