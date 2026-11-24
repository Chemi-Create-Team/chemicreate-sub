"""Uranium fuel line: item and ore icons, the gas-centrifuge and stirred-reactor modules of the reaction plant, recipes, worldgen, translations and Ponder storyboards."""
import json
import sys
from pathlib import Path


from PIL import Image
from minecraft_style import grain
from powder_assets import MANIFEST as POWDER_ITEMS, write_powder_item
from generate_reactor_assets import A, D, NS, element, slice_parts, octagon, write
from generate_petrochemical_ponder import Structure

REACTION_HEAT = json.loads((Path(__file__).resolve().parent / 'data/reaction_heat.json').read_text(encoding='utf-8'))

ITEMS = {
    'uraninite': ('Raw Uranium', 'ウラン原石', '粗铀矿'),
    'fluorite': ('Fluorite', '蛍石', '萤石'),
    'yellowcake': ('Yellowcake', 'イエローケーキ', '黄饼'),
    'uranium_trioxide': ('Uranium Trioxide', '三酸化ウラン', '三氧化铀'),
    'uranium_dioxide': ('Uranium Dioxide', '二酸化ウラン', '二氧化铀'),
    'uranium_tetrafluoride': ('Uranium Tetrafluoride', '四フッ化ウラン', '四氟化铀'),
    'enriched_uranium_dioxide': ('Enriched Uranium Dioxide', '濃縮二酸化ウラン', '浓缩二氧化铀'),
    'green_uranium_pellet': ('Green Uranium Pellet', '未焼結ウランペレット', '未烧结铀芯块'),
    'uranium_pellet': ('Uranium Pellet', 'ウランペレット', '铀芯块'),
}
FLUIDS = {
    'sulfur_dioxide': ('Sulfur Dioxide', '二酸化硫黄', '二氧化硫'),
    'sulfur_trioxide': ('Sulfur Trioxide', '三酸化硫黄', '三氧化硫'),
    'sulfuric_acid': ('Sulfuric Acid', '硫酸', '硫酸'),
    'hydrogen_fluoride': ('Hydrogen Fluoride', 'フッ化水素', '氟化氢'),
    'fluorine': ('Fluorine', 'フッ素', '氟气'),
    'uranyl_nitrate_solution': ('Uranyl Nitrate Solution', '硝酸ウラニル溶液', '硝酸铀酰溶液'),
    'uranium_hexafluoride': ('Uranium Hexafluoride', '六フッ化ウラン', '六氟化铀'),
    'enriched_uranium_hexafluoride': ('Enriched Uranium Hexafluoride', '濃縮六フッ化ウラン', '浓缩六氟化铀'),
    'depleted_uranium_hexafluoride': ('Depleted Uranium Hexafluoride', '劣化六フッ化ウラン', '贫化六氟化铀'),
}
BLOCKS = {}


def shade(color, v):
    return tuple(max(0, min(255, round(c * (1 + v / 100)))) for c in color) + (255,)


def icon(name, rows, light, base, edge=-38, speckle=None, rough=1.2):
    """Fills a row mask with a material lit from the upper left; the outline is darker and speckles mark grains or inclusions."""
    im = Image.new('RGBA', (16, 16), (0, 0, 0, 0)); p = im.load()
    inside = {(x, y) for y, (a, b) in rows.items() for x in range(a, b + 1)}
    for x, y in inside:
        v = light(x, y) + grain(name, x, y, rough)
        if any((x + dx, y + dy) not in inside for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))): v = edge if (x + 1, y) not in inside or (x, y + 1) not in inside else v + 10
        color = base
        if speckle:
            for test, tone in speckle:
                if test(x, y): color = tone
        p[x, y] = shade(color, v)
    return im


PILE = {5: (7, 8), 6: (6, 9), 7: (5, 10), 8: (4, 11), 9: (3, 12), 10: (2, 13), 11: (2, 13), 12: (1, 14), 13: (1, 14)}
LUMP = {2: (8, 10), 3: (5, 11), 4: (4, 12), 5: (3, 13), 6: (2, 13), 7: (2, 14), 8: (2, 13), 9: (3, 13), 10: (3, 12), 11: (4, 12), 12: (5, 10), 13: (6, 8)}


def pile(x, y): return 18 - (x - 4) * 3 - (y - 5) * 3 if y < 9 else -(x - 7) * 3 - (y - 9) * 4


def lump(x, y): return -14 if x + y == 12 or x - y == 5 else 26 if x + y < 12 else -24 if x - y > 5 or x + y > 19 else 0


def granules(seed): return lambda x, y: (x * 7 + y * 13 + seed) % 11 == 0


