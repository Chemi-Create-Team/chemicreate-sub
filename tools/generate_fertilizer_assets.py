#!/usr/bin/env python3
"""Pixel-art material piles and the two tinted process-fluid buckets."""
from pathlib import Path
from random import Random

from PIL import Image, ImageDraw
import generate_petrochemical_ponder as ponder


TEXTURES = Path(__file__).resolve().parents[1] / 'src/main/resources/assets/create_refinery_n_reactors/textures/item'


def pile(name, points, palette, seed):
    sprite = Image.new('RGBA', (16, 16))
    draw = ImageDraw.Draw(sprite)
    shadow, base, grain, shine = palette
    draw.polygon([(x, min(y + 1, 15)) for x, y in points], shadow)
    draw.polygon(points, base)
    random = Random(seed)
    for y in range(4, 15):
        for x in range(2, 15):
            if sprite.getpixel((x, y))[:3] == base and random.random() < .32:
                draw.point((x, y), shine if random.randrange(3) == 0 else grain)
    sprite.save(TEXTURES / (name + '.png'))


def bucket(name, pool, highlight):
    sprite = Image.new('RGBA', (16, 16))
    draw = ImageDraw.Draw(sprite)
    draw.polygon([(2, 5), (5, 3), (11, 3), (13, 5), (12, 12), (10, 14),
                  (5, 14), (3, 12)], '#343b3d')
    draw.polygon([(3, 6), (12, 6), (11, 12), (10, 13), (5, 13), (4, 11)], '#7d898a')
    draw.line([(5, 7), (6, 12), (9, 12)], '#b1b8b6')
    draw.line([(11, 7), (10, 12)], '#565f62')
    draw.polygon([(3, 5), (5, 4), (11, 4), (12, 5), (10, 6), (5, 6)], pool)
    draw.line([(5, 4), (10, 4)], highlight)
    draw.line([(2, 6), (3, 9), (5, 10), (8, 10), (10, 8)], '#c2c8c2')
    sprite.save(TEXTURES / (name + '_bucket.png'))


def fertilizer_scene():
    ponder.OUT = TEXTURES.parents[1] / 'ponder/plant'
    scene = ponder.Structure(7, 3)
    scene.block(0, 1, 4, 'minecraft:water', level='0')
    scene.block(5, 1, 4, 'create:depot')
    for x in range(1, 4):
        for z in range(3, 6):
            scene.block(x, 1, z, 'minecraft:farmland', moisture='7')
            scene.block(x, 2, z, 'minecraft:wheat', age='0')
    scene.save('fertilizer_cycle')


if __name__ == '__main__':
    pile('quicklime', [(2, 11), (4, 8), (7, 7), (9, 5), (12, 7), (14, 12), (11, 13), (5, 13)],
         ((89, 81, 71), (168, 159, 133), (136, 128, 106), (211, 204, 179)), 13)
    pile('sodium_bicarbonate', [(2, 12), (5, 9), (7, 8), (9, 8), (11, 10), (13, 12), (11, 13), (4, 13)],
         ((91, 99, 95), (189, 198, 182), (155, 171, 162), (229, 232, 211)), 21)
    pile('sodium_carbonate', [(2, 12), (4, 9), (6, 9), (8, 6), (10, 7), (12, 11), (14, 12), (11, 13), (5, 13)],
         ((87, 96, 110), (179, 195, 202), (143, 165, 177), (218, 231, 226)), 36)
    pile('urea', [(2, 12), (4, 10), (6, 8), (8, 7), (11, 9), (14, 12), (12, 13), (4, 13)],
         ((103, 94, 112), (194, 181, 207), (153, 143, 173), (224, 220, 231)), 41)
    pile('potash', [(2, 11), (4, 9), (7, 7), (9, 7), (12, 10), (14, 12), (11, 13), (4, 13)],
         ((86, 62, 56), (159, 119, 99), (122, 88, 76), (202, 161, 130)), 15)
    pile('phosphate_fertilizer', [(2, 12), (4, 10), (6, 6), (9, 7), (10, 9), (13, 10), (14, 12), (10, 13), (3, 13)],
         ((87, 75, 54), (178, 153, 110), (143, 117, 82), (215, 192, 141)), 10)
    pile('npk_fertilizer', [(2, 12), (4, 8), (7, 6), (10, 7), (13, 10), (14, 13), (10, 14), (4, 14)],
         ((66, 76, 61), (122, 140, 106), (195, 163, 116), (201, 196, 177)), 6)
    bucket('methanol', '#9cc4ca', '#d7e8e8')
    bucket('ammonium_chloride_liquor', '#b9b084', '#d9d0ac')
    fertilizer_scene()
