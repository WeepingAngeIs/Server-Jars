# Bounded physical-wallet custody implementation

Base report: `d1e300df17d9a5e9caa7699c6bbd418c01463504`.
Implementation branch: `codex/jail-packed-wallet-custody`.

## Selected incremental path

This is an actual escrow path for exactly one initialized Lightman's Currency
leather wallet in a non-selected ordinary main-inventory slot. Its complete native
ItemStack, WalletData, gold-coin stacks, component patch, names, lore and inert
custom data are preserved by the existing inventory journal. No denomination
conversion, reconstructed balance, native wallet setter, bank API or item-drop
restoration is used. The inventory slot is the sole live owner before admission;
the durable exact-slot journal owns the transfer while held; a receipt in the same
player save as the returned inventory proves return. Completed records cannot
regrant items, even after the player moves the wallet.

The new `native-packed-wallet-v4` and
`native-default-equipment-packed-wallet-v1` contracts do not reinterpret legacy
snapshots or silently grant broader authority to old supported-mode transactions.
`jail.packed_wallet_admissions` defaults false, as does the existing admission
switch. The console's `jailintegrated mode packed-wallet` opts into both checks;
`mode supported` or `mode disabled` turns the new option off. Switching off new
admissions must not discard existing custody or bypass restoration validation.

## Narrow envelope and unchanged gates

Only a single non-equipped leather wallet, zero retained XP, a completely empty
Pokemon party, reviewed native origins/default equipment and the original exact
runtime certificate qualify. Nested contents must be native gold coins with
inert decoration only. Functional/removed components, extra data fields, upgrades,
overflow slots, multiple wallets, selected-hand wallets, any equipped wallet,
native/Curios ownership ambiguity and configured bank/exchange/magnet features
are rejected. Full codec round-trips are checked on detached data without calling
potentially mutating wallet-wrapper accessors on the live source.

The companion SHA-512 certificate, dependencies, mixins, player death protection,
party refusal, native wallet-attachment refusal and the pre-existing exact-slot
journal are unchanged. No other Unyielding feature or production companion byte
is modified. The new adapter checks current native wallet ownership as well as
the raw attachment; it does not assume an empty attachment means no Curios wallet.

## Persistence and conflict behavior

Removal uses PREPARED / REMOVAL_STARTED / HELD with the original durable player-save
receipt. Return uses RESTORE_STARTED / COMPLETE and the original persisted return
receipt. Both still require matching runtime, identity, full snapshot and empty
restoration destinations. A conflicting destination is never cleared, merged or
dropped. Unsupported or uncertain saved phases stay quarantined without automatic
replay. An authorized explicit recovery may confirm an already durable operation;
it must not recreate resources from an unreceipted ambiguous operation. Recovery deliberately retains the custody-ledger hold even after confirming completed native returns. Final release requires the existing explicit `jailcustody review <transaction>` followed by `jailcustody restore <transaction>`; these confirm completion without another item grant. Ordinary unjail cannot skip this review.

Two additional interruption checkpoints expose already-existing return boundaries
MUTATED and PLAYER_DURABLE through the existing hosted-disposable-only fault hook.
They do nothing in ordinary production and confer no admission or recovery bypass.
The external test mod abruptly terminates its owned game JVM with exit code 86;
it is not packaged into either release JAR. A passing report requires the actual
exit code, no graceful shutdown, observed live/durable states, and subsequent
restart behavior rather than an exception-only simulation.

## Runtime evidence requirements

A code build is not approval. The `Bounded packed-wallet custody` workflow must
pass on the delivered commit and exact two binaries. It builds only Jail, checks
all original Jail unit tests plus finite-envelope tests, and compares the companion
byte-for-byte with the previously verified artifact. Certificate bytes and all
other source modules are checked against the base report. A separate fixture loads
the assembled new Jail JAR and unchanged companion with synthetic players/data.

The new scenario matrix has 23 phases: lifecycle (5), conflicting destination (4),
interrupted removal before player save (3), interrupted removal after player save
(3), interrupted return before player save (4), and interrupted return after player
save (4). It includes native escape death/respawn, real disconnects, repeated
release, moved returned items, and explicit saved-phase recovery. Four existing
split-runtime regression slices are required as well, including byte-altered
companion and unapproved-dependency refusal. A final artifact is published only if
all groups and exact binary identities pass. Consult the run's machine-readable
report for actual results; this design document alone is not evidence of passing.

## Still blocked and next design boundary

Nonempty Pokemon parties are not enabled. Their separate asynchronous persistence
needs a verifiable durable completion barrier and authoritative per-Pokemon
ownership/identity checks spanning party and PC stores. Transferring serialized
Pokemon and trusting a vanilla player save would not be a safe implementation.
An in-place party restriction alternative must first cover send-out, riding,
trading, battles, PC access and mutation packets. Those are separate work, not a
reason to delay the finite physical-wallet path.

Equipped wallets, upgraded/bank-linked wallets, other currencies and nonzero XP
with this contract also remain blocked. Adding them requires their own verified
adapter and saved-state ownership rather than broadening an item whitelist.

Full current-modpack and occupied pre-split save migration remain unverified until
exact private copies are provided. The existing read-only private-copy planner
and matrix remain unchanged. No real save, credentials or player data are uploaded
to public GitHub; all runtime fixtures are synthetic, hosted and loopback-only.
No installed JARs, live worlds, artwork or model settings are changed.
