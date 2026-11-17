# Jail nonempty-party persistence and ownership investigation

## Outcome and provenance

Continues completed report `f5f68940ede91e7217af700c17379640416cf275` and the passing packed-wallet implementation at `8e9716d94a932fe5110ae84abdc90adfe0017974`. The initial branch check matched that report and found no executing, queued, waiting or pending run.

**Nonempty-party admission remains blocked. No production party-custody adapter was enabled or added.** The selected narrow transfer design failed prerequisite completion and ownership guarantees. This pass implemented executable native-storage failure/ownership tests and an exact-evidence gate, not another wallet preservation suite.

The pinned API audit succeeded at `be4c6a336207ec62d9d668b8f5f9205dc3c56dbe`, run `43997255522`, artifact `14290606961`. It inspected 107 storage/prioritized-list classes and 25 mutation handlers from the exact checksum-pinned Cobblemon binary. Final runtime test code is `3353a43526130126ed8e3c097b8025a77c261373`; run `43999123113` completed successfully. There were **two backend suites with two JVM phases each**, four server starts total, and fourteen required boolean observations per backend. Successful counterexample tests do not mean custody admission passed.

## Bounded design evaluated

The candidate was complete native party snapshot custody using the reviewed file-backed factory, exact owner/store/Pokemon identities and original slots, followed by source removal and later conflict-refusing restoration. A safe implementation would promote ownership to HELD only after a durable source-removal receipt, and to RETURNED only after a durable, complete destination save. Unknown or conflicting state would remain quarantined.

The experiments used native NBT and JSON adapter instances in explicitly test-owned subdirectories of disposable hosted worlds. Synthetic parties included an Eevee in slot 0 and a Pikachu in slot 5, with additional Pokemon for mutation tests. The ownership test used a one-box synthetic PC. Full native NBT snapshots, UUIDs and slot contents were compared for these samples.

The actual reviewed runtime selected one `FileBackedPokemonStoreFactory` for both the connected synthetic player's party and PC, with `NBTStoreAdapter` and a `ReforgedConversion` child. The JSON suite exercised separate native JSON scratch stores; it did not change the server's configured primary backend. The alternate-provider test constructed a separate native manager with two factories. It is not a claim that the user's actual server currently uses that topology.

## New observed results

All rows below were reproduced for both native NBT and JSON scratch stores.

| Experiment | Observed result and custody consequence |
| --- | --- |
| Ordinary save/load control | Complete serialized state of the synthetic sample round-tripped. This is not certification of every custom Pokemon/held-item case. |
| Return from `save` while writer is blocked | The method returned while the disk still contained the previous snapshot. Releasing the writer allowed the updated state to appear. A return from `save` is not a completed transfer. |
| Real filesystem write failure plus queue sentinel | A test-owned parent directory was temporarily replaced by a file. Native write failed with `java.io.IOException: Not a directory`, while a subsequent sentinel task completed successfully. The store was dirty before the call but no longer dirty afterward. |
| Subsequent `saveAll` after restoring the directory | The failed change was not retried; native reload still returned the preceding snapshot. Merely draining the executor or calling saveAll cannot serve as an error-aware removal receipt. |
| Successful empty save, then native backup recovery | A populated party was saved, cleared and successfully saved/reloaded as empty. Its previous populated generation remained in `.dat.old` or `.json.old`. After deliberate corruption of the current test file and an actual restart, native recovery loaded the original populated party. This can contradict a separate custody journal's HELD state. |
| Abrupt JVM halt after queued empty save | After live clearing and a returned save call, the writer remained blocked and the disposable JVM halted with marker `PARTY_STORAGE_PLANNED_HALT_91`. A separate JVM loaded the original populated disk state. No graceful shutdown flushed the queued removal. |
| Same UUID in party and PC | A native-codec clone retained the original UUID. The native PC accepted it while the original remained in party. Both full store snapshots and both copies survived independent restart/reload. This deliberately constructed conflict demonstrates missing global uniqueness at these calls, not spontaneous duplication in normal gameplay. |
| Occupied destination | Native `set` replaced the existing occupant. A custody adapter must reject conflicts before mutation; it cannot rely on native set to refuse. |
| Provider selection | A separate native manager selected party from one factory and PC from another when the first lacked a PC. Independent getParty/getPC results do not prove a single exclusive storage owner. |

The raw logs contain the native old-save recovery warning, the intended hard-halt marker and successful independent startup identities. Downloaded reports were independently revalidated against the exact test source, all four startup identities, actual JAR bytes and phase flags. No partial report is accepted as a pass.

## Concrete blocker

The stock `FileBackedPokemonStoreFactory.save` returns void, removes dirty state before dispatching a worker and exposes no per-save success receipt to the caller. A later executor task can complete even when that write failed. The native flat-file writer uses a temporary file, copies the current file to an old backup, copies temporary bytes to current, then deletes temporary. It is not an inspected atomic-rename/generation transaction. Its load path can recover the previous generation independently of Jail's journal.

