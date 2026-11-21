#!/usr/bin/env python3
"""Flare fittings only: native voxel models, three 16 px materials and their survival assets."""
import json
import math
from pathlib import Path
from PIL import Image
from generate_lidded_vessel import internal_model

ROOT=Path(__file__).resolve().parents[1]
A=ROOT/'src/main/resources/assets/create_refinery_n_reactors'
D=ROOT/'src/main/resources/data/create_refinery_n_reactors'
NS='create_refinery_n_reactors:'
TEXTURES={'steel':NS+'block/steam/steel','frame':NS+'block/steam/frame','brass':'create:block/brass_block',
          'nozzle':NS+'block/flare/nozzle','actuator':NS+'block/flare/actuator','particle':NS+'block/steam/steel'}
NAMES={
 'block.create_refinery_n_reactors.flare_tip':('Flare Tip','フレアスタック先端部','火炬头'),
 'pipe_gauge.create_refinery_n_reactors.analog':('Comparator: %s/15 at %s bar full scale; %s','比較器：%s/15（全目盛%s bar）・%s','比较器：%s/15，满量程%s bar；%s'),
 'flare.create_refinery_n_reactors.state':('Flare: %s','フレア：%s','火炬：%s'),
 'flare.create_refinery_n_reactors.pressure':('Receiving pressure: %s bar','入口圧力：%s bar','入口压力：%s bar'),
 'flare.create_refinery_n_reactors.flow':('Discharge: %s mB/t; flame: %s m','排出：%s mB/t・炎高：%s m','排出：%s mB/t；火焰高：%s m'),
 'flare.create_refinery_n_reactors.chimney':('Needs three connected vertical pipes below','下に接続済みの縦配管3本が必要','下方需要3格连通的竖直管道'),
 'flare.create_refinery_n_reactors.blocked':('Exhaust obstructed or submerged','排気口が閉塞・水没','排气口被遮挡或浸水'),
 'flare.create_refinery_n_reactors.unlit':('Pilot out; discharged fuel remains flammable','種火なし・排出燃料は引火する','引火火焰熄灭，排出的燃气可燃'),
 'flare.create_refinery_n_reactors.burning':('Burning surplus gas','余剰ガスを燃焼中','正在燃烧剩余气体'),
 'flare.create_refinery_n_reactors.venting':('Venting noncombustible gas','不燃性ガスを放出中','正在排放不燃气体'),
 'flare.create_refinery_n_reactors.ready':('Pilot lit; awaiting gas','種火あり・ガス待機','引火火焰已点燃，等待气体'),
 'flare.create_refinery_n_reactors.pilot_on':('Pilot lit — empty hand extinguishes it','種火点火済み・素手で消火','引火火焰已点燃，空手可熄灭'),
 'flare.create_refinery_n_reactors.pilot_off':('Ignite with flint and steel or a fire charge','火打石と打ち金・ファイヤーチャージで点火','使用打火石或火焰弹点燃'),
 'flare.create_refinery_n_reactors.valve_open':('Open — gas can reach the bypass','開・バイパスへ流通','开启，气体可流入旁路'),
 'flare.create_refinery_n_reactors.valve_closed':('Closed — apply redstone to open','閉・レッドストーンで開く','关闭，通入红石信号以开启'),
 'flare.create_refinery_n_reactors.signal':('Input signal: %s/15','入力信号：%s/15','输入信号：%s/15'),
 'flare.create_refinery_n_reactors.pipe_rating':('Pressure rating: %s bar','耐圧：%s bar','额定压力：%s bar'),
 'display.create_refinery_n_reactors.flare_height':('Flare height (m)','フレアの炎高（m）','火炬火焰高度（m）'),
 'display.create_refinery_n_reactors.flare_burnt':('Gas processed (mB)','フレア処理量（mB）','火炬处理量（mB）'),
 'display.create_refinery_n_reactors.flare_vented':('Gas vented (mB)','未燃焼排出ガス（mB）','未燃烧排出气体（mB）'),
 'display.create_refinery_n_reactors.redstone_signal':('Redstone signal','レッドストーン信号','红石信号'),
 'subtitles.create_refinery_n_reactors.machine.flare_roar':('Flare roars','フレアが轟く','火炬轰鸣'),
}


