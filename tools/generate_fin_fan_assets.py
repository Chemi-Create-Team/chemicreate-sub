#!/usr/bin/env python3
"""Fin-fan bank: cooler radiator, large cooling fan and fan shroud textures and models.

Formed radiators show the fin pack on one long side only. The other long side is a
brass-framed slate panel, and the ends are riveted iron. Each side panel spans one
fan section (3x3): FinFanModels picks the tile from the *_connected 48 px sheets,
and the 16 px originals are the single-block form of the same panel. The shroud deck
tiles are cut from one 48 px sheet, so the 3x3 top reads as one fan box.
"""
from pathlib import Path
import json, math
from PIL import Image
from minecraft_style import grain

ROOT = Path(__file__).resolve().parents[1]
A = ROOT / 'src/main/resources/assets/create_refinery_n_reactors'
T = A / 'textures/block/chemical'
# Connected panel sheets are 48 px, so they live apart from the 16 px chemical textures, like block/tank.
P = A / 'textures/block/fin_fan'
NS = 'create_refinery_n_reactors:'
C = NS + 'block/chemical/'
F = NS + 'block/fin_fan/'
# Fan box steel, sampled from the fin-fan reference render.
STEEL = dict(shadow=(24, 21, 17), dark=(40, 37, 33), base=(64, 60, 55), mid=(80, 76, 70), hi=(96, 92, 86), edge=(122, 118, 112))
BRASS = dict(shadow=(74, 56, 22), dark=(104, 82, 36), base=(150, 122, 58), hi=(196, 168, 96))
# Radiator panels, sampled from the two block references: copper and brass frame over slate, and riveted iron.
COPPER = dict(dark=(68, 43, 29), base=(95, 63, 43), mid=(126, 84, 57), hi=(158, 105, 71))
GOLD = dict(shadow=(124, 96, 54), dark=(165, 128, 72), base=(197, 162, 86), hi=(247, 203, 108), glint=(255, 235, 140))
SLATE = dict(line=(18, 20, 23), dark=(29, 32, 34), base=(38, 43, 46), mid=(48, 54, 57), hi=(53, 59, 62))
IRON = dict(line=(21, 21, 22), dark=(35, 35, 35), base=(44, 44, 44), mid=(57, 56, 56), rim=(78, 78, 78), rivet=(150, 149, 149), rivet_dark=(102, 96, 96))
FIN = dict(gap=SLATE['line'], face=(48, 54, 57), lit=(76, 83, 87))
BLADE = dict(dark=(92, 87, 83), base=(128, 122, 117), hi=(158, 152, 146))
THROAT = (19, 18, 13)
FLAT = (THROAT, SLATE['line'], IRON['line'])
FAN_RADIUS = 17.5
PITCH = 22.5
SIDES = ('north', 'south', 'east', 'west', 'up', 'down')


def draw(name, size, pixel, roughness=1):
    im = Image.new('RGBA', size)
    for y in range(size[1]):
        for x in range(size[0]):
            c = pixel(x, y)
            if c is None:
                continue
            n = grain(name, x, y, roughness) if c not in FLAT else 0
            im.putpixel((x, y), tuple(max(0, min(255, v + n)) for v in c) + (255,))
    return im


def save(name, pixel, roughness=1):
    draw(name, (16, 16), pixel, roughness).save(T / f'{name}.png')


def write(path, data):
    text = json.dumps(data, ensure_ascii=False, indent=2) + '\n'
    path.write_text(text.replace('\n', '\r\n'), encoding='utf-8', newline='')


def edge_of(x, y, size):
    """Distance to the panel edge, and whether the pixel is on the lit (top or left) half of the frame."""
    edge = min(x, y, size - 1 - x, size - 1 - y)
    return edge, min(x, y) <= min(size - 1 - x, size - 1 - y)


