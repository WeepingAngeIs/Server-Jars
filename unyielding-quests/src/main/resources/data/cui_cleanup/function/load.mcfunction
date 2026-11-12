# Preserve existing settings and countdown across reloads and restarts.
scoreboard objectives add cui_items dummy
scoreboard players add #timer cui_items 0
execute unless score #interval cui_items matches 1.. run scoreboard players set #interval cui_items 12000
execute unless score #enabled cui_items matches 0..1 run scoreboard players set #enabled cui_items 1
scoreboard players set #twenty cui_items 20
