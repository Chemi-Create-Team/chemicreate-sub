#!/usr/bin/env python3
"""Build the 3×3 press/depot models, original pixel art and Ponder structure."""
import json
from pathlib import Path

from PIL import Image, ImageDraw
from generate_petrochemical_ponder import NS, OUT, Structure, string

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / 'src/main/resources/assets' / NS[:-1]
MODELS = ASSETS / 'models/block/large_press'
TEXTURES = ASSETS / 'textures/block/large_press'
EDGES = {(-1, -1): 'north_west', (0, -1): 'north', (1, -1): 'north_east',
         (-1, 0): 'west', (1, 0): 'east', (-1, 1): 'south_west', (0, 1): 'south', (1, 1): 'south_east'}
FACES = ('down', 'up', 'north', 'south', 'west', 'east')


def write(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2) + '\n', encoding='utf-8')


def box(start, end, side, top=None, bottom=None, visible=FACES):
    textures = {'up': top or side, 'down': bottom or side}
    return {'from': start, 'to': end,
            'faces': {face: {'texture': '#' + textures.get(face, side), 'uv': [0, 0, 16, 16]} for face in visible}}


def model(elements, path, item=False):
    data = {'parent': 'minecraft:block/block',
            'textures': {key: NS + 'block/large_press/' + name for key, name in
                         {'particle': 'case', 'case': 'case', 'side': 'rib', 'deck': 'deck', 'bed': 'bed',
                          'ram': 'ram', 'trim': 'brass', 'tool': 'platen'}.items()},
            'elements': elements}
    if item:
        data['display'] = {'gui': {'rotation': [30, 225, 0], 'translation': [0, 1, 0], 'scale': [.72, .72, .72]}}
    write(path, data)


def build_models():
    deck = [box([0, 0, 0], [16, 11, 16], 'side', 'deck'), box([0, 11, 0], [16, 13, 16], 'trim', 'deck')]
    case = [box([0, 5, 0], [16, 16, 16], 'case')]
    saddle = [box([0, 5, 0], [16, 12, 16], 'case'),
              box([0, 12, 0], [16, 16, 5], 'case'), box([0, 12, 11], [16, 16, 16], 'case'),
              box([0, 12, 5], [5, 16, 11], 'case'), box([11, 12, 5], [16, 16, 11], 'case')]
    model([deck[0], box([0, 11, 0], [16, 13, 16], 'trim', 'bed')], MODELS / 'depot.json')
    model(saddle, MODELS / 'press.json')
    model(case, MODELS / 'frame.json')
    rim = box([0, 13, 0], [16, 13.125, 1], 'trim', visible=['up'])
    model(deck + [rim], MODELS / 'depot_edge.json')
    model(deck + [rim, box([0, 13, 1], [1, 13.125, 16], 'trim', visible=['up'])], MODELS / 'depot_corner.json')
    variants = {}
    rotations = {(-1, -1): 0, (0, -1): 0, (1, -1): 90, (1, 0): 90,
                 (1, 1): 180, (0, 1): 180, (-1, 1): 270, (-1, 0): 270}
    for (x, z), label in EDGES.items():
        deck_model = 'depot_corner' if x and z else 'depot_edge'
        variants['depot=true,edge=' + label] = {'model': NS + 'block/large_press/' + deck_model, 'y': rotations[x, z]}
        variants['depot=false,edge=' + label] = {'model': NS + 'block/large_press/frame'}
    write(ASSETS / 'blockstates/large_press_frame.json', {'variants': variants})
    write(ASSETS / 'blockstates/large_depot.json', {'variants': {'': {'model': NS + 'block/large_press/depot'}}})
    write(ASSETS / 'blockstates/large_mechanical_press.json', {'variants': {'': {'model': NS + 'block/large_press/press'}}})

    platen = []
    for x in (-1, 0, 1):
        for z in (-1, 0, 1):
            x1, x2 = x * 16 + (1 if x == -1 else 0), x * 16 + (15 if x == 1 else 16)
            z1, z2 = z * 16 + (1 if z == -1 else 0), z * 16 + (15 if z == 1 else 16)
            outer = [face for edge, face in ((x == -1, 'west'), (x == 1, 'east'), (z == -1, 'north'), (z == 1, 'south')) if edge]
            platen.append(box([x1, -2, z1], [x2, 1, z2], 'tool', visible=['up', 'down', *outer]))
    model(platen, MODELS / 'platen.json')
    model([box([5, 0, 5], [11, 16, 11], 'ram')], MODELS / 'ram.json')

    item_models = ASSETS / 'models/item'
    model([box([0, 6, 0], [16, 10, 16], 'side', 'deck'), box([0, 10, 0], [16, 11, 16], 'trim', 'bed')],
          item_models / 'large_depot.json', item=True)
    model([box([0, 9, 0], [16, 14, 16], 'case'), box([0, 8, 0], [16, 9, 16], 'trim'),
           box([6, 5, 6], [10, 8, 10], 'ram'), box([.5, 3, .5], [15.5, 5, 15.5], 'tool'),
           box([7, 14, 7], [9, 16, 9], 'ram')], item_models / 'large_mechanical_press.json', item=True)
    write(item_models / 'large_iron_plate.json', {'parent': 'minecraft:item/generated', 'textures': {'layer0': NS + 'item/large_iron_plate'}})