def framed(size, inside):
    """First block reference: copper rim, bevelled brass frame, then a sunken slate field."""
    def pixel(x, y):
        edge, lit = edge_of(x, y, size)
        along = x if edge in (y, size - 1 - y) else y
        if edge == 0:
            return COPPER['dark'] if x in (0, size - 1) and y in (0, size - 1) else COPPER['base']
        if edge == 1:
            return COPPER['hi'] if lit else COPPER['mid']
        if edge == 2:
            return GOLD['glint'] if lit and along % 13 == 5 else GOLD['hi'] if lit else GOLD['dark']
        if edge == 3:
            return GOLD['base'] if lit else GOLD['shadow']
        if edge == 4:
            return SLATE['line']
        if edge == 5:
            return SLATE['dark'] if lit else SLATE['hi']
        return inside(x, y)
    return pixel


def slate(x, y):
    return SLATE['base']


def fins(x, y):
    """Round fin tubes, four per block, under the frame's shadow."""
    if x % 4 == 0:
        return FIN['gap']
    return (FIN['lit'], FIN['face'], SLATE['dark'])[x % 4 - 1]


def riveted(size):
    """Second block reference: dark iron with a raised rim and corner rivets."""
    rivets = {1, size - 3} | ({size // 2 - 1} if size > 16 else set())
    def pixel(x, y):
        edge, lit = edge_of(x, y, size)
        for rx in rivets:
            for ry in rivets:
                if (rx in (1, size - 3) or ry in (1, size - 3)) and 0 <= x - rx < 2 and 0 <= y - ry < 2:
                    return IRON['rivet'] if (x - rx, y - ry) == (0, 0) else IRON['rivet_dark']
        if edge == 0:
            return IRON['line']
        if edge in (1, 2):
            return IRON['rim'] if lit and edge == 1 else IRON['mid']
        if edge == 3:
            return IRON['line']
        blotch = (x // 2 * 73 + y // 2 * 151 + x // 5 * 29) % 7
        return IRON['dark'] if blotch < 2 else IRON['mid'] if blotch == 6 else IRON['base']
    return pixel


def frame(x, y):
    """Brass stock for the central port frame."""
    if y == 0 or x == 0:
        return BRASS['hi']
    if y == 15 or x == 15:
        return BRASS['shadow']
    return BRASS['dark'] if y in (1, 14) else BRASS['base']


def grille(x, y):
    """Port grating and the loose radiator top."""
    if x % 4 == 0:
        return SLATE['mid']
    return THROAT if x % 2 else SLATE['line']


def drive(x, y):
    """Bottom of the drive column: a brass bushing around the shaft socket."""
    d = math.hypot(x - 7.5, y - 7.5)
    if d < 2.6:
        return THROAT
    if d < 4.2:
        return BRASS['hi'] if x + y < 15 else BRASS['dark']
    if d < 5.2:
        return IRON['line']
    return riveted(16)(x, y)


def deck(x, y):
    """One pixel of the 48 px fan box top: rim band, riveted plate, toothed throat edge."""
    edge = min(x, y, 47 - x, 47 - y)
    d = math.hypot(x - 23.5, y - 23.5)
    if d < FAN_RADIUS:
        return None
    if edge == 0:
        return STEEL['edge']
    if edge < 4:
        along = x if edge in (y, 47 - y) else y
        if edge == 2 and along in (2, 45):
            return STEEL['edge']
        return STEEL['hi'] if edge == 1 else STEEL['mid']
    if edge == 4:
        return STEEL['shadow']
    if d < FAN_RADIUS + 1.5:
        tooth = int((math.atan2(y - 23.5, x - 23.5) + math.pi) / (2 * math.pi) * 48) % 2
        return STEEL['base'] if tooth else STEEL['shadow']
    if d < FAN_RADIUS + 2.5:
        return STEEL['edge']
    if x in (8, 39) and y in (8, 39):
        return STEEL['edge']
    if x in (8, 39) and y in (9, 40) or x in (9, 40) and y in (8, 39):
        return STEEL['shadow']
    return STEEL['hi'] if edge == 5 else STEEL['mid']


def wall(x, y):
    """Outer side of a fan box with a bevelled top edge and corner rivets."""
    if y == 0:
        return STEEL['edge']
    if y in (1, 2):
        return STEEL['hi'] if y == 1 else STEEL['base']
    if y in (3, 15):
        return STEEL['shadow']
    if y in (5, 13) and x in (2, 13):
        return STEEL['edge']
    return STEEL['dark'] if y == 14 else STEEL['mid']


def inner(x, y):
    """Shadowed inside of the fan box."""
    return STEEL['dark'] if y < 2 else STEEL['shadow']


def guard(x, y):
    """Loose shroud plate: a coarse grating inside a steel edge."""
    if x in (0, 15) or y in (0, 15):
        return STEEL['hi'] if x == 0 or y == 0 else STEEL['shadow']
    if x % 5 == 0 or y % 5 == 0:
        return STEEL['base']
    return THROAT


def blade(x, y):
    """Blade stock; rows run across the blade: lit leading edge, dark trailing edge, darker root."""
    if y == 0:
        return BLADE['hi']
    if y >= 6 or x < 2:
        return BLADE['dark']
    return BLADE['hi'] if y == 1 and x % 5 == 2 else BLADE['base']


def hub(x, y):
    """Dark hub cap with a bolt ring."""
    d = math.hypot(x - 7.5, y - 7.5)
    if 2.5 < d < 3.5 and (x + y) % 3 == 0:
        return STEEL['edge']
    if x in (0, 15) or y in (0, 15):
        return STEEL['shadow']
    return STEEL['dark'] if d < 2.5 else STEEL['base']


def textures():
    save('radiator_fins', framed(16, fins))
    for name, pixel in (('radiator_fin_panel', lambda size: framed(size, fins)), ('radiator_side_panel', lambda size: framed(size, slate)),
                        ('radiator_end_panel', riveted)):
        draw(name, (16, 16), pixel(16), 1.5).save(P / f'{name}.png')
        draw(name, (48, 48), pixel(48), 1.5).save(P / f'{name}_connected.png')
    save('radiator_top', riveted(16), 1.5)
    save('radiator_frame', frame)
    save('radiator_grille', grille)
    save('radiator_drive', drive, 1.5)
    sheet = draw('fan_deck', (48, 48), deck)
    sheet.crop((16, 0, 32, 16)).save(T / 'fan_deck_side.png')
    sheet.crop((0, 0, 16, 16)).save(T / 'fan_deck_corner.png')
    save('fan_guard', guard, 1.5)
    save('fan_shroud_wall', wall, 1.5)
    save('fan_shroud_inner', inner)
    save('fan_blade', blade)
    save('fan_hub', hub, 1.5)


def uv(f, t, face):
    """Vanilla UVs from position, which FinFanModels relies on for the panel orientation."""
    x1, y1, z1 = f
    x2, y2, z2 = t
    return {'north': [16 - x2, 16 - y2, 16 - x1, 16 - y1], 'south': [x1, 16 - y2, x2, 16 - y1], 'west': [z1, 16 - y2, z2, 16 - y1],
            'east': [16 - z2, 16 - y2, 16 - z1, 16 - y1], 'up': [x1, z1, x2, z2], 'down': [x1, 16 - z2, x2, 16 - z1]}[face]


def box(f, t, faces, cull=()):
    """faces maps each drawn side to a texture key or (key, uv)."""
    out = {}
    for side, spec in faces.items():
        key, area = spec if isinstance(spec, tuple) else (spec, uv(f, t, side))
        out[side] = {'uv': area, 'texture': '#' + key}
        if side in cull:
            out[side]['cullface'] = side
    return {'from': list(f), 'to': list(t), 'faces': out}


def model(textures, elements, **extra):
    return {'parent': 'minecraft:block/block', **extra, 'textures': textures, 'elements': elements}


def radiators():
    """Formed parts face along the bank (FACING): model east is the fin side, west the plain side, north/south the ends."""
    loose = {'fins': C + 'radiator_fins', 'grille': C + 'radiator_grille', 'base': C + 'radiator_top', 'particle': C + 'radiator_fins'}
    write(A / 'models/block/cooler_radiator.json', model(loose, [box((0, 0, 0), (16, 16, 16),
          {'north': 'fins', 'south': 'fins', 'east': 'fins', 'west': 'fins', 'up': 'grille', 'down': 'base'}, SIDES)]))
    for name, base in (('formed', 'radiator_top'), ('formed_drive', 'radiator_drive')):
        textures = {'fins': F + 'radiator_fin_panel', 'side': F + 'radiator_side_panel', 'end': F + 'radiator_end_panel', 'top': C + 'radiator_top',
                    'base': C + base, 'frame': C + 'radiator_frame', 'grille': C + 'radiator_grille', 'particle': F + 'radiator_side_panel'}
        core = box((0, 0, 0), (16, 16, 16), {'north': 'end', 'south': 'end', 'east': 'fins', 'west': 'side', 'up': 'top', 'down': 'base'}, SIDES)
        write(A / f'models/block/cooler_radiator/{name}.json', model(textures, [core]))
    central = json.loads((A / 'models/block/cooler_radiator/formed.json').read_text(encoding='utf-8'))
    for lo, hi in (((3, 3, 15), (13, 5, 16)), ((3, 11, 15), (13, 13, 16)), ((3, 5, 15), (5, 11, 16)), ((11, 5, 15), (13, 11, 16))):
        central['elements'].append(box(lo, hi, {face: 'frame' for face in SIDES}))
    central['elements'].append(box((5, 5, 14.5), (11, 11, 15), {face: ('grille', [0, 0, 16, 16]) for face in SIDES}))
    write(A / 'models/block/cooler_radiator/central_port.json', central)
    variants = {}
    for facing, y in (('south', 0), ('west', 90), ('north', 180), ('east', 270)):
        rot = {'y': y} if y else {}
        for drive in ('false', 'true'):
            formed = {'model': NS + 'block/cooler_radiator/formed' + ('_drive' if drive == 'true' else ''), **rot}
            for out in ('false', 'true'):
                for section in ('none', 'bottom', 'middle', 'top'):
                    key = f'drive={drive},facing={facing},outlet={out},section={section}'
                    if section == 'none':
                        variants[key + ',port=false'] = {'model': NS + 'block/cooler_radiator'}
                    else:
                        variants[key + ',port=false'] = formed if section == 'bottom' else {'model': NS + 'block/cooler_radiator/formed', **rot}
                    variants[key + ',port=true'] = {'model': NS + 'block/cooler_radiator/central_port', 'y': y}
    write(A / 'blockstates/cooler_radiator.json', {'variants': variants})


def blade_element(f, t, along_x, rotation):
    long_side, end = ('blade', [0, 6, 13, 7]), ('blade', [0, 6, 7, 7])
    sides = {'north': long_side, 'south': long_side, 'east': end, 'west': end} if along_x else \
        {'north': end, 'south': end, 'east': long_side, 'west': long_side}
    element = box(f, t, {'up': ('blade', [0, 0, 13, 7]), 'down': ('blade', [0, 0, 13, 7]), **sides})
    if not along_x:
        element['faces']['up']['rotation'] = element['faces']['down']['rotation'] = 90
    element['rotation'] = rotation
    return element


def blades(pitched=True):
    """Four wide paddle blades, each pitched about its own length; the renderer draws them twice 45 degrees apart."""
    elements = []
    for f, t, axis, sign in (((12, 7, 4.5), (25, 8, 11.5), 'x', 1), ((-9, 7, 4.5), (4, 8, 11.5), 'x', -1),
                             ((4.5, 7, 12), (11.5, 8, 25), 'z', 1), ((4.5, 7, -9), (11.5, 8, 4), 'z', -1)):
        rotation = {'angle': sign * PITCH, 'axis': axis, 'origin': [8, 7.5, 8]} if pitched else {'angle': 45, 'axis': 'y', 'origin': [8, 7.5, 8]}
        elements.append(blade_element(f, t, axis == 'x', rotation))
    return elements


def fan():
    rotor_textures = {'cast_iron': C + 'fan_hub', 'axis': 'create:block/axis', 'axis_top': 'create:block/axis_top', 'particle': C + 'fan_hub'}
    rotor = json.loads((A / 'models/block/cooling_fan/rotor.json').read_text(encoding='utf-8'))
    rotor['textures'] = rotor_textures
    rotor['elements'] = [e for e in rotor['elements'] if any(f['texture'] in ('#axis', '#cast_iron') for f in e['faces'].values())]
    write(A / 'models/block/cooling_fan/rotor.json', rotor)
    write(A / 'models/block/cooling_fan/blades.json', {'textures': {'blade': C + 'fan_blade', 'particle': C + 'fan_blade'}, 'elements': blades()})
    block = json.loads((A / 'models/block/cooling_fan/block.json').read_text(encoding='utf-8'))
    item = json.loads((A / 'models/item/cooling_fan.json').read_text(encoding='utf-8'))
    # An item model has no renderer, so its diagonal set is flat.
    item['textures'] = {**rotor_textures, 'blade': C + 'fan_blade'}
    item['elements'] = block['elements'] + rotor['elements'] + blades() + blades(False)
    write(A / 'models/item/cooling_fan.json', item)


def shroud():
    """Fan box pieces: the hub lies to +Z of a side and to +X+Z of a corner; the deck sits 1 px under the rim."""
    rim_top = ('wall', [0, 0, 16, 3])
    side = [box((0, 0, 0), (16, 13, 3), {'north': ('wall', [0, 3, 16, 16]), 'south': 'inner', 'down': 'inner'}, ('north', 'down')),
            box((0, 13, 0), (16, 16, 4), {'up': 'deck', 'north': rim_top, 'south': ('inner', [0, 0, 16, 1])}, ('north', 'up')),
            box((0, 13, 4), (16, 15, 16), {'up': 'deck', 'down': 'inner'})]
    corner = [box((0, 0, 0), (16, 13, 3), {'north': ('wall', [0, 3, 16, 16]), 'west': ('wall', [0, 3, 3, 16]), 'south': 'inner', 'down': 'inner'},
                  ('north', 'west', 'down')),
              box((0, 0, 3), (3, 13, 16), {'west': ('wall', [3, 3, 16, 16]), 'east': 'inner', 'down': 'inner'}, ('west', 'down')),
              box((3, 0, 3), (7, 13, 7), {'south': 'inner', 'east': 'inner', 'down': 'inner'}, ('down',)),
              box((0, 13, 0), (16, 16, 4), {'up': 'deck', 'north': rim_top, 'west': ('wall', [0, 0, 4, 3]), 'south': ('inner', [0, 0, 16, 1])},
                  ('north', 'west', 'up')),
              box((0, 13, 4), (4, 16, 16), {'up': 'deck', 'west': ('wall', [4, 0, 16, 3]), 'east': ('inner', [0, 0, 12, 1])}, ('west', 'up')),
              box((4, 13, 4), (16, 15, 16), {'up': 'deck', 'down': 'inner'})]
    for part, elements in (('side', side), ('corner', corner)):
        textures = {'wall': C + 'fan_shroud_wall', 'inner': C + 'fan_shroud_inner', 'deck': C + f'fan_deck_{part}', 'particle': C + 'fan_shroud_wall'}
        write(A / f'models/block/cooling_fan_shroud/{part}.json', model(textures, elements, render_type='minecraft:cutout'))


if __name__ == '__main__':
    textures()
    radiators()
    fan()
    shroud()
    print('Generated fin-fan radiator, fan and shroud textures and models')
