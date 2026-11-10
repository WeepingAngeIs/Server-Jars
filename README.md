# Server-Jars
Jar creations

Unyielding 1.1.10 includes the approved custom Pokemon ZIP edits, animated Mawonna portraits/map icons, Inkyeon's still face portrait and animated map icon, Frilligoo's original Sliggoo face, and Ghost Jirachi's exact below-half-health belly eye. It adds live /config controls, custom map-icon scaling, operator Day/Night/Weather inventory buttons, optional global admin claim bypass, and the latest Mawonna/Frilligoo map artwork. Heavy builds and game checks ran on GitHub-hosted runners.

## Folders

* `unyielding-quests/` - the quest mod source (code + quest book). GitHub Actions builds and tests the jar in the cloud.
* `releases/` - built jars + install notes. [Unyielding 1.1.10](releases/Unyielding-1.1.10.jar) includes the complete custom Pokémon pack and riding for all nine species, alongside the existing Unyielding, quests and currency bridge. All four friends' move lists are now available at level one. Admin Mawonna Flower keeps separate stats and has 862 eligible moves. Replace the older combined file on both server and clients; remove older Mawonna/custom Pokémon ZIP packs that would override these embedded assets. Keep external dependencies and unrelated resource/song packs. See [installation](releases/Unyielding-1.1.10-Install.txt) and [validation and test limits](releases/Unyielding-1.1.10-Validation.txt). Existing armor balance, raid IV odds, music controls, spin odds, loot tables, crates, tutorial and quest settings remain included. Current [source and reproducible build](https://github.com/WeepingAngeIs/unyielding-mod/blob/main/docs/settings-release.md) are in unyielding-mod.
* `model-hotfix/` - source and verification notes for the Fairy Sliggoo and Inkyeon asset repair.
* `riding-update/` - reproducible assembly, imported asset credits, and actual server/client checks for the complete custom Pokémon JAR.
* `tutorial-update/` - cloud build and checks for player-guide permissions, quest controls, collection-task layout and admin settings.
* `currency-bridge/` - cloud assembly and compatibility checks for the CobbleDollars and Lightman's Currency integration.
* `combined-unyielding/` - reproducible cloud assembly and checks for the single-file Unyielding and Quests update.
* `astra-art/` - Astra's folder: the art list, a ready resource pack to drop PNGs into, and upload instructions (`READ-ME-FIRST.txt`).
