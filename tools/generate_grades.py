#!/usr/bin/env python3
"""Names of the temperature and pressure grades (thermal/Grade.java), and the transport lines that show them, in the three languages.
Run from the repository root."""
import json
from pathlib import Path

LANG = Path(__file__).resolve().parents[1] / 'src/main/resources/assets/create_refinery_n_reactors/lang'
P = 'grade.create_refinery_n_reactors.'
NAMES = {
    'heat.cryogenic': ('Cryogenic', '極低温', '极低温'), 'heat.cold': ('Cold', '低温', '低温'), 'heat.ambient': ('Ambient', '常温', '常温'),
    'heat.hot': ('Hot', '高温', '高温'), 'heat.very_hot': ('Very hot', '超高温', '超高温'),
    'pressure.atmospheric': ('Atmospheric', '常圧', '常压'), 'pressure.pressurised': ('Pressurised', '加圧', '加压'), 'pressure.high': ('High pressure', '高圧', '高压'),
    'fluid': ('Temperature %s / pressure %s', '温度 %s／圧力 %s', '温度 %s／压力 %s'),
    'heat.cryogenic.detail': ('Cryogenic (below -123 °C)', '極低温（-123℃未満）', '极低温（低于-123℃）'),
    'heat.cold.detail': ('Cold (-123 to 10 °C)', '低温（-123～10℃）', '低温（-123～10℃）'),
    'heat.ambient.detail': ('Ambient (10 to 100 °C)', '常温（10～100℃）', '常温（10～100℃）'),
    'heat.hot.detail': ('Hot (100 to 500 °C)', '高温（100～500℃）', '高温（100～500℃）'),
    'heat.very_hot.detail': ('Very hot (500 °C and above)', '超高温（500℃以上）', '超高温（500℃以上）'),
    'pressure.atmospheric.detail': ('Atmospheric (below 2 bar)', '常圧（2 bar未満）', '常压（低于2 bar）'),
    'pressure.pressurised.detail': ('Pressurised (2 to 20 bar)', '加圧（2～20 bar）', '加压（2～20 bar）'),
    'pressure.high.detail': ('High pressure (20 bar and above)', '高圧（20 bar以上）', '高压（20 bar以上）'),
}
NAMES.update({
    'exchange.row': ('%s | %s mB/t', '%s｜%s mB/t', '%s｜%s mB/t'),
    'turbine.wear': ('Wear %s%%','摩耗 %s%%','磨损 %s%%'),
    'radiator': ('%s fans | %s mB/t', 'ファン %s台｜%s mB/t', '%s台风扇｜%s mB/t'),
    'tower': ('Warm water → water | %s mB/t', '温排水 → 水｜%s mB/t', '温排水 → 水｜%s mB/t'),
    'separator': ('Feed %s mB | liquid %s mB | recycle %s mB', '原料 %s mB｜液体 %s mB｜循環ガス %s mB', '原料 %s mB｜液体 %s mB｜循环气 %s mB'),
    'reducer': ('Lowers one pressure grade', '圧力等級を一段下げます', '降低一个压力等级'),
    'tank.range': ('Holds %s to %s; pressure up to %s', '入れられる温度：%s～%s／圧力：%sまで', '可容纳温度：%s～%s／压力：最高%s'),
    'tank.kind.all': ('Liquids and gases','液体・気体','液体与气体'),
    'tank.kind.liquid': ('Liquids only','液体専用','仅液体'),
    'tank.kind.gas': ('Gases only','気体専用','仅气体'),
})
TRANSPORT = {
    'grade': ('Grade: %s', '等級：%s', '等级：%s'),
    'carries': ('Liquids it carries: %s to %s; pressure up to %s', '運べる液体の温度：%s～%s／圧力：%sまで', '可输送液体温度：%s～%s／压力：最高%s'),
    'wear': ('⚠ %s: wear %s%%. Drain it before it bursts', '⚠ %s：損傷 %s%%。壊れる前に抜いてください', '⚠ %s：损伤 %s%%，破裂前请排空'),
    'wear.gas': ('Not rated for gas', '気体に非対応', '不耐气体'),
    'wear.liquid': ('Not rated for liquid', '液体に非対応', '不耐液体'),
    'wear.corrosion': ('Not resistant to this corrosive fluid', 'この腐食性流体に非対応', '不耐该腐蚀性流体'),
    'wear.hot': ('Too hot', '温度が高すぎます', '温度过高'),
    'wear.overheat': ('Too hot', '温度が高すぎます', '温度过高'),
    'wear.softening': ('Softening in the heat', '熱で軟化しています', '受热软化'),
    'wear.cold': ('Too cold', '温度が低すぎます', '温度过低'),
    'wear.brittle': ('Too cold: brittle', '温度が低すぎます（脆くなります）', '温度过低（变脆）'),
    'wear.pressure': ('Pressure too high', '圧力が高すぎます', '压力过高'),
    'wear.acid': ('Not resistant to acids', '酸に非対応', '不耐酸'),
    'wear.hot_acid': ('Not resistant to hot acid liquors', '熱い酸に非対応', '不耐高温酸液'),
    'wear.fluoride': ('Not resistant to fluorides', 'フッ化物に非対応', '不耐氟化物'),
    'wear.halogen': ('Not resistant to halogens', 'ハロゲンに非対応', '不耐卤素'),
    'wear.base': ('Not resistant to alkalis', '塩基に非対応', '不耐碱'),
}

