from pathlib import Path
import hashlib


def read(path: str) -> str:
    return Path(path).read_text(encoding="utf-8")


def write(path: str, value: str) -> None:
    target = Path(path)
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(value.rstrip() + "\n", encoding="utf-8")


# ----- menu layout 4 + safe migration -----
menus_path = Path("src/main/resources/menus.yml")
legacy_menu_sha256 = hashlib.sha256(menus_path.read_bytes()).hexdigest()

cm_path = Path("src/main/java/com/zpkdxgames/plexonquests/config/ConfigManager.java")
cm = cm_path.read_text(encoding="utf-8")
if "CURRENT_MENU_LAYOUT = 3" not in cm:
    raise SystemExit("ConfigManager is not at menu layout 3")
cm = cm.replace(
    "import java.nio.file.StandardCopyOption;\n",
    "import java.nio.file.StandardCopyOption;\nimport java.security.MessageDigest;\nimport java.security.NoSuchAlgorithmException;\n",
    1,
)
cm = cm.replace("import java.util.HashMap;\n", "import java.util.HashMap;\nimport java.util.HexFormat;\n", 1)
cm = cm.replace(
    "    private static final int CURRENT_MENU_LAYOUT = 3;\n",
    f"    private static final int CURRENT_MENU_LAYOUT = 4;\n"
    f"    private static final String BUNDLED_MENU_LAYOUT_3_SHA256 = \"{legacy_menu_sha256}\";\n",
    1,
)
start = cm.index("    private void migrateMenus() throws IOException {")
end = cm.index("    private YamlConfiguration loadYaml(", start)
migrate = r'''    private void migrateMenus() throws IOException {
        Path menusPath = dataDirectory.resolve("menus.yml");
        String existing = Files.readString(menusPath, StandardCharsets.UTF_8);
        YamlConfiguration menus = new YamlConfiguration();
        try {
            menus.loadFromString(existing);
        } catch (InvalidConfigurationException exception) {
            return;
        }
        int layout = menus.getInt("layout-version", 1);
        if (layout >= CURRENT_MENU_LAYOUT) {
            return;
        }

        Path backup = dataDirectory.resolve("backups")
                .resolve("menus-v" + layout + "-" + Instant.now().toEpochMilli() + ".yml");
        Files.copy(menusPath, backup, StandardCopyOption.COPY_ATTRIBUTES);

        boolean exactBundledV3 = layout == 3 && BUNDLED_MENU_LAYOUT_3_SHA256.equals(sha256(existing));
        if (!exactBundledV3) {
            String migrated = existing.replaceFirst(
                    "(?m)^layout-version\\s*:\\s*[0-9]+\\s*$",
                    "layout-version: " + CURRENT_MENU_LAYOUT);
            if (migrated.equals(existing)) {
                plugin.getLogger().warning("Could not safely advance customized menus.yml layout marker; previous file saved as "
                        + backup.getFileName());
                return;
            }
            AtomicFiles.writeUtf8(menusPath, migrated);
            plugin.getLogger().info("Preserved customized menus.yml while advancing its layout marker to "
                    + CURRENT_MENU_LAYOUT + "; previous file saved as " + backup.getFileName());
            return;
        }

        try (InputStream bundled = plugin.getResource("menus.yml")) {
            if (bundled == null) {
                throw new IOException("Bundled menus.yml is missing");
            }
            AtomicFiles.writeUtf8(menusPath, new String(bundled.readAllBytes(), StandardCharsets.UTF_8));
        }
        plugin.getLogger().info("Upgraded bundled-default menus.yml to layout version " + CURRENT_MENU_LAYOUT
                + "; previous layout saved as " + backup.getFileName());
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

'''
cm_path.write_text(cm[:start] + migrate + cm[end:], encoding="utf-8")

menus = menus_path.read_text(encoding="utf-8")
if "layout-version: 3" not in menus:
    raise SystemExit("bundled menus.yml is not layout 3")
