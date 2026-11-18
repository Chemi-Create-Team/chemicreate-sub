#!/usr/bin/env python3
"""Draw the standalone diorite casing and its Create connected-texture atlas."""
import argparse
from pathlib import Path

from PIL import Image, ImageDraw
from create_casing_ct import tile_masks

TEXTURES = Path(__file__).resolve().parents[1] / 'src/main/resources/assets/create_refinery_n_reactors/textures/block'


def stone(x, y):
    # Broad mineral clusters leave a quiet pale stone field inside the alloy rim.
    colours = ((196, 195, 180), (211, 211, 194), (227, 229, 210), (239, 239, 220))
    if (x // 2, y // 2) in ((2, 2), (5, 4), (3, 6)):
        return (141, 148, 128, 255)
    tone = (x // 3 * 3 + y // 2 * 5) % 11
    return colours[0 if tone == 0 else 1 if tone in (2, 7) else 3 if tone == 5 else 2] + (255,)


def casing_tile(mask):
    image = Image.new('RGBA', (16, 16))
    for y in range(16):
        for x in range(16):
            image.putpixel((x, y), stone(x, y))
    d = ImageDraw.Draw(image)
    frame, shadow, bevel, inner = (85, 93, 78), (46, 53, 45), (159, 169, 144), (111, 124, 99)
    for side in mask['outer_frame']:
        if side == 'up':
            d.line((0, 0, 15, 0), fill=frame); d.line((0, 1, 15, 1), fill=bevel); d.line((0, 2, 15, 2), fill=shadow)
        elif side == 'left':
            d.line((0, 0, 0, 15), fill=frame); d.line((1, 0, 1, 15), fill=bevel); d.line((2, 0, 2, 15), fill=shadow)
        elif side == 'down':
            d.line((0, 13, 15, 13), fill=inner); d.line((0, 14, 15, 14), fill=frame); d.line((0, 15, 15, 15), fill=shadow)
        else:
            d.line((13, 0, 13, 15), fill=inner); d.line((14, 0, 14, 15), fill=frame); d.line((15, 0, 15, 15), fill=shadow)
    for corner, sides in {'top_left': ('up', 'left'), 'top_right': ('up', 'right'),
                          'bottom_left': ('down', 'left'), 'bottom_right': ('down', 'right')}.items():
        left, top = corner.endswith('left'), corner.startswith('top')
        x, y = (0 if left else 15), (0 if top else 15)
        dx, dy = (1 if left else -1), (1 if top else -1)
        if corner in mask['inner_corners']:
            for yy in range(3):
                for xx in range(3):
                    d.point((x + xx * dx, y + yy * dy), fill=(frame if min(xx, yy) == 0 else bevel if min(xx, yy) == 1 else inner))
        if all(side in mask['outer_frame'] for side in sides):
            # Small warm alloy rivet sits on the structural frame, not on stone.
            d.point((x + dx, y + dy), fill=(207, 173, 93))
            d.point((x + 2 * dx, y + 2 * dy), fill=(113, 84, 38))
    return image


def generated():
    single = casing_tile({'outer_frame': ['up', 'down', 'left', 'right'], 'inner_corners': []})
    sheet = Image.new('RGBA', (128, 128))
    for index, mask in enumerate(tile_masks()):
        sheet.paste(casing_tile(mask) if mask is not None else single, (index % 8 * 16, index // 8 * 16))
    yield 'diorite_casing', single
    yield 'diorite_casing_connected', sheet


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--check', action='store_true', help='verify existing casing images without writing files')
    args = parser.parse_args()
    for name, image in generated():
        path = TEXTURES / (name + '.png')
        if args.check:
            actual = Image.open(path).convert('RGBA')
            assert actual.size == image.size and actual.tobytes() == image.tobytes(), f'Outdated texture: {path}'
        else:
            path.parent.mkdir(parents=True, exist_ok=True)
            image.save(path)
    print(f'Diorite casing: standalone 16px tile and 128px connected atlas {"verified" if args.check else "generated"}.')


if __name__ == '__main__':
    main()
