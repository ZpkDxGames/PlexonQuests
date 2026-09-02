package com.zpkdxgames.plexonquests.integration;

import com.zpkdxgames.plexonquests.config.ConfigManager;
import com.zpkdxgames.plexonquests.presentation.TextService;
import com.zpkdxgames.plexonquests.quest.QuestAssignment;
import com.zpkdxgames.plexonquests.quest.QuestScope;
import com.zpkdxgames.plexonquests.rotation.PeriodKeyService;
import com.zpkdxgames.plexonquests.rotation.RerollService;
import com.zpkdxgames.plexonquests.service.PlayerProfile;
import com.zpkdxgames.plexonquests.service.ProfileService;
import com.zpkdxgames.plexonquests.service.SlotResolver;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class PlexonQuestsExpansion extends PlaceholderExpansion {
    private final ConfigManager configs;
    private final ProfileService profiles;
    private final IntegrationManager integrations;
    private final RerollService rerolls;
    private final TextService text;
    private final SlotResolver slots = new SlotResolver();
    private final String version;

    public PlexonQuestsExpansion(
            ConfigManager configs,
            ProfileService profiles,
            IntegrationManager integrations,
            RerollService rerolls,
            TextService text,
            String version) {
        this.configs = configs;
        this.profiles = profiles;
        this.integrations = integrations;
        this.rerolls = rerolls;
        this.text = text;
        this.version = version;
    }

    @Override public @NotNull String getIdentifier() { return "plexonquests"; }

    @Override public @NotNull String getAuthor() { return "Tonim (ZpkDxGames)"; }

    @Override public @NotNull String getVersion() { return version; }

    @Override public boolean persist() { return true; }

    @Override
    public @Nullable String onRequest(OfflinePlayer offline, @NotNull String parameters) {
        String neutral = configs.snapshot().messages().string("neutral-placeholder", "—");
        if (parameters.startsWith("integration_") && parameters.endsWith("_status")) {
            String id = parameters.substring("integration_".length(), parameters.length() - "_status".length());
            return integrations.state(id).status().name();
        }
        if (offline == null || !offline.isOnline() || !Bukkit.isPrimaryThread()) {
            return neutral;
        }
        Player player = offline.getPlayer();
        PlayerProfile profile = player == null ? null : profiles.profile(player).orElse(null);
        if (player == null || profile == null) {
            return neutral;
        }
        QuestAssignment pinned = profile.pinnedAssignment().flatMap(profile::assignment).orElse(null);
        return switch (parameters.toLowerCase(Locale.ROOT)) {
            case "active" -> Long.toString(profile.assignments().stream()
                    .filter(assignment -> assignment.state() == com.zpkdxgames.plexonquests.quest.AssignmentState.ACTIVE)
                    .count());
            case "completed_unclaimed" -> Long.toString(profile.claimableCount());
            case "completed_total" -> Long.toString(profile.completedTotal());
            case "daily_time_left" -> timeLeft(QuestScope.DAILY);
            case "weekly_time_left" -> timeLeft(QuestScope.WEEKLY);
            case "daily_rerolls" -> Integer.toString(rerolls.freeRemaining(player, QuestScope.DAILY));
            case "pinned_name" -> pinned == null
                    ? neutral
                    : text.plain(text.parse(pinned.definition().display().name()));
            case "pinned_progress" -> pinned == null
                    ? neutral
                    : text.formatNumber(pinned.displayProgress().current()) + "/"
                            + text.formatNumber(pinned.displayProgress().required());
            case "pinned_percentage" -> pinned == null
                    ? neutral
                    : Integer.toString((int) Math.floor(pinned.percentage()));
            case "pinned_time_left" -> pinned == null
                    ? neutral
                    : pinned.expiresAt().map(expiry -> text.formatDuration(Duration.between(Instant.now(), expiry)))
                            .orElse(neutral);
            case "slot_limit" -> Integer.toString(slots.resolve(
                    player, QuestScope.DAILY, profile.rankCategory(), configs.snapshot().settings()));
            default -> neutral;
        };
    }

    private String timeLeft(QuestScope scope) {
        Instant end = new PeriodKeyService(configs.snapshot().settings().rotation())
                .period(scope, Instant.now()).endsAt();
        return text.formatDuration(Duration.between(Instant.now(), end));
    }
}
