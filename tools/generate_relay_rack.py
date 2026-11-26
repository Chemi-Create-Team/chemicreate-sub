#!/usr/bin/env python3
"""Relay rack textures and models: open light-grey frame, rows of relays and cream terminal strips, wire looms on the sides."""
import json, random
from pathlib import Path
from PIL import Image

A = Path(__file__).resolve().parents[1] / 'src/main/resources/assets/create_refinery_n_reactors'
T, M = A / 'textures/block/relay', A / 'models/block/relay'
FRAME, FRAME_HI, FRAME_LO = (196, 196, 188), (222, 222, 214), (140, 140, 134)
CREAM, CREAM_LO, DARK, COIL = (226, 212, 176), (170, 150, 112), (38, 34, 32), (120, 78, 44)


def px(im, x, y, c): im.putpixel((x, y), tuple(c) + (255,))


def relays(frame):
    """Front of the bank: from top, terminal strip, two relay rows, terminal strip, two relay rows (tiles vertically)."""
    rnd = random.Random(3)
    im = Image.new('RGBA', (16, 16))
    for y in range(16):
        row = y % 8
        for x in range(16):
            if row == 0: c = FRAME_LO                                  # shelf rail
            elif row in (1, 2):                                         # terminal strip with screws
                c = CREAM if x % 2 else CREAM_LO
                if row == 2 and x % 2: c = (90, 80, 60)
            else:                                                       # relays: dark cases with coil ends
                cell = x % 4
                c = DARK if cell in (0, 3) else COIL if row in (4, 5) else (58, 52, 48)
                if row == 7: c = (24, 22, 20)
                if frame is not None and row == 3 and cell == 1:
                    on = (x // 4 + y // 8 * 2 + frame) % 3 == 0
                    c = (255, 170, 60) if on else (70, 50, 30)
            if not c == FRAME_LO: c = tuple(max(0, min(255, v + rnd.randint(-5, 5))) for v in c)
            px(im, x, y, c)
    return im


def frame_tex():
    im = Image.new('RGBA', (16, 16))
    for y in range(16):
        for x in range(16):
            px(im, x, y, FRAME_HI if x < 2 else FRAME_LO if x > 13 else FRAME)
    for y in (3, 12): px(im, 8, y, (120, 120, 114))
    return im


def loom():
    """Bundles of cloth-covered wire running down the side."""
    rnd = random.Random(5)
    cols = [(150, 82, 40), (110, 60, 32), (60, 40, 30), (180, 120, 70), (40, 40, 44)]
    im = Image.new('RGBA', (16, 16))
    for x in range(16):
        c = cols[x % len(cols)]
        for y in range(16):
            v = tuple(max(0, min(255, k + rnd.randint(-8, 8) - (20 if (y + x) % 5 == 0 else 0))) for k in c)
            px(im, x, y, v)
    for y in (2, 10):                                                   # lacing
        for x in range(16): px(im, x, y, (210, 200, 170))
    return im


def main():
    T.mkdir(parents=True, exist_ok=True)
    relays(None).save(T / 'rack.png')
    sheet = Image.new('RGBA', (16, 64))
    for f in range(4): sheet.paste(relays(f), (0, f * 16))
    sheet.save(T / 'rack_on.png')
    frame_tex().save(T / 'frame.png')
    loom().save(T / 'loom.png')
    side = frame_tex()
    for y in range(16):
        for x in range(2, 14): px(side, x, y, (70, 68, 64) if y % 4 else (96, 94, 88))
    side.save(T / 'computer_side.png')
    for on in (False, True):
        sfx = '_on' if on else ''
        f, r, l = '#frame', '#relays', '#loom'
        el = []
        def box(a, b, faces): el.append({'from': a, 'to': b, 'faces': faces})
        all6 = lambda t: {d: {'texture': t} for d in ('north', 'south', 'east', 'west', 'up', 'down')}
        for x in (0, 14):
            for z in (0, 14): box([x, 0, z], [x + 2, 16, z + 2], all6(f))
        box([2, 0, 3], [14, 16, 13], {'north': {'texture': r}, 'south': {'texture': r},
                                      'east': {'texture': '#back'}, 'west': {'texture': '#back'}})
        for x in (1, 13.5):
            box([x, 0, 5], [x + 1.5, 16, 11], {d: {'texture': l} for d in ('north', 'south', 'east', 'west')})
        for z in (0.5, 14.5):
            box([2, 15, z], [14, 16, z + 1], all6(f))
            box([2, 0, z], [14, 1, z + 1], all6(f))
        model = {'parent': 'block/block', 'ambientocclusion': False,
                 'textures': {'particle': f'create_refinery_n_reactors:block/relay/frame', 'frame': 'create_refinery_n_reactors:block/relay/frame',
                              'relays': f'create_refinery_n_reactors:block/relay/rack{sfx}', 'loom': 'create_refinery_n_reactors:block/relay/loom',
                              'back': 'create_refinery_n_reactors:block/relay/computer_side'},
                 'elements': el}
        (M / f'rack{sfx}.json').write_text(json.dumps(model, indent=2) + '\n')


if __name__ == '__main__':
    main()
