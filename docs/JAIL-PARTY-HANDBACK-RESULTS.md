# Jail restricted native publication: completed bounded increment

## Outcome and provenance

Continues report `87b2d81d714f5660c3e6bdac93db2fab2a785f00` and passing native-fence tests at `87c2b6383ea528b1d604f415c0ffab49131884ac`, run `44019522739`. Initial branch and executing/queued/waiting/pending checks matched that report and found no active work. Existing generation and native-fence implementations/evidence were reused, with source hashes and a baseline diff check. Their old test suites were not rerun or counted as new coverage.

**A restricted READ-ONLY native successor-publication component now passes six disposable runtime phases. This is NOT a playable-party release. Nonempty-party admission, end-to-end custody and production inclusion remain disabled/unproven.**

Initial implementation starts at `43ee15af798507f00fd239173a214d987cc5ed15`. The complete initial test setup is `3b3af3fafaa9fd8743f13040bdadd13abe3a2519`. The corrected and successful tested source is `549ca5db231446e3cb9802345e53a4055b7fa3bf`, run `44024694485`, attempt 1. Both backend jobs and the final exact-commit gate succeeded. The downloaded archives and raw evidence were independently verified.

## Implemented scope

All additions are under `.github/party-handback/` and `.github/workflows/jail-party-handback.yml`. `PublicationGate` adds a private test-mod policy in front of the unchanged native fence. Required constructor-return hooks identify newly born native stores, Pokemon and PC boxes during a non-inheritable reconstruction capability. Existing objects and separately constructed same-UUID clones do not acquire successor permission.

The component accepts an exactly verified RESTORED managed snapshot and its matching current native files, one reviewed concrete manager/factory/adapter binding, and one empty PC box. It creates a separate generation journal bound to owner, provider, full source hash, exact custody receipt, monotonically increasing epoch and publication phase. The original generation records, identity reservations and permanent native-fence tombstones are not removed.

Ordering is:

1. Verify the complete staged return, native destination and absence of another active successor.
2. Commit PREPARED, then reconstruct and initialize fresh native objects privately. Validate complete NBT, UUIDs, slots, empty backup topology and the exact native owner-observer list.
3. Replace the reviewed party and PC cache entries under the existing native lock. The objects are still inaccessible through the gate.
4. Revalidate and commit READ_ONLY; revalidate again before installing the single accessible in-memory successor graph.

Here, retirement means replacing deny-all access with a narrowly authorized read-only epoch. It does NOT mean deleting identity reservations, authorizing a new custody transaction or enabling unrestricted Pokemon use. The allowed surface is deliberately small: selected party lookups/iteration and level/shiny reads through the exact bound native manager/factory. Direct adapter/file loads, alternate managers, mutations and send-out remain refused. No client party UI or player gameplay is certified.

Disconnect suspension commits SUSPENDED, revokes the current graph/iterator lease and removes only its exact cache entries. Republishing uses a newer epoch and newly reconstructed objects. Restart does not trust an old in-memory permission. It must reopen a clean committed/suspended record and revalidate current files, frozen source and custody receipt before creating a fresh epoch. PREPARED or mismatched records remain quarantined and are not replayed. A committed epoch interrupted before access grant can be independently revalidated into a new epoch.

Complete graph/native equality is checked at permitted access and read-only save boundaries. A changed graph, custody receipt, provider or native file withdraws access. The request adapter reports unavailable with no empty-party fallback. This adapter is not yet wired to actual gameplay packet handlers.

## Mixed-owner lifecycle

The explicitly bound synthetic factory retains its REAL native autosave subscription. Its dirty-store batch is partitioned by ownership. Quarantined/revoked entries remain dirty, are not acknowledged as saved and cannot stop unrelated entries from reaching the native saver. A fresh read-only store may clear its own dirty entry only after verifying that its entire state and existing durable native files are unchanged; no new native async-save receipt is invented.

A batch already queued before enrollment is checked entry by entry at execution. The fenced stale entry gets a rejection outcome, not a save receipt, while the unrelated entry in the same native batch still persists. Only ownership-denial exceptions receive this per-entry handling; unrelated IO errors are not silently converted into success.

Native shutdown suspends active epochs under the lock, then invokes the original factory shutdown and executor join OUTSIDE that lock. This avoids joining a worker that needs the same lock. The tested shutdown drains the executor and preserves unrelated owners. Cross-thread context lookup is synchronized and counters are concurrent. Existing callback cache predicates return false for revoked store objects instead of treating them as current.

## Exact completed tests

**Two native backend suites each passed three independent JVM phases: six server starts total. There are 52 required phase observations per backend and ten unique Python crash-evidence tests, run in both jobs.** These are not 52 independent JUnit cases, six jail admissions or an end-to-end gameplay test. The previous generation/fence passes are reused rather than added to these totals.

| Case | Verified result |
| --- | --- |
| Fresh publication and full state | Manager and factory return the same new native party. Its objects differ from the original aliases. Complete serialized state, identities and original slots match the frozen source. |
| One authorized successor | A second publication and an alternate manager are refused. Direct native adapter access remains denied after restricted publication. |
| Old references and identity clones | Old party/Pokemon references, an untracked same-UUID clone, a retired-epoch Pokemon and an iterator obtained before suspension cannot perform the tested access. |
| Old queued write and mixed batch | A populated native save captured before enrollment is rejected after retirement; the unrelated owner in that same queued batch is fully preserved. |
| Native autosave | The actual autosave subscription runs with fresh, quarantined and unrelated owners together. The unrelated change persists, fresh read-only state stays exact, and quarantined dirty evidence is retained. |
| Native disconnect callback | A synthetic ServerPlayer passed to the native disconnect callback suspends the epoch. Republishing creates a newer epoch and new objects. A queued action targeting an old epoch is denied. |
| Shared native shutdown | The original shutdown completes and its executor terminates without a lock/join deadlock. Unrelated native state survives reload. |
| Cache swap before retirement receipt | Actual JVM halt94 occurs after cache insertion while the access gate is still closed. PREPARED state remains quarantined through both later starts. |
| Receipt before access grant | Actual JVM halt95 occurs after READ_ONLY commits but before access is exposed. The next JVM verifies the record and exact files, then publishes a fresh epoch rather than reusing old objects. |
| Changed destination | An altered native current file blocks publication before a new publication journal is created. The changed bytes and custody generation remain untouched. |
| Unsupported gameplay | Mutation and send-out on the new graph remain denied. No nonempty-party admission or playable release is enabled. |