menus_path.write_text(menus.replace("layout-version: 3", "layout-version: 4", 1), encoding="utf-8")

# ----- configured player-journal filler -----
journal_path = Path("src/main/java/com/zpkdxgames/plexonquests/gui/Phase2JournalService.java")
journal = journal_path.read_text(encoding="utf-8")
old_filler = "ItemStack filler = items.create(Material.BLACK_STAINED_GLASS_PANE, Component.empty(), List.of(), false);"
if journal.count(old_filler) != 2:
    raise SystemExit(f"expected two hardcoded filler sites, found {journal.count(old_filler)}")
journal = journal.replace(old_filler, "ItemStack filler = filler();")
anchor = "    private void item(Holder holder, int slot, Material material, String name, List<String> lore, boolean glow, Action action) {"
helper = '''    private ItemStack filler() {
        String configured = configs.snapshot().menus().string(
                "common.filler.material", Material.BLACK_STAINED_GLASS_PANE.name());
        Material material;
        try {
            material = Material.valueOf(configured.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            material = Material.BLACK_STAINED_GLASS_PANE;
        }
        return items.create(material, Component.empty(), List.of(), false);
    }

'''
if anchor not in journal:
    raise SystemExit("journal item helper anchor missing")
journal_path.write_text(journal.replace(anchor, helper + anchor, 1), encoding="utf-8")

# ----- exact rollback metadata -----
for workflow in (".github/workflows/build.yml", ".github/workflows/release.yml"):
    value = read(workflow)
    value = value.replace("ROLLBACK_TAG: 'v4.0.0'", "ROLLBACK_TAG: 'v4.1.0'")
    value = value.replace(
        "ROLLBACK_SHA: '57a235460e5e936ca931ee8d0e96b0a424f134f8'",
        "ROLLBACK_SHA: '3b0082bc181b3765e7cdb2bf4e9ce6c72206b896'",
    )
    write(workflow, value)

# ----- release notes and product docs -----
write(
    ".release/RELEASE_NOTES_4.2.0.md",
    '''# PlexonQuests 4.2.0

PlexonQuests 4.2.0 changes normal quest participation from automatic assignment to an explicit join-based Quest Board while preserving the 4.1 persistence, reward, objective-index, provenance and transaction architecture.

## Player experience

- `/quests` now centers Current, Available, Completed/History, Skills and Help.
- Players explicitly choose a Daily, Weekly or eligible Milestone quest.
- One normal active quest is allowed by default; completed-but-unclaimed quests do not occupy that runtime slot.
- Daily/Weekly slot settings remain period participation budgets.
- Abandon requires confirmation and consumes period budget by default.
- Existing 4.1 multi-active profiles remain non-destructive legacy overflow and cannot join another normal quest until within policy.
- The summary uses the player's actual head.
- PlexonSkills is optional and shown as read-only progression context when available; unavailable/loading skills never disable quests.
- Reroll remains a compatibility/deprecation route back to Available Quests.

## Runtime and safety

- Normal Daily/Weekly/Milestone quests are no longer auto-created by lifecycle maintenance.
- Catalog offers are side-effect free and deterministic per player/period/server seed while retaining weighted pools, mix constraints and recent-history exclusion.
- `QuestParticipationService` is the player join/abandon authority and rechecks active/period limits, duplicate history, eligibility and stale periods before insertion.
- Existing objective-interest indexing remains authoritative: no joined quest means no normal objective candidates; one joined quest means only that active quest's objectives are indexed.
- Persistence remains asynchronous; Bukkit/player mutations remain primary-thread authoritative.
- No per-player, per-quest or GUI redraw scheduler was added.
- Menu layout 4 honors configured filler and safely preserves customized menu files during migration.

## Compatibility

Java 25, Paper `26.2.build.121-stable`, PlexonCore `2.0.4`, existing SQLite assignment/history data and 4.1 frozen snapshots remain the release boundary. Public/admin assignment, claim, tracking and diagnostics remain available.

GitHub CI/release verification is source/distribution evidence. Live PlexonCraft/Spark runtime certification is not claimed unless separately executed.

Rollback: `v4.1.0` at `3b0082bc181b3765e7cdb2bf4e9ce6c72206b896`.
''',
)

