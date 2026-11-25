#!/usr/bin/env python3
"""Large flywheel textures: the default is Create's own flywheel texture; each dye colour recolours only its brass and keeps the grey and dark parts.

Run from the repository root with Create's jar on the command line:
    python3 tools/generate_large_flywheel_textures.py path/to/create.jar
"""
import colorsys
import io
import sys
import zipfile
from pathlib import Path
from PIL import Image

TEXTURES = Path(__file__).resolve().parents[1] / 'src/main/resources/assets/create_refinery_n_reactors/textures/block'
# Each dye's hue and mean saturation, measured from the textures the flywheel had before it took Create's look; the default stands for yellow.
DYES = {
    'white': (.458, .017), 'orange': (.075, .658), 'magenta': (.847, .427), 'light_blue': (.54, .527), 'lime': (.237, .591),
    'pink': (.95, .315), 'gray': (.544, .063), 'light_gray': (.167, .026), 'cyan': (.5, .556), 'purple': (.775, .498),
    'blue': (.654, .432), 'brown': (.07, .375), 'green': (.216, .487), 'red': (.01, .529), 'black': (.667, .036),
}
YELLOW_SATURATION = .567
# How bright each dye's brass is compared with the yellow one, measured the same way; the dark dyes also darken the grey housing.
BRIGHTNESS = {'white': 1, 'orange': .99, 'magenta': .84, 'light_blue': .9, 'lime': .84, 'pink': .97, 'gray': .5, 'light_gray': .72,
              'cyan': .72, 'purple': .8, 'blue': .76, 'brown': .64, 'green': .62, 'red': .78, 'black': .35}


def recolour(source, hue, saturation, brightness):
    scale = saturation / YELLOW_SATURATION
    result = source.copy()
    pixels = result.load()
    for y in range(result.height):
        for x in range(result.width):
            r, g, b, a = pixels[x, y]
            h, s, v = colorsys.rgb_to_hsv(r / 255, g / 255, b / 255)
            if a == 0:
                continue
            if s < .25:  # the grey housing and the dark base keep their colour, except in the dark dyes
                if brightness < .6:
                    pixels[x, y] = tuple(round(c * 255) for c in colorsys.hsv_to_rgb(h, s, v * brightness ** .5)) + (a,)
                continue
            s = min(1, s * scale)
            pixels[x, y] = tuple(round(c * 255) for c in colorsys.hsv_to_rgb(hue, s, min(1, v * brightness))) + (a,)
    return result


if __name__ == '__main__':
    create = Image.open(io.BytesIO(zipfile.ZipFile(sys.argv[1]).read('assets/create/textures/block/flywheel.png'))).convert('RGBA')
    create.save(TEXTURES / 'large_flywheel.png')
    for name, (hue, saturation) in DYES.items():
        recolour(create, hue, saturation, BRIGHTNESS[name]).save(TEXTURES / f'{name}_large_flywheel.png')
    print('Wrote the default and', len(DYES), 'dyed large flywheel textures')
