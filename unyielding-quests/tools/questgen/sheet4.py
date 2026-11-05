import sys, os, re
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import gen, snbt
from gen import hid
import ch_milestones, ch_raids_league, ch_world
from openpyxl import Workbook
from openpyxl.styles import Font, PatternFill, Alignment
from openpyxl.utils import get_column_letter

DQ = '/home/claude/uq/src/main/resources/defaultquests'
ART = '/home/claude/uq/src/main/resources/assets/unyieldingquests/textures/quest_art'
have = {f[:-4].upper() for f in os.listdir(ART) if f.endswith('.png')}

def strip(s): return re.sub(r'&[0-9a-fk-or]', '', s)

chapters = [ch_milestones.chapter, ch_raids_league.raids, ch_raids_league.league,
            ch_world.travel, ch_world.hobbies, ch_world.dungeons, ch_world.biomes]
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
        elif t['type'] == 'item': what = 'The item: ' + (t['title'] or t.get('item', ''))
        elif t['type'] == 'cobblemon': what = 'Pokémon theme: ' + t['title']
        elif t['type'] == 'checkmark': what = 'Chapter intro picture: ' + strip(q['sub']).strip()
        else: what = t['title']
        if ch['file'] == '33_trainer_league' and 'Badge' in title: what = 'The gym badge: ' + title + ' (' + strip(q['sub']).replace('gym leader: ', '').strip() + ')'
        rows.append((cname, title, strip(q['sub']).strip(), what, qid + '.png', 'Done' if qid.upper() in have else ''))

# new quests from the team-book edits (Pokédex goals + Medicine cabinet)
lang = snbt.load(DQ + '/lang/en_us.snbt')
pa = snbt.load(DQ + '/chapters/14_sunny_time.snbt')
for q in pa['quests']:
    title = strip(lang.get(f'quest.{q["id"]}.title', ''))
    if title.startswith('Pokédex:'):
        pct = title.split(':')[1].strip()
        rows.append(('Pokédex Adventures', title, f'Have {pct} of all Pokémon caught', f'A Pokédex with a progress bar / glowing screen, {pct} filled. Make the 10 of them look like a set that gets fancier: red plastic at 10%, gold at 90%, rainbow/diamond at 100%', q['id'].lower() + '.png', 'Done' if q['id'] in have else ''))
md = snbt.load(DQ + '/chapters/medicine_and_recovery.snbt')
draw = {'Every Potion': 'All five potion bottles in a row (purple, blue, yellow, red, green)', 'Every Status Cure': 'Spray bottles with a little poison / zzz / flame / snowflake / lightning icon',
        'Back from the Brink': 'A yellow Revive crystal with a sparkle', 'Every PP Item': 'A blue Ether bottle with a yellow PP Up next to it',
        'Healing Berries': 'A pile of Oran and Sitrus berries', 'Pinch Berries': 'A Liechi berry with a sweat drop', 'EV-Reset Berries': 'A Pomeg berry with a down-arrow',
        'Type-Resist Berries': 'An Occa berry inside a shield', 'Garden Berries': 'A berry bush with mixed berries', '★ The Complete Pharmacy': 'A medicine cabinet full of bottles, pink cross on the door'}
for q in md['quests']:
    title = strip(lang.get(f'quest.{q["id"]}.title', ''))
    rows.append(('Medicine and Recovery', title, strip(lang.get(f'quest.{q["id"]}.subtitle', '')), draw.get(title, title), q['id'].lower() + '.png', 'Done' if q['id'] in have else ''))

wb = Workbook()
ws = wb.active; ws.title = 'Quest pictures'
head = ['Chapter', 'Quest', 'What the quest is about', 'What to draw', 'File name (use exactly)', 'Done?']
ws.append(head)
for r in rows: ws.append(list(r))
for c in range(1, 7):
    ws.cell(row=1, column=c).font = Font(bold=True, color='FFFFFF'); ws.cell(row=1, column=c).fill = PatternFill('solid', fgColor='8E44AD')
widths = [24, 34, 44, 62, 24, 8]
for i, w in enumerate(widths, 1): ws.column_dimensions[get_column_letter(i)].width = w
ws.freeze_panes = 'A2'
ws2 = wb.create_sheet('Chapter icons')
ws2.append(['Chapter', 'What to draw (chapter icon)', 'File name (use exactly)', 'Done?'])
icon_ideas = {'31_trainer_milestones': 'A gold trophy with a Poké Ball on it',
              '32_raid_dens': 'A glowing pink raid den rock with a beam of light', '33_trainer_league': 'A gym badge case / a row of three badges',
              '36_travel_and_trade': 'A waystone with a gold coin in front', '37_hobbies': 'An old camera and a flute',
              '38_dungeons_and_ruins': 'A rolled treasure map with an X and a small castle', '39_biomes_and_nature': 'A cherry blossom tree on a hill'}
CI = '/home/claude/uq/src/main/resources/assets/unyieldingquests/textures/chapter_icon'
for ch in chapters:
    ws2.append([strip(ch['title']).replace('> ', '').strip(), icon_ideas[ch['file']], ch['file'] + '.png', 'Done' if os.path.exists(f'{CI}/{ch["file"]}.png') else ''])
for c in range(1, 5):
    ws2.cell(row=1, column=c).font = Font(bold=True, color='FFFFFF'); ws2.cell(row=1, column=c).fill = PatternFill('solid', fgColor='2E86C1')
for i, w in enumerate([26, 60, 30, 8], 1): ws2.column_dimensions[get_column_letter(i)].width = w
ws3 = wb.create_sheet('How to')
for line in ['HOW TO MAKE THE PICTURES (same rules as last time)', '',
             'Quest pictures go in:   assets/unyieldingquests/textures/quest_art/',
             'Chapter icons go in:    assets/unyieldingquests/textures/chapter_icon/',
             'Size: 32x32 pixels, transparent background, pixel art.',
             'Moving picture: stack 4 frames top to bottom in ONE file -> 32 wide x 128 tall. A plain 32x32 file is a still picture (also fine).',
             'Keep the main shape the same in all 4 frames and only move small parts (eyes, flames, water, sparkles); the game plays 1-2-3-4-3-2-1.',
             'File name: exactly the name in the sheet, lower case, .png.',
             'Test it yourself: drop the assets folder into a resource pack (example pack in the kit) and open the quest book.',
             'No plain book icons please. Badges for the Trainer League are welcome (one badge per gym leader).',
             '',
             'CHANGES SINCE SHEET 3:',
             ' - The Twilight Forest, Boss Hunts and Ars Nouveau rows from sheet 3 are CANCELLED (the team\'s own book already has those pages). Skip them if not started.',
             ' - New: 10 "Pokédex: N% Caught" pictures and 10 Medicine and Recovery pictures (bottom of the Quest pictures sheet).',
             ' - The Held Items page now has one quest per item; those use the real item icons, so no art needed there.',
             ' - "Done" in the last column means a picture with that file name is already in the mod.',
             '', f'Quest pictures sheet: {len(rows)} quests. Chapter icons sheet: {len(chapters)} chapters.']:
    ws3.append([line])
ws3.column_dimensions['A'].width = 120
out = '/mnt/user-data/outputs/Astra-Art-Sheet-4.xlsx'
wb.save(out); print(out, len(rows))
