#!/usr/bin/env python3
"""Build the Steam Gun's native item models and pixel texture."""

from math import atan2, hypot, pi

from generate_portable_sprayers import A, D, NS, ALLOY, BRASS, COPPER, RED, RUBBER, STEEL, box, octagon, sheet, write

WOOD = ('#936351', '#6d4638', '#452d28', '#312325')
BLADE = ('#ede6dc', '#bcc4c5', '#8c9b9f', '#68787c')
TRANSPARENT = (0, 0, 0, 0)
SIDES = ('east', 'west', 'up', 'down')


def sprites(fill):
    for y in range(5):
        for x in range(5):
            rim = max(abs(x - 2), abs(y - 2)) == 2
            colour = ('#262728' if abs(x - 2) + abs(y - 2) == 4 else BRASS[3]) if rim else '#e8dbbd'
            if (x, y) in ((2, 2), (3, 1)):
                colour = RED[2]
            fill(x, 32 + y, x, 32 + y, colour)

    for offset, size, colours in ((8, 9, BRASS), (22, 7, RED)):
        centre = size // 2
        for y in range(size):
            for x in range(size):
                dx, dy = x - centre, y - centre
                radius = hypot(dx, dy)
                tooth = abs((atan2(dy, dx) / pi * 4 + .5) % 1 - .5) < .24
                spoke = min(abs(dx), abs(dy), abs(abs(dx) - abs(dy))) == 0
                colour = TRANSPARENT
                if radius <= 1.4 or 1.8 <= radius <= centre + .4 and (spoke or radius >= centre - 1.4):
                    colour = colours[0] if dx + dy < -1 else colours[1] if dx + dy < 2 else colours[2]
                if tooth and centre + .4 < radius <= centre + .9:
                    colour = colours[1] if dx + dy < 0 else colours[3]
                fill(offset + x, 32 + y, offset + x, 32 + y, colour)

    # The sheet has a separate strip for the lit window and the blade's bevel.
    fill(32, 32, 40, 34, BRASS[3]);fill(33, 33, 39, 33, '#f7c179')
    fill(32, 38, 45, 39, BLADE[2]);fill(32, 38, 45, 38, BLADE[0])