write(
    "README.md",
    '''# PlexonQuests

[![Build](https://github.com/ZpkDxGames/PlexonQuests/actions/workflows/build.yml/badge.svg)](https://github.com/ZpkDxGames/PlexonQuests/actions/workflows/build.yml)

PlexonQuests **4.2.0** is a join-based quest board for Paper 26.2 / Java 25. It preserves the mature 4.1 objective, persistence, reward-transaction, PlexonCore provenance and integration architecture while changing normal participation to explicit player choice.

> PlexonQuests 4.2 no longer automatically starts normal quests. Players choose a quest from `/quests`, and only one normal quest runs at a time by default.

## Requirements

- Paper 26.2
- Java 25
- PlexonCore 2.0.4 recommended for shared Core Runtime acquisition/provenance
- Maven 3.9+ for source builds

SQLite JDBC and relocated FastUtil are bundled. PlaceholderAPI, Vault, LuckPerms and Plexon-family integrations including PlexonSkills are optional/runtime-detected.

## Quest Board

`/quests` exposes:

- **Current** — running quest, progress, objectives, rewards and guarded abandon/claim actions.
- **Available** — deterministic Daily, Weekly and eligible Milestone offers.
- **Completed / History** — asynchronous paginated persisted history.
- **Skills** — optional PlexonSkills level/progress context.
- **Help** — participation, budget and compatibility rules.

The summary uses the player's real Minecraft head. Skills are informational in 4.2 and are not silently introduced as quest eligibility requirements.

## Participation model

- Normal Daily/Weekly/Milestone quests are not auto-assigned.
- `participation.maximum-active-quests` defaults to `1`.
- Completing a quest frees the active runtime slot even before reward claim.
- Daily/Weekly slot settings remain the source of period participation budgets rather than simultaneous normal assignments.
- The same rotating quest cannot be joined twice in one period.
- Abandon is confirmation-gated and consumes period budget by default.
- Existing 4.1 multi-active assignments are preserved; overflow players finish those assignments before joining another normal quest.
- Manual/admin assignments and existing public assignment APIs remain supported separately from normal player participation.

## Runtime model

PlexonQuests continues to use shared interest-gated listeners, not one listener per player/quest.

`no joined quest -> no normal objective candidates`

`one joined quest -> only that active quest's normal objective candidates`

Completion, cancellation and expiration reindex the player profile and remove inactive objective interest. Travel/play-time sampling remains interest-gated. SQLite/catalog/history work stays asynchronous; Bukkit/inventory mutations stay on the primary thread. No repeating GUI redraw task is used.

## Configuration and migration

Config schema remains version 1 and adds `participation`, `catalog` and `skills-display`. `menus.yml` advances to layout 4 and the player journal uses `common.filler.material`. Exact bundled v3 defaults are backed up/replaced; customized older menu files are backed up and preserved rather than blindly overwritten.

See [4.1 -> 4.2 migration](docs/MIGRATION_4.1.0_TO_4.2.0.md), [configuration](docs/CONFIGURATION.md), [commands](docs/COMMANDS.md) and [player UX](docs/PLAYER_UX_4.2.md).

## Build/release verification

```bash
mvn -B -ntp clean verify
```

Canonical CI verifies ancestry, Java class major 69, exact Paper/PlexonCore boundaries, a non-empty all-green test suite, installable JAR structure, bundled SQLite, relocated FastUtil, provided-dependency non-shading, SHA-256, test summary, provenance and whitespace.

Stable publication accepts exact current `main`, rebuilds/tests it, publishes `PlexonQuests-4.2.0.jar` plus `SHA256SUMS.txt`, `TEST_SUMMARY.txt` and `PROVENANCE.txt`, then re-downloads/verifies public assets. This does not imply live PlexonCraft/Spark certification.

Rollback baseline: `v4.1.0` (`3b0082bc181b3765e7cdb2bf4e9ce6c72206b896`).

PlexonQuests is available under the [MIT License](LICENSE).
''',
)

