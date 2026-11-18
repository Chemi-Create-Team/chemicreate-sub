#!/usr/bin/env python3
"""Stainless steel ingot and sheet textures (the shapes of this mod's aluminium ingot and plastic sheet, in a darker neutral grey with brushed
lines), and the zircaloy fuel cladding tube."""
from pathlib import Path
from PIL import Image

T = Path(__file__).resolve().parents[1] / 'src/main/resources/assets/create_refinery_n_reactors/textures/item'


def steel(value, y):
    """Map a shape's brightness (0..255) onto brushed stainless: neutral grey, a faint cool cast, and alternate rows slightly darker."""
    v = 40 + value * 0.58 - (8 if y % 2 else 0)
    return tuple(max(0, min(255, round(c))) for c in (v, v + 3, v + 9)) + (255,)


def recolour(source, target, outline):
    im = Image.open(T / source).convert('RGBA')
    out = Image.new('RGBA', im.size)
    for y in range(im.height):
        for x in range(im.width):
            r, g, b, a = im.getpixel((x, y))
            if a == 0:
                continue
            value = (r * 299 + g * 587 + b * 114) / 1000
            out.putpixel((x, y), outline if value < 60 else steel(value, y))
    out.save(T / target)


recolour('aluminum_ingot.png', 'stainless_steel_ingot.png', (52, 55, 60, 255))
recolour('plastic_sheet.png', 'stainless_steel_sheet.png', (38, 41, 46, 255))


def cladding():
    """Zircaloy fuel cladding tube: a thin diagonal tube in the grey of the zirconium ingot, open at the upper end."""
    ingot = Image.open(T / 'zirconium_ingot.png').convert('RGBA')
    shades = sorted({p[:3] for p in ingot.getdata() if p[3]}, key=lambda c: c[0] * 299 + c[1] * 587 + c[2] * 114)
    dark, mid, light = shades[len(shades) // 6], shades[len(shades) // 2], shades[-len(shades) // 8]
    out = Image.new('RGBA', (16, 16))
    for i in range(2, 14):
        x, y = i, 15 - i
        for d, colour in ((-1, light), (0, mid), (1, dark)):
            out.putpixel((x + d if d < 0 else x, y + d if d > 0 else y), colour + (255,))
    out.putpixel((13, 2), (40, 44, 48, 255))
    out.save(T / 'zircaloy_cladding_tube.png')


cladding()
