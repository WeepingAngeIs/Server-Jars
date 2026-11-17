# Jail party generation safeguards: completed bounded increment

## Outcome and exact checkpoints

Continues report `cdcf7737817b7ac93c81d297e8614765d2d4dc38` and unchanged native regression fixtures at `3353a43526130126ed8e3c097b8025a77c261373`. Initial branch and all executing/queued/waiting/pending run checks found the requested checkpoint and no active work.

The new implementation is a Jail-owned generation journal, identity-reservation authority and guarded native snapshot bridge. It is real new executable safeguard code, not another wallet preservation probe. It is deliberately UNREGISTERED and excluded from production source sets. It does not admit a nonempty party, remove a live party, return Pokemon to a player, or install global native hooks.

Implementation commit: `7c9f5391beb2363442ff80e084e978a4b2676184`. Native codec correction: `613288c7fbe36f1df945b542515d2bd480f39a3c`. Exact successful tested commit: `a41634908aa5b5d6d45833867a4f04646769a68a`. Run `44006642234` completed successfully, including the final exact-commit evidence gate. Downloaded artifacts, source hashes and raw phase reports were independently verified.

## What was implemented

`GenerationJournal` has one exclusive writer and immutable, hash-chained generations. A receipt binds journal UUID, runtime/provider binding, generation, record hash and complete state hash. Commit forces the intent and data, atomically installs the generation and current pointer with directory synchronization, removes the intent, and verifies the complete readback. Only then is a receipt returned. IO failure propagates and poisons the open instance. Pending/temp/orphan state, missing or corrupt history, stale pointers, wrong bindings and unsupported filesystem operations refuse load instead of selecting an older backup or replaying an uncertain write.

This is an error-aware receipt for the NEW managed write, not a receipt for Cobblemon's unrelated asynchronous saver. The trusted private local directory and filesystem must support these operations. Process-crash tests are not proof of physical power-loss durability everywhere, hostile filesystem safety or detection of a privileged whole-directory rollback.

`PartyCustodyGuard` commits complete original/active party and PC snapshot bytes, UUID manifests, owner, provider, transaction and reservations in ONE generation. RESERVED denies exposure; SEALED holds an owned empty-party view with unchanged PC; RESTORED contains the original snapshot after exact destination comparison. These are managed storage phases, not completed native source removal or player restoration.

Stale receipts, foreign owners, alternate provider labels, conflicting identities across managed owners/providers, occupied or changed return destinations and repeated returns are refused before committing. Reservations remain pinned after managed restoration, preventing replay; retirement and reuse are intentionally not implemented. Limits are explicit: 16 transactions, six party IDs, one PC box/30 PC IDs, bounded bytes and generations. Hitting a limit refuses rather than discards state.

`CobblemonSnapshotCodec` derives UUID manifests from concrete native stores, rejects foreign/duplicate/overflow/unknown topology, stores complete canonical native NBT and requires exact native reconstruction. Its empty view removes uniquely matched full Pokemon slot payloads only in a PRIVATE snapshot copy, preserving the remaining metadata and PC bytes. It never initializes a live store, recalls Pokemon, overwrites native files or publishes reconstructed objects.

The guarded load path accepts only the exact managed generation, owner/provider and full active state. Actual native current/backup/provider results are untrusted candidates, not fallback authority. A native old-backup resurrection is rejected WHEN PRESENTED TO THIS GATE. Direct native reads and mutable references that bypass it are not protected. Provider labels are trusted-call inputs; real provider identification and interception remain integration work.

Code locations: `minecraft-jail/src/partySafety/`, `src/partySafetyNative/`, `src/partySafetyTest/`, `.github/party-guard/`, and `.github/workflows/jail-party-generation-guard.yml`. These sources are compiled for independent tests, not normal production JARs. Existing native fixtures are adapted only as copies in the disposable test mod.

## Exact completed tests

**34 standalone Java tests passed, zero failed. Two native backend suites each passed three independent JVM phases: six server starts total.** These are not 34 JUnit cases or six end-to-end jail admissions. Every backend retains the 14 old native counterexample observations and verifies 15 new safeguard observations. Earlier passes are not double-counted.

| Case | Verified result |
| --- | --- |
| Exact generation and reservation order | Reservation generation 1 precedes managed empty generation 2; exact original snapshots return in generation 3. |
| Actual failed write and queue progress | A real filesystem obstruction fails the write Future with IOException. A later queue sentinel succeeds, but no receipt is issued and the guard refuses further use. |
| Interrupted commits | IOException injection at all six commit boundaries and six separate child-JVM hard exits pass. Incomplete stages refuse reopen; fully completed clean generations can be verified without replay. |
| Actual native backup recovery | Native `.old` recovery really reloads the populated synthetic party after corruption and restart. The gate refuses that candidate and supplies its own empty active snapshot. |
| Identity and provider conflicts | Native same-UUID party/PC data, conflicting reservations, stale receipts, foreign owners and alternate provider labels are refused. Two racing reservations have exactly one winner. |
| Occupied or changed destination | Refusal preserves state and does not advance the generation; no merge or overwrite. |
| Complete managed return and restart | Native sample UUIDs, complete serialized state and original slots are preserved. Restoration survives a second restart; repeated return and re-reservation refuse. |
| Ambiguous write across real restart | The incomplete new journal remains quarantined through the native fixture's hard halt and both following starts, without fallback or replay. |
| Corruption and writer checks | Missing/corrupt pointers or generations, backward pointers with newer history, wrong runtime bindings, symlink roots, second writers and implicit reset attempts refuse. |

