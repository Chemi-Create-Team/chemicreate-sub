#!/usr/bin/env python3
"""The fluid heat exchangers, each a straight run of one casing block: the 2 x 2 and 3 x 3 shell-and-tube exchangers, built of
tube-piped tanks, and the 1 x 1 jacketed pipe. Writes their block states, models, textures, item models, loot, tags, recipes, names
and Ponder lessons, and removes retired parts. Run from the repository root.

The tube-piped tank is Create's fluid tank model laid on its side, with a tube sheet on each end; its connected textures join the cells
of one formed exchanger (TubeTankCTBehaviour). A formed jacketed pipe's cell is segment * 13 (ExchangerStructure): segment 0 the first
end, 1 the middle, 2 the last end, 3 a one-block pipe. Its models are authored lying north: the first end is the south face."""
import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from generate_petrochemical_ponder import OUT, Structure
from generate_ponder_language import SHARED, generate as ponder_titles
from generate_refrigeration import exchanger as shell_run

ROOT = Path(__file__).resolve().parents[1]
A = ROOT / 'src/main/resources/assets/create_refinery_n_reactors'
D = ROOT / 'src/main/resources/data'
NS = 'create_refinery_n_reactors:'
FACINGS = (('north', 0), ('east', 90), ('south', 180), ('west', 270))
JACKET_TEXTURES = {'jacket': NS + 'block/jacketed_pipe/jacket', 'flange': NS + 'block/jacketed_pipe/flange', 'bolt': NS + 'block/jacketed_pipe/bolt',
                   'pipe': 'create:block/pipes', 'particle': NS + 'block/jacketed_pipe/jacket'}
RETIRED = ('heat_exchanger_support', 'heat_exchanger_casing', 'heat_exchanger_cover', 'heat_exchanger_tubes', 'heat_exchanger', 'large_heat_exchanger')
NAMES = {
    'tube_piped_tank': ('Tube-Piped Tank', 'チューブパイプドタンク', '管束储罐'),
    'heat_exchanger_controller': ('Shell-and-Tube Heat Exchanger Controller', 'シェル＆チューブ式熱交換器の制御部', '管壳式换热器控制部'),
    'large_heat_exchanger_controller': ('Large Shell-and-Tube Heat Exchanger Controller', '大型シェル＆チューブ式熱交換器の制御部', '大型管壳式换热器控制部'),
    'jacketed_pipe': ('Jacketed Pipe', '水冷ジャケット管', '夹套管'),
    'jacketed_pipe_controller': ('Jacketed Pipe Controller', '水冷ジャケット管の制御部', '夹套管控制部'),
}
SIDES = ('north', 'south', 'east', 'west', 'up', 'down')
ITEMS = {'iron_pipe': ('Iron Pipe', '鉄パイプ', '铁管')}
# Create's tank models put their top on the positive end of the axis once turned; the tube-piped tank turns the same way.
TURNS = {'north': {'x': 270}, 'south': {'x': 270}, 'east': {'x': 270, 'y': 270}, 'west': {'x': 270, 'y': 270}}
SHELL_CASING = 'tube_piped_tank'
SHELL_OFFSET = {2: 0, 3: 4}
PLACES = 13


def write(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + '\n')


def box(bounds, texture, sides=SIDES, **faces):
    x, y, z, X, Y, Z = bounds
    uv = {'north': [16 - X, 16 - Y, 16 - x, 16 - y], 'south': [x, 16 - Y, X, 16 - y], 'west': [z, 16 - Y, Z, 16 - y],
          'east': [16 - Z, 16 - Y, 16 - z, 16 - y], 'up': [x, z, X, Z], 'down': [x, 16 - Z, X, 16 - z]}
    return {'from': [x, y, z], 'to': [X, Y, Z],
            'faces': {side: {'texture': '#' + faces.get(side, texture), 'uv': [max(0, min(16, n)) for n in uv[side]]} for side in sides}}


