#!/usr/bin/env python3
"""Native Ponder structures and three-language captions for the unified fractionation tower, and the stirred vessel the manufacturing lessons build on."""
import json
import struct
from pathlib import Path
from generate_petrochemical_ponder import Structure, OUT, NS, string, named, integer

A = Path(__file__).resolve().parents[1] / 'src/main/resources/assets/create_refinery_n_reactors'
TEMPLATE_DATA = Path(__file__).resolve().parent / 'data/manufacturing'


def save(scene, name, target=None):
    scene.save(name)
    target = target or A / 'ponder/plant' / (name + '.nbt')
    target.write_bytes((OUT / (name + '.nbt')).read_bytes())
    (OUT / (name + '.nbt')).unlink()


def rated_pipe(scene, x, y, z, material, *sides):
    scene.block(x, y, z, NS + material, **{side: str(side in sides).lower() for side in ('north', 'south', 'east', 'west', 'up', 'down')}, waterlogged='false')


def structures():
    scene = Structure(11, 9)
    scene.reactor(5, 2, 5, 'stirred', 3, ((x, z) for x in (-1, 0, 1) for z in (-1, 0, 1) if (x, z) != (0, 0)))
    scene.block(3, 2, 5, 'create:andesite_funnel', facing='west', extracting='false', waterlogged='false')
    scene.block(4, 2, 0, NS + 'gas_receiver', axis='z', bottom='true', top='true', shape='plain')
    rated_pipe(scene, 4, 2, 1, 'stainless_steel_pipe', 'north', 'south')
    scene.pump(4, 2, 2, 'south')
    rated_pipe(scene, 4, 2, 3, 'stainless_steel_pipe', 'north', 'south')
    scene.block(4, 1, 2, 'create:cogwheel', axis='z', waterlogged='false')
    scene.block(4, 1, 1, 'create:creative_motor', facing='south')
    for y in (2, 4):
        rated_pipe(scene, 4, y, 7, 'stainless_steel_pipe', 'north', 'south')
        scene.pump(4, y, 8, 'north' if y == 2 else 'south')
        scene.block(4, y, 9, NS + 'pressure_tank', bottom='true', top='true', shape='plain')
        scene.block(4, y - 1, 8, 'create:cogwheel', axis='z', waterlogged='false')
        scene.block(4, y - 1, 9, 'create:creative_motor', facing='north')
    rated_pipe(scene, 4, 5, 4, 'stainless_steel_pipe', 'up', 'down')
    scene.pump(4, 6, 4, 'up')
    scene.block(4, 7, 4, NS + 'gas_receiver', axis='z', bottom='true', top='true', shape='plain')
    scene.block(3, 6, 4, 'create:cogwheel', axis='y', waterlogged='false')
    scene.block(3, 5, 4, 'create:creative_motor', facing='up')
    scene.block(7, 3, 5, 'create:fluid_valve', facing='up', axis_along_first='false', enabled='true', waterlogged='false')
    save(scene, 'process_vessel', TEMPLATE_DATA / 'process_vessel.nbt')

    for name,height,cuts in (('fractionating_column',5,5),('fractionating_column_binary',2,2),('fractionating_column_air',14,3)):
        scene = Structure(17,height+3)
        packed = (8 << 38) | (13 << 12) | 1
        for y in range(height):
            for x in range(2):
                for z in range(2):
                    data=[string('id',NS+'fractionating_column'),named(3,'Width',integer(2)),named(3,'Height',integer(height))]
                    if x or y or z:data.append(named(4,'Controller',struct.pack('>q',packed)))
                    scene.block(8+x,y+1,13+z,NS+'fractionating_column',nbt=data,width='2',cell=str(z*3+x),
                        section='base' if y==0 else 'top' if y==height-1 else 'service' if y==1 else 'body',
                        inlet='1' if x==1 and z==0 and y==min(2,height-1) else '0',cut='0')
        inlet=1+min(2,height-1)
        for x in (10,12):rated_pipe(scene,x,inlet,13,'stainless_steel_pipe','east','west')
        scene.pump(11,inlet,13,'west');scene.block(13,inlet,13,NS+'stainless_steel_tank',bottom='true',top='true',shape='plain')
        scene.block(11,inlet-1,13,'create:cogwheel',axis='x',waterlogged='false')
        scene.block(12,inlet-1,13,'create:creative_motor',facing='west')
        layers=(13,9,0) if height==14 else tuple(range(height-1,-1,-1))
        for i,layer in enumerate(layers):
            y=layer+1;pump=11-2*i;tank=9-2*i
            for z in range(tank+1,13):
                if z!=pump:scene.block(8,y,z,'create:smart_fluid_pipe',face='floor',facing='north',waterlogged='false')
            scene.pump(8,y,pump,'north')
            scene.block(8,y,tank,NS+'stainless_steel_tank',bottom='true',top='true',shape='plain')
            scene.block(9,y,pump,'create:cogwheel',axis='z',waterlogged='false')
            scene.block(9,y,pump+1,'create:creative_motor',facing='north')
        save(scene,name)