Positive samples use synthetic Eevee/Pikachu in party slots 0/5, custom nicknames, shiny state, an exact 1.1f scale and one empty PC box. Complete NBT and UUIDs are compared, not just species counts. Tests use independent scratch native managers/factories. The main reviewed runtime still uses its original configured provider; JSON is exercised through native scratch adapters. Connected clients are not jailed with nonempty parties.

The disconnect test invokes the actual native callback with a synthetic player object. It is NOT a network logout/rejoin of a jailed player. Normal final-phase server shutdown is real; both earlier phases use verified abrupt process exits, not substituted graceful saves. Process halts do not establish physical power-loss durability.

## Failed attempt and correction

Run `44023508274` at `3b3af3fafaa9fd8743f13040bdadd13abe3a2519` compiled successfully and passed the ten Python evidence checks, but both native suites stopped in their first phase. Detached reconstruction required `Pokemon.refreshOriginalTrainer` and `PCStore.tryRestoreBackedUpPokemon`; the new capability had not permitted those calls, so the unchanged fence correctly refused them before any publication.

Commit `549ca5db231446e3cb9802345e53a4055b7fa3bf` permits the reviewed reconstruction call only for constructor-tracked new objects. The PC routine additionally requires an empty backup. This is not permission for native file fallback, an existing store or an arbitrary provider. Full NBT/identity comparisons remain mandatory. Inspection also showed that native PlayerPartyStore.initialize adds its own owner to the observer list, so the initial empty-observer assumption was corrected to exact equality with that single owner. The same patch synchronizes cross-thread policy lookup and gives access-boundary integrity failures an explicit denial outcome.

The incomplete first run is retained separately and excluded from the final six-phase total. The final run has no failed or incomplete phase. No equality, source/identity reservation, certificate or production admission check was relaxed.

## Independent evidence verification

Downloaded archive digests match GitHub's reported values. The read-only independent verifier checked both backend summaries against raw phase reports and the final gate; all nine incremental source files; generated wrapper/constructor/lifecycle source hashes; six startup identities; unchanged JAR hashes; four exact crash exits with closed-gate observations; normal final shutdown readiness; and all ten Python test names/results. It passed. Actual bytes of the reused JAR pair were independently rehashed from the prior verified bundle.

The generated interception breadth is not an exhaustive method-test count. Reused baseline evidence proves its unchanged earlier implementation; it is not represented as an exhaustive rerun of every previous case under this new publication policy.

## Preserved production scope

| Exact reused binary | Bytes | SHA-256 |
| --- | ---: | --- |
| Minecraft-Jail-1.2.0.jar | 304634 | `5c2653a9c14d3199581fdd67e05d055b334ec2746fe7047e3c80454eb732474e` |
| Unyielding-1.2.0.jar | 17109697 | `61f6add6c68364b1c9dda4486948faf8d104c45ca2db2c40199de64a04ea38f7` |

No production source set, registered production mixin, admission policy, wallet behavior/default-off setting, certificate, companion requirement, existing generation/fence source, quarantine record or non-jail feature changed. Only the disposable test-mod copies use the new hooks. No new production JAR was built or delivered. No installed files, live worlds, real player data, artwork or model settings were changed. No deployment or private-save publication occurred.

## Remaining integration gaps

Playable handback is still missing. It needs a safe contract for normal post-release mutations and their durable saves, actual global player-provider/cache/session publication, client synchronization, appropriate denial handling in real packet/gameplay callers, and verified behavior for ongoing send-out/riding/battle/trade references. Terminal reservation retirement/reuse is not implemented: permanent identity tombstones remain in place. The current read-only graph cannot be called a playable party.

Actual nonempty-party jail admission/release, death/respawn and network disconnect-custody are still unproven and fail-closed. Arbitrary custom Pokemon/held items, populated PCs, conversion-backed or third-party providers, cross-owner gameplay transactions and arbitrary raw/reflection access are outside this tested contract. This is not a hostile-code sandbox. The prior journal's trusted-local-filesystem assumptions and whole-directory rollback limitations still apply.

There is no new external blocker to continued synthetic coding. Full-modpack compatibility and occupied old-save migration still await exact private current/old runtime, configuration and storage copies plus the matching old integrated JAR. Nothing additional was needed from the user for this increment.

## Successful artifacts

Run `44024694485`, commit `549ca5db231446e3cb9802345e53a4055b7fa3bf`, attempt 1:

- NBT artifact `14301959471`: SHA-256 `86a3648813856986772560b7b33c09e0cd98ecd459166956e36076e21c7c1587`.
- JSON artifact `14302837562`: SHA-256 `5514f7253a6237f22eb9745c3bc505e8f2b86f2e5e8ee8ad3a44277f859753ea`.
- Final gate artifact `14302573563`: SHA-256 `d313598f8e6ddf9a4d66fb92c55c0255cee8d6ef227dbbe0de029dca436c5e78`.

The delivery contains source, successful and failed-attempt evidence, checksums, this report and the read-only independent verifier/results. It contains no production JAR or private save.
