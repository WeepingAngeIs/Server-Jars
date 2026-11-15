# Counts dropped stacks in loaded chunks, matching the original pack.
execute unless score #grace cui_items matches 1200.. run scoreboard players set #grace cui_items 1200
scoreboard players set #preview_mode cui_items 0
scoreboard players set #removed cui_items 0
# Classify each stack before deleting it, and count only deleted stacks.
execute if score #dim_overworld cui_items matches 1 in minecraft:overworld as @e[type=minecraft:item] run function cui_cleanup:clear_item
execute if score #dim_nether cui_items matches 1 in minecraft:the_nether as @e[type=minecraft:item] run function cui_cleanup:clear_item
execute if score #dim_end cui_items matches 1 in minecraft:the_end as @e[type=minecraft:item] run function cui_cleanup:clear_item
execute if score #dim_custom cui_items matches 1 run function cui_cleanup:extra_dimensions
scoreboard players set #timer cui_items 0
scoreboard players operation #seconds cui_items = #interval cui_items
scoreboard players operation #seconds cui_items /= #twenty cui_items
tellraw @a [{"text":"[Unyielding Cleanup] ","color":"light_purple","bold":true},{"text":"Cleared ","color":"white"},{"score":{"name":"#removed","objective":"cui_items"},"color":"aqua","bold":true},{"text":" dropped item stacks. Interval: ","color":"white"},{"score":{"name":"#seconds","objective":"cui_items"},"color":"aqua"},{"text":" seconds.","color":"white"}]
