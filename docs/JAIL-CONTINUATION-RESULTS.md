# Jail continuation: resource preservation and migration preparation

Date: November 16, 2026.
Repository: WeepingAngeIs/Server-Jars.
Branch: codex/jail-standalone-extraction.
Production baseline: 4e3667a149d4e530bddb5326ee739ad386fe6a47.

## Scope and interruption audit

At resumption, the remote branch was still at the saved investigation report
7ddae89c8fd3e923e195698fbf505aec7999f69e. Its latest native lifecycle run
43950904155 had completed successfully. There was no newer unfinished GitHub
investigation run to resume. This does not inspect the inaccessible PC checkout
or certify that it has no uncommitted local work.

The unfinished work was safe nonempty-resource custody and actual-pack/save
migration, not extraction of the standalone Jail. This continuation changes only
Jail test fixtures, evidence validators, private-copy planning tools and documentation.
It does not continue the separate non-jail feature request.

No production JAR was rebuilt or replaced. Production source and certificate
comparisons against the baseline passed in every runtime job. The two unchanged
packaged artifacts were reused:

| Artifact | SHA-256 |
| --- | --- |
| Minecraft-Jail-1.2.0.jar | d3145b1bc8f04c0370db30ff10e87158b6c3c88c0fde68cb0f226d3b73f79baf |
| Unyielding-1.2.0.jar | 61f6add6c68364b1c9dda4486948faf8d104c45ca2db2c40199de64a04ea38f7 |

No installed JARs, live servers, real saves or artwork were changed.

## Completed cloud verification

Runtime workflow 43964276228 completed successfully at exact test commit
`a45102ced3b0ed11b20ad1b449302f2dff17a7a2`.

It ran three four-phase continuation groups (party, wallet, and both together)
and two three-phase unjailed native death controls (party and wallet):
**18 passing server phases in this run.** Each startup recorded the unchanged
loaded pair, separate ownership, absence of old integrated jail classes, loaded
non-jail components and the empty escape reward. All 18 identity records and the
five group summaries were checked again after artifact download.

The same workflow passed nine new evidence-validation tests and ten existing
archive preflight tests. Planner workflow 43965030188 completed successfully at
`f6d105fd67f6db8e7cf62a4d852a6c61d55a7684`, with eighteen new synthetic planner
tests. Total Python checks: **37**. The two exact test-source commits are separate;
these results are not a claim that a different production build was tested.

## New continuation results

The fixtures use newly generated test objects: a two-Pokemon party and a marked
physical leather wallet holding eleven named gold coins. Resource comparisons use
complete native serialized state, including active wallet contents and native
attachment/routing evidence, rather than merely object counts.

| Scenario | Party only | Wallet only | Party and wallet together |
| --- | --- | --- | --- |
| Repeated initial admission and release attempts | Refused without resource/equipment change or new custody | Same | Same |
| Actual disconnect/restart after refusal | Exact resources preserved | Exact resources preserved | Both resources preserved |
| Empty-resource positive control | Actual supported admission succeeded | Actual supported admission succeeded | Actual supported admission succeeded |
| Exact resources introduced externally after that admission | Resource state preserved | Resource state preserved | Both resources preserved |
| External lethal damage and escape attempts in unsupported custody state | Death blocked, no drops or resource mutation | Same | Same |
| Disconnect/restart in custody | Resource and protection preserved | Same | Same |
| Release of the existing transaction | Succeeded; original equipment restored, party untouched | Refused; custody remained quarantined, wallet unchanged | Refused; custody remained quarantined, both resources unchanged |
| Additional disconnect/restart after release attempt | Released state and exact restored equipment persisted | Quarantine and wallet contents persisted | Quarantine and both resources persisted |
| Repeated release after that restart | Refused with no duplicate grant | Refused with no duplicate grant; unsafe deaths still blocked | Same |

The controlled introduction stage is a synthetic fault probe, NOT a nonempty
admission or custody implementation. Only exact freshly generated fixture objects
were staged to establish an empty-resource positive control. No user data was
removed, recreated or transferred. Neither Pokemon nor wallet custody is newly
certified. In particular, the successful party release restores the ORIGINAL
supported transaction's equipment while leaving the externally introduced party
alone. It does not escrow and restore a nonempty party.

Wallet and combined release holds were not overridden. The saved native player,
origin/accessory and held-equipment state could not justify a verified restoration.
No conflicting slot was cleared, no receipt was rewritten, and no uncertain grant
was replayed to obtain a passing result.

## Actual native death controls

The unjailed selected Human/Warrior controls were rerun on the same immutable JAR
pair. A vanilla kill caused observed actual death, followed by disconnect, server
restart while dead, native respawn, and another disconnect/restart while alive.
The complete party stayed equal at every observation.