def write(path,value):
 path.parent.mkdir(parents=True,exist_ok=True)
 path.write_text(json.dumps(value,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')


def texture(name):
 palette=[(53,58,61),(67,72,74),(83,88,89),(100,105,105),(124,129,128),(149,153,149),
          (80,65,43),(105,82,47),(132,105,58),(155,127,75),(178,150,95),(98,42,32)]
 im=Image.new('RGBA',(16,16));p=im.load()
 for y in range(16):
  for x in range(16):
   if x in (0,15) or y in (0,15): c=palette[0]
   elif x==1 or y==1:c=palette[5]
   elif x==14 or y==14:c=palette[1]
   else:c=palette[2+((x//3+2*(y//3))%2)]
   if name=='nozzle' and 3<=x<=12 and y>=5:
    c=palette[(x//2+y//3)%3] if y<11 else palette[0 if x%4<2 else 1]
   if name=='actuator' and 3<=x<=12 and 3<=y<=12:
    c=palette[6+(x-3)//3] if y%3!=0 else palette[1]
   if name=='actuator' and x in (11,12) and y in (10,11):c=palette[11]
   p[x,y]=(*c,255)
 out=A/f'textures/block/flare/{name}.png';out.parent.mkdir(parents=True,exist_ok=True);im.save(out)


def model(name,parts,textures=TEXTURES):
 m=internal_model(parts);m['textures']=textures
 for e in m['elements']:
  for face in e['faces'].values():
   uv=face['uv'];shift=-16*math.floor(min(uv[1],uv[3])/16)
   uv[1]+=shift;uv[3]+=shift
 write(A/f'models/block/flare/{name}.json',m)


def models():
 tip=[]
 def box(parts,bounds,side,ends=None):parts.append((bounds,side,ends or side))
 for y0,y1,radius,inner,material in [(0,3,6,3,'frame'),(3,9,5,3,'steel'),(8,12,7,4,'frame'),(12,16,6,4,'nozzle')]:
  mask={(x,z) for x in range(1,15) for z in range(1,15)
        if max(abs(x-7.5),abs(z-7.5))<radius and abs(x-7.5)+abs(z-7.5)<radius*1.7
        and not (max(abs(x-7.5),abs(z-7.5))<inner and abs(x-7.5)+abs(z-7.5)<inner*1.7)}
  for x,z in sorted(mask):box(tip,[x,y0,z,x+1,y1,z+1],material)
 for x,z in [(1,7),(13,7),(7,13)]:
  box(tip,[x,5,z,x+2,14,z+2],'brass');box(tip,[x,11,z,x+2,13,z+2],'frame');box(tip,[x,14,z,x+2,17,z+2],'nozzle')
 for x,z in [(4,4),(10,4),(4,10),(10,10)]:box(tip,[x,15,z,x+2,18,z+2],'frame')
 # The tip is dull, heat-darkened steel rather than the bright sheet of the steam flue.
 model('tip',tip,{**TEXTURES,'steel':NS+'block/flare/steel','particle':NS+'block/flare/steel'})
 write(A/'blockstates/flare_tip.json',{'variants':{'':{'model':NS+'block/flare/tip'}}})
 for name,parent in [('flare_tip','tip')]:
  write(A/f'models/item/{name}.json',{'parent':NS+'block/flare/'+parent})


def data():
 for name in ('flare_tip',):
  write(D/f'loot_table/blocks/{name}.json',{'type':'minecraft:block','pools':[{'rolls':1,'entries':[{'type':'minecraft:item','name':NS+name}],'conditions':[{'condition':'minecraft:survives_explosion'}]}]})
 for name,pattern,key in [
  ('flare_tip',['IBI','SPS',' C '],{'I':{'item':'minecraft:iron_bars'},'B':{'tag':'c:plates/brass'},'S':{'tag':'c:plates/iron'},'P':{'item':NS+'reinforced_pipe'},'C':{'item':'minecraft:flint_and_steel'}}),
 ]:
  write(D/f'recipe/{name}.json',{'type':'minecraft:crafting_shaped','pattern':pattern,'key':key,'result':{'id':NS+name,'count':1}})
 for tag in ['minecraft/tags/block/mineable/pickaxe.json','minecraft/tags/block/needs_iron_tool.json']:
  p=ROOT/'src/main/resources/data'/tag
  values=json.loads(p.read_text()) if p.exists() else {'replace':False,'values':[]}
  values['values']=list(dict.fromkeys(values['values']+[NS+'flare_tip']));write(p,values)
 p=A/'sounds.json';raw=p.read_text()
 if '"machine.flare_roar"' not in raw:
  raw=raw.rstrip()[:-1].rstrip()+',\n  "machine.flare_roar": {\n    "subtitle": "subtitles.create_refinery_n_reactors.machine.flare_roar",\n    "sounds": [{ "name": "create_refinery_n_reactors:machine/flare_roar", "attenuation_distance": 96 }]\n  }\n}\n'
  p.write_text(raw)



def translations():
 for index,lang in enumerate(('en_us','ja_jp','zh_cn')):
  p=A/f'lang/{lang}.json';raw=p.read_bytes().decode('utf-8');nl='\r\n' if '\r\n' in raw else '\n';values=json.loads(raw)
  for key,words in NAMES.items():values[key]=words[index]
  p.write_bytes((json.dumps(values,ensure_ascii=False,indent=2).replace('\n',nl)+nl).encode('utf-8'))


if __name__=='__main__':
 for name in ('steel','nozzle','actuator'):texture(name)
 models();data();translations()
