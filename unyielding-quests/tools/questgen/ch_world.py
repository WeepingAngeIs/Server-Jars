from gen import Q, T, R, poke_icon

G_WORLD = 'group:world_exploration'  # resolved by build.py (new group)
WS = 'waystones:'
LC = 'lightmanscurrency:'

# =============================== Travel and Trade ===================================
tq = []
tq.append(Q('waystone', '&bYour First Waystone', WS + 'waystone', [T.item(WS + 'waystone', 1, 'Craft a Waystone')], [R.item(WS + 'warp_dust', 4), R.xp(60)],
            pos=(0, 0), shape='hexagon', size=1.5, sub='&7Fast travel between the places you have been',
            desc=['&fWaystones stand in villages and out in the world; right-click one to activate it. Craft your own (hover the Waystone in EMI and press R for the recipe) and place it at your base.',
                  '&7Using a waystone opens a menu of every waystone you have activated. Travel costs XP unless the server says otherwise.']))
tq.append(Q('warp_stone', '&bWarp Stone', WS + 'warp_stone', [T.item(WS + 'warp_stone', 1, 'Craft a Warp Stone')], [R.item('minecraft:ender_pearl', 4), R.xp(80)],
            deps=['waystone'], pos=(3, 0), sub='&7A reusable teleport item', desc=['&fThe &eWarp Stone&f teleports you to any activated waystone from anywhere, with a cooldown. Craft it with Warp Dust and Ender Pearls.']))
tq.append(Q('warp_scroll', '&bWarp Scrolls', WS + 'warp_scroll', [T.anyof([WS + 'warp_scroll', WS + 'return_scroll', WS + 'bound_scroll'], 'Craft a Warp, Return or Bound Scroll')], [R.item(WS + 'warp_scroll', 2), R.xp(60)],
            deps=['waystone'], pos=(3, 3), sub='&7One-use teleports', desc=['&fA &eWarp Scroll&f teleports you to any waystone once, a &eReturn Scroll&f to the last one you used, a &eBound Scroll&f to one specific waystone you bound it to.']))
tq.append(Q('sharestone', '&dSharestones', WS + 'blue_sharestone', [T.anyof([WS + c + '_sharestone' for c in ['orange', 'magenta', 'light_blue', 'yellow', 'lime', 'pink', 'gray', 'light_gray', 'cyan', 'purple', 'blue', 'brown', 'green', 'red', 'black']], 'Craft a Sharestone of any color')], [R.item('minecraft:emerald', 4), R.xp(100)],
            deps=['warp_stone'], pos=(6, 0), optional=True, sub='&7A network only your color can see', desc=['&fSharestones of the same color link to each other and nobody else\'s. Perfect for a team base network.']))
tq.append(Q('warp_plate', '&dWarp Plate', WS + 'warp_plate', [T.item(WS + 'warp_plate', 1, 'Craft a Warp Plate')], [R.item(WS + 'warp_dust', 8), R.xp(120)],
            deps=['warp_stone'], pos=(6, 3), optional=True, sub='&7Step on it and go', desc=['&fPut an &eAttuned Shard&f from one Warp Plate into another and the two link: stand on the plate to teleport instantly, no menu.']))
tq.append(Q('portstone', '&dPortstone', WS + 'white_portstone', [T.item(WS + 'white_portstone', 1, 'Craft a Portstone')], [R.item('minecraft:ender_pearl', 4), R.xp(100)],
            deps=['warp_stone'], pos=(9, 0), optional=True, sub='&7A public departure point', desc=['&fA &ePortstone&f lets anyone travel from it to any waystone, but cannot be travelled to. Great for spawn or shops.']))
# currency
tq.append(Q('coins', '&6Your First Coins', LC + 'coin_copper', [T.item(LC + 'coin_copper', 1, 'Get a Copper Coin')], [R.item(LC + 'coin_copper', 32), R.xp(50)],
            pos=(0, 6), shape='hexagon', size=1.5, sub='&7Lightman\'s Currency: the server economy',
            desc=['&fCoins come from mob drops, loot chests and selling things to other players. &e10 copper = 1 iron, 10 iron = 1 gold, 10 gold = 1 emerald, 10 emerald = 1 diamond, 10 diamond = 1 netherite&f.',
                  '&7Coins also work in CobbleDollars shops once the bridge is set up by the server.']))
tq.append(Q('mint', '&6Coin Mint', LC + 'coin_copper', [T.adv(LC + 'currency/coin_mint', 'Craft a Coin Mint')], [R.item(LC + 'coin_iron', 10), R.xp(80)],
            deps=['coins'], pos=(3, 6), sub='&7Turn ingots into coins', desc=['&fA &eCoin Mint&f turns copper, iron, gold, emerald, diamond and netherite into coins and back.']))
