package com.zpkdxgames.plexonquests.command;

import com.zpkdxgames.plexonquests.integration.core.CoreOriginMigrator;
import com.zpkdxgames.plexonquests.integration.core.CoreRuntimeCoordinator;
import com.zpkdxgames.plexonquests.presentation.TextService;
import java.util.List;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Adds Core 2 runtime diagnostics without changing the established quest command implementation. */
public final class RuntimeAwareQuestCommand implements CommandExecutor, TabCompleter {
    private final QuestCommand delegate;
    private final CoreRuntimeCoordinator runtime;
    private final CoreOriginMigrator migrator;
    private final TextService text;

    public RuntimeAwareQuestCommand(
            QuestCommand delegate,
            CoreRuntimeCoordinator runtime,
            CoreOriginMigrator migrator,
            TextService text) {
        this.delegate = delegate;
        this.runtime = runtime;
        this.migrator = migrator;
        this.text = text;
    }

    @Override
    public boolean onCommand(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String label,
            @NotNull String[] args) {
        boolean handled = delegate.onCommand(sender, command, label, args);
        if (args.length > 0
                && args[0].equalsIgnoreCase("diagnostics")
                && sender.hasPermission("plexonquests.admin.diagnostics")) {
            appendRuntimeDiagnostics(sender);
        }
        return handled;
    }

    private void appendRuntimeDiagnostics(CommandSender sender) {
        if (runtime == null) {
            sender.sendMessage(text.parse("<gray>Runtime mode <white>STANDALONE"));
            return;
        }

        CoreRuntimeCoordinator.Diagnostics state = runtime.diagnostics();
        List<String> lines = List.of(
                "<gray>Runtime mode <white>" + state.mode(),
                "<gray>Origin provider <white>" + state.originProvider(),
                "<gray>Runtime epoch <white>" + state.epoch(),
                "<gray>Core routed materials <white>" + state.subscribedMaterials(),
                "<gray>Core origin requested <white>" + state.originRequested(),
                "<gray>Core events received/consumed <white>" + state.eventsReceived()
                        + "<dark_gray>/</dark_gray><white>" + state.eventsConsumed(),
                "<gray>Runtime fallbacks/callback failures <white>" + state.fallbackCount()
                        + "<dark_gray>/</dark_gray><white>" + state.callbackFailures(),
                "<gray>Local block MONITOR gate <white>ACTIVE <dark_gray>(final cancellation)",
                "<gray>Local provenance listener <white>" + (state.coreOriginAuthoritative() ? "INACTIVE" : "ACTIVE"),
                "<gray>Shadow comparisons/mismatches <white>" + state.shadowComparisons()
                        + "<dark_gray>/</dark_gray><white>" + state.shadowMismatches());
        lines.forEach(line -> sender.sendMessage(text.parse(line)));

        if (migrator != null) {
            CoreOriginMigrator.Diagnostics migration = migrator.diagnostics();
            List<String> migrationLines = List.of(
                    "<gray>Origin import tracked/pending <white>" + migration.trackedChunks()
                            + "<dark_gray>/</dark_gray><white>" + migration.pending(),
                    "<gray>Origin marker checks/imports <white>" + migration.markerChecks()
                            + "<dark_gray>/</dark_gray><white>" + migration.importsStarted(),
                    "<gray>Origin imports completed/failed <white>" + migration.completed()
                            + "<dark_gray>/</dark_gray><white>" + migration.failures(),
                    "<gray>Origin unknown chunks <white>" + migration.unknownChunks());
            migrationLines.forEach(line -> sender.sendMessage(text.parse(line)));
        }
    }

    @Override
    public @Nullable List<String> onTabComplete(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String alias,
            @NotNull String[] args) {
        return delegate.onTabComplete(sender, command, alias, args);
    }
}
