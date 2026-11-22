#!/usr/bin/env python3
"""Generate the reaction tower: block models of a 3x3 tank hollow inside its 6 px walls, floor and roof, with a 22 px hatch in the roof, sight glasses in the middle of each side, names and the Ponder structures."""
from pathlib import Path
import itertools
import json
import shutil
import sys
import zipfile

sys.path.insert(0, str(Path(__file__).resolve().parent))
from generate_plant_tank_assets import A, D, NS, write
import generate_petrochemical_ponder as ponder

REACTION = 'reaction_tower'
K = 'create_refinery_n_reactors'
WALL, FLOOR, ROOF = 6, 2, 2
# The hatch in the middle of the roof, 22 px across, as the lidded vessel's: its span in tower pixels (the tower is 48 px across).
HATCH = (13, 35)  # ReactionTowerBlock.HATCH_MIN, HATCH_MAX
# The sight glass through the middle block of each side: its opening across the wall and up the block, a thick pane set 2 px into the wall,
# and a bolted steel flange standing 1 px proud of the shell round it.
OPENING, GLASS = (4, 12), (2, 4)
FLANGE, BOLTS = (2, 14), ((2.5, 2.5), (2.5, 12.5), (12.5, 2.5), (12.5, 12.5), (7.5, 2.5), (7.5, 12.5), (2.5, 7.5), (12.5, 7.5))
# Outer-wall flags as the block state numbers them; a 3x3 tower uses the empty mask, the four sides and the four corners.
NORTH, EAST, SOUTH, WEST = 1, 2, 4, 8
SHAFT_MASKS = (0, NORTH, EAST, SOUTH, WEST, NORTH | EAST, NORTH | WEST, SOUTH | EAST, SOUTH | WEST)
TEXTURES = {'side': NS + 'block/tank/reaction_tower', 'top': NS + 'block/tank/reaction_tower_top', 'inner': NS + 'block/reactor/steel',
            'floor': NS + 'block/reactor/steel', 'glass': 'minecraft:block/glass', 'frame': NS + 'block/reactor/silver/frame',
            'bolt': NS + 'block/reactor/steel', 'particle': NS + 'block/tank/reaction_tower'}
