# Compact navigation readability and access

This is the explicitly bounded follow-up to the [navigation report](UNYIELDING-NAVIGATION-ACCEPTANCE-RESULTS.md): clean, usable compact screens after normal notifications finish. It is functional UI code/readiness work, with no redesign, asset creation or art phase. The earlier eight logic cases remain separately scoped; this pass checks three meaningful compact layouts rather than rerunning that matrix.

## Inputs and notification readiness

The starting version is **1.2.0**, SHA-256 `216de93ca87cc9e0a54f0f0991c675a1058ee95de3247c8efbebffc664f0c504`, reused from navigation artifact `14318597401`, run `44061079937`. Work is isolated on `codex/unyielding-compact-navigation`, based on report checkpoint `65b667f77b5255a5bbfd41be2cf7bb46a246219c`. No unchanged production build is repeated for the baseline.

Two actual disposable clients use a native 640x480 framebuffer at GUI scale 2 (320x240 GUI units). They have synthetic personal progress, rewards, inventory and a fourteen-member party, including twelve offline roster entries. Only one selected compact viewport is checked; this does not certify every resolution, font or pack overlay.

Cobblemon's starter prompt is persistent rather than a timed startup notification. The [pinned 1.8.4 PartyOverlay source](https://gitlab.com/cable-mc/cobblemon/-/raw/1.8.4/common/src/main/kotlin/com/cobblemon/mod/common/client/gui/PartyOverlay.kt) dismisses it through normal world rendering when the player's Pokemon party is nonempty. Before the preservation baseline, each disposable player receives one ordinary owned Eevee and selected-starter state through supported Cobblemon APIs. Clients render the normal world, then wait until the native visible and queued toast collections have both been empty for at least twenty ticks. Every screenshot has the same readiness gate. No toast is cleared, reset or hidden; the HUD stays visible, and no screenshot is edited.

## Reproduced baseline defect

