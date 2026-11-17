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

Native verification pending. The workflow reuses the exact verified candidate and skips rebuilding/repeating unit tests while production is unchanged. A production fix requires a reproduced issue. No Jail runtime or separate Jail build/certificate prerequisite is invoked.

## Preservation and limits

All heavy builds/runtime checks run on GitHub-hosted disposable clients/worlds with pinned public inputs. No installed profile, live world, private data, settings, hotbar, art or model file is modified or uploaded. Version remains 1.2.0; there is no installation or deployment.

The user's basic Mawile dropped-food pass, with Spice of Life Onion Diversity >= 8 inclusive, remains separately recorded and is not repeated. Jail and its earlier exact-pair certificate/gates remain independent and unchanged. Synthetic destination data and feature settings do not establish authoritative live rank/provider policy or full-pack compatibility. Actual old-save migration requires consistent real backup inputs; those inputs are not used here. Other remaining public-input opportunities and authoritative-input blockers are distinguished in the acceptance index rather than silently counted as complete.
