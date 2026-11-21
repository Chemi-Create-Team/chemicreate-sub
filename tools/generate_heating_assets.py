from pathlib import Path
import sys,json
sys.path.insert(0,'tools')
import generate_reactor_assets as r
from PIL import Image
from minecraft_style import grain
A=r.A;D=r.D;NS=r.NS
H=A/'models/block/heating';T=A/'textures/block/heating';T.mkdir(parents=True,exist_ok=True)
for name,base in {'steel':(113,122,128),'frame':(61,70,77),'copper':(185,113,73),'refractory':(119,91,77),'mesh':(75,84,89)}.items():
 im=Image.new('RGBA',(16,16));p=im.load()
 for y in range(16):
  for x in range(16):
   v=grain('heating_'+name,x,y,1.5)+(18 if x==1 else -16 if x==15 else 0)
   if name=='refractory' and (y%8==0 or (x+(8 if y>8 else 0))%16==0):v=-25
   if name=='mesh':v+=-24 if x%4 in (1,2) else 22
   p[x,y]=tuple(max(0,min(255,c+v)) for c in base)+(255,)
 im.save(T/(name+'.png'))
shape_path=A/'heating_shapes.json'
shapes={key: value for key, value in json.loads(shape_path.read_text()).items() if key.startswith('regulator/')} if shape_path.exists() else {}
def write(p,d):r.write(p,d)
def model(name,parts,folder=H):
 textures={}
 for b,t in parts:textures[t]=NS+('block/reactor/'+t if t in ('dial','lamp','lamp_on','brass') else 'block/heating/'+t)
 write(folder/(name+'.json'),{'parent':'minecraft:block/block','textures':{**textures,'particle':NS+'block/heating/steel'},'elements':[r.element(b,t) for b,t in parts]})
base=r.BASE+[([2,2,2,14,6,14],'refractory')]
shapes['machine/z']=[b for b,t in base];shapes['machine/y']=shapes['machine/z'];shapes['machine/x']=[[16-b[5],b[1],b[0],16-b[2],b[4],b[3]] for b,t in base]
write(A/'heating_shapes.json',shapes)
# Exchanger steel, tube and frame stock; the exchangers' models are generate_heat_exchangers.py.
for name,base in {'exchanger_steel':(119,130,139),'exchanger_pipe':(173,112,79),'exchanger_frame':(57,68,77)}.items():
 im=Image.new('RGBA',(16,16));p=im.load()
 for y in range(16):
  for x in range(16):
   band=(0,14,32,21,7,-4,-13,-20,-13,-5,3,12,20,8,-6,-25)[x]
   v=band+grain(name,x,y,1.2)
   if y==0:v=-24
   if y==1:v=28
   p[x,y]=tuple(max(0,min(255,c+v)) for c in base)+(255,)
 im.save(A/'textures/block/reactor'/f'{name}.png')
for lang,i in [('en_us',0),('ja_jp',1)]:
 p=A/'lang'/f'{lang}.json';d=json.loads(p.read_text())
 for k,v in list(d.items()):
  if isinstance(v,str):d[k]=v.replace('反応原料予熱器','流体熱交換器').replace('反応予熱器','流体熱交換器').replace('工程加熱器','工業用加熱器').replace('Reaction-feed preheater','Fluid heat exchanger').replace('Process heater','Industrial heater')
 d['block.create_refinery_n_reactors.process_heater']=('Compact heater','小型加熱器')[i]
 d['block.create_refinery_n_reactors.reactor_preheater']=('Fluid heat exchanger manifold support','流体熱交換器の接続架台')[i]
 phrases={'target':('Target temperature','目標温度'),'performance':('Target %s °C; flow %s mB/s; maximum %s J/s; transferred %s J/s','目標 %s ℃・最大流量 %s mB/秒・最大熱量 %s J/秒・直近の移動熱量 %s J/秒'),'heater_ports':('Feed: %s side, low; outlet: %s side, high; fuel: front, bottom left; shaft and power terminals: burner','原料：%s面の下段・出口：%s面の上段・燃料：正面の下段左・シャフトと電源端子：正面下段中央のバーナー'),'exchanger_ports':('Cold stream: controller inlet/outlet; hot stream: west/east of first tube section','低温側：制御部の入口／出口・高温側：最初の管束段の西入口／東出口'),'energy':('Electric: %s kW at %s V; stored %s kJ; fuel %s mB','電力：%s kW（%s V）・蓄熱 %s kJ・燃料 %s mB')}
 for n in range(4):phrases['buffer_'+str(n)]=(('Cold inlet','Cold outlet','Hot inlet','Hot outlet')[n]+': %s (%s mB)',('低温入口','低温出口','高温入口','高温出口')[n]+'：%s（%s mB）')
 for k,v in phrases.items():d['heating.create_refinery_n_reactors.'+k]=v[i]
 for k,v in {'feed':('Waiting for fluid','流体待ち'),'energy':('Insufficient fuel / electric power','燃料・電力不足'),'output':('Outlet buffers are full or contain another fluid','出口満杯、または別の流体が残っています'),'heating':('Heating','加熱中'),'exchange':('Exchanging heat','熱交換中'),'two_streams':('Supply both hot and cold streams','高温・低温の両系統へ流体を供給してください'),'bypass':('Flowing without heat transfer; check temperatures','通液中・熱移動なし。現在温度と目標温度を確認してください')}.items():d['reactor.create_refinery_n_reactors.status.'+k]=v[i]
 write(p,d)
print('Generated the heating textures, heat-exchanger artwork and heating translations')
