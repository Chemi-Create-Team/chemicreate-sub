#!/usr/bin/env python3
"""The gas tank container: a horizontal octagonal steel gas tank held in an open andesite-alloy container frame, formed only at the container sizes
(1 x 1 and 2 long, 2 x 2 and 5 long, 3 x 3 and 7 long). Writes its textures, models, block state, item model, loot, tags, names and
Ponder lesson. Run from the repository root.

Models are authored with the run along Z, north being the tank's first (lowest) end, and across along X; the block state turns a tank
along X by 270 degrees. Every block of a finished tank draws its share of the frame. The tank itself and the end braces are drawn
whole, slice by slice, by one block of each slice, the middle one, or for 2 x 2 the first, reaching into its neighbours; a loose block
draws a closed one-block container."""
import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from generate_petrochemical_ponder import OUT, Structure, integer, named, string
from generate_ponder_language import SHARED, generate as ponder_titles

ROOT = Path(__file__).resolve().parents[1]
A = ROOT / 'src/main/resources/assets/create_refinery_n_reactors'
D = ROOT / 'src/main/resources/data'
NS = 'create_refinery_n_reactors:'
BLOCK = 'gas_tank_container'
LENGTHS = {1: 2, 2: 5, 3: 7}
SIDES = ('north', 'south', 'east', 'west', 'up', 'down')
NAMES = ('Gas Tank Container', 'ガスタンクコンテナ', '气体罐式集装箱')
INCOMPLETE = ('Not a finished tank: %s × %s across needs exactly %s blocks in a row', '未完成：断面%s×%sは長さちょうど%s個で完成します',
              '未完成：%s×%s截面需正好排成%s格')
ENDS = {'single': (True, True), 'first': (True, False), 'middle': (False, False), 'last': (False, True)}


def write(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + '\n')


FRAME = {'light': (154, 164, 157, 255), 'base': (130, 135, 132, 255), 'dark': (96, 99, 95, 255), 'seam': (80, 83, 81, 255)}
CASTING = {'light': (112, 117, 112, 255), 'base': (96, 99, 95, 255), 'dark': (73, 76, 74, 255), 'hole': (23, 25, 26, 255)}
STEEL = {'shine': (125, 130, 135, 255), 'light': (84, 87, 91, 255), 'base': (72, 75, 79, 255), 'dark': (60, 63, 67, 255), 'weld': (34, 37, 41, 255)}
# Pixel origins inside the sheets: a beam's faces pick the strip that runs their way, the shell's faces the weld that runs theirs.
BEAM_ALONG, BEAM_UPRIGHT, BEAM_END, BRACE = (0, 0), (0, 16), (16, 16), (0, 4)
SHELL_SIDE, SHELL_TOP, WELD = (0, 0), (24, 0), 8
SHEET = {'frame': 32, 'brace': 32, 'casting': 16, 'shell': 48, 'head': 48}


