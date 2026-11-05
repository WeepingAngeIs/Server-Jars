#!/usr/bin/env python3
"""Takes the team's updated FTB quest pack, applies the requested edits and writes the mod's defaultquests."""
import sys, os, glob, json, re, shutil, copy, hashlib
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import snbt
from snbt import Num

SRC = '/tmp/claude-0/-home-claude/be528b7f-1fff-5c6b-9047-83311fc9f524/scratchpad/ftbq3/ftbquests/quests'
DST = '/home/claude/uq/src/main/resources/defaultquests'
COB_LANG = json.load(open('/tmp/claude-0/-home-claude/be528b7f-1fff-5c6b-9047-83311fc9f524/scratchpad/cob/assets/cobblemon/lang/en_us.json', encoding='utf-8'))
REG = json.load(open('/home/claude/modjars/registry.json'))
ITEM_NAMES = {}
for jar, info in REG.items():
    ITEM_NAMES.update(info['items'])
for k, v in COB_LANG.items():
    if k.startswith('item.cobblemon.'):
        ITEM_NAMES['cobblemon:' + k[len('item.cobblemon.'):]] = v

lang = snbt.load(SRC + '/lang/en_us.snbt')
chapters = {os.path.basename(f)[:-5]: snbt.load(f) for f in glob.glob(SRC + '/chapters/*.snbt')}
groups = snbt.load(SRC + '/chapter_groups.snbt')

ALL_IDS = set()
for ch in chapters.values():
    ALL_IDS.add(ch['id'])
    for q in ch['quests']:
        ALL_IDS.add(q['id'])
        for t in q.get('tasks', []):
            ALL_IDS.add(t['id'])
        for r in q.get('rewards', []):
            ALL_IDS.add(r['id'])
    for i in ch.get('images', []):
        ALL_IDS.add(i['id'])


def hid(seed):
    n = 0
    while True:
        h = hashlib.sha1(f'migrate:{seed}#{n}'.encode()).hexdigest()[:16].upper()
        if h not in ALL_IDS and h[0] != '0':
            ALL_IDS.add(h)
            return h
        n += 1


def D(x):
    return Num(f'{float(x):.1f}d' if float(x) == int(float(x)) else f'{float(x)}d')


def item_name(iid):
    return ITEM_NAMES.get(iid, iid.split(':')[-1].replace('_', ' ').title())


# ---- reference rewards ------------------------------------------------------------
ref = [q for q in chapters['10_start_here']['quests'] if q['id'] == '1625FF5CBF3FBEC0'][0]
SNACK = copy.deepcopy([r for r in ref['rewards'] if r['type'] == 'item'][0]['item'])  # seasoned Poke Snack (count 1)
SNACK['count'] = Num('1')


def cd_reward(amount, seed, icon='cobblemon:relic_coin'):
    return {'command': f'/cobbledollars give {{p}} {amount}', 'console': True, 'icon': {'id': icon}, 'id': hid(seed), 'title': f'{amount} CobbleDollars', 'type': 'command'}


def item_reward(iid, count, seed, components=None):
    it = {'count': Num('1'), 'id': iid}
    if components:
        it = {'components': components, 'count': Num('1'), 'id': iid}
    r = {'id': hid(seed), 'item': it, 'type': 'item'}
    if count > 1:
        r = {'count': Num(str(count)), 'id': hid(seed), 'item': it, 'type': 'item'}
    return r


def xp_reward(xp, seed):
    return {'id': hid(seed), 'type': 'xp', 'xp': Num(str(xp))}


def set_xp(q, xp, seed):
    for r in q.get('rewards', []):
        if r.get('type') == 'xp':
            r['xp'] = Num(str(xp))
            return
    q.setdefault('rewards', []).append(xp_reward(xp, seed))


def has_cd(q):
    return any(r.get('type') == 'command' and 'cobbledollars' in r.get('command', '') for r in q.get('rewards', []))


def item_task(iid, seed, count=1):
    return {'count': Num(f'{count}L'), 'id': hid(seed), 'item': iid, 'type': 'item'}


def dex_task(percent, seed):
    return {'action': 'have_registered', 'amount': Num(f'{percent}L'), 'dex_progress': 'caught_percent', 'id': hid(seed), 'type': 'unyieldingquests:cobblemon'}


