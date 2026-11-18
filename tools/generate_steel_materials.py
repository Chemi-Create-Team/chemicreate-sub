#!/usr/bin/env python3
"""Steel stock and existing structural lesson templates."""
import gzip
import json
from pathlib import Path
import nbtlib
from PIL import Image, ImageDraw
import generate_metal_blocks as blocks
from generate_reactor_assets import A, D, NS, write

ROOT = Path(__file__).resolve().parents[1]
TEXT = {
    'item.create_refinery_n_reactors.steel_ingot': ('Steel Ingot', '鋼鉄インゴット', '钢锭'),
    'fuel_pool.create_refinery_n_reactors.status.wall': (
        'Enclose with steel blocks, 23 blocks tall', '鋼鉄ブロックで高さ23の壁を囲んでください', '用钢块围成23格高的池壁'),
    'create_refinery_n_reactors.ponder.shared.fuel_pool.structure': (
        'Lay 2–9 by 2–9 rack tiles. Enclose them in steel-block walls, 23 blocks high from the rack floor, leaving the 22 blocks above each rack empty.',
        'ラックを縦横2～9枚の長方形に並べ、床と同じ高さから23段の鋼鉄ブロックで囲みます。ラックの上22ブロックは空けてください。',
        '铺设2–9×2–9格格架，用钢块围成从格架底面起高23格的池壁，各格架上方22格留空。'),
    'cold_box.create_refinery_n_reactors.status.structure': (
        'Needs a 3x3x3 steel-block jacket and central regenerator',
        '鋼鉄ブロック3×3×3の断熱箱と中央の再生熱交換器が必要', '需要3×3×3钢块外壳与中央回热器'),
}


def ingot():
    image = Image.open(A / 'textures/item/aluminum_ingot.png').convert('RGBA')
    for y in range(image.height):
        for x in range(image.width):
            r, g, b, a = image.getpixel((x, y))
            if a:
                brightness = (r * 299 + g * 587 + b * 114) // 1000
                shade = 25 + brightness * 3 // 5
                image.putpixel((x, y), (shade, shade + 4, shade + 9, a))
    image.save(A / 'textures/item/steel_ingot.png')
    write(A / 'models/item/steel_ingot.json', {'parent': 'minecraft:item/generated', 'textures': {'layer0': NS + 'item/steel_ingot'}})
    blocks.tag('c/tags/item/ingots/steel.json', [NS + 'steel_ingot'])
    blocks.tag('c/tags/item/ingots.json', ['#c:ingots/steel'])


def structural_templates():
    paths = [ROOT / 'tools/data/manufacturing/helium_cold_box.nbt',
        A / 'ponder/plant/helium_cold_box.nbt', A / 'ponder/nuclear/fuel_pool.nbt',
        A / 'ponder/crane/crane_refuelling.nbt']
    for path in paths:
        structure = nbtlib.load(path)
        old = [index for index, state in enumerate(structure['palette']) if str(state['Name']) == 'create:industrial_iron_block']
        if not old: continue
        if path.stem == 'crane_refuelling':
            state = nbtlib.Compound({'Name': nbtlib.String(NS + 'steel_block')})
            try: index = structure['palette'].index(state)
            except ValueError:
                index = len(structure['palette']); structure['palette'].append(state)
            changed = False
            for block in structure['blocks']:
                x, y, z = map(int, block['pos'])
                if int(block['state']) in old and 10 <= x <= 13 and 5 <= z <= 8 and y >= 8:
                    block['state'] = nbtlib.Int(index); changed = True
            if not changed: continue
        else:
            for index in old: structure['palette'][index]['Name'] = nbtlib.String(NS + 'steel_block')
        structure.save(path, gzipped=False)
        path.write_bytes(gzip.compress(path.read_bytes(), mtime=0))


def translations():
    from generate_multiblock_lessons import TEXT as cold_box
    captions = {**TEXT, **{'create_refinery_n_reactors.ponder.shared.helium_cold_box.' + name: cold_box[name] for name in ('base', 'core')}}
    for index, locale in enumerate(('en_us', 'ja_jp', 'zh_cn')):
        path = A / 'lang' / (locale + '.json')
        raw = path.read_bytes().decode('utf-8'); newline = '\r\n' if '\r\n' in raw else '\n'
        strings = json.loads(raw)
        strings.update({key: values[index] for key, values in captions.items()})
        path.write_bytes((json.dumps(strings, ensure_ascii=False, indent=2).replace('\n', newline) + newline).encode('utf-8'))


def generate():
    selected = {'steel_block'}
    blocks.textures(selected); blocks.data(selected); blocks.translations(selected)
    ingot(); structural_templates(); translations()
    print('Generated steel ingots, blocks and structural lesson materials')


if __name__ == '__main__':
    generate()
