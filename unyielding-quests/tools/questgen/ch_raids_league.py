from gen import Q, T, R, poke_icon

G_BATTLE = '28AD4BAFAFBA049E'  # Battle Phenomena
RD = 'cobblemonraiddens:'
RC = 'rctmod:trainers/'

# =============================== Raid Dens =====================================
rq = []
rq.append(Q('join', '&cYour First Raid', RD + 'raid_shard', [T.adv(RD + 'join_raid', 'Join a raid battle')], [R.item('cobblemon:ultra_ball', 6), R.xp(100)],
            pos=(0, 0), shape='hexagon', size=1.5, sub='&7Giant Pokémon in glowing dens',
            desc=['&fRaid Dens are glowing rock dens found around the world. Right-click one with a &eRaid Shard&f or wait for it to activate, then join the battle with up to three other trainers.',
                  '&7The raid boss is huge and has a lot of HP. Everyone who helps beat it gets a chance to catch it.']))
tiers = [('tier_one', 1, '&aTier 1: First Steps', (3, 0)), ('tier_two', 2, '&aTier 2: Raid Buddy', (6, 0)), ('tier_three', 3, '&eTier 3: A Formidable Foe', (9, 0)),
         ('tier_four', 4, '&eTier 4: Raid Veteran', (12, 0)), ('tier_five', 5, '&6Tier 5: Raid Master', (15, 0)), ('tier_six', 6, '&6Tier 6: Even Further Beyond', (18, 0)),
         ('tier_seven', 7, '&dTier 7: The Mightiest', (21, 0))]
prev = 'join'
rewards = {1: [R.item('cobblemon:great_ball', 6), R.xp(60)], 2: [R.item('cobblemon:ultra_ball', 6), R.xp(100)], 3: [R.item('cobblemon:rare_candy', 3), R.xp(150)],
           4: [R.item('cobblemon:rare_candy', 4), R.item('cobblemon:max_revive', 2), R.xp(200)], 5: [R.item('cobblemon:rare_candy', 6), R.item('cobblemon:ability_capsule', 1), R.xp(300)],
           6: [R.item('cobblemon:rare_candy', 8), R.item('cobblemon:master_ball', 1), R.xp(450)], 7: [R.item('cobblemon:master_ball', 2), R.item('minecraft:enchanted_golden_apple', 2), R.xp(800)]}
for key, n, title, pos in tiers:
    rq.append(Q(key, title, RD + 'raid_shard' if n < 4 else 'cobblemon:master_ball' if n == 7 else 'cobblemon:ultra_ball',
                [T.adv(RD + key, f'Win a tier {n} raid')], rewards[n], deps=[prev], pos=pos,
                shape='hexagon' if n == 7 else None, size=1.5 if n == 7 else None,
                sub=f'&7Beat a tier {n} raid boss',
                desc=[f'&fWin a &etier {n}&f raid. Higher tiers have stronger bosses, better rewards and more perfect IVs on the catch.' +
                      (' Tier 7 bosses need a strong, coordinated team: bring Cheers and your best Pokémon.' if n == 7 else '')]))
    prev = key
rq.append(Q('cheers', '&bThe Great Friend', RD + 'cheer_heal', [T.adv(RD + 'great_friend', 'Use an Attack, Defense and Heal Cheer in raids')], [R.item(RD + 'cheer_heal', 2), R.xp(120)],
            deps=['join'], pos=(3, 3), sub='&7Support your raid team',
            desc=['&fCheers are one-use items for raids: the &cAttack Cheer&f and &9Defense Cheer&f boost everyone\'s stats by 50%, the &aHeal Cheer&f restores half of everyone\'s HP. Use one of each.']))
rq.append(Q('shard', '&bRaid Energy', RD + 'raid_shard', [T.adv(RD + 'raid_shard_charge', 'Fully charge a Raid Shard')], [R.item(RD + 'raid_shard', 1), R.xp(120)],
            deps=['join'], pos=(6, 3), sub='&7Start your own raids',
            desc=['&fClearing raids fills a &eRaid Shard&f with energy. A fully charged shard can wake up any den on your own schedule.']))
