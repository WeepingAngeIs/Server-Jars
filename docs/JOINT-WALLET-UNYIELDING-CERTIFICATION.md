# Bounded joint wallet / Unyielding certification

## Scope and isolation

This is disposable joint certification, not deployment or permission to enable live admissions. Work is isolated on `codex/joint-wallet-vein-certification`, forked from non-jail report `37da182759caacfa2346a0c4cd1e187056a753a7`. The ongoing standalone Jail Pokémon-custody branch/worktree is not edited, merged, interrupted or used as a moving input. Only completed checkpoint `8e9716d94a932fe5110ae84abdc90adfe0017974` supplies the wallet binary and existing test fixtures. The isolated branch changes no production Java sources or installed files.

## Exact inspected checkpoints

| Input | Tested source / evidence | SHA-256 |
| --- | --- | --- |
| Current `Unyielding-1.2.0.jar` | `51c23f468fa2af1510d93834946269298b7c83f6`; [run 44003918234](https://github.com/WeepingAngeIs/Server-Jars/actions/runs/44003918234), artifact `14293254287`; report `37da1827` | `7a0a1cec09001ca6f47ee6180371842f27ef6d83f7411a7e61fff6a83e74b414` |
| Wallet-only `Minecraft-Jail-1.2.0.jar` | `8e9716d94a932fe5110ae84abdc90adfe0017974`; [run 43991171187](https://github.com/WeepingAngeIs/Server-Jars/actions/runs/43991171187), verified-pair artifact `14287794515`; report `f5f68940ede91e7217af700c17379640416cf275` | `5c2653a9c14d3199581fdd67e05d055b334ec2746fe7047e3c80454eb732474e` |

Before certification, the downloaded current-Unyielding artifact ZIP matched published SHA-256 `f4ac41d0f501180c056ec894cdcd0927f075479d066e5abeb3c443c933555eb6`. The downloaded wallet verified-pair ZIP matched `cc3c6499429fb400149418fc0aae1b366f3eb2f0aa35c8dcc8c641f5641598bb`; integrated evidence artifact `14288093985` matched `67dbcf84cd12fea66b7737fecb04dc22b9cc8246a6a1ab163731921e66883ace`. The original wallet pair used Unyielding SHA-256 `61f6add6c68364b1c9dda4486948faf8d104c45ca2db2c40199de64a04ea38f7`; that older companion is reference evidence only, never the base for the current candidate.

## Narrow certificate overlay

The original wallet binary does not contain the current companion's SHA-512 pin. `.github/joint-certification/certify.py` checks both original artifact hashes and the completed wallet-runtime report, then constructs a separate copy adding exactly this pin:

`9a36367d2bd92a1a3acd570de31649c0db02de5aea32d2d933a323212ccb2a35be6fdb48bc7932771c95998121a25b0467a131c39ae879c5c6481154fdddcbd6`

Only `assets/unyieldingquests/jail-certified-runtime.json` may differ by content. Every Jail class, other resource, metadata and mixin descriptor remains byte-identical. Existing certificate fields, embedded proofs and historical approved pins are retained. No additional mod, origin, wallet or custody path is approved. ZIP metadata/compression is canonicalized, so the raw Jail hash changes; the original artifact is preserved.

The derived Jail is 304103 bytes, SHA-256 `43074f9301315e51a476f1072884f9d66a0c47b839734989693c938cbc160027`, raw SHA-512 `4529024ae8596c0619a685d9c2c7bad9e22ef04f0be57375291a970d6117826cfecdd92a7808bca779789cd3fc0b3cff2f654fd77e14440404d7e895acaec733`. Unyielding remains byte-identical to the current tested candidate above. Version stays 1.2.0. Preliminary pair artifact `14296506706` ZIP SHA-256 independently matched published `c887e5973680e025261b9199c99c986ce3007df2c68e24bff7204452605ae3d3`. Independent comparison against the original wallet JAR confirmed the exact one-entry/one-pin content difference. Successful assembly alone is not certification.

## Runtime matrix

The workflow runs the completed wallet fixtures from a detached, exact-SHA checkout inside each GitHub-hosted disposable runner. It stages only the two actual production JARs, changes the fixture's expected companion hash, and supplies the identical certificate resource for the existing package/source consistency check. No older Unyielding production code is built or substituted. The current non-jail fixture is separately run with both JARs loaded.

| Slice | Required evidence |
| --- | --- |
| Wallet core, five phases | Actual occupied-wallet admission/source removal, bound escrow, exact-slot/NBT return, occupied destination refusal, disconnect/restart, escape death/native respawn/restarts, no drops or duplicate return, thirteen unsupported admissions |
| Wallet faults, four phases | Saved removal and explicit recovery with admissions off, returned-state restart, hard JVM halt before player-save completion, actual restart preserving one saved source, uncertain transaction still quarantined without replay |
| Integrated, five phases | Normal registered jail/unjail paths and restart/recovery, genuine unapproved loaded dependency refusal, byte-altered companion refusal, approved bytes restored before explicit completion |
| Non-jail, 42 cases | Both exact JARs loaded; fresh certified/packed-wallet options off; current cold-input/Survival/durability, claim/tool protections, six shapes and crowded populated HUD matrix |

The non-jail regression runtime additionally loads the exact bonus-claims/PvP/minimap builds from the prior acceptance task. Those extra hashes are deliberately **not** added to the Jail certificate. Passing non-jail behavior in that runtime does not certify custody admission with those addons loaded. The wallet admission matrix uses its separately pinned, previously reviewed runtime.

Native execution and heavy preparation run on GitHub only. All worlds, players, wallet markers and faults are synthetic and loopback-only. No private saves, configurations, waypoints or server credentials are copied into the workflow or artifacts. Existing unit evidence is reused for unchanged binaries/code; this run must report its native phases independently rather than relabeling old passes as new tests.

## Verification status

[Run 44010514867](https://github.com/WeepingAngeIs/Server-Jars/actions/runs/44010514867), source `b77f51cad7165153dfe23fd341c89edb5c7a7d61`, completed all three custody slices successfully: core five phases (job `133796208803`), faults four (job `133796208906`), integrated five (job `133796208796`). All 14 startup identities and reports bind the actual loaded JARs to `43074f...` / `7a0a1c...`; no old companion was substituted. The core evidence records all thirteen refusal cases, `party_admission_enabled=false` throughout, default-off wallet admission, exact returned wallet/nested slots and occupied destination refusal. Fault evidence records saved-phase recovery with admissions off, actual disconnect/restart, hard JVM halt before player save, one saved original after restart and continuing quarantine without replay. Integrated evidence includes actual unapproved-dependency and altered-companion refusals.

Independent downloaded ZIP checks matched published digests:

| Evidence | Artifact | ZIP SHA-256 |
| --- | --- | --- |
| Core | `14297081794` | `5e5f5938d55446d1598aa03767f9b775cf122f01f6d7326a31fb13b0283038e5` |
| Faults | `14296658082` | `f00a3c975168347a06b48968e79684b9e4e4df5a133d0430e9a7a22f546ba50b` |
| Integrated | `14297605082` | `4566294aeefb308d95b6332169bbb47a037af39a3ac1deb72bee6faca6aeda43` |

The initial non-jail slice passed 41/42 cases; the overall workflow therefore correctly failed and skipped verified delivery. Failure was the allowed tilling plot's later starting-block assertion. Its server trace has uncancelled origin/extra item events, and its screenshot shows all three blocks tilled, while the later server assertion finds the starting block reverted. The fixture was dry and uncropped, leaving native farmland reversion between observations. Follow-up `849e5f38478b7894983d62f9bccec24b57c2f3b6` supplies nearby water in the disposable tilling setup, retaining the same action and all three transformation assertions, including protected negative controls. Neither production JAR changes. Initial failed-vein artifact `14296632232` ZIP SHA-256 matched `a65f5614e1794716bb72706bba916f81ecf526147829f55d4740f3e51bb0c6e6`.

[Follow-up run 44012194499](https://github.com/WeepingAngeIs/Server-Jars/actions/runs/44012194499), source `849e5f38478b7894983d62f9bccec24b57c2f3b6`, **passed**. Non-jail job `133801596809` passed all **42 native cases**, with **48 captured screenshots**. Final job `133804247101` passed the combined gate after independently requiring successful status, pinned evidence commit, complete reports and identical actual JAR hashes for the three custody slices. It does not count the initial partial non-jail pass as certification or relabel the custody tests as a new run.

Verified 2026-11-16 CST (2026-11-17 UTC). Independent downloads matched published hashes:

| Final evidence | Artifact | ZIP SHA-256 |
| --- | --- | --- |
| Non-jail follow-up | `14298070720` | `a8fa9eb16f2e79f311f3d050f8bc3dbedde34a9cfc6444799c6a5d35069e7c56` |
| [Verified bounded pair](https://github.com/WeepingAngeIs/Server-Jars/actions/runs/44012194499/artifacts/14297766364) | `14297766364` | `8950e90164a2a9ea3a88ae85fc58be9b16f59fb9b9db05218d836106444e8bfd` |

The final downloaded JARs independently rehashed to Jail `43074f9301315e51a476f1072884f9d66a0c47b839734989693c938cbc160027` and Unyielding `7a0a1cec09001ca6f47ee6180371842f27ef6d83f7411a7e61fff6a83e74b414`, exactly matching every approved phase's identities. `joint-runtime-verified.json` binds those hashes to both evidence commits, all 14 custody phases, 42 non-jail cases, the off-by-default admission flags and explicit deployment refusal. Local light verification also fed the actual initial 41/42 result to the final validator: it rejected the partial evidence and produced no verified report.

Final screenshots of the hydrated tilled plot, populated six-Eevee party with stacked startup toasts/chat, and completed Survival tool readout were visually inspected. The plot remains tilled for the server assertion; the vein panel stays clear of party/toasts, and `247/250` matches completed tool wear. Native non-jail evidence records both admission options off while both exact JARs are loaded. These observations remain bounded to the tested 640×480 / GUI 2 layout and scenarios.

This joint run reuses the original wallet checkpoint's 65 Jail unit tests, 69 companion unit tests and 15 Python build/certificate tests as prior evidence for unchanged code. The exact current Unyielding binary separately passed 69 unit tests in run `44003918234`. Those unit suites were not rerun or counted as new joint tests; the new joint proof is the native matrix and exact-entry/hash audit above.

## Supported and refused scope

The added companion pin supports only the existing reviewed contracts, including `native-packed-wallet-v1`: one unequipped Lightman's Currency 2.3.0.5 leather wallet in main inventory slots 9–35, six inner slots, zero bonus slots, auto-exchange off, ordinary physical gold coins, no bank link, and only supported native data plus optional names/lore. Native attachment, backing/cache, active and Curios wallet routes must be empty. The positive wallet profile remains the reviewed Human/Warrior profile with no retained XP. Complete original-slot serialized contents are escrowed; no balance conversion, normalization, merge, overflow drop or partial return is introduced.

Nonempty Pokémon parties, equipped wallets, unsupported variants/denominations, upgrades, bank/external accounts, retained-XP combinations, unknown profiles and unreceipted ambiguous outcomes remain blocked. Pokémon custody remains separate ongoing work. Existing verified-phase recovery with admissions off does not authorize replay of uncertain operations.

Actual authoritative claim/rank assignments, full-modpack behavior, arbitrary origins/equipment, external storage, old-save migration and rollback with active new-contract custody remain unverified. Consistent private old/current server copies and matching native/journal stores are required for migration acceptance; no live data is requested or published here. Newer Jail/Pokémon-custody builds are not covered by these exact hashes and need their own review and paired tests. No certificate is a replacement for those checks.

Installed JARs, existing release artifacts, live worlds/server settings and player data are unchanged. No deployment occurred and no live admissions were enabled. Both admission options remain default-off. Keep deployment held.
