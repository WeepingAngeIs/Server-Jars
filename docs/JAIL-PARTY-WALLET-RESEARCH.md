# Party and wallet custody investigation

Baseline production commit: `4e3667a149d4e530bddb5326ee739ad386fe6a47`.
This investigation adds independent fixtures and read-only tooling. It does not
change the production JARs, certificates, admission policy, or saved custody data.
Nonempty parties and occupied/unsupported wallets remain unsupported admissions.
A successful preservation probe must never be described as an enabled custody adapter.

## Exact pinned API findings

The hosted fixtures disassemble the exact downloaded runtime artifacts with `javap
-p -c`; the evidence includes each artifact's SHA-512. These are not findings about
an arbitrary upstream version or the user's entire current pack.

### Pokemon

`PokemonStoreManager.getParty(player)` resolves player identity through registered
storage factories. Party contents do not live solely in vanilla playerdata. The
pinned `FileBackedPokemonStoreFactory.save` serializes a store, removes it from its
dirty set, and submits work to a separate save executor. Returning from that call
is not proof that bytes are durable. Disconnect and shutdown have their own store
save handling. The existing jail playerdata flush cannot establish an atomic
transaction spanning this separate store and jail journals.

Before a custody adapter may admit nonempty parties, it needs an explicitly
identified supported storage backend, complete roundtrip-verified native state
(including UUIDs, order, held items and mod data), durable transaction ownership,
and verified source/destination receipts. Removing and later recreating Pokemon
from a snapshot is not sufficient: that can conflict with PC storage, trades,
battles, a second save backend or a partially committed asynchronous write.
Admission must refuse in-flight actions, unknown providers, duplicate UUIDs,
uncertain writes, or changed state. Recovery must not overwrite an occupied party,
create replacement UUIDs, duplicate a party into the PC, or replay uncertain grants.
The existing nonempty-party refusal remains intact.

An in-place preservation design could avoid custody transfer, but would still need
tested confinement of send-out, riding, trades, PC access, battle and mutation
packets plus a proven death/clone lifecycle. Blocking commands alone is not that
proof. No such broader certification is claimed by the current experiments.

### Wallets

The pinned Lightman's Currency `WalletHandler.getWallet` can route through Curios.
`setWallet` uses the Curios slot when available, otherwise its own attachment.
Its serialized attachment stores the internal wallet field, which can be empty
while an occupied wallet exists in Curios. Tick processing may migrate an internal
wallet into the accessory slot. Therefore the tests observe the active wallet via
the native API as well as the attachment and route flag; an empty attachment is
never treated as proof that the wallet or its nested coins were preserved.

A future adapter needs canonical ownership of one physical wallet slot across
Accessories/Curios aliases, callback-safe removal and restoration, exact components
and nested contents, and durable per-stage receipts. Native death drop/clone policy
must be exercised under the actual configuration. A returned wallet cannot be
recreated merely because an attachment is empty. Occupied return slots, changed
wallet components or ambiguous duplicate copies must stop recovery.

Wallet bank access and external account balances are a separate problem from a
physical wallet's contained coins. This investigation does not debit, escrow,
refund or certify CobbleDollars balances, bank accounts, or other economy stores.

## Implemented experiments

`.github/party-wallet/` tests refused admissions, nonmutation, real disconnect and
restart, then controlled resource introduction into an already supported synthetic
custody transaction. It checks unsafe death is blocked and observes release without
transferring the external resource. Fixture-only staging uses newly generated test
objects, never user Pokemon or items. It is not a production escrow implementation.

`.github/resource-native/` uses a separate synthetic, explicitly unjailed player to
observe actual native death, dead restart, native respawn and live restart. The
vanilla kill command is a lifecycle trigger, not a jail bypass. Wallet observations
count the exact native stack across equipment, inventory and ground after respawn;
a dead source object is not automatically considered an independently redeemable
copy. Pokemon comparisons include the full serialized party, not just species or
counts. These reference tests do not prove jail escrow, crash consistency or
confinement of Pokemon actions.

Both experiment workflows verify no production-source difference from the baseline
and download the exact immutable pair from run `43928823842`. Each is confined to
GitHub-hosted runners and named disposable worlds. Original launch fixtures and
runtime guards remain untouched in production.

## Actual-pack and old-save test preparation

`tools/jail-sandbox/preflight.py` inventories ZIP copies read-only, hashes inputs
before and after inspection, rejects unsafe archive paths/links/collisions, inventories
exact mod binaries, and distinguishes a manifest from an installed runtime. It always
reports runtime/migration as NOT_RUN and never launches a server or authorizes migration.
Ten unit tests cover archive safety and refusal of incomplete evidence.

Required private inputs are an exact server-pack copy (mod binaries, loader version,
configs/defaultconfigs, scripts and datapacks), the exact old integrated Unyielding
JAR/runtime, and a consistent pre-split world backup including playerdata, data,
serverconfig, jail journals/custody stores and Pokemon/economy storage. External
storage must be snapshotted consistently or confirmed absent. No actual save,
credentials, server addresses or private player data may be committed to this public
repository or uploaded as its public Actions artifacts.

Copy-only test matrix, not executed on a user's save yet:

1. Preserve immutable input hashes; run the old runtime on a separate working copy
   to establish counts, identities, inventory, wallet and pending-transaction baseline.
2. Run the split pair on another copy with no occupied jail, then restart and compare
   unrelated quest/reward/Pokemon/economy state. Never silently remove unknown mods or
   rewrite certificate pins to make the copy boot.
3. With occupied pre-split custody, require changed runtime fingerprints or unresolved
   receipts to remain quarantined. Do not edit old fingerprints or clear journals.
4. Exercise admission/release/death/disconnect and each proved transaction handoff,
   including abrupt-process failure once a supported adapter exists; verify UUID,
   item-component and wallet conservation and zero duplicate grants.
5. Test occupied destinations, concurrent native changes, missing providers and
   corrupted/missing receipts. Each must remain blocked without overwriting state.

The historical October client export available during this investigation contains
manifest references rather than exact mod binaries and no old world. It is not a
substitute for the requested current server pack or migration backup. A loader
mismatch is evidence to report, not permission to broaden the certificate.

Artwork is outside this coding/testing investigation.
