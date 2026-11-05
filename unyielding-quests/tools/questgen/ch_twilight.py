from gen import Q, T, R, poke_icon

TF = 'twilightforest:'
G_DIM = '6E41A6B769F20604'  # Dimensions and Creatures

qs = []
# ---- main progression line (y = 0) ----
qs.append(Q('portal', '&bThe Twilight Portal', TF + 'twilight_portal_miniature_structure',
            [T.dim(TF + 'twilight_forest', 'Step through a Twilight Portal')],
            [R.xp(60), R.item('minecraft:torch', 16), R.item('cobblemon:poke_ball', 6)],
            pos=(0, 0), shape='hexagon', size=1.5, sub='&7A dark, endless forest under an eternal dusk',
            desc=['&fDig a &e2×2 hole&f, fill it with &bwater&f and surround all 12 edge blocks with &aflowers, grass or mushrooms&f. Throw a &bDiamond&f into the pool: lightning strikes and the portal opens.',
                  '', '&7The Twilight Forest is always dusk. Hollow Hills, a hedge maze, giant trees and the Naga are all nearby when you arrive.',
                  '&7Wild Pokémon spawn here too, so bring Poké Balls.']))
qs.append(Q('naga', '&cThe Naga', TF + 'naga_trophy',
            [T.kill(TF + 'naga', 1, 'Defeat the Naga')],
            [R.xp(150), R.item('minecraft:golden_apple', 2), R.item('cobblemon:ultra_ball', 4)],
            deps=['portal'], pos=(3, 0), shape='hexagon', size=1.25, sub='&7The serpent of the stone courtyard',
            desc=['&fFind the &bNaga Courtyard&f: a ring of mossy stone walls out in the forest. The &cNaga&f rushes around the inside at high speed.',
                  '&7Fight it from a corner so it cannot build up a charge, and watch its tail segments. It drops &eNaga Scales&f, the first boss material.']))
qs.append(Q('lich', '&cThe Lich', TF + 'lich_trophy',
            [T.kill(TF + 'lich', 1, 'Defeat the Lich')],
            [R.xp(250), R.item('minecraft:golden_apple', 3), R.item('cobblemon:rare_candy', 3)],
            deps=['naga'], pos=(6, 0), shape='hexagon', size=1.25, sub='&7Climb the Lich Tower to the top floor',
            desc=['&fThe &bLich Tower&f is a tall stone tower full of staircases, ghosts and death tomes. The &cLich&f waits at the very top.',
                  '&7Phase 1: it hides behind copies of itself and keeps a shield up. Break the shield, then hit the real one. It drops a &eScepter&f.',
                  '&cYou cannot hurt the Lich until the Naga is dead.&7 Twilight bosses must be done in order.']))
qs.append(Q('minoshroom', '&cThe Minoshroom', TF + 'minoshroom_trophy',
            [T.kill(TF + 'minoshroom', 1, 'Defeat the Minoshroom')],
            [R.xp(250), R.item('minecraft:golden_apple', 3), R.item('cobblemon:exp_candy_l', 3)],
            deps=['lich'], pos=(9, 0), shape='hexagon', size=1.25, sub='&7Deep inside the Labyrinth under a swamp',
            desc=['&fThe &bLabyrinth&f is buried under the swamp: a two-level maze of mazestone, minotaurs and slimes. The &cMinoshroom&f guards the bottom level.',
                  '&7Dig straight down from a swamp hill to find the maze, then search for the big room with mushroom decorations.',
                  '&7Beware: swamp mosquitoes give you Hunger if the Lich is still alive.']))
qs.append(Q('hydra', '&cThe Hydra', TF + 'hydra_trophy',
            [T.kill(TF + 'hydra', 1, 'Defeat the Hydra')],
            [R.xp(400), R.item('minecraft:enchanted_golden_apple', 1), R.item('cobblemon:rare_candy', 5)],
            deps=['minoshroom'], pos=(12, 0), shape='hexagon', size=1.5, sub='&7Three heads, fire breath and a whole lot of HP',
            desc=['&fThe &cHydra&f sleeps in a huge &bHydra Lair&f dome in the Fire Swamp. Attack a head while its mouth is open, and dodge its mortars.',
                  '&7Bring fire resistance potions and a bow. Each head bites, breathes fire or spits lava bombs.',
                  '&7It drops &eHydra Chops&f (great food) and &eFiery Blood&f, which smelts into Fiery Ingots.']))
