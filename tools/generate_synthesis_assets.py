#!/usr/bin/env python3
"""16-pixel catalyst cartridges and the synthetic-process containers."""
from PIL import Image, ImageDraw
from generate_fertilizer_assets import TEXTURES, bucket


def cartridge(name, bed, highlight, rim):
    image = Image.new('RGBA', (16, 16))
    draw = ImageDraw.Draw(image)
    draw.polygon([(4, 2), (10, 2), (13, 5), (13, 12), (10, 14), (4, 14), (2, 12), (2, 4)], '#292d2f')
    draw.polygon([(4, 3), (10, 3), (12, 5), (12, 11), (10, 13), (4, 13), (3, 11), (3, 5)], '#616b68')
    draw.rectangle((4, 5, 11, 10), fill=bed)
    draw.line([(5, 5), (10, 5)], fill=highlight)
    for x, y in [(5, 7), (8, 6), (10, 8), (6, 9), (9, 10)]:
        draw.point((x, y), fill=highlight)
    draw.rectangle((4, 3, 10, 4), fill=rim)
    draw.rectangle((4, 11, 10, 12), fill=rim)
    draw.line([(4, 13), (10, 13)], fill='#383e40')
    draw.line([(3, 5), (3, 10)], fill='#959a8e')
    image.save(TEXTURES / (name + '.png'))


def wax():
    image = Image.new('RGBA', (16, 16))
    draw = ImageDraw.Draw(image)
    draw.polygon([(2, 8), (8, 4), (13, 6), (14, 10), (8, 14), (2, 12)], '#756954')
    draw.polygon([(3, 8), (8, 5), (12, 6), (13, 9), (8, 12), (3, 11)], '#d8c699')
    draw.polygon([(3, 8), (8, 5), (12, 6), (7, 9)], '#eee0b5')
    draw.polygon([(7, 9), (12, 6), (13, 9), (8, 12)], '#b9a475')
    draw.line([(4, 9), (6, 9)], fill='#f6e7c4')
    image.save(TEXTURES / 'paraffin_wax.png')


if __name__ == '__main__':
    cartridge('shift_catalyst_core', '#636e75', '#bac4c8', '#b89d63')
    cartridge('iron_ft_catalyst_core', '#744b3f', '#c3835d', '#aca38d')
    cartridge('cobalt_ft_catalyst_core', '#385c79', '#78afc8', '#c49450')
    cartridge('zsm5_catalyst_core', '#725176', '#c3a4c6', '#b9b18a')
    wax()
    bucket('purified_syngas', '#86b0b2', '#c2dbd9')
    bucket('pressurised_carbon_monoxide', '#78878f', '#b2bec4')
    bucket('synthetic_crude_oil', '#524333', '#9e8364')
    bucket('ft_offgas', '#9aa673', '#cdd3a8')
    bucket('synthetic_naphtha', '#cfb173', '#f0d694')
    bucket('molten_paraffin', '#cabc9b', '#e6d9b1')
    bucket('btx', '#bc8048', '#e0a66c')