rq.append(Q('charm', '&bCatching Charm', RD + 'catching_charm', [T.item(RD + 'catching_charm', 1, 'Get a Catching Charm')], [R.item('cobblemon:ultra_ball', 8), R.xp(100)],
            deps=['tier_three'], pos=(9, 3), optional=True, sub='&7Better odds on raid bosses',
            desc=['&fHold a &eCatching Charm&f in your off-hand after a raid to raise the catch rate of the raid boss.']))
rq.append(Q('mega', '&dOverflowing With Power', 'mega_showdown:mega_bracelet', [T.adv(RD + 'raid_mega', 'Win a Mega raid')], [R.item('cobblemon:rare_candy', 5), R.xp(300)],
            deps=['tier_four'], pos=(12, 3), optional=True, sub='&7A Mega-Evolved raid boss', desc=['&fSome dens hold a &dMega-Evolved&f boss. Beat one.']))
rq.append(Q('dyna', '&dA Dynamax Adventure', 'mega_showdown:dynamax_band', [T.adv(RD + 'raid_dynamax', 'Win a Dynamax raid')], [R.item('cobblemon:rare_candy', 5), R.xp(300)],
            deps=['tier_four'], pos=(15, 3), optional=True, sub='&7A Dynamaxed raid boss', desc=['&fBeat a raid whose boss is &dDynamaxed&f.']))
rq.append(Q('tera', '&dThe Treasure of Area Zero', 'mega_showdown:tera_orb', [T.adv(RD + 'raid_tera', 'Win a Tera raid')], [R.item('cobblemon:rare_candy', 5), R.xp(300)],
            deps=['tier_four'], pos=(18, 3), optional=True, sub='&7A Terastallized raid boss', desc=['&fBeat a raid whose boss has &dTerastallized&f. Check its Tera type before you pick your team.']))
rq.append(Q('shiny', '&eA Sparkling Success', 'cobblemon:premier_ball', [T.adv(RD + 'raid_shiny', 'Get a shiny Pokémon from a raid')], [R.item('cobblemon:master_ball', 1), R.xp(500)],
            deps=['tier_two'], pos=(21, 3), optional=True, shape='hexagon', size=1.25, sub='&7Lucky raid catch', desc=['&fCatch a &eshiny&f raid boss. Raid bosses have better shiny odds than wild Pokémon.']))
raids = dict(file='32_raid_dens', title='&c> Raid Dens&r', group=G_BATTLE, order=50, icon='cobblemonraiddens:raid_shard',
             sub='&7Team up against giant raid bosses, tier by tier.', quests=rq, autofocus='join')

# =============================== Trainer League ===================================
lq = []
lq.append(Q('intro', '&eThe Trainer League', 'rctmod:trainer_card', [T.check('Read this, then press Check')], [R.item('cobblemon:potion', 4), R.xp(30)],
            pos=(0, 0), shape='hexagon', size=1.5, sub='&7NPC trainers, gym leaders, the Elite Four and champions',
            desc=['&fTrainers wander the world and challenge you on sight. Gym leaders, Elite Four members and champions belong to a &eseries&f: &bKanto&f (Radical Red), &bSinnoh&f (BDSP) and &bBorrius&f (Unbound).',
                  '&7Find a &eTrainer Association&f NPC (or use a Trainer Card) to pick your series. Only trainers of your current series spawn, in order: beat the leaders to unlock the next ones.',
                  '&7Each series below is its own path. Do them in any order.']))
lq.append(Q('first_win', '&aRadical Trainers', 'minecraft:iron_sword', [T.adv(RC + 'defeat_any', 'Defeat any trainer')], [R.item('cobblemon:super_potion', 4), R.item('cobblemon:great_ball', 6), R.xp(80)],
            deps=['intro'], pos=(3, 0), sub='&7Your first trainer battle', desc=['&fBeat any wandering trainer. They carry a few Pokémon each and pay out money and XP.']))

def badge(key, adv, title, leader, btype, region, pos, deps, reward):
    return Q(key, title, 'minecraft:gold_nugget' if False else ICONS.get(btype, 'minecraft:gold_nugget'),
             [T.adv(RC + adv, f'Defeat {leader}')], reward, deps=deps, pos=pos,
             sub=f'&7{region} gym leader: {btype}',
             desc=[f'&fFind and defeat &e{leader}&f, the {btype}-type gym leader of the {region} series.',
                   '&7Leaders only spawn once the previous one in the series is beaten. Bring type counters.'])

