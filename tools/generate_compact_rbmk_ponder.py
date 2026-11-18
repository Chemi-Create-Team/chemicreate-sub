#!/usr/bin/env python3
"""Generate the compact RBMK storyboard and its English/Japanese/Chinese captions."""
import json
from pathlib import Path
import generate_petrochemical_ponder as ponder

ROOT = Path(__file__).resolve().parents[1]
A = ROOT / 'src/main/resources/assets/create_refinery_n_reactors'
NS = 'create_refinery_n_reactors:'


def inside(x, z, size):
    r = size / 2
    return (x + .5 - r) ** 2 + (z + .5 - r) ** 2 <= r * r


def generate():
    scene = ponder.Structure(9, 15)
    ports = {(0, 6, 4): ('biological_shield_drive', {'axis': 'x'}),
             (1, 6, 4): ('biological_shield_drive', {'axis': 'x'}),
             (6, 9, 2): ('biological_shield_coolant_outlet', {}),
             (7, 9, 2): ('biological_shield_coolant_outlet', {})}
    for x in range(9):
        for z in range(9):
            if not inside(x, z, 9): continue
            core = 2 <= x <= 6 and 2 <= z <= 6 and inside(x - 2, z - 2, 5)
            if core:
                for y in range(2, 5): scene.block(x, y, z, NS + 'reactor_feed_bundle')
                for y in range(5, 8):
                    nbt = [ponder.string('id', NS + 'reactor_fuel_channel'), ponder.named(3, 'CoreHeight', ponder.integer(3))]
                    scene.block(x, y, z, NS + 'reactor_fuel_channel', nbt=nbt)
                for y in range(8, 12): scene.block(x, y, z, NS + 'reactor_fuel_channel_tube')
                scene.block(x, 12, z, NS + 'reactor_top_cap')
            else:
                for y in range(2, 13):
                    if (x, y, z) == (8, 9, 2): continue
                    block, properties = ports.get((x, y, z), ('biological_shield', {}))
                    scene.block(x, y, z, NS + block, **properties)
    for x, y, z, sides in ((4, 1, 4, ('north', 'up')), (4, 1, 3, ('north', 'south')), (8, 9, 2, ('west',))):
        scene.block(x, y, z, 'create:fluid_pipe', waterlogged='false',
                    **{face: str(face in sides).lower() for face in ('north', 'south', 'east', 'west', 'up', 'down')})
    scene.block(3, 13, 4, 'create:display_link', facing='up')
    ponder.OUT = A / 'ponder/nuclear'
    scene.save('rbmk_compact_core')
    for locale, index in (('en_us', 0), ('ja_jp', 1), ('zh_cn', 2)):
        path = A / 'lang' / (locale + '.json')
        raw = path.read_text(encoding='utf-8'); newline = '\r\n' if '\r\n' in path.read_bytes().decode('utf-8') else '\n'
        language = json.loads(raw)
        for key, values in TEXTS.items():
            caption = CHINESE[key] if locale == 'zh_cn' else values[index]
            language[f'{NS[:-1]}.ponder.shared.rbmk_compact_core.{key}'] = caption.replace('%', ' percent' if locale == 'en_us' else '％')
        language[f'{NS[:-1]}.ponder.rbmk_compact_core.header'] = CHINESE['title'] if locale == 'zh_cn' else TEXTS['title'][index]
        path.write_bytes((json.dumps(language, ensure_ascii=False, indent=2).replace('\n', newline) + newline).encode('utf-8'))


