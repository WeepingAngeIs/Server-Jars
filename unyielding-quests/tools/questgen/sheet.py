import sys, os, re
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import gen
from gen import hid
import ch_twilight, ch_milestones, ch_raids_league, ch_bosses_ars, ch_world
from openpyxl import Workbook
from openpyxl.styles import Font, PatternFill, Alignment
from openpyxl.utils import get_column_letter

def strip(s): return re.sub(r'&[0-9a-fk-or]', '', s)

chapters = [ch_twilight.chapter, ch_milestones.chapter, ch_raids_league.raids, ch_raids_league.league,
            ch_bosses_ars.bosses, ch_bosses_ars.ars, ch_world.travel, ch_world.hobbies, ch_world.dungeons, ch_world.biomes]
rows = []
for ch in chapters:
    cname = strip(ch['title']).replace('> ', '').strip()
    for q in ch['quests']:
        qid = hid(f"{ch['file']}:{q['key']}").lower()
        t = q['tasks'][0]
        title = strip(q['title'])
        if t['type'] == 'kill': what = 'The creature/boss: ' + (t.get('title') or t['entity'])
        elif t['type'] == 'structure': what = 'The place: ' + t['title'].replace('Find ', '').replace('Enter ', '').replace('Explore ', '')
        elif t['type'] == 'biome': what = 'A tiny landscape of this biome: ' + strip(q['sub']).strip()
        elif t['type'] == 'item':
            what = 'The item: ' + (t['title'] or t.get('item', ''))
        elif t['type'] == 'dimension': what = 'The Twilight Forest portal (pool of water with flowers around it, lightning)'
        elif t['type'] == 'cobblemon': what = 'Pokémon theme: ' + t['title']
        elif t['type'] == 'checkmark': what = 'Chapter intro picture: ' + strip(q['sub']).strip()
        else: what = t['title']
        if ch['file'] == '33_trainer_league' and 'Badge' in title: what = 'The gym badge: ' + title + ' (' + strip(q['sub']).replace('gym leader: ', '').strip() + ')'
        rows.append((cname, title, strip(q['sub']).strip(), what, qid + '.png', 'assets/unyieldingquests/textures/quest_art/'))

wb = Workbook()
ws = wb.active; ws.title = 'Quest pictures'
head = ['Chapter', 'Quest', 'What the quest is about', 'What to draw', 'File name (use exactly)', 'Folder', 'Done?']
ws.append(head)
for r in rows: ws.append(list(r) + [''])
for c in range(1, 8):
    ws.cell(row=1, column=c).font = Font(bold=True, color='FFFFFF'); ws.cell(row=1, column=c).fill = PatternFill('solid', fgColor='8E44AD')
widths = [22, 30, 42, 60, 24, 44, 8]
for i, w in enumerate(widths, 1): ws.column_dimensions[get_column_letter(i)].width = w
ws.freeze_panes = 'A2'
ws2 = wb.create_sheet('Chapter icons')
ws2.append(['Chapter', 'What to draw (chapter icon)', 'File name (use exactly)', 'Folder', 'Done?'])
icon_ideas = {'30_twilight_forest': 'A Twilight Forest portal: pool of water ringed with flowers, lightning bolt', '31_trainer_milestones': 'A gold trophy with a Poké Ball on it',
              '32_raid_dens': 'A glowing pink raid den rock with a beam of light', '33_trainer_league': 'A gym badge case / a row of three badges', '34_boss_hunts': 'A crossed sword and a monster claw, red glow',
              '35_ars_nouveau': 'An open purple spell book with sparkles', '36_travel_and_trade': 'A waystone with a gold coin in front', '37_hobbies': 'An old camera and a flute',
              '38_dungeons_and_ruins': 'A rolled treasure map with an X and a small castle', '39_biomes_and_nature': 'A cherry blossom tree on a hill'}
for ch in chapters:
    ws2.append([strip(ch['title']).replace('> ', '').strip(), icon_ideas[ch['file']], ch['file'] + '.png', 'assets/unyieldingquests/textures/chapter_icon/', ''])
for c in range(1, 6):
    ws2.cell(row=1, column=c).font = Font(bold=True, color='FFFFFF'); ws2.cell(row=1, column=c).fill = PatternFill('solid', fgColor='2E86C1')
for i, w in enumerate([26, 60, 30, 48, 8], 1): ws2.column_dimensions[get_column_letter(i)].width = w
ws3 = wb.create_sheet('How to')
for line in ['HOW TO MAKE THE PICTURES (same rules as last time)', '',
             'Size: 32x32 pixels, transparent background, pixel art.',
             'Moving picture: stack 4 frames top to bottom in ONE file -> 32 wide x 128 tall. A plain 32x32 file is a still picture (also fine).',
             'Keep the main shape the same in all 4 frames and only move small parts (eyes, flames, water, sparkles); the game plays 1-2-3-4-3-2-1.',
             'File name: exactly the name in the sheet, lower case, .png. Put it in the folder shown.',
             'Test it yourself: drop the assets folder into a resource pack (example pack in the kit) and open the quest book.',
             'No plain book icons please. Badges for the Trainer League are welcome (one badge per gym leader).',
             'Chapter icons: 32x32 (or 32x128 moving), folder chapter_icon, file name = chapter file name.',
             '', 'Quest pictures sheet: 289 quests. Chapter icons sheet: 10 chapters.']:
    ws3.append([line])
ws3.column_dimensions['A'].width = 120
out = '/mnt/user-data/outputs/Astra-Art-Sheet-3.xlsx'
wb.save(out); print(out, len(rows))