def textures():
    out = {}
    from mineral_reference_assets import textures as reference_textures
    reference_textures(('uranium', 'uraninite', 'fluorite'))
    for name, color, grains in [('yellowcake', (192, 154, 60), None)]:
        out[name] = icon(name, PILE, pile, color, speckle=grains and [(grains, tuple(min(255, c + 46) for c in color))], rough=2.6)
    pellet = {y: (4, 11) for y in range(4, 13)} | {2: (6, 9), 3: (5, 10), 13: (5, 10)}
    def cylinder(x, y):
        if y <= 4: return 34 - abs(x - 7) * 3
        return (-26, -8, 14, 22, 10, 0, -10, -30)[x - 4]
    out['green_uranium_pellet'] = icon('green_pellet', pellet, lambda x, y: cylinder(x, y) * .6, (128, 124, 114), edge=-30)
    out['uranium_pellet'] = icon('uranium_pellet', pellet, cylinder, (82, 82, 86), speckle=[(lambda x, y: y == 4 and 5 <= x <= 10, (120, 120, 124))])
    for name, im in out.items():
        if name in POWDER_ITEMS['powders']:
            write_powder_item(name)
        else:
            im.save(A / 'textures/item' / f'{name}.png')
            write(A / 'models/item' / f'{name}.json', {'parent': 'minecraft:item/generated', 'textures': {'layer0': NS + 'item/' + name}})
    for name in TINTED_POWDERS:
        write_powder_item(name)


# Their tints are applied by the client (ElectrolysisClient); the vanilla gunpowder picture is only referenced, never copied.
TINTED_POWDERS = ('uranium_trioxide', 'uranium_dioxide', 'uranium_tetrafluoride', 'enriched_uranium_dioxide')

# Create's casings: a dark 1px rim, a lit top-left and shaded bottom-right bevel inside it, a flat field with faint grain
# and riveted corners. Each entry is (outline, shade, field, light) and the pattern of the field.
CASINGS = {
    'reactor/steel': (((58, 60, 64), (101, 104, 108), (138, 142, 145), (178, 182, 183)), 'plate'),
    'reactor/frame': (((44, 46, 48), (76, 78, 79), (102, 104, 103), (133, 136, 133)), 'plate'),
    'reactor/brass': (((88, 56, 26), (146, 98, 38), (196, 146, 58), (236, 196, 104)), 'plate'),
    'reactor/copper': (((82, 42, 30), (136, 72, 50), (188, 108, 74), (226, 150, 108)), 'plate'),
    'reactor/mesh': (((44, 46, 48), (64, 66, 70), (138, 142, 145), (178, 182, 183)), 'grille'),
}


def casing(name, colours, pattern):
    outline, dark, field, light = colours
    im = Image.new('RGBA', (16, 16)); p = im.load()
    for y in range(16):
        for x in range(16):
            if x in (0, 15) or y in (0, 15): c = outline
            elif x == 1 or y == 1: c = light
            elif x == 14 or y == 14: c = dark
            else:
                c = shade(field, grain(name, x, y, .7))[:3]
                if pattern == 'grille': c = dark if x % 3 == 0 else field if x % 3 == 1 else light
                elif pattern == 'seam' and y in (7, 8): c = dark if y == 8 else light
            p[x, y] = (*c, 255)
    rivets = {'plate': ((3, 3), (11, 3), (3, 11), (11, 11)), 'seam': ((3, 3), (11, 3), (3, 11), (11, 11)), 'bolts': ((3, 3), (7, 2), (11, 3), (12, 7), (11, 11), (7, 12), (3, 11), (2, 7)), 'grille': ()}[pattern]
    for x, y in rivets:
        p[x, y] = (*light, 255); p[x + 1, y] = (*field, 255); p[x, y + 1] = (*field, 255); p[x + 1, y + 1] = (*dark, 255)
    dest = A / 'textures/block' / f'{name}.png'; dest.parent.mkdir(parents=True, exist_ok=True); im.save(dest)


# The four reaction-plant machines share one silver set taken from the gas-liquid separator: its banded side for the
# shell, darkened for the frame, and its rim, groove and field colours for the bands, trim and grilles.
SEPARATOR_SILVER = ((41, 41, 39), (86, 88, 88), (161, 161, 161), (224, 224, 224))
SILVER = {'steel': 'silver/shell', 'steel_top': 'silver/top', 'frame': 'silver/frame', 'brass': 'silver/band', 'copper': 'silver/trim', 'mesh': 'silver/grille'}


def silver_texture(t): return NS + 'block/reactor/' + SILVER.get(t, t)


SILVER_PARTICLE = NS + 'block/reactor/silver/shell'