TEXTS = {
    'title': ('Building a compact three-metre RBMK core', '燃料3マスの小型RBMK炉を組む'),
    'core': ('Use three active fuel-channel layers instead of seven. Keep the same 5–13 block footprint and two-block side shielding. Every column must use the same supported height: three or seven.',
             '燃料チャネルの有効部分を従来の7段から3段にします。平面5～13マスと側面シールド2マスは共通です。全列を同じ高さの3段か7段に揃えてください。'),
    'lower': ('Below the three-layer core, use one lower shield layer and two service layers, filled with feed bundles. The lower structure is three blocks high; connect a Create pipe to an exposed bundle.',
              '3段炉心の下は下部遮蔽1段と保守空間2段、合計3段を給水細管束で満たします。露出した細管束へCreateのパイプを直接つなぎます。'),
    'upper': ('Above the active core, use one shield layer and three upper tube layers. The cap is five blocks above the top active layer. The seven-block fuel assembly and its cap need an eleven-block-high complete reactor.',
              '有効炉心の上は遮蔽1段と上部細管3段です。炉心最上段から5マス上へキャップを置きます。燃料集合体は全長7マス、下部からキャップまでの炉体は全高11マスになります。'),
    'rods': ('Drive the biological-shield input. Rod controls and AZ-5 work as on the large reactor.',
             '回転はバイオシールド入力部へ入れます。制御棒とAZ-5の操作は大型炉と同じです。'),
    'fuel': ('Use the double-striped 5% rods in five- or seven-wide compact cores. They do not fit seven-layer cores.', '平面5・7マスの3段炉には、2本帯の5%燃料棒を使います。7段炉には装荷できません。'),
    'fuel_line': ('Further enrich the existing enriched gas in another centrifuge. Stir the resulting compact-grade gas with water and hydrogen into dioxide powder. Press it, fire the pellets with a Blaze Burner or furnace, and assemble them with cladding tubes.',
                  '濃縮済みの気体を別の遠心分離機でもう一段濃縮し、水・水素と撹拌して小型炉用二酸化ウラン粉末にします。プレスで固め、ブレイズバーナーやかまどで焼き、被覆管で集合体にします。'),
    'cooling': ('Keep real coolant supply and a water/steam outlet on the upper side. Use Create pipes and pumps, then separate the wet outlet in a gas-liquid separator. A shorter reactor still needs sufficient cooling and an unobstructed outlet.',
                '給水を保ち、水と蒸気の混相流は上部側面から出します。Createの配管・ポンプで運び、気液分離器へ送ります。短い炉でも十分な冷却と詰まらない出口が必要です。'),
    'scaling': ('With the same footprint, output is 3/7 of a seven-layer core. Check the rating with goggles.',
                '同じ平面なら出力は7段炉の3/7です。必要な出力はゴーグルで確認します。'),
    'readout': ('Check power and cooling with goggles. Remove all fuel and control rods before changing core height.',
                '出力と冷却はゴーグルで確認します。燃料部の高さを変える前に、燃料棒と制御棒をすべて回収します。'),
}

CHINESE = {
    'title': '搭建燃料区高3格的小型RBMK反应堆',
    'core': '将有效燃料通道从7层缩短到3层。平面仍为5至13格，侧面屏蔽厚2格。所有通道必须采用相同的3格或7格高度。',
    'lower': '3层堆芯下方用1层下部屏蔽和2层维护空间，共3层给水管束。将Create流体管直接连接到外露管束。',
    'upper': '有效堆芯上方为1层屏蔽和3层上部管束。顶盖位于最高燃料层上方5格。燃料组件总长7格，含下部结构和顶盖的总高度为11格。',
    'rods': '旋转动力接生物屏蔽输入端，控制棒及AZ-5操作与大型炉相同。',
    'fuel': '5或7格宽的三层小型炉使用双条纹5%燃料棒，七层炉不能装入。',
    'fuel_line': '将浓缩气体送入另一台离心机进一步浓缩，与水和氢气搅拌生成小型堆用二氧化铀粉末。用机械压力机压制，以烈焰人燃烧室或熔炉烧成芯块，再与包壳管组装。',
    'cooling': '保持实际给水，从上部侧面排出水汽混合物。用Create流体管和泵送入汽水分离器。缩短的反应堆仍需充足冷却和畅通的出口。',
    'scaling': '相同占地下出力为七层炉的3/7，额定值可用护目镜查看。',
    'readout': '用护目镜查看功率和冷却，改变燃料部高度前回收全部燃料棒与控制棒。',
}


if __name__ == '__main__':
    generate()
