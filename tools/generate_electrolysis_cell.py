#!/usr/bin/env python3
"""Export the explicitly requested realistic electrolyser hardware, recipes and translations."""
from pathlib import Path
import json
from electrolysis_hardware import generate

ROOT = Path(__file__).resolve().parents[1] / 'src/main/resources'
MOD = 'create_refinery_n_reactors'
A = ROOT / 'assets' / MOD


def write(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    raw = path.read_bytes().decode('utf-8') if path.exists() else ''
    newline = '\r\n' if '\r\n' in raw else '\n'
    path.write_bytes((json.dumps(value, indent=2, ensure_ascii=False).replace('\n', newline) + newline).encode('utf-8'))


def main():
    generate(A, write)
    recipes = {
        'electrolysis_cell': (['IGI', 'CBC', 'IGI'], {'I': MOD + ':stainless_steel_sheet', 'G': 'minecraft:glass', 'C': 'create:copper_sheet', 'B': 'create:basin'}, 4),
        'electrolysis_cell_controller': (['IRI', 'CTC', 'IRI'], {'I': 'create:iron_sheet', 'R': 'minecraft:redstone', 'C': 'create:copper_sheet', 'T': 'create:electron_tube'}, 4),
        # A gearbox on a frame, with copper bus bars for the plates.
        'electrolysis_electrode_head': (['ISI', 'CAC'], {'I': MOD + ':stainless_steel_sheet', 'S': 'create:shaft', 'C': 'create:copper_sheet', 'A': 'create:andesite_casing'}, 2),
    }
    for name, (pattern, keys, count) in recipes.items():
        write(ROOT / f'data/{MOD}/loot_table/blocks/{name}.json', {'type': 'minecraft:block', 'pools': [{
            'rolls': 1, 'entries': [{'type': 'minecraft:item', 'name': MOD + ':' + name}],
            'conditions': [{'condition': 'minecraft:survives_explosion'}]}]})
        write(ROOT / f'data/{MOD}/recipe/{name}.json', {'type': 'minecraft:crafting_shaped', 'category': 'misc',
            'pattern': pattern, 'key': {k: {'item': v} for k, v in keys.items()}, 'result': {'id': MOD + ':' + name, 'count': count}})
    for tag in ('minecraft/tags/block/mineable/pickaxe', 'minecraft/tags/block/needs_iron_tool', 'create/tags/block/non_movable'):
        path = ROOT / 'data' / (tag + '.json')
        data = json.loads(path.read_text()) if path.exists() else {'replace': False, 'values': []}
        data['values'] = [v for v in data['values'] if v != MOD + ':electrolyzer_lid']
        for name in recipes:
            if MOD + ':' + name not in data['values']: data['values'].append(MOD + ':' + name)
        write(path, data)
    for path in A.rglob('*electrolyzer_lid*'):
        if path.is_file(): path.unlink()
    for path in (ROOT / f'data/{MOD}').rglob('*electrolyzer_lid*'):
        if path.is_file(): path.unlink()
    strings = {
        f'block.{MOD}.electrolysis_cell': ('Electrolysis Cell', '電解セル', '电解单元'),
        f'block.{MOD}.electrolysis_cell_controller': ('Electrolysis Cell Controller', '電解セルコントローラー', '电解单元控制器'),
        f'jei.{MOD}.cell_electrolysis': ('Filter-press electrolysis', 'フィルタープレス型電解槽', '压滤式电解'),
        f'block.{MOD}.electrolysis_electrode_head': ('Electrode Head', '電極ヘッド', '电极吊架'),
        f'jei.{MOD}.note.electrolysis': ('One plate for %s ticks, %s kJ. Feed from below and wire the two terminals on top of the panel.', '極板1枚・%s tick、%s kJ。底面から給液し、制御盤の上の2端子へ給電します。', '一片电极运行%s tick消耗%s kJ。从底部供液并连接控制器顶部的两个端子。'),
        f'jei.{MOD}.note.electrolysis_fluoride': ('One plate for %s ticks, %s kJ. Purge the feed before switching to hydrogen fluoride.', '極板1枚・%s tick、%s kJ。フッ化水素へ切り替える前に原料を抜き切ります。', '一片电极运行%s tick消耗%s kJ。换用氟化氢前须排空原料。'),
        f'jei.{MOD}.note.electrolysis_sulfurous': ('One plate for %s ticks, %s kJ: the hybrid sulfur cell. Hydrogen leaves the cathode side; the sulfuric acid leaves the far end.', '極板1枚・%s tick、%s kJ（ハイブリッド硫黄法）。水素は陰極側の側面、硫酸は奥の端面から出ます。', '一片电极运行%s tick消耗%s kJ（混合硫法）。氢气从阴极侧排出，硫酸从远端排出。'),
        f'jei.{MOD}.note.electrolysis_brine': ('One plate for %s ticks, %s kJ. The far end exports soda; the two long sides export hydrogen and chlorine.', '極板1枚・%s tick、%s kJ。奥の端面から苛性ソーダ、長い両側面から水素と塩素を回収します。', '一片电极运行%s tick消耗%s kJ。远端端面排烧碱，两条长边侧面分别排氢和氯。'),
        'electrolysis.status.structure': ('Incomplete: a row of 1–8 cells, an electrode head on every cell, two panel units stacked at one end', '未形成：一列1～8マスのセル、各セルに電極ヘッド、片端に2段の制御盤を1組', '未成型：一列1～8格电解单元，每格一个电极吊架，一端叠放两段控制器'),
        'electrolysis.status.running': ('Electrolysing', '電気分解中', '电解中'),
        'electrolysis.status.stopped': ('Stopped: press START on the upper-left panel', '停止：左上区画のSTARTボタンを押してください', '停止：按左上区域的START按钮'),
        'electrolysis.status.redstone': ('Stopped by redstone signal', 'レッドストーン信号で停止中', '红石信号停机'),
        'electrolysis.status.raised': ('Interlock: an electrode head is raised or rising; press START to lower every head', 'インターロック：上がっている電極ヘッドがあります。STARTを押すと全部のヘッドが下ります', '联锁：有电极吊架处于升起或上升中；按START降下全部吊架'),
        'electrolysis.status.lowering': ('Lowering the electrode plates', '電極板を下ろしています', '正在降下电极'),
        'electrolysis.status.plates': ('Waiting for electrode plates: load them into the raised heads', '電極板待ち：上がっているヘッドに入れてください', '等待电极：装入升起的吊架'),
        'electrolysis.status.feed': ('Waiting for feed solution', '原料溶液待ち', '等待原料溶液'),
        'electrolysis.status.power': ('Waiting for electrical work: check both CEE wires on top of the upper panel unit', '電力不足：上段の制御盤の上のCEEの2本の電線を確認してください', '电力不足：检查上段控制器顶部的两根CEE电线'),
        'electrolysis.status.output': ('Product buffer full: pump out both gases and soda', '製品満杯：両方の気体と苛性ソーダを搬出してください', '产物满载：排出两路气体及烧碱'),
        'electrolysis.status.idle': ('Batch complete', 'バッチ完了', '批次完成'),
        'electrolysis.electric': ('%s V · %s A · %s kW', '%s V・%s A・%s kW', '%s V · %s A · %s kW'),
        'electrolysis.rate': ('Gas %s/%s · soda %s mB/t · %s plates · %s%%', '気体%s/%s・ソーダ%s mB/t・極板%s枚・%s%%', '气体%s/%s · 烧碱%s mB/t · %s片电极 · %s%%'),
        'electrolysis.storage': ('Feed %s · cathode %s · anode %s · soda %s mB', '原料%s・陰極%s・陽極%s・苛性ソーダ%s mB', '原料%s · 阴极%s · 阳极%s · 烧碱%s mB'),
        'electrolysis.plates': ('Electrode plates: %s / %s', '電極板：%s / %s枚', '电极：%s / %s片'),
        'electrolysis.head': ('Electrode head: %s', '電極ヘッド：%s', '电极吊架：%s'),
        'electrolysis.head.up': ('raised', '上', '升起'), 'electrolysis.head.down': ('lowered', '下', '降下'), 'electrolysis.head.moving': ('moving', '移動中', '移动中'),
        'electrolysis.head.lowered': ('The plates are down: press STOP to raise them first', '電極板が下りています。先にSTOPで上げてください', '电极已降下：请先按STOP升起'),
    }
    for key, text in {
        'electrolysis_voltage': ('Cell voltage (V)', '電解槽の電圧（V）', '电解槽电压（V）'),
        'electrolysis_current': ('Cell current (A)', '電解槽の電流（A）', '电解槽电流（A）'),
        'electrolysis_plates': ('Electrode plates', '電極板の枚数', '电极数量'),
        'electrolysis_limit': ('Cell load (%)', '電解槽の出力設定（%）', '电解槽负载（%）'),
        'electrolysis_rate': ('Electrolysis cycles/t', '電解回数/t', '电解周期/t'),
        'electrolysis_cathode': ('Cathode gas (mB/t)', '陰極ガス生成量（mB/t）', '阴极气体产量（mB/t）'),
        'electrolysis_anode': ('Anode gas (mB/t)', '陽極ガス生成量（mB/t）', '阳极气体产量（mB/t）'),
        'electrolysis_soda': ('Caustic soda (mB/t)', '苛性ソーダ生成量（mB/t）', '烧碱产量（mB/t）'),
    }.items(): strings[f'display.{MOD}.{key}'] = text
    retired = ('electrolysis.status.hatch', 'electrolysis.hatch', 'electrolysis.open', 'electrolysis.closed')
    for lang, index in (('en_us', 0), ('ja_jp', 1), ('zh_cn', 2)):
        path = A / 'lang' / (lang + '.json');data = json.loads(path.read_text())
        for key in retired: data.pop(key, None)
        for key, value in strings.items(): data[key] = value[index]
        write(path, data)


if __name__ == '__main__': main()
