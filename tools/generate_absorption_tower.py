#!/usr/bin/env python3
"""The absorption tower in its two sizes: a one-block storey drawn as an octagon three blocks across, and a 3 x 3 octagonal storey with its
ring, both in Create's style: the lidded vessel's silver plate inside the rim of Create's andesite casing. Writes their models, side and
deck textures, block states, loot, tags, names in three languages and the Ponder structures;
tools/generate_crafting_recipes.py writes their recipes. Run from the repository root."""
import json
import math
from pathlib import Path
from PIL import Image
import generate_petrochemical_ponder as ponder
from electrolysis_hardware import LEFT, RIGHT, andesite_frame, silver_field

ROOT = Path(__file__).resolve().parents[1]
NS = 'create_refinery_n_reactors'
A = ROOT / 'src/main/resources/assets' / NS
D = ROOT / 'src/main/resources/data' / NS
TEX = f'{NS}:block/absorption_tower/'
# Flat-to-flat widths in pixels: the small storey is a regular octagon of 1.5 blocks' radius round its block's centre, the large one fills 3 x 3.
WIDTHS = {'large_absorption_tower': 48}


def write(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, indent=2, ensure_ascii=False) + '\n')


# The lidded vessel's silver deck (tools/generate_lidded_vessel.py, top_textures), rimmed in the colours of Create's andesite casing.
FIELD = ((112, 115, 114, 255), (122, 125, 123, 255), (132, 135, 133, 255), (143, 146, 143, 255))
DARK, EDGE, LIGHT, SHADE = (80, 83, 81, 255), (96, 99, 95, 255), (168, 179, 171, 255), (112, 117, 112, 255)
PATCHES = ('11212120', '12322111', '22101212', '11213221', '21122113', '12321221', '22111212', '11232121')


