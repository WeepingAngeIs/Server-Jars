scoreboard players operation @s cui_candidate = #preview_epoch cui_candidate
scoreboard players set #item_count cui_items 1
execute if data entity @s Item.count store result score #item_count cui_items run data get entity @s Item.count
scoreboard players operation @s cui_itemcount = #item_count cui_items
scoreboard players add #preview_stacks cui_items 1
scoreboard players operation #preview_items cui_items += #item_count cui_items