TEXTS = {
    f'block.{K}.reaction_tower': ('Reaction Tower', '反応塔', '反应塔'),
    f'reaction_tower.{K}.size': ('Build it 3x3 and %s to %s layers high', '3×3で%s～%s段に積んでください', '请建成3×3、%s～%s层高'),
    f'reaction_tower.{K}.status.unformed': ('unformed', '未成立', '未成形'),
    f'reaction_tower.{K}.separator': ('No trays: a high-pressure gas-liquid separator', '反応棚段なし：高圧気液分離タンク', '无塔板：高压气液分离罐'),
    f'reaction_tower.{K}.status.catalyst': ('put catalyst onto the trays', '反応棚段に触媒を入れてください', '请在塔板上放入催化剂'),
    f'reaction_tower.{K}.status.mixed': ('the trays hold different catalysts', '反応棚段の触媒がそろっていません', '塔板上的催化剂不一致'),
    f'reaction_tower.{K}.status.hatch': ('hatch open: close it with an empty hand to run', 'ハッチが開いています。素手で閉じると運転します', '舱口已打开：空手关闭后运行'),
    f'reaction_tower.{K}.status.no_feed': ('waiting for feed', '原料待ち', '等待原料'),
    f'reaction_tower.{K}.status.heat': ('needs more heat under the base', '底面の下の熱が足りません', '底部下方热量不足'),
    f'reaction_tower.{K}.status.output': ('product outlet full', '製品の出口が満杯', '产物出口已满'),
    f'reaction_tower.{K}.status.running': ('running', '運転中', '运行中'),
    f'reaction_tower.{K}.catalyst': ('Working catalysts %s | trays %s of %s layers | longest life %s batches', '働いている触媒 %s | 反応棚段 %s／%s段 | 最長寿命 %sバッチ',
                                     '有效催化剂 %s | 塔板 %s／%s层 | 最长寿命 %s批'),
    f'reaction_tower.{K}.solids': ('Trays %s of %s layers | solid feed %s | products %s', '反応棚段 %s／%s段 | 固体原料 %s | 生成物 %s', '塔板 %s／%s层 | 固体原料 %s | 产物 %s'),
    # The tower's feed is an intermediate gas, so its page says what it is made of and the compressor gets a page of its own.
    f'jei.{K}.note.haber_bosch': ('Feed: H2 3 : N2 1 gas, compressed once. Heat: %s', '原料＝水素3：窒素1の合成ガスを1段圧縮。熱：%s',
                                  '原料＝氢3：氮1合成气压缩一级。热：%s'),
    f'jei.{K}.gas_compression': ('Gas Compression', 'ガスの圧縮', '气体压缩'),
    f'jei.{K}.note.compression': ('One pressure step in the reciprocating compressor at %s RPM or more. The amount stays the same.',
                                  '往復式圧縮機で圧力を1段上げる。%s RPM以上。量は変わらない。', '在往复式压缩机中升高一级压力，需%s RPM以上，量不变。'),
    f'reaction_tower.{K}.contents': ('Feed %s mB | Product %s mB | Batch %s/%s ticks', '原料 %s mB | 製品 %s mB | バッチ %s／%s tick', '原料 %s mB | 产物 %s mB | 批次 %s／%s tick'),
}
# Left from the pile-packed Haber-Bosch tower this one replaces.
RETIRED_PREFIXES = (f'haber_bosch.{K}.', f'message.{K}.haber_bosch.', f'{K}.ponder.shared.haber_bosch_tower.')
RETIRED = (f'block.{K}.spent_triiron_tetroxide_pile', f'block.{K}.reaction_tower_gauge_panel', f'jei.{K}.note.haber_bosch_tower', f'reaction_tower.{K}.status.baskets')


def hollow(mask, low, high):
    return (WALL if mask & low else 0, 16 - WALL if mask & high else 16)


def face(texture, cull=None):
    value = {'texture': '#' + texture}
    if cull: value['cullface'] = cull
    return value


def sight_glass(mask):
    """The wall of a side's middle block with an opening through it, a thick pane of glass in the opening and a bolted flange round it outside."""
    side = {NORTH: 'north', SOUTH: 'south', WEST: 'west', EAST: 'east'}[mask]
    inward = {'north': 'south', 'south': 'north', 'west': 'east', 'east': 'west'}[side]
    outward = side in ('north', 'west')
    depth = (0, WALL) if outward else (16 - WALL, 16)
    along_x = side in ('north', 'south')

    def into(d0, d1):
        """A depth range measured inward from the outer face."""
        return (d0, d1) if outward else (16 - d1, 16 - d0)

    def box(u0, u1, y0, y1, faces, w0=depth[0], w1=depth[1]):
        lo, hi = ([u0, y0, w0], [u1, y1, w1]) if along_x else ([w0, y0, u0], [w1, y1, u1])
        return {'from': lo, 'to': hi, 'faces': faces}

    o0, o1 = OPENING
    across = ('west', 'east') if along_x else ('north', 'south')
    ends = ('down', 'up')
    elements = [
        box(0, o0, 0, 16, {side: face('side', side), inward: face('inner'), across[1]: face('inner'), 'up': face('top', 'up'), 'down': face('top', 'down')}),
        box(o1, 16, 0, 16, {side: face('side', side), inward: face('inner'), across[0]: face('inner'), 'up': face('top', 'up'), 'down': face('top', 'down')}),
        box(o0, o1, 0, o0, {side: face('side', side), inward: face('inner'), 'up': face('inner'), 'down': face('top', 'down')}),
        box(o0, o1, o1, 16, {side: face('side', side), inward: face('inner'), 'down': face('inner'), 'up': face('top', 'up')}),
        box(o0, o1, o0, o1, {side: face('glass'), inward: face('glass')}, *into(*GLASS)),
    ]
    # The flange: four bars framing the opening, 1 px proud of the shell.
    f0, f1 = FLANGE
    proud = into(-1, 0)
    all_faces = lambda: {d: face('frame') for d in ('north', 'east', 'south', 'west', 'up', 'down') if d != inward}
    for u0, u1, y0, y1 in ((f0, f1, f0, o0), (f0, f1, o1, f1), (f0, o0, o0, o1), (o1, f1, o0, o1)):
        elements.append(box(u0, u1, y0, y1, all_faces(), *proud))
    # Bolt heads on the flange.
    head = into(-1.5, -1)
    for u, y in BOLTS:
        elements.append(box(u, u + 1, y, y + 1, {d: face('bolt') for d in ('north', 'east', 'south', 'west', 'up', 'down') if d != inward}, *head))
    return elements


