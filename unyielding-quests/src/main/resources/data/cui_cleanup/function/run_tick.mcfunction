scoreboard players add #timer cui_items 1
scoreboard players operation #remaining cui_items = #interval cui_items
scoreboard players operation #remaining cui_items -= #timer cui_items
execute if score #remaining cui_items matches 1200 run function cui_cleanup:warn_minute
execute if score #remaining cui_items matches 100 run function cui_cleanup:warn_five
execute if score #remaining cui_items matches ..0 run function cui_cleanup:clear
