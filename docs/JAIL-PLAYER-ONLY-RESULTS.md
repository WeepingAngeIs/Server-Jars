# Jail player-only escape death and automatic respawn: verified test-only implementation

## Outcome

Anya superseded the Pokemon custody/storage/login/callback work. This increment leaves Pokemon to their ordinary mod behavior and implements only player escape death and automatic native respawn inside Jail. Existing investigation source and evidence remain preserved, not resumed or deleted.

**Run 44092346048 at exact commit 9f0058f27ef0495ec8fab67a998b07c82e9c252d succeeded in the runtime job and final gate. Three disposable server phases, 19 required named runtime observations, two automatic native player respawns and 65 original Jail unit tests passed. There were zero client Respawn requests and zero JUnit failures, errors or skipped tests. Independent downloaded-evidence verification also passed.**

This is a working test-mod implementation around the unchanged reviewed JAR pair, NOT production inclusion or deployment. No replacement production JAR is provided.

## Isolation and implementation

Initial branch and executing/queued/waiting/pending checks found shared codex/jail-standalone-extraction at 5948ccabbfc24f81e620f7e3504e0906e4a50108 and no active branch runs. Work is on the separate codex/jail-player-only-autorespawn branch. The shared branch remains unchanged. This avoids overlap; it is not a claim of direct messaging to another worker.

All new code is under .github/player-only/ and its dedicated workflow. No production source, certificate, registered production mixin or non-jail source was changed. The test harness wraps the exact existing JailAdmissions boundary, deleting only its explicit nonempty-party loop. All original non-party checks remain, with exact reflective access to the same private XP check after package isolation. Hardcore is additionally refused. The original boundary blob is 5b68646b5bbbe6b1a7d89f1fbf7f775625bf824b.

The existing movement-crossing path starts the journal-bound escape death. A test-only enforcement hook also covers a player found fully outside the cell by tick/login enforcement. Both call the original item/origin/journal-safe escape routine. Its single owned player hit uses native generic_kill damage, scoped to that exact call, thread, owned-damage token and expected amount. This fixed the rapid second escape that the original generic-damage path prevented. Ordinary damage elsewhere and death cancellation are unchanged.

After five death ticks, the coordinator verifies the authoritative connected dead player, the same intact/safe/claim-accessible jail, original custody/origin receipts, exactly one HELD death attempt, matching owner/transaction/digest, death count and saved corpse. It invokes the server's native player-respawn handler. There is no client packet, button, key input or global immediate-respawn gamerule change. Existing clone/respawn handlers retain their receipt/completion checks. The replacement must be a different alive player object with rebound connection, inside the same jail, and with the escape record completed.

Generated runtime adapters contain no Pokemon API, party/PC lookup, custody, storage interception, login fence or callback adapter. No retired Pokemon investigation mod is loaded. The fixture creates one synthetic Eevee before admission to prove a nonempty party does not block this path. It does not clear, confiscate, restore or assert preservation of that party afterward.

## Verified runtime coverage

Three independent JVM server starts use actual Dev/Visitor loopback client processes. Movement is driven through the connected player's native movement handler with synthetic coordinates, not human keyboard input. The second out-of-bounds case uses the normal player-tick event. Reconnect is an actual new client connection after server restart, not synthetic callback invocation.

