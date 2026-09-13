# PlexonQuests 4.2.2

PlexonQuests 4.2.2 is a focused Quest Board navigation reliability patch.

## Fixed

- Fixed the **Back** button on the direct single-current-quest details screen reopening the same details screen instead of navigating away.
- Direct Current Quest details now return to **Quest Board Home** when Back is pressed.
- Preserved exact Back behavior for legacy multi-active quest lists, including the originating page and scope.
- Added regression coverage for both navigation paths.

## Compatibility

Java 25, Paper `26.2.build.121-stable`, PlexonCore `2.0.4`, and existing PlexonQuests 4.2 data remain unchanged.

No quest progression, persistence, rewards, permissions, integrations, or performance behavior changed.

GitHub CI/release verification is source/distribution evidence. Live PlexonCraft runtime certification is not claimed unless separately executed.

Rollback: `v4.2.1` at `34b5472befc4743ad18114912002d0c82446735e`.
