package com.zpkdxgames.plexonquests.service;

import com.zpkdxgames.plexonquests.rotation.RerollService;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class PlayerLifecycleListener implements Listener {
    private final ProfileService profiles;
    private final ProgressService progress;
    private final RerollService rerolls;

    public PlayerLifecycleListener(ProfileService profiles, ProgressService progress, RerollService rerolls) {
        this.profiles = profiles;
        this.progress = progress;
        this.rerolls = rerolls;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        profiles.load(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        rerolls.cancel(event.getPlayer());
        profiles.unload(event.getPlayer());
        progress.removeIndex(event.getPlayer().getUniqueId());
    }
}
