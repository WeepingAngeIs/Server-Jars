# Jail-free Unyielding: named quest-credit recovery

Completed 2026-11-16. This is a test report, not an installation instruction or a new paired-Jail certification.

## Source and outcome

Implemented an independent assembled-JAR server fixture, a hosted-only preparation/runner, and a dedicated GitHub workflow on `codex/unyielding-jail-free-followup`. Started from the clean `d1e300df17d9a5e9caa7699c6bbd418c01463504` extraction checkout, preserving the original worktrees and dirty prototype.

Tested commit: `1ffad55f5387c3accea3d199a2447cb1dc0c09d5` (initial fixture `9a545cec44f23adba281585ea2d2af9efd44adf8`). No production defect was reproduced in these scenarios, so no production source change was justified. The Unyielding and standalone Jail production trees remain byte-identical to the verified `4e3667a149d4e530bddb5326ee739ad386fe6a47` pair:

- `unyielding-quests`: `d39585fbd4799f95536cd352b614e604d407b385`
- `minecraft-jail`: `a076432b535ebd230fe8ab0681a93ae85e786a69`

## Verified results

[GitHub run 43973062479](https://github.com/WeepingAngeIs/Server-Jars/actions/runs/43973062479) passed: build, 69 unit tests in 15 suites (zero failures/errors), assembled-JAR checks, and six native server phases across five actual JVM restarts using one disposable world.

The separate test mod loads production classes from the assembled JAR. It calls the real quest-spin claim path and administrator reset implementation using a NeoForge fake player; it does not test a connected client's UI or command parsing. Assertions cover:

1. Save a legacy balance/streak and old team-local claim record; force a new quest-credit journal intent without applying its balance/marker. Remove newer-format fields from the saved files to create an actual legacy-format load on the next JVM.
2. Recover that intent exactly once; import and retain the old claim/progress. Retrying its quest claim adds nothing. Earn a second quest credit and save its claim and balance.
3. Before restart, deliberately substitute the prior balance/marker file while retaining the newer quest save and journal. Recovery restores the missing earned credit once, retaining both saved claims. Repeated reconciliation adds nothing. A single-quest administrator reset produces a new generation; a new completion earns one credit, while unrelated claims and progress remain.
4. Restart with the new generation saved. Close reward storage, perform the real full administrator reset, earn later quest progress during this simulated reward-storage outage, and save.
5. Restart and reconcile old credit history without reinstating old claims or erasing later progress. Recomplete the reset quest and earn exactly one new credit.
6. Restart again and retry both the claim and reconciliation. The original 11 earned spins plus four distinct journal identities equal 15. The four markers and four journal credits remain; no prize payout was created. The other player's balances and both players' seeded daily balances/streaks remain unchanged throughout.

The first run, [43972454163](https://github.com/WeepingAngeIs/Server-Jars/actions/runs/43972454163), stopped at mandatory dependency loading because this new harness initially omitted CobbleDollars and Lightman's Currency. The harness was corrected to load their pinned, checksum-verified versions; no production fix or weakened loader checks were used.

## Artifact identity and deployment

[Successful evidence artifact](https://github.com/WeepingAngeIs/Server-Jars/actions/runs/43973062479/artifacts/14279362581): `unyielding-reward-recovery`, ZIP SHA-256 `400458f91f0f6886adce81656eb085fab2b5e6b782dc8708168266c835f5a680`. Downloaded evidence was checked against this published digest. It contains all six phase markers, native logs, runtime dependency manifest, unit-test XML and the assembled JAR.

`Unyielding-1.2.0.jar` SHA-256: `61f6add6c68364b1c9dda4486948faf8d104c45ca2db2c40199de64a04ea38f7`.

This exactly matches the previously verified production companion, rather than creating a changed binary needing a new hash allowance. Standalone Jail was neither built nor modified or loaded by this workflow. Its source-tree hash and absence of integrated Jail in the assembled JAR were checked. No companion-hash checks or certificates were changed. Any future production Unyielding binary change still requires coordinated paired certification; this run does not authorize one.

No installed JARs, worlds, profiles, settings, artwork, production balances or live progress were changed. Deployment remains pending.

## Limits

The failed-save case injects an older valid balance/marker snapshot; it models a missing SavedData write, not an operating-system write exception, physical power cut, arbitrary corruption or restoration from inconsistent whole-world backups. Stops after explicit native saves use abrupt JVM exit to keep the deliberately constructed on-disk fixtures intact. This does not certify graceful shutdown callbacks.

The outage is induced by closing reward storage through its lifecycle hook, not by removing or corrupting its journal. These checks cover named quest credits and their reset generations, not generic crate/loot credits, inventory/Pokémon prize durability, all modpack integrations or multiplayer UI. Optional missing-mod mixin warnings are present in this deliberately limited dependency harness. Existing conservative prize-review rules and recovery limits still apply; this is not exactly-once cross-store prize delivery.
