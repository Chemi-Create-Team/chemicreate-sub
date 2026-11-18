#!/usr/bin/env python3
"""Steel, stainless steel and titanium blocks: textures, models, loot, recipes, tags, their Sable mass and translations. Run from the repository root.

Stainless steel is a bright, brushed plate in a darker frame with four bolts; titanium a grid of four darker blue-grey plates
welded edge to edge. In Create: Deep Seas both are pressure hulls (compat/deepseas);
in Sable the titanium block weighs what titanium does, about 57% of iron, and the stainless one as much as iron."""
import json, sys
from PIL import Image
sys.path.insert(0, 'tools')
from minecraft_style import grain
from generate_reactor_assets import A, D, NS, write

BLOCKS = {
    'steel_block': ('steel', 'c:ingots/steel', 4.0, ('Block of Steel', '鋼鉄ブロック', '钢块')),
    'stainless_steel_block': ('stainless_steel', 'c:ingots/stainless_steel', 4.0, ('Block of Stainless Steel', 'ステンレス鋼ブロック', '不锈钢块')),
    'titanium_block': ('titanium', 'c:ingots/titanium', 2.3, ('Block of Titanium', 'チタンブロック', '钛块')),
}


def shade(color, value=0):
    return tuple(max(0, min(255, c + value)) for c in color) + (255,)


def stainless(x, y):
    edge = x in (0, 15) or y in (0, 15)
    frame = x in (1, 14) or y in (1, 14)
    if edge: return shade((112, 118, 126), -6 if x == 15 or y == 15 else 0)
    if (x, y) in ((2, 2), (13, 2), (2, 13), (13, 13)): return shade((236, 240, 244))
    if frame: return shade((150, 157, 166), 12 if x == 1 or y == 1 else -10)
    # Brushed: faint horizontal streaks.
    return shade((196, 203, 210), (6 if y % 3 == 0 else -4 if y % 5 == 0 else 0) + grain('stainless_block', x, y, 1))


def titanium(x, y):
    edge = x in (0, 15) or y in (0, 15)
    seam = x in (7, 8) or y in (7, 8)
    if edge: return shade((70, 76, 88), -6 if x == 15 or y == 15 else 0)
    if seam: return shade((92, 98, 110) if x in (7, 8) and y in (7, 8) or (x + y) % 2 else (84, 90, 102))
    lx, ly = x % 8, y % 8
    light = 14 if lx == 1 or ly == 1 else -12 if lx == 6 or ly == 6 else 0
    return shade((132, 140, 154), light + grain('titanium_block', x, y, 2))


def steel(x, y):
    if x == 15 or y == 15: return shade((25, 27, 30))
    if x == 0 or y == 0: return shade((53, 56, 60))
    if x == 1 or y == 1: return shade((125, 130, 135))
    if x == 14 or y == 14: return shade((39, 42, 46))
    bolt = ((82, 126, 49), (112, 31, 86), (40, 76, 55))
    for cx, cy in ((3, 3), (12, 3), (3, 12), (12, 12)):
        if abs(x - cx) <= 1 and abs(y - cy) <= 1:
            return shade((bolt[y - cy + 1][x - cx + 1],) * 3)
    if x == 2 or y == 2: return shade((34, 37, 41))
    if x == 13 or y == 13: return shade((89, 94, 99))
    reflection = ((2, 3, 4, 2, 1, 1), (3, 5, 3, 2, 0, 1), (3, 3, 2, 0, 1, 2),
                  (2, 2, 0, 0, 2, 3), (1, 0, 0, 2, 3, 3), (1, 0, 1, 2, 3, 2))
    value = (48, 60, 72, 84, 96, 108)[reflection[(y - 2) // 2][(x - 2) // 2]]
    if (x, y) in ((5, 5), (6, 5), (9, 10), (10, 10)): value = 108
    return shade((value, value + 3, value + 7))


def textures(selected=None):
    for name, pixel in (('steel_block', steel), ('stainless_steel_block', stainless), ('titanium_block', titanium)):
        if selected and name not in selected: continue
        im = Image.new('RGBA', (16, 16)); p = im.load()
        for y in range(16):
            for x in range(16): p[x, y] = pixel(x, y)
        im.save(A / f'textures/block/{name}.png')


def tag(path, values):
    path = D.parent / path
    data = json.loads(path.read_text()) if path.exists() else {'replace': False, 'values': []}
    data['values'] = list(dict.fromkeys(data['values'] + values)); write(path, data)


def data(selected=None):
    for name, (metal, ingot, mass, _) in BLOCKS.items():
        if selected and name not in selected: continue
        write(A / f'models/block/{name}.json', {'parent': 'minecraft:block/cube_all', 'textures': {'all': NS + f'block/{name}'}})
        write(A / f'blockstates/{name}.json', {'variants': {'': {'model': NS + f'block/{name}'}}})
        write(A / f'models/item/{name}.json', {'parent': NS + f'block/{name}'})
        write(D / f'loot_table/blocks/{name}.json', {'type': 'minecraft:block', 'pools': [{'rolls': 1, 'entries': [{'type': 'minecraft:item', 'name': NS + name}],
                                                                                         'conditions': [{'condition': 'minecraft:survives_explosion'}]}]})
        write(D / f'recipe/{name}.json', {'type': 'minecraft:crafting_shaped', 'category': 'building', 'pattern': ['III', 'III', 'III'], 'key': {'I': {'tag': ingot}},
                                          'result': {'id': NS + name, 'count': 1}})
        ingot_id = NS + metal + '_ingot'
        write(D / f'recipe/{metal}_ingot_from_{name}.json', {'type': 'minecraft:crafting_shapeless', 'category': 'misc', 'ingredients': [{'item': NS + name}],
                                                             'result': {'id': ingot_id, 'count': 9}})
        for kind in ('block', 'item'):
            tag(f'c/tags/{kind}/storage_blocks/{metal}.json', [NS + name]); tag(f'c/tags/{kind}/storage_blocks.json', [f'#c:storage_blocks/{metal}'])
        # Sable reads this when installed; without it the folder is unused data.
        write(D / f'physics_block_properties/{name}.json', {'selector': NS + name, 'priority': 1001, 'properties': {'sable:mass': mass}})
    ids = [NS + name for name in BLOCKS if not selected or name in selected]
    tag('minecraft/tags/block/mineable/pickaxe.json', ids)
    tag('minecraft/tags/block/needs_iron_tool.json', ids)
    tag('minecraft/tags/block/beacon_base_blocks.json', ids)


def translations(selected=None):
    for language, index in (('en_us', 0), ('ja_jp', 1), ('zh_cn', 2)):
        path = A / 'lang' / f'{language}.json'; raw = path.read_bytes().decode('utf8'); nl = '\r\n' if '\r\n' in raw else '\n'; strings = json.loads(raw)
        for name, (*_, names) in BLOCKS.items():
            if not selected or name in selected: strings[f'block.create_refinery_n_reactors.{name}'] = names[index]
        path.write_bytes((json.dumps(strings, ensure_ascii=False, indent=2).replace('\n', nl) + nl).encode('utf8'))


if __name__ == '__main__':
    textures(); data(); translations()
    print('Generated the steel, stainless steel and titanium blocks')
