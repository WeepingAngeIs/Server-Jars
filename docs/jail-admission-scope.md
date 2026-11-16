# Opt-in jail admission scope

Version stays 1.2.0. This document describes implementation scope, not a passing test result. Exact commits, run results and artifact hashes must accompany any validation claim. No installation or live setting change is authorized by the gate.

## Gate and operation

`jail.certified_admissions` defaults to `false`. Console `/jailintegrated mode supported` saves an explicit opt-in after existing custody and integrated history have been initialized. Console `/jailintegrated mode disabled` disables new admissions without disabling protection or recovery of existing cases. OP `/jailintegrated check <player>` diagnoses compatibility without moving items, invoking origin callbacks or saving player data. Ordinary `/jail <player>` and `/unjail <player>` then use the durable coordinator.

Initialize a new jail history only with `/jailcustody init` and `/jailintegrated init`; these refuse a missing-history reset after initialization. Do not delete histories or switch contracts to work around a refusal. Save the full native player/world data and transaction journals together when making administrative backups.

The loaded third-party binary set must match `assets/unyieldingquests/jail-certified-runtime.json`, including embedded-library proofs. This certificate covers the disposable test runtime, not the user's approximately 387-mod profile. Unknown binaries remain a concrete production blocker. A switch cannot bypass it.

## Native origin/class adapter

The concrete added native combination is tier-zero `neoorigins:human` in `neoorigins:origin` plus `neoorigins:class_warrior` in `neoorigins:class`. Warrior is temporarily replaced by native unpowered `neoorigins:class_nitwit`; Human remains selected. The actual SDK performs grant/revoke/sync, and exact modifier and callback outcomes must verify before a receipt is issued.

Warrior declares attack damage +1, knockback resistance +0.3, armor +2, maximum health +2 and Weakness immunity. Each actual registered implementation and raw definition is checked. Only unconditional positive additive modifiers for the explicitly enumerated vanilla attributes, and the exact no-state Weakness immunity implementation, are accepted. Duplicate attributes, arbitrary implementations/keys, conditions and other operations are refused. The adapter also accepts unpowered selections and one powered main layer matching this narrow policy; these are not claims of native testing for every possible datapack definition.

All unrelated selected layers must be unpowered. A powered class requires native Human in the main layer. All layer selections, current progress and unique claims are preserved. The original claim is retained; the temporary Human/Nitwit claim must be free or already owned by the same player. A foreign owner causes refusal before source capture. Higher tiers, dynamic/global powers, pending selection and unpersisted cooldowns are refused.

Health uses conserved missing-health points: Warrior 22/22 becomes 20/20, and 20/22 becomes 18/20. Returning adds only the removed maximum-health bonus, retaining damage/healing during custody. If the subtraction would be lethal, admission refuses. Native SDK health changes are separately predicted and checked before the declared normalization; arbitrary callback differences quarantine the case.

## Actual refused addon cases

Read-only inspection found 105 definitions: 78 NeoOrigins, 8 Classes Ex, 10 Fantasy and 9 Furries. Runtime folded layers and loaded definitions remain authoritative. These actual examples explain why a blanket addon adapter would be unsafe:

| Definition | Actual power/configuration | Reason for refusal |
| --- | --- | --- |
| Classes Ex Artificer | `starting_gear_art`: `starting_equipment`, 24 leather | Regrant and durable grant-marker lifecycle need a dedicated item-safe adapter. |
| Classes Ex Crusader | `kill_count`: `variable` | Resource/progress callbacks are outside the audited modifier policy. |
| Classes Ex Duskblade | `dusk_attack_speed`: conditional nighttime `add_multiplied_total` | Conditional ticking and multiplied attributes are not declared reversible effects. |
| Fantasy Fae | `fae_sized`: `size_scaling`, scale 0.67 | Body geometry/reach lifecycle needs its own adapter. |
| Fantasy Lich | `undead_body`: `entity_group`, undead | Damage/healing identity hooks are outside the audited policy. |
| Fantasy Ogre | `rage_counter`: resource regeneration with a Strength max-action | Mutable resources and triggered actions cannot be replayed safely. |
| Furries Otter | `secret_pocket`: `extra_inventory`, size 9 | Additional item storage needs authoritative custody and death/respawn coverage. |
| Furries Sheep | `starting_wool`: `starting_equipment`, 3 wool | Regrant and grant-marker lifecycle are not certified. |

These are examples of real blockers, not permission to strip those powers. A rejected player remains unchanged. The base native Warrior/Nitwit case does not certify the addon origins.

## Equipment, packed coins and death boundary

Vanilla hand, armor and offhand use native equipment callbacks with declared-effect verification. Accessories supports only the actual default provider in physical and cosmetic channels; Curios compatibility aliases are deduplicated against those backing containers. Slots, selected hotbar, count, components, nested contents, permissions, layout and settled previous-stack state are retained. Custom Curios/Accessories providers, bound equipment, accessory effect components and slot modifiers remain refused.

Packed coin support is restricted to `lightmanscurrency:coin_gold` with exact class `io.github.lightman314.lightmanscurrency.common.items.CoinItem`, in a nonselected inventory slot 0–35, with vanilla components and no serialized attachments. Pinned Lightman's Currency 2.3.0.5 `ModItems` registers that ID through bootstrap 2 / `lambda$static$2`, which constructs `CoinItem`. `coin_copper` and `coin_iron` instead construct plain `Item`; the initial fixture's Copper/CoinItem assertion was wrong. The lookup now reports actual ID/class on failure. Other coin IDs are not inferred compatible. Selected coins, equipped wallets and arbitrary currency providers remain refused.

The player must be alive and connected with settled native stores, a nonempty escrow source, default survival abilities, default base attributes apart from declared equipment/origin modifiers, zero XP, no active potion/absorption, no active origin resource state, no occupied wallet, no unknown death attachments and an empty Cobblemon party. Deposit party members in a PC first; the command never moves them. Empty inventories currently refuse because the underlying escrow contract requires a nonempty source. These restrictions are deliberate limitations, not implemented XP, wallet or Pokémon custody.

Supported custody cancels outside damage and direct death attempts; only the trusted escape damage scope may request ordinary lethal damage. Gameplay custom payloads are fenced on the server thread during custody, with branding exempt. Switching the admission mode off does not remove those protections. Death is acknowledged only with native death-stat advancement as well as the dead/zero-health state. Escape cancellation/uncertainty stays held; no repeat damage, invented respawn or refund occurs. Real native respawn must save the alive replacement at the safe jail with exact empty stores and native origin/claim receipts before recovery can complete.

## Verification requirements

The certified five-phase hosted fixture exercises actual commands, native Warrior/Nitwit, both accessory channels, packed gold components, disabled mode, selected-coin and lethal-health refusals, foreign unique claims, progress preservation, outside damage/direct death, blocked/allowed gameplay payloads, callback faults, offline refusal, four process restarts, interrupted release, actual escape death, native client respawn, interrupted respawn save and cancelled-death no-replay. A successful focused run must be followed by all older regression suites against the same assembled final JAR. Test-only fixture classes must remain absent from that JAR. Failed or partial runs do not establish usable runtime support.