def tile(name, dark, rim, light, lines=()):
    image = Image.new('RGB', (16, 16), dark)
    draw = ImageDraw.Draw(image)
    draw.rectangle((1, 1, 14, 14), fill=rim)
    draw.line((1, 1, 14, 1), fill=light)
    draw.line((1, 1, 1, 14), fill=light)
    for rect, shade in lines:
        draw.rectangle(rect, fill=shade)
    image.save(TEXTURES / (name + '.png'))


def draw_textures():
    TEXTURES.mkdir(parents=True, exist_ok=True)
    tile('case', '#323839', '#5a6666', '#818b85', [((3, 4, 12, 11), '#434e50'),
         ((3, 4, 12, 4), '#283337'), ((3, 11, 12, 11), '#6a7775'), ((2, 2, 3, 3), '#9e997f'), ((12, 12, 13, 13), '#99896b')])
    tile('rib', '#2f3638', '#4c5758', '#727e7c', [((x, 2, x+1, 13), '#65716d') for x in (3, 7, 11)])
    tile('deck', '#4d5045', '#7d8275', '#9ca294', [((4, y, 11, y), '#6e756b') for y in (5, 8, 11)])
    tile('bed', '#434d50', '#686f69', '#909991', [((3, 3, 12, 12), '#4e5856'), ((5, 5, 10, 10), '#75817a'),
         ((6, 6, 9, 9), '#626a63'), ((2, 2, 2, 2), '#b6a27e'), ((13, 13, 13, 13), '#b6a27e')])
    tile('brass', '#6c5235', '#9a774b', '#c2a16a', [((3, 10, 12, 10), '#7f5f3e')])
    tile('platen', '#404b4f', '#75817e', '#abb0a7', [((3, 11, 12, 11), '#5d6c6c'), ((3, 4, 12, 6), '#818c89')])
    tile('ram', '#515e62', '#8a9896', '#b7c1b7', [((4, 0, 5, 15), '#acb7af'), ((11, 0, 13, 15), '#707e7e')])

    plate = Image.new('RGBA', (16, 16))
    draw = ImageDraw.Draw(plate)
    draw.polygon([(1, 9), (9, 2), (15, 5), (15, 7), (7, 14), (1, 11)], fill='#46525a')
    draw.polygon([(1, 9), (9, 2), (15, 5), (7, 12)], fill='#a9b3b5')
    draw.line([(1, 9), (9, 2), (14, 5)], fill='#e2e9e4')
    draw.line([(1, 10), (7, 13), (15, 6)], fill='#778890')
    draw.line([(4, 9), (10, 4)], fill='#bbc7c7')
    draw.line([(7, 10), (12, 6)], fill='#8f9ea2')
    plate.save(ASSETS / 'textures/item/large_iron_plate.png')


def build_ponder():
    scene = Structure(7, 6)
    for y, center in ((1, 'large_depot'), (3, 'large_mechanical_press')):
        scene.block(3, y, 3, NS + center, nbt=[string('id', NS + center)])
        for (x, z), edge in EDGES.items():
            scene.block(3+x, y, 3+z, NS + 'large_press_frame', edge=edge, depot=str(y == 1).lower())
    scene.block(3, 4, 3, 'create:shaft', axis='y', waterlogged='false')
    scene.block(3, 5, 3, 'create:creative_motor', facing='down')
    scene.block(1, 1, 3, 'create:brass_funnel', facing='west', extracting='false', powered='false', waterlogged='false')
    scene.block(5, 1, 3, 'create:brass_funnel', facing='east', extracting='true', powered='false', waterlogged='false')
    scene.save('large_press')
    (OUT / 'large_press.nbt').replace(ASSETS / 'ponder/plant/large_press.nbt')


if __name__ == '__main__':
    build_models()
    draw_textures()
    build_ponder()