def deck(name, width):
    """A 48 px deck under the octagon: the vessel's plate inside a dark, lit rim along the eight edges, and a round manway in the middle."""
    image = Image.new('RGBA', (48, 48))
    r = width / 2
    for y in range(48):
        for x in range(48):
            dx, dz = x + .5 - 24, y + .5 - 24
            inset = min(r - abs(dx), r - abs(dz), (r * math.sqrt(2) - abs(dx) - abs(dz)) / math.sqrt(2))
            hole = math.hypot(dx, dz)
            colour = FIELD[int(PATCHES[(y // 2) % 8][(x // 2) % 8])]
            # The rotated corner pieces of the model also sample just outside the outline, so it stays plate there.
            if 0 <= inset < 1: colour = DARK
            elif 0 <= inset < 2: colour = LIGHT if dx + dz < 0 else SHADE
            elif 0 <= inset < 3 and (x + y) % 6 == 0: colour = EDGE
            if hole < 7.5: colour = DARK if hole > 6.5 else LIGHT if hole > 5.5 and dx + dz < 0 else EDGE if hole > 5.5 else FIELD[int(PATCHES[(y // 2) % 8][(x // 2) % 8])]
            if 5.5 < hole < 6.5 and round(math.degrees(math.atan2(dz, dx))) % 45 == 0: colour = DARK
            image.putpixel((x, y), colour)
    path = A / 'textures/block/absorption_tower' / (name + '_top.png')
    path.parent.mkdir(parents=True, exist_ok=True)
    image.save(path)


def octagon(width):
    """A regular octagon centred on the block with the given flat-to-flat width, one block high; its deck spans the 48 px top texture."""
    h, a = width / 2, round(width * (math.sqrt(2) - 1), 4)
    def span(t): return round((t + 16) / 3, 4)
    def face(texture, uv): return {'texture': '#' + texture, 'uv': uv}
    def plate(x0, z0, x1, z1): return [span(x0), span(z0), span(x1), span(z1)]
    side = face('side', [0, 0, 16, 16])
    result = [{'from': [8 - a/2, 0, 8 - h], 'to': [8 + a/2, 16, 8 + h], 'faces': {'north': side, 'south': side,
        'up': face('top', plate(8 - a/2, 8 - h, 8 + a/2, 8 + h)), 'down': face('top', plate(8 - a/2, 8 - h, 8 + a/2, 8 + h))}}]
    for x0, x1, outer in ((8 - h, 8 - a/2, 'west'), (8 + a/2, 8 + h, 'east')):
        result.append({'from': [x0, 0, 8 - a/2], 'to': [x1, 16, 8 + a/2], 'faces': {outer: side,
            'up': face('top', plate(x0, 8 - a/2, x1, 8 + a/2)), 'down': face('top', plate(x0, 8 - a/2, x1, 8 + a/2))}})
    for cx in (8 - a/2, 8 + a/2):
        for cz in (8 - a/2, 8 + a/2):
            result.append({'from': [cx - a/2, .01, cz - a/2], 'to': [cx + a/2, 15.99, cz + a/2],
                           'rotation': {'angle': 45, 'axis': 'y', 'origin': [cx, 0, cz]},
                           'faces': {**{d: side for d in ('north', 'south', 'east', 'west')},
                                     'up': face('top', plate(cx - a/2, cz - a/2, cx + a/2, cz + a/2)), 'down': face('top', plate(cx - a/2, cz - a/2, cx + a/2, cz + a/2))}})
    return result


DISPLAY = {
    'gui': {'rotation': [30, 225, 0], 'translation': [0, 0, 0], 'scale': [.3, .3, .3]},
    'ground': {'rotation': [0, 0, 0], 'translation': [0, 3, 0], 'scale': [.15, .15, .15]},
    'fixed': {'rotation': [0, 0, 0], 'translation': [0, 0, 0], 'scale': [.3, .3, .3]},
    'thirdperson_righthand': {'rotation': [75, 45, 0], 'translation': [0, 2.5, 0], 'scale': [.18, .18, .18]},
    'thirdperson_lefthand': {'rotation': [75, 45, 0], 'translation': [0, 2.5, 0], 'scale': [.18, .18, .18]},
    'firstperson_righthand': {'rotation': [0, 45, 0], 'translation': [0, 0, 0], 'scale': [.2, .2, .2]},
    'firstperson_lefthand': {'rotation': [0, 225, 0], 'translation': [0, 0, 0], 'scale': [.2, .2, .2]},
}


def side():
    """One storey's face: the grooved plate running on round the octagon, with the andesite rim at the joints between storeys."""
    path = A / 'textures/block/absorption_tower/side.png'
    path.parent.mkdir(parents=True, exist_ok=True)
    andesite_frame(silver_field(A), LEFT | RIGHT).save(path)


def assets():
    side()
    for name, width in WIDTHS.items():
        deck(name, width)
        # Faces reaching past the block take their occlusion from the neighbours, so the shell is lit flat.
        write(A / 'models/block/absorption_tower' / (name + '.json'), {'parent': 'minecraft:block/block', 'ambientocclusion': False,
            'textures': {'particle': TEX + 'side', 'side': TEX + 'side', 'top': TEX + name + '_top'}, 'elements': octagon(width)})
        write(A / 'blockstates' / (name + '.json'), {'variants': {'': {'model': f'{NS}:block/absorption_tower/{name}'}}})
        write(A / 'models/item' / (name + '.json'), {'parent': f'{NS}:block/absorption_tower/{name}', 'display': DISPLAY})
        write(D / 'loot_table/blocks' / (name + '.json'), {'type': 'minecraft:block', 'pools': [{'rolls': 1,
            'entries': [{'type': 'minecraft:item', 'name': f'{NS}:{name}'}], 'conditions': [{'condition': 'minecraft:survives_explosion'}]}]})

    # The ring around a large storey draws nothing and drops nothing; breaking it breaks the storey at its centre.
    write(A / 'models/block/absorption_tower/large_absorption_tower_ring.json', {'textures': {'particle': TEX + 'side'}})
    write(A / 'blockstates/large_absorption_tower_ring.json', {'variants': {f'facing={d}': {'model': f'{NS}:block/absorption_tower/large_absorption_tower_ring'} for d in ('north', 'east', 'south', 'west')}})

    tag = ROOT / 'src/main/resources/data/minecraft/tags/block/mineable/pickaxe.json'
    values = json.loads(tag.read_text())
    for name in (*WIDTHS, 'large_absorption_tower_ring'):
        if f'{NS}:{name}' not in values['values']: values['values'].append(f'{NS}:{name}')
    write(tag, values)


NAMES = {
    f'block.{NS}.large_absorption_tower': ('Large Absorption Tower Storey', '大型吸収塔の段', '大型吸收塔塔节'),
    f'block.{NS}.large_absorption_tower_ring': ('Large Absorption Tower Structure', '大型吸収塔構造部', '大型吸收塔结构'),
    f'item.{NS}.reactor_separator_core': ('Reaction Trays', '反応棚段', '反应塔板'),
    f'reactor.{NS}.status.column': ('Stack four to eight storeys of the same size', '同じ大きさの段を4～8段積んでください', '请堆叠4～8节相同尺寸的塔节'),
    f'jei.{NS}.absorption_tower': ('Absorption Tower', '吸収塔', '吸收塔'),
    f'jei.{NS}.note.absorption_tower': ('%s ticks, one batch per storey. Gas in at the bottom, water at the top', '%s tick、1段につき1バッチ。気体は下、水は上から', '%s tick，每节1批。气体从下、水从上进入'),
}
RETIRED = (f'jei.{NS}.nitric_absorption', f'jei.{NS}.note.nitric_absorption')
RETIRED_PREFIXES = (f'{NS}.ponder.shared.guide.absorption_trays.', f'{NS}.ponder.plant_absorption_trays.')


def names():
    for index, language in enumerate(('en_us', 'ja_jp', 'zh_cn')):
        path = A / 'lang' / (language + '.json')
        strings = json.loads(path.read_text())
        for key in list(strings):
            if key in RETIRED or key.startswith(RETIRED_PREFIXES): strings.pop(key)
        for key, text in NAMES.items(): strings[key] = text[index]
        path.write_text(json.dumps(strings, ensure_ascii=False, indent=2) + '\n')


def ring(scene, x, y, z):
    """A large storey at its centre, with the eight invisible ring cells pointing in towards it."""
    scene.block(x, y, z, f'{NS}:large_absorption_tower')
    for dx in (-1, 0, 1):
        for dz in (-1, 0, 1):
            if dx or dz:
                toward = ('west' if dx > 0 else 'east') if dx and dz else {(1, 0): 'west', (-1, 0): 'east', (0, 1): 'north', (0, -1): 'south'}[(dx, dz)]
                scene.block(x + dx, y, z + dz, f'{NS}:large_absorption_tower_ring', facing=toward)


def tank(scene, x, y, z):
    scene.block(x, y, z, 'create:fluid_tank', bottom='true', top='true', shape='window')


def structures():
    """Six small storeys, then four large ones: gas in and acid out at the bottom, water in and spent gas out at the top."""
    ponder.OUT = A / 'ponder/plant'
    for name, storeys, reach in (('large_absorption_tower', 4, 2),):
        s = ponder.Structure(11, storeys + 4)
        for y in range(1, storeys + 1):
            if reach == 2: ring(s, 5, y, 5)
        top = storeys
        s.pipe(5 + reach, 1, 5, 'east', 'west');s.pump(6 + reach, 1, 5, 'west');tank(s, 7 + reach, 1, 5)
        s.pipe(5 - reach, 1, 5, 'east', 'west');s.pump(4 - reach, 1, 5, 'west');tank(s, 3 - reach, 1, 5)
        s.pipe(5 + reach, top, 5, 'east', 'west');s.pump(6 + reach, top, 5, 'east');tank(s, 7 + reach, top, 5)
        s.block(5, top + 1, 5, 'create:fluid_pipe', up='true', down='true', north='false', south='false', east='false', west='false', waterlogged='false')
        s.pump(5, top + 2, 5, 'down')
        tank(s, 5, top + 3, 5)
        s.save(name)


if __name__ == '__main__':
    assets(); names(); structures()
    print('Generated both absorption tower sizes: models, decks, names and Ponder structures')
