"""One registered console unit: a four-unit desk and an optional 7 x 4 wall, drawn as 16 px steel pixel art."""
import json
import math
import re
from pathlib import Path
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / 'src/main/resources/assets/create_refinery_n_reactors'
TEXTURES = ASSETS / 'textures/block/reactor/rbmk'
MOD = 'create_refinery_n_reactors:'
FOLDER = 'block/reactor_console/'
MATERIALS = {key: MOD + 'block/reactor/rbmk/' + name for key, name in {
    'panel': 'console_panel', 'top': 'console_desk_top', 'plate': 'console_plate', 'parts': 'console_controls',
    'meter': 'console_meter', 'chart': 'recorder_chart', 'glass': 'console_glass', 'recess': 'console_recess',
}.items()}
MATERIALS['trim'] = 'create:block/industrial_iron_block'
MATERIALS['particle'] = MATERIALS['panel']
SIDES = ('north', 'south', 'east', 'west', 'up', 'down')
OUTWARD = ('north', 'east', 'west', 'up')
DESK, WALL_WIDTH, WALL_HEIGHT, MAP = 4, 7, 4, 4
# Nixie recesses (u from the operator's left, y from the bottom, in pixels). A glass pane sits GLASS_INSET px inside the board face
# and the tubes stand NIXIE_DEPTH px deep behind it; ReactorControlConsoleRenderer fills the recesses from the same numbers.
GLASS_INSET, NIXIE_DEPTH = 1, 2.5
NIXIE_RECESSES = {'meter': ((1, 15, 13, 15.5), (2, 14, 1, 4)), 'annunciator': ((1, 15, 2, 7), (1, 15, 9, 14)),
                  'readout': ((1, 15, 2.5, 9.5), (1, 15, 11.5, 14.5))}
# Meter window (x, y from, x, y to) in pixels and its needle pivot height; ReactorControlConsoleRenderer.METER_PIVOT matches.
# The dial lies one pixel behind the glass, with the needle between them.
METER_CASE, METER_PIVOT, METER_DIAL = (4, 4.5, 12, 12.5), 8.5, 3 + GLASS_INSET + 1

STEEL = {'rim': '#4A4F52', 'rim_dark': '#2B2E30', 'bevel': '#8E9497', 'bevel_low': '#7C8285', 'recess': '#5A5F62',
         'body': '#6E7376', 'body_low': '#666B6E', 'body_high': '#787D80', 'shine': '#A9AFB2'}
DARK = ('#3A3F42', '#4A5054', '#2A2E30', '#1A1D1F')
# The near-black walls and backs inside the board's windows.
RECESS = ('#101213', '#141718', '#0B0C0D')
BLACK = ('#26292B', '#34383B', '#1B1E20')
RED = ('#B8423A', '#D06B60', '#8C3A34')
GREEN = ('#4E8A5A', '#6FA57A', '#356642')
AMBER = ('#C9963C', '#E0B55E', '#9A6F2A')
GREY = ('#9AA0A3', '#BCC2C4', '#70767A')
WHITE = '#E4E6E1'
FACE = ('#D9DCD8', '#B9BDB9')
PAPER = ('#D9D4BE', '#C2BDA6')
SCREEN = ('#24322C', '#30423A', '#1A211D')
WATER, GRAPHITE = '#4C7FA8', '#C27A3A'
# Sprite origins in console_controls.png: caps and lamps show their lit face, SHADE holds one-pixel side colours.
CAPS = {'red': (0, 0), 'grey': (6, 0), 'green': (9, 0), 'amber': (12, 0)}
LAMPS = {'red': (6, 3), 'green': (9, 3), 'grey': (12, 3)}
SHADE = {'black': (0, 13), 'red': (1, 13), 'grey': (2, 13), 'green': (3, 13), 'amber': (4, 13), 'white': (5, 13),
         'water': (6, 13), 'graphite': (0, 14)}
DIAL_PLATE, DIAL_SKIRT, DIAL_KNOB, SWITCH_BAR = (0, 6), (7, 6), (7, 12), (13, 6)


def write(path, obj):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(obj, indent=2) + '\n')


def tile(bounds, texture, sides=SIDES, **faces):
    """Block-aligned faces: every pixel of the model shows the texel at the same place on the block face."""
    x, y, z, X, Y, Z = bounds
    uvs = {'north': [16-X, 16-Y, 16-x, 16-y], 'south': [x, 16-Y, X, 16-y], 'west': [z, 16-Y, Z, 16-y],
           'east': [16-Z, 16-Y, 16-z, 16-y], 'up': [x, z, X, Z], 'down': [x, 16-Z, X, 16-z]}
    edge = {'north': z == 0, 'south': Z == 16, 'west': x == 0, 'east': X == 16, 'up': Y == 16, 'down': y == 0}
    result = {}
    for side in sides:
        face = {'texture': '#' + faces.get(side, texture), 'uv': [max(0, min(16, n)) for n in uvs[side]]}
        if edge[side]: face['cullface'] = side
        result[side] = face
    return {'from': [x, y, z], 'to': [X, Y, Z], 'faces': result}


