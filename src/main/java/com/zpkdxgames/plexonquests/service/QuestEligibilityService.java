package com.zpkdxgames.plexonquests.service;

import com.zpkdxgames.plexonquests.integration.IntegrationManager;
import com.zpkdxgames.plexonquests.quest.PoolDefinition;
import com.zpkdxgames.plexonquests.quest.QuestDefinition;
import org.bukkit.entity.Player;

public final class QuestEligibilityService {
    private final IntegrationManager integrations;

    public QuestEligibilityService(IntegrationManager integrations) {
        this.integrations = integrations;
    }

    public EligibilityResult evaluate(Player player, PlayerProfile profile, QuestDefinition quest) {
        if (!quest.enabled()) {
            return EligibilityResult.denied("Quest is disabled");
        }
        if (!quest.eligibility().requiredPermission().isBlank()
                && !player.hasPermission(quest.eligibility().requiredPermission())
                && !player.hasPermission("plexonquests.bypass.eligibility")) {
            return EligibilityResult.denied("Missing permission " + quest.eligibility().requiredPermission());
        }
        for (String permission : quest.eligibility().blockedPermissions()) {
            if (player.hasPermission(permission) && !player.hasPermission("plexonquests.bypass.eligibility")) {
                return EligibilityResult.denied("Blocked by permission " + permission);
            }
        }
        if (!quest.eligibility().rankCategories().isEmpty()
                && !quest.eligibility().rankCategories().contains(profile.rankCategory())
                && !player.hasPermission("plexonquests.bypass.eligibility")) {
            return EligibilityResult.denied("Requires another rank category");
        }
        if (!quest.eligibility().worlds().isEmpty()
                && !quest.eligibility().worlds().contains(player.getWorld().getName())
                && !player.hasPermission("plexonquests.bypass.eligibility")) {
            return EligibilityResult.denied("Unavailable in this world");
        }
        for (String integration : quest.eligibility().requiredIntegrations()) {
            if (!integrations.available(integration)) {
                return EligibilityResult.denied(
                        integration + " is " + integrations.state(integration).status().name());
            }
        }
        return EligibilityResult.allowed();
    }

    public EligibilityResult evaluate(Player player, PlayerProfile profile, PoolDefinition pool) {
        boolean bypass = player.hasPermission("plexonquests.bypass.eligibility");
        if (!pool.enabled()) {
            return EligibilityResult.denied("Pool is disabled");
        }
        for (String permission : pool.requiredPermissions()) {
            if (!player.hasPermission(permission) && !bypass) {
                return EligibilityResult.denied("Missing permission " + permission);
            }
        }
        for (String permission : pool.blockedPermissions()) {
            if (player.hasPermission(permission) && !bypass) {
                return EligibilityResult.denied("Blocked by permission " + permission);
            }
        }
        if (!pool.rankCategories().isEmpty()
                && !pool.rankCategories().contains(profile.rankCategory())
                && !bypass) {
            return EligibilityResult.denied("Requires another rank category");
        }
        String world = player.getWorld().getName();
        if (!pool.worlds().isEmpty() && !pool.worlds().contains(world) && !bypass) {
            return EligibilityResult.denied("Unavailable in this world");
        }
        if (pool.excludedWorlds().contains(world) && !bypass) {
            return EligibilityResult.denied("Unavailable in this world");
        }
        for (String integration : pool.requiredIntegrations()) {
            if (!integrations.available(integration)) {
                return EligibilityResult.denied(
                        integration + " is " + integrations.state(integration).status().name());
            }
        }
        return EligibilityResult.allowed();
    }

    public record EligibilityResult(boolean eligible, String reason) {
        public static EligibilityResult allowed() {
            return new EligibilityResult(true, "");
        }

        public static EligibilityResult denied(String reason) {
            return new EligibilityResult(false, reason);
        }
    }
}