tq.append(Q('wallet', '&6A Wallet', LC + 'wallet_leather', [T.anyof([LC + 'wallet_leather', LC + 'wallet_copper', LC + 'wallet_iron', LC + 'wallet_gold', LC + 'wallet_emerald', LC + 'wallet_diamond', LC + 'wallet_netherite'], 'Craft any wallet')], [R.item(LC + 'coin_iron', 10), R.xp(60)],
            deps=['coins'], pos=(3, 9), sub='&7Keep your coins in one place', desc=['&fCraft a &eWallet&f and put it in the wallet slot of your inventory. Picked-up coins go straight in; better wallets hold more and can auto-convert.']))
tq.append(Q('atm', '&6The Bank', LC + 'portable_atm', [T.adv(LC + 'currency/atm', 'Use an ATM')], [R.item(LC + 'coin_gold', 5), R.xp(100)],
            deps=['wallet'], pos=(6, 9), sub='&7Deposit, withdraw, exchange', desc=['&fAn &eATM&f lets you deposit coins into your bank account, withdraw them and exchange coin types.']))
tq.append(Q('trader', '&6Open a Shop', LC + 'trading_core', [T.adv(LC + 'currency/trader', 'Set up a trader')], [R.item(LC + 'coin_gold', 10), R.xp(150)],
            deps=['mint'], pos=(6, 6), sub='&7Sell to other players while you are away', desc=['&fCraft a &eTrading Core&f and a &eDisplay Case&f or &eVending Machine&f. Put items in, set prices, and other players can buy them any time.']))
tq.append(Q('terminal', '&6Trading Terminal', LC + 'portable_terminal', [T.adv(LC + 'currency/terminal', 'Use a Trading Terminal')], [R.item(LC + 'coin_gold', 5), R.xp(100)],
            deps=['trader'], pos=(9, 6), optional=True, sub='&7Shop from anywhere', desc=['&fA &eTrading Terminal&f lists every network trader on the server so you can buy without walking there.']))
tq.append(Q('rich', '&6Diamond Coin', LC + 'coin_diamond', [T.item(LC + 'coin_diamond', 1, 'Hold a Diamond Coin (10,000 copper)')], [R.item(LC + 'coin_emerald', 5), R.xp(300)],
            deps=['atm'], pos=(9, 9), shape='hexagon', size=1.25, sub='&7Ten thousand copper in one coin', desc=['&fEarn or exchange your way to a single &bDiamond Coin&f.']))
tq.append(Q('netherite_coin', '&6Netherite Coin', LC + 'coin_netherite', [T.item(LC + 'coin_netherite', 1, 'Hold a Netherite Coin')], [R.item(LC + 'coin_diamond', 2), R.item('cobblemon:master_ball', 1), R.xp(800)],
            deps=['rich'], pos=(12, 9), optional=True, shape='hexagon', size=1.5, sub='&7One hundred thousand copper', desc=['&fA &5Netherite Coin&f is worth 100,000 copper. Only serious traders ever hold one.']))
tq.append(Q('merchant', '&aCobble Merchant', 'cobbledollars:cobble_merchant_spawn_egg', [T.check('Buy something from a Cobble Merchant, then press Check')], [R.item('cobblemon:poke_ball', 8), R.xp(60)],
            deps=['coins'], pos=(0, 9), sub='&7CobbleDollars shops', desc=['&fCobble Merchants sell Poké Balls, medicine and more for &eCobbleDollars&f, which you earn from battles. Find one (they live in villages) and buy something.']))

travel = dict(file='36_travel_and_trade', title='&6> Travel and Trade&r', group=G_WORLD, order=10, icon='waystones:waystone',
              sub='&7Waystones to get around, coins and shops to get rich.', quests=tq, autofocus='waystone')

# =============================== Hobbies ===================================
hq = []
hq.append(Q('camera', '&eSay Cheese', 'exposure:camera', [T.item('exposure:camera', 1, 'Craft a Camera')], [R.item('exposure:color_film', 1), R.xp(60)],
            pos=(0, 0), shape='hexagon', size=1.5, sub='&7Exposure: real photos in Minecraft',
            desc=['&fCraft a &eCamera&f and load it with &eFilm&f. Right-click to look through it, click to take a photo. Develop the film in a cauldron with water, then in a crafting grid.',
                  '&7Photos can be framed, put in albums or given to friends.']))
