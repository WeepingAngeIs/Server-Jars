# Server-Jars
Jar creations

## Folders

* `unyielding-quests/` - the quest mod source (code + quest book). GitHub Actions builds and tests the jar in the cloud.
* `releases/` - built jars + install notes. The combined `Unyielding-1.1.6.jar` includes Unyielding, quests and the currency bridge. Admins can edit armor balance, raid IV odds and Reactive Music track volumes from `/quests settings`, alongside spin odds, loot tables, crates and other server settings. Earlier tutorial, guide and quest controls remain included. Replace the older combined file and remove standalone copies of its modules; keep external dependencies and existing packs. Install the update on both server and clients.
* `tutorial-update/` - cloud build and checks for player-guide permissions, quest controls, collection-task layout and admin settings.
* `currency-bridge/` - cloud assembly and compatibility checks for the CobbleDollars and Lightman's Currency integration.
* `combined-unyielding/` - reproducible cloud assembly and checks for the single-file Unyielding and Quests update.
* `astra-art/` - Astra's folder: the art list, a ready resource pack to drop PNGs into, and upload instructions (`READ-ME-FIRST.txt`).
