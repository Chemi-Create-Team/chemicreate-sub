#!/usr/bin/env python3
"""Deterministic 16 px tokamak assets, recipes, tags, translations and Ponder structures."""
import json
import math
import struct
import sys
import zlib
from pathlib import Path
from minecraft_style import grain
from generate_petrochemical_ponder import Structure

ROOT = Path(__file__).resolve().parents[1]
A = ROOT / 'src/main/resources/assets/create_refinery_n_reactors'
D = ROOT / 'src/main/resources/data'
NS = 'create_refinery_n_reactors:'
T = A / 'textures/block/fusion'


def tokamak_ponder():
    # Runtime assembly uses FusionStructure.cells; only the scene bounds are stored here.
    ponder = Structure(11, 10)
    ponder.block(10, 8, 10, 'minecraft:polished_andesite')
    ponder.save('../nuclear/fusion_tokamak')


if '--ponder-only' in sys.argv:
    tokamak_ponder()
    raise SystemExit


def write(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, indent=2, ensure_ascii=False) + '\n')


def shade(colour, amount):
    return tuple(max(0, min(255, round(c * (1 + amount / 100)))) for c in colour) + (255,)


def texture(name, painter, size=16, folder=T):
    pixels = []
    for y in range(size):
        row = []
        for x in range(size):
            row.append(painter(x, y) or (0, 0, 0, 0))
        pixels.append(row)
    folder.mkdir(parents=True, exist_ok=True)
    def chunk(kind, data):
        return struct.pack('>I', len(data)) + kind + data + struct.pack('>I', zlib.crc32(kind + data) & 0xffffffff)
    payload = b''.join(b'\0' + bytes(channel for pixel in row for channel in pixel) for row in pixels)
    path = folder / f'{name}.png'
    encoded = (b'\x89PNG\r\n\x1a\n'
        + chunk(b'IHDR', struct.pack('>IIBBBBB', size, size, 8, 6, 0, 0, 0))
        + chunk(b'IDAT', zlib.compress(payload, 9)) + chunk(b'IEND', b''))
    if '--check' in sys.argv:
        assert path.read_bytes() == encoded, f'Outdated texture: {path}'
    else:
        path.write_bytes(encoded)


# Four-tone ramps follow the user's Create palette reference.
METAL = ((47, 48, 53), (78, 80, 83), (119, 123, 120), (178, 180, 164))
STONE = ((58, 63, 54), (85, 93, 78), (108, 115, 97), (141, 148, 124))
COPPER = ((70, 39, 29), (111, 60, 37), (161, 92, 48), (209, 147, 79))
BRASS = ((73, 55, 31), (113, 84, 38), (160, 123, 54), (207, 173, 93))
TEAL = ((25, 49, 52), (39, 76, 78), (64, 111, 111), (111, 152, 143))
OLIVE = ((54, 60, 45), (78, 87, 63), (104, 115, 85), (144, 151, 115))
PAPER = ((110, 108, 92), (166, 165, 139), (207, 205, 176), (232, 230, 203))


def colour(ramp, tone):
    return ramp[tone] + (255,)


def frame(x, y, ramp=METAL):
    if x == 15 or y == 15: return colour(ramp, 0)
    if x == 0 or y == 0: return colour(ramp, 1)
    if x == 1 or y == 1: return colour(ramp, 3)
    if x == 14 or y == 14: return colour(ramp, 2)
    if x == 2 or y == 2 or x == 13 or y == 13: return colour(ramp, 0)
    return None