hq.append(Q('photo', '&eA Moment in Time', 'exposure:photograph', [T.adv('exposure:adventure/exposure', 'Take a photo')], [R.item('exposure:color_film', 2), R.xp(80)],
            deps=['camera'], pos=(3, 0), sub='&7Your first picture', desc=['&fTake a photo with the camera. Point it at your Pokémon, your base, a sunset, anything.']))
hq.append(Q('album', '&ePhoto Album', 'exposure:album', [T.item('exposure:album', 1, 'Craft a Photo Album')], [R.item('minecraft:leather', 4), R.xp(60)],
            deps=['photo'], pos=(6, 0), optional=True, sub='&7Keep your memories', desc=['&fCraft a &ePhoto Album&f to store developed photographs and add captions.']))
hq.append(Q('wildlife', '&eWildlife Archivist', 'exposure:aged_photograph', [T.adv('exposure:adventure/wildlife_archivist', 'Photograph animals')], [R.item('exposure:high_sensitivity_color_film', 1), R.xp(120)],
            deps=['photo'], pos=(6, 3), optional=True, sub='&7Nature photography', desc=['&fPhotograph animals in the wild.']))
hq.append(Q('instrument', '&dFirst Notes', 'immersive_melodies:flute', [T.anyof(['immersive_melodies:' + i for i in ['flute', 'lute', 'trumpet', 'piano', 'vielle', 'bagpipe', 'didgeridoo', 'triangle', 'tiny_drum', 'handpan', 'ender_bass']], 'Craft any instrument')], [R.item('minecraft:note_block', 2), R.xp(60)],
            pos=(0, 6), shape='hexagon', size=1.5, sub='&7Immersive Melodies: play real songs',
            desc=['&fCraft an instrument (flute, lute, trumpet, piano and more). Hold it and right-click to open the song list; pick a melody and your character plays it for everyone nearby.',
                  '&7You can import your own MIDI songs through the song menu.']))
hq.append(Q('band', '&dThe Band', 'immersive_melodies:lute', [T.anyof(['immersive_melodies:' + i for i in ['lute', 'trumpet', 'piano', 'vielle', 'bagpipe', 'didgeridoo', 'handpan', 'ender_bass']], 'Craft 3 different instruments', 3)], [R.item('minecraft:jukebox', 1), R.xp(120)],
            deps=['instrument'], pos=(3, 6), optional=True, sub='&7Three instruments', desc=['&fCollect three different instruments so your friends can play along.']))
hq.append(Q('ribbits', '&aRibbit Village', 'ribbits:maraca', [T.struct('ribbits:ribbit_village', 'Find a Ribbit village')], [R.item('minecraft:lily_pad', 8), R.xp(100)],
            pos=(0, 9), shape='hexagon', size=1.25, sub='&7Frog folk in the swamps', desc=['&fRibbits are small frog people living in stilt villages in swamps. They fish, garden, trade and play the maraca.']))
hq.append(Q('maraca', '&aMaraca', 'ribbits:maraca', [T.item('ribbits:maraca', 1, 'Get a Maraca')], [R.item('minecraft:music_disc_cat', 1), R.xp(80)],
            deps=['ribbits'], pos=(3, 9), optional=True, sub='&7Shake it', desc=['&fTrade with a Ribbit for a &eMaraca&f and shake it next to them: they start dancing.']))
hq.append(Q('pokenav', '&bPokéNav', 'cobblenav:pokenav_item', [T.item('cobblenav:pokenav_item', 1, 'Craft a PokéNav')], [R.item('cobblemon:great_ball', 6), R.xp(80)],
            pos=(0, 12), shape='hexagon', size=1.25, sub='&7Where do Pokémon spawn?', desc=['&fThe &ePokéNav&f shows which Pokémon can spawn around you, when, and what you need for them. A must for hunting specific species.']))
hq.append(Q('pokefinder', '&bPokéFinder', 'cobblenav:pokefinder_item', [T.item('cobblenav:pokefinder_item', 1, 'Craft a PokéFinder')], [R.item('cobblemon:ultra_ball', 4), R.xp(80)],
            deps=['pokenav'], pos=(3, 12), optional=True, sub='&7Track a Pokémon', desc=['&fThe &ePokéFinder&f points you towards a chosen nearby Pokémon.']))
hq.append(Q('fishingnav', '&bFishingNav', 'cobblenav:fishingnav_item', [T.item('cobblenav:fishingnav_item', 1, 'Craft a FishingNav')], [R.item('cobblemon:lure_ball', 4), R.xp(80)],
            deps=['pokenav'], pos=(6, 12), optional=True, sub='&7What bites here?', desc=['&fThe &eFishingNav&f lists the Pokémon you can fish up at your spot with each kind of Poké Rod.']))
