# Jail production player-only integration: verified candidate

## Outcome and exact provenance

Continues test checkpoint `9f0058f27ef0495ec8fab67a998b07c82e9c252d` and report `36816aff5d96ab87a97f5285a41288c5f46e040f`. Initial branch and running/queued/waiting/pending checks found the expected head and no overlapping job. The update used an expected-head lease without force. Work is confined to `codex/jail-player-only-autorespawn`; the shared investigation branch and historical source/evidence remain unchanged.

**The behavior is now implemented in the actual production Jail source and compiled candidate JAR, not supplied by test adapters. Run `44099299150`, attempt 1, at exact source commit `7b2f6b34412403b0839f1be9b82e136d1d893b20` passed the build, all three runtime jobs and final exact-byte verification.**

**73 Jail unit tests, 11 Python certificate/metadata/split tests and 12 disposable runtime phases passed. Two automatic native player respawns completed; measured client Respawn requests were zero.** Independent verification of the downloaded archives also passed. No failed or canceled attempt occurred in this production-integration run. Earlier test-only runs are retained, not added to these counts.

Production integration/build was authorized. Deployment was not authorized and was not performed. No installed files, live worlds, real player data, private saves, artwork, model settings or unrelated Unyielding source were changed.

## Production implementation

`JailAutoRespawn` is a production `minecraftjail` event subscriber. After at least five death ticks it verifies the authoritative connected dead player, same intact/safe/claim-accessible jail, original custody/origin receipts, one durably HELD escape-death record, matching owner/transaction/digest, accepted death count and saved corpse. Retained-XP receipts are checked where relevant. It directly invokes the native server respawn handler and requires a distinct alive replacement, rebound connection, the same jail assignment and completed escape record.

The native handler's packet-shaped argument is constructed on the server. There is no transmitted client request, button/key input, client-side respawn implementation or global immediate-respawn gamerule change. Existing native clone and completion handlers still own receipt verification. A single uncertain invocation is not retried in that player session.

The original crossing detector still initiates escape death. Production enforcement also invokes the existing verified escape routine for a player found fully outside by tick/login enforcement. The one scoped owned-damage hit uses native `generic_kill`, resolving repeated escape during respawn grace. Existing invulnerability preflight, safe destination, item/origin audits, cancellable events, saved death evidence and journal ordering remain intact. Unsafe, canceled or ambiguous outcomes are not replayed.

`JailAdmissions` removes only its explicit nonempty-party refusal. Hardcore is additionally refused. All other XP, effects, abilities, attachment, equipment and equipped-wallet restrictions remain. Both admission settings remain off by default. The wallet comment now accurately states that Pokemon are outside Jail custody.

**Production Jail Java contains no Pokemon API, party/PC lookup, custody, storage interception, login fence or callback adapter.** The fixture seeds a synthetic Eevee before admission only to prove a nonempty party no longer prevents player-only jailing. It performs no subsequent party preservation, clearing or restoration. Earlier Pokemon investigation code is not loaded or resumed.

## Actual-artifact validation, not behavior supplied by the test mod

The new harness reuses the historical player stimulus and client observer, but never includes the old `PlayerOnlyJail` behavior adapters, their admission wrapper, enforcement wrapper or damage substitution. No production Java class is shadowed by a test source set. Runtime checks match loaded class resources to the compiled candidate archive.

Three passive observation mixins count successful production respawns, distinguish server automatic invocation from received client requests, and count actual outgoing client Respawn packets. They do not redirect, overwrite, cancel or replace behavior. The client observer reads dead/alive object identities and the current screen without sending input. Its earlier constant-zero field has been replaced with a measured outgoing counter.

All new tests run on GitHub-hosted disposable machines. The primary scenario uses three separate server JVM starts and actual Dev/Visitor loopback client processes. Movement is driven through the connected player's native movement handler with synthetic coordinates, not human keyboard input. The second fully-outside case uses the normal player-tick event. Reconnection is a new actual network connection after server restart, not a synthetic callback invocation.

