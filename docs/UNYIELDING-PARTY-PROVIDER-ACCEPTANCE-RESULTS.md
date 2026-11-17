# Independent party-meeting provider and lifecycle acceptance

This follows the completed [reward-input checkpoint](UNYIELDING-REWARD-CLIENT-ACCEPTANCE-RESULTS.md), report commit `8b18dda72dd4745ed35381abcbc0a1072a8e13a7`. The initial candidate is the exact Unyielding-1.2.0.jar from [run 44024899650](https://github.com/WeepingAngeIs/Server-Jars/actions/runs/44024899650), artifact `14301829248`, SHA-256 `744be06c8fa5ad6001f1ba214608c4f7c1dde59664afe2bf8bf33405063512d8`. Work is isolated on `codex/unyielding-party-provider-acceptance`.

## Checklist and prior evidence

`unyielding-quests/IMPROVEMENTS-STATUS.md` item 2 leaves actual modpack/rank acceptance for opt-in party meetings open. This task targets public provider contracts and lifecycle boundaries without pretending those contracts are authoritative live policy.

The completed [27-case party confirmation checkpoint](UNYIELDING-PARTY-CONFIRMATION-RESULTS.md), run `43979575601`, source `09074d4789294c5b1e7946e21835edc83de0a39e`, concerns TeamManager ownership/disband confirmation. It is preserved and is not a meeting-travel matrix. Its reconnect/restart/progress cases are not repeated here.

An earlier meeting source fixture passed in [run 43812073792](https://github.com/WeepingAngeIs/Server-Jars/actions/runs/43812073792), source `dcc3c62edd45df42d3bd042f27feacf462cf21d0`, job `133115743506`. Its log reports permission, membership, officer, clear, dimension, unloaded chunk, obstruction, OPAC revocation, movement and cooldown. Artifact `14200658173` has published ZIP SHA-256 `e990971565449d2b4ae77733a9bf02d4222982b1474cfcb9ce554afaba6b380d`; this task has not independently downloaded that older archive. Current meeting source and fixture blobs match that source checkpoint, but it tested development classes and server-executed commands on connected players, rather than this exact assembled JAR and client-issued commands. Those ten basic cases are not repeated as a new broad matrix.

## New bounded contract

Two actual disposable clients issue production `/partymeet` commands over their native connections. A test-only NeoForge IPermissionHandler is registered through PermissionGatherEvent.Handler and selected by the normal generated `permissionHandler` server setting. It delegates unrelated nodes to the native default handler. Runtime assertions require that exact provider to be active. Real public OPAC 0.33.0 supplies claim checks. Test-only ordered receipts prove server processing before state assertions.

The bounded cases target explicit provider denials overriding operator/public defaults, provider exceptions, setter permission failure, explicit provider grants, execution-time permission revocation, a single ordered reentrant membership change, actual setter disconnect/reconnect, setter role/party lifecycle, disabled/re-enabled point retention and a native mounted caller. The reentrant case uses the public TeamManager.leave API during one setter-permission query. It represents a deliberately adversarial public callback contract, not a claim that the live server's provider behaves that way. No production change is justified until an actual case reproduces a fault.

Checks compare location, cooldown, meeting point, private home/back history and unrelated progress/economy/party data as appropriate after controlled lifecycle preconditions. No actual live rank assignments, claims or server configuration are imported. The existing documented meeting contract uses Unyielding policy and OPAC; other teleport restriction mods remain separately unverified.

## Verification record

Baseline source `9d2a3e3266805f932f5dfe4c472c68cfe346b21f`, [run 44030348339](https://github.com/WeepingAngeIs/Server-Jars/actions/runs/44030348339), job `133860513816`, ran all ten cases against the exact `744be...` candidate: **nine passed and one failed**. No production build or unit suite was repeated. Artifact `14304882521` downloaded ZIP SHA-256 independently matched published `a01119f1173bc803ca4e8ff1dba8f7c8c463823ea86083f5b007dc071cae6d6e`. The selected native provider identifier was `uq_bundle_smoke:meeting_contract`; a real setter disconnect and new connected player session were observed.

The single failure reproduced the ordered reentrant boundary. The provider trace records Visitor's two ALLOW queries, followed by Dev's setter-permission query invoking public TeamManager.leave(Visitor), which returned success. Visitor was then in its personal team, but had still moved from x=138.5 to the meeting at x=132.5 and received a new partymeet cooldown. Inventory/progress/home/point preservation checks ran before the cooldown assertion failed. This demonstrates stale membership during this explicit synthetic provider callback, not that the live provider performs such a callback.

The narrow production fix changes only PartyMeetings: obtain both permission answers, retain the second claim/destination validation and cooldown check, then take the final current membership, setter role, enabled-state and point-identity snapshot immediately before travel. No external permission/claim query follows that snapshot. A provider-triggered leave can no longer reuse the earlier membership boolean. Permission nodes/defaults, saved data, cooldown duration and opt-in configuration are unchanged.

The follow-up reruns the ten affected meeting cases because the execution-order change touches allowed and refused provider/lifecycle paths. It builds and runs the existing unit checks only because production changed. Ground, reward, vein and old destructive-confirmation suites are not rerun. Follow-up results pending. No Jail runtime or separate Jail build/certificate prerequisite is invoked.

## Preservation and limits

Heavy builds/runtime checks use GitHub-hosted disposable worlds and pinned public inputs. No files from installed profiles, live worlds, private data, settings, hotbars, art or models are modified or uploaded. No installation or deployment occurs, and version remains 1.2.0.

Prior ground, reward and vein reports/artifacts remain intact. Basic Mawile feeding is not retested: the user-observed pass and precise Spice of Life Onion Diversity >= 8 rule remain separately recorded. The earlier bounded Jail exact-pair certificate and its gates are not edited; independent Unyielding tests do not depend on Jail development/certification.

This is not exhaustive provider, callback, lifecycle, full-pack or old-save certification. Generated policy is test policy. Authoritative live policy, complete pack behavior and actual old-save migration remain unverified without the relevant authoritative inputs.