def model(path, elements):
    write(A / 'models/block' / (path + '.json'), {'parent': 'minecraft:block/block', 'textures': JACKET_TEXTURES, 'elements': elements})
    return NS + 'block/' + path


def octagon(apothem, z0, z1, texture, sides=SIDES):
    """An octagonal prism along the run: two crossed bars for the flat sides and four plates turned 45 degrees for the corners."""
    half = apothem * 0.41421356
    r = lambda n: round(n, 3)
    elements = [box((r(8 - apothem), r(8 - half), z0, r(8 + apothem), r(8 + half), z1), texture, sides),
                box((r(8 - half), r(8 - apothem), z0, r(8 + half), r(8 + apothem), z1), texture, sides)]
    thick = apothem / 2
    reach = (apothem - thick / 2) * 0.70710678
    for sx, sy in ((1, 1), (-1, 1), (-1, -1), (1, -1)):
        cx, cy = 8 + sx * reach, 8 + sy * reach
        plate = box((r(cx - half), r(cy - thick / 2), z0, r(cx + half), r(cy + thick / 2), z1), texture, sides)
        plate['faces'] = {side: {**face, 'uv': [5, 5, 11, 11] if side in ('north', 'south') else [5, z0, 11, z1] if side in ('up', 'down') else [z0, 5, z1, 11]}
                          for side, face in plate['faces'].items()}
        plate['rotation'] = {'angle': -45 if sx == sy else 45, 'axis': 'z', 'origin': [r(cx), r(cy), 8]}
        elements.append(plate)
    return elements


def jacket_cell(segment):
    """A straight Create fluid pipe closed in an octagonal steel jacket. A run's ends close with a bolted flange, through which the
    pipe comes out; the jacket's nozzles are on the top of the first block and the bottom of the last."""
    first, last = segment in (0, 3), segment in (2, 3)
    elements = octagon(6, 4 if last else 0, 12 if first else 16, 'jacket')
    for z0, z1, bolts in ((12, 14, 14), (2, 4, 1)) if first and last else ((12, 14, 14),) if first else ((2, 4, 1),) if last else ():
        elements += octagon(7.5, z0, z1, 'flange')
        for angle in range(8):
            x = 8 + 6.4 * (1, 0.7071, 0, -0.7071, -1, -0.7071, 0, 0.7071)[angle]
            y = 8 + 6.4 * (0, 0.7071, 1, 0.7071, 0, -0.7071, -1, -0.7071)[angle]
            elements.append(box((round(x - .5, 3), round(y - .5, 3), bolts, round(x + .5, 3), round(y + .5, 3), bolts + 1), 'bolt'))
        mouth = z1 if z0 == 12 else 0
        elements.append({'from': [4, 4, mouth], 'to': [12, 12, mouth + 2], 'faces': {
            **{side: {'texture': '#pipe', 'uv': [0, 0, 4, 1], 'rotation': 90 if side in ('east', 'west') else 0} for side in ('east', 'west', 'up', 'down')},
            ('south' if z0 == 12 else 'north'): {'texture': '#pipe', 'uv': [11, 11, 16, 16]}}})
    if first and not last:
        elements += octagon(6.6, 9, 9.8, 'flange') + octagon(6.6, 10.6, 11.4, 'flange')
    nozzle = lambda z, y0, y1, f0, f1: [box((6, y0, z - 2, 10, y1, z + 2), 'flange'), box((5, f0, z - 3, 11, f1, z + 3), 'flange')]
    if first: elements += nozzle(9 if last else 7, 13, 15, 15, 16)
    if last: elements += nozzle(7 if first else 9, 1, 3, 0, 1)
    return elements


