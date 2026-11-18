#!/usr/bin/env python3
"""White zinc-coated native Create tank casing and telescopic bell plates; only this holder is generated."""
from pathlib import Path
import json, shutil
from PIL import Image
import generate_plant_tank_assets as native
R=Path(__file__).resolve().parents[1];A=R/'src/main/resources/assets/create_refinery_n_reactors';D=R/'src/main/resources/data';NS='create_refinery_n_reactors:'
def write(path,data):
 path.parent.mkdir(parents=True,exist_ok=True);path.write_text(json.dumps(data,ensure_ascii=False,indent=2)+'\n')
P={'hi':(244,243,232,255),'shadow':(81,96,100,255),'frame':(135,148,147,255),'rivet':(174,147,91,255)}
def tile(kind,w,h,c,r):
 im=Image.new('RGBA',(16,16));pix=im.load()
 for y in range(16):
  for x in range(16):
   gx,gy=c*16+x,r*16+y;edge=min(gx,gy,w*16-1-gx,h*16-1-gy)
   tone=(gx//3+2*(gy//3))%5
   colour=[(219,222,214,255),(224,226,217,255),(217,221,213,255),(221,224,215,255),(219,222,214,255)][tone]
   if kind=='inner':colour=(180,190,187,255) if tone else (187,195,190,255)
   if edge==0:colour=P['hi'] if gx==0 or gy==0 else P['shadow']
   elif edge==1:colour=P['rivet'] if (gx+gy)%8==3 else P['frame']
   elif edge==2:colour=(234,236,225,255) if gx==2 or gy==2 else (173,184,181,255)
   pix[x,y]=colour
 return im
def connected(kind):
 im=Image.new('RGBA',(64,64))
 for tx,(w,c) in enumerate(((1,0),(3,0),(3,1),(3,2))):
  for ty,(h,r) in enumerate(((3,0),(3,1),(3,2),(1,0))):im.paste(tile(kind,w,h,c,r),(tx*16,ty*16))
 return im
def generate():
 for file in ['gas_holder_anchor','gas_holder_guide_rail']:
  for p in [A/f'blockstates/{file}.json',A/f'models/item/{file}.json',D/f'create_refinery_n_reactors/recipe/{file}.json',D/f'create_refinery_n_reactors/loot_table/blocks/{file}.json']:p.unlink(missing_ok=True)
 for folder in [A/'models/block/gas_bell',A/'textures/block/gas_bell']:
  if folder.exists():shutil.rmtree(folder)
 textures=A/'textures/block/tank';textures.mkdir(parents=True,exist_ok=True)
 for suffix,kind in [('', 'side'),('_top','top'),('_inner','inner')]:
  tile(kind,1,1,0,0).save(textures/f'gas_holder{suffix}.png');connected(kind).save(textures/f'gas_holder{suffix}_connected.png')
 for suffix,mask in [('_window',native.WINDOW),('_window_single',native.WINDOW_SINGLE)]:native.window(P,mask).save(textures/f'gas_holder{suffix}.png')
 body=A/'textures/block/gas_holder';body.mkdir(parents=True,exist_ok=True)
 for name in ['plate','rim']:
  im=Image.new('RGBA',(16,16));pix=im.load()
  for y in range(16):
   for x in range(16):
    shade=(x//3+2*(y//3))%4
    colour=[(219,222,214,255),(226,228,218,255),(213,218,211,255),(220,223,215,255)][shade]
    if name=='rim':
     colour=(113,131,133,255) if y>2 else (188,201,194,255)
     if y>=13:colour=(73,91,96,255)
     if 6<=x<=9 and 6<=y<=9:colour=P['rivet'] if x<9 and y<9 else (104,87,54,255)
    elif y in (0,15):colour=(186,198,191,255) if y==0 else (153,169,166,255)
    pix[x,y]=colour
  im.save(body/f'{name}.png')
 tex=lambda s:NS+f'block/tank/gas_holder{s}'
 material={'0':tex('_top'),'1':tex(''),'3':tex('_window'),'4':tex('_inner'),'5':tex('_window_single'),'particle':tex('')}
 for name in native.CREATE_TANK_MODELS:
  write(A/f'models/block/gas_holder/{name}.json',{'parent':f'create:block/fluid_tank/{name}','render_type':'minecraft:cutout_mipped','textures':material})
 variants={}
 for top in ['false','true']:
  for bottom in ['false','true']:
   for shape in ['plain','window','window_ne','window_nw','window_se','window_sw']:
    part='bottom' if bottom=='true' else 'middle';suffix='' if shape=='plain' else '_'+shape
    variants[f'bottom={bottom},shape={shape},top={top}']={'model':NS+f'block/gas_holder/block_{part}{suffix}'}
 write(A/'blockstates/gas_holder.json',{'variants':variants})
 write(A/'models/item/gas_holder.json',{'parent':NS+'block/gas_holder/block_single_window'})
 write(D/'create_refinery_n_reactors/loot_table/blocks/gas_holder.json',{'type':'minecraft:block','pools':[{'rolls':1,'entries':[{'type':'minecraft:item','name':NS+'gas_holder'}],'conditions':[{'condition':'minecraft:survives_explosion'}]}]})
 write(D/'create_refinery_n_reactors/recipe/gas_holder.json',{'type':'minecraft:crafting_shaped','pattern':[' I ','ZWZ',' T '],'key':{'I':{'tag':'c:plates/iron'},'Z':{'item':'create:zinc_ingot'},'W':{'item':'minecraft:water_bucket'},'T':{'item':'create:fluid_tank'}},'result':{'id':NS+'gas_holder','count':1}})
 for name in ['mineable/pickaxe','needs_iron_tool']:
  p=D/f'minecraft/tags/block/{name}.json';data=json.loads(p.read_text());data['values']=[v for v in data['values'] if v not in [NS+'gas_holder_anchor',NS+'gas_holder_guide_rail']]
  if NS+'gas_holder' not in data['values']:data['values'].append(NS+'gas_holder')
  write(p,data)
 for index,lang in enumerate(['en_us','ja_jp','zh_cn']):
  p=A/f'lang/{lang}.json';raw=p.read_bytes().decode();nl='\r\n' if '\r\n' in raw else '\n';data=json.loads(raw)
  for key in list(data):
   if key.startswith('gas_bell.create_refinery_n_reactors.') or key in ['block.create_refinery_n_reactors.gas_holder_anchor','block.create_refinery_n_reactors.gas_holder_guide_rail']:del data[key]
  for key,value in TEXT.items():data[key]=value[index]
  p.write_bytes((json.dumps(data,ensure_ascii=False,indent=2).replace('\n',nl)+nl).encode())
TEXT={
'block.create_refinery_n_reactors.gas_holder':('Telescopic Gas Holder','伸縮式ガスホルダー','伸缩式储气柜'),
'entity.create_refinery_n_reactors.gas_holder_sleeve':('Gas Holder Bell','ガスホルダーの伸縮筒','储气柜钟罩'),
'fluid.create_refinery_n_reactors.contaminated_gas':('Contaminated Gas','汚染混合ガス','污染混合气体'),
'gas_bell.create_refinery_n_reactors.state':('Gas holder: %s','ガスホルダー：%s','储气柜：%s'),
'gas_bell.create_refinery_n_reactors.contents':('%s: %s / %s mB','%s：%s / %s mB','%s：%s / %s mB'),
'gas_bell.create_refinery_n_reactors.travel':('Lift: %s / %s m | %s bar','上昇量：%s / %s m｜%s bar','升高：%s / %s m｜%s bar'),
'gas_bell.create_refinery_n_reactors.controls':('Wrench: windows | Shift-wrench: purge','レンチ：窓／Shift＋レンチ：パージ','扳手：视窗；潜行＋扳手：排空'),
'gas_bell.create_refinery_n_reactors.empty':('Empty','空','空'),
'gas_bell.create_refinery_n_reactors.ready':('Three nested bells ready','3段の筒がガス量に応じて伸縮','三段钟罩随气量伸缩'),
'gas_bell.create_refinery_n_reactors.height':('Stack at least two complete tank layers','同じ幅で2段以上積んでください','请堆叠至少两层完整柜体'),
'gas_bell.create_refinery_n_reactors.contaminated':('Contaminated: drain to a lit flare or purge','汚染：点火したフレアへ排出またはパージ','已污染：排向点燃的火炬或排空'),
'gas_bell.create_refinery_n_reactors.unloaded':('Part unloaded: motion and transfer paused','一部が未読込：伸縮と搬送を停止','部分未加载：暂停伸缩和输送'),
'gas_bell.create_refinery_n_reactors.blocked':('Bell obstructed: clear its path','伸縮部が障害物に接触：進路を空けてください','钟罩受阻：请清空路径'),
'gas_bell.create_refinery_n_reactors.missing':('Saved sleeve unavailable: inspect and rebuild','保存された伸縮部が見つかりません：点検・再建','找不到保存的套筒：请检查并重建'),
'gas_bell.create_refinery_n_reactors.reaction':('Explosive mixing: rebuild the holder','混合反応で爆発：再建が必要','混气爆炸：必须重建'),
'gas_bell.create_refinery_n_reactors.removed':('Casing broken: gas released','外槽が破損：ガスを放出','外壳破损：已释放气体'),
}
if __name__=='__main__':generate()
