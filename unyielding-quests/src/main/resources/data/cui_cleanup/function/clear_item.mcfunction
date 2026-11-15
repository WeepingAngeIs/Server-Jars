# Vanilla merged stacks retain the younger item age; negative ages stay protected.
scoreboard players set #item_age cui_items -32768
execute store result score #item_age cui_items run data get entity @s Age
execute if score #item_age cui_items >= #grace cui_items if score #preview_mode cui_items matches 1 run function cui_cleanup:preview_eligible
execute if score #item_age cui_items >= #grace cui_items unless score #preview_mode cui_items matches 1 run function cui_cleanup:clear_eligible
