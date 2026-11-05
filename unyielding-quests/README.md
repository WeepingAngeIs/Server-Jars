# Unyielding Quests

The quest / teams / essentials / vein-mine mod for the Cobblemon Unyielding modpack
(Minecraft 1.21.1, NeoForge 21.1). Replaces FTB Quests, FTB Teams, FTB Essentials and FTB Ultimine.

* `src/main/resources/defaultquests/` - the quest book that ships in the jar (FTB Quests SNBT format).
* `src/main/resources/assets/unyieldingquests/textures/quest_art/` - Astra's quest pictures (file name = quest id).
* `tools/questgen/` - Python scripts: `migrate.py` applies the edits to the team's FTB export, `build.py` adds the generated chapters.
* `libs/` is not in the repo: put `cobblemon.jar`, `emi.jar` and `kotlin-stdlib.jar` there to build.

Build: `./gradlew build` -> `build/libs/UnyieldingQuests-<version>.jar`. Releases on this repo carry the built jar.
