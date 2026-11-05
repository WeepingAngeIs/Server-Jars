from gen import Q, T, R, poke_icon

G = '69C15075C3ED44EB'  # Pokédex and Mastery
C = 'cobblemon:'
qs = []

# row 0: catching ladder
catches = [('catch10', 10, 'poke_ball', '&aTen Friends', (0, 0), [R.item(C + 'great_ball', 8), R.xp(50)]),
           ('catch50', 50, 'great_ball', '&aFifty Strong', (3, 0), [R.item(C + 'ultra_ball', 8), R.item(C + 'rare_candy', 2), R.xp(120)]),
           ('catch100', 100, 'ultra_ball', '&aThe Hundred Club', (6, 0), [R.item(C + 'rare_candy', 5), R.item(C + 'exp_candy_l', 4), R.xp(250)]),
           ('catch250', 250, 'luxury_ball', '&eCollector', (9, 0), [R.item(C + 'master_ball', 1), R.item(C + 'rare_candy', 8), R.xp(500)]),
           ('catch500', 500, 'master_ball', '&6Living Pokédex Hunter', (12, 0), [R.item(C + 'master_ball', 2), R.item('minecraft:enchanted_golden_apple', 2), R.xp(1000)])]
prev = None
for key, n, ball, title, pos, rew in catches:
    qs.append(Q(key, title, C + ball, [T.mon('catch', n, title=f'Catch {n} Pokémon')], rew, deps=[prev] if prev else [], pos=pos,
                shape='hexagon' if n >= 250 else None, size=1.25 if n >= 250 else None,
                sub=f'&7Catch {n} Pokémon in total',
                desc=[f'&fCatch &e{n}&f Pokémon. Every successful capture counts, any species, any ball.',
                      '&7Progress counts from the moment this quest is unlocked.']))
    prev = key

# row 1: battles
battles = [('win10', 10, '&cTen Victories', (0, 3), [R.item(C + 'potion', 6), R.xp(60)]),
           ('win50', 50, '&cBattle Hardened', (3, 3), [R.item(C + 'super_potion', 6), R.item(C + 'rare_candy', 2), R.xp(150)]),
           ('win200', 200, '&cArena Legend', (6, 3), [R.item(C + 'hyper_potion', 8), R.item(C + 'rare_candy', 6), R.xp(400)])]
prev = None
for key, n, title, pos, rew in battles:
    qs.append(Q(key, title, C + 'medicinal_leek' if False else 'minecraft:iron_sword', [T.mon('defeat', n, title=f'Win {n} Pokémon battles')], rew, deps=[prev] if prev else [], pos=pos,
                sub=f'&7Win {n} battles against wild Pokémon, trainers or players',
                desc=[f'&fWin &e{n}&f Pokémon battles. Wild battles, NPC trainers and other players all count; catching a wild Pokémon does not.']))
    prev = key

# row 2: evolution + levels
qs.append(Q('evolve5', '&dFive Evolutions', C + 'fire_stone', [T.mon('evolve', 5, title='Evolve 5 Pokémon')], [R.item(C + 'rare_candy', 3), R.xp(80)],
            pos=(0, 6), sub='&7Grow your team', desc=['&fEvolve &e5&f Pokémon. Level-ups, stones, trades and friendship evolutions all count.']))
qs.append(Q('evolve25', '&dEvolution Expert', C + 'shiny_stone', [T.mon('evolve', 25, title='Evolve 25 Pokémon')], [R.item(C + 'rare_candy', 8), R.item(C + 'link_cable', 1), R.xp(300)],
            deps=['evolve5'], pos=(3, 6), sub='&7Twenty-five evolutions', desc=['&fEvolve &e25&f Pokémon in total.']))
