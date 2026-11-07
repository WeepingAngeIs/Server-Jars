# Unyielding 1.1.0 combined release

This update belongs to the existing [Unyielding CurseForge project](https://www.curseforge.com/minecraft/mc-mods/unyielding)
(project ID 1766517). It combines the supplied `Unyielding-Mawonna.jar` with the quest module in one installable JAR.

The base is the user's approved Unyielding **1.0.8**, already extended with Mawonna gameplay.
Neither the older installed 1.0.7 nor the separate 1.0.9 Substitute variant is used.
`release.json` pins the supplied base checksum. Its crafting restrictions are preserved.

Quest component **1.6.3** retains 1.6.2's admin settings and personal progress protection.
It adds `/quests daily` for login-reward status and leaves `/daily` to Unyielding Ranks
when `curanks` is installed. The standalone quest mod retains its `/daily` alias when Ranks is absent.

## Installation

Requires Minecraft **1.21.1**, Java **21**, NeoForge **21.1.248 or newer** (tested on 21.1.255),
and **Cobblemon 1.8.4** (the declared range is 1.8.4 through versions below 1.9).
Install the same combined file on the server and every client.

With Minecraft/server stopped, replace the previous Unyielding JAR and separate
UnyieldingQuests JAR with `Unyielding-1.1.0.jar`. Do not leave duplicate copies of either
module installed. This includes a separate Mawonna companion JAR if one was installed.

The Mawonna Pokémon assets and species definitions still come from the matching
`Mawonna.zip`: keep it in server datapacks and client resourcepacks. This release combines
the Java mods; it does not replace or rewrite that pack.

All eight mod IDs stay unchanged: `cusoultome`, `curanks`, `cugiant`, `culeaderboard`,
`cuportal`, `cubalance`, `mawonna`, `unyieldingquests`. Existing configuration filenames,
registry namespaces, saved-data keys and quest IDs therefore remain the same.

Open the quest book with J or `/quests`. Admins use `/quests settings` or Teams → Admin.
Quest sharing defaults to OFF, and existing player progress is preserved.

## Assembly and verification

All compilation, assembly and gameplay checks run on GitHub-hosted runners.
The workflow builds and checks the quest component, then runs:

```text
python combined-unyielding/build_bundle.py
```

The packager rejects input hash changes, signed archives and unexpected overlapping paths.
It combines the two NeoForge metadata files, sets an explicit quest component version, and
writes a valid combined manifest. Every other file from both input JARs is verified byte-for-byte.
All five base mixin configurations, the access transformer and the 923 quest-art PNGs remain intact.

The final JAR is tested using a separate cloud test harness, so the runtime loads the packaged
production classes. Test helpers are excluded from the distributed JAR. Workflow artifacts
contain the assembly report, checksums, logs and screenshots.
