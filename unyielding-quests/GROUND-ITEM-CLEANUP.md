# Ground item cleanup settings (1.21.1)

The included cleanup runs every 12,000 server ticks (10 minutes at 20 TPS) by default. It warns 60, 30, and 10 seconds before cleanup when the configured interval is long enough. It checks loaded dropped stacks in the Overworld, Nether, and End; unloaded chunks and other dimensions are not covered.

The safe default clears food while protecting tools, weapons and all other drops. Nether Stars are in the protected other category. Food on the ground can be cleared before Mawile eats it. Classification uses NeoForge's common tool tag, the `minecraft:max_damage` component, and an extensible `cui_cleanup:gear` item tag. Food uses `minecraft:food` and `cui_cleanup:food`. Some modded weapons have no tool tag and no durability: those fall in the other category, so enabling cleanup of other items can delete them. Add specific item IDs to the `cui_cleanup:gear` item tag with a datapack to protect them as gear. Armour and other items with durability also count as gear.

Run these as an operator. Settings are stored in the world and survive reloads and restarts. 0 turns a category off; 1 turns it on:

- `/scoreboard players set #clear_gear cui_items 0` — protect tools, weapons and durability items (default). Set to 1 to clear them.
- `/scoreboard players set #clear_food cui_items 1` — clear food (default). Set to 0 to protect it.
- `/scoreboard players set #clear_other cui_items 0` — protect all other items (default). Set to 1 to clear them.
- `/scoreboard players set #interval cui_items 6000` — every 5 minutes.
- `/scoreboard players set #interval cui_items 12000` — every 10 minutes.
- `/scoreboard players set #interval cui_items 24000` — every 20 minutes.
- `/scoreboard players set #enabled cui_items 0` — pause automatic cleanup; use 1 to resume.
- `/function cui_cleanup:clear` — clear enabled categories now and restart the countdown, even while automatic cleanup is paused.
- `/scoreboard players get #interval cui_items` — see the interval in ticks.
- `/scoreboard players get #timer cui_items` — see elapsed ticks.

One minute is 1,200 server ticks. Changing the interval resets the countdown on the next server tick, so shortening it does not immediately clear items. The server announces the new duration. Automatic cleanup gives chat warnings at one minute and five seconds remaining when the interval is long enough; at low TPS, these durations are longer in real time. All cleanup calls protect item entities younger than 1,200 ticks, and negative age items. Set `#grace` in `cui_items` to a higher number of ticks to lengthen the grace period; it cannot be set below 1,200. Vanilla merging carries forward the younger item age. This has not been verified against mods that replace merge behavior. Do not also enable the standalone Ground-Item-Cleanup datapack: it uses the same function names and may override these configurable functions. A server restart or `/reload` loads the bundled data.
