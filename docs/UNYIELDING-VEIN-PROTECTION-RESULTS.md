# Vein mining: protection compatibility and display checks

Verified 2026-11-17 UTC (2026-11-16 CST). This continues checklist item 6 from the completed [party-confirmation checkpoint](UNYIELDING-PARTY-CONFIRMATION-RESULTS.md), `d93f9d57f958f3298a99875c966fa697f5161ca6`, on `codex/unyielding-jail-free-followup`. Original worktrees, prior reports/evidence and the dirty prototype were preserved. Deployment remains blocked pending paired Jail certification.

## Supported protection scope

A read-only inventory of the actual `Cobblemon Unyielding test/mods` directory identified Open Parties and Claims 0.33.0 plus `opacbonusclaims-1.2.0-1.21.1.jar`; no separate FTB claim mod was found. The installed OPAC JAR SHA-512 matches the pinned runtime dependency used in these checks:

`646fd9796ef2dfd1132e22aacf1a417639233d90fb7eb862a718badfed955d54714c88327b7ddc651f98cd5b7d2a80e8c91233d2232680d4998fdd10ef392ffb`.

The disposable environment uses Minecraft 1.21.1, NeoForge 21.1.255, actual OPAC 0.33.0 and the assembled Unyielding JAR with its pinned required dependencies. Tests cover OPAC owner/foreign access, configured admin full-pass grant/revocation, vanilla spawn protection and representative NeoForge cancellation/item-use denial. The bonus-claims addon and the full modpack's claim/rank configuration are not loaded or certified here.

## Reproduced failures and production changes

