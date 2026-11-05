#!/usr/bin/env python3
"""Generates UnyieldingQuests chapter SNBT files + lang entries from Python specs.

Usage: python3 gen.py <quest dir>  (writes chapters/*.snbt, appends lang/en_us.snbt, updates chapter_groups.snbt)
"""
import hashlib, json, os, re, sys, glob

QDIR = sys.argv[1] if len(sys.argv) > 1 else '/home/claude/uq/src/main/resources/defaultquests'
REG = json.load(open('/home/claude/modjars/registry.json')) if os.path.exists('/home/claude/modjars/registry.json') else {}

# ---- id helpers -------------------------------------------------------------
USED = set()
GENERATED_FILES = ('30_', '31_', '32_', '33_', '34_', '35_', '36_', '37_', '38_', '39_')
for f in glob.glob(os.path.join(QDIR, 'chapters', '*.snbt')):
    if os.path.basename(f).startswith(GENERATED_FILES):
        continue  # our own output; ids are recomputed deterministically
    USED.update(re.findall(r'id: "([0-9A-F]{16})"', open(f, encoding='utf-8').read()))
for f in [os.path.join(QDIR, 'chapter_groups.snbt')]:
    USED.update(re.findall(r'id: "([0-9A-F]{16})"', open(f, encoding='utf-8').read()))
GEN = {}


def hid(seed):
    """Stable 16-hex id for a seed string (never collides with existing ids)."""
    if seed in GEN:
        return GEN[seed]
    n = 0
    while True:
        h = hashlib.sha1(f'{seed}#{n}'.encode()).hexdigest()[:16].upper()
        if h not in USED and h[0] != '0':
            USED.add(h)
            GEN[seed] = h
            return h
        n += 1


# ---- known ids (for validation) ---------------------------------------------
KNOWN_ITEMS, KNOWN_ENT, KNOWN_STRUCT, KNOWN_BIOME, KNOWN_ADV, KNOWN_TAGS = set(), set(), set(), set(), set(), set()
for jar, info in REG.items():
    KNOWN_ITEMS.update(info['items'].keys())
    KNOWN_ITEMS.update(info['blocks'].keys())
    KNOWN_ENT.update(info['entities'].keys())
    KNOWN_STRUCT.update(info['structures'])
    KNOWN_TAGS.update(info['structure_tags'])
    KNOWN_BIOME.update(info['biomes'])
    KNOWN_ADV.update(info['advancements'])
# Twilight Forest (from the GitHub source) and vanilla are checked separately
TF_ITEMS = set()
try:
    src = open('/home/claude/teamtwilight/twilightforest/src/main/java/twilightforest/init/TFItems.java').read()
    TF_ITEMS.update('twilightforest:' + x for x in re.findall(r'register\("([a-z_0-9]+)"', src))
    src = open('/home/claude/teamtwilight/twilightforest/src/main/java/twilightforest/init/TFBlocks.java').read()
    TF_ITEMS.update('twilightforest:' + x for x in re.findall(r'register\("([a-z_0-9]+)"', src))
    src = open('/home/claude/teamtwilight/twilightforest/src/main/java/twilightforest/entity/TFEntityNames.java').read()
    KNOWN_ENT.update('twilightforest:' + x for x in re.findall(r'"([a-z_0-9]+)"', src))
    for f in glob.glob('/home/claude/teamtwilight/twilightforest/src/generated/resources/data/twilightforest/worldgen/structure/*.json'):
        KNOWN_STRUCT.add('twilightforest:' + os.path.basename(f)[:-5])
    for f in glob.glob('/home/claude/teamtwilight/twilightforest/src/generated/resources/data/twilightforest/worldgen/biome/*.json'):
        KNOWN_BIOME.add('twilightforest:' + os.path.basename(f)[:-5])
    for f in glob.glob('/home/claude/teamtwilight/twilightforest/src/generated/resources/data/twilightforest/advancement/*.json'):
        KNOWN_ADV.add('twilightforest:' + os.path.basename(f)[:-5])
except FileNotFoundError:
    pass
KNOWN_ITEMS |= TF_ITEMS
VANILLA = set()
try:
    VANILLA = set('minecraft:' + x for x in open('/home/claude/modjars/vanilla_items.txt').read().split())
except FileNotFoundError:
    pass
WARN = []


