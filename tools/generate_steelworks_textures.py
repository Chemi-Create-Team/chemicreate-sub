#!/usr/bin/env python3
"""Paint the small refractory and steelworks sprites."""
from pathlib import Path
from random import Random

from PIL import Image, ImageDraw

ASSETS = Path(__file__).resolve().parents[1] / 'src/main/resources/assets/create_refinery_n_reactors/textures'
RNG = Random(39179)


def color(base, offset):
    return tuple(min(255, max(0, value + offset)) for value in base)


def speckle(image, box, base, variation):
    x1, y1, x2, y2 = box
    for y in range(y1, y2 + 1):
        for x in range(x1, x2 + 1):
            image.putpixel((x, y), (*color(base, RNG.randint(-variation, variation)), 255))


def save(image, name):
    path = ASSETS / (name + '.png')
    path.parent.mkdir(parents=True, exist_ok=True)
    image.save(path)


brick = Image.new('RGBA', (16, 16), (69, 56, 48, 255))
for y, bonds in ((0, (0, 8, 16)), (5, (0, 4, 12, 16)), (10, (0, 8, 16))):
    for x, right in zip(bonds, bonds[1:]):
        face = color((171, 129, 90), RNG.randint(-12, 12))
        speckle(brick, (x, y, right - 2, y + 3), face, 12)
        speckle(brick, (x, y, right - 2, y), color(face, 28), 9)
        speckle(brick, (x, y + 3, right - 2, y + 3), color(face, -20), 6)
        if x > 0:
            speckle(brick, (x, y + 1, x, y + 2), color(face, 8), 9)
speckle(brick, (0, 14, 15, 14), (95, 78, 64), 7)
save(brick, 'block/silica_refractory_bricks')

side = Image.new('RGBA', (16, 16))
for y in range(16):
    face = color((69, 72, 74), (15 if y < 5 else 1 if y < 12 else -10))
    speckle(side, (0, y, 15, y), face, 7)
draw = ImageDraw.Draw(side)
for y, fill in ((1, '#ac9380'), (2, '#584638'), (6, '#343839'), (7, '#7f8180'),
                (12, '#303639'), (13, '#827368'), (14, '#544641')):
    draw.line((0, y, 15, y), fill=fill)
for x in (2, 13):
    for y in (3, 10, 14):
        draw.point((x, y), fill='#afb0a6')
        draw.point((x, y + 1), fill='#343637')
save(side, 'block/torpedo/side')

collar = side.copy()
draw = ImageDraw.Draw(collar)
for x in (0, 12):
    speckle(collar, (x, 0, x + 3, 15), (105, 90, 76), 7)
    draw.line((x, 0, x, 15), fill='#ad9986')
    draw.line((x + 3, 0, x + 3, 15), fill='#473b34')
    for y in (2, 8, 13):
        draw.point((x + 1, y), fill='#c3ac90')
save(collar, 'block/torpedo/collar')

end = Image.new('RGBA', (16, 16))
speckle(end, (0, 0, 15, 15), (55, 57, 58), 8)
draw = ImageDraw.Draw(end)
draw.ellipse((0, 0, 15, 15), fill='#9d7760', outline='#c6a388', width=1)
draw.ellipse((2, 2, 13, 13), fill='#4f5355', outline='#46352c', width=2)
draw.ellipse((4, 4, 11, 11), fill='#2e3233', outline='#a17c61', width=1)
draw.line((6, 5, 9, 5), fill='#ad7655')
draw.rectangle((6, 6, 9, 9), fill='#545958', outline='#77746b')
for point in ((5, 2), (10, 2), (13, 5), (13, 10), (10, 13), (5, 13), (2, 10), (2, 5)):
    draw.point(point, fill='#ddd0b0')
save(end, 'block/torpedo/end')
joint = end.copy()
draw = ImageDraw.Draw(joint)
draw.rectangle((3, 3, 12, 12), fill='#42474a')
save(joint, 'block/torpedo/joint')

slab = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
draw = ImageDraw.Draw(slab)
draw.polygon([(1, 8), (10, 4), (15, 7), (15, 10), (6, 14), (1, 11)], fill='#343839')
draw.polygon([(2, 8), (10, 5), (14, 7), (6, 11)], fill='#a59b90')
draw.polygon([(2, 9), (6, 12), (6, 13), (2, 11)], fill='#65605d')
draw.polygon([(7, 11), (14, 8), (14, 10), (7, 13)], fill='#747674')
draw.line((3, 8, 10, 5), fill='#d1b198')
draw.line((7, 11, 14, 8), fill='#af9e90')
save(slab, 'item/cast_steel_slab')

for name, liquid in (('molten_pig_iron', '#e06e30'), ('desulfurized_hot_metal', '#f48d44'), ('molten_steel', '#f5b563')):
    bucket = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    draw = ImageDraw.Draw(bucket)
    draw.polygon([(2, 3), (5, 2), (10, 2), (13, 3), (13, 7), (11, 13), (10, 14),
                  (5, 14), (4, 13), (2, 7)], fill='#333736')
    draw.polygon([(3, 4), (12, 4), (12, 7), (10, 13), (5, 13), (3, 7)], fill='#807b73')
    draw.polygon([(3, 7), (6, 8), (10, 8), (12, 7), (10, 13), (5, 13)], fill='#686864')
    draw.line((4, 7, 6, 12), fill='#b9b2a0')
    draw.polygon([(3, 4), (5, 3), (10, 3), (12, 4), (11, 6), (4, 6)], fill=liquid)
    draw.line((5, 4, 9, 4), fill='#ffe0a1')
    draw.line((4, 6, 11, 6), fill='#a99d8c')
    save(bucket, 'item/' + name + '_bucket')
