# Migrating PlexonQuests 4.1.0 -> 4.2.0

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