def check_item(i):
    if i.startswith('minecraft:'):
        if VANILLA and i not in VANILLA:
            WARN.append(f'unknown vanilla item {i}')
    elif i.startswith('cobblemon:'):
        if i not in KNOWN_ITEMS:
            WARN.append(f'unknown cobblemon item {i}')
    elif i not in KNOWN_ITEMS and i != 'unyieldingquests:quest_book':
        WARN.append(f'unknown item {i}')


# ---- spec helpers -------------------------------------------------------------
class T:
    @staticmethod
    def item(id, n=1, title=None, consume=False):
        return {'type': 'item', 'item': id, 'count': n, 'consume': consume, 'title': title}

    @staticmethod
    def anyof(ids, title, n=1):
        return {'type': 'item', 'filter': 'or(' + ''.join(f'item({i})' for i in ids) + ')', 'count': n, 'title': title, 'ids': ids}

    @staticmethod
    def adv(id, title):
        return {'type': 'advancement', 'advancement': id, 'title': title}

    @staticmethod
    def struct(id, title):
        return {'type': 'structure', 'structure': id, 'title': title}

    @staticmethod
    def biome(id, title):
        return {'type': 'biome', 'biome': id, 'title': title}

    @staticmethod
    def dim(id, title):
        return {'type': 'dimension', 'dimension': id, 'title': title}

    @staticmethod
    def kill(entity, n=1, title=None):
        return {'type': 'kill', 'entity': entity, 'value': n, 'title': title}

    @staticmethod
    def mon(action, n=1, pokemon='', ptype='', dex='', shiny=False, title=None):
        return {'type': 'cobblemon', 'action': action, 'amount': n, 'pokemon': pokemon, 'pokemon_type': ptype, 'dex_progress': dex, 'shiny': shiny, 'title': title}

    @staticmethod
    def check(title='Read this, then press Check'):
        return {'type': 'checkmark', 'title': title}


class R:
    @staticmethod
    def item(id, n=1):
        return {'type': 'item', 'item': id, 'count': n}

    @staticmethod
    def xp(n):
        return {'type': 'xp', 'xp': n}


def poke_icon(species):
    return {'pokemon': species}


def Q(key, title, icon, tasks, rewards=(), deps=(), pos=(0, 0), sub='', desc=(), shape=None, size=None, optional=False, hide_lines=False):
    return dict(key=key, title=title, icon=icon, tasks=tasks, rewards=list(rewards), deps=list(deps), pos=pos, sub=sub,
                desc=list(desc), shape=shape, size=size, optional=optional, hide_lines=hide_lines)


# ---- SNBT writing ---------------------------------------------------------------
def s(v):
    return '"' + v.replace('\\', '\\\\').replace('"', '\\"') + '"'


def d(v):
    return f'{float(v):.1f}d' if float(v) == int(float(v)) else f'{float(v)}d'


def icon_snbt(icon, ind):
    if isinstance(icon, dict) and 'pokemon' in icon:
        sp = icon['pokemon']
        sp = sp if ':' in sp else 'cobblemon:' + sp
        return (f'{ind}icon: {{\n{ind}\tcomponents: {{\n{ind}\t\t"cobblemon:pokemon_item": {{\n{ind}\t\t\taspects: [ ]\n'
                f'{ind}\t\t\tspecies: {s(sp)}\n{ind}\t\t}}\n{ind}\t}}\n{ind}\tid: "cobblemon:pokemon_model"\n{ind}}}\n')
    check_item(icon)
    return f'{ind}icon: {{\n{ind}\tid: {s(icon)}\n{ind}}}\n'