| Scenario | Passed result |
| --- | --- |
| Defaults and admission | Admission and packed-wallet options default off. Disabled modes refuse without mutation. Explicit supported mode admits the player with nonempty party, occupied bounded wallet and equipment. |
| Escape death and autorespawn | Boundary crossing kills once. Server creates native alive replacement inside Jail with zero client Respawn requests. |
| Client observation | Actual client observes dead object, distinct living replacement and cleared death screen. The historical event survives the deliberately later death. Client observer sends no inputs or packets. |
| Release | Complete stored inventory/equipment/accessory bytes and physical wallet/coin-slot contents return exactly. Tested Human/Warrior profile returns from temporary Human/Nitwit state. Repeated release grants nothing. |
| Repeated escape | Rapid readmission and fully-outside tick causes a second actual death. Player disconnects after durable death, before the automatic tick. |
| Restart/reconnect | Saved dead prisoner automatically respawns inside jail on real reconnect, with no new death and zero client Respawn requests. This works while new admissions are disabled. |
| Restrictions and reward | Player commands, teleport commands and outside damage remain blocked; console control works. Escape achievement is one-time with no reward. |
| Restoration conflict | Occupied wallet destination is not overwritten. Removing only the synthetic conflict permits exact release. |
| Unsafe jail | Damaged destination refuses lethal consequences/automatic respawn. |
| Canceled death | Native event cancellation is honored. Uncertain death is not replayed or released; quarantine persists through another actual restart. |
| Startup and regressions | Exact separate JAR pair and non-jail components load at all starts, old integrated jail classes are absent, and all 65 original Jail units pass. |

The 19 runtime observations are assertions across phases, not 19 independent JUnit tests. Earlier passes are not added to final counts.

## Failed attempts and corrections

- 44087483251 at 6e3e8d609f06ca354f325a3204a2bcaaca39f865: 65 units and compilation passed, but startup failed from test-helper/production split Java package. No gameplay phase passed. Fixed by separate test packages and exact original private-check invocations; production visibility and safety logic unchanged.
- 44088726575 at 57633917273d23bc05f3b9510adfe1dd025627df: first native escape/autorespawn/release worked, rapid second generic-damage hit did not kill and retained quarantine. Whole run failed; no completed phase counted. Scoped native kill damage fixed this case while preserving cancellation.
- 44090060852 at 933195877deecb73570edf0fc4280f4d2f463eff: all three phases and 65 units passed. Independent review found the final current-screen observation described the deliberately later death. The server had already required screen clearing before proceeding, but explicit historical client evidence was preferable.
- 9f0058f27ef0495ec8fab67a998b07c82e9c252d changed only the client observation recorder/checker. Run 44092346048 passed with a retained per-respawn screen-clear event. Server behavior was unchanged. Failed/partial/intermediate evidence is retained separately.

## Independent verification and unchanged pair

Downloaded runtime/final-gate archive digests, exact source commit, all nine incremental code/test-file hashes, six generated runtime/helper hashes, retained original admission checks, three startup identities, raw phase reports versus summary, two auto markers, zero client requests, actual client joins, shutdown readiness, the client historical event and all JUnit results were independently checked. The final gate matches the raw runtime report.

| Unchanged binary | SHA-256 |
| --- | --- |
| Minecraft-Jail-1.2.0.jar | 5c2653a9c14d3199581fdd67e05d055b334ec2746fe7047e3c80454eb732474e |
| Unyielding-1.2.0.jar | 61f6add6c68364b1c9dda4486948faf8d104c45ca2db2c40199de64a04ea38f7 |

Runtime artifact 14332911505: 292536 bytes; SHA-256 38d5412aabd3b472436831f206fb9e2865a9556524c9c2b9b6d7777ecbbe8dbe.
Final gate artifact 14332227948: 2282 bytes; SHA-256 5f3b0399b53ec4a133292dd84647636e1f8b5dd4c109297cdb0e0fb917fee8d8.

## Remaining limits

Production inclusion remains unapproved and unperformed. Existing installed Jail has not silently acquired test-mod behavior. Full private modpack and occupied old-save migration were not run. The existing Human/Warrior origin and bounded physical-wallet support are not arbitrary non-Human origin, equipment, attachment or external-account certification. Hardcore is refused, not positively tested here. Ambiguous/canceled deaths or unsafe destinations retain protection rather than being forcibly replayed. These boundary tests do not certify unrelated forced deaths or physical power loss.

No Pokémon preservation or modification is implemented or promised. All previous Pokemon research is stopped and preserved. All builds/game launches ran in disposable hosted GitHub environments. No installed files, live worlds, real player data, private saves, artwork, non-jail features or model settings changed. Nothing was deployed.