hq.append(Q('pokestaff', '&cPoké Staff', 'fightorflight:pokestaff', [T.item('fightorflight:pokestaff', 1, 'Craft a Poké Staff')], [R.item('cobblemon:rare_candy', 2), R.xp(100)],
            pos=(0, 15), shape='hexagon', size=1.25, sub='&7Fight or Flight: command your Pokémon outside battle',
            desc=['&fWild Pokémon can attack you in the open and your own Pokémon fight back. With a &ePoké Staff&f you can order your sent-out Pokémon: attack a target, use a move, hold position.']))

hobbies = dict(file='37_hobbies', title='&d> Hobbies and Gadgets&r', group=G_WORLD, order=20, icon='exposure:camera',
               sub='&7Cameras, music, frog villages and trainer gadgets.', quests=hq, autofocus='camera')

# =============================== Dungeons and Ruins ===================================
dq = []
def S(key, title, icon, sid, taskt, pos, deps=(), rew=None, sub='', desc=(), optional=False, shape=None, size=None):
    return Q(key, title, icon, [T.struct(sid, taskt)], rew or [R.xp(80)], deps=list(deps), pos=pos, sub=sub, desc=list(desc), optional=optional, shape=shape, size=size)

dq.append(Q('intro', '&6Ruins and Dungeons', 'minecraft:filled_map', [T.check('Read this, then press Check')], [R.item('minecraft:torch', 32), R.item('minecraft:bread', 8), R.xp(30)],
           pos=(0, 0), shape='hexagon', size=1.5, sub='&7Hundreds of structures to find and loot',
           desc=['&fThe pack adds dungeons, castles, ships and towers from YUNG\'s mods, When Dungeons Arise, Dungeon Crawl, Towns and Towers, Explorify and more.',
                 '&7Most loot chests are &eLootr&f chests: every player gets their own loot, so nothing is ever "already taken".']))
dq.append(S('dungeon', '&7A Better Dungeon', 'minecraft:spawner', '#betterdungeons:better_dungeons', 'Find a YUNG\'s dungeon (zombie, skeleton, spider or small)', (3, -3), ['intro'],
            [R.item('minecraft:iron_ingot', 8), R.xp(100)], '&7Spawners, loot and traps', ['&fYUNG\'s dungeons replace the little cobblestone rooms: zombie crypts, skeleton halls, spider nests and small dungeons with several spawners and chests.']))
dq.append(S('mineshaft', '&7Better Mineshaft', 'minecraft:minecart', '#bettermineshafts:better_mineshafts', 'Explore a Better Mineshaft', (3, 0), ['intro'],
            [R.item('minecraft:rail', 32), R.xp(80)], '&7Huge branching tunnels', ['&fBetter Mineshafts are enormous: main shafts, side tunnels, ore deposits, abandoned workstations and minecarts full of loot.']))
dq.append(S('stronghold', '&7Better Stronghold', 'minecraft:end_portal_frame', 'betterstrongholds:stronghold', 'Enter a Better Stronghold', (3, 3), ['intro'],
            [R.item('minecraft:ender_eye', 4), R.xp(150)], '&7Libraries, armories and the End portal', ['&fFollow Eyes of Ender to a &eBetter Stronghold&f: libraries, armories, prisons, a grand staircase and the End Portal room.']))
dq.append(S('jungle', '&aBetter Jungle Temple', 'minecraft:mossy_cobblestone', 'betterjungletemples:jungle_temple', 'Find a Better Jungle Temple', (3, 6), ['intro'],
            [R.item('minecraft:emerald', 4), R.xp(100)], '&7Traps and treasure in the jungle', ['&fA &eBetter Jungle Temple&f hides its treasure behind tripwire traps and puzzle rooms. Watch the floor.']))
dq.append(S('witch', '&5Witch Hut', 'minecraft:cauldron', 'betterwitchhuts:witch_hut', 'Find a Better Witch Hut', (3, 9), ['intro'],
            [R.item('minecraft:glass_bottle', 8), R.xp(80)], '&7Swamp witches at home', ['&fBetter Witch Huts are bigger swamp houses with brewing stands, cats and, of course, a witch.']))
dq.append(S('crawl', '&cDungeon Crawl', 'minecraft:chain', 'dungeoncrawl:dungeon', 'Descend into a Dungeon Crawl dungeon', (6, -3), ['dungeon'],
            [R.item('minecraft:golden_apple', 2), R.xp(200)], '&7Floor after floor underground', ['&fDungeon Crawl dungeons start as a small entrance and go down many floors, each harder than the last, ending in a treasure vault.'], shape='hexagon', size=1.25))