def plate(x0, x1, z0, z1, y0, y1, inside, outside, opening=None):
    """The floor or roof across the hollow, less the hatch opening where it crosses this block (x0, x1, z0, z1 in block pixels)."""
    if opening is None:
        return [{'from': [x0, y0, z0], 'to': [x1, y1, z1], 'faces': {inside_face(outside): face(inside), outside: face('top', outside)}}]
    ox0, ox1, oz0, oz1 = opening
    xs = sorted({x0, x1, max(x0, min(x1, ox0)), max(x0, min(x1, ox1))})
    zs = sorted({z0, z1, max(z0, min(z1, oz0)), max(z0, min(z1, oz1))})
    elements = []
    for a, b in zip(xs, xs[1:]):
        for c, d in zip(zs, zs[1:]):
            if a >= ox0 and b <= ox1 and c >= oz0 and d <= oz1: continue
            faces = {inside_face(outside): face(inside), outside: face('top', outside)}
            # The opening's rim: the sides of a piece that face the opening.
            if b == ox0 and c < oz1 and d > oz0: faces['east'] = face('inner')
            if a == ox1 and c < oz1 and d > oz0: faces['west'] = face('inner')
            if d == oz0 and a < ox1 and b > ox0: faces['south'] = face('inner')
            if c == oz1 and a < ox1 and b > ox0: faces['north'] = face('inner')
            elements.append({'from': [a, y0, c], 'to': [b, y1, d], 'faces': faces})
    return elements


def hatch_opening(mask):
    """The hatch's span across this block, in block pixels, from which column and row of the tower it is in."""
    column = 0 if mask & WEST else 2 if mask & EAST else 1
    row = 0 if mask & NORTH else 2 if mask & SOUTH else 1
    span = lambda index: (max(0, HATCH[0] - 16 * index), min(16, HATCH[1] - 16 * index))
    (x0, x1), (z0, z1) = span(column), span(row)
    return (x0, x1, z0, z1) if x0 < x1 and z0 < z1 else (0, 0, 0, 0)


def inside_face(outside):
    return 'up' if outside == 'down' else 'down'


def cell(mask, bottom, top, window=False):
    """The block's share of the tower: its sub-boxes on the grid the hollow cuts, with faces only where they show."""
    if mask == 15:
        return [{'from': [0, 0, 0], 'to': [16, 16, 16], 'faces': {d: face('top' if d in ('up', 'down') else 'side', d)
                                                                  for d in ('north', 'east', 'south', 'west', 'up', 'down')}}]
    x0, x1 = hollow(mask, WEST, EAST)
    z0, z1 = hollow(mask, NORTH, SOUTH)
    floor = plate(x0, x1, z0, z1, 0, FLOOR, 'floor', 'down') if bottom else []
    floor += plate(x0, x1, z0, z1, 16 - ROOF, 16, 'inner', 'up', hatch_opening(mask)) if top else []
    if window: return sight_glass(mask) + floor
    xs = sorted({0, x0, x1, 16})
    zs = sorted({0, z0, z1, 16})
    inside = lambda a, b, c, d: a >= x0 and b <= x1 and c >= z0 and d <= z1
    elements = []
    for i in range(len(xs) - 1):
        for j in range(len(zs) - 1):
            a, b, c, d = xs[i], xs[i + 1], zs[j], zs[j + 1]
            if inside(a, b, c, d):
                continue
            faces = {'up': face('top', 'up'), 'down': face('top', 'down')}
            for name, edge, outer, neighbour in (('north', c == 0, mask & NORTH, j > 0 and inside(a, b, zs[j - 1], c)),
                                                  ('south', d == 16, mask & SOUTH, j + 2 < len(zs) and inside(a, b, d, zs[j + 2])),
                                                  ('west', a == 0, mask & WEST, i > 0 and inside(xs[i - 1], a, c, d)),
                                                  ('east', b == 16, mask & EAST, i + 2 < len(xs) and inside(b, xs[i + 2], c, d))):
                if edge and outer: faces[name] = face('side', name)
                elif not edge and neighbour: faces[name] = face('inner')
            elements.append({'from': [a, 0, c], 'to': [b, 16, d], 'faces': faces})
    return elements + floor


