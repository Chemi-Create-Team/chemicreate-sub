#!/usr/bin/env python3
"""Cement and industrial construction products made with existing Create machines."""
import json
from pathlib import Path
from PIL import Image, ImageDraw
from powder_assets import MANIFEST, write_powder_item

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / 'src/main/resources/assets/create_refinery_n_reactors'
DATA = ROOT / 'src/main/resources/data/create_refinery_n_reactors'
NS = 'create_refinery_n_reactors:'
POWDERS = {
    'ground_clinker': {'colour': [124, 124, 112], 'reason': 'Finely ground cement clinker before gypsum addition.'},
    'cement_powder': {'colour': [159, 157, 139], 'reason': 'Loose Portland cement binder powder.'},
}


def write(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + '\n')


def item(name, count=1):
    return {'id': name, 'count': count}


def recipe(name, value):
    write(DATA / 'recipe' / (name + '.json'), value)


def mixing(name, ingredients, results, heat=None, duration=200):
    value = {'type': 'create:mixing', 'ingredients': ingredients, 'results': results, 'processing_time': duration}
    if heat: value['heat_requirement'] = heat
    recipe('mixing/' + name, value)


def recipes():
    kiln = [{'item': 'minecraft:calcite'}] * 4 + [{'item': 'minecraft:clay_ball'}, {'tag': 'minecraft:sand'}, {'tag': 'c:nuggets/iron'}]
    mixing('cement_clinker', kiln, [item(NS + 'cement_clinker', 4)], 'superheated', 400)
    mixing('cement_clinker_with_sludge', kiln + [{'item': NS + 'treatment_sludge'}], [item(NS + 'cement_clinker', 5)], 'superheated', 400)
    recipe('crushing/ground_clinker', {'type': 'create:crushing', 'ingredients': [{'item': NS + 'cement_clinker'}],
        'results': [item(NS + 'ground_clinker')], 'processing_time': 200})
    mixing('cement_powder', [{'item': NS + 'ground_clinker'}] * 8 + [{'item': NS + 'gypsum'}], [item(NS + 'cement_powder', 8)])
    concrete = [{'item': NS + 'cement_powder'}] + [{'tag': 'minecraft:sand'}] * 4 + [{'item': 'minecraft:gravel'}] * 4
    concrete.append({'type': 'neoforge:single', 'fluid': 'minecraft:water', 'amount': 1000})
    mixing('industrial_concrete', concrete, [item(NS + 'industrial_concrete', 8)], duration=200)
    recipe('deploying/corrosion_resistant_floor', {'type': 'create:deploying',
        'ingredients': [{'item': NS + 'industrial_concrete'}, {'item': NS + 'plastic_sheet'}],
        'results': [item(NS + 'corrosion_resistant_floor')]})
    for block in ('industrial_concrete', 'corrosion_resistant_floor'):
        recipe(block + '_slab', {'type': 'minecraft:crafting_shaped', 'category': 'building', 'pattern': ['CCC'],
            'key': {'C': {'item': NS + block}}, 'result': item(NS + block + '_slab', 6)})
        recipe('stonecutting/' + block + '_slab', {'type': 'minecraft:stonecutting', 'ingredient': {'item': NS + block},
            'result': item(NS + block + '_slab', 2)})
        recipe(block + '_from_slabs', {'type': 'minecraft:crafting_shapeless', 'category': 'building',
            'ingredients': [{'item': NS + block + '_slab'}] * 2, 'result': item(NS + block)})
    recipe('industrial_concrete_stairs', {'type': 'minecraft:crafting_shaped', 'category': 'building', 'pattern': ['C  ', 'CC ', 'CCC'],
        'key': {'C': {'item': NS + 'industrial_concrete'}}, 'result': item(NS + 'industrial_concrete_stairs', 4)})
    recipe('stonecutting/industrial_concrete_stairs', {'type': 'minecraft:stonecutting', 'ingredient': {'item': NS + 'industrial_concrete'},
        'result': item(NS + 'industrial_concrete_stairs')})