qs.append(Q('lvl50', '&bLevel 50', C + 'exp_candy_l', [T.mon('level_up_to', 50, title='Raise a Pokémon to level 50')], [R.item(C + 'exp_candy_l', 4), R.xp(120)],
            pos=(6, 6), sub='&7Halfway to the top', desc=['&fRaise any Pokémon to &elevel 50&f. Battles, XP candy and the Lucky Egg all help.']))
qs.append(Q('lvl100', '&bLevel 100', C + 'exp_candy_xl', [T.mon('level_up_to', 100, title='Raise a Pokémon to level 100')], [R.item(C + 'master_ball', 1), R.item(C + 'exp_candy_xl', 4), R.xp(500)],
            deps=['lvl50'], pos=(9, 6), shape='hexagon', size=1.25, sub='&7The maximum level', desc=['&fRaise any Pokémon to &elevel 100&f. A fully trained partner for the hardest battles.']))
qs.append(Q('baby100', '&bDarwin? Who\'s That?', poke_icon('pichu'), [T.adv(C + 'catching/max_level_baby', 'Take an unevolved Pokémon to level 100')], [R.item(C + 'everstone', 1), R.item(C + 'rare_candy', 5), R.xp(400)],
            deps=['lvl100'], pos=(12, 6), optional=True, sub='&7Level 100 without ever evolving',
            desc=['&fTake a first-stage Pokémon that &ecould&f evolve all the way to level 100 without evolving it. Hold B or give it an &eEverstone&f when it tries to evolve.']))

# row 3: shiny / fishing / fossils / scanning
qs.append(Q('shiny1', '&eA Shiny Friend', C + 'premier_ball', [T.mon('catch', 1, shiny=True, title='Catch a shiny Pokémon')], [R.item(C + 'premier_ball', 10), R.item(C + 'rare_candy', 5), R.xp(300)],
            pos=(0, 9), shape='hexagon', size=1.25, sub='&7Sparkles on sight', desc=['&fCatch a &eshiny&f Pokémon: a rare color variant that sparkles when it appears. Check the chat for shiny spawn notifications.']))
qs.append(Q('shiny5', '&eShiny Hunter', C + 'luxury_ball', [T.mon('catch', 5, shiny=True, title='Catch 5 shiny Pokémon')], [R.item(C + 'master_ball', 1), R.item('minecraft:diamond', 8), R.xp(800)],
            deps=['shiny1'], pos=(3, 9), shape='hexagon', size=1.25, sub='&7Five shinies caught', desc=['&fCatch &e5&f shiny Pokémon in total. Catch combos (Unchained) raise your shiny odds.']))
qs.append(Q('fish10', '&bAngler', C + 'poke_rod', [T.mon('fishing', 10, title='Fish up 10 Pokémon with a Poké Rod')], [R.item(C + 'lure_ball', 6), R.xp(100)],
            pos=(6, 9), sub='&7Pokémon on the line', desc=['&fUse a &ePoké Rod&f (a fishing rod upgraded with a Poké Ball at a smithing table) to reel in &e10&f Pokémon. Different balls on the rod attract different Pokémon.']))
qs.append(Q('master_rod', '&bMaster Rod', C + 'master_ball', [T.adv(C + 'catching/craft_master_rod', 'Craft a Poké Rod with a Master Ball')], [R.item(C + 'dive_ball', 8), R.xp(200)],
            deps=['fish10'], pos=(9, 9), optional=True, sub='&7Fishing with the best ball', desc=['&fCombine a fishing rod with a &eMaster Ball&f at a smithing table. Everything you hook with it is caught instantly.']))
qs.append(Q('fossil3', '&6Fossil Reviver', C + 'helix_fossil', [T.mon('revive_fossil', 3, title='Revive 3 fossil Pokémon')], [R.item(C + 'rare_candy', 4), R.xp(200)],
            pos=(0, 12), sub='&7Bring the past back to life', desc=['&fFind fossils underground, put them in a &eResurrection Machine&f and revive &e3&f fossil Pokémon.']))
