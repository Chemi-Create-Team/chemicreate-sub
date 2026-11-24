#!/usr/bin/env python3
"""One advancement for each multiblock facility, under a hub in the guide tab. Run from the repository root.

FacilityAchievements.java grants them by code (minecraft:impossible criterion "built") when a player stands near a finished structure. This script
writes advancement/facilities/*.json and its own language keys only, and fails if the Java table and FACILITIES differ or an icon block has no name.
"""
import json
import re
from pathlib import Path

R = Path(__file__).resolve().parents[1]
NS = 'create_refinery_n_reactors'
A = R / 'src/main/resources/assets' / NS
FOLDER = R / 'src/main/resources/data' / NS / 'advancement/facilities'
JAVA = R / 'src/main/java/net/createplant/event/FacilityAchievements.java'
LANGUAGES = ('en_us', 'ja_jp', 'zh_cn')
HUB = (('Plant manager', 'Finish a multiblock facility. Each one you build here is ticked off.'),
       ('プラントマネージャー', 'マルチブロック施設を完成させよう。建てた施設がここに並ぶ。'),
       ('工厂主管', '建成多方块设施。你建好的每一座都会记在这里。'))
# id: (icon block, frame, (title, description) in en, ja, zh)
FACILITIES = {
    'fractionating_column': ('fractionating_column', 'task', (('Layer cake', 'Stack a fractionating column.'), ('層になった原油', '分留塔を組み上げよう。'), ('千层原油', '建成一座分馏塔。'))),
    'ammonia_separator': ('ammonia_separator', 'task', (('Cold and clear', 'Build a gas-liquid separator.'), ('気と液のさかいめ', '気液分離器を組み上げよう。'), ('气液分界', '建成一台气液分离器。'))),
    'reaction_tower': ('reaction_tower', 'task', (('Tower of trays', 'Build a reaction tower with at least three layers.'), ('棚段の塔', '3段以上の反応塔を組み上げよう。'), ('塔盘之塔', '建成至少三层的反应塔。'))),
    'gas_holder': ('gas_holder', 'task', (('A lung for the plant', 'Raise a gas holder.'), ('工場の肺', 'ガスホルダーを建てよう。'), ('工厂之肺', '建起一座储气柜。'))),
    'lidded_vessel': ('lidded_vessel', 'task', (('Lid on', 'Close a lidded vessel.'), ('ふたをしめて', '蓋付き容器を完成させよう。'), ('盖上盖子', '建成一个带盖容器。'))),
    'absorption_tower': ('large_absorption_tower', 'task', (('Scrubbed clean', 'Stack an absorption tower.'), ('洗い落とす塔', '吸収塔を積み上げよう。'), ('洗净之塔', '建成一座吸收塔。'))),
    'heat_exchanger': ('heat_exchanger_controller', 'task', (('Heat, handed over', 'Assemble a heat exchanger.'), ('熱のバトン', '熱交換器を組み立てよう。'), ('热量交接', '建成一台换热器。'))),
    'large_heat_exchanger': ('large_heat_exchanger_controller', 'task', (('Bigger shell, more heat', 'Assemble a large heat exchanger.'), ('大きな胴、大きな熱', '大型熱交換器を組み立てよう。'), ('大壳大热', '建成一台大型换热器。'))),
    'jacketed_pipe': ('jacketed_pipe_controller', 'task', (('Coat for a pipe', 'Assemble a jacketed pipe.'), ('管のコート', 'ジャケット管を組み立てよう。'), ('管子的外套', '建成一段夹套管。'))),
    'cooling_tower': ('natural_draft_cooling_tower', 'goal', (('Plume on the horizon', 'Finish a natural-draft cooling tower.'), ('地平線の白煙', '自然通風式冷却塔を完成させよう。'), ('天际的白烟', '建成一座自然通风冷却塔。'))),
    'condenser': ('condenser_waterbox', 'task', (('Back to water', 'Build a condenser.'), ('もう一度、水へ', '復水器を建てよう。'), ('重归于水', '建成一台冷凝器。'))),
    'cold_box': ('cryogenic_cold_box_drive', 'goal', (('Ice palace', 'Assemble a cryogenic cold box.'), ('氷の宮殿', '極低温コールドボックスを組み上げよう。'), ('冰之宫殿', '建成一座低温冷箱。'))),
    'electrolysis_furnace': ('electrolysis_furnace_shell', 'task', (('Molten current', 'Close an electrolysis furnace.'), ('とけた電流', '電解炉を完成させよう。'), ('熔融的电流', '建成一座电解炉。'))),
    'electrolysis_cell': ('electrolysis_cell_controller', 'task', (('Cell block', 'Assemble an electrolysis cell.'), ('セルの並ぶ棟', '電解セルを組み立てよう。'), ('电解槽车间', '建成一组电解槽。'))),
    'vitrification': ('vitrification_melter', 'goal', (('Glass for a thousand years', 'Finish a vitrification melter.'), ('千年のガラス', 'ガラス固化炉を完成させよう。'), ('千年的玻璃', '建成一台玻璃固化炉。'))),
    'fuel_pool': ('fuel_pool', 'task', (('Blue glow', 'Fill out a spent-fuel pool.'), ('青い光', '使用済み燃料プールを完成させよう。'), ('幽蓝微光', '建成乏燃料池。'))),
    'fusion_reactor': ('fusion_reactor_controller', 'challenge', (('A star in a jar', 'Close the ring of a fusion reactor.'), ('瓶の中の星', '核融合炉のリングを閉じよう。'), ('瓶中之星', '合拢聚变反应堆的环。'))),
    'gasifier': ('steam_gasifier', 'task', (('Rock to gas', 'Finish a steam gasifier.'), ('石から気体へ', '蒸気ガス化炉を完成させよう。'), ('石变气', '建成一座蒸汽气化炉。'))),
    'pressure_pump': ('high_pressure_pump', 'task', (('Push harder', 'Assemble a high-pressure pump.'), ('もっと強く押せ', '高圧ポンプを組み立てよう。'), ('再使点劲', '建成一台高压泵。'))),
    'converter': ('converter_trunnion_bearing', 'goal', (('Rain of sparks', 'Hang a converter on its trunnions.'), ('火花の雨', '転炉を支軸にかけよう。'), ('火花如雨', '把转炉架在耳轴上。'))),
    'clarifier': ('clarifier_center_drive', 'task', (('Slow circles', 'Build a clarifier.'), ('ゆっくり回る', '沈殿槽を建てよう。'), ('缓缓转圈', '建成一座澄清池。'))),
    'flare': ('flare_tip', 'task', (('Last line of defence', 'Raise a flare stack.'), ('最後の砦', 'フレアスタックを建てよう。'), ('最后防线', '建起一座火炬塔。'))),
    'crane': ('crane_drive', 'task', (('Heavy lifting', 'Frame a gantry crane.'), ('重い物は任せろ', '門型クレーンを組もう。'), ('重活交给我', '搭起一台龙门吊。'))),
    'quarry': ('quarry_drive', 'task', (('Open pit', 'Frame a quarry.'), ('露天掘り', '採石場を組もう。'), ('露天开采', '搭起一座采石场。'))),
    'reactor_control_room': ('reactor_control_console', 'goal', (('Eyes on every dial', 'Fit out a reactor control room.'), ('すべての計器に目を', '原子炉制御室を整えよう。'), ('盯紧每个仪表', '建成反应堆控制室。'))),
}


