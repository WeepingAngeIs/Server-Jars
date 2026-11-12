# Counts dropped stacks in loaded chunks, matching the original pack.
scoreboard players set #removed cui_items 0
scoreboard players set #dimension cui_items 0
execute in minecraft:overworld store result score #dimension cui_items run kill @e[type=minecraft:item]
scoreboard players operation #removed cui_items += #dimension cui_items
scoreboard players set #dimension cui_items 0
execute in minecraft:the_nether store result score #dimension cui_items run kill @e[type=minecraft:item]
scoreboard players operation #removed cui_items += #dimension cui_items
scoreboard players set #dimension cui_items 0
execute in minecraft:the_end store result score #dimension cui_items run kill @e[type=minecraft:item]
scoreboard players operation #removed cui_items += #dimension cui_items
function cui_cleanup:extra_dimensions
scoreboard players set #timer cui_items 0
scoreboard players operation #seconds cui_items = #interval cui_items
scoreboard players operation #seconds cui_items /= #twenty cui_items
tellraw @a [{"text":"[Unyielding Cleanup] ","color":"light_purple","bold":true},{"text":"Cleared ","color":"white"},{"score":{"name":"#removed","objective":"cui_items"},"color":"aqua","bold":true},{"text":" dropped item stacks. Interval: ","color":"white"},{"score":{"name":"#seconds","objective":"cui_items"},"color":"aqua"},{"text":" seconds.","color":"white"}]