TEXT = {
    'fractionating_column.mixtures': ('Cooled gas mixtures and reaction liquids can also be separated. Check JEI and goggles for feeds and outlet products.',
        '冷却した混合ガスや反応液も分離できます。原料と出口の対応はJEIとゴーグルで確認します。',
        '冷却后的混合气和反应液也可分离，原料及出口对应产物查看JEI和护目镜。'),
    'fractionating_column.air': ('Feed air at least at 6 bar and no hotter than 40 °C. Cool it after compression.',
        '空気は6 bar以上・40 °C以下にして送ります。圧縮後は先に冷却します。',
        '供气至少6 bar、不高于40 °C，压缩后先冷却。'),
    'fractionating_column.air_ports': ('Air mode uses three north outlets: liquid oxygen at layer 1, argon at layer 10, nitrogen at layer 14. Chemical mode uses up to five outlets instead; let the actual port shape guide the pipes.',
        '空気分離の北面出口は1段目の液体酸素、10段目のアルゴン、14段目の窒素です。化学分留では最大5口へ変わるため、実際のポート形状を見て配管します。',
        '空气模式仅用北面三个出口：第1层液氧、第10层氩、第14层氮；化学模式最多五口，请按实际接口形状配管。'),
}

TEXT.update({
 'fractionating_column.title': ('Build a five-layer crude fractionation tower','5段の原油分留塔を組む','搭建五层原油分馏塔'),
 'fractionating_column.base': ('Build every layer as a complete 2×2 square. The example is five layers high; 1×1 and 3×3 cannot operate.','各段を2×2の正方形に組みます。この原油用は高さ5段です。1×1や3×3では運転できません。','每层组装完整2×2正方形，原油示例高五层；1×1及3×3不能运行。'),
 'fractionating_column.build': ('Chemical fractionation uses 2×2 columns, with a minimum of two to five layers. No shaft or tray items are needed.','化学分留は2×2・必要な高さは2～5段です。回転動力や棚段アイテムは不要です。','化学分馏使用2×2塔，最低高度按配方为2～5层，无需传动轴或棚板物品。'),
 'fractionating_column.feed': ('Heat crude oil to at least 620 K. Pump it into the east face of layer 3. A two-layer tower instead takes feed at layer 2. The feed selects the process without a GUI.','原油を620 K以上へ加熱し、東面3段目からポンプで供給します。2段塔の入口は2段目です。入れた原料で工程が決まります。','将原油加热到至少620 K，经泵送入第三层东面。二层塔的入口在第二层。原料自动选择工艺。'),
 'fractionating_column.light': ('The north outlet of layer 5 produces gasoline; layer 4 produces naphtha. Keep each product in a separate pipe and tank.','北面5段目がガソリン、4段目がナフサです。留分ごとに別の配管・タンクへ送ります。','第五层北面出汽油，第四层出石脑油。各馏分使用独立管线和储罐。'),
 'fractionating_column.middle': ('Layer 3 draws diesel; layer 2 draws lubricating oil. Smart pipes keep adjacent outlets isolated instead of joining into a shared pipe.','3段目は軽油、2段目は潤滑油。隣り合う出口には軸が固定されたスマートパイプを使い、合流を避けます。','第三层出柴油，第二层出润滑油。相邻出口使用固定轴向的智能管道，避免混合。'),
 'fractionating_column.heavy': ('Collect heavy oil at layer one. Check JEI for product quantities.','1段目から重油を取り出します。生成量はJEIで確認できます。','第一层取出重油，产量查看JEI。'),
 'fractionating_column.empty': ('Drain all feed and every product before changing recipes. Filling one output buffer pauses the whole batch. Redstone at the bottom controller stops the tower.','原料を変える前に、原料と全留分を抜き切ります。出口満杯ではバッチ全体が待機し、最下段への赤石入力で停止します。','更换原料前排空原料和所有产物。任一出口满载会暂停整批；底部控制器接收红石停机。'),
 'fractionating_column.observe': ("Use goggles to check the required height and each outlet's contents.",'ゴーグルで必要な段数と各出口の中身を確認します。','用护目镜查看所需层数和各出口内容物。'),
 'fractionating_column_binary.title': ('A two-layer separation tower','2段の分離塔を使う','使用两层分离塔'),
 'fractionating_column_binary.build': ('Two products need only two layers. Fill a complete 2×2 cross-section at both levels; 1×1 and 3×3 cannot operate.','生成物が2種類なら2段で動きます。両段とも2×2の断面を埋めます。1×1や3×3では動きません。','两种产物只需两层。每层均铺满2×2截面；1×1及3×3不能运行。'),
 'fractionating_column_binary.feed': ('Pump cooled nitric-oxide mixture into the east face of layer 2. It separates without heating or rotational input.','冷却済み一酸化窒素混合物を、東面2段目へ供給します。加熱や回転入力は不要です。','将冷却的一氧化氮混合物泵入第二层东面。无需加热或旋转输入。'),
 'fractionating_column_binary.outputs': ('The upper north outlet draws nitric oxide, the lower one water. One 250 mB batch gives 100 mB nitric oxide and 150 mB water. Keep the lines isolated.','北面の上段から一酸化窒素、下段から水を回収します。250 mBあたり100 mBと150 mBです。配管は合流させません。','上层北面出一氧化氮，下层出水。每批250 mB产出100 mB一氧化氮和150 mB水。管线保持独立。'),
 'fractionating_column_air.title': ('Air separation still needs fourteen layers','空気分離は14段で組む','空气分离仍需十四层'),
 'fractionating_column_air.build': ('Build a 2×2, fourteen-layer air column. Short towers cannot separate air.','空気分離は2×2・14段で組みます。短い塔では動きません。','空气分离须搭建2×2、十四层塔，短塔无法工作。'),
 'fractionating_column_air.feed': ('Use the filter, reciprocating compressor and radiator upstream. Feed air at 6 bar or more and no warmer than 40 °C into the east face of layer 3.','上流でフィルター・往復式圧縮機・ラジエーターを使います。6 bar以上・40 ℃以下の空気を東面3段目へ送ります。','上游使用过滤器、往复式压缩机和散热器，将不低于6 bar、不高于40 ℃的空气送入第三层东面。'),
 'fractionating_column_air.outputs': ('North outlets give nitrogen at layer 14, argon at 10 and liquid oxygen at 1. Collect them separately.','北面の14段目が窒素、10段目がアルゴン、1段目が液体酸素です。別々の配管で回収します。','北面14层出氮气、10层出氩气、1层出液氧，分别接管回收。'),
})


