scoreboard players add #timer cui_items 1
scoreboard players operation #remaining cui_items = #interval cui_items
scoreboard players operation #remaining cui_items -= #timer cui_items
execute if score #remaining cui_items matches 1200 run tellraw @a [{"text":"[Unyielding Cleanup] ","color":"light_purple","bold":true},{"text":"Ground items will clear in ","color":"white"},{"text":"1 minute","color":"gold","bold":true},{"text":"!","color":"white"}]
execute if score #remaining cui_items matches 600 run tellraw @a [{"text":"[Unyielding Cleanup] ","color":"light_purple","bold":true},{"text":"Ground items will clear in ","color":"white"},{"text":"30 seconds","color":"gold","bold":true},{"text":"!","color":"white"}]
execute if score #remaining cui_items matches 200 run tellraw @a [{"text":"[Unyielding Cleanup] ","color":"light_purple","bold":true},{"text":"Ground items will clear in ","color":"white"},{"text":"10 seconds","color":"red","bold":true},{"text":"!","color":"white"}]
execute if score #remaining cui_items matches ..0 run function cui_cleanup:clear
