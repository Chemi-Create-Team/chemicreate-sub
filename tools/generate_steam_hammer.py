#!/usr/bin/env python3
"""Native model, pixel art, crafting data and Ponder structure for the steam hammer."""

from PIL import Image, ImageDraw

from generate_portable_sprayers import A, D, NS, ALLOY, BRASS, COPPER, RED, RUBBER, STEEL, box, octagon, sheet, write
from generate_steam_gun import WOOD, sprites as gauge_sprites


def models():
    def sprites(fill):
        gauge_sprites(fill)
        fill(32, 44, 40, 50, '#372425')
        fill(33, 45, 39, 49, '#793b2d')
        for x in (34, 36, 38):
            fill(x, 45, x, 48, '#e38042')
            fill(x, 46, x, 47, '#f5ca7c')

    cells = sheet('steam_hammer', {'steel': STEEL, 'brass': BRASS, 'copper': COPPER, 'wood': WOOD,
        'alloy': ALLOY, 'dark': RUBBER, 'red': RED}, sprites, A / 'textures/item')
    parts = []

    def part(label, start, end, metal, decals=None, **kwargs):
        element = box(cells, label, start, end, metal, **kwargs)
        for face, (x, y, width, height) in (decals or {}).items():
            element['faces'][face]['uv'] = [value / 4 for value in (x, y, x + width, y + height)]
        parts.append(element)

    for low, high, radius, metal in (
        (-8.8, -7.7, 4.2, 'dark'), (-7.7, -6.25, 4.85, 'steel'), (-6.25, -5.2, 5.0, 'brass'),
        (-5.2, -2.0, 4.75, 'steel'), (-2.0, -1.15, 4.55, 'brass'),
        (-1.15, 1.0, 4.1, 'copper'), (5.35, 6.1, 4.0, 'copper'), (6.1, 7.2, 4.85, 'brass'),
        (7.2, 12.9, 4.65, 'brass'), (12.9, 14.0, 4.85, 'brass'), (14.0, 15.3, 4.05, 'copper'),
        (15.3, 15.8, 3.55, 'steel'),
    ):
        parts.extend(octagon(cells, 'hammer head', 'z', (8, 19), radius, low, high, metal, caps=('north', 'south')))
    part('piston enclosure', (4.4, 15.45, 1.0), (11.6, 22.55, 5.45), 'dark')
    for x in (3.86, 11.50):
        for y in (15.05, 21.75):
            part('piston rail', (x, y, .6), (x + .68, y + 1.2, 5.72), 'brass')
        part('silver ram', (x + .11, 17.7, 1.02), (x + .52, 20.1, 3.4), 'alloy')
        part('ram sleeve', (x + .13, 17.4, 3.4), (x + .57, 20.4, 5.25), 'steel')
    for x, face in ((3.24, 'west'), (12.76, 'east')):
        part('boiler window', (x - .09, 16.65, 7.4), (x + .09, 21.35, 12.65), 'copper', decals={face: (32, 44, 9, 7)})
    for y, z, radius in ((15.8, 2.75, 2.55), (17.75, 5.7, 1.65)):
        part('gear hub', (12.18, y - .59, z - .59), (13.38, y + .59, z + .59), 'steel')
        part('brass gear', (13.42, y - radius, z - radius), (13.42, y + radius, z + radius), 'brass',
            sides=('east', 'west'), decals={'east': (8, 32, 9, 9), 'west': (8, 32, 9, 9)})
    part('gauge housing', (12.4, 21, 5.05), (13.37, 23.45, 7.5), 'brass')
    part('pressure dial', (13.39, 21.06, 5.11), (13.39, 23.39, 7.44), 'brass', sides=('east',), decals={'east': (0, 32, 5, 5)})
    parts.extend(octagon(cells, 'copper accumulator', 'z', (13.45, 22.1), 1.05, 8, 12.45, 'copper', caps=('north', 'south')))
    for y, z, angle in ((21, 12, 22.5), (17.8, 13, 22.5)):
        rotation = {'origin': [10.4, y, z], 'axis': 'y', 'angle': angle}
        part('steam exhaust', (10.2, y - .5, z - .55), (16.8, y + .5, z + .55), 'copper', rotation=rotation)
        part('exhaust ring', (16.1, y - .76, z - .76), (17.02, y + .76, z + .76), 'alloy', rotation=rotation)
        part('exhaust opening', (17.035, y - .43, z - .43), (17.045, y + .43, z + .43), 'dark', rotation=rotation)

    part('handle socket', (5.7, 12.85, 1.7), (10.3, 15.3, 6.6), 'brass')
    part('wooden haft', (6.8, -8.7, 2.65), (9.2, 13.1, 5.65), 'wood')
    part('handle steam line', (9.19, -8.25, 4.94), (9.84, 13.35, 5.5), 'copper')
    for y in (-8.2, -6.9, -5.6, -4.3, -3.0, -1.7):
        part('wrapped grip', (6.55, y, 2.45), (9.5, y + 1.17, 5.85), 'wood')
    part('top grip collar', (6.47, -.5, 2.35), (9.6, .18, 5.95), 'brass')
    part('pommel plate', (6.3, -10.8, 2.25), (9.7, -8.52, 6.08), 'steel')
    part('pommel valve hub', (9.35, -9.73, 3.66), (10.28, -8.63, 4.73), 'brass')
    part('pommel valve', (10.30, -10.5, 2.5), (10.30, -7.55, 5.45), 'red', sides=('east', 'west'),
        decals={'east': (22, 32, 7, 7), 'west': (22, 32, 7, 7)})

    def held(rotation, translation, scale):
        return {'rotation': rotation, 'translation': translation, 'scale': [scale] * 3}

    first_person = held([0, 0, 0], [0, -1.5, 0], .57)
    third_person = held([0, -90, 0], [0, 1, 0], .53)
    guard = held([25, -70, 45], [1, 1, -2], .53)
    common = {'gui': held([22, -36, -38], [-.25, .65, 0], .46),
        'ground': held([85, 0, -29], [0, 3.5, 0], .38), 'fixed': held([14, -22, -36], [0, .5, 0], .41),
        'firstperson_righthand': first_person, 'firstperson_lefthand': first_person}
    texture = NS + ':item/steam_hammer'
    models_path = A / 'models/item'
    write(models_path / 'steam_hammer/guard.json', {'parent': NS + ':item/steam_hammer',
        'display': {**common, 'thirdperson_righthand': guard, 'thirdperson_lefthand': guard}, 'overrides': []})
    write(models_path / 'steam_hammer.json', {'parent': 'minecraft:block/block', 'textures': {'sheet': texture, 'particle': texture},
        'display': {**common, 'thirdperson_righthand': third_person, 'thirdperson_lefthand': third_person}, 'elements': parts,
        'overrides': [{'predicate': {NS + ':steam_hammer_guard': 1}, 'model': NS + ':item/steam_hammer/guard'}]})


