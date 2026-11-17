# Experimental party generation safeguards

This is an unregistered storage implementation in the existing Jail project. The normal Gradle source sets do not compile or package these directories. It does not replace Cobblemon's provider, add mixins or permit nonempty-party admission. The passing packed-wallet JAR and its exact companion are reused byte-for-byte.

## Implemented boundary

`GenerationJournal` owns complete opaque snapshots under one exclusive writer lock. Immutable generations contain the journal identity, exact runtime/provider binding, previous-generation hash and full payload. A forced intent precedes each write. New data and the head are forced and atomically renamed, with directory synchronization. A successful receipt identifies the exact generation, record and state hashes; queue progress never produces a receipt. An IO error poisons the instance. Any pending intent, missing/corrupt pointer, orphan, chain mismatch, foreign binding or unsupported filesystem operation refuses load. There is no old-generation fallback, automatic intent deletion or uncertain replay.

`PartyCustodyGuard` persists original and active party/PC snapshots and exclusive UUID reservations in that same generation, not a separately committed index. RESERVED denies exposure, SEALED exposes only an exact empty-party snapshot with unchanged PC, and RESTORED exposes only the complete original snapshot. Provider, owner, transaction and expected receipt must match. Changed destinations, stale requests and duplicate UUID claims are refused before a commit. Terminal identity reservations remain pinned; reusing an owner after a completed cycle is deliberately unsupported in this increment.

`CobblemonSnapshotCodec` derives UUID manifests from actual native stores, uses canonical full NBT and requires exact native roundtrip equality. The bounded codec accepts concrete native PlayerPartyStore and a one-box PC with no backup/overflow identities. It rejects duplicate party/PC identities and mismatched owners. It creates detached copies only, never clears or populates a real player's store.

## What a receipt does NOT certify

It certifies only this gateway's owned storage commit under the tested filesystem semantics. It is NOT evidence that an asynchronous native save finished, that every native provider passes through the gate, or that an existing mutable Pokemon reference has been revoked. The raw snapshot core trusts its native codec boundary; it is not a parser for arbitrary caller-supplied Pokemon bytes.

The load gate denies stale native backup and alternate-provider candidates presented to it. No global Cobblemon load/provider/action hook is installed. The entire process, trusted parent directory and journal files must remain under one authority; hostile filesystem edits, whole-directory rollback/deletion, external writers and total backup rollback are outside this model. Hashes detect corruption, not a privileged attacker rewriting all history. File/directory force and atomic-move failures are fail-closed. Hosted process-crash tests are not physical machine power-cut or arbitrary/network-filesystem certification.

Full native admission, runtime source removal, managed provider registration, preexisting-reference revocation, send-out/PC/trade/battle/riding fencing, release handoff into gameplay, death/clone integration and upgrade/migration remain unimplemented. Party admission and ambiguous recovery remain blocked. No certificate or companion approval changes are part of this work.

## Tests

JDK-only tests compile `partySafety/java` and `partySafetyTest/java`. Hosted native tests copy the candidate and codec into the separate test mod, reuse the exact packed-wallet pair, and add guarded assertions to copies of the existing native storage failure fixtures. Neither production JAR is modified. The unmodified fixture files remain regression sources. All data and filesystem failure points are synthetic and disposable.