def part(bounds, shade, top=None, sides=SIDES, front=None):
    """A small control from console_controls.png: one flat side colour and an optional sprite on its top or front face.
    Top sprites are turned to read upright from the operator's side."""
    u, v = SHADE[shade]
    faces = {side: {'texture': '#parts', 'uv': [u, v, u + 1, v + 1]} for side in sides}
    for side, sprite, w, h in (('up', top, bounds[3]-bounds[0], bounds[5]-bounds[2]), ('north', front, bounds[3]-bounds[0], bounds[4]-bounds[1])):
        if sprite is not None and side in faces: faces[side]['uv'] = [sprite[0], sprite[1], sprite[0] + w, sprite[1] + h]
    if top is not None and 'up' in faces: faces['up']['rotation'] = 180
    return {'from': list(bounds[:3]), 'to': list(bounds[3:]), 'faces': faces}


def model(name, elements, materials=None, **kwargs):
    write(ASSETS / 'models' / (FOLDER + name + '.json'), {
        'parent': 'minecraft:block/block', 'textures': materials or MATERIALS,
        'elements': elements, **kwargs})
    return MOD + FOLDER + name


def fill(image, x0, y0, x1, y1, colour):
    ImageDraw.Draw(image).rectangle((x0, y0, x1, y1), fill=colour)


def cap(image, x, y, size, colours):
    base, high, low = colours
    fill(image, x, y, x + size - 1, y + size - 1, low);fill(image, x, y, x + size - 2, y + size - 2, high)
    if size > 2: fill(image, x + 1, y + 1, x + size - 2, y + size - 2, base)


def textures():
    """Every face is 16 x 16 and lit from the upper left: dark rim, bright chamfer, then a sunken field."""
    panel = Image.new('RGB', (16, 16), STEEL['body'])
    fill(panel, 0, 0, 15, 15, STEEL['rim_dark']);fill(panel, 0, 0, 14, 14, STEEL['rim'])
    fill(panel, 1, 1, 14, 14, STEEL['bevel_low']);fill(panel, 1, 1, 13, 13, STEEL['bevel']);fill(panel, 1, 1, 2, 2, STEEL['shine'])
    fill(panel, 2, 2, 13, 13, STEEL['body_high']);fill(panel, 2, 2, 12, 12, STEEL['recess']);fill(panel, 3, 3, 12, 12, STEEL['body'])
    for x, y, length in ((4, 4, 3), (9, 5, 2), (5, 7, 2), (8, 9, 3), (3, 10, 2), (9, 11, 2)):
        fill(panel, x, y, x + length - 1, y, STEEL['body_low']);fill(panel, x + 1, y + 1, x + length, y + 1, STEEL['body_high'])
    panel.save(TEXTURES / 'console_panel.png')

    top = Image.new('RGB', (16, 16), DARK[0])
    for z in range(0, 16, 4):
        fill(top, 0, z, 15, z, DARK[1]);fill(top, 0, z + 3, 15, z + 3, DARK[2])
    for x, y in ((3, 1), (10, 2), (6, 5), (12, 6), (2, 10), (9, 9), (5, 13), (11, 14)):fill(top, x, y, x + 1, y, DARK[2])
    top.save(TEXTURES / 'console_desk_top.png')

    plate = Image.new('RGB', (16, 16), DARK[0])
    fill(plate, 0, 0, 15, 15, DARK[3]);fill(plate, 0, 0, 14, 14, DARK[2]);fill(plate, 1, 1, 14, 14, DARK[0])
    fill(plate, 1, 1, 14, 1, DARK[1]);fill(plate, 1, 1, 1, 14, DARK[1])
    for x, y in ((2, 2), (12, 2), (2, 12), (12, 12)):
        fill(plate, x, y, x + 1, y + 1, DARK[3]);fill(plate, x, y, x, y, DARK[1])
    plate.save(TEXTURES / 'console_plate.png')

    recess = Image.new('RGB', (16, 16), RECESS[0])
    for x, y in ((1, 2), (9, 1), (4, 6), (12, 5), (2, 10), (8, 9), (13, 12), (5, 13)):
        fill(recess, x, y, x + 1, y, RECESS[1]);fill(recess, x + 1, y + 1, x + 2, y + 1, RECESS[2])
    recess.save(TEXTURES / 'console_recess.png')

    parts = Image.new('RGB', (16, 16), BLACK[0])
    cap(parts, 0, 0, 6, RED);fill(parts, 2, 2, 3, 2, RED[1])
    for kind, colours in (('grey', GREY), ('green', GREEN), ('amber', AMBER)):
        cap(parts, *CAPS[kind], 3, colours)
    for kind, colours in (('red', RED), ('green', GREEN), ('grey', GREY)):
        cap(parts, *LAMPS[kind], 2, colours)
    cap(parts, *DIAL_PLATE, 7, (BLACK[0], BLACK[1], BLACK[2]))
    for x, y in ((3, 0), (0, 3), (6, 3), (3, 6), (1, 1), (5, 1), (1, 5), (5, 5)):
        fill(parts, x, 6 + y, x, 6 + y, WHITE)
    cap(parts, *DIAL_SKIRT, 5, (BLACK[1], GREY[2], BLACK[2]))
    for x, y in ((7, 6), (11, 6), (7, 10), (11, 10)):fill(parts, x, y, x, y, BLACK[2])
    cap(parts, *DIAL_KNOB, 3, (BLACK[0], BLACK[1], BLACK[2]))
    fill(parts, 13, 6, 14, 12, GREY[0]);fill(parts, 13, 6, 13, 12, GREY[1]);fill(parts, 13, 12, 14, 12, GREY[2])
    for name, colour in (('black', BLACK[2]), ('red', RED[2]), ('grey', GREY[2]), ('green', GREEN[2]), ('amber', AMBER[2]),
                         ('white', WHITE), ('water', WATER), ('graphite', GRAPHITE)):
        fill(parts, *SHADE[name], *SHADE[name], colour)
    parts.save(TEXTURES / 'console_controls.png')

    meter_texture().save(TEXTURES / 'console_meter.png')

    (TEXTURES / 'console_annunciator.png').unlink(missing_ok=True)

    chart = Image.new('RGB', (16, 16), PAPER[0])
    for y in range(2, 16, 3):fill(chart, 0, y, 15, y, PAPER[1])
    fill(chart, 0, 0, 0, 15, PAPER[1]);fill(chart, 5, 0, 5, 15, PAPER[1])
    chart.save(TEXTURES / 'recorder_chart.png')