The exact physical wallet left its equipped slot and appeared as one matching
ground item. It remained there across dead restart, respawn and live restart.
Native pickup then moved that wallet into inventory and removed the ground copy.
This verifies bounded conservation, NOT secure jail custody or automatic wallet
restoration. A ground item would be exposed to pickup/despawn in normal play.

Jailed unsupported-state death attempts intentionally remained blocked. They must
not be described as passing nonempty-resource jail death/respawn support. These
controls do not certify arbitrary origins, world rules or a complete modpack.

## Decisions and remaining safety gaps

Nonempty-party and occupied-wallet admissions remain BLOCKED. No production safety
check, dependency pin, admission switch or runtime certificate was relaxed.

Pokemon transfer needs a supported storage backend with durable source/destination
receipts, complete UUID/order/state preservation, transaction ownership, collision
handling and tests at interrupted asynchronous-save boundaries. Vanilla playerdata
persistence does not prove that a separate Pokemon store has committed. In-place
preservation instead needs verified restrictions for send-out, riding, trades,
PC access, battles and mutation packets. None is established by a normal-death test.

Wallet custody needs a single authoritative physical slot across native attachment
and accessory aliases, exact stack components and nested contents, callback-safe
handoffs and durable receipts. Occupied/changed restore destinations must stop
recovery. Wallet ground-drop behavior cannot be used as a restoration strategy.
Bank links, CobbleDollars balances and other external economy accounts are separate
and remain uncertified. Abrupt-process-failure recovery for a new resource adapter
has not been established.

## Private-copy migration preparation

`tools/jail-migration/plan.py` adds a read-only planner above the existing archive
preflight. It reads the runtime certificate only from the exact approved Jail
binary, checks hashes before/after inspection, rejects unsafe/nonportable archive
paths, identifies unknown binaries and mixed/duplicate Jail owners, and requires
an independently supplied old integrated JAR identity. Reports omit local paths
and individual player filenames. The private-input CLI refuses GitHub Actions;
only synthetic unit tests run in this public repository's CI.

The planner does NOT extract files, launch a server, migrate a world or authorize
recovery. Even complete inventory evidence reports `launch_authorized: false`.
A reviewed artifact match is not a compatibility pass. Read-only inspection does
not establish backup consistency, complete external database capture or network
isolation.

Prepared real-copy matrix, all currently NOT_RUN:

1. Immutable input hashes and independent old-control/split-candidate working copies.
2. Exact old-runtime baseline, complete resource identity/state inventory and restart.
3. Empty-custody split startup and lifecycle checks, with unrelated data comparison.
4. Occupied legacy custody: changed fingerprints or uncertain receipts retain
   quarantine; never rewrite evidence to force acceptance.
5. Missing providers, changed destinations, duplicate ownership and uncertain saves
   refuse mutation/replay. Interrupted-write tests need a supported adapter first.

Actual current full-modpack compatibility: NOT_RUN.
Actual pre-split old-save migration: NOT_RUN.
No new exact current runtime or old-save copy was supplied for this continuation.
The historical October client manifest export is not a substitute.

Needed when available, privately: the exact current server pack with actual binaries,
loader identity, configs/defaultconfigs, scripts and datapacks; the separate matching
old runtime and integrated Unyielding JAR with its hash; and a consistent pre-split
world backup with playerdata, serverconfig, jail journals, Pokemon/economy storage
and any consistent external-store exports. Do not supply live credentials. Inspect
copied endpoints and establish private network-isolated execution before any launch.
Never upload real saves or private player data as public repository content or
public Actions artifacts.

## Evidence artifacts

| Group | Artifact ID | Downloaded ZIP SHA-256 |
| --- | --- | --- |
| Party continuation | 14276680806 | ceac49b7b897419eca62e515ce78b8fd6a75d6e2a2e7e1c34515dbae1e18f646 |
| Wallet continuation | 14275554715 | c1bcd68a659191b866d73233ed493dcda5718ff35560a4e9fc092171c5fbda0b |
| Combined continuation | 14275857782 | aaa6a3c76f876e96235974b10b1f0eda5258ac59089bfab92b8eb4f7869188a8 |
| Native party control | 14274904939 | bd8139e71a0ef02983a8b8264b7f8505178a01885bbd106d8a0453bc51112d47 |
| Native wallet control | 14276135458 | b93200f7fa717eb53172b54ee493f6a22bb9bfe48c072e3e81c95ee7a9620551 |
| Planner unit tests | 14273969772 | dd0bcbee8386c35d827cc2ac3ce97283b9473c733ed464697880f9a68aa7ca71 |

All six downloaded archive hashes were independently checked. Raw evidence and
incremental source are supplied separately from the unchanged production JARs.
