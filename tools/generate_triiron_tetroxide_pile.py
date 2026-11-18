#!/usr/bin/env python3
"""Triiron tetroxide pile: samples the three visible faces of the reference cube render into 16 px textures.

Usage: generate_triiron_tetroxide_pile.py <cube render png>
"""
from pathlib import Path
import json, sys
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
A = ROOT / 'src/main/resources/assets/create_refinery_n_reactors'
D = ROOT / 'src/main/resources/data/create_refinery_n_reactors'
NS = 'create_refinery_n_reactors:'
ID = 'triiron_tetroxide_pile'
# Corners (top-left, top-right, bottom-left, bottom-right) of each face in the isometric render.
FACES = {
    'front': ((54, 229), (512, 454), (54, 793), (512, 1018)),
    'side': ((512, 454), (970, 229), (512, 1018), (970, 794)),
    'top': ((54, 229), (512, 6), (512, 452), (970, 229)),
}


def sample(src, tl, tr, bl, br, u, v):
    top = [tl[i] + (tr[i] - tl[i]) * u for i in (0, 1)]
    bottom = [bl[i] + (br[i] - bl[i]) * u for i in (0, 1)]
    return src.getpixel((round(top[0] + (bottom[0] - top[0]) * v), round(top[1] + (bottom[1] - top[1]) * v)))


def write(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')


def build(render):
    src = Image.open(render).convert('RGB')
    for name, corners in FACES.items():
        face = Image.new('RGBA', (16, 16))
        for y in range(16):
            for x in range(16):
                if name == 'top':
                    # Diamond: x runs west to east, y north to south.
                    nw, ne, sw = corners[1], corners[3], corners[0]
                    px = (nw[0] + (ne[0] - nw[0]) * (x + .5) / 16 + (sw[0] - nw[0]) * (y + .5) / 16,
                          nw[1] + (ne[1] - nw[1]) * (x + .5) / 16 + (sw[1] - nw[1]) * (y + .5) / 16)
                    colour = src.getpixel((round(px[0]), round(px[1])))
                else:
                    colour = sample(src, *corners, (x + .5) / 16, (y + .5) / 16)
                face.putpixel((x, y), colour + (255,))
        face.save(A / f'textures/block/{ID}_{name}.png')
    tex = lambda s: NS + f'block/{ID}_{s}'
    write(A / f'models/block/{ID}.json', {'parent': 'minecraft:block/cube', 'textures': {
        'particle': tex('front'), 'up': tex('top'), 'down': tex('top'), 'north': tex('front'), 'south': tex('front'), 'east': tex('side'), 'west': tex('side')}})
    write(A / f'blockstates/{ID}.json', {'variants': {'': {'model': NS + f'block/{ID}'}}})
    write(A / f'models/item/{ID}.json', {'parent': NS + f'block/{ID}'})
    write(D / f'loot_table/blocks/{ID}.json', {'type': 'minecraft:block', 'pools': [{'rolls': 1, 'entries': [{'type': 'minecraft:item', 'name': NS + ID}],
          'conditions': [{'condition': 'minecraft:survives_explosion'}]}]})


if __name__ == '__main__':
    build(sys.argv[1])
    print('Generated triiron tetroxide pile')
