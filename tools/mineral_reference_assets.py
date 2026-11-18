#!/usr/bin/env python3
"""Import the user's exact sprite sheets without redrawing or interpolating their pixels."""
from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / 'tools/data/minerals'
TEXTURES = ROOT / 'src/main/resources/assets/create_refinery_n_reactors/textures'
ORES = ('bauxite', 'pentlandite', 'ilmenite', 'zircon', 'chromite', 'molybdenite', 'uranium', 'halite')
ITEMS = ('uraninite', 'bauxite', 'pentlandite', 'ilmenite', 'zircon', 'chromite', 'molybdenite', 'fluorite')


def extract(sheet, x, y, size, background, output):
    output.parent.mkdir(parents=True, exist_ok=True)
    subprocess.run(['convert', str(SOURCE / sheet), '-crop', f'{size}x{size}+{x}+{y}',
                    '+repage', '-sample', '16x16!', '-transparent', background, '-strip',
                    '-define', 'png:color-type=6', str(output)], check=True)


def textures(names=None):
    selected = set(names) if names is not None else set(ORES) | set(ITEMS)
    for index, ore in enumerate(ORES):
        if ore not in selected:
            continue
        for prefix, row, background in (('', 0, '#7f7f7f'), ('deepslate_', 2, '#4d4d50')):
            extract('ore-sheet.png', index % 4 * 248, (index // 4 + row) * 248, 240, background,
                    TEXTURES / f'block/{prefix}{ore}_ore_overlay.png')
    for index, item in enumerate(ITEMS):
        if item in selected:
            extract('mineral-sheet.png', index % 4 * 264, index // 4 * 264, 256, '#8b8b8b',
                    TEXTURES / f'item/{item}.png')


if __name__ == '__main__':
    textures()
    print('Imported 16 ore overlays and 8 mineral sprites from the supplied sheets')
