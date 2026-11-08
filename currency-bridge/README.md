# Unyielding 1.1.1 currency integration

Adds the user's finished Cobble Currency Bridge 1.0.0 to the verified Unyielding 1.1.0 release.
The bridge registers CobbleDollars as a currency in Lightman's Currency. The existing quest
module and other Unyielding features are retained.

The supplied planning document is background only. This release preserves the actual bridge
implementation, its `uwucobblecurrency` mod ID, and its currency identifier; it does not implement
every optional feature described in the old plan.

## Installation

Install `Unyielding-1.1.1.jar` on the server and every client. Replace the previous Unyielding
JAR. Remove the separate Cobble Currency Bridge JAR and any separate UnyieldingQuests JAR,
because those modules are included in the combined file. Keep existing datapacks/resourcepacks.

Required separately:

- Minecraft 1.21.1, Java 21, and NeoForge 21.1.248 or newer.
- Cobblemon 1.8.4 (declared range below 1.9).
- Lightman's Currency 2.3.0.5 or newer.
- CobbleDollars 2.x (tested with 2.0.0+Beta-6.1 for 1.21.1).
- Dependencies required by those mods, including Kotlin for Forge.

These external mods are not bundled in Unyielding. Both the server and clients must have
the same compatible currency mods. The finished JAR is for the existing
[Unyielding CurseForge project](https://www.curseforge.com/minecraft/mc-mods/unyielding).

## Build and verification

All assembly and Minecraft checks run on GitHub-hosted runners. Input SHA256 hashes are pinned
in `release.json`. Only the combined mod metadata and outer manifest change; every other file
from both source JARs is checked byte-for-byte. All nine mod IDs are retained, and test helpers
and third-party mod code are excluded from the release.

The cloud workflow loads the actual combined release and tests currency registration,
currency-handler transfers, client integration, and the existing Unyielding tutorial/admin behavior.
These focused checks do not replace an end-to-end multiplayer test of every trader or auction flow.