def block_textures():
    for name, (colours, pattern) in CASINGS.items(): casing(name, colours, pattern)
    tank = A / 'textures/block/tank'
    for name, source, factor in (('shell', 'ammonia_separator', 1), ('top', 'ammonia_separator_top', 1), ('frame', 'ammonia_separator', .72)):
        im = Image.open(tank / f'{source}.png').convert('RGBA'); p = im.load()
        for y in range(16):
            for x in range(16):
                r, g, b, a = p[x, y]; p[x, y] = (round(r * factor), round(g * factor), round(b * factor), a)
        dest = A / 'textures/block/reactor/silver' / f'{name}.png'; dest.parent.mkdir(parents=True, exist_ok=True); im.save(dest)
    outline, groove, field, rim = SEPARATOR_SILVER
    # Bands and trims are only two or three pixels tall, so every pair of rows is a lit rim over a dark groove.
    for name, base in (('band', field), ('trim', (190, 190, 190))):
        im = Image.new('RGBA', (16, 16)); p = im.load()
        for y in range(16):
            for x in range(16):
                if x % 8 == 0: c = outline
                elif y % 2: c = groove
                elif x % 8 in (1, 7): c = rim
                else: c = shade(base, grain(name, x, y, .6) + 18)[:3]
                p[x, y] = (*c, 255)
        im.save(A / 'textures/block/reactor/silver' / f'{name}.png')
    casing('reactor/silver/grille', SEPARATOR_SILVER, 'grille')
    im = Image.new('RGBA', (16, 16), (0, 0, 0, 0)); p = im.load()
    for i in range(16):
        for x, y in ((i, 15 - i), (i + 4, 15 - i), (i - 6, 15 - i)):
            if 0 <= x < 16 and (x + y) % 3: p[x, y] = (196, 214, 220, 255)
    im.save(A / 'textures/block/reactor/sight_glass.png')


def full_cube(texture, cull=True):
    return {'from': [0, 0, 0], 'to': [16, 16, 16], 'faces': {f: {'texture': texture, **({'cullface': f} if cull else {})} for f in ('down', 'up', 'north', 'south', 'west', 'east')}}


def loot(id):
    write(D / 'loot_table/blocks' / f'{id}.json', {'type': 'minecraft:block', 'pools': [{'rolls': 1, 'entries': [{'type': 'minecraft:item', 'name': NS + id}], 'conditions': [{'condition': 'minecraft:survives_explosion'}]}]})


def cut(parts, hole):
    """Removes a box from every straight part, splitting each into the pieces that lie outside it."""
    out = []
    for part in parts:
        if len(part) == 3: out.append(part); continue
        b, t = part
        if any(b[i] >= hole[i + 3] or b[i + 3] <= hole[i] for i in range(3)): out.append((b, t)); continue
        rest = list(b)
        for i in range(3):
            if rest[i] < hole[i]: piece = list(rest); piece[i + 3] = hole[i]; out.append((piece, t)); rest[i] = hole[i]
            if rest[i + 3] > hole[i + 3]: piece = list(rest); piece[i] = hole[i + 3]; out.append((piece, t)); rest[i + 3] = hole[i + 3]
    return out


SQRT2 = 2 ** .5


