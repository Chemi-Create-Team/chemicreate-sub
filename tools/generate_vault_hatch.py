#!/usr/bin/env python3
"""Generate the vault hatch model, 16px metalwork, recipe and Ponder structure."""
import json
from pathlib import Path
from PIL import Image, ImageDraw
from generate_petrochemical_ponder import Structure, OUT

ROOT = Path(__file__).resolve().parents[1]
NS = 'create_refinery_n_reactors'
A = ROOT / 'src/main/resources/assets' / NS
D = ROOT / 'src/main/resources/data' / NS


def write(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, ensure_ascii=False, indent=2) + '\n')


image = Image.new('RGBA', (16, 16), '#343a3e')
draw = ImageDraw.Draw(image)
draw.rectangle((0, 0, 15, 15), outline='#555d61')
draw.line((1, 14, 1, 1, 14, 1), fill='#929b9b')
draw.line((2, 14, 14, 14, 14, 2), fill='#292f33')
draw.rectangle((3, 3, 12, 12), fill='#626b6e', outline='#3f484c')
draw.line((4, 11, 4, 4, 11, 4), fill='#798387')
draw.line((5, 11, 11, 11, 11, 5), fill='#535c60')
for x in (2, 13):
    for y in (2, 13):
        draw.point((x, y), fill='#a4a9a3')
for x in (3, 10):
    draw.rectangle((x, 0, x + 2, 2), fill='#746045')
    draw.line((x, 0, x + 2, 0), fill='#b29a62')
draw.rectangle((6, 9, 9, 10), fill='#282f33')
draw.line((6, 8, 9, 8), fill='#b29a62')
draw.line((6, 9, 9, 9), fill='#887448')
image.save(A / 'textures/block/vault_hatch.png')


def element(start, end, texture):
    x0, y0, z0 = start
    x1, y1, z1 = end
    faces = {}
    for side in ('north', 'south', 'east', 'west', 'up', 'down'):
        uv = [x0, 16-y1, x1, 16-y0] if side in ('north', 'south') else [z0, 16-y1, z1, 16-y0] if side in ('east', 'west') else [x0, z0, x1, z1]
        faces[side] = {'texture': '#' + texture, 'uv': uv}
    return {'from': start, 'to': end, 'faces': faces}


parts = [element([0, 0, 14], [16, 16, 16], 'hatch'),
         element([3, 14, 13], [6, 16, 14], 'metal'),
         element([10, 14, 13], [13, 16, 14], 'metal'),
         element([6, 5, 13], [10, 7, 14], 'metal')]
for opened in (False, True):
    elements = json.loads(json.dumps(parts))
    if opened:
        for part in elements:
            for key in ('from', 'to'):
                x, y, z = part[key]
                part[key] = [x, z, y]
    write(A / ('models/block/vault_hatch' + ('_open' if opened else '') + '.json'), {
        'parent': 'minecraft:block/block',
        'textures': {'hatch': NS + ':block/vault_hatch', 'metal': NS + ':block/reactor/steel', 'particle': NS + ':block/vault_hatch'},
        'elements': elements})
variants = {}
for face, x, y in [('north', 0, 0), ('east', 0, 90), ('south', 0, 180), ('west', 0, 270), ('up', 270, 0), ('down', 90, 0)]:
    for opened in (False, True):
        variants[f'facing={face},open={str(opened).lower()}'] = {
            'model': NS + ':block/vault_hatch' + ('_open' if opened else ''), 'x': x, 'y': y}
write(A / 'blockstates/vault_hatch.json', {'variants': variants})
write(A / 'models/item/vault_hatch.json', {'parent': NS + ':block/vault_hatch'})
write(D / 'recipe/vault_hatch.json', {'type': 'minecraft:crafting_shaped', 'pattern': [' A ', 'STS', ' S '],
    'key': {'A': {'item': 'create:andesite_alloy'}, 'S': {'item': NS + ':steel_ingot'}, 'T': {'item': 'minecraft:iron_trapdoor'}},
    'result': {'id': NS + ':vault_hatch', 'count': 1}})
write(D / 'loot_table/blocks/vault_hatch.json', {'type': 'minecraft:block', 'pools': [{'rolls': 1,
    'entries': [{'type': 'minecraft:item', 'name': NS + ':vault_hatch'}],
    'conditions': [{'condition': 'minecraft:survives_explosion'}]}]})

s = Structure(7, 5)
for x in range(2, 5):
    s.block(x, 1, 3, 'create:item_vault', axis='x', large='false')
s.block(3, 1, 2, NS + ':vault_hatch', facing='north', open='false')
s.block(2, 2, 3, 'create:andesite_funnel', facing='up', extracting='false')
s.block(4, 2, 3, NS + ':vault_hatch', facing='up', open='false')
s.save('vault_hatch')
(A / 'ponder/plant/vault_hatch.nbt').write_bytes((OUT / 'vault_hatch.nbt').read_bytes())
(OUT / 'vault_hatch.nbt').unlink()
