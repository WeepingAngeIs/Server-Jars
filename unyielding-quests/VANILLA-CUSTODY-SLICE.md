# Experimental vanilla custody slice

Production jail admissions remain disabled. This is not complete custody, an
accessible confiscation chest, an accessory transfer, or an origin swap.
The only Minecraft inventory mutation adapter is a devtest fixture excluded from
the component JAR and copied into a separate disposable CI test mod. The shipped
transaction class has no command/event registration or production adapter.

`JailVanillaCustody` first saves/verifies the baseline player inventory, appends a
PREPARED record with transaction UUID, owner UUID and full serialized inventory,
then persists REMOVAL_STARTED before calling the inventory mutation. Only after
player save, file force, directory force and exact readback of inventory plus the
same transaction marker does it append HELD. Snapshots preserve vanilla source
Slot tags, stack components and nested item data without traversing nested items
as a second inventory. Journal snapshots cannot be opened or withdrawn as items.
The fixture encodes standard NBT with compound keys sorted, retaining all types,
values and list order; arbitrary hash-map iteration order is not inventory state.
Every encoding is decoded and compared with the entire original tag, so this
normalization cannot silently omit a component or change its value.

The fixture uses vanilla `Inventory.save`, including armor/offhand. It refuses a
non-player menu, cursor stack and crafting/result slots. It runs synchronously
on the server thread, entirely within one callback, in a separate named world on
a GitHub-hosted runner only. Arbitrary unequip callbacks and other mods' stores
are not supported by this slice. It is not safe to use this fixture for real players.

Reconciliation is read-only. A PREPARED record with matching live and persisted
original inventory is distinguishable from an unchanged interrupted removal.
Matching empty inventories plus the exact transaction marker distinguish a
persisted removal. An interrupted removal still requires review even when both
copies match. Mismatches, foreign markers and later changes require review.
Nothing replays a removal, promotes an interrupted phase, or grants a snapshot.
Repeated transaction IDs and unresolved transactions for the same owner are
rejected before any save or player mutation.

Durability is not inferred from `setDirty` or the return of PlayerList.save (which
can swallow write errors). The fixture forces the replaced player file and its
directory, then reads/validates the actual owner/inventory/marker. Journal
initialization also forces file, new directory and parent directory metadata;
unsupported directory forcing fails closed. Appends force the existing journal
file. These guarantees depend on the OS/filesystem honoring force; CI interruption
checks and actual server restart do not prove physical power-loss durability or
atomicity across player and journal stores. Ambiguous states deliberately remain
unresolved, preventing a second accessible copy.

Tests inject failures before/after intent persistence, before/after inventory
mutation, after player persistence and after HELD; reopen the journal at every
boundary; verify duplicate/second-request refusal and mismatched state handling.
The independent final-JAR harness tests real player saves, custom names/data,
damage, armor/offhand and a nested shulker stack. A second server process reloads
both a HELD player and a player interrupted before removal. Existing partial-frame
and checksum corruption tests remain in place. No installation/profile/world
changes are part of this slice.

## Controlled vanilla restoration

The experimental transaction now supports HELD -> RESTORE_STARTED -> COMPLETE.
Before changing that phase, restoration requires an online player, exact matching
live/saved HELD marker, and a wholly empty vanilla inventory. It decodes the entire
snapshot into a separate staging inventory, checks its supported shape/slot count,
round-trips every component and original source slot, and refuses stack overflow.
This conservative slice does not merge items or choose alternate slots. Any
occupied slot (including one originally empty), a full inventory or an unknown
format leaves HELD and the exact snapshot unchanged. Nothing overwrites existing
items, drops overflow or deletes custody records.

After the durable RESTORE_STARTED append, the destination is checked again. The
fixture inserts staged copies into original slots and replaces the held marker
with a transaction-specific restored receipt in the same player save. COMPLETE
requires exact live/player-file comparison, forced player file and directory,
then a forced journal append. The COMPLETE record and snapshot remain retained;
repeated release returns ALREADY_COMPLETE without inspecting or replacing any
subsequently consumed, moved or lost item. Receipts are retained; clearing/rotating
them for future admission is outside this slice.

RESTORE_STARTED never authorizes another grant, even when a restart shows the
empty pre-restore inventory. Matching complete inventory plus its saved restored
receipt is still review-only until explicit `confirmRestoredForDisposableTest`.
That separate operation only appends COMPLETE, never changes inventory or saves
the player. Partial restores, mismatched slots/receipts, failed saves and missing
or corrupt history refuse both automatic transfer and completion. Original
snapshots stay available for future explicit administrative reconciliation; no
general partial-inventory repair or offline-file editing is implemented.

Pure fault tests reopen journals and reload persisted player copies at every
restoration boundary. They cover pre/post-save failures, partial setters, changed
destinations, occupied/full inventories, offline players, overflow, invalid plans,
missing/corrupt history and repeat releases. Independent assembled-JAR tests run
the real adapter with connected players in a separate `jail_restore_smoke` world.
They exercise all checkpoints and real component/armor/offhand/nested-container
restoration, then actually restart with one pre-mutation interrupted player and
one post-save interrupted player. A third server process verifies COMPLETE
survives another restart and consumed items cannot be granted twice. These are
controlled interruption fixtures and ordinary restarts, not physical power-loss
tests or proof of cross-store atomicity. The adapter still exists only in the
separate test mod; full accessory/origin workflow, production quarantine/locks,
chest access and production admission remain unfinished and disabled.