def buttons():
    source = (ROOT / 'src/main/java/net/createplant/nuclear/ReactorConsoleButton.java').read_text()
    pattern = r'(\w+)\(\s*(\d+),([\d.]+),([\d.]+),([\d.]+),"([^"]+)","([^"]+)"\s*\)'
    pattern = pattern.replace(',', r'\s*,\s*')
    return [(name, int(col), float(u), float(v), float(r), label, kind)
            for name, col, u, v, r, label, kind in re.findall(pattern, source)]


def height(v):
    """Stepped desk top in pixels at a depth from the operator's edge; ReactorControlConsoleRenderer.surface adds the trim."""
    return 10 + min(3, int(v // 4))


def desk(column, controls, left_end, right_end):
    elements = [tile((0, 0, 0, 16, 1, 16), 'panel', ('north', 'south', 'east', 'west', 'down')),
                tile((0, 1, 1, 16, 7, 15), 'panel', ('north', 'south', 'east', 'west'))]
    for z in range(0, 16, 4):
        if column == 3 and z:
            elements.append(tile((.5, 7, 4, 15.5, 13.1, 16), 'panel', ('north', 'south', 'up', 'down'), up='plate'))
            break
        elements.append(tile((.5, 7, z, 15.5, height(z) + .15, z + 4), 'panel', ('north', 'south', 'up', 'down') if z == 12 else ('north', 'up', 'down'), up='top'))
    elements += [tile((15.5, 7, 0, 16, 13.2, 16), 'trim' if left_end else 'panel'), tile((0, 7, 0, .5, 13.2, 16), 'trim' if right_end else 'panel')]
    for name, c, u, v, r, _, kind in controls:
        if c != column or kind == 'area':
            continue
        x, h = 16 - u, height(v) + .15
        elements.append(part((x-r, h, v-r, x+r, h+.25, v+r), 'black', DIAL_PLATE if kind == 'dial' else None, OUTWARD + ('south',)))
    if column == 3:
        for x0 in (1.5, 8.5):
            strip = part((x0, 13.1, 5, x0 + 6, 13.2, 15.5), 'white', (0, 0))
            strip['faces'] = {side: {**face, 'texture': '#chart'} for side, face in strip['faces'].items() if side != 'down'}
            elements.append(strip)
    return elements


def recessed(cavities, meter=False):
    """The panel with nixie recesses, and a meter window if asked, cut into its face; each has a glass pane GLASS_INSET px inside the
    face, and behind it the meter dial or a near-black back for the tubes."""
    face, glass, back = 3, 3 + GLASS_INSET, 3 + GLASS_INSET + NIXIE_DEPTH
    elements = [tile((0, 0, back, 16, 16, 16), 'panel', ('south', 'east', 'west', 'up', 'down', 'north'))]
    windows = [(16 - u1, 16 - u0, y0, y1, back, 'recess') for u0, u1, y0, y1 in cavities]
    if meter:
        x, y, X, Y = METER_CASE
        windows.append((x, X, y, Y, METER_DIAL, 'meter'))
    # The walls inside each window are near black; the face and the board's outer edges stay panel.
    y = 0
    for x0, x1, y0, y1, depth, texture in sorted(windows, key=lambda w: w[2]):
        if y0 > y: elements.append(tile((0, y, face, 16, y0, back), 'panel', up='recess', **({'down': 'recess'} if y > 0 else {})))
        if x0 > 0: elements.append(tile((0, y0, face, x0, y1, back), 'panel', east='recess'))
        if x1 < 16: elements.append(tile((x1, y0, face, 16, y1, back), 'panel', west='recess'))
        elements.append(tile((x0, y0, depth - .01, x1, y1, depth), 'recess', ('north',), north=texture))
        pane = tile((x0, y0, glass, x1, y1, glass), 'glass', ('north',))
        pane['faces']['north'].pop('cullface', None)
        elements.append(pane)
        y = y1
    if y < 16: elements.append(tile((0, y, face, 16, 16, back), 'panel', down='recess'))
    return elements


def wall(column, row):
    materials = dict(MATERIALS)
    if column < MAP:
        panel = tile((0, 0, 3, 16, 16, 16), 'panel')
        materials['map'] = MOD + f'block/reactor/rbmk/console_core_map_c{column}_r{row}'
        panel['faces']['north'] = {'texture': '#map', 'uv': [0, 0, 16, 16]}
        elements = [panel]
    else:
        kind = 'annunciator' if row == 3 else 'readout' if row == 0 else 'meter'
        elements = recessed(NIXIE_RECESSES[kind], kind == 'meter')
    if column == 0: elements.append(tile((15.5, 0, 2.5, 16, 16, 3), 'trim', ('north', 'east', 'west')))
    if column == WALL_WIDTH - 1: elements.append(tile((0, 0, 2.5, .5, 16, 3), 'trim', ('north', 'east', 'west')))
    return elements, materials


def meter_texture():
    """An 8 px meter face at the electrolysis panel's instrument density (4 texels per pixel), block-aligned on a 64 px sheet.
    The needle pivot sits at METER_PIVOT; ReactorControlConsoleRenderer turns the needle through +-130 degrees around it."""
    scale = 4
    meter = Image.new('RGB', (16 * scale, 16 * scale), DARK[0])
    x0, x1 = METER_CASE[0] * scale, METER_CASE[2] * scale - 1
    y0, y1 = round((16 - METER_CASE[3]) * scale), round((16 - METER_CASE[1]) * scale) - 1
    fill(meter, x0, y0, x1, y1, DARK[3]);fill(meter, x0, y0, x1 - 1, y1 - 1, STEEL['bevel'])
    fill(meter, x0 + 1, y0 + 1, x1 - 1, y1 - 1, STEEL['rim']);fill(meter, x0 + 2, y0 + 2, x1 - 2, y1 - 2, FACE[1])
    fill(meter, x0 + 3, y0 + 3, x1 - 2, y1 - 2, FACE[0])
    cx, cy = 8 * scale, (16 - METER_PIVOT) * scale
    for n in range(21):
        angle = math.radians(-130 + 13 * n)
        inner = 9 if n % 5 == 0 else 10.5
        colour = RED[0] if n >= 17 else BLACK[0]
        for step in range(int((12 - inner) * 2) + 1):
            r = inner + step / 2
            fill(meter, math.floor(cx + math.sin(angle) * r), math.floor(cy - math.cos(angle) * r),
                 math.floor(cx + math.sin(angle) * r), math.floor(cy - math.cos(angle) * r), colour)
    return meter


def core_map_tiles():
    """A dark green-grey screen with quarter lines across the left 4 x 4 of the wall; the block-entity renderer adds the live lamps."""
    size = MAP * 16
    image = Image.new('RGB', (size, size), STEEL['rim_dark'])
    fill(image, 0, 0, size - 2, size - 2, STEEL['rim'])
    fill(image, 1, 1, size - 2, size - 2, STEEL['bevel_low']);fill(image, 1, 1, size - 3, size - 3, STEEL['bevel'])
    fill(image, 2, 2, size - 3, size - 3, SCREEN[2]);fill(image, 3, 3, size - 3, size - 3, SCREEN[0])
    # Only quarter lines: a finer grid cannot match every core size, so the live lamps would sit between its cells.
    for p in range(size // 4, size - 3, size // 4):
        fill(image, p, 3, p, size - 4, SCREEN[1]);fill(image, 3, p, size - 4, p, SCREEN[1])
    for row in range(MAP):
        for column in range(MAP):
            tile_image = image.crop((column * 16, (MAP - 1 - row) * 16, (column + 1) * 16, (MAP - row) * 16))
            tile_image.save(TEXTURES / f'console_core_map_c{column}_r{row}.png')
    for stale in TEXTURES.glob('console_core_map_c*_r*.png'):
        column, row = (int(n[1:]) for n in stale.stem.split('_')[-2:])
        if column >= MAP or row >= MAP:
            stale.unlink()


def main():
    controls = buttons()
    if len(controls) != 13:
        raise ValueError('Button definitions did not match the generator')
    textures()
    core_map_tiles()
    # The desk: four units side by side; section 4 is a raw unit. The display board: 7 x 4 units; section 28 is a raw unit.
    for block, prefix, count, shape in (('reactor_control_console', 'section_', DESK,
                                         lambda n: (desk(n, controls, n == 0, n == DESK - 1) if n < DESK else desk(0, controls, True, True), dict(MATERIALS))),
                                        ('reactor_console_display', 'display_', WALL_WIDTH * WALL_HEIGHT,
                                         lambda n: wall(n % WALL_WIDTH, n // WALL_WIDTH) if n < WALL_WIDTH * WALL_HEIGHT else wall(WALL_WIDTH - 1, 1))):
        variants = {}
        for section in range(count + 1):
            elements, materials = shape(section)
            glazed = any(element['faces'].get('north', {}).get('texture') == '#glass' for element in elements)
            path = model(prefix + str(section), elements, materials, render_type='minecraft:translucent' if glazed else 'minecraft:cutout')
            for direction, degrees in {'north': 0, 'east': 90, 'south': 180, 'west': 270}.items():
                variants[f'facing={direction},section={section}'] = {'model': path, 'y': degrees}
        for stale in (ASSETS / 'models' / FOLDER).glob(prefix + '*.json'):
            if int(stale.stem.split('_')[1]) > count:
                stale.unlink()
        write(ASSETS / f'blockstates/{block}.json', {'variants': variants})
        write(ASSETS / f'models/item/{block}.json', {
            'parent': MOD + FOLDER + prefix + str(count),
            'display': {'gui': {'rotation': [30, 225, 0], 'translation': [0, 1, 0], 'scale': [.65, .65, .65]}}})
    names = set()
    for name, col, u, v, r, label, kind in controls:
        if kind in ('dial', 'switch', 'area'):
            continue
        x, h, r = 16-u, height(v) + .15, r-.5
        names.add('button_' + name.lower())
        model('button_' + name.lower(), [part((x-r, h+.25, v-r, x+r, h+.9, v+r), kind, CAPS[kind], OUTWARD + ('south',))])
    for stale in (ASSETS / 'models' / FOLDER).glob('button_*.json'):
        if stale.stem not in names:
            stale.unlink()
    model('dial', [part((5.5, .25, 5.5, 10.5, 1.3, 10.5), 'black', DIAL_SKIRT, OUTWARD + ('south',)),
                   part((6.5, 1.3, 6.5, 9.5, 2.2, 9.5), 'black', DIAL_KNOB, OUTWARD + ('south',)),
                   part((7.5, 2.2, 5.5, 8.5, 2.4, 8), 'white', None, OUTWARD + ('south',))])
    model('mode_switch', [part((6.5, .25, 6.5, 9.5, 1.2, 9.5), 'black', None, OUTWARD + ('south',)),
                          part((7, 1.2, 4.5, 9, 2.6, 11.5), 'grey', SWITCH_BAR, OUTWARD + ('south',)),
                          part((7.5, 2.6, 4.5, 8.5, 2.8, 6), 'white', None, OUTWARD + ('south',))])
    az5 = next(c for c in controls if c[0] == 'AZ5')
    x, v, r, floor = 16 - az5[2], az5[3], az5[4], height(az5[3]) + .4
    top = floor + 1.25
    glass = [(x-r, top-.5, v-r, x+r, top, v+r), (x-r, floor, v-r-.5, x+r, top, v-r)]
    glass += [(x0, floor, v-r, x0+.5, top-.5, v+r) for x0 in (x-r, x+r-.5)]
    cover = []
    for bounds in glass:
        pane = tile(bounds, 'glass');cover.append(pane)
        for face in pane['faces'].values():face.pop('cullface', None)
    cover.append(tile((x-1.5, top, v-r-.5, x+1.5, top+.5, v-r), 'trim'))
    model('guard', cover, render_type='minecraft:translucent')
    # Annunciators and readouts are drawn as flush nixie tubes by NixieStrip; their former standing models are removed.
    for stale in ('annunciator_dark', 'annunciator_red', 'annunciator_green', 'nixie_base', 'nixie_glass'):
        (ASSETS / 'models' / FOLDER / (stale + '.json')).unlink(missing_ok=True)
    (ASSETS / 'models' / FOLDER / 'meter_case.json').unlink(missing_ok=True)
    model('meter_needle', [part((7.85, METER_PIVOT, METER_DIAL - .5, 8.15, METER_PIVOT + 3, METER_DIAL - .35), 'red'),
                           part((7.6, METER_PIVOT - .4, METER_DIAL - .6, 8.4, METER_PIVOT + .4, METER_DIAL - .01), 'black')])
    for colour in ('red', 'green', 'grey'):
        model('lamp_' + colour, [part((7, 10.4, 7, 9, 11.6, 9), colour, LAMPS[colour], OUTWARD + ('south',))])
    for trace in ('water', 'graphite'):
        model('trace_' + trace, [part((-.18, 0, -.22, .18, .045, .22), trace, None, OUTWARD + ('south',))])
    for stale in ('flow_case', 'flow_needle', 'flow_glass', 'recorder_dot_water', 'recorder_dot_graphite'):
        (ASSETS / 'models' / FOLDER / (stale + '.json')).unlink(missing_ok=True)
    print(f'Generated one console block, {len(variants)} states, {len(controls)} physical controls')


TEXT = {
    'crane.remembered': ('Crane at %s %s %s selected. Right-click the assembled desk with this unit.', 'クレーン %s %s %s を記憶しました。このユニットで完成した制御盤を右クリックしてください。', '已记住位于 %s %s %s 的起重机。用此单元右击已组装的控制台。'),
    'crane.connected': ('Crane at %s %s %s linked and stopped', 'クレーン %s %s %s に接続しました。停止中です。', '已连接位于 %s %s %s 的起重机，当前已停止。'),
    'crane.disconnected': ('Crane link removed', 'クレーンの接続を解除しました。', '已解除起重机链接。'),
    'crane.unavailable': ('Load the crane and assemble the full desk to operate it', 'クレーンの場所を読み込み、制御盤を完成させてください。', '请加载起重机所在区域并完成控制台组装。'),
    'crane.in_use': ('The crane is already linked to another desk', 'クレーンは別の制御盤に接続されています。', '起重机已链接到另一台控制台。'),
    'crane.no_link': ('Use a desk unit on the crane drive, then on this desk', '制御盤ユニットで駆動部を選び、この制御盤を右クリックしてください。', '使用控制台单元依次右击驱动端与此控制台。'),
    'crane.no_link_short': ('OFF', '未接続', '未链接'),
    'crane.manual_required': ('Switch the crane to manual to jog it', '走行・横行を操作するにはクレーンを手動に切り替えてください。', '请切换到手动模式以移动起重机。'),
    'crane.mode_changed': ('Crane mode %s', 'クレーンモード %s', '起重机模式 %s'),
    'crane.mode.0': ('STOP', '停止', '停止'),
    'crane.mode.1': ('MAN', '手動', '手动'),
    'crane.mode.2': ('AUTO', '自動', '自动'),
    'crane.goggle': ('Crane %s — %s', 'クレーン %s ／ %s', '起重机 %s ／ %s'),
    'bank_control': ('bank %s', '一括%s', '成组%s'),
    'button.select_indicator': ('Click a control-rod lamp on the map, or use PREV / NEXT', '炉心図の制御棒ランプ、またはPREV／NEXTで棒を選択してください', '点击堆芯图上的控制棒灯，或使用PREV／NEXT选择'),
    'button.guard_open': ('AZ-5 cover opened; press the red button again to scram', 'AZ-5のカバーを開きました。もう一度赤ボタンを押すと緊急停止します', 'AZ-5保护盖已打开；再按红色按钮紧急停堆'),
    'button.manual_required': ('Turn the mode switch to MAN before using the bank dial', '一括ダイヤルを使うにはモード切替をMANにしてください', '使用成组旋钮前请将模式开关转到MAN'),
    'goggle.az5_ready': ('AZ-5 ready; RESET releases the latch', 'AZ-5待機。RESETでラッチ解除', 'AZ-5就绪；RESET解除锁存'),
    'panel.power': ('POWER MW', '出力 MW', '功率 MW'),
    'panel.reactivity': ('REACT. %', '反応度 %', '反应性 %'),
    'panel.outlet': ('OUTLET °C', '出口水温 °C', '出口 °C'),
    'panel.flow': ('FLOW mB/t', '流量 mB/t', '流量 mB/t'),
    'panel.void': ('VOID %', 'ボイド %', '空泡 %'),
    'panel.xenon': ('XENON %', 'キセノン %', '氙 %'),
    'panel.fuel': ('FUEL °C', '燃料 °C', '燃料 °C'),
    'panel.graphite': ('GRAPH. °C', '黒鉛 °C', '石墨 °C'),
    'panel.rods': ('RODS %', '挿入率 %', '插入 %'),
    'panel.chart_outlet': ('OUTLET', '出口水温', '出口水温'),
    'panel.chart_graphite': ('GRAPHITE', '黒鉛温度', '石墨温度'),
    'alarm.0': ('SCRAM', 'AZ-5', '停堆'),
    'alarm.1': ('FUEL HOT', '燃料高温', '燃料高温'),
    'alarm.2': ('OUTLET HOT', '出口高温', '出口高温'),
    'alarm.3': ('LOW FLOW', '低流量', '低流量'),
    'alarm.4': ('CONTROL', '所有権競合', '控制权冲突'),
    'alarm.5': ('LINK', '接続', '连接'),
    'link.stored': ('Remembered the station at %s, %s, %s; place this unit to link to it', '%s, %s, %sの設備を記憶しました。このユニットを置くとリンクします', '已记住位于%s, %s, %s的设备；放置此单元即可链接'),
    'link.desk_incomplete': ('Complete the four-unit desk first', '先に制御盤ユニット4個の卓を完成させてください', '请先完成4个单元的操作桌'),
    'link.display_incomplete': ('Display board incomplete: 7 wide and 4 high, all facing the operator', '表示板が未完成：幅7×高さ4、すべて操作者側へ向けてください', '显示板未完成：宽7高4，全部朝向操作员'),
    'link.display_unlinked': ('Not linked: right-click a desk with a display unit, or this board with a desk unit, then place it', '未リンク：表示板ユニットで制御盤を、または制御盤ユニットでこの表示板を右クリックしてから置いてください', '未链接：用显示板单元右键操作桌，或用控制台单元右键此显示板，然后放置'),
    'mode.0': ('MODE: LOCAL', 'モード：LOCAL', '模式：LOCAL'),
    'mode.1': ('MODE: MAN', 'モード：MAN', '模式：MAN'),
    'mode.2': ('MODE: AUTO', 'モード：AUTO', '模式：AUTO'),
}
CRANE_STATUS = {
    'console_paused': ('Paused by the control desk', '制御盤で停止中', '控制台已暂停'),
    'console_manual': ('Ready for manual travel', '手動移動待機中', '等待手动移动'),
    'console_load': ('Grapple paused mid-operation; use AUTO to resume', '昇降途中で保持中。自動に切り替えると再開します。', '吊具在升降途中暂停。切换到自动模式以继续。'),
    'console_limit': ('The crane has reached its travel limit', 'クレーンの移動範囲の端です。', '起重机已到达行程尽头。'),
}
# Desk labels in every language; anything missing falls back to the label written on the control in ReactorConsoleButton.
LABELS = {
    'az5': ('AZ-5', 'AZ-5', 'AZ-5'),
    'az5_reset': ('RESET', 'RESET', 'RESET'),
    'rod_dial': ('ROD', '個別棒', '单棒'),
    'release_rod': ('LOCAL', '個別解除', '解除单棒'),
    'bank_dial': ('BANK', '一括', '成组'),
    'chart': ('PRESS TO CLEAR', '押して消去', '按下清除'),
    'acknowledge': ('ACK', '警報確認', '确认'),
    'crane_mode': ('CRANE', 'クレーン', '起重机'),
    'crane_along': ('RAIL ±', '走行 ±', '纵移 ±'),
    'crane_across': ('CROSS ±', '横行 ±', '横移 ±'),
}

ROOM = {
    'title': ('Building and operating the reactor control room', '原子炉制御室を組んで操作する', '搭建并操作反应堆控制室'),
    'build': ('Place four Reactor Control Desk units side by side, all facing the operator: the desk alone can run a reactor. Separately, stack 28 Reactor Display Board units into a wall 7 wide and 4 high, facing the same way, anywhere within 32 blocks.', '原子炉制御盤ユニットを4個、操作者側へ向けて横に並べます。この卓だけでも炉を運転できます。別に、原子炉表示板ユニット28個を同じ向きで幅7×高さ4に積み上げ、32マス以内のどこにでも表示板を組めます。', '将4个反应堆控制台单元并排放置，全部朝向操作员：仅此操作桌即可运行反应堆。另外，用28个反应堆显示板单元以同一朝向堆成宽7高4的墙，可放在32格以内任意位置。'),
    'link': ('Link the two by right-clicking a formed board with a desk unit, or a formed desk with a display unit, then placing that unit. Point a standard Create Display Link from a formed reactor at the desk or the board. The left 4 by 4 of the board maps fuel by temperature and every control rod; a channel short of coolant blinks.', '制御盤ユニットで完成した表示板を、または表示板ユニットで完成した卓を右クリックしてから、そのユニットを置くとリンクします。形成済み原子炉からCreate標準のディスプレイリンクで、卓か表示板を指定します。表示板の左4×4が炉心図で、燃料を温度の色で、制御棒を白いランプで示し、冷却水が足りないチャネルは点滅します。', '用控制台单元右键已完成的显示板，或用显示板单元右键已完成的操作桌，然后放置该单元即可链接。从已形成的反应堆用Create标准显示链接指向操作桌或显示板。显示板左侧4×4为堆芯图，按温度显示燃料并显示每根控制棒；冷却不足的通道会闪烁。'),
    'select': ('Click a white rod lamp on the map, or use PREV / NEXT. Click the right half of the ROD dial to insert the selected rod 5%, the left half to withdraw it; sneak for 1% steps. LOCAL hands the rod back to the bank or cap; sneak-click it to release every rod.',
        '炉心図の白い制御棒ランプ、またはPREV／NEXTで1本を選びます。個別棒ダイヤルの右半分で5%挿入、左半分で5%引き抜き、しゃがむと1%刻みです。個別解除でその棒を一括・キャップの指示へ戻し、しゃがんで押すと全棒を解除します。',
        '点击堆芯图上的白色控制棒灯或用PREV／NEXT选择一根棒。单棒旋钮右半边插入5%，左半边抽出5%，潜行时每次1%。解除单棒将其交还成组或顶盖指令，潜行点击则解除全部。'),
    'az5': ('The first press on the red AZ-5 button opens its cover; the second latches full insertion of every rod, overriding all other targets. RESET closes the cover and releases only the console latch; a reactor-side redstone scram still holds.',
        '赤いAZ-5ボタンは、1回目でカバーが開き、2回目で全制御棒の全挿入をラッチします。ほかのすべての目標より優先します。RESETはカバーを閉じて卓のラッチだけを解除し、炉側のレッドストーンAZ-5信号は残ります。',
        '第一次按红色AZ-5按钮打开保护盖，第二次锁存全部控制棒全插入，优先于其他所有目标。RESET关闭保护盖并仅解除控制台锁存，反应堆侧红石停堆信号仍有效。'),
    'readout': ('Six meters show power, reactivity, outlet water, total coolant flow, steam void and xenon. Every name and reading on the board is spelled out in small Create nixie tubes set flush into the panel; the bottom row reads the hottest fuel and graphite temperatures and the mean rod insertion. The annunciator names glow red while their condition is on and blink until acknowledged.', '6個の計器が熱出力・反応度・最高出口水温・総冷却水流量・ボイド率・キセノンを示します。表示板の名前と数値は、すべてパネルに平らに埋め込んだ小さなCreateのニキシー管で表示します。最下段は最高燃料温度・黒鉛温度・平均挿入率です。警報の名前は、その状態の間は赤く光り、確認するまで点滅します。', '六个仪表显示热功率、反应性、出口水温、冷却水总流量、空泡率和氙。显示板上的名称和数值全部由平嵌在面板上的小型Create辉光管显示；最下排显示燃料最高温度、石墨温度和平均插入率。报警名称在条件成立时亮红色，确认前闪烁。'),
    'recorder': ('The right-hand desk unit keeps 48 seconds of the hottest outlet water and graphite temperature on two paper strips; press the paper to clear it. ACK acknowledges blinking alarms without clearing their cause; sneak-click it for a lamp test.',
        '右端の卓は、最高出口水温と黒鉛温度の48秒分を2本の記録紙に残します。記録紙を押すと消去します。警報確認ボタンは点滅を止めますが、原因は解消しません。しゃがんで押すとランプ試験です。',
        '最右侧的操作桌在两条记录纸上保留48秒的出口水最高温度和石墨温度；按记录纸可清除。确认按钮停止报警闪烁但不排除原因；潜行点击进行灯测试。'),
    'automatic': ('Each press of the mode switch turns it LOCAL, MAN, AUTO. LOCAL leaves rods to the caps and links; MAN lets the BANK dial move every rod without its own target; AUTO runs the existing temperature-and-flow rule, whose known cold-start overshoot is unchanged.',
        'モード切替は押すたびにLOCAL→MAN→AUTOと切り替わります。LOCALでは各キャップとリンクの指示に従い、MANでは一括ダイヤルで個別目標のない棒をまとめて動かします。AUTOは従来の温度・流量の制御則で、既知の冷態起動時の出力超過は変わっていません。',
        '每按一次模式开关依次切换为LOCAL、MAN、AUTO。LOCAL由各顶盖和链接控制；MAN用成组旋钮移动所有无单独目标的棒；AUTO运行原有温度流量规则，已知冷态启动超调未改变。'),
}


def support():
    retired = ('reactor_console_desk', 'reactor_core_map', 'reactor_instrument_panel', 'reactor_chart_recorder', 'reactor_automatic_control')
    display = {
        'console_mode': ('Console mode (0 local / 1 manual / 2 auto)', '制御盤モード（0各キャップ／1手動／2自動）', '控制台模式（0顶盖／1手动／2自动）'),
        'console_bank_target': ('Manual bank target (%)', '手動一括目標（%）', '手动成组目标（%）'),
        'console_control_target': ('Active bank target (%)', '作動中の一括目標（%）', '当前成组目标（%）'),
        'console_alarms': ('Active console alarms', '制御盤の作動警報数', '控制台活动报警数'),
        'console_trace_samples': ('Paper trace samples', '記録紙の記録点数', '纸图记录点数'),
        'console_total_flow': ('Total channel-bundle coolant (mB/t)', '全チャネル束の冷却水流量（mB/t）', '全部通道束冷却水流量（mB/t）'),
        'reactor_surface_temperature': ('Maximum fuel cladding (°C)', '最高燃料棒表面温度（℃）', '燃料包壳最高温度（℃）'),
    }
    prefix = 'reactor_control_console.create_refinery_n_reactors.'
    rebuilt = tuple(prefix + part for part in ('label.', 'panel.', 'mode.', 'alarm.', 'hover.'))
    for index, lang in enumerate(('en_us', 'ja_jp', 'zh_cn')):
        path = ASSETS / 'lang' / (lang + '.json')
        raw = path.read_bytes().decode('utf-8'); newline = '\r\n' if '\r\n' in raw else '\n'
        strings = json.loads(raw)
        for key in list(strings):
            if any(key == 'block.create_refinery_n_reactors.' + name or key.startswith(name + '.') for name in retired) or key.startswith(rebuilt):
                del strings[key]
        strings['block.create_refinery_n_reactors.reactor_control_console'] = ('Reactor Control Desk Unit', '原子炉制御盤ユニット', '反应堆控制台单元')[index]
        strings['block.create_refinery_n_reactors.reactor_console_display'] = ('Reactor Display Board Unit', '原子炉表示板ユニット', '反应堆显示板单元')[index]
        for key, words in TEXT.items(): strings[prefix + key] = words[index]
        for key, words in CRANE_STATUS.items(): strings['crane.create_refinery_n_reactors.status.' + key] = words[index]
        for name, _, _, _, _, label, _ in buttons():
            words = LABELS.get(name.lower())
            strings[prefix + 'label.' + name.lower()] = (words[index] if words else label).replace('%', '％')
        for key, words in display.items(): strings['display.create_refinery_n_reactors.' + key] = words[index].replace('%', '％')
        for key, words in ROOM.items(): strings['create_refinery_n_reactors.ponder.shared.rbmk_control_room.' + key] = words[index].replace('%', '％')
        strings['create_refinery_n_reactors.ponder.rbmk_control_room.header'] = ROOM['title'][index]
        path.write_bytes((json.dumps(strings, ensure_ascii=False, indent=2).replace('\n', newline) + newline).encode('utf-8'))
    from generate_petrochemical_ponder import Structure
    import generate_rbmk_assets
    scene = Structure(12, 8)
    for column in range(DESK): scene.block(7 - column, 1, 3, MOD + 'reactor_control_console', facing='north', section=str(column))
    for row in range(WALL_HEIGHT):
        for column in range(WALL_WIDTH):
            scene.block(8 - column, 2 + row, 4, MOD + 'reactor_console_display', facing='north', section=str(row * WALL_WIDTH + column))
    generate_rbmk_assets.save_story(scene, 'rbmk_control_room')


if __name__ == '__main__':
    main()
    support()
