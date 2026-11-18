"""The native flow-control valve, the compact heater's states and the thermal-control names."""
import json,sys
from pathlib import Path


def steel_unification(assets, data, namespace, writer):
 for obsolete in (assets/'textures/item/industrial_steel_stock.png',assets/'models/item/industrial_steel_stock.json'):
  obsolete.unlink(missing_ok=True)
 (data/'recipe/industrial_steel_stock.json').unlink(missing_ok=True)
 for language in ('en_us','ja_jp','zh_cn'):
  path=assets/'lang'/f'{language}.json';raw=path.read_bytes().decode('utf-8');newline='\r\n' if '\r\n' in raw else '\n';strings=json.loads(raw)
  strings.pop('item.create_refinery_n_reactors.industrial_steel_stock',None)
  path.write_bytes((json.dumps(strings,ensure_ascii=False,indent=2).replace('\n',newline)+newline).encode('utf-8'))


if __name__=='__main__' and '--steel-only' in sys.argv:
 root=Path(__file__).resolve().parents[1];assets=root/'src/main/resources/assets/create_refinery_n_reactors';data=root/'src/main/resources/data/create_refinery_n_reactors';namespace='create_refinery_n_reactors:'
 def standalone_write(path,value):
  path.parent.mkdir(parents=True,exist_ok=True);path.write_text(json.dumps(value,indent=2,ensure_ascii=False)+'\n')
 steel_unification(assets,data,namespace,standalone_write)
 print('Generated steel-unification thermal recipes and removed the obsolete stock assets')
 raise SystemExit


sys.path.insert(0,'tools')
from generate_reactor_assets import A,D,NS,write,element
shapes=json.loads((A/'heating_shapes.json').read_text())
textures={name:NS+'block/heating/'+name for name in ('steel','frame','copper','refractory','mesh')}
textures['dial']=NS+'block/reactor/dial';textures['particle']=textures['steel']
def box(bounds,material):return (bounds,material)
write(A/'blockstates/process_heater.json',{'variants':{f'facing={face},lit={str(lit).lower()}':{'model':NS+'block/process_heater'+('_active' if lit else ''),'y':angle} for face,angle in [('north',0),('east',90),('south',180),('west',270)] for lit in (False,True)}})
parts=[box([0,5,5,16,11,11],'steel'),box([0,3,3,3,13,13],'frame'),box([13,3,3,16,13,13],'frame'),box([5,3,3,11,13,13],'copper'),box([7,13,7,9,16,9],'steel'),box([4,14,4,12,16,6],'frame'),box([4,14,10,12,16,12],'frame'),box([4,14,6,6,16,10],'frame'),box([10,14,6,12,16,10],'frame'),box([5,8,2,11,13,3],'dial')]
write(A/'models/block/thermal_control/flow_regulator.json',{'parent':'minecraft:block/block','textures':textures,'elements':[element(b,t) for b,t in parts]})
for axis in ('x','y','z'):
 def rotate(b):
  x,y,z,X,Y,Z=b
  return b if axis=='x' else [16-Z,y,x,16-z,Y,X] if axis=='z' else [y,16-X,z,Y,16-x,Z]
 shapes['regulator/'+axis]=[rotate(b) for b,t in parts]
# The fluid heat exchangers are generate_heat_exchangers.py; only the regulator's shapes are kept here.
shapes={key:value for key,value in shapes.items() if key.startswith(('machine/','regulator/'))}
write(A/'heating_shapes.json',shapes)
write(A/'blockstates/flow_regulator.json',{'variants':{'axis=x':{'model':NS+'block/thermal_control/flow_regulator'},'axis=z':{'model':NS+'block/thermal_control/flow_regulator','y':90},'axis=y':{'model':NS+'block/thermal_control/flow_regulator','x':90,'y':90}}})
write(A/'models/item/flow_regulator.json',{'parent':NS+'block/thermal_control/flow_regulator'})
# Structural parts use the plant's steel block.
steel_unification(A,D,NS,write)
new=['flow_regulator']
for name in new:
 write(D/'loot_table/blocks'/f'{name}.json',{'type':'minecraft:block','pools':[{'rolls':1,'entries':[{'type':'minecraft:item','name':NS+name}],'conditions':[{'condition':'minecraft:survives_explosion'}]}]})