def textures():
    """Andesite-alloy frame beams and corner castings, and the bare steel shell with one girth weld per block and its dished heads.
    Everything is lit from the upper left and drawn at one pixel per model pixel."""
    from PIL import Image, ImageDraw
    folder = A / 'textures/block' / BLOCK
    for old in folder.glob('*.png'): old.unlink()
    folder.mkdir(parents=True, exist_ok=True)
    def sheet(name, base):
        image = Image.new('RGBA', (SHEET[name], SHEET[name]), base)
        return image, lambda x0, y0, x1, y1, colour: ImageDraw.Draw(image).rectangle((x0, y0, x1, y1), fill=colour)

    image, fill = sheet('frame', FRAME['base'])
    fill(0, 0, 15, 0, FRAME['light']); fill(0, 0, 0, 1, FRAME['seam']); fill(1, 0, 1, 1, FRAME['light'])
    fill(6, 1, 8, 1, FRAME['dark']); fill(12, 1, 13, 1, FRAME['dark'])
    fill(0, 16, 0, 31, FRAME['light']); fill(0, 16, 1, 16, FRAME['seam']); fill(0, 17, 1, 17, FRAME['light'])
    fill(1, 22, 1, 24, FRAME['dark']); fill(1, 28, 1, 29, FRAME['dark'])
    fill(0, 4, 15, 4, FRAME['light']); fill(0, 5, 15, 5, FRAME['dark']); fill(4, 5, 7, 5, FRAME['base']); fill(11, 5, 12, 5, FRAME['base'])
    fill(16, 16, 17, 16, FRAME['light']); fill(16, 17, 16, 17, FRAME['light']); fill(17, 17, 17, 17, FRAME['dark'])
    image.save(folder / 'frame.png')

    image, fill = sheet('casting', CASTING['base'])
    fill(0, 0, 2, 0, CASTING['light']); fill(0, 0, 0, 2, CASTING['light'])
    fill(2, 1, 2, 2, CASTING['dark']); fill(1, 2, 2, 2, CASTING['dark']); fill(1, 1, 1, 1, CASTING['hole'])
    image.save(folder / 'casting.png')

    image, fill = sheet('shell', STEEL['base'])
    x, y = SHELL_SIDE
    for sx, sy, length in ((1, 2, 3), (10, 4, 4), (3, 9, 2), (11, 12, 3), (2, 15, 4), (10, 19, 3), (4, 21, 2)):
        fill(x + sx, y + sy, x + sx + length - 1, y + sy, STEEL['light']); fill(x + sx + 1, y + sy + 1, x + sx + length, y + sy + 1, STEEL['dark'])
    fill(x + WELD - 1, y, x + WELD - 1, y + 23, STEEL['shine']); fill(x + WELD, y, x + WELD, y + 23, STEEL['weld'])
    x, y = SHELL_TOP
    for sx, sy, length in ((2, 1, 3), (5, 10, 4), (9, 3, 2), (13, 11, 3), (16, 2, 4), (19, 10, 3), (21, 4, 2)):
        fill(x + sx, y + sy, x + sx, y + sy + length - 1, STEEL['light']); fill(x + sx + 1, y + sy + 1, x + sx + 1, y + sy + length, STEEL['dark'])
    fill(x, y + WELD - 1, x + 23, y + WELD - 1, STEEL['shine']); fill(x, y + WELD, x + 23, y + WELD, STEEL['weld'])
    image.save(folder / 'shell.png')

    # The head stays one flat steel outside its middle, so the eight overlapping pieces of an end never disagree.
    image, fill = sheet('head', STEEL['base'])
    for py in range(19, 29):
        for px in range(19, 29):
            distance = ((px - 23.5) ** 2 + (py - 23.5) ** 2) ** .5
            if 3 <= distance < 4: fill(px, py, px, py, STEEL['weld'] if px + py >= 47 else STEEL['shine'])
    fill(23, 23, 24, 24, STEEL['dark']); fill(23, 23, 23, 23, STEEL['shine']); fill(24, 23, 24, 23, STEEL['light']); fill(23, 24, 23, 24, STEEL['light'])
    image.save(folder / 'head.png')


