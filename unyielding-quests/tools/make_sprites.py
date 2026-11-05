"""Generates the default GUI sprites for Unyielding Quests.

Run:  python3 tools/make_sprites.py
Output: src/main/resources/assets/unyieldingquests/textures/gui/sprites/**.png (+ .png.mcmeta for nine-slice sprites)

These are the *default* art. Anyone can redraw them: keep the same file name, size and (for nine-slice
sprites) the same border width, and the game will use the new picture. A resource pack with the same paths
also overrides them.
"""
import json
import os
from PIL import Image

OUT = os.path.join(os.path.dirname(__file__), '..', 'src', 'main', 'resources', 'assets', 'unyieldingquests',
                   'textures', 'gui', 'sprites')
MANIFEST = []


def save(img, name, border=None, note=''):
    path = os.path.join(OUT, name + '.png')
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save(path)
    meta = path + '.mcmeta'
    if border is not None:
        with open(meta, 'w') as f:
            json.dump({"gui": {"scaling": {"type": "nine_slice", "width": img.width, "height": img.height,
                                           "border": border}}}, f, indent=2)
    elif os.path.exists(meta):
        os.remove(meta)
    MANIFEST.append((name, img.width, img.height, border, note))


def inside(x, y, w, h, r):
    """True when pixel (x, y) is inside a w*h rectangle with corner radius r (hard pixel edge)."""
    cx = min(max(x, r), w - 1 - r)
    cy = min(max(y, r), h - 1 - r)
    dx, dy = x - cx, y - cy
    return dx * dx + dy * dy <= (r + 0.35) ** 2


def lerp(a, b, t):
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(len(a)))


def rrect(w, h, r, top, bottom=None, border=None, highlight=None, shadow=None):
    """Rounded rectangle: vertical gradient fill, 1px border, 1px top highlight, 1px bottom shade."""
    bottom = bottom or top
    img = Image.new('RGBA', (w, h), (0, 0, 0, 0))
    px = img.load()
    for y in range(h):
        col = lerp(top, bottom, y / max(1, h - 1))
        for x in range(w):
            if not inside(x, y, w, h, r):
                continue
            edge = not (inside(x - 1, y, w, h, r) and inside(x + 1, y, w, h, r) and inside(x, y - 1, w, h, r) and inside(x, y + 1, w, h, r))
            if edge and border:
                px[x, y] = border
            elif highlight and inside(x, y - 1, w, h, r) and not inside(x, y - 2, w, h, r) and y <= r + 1:
                px[x, y] = highlight
            elif highlight and y == 1 and 0 < x < w - 1:
                px[x, y] = highlight
            elif shadow and y == h - 2:
                px[x, y] = shadow
            else:
                px[x, y] = col
    return img


def glyph(rows, color=(255, 255, 255, 255)):
    h = len(rows)
    w = max(len(r) for r in rows)
    img = Image.new('RGBA', (w, h), (0, 0, 0, 0))
    px = img.load()
    for y, row in enumerate(rows):
        for x, c in enumerate(row):
            if c == '#':
                px[x, y] = color
            elif c == '+':
                px[x, y] = (color[0], color[1], color[2], 140)
    return img