write(
    "docs/COMMANDS.md",
    '''# Commands and permissions — PlexonQuests 4.2

`/quest`, `/pquests`, and `/plexonquests` are aliases of `/quests`.

## Player commands

| Command | Permission | Result |
| --- | --- | --- |
| `/quests` | `plexonquests.use` | Quest Board home |
| `/quests current` / `/quests active` | `plexonquests.use` | Current quest or legacy overflow list |
| `/quests available` | `plexonquests.use` | All current quest offers |
| `/quests daily` | `plexonquests.use` | Daily offers |
| `/quests weekly` | `plexonquests.use` | Weekly offers |
| `/quests milestones` | `plexonquests.use` | Eligible Milestone offers |
| `/quests completed` / `/quests history` | `plexonquests.history` | Paginated persisted history |
| `/quests skills` / `/quests stats` | `plexonquests.use` | Optional PlexonSkills context |
| `/quests help` | `plexonquests.use` | Join/limit/budget/abandon help |
| `/quests track <assignment|quest>` | `plexonquests.pin` | Compatibility tracking |
| `/quests untrack` | `plexonquests.pin` | Clear tracking |
| `/quests reroll` | `plexonquests.reroll` | Compatibility/deprecation redirect to Available Quests |

Joining/abandoning use guarded GUI actions backed by the same `QuestParticipationService`; the GUI is not an assignment authority.

## Administrative compatibility

The 4.1 administrative surface remains available: `admin`, `reload`, `validate`, `assign`, `cancel`, `progress`, `complete`, `reset`, `rotate`, `save`, `backup`, and `diagnostics`. Destructive targeted mutations keep their repeat-to-confirm state guard.

`plexonquests.bypass.active-limit` allows authorized flows to exceed the normal active cap. Existing eligibility/reroll-cost/slot-limit bypasses remain. Daily/Weekly numbered slot permissions now influence period participation budget through `SlotResolver`; they do not make multiple normal quests run simultaneously.
''',
)

config_doc = read("docs/CONFIGURATION.md")
config_header = '''# PlexonQuests 4.2 configuration changes

4.2 keeps `schema-version: 1`. Existing Daily/Weekly slot configuration now defines **period participation budget** instead of simultaneous normal assignments.

```yaml
participation:
  maximum-active-quests: 1
  allow-abandon: true
  abandon-consumes-period-budget: true
  manual-assignments-count-toward-limit: true
catalog:
  daily-offers: 7
  weekly-offers: 7
  refresh-on-period-change: true
skills-display:
  enabled: true
  provider: PLEXON_SKILLS
```

`assignments.base-*-slots`, `assignments.maximum-*-slots` and numbered slot permissions remain in use for participation budgets. `skills-display` is presentation-only: missing/incompatible PlexonSkills never disables PlexonQuests and no skill eligibility gate is added implicitly.

`menus.yml` uses `layout-version: 4` and `common.filler.material` for player-journal filler. Exact bundled v3 defaults are backed up/replaced; customized older menu files are backed up and preserved with only a safe layout-marker advance.

---

'''
if not config_doc.startswith("# PlexonQuests 4.2 configuration changes"):
    write("docs/CONFIGURATION.md", config_header + config_doc)

api_doc = read("docs/API.md")
api_header = '''# PlexonQuests 4.2 API compatibility note

4.2 changes normal **player participation UX**, not public API ownership. Existing public/admin manual assignment remains available and distinct from player Quest Board joins. Active assignment state remains the runtime/progress authority; an offer appearing in the catalog does not itself make that quest active.

PlexonSkills is consumed only through its optional public API/ServicesManager boundary and is not a mandatory eligibility dependency.

---

'''
if not api_doc.startswith("# PlexonQuests 4.2 API compatibility note"):
    write("docs/API.md", api_header + api_doc)