qs.append(Q('pedestal', '&bThe Trophy Pedestal', TF + 'trophy_pedestal',
            [T.adv(TF + 'progress_trophy_pedestal', 'Place a boss trophy on the Trophy Pedestal')],
            [R.xp(100), R.item('minecraft:diamond', 3)],
            deps=['hydra'], pos=(15, 0), sub='&7Prove yourself to enter the Dark Forest strongholds',
            desc=['&fThe &bGoblin Knight Stronghold&f in the Dark Forest is sealed. Place any &eboss trophy&f on the &eTrophy Pedestal&f at its entrance to open the way.',
                  '&7The pedestal only accepts a trophy after the Lich, Minoshroom and Hydra are all beaten.']))
qs.append(Q('knights', '&cThe Knight Phantoms', TF + 'knight_phantom_trophy',
            [T.adv(TF + 'progress_knights', 'Defeat all six Knight Phantoms')],
            [R.xp(350), R.item('minecraft:golden_apple', 3), R.item('cobblemon:dusk_ball', 6)],
            deps=['pedestal'], pos=(18, 0), shape='hexagon', size=1.25, sub='&7Six ghostly knights circling their treasure',
            desc=['&fIn the lowest room of the &bGoblin Knight Stronghold&f, six &cKnight Phantoms&f fly in a circle around a chest.',
                  '&7They throw axes and charge. Stand in the middle and swing when they pass, or shoot them one by one. All six must fall.',
                  '&7Their chest holds &eKnightmetal&f and the &ePhantom armor&f.']))
qs.append(Q('dark_tower', '&bThe Dark Tower', TF + 'dark_tower_miniature_structure',
            [T.struct(TF + 'dark_tower', 'Enter a Dark Tower')],
            [R.xp(120), R.item('minecraft:ender_pearl', 4)],
            deps=['knights'], pos=(21, 0), sub='&7A towerwood fortress wrapped in protection magic',
            desc=['&fThe &bDark Tower&f stands in the Dark Forest. Its outer wall is protected until the Knight Phantoms are dead, then you can break in.',
                  '&7Inside: carminite golems, ghastlings, vending machines, reappearing blocks and a lot of floors. The top is the Ur-Ghast arena.']))
qs.append(Q('ur_ghast', '&cThe Ur-Ghast', TF + 'ur_ghast_trophy',
            [T.kill(TF + 'ur_ghast', 1, 'Defeat the Ur-Ghast')],
            [R.xp(400), R.item('minecraft:enchanted_golden_apple', 1), R.item('cobblemon:exp_candy_xl', 2)],
            deps=['dark_tower'], pos=(24, 0), shape='hexagon', size=1.5, sub='&7A crying ghast the size of a house',
            desc=['&fThe &cUr-Ghast&f floats above the roof of the Dark Tower, raining fireballs. It is immune to most damage while it cries.',
                  '&7Use the &eGhast Traps&f on the roof: lure ghastlings into them to charge, then fire the trap at the boss to stun it and hit it.',
                  '&7Drops &eFiery Tears&f for Fiery armor and tools.']))
qs.append(Q('alpha_yeti', '&cThe Alpha Yeti', TF + 'alpha_yeti_trophy',
            [T.kill(TF + 'alpha_yeti', 1, 'Defeat the Alpha Yeti')],
            [R.xp(350), R.item('minecraft:golden_apple', 3), R.item('cobblemon:ice_stone', 1)],
            deps=['ur_ghast'], pos=(27, 0), shape='hexagon', size=1.25, sub='&7The giant in the Yeti Cave of the Snowy Forest',
            desc=['&fThe &bYeti Cave&f is a big dark cavern in the Snowy Forest. The &cAlpha Yeti&f throws ice and grabs you to toss you around.',
                  '&7Until the Ur-Ghast is dead the snowy forest freezes you. Afterwards it is safe to go in.',
                  '&7It drops &eAlpha Yeti Fur&f for the Yeti armor set.']))
