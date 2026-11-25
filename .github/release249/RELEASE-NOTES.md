# Minecraft Jail 1.3.0-beta.4-test.1 — default inventory policy

Fresh/default configurations now leave inventory confiscation **Off**. Administrators can opt in using `jail.confiscate_inventory` in the existing server settings or the `confiscate_inventory` key in `minecraftjail-common.toml`.

Existing saved On or Off values are preserved. Each admission keeps the policy recorded when it began. Opting in does not confiscate an already admitted Off prisoner's inventory, and switching Off does not discard items already held under an On admission. Existing custody and return safeguards remain unchanged.

This is a focused default change from source `41a8c88f19d8436910a18f23ed247cfe6c97fae7`, JAR SHA-256 `f829bedc430edff21b6787e1d401724a2cb9d56d6ab144e74f2242b66bfb522c`. The release package verifies that `JailConfig.class` is the only changed JAR entry. The pending version remains unchanged; identify this candidate by its source commit and raw JAR checksum.

## Requirements and scope

- Java 21; Minecraft 1.21.1; tested NeoForge 21.1.249. Loader metadata permits NeoForge `[21.1.249,21.2)`; other loader versions are not covered by this focused run.
- Cobblemon `1.8.4+1.21.1` is required on both client and server.
- `unyieldingquests` is optional in loader metadata, on both sides. Loader optionality is not a custody compatibility guarantee.
- Matching Jail versions are required on both sides. This is not a server-only release.
- Certified admission remains disabled by default. The inventory policy does not enable admission or bypass certification, confinement, runtime, player-state, origin, wallet, equipment or journal checks.
- Actual retained-inventory admission remains bounded to the exactly reviewed roster and safe supported state. This run uses the approved public fixture with the pinned spawn-count Unyielding and Storage roommedia.1 JARs listed in `PROVENANCE.json`; it does not certify an arbitrary modpack.

## Verification evidence

The workflow builds once and performs one focused two-session hosted acceptance scenario: actual generated default-Off admission through the normal command, inventory retention and confinement, administrator opt-in, saved On across restart, and exactly-once return of an already-held transaction even after switching Off. The terminal evidence artifact determines whether these checks passed; packaging alone is not gameplay evidence.

The accepted cumulative evidence at https://github.com/WeepingAngeIs/Server-Jars/actions/runs/45485490905 is reused for unchanged escape, forced native respawn, lighting, policy transitions and compatibility safeguards. Its separate unapproved-roster negative is retained at https://github.com/WeepingAngeIs/Server-Jars/actions/runs/45482773316/artifacts/14962488355. Those broad scenarios are not repeated for this one-entry default change.

## Caveats before independent staging

Known native hosted-client shutdown crashes remain unresolved; successful gameplay assertions do not establish a clean native client exit. Private server packs, Windows TEST and occupied worlds are not certified by public disposable-world evidence. A different raw JAR changes the custody fingerprint: restarting a held transaction on this exact candidate is not proof that an occupied f829 installation can be upgraded safely. Resolve held custody on its verified installation before changing JARs or loaders; never bypass a held or mismatched transaction.

The mod metadata declares MIT, but applicable license grant/copyright text is absent from the supplied source and JAR. See `LICENSE-STATUS.md`. This artifact is prepared for independent verification by Stella; it is not a CurseForge publication, installation or authorization to bypass the remaining license/compatibility review.
