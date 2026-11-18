#!/usr/bin/env python3
"""Assets, recipes and the teaching structure for the externally heated steam gasifier."""
import json
from pathlib import Path
from PIL import Image, ImageColor, ImageDraw
import generate_petrochemical_ponder as ponder

ROOT = Path(__file__).resolve().parents[1]
NS = 'create_refinery_n_reactors:'
A = ROOT / ('src/main/resources/assets/' + NS[:-1])
D = ROOT / ('src/main/resources/data/' + NS[:-1])
PARTS = ('gasifier_casing', 'water_cooled_refractory_wall', 'entrained_burner_nozzle', 'slag_tap_hearth', 'quench_sight_glass')


def read(path):
    return json.loads(path.read_text())


def write(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, ensure_ascii=False, indent=2) + '\n')


def box(bounds, texture):
    return {'from': bounds[:3], 'to': bounds[3:],
            'faces': {face: {'texture': '#' + texture} for face in ponder.DIRECTIONS}}


def panel(frame, plate, middle):
    img = Image.new('RGBA', (16, 16), frame)
    pen = ImageDraw.Draw(img)
    pen.rectangle((1, 1, 14, 14), fill=plate)
    pen.rectangle((2, 3, 13, 12), fill=middle)
    for x, y in ((2, 2), (13, 2), (2, 13), (13, 13)):
        pen.point((x, y), fill=frame)
        pen.point((x, y - 1), fill=tuple(min(255, c + 32) for c in ImageColor.getrgb(plate)) + (255,))
    return img


def assets():
    texture = A / 'textures/block/gasifier'
    texture.mkdir(parents=True, exist_ok=True)
    steel = panel('#363b3b', '#a1a4a0', '#777f7b')
    steel.save(texture / 'casing.png')
    wall = panel('#433c35', '#98928a', '#676259')
    pen = ImageDraw.Draw(wall)
    for x in (5, 10):
        pen.rectangle((x - 1, 3, x + 1, 12), fill='#526668')
        pen.line((x - 1, 4, x - 1, 11), fill='#859796')
    wall.save(texture / 'wall.png')
    window = panel('#343c39', '#848c87', '#bfd9d970')
    ImageDraw.Draw(window).line((3, 9, 8, 4), fill='#eaf9f8ad')
    window.save(texture / 'window.png')
    nozzle = panel('#423027', '#ad8968', '#856a4c')
    pen = ImageDraw.Draw(nozzle)
    pen.rectangle((5, 5, 10, 10), fill='#38342d')
    pen.rectangle((6, 6, 9, 9), fill='#231f1b')
    nozzle.save(texture / 'nozzle.png')
    panel('#5c4736', '#a48a66', '#897458').save(texture / 'ceramic.png')
    models = {
        PARTS[0]: [box([0, 0, 0, 16, 16, 16], 'casing')],
        PARTS[1]: [box([0, 0, 0, 16, 16, 16], 'wall')],
        PARTS[2]: [box([0, 0, 3, 16, 16, 16], 'casing'), box([3, 3, 0, 13, 13, 3], 'nozzle')],
        PARTS[3]: [box([0, 12, 0, 16, 16, 16], 'ceramic'), box([2, 8, 2, 14, 12, 14], 'ceramic'),
                   box([4, 4, 4, 12, 8, 12], 'ceramic'), box([6, 0, 6, 10, 4, 10], 'nozzle')],
        PARTS[4]: [box([0, 0, 0, 16, 16, 16], 'window')]
    }
    # The outside nozzle flange faces north in the unrotated model.
    models[PARTS[2]][-1]['faces']['north']['uv'] = [2, 2, 14, 14]
    for name, elements in models.items():
        tex = {face['texture'][1:] for element in elements for face in element['faces'].values()}
        model = {'parent': 'minecraft:block/block', 'textures': {key: NS + 'block/gasifier/' + key for key in sorted(tex)},
                 'elements': elements}
        model['textures']['particle'] = NS + 'block/gasifier/' + sorted(tex)[0]
        if name == PARTS[4]:
            model['render_type'] = 'minecraft:translucent'
        write(A / f'models/block/gasifier/{name}.json', model)
        target = NS + 'block/gasifier/' + name
        write(A / f'blockstates/{name}.json', {'variants': {
            'facing=' + face: {'model': target, **({'y': angle} if angle and name == PARTS[2] else {})}
            for face, angle in zip(('north', 'east', 'south', 'west'), (0, 90, 180, 270))
        }})
        write(A / f'models/item/{name}.json', {'parent': target})
        write(D / f'loot_table/blocks/{name}.json', {'type': 'minecraft:block', 'pools': [
            {'rolls': 1, 'conditions': [{'condition': 'minecraft:survives_explosion'}],
             'entries': [{'type': 'minecraft:item', 'name': NS + name}]}]})
    img = Image.new('RGBA', (16, 16))
    pen = ImageDraw.Draw(img)
    for polygon, shade in [([(2, 11), (5, 7), (8, 6), (11, 8), (14, 11), (13, 13), (3, 13)], '#343b3c'),
                           ([(4, 10), (6, 7), (8, 7), (10, 10), (9, 12), (4, 12)], '#677376'),
                           ([(9, 11), (11, 9), (13, 11), (12, 12), (10, 12)], '#849093')]:
        pen.polygon(polygon, fill=shade)
    pen.point((6, 8), fill='#a8b9b7')
    pen.point((11, 10), fill='#b2c1be')
    img.save(A / 'textures/item/glassy_slag.png')
    write(A / 'models/item/glassy_slag.json', {'parent': 'minecraft:item/generated', 'textures': {'layer0': NS + 'item/glassy_slag'}})
    for tag in ('mineable/pickaxe', 'needs_iron_tool'):
        path = ROOT / f'src/main/resources/data/minecraft/tags/block/{tag}.json'
        data = read(path)
        data['values'] = list(dict.fromkeys(data['values'] + [NS + name for name in PARTS]))
        write(path, data)


