# Reject unsafe grace values and reset the countdown when the interval changes.
execute unless score #grace cui_items matches 1200.. run scoreboard players set #grace cui_items 1200
execute unless score #interval cui_items matches 1.. run scoreboard players set #interval cui_items 12000
execute unless score #interval cui_items = #last_interval cui_items run function cui_cleanup:interval_changed
execute if score #enabled cui_items matches 1 run function cui_cleanup:run_tick