def textures():
    """The jacketed pipe's stainless jacket, its darker flanges and bright bolt heads; the core is Create's own pipe texture."""
    from PIL import Image
    folder = A / 'textures/block/jacketed_pipe'
    folder.mkdir(parents=True, exist_ok=True)
    def paint(name, base, marks):
        image = Image.new('RGBA', (16, 16), base)
        for colour, cells in marks:
            for x, y, w, h in cells:
                for px in range(x, x + w):
                    for py in range(y, y + h): image.putpixel((px, py), colour)
        image.save(folder / f'{name}.png')
    paint('jacket', (172, 179, 188, 255), [((182, 189, 197, 255), [(0, 2, 16, 2), (1, 9, 4, 2), (10, 11, 4, 2)]),
                                          ((162, 169, 178, 255), [(6, 6, 4, 2), (0, 14, 16, 1)])])
    paint('flange', (132, 139, 148, 255), [((156, 163, 172, 255), [(0, 0, 16, 1), (0, 0, 1, 16)]), ((104, 110, 118, 255), [(0, 15, 16, 1), (15, 0, 1, 16)]),
                                           ((118, 125, 134, 255), [(4, 4, 2, 2), (10, 9, 2, 2), (5, 11, 2, 2)])])
    paint('bolt', (198, 204, 211, 255), [((120, 126, 134, 255), [(8, 0, 8, 16), (0, 8, 8, 8)])])
    tube_sheet()
    iron_pipe()


# Create's copper tank colours, from light to dark, and the tube bores.
COPPER = {'light': (227, 130, 108, 255), 'lit': (214, 123, 91, 255), 'base': (194, 107, 76, 255), 'groove': (154, 80, 56, 255),
          'dark': (144, 73, 49, 255), 'bore': (92, 48, 32, 255), 'deep': (74, 36, 22, 255)}


def rim(x, y, size):
    """The four-pixel riveted rim round a tank end, lit from the upper left; None inside it."""
    depth, side = min((y, 'top'), (x, 'left'), (size - 1 - y, 'bottom'), (size - 1 - x, 'right'))
    along = x if side in ('top', 'bottom') else y
    if depth == 0: return COPPER['light'] if side in ('top', 'left') else COPPER['dark']
    if depth == 2 and along % 4 == 2: return COPPER['lit']
    if depth < 3: return COPPER['base']
    if depth == 3: return COPPER['groove']
    return None


def sheet_pixel(x, y, size):
    """A loose tank's end: the tube sheet, tube bores on a four-pixel pitch inside the rim."""
    edge = rim(x, y, size)
    if edge: return edge
    px, py = x % 4, y % 4
    if px in (1, 2) and py in (1, 2): return COPPER['deep'] if (px, py) == (1, 1) else COPPER['bore']
    if px == 3 and py in (1, 2) or py == 3 and px in (1, 2): return COPPER['lit']
    return COPPER['base']


def head_pixel(x, y, size):
    """A formed exchanger's end: the bolted cover over the tube sheet, its plate dished in from the rim."""
    edge = rim(x, y, size)
    if edge: return edge
    depth, side = min((y, 'top'), (x, 'left'), (size - 1 - y, 'bottom'), (size - 1 - x, 'right'))
    if depth == 5: return COPPER['lit'] if side in ('top', 'left') else COPPER['dark']
    return COPPER['base']


def tube_sheet():
    """The tank ends, each as a single tile and as a connected sheet in Create's rectangle layout, its 3 x 3 picture in the upper
    right tiles and the single tile at the lower left; the one-wide strips are never used, as an exchanger is square across.
    A loose tank shows its tube sheet; a formed exchanger closes both ends with a cover, as the channel heads of a real one."""
    from PIL import Image
    folder = A / 'textures/block/tube_piped_tank'
    folder.mkdir(parents=True, exist_ok=True)
    for name, pixel in (('tube_sheet', sheet_pixel), ('channel_head', head_pixel)):
        single = Image.new('RGBA', (16, 16))
        for x in range(16):
            for y in range(16): single.putpixel((x, y), pixel(x, y, 16))
        single.save(folder / f'{name}.png')
        connected = Image.new('RGBA', (64, 64))
        for x in range(48):
            for y in range(48): connected.putpixel((16 + x, y), pixel(x, y, 48))
        connected.paste(single, (0, 48))
        connected.save(folder / f'{name}_connected.png')


