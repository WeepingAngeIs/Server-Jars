# Jail bounded nickname mutation: completed test-only increment

## Outcome and provenance

Continues completed report `9e9f8571e211f1dcf94029272c6de055efe2417d` and tested read-only handback `549ca5db231446e3cb9802345e53a4055b7fa3bf`, run `44024694485`. Initial branch and executing/queued/waiting/pending checks matched the requested checkpoint and found no active work. Passing native-fence and handback evidence was reused with exact source hashes and a baseline diff check, not rebuilt or counted again.

**A transactional plain nickname edit now passes eight disposable runtime phases. This is a bounded returned-state mutation component, NOT unrestricted playable-party handback or end-to-end jail custody. Nonempty-party admission remains blocked. All new integration is in the separate test mod.**

Exact successful tested source: `a512e532bc105ff8ccfcf913ace9ca28873ea7ca`. Run `44039907489`, attempt 1, completed successfully in both backend jobs and the final exact-commit gate. Independent downloaded-evidence verification also passed.

## Implemented mutation contract

`MutationGate` accepts one state-changing request: a plain nickname of 1-32 ASCII letters, digits, spaces, underscore or hyphen. The request binds the current session and epoch, exact saved-generation receipt, exact current Pokemon object and an unused persistent action UUID. Stale sessions/receipts/objects, another object's request, repeated IDs, unsupported names, no-ops and worker-thread requests refuse. No public game command or client packet handler is registered.

The native setter runs on privately reconstructed, constructor-tracked replacement objects. Complete native NBT must differ only at the matched original Pokemon's `Nickname` field. Full identity manifests, slots, all other Pokemon/party fields and PC bytes must remain unchanged. JSON uses the native adapter's actual Gson encoding, stable wire re-encoding and exact native reconstruction equality, with no numeric tolerance or omitted fields. Nickname reads return a detached component instead of a mutable alias to authoritative state.

Ordering is: validate current session/provider/cache/full state and original custody receipt -> construct and verify candidate privately -> commit full candidate/action ID/predecessor hash/new epoch as MUTATING -> withdraw old access and evict its exact caches -> synchronous frozen native write -> file/directory force and complete readback -> install new cache graph while access stays closed -> validate and commit READ_ONLY -> revalidate and publish a fresh session. Native queue progress is never a write receipt.

The original custody snapshot and permanent identity tombstones remain intact. The successor journal binds that original exact custody receipt while recording subsequent authorized changes. It does not rewrite custody evidence or rehabilitate old references. Accepted command IDs survive restart. Limits are 32 accepted IDs and publication epochs below 64; exhaustion refuses without reset. Maximum-exhaustion branches were inspected, not exercised to the maximum by this runtime suite.

An uncertain MUTATING record is never replayed or rolled back. A post-write exception keeps it incomplete even when new native bytes exist, with access withdrawn. A clean completed generation interrupted before access grant can be independently revalidated into a fresh epoch. Changed current files and occupied temporary destinations cause refusal rather than replacement from an old snapshot or backup.

## Native ownership, mixed lifecycle and denial handling

Tests use actual initialized native scratch managers/factories, native caches, native serialization and save workers. One explicit reviewed manager/factory/adapter owns the synthetic current graph. Alternate managers and direct adapter loads remain denied. Each accepted edit replaces the accessible objects; old session close cannot close the journal now owned by its successor. Old iterators, same-UUID clones and queued old-session requests remain rejected.

The real native autosave subscription remains enabled. Quarantined entries stay dirty and are not acknowledged as saved; unrelated entries continue through the native saver. Current edited state can acknowledge a dirty entry only after complete graph/current-file equality. A populated write captured before enrollment cannot revert the new generation, while an unrelated entry in that same queued native batch persists. Native disconnect suspension and mixed shutdown retain ownership checks. The original shutdown/executor join runs outside the lock needed by workers. Request refusal returns unavailable/false with no empty-party substitute.

This is scratch-provider integration, not the actual global player-provider/network-session/client integration. The concrete layout has no conversion children and one empty PC box. Connected clients host the fixture but are not jailed with nonempty parties. Disconnect tests call the actual native callback with a synthetic player, not a real jailed logout/rejoin. Gameplay packet/client synchronization and denial presentation remain unimplemented.

## Exact successful tests

**Eight runtime phases passed: four independent server starts for NBT and four for JSON. Each backend satisfied 67 required named phase observations. Ten unique Python crash-evidence tests passed in each matrix job.** These are not 67 independent JUnit cases or eight jail admissions. Baseline passes and the partial first-run NBT pass are not added to these totals.

