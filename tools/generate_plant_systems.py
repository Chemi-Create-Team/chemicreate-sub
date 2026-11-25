#!/usr/bin/env python3
"""Photo-informed, metre-scale power-plant geometry and deterministic 16 px/m assets."""
import json
import math
import struct
import zlib
from pathlib import Path
from minecraft_style import grain
from powder_assets import MANIFEST as POWDER_ITEMS, write_powder_item
import generate_petrochemical_ponder as ponder

ROOT = Path(__file__).resolve().parents[1]
NS = 'create_refinery_n_reactors:'
A = ROOT / 'src/main/resources/assets/create_refinery_n_reactors'
D = ROOT / 'src/main/resources/data/create_refinery_n_reactors'
TEX = NS + 'block/plant_systems/'
MODEL = NS + 'block/plant_systems/'
EN, JA = {}, {}


def write(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, indent=2, ensure_ascii=False) + '\n')


def colour(base, shade=0):
    return tuple(max(0, min(255, round(c + shade))) for c in base) + (255,)


def texture(name, painter, item=False, width=16, height=16):
    path = A / 'textures' / ('item' if item else 'block/plant_systems') / (name + '.png')
    path.parent.mkdir(parents=True, exist_ok=True)
    def chunk(kind, value):
        return struct.pack('>I', len(value)) + kind + value + struct.pack('>I', zlib.crc32(kind + value) & 0xffffffff)
    data = b''.join(b'\0' + bytes(c for x in range(width) for c in (painter(x, y) or (0, 0, 0, 0))) for y in range(height))
    path.write_bytes(b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>IIBBBBB', width, height, 8, 6, 0, 0, 0))
                    + chunk(b'IDAT', zlib.compress(data, 9)) + chunk(b'IEND', b''))


def steel(x, y):
    shade = grain('plant_stainless', x, y, .7) + (10 if x < 5 else -8 if x > 11 else 0)
    if y in (0, 15): shade -= 20
    if y == 1: shade += 16
    return colour((166, 174, 173), shade)


def concrete(x, y):
    shade = grain('tower_cast_concrete', x, y, .9)
    if y == 15: shade -= 12
    if y == 0: shade += 5
    if x in (3, 12) and y in (6, 7): shade -= 5
    return colour((171, 169, 158), shade)


def panel(x, y):
    if x in (0, 15) or y in (0, 15): return colour((68, 76, 71), -10)
    if 2 <= x <= 13 and 2 <= y <= 9:
        if x in (2, 13) or y in (2, 9): return colour((91, 101, 97), -15)
        if x % 3 == 0 and y == 4: return colour((77, 78, 65))
        return colour((209, 203, 177), grain('cream_dial', x, y, .4))
    if y in (12, 13) and x in (4, 8, 12): return colour((174, 138, 68), 14 if y == 12 else -15)
    return colour((114, 127, 115), grain('instrument_rack', x, y, .7))


for name, painter in [('steel', steel), ('concrete', concrete), ('panel', panel),
                      ('refractory', lambda x, y: colour((142, 126, 103), grain('vitrification_firebrick', x, y, 1.1) - (18 if y % 8 == 7 or (x + (8 if y < 8 else 0)) % 16 == 15 else 0))),
                      ('dark', lambda x, y: colour((61, 70, 68), grain('plant_dark', x, y, .6))),
                      ('brass', lambda x, y: colour((177, 144, 76), grain('valve_brass', x, y, .8))),
                      ('tungsten', lambda x, y: colour((88, 92, 91), grain('target_tungsten', x, y, .6) - (18 if x % 4 == 0 else 0))),
                      ('glass', lambda x, y: colour((100, 144, 144), 20 if x == 3 else -9)),
                      ('red', lambda x, y: colour((151, 73, 52))), ('water', lambda x, y: colour((61, 111, 127), grain('water', x, y, 1)))]:
    texture(name, painter)


def box(lo, hi, skin='steel', front=None):
    faces = {face: {'texture': '#' + skin} for face in ('up', 'down', 'north', 'south', 'east', 'west')}
    if front: faces['north'] = {'texture': '#' + front}
    return {'from': lo, 'to': hi, 'faces': faces}


def cylinder(radius=6, y0=0, y1=16, skin='steel', centre=8):
    result = []
    for x in range(-radius, radius, 2):
        z = math.sqrt(max(0, radius * radius - (x + 1) ** 2))
        result.append(box([centre + x, y0, centre - z], [centre + min(radius, x + 2), y1, centre + z], skin))
    return result


# Extraction column storeys span 3 x 3 blocks. Each of the eight sides is about 20 px wide and takes a whole 20 x 16 side
# texture; the deck spans the 48 px octagon.
COLUMN = ((84, 91, 92), (116, 124, 124), (143, 151, 150), (168, 176, 173), (199, 205, 199))
COLUMN_DARK = ((38, 43, 44), (54, 60, 60), (72, 79, 78), (96, 103, 100))
SIGHT_GLASS = ((44, 74, 78), (72, 112, 114), (126, 168, 164))


