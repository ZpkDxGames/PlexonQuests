package com.zpkdxgames.plexonquests.integration;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;

public final class EconomyBridge {
    public boolean available() {
        return provider() != null;
    }

    public double balance(Player player) {
        Provider provider = provider();
        if (provider == null) {
            return 0D;
        }
        try {
            return ((Number) provider.type().getMethod("getBalance", OfflinePlayer.class)
                    .invoke(provider.instance(), player)).doubleValue();
        } catch (ReflectiveOperationException exception) {
            return 0D;
        }
    }

    public Result withdraw(Player player, double amount) {
        return transact("withdrawPlayer", player, amount);
    }

    public Result deposit(Player player, double amount) {
        return transact("depositPlayer", player, amount);
    }

    private Result transact(String methodName, Player player, double amount) {
        Provider provider = provider();
        if (provider == null) {
            return new Result(false, 0D, "Vault economy provider is unavailable");
        }
        try {
            Object response = provider.type().getMethod(methodName, OfflinePlayer.class, double.class)
                    .invoke(provider.instance(), player, amount);
            Method success = response.getClass().getMethod("transactionSuccess");
            Method error = response.getClass().getMethod("errorMessage");
            Method transacted = response.getClass().getMethod("amount");
            return new Result(
                    Boolean.TRUE.equals(success.invoke(response)),
                    ((Number) transacted.invoke(response)).doubleValue(),
                    String.valueOf(error.invoke(response)));
        } catch (ReflectiveOperationException exception) {
            return new Result(false, 0D, exception.getClass().getSimpleName());
        }
    }

    private Provider provider() {
        if (!Bukkit.getPluginManager().isPluginEnabled("Vault")) {
            return null;
        }
        try {
            Class<?> economy = Class.forName("net.milkbowl.vault.economy.Economy");
            @SuppressWarnings({"rawtypes", "unchecked"})
            RegisteredServiceProvider<?> registration = Bukkit.getServicesManager().getRegistration((Class) economy);
            if (registration == null) {
                return null;
            }
            return new Provider(economy, registration.getProvider());
        } catch (ClassNotFoundException | LinkageError exception) {
            return null;
        }
    }

    public record Result(boolean success, double amount, String detail) {}

    private record Provider(Class<?> type, Object instance) {}
}

