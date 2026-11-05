import sys, os
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import gen
from gen import hid, write_chapter, write_lang, write_groups, WARN
import ch_twilight, ch_milestones, ch_raids_league, ch_bosses_ars, ch_world

lang = {}
# new group inserted after "Dimensions and Creatures"
write_groups([('world_exploration', '&6&lWorld Exploration&r', '6E41A6B769F20604')], lang)
gid = hid('group:world_exploration')
# twilight / boss hunts / ars nouveau are left out: the team's own book has those chapters now
chapters = [ch_milestones.chapter, ch_raids_league.raids, ch_raids_league.league,
            ch_world.travel, ch_world.hobbies, ch_world.dungeons, ch_world.biomes]
total = 0
for ch in chapters:
    if ch['file'] in ('38_dungeons_and_ruins', '39_biomes_and_nature'):
        for q in ch['quests']:
            if q['deps'] == ['intro']:
                q['hide_lines'] = True
    if ch['group'] == 'group:world_exploration':
        ch['group'] = gid
    cid, keys = write_chapter(ch, lang)
    total += len(ch['quests'])
    print(ch['file'], cid, len(ch['quests']), 'quests')
write_lang(lang, 'GENERATED v1.4')
print('total new quests', total)
seen = set()
for w in WARN:
    if w not in seen:
        seen.add(w); print('WARN', w)