def task_snbt(t, tid, ind):
    out = [f'{ind}{{\n']
    i2 = ind + '\t'
    ty = t['type']
    if ty == 'item':
        if 'filter' in t:
            for i in t['ids']:
                check_item(i)
            out.append(f'{i2}count: {t["count"]}L\n')
            out.append(f'{i2}id: {s(tid)}\n')
            out.append(f'{i2}item: {{\n{i2}\tcomponents: {{\n{i2}\t\t"ftbfiltersystem:filter": {s(t["filter"])}\n{i2}\t}}\n{i2}\tid: "ftbfiltersystem:smart_filter"\n{i2}}}\n')
            out.append(f'{i2}type: "item"\n')
        else:
            check_item(t['item'])
            if t.get('consume'):
                out.append(f'{i2}consume_items: true\n')
            out.append(f'{i2}count: {t["count"]}L\n')
            out.append(f'{i2}id: {s(tid)}\n')
            out.append(f'{i2}item: {s(t["item"])}\n')
            out.append(f'{i2}type: "item"\n')
    elif ty == 'advancement':
        a = t['advancement']
        if a not in KNOWN_ADV and not a.startswith('minecraft:'):
            WARN.append(f'unknown advancement {a}')
        out.append(f'{i2}advancement: {s(a)}\n{i2}criterion: ""\n{i2}id: {s(tid)}\n{i2}type: "advancement"\n')
    elif ty == 'structure':
        st = t['structure']
        if st.startswith('#'):
            if st[1:] not in KNOWN_TAGS and not st.startswith('#minecraft:'):
                WARN.append(f'unknown structure tag {st}')
        elif st not in KNOWN_STRUCT and not st.startswith('minecraft:'):
            WARN.append(f'unknown structure {st}')
        out.append(f'{i2}id: {s(tid)}\n{i2}structure: {s(st)}\n{i2}type: "structure"\n')
    elif ty == 'biome':
        b = t['biome']
        if not b.startswith('#') and b not in KNOWN_BIOME and not b.startswith('minecraft:'):
            WARN.append(f'unknown biome {b}')
        out.append(f'{i2}biome: {s(b)}\n{i2}id: {s(tid)}\n{i2}type: "biome"\n')
    elif ty == 'dimension':
        out.append(f'{i2}dimension: {s(t["dimension"])}\n{i2}id: {s(tid)}\n{i2}type: "dimension"\n')
    elif ty == 'kill':
        e = t['entity']
        if e not in KNOWN_ENT and not e.startswith('minecraft:') and not e.startswith('#'):
            WARN.append(f'unknown entity {e}')
        out.append(f'{i2}entity: {s(e)}\n{i2}id: {s(tid)}\n{i2}type: "kill"\n{i2}value: {t["value"]}L\n')
    elif ty == 'cobblemon':
        out.append(f'{i2}action: {s(t["action"])}\n{i2}amount: {t["amount"]}L\n')
        if t['dex_progress']:
            out.append(f'{i2}dex_progress: {s(t["dex_progress"])}\n')
        out.append(f'{i2}id: {s(tid)}\n')
        if t['pokemon']:
            out.append(f'{i2}pokemon: {s(t["pokemon"])}\n')
        if t['pokemon_type']:
            out.append(f'{i2}pokemon_type: {s(t["pokemon_type"])}\n')
        if t['shiny']:
            out.append(f'{i2}shiny: true\n')
        out.append(f'{i2}type: "unyieldingquests:cobblemon"\n')
    elif ty == 'checkmark':
        out.append(f'{i2}id: {s(tid)}\n{i2}type: "checkmark"\n')
    out.append(f'{ind}}}\n')
    return ''.join(out)


def reward_snbt(r, rid, ind):
    i2 = ind + '\t'
    if r['type'] == 'item':
        check_item(r['item'])
        return (f'{ind}{{\n{i2}count: {r["count"]}\n{i2}id: {s(rid)}\n{i2}item: {{\n{i2}\tcount: 1\n{i2}\tid: {s(r["item"])}\n{i2}}}\n'
                f'{i2}type: "item"\n{ind}}}\n')
    return f'{ind}{{\n{i2}id: {s(rid)}\n{i2}type: "xp"\n{i2}xp: {r["xp"]}\n{ind}}}\n'


