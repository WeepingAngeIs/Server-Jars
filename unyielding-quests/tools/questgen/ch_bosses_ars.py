from gen import Q, T, R, poke_icon

G_COMBAT = '6E41A6B769F20602'  # Combat and Relics
CA = 'cataclysm:'
BM = 'bosses_of_mass_destruction:'
AN = 'ars_nouveau:'

# =============================== Boss Hunts ===================================
bq = []
bq.append(Q('intro', '&cOverworld Titans', 'minecraft:diamond_sword', [T.check('Read this, then press Check')], [R.item('minecraft:golden_apple', 2), R.xp(40)],
            pos=(0, 0), shape='hexagon', size=1.5, sub='&7The big fights outside the Nether and the End',
            desc=['&fL_Ender\'s Cataclysm, Bosses of Mass Destruction and Ars Nouveau add real boss fights to the Overworld. Each one lives in its own structure and drops gear you cannot get anywhere else.',
                  '&7Bring enchanted armor, golden apples, a bow and a full team. Nether and End bosses have their own chapters.']))
# Cataclysm overworld
bq.append(Q('factory', '&7The Ancient Factory', 'minecraft:iron_block', [T.adv(CA + 'find_ancient_factory', 'Find the Ancient Factory')], [R.item('minecraft:iron_ingot', 12), R.xp(100)],
            deps=['intro'], pos=(3, -3), sub='&7A buried machine fortress in snowy mountains',
            desc=['&fThe &bAncient Factory&f is a huge industrial ruin under cold mountains. Robots patrol its halls; the &cHarbinger&f sleeps in its core.']))
bq.append(Q('harbinger', '&cThe Harbinger', 'minecraft:netherite_scrap', [T.adv(CA + 'kill_harbinger', 'Defeat the Harbinger')], [R.item('minecraft:netherite_ingot', 1), R.item('minecraft:enchanted_golden_apple', 1), R.xp(500)],
            deps=['factory'], pos=(6, -3), shape='hexagon', size=1.5, sub='&7A war machine armed with lasers and missiles',
            desc=['&fThe &cHarbinger&f fires lasers, wither missiles and howitzer shells. Keep moving, break line of sight behind pillars and strike when it lands.',
                  '&7It drops the &eWitherite&f material used for Cataclysm weapons.']))
bq.append(Q('sunken', '&9The Sunken City', 'minecraft:prismarine_shard', [T.adv(CA + 'find_sunken_city', 'Find the Sunken City')], [R.item('minecraft:prismarine_crystals', 8), R.xp(100)],
            deps=['intro'], pos=(3, 0), sub='&7Deepling ruins at the bottom of the ocean',
            desc=['&fThe &bSunken City&f lies deep in the ocean. Deeplings, priests and brutes guard it; the &cLeviathan&f waits in the center.']))
bq.append(Q('leviathan', '&9The Leviathan', 'minecraft:heart_of_the_sea', [T.adv(CA + 'kill_leviathan', 'Defeat the Leviathan')], [R.item('minecraft:heart_of_the_sea', 1), R.item('minecraft:enchanted_golden_apple', 1), R.xp(500)],
            deps=['sunken'], pos=(6, 0), shape='hexagon', size=1.5, sub='&7Only fights in the water',
            desc=['&fThe &cLeviathan&f is invincible outside water, so the whole fight is underwater. Bring Water Breathing and Respiration, and dodge its abyss blasts and tentacles.',
                  '&7It drops the &eAbyssal Sacrifice&f and materials for the Tidal Claws.']))
bq.append(Q('pyramid', '&6The Cursed Pyramid', 'minecraft:sandstone', [T.adv(CA + 'find_cursed_pyramid', 'Find the Cursed Pyramid')], [R.item('minecraft:gold_ingot', 8), R.xp(100)],
            deps=['intro'], pos=(3, 3), sub='&7Deep in the desert',
            desc=['&fThe &bCursed Pyramid&f rises out of the desert. Koboletons and the Wadjet guard its chambers; the &cAncient Remnant&f rests at the top.']))
