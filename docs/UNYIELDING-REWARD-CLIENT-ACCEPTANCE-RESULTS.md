# Independent connected-client reward acceptance

This follows the completed [ground-cleanup checkpoint](UNYIELDING-GROUND-ACCEPTANCE-RESULTS.md), report commit `c93c578a5507af7657c2351b95caeabf8d434f1d`. The initial candidate is the exact Unyielding-1.2.0.jar from [run 44019741055](https://github.com/WeepingAngeIs/Server-Jars/actions/runs/44019741055), artifact `14300532555`, SHA-256 `be397290bfbac8d8f0b1010d04daded458673eca2f97b6e0eff59272e2eec886`. Work is isolated on `codex/unyielding-reward-client-acceptance`; the release remains 1.2.0.

## Scope and existing evidence

The prior [named-credit recovery evidence](UNYIELDING-REWARD-RECOVERY-RESULTS.md) uses a fake player/private claim helper and six server phases across five restarts. It covers saved credit intents, missing balance snapshots, reset generations and reconciliation. It is not connected-client menu/token/network authorization evidence and is not counted again here. The passing ground, vein and party suites are also preserved without unnecessary repetition.

This fixture uses the actual assembled JAR, two real public loopback clients (Dev and Visitor), actual LuckySpinScreen status/button handling and the registered production SpinMenuAction/Claim payload handlers. Synthetic new player balances, starter flags, quests, item prizes and a newly provisioned journal exist only in the disposable world. Tokens used for successful actions are captured from real server responses; they are not minted reflectively. Test-only ordered server receipts establish production processing and response ordering before assertions.

Bounded coverage targets starter readiness despite a nonempty party; valid single-use payouts; repeated, foreign, stale, missing and forged tokens; reconnect invalidation; closing an active reel; disabled/empty balances; incomplete/private quest claims; duplicate named credits; and claim-mode validation. Assertions compare actual item components/counts, balances, actual player identity and journal debit/credit/claim records. This is not exhaustive feature testing or a crash-durability test.

## Verification record

Baseline test source `1afb007025daf8a0d2f85102210bf9d465e44ee0`, [run 44022982101](https://github.com/WeepingAngeIs/Server-Jars/actions/runs/44022982101), job `133836521063`, ran all sixteen native cases against the exact `be397...` candidate: **fifteen passed, one failed**. The workflow did not rebuild unchanged production or repeat its passing unit tests. Artifact `14302555521` was downloaded; its ZIP SHA-256 independently matched published `bb88cb1f30eab52273452d982ebdc83225aff827282f8b3d25f1e7b6fcccd543`.

The failure is a reproduced input-validation defect. A real network client sent Claim modes -1 and 3 for an eligible completed quest. QuestManager treated the undefined mode as claim-all: Dev's earned balance increased from 6 to 7, two native marked diamonds were delivered, and the quest-credit/claim records changed. Visitor's rewards remained unchanged. This is unintended caller reward claiming, not a demonstrated cross-player privilege escalation. The real disconnect/reconnect was observed, and all starter, token, native delivery, daily priority, empty/disabled balance and legitimate/private claim cases passed.

The narrow production fix rejects modes outside 0, 1 and 2 before reward or progress handling. No token, prize-selection, journal, starter, packet format or save schema change is needed. The follow-up builds because production changed, and targets the four Claim network cases plus a new complete quest-credit-to-redemption/replay path. The twelve passing unchanged token cases are preserved as evidence on their exact baseline binary, not relabeled as new checks on the changed candidate. Follow-up results pending.

## Separation and limits

Jail is neither built nor loaded and is not a prerequisite. The separate [bounded joint certificate](https://github.com/WeepingAngeIs/Server-Jars/blob/e4bdecaa5cfa4d8ca89e532d3c38804d6ed207cc/docs/JOINT-WALLET-UNYIELDING-CERTIFICATION.md), its exact older pair, branches, artifacts and gates remain unchanged. Independent Unyielding testing does not wait for Jail development or certification.

All heavy builds and runtime checks run on GitHub-hosted disposable worlds/clients using pinned public dependencies. No private/live data, installed profiles, settings, hotbars, models or art are modified or uploaded. There is no deployment. The user's reported basic Mawile feeding pass and precise Spice of Life Onion Diversity >= 8 threshold remain recorded in the ground report; no basic feeding retest is added without a regression reason.

A successful journal RETURNED record establishes that the delivery call returned. It does not establish an atomically durable item/PC receipt across power loss or a crash between separate stores. Authoritative live rank/provider policy, the full pack and real old-save migration remain unverified without the relevant authoritative inputs.
