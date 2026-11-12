# Equipment has priority over food; anything else is in the other category.
tag @s remove cui_cleanup.gear
tag @s remove cui_cleanup.food
execute if items entity @s contents #cui_cleanup:gear run tag @s add cui_cleanup.gear
execute if items entity @s contents *[minecraft:max_damage] run tag @s add cui_cleanup.gear
execute if items entity @s contents #cui_cleanup:food run tag @s add cui_cleanup.food
execute if items entity @s contents *[minecraft:food] run tag @s add cui_cleanup.food
execute if entity @s[tag=cui_cleanup.gear] if score #clear_gear cui_items matches 1 run function cui_cleanup:remove_one
execute unless entity @s[tag=cui_cleanup.gear] if entity @s[tag=cui_cleanup.food] if score #clear_food cui_items matches 1 run function cui_cleanup:remove_one
execute unless entity @s[tag=cui_cleanup.gear] unless entity @s[tag=cui_cleanup.food] if score #clear_other cui_items matches 1 run function cui_cleanup:remove_one
tag @s remove cui_cleanup.gear
tag @s remove cui_cleanup.food