bq.append(Q('remnant', '&6The Ancient Remnant', 'minecraft:bone', [T.adv(CA + 'kill_remnant', 'Defeat the Ancient Remnant')], [R.item('minecraft:diamond', 6), R.item('minecraft:enchanted_golden_apple', 1), R.xp(500)],
            deps=['pyramid'], pos=(6, 3), shape='hexagon', size=1.5, sub='&7A skeletal beast that shakes the ground',
            desc=['&fThe &cAncient Remnant&f charges, stomps earthquakes and summons sandstorms. Fight it in the open and watch for its roar.',
                  '&7It drops the &eRemnant Skull&f and &eNecklace of the Desert&f materials.']))
bq.append(Q('prison', '&bThe Frosted Prison', 'minecraft:packed_ice', [T.adv(CA + 'find_frosted_prison', 'Find the Frosted Prison')], [R.item('minecraft:packed_ice', 8), R.xp(100)],
            deps=['intro'], pos=(3, 6), sub='&7A frozen fortress in the mountains',
            desc=['&fThe &bFrosted Prison&f is a frozen keep full of draugr. The &cMaledictus&f, a cursed knight, is locked in its throne room.']))
bq.append(Q('maledictus', '&bThe Maledictus', 'minecraft:iron_axe', [T.adv(CA + 'kill_maledictus', 'Defeat the Maledictus')], [R.item('minecraft:diamond', 6), R.item('minecraft:enchanted_golden_apple', 1), R.xp(500)],
            deps=['prison'], pos=(6, 6), shape='hexagon', size=1.5, sub='&7A knight with a cursed axe',
            desc=['&fThe &cMaledictus&f swings a huge axe and throws flare bombs. It has two phases; keep your distance when it raises its weapon.',
                  '&7It drops the &eCursed Bow&f and &eAxe Blade&f materials.']))
bq.append(Q('acropolis', '&dThe Acropolis', 'minecraft:quartz_block', [T.adv(CA + 'find_acropolis', 'Find the Acropolis')], [R.item('minecraft:quartz', 12), R.xp(100)],
            deps=['intro'], pos=(3, 9), sub='&7A temple on the water',
            desc=['&fThe &bAcropolis&f is a white temple standing in the sea. Storm serpents and clawdians guard it; &cScylla&f rules it.']))
bq.append(Q('scylla', '&dScylla', 'minecraft:trident', [T.adv(CA + 'kill_scylla', 'Defeat Scylla')], [R.item('minecraft:trident', 1), R.item('minecraft:enchanted_golden_apple', 1), R.xp(600)],
            deps=['acropolis'], pos=(6, 9), shape='hexagon', size=1.5, sub='&7Lightning, waves and tentacles',
            desc=['&fScylla fights with lightning storms, water spears and giant waves, in and out of the water. Bring Water Breathing and keep clear of her tentacle slams.']))
bq.append(Q('clawdian', '&dClawdian', 'minecraft:nautilus_shell', [T.adv(CA + 'kill_clawdian', 'Defeat a Clawdian')], [R.item('minecraft:prismarine_crystals', 8), R.xp(200)],
            deps=['acropolis'], pos=(9, 9), optional=True, sub='&7Scylla\'s crab guardians', desc=['&fDefeat a &cClawdian&f, the armored crab knights that patrol the Acropolis.']))
bq.append(Q('all_bosses', '&6Cataclysm Conqueror', 'minecraft:nether_star', [T.adv(CA + 'kill_all_bosses', 'Defeat every Cataclysm boss')], [R.item('minecraft:netherite_ingot', 2), R.item('cobblemon:master_ball', 1), R.xp(1500)],
            deps=['harbinger', 'leviathan', 'remnant', 'maledictus', 'scylla'], pos=(9, 3), shape='gear', size=1.5, hide_lines=True, sub='&7Every Cataclysm boss, including Nether and End',
            desc=['&fDefeat &eevery&f Cataclysm boss: Ender Guardian, Netherite Monstrosity, Ignis, the Harbinger, the Leviathan, the Ancient Remnant, the Maledictus and Scylla.',
                  '&7The Nether and End ones are in their own chapters.']))
