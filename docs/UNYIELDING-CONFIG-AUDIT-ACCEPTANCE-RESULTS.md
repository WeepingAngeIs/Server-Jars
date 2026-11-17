# Independent config-audit undo acceptance

This advances the public-input gap recorded in the [acceptance index](UNYIELDING-ACCEPTANCE-INDEX.md), after the completed [destination checkpoint](UNYIELDING-DESTINATION-ACCEPTANCE-RESULTS.md), report commit `e81a6298f7ed33127b4788ff96d4dc9f2d43d539`. The initial candidate is the exact Unyielding-1.2.0.jar from [run 44036656345](https://github.com/WeepingAngeIs/Server-Jars/actions/runs/44036656345), artifact `14307985889`, SHA-256 `216de93ca87cc9e0a54f0f0991c675a1058ee95de3247c8efbebffc664f0c504`. Work is isolated on `codex/unyielding-config-audit-acceptance`.

## Existing contract and prior evidence

Five ConfigAuditPolicyTest cases cover pure redaction, display, allowlist/current-value comparison, retention and paging rules. Earlier ConfigEditor runtime evidence covers generic editor behavior, including direct handler calls with connected players, but does not establish the later audit/undo implementation through actual client requests and commands. Completed ground, reward, meeting, destination, vein and tutorial suites are preserved and are not repeated here.

The command is `/configaudit [page]` or `/configaudit undo`; there is no entry ID or undo token. Both command and implementation require operator level 2. Undo acts on the global latest entry, including another administrator's edit, rather than a per-actor history. A successful undo uses normal ConfigEditor handling and appends a reverse audit record. Repeating undo can therefore reverse that new record. Unsupported or no-op latest entries block undo; older entries are not searched.

Current-value matching is an exact optimistic comparison, not an epoch/ABA-history guarantee. Numeric allowlisting covers map icon scale, origin creative flight speed and seven existing teleport cooldowns. This fixture uses representative Double and Int settings rather than testing every allowlisted value.

## Bounded native scope

Two actual disposable clients use real ConfigEditor.Action packets and ordinary `/configaudit` commands. Test-only ordered server receipts and client responses establish production processing before assertions. Config settings, world/player state and actor roles are synthetic inputs in the GitHub-hosted world; no live configuration is imported.

Eight cases target empty-history refusal; valid numeric edits/undo/reverse entries; representative Int undo; nonoperator and revoked-operator refusal; stale expected values and a declared unmanaged config conflict; unsupported/no-op latest entries; global ordering across two administrators; and text minimization with unsupported string undo refusal. Text/list minimization is checked only in the audit record; authorized config editors may legitimately see the current setting.

Successful actions must record the actual actor, key and values, preserve prior audit entries, change only the expected config target and retain unrelated settings/player data. The target is checked in memory and in generated TOML files using a read-only parser. Whole-file byte equality is not required after valid saves, because normal config saving may rewrite formatting. Refused operations must preserve current config and audit state. Native inventory/physical coins, XP, progress/claims, spin balances, home/back data and an unrelated party provide preservation sentinels; this is not every external bank currency.

## Verification record

Native verification pending. The workflow reuses the exact verified candidate and skips production builds/unit tests while production is unchanged. Only a reproduced defect justifies a narrow production fix. No Jail runtime or separate Jail build/certificate prerequisite is invoked.

## Preservation and limits

Heavy builds/runtime work uses GitHub-hosted disposable clients/worlds and generated configuration files. No installed profile, live config, real world, private data, setting, hotbar, artwork or model file is modified or uploaded. Version remains 1.2.0; no installation or deployment occurs. Prior artifacts/reports and the separate earlier exact-pair Jail certificate/gates are preserved. Basic Mawile food behavior is not retested.

The audit uses ordinary world SavedData, while COMMON config files save separately. A successful method call, in-memory record or NBT snapshot does not prove atomic durability, a real server restart, a crash or physical power-loss recovery across those stores. This is not an exhaustive settings, provider, paging/retention or failure-injection matrix. Actual live permission policy, full-pack behavior and real old-save migration remain unverified without authoritative inputs. No new audit features, journal integration or broader undo contract are introduced by this acceptance work.
