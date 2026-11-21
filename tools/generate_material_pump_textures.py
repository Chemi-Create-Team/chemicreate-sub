#!/usr/bin/env python3
"""Each material pump's flange texture: Create's pump flange, with the arrow that shows the flow direction, recoloured to the pipe material.
Run from the repository root after resolving the Create dependency."""
import io
import os
from pathlib import Path
from zipfile import ZipFile
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
A = ROOT / 'src/main/resources/assets/create_refinery_n_reactors'
TEXTURES = A / 'textures/block'
# The pipe texture each pump takes its colours from.
SOURCES = {
    'nickel_alloy': 'transport/nickel_alloy_pipe', 'zirconium': 'transport/zirconium_pipe',
    'stainless_steel': 'transport/stainless_steel_pipes',
}


def luminance(pixel):
    return .299 * pixel[0] + .587 * pixel[1] + .114 * pixel[2]


def ramp(image):
    """The material's colours from dark to light."""
    colours = sorted((p for p in image.convert("RGBA").get_flattened_data() if p[3] == 255), key=luminance)
    return [colours[min(len(colours) - 1, int(len(colours) * q))] for q in (.04, .2, .4, .6, .8, .97)]


def main():
    cache = Path(os.environ.get('GRADLE_USER_HOME', Path.home() / '.gradle'))
    jar = next((cache / 'caches/modules-2/files-2.1/maven.modrinth/create/UjX6dr61').rglob('*.jar'))
    with ZipFile(jar) as archive:
        pump = Image.open(io.BytesIO(archive.read('assets/create/textures/block/pump.png'))).convert('RGBA')
    # Create's pump model reads only the flange face and its edge, where the arrow is drawn.
    region = (0, 0, 16, 12)
    source = [pump.getpixel((x, y)) for y in range(region[1], region[3]) for x in range(region[0], region[2])]
    lit = [luminance(p) for p in source if p[3] == 255]
    low, high = min(lit), max(lit)
    for material, path in SOURCES.items():
        colours = ramp(Image.open(TEXTURES / f'{path}.png'))
        out = Image.new('RGBA', pump.size, (0, 0, 0, 0))
        for y in range(region[1], region[3]):
            for x in range(region[0], region[2]):
                pixel = pump.getpixel((x, y))
                if pixel[3] == 0: continue
                step = (luminance(pixel) - low) / (high - low) * (len(colours) - 1)
                out.putpixel((x, y), colours[round(step)][:3] + (pixel[3],))
        target = TEXTURES / f'material_pump/{material}_pump.png'
        target.parent.mkdir(parents=True, exist_ok=True)
        out.save(target)


if __name__ == '__main__':
    main()
