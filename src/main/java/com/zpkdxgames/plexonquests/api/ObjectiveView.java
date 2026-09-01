package com.zpkdxgames.plexonquests.api;

/** Immutable objective progress exposed to other plugins. */
public record ObjectiveView(String id, String type, String display, long current, long required, boolean complete) {}
