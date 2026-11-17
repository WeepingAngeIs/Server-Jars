# Independent config-audit undo acceptance

This advances the public-input gap recorded in the [acceptance index](UNYIELDING-ACCEPTANCE-INDEX.md), after the completed [destination checkpoint](UNYIELDING-DESTINATION-ACCEPTANCE-RESULTS.md), report commit `e81a6298f7ed33127b4788ff96d4dc9f2d43d539`. The initial candidate is the exact Unyielding-1.2.0.jar from [run 44036656345](https://github.com/WeepingAngeIs/Server-Jars/actions/runs/44036656345), artifact `14307985889`, SHA-256 `216de93ca87cc9e0a54f0f0991c675a1058ee95de3247c8efbebffc664f0c504`. Work is isolated on `codex/unyielding-config-audit-acceptance`.

## Existing contract and prior evidence

Five ConfigAuditPolicyTest cases cover pure redaction, display, allowlist/current-value comparison, retention and paging rules. Earlier ConfigEditor runtime evidence covers generic editor behavior, including direct handler calls with connected players, but does not establish the later audit/undo implementation through actual client requests and commands. Completed ground, reward, meeting, destination, vein and tutorial suites are preserved and are not repeated here.

The command is `/configaudit [page]` or `/configaudit undo`; there is no entry ID or undo token. Both command and implementation require operator level 2. Undo acts on the global latest entry, including another administrator's edit, rather than a per-actor history. A successful undo uses normal ConfigEditor handling and appends a reverse audit record. Repeating undo can therefore reverse that new record. Unsupported or no-op latest entries block undo; older entries are not searched.

Current-value matching is an exact optimistic comparison, not an epoch/ABA-history guarantee. Numeric allowlisting covers map icon scale, origin creative flight speed and seven existing teleport cooldowns. This fixture uses representative Double and Int settings rather than testing every allowlisted value.

## Bounded native scope

Two actual disposable clients use real ConfigEditor.Action packets and ordinary `/configaudit` commands. Test-only ordered server receipts and client responses establish production processing before assertions. Config settings, world/player state and actor roles are synthetic inputs in the GitHub-hosted world; no live configuration is imported.

Eight cases target empty-history refusal; valid numeric edits/undo/reverse entries; representative Int undo; nonoperator and revoked-operator refusal; stale expected values and a declared unmanaged config conflict; unsupported/no-op latest entries; global ordering across two administrators; and text minimization with unsupported string undo refusal. Native text minimization is checked in audit storage and display; authorized config editors may legitimately see the current setting.

Successful actions must record the actual actor, key and values, preserve prior audit entries, change only the expected config target and retain unrelated settings/player data. The target is checked in memory and in generated TOML files using a read-only parser. Whole-file byte equality is not required after valid saves, because normal config saving may rewrite formatting. Refused operations must preserve current config and audit state. Native inventory/physical coins, XP, progress/claims, spin balances, home/back data and an unrelated party provide preservation sentinels; this is not every external bank currency.

## Verification record

Verified 2026-11-17 UTC. Source `486ad52fef82fb840fadb22fd687dad399843e10`, [run 44043593748](https://github.com/WeepingAngeIs/Server-Jars/actions/runs/44043593748), job `133903776370`, passed **all eight native config-audit cases**, zero failures. Both real clients reached the required readiness state. Runtime assertions verified the actual assembled JAR path/hash, absence of Jail and no private/live data use.

[Artifact 14311265884](https://github.com/WeepingAngeIs/Server-Jars/actions/runs/44043593748/artifacts/14311265884) downloaded ZIP SHA-256 independently matched published `957b6104f785a34e5ad5dbae433c1ad513b350fbb3295f45bf2f5191a693b1bc`. The downloaded JAR independently matched the native runtime hash and was byte-for-byte identical to the prior meeting-verified candidate. No production source change, production build or unit-suite rerun occurred; the artifact contains zero new unit-result files. The identical candidate's 69-unit evidence remains scoped to run `44031631588`.

The passing cases establish:

1. An operator can view empty history; undo with no entry changes no config, audit or unrelated state.
2. A native Double edit persists its exact map-icon value and records the actual actor/key/before/after/time. Native undo restores it and appends a reverse record. Another undo reverses that new latest record, matching the documented global-history behavior.
3. A representative Int home cooldown edit and undo persist exact numeric values. Actual audit output displays the tested 65-second value as `1m 5s`; unrelated settings remain unchanged.
4. A nonoperator and a subsequently deoperated administrator cannot edit, open audit details or undo a fresh eligible latest value. Actual post-receipt messages/screens contain no newly disclosed audit heading or editor, and state remains unchanged. This is current native operator authorization, not an actual live rank-provider certification.
5. After another administrator changes a value, a stale expected-JSON edit refuses without an audit append. A separately declared unmanaged synthetic config change makes latest undo refuse; its current memory/disk value and prior audit history remain intact.
6. A latest unsupported Boolean edit blocks undo of an older eligible numeric edit. An accepted no-op numeric record likewise blocks undo; neither operation searches backward or changes the earlier value.
7. Two administrators edit different allowlisted keys. Undo acts on the global latest key, records the undoing actor and preserves the earlier independently edited key and all unrelated state.
8. A valid synthetic timezone String is stored in the generated config, while native audit storage and display use `[text or list omitted]` for its values. Unsupported String undo refuses and preserves the current setting.

Every accepted edit checks exactly one new entry, immutable prior history, actual network-caller identity and expected config changes. Refused/read-only actions compare full audit NBT, all exposed ConfigEditor values, parsed values from the three generated module TOML files and the declared native player/data sentinels. This is not a claim about every third-party config file or every external bank currency.

No defect was reproduced, so no production fix was made. Actual client packets and commands exercise the existing implementation; no undo ID, token, actor-ownership rule or new UI was added. No Jail runtime or separate Jail build/certificate prerequisite was invoked.

## Exact unchanged candidate

Version remains **1.2.0**:

- SHA-256: `216de93ca87cc9e0a54f0f0991c675a1058ee95de3247c8efbebffc664f0c504`
- Raw SHA-512: `349284d438561c34d7005899cec9a80025ccd6bd3589f9b51c8768303cb22e60275ebf4ba7cb31acd2023bbaf9dabc414c5313db4ed0ee6856c167c8ae5c3ef2`

All candidate bytes, including prior fixes, features, settings resources, artwork and models, are unchanged. The [machine-readable results](UNYIELDING-CONFIG-AUDIT-ACCEPTANCE-RESULTS.json) record the same exact source, hashes, cases and scope.

## Preservation and limits

Heavy builds/runtime work uses GitHub-hosted disposable clients/worlds and generated configuration files. No installed profile, live config, real world, private data, setting, hotbar, artwork or model file is modified or uploaded. Version remains 1.2.0; no installation or deployment occurs. Prior artifacts/reports and the separate earlier exact-pair Jail certificate/gates are preserved. Basic Mawile food behavior is not retested.

The audit uses ordinary world SavedData, while COMMON config files save separately. Audit verification reads its native SavedData NBT snapshot in the running world, not a reloaded on-disk audit file. Generated COMMON, Essentials and Ultimine TOML values are read from disk. This one-phase test establishes no server restart, crash/IO-failure rollback, atomic transaction or physical power-loss behavior. This is not an exhaustive settings, provider, paging/retention or failure-injection matrix. Actual live permission policy, full-pack behavior and real old-save migration remain unverified without authoritative inputs. No new audit features, journal integration or broader undo contract are introduced by this acceptance work.
