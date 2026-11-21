#!/usr/bin/env python3
"""Japanese and Chinese names for the bundled dependencies' blocks, items and fluids that ship only in English
(Create: Electro Energetics and Create Diesel Generators). Translation keys are global, so this mod's language files carry them.
Run from the repository root."""
import json
from pathlib import Path

LANG = Path(__file__).resolve().parents[1] / 'src/main/resources/assets/create_refinery_n_reactors/lang'
COLOURS = {
    'white': ('白色', '白色'), 'orange': ('橙色', '橙色'), 'magenta': ('赤紫色', '品红色'), 'light_blue': ('空色', '淡蓝色'),
    'yellow': ('黄色', '黄色'), 'lime': ('黄緑色', '黄绿色'), 'pink': ('桃色', '粉红色'), 'gray': ('灰色', '灰色'),
    'light_gray': ('薄灰色', '淡灰色'), 'cyan': ('青緑色', '青色'), 'purple': ('紫色', '紫色'), 'blue': ('青色', '蓝色'),
    'brown': ('茶色', '棕色'), 'green': ('緑色', '绿色'), 'red': ('赤色', '红色'), 'black': ('黒色', '黑色'),
}
E, D = 'electroenergetics', 'createdieselgenerators'
NAMES = {
    f'fluid.{E}.plant_oil': ('植物油', '植物油'), f'fluid.{E}.transformer_oil': ('変圧器油', '变压器油'),
    f'item.{E}.plant_oil_bucket': ('植物油入りバケツ', '植物油桶'), f'item.{E}.transformer_oil_bucket': ('変圧器油入りバケツ', '变压器油桶'),
    f'block.{D}.andesite_girder': ('安山岩の桁', '安山岩梁'), f'block.{D}.bulk_fermenter': ('大型発酵槽', '大型发酵罐'),
    f'block.{D}.burner': ('バーナー', '燃烧器'), f'block.{D}.chemical_turret': ('化学タレット', '化学炮塔'),
    f'block.{D}.chip_wood_beam': ('木片の梁', '木屑梁'), f'item.{D}.entity_filter': ('エンティティフィルター', '实体过滤器'),
    f'item.{D}.hammer': ('ハンマー', '锤子'), f'item.{D}.mold': ('鋳型', '模具'), f'block.{D}.oil_barrel': ('オイル樽', '油桶'),
    f'block.{D}.sheet_metal_panel': ('板金パネル', '金属板面板'), f'item.{D}.track_layers_bag': ('線路敷設袋', '铺轨袋'),
    f'item.{D}.wire_cutters': ('ワイヤーカッター', '剪线钳'),
    f'block.{E}.accumulator': ('蓄電池', '蓄电池'), f'block.{E}.alternator_brushes': ('発電機ブラシ', '发电机电刷'),
    f'block.{E}.alternator_rotor': ('発電機ローター', '发电机转子'), f'block.{E}.ammeter': ('電流計', '电流表'),
    f'block.{E}.broken_bulb': ('切れた電球', '损坏的灯泡'), f'block.{E}.broken_fuse': ('切れたヒューズ', '熔断的保险丝'),
    f'block.{E}.bulb': ('電球', '灯泡'), f'block.{E}.buzzer': ('ブザー', '蜂鸣器'), f'block.{E}.capacitor': ('コンデンサー', '电容器'),
    f'block.{E}.catenary_holder': ('架線ホルダー', '接触网支架'), f'item.{E}.clamp_meter': ('クランプメーター', '钳形表'),
    f'item.{E}.commutator': ('整流子', '换向器'), f'block.{E}.concrete_pole': ('コンクリート柱', '混凝土电杆'),
    f'block.{E}.connector': ('コネクター', '连接器'), f'block.{E}.converter': ('変換器', '变流器'),
    f'item.{E}.copper_wire': ('銅線', '铜线'), f'item.{E}.copper_wire_spool': ('銅線のスプール', '铜线卷'),
    f'block.{E}.creative_battery': ('クリエイティブ電池', '创造电池'), f'block.{E}.creative_resistor': ('クリエイティブ抵抗器', '创造电阻器'),
    f'item.{E}.creative_wire_spool': ('クリエイティブ電線スプール', '创造线卷'), f'block.{E}.current_transformer': ('変流器', '电流互感器'),
    f'block.{E}.cut_off_switch': ('遮断スイッチ', '切断开关'), f'block.{E}.diode': ('ダイオード', '二极管'),
    f'block.{E}.double_connector': ('2連コネクター', '双连接器'), f'block.{E}.double_switch': ('2連スイッチ', '双开关'),
    f'item.{E}.duplex_wire_spool': ('2芯線のスプール', '双芯线卷'), f'block.{E}.duplex_wire_termination': ('2芯線の端末', '双芯线终端'),
    f'block.{E}.electric_pump': ('電動ポンプ（Electro Energetics）', '电动泵（Electro Energetics）'),
    f'block.{E}.electric_shock_sign': ('感電注意の標識', '触电警告标志'), f'block.{E}.electrical_panel': ('配電盤', '配电盘'),
    f'item.{E}.electrum_wire': ('エレクトラム線', '琥珀金线'), f'item.{E}.electrum_wire_spool': ('エレクトラム線のスプール', '琥珀金线卷'),
    f'block.{E}.emergency_stop_button': ('非常停止ボタン', '紧急停止按钮'), f'item.{E}.empty_spool': ('空のスプール', '空线轴'),
    f'block.{E}.energy_meter': ('電力量計', '电能表'), f'block.{E}.frequency_meter': ('周波数計', '频率表'),
    f'block.{E}.fuse': ('ヒューズ', '保险丝'), f'block.{E}.fuse_holder': ('ヒューズホルダー', '保险丝座'),
    f'item.{E}.glass_insulator_segment': ('ガラス碍子の部品', '玻璃绝缘子段'), f'item.{E}.glass_insulator_spool': ('ガラス碍子のスプール', '玻璃绝缘子卷'),
    f'block.{E}.ground_rod': ('接地棒', '接地棒'), f'block.{E}.grounding_sign': ('接地の標識', '接地标志'),
    f'item.{E}.hanging_glass_insulator': ('吊り下げガラス碍子', '悬挂式玻璃绝缘子'), f'item.{E}.heavily_insulated_wire': ('厚い被覆の電線', '重绝缘线'),
    f'item.{E}.heavily_insulated_wire_spool': ('厚い被覆の電線のスプール', '重绝缘线卷'), f'block.{E}.high_voltage_capacitor': ('高圧コンデンサー', '高压电容器'),
    f'block.{E}.high_voltage_sign': ('高電圧の標識', '高压标志'), f'block.{E}.high_voltage_switch': ('高圧スイッチ', '高压开关'),
    f'item.{E}.incomplete_miniature_circuit_breaker': ('未完成の配線用遮断器', '未完成的小型断路器'),
    f'item.{E}.incomplete_transformer_core': ('未完成の変圧器鉄心', '未完成的变压器铁芯'), f'block.{E}.indicator_bulb': ('表示灯', '指示灯'),
    f'block.{E}.inductor': ('インダクター', '电感器'), f'item.{E}.insulated_wire': ('被覆電線', '绝缘线'), f'block.{E}.insulator': ('碍子', '绝缘子'),
    f'item.{E}.iron_bus_spool': ('鉄の母線のスプール', '铁母线卷'), f'item.{E}.iron_rail_spool': ('鉄の給電レールのスプール', '铁轨线卷'),
    f'item.{E}.iron_wire': ('鉄線', '铁线'), f'item.{E}.iron_wire_spool': ('鉄線のスプール', '铁线卷'), f'item.{E}.iron_wire_strand': ('鉄の素線', '铁线股'),
    f'item.{E}.linemans_stick': ('操作棒', '绝缘操作杆'), f'block.{E}.magnet': ('磁石', '磁铁'),
    f'item.{E}.miniature_circuit_breaker': ('配線用遮断器', '小型断路器'), f'block.{E}.momentary_switch': ('押しボタンスイッチ', '点动开关'),
    f'block.{E}.pantograph': ('パンタグラフ', '受电弓'), f'block.{E}.pole_mount': ('電柱の取付金具', '电杆支架'),
    f'block.{E}.potentiometer': ('可変抵抗器', '电位器'), f'block.{E}.quad_connector': ('4連コネクター', '四连接器'),
    f'block.{E}.radiator_panel': ('放熱パネル', '散热板'), f'block.{E}.rail_contact_shoe': ('集電靴', '集电靴'),
    f'block.{E}.redstone_potentiometer': ('レッドストーン可変抵抗器', '红石电位器'), f'block.{E}.redstone_relay': ('レッドストーンリレー', '红石继电器'),
    f'block.{E}.redstone_variac': ('レッドストーン可変変圧器', '红石调压器'), f'block.{E}.relay': ('リレー', '继电器'),
    f'block.{E}.resistive_heater': ('抵抗加熱器', '电阻加热器'), f'block.{E}.resistor': ('抵抗器', '电阻器'), f'block.{E}.stator': ('固定子', '定子'),
    f'block.{E}.sulfur_hexafluoride_breaker': ('六フッ化硫黄遮断器', '六氟化硫断路器'), f'block.{E}.synchroscope': ('同期検定器', '同步表'),
    f'block.{E}.three_phase_alternator_brushes': ('三相発電機ブラシ', '三相发电机电刷'), f'block.{E}.transformer': ('変圧器', '变压器'),
    f'block.{E}.transformer_core': ('変圧器の鉄心', '变压器铁芯'), f'item.{E}.transformer_core_lamination': ('変圧器の鉄心板', '变压器铁芯片'),
    f'block.{E}.tri_polar_energy_meter': ('三相電力量計', '三相电能表'), f'block.{E}.triple_connector': ('3連コネクター', '三连接器'),
    f'block.{E}.variac': ('可変変圧器', '调压器'), f'block.{E}.voltage_regulator': ('電圧調整器', '稳压器'), f'block.{E}.voltmeter': ('電圧計', '电压表'),
    f'item.{E}.wire_damper': ('電線ダンパー', '防振锤'), f'item.{E}.wire_spool': ('電線のスプール', '线卷'),
}
for colour, (ja, zh) in COLOURS.items():
    NAMES[f'block.{E}.{colour}_electric_motor'] = (f'電動機（{ja}）', f'电动机（{zh}）')
    NAMES[f'block.{E}.{colour}_electrical_panel'] = (f'配電盤（{ja}）', f'配电盘（{zh}）')
    NAMES[f'item.{D}.{colour}_cement_bucket'] = (f'{ja}のセメント入りバケツ', f'{zh}水泥桶')

for index, code in enumerate(('ja_jp', 'zh_cn')):
    path = LANG / f'{code}.json'
    text = path.read_text(encoding='utf-8')
    strings = json.loads(text)
    lines = text.rstrip('\n').split('\n')
    added = [f'  {json.dumps(key, ensure_ascii=False)}: {json.dumps(names[index], ensure_ascii=False)},' for key, names in sorted(NAMES.items()) if key not in strings]
    for key, names in NAMES.items():
        if key in strings: strings[key] = names[index]
    if added:
        lines[-2] = lines[-2] if lines[-2].endswith(',') else lines[-2] + ','
        lines[-1:-1] = added
        lines[-2] = lines[-2].rstrip(',')
    path.write_text('\n'.join(lines) + '\n', encoding='utf-8')
    json.loads(path.read_text(encoding='utf-8'))
    print(code, len(added), 'added')
