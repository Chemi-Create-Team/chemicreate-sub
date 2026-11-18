#!/usr/bin/env python3
"""Native Create forging recipes and item models using the existing ingot and raw-ore sprites."""
import json
import generate_petrochemical_ponder as ponder
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
A = ROOT / 'src/main/resources/assets/create_refinery_n_reactors'
R = ROOT / 'src/main/resources/data/create_refinery_n_reactors/recipe'
NS = 'create_refinery_n_reactors:'


def write(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + '\n')


def recipes():
    write(R / 'mixing/pig_iron.json', {
        'type': 'create:mixing', 'ingredients': [{'item': 'minecraft:iron_ingot'}, {'item': 'minecraft:charcoal'}],
        'results': [{'id': NS + 'pig_iron'}], 'heat_requirement': 'heated'})
    write(R / 'haunting/fluorite_from_diorite.json', {
        'type': 'create:haunting', 'ingredients': [{'item': 'minecraft:diorite'}],
        'results': [{'id': NS + 'fluorite'}]})
    write(R / 'mixing/fluorite_from_bone_meal.json', {
        'type': 'create:mixing', 'ingredients': [{'item': 'minecraft:bone_meal'}, {'item': 'minecraft:gravel'},
            {'type': 'neoforge:single', 'fluid': 'minecraft:water', 'amount': 250}],
        'results': [{'id': NS + 'fluorite'}]})
    step = lambda kind, extras=(): {'type': kind, 'ingredients': [{'item': NS + 'heated_unfinished_steel'}],
                                  'results': [{'id': NS + 'heated_unfinished_steel'}, *extras]}
    write(R / 'sequenced_assembly/forged_steel.json', {
        'type': 'create:sequenced_assembly', 'ingredient': {'item': NS + 'pig_iron'}, 'loops': 3,
        'results': [{'id': NS + 'steel_ingot'}], 'transitional_item': {'id': NS + 'heated_unfinished_steel'},
        'sequence': [step('create:pressing'), step(NS + 'forging_blasting'),
                     step(NS + 'forging_pressing', [{'id': NS + 'steel_slag', 'chance': .25}])]})


def models():
    for item, texture in [('pig_iron', 'iron_ingot'), ('heated_unfinished_steel', 'iron_ingot'), ('steel_slag', 'raw_iron')]:
        write(A / f'models/item/{item}.json', {'parent': 'minecraft:item/generated', 'textures': {'layer0': 'minecraft:item/' + texture}})


def structures():
    previous = ponder.OUT
    ponder.OUT = ROOT / 'src/main/resources/data/create_refinery_n_reactors/structure'
    ponder.Structure(24, 8).save('test_forging')
    ponder.OUT = A / 'ponder/plant'
    line = ponder.Structure(24, 7, floor=False)
    for x in range(21):
        for z in range(13):
            if (x, z) != (2, 10):line.block(x, 0, z, 'minecraft:smooth_stone')
    for x in range(1, 18):
        nbt = [ponder.string('id', 'create:belt'), ponder.named(3, 'Length', ponder.integer(17)),
               ponder.named(3, 'Index', ponder.integer(x - 1)),
               ponder.named(11, 'Controller', b''.join(ponder.integer(v) for v in (3, 1, 1, 5))),
               ponder.named(1, 'IsController', bytes([x == 1]))]
        line.block(x, 1, 5, 'create:belt', nbt=nbt, facing='east', slope='horizontal',
                   part='start' if x == 1 else 'end' if x == 17 else 'middle', casing='false')
    line.block(1, 1, 6, 'create:creative_motor', facing='north')
    for x in (2, 6, 7, 11, 12, 16):
        line.block(x, 3, 5, 'create:mechanical_press', facing='north')
        line.block(x, 3, 6, 'create:creative_motor', facing='north')
    for x in (4, 9, 14):
        line.block(x, 2, 1, 'create:encased_fan', facing='south')
        line.block(x, 2, 0, 'create:creative_motor', facing='south')
        line.block(x, 1, 2, 'minecraft:stone')
        line.block(x, 2, 2, 'minecraft:lava', level='0')
        line.block(x, 2, 3, 'minecraft:iron_bars')
        line.block(x - 1, 2, 2, 'minecraft:stone')
        line.block(x + 1, 2, 2, 'minecraft:stone')
    line.block(1, 3, 5, 'minecraft:chest', facing='west')
    line.block(1, 2, 5, 'create:andesite_funnel', facing='down', extracting='true', powered='false')
    line.block(18, 2, 5, 'minecraft:chest', facing='west')
    line.block(17, 2, 5, 'create:andesite_belt_funnel', facing='west', shape='pushing', powered='false')
    line.block(2, 0, 10, 'create:blaze_burner', blaze='kindled')
    line.block(2, 1, 10, 'create:basin')
    line.block(2, 3, 10, 'create:mechanical_mixer')
    line.block(2, 4, 10, 'create:creative_motor', facing='down')
    line.save('steel_forging')
    sources = ponder.Structure(9, 7)
    sources.block(0, 2, 2, 'create:creative_motor', facing='east')
    sources.block(1, 2, 2, 'create:encased_fan', facing='east')
    sources.block(2, 1, 2, 'minecraft:stone')
    sources.block(2, 2, 2, 'minecraft:soul_campfire', lit='true', waterlogged='false')
    sources.block(4, 1, 2, 'create:depot')
    sources.block(5, 2, 6, 'create:basin')
    sources.block(5, 4, 6, 'create:mechanical_mixer')
    sources.block(5, 5, 6, 'create:creative_motor', facing='down')
    sources.block(6, 2, 6, 'create:fluid_pipe', east='true', west='true', north='false', south='false', up='false', down='false', waterlogged='false')
    sources.pump(7, 2, 6, 'west')
    sources.block(7, 2, 7, 'create:creative_motor', facing='north')
    sources.block(8, 2, 6, 'create:fluid_tank', bottom='true', top='true', shape='plain')
    sources.save('fluorite_sources')
    ponder.OUT = previous


