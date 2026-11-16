# Bounded origin custody prototype

Production jail admission remains disabled. This is not an installed feature or
an assurance that arbitrary installed origin powers can safely be removed.

`JailOriginTransaction` durably links an origin intent to the existing held-item
transaction UUID, owner and SHA-256 snapshot digest. Its durable phases precede
native mutation. An interrupted phase is inspected, never automatically replayed.
Only an explicit confirmation of an already saved, matching target state can
finish a phase without invoking native callbacks again. Item return has a second
origin/claims gate immediately before insertion. The source snapshots remain.

The native adapter is a separate CI-only test mod. Its environment and world
guards prohibit use outside GitHub-hosted `jail_origin_smoke`. The assembled
release contains the pure coordinator, not this mutation adapter or fixtures.

The runtime installer pins NeoOrigins 2.2.27 and the three installed origin-addon
binary SHA-512 values. It extends actual canonical main/class layers with two
audited origin fixtures and an unrelated powerless class fixture, preserving
the native and addon choices. Human is resolved from its loaded translation
identity and checked for availability, not assumed from an arbitrary ID.

Only tier zero, the exact native Luck attribute modifier fixture, and unrelated
powerless layers are supported. The real native `applyOriginPowers` lifecycle,
events and sync run. Power JSON and exact implementation are audited before
mutation. KubeJS, unknown powers/hooks, higher tiers, dynamic/global powers,
pending pickers and any active cooldown map refuse. This version's origin codec
does not persist its active cooldown map; the adapter must not pretend to restore
it. Actual powered addon classes remain refused, not silently stripped.

Only the selected main-layer map entry is changed. No attachment replacement or
snapshot-wide progress restore occurs. Restoration plans from the latest saved
progress. Native attributes, health, other layers, all claim reservations,
inventory and accessory container state are compared across callbacks. Unexpected
effects are retained for review, never silently rolled back.

The original unique reservation remains owned throughout Human custody. A free
Human claim is reserved after a same-thread ownership check and retained across
disconnect. Restoration releases only a Human reservation created by this
transaction and still owned by that player. A preexisting self-owned Human claim
is retained. Conflicts never authorize overwriting another owner's claim.

Player and claims saves are separate stores. NeoForge queues the actual
SavedData write; the adapter waits on its native IO-worker completion barrier
after scheduling the claims save. Both native saves are then forced and
read back, including parent directories, before a verified phase is recorded.
This is not cross-store atomicity. Failures between saves retain an incomplete
phase and block item return. The fixtures inject failures at both journal and
native-save boundaries and test actual restart recovery. CI results, not the
presence of these fixtures, establish what has passed.

## Contract needed before accessory adapters can graduate

An adapter must pin provider binaries and the complete global event/callback set;
enumerate synchronous, queued, world and external effects; and prove a bounded
native lifecycle that settles before verification. A provider's dry-run method
alone cannot certify arbitrary external callbacks. Unsupported effects refuse.

It must identify canonical physical backing stores and compatibility aliases,
normal/cosmetic/nested slots, binding and permission semantics, dynamic sizing,
and invalid-stack queues. It must use native removal/equip callbacks and read back
all owned effects, without editing previous-stack bookkeeping to suppress them.

It must expose exact snapshots, current/native persisted readback, deterministic
restore plans and capacity refusal, durable mutation receipts and a complete
failure/restart matrix. The admission/release coordinator must fence concurrent
inventory, origin, network, command, orb and UI mutations and quarantine an
uncertain player. A display chest cannot become a second authoritative copy.

Those production fences, the protected chest UI, integrated admission/release,
and escape punishment remain unfinished. No universal accessory enablement is
claimed, and admissions must stay disabled until the entire supported path is
implemented and verified against the same final JAR.

The concrete port boundary is `JailVanillaCustody.InventoryPort`: `validateRemoval`
must reject unsupported providers without calling them, `current` and `durable`
must encode identical canonical physical channels and receipts, `planRestore`
must be data-only and capacity-complete, `clearAndMark`/`restoreExact` must execute
the audited native lifecycle once, and `persistAndVerify` must force/read back
all stores and their effects. An audit descriptor must bind the binary hashes,
registered global callback identities, native slot/backing coverage and effect
scope to the snapshot version; any descriptor change during custody refuses.
Native `AccessoriesInternalSlot.mayPickup/mayPlace/remove/set` and the genuine
Accessories tick settlement are part of that descriptor, including the distinct
cosmetic semantics and Curios compatibility aliases. The existing controlled
fixture adapter demonstrates these calls, but does not certify third-party
providers, unbounded callbacks or the missing production quarantine.