ICONS = {'Rock': 'minecraft:cobblestone', 'Water': 'minecraft:water_bucket', 'Electric': 'cobblemon:thunder_stone', 'Grass': 'cobblemon:leaf_stone', 'Poison': 'minecraft:spider_eye',
         'Psychic': 'cobblemon:twisted_spoon', 'Fire': 'cobblemon:fire_stone', 'Ground': 'cobblemon:soft_sand', 'Fighting': 'cobblemon:black_belt', 'Ghost': 'cobblemon:spell_tag',
         'Steel': 'cobblemon:metal_coat', 'Ice': 'cobblemon:ice_stone', 'Flying': 'cobblemon:sharp_beak', 'Normal': 'cobblemon:silk_scarf', 'Dragon': 'cobblemon:dragon_fang',
         'Fairy': 'cobblemon:sachet', 'Dark': 'cobblemon:black_glasses', 'Bug': 'cobblemon:silver_powder', 'Mixed': 'cobblemon:expert_belt'}
small = [R.item('cobblemon:rare_candy', 2), R.item('cobblemon:hyper_potion', 2), R.xp(150)]
elite = [R.item('cobblemon:rare_candy', 4), R.item('cobblemon:max_revive', 2), R.xp(300)]
champ = [R.item('cobblemon:master_ball', 1), R.item('cobblemon:rare_candy', 8), R.item('minecraft:enchanted_golden_apple', 1), R.xp(800)]

# ---- Kanto row (y = -4) ----
kanto = [('brock', 'defeat_leader_brock', '&7Boulder Badge', 'Leader Brock', 'Rock'), ('misty', 'defeat_leader_misty', '&9Cascade Badge', 'Leader Misty', 'Water'),
         ('surge', 'defeat_leader_lt_surge', '&eThunder Badge', 'Leader Lt. Surge', 'Electric'), ('erika', 'defeat_leader_erika', '&aRainbow Badge', 'Leader Erika', 'Grass'),
         ('koga', 'defeat_leader_koga', '&5Soul Badge', 'Leader Koga', 'Poison'), ('sabrina', 'defeat_leader_sabrina', '&dMarsh Badge', 'Leader Sabrina', 'Psychic'),
         ('blaine', 'defeat_leader_blaine', '&cVolcano Badge', 'Leader Blaine', 'Fire'), ('giovanni', 'defeat_leader_giovanni', '&6Earth Badge', 'Leader Giovanni', 'Ground')]
prev = 'first_win'
for i, (key, adv, title, leader, t) in enumerate(kanto):
    lq.append(badge('k_' + key, adv, title, leader, t, 'Kanto', (6 + i * 3, -4), [prev], small))
    prev = 'k_' + key
ke4 = [('lorelei', 'defeat_elite_four_lorelei', '&bElite Four: Lorelei', 'Ice'), ('bruno', 'defeat_elite_four_bruno', '&6Elite Four: Bruno', 'Fighting'),
       ('agatha', 'defeat_elite_four_agatha', '&5Elite Four: Agatha', 'Ghost'), ('lance', 'defeat_elite_four_lance', '&cElite Four: Lance', 'Dragon')]
for i, (key, adv, title, t) in enumerate(ke4):
    lq.append(Q('k_' + key, title, ICONS[t], [T.adv(RC + adv, f'Defeat Elite Four {key.capitalize()}')], elite, deps=[prev], pos=(30 + i * 3, -4),
                sub=f'&7Kanto Elite Four: {t}', desc=[f'&fDefeat &e{key.capitalize()}&f of the Kanto Elite Four. The Elite Four use full teams of strong {t}-type Pokémon.']))
    prev = 'k_' + key
lq.append(Q('k_champ', '&6Kanto Champion', 'minecraft:golden_apple' if False else 'cobblemon:master_ball', [T.adv(RC + 'defeat_champion_terry', 'Defeat Champion Terry')], champ, deps=[prev], pos=(42, -4),
            shape='hexagon', size=1.5, sub='&7The top of the Kanto series', desc=['&fDefeat &eChampion Terry&f, the strongest trainer of the Kanto series. The whole Elite Four must fall first.']))