Consequently, **snapshot -> clear -> native save -> queue wait -> mark HELD is unsafe**. Reading the empty current file afterward does not solve the older-backup recovery path. UUIDs and store coordinates also do not establish exclusive cross-store ownership, and native destination assignment is an overwrite operation.

No real durable-save acknowledgement or authoritative ownership handoff suitable for party custody was established. These are specific integration gaps, not a claim that safe custody is impossible or that access to private saves is required for all further coding.

## Required next implementation boundary

A version-bound Jail-owned persistence/ownership integration would need:

1. An error-aware, monotonic generation receipt covering the exact source state and backed by the owned durable write, rather than queue progress. All queued native writes must respect that generation.
2. A load/recovery interlock before any native current, backup or conversion/provider result becomes player-accessible. Older populated data must not silently become live while the transaction owns those Pokemon.
3. Exclusive Pokemon-identity reservations across party, PC, overflow/backup state and all supported providers; complete snapshots; and pre-mutation refusal of occupied or mismatched restoration destinations.
4. Fencing of direct references and native send-out, riding, trade, battle, PC and mutation paths, including already-queued actions. A higher-priority provider alone does not revoke previously obtained references.
5. Fault injection across the implemented transfer stages, followed by actual nonempty-party admission, release, death/respawn, disconnect and restart tests. Ambiguous records must retain quarantine, without rewriting fingerprints or replaying uncertain transfers.

Keeping a party in place avoids the transfer but still needs verified action/mutation fencing. That alternative was not implemented or certified here.

## What did not run or pass

There was no newly enabled nonempty-party jail admission, release, death/respawn or disconnect-custody path to test. Existing ordinary-death preservation and wallet passes were not recycled as evidence for that missing implementation. This run tests storage primitives, IO failure, a hard process stop and native restart/fallback using synthetic stores, not end-to-end party escrow.

A process halt is not a machine power cut. Current-file corruption was deliberately injected to exercise fallback; this is not a claim that an interrupted native copy was observed producing those exact bytes. No power-loss durability guarantee is made. Arbitrary origins, attached items, third-party callbacks, live trade/battle references and private full-modpack/save migration remain unverified.

The first runtime attempt, `43998437915` at `966a24c4fbe39a714b8db365eeaf379b5d5f6fd6`, stopped because the synthetic PC had zero boxes. The fixture was corrected using native resize to allocate a real test box. No duplicate check, production rule or certificate was relaxed. That incomplete run is excluded from the final four phases.

## Preserved production scope

No production source, dependency certificate, companion hash, admission switch, custody journal, wallet implementation, private-copy planner or migration matrix changed. The default-off wallet setting was checked at every startup. No replacement production JAR was built.

| Exact reused binary | SHA-256 |
| --- | --- |
| Minecraft-Jail-1.2.0.jar | `5c2653a9c14d3199581fdd67e05d055b334ec2746fe7047e3c80454eb732474e` |
| Unyielding-1.2.0.jar | `61f6add6c68364b1c9dda4486948faf8d104c45ca2db2c40199de64a04ea38f7` |

The existing packed-wallet candidate remains the passing baseline, not a different companion from the non-jail chat. Tests ran on GitHub-hosted synthetic loopback environments. No installed file, real world/player data, artwork or model setting was changed, and no deployment occurred.

Full-pack and occupied old-save tests still wait for exact private runtime/config/storage copies and the matching old integrated JAR. Nothing new is needed from the user to reproduce the native blockers. No private saves or credentials were uploaded.

## Evidence locations

- Audit artifact `14290606961`: SHA-256 `09c1dec93067e3ea41218ad7fc1f9bba9655380c731771229e16a4f128a133ed`.
- NBT runtime artifact `14290973191`: SHA-256 `cdadc32358b496b22106e0972892667e6eb43315d04ff2dea3c70c518c7c7690`.
- JSON runtime artifact `14291551898`: SHA-256 `3a000ee8ef314d8ecf21550e70059e8585252e7ca7ae8b7c4cce653076981037`.
- Runtime flags and startup identities: `party-storage-{nbt,json}-phase{1,2}.json`, `party-storage-{nbt,json}-summary.json`, and `split-identity-jail_integrated_smoke-{1,2}-normal.json` in the respective runtime archives.
- Audited methods: `FileBackedPokemonStoreFactory.save/saveAll/shutdown/onPlayerDisconnect`, `OneToOneFileStoreAdapter.save/provide`, `PokemonStore.set`, and `PokemonStoreManager.getParty/getPC`. The delivery's `audit-method-index.json` gives disassembly line ranges and hashes.