The corrected baseline [run 43984943211](https://github.com/WeepingAngeIs/Server-Jars/actions/runs/43984943211), commit `1d34e24ae23060a3a06426d8f8492ac9073a42bb`, exposed five destructive protection failures:

- A `LOW`-priority listener cancelled the starting break, but connected extra blocks had already been removed.
- An allowed break outside vanilla spawn protection removed connected blocks inside the protected area.
- Connected axe stripping, hoe tilling and shovel path creation changed extra blocks inside another player's OPAC claim.

Fix `3de7800e9924da61bce75a4e43dd6c2e520020e7`, refined by `08ec8c819adc1fafd1e61fe0f3dd3165bd9eb343`, changes only `ultimine/Ultimine.java` relative to the party checkpoint:

- Bulk handlers run at `LOWEST` priority, after the tested cancellation handlers.
- Each additional mining/tool target passes the native `level.mayInteract` check, including spawn protection.
- Each extra tool target posts the native right-click hook and respects cancellation or `getUseItem() == TriState.FALSE` before applying the item.
- Spectator tool actions and a denied starting item-use event do not expand into bulk actions.
- Tool actions remain item-only. The fix does not activate neighboring block menus. Native Adventure item-use predicates remain authoritative; a mining `CanBreak` restriction is not substituted for right-click `CanPlaceOn` rules.

These choices follow the native [NeoForge right-click event contract](https://raw.githubusercontent.com/neoforged/NeoForge/1.21.1/src/main/java/net/neoforged/neoforge/event/entity/player/PlayerInteractEvent.java) and [CommonHooks implementation](https://raw.githubusercontent.com/neoforged/NeoForge/1.21.1/src/main/java/net/neoforged/neoforge/common/CommonHooks.java). OPAC likewise [recommends native interaction events when available](https://thexaero.github.io/open-parties-and-claims/javadoc/xaero/pac/common/server/claims/protection/api/IChunkProtectionAPI.html).

Compact feedback remains off by default. No UI assets, rank plans, quest/reward implementation or version numbers changed.

## Verification history and fixture corrections

All builds and game execution ran on GitHub-hosted runners with disposable worlds and two actual clients, Dev and Visitor. Dev sends the production held-key payload and native block/use packets; Visitor supplies a real claim owner and the operator required to enable vanilla spawn protection. No fake players are used.

- [43983833155](https://github.com/WeepingAngeIs/Server-Jars/actions/runs/43983833155), `a7f4108a5c14b9f290c5a9773961ba2fb0032f3b`: initial 21-case fixture failed its action-bar reflection check because it used `overlayMessage` instead of Minecraft 1.21.1's `overlayMessageString`. This is not evidence of 21 production defects.
- [43984943211](https://github.com/WeepingAngeIs/Server-Jars/actions/runs/43984943211), `1d34e24ae23060a3a06426d8f8492ac9073a42bb`: 16/23 cases passed; five protection failures above were reproduced. Two additional failures were fixture conditions: the first allowed action returned zero extras immediately after the initial teleport, and Peaceful difficulty replenished the hunger-stop fixture's food. A loaded-area preview warmup and Normal difficulty isolate those conditions.
- [43986044463](https://github.com/WeepingAngeIs/Server-Jars/actions/runs/43986044463), `3de7800e9924da61bce75a4e43dd6c2e520020e7`: all 27 native cases passed after the protection fix, alongside the 69 existing unit tests. Review then removed an unnecessary mining restriction from right-click handling and added four explicit Adventure-mode tests.
- [43986826373](https://github.com/WeepingAngeIs/Server-Jars/actions/runs/43986826373), `08ec8c819adc1fafd1e61fe0f3dd3165bd9eb343`: 29/31 cases passed, including all four Adventure cases and the denied-target checks. Two allowed tool assertions were inconsistent.
- [43988413467](https://github.com/WeepingAngeIs/Server-Jars/actions/runs/43988413467), `a10d20a93a90eb9e9d0c4fa605c018eae3072c98`, and [43989656485](https://github.com/WeepingAngeIs/Server-Jars/actions/runs/43989656485), `f766e73ce37a5763516716b057096bdb449cc00d`: stricter test handshakes stopped while awaiting ready state, rather than accepting partial runs. Diagnostics verified client position/tool/mode/target blocks; the harness now keeps the world screen focused, refreshes eligible held state until server acknowledgement, and explicitly synchronizes inventory and target replication. These revisions change only test code; the production JAR remains identical to `08ec8c8`.

Final synchronized verification **passed** at `37af7a7de267504700916677d52c792e6f505401`, [run 43990790193](https://github.com/WeepingAngeIs/Server-Jars/actions/runs/43990790193): build, all 69 existing unit tests in 15 suites (zero failures/errors), all 31 native cases (zero failures), and 31 captured screenshots. The synchronized harness uses the identical production JAR from `08ec8c8`; no production networking workaround was added. Client world actions wait for replicated position, mode, tool and target blocks, plus server acknowledgement of eligible held state. The world screen is kept focused for those actions; the intentional inventory case is separate.

The native matrix includes:

- Unclaimed/owner mining allowed; foreign claim extras and starting block denied; admin full pass allowed and revoked access denied; allowed origin outside vanilla spawn protection with protected extras retained.
- Late break cancellation, extra-break cancellation, denied starting item use and denied extra item use.
- Connected stripping/tilling/path creation allowed on unclaimed targets and denied on foreign extras; Spectator use denied; Adventure mining and tool use both denied without their predicates and allowed with the correct explicit predicates.
- Feedback off/default, server master switch, require-item empty hand, durability/hunger stops, loaded preview, shape list, hidden HUD and inventory gating.

[Final evidence artifact 14287777488](https://github.com/WeepingAngeIs/Server-Jars/actions/runs/43990790193/artifacts/14287777488), `unyielding-vein-protection`, contains native/client logs, case outcomes, dependency manifest, screenshots, unit-test XML and the candidate JAR. Its downloaded ZIP SHA-256 was verified against the published digest: `08d86f5ed5c1f4ce915c4a06d61c46b6de8db92df31b4ed869893d6d446ecbce`. The JAR entry was independently hashed and matched the candidate below. The corrected baseline artifact was also retained with verified ZIP SHA-256 `a2be8435d1e9b221744dd95b7b1f31b9a2b81bd7671a092e753c9c998e82646e`.

## Display observations

Generated screenshots at 640×480 with GUI scale 2 were visually inspected for the ordinary count, durability-stop and hunger-stop messages, shape list/outline, hidden HUD and open inventory. In a clear HUD, the longest tested stop labels fit on screen without clipping. Opening inventory removes the held mining overlay. Hiding the HUD hides the shape list; the existing world-space outline remains visible. No display assets or layout code were changed.

**Observed visual gap:** the initial ordinary-feedback screenshot has recent vanilla/login chat crossing the action-bar line, and startup toasts cover part of the shape-list heading. Later clear-HUD screenshots show the text fitting, but crowded startup/full-pack HUD acceptance remains open. This change does not reposition vanilla chat, toasts or other mods' UI to conceal that overlap.

The action-bar assertions check actual extra-block counts, feedback off, durability/hunger stop reasons, server master-switch gating and require-item gating. Outlines describe geometric candidates, including possibly protected blocks; they are not an authorization promise. The existing `finished` label means the scan ended, not that every highlighted block was permitted or removed. Other resolutions, all shape-cycling inputs, shaders and the full pack's HUD combinations remain outside this visual check.

## Candidate integrity and deployment hold

Current candidate remains version 1.2.0:

- SHA-256: `4f8093631266cf7434309dc3bfc333be2ca1338f14e8d3a58845ddeb0eb1b6fa`
- Raw SHA-512: `e5f508f6e0b1d464c240f3800bbe21459ce722bce80f3e48f2e5185b5f50451435dc098a5add6e3ab4146b7d2a7bcbb5ba2eab03148bac0abbb4e2cc25907cf6`

Every ZIP entry was compared by content hash against the previously certified jail-free companion (`61f6add6c68364b1c9dda4486948faf8d104c45ca2db2c40199de64a04ea38f7`). Only `TeamManager.class` from the prior party fix and `Ultimine.class`, `Ultimine$State.class`, `Ultimine$Events.class` differ. No entries were added or removed. All other feature classes and resources match the certified companion; the outer/state Ultimine classes also contain changed source line metadata.

Standalone Jail was not built, loaded or edited. Its source tree remains `a076432b535ebd230fe8ab0681a93ae85e786a69`. No certificates or companion-hash allowances were changed. **This different Unyielding binary requires coordinated paired Jail certification before deployment.**

Installed JARs, live worlds, profiles, settings and player data were not changed. Local work was source editing and read-only file/hash/evidence inspection. Evidence and candidate files remain under this checkout's untracked `outputs/vein-protection/` directory.

## Remaining scope

Coverage is bounded to the versions and scenarios above. It does not certify arbitrary protection mods, cancellations from later listeners at the same `LOWEST` priority, world-border permutations, the bonus-claims addon, full-pack claim/rank policy or every custom tool. Normal-hardness Survival hold-to-break and cold first-click timing are not certified; Survival stop fixtures use instant-breaking slime blocks. The held-key acknowledgement and eligible-state refresh are test orchestration, not a production networking change or certification of every rapid input transition. Crowded startup/full-pack HUD overlap is an observed remaining acceptance gap.

Prior party/reward evidence is preserved; those complete suites were not rerun for this Ultimine-only change. After this item, actual-pack/rank acceptance for opt-in party meetings and real-pack ground-item merging/multiplayer checks remain open. Deployment also remains blocked by the separate paired-certification requirement.