RUNTIME = {
    'block.create_refinery_n_reactors.fractionating_column': ('Fractionation Tower', '分留塔', '分馏塔'),
    'jei.create_refinery_n_reactors.fractionating_column': ('Fractionation Tower', '分留塔', '分馏塔'),
    'jei.create_refinery_n_reactors.haber_bosch': ('Ammonia Synthesis — Iron Catalyst Bed', 'アンモニア合成（鉄触媒床）', '合成氨（铁催化床）'),
    'jei.create_refinery_n_reactors.note.fractionation': ('%s ticks per batch × tower area. 2×2 only, minimum %s layers; north outlets run light to heavy from top to bottom.', '%s tick/バッチ×断面積。2×2専用・必要高さ%s段、北面は上から軽い留分の順。', '每批%s tick×塔截面积。仅限2×2、最低%s层，北面从上至下按轻到重出料。'),
    'reactor.create_refinery_n_reactors.status.heat': ('Needs more heat from below', '下からの熱が足りません', '下方热量不足'),
}

CORRECTED = {
    'vessel_internals.title': ('Internals for reaction vessels', '反応槽の内部部品', '反应槽的内部组件'),
    'vessel_internals.operate': ('Close the hatch and drive the bottom centre at 32 RPM or more. Fit a relief valve. Catalytic reactions run in the reaction tower.', 'ハッチを閉じ、底面中央へ32 RPM以上を入れます。安全弁も付けます。触媒反応は反応塔で行います。', '关盖并从底部中心输入至少32 RPM，另装安全阀。催化反应在反应塔中进行。'),
    'vessel_basin.products': ('Extract items with funnels and fluids with pumps. If stopped, check outlets, heat, rotation, hatch and dirt.', '製品はファンネル、液体はポンプで取り出します。止まったら出口・加熱・回転・ハッチ・汚れを確認します。', '物品用漏斗、流体用泵取出，停机时检查出口、加热、转动、检修口及污垢。'),
    'vessel_maintenance.pressure': ('A vessel is rated to 16 bar. Set relief below the limit and keep the vent clear.', '槽の耐圧は16 barです。安全弁を限界未満に設定し、排気口を空けます。', '槽耐压16 bar。安全阀设在上限以下，排气口保持畅通。'),
    'vessel_maintenance.boiling': ('Water in an open vessel boils near 100 °C. Sealed water can be hotter, so also check pressure.', '開いた槽の水は約100 °Cで沸騰します。密閉中は100 °Cを超えることもあるので、圧力計も確認します。', '敞口槽中水约100 °C沸腾，密闭时可超过100 °C，因此也要检查压力。'),
    'vessel_maintenance.hatch': ('Hot, pressurised contents spray steam when opened. Cool and depressurise first.', '高温・高圧のままハッチを開けると蒸気が噴き出します。先に冷却・減圧します。', '高温高压时开盖会喷汽，应先冷却降压。'),
    'ammonia_separator.feed': ("Pipe the reaction tower's product gas into the separator's upper side inlet.", '反応塔の生成ガスを、分離器の上段側面へ送ります。', '反应塔产物气送入分离器上层侧面入口。'),
}

