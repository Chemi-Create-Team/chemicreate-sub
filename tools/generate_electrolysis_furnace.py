#!/usr/bin/env python3
"""Molten-salt electrolysis furnace: bath textures, models, blockstates, loot, recipes, tags and translations. Run from the
repository root."""
import json, sys
from PIL import Image
sys.path.insert(0, 'tools')
from minecraft_style import grain, metal_pixel
from generate_reactor_assets import A, D, NS, write
from generate_uranium_assets import shade, tag, plant
from generate_petrochemical_ponder import Structure, OUT

T = NS + 'block/furnace/'


def texture(name, paint):
    im = Image.new('RGBA', (16, 16)); p = im.load()
    for y in range(16):
        for x in range(16): p[x, y] = paint(x, y)
    (A / 'textures/block/furnace').mkdir(parents=True, exist_ok=True); im.save(A / 'textures/block/furnace' / f'{name}.png')


def textures():
    """Brushed stainless cladding, cast-iron and blackened-steel framing, white service pipes, a firebrick lining, sight glass, the bath, graphite electrodes, and
    the aluminium and porcelain of the busbar: none of it shared with the other machines."""
    texture('panel', lambda x, y: metal_pixel('stainless', (204, 210, 214), x, y))
    texture('frame', lambda x, y: metal_pixel('cast_iron', (112, 120, 126), x, y))
    texture('base', lambda x, y: metal_pixel('cast_iron', (66, 70, 76), x, y))
    texture('pipe', lambda x, y: shade((232, 232, 228), (22 if x in (1, 2) else 8 if x < 6 else -6 if x < 12 else -22) + (-30 if y in (0, 15) else 0) + grain('furnace_pipe', x, y // 4, .8)))
    texture('aluminium', lambda x, y: metal_pixel('stainless', (184, 190, 196), x, y) if y % 4 else shade((120, 126, 132), 0))
    texture('lining', lambda x, y: shade((150, 138, 120) if y % 4 == 3 or (x + (4 if (y // 4) % 2 else 0)) % 8 == 7 else (222, 212, 188), grain('furnace_lining', x, y, 2.4)))
    for name, glow in (('window_off', False), ('window_on', True)):
        def window(x, y, glow=glow):
            # A band of glass that runs on across the side; only its top and bottom carry the sill.
            if y in (0, 15): return shade((58, 62, 70), -8 if x % 4 == 0 else 0)
            if y in (1, 14): return shade((150, 156, 162), 6 if y == 1 else -10)
            if x % 8 == 0: return shade((96, 100, 108), 0)
            if glow:
                d = abs(y - 9.5)
                return shade((255, 214, 112) if d < 2 else (246, 150, 48) if d < 4 else (196, 76, 26), grain('furnace_glow', x, y, 3))
            return shade((96, 92, 92) if (x + y) % 7 == 0 else (52, 48, 50), grain('furnace_glass', x, y, 1.5))
        texture(name, window)
    # The bath lies under a crust of alumina: grey and matt when cold; once molten a brushed, silvery skin with only a faint warm
    # glint where it parts.
    texture('bath_crust', lambda x, y: shade((132, 134, 136) if (x * 3 + y * 5) % 13 == 0 or (x * 7 - y * 2) % 17 == 0 else (168, 170, 172), grain('bath_crust', x, y, 2.4)))
    texture('bath_molten', lambda x, y: shade((226, 176, 122), 0) if (x, y) in ((3, 4), (4, 4), (11, 9), (12, 9), (6, 13), (13, 2))
        else shade((184, 190, 196), (0, 10, 22, 30, 22, 10, 0, -8, -14, -10, -4, 4, 14, 8, 0, -10)[(x + 2 * y) % 16] + grain('bath_molten', x // 2, y // 2, .8)))
    texture('electrode', lambda x, y: shade((44, 46, 50), (20 if x in (5, 6) else -14 if x in (0, 15) else 0) + (-18 if y % 8 == 0 else 0) + grain('furnace_electrode', x // 2, y, 2)))
    texture('clamp', lambda x, y: shade((172, 178, 184), (18 if y == 0 else -24 if y == 15 else 0) + (-40 if (x, y % 8) in ((3, 4), (12, 4)) else 0) + grain('furnace_clamp', x, y, 1.2)))
    texture('insulator', lambda x, y: shade((236, 234, 226), (-34 if y % 4 == 3 else 14 if y % 4 == 0 else 0) + (-10 if x >= 12 else 0)))


def span(a, b):
    """UV range of a face edge, kept inside one texture for parts reaching into the next block."""
    shift = (a // 16) * 16; a, b = a - shift, b - shift
    return (a, b) if b <= 16 else (max(0, 16 - (b - a)), 16)


def box(a, b, texture, cull=(), faces=None):
    faces = faces or {}
    (x1, x2), (y1, y2), (z1, z2) = span(a[0], b[0]), span(a[1], b[1]), span(a[2], b[2])
    uv = {'down': [x1, 16 - z2, x2, 16 - z1], 'up': [x1, z1, x2, z2], 'north': [16 - x2, 16 - y2, 16 - x1, 16 - y1], 'south': [x1, 16 - y2, x2, 16 - y1],
          'west': [z1, 16 - y2, z2, 16 - y1], 'east': [16 - z2, 16 - y2, 16 - z1, 16 - y1]}
    return {'from': a, 'to': b, 'faces': {f: {'uv': uv[f], 'texture': faces.get(f, texture), **({'cullface': f} if f in cull else {})} for f in ('down', 'up', 'north', 'south', 'west', 'east')}}


SHELL_TEXTURES = {'particle': T + 'panel', 'panel': T + 'panel', 'frame': T + 'frame', 'base': T + 'base', 'lining': T + 'lining', 'pipe': T + 'pipe'}
ALL = ('down', 'up', 'north', 'south', 'west', 'east')


def shell(name, elements, extra={}):
    write(A / 'models/block/furnace' / f'{name}.json', {'parent': 'minecraft:block/block', 'textures': {**SHELL_TEXTURES, **extra}, 'elements': elements})


def chamfer(y1, y2, texture, faces=None):
    """The outer corner of the vessel cut on the diagonal in four steps, so the furnace reads as an octagon."""
    return [box([0, y1, 10], [16, y2, 16], texture, (), faces), box([3, y1, 6], [16, y2, 10], texture, (), faces),
            box([6, y1, 3], [16, y2, 6], texture, (), faces), box([10, y1, 0], [16, y2, 3], texture, (), faces)]


def column(y1, y2):
    """A white service pipe standing at the cut corner, flanged where the layers meet."""
    return [box([3, y1, 3], [7, y2, 7], '#pipe'), box([2, y1, 2], [8, y1 + 1, 8], '#frame')]


def models():
    """The 5 × 5 × 3 vessel, each place authored for the north-west corner or the north side and turned by the blockstate:
    an octagonal shell on a blackened-steel stand, stainless cladding round a recessed sight-glass band, a rim with a pipe ring
    round the bath, and the electrode unit over the centre."""
    bath = lambda lit: {'bath': T + ('bath_molten' if lit else 'bath_crust')}
    panel = {'up': '#lining', 'down': '#base'}
    shell('loose', [box([0, 0, 0], [16, 16, 16], '#panel', ALL, panel), box([0, 0, 0], [16, 3, 16], '#frame', ('down', 'north', 'south', 'west', 'east')),
        box([0, 13, 0], [16, 16, 16], '#frame', ('up', 'north', 'south', 'west', 'east'), {'up': '#lining'})])
    shell('interior', [box([0, 0, 0], [16, 16, 16], '#lining', ALL)])
    # Stand: a recessed blackened plinth behind cast-iron girders and feet.
    shell('stand_corner', chamfer(2, 13, '#base') + chamfer(0, 2, '#frame') + chamfer(13, 16, '#frame') + column(0, 16))
    shell('stand_side', [box([0, 2, 4], [16, 13, 16], '#base', ('south', 'west', 'east')), box([0, 0, 1], [16, 2, 16], '#frame', ('down', 'south', 'west', 'east')),
        box([0, 13, 0], [16, 16, 16], '#frame', ('up', 'south', 'west', 'east')), box([0, 2, 2], [2, 13, 4], '#frame'), box([14, 2, 2], [16, 13, 4], '#frame'),
        box([7, 2, 3], [9, 13, 4], '#frame'), box([2, 6, 3], [14, 8, 4], '#frame')])
    # Cladding: stainless panels; on every side a sight-glass band set three pixels deep between a sill and a lintel.
    shell('wall_corner', chamfer(0, 16, '#panel', panel) + chamfer(12, 16, '#frame') + column(0, 16))
    for lit in (False, True):
        on = '_lit' if lit else ''
        shell('wall_window' + on, [box([0, 0, 4], [16, 16, 16], '#panel', ('south', 'west', 'east'), panel),
            box([0, 0, 0], [16, 3, 4], '#frame', ('down', 'west', 'east')), box([0, 12, 0], [16, 16, 4], '#frame', ('up', 'west', 'east')),
            box([0, 3, 3], [16, 12, 4], '#window', ('west', 'east')), box([0, 3, 1], [1, 12, 3], '#frame'), box([15, 3, 1], [16, 12, 3], '#frame')],
            {'window': T + ('window_on' if lit else 'window_off')})
        # Rim: the bath a third of the way down inside a firebrick-capped wall, a pipe ring outside on brackets.
        rim = [box([0, 0, 1], [16, 10, 16], '#panel', ('down', 'south', 'west', 'east'), panel), box([0, 10, 4], [16, 11, 16], '#bath', ('south', 'west', 'east')),
               box([0, 10, 1], [16, 16, 4], '#panel', ('west', 'east'), {'up': '#lining', 'south': '#lining'}), box([0, 15, 0], [16, 17, 5], '#frame', ('west', 'east')),
               box([0, 0, 0], [16, 2, 1], '#frame', ('down', 'west', 'east')), box([0, 11, -3], [16, 14, 0], '#pipe', ('west', 'east')), box([6, 10, -4], [10, 15, 1], '#frame')]
        shell('rim_side' + on, rim, bath(lit))
        shell('rim_centre' + on, rim + [box([4, 17, 1], [12, 19, 5], '#base')], bath(lit))
        shell('rim_corner' + on, chamfer(0, 10, '#panel', panel) + [box([10, 10, 0], [16, 16, 3], '#panel', (), {'up': '#lining'}), box([0, 10, 10], [3, 16, 16], '#panel', (), {'up': '#lining'}),
            box([3, 10, 6], [6, 16, 10], '#panel', (), {'up': '#lining'}), box([6, 10, 3], [10, 16, 6], '#panel', (), {'up': '#lining'}),
            box([6, 10, 6], [16, 11, 16], '#bath'), box([3, 10, 10], [16, 11, 16], '#bath')] + chamfer(15, 17, '#frame') + column(0, 19), bath(lit))
        shell('bath' + on, [box([0, 0, 0], [16, 10, 16], '#lining', ('down', 'north', 'south', 'west', 'east')), box([0, 10, 0], [16, 11, 16], '#bath', ('north', 'south', 'west', 'east'))], bath(lit))
        # Electrode unit: four graphite electrodes on two cast-iron beams, a stainless hub feeding them through white bus pipes, and
        # four guide posts carrying the gantry.
        parts = [box([0, 0, 0], [16, 10, 16], '#lining', ('down', 'north', 'south', 'west', 'east')), box([0, 10, 0], [16, 11, 16], '#bath', ('north', 'south', 'west', 'east'))]
        for x in (-7, 17):
            for z in (-7, 17):
                parts += [box([x, 6, z], [x + 6, 27, z + 6], '#electrode'), box([x - 1, 15, z - 1], [x + 7, 17, z + 7], '#clamp'), box([x - 1, 22, z - 1], [x + 7, 24, z + 7], '#clamp')]
        for x in (-13, 26):
            for z in (-13, 26):
                parts += [box([x, 11, z], [x + 3, 30, z + 3], '#frame'), box([x - 1, 11, z - 1], [x + 4, 12, z + 4], '#base')]
        parts += [box([-13, 27, -6], [29, 30, -2], '#frame'), box([-13, 27, 18], [29, 30, 22], '#frame'), box([-6, 27, -13], [-2, 30, 29], '#frame'), box([18, 27, -13], [22, 30, 29], '#frame'),
                  box([3, 18, 3], [13, 31, 13], '#hub'), box([2, 30, 2], [14, 32, 14], '#frame'), box([5, 11, 5], [11, 18, 11], '#base'),
                  box([6, 25, -4], [10, 29, 3], '#pipe'), box([6, 25, 13], [10, 29, 20], '#pipe'), box([-4, 25, 6], [3, 29, 10], '#pipe'), box([13, 25, 6], [20, 29, 10], '#pipe')]
        write(A / 'models/block/furnace' / f'anode{on}.json', {'parent': 'minecraft:block/block', 'textures': {'particle': T + 'electrode', 'lining': T + 'lining', 'electrode': T + 'electrode',
            'clamp': T + 'clamp', 'frame': T + 'frame', 'base': T + 'base', 'pipe': T + 'pipe', 'hub': T + 'panel', **bath(lit)}, 'elements': parts})
    # Busbar: aluminium bars on a blackened stand, reaching over the rim, with porcelain insulators under the terminals.
    write(A / 'models/block/furnace/busbar.json', {'parent': 'minecraft:block/block', 'textures': {'particle': T + 'aluminium', 'frame': T + 'frame', 'bus': T + 'aluminium',
        'insulator': T + 'insulator', 'clamp': T + 'clamp', 'base': T + 'base'},
        'elements': [box([1, 0, 0], [15, 16, 3], '#base', ('north',)), box([0, 0, 3], [16, 2, 13], '#frame', ('down',)), box([1, 2, 10], [3, 16, 12], '#frame'), box([13, 2, 10], [15, 16, 12], '#frame'),
                     box([1, 14, 3], [15, 16, 12], '#frame'),
                     box([3, 2, 3], [6, 19, 9], '#bus'), box([10, 2, 3], [13, 19, 9], '#bus'), box([3, 17, -12], [6, 20, 3], '#bus'), box([10, 17, -12], [13, 20, 3], '#bus'),
                     box([3, 19, 3], [6, 24, 7], '#insulator'), box([10, 19, 3], [13, 24, 7], '#insulator'), box([2, 24, 3], [7, 26, 7], '#clamp'), box([9, 24, 3], [14, 26, 7], '#clamp')]})
    corner = {(0, 0): 0, (0, 4): 90, (4, 4): 180, (4, 0): 270}
    def side(row, col):
        return 0 if row == 0 else 90 if col == 4 else 180 if row == 4 else 270
    variants = {}
    for cell in range(76):
        layer, row, col = cell // 25, cell % 25 // 5, cell % 5
        for lit in ('false', 'true'):
            on = '_lit' if lit == 'true' else ''; y = 0
            if cell == 75: model = 'loose'
            elif (row, col) in corner: model, y = ('stand_corner', 'wall_corner', 'rim_corner' + on)[layer], corner[(row, col)]
            elif row in (0, 4) or col in (0, 4):
                centre = row == 2 or col == 2; y = side(row, col)
                model = ('stand_side', 'wall_window' + on, ('rim_centre' if centre else 'rim_side') + on)[layer]
            else: model = 'bath' + on if layer == 2 else 'interior'
            variants[f'cell={cell},lit={lit}'] = {'model': NS + 'block/furnace/' + model, **({'y': y} if y else {})}
    write(A / 'blockstates/electrolysis_furnace_shell.json', {'variants': variants})
    write(A / 'blockstates/electrolysis_furnace_anode.json', {'variants': {f'cell={c},lit={l}': {'model': NS + 'block/furnace/anode' + ('_lit' if l == 'true' else '')} for c in range(76) for l in ('false', 'true')}})
    write(A / 'blockstates/electrolysis_furnace_busbar.json', {'variants': {f'facing={f}': {'model': NS + 'block/furnace/busbar', **({'y': y} if y else {})}
        for f, y in (('north', 0), ('east', 90), ('south', 180), ('west', 270))}})
    for id, model in (('electrolysis_furnace_shell', 'loose'), ('electrolysis_furnace_anode', 'anode'), ('electrolysis_furnace_busbar', 'busbar')):
        write(A / 'models/item' / f'{id}.json', {'parent': NS + 'block/furnace/' + model})
        write(D / 'loot_table/blocks' / f'{id}.json', {'type': 'minecraft:block', 'pools': [{'rolls': 1, 'entries': [{'type': 'minecraft:item', 'name': NS + id}], 'conditions': [{'condition': 'minecraft:survives_explosion'}]}]})


def recipes():
    shaped = {
        'electrolysis_furnace_shell': (['PPP', 'BZB', 'PPP'], {'P': {'item': 'create:iron_sheet'}, 'B': {'item': 'minecraft:bricks'}, 'Z': {'item': NS + 'zirconia'}}, 8),
        'electrolysis_furnace_anode': ([' I ', 'CCC', 'CCC'], {'I': {'item': 'minecraft:iron_ingot'}, 'C': {'item': 'minecraft:coal_block'}}, 1),
        'electrolysis_furnace_busbar': (['T T', 'CPC', 'CPC'], {'T': {'item': 'minecraft:terracotta'}, 'C': {'item': 'create:copper_sheet'}, 'P': {'item': 'create:iron_sheet'}}, 1),
    }
    for id, (pattern, key, count) in shaped.items():
        write(D / 'recipe' / f'{id}.json', {'type': 'minecraft:crafting_shaped', 'category': 'misc', 'pattern': pattern, 'key': key, 'result': {'id': NS + id, 'count': count}})
    ids = [NS + id for id in shaped]
    tag('minecraft/tags/block/mineable/pickaxe.json', ids); tag('create/tags/block/non_movable.json', ids)


def furnace_scene():
    s = Structure(11, 7)
    for x in range(3, 8):
        for z in range(3, 8):
            for layer in range(3):
                cell = layer * 25 + (z - 3) * 5 + x - 3
                if layer == 2 and (x, z) == (5, 5): s.block(x, 3, z, NS + 'electrolysis_furnace_anode', cell='75', lit='true')
                else: s.block(x, 1 + layer, z, NS + 'electrolysis_furnace_shell', cell=str(cell), lit='true')
    s.block(5, 3, 2, NS + 'electrolysis_furnace_busbar', facing='south')
    s.block(8, 1, 1, 'electroenergetics:creative_battery')
    return s




SCENES = {
    'electrolysis_furnace': (furnace_scene, 11, 7, ['electrolysis_furnace_shell', 'electrolysis_furnace_anode', 'electrolysis_furnace_busbar'], ('Molten-salt electrolysis furnace', '溶融塩電解炉'), [
        ([3, 1, 3], {},
         'Build a 5 × 5 × 3 shell and put the carbon anode in the centre of the top layer. The blocks take the shape of the stand, the cladding and the rim.',
         '外殻を5×5×3に組み、上段の中央に炭素陽極を置きます。組み上がると、架台・外装・縁の形になります。'),
        ([5, 3, 2], {},
         'Fit the busbar on an upper side and wire both terminals to CEE. At 200 V it uses 20 kW.',
         '上段側面に母線を付け、2端子をCEE回路につなぎます。200 Vで20 kWです。'),
        ([5, 3, 5], {},
         'Wait for the bath to reach 960 °C. Increase power if it fails to heat up.',
         '電解浴が960 °Cまで温まるのを待ちます。温度が上がらなければ電力を増やします。'),
        ([3, 3, 3], {},
         'Feed oxides and coal or charcoal through any non-bottom face. Check JEI for inputs and power.',
         '底面以外から酸化物と石炭か木炭を入れます。材料と電力はJEIで確認します。'),
        ([6, 1, 3], {},
         'Take the metal from the bottom faces. Goggles on any part show the bath temperature, the power and the progress.',
         '金属は底面から取り出します。ゴーグルを使うと、電解浴の温度・電力・進み具合がわかります。'),
    ]),
}



def ponder():
    path = A / 'ponder/tutorials.json'
    raw = path.read_bytes().decode('utf-8'); newline = '\r\n' if '\r\n' in raw else '\n'
    catalog = json.loads(raw)
    for id, (layout, size, height, components, _, steps) in SCENES.items():
        layout().save(id)
        (A / 'ponder/plant' / f'{id}.nbt').write_bytes((OUT / f'{id}.nbt').read_bytes()); (OUT / f'{id}.nbt').unlink()
        entry = {'id': id, 'size': size, 'height': height, 'scale': .6 if id == 'electrolysis_furnace' else .5, 'components': components,
            'steps': [{'target': target, 'key': f'step_{i + 1}', **extra} for i, (target, extra, _, _) in enumerate(steps)]}
        for index, tutorial in enumerate(catalog):
            if tutorial['id'] == id: catalog[index] = entry; break
        else: catalog.append(entry)
    path.write_bytes((json.dumps(catalog, ensure_ascii=False, indent=2).replace('\n', newline) + newline).encode('utf-8'))


NAMES = {
    'block.create_refinery_n_reactors.electrolysis_furnace_shell': ('Electrolysis Furnace Shell', '電解炉の外殻', '电解炉外壳'),
    'block.create_refinery_n_reactors.electrolysis_furnace_anode': ('Electrolysis Furnace Carbon Anode', '電解炉の炭素陽極', '电解炉碳阳极'),
    'block.create_refinery_n_reactors.electrolysis_furnace_busbar': ('Electrolysis Furnace Busbar', '電解炉の母線', '电解炉母线'),
}
GOGGLES = {
    'status.structure': ('Incomplete: 5 × 5 × 3 shell with the carbon anode at the top centre, busbar outside facing it', '未完成：外殻5×5×3、上段中央に炭素陽極、外側に陽極を向いた母線'),
    'status.power': ('No current: connect both busbar terminals to the circuit', '電流なし：母線の両端子を回路につないでください'),
    'status.melting': ('Melting the salt bath', '電解浴を溶かしています'),
    'status.materials': ('Waiting for an oxide and anode carbon', '酸化物と陽極用の炭素を待っています'),
    'status.output': ('Metal output full', '金属の取り出し口が満杯です'),
    'status.running': ('Electrolysing', '電解中'),
    'bath': ('Bath %s °C (molten from %s °C)', '電解浴 %s °C（%s °Cで溶融）'),
    'power': ('%s kW at %s V', '%s kW（%s V）'),
    'progress': ('%s: %s%%', '%s：%s%%'),
}


def translations():
    for language, index in (('en_us', 0), ('ja_jp', 1), ('zh_cn', 2)):
        path = A / 'lang' / f'{language}.json'; raw = path.read_bytes().decode('utf8'); nl = '\r\n' if '\r\n' in raw else '\n'; data = json.loads(raw)
        for key, names in NAMES.items(): data[key] = names[index]
        if index < 2:
            for key, text in GOGGLES.items(): data['electrolysis_furnace.create_refinery_n_reactors.' + key] = text[index]
            data['jei.create_refinery_n_reactors.electrolysis_furnace'] = ('Electrolysis Furnace', '溶融塩電解炉')[index]
            data['jei.create_refinery_n_reactors.note.electrolysis_furnace'] = ('%s kJ of electrical work with the bath molten; one coal or charcoal burns at the anode', '電解浴を溶かした状態で%s kJの電力量。陽極で石炭か木炭を1個消費')[index]
            for id, (_, _, _, _, title, steps) in SCENES.items():
                data[f'create_refinery_n_reactors.ponder.shared.guide.{id}.title'] = title[index]
                data[f'create_refinery_n_reactors.ponder.plant_{id}.header'] = title[index]
                for i, step in enumerate(steps): data[f'create_refinery_n_reactors.ponder.shared.guide.{id}.step_{i + 1}'] = step[2 + index]
        path.write_bytes((json.dumps(data, ensure_ascii=False, indent=2).replace('\n', nl) + nl).encode('utf8'))


if __name__ == '__main__':
    textures(); models(); recipes(); ponder(); translations()
    print('Generated the electrolysis furnace')