dq.append(S('fort', '&cIllager Fort', 'minecraft:crossbow', 'dungeons_arise:illager_fort', 'Storm an Illager Fort', (9, -3), ['intro'],
            [R.item('minecraft:crossbow', 1), R.item('minecraft:arrow', 32), R.xp(150)], '&7When Dungeons Arise', ['&fThe &eIllager Fort&f is a walled stronghold packed with pillagers, vindicators and evokers. Clear it for its armory and loot.']))
dq.append(S('bandit', '&cBandit Towers', 'minecraft:iron_sword', 'dungeons_arise:bandit_towers', 'Find the Bandit Towers', (12, -3), ['fort'],
            [R.item('minecraft:emerald', 6), R.xp(150)], '&7Twin towers on a bridge', ['&fTwo towers joined by a bridge, full of bandits and chests. The top floors have the best loot.'], optional=True))
dq.append(S('kayra', '&dKeep Kayra', 'minecraft:stone_bricks', 'dungeons_arise:keep_kayra', 'Explore Keep Kayra', (9, 0), ['intro'],
            [R.item('minecraft:diamond', 3), R.xp(250)], '&7A castle in the forest', ['&fKeep Kayra is a big castle with towers, dungeons, a library and a throne room. Mobs hide in every corner.'], shape='hexagon', size=1.25))
dq.append(S('monastery', '&dMonastery', 'minecraft:bookshelf', 'dungeons_arise:monastery', 'Visit a Monastery', (12, 0), ['intro'],
            [R.item('minecraft:book', 8), R.xp(120)], '&7A cliffside temple', ['&fMonasteries sit on cliffs and mountains with long stairs and a shrine at the top.'], optional=True))
dq.append(S('asylum', '&8Plague Asylum', 'minecraft:iron_bars', 'dungeons_arise:plague_asylum', 'Enter the Plague Asylum', (15, 0), ['intro'],
            [R.item('minecraft:golden_apple', 2), R.xp(200)], '&7Nobody checks out', ['&fThe &ePlague Asylum&f is a dark hospital full of zombies and husks. Bring light and healing.'], optional=True))
dq.append(S('shiraz', '&ePalace of Shiraz', 'minecraft:gold_block', 'dungeons_arise:shiraz_palace', 'Find the Shiraz Palace', (9, 3), ['intro'],
            [R.item('minecraft:gold_ingot', 8), R.xp(200)], '&7Desert gold', ['&fA huge desert palace with gardens, domes and a treasury guarded by illagers.'], shape='hexagon', size=1.25))
dq.append(S('thornborn', '&2Thornborn Towers', 'minecraft:vine', 'dungeons_arise:thornborn_towers', 'Climb the Thornborn Towers', (12, 3), ['intro'],
            [R.item('minecraft:emerald', 6), R.xp(200)], '&7Overgrown towers in the jungle', ['&fTall mossy towers wrapped in vines, with loot on every floor.'], optional=True))
dq.append(S('pirates', '&9Undead Pirate Ship', 'minecraft:oak_boat', 'dungeons_arise:undead_pirate_ship', 'Board an Undead Pirate Ship', (15, 3), ['intro'],
            [R.item('minecraft:gold_ingot', 6), R.item('minecraft:fishing_rod', 1), R.xp(150)], '&7Ghost ship on the ocean', ['&fA drowned crew sails this wreck. The captain\'s cabin holds the treasure.'], optional=True))
dq.append(S('heavenly', '&bHeavenly Challenger', 'minecraft:phantom_membrane', 'dungeons_arise:heavenly_challenger', 'Reach a Heavenly Challenger airship', (18, 3), ['intro'],
            [R.item('minecraft:feather', 16), R.xp(200)], '&7An airship in the sky', ['&fThe &eHeavenly Challenger&f floats high in the sky. Fly, build up or find its anchor chain to board it.'], optional=True))
dq.append(S('mushroom', '&dMushroom Village', 'minecraft:red_mushroom_block', 'dungeons_arise:mushroom_village', 'Visit a Mushroom Village', (18, 0), ['intro'],
            [R.item('minecraft:mushroom_stew', 4), R.xp(100)], '&7Houses made of mushrooms', ['&fIn the mushroom fields the villagers live inside giant mushrooms.'], optional=True))
dq.append(S('town', '&6Towns and Towers', 'minecraft:bell', '#towns_and_towers:town', 'Visit a Towns and Towers village', (9, 6), ['intro'],
            [R.item('minecraft:emerald', 4), R.xp(80)], '&7Villages for every biome', ['&fTowns and Towers rebuilds villages for every biome: jungle, swamp, beach, mushroom, badlands and more. Each has its own look and its own trades.']))