Source `f0cacad89288fc7d83d277c5de6e9033ac1f35e0`, [run 44079788061](https://github.com/WeepingAngeIs/Server-Jars/actions/runs/44079788061), job `134027070228`, completed three native cases: **two passed and one failed** on the unchanged `216de93c...` JAR. Quest search/STARTED filter and Pin/Party goal/Unpin/close passed. The owner roster case reproduced real control overlaps after taking clean native screenshots:

- Find occupied x=156..204, while the previous-page button occupied x=202..244: a two-GUI-pixel overlap.
- Invite occupied x=158..218, while Leave occupied x=174..232: a forty-four-GUI-pixel overlap.

The issue was present on the first page, last page and filtered roster. Actual framebuffer inspection confirmed the Invite/Leave collision; native rectangle observations independently measured both overlaps. Screenshot completion, ordered server receipt and the preservation comparison precede the failure report, so the failed case retains its observations and original images.

[Baseline artifact 14327026420](https://github.com/WeepingAngeIs/Server-Jars/actions/runs/44079788061/artifacts/14327026420) was downloaded. ZIP SHA-256 `6165a43ae56f0ac1bc6407e95d18e80788bce6f244711340bd7cf03ca49fe959` independently matches the uploaded digest in the job log. The included JAR matches the exact starting candidate. Seven original 640x480 PNGs are retained. This is a native layout defect, not a reason to repeat unrelated passed suites.

## Narrow functional fix

Commit `cdd1ea8c874730193453c816b2ebc6b753f1995a` changes only `TeamScreen.java`. The roster search field leaves room for Find and both page arrows. The Invite button and its text field leave room for the existing right-aligned Leave/Disband controls. Button widths, callbacks, authority checks and row/page sizes remain unchanged. At the previous 400-GUI-pixel panel width, the coordinate calculations preserve the original field widths and positions; this is source-level preservation, not a new wide-viewport runtime claim.

At the tested compact 304-unit panel width, the search field is 122 units wide. Find ends at x=196, before the previous-page button starts at x=202. The owner's Invite ends at x=166, before Leave starts at x=174; the player-name field uses the remaining 78 units and retains native horizontal text scrolling. No quest-screen behavior, server policy, progression, rewards, assets or models are changed.

## Fixed-candidate verification

Verified 2026-11-17 UTC: [run 44080965102](https://github.com/WeepingAngeIs/Server-Jars/actions/runs/44080965102), job `134031126935`, on exact source `cdd1ea8c874730193453c816b2ebc6b753f1995a`, passed **three of three native cases and 69 unit tests**, with zero failures/errors/skips. Fifteen native Gradle unit-result XML files were inspected. The same three-case fixture is unchanged from the reproduced baseline. This one hosted build/check and forward assembly are justified by the production layout fix; no local build or game test ran.

The three selected cases are:

1. Owner roster first/last pages and a native member search, with in-bounds, disjoint controls and readable button labels.
2. Real quest Search prompt and one STARTED status selection/Next, with the complete status controls visible.
3. Native Pin, another member's Party goal/detail, Unpin and close, retaining distinct personal 4/10 and 2/10 counters.

Preservation compares full native QuestData and DailyLogin data, inventories/components, XP, TeamData except only the tested party marker, separate personal records and disabled sharing. Selected-starter state and owned-Pokemon count are also checked. This does not claim full serialized Pokemon-stat equality or every external bank. No quest claim, task-completion, destructive party or payout button is pressed.

All seven original fixed-run PNGs were independently verified at 640 by 480 pixels and visually inspected without editing. First/last/search roster states now show separate Find/arrow and Invite/Leave controls. The native Search prompt is legible, the complete STARTED selector is visible, and owner/member goal details show readable Pin/Unpin/close buttons and their distinct 4/10 and 2/10 counters. Visible and queued notification counts were zero in the retained observations, with the per-capture twenty-tick quiet gate satisfied. Long labels retain their existing shortening behavior and a modal detail normally covers background sidebar controls; no universal full-text or every-overlay claim is made.

[Fixed artifact 14326653733](https://github.com/WeepingAngeIs/Server-Jars/actions/runs/44080965102/artifacts/14326653733) was downloaded independently. Its ZIP SHA-256 matched the published upload digest `9d2524e404758a03eb7fcb7ed52b19f9da1e8dd5a7d01ba4d79b65fabe76a570`. The downloaded JAR matched the native loaded ModList identity/hash:

- Version: **1.2.0**
- SHA-256: `9975ec46f65bcab44fb819f00adb854a10071b6158c56b1ac78f7695af634a40`
- Raw SHA-512: `eb725e37dc0bd8a27f4cff60f29c27613d92cc8be4ddcdc862a3c3e0a39bb32247e8ef437ebba40513278e9d07171fb6e56d6bf4c3208e81e9bc8ddd3fddc228`

There are no added or removed JAR entries compared with `216de93c...`. Only `TeamScreen.class` and its compiler-generated `TeamScreen$1.class` differ. Read-only classfile parsing established that the companion differs by one debug byte: its source line changes from 370 to 373, while executable code, constants, exception handling and other attributes remain identical. All other entry bytes, including quest code, editor, settings, existing fixes, resources, artwork and models, are preserved. This binary comparison does not relabel the earlier eight logic passes as new runs on `9975ec46...`.

The [machine-readable results](UNYIELDING-COMPACT-NAVIGATION-RESULTS.json) retain both runs, exact hashes, case results, entry comparison and screenshot digests.

## Disposable test readiness and remaining limits

The exact `9975ec46...` **1.2.0** JAR in the fixed artifact is ready for a **disposable compact-UI user test** with matching normal dependencies. The selected roster/search, STARTED filter and informational goal controls passed at 640x480, GUI 2. A local handoff copy and unedited images are retained under `outputs/compact-navigation` in this worktree. Nothing is installed, published as a release or deployed by this pass. This is not full-pack or live-server certification.

The UI check uses one generated running server and public pinned dependencies. It does not establish server restart/crash recovery, physical power-loss or cross-store payout atomicity. Full-pack presentation needs the exact reproducible pack, resources and relevant configuration; actual live rank/provider/claim behavior needs authoritative server policy. Real old-save migration needs a consistent authorized disposable backup set. Those inputs are not imported or inferred here, and no further acceptance target is selected.

All installed profiles, JARs, worlds, private data, settings, saved toolbars, art and models remain untouched. Prior evidence and artifacts are preserved. The separate Jail project, worktree, certificate and gates remain unchanged and are not prerequisites. Basic Mawile behavior and unrelated passed suites are not retested. Version stays 1.2.0. This bounded pass ends after its verification report.
