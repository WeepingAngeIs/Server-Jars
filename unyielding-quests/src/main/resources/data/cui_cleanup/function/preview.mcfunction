# Preview never executes arbitrary custom-dimension overrides.
scoreboard players set #preview_mode cui_items 1
scoreboard players set #preview_stacks cui_items 0
scoreboard players set #preview_items cui_items 0
execute unless score #grace cui_items matches 1200.. run scoreboard players set #grace cui_items 1200
execute if score #dim_overworld cui_items matches 1 in minecraft:overworld as @e[type=minecraft:item] run function cui_cleanup:clear_item
execute if score #dim_nether cui_items matches 1 in minecraft:the_nether as @e[type=minecraft:item] run function cui_cleanup:clear_item
execute if score #dim_end cui_items matches 1 in minecraft:the_end as @e[type=minecraft:item] run function cui_cleanup:clear_item
scoreboard players set #preview_mode cui_items 0
scoreboard players set #preview_done cui_items 1
