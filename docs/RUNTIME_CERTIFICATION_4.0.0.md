# PlexonQuests 4.0.0 PlexonCraft Runtime Certification

This checklist is the **post-release deployment/soak matrix** for stable `v4.0.0`. GitHub source/release closure does not require live PlexonCraft access; release provenance may therefore record `runtime_certification=NOT_EXECUTED`.

Use this matrix before or during production cutover when live operational certification is required. Test the exact published stable JAR; do not certify a local rebuild and deploy different bytes.

## Stable artifact identity

Record before testing:

- stable tag and exact source SHA;
- JAR filename and byte size;
- SHA-256 from `SHA256SUMS.txt`;
- Paper build and Java version;
- PlexonCore version;
- versions of enabled optional integrations.

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

- Journal Home;
- Active Quests;
- Eligible Quests;
- Completed / History;
- Tracked Quest;
- Quest Details;
- Help;
- concise quest cards and details;
- prerequisite display;
- reward preview;
- terminal/completed states;
- final shared control geometry: Back/Home 45, Previous 48, page/context 49, Next 50, primary action 53;
- rapid-click debounce and in-place Track/Untrack refresh.

## State and prerequisite gate

- locked definitions remain inaccessible until prerequisites complete;
- eligible definitions enter only their authoritative assignment mechanism;
- active/tracked/completable/completed/cooldown/expired presentation matches authoritative state;
- disabled definitions do not activate;
- invalid/missing/cyclic prerequisite edits are rejected with diagnostics;
- reconnect/restart does not briefly auto-assign a prerequisite-locked quest before history is ready.

## Objective gate

Exercise every supported objective type present in the deployed catalog/engine, including mixed-objective quests and progress bounds. Confirm:

- no duplicate contribution credit;
- final-cancelled events do not credit;
- indexed interest limits work to relevant players/objectives;
- no global quest scan is introduced by the journal;
- player-placed blocks do not satisfy natural-only objectives;
- unknown origin fails closed where required;
- compatible Plexon integration events do not double-credit.

## Reward and transaction gate

- successful claim;
- double-click rejection;
- duplicate-event/reconnect rejection;
- full inventory policy;
- exact custom ItemStack/PDC/components preservation;
- Vault/Theosis economy rewards where configured;
- XP/item/command/permission and supported first-party reward types;
- reward preflight failure;
- reversible-delivery exception;
- irreversible command/key boundary behavior;
- restart/retry idempotency;
- uncertain-claim diagnostic/recovery behavior.

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
- targeted reset/force-complete/cancel confirmation;
- another administrator cannot consume another actor's confirmation;
- different target/action cannot consume it;
- expired/used confirmation cannot execute;
- target state is revalidated immediately before mutation;
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

With PlexonCore 2.0.4, verify `/plexon modules`, `/plexon diagnostics`, `/quests diagnostics`, shared acquisition mode, shared origin provider, fallback counters and no duplicate local+Core objective authority.

## Persistence / reload gate

- restart reconstruction;
- shutdown flush;
- SQLite WAL/checkpoint health;
- bounded writer queue health;
- no synchronous DB query in objective, journal-open or PlaceholderAPI hot paths;
- corrupt candidate configuration does not partially activate;
- malformed player state is diagnosed rather than silently wiped.

## Performance gate

Capture comparable Spark evidence under the same player/tool/objective workload:

- TPS;
- MSPT median/p95/p99 where available;
- objective-event handlers;
- SQLite writer activity;
- scheduled task count;
- allocation/hot-path regressions.

Investigate any meaningful unexplained MSPT regression before wider production rollout.

## Soak gate

Run the exact stable JAR for at least 30 continuous minutes with representative quest progress, journal use, reward claims, rotations/integrations, reconnects and at least one restart/reload sequence.

Target result:

- no HIGH/CRITICAL defect;
- no duplicate rewards/progress;
- no data loss;
- no unbounded queue/task growth;
- no repeating console error;
- no material performance regression.

## Operational decision

A failed live gate blocks or rolls back the **deployment**, not the already verified GitHub source/release lineage. Preserve the published stable artifact and open a new remediation version if a real runtime defect is proven.
