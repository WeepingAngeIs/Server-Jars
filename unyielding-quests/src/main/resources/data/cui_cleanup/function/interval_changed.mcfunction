scoreboard players operation #last_interval cui_items = #interval cui_items
scoreboard players set #timer cui_items 0
scoreboard players operation #seconds cui_items = #interval cui_items
scoreboard players operation #seconds cui_items /= #twenty cui_items
tellraw @a [{"text":"[Unyielding Cleanup] Interval changed. Next automatic cleanup in ","color":"light_purple"},{"score":{"name":"#seconds","objective":"cui_items"}},{"text":" seconds."}]