write(
    "docs/PLAYER_UX_4.2.md",
    '''# PlexonQuests 4.2 player UX

4.2 turns the normal journal into an explicit Quest Board. The player browses offers, inspects a quest and joins it. The default is one running normal quest so current work remains obvious and objective-interest indexing remains narrow.

## Home

Home shows the player's real Minecraft head and summary, Current Quest, Available Quests, Completed/History, Skills and Help. Legacy multi-active profiles receive an explicit overflow state instead of silent cancellation.

## Available

Daily/Weekly offers are deterministic for player/period/server seed and retain weighted pools, category/rarity constraints and recent-history exclusion. Milestones derive from registry/eligibility/prerequisite state. Join revalidates profile, online state, definition, period, eligibility, pool, active limit, duplicate history and participation budget before persistence.

## Current / abandon

A single active assignment opens to details. Completion/cancel/expire reindex the profile. Abandon requires explicit confirmation, loses progress and consumes rotating-period participation by default; the same quest cannot be rejoined in that period.

## Skills

PlexonSkills is optional. UI explicitly distinguishes unavailable, profile-loading and ready states. Skill context is informational and does not introduce a new eligibility rule.

## Completed/history

History stays asynchronous/paginated and final rendering validates the exact open holder to avoid stale menu updates.

## Compatibility

Tracking remains compatible. Reroll is retained as a deprecation/redirect path to Available Quests. Existing 4.1 assignments/frozen snapshots and administrator/manual assignment flows remain compatible.
''',
)

phase3 = read("docs/PHASE3_PLAYER_UX.md")
phase3_note = '''# 4.2 supersession note

The historical 4.0/4.1 Phase 3 journal below is retained as design context. PlexonQuests 4.2 supersedes its normal assignment/discovery flow with the explicit join-based Quest Board in [`PLAYER_UX_4.2.md`](PLAYER_UX_4.2.md), while retaining the mature persistence/transaction authorities.

---

'''
if not phase3.startswith("# 4.2 supersession note"):
    write("docs/PHASE3_PLAYER_UX.md", phase3_note + phase3)

performance = read("docs/PERFORMANCE.md")
perf_note = '''# PlexonQuests 4.2 participation scaling note

4.2 preserves shared interest-gated listeners and narrows normal per-player interest through explicit participation: no joined quest means no normal objective candidates; one joined quest means only that active quest's objectives are indexed. No per-player/per-quest listener or scheduler, synchronous gameplay DB read, or repeating GUI redraw was added. Live Spark measurements are separate deployment evidence and are not inferred from CI.

---

'''
if not performance.startswith("# PlexonQuests 4.2 participation scaling note"):
    write("docs/PERFORMANCE.md", perf_note + performance)

write(
    "docs/MIGRATION_4.1.0_TO_4.2.0.md",
    '''# Migrating PlexonQuests 4.1.0 -> 4.2.0

Back up `plugins/PlexonQuests/` before upgrade. Rollback baseline is `v4.1.0`, SHA `3b0082bc181b3765e7cdb2bf4e9ce6c72206b896`, JAR SHA-256 `acfcf34a829d5ff19ee1a4fa356129a121304c8b1d4473efb7471670683fa22f`.

## Behavior change

PlexonQuests 4.2 no longer automatically starts normal quests. Players choose a quest from `/quests`, and only one normal quest runs at a time by default.

Daily/Weekly slot settings remain as period participation budgets. Existing 4.1 active assignments are not cancelled. A player above the new limit enters non-destructive legacy overflow and cannot join another normal quest until within policy. Completed-but-unclaimed quests free the active runtime slot. Abandon consumes rotating-period participation by default and prevents same-period rejoin of that quest.

## Configuration

Schema stays version 1. New sections are `participation`, `catalog`, and `skills-display`. `menus.yml` moves from layout 3 to 4: exact bundled v3 defaults are backed up/replaced; customized old menus are backed up/preserved instead of blindly overwritten.

## PlexonSkills and rerolls

PlexonSkills remains optional; unavailable/loading provider state affects only presentation and does not add implicit eligibility. Reroll configuration/API compatibility remains, but the normal player reroll command redirects to Available Quests.

## Upgrade verification

Verify startup/diagnostics, Current/Available/Completed/Skills routes, one successful join plus rejected second join, progress only for joined objectives, completion/claim or guarded abandon cleanup, reconnect persistence/period usage, and legacy overflow if present. Live Spark/runtime certification is deployment evidence and is not implied by GitHub CI.
''',
)