qs.append(Q('snow_queen', '&cThe Snow Queen', TF + 'snow_queen_trophy',
            [T.kill(TF + 'snow_queen', 1, 'Defeat the Snow Queen')],
            [R.xp(450), R.item('minecraft:enchanted_golden_apple', 1), R.item('cobblemon:rare_candy', 5)],
            deps=['alpha_yeti'], pos=(30, 0), shape='hexagon', size=1.5, sub='&7Ruler of the Aurora Palace on the glacier',
            desc=['&fThe &bAurora Palace&f shines on top of the Glacier. The &cSnow Queen&f floats at the top, shielded by spinning ice blocks.',
                  '&7Break or dodge the ice ring, then hit her when the shield drops. She also drops icicles from above.',
                  '&7Her loot includes the &eTriple Bow&f, &eSeeker Bow&f, &eIce Bow&f and &eEnder Bow&f.']))
qs.append(Q('troll_cave', '&bThe Highlands', TF + 'troll_cave_miniature_structure' if False else TF + 'magic_beans',
            [T.struct(TF + 'troll_cave', 'Explore a Troll Cave in the Highlands')],
            [R.xp(120), R.item('minecraft:iron_ingot', 8)],
            deps=['snow_queen'], pos=(33, 0), sub='&7Troll caves and cloud cottages',
            desc=['&fThe &bHighlands&f are the rocky plateau past the glacier. &bTroll Caves&f wind through the hills, full of trolls and hidden treasure.',
                  '&7The Highlands are only safe once the Snow Queen is beaten; before that the area is cursed.']))
qs.append(Q('troll', '&cThe Troll', TF + 'troll_trophy' if False else 'minecraft:stone_pickaxe',
            [T.kill(TF + 'troll', 1, 'Defeat a Troll')],
            [R.xp(150), R.item('minecraft:emerald', 3)],
            deps=['troll_cave'], pos=(36, 0), sub='&7Big, grey and surprisingly fast',
            desc=['&fTrolls lurk in the troll caves. They hit hard and sometimes drop &eMagic Beans&f.',
                  '&7Knock them back and keep swinging; they are slow to turn.']))
qs.append(Q('lamp', '&eLamp of Cinders', TF + 'lamp_of_cinders',
            [T.item(TF + 'lamp_of_cinders', 1, 'Get the Lamp of Cinders')],
            [R.xp(300), R.item('minecraft:diamond', 4)],
            deps=['troll'], pos=(39, 0), shape='hexagon', size=1.25, sub='&7The key to the Thornlands',
            desc=['&fThe &eLamp of Cinders&f is in the treasure room of a &bGiant\'s cloud cottage&f, reached by climbing a beanstalk grown from Magic Beans.',
                  '&7Right-click it to burn the thorns of the Thornlands and reach the Final Castle.']))
qs.append(Q('final_castle', '&dThe Final Castle', 'minecraft:end_crystal',
            [T.struct(TF + 'final_castle', 'Enter the Final Castle')],
            [R.xp(300), R.item('minecraft:diamond', 4)],
            deps=['lamp'], pos=(42, 0), shape='hexagon', size=1.25, sub='&7Beyond the burning thorns',
            desc=['&fUse the &eLamp of Cinders&f to burn a path through the &bThornlands&f. The huge &dFinal Castle&f stands on the plateau behind them.',
                  '&7The castle is still being built by the Twilight Forest developers: explore it, loot it and watch the Final Plateau.']))