def label_image(seed, title, x, y, w=6.8, h=0.4):
    iid = hid(seed)
    lang[f'image.{iid}.title'] = title
    return {'height': D(h), 'id': iid, 'image': '', 'position_locked': Num('1b'), 'rotation': D(0), 'text_on_image': Num('1b'), 'text_shadow': Num('1b'), 'width': D(w), 'x': D(x), 'y': D(y)}


def new_quest(seed, title, icon, tasks, rewards, x, y, deps=(), sub='', desc=(), shape=None, size=None):
    q = {'id': hid(seed)}
    if deps:
        q['dependencies'] = list(deps)
    q['icon'] = {'id': icon} if isinstance(icon, str) else icon
    q['rewards'] = rewards
    if shape:
        q['shape'] = shape
    if size:
        q['size'] = D(size)
    q['tasks'] = tasks
    q['x'] = D(x)
    q['y'] = D(y)
    lang[f'quest.{q["id"]}.title'] = title
    if sub:
        lang[f'quest.{q["id"]}.quest_subtitle'] = sub
    if desc:
        lang[f'quest.{q["id"]}.quest_desc'] = list(desc)
    return q


# ---- 0. welcome quest rename ------------------------------------------------------------
for k, v in list(lang.items()):
    if isinstance(v, str) and 'Cobblemon Adventures' in v:
        lang[k] = v.replace('Cobblemon Adventures', 'Cobblemon Unyielding')

# ---- 1. 25 CobbleDollars for every quest on the Welcome and Pack Tools page -----------------
for q in chapters['10_start_here']['quests']:
    if q['id'] == '1625FF5CBF3FBEC0':
        continue  # the reference quest keeps its own rewards
    if not has_cd(q):
        q.setdefault('rewards', []).append(cd_reward(25, 'welcome25:' + q['id']))

# ---- 2. seasoned Poke Snacks everywhere -----------------------------------------------------
def seasoned(r):
    it = r.get('item')
    iid = it.get('id') if isinstance(it, dict) else it
    if iid == 'cobblemon:poke_snack' and not (isinstance(it, dict) and 'components' in it):
        r['item'] = copy.deepcopy(SNACK)
        return True
    return False


# ---- 3. Legendary Expeditions: 1000 xp + 100 CobbleDollars ----------------------------------
for n in ['15_legends_kanto_hoenn', '16_legends_sinnoh_kalos', '17_legends_alola_paldea']:
    for q in chapters[n]['quests']:
        if any(t.get('type') == 'cobblemon_tasks:cobblemon_task' for t in q.get('tasks', [])):
            set_xp(q, 1000, 'legxp:' + q['id'])
        if not has_cd(q):
            q.setdefault('rewards', []).append(cd_reward(100, 'leg100:' + q['id']))

# ---- 4. Type Mastery Trials: 1000 xp + 100 CobbleDollars -----------------------------------
for q in chapters['13_regional_mastery_type_gauntlet']['quests']:
    set_xp(q, 1000, 'typexp:' + q['id'])
    if not has_cd(q):
        q.setdefault('rewards', []).append(cd_reward(100, 'type100:' + q['id']))

# ---- 5. remove whole pages -------------------------------------------------------------------
REMOVED_QUESTS = set()
for n in ['type_field_guide', 'recovery_berries_and_battle_gear', 'evolution_essentials']:
    for q in chapters[n]['quests']:
        REMOVED_QUESTS.add(q['id'])
    del chapters[n]

# ---- 6. Pokedex Adventures: completion goals + moved quests --------------------------------------
pa = chapters['14_sunny_time']
for q in pa['quests']:
    REMOVED_QUESTS.add(q['id'])
