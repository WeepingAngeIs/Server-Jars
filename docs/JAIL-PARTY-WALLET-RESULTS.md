# Party and wallet investigation: verified results

Date: 2026-11-16.
Production baseline: `4e3667a149d4e530bddb5326ee739ad386fe6a47`.

## Decision

Nonempty-party and occupied-wallet admissions remain blocked. No production
classes, runtime certificate entries, admission gates or packaged JARs changed.
Only independent test fixtures, read-only preflight tooling and documentation
were added. There is no replacement production JAR from this investigation.

Exact unchanged binaries used in all final server phases:

- Minecraft-Jail-1.2.0.jar SHA-256: `d3145b1bc8f04c0370db30ff10e87158b6c3c88c0fde68cb0f226d3b73f79baf`
- Unyielding-1.2.0.jar SHA-256: `61f6add6c68364b1c9dda4486948faf8d104c45ca2db2c40199de64a04ea38f7`

Each phase records the loaded JAR identities and confirms separate ownership,
non-jail components present and old integrated jail classes absent. Production
source was compared to the exact baseline before each experiment.

## Final passing workflows

| Workflow | Exact test-source commit | Run | Result |
| --- | --- | --- | --- |
| Refusal and preservation | `8acb9cecaa254e446e3629d9d4851a8a0d667c45` | `43947043291` | Six server phases plus ten preflight unit tests passed |
| Unjailed native death/respawn reference | `d0e61fe36465c8ed3ff2264c8c26adce1319b767` | `43950904155` | Six server phases passed |

These are twelve final passing server phases across four bounded groups, not
proof of a production nonempty-resource custody implementation. Earlier failed
fixture attempts are not counted as passes. The final native reference explicitly
uses the selected Human/Warrior synthetic profile, not arbitrary origin profiles.

## Refusal and preservation

The fixtures used one native party with a shiny level-7 Eevee and level-11 Pikachu,
and separately a native equipped leather wallet with a unique marker and eleven
named gold coins. Party comparison used complete native serialized state. Wallet
comparison used the active wallet stack, native attachment and Curios route flag,
not an empty attachment alone.

An empty-resource positive control actually admitted successfully. With supported
mode opted in inside the disposable world, repeated occupied-resource admissions
were refused without mutation, custody assignment or a new transaction. Actual
disconnect and server restart preserved the resources and ordinary inventory.

The external-change experiment then introduced the same synthetic resources AFTER
an otherwise supported admission. This staging is test-only, not production escrow.
Outside lethal damage and attempted boundary death were blocked. The resources
survived another disconnect/restart while protected.

For the party case, release of the existing custody transaction succeeded, restored
its stored equipment and left the entire party untouched. Repeat release was refused.
This does not certify initial admission with a party or Pokemon transfer/restoration.

For the wallet case, release remained held with the wallet and all coins intact.
The reported reason was: `Native origin/accessory or player-save outcome is ambiguous;
explicit saved-phase recovery required`. The test did not clear an occupied slot,
override the ambiguity or replay a return. This is a passing safe-refusal assertion,
not a passing occupied-wallet restoration.

## Actual native death and respawn reference

Both resource cases used an explicitly unjailed Human/Warrior synthetic player,
initialized empty jail stores, no custody transaction and vanilla `/kill`.
Diagnostics proved actual death: health zero, alive false, deaths incremented from
zero to one, and protected custody false. Each group included a real disconnect,
restart while dead, native respawn, another disconnect and restart while alive.

The two-Pokemon party's complete serialized state remained exactly equal at every
stage. This establishes that bounded native lifecycle, not party action confinement,
escrow transfers, arbitrary storage backends or abrupt-crash consistency.

The wallet dropped as ONE identical marked wallet containing the eleven named coins.
It remained on the ground through both restarts and respawn. Native pickup moved it
into the player's inventory and removed the matching ground instance. The test
observed one matching stack across equipped wallet, inventory and nearby drops.

Wallet conservation is NOT secure jail custody or automatic restoration: a ground
wallet could be picked up or despawn in real jail use. Occupied-wallet admission
therefore remains blocked despite this conservation reference passing.

## Remaining implementation gaps

The pinned Cobblemon file-backed store queues asynchronous writes separately from
vanilla playerdata and jail journals. Safe transfer needs supported-provider identity,
durable ownership/receipts, collision checks, and fault tests across party, PC,
trading and battle changes. Normal shutdown preservation is not an atomic transfer.
An in-place design still needs proven network/action confinement and death handling.

Lightman's Currency can route wallets through Curios or its own attachment. Its
internal attachment can be empty while a wallet exists in an accessory slot. A safe
adapter needs one canonical owner across slot aliases, callback-safe transfer,
component/coin conservation, collision refusal and durable crash recovery. Bank
links, CobbleDollars balances and external economy accounts were not certified.

See `JAIL-PARTY-WALLET-RESEARCH.md` and the exact-artifact javap audits in the evidence.

## Actual-pack compatibility and old-save migration

Both are **NOT_RUN**. The accessible historical client export
`Cobblemon Unyielding (B)-1.0.8(2).zip` has 412 manifest references, zero embedded
mod JARs and no old world/playerdata. Its manifest lists NeoForge 21.1.249; the
reviewed runtime pins 21.1.255. It was inspected read-only and its SHA-256 stayed
`12b621a7475ca56d8a34fadd2612d706890d5b85167d02147ceb6ec6008a785b`.
It is not evidence of today's installed server runtime.

The historical wallet config refers to the world's `coinDropPercent` rule; the
actual old-world value is unknown without the save. No certificate or dependency
pin was broadened to accept the historical export.

The read-only ZIP preflight and copy-only test matrix are prepared. The preflight
is not a migration executor and always distinguishes metadata inspection from an
actual runtime pass. No old custody fingerprint may be edited to force recovery.

Required private copies: exact current server binaries/configs/datapacks/scripts;
a consistent pre-split world including playerdata, native Pokemon/economy storage
and jail journals; the old integrated JAR/runtime matching that backup; and a
consistent external-storage export or confirmation that no external store is used.
Do not publish player saves, credentials or live connection strings in this public
repository or its Actions artifacts. Use private copies and isolated endpoints.

No installed JARs, live servers or real worlds were changed. Only synthetic
GitHub-hosted worlds were run. Artwork remains untouched.