def textures():
    MANIFEST['powders'].update(POWDERS)
    for name in POWDERS: write_powder_item(name)
    im = Image.new('RGBA', (16, 16)); draw = ImageDraw.Draw(im)
    draw.polygon([(2, 8), (3, 5), (7, 4), (9, 6), (13, 6), (14, 9), (12, 13), (5, 13), (2, 11)], fill=(73, 75, 68))
    draw.polygon([(3, 8), (4, 6), (7, 5), (8, 7), (6, 10), (3, 10)], fill=(148, 147, 121))
    draw.polygon([(8, 9), (10, 7), (12, 7), (13, 9), (11, 12), (8, 12)], fill=(115, 117, 102))
    draw.rectangle((5, 11, 6, 12), fill=(94, 95, 82));draw.rectangle((10, 7, 11, 8), fill=(165, 162, 134))
    draw.point((4, 7), fill=(188, 183, 153));draw.point((12, 10), fill=(88, 91, 81))
    (ASSETS / 'textures/item').mkdir(parents=True, exist_ok=True)
    im.save(ASSETS / 'textures/item/cement_clinker.png')
    write(ASSETS / 'models/item/cement_clinker.json', {'parent': 'minecraft:item/generated', 'textures': {'layer0': NS + 'item/cement_clinker'}})
    for name, coating in (('industrial_concrete', False), ('corrosion_resistant_floor', True)):
        colors = [(98, 101, 99), (112, 115, 112), (120, 123, 118), (128, 130, 124), (143, 143, 133)]
        if coating: colors = [(62, 75, 68), (70, 84, 75), (79, 94, 82), (89, 103, 91), (107, 117, 103)]
        im = Image.new('RGBA', (16, 16), colors[2]);draw = ImageDraw.Draw(im)
        # Aggregate is drawn in small clusters, with a moulded joint and a fixed upper-left highlight.
        for x, y, colour in ((3, 3, 1), (9, 2, 3), (12, 5, 1), (6, 7, 3), (2, 10, 3), (10, 11, 1), (6, 13, 1)):
            draw.rectangle((x, y, x + 1, y + 1), fill=colors[colour])
        draw.line((0, 0, 15, 0), fill=colors[4]);draw.line((0, 1, 0, 14), fill=colors[3])
        draw.line((1, 15, 15, 15), fill=colors[0]);draw.line((15, 1, 15, 14), fill=colors[0])
        if coating:
            draw.line((1, 1, 14, 1), fill=(126, 131, 112));draw.line((1, 2, 1, 14), fill=(107, 117, 103))
            draw.line((2, 14, 14, 14), fill=(53, 65, 59));draw.line((14, 2, 14, 13), fill=(53, 65, 59))
        (ASSETS / 'textures/block').mkdir(parents=True, exist_ok=True)
        im.save(ASSETS / 'textures/block' / (name + '.png'))


def cube_model(name, parent='cube_all'):
    write(ASSETS / 'models/block' / (name + '.json'), {'parent': 'minecraft:block/' + parent, 'textures': {'all': NS + 'block/' + name}})