def models():
    cells = sheet('steam_gun', {'steel': STEEL, 'brass': BRASS, 'copper': COPPER, 'wood': WOOD,
        'alloy': ALLOY, 'blade': BLADE, 'dark': RUBBER, 'red': RED}, sprites, A / 'textures/item')
    parts = []

    def part(label, start, end, metal, decals=None, **kwargs):
        element = box(cells, label, start, end, metal, **kwargs)
        for face, (x, y, width, height) in (decals or {}).items():
            element['faces'][face]['uv'] = [value / 4 for value in (x, y, x + width, y + height)]
        parts.append(element)

    for label, start, end in (
        ('barrel roof', (6.25, 11.15, -12.4), (9.75, 11.8, 3)),
        ('barrel floor', (6.25, 8.25, -12.4), (9.75, 8.9, 3)),
        ('left barrel wall', (6.25, 8.9, -12.4), (6.85, 11.15, 3)),
        ('right barrel wall', (9.15, 8.9, -12.4), (9.75, 11.15, 3)),
    ):
        part(label, start, end, 'steel')
    part('muzzle recess', (6.85, 8.9, -11.25), (9.15, 11.15, -11.0), 'dark')
    for low in (-11.95, -9.45):
        for side, start, end in (
            ('top', (5.95, 11.8, low), (10.05, 12.25, low + .65)),
            ('bottom', (5.95, 7.8, low), (10.05, 8.25, low + .65)),
            ('left', (5.95, 8.25, low), (6.25, 11.8, low + .65)),
            ('right', (9.75, 8.25, low), (10.05, 11.8, low + .65)),
        ):
            part('brass collar ' + side, start, end, 'brass')
    part('front sight', (7.75, 12.2, -10.6), (8.25, 13.5, -9.9), 'brass')
    part('front sight bead', (7.65, 13.45, -10.65), (8.35, 13.8, -9.95), 'blade')
    for x in (5.8, 9.8):
        part('copper return line', (x, 9.3, -6.1), (x + .4, 9.7, 5.5), 'copper')
        for z in (-5.8, -1.55, 1.2):
            part('pipe clip', (x - .12, 9.0, z), (x + .52, 10.0, z + .4), 'brass')

    parts.extend(octagon(cells, 'copper steam chamber', 'z', (8, 10), 3.05, 3, 14.45, 'copper', caps=('north', 'south')))
    for low, high, radius in ((2.35, 3.25, 3.2), (3.15, 3.9, 3.45), (8.9, 9.4, 3.2), (13.6, 14.45, 3.35), (14.45, 15.15, 3.0)):
        parts.extend(octagon(cells, 'chamber rim', 'z', (8, 10), radius, low, high, 'brass', caps=('north', 'south')))
    for x in (4.88, 11.12):
        face = 'west' if x < 8 else 'east'
        part('chamber window', (x - .03, 10.1, 4.0), (x + .03, 11.3, 8.3), 'brass', decals={face: (32, 32, 9, 3)})
    for x in (5.6, 10.4):
        part('rear sight rail', (x - .35, 12.4, 13.8), (x + .35, 12.9, 15.8), 'steel')
    part('rear sight notch left', (6.85, 13.25, 14), (7.45, 14.4, 14.7), 'brass')
    part('rear sight notch right', (8.55, 13.25, 14), (9.15, 14.4, 14.7), 'brass')
    for z, tip in ((5.2, 15.4), (9.7, 16.3), (11.5, 15.8)):
        part('vent flange', (7.05, 12.75, z - .65), (8.95, 13.4, z + .65), 'brass')
        part('steam vent', (7.5, 13.3, z - .28), (8.5, tip, z + .28), 'copper')
        part('vent lip', (7.35, tip - .3, z - .4), (8.65, tip, z + .4), 'alloy')

    for face, x, sign in (('east', 11.05, 1), ('west', 4.95, -1)):
        cap = x + sign * .55
        part('gauge housing', (min(x, cap), 9.55, 4.1), (max(x, cap), 12.05, 6.6), 'brass')
        part('ivory gauge dial', (cap, 9.62, 4.17), (cap, 11.97, 6.52), 'brass', sides=(face,), decals={face: (0, 32, 5, 5)})
    part('regulator bearing', (10.9, 8.2, 8.1), (11.95, 9.9, 9.8), 'steel')
    part('large brass gear', (12.03, 6.35, 6.65), (12.03, 11.35, 11.65), 'brass', sides=('east', 'west'),
         decals={'east': (8, 32, 9, 9), 'west': (8, 32, 9, 9)})
    part('red handwheel hub', (10.1, 7.1, 14.7), (11.4, 8.15, 15.75), 'steel')
    part('red handwheel', (11.45, 5.85, 13.45), (11.45, 9.35, 16.95), 'red', sides=('east', 'west'),
         decals={'east': (22, 32, 7, 7), 'west': (22, 32, 7, 7)})

    part('lower receiver', (6.4, 6.0, -2.6), (9.6, 8.3, 17.6), 'steel')
    part('fore-end wood', (6.55, 6.1, -6.65), (9.45, 7.85, 1.4), 'wood')
    for z in (-6.2, -5.2, -4.2, -3.2, -2.2, -1.2):
        part('foregrip rib', (6.42, 5.98, z), (9.58, 7.9, z + .22), 'wood')
    part('stock', (6.85, 7.2, 15.1), (9.15, 10.45, 27.25), 'wood')
    part('stock heel', (6.6, 6.4, 26.2), (9.4, 10.8, 27.6), 'steel')
    part('grip', (6.9, 1.65, 12.75), (9.1, 6.35, 15.1), 'wood',
         rotation={'origin': [8, 6.35, 14], 'axis': 'x', 'angle': -22.5})
    part('grip heel', (6.8, 1.45, 14.65), (9.2, 2.0, 17.1), 'brass')
    part('trigger guard floor', (7.25, 3.65, 8.85), (8.75, 4.15, 12.6), 'brass')
    part('trigger guard front', (7.25, 4.15, 8.85), (8.75, 6.1, 9.38), 'brass')
    part('trigger', (7.7, 4.72, 10.4), (8.3, 6.2, 11), 'steel')
    part('stock copper pipe', (7.7, 10.2, 25.3), (8.3, 13.7, 26.05), 'copper',
         rotation={'origin': [8, 10.2, 25.7], 'axis': 'x', 'angle': 22.5})

    part('bayonet clasp', (6.5, 5.65, -8.2), (9.5, 8.2, -7.45), 'steel')
    part('bayonet guard', (6.85, 4.35, -8.05), (9.15, 6.35, -7.35), 'brass')
    part('bayonet blade', (7.73, 4.45, -14.3), (8.27, 6.0, -8.1), 'blade',
         sprites={'east': (32, 38), 'west': (32, 38)})
    part('bayonet tip', (7.73, 5.0, -15.6), (8.27, 5.85, -14.3), 'blade')
    part('bayonet point', (7.8, 5.27, -15.95), (8.2, 5.67, -15.6), 'blade')

    def held(rotation, translation, scale):
        return {'rotation': rotation, 'translation': translation, 'scale': [scale] * 3}

    first_person = held([0, 0, 0], [0, 0, 0], .61)
    lowered = held([90, 0, 0], [0, 3, 3], .56)
    raised = held([0, -15, 0], [0, 0, -3], .56)
    common = {'gui': held([24, -70, 20], [0, 0, 0], .41),
        'ground': held([0, -60, 10], [0, 3, 0], .37), 'fixed': held([0, -90, 0], [0, 0, 0], .38),
        'firstperson_righthand': first_person, 'firstperson_lefthand': first_person}
    item_model = {'parent': 'minecraft:block/block', 'textures': {'sheet': NS + ':item/steam_gun', 'particle': NS + ':item/steam_gun'},
        'elements': parts}
    models_path = A / 'models/item'
    active_display = {**common, 'thirdperson_righthand': raised, 'thirdperson_lefthand': raised}
    write(models_path / 'steam_gun_raised.json', {'parent': NS + ':item/steam_gun', 'display': active_display, 'overrides': []})
    write(models_path / 'steam_gun.json', {**item_model, 'display': {**common, 'thirdperson_righthand': lowered, 'thirdperson_lefthand': lowered},
        'overrides': [{'predicate': {NS + ':steam_gun_raised': 1}, 'model': NS + ':item/steam_gun_raised'}]})