def effect_icon():
    icon = Image.new('RGBA', (18, 18))
    draw = ImageDraw.Draw(icon)
    draw.arc((1, 3, 16, 16), 28, 340, fill='#896b36', width=2)
    draw.arc((2, 4, 15, 15), 28, 315, fill='#ecd48d')
    for x, y, size in ((5, 5, 4), (12, 9, 3), (7, 13, 2)):
        draw.polygon(((x, y - size), (x + 1, y - 1), (x + size, y), (x + 1, y + 1),
            (x, y + size), (x - 1, y + 1), (x - size, y), (x - 1, y - 1)), fill='#d49a3c', outline='#70502f')
        draw.point((x, y), fill='#ffeea5')
    icon.save(A / 'textures/mob_effect/dazed.png')


def recipe():
    write(D / 'recipe/steam_hammer.json', {'type': 'create:mechanical_crafting',
        'pattern': [' SBS ', ' PGAP', '  M  ', '  W  ', '  L  '],
        'key': {'A': {'item': NS + ':gas_receiver'}, 'P': {'item': 'create:fluid_pipe'},
            'S': {'item': NS + ':stainless_steel_sheet'}, 'B': {'item': 'create:brass_casing'},
            'G': {'item': 'create:cogwheel'}, 'M': {'item': 'create:precision_mechanism'},
            'W': {'tag': 'minecraft:planks'}, 'L': {'item': 'minecraft:leather'}},
        'result': {'id': NS + ':steam_hammer', 'count': 1}, 'accept_mirrored': True})


def ponder():
    from generate_petrochemical_ponder import OUT, Structure
    scene = Structure(7, 5)
    scene.block(2, 1, 2, NS + ':gas_receiver', axis='y', bottom='true', top='true', shape='plain')
    scene.block(4, 1, 3, 'create:depot', waterlogged='false')
    scene.block(4, 1, 5, 'minecraft:hay_block', axis='y')
    scene.block(4, 2, 5, 'minecraft:target', power='0')
    scene.save('steam_hammer')
    (OUT / 'steam_hammer.nbt').replace(A / 'ponder/plant/steam_hammer.nbt')


if __name__ == '__main__':
    models()
    effect_icon()
    recipe()
    ponder()