def models():
    for name in ('industrial_concrete', 'corrosion_resistant_floor'):
        cube_model(name)
        write(ASSETS / 'blockstates' / (name + '.json'), {'variants': {'': {'model': NS + 'block/' + name}}})
        for suffix, parent in (('_slab', 'slab'), ('_slab_top', 'slab_top')):
            write(ASSETS / 'models/block' / (name + suffix + '.json'), {'parent': 'minecraft:block/' + parent,
                'textures': {face: NS + 'block/' + name for face in ('bottom', 'top', 'side')}})
        write(ASSETS / 'blockstates' / (name + '_slab.json'), {'variants': {
            'type=bottom': {'model': NS + 'block/' + name + '_slab'},
            'type=top': {'model': NS + 'block/' + name + '_slab_top'}, 'type=double': {'model': NS + 'block/' + name}}})
    for shape, parent in (('', 'stairs'), ('_inner', 'inner_stairs'), ('_outer', 'outer_stairs')):
        write(ASSETS / 'models/block' / ('industrial_concrete_stairs' + shape + '.json'), {'parent': 'minecraft:block/' + parent,
            'textures': {face: NS + 'block/industrial_concrete' for face in ('bottom', 'top', 'side')}})
    variants = {}
    for facing, facing_y in (('east', 0), ('south', 90), ('west', 180), ('north', 270)):
        for half in ('bottom', 'top'):
            for shape in ('straight', 'inner_left', 'inner_right', 'outer_left', 'outer_right'):
                y = facing_y
                if shape.endswith('_left'): y += 270
                if half == 'top' and shape != 'straight': y += 90
                model = 'industrial_concrete_stairs' + ('_inner' if shape.startswith('inner') else '_outer' if shape.startswith('outer') else '')
                variants[f'facing={facing},half={half},shape={shape}'] = {'model': NS + 'block/' + model, 'y': y % 360, 'uvlock': True}
                if half == 'top': variants[f'facing={facing},half={half},shape={shape}']['x'] = 180
    write(ASSETS / 'blockstates/industrial_concrete_stairs.json', {'variants': variants})
    blocks = ['industrial_concrete', 'industrial_concrete_slab', 'industrial_concrete_stairs', 'corrosion_resistant_floor', 'corrosion_resistant_floor_slab']
    for name in blocks:
        write(ASSETS / 'models/item' / (name + '.json'), {'parent': NS + 'block/' + name})
        loot = {'type': 'minecraft:block', 'pools': [{'rolls': 1, 'entries': [{'type': 'minecraft:item', 'name': NS + name}],
            'conditions': [{'condition': 'minecraft:survives_explosion'}]}]}
        if name.endswith('_slab'):
            loot['pools'][0]['entries'][0]['functions'] = [{'function': 'minecraft:set_count', 'count': 2,
                'conditions': [{'condition': 'minecraft:block_state_property', 'block': NS + name, 'properties': {'type': 'double'}}]}]
        write(DATA / 'loot_table/blocks' / (name + '.json'), loot)
    for tag in ('mineable/pickaxe', 'needs_stone_tool'):
        write(ROOT / 'src/main/resources/data/minecraft/tags/block' / (tag + '.json'),
            {'replace': False, 'values': merge_tag(tag, [NS + name for name in blocks])})
    for kind in ('slabs', 'stairs'):
        values = [NS + 'industrial_concrete_' + kind[:-1]] if kind == 'slabs' else [NS + 'industrial_concrete_stairs']
        if kind == 'slabs': values.append(NS + 'corrosion_resistant_floor_slab')
        # Kept in integration data so multiple feature generators never overwrite shared tags.
        write(ROOT / 'tools/data' / ('building_' + kind + '_tag.json'), {'values': values})


def merge_tag(tag, values):
    path = ROOT / 'src/main/resources/data/minecraft/tags/block' / (tag + '.json')
    old = json.loads(path.read_text()) if path.exists() else {'values': []}
    return list(dict.fromkeys(old['values'] + values))


NAMES = {
    'cement_clinker': ('Cement Clinker', 'セメントクリンカー', '水泥熟料'),
    'ground_clinker': ('Ground Clinker', '粉砕クリンカー', '熟料粉'),
    'cement_powder': ('Portland Cement', 'ポルトランドセメント', '波特兰水泥'),
    'industrial_concrete': ('Industrial Concrete', '工場用コンクリート', '工业混凝土'),
    'industrial_concrete_slab': ('Industrial Concrete Slab', '工場用コンクリートのハーフブロック', '工业混凝土台阶'),
    'industrial_concrete_stairs': ('Industrial Concrete Stairs', '工場用コンクリートの階段', '工业混凝土楼梯'),
    'corrosion_resistant_floor': ('Polymer-Lined Floor', '樹脂張り防食床', '树脂防腐地板'),
    'corrosion_resistant_floor_slab': ('Polymer-Lined Floor Slab', '樹脂張り防食床のハーフブロック', '树脂防腐地板台阶'),
}





def integration():
    translations = {language: {} for language in ('en_us', 'ja_jp', 'zh_cn')}
    for name, strings in NAMES.items():
        kind = 'item' if name in ('cement_clinker', 'ground_clinker', 'cement_powder') else 'block'
        for index, language in enumerate(translations): translations[language][kind + '.create_refinery_n_reactors.' + name] = strings[index]
    write(ROOT / 'tools/data/building_materials_lang.json', translations)
    write(ROOT / 'tools/data/building_materials_powders.json', {'powders': POWDERS, 'preserved_items': {'cement_clinker': 'Fired clinker nodules before crushing.'}})


if __name__ == '__main__':
    recipes();textures();models();integration()
    print('Generated cement, concrete, resin-lined floors and structural pipe supports; shared language/Ponder registration stays with integration.')