qs.append(Q('galar_fossil', '&6Didn\'t Stop to Think', C + 'fossilized_fish', [T.adv(C + 'geological/resurrect_galar_fossil', 'Revive a Galar fossil combination')], [R.item('minecraft:bone_block', 4), R.xp(200)],
            deps=['fossil3'], pos=(3, 12), optional=True, sub='&7Dracovish, Dracozolt, Arctovish or Arctozolt', desc=['&fCombine a &eFossilized Bird&f or &eFish&f with a &eFossilized Drake&f or &eDino&f in the Resurrection Machine for one of the four Galar fossil Pokémon.']))
qs.append(Q('scan50', '&bField Researcher', C + 'pokedex_red', [T.mon('scan', 50, title='Scan 50 wild Pokémon with your Pokédex')], [R.item(C + 'ultra_ball', 6), R.xp(150)],
            pos=(6, 12), sub='&7Point, scan, learn', desc=['&fHold your &ePokédex&f and point it at wild Pokémon to scan them. Scan &e50&f different Pokémon.']))

# row 4: pokedex counts
dex = [('seen100', 'seen', 100, '&bSeen: 100', (0, 15), [R.item(C + 'great_ball', 10), R.xp(100)]),
       ('seen300', 'seen', 300, '&bSeen: 300', (3, 15), [R.item(C + 'ultra_ball', 10), R.item(C + 'rare_candy', 3), R.xp(300)]),
       ('caught100', 'caught', 100, '&dCaught: 100', (6, 15), [R.item(C + 'rare_candy', 5), R.xp(300)]),
       ('caught300', 'caught', 300, '&dCaught: 300', (9, 15), [R.item(C + 'master_ball', 1), R.item(C + 'rare_candy', 10), R.xp(800)]),
       ('caught500', 'caught', 500, '&6Caught: 500', (12, 15), [R.item(C + 'master_ball', 2), R.item('minecraft:enchanted_golden_apple', 3), R.xp(1500)])]
for key, mode, n, title, pos, rew in dex:
    deps = [] if key in ('seen100', 'caught100') else [{'seen300': 'seen100', 'caught300': 'caught100', 'caught500': 'caught300'}[key]]
    qs.append(Q(key, title, C + 'pokedex_blue' if mode == 'seen' else C + 'pokedex_pink', [T.mon('have_registered', n, dex=mode, title=f'Register {n} Pokémon as {mode} in the Pokédex')], rew,
                deps=deps, pos=pos, shape='hexagon' if n >= 300 else None, size=1.25 if n >= 300 else None,
                sub=f'&7{n} species {mode}', desc=[f'&fHave &e{n}&f different species marked as &e{mode}&f in your Pokédex.' + (' Seeing one in the wild or scanning it is enough.' if mode == 'seen' else ' Catching, evolving and trading all register species as caught.')]))

# row 5: misc advancements
qs.append(Q('full_party', '&aFull House', C + 'poke_ball', [T.adv(C + 'catching/full_party', 'Fill all six party slots')], [R.item(C + 'oran_berry', 8), R.xp(40)],
            pos=(0, 18), sub='&7Six Pokémon in your party', desc=['&fCatch enough Pokémon to fill all &esix&f party slots.']))
qs.append(Q('trade', '&aTake Good Care!', C + 'link_cable', [T.adv(C + 'catching/trade_pokemon', 'Trade a Pokémon with another player')], [R.item(C + 'link_cable', 1), R.xp(100)],
            pos=(3, 18), sub='&7Trade with a friend', desc=['&fOpen the party menu, pick a Pokémon and choose &eTrade&f while looking at another player. Some Pokémon only evolve through trades.']))
qs.append(Q('ride', '&bPokémon Jockey', poke_icon('arcanine'), [T.adv(C + 'catching/ride_pokemon', 'Ride a Pokémon')], [R.item(C + 'saddle' if False else 'minecraft:saddle', 1), R.xp(80)],
            pos=(6, 18), sub='&7Hop on', desc=['&fSend out a rideable Pokémon (Arcanine, Lapras, Charizard and many more) and right-click it with an empty hand to ride it. Feed it flavored Aprijuice to raise its ride stats.']))