def recipe():
    write(D / 'recipe/steam_gun.json', {'type': 'create:mechanical_crafting',
        'pattern': ['  A  ', 'PSBGP', '  MI ', '  W  '],
        'key': {'A': {'item': NS + ':gas_receiver'}, 'P': {'item': 'create:fluid_pipe'},
            'S': {'item': NS + ':stainless_steel_sheet'}, 'B': {'item': 'create:brass_casing'},
            'G': {'item': 'create:cogwheel'}, 'M': {'item': 'create:precision_mechanism'},
            'I': {'item': 'minecraft:iron_sword'}, 'W': {'tag': 'minecraft:planks'}},
        'result': {'id': NS + ':steam_gun', 'count': 1}, 'accept_mirrored': True})


def ponder():
    from generate_petrochemical_ponder import OUT, Structure
    scene = Structure(7, 5)
    scene.block(2, 1, 2, NS + ':gas_receiver', axis='y', bottom='true', top='true', shape='plain')
    scene.block(4, 1, 3, 'create:depot', waterlogged='false')
    scene.block(4, 1, 5, 'minecraft:target', power='0')
    scene.block(4, 2, 5, 'minecraft:target', power='0')
    scene.save('steam_gun')
    (OUT / 'steam_gun.nbt').replace(A / 'ponder/plant/steam_gun.nbt')


if __name__ == '__main__':
    models()
    recipe()
    ponder()
