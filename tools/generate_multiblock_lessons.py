#!/usr/bin/env python3
"""Assembly lesson for the existing helium cold box; reuses its production-line fixture."""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / 'src/main/resources/assets/create_refinery_n_reactors'
TEXT = {
    'title': ('Assembling and supplying a helium cold box', 'ヘリウム液化器の組立と接続', '氦液化冷箱的组装与连接'),
    'base': ('Build the bottom 3×3 layer from steel blocks. The jacket is three layers high.',
             '鋼鉄ブロックで3×3の底を作ります。外殻は3段の高さです。',
             '用钢块铺3×3底层，外壳共三层。'),
    'core': ('Place one cryogenic regenerator at the exact centre. Complete the other 26 positions with steel blocks.',
             'ちょうど中央に極低温再生器を1個置き、残る26マスを鋼鉄ブロックで埋めます。',
             '正中心放一个低温回热器，其余26格填钢块。'),
    'drive': ('Fit the cold-box drive outside a face centre, with its shaft pointing outward. Feed at least 32 RPM through that shaft.',
              '外側の面中央に液化器の駆動部を付け、軸を外へ向けます。この軸へ32 RPM以上を入力します。',
              '在外表面中心安装冷箱驱动部，轴朝外，并从此轴输入至少32 RPM。'),
    'gas': ('Pump helium into the drive’s TOP port. The first 1,000 mB remains as circulating helium, before any liquid is produced.',
            '駆動部の上面へヘリウムを送ります。最初の1,000 mBは循環用として保持され、その後に液化が始まります。',
            '向驱动部顶面泵入氦，最初1,000 mB保留为循环工质，之后才开始液化。'),
    'water': ('Viewed from the drive front, supply cooling water at LEFT and drain warm water at RIGHT. The inlet must stay below 80 °C.',
              '駆動部の正面から見て左へ冷却水を送り、右から温水を抜きます。入口の水は80℃未満に保ちます。',
              '从驱动部正面看，左进冷却水，右排温水。进水必须低于80℃。'),
    'liquid': ('Extract liquid helium from the drive’s BOTTOM port into cryogenic pipes and tanks. Cooling down to 4.2 K consumes shaft work first.',
               '駆動部の下面から極低温配管・タンクへ液体ヘリウムを搬出します。まず4.2 Kまで予冷するための回転仕事を消費します。',
               '从驱动部底面用低温管道和储罐收集液氦，首先消耗轴功预冷到4.2 K。'),
    'check': ('Read temperature and the stop reason with goggles. Missing helium, warm or blocked cooling water, low speed, full output, or redstone stops the process.',
              'ゴーグルで温度と停止理由を確認します。ヘリウム不足・冷却水の高温や詰まり・低回転・製品満杯・信号で停止します。',
              '用护目镜查看温度及停止原因。缺氦、冷却水过热或堵塞、转速低、出料满载及红石均会停机。'),
}
EXTRA = {
    'condenser_loop.bundle': ('Build the condenser floor, then its five tube-bundle sections. The shell encloses these tubes; the front waterbox controls the whole unit.',
                              '復水器の底を作り、伝熱管束を5区間並べます。外殻で管束を包み、手前の水室が装置全体を管理します。',
                              '先铺凝汽器底层，再放五段管束，外壳包住管束，前方水室控制整台设备。'),
    'fusion_maintenance.reactor': ('First assemble the 11×11 tokamak using its own lesson. The crane is a separate structure above the service hatch.',
                                   'まず専用Ponderどおりに11×11のトカマクを組みます。保守ハッチの上へ、別構造のクレーンを建てます。',
                                   '先按专用Ponder组装11×11托卡马克，再在检修口上方搭建独立起重机。'),
}


def main():
    target = ASSETS / 'ponder/plant/helium_cold_box.nbt'
    target.write_bytes((ROOT / 'tools/data/manufacturing/helium_cold_box.nbt').read_bytes())
    for i, language in enumerate(('en_us', 'ja_jp', 'zh_cn')):
        path = ASSETS / 'lang' / (language + '.json')
        strings = json.loads(path.read_text())
        for suffix, values in TEXT.items():
            strings['create_refinery_n_reactors.ponder.shared.helium_cold_box.' + suffix] = values[i]
        for suffix, values in EXTRA.items():
            strings['create_refinery_n_reactors.ponder.shared.' + suffix] = values[i]
        strings['create_refinery_n_reactors.ponder.helium_cold_box.header'] = TEXT['title'][i]
        path.write_text(json.dumps(strings, ensure_ascii=False, indent=2) + '\n')
    path = ASSETS / 'ponder/tutorials.json'
    tutorials = json.loads(path.read_text())
    for tutorial in tutorials:
        name = tutorial['id']
        if name.startswith('tubular_boiler'):
            width = 3 if name.endswith('3x3') else 2 if name.endswith('2x2') else 1
            boxes = [(2, 2, 3, 4, width + 1, width + 2), (1, 2, 3, 1, 2, 3),
                     (2, 1, 3, 4, 1, width + 2), (5, 2, 3, 5, 2, 3)]
        elif name == 'electrolysis_furnace':
            boxes = [(3, 1, 3, 7, 3, 7), (5, 1, 1, 8, 3, 2), None, None, None]
        elif name == 'industrial_dispatch':
            boxes = [(4, 1, 3, 4, 1, 4), (3, 1, 4, 3, 1, 4), None, (4, 2, 4, 4, 2, 4)]
        else:
            continue
        for step, box in zip(tutorial['steps'], boxes):
            if box:
                step['reveal'] = list(box)
    path.write_text(json.dumps(tutorials, ensure_ascii=False, indent=2) + '\n')


if __name__ == '__main__':
    main()
