# Ground item cleanup settings (1.21.1)

The included cleanup runs every 12,000 server ticks (10 minutes at 20 TPS) by default. It warns 60, 30, and 10 seconds before cleanup when the configured interval is long enough. It clears loaded dropped item stacks in the Overworld, Nether, and End and announces the count. It includes death drops, food and Nether Stars left on the ground. Unloaded chunks and other dimensions are not covered.

Run these as an operator. Values are stored in the world and survive reloads and restarts:

- `/scoreboard players set #interval cui_items 6000` — every 5 minutes.
- `/scoreboard players set #interval cui_items 12000` — every 10 minutes.
- `/scoreboard players set #interval cui_items 24000` — every 20 minutes.
- `/scoreboard players set #enabled cui_items 0` — pause automatic cleanup.
- `/scoreboard players set #enabled cui_items 1` — resume automatic cleanup.
- `/function cui_cleanup:clear` — clear now and restart the countdown, even while automatic cleanup is paused.
- `/scoreboard players get #interval cui_items` — see the interval in ticks.
- `/scoreboard players get #timer cui_items` — see elapsed ticks.

One minute is 1,200 ticks. Changing the interval takes effect on the next server tick; shortening it below the elapsed timer triggers cleanup immediately. Do not also enable the standalone Ground-Item-Cleanup datapack: it uses the same function names and may override these configurable functions. A server restart or `/reload` loads the bundled data.
