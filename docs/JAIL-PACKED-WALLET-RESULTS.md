# Bounded occupied packed-wallet custody: completed results

## Checkpoint and outcome

Continues `9fc1f509000254a81ac3d6fc79a1dac4f51d323e`, after the completed report at `d1e300df17d9a5e9caa7699c6bbd418c01463504`. The API audit from run `43969084368`, artifact `14276833196`, was reused, not rerun.

Actual occupied-wallet custody is implemented, not merely preservation probing. Implementation commit: `372d8fa8812309550ffc63f388476d60b20de3a9`. Exact tested commit: `8e9716d94a932fe5110ae84abdc90adfe0017974`. GitHub Actions run `43991171187`, `Implement and verify bounded packed-wallet custody`, completed successfully, including the final exact-commit/JAR-identity gate.

This is a runtime-tested development candidate for a bounded reviewed setup. It is not approval for an arbitrary modpack, occupied old-save migration or live deployment. The new admission option defaults off. Nonempty Pokemon parties and equipped wallets remain blocked.

## Concrete implementation

The `native-packed-wallet-v1` contract and its distinct inventory policy use the existing complete exact-slot inventory escrow, source-removal receipts and phase journal. There is no coin-balance transfer, denomination conversion, wrapper normalization, merge, drop or partial return. The full serialized wallet, native inner-slot order, stack counts, names and lore remain bound to the owner, transaction and digest. Return requires the original destination state to be empty; conflicts refuse rather than overwrite.

Admission is limited to one Lightman's Currency 2.3.0.5 leather wallet in main inventory slots 9-35, with six native inner slots, zero bonus slots, auto-exchange disabled, no bank access, ordinary physical gold CoinItem contents and exact serialization round-trips. Only native wallet data and optional names/lore may differ from item defaults. Unknown components, attachments, modified defaults, extra slots and other contents are rejected.

The native attachment, hidden backing/cache wallet and routed active/visible/Curios wallet must all be empty. The new implementation does not unequip or re-equip anything. The positive native profile tested is the existing Human/Warrior profile, not arbitrary non-Human origins. Retained-XP-plus-wallet combinations are not approved.

`jail.packed_wallet_admissions` is a separate default-off option, in addition to the existing supported-admissions opt-in. All existing runtime SHA-512, companion, party, origin, equipment, claim, menu, permission and saved-state checks remain. Existing new-contract transactions can be recovered from verified saved phases even when new admissions are off. Unreceipted or ambiguous phases stay quarantined and are not replayed.

## Exact completed tests

Fourteen disposable-runtime phases passed: core (5), fault/restart (4), and integrated/certificate regression (5). The actual final JAR pair was used with two local test clients and synthetic GitHub-hosted loopback worlds. Every startup recorded the commit, actual separate JAR identities, non-jail component presence, absence of old integrated jail classes and an empty escape-reward definition.

The occupied wallet was seeded BEFORE admission in slot 32. It had a randomized custom name and lore, with marked gold stacks of 11 and 7 in inner slots 0 and 5 and four empty slots. Assertions compared complete wallet NBT, full inventory and native equipment state, not just wallet presence or coin totals.

| Case | Result |
| --- | --- |
| Actual `/jail` and `/unjail` | Full source removal into bound escrow and exact original-slot return passed; one physical returned wallet. |
| Thirteen unsupported admissions | Refused without resource mutation, assignment or new custody transaction. |
| Occupied return destination | Repeated refusal preserved a named synthetic netherite stack and did not grant another wallet. Only that test-created conflict was subsequently removed by the fixture. |
| Disconnect/restart while held | Actual disconnect and independent restart preserved custody and exact escrow state. |
| Saved removal before origin mutation | Interrupted transaction remained held; explicit saved-phase recovery and exact release passed after restart with new wallet admissions disabled. |
| Saved return before metadata completion | Restart and explicit receipt-verified completion passed without a second grant. |
| Jailed escape death/native respawn | Player admitted with occupied wallet died while the wallet was in escrow, not live inventory. No wallet/coin drops occurred. Restart on death screen, native respawn inside jail, saved completion interruption, another restart and exact final return passed. |
| Completed-release restart/repeated actions | Returned state survived restart; repeated admission/release/recovery did not duplicate the wallet. One-time no-reward achievement and cancelled-death no-repeat protections passed. |
| Hard stop before player save | Disposable JVM was halted after live removal but before player-save completion. Actual restart loaded the saved original once. Ambiguous custody remained quarantined; repeated actions neither removed it again nor granted another copy. This is a verified safe hold, not automatic recovery. |
| Dependency/companion enforcement | Genuine unapproved dependency and byte-altered companion were rejected. Restoring approved bytes allowed explicit recovery. |

