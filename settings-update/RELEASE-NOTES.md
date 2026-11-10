# Unyielding 1.1.10 - draft release and install notes

Built and tested on GitHub-hosted runners. See [validation](../releases/Unyielding-1.1.10-Validation.txt) for checks and practical limits.

## Map artwork

- Mawonna and Mawonna Flower use the new transparent animated map artwork. The previous animated team portrait stays unchanged.
- Frilligoo uses the supplied new static map artwork. Its portrait and 3D model stay unchanged.
- Inkyeon keeps its right-facing animated map icon and still face portrait.
- One map-size setting applies to all nine custom species, including Corvislight. It does not resize portraits, 3D models, or other Pokemon.

## Settings and inventory controls

Settings require operator permission level 2. The server checks permission again for every change and every inventory-control click.

Run **`/config`** to open **Server settings**. The existing `/quests settings`, quest-book sidebar, and **Admin** button in the Teams window open the same menu.

For map size, choose **Config values  ->  Map icon size**, click the current number, enter the new value, then **Save**. Default **1** keeps the normal size; **0.5** halves it and **2** doubles it. Allowed range: **0.25-4**. The change applies to everyone and is synchronized when players join. Xaero Minimap is required to display map icons.

The main **`/config` → Server settings** list has these new switches:

| Setting | Default | When switched OFF |
|---|---|---|
| Home commands | ON | Blocks home commands; keeps saved homes. |
| Return with /back | ON | Blocks `/back`; keeps and continues recording destination history. |
| Warp commands | ON | Blocks warp commands; keeps saved warps. |
| Random teleport | ON | Blocks `/rtp` and new random-location searches. |
| Teleport requests | ON | Blocks request/accept/deny commands and cancels pending requests. |
| Player nicknames | ON | Blocks `/nick` and shows real names; keeps saved nicknames. |
| Recording tags | ON | Blocks `/rec` and hides tags; keeps saved recording states. |
| Nearby-player lookup | ON | Blocks `/near`. |
| Admin inventory controls | ON | Hides and disables the Day, Night and Weather buttons. |
| Admin claim bypass | OFF | Removes this automatic bypass; existing manual OPAC modes remain separate. |

Switches save automatically and take effect live. Detailed command settings are under **Config values  ->  Player commands**. The existing `disabled_commands` list still requires a restart.

The new inventory buttons are visible only to operators. **Day** sets time to 1000 and **Night** to 13000 across all dimensions, matching vanilla `/time set`. **Weather** cycles clear  ->  rain  ->  thunder  ->  clear for ten minutes in the current skylight dimension. Nether/End clicks report that weather is unavailable. Hover over Weather to see the next state indicated by the client.

**Admin claim bypass** optionally uses Open Parties and Claims. Turning it ON automatically allows online operators through claim interaction protection; turning it OFF removes the automatic pass. Losing operator status or leaving the server also removes that pass. OPAC remains a separate dependency for this feature; claim ownership and existing manual bypass modes are unchanged.

## Install

1. Close Minecraft and stop the server.
2. Put the same **Unyielding-1.1.10.jar** in both server and client `mods` folders. Keep only one combined Unyielding JAR; replace 1.1.9 and remove duplicate standalone copies of bundled modules.
3. Keep normal pack dependencies, including Cobblemon, NeoForge, CobbleDollars, Lightman's Currency, the relevant food mods, and any existing rideable coverage. Keep Xaero Minimap for map icons and OPAC for the optional claim switch.
4. Disable older Mawonna/friends test resource packs and datapacks that override these bundled assets, including copies loaded by a server or `global_packs`. Keep unrelated packs. Restart the server and clients fully.

Existing saved Pokemon IDs, evolution requirements, moves and stats carry forward from 1.1.9. Keep existing configuration files; new switches receive their defaults without resetting saved settings.

## Validation

Passed 28 unit tests, 227 settings runtime assertions, 18 normal/shiny spawn commands, the progression/HP self-test, and the actual Minecraft client resource/render checks. [Hosted run](https://github.com/WeepingAngeIs/unyielding-mod/actions/runs/42884115072). The full user modpack, player-operated inventory UI and live minimap gameplay were not replayed.