qs.append(Q('plateau', '&dThe Final Plateau', 'minecraft:nether_star',
            [T.adv(TF + 'progression_end', 'Reach the Final Plateau')],
            [R.xp(500), R.item('minecraft:enchanted_golden_apple', 2), R.item('cobblemon:master_ball', 1)],
            deps=['final_castle'], pos=(45, 0), shape='gear', size=1.5, sub='&7The end of the Twilight journey',
            desc=['&fStand on the &dFinal Plateau&f with every Twilight boss behind you. This is the last step of the Twilight Forest progression.',
                  '&7You beat the Naga, the Lich, the Minoshroom, the Hydra, the Knight Phantoms, the Ur-Ghast, the Alpha Yeti and the Snow Queen. Well done, trainer.']))

# ---- side quests above (y = -3) ----
qs.append(Q('hollow_hill', '&bHollow Hills', 'minecraft:glowstone_dust',
            [T.struct(TF + 'small_hollow_hill', 'Explore a Hollow Hill')],
            [R.xp(40), R.item('minecraft:torch', 16)],
            deps=['portal'], pos=(0, -3), sub='&7Hills with a cave of ores and loot inside',
            desc=['&fA &bHollow Hill&f is a big dome hill with a cavern inside: ore stalactites hang from the ceiling and chests are hidden on the floor.',
                  '&7Small hills have redcap goblins and kobolds. Medium and large hills have more loot but also more monsters.']))
qs.append(Q('hill_treasures', '&eHill Treasures', TF + 'ore_magnet',
            [T.anyof([TF + 'ore_magnet', TF + 'peacock_feather_fan', TF + 'moonworm_queen', TF + 'crumble_horn', TF + 'transformation_powder'],
                     'Find one of the hill treasures (Ore Magnet, Peacock Fan, Moonworm Queen, Crumble Horn or Transformation Powder)')],
            [R.xp(80), R.item('cobblemon:great_ball', 6)],
            deps=['hollow_hill'], pos=(0, -6), optional=True, sub='&7Strange tools from hollow hill chests',
            desc=['&fHollow hill chests hide odd magic tools: the &eOre Magnet&f pulls ores out of stone, the &ePeacock Feather Fan&f lets you double jump, the &eMoonworm Queen&f places living light, the &eCrumble Horn&f crumbles blocks.',
                  '&7Any one of them completes this quest.']))
qs.append(Q('hedge_maze', '&aThe Hedge Maze', TF + 'hedge',
            [T.struct(TF + 'hedge_maze', 'Walk into a Hedge Maze')],
            [R.xp(50), R.item('minecraft:shears', 1)],
            deps=['portal'], pos=(-3, -3), sub='&7Hedges, spiders and loot in the middle',
            desc=['&fThe &aHedge Maze&f is a square maze of hedges with chests inside. Hedge spiders and swarm spiders hide in the corners.',
                  '&7The hedge blocks hurt to touch. Shears or an axe get through faster than walking.']))
qs.append(Q('charm', '&eA Lucky Charm', TF + 'charm_of_keeping_1',
            [T.anyof([TF + 'charm_of_keeping_1', TF + 'charm_of_keeping_2', TF + 'charm_of_keeping_3', TF + 'charm_of_life_1', TF + 'charm_of_life_2'],
                     'Get a Charm of Keeping or a Charm of Life')],
            [R.xp(60), R.item('minecraft:golden_carrot', 8)],
            deps=['hollow_hill'], pos=(-3, -6), optional=True, sub='&7Insurance for dangerous trips',
            desc=['&fA &eCharm of Keeping&f saves your items when you die; a &eCharm of Life&f saves you from dying once. Both are found in Twilight chests and crafted from gold.',
                  '&7Keep one in your inventory before every boss.']))
qs.append(Q('quest_ram', '&dThe Questing Ram', TF + 'quest_ram_trophy',
            [T.adv(TF + 'quest_ram', 'Give the Questing Ram all 16 colors of wool')],
            [R.xp(200), R.item('minecraft:diamond', 2)],
            deps=['portal'], pos=(-6, -3), optional=True, sub='&7A rainbow ram in a quiet grove',
            desc=['&fIn the &bQuest Grove&f lives a &dQuesting Ram&f. Right-click it with each of the &e16 wool colors&f.',
                  '&7When it has eaten every color it rewards you with blocks of diamond, iron, gold, emerald and lapis.']))