RUNTIME.update({
 'distillation.create_refinery_n_reactors.status.width': ('Use a complete 2×2 cross-section; other widths cannot operate','断面を2×2にしてください。ほかの幅では運転できません','请组装完整2×2截面，其他宽度不能运行'),
 'distillation.create_refinery_n_reactors.size': ('2×2 tower: %s×%s×%s; selected feed needs %s layers','2×2専用 %s×%s×%s・選択原料は%s段必要','2×2专用%s×%s×%s；所选原料需%s层'),
 'distillation.create_refinery_n_reactors.status.height': ('Too short for the selected feed; see its required height','選択原料に必要な高さが足りません','高度不足；查看所选原料的最低层数'),
 'distillation.create_refinery_n_reactors.status.feed': ('Supply a compatible feed to the east inlet; air requires 14 layers','東面へ原料を供給してください。空気は14段必要です','向东面供入合适原料；空气需十四层'),
 'distillation.create_refinery_n_reactors.ports': ('East inlet: layer 3 (layer 2 on short towers). North: separate cuts.','入口は東面3段目（2段塔は2段目）、留分出口は北面です','东面第三层进料（二层塔在第二层），北面分路出料'),
 'distillation.create_refinery_n_reactors.inout': ('Layer %s: east feed %s / north product %s','%s段目：東入口 %s / 北出口 %s','第%s层：东面进%s／北面出%s'),
})


def translations():
    for index, language in enumerate(('en_us', 'ja_jp', 'zh_cn')):
        path = A / 'lang' / (language + '.json')
        values = json.loads(path.read_text())
        for suffix, text in TEXT.items():
            values['ponder.create_refinery_n_reactors.' + suffix] = text[index]
            values['create_refinery_n_reactors.ponder.shared.' + suffix] = text[index]
        for key, text in RUNTIME.items():
            values[key] = text[index]
        for scene in ('fractionating_column', 'fractionating_column_binary', 'fractionating_column_air'):
            values['create_refinery_n_reactors.ponder.' + scene + '.header'] = TEXT[scene + '.title'][index]
        for suffix, text in CORRECTED.items():
            values.pop('ponder.create_refinery_n_reactors.' + suffix, None)
            values['create_refinery_n_reactors.ponder.shared.' + suffix] = text[index]
        for key in list(values):
            if 'haber_bosch_tower' in key or 'catalyst_basket.' in key or key.endswith(('vessel_internals.trays', 'vessel_internals.basket', 'catalytic_operation', 'status.equilibrium', 'status.core', 'fluid_9', 'temperature_9', 'display.create_refinery_n_reactors.item_13', 'vessel_overpressure')) or key.endswith('category.fractionating_column'):
                del values[key]
        path.write_text(json.dumps(values, ensure_ascii=False, indent=2) + '\n')


if __name__ == '__main__':
    structures()
    translations()