def iron_pipe():
    """The iron pipe pressed from a sheet: a thick diagonal tube in iron grey, open at its upper end."""
    from PIL import Image
    shades = {-2: (40, 42, 46, 255), -1: (216, 216, 216, 255), 0: (172, 172, 172, 255), 1: (128, 128, 130, 255), 2: (40, 42, 46, 255)}
    out = Image.new('RGBA', (16, 16))
    for x in range(1, 15):
        for y in range(1, 15):
            across, along = x + y - 15, x - y
            if abs(across) <= 2 and -11 <= along <= 11: out.putpixel((x, y), shades[across])
    for x, y in ((12, 2), (13, 3), (12, 3), (13, 2)): out.putpixel((x, y), (58, 60, 64, 255))
    out.save(A / 'textures/item/iron_pipe.png')


def assets():
    # Authored standing like Create's tank; turned onto its side, the down face would read upside down and mirrored, so it is turned
    # half round to keep both tube sheets upright for the connected texture.
    faces = {side: {'texture': '#end' if side in ('up', 'down') else '#side', 'uv': [0, 0, 16, 16], 'cullface': side} for side in SIDES}
    faces['down']['rotation'] = 180
    tank, head = (write_model(path, {'parent': 'minecraft:block/block', 'textures': {
        'end': NS + f'block/tube_piped_tank/{end}', 'side': 'create:block/fluid_tank', 'particle': 'create:block/fluid_tank'},
        'elements': [{'from': [0, 0, 0], 'to': [16, 16, 16], 'faces': faces}]}) for path, end in (('tube_piped_tank', 'tube_sheet'), ('tube_piped_tank_formed', 'channel_head')))
    write(A / 'blockstates' / f'{SHELL_CASING}.json', {'variants': {f'facing={facing},formed={str(formed).lower()}': {'model': head if formed else tank, **turn}
                                                                   for facing, turn in TURNS.items() for formed in (False, True)}})
    for block in ('heat_exchanger_controller', 'large_heat_exchanger_controller'):
        write(A / 'blockstates' / f'{block}.json', {'variants': {f'facing={facing}': {'model': head, **turn} for facing, turn in TURNS.items()}})
    write(A / 'models/item' / f'{SHELL_CASING}.json', {'parent': tank})
    write(A / 'models/item/iron_pipe.json', {'parent': 'minecraft:item/generated', 'textures': {'layer0': NS + 'item/iron_pipe'}})
    alone = model('jacketed_pipe/single', jacket_cell(3))
    variants = {'formed=false': {'model': alone}}
    for segment in range(4):
        path = model(f'jacketed_pipe/cell_{segment * PLACES}', jacket_cell(segment))
        for place in range(PLACES):
            for facing, angle in FACINGS:
                variants[f'cell={segment * PLACES + place},facing={facing},formed=true'] = {'model': path, 'y': angle}
    write(A / 'blockstates/jacketed_pipe.json', {'variants': variants})
    write(A / 'models/item/jacketed_pipe.json', {'parent': alone})
    write(A / 'blockstates/jacketed_pipe_controller.json', {'variants': {
        f'facing={facing}': {'model': NS + 'block/jacketed_pipe/cell_0', 'y': angle} for facing, angle in FACINGS}})
    # Cell models no longer in use by the numbering.
    for path in (A / 'models/block/jacketed_pipe').glob('cell_*.json'):
        if int(path.stem[5:]) % PLACES: path.unlink()


def write_model(path, value):
    write(A / 'models/block' / (path + '.json'), value)
    return NS + 'block/' + path