dq.append(S('sky', '&bSky Village', 'minecraft:elytra', 'skyvillages:skyvillage', 'Find a Sky Village', (12, 6), ['intro'],
            [R.item('minecraft:feather', 8), R.item('minecraft:ender_pearl', 2), R.xp(150)], '&7A village on floating islands', ['&fSky Villages float far above the ground. Look up, or follow the shadows on the land below.'], shape='hexagon', size=1.25))
dq.append(S('tidal', '&9Tidal Town', 'minecraft:kelp', 'joshie:village_ocean', 'Find a Tidal Town', (15, 6), ['intro'],
            [R.item('minecraft:cod', 8), R.xp(100)], '&7A village on the water', ['&fTidal Towns are villages on stilts out at sea, with docks and fishing boats.'], optional=True))
dq.append(S('underground', '&7Underground Village', 'minecraft:lantern', 'underground_villages:underground_village', 'Find an Underground Village', (18, 6), ['intro'],
            [R.item('minecraft:lantern', 4), R.xp(120)], '&7Villagers who went deep', ['&fSome villages are built inside caves. Listen for villager sounds while mining.'], optional=True))
dq.append(S('witch_village', '&5Witch Village', 'minecraft:brewing_stand', 'villagesandpillages:village_witch', 'Find a Witch Village', (21, 6), ['intro'],
            [R.item('minecraft:redstone', 8), R.xp(120)], '&7A whole town of witches', ['&fWitches have their own villages in dark forests and swamps. Careful with the potions.'], optional=True))
dq.append(S('watchtower', '&6Watchtower', 'minecraft:spyglass', 'explorify:watchtower/plains', 'Climb a plains Watchtower', (9, 9), ['intro'],
            [R.item('minecraft:spyglass', 1), R.xp(80)], '&7Explorify', ['&fExplorify adds watchtowers, taverns, farmsteads, ruins and shrines to the overworld. Climb a &eWatchtower&f on the plains.']))
dq.append(S('tavern', '&6The Tavern', 'minecraft:barrel', 'explorify:tavern', 'Find a Tavern', (12, 9), ['watchtower'],
            [R.item('minecraft:honey_bottle', 4), R.xp(100)], '&7A warm place on the road', ['&fTaverns have beds, food, a bar and travelers\' stories.'], optional=True))
dq.append(S('mausoleum', '&8Mausoleum', 'minecraft:skeleton_skull', 'explorify:mausoleum', 'Enter a Mausoleum', (15, 9), ['watchtower'],
            [R.item('minecraft:bone', 8), R.xp(120)], '&7A tomb worth robbing', ['&fThe &eMausoleum&f is a stone tomb guarded by skeletons, with a hidden vault.'], optional=True))
dq.append(S('pyramid', '&eBadlands Pyramid', 'minecraft:terracotta', 'explorify:badlands_pyramid', 'Find a Badlands Pyramid', (18, 9), ['watchtower'],
            [R.item('minecraft:gold_ingot', 6), R.xp(150)], '&7Terracotta temple', ['&fA step pyramid in the badlands with chambers full of traps and gold.'], optional=True))
dq.append(S('prairie', '&eAbandoned Prairie House', 'minecraft:hay_block', 'biomeswevegone:abandoned_prairie_house', 'Find an abandoned Prairie House', (21, 9), ['intro'],
            [R.item('minecraft:wheat', 16), R.xp(80)], '&7Oh The Biomes We\'ve Gone', ['&fAn abandoned farmhouse on the prairie. Someone left in a hurry; their chests are still there.'], optional=True))
dq.append(S('manor', '&6Aspen Manor', 'minecraft:oak_door', '#biomeswevegone:aspen_manors', 'Find an Aspen Manor', (24, 9), ['prairie'],
            [R.item('minecraft:diamond', 2), R.xp(150)], '&7A mansion in the aspen woods', ['&fAspen Manors are large wooden mansions in the aspen forests, with many rooms to search.'], optional=True))
dq.append(S('fossil', '&7Rugged Fossil', 'minecraft:bone_block', 'biomeswevegone:rugged_fossil', 'Find a Rugged Fossil', (24, 6), ['intro'],
            [R.item('minecraft:bone_block', 4), R.xp(80)], '&7Bones of something huge', ['&fA giant fossil half-buried in the badlands.'], optional=True))
dq.append(S('medieval', '&6Medieval Fort', 'minecraft:stone_brick_wall', 'medieval_buildings:fort', 'Find a Medieval Fort', (21, 3), ['intro'],
            [R.item('minecraft:iron_ingot', 8), R.xp(120)], '&7Medieval Buildings', ['&fStone forts, towers and ships from Medieval Buildings dot the land and sea. Find a &eFort&f.'], optional=True))