pa['quests'] = []
pa['images'] = []
pa['default_hide_dependency_lines'] = False
pa['icon'] = {'id': 'cobblemon:pokedex_red'}
lang[f'chapter.{pa["id"]}.title'] = '&c> Pokédex Adventures&r'
lang[f'chapter.{pa["id"]}.chapter_subtitle'] = '&7Fill the Pokédex, track Pokémon and bring fossils back to life.'
goal_rewards = {
    10: ([('cobblemon:ultra_ball', 10), ('cobblemon:rare_candy', 3)], 25, 100),
    20: ([('cobblemon:rare_candy', 5), ('cobblemon:exp_candy_l', 3)], 50, 200),
    30: ([('cobblemon:ability_capsule', 1), ('cobblemon:rare_candy', 5), ('cobblemon:dusk_ball', 10)], 75, 300),
    40: ([('cobblemon:rare_candy', 10), ('cobblemon:exp_candy_xl', 3)], 100, 400),
    50: ([('cobblemon:master_ball', 1), ('cobblemon:ability_patch', 1)], 150, 500),
    60: ([('cobblemon:rare_candy', 10), ('cobblemon:exp_candy_xl', 5), ('cobblemon:pp_max', 2)], 200, 600),
    70: ([('cobblemon:master_ball', 1), ('cobblemon:rare_candy', 15), ('minecraft:enchanted_golden_apple', 1)], 250, 700),
    80: ([('cobblemon:master_ball', 2), ('cobblemon:ability_patch', 1), ('cobblemon:rare_candy', 15)], 300, 800),
    90: ([('cobblemon:master_ball', 2), ('cobblemon:rare_candy', 20), ('minecraft:enchanted_golden_apple', 2)], 400, 900),
    100: ([('cobblemon:master_ball', 3), ('cobblemon:rare_candy', 32), ('minecraft:enchanted_golden_apple', 3), ('cobblemon:relic_coin_sack', 1)], 1000, 2000),
}
icons = {10: 'cobblemon:pokedex_red', 20: 'cobblemon:pokedex_blue', 30: 'cobblemon:pokedex_green', 40: 'cobblemon:pokedex_yellow', 50: 'cobblemon:pokedex_pink',
         60: 'cobblemon:pokedex_white', 70: 'cobblemon:pokedex_black', 80: 'cobblemon:luxury_ball', 90: 'cobblemon:master_ball', 100: 'cobblemon:master_ball'}
pa['images'].append(label_image('pa:label:goals', '&cPokédex Completion', 0, -5.5, 8.0, 0.4))
prev = None
for i, pct in enumerate(range(10, 101, 10)):
    items, cd, xp = goal_rewards[pct]
    rewards = [item_reward(iid, n, f'pa:{pct}:r{iid}') for iid, n in items] + [cd_reward(cd, f'pa:{pct}:cd'), xp_reward(xp, f'pa:{pct}:xp')]
    color = '&a' if pct <= 30 else '&e' if pct <= 60 else '&6' if pct < 100 else '&d'
    q = new_quest(f'pa:goal:{pct}', f'{color}Pokédex: {pct}% Caught', icons[pct], [dex_task(pct, f'pa:{pct}:task')], rewards,
                  -10.8 + i * 2.4, -4.2, deps=[prev] if prev else (),
                  sub=f'&7Catch {pct}% of every Pokémon in the pack',
                  desc=[f'&fHave &e{pct}%&f of all the Pokémon in this modpack registered as &ecaught&f in your Pokédex.',
                        '&7Catching, evolving, hatching and trading all count. Addon Pokémon count too. The progress bar shows your current percentage.',
                        '&7The rewards get bigger with every 10%.' + (' This is the final goal: a complete Pokédex.' if pct == 100 else '')],
                  shape='hexagon' if pct % 50 == 0 else None, size=1.5 if pct == 100 else 1.25 if pct == 50 else None)
    lang[f'task.{q["tasks"][0]["id"]}.title'] = f'Register {pct}% of all Pokémon as caught'
    pa['quests'].append(q)
    prev = q['id']

# moved quests
ex = chapters['exploration_fossils_and_trainer_utilities']
MOVE = {'688470F43F810C32': (-9.0, 0.0), '507518777C14EB2B': (-6.6, 0.0), '751F2F2CFF0F8EF2': (-4.2, 0.0), '622867D85C28F95C': (-6.6, 2.4),
        '277A8287F1735165': (2.4, 0.0), '3E1E90E95C3C00A1': (2.4, 2.4), '5B760F0C10DFE721': (4.8, 0.0), '7D53223287FDC43F': (4.8, 2.6),
        '560461A3F608A747': (-6.0, 7.2), '6C51BEE295979C3B': (-3.6, 7.2), '3E106D44040F5F68': (-1.2, 7.2), '2CE8DE01FA1BC458': (1.2, 7.2), '05A1345C164645D7': (3.6, 7.2), '4B2CB1500216AE27': (6.0, 7.2)}
