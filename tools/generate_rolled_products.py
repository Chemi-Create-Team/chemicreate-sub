#!/usr/bin/env python3
"""16 px item art and models for the rolling-mill products added in 0.4.0."""
import json
from pathlib import Path

from PIL import Image, ImageDraw

A = Path(__file__).resolve().parents[1] / 'src/main/resources/assets/create_refinery_n_reactors'


def plate(draw, y, depth, top, edge, light):
    """A plate seen from above-front: top face from (1,y) to (15,y-6), front edge depth px thick."""
    face = [(1, y), (8, y - 6), (15, y - 3), (8, y + 3)]
    draw.polygon(face, fill=top)
    draw.polygon([(1, y), (8, y + 3), (8, y + 3 + depth), (1, y + depth)], fill=edge)
    draw.polygon([(8, y + 3), (15, y - 3), (15, y - 3 + depth), (8, y + 3 + depth)], fill=edge)
    draw.line([(1, y), (8, y - 6), (15, y - 3)], fill=light)


def item(name, paint):
    image = Image.new('RGBA', (16, 16))
    paint(ImageDraw.Draw(image))
    image.save(A / f'textures/item/{name}.png')
    (A / f'models/item/{name}.json').write_text(json.dumps(
        {'parent': 'minecraft:item/generated', 'textures': {'layer0': f'create_refinery_n_reactors:item/{name}'}}, indent=2) + '\n')


def electrical(d):  # a stack of coated laminations
    for i, y in enumerate((13, 11, 9, 7)):
        plate(d, y, 1, '#7d8a99' if i % 2 else '#8c99a8', '#3d4650', '#b9c4cf')
    d.line([(4, 5), (9, 8)], fill='#5e6b78')


def clad(d):  # thick carbon-steel base with a bright stainless skin
    plate(d, 9, 4, '#5b6366', '#3a4042', '#7a8387')
    plate(d, 8, 1, '#c9d0d3', '#9aa3a6', '#f1f4f5')


def lead(d):  # dull soft sheet with a curled corner
    plate(d, 10, 1, '#6c7280', '#40444d', '#8f96a3')
    d.polygon([(11, 3), (15, 7), (13, 8), (11, 6)], fill='#8a909c')
    d.point((12, 5), fill='#4d525c')


def gold(d):  # thin crumpled foil
    plate(d, 10, 0, '#e8b734', '#9c6f12', '#fff0a0')
    for x, y in ((5, 8), (8, 6), (10, 9), (12, 7)): d.point((x, y), fill='#c48f1c')
    d.line([(3, 9), (7, 7)], fill='#ffe27a')


if __name__ == '__main__':
    item('electrical_steel_sheet', electrical)
    item('stainless_clad_plate', clad)
    item('lead_sheet', lead)
    item('gold_foil', gold)