def data():
    loot = lambda drop: {'type': 'minecraft:block', 'pools': [{'rolls': 1, 'entries': [{'type': 'minecraft:item', 'name': NS + drop}],
                                                                'conditions': [{'condition': 'minecraft:survives_explosion'}]}]}
    for block in NAMES:
        table = loot(SHELL_CASING if 'heat_exchanger' in block else block.removesuffix('_controller'))
        if block.startswith('jacketed_pipe'):
            table['pools'].append({**table['pools'][0], 'entries': [{'type': 'minecraft:item', 'name': 'create:fluid_pipe'}]})
        write(D / 'create_refinery_n_reactors/loot_table/blocks' / f'{block}.json', table)
    recipes = D / 'create_refinery_n_reactors/recipe'
    write(recipes / 'pressing/iron_pipe.json', {'type': 'create:pressing', 'ingredients': [{'tag': 'c:plates/iron'}], 'results': [{'id': NS + 'iron_pipe'}]})
    # Any of the plant's liquid and pressure tanks, or Create's own, takes the tube bundle.
    write(D / 'create_refinery_n_reactors/tags/item/tanks.json', {'values': ['create:fluid_tank'] + [NS + name for name in (
        'horizontal_fluid_tank', 'pressure_tank', 'stainless_steel_tank', 'gas_receiver', 'cryogenic_tank')]})
    write(recipes / f'{SHELL_CASING}.json', {'type': 'minecraft:crafting_shapeless', 'category': 'misc', 'ingredients': [
        {'tag': NS + 'tanks'}] + [{'item': NS + 'iron_pipe'}] * 4, 'result': {'id': NS + SHELL_CASING, 'count': 1}})
    for tag in (D / 'minecraft/tags/block/mineable/pickaxe.json', D / 'create/tags/block/non_movable.json'):
        current = json.loads(tag.read_text())
        values = [v for v in current['values'] if v.removeprefix(NS) not in RETIRED]
        current['values'] = list(dict.fromkeys(values + [NS + name for name in NAMES]))
        write(tag, current)


def retire():
    for name in RETIRED + ('reactor_preheater',):
        for path in (A / 'blockstates' / f'{name}.json', A / 'models/item' / f'{name}.json',
                     D / 'create_refinery_n_reactors/recipe' / f'{name}.json', D / 'create_refinery_n_reactors/loot_table/blocks' / f'{name}.json'):
            path.unlink(missing_ok=True)
    for folder in ('heat_exchanger', 'large_heat_exchanger', 'thermal_control/exchanger_core', 'thermal_control/exchanger_roof', 'thermal_control/exchanger_support', 'thermal_control/assembled_cover',
                   'reactor/preheater/core', 'reactor/preheater/head', 'reactor/preheater/jacket', 'reactor/preheater/support', 'reactor/preheater'):
        directory = A / 'models/block' / folder
        for path in directory.glob('*.json'):
            path.unlink()
        if directory.exists() and not any(directory.iterdir()): directory.rmdir()
    for path in ('reactor/preheater.json', 'reactor/preheater_running.json', 'thermal_control/connections/exchanger_wall_arm.json',
                 'thermal_control/connections/exchanger_wall_post.json'):
        (A / 'models/block' / path).unlink(missing_ok=True)
    connections = A / 'models/block/thermal_control/connections'
    if connections.exists() and not any(connections.iterdir()): connections.rmdir()
    shapes = json.loads((A / 'heating_shapes.json').read_text())
    write(A / 'heating_shapes.json', {key: value for key, value in shapes.items() if not key.startswith(('exchanger', 'assembled/exchanger'))})
    for lesson in ('reactor_plant_preheat', 'reactor_plant_preheat_expansion', 'reactor_plant_preheat_integration'):
        (A / 'ponder/plant' / f'{lesson}.nbt').unlink(missing_ok=True)


def pipe(scene, x, y, z, a, b):
    scene.block(x, y, z, 'create:encased_fluid_pipe', **{side: str(side in (a, b)).lower() for side in SIDES})


def tank(scene, x, y, z):
    scene.block(x, y, z, 'create:fluid_tank', bottom='true', top='true', shape='plain')


def save(scene, lesson):
    scene.save(lesson)
    (A / 'ponder/plant' / f'{lesson}.nbt').write_bytes((OUT / f'{lesson}.nbt').read_bytes())
    (OUT / f'{lesson}.nbt').unlink()


