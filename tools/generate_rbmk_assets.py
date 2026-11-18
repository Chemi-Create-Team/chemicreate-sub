"""RBMK reactor: a 2 x 2 fine-channel graphite core, integrated top-cap rod drives and biological shield.
Textures, models, block states, loot, tags, recipes, translations and the Ponder storyboards."""
import json
import math
from PIL import Image, ImageDraw
from minecraft_style import grain
from generate_reactor_assets import A, D, NS, write
from generate_petrochemical_ponder import Structure, OUT

T = A / 'textures/block/reactor/rbmk'
GRAPHITE = (58, 60, 63)
STEEL = (150, 156, 160)
IRON = (86, 91, 95)
WARNING = ((214, 170, 46), (156, 44, 36))
CAP = (164, 168, 165)
ROD_CAP = (172, 58, 46)
CONTROL_CAPS = {
    'control': (174, 58, 50),
    'control_green': (104, 150, 105),
    'control_blue': (75, 132, 166),
    'control_yellow': (190, 151, 48),
}


def shade(color, v):
    return tuple(max(0, min(255, round(c * (1 + v / 100)))) for c in color) + (255,)


def save(name, draw, folder=T):
    im = Image.new('RGBA', (16, 16), (0, 0, 0, 0)); p = im.load()
    for y in range(16):
        for x in range(16):
            c = draw(x, y)
            if c is not None: p[x, y] = c
    folder.mkdir(parents=True, exist_ok=True); im.save(folder / f'{name}.png')


def pixel_panel(size, colour, key):
    image = Image.new('RGBA', size, colour + (255,)); pixels = image.load()
    for y in range(size[1]):
        for x in range(size[0]):
            pixels[x, y] = shade(colour, grain(key, x, y, .7))
    return image


def graphite(x, y, seams=(0, 8)):
    """Matte stacked graphite: brick seams, a lit chamfer under each seam, low grain and no metallic bands."""
    v = grain('rbmk_graphite', x, y, 1.6)
    if y in seams: v = -34
    elif y - 1 in seams: v = 14
    if x == 0: v = min(v, -20)
    if x == 15: v -= 8
    return shade(GRAPHITE, v)


def graphite_top(x, y):
    v = grain('rbmk_graphite_top', x, y, 1.6)
    if x in (0, 15) or y in (0, 15): v = -30
    elif x == 1 or y == 1: v = 12
    return shade(GRAPHITE, v)


def cap(x, y, colour):
    """One of four close-packed channel caps, bevelled with a dark centre."""
    start_x = 0 if x < 8 else 8
    start_y = 0 if y < 8 else 8
    lx, ly = x - start_x, y - start_y
    if lx == 0 or ly == 0: return shade(GRAPHITE, -34)
    if lx == 7 or ly == 7: return shade(colour, -20)
    v = 18 if lx == 1 or ly == 1 else -32
    return shade(colour, v)


def fuel_top(x, y):
    """Four pressure-channel caps on a 2 x 2 grid."""
    return cap(x, y, CAP)


def fuel_bottom(x, y):
    dx = min(abs(x - centre) for centre in (3.5, 11.5))
    dy = min(abs(y - centre) for centre in (3.5, 11.5))
    if dx <= 1 and dy <= 1: return shade((26, 30, 34), grain('rbmk_inlet', x, y, 1))
    if dx <= 2.5 and dy <= 2.5: return shade(STEEL, 14 if dx + dy < 3 else -14)
    return graphite_top(x, y)


def fuel_side(x, y):
    """Graphite with two fine zirconium pressure tubes visible at the block edge."""
    for tube in (4, 12):
        if x in (tube - 1, tube): return shade(STEEL, (12 if x == tube - 1 else -12) + grain('rbmk_tube', x, y, .8) - (30 if y in (0, 8) else 0))
    return graphite(x, y)


