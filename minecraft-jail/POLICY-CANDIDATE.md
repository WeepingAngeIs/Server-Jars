# Rosecourt admission policy candidate

This is a separate test candidate based on `547be9a6b2ce147f1afcd771eae420fe266eabd7`,
preserving the already dispatched successor to `41e42706fcc05e1ee55c336195e3c0120708d2b0`.
The version remains `1.3.0-beta.4-test.1`; identify each binary by its raw SHA-256 and Actions provenance.
No release, main merge, private world migration or Windows TEST installation is part of this work.

## Reconciled existing evidence

The already-dispatched run [45471190783](https://github.com/WeepingAngeIs/Server-Jars/actions/runs/45471190783)
completed successfully on attempt 1. It was not cancelled or rerun.

| Probe | Actual companion SHA-256 | Native spawn results: single / count 5 / alias 5 / level 5 count 3 | Jail result |
|---|---|---|---|
| [Baseline job 138719268265](https://github.com/WeepingAngeIs/Server-Jars/actions/runs/45471190783/job/138719268265) | `e4f34f6a4640fa6c59fc8efe3d2253d0b701bd83371fd960c5d38cfac73ccf2c` | 1 / 1 / 1 / 1 | Refused unapproved roster; inventory and custody unchanged |
| [Spawn job 138719267827](https://github.com/WeepingAngeIs/Server-Jars/actions/runs/45471190783/job/138719267827) | `9dbfb659a2ba2c995d0f3974fa48cf9f37b9b5f8162c3e2e4faf2af9946e0904` | 1 / 5 / 5 / 3 | Refused unapproved roster; inventory and custody unchanged |

Terminal artifacts: baseline `14956404179`, spawn `14956283769`, comparison `14958442533`.
These are actual distinct-entity observations. Successful installation alone is not a spawn-count result.
The companion `61f6add6c68364b1c9dda4486948faf8d104c45ca2db2c40199de64a04ea38f7` was a setup input, not the runtime binary of either probe.

The earlier published 1.0.14 + roommedia.1 pass [45400948626](https://github.com/WeepingAngeIs/Server-Jars/actions/runs/45400948626)
used runtime companion `ec3ae2c48399e40ffa615ddec85408bc29b2479c5a35eb7236c3e036ebdf762c`.
It covered admission, escape, native no-button respawn, a marked upgraded bag with 13 marked shards,
restart, exact original-slot return and held/stale recovery controls in a disposable fresh world.

The successful same-loader occupied migration in [45405036526](https://github.com/WeepingAngeIs/Server-Jars/actions/runs/45405036526)
used NeoForge **21.1.255** in a synthetic predecessor world. Its changed-loader **21.1.249** case passed by refusing safely.
Neither result proves occupied private-server or Windows TEST compatibility.

## Exact new roster boundary

The preserved compatibility successor adds one complete server file-set pin:
`56172e09426f12aa24a50db27bc03755ce6d7186370febe8ba745acc597dbbee`.

Its relevant raw inputs are:

- Previous Jail: `8e4e710979d82fc3e12c1931fea3f718173e1efb52bc6e3aa86a02b65ef8aaea`, product source `2c4e7e47a5d939885dd8285e3f792fe99903aca7`.
- New companion artifact `14925506112`: `9dbfb659a2ba2c995d0f3974fa48cf9f37b9b5f8162c3e2e4faf2af9946e0904`.
- Storage roommedia.1: `afd62612c2f09c6bf156831b0e798da58ef0fd1632297dc936ea69e11b66e91e`.
- Minecraft 1.21.1 / NeoForge 21.1.249.

The comparison companion's roster `8e0a25e7a8454bc38fffc25596f6a4c26820dd1295bcaf27a13a7240c0906d43`
remains unapproved. Same filename or mod version never substitutes for the exact bytes.
Runtime verification is still required; adding the pin does not itself establish a gameplay pass.

The successor product was built at `085e2fba2b5b3bba71d028e7da64dd502590d921` in
[45475105117](https://github.com/WeepingAngeIs/Server-Jars/actions/runs/45475105117),
artifact [14959401594](https://github.com/WeepingAngeIs/Server-Jars/actions/runs/45475105117/artifacts/14959401594).
Its raw Jail SHA-256 is `a8b76f433f36dc3e1607bcd9526832386973a7eaa007ae150307fa0334bafec0`.
It passed 143 unit tests and the unapproved-roster control. The first connected-player attempt
recorded server admission and automatic respawn, but lacked the matching client observation;
restart was skipped, so that attempt did not establish complete custody acceptance.
The already-dispatched follow-up [45478889426](https://github.com/WeepingAngeIs/Server-Jars/actions/runs/45478889426)
reuses those exact bytes and completed successfully. Terminal artifact
[14963670001](https://github.com/WeepingAngeIs/Server-Jars/actions/runs/45478889426/artifacts/14963670001)
records one client kill packet, one respawn packet, one replacement object, zero client respawn actions,
20 alive no-modal ticks, and server replacement after five ticks. Restart, held recovery refusal,
occupied-return conflict safety and once-only original-slot backpack/wallet/equipment return passed.
Its artifact also contains client native crash reports at teardown; the gameplay assertions do not
establish a clean client shutdown. This compatibility evidence is separate from the policy candidate.
No dispatched run was cancelled, rerun or duplicated by this takeover.

## Pending teleport admission guard

The reviewed companion has an ephemeral `TeleCmds.REQUESTS` queue. An outside recipient can accept
an old outgoing request, so command-actor restrictions alone do not establish confinement.
The new guard reads the exact known request shape and refuses live incoming or outgoing participation
before admission. It never removes requests. Unknown schema, inaccessible state and oversized audits
fail closed. Expiry matches the companion: age exactly 60 seconds is live; greater than 60 seconds expires.
The inherited ongoing-custody check permits incoming-only requests: the existing command/payload
fences stop the prisoner accepting them, and an outsider cannot use a new request to block release
or native escape recovery. A live outgoing request remains unsafe because an outsider could accept it.
The policy candidate shares one bounded, read-only parser between that check and new admission;
it does not change the companion queue or relax any malformed-state refusal.

## Inventory policy

`jail.confiscate_inventory` defaults to **true** in the existing server-loaded Jail config spec.
The existing Configtopia admin UI supplies the server-authoritative OP-gated editing path.
The master `certified_admissions` switch and all independent admission safeguards remain required.

- On uses the existing confiscation and return contract.
- Off uses a separate retained-inventory confinement contract with no item escrow journal or return staging.
- Off is enabled only for the one exact reviewed spawn-companion and roommedia.1 roster; older On-certified rosters are not implicitly certified for native retained-item death.
- Each admission snapshots its policy. Missing policy on historical records means On.
- Switching Off does not discard or withhold existing held items.
- Switching On does not confiscate inventory from an already active Off session, including after restart.
- Off is bounded to reviewed safe packed contents with empty hands, armor and accessories; it does not admit arbitrary unsafe inventory providers.
- The intended retained-item escape path uses vanilla retention only for a journal-bound Jail death and its verified native clone; it does not change the world gamerule.

## Hosted acceptance

Only `.github/workflows/jail-policy249.yml` on `codex/jail-inventory-policy-249` owns the candidate run.
All jobs use standard `ubuntu-24.04` GitHub-hosted runners in this public repository.
The candidate is built once, hashed, then supplied unchanged to the gameplay jobs.

Keep these results distinct:

1. Exact-roster On custody, marked backpack/wallet, no-button respawn with light churn, restart and once-only return.
2. On-to-Off and Off-to-On policy changes during active custody, persistent snapshots and retained-item safety.
3. The unapproved-roster negative, including actual refusal without inventory/custody changes.

The workflow artifacts and their terminal assertions determine pass/fail. This source note does not claim a completed acceptance result.
The private Windows pack has not been reconstructed or uploaded; published-pack tests cannot certify it.
Policy flips and restart checks use custody created by this exact candidate. They do not authorize
replacing the JAR in a world already occupied under `8e4e7109...`: that predecessor's artifact binding
has not been added to the migration allowlist or verified by these fresh-world jobs.