for index, code in enumerate(('en_us', 'ja_jp', 'zh_cn')):
    path = LANG / f'{code}.json'
    strings = json.loads(path.read_text())
    for key in list(strings):
        if any(key.endswith('.'+name) or '.'+name+'.' in key for name in ('relief_valve','redstone_relief_valve','back_pressure_valve')): del strings[key]
    retired_metrics = ['boiler_outlet', 'boiling_point', 'cold_box_kelvin', 'compression_efficiency', 'compression_inlet', 'compression_outlet', 'cooling_ambient', 'cooling_evaporated', 'cooling_wetbulb', 'equipment_damage', 'exchange_joules', 'exchange_ua', 'fluid_', 'inlet_lower', 'inlet_upper', 'item_', 'outlet_lower', 'outlet_upper', 'pipe_heat_loss', 'pipe_minimum', 'pipe_pressure_limit', 'pipe_temperature', 'pipe_temperature_limit', 'pipe_thermal_damage', 'pipe_wear', 'pressure', 'pressure_setpoint', 'pump_power', 'radiator_ambient', 'reaction_temperature', 'recovered_heat', 'rotor_efficiency', 'rotor_exhaust_pressure', 'rotor_exhaust_temperature', 'rotor_inlet_temperature', 'section_pressure', 'steam_damage', 'steam_liquid', 'steam_source_in', 'steam_source_out', 'steam_vapour', 'temperature_', 'temperature_8', 'temperature_9', 'thermal_power', 'vessel_overpressure', 'vessel_strain']
    for key in list(strings):
        if key.startswith(('pressure.create_refinery_n_reactors.', 'thermal.create_refinery_n_reactors.')) or key.startswith('display.create_refinery_n_reactors.') and (key.rsplit('.',1)[-1] in retired_metrics or key.rsplit('.',1)[-1].startswith('temperature_')) or key.startswith('death.attack.create_refinery_n_reactors.') and any(name in key for name in ('hot_surface','cold_surface')): del strings[key]
    for key, names in NAMES.items(): strings[P + key] = names[index]
    for key, names in {
        'steam_generation': ('Steam Generation','蒸気の生成','蒸汽生成'),
        'steam_turbine': ('Steam Turbine','蒸気タービン','蒸汽轮机'),
        'note.steam_generation_ordinary': ('Create boiler: heated burner','Createボイラー：加熱','机械动力锅炉：加热'),
        'note.steam_generation_high_pressure': ('Horizontal tank: heated burner','横型タンク：加熱','卧式储罐：加热'),
        'note.steam_generation_superheated': ('Horizontal tank: superheated burner','横型タンク：超加熱','卧式储罐：超级加热'),
        'note.steam_turbine_high': ('HP stage: %s J/mB; pump exhaust to LP','高圧段：%s J/mB・排気を低圧段へポンプ搬送','高压段：%s J/mB；泵送排气至低压段'),
        'note.steam_turbine_low': ('LP stage: %s J/mB; requires condenser cooling','低圧段：%s J/mB・復水器の冷却が必要','低压段：%s J/mB；需要冷凝器冷却'),
        'heat_exchange': ('Fluid Heat Exchange', '流体の熱交換', '流体换热'),
        'note.heat_exchange': ('Both streams convert together; counterflow runs 1.5 times faster', '2系統を同時に変換・対向流は処理量1.5倍', '同时转换两股流体；逆流处理量为1.5倍'),
        'helium_refrigeration': ('Cryogenic Liquefaction', '極低温液化', '低温液化'),
        'note.helium_refrigeration': ('Requires shaft work and circulating water; drain both outputs', '回転動力と循環水が必要・液体と温排水を両方搬出', '需要轴功与循环水；及时排出液体和温排水'),
    }.items(): strings['jei.create_refinery_n_reactors.'+key] = names[index]
    for key,names in {
        'turbine.create_refinery_n_reactors.wear': ('Stopped: service required','停止：整備が必要','停止：需要维护'),
        'turbine.create_refinery_n_reactors.pass': ('Passing ordinary steam','通常蒸気を通過中','普通蒸汽通过中'),
        'tubular_boiler.working': ('Accumulating heat','熱を蓄積中','积累热量中'),
        'tubular_boiler.structure': ('The tank has not formed or is not loaded','タンクが形成されていないか、読み込まれていません','储罐未成形或未加载'),
        'tubular_boiler.water': ('Fill it with water to boil','沸かす水を入れてください','注入要煮沸的水'),
        'tubular_boiler.engines': ('Steam engines attached: it works as Create\'s boiler','蒸気エンジンが付いているので、Createのボイラーとして働きます','已连接蒸汽引擎：作为机械动力锅炉运行'),
    }.items(): strings[key]=names[index]
    strings['block.create_refinery_n_reactors.pressure_tank'] = ('Pressure Tank', '耐圧タンク', '耐压储罐')[index]
    for key in (
        'separator.create_refinery_n_reactors.conditions',
        'haber_bosch.create_refinery_n_reactors.conditions',
        'thermal_control.create_refinery_n_reactors.target',
        'turbine.create_refinery_n_reactors.pressures',
        'reciprocating.create_refinery_n_reactors.cylinder',
        'gas_pipe.create_refinery_n_reactors.pressure',
        'fuel_pool.create_refinery_n_reactors.status.hot',
        'pipe_gauge.create_refinery_n_reactors.temperature',
        'pipe_gauge.create_refinery_n_reactors.pressure',
        'jei.create_refinery_n_reactors.note.reactor_pressure',
        'separator.create_refinery_n_reactors.water_conditions',
        'plant_systems.tower.power',
        'plant_systems.status.coolant_warm',
        'pipe_gauge.create_refinery_n_reactors.vessel_tap',
        'lidded_vessel.create_refinery_n_reactors.thermal',
        'tubular_boiler.create_refinery_n_reactors.outlet',
        'pipe_gauge.create_refinery_n_reactors.analog',
        'flare.create_refinery_n_reactors.pressure',
        'flare.create_refinery_n_reactors.pipe_rating',
        'gas_bell.create_refinery_n_reactors.travel',
        'electric_utility.create_refinery_n_reactors.vessel_temperature',
        'jei.create_refinery_n_reactors.note.minimum_temperature',
        'jei.create_refinery_n_reactors.note.heating_temperature',
        'jei.create_refinery_n_reactors.note.fluid_temperature',
        'pipe_gauge.create_refinery_n_reactors.vessel_overpressure',
        'reactor.create_refinery_n_reactors.status.overpressure',
        'reactor.create_refinery_n_reactors.status.temperature',
        'turbine.create_refinery_n_reactors.pressure',
        'turbine.create_refinery_n_reactors.unsafe',
        'reactor.create_refinery_n_reactors.status.unsafe',
        'reciprocating.create_refinery_n_reactors.unsafe',
        'reciprocating.create_refinery_n_reactors.pressure',
        'radiator.create_refinery_n_reactors.target',
        'haber_bosch.create_refinery_n_reactors.status.heating',
        'transport.create_refinery_n_reactors.stage.1',
        'transport.create_refinery_n_reactors.stage.2',
        'transport.create_refinery_n_reactors.stage.3',
        'transport.create_refinery_n_reactors.reason.corrosion',
        'transport.create_refinery_n_reactors.reason.pressure',
        'transport.create_refinery_n_reactors.reason.overheat',
        'transport.create_refinery_n_reactors.reason.softening',
        'transport.create_refinery_n_reactors.reason.embrittlement',
    ): strings.pop(key, None)
    for key, names in {
        'electrolysis_furnace.create_refinery_n_reactors.bath': ('Bath: %s', '浴：%s', '电解浴：%s'),
        'plant_systems.fuel_cycle.process': ('Progress: %s ticks | %s | %s kW', '進捗：%s tick｜%s｜%s kW', '进度：%s tick｜%s｜%s kW'),
        'fuel_pool.create_refinery_n_reactors.water': ('Water %s / %s mB | %s', '水：%s / %s mB｜%s', '水：%s / %s mB｜%s'),
        'jei.create_refinery_n_reactors.note.fuel_cell': ('%s kJ at open-circuit 400 V; delivered work follows terminal voltage. Up to 2 kW into 20 Ω. Produces ordinary water.', '開放電圧400 Vで%s kJ。実仕事は端子電圧に比例。20 Ωへ最大2 kW。普通の水を生成。', '开路400 V时%s kJ，实际电功随端电压变化。向20 Ω负载最多输出2 kW，生成普通水。'),
        'block.create_refinery_n_reactors.process_transmitter': ('Tank Level / Flow Transmitter', '液位・流量発信器', '液位／流量变送器'),
        'cold_box.create_refinery_n_reactors.status.running': ('Liquefying gas', '気体を液化中', '气体液化中'),
        'reactor.create_refinery_n_reactors.status.bypass': ('No matching conversion; check both input types', '変換なし：両系統の流体種類を確認', '无匹配转换：检查两路流体种类'),
        'reactor.create_refinery_n_reactors.status.pressure': ('Supply high-pressure hydrogen', '高圧水素を供給してください', '请供应高压氢气'),
        'tubular_boiler.running': ('Producing steam', '蒸気を生成中', '生成蒸汽中'),
    }.items(): strings[key] = names[index]
    strings.pop("tubular_boiler.outlet", None)
    for key in ("block.create_refinery_n_reactors.tubular_boiler", "tubular_boiler.evaporator", "tubular_boiler.evaporation_heat", "tubular_boiler.upper", "tubular_boiler.warming"): strings.pop(key, None)
    strings.pop("electrolysis_furnace.create_refinery_n_reactors.status.melting", None)
    for key, names in TRANSPORT.items(): strings['transport.create_refinery_n_reactors.' + key] = names[index]
    path.write_text(json.dumps(strings, ensure_ascii=False, indent=2) + '\n')
print(f'Wrote {len(NAMES)} grade names and {len(TRANSPORT)} transport lines')