def write(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')


def advancement(parent, icon, frame, key):
    return {'display': {'icon': {'id': icon}, 'title': {'translate': key}, 'description': {'translate': key + '.description'}, 'frame': frame,
                        'show_toast': True, 'announce_to_chat': False},
            'parent': parent, 'criteria': {'built': {'trigger': 'minecraft:impossible'}}, 'requirements': [['built']]}


def main():
    ids = re.findall(r'of\("(\w+)"', JAVA.read_text())
    assert sorted(ids) == sorted(FACILITIES), set(ids) ^ set(FACILITIES)
    lang = json.loads((A / 'lang/en_us.json').read_text(encoding='utf-8'))
    for old in FOLDER.glob('*.json'): old.unlink()
    write(FOLDER / 'hub.json', advancement(f'{NS}:guide/root', f'{NS}:fractionating_column', 'goal', f'advancement.{NS}.facility.hub'))
    for name, (icon, frame, _) in FACILITIES.items():
        assert f'block.{NS}.{icon}' in lang, icon
        write(FOLDER / f'{name}.json', advancement(f'{NS}:facilities/hub', f'{NS}:{icon}', frame, f'advancement.{NS}.facility.{name}'))
    for index, code in enumerate(LANGUAGES):
        path = A / 'lang' / f'{code}.json'
        kept = [line for line in path.read_text(encoding='utf-8').rstrip().splitlines() if not line.startswith(f'  "advancement.{NS}.facility.')]
        assert kept[-1] == '}'
        pairs = {'hub': HUB[index], **{name: text[index] for name, (_, _, text) in FACILITIES.items()}}
        lines = [f'  {json.dumps(f"advancement.{NS}.facility.{name}", ensure_ascii=False)}: {json.dumps(title, ensure_ascii=False)},\n'
                 f'  {json.dumps(f"advancement.{NS}.facility.{name}.description", ensure_ascii=False)}: {json.dumps(text, ensure_ascii=False)}'
                 for name, (title, text) in pairs.items()]
        kept[-2] = kept[-2].rstrip(',') + ','
        path.write_text('\n'.join(kept[:-1] + [',\n'.join(lines), '}']) + '\n', encoding='utf-8')
        json.loads(path.read_text(encoding='utf-8'))


if __name__ == '__main__':
    main()