qs.append(Q('pasture', '&aFree Roam', C + 'pasture', [T.adv(C + 'catching/use_pasture', 'Put a Pokémon in a Pasture block')], [R.item(C + 'pasture', 1), R.xp(60)],
            pos=(9, 18), sub='&7Let them out', desc=['&fCraft a &ePasture&f block and move Pokémon from your PC into it so they wander around your base.']))
qs.append(Q('alpha_wailord', '&bCall Me Ishmael!', poke_icon('wailord'), [T.adv(C + 'catching/catch_alpha_wailord', 'Reel in an Alpha Wailord')], [R.item(C + 'dive_ball', 8), R.item(C + 'rare_candy', 5), R.xp(400)],
            deps=['fish10'], pos=(12, 18), optional=True, shape='hexagon', size=1.25, sub='&7The biggest catch of all', desc=['&fFish in deep ocean with a Poké Rod until an &eAlpha Wailord&f bites, then catch it. Bring Dive Balls and patience.']))
qs.append(Q('all_balls', '&ePoké Ball Maniac', C + 'cherish_ball', [T.adv(C + 'catching/craft_all_balls', 'Craft one of every Poké Ball')], [R.item(C + 'master_ball', 1), R.xp(500)],
            pos=(0, 21), optional=True, shape='hexagon', size=1.25, sub='&7Every ball in the game', desc=['&fCraft one of &eevery&f Poké Ball: the seven basic colors, the seven Apricorn balls and the fifteen Ancient balls made with Tumblestones.']))
qs.append(Q('all_berries', '&aOops, All Berries!', C + 'sitrus_berry', [T.adv(C + 'agriculture/obtain_all_berries', 'Obtain one of every berry')], [R.item(C + 'lum_berry', 4), R.xp(300)],
            pos=(3, 21), optional=True, sub='&7A full berry collection', desc=['&fCollect one of every Cobblemon berry. Plant berry trees of different kinds next to each other to grow mutations you cannot find in the wild.']))
qs.append(Q('all_tms', '&dCollector\'s Discography', C + 'tm_machine' if False else C + 'blank_tm', [T.adv(C + 'geological/register_all_tms', 'Register every move in a TM Machine')], [R.item(C + 'master_ball', 1), R.xp(800)],
            pos=(6, 21), optional=True, shape='hexagon', size=1.25, sub='&7Every TM registered', desc=['&fFind TMs in ruins, from alphas and from battles, and register all of them in a &eTM Machine&f so you can print any move.']))
qs.append(Q('vivillon', '&dVivillionaire', poke_icon('vivillon'), [T.adv(C + 'catching/collect_all_vivillon', 'Collect every Vivillon pattern')], [R.item(C + 'rare_candy', 6), R.xp(400)],
            pos=(9, 21), optional=True, sub='&7Every wing pattern', desc=['&fVivillon\'s pattern depends on the biome you catch it in. Collect all of the common patterns.']))
qs.append(Q('shedinja', '&7Ghost in the Shell', poke_icon('shedinja'), [T.adv(C + 'catching/evolve_shedinja', 'Get a Shedinja')], [R.item(C + 'rare_candy', 3), R.xp(150)],
            pos=(12, 21), optional=True, sub='&7One Nincada, two Pokémon', desc=['&fEvolve a &eNincada&f at level 20 with a free party slot and a spare Poké Ball in your inventory: you get Ninjask and a &eShedinja&f.']))

chapter = dict(file='31_trainer_milestones', title='&a> Trainer Milestones&r', group=G, order=40,
               icon='cobblemon:master_ball', sub='&7Long-term goals for every trainer: catches, battles, evolutions and the Pokédex.',
               quests=qs, autofocus='catch10')
