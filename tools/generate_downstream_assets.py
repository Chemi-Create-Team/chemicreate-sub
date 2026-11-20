#!/usr/bin/env python3
"""Native Minecraft pixel art and the crop-spraying Ponder fixture."""
from PIL import Image, ImageDraw
import generate_petrochemical_ponder as ponder
from generate_fertilizer_assets import TEXTURES, pile
from generate_synthesis_assets import cartridge
from generate_world_liquids import bucket_image, fluid_entries


FLUIDS = {
    'benzene', 'toluene', 'xylene', 'ethylbenzene', 'styrene', 'butadiene',
    'cumene', 'cumene_process_liquor', 'phenol', 'acetone', 'formaldehyde_solution',
    'chlorobenzene', 'chlorotoluene', 'hexane', 'recovered_solvent', 'liquid_fertilizer',
}


def board():
    image = Image.new('RGBA', (16, 16))
    draw = ImageDraw.Draw(image)
    draw.polygon([(2, 4), (11, 2), (14, 5), (14, 11), (5, 14), (2, 11)], '#64452d')
    draw.polygon([(3, 4), (11, 3), (13, 5), (13, 10), (5, 12), (3, 10)], '#a97449')
    draw.line([(4, 5), (10, 4), (12, 6)], '#d2a373', 1)
    draw.line([(5, 11), (5, 8), (9, 7), (9, 5)], '#d8ad66', 1)
    draw.line([(8, 10), (11, 9), (11, 7)], '#74492e', 1)
    image.save(TEXTURES / 'phenolic_board.png')


def wire(name, spool):
    image = Image.new('RGBA', (16, 16))
    draw = ImageDraw.Draw(image)
    if spool:
        draw.ellipse((2, 2, 13, 13), '#292c2d')
        draw.ellipse((3, 3, 12, 12), '#76726b')
        draw.ellipse((4, 4, 11, 11), '#3e3431')
        draw.arc((4, 4, 11, 11), 160, 380, '#c7a078', 2)
        draw.rectangle((7, 7, 8, 8), '#a69d86')
    else:
        draw.line([(3, 7), (6, 4), (11, 4), (13, 6), (13, 9), (11, 11), (4, 11)], '#24272b', 3)
        draw.line([(3, 7), (6, 4), (11, 4), (13, 6), (13, 9), (11, 11), (4, 11)], '#b8835e', 1)
        draw.point((3, 7), '#e4bb74')
    image.save(TEXTURES / (name + '.png'))


def polymer_block():
    image = Image.new('RGB', (16, 16), '#a4aaa4')
    draw = ImageDraw.Draw(image)
    draw.rectangle((0, 0, 15, 15), outline='#747f7e')
    draw.rectangle((1, 1, 14, 14), outline='#b9c4bc')
    draw.rectangle((2, 2, 13, 13), outline='#98a5a1')
    draw.line((3, 12, 12, 12), '#879594')
    for x, y in [(2, 2), (13, 2), (2, 13), (13, 13)]:
        draw.point((x, y), '#737d78')
    image.save(TEXTURES.parent / 'block/molded_polymer.png')


def sprayer_scene():
    ponder.OUT = TEXTURES.parents[1] / 'ponder/plant'
    scene = ponder.Structure(9, 5)
    scene.block(1, 1, 4, 'minecraft:water', level='0')
    for x in range(2, 5):
        for z in range(3, 6):
            scene.block(x, 1, z, 'minecraft:farmland', moisture='7')
            scene.block(x, 2, z, 'minecraft:wheat', age='0')
    scene.block(6, 1, 4, 'create:depot')
    scene.block(6, 3, 4, 'create:spout')
    scene.pipe(6, 4, 4, 'down', 'up')
    scene.save('liquid_fertilizer')


if __name__ == '__main__':
    for fluid in fluid_entries():
        if fluid['name'] in FLUIDS:
            bucket_image(fluid['name'] + '_bucket', fluid['color'], fluid['corrosive'])
    cartridge('cumene_catalyst_core', '#895f43', '#c7a575', '#8e9e82')
    cartridge('platinum_zeolite_core', '#769299', '#c5d8d4', '#aaa791')
    pile('phenolic_resin', [(2, 12), (5, 8), (8, 6), (11, 8), (14, 12), (9, 14), (4, 13)],
         ((74, 45, 36), (127, 75, 52), (102, 61, 44), (162, 101, 67)), 703)
    pile('phosphate_compound', [(2, 11), (6, 7), (8, 6), (12, 8), (14, 12), (11, 13), (3, 13)],
         ((89, 93, 76), (148, 150, 112), (110, 117, 86), (184, 182, 135)), 31)
    pile('heat_resistant_coating', [(2, 12), (4, 9), (8, 6), (11, 7), (14, 11), (12, 13), (3, 14)],
         ((89, 61, 49), (161, 117, 87), (139, 94, 66), (190, 145, 108)), 123)
    board()
    wire('fire_resistant_conductor', False)
    wire('fire_resistant_cable', True)
    polymer_block()
    sprayer_scene()