# BOMD
bq.append(Q('lich_tower', '&5The Lich Tower', 'minecraft:soul_lantern', [T.struct(BM + 'lich_tower', 'Find the Night Lich\'s tower')], [R.item('minecraft:soul_lantern', 4), R.xp(100)],
            deps=['intro'], pos=(12, -3), sub='&7A floating tower in the night sky',
            desc=['&fThe &bLich Tower&f floats high above snowy plains. Climb the chains to reach the &cNight Lich&f at the top.']))
bq.append(Q('night_lich', '&5The Night Lich', BM + 'soul_star', [T.adv(BM + 'adventure/night_lich_defeat', 'Defeat the Night Lich')], [R.item('minecraft:enchanted_golden_apple', 1), R.item('minecraft:diamond', 6), R.xp(500)],
            deps=['lich_tower'], pos=(15, -3), shape='hexagon', size=1.5, sub='&7Comets, blue fire and teleports',
            desc=['&fThe &cNight Lich&f throws comets and blue fireballs and teleports around its arena. Dodge the comet drops and hit it between attacks.',
                  '&7It drops the &eSoul Star&f and &eAncient Anima&f.']))
bq.append(Q('blossom', '&aThe Void Blossom', BM + 'void_thorn', [T.adv(BM + 'adventure/void_blossom_defeat', 'Defeat the Void Blossom')], [R.item('minecraft:enchanted_golden_apple', 1), R.item('minecraft:emerald', 8), R.xp(500)],
            deps=['intro'], pos=(12, 0), shape='hexagon', size=1.5, sub='&7A giant flower in a cave of spikes',
            desc=['&fThe &cVoid Blossom&f grows in a huge underground cavern in jungles. It fires petal blades and spores and heals from its roots. Break the roots, then hit the flower when it opens.',
                  '&7It drops the &eVoid Thorn&f and &eCrystal Fruit&f.']))
# Ars Nouveau chimera
bq.append(Q('chimera', '&2The Wilden Chimera', AN + 'wilden_tribute', [T.kill(AN + 'wilden_boss', 1, 'Defeat the Wilden Chimera')], [R.item('minecraft:enchanted_golden_apple', 1), R.item(AN + 'source_gem', 8), R.xp(500)],
            deps=['intro'], pos=(12, 3), shape='hexagon', size=1.5, sub='&7Summoned with a Wilden Tribute',
            desc=['&fCraft a &eWilden Tribute&f from Wilden horns, spikes and wings and burn it in a &eRitual Brazier&f to summon the &cWilden Chimera&f.',
                  '&7It flies, charges, spits and summons wilden. Its drops unlock the strongest Ars Nouveau threads and the Enchanter\'s armor upgrades.']))
# Weeping Angels
bq.append(Q('catacombs', '&8The Catacombs', 'minecraft:stone_bricks', [T.struct('weeping_angels:catacombs', 'Find the Weeping Angel catacombs')], [R.item('minecraft:torch', 32), R.xp(120)],
            deps=['intro'], pos=(12, 6), sub='&7Don\'t blink',
            desc=['&fThe &bCatacombs&f are dark tunnels under the ground where &cWeeping Angels&f stand as statues. They only move when you are not looking.',
                  '&7Keep them in sight, keep the torches up and never turn your back.']))
bq.append(Q('angels', '&8Don\'t Blink', 'weeping_angels:timey_wimey_detector', [T.kill('weeping_angels:weeping_angel', 5, 'Defeat 5 Weeping Angels')], [R.item('minecraft:diamond', 3), R.xp(300)],
            deps=['catacombs'], pos=(15, 6), sub='&7Five angels, no blinking',
            desc=['&fWeeping Angels are stone while you look at them and strike the moment you look away. They can take you back in time or steal your light.',
                  '&7A pickaxe hurts them while they are stone. Defeat five.']))
