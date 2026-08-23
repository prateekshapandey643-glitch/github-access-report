package com.example.githubaccessreport;

import com.example.githubaccessreport.config.GitHubProperties;
import com.example.githubaccessreport.config.ReportProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({GitHubProperties.class, ReportProperties.class})
public class GithubAccessReportApplication {

    public static void main(String[] args) {
        SpringApplication.run(GithubAccessReportApplication.class, args);
    }
}