changelog = read("CHANGELOG.md")
if "## [4.2.0]" not in changelog:
    entry = '''## [4.2.0] - 2026-09-12

- Replaced automatic normal Daily/Weekly/Milestone starts with an explicit join-based Quest Board.
- Added deterministic offer catalogs preserving weighted pools, mix constraints and recent-history exclusion.
- Added default one-active-normal-quest policy, period-budget enforcement, duplicate protection, guarded abandon and non-destructive legacy overflow.
- Reused assignment persistence to reconstruct period participation without a new schema/table.
- Preserved shared objective-interest gates: no joined quest has no normal objective candidates; joined players index only active quest objectives.
- Redesigned `/quests` around Current, Available, Completed/History, Skills and Help with a real player head and stale-safe async rendering.
- Added optional PlexonSkills context with unavailable/loading/ready states and no implicit skill eligibility.
- Retained reroll/tracking/admin/API compatibility while removing reroll clutter from normal UX.
- Advanced menu layout to 4, honored configured filler material, and preserved customized menu files on migration.
- Updated rollback metadata to `v4.1.0` / `3b0082bc181b3765e7cdb2bf4e9ce6c72206b896`.

'''
    changelog = changelog.replace("## [Unreleased]\n\n", "## [Unreleased]\n\n" + entry, 1)
    write("CHANGELOG.md", changelog)

# ----- behavioral regression tests -----
write(
    "src/test/java/com/zpkdxgames/plexonquests/service/QuestParticipationModelTest.java",
    '''package com.zpkdxgames.plexonquests.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zpkdxgames.plexonquests.TestFixtures;
import com.zpkdxgames.plexonquests.quest.CompletionMode;
import com.zpkdxgames.plexonquests.quest.QuestAssignment;
import com.zpkdxgames.plexonquests.quest.QuestScope;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class QuestParticipationModelTest {
    @Test
    void completedQuestFreesRuntimeSlotButStillConsumesPeriodBudget() {
        UUID playerId = UUID.randomUUID();
        QuestAssignment assignment = QuestAssignment.create(playerId,
                TestFixtures.quest("daily-one", CompletionMode.ALL, 1L), "daily", "daily:test",
                Instant.now(), Instant.now().plusSeconds(3600));
        PlayerProfile profile = profile(playerId, List.of(assignment));
        assertEquals(1, profile.activeAssignments().size());
        assertEquals(1, profile.periodParticipationCount(QuestScope.DAILY, "daily:test"));
        assertTrue(assignment.forceComplete(Instant.now()));
        assertTrue(profile.activeAssignments().isEmpty());
        assertEquals(1, profile.periodParticipationCount(QuestScope.DAILY, "daily:test"));
    }

    @Test
    void cancelledQuestRemainsPartOfPeriodParticipationHistory() {
        UUID playerId = UUID.randomUUID();
        QuestAssignment assignment = QuestAssignment.create(playerId,
                TestFixtures.quest("daily-two", CompletionMode.ALL, 5L), "daily", "daily:test",
                Instant.now(), Instant.now().plusSeconds(3600));
        PlayerProfile profile = profile(playerId, List.of(assignment));
        assertTrue(assignment.cancel());
        assertTrue(profile.activeAssignments().isEmpty());
        assertEquals(1, profile.periodParticipationCount(QuestScope.DAILY, "daily:test"));
    }

    @Test
    void legacyMultipleActiveAssignmentsArePreservedByProfileModel() {
        UUID playerId = UUID.randomUUID();
        QuestAssignment first = QuestAssignment.create(playerId,
                TestFixtures.quest("legacy-one", CompletionMode.ALL, 5L), "daily", "daily:test",
                Instant.now(), Instant.now().plusSeconds(3600));
        QuestAssignment second = QuestAssignment.create(playerId,
                TestFixtures.quest("legacy-two", CompletionMode.ALL, 5L), "daily", "daily:test",
                Instant.now(), Instant.now().plusSeconds(3600));
        PlayerProfile profile = profile(playerId, List.of(first, second));
        assertEquals(2, profile.activeAssignments().size());
        assertEquals(2, profile.periodParticipationCount(QuestScope.DAILY, "daily:test"));
    }

    private static PlayerProfile profile(UUID playerId, List<QuestAssignment> assignments) {
        return new PlayerProfile(playerId, "Player", FeedbackPreferences.defaults(), null, 0L, assignments);
    }
}
''',
)