def badge(bg, rows, fg=(255, 255, 255, 255)):
    img = rrect(9, 9, 4, bg, lerp(bg, (0, 0, 0, 255), 0.25), border=(0, 0, 0, 200))
    g = glyph(rows, fg)
    img.alpha_composite(g, ((9 - g.width) // 2, (9 - g.height) // 2))
    return img


def main():
    # ---- panels ----------------------------------------------------------
    save(rrect(32, 32, 4, (24, 28, 37, 228), (17, 20, 27, 232), border=(6, 7, 10, 235),
               highlight=(255, 255, 255, 30)), 'panel', 5, 'Big glass window behind the quest book sidebar, detail page and party screen')
    save(rrect(16, 16, 3, (44, 50, 64, 205), (36, 41, 53, 205), border=(10, 12, 16, 200),
               highlight=(255, 255, 255, 22)), 'card', 4, 'Rows inside a window: task cards, reward cards, member rows')
    save(rrect(32, 16, 3, (48, 55, 72, 236), (34, 39, 51, 236), border=(6, 7, 10, 235),
               highlight=(255, 255, 255, 34)), 'header', 4, 'Top bar of the quest book and the title strip of windows')
    save(rrect(16, 16, 4, (232, 74, 62, 46), (232, 74, 62, 30), border=(232, 92, 78, 170)), 'goal', 5,
         'The red "Goal" box at the top of a quest page')

    # ---- buttons ---------------------------------------------------------
    base_border = (8, 9, 12, 255)
    save(rrect(32, 16, 3, (70, 79, 100, 255), (52, 59, 76, 255), border=base_border, highlight=(255, 255, 255, 60),
               shadow=(0, 0, 0, 60)), 'button', 4, 'Normal button')
    save(rrect(32, 16, 3, (96, 108, 136, 255), (72, 81, 104, 255), border=(232, 92, 78, 255), highlight=(255, 255, 255, 80),
               shadow=(0, 0, 0, 60)), 'button_hover', 4, 'Normal button under the mouse')
    save(rrect(32, 16, 3, (44, 48, 58, 255), (38, 42, 50, 255), border=base_border, highlight=(255, 255, 255, 18)),
         'button_disabled', 4, 'Button that cannot be clicked right now')
    save(rrect(32, 16, 3, (238, 84, 70, 255), (196, 52, 44, 255), border=(70, 14, 10, 255), highlight=(255, 190, 180, 120),
               shadow=(0, 0, 0, 60)), 'button_accent', 4, 'Red main-action button (Submit, Create party, Invite)')
    save(rrect(32, 16, 3, (255, 112, 96, 255), (222, 70, 58, 255), border=(255, 230, 220, 255), highlight=(255, 220, 210, 150),
               shadow=(0, 0, 0, 60)), 'button_accent_hover', 4, 'Red button under the mouse')
    save(rrect(32, 16, 3, (250, 206, 84, 255), (214, 156, 30, 255), border=(74, 50, 6, 255), highlight=(255, 240, 190, 150),
               shadow=(0, 0, 0, 60)), 'button_gold', 4, 'Gold reward button (Claim, Claim all)')
    save(rrect(32, 16, 3, (255, 226, 120, 255), (236, 182, 52, 255), border=(255, 250, 225, 255), highlight=(255, 250, 220, 170),
               shadow=(0, 0, 0, 60)), 'button_gold_hover', 4, 'Gold button under the mouse')

    # ---- tabs / rows / fields -------------------------------------------
    save(rrect(32, 16, 3, (40, 45, 58, 220), (34, 38, 49, 220), border=(8, 9, 12, 220), highlight=(255, 255, 255, 22)), 'tab', 4,
         'Party screen tab (not selected)')
    save(rrect(32, 16, 3, (58, 65, 82, 235), (46, 52, 66, 235), border=(8, 9, 12, 230), highlight=(255, 255, 255, 40)), 'tab_hover', 4,
         'Tab under the mouse')
    sel = rrect(32, 16, 3, (60, 68, 86, 245), (48, 54, 69, 245), border=(8, 9, 12, 240), highlight=(255, 255, 255, 50))
    for x in range(3, 29):
        sel.putpixel((x, 13), (232, 74, 62, 255))
        sel.putpixel((x, 12), (255, 120, 104, 255))
    save(sel, 'tab_selected', 4, 'The tab that is open (red underline)')
    save(rrect(16, 16, 3, (255, 255, 255, 20), (255, 255, 255, 14)), 'row_hover', 4, 'Chapter row under the mouse (sidebar)')
    row = rrect(16, 16, 3, (232, 74, 62, 70), (232, 74, 62, 46), border=(232, 92, 78, 130))
    for y in range(3, 13):
        row.putpixel((1, y), (255, 120, 104, 255))
    save(row, 'row_selected', 4, 'The chapter that is open (sidebar)')
    save(rrect(16, 16, 3, (8, 10, 13, 235), (12, 14, 18, 235), border=(70, 78, 96, 255)), 'field', 4, 'Text box (party name, chat)')
    save(rrect(16, 16, 3, (8, 10, 13, 245), (12, 14, 18, 245), border=(232, 92, 78, 255)), 'field_focused', 4, 'Text box you are typing in')

    # ---- small parts ------------------------------------------------------
    slot = rrect(18, 18, 2, (8, 10, 14, 210), (14, 16, 21, 210), border=(70, 78, 96, 200))
    save(slot, 'slot', None, 'Item frame behind task and reward items (18x18)')
    save(rrect(8, 5, 2, (0, 0, 0, 170), (0, 0, 0, 170), border=(0, 0, 0, 210)), 'bar_bg', 2, 'Progress bar background')
    save(rrect(8, 5, 2, (255, 255, 255, 255), (205, 205, 205, 255), highlight=(255, 255, 255, 255)), 'bar_fill', 2,
         'Progress bar fill (drawn white, the game colours it)')
    save(rrect(8, 9, 3, (255, 255, 255, 255), (220, 220, 220, 255)), 'chip', 3, 'Small rounded label behind words like DONE / LOCKED (game colours it)')
    save(rrect(4, 12, 1, (255, 255, 255, 110), (255, 255, 255, 80)), 'scroll_thumb', 1, 'Scroll bar handle')
    save(rrect(20, 20, 4, (70, 79, 100, 240), (52, 59, 76, 240), border=base_border, highlight=(255, 255, 255, 60)), 'inv_button', None,
         'Square Quests/Teams buttons beside the inventory (20x20)')
    save(rrect(20, 20, 4, (96, 108, 136, 250), (72, 81, 104, 250), border=(232, 92, 78, 255), highlight=(255, 255, 255, 80)),
         'inv_button_hover', None, 'Inventory button under the mouse')

    # ---- toast -------------------------------------------------------------
    t = rrect(160, 32, 5, (30, 35, 46, 245), (20, 23, 30, 245), border=(6, 7, 10, 255), highlight=(255, 255, 255, 34))
    for y in range(4, 28):
        for x in (2, 3):
            t.putpixel((x, y), (76, 194, 106, 255))
    t.alpha_composite(rrect(22, 22, 3, (8, 10, 14, 220), (14, 16, 21, 220), border=(76, 194, 106, 255)), (7, 5))
    save(t, 'toast', None, '"Quest complete" popup (160x32). The item is drawn inside the square on the left')

    # ---- badges (9x9) -------------------------------------------------------
    save(badge((76, 194, 106, 255), ["     #", "    # ", "#  #  ", " ##   "]), 'badge_check', None, 'Green tick on finished quests')
    save(badge((96, 104, 120, 255), [" ## ", "#  #", "####", "####"]), 'badge_lock', None, 'Padlock on locked quests')
    save(badge((255, 206, 64, 255), ["#", "#", "#", " ", "#"], (60, 36, 0, 255)), 'badge_alert', None, 'Gold "!" on quests with rewards to claim')

    # ---- icons (white, the game tints them) ---------------------------------
    icons = {
        'icon_close': ["#....#", ".#..#.", "..##..", "..##..", ".#..#.", "#....#"],
        'icon_book': [".######", "##+++++#"[:7], "##+++++", "##+++++", "##+++++", "##+++++", ".######"],
        'icon_team': ["..##...##.", ".####.####", ".####.####", "..##...##.", "..........", ".####.####", "##########"],
        'icon_gift': [".##.##.", "...#...", "#######", "#######", ".#.#.#.", ".#.#.#.", ".#####."],
        'icon_crown': ["#..#..#", "##.#.##", "#######", "#######", ".#####."],
        'icon_star': ["...#...", "..###..", "#######", ".#####.", ".##.##.", "#.....#"],
        'icon_gear': ["..##..", "######", "##..##", "##..##", "######", "..##.."],
        'icon_chat': ["#######", "#.....#", "#.....#", "#######", ".##....", ".#....."],
        'icon_join': ["...#...", "...#...", "#######", "...#...", "...#..."],
        'icon_check': ["......#", ".....##", "#...##.", "##.##..", ".###...", "..#...."],
        'icon_lock': ["..##..", ".#..#.", ".#..#.", "######", "##..##", "######"],
        'icon_quests': ["..#####..", ".#######.", "#########", "###...###", "#...#...#", "###...###", "#.......#", ".#.....#.", "..#####.."],
    }
    for n, rows in icons.items():
        save(glyph(rows), n, None, 'Small white icon (the game colours it)')

    with open(os.path.join(OUT, '..', '..', '..', 'ui', 'SPRITES.txt'), 'w') as f:
        f.write('Unyielding Quests GUI sprites (assets/unyieldingquests/textures/gui/sprites/)\n')
        f.write('name | size | nine-slice border | what it is\n')
        for n, w, h, b, note in MANIFEST:
            f.write(f"{n}.png | {w}x{h} | {b if b is not None else '-'} | {note}\n")
    print(len(MANIFEST), 'sprites written')


if __name__ == '__main__':
    os.makedirs(os.path.join(OUT, '..', '..', '..', 'ui'), exist_ok=True)
    main()