qs.append(Q('scepters', '&dScepters of the Lich', TF + 'twilight_scepter',
            [T.anyof([TF + 'twilight_scepter', TF + 'lifedrain_scepter', TF + 'zombie_scepter', TF + 'fortification_scepter'],
                     'Get any Lich scepter')],
            [R.xp(100), R.item('minecraft:experience_bottle', 8)],
            deps=['lich'], pos=(6, -3), optional=True, sub='&7Twilight, Lifedrain, Zombie or Fortification',
            desc=['&fThe Lich drops one of four scepters: the &dTwilight Scepter&f shoots ender bolts, the &dLifedrain Scepter&f heals you by draining mobs, the &dZombie Scepter&f summons loyal zombies and the &dFortification Scepter&f makes a shield.',
                  '&7Recharge a scepter by crafting it with an Ender Pearl.']))
qs.append(Q('mazebreaker', '&eThe Mazebreaker', TF + 'mazebreaker_pickaxe',
            [T.item(TF + 'mazebreaker_pickaxe', 1, 'Find the Mazebreaker pickaxe')],
            [R.xp(120), R.item('minecraft:diamond', 2)],
            deps=['lich'], pos=(6, 3), optional=True, sub='&7The only pickaxe that likes mazestone',
            desc=['&fMazestone breaks pickaxes fast. The &eMazebreaker&f, found in the vault rooms of the &bLabyrinth&f, mines it instantly.',
                  '&7Look for the secret vaults behind the walls of the lower maze level.']))
qs.append(Q('fiery_ingot', '&6Fiery Ingots', TF + 'fiery_ingot',
            [T.item(TF + 'fiery_ingot', 2, 'Smelt 2 Fiery Ingots')],
            [R.xp(120), R.item('minecraft:blaze_rod', 4)],
            deps=['hydra'], pos=(12, -3), sub='&7Forged from the Hydra\'s blood',
            desc=['&fSmelt &eFiery Blood&f (from the Hydra) or &eFiery Tears&f (from the Ur-Ghast) into &6Fiery Ingots&f.',
                  '&7They make the Fiery armor, sword and pickaxe: enemies you hit catch fire.']))
qs.append(Q('fiery_sword', '&6A Fiery Weapon', TF + 'fiery_sword',
            [T.anyof([TF + 'fiery_sword', TF + 'fiery_pickaxe'], 'Craft a Fiery Sword or Fiery Pickaxe')],
            [R.xp(150), R.item('minecraft:golden_apple', 2)],
            deps=['fiery_ingot'], pos=(15, -3), sub='&7Burn everything you touch',
            desc=['&fCraft a &6Fiery Sword&f or &6Fiery Pickaxe&f from Fiery Ingots. The sword sets enemies on fire; the pickaxe smelts ores as you mine them.']))
qs.append(Q('knightmetal', '&7Knightmetal', TF + 'knightmetal_ingot',
            [T.item(TF + 'knightmetal_ingot', 4, 'Collect 4 Knightmetal Ingots')],
            [R.xp(120), R.item('minecraft:iron_block', 2)],
            deps=['knights'], pos=(18, -3), sub='&7Armor shards fused into metal',
            desc=['&fGoblin knights in the stronghold drop &eArmor Shards&f. Nine shards make a cluster; smelt the cluster into a &7Knightmetal Ingot&f.',
                  '&7Knightmetal gear deals bonus damage to armored enemies.']))
qs.append(Q('block_chain', '&7Block and Chain', TF + 'block_and_chain',
            [T.item(TF + 'block_and_chain', 1, 'Craft the Block and Chain')],
            [R.xp(150), R.item('minecraft:golden_apple', 2)],
            deps=['knightmetal'], pos=(21, -3), optional=True, sub='&7A spiked ball on a chain',
            desc=['&fThe &7Block and Chain&f is a thrown knightmetal flail: hold right-click to swing it and release to launch it at enemies.']))