keep = []
for q in ex['quests']:
    if q['id'] in MOVE:
        q['x'], q['y'] = D(MOVE[q['id']][0]), D(MOVE[q['id']][1])
        for r in q.get('rewards', []):
            it = r.get('item')
            iid = it.get('id') if isinstance(it, dict) else it
            if iid in ('cobblenav:track_arrow', 'cobblemon:poke_bait'):
                r['item'] = copy.deepcopy(SNACK)
                if iid == 'cobblenav:track_arrow':
                    r['count'] = Num('2')
        pa['quests'].append(q)
    else:
        keep.append(q)
ex['quests'] = keep
ex['images'] = [i for i in ex['images'] if lang.get(f'image.{i["id"]}.title', '') in ('&cFinding and Tracking Pokémon', '&cBase, Ranch and Discoveries')]
pa['images'].append(label_image('pa:label:track', '&cFinding and Tracking Pokémon', -6.6, -1.3))
pa['images'].append(label_image('pa:label:fossil', '&cFossil Restoration', 3.6, -1.3))
pa['images'].append(label_image('pa:label:regions', '&cFossil Collections by Region', 0.0, 5.7, 12.0, 0.4))
for name, x in [('Kanto', -6.0), ('Hoenn', -3.6), ('Sinnoh', -1.2), ('Unova', 1.2), ('Kalos', 3.6), ('Galar', 6.0)]:
    pa['images'].append(label_image('pa:label:' + name, '&c' + name, x, 8.4, 2.25, 0.3))

# ---- 7. Medicine and Recovery: gather everything --------------------------------------------------
md = chapters['medicine_and_recovery']
for q in md['quests']:
    REMOVED_QUESTS.add(q['id'])
md['quests'] = []
md['images'] = [label_image('md:label', '&dStock the Medicine Cabinet', 0, -3.6, 8.0, 0.4)]
md['default_hide_dependency_lines'] = False
C = 'cobblemon:'
POTIONS = ['potion', 'super_potion', 'hyper_potion', 'max_potion', 'full_restore']
STATUS = ['antidote', 'awakening', 'burn_heal', 'ice_heal', 'paralyze_heal', 'full_heal']
REVIVES = ['revive', 'max_revive']
PP = ['ether', 'max_ether', 'elixir', 'max_elixir', 'pp_up', 'pp_max']
B_HEAL = ['cheri', 'chesto', 'pecha', 'rawst', 'aspear', 'leppa', 'oran', 'persim', 'lum', 'sitrus']
B_PINCH = ['figy', 'wiki', 'mago', 'aguav', 'iapapa', 'liechi', 'ganlon', 'salac', 'petaya', 'apicot', 'lansat', 'starf', 'enigma', 'micle', 'custap', 'jaboca', 'rowap', 'kee', 'maranga']
B_EV = ['pomeg', 'kelpsy', 'qualot', 'hondew', 'grepa', 'tamato']
B_RESIST = ['occa', 'passho', 'wacan', 'rindo', 'yache', 'chople', 'kebia', 'shuca', 'coba', 'payapa', 'tanga', 'charti', 'kasib', 'haban', 'colbur', 'babiri', 'chilan', 'roseli']
B_FARM = ['razz', 'bluk', 'nanab', 'wepear', 'pinap', 'cornn', 'magost', 'rabuta', 'nomel', 'spelon', 'pamtre', 'watmel', 'durin', 'belue', 'hopo', 'eggant', 'touga']


def gather(seed, title, icon, ids, x, y, rewards, sub, desc, size=None, shape=None):
    tasks = [item_task(C + i, f'{seed}:t:{i}') for i in ids]
    for t, i in zip(tasks, ids):
        lang[f'task.{t["id"]}.title'] = 'Have ' + item_name(C + i)
    return new_quest(seed, title, icon, tasks, rewards, x, y, sub=sub, desc=desc, size=size, shape=shape)


