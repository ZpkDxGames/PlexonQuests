package com.zpkdxgames.plexonquests.integration;

public record IntegrationState(
        String id,
        String pluginName,
        IntegrationStatus status,
        String detectedVersion,
        String detail) {}

