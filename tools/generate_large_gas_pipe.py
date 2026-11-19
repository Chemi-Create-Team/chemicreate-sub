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
    'step_1': ('Use a filled gas bucket on the Creative Fluid Tank to set its supply. Any fluid pipe can carry gas.',
        'ガス入りバケツでクリエイティブタンクが供給する気体を設定します。気体はどの流体パイプでも送れます。',
        '用装有气体的桶设定创造模式流体储罐的气源。所有流体管道都能输送气体。'),
    'step_2': ('Gas needs a pump, just like liquid. Connect rotational power to the pump.',
        'ガスも液体と同じくポンプで送ります。ポンプへ回転動力を入力します。',
        '气体与液体一样需要泵输送，给泵接入旋转动力。'),
    'step_3': ('Use large gas pipes for more flow. They connect directly to narrow pipes.',
        '流量を増やすなら太い気体パイプを使います。細い管にもそのままつなげられます。',
        '需要更大流量时使用粗气管，也可直接连接细管。'),
    'step_4': ('High-pressure gas hisses while flowing in any pipe. Choose a receiving tank rated for that gas.',
        '高圧ガスはどの配管でも流れると風切り音がします。受け取りには気体の等級に合うタンクを使います。',
        '高压气体流过任何管道都会发出嘶声。接收端使用符合气体等级的储罐。'),
}
STEPS = ([1, 1, 1], [2, 1, 1], [3, 1, 4], [5, 1, 1])


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
    """Two gas lines from creative sources to axial gas receivers."""
    s = ponder.Structure(7, 4)
    for z, pipes in ((1, ('create:fluid_pipe', NS+'gas_pipe')), (4, (NS+'large_gas_pipe', NS+'large_gas_pipe'))):
        s.block(1, 1, z, 'create:creative_fluid_tank', bottom='true', top='true', shape='window')
        s.block(5, 1, z, NS+'gas_receiver', bottom='true', top='true', shape='plain', axis='x')
        s.pump(2, 1, z, 'east')
        s.block(2, 1, z+1, 'create:cogwheel', axis='x', waterlogged='false')
        s.block(1, 1, z+1, 'create:creative_motor', facing='east')
        for x, pipe in enumerate(pipes, 3):
            s.block(x, 1, z, pipe, east='true', west='true', north='false', south='false', up='false', down='false', waterlogged='false')
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
