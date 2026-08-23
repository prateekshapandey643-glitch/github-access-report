package com.example.githubaccessreport.github.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

/** Minimal projection of GitHub's collaborator JSON object. */
@JsonIgnoreProperties(ignoreUnknown = true)
public class CollaboratorDto {

    private String login;

    private long id;

    /** e.g. {"admin": true, "maintain": false, "push": true, "triage": true, "pull": true} */
    private Map<String, Boolean> permissions;

    /** GitHub's computed highest role name, e.g. "admin", "write", or a custom repo role. */
    @JsonProperty("role_name")
    private String roleName;

    public String getLogin() {
        return login;
    }

    public void setLogin(String login) {
        this.login = login;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public Map<String, Boolean> getPermissions() {
        return permissions;
    }

    public void setPermissions(Map<String, Boolean> permissions) {
        this.permissions = permissions;
    }

    public String getRoleName() {
        return roleName;
    }

    public void setRoleName(String roleName) {
        this.roleName = roleName;
    }
}
