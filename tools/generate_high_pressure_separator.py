#!/usr/bin/env python3
"""The high-pressure gas-liquid separator: the gas tank container's frame, models and block state with a separator shell drawn over them. Writes
its textures, models, block state, loot, tags, names and Ponder lesson. Run from the repository root."""
import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import generate_gas_tank_container as tank

BLOCK = 'high_pressure_separator'
NAMES = ('High-pressure Separator', '高圧気液分離機', '高压气液分离器')
INCOMPLETE = ('Not a finished separator: %s × %s across needs exactly %s blocks in a row', '未完成：断面%s×%sは長さちょうど%s個で完成します',
              '未完成：%s×%s截面需正好排成%s格')
# A blue-grey pressure-vessel steel, so it reads as a different vessel from the bare steel gas tank.
VESSEL = {'shine': (132, 154, 178, 255), 'light': (96, 118, 144, 255), 'base': (76, 96, 120, 255), 'dark': (58, 74, 96, 255), 'weld': (30, 38, 52, 255)}
GLASS, WATER, BOLT, RING, HAZARD = (190, 222, 232, 255), (70, 150, 205, 255), (150, 160, 170, 255), (118, 126, 136, 255), (222, 176, 52, 255)


def details():
    """A level sight glass and a hazard band on the shell's sides, a bolted nozzle flange on its top, a bolted manway on each head."""
    from PIL import Image, ImageDraw
    folder = tank.A / 'textures/block' / BLOCK
    shell = Image.open(folder / 'shell.png').convert('RGBA')
    pen = ImageDraw.Draw(shell)
    x, y = tank.SHELL_SIDE
    for row in range(2, 22):
        pen.point((x + 2, y + row), HAZARD if row // 2 % 2 else VESSEL['weld'])
    pen.rectangle((x + 12, y + 4, x + 14, y + 19), fill=VESSEL['weld'])
    pen.rectangle((x + 13, y + 5, x + 13, y + 18), fill=GLASS)
    pen.rectangle((x + 13, y + 12, x + 13, y + 18), fill=WATER)
    for row in (4, 19): pen.point((x + 11, y + row), BOLT); pen.point((x + 15, y + row), BOLT)
    x, y = tank.SHELL_TOP
    pen.ellipse((x + 9, y + 9, x + 16, y + 16), fill=RING, outline=VESSEL['weld'])
    pen.ellipse((x + 11, y + 11, x + 14, y + 14), fill=VESSEL['dark'])
    for dx, dy in ((9, 12), (16, 12), (12, 9), (12, 16)): pen.point((x + dx, y + dy), BOLT)
    shell.save(folder / 'shell.png')
    head = Image.open(folder / 'head.png').convert('RGBA')
    pen = ImageDraw.Draw(head)
    pen.ellipse((19, 19, 28, 28), fill=VESSEL['base'], outline=VESSEL['weld'])
    pen.ellipse((21, 21, 26, 26), fill=RING, outline=VESSEL['dark'])
    for dx, dy in ((20, 23), (27, 23), (23, 20), (23, 27), (21, 21), (26, 21), (21, 26), (26, 26)): pen.point((dx, dy), BOLT)
    pen.point((23, 23), VESSEL['dark']); pen.point((24, 24), VESSEL['dark'])
    head.save(folder / 'head.png')


def lesson():
    scene = tank.Structure(11, 5)
    tank.tank(scene, (2, 1, 1), 1)
    tank.tank(scene, (2, 1, 3), 2)
    tank.tank(scene, (2, 1, 6), 3)
    scene.save(BLOCK)
    (tank.A / f'ponder/plant/{BLOCK}.nbt').write_bytes((tank.OUT / f'{BLOCK}.nbt').read_bytes())
    (tank.OUT / f'{BLOCK}.nbt').unlink()
    steps = [
        ({'target': [2, 1, 3], 'reveal': [2, 1, 1, 8, 3, 8], 'item': tank.NS + BLOCK},
         ('The RBMK gives out wet steam: water and steam in one stream. Only a high-pressure separator takes it. The ordinary gas-liquid separator is rated for pressurised streams, not for this one.',
          'RBMKは水と蒸気が混ざった湿り蒸気を出します。これを受け取れるのは高圧気液分離機だけです。ふつうの気液分離器は加圧の流れまでが定格で、この圧力には耐えません。',
          'RBMK排出水汽混合的湿蒸汽，只有高压气液分离器能接收。普通气液分离器只适用于加压流体，承受不了这个压力。')),
        ({'target': [8, 2, 7]},
         ('It is built like a gas tank container: 1 × 1 two long, 2 × 2 five long, 3 × 3 seven long. Anything else stays a set of single blocks that take nothing.',
          'ガスタンクコンテナと同じ作りで、1×1は長さ2、2×2は長さ5、3×3は長さ7です。それ以外の並びは1個ずつのままで、何も受け付けません。',
          '结构与气体罐式集装箱相同：1×1长2格、2×2长5格、3×3长7格。其他排列保持为单个方块，不接收任何流体。')),
        ({'target': [3, 1, 1]},
         ('Pipe the reactor outlet into a side face. The steam leaves from the top row, the water from the bottom row. Goggles show how much of each it holds.',
          '原子炉の出口を側面へつなぎます。蒸気は最上段の上面から、水は最下段の下面から出ます。ゴーグルでそれぞれの量を確認できます。',
          '把反应堆出口接到侧面。蒸汽从最上排顶面排出，水从最下排底面排出，护目镜可查看各自的存量。')),
    ]
    return {'id': BLOCK, 'size': 11, 'height': 5, 'scale': .55, 'formed': 'step_1', 'components': [BLOCK],
            'steps': [{'key': f'step_{i}', **step} for i, (step, _) in enumerate(steps)]}, steps


if __name__ == '__main__':
    tank.BLOCK, tank.NAMES, tank.INCOMPLETE, tank.STEEL, tank.lesson = BLOCK, NAMES, INCOMPLETE, VESSEL, lesson
    tank.textures()
    details()
    tank.assets()
    tank.data()
    tank.tutorials()
    print('Generated the high-pressure separator: textures, models, block state, loot, names and its lesson')
