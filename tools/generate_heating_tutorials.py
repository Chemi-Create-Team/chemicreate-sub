"""Horizontal thermal modules, automatic exchange and shared native flow control."""
import json
from generate_petrochemical_ponder import Structure
from generate_reactor_assets import A,NS,write
from generate_ponder_language import generate
SIDES=('east','west','north','south','up','down')
def pipe(s,x,y,z,a='east',b='west'):
 s.block(x,y,z,'create:encased_fluid_pipe',**{side:str(side in (a,b)).lower() for side in SIDES})
def tank(s,x,y,z):s.block(x,y,z,'create:fluid_tank',bottom='true',top='true',shape='plain')
def save(s,id):
 s.save(id);source=A/'ponder/chemistry'/f'{id}.nbt';(A/'ponder/plant'/f'{id}.nbt').write_bytes(source.read_bytes());source.unlink()
def tutorial(s,id,title,components,steps):
 save(s,id)
 tutorials.append({'id':id,'size':s.size[0],'height':s.size[1],'scale':.58,'components':components,'steps':[{'target':pos,'key':f'step_{i}',**extra} for i,(pos,e,j,extra) in enumerate(steps)]})
 for lang,index in [('en_us',0),('ja_jp',1)]:
  strings[lang]['ponder.create_refinery_n_reactors.guide.'+id+'.title']=title[index]
  for i,(pos,e,j,extra) in enumerate(steps):strings[lang][f'ponder.create_refinery_n_reactors.guide.{id}.step_{i}']=(e,j)[index]
def flow():
 s=Structure(11,7)
 tank(s,1,2,4);s.pump(2,2,4,'east');s.block(3,2,4,NS+'flow_regulator',axis='x');pipe(s,4,2,4)
 s.block(5,2,4,NS+'lidded_vessel',axis='y',facing='north');s.block(5,1,4,'create:blaze_burner',blaze='seething')
 pipe(s,6,2,4);s.pump(7,2,4,'east');pipe(s,8,2,4)
 for x in (9,10):
  for z in (4,5):
   for y in (2,3):s.block(x,y,z,'create:fluid_tank',bottom=str(y==2).lower(),top=str(y==3).lower(),shape='plain')
 tutorial(s,'flow_regulation',('Flow control and vessel heating','流量調節と槽での加熱'),['flow_regulator','lidded_vessel'],[
  ([3,2,4],'Insert the valve in a straight pipe section. Native pumps provide pressure; the valve never creates flow.','直線の配管に流量調節器を挟みます。圧力はCreateのポンプで供給し、調節器は流量の上限だけを制限します。',{'reveal':[0,1,0,10,6,10],'speed':64}),
  ([3,2,4],'Hold a Create wrench on the valve body to set 0–256 mB/t. Zero closes it; branches share the same limit.','調節弁をCreateのレンチで右クリック長押しすると0～256 mB/tを設定できます。0で閉止し、分岐先の合計にも同じ上限が適用されます。',{'wrench':True}),
  ([5,1,4],'Heat the lidded vessel from below with a Blaze Burner or an electric heater.','ふた付き槽の下にブレイズバーナーか電熱線ヒーターを置いて加熱します。',{}),
  ([5,2,4],'Check actual temperature with goggles. Heating keeps the same fluid; reaction recipes check temperature.','ゴーグルで実温度を確認します。加熱しても流体の種類は変わらず、反応レシピが温度を確認します。',{})])
catalog=A/'ponder/tutorials.json'
tutorials=[t for t in json.loads(catalog.read_text()) if t['id'] not in ('module_heat','flow_regulation')]
strings={lang:json.loads((A/'lang'/f'{lang}.json').read_text()) for lang in ('en_us','ja_jp')}
for lang,data in strings.items():
 for key in list(data):
  if 'module_heat' in key:del data[key]
# Electric heaters and heat exchangers have their own lessons.
flow()
old=A/'ponder/plant/module_heat.nbt'
if old.exists():old.unlink()
for lang,data in strings.items():write(A/'lang'/f'{lang}.json',data)
write(catalog,tutorials);generate()