def element(lo, hi, texture, rotation=None, centre=None):
    """A box whose faces show their sheet at one pixel per model pixel. Beams and the shell place the pattern by their position along
    the run, so seams and welds repeat once a block; an end face with a centre shows the head around the tank's axis."""
    assert all(-16 <= c <= 32 for c in (*lo, *hi)), (lo, hi)
    size = [b - a for a, b in zip(lo, hi)]
    face = {'north': (0, 1), 'south': (0, 1), 'east': (2, 1), 'west': (2, 1), 'up': (0, 2), 'down': (0, 2)}
    faces = {}
    for side, (u, v) in face.items():
        sheet, du, dv = texture, size[u], size[v]
        if texture == 'casting': area = (0, 0, 3, 3)
        elif texture == 'brace': area = (*BRACE, BRACE[0] + du, BRACE[1] + dv)
        elif texture == 'frame':
            x, y = BEAM_ALONG if du > dv else BEAM_UPRIGHT if dv > du else BEAM_END
            start = lo[u] % 16 if du > dv else lo[v] % 16 if dv > du else 0
            area = (x + start, y, x + start + du, y + dv) if du > dv else (x, y + start, x + du, y + start + dv)
        elif side in ('north', 'south'):
            sheet = 'head'
            area = (lo[0] - centre[0] + 24, centre[1] + 24 - hi[1], hi[0] - centre[0] + 24, centre[1] + 24 - lo[1]) if centre else (0, 0, du, dv)
        elif side in ('east', 'west'): area = (SHELL_SIDE[0] + lo[2], SHELL_SIDE[1], SHELL_SIDE[0] + hi[2], SHELL_SIDE[1] + min(24, dv))
        else: area = (SHELL_TOP[0], SHELL_TOP[1] + lo[2], SHELL_TOP[0] + min(24, du), SHELL_TOP[1] + hi[2])
        faces[side] = {'texture': '#' + ('frame' if sheet == 'brace' else sheet), 'uv': [round(c * 16 / SHEET[sheet], 3) for c in area]}
    shape = {'from': [round(c, 3) for c in lo], 'to': [round(c, 3) for c in hi], 'faces': faces}
    if rotation: shape['rotation'] = rotation
    return shape


def clipped(lo, hi, texture):
    """The part of a box, given across the whole tank's section, that lies in this block; None if none does."""
    lo, hi = [max(0, c) for c in lo], [min(16, c) for c in hi]
    return element(lo, hi, texture) if all(a < b for a, b in zip(lo, hi)) else None


def octagon(cx, cy, apothem, z0, z1, texture):
    """An octagonal prism along Z: two crossed bars for the flat sides and four plates turned 45 degrees for the slanted ones."""
    half = apothem * 0.41421356
    shapes = [element((cx - apothem, cy - half, z0), (cx + apothem, cy + half, z1), texture, centre=(cx, cy)),
              element((cx - half, cy - apothem, z0), (cx + half, cy + apothem, z1), texture, centre=(cx, cy))]
    thick = apothem / 2
    reach = (apothem - thick / 2) * 0.70710678
    for sx, sy in ((1, 1), (-1, 1), (-1, -1), (1, -1)):
        px, py = cx + sx * reach, cy + sy * reach
        shapes.append(element((px - half, py - thick / 2, z0), (px + half, py + thick / 2, z1), texture,
                              {'angle': -45 if sx == sy else 45, 'axis': 'z', 'origin': [round(px, 3), round(py, 3), (z0 + z1) / 2]}))
    return shapes


def braces(cx, cy, span, z0, z1, ox, oy):
    """The end's diagonal plates, a diamond from the middle of each side of the frame, each plate in two halves to keep within a model."""
    left, right, low, high = ox + 3, ox + span - 3, oy + 3, oy + span - 3
    shapes = []
    for (ax, ay), (bx, by) in (((cx, low), (right, cy)), ((right, cy), (cx, high)), ((cx, high), (left, cy)), ((left, cy), (cx, low))):
        angle = 45 if (bx - ax) * (by - ay) > 0 else -45
        length = ((bx - ax) ** 2 + (by - ay) ** 2) ** .5 / 2
        for t in (.25, .75):
            mx, my = ax + (bx - ax) * t, ay + (by - ay) * t
            shapes.append(element((mx - length / 2, my - .75, z0), (mx + length / 2, my + .75, z1), 'brace',
                                  {'angle': angle, 'axis': 'z', 'origin': [round(mx, 3), round(my, 3), (z0 + z1) / 2]}))
    return shapes


