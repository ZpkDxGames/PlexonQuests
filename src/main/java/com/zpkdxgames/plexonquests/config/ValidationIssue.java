package com.zpkdxgames.plexonquests.config;

public record ValidationIssue(Severity severity, String path, String message) {
    public enum Severity {
        WARNING,
        ERROR
    }
}

