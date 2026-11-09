# Server-Jars
Jar creations

## Folders

* `unyielding-quests/` - the quest mod source (code + quest book). GitHub Actions builds and tests the jar in the cloud.
* `releases/` - built jars + install notes. [Unyielding 1.1.7](releases/Unyielding-1.1.7.jar) includes the complete custom Pokémon pack and riding for all nine species, alongside the existing Unyielding, quests and currency bridge. Lunavee, Buzzeon, Ghost Jirachi and Creepyeon include corrected models, sizes, icons and level-up moves. Replace the older combined file on both server and clients; remove older Mawonna/custom Pokémon ZIP packs that would override these embedded assets. Keep external dependencies and unrelated resource/song packs. See [installation](releases/Unyielding-1.1.7-Install.txt) and [passed validation](releases/Unyielding-1.1.7-Validation.txt). Existing armor balance, raid IV odds, music controls, spin odds, loot tables, crates, tutorial and quest settings remain included.
* `riding-update/` - reproducible assembly, imported asset credits, and actual server/client checks for the complete custom Pokémon JAR.
* `tutorial-update/` - cloud build and checks for player-guide permissions, quest controls, collection-task layout and admin settings.
* `currency-bridge/` - cloud assembly and compatibility checks for the CobbleDollars and Lightman's Currency integration.
* `combined-unyielding/` - reproducible cloud assembly and checks for the single-file Unyielding and Quests update.
* `astra-art/` - Astra's folder: the art list, a ready resource pack to drop PNGs into, and upload instructions (`READ-ME-FIRST.txt`).
