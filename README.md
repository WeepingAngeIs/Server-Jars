# Server-Jars
Jar creations

## Folders

* `unyielding-quests/` - the quest mod source (code + quest book). GitHub Actions builds and tests the jar in the cloud.
* `releases/` - built jars + install notes. The combined `Unyielding-1.1.5.jar` includes Unyielding, quests and the currency bridge, with remembered tutorial dismissal and a dedicated in-game spin percentage editor, editable loot tables and crates, in-game config values, working EMI quest shortcuts, compact collection tasks and the earlier guide/art/admin improvements. Replace the older combined file and remove standalone copies of its modules; keep external dependencies and existing packs.
* `tutorial-update/` - cloud build and checks for player-guide permissions, quest controls, collection-task layout and admin settings.
* `currency-bridge/` - cloud assembly and compatibility checks for the CobbleDollars and Lightman's Currency integration.
* `combined-unyielding/` - reproducible cloud assembly and checks for the single-file Unyielding and Quests update.
* `astra-art/` - Astra's folder: the art list, a ready resource pack to drop PNGs into, and upload instructions (`READ-ME-FIRST.txt`).
