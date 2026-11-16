# Minecraft Jail: extracted implementation

This module continues the jail implementation from `Server-Jars` commit
`dc3699ab04e775bd772c38b9ff24646e0791f81a`. It is not a replacement implementation.
The original commit and branch remain intact. `extraction-manifest.json` records
all moved files; the source verifier permits only explicit ownership/import
changes to the 39 existing jail classes, four mixins and 14 unit-test classes.

## Scope and status

This is a **code-only extraction candidate, not a runtime-certified release**.
The split workflow builds both outputs, executes unit tests, compares non-jail
binary entries against an independently assembled pre-extraction build, and
checks each JAR's own application-class dependencies. It does not start Minecraft,
create a game world, connect to a server, install mods, or alter any real saves.
Consult the exact-commit `verification.json` artifact for completed checks.

The original procedural room, chair, barred cell, terrain preview/backup checks,
command and movement enforcement, no-reward escape advancement, custody journals
and origin adapters are retained. Their post-extraction gameplay behavior is not
claimed verified by unit or packaging checks. No art redesign is included.

## Outputs and dependencies

* `Minecraft-Jail-1.2.0.jar`: module ID `minecraftjail`, Java 21, Minecraft 1.21.1,
  NeoForge 21.1.255 build target, required Cobblemon 1.8.4. It contains no Unyielding
  quest/reward/Pokemon implementation classes. The version matches the pending
  assembled release; it does not bump the Unyielding or quest component version.
* `Unyielding-1.2.0.jar`: the current assembled Unyielding output with only jail
  code, four jail mixins and four jail resources removed. Non-jail features and
  the original quest component version 1.6.9 remain in place.

The build checks these outputs separately, not merely their combined classpath.
NeoOrigins, accessory/Curios, claim and wallet handling still use the original
bounded runtime inspections. Absence or unsupported state is not treated as
successful compatibility. Compile-only dependencies are not embedded in either
new output.

## Safety boundary: deliberately not bypassed

Admission defaults to **off** in the new `minecraftjail-common.toml`. An enabled
setting from the old Unyielding config is not imported. The existing
`/jailintegrated check` and explicit admission-mode controls remain, and enabling
that switch does not override any runtime, inventory, equipment, origin, wallet,
XP, claim, callback or journal check. Nonempty Pokemon parties are still refused;
no Pokemon are confiscated, moved or deleted by this extraction.

**Splitting the bundle changes the runtime trust boundary.** The runtime audit now
excludes only its own `minecraftjail` JAR, not the separate Unyielding companion.
The old certificate is copied byte-for-byte; the new companion is not silently
added to it. Therefore admission alongside the newly assembled Unyielding JAR can
be refused as an uncertified runtime. Existing transaction fingerprints can also
cause recovery to hold for explicit review. That is a safety limitation, not a
reason to edit out a check, replace a certificate, or replay uncertain custody.
Runtime certification and live recovery are outside this code-only task.

The standalone entry point rejects an old Unyielding JAR that still owns jail
classes/mixins before opening a world. Use of both an integrated old JAR and this
module is not supported. Do not install this candidate into an occupied prison
or real save on the strength of compile-only evidence.

## Preserved persistent identities

No save migration is run. The existing identities remain:

* SavedData `unyielding_jail` and existing `integrated-jail-v1`,
  `custody-interface-v1`, placement-backup and escrow paths/receipt keys.
* Block ID `unyieldingquests:custody_vault`, still registered exactly once,
  now by this module. Its resource namespace is intentionally unchanged.
* Advancement `unyieldingquests:jail_escape`, preserving existing progress and
  the original impossible trigger with no reward object.
* Permission node `unyieldingquests.jail.manage` and existing command names.

Retaining those identifiers does not establish that existing live custody can
be migrated without a dedicated runtime/recovery test.

## Cloud/command-line build

From the repository root, with Java 21:

```sh
python3 .github/scripts/fetch-build-dependencies.py
python3 .github/scripts/fetch-build-dependencies.py minecraft-jail/libs
(cd unyielding-quests && sh gradlew --no-daemon build)
(cd minecraft-jail && sh gradlew --no-daemon build)
python3 .github/scripts/assemble-118-followup.py
python3 .github/scripts/verify-jail-source.py
python3 -m unittest discover -s .github/scripts -p 'test_*.py' -v
```

The independent jail module shares only the repository's Gradle wrapper tooling.
It does not require Unyielding as a runtime dependency. Its build declares no game
run configurations. The source verifier requires the base commit to be available
in Git; shallow CI checkouts fetch that exact commit first.

The split CI additionally builds the original commit in a detached worktree,
then invokes `verify-jail-split.py --before <original-assembled-jar>`. A successful
artifact records the tested source commit, per-module JUnit totals, artifact
checksums, changed integration entries and preserved non-jail entry count.

## Tests and remaining work

The 14 existing jail unit-test classes are moved with their packages, not replaced.
A new five-case unit test covers rejection of integrated/ambiguous companion
JARs. Static checks verify source provenance, untouched non-jail sources,
separate class closure, no duplicate application classes, metadata and resources.

Sixteen original launch-based fixtures plus the ground-smoke jail geometry check
are retained under `src/legacySmoke/`. That directory is deliberately not a
configured source set and cannot leak into either production JAR. Those fixtures
still describe the old integrated topology; porting and executing a two-JAR
launch/restart harness is pending explicit authorization to launch Minecraft.
Historical jail/full-regression workflows must not be treated as split-JAR test
evidence. The new split workflow is the code-only verification entry point.

Art phase: deferred. Existing geometry and visual resources were not redesigned.
