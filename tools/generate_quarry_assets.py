#!/usr/bin/env python3
"""The gantry quarry: the quarry drive's block, item, loot, recipe and pickaxe tag, and the parts its renderer draws (the head bin,
the bellows chute and the belt along the bridge) from Create's andesite casing, belt and chute textures. Its names and statuses
are written by generate_crane_assets.py. Run from the repository root."""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
A = ROOT / 'src/main/resources/assets/create_refinery_n_reactors'
D = ROOT / 'src/main/resources/data'
NS = 'create_refinery_n_reactors'
CASING, BELT, CHUTE = 'create:block/andesite_casing', 'create:block/belt', 'create:block/chute'
FACES = ('north', 'south', 'east', 'west', 'up', 'down')


def write(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + '\n')


def box(start, end, texture='#casing', faces=FACES, rotation=None):
    face = lambda side: {'texture': texture, **({'rotation': rotation} if rotation is not None and side in ('north', 'south') else {})}
    return {'from': list(start), 'to': list(end), 'faces': {side: face(side) for side in faces}}


def chute():
    """The bin on the drill head, and the alternating hoop and rubber fold the renderer threads along the chute's curve."""
    write(A / 'models/block/crane/head_bin.json', {'parent': 'minecraft:block/block', 'textures': {'particle': CASING, 'casing': CASING, 'chute': CHUTE},
        'elements': [box((2, 5, 2), (14, 16, 14), faces=('north', 'south', 'east', 'west', 'down')),
                     {'from': [2, 5, 2], 'to': [14, 16, 14], 'faces': {'up': {'texture': '#chute', 'uv': [0, 0, 8, 8]}}},
                     # The spout under the chute's foot, beside the stem along +X; the renderer turns +X towards the near rail.
                     box((14, 11, 4), (23, 16, 12))]})
    write(A / 'models/block/crane/chute_ring.json', {'parent': 'minecraft:block/block', 'textures': {'particle': CASING, 'casing': CASING},
        'elements': [box((4.5, 7.5, 4.5), (11.5, 8.5, 11.5))]})
    # The moving rubber is separate so scrolling its UVs never moves the steel frame.
    write(A / 'models/block/crane/bridge_belt.json', {'parent': 'minecraft:block/block', 'textures': {'particle': BELT, 'belt': BELT},
        'elements': [{'from': [0, 11, 5], 'to': [16, 12, 11], 'faces': {
            'up': {'texture': '#belt', 'uv': [0, 0, 16, 16], 'rotation': 90},
            'down': {'texture': '#belt', 'uv': [0, 0, 16, 16], 'rotation': 270}}}]})
    write(A / 'models/block/crane/bridge_belt_frame.json', {'parent': 'minecraft:block/block', 'textures': {'particle': CASING, 'casing': CASING},
        'elements': [box((0, 9, 4), (16, 11, 12)), box((0, 11, 4), (16, 13, 5)), box((0, 11, 11), (16, 13, 12))]})
    write(A / 'models/block/crane/chute_fold.json', {'parent': 'minecraft:block/block', 'textures': {'particle': BELT, 'belt': BELT},
        'elements': [box((5, 6.5, 5), (11, 9.5, 11), '#belt')]})


def drive():
    """The quarry drive's block, item, loot and recipe; it shares the crane drive's quarry model, its shaft square to the rails."""
    model = f'{NS}:block/crane/quarry_drive'
    write(A / 'blockstates/quarry_drive.json', {'variants': {'facing=south': {'model': 'create_refinery_n_reactors:block/crane/quarry_drive'}, 'facing=west': {'model': 'create_refinery_n_reactors:block/crane/quarry_drive', 'y': 90}, 'facing=north': {'model': 'create_refinery_n_reactors:block/crane/quarry_drive', 'y': 180}, 'facing=east': {'model': 'create_refinery_n_reactors:block/crane/quarry_drive', 'y': 270}}})
    write(A / 'models/item/quarry_drive.json', {'parent': model})
    write(D / f'{NS}/loot_table/blocks/quarry_drive.json', {'type': 'minecraft:block', 'pools': [{'rolls': 1, 'entries': [{'type': 'minecraft:item', 'name': f'{NS}:quarry_drive'}],
        'conditions': [{'condition': 'minecraft:survives_explosion'}]}]})
    write(D / f'{NS}/recipe/quarry_drive.json', {'type': 'minecraft:crafting_shapeless', 'ingredients': [{'item': 'create:gantry_carriage'}, {'item': 'create:mechanical_drill'},
        {'item': 'create:precision_mechanism'}, {'item': 'create:andesite_casing'}], 'result': {'id': f'{NS}:quarry_drive', 'count': 1}})
    path = D / 'minecraft/tags/block/mineable/pickaxe.json'
    tag = json.loads(path.read_text())
    tag['values'] = [value for value in tag['values'] if value != f'{NS}:crane_lift']
    if f'{NS}:quarry_drive' not in tag['values']: tag['values'].append(f'{NS}:quarry_drive')
    write(path, tag)
    crane = f'{NS}:block/crane/drive'
    write(A / 'blockstates/crane_drive.json', {'variants': {'axis=z': {'model': crane}, 'axis=x': {'model': crane, 'y': 90}, 'axis=y': {'model': crane, 'x': 90}}})


if __name__ == '__main__':
    chute(); drive()
    print('Generated the quarry drive, its head bin, chute and bridge belt')