def write_chapter(ch, lang):
    """ch: dict(file, title, group, order, icon, sub, shape, quests, autofocus)"""
    cid = hid('chapter:' + ch['file'])
    keys = {q['key']: hid(f'{ch["file"]}:{q["key"]}') for q in ch['quests']}
    lang[f'chapter.{cid}.title'] = ch['title']
    if ch.get('sub'):
        lang[f'chapter.{cid}.chapter_subtitle'] = ch['sub']
    out = ['{\n']
    if ch.get('autofocus'):
        out.append(f'\tautofocus_id: {s(keys[ch["autofocus"]])}\n')
    out.append('\tdefault_hide_dependency_lines: false\n')
    out.append(f'\tdefault_quest_shape: {s(ch.get("shape", "circle"))}\n')
    out.append(f'\tfilename: {s(ch["file"])}\n')
    out.append(f'\tgroup: {s(ch["group"])}\n')
    out.append(icon_snbt(ch['icon'], '\t'))
    out.append(f'\tid: {s(cid)}\n')
    out.append('\timages: [ ]\n')
    out.append(f'\torder_index: {ch["order"]}\n')
    out.append('\tquest_links: [ ]\n')
    out.append('\tquests: [\n')
    for q in ch['quests']:
        qid = keys[q['key']]
        for dep in q['deps']:
            if dep not in keys:
                raise SystemExit(f'{ch["file"]}: quest {q["key"]} depends on unknown {dep}')
        lang[f'quest.{qid}.title'] = q['title']
        if q['sub']:
            lang[f'quest.{qid}.quest_subtitle'] = q['sub']
        if q['desc']:
            lang[f'quest.{qid}.quest_desc'] = list(q['desc'])
        out.append('\t\t{\n')
        if q['deps']:
            out.append('\t\t\tdependencies: [' + ', '.join(s(keys[x]) for x in q['deps']) + ']\n')
        if q['hide_lines']:
            out.append('\t\t\thide_dependency_lines: true\n')
        out.append(icon_snbt(q['icon'], '\t\t\t'))
        out.append(f'\t\t\tid: {s(qid)}\n')
        if q['optional']:
            out.append('\t\t\toptional: true\n')
        if q['rewards']:
            out.append('\t\t\trewards: [\n')
            for i, r in enumerate(q['rewards']):
                out.append(reward_snbt(r, hid(f'{ch["file"]}:{q["key"]}:r{i}'), '\t\t\t\t'))
            out.append('\t\t\t]\n')
        if q['shape']:
            out.append(f'\t\t\tshape: {s(q["shape"])}\n')
        if q['size']:
            out.append(f'\t\t\tsize: {d(q["size"])}\n')
        out.append('\t\t\ttasks: [\n')
        for i, t in enumerate(q['tasks']):
            tid = hid(f'{ch["file"]}:{q["key"]}:t{i}')
            if t.get('title'):
                lang[f'task.{tid}.title'] = t['title']
            out.append(task_snbt(t, tid, '\t\t\t\t'))
        out.append('\t\t\t]\n')
        out.append(f'\t\t\tx: {d(q["pos"][0])}\n\t\t\ty: {d(q["pos"][1])}\n')
        out.append('\t\t}\n')
    out.append('\t]\n')
    out.append('}\n')
    path = os.path.join(QDIR, 'chapters', ch['file'] + '.snbt')
    open(path, 'w', encoding='utf-8').write(''.join(out))
    return cid, keys


def lang_value(v):
    if isinstance(v, list):
        if len(v) == 1:
            return '[' + s(v[0]) + ']'
        return '[\n' + ''.join('\t\t' + s(x) + '\n' for x in v) + '\t]'
    return s(v)


def write_lang(lang, marker):
    """Appends (or replaces) our generated block in lang/en_us.snbt."""
    path = os.path.join(QDIR, 'lang', 'en_us.snbt')
    text = open(path, encoding='utf-8').read()
    start, end = f'\t# BEGIN {marker}\n', f'\t# END {marker}\n'
    if start in text:
        a = text.index(start)
        b = text.index(end) + len(end)
        text = text[:a] + text[b:]
    block = start + ''.join(f'\t{k}: {lang_value(v)}\n' for k, v in sorted(lang.items())) + end
    text = text.rstrip()
    assert text.endswith('}')
    text = text[:-1].rstrip('\n') + '\n' + block + '}\n'
    open(path, 'w', encoding='utf-8').write(text)


def write_groups(new_groups, lang):
    """new_groups: list of (file-key, title, insert-after-id)."""
    path = os.path.join(QDIR, 'chapter_groups.snbt')
    text = open(path, encoding='utf-8').read()
    for key, title, after in new_groups:
        gid = hid('group:' + key)
        lang[f'chapter_group.{gid}.title'] = title
        line = f'\t\t{{ id: {s(gid)} }}\n'
        if gid in text:
            continue
        anchor = f'\t\t{{ id: {s(after)} }}\n'
        text = text.replace(anchor, anchor + line, 1)
    open(path, 'w', encoding='utf-8').write(text)
