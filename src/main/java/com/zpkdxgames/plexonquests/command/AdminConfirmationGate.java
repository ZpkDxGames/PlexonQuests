package com.zpkdxgames.plexonquests.command;

import java.time.Duration;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;
import org.bukkit.command.CommandSender;

/**
 * One-shot confirmation gate for destructive admin commands.
 * The second identical command from the same actor within the TTL is allowed through to the
 * authoritative command handler, which performs the final stale-state check immediately before mutation.
 */
public final class AdminConfirmationGate {
    private final long ttlNanos;
    private final LongSupplier nanoTime;
    private final Map<String, Pending> pending = new ConcurrentHashMap<>();

    public AdminConfirmationGate(Duration ttl) {
        this(ttl, System::nanoTime);
    }

    AdminConfirmationGate(Duration ttl, LongSupplier nanoTime) {
        this.ttlNanos = Objects.requireNonNull(ttl, "ttl").toNanos();
        this.nanoTime = Objects.requireNonNull(nanoTime, "nanoTime");
    }

    public Decision check(CommandSender sender, String[] args) {
        return check(actorKey(sender), args);
    }

    Decision check(String actor, String[] args) {
        long now = nanoTime.getAsLong();
        String normalized = normalize(args);
        Pending current = pending.get(actor);
        if (current != null && current.expiresAtNanos() >= now && current.normalizedCommand().equals(normalized)) {
            pending.remove(actor, current);
            return Decision.CONFIRMED;
        }
        pending.put(actor, new Pending(normalized, now + ttlNanos));
        prune(now);
        return Decision.STAGED;
    }

    public void clear(CommandSender sender) {
        pending.remove(actorKey(sender));
    }

    private void prune(long now) {
        if (pending.size() > 256) {
            pending.entrySet().removeIf(entry -> entry.getValue().expiresAtNanos() < now);
        }
    }

    private static String actorKey(CommandSender sender) {
        return sender.getClass().getName() + ':' + sender.getName().toLowerCase(Locale.ROOT);
    }

    private static String normalize(String[] args) {
        return Arrays.stream(args)
                .map(value -> value.toLowerCase(Locale.ROOT))
                .reduce((left, right) -> left + '\u0000' + right)
                .orElse("");
    }

    public enum Decision { STAGED, CONFIRMED }

    private record Pending(String normalizedCommand, long expiresAtNanos) {}
}
