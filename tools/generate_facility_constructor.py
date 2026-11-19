#!/usr/bin/env python3
"""Derive the facility constructor from the resolved Create schematicannon, adding a selector panel."""
import argparse
import io
import copy
import json
import zipfile
from pathlib import Path
from PIL import Image, ImageDraw
import generate_petrochemical_ponder as ponder

ROOT = Path(__file__).resolve().parents[1]
NS = 'create_refinery_n_reactors'
A = ROOT / 'src/main/resources/assets' / NS
D = ROOT / 'src/main/resources/data'


def write(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, ensure_ascii=False, indent=2) + '\n')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--create-jar', required=True, type=Path)
    args = parser.parse_args()
    panel = {'name': 'Facility selector', 'from': [3, 3, 0], 'to': [13, 8, 2], 'faces': {
        face: {'uv': [0, 0, 10, 5], 'texture': '#selector'} for face in ('north', 'south', 'up', 'down', 'east', 'west')}}
    with zipfile.ZipFile(args.create_jar) as source:
        for kind, asset in [('block', 'block'), ('item', 'item')]:
            model = json.loads(source.read(f'assets/create/models/block/schematicannon/{asset}.json'))
            model['credit'] = 'Derived from Create 6.0.10 schematicannon assets, copyright The Create Team / The Creators of Create; Create-Plant facility selector'
            model['textures']['selector'] = NS + ':block/facility_constructor'
            model['elements'].append(copy.deepcopy(panel))
            write(A / f'models/{kind}/facility_constructor.json', model)
    with zipfile.ZipFile(args.create_jar) as source:
        license_path = ROOT / 'src/main/resources/META-INF/licenses/create-assets.md'
        license_path.parent.mkdir(parents=True, exist_ok=True)
        license_path.write_bytes(source.read('LICENSE.md_create-1.21.1'))
        gui = Image.open(io.BytesIO(source.read('assets/create/textures/gui/schematics_2.png'))).convert('RGBA').crop((0, 77, 213, 218))
    tile = gui.crop((40, 45, 44, 49))
    for left, right in ((9, 36), (165, 197)):
        for y in range(45, 87):
            for x in range(left, right): gui.putpixel((x, y), tile.getpixel((x % 4, y % 4)))
    (A / 'textures/gui').mkdir(parents=True, exist_ok=True)
    gui.save(A / 'textures/gui/facility_constructor.png')
    image = Image.new('RGBA', (16, 16), '#454a4b')
    draw = ImageDraw.Draw(image)
    draw.rectangle((0, 0, 9, 4), fill='#343b40', outline='#848c8b')
    draw.rectangle((1, 1, 8, 3), fill='#324e60')
    for x in (2, 4, 6):
        draw.rectangle((x, 1, x + 1, 2), fill='#b5a573')
        draw.point((x, 1), fill='#e0d2a5')
    image.save(A / 'textures/block/facility_constructor.png')
    write(A / 'blockstates/facility_constructor.json', {'variants': {f'facing={facing}': {'model': NS + ':block/facility_constructor', 'y': rotation}
        for facing, rotation in [('south', 0), ('west', 90), ('north', 180), ('east', 270)]}})
    write(D / NS / 'recipe/facility_constructor.json', {'type': 'minecraft:crafting_shaped', 'pattern': [' B ', 'SCS', ' A '],
        'key': {'B': {'item': 'create:brass_sheet'}, 'S': {'item': NS + ':steel_ingot'}, 'C': {'item': 'create:schematicannon'}, 'A': {'item': 'create:precision_mechanism'}},
        'result': {'id': NS + ':facility_constructor', 'count': 1}})
    write(D / NS / 'loot_table/blocks/facility_constructor.json', {'type': 'minecraft:block', 'pools': [{'rolls': 1,
        'entries': [{'type': 'minecraft:item', 'name': NS + ':facility_constructor'}], 'conditions': [{'condition': 'minecraft:survives_explosion'}]}]})
    for path in [D / 'minecraft/tags/block/mineable/pickaxe.json', D / 'create/tags/block/non_movable.json']:
        data = json.loads(path.read_text())
        data['values'] = [v for v in data['values'] if not v.endswith('_assembly_base')]
        if NS + ':facility_constructor' not in data['values']: data['values'].append(NS + ':facility_constructor')
        write(path, data)
    ponder.OUT = A / 'ponder/plant'
    scene = ponder.Structure(17, 12)
    scene.block(8, 1, 1, NS + ':facility_constructor', facing='south')
    scene.block(7, 1, 1, 'minecraft:chest', facing='north', type='single', waterlogged='false')
    scene.save('facility_constructor')


if __name__ == '__main__':
    main()