bq.append(Q('elder', '&9Elder Guardian', 'minecraft:sponge', [T.kill('minecraft:elder_guardian', 1, 'Defeat an Elder Guardian')], [R.item('minecraft:sponge', 4), R.xp(200)],
            deps=['intro'], pos=(12, 9), optional=True, sub='&7The ocean monument boss', desc=['&fDefeat an &cElder Guardian&f in an ocean monument. Mining Fatigue is its curse; milk cures it.']))
bq.append(Q('warden', '&3The Warden', 'minecraft:sculk_catalyst', [T.kill('minecraft:warden', 1, 'Defeat a Warden')], [R.item('minecraft:enchanted_golden_apple', 1), R.item('minecraft:echo_shard', 2), R.xp(600)],
            deps=['intro'], pos=(15, 9), optional=True, shape='hexagon', size=1.25, sub='&7Most people run. You fight.',
            desc=['&fThe &cWarden&f rises from sculk in the deep dark when shriekers are triggered. It is blind, deaf to sneaking and hits harder than anything in vanilla. Defeat one.']))

bosses = dict(file='34_boss_hunts', title='&c> Boss Hunts&r', group=G_COMBAT, order=60, icon='minecraft:netherite_sword',
              sub='&7The Overworld\'s biggest fights: Cataclysm, Bosses of Mass Destruction, the Chimera and more.', quests=bq, autofocus='intro')

# =============================== Ars Nouveau ===================================
aq = []
aq.append(Q('tome', '&dThe Tattered Tome', AN + 'worn_notebook', [T.item(AN + 'worn_notebook', 1, 'Craft the Tattered Tome')], [R.item(AN + 'source_gem', 4), R.xp(40)],
            pos=(0, 0), shape='hexagon', size=1.5, sub='&7Your guide to Ars Nouveau magic',
            desc=['&fCraft the &eTattered Tome&f (a book and a Source Gem) and read it: it explains every block, glyph and creature of Ars Nouveau.',
                  '&7&eSource Gems&f come from Source Gem ore underground, Archwood trees and Sourceberries.']))
aq.append(Q('novice', '&dNovice Spell Book', AN + 'novice_spell_book', [T.adv(AN + 'novice_spell_book', 'Craft a Novice Spell Book')], [R.item(AN + 'source_gem', 6), R.xp(80)],
            deps=['tome'], pos=(3, 0), sub='&7Your first spells',
            desc=['&fCraft a &eNovice Spell Book&f. Press the spell book key (default C) to open it and combine glyphs into spells: for example &eProjectile → Harm&f.',
                  '&7The book starts with a few glyphs; learn more at the Scribe\'s Table.']))
aq.append(Q('scribes', '&dScribe\'s Table', AN + 'scribes_table', [T.item(AN + 'scribes_table', 1, 'Craft a Scribe\'s Table')], [R.item('minecraft:paper', 8), R.xp(60)],
            deps=['novice'], pos=(6, 0), sub='&7Learn new glyphs',
            desc=['&fPlace a &eScribe\'s Table&f and put a &eBlank Glyph&f on it. Throw the items a glyph asks for onto the table (see the tome) and the table writes the glyph for your book.']))
aq.append(Q('core', '&dArcane Core', AN + 'arcane_core', [T.item(AN + 'arcane_core', 1, 'Craft an Arcane Core')], [R.item(AN + 'source_gem', 6), R.xp(60)],
            deps=['novice'], pos=(3, 3), sub='&7The heart of magic machines', desc=['&fThe &eArcane Core&f powers the Imbuement Chamber and the Enchanting Apparatus.']))
aq.append(Q('imbue', '&dImbuement Chamber', AN + 'imbuement_chamber', [T.adv(AN + 'imbuement_chamber', 'Craft an Imbuement Chamber')], [R.item(AN + 'source_gem', 8), R.xp(80)],
            deps=['core'], pos=(6, 3), sub='&7Turns gems into essences',
            desc=['&fThe &eImbuement Chamber&f fills with Source from nearby jars and turns Lapis into &eSource Gems&f and gems into &eessences&f for glyphs.']))
