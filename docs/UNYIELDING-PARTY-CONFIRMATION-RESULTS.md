# Party confirmation races: verified scope and deployment hold

Completed 2026-11-16 on `codex/unyielding-jail-free-followup`. This follows checklist item 5 and preserves the completed [reward-credit recovery report](UNYIELDING-REWARD-RECOVERY-RESULTS.md). It does not authorize installation or certify a changed Jail companion pair.

## Reproduced defect and narrow fix

A pending ownership transfer survived the requester's actual disconnect and reconnect. [Baseline run 43978757680](https://github.com/WeepingAngeIs/Server-Jars/actions/runs/43978757680), commit `32892bce5e6a954addc5fd19f5e402bae89759d2`, passed 18 earlier cases, then failed with `confirmation survived actual disconnect/reconnect`. Its native server log records Dev disconnecting at 23:26:54 UTC and rejoining at 23:26:59 UTC, within the confirmation window. This was a real client connection cycle, not a fake-player lifecycle call.

Production fix `09074d4789294c5b1e7946e21835edc83de0a39e` adds `pending.remove(p.getUUID())` in `TeamManager.onLogout`. Reconnecting now requires a fresh destructive-action request. Ownership/rank permissions, the 60-second window, membership checks, cancellation behavior, quest sharing and rank plans are unchanged.

An earlier harness-only run, [43977407679](https://github.com/WeepingAngeIs/Server-Jars/actions/runs/43977407679), exhausted the clients' initial 1200 MiB heaps before completing the checks. The isolated client heaps were increased to 2200 MiB; production memory settings were not edited. A redundant unchanged reward workflow was cancelled, then its push paths were restricted to relevant reward/quest changes to avoid rerunning completed recovery work for unrelated fixture edits.

## Passing verification

[Run 43979575601](https://github.com/WeepingAngeIs/Server-Jars/actions/runs/43979575601) tested commit `09074d4789294c5b1e7946e21835edc83de0a39e` and passed:

- Unyielding build and all 69 existing unit tests across 15 suites, zero failures/errors.
- Jail-free assembly with retained baseline entries, application-class dependency closure and no integrated Jail code/resources.
- 27 native party cases: 21 before a saved server restart and six after it, using two actual NeoForge clients connected to an independent assembled-JAR server.
- Real requester disconnect/reconnect and rejection of its stale confirmation; existing successful actions still complete once and replayed confirmations do not repeat them.
- Graceful client/server shutdown with native generation/save readiness checks and a real second server process loading the same disposable world.

### Actual client/network/UI coverage

Both Dev and Visitor run Minecraft clients under separate displays, game directories and identities. The independent client fixture sends production `TeamAction` payloads over their real connections. It also opens the production confirmation handler and presses the actual vanilla `ConfirmScreen` Yes/No buttons through their callbacks. The handler is entered reflectively; this is not a mouse-layout or manual visual review of the Owner/Disband entry buttons.

Client-driven cases cover UI cancellation, a pending disband replaced by a transfer, the stale disband confirmation, a competing member's unauthorized confirmation, successful ownership transfer, transfer replay, reconnect invalidation, stale confirmation after reconnect, successful disband and disband replay. The former owner becomes an officer as already specified.

### Server-path coverage with connected players

Other cases directly call the production confirmation/revalidation implementation using those same connected players: unauthorized requests, cancellation and repeated confirmation, expiry, target leaving, changed ownership/rank, administrator deletion permission revoked between request and confirmation, and competing request expectations. Expiry is injected by changing the private pending record's deadline to the past; the real expiration branch runs, but this does not measure a full 60-second wait. Ownership/rank state is deliberately changed and restored to model an intervening authority change; membership uses the real leave/invite/join paths and permission revocation uses native op/deop.

The restart occurs after a pending disband request. After restart, no old confirmation is usable and the party still exists until a fresh confirmed disband. This is a graceful restart, not a hard server crash or physical power-loss test.

### Quest, claim and unrelated-party preservation

The owner completes a real checkmark quest and claims its 3 XP-level reward before the other player joins. With sharing disabled, the visitor gains neither that completion nor its reward; an attempted claim pays nothing. Across ownership actions, cancellation/rejection, reconnect and restart, the owner's claim/3 XP and the visitor's absent claim/0 XP remain correct. Seeded earned-spin balances of 11 and 4 survive those operations and the restart.

After restart, enabling the existing sharing configuration and confirming disband preserves the shared completion in personal records. The original owner's reward remains claimed, the visitor's reward remains unclaimed, and disband itself pays neither player again. An unrelated party's serialized data is unchanged during each phase and the party is present after restart. No production progress-reset, payout, economy or rank implementation was changed.

## Evidence and binary integrity

[Successful artifact 14281483318](https://github.com/WeepingAngeIs/Server-Jars/actions/runs/43979575601/artifacts/14281483318), `unyielding-party-confirmation`, includes both native phase logs, client logs, shutdown evidence, dependency manifest, unit-test XML and assembled JAR. Downloaded ZIP SHA-256 matches the published digest: `b2b29df2796506f2495f9663460ecb46bf3e789c002e28afd1ba60a65aad419c`.

The reproduced-failure [artifact 14282797400](https://github.com/WeepingAngeIs/Server-Jars/actions/runs/43978757680/artifacts/14282797400) was also retained and its ZIP SHA-256 verified: `aba73e2a22fb0439f24e4d88c021446e8c92841da2669a615238049eb8dc5c50`.

Candidate remains version `1.2.0`:

- JAR SHA-256: `fb8378e987afacc0aadeeb09bee3ed3e5fd8c1ea4f2f45b1a4862281899593a9`
- Raw JAR SHA-512: `f89c16349c4b6f1c58f66d9f4e63953974020271a0a41476165509c018f346e89525f7a3dba1a0f85978f6c93c1e7112b06fb403e07cd8d83957be226716b665`

Every ZIP entry was compared by content hash against the previously verified jail-free companion (`61f6add6c68364b1c9dda4486948faf8d104c45ca2db2c40199de64a04ea38f7`). Only `dev/unyielding/quests/team/TeamManager.class` changed. No entries were added or removed; all other feature classes and resources match that verified JAR.

## Deployment blocker and remaining scope

**Deployment is pending coordinated paired Jail certification.** This production change creates a different Unyielding binary. The existing standalone Jail's exact companion checks must not be bypassed or silently updated. No standalone Jail code, certificates or allowances were edited; its production tree remains `a076432b535ebd230fe8ab0681a93ae85e786a69`. This workflow neither built nor loaded standalone Jail. The changed artifact is a candidate, not a certified replacement for the deployed pair.

All heavy work ran on GitHub-hosted runners with disposable worlds/clients. Installed JARs, live worlds, profiles, settings, artwork, original worktrees and the prior dirty prototype were untouched. Local evidence/candidate files are retained only under this follow-up checkout's untracked `outputs/` directory.

These are bounded, ordered server-thread interleavings, not exhaustive race permutations, physical network-failure testing, full-modpack/rank acceptance or visual layout certification. Fake players were not used for the party cases; the unrelated offline party is seeded fixture data. Repeated leave/rejoin or authority changes that return to an identical snapshot before confirmation were not separately certified. Prior reward-credit tests and their stated limits remain intact; their six recovery phases were not rerun for this single team-logout change.

The next unfinished checklist item is item 6: optional vein-mining feedback's protection compatibility and in-game presentation. Actual-pack/rank acceptance for opt-in party meetings and real-pack ground-item merging/multiplayer checks also remain open. Those tasks were not expanded into this change.