qs.append(Q('ghast_trap', '&cGhast Trap', TF + 'ghast_trap',
            [T.adv(TF + 'ghast_trap', 'Fire a charged Ghast Trap')],
            [R.xp(100), R.item('minecraft:ghast_tear', 2)],
            deps=['dark_tower'], pos=(21, 3), sub='&7The Ur-Ghast\'s weak spot',
            desc=['&fOn the Dark Tower roof stand &eGhast Traps&f. Kill &ccarminite ghastlings&f next to a trap to charge it, then step on it to fire a beam that stuns the Ur-Ghast.']))
qs.append(Q('winter_bows', '&bWinter Arsenal', TF + 'ice_bow',
            [T.anyof([TF + 'triple_bow', TF + 'seeker_bow', TF + 'ice_bow', TF + 'ender_bow'], 'Get a Triple, Seeker, Ice or Ender Bow')],
            [R.xp(150), R.item('minecraft:arrow', 32)],
            deps=['snow_queen'], pos=(30, -3), optional=True, sub='&7Bows from the Aurora Palace',
            desc=['&fThe Snow Queen\'s palace chests hold magic bows: the &bTriple Bow&f shoots three arrows, the &bSeeker Bow&f homes in, the &bIce Bow&f freezes and the &bEnder Bow&f swaps places with its target.']))
qs.append(Q('magic_beans', '&aMagic Beans', TF + 'magic_beans',
            [T.item(TF + 'magic_beans', 1, 'Get Magic Beans')],
            [R.xp(80), R.item('minecraft:bone_meal', 16)],
            deps=['troll'], pos=(36, -3), sub='&7Plant them on Uberous Soil',
            desc=['&fTrolls and troll cave chests drop &aMagic Beans&f. Plant them on &eUberous Soil&f (found in the caves) and a huge beanstalk grows into the clouds.',
                  '&7Climb it to reach the Giants\' cloud cottages.']))
qs.append(Q('giants', '&cGiants in the Clouds', TF + 'giant_sword',
            [T.kill(TF + 'giant_miner', 1, 'Defeat a Giant Miner')],
            [R.xp(200), R.item('minecraft:diamond', 2)],
            deps=['magic_beans'], pos=(39, -3), sub='&7Cloud cottages above the Highlands',
            desc=['&fOn the clouds live &cGiants&f: Giant Miners swing giant pickaxes and Armored Giants guard the cottages. Everything up there is four times normal size.']))
qs.append(Q('giant_pickaxe', '&eThe Giant\'s Pickaxe', TF + 'giant_pickaxe',
            [T.item(TF + 'giant_pickaxe', 1, 'Get a Giant Pickaxe')],
            [R.xp(150), R.item('minecraft:obsidian', 8)],
            deps=['giants'], pos=(42, -3), optional=True, sub='&7Mines giant blocks and clears 4×4 areas',
            desc=['&fGiant Miners drop their &eGiant Pickaxe&f. It mines the giant cobblestone and obsidian of the clouds, and whole 4×4×4 chunks of normal blocks at once.']))
qs.append(Q('hunter', '&6Twilight Hunter', TF + 'raven_feather',
            [T.adv(TF + 'twilight_hunter', 'Defeat one of every Twilight creature')],
            [R.xp(600), R.item('minecraft:enchanted_golden_apple', 1), R.item('cobblemon:master_ball', 1)],
            deps=['plateau'], pos=(45, -3), optional=True, sub='&7Every creature of the forest, hunted',
            desc=['&fDefeat at least one of &eevery&f Twilight Forest creature: from tiny birds and squirrels to every boss.',
                  '&7Check the vanilla advancement screen (L) under Twilight Forest to see which ones are still missing.']))

# ---- side quests below (y = 3) ----
qs.append(Q('pantry', '&aTwilight Pantry', TF + 'cooked_venison',
            [T.anyof([TF + 'cooked_venison', TF + 'cooked_meef', TF + 'torchberries', TF + 'maze_wafer'], 'Collect Twilight food (venison, meef, torchberries or maze wafers)', 4)],
            [R.xp(40), R.item('minecraft:bread', 8)],
            deps=['portal'], pos=(0, 3), sub='&7Deer, boars and glowing berries',
            desc=['&fDeer drop &eVenison&f and wild boars drop &eMeef&f; cook them. &eTorchberries&f glow on cave walls and &eMaze Wafers&f are found in the Labyrinth.']))