aq.append(Q('jar', '&9Source Jar', AN + 'source_jar', [T.adv(AN + 'source_jar', 'Craft a Source Jar')], [R.item(AN + 'source_gem', 6), R.xp(60)],
            deps=['core'], pos=(3, 6), sub='&7Stores the magic that powers everything',
            desc=['&fA &eSource Jar&f stores Source. Fill it with a &eSourcelink&f: the Agronomic link eats crops, the Volcanic link burns Archwood, the Vitalic link takes it from animals.']))
aq.append(Q('apparatus', '&dEnchanting Apparatus', AN + 'enchanting_apparatus', [T.adv(AN + 'enchanting_apparatus', 'Build an Enchanting Apparatus')], [R.item(AN + 'source_gem', 10), R.xp(120)],
            deps=['imbue'], pos=(9, 3), sub='&7Pedestals around a core',
            desc=['&fPlace an &eEnchanting Apparatus&f on an Arcane Core with &eArcane Pedestals&f around it. Put the items on the pedestals and the main item in the middle to craft charms, wands and the bigger spell books.']))
aq.append(Q('magebloom', '&aMagebloom', AN + 'magebloom', [T.adv(AN + 'magebloom_crop', 'Grow Magebloom')], [R.item(AN + 'magebloom_fiber', 4), R.xp(80)],
            deps=['apparatus'], pos=(12, 3), sub='&7A flower made with magic', desc=['&fCraft a &eMagebloom Seed&f in the Enchanting Apparatus and grow it. Magebloom Fiber makes the Sorcerer robes and magic threads.']))
aq.append(Q('robes', '&dSorcerer\'s Robes', AN + 'sorcerer_robes', [T.anyof([AN + 'sorcerer_hood', AN + 'sorcerer_robes', AN + 'sorcerer_leggings', AN + 'sorcerer_boots'], 'Craft a piece of Sorcerer armor')], [R.item(AN + 'magebloom_fiber', 6), R.xp(120)],
            deps=['magebloom'], pos=(15, 3), sub='&7More mana, cheaper spells', desc=['&fCraft any piece of &eSorcerer\'s&f armor from Magebloom Fiber. Magic armor raises your mana and can hold threads with extra effects.']))
aq.append(Q('apprentice', '&dMage\'s Spell Book', AN + 'apprentice_spell_book', [T.adv(AN + 'apprentice_spell_book', 'Upgrade to the Mage\'s Spell Book')], [R.item(AN + 'source_gem', 10), R.xp(150)],
            deps=['apparatus'], pos=(9, 0), sub='&7Tier two spells', desc=['&fUpgrade your book in the Enchanting Apparatus: the &eMage\'s Spell Book&f unlocks tier-two glyphs and more spell slots.']))
aq.append(Q('archmage', '&5Archmage Spell Book', AN + 'archmage_spell_book', [T.adv(AN + 'archmage_spell_book', 'Upgrade to the Archmage Spell Book')], [R.item(AN + 'source_gem', 16), R.item('minecraft:enchanted_golden_apple', 1), R.xp(400)],
            deps=['apprentice', 'chimera_ref'], pos=(15, 0), shape='hexagon', size=1.5, sub='&7The strongest spell book', desc=['&fThe &eArchmage Spell Book&f needs a &eWilden Tribute&f: beat the Wilden Chimera for its parts. It unlocks every glyph tier.']))
aq.append(Q('chimera_ref', '&2Wilden Tribute', AN + 'wilden_tribute', [T.item(AN + 'wilden_tribute', 1, 'Craft a Wilden Tribute')], [R.item(AN + 'source_gem', 8), R.xp(150)],
            deps=['apparatus'], pos=(12, 0), sub='&7Horns, spikes and wings', desc=['&fHunt Wilden in the forests: &eWilden Hunters&f drop horns, &eStalkers&f wings and &eDefenders&f spikes. Craft them into a &eWilden Tribute&f and burn it in a Ritual Brazier to call the Chimera (see Boss Hunts).']))
