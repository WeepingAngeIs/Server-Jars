# Protected custody interface and quarantine slice

Production admissions remain disabled. Nothing in this slice enables arbitrary
origin/accessory providers or installs anything into a profile or live world.

The barrel-shaped custody vault is a non-container block: no block entity, item
handler, item drop or physical inventory. Hoppers have nothing to extract or
insert. Break/place and explosion handlers protect the block; its piston reaction
is BLOCK. An administrator's read-only six-row chest screen pages over up to
2,048 recorded stacks. Type/name/count/source-slot previews are derived from the
immutable journal snapshot. Unique components and nested inventories remain only
in the original authoritative snapshot, not in a second withdrawable inventory.
All normal click, drag, hotbar, shift, throw, clone and double-click paths are
denied and resynchronized, including clicks in the player's own inventory.
The underlying display container and every menu slot also reject standard
insertion/removal APIs and return detached item values; internal painting is a
private path. The view is not a `SimpleContainer` that another inventory handler
can freely withdraw from.

The screen is bound server-side to transaction, owner, revision and physical
anchor. Permission API and actual Open Parties and Claims permission, range,
player state, loaded chunk and block identity are rechecked on clicks and ticks.
A stale screen cannot select another prisoner or become valid again merely by
restoring permissions. Missing blocks/chunks close screens and quarantine the
record without deleting or changing escrow. Reattachment changes the metadata
revision; old interfaces/screens do not gain access to the new selection.

## Single authority and durable recovery

Existing item/origin journals remain authoritative. `JailCustodyLedger` stores
only pointers, transaction/owner, source digest, anchor, revision, state and actor/
reason audit history. Its append-only hash chain is forced before mutations.
File identity, full checksum history and expected revision are verified before
recovery. Missing, replaced, truncated or corrupt history is refused, never
silently initialized. Source snapshots and terminal records are retained.
Paths are constrained to existing, non-symlinked journals inside this world.

Every imported record begins QUARANTINED. REVIEWED means a specific audited
adapter's current proof passed; the prisoner remains quarantined until a verified
return completes. A durable quarantine event precedes the adapter call. Origin
and inventory operations go through the existing coordinators and their native
persistence barriers. No UI/command inserts captured stacks directly.

An interrupted native phase stays ambiguous. `confirm` is an explicit phase-only
coordinator confirmation of an exact already saved target, not a replay or a
manual item grant. A crash after native completion but before the interface
ledger's completion requires review, then records the existing completion
without calling restore again. Repeated returns never replenish consumed items.
Separate stores are not represented as an atomic transaction.

The production adapter set is intentionally empty. Unsupported origin/accessory
states remain quarantined, with a recorded reason. The only concrete adapter in
this slice is in a separate GitHub-only disposable test mod, using the actual
pinned native origin adapter plus the verified guarded vanilla return path.
The production JAR excludes that adapter and every smoke fixture.

Quarantine fences standard inventory/creative/drop/use/interact/recipe/hotbar
packets on the server thread, pickup, item use, container opening and existing
jail interaction/command paths. Legacy unjail/pardon refuses outstanding or
unavailable custody; legacy assignments are not migrated away or discarded.
Console recovery remains available. These fences do not certify arbitrary mods'
direct inventory, asynchronous callback or death/drop behavior.

## Administrator controls

All controls require the existing jail-management permission. `init` and `bind`
also require an entity-free server command source. There is no player bypass for
their own quarantine.

* `/jailcustody list [page]` lists retained transaction IDs, owners and reasons.
* `/jailcustody inspect <transaction>` verifies source identity and reports state.
* `/jailcustody audit <transaction>` reports recent actor/reason history.
* `/jailcustody review <transaction>` checks an audited adapter; it cannot force
  an unsupported or ambiguous result to READY.
* `/jailcustody confirm <transaction>` explicitly confirms an exact saved phase.
* `/jailcustody restore <transaction>` requires review and verified native return.
* `/jailcustody hold <transaction>` records an explicit quarantine hold.
* `/jailcustody reattach <transaction> <x> <y> <z>` rebinds a lost interface to
  empty loaded space with claim access, retaining the original escrow pointers.
* `/jailcustody init` explicitly provisions a new metadata directory; existing
  directories, including missing-file/corrupt cases, must be recovered, not reset.
* `/jailcustody bind <transaction> <owner> "<items-relative-directory>"
  "<origins-relative-directory>" <x> <y> <z>` indexes existing histories and
  quarantines the owner before placing the interface. It creates no item journal.

## Verification and remaining integration

The focused GitHub workflow exercises actual block use and menu packets,
permission and claim changes while open, all click modes, cross-prisoner stale
screens, explosion/break protection, real hopper ticks, pickup/creative fences,
unsupported adapters, missing block/chunk retention, native saved-phase
confirmation, legacy assignment preservation, interrupted completion, repeated
requests and two real restarts against the final assembled JAR. Pure tests cover
ledger durability, immutable identity, stale revisions, corruption, truncation,
missing history, independent owners and retained audit. Tests are not a claim of
success until their GitHub runs pass. The main build repeats these phases after
the existing startup, dependency, jail, terrain, vanilla, accessory and origin
checks against its same unchanged assembled JAR.

Still required before production admission/release: audited production adapters,
one integrated coordinator owning all admission/origin/item/quarantine phases,
all relevant provider/network/UI/automation/death fences, restart reconciliation
and recovery authority, and a verified whole-path test matrix. Safe escape-death
must distinguish actual supported custody from legacy/ambiguous records, retain
items through death/respawn without duplication, and preserve console recovery.
This slice neither adds escape-death punishment nor marks the full jail complete.

## Focused native evidence

Commit `6d5b3cf8c6dafe70c0d435855d4d327a07025843` passed the component build,
policy tests, assembled dependency checks and all three native interface phases
in [GitHub run 43798304231](https://github.com/WeepingAngeIs/Server-Jars/actions/runs/43798304231),
job `133072160166`. Its independently assembled JAR SHA-256 was
`52c02df041b90e3bbcb46d825a959bc109a138a8be8cb84df5ca7277b8cf650a`.
This evidence does not replace the main workflow's same-JAR regression sequence.

The sealed display/slot APIs additionally passed all phases at commit
`e9f79fd3d4bdba7d7f3a0b3e97368ee8c9c2ec4b` in
[run 43800692226](https://github.com/WeepingAngeIs/Server-Jars/actions/runs/43800692226),
job `133079981220`, including direct standard extraction/insertion calls and
detached preview values. That run's assembled SHA-256 was
`045bac979045ce9fd72df940c9b895afaf22d7507537fba85fda4278c30a30c2`.
