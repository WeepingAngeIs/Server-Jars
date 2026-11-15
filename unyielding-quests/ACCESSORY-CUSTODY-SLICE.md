# Controlled accessory custody slice

Production jail admissions remain disabled. This is a controlled-provider API
experiment in a separate test mod, not generic support for the user's items.
The mutation adapter refuses unless running on GitHub-hosted CI in the exact
`jail_accessory_smoke` world with its explicit test property. It is excluded from
the component/release JAR. The shipped code contains the pure canonical slot
policy and durable transaction coordinator, without a production accessory port.

Exact integrations: Accessories 1.1.0-beta.53+1.21.1, Accessories compatibility
layer 0.1.12 and Curios 9.5.1+1.21.1. CI fetches their published version IDs and
checks SHA512 hashes. Curios aliases are verified against the exact backing
container identity; only canonical Accessories slots supply items. Both regular
and cosmetic slots are captured once, with original names/indices, all item
components, vanilla armor/offhand and the complete serialized accessory holder.

The adapter requires audited synchronous fixture providers, single accessories,
no slot modifiers or pending lifecycle/invalid-item queues at removal, and no
binding effect on accessories or vanilla armor/offhand. Unknown providers are
rejected by a data-only preflight before saving or recording a transfer. No
production provider whitelist is implied by the fixture whitelist.

Removal intent is forced before calling actual `AccessoriesInternalSlot.mayPickup`.
Regular slots invoke actual canUnequip integration; cosmetics follow the mod's
own cosmetic UI semantics (no regular canUnequip call). Binding is conservatively
refused even for cosmetics. Restoration stages and checks all original slots
without callbacks, persists RESTORE_STARTED, then uses actual `mayPlace` semantics
before insertion. It never substitutes its own TRUE callback or bypasses denial.
It checks complete inventory/layout and backing identity after each permission
call and each physical slot change. Partial/changed outcomes remain unresolved.

The genuine `AccessoriesEventHandler.onLivingEntityTick` consumes native previous
stack state and invokes normal equip/unequip lifecycle callbacks. The adapter
does not edit previous stacks or call callbacks in place of the mod. It verifies
settled normal/cosmetic slots after this handler and refuses unexpected inventory,
layout or holder changes. Uncertain transfers are never replayed. Receipts share
the player save with the Accessories attachment; the player file and directory
are forced and the serialized holder plus vanilla inventory read back before HELD
or COMPLETE. Journal records retain all original data, including after completion.
Changed/full/missing slots, changed dependency/policy metadata and unknown stack
formats retain escrow. No fallback insertion, dropping, overwriting or snapshot
deletion is implemented. Completed records never recreate consumed accessories.

## Exact blocker to generic production support

The installed `AccessoriesAPI.canUnequip` and `canEquip` execute global callbacks
and provider methods on the live player. These APIs have no mutation-free dry-run,
callback-effect declaration, transaction token, cancellation/undo for arbitrary
effects or idempotent replay contract. Native lifecycle callbacks run in the tick
handler, which also updates slot containers and distributes `invalidStacks` to
players/world. A callback can schedule future changes or give/drop items outside
the observed stores. Checking the inventory afterward detects some changes but
does not undo world effects or make that callback safe to replay.

Therefore this slice supports only explicitly controlled test providers and does
not claim arbitrary third-party items are safe. Tests intentionally exercise
immediate inventory mutations and safe growth of slot layouts, preserve their
observed state and stop the transfer; they do not certify destructive/shrinking
callbacks or asynchronous third-party behavior. A generic production path needs
audited provider adapters and a connected quarantine/interception policy that
covers lifecycle, network, queued effects and item/world side effects. Origins,
global custody locks, protected chest access and production admission remain
unfinished. Physical power-loss safety and cross-store atomicity are not claimed.

## Verification

The focused CI job assembles the release, loads production classes only from that
JAR in a separate harness with the actual three integration mods, and connects two
clients. It tests normal/cosmetic custom components and nested contents, alias
deduplication, binding/unknown-provider refusal before permission hooks, denied
permissions, inventory/slot-growth callback side effects, native lifecycle effects,
partial removal/insertion, occupied/missing slots, changed dependency metadata,
pre/post-save failure, all transaction checkpoints and repeat requests. No source
fixture item may be dropped. A real disconnect leaves HELD and refuses offline
restoration; restart reconnects and restores. An independently saved interrupted
restore requires explicit item-free confirmation. Another restart verifies both
completed records and that consumed accessories cannot be granted again.

The standard exact-commit final-JAR regressions still cover vanilla restoration,
capture, jail terrain/layout/protection, advancement and startup/restart. These are
ordinary restarts and deterministic interruptions, not real power-loss tests.