med = []
med.append(gather('md:potions', '&aEvery Potion', C + 'full_restore', POTIONS, -6.0, -2.0,
                  [item_reward(C + 'full_restore', 3, 'md:potions:r1'), cd_reward(50, 'md:potions:cd'), xp_reward(200, 'md:potions:xp')],
                  '&7Potion, Super, Hyper, Max and Full Restore',
                  ['&fCollect one of every HP potion: &ePotion&f (20 HP), &eSuper Potion&f (60), &eHyper Potion&f (120), &eMax Potion&f (full HP) and &eFull Restore&f (full HP and cures status).',
                   '&7Craft them with Medicinal Brew and berries, buy them from Cobble Merchants, or find them in loot.']))
med.append(gather('md:status', '&bEvery Status Cure', C + 'full_heal', STATUS, -2.0, -2.0,
                  [item_reward(C + 'full_heal', 5, 'md:status:r1'), cd_reward(50, 'md:status:cd'), xp_reward(200, 'md:status:xp')],
                  '&7Antidote, Awakening, Burn Heal, Ice Heal, Paralyze Heal, Full Heal',
                  ['&fCollect one of every status cure: &eAntidote&f (poison), &eAwakening&f (sleep), &eBurn Heal&f, &eIce Heal&f (freeze), &eParalyze Heal&f and &eFull Heal&f (any status).',
                   '&7Each is brewed from Medicinal Brew plus the matching berry.']))
med.append(gather('md:revive', '&dBack from the Brink', C + 'max_revive', REVIVES, 2.0, -2.0,
                  [item_reward(C + 'max_revive', 2, 'md:revive:r1'), cd_reward(25, 'md:revive:cd'), xp_reward(100, 'md:revive:xp')],
                  '&7Revive and Max Revive',
                  ['&fCollect a &eRevive&f (brings a fainted Pokémon back with half HP) and a &eMax Revive&f (full HP). Revival Herbs work too but lower friendship.']))
med.append(gather('md:pp', '&eEvery PP Item', C + 'pp_max', PP, 6.0, -2.0,
                  [item_reward(C + 'pp_max', 1, 'md:pp:r1'), item_reward(C + 'max_elixir', 2, 'md:pp:r2'), cd_reward(50, 'md:pp:cd'), xp_reward(250, 'md:pp:xp')],
                  '&7Ether, Max Ether, Elixir, Max Elixir, PP Up, PP Max',
                  ['&fCollect every PP item: &eEther&f (10 PP to one move), &eMax Ether&f (all PP of one move), &eElixir&f (10 PP to every move), &eMax Elixir&f (all moves fully), &ePP Up&f (raises a move\'s max PP) and &ePP Max&f (raises it to the limit).']))
berry_rows = [
    ('md:b_heal', '&aHealing Berries', 'oran_berry', B_HEAL, -6.0, 2.0, '&7Ten berries that heal HP or cure status', 'Oran, Sitrus, Leppa, Lum, Cheri, Chesto, Pecha, Rawst, Aspear and Persim. A held one is used automatically in battle.'),
    ('md:b_pinch', '&ePinch Berries', 'liechi_berry', B_PINCH, -2.0, 2.0, '&7Nineteen berries that trigger at low HP', 'Figy, Wiki, Mago, Aguav and Iapapa heal; Liechi, Ganlon, Salac, Petaya, Apicot, Lansat, Starf and Micle boost stats; Custap, Jaboca, Rowap, Kee, Maranga and Enigma do the rest.'),
    ('md:b_ev', '&cEV-Reset Berries', 'pomeg_berry', B_EV, 2.0, 2.0, '&7Six berries that lower EVs and raise friendship', 'Pomeg (HP), Kelpsy (Attack), Qualot (Defense), Hondew (Sp. Atk), Grepa (Sp. Def) and Tamato (Speed).'),
    ('md:b_resist', '&bType-Resist Berries', 'occa_berry', B_RESIST, 6.0, 2.0, '&7Eighteen berries that weaken one super-effective hit', 'One berry per type, from Occa (Fire) to Roseli (Fairy). Each halves the damage of one super-effective move, once.'),
    ('md:b_farm', '&6Garden Berries', 'pinap_berry', B_FARM, 0.0, 5.0, '&7Seventeen berries for cooking, mutations and trade', 'Razz, Bluk, Nanab, Wepear, Pinap, Cornn, Magost, Rabuta, Nomel, Spelon, Pamtre, Watmel, Durin, Belue, Hopo, Eggant and Touga. Grow mutations by planting different berries next to each other.'),
]
for seed, title, icon, ids, x, y, sub, extra in berry_rows:
    n = len(ids)
    med.append(gather(seed, title, C + icon, [b + '_berry' for b in ids], x, y,
                      [item_reward(C + 'rare_candy', 2, seed + ':r1'), item_reward(C + 'lum_berry', 4, seed + ':r2'), cd_reward(50, seed + ':cd'), xp_reward(50 + 10 * n, seed + ':xp')],
                      sub, [f'&fCollect one of each of these &e{n}&f berries. {extra}', '&7Berry trees grow from the berries themselves; wild ones spawn across many biomes.']))
