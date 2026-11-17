# Independent home/warp selector acceptance

This continues the verified [meeting-provider checkpoint](UNYIELDING-PARTY-PROVIDER-ACCEPTANCE-RESULTS.md), report commit `3f8cad9f8b648804b52718e85be6c2951def391e`. The initial candidate is the exact Unyielding-1.2.0.jar from [run 44031631588](https://github.com/WeepingAngeIs/Server-Jars/actions/runs/44031631588), artifact `14305363144`, SHA-256 `216de93ca87cc9e0a54f0f0991c675a1058ee95de3247c8efbebffc664f0c504`. Work is isolated on `codex/unyielding-destination-acceptance`.

## Why this remaining gap

The [acceptance index](UNYIELDING-ACCEPTANCE-INDEX.md) reconciles the living checklist against completed ground, reward, meeting-provider, party-confirmation, vein and tutorial evidence. Their historical candidate scopes remain intact; those suites are not rerun here. The living checklist's duplicate numbering and repeated ground/Mawile notes are consolidated separately from production code.

For home/warp selectors, the existing three DestinationPolicyTest cases and two CommandGateTest cases exercise pure helper rules. Meeting-provider checks preserve home/back data as unrelated state; they do not open DestinationScreen, capture a native destination payload or select a row. No prior native selector fixture was found. This owner-privacy and stale-menu boundary can be checked with public/synthetic disposable inputs, without importing live policy or a real old save.

## Five-case scope

Two actual clients, Dev and Visitor, use the production list commands and DestinationScreen. Test-only ordered server receipts establish production handling and response ordering before reading the actual screen data or invoking its row buttons. The fixture never invents a client-to-server selector packet: DestinationMenu.Sync is server-to-client only, and selections issue the existing home/warp commands.

The cases target each requester's own home names/dimensions with distinct and identically named homes; actual same-name selection resolving that caller's own location and normal cooldown behavior; refusal of another player's private-only home name; an already-open home selector after HOME_ENABLED changes to false; and normal public warp selection plus an already-open warp selector after WARP_ENABLED changes to false. Re-enabling each feature provides an allowed control. Home/warp feature gates are tested as implemented; the unrelated party-meeting permission-provider contract is not applied to them.

Refusals must preserve location, native cooldown, home/warp data, private back history and measured inventory/progress/economy sentinels. Successful travel may change the actor's location, existing command cooldown and normal back-history origin; unrelated player data remains unchanged. This does not claim all failed teleport reasons avoid cooldown: nonexistent-dimension failure occurs later in the existing handler and is outside these cases.

## Verification record

Verified 2026-11-17 UTC. Source `231098017c86fac72fc7d09c44c54041caf0e723`, [run 44036656345](https://github.com/WeepingAngeIs/Server-Jars/actions/runs/44036656345), job `133881042798`, passed **all five native destination cases**, zero failures. Both real clients reached the required readiness state, and runtime assertions verified the actual assembled JAR path/hash, absence of Jail and no private/live data use.

[Artifact 14307985889](https://github.com/WeepingAngeIs/Server-Jars/actions/runs/44036656345/artifacts/14307985889) downloaded ZIP SHA-256 independently matched published `4ee18032eaca3115b6fda3c81aa3a04d20439f11b19462cfc1e398127683d285`. The downloaded JAR was independently hashed and compared byte-for-byte with the earlier meeting-verified candidate: the entire file is identical. No production source changed, and no production build or unit suite was repeated. Final artifacts contain zero new unit-result files; the same binary's 69 unit passes remain scoped to meeting run `44031631588`.

The actual passing cases establish:

1. Dev receives only `dev-only` and `shared`; Visitor receives only `visitor-only` and `shared`. Actual screen rows contain only name/dimension fields, with no coordinates, owner UUID or other player's rows. An ordered observer check proves Dev's request does not open an unrequested destination screen for Visitor; after Visitor requests its own list, Dev's existing screen still has Dev's rows.
2. Pressing each real `shared` row reaches that caller's own authoritative home. Each success appends exactly one caller departure to the normal back history and starts only its home cooldown. A fresh actual home menu reports a remaining cooldown; pressing its active row again is refused without another movement, history entry or cooldown change.
3. Visitor's normal `home dev-only` command refuses without selecting Dev's location or changing state.
4. A still-open, previously allowed home menu cannot bypass a later HOME_ENABLED=false server setting. Re-enabling and opening a fresh menu permits normal travel.
5. Normal public warp selection works through the actual row command. A still-open, previously allowed warp menu refuses after WARP_ENABLED=false; a fresh re-enabled menu works again.

Refusal checks compare both players' positions, native cooldown map, saved homes/warps, private back histories, full native inventory/components including physical Lightman's Currency coins, XP, seeded earned/daily spin balances, personal quest/task completion and claims, and an unrelated party. Legitimate successes allow only the actor's expected location/back/cooldown effects. These checks do not certify every external bank currency.

The fixture drives actual production Button callbacks after asserting that the captured DestinationScreen remains the current screen. It does not restore closed widgets or invent a selector payload. This is native data/behavior evidence, not manual mouse-hit/layout certification or a full resolution/font matrix. No defect was reproduced, so no production fix was made. No Jail runtime or separate Jail build/certificate prerequisite was invoked.

## Exact unchanged candidate

Version remains **1.2.0**:

- SHA-256: `216de93ca87cc9e0a54f0f0991c675a1058ee95de3247c8efbebffc664f0c504`
- Raw SHA-512: `349284d438561c34d7005899cec9a80025ccd6bd3589f9b51c8768303cb22e60275ebf4ba7cb31acd2023bbaf9dabc414c5313db4ed0ee6856c167c8ae5c3ef2`

All candidate bytes, including existing features, fixes, settings resources, art and models, remain identical to the prior verified file. The [machine-readable results](UNYIELDING-DESTINATION-ACCEPTANCE-RESULTS.json) record the same exact source, candidate, outcomes and limitations.

## Preservation and limits

All heavy builds/runtime checks run on GitHub-hosted disposable clients/worlds with pinned public inputs. No installed profile, live world, private data, settings, hotbar, art or model file is modified or uploaded. Version remains 1.2.0; there is no installation or deployment.

The user's basic Mawile dropped-food pass, with Spice of Life Onion Diversity >= 8 inclusive, remains separately recorded and is not repeated. Jail and its earlier exact-pair certificate/gates remain independent and unchanged. Synthetic destination data and feature settings do not establish authoritative live rank/provider policy or full-pack compatibility. Actual old-save migration requires consistent real backup inputs; those inputs are not used here. Native config-audit/undo remains a separate available public-input gap; it is not tested alongside these destination cases. The acceptance index distinguishes that gap and other implementation-only entries from authoritative-input blockers rather than silently counting them as complete.
