package com.zpkdxgames.plexonquests.integration.skills;

import com.zpkdxgames.plexonquests.integration.IntegrationManager;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

/** Cached reflection bridge against PlexonSkills public API 2.0. */
public final class PlexonSkillsBridge {
    private final JavaPlugin plugin;
    private final IntegrationManager integrations;
    private volatile Bindings bindings;
    private volatile boolean warned;

    public PlexonSkillsBridge(JavaPlugin plugin, IntegrationManager integrations) {
        this.plugin = plugin;
        this.integrations = integrations;
    }

    public PlayerSkillsSnapshot snapshot(UUID playerId) {
        if (!integrations.available("PLEXON_SKILLS")) return PlayerSkillsSnapshot.unavailable();
        try {
            Bindings active = bindings();
            if (active == null) return PlayerSkillsSnapshot.unavailable();
            Object api = active.api();
            boolean ready = (boolean) active.isProfileReady().invoke(api, playerId);
            if (!ready) return PlayerSkillsSnapshot.loading();
            int totalLevel = ((Number) active.getTotalLevel().invoke(api, playerId)).intValue();
            Object optional = active.profile().invoke(api, playerId);
            if (!(optional instanceof Optional<?> profileOptional) || profileOptional.isEmpty()) {
                return PlayerSkillsSnapshot.loading();
            }
            Object profileView = profileOptional.get();
            Object rawSkills = active.playerSkills().invoke(profileView);
            if (!(rawSkills instanceof List<?> skillValues)) {
                return new PlayerSkillsSnapshot(true, true, totalLevel, List.of());
            }
            List<SkillSnapshot> snapshots = new ArrayList<>(skillValues.size());
            for (Object progress : skillValues) {
                Object skill = active.progressSkill().invoke(progress);
                String id = String.valueOf(active.skillId().invoke(skill));
                String display = String.valueOf(active.skillDisplayName().invoke(skill));
                Object iconValue = active.skillIcon().invoke(skill);
                Material icon = iconValue instanceof Material material ? material : Material.EXPERIENCE_BOTTLE;
                int level = ((Number) active.progressLevel().invoke(progress)).intValue();
                long totalXp = ((Number) active.progressTotalXp().invoke(progress)).longValue();
                long nextXp = ((Number) active.progressXpForNextLevel().invoke(progress)).longValue();
                double percentage = ((Number) active.progressProgress().invoke(progress)).doubleValue();
                snapshots.add(new SkillSnapshot(id, display, icon, level, totalXp, nextXp, percentage));
            }
            warned = false;
            return new PlayerSkillsSnapshot(true, true, totalLevel, snapshots);
        } catch (ReflectiveOperationException | LinkageError exception) {
            bindings = null;
            if (!warned) {
                warned = true;
                plugin.getLogger().log(Level.WARNING, "PlexonSkills public API 2.0 became unavailable", exception);
            }
            return PlayerSkillsSnapshot.unavailable();
        }
    }

    private Bindings bindings() throws ReflectiveOperationException {
        Plugin provider = Bukkit.getPluginManager().getPlugin("PlexonSkills");
        if (provider == null || !provider.isEnabled()) return null;
        Bindings current = bindings;
        if (current != null && current.provider() == provider) return current;
        synchronized (this) {
            current = bindings;
            if (current != null && current.provider() == provider) return current;
            ClassLoader loader = provider.getClass().getClassLoader();
            Class<?> apiType = Class.forName("com.zpkdxgames.plexonskills.api.PlexonSkillsAPI", false, loader);
            @SuppressWarnings({"rawtypes", "unchecked"})
            RegisteredServiceProvider<?> registration = Bukkit.getServicesManager().getRegistration((Class) apiType);
            if (registration == null) return null;
            Object api = registration.getProvider();
            String apiVersion = String.valueOf(apiType.getMethod("apiVersion").invoke(api));
            if (!apiVersion.startsWith("2.")) return null;

            Class<?> playerView = Class.forName("com.zpkdxgames.plexonskills.api.PlayerSkillView", false, loader);
            Class<?> progressView = Class.forName("com.zpkdxgames.plexonskills.api.SkillProgressView", false, loader);
            Class<?> skillType = Class.forName("com.zpkdxgames.plexonskills.skill.SkillType", false, loader);
            current = new Bindings(
                    provider,
                    api,
                    apiType.getMethod("isProfileReady", UUID.class),
                    apiType.getMethod("getTotalLevel", UUID.class),
                    apiType.getMethod("profile", UUID.class),
                    playerView.getMethod("skills"),
                    progressView.getMethod("skill"),
                    progressView.getMethod("totalXp"),
                    progressView.getMethod("level"),
                    progressView.getMethod("xpForNextLevel"),
                    progressView.getMethod("progress"),
                    skillType.getMethod("id"),
                    skillType.getMethod("displayName"),
                    skillType.getMethod("icon"));
            bindings = current;
            return current;
        }
    }

    private record Bindings(
            Plugin provider,
            Object api,
            Method isProfileReady,
            Method getTotalLevel,
            Method profile,
            Method playerSkills,
            Method progressSkill,
            Method progressTotalXp,
            Method progressLevel,
            Method progressXpForNextLevel,
            Method progressProgress,
            Method skillId,
            Method skillDisplayName,
            Method skillIcon) {}
}
