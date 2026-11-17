# Vein controls, crowded HUD and installed-addon acceptance

This follows the passing 31-case [protection checkpoint](UNYIELDING-VEIN-PROTECTION-RESULTS.md), finalized at `89fc291cb7f49f76f5678ce16778fedf1ebcf474`. That report describes its own binary and remains unchanged. Version stays 1.2.0. Installed JARs, worlds, profiles, settings and player data are untouched. Deployment remains held pending coordinated paired Jail certification.

## Baseline evidence

- Commit `c1966f14c4bce5926348d6a97a26e1d047748626`, [run 43997368182](https://github.com/WeepingAngeIs/Server-Jars/actions/runs/43997368182), artifact `14291100298`: production build, unit tests and assembly passed, but the new test fixture failed compilation before game launch. Its party API required Kotlin collection marker interfaces on the compile classpath. This was a fixture failure, not native gameplay evidence.
- Fixture correction `8f89272e8512bfa30bee4991f2b57538c8f99ab7` uses the existing checksum-pinned compile-only Kotlin library. [Run 43999608165](https://github.com/WeepingAngeIs/Server-Jars/actions/runs/43999608165), artifact `14292221121`, completed all 41 native cases: 38 passed, three failed. Downloaded artifact ZIP SHA-256 matched the published `2621f8e5d12e48ff86feb0619d3c376e3587e57901e9b2d364419000748f1a0d`.
- Cold same-frame Creative input broke the starting block without the two expected extras. Cold Survival and warmed normal-hardness Survival input passed. These cases use the actual attack key mapping and vanilla hold-to-break pipeline, without the earlier fixture's held-state refresh/acknowledgement.
- Completed Survival tool durability was `247/250`, while feedback reported `248/250`: the readout ran inside BreakEvent before vanilla applied the starting block's wear.
- The Survival foreign-extras case failed with its starting block retained. This alone did not establish a claim bypass; further diagnostics and verification were required at that checkpoint.
- Existing cancellation, spawn, Adventure, Spectator, OPAC owner/foreign/admin/revocation and connected tool checks passed. Bonus-claim unauthorized grant denial and console grant/revoke protection checks passed.

## Bounded fixes

Commit `4bf141bee4d9f0190cae519d246717b52c62d111` synchronizes changed held state at the interaction-key event before vanilla sends the action, retaining tick-based release/menu gating. Feedback alone is deferred to server tick end so durability includes the completed starting break. Pending feedback clears on logout/server stop; gameplay expansion and protection ordering are unchanged.

The reproduced crowded-startup screenshot has chat crossing the native action-bar readout. The module now uses a bounded, wrapped panel with its selected shape and optional result. Quest toasts, general notices, vanilla chat and other mods' HUDs retain their behavior. The existing Notify payload carries a dedicated kind; serialization is unchanged. Feedback remains off by default.

Review also found a fixture error: Cobblemon party `size()` means capacity, not occupied slots. The initial “populated” screenshots actually showed an empty party. They are not accepted as populated-party evidence. The fixture must use occupied slots, synchronize them, and verify six client-side Pokémon plus a renderable party overlay. Public [PartyStore source](https://gitlab.com/cable-mc/cobblemon/-/raw/main/common/src/main/kotlin/com/cobblemon/mod/common/api/storage/party/PartyStore.kt) distinguishes `size()`/`occupied()`; the [PartyOverlay layout](https://gitlab.com/cable-mc/cobblemon/-/raw/main/common/src/main/kotlin/com/cobblemon/mod/common/client/gui/PartyOverlay.kt) reserves a left-hand column. The compact panel is positioned beside that column, below the top startup toast and above recent chat, leaving the observed right-side minimap clear.

## Exact public inputs and privacy boundary

The fixture downloads public releases corresponding to locally inspected installed-file hashes: OPAC 0.33.0, OPAC Bonus Claims 1.2.0, OPAC PvP 1.0.1, Necronomicon 1.6.0, Xaero Minimap 26.5.0 and Xaero World Map 1.46.0. The artifact runtime manifest records exact filenames and hashes. Only the observed non-sensitive upper-right minimap placement is reproduced. No waypoints, map cache, private saves or private configuration contents are uploaded.

Locally normalized rank definitions match the generated synthetic defaults (SHA-256 `754060f8c0f37c9ec3339f2788fe4d192c0e434bddad282c0ae9219b5bea6520`). Local client OPAC server-configuration contents differ from the generated defaults. Neither client-side files nor matching rank definitions establish the authoritative server's assignments, permission provider or world-specific claim policy. Those inputs are unavailable here; actual live policy remains unverified. No rank or claim policy is rewritten to force acceptance.

## Verification history and status

The first fix verification, [run 44001452065](https://github.com/WeepingAngeIs/Server-Jars/actions/runs/44001452065), commit `4bf141bee4d9f0190cae519d246717b52c62d111`, passed all 69 unit tests in 15 suites and all 42 native cases. The additional case exercises all six shape selections and wraparound through the scroll handler, with six screenshots. It is not a physical mouse-wheel test. Cold Creative, cold Survival, normal-hardness Survival hold, completed durability and Survival foreign extras all passed. The Survival boundary trace records the starting block uncancelled and both foreign extras cancelled. No new protection-policy change was made for that earlier failure; the evidence does not isolate its initial cause.

Artifact `14292876994` ZIP SHA-256 matched the published `310397185607af603299f98729cd3ba6fb2eb3b6c0726c885f68fb2c23544b9b`. Its intermediate JAR SHA-256 is `bfff44fe536229ea0e4d27416bf181fae1205618bf8ce3958826e4fccccc52bc`. The Survival screenshot shows the correct `Tool 247/250`. This run still used the erroneous empty-party fixture and earlier panel placement; it does not establish populated-party acceptance.

Commit `eec7d13ea1b5783741a51d4429a23015b4a6b74b` corrects party occupancy/synchronization, requires six client-side Pokémon and a renderable party overlay, and positions the readout beside the party column. It also records panel bounds and requires no panel with inventory/hidden HUD. [Run 44002614402](https://github.com/WeepingAngeIs/Server-Jars/actions/runs/44002614402) passed 69 unit tests and all 42 native cases, capturing 48 screenshots (42 case views plus six shape selections). Artifact `14292498896` ZIP SHA-256 matched published `d8d4df889e9987d94991b8f59cc02ac6965e3cae7751b3a5be9fb1cc8b0f844f`. Its intermediate JAR SHA-256 is `7672ed7ba266738c61662d9079eb302f8e992c9e3d3bacd23e51851f55ef8a8d`.

Visual inspection confirmed six rendered Eevee slots, chat and several simultaneous startup/quest toasts. It exposed one remaining layout defect: stacked toasts covered the panel's right edge, even though the short tested result text stayed readable. Commit `51c23f468fa2af1510d93834946269298b7c83f6` narrows the panel at minimum GUI width and strengthens the reserved-toast bounds check. This is an evidence-driven layout refinement, not a gameplay or policy change.

## Final verified checkpoint

Verified 2026-11-17 UTC (2026-11-16 CST), source `51c23f468fa2af1510d93834946269298b7c83f6`, [run 44003918234](https://github.com/WeepingAngeIs/Server-Jars/actions/runs/44003918234), job `133774469774`:

- All **69 unit tests / 15 suites** passed, zero failures/errors.
- All **42 native cases** passed, zero failures; **48 screenshots** captured.
- All building/game execution used GitHub-hosted runners, disposable worlds and two actual clients. Local work was source editing and light file/hash/evidence inspection.
- [Artifact 14293254287](https://github.com/WeepingAngeIs/Server-Jars/actions/runs/44003918234/artifacts/14293254287) downloaded ZIP SHA-256 independently matched published `f4ac41d0f501180c056ec894cdcd0927f075479d066e5abeb3c443c933555eb6`.

Final crowded-result and populated-preview screenshots were visually inspected at 640×480, GUI 2, with six rendered Eevee slots, recent startup chat, stacked advancement/tutorial toasts and the quest toast. The panel now clears both the party column and stacked toast edge. Recorded result bounds are `[70,34,158,83]` and preview bounds `[70,34,158,65]` in the 320×240 GUI coordinate space. All six selected-shape screenshots were inspected: labels and scroll hint fit, with wraparound back to Shapeless. The minimap is visible once unrelated toasts clear it; this change does not reposition those other HUDs. The completed Survival screenshot/readout reports `247/250`, matching actual tool state. Inventory and hidden-HUD cases record null panel bounds; the existing geometric world outline remains visible when the HUD is hidden.

The final artifact repeats the previously passing owner/foreign/admin/revocation, cancellation, native spawn/Adventure/Spectator, connected-tool and bonus-grant/revoke restrictions. Feedback remains off by default. These are bounded scenarios; they do not prove exhaustive input timing or every external policy combination.

Final candidate, still version **1.2.0**:

- SHA-256: `7a0a1cec09001ca6f47ee6180371842f27ef6d83f7411a7e61fff6a83e74b414`
- Raw SHA-512: `9a36367d2bd92a1a3acd570de31649c0db02de5aea32d2d933a323212ccb2a35be6fdb48bc7932771c95998121a25b0467a131c39ae879c5c6481154fdddcbd6`

Every JAR entry was compared against the previous protection candidate (`4f8093631266cf7434309dc3bfc333be2ca1338f14e8d3a58845ddeb0eb1b6fa`). Only `client/ClientHandlers.class`, `client/UltimineClient.class`, `client/UltimineClient$Events.class`, `ultimine/Ultimine.class`, `ultimine/Ultimine$Events.class` and `ultimine/Ultimine$State.class` changed; `ultimine/Ultimine$Feedback.class` was added. No entries were removed. All other feature classes and resources match, including the earlier party fix. Some server outer/state differences are source-line metadata. No art/model assets changed, no test helpers were added to the production JAR, and no integrated Jail entries returned. The Notify serialization class is byte-identical; its source comment documents the dedicated feedback kind.

## Remaining boundaries and deployment hold

The bounded addon/HUD matrix does not load the complete modpack or certify arbitrary HUD layouts, resource fonts, shaders, resolutions, protection mods, later same-priority cancellation listeners, custom tools or all rapid input permutations. Actual live server rank/claim policy remains a separate acceptance gap. Party meetings and ground-item multiplayer/merging retain their previously documented gaps.

Standalone Jail remains unedited and unbuilt, tree `a076432b535ebd230fe8ab0681a93ae85e786a69`. Certificates and companion-hash allowances are unchanged. Every changed Unyielding binary remains blocked from deployment until the separate coordinated paired certification is completed. Candidate and evidence downloads remain only inside this checkout's untracked `outputs/vein-acceptance/` directory.
