#!/usr/bin/env python3
"""Generate the oxygen converter's recipes, restrained block models, loot and translations."""
import json
import struct
import sys
import zlib
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'tools'))
from minecraft_style import box

A = ROOT / 'src/main/resources/assets/create_refinery_n_reactors'
D = ROOT / 'src/main/resources/data/create_refinery_n_reactors'
NS = 'create_refinery_n_reactors:'


def write(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')


def preserve(path, value):
    raw = path.read_bytes().decode('utf-8') if path.exists() else ''
    newline = '\r\n' if '\r\n' in raw else '\n'
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes((json.dumps(value, ensure_ascii=False, indent=2).replace('\n', newline) + newline).encode('utf-8'))


def png(path, image):
    def chunk(kind, data):
        return struct.pack('>I', len(data)) + kind + data + struct.pack('>I', zlib.crc32(kind + data) & 0xffffffff)
    path.parent.mkdir(parents=True, exist_ok=True)
    raw = b''.join(b'\0' + bytes(channel for pixel in row for channel in pixel) for row in image)
    path.write_bytes(b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>IIBBBBB', 16, 16, 8, 6, 0, 0, 0))
        + chunk(b'IDAT', zlib.compress(raw, 9)) + chunk(b'IEND', b''))


TEXTURES = {
    'girder': 'create:block/girder',
    'steel': 'create:block/industrial_iron_block',
    'steel_top': 'create:block/industrial_iron_block_top',
    'brass': 'create:block/brass_casing',
    'blackstone': 'minecraft:block/polished_blackstone_bricks',
    'rough': 'minecraft:block/blackstone',
}


def model(name, elements, display=None):
    data = {
        'parent': 'minecraft:block/block',
        'ambientocclusion': True,
        'textures': TEXTURES | {'particle': TEXTURES['blackstone']},
        'elements': elements,
    }
    if display is not None:
        data['display'] = display
    write(A / f'models/block/converter/{name}.json', data)


def block_models():
    parts = {}
    e = []
    box(e, [1, 0, 1], [15, 16, 15], 'blackstone')
    box(e, [0, 1, 0], [16, 4, 16], 'steel')
    box(e, [0, 12, 0], [16, 15, 16], 'steel')
    box(e, [2, 4, 0], [14, 6, 16], 'brass')
    parts['loose'] = e

    e = []
    box(e, [2, 0, 2], [14, 6, 14], 'blackstone')
    box(e, [4, 4, 4], [12, 16, 12], 'steel')
    box(e, [6, -8, 6], [10, 8, 10], 'steel')
    box(e, [3, 4, 3], [13, 8, 13], 'brass')
    parts['lance'] = e

    e = []
    box(e, [2, 0, 2], [14, 5, 14], 'blackstone')
    box(e, [4, -4, 4], [12, 16, 12], 'steel')
    box(e, [2, 5, 2], [14, 9, 14], 'brass')
    parts['raw_port'] = e

    e = []
    box(e, [2, 2, 2], [14, 14, 14], 'blackstone')
    box(e, [5, 5, 8], [11, 11, 24], 'steel')
    box(e, [3, 3, 11], [13, 13, 16], 'brass')
    parts['gas_port'] = e

    e = []
    box(e, [5, 5, -8], [11, 11, 12], 'steel')
    box(e, [2, 2, 2], [14, 14, 14], 'blackstone')
    box(e, [3, 3, -1], [13, 13, 4], 'brass')
    parts['tap'] = e

    e = []
    box(e, [0, 0, 0], [16, 16, 16], 'blackstone')
    box(e, [2, 2, 2], [14, 14, 14], 'steel')
    box(e, [-4, 4, 4], [4, 12, 12], 'brass')
    box(e, [3, 3, 3], [13, 13, 13], 'rough')
    parts['bearing'] = e

    for name, elements in parts.items():
        model(name, elements)

    variants = {}
    rotations = {'north': 0, 'east': 90, 'south': 180, 'west': 270}
    for facing, rotation in rotations.items():
        for part in parts:
            variants[f'facing={facing},part={part}'] = {
                'model': NS + f'block/converter/{part}', 'y': rotation}
    write(A / 'blockstates/converter_shell.json', {'variants': variants})


def item_and_material_models():
    display = {
        'gui': {'rotation': [24, 225, 0], 'translation': [0, -2, 0], 'scale': [.3, .3, .3]},
        'ground': {'translation': [0, 2, 0], 'scale': [.2, .2, .2]},
        'fixed': {'rotation': [0, 180, 0], 'scale': [.25, .25, .25]},
        'thirdperson_righthand': {'rotation': [70, 45, 0], 'translation': [0, 1, 0], 'scale': [.22, .22, .22]},
        'firstperson_righthand': {'rotation': [0, 45, 0], 'scale': [.25, .25, .25]},
    }
    item_elements = []
    box(item_elements, [3, 1, 3], [13, 11, 13], 'blackstone')
    box(item_elements, [2, 3, 2], [14, 6, 14], 'brass')
    box(item_elements, [2, 9, 2], [14, 12, 14], 'steel')
    box(item_elements, [0, 5, 6], [16, 9, 10], 'rough')
    box(item_elements, [6, 12, 6], [10, 16, 10], 'steel')
    model('item', item_elements, display)
    write(A / 'models/item/converter_shell.json', {'parent': NS + 'block/converter/item'})

    write(A / 'models/block/converter_trunnion_bearing.json', {
        'parent': 'create:block/bearing/block',
        'textures': {'back': 'create:block/gearbox', 'side': 'create:block/industrial_iron_block'}})
    write(A / 'models/item/converter_trunnion_bearing.json', {
        'parent': 'create:block/bearing/item',
        'textures': {
            'back': 'create:block/gearbox',
            'side': 'create:block/industrial_iron_block',
            'top': 'create:block/bearing_top'}})
    variants = {
        'facing=down': {'model': NS + 'block/converter_trunnion_bearing', 'x': 180},
        'facing=east': {'model': NS + 'block/converter_trunnion_bearing', 'x': 90, 'y': 90},
        'facing=north': {'model': NS + 'block/converter_trunnion_bearing', 'x': 90},
        'facing=south': {'model': NS + 'block/converter_trunnion_bearing', 'x': 90, 'y': 180},
        'facing=up': {'model': NS + 'block/converter_trunnion_bearing'},
        'facing=west': {'model': NS + 'block/converter_trunnion_bearing', 'x': 90, 'y': 270},
    }
    write(A / 'blockstates/converter_trunnion_bearing.json', {'variants': variants})
    write(A / 'blockstates/zirconia_refractory_bricks.json', {
        'variants': {'': {'model': NS + 'block/zirconia_refractory_bricks'}}})
    write(A / 'models/block/zirconia_refractory_bricks.json', {
        'parent': 'minecraft:block/cube_all',
        'textures': {'all': NS + 'block/zirconia_refractory_bricks'}})
    write(A / 'models/item/zirconia_refractory_bricks.json', {
        'parent': NS + 'block/zirconia_refractory_bricks'})


def refractory_texture():
    mortar = (116, 119, 116, 255)
    palette = ((205, 207, 198, 255), (221, 221, 210, 255), (189, 193, 188, 255),
               (231, 229, 215, 255), (173, 178, 175, 255))
    image = []
    for y in range(16):
        row = []
        course = y // 4
        offset = 4 if course % 2 else 0
        for x in range(16):
            joint = y % 4 == 3 or (x + offset) % 8 == 7
            if joint:
                colour = mortar if (x * 3 + y) % 5 else (104, 108, 107, 255)
            else:
                colour = palette[(x * 5 + y * 3 + course) % len(palette)]
                if (x * 11 + y * 7) % 19 == 0:
                    colour = tuple(max(0, channel - 18) for channel in colour[:3]) + (255,)
            row.append(colour)
        image.append(row)
    png(A / 'textures/block/zirconia_refractory_bricks.png', image)


def remove_retired_assets():
    for name in ('base', 'column', 'beam_x', 'beam_z', 'top_x', 'top_z', 'vessel',
                 'drive', 'empty', 'vessel_hot'):
        (A / f'models/block/converter/{name}.json').unlink(missing_ok=True)
    for name in ('steel', 'dark', 'copper', 'brass', 'refractory', 'hot'):
        (A / f'textures/block/converter/{name}.png').unlink(missing_ok=True)


def recipes_and_data():
    for name in ('converter_shell', 'converter_trunnion_bearing', 'zirconia_refractory_bricks'):
        write(D / f'loot_table/blocks/{name}.json', {
            'type': 'minecraft:block',
            'pools': [{'rolls': 1, 'entries': [{'type': 'minecraft:item', 'name': NS + name}],
                'conditions': [{'condition': 'minecraft:survives_explosion'}]}]})
    write(D / 'recipe/converter_shell.json', {
        'type': 'minecraft:crafting_shaped', 'category': 'misc', 'pattern': ['SB', 'BS'],
        'key': {'S': {'item': NS + 'steel_block'},
                'B': {'item': 'minecraft:polished_blackstone_bricks'}},
        'result': {'id': NS + 'converter_shell', 'count': 4}})
    write(D / 'recipe/converter_trunnion_bearing.json', {
        'type': 'minecraft:crafting_shaped', 'category': 'misc',
        'pattern': ['ISI', 'SMS', 'IBI'],
        'key': {
            'I': {'item': NS + 'steel_block'},
            'S': {'item': 'create:shaft'},
            'M': {'item': 'create:mechanical_bearing'},
            'B': {'item': 'create:brass_casing'}},
        'result': {'id': NS + 'converter_trunnion_bearing'}})
    write(D / 'recipe/zirconia_refractory_bricks.json', {
        'type': 'minecraft:crafting_shapeless', 'category': 'building',
        'ingredients': ([{'item': NS + 'zirconia'}] * 4
                        + [{'item': 'minecraft:brick'}] * 4
                        + [{'item': 'minecraft:clay_ball'}]),
        'result': {'id': NS + 'zirconia_refractory_bricks', 'count': 4}})
    write(D / 'recipe/converter/oxygen_steel.json', {
        'type': NS + 'converter',
        'ingredients': [
            {'item': 'minecraft:iron_ingot', 'count': 48},
            {'item': 'minecraft:raw_iron', 'count': 24}],
        'gas': {'fluid': NS + 'oxygen_gas', 'amount': 64000},
        'result': {'id': NS + 'steel_ingot', 'count': 64},
        'byproduct': {'id': NS + 'converter_gas', 'amount': 128000},
        'processingTime': 800})
    write(D / 'recipe/converter/aod_stainless_steel.json', {
        'type': NS + 'converter',
        'ingredients': [
            {'item': 'minecraft:iron_ingot', 'count': 112},
            {'item': NS + 'chromium_ingot', 'count': 32},
            {'item': NS + 'nickel_ingot', 'count': 16}],
        'gas': {'fluid': NS + 'argon_gas', 'amount': 1600},
        'result': {'id': NS + 'stainless_steel_ingot', 'count': 192},
        'processingTime': 800})
    for relative in ('mineable/pickaxe.json', 'needs_iron_tool.json'):
        path = ROOT / 'src/main/resources/data/minecraft/tags/block' / relative
        tag = json.loads(path.read_text(encoding='utf-8'))
        tag['values'] = list(dict.fromkeys(tag['values'] + [
            NS + 'converter_shell', NS + 'converter_trunnion_bearing',
            NS + 'zirconia_refractory_bricks']))
        preserve(path, tag)


def gas_assets():
    oxygen_state = json.loads((A / 'blockstates/oxygen_gas.json').read_text(encoding='utf-8'))
    write(A / 'blockstates/converter_gas.json', oxygen_state)
    write(A / 'models/item/converter_gas_bucket.json', {'parent': 'minecraft:item/generated', 'textures': {'layer0': NS + 'item/converter_gas_bucket'}})
    template = json.loads((ROOT / 'tools/vanilla_bucket_template.json').read_text(encoding='utf-8'))
    image = [[tuple(template['pixels'][y * 16 + x]) for x in range(16)] for y in range(16)]
    base = (174, 183, 169)
    for index in template['content_mask']:
        x, y = index % 16, index // 16
        shade = 26 if y == 5 and x in (6, 9) else -20 if y >= 7 else 8 if y == 4 else 0
        image[y][x] = tuple(max(0, min(255, channel + shade)) for channel in base) + (255,)
    png(A / 'textures/item/converter_gas_bucket.png', image)


TRANSLATIONS = {
    'block.create_refinery_n_reactors.converter_shell': ('Converter Shell', '転炉の炉殻', '转炉外壳'),
    'block.create_refinery_n_reactors.converter_trunnion_bearing': ('Converter Trunnion Bearing', '転炉トラニオン軸受', '转炉耳轴轴承'),
    'block.create_refinery_n_reactors.zirconia_refractory_bricks': ('Zirconia Refractory Bricks', 'ジルコニア耐火レンガ', '氧化锆耐火砖'),
    'block.create_refinery_n_reactors.converter_gas': ('Converter Gas', '転炉ガス', '转炉煤气'),
    'fluid_type.create_refinery_n_reactors.converter_gas': ('Converter Gas', '転炉ガス', '转炉煤气'),
    'item.create_refinery_n_reactors.converter_gas_bucket': ('Converter Gas Bucket', '転炉ガス入りバケツ', '转炉煤气桶'),
    'converter.create_refinery_n_reactors.state': ('Converter: %s', '転炉：%s', '转炉：%s'),
    'converter.create_refinery_n_reactors.angle': ('Tilt: %s / %s degrees', '傾き：%s / %s度', '倾角：%s / %s度'),
    'converter.create_refinery_n_reactors.progress': ('Blowing: %s / %s ticks', '吹錬：%s / %s tick', '吹炼：%s / %s tick'),
    'converter.create_refinery_n_reactors.stores': ('Items %s; gas in %s/%s mB; offgas %s/%s mB', 'アイテム%s個・吹込気体%s/%s mB・転炉ガス%s/%s mB', '物品%s个；吹入气体%s/%s mB；转炉煤气%s/%s mB'),
    'converter.create_refinery_n_reactors.status.unformed': ('Structure incomplete', '構造が未完成', '结构未完成'),
    'converter.create_refinery_n_reactors.status.unloaded': ('Structure chunk unloaded', '構造のチャンクが未読み込み', '结构区块未加载'),
    'converter.create_refinery_n_reactors.status.idle': ('Upright and idle', '直立・待機中', '直立待机'),
    'converter.create_refinery_n_reactors.status.no_feed': ('Waiting for a complete charge', '原料と気体を待機中', '等待完整炉料'),
    'converter.create_refinery_n_reactors.status.waiting': ('Waiting', '待機中', '等待中'),
    'converter.create_refinery_n_reactors.status.blowing': ('Blowing', '吹錬中', '吹炼中'),
    'converter.create_refinery_n_reactors.status.tilting': ('Tilting', '傾動中', '倾动中'),
    'converter.create_refinery_n_reactors.status.tapping': ('Tapping', '出鋼中', '出钢中'),
    'converter.create_refinery_n_reactors.status.ready': ('Charge ready to tap', '出鋼可能', '可出钢'),
    'converter.create_refinery_n_reactors.status.output_full': ('Output blocked', '出力が満杯', '输出堵塞'),
    'converter.create_refinery_n_reactors.status.gas_disconnected': ('Connect the converter-gas outlet', '転炉ガス出口を接続してください', '请连接转炉煤气出口'),
    'converter.create_refinery_n_reactors.status.recipe_missing': ('Saved recipe is unavailable', '保存されたレシピがありません', '已保存配方不可用'),
    'jei.create_refinery_n_reactors.note.converter': ('Whole charge; %s ticks. Tilt the vessel to extract the product.', '1チャージ・%s tick。炉体を傾けて製品を取り出します。', '整炉处理需%s tick；倾动炉体后取出产品。'),
    'display.create_refinery_n_reactors.converter_angle': ('Converter tilt (degrees)', '転炉の傾き（度）', '转炉倾角（度）'),
    'display.create_refinery_n_reactors.converter_items_in': ('Converter raw materials', '転炉の原料数', '转炉原料数'),
    'display.create_refinery_n_reactors.converter_items_out': ('Converter products', '転炉の製品数', '转炉产品数'),
    'display.create_refinery_n_reactors.converter_input_gas': ('Converter blowing gas (mB)', '転炉の吹込気体（mB）', '转炉吹入气体（mB）'),
    'display.create_refinery_n_reactors.converter_offgas': ('Converter gas (mB)', '転炉ガス（mB）', '转炉煤气（mB）'),
    'gui.assembly.exception.create_refinery_n_reactors.converter_direction': (
        'The converter trunnion must face horizontally', '転炉トラニオン軸受を水平に向けてください', '转炉耳轴轴承必须水平放置'),
    'gui.assembly.exception.create_refinery_n_reactors.converter_frame': (
        'The fixed converter frame is incomplete', '転炉の固定架台が未完成です', '转炉固定框架不完整'),
    'gui.assembly.exception.create_refinery_n_reactors.converter_missing': (
        'The converter vessel is missing blocks', '転炉の炉体に不足しているブロックがあります', '转炉炉体缺少方块'),
    'gui.assembly.exception.create_refinery_n_reactors.converter_material': (
        'The converter vessel contains an incorrect material', '転炉の炉体に指定外の素材があります', '转炉炉体含有错误材料'),
    'gui.assembly.exception.create_refinery_n_reactors.converter_unglued': (
        'Glue every block of the converter vessel', '転炉の炉体をすべて超粘着剤で接着してください', '请用强力胶粘合转炉炉体的所有方块'),
    'gui.assembly.exception.create_refinery_n_reactors.converter_extra': (
        'Extra glued blocks are attached to the converter vessel', '転炉の炉体に余計な接着ブロックがあります', '转炉炉体粘连了多余方块'),
}


def translations():
    for language, index in (('en_us', 0), ('ja_jp', 1), ('zh_cn', 2)):
        path = A / f'lang/{language}.json'
        data = json.loads(path.read_text(encoding='utf-8'))
        for key, values in TRANSLATIONS.items():
            data[key] = values[index]
        preserve(path, data)


def generate():
    remove_retired_assets()
    block_models()
    item_and_material_models()
    refractory_texture()
    recipes_and_data()
    gas_assets()
    translations()
    print('Generated converter models, gas, recipes, loot, tags and three-language text')


if __name__ == '__main__':
    generate()