def reaction_tower():
    root = A / f'models/block/{REACTION}'
    shutil.rmtree(root, ignore_errors=True)
    multipart = []
    for mask in range(16):
        for bottom, top, window in itertools.product((False, True), repeat=3):
            used = mask if mask in SHAFT_MASKS else 15
            glazed = window and used in (NORTH, EAST, SOUTH, WEST)
            name = f'cell_{used}' + ('_window' if glazed else '') + ('_bottom' if bottom and used != 15 else '') + ('_top' if top and used != 15 else '')
            if not (root / f'{name}.json').exists():
                write(root / f'{name}.json', {'parent': 'minecraft:block/block', 'render_type': 'minecraft:cutout_mipped', 'textures': TEXTURES,
                                              'elements': cell(used, bottom, top, glazed)})
            when = {side: str(bool(mask & flag)).lower() for side, flag in (('north', NORTH), ('east', EAST), ('south', SOUTH), ('west', WEST))}
            when['bottom'] = str(bottom).lower()
            when['top'] = str(top).lower()
            when['window'] = str(window).lower()
            multipart.append({'when': when, 'apply': {'model': NS + f'block/{REACTION}/{name}'}})
    write(A / f'blockstates/{REACTION}.json', {'multipart': multipart})
    write(A / f'models/item/{REACTION}.json', {'parent': NS + f'block/{REACTION}/cell_15'})
    write(D / f'loot_table/blocks/{REACTION}.json', {'type': 'minecraft:block', 'pools': [{'rolls': 1, 'entries': [{'type': 'minecraft:item', 'name': NS + REACTION}],
          'conditions': [{'condition': 'minecraft:survives_explosion'}]}]})
    tags = Path(D.parent / 'minecraft/tags/block/mineable/pickaxe.json')
    tag = json.loads(tags.read_text())
    if NS + REACTION not in tag['values']: tag['values'].append(NS + REACTION); write(tags, tag)


# The dial face of Create's boiler gauge texture, which keeps its colours; the rest of its copper is repainted in the tower's steel.
GAUGE_DIAL_FACE = (1, 17, 14, 27)


def gauge():
    """Create's boiler gauge and dial as the tower's own models, on a copy of their texture whose copper body is repainted in the shell's dark steel."""
    from electrolysis_hardware import create_texture
    texture = create_texture('boiler_gauge', 'RGBA')
    px = texture.load()
    x0, y0, x1, y1 = GAUGE_DIAL_FACE
    for y in range(texture.height):
        for x in range(texture.width):
            r, g, b, a = px[x, y]
            if a == 0 or x0 <= x < x1 and y0 <= y < y1 or not (r > g > b and r - b > 40): continue
            grey = round((.3 * r + .59 * g + .11 * b) * .5)
            px[x, y] = (grey, grey, grey + 3, a)
    out = A / f'textures/block/{REACTION}/gauge.png'
    out.parent.mkdir(parents=True, exist_ok=True)
    texture.save(out)
    for name in ('gauge', 'gauge_dial'):
        model = json.loads(create_model(f'steam_engine/{name}'))
        model['textures'] = {'0': NS + f'block/{REACTION}/gauge', 'particle': NS + f'block/{REACTION}/gauge'}
        write(A / f'models/block/{REACTION}/{name}.json', model)