## Exact passing coverage

| Suite | New passing coverage at the exact candidate commit |
| --- | --- |
| Production Jail units | 73 tests: the 65 existing tests plus eight artifact-binding tests. Zero failures, errors or skips. |
| Python checks | Six companion-certificate, one exact loader-metadata and four split/history-preservation tests. Eleven total. |
| Player lifecycle | Three phases with 21 named observations, two native automatic respawns, zero client Respawn requests. These are phase assertions, not 21 independent JUnit tests. |
| Original integrated custody/certificate | Five phases: admission/release and interruption/restart recovery, unknown-dependency refusal, altered-companion refusal, then verified recovery with approved bytes restored. |
| Original startup/terrain | Four phases: two packaged startups/quest-ledger restart checks, protected chest/ore/claim placement and recovery from simulated interrupted terrain placement. |

Primary outcomes:

- Default-off admission and packed-wallet settings refuse until explicitly enabled. The supported player with a nonempty party, inventory/equipment/accessories and an occupied bounded wallet then admits successfully. Wallet opt-in is still required.
- Boundary crossing causes exactly one character death and server-native automatic respawn inside jail. The actual client observes the dead object, a distinct living replacement and a cleared death screen. Its measured Respawn requests are zero. The historical screen-clear observation survives the deliberately later death.
- Release returns the complete tested inventory/equipment/accessory bytes and wallet coin-slot contents exactly. The reviewed Human/Warrior profile returns from temporary Human/Nitwit. Repeated release grants nothing. The escape achievement remains one-time and reward-free.
- Rapid readmission and a fully-outside position cause a second actual death. The player disconnects after the saved death but before the auto tick. After restart and real reconnection, the saved dead prisoner automatically respawns inside jail without a new death or client request, while new admissions are disabled.
- An occupied restoration slot is not overwritten. Removing only the synthetic conflict permits exact return. Player commands, teleport commands and outside damage remain restricted; console control still works.
- A damaged jail prevents the unsafe escape death. A canceled death remains unreplayed and prevents uncertain restoration. Another restart preserves that protective hold.
- Changed bytes in only the disposable Jail archive change its runtime identity and refuse release; restoring the exact original bytes restores the correct identity and permits the supported release path.

The first automatic respawn has explicit client death/replacement/screen-clear evidence. The reconnect recovery has native-server and real-connection evidence; it does not claim the reconnecting client first displayed a death screen before loading. The tests do not cover every unsafe-destination timing or every crash point.

## Certificate and build identity

The dependency certificate remains byte-identical because the reviewed dependencies and companion approvals did not change. Its SHA-256 is `e1aa5b7c583d50e9c74b05899df05138e9c52b3f6e29f1abe119d51602766dc0`. Loader metadata and registered production mixins also remain byte-identical. Their unchanged bytes were rechecked, not used to claim an old run certifies the new implementation.

`JailHumanAudit` now adds the actual Jail artifact's SHA-512 and policy `player-only-autorespawn-v1` to the custody runtime identity. The old implementation excluded its own JAR. An external candidate-identity manifest binds new bytes to source and build results; the new runtime report binds passing outcomes to those same bytes. No filename/version wildcard or dependency approval is added.

The negative self-artifact test appends bytes only to the disposable runtime copy, observes the changed fingerprint and refused release, then restores and rehashes the original bytes. This is an artifact/receipt binding test, not execution of malicious replacement classes or a signed authenticity system.

**Old-artifact custody fingerprints are not rewritten or silently approved. Occupied old-save migration remains unverified. An old active transaction can remain blocked because its recorded runtime differs. This is not a drop-in approval for an occupied jail.**

| Binary | Bytes | SHA-256 |
| --- | ---: | --- |
| New Minecraft-Jail-1.2.0.jar | 311297 | `c04a0564022ffdcfd1e9c0710ffd024260994498ea786cbab68711159fdc2883` |
| Required unchanged Unyielding-1.2.0.jar | 17109697 | `61f6add6c68364b1c9dda4486948faf8d104c45ca2db2c40199de64a04ea38f7` |

