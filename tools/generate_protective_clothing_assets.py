#!/usr/bin/env python3
"""Worn layers, item icons, models, recipes and armor tags of the reactor hall workers' clothing."""
import json
from pathlib import Path
from PIL import Image

ROOT = Path(__file__).resolve().parents[1] / 'src/main/resources'
ASSETS = ROOT / 'assets/create_refinery_n_reactors'
DATA = ROOT / 'data/create_refinery_n_reactors/recipe'
TAGS = ROOT / 'data/minecraft/tags/item'
NS = 'create_refinery_n_reactors:'

COLORS = {
    '.': (0, 0, 0, 0), 'W': (238, 238, 230, 255), 'F': (216, 218, 210, 255), 'S': (178, 182, 176, 255),
    'O': (92, 96, 98, 255), 'H': (158, 184, 206, 255), 'T': (38, 46, 84, 255), 't': (92, 62, 58, 255),
    'B': (34, 36, 40, 255), 'G': (112, 196, 132, 255), 'N': (38, 44, 72, 255), 'n': (24, 28, 48, 255),
    'L': (106, 164, 216, 255), 'l': (72, 122, 176, 255), 'D': (46, 84, 130, 255),
}


def fill(image, x0, y0, x1, y1, color):
    for y in range(y0, y1):
        for x in range(x0, x1):
            image.putpixel((x, y), COLORS[color])


def rows(image, x, y, lines):
    for j, line in enumerate(lines):
        for i, color in enumerate(line):
            image.putpixel((x + i, y + j), COLORS[color])


def limb_folds(image, x0, length=12, y0=20):
    """Soft folds round a 4 x 4 limb whose side faces start at x0, darker towards the back."""
    for x in (x0 + 3, x0 + 8):
        fill(image, x, y0, x + 1, y0 + length, 'F')
    fill(image, x0 + 13, y0, x0 + 14, y0 + length, 'F')


def layer_1():
    image = Image.new('RGBA', (64, 32), COLORS['.'])
    # The cap: one thin pleated cloth on the hat layer, down to the forehead. ProtectiveCapModel draws it close to the head.
    fill(image, 40, 0, 48, 8, 'W')
    fill(image, 32, 8, 64, 12, 'W')
    for x in range(34, 64, 4):
        fill(image, x, 8, x + 1, 11, 'F')
    fill(image, 32, 11, 64, 12, 'F')
    # The coat, over a shirt and tie, with a badge on the left breast pocket.
    fill(image, 20, 16, 28, 20, 'W')
    fill(image, 28, 16, 36, 20, 'F')
    fill(image, 16, 20, 40, 32, 'W')
    fill(image, 19, 20, 20, 32, 'F')
    fill(image, 28, 20, 29, 32, 'F')
    fill(image, 35, 20, 36, 32, 'S')
    rows(image, 20, 20, (
        'WSHTTHSW',
        'WSHTTHSW',
        'WWSTTSWW',
        'WWWSTWWW',
        'WWWSWBBW',
        'WWWSWBGW',
        'WWWSWSSW',
        'WWWSWWWW',
        'WWWSWWWW',
        'WSSSWSSW',
        'WFFSWFFW',
        'WWWSWWWW',
    ))
    fill(image, 44, 16, 48, 20, 'W')
    fill(image, 48, 16, 52, 20, 'S')
    fill(image, 40, 20, 56, 32, 'W')
    limb_folds(image, 40)
    fill(image, 40, 30, 56, 31, 'S')
    # The shoe covers, gathered at the top by an elastic hem.
    fill(image, 8, 16, 12, 20, 'D')
    fill(image, 0, 29, 16, 32, 'L')
    fill(image, 0, 29, 16, 30, 'l')
    return image


def layer_2():
    image = Image.new('RGBA', (64, 32), COLORS['.'])
    # The waist and the skirt of the coat, over dark trousers.
    fill(image, 16, 20, 40, 32, 'W')
    fill(image, 23, 20, 24, 32, 'S')
    fill(image, 35, 20, 36, 32, 'F')
    fill(image, 8, 16, 12, 20, 'n')
    fill(image, 0, 20, 16, 25, 'W')
    fill(image, 0, 24, 16, 25, 'S')
    fill(image, 7, 20, 8, 24, 'S')
    fill(image, 3, 20, 4, 24, 'F')
    fill(image, 12, 20, 13, 24, 'F')
    fill(image, 0, 25, 16, 32, 'N')
    fill(image, 3, 25, 4, 32, 'n')
    fill(image, 11, 25, 12, 32, 'n')
    return image