def create_model(name):
    """A block model from the Create jar Gradle resolved for this project."""
    path = f'assets/create/models/block/{name}.json'
    for jar in sorted(Path.home().glob('.gradle/caches/modules-2/files-2.1/maven.modrinth/create/*/*/*.jar')):
        with zipfile.ZipFile(jar) as source:
            if path in source.namelist(): return source.read(path).decode()
    raise SystemExit('Create jar not found; run a Gradle build first')


def names():
    for index, language in enumerate(('en_us', 'ja_jp', 'zh_cn')):
        path = A / 'lang' / f'{language}.json'
        strings = json.loads(path.read_text())
        for key in list(strings):
            if key in RETIRED or key.startswith(RETIRED_PREFIXES): strings.pop(key)
        for key, text in TEXTS.items(): strings[key] = text[index]
        path.write_text(json.dumps(strings, ensure_ascii=False, indent=2) + '\n')


def tower(s, window=True):
    """Four layers of a 3x3 tower from y 2, with sight glasses in its side middles as a formed tower has."""
    for y in range(2, 6):
        for x in range(3, 6):
            for z in range(3, 6):
                side = (x == 4) != (z == 4)
                s.block(x, y, z, NS + REACTION, north=str(z == 3).lower(), south=str(z == 5).lower(), west=str(x == 3).lower(), east=str(x == 5).lower(),
                        bottom=str(y == 2).lower(), top=str(y == 5).lower(), shape='plain', window=str(window and side).lower())


def structures():
    """Feed piped into the bottom layer's west side and products out of its east side; a reactor stands over four corner burners, and a separator
    also lets its gas out of the top layer's east side, behind sight glasses."""
    ponder.OUT = A / 'ponder/plant'
    for name, window in ((REACTION, False), (REACTION + '_separator', True)):
        s = ponder.Structure(9, 7)
        tower(s)
        if not window:
            for x, z in ((3, 3), (5, 3), (3, 5), (5, 5)): s.burner(x, 1, z, 'kindled')
        s.pipe(2, 2, 4, 'east', 'west');s.pump(1, 2, 4, 'east');s.tank(0, 2, 4)
        s.pipe(6, 2, 4, 'east', 'west');s.pump(7, 2, 4, 'east');s.tank(8, 2, 4)
        if window:
            s.pipe(6, 5, 4, 'east', 'west');s.pump(7, 5, 4, 'east')
            # The gas tank stands on a pillar of stone over the liquid tank.
            s.block(8, 3, 4, 'minecraft:smooth_stone')
            s.tank(8, 5, 4)
        s.save(name)


def hatch():
    """Both roof hatches share a 22 px plate with each UV held within its sprite."""
    lo, hi = HATCH[0] - 16, HATCH[1] - 16
    faces = {'up': {'texture': '#top', 'uv': [0, 0, 16, 16]},
             'down': {'texture': '#inner', 'uv': [0, 0, 16, 16]},
             **{side: {'texture': '#top', 'uv': [0, 14, 16, 16]} for side in ('north', 'east', 'south', 'west')}}
    write(A / f'models/block/{REACTION}/hatch.json', {
        'textures': {'top': NS + 'block/tank/reaction_tower_top', 'inner': NS + 'block/tank/reaction_tower_inner', 'particle': '#top'},
        'elements': [{'from': [lo, 16 - ROOF, lo], 'to': [hi, 16, hi], 'faces': faces}]})
    write(A / 'models/block/lidded_vessel/hatch.json', {
        'parent': NS + f'block/{REACTION}/hatch',
        'textures': {'top': NS + 'block/tank/ammonia_separator_top', 'inner': NS + 'block/tank/ammonia_separator_inner'}})


if __name__ == '__main__':
    reaction_tower(); gauge(); hatch(); names(); structures()
    print('Generated the reaction tower models, names and Ponder structure')
