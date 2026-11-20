#!/usr/bin/env python3
"""Lidded vessel Ponder: a closed 3x3 vessel with a pipe on its top and one on its side; English/Japanese captions.
The vessel's controller data and the lid movements are set by LiddedVesselPonderScenes. Run from the repository root."""
import json, sys
sys.path.insert(0, 'tools')
from generate_reactor_assets import A, NS
from generate_petrochemical_ponder import Structure, OUT, string, named, integer

s = Structure(6, 4)
for x in (1, 2, 3):
    for z in (1, 2, 3):
        s.block(x, 1, z, NS + 'lidded_vessel', drive='true', axis='y', open='false', facing='north', bottom='true', hatch='full' if (x, z) == (2, 2) else 'none', ladder=str((x, z) == (1, 1)).lower(),
            north=str(z == 1).lower(), south=str(z == 3).lower(), west=str(x == 1).lower(), east=str(x == 3).lower())
s.block(1, 2, 1, 'create:fluid_pipe', up='true', down='true', north='false', south='false', east='false', west='false', waterlogged='false')
s.block(1, 3, 1, 'create:fluid_pipe', up='false', down='true', north='false', south='false', east='false', west='false', waterlogged='false')
s.block(4, 1, 1, 'create:fluid_pipe', east='true', west='true', north='false', south='false', up='false', down='false', waterlogged='false')
s.pump(5, 1, 1, 'west')
s.save('lidded_vessel')
(A / 'ponder/plant/lidded_vessel.nbt').write_bytes((OUT / 'lidded_vessel.nbt').read_bytes())
(OUT / 'lidded_vessel.nbt').unlink()

s = Structure(6, 5, floor=False)
for x in range(6):
    for z in range(6):
        s.block(x, 0, z, 'minecraft:smooth_stone')
for y in (1, 2):
    for x in (1, 2):
        for z in (1, 2):
            nbt = [string('id', NS + 'lidded_vessel')]
            nbt += [named(3, 'Size', integer(2)), named(3, 'Height', integer(2))] if (x, y, z) == (1, 1, 1) else [named(11, 'Controller', b''.join(integer(v) for v in (3, 1, 1, 1)))]
            hatch = ('s' if z == 1 else 'n') + ('e' if x == 1 else 'w')
            s.block(x, y, z, NS + 'lidded_vessel', nbt=nbt, drive='false', axis='y',
                open='false', facing='north', bottom=str(y == 1).lower(), hatch=hatch if y == 2 else 'none',
                ladder=str((x, z) == (1, 1)).lower(), north=str(z == 1).lower(), south=str(z == 2).lower(), west=str(x == 1).lower(), east=str(x == 2).lower())
s.save('lidded_vessel_2x2')
(A / 'ponder/plant/lidded_vessel_2x2.nbt').write_bytes((OUT / 'lidded_vessel_2x2.nbt').read_bytes())
(OUT / 'lidded_vessel_2x2.nbt').unlink()

s = Structure(9, 4, floor=False)
for x in range(9):
    for z in range(9):
        pad = (1 <= x <= 3 or 5 <= x <= 7) and (1 <= z <= 3 or 5 <= z <= 7)
        s.block(x, 0, z, 'minecraft:polished_andesite' if pad else 'minecraft:smooth_stone')
s.save('vessel_internals')
(A / 'ponder/plant/vessel_internals.nbt').write_bytes((OUT / 'vessel_internals.nbt').read_bytes())
(OUT / 'vessel_internals.nbt').unlink()

