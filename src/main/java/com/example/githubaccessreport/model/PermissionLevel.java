package com.example.githubaccessreport.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/** GitHub's standard repository permission levels, ordered from highest to lowest. */
public enum PermissionLevel {
    ADMIN("admin"),
    MAINTAIN("maintain"),
    WRITE("write"),
    TRIAGE("triage"),
    READ("read");

    private final String value;

    PermissionLevel(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    /** Maps GitHub's role_name (or an unrecognized custom role) to the closest standard level. */
    @JsonCreator
    public static PermissionLevel fromValue(String value) {
        if (value == null) {
            return READ;
        }
        for (PermissionLevel level : values()) {
            if (level.value.equalsIgnoreCase(value)) {
                return level;
            }
        }
        return READ;
    }
}
