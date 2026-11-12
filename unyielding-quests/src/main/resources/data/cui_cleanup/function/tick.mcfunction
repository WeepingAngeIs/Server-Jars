# Count loaded server ticks only while enabled.
execute if score #enabled cui_items matches 1 run function cui_cleanup:run_tick