def plate(x, y, ramp=METAL, tone=1):
    patch = (x // 3 * 13 + y // 2 * 7) % 13
    return colour(ramp, min(3, tone + (1 if patch in (1, 8) else 0)))


def bolt(x, y):
    if (x, y) in ((3, 3), (11, 3), (3, 11), (11, 11)): return colour(BRASS, 3)
    if (x, y) in ((4, 3), (12, 3), (4, 11), (12, 11)): return colour(BRASS, 2)
    if (x, y) in ((3, 4), (4, 4), (11, 4), (12, 4), (3, 12), (4, 12), (11, 12), (12, 12)): return colour(BRASS, 0)
    return None


# Reactor skins are silver plates drawn as a seamless field plus a rim that Create's connected textures keep only on the outer edge of a
# joined face. Fields repeat every 16 px without touching the tile border, so rows of blocks read as one sheet.
SKIN = ((104, 110, 112), (146, 152, 153), (170, 176, 176), (194, 199, 197), (222, 226, 222))
RIM = ((58, 62, 64), (92, 98, 99), (118, 124, 124), (150, 156, 154), (190, 195, 192))
WINDING = ((88, 47, 30), (128, 70, 39), (166, 98, 52), (206, 145, 80))


# Each block carries two wide plates. Three shades of the same silver are chosen at random per block, so a wall of
# plating varies the way a real first wall does; small marks alone tell the parts apart.
PLATE_SHADES = ((170, 176, 176), (183, 189, 188), (157, 163, 164))
PLATE_VARIANTS = ((0, 0), (1, 0), (0, 2))


def shifted(rgb, amount): return tuple(max(0, min(255, c + amount)) for c in rgb) + (255,)


def plating(variant, mark=None):
    def paint(x, y):
        base = PLATE_SHADES[PLATE_VARIANTS[variant][y // 8]]
        if x == 15 or y % 8 == 7: return shifted(base, -46)
        if x == 0 or y % 8 == 0: return shifted(base, 22)
        tone = mark(x, y) if mark else None
        if tone: return shifted(base, -34 if tone == 'dark' else 18)
        return shifted(base, 0)
    return paint


def blanket_mark(x, y):
    # A small coolant connection on the lower plate.
    if (x, y) in ((7, 11), (8, 11), (7, 12)): return 'dark'
    return 'light' if (x, y) == (8, 12) else None


def vessel_mark(x, y):
    # Two bolts on each plate.
    if (x, y) in ((3, 3), (12, 3), (3, 11), (12, 11)): return 'dark'
    return 'light' if (x, y) in ((4, 4), (13, 4), (4, 12), (13, 12)) else None


def divertor_mark(x, y):
    # A narrow cooling slot across the lower plate.
    if 5 <= x <= 10 and y in (11, 12): return 'dark' if y == 11 else 'light'
    return None


casing_field = plating(0)


def viewport_field(x, y):
    # Quartz glass: faint and cool, with two light streaks.
    if (x - y) % 16 in (4, 5) or (x - y) % 16 == 11: return (214, 232, 236, 120)
    return (164, 196, 206, 70)


def coil_field(x, y):
    # Level copper turns, rounded by a highlight above and a gap below each.
    return colour(WINDING, (3, 2, 1, 0)[y % 4])


def solenoid_field(x, y):
    # Upright turns on the central solenoid, wound the other way from the field coils.
    return colour(WINDING, (3, 2, 2, 1, 1, 0, 2, 1)[x % 8])


def rim(field, bolts=False):
    """Paint one connected tile: the field inside a 3 px steel rim on unjoined sides, with notches at inner corners."""
    def tile(mask):
        sides = set(mask['outer_frame'])
        def depth(x, y):
            near = [d for d, side in ((x, 'left'), (y, 'up'), (15 - x, 'right'), (15 - y, 'down')) if side in sides and d < 3]
            return min(near) if near else None
        def paint(x, y):
            for corner in mask['inner_corners']:
                dx, dy = (x if 'left' in corner else 15 - x), (y if 'top' in corner else 15 - y)
                if dx < 3 and dy < 3: return colour(RIM, (0, 4, 3)[min(dx, dy)] if 'top' in corner else (0, 2, 1)[min(dx, dy)])
            d = depth(x, y)
            if d is None: return field(x, y)
            lit = (d == x and 'left' in sides) or (d == y and 'up' in sides)
            corner = {'left' if x == 2 else 'right', 'up' if y == 2 else 'down'}
            if bolts and x in (2, 13) and y in (2, 13) and corner <= sides: return colour(BRASS, 3 if (x, y) == (2, 2) else 1)
            return colour(RIM, (0, 4, 3)[d] if lit else (0, 2, 1)[d])
        return paint
    return tile


def skin(name, field, bolts=False):
    from create_casing_ct import tile_masks
    tile = rim(field, bolts)
    single = tile({'outer_frame': ['up', 'down', 'left', 'right'], 'inner_corners': []})
    masks = tile_masks()
    def sheet(x, y):
        mask = masks[y // 16 * 8 + x // 16]
        return (tile(mask) if mask is not None else single)(x % 16, y % 16)
    texture(name, single)
    texture(name + '_connected', sheet, size=128)


def plated_skin(name, mark=None, bolts=False):
    for variant in range(len(PLATE_VARIANTS)):
        skin(name + ('_' + str(variant) if variant else ''), plating(variant, mark), bolts)


def pump(x, y):
    edge = frame(x, y)
    if edge: return edge
    r = math.hypot(x - 7.5, y - 7.5)
    if r > 5.7: return plate(x, y, STONE)
    if r > 4.5: return colour(BRASS, 3 if x + y < 15 else 1)
    if r > 3.4: return colour(METAL, 0)
    if abs(x - 7.5) < 1 or abs(y - 7.5) < 1: return colour(METAL, 2)
    if x == y or x + y == 15: return colour(COPPER, 2)
    return colour(METAL, 0)


def motor_generator_side(active):
    def paint(x, y):
        edge = frame(x, y)
        if edge: return edge
        if x in (3, 4, 11, 12): return colour(COPPER, 3 if x in (3, 11) else 1)
        if x in (6, 9): return colour(BRASS, 2 if x == 6 else 0)
        if x in (7, 8) and y in (6, 7, 8, 9): return colour(TEAL, 3 if active else 1)
        return plate(x, y, METAL)
    return paint


def motor_generator_end(x, y):
    edge = frame(x, y)
    if edge: return edge
    r = math.hypot(x - 7.5, y - 7.5)
    if r < 2.2: return colour(METAL, 0)
    if r < 3.6: return colour(BRASS, 3 if x + y < 15 else 1)
    if r < 5.6:
        spoke = min(abs(x - 7.5), abs(y - 7.5), abs(x - y), abs(x + y - 15))
        return colour(COPPER if spoke < 1 else METAL, 2 if x + y < 15 else 1)
    return plate(x, y, STONE)


PORT_COLOURS = {
    'deuterium': ((34, 55, 83), (83, 132, 171)),
    'tritium': ((43, 64, 39), (119, 158, 77)),
    'ash': ((80, 62, 29), (190, 152, 69)),
    'helium_in': ((31, 65, 69), (122, 184, 179)),
    'helium_out': ((67, 81, 73), (177, 193, 164)),
    'coolant_in': ((34, 57, 81), (76, 136, 170)),
    'coolant_out': ((87, 43, 30), (188, 99, 59)),
    'controller': ((41, 63, 49), (122, 184, 109)),
}


def arrow(x, y, inward):
    if not inward: y = 15 - y
    return (x in (7, 8) and 5 <= y <= 10) or (y == 8 and 5 <= x <= 10) or (y == 9 and 6 <= x <= 9)


def icon_face(kind, active=False):
    # Ports sit flush in the joined casing: no rim, a steel flange, a dark bore and the fluid's own colour.
    def paint(x, y):
        r = math.hypot(x - 7.5, y - 7.5)
        if kind == 'controller':
            if not (3 <= x <= 12 and 4 <= y <= 11): return casing_field(x, y)
            if y == 4 or x == 3: return colour(BRASS, 3)
            if y == 11 or x == 12: return colour(BRASS, 1)
            if y == 5 or x == 4 or y == 10 or x == 11: return colour(RIM, 0)
            dark, light = PORT_COLOURS[kind]
            return (light if active and y in (7, 8) and 6 <= x <= 9 else dark) + (255,)
        if r > 6: return casing_field(x, y)
        if r > 5.2: return colour(RIM, 0)
        if r > 4.2: return colour(RIM, 4 if x + y < 14 else 2)
        if r > 3.5: return colour(RIM, 1)
        dark, light = PORT_COLOURS[kind]
        if kind in ('helium_in', 'helium_out'):
            mark = (x in (7, 8) or y in (7, 8)) and (kind == 'helium_in' or r > 1.6)
        elif kind.startswith('coolant'):
            mark = arrow(x, y, kind.endswith('in'))
        else:
            points = {'deuterium': ((6, 8), (9, 7)), 'tritium': ((6, 9), (8, 6), (10, 9)), 'ash': ((6, 7), (9, 6), (8, 10))}[kind]
            mark = any((x, y) == p or (x, y) == (p[0] + 1, p[1]) for p in points)
        return (light if mark else dark) + (255,)
    return paint


plated_skin('reinforced_casing', bolts=True)
plated_skin('breeding_blanket', blanket_mark)
plated_skin('vacuum_vessel', vessel_mark)
plated_skin('divertor', divertor_mark)
skin('viewport', viewport_field, bolts=True)
skin('toroidal_field_coil', coil_field)
skin('central_solenoid', solenoid_field)
texture('vacuum_pump', pump)
texture('motor_generator_off', motor_generator_side(False))
texture('motor_generator_on', motor_generator_side(True))
texture('motor_generator_end', motor_generator_end)
for kind in ('deuterium', 'tritium', 'ash', 'helium_in', 'helium_out', 'coolant_in', 'coolant_out'):
    texture('port_' + kind, icon_face(kind))
texture('controller_off', icon_face('controller', False))
texture('controller_on', icon_face('controller', True))


def cabinet(x, y):
    edge = frame(x, y)
    if edge: return edge
    if y in (7, 8): return colour(BRASS, 2 if y == 7 else 0)
    if x in (11, 12) and y in (5, 6): return colour(METAL, 3 if x == 11 else 0)
    return plate(x, y, STONE)


texture('console_cabinet', cabinet)


def console_panel(kind, on):
    signal = {
        'vacuum': ((43, 60, 43), (139, 178, 105)),
        'emergency': ((91, 38, 30), (200, 68, 43)),
        'cryo': ((34, 64, 69), (123, 184, 178)),
        'magnets': ((53, 67, 39), (151, 176, 96)),
        'plasma': ((73, 37, 75), (178, 95, 170)),
        'output': ((61, 67, 36), (182, 184, 95)),
    }[kind][int(on)]
    def paint(x, y):
        edge = frame(x, y, BRASS)
        if edge: return edge
        if y == 12 and 6 <= x <= 9: return signal + (255,)
        if kind == 'vacuum':
            r = math.hypot(x - 7.5, y - 7.5)
            if 3.6 < r < 5.6: return colour(METAL, 1)
            if r <= 3.6:
                if (x == 7 and 6 <= y <= 9) or (y == 9 and 6 <= x <= 8): return colour(METAL, 0)
                if (x, y) in ((5, 6), (7, 4), (10, 6), (11, 8)): return colour(PAPER, 0)
                return colour(PAPER, 2)
        elif kind == 'emergency':
            if x in (3, 12) and 4 <= y <= 11: return colour(METAL, 3)
            if 4 <= x <= 11 and 4 <= y <= 11:
                if x in (4, 11) or y in (4, 11): return colour(METAL, 0)
                return signal + (255,)
        elif kind == 'cryo':
            if 4 <= x <= 11 and 4 <= y <= 11 and (x in (7, 8) or y in (7, 8) or x == y or x + y == 15): return signal + (255,)
        elif kind == 'magnets':
            if (x in (4, 5, 10, 11) and 4 <= y <= 11) or (y in (4, 5) and 4 <= x <= 11): return signal + (255,)
            if y in (10, 11) and x in (4, 5, 10, 11): return colour(METAL, 3)
        elif kind == 'plasma':
            r = math.hypot(x - 7.5, y - 7.5)
            if 2.5 < r < 4.5: return signal + (255,)
        elif kind == 'output':
            if 4 <= x <= 11 and y in (4, 5, 6): return colour(PAPER, 2) if x in (4, 6, 8, 10) else colour(METAL, 0)
            r = math.hypot(x - 8, y - 10)
            if x == 8 and y in (8, 9): return signal + (255,)
            if r < 2.4: return colour(BRASS, 3 if x + y < 18 else 1)
        return plate(x, y, METAL, 1)
    return paint


CONTROLS = ('vacuum', 'emergency', 'cryo', 'magnets', 'plasma', 'output')
for control in CONTROLS:
    texture(f'console_{control}_off', console_panel(control, False))
    texture(f'console_{control}_on', console_panel(control, True))


def plasma(x, y):
    n = grain('fusion_plasma', x, y, 5)
    alpha = max(35, min(220, 130 + n * 4))
    return (max(120, min(255, 230 + n)), max(20, min(180, 70 + n * 2)), max(140, min(255, 235 + n)), alpha)


texture('plasma', plasma)
if '--textures-only' in sys.argv or '--check' in sys.argv:
    print('Fusion block textures verified.' if '--check' in sys.argv else 'Generated fusion block textures only; models, recipes and language files unchanged.')
    raise SystemExit


def cube_model(texture_name):
    return {'parent': 'minecraft:block/cube_all', 'textures': {'all': NS + 'block/fusion/' + texture_name}}


simple = {
    'fusion_reinforced_casing': 'reinforced_casing', 'fusion_toroidal_field_coil': 'toroidal_field_coil',
    'fusion_central_solenoid': 'central_solenoid', 'fusion_vacuum_vessel': 'vacuum_vessel',
    'fusion_breeding_blanket': 'breeding_blanket', 'fusion_divertor': 'divertor', 'fusion_viewport': 'viewport',
}
PLATED = ('fusion_reinforced_casing', 'fusion_vacuum_vessel', 'fusion_breeding_blanket', 'fusion_divertor')
for block, skin in simple.items():
    shades = range(len(PLATE_VARIANTS)) if block in PLATED else range(1)
    for tone in shades:
        suffix = '_' + str(tone) if tone else ''
        write(A / 'models/block/fusion' / f'{block}{suffix}.json', cube_model(skin + suffix))
    models = [{'model': NS + 'block/fusion/' + block + ('_' + str(tone) if tone else '')} for tone in shades]
    write(A / 'blockstates' / f'{block}.json', {'variants': {'': models if len(models) > 1 else models[0]}})
    write(A / 'models/item' / f'{block}.json', {'parent': NS + 'block/fusion/' + block})
write(A / 'models/block/fusion/fusion_viewport.json', {**cube_model('viewport'), 'render_type': 'minecraft:translucent'})


def faced_model(front):
    faces = {face: {'texture': '#side'} for face in ('up', 'down', 'south', 'west', 'east')}
    faces['north'] = {'texture': '#front'}
    return {'parent': 'minecraft:block/block', 'textures': {'particle': '#side', 'side': NS + 'block/fusion/reinforced_casing', 'front': NS + 'block/fusion/' + front},
            'elements': [{'from': [0, 0, 0], 'to': [16, 16, 16], 'faces': faces}]}


rotations = {'north': 0, 'east': 90, 'south': 180, 'west': 270}
ports = ('deuterium', 'tritium', 'ash', 'helium_in', 'helium_out', 'coolant_in', 'coolant_out')
for name in ports:
    block = 'fusion_' + name + ('_port' if name in ('deuterium', 'tritium', 'ash') else 'let' if name == 'coolant_out' else '')
    block = {'helium_in': 'fusion_helium_inlet', 'helium_out': 'fusion_helium_outlet',
             'coolant_in': 'fusion_coolant_inlet', 'coolant_out': 'fusion_coolant_outlet'}.get(name, block)
    model = 'fusion_' + name + '_port'
    write(A / 'models/block/fusion' / f'{model}.json', faced_model('port_' + name))
    write(A / 'blockstates' / f'{block}.json', {'variants': {
        f'facing={facing}': {'model': NS + 'block/fusion/' + model, **({'y': angle} if angle else {})}
        for facing, angle in rotations.items()}})
    write(A / 'models/item' / f'{block}.json', {'parent': NS + 'block/fusion/' + model})

for active in (False, True):
    write(A / 'models/block/fusion' / f'controller_{str(active).lower()}.json', faced_model('controller_' + ('on' if active else 'off')))
write(A / 'blockstates/fusion_reactor_controller.json', {'variants': {
    f'active={str(active).lower()},facing={facing}': {'model': NS + 'block/fusion/controller_' + str(active).lower(), **({'y': angle} if angle else {})}
    for active in (False, True) for facing, angle in rotations.items()}})
write(A / 'models/item/fusion_reactor_controller.json', {'parent': NS + 'block/fusion/controller_false'})

write(A / 'models/block/fusion/vacuum_pump.json', cube_model('vacuum_pump'))
write(A / 'blockstates/fusion_vacuum_pump.json', {'variants': {
    'axis=x': {'model': NS + 'block/fusion/vacuum_pump', 'x': 90, 'y': 90},
    'axis=y': {'model': NS + 'block/fusion/vacuum_pump'},
    'axis=z': {'model': NS + 'block/fusion/vacuum_pump', 'x': 90}}})
write(A / 'models/item/fusion_vacuum_pump.json', {'parent': NS + 'block/fusion/vacuum_pump'})

for active in (False, True):
    state = 'on' if active else 'off'
    write(A / 'models/block/fusion' / f'motor_generator_{state}.json', {
        'parent': 'minecraft:block/cube_column',
        'textures': {'end': NS + 'block/fusion/motor_generator_end',
                     'side': NS + 'block/fusion/motor_generator_' + state}})
write(A / 'blockstates/fusion_motor_generator.json', {'variants': {
    f'active={str(active).lower()},axis={axis}': {
        'model': NS + 'block/fusion/motor_generator_' + ('on' if active else 'off'),
        **({'x': 90, 'y': 90} if axis == 'x' else {'x': 90} if axis == 'z' else {})}
    for active in (False, True) for axis in ('x', 'y', 'z')}})
write(A / 'models/item/fusion_motor_generator.json', {'parent': NS + 'block/fusion/motor_generator_off'})


def console_model(panel):
    return {'parent': 'minecraft:block/block', 'textures': {'particle': NS + 'block/fusion/console_cabinet', 'case': NS + 'block/fusion/console_cabinet',
                         'panel': NS + 'block/fusion/' + panel},
            'elements': [
                {'from': [0, 0, 0], 'to': [16, 8, 16], 'faces': {face: {'texture': '#case'} for face in ('up','down','north','south','west','east')}},
                {'from': [0, 8, 1], 'to': [16, 11, 15], 'faces': {
                    'up': {'texture': '#panel'}, 'down': {'texture': '#case'}, 'north': {'texture': '#case'},
                    'south': {'texture': '#case'}, 'west': {'texture': '#case'}, 'east': {'texture': '#case'}}},
                {'from': [0, 11, 13], 'to': [16, 15, 16], 'faces': {face: {'texture': '#case'} for face in ('up','down','north','south','west','east')}}]}


for cell, control in enumerate(CONTROLS):
    for active in (False, True):
        state = 'on' if active else 'off'
        write(A / 'models/block/fusion' / f'console_{cell}_{state}.json', console_model(f'console_{control}_{state}'))
write(A / 'blockstates/fusion_control_console.json', {'variants': {
    f'active={str(active).lower()},facing={facing}': {'model': NS + 'block/fusion/console_1_' + ('on' if active else 'off'), **({'y': angle} if angle else {})}
    for active in (False, True) for facing, angle in rotations.items()}})
write(A / 'models/item/fusion_control_console.json', {'parent': NS + 'block/fusion/console_1_off'})
write(A / 'blockstates/fusion_console_panel.json', {'variants': {
    f'active={str(active).lower()},column={column},depth={depth},facing={facing}': {
        'model': NS + f'block/fusion/console_{column + depth * 3}_' + ('on' if active else 'off'), **({'y': angle} if angle else {})}
    for active in (False, True) for column in range(3) for depth in range(2) for facing, angle in rotations.items()}})
write(A / 'models/item/fusion_console_panel.json', {'parent': NS + 'block/fusion/console_0_off'})

fluid_state = {'variants': {f'level={level}': {'model': 'minecraft:block/water'} for level in range(16)}}
for fluid in ('deuterium_gas', 'tritium_gas', 'helium_gas', 'liquid_helium'):
    write(A / 'blockstates' / f'{fluid}.json', fluid_state)


template = json.loads((ROOT / 'tools/vanilla_bucket_template.json').read_text())
bucket_colours = {'deuterium_gas': (138, 162, 226), 'tritium_gas': (154, 190, 105), 'helium_gas': (205, 224, 206)}
for number, (name, colour) in enumerate(bucket_colours.items()):
    pixels = [[tuple(template['pixels'][y * 16 + x]) for x in range(16)] for y in range(16)]
    for index in template['content_mask']:
        x, y = index % 16, index // 16
        amount = 24 if y == 4 else -20 if y >= 7 else 0
        pixels[y][x] = tuple(max(0, min(255, c + amount)) for c in colour) + (255,)
    texture(name + '_bucket', lambda x, y, p=pixels: p[y][x], folder=A / 'textures/item')
    write(A / 'models/item' / f'{name}_bucket.json', {'parent': 'minecraft:item/generated', 'textures': {'layer0': NS + 'item/' + name + '_bucket'}})


def cartridge(x, y):
    if not (3 <= x <= 12 and 2 <= y <= 14): return None
    if x in (3, 12) or y in (2, 14): return shade((67, 77, 78), -20)
    if x in (4, 11) or y in (3, 13): return shade((158, 169, 170), 15 if x == 4 or y == 3 else -14)
    if (x + y) % 4 == 0: return shade((176, 92, 92), 8)
    return shade((97, 116, 82), grain('lithium_lead_cartridge', x, y, 1.4))


texture('lithium_lead_blanket_cartridge', cartridge, folder=A / 'textures/item')
write(A / 'models/item/lithium_lead_blanket_cartridge.json', {'parent': 'minecraft:item/generated',
    'textures': {'layer0': NS + 'item/lithium_lead_blanket_cartridge'}})


blocks = list(simple) + ['fusion_reactor_controller', 'fusion_motor_generator', 'fusion_vacuum_pump'] + [
    'fusion_deuterium_port', 'fusion_tritium_port', 'fusion_ash_port', 'fusion_helium_inlet',
    'fusion_helium_outlet', 'fusion_coolant_inlet', 'fusion_coolant_outlet', 'fusion_control_console', 'fusion_console_panel']
for block in blocks:
    write(D / 'create_refinery_n_reactors/loot_table/blocks' / f'{block}.json', {'type': 'minecraft:block', 'pools': [{
        'rolls': 1, 'entries': [{'type': 'minecraft:item', 'name': NS + block}],
        'conditions': [{'condition': 'minecraft:survives_explosion'}]}]})


def shaped(name, pattern, key, count=1):
    write(D / 'create_refinery_n_reactors/recipe' / f'{name}.json', {'type': 'minecraft:crafting_shaped', 'pattern': pattern,
        'key': {symbol: item if isinstance(item, dict) else {'item': item} for symbol, item in key.items()}, 'result': {'id': NS + name, 'count': count}})


shaped('fusion_reinforced_casing', ['ASA', 'SCS', 'ASA'], {'A': 'create:andesite_alloy', 'S': NS + 'stainless_steel_sheet', 'C': 'create:sturdy_sheet'}, 8)
shaped('fusion_toroidal_field_coil', ['CCC', 'RBR', 'CCC'], {'C': 'create:copper_sheet', 'R': 'minecraft:redstone', 'B': 'create:brass_casing'}, 8)
shaped('fusion_central_solenoid', ['CCC', 'RER', 'CCC'], {'C': 'create:copper_sheet', 'R': 'minecraft:redstone', 'E': 'create:electron_tube'}, 8)
shaped('fusion_vacuum_vessel', ['SSS', 'SCS', 'SSS'], {'S': NS + 'stainless_steel_sheet', 'C': NS + 'fusion_reinforced_casing'}, 8)
shaped('fusion_viewport', [' G ', 'GVG', ' G '], {'G': NS + 'industrial_glass', 'V': NS + 'fusion_vacuum_vessel'})
shaped('fusion_breeding_blanket', ['CAC', 'ASA', 'CAC'], {'C': 'create:copper_sheet', 'A': 'minecraft:amethyst_shard', 'S': NS + 'fusion_reinforced_casing'}, 8)
shaped('fusion_divertor', ['OOO', 'SSS', 'CCC'], {'O': 'minecraft:obsidian', 'S': NS + 'stainless_steel_sheet', 'C': NS + 'fusion_reinforced_casing'}, 8)
shaped('fusion_reactor_controller', ['ELE', 'RCR', 'SAS'], {'E': 'create:electron_tube', 'L': 'create:redstone_link', 'R': 'minecraft:redstone',
    'C': NS + 'fusion_reinforced_casing', 'S': NS + 'stainless_steel_sheet', 'A': 'create:precision_mechanism'})
shaped('fusion_motor_generator', ['CBC', 'PAP', 'CBC'], {'C': 'create:copper_sheet', 'B': 'create:brass_casing',
    'P': 'create:precision_mechanism', 'A': NS + 'fusion_reinforced_casing'})
shaped('fusion_vacuum_pump', ['SPS', 'PCP', 'SPS'], {'S': NS + 'stainless_steel_sheet', 'P': 'create:mechanical_pump', 'C': NS + 'fusion_reinforced_casing'})
for block, marker in [('fusion_deuterium_port', 'minecraft:light_blue_dye'), ('fusion_tritium_port', 'minecraft:lime_dye'),
                      ('fusion_ash_port', 'minecraft:yellow_dye'), ('fusion_helium_inlet', 'minecraft:cyan_dye'),
                      ('fusion_helium_outlet', 'minecraft:white_dye'), ('fusion_coolant_inlet', 'minecraft:blue_dye'),
                      ('fusion_coolant_outlet', 'minecraft:red_dye')]:
    shaped(block, [' S ', 'PCP', ' M '], {'S': NS + 'stainless_steel_sheet', 'P': 'create:fluid_pipe', 'C': NS + 'fusion_reinforced_casing', 'M': marker})
shaped('fusion_control_console', ['ERE', 'BCB', 'SAS'], {'E': 'create:electron_tube', 'R': 'minecraft:redstone', 'B': 'create:brass_sheet',
    'C': 'create:brass_casing', 'S': NS + 'stainless_steel_sheet', 'A': 'minecraft:lever'})
shaped('fusion_console_panel', ['EGE', 'BCB', 'SRS'], {'E': 'create:electron_tube', 'G': 'minecraft:tinted_glass', 'B': 'create:brass_sheet',
    'C': {'tag': NS + 'machine_casings'}, 'S': NS + 'stainless_steel_sheet', 'R': 'minecraft:redstone'}, 2)
shaped('lithium_lead_blanket_cartridge', ['CAC', 'PBP', 'CAC'], {'C': 'create:copper_sheet', 'A': 'minecraft:amethyst_shard',
    'P': NS + 'stainless_steel_sheet', 'B': 'minecraft:heavy_weighted_pressure_plate'})


for tag_path in (D / 'minecraft/tags/block/mineable/pickaxe.json', D / 'create/tags/block/non_movable.json'):
    tag = json.loads(tag_path.read_text())
    for block in blocks:
        value = NS + block
        if value not in tag['values']: tag['values'].append(value)
    write(tag_path, tag)


tokamak_ponder()

console = Structure(7, 5)
for depth in range(2):
    for column in range(3):
        x, z = 2 + column, 2 + depth
        if column == 1 and depth == 0:
            console.block(x, 1, z, NS + 'fusion_control_console', facing='north', active='false')
        else:
            console.block(x, 1, z, NS + 'fusion_console_panel', facing='north', active='false', column=str(column), depth=str(depth))
console.save('../nuclear/fusion_console')

motor_generator = Structure(13, 9)
motor_generator.block(4, 2, 4, NS + 'fusion_reactor_controller', facing='east', active='false')
motor_generator.block(5, 2, 4, NS + 'fusion_motor_generator', axis='x', active='false')
motor_generator.block(6, 2, 4, NS + 'large_flywheel', axis='x')
for x in range(7, 10): motor_generator.block(x, 2, 4, 'create:shaft', axis='x')
motor_generator.block(10, 2, 4, 'create:creative_motor', facing='west')
motor_generator.save('../nuclear/fusion_motor_generator')

game_test = Structure(80, 40)
game_test.save('../../../../data/create_refinery_n_reactors/structure/test_fusion')

plant_test = Structure(88, 40)
plant_test.save('../../../../data/create_refinery_n_reactors/structure/test_fusion_plant')


EN = {
    'block.create_refinery_n_reactors.fusion_reinforced_casing': 'Reinforced Andesite Tokamak Support',
    'block.create_refinery_n_reactors.fusion_toroidal_field_coil': 'Toroidal Field Coil',
    'block.create_refinery_n_reactors.fusion_central_solenoid': 'Central Solenoid',
    'block.create_refinery_n_reactors.fusion_vacuum_vessel': 'Double-Wall Vacuum Vessel',
    'block.create_refinery_n_reactors.fusion_breeding_blanket': 'Lithium-Lead Breeding Blanket',
    'block.create_refinery_n_reactors.fusion_divertor': 'Tungsten Divertor',
    'block.create_refinery_n_reactors.fusion_viewport': 'Fusion Viewport',
    'block.create_refinery_n_reactors.fusion_reactor_controller': 'Tokamak Magnet Feeder',
    'block.create_refinery_n_reactors.fusion_motor_generator': 'Tokamak Flywheel Motor-Generator',
    'block.create_refinery_n_reactors.fusion_vacuum_pump': 'Tokamak Vacuum Pump',
    'block.create_refinery_n_reactors.fusion_deuterium_port': 'Deuterium Fuelling Port',
    'block.create_refinery_n_reactors.fusion_tritium_port': 'Tritium Fuelling Port',
    'block.create_refinery_n_reactors.fusion_ash_port': 'Helium Ash Port',
    'block.create_refinery_n_reactors.fusion_helium_inlet': 'Liquid Helium Inlet',
    'block.create_refinery_n_reactors.fusion_helium_outlet': 'Helium Boil-Off Outlet',
    'block.create_refinery_n_reactors.fusion_coolant_inlet': 'Blanket Coolant Inlet',
    'block.create_refinery_n_reactors.fusion_coolant_outlet': 'Blanket Coolant Outlet',
    'block.create_refinery_n_reactors.fusion_control_console': 'Fusion Emergency Console',
    'block.create_refinery_n_reactors.fusion_console_panel': 'Fusion Instrument Console',
    'item.create_refinery_n_reactors.lithium_lead_blanket_cartridge': 'Lithium-Lead Blanket Cartridge',
    'item.create_refinery_n_reactors.deuterium_gas_bucket': 'Bucket of Deuterium',
    'item.create_refinery_n_reactors.tritium_gas_bucket': 'Bucket of Tritium',
    'item.create_refinery_n_reactors.helium_gas_bucket': 'Bucket of Helium',
    'item.create_refinery_n_reactors.liquid_helium_bucket': 'Bucket of Liquid Helium',
    'fluid.create_refinery_n_reactors.deuterium_gas': 'Deuterium',
    'fluid.create_refinery_n_reactors.tritium_gas': 'Tritium',
    'fluid.create_refinery_n_reactors.helium_gas': 'Helium',
    'fluid.create_refinery_n_reactors.liquid_helium': 'Liquid Helium',
}
JA = {
    'block.create_refinery_n_reactors.fusion_reinforced_casing': '強化安山岩トカマク支持材',
    'block.create_refinery_n_reactors.fusion_toroidal_field_coil': 'トロイダル磁場コイル',
    'block.create_refinery_n_reactors.fusion_central_solenoid': '中央ソレノイド',
    'block.create_refinery_n_reactors.fusion_vacuum_vessel': '二重壁真空容器',
    'block.create_refinery_n_reactors.fusion_breeding_blanket': 'リチウム鉛増殖ブランケット',
    'block.create_refinery_n_reactors.fusion_divertor': 'タングステンダイバータ',
    'block.create_refinery_n_reactors.fusion_viewport': '核融合炉のぞき窓',
    'block.create_refinery_n_reactors.fusion_reactor_controller': 'トカマク磁石給電盤',
    'block.create_refinery_n_reactors.fusion_motor_generator': 'トカマク・フライホイール電動発電機',
    'block.create_refinery_n_reactors.fusion_vacuum_pump': 'トカマク真空ポンプ',
    'block.create_refinery_n_reactors.fusion_deuterium_port': '重水素燃料口',
    'block.create_refinery_n_reactors.fusion_tritium_port': '三重水素燃料口',
    'block.create_refinery_n_reactors.fusion_ash_port': 'ヘリウム灰排出口',
    'block.create_refinery_n_reactors.fusion_helium_inlet': '液体ヘリウム入口',
    'block.create_refinery_n_reactors.fusion_helium_outlet': 'ヘリウム蒸発ガス出口',
    'block.create_refinery_n_reactors.fusion_coolant_inlet': 'ブランケット冷却水入口',
    'block.create_refinery_n_reactors.fusion_coolant_outlet': 'ブランケット冷却水出口',
    'block.create_refinery_n_reactors.fusion_control_console': '核融合炉緊急操作卓',
    'block.create_refinery_n_reactors.fusion_console_panel': '核融合炉計器卓',
    'item.create_refinery_n_reactors.lithium_lead_blanket_cartridge': 'リチウム鉛ブランケットカートリッジ',
    'item.create_refinery_n_reactors.deuterium_gas_bucket': '重水素入りバケツ',
    'item.create_refinery_n_reactors.tritium_gas_bucket': '三重水素入りバケツ',
    'item.create_refinery_n_reactors.helium_gas_bucket': 'ヘリウム入りバケツ',
    'item.create_refinery_n_reactors.liquid_helium_bucket': '液体ヘリウム入りバケツ',
    'fluid.create_refinery_n_reactors.deuterium_gas': '重水素',
    'fluid.create_refinery_n_reactors.tritium_gas': '三重水素',
    'fluid.create_refinery_n_reactors.helium_gas': 'ヘリウム',
    'fluid.create_refinery_n_reactors.liquid_helium': '液体ヘリウム',
}

statuses = {
    'structure': ('Structure incomplete', '構造が未完成です'), 'unloaded': ('A structure chunk is unloaded', '構造のチャンクが未読み込みです'),
    'casing': ('Tokamak support missing', 'トカマク支持材が不足しています'), 'coil': ('Toroidal coil missing', 'トロイダルコイルが不足しています'),
    'solenoid': ('Central solenoid missing', '中央ソレノイドが不足しています'), 'vessel': ('Vacuum vessel missing', '真空容器が不足しています'),
    'blanket': ('Breeding blanket missing or spent', '増殖ブランケットがないか消耗しています'), 'divertor': ('Divertor missing', 'ダイバータが不足しています'),
    'clearance': ('Required clearance obstructed', '必要な空間に障害物があります'), 'controller': ('Controller misplaced', '制御盤の位置が違います'),
    'vacuum_pump': ('Vacuum pump missing', '真空ポンプが不足しています'), 'deuterium': ('Deuterium port missing', '重水素口が不足しています'),
    'tritium': ('Tritium port missing', '三重水素口が不足しています'), 'ash': ('Ash port missing', '灰排出口が不足しています'),
    'helium_in': ('Liquid-helium inlet missing', '液体ヘリウム入口が不足しています'), 'helium_out': ('Helium outlet missing', 'ヘリウム出口が不足しています'),
    'coolant_in': ('Coolant inlet missing', '冷却水入口が不足しています'), 'coolant_out': ('Coolant outlet missing', '冷却水出口が不足しています'),
    'quench': ('MAGNET QUENCH — energy dump active', '磁石クエンチ — エネルギーを放出中'), 'dumping': ('Emergency magnet dump', '緊急磁場ダンプ中'),
    'vacuum_off': ('Vacuum pumps disabled', '真空ポンプが停止しています'), 'pumping': ('Pumping down vacuum vessel', '真空容器を排気中'),
    'helium': ('Liquid-helium cooling disabled or empty', '液体ヘリウム冷却が停止または空です'), 'cooling': ('Cooling superconducting coils', '超電導コイルを冷却中'),
    'magnets_off': ('Magnet charging disabled', '磁石充電が停止しています'), 'charging': ('Charging toroidal magnets', 'トロイダル磁石を充電中'),
    'fuel': ('Deuterium or tritium missing', '重水素または三重水素が不足しています'), 'coolant': ('Blanket coolant unavailable', 'ブランケット冷却水を流せません'),
    'ready': ('Ready for plasma', 'プラズマ点火準備完了'), 'heating': ('Heating initial plasma', '初期プラズマを加熱中'),
    'burning': ('D-T plasma burning', 'D-Tプラズマ燃焼中'), 'heating_power': ('Plasma heating power too low', 'プラズマ加熱電力が不足しています'),
}
for key, (en, ja) in statuses.items():
    EN['fusion_reactor.create_refinery_n_reactors.status.' + key] = en
    JA['fusion_reactor.create_refinery_n_reactors.status.' + key] = ja

extra = {
    'fusion_reactor.create_refinery_n_reactors.terminal.positive': ('Magnet feeder +', '磁石給電 +'),
    'fusion_reactor.create_refinery_n_reactors.terminal.negative': ('Magnet feeder −', '磁石給電 −'),
    'fusion_reactor.create_refinery_n_reactors.goggle.status': ('Tokamak: %s', 'トカマク: %s'),
    'fusion_reactor.create_refinery_n_reactors.goggle.size': ('Required: %s × %s × %s', '必要寸法: %s × %s × %s'),
    'fusion_reactor.create_refinery_n_reactors.goggle.fault': ('First fault: %s, %s, %s', '最初の不一致: %s, %s, %s'),
    'fusion_reactor.create_refinery_n_reactors.goggle.ponder': ('Hold [W] to Ponder the fixed layout', '[W]長押しで固定構造を確認'),
    'fusion_reactor.create_refinery_n_reactors.goggle.power': ('Fusion: %s MW / %s%% setpoint', '核融合: %s MW / 設定%s%%'),
    'fusion_reactor.create_refinery_n_reactors.goggle.fuel': ('D₂ %s | T₂ %s | He ash %s mB', 'D₂ %s | T₂ %s | He灰 %s mB'),
    'fusion_reactor.create_refinery_n_reactors.goggle.coolant': ('Blanket %s °C | %s mB/t | hot %s mB', 'ブランケット %s °C | %s mB/t | 温水 %s mB'),
    'fusion_reactor.create_refinery_n_reactors.goggle.magnet': ('Coil %s K | %s T | %s%%', 'コイル %s K | %s T | %s%%'),
    'fusion_reactor.create_refinery_n_reactors.goggle.vacuum': ('Vacuum %s Pa | plasma %s MK', '真空 %s Pa | プラズマ %s MK'),
    'fusion_reactor.create_refinery_n_reactors.control.emergency_latched': ('Emergency dump is latched', '緊急ダンプが保持されています'),
    'fusion_reactor.create_refinery_n_reactors.control.vacuum': ('Vacuum pumps: %s', '真空ポンプ: %s'),
    'fusion_reactor.create_refinery_n_reactors.control.cryogenics': ('Helium cooling: %s', 'ヘリウム冷却: %s'),
    'fusion_reactor.create_refinery_n_reactors.control.magnets': ('Magnet charge: %s', '磁石充電: %s'),
    'fusion_reactor.create_refinery_n_reactors.control.plasma': ('Plasma heating: %s', 'プラズマ加熱: %s'),
    'fusion_reactor.create_refinery_n_reactors.control.state.on': ('ON', '入'),
    'fusion_reactor.create_refinery_n_reactors.control.state.off': ('OFF', '切'),
    'fusion_reactor.create_refinery_n_reactors.control.target': ('Fusion setpoint: %s%%', '核融合出力設定: %s%%'),
    'fusion_reactor.create_refinery_n_reactors.control.reset_blocked': ('Dump reset blocked until coils are cold and discharged', 'コイルが低温かつ放電完了するまで復帰できません'),
    'fusion_reactor.create_refinery_n_reactors.control.reset': ('Emergency dump reset', '緊急ダンプを復帰しました'),
    'fusion_reactor.create_refinery_n_reactors.control.dump': ('Emergency magnet dump commanded', '緊急磁場ダンプを開始しました'),
    'fusion_console.create_refinery_n_reactors.link.missing': ('Attach a Display Link from the reactor', '原子炉からディスプレイリンクを接続してください'),
    'fusion_console.create_refinery_n_reactors.link.unlinked': ('Display Link has the wrong source or target', 'ディスプレイリンクの入出力が違います'),
    'fusion_console.create_refinery_n_reactors.link.ambiguous': ('More than one reactor is linked', '複数の原子炉が接続されています'),
    'fusion_console.create_refinery_n_reactors.link.incomplete': ('Complete the 3 × 2 control desk', '3×2の制御卓を完成させてください'),
    'fusion_console.create_refinery_n_reactors.reserved': ('This panel is an indicator; use the guarded master switch', 'この面は表示器です。中央の保護スイッチを使ってください'),
    'fusion_console.create_refinery_n_reactors.goggle.status': ('Linked reactor: %s', '接続炉: %s'),
    'fusion_console.create_refinery_n_reactors.goggle.power': ('Fusion %s MW | setpoint %s%%', '核融合 %s MW | 設定%s%%'),
    'fusion_console.create_refinery_n_reactors.goggle.vacuum': ('Vacuum %s Pa | coil %s K', '真空 %s Pa | コイル%s K'),
    'fusion_console.create_refinery_n_reactors.goggle.magnet': ('Magnet %s%% | plasma %s MK', '磁場 %s%% | プラズマ%s MK'),
    'fusion_console.create_refinery_n_reactors.goggle.dump': ('DUMP ACTIVE | magnet %s%% | plasma %s MK', 'ダンプ作動 | 磁場%s%% | プラズマ%s MK'),
    'fusion_motor_generator.create_refinery_n_reactors.status': ('Motor-generator: %s', '電動発電機: %s'),
    'fusion_motor_generator.create_refinery_n_reactors.status.assembly': ('Awaiting assembly', '構成待ち'),
    'fusion_motor_generator.create_refinery_n_reactors.status.controller': ('Attach to magnet feeder', '磁石給電盤へ接続してください'),
    'fusion_motor_generator.create_refinery_n_reactors.status.flywheel': ('Large flywheel missing', '大型フライホイールがありません'),
    'fusion_motor_generator.create_refinery_n_reactors.status.idle': ('No electrical demand', '電力要求なし'),
    'fusion_motor_generator.create_refinery_n_reactors.status.overload': ('Shaft overloaded', '軸が過負荷です'),
    'fusion_motor_generator.create_refinery_n_reactors.status.stopped': ('Shaft stopped', '軸停止'),
    'fusion_motor_generator.create_refinery_n_reactors.status.running': ('Supplying tokamak', 'トカマクへ給電中'),
    'fusion_motor_generator.create_refinery_n_reactors.power': ('Electrical %s / %s MW', '電力 %s / %s MW'),
    'fusion_motor_generator.create_refinery_n_reactors.speed': ('Shaft %s / %s RPM', '軸回転 %s / %s RPM'),
    'fusion_motor_generator.create_refinery_n_reactors.efficiency': ('Conversion efficiency %s%%', '変換効率 %s%%'),
}
for key, (en, ja) in extra.items(): EN[key] = en; JA[key] = ja

display_names = {
    'fusion_power': ('Fusion power (MW)', '核融合出力 (MW)'), 'fusion_target': ('Fusion setpoint (%)', '核融合出力設定 (%)'),
    'fusion_vacuum': ('Base vacuum (Pa)', '基底真空 (Pa)'), 'fusion_plasma_temperature': ('Plasma temperature (MK)', 'プラズマ温度 (MK)'),
    'fusion_coil_temperature': ('Coil temperature (K)', 'コイル温度 (K)'), 'fusion_magnetic_field': ('Toroidal field (T)', 'トロイダル磁場 (T)'),
    'fusion_magnet_charge': ('Magnet charge (%)', '磁石充電率 (%)'), 'fusion_blanket_temperature': ('Blanket temperature (°C)', 'ブランケット温度 (°C)'),
    'fusion_coolant_flow': ('Blanket coolant (mB/t)', 'ブランケット冷却水 (mB/t)'), 'fusion_deuterium': ('Deuterium inventory', '重水素在庫'),
    'fusion_tritium': ('Tritium inventory', '三重水素在庫'), 'fusion_liquid_helium': ('Liquid helium inventory', '液体ヘリウム在庫'),
    'fusion_blanket_life': ('Blanket cartridge life', 'ブランケット寿命'), 'fusion_electric_power': ('Heating power (MW)', '加熱電力 (MW)'),
    'fusion_emergency': ('Emergency dump', '緊急ダンプ'), 'fusion_quench': ('Magnet quench', '磁石クエンチ'),
}
for key, (en, ja) in display_names.items(): EN['display.create_refinery_n_reactors.' + key] = en; JA['display.create_refinery_n_reactors.' + key] = ja

ponder_text = {
    'title': ('Building a compact doughnut-shaped tokamak', '2×2マス流路のドーナツ炉を建設する'),
    'shell': ('Build the 11 × 11 × 8 tokamak. Keep the central 3 × 3 opening clear above the foundation. The service hatch stays at the front edge.', '11×11×8のトカマクを組みます。中央3×3マスは基礎より上を空けます。保守ハッチは手前の外周に置きます。'),
    'magnets': ('Build four coil pairs into the channel walls and the solenoid into the base. Leave the central hole open.', 'コイル4組を流路の壁へ、ソレノイドを基礎へ組み込みます。中央の穴は空けます。'),
    'vessel': ('Make a clear, continuous 2×2 channel around the ring. Enclose it with vacuum vessel, blanket, divertor and coils.', '幅2×高さ2の流路を一周つなぎ、内部を空けます。周囲を真空容器・ブランケット・ダイバータ・コイルで囲みます。'),
    'ports': ('Supply equal D₂ and T₂, 4.2 K liquid helium and 200 °C water. Remove ash, boil-off gas and hot water.', 'D₂とT₂を同量、液体ヘリウムを4.2 K、冷却水を200 °Cで供給します。灰・蒸発ガス・温水も搬出します。'),
    'drive': ('Drive the vacuum pump at 32 RPM or more. Provide 120 MW for charging and 40 MW for 80% operation.', '真空ポンプへ32 RPM以上を入力します。電源は磁石充電時120 MW、80%運転時40 MWを用意します。'),
    'operation': ('Start vacuum, helium cooling, magnets, then plasma heating. At 80%, heat output is 400 MW.', '真空→ヘリウム冷却→磁石→プラズマ加熱の順に入れます。80%運転で400 MWです。'),
    'safety': ('If stopped, check vacuum, helium, coolant, fuel and power. Cool the coils before restarting.', '停止したら真空・ヘリウム・冷却水・燃料・給電を確認します。コイルを冷やしてから再始動します。'),
}
for key, (en, ja) in ponder_text.items():
    EN['create_refinery_n_reactors.ponder.shared.fusion_tokamak.' + key] = en
    JA['create_refinery_n_reactors.ponder.shared.fusion_tokamak.' + key] = ja
EN['create_refinery_n_reactors.ponder.fusion_tokamak.header'] = ponder_text['title'][0]
JA['create_refinery_n_reactors.ponder.fusion_tokamak.header'] = ponder_text['title'][1]

console_ponder = {
    'title': ('Operating the electromechanical fusion desk', '電気機械式核融合制御卓を操作する'),
    'build': ('Build exactly three panels by two. The centre-front guarded red switch is the master emergency dump; the other five faces are vacuum, cryogenics, magnets, plasma and power.', '計器面を3×2で組みます。手前中央の赤い保護スイッチが緊急ダンプで、残る5面は真空・極低温・磁場・プラズマ・出力です。'),
    'link': ('Use a standard Create Display Link from a free reactor port or the motor-generator to any desk section; choose Machine Data as the source and Fusion Console as the target.', '空いている炉ポートまたは電動発電機から任意の卓面へCreate標準ディスプレイリンクを接続し、入力を機械データ、出力を核融合制御卓にします。'),
    'sequence': ('Right-click the switches in physical order: vacuum, cryogenics, magnets, plasma. Right-click the output panel to add 10 percent; sneak-right-click subtracts 10 percent.', '真空、極低温、磁場、プラズマの順に各スイッチを右クリックします。出力面は右クリックで10パーセント増、スニーク右クリックで10パーセント減です。'),
    'dump': ('Right-click the guarded master switch for an emergency magnetic dump. Sneak-right-click it to reset only after the coils are cold and almost fully discharged.', '中央の保護スイッチで緊急磁場ダンプを行います。コイルが低温でほぼ放電済みになってからスニーク右クリックで復帰できます。'),
}
for key, (en, ja) in console_ponder.items():
    EN['create_refinery_n_reactors.ponder.shared.fusion_console.' + key] = en
    JA['create_refinery_n_reactors.ponder.shared.fusion_console.' + key] = ja
EN['create_refinery_n_reactors.ponder.fusion_console.header'] = console_ponder['title'][0]
JA['create_refinery_n_reactors.ponder.fusion_console.header'] = console_ponder['title'][1]

motor_generator_ponder = {
    'title': ('Powering a tokamak from its turbine shaft', 'タービン軸でトカマクへ給電する'),
    'assembly': ('Place the motor-generator directly on the magnet feeder shaft face and a Large Flywheel directly opposite it. All three axes must line up; either missing neighbour opens the electrical feed.', '電動発電機を磁石給電盤の軸面へ直付けし、反対面へ大型フライホイールを直付けします。3部品の軸を一致させ、どちらかが欠けると給電は開放されます。'),
    'cold_start': ('Start at 256 RPM using external rotation. Provide 120 MW for charging and 40 MW for 80% operation.', '外部動力で256 RPMにします。磁石充電に120 MW、80%運転に40 MWの発電能力が必要です。'),
    'transfer': ('Once the steam turbine reaches 256 RPM with enough capacity, match rotation direction before joining its shaft. Disconnect the starting drive only after the motor-generator remains green under load.', '蒸気タービンが十分な容量で256 RPMに達したら回転方向を一致させて軸を接続します。負荷中も電動発電機が緑色を保ってから始動動力を切り離します。'),
    'observe': ("Check power status with goggles. Attach the Display Link to the generator's side.", '給電状態はゴーグルで確認します。Display Linkは電動発電機の側面へ付けます。'),
}
for key, (en, ja) in motor_generator_ponder.items():
    EN['create_refinery_n_reactors.ponder.shared.fusion_motor_generator.' + key] = en
    JA['create_refinery_n_reactors.ponder.shared.fusion_motor_generator.' + key] = ja
EN['create_refinery_n_reactors.ponder.fusion_motor_generator.header'] = motor_generator_ponder['title'][0]
JA['create_refinery_n_reactors.ponder.fusion_motor_generator.header'] = motor_generator_ponder['title'][1]

# The fluid block and the fluid type each show the fluid's own name.
for names in (EN, JA):
    for fluid in ('deuterium_gas', 'tritium_gas', 'helium_gas', 'liquid_helium'):
        for kind in ('block', 'fluid_type'): names[f'{kind}.create_refinery_n_reactors.{fluid}'] = names[f'fluid.create_refinery_n_reactors.{fluid}']

for locale, additions in [('en_us', EN), ('ja_jp', JA), ('zh_cn', EN)]:
    path = A / 'lang' / f'{locale}.json'
    language = json.loads(path.read_text())
    if locale == 'zh_cn':
        additions = {key: language.get(key, value) if '.ponder.' in key else value for key, value in additions.items()}
    language.update(additions)
    write(path, language)

print(f'Generated fusion assets: {len(blocks)} blocks, {len(EN)} translated entries.')