ICONS = {
    'protective_cap': (
        '................',
        '................',
        '................',
        '....OOOOOOOO....',
        '...OWWWWWWWWO...',
        '..OWWFWWFWWFWO..',
        '..OWWFWWFWWFWO..',
        '..OWWFWWFWWFWO..',
        '..OSSSSSSSSSSO..',
        '..OWWWWWWWWWWO..',
        '..OFFFFFFFFFFO..',
        '...OOOOOOOOOO...',
        '................',
        '................',
        '................',
        '................',
    ),
    'protective_coat': (
        '................',
        '...OOOHHHHOOO...',
        '..OWWSHTTHSWWO..',
        '.OWWWWSTTSWWWWO.',
        '.OWFOWWTTWWOFWO.',
        '.OWFOWWSWBBOFWO.',
        '.OWFOWWSWBGOFWO.',
        '.OOOOWWSWWWOOOO.',
        '....OWWSWWWO....',
        '....OWWSWWWO....',
        '....OSSSWSSO....',
        '....OWWSWWWO....',
        '....OWWSWWWO....',
        '....OWWSWWWO....',
        '....OOOOOOOO....',
        '................',
    ),
    'protective_trousers': (
        '................',
        '...OOOOOOOOOO...',
        '...OWWWWWWWWO...',
        '...OWWWSSWWWO...',
        '...OWWWSSWWWO...',
        '...OWWWOOWWWO...',
        '...OWWWOOWWWO...',
        '...OSSSOOSSSO...',
        '...ONNNOONNNO...',
        '...ONNnOOnNNO...',
        '...ONNnOOnNNO...',
        '...ONNnOOnNNO...',
        '...ONNnOOnNNO...',
        '...ONNNOONNNO...',
        '...OOOOOOOOOO...',
        '................',
    ),
    'protective_shoe_covers': (
        '................',
        '................',
        '................',
        '................',
        '................',
        '..OOOO....OOOO..',
        '..OllO....OllO..',
        '..OLLO....OLLO..',
        '..OLLO....OLLO..',
        '..OLLO....OLLO..',
        'OOOLLO..OOOLLO..',
        'OLLLLO..OLLLLO..',
        'OLLLLO..OLLLLO..',
        'ODDDDO..ODDDDO..',
        'OOOOOO..OOOOOO..',
        '................',
    ),
}

WOOL, PLASTIC = {'item': 'minecraft:white_wool'}, {'item': NS + 'plastic_sheet'}
RECIPES = {
    'protective_cap': (['WWW', 'WPW'], {'W': WOOL, 'P': PLASTIC}),
    'protective_coat': (['W W', 'WTW', 'WPW'], {'W': WOOL, 'P': PLASTIC, 'T': {'item': 'minecraft:blue_dye'}}),
    'protective_trousers': (['WPW', 'W W', 'N N'], {'W': WOOL, 'P': PLASTIC, 'N': {'item': 'minecraft:blue_wool'}}),
    'protective_shoe_covers': (['D D', 'P P'], {'P': PLASTIC, 'D': {'item': 'minecraft:light_blue_dye'}}),
}
ARMOR_TAGS = {'head_armor': 'protective_cap', 'chest_armor': 'protective_coat', 'leg_armor': 'protective_trousers', 'foot_armor': 'protective_shoe_covers'}


def write_json(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, indent=2) + '\n', encoding='utf-8', newline='\n')


def main():
    armor = ASSETS / 'textures/models/armor'
    armor.mkdir(parents=True, exist_ok=True)
    layer_1().save(armor / 'protective_clothing_layer_1.png')
    layer_2().save(armor / 'protective_clothing_layer_2.png')
    for name, lines in ICONS.items():
        assert len(lines) == 16 and all(len(line) == 16 for line in lines), name
        icon = Image.new('RGBA', (16, 16))
        rows(icon, 0, 0, lines)
        icon.save(ASSETS / 'textures/item' / (name + '.png'))
        write_json(ASSETS / 'models/item' / (name + '.json'), {'parent': 'minecraft:item/generated', 'textures': {'layer0': NS + 'item/' + name}})
    for name, (pattern, key) in RECIPES.items():
        write_json(DATA / (name + '.json'), {'type': 'minecraft:crafting_shaped', 'pattern': pattern, 'key': key, 'result': {'id': NS + name, 'count': 1}})
    for tag, name in ARMOR_TAGS.items():
        write_json(TAGS / (tag + '.json'), {'replace': False, 'values': [NS + name]})


if __name__ == '__main__':
    main()