write(
    "src/test/java/com/zpkdxgames/plexonquests/integration/skills/PlayerSkillsSnapshotTest.java",
    '''package com.zpkdxgames.plexonquests.integration.skills;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

class PlayerSkillsSnapshotTest {
    @Test
    void unavailableProviderIsSafeAndEmpty() {
        PlayerSkillsSnapshot snapshot = PlayerSkillsSnapshot.unavailable();
        assertFalse(snapshot.available());
        assertFalse(snapshot.ready());
        assertTrue(snapshot.skills().isEmpty());
    }

    @Test
    void presentButNotReadyProviderUsesLoadingState() {
        PlayerSkillsSnapshot snapshot = PlayerSkillsSnapshot.loading();
        assertTrue(snapshot.available());
        assertFalse(snapshot.ready());
        assertEquals(0, snapshot.totalLevel());
    }

    @Test
    void readySnapshotFindsSkillCaseInsensitively() {
        SkillSnapshot mining = new SkillSnapshot("MINING", "Mining", Material.IRON_PICKAXE,
                12, 345L, 500L, 0.69D);
        PlayerSkillsSnapshot snapshot = new PlayerSkillsSnapshot(true, true, 12, List.of(mining));
        assertTrue(snapshot.ready());
        assertEquals(12, snapshot.skill("mining").orElseThrow().level());
        assertTrue(snapshot.skill(null).isEmpty());
    }
}
''',
)

ux_path = Path("src/test/java/com/zpkdxgames/plexonquests/gui/player/Phase3JournalUxArchitectureTest.java")
ux = ux_path.read_text(encoding="utf-8")
if "menuLayoutFourUsesConfiguredFiller" not in ux:
    addition = '''
    @Test
    void menuLayoutFourUsesConfiguredFiller() throws IOException {
        String journal = Files.readString(JOURNAL);
        String configManager = Files.readString(Path.of(
                "src/main/java/com/zpkdxgames/plexonquests/config/ConfigManager.java"));
        String menus = Files.readString(Path.of("src/main/resources/menus.yml"));
        assertTrue(configManager.contains("CURRENT_MENU_LAYOUT = 4"));
        assertTrue(configManager.contains("BUNDLED_MENU_LAYOUT_3_SHA256"));
        assertTrue(journal.contains("menus().string(\"common.filler.material\""));
        assertTrue(menus.contains("layout-version: 4"));
    }
'''
    ux_path.write_text(ux.rsplit("}", 1)[0] + addition + "}\n", encoding="utf-8")

# Remove all temporary finalization machinery from the candidate.
Path(".github/workflows/agent-finalize-4.2.yml").unlink(missing_ok=True)
Path(".github/scripts/finalize_4_2.py").unlink(missing_ok=True)
