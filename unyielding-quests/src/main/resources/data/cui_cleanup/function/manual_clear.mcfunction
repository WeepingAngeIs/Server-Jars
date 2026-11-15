# Only vanilla dimensions are supported for manual preview and clear.
scoreboard players set #preview_mode cui_items 0
scoreboard players set #removed cui_items 0
execute unless score #grace cui_items matches 1200.. run scoreboard players set #grace cui_items 1200
execute if score #dim_overworld cui_items matches 1 in minecraft:overworld as @e[type=minecraft:item] run function cui_cleanup:manual_candidate
execute if score #dim_nether cui_items matches 1 in minecraft:the_nether as @e[type=minecraft:item] run function cui_cleanup:manual_candidate
execute if score #dim_end cui_items matches 1 in minecraft:the_end as @e[type=minecraft:item] run function cui_cleanup:manual_candidate
tellraw @a [{"text":"[Unyielding Cleanup] Manual clear removed ","color":"light_purple"},{"score":{"name":"#removed","objective":"cui_items"}},{"text":" eligible item stacks. Fresh drops were protected."}]
