# Reject stacks changed by later item merges or extraction.
scoreboard players set #item_count cui_items 1
execute if data entity @s Item.count store result score #item_count cui_items run data get entity @s Item.count
execute if score @s cui_candidate = #preview_epoch cui_candidate if score @s cui_itemcount = #item_count cui_items run function cui_cleanup:clear_item