| Case | Verified result |
| --- | --- |
| Exact native edits | Consecutive nickname changes preserve complete other state, UUIDs and original slots. The completed receipt precedes accessible fresh objects. |
| Current object/session ownership | Original and retired graphs, old iterators, untracked same-UUID clones, stale receipts/sessions, foreign-object and worker-thread requests refuse. |
| Duplicate actions | Accepted action IDs refuse replay before/after restart, including a committed edit interrupted before access grant. |
| Unsupported operations | Direct nickname setters, unapproved level changes and send-out remain denied. Invalid names and no-ops do not advance state. |
| Detached read value | Editing the returned nickname component does not change authoritative state. |
| Queued native batch | The stale populated write cannot revert an edit; the unrelated owner in the same batch still persists. |
| Actual autosave and shutdown | Latest edited state stays exact, quarantined dirty evidence remains unacknowledged, unrelated changes persist and the executor terminates without lock/join deadlock. |
| Native disconnect callback | Suspension/republication preserves edits with fresh objects and epoch; old session requests stay denied. |
| Halt after durable intent, before native write | Actual exit96 leaves pending candidate metadata with old native bytes. It stays quarantined on later starts with no replay/fallback. |
| Exception after verified native write | No success is acknowledged. Incomplete state and unavailable access survive subsequent starts. |
| Halt after completed generation, before access grant | Actual exit97 leaves the edit committed but inaccessible. The next JVM revalidates full state, republishes fresh objects and refuses replay. |
| Temporary/current destination conflicts | Existing .temp and changed current files remain untouched. Access is withdrawn; changed-current refusal does not advance the accepted generation. |
| Ordinary final shutdown/restart | The third edit and complete state survive normal shutdown and a fourth start; accepted command IDs remain non-replayable. |

Samples are Eevee/Pikachu in slots 0/5, native nicknames, shiny state, exact 1.1f scale and one empty PC box. Full NBT and UUID checks certify these samples, not arbitrary custom Pokemon/held items/populated PCs. JSON uses native scratch adapters; the main reviewed runtime's provider configuration is unchanged. Four total planned process exits are verified, not substituted graceful saves or physical power-loss certification. No actual nonempty jail admission/release/death/respawn pass is claimed.

## Failed attempt and correction

Run `44038406332` at `5aa9591bb0b121b663dc46b03355104329e3ff05` passed all four NBT phases but failed JSON before its first publication. Its newborn construction capability omitted native loadFromJSON/saveToJSON calls, so PokemonStore.saveToJSON was correctly denied. That overall failed run is excluded from final counts.

Commit `bf307314595fe35a5335b0bc95d1f942cb730de` adds only those codec calls to constructor-tracked private new-object construction, not permission for existing/current/revoked objects. It also refuses epoch exhaustion before a PREPARED commit. Native equality, identity, frozen IO scope and production admission checks were not weakened. Repaired policy SHA-256: `9d481491d292871dd20281dd53b8553d5ec118a56f586797035dd5cb3a4ca26a`.

Source transfer encountered a truncated inert staging fragment. A checksum-gated read-only export recovered complete records; a partial test source was completed as readable text and compiled in the disposable workflow. An initial automated repair failed its exact source assertion before changing code; the corrected repair succeeded. Temporary import/repair workflows and inert fragments are absent from the final tree; Git history remains intact. These tooling operations are not runtime test passes.

## Preserved scope and remaining blockers

Only the nine new test-directory/workflow files changed before this report. Prior generation/native-fence/read-only handback source, production Java/source sets/registered mixins, certificates, companion requirements, wallet behavior/default-off switches, existing quarantine and non-jail source remain unchanged. No production JAR was built or delivered and no different companion is approved.

| Exact reused binary | Bytes | SHA-256 |
| --- | ---: | --- |
| Minecraft-Jail-1.2.0.jar | 304634 | `5c2653a9c14d3199581fdd67e05d055b334ec2746fe7047e3c80454eb732474e` |
| Unyielding-1.2.0.jar | 17109697 | `61f6add6c68364b1c9dda4486948faf8d104c45ca2db2c40199de64a04ea38f7` |

Normal gameplay mutations, arbitrary text components, HP/experience/evolution, held items, populated-PC mutation and cross-owner transfers remain unsupported. Global provider/session/client publication, actual caller denial handling, existing battle/trade/riding references and packet/callback ordering still need integration and tests. Permanent reservation retirement/reuse and ambiguous intent resolution are unfinished. This is not a hostile-code sandbox against reflection/raw filesystem writes; prior trusted-filesystem and whole-directory rollback limitations remain.

Actual nonempty-party /jail, /unjail, death/respawn and network-disconnect custody stay fail-closed/unproven. There is no new external blocker to synthetic coding. Full-pack compatibility and occupied old-save migration require exact private current/old runtimes/config/storage copies and the matching integrated JAR; those tests were not run. No installed files, live worlds, real player data, artwork or model settings changed. Nothing was deployed or privately sourced data published.

## Independent verification and artifacts

Downloaded archive digests matched GitHub. Independent verification passed for all nine source files, raw reports versus both summaries/final gate, generated wrapper/constructor/lifecycle hashes, eight startup identities, unchanged JAR hashes, four exact crash exits, ordinary final shutdown readiness and all ten Python test names/results. Reused JAR bytes were independently rehashed from the prior candidate bundle.

Successful run `44039907489`, commit `a512e532bc105ff8ccfcf913ace9ca28873ea7ca`, attempt 1:

- NBT artifact `14309447172`: SHA-256 `76aeea356d83118f0ba04b59f8f8f4974d467bcb8a785b04f61e2ab06198fd99`.
- JSON artifact `14309735656`: SHA-256 `6aee93c89b4cd8ce9a25df62be7462ded7ff7e24e2e0dbbcd969f4979fca35d2`.
- Final gate artifact `14310215024`: SHA-256 `2545280d64ef348aed1cd13ef5f534a32a4fd67c85eeb42c5a1d578bb05e6201`.

Delivery includes exact incremental source, successful and failed-attempt raw evidence, checksums and read-only independent verifier/results. No production JAR or private save is included.