def absorber(x, y):
    """Boron carbide in a steel sleeve; a coupling with red and yellow warning stripes at the top of every section."""
    if y <= 2: return shade(WARNING[(x + y) // 2 % 2], (8, 0, -12)[y])
    if y in (3, 15): return shade(STEEL, -20 if y == 15 else 10)
    v = grain('rbmk_b4c', x, y, 1.4) + (16 if x % 4 == 1 else -10 if x % 4 == 3 else 0)
    return shade((50, 52, 56), v)


def displacer(x, y):
    """Graphite displacer: matte black, a thin steel coupling at the top, no sheen."""
    if y == 0: return shade(STEEL, -10)
    if y == 1: return shade((30, 31, 33), -20)
    return shade((32, 32, 34), grain('rbmk_displacer', x, y, 1.1) + (6 if x % 4 == 1 else 0))


def connector(x, y):
    """The 1.25 m steel connector between graphite and absorber."""
    if y in (0, 15): return shade(STEEL, -18)
    return shade((128, 134, 138), grain('rbmk_connector', x, y, .7) + (15 if x < 7 else -14 if x > 8 else 0))


def shield_plug(x, y):
    """Dense steel/serpentinite plug through the two-metre upper biological shield."""
    if y in (0, 15): return shade(STEEL, -24)
    return shade((92, 108, 88), grain('rbmk_shield_plug', x, y, 1) + (12 if x < 7 else -12 if x > 8 else 0))


SERPENTINE = (78, 104, 74)
SHIELD = (164, 168, 162)


def serpentinite(x, y):
    """Green-grey serpentinite with pale veins of chrysotile and darker olivine flecks."""
    v = grain('serpentinite', x, y, 2.4)
    if (x + 2 * y) % 11 == 0 or (2 * x - y) % 13 == 0: return shade((150, 170, 138), v // 2)
    if grain('serpentinite_fleck', x, y, 1) > 18: return shade((44, 62, 46), 0)
    return shade(SERPENTINE, v)


def shield_top(x, y):
    """The top of a shield block: steel plate with a hatched warning band, as the RBMK hall floor over the reactor."""
    if x in (0, 15) or y in (0, 15): return shade(SHIELD, -36)
    if x in (1, 14) or y in (1, 14): return shade(SHIELD, 16 if x == 1 or y == 1 else -12)
    return shade(SHIELD, grain('shield_top', x, y, 1.2) + (6 if (x // 4 + y // 4) % 2 else -4))


def fuel_rod_item(x, y, spent):
    """A long clad fuel rod on the diagonal: its lower half the pellet stack, its upper half plain tube."""
    d = x - (15 - y)
    if abs(d) > 1 or x < 1 or x > 14: return None
    clad = (176, 182, 186) if not spent else (104, 98, 86)
    if x <= 7 and x % 2 == 1 and d == 0: return shade((120, 128, 74) if not spent else (40, 38, 36), 0)
    if x in (1, 14): return shade((92, 96, 100) if not spent else (58, 56, 54), 0)
    return shade(clad, (22 if d == -1 else -22 if d == 1 else 0) + grain('fuel_rod' + str(spent), x, y, 1.4 if spent else .7))


def control_rod_item(x, y, spent):
    """A control rod on the diagonal: black graphite at its foot, boron carbide in steel with warning stripes above."""
    d = x - (15 - y)
    if abs(d) > 1 or x < 1 or x > 14: return None
    if x <= 6: return shade((34, 34, 36), (10 if d == -1 else -10 if d == 1 else 0))
    if x == 7: return shade(STEEL, 0)
    if x % 3 == 0: return shade(WARNING[(x // 3) % 2], 0 if not spent else -40)
    return shade((50, 52, 56) if not spent else (76, 64, 52), (16 if d == -1 else -14 if d == 1 else 0) + grain('control_rod' + str(spent), x, y, 1))


def tube_side(x, y):
    """The upper bundle casing, with two of its four fine tubes showing as ribs."""
    if y >= 14: return shade(STEEL, -20 if y == 15 else 10)
    if x in (0, 15): return shade(STEEL, -30)
    if x in (3, 4, 11, 12): return shade((176, 182, 186), (14 if x in (3, 11) else -12) + grain('rbmk_hall_tube', x, y, .6))
    return shade(STEEL, grain('rbmk_hall_casing', x, y, .9) + (-8 if x in (7, 8) else 0))


PLATE = (150, 153, 151)


def cap_quarters(x, y):
    """A continuous operating-floor plate under the four separately rendered channel caps."""
    if x == 0 or y == 0: return shade(PLATE, -38)
    if x == 1 or y == 1: return shade(PLATE, 14)
    if x == 15 or y == 15: return shade(PLATE, -22)
    return shade(PLATE, grain('rbmk_operating_plate', x, y, 1.1) - (4 if (x + 2 * y) % 7 == 0 else 0))


def channel_cap(x, y, colour, control=False, empty=False):
    """One close-packed pressure-channel cover, with a flanged bore or the control-drive identification slot."""
    if x == 0 or y == 0: return shade(colour, -46)
    if x == 15 or y == 15: return shade(colour, -30)
    if x == 1 or y == 1: return shade(colour, 20)
    if x == 14 or y == 14: return shade(colour, -16)
    if 6 <= x <= 9 and 6 <= y <= 9:
        centre = x in (7, 8) and y in (7, 8)
        if centre: return shade((28, 31, 32), -8 if y == 8 else 5)
        ring = STEEL if not control else colour
        return shade(ring, 14 if x == 6 or y == 6 else -16)
    r = math.hypot(x - 7.5, y - 7.5)
    if empty:
        if r < 3.2: return shade((26, 29, 30), -8 if y > 7 else 5)
        if r < 4.6: return shade(STEEL, 18 if x + y < 15 else -24)
    elif control:
        if 5 <= x <= 10 and 7 <= y <= 8: return shade((31, 33, 34), -8)
        if 4 <= x <= 11 and y in (6, 9): return shade(colour, -25)
    else:
        if r < 1.8: return shade((34, 37, 38), -10 if y > 7 else 5)
        if r < 3.6: return shade(STEEL, 20 if x + y < 15 else -22)
        if r < 4.3: return shade((93, 98, 98), -8)
    return shade(colour, grain('rbmk_channel_cap', x, y, 1.2) + (4 if x < 8 and y < 8 else -4))


def cap_side(x, y):
    if y in (0, 15) or x in (0, 15): return shade(PLATE, -40)
    return shade(PLATE, grain('rbmk_plate_side', x, y, 1) - (10 if y > 11 else 0))


def port_face(x, y, colour):
    """A shield block face with a flanged port: a steel ring round a coloured bore, blue for coolant in, red for coolant out."""
    r = math.hypot(x - 7.5, y - 7.5)
    if r < 3: return shade(colour, -30 + (12 if r < 1.5 else 0))
    if r < 5: return shade(STEEL, 20 if y < 8 else -18)
    if r < 5.8: return shade(STEEL, -40)
    return shield_top(x, y)


def drive_face(x, y):
    """The rotation input's shaft face: a brass bearing round the shaft opening in the shield."""
    r = math.hypot(x - 7.5, y - 7.5)
    if 6 <= x <= 9 and 6 <= y <= 9: return shade((40, 40, 42), 0)
    if r < 4.5: return shade((199, 158, 61), 18 if y < x else -16)
    if r < 5.3: return shade(STEEL, -40)
    return shield_top(x, y)


def bundle_top(x, y):
    """The end of a feed bundle: a cluster of thin steel tubes, each a dark bore in a bright ring."""
    lx, ly = x % 4, y % 4
    if (lx, ly) in ((1, 1), (2, 1), (1, 2), (2, 2)): return shade((30, 34, 38), 0)
    if lx in (0, 3) and ly in (0, 3): return shade((60, 64, 66), 0)
    return shade((176, 182, 186), (12 if ly == 0 or lx == 0 else -10) + grain('rbmk_bundle', x, y, .6))


def bundle_side(x, y):
    """A feed bundle from the side: thin tubes running up, bound by two steel bands."""
    if y in (3, 12): return shade(STEEL, 14 if x % 4 == 0 else -6)
    return shade((176, 182, 186), (16 if x % 2 == 0 else -18) + grain('rbmk_bundle_side', x, y, .6))


def link_top(x, y):
    """A 100-step rod-control link: a brass panel with two frequency sockets and a red warning edge."""
    if x in (0, 15) or y in (0, 15): return shade(WARNING[1], -10)
    if 3 <= y <= 12 and (2 <= x <= 6 or 9 <= x <= 13): return shade((60, 52, 40), 0 if 4 <= y <= 11 else 20)
    return shade((199, 158, 61), grain('rbmk_link', x, y, .8) + (14 if y < 8 else -10))


CONSOLE = (92, 101, 94)
CONSOLE_DARK = (44, 51, 48)
CONSOLE_CREAM = (183, 176, 143)


def console_metal(x, y):
    if x in (0, 15) or y in (0, 15): return shade(CONSOLE_DARK, -18)
    if x in (1, 14) or y == 1: return shade(CONSOLE, 15)
    return shade(CONSOLE, grain('rbmk_console_metal', x, y, 1.1) + (-8 if y > 11 else 0))


def console_desk(x, y):
    """Restrained Create-style operator desk: green-grey plate, brass screws and a guarded red AZ-5 field."""
    if x in (0, 15) or y in (0, 15): return shade(CONSOLE_DARK, -20)
    if (x, y) in ((2, 2), (13, 2), (2, 13), (13, 13)): return shade((183, 143, 58), 4)
    if 10 <= x <= 14 and 3 <= y <= 8:
        if x in (10, 14) or y in (3, 8): return shade((190, 162, 72), -12)
        return shade((96, 32, 28), 4 if y < 6 else -14)
    if 2 <= x <= 7 and 3 <= y <= 5: return shade((48, 58, 51), 12 if y == 3 else -8)
    return shade(CONSOLE, grain('rbmk_console_desk', x, y, 1.0) + (8 if y < 7 else -7))


def pixel_text(draw, x, y, text, colour):
    for char in text:
        glyph = PIXEL_FONT.get(char)
        if glyph:
            for gy, line in enumerate(glyph):
                for gx, bit in enumerate(line):
                    if bit == '1': draw.point((x + gx, y + gy), fill=colour)
        x += 4


def desk_tile(column, depth):
    """One 16 px section of the six-wide stepped operator console."""
    image = pixel_panel((16, 16), (147, 146, 128), f'rbmk_desk_{column}_{depth}'); draw = ImageDraw.Draw(image)
    if column == 0: draw.line((0, 0, 0, 15), fill=(41, 47, 45, 255))
    if column == 5: draw.line((15, 0, 15, 15), fill=(41, 47, 45, 255))
    draw.line((0, 0, 15, 0), fill=(194, 188, 158, 255))
    draw.line((0, 15, 15, 15), fill=(67, 71, 66, 255))
    for screw in ((2, 2), (13, 2), (2, 13), (13, 13)): draw.point(screw, fill=(179, 142, 57, 255))
    if depth == 0:
        if column == 2:
            draw.rectangle((0, 1, 15, 7), fill=(46, 48, 44, 255), outline=(80, 68, 45, 255))
            pixel_text(draw, 1, 2, 'AZ-5', (226, 54, 39, 255))
            draw.rectangle((9, 9, 15, 14), outline=(181, 143, 55, 255))
            draw.rectangle((11, 10, 14, 13), fill=(151, 38, 32, 255))
        elif column == 3:
            draw.rectangle((0, 1, 15, 7), fill=(47, 48, 43, 255), outline=(80, 68, 45, 255))
            pixel_text(draw, 2, 2, '221', (220, 58, 39, 255))
            for x in (3, 7, 11): draw.rectangle((x, 10, x + 1, 12), fill=(61, 63, 57, 255))
        else:
            for cx in (4, 12):
                draw.rectangle((cx - 3, 2, cx + 2, 7), fill=(57, 60, 55, 255), outline=(183, 175, 142, 255))
                draw.point((cx, 4), fill=(197, 65, 45, 255))
            for x in (3, 7, 11): draw.rectangle((x, 10, x + 1, 12), fill=(63, 66, 60, 255))
    else:
        if column == 1:
            draw.rectangle((2, 2, 13, 13), fill=(103, 60, 39, 255), outline=(56, 42, 34, 255))
            draw.line((3, 3, 12, 3), fill=(144, 91, 54, 255))
        elif column == 3:
            draw.arc((2, 1, 13, 13), 190, 350, fill=(48, 52, 48, 255))
            for x in range(3, 14, 2): draw.point((x, 11 - abs(8 - x) // 2), fill=(189, 161, 80, 255))
        else:
            colours = ((137, 45, 38, 255), (190, 144, 43, 255), (70, 117, 76, 255), (204, 196, 163, 255))
            for by in range(3):
                for bx in range(4):
                    x, y = 2 + bx * 3, 3 + by * 4
                    draw.rectangle((x, y, x + 1, y + 1), fill=colours[(bx + by + column) % len(colours)])
    return image


def console_cabinet(x, y):
    if x in (0, 15) or y in (0, 15): return shade((28, 31, 30), -12)
    if x < 10: return shade((39, 43, 42), grain('rbmk_console_cabinet_dark', x, y, .7))
    return shade((176, 174, 156), grain('rbmk_console_cabinet_light', x, y, .6) + (10 if x == 10 else 0))


def solid(colour, name):
    return lambda x, y: shade(colour, grain(name, x, y, .45) + (10 if x < 8 else -8))


def console_glass(x, y):
    alpha = 72 if x + y in (5, 6, 7, 21, 22) else 34
    return (150, 181, 177, alpha)


def textures():
    save('graphite', graphite)
    save('graphite_top', graphite_top)
    save('fuel_channel_top', fuel_top)
    save('fuel_channel_bottom', fuel_bottom)
    save('fuel_channel_side', fuel_side)
    save('absorber', absorber)
    save('displacer', displacer)
    save('connector', connector)
    save('shield_plug', shield_plug)
    save('fuel_channel_tube', tube_side)
    save('top_cap', cap_quarters)
    save('top_cap_side', cap_side)
    save('channel_cap_fuel', lambda x, y: channel_cap(x, y, CAP))
    for name, colour in CONTROL_CAPS.items():
        save('channel_cap_' + name, lambda x, y, colour=colour: channel_cap(x, y, colour, control=True))
    save('channel_cap_control_empty', lambda x, y: channel_cap(x, y, (119, 123, 121), empty=True))
    save('shield_outlet', lambda x, y: port_face(x, y, (170, 52, 44)))
    save('shield_drive', drive_face)
    save('feed_bundle_top', bundle_top)
    save('feed_bundle_side', bundle_side)
    save('control_rod_link_top', link_top)
    save('console_metal', console_metal)
    save('console_cabinet', console_cabinet)
    save('console_case', solid((77, 80, 75), 'rbmk_console_case'))
    save('console_glass', console_glass)
    desk_tile(3, 1).save(T / 'console_desk_c3_d1.png')
    save('console_needle', solid((190, 64, 44), 'rbmk_console_needle'))
    save('console_lamp_red', solid((184, 45, 36), 'rbmk_lamp_red'))
    save('console_lamp_white', solid((250, 250, 250), 'rbmk_lamp_white'))
    save('console_lamp_green', solid((78, 174, 91), 'rbmk_lamp_green'))
    save('console_lamp_selected', solid((236, 190, 44), 'rbmk_lamp_selected'))
    save('console_az5', solid((222, 44, 34), 'rbmk_az5'))
    temperature_colours = ((55, 154, 73), (95, 174, 68), (151, 183, 61), (205, 179, 52),
                           (224, 143, 44), (218, 99, 39), (204, 60, 37), (179, 35, 31))
    for i, colour in enumerate(temperature_colours): save(f'console_fuel_{i}', solid(colour, f'rbmk_fuel_lamp_{i}'))
    save('console_flow_face', solid((198, 191, 157), 'rbmk_flow_face'))
    save('recorder_paper', solid((213, 204, 172), 'rbmk_recorder_paper'))
    save('recorder_water', solid((52, 114, 174), 'rbmk_recorder_water'))
    save('recorder_graphite', solid((198, 101, 42), 'rbmk_recorder_graphite'))
    save('fuel_rod_tube', lambda x, y: shade((176, 182, 186), (18 if x < 7 else -16 if x > 8 else 0) + (-26 if y == 0 else 0) + grain('rbmk_tube_hall', x, y, .6)))
    save('serpentinite', serpentinite, A / 'textures/block')
    save('biological_shield_top', shield_top)
    save('fuel_rod', lambda x, y: fuel_rod_item(x, y, False), A / 'textures/item')
    save('spent_fuel_rod', lambda x, y: fuel_rod_item(x, y, True), A / 'textures/item')
    save('control_rod', lambda x, y: control_rod_item(x, y, False), A / 'textures/item')
    save('spent_control_rod', lambda x, y: control_rod_item(x, y, True), A / 'textures/item')


def tex(name): return NS + 'block/reactor/rbmk/' + name


def face(uv, texture, cull=None, rotation=None):
    f = {'uv': uv, 'texture': '#' + texture}
    if cull: f['cullface'] = cull
    if rotation: f['rotation'] = rotation
    return f


def box(b, faces):
    return {'from': list(b[:3]), 'to': list(b[3:]), 'faces': faces}


def block_model(name, textures, elements=None, parent='minecraft:block/block', render=None):
    data = {'parent': parent, 'textures': textures}
    if render: data['render_type'] = render
    if elements: data['elements'] = elements
    write(A / 'models/block/rbmk' / f'{name}.json', data)
    return NS + 'block/rbmk/' + name


def rod_model(name, texture):
    side = lambda: face([7, 0, 9, 16], 't')
    return block_model(name, {'t': tex(texture), 'particle': tex(texture)}, [box((7, 0, 7, 9, 16, 9), {
        'north': side(), 'south': side(), 'east': side(), 'west': side(),
        'up': face([7, 7, 9, 9], 't', 'up'), 'down': face([7, 7, 9, 9], 't', 'down')})], render='minecraft:cutout')


BLOCKS = ['fuel_pool', 'reactor_feed_bundle', 'control_rod_link', 'control_rod_controller', 'reactor_control_console', 'reactor_graphite', 'reactor_fuel_channel', 'reactor_fuel_channel_tube', 'reactor_top_cap', 'biological_shield_drive',
          'biological_shield_coolant_outlet', 'serpentinite', 'biological_shield']
ITEMS = ('fuel_rod', 'spent_fuel_rod', 'control_rod', 'spent_control_rod')


def models():
    graphite = block_model('graphite', {'side': tex('graphite'), 'end': tex('graphite_top')}, parent='minecraft:block/cube_column')
    fuel = block_model('fuel_channel', {'side': tex('fuel_channel_side'), 'top': tex('fuel_channel_top'), 'bottom': tex('fuel_channel_bottom')}, parent='minecraft:block/cube_bottom_top')
    tube = block_model('fuel_channel_tube', {'side': tex('fuel_channel_tube'), 'end': tex('fuel_channel_top')}, parent='minecraft:block/cube_column')
    shield = block_model('biological_shield', {'all': tex('biological_shield_top'), 'particle': tex('biological_shield_top')}, parent='minecraft:block/cube_all')
    rod_model('control_rod_absorber', 'absorber')
    rod_model('control_rod_connector', 'connector')
    rod_model('control_rod_displacer', 'displacer')
    rod_model('fuel_rod_shield_plug', 'shield_plug')
    for id, face_tex in (('biological_shield_coolant_outlet', 'shield_outlet'),):
        model = block_model(id, {'all': tex(face_tex), 'particle': tex(face_tex)}, parent='minecraft:block/cube_all')
        write(A / 'blockstates' / f'{id}.json', {'variants': {'': {'model': model}}})
        write(A / 'models/item' / f'{id}.json', {'parent': model})
    drive = block_model('biological_shield_drive', {'side': tex('biological_shield_top'), 'end': tex('shield_drive')}, parent='minecraft:block/cube_column')
    write(A / 'blockstates/biological_shield_drive.json', {'variants': {'axis=y': {'model': drive}, 'axis=x': {'model': drive, 'x': 90, 'y': 90}, 'axis=z': {'model': drive, 'x': 90}}})
    write(A / 'models/item/biological_shield_drive.json', {'parent': drive})
    cap_model = block_model('top_cap', {'top': tex('top_cap'), 'bottom': tex('top_cap_side'), 'side': tex('top_cap_side')}, parent='minecraft:block/cube_bottom_top')
    write(A / 'blockstates/reactor_top_cap.json', {'variants': {'': {'model': cap_model}}})
    write(A / 'models/item/reactor_top_cap.json', {'parent': cap_model})
    from generate_fuel_pool_assets import models as pool_models
    pool_models()
    bundle = block_model('feed_bundle', {'side': tex('feed_bundle_side'), 'end': tex('feed_bundle_top')}, parent='minecraft:block/cube_column')
    write(A / 'blockstates/reactor_feed_bundle.json', {'variants': {'': {'model': bundle}}})
    write(A / 'models/item/reactor_feed_bundle.json', {'parent': bundle})
    link = block_model('control_rod_link', {'top': tex('control_rod_link_top'), 'bottom': tex('top_cap_side'), 'side': tex('top_cap_side')}, parent='minecraft:block/cube_bottom_top')
    write(A / 'blockstates/control_rod_link.json', {'variants': {'': {'model': link}}})
    write(A / 'models/item/control_rod_link.json', {'parent': link})
    # The controller remains reusable, but its cabinet-sized pedestal also fills its position in the large desk.
    every = lambda texture, uv: {f: face(uv, texture) for f in ('north', 'south', 'east', 'west', 'up', 'down')}
    t = {'metal': tex('console_metal'), 'panel': tex('console_desk_c3_d1'), 'knob': tex('channel_cap_control'), 'particle': tex('console_metal')}
    controller = block_model('control_rod_controller', t, [box((0, 0, 0, 16, 7, 16), every('metal', [0, 0, 16, 16])),
                                                         box((0, 7, 1, 16, 10, 15), every('panel', [0, 1, 16, 15])),
                                                         box((7, 10, 6, 9, 12, 10), every('metal', [7, 6, 9, 10])),
                                                         box((7.5, 12, 7.5, 8.5, 14, 8.5), every('metal', [7, 6, 8, 14])),
                                                         box((6.5, 14, 6.5, 9.5, 16, 9.5), every('knob', [6, 6, 9, 9]))], render='minecraft:cutout')
    write(A / 'blockstates/control_rod_controller.json', {'variants': {'': {'model': controller}}})
    write(A / 'models/item/control_rod_controller.json', {'parent': controller})

    # Renderer-only pieces are deliberately pixel-sized, so even a 51-rod map remains readable rather than photorealistic.
    dynamic = A / 'models/block/reactor_console'
    for name, texture in {'white': 'console_lamp_white'}.items():
        write(dynamic / f'rod_lamp_{name}.json', {'parent': 'minecraft:block/block', 'textures': {'lamp': tex(texture), 'particle': tex(texture)}, 'elements': [
            box((7.5, 7.5, 2, 8.5, 8.5, 2.8), every('lamp', [7, 7, 9, 9]))]})
    ring = [box(bounds, every('lamp', [6, 6, 10, 10])) for bounds in (
        (6.8, 6.8, 1.8, 9.2, 7.15, 2.9), (6.8, 8.85, 1.8, 9.2, 9.2, 2.9),
        (6.8, 7.15, 1.8, 7.15, 8.85, 2.9), (8.85, 7.15, 1.8, 9.2, 8.85, 2.9))]
    write(dynamic / 'rod_lamp_selected.json', {'parent': 'minecraft:block/block', 'textures': {'lamp': tex('console_lamp_selected'), 'particle': tex('console_lamp_selected')}, 'elements': ring})
    for i in range(8):
        write(dynamic / f'fuel_lamp_{i}.json', {'parent': 'minecraft:block/block', 'textures': {
            'lamp': tex(f'console_fuel_{i}'), 'particle': tex(f'console_fuel_{i}')}, 'elements': [
            box((7.55, 7.55, 1.85, 8.45, 8.45, 2.55), every('lamp', [7, 7, 9, 9]))]})
    from generate_reactor_console import main as console_models
    console_models()
    write(A / 'models/block/serpentinite.json', {'parent': 'minecraft:block/cube_all', 'textures': {'all': NS + 'block/serpentinite'}})
    simple = {'reactor_graphite': graphite, 'reactor_fuel_channel': fuel, 'reactor_fuel_channel_tube': tube, 'biological_shield': shield,
              'serpentinite': NS + 'block/serpentinite'}
    for id, model in simple.items():
        write(A / 'blockstates' / f'{id}.json', {'variants': {'': {'model': model}}})
        write(A / 'models/item' / f'{id}.json', {'parent': model})
    # The plain upper tube of a loaded fuel rod, a block long, drawn up the hall over each hole and under the crane's grapple.
    write(A / 'models/block/rbmk/fuel_rod_tube.json', {'parent': 'minecraft:block/block', 'textures': {'t': tex('fuel_rod_tube'), 'particle': tex('fuel_rod_tube')}, 'elements': [
        box((7, 0, 7, 9, 16, 9), {f: face([7, 0, 9, 16], 't') for f in ('north', 'south', 'east', 'west')} | {f: face([7, 7, 9, 9], 't') for f in ('up', 'down')})]})
    cap_models = ['fuel', *CONTROL_CAPS, 'control_empty']
    for kind in cap_models:
        id = 'channel_cap_' + kind
        block_model(id, {'top': tex(id), 'side': tex('top_cap_side'), 'particle': tex(id)}, [
            box((5, 16, 5, 11, 16.5, 11), {
                'up': face([5, 5, 11, 11], 'top'),
                'down': face([5, 5, 11, 11], 'side'),
                **{f: face([5, 15, 11, 16], 'side') for f in ('north', 'south', 'east', 'west')},
            })], render='minecraft:cutout')
    for id in ITEMS:
        write(A / 'models/item' / f'{id}.json', {'parent': 'minecraft:item/generated', 'textures': {'layer0': NS + 'item/' + id}})


def data():
    for id in BLOCKS:
        write(D / 'loot_table/blocks' / f'{id}.json', {'type': 'minecraft:block', 'pools': [{'rolls': 1, 'entries': [{'type': 'minecraft:item', 'name': NS + id}],
                                                                                             'conditions': [{'condition': 'minecraft:survives_explosion'}]}]})
    for gone in ('control_rod_absorber', 'control_rod_displacer', 'control_rod_drive', 'reactor_control_rod_channel', 'control_rod_winch',
                 'biological_shield_coolant_inlet'):
        for path in (D / 'loot_table/blocks' / f'{gone}.json', D / 'recipe' / f'{gone}.json'):
            if path.exists(): path.unlink()
    tag = A.parents[1] / 'data/minecraft/tags/block/mineable/pickaxe.json'; values = json.loads(tag.read_text())
    values['values'] = [v for v in values['values'] if v not in (NS + 'control_rod_absorber', NS + 'control_rod_displacer', NS + 'control_rod_drive',
                                                                  NS + 'reactor_control_rod_channel', NS + 'control_rod_winch',
                                                                  NS + 'biological_shield_coolant_inlet')]
    for id in BLOCKS:
        if NS + id not in values['values']: values['values'].append(NS + id)
    write(tag, values)
    r = D / 'recipe'
    shaped = lambda id, pattern, key, count=1: write(r / f'{id}.json', {'type': 'minecraft:crafting_shaped', 'pattern': pattern,
                                                                      'key': {k: v if isinstance(v, dict) else {'item': v} for k, v in key.items()}, 'result': {'id': NS + id, 'count': count}})
    shaped('fuel_rod', ['PPP', 'SPS', 'PPP'], {'S': NS + 'zircaloy_cladding_tube', 'P': NS + 'uranium_pellet'})
    write(r / 'compacting/reactor_graphite.json', {'type': 'create:compacting', 'heat_requirement': 'superheated',
                                                   'ingredients': [{'item': 'minecraft:coal_block'}], 'results': [{'id': NS + 'reactor_graphite'}]})
    shaped('reactor_fuel_channel', ['P', 'G', 'P'], {'P': 'create:fluid_pipe', 'G': NS + 'reactor_graphite'})
    shaped('reactor_fuel_channel_tube', ['S', 'P', 'S'], {'S': 'create:iron_sheet', 'P': 'create:fluid_pipe'}, 2)
    shaped('control_rod', ['SBS', 'SBS', 'SGS'], {'S': 'create:iron_sheet', 'B': 'minecraft:coal_block', 'G': NS + 'reactor_graphite'})
    shaped('biological_shield', ['RSR', 'SRS', 'RSR'], {'R': NS + 'serpentinite', 'S': 'create:iron_sheet'}, 4)
    shaped('reactor_feed_bundle', ['PPP', 'PPP', 'PPP'], {'P': 'create:fluid_pipe'}, 4)
    shaped('control_rod_link', ['B', 'L'], {'B': 'create:brass_sheet', 'L': 'create:redstone_link'})
    shaped('control_rod_controller', ['A', 'E'], {'A': 'create:analog_lever', 'E': 'create:electron_tube'})
    shaped('reactor_control_console', ['RPR', 'BCB'], {'R': 'minecraft:redstone', 'P': 'create:precision_mechanism', 'B': 'create:brass_sheet', 'C': 'create:brass_casing'}, 4)
    shaped('reactor_top_cap', ['SSS', 'SRS'], {'S': 'create:iron_sheet', 'R': NS + 'serpentinite'}, 4)
    shaped('biological_shield_drive', ['B', 'S'], {'B': NS + 'biological_shield', 'S': 'create:shaft'})
    shaped('biological_shield_coolant_outlet', ['V', 'P', 'B'], {'B': NS + 'biological_shield', 'P': 'create:fluid_pipe', 'V': 'create:fluid_valve'})
    # Serpentinite in large blobs through the overworld stone, like Create's and vanilla's stone variants.
    write(D / 'worldgen/configured_feature/serpentinite.json', {'type': 'minecraft:ore', 'config': {'size': 48, 'discard_chance_on_air_exposure': 0.0, 'targets': [
        {'target': {'predicate_type': 'minecraft:tag_match', 'tag': 'minecraft:stone_ore_replaceables'}, 'state': {'Name': NS + 'serpentinite'}}]}})
    write(D / 'worldgen/placed_feature/serpentinite.json', {'feature': NS + 'serpentinite', 'placement': [
        {'type': 'minecraft:count', 'count': 2}, {'type': 'minecraft:in_square'},
        {'type': 'minecraft:height_range', 'height': {'type': 'minecraft:uniform', 'min_inclusive': {'absolute': 0}, 'max_inclusive': {'absolute': 96}}},
        {'type': 'minecraft:biome'}]})
    write(D / 'neoforge/biome_modifier/serpentinite.json', {'type': 'neoforge:add_features', 'biomes': '#minecraft:is_overworld', 'features': NS + 'serpentinite',
                                                            'step': 'underground_ores'})
    write(D / 'damage_type/acute_radiation_syndrome.json', {'message_id': 'create_refinery_n_reactors.acute_radiation_syndrome', 'scaling': 'never', 'exhaustion': 0.0})
    for name in ('bypasses_armor', 'bypasses_shield', 'no_knockback'):
        write(A.parents[1] / f'data/minecraft/tags/damage_type/{name}.json', {'replace': False, 'values': [NS + 'acute_radiation_syndrome']})


NAMES = {
    'block.create_refinery_n_reactors.reactor_graphite': ('Graphite Moderator', '黒鉛減速材'),
    'block.create_refinery_n_reactors.reactor_fuel_channel': ('Fuel Channel Block', '燃料チャネルブロック'),
    'block.create_refinery_n_reactors.reactor_fuel_channel_tube': ('Fuel Channel Tube', '燃料チャネル上部管'),
    'block.create_refinery_n_reactors.serpentinite': ('Serpentinite', '蛇紋岩'),
    'block.create_refinery_n_reactors.biological_shield': ('Biological Shield', 'バイオロジカルシールド'),
    'block.create_refinery_n_reactors.biological_shield_drive': ('Biological Shield Rotation Input', 'バイオシールド回転動力入力部'),
    'block.create_refinery_n_reactors.biological_shield_coolant_outlet': ('Biological Shield Coolant Outlet', 'バイオシールド冷媒出力部'),
    'block.create_refinery_n_reactors.reactor_top_cap': ('Reactor Top Cap', '原子炉上部キャップ'),
    'block.create_refinery_n_reactors.fuel_pool': ('Fuel Pool', '燃料プール'),
    'block.create_refinery_n_reactors.reactor_feed_bundle': ('Feed Tube Bundle', '給水細管束'),
    'block.create_refinery_n_reactors.control_rod_link': ('Control Rod Link', '制御棒リンク'),
    'block.create_refinery_n_reactors.control_rod_controller': ('Control Rod Controller', '制御棒コントローラー'),
    'block.create_refinery_n_reactors.reactor_control_console': ('Reactor Control Console', '原子炉制御卓'),
    'control_rod_link.create_refinery_n_reactors.setting': ('Sending depth %s of 100', '送信中の挿入深度 %s／100'),
    'control_rod_link.create_refinery_n_reactors.idle': ('No controller attached', 'コントローラー未接続'),
    'control_rod_controller.create_refinery_n_reactors.setting': ('Rod depth (0 out, 100 in)', '制御棒挿入深度（0＝全引抜、100＝全挿入）'),
    'reactor_control_console.create_refinery_n_reactors.status.linked': ('Reactor linked: %s', '原子炉接続：%s'),
    'reactor_control_console.create_refinery_n_reactors.status.missing': ('Complete desk; target it from a Create Display Link', '制御卓は完成、Createのディスプレイリンク未接続'),
    'reactor_control_console.create_refinery_n_reactors.status.unlinked': ('Display Link source has no formed reactor', 'ディスプレイリンクの情報源に形成済み原子炉なし'),
    'reactor_control_console.create_refinery_n_reactors.status.ambiguous': ('Display Links target this room from multiple reactors', '複数原子炉のディスプレイリンクが同じ制御室を指定'),
    'reactor_control_console.create_refinery_n_reactors.status.incomplete': ('Incomplete control desk', '制御卓が未完成'),
    'reactor_control_console.create_refinery_n_reactors.incomplete': ('Complete the 6 x 2 desk and both 5 x 5 wall panels first', '先に6×2の制御卓と2面の5×5壁面パネルを完成させてください'),
    'reactor_control_console.create_refinery_n_reactors.no_display_link': ('Target this desk from a Create Display Link attached to the reactor', '原子炉に付けたCreateのディスプレイリンクからこの制御卓を指定してください'),
    'reactor_control_console.create_refinery_n_reactors.no_core': ('The Display Link source is not a loaded formed reactor', 'ディスプレイリンクの情報源が読み込み済みの形成炉心ではありません'),
    'reactor_control_console.create_refinery_n_reactors.ambiguous_link': ('Only one reactor may target a control room', '1つの制御室を指定できる原子炉は1基だけです'),
    'reactor_control_console.create_refinery_n_reactors.selected': ('Selected rod X%s Z%s / position %s; target %s%%', '制御棒 X%s Z%s／区画%sを選択、目標%s%%'),
    'reactor_control_console.create_refinery_n_reactors.released': ('Rod X%s Z%s / position %s returned to cap control', '制御棒 X%s Z%s／区画%sをキャップ制御に戻しました'),
    'reactor_control_console.create_refinery_n_reactors.no_selection': ('No individually controlled rod is selected', '個別制御中の制御棒が選択されていません'),
    'reactor_control_console.create_refinery_n_reactors.az5_pressed': ('AZ-5 pressed: emergency insertion latched', 'AZ-5作動：緊急全挿入をラッチ'),
    'reactor_control_console.create_refinery_n_reactors.az5_reset': ('AZ-5 desk latch reset', 'AZ-5制御卓ラッチを解除'),
    'reactor_control_console.create_refinery_n_reactors.goggle.no_selection': ('Core map: click a control-rod lamp', '炉心マップ：制御棒ランプをクリック'),
    'reactor_control_console.create_refinery_n_reactors.goggle.selection': ('Selected X%s Z%s, fine position %s of 4', '選択 X%s Z%s、4区画中%s番'),
    'reactor_control_console.create_refinery_n_reactors.goggle.rod': ('Actual %s%%; target %s%%', '実深度 %s%%、目標 %s%%'),
    'reactor_control_console.create_refinery_n_reactors.goggle.az5_active': ('AZ-5 ACTIVE: every rod commanded fully in', 'AZ-5 作動中：全制御棒を全挿入'),
    'reactor_control_console.create_refinery_n_reactors.goggle.az5_ready': ('AZ-5 ready; sneak-use the button to reset', 'AZ-5 待機、スニーク操作でリセット'),
    'reactor_control_console.create_refinery_n_reactors.cap_control': ('cap', 'キャップ'),
    'fuel_pool.create_refinery_n_reactors.rods': ('Fuel rods: %s fresh, %s spent of %s places', '燃料棒 新%s・使用済%s／%s本'),
    'item.create_refinery_n_reactors.fuel_rod': ('Fuel Rod', '燃料棒'),
    'item.create_refinery_n_reactors.spent_fuel_rod': ('Spent Fuel Rod', '使用済み燃料棒'),
    'item.create_refinery_n_reactors.control_rod': ('Control Rod', '制御棒'),
    'item.create_refinery_n_reactors.spent_control_rod': ('Spent Control Rod', '使用済み制御棒'),
    'death.attack.create_refinery_n_reactors.acute_radiation_syndrome': ('%1$s died of acute radiation syndrome', '%1$sは急性放射線障害で死亡した'),
    'death.attack.create_refinery_n_reactors.acute_radiation_syndrome.player': ('%1$s died of acute radiation syndrome', '%1$sは急性放射線障害で死亡した'),
    'radiation.create_refinery_n_reactors.stage.1': ('You feel sick to your stomach', '吐き気がする'),
    'radiation.create_refinery_n_reactors.stage.2': ('Your head is spinning', 'めまいがする'),
    'radiation.create_refinery_n_reactors.stage.3': ('You are weak and feverish', '体に力が入らず、熱がある'),
    'radiation.create_refinery_n_reactors.stage.4': ('Acute radiation syndrome: your body is failing', '急性放射線障害：体が衰弱していく'),
    'radiation.create_refinery_n_reactors.stage.5': ('A lethal dose of radiation', '致死量の放射線を浴びた'),
}
CORE = {
    'status.structure': ('Not a complete core', '炉心が完成していない'),
    'status.height': ('The core must be 7 blocks high', '炉心の高さは7ブロック'),
    'status.size': ('Core width and depth must be 5 to 13 blocks', '炉心の幅と奥行きは5～13ブロック'),
    'status.shape': ('The footprint must be a full rectangle or a circle', '炉心の平面形は隙間の無い長方形か円にする'),
    'status.unloaded': ('Part of the core is unloaded', '炉心の一部が読み込まれていない'),
    'status.column': ('Every column must be one kind from bottom to top', '各列は下から上まで同じ種類にする'),
    'status.outside': ('A core block touches the core from outside', '炉心の外側に炉心ブロックが接している'),
    'status.fuel': ('No fuel channel column', '燃料チャネルの列がない'),
    'status.subcritical': ('Subcritical', '未臨界'),
    'status.critical': ('Critical', '臨界'),
    'status.supercritical': ('Supercritical: power rising', '超臨界：出力上昇中'),
    'status.prompt_critical': ('PROMPT CRITICAL', '即発臨界'),
    'status.shutdown': ('Shut down', '停止中'),
    'status.scram': ('AZ-5 SCRAM', 'AZ-5 緊急停止'),
    'status.az5_danger': ('DANGER: rods out, xenon high, water near boiling. AZ-5 now would raise power', '危険：制御棒不足・キセノン過多・冷却水沸騰寸前。今AZ-5を押すと出力が上がる'),
    'status.rods_jammed': ('AZ-5 SURGE: rods jammed with their tips in the core', 'AZ-5で出力急上昇：制御棒が先端を炉心に入れたまま固着'),
    'status.unstable': ('UNSTABLE: rods out, power wandering and void feedback strong', '不安定：制御棒不足で出力が揺らぎ、ボイド効果が強い'),
    'status.overheat': ('FUEL OVERHEATING', '燃料過熱'),
    'status.meltdown': ('Core destroyed', '炉心崩壊'),
    'status.steam_explosion': ('Channels ruptured: steam explosion, core explosion follows', '燃料チャネル破損：蒸気爆発、続いて炉心が爆発'),
    'fault': ('Check X %s, Y %s, Z %s', '確認位置 X %s, Y %s, Z %s'),
    'power': ('%s MW, reactivity %s $, void %s %%', '%s MW、反応度 %s $、ボイド率 %s %%'),
    'core': ('Fuel %s °C average, %s °C hottest; absorber in %s %%; xenon %s %%; %s fuel / %s control channels',
             '燃料 平均%s ℃・最高%s ℃、吸収体挿入 %s %%、キセノン %s %%、燃料%s／制御%sチャネル'),
    'fuel': ('Assemblies: %s fresh, %s spent; %s fuel / %s control positions; water %s mB, void %s %%',
             '燃料集合体 新%s・使用済%s、燃料%s／制御%s区画、水 %s mB、ボイド率 %s %%'),
    'fuel_temperature': ('Fuel %s °C, xenon %s %% of equilibrium', '燃料 %s ℃、キセノン 平衡比 %s %%'),
    'rod': ('Absorber in core: this rod %s %%, burn-up %s %%', '炉心内の吸収体：この棒 %s %%、消耗 %s %%'),
    'scram': ('AZ-5 SCRAM active: all rods going in', 'AZ-5 発動中：全制御棒を挿入'),
    'scram_ready': ('Redstone on any channel triggers AZ-5', 'どのチャネルへのレッドストーン信号でもAZ-5'),
    'rod_unformed': ('Part of no formed core', '形成済みの炉心に属していない'),
}
DISPLAY = {
    'reactor_power': ('Thermal power (MW)', '原子炉熱出力（MW）'),
    'reactor_reactivity': ('Reactor reactivity ($)', '原子炉反応度（$）'),
    'reactor_void': ('Reactor void fraction', '原子炉ボイド率'),
    'reactor_fuel_temperature': ('Hottest fuel temperature', '最高燃料温度'),
    'reactor_xenon': ('Xenon (% of equilibrium)', 'キセノン（平衡比%）'),
    'reactor_rods': ('Absorber inserted', '吸収体挿入率'),
    'rod_depth': ('Control rod depth', '制御棒挿入深度'),
    'rod_target': ('Control rod target depth', '制御棒目標深度'),
    'reactor_coolant_temperature': ('Coolant temperature', '原子炉冷却水温度'),
    'reactor_average_fuel_temperature': ('Average fuel temperature', '平均燃料温度'),
    'reactor_instability': ('Core instability', '炉心不安定度'),
    'reactor_hazard': ('AZ-5 positive-scram risk', 'AZ-5正スクラム危険度'),
    'reactor_radiation_rate': ('Core-port radiation (mSv/h)', '炉ポート線量率（mSv/h）'),
    'reactor_fuel_channels': ('Fuel channel count', '燃料チャネル数'),
    'reactor_control_rods': ('Control rod count', '設置済み制御棒数'),
    'reactor_scram': ('AZ-5 SCRAM state', 'AZ-5緊急停止状態'),
    'reactor_graphite_temperature': ('Graphite temp. (°C)', '黒鉛温度（℃）'),
    'reactor_channel_flow': ('Channel flow (mB/t)', 'チャネル流量（mB/t）'),
    'reactor_channel_outlet_temperature': ('Channel outlet (°C)', '出口水温（℃）'),
}
PONDER = {
    'rbmk_core': ('Building an RBMK core', 'RBMK炉心を組み立てる', [
        ('graphite', 'Build a 7-high core, at least 5×5, with a two-block shield around it. Each fuel-channel block holds four rods.',
         '炉心は高さ7、最小5×5です。外周を厚さ2マスのシールドで囲みます。燃料チャネル1個に4本入ります。'),
        ('fuel', 'Wrench a cap position to select fuel or control service, then insert the matching rod.',
         'キャップの区画をレンチで燃料用・制御用に切り替え、対応する棒を入れます。'),
        ('rods', 'Control rods use the same channel blocks. Their drives are built into the caps.',
         '制御棒も同じチャネルへ入れます。駆動部はキャップに内蔵されています。'),
        ('shield', 'Above the core, add two shield layers and twelve tube-bundle layers, then fit the caps.',
         '炉心の上に遮蔽2段と管束12段を積み、上端へキャップを置きます。'),
        ('sizes', 'Core widths from 5 to 13 blocks are supported. Keep the surrounding shield two blocks thick.',
         '炉心は5～13マスに対応します。大きくする場合も、周囲のシールドは2マス厚です。'),
        ('water', 'Below the core, fill two metres of lower shield and five metres of control-rod clearance with feed tube bundles. Connect a Create pipe directly to any exposed bundle; water rises through the connected bed. The side outlet emits one wet water/steam stream for a gas-liquid separator.',
         '炉心の下は下部生体遮蔽体2メートルと制御棒用空間5メートルを給水細管束で満たします。露出した細管束へCreateのパイプを直接つなぐと、水が接続済みの束を上がります。側面出口からは水と蒸気の混相流が出るため、気液分離器へ送ります。'),
        ('drive', 'Drive the biological-shield input to move the control rods.',
         'バイオシールドの回転入力部へ動力を入れ、制御棒を動かします。'),
        ('readout', 'Goggles on any part of the reactor, shield, caps or ports, show the whole core: its state, power, reactivity, fuel temperatures, rods, xenon and any radiation leak.',
         'シールド、キャップ、入出力部など原子炉のどの部分にゴーグルを向けても、炉心全体の状態・出力・反応度・燃料温度・制御棒・キセノン・放射線漏れが表示されます。'),
        ('data_link', 'Attach a Display Link to a fuel channel or cap, then target a display or control room.',
         '燃料チャネルかキャップへDisplay Linkを付け、表示板や制御盤へつなぎます。'),
        ('hall', 'Place caps 15 blocks above the top core layer. Leave the upper service space clear.',
         'キャップは炉心最上段の15マス上です。燃料を扱う上部空間を空けておきます。'),
        ('crane', 'Use a gantry crane to exchange rods. Build the neighbouring fuel pool 22 blocks deep.',
         '門型クレーンで燃料棒を交換できます。隣の燃料プールは深さ22マスにします。'),
        ('scram', 'Redstone on the reactor triggers AZ-5, inserting every control rod within five seconds.',
         '炉へレッドストーン信号を入れるとAZ-5が作動し、全制御棒が5秒で入ります。'),
    ]),
    'rbmk_control_rods': ('Integrated control rods', '内蔵制御棒機構', [
        ('winch', 'Wrench a cap position to control service and insert a control rod. No separate winch is needed.',
         'キャップの区画をレンチで制御用にし、制御棒を入れます。別のウインチは不要です。'),
        ('relief', 'A jumping, steaming cap warns of overpressure. Lower power and check cooling and outlets.',
         'キャップが跳ねて蒸気が漏れたら過圧の警告です。出力を下げ、冷却と出口を確認します。'),
        ('rod', 'Move control rods in and out to regulate power. Avoid withdrawing too many.',
         '制御棒を出し入れして出力を調節します。抜きすぎないようにします。'),
        ('signal', 'Set depth with the cap handle or redstone. For remote control, use a rod link and controller; the link takes priority.',
         'キャップの手輪か赤石で深さを設定します。遠隔操作には制御棒リンクと制御器を使い、リンクの指令が優先します。'),
        ('tip', 'With rods mostly out, high xenon and hot water, AZ-5 can cause a power surge. Keep an operating margin.',
         '棒を抜きすぎ、キセノンが多く水温も高いと、AZ-5で出力が急上昇する危険があります。余裕を持って運転します。'),
        ('scram', 'AZ-5 overrides every cap target and inserts all rods within five seconds, with or without rotation.',
         'AZ-5は全キャップの目標値より優先し、回転の有無に関係なく5秒で全制御棒を挿入します。'),
    ]),

}


def translations():
    for language, index in (('en_us', 0), ('ja_jp', 1)):
        path = A / 'lang' / f'{language}.json'; raw = path.read_bytes().decode('utf8'); nl = '\r\n' if '\r\n' in raw else '\n'; data = json.loads(raw)
        for key in [k for k in data if k.startswith('control_rod_drive.') or k.startswith('control_rod_winch.')
                    or k.startswith('control_rod_link.create_refinery_n_reactors.role.') or k.startswith('control_rod_link.create_refinery_n_reactors.network.')
                    or k == 'reactor_control_console.create_refinery_n_reactors.no_data_link'
                    or k == 'block.create_refinery_n_reactors.biological_shield_coolant_inlet'
                    or k in ('block.create_refinery_n_reactors.control_rod_drive', 'block.create_refinery_n_reactors.control_rod_winch',
                             'block.create_refinery_n_reactors.reactor_control_rod_channel', 'block.create_refinery_n_reactors.control_rod_absorber',
                             'block.create_refinery_n_reactors.control_rod_displacer', 'item.create_refinery_n_reactors.spent_control_rod_absorber',
                             'reactor_core.create_refinery_n_reactors.status.rods')
                    or k.startswith('create_refinery_n_reactors.ponder.shared.rbmk_')]:
            del data[key]
        for key, values in NAMES.items(): data[key] = values[index]
        for key, values in CORE.items(): data['reactor_core.create_refinery_n_reactors.' + key] = values[index]
        for key, values in DISPLAY.items(): data['display.create_refinery_n_reactors.' + key] = values[index]
        for scene, (title_en, title_ja, steps) in PONDER.items():
            title = (title_en, title_ja)[index]
            data[f'create_refinery_n_reactors.ponder.shared.{scene}.title'] = title
            data[f'create_refinery_n_reactors.ponder.{scene}.header'] = title
            for key, en, ja in steps: data[f'create_refinery_n_reactors.ponder.shared.{scene}.{key}'] = (en, ja)[index]
        path.write_bytes((json.dumps(data, ensure_ascii=False, indent=2).replace('\n', nl) + nl).encode('utf8'))


# Five metres of lower tubes, two lower shield, seven core, two Elena and twelve upper tubes; the cap is 21 blocks above the core bottom.
LOWER, Y0, TOP, PLUG_TOP, CAP_Y = 2, 9, 15, 17, 30


def in_circle(i, j, size):
    """The same footprint test as ReactorCoreStructure.inCircle."""
    r = size / 2; x = i + 0.5 - r; z = j + 0.5 - r
    return x * x + z * z <= r * r


def pipe(*open_sides):
    return {side: str(side in open_sides).lower() for side in ('north', 'south', 'east', 'west', 'up', 'down')} | {'waterlogged': 'false'}


def ponder():
    """The minimum five-wide core in both scenes, using the full 5 + 2 + 7 + 2 + 12 m vertical profile."""
    size, vault_size, centre = 5, 9, 4
    s = Structure(vault_size, 33)
    ports = {(6, PLUG_TOP + 4, 2): ('biological_shield_coolant_outlet', {}), (7, PLUG_TOP + 4, 2): ('biological_shield_coolant_outlet', {}),
             (0, Y0 + 3, centre): ('biological_shield_drive', {'axis': 'x'}), (1, Y0 + 3, centre): ('biological_shield_drive', {'axis': 'x'})}
    for x in range(vault_size):
        for z in range(vault_size):
            vault = in_circle(x, z, vault_size)
            if not vault: continue
            core = 2 <= x <= 6 and 2 <= z <= 6 and in_circle(x - 2, z - 2, size)
            if not core:
                for y in range(LOWER, CAP_Y + 1):
                    if (x, y, z) == (8, PLUG_TOP + 4, 2): continue
                    name, properties = ports.get((x, y, z), ('biological_shield', {}))
                    s.block(x, y, z, NS + name, **properties)
                continue
            for y in range(LOWER, Y0): s.block(x, y, z, NS + 'reactor_feed_bundle')
            for y in range(Y0, TOP + 1): s.block(x, y, z, NS + 'reactor_fuel_channel')
            for y in range(TOP + 1, CAP_Y): s.block(x, y, z, NS + 'reactor_fuel_channel_tube')
            s.block(x, CAP_Y, z, NS + 'reactor_top_cap')
    s.block(centre, LOWER - 1, centre, 'create:fluid_pipe', **pipe('north', 'up'))
    s.block(centre, LOWER - 1, centre - 1, 'create:fluid_pipe', **pipe('north', 'south'))
    s.block(8, PLUG_TOP + 4, 2, 'create:fluid_pipe', **pipe('west'))
    s.block(centre - 1, CAP_Y + 1, centre, 'create:display_link', facing='up')
    save_story(s, 'rbmk_core')
    s = Structure(vault_size, 33)
    for x in range(vault_size):
        for z in range(vault_size):
            if not in_circle(x, z, vault_size): continue
            core = 2 <= x <= 6 and 2 <= z <= 6 and in_circle(x - 2, z - 2, size)
            if not core:
                for y in range(LOWER, CAP_Y + 1): s.block(x, y, z, NS + ('biological_shield_drive' if x in (0, 1) and z == centre and y == Y0 + 2 else 'biological_shield'),
                                                                    **({'axis': 'x'} if x in (0, 1) and z == centre and y == Y0 + 2 else {}))
                continue
            for y in range(LOWER, Y0): s.block(x, y, z, NS + 'reactor_feed_bundle')
            for y in range(Y0, TOP + 1): s.block(x, y, z, NS + 'reactor_fuel_channel')
            for y in range(TOP + 1, CAP_Y): s.block(x, y, z, NS + 'reactor_fuel_channel_tube')
            s.block(x, CAP_Y, z, NS + 'reactor_top_cap')
    s.block(centre, CAP_Y + 1, centre, 'create:copper_valve_handle', facing='up')
    save_story(s, 'rbmk_control_rods')


def save_story(structure, id):
    structure.save(id)
    source = OUT / f'{id}.nbt'; dest = A / 'ponder/nuclear' / f'{id}.nbt'
    dest.parent.mkdir(parents=True, exist_ok=True); dest.write_bytes(source.read_bytes()); source.unlink()


def test_template():
    """An empty 20 x 64 x 20 room for the 21 m assemblies, 22 m pools and their fixed service crane."""
    Structure(20, 64).save('test_rbmk')
    source = OUT / 'test_rbmk.nbt'; dest = D / 'structure/test_rbmk.nbt'
    dest.write_bytes(source.read_bytes()); source.unlink()


def cleanup_obsolete():
    """Remove pre-integration control parts and the abolished biological-shield coolant inlet."""
    paths = [
        A / 'blockstates/control_rod_absorber.json', A / 'blockstates/control_rod_displacer.json',
        A / 'blockstates/control_rod_winch.json', A / 'blockstates/reactor_control_rod_channel.json',
        A / 'models/item/control_rod_winch.json', A / 'models/item/reactor_control_rod_channel.json',
        A / 'models/block/rbmk/control_rod_winch.json', A / 'models/block/rbmk/control_rod_channel.json',
        A / 'models/block/rbmk/top_cap_rod.json',
        A / 'models/block/rbmk/reactor_core_map.json', A / 'models/block/rbmk/reactor_instrument_panel.json',
        A / 'models/block/reactor_console/instrument_glass.json',
        T / 'control_rod_winch.png', T / 'rod_channel_top.png', T / 'rod_channel_side.png', T / 'rod_channel_inner.png', T / 'top_cap_rod.png',
        T / 'console_desk.png', T / 'console_core_map.png', T / 'console_instruments.png',
        D / 'loot_table/blocks/control_rod_winch.json', D / 'loot_table/blocks/reactor_control_rod_channel.json',
        D / 'recipe/control_rod_winch.json', D / 'recipe/reactor_control_rod_channel.json',
        A / 'blockstates/biological_shield_coolant_inlet.json',
        A / 'models/item/biological_shield_coolant_inlet.json',
        A / 'models/block/rbmk/biological_shield_coolant_inlet.json',
        T / 'shield_inlet.png',
        D / 'loot_table/blocks/biological_shield_coolant_inlet.json',
        D / 'recipe/biological_shield_coolant_inlet.json',
    ]
    paths += [A / f'models/block/reactor_console/instrument_needle_{i}.json' for i in range(6)]
    for path in paths:
        if path.exists(): path.unlink()


if __name__ == '__main__':
    cleanup_obsolete(); textures(); models(); data(); translations(); ponder(); test_template()
    from generate_reactor_console import support
    support()
    from generate_fuel_pool_assets import generate as fuel_pool_assets
    fuel_pool_assets()
    print('Generated the RBMK reactor assets and the reactor control room')
