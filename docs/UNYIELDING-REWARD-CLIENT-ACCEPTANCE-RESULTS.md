# Independent connected-client reward acceptance

This follows the completed [ground-cleanup checkpoint](UNYIELDING-GROUND-ACCEPTANCE-RESULTS.md), report commit `c93c578a5507af7657c2351b95caeabf8d434f1d`. The initial candidate is the exact Unyielding-1.2.0.jar from [run 44019741055](https://github.com/WeepingAngeIs/Server-Jars/actions/runs/44019741055), artifact `14300532555`, SHA-256 `be397290bfbac8d8f0b1010d04daded458673eca2f97b6e0eff59272e2eec886`. Work is isolated on `codex/unyielding-reward-client-acceptance`; the release remains 1.2.0.

## Scope and existing evidence

The prior [named-credit recovery evidence](UNYIELDING-REWARD-RECOVERY-RESULTS.md) uses a fake player/private claim helper and six server phases across five restarts. It covers saved credit intents, missing balance snapshots, reset generations and reconciliation. It is not connected-client menu/token/network authorization evidence and is not counted again here. The passing ground, vein and party suites are also preserved without unnecessary repetition.

This fixture uses the actual assembled JAR, two real public loopback clients (Dev and Visitor), actual LuckySpinScreen status/button handling and the registered production SpinMenuAction/Claim payload handlers. Synthetic new player balances, starter flags, quests, item prizes and a newly provisioned journal exist only in the disposable world. Tokens used for successful actions are captured from real server responses; they are not minted reflectively. Test-only ordered server receipts establish production processing and response ordering before assertions.

Bounded coverage targets starter readiness despite a nonempty party; valid single-use payouts; repeated, foreign, stale, missing and forged tokens; reconnect invalidation; closing an active reel; disabled/empty balances; incomplete/private quest claims; duplicate named credits; and claim-mode validation. Assertions compare actual item components/counts, balances, actual player identity and journal debit/credit/claim records. This is not exhaustive feature testing or a crash-durability test.

## Verification record

Native verification pending. The workflow reuses the exact verified candidate and skips rebuilding/repeating unit tests while production is unchanged. Only a reproduced production defect permits a narrow source fix and the corresponding build checks.

## Separation and limits

Jail is neither built nor loaded and is not a prerequisite. The separate [bounded joint certificate](https://github.com/WeepingAngeIs/Server-Jars/blob/e4bdecaa5cfa4d8ca89e532d3c38804d6ed207cc/docs/JOINT-WALLET-UNYIELDING-CERTIFICATION.md), its exact older pair, branches, artifacts and gates remain unchanged. Independent Unyielding testing does not wait for Jail development or certification.

All heavy builds and runtime checks run on GitHub-hosted disposable worlds/clients using pinned public dependencies. No private/live data, installed profiles, settings, hotbars, models or art are modified or uploaded. There is no deployment. The user's reported basic Mawile feeding pass and precise Spice of Life Onion Diversity >= 8 threshold remain recorded in the ground report; no basic feeding retest is added without a regression reason.

A successful journal RETURNED record establishes that the delivery call returned. It does not establish an atomically durable item/PC receipt across power loss or a crash between separate stores. Authoritative live rank/provider policy, the full pack and real old-save migration remain unverified without the relevant authoritative inputs.