for filename in ('src/main/resources/data/minecraft/tags/block/mineable/pickaxe.json','src/main/resources/data/create/tags/block/non_movable.json'):
 path=Path(filename);d=json.loads(path.read_text());d['values']=list(dict.fromkeys(d['values']+[NS+n for n in new]));write(path,d)
for lang,index in [('en_us',0),('ja_jp',1)]:
 path=A/'lang'/f'{lang}.json';d=json.loads(path.read_text())
 labels={'process_heater':('Compact heater','小型加熱器'),'flow_regulator':('Flow regulator','流量調節器')}
 for k,v in labels.items():d['block.create_refinery_n_reactors.'+k]=v[index]
 for k,v in {'flow':('Maximum flow','最大流量'),'flow_readout':('Maximum transfer: %s mB/t','最大転送量：%s mB/t'),'actual_flow':('Processed flow: cold %s / hot %s mB/t','実処理流量：低温側 %s／高温側 %s mB/t')}.items():d['thermal_control.create_refinery_n_reactors.'+k]=v[index]
 for k,v in {
  'structure':('Tube columns: %s / %s | size: %s × 3 × %s | buffers: %s mB','管束列：%s／%s列｜外寸：長さ%s×幅3×高さ%s｜各バッファ：%s mB'),
  'capacity':('Maximum flow: %s mB/t | thermal capacity: %s W | current: %s W','最大流量：%s mB/t｜熱移動能力：%s W｜現在：%s W'),
  'target':('Target: %s °C | structural ceiling: %s °C','設定温度：%s ℃｜構造による上限：%s ℃'),
  'automatic':('Automatic two-stream exchange | conductance: %s W/K','二系統の温度と流量で自動熱交換｜伝熱性能：%s W/K'),
  'consumption':('Fuel consumption: %s mB/t','燃料消費：%s mB/t'),
  'heater_ports':('Feed %s side, low ↔ outlet %s side, high: either flow direction | fuel: front, bottom left | shaft and power terminals: burner','原料%s面の下段↔出口%s面の上段：流入方向は自動｜燃料：正面の下段左｜シャフトと電源端子：正面下段中央のバーナー'),
  'exchanger_ports':('Tube side: both end faces | shell side: the top of each end (jacketed pipe: top of the first, bottom of the last) | each side picks its own flow direction','管側：両端の面｜胴側：両端の上面（ジャケット管は最初の上面と最後の下面）｜流入方向はそれぞれ自動判定'),
 }.items():d['thermal_control.create_refinery_n_reactors.'+k]=v[index]
 d['heating.create_refinery_n_reactors.working']=('Heating hold-up: %s (%s mB)','加熱中の保持流体：%s（%s mB）')[index]
 d['thermal_control.create_refinery_n_reactors.row_flow']=('Processed flow: tube side %s / shell side %s mB/t','実処理流量：管側 %s／胴側 %s mB/t')[index]
 for i,label in enumerate([('Tube inlet','管側入口'),('Tube outlet','管側出口'),('Shell inlet','胴側入口'),('Shell outlet','胴側出口')]):d['thermal_control.create_refinery_n_reactors.row_buffer_'+str(i)]=label[index]+': %s / %s mB'
 d['reactor.create_refinery_n_reactors.status.two_streams']=('Supply fluid to both independent streams','独立した二系統へ流体を供給してください')[index]
 d['reactor.create_refinery_n_reactors.status.thermal_length']=('Lay the casings in a straight run: 2 to 12 for the shell-and-tube exchanger, 1 to 8 for the jacketed pipe','外装を一直線に並べてください（シェル＆チューブ式は2～12、ジャケット管は1～8）')[index]
 d['reactor.create_refinery_n_reactors.status.shared']=('A structural part already belongs to another machine','他の設備と構造部品を共有できません')[index]
 d['reactor.create_refinery_n_reactors.status.bypass']=('Flowing without heat transfer; check inlet temperatures','通液中・熱移動なし（入口温度を確認してください）')[index]
 d['process.create_refinery_n_reactors.status.bypass']=('Flowing; no additional heating required','通液中・追加の加熱は不要です')[index]
 write(path,d)
print('Generated the flow regulator, the compact heater's states and the thermal-control names')