The positive native sample has Eevee in party slot 0, Pikachu in slot 5 and one empty PC box. The negative identity fixture has the deliberate same-UUID PC entry. Full-state checks certify these samples, not every custom Pokemon, held item or populated PC. Native NBT and JSON scratch adapters are exercised; the configured primary runtime stays NBT. The unit suite uses opaque synthetic bytes, while the native suites use the pinned Cobblemon codec and real storage adapters.

All six startup identities match the exact commit and unchanged JAR hashes, separate ownership, old integrated Jail absence, non-jail component presence, empty escape rewards and default-off packed wallets. Independent verification matches all ten incremental source files, unit names/logs, phase flags, source hashes, real hard-halt markers and the final report.

## Failed attempts, corrections and ancillary CI

- `44003829863` at `a63c9553...`: units passed, native setup failed because the synthetic PC helper passed a null overflow callback. `73b53c36...` supplies the native-required callback and throws on unexpected overflow.
- `44004496692` at `73b53c36...`: exact-state checks refused the incorrectly empty view. The initial codec relied on `clearParty()` on an uninitialized detached clone; native removal needs store coordinates. `613288c7...` replaces that assumption with the verified private-snapshot derivation. No equality or ownership check was relaxed.
- `44005372440` at `613288c7...`: all units and all three NBT phases passed. JSON passed phase one but its client crashed before phase two in NeoForge early-display rendering (`Already building`, following a union-filesystem startup error). No phase-two guard assertion pass is claimed. The final workflow-only change preserves per-attempt evidence/client crash reports and retries unchanged safeguard code. The final dual-backend run passes.

The repository's preexisting broad split-runtime trigger also automatically launched `44005372427` at `613288c7...`; its jobs and final gate succeeded. Its rebuilt baseline excludes the unregistered guard and is not delivered here, counted as new party-guard coverage, or used to approve a different companion. Failed and partial runs are retained separately and excluded from the final totals.

## Preserved production scope

No production Java, wallet behavior, default-off switch, quarantine mechanism, certificate, companion requirement, non-jail source, original native fixture or private-copy planner changed. The safeguard workflow reused these exact binaries, independently rehashed from the prior verified archive:

| Binary | Bytes | SHA-256 |
| --- | ---: | --- |
| Minecraft-Jail-1.2.0.jar | 304634 | `5c2653a9c14d3199581fdd67e05d055b334ec2746fe7047e3c80454eb732474e` |
| Unyielding-1.2.0.jar | 17109697 | `61f6add6c68364b1c9dda4486948faf8d104c45ca2db2c40199de64a04ea38f7` |

No new production JAR containing these safeguards was built or delivered. No installed files, live worlds, real player data, artwork or model settings were changed; no deployment or public private-save upload occurred.

## Remaining integration gaps and genuine blockers

Nonempty-party admission remains blocked. There is no new actual jail admission/release/death/respawn/disconnect-custody pass. The next integration needs global interception of supported native current/backup/conversion/provider loads, actual provider binding, native and queued-write fencing, direct-reference and action fencing, and a verified live ownership handoff before source removal or player return. Send-out, riding, trades, battles, PC mutation, death/clone and disconnect ordering need explicit coverage. Terminal reservation retirement, ambiguous-transaction recovery and migration remain unfinished.

No new external blocker prevents continued coding: the bounded managed layer now passes its tests. The missing global interlock and live handoff are the blocker to enabling admissions. Native counterexamples still occur outside this new gate, as the regression controls demonstrate.

Full-pack and occupied old-save tests still require exact private current/old runtime, configuration and storage copies plus the matching old integrated JAR. They were not run here. No private files are required to reproduce or continue the new managed-layer work.

## Successful evidence

Run `44006642234`, commit `a41634908aa5b5d6d45833867a4f04646769a68a`, attempt 1:

- Unit/source artifact `14294932206`, SHA-256 `6de96d51b8f16b010a45647f4e26ca72049d1fbfc6e6b582ed2a898996d46a29`.
- NBT artifact `14295266340`, SHA-256 `148d0ff1316f18fc3cb35adc101bbbb8b39f114d4eba135ee6090fa1818ecf89`.
- JSON artifact `14294174604`, SHA-256 `f2f95b1eb69928ae5b375e6eda658fd9a35d147adc55af875655d4aca0a2b79f`.
- Final gate artifact `14294687906`, SHA-256 `26037527d9f9e36db166688104aea4429b6e7ed77d8dbca2d2c81b2abd7147f8`.

The delivery contains raw successful and failed-attempt evidence, source, full report, checksums and an independent verification script/results. It contains no production JAR or private save.