def basin_structure(identifier, maintenance=False):
    scene = Structure(9, 7)
    for x in (3, 4, 5):
        for z in (3, 4, 5):
            nbt = [string('id', NS + 'lidded_vessel')]
            nbt += [named(3, 'Size', integer(3)), named(3, 'Height', integer(1))] if (x, z) == (3, 3) else [named(11, 'Controller', b''.join(integer(v) for v in (3, 3, 2, 3)))]
            scene.block(x, 2, z, NS + 'lidded_vessel', nbt=nbt, drive='true', axis='y',
                open='false', facing='north', bottom='true', hatch='full' if (x, z) == (4, 4) else 'none',
                ladder=str((x, z) == (3, 3)).lower(), north=str(z == 3).lower(), south=str(z == 5).lower(), west=str(x == 3).lower(), east=str(x == 5).lower())
            if (x, z) != (4, 4): scene.block(x, 1, z, 'create:blaze_burner', blaze='smouldering')
    scene.block(4, 1, 4, 'create:creative_motor', facing='up')
    scene.block(2, 2, 3, NS + 'pipe_pressure_gauge', axis='x')
    scene.block(0, 1, 5, 'create:creative_motor', facing='north')
    for x in (0, 1, 2):
        part = 'start' if x == 0 else 'end' if x == 2 else 'middle'
        belt = [string('id', 'create:belt'), string('BeltFacing', 'east'), named(11, 'Controller', b''.join(integer(v) for v in (3, 0, 1, 4))), named(3, 'Length', integer(3)), named(3, 'Index', integer(x))]
        scene.block(x, 1, 4, 'create:belt', nbt=belt, casing='false', facing='east', part=part, slope='horizontal', waterlogged='false')
    scene.block(2, 2, 4, 'create:andesite_belt_funnel', facing='east', shape='pushing', powered='false', waterlogged='false')
    for x in (6, 7, 8):
        part = 'start' if x == 6 else 'end' if x == 8 else 'middle'
        belt = [string('id', 'create:belt'), string('BeltFacing', 'east'), named(11, 'Controller', b''.join(integer(v) for v in (3, 8, 1, 3))), named(3, 'Length', integer(3)), named(3, 'Index', integer(x - 6))]
        scene.block(x, 1, 3, 'create:belt', nbt=belt, casing='false', facing='east', part=part, slope='horizontal', waterlogged='false')
    scene.block(8, 1, 2, 'create:creative_motor', facing='south')
    scene.block(6, 2, 3, 'create:andesite_belt_funnel', facing='east', shape='pushing', powered='false', waterlogged='false')
    scene.block(4, 2, 2, 'create:glass_fluid_pipe', axis='z', waterlogged='false')
    scene.pump(4, 2, 1, 'south')
    scene.block(4, 1, 1, 'create:cogwheel', axis='z', waterlogged='false')
    scene.block(4, 2, 0, 'create:fluid_tank', bottom='true', top='true', shape='plain')
    scene.block(4, 1, 0, 'create:creative_motor', facing='south')
    scene.block(4, 2, 6, 'create:glass_fluid_pipe', axis='z', waterlogged='false')
    scene.pump(4, 2, 7, 'south')
    scene.block(4, 1, 7, 'create:cogwheel', axis='z', waterlogged='false')
    scene.block(4, 2, 8, 'create:fluid_tank', bottom='true', top='true', shape='plain')
    scene.block(4, 1, 8, 'create:creative_motor', facing='north')
    if maintenance:
        scene.block(6, 2, 5, 'create:fluid_valve', facing='up', axis_along_first='false', enabled='true', waterlogged='false')
        scene.block(3, 1, 2, 'minecraft:smooth_stone')
        scene.block(3, 2, 2, NS + 'cryogenic_tank', bottom='true', top='true', shape='window')
        scene.block(4, 4, 4, 'create:deployer', facing='down', axis_along_first='false')
        scene.block(4, 5, 4, 'create:creative_motor', facing='down')
    scene.save(identifier)
    (A / 'ponder/plant' / f'{identifier}.nbt').write_bytes((OUT / f'{identifier}.nbt').read_bytes())
    (OUT / f'{identifier}.nbt').unlink()

basin_structure('vessel_basin')
basin_structure('vessel_maintenance', maintenance=True)