qs.append(Q('magic_map', '&bMagic Map', TF + 'filled_magic_map',
            [T.item(TF + 'filled_magic_map', 1, 'Make a Magic Map')],
            [R.xp(80), R.item('minecraft:paper', 8)],
            deps=['portal'], pos=(-3, 3), sub='&7Shows biomes and landmarks of the forest',
            desc=['&fCraft a &eMagic Map Focus&f from a &eRaven Feather&f, &eGlowstone Dust&f and &eTorchberries&f, surround it with paper for a &bMagic Map&f, then right-click to fill it.',
                  '&7The map shows every biome and marks hollow hills, towers, lairs and other landmarks.']))
qs.append(Q('uncrafting', '&eUncrafting Table', TF + 'uncrafting_table',
            [T.item(TF + 'uncrafting_table', 1, 'Craft an Uncrafting Table')],
            [R.xp(80), R.item('minecraft:experience_bottle', 6)],
            deps=['portal'], pos=(-3, 0), sub='&7Take items apart for their ingredients',
            desc=['&fCraft an &eUncrafting Table&f: a crafting table surrounded by &eMaze Map Foci&f. Put a crafted item in the left slot to get its ingredients back, for a little XP.',
                  '&7It also works as a normal crafting table and can recraft items with different parts.']))
qs.append(Q('ironwood', '&7Ironwood and Steeleaf', TF + 'ironwood_ingot',
            [T.anyof([TF + 'ironwood_ingot', TF + 'steeleaf_ingot'], 'Get 4 Ironwood or Steeleaf Ingots', 4)],
            [R.xp(100), R.item('minecraft:iron_ingot', 6)],
            deps=['hollow_hill'], pos=(3, -6), sub='&7Metal that grows in the forest',
            desc=['&fCraft &eLiveroot&f (from hollow hills) with an iron ingot into &eRaw Ironwood&f, then smelt it. &7Steeleaf&f is found in Labyrinth chests.',
                  '&7Ironwood gear comes with free enchantments; Steeleaf gear is light and strong.']))
qs.append(Q('naga_armor', '&aNaga Scale Armor', TF + 'naga_chestplate',
            [T.anyof([TF + 'naga_chestplate', TF + 'naga_leggings'], 'Craft Naga scale armor')],
            [R.xp(100), R.item('minecraft:leather', 8)],
            deps=['naga'], pos=(3, 3), sub='&7Light armor from Naga Scales',
            desc=['&fUse the &eNaga Scales&f to craft a chestplate or leggings. Naga armor is lighter than iron but protects almost as well as diamond.']))
qs.append(Q('stroganoff', '&6Meef Stroganoff', TF + 'meef_stroganoff',
            [T.adv(TF + 'progress_labyrinth', 'Eat Meef Stroganoff after beating the Lich')],
            [R.xp(100), R.item('minecraft:bowl', 4), R.item('minecraft:cooked_beef', 8)],
            deps=['minoshroom'], pos=(9, 3), sub='&7The Minoshroom\'s own recipe',
            desc=['&fThe &cMinoshroom&f drops &6Meef Stroganoff&f. Eating it is part of the Twilight progression: it lifts the hunger curse of the swamps.']))
qs.append(Q('hydra_chop', '&cHydra Chop', TF + 'hydra_chop',
            [T.item(TF + 'hydra_chop', 1, 'Get a Hydra Chop')],
            [R.xp(60), R.item('cobblemon:sitrus_berry', 4)],
            deps=['hydra'], pos=(12, 3), optional=True, sub='&7The most filling food in the forest',
            desc=['&fThe Hydra drops &cHydra Chops&f: eating one fills your whole hunger bar and gives regeneration.']))
