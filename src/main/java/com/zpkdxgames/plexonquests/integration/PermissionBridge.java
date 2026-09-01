package com.zpkdxgames.plexonquests.integration;

import java.lang.reflect.Method;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public final class PermissionBridge {
    private final Logger logger;

    public PermissionBridge(Logger logger) {
        this.logger = logger;
    }

    public boolean available() {
        return Bukkit.getPluginManager().isPluginEnabled("LuckPerms") && luckPerms() != null;
    }

    public Result grant(Player player, String permission, Duration duration) {
        Object api = luckPerms();
        if (api == null) {
            return new Result(false, "LuckPerms is unavailable");
        }
        try {
            Object userManager = api.getClass().getMethod("getUserManager").invoke(api);
            Object user = userManager.getClass().getMethod("getUser", UUID.class)
                    .invoke(userManager, player.getUniqueId());
            if (user == null) {
                return new Result(false, "LuckPerms user is not loaded");
            }
            Class<?> nodeType = Class.forName("net.luckperms.api.node.Node");
            Object builder = nodeType.getMethod("builder", String.class).invoke(null, permission);
            if (!duration.isZero() && !duration.isNegative()) {
                Method expiry = builder.getClass().getMethod("expiry", Duration.class);
                builder = expiry.invoke(builder, duration);
            }
            Object node = builder.getClass().getMethod("build").invoke(builder);
            Object data = user.getClass().getMethod("data").invoke(user);
            Class<?> nodeInterface = Class.forName("net.luckperms.api.node.Node");
            data.getClass().getMethod("add", nodeInterface).invoke(data, node);
            Class<?> userType = Class.forName("net.luckperms.api.model.user.User");
            Object saveResult = userManager.getClass().getMethod("saveUser", userType)
                    .invoke(userManager, user);
            if (saveResult instanceof CompletableFuture<?> future) {
                future.exceptionally(failure -> {
                    logger.log(Level.SEVERE, "LuckPerms failed to persist a PlexonQuests permission reward", failure);
                    return null;
                });
            }
            return new Result(true, "granted");
        } catch (ReflectiveOperationException | LinkageError exception) {
            return new Result(false, exception.getClass().getSimpleName() + ": " + exception.getMessage());
        }
    }

    private Object luckPerms() {
        try {
            Class<?> provider = Class.forName("net.luckperms.api.LuckPermsProvider");
            return provider.getMethod("get").invoke(null);
        } catch (ReflectiveOperationException | LinkageError exception) {
            return null;
        }
    }

    public record Result(boolean success, String detail) {}
}