TEXTS = {
    'title': ('Lidded vessel', 'ふた付き槽'),
    'place': ('Vessels join up to 3×3 and four layers high. Hold enough blocks and place on top to add a whole layer.',
        '最大3×3・高さ4段につながります。十分な個数を持って上面へ置くと、1段まとめて増設できます。'),
    'window': ('Use the wrench on a side to switch inspection windows for the whole connected vessel. Its stored liquid, items and fitted internals can be seen without opening the pressure hatch.',
        '側面をレンチで右クリックすると、つながった槽全体が点検窓になります。ハッチを開けずに、保管した液体・アイテム・内部部品を確認できます。'),
    'pipes': ('Connect pipes to the sides or top. Pipes over the hatch work only while it is closed.',
        'パイプは側面と上面へつなぎます。ハッチ上のパイプは、閉じているときだけ使えます。'),
    'open': ('Empty-hand right-click opens the hatch for bucket filling and draining.',
        '空手右クリックでハッチを開け、バケツで中身を出し入れできます。'),
    'internals': ('Fit impellers in a 3×3 vessel; smaller vessels store fluids. Remove internals with a wrench on top.', '撹拌翼は3×3へ取り付けます。小さな槽は保管用です。上面レンチで内部部品を回収します。'),
    'blowout': ('Open it while it holds pressure and the gas or vapour bursts out, throwing up whatever stands over it. Toxic contents spread as a cloud.',
        '圧力がかかったまま開けると、気体や蒸気が噴き出し、上にいるものを吹き上げます。有毒なものは周りに雲となって広がります。'),
    'goggles': ('Goggles show the contents, pressure and lid; a Display Link reads the details.',
        '中身・圧力・ふたの状態はゴーグルで、詳しい値はディスプレイリンクで確認できます。'),
}
COMPACT_TEXTS = {
    'title': ('Stacking a storage-only 2x2 vessel', 'タンク専用の2×2槽を積む'),
    'base': ('Four vessel blocks join into a 2x2 tank. The ladder is on the far edge of the wall to the left of the front.',
        '4個のステンレスタンクが2×2につながります。正面から見て左の側面、その奥側の端にはしごがあります。'),
    'stack': ('Hold at least four vessel blocks and right-click the top: a complete 2x2 layer is placed, while the hatch stays closed. A 3x3 vessel similarly uses nine blocks. Sneak to place just one block.',
        'ふた付き槽を4個以上持って上を右クリックすると、ハッチを開かず2×2を1段まとめて置けます。3×3なら9個です。スニークすると1個ずつ置けます。'),
    'hatch': ('Switch to an empty hand to open the hatch. Holding another lidded vessel gives placement priority, so it does not open the hatch.',
        '何も持たない手で右クリックするとハッチが開きます。ふた付き槽を持っている間は設置が優先され、ハッチは開きません。'),
    'window': ('Wrench a side to switch the inspection windows for the whole vessel. Stored fluids and items are visible through them. This does not change the hatch.',
        '側面をレンチで右クリックすると、槽全体を点検窓へ切り替えます。液体・アイテムが見え、ハッチは変わりません。'),
    'internals': ('Fit impellers in a 3×3 vessel; smaller vessels store fluids. Remove internals with a wrench on top.', '撹拌翼は3×3へ取り付けます。小さな槽は保管用です。上面レンチで内部部品を回収します。'),
    'rotation': ('A 2×2 vessel has no shaft input. For 3×3, supply at least 32 RPM at the bottom centre.',
        '2×2は動力を受けません。3×3は底面中央1か所から32 RPM以上を入力します。'),
    'top': ('The outer rim and lid surface join across the whole 2x2 or 3x3 top. Only the central hatch opens; the rest of the lid stays in place.',
        '上面は2×2・3×3全体で縁と板がつながります。開くのは中央ハッチだけで、周囲のふたはそのままです。'),
}
INTERNAL_TEXTS = {
    'title': ('Internals for reaction vessels', '反応槽の内部部品'),
    'impeller': ('Fit an impeller to mix materials inside the vessel. Drive it from below.',
        '撹拌翼を入れると、槽の中で材料を混ぜられます。底面から回転を入れます。'),
    'install': ('Fit impellers in a 3×3 vessel. Use a wrench on top to recover the highest internal.', '撹拌翼は3×3へ取り付けます。上面レンチで最上段の内部部品を回収します。'),
    'operate': ('Close the hatch and drive the bottom centre at 32 RPM or more. Fit a relief valve. Catalytic reactions run in the reaction tower.', 'ハッチを閉じ、底面中央へ32 RPM以上を入れます。安全弁も付けます。触媒反応は反応塔で行います。'),
}
BASIN_TEXTS = {
    'title': ('Mixing and heating in a lidded vessel', 'ふた付き槽で搬送・撹拌・加熱する'),
    'assemble': ('Build a 3x3 vessel from nine blocks. It stores items and fluids before an internal is installed. The hatch and contents are shared; 2x2 vessels provide storage only.', '9個で3×3の槽を組みます。内部部品を入れる前からアイテムと液体を保管でき、ハッチと中身を共有します。2×2はタンク専用です。'),
    'window': ('Wrench a side for windows or the top to remove internals. The shaft connects only at the bottom centre.', '側面レンチで点検窓、上面レンチで内部部品を回収します。回転入力は底面中央だけです。'),
    'feed': ('Use an andesite funnel over the moving input belt for solids, and a powered Create pump and pipe for liquid. Items and liquid may be loaded before fitting the impeller; their existing stock is preserved.', '固体は動くベルトと安山岩ファンネルで、液体は回転を入れたCreateポンプと配管で搬入します。撹拌翼より先に原料を入れても、中身はそのまま引き継ぎます。'),
    'impeller': ('Fit an impeller in each layer and close the hatch. No external mixer is needed.', '各段へ撹拌翼を入れてハッチを閉じます。外付けミキサーは不要です。'),
    'heat': ('Fuel the Blaze Burner below the vessel for heated recipes. This example uses two bauxite and 500 mB caustic soda solution to produce 500 mB sodium aluminate solution and three iron nuggets.', '加熱が必要なレシピでは、下面のブレイズバーナーに燃料を入れます。この例はボーキサイト2個と苛性ソーダ液500 mBから、アルミン酸ソーダ液500 mBと鉄塊3個を作ります。'),
    'rotate': ('Drive the bottom centre at 32 RPM or more. Place heating burners around the central shaft.', '底面中央へ32 RPM以上を入力します。加熱用バーナーは中央を避けて置きます。'),
    'products': ('Extract items with funnels and fluids with pumps. If stopped, check outlets, heat, rotation, hatch and dirt.', '製品はファンネル、液体はポンプで取り出します。止まったら出口・加熱・回転・ハッチ・汚れを確認します。'),
}
MAINTENANCE_TEXTS = {
    'title': ('Pressure, cooling and cleaning', '槽の圧力・冷却・清掃'),
    'pressure': ('A vessel is rated to 16 bar. Set relief below the limit and keep the vent clear.', '槽の耐圧は16 barです。安全弁を限界未満に設定し、排気口を空けます。'),
    'cooling': ('Before cleaning, stop rotation and heat; cool to 60 °C or less and 1.2 bar or less. Use ice or liquid nitrogen in a neighbouring tank.', '清掃前に回転と加熱を止め、60 °C以下・1.2 bar以下にします。冷却には氷や隣のタンクの液体窒素を使えます。'),
    'brush': ('Processing stops at 400 dirt. Open the central hatch and clean with a brush.', '汚れが400になると止まります。中央ハッチを開け、ブラシで清掃します。'),
    'deployer': ('A downward-facing Deployer holding a brush can clean automatically under the same stop, hatch, temperature and pressure conditions.', 'ブラシを持たせた下向きデプロイヤーでも清掃できます。停止・開ハッチ・温度・圧力の条件は同じです。'),
    'wash': ('Empty-hand right-click below to open the drain, then supply 1,000 mB/s water. Remove wash wastewater underneath.', '下面を空手で右クリックしてドレンを開き、水を1,000 mB/s供給します。出た洗浄排水は下面から抜きます。'),
    'cryo': ("Fit a Smart Fluid Pipe to the air tower's north port at layer 14, filter liquid nitrogen, and pump it into a cryogenic tank beside the vessel.", '空気分離塔14段目の北面へスマートパイプを付け、液体窒素でフィルターします。槽の隣の保冷タンクへ送ります。'),
}
RUNTIME_TEXTS = {
    'jei.create_refinery_n_reactors.note.air_separation_nitrogen': ('Filter liquid nitrogen at layer 14.\n%s gas mB = 1 mB; keep residue.','14層の出口で液体窒素をフィルター。\n気体%s mBで液体1 mB。端数保持。'),
    'fluid.create_refinery_n_reactors.wastewater': ('Wash Wastewater','洗浄排水'),
    'fluid.create_refinery_n_reactors.liquid_nitrogen': ('Liquid Nitrogen','液体窒素'),
    'lidded_vessel.create_refinery_n_reactors.storage': ('Storage','貯蔵'),
    'lidded_vessel.create_refinery_n_reactors.summary': ('%s | %s | %s x %s','%s | %s | %s×%s'),
    'lidded_vessel.create_refinery_n_reactors.operation': ('%s RPM | %s%% | %s mB','%s RPM | %s%% | %s mB'),
    'lidded_vessel.create_refinery_n_reactors.maintenance': ('Dirt %s%% | Drain %s','汚れ %s%% | 排水 %s'),
    'lidded_vessel.create_refinery_n_reactors.drain.open': ('Open','開'),
    'lidded_vessel.create_refinery_n_reactors.drain.closed': ('Closed','閉'),
    'reactor.create_refinery_n_reactors.status.fouled': ('Stopped: clean the fouled vessel','停止中：槽の汚れを清掃してください'),
    'display.create_refinery_n_reactors.vessel_fouling': ('Vessel fouling (%)','槽の汚れ（%）'),
    'display.create_refinery_n_reactors.vessel_drain': ('Vessel drain (0/1)','槽ドレン開放（0/1）'),
    'display.create_refinery_n_reactors.vessel_waste': ('Wash wastewater (mB)','洗浄排水（mB）'),
    'display.create_refinery_n_reactors.fluid_8': ('Stored gas (mB)','貯蔵気体（mB）'),
}
for slot in range(3,13): RUNTIME_TEXTS[f'display.create_refinery_n_reactors.item_{slot}'] = (f'Feed slot {slot+1} count',f'原料スロット{slot+1}の個数') if slot<9 else (f'Product slot {slot-8} count',f'製品スロット{slot-8}の個数')
for lang, index in (('en_us', 0), ('ja_jp', 1)):
    p = A / 'lang' / f'{lang}.json'
    raw = p.read_bytes().decode('utf-8'); newline = '\r\n' if '\r\n' in raw else '\n'
    strings = json.loads(raw)
    strings.pop('create_refinery_n_reactors.ponder.shared.lidded_vessel.hinge', None)
    for key, text in TEXTS.items(): strings[f'create_refinery_n_reactors.ponder.shared.lidded_vessel.{key}'] = text[index]
    strings['create_refinery_n_reactors.ponder.lidded_vessel.header'] = TEXTS['title'][index]
    for key, text in COMPACT_TEXTS.items(): strings[f'create_refinery_n_reactors.ponder.shared.lidded_vessel_2x2.{key}'] = text[index]
    strings['create_refinery_n_reactors.ponder.lidded_vessel_2x2.header'] = COMPACT_TEXTS['title'][index]
    for key, text in INTERNAL_TEXTS.items(): strings[f'create_refinery_n_reactors.ponder.shared.vessel_internals.{key}'] = text[index]
    strings['create_refinery_n_reactors.ponder.vessel_internals.header'] = INTERNAL_TEXTS['title'][index]
    for name, texts in (('vessel_basin',BASIN_TEXTS),('vessel_maintenance',MAINTENANCE_TEXTS)):
        for key, text in texts.items(): strings[f'create_refinery_n_reactors.ponder.shared.{name}.{key}'] = text[index]
        strings[f'create_refinery_n_reactors.ponder.{name}.header'] = texts['title'][index]
    for key,text in RUNTIME_TEXTS.items(): strings[key]=text[index]
    p.write_bytes((json.dumps(strings, ensure_ascii=False, indent=2).replace('\n', newline) + newline).encode('utf-8'))
