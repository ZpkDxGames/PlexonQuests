# PlexonQuests 4.0.0 PlexonCraft Runtime Certification

Stable `v4.0.0` is blocked until every required gate below passes on PlexonCraft with the exact frozen candidate JAR and zero HIGH/CRITICAL defects.

## Candidate identity

Record before testing:

- branch and exact commit SHA;
- GitHub prerelease tag;
- JAR filename and byte size;
- SHA-256 from `SHA256SUMS.txt`;
- Paper build;
- Java version;
- PlexonCore version;
- versions of every enabled optional integration.

Do not test an uncommitted/local rebuild and then promote a different artifact.

## Migration gate

- start from a representative copy of 3.3.1 player data;
- preserve active quests and objective progress;
- preserve claimed/completed quest history;
- preserve reroll/cooldown/rotation state;
- preserve tracked/pinned assignment where applicable;
- preserve reward-completion identity;
- verify backup and 3.3.1 rollback procedure.

## Journal gate

Validate `/quests` plus:

- Overview;
- Active Quests;
- Available Quests;
- Categories;
- Tracked Quest;
- Completed / History;
- Statistics;
- Help;
- concise quest cards and details;
- prerequisite display;
- reward preview;
- terminal/completed states;
- stable Back/Close controls.

## State and prerequisite gate

- `LOCKED` when prerequisites are incomplete;
- `AVAILABLE` when eligible and unassigned;
- `ACTIVE` after authoritative assignment;
- `TRACKED` after right-click tracking;
- `COMPLETABLE` when all objective requirements are complete;
- `COMPLETED` for completed non-rotating progression;
- `COOLDOWN` for a completed rotating quest in its period;
- `EXPIRED` where applicable;
- `DISABLED` definitions do not activate;
- invalid/missing/cyclic prerequisite edits are rejected with diagnostics;
- reconnect/restart does not briefly auto-assign a prerequisite-locked quest before history is loaded.

## Objective gate

Exercise every supported objective type present in the candidate catalog/engine, including mixed-objective quests and progress bounds. Confirm:

- no duplicate contribution credit;
- final-cancelled events do not credit;
- indexed interest limits work to relevant players/objectives;
- no global quest scan is introduced by the journal;
- player placed blocks do not satisfy natural-only objectives;
- unknown origin fails closed where required;
- PlexonSpawners provenance, when tested with its compatible candidate API, does not double-credit or trust fake/NPC/custom entities incorrectly.

## Reward and transaction gate

- successful claim;
- double-click rejection;
- duplicate-event/reconnect rejection;
- full inventory policy;
- exact custom ItemStack/PDC/components preservation;
- Vault/Theosis economy rewards;
- XP/item/command/permission and supported first-party reward types;
- reward preflight failure;
- reversible delivery exception;
- irreversible boundary behavior;
- restart/retry idempotency;
- uncertain claim diagnostic/recovery behavior.

## Rotation/repeat gate

For daily/weekly behavior:

- deterministic reset period;
- timezone configuration;
- restart over reset boundary;
- completion near reset boundary;
- repeated reload;
- no task per player/quest;
- no duplicate assignment after reset/reconnect.

## Admin gate

- inspect/diagnostics;
- targeted reset stages confirmation;
- force-complete stages confirmation;
- cancel stages confirmation;
- another administrator cannot consume the confirmation;
- different target/action cannot consume it;
- expired confirmation cannot execute;
- used confirmation cannot execute twice;
- target state is revalidated by the authoritative handler immediately before mutation;
- audit history records successful mutations;
- invalid reload retains known-good behavior.

## API / PlaceholderAPI gate

- existing `PlexonQuestsAPI` consumers still load;
- `PlexonQuestsJournalAPI` is registered;
- immutable journal views cannot mutate plugin state;
- `QuestTrackEvent` fires once per successful change;
- existing pinned placeholders remain compatible;
- tracked/count aliases resolve correctly;
- unloaded/offline player resolution stays neutral and non-blocking.

## Cross-plugin gate

Validate installed Plexon integrations against their actual public API contracts. Missing/incompatible optional providers must fail closed rather than disabling PlexonQuests or fabricating progress.

With PlexonCore 2.0.4, verify `/plexon modules`, `/plexon diagnostics`, `/quests diagnostics`, shared acquisition mode, shared origin provider, fallback counters, and no duplicate local+Core objective authority.

## Persistence / reload gate

- restart reconstruction;
- shutdown flush;
- SQLite WAL/checkpoint health;
- bounded writer queue health;
- no synchronous DB query in objective, GUI-open, or PlaceholderAPI hot paths;
- corrupt candidate configuration does not partially activate;
- malformed player state is diagnosed rather than silently wiped.

## Performance gate

Capture comparable Spark evidence before and after the candidate under the same player/tool/objective workload:

- TPS;
- MSPT median/p95/p99 where available;
- objective-event handlers;
- SQLite writer activity;
- scheduled task count;
- allocation/hot-path regressions.

Reject the candidate for a meaningful unexplained MSPT regression.

## Soak gate

Run the exact candidate for at least 30 continuous minutes with representative quest progress, GUI use, reward claims, rotations/integrations, reconnects, and at least one restart/reload sequence.

Required result:

- no HIGH defects;
- no CRITICAL defects;
- no duplicate rewards/progress;
- no data loss;
- no unbounded queue/task growth;
- no repeating console error;
- no material performance regression.

## Certification decision

Only after every applicable gate passes may the draft Phase 2 PR be considered for merge and `v4.0.0` stable promotion. Until then the repository state is:

`RC RELEASED / RUNTIME PENDING`
