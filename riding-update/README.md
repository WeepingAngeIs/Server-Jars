# Custom Pokémon update: Unyielding 1.1.7

This cloud-only assembly extends the pinned, accepted Unyielding 1.1.6 JAR. It embeds the pinned Mawonna pack's species, models, normal/shiny textures, abilities, evolution data and ring assets, and adds riding for all nine custom Pokémon. Existing quest, rank, currency, balance and other components are preserved.

The provided `cu-rideable-1.3.0.jar` is a source reference for riding behavior templates. Only nine custom targets are added. Its 982 native/other-addon additions and four native full-species overrides are **not** included. The `curideable` lowcode module is not registered inside the combined Java mod; ordinary data resources load through the existing combined JAR. The existing nine production mod IDs and versions remain unchanged.

| Species command ID | Riding movement | Template |
| --- | --- | --- |
| `mawonna` | Ground | Mawile |
| `mawonna_flower` | Ground | Mawile |
| `covislight` | Flying and ground | Corvisquire |
| `fairy_sliggoo` | Hovering and ground | Jirachi and Sliggoo |
| `inkyeon` | Ground | Sylveon |
| `lunavee` | Ground | Eevee |
| `buzzeon` | Flying and ground | Butterfree |
| `ghost_jirachi` | Hovering | Jirachi |
| `creepyeon` | Ground | Sylveon |

All custom seats use the model's `middle` locator. Normal and shiny versions share riding behavior.

Install the same verified Unyielding 1.1.7 JAR on server and clients, replacing the previous combined Unyielding JAR. A separate Mawonna.zip and old individual custom Pokémon packs are no longer needed; remove/disable their older copies so they cannot override the embedded fixes. Existing external dependencies, including Cobblemon, remain required. The standalone cu-rideable mod is independent and optional for its broader native-species riding; this update does not replace its non-custom coverage.

`build_bundle.py` pins the base and source pack hashes, rejects unexpected collisions, merges the shared language file without changing existing keys, and verifies unrelated original payload bytes remain unchanged. `prepare_smoke.py` reuses the accepted server/client/currency/admin harness. It removes the external model pack before startup, verifies all nine resolvers and normal/shiny textures from the combined JAR, and checks the four friends' size and level progression. `CustomRidingSmoke` uses the connected cloud player to mount each of the nine owned species in normal and shiny form and checks passenger and saddle state. It does not simulate a long player-controlled flight or a full-modpack multiplayer session.

All assembly, compilation, and game checks are restricted to GitHub-hosted runners. Publish the release only after the workflow passes. No live server or local game installation is changed by this workflow.
