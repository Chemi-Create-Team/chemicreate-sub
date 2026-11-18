#!/usr/bin/env python3
"""Large gas pipe: a full-block bore of the gas pipe. Textures, models, survival data, translations and the gas pipe Ponder."""
import json, sys
from pathlib import Path
from PIL import Image
sys.path.insert(0, 'tools')
import generate_petrochemical_ponder as ponder

ROOT = Path(__file__).resolve().parents[1]
A = ROOT / 'src/main/resources/assets/create_refinery_n_reactors'
D = ROOT / 'src/main/resources/data/create_refinery_n_reactors'
NS = 'create_refinery_n_reactors:'
EDGE, LIGHT, SHADE, SHELL, SEAM, RIVET, RING, BORE, DEEP = (69, 79, 86), (174, 184, 188), (100, 110, 116), (128, 138, 143), (112, 122, 128), (150, 160, 165), (146, 156, 161), (44, 50, 55), (30, 34, 38)


def write(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')


def shell(x, y):
    """The frame and bevel shared by the wall and the flange, lit from the top left."""
    if x in (0, 15) or y in (0, 15): return EDGE
    if x == 1 or y == 1: return LIGHT
    if x == 14 or y == 14: return SHADE
    return None


def wall(x, y):
    """Rolled plate with one welded seam and a rivet in each corner."""
    if (x, y) in ((3, 3), (12, 3), (3, 12), (12, 12)): return RIVET
    return SEAM if y in (7, 8) else SHELL


def flange(x, y):
    """A bolted flange around the open bore."""
    distance = (x - 7.5) ** 2 + (y - 7.5) ** 2
    if distance < 12: return DEEP
    if distance < 21: return BORE
    if distance < 33: return RING
    return RIVET if (x, y) in ((3, 3), (12, 3), (3, 12), (12, 12)) else SHELL


def textures():
    for name, inner in (('side', wall), ('end', flange)):
        image = Image.new('RGBA', (16, 16)); pixels = image.load()
        for y in range(16):
            for x in range(16): pixels[x, y] = (*(shell(x, y) or inner(x, y)), 255)
        image.save(A / f'textures/block/transport/large_gas_pipe_{name}.png')


def models():
    for name in ('side', 'end'):
        texture = NS + f'block/transport/large_gas_pipe_{name}'
        write(A / f'models/block/large_gas_pipe/{name}.json', {'textures': {'face': texture, 'particle': NS + 'block/transport/large_gas_pipe_side'},
            'elements': [{'from': [0, 0, 0], 'to': [16, 16, 16], 'faces': {'north': {'uv': [0, 0, 16, 16], 'texture': '#face', 'cullface': 'north'}}}]})
    parts = []
    for face, rotation in (('north', {}), ('east', {'y': 90}), ('south', {'y': 180}), ('west', {'y': 270}), ('up', {'x': 270}), ('down', {'x': 90})):
        for connected, name in (('true', 'end'), ('false', 'side')):
            parts.append({'when': {face: connected}, 'apply': {'model': NS + f'block/large_gas_pipe/{name}', **rotation}})
    write(A / 'blockstates/large_gas_pipe.json', {'multipart': parts})
    write(A / 'models/item/large_gas_pipe.json', {'parent': 'minecraft:block/cube_column',
        'textures': {'end': NS + 'block/transport/large_gas_pipe_end', 'side': NS + 'block/transport/large_gas_pipe_side'}})


def data():
    write(D / 'loot_table/blocks/large_gas_pipe.json', {'type': 'minecraft:block', 'pools': [{'rolls': 1,
        'entries': [{'type': 'minecraft:item', 'name': NS + 'large_gas_pipe'}], 'conditions': [{'condition': 'minecraft:survives_explosion'}]}]})
    write(D / 'recipe/large_gas_pipe.json', {'type': 'minecraft:crafting_shaped', 'category': 'misc', 'pattern': ['PP', 'PP'],
        'key': {'P': {'item': NS + 'gas_pipe'}}, 'result': {'id': NS + 'large_gas_pipe', 'count': 1}})
    path = ROOT / 'src/main/resources/data/minecraft/tags/block/mineable/pickaxe.json'
    tag = json.loads(path.read_text(encoding='utf-8'))
    tag['values'] = list(dict.fromkeys(tag['values'] + [NS + 'large_gas_pipe']))
    path.write_text(json.dumps(tag, indent=2) + '\n', encoding='utf-8')


NAMES = {
    'block.create_refinery_n_reactors.large_gas_pipe': ('Large Gas Pipe', '太い気体パイプ', '大口径气体管道'),
    'gas_pipe.create_refinery_n_reactors.volume': ('Volume: %s mB, holding %s mB of gas', '容積：%s mB・気体：%s mB', '容积：%s mB，气体：%s mB'),
}
SCENE = 'gas_pipes'
TEXTS = {
    'title': ('Gas pipes and their bore', '気体パイプと管の太さ', '气体管道与管径'),
    'step_1': ('Use gas pipes to transfer gas between vessels.',
        '気体用の配管です。容器同士をつないでガスを移せます。',
        '气体管道用于连接容器并转移气体。'),
    'step_2': ('Gas moves from higher to lower pressure. Check both vessels with pressure gauges.',
        '圧力の高い容器から低い容器へ流れます。圧力計で流れ先を確認します。',
        '气体从高压容器流向低压容器，可用压力计确认。'),
    'step_3': ('Use large gas pipes for more flow. They connect directly to narrow pipes.',
        '流量を増やすなら太い気体パイプを使います。細い管にもそのままつなげられます。',
        '需要更大流量时使用粗气管，也可直接连接细管。'),
    'step_4': ('Use large pipes at big tanks and compressor outlets. Both pipe sizes are rated to 16 bar.',
        '大きなタンクや圧縮機の出口には太い管を使います。どちらの管も耐圧は16 barです。',
        '大型储罐或压缩机出口使用粗管，两种管径耐压均为16 bar。'),
}
STEPS = ([3, 1, 1], [2, 1, 1], [3, 1, 4], [5, 2, 4])


def translations():
    for index, lang in enumerate(('en_us', 'ja_jp', 'zh_cn')):
        path = A / 'lang' / f'{lang}.json'
        raw = path.read_bytes().decode('utf-8'); newline = '\r\n' if '\r\n' in raw else '\n'
        strings = json.loads(raw)
        for key, words in NAMES.items(): strings[key] = words[index]
        for key, words in TEXTS.items(): strings[f'create_refinery_n_reactors.ponder.shared.guide.{SCENE}.{key}'] = words[index]
        strings[f'create_refinery_n_reactors.ponder.plant_{SCENE}.header'] = TEXTS['title'][index]
        path.write_bytes((json.dumps(strings, ensure_ascii=False, indent=2).replace('\n', newline) + newline).encode('utf-8'))


def storyboard():
    """Two pairs of Create tanks: the near pair joined by gas pipes, the far pair by large gas pipes."""
    s = ponder.Structure(7, 4)
    for z, pipe in ((1, 'gas_pipe'), (4, 'large_gas_pipe')):
        for x in (1, 5):
            for y in (1, 2): s.block(x, y, z, 'create:fluid_tank', bottom=str(y == 1).lower(), top=str(y == 2).lower(), shape='window')
        s.pump(2, 1, z, 'east')
        s.block(2, 1, z+1, 'create:cogwheel', axis='x', waterlogged='false')
        s.block(1, 1, z+1, 'create:creative_motor', facing='east')
        for x in (3, 4):
            s.block(x, 1, z, NS + pipe, east='true', west='true', north='false', south='false', up='false', down='false', waterlogged='false')
    ponder.OUT = A / 'ponder/plant'
    s.save(SCENE)
    path = A / 'ponder/tutorials.json'
    raw = path.read_bytes().decode('utf-8'); newline = '\r\n' if '\r\n' in raw else '\n'
    catalogue = json.loads(raw)
    entry = {'id': SCENE, 'size': 7, 'height': 4, 'components': ['gas_pipe', 'large_gas_pipe'],
        'steps': [{'target': target, 'key': f'step_{i + 1}', **({'speed': 64} if i==1 else {})} for i, target in enumerate(STEPS)]}
    for index, tutorial in enumerate(catalogue):
        if tutorial['id'] == SCENE: catalogue[index] = entry; break
    else: catalogue.append(entry)
    path.write_bytes((json.dumps(catalogue, ensure_ascii=False, indent=2).replace('\n', newline) + newline).encode('utf-8'))


if __name__ == '__main__':
    textures(); models(); data(); translations(); storyboard()
    print('Generated the large gas pipe and the gas pipe Ponder')
