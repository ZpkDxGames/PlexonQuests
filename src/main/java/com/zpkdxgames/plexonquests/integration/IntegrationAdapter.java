package com.zpkdxgames.plexonquests.integration;

/** One optional provider bridge. Adapters are discovered once and register only verified public event/API surfaces. */
public interface IntegrationAdapter {
    String id();

    void register(IntegrationContext context);
}
