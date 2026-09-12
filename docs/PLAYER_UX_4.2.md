# PlexonQuests 4.2 player UX

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