def cell_model(width, across, up, first, last):
    """One block of a finished tank {@code width} across, at {@code across}, {@code up} in its section, first or last along the run."""
    span, ox, oy = 16 * width, -16 * across, -16 * up
    shapes = []
    for x0, x1 in ((0, 2), (span - 2, span)):
        for y0, y1 in ((0, 2), (span - 2, span)):
            shapes.append(clipped((ox + x0, oy + y0, 0), (ox + x1, oy + y1, 16), 'frame'))
    for at, z0, z1 in ((first, 0, 2), (last, 14, 16)):
        if not at: continue
        for y0, y1 in ((0, 2), (span - 2, span)):
            shapes.append(clipped((ox + 2, oy + y0, z0), (ox + span - 2, oy + y1, z1), 'frame'))
        for x0, x1 in ((0, 2), (span - 2, span)):
            shapes.append(clipped((ox + x0, oy + 2, z0), (ox + x1, oy + span - 2, z1), 'frame'))
        for x0, x1 in ((-.2, 3), (span - 3, span + .2)):
            for y0, y1 in ((-.2, 3), (span - 3, span + .2)):
                shapes.append(clipped((ox + x0, oy + y0, z0 - .2 if z0 == 0 else z0 - 1), (ox + x1, oy + y1, z1 + .2 if z1 == 16 else z1 + 1), 'casting'))
    if (across, up) == ((width - 1) // 2, (width - 1) // 2):
        cx, cy, apothem = ox + span / 2, oy + span / 2, span / 2 - 3
        shapes += octagon(cx, cy, apothem, 3 if first else 0, 13 if last else 16, 'shell')
        if first: shapes += octagon(cx, cy, apothem - 2, 1.5, 3, 'shell') + braces(cx, cy, span, .5, 1.5, ox, oy)
        if last: shapes += octagon(cx, cy, apothem - 2, 13, 14.5, 'shell') + braces(cx, cy, span, 14.5, 15.5, ox, oy)
    return [shape for shape in shapes if shape]


def assets():
    texture = NS + f'block/{BLOCK}/'
    textures_of = {'frame': texture + 'frame', 'casting': texture + 'casting', 'shell': texture + 'shell', 'head': texture + 'head', 'particle': texture + 'frame'}
    folder = A / f'models/block/{BLOCK}'
    for old in folder.glob('*.json'): old.unlink()
    def model(name, shapes):
        # Shapes reach into the neighbouring blocks, where ambient occlusion would darken them by blocks they do not touch.
        write(folder / f'{name}.json', {'parent': 'minecraft:block/block', 'ambientocclusion': False, 'textures': textures_of, 'elements': shapes})
        return NS + f'block/{BLOCK}/{name}'
    loose = model('loose', cell_model(1, 0, 0, True, True))
    models = {}
    for width in (1, 2, 3):
        for cell in range(width * width):
            for ends, (first, last) in ENDS.items():
                models[width, cell, ends] = model(f'size_{width}_{cell}_{ends}', cell_model(width, cell % width, cell // width, first, last))
    variants = {}
    for axis, turn in (('z', {}), ('x', {'y': 270})):
        for size in range(4):
            for cell in range(9):
                for bottom in (False, True):
                    for top in (False, True):
                        ends = 'single' if bottom and top else 'first' if bottom else 'last' if top else 'middle'
                        path = loose if size == 0 else models[size, cell if cell < size * size else 0, ends]
                        key = f'axis={axis},bottom={str(bottom).lower()},cell={cell},size={size},top={str(top).lower()}'
                        variants[key] = {'model': path, **turn}
    write(A / f'blockstates/{BLOCK}.json', {'variants': variants})
    write(A / f'models/item/{BLOCK}.json', {'parent': loose})


def data():
    write(D / f'create_refinery_n_reactors/loot_table/blocks/{BLOCK}.json', {'type': 'minecraft:block', 'pools': [{'rolls': 1,
        'entries': [{'type': 'minecraft:item', 'name': NS + BLOCK}], 'conditions': [{'condition': 'minecraft:survives_explosion'}]}]})
    tag = D / 'minecraft/tags/block/mineable/pickaxe.json'
    current = json.loads(tag.read_text())
    current['values'] = list(dict.fromkeys(current['values'] + [NS + BLOCK]))
    write(tag, current)


def tank(scene, first, width):
    """A finished container tank lying east from {@code first}, its block entities naming the controller at the first end's corner."""
    x0, y0, z0 = first
    length = LENGTHS[width]
    for x in range(length):
        for y in range(width):
            for z in range(width):
                at = (x0 + x, y0 + y, z0 + z)
                nbt = [string('id', NS + BLOCK), named(11, 'LastKnownPos', b''.join(integer(c) for c in (3, *at)))]
                nbt += [named(3, 'Size', integer(width)), named(3, 'Height', integer(length))] if at == first else \
                    [named(11, 'Controller', b''.join(integer(c) for c in (3, *first)))]
                scene.block(*at, NS + BLOCK, nbt=nbt, axis='x', size=str(width), cell=str(width - 1 - z + width * y), bottom=str(x == 0).lower(),
                            top=str(x == length - 1).lower(), shape='plain')


def lesson():
    scene = Structure(11, 5)
    tank(scene, (2, 1, 1), 1)
    tank(scene, (2, 1, 3), 2)
    tank(scene, (2, 1, 6), 3)
    scene.save(BLOCK)
    (A / f'ponder/plant/{BLOCK}.nbt').write_bytes((OUT / f'{BLOCK}.nbt').read_bytes())
    (OUT / f'{BLOCK}.nbt').unlink()
    steps = [
        ({'target': [2, 1, 3], 'reveal': [2, 1, 1, 8, 3, 8], 'item': NS + BLOCK},
         ('Gas tank containers lie along the way you look. Loose blocks, or a run of any other size, stay single blocks that take no gas.',
          'ガスタンクコンテナは見ている方向に沿って寝かせて置かれます。並べただけのブロックや、決まった大きさでない並びは、1個ずつのままでガスを受け付けません。',
          '气体罐式集装箱沿视线方向横放。单独的方块或尺寸不对的排列保持为单个方块，不接收气体。')),
        ({'target': [8, 2, 7]},
         ('Only the container sizes form a tank: 1 × 1 two long, 2 × 2 five long, 3 × 3 seven long. Then the frames join and the blocks become one gas tank.',
          '決まった大きさだけがタンクになります：1×1は長さ2、2×2は長さ5、3×3は長さ7。そろうとフレームがつながり、1つのガスタンクになります。',
          '只有规定尺寸才能成罐：1×1长2格、2×2长5格、3×3长7格。凑齐后框架连成一体，成为一个气罐。')),
        ({'target': [3, 1, 1]},
         ('It holds gases only and is rated at 50 bar, like the gas receiver. Pipes connect to any face; goggles show what a run still needs.',
          'ガスだけを入れられ、定格はガスレシーバーと同じ50 barです。配管はどの面にもつながります。ゴーグルで未完成の並びに足りない長さを確認できます。',
          '只能装气体，额定压力与气体储罐相同为50 bar。管道可接任意面；护目镜可查看未完成排列还缺多长。')),
    ]
    return {'id': BLOCK, 'size': 11, 'height': 5, 'scale': .55, 'formed': 'step_1', 'components': [BLOCK],
            'steps': [{'key': f'step_{i}', **step} for i, (step, _) in enumerate(steps)]}, steps


def tutorials():
    path = A / 'ponder/tutorials.json'
    catalog = json.loads(path.read_text())
    entry, steps = lesson()
    rebuilt = [e for e in catalog if e['id'] != BLOCK]
    rebuilt.insert(next(i for i, e in enumerate(rebuilt) if e['id'] == 'tank_materials') + 1, entry)
    write(path, rebuilt)
    for index, language in enumerate(('en_us', 'ja_jp', 'zh_cn')):
        lang = A / 'lang' / f'{language}.json'
        strings = json.loads(lang.read_text())
        strings[f'block.{NS[:-1]}.{BLOCK}'] = NAMES[index]
        strings[f'goggles.{NS[:-1]}.{BLOCK}.incomplete'] = INCOMPLETE[index]
        strings[SHARED + f'guide.{BLOCK}.title'] = NAMES[index]
        for i, (_, text) in enumerate(steps): strings[SHARED + f'guide.{BLOCK}.step_{i}'] = text[index]
        write(lang, strings)
    ponder_titles()


if __name__ == '__main__':
    textures()
    assets()
    data()
    tutorials()
    print('Generated the gas tank container: textures, models for each size and place, block state, loot, names and its lesson')
