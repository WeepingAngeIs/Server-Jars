# Bounded packed-wallet custody increment

Continues `9fc1f509000254a81ac3d6fc79a1dac4f51d323e`, reusing the completed
`43969084368` API audit and artifact `14276833196`. No audit rerun is required.

## Implemented contract

`native-packed-wallet-v1` uses the existing exact-slot item escrow and native
Human/Warrior origin adapter. It is not a separate balance transfer. The entire
wallet item (native data component, ordered inner slots, counts, custom names and
lore) is one item in the already durable snapshot and source-removal receipt.
The new contract and inventory policy are distinct from all old transactions;
an older Jail implementation must refuse rather than guess their recovery.

A new `jail.packed_wallet_admissions` option defaults off. It does not replace the
existing supported-admissions opt-in, runtime SHA-512 certificate, player/origin,
party, equipment, claim, menu, transaction or saved-state checks. Recovery of an
existing wallet transaction does not depend on the new-admissions option.

Admission is limited to one Lightman's Currency 2.3.0.5 leather wallet in main
inventory slots 9-35, containing ordinary physical gold coins. The wallet must
have the native six slots, zero bonus slots, auto-exchange disabled and no modified
item defaults. Only the native wallet-data component, custom name and lore may
be customized. Each nonempty inner slot must be a round-trippable gold CoinItem
with an ordinary count and only name/lore customization. The wallet and every
coin must round-trip exactly. Both hidden native wallet state and the routed
Curios/visible wallet must be empty. Bank-enabled configurations, additional
wallets, noncoin contents, unknown components and retained-XP combinations fail
before a custody record is created. No WalletDataWrapper normalization or coin
conversion is performed on player-owned data.

During custody the player-accessible stores are empty and the complete source
stack is bound to its owner, transaction and digest in the existing escrow. On
release every destination must be empty; there is no merge, drop, overwrite or
partial return. The existing phase journal verifies player save/receipt before
promoting ownership. A saved interrupted phase requires explicit verified
recovery. An unsaved/ambiguous phase stays quarantined; it is never replayed.

## Test plan and evidence boundary

The new hosted workflow builds the actual standalone Jail JAR and uses the exact
approved companion (SHA-256 `61f6add6c68364b1c9dda4486948faf8d104c45ca2db2c40199de64a04ea38f7`).
The legacy native custody fixture is adapted only inside a separate test mod:
its original source is not edited, and neither test fixture nor runtime exception
is inserted into the production JAR. A genuinely occupied wallet is seeded
BEFORE admission and included in every full-inventory snapshot/return comparison.

Core phases cover positive admission/release, negative provider states, an occupied
return slot, callback interruptions, disconnect, restart, saved return interruption,
actual jailed escape death, native respawn, explicit restart recovery and prevention
of repeated lethal damage after a cancelled death. Additional fault phases cover
saved removal before origin mutation, recovery/release with new admission off,
completed return after restart, and a hard halt after live item removal but BEFORE
player save. The last outcome must retain the saved original wallet and quarantine
without another grant or another removal. The existing integrated runtime regression
still rejects unknown dependencies and a byte-altered companion.

A plan, a successful compile, or a partial run is NOT a runtime pass. Consult the
successful exact-commit `packed-wallet-runtime.json` and raw phase evidence.

## Remaining limits

Equipped wallets, other denominations/providers, upgrades, auto-exchange, bank
links, and arbitrary attached wallet storage remain unimplemented. Nonempty
Pokemon parties remain blocked by the unchanged boundary: no party is removed,
rewritten, converted or copied into a new live store. A party-transfer adapter still
needs a proven Cobblemon persistence barrier plus complete identity/ownership and
PC/party mutation fencing; the existing asynchronous storage observations do not
provide those guarantees. These limits do not prevent proving the physical packed
wallet path with the reviewed dependencies.

Full-pack compatibility and occupied old-save migration require exact private
copies, not a historical client manifest. No private save is uploaded, no live
world is accessed, no installed JAR is modified and no non-jail source is changed.
No artwork or model settings changes are part of this increment.