aq.append(Q('starbuncle', '&6Starbuncle Charm', AN + 'starbuncle_charm', [T.adv(AN + 'starby_charm', 'Tame a Starbuncle')], [R.item('minecraft:gold_nugget', 16), R.xp(100)],
            deps=['apparatus'], pos=(3, 9), sub='&7A golden helper that carries items', desc=['&fGive a wild &eStarbuncle&f a gold nugget to tame it, then summon it with its charm. Starbuncles move items between chests and love shiny things.']))
aq.append(Q('whirlisprig', '&aWhirlisprig Charm', AN + 'whirlisprig_charm', [T.adv(AN + 'whirlisprig_charm', 'Tame a Whirlisprig')], [R.item('minecraft:bone_meal', 16), R.xp(100)],
            deps=['apparatus'], pos=(6, 9), sub='&7A plant spirit that grows your farm', desc=['&fFeed a wild &eWhirlisprig&f a sapling to tame it. It makes nearby crops and trees grow and drops seeds and flowers.']))
aq.append(Q('drygmy', '&2Drygmy Charm', AN + 'drygmy_charm', [T.adv(AN + 'drygmy_charm', 'Tame a Drygmy')], [R.item(AN + 'source_gem', 6), R.xp(100)],
            deps=['apparatus'], pos=(9, 9), sub='&7Gets drops from mobs without killing them', desc=['&fFeed a wild &eDrygmy&f a Wilden Horn to tame it. Put mobs near its home and it gathers their drops, Pokémon included.']))
aq.append(Q('wixie', '&dWixie Charm', AN + 'wixie_charm', [T.adv(AN + 'wixie_charm', 'Tame a Wixie')], [R.item('minecraft:cauldron', 1), R.xp(100)],
            deps=['apparatus'], pos=(12, 9), sub='&7A tiny chef and potion brewer', desc=['&fFind a Wixie in a &eWixie Cauldron&f (crafted in the apparatus). It crafts items and brews potions from what you put in nearby chests.']))
aq.append(Q('familiar', '&dA Familiar', AN + 'familiar_starbuncle', [T.adv(AN + 'familiar', 'Summon a familiar')], [R.item(AN + 'source_gem', 6), R.xp(120)],
            deps=['starbuncle'], pos=(3, 12), optional=True, sub='&7A magical pet that follows you', desc=['&fPerform the &eBinding&f ritual with a tamed creature\'s charm to turn it into a &efamiliar&f. Familiars follow you and give bonuses.']))
aq.append(Q('brazier', '&cRitual Brazier', AN + 'ritual_brazier', [T.adv(AN + 'ritual_brazier', 'Perform a ritual')], [R.item(AN + 'source_gem', 6), R.xp(100)],
            deps=['apparatus'], pos=(6, 12), sub='&7Rituals: weather, growth, summons', desc=['&fPlace a &eRitual Brazier&f, put a ritual tablet in it and light it. Rituals change the weather, grow forests, summon animals and much more.']))
aq.append(Q('warp', '&bWarp Scroll', AN + 'warp_scroll', [T.adv(AN + 'warp_scroll', 'Use a Warp Scroll')], [R.item(AN + 'warp_scroll', 2), R.xp(80)],
            deps=['novice'], pos=(0, 3), sub='&7Teleport home', desc=['&fCraft a &eWarp Scroll&f, sneak-right-click to bind it to a spot, then use it to teleport there.']))
aq.append(Q('turret', '&cSpell Turret', AN + 'basic_spell_turret', [T.adv(AN + 'basic_spell_turret', 'Build a Spell Turret')], [R.item('minecraft:redstone', 16), R.xp(100)],
            deps=['apparatus'], pos=(9, 12), optional=True, sub='&7Automated magic', desc=['&fA &eSpell Turret&f casts a spell you give it whenever it gets a redstone signal: automatic farms, defenses and traps.']))
