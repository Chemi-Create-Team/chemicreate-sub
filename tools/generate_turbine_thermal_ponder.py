"""Thermodynamic operation lessons; preserve existing rotor/casing geometry."""
import json,sys
from pathlib import Path
from generate_petrochemical_ponder import Structure
from generate_ponder_language import generate
A=Path(__file__).resolve().parents[1]/'src/main/resources/assets/create_refinery_n_reactors'
lessons={
 'turbine_operation':[
 ('二段蒸気タービン：圧力と軸出力','Two-stage steam turbines: pressure and shaft work'),
 ('高圧を5断面・低圧を10断面並べ、台の高さで軸をそろえて普通のシャフトでつなぎます。','Place 5 HP and 10 LP sections, align their centers with foundations, and connect ordinary shafts.'),
 ('中心は回転出力、外周は配管です。高圧→低圧へ蒸気を送ります。','Central hubs carry shaft power; outer ports carry fluids. Pipe HP exhaust into LP.'),
 ('蒸気を供給し、排気を取り出すと発電します。止まったら供給圧力と出口を確認します。','Supply steam and remove exhaust to produce power. If stopped, check inlet pressure and the outlet.'),
 ('湿った蒸気や内部の水は、下面の配管から排出します。','Drain wet steam and internal water through the underside piping.'),
 ('通常は256 RPMで回転します。出力を増やすには蒸気の供給を増やします。','The shaft normally runs at 256 RPM. Supply more steam for more power.'),
 ('ゴーグルで出力・圧力・流量を確認します。詳細はディスプレイリンクで表示できます。','Read power, pressure and flow with goggles. Use Display Links for details.')],
 'turbine_cycle':[
 ('蒸気プラント：発生から復水・給水まで','Steam plant: generation, expansion and return'),
 ('分離器の蒸気を高圧・低圧タービンへ、水を戻り配管へ送ります。','Send separator steam through HP and LP turbines, and separated water to the return header.'),
 ('低圧タービンから30 °Cの水が出ます。別のラジエーター復水ラインは不要です。','The LP turbine discharges 30 °C water. No separate radiator condenser line is needed.'),
 ('出口へポンプを付け、復水を流し続けます。出口が満杯になると停止します。','Keep a pump removing condensate. A full outlet stops the turbine.'),
 ('30 °Cの復水約5.512%と284 °Cの分離水を混ぜ、270 °Cで原子炉へ戻します。','Mix about 5.512% of 30 °C condensate with 284 °C separator water; return it to the reactor at 270 °C.'),
 ('Createボイラーの蒸気も使えます。温度と圧力に耐える配管で接続します。','Create boilers can also supply steam. Use pipes rated for its temperature and pressure.')],
 'turbine_safety':[
 ('タービン：閉塞・湿り・異常時の対処','Turbine faults and recovery'),
 ('蒸気が少ないと出力が低下します。まず熱源、過熱器、流量調節器、ポンプの向きを確認し、供給と需要を合わせます。','Low steam supply reduces output. Check heat sources, superheating, regulators and pump direction, then balance supply and demand.'),
 ('低温・湿りすぎた蒸気では停止します。タービン下面の配管から内部の液体を排出し、過熱条件を整えてください。','Cold or excessively wet steam trips the rotor. Drain internal liquid through underside piping and restore adequate superheat.'),
 ('排気が詰まったら、出口の配管とタンクを確認します。','If exhaust backs up, check the outlet pipes and tank.'),
 ('過負荷なら負荷を減らすか蒸気を増やします。過速度なら回転の接続を見直します。','Reduce load or add steam after an overload. Check the shaft connection after overspeed.'),
 ('限界は250 bar・1126.85 °Cです。安全弁と耐熱・耐圧配管を使います。','The limits are 250 bar and 1126.85 °C. Fit relief valves and suitably rated pipes.'),
 ('解体前に蒸気と水を抜きます。中身を残して壊すと周囲へ漏れます。','Drain steam and water before dismantling. Breaking a stocked turbine releases its contents.')]
}
catalog=A/'ponder/tutorials.json';cat=json.loads(catalog.read_text())
lesson_index=min((i for i,t in enumerate(cat) if t['id'] in lessons),default=len(cat))
cat=[t for t in cat if t['id'] not in lessons];entries=[]
components=['high_pressure_turbine_unit','low_pressure_turbine_unit','low_pressure_turbine_blade','high_pressure_turbine_blade','low_pressure_turbine_casing','high_pressure_turbine_casing','tarbin_end','low_pressure_turbine_end']
for ident,texts in lessons.items():
 s=Structure(25,11)
 s.block(24,10,24,'minecraft:polished_andesite')
 s.save(ident);p=A/'ponder/chemistry'/f'{ident}.nbt';(A/'ponder/plant'/p.name).write_bytes(p.read_bytes());p.unlink()
 steps=[]
 for i,pair in enumerate(texts):
  for lang,index in [('ja_jp',0),('en_us',1)]:
   p=A/'lang'/f'{lang}.json';d=json.loads(p.read_text());d['ponder.create_refinery_n_reactors.guide.'+ident+'.'+('title' if i==0 else f'step_{i}')]=pair[index];p.write_text(json.dumps(d,ensure_ascii=False,indent=2)+'\n')
  targets={'turbine_operation':[[17,5,10],[17,5,12],[17,5,10],[8,1,10],[13,5,10],[8,5,10]],
   'turbine_cycle':[[17,5,12],[8,1,10],[8,1,10],[8,1,10],[17,5,10]],
   'turbine_safety':[[17,5,10],[8,1,10],[8,5,14],[13,5,10],[17,5,12],[17,5,10]]}
  if i:steps.append({'target':targets[ident][i-1],'key':f'step_{i}','reveal':[0,1,0,24,10,24]})
 entries.append({'id':ident,'size':25,'height':11,'scale':.31,'components':components,'steps':steps})
cat[lesson_index:lesson_index]=entries
catalog.write_text(json.dumps(cat,ensure_ascii=False,indent=2)+'\n');generate()
