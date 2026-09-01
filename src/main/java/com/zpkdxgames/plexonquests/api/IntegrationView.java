package com.zpkdxgames.plexonquests.api;

/** Immutable provider diagnostic without plugin implementation objects. */
public record IntegrationView(String id, String pluginName, String status, String version, String detail) {}
