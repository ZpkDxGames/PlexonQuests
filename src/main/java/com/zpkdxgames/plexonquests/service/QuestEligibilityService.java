package com.zpkdxgames.plexonquests.service;

import com.zpkdxgames.plexonquests.integration.IntegrationManager;
import com.zpkdxgames.plexonquests.quest.PoolDefinition;
import com.zpkdxgames.plexonquests.quest.QuestDefinition;
import java.util.Set;
import org.bukkit.entity.Player;

public final class QuestEligibilityService {
    private final IntegrationManager integrations;
    private final QuestPrerequisiteService prerequisites;
    private final CompletionHistoryCache completionHistory;

    /** Backwards-compatible constructor used by 3.x integrations/tests without prerequisite metadata. */
    public QuestEligibilityService(IntegrationManager integrations) {
        this(integrations, null, null);
    }

    public QuestEligibilityService(
            IntegrationManager integrations,
            QuestPrerequisiteService prerequisites,
            CompletionHistoryCache completionHistory) {
        this.integrations = integrations;
        this.prerequisites = prerequisites;
        this.completionHistory = completionHistory;
    }

    public EligibilityResult evaluate(Player player, PlayerProfile profile, QuestDefinition quest) {
        if (!quest.enabled()) {
            return EligibilityResult.denied("Quest is disabled");
        }
        boolean bypass = player.hasPermission("plexonquests.bypass.eligibility");
        if (prerequisites != null && completionHistory != null && !bypass) {
            Set<String> required = prerequisites.prerequisites(quest.id());
            if (!required.isEmpty()) {
                if (!completionHistory.loaded(player.getUniqueId())) {
                    return EligibilityResult.denied("Quest completion history is still loading");
                }
                for (String prerequisite : required) {
                    if (!completionHistory.completed(player.getUniqueId(), prerequisite)) {
                        return EligibilityResult.denied("Complete prerequisite quest " + prerequisite);
                    }
                }
            }
        }
        if (!quest.eligibility().requiredPermission().isBlank()
                && !player.hasPermission(quest.eligibility().requiredPermission())
                && !bypass) {
            return EligibilityResult.denied("Missing permission " + quest.eligibility().requiredPermission());
        }
        for (String permission : quest.eligibility().blockedPermissions()) {
            if (player.hasPermission(permission) && !bypass) {
                return EligibilityResult.denied("Blocked by permission " + permission);
            }
        }
        if (!quest.eligibility().rankCategories().isEmpty()
                && !quest.eligibility().rankCategories().contains(profile.rankCategory())
                && !bypass) {
            return EligibilityResult.denied("Requires another rank category");
        }
        if (!quest.eligibility().worlds().isEmpty()
                && !quest.eligibility().worlds().contains(player.getWorld().getName())
                && !bypass) {
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