def column_body(x, y):
    """Stainless sheet: the left edge of each of the eight faces catches the light, the right edge falls into shade."""
    if x == 0: return colour(COLUMN[3])
    if x == 19: return colour(COLUMN[1])
    cell = (x // 2, y // 2)
    if cell in ((2, 2), (7, 5), (4, 6)): return colour(COLUMN[3])
    if cell in ((6, 1), (1, 5)): return colour(COLUMN[1])
    return colour(COLUMN[2])


def storey_joint(x, y):
    """The bolted joint shared by every storey at its top and bottom edge."""
    if y == 0 or y == 15: return colour(COLUMN[0])
    if y == 1: return colour(COLUMN[4])
    if y == 14: return colour(COLUMN[1])
    return None


def settler_side(x, y):
    # Wider settling chambers at both ends: a reinforcing band and a sight glass on every face.
    joint = storey_joint(x, y)
    if joint: return joint
    if y in (3, 12): return colour(COLUMN[1] if y == 12 else COLUMN[4])
    if 7 <= x <= 12 and 5 <= y <= 10:
        if x in (7, 12) or y in (5, 10): return colour(COLUMN_DARK[1])
        if (x, y) in ((8, 6), (9, 6), (8, 7)): return colour(SIGHT_GLASS[2])
        return colour(SIGHT_GLASS[1] if x + y < 18 else SIGHT_GLASS[0])
    return column_body(x, y)


def pulse_side(x, y):
    joint = storey_joint(x, y)
    if joint: return joint
    if y in (3, 12) and x in (4, 15): return colour(COLUMN_DARK[1])
    return column_body(x, y)


def flange(x, y):
    # Only rows 6-9 show: a dark steel band with evenly spaced bolt heads.
    if y <= 6: return colour(COLUMN_DARK[3])
    if y >= 9: return colour(COLUMN_DARK[0])
    if x % 5 in (2, 3): return colour(COLUMN[3] if (x % 5, y) == (2, 7) else COLUMN[1])
    return colour(COLUMN_DARK[2])


def column_top(x, y):
    # The deck seen from above: a bolted rim along the octagon and a round lid in the middle.
    dx, dy = abs(x - 23.5), abs(y - 23.5)
    m = max(dx, dy, (dx + dy) / math.sqrt(2))
    r = math.hypot(dx, dy)
    if m > 23: return colour(COLUMN[0])
    if m > 21.5:
        angle = math.degrees(math.atan2(y - 23.5, x - 23.5)) % 22.5
        if 22 < m < 23 and (angle < 4 or angle > 18.5): return colour(COLUMN_DARK[1])
        return colour(COLUMN[4] if x + y < 47 else COLUMN[1])
    if 9 < r <= 10.5: return colour(COLUMN_DARK[1])
    if r <= 9:
        if 19 <= x <= 28 and y in (23, 24): return colour(COLUMN[1] if y == 24 else COLUMN[4])
        return colour(COLUMN[3] if x + y < 44 else COLUMN[2])
    cell = (x // 2 % 8, y // 2 % 8)
    return colour(COLUMN[3] if cell in ((1, 2), (5, 6)) else COLUMN[1] if cell in ((6, 1), (2, 5)) else COLUMN[2])


texture('extraction_column_settler_side', settler_side, width=20)
texture('extraction_column_pulse_side', pulse_side, width=20)
texture('extraction_column_flange', flange, width=20)
texture('extraction_column_top', column_top, width=48, height=48)


def span(t):
    """Model coordinate across the 48 px column (-16..32) to a 0..16 UV coordinate."""
    return round((t + 16) / 3, 4)


def octagon(width, y0, y1, skin='column', top='column_top'):
    """A regular octagon centred on the block: a cross of three boxes and four 45-degree boxes filling the chamfers."""
    h, a = width / 2, round(width * (math.sqrt(2) - 1), 4)
    def face(texture, uv): return {'texture': '#' + texture, 'uv': uv}
    def deck(x0, z0, x1, z1): return [span(x0), span(z0), span(x1), span(z1)]
    side = face(skin, [0, 16 - y1, 16, 16 - y0])
    result = [{'from': [8 - a/2, y0, 8 - h], 'to': [8 + a/2, y1, 8 + h], 'faces': {'north': side, 'south': side,
        'up': face(top, deck(8 - a/2, 8 - h, 8 + a/2, 8 + h)), 'down': face(top, deck(8 - a/2, 8 - h, 8 + a/2, 8 + h))}}]
    for x0, x1, outer in ((8 - h, 8 - a/2, 'west'), (8 + a/2, 8 + h, 'east')):
        result.append({'from': [x0, y0, 8 - a/2], 'to': [x1, y1, 8 + a/2], 'faces': {outer: side,
            'up': face(top, deck(x0, 8 - a/2, x1, 8 + a/2)), 'down': face(top, deck(x0, 8 - a/2, x1, 8 + a/2))}})
    for cx in (8 - a/2, 8 + a/2):
        for cz in (8 - a/2, 8 + a/2):
            # Decks sit a hair inside the cross so the overlapping tops do not flicker.
            result.append({'from': [cx - a/2, y0 + .01, cz - a/2], 'to': [cx + a/2, y1 - .01, cz + a/2],
                           'rotation': {'angle': 45, 'axis': 'y', 'origin': [cx, y0, cz]},
                           'faces': {**{d: side for d in ('north', 'south', 'east', 'west')},
                                     'up': face(top, deck(cx - a/2, cz - a/2, cx + a/2, cz + a/2)), 'down': face(top, deck(cx - a/2, cz - a/2, cx + a/2, cz + a/2))}})
    return result


def model(name, elements):
    textures = {'particle': TEX + 'steel', **{s: TEX + s for s in ('steel', 'concrete', 'panel', 'dark', 'refractory', 'brass', 'tungsten', 'glass', 'red', 'water')}}
    if name in ('extraction_column_shell', 'extraction_pulse_plate', 'extraction_column_outlet'):
        textures.update(column=TEX + ('extraction_column_settler_side' if name == 'extraction_column_shell' else 'extraction_column_pulse_side'),
                        column_top=TEX + 'extraction_column_top', flange=TEX + 'extraction_column_flange')
    data = {'parent': 'minecraft:block/block', 'textures': textures, 'elements': elements}
    # Faces reaching into the neighbouring ring cells would take their occlusion from the wrong blocks.
    if 'column' in textures: data = {'ambientocclusion': False, **data}
    write(A / 'models/block/plant_systems' / (name + '.json'), data)


def block(name, elements, title, ja, facing=False):
    model(name, elements)
    states = {'': {'model': MODEL + name}}
    if facing: states = {f'facing={d}': {'model': MODEL + name, 'y': r} for d, r in [('north', 0), ('east', 90), ('south', 180), ('west', 270)]}
    write(A / 'blockstates' / (name + '.json'), {'variants': states})
    write(A / 'models/item' / (name + '.json'), {'parent': MODEL + name})
    EN['block.create_refinery_n_reactors.' + name], JA['block.create_refinery_n_reactors.' + name] = title, ja
    write(D / 'loot_table/blocks' / (name + '.json'), {'type': 'minecraft:block', 'pools': [{'rolls': 1,
        'entries': [{'type': 'minecraft:item', 'name': NS + name}], 'conditions': [{'condition': 'minecraft:survives_explosion'}]}]})


BLOCKS = [
    ('cooling_tower_shell', [box([0, 0, 0], [16, 16, 16], 'concrete')], 'Cooling Tower Concrete Shell', '冷却塔コンクリート外殻'),
    ('cooling_tower_basin', [box([0, 0, 0], [16, 16, 16], 'concrete')], 'Cooling Tower Basin', '冷却塔集水槽'),
    ('cooling_tower_fill', [box([0, 0, 0], [16, 2, 16], 'dark')] + [box([x, 2, 0], [x+1, 16, 16], 'dark') for x in range(0, 16, 3)], 'Cooling Tower Fill Pack', '冷却塔充填材'),
    ('cooling_tower_distributor', [box([0, 6, 6], [16, 10, 10]), box([6, 6, 0], [10, 10, 16])] + [box([x, 3, 6], [x+2, 6, 10], 'brass') for x in (2, 12)], 'Cooling Tower Spray Distributor', '冷却塔散水樋'),
    ('natural_draft_cooling_tower', [box([0, 0, 0], [16, 16, 16], 'concrete'), box([2, 4, -.5], [14, 14, .5], 'panel')], 'Natural Draft Cooling Tower', '自然通風冷却塔制御部'),
    ('condenser_shell', [box([0, 0, 0], [16, 16, 16])], 'Surface Condenser Shell', '表面復水器外殻'),
    ('condenser_tube_bundle', [box([0, 0, 0], [16, 2, 16], 'dark')] + [box([x, 3, 0], [x+1, 4, 16], 'brass') for x in range(2, 15, 3)] + [box([x, 11, 0], [x+1, 12, 16], 'brass') for x in range(2, 15, 3)], 'Condenser Tube Bundle', '復水器伝熱管束'),
    ('condenser_waterbox', [box([0, 0, 0], [16, 16, 16]), box([2, 2, -1], [14, 14, 1]), box([3, 6, -3], [7, 10, -1], 'brass'), box([9, 6, -3], [13, 10, -1], 'brass')], 'Condenser Cooling Waterbox', '復水器冷却水箱'),
    ('pulse_extraction_column', [box([1, 0, 1], [15, 4, 15]), box([3, 4, 3], [13, 11, 13], 'dark'), box([6, 11, 6], [10, 16, 10], 'brass')], 'Pulse Solvent Extraction Column', 'パルス式溶媒抽出塔'),
    ('extraction_column_shell', octagon(48, 0, 16), 'Extraction Column Settler Storey', '抽出塔分離段'),
    ('extraction_pulse_plate', octagon(40, 0, 16) + octagon(48, 6, 10, 'flange', 'flange'), 'Extraction Column Pulse Storey', '抽出塔脈動段'),
    ('extraction_column_outlet', octagon(40, 0, 16) + octagon(48, 6, 10, 'flange', 'flange') + [box([28, 1, 6], [32, 5, 10], 'brass')],
     'Extraction Column Product Storey', '抽出塔生成物出口段'),
    ('vitrification_melter', [box([2, 0, 4], [14, 13, 15]), box([3, 3, 3], [13, 12, 4], 'panel'), box([2, 13, 3], [5, 16, 6], 'brass'), box([11, 13, 3], [14, 16, 6], 'brass')], 'Joule-Heated Vitrification Melter', '電気式ガラス固化炉'),
    ('vitrification_electrode', [box([0, 0, 0], [16, 16, 16], 'refractory'), box([6, 0, 6], [10, 16, 10], 'dark')], 'Vitrification Melter Electrode', 'ガラス固化炉電極・耐火炉心'),
    ('pid_controller', [box([3, 0, 4], [13, 16, 13]), box([3, 3, 3], [13, 15, 4], 'panel')], 'Analogue PID Controller', 'アナログPID調節器'),
    ('pid_instrument_rack', [box([3, 0, 4], [13, 16, 13]), box([3, 1, 3], [13, 14, 4], 'panel')], 'PID Instrument Rack', 'PID計器ラック上段'),
]
for name, elements, en, ja in BLOCKS:
    block(name, elements, en, ja, name in ('condenser_waterbox', 'pulse_extraction_column', 'vitrification_melter', 'pid_instrument_rack'))
for name, en, ja in [('condenser_cooling_inlet', 'Condenser Cooling Water Inlet', '復水器循環水入口'), ('condenser_cooling_outlet', 'Condenser Cooling Water Outlet', '復水器循環水出口')]:
    block(name, [box([0, 0, 0], [16, 16, 16]), box([3, 3, -1], [13, 13, 1]), box([5, 5, -2], [11, 11, 0], 'brass')], en, ja, True)

for name, en, ja, side in [('cooling_tower_hot_inlet', 'Cooling Tower Hot Inlet', '冷却塔温水入口', 'north'),
                           ('cooling_tower_cold_outlet', 'Cooling Tower Cold Outlet', '冷却塔冷水出口', 'south'),
                           ('cooling_tower_makeup_inlet', 'Cooling Tower Makeup Inlet', '冷却塔補給水入口', 'east')]:
    block(name, [box([0, 0, 0], [16, 16, 16], 'concrete'), box([4, 4, 0], [12, 12, 2]), box([6, 6, -1], [10, 10, 1], 'brass')], en, ja)
    if side != 'north': write(A / 'blockstates' / (name + '.json'), {'variants': {'': {'model': MODEL + name, 'y': 180 if side == 'south' else 90}}})

# The frame surrounds a separate refractory vessel, as in the DOE Melter 4 photograph.
frame_parts = [
    [box([6, 0, 6], [10, 16, 10])],
    [box([6, 5, 0], [10, 11, 16])],
    [box([0, 5, 6], [16, 11, 10])],
    [box([0, 0, 0], [16, 16, 16], 'refractory'), box([0, 14, 0], [16, 16, 16])],
    cylinder(8),
    [box([0, 0, 0], [16, 4, 16]), box([4, 4, 4], [12, 7, 12]), box([6, 7, 6], [10, 16, 10], 'brass')],
    [box([5, 5, 0], [8, 8, 16]), box([10, 10, 0], [13, 13, 16])],
    [box([0, 0, 6], [16, 3, 10]), box([5, 3, 6], [8, 16, 10])],
]
for i, geometry in enumerate(frame_parts): model('melter_frame_' + str(i), geometry)
block('vitrification_refractory_shell', frame_parts[0], 'Vitrification Melter Frame and Shell', 'ガラス固化炉フレーム・炉壁')
write(A / 'blockstates/vitrification_refractory_shell.json', {'variants': {
    f'part={p},facing={f}': {'model': MODEL + 'melter_frame_' + str(p), 'y': r}
    for p in range(8) for f, r in [('north', 0), ('east', 90), ('south', 180), ('west', 270)]}})
write(A / 'models/item/vitrification_refractory_shell.json', {'parent': MODEL + 'melter_frame_3'})

for name, en, ja in [('process_transmitter', 'Tank Level / Pressure Transmitter', '水位・圧力伝送器'),
                     ('control_valve_actuator', 'Diaphragm Control Valve Actuator', 'ダイアフラム式調節弁アクチュエータ')]:
    block(name, [], en, ja)
    states = {}
    for indicator in range(16):
        if name == 'process_transmitter':
            geometry = [box([3, 1, 9], [13, 15, 14]), box([4, 3, 8], [12, 13, 9], 'panel'),
                        box([7, 3, 7.7], [9, 3.6 + indicator * .55, 8], 'water'), box([7, 6, 14], [9, 10, 16], 'brass')]
        else:
            geometry = cylinder(6, 10, 13) + [box([4, 0, 4], [6, 10, 6]), box([10, 0, 10], [12, 10, 12]),
                box([7, 0, 7], [9, 10, 9], 'brass'), box([9, 2 + indicator/4, 7], [12, 3 + indicator/4, 9], 'red'), box([3, 0, 3], [13, 2, 13])]
        model(name + '_' + str(indicator), geometry)
        for facing, rotation in [('north', {'x': 90}), ('south', {'x': 270}), ('west', {'x': 90, 'y': 270}),
                                 ('east', {'x': 90, 'y': 90}), ('up', {}), ('down', {'x': 180})]:
            if name == 'process_transmitter': rotation = {'north': {}, 'south': {'y': 180}, 'west': {'y': 270}, 'east': {'y': 90}, 'up': {'x': 270}, 'down': {'x': 90}}[facing]
            states[f'facing={facing},indicator={indicator}'] = {'model': MODEL + name + '_' + str(indicator), **rotation}
    write(A / 'blockstates' / (name + '.json'), {'variants': states})
    write(A / 'models/item' / (name + '.json'), {'parent': MODEL + name + '_0'})

# The PID pointer shows the actual controller output on its lower dial.
states = {}
for indicator in range(16):
    elements = BLOCKS[14][1].copy()
    angle = (-135 + 270 * indicator/15) * math.pi/180
    for dot in range(1, 5):
        x, y = 8 + math.sin(angle)*dot*.65, 10 + math.cos(angle)*dot*.65
        elements.append(box([x-.3, y-.3, 2.7], [x+.3, y+.3, 3], 'red'))
    model('pid_' + str(indicator), elements)
    for facing, rotation in [('north', {}), ('south', {'y': 180}), ('east', {'y': 90}), ('west', {'y': 270}), ('up', {'x': 270}), ('down', {'x': 90})]:
        states[f'facing={facing},indicator={indicator}'] = {'model': MODEL + 'pid_' + str(indicator), **rotation}
write(A / 'blockstates/pid_controller.json', {'variants': states})
write(A / 'models/item/pid_controller.json', {'parent': MODEL + 'pid_0'})
write(A / 'blockstates/extraction_column_outlet.json', {'variants': {f'tank={i}': {'model': MODEL + 'extraction_column_outlet'} for i in range(2, 6)}})
# Storey items are drawn small enough for a slot; the ring around a placed storey draws nothing and drops nothing.
for name in ('extraction_column_shell', 'extraction_pulse_plate', 'extraction_column_outlet'):
    write(A / 'models/item' / (name + '.json'), {'parent': MODEL + name, 'display': {
        'gui': {'rotation': [30, 225, 0], 'translation': [0, 0, 0], 'scale': [.3, .3, .3]},
        'ground': {'rotation': [0, 0, 0], 'translation': [0, 3, 0], 'scale': [.15, .15, .15]},
        'fixed': {'rotation': [0, 0, 0], 'translation': [0, 0, 0], 'scale': [.3, .3, .3]},
        'thirdperson_righthand': {'rotation': [75, 45, 0], 'translation': [0, 2.5, 0], 'scale': [.18, .18, .18]},
        'thirdperson_lefthand': {'rotation': [75, 45, 0], 'translation': [0, 2.5, 0], 'scale': [.18, .18, .18]},
        'firstperson_righthand': {'rotation': [0, 45, 0], 'translation': [0, 0, 0], 'scale': [.2, .2, .2]},
        'firstperson_lefthand': {'rotation': [0, 225, 0], 'translation': [0, 0, 0], 'scale': [.2, .2, .2]}}})
write(A / 'models/block/plant_systems/extraction_column_structure.json', {'textures': {'particle': TEX + 'steel'}})
write(A / 'blockstates/extraction_column_structure.json', {'variants': {f'facing={d}': {'model': MODEL + 'extraction_column_structure'} for d in ('north', 'east', 'south', 'west')}})
EN['block.create_refinery_n_reactors.extraction_column_structure'], JA['block.create_refinery_n_reactors.extraction_column_structure'] = 'Extraction Column Structure', '抽出塔構造部'

for name, en, ja in [('empty_waste_canister', 'Empty Waste Canister (3 m)', '空の固化体容器（高さ3 m）'),
                     ('vitrified_waste_canister', 'Vitrified High-Level Waste Canister', '高レベルガラス固化体容器')]:
    block(name, [], en, ja)
    for section in range(3):
        elements = cylinder(5)
        if section == 0: elements += cylinder(5, 0, 2, 'dark')
        if section == 2: elements += cylinder(3, 14, 16, 'dark') + [box([5, 15, 5], [11, 16, 11])]
        if name.startswith('vitrified') and section == 1: elements += [box([5, 5, 2.7], [11, 10, 3.2], 'brass')]
        model(name + '_' + str(section), elements)
    write(A / 'blockstates' / (name + '.json'), {'variants': {f'section={i}': {'model': MODEL + name + '_' + str(i)} for i in range(3)}})
    full = cylinder(5, 0, 30) + cylinder(3, 30, 32, 'dark')
    model(name + '_item', full)
    write(A / 'models/item' / (name + '.json'), {'parent': MODEL + name + '_item', 'display': {'gui': {'rotation': [20, -35, 0], 'translation': [0, -2, 0], 'scale': [.32, .48, .32]}}})
    loot = json.loads((D / 'loot_table/blocks' / (name + '.json')).read_text())
    loot['pools'][0]['conditions'].append({'condition': 'minecraft:block_state_property', 'block': NS+name, 'properties': {'section': '0'}})
    write(D / 'loot_table/blocks' / (name + '.json'), loot)

MATERIALS = {
    'chopped_spent_fuel': ('Chopped Spent Fuel', 'せん断済み使用済み燃料', (95, 88, 66)),
    'spent_cladding_hulls': ('Spent Cladding Hulls', '使用済み被覆管片', (125, 139, 134)),
    'plutonium_dioxide': ('Plutonium Dioxide', '二酸化プルトニウム', (123, 117, 72)),
    'depleted_uranium_dioxide': ('Depleted Uranium Dioxide', '劣化二酸化ウラン', (99, 103, 84)),
    'mox_powder': ('MOX Powder', 'MOX混合粉末', (99, 92, 70)),
    'green_mox_pellet': ('Green MOX Pellet', 'MOX成形ペレット', (130, 126, 105)),
    'mox_pellet': ('Sintered MOX Pellet', 'MOX焼結ペレット', (75, 80, 74)),
    'mox_fuel_rod': ('MOX Fuel Assembly', 'MOX燃料集合体', (164, 170, 143)),
    'borosilicate_glass_frit': ('Borosilicate Glass Frit', 'ホウケイ酸ガラスフリット', (152, 172, 164)),
    'borax': ('Borax', 'ホウ砂', (214, 210, 195)),
    'gypsum': ('Gypsum', '石膏', (184, 178, 160)),
    'tungsten_divertor_cassette': ('Tungsten Divertor Cassette', 'タングステンダイバータカセット', (86, 95, 92)),
    'spent_blanket_cartridge': ('Spent Blanket Cartridge', '使用済みブランケットカートリッジ', (100, 102, 81)),
    'spent_divertor_cassette': ('Spent Divertor Cassette', '使用済みダイバータカセット', (78, 76, 66)),
}
for name, (en, ja, base) in MATERIALS.items():
    def paint(x, y, name=name, base=base):
        if 'rod' in name:
            return colour(base, 25 if x % 3 == 0 else -16) if 4 <= x <= 11 and 1 <= y <= 14 else None
        if 'cartridge' in name or 'cassette' in name:
            if 2 <= x <= 13 and 3 <= y <= 13: return colour(base, 25 if x in (2, 13) or y in (3, 13) else grain(name, x, y) - (18 if x % 3 == 0 else 0))
            return None
        if 'pellet' in name:
            return colour(base, 25 if x < 6 else -20 if x > 10 else 0) if 4 <= x <= 11 and 4 <= y <= 13 else None
        return colour(base, grain(name, x, y, 2) + (12 if x+y<15 else -9)) if 4 <= y <= 13 and abs(x-7.5) < (y-2)*.65 else None
    if name in POWDER_ITEMS['powders']:
        write_powder_item(name)
    else:
        texture(name, paint, True)
        write(A / 'models/item' / (name + '.json'), {'parent': 'minecraft:item/generated', 'textures': {'layer0': NS+'item/'+name}})
    EN['item.create_refinery_n_reactors.'+name], JA['item.create_refinery_n_reactors.'+name] = en, ja

# Service loads have physical, metre-scale crane models, rather than an enlarged item sprite.
model('blanket_load', [box([0, 0, 0], [16, 22, 8]), box([1, 1, -.3], [15, 21, 0], 'tungsten'), box([4, 22, 2], [12, 25, 6], 'brass')])
model('divertor_load', [box([0, 0, 1], [16, 4, 14], 'tungsten')])
model('divertor_load_half', [box([0, 0, 1], [8, 4, 14], 'tungsten')])
model('divertor_load_left', [box([0, 0, 1], [8, 29.6, 14], 'tungsten')])
model('divertor_load_right', [box([0, 0, 1], [12, 26, 14], 'tungsten')])
model('pulse_plunger', [box([5, 0, 5], [11, 4, 11], 'brass'), box([7, 4, 7], [9, 16, 9])])
model('pour_stream', [box([7, 0, 7], [9, 16, 9], 'red')])


def fluid(name, amount): return {'type': 'neoforge:single', 'fluid': name if ':' in name else NS+name, 'amount': amount}
def item(name): return {'item': name if ':' in name else NS+name}
def result(name, amount=None, count=1): return {'id': name if ':' in name else NS+name, **({'amount': amount} if amount is not None else {'count': count})}
def recipe(kind, name, ingredients, results, heat=None, ticks=200):
    data = {'type': ('create:' if kind in ('mixing', 'pressing', 'cutting', 'milling') else NS)+kind, 'ingredients': ingredients, 'results': results, 'processing_time': ticks}
    if kind == 'pressing': data.pop('processing_time')
    if heat: data['heat_requirement'] = heat
    write(D / 'recipe' / kind / (name+'.json'), data)


recipe('cutting', 'spent_fuel_shearing', [item('spent_fuel_rod')], [result('chopped_spent_fuel'), result('spent_cladding_hulls')], ticks=200)
recipe('pressure_reaction', 'spent_fuel_reprocessing', [item('chopped_spent_fuel'), fluid('nitric_acid', 4000)], [result('dissolved_spent_fuel', 4000)], 'heated', 400)
recipe('solvent_extraction', 'purex_partition', [fluid('dissolved_spent_fuel', 4000), fluid('tbp_extraction_solvent', 1000)],
       [result('uranyl_nitrate_solution', 3500), result('plutonium_nitrate_solution', 100), result('high_level_raffinate', 400), result('tbp_extraction_solvent', 1000)], ticks=400)
recipe('pressure_reaction', 'plutonium_oxide_recovery', [fluid('plutonium_nitrate_solution', 100)], [result('plutonium_dioxide'), result('nitrogen_dioxide', 100), result('oxygen_gas', 25)], 'heated', 300)
recipe('pressure_reaction', 'hydrogen_fluoride_from_depleted_uranium_hexafluoride', [fluid('depleted_uranium_hexafluoride', 250), fluid('minecraft:water', 250)], [result('depleted_uranium_dioxide'), result('hydrogen_fluoride', 500)], 'heated')
recipe('mixing', 'mox_blending', [item('plutonium_dioxide')] + [item('depleted_uranium_dioxide')]*7, [result('mox_powder', count=8)])
recipe('pressing', 'green_mox_pellet', [item('mox_powder')], [result('green_mox_pellet')], ticks=100)
recipe('pressure_reaction', 'mox_sintering', [item('green_mox_pellet'), fluid('hydrogen_gas', 100)], [result('mox_pellet')], 'superheated', 300)
recipe('pressure_reaction', 'borax_from_brine', [fluid('brine', 8000)], [result('borax'), result('salt', count=4)], 'superheated', 400)
recipe('mixing', 'borosilicate_glass_frit', [item('minecraft:quartz')]*3 + [item('borax'), item('minecraft:glass'), item('minecraft:calcite')], [result('borosilicate_glass_frit', count=6)], 'superheated', 200)
recipe('vitrification', 'high_level_waste_glass', [fluid('high_level_raffinate', 1600)] + [item('borosilicate_glass_frit')]*4 + [item('empty_waste_canister')], [result('vitrified_waste_canister')], ticks=1200)
recipe('pressure_reaction', 'phosphate_acidulation', [item('minecraft:bone_meal')]*3 + [fluid('sulfuric_acid', 1000), fluid('minecraft:water', 250)], [result('phosphoric_acid', 1000), result('gypsum', count=3)], 'heated')
recipe('pressure_reaction', 'butyraldehyde_oxo_synthesis', [fluid('propylene', 1000), fluid('synthesis_gas', 1000)], [result('butyraldehyde', 1000)], 'heated', 300)
recipe('pressure_reaction', 'butanol_hydrogenation', [fluid('butyraldehyde', 500), fluid('hydrogen_gas', 500)], [result('butanol', 500)], 'heated')
recipe('pressure_reaction', 'tributyl_phosphate_esterification', [fluid('phosphoric_acid', 250), fluid('butanol', 750)], [result('tributyl_phosphate', 750), result('minecraft:water', 250)], 'heated', 300)
recipe('mixing', 'extraction_solvent', [fluid('tributyl_phosphate', 300), fluid('naphtha', 700)], [result('tbp_extraction_solvent', 1000)])


def shaped(name, pattern, keys, count=1):
    write(D / 'recipe' / (name + '.json'), {'type': 'minecraft:crafting_shaped', 'pattern': pattern,
        'key': {k: v if isinstance(v, dict) else item(v) for k, v in keys.items()}, 'result': result(name, count=count)})


shaped('mox_fuel_rod', ['PPP', 'SPS', 'PPP'], {'P': 'mox_pellet', 'S': 'zircaloy_cladding_tube'})
shaped('empty_waste_canister', [' S ', 'S S', ' S '], {'S': 'stainless_steel_sheet'})
shaped('tungsten_divertor_cassette', ['TTT', 'CBC', 'SSS'], {'T': 'fusion_divertor', 'C': 'create:copper_sheet', 'B': 'fusion_reinforced_casing', 'S': 'stainless_steel_sheet'})
shaped('cooling_tower_shell', ['SSS', 'SIS', 'SSS'], {'S': 'minecraft:stone', 'I': 'minecraft:iron_ingot'}, 8)
shaped('cooling_tower_basin', ['SSS', 'SWS', 'SSS'], {'S': 'cooling_tower_shell', 'W': 'minecraft:water_bucket'}, 8)
shaped('cooling_tower_fill', ['IRI', 'R R', 'IRI'], {'I': 'minecraft:iron_bars', 'R': 'industrial_resin'}, 8)
shaped('cooling_tower_distributor', [' P ', 'PBP', ' P '], {'P': 'create:fluid_pipe', 'B': 'create:brass_sheet'}, 4)
shaped('natural_draft_cooling_tower', ['ETE', 'RCR', 'SWS'], {'E': 'create:electron_tube', 'T': 'create:fluid_tank', 'R': 'minecraft:redstone', 'C': 'create:brass_casing', 'S': 'stainless_steel_sheet', 'W': 'cooling_tower_basin'})
shaped('pid_controller', ['ETE', 'RCR', 'SLS'], {'E': 'create:electron_tube', 'T': 'create:precision_mechanism', 'R': 'minecraft:redstone', 'C': 'create:brass_casing', 'S': 'stainless_steel_sheet', 'L': 'minecraft:lever'})
shaped('vitrification_melter', ['CEC', 'SPS', 'SRS'], {'C': 'create:copper_sheet', 'E': 'create:electron_tube', 'S': 'stainless_steel_sheet', 'P': 'vitrification_electrode', 'R': 'minecraft:redstone'})
shaped('pulse_extraction_column', ['SPS', 'ACA', 'SRS'], {'S': 'stainless_steel_sheet', 'P': 'create:mechanical_pump', 'A': 'create:shaft', 'C': {'tag': NS + 'machine_casings'}, 'R': 'minecraft:redstone'})
shaped('condenser_shell', ['SSS', 'SPS', 'SSS'], {'S': 'stainless_steel_sheet', 'P': 'create:fluid_pipe'}, 8)
shaped('condenser_tube_bundle', ['PPP', 'PCP', 'PPP'], {'P': 'create:fluid_pipe', 'C': 'create:copper_sheet'}, 8)
shaped('condenser_waterbox', ['SPS', 'CTC', 'SPS'], {'S': 'stainless_steel_sheet', 'P': 'create:mechanical_pump', 'C': 'condenser_shell', 'T': 'condenser_tube_bundle'})
shaped('condenser_cooling_inlet', [' C ', 'PSP', ' B '], {'C': 'minecraft:blue_dye', 'P': 'create:fluid_pipe', 'S': 'condenser_shell', 'B': 'create:brass_sheet'}, 2)
shaped('condenser_cooling_outlet', [' C ', 'PSP', ' B '], {'C': 'minecraft:red_dye', 'P': 'create:fluid_pipe', 'S': 'condenser_shell', 'B': 'create:brass_sheet'}, 2)
def mechanical(name, pattern, keys):
    write(D / 'recipe' / (name + '.json'), {'type': 'create:mechanical_crafting', 'accept_mirrored': True, 'category': 'misc', 'pattern': pattern,
        'key': {k: item(v) for k, v in keys.items()}, 'result': result(name)})


mechanical('extraction_column_shell', [' SSS ', 'SGTGS', ' SSS '], {'S': 'stainless_steel_sheet', 'G': 'industrial_glass', 'T': 'create:fluid_tank'})
mechanical('extraction_pulse_plate', [' SIS ', 'SPMPS', ' SIS '], {'S': 'stainless_steel_sheet', 'I': 'minecraft:iron_bars', 'P': 'create:fluid_pipe', 'M': 'create:mechanical_piston'})
mechanical('extraction_column_outlet', [' SIS ', 'SPMPV', ' SIS '], {'S': 'stainless_steel_sheet', 'I': 'minecraft:iron_bars', 'P': 'create:fluid_pipe', 'M': 'create:mechanical_piston', 'V': 'create:fluid_valve'})
shaped('vitrification_electrode', ['CCC', 'SBS', 'CCC'], {'C': 'create:copper_sheet', 'S': 'stainless_steel_sheet', 'B': 'minecraft:bricks'}, 4)
shaped('pid_instrument_rack', ['SDS', 'EGE', 'SRS'], {'S': 'stainless_steel_sheet', 'D': 'create:display_board', 'E': 'create:electron_tube', 'G': 'industrial_glass', 'R': 'minecraft:redstone'})
for name, dye in [('cooling_tower_hot_inlet', 'minecraft:red_dye'), ('cooling_tower_cold_outlet', 'minecraft:blue_dye'), ('cooling_tower_makeup_inlet', 'minecraft:light_blue_dye')]:
    shaped(name, [' D ', 'PBP', ' S '], {'D': dye, 'P': 'create:fluid_pipe', 'B': 'cooling_tower_basin', 'S': 'stainless_steel_sheet'}, 4)
shaped('process_transmitter', [' G ', 'SCS', ' R '], {'G': 'industrial_glass', 'S': 'stainless_steel_sheet', 'C': 'minecraft:comparator', 'R': 'minecraft:redstone'})
shaped('control_valve_actuator', ['RRR', 'SPS', ' A '], {'R': 'industrial_resin', 'S': 'stainless_steel_sheet', 'P': 'create:precision_mechanism', 'A': 'create:shaft'})
shaped('vitrification_refractory_shell', ['SSS', 'SBS', 'SSS'], {'S': 'stainless_steel_sheet', 'B': 'minecraft:bricks'}, 8)

for tag in ('minecraft/tags/block/mineable/pickaxe.json', 'create/tags/block/non_movable.json'):
    path = ROOT / 'src/main/resources/data' / tag
    data = json.loads(path.read_text())
    for name in [r[0] for r in BLOCKS] + ['process_transmitter', 'control_valve_actuator', 'vitrification_refractory_shell',
                 'empty_waste_canister', 'vitrified_waste_canister', 'cooling_tower_hot_inlet', 'cooling_tower_cold_outlet', 'cooling_tower_makeup_inlet', 'condenser_cooling_inlet', 'condenser_cooling_outlet']:
        if NS + name not in data['values']: data['values'].append(NS + name)
    write(path, data)

write(ROOT / 'src/main/resources/data/neoforge/tags/item/fuel_rods.json', {'replace': False, 'values': [NS+'fuel_rod', NS+'mox_fuel_rod']})

FLUIDS = {
    'dissolved_spent_fuel': ('Dissolved Spent Fuel', '使用済み燃料溶解液'), 'tbp_extraction_solvent': ('30% TBP Extraction Solvent', '30%TBP抽出溶媒'),
    'plutonium_nitrate_solution': ('Plutonium Nitrate Solution', '硝酸プルトニウム溶液'), 'high_level_raffinate': ('High-Level Raffinate', '高レベル放射性廃液'),
    'phosphoric_acid': ('Phosphoric Acid', 'リン酸'), 'butyraldehyde': ('Butyraldehyde', 'ブチルアルデヒド'),
    'butanol': ('n-Butanol', 'n-ブタノール'), 'tributyl_phosphate': ('Tributyl Phosphate', 'リン酸トリブチル'),
}
for name, (en, ja) in FLUIDS.items():
    for prefix in ('fluid', 'fluid_type'): EN[f'{prefix}.create_refinery_n_reactors.{name}'], JA[f'{prefix}.create_refinery_n_reactors.{name}'] = en, ja

STRINGS = {
    'tower.state': ('Natural draft tower: %s', '自然通風冷却塔：%s'), 'tower.power': ('Heat rejected: %s MW | inlet/air: %s °C', '放熱量：%s MW｜入口・外気：%s ℃'),
    'tower.water': ('Hot / cold / makeup: %s / %s / %s mB', '温水／冷水／補給水：%s／%s／%s mB'), 'structure_at': ('Check structure at %s', '構造確認箇所：%s'),
    'condenser.state': ('Condenser formed: %s | turbine connected: %s', '復水器形成：%s｜タービン接続：%s'),
    'condenser.water': ('Cooling water inlet / outlet: %s / %s mB', '冷却水入口／出口：%s／%s mB'),
    'control.state': ('Process instrument: %s', 'プロセス計器：%s'), 'control.reading': ('Measured: %s %s | output: %s%%', '測定値：%s %s｜出力：%s%%'),
    'control.tuning': ('SP: %s%% | P: %s | Ti: %s s | Td: %s s', '設定：%s%%｜P：%s｜Ti：%s秒｜Td：%s秒'),
    'control.action': ('Reverse: %s | automatic: %s', '逆作用：%s｜自動：%s'),
    'control.settings': ('SP %s%% / P %s / Ti %s / Td %s / reverse %s / auto %s', '設定%s%%／P %s／Ti %s／Td %s／逆作用%s／自動%s'),
    'fuel_cycle.state': ('Fuel cycle: %s', '燃料サイクル：%s'), 'fuel_cycle.process': ('Progress: %s ticks | %s °C | %s kW', '進捗：%s tick｜%s ℃｜%s kW'),
    'fuel_cycle.inputs': ('Feed / solvent: %s / %s mB', '原料／溶媒：%s／%s mB'),
    'fuel_cycle.outputs': ('U / Pu / HLW / solvent: %s / %s / %s / %s mB | %s items', 'U／Pu／廃液／溶媒：%s／%s／%s／%s mB｜製品%s個'),
}
for key, (en, ja) in STRINGS.items(): EN['plant_systems.'+key], JA['plant_systems.'+key] = en, ja
STATUSES = {
    'validating': ('Validating the full tower', '塔全体を検証中'), 'structure': ('Incomplete structure', '構造未完成'), 'unloaded': ('Load every structure chunk', '構造内のチャンク未読込'),
    'idle': ('Ready', '待機中'), 'feed': ('Supply process feed', '原料を供給してください'), 'output': ('Clear the product outlet', '生成物出口が満杯です'),
    'ambient': ('Ambient wet bulb is too warm', '外気湿球温度が高すぎます'), 'makeup': ('Cooling; supply evaporation makeup', '冷却中・蒸発分の補給水が必要'),
    'cooling': ('Cooling by natural draft', '自然通風で冷却中'), 'signal': ('Waiting for a measured signal', '測定信号を待機中'),
    'automatic': ('PID automatic', 'PID自動制御中'), 'manual': ('Manual output', '手動出力'), 'actuator': ('Valve actuator connected', '弁アクチュエータ接続済み'),
    'measuring': ('Measuring the attached vessel', '取付先を測定中'), 'power': ('Supply electric power', '電力を供給してください'), 'heating': ('Heating the refractory vessel', '耐火炉を昇温中'),
    'speed': ('Supply at least 32 RPM', '32 RPM以上の回転が必要'), 'extracting': ('Pulsing and partitioning U/Pu', '脈動抽出・U/Pu分離中'), 'vitrifying': ('Melting and casting waste glass', '廃液をガラス固化中'),
    'coolant_warm': ('Cooling water must be below 28 °C', '循環水を28 ℃未満に冷却してください'), 'turbine': ('Connect the waterbox to an LP turbine casing', '水箱を低圧タービン外殻へ隣接させてください'),
}
for key, (en, ja) in STATUSES.items(): EN['plant_systems.status.'+key], JA['plant_systems.status.'+key] = en, ja
EN['create_refinery_n_reactors.crane.mode.fusion'], JA['create_refinery_n_reactors.crane.mode.fusion'] = 'Fusion Remote Maintenance', '核融合炉遠隔保守'
for key, en, ja in [('fusion_service', 'Exchanging a fusion component', '核融合炉部品を交換中'), ('fusion_unsafe', 'Stop plasma, discharge magnets and cool the blanket', 'プラズマ停止・磁場放電・ブランケットの冷却が必要')]:
    EN['crane.create_refinery_n_reactors.status.'+key], JA['crane.create_refinery_n_reactors.status.'+key] = en, ja
EN['fusion_reactor.create_refinery_n_reactors.status.divertor'], JA['fusion_reactor.create_refinery_n_reactors.status.divertor'] = 'Replace the divertor cassette', 'ダイバータカセットを交換してください'
EN['turbine.create_refinery_n_reactors.condenser'], JA['turbine.create_refinery_n_reactors.condenser'] = 'Condenser heat buffer full; circulate cooling water', '復水器蓄熱上限・冷却水を循環させてください'
METRICS = {
    'control_measurement': ('Process measurement', 'プロセス測定値'), 'control_output': ('Controller output (%)', '調節器出力（%）'), 'control_setpoint': ('Controller setpoint (%)', '調節器設定値（%）'),
    'control_kp': ('PID proportional gain', 'PID比例ゲイン'), 'control_ti': ('PID integral time (s)', 'PID積分時間（秒）'), 'control_td': ('PID derivative time (s)', 'PID微分時間（秒）'),
    'cooling_power': ('Rejected heat (MW)', '放熱量（MW）'), 'cooling_ambient': ('Ambient temperature (°C)', '外気温（℃）'), 'cooling_wetbulb': ('Wet-bulb temperature (°C)', '湿球温度（℃）'),
    'cooling_evaporated': ('Evaporated water (mB)', '蒸発水量（mB）'), 'condenser_heat': ('Stored condenser heat (MJ)', '復水器蓄熱（MJ）'), 'fusion_divertor_life': ('Divertor remaining life', 'ダイバータ残寿命'),
}
for key, (en, ja) in METRICS.items(): EN['display.create_refinery_n_reactors.'+key], JA['display.create_refinery_n_reactors.'+key] = en, ja
for key, en, ja in [('solvent_extraction', 'Solvent Extraction', '溶媒抽出'), ('vitrification', 'Waste Vitrification', 'ガラス固化')]:
    EN['jei.create_refinery_n_reactors.'+key], JA['jei.create_refinery_n_reactors.'+key] = en, ja
    EN['jei.create_refinery_n_reactors.note.'+key], JA['jei.create_refinery_n_reactors.note.'+key] = '%s ticks; see Ponder for connections', '%s tick・接続はPonderを参照'

CAPTIONS = {
    'natural_draft_tower': {
        'title': ('Building a compact natural draft cooling tower', '小型の自然通風冷却塔を組み立てる'),
        'size': ('Build a 36-high tower with diameters 25 at the base, 14 at the throat and 17 at the top. Keep the interior clear.', '底径25・高さ36・喉14・頂径17マスに組み、内部は空けます。'),
        'fill': ('The basin, open air intake, fill packs and spray distributors are working parts, not optional decoration.', '集水槽・開放吸気口・充填材・散水樋が必要です。内部の通風を塞がないでください。'),
        'water': ('North ports take hot water, south ports return cold water. Supply makeup water at the east port. Use parallel Create pumps and pipes.', '北側に温水、南側から冷水、東側に補給水を接続。Createのパイプとポンプを並列に使います。'),
        'climate': ('Cooling is weaker in hot, humid areas. Supply makeup water to replace evaporation.', '暑く湿った場所は冷えにくくなります。蒸発した分の補給水も送ります。'),
        'stop': ('All 25 × 25 m footprint chunks must be loaded. Goggles show faults; Display Links expose heat, temperature, water and evaporation.', '25×25 m内の全チャンクを読み込みます。構造不良はゴーグル、放熱・温度・水量・蒸発量はDisplay Linkで確認できます。'),
    },
    'condenser_loop': {
        'title': ('Rejecting turbine condenser heat', 'タービン復水熱を冷却塔へ捨てる'),
        'build': ('Build a 3 × 3 × 7 m condenser tube shell. Put its waterbox against a radial LP turbine casing port, facing the turbine.', '3×3×7 mの復水器管束を組み、水箱を低圧タービンの半径方向ケーシングへ向けて隣接させます。'),
        'loop': ('Pump tower cold water into the left-side inlet and drain warmed water at the right-side outlet back to the tower. Use parallel pumps and pressure mains for station flow.', '塔の冷水を左側入口へ送り、右側出口の温水を塔へ戻します。発電所の大流量には並列ポンプと超高圧主管を使います。'),
        'heat': ('Supply cooling water below 28 °C and collect 30 °C condensate from the LP stage.', '冷却水は28 °C未満で供給します。低圧段から30 °Cの復水を回収します。'),
        'stop': ('A stopped coolant loop eventually stops steam processing. Restore coolant flow to resume.', '循環が止まると、やがて蒸気処理も止まります。冷却水の流れを戻すと再開します。'),
    },
    'process_pid': {
        'title': ('Building automatic level and pressure loops', '水位と圧力の自動制御を組む'),
        'sensor': ('Attach the transmitter to a tank. Click to select level or pressure; Shift-click cycles the pressure span: 10, 50, 100, 250 bar.', '伝送器をタンクに取り付けます。クリックで水位・圧力を切替。Shiftクリックで圧力範囲10・50・100・250 barを切り替えます。'),
        'rack': ('Stack the instrument rack above the PID controller, facing the same way. Standard Display Links carry the measured percentage to this rack.', 'PID調節器の上に同じ向きの計器ラックを重ね、標準Display Linkで伝送器の測定信号を接続します。'),
        'knobs': ('Upper left / middle / right: setpoint, P, Ti. Lower: Td, reverse action, auto/manual. Click increases, Shift-click decreases.', '上段左から設定値・P・Ti、下段はTd・逆作用・自動/手動。クリックで増、Shiftクリックで減です。'),
        'valve': ('Mount the actuator on the flow regulator pointer face. Its 0–15 redstone input opens the valve from zero to 262,144 mB/t.', '流量調節器の指針面にアクチュエータを取り付け、0～15のレッドストーン信号で0～262,144 mB/tを調節します。'),
        'loops': ('Use separate controllers for level and pressure. Loss of signal closes the valve.', '水位と圧力には別々の調節器を使います。信号が切れると弁は閉じます。'),
    },
    'fuel_cycle': {
        'title': ('Closing the nuclear fuel cycle', '再処理・MOX・ガラス固化で燃料サイクルを閉じる'),
        'feed': ('Cool spent assemblies in the pool, shear them with a mechanical saw, and dissolve the chopped fuel with nitric acid in the existing heated reaction plant.', '使用済み燃料をプールで冷却し、メカニカルソーでせん断。既存の加熱反応プラントで硝酸溶解します。'),
        'column': ('Each storey is made in Mechanical Crafters and placed as a whole 3 × 3 octagon. Stack twelve behind the drive: two settlers, eight pulse storeys, two settlers. The drive takes at least 32 RPM at its front, dissolved fuel on top and 30 percent TBP solvent on its right.', '各段はメカニカルクラフターで作り、置くとそのまま3×3の八角形になります。駆動部の後ろに分離段2・脈動段8・分離段2の12段を積みます。駆動部の正面に32 RPM以上、上に燃料溶解液、右側に30パーセントTBP溶媒を供給します。'),
        'outlets': ('Four of the pulse storeys are product storeys. From bottom to top they deliver uranium solution, plutonium solution, high-level waste and recycled solvent on any side. Pipe every output away.', '脈動段のうち4段は生成物出口段で、下からU溶液・Pu溶液・高レベル廃液・循環溶媒をどの側面からも取り出せます。全出口を搬出してください。'),
        'mox': ('Recover PuO₂, mix it with depleted UO₂, press the powder, sinter in hydrogen and assemble MOX rods using zircaloy tubes.', 'PuO₂を回収し、劣化UO₂と混合・プレス成形・水素雰囲気焼結。ジルカロイ管でMOX燃料棒に組み立てます。'),
        'melter': ('Build the 7 × 4 × 4 m steel frame and refractory melter. Wire its two terminals to Electro Energetics and heat to 1,150 °C.', '7×4×4 mの鋼製フレームと耐火炉を組み、2端子にElectro Energeticsを接続。1,150 ℃まで昇温します。'),
        'glass': ('Feed waste liquid from above, frit and empty canisters through funnels, and extract sealed canisters below. Each placed canister stands 3 m tall.', '上から廃液、ファンネルからフリットと空容器を投入し、下から密封固化体を搬出。設置した容器は高さ3 mです。'),
        'stop': ('Blocked outlets stop the batch without consuming feed. Goggles show state; Display Links expose all six fluid inventories, items, heat and progress.', '出口閉塞では原料を消費せず停止。状態はゴーグル、6流体槽・アイテム・温度・進捗はDisplay Linkで確認します。'),
    },
    'fusion_maintenance': {
        'title': ('Remote blanket and divertor maintenance', 'クレーンでブランケットとダイバータを遠隔交換する'),
        'build': ('Span the 11 m tokamak with the existing gantry crane. The top of the reactor must be within 15 m below the rails.', '既存門型クレーンで11 mのトカマクを跨ぎます。炉の上面からレールまでを15 m以内にします。'),
        'safe': ('Switch off the plasma and magnets. Wait for the field to fall below 1% and the blanket to cool to 100 °C or below.', 'プラズマと磁石を切ります。磁場が1％未満に下がり、ブランケットが100℃以下になるまで待ちます。'),
        'supply': ('Select Fusion Remote Maintenance with the Create wrench. Feed fresh blanket cartridges and tungsten divertor cassettes to the drive using a funnel.', 'Createレンチで核融合遠隔保守モードを選び、駆動部へファンネルで新品ブランケットとタングステンダイバータを搬入します。'),
        'exchange': ('Apply rotation and redstone. The bridge and trolley travel to the nearest point on top of the reactor, lower the handling tool and exchange components with more than 25 percent wear.', '回転とレッドストーンを供給すると、橋桁と台車が炉の上面の最寄りの位置へ移動し、吊具を下ろして25パーセント以上損耗した部品を交換します。'),
        'damage': ('Ordinary operation wears both parts; an unplanned plasma trip damages them further. Used components leave through the drive output funnel.', '通常運転で両部品が損耗し、プラズマ異常停止で追加損傷。使用済み部品は駆動部の搬出ファンネルから回収します。'),
    },
}
for scene, captions in CAPTIONS.items():
    for key, (en, ja) in captions.items():
        EN[f'create_refinery_n_reactors.ponder.shared.{scene}.{key}'], JA[f'create_refinery_n_reactors.ponder.shared.{scene}.{key}'] = en, ja
    EN[f'create_refinery_n_reactors.ponder.{scene}.header'], JA[f'create_refinery_n_reactors.ponder.{scene}.header'] = captions['title']

for locale, additions in [('en_us', EN), ('ja_jp', JA), ('zh_cn', {**EN, **json.loads((Path(__file__).parent / 'data/zh_cn_overrides.json').read_text())})]:
    path = A / 'lang' / (locale + '.json');data = json.loads(path.read_text())
    if locale == 'zh_cn':
        additions = {key: data.get(key, value) if '.ponder.' in key else value for key, value in additions.items()}
    data.update(additions);write(path, data)


def tower_role(x, y, z):
    r = 12 if y < 4 else math.sqrt(7**2 + ((12**2-7**2)/22**2 if y <= 26 else (8.5**2-7**2)/9**2)*(y-26)**2)
    distance = math.hypot(x, z)
    if distance > r + .35: return None
    role = None
    if y == 0 or y == 1 and distance >= 11: role = 'cooling_tower_basin'
    elif y >= 4 and distance >= r-1: role = 'cooling_tower_shell'
    elif 1 < y < 4 and distance >= 10 and any(math.hypot(x-math.cos(a)*(11+(y-2)/2)+math.sin(a)*(4-y)*.6*s,
            z-math.sin(a)*(11+(y-2)/2)-math.cos(a)*(4-y)*.6*s) < .75 for a in (i*math.pi/6 for i in range(12)) for s in (-1, 1)): role = 'cooling_tower_shell'
    elif 3 <= y <= 5 and distance < 10: role = 'cooling_tower_fill'
    elif y == 6 and distance < 10 and (x % 4 == 0 or z % 4 == 0): role = 'cooling_tower_distributor'
    if (x, y, z) == (0, 1, -12): role = 'natural_draft_cooling_tower'
    if y == 1 and x in (-9,-7,-5,-3,3,5,7,9) and abs(z) == math.floor(math.sqrt(12.35**2-x*x)):
        role = 'cooling_tower_hot_inlet' if z < 0 else 'cooling_tower_cold_outlet'
    if (x, y, z) == (12, 1, 0): role = 'cooling_tower_makeup_inlet'
    return role


ponder.OUT = A / 'ponder/plant'
tower = ponder.Structure(27, 38)
for y in range(36):
    for x in range(-12, 13):
        for z in range(-12, 13):
            role = tower_role(x, y, z)
            if role: tower.block(x+13, y+1, z+13, NS+role)
tower.save('natural_draft_tower')

control = ponder.Structure(11, 8)
for y in range(1, 4): control.block(2, y, 3, 'create:fluid_tank')
control.block(2, 2, 2, NS+'process_transmitter', facing='north', indicator='8')
control.block(6, 1, 3, NS+'pid_controller', facing='north', indicator='8')
control.block(6, 2, 3, NS+'pid_instrument_rack', facing='north')
control.block(8, 1, 3, NS+'flow_regulator', axis='z')
control.block(8, 2, 3, NS+'control_valve_actuator', facing='up', indicator='8')
control.pipe(8, 1, 2, 'north', 'south');control.pump(8, 1, 1, 'south')
control.block(2, 2, 1, 'create:display_link', facing='north', powered='false')
control.save('process_pid')

cycle = ponder.Structure(19, 15)
cycle.block(4, 1, 2, NS+'pulse_extraction_column', facing='north')
for y in range(12):
    if y in (2, 4, 7, 9): cycle.block(4, y+1, 4, NS+'extraction_column_outlet', tank=str((2, 4, 7, 9).index(y) + 2))
    else: cycle.block(4, y+1, 4, NS+('extraction_pulse_plate' if 1 < y < 10 else 'extraction_column_shell'))
    for x in (-1, 0, 1):
        for z in (-1, 0, 1):
            if x or z:
                toward = ('east' if x < 0 else 'west') if x else ('south' if z < 0 else 'north')
                cycle.block(4+x, y+1, 4+z, NS+'extraction_column_structure', facing=toward)
for d in range(7):
    for x in range(-1, 3):
        for y in range(4):
            core, ex, ez = 2 <= d <= 4 and 0 <= x <= 1, x in (-1, 2), d in (0, 6)
            if (d, x, y) == (0, 0, 0): cycle.block(12+x, y+1, 3+d, NS+'vitrification_melter', facing='north')
            elif core and y in (1, 2): cycle.block(12+x, y+1, 3+d, NS+'vitrification_electrode')
            else:
                part = (5 if y == 3 else 3) if core else 0 if ex and ez else 1 if ex and y in (0, 3) else 2 if ez and y in (0, 3) else 6 if ex and y == 2 else -1
                if part >= 0: cycle.block(12+x, y+1, 3+d, NS+'vitrification_refractory_shell', part=str(part), facing='north')
for section in range(3): cycle.block(15, section+1, 3, NS+'vitrified_waste_canister', section=str(section))
cycle.block(3, 1, 12, 'create:basin');cycle.block(3, 3, 12, 'create:mechanical_mixer');cycle.block(6, 3, 12, 'create:mechanical_press', facing='east')
cycle.save('fuel_cycle')

from generate_surface_condenser_ponder import generate as condenser_ponder
condenser_ponder()

# The tokamak itself is placed by the scene from FusionStructure; this stores the service crane around it.
maintenance = ponder.Structure(17, 17)
for x in (2, 14):
    for z in (2, 14):
        for y in range(1, 14): maintenance.block(x, y, z, 'create:metal_girder', axis='y', x='false', z='false', top='true', bottom='true', waterlogged='false')
for z in (2, 14):
    for x in range(2, 15): maintenance.block(x, 14, z, 'create:metal_girder', axis='x', x='true', z='false', top='false', bottom='true' if x in (2, 14) else 'false', waterlogged='false')
maintenance.block(1, 14, 2, NS+'fusion_crane_drive', axis='x', facing='east')
maintenance.block(0, 14, 2, 'create:creative_motor', facing='east')
maintenance.block(1, 15, 2, 'minecraft:redstone_block')
maintenance.save('fusion_maintenance')

previous = {
    'step_1': ('Cool spent rods in the pool, shear them with a mechanical saw, then send the chopped fuel to this heated reaction plant.', '使用済み燃料をプールで冷却し、メカニカルソーでせん断してから、この加熱反応プラントへ送ります。'),
    'step_2': ('One chopped fuel batch and 4,000 mB nitric acid produce 4,000 mB dissolved spent fuel. Send it to the pulse extraction column.', 'せん断燃料1個と硝酸4,000 mBから燃料溶解液4,000 mBを作り、パルス抽出塔へ送ります。'),
    'step_3': ('The extraction column separates U, Pu and high-level waste. Recycle the solvent; fabricate MOX and vitrify the waste. See the fuel-cycle Ponder.', '抽出塔でU・Pu・高レベル廃液を分離。溶媒を循環し、MOX製造と廃液ガラス固化へ進みます。燃料サイクルPonderを参照。'),
}
for locale in ('en_us', 'ja_jp', 'zh_cn'):
    path = A/'lang'/f'{locale}.json';language = json.loads(path.read_text())
    for key, texts in previous.items():
        name = 'create_refinery_n_reactors.ponder.shared.guide.spent_fuel_reprocessing.' + key
        language[name] = language.get(name, texts[0]) if locale == 'zh_cn' else texts[1 if locale == 'ja_jp' else 0]
    write(path, language)

# A dedicated empty footprint makes the full-size GameTest reserve all of its chunks.
ponder.OUT = D / 'structure'
fixture = ponder.Structure(40, 40);fixture.save('test_power_plant')
print('Generated metre-scale plant assets, complete fuel-cycle recipes and four Ponder structures.')