final = new_quest('md:final', '&d★ The Complete Pharmacy', C + 'medicinal_brew', [{'id': hid('md:final:task'), 'type': 'checkmark'}],
                  [item_reward(C + 'master_ball', 1, 'md:final:r1'), item_reward(C + 'rare_candy', 10, 'md:final:r2'), item_reward(C + 'ability_capsule', 1, 'md:final:r3'),
                   item_reward(C + 'full_restore', 10, 'md:final:r4'), item_reward(C + 'max_elixir', 5, 'md:final:r5'), cd_reward(300, 'md:final:cd'), xp_reward(1000, 'md:final:xp')],
                  0.0, 7.4, deps=[q['id'] for q in med], sub='&7Every potion, cure, PP item and berry collected', shape='hexagon', size=1.5,
                  desc=['&fYou have gathered every potion, every status cure, both revives, every PP item and all 70 berries. Press Check to claim the big one.',
                        '&7Unlocks once every other quest on this page is done.'])
lang[f'task.{final["tasks"][0]["id"]}.title'] = 'Press Check'
md['quests'] = med + [final]

# ---- 8. Held Items: one quest per item ----------------------------------------------------------------
hi = chapters['held_items_and_battle_gear']
master = [q for q in hi['quests'] if q['id'] == '0D58D3F30EF397C3'][0]
groups_q = [q for q in hi['quests'] if q['id'] not in ('0D58D3F30EF397C3', '47BE5543E684460B')]
REMOVED_QUESTS.add('47BE5543E684460B')


def bullet_for(desc_lines, name):
    """The '• Name: text' bullet of an item inside a group description."""
    out = []
    key = name.lower()
    for ln in desc_lines:
        plain = re.sub(r'&[0-9a-fk-or]', '', ln)
        if '•' in plain and ':' in plain:
            label = plain.split('•', 1)[1].split(':', 1)[0].strip().lower()
            if label == key or label.replace('.', '') == key.replace('.', '') or key in label or label in key:
                out.append(plain.split(':', 1)[1].strip())
    return out