def languages():
    entries = {
        'item.create_refinery_n_reactors.pig_iron': ('Pig Iron', '銑鉄', '生铁'),
        'item.create_refinery_n_reactors.heated_unfinished_steel': ('Heated Unfinished Steel', '赤熱した未完成鋼', '炽热的未完成钢'),
        'item.create_refinery_n_reactors.steel_slag': ('Steel Slag', '製鋼スラグ', '炼钢炉渣'),
        'recipe.create_refinery_n_reactors.assembly.folding': ('Fold; slag only on completion', '折り返しプレス：完成時だけスラグを分離', '折叠锻压：仅在完成时分离炉渣'),
        'recipe.create_refinery_n_reactors.assembly.blasting': ('Reheat with a lava fan', '溶岩ファンで再加熱', '用熔岩风扇重新加热'),
    }
    lessons = {
        'steel_forging': {
            'title': ('Forge steel along a belt', 'ベルトラインで鋼鉄を鍛造する', '在传送带上锻造钢'),
            'pig': ('Mix one iron ingot and one charcoal in a heated Basin to make pig iron.', '加熱した鉢で鉄インゴット1個と木炭1個を混ぜ、銑鉄を作ります。', '在加热的工作盆中混合1个铁锭和1个木炭，制成生铁。'),
            'press': ('Feed pig iron onto the belt. The first press starts the heated unfinished steel.', '銑鉄をベルトへ入れ、最初のプレスで赤熱した未完成鋼にします。', '将生铁送上传送带，第一次锻压开始炽热钢坯的加工。'),
            'heat': ('Blow air through lava to reheat it. Iron bars contain the lava; slow the belt so heating finishes.', '溶岩を通した風で再加熱します。鉄格子で溶岩を止め、加工が終わる速さまでベルトを落とします。', '用穿过熔岩的气流重新加热。用铁栏杆挡住熔岩，并降低带速以完成加热。'),
            'fold': ('Use a second press to fold the hot steel. Heating twice cannot replace this press.', '2台目のプレスで折り返します。加熱だけを繰り返しても工程は進みません。', '用第二台压机折叠热钢，反复加热不能代替这次锻压。'),
            'repeat': ('Repeat press, reheat and fold three times: nine operations. Unfinished work keeps its saved progress.', 'プレス・再加熱・折り返しを3周、計9工程行います。途中で保管しても進捗は残ります。', '锻压、再加热和折叠重复3轮，共9步。中途储存会保留进度。'),
            'output': ('Collect one steel ingot. Only the final fold has a 25% chance of extra slag; leave room for both.', '鋼鉄インゴットを1個回収します。最後の折り返しでだけ25%のスラグが別に出るので、両方の収納を用意します。', '回收1个钢锭。只有最后一次折叠有25%的概率额外产出炉渣，请为两者留出空间。'),
        },
        'fluorite_sources': {
            'title': ('Make fluorite with fans or mixing', '送風か混合で蛍石を作る', '用风扇或搅拌制取萤石'),
            'haunt': ('Blow a fan through soul fire or a soul campfire onto diorite: one diorite becomes one fluorite.', '魂の炎か魂の焚き火を通した風を閃緑岩に当てます。閃緑岩1個から蛍石1個になります。', '将风扇气流穿过灵魂火或灵魂营火吹向闪长岩，1个闪长岩变成1个萤石。'),
            'mix': ('Alternatively, put one bone meal and one gravel into a Basin under a mechanical mixer.', '別の製法では、メカニカルミキサーの下の鉢へ骨粉1個と砂利1個を入れます。', '另一种方法是在机械搅拌器下的工作盆中加入1个骨粉和1个砂砾。'),
            'water': ('Pipe in 250 mB of water and mix without heating to obtain one fluorite.', '水250 mBを配管で入れ、加熱せずに混ぜると蛍石1個ができます。', '用管道输入250 mB水，无需加热，搅拌即可获得1个萤石。'),
        },
    }
    for index, lang in enumerate(('en_us', 'ja_jp', 'zh_cn')):
        path = A / f'lang/{lang}.json'
        data = json.loads(path.read_text())
        data.update({key: values[index] for key, values in entries.items()})
        for name, captions in lessons.items():
            for key, words in captions.items():data[f'create_refinery_n_reactors.ponder.shared.{name}.{key}'] = words[index]
            data[f'create_refinery_n_reactors.ponder.{name}.header'] = captions['title'][index]
        write(path, data)


if __name__ == '__main__':
    recipes()
    structures()
    models()
    languages()
