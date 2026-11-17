# Experimental native custody quarantine fence

This test-only increment continues a43219a6 and reuses the immutable guard and
8e9716d9 JAR pair. No production sources, source sets, mixin descriptors,
certificates, wallet settings or native test fixtures are edited.

The dedicated disposable test mod instruments exact public method descriptors
from the checksum-pinned Cobblemon binary. Every native method is wrapped for its
whole execution under one reentrant lock; owner/UUID/input/output checks occur at
the actual native methods, not merely voluntarily at a wrapper service. Immutable
UUID getters and adapter filename resolution are excluded for classification.

A separate monotonic GenerationJournal catalog commits permanent owner/identity
fences BEFORE a custody reservation or source change. Startup loads it lazily at
the first intercepted native call and fails closed on missing/ambiguous history.
There is no retirement API. File-backed and manager loads, direct current/backup
loads, reviewed conversion entry points, native queued writes and retained native
store/Pokemon references are fenced for enrolled synthetic owners.

HandoffCoordinator binds a concrete reviewed adapter to its real factory and
cached party/PC, refuses conversion children, unknown classes and populated PCs,
then reserves full state in the unchanged guard. Its synchronous native empty
write is forced and read back before SEALED. Return refuses changed native bytes,
commits the exact original generation, writes and checks that generation, and
LEAVES THE FENCE CLOSED. This is staged native storage handoff, not player release.

The old native store objects are revoked, not reused or published. Preexisting
nested Java aliases (such as a CompoundTag) are NOT immutable: tests deliberately
mutate one. Its changes cannot be saved or used as the managed returned snapshot
through the audited entry points. This is not a hostile-code sandbox. Arbitrary
reflection, raw filesystem writes, direct third-party providers, saved BattlePokemon
objects, fabricated new UUIDs, packets and all gameplay action paths are NOT certified.

The internal codec scope reconstructs detached objects only; it does not initialize
or send them. The IO scope is bound to one adapter, owner, exact payload and current
or temporary file. It is not inherited by writer threads. These scopes are not
public bypass APIs. Complete callback containment remains integration work.

Both JSON and NBT suites use initialized synthetic file-backed stores, existing
mutable references, a real queued native writer, native .old files, native IO
failure and two abrupt JVM halts followed by independent restarts. No real player
is jailed with a nonempty party. Nonempty admission and player handback stay blocked.