def lessons():
    shell = Structure(11, 5)
    shell_run(shell, 3, 1, 4, 4)
    # The tube side along the bottom from west to east through both end faces; the shell side the other way, in at the top of the east
    # end and out at the west: counterflow.
    tank(shell, 0, 1, 4); shell.pump(1, 1, 4, 'east'); pipe(shell, 2, 1, 4, 'east', 'west')
    pipe(shell, 7, 1, 4, 'east', 'west'); shell.pump(8, 1, 4, 'east'); tank(shell, 9, 1, 4)
    tank(shell, 3, 3, 1); shell.pump(3, 3, 2, 'north'); pipe(shell, 3, 3, 3, 'north', 'south'); pipe(shell, 3, 3, 4, 'north', 'down')
    pipe(shell, 6, 3, 4, 'north', 'down'); pipe(shell, 6, 3, 3, 'north', 'south'); shell.pump(6, 3, 2, 'south'); tank(shell, 6, 3, 1)
    save(shell, 'heat_exchanger')
    large = Structure(11, 6)
    shell_run(large, 3, 1, 3, 4, 3)
    tank(large, 0, 1, 3); large.pump(1, 1, 3, 'east'); pipe(large, 2, 1, 3, 'east', 'west')
    pipe(large, 7, 1, 3, 'east', 'west'); large.pump(8, 1, 3, 'east'); tank(large, 9, 1, 3)
    tank(large, 3, 4, 0); large.pump(3, 4, 1, 'north'); pipe(large, 3, 4, 2, 'north', 'south'); pipe(large, 3, 4, 3, 'north', 'down')
    pipe(large, 6, 4, 3, 'north', 'down'); pipe(large, 6, 4, 2, 'north', 'south'); large.pump(6, 4, 1, 'south'); tank(large, 6, 4, 0)
    save(large, 'large_heat_exchanger')
    jacket = Structure(11, 5)
    for index in range(4):
        segment = 0 if index == 0 else 2 if index == 3 else 1
        at = (3 + index, 2, 4)
        if index == 0: jacket.block(*at, NS + 'jacketed_pipe_controller', facing='east', axis='x', lit='false')
        else: jacket.block(*at, NS + 'jacketed_pipe', formed='true', cell=str(segment * PLACES), facing='east')
    tank(jacket, 0, 2, 4); jacket.pump(1, 2, 4, 'east'); pipe(jacket, 2, 2, 4, 'east', 'west')
    pipe(jacket, 7, 2, 4, 'east', 'west'); jacket.pump(8, 2, 4, 'east'); tank(jacket, 9, 2, 4)
    tank(jacket, 3, 3, 7); jacket.pump(3, 3, 6, 'north'); pipe(jacket, 3, 3, 5, 'north', 'south'); pipe(jacket, 3, 3, 4, 'south', 'down')
    pipe(jacket, 6, 1, 4, 'up', 'south'); pipe(jacket, 6, 1, 5, 'north', 'south'); jacket.pump(6, 1, 6, 'south'); tank(jacket, 6, 1, 7)
    save(jacket, 'jacketed_pipe')
    return {
        'heat_exchanger': ({'size': 11, 'height': 5, 'scale': .6, 'formed': 'step_1', 'components': [SHELL_CASING]}, [
            ({'target': [3, 1, 4], 'reveal': [3, 1, 4, 3, 2, 5], 'item': NS + SHELL_CASING},
             ('Craft tube-piped tanks from a tank and four iron pipes, pressed from iron sheets. Set them 2 × 2 across; they lie along the way you look.',
              'チューブパイプドタンクは、タンクと鉄パイプ4本（鉄板をプレス）で作ります。断面2×2に置きます。タンクは見ている方向に沿って寝かせて置かれます。',
              '管束储罐由储罐和4根铁管（用压机压制铁板）合成。摆成2×2截面，储罐沿视线方向横放。')),
            ({'target': [6, 1, 4], 'reveal': [4, 1, 4, 6, 2, 5]},
             ('Lay them two to twelve long in a straight run. Only then do the tanks join into one shell, both ends close with a cover over the tubes, and it works as a heat exchanger; a longer run moves more heat.',
              '一直線に長さ2～12で並べると、初めてタンクの外側がつながって1つの胴になり、両端が管を覆うふたで閉じて、熱交換器として動きます。長いほど多くの熱を移します。',
              '沿直线排成2～12格长后，储罐外壳才连成一体，两端由盖住管束的端盖封闭，作为换热器工作。越长传热越多。')),
            ({'target': [2, 1, 4], 'reveal': [0, 1, 0, 10, 4, 10]},
             ('The tube side flows through the two end covers, the shell side through the top of each end. Send the two streams opposite ways, as here, for counterflow: it cools and heats far better than both flowing the same way.',
              '管側は両端のふたの面から、胴側は両端の上面から出入りします。ここのように2つの流れを反対向きに流すと対向流になり、同じ向きに流す並流よりずっとよく冷やし、温めます。',
              '管程从两端端盖进出，壳程从两端顶面进出。像这里一样让两股流体反向流动即为逆流，冷却和加热效果远好于同向流动的并流。')),
            ({'target': [4, 2, 4], 'speed': 64},
             ('Heat passes by itself from the hotter stream to the colder. Goggles show both temperatures and the heat moved.',
              '熱は温度の高い流れから低い流れへ自動で移ります。ゴーグルで両側の温度と移した熱量を確認できます。',
              '热量自动从较热的流体传向较冷的流体。护目镜可查看两侧温度和传热量。')),
        ]),
        'large_heat_exchanger': ({'size': 11, 'height': 6, 'scale': .55, 'formed': 'step_1', 'components': [SHELL_CASING]}, [
            ({'target': [3, 1, 3], 'reveal': [3, 1, 3, 3, 3, 5], 'item': NS + SHELL_CASING},
             ('The large exchanger uses the same tube-piped tanks, set 3 × 3 across.',
              '大型も同じチューブパイプドタンクを、断面3×3に置いて作ります。', '大型换热器使用相同的管束储罐，摆成3×3截面。')),
            ({'target': [6, 1, 3], 'reveal': [4, 1, 3, 6, 3, 5]},
             ('It needs at least three in a row, up to twelve, before the tanks join, the ends close and it works. Each block of length moves about twice the heat of the 2 × 2 one.',
              '3個以上（最大12）並べて初めて外側がつながり、両端がふたで閉じて動きます。長さ1ブロックあたり、2×2の約2倍の熱を移します。',
              '至少排成3格（最多12格）后外壳才连成一体、两端封闭并工作。每格长度的传热量约为2×2型的两倍。')),
            ({'target': [2, 1, 3], 'reveal': [0, 1, 0, 10, 5, 10]},
             ('The tube side flows through the two end covers, the shell side through the top of each end, as on the smaller one; run them opposite ways for counterflow.',
              '管側は両端のふた、胴側は両端の上面から出入りします。2×2と同じく、反対向きに流すと対向流になります。', '管程从两端端盖进出，壳程从两端顶面进出，与2×2型相同；反向流动即为逆流。')),
            ({'target': [4, 2, 3], 'speed': 64},
             ('Use it where a 2 × 2 exchanger twelve blocks long is not enough, such as large cooling or condensing duties.',
              '長さ12の2×2でも熱交換が足りない、大量の冷却や凝縮に使います。', '用于12格长的2×2型仍不够用的大量冷却或冷凝。')),
        ]),
        'jacketed_pipe': ({'size': 11, 'height': 5, 'scale': .6, 'formed': 'step_1', 'components': ['jacketed_pipe']}, [
            ({'target': [3, 2, 4], 'reveal': [3, 2, 4, 6, 2, 4], 'item': 'create:fluid_pipe'},
             ('Run an ordinary fluid pipe straight and level where the fluid is to be cooled or heated.',
              '冷却・加熱したい場所に、普通の液体パイプを水平に真っすぐ通します。',
              '在需要冷却或加热的位置，水平笔直地铺设普通流体管道。')),
            ({'target': [4, 2, 4], 'use': NS + 'jacketed_pipe'},
             ('Right-click the pipe with a jacketed pipe to close a jacket around it. One to eight jackets in a line make one exchanger; one already works, a longer line moves more heat.',
              '水冷ジャケット管を持ってパイプを右クリックすると、パイプの周りにジャケットが付きます。一直線に1～8個つなげると1台の熱交換器になります。1個でも動き、長いほど多くの熱を移します。',
              '手持夹套管右键点击管道，即可在管道外套上夹套。直线连续1～8格组成一台换热器。一格即可工作，越长传热越多。')),
            ({'target': [2, 2, 4], 'reveal': [0, 1, 0, 10, 4, 10]},
             ('The process fluid runs through the core from end to end. Cooling water enters the jacket at the top of the first block and leaves at the bottom of the last.',
              '処理する流体は中心の管を端から端へ流れます。冷却水は最初のブロックの上面からジャケットへ入り、最後のブロックの下面から出ます。',
              '工艺流体沿内管从一端流到另一端。冷却水从第一格顶面进入夹套，从最后一格底面流出。')),
            ({'target': [4, 2, 4], 'speed': 64},
             ('Heat passes by itself from the hotter stream to the colder, with no power or setting.',
              '熱は温度の高い流れから低い流れへ自動で移ります。動力も設定も要りません。', '热量自动从较热的流体传向较冷的流体，无需动力或设定。')),
        ]),
    }