Negative admissions: disabled new option, auto-exchange, upgrades/bonus slots, noncoin contents, extra hidden slot, unknown coin component, unknown wallet component, wallet count two, multiple wallets, hotbar wallet, retained XP, nonempty synthetic Eevee party and equipped native wallet.

The build passed 65 Jail JUnit tests (10 new), 69 Unyielding JUnit tests and 15 Python certificate/packaging tests. JUnit had zero failures, errors or skips. The independently rebuilt original reference passed 119 tests. Independent downloaded-evidence verification also passed, checking 14 startup identities, all phase reports, actual binary hashes, refusal flags, hard-stop evidence and JUnit XML totals.

## Exact binaries and unchanged scope

| File | Bytes | SHA-256 |
| --- | ---: | --- |
| `Minecraft-Jail-1.2.0.jar` | 304634 | `5c2653a9c14d3199581fdd67e05d055b334ec2746fe7047e3c80454eb732474e` |
| `Unyielding-1.2.0.jar` | 17109697 | `61f6add6c68364b1c9dda4486948faf8d104c45ca2db2c40199de64a04ea38f7` |

The whole companion JAR is byte-for-byte identical to the reviewed split companion. All Jail resources, certificates, metadata and mixin descriptors are unchanged. No test-mod classes are in production. Version remains 1.2.0.

Production changes are limited to five existing Jail Java files and two new classes. Runtime hash audit, escrow journal, vanilla custody/port, custody ledger/service, escape custody, origin transaction engine and the existing party/equipped-wallet boundary remain unchanged. Non-jail source was not edited. Original runtime fixtures were preserved and adapted only as copies in a separate test mod.

The reusable build's `verification.json` remains a code-only report. Runtime proof is `packed-wallet-runtime.json` from final artifact `14287794515`, with raw core artifact `14287393525`, fault artifact `14287374523`, integrated artifact `14288093985`, and build-test evidence `14287957306`. Code-only evidence must not be represented as gameplay proof.

## Earlier failures

Run `43989778505` failed its full runtime gate. One test incorrectly assumed hotbar slot 8 was empty; it now chooses an actually empty slot without overwriting fixture equipment. Another assertion tried acquiring a second command-journal writer while the coordinator already held its exclusive lock. It now reads through that existing owner without acquiring or closing another lock. Those corrections were test-only; production checks were not weakened. Earlier partial runs are excluded from the final pass count.

An earlier transfer workflow could not push a workflow file with its runner token. Source was transferred separately and the authorized connector added the workflow. Temporary scaffolding was removed, history preserved, and no permissions weakened.

## Remaining limits and genuine gaps

Nonempty Pokemon-party admission remains blocked, including with an otherwise valid packed wallet. No party custody adapter was implemented here. A safe adapter still needs a proven Cobblemon persistence-completion boundary, complete Pokemon identities/state, authoritative party/PC ownership and mutation fencing for send-out, riding, trades, battles and other paths. Saving vanilla player data does not establish completion of the separately asynchronous store. This is remaining implementation work, not a claim that private copies are required for all useful progress.

Equipped native/Curios wallets, other wallet variants/denominations, upgrades, auto-exchange, bank links, external accounts, retained-XP combinations and unknown profiles remain refused. Ambiguous hard-stop transactions stay quarantined. Older-build rollback with active new-contract custody is not certified; do not rewrite old fingerprints to force recovery.

Actual full-modpack and old-save migration were not run. Existing read-only planner and migration matrix remain unchanged. They require exact current/old server-pack binaries/configs, the matching integrated old JAR, consistent pre-split world/player/Pokemon/economy/jail-journal data and any external-store exports. The old client manifest is not a substitute. Private saves and credentials must not be uploaded publicly.

No installed JAR, live world, real player data, artwork or model setting was changed. No deployment occurred. Keep the exact candidate pair together for reviewed disposable testing only.