dq.append(S('apotheosis', '&dBoss Tower', 'minecraft:amethyst_shard', 'apotheosis:tower_main', 'Find an Apotheosis boss tower', (21, 0), ['intro'],
            [R.item('minecraft:golden_apple', 2), R.xp(200)], '&7Apotheosis', ['&fApotheosis towers hold affix bosses on each floor and rare gems and affix gear in their chests.'], optional=True, shape='hexagon', size=1.25))

dungeons = dict(file='38_dungeons_and_ruins', title='&6> Dungeons and Ruins&r', group=G_WORLD, order=30, icon='minecraft:filled_map',
                sub='&7Forts, towers, ships, villages and dungeons to find.', quests=dq, autofocus='intro')

# =============================== Biomes and Nature ===================================
bq = []
def B(key, title, icon, bid, pos, deps=('intro',), rew=None, sub='', desc=(), optional=False):
    name = title.split(' ', 0)[0]
    return Q(key, title, icon, [T.biome(bid, 'Visit the ' + sub.replace('&7', '').strip() if False else 'Stand in this biome')], rew or [R.xp(60)], deps=list(deps), pos=pos, sub=sub, desc=list(desc), optional=optional)

bq.append(Q('intro', '&aWonders of the World', 'minecraft:oak_sapling', [T.check('Read this, then press Check')], [R.item('minecraft:oak_boat', 1), R.item('minecraft:bread', 8), R.xp(30)],
           pos=(0, 0), shape='hexagon', size=1.5, sub='&7Biomes O\' Plenty, Oh The Biomes We\'ve Gone and Nature\'s Spirit',
           desc=['&fThree biome mods fill the world with new forests, deserts, meadows and mountains. Each biome below is worth a visit and has its own Pokémon spawns.',
                 '&7Use the &eNature\'s Compass&f to find a biome, or just explore. Every quest completes the moment you stand in the biome.']))
bop = [('mystic', '&dMystic Grove', 'minecraft:glow_berries', 'biomesoplenty:mystic_grove', (3, -3), '&7Pink trees, glowing flowers', 'Fairy Pokémon love it here.'),
       ('lavender', '&dLavender Field', 'minecraft:purple_dye' if False else 'minecraft:lilac', 'biomesoplenty:lavender_field', (6, -3), '&7Purple as far as you can see', 'Bees and Grass-types everywhere.'),
       ('redwood', '&cRedwood Forest', 'minecraft:spruce_log', 'biomesoplenty:redwood_forest', (9, -3), '&7The tallest trees', 'Giant redwoods; great for tree houses.'),
       ('origin', '&aOrigin Valley', 'minecraft:grass_block', 'biomesoplenty:origin_valley', (12, -3), '&7Minecraft like it was in 2009', 'Old-school colors and blocks.'),
       ('volcano', '&cVolcano', 'minecraft:magma_block', 'biomesoplenty:volcano', (15, -3), '&7Lava, ash and Fire-types', 'Watch your step.'),
       ('hotsprings', '&bHot Springs', 'minecraft:water_bucket', 'biomesoplenty:hot_springs', (18, -3), '&7Warm pools in the mountains', 'A good place to relax and heal.'),
       ('tropics', '&eTropics', 'minecraft:coconut' if False else 'minecraft:sand', 'biomesoplenty:tropics', (21, -3), '&7Palm trees and white sand', 'Water and Grass Pokémon.'),
       ('ominous', '&5Ominous Woods', 'minecraft:dark_oak_log', 'biomesoplenty:ominous_woods', (24, -3), '&7Dark and dangerous', 'Ghost and Dark-types at night.'),
       ('wasteland', '&7Wasteland', 'minecraft:dead_bush', 'biomesoplenty:wasteland', (27, -3), '&7Nothing grows here', 'Rock and Ground Pokémon.')]
for key, title, icon, bid, pos, sub, extra in bop:
    bq.append(B(key, title, icon, bid, pos, sub=sub, desc=[f'&fFind and stand in the &e{title[2:]}&f biome (Biomes O\' Plenty). {extra}']))
