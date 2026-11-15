# Preserve existing settings and countdown across reloads and restarts.
scoreboard objectives add cui_items dummy
scoreboard players add #timer cui_items 0
execute unless score #interval cui_items matches 1.. run scoreboard players set #interval cui_items 12000
execute unless score #enabled cui_items matches 0..1 run scoreboard players set #enabled cui_items 1
scoreboard players set #twenty cui_items 20
execute unless score #clear_gear cui_items matches 0..1 run scoreboard players set #clear_gear cui_items 0
execute unless score #clear_food cui_items matches 0..1 run scoreboard players set #clear_food cui_items 1
execute unless score #clear_other cui_items matches 0..1 run scoreboard players set #clear_other cui_items 0

# Keep operator settings; introduce only a minimum grace and remembered interval.
execute unless score #grace cui_items matches 1200.. run scoreboard players set #grace cui_items 1200
execute unless score #last_interval cui_items matches 1.. run scoreboard players operation #last_interval cui_items = #interval cui_items

# Existing installations previously ran all of these dimensions.
execute unless score #dim_overworld cui_items matches 0..1 run scoreboard players set #dim_overworld cui_items 1
execute unless score #dim_nether cui_items matches 0..1 run scoreboard players set #dim_nether cui_items 1
execute unless score #dim_end cui_items matches 0..1 run scoreboard players set #dim_end cui_items 1
execute unless score #dim_custom cui_items matches 0..1 run scoreboard players set #dim_custom cui_items 1

# Separate objectives keep preview candidates distinct between previews and restarts.
scoreboard objectives add cui_candidate dummy
scoreboard objectives add cui_itemcount dummy
scoreboard players add #preview_epoch cui_candidate 0