aq.append(Q('prism', '&dSpell Prism', AN + 'spell_prism', [T.adv(AN + 'spell_prism', 'Use a Spell Prism')], [R.item(AN + 'source_gem', 4), R.xp(80)],
            deps=['turret'], pos=(12, 12), optional=True, sub='&7Bend your spells', desc=['&fA &eSpell Prism&f redirects projectile spells that hit it, so turrets can shoot around corners.']))
aq.append(Q('potion_jar', '&5Potion Jar', AN + 'potion_jar', [T.adv(AN + 'potion_jar', 'Fill a Potion Jar')], [R.item('minecraft:glass_bottle', 8), R.xp(80)],
            deps=['wixie'], pos=(15, 9), optional=True, sub='&7Store potions by the bucket', desc=['&fA &ePotion Jar&f stores up to 100 potions of one kind. Wixies and Potion Melders fill them; Potion Diffusers spray them.']))
aq.append(Q('wand', '&dEnchanter\'s Wand', AN + 'wand', [T.item(AN + 'wand', 1, 'Craft an Enchanter\'s Wand')], [R.item(AN + 'source_gem', 8), R.xp(150)],
            deps=['apprentice'], pos=(9, -3), optional=True, sub='&7A wand loaded with a spell', desc=['&fCraft the &eEnchanter\'s Wand&f in the apparatus and inscribe a spell on it. Its spells always start with Projectile and are cheaper to cast.']))
aq.append(Q('sword', '&dEnchanter\'s Sword', AN + 'enchanters_sword', [T.item(AN + 'enchanters_sword', 1, 'Craft an Enchanter\'s Sword')], [R.item('minecraft:diamond', 2), R.xp(150)],
            deps=['apprentice'], pos=(12, -3), optional=True, sub='&7Casts a spell on every hit', desc=['&fThe &eEnchanter\'s Sword&f casts the spell inscribed on it each time you hit something: Harm, Ignite, Freeze, your choice.']))
aq.append(Q('mirror', '&dEnchanter\'s Mirror', AN + 'enchanters_mirror', [T.item(AN + 'enchanters_mirror', 1, 'Craft an Enchanter\'s Mirror')], [R.item(AN + 'source_gem', 8), R.xp(150)],
            deps=['apprentice'], pos=(15, -3), optional=True, sub='&7Casts a spell on yourself', desc=['&fThe &eEnchanter\'s Mirror&f casts its spell on you: perfect for Heal, Haste or Jump Boost with one click.']))
aq.append(Q('belt', '&dBelt of Levitation', AN + 'belt_of_levitation', [T.item(AN + 'belt_of_levitation', 1, 'Craft a Belt of Levitation')], [R.item('minecraft:feather', 8), R.xp(150)],
            deps=['apprentice'], pos=(18, -3), optional=True, sub='&7Float with the sneak key', desc=['&fWear the &eBelt of Levitation&f and hold sneak in the air to float slowly, using your mana.']))
aq.append(Q('portal', '&5Warp Portal', AN + 'portal', [T.adv(AN + 'create_portal', 'Create a Warp Portal')], [R.item(AN + 'source_gem', 10), R.xp(200)],
            deps=['warp'], pos=(0, 6), optional=True, sub='&7A permanent gateway', desc=['&fCraft a &eStabilized Warp Scroll&f and throw it into a decorated portal frame to make a permanent &eWarp Portal&f between two places.']))
aq.append(Q('mythical', '&6Mythical Clay', AN + 'mythical_clay', [T.item(AN + 'mythical_clay', 1, 'Craft Mythical Clay')], [R.item(AN + 'source_gem', 12), R.xp(250)],
            deps=['archmage'], pos=(18, 0), optional=True, sub='&7Top-tier upgrade material', desc=['&fCraft &eMythical Clay&f in the Enchanting Apparatus from Marvelous Clay and Nether Stars. It upgrades armor to the highest tier.']))

ars = dict(file='35_ars_nouveau', title='&d> Ars Nouveau&r', group=G_COMBAT, order=65, icon='ars_nouveau:novice_spell_book',
           sub='&7Spell books, magical machines and tamed helpers.', quests=aq, autofocus='tome')