def recipes():
    casing, wall, nozzle, hearth, window = PARTS
    steel = NS + 'stainless_steel_sheet'
    def craft(output, pattern, ingredients, count=1):
        write(D / f'recipe/{output}.json', {'type': 'minecraft:crafting_shaped', 'pattern': pattern,
            'key': {key: {'item': item} for key, item in ingredients.items()}, 'result': {'id': NS + output, 'count': count}})
    craft(casing, ['SSS', 'BBB', 'SSS'], {'S': steel, 'B': 'minecraft:bricks'}, 6)
    craft(wall, [' S ', 'BCB', ' P '], {'S': steel, 'B': 'minecraft:nether_bricks', 'C': NS + casing, 'P': NS + 'stainless_steel_pipe'}, 4)
    craft(nozzle, [' B ', 'PCP', ' S '], {'B': 'create:brass_sheet', 'C': NS + casing, 'P': NS + 'stainless_steel_pipe', 'S': steel})
    craft(hearth, ['B B', 'BCB', ' S '], {'B': 'minecraft:nether_bricks', 'C': NS + casing, 'S': steel})
    craft(window, [' S ', 'GCG', ' S '], {'S': steel, 'G': 'minecraft:glass', 'C': NS + casing}, 2)
    write(D / 'tags/fluid/gasifier_steam.json', {'values': [NS + name for name in ('steam', 'high_pressure_steam', 'superheated_steam')]})
    write(D / 'recipe/entrained_gasification/carbon_steam.json', {
        'type': NS + 'entrained_gasification', 'ingredient': {'item': NS + 'carbon_powder'},
        'steam': {'tag': NS + 'gasifier_steam', 'amount': 250},
        'duration': 40, 'gas_result': {'fluid': NS + 'water_gas', 'amount': 500}})
    write(D / 'recipe/crushing/glassy_slag.json', {'type': 'create:crushing', 'processing_time': 150,
        'ingredients': [{'item': NS + 'glassy_slag'}], 'results': [{'id': 'minecraft:sand'}, {'id': 'minecraft:gravel', 'chance': .25}]})


def lesson():
    scene = ponder.Structure(13, 10)
    for y in range(6):
        for x in range(-1, 2):
            for z in range(-1, 2):
                center = x == z == 0
                if center and y in (1, 3, 4):
                    continue
                role, facing = PARTS[0], 'north'
                if center and y == 2:
                    role = PARTS[3]
                elif y in (3, 4):
                    role = PARTS[1]
                if y == 3 and x == 0 and z in (-1, 1):
                    role, facing = PARTS[2], 'north' if z < 0 else 'south'
                if y in (1, 4) and abs(x) + abs(z) == 1:
                    role = PARTS[4]
                scene.block(6+x, 2+y, 6+z, NS+role, facing=facing)
    scene.block(6, 1, 6, 'create:blaze_burner', blaze='kindled', facing='north')
    scene.block(6, 1, 4, NS + 'stainless_steel_pump', facing='south')
    scene.block(7, 1, 4, 'create:cogwheel', axis='z')
    scene.block(7, 1, 3, 'create:creative_motor', facing='south')
    scene.block(6, 5, 8, 'create:andesite_funnel', facing='south', extracting='false', powered='false')
    pipe = NS + 'stainless_steel_pipe'
    for x, y, z, axis in ((6,1,5,'z'), (6,5,4,'z'), (6,8,6,'y')):
        directions = {'x': ('east', 'west'), 'z': ('north', 'south'), 'y': ('up', 'down')}[axis]
        scene.block(x,y,z,pipe,waterlogged='false', **{face: str(face in directions).lower() for face in ponder.DIRECTIONS})
    scene.block(6, 9, 6, NS + 'stainless_steel_pump', facing='up')
    scene.block(7, 9, 6, 'create:cogwheel', axis='y')
    scene.block(7, 8, 6, 'create:creative_motor', facing='up')
    ponder.OUT = A / 'ponder/plant'
    scene.save('entrained_gasifier')


if __name__ == '__main__':
    assets()
    recipes()
    lesson()