byg = [('sakura', '&dSakura Grove', 'minecraft:cherry_sapling', 'biomeswevegone:sakura_grove', (3, 0), '&7Cherry blossoms everywhere', 'Beautiful at sunset.'),
       ('skyris', '&bSkyris Vale', 'minecraft:blue_orchid', 'biomeswevegone:skyris_vale', (6, 0), '&7Blue trees and a village', 'Home of the Skyris village.'),
       ('howling', '&bHowling Peaks', 'minecraft:snow_block', 'biomeswevegone:howling_peaks', (9, 0), '&7Wind-blasted mountains', 'Ice-types and great views.'),
       ('redrock', '&cRed Rock Valley', 'minecraft:red_sandstone', 'biomeswevegone:red_rock_valley', (12, 0), '&7Canyons and arches', 'Rock and Ground-types.'),
       ('weeping', '&5Weeping Witch Forest', 'minecraft:cobweb', 'biomeswevegone:weeping_witch_forest', (15, 0), '&7Twisted trees, witches', 'Spooky at any hour.'),
       ('forgotten', '&2Forgotten Forest', 'minecraft:mossy_cobblestone', 'biomeswevegone:forgotten_forest', (18, 0), '&7Ancient and overgrown', 'Look for the Forgotten village.'),
       ('rainbow', '&eRainbow Beach', 'minecraft:sand', 'biomeswevegone:rainbow_beach', (21, 0), '&7Sand in every color', 'Water-types along the shore.'),
       ('pumpkin', '&6Pumpkin Valley', 'minecraft:carved_pumpkin', 'biomeswevegone:pumpkin_valley', (24, 0), '&7Halloween all year', 'Ghost and Grass-types.'),
       ('shattered', '&bShattered Glacier', 'minecraft:blue_ice', 'biomeswevegone:shattered_glacier', (27, 0), '&7Ice spikes and crevasses', 'Bring warm boots.')]
for key, title, icon, bid, pos, sub, extra in byg:
    bq.append(B(key, title, icon, bid, pos, sub=sub, desc=[f'&fFind and stand in the &e{title[2:]}&f biome (Oh The Biomes We\'ve Gone). {extra}']))
ns = [('wisteria', '&dWisteria Forest', 'minecraft:lilac', 'natures_spirit:wisteria_forest', (3, 3), '&7Hanging purple blossoms', 'Home of the Wisteria village.'),
      ('marigold', '&6Marigold Meadows', 'minecraft:dandelion', 'natures_spirit:marigold_meadows', (6, 3), '&7Golden flower fields', 'Bug and Grass-types.'),
      ('sugi', '&aSugi Forest', 'minecraft:spruce_sapling', 'natures_spirit:sugi_forest', (9, 3), '&7Tall Japanese cedars', 'Calm and quiet.'),
      ('whitecliffs', '&fWhite Cliffs', 'minecraft:calcite', 'natures_spirit:white_cliffs', (12, 3), '&7Chalk cliffs over the sea', 'Flying-types soar here.'),
      ('bamboo', '&2Bamboo Wetlands', 'minecraft:bamboo', 'natures_spirit:bamboo_wetlands', (15, 3), '&7Bamboo and still water', 'Pandas and Water-types.'),
      ('golden', '&eGolden Wilds', 'minecraft:wheat', 'natures_spirit:golden_wilds', (18, 3), '&7Golden grass savanna', 'Normal and Fire-types.'),
      ('lavender2', '&dLavender Fields', 'minecraft:lilac', 'natures_spirit:lavender_fields', (21, 3), '&7Nature\'s Spirit\'s purple fields', 'Bees love it.'),
      ('aspen', '&eAspen Forest', 'minecraft:birch_log', 'natures_spirit:aspen_forest', (24, 3), '&7White trunks, yellow leaves', 'Autumn all year.'),
      ('redpeaks', '&cRed Peaks', 'minecraft:red_sand', 'natures_spirit:red_peaks', (27, 3), '&7Red mountains', 'Rock-types and a long climb.')]
for key, title, icon, bid, pos, sub, extra in ns:
    bq.append(B(key, title, icon, bid, pos, sub=sub, desc=[f'&fFind and stand in the &e{title[2:]}&f biome (Nature\'s Spirit). {extra}']))
bq.append(Q('globetrotter', '&6Globetrotter', 'minecraft:compass', [T.check('Visited every biome in this chapter? Press Check')], [R.item('cobblemon:master_ball', 1), R.item('minecraft:enchanted_golden_apple', 1), R.xp(800)],
           deps=[k for k, *_ in bop] + [k for k, *_ in byg] + [k for k, *_ in ns], pos=(30, 0), shape='gear', size=1.5, hide_lines=True,
           sub='&7All 27 biomes visited', desc=['&fVisit every biome in this chapter. This quest unlocks when all of them are done.']))

biomes = dict(file='39_biomes_and_nature', title='&a> Biomes and Nature&r', group=G_WORLD, order=40, icon='minecraft:oak_sapling',
              sub='&7Twenty-seven of the most beautiful modded biomes.', quests=bq, autofocus='intro')
