package com.zpkdxgames.plexonquests.service;

import com.zpkdxgames.plexonquests.config.PluginSettings;
import com.zpkdxgames.plexonquests.quest.QuestScope;
import org.bukkit.entity.Player;

public final class SlotResolver {
    public int resolve(Player player, QuestScope scope, String rankCategory, PluginSettings settings) {
        int configuredBase = switch (scope) {
            case DAILY -> settings.assignments().baseDailySlots();
            case WEEKLY -> settings.assignments().baseWeeklySlots();
            case MILESTONE, MANUAL -> settings.assignments().maximumActiveManual();
        };
        return resolve(player, scope, rankCategory, settings, configuredBase);
    }

    public int resolve(
            Player player,
            QuestScope scope,
            String rankCategory,
            PluginSettings settings,
            int baseAssignments) {
        int base = Math.max(0, baseAssignments);
        int maximum = switch (scope) {
            case DAILY -> settings.assignments().maximumDailySlots();
            case WEEKLY -> settings.assignments().maximumWeeklySlots();
            case MILESTONE, MANUAL -> settings.assignments().maximumActiveManual();
        };
        if (player.hasPermission("plexonquests.bypass.slot-limit")) {
            return maximum;
        }
        int category = settings.rankProgression().enabled()
                ? settings.rankProgression().categories().getOrDefault(rankCategory, 0)
                : 0;
        int resolved = base + category * settings.rankProgression().bonusPerCategory();
        if (settings.rankProgression().fallbackPermissions()) {
            int permissionBound = Math.min(maximum, settings.security().maximumNumberedPermission());
            String prefix = scope == QuestScope.WEEKLY
                    ? "plexonquests.slots.weekly."
                    : "plexonquests.slots.daily.";
            for (int number = permissionBound; number >= 0; number--) {
                if (player.hasPermission(prefix + number)) {
                    resolved = Math.max(resolved, number);
                    break;
                }
            }
        }
        return Math.max(0, Math.min(maximum, resolved));
    }
}
