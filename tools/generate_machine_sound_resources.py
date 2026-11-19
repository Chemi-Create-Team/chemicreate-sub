#!/usr/bin/env python3
"""Keep machine subtitles and the real-world sound-operation Ponder note reproducible."""
import json
from pathlib import Path

ASSETS = Path(__file__).resolve().parents[1] / 'src/main/resources/assets/create_refinery_n_reactors'
TEXTS = {
    'reactor_circulation': ('Reactor hums', '原子炉の運転音'),
    'fusion_drive': ('Fusion reactor hums', '核融合炉の運転音'),
    'turbine_high': ('High-pressure turbine spins', '高圧タービンが回転する'),
    'turbine_low': ('Low-pressure turbine spins', '低圧タービンが回転する'),
    'compressor': ('Compressor runs', '圧縮機が作動する'),
    'agitator': ('Vessel agitator spins', '撹拌槽の撹拌機が回転する', '搅拌釜的搅拌器旋转'),
    'hot_process': ('Process heater runs', '加熱器が作動する', '工艺加热器正在运行'),
    'fluid_process': ('Clarifier rake turns', '清澄槽のかき寄せ機が回る', '澄清槽刮泥机转动'),
    'blower': ('Cooling fans spin', '冷却ファンが回転する'),
    'crane_drive': ('Crane drive moves', 'クレーンの駆動部が動く'),
    'centrifuge': ('Gas centrifuge spins', 'ガス遠心分離機が高速回転する', '气体离心机高速旋转'),
    'cubic_press': ('Cubic press compresses', '立方体プレスが圧縮する', '立方压机正在加压'),
    'boiler': ('Boiler water boils', 'ボイラーの水が沸騰する', '锅炉中的水在沸腾'),
    'coldbox': ('Cryogenic cold box runs', '極低温分離装置が作動する', '深冷分离装置正在运行'),
    'shell_exchanger': ('Fluid runs through the shell-and-tube exchanger', 'シェル＆チューブ熱交換器に流体が流れる', '流体流过管壳式换热器'),
    'large_exchanger': ('Fluid rushes through the large heat exchanger', '大型熱交換器に流体が流れる', '流体流过大型换热器'),
    'jacket': ('Fluid circulates in the jacketed exchanger', 'ジャケット式熱交換器に流体が流れる', '流体在夹套式换热器内循环'),
    'absorber': ('Gas bubbles through the absorption vessel', '吸収槽でガスが泡立つ', '气体在吸收槽中鼓泡'),
    'packed_tower': ('Fluid trickles through the absorption tower', '吸収塔の内部を液体が流れる', '液体流过吸收塔'),
    'reaction_tower': ('Reaction tower processes gas', '反応塔の中を気体が通る', '气体通过反应塔'),
    'vertical_separator': ('Gas and liquid separate in the tower', '気液分離塔で液体が滴る', '液体在气液分离塔中滴落'),
    'separator_drum': ('Separator drum gurgles', '気液分離ドラムで液体が落ちる', '气液分离罐内液体咕噜作响'),
    'distillation': ('Distillation column bubbles', '蒸留塔が沸き立つ', '蒸馏塔内液体在沸腾'),
    'electric_furnace': ('Electric furnace arcs crackle', '電気炉のアークがはぜる', '电炉电弧噼啪作响'),
    'electrolysis': ('Electrolysis cells fizz', '電解槽で気泡がはじける', '电解槽内气泡破裂'),
    'cooling_tower': ('Water falls inside the cooling tower', '冷却塔の中を水が降る', '水在冷却塔中落下'),
    'condenser': ('Steam condenses in the condenser', '復水器で蒸気が凝縮する', '蒸汽在凝汽器内冷凝'),
    'waste_melter': ('Waste melter hums', '廃棄物溶融炉が低く唸る', '废料熔炉低鸣'),
    'solvent_extractor': ('Solvent extraction mixers turn', '溶媒抽出機が液を混ぜる', '溶剂萃取机正在搅拌'),
    'converter': ('Converter roars', '転炉の内部が轟く', '转炉内传来轰鸣'),
    'fuel_cell': ('Fuel cell generates power', '燃料電池の発電音', '燃料电池正在发电'),
    'fusion_crane': ('Fusion service crane moves', '核融合炉保守クレーンが動く', '聚变堆检修起重机移动'),
    'quarry': ('Quarry drills and moves', 'クァーリーの掘削機構が動く', '采石机正在掘进'),
    'gasifier': ('Gasifier churns', 'ガス化炉の内部でガスが流れる', '气化炉内气体流动'),
    'motor_generator': ('Motor-generator hums', '電動発電機が唸る', '电动发电机嗡鸣'),
    'flywheel': ('Flywheel spins', '大型フライホイールが回る', '大型飞轮旋转'),
    'vacuum_pump': ('Vacuum pump draws gas', '真空ポンプが排気する', '真空泵正在抽气'),
    'electric_pump': ('Electric pump runs', '電動ポンプが回る', '电动泵运转'),
    'material_pump': ('Mechanical pump runs', '機械式ポンプが回る', '机械泵运转'),
    'relief_scrubber': ('Relief scrubber washes gas', '排ガス洗浄塔で泡がはじける', '排气洗涤塔内气泡破裂'),
    'gas_holder': ('Gas holder walls slide', 'ガスホルダーの外筒が動く', '储气柜筒体升降'),
    'constructor': ('Facility constructor places blocks', '施設建設機がブロックを設置する', '设施建造机正在放置方块'),
    'dispatch': ('Dispatch machine handles cargo', '産業出荷機が荷物を処理する', '工业发货机正在处理货物'),
    'pressurised_gas': ('Pressurised gas rushes through a pipe', '加圧ガスが管の中を流れる', '加压气体掠过管道'),
    'high_pressure_gas': ('High-pressure gas whistles through a pipe', '高圧ガスが管の中でヒューと鳴る', '高压气体在管道内啸叫'),
}
NOTE = ('Adjust running sounds with the Blocks volume setting.',
        '駆動音は「ブロック」の音量設定で調整できます。',
        '运行声音可在“方块”音量中调整。')
for locale, index in (('en_us', 0), ('ja_jp', 1), ('zh_cn', 2)):
    path = ASSETS / 'lang' / (locale + '.json')
    raw = path.read_bytes().decode('utf-8')
    newline = '\r\n' if '\r\n' in raw else '\n'
    values = json.loads(raw)
    for name, words in TEXTS.items():
        if index < len(words):
            values['subtitles.create_refinery_n_reactors.machine.' + name] = words[index]
    values['create_refinery_n_reactors.ponder.shared.turbine_multiblock.operating_sound'] = NOTE[index]
    path.write_bytes((json.dumps(values, ensure_ascii=False, indent=2).replace('\n', newline) + newline).encode('utf-8'))