Jail SHA-512: `0c992e69bcd694b0121653879a2b3395902c374db47653dc743eba1df8fa2c5ca37b6c8b02013e56f2a53b9c972e5e539d9832b58a1dd08009b1e457da9f79a6`.

Unyielding SHA-512: `31b34a1c655c306d4317ae8a765eda6d7d2a5a691fe517e4c5cf45cd1dff7ba9c93ef03a37cea0810083adea1cdb938af610680d7001351b9873c5b4ae4c56e4`.

The pending-release version label remains 1.2.0. Hashes and the exact source commit distinguish this candidate from prior archives. Unyielding was not rebuilt or edited, and a newer companion from another chat is not approved here.

Seven production class entries changed or were added: JailAdmissions, JailArtifactBinding, JailAutoRespawn, JailConfig, JailEnforcement, JailEscapeCustody and JailHumanAudit. The other 133 archive entries are byte-identical to the reviewed prior Jail. Jar ordering and timestamps are reproducible.

## Independent verification and artifacts

Independent read-only verification matched all five downloaded GitHub archive digests, both JAR SHA-256/SHA-512 values and sizes, 86 production/test source hashes, 25 generated harness source hashes, all 12 startup byte identities, raw phase reports versus summaries/final gate, actual client connections, two automatic completion log records, measured zero client requests, historical screen-clear evidence, 73 JUnit XML outcomes and all 11 named Python checks. A separate byte comparison against the prior candidate independently confirmed the seven-entry change and 133 unchanged entries.

Artifacts for run 44099299150:

| Artifact ID | Content | ZIP SHA-256 |
| --- | --- | --- |
| 14336385159 | Build/source/units and candidate pair | `d2c2ed45795a24d81eed5ea96855d8c39c342d44a3249e56e265f40b36e804a5` |
| 14335503073 | Player runtime | `5d2b2280f05fb791552afa53074a9f76ab906e11b7d664e2b6a2655cc86b3d21` |
| 14336651848 | Integrated custody/certificate runtime | `924fad94e952ff37d0cc831a43675ff4757e3cecf4a09e9c35d9e979a4f6538c` |
| 14336960660 | Startup/terrain runtime | `ea3efe8165e13cca4075efce9a53771d47adda02a233c5343738ed4b7ada17fb` |
| 14335199041 | Final exact-byte candidate and validation | `401a1d69d1adbce37b4c9d426732eabf1fb4075495aaedb67deade74000f9a9a` |

## Remaining limits

The reviewed profile remains Minecraft 1.21.1, NeoForge 21.1.255 and Java 21 with exact approved dependencies, including Cobblemon metadata 1.8.4+1.21.1. No private full-pack compatibility or occupied old-save migration was run. No additional external capability blocks continued coding, but those private-copy validations require the exact inputs.

Existing bounded origin/equipment/wallet restrictions remain. The origin runtime test is Human/Warrior with temporary Human/Nitwit and restoration, not arbitrary non-Human origins. Physical-wallet coverage remains the existing one unequipped leather wallet in main inventory slots 9-35, six native slots, ordinary gold coins and supported names/lore, no upgrades or auto-exchange. Equipped wallets and external economy accounts are not newly certified.

Hardcore is refused in source but not exercised as a separate positive runtime case. The dedicated retained-XP runtime matrix was not rerun in this increment; its logic and existing unit coverage remain intact. No universal power-loss, arbitrary forced-death, unknown-mod compatibility or automatic resolution of uncertain transactions is claimed. Logs retain dependency/data warnings, deliberate negative-case errors and client-shutdown connection resets; passing assertions do not mean warning-free logs.

Historical Pokemon research remains stopped and preserved. This implementation confines and respawns the player character without promising Pokemon preservation. Nothing was deployed.