lq.append(Q('k_rival', '&cRival Wayne', 'minecraft:iron_sword', [T.adv(RC + 'defeat_rival_wayne_1', 'Defeat your rival Wayne')], small, deps=['k_brock'], pos=(9, -7), optional=True,
            sub='&7He keeps coming back', desc=['&fYour Kanto rival &eWayne&f challenges you several times during the series. Beat him the first time.']))
lq.append(Q('k_rocket', '&4Team Rocket', 'minecraft:black_dye' if False else 'cobblemon:black_glasses', [T.adv(RC + 'defeat_rocket_admin_archer_1', 'Defeat Rocket Admin Archer')], elite, deps=['k_koga'], pos=(18, -7), optional=True,
            sub='&7Admins of the villain team', desc=['&fTeam Rocket operates around Kanto. Beat &eAdmin Archer&f.']))
lq.append(Q('k_boss', '&4Behind the Scenes', 'cobblemon:black_glasses', [T.adv(RC + 'defeat_boss_giovanni_1', 'Defeat Boss Giovanni')], elite, deps=['k_rocket'], pos=(21, -7), optional=True,
            sub='&7The head of Team Rocket', desc=['&fDefeat &eBoss Giovanni&f of Team Rocket for the first time.']))
lq.append(Q('k_red', '&cTrainer Red', poke_icon('pikachu'), [T.adv(RC + 'defeat_trainer_red', 'Defeat Trainer Red')], champ, deps=['k_champ'], pos=(45, -7), optional=True, shape='hexagon', size=1.25,
            sub='&7The silent legend', desc=['&fAfter becoming Kanto Champion, &eRed&f appears with the strongest team in the series. Defeat him.']))
lq.append(Q('k_blue', '&9Trainer Blue', poke_icon('blastoise'), [T.adv(RC + 'defeat_trainer_blue', 'Defeat Trainer Blue')], elite, deps=['k_champ'], pos=(45, -1), optional=True,
            sub='&7Smell ya later', desc=['&fDefeat &eBlue&f, the former champion of Kanto.']))

# ---- Sinnoh row (y = 0) ----
sinnoh = [('roark', 'defeat_gym_leader_roark_1', '&7Coal Badge', 'Gym Leader Roark', 'Rock'), ('gardenia', 'defeat_gym_leader_gardenia_1', '&aForest Badge', 'Gym Leader Gardenia', 'Grass'),
          ('maylene', 'defeat_gym_leader_maylene_1', '&6Cobble Badge', 'Gym Leader Maylene', 'Fighting'), ('wake', 'defeat_gym_leader_wake_1', '&9Fen Badge', 'Gym Leader Wake', 'Water'),
          ('fantina', 'defeat_gym_leader_fantina_1', '&5Relic Badge', 'Gym Leader Fantina', 'Ghost'), ('byron', 'defeat_gym_leader_byron_1', '&7Mine Badge', 'Gym Leader Byron', 'Steel'),
          ('candice', 'defeat_gym_leader_candice_1', '&bIcicle Badge', 'Gym Leader Candice', 'Ice'), ('volkner', 'defeat_gym_leader_volkner_1', '&eBeacon Badge', 'Gym Leader Volkner', 'Electric')]
prev = 'first_win'
for i, (key, adv, title, leader, t) in enumerate(sinnoh):
    lq.append(badge('s_' + key, adv, title, leader, t, 'Sinnoh', (6 + i * 3, 0), [prev], small))
    prev = 's_' + key
se4 = [('aaron', 'defeat_elite_four_aaron_1', '&aElite Four: Aaron', 'Bug'), ('bertha', 'defeat_elite_four_bertha_1', '&6Elite Four: Bertha', 'Ground'),
       ('flint', 'defeat_elite_four_flint_1', '&cElite Four: Flint', 'Fire'), ('lucian', 'defeat_elite_four_lucian_1', '&dElite Four: Lucian', 'Psychic')]