def tutorials():
    path = A / 'ponder/tutorials.json'
    catalog = json.loads(path.read_text())
    texts = lessons()
    built = {lesson: {'id': lesson, **extra, 'steps': [{'key': f'step_{i}', **step} for i, (step, _) in enumerate(steps)]}
             for lesson, (extra, steps) in texts.items()}
    # The new lessons take the retired rack's place in the catalogue, or keep their own on a later run.
    rebuilt = []
    for entry in catalog:
        if entry['id'] in ('reactor_plant_preheat', 'heat_exchanger'): rebuilt += list(built.values())
        elif entry['id'] not in built and not entry['id'].startswith('reactor_plant_preheat'): rebuilt.append(entry)
    write(path, rebuilt)
    titles = {'heat_exchanger': ('Shell-and-tube heat exchanger', 'シェル＆チューブ式熱交換器', '管壳式换热器'),
              'large_heat_exchanger': ('Large shell-and-tube heat exchanger', '大型シェル＆チューブ式熱交換器', '大型管壳式换热器'),
              'jacketed_pipe': ('Jacketed pipe', '水冷ジャケット管', '夹套管')}
    for index, language in enumerate(('en_us', 'ja_jp', 'zh_cn')):
        lang = A / 'lang' / f'{language}.json'
        strings = json.loads(lang.read_text())
        for key in [k for k in strings if 'reactor_plant_preheat' in k]: del strings[key]
        for name in RETIRED: strings.pop('block.' + NS.replace(':', '.') + name, None)
        for name, names in NAMES.items(): strings['block.' + NS.replace(':', '.') + name] = names[index]
        for name, names in ITEMS.items(): strings['item.' + NS.replace(':', '.') + name] = names[index]
        strings['message.create_refinery_n_reactors.jacketed_pipe.straight'] = (
            'Right-click a straight, level fluid pipe to fit the jacket', '水平で真っすぐな液体パイプを右クリックして取り付けます', '请右键点击水平笔直的流体管道来安装夹套')[index]
        for lesson, (_, steps) in texts.items():
            strings[SHARED + f'guide.{lesson}.title'] = titles[lesson][index]
            for i, (_, text) in enumerate(steps): strings[SHARED + f'guide.{lesson}.step_{i}'] = text[index]
        write(lang, strings)
    ponder_titles()


if __name__ == '__main__':
    textures()
    assets()
    data()
    retire()
    tutorials()
    print('Generated the 2 x 2 and 3 x 3 shell-and-tube heat exchangers and the jacketed pipe, their models, lessons and names')
