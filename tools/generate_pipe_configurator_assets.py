#!/usr/bin/env python3
"""Draw the 16-pixel hand tool and its four-pipe Ponder layout."""
from pathlib import Path

from PIL import Image, ImageDraw

import generate_petrochemical_ponder as ponder


ASSETS = Path(__file__).resolve().parents[1] / 'src/main/resources/assets/create_refinery_n_reactors'


def draw_item():
    image = Image.new('RGBA', (16, 16))
    draw = ImageDraw.Draw(image)
    # Forged jaws, adjustable screw, and a riveted dark grip.
    draw.polygon([(0, 13), (6, 7), (6, 5), (10, 1), (13, 1), (13, 3), (9, 6),
                  (10, 8), (12, 8), (14, 6), (15, 7), (15, 10), (12, 12),
                  (8, 12), (4, 15), (1, 15)], '#322923')
    draw.polygon([(1, 13), (7, 7), (9, 9), (3, 14), (1, 14)], '#64727a')
    draw.line([(2, 13), (7, 8)], '#9aa8ae')
    draw.line([(3, 14), (7, 11)], '#3f494d')
    draw.polygon([(7, 5), (10, 2), (12, 2), (9, 4), (8, 6), (9, 8), (7, 8)], '#b28a42')
    draw.line([(8, 5), (11, 2), (12, 2)], '#ebca78')
    draw.polygon([(8, 9), (10, 10), (12, 9), (14, 7), (14, 10), (12, 11),
                  (9, 11), (8, 10)], '#ba944d')
    draw.line([(10, 10), (12, 9), (14, 7)], '#f0d78b')
    draw.line([(10, 11), (12, 11), (14, 10)], '#806132')
    draw.point((7, 9), '#d6b66b')
    draw.point((6, 10), '#374047')
    draw.point((2, 14), '#b9bcc1')
    image.save(ASSETS / 'textures/item/pipe_configurator.png')


def build_lesson():
    ponder.OUT = ASSETS / 'ponder/plant'
    scene = ponder.Structure(5, 3)
    for x, z, sides in [(1, 2, ('east', 'west')), (2, 2, ('west', 'east', 'north')),
                         (3, 2, ('east', 'west')), (2, 1, ('north', 'south'))]:
        scene.block(x, 1, z, 'create:fluid_pipe', waterlogged='false',
                    **{side: str(side in sides).lower() for side in ponder.DIRECTIONS})
    scene.save('pipe_configurator')


if __name__ == '__main__':
    draw_item()
    build_lesson()