for i, (key, adv, title, t) in enumerate(se4):
    lq.append(Q('s_' + key, title, ICONS[t], [T.adv(RC + adv, f'Defeat Elite Four {key.capitalize()}')], elite, deps=[prev], pos=(30 + i * 3, 0),
                sub=f'&7Sinnoh Elite Four: {t}', desc=[f'&fDefeat &e{key.capitalize()}&f of the Sinnoh Elite Four ({t}-type specialist).']))
    prev = 's_' + key
lq.append(Q('s_champ', '&6Sinnoh Champion', poke_icon('garchomp'), [T.adv(RC + 'defeat_champion_cynthia_1', 'Defeat Champion Cynthia')], champ, deps=[prev], pos=(42, 0),
            shape='hexagon', size=1.5, sub='&7Cynthia and her Garchomp', desc=['&fDefeat &eChampion Cynthia&f at the end of the Sinnoh series. Her Garchomp is famous for a reason.']))
lq.append(Q('s_cynthia3', '&6Sinnoh Legend', poke_icon('garchomp'), [T.adv(RC + 'defeat_champion_cynthia_3', 'Defeat Cynthia in the final rematch')], champ, deps=['s_champ'], pos=(45, 0), optional=True,
            sub='&7Beat her twice more', desc=['&fCynthia offers rematches with stronger teams. Win the final rematch.']))
lq.append(Q('s_galactic', '&9Team Galactic', 'cobblemon:black_glasses', [T.adv(RC + 'defeat_commander_mars_1', 'Defeat Commander Mars')], elite, deps=['s_gardenia'], pos=(9, 3), optional=True,
            sub='&7Commanders of Team Galactic', desc=['&fTeam Galactic\'s commanders show up during the Sinnoh series. Beat &eCommander Mars&f.']))
lq.append(Q('s_saturn', '&9Commander Saturn', 'cobblemon:black_glasses', [T.adv(RC + 'defeat_commander_saturn_1', 'Defeat Commander Saturn')], elite, deps=['s_galactic'], pos=(12, 3), optional=True,
            sub='&7Another Galactic commander', desc=['&fDefeat &eCommander Saturn&f of Team Galactic.']))
lq.append(Q('s_jupiter', '&9Commander Jupiter', 'cobblemon:black_glasses', [T.adv(RC + 'defeat_commander_jupiter_1', 'Defeat Commander Jupiter')], elite, deps=['s_saturn'], pos=(15, 3), optional=True,
            sub='&7The third commander', desc=['&fDefeat &eCommander Jupiter&f of Team Galactic.']))
lq.append(Q('s_cyrus', '&9Red Dwarf', 'minecraft:nether_star', [T.adv(RC + 'defeat_team_galactic_boss_cyrus_1', 'Defeat Galactic Boss Cyrus')], champ, deps=['s_jupiter'], pos=(18, 3), optional=True, shape='hexagon', size=1.25,
            sub='&7The boss of Team Galactic', desc=['&fDefeat &eCyrus&f, the leader of Team Galactic, for the first time.']))

# ---- Borrius row (y = 6) ----
borrius = [('alice', 'defeat_leader_alice', '&bWings Badge', 'Leader Alice', 'Flying'), ('benjamin', 'defeat_leader_benjamin', '&dTime Badge', 'Leader Benjamin', 'Psychic'),
           ('bigmo', 'defeat_leader_big_mo', '&6Ring Badge', 'Leader Big Mo', 'Fighting'), ('galavan', 'defeat_leader_galavan', '&eBattery Badge', 'Leader Galavan', 'Electric'),
           ('mel', 'defeat_leader_mel', '&cFall Badge', 'Leader Mel', 'Fire'), ('mirskle', 'defeat_leader_mirskle', '&aLeaf Badge', 'Leader Mirskle', 'Grass'),
           ('tessy', 'defeat_leader_tessy', '&5Swamp Badge', 'Leader Tessy', 'Poison'), ('vega', 'defeat_leader_vega', '&dVision Badge', 'Leader Vega', 'Mixed')]
prev = 'first_win'
for i, (key, adv, title, leader, t) in enumerate(borrius):
    lq.append(badge('b_' + key, adv, title, leader, t, 'Borrius', (6 + i * 3, 6), [prev], small))
    prev = 'b_' + key