new_quests = []
cols = [-10.0, 0.0, 10.0]
col_y = [-4.0, -4.0, -4.0]
SP = 1.25
hi['images'] = [label_image('hi:label', '&dHeld Items and Battle Gear', 0, -6.0, 10.0, 0.4)]
for gi, g in enumerate(groups_q):
    REMOVED_QUESTS.add(g['id'])
    gtitle = lang.get(f'quest.{g["id"]}.title', '')
    gdesc = lang.get(f'quest.{g["id"]}.quest_desc', [])
    if isinstance(gdesc, str):
        gdesc = [gdesc]
    items = []
    for t in g.get('tasks', []):
        it = t.get('item')
        iid = it.get('id') if isinstance(it, dict) else it
        if t.get('type') == 'item' and iid:
            items.append(iid)
    if not items:
        continue
    col = gi % 3
    x0 = cols[col] - SP * (len(items) - 1) / 2
    y = col_y[col]
    short = gtitle.split(':', 1)[0].strip() if ':' in gtitle else gtitle
    hi['images'].append(label_image('hi:glabel:' + g['id'], short, cols[col], y - 0.95, max(3.0, min(9.5, len(re.sub(r'&[0-9a-fk-or]', '', short)) * 0.42 + 1.0)), 0.32))
    first = True
    for k, iid in enumerate(items):
        name = item_name(iid)
        bl = bullet_for(gdesc, name)
        intro = [re.sub(r'&[0-9a-fk-or]', '', l) for l in gdesc[:1]] if gdesc and '•' not in gdesc[0] else []
        desc = ['&f' + (bl[0] if bl else f'Get a {name} for your team.')]
        if intro:
            desc += ['', '&7' + intro[0]]
        desc += ['', '&7Part of: ' + re.sub(r'&[0-9a-fk-or]', '', gtitle)]
        rewards = [xp_reward(100, f'hi:{iid}:xp'), cd_reward(10, f'hi:{iid}:cd')]
        if first:
            for r in g.get('rewards', []):
                if r.get('type') == 'item':
                    rr = copy.deepcopy(r)
                    rr['id'] = hid(f'hi:{iid}:keep:{r["id"]}')
                    rewards.insert(0, rr)
            first = False
        q = new_quest(f'hi:item:{iid}', '&e' + name, iid, [item_task(iid, f'hi:{iid}:task')], rewards, x0 + k * SP, y,
                      sub='&7' + re.sub(r'&[0-9a-fk-or]', '', gtitle), desc=desc)
        lang[f'task.{q["tasks"][0]["id"]}.title'] = 'Have a ' + name
        new_quests.append(q)
    col_y[col] = y + 2.1
master['dependencies'] = [q['id'] for q in new_quests]
master['x'], master['y'] = D(0.0), D(max(col_y) + 0.5)
master['hide_dependency_lines'] = Num('1b')
master['shape'] = 'gear'
master['size'] = D(1.6)
set_xp(master, 1000, 'hi:master:xp')
master['rewards'].append(cd_reward(500, 'hi:master:cd', 'cobblemon:relic_coin_sack'))
hi['quests'] = new_quests + [master]
hi['default_hide_dependency_lines'] = True

# ---- 9. seasoned snacks + strip dependencies on removed quests -------------------------------------------
for ch in chapters.values():
    for q in ch['quests']:
        for r in q.get('rewards', []):
            if r.get('type') == 'item':
                seasoned(r)
        if 'dependencies' in q:
            q['dependencies'] = [d for d in q['dependencies'] if d not in REMOVED_QUESTS]
            if not q['dependencies']:
                del q['dependencies']

# ---- 10. write everything ----------------------------------------------------------------------------------
for f in glob.glob(DST + '/chapters/*.snbt'):
    os.remove(f)
for n, ch in chapters.items():
    snbt.save(f'{DST}/chapters/{n}.snbt', ch)
os.makedirs(DST + '/reward_tables', exist_ok=True)
for f in glob.glob(DST + '/reward_tables/*.snbt'):
    os.remove(f)
for f in glob.glob(SRC + '/reward_tables/*.snbt'):
    shutil.copy(f, DST + '/reward_tables/')
shutil.copy(SRC + '/chapter_groups.snbt', DST + '/chapter_groups.snbt')
shutil.copy(SRC + '/data.snbt', DST + '/data.snbt')
# lang: drop entries of things that no longer exist
live = set()
for ch in chapters.values():
    live.add(ch['id'])
    for q in ch['quests']:
        live.add(q['id'])
        for t in q.get('tasks', []):
            live.add(t['id'])
        for r in q.get('rewards', []):
            live.add(r['id'])
    for i in ch.get('images', []):
        live.add(i['id'])
for g in groups['chapter_groups']:
    live.add(g['id'])
out = {}
for k, v in lang.items():
    m = re.match(r'(quest|task|reward|image|chapter|chapter_group)\.([0-9A-F]{16})\.', k)
    if m and m.group(2) not in live:
        continue
    out[k] = v
snbt.save(DST + '/lang/en_us.snbt', out)
total = sum(len(ch['quests']) for ch in chapters.values())
print('chapters', len(chapters), 'quests', total, 'lang keys', len(out), 'held item quests', len(new_quests))
