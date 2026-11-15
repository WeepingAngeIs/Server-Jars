# Experimental vanilla custody slice

Production jail admissions remain disabled. This is not complete custody, an
accessible confiscation chest, an accessory transfer, an origin swap, or a refund.
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
