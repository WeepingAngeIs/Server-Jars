# Independent ground-cleanup multiplayer acceptance

This continues the current jail-free Unyielding candidate from report `37da182759caacfa2346a0c4cd1e187056a753a7`, tested source `51c23f468fa2af1510d93834946269298b7c83f6`. Baseline Unyielding-1.2.0.jar SHA-256 is `7a0a1cec09001ca6f47ee6180371842f27ef6d83f7411a7e61fff6a83e74b414`. Work is isolated on `codex/unyielding-ground-acceptance`. Version remains 1.2.0.

Jail is neither built nor loaded and is not a dependency of these checks. The completed [bounded joint certificate](https://github.com/WeepingAngeIs/Server-Jars/blob/e4bdecaa5cfa4d8ca89e532d3c38804d6ed207cc/docs/JOINT-WALLET-UNYIELDING-CERTIFICATION.md), its exact pair and artifacts are preserved; this task does not edit Jail work, certificates or gates. Independent Unyielding testing does not wait for Jail development or certification.

## Why this acceptance target

The existing `GroundCleanupSmoke` runs source classes and invokes `ItemEntity.tryToMerge` reflectively. Its age/category checks do not establish native cross-player drops, naturally ticked merges, multiplayer network authorization, reconnect/session behavior or a saved-world restart against the current assembled JAR. Cleanup is destructive, so those remaining boundaries have priority over repeating already-passing reward and vein suites.

The separate fixture uses two actual loopback clients, Dev and Visitor, and the actual assembled JAR. It drives `GroundSettings.Action` through the registered network handler, actual client drop requests and native pickup/merge ticks. Fixed positions and injected old ages are controlled preconditions; this is not a long-duration unattended world test. No reflected merge call, fake player or production test helper is used.

## Exact public addon inputs

Two installed food-mod filenames and SHA-512 values were matched read-only to official public releases. No private configurations, server policy, saved hotbars or player data were read or uploaded.

| Public input | Version ID | Representative runtime food ID |
| --- | --- | --- |
| [Farmer's Delight 1.21.1-1.3.4](https://modrinth.com/mod/R2OftAxM/version/XTVZDOol) | `XTVZDOol` | `farmersdelight:hamburger` |
| [My Nether's Delight 1.21.1-1.10.4.1](https://modrinth.com/mod/O53VhQoZ/version/SGFJBL5q) | `SGFJBL5q` | `mynethersdelight:nether_burger` |

The runtime manifest records exact public filenames/URLs/hashes, and preparation additionally requires these installed-file hashes:

- Farmer's Delight SHA-512: `1f6f8796469f747cff36f477b4f589a3d69613882399f9ee7f86db36c7bca9aa4828731f43db15dc6c7f5a4c1bbe3d4092a232e8cef010b09cec0581848fda90`
- My Nether's Delight SHA-512: `36df146974d70fe5f7c92c4aeef613d1e0192efbb550faa88efaa8170212c07f9fc5938961d1b8da489355579910e95fea2cd5ae714b614ce17f1b8e352f0bbf`

The actual item registry must resolve both food IDs and their native `minecraft:food` components. Lightman's Currency 2.3.0.5 is the existing pinned public dependency; the wallet fixture contains eighteen physical gold coins in slots 0 and 5 of six native inner slots, with auto-exchange off. Its full serialized stack/components are compared, not just its item ID. An ordinary iron sword given a native food component tests gear-before-food priority without creating a new registered item.

## Baseline verification

Fixture source `648b3abde9826b6e02d4585517f18aab9206062d`, [run 44015990494](https://github.com/WeepingAngeIs/Server-Jars/actions/runs/44015990494), reuses the exact current JAR from artifact `14293254287` rather than rebuilding unchanged production code or rerunning its 69 passing unit tests. Phase 1 reported twelve passed assertions and three failures; strict failure handling stopped before phase 2, which is **not counted**. Artifact `14298454211` downloaded ZIP SHA-256 matched published `0518c9aea805fd7154c67a57a4a5d396e15c3912a92f5d583ac255e7f36bb50e`.

Two initial failures checked preview marking after the client had sent its action, without proof that the server had finished handling it. The third assumed a 60+8 ground pair would partially merge into 64+4. Native behavior refused that overfull merge. Fixture correction `c9aaf5c` adds a test-only ordered server-processing receipt after each action, and asserts the native overfull refusal followed by clearing only the old, previewed 60 while preserving the separate fresh 8. The production JAR remains unchanged. Initial network-case “passes” are not final acceptance until verified with the stronger receipt.

The corrected baseline, source `c9aaf5cadb5103b5dfe47c31ef2bb541fbf016d8`, [run 44016843418](https://github.com/WeepingAngeIs/Server-Jars/actions/runs/44016843418), passed thirteen phase-1 cases and exposed two remaining failures. Artifact `14299686891` downloaded ZIP SHA-256 matched `c9737148acb4d1eb78d0ff758966f8c2d232df5417f04d51d679a2e28f0adbf4`. It still stopped before phase 2.

The reconnect case now demonstrated an actual production defect: a completed server preview survived a real disconnect and a new ServerPlayer session. The new session retained operator permission and confirmed before the original 30-second deadline; server-side confirmation deleted the previewed stack without a new preview. The original-UUID merge case also timed out; the fixture moves players closer to the plot and requires actual entity ticks, with explicit failure diagnostics. No production merge change is inferred from that fixture failure.

## Narrow fix and follow-up

Source `662310d` changes only production `settings/GroundSettings.java`: remove the logging-out player's pending confirmation, and release pending entries associated with a stopping server. It leaves scoreboard settings, candidate marks, category rules, age grace, functions, protocol and other players' confirmations unchanged. A new session must request a fresh preview. This also avoids retaining a stopped server through a stale pending record.

Because production changed, [run 44017869818](https://github.com/WeepingAngeIs/Server-Jars/actions/runs/44017869818), source `662310d8ad42069c92250e0ec1b4982e567dfc9d`, built Unyielding and passed all **69 unit tests** with zero failures/errors. Its native phase 1 passed fourteen cases, including the corrected reconnect refusal; the first merge fixture still failed its readiness/preview precondition. Phase 2 did not run. Artifact `14299688317` ZIP SHA-256 matched `a0aeec44d6a316a8df11705100c4d70822f60fbce17bc319e90b1d815e3b7e36`.

Fixture-only [run 44018787770](https://github.com/WeepingAngeIs/Server-Jars/actions/runs/44018787770), source `46650005b9ac9a35a0059416d637530caa7f718e`, required the first item to become ticking/selectable before preview. It again passed fourteen phase-1 assertions, but specifically showed the initial plot item never reaching that readiness state. Artifact `14300825246` ZIP SHA-256 matched `bce6e8d02131986830901574553591e550e213cf1adf70b978a850303671577a`. This is not presented as a production merge defect or complete matrix pass.

The final fixture revision waits for both clients' loaded-world signals and server player ticks, then proves the teleported plot is entity-ticking using a disposable marked probe before starting any cases. The probe is removed, and normal transformation assertions remain intact. Revisions after `662310d` reuse its exact unit-tested candidate instead of rebuilding unchanged production or repeating those unit tests. No Jail build or certificate is involved.

## Final verified checkpoint

Verified 2026-11-16 CST (2026-11-17 UTC). Test source `b27836ca718f71dc24f9b4e66d2f4775f02f8589`, [run 44019741055](https://github.com/WeepingAngeIs/Server-Jars/actions/runs/44019741055), job `133826113746`, passed **all 17 cases across two actual server phases**: fifteen before restart and two after restart, zero failures. Each phase records the actually loaded JAR hash, absence of Jail and no private/live data use. Both plots became ready after 45 native ticks; readiness is a fixture prerequisite, not a skipped acceptance case.

[Artifact 14300532555](https://github.com/WeepingAngeIs/Server-Jars/actions/runs/44019741055/artifacts/14300532555) downloaded ZIP SHA-256 independently matched published `fc62785837a45074d7d7d0ecc1a6dd4efe956aa7e4f93b12801f96847d15ef1e`. The downloaded final JAR independently matched the candidate hash below and both native phase identities. Its unchanged production code had already passed the 69 unit tests in run `44017869818`; those are separate unit evidence, not 69 new tests in the fixture-only final run.

Newly passing behavior:

- Naturally ticked merges with either the original or fresh UUID surviving, including an actual Nether Burger; youngest-age protection and complete component/count conservation.
- An overfull 60+8 pair remains separate; confirmation clears only the old previewed 60 and preserves the fresh 8. Old+old merges invalidate the changed preview count.
- Actual Dev and Visitor drop requests produce a native merged stack of six; the real scheduled cleanup tick preserves its fresh age. Actual native pickup and redrop replace the old entity UUID/age, so the prior preview cannot remove the redrop.
- A later admin preview supersedes the earlier one; nonoperator preview/config/confirm payloads and confirmation after operator revocation cannot delete items. Another operator's changed category setting invalidates the old preview.
- Actual Farmer's Delight/My Nether's Delight foods are selected under the tested food setting; the packed coin wallet and food-component iron sword remain exact under disabled other/gear categories.
- Cancelled, reconnected-session and exhausted-epoch confirmations refuse deletion. The reconnect case observes a real offline interval and new ServerPlayer, with confirmation before the original preview deadline.
- A real saved-world restart preserves the marked stack and disabled automatic setting; the stale confirmation refuses, while a new postrestart preview/confirmation works normally.

No UI screenshot/layout claim is made by this item-safety fixture. No already-passing reward/vein/party suite was relabeled as new ground-cleanup testing.

## Candidate integrity

The changed candidate remains version 1.2.0:

- SHA-256: `be397290bfbac8d8f0b1010d04daded458673eca2f97b6e0eff59272e2eec886`
- Raw SHA-512: `3ec6df4c0ec4c985c48ec45c04927fe27ca3ffdca45dac53394daa010711c0cec1f5893d95704597517085f015b846306e269ba4281d87ca6ccfd08ca833689f`

Every JAR entry was compared with the verified `7a0a1cec...` baseline. No entries were added or removed. Only `GroundSettings.class`, `GroundSettings$Pending.class`, `GroundSettings$Action.class` and `GroundSettings$Sync.class` differ; nested-record differences include shifted source-line metadata. Other feature classes, cleanup functions, settings resources, art and models are byte-identical. The existing joint certificate continues to describe its recorded earlier pair; no certificate or hash allowance is rewritten in this task.

## Mawile user observation

Anya's firsthand test update on 2026-11-16: Mawile runs to dropped food and eats it; she reports that the requested threshold-based food handling works and considers basic Mawile behavior good. The precise threshold term is **Spice of Life Onion Diversity ≥ 8**, including exactly 8, not hunger. This is user-observed basic behavior, not exhaustive automated/full-pack certification. No basic Mawile pickup/eating test was repeated without a regression reason.

Ground-cleanup classification is a separate feature using the native food component. These cleanup checks do not redefine or certify Mawile's meal-diversity rules. Mawonna classes/resources remain byte-identical in this candidate.

## Remaining limits

This is a bounded sample of native/representative addon items and operator permissions. It does not certify all pack item entities, event handlers, foods, component mutation paths, partial pickup permutations, unloaded chunks, arbitrary custom dimensions or the full live pack. Actual authoritative rank/provider assignments and claim/server policy are unavailable; generated defaults and client settings are not treated as those inputs. No actual old-save migration or private backup restoration is performed.

Party-meeting provider-denial/lifecycle coverage and connected-client reward-token replay/starter/network cases remain independent follow-up opportunities. Those public/synthetic contract tests can proceed without waiting for private live-server inputs; real-policy and full-pack claims still need authoritative inputs. Prior party, reward and vein evidence retains its own exact binary/test scope and is not relabeled as new ground-cleanup coverage.

All heavy runtime work uses GitHub-hosted disposable worlds and clients. Local work is source editing and lightweight inspection/hashing. No installed JAR, live world, setting, player data, release artifact, artwork or model setting is changed. No deployment occurs.