qs.append(Q('phantom_armor', '&7Phantom Armor', TF + 'phantom_chestplate',
            [T.anyof([TF + 'phantom_helmet', TF + 'phantom_chestplate'], 'Get Phantom armor')],
            [R.xp(120), R.item('minecraft:diamond', 2)],
            deps=['knights'], pos=(18, 3), optional=True, sub='&7Armor of the Knight Phantoms',
            desc=['&fThe Knight Phantoms\' chest holds the &7Phantom Helmet&f and &7Chestplate&f, strong armor that comes already enchanted.']))
qs.append(Q('carminite', '&cCarminite', TF + 'carminite',
            [T.item(TF + 'carminite', 1, 'Craft Carminite')],
            [R.xp(100), R.item('minecraft:redstone', 16)],
            deps=['dark_tower'], pos=(24, 3), sub='&7Dark Tower redstone magic',
            desc=['&fCraft &cCarminite&f from &eBorer Essence&f (dropped by towerwood borers in the Dark Tower), redstone and ghast tears.',
                  '&7Carminite builds reactors, builders, reappearing blocks and the Carminite Golem.']))
qs.append(Q('yeti_armor', '&bYeti Armor', TF + 'yeti_helmet',
            [T.anyof([TF + 'yeti_helmet', TF + 'yeti_chestplate', TF + 'yeti_leggings', TF + 'yeti_boots'], 'Craft a piece of Yeti armor')],
            [R.xp(120), R.item('minecraft:snowball', 16)],
            deps=['alpha_yeti'], pos=(27, 3), sub='&7Made from Alpha Yeti Fur',
            desc=['&fThe Alpha Yeti drops &eAlpha Yeti Fur&f. Craft it into &bYeti armor&f: the full set makes you immune to freezing.']))
qs.append(Q('arctic_armor', '&bArctic Armor', TF + 'arctic_chestplate',
            [T.anyof([TF + 'arctic_helmet', TF + 'arctic_chestplate', TF + 'arctic_leggings', TF + 'arctic_boots'], 'Craft a piece of Arctic armor')],
            [R.xp(100), R.item('minecraft:blue_dye', 4)],
            deps=['snow_queen'], pos=(30, 3), optional=True, sub='&7Warm, light and dyeable',
            desc=['&fWinter wolves and yetis drop &eArctic Fur&f. Arctic armor is light leather-style armor that can be dyed any color.']))
qs.append(Q('ice_bomb', '&bIce Bombs', TF + 'ice_bomb',
            [T.item(TF + 'ice_bomb', 1, 'Get an Ice Bomb')],
            [R.xp(60), R.item('minecraft:packed_ice', 8)],
            deps=['snow_queen'], pos=(33, 3), optional=True, sub='&7Freeze water and mobs',
            desc=['&f&bIce Bombs&f are thrown by the Snow Queen\'s ice cores. Throw one to freeze water and snow mobs in place.']))
qs.append(Q('experiment', '&dExperiment 115', TF + 'experiment_115',
            [T.item(TF + 'experiment_115', 1, 'Get Experiment 115')],
            [R.xp(60), R.item('minecraft:cake', 1)],
            deps=['dark_tower'], pos=(24, -3), optional=True, sub='&7A cake from the Dark Tower vending machines',
            desc=['&fThe Dark Tower\'s vending machines sell &dExperiment 115&f, a strange edible cake block. It can be placed and eaten slice by slice.']))
qs.append(Q('dinner', '&6Twilight Dinner', 'minecraft:bowl',
            [T.adv(TF + 'twilight_dinner', 'Eat every Twilight Forest food')],
            [R.xp(200), R.item('minecraft:golden_apple', 2)],
            deps=['pantry'], pos=(0, 6), optional=True, sub='&7Venison, meef, berries, soup and more',
            desc=['&fTry every Twilight food: venison, meef, meef stroganoff, hydra chops, maze wafers, berries, berry medley, moss soup, slime drops, jerky and Experiment 115.']))

chapter = dict(file='30_twilight_forest', title='&b> Twilight Forest&r', group=G_DIM, order=30,
               icon='twilightforest:twilight_portal_miniature_structure', sub='&7Boss by boss through the forest of eternal dusk.',
               quests=qs, autofocus='portal')