def octagon_flat(y, Y, tex, inset=2, hollow=True, wall=3):
    """An octagon with smooth 45-degree faces: the straight sides are boxes and each chamfer is one box turned about its centre.
    A solid octagon's chamfers stay a hair inside its top and bottom, so their faces never lie on those of the straight boxes."""
    a, b = inset, 48 - inset; c = min(10, (b - a) // 2 - 1)
    if hollow:
        parts = [([a + c, y, a, b - c, Y, a + wall], tex), ([a + c, y, b - wall, b - c, Y, b], tex), ([a, y, a + c, a + wall, Y, b - c], tex), ([b - wall, y, a + c, b, Y, b - c], tex)]
        depth, e = wall, 0
    else:
        parts = [([a + c, y, a, b - c, Y, b], tex), ([a, y, a + c, a + c, Y, b - c], tex), ([b - c, y, a + c, b, Y, b - c], tex)]
        depth, e = c / SQRT2, .01
    length = c * SQRT2 + 1
    for (x0, z0), (x1, z1), (nx, nz) in (((a, a + c), (a + c, a), (1, 1)), ((b - c, a), (b, a + c), (-1, 1)), ((a, b - c), (a + c, b), (1, -1)), ((b - c, b), (b, b - c), (-1, -1))):
        cx = (x0 + x1) / 2 + nx * depth / 2 / SQRT2; cz = (z0 + z1) / 2 + nz * depth / 2 / SQRT2
        parts.append(([cx - length / 2, y + e, cz - depth / 2, cx + length / 2, Y - e, cz + depth / 2], tex, 45 if (x1 - x0) * (z1 - z0) < 0 else -45))
    return parts


def cell_parts(parts, cell):
    """One block of a 3x3 layer: straight boxes are cut at the block edges, a turned box stays whole in the block that holds its centre."""
    out = slice_parts([part for part in parts if len(part) == 2], cell)
    cx, cz = cell % 3, cell // 3
    for b, t, angle in (part for part in parts if len(part) == 3):
        if int((b[0] + b[3]) / 2 // 16) == cx and int((b[2] + b[5]) / 2 // 16) == cz:
            out.append(([b[0] - cx * 16, b[1], b[2] - cz * 16, b[3] - cx * 16, b[4], b[5] - cz * 16], t, angle))
    return out


def turned(b, t, angle):
    return {'from': b[:3], 'to': b[3:], 'rotation': {'origin': [(b[0] + b[3]) / 2, (b[1] + b[4]) / 2, (b[2] + b[5]) / 2], 'axis': 'y', 'angle': angle, 'rescale': False},
            'faces': {f: {'uv': [0, 0, 16, 16], 'texture': '#' + t} for f in ('north', 'south', 'west', 'east', 'up', 'down')}}


def panel(b, t):
    """A box whose faces of six pixels or more each show the whole texture, the way the gas-liquid separator shows one panel
    per face; smaller faces keep the slice under them. A steel face looking up or down shows the separator's top."""
    e = element(b, t)
    x, y, z, X, Y, Z = b
    sizes = {'north': (X - x, Y - y), 'south': (X - x, Y - y), 'west': (Z - z, Y - y), 'east': (Z - z, Y - y), 'up': (X - x, Z - z), 'down': (X - x, Z - z)}
    for face, (w, h) in sizes.items():
        if w >= 6 and h >= 6: e['faces'][face]['uv'] = [0, 0, 16, 16]
        if face in ('up', 'down') and t == 'steel': e['faces'][face]['texture'] = '#steel_top'
    return e


def cell_model(name, parts, texture, particle):
    data = {'parent': 'minecraft:block/block'}
    if any(part[1] == 'sight_glass' for part in parts): data['render_type'] = 'minecraft:cutout'
    keys = list(dict.fromkeys(part[1] for part in parts))
    if 'steel' in keys: keys.append('steel_top')
    data |= {'textures': {**{key: texture(key) for key in keys}, 'particle': particle},
             'elements': [panel(part[0], part[1]) if len(part) == 2 else turned(*part) for part in parts]}
    write(A / 'models/block/reactor' / f'{name}.json', data)


def plant_shell(kind, role, flat=False):
    """The jacket and head of the catalytic vessel (also the stirred reactor's) or of the flash column. Flat gives the
    models their smooth chamfers; otherwise the stepped outline is what the collision boxes use."""
    oc = octagon_flat if flat else octagon
    inset = 7 if kind == 'separator' else 2
    if role == 'head':
        return oc(0, 3, 'steel', inset, False) + oc(3, 7, 'steel', inset + 4, False) + oc(7, 10, 'steel', inset + 8, False) + [
            ([20, 10, 20, 28, 13, 28], 'brass'), ([18, 12, 22, 30, 14, 26], 'steel'), ([22, 12, 18, 26, 14, 30], 'steel')]
    # One full-height shell per section, so each face shows one separator panel with its own lit rims and shaded grooves.
    parts = oc(0, 16, 'steel', inset, True)
    if kind == 'separator':
        return parts + [([16, 3, 9, 32, 5, 12], 'brass'), ([16, 11, 9, 32, 13, 12], 'brass'), ([16, 3, 9, 18, 13, 11], 'steel'), ([30, 3, 9, 32, 13, 11], 'steel')]
    return parts


ROTORS = ((14, 14), (34, 14), (14, 34), (34, 34))
WINDOWS = ([20, 3, 0, 28, 13, 7], [20, 3, 41, 28, 13, 48], [0, 3, 20, 7, 13, 28], [41, 3, 20, 48, 13, 28])
PANES = ([20, 3, 2, 28, 13, 3], [20, 3, 45, 28, 13, 46], [2, 3, 20, 3, 13, 28], [45, 3, 20, 46, 13, 28])
BOLTS = ((23, 1), (23, 45), (1, 23), (45, 23), (9, 9), (37, 9), (9, 37), (37, 37))


def assembly(role, flat=False):
    """A stainless drum closed by a bolted dome lid; glazed sight windows show the rotors spinning inside."""
    oc = octagon_flat if flat else octagon
    if role == 'core':
        return [([22, 0, 22, 26, 16, 26], 'steel'), ([20, 0, 20, 28, 1, 28], 'brass')]
    if role == 'head':
        parts = oc(0, 2, 'frame', 0, False) + oc(2, 4, 'steel', 2, False) + oc(4, 6, 'steel', 8, False) + oc(6, 8, 'steel', 13, False)
        parts += [([17, 8, 17, 31, 16, 31], 'frame')] + [([x, 2, z, x + 2, 3, z + 2], 'brass') for x, z in BOLTS]
        return parts
    parts = oc(0, 16, 'steel', 1, True) + oc(0, 1, 'frame', 0, True)
    for window in WINDOWS: parts = cut(parts, window)
    return parts + [(pane, 'sight_glass') for pane in PANES]


# The stirred reactor shares the catalytic vessel's frame and jacket shapes.








ORES = {'uranium_ore': ('stone', 'minecraft:cobblestone'), 'deepslate_uranium_ore': ('deepslate', 'minecraft:cobbled_deepslate')}


def tag(path, values):
    path = D.parent / path
    data = json.loads(path.read_text()) if path.exists() else {'replace': False, 'values': []}
    data['values'] = list(dict.fromkeys(data['values'] + values)); write(path, data)


def deep_layer(feature, count, low, high):
    """Continues a deep ore below Y -64 in a world made deeper by another mod, at the tries per 16 blocks of its vanilla range; empty in a vanilla-depth world."""
    write(D / 'worldgen/placed_feature' / f'{feature}_deep.json', {'feature': NS + feature, 'placement': [
        {'type': NS + 'deep_layer', 'min_inclusive': {'above_bottom': 8}, 'max_inclusive': {'absolute': -64}, 'per_16_blocks': round(count * 16 / (high - low + 1), 2)},
        {'type': 'minecraft:in_square'}, {'type': 'minecraft:biome'}]})
    return [NS + feature, NS + f'{feature}_deep']


def ores():
    for id, (stone, rubble) in ORES.items():
        base = 'minecraft:block/' + stone
        write(A / 'models/block' / f'{id}.json', {'parent': 'minecraft:block/block', 'render_type': 'minecraft:cutout_mipped',
                                                 'textures': {'particle': base, 'base': base, 'overlay': NS + 'block/' + ('deepslate_' if id.startswith('deepslate_') else '') + 'uranium_ore_overlay'},
                                                 'elements': [full_cube('#base'), full_cube('#overlay')]})
        write(A / 'blockstates' / f'{id}.json', {'variants': {'': {'model': NS + 'block/' + id}}})
        write(A / 'models/item' / f'{id}.json', {'parent': NS + 'block/' + id})
        silk = {'condition': 'minecraft:match_tool', 'predicate': {'predicates': {'minecraft:enchantments': [{'enchantments': 'minecraft:silk_touch', 'levels': {'min': 1}}]}}}
        write(D / 'loot_table/blocks' / f'{id}.json', {'type': 'minecraft:block', 'pools': [{'rolls': 1, 'bonus_rolls': 0, 'entries': [{'type': 'minecraft:alternatives', 'children': [
            {'type': 'minecraft:item', 'name': NS + id, 'conditions': [silk]},
            {'type': 'minecraft:item', 'name': NS + 'uraninite', 'functions': [{'function': 'minecraft:apply_bonus', 'enchantment': 'minecraft:fortune', 'formula': 'minecraft:ore_drops'}, {'function': 'minecraft:explosion_decay'}]}]}]}]})
        write(D / 'recipe/crushing' / f'{id}.json', {'type': 'create:crushing', 'ingredients': [{'item': NS + id}], 'processing_time': 250,
                                                     'results': [item(NS + 'uraninite'), item(NS + 'uraninite', chance=0.75), item('create:experience_nugget', chance=0.75), item(rubble, chance=0.12)]})
    ids = [NS + id for id in ORES]
    machines = []
    for kind in ('block', 'item'):
        tag(f'c/tags/{kind}/ores/uranium.json', ids); tag(f'c/tags/{kind}/ores.json', ['#c:ores/uranium'])
        tag(f'c/tags/{kind}/ores_in_ground/stone.json', [NS + 'uranium_ore']); tag(f'c/tags/{kind}/ores_in_ground/deepslate.json', [NS + 'deepslate_uranium_ore'])
    tag('minecraft/tags/block/mineable/pickaxe.json', ids + machines)
    tag('minecraft/tags/block/needs_iron_tool.json', ids)
    tag('create/tags/block/non_movable.json', machines)
    write(D / 'worldgen/configured_feature/uranium_ore.json', {'type': 'minecraft:ore', 'config': {'size': 6, 'discard_chance_on_air_exposure': 0.0, 'targets': [
        {'target': {'predicate_type': 'minecraft:tag_match', 'tag': 'minecraft:stone_ore_replaceables'}, 'state': {'Name': NS + 'uranium_ore'}},
        {'target': {'predicate_type': 'minecraft:tag_match', 'tag': 'minecraft:deepslate_ore_replaceables'}, 'state': {'Name': NS + 'deepslate_uranium_ore'}}]}})
    write(D / 'worldgen/placed_feature/uranium_ore.json', {'feature': NS + 'uranium_ore', 'placement': [{'type': 'minecraft:count', 'count': 4}, {'type': 'minecraft:in_square'},
        {'type': 'minecraft:height_range', 'height': {'type': 'minecraft:trapezoid', 'min_inclusive': {'absolute': -48}, 'max_inclusive': {'absolute': 32}}}, {'type': 'minecraft:biome'}]})
    write(D / 'neoforge/biome_modifier/uranium_ore.json', {'type': 'neoforge:add_features', 'biomes': '#minecraft:is_overworld', 'features': deep_layer('uranium_ore', 4, -48, 32), 'step': 'underground_ores'})


def fluid(id, amount): return {'type': 'neoforge:single', 'amount': amount, 'fluid': id}


def fluid_out(id, amount): return {'amount': amount, 'id': id}


def item(id, count=1, chance=None):
    out = {'id': id}
    if count > 1: out['count'] = count
    if chance is not None: out['chance'] = chance
    return out


def fluorite_recipes():
    results = [item('minecraft:bone_meal', chance=0.75), item(NS + 'fluorite', chance=0.5)]
    for machine, path in (
            ('crushing', D / 'recipe/crushing/calcite_to_fluorite.json'),
            ('milling', D.parent / 'create/recipe/milling/calcite.json')):
        write(path, {'type': 'create:' + machine, 'ingredients': [{'item': 'minecraft:calcite'}],
                     'processing_time': 250, 'results': results})


def recipes():
    """Stirred and tray reactions and the remaining Create-machine recipes."""
    r = D / 'recipe'; U = NS
    fluorite_recipes()
    reactions = {
        'sulfur_dioxide': ([{'tag': 'c:dusts/sulfur'}, fluid(U + 'oxygen_gas', 500)], [fluid_out(U + 'sulfur_dioxide', 500)], 'heated'),
        'hydrogen_fluoride': ([{'item': U + 'fluorite'}, fluid(U + 'sulfuric_acid', 250)], [fluid_out(U + 'hydrogen_fluoride', 1000)], 'heated'),
        'uranium_trioxide': ([fluid(U + 'uranyl_nitrate_solution', 250)], [item(U + 'uranium_trioxide'), fluid_out(U + 'nitrogen_dioxide', 100)], 'heated'),
        'uranium_dioxide': ([{'tag': U + 'uranium_reduction_feed'}, fluid(U + 'hydrogen_gas', 250)], [item(U + 'uranium_dioxide')], 'heated'),
        'uranium_tetrafluoride': ([{'item': U + 'uranium_dioxide'}, fluid(U + 'hydrogen_fluoride', 500)], [item(U + 'uranium_tetrafluoride')], 'heated'),
        'uranium_hexafluoride': ([{'item': U + 'uranium_tetrafluoride'}, fluid(U + 'fluorine', 250)], [fluid_out(U + 'uranium_hexafluoride', 250)], 'superheated'),
        'enriched_uranium_dioxide': ([fluid(U + 'enriched_uranium_hexafluoride', 250), fluid('minecraft:water', 250), fluid(U + 'hydrogen_gas', 250)], [item(U + 'enriched_uranium_dioxide'), fluid_out(U + 'hydrogen_fluoride', 750)], None),
        'uranium_pellet': ([{'item': U + 'green_uranium_pellet'}], [item(U + 'uranium_pellet')], 'heated'),
        'spent_fuel_reprocessing': ([{'item': U + 'chopped_spent_fuel'}, fluid(U + 'nitric_acid', 4000)],
                                    [fluid_out(U + 'dissolved_spent_fuel', 4000)], 'heated'),
        'spent_control_rod_reprocessing': ([{'item': U + 'spent_control_rod'}, fluid(U + 'nitric_acid', 500)],
                                           [item(U + 'reactor_graphite')], 'heated'),
    }
    for name, (ingredients, results, heat) in reactions.items():
        recipe = {'type': U + 'pressure_reaction'}
        if name in ('uranium_dioxide', 'uranium_hexafluoride', 'uranium_pellet'): recipe['module'] = 'absorber'
        if name == 'spent_fuel_reprocessing': recipe['processing_time'] = 400
        if heat: recipe['heat_requirement'] = heat
        recipe |= {'ingredients': ingredients, 'results': results}
        if name in REACTION_HEAT: recipe['reaction_heat'] = REACTION_HEAT[name]
        write(r / 'pressure_reaction' / f'{name}.json', recipe)
    write(r / 'blasting/roast_raw_uranium.json', {'type': 'minecraft:blasting', 'ingredient': {'tag': U + 'uranium_roasting_feed'}, 'result': item(U + 'yellowcake'), 'experience': 0.1, 'cookingtime': 100})
    write(D / 'tags/item/uranium_roasting_feed.json', {'replace': False, 'values': [U + 'uraninite', 'create:crushed_raw_uranium']})
    write(D / 'tags/item/uranium_reduction_feed.json', {'replace': False, 'values': [U + 'yellowcake', U + 'uranium_trioxide']})
    write(r / 'smelting/uranium_pellet.json', {'type': 'minecraft:smelting', 'ingredient': {'item': U + 'green_uranium_pellet'}, 'result': item(U + 'uranium_pellet'), 'experience': 0.1, 'cookingtime': 200})
    write(r / 'pressing/green_uranium_pellet.json', {'type': 'create:pressing', 'ingredients': [{'item': U + 'enriched_uranium_dioxide'}], 'results': [item(U + 'green_uranium_pellet', 2)]})


def plant(s, x, y, z, kind, sections, burners=()):
    """A reaction-plant vessel starting at y, with its motor and any Blaze Burners in the plinth below."""
    s.reactor(x, y, z, kind, sections, burners)


def receiver(s, x, y, z):
    s.block(x, y, z, NS + 'pressure_tank', bottom='true', top='false', shape='plain'); s.block(x, y + 1, z, NS + 'pressure_tank', bottom='false', top='true', shape='plain')


def plumbing(s, x, y, z):
    """A pump into the west face of the vessel's bottom layer from a gas receiver, and a pump out of its east face into a tank."""
    for side in (-1, 1):
        s.block(x + 2 * side, y - 1, z, 'create:andesite_casing'); s.pump(x + 2 * side, y, z, 'east')
    receiver(s, x - 3, y - 1, z); s.block(x + 3, y - 1, z, 'create:fluid_tank', bottom='true', top='false', shape='plain'); s.block(x + 3, y, z, 'create:fluid_tank', bottom='false', top='true', shape='plain')


RING = {'reveal': [3, 1, 3, 5, 2, 5]}
STEPS = {
    'reactor_plant_stirred': ('stirred', 2, ((1, 0), (-1, 0)), ('Building a stirred reactor', '撹拌反応槽を組み立てる'), [
        ([4, 2, 4], 'Build a 3x3 lidded vessel and put an impeller into every layer. The vessel itself becomes the stirred reactor; Blaze Burners underneath heat reactions that need it. The square shell stays the same for every internal.',
         '3×3のふた付き槽を組み、各段に撹拌翼を入れると、槽自身が撹拌反応槽になります。加熱が必要な反応は槽の下のブレイズバーナーで加熱します。内部部品を入れても四角い外形を保ちます。', RING),
        ([4, 3, 4], 'Stack the vessel one to four layers high and put an impeller into every layer; every stage runs one batch at a time.', '槽を1～4段に積み、各段に撹拌翼を入れます。1段ごとに1バッチを同時に処理します。', {'reveal': [3, 3, 3, 5, 4, 5]}),
        ([4, 3, 4], 'Keep the hatch shut.', 'ハッチは閉じておきます。', {'reveal': [3, 3, 3, 5, 3, 5]}),
        ([4, 1, 4], 'Supply at least 32 RPM at the single bottom centre. Side faces have no shaft connection.', '底面中央の1か所へ32 RPM以上を入力します。側面には軸をつなげません。', {'reveal': [0, 1, 0, 8, 6, 8], 'speed': 64}),
        ([2, 2, 4], 'Use pipes for fluids and funnels for items. Check JEI for recipes.',
         '流体はパイプ、固体はファンネルで出し入れします。レシピはJEIで確認できます。', {}),
    ]),

}
COMPONENTS = {'stirred': ['reactor_impeller'], 'centrifuge': ['gas_centrifuge']}


def ponder():
    for id, (kind, sections, burners, _, _) in STEPS.items():
        s = Structure(9, sections + 5)
        plant(s, 4, 2, 4, kind, sections, burners); plumbing(s, 4, 2, 4)
        save(s, id)
    catalog = A / 'ponder/tutorials.json'; tutorials = json.loads(catalog.read_text())
    known = {t['id'] for t in tutorials}
    for id, (kind, sections, _, _, steps) in STEPS.items():
        if id not in known: tutorials.append({'id': id, 'size': 9})
    for t in tutorials:
        if t['id'] in STEPS:
            kind, sections, _, _, steps = STEPS[t['id']]
            t['height'] = sections + 5
            t['components'] = COMPONENTS[kind] + ['lidded_vessel']
            t['steps'] = [{'target': target, 'key': f'step_{i}', **extra} for i, (target, _, _, extra) in enumerate(steps)]
    write(catalog, tutorials)


def save(structure, id):
    structure.save(id)
    source = A / 'ponder/chemistry' / f'{id}.nbt'; dest = A / 'ponder/plant' / f'{id}.nbt'
    # Keep the historical RFC 1952 OS byte so regeneration is byte-for-byte stable across Python versions.
    payload = bytearray(source.read_bytes())
    if len(payload) >= 10 and payload[:2] == b'\x1f\x8b': payload[9] = 10
    dest.write_bytes(payload); source.unlink()


ITEMS |= {'uranium_ore': ('Uranium Ore', 'ウラン鉱石', '铀矿石'), 'deepslate_uranium_ore': ('Deepslate Uranium Ore', '深層ウラン鉱石', '深层铀矿石')}
BLOCKS |= {'uranium_ore': ITEMS['uranium_ore'], 'deepslate_uranium_ore': ITEMS['deepslate_uranium_ore']}
JEI = {
    'stirred_reactor': ('Stirred Reactor', '撹拌反応槽'), 'reaction_vessel': ('Catalytic Pressure Vessel', '触媒反応槽'),
    'gas_centrifuge': ('Gas Centrifuge', 'ガス遠心分離機'), 'basin_electrolysis': ('Basin Electrolyzer', 'ベイスン電解槽'),
    'haber_bosch': ('Haber-Bosch Tower', 'ハーバー・ボッシュ塔'), 'nitric_absorption': ('Nitric Acid Absorption', '硝酸吸収（ふた付き槽＋吸収棚段）'),
    'ammonia_separator': ('Ammonia Separator', 'アンモニア分離器'),
    'air_separation': ('Air Separation Column', '空気分離塔'), 'process_heating': ('Process Heating', '工程加熱'), 'process_cooling': ('Process Cooling', '工程冷却'),
    'note.reactor': ("%s ticks in the reaction tower, one batch per layer with a basket. Each batch wears that layer's catalyst; it lasts 4,000 batches.", '反応塔で%s tick、バスケットのある段ごとに1バッチ。触媒は1バッチごとにその段で劣化し、4,000バッチで使えなくなる'),
    'note.stirred_reactor': ('%s ticks at 32 RPM, one batch per stage. Burner heat: %s', '32 RPMで%s tick、1段につき1バッチ。バーナーの熱：%s'),
    'note.electrolysis': ('One plate for %s ticks, %s kJ. Water is used first.', '極板1枚・%s tick・%s kJ。水を優先して使う'),
    'note.electrolysis_fluoride': ('One plate for %s ticks, %s kJ, when the basins hold neither water nor brine.', '極板1枚・%s tick・%s kJ。水も塩水も無いとき'),
    'note.nitric_absorption': ('%s ticks; one parallel batch per layer of trays, no rotation. Recycle the nitric oxide.', '%s tick、棚段1段につき1バッチを並列処理。回転は不要。一酸化窒素は循環させる'),
    'note.ammonia_separator': ('A typical split; the ammonia depends on the jacket cooling.', '目安の分配。アンモニアの量はジャケットの冷却で変わる'),
    'note.air_separation': ('100 batches; one batch takes %s ticks. Liquid oxygen keeps the mass of the oxygen: 1 mB for %s mB of gas; the rest waits in the column.',
                            '100バッチ分。1バッチは%s tick。液化酸素は酸素の質量どおり、気体%s mBにつき1 mB。端数は塔に残る'),
    'note.heating': ('Industrial heater or preheater, up to its set temperature.', '工業用加熱器・予熱器で設定温度まで加熱'),
    'note.cooling': ('Industrial cooler; its cooling water leaves as warm water.', '工業用冷却器。冷却水は温排水になる'),
    'heat.none': ('none', '不要'), 'heat.heated': ('heated', '加熱'), 'heat.superheated': ('superheated', '超加熱'),
}
REACTOR = {'item_input': ('Feed: %s × %s', '原料：%s × %s'), 'item_output': ('Product: %s × %s', '生成物：%s × %s'),
           'ports': ('Pipes and funnels connect to the formed vessel; rotation enters from a side or the bottom centre', '配管とファンネルは形成した槽に接続し、回転は側面か底面中央から入力します')}

# The absorber's recipe page is shown in Chinese as well; the other runtime and scene texts here are English and Japanese only.
ZH_ABSORPTION = {
    'jei.create_refinery_n_reactors.nitric_absorption': '硝酸吸收（带盖槽＋吸收塔板）',
    'jei.create_refinery_n_reactors.note.nitric_absorption': '%s tick；每层塔板并行处理1批，无需旋转。一氧化氮循环使用',
}


def translations():
    for language, index in (('en_us', 0), ('ja_jp', 1), ('zh_cn', 2)):
        path = A / 'lang' / f'{language}.json'; raw = path.read_bytes().decode('utf8'); nl = '\r\n' if '\r\n' in raw else '\n'; data = json.loads(raw)
        for id, names in ITEMS.items():
            if id not in BLOCKS: data['item.create_refinery_n_reactors.' + id] = names[index]
        for id, names in FLUIDS.items(): data['fluid.create_refinery_n_reactors.' + id] = names[index]
        for id, names in BLOCKS.items(): data['block.create_refinery_n_reactors.' + id] = names[index]
        if index < 2:
            data['reactor.create_refinery_n_reactors.status.core'] = ('Missing functional internals in one or more layers', '内部部品が不足している段があります')[index]
            data['process.create_refinery_n_reactors.recipe.oxidize_sulfur_dioxide'] = ('Catalytic sulfur dioxide oxidation', '触媒による二酸化硫黄の酸化')[index]
            data['process.create_refinery_n_reactors.recipe.enrich_uranium'] = ('Uranium enrichment', 'ウランの濃縮')[index]
            for key, values in REACTOR.items(): data['reactor.create_refinery_n_reactors.' + key] = values[index]
            for key, values in JEI.items(): data['jei.create_refinery_n_reactors.' + key] = values[index]
            for id, (_, _, _, title, steps) in STEPS.items():
                shared = f'create_refinery_n_reactors.ponder.shared.guide.{id}.'
                expected = {shared + f'step_{i}' for i in range(len(steps))}
                for key in [k for k in data if k.startswith(shared + 'step_') and k not in expected]: del data[key]
                if title: data[shared + 'title'] = title[index]; data[f'create_refinery_n_reactors.ponder.plant_{id}.header'] = title[index]
                for i, step in enumerate(steps): data[shared + f'step_{i}'] = step[1 + index]
        if index == 2:
            data.update(ZH_ABSORPTION)
        path.write_bytes((json.dumps(data, ensure_ascii=False, indent=2).replace('\n', nl) + nl).encode('utf8'))


if __name__ == '__main__':
    textures(); block_textures(); ores(); recipes(); ponder(); translations()
    print('Generated the uranium fuel line, uranium ores, the gas centrifuge and stirred reactor modules, recipes, translations and Ponder storyboards')