be4 = [('arabella', 'defeat_elite_four_arabella', '&dElite Four: Arabella', 'Fairy'), ('elias', 'defeat_elite_four_elias', '&cElite Four: Elias', 'Dragon'),
       ('moleman', 'defeat_elite_four_moleman', '&6Elite Four: Moleman', 'Ground'), ('penny', 'defeat_elite_four_penny', '&bElite Four: Penny', 'Mixed')]
for i, (key, adv, title, t) in enumerate(be4):
    lq.append(Q('b_' + key, title, ICONS[t], [T.adv(RC + adv, f'Defeat Elite Four {key.capitalize()}')], elite, deps=[prev], pos=(30 + i * 3, 6),
                sub=f'&7Borrius Elite Four', desc=[f'&fDefeat &e{key.capitalize()}&f of the Borrius Elite Four.']))
    prev = 'b_' + key
lq.append(Q('b_champ', '&6Borrius Champion', 'cobblemon:master_ball', [T.adv(RC + 'defeat_champion_jax', 'Defeat Champion Jax')], champ, deps=[prev], pos=(42, 6),
            shape='hexagon', size=1.5, sub='&7The top of the Borrius series', desc=['&fDefeat &eChampion Jax&f at the end of the Borrius (Unbound) series. Borrius unlocks after you finish the Kanto or Sinnoh series.']))
lq.append(Q('b_shadows', '&8The Shadows', 'cobblemon:black_glasses', [T.adv(RC + 'defeat_shadow_admin_ivory_1', 'Defeat Shadow Admin Ivory')], elite, deps=['b_galavan'], pos=(15, 9), optional=True,
            sub='&7Borrius\' villain team', desc=['&fThe Shadows plot across Borrius. Beat &eAdmin Ivory&f.']))
lq.append(Q('b_marlon', '&8Shadow Admin Marlon', 'cobblemon:black_glasses', [T.adv(RC + 'defeat_shadow_admin_marlon_1', 'Defeat Shadow Admin Marlon')], elite, deps=['b_shadows'], pos=(18, 9), optional=True,
            sub='&7Another Shadow admin', desc=['&fDefeat &eAdmin Marlon&f of The Shadows.']))
lq.append(Q('b_zeph', '&8Dark Omen', 'minecraft:wither_skeleton_skull', [T.adv(RC + 'defeat_boss_zeph_1', 'Defeat Shadow Boss Zeph')], champ, deps=['b_marlon'], pos=(21, 9), optional=True, shape='hexagon', size=1.25,
            sub='&7The boss of The Shadows', desc=['&fDefeat &eShadow Boss Zeph&f for the first time.']))

# ---- finale ----
lq.append(Q('all_elite', '&6Elite Trainer', 'minecraft:diamond', [T.adv(RC + 'defeat_all_elite_four', 'Defeat every Elite Four member')], [R.item('cobblemon:master_ball', 1), R.item('cobblemon:rare_candy', 10), R.xp(1000)],
            deps=['k_champ', 's_champ', 'b_champ'], pos=(48, 1), shape='hexagon', size=1.5, hide_lines=True, sub='&7All twelve Elite Four trainers beaten',
            desc=['&fDefeat all twelve Elite Four members of Kanto, Sinnoh and Borrius.']))
lq.append(Q('all_champ', '&6True Champ', 'minecraft:nether_star', [T.adv(RC + 'defeat_all_champion', 'Defeat every champion')], [R.item('cobblemon:master_ball', 2), R.item('minecraft:enchanted_golden_apple', 3), R.xp(2000)],
            deps=['all_elite'], pos=(48, 5), shape='gear', size=1.5, sub='&7Champion of all three regions',
            desc=['&fDefeat every champion: Terry, Cynthia and Jax. The highest honor of the Trainer League.']))

league = dict(file='33_trainer_league', title='&e> Trainer League&r', group=G_BATTLE, order=55, icon='rctmod:trainer_card',
              sub='&7Badges, Elite Four and champions of Kanto, Sinnoh and Borrius.', quests=lq, autofocus='intro')
