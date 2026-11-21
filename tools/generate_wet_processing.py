#!/usr/bin/env python3
"""Wet-process machine parts, finite batch recipes, captions and full-scale teaching layout."""
import json, math
from pathlib import Path
from PIL import Image, ImageDraw
import generate_petrochemical_ponder as p
from generate_world_liquids import bucket_image
R=Path(__file__).resolve().parents[1];A=R/'src/main/resources/assets/create_refinery_n_reactors';D=R/'src/main/resources/data/create_refinery_n_reactors';NS='create_refinery_n_reactors:'
PARTS={
'clarifier_basin_wall':('Lined Clarifier Basin','沈殿池のライニング槽壁','沉淀池衬里槽壁'),
'clarifier_feed_port':('Clarifier Bottom Feed','沈殿池の底面流入口','沉淀池底部进料口'),
'overflow_weir':('Clarifier Overflow Weir','沈殿池の越流堰','沉淀池溢流堰'),
'underflow_drain':('Clarifier Underflow Drain','沈殿池の汚泥排出口','沉淀池污泥出口'),
'clarifier_center_drive':('Clarifier Center Drive','円形沈殿池のセンタードライブ','圆形沉淀池中心驱动器')}
COLORS={'copper':0xFF315B57,'iron':0xFF884A35,'zinc':0xFF73867A,'tin':0xFF7A817F,'wastewater':0xFF716557}
def write(path,data):
 path.parent.mkdir(parents=True,exist_ok=True);path.write_text(json.dumps(data,ensure_ascii=False,indent=2)+'\n')
def box(bounds,texture,rotation=None):
 d={'from':bounds[:3],'to':bounds[3:],'faces':{face:{'texture':'#'+texture} for face in p.DIRECTIONS}}
 if rotation:d['rotation']=rotation
 return d
def model(name,elements,textures=None):
 tex={'steel':NS+'block/chemical/stainless','concrete':NS+'block/industrial_concrete','frame':NS+'block/pressure/frame',
 'brass':'create:block/brass_block','particle':NS+'block/chemical/stainless'}
 if textures:tex.update(textures)
 write(A/f'models/block/wet_processing/{name}.json',{'parent':'minecraft:block/block','textures':tex,'elements':elements})
def assets():
 for name in ('slurry_still','slurry_flow'):
  texture=Image.new('RGBA',(16,16))
  for y in range(16):
   for x in range(16):
    value=180+((x*23+y*13+(x*y)%11)%5)*7
    texture.putpixel((x,y),(value,value,value,255))
  target=A/f'textures/block/wet_processing/{name}.png';target.parent.mkdir(parents=True,exist_ok=True);texture.save(target)
 for name in PARTS:
  if name=='clarifier_center_drive':
   elements=[box([1,0,1,15,3,15],'steel'),box([4,3,4,12,11,12],'frame'),box([2,9,2,14,12,14],'steel'),box([6,12,6,10,16,10],'steel'),box([5,4,3,11,9,4],'brass'),box([1,3,1,3,9,15],'brass')]
  elif name=='clarifier_basin_wall':elements=[box([0,0,0,16,16,16],'concrete'),box([0,14,0,16,16,16],'steel')]
  else:
   elements=[box([0,0,2,16,16,16],'concrete'),box([3,3,0,13,13,2],'frame'),box([5,5,0,11,11,3],'brass')]
   if name=='underflow_drain':elements += [box([3,0,3,13,2,13],'frame'),box([5,0,5,11,3,11],'steel')]
   if name=='overflow_weir':elements += [box([2,12,0,14,13,7],'steel'),box([2,13,0,3,16,7],'brass'),box([13,13,0,14,16,7],'brass')]
  textures=None
  if name in ('clarifier_feed_port','overflow_weir','underflow_drain'):
   elements.append(box([5,10,1.5,11,16,1.6],'sign'));textures={'sign':NS+f'block/wet_processing/{name}_sign'}
  model(name,elements,textures)
  rotations={'north':{},'east':{'y':90},'south':{'y':180},'west':{'y':270}}
  if name!='clarifier_center_drive':rotations.update(up={'x':90},down={'x':270})
  write(A/f'blockstates/{name}.json',{'variants':{f'facing={face}':{'model':NS+'block/wet_processing/'+name,**rot} for face,rot in rotations.items()}})
  write(A/f'models/item/{name}.json',{'parent':NS+'block/wet_processing/'+name})
  write(D/f'loot_table/blocks/{name}.json',{'type':'minecraft:block','pools':[{'rolls':1,'entries':[{'type':'minecraft:item','name':NS+name}],'conditions':[{'condition':'minecraft:survives_explosion'}]}]})
 model('shaft',[box([7,0,7,9,16,9],'steel')])
 bridge=[box([0,0,5,16,2,11],'steel'),box([0,6,5,16,7,6],'brass'),box([0,6,10,16,7,11],'brass')]
 for x in (0,4,8,12,15):bridge.append(box([x,2,5,x+1,7,6],'steel'));bridge.append(box([x,2,10,x+1,7,11],'steel'))
 model('bridge',bridge)
 model('scraper',[box([0,0,6,16,2,10],'steel'),box([0,1,5,16,4,6],'brass'),box([0,1,10,16,4,11],'brass')])
 model('feedwell',[box([2,0,2,4,16,14],'steel'),box([12,0,2,14,16,14],'steel'),box([4,0,2,12,16,4],'steel'),box([4,0,12,12,16,14],'steel')])
 for name,color in COLORS.items():
  fluid=name+'_leach_slurry' if name!='wastewater' else 'wastewater_slurry'
  bucket_image(fluid+'_bucket',color,name!='wastewater')
  write(A/f'blockstates/{fluid}.json',{'variants':{'':{'model':'minecraft:block/water'}}})
 # The tailings keep the established pile silhouette; the polymer uses a separate pale granule sprite.
 im=Image.new('RGBA',(16,16));palette=('#a2a08c','#7e8169','#595f4b','#373e32')
 rows=['................','................','................','................','................','.....3333.......','...3322223......','..3221122233....','.321122222223...','.3222222112223..','..322211222223..','...3222222233...','....3333333.....','................','................','................']
 for y,row in enumerate(rows):
  for x,ch in enumerate(row):
   if ch!='.':im.putpixel((x,y),tuple(int(palette[int(ch)][i:i+2],16) for i in (1,3,5))+(255,))
 im.save(A/'textures/item/tailings_mud.png')
 im=Image.new('RGBA',(16,16));dr=ImageDraw.Draw(im)
 for x,y in ((3,7),(6,5),(9,7),(5,10),(10,10)):
  dr.ellipse((x-1,y-1,x+3,y+3),fill=(89,103,113,255));dr.rectangle((x,y,x+2,y+1),fill=(221,230,215,255));dr.point((x,y),fill=(248,245,218,255))
 im.save(A/'textures/item/flocculant.png')
 for name in ('tailings_mud','flocculant'):
  write(A/f'models/item/{name}.json',{'parent':'minecraft:item/generated','textures':{'layer0':NS+'item/'+name}})
 for tag in ('mineable/pickaxe','needs_iron_tool'):
  path=R/f'src/main/resources/data/minecraft/tags/block/{tag}.json';data=json.loads(path.read_text());data['values']=list(dict.fromkeys(data['values']+[NS+n for n in PARTS]));write(path,data)

def fluid(name,amount,tag=False):return {'type':'neoforge:tag' if tag else 'neoforge:single','tag' if tag else 'fluid':name,'amount':amount}
def recipes():
 write(D/'tags/fluid/leaching_oxidants.json',{'replace':False,'values':[NS+'compressed_air',NS+'oxygen_gas']})
 for metal in ('copper','iron','zinc','tin'):
  crushed=NS+'crushed_raw_tin' if metal=='tin' else 'create:crushed_raw_'+metal
  write(D/f'recipe/pressure_reaction/{metal}_leaching.json',{'type':NS+'pressure_reaction','module':'stirred','heat_requirement':'heated','processing_time':200,
   'ingredients':[{'item':crushed}]*3+[fluid(NS+'dilute_sulfuric_acid',750),fluid(NS+'leaching_oxidants',250,True)],
   'results':[{'id':NS+metal+'_leach_slurry','amount':4000}]})
  write(D/f'recipe/clarifying/{metal}.json',{'type':NS+'clarifying','processing_time':200,
   'ingredients':[fluid(NS+metal+'_leach_slurry',1000)],'results':[{'id':NS+metal+'_sulfate_solution','amount':750},{'id':NS+'tailings_mud','count':2}]})
 write(D/'recipe/pressure_reaction/wastewater_leaching.json',{'type':NS+'pressure_reaction','module':'stirred','processing_time':160,'ingredients':[{'item':'minecraft:clay_ball'},fluid(NS+'wastewater',1000),fluid(NS+'leaching_oxidants',125,True)],'results':[{'id':NS+'wastewater_slurry','amount':1000}]})
 write(D/'recipe/clarifying/wastewater.json',{'type':NS+'clarifying','processing_time':200,'ingredients':[fluid(NS+'wastewater_slurry',1000)],'results':[{'id':'minecraft:water','amount':750},{'id':NS+'tailings_mud','count':2}]})
 write(D/'recipe/mixing/flocculant.json',{'type':'create:mixing','ingredients':[{'item':NS+'polyethylene'},{'item':'minecraft:slime_ball'},fluid('minecraft:water',250)],'results':[{'id':NS+'flocculant','count':8}]})
 write(D/'recipe/pressing/tailings_dewatering.json',{'type':'create:pressing','ingredients':[{'item':NS+'tailings_mud'}],'results':[{'id':'minecraft:clay_ball'},{'id':'minecraft:sand','chance':.5}]})
 write(D/'recipe/mixing/tailings_drying.json',{'type':'create:mixing','heat_requirement':'heated','ingredients':[{'item':NS+'tailings_mud'}],'results':[{'id':'minecraft:clay_ball'},{'id':'minecraft:sand','chance':.5},{'id':'minecraft:quartz','chance':.125}]})
 for name in PARTS:
  if name=='clarifier_center_drive':
   pattern=[' S ','CMC',' T '];key={'S':{'item':'create:shaft'},'C':{'item':NS+'stainless_steel_ingot'},'M':{'item':'create:precision_mechanism'},'T':{'item':NS+'tank_casing'}};count=1
  elif name=='clarifier_basin_wall':pattern=['SSS','CCC'];key={'S':{'item':'create:iron_sheet'},'C':{'item':NS+'industrial_concrete'}};count=6
  else:
   pattern=[' C ','CPC',' C '];key={'C':{'item':NS+'tank_casing'},'P':{'item':'create:andesite_funnel' if name=='underflow_drain' else 'create:brass_sheet' if name=='overflow_weir' else NS+'stainless_steel_pipe'}};count=1
  write(D/f'recipe/{name}.json',{'type':'minecraft:crafting_shaped','category':'misc','pattern':pattern,'key':key,'result':{'id':NS+name,'count':count}})

def translations():
 texts={'block.create_refinery_n_reactors.'+n:v for n,v in PARTS.items()}
 texts.update({
 'item.create_refinery_n_reactors.tailings_mud':('Tailings Mud','尾鉱泥','尾矿泥'),
 'item.create_refinery_n_reactors.flocculant':('Polymer Flocculant','高分子凝集剤','高分子絮凝剂'),
 'menu.create_refinery_n_reactors.facility_constructor.basin_diameter':('Diameter: %s m','直径：%s m','直径：%s m'),
 'menu.create_refinery_n_reactors.facility_constructor.basin_height':('Height: %s m','高さ：%s m','高度：%s m'),
 'facility.create_refinery_n_reactors.clarifier':('Circular Clarifier','円形沈殿池','圆形沉淀池'),
 'jei.create_refinery_n_reactors.clarifying':('Slurry Clarification','スラリーの固液分離','矿浆固液分离'),
 'jei.create_refinery_n_reactors.note.clarifying':('%s ticks at 1–4 RPM. Optional flocculant: 2x speed. Above 4 RPM: dirty overflow, up to 2x electrowinning work.','1〜4 RPMで%s tick。凝集剤は任意・速度2倍。4 RPM超は濁り、電解電力が最大2倍。','1–4 RPM 时 %s tick。可选絮凝剂加速2倍。超过4 RPM使溢流混浊，电解能耗最高2倍。'),
 'wet_process.create_refinery_n_reactors.dimensions':('Wet process %s × %s × %s','湿式処理設備 %s×%s×%s','湿法处理设备 %s×%s×%s'),
 'wet_process.create_refinery_n_reactors.impurity':('Suspended impurity %s%%','懸濁不純物 %s%%','悬浮杂质 %s%%'),
 'wet_process.create_refinery_n_reactors.drive':('%s RPM / %s SU','%s RPM／%s SU','%s RPM／%s SU'),
 'wet_process.create_refinery_n_reactors.separation':('Separation %s%% · use 1–4 RPM','分離効率 %s%%・推奨1〜4 RPM','分离效率 %s%% · 建议1–4 RPM'),
 'display.create_refinery_n_reactors.separation':('Clarifier separation efficiency','沈殿池の分離効率','沉淀池分离效率')})
 states={'structure':('Incomplete tank: check shell, airspace and port directions','未形成：槽壁・内部の空間・ポートの向きを確認','未成型：检查槽壁、内部空间和接口朝向'),
 'capacity':('Stored contents exceed this size; restore the original tank','在庫がこのサイズを超えています。元の大きさへ戻してください','库存超过此尺寸容量，请恢复原尺寸'),
 'feed':('Waiting for slurry','スラリーの供給待ち','等待矿浆'),
 'output':('Outlet full: drain overflow / mud','搬出口満杯：上澄み液・汚泥を搬出','出口已满：排出上清液和污泥'),
 'clarifying':('Clarifying at low speed','低速で固液分離中','正在低速固液分离'),
 'dirty':('Scraper too fast: contaminated overflow','回転過大：濁った上澄み液を生成','刮泥机转速过高：上清液受到污染'),
 'turbulent':('Turbulence: unchanged slurry overflow','乱流：未分離のスラリーが越流','湍流：未分离矿浆直接溢出'),
 'stagnant':('Settling; stopped scraper locks mud extraction','沈降中：停止しているため汚泥を搬出できません','正在沉降：刮泥机停止，污泥无法排出')}
 for k,v in states.items():texts['wet_process.create_refinery_n_reactors.status.'+k]=v
 metals={'copper':('Copper Leach Slurry','銅浸出スラリー','铜浸出矿浆'),'iron':('Iron Leach Slurry','鉄浸出スラリー','铁浸出矿浆'),'zinc':('Zinc Leach Slurry','亜鉛浸出スラリー','锌浸出矿浆'),'tin':('Tin Leach Slurry','錫浸出スラリー','锡浸出矿浆'),'wastewater':('Flocculated Wastewater Slurry','凝集懸濁廃水','絮凝废水浆')}
 for name,v in metals.items():
  n=name+'_leach_slurry' if name!='wastewater' else 'wastewater_slurry';texts['fluid.create_refinery_n_reactors.'+n]=v
  texts['block.create_refinery_n_reactors.'+n]=v
  texts['item.create_refinery_n_reactors.'+n+'_bucket']=(v[0]+' Bucket',v[1]+'入りバケツ',v[2]+'桶')
 for i,lang in enumerate(('en_us','ja_jp','zh_cn')):
  path=A/f'lang/{lang}.json';data=json.loads(path.read_text());data.update({k:v[i] for k,v in texts.items()});write(path,data)
 captions={
 'title':('Wet leaching and slow clarification','湿式浸出と低速沈降','湿法浸出与低速沉降'),
 'build':('Use a lidded vessel with stirred internals and heating below. No dedicated leaching tank is needed.', 'ふた付き槽に攪拌羽根を組み込み、下から加熱します。専用の浸出槽は不要です。', '在带盖槽中安装搅拌叶片并从下方加热，无需专用浸出槽。'),
 'ore':('Feed crushed iron, copper, zinc or tin through a funnel into the lidded vessel. Gold has no chemical route.', 'ふた付き槽へファンネルで砕いた鉄・銅・亜鉛・錫を投入。金は対象外です。', '用漏斗向带盖槽投入粉碎铁、铜、锌或锡，金不支持此路线。'),
 'inlets':('Supply dilute sulfuric acid to a lower liquid port and compressed air or oxygen to an upper gas process port.', '下段の液体入口へ希硫酸、上段の気体処理口へ圧搾空気か酸素を供給します。', '下层液体口供给稀硫酸，上层工艺气体口供给压缩空气或氧气。'),
 'stir':('Heat and stir: 3 crushed ores + 750 mB acid + 250 mB oxidant produce 4000 mB slurry in the lidded vessel.', '加熱しながら撹拌すると、砕いた鉱石3個＋希硫酸750 mB＋酸化用気体250 mBからスラリー4000 mBを生成します。', '加热搅拌：3份粉碎矿石＋750 mB酸＋250 mB氧化气体生成4000 mB矿浆。'),
 'clarifier':('Build an odd circular tank from tank casings. Place the center drive at rim height and replace the center floor block with a downward-facing inlet.', 'タンク構成ブロックで奇数径の円形タンクを作ります。縁と同じ高さの中央に駆動部を置き、床中央を下向きの流入口に替えます。', '用储罐外壳建造奇数直径的圆形储罐。在罐沿高度的中心安装驱动器，并把底板中心换成朝下的进料口。'),
 'slurry_side':('Pump slurry into the left wall or the bottom feed. Use acid-resistant piping for leach slurry.','ドライブと同じ向きで左側の壁か、底面流入口へポンプでスラリーを送ります。浸出スラリーには耐食配管を使います。','用泵从驱动同向时的左侧壁或底部进料口送入矿浆。浸出矿浆需要耐腐蚀管道。'),
 'flocculant_side':('Use a funnel at the opposite wall to load flocculant. One item halves a 200-tick batch.','反対側の壁からファンネルで凝集剤を投入できます。1個で200 tickの処理時間が半分になります。','在对侧壁用漏斗送入絮凝剂。每份可将200 tick的处理时间减半。'),
 'slow':('Reduce the center drive to 1–4 RPM. Per 1000 mB slurry, collect 750 mB clear solution at the rim and two tailings mud below.','中央の駆動を1〜4 RPMに落とします。スラリー1000 mBから精製液750 mBと尾鉱泥2個を分離します。','中心驱动降至1–4 RPM。每1000 mB矿浆分离为750 mB清液和2份尾矿泥。'),
 'fast':('At 5–16 RPM, suspended dirt carries into the overflow and raises the next electrowinning batch’s work, up to twice normal. At 17 RPM or above, the same slurry simply overflows without separation.','5〜16 RPMでは巻き上げた不純物が上澄みに残り、次の電解採取の電力が最大2倍に増加。17 RPM以上では分離せず、元のスラリーがそのまま越流します。','5–16 RPM使杂质进入溢流，后续电解能耗最高2倍。17 RPM以上无法分离，原矿浆直接溢出。'),
 'stopped':('At zero RPM, settling still works but mud cannot leave. The underflow fills and stops further processing until the scraper runs again.','0 RPMでも沈降しますが、汚泥を搬出できません。汚泥が満杯になると停止するので、低速で駆動を再開してください。','0 RPM仍会沉降，但污泥无法排出。积满后停止处理，需要恢复低速驱动。'),
 'recycle':('Send clear solution to the electrolysis cells: each 1000 mB returns 3 ingots and 250 mB acid. For 3 ores, this line yields 9 ingots and recycles 750 mB acid. Press tailings mud for clay and sand; heated mixing also recovers occasional quartz.','精製液を電解槽へ送り、1000 mBごとにインゴット3個と希硫酸250 mBを回収。鉱石3個から合計9個・酸750 mBが戻ります。尾鉱泥はプレスで粘土・砂へ、加熱ミキサーで乾燥すると石英も得られます。','清液送入电解槽：每1000 mB产3个锭并回收250 mB酸。3份矿石共得9个锭及750 mB回用酸。尾矿泥压榨得粘土和砂，加热搅拌干燥还能得少量石英。')}
 path=R/'tools/data/ponder_lessons.json';data=json.loads(path.read_text())
 for suffix,v in captions.items():data['wet_processing.'+suffix]=dict(zip(('en_us','ja_jp','zh_cn'),v))
 for key in list(data):
  if key.startswith('absorption_tower.'):del data[key]
 data['large_absorption_tower.observe']=dict(zip(('en_us','ja_jp','zh_cn'),('Use goggles to check formation and processing. The warning icon shows why the tower has stopped.','ゴーグルで形成状態と処理を確認します。⚠が出たら、その横に停止理由が表示されます。','用护目镜查看结构和处理。⚠ 旁会显示停机原因。')))
 write(path,data)

def machine(scene,center,width,height):
 cx,cy,cz=center;r=width//2;low=-r
 def inside(x,z):return -r<=x<=r and -r<=z<=r and (2*x)**2+(2*z)**2<=width*width
 for x in range(low,r+1):
  for z in range(low,r+1):
   if not inside(x,z):continue
   wall=not all(inside(xx,zz) for xx,zz in ((x+1,z),(x-1,z),(x,z+1),(x,z-1)))
   for y in range(1-height,1):
    name='tank_casing' if y==1-height or wall else None;face='down' if y==1-height else 'up'
    if x==z==0 and y==0:name='clarifier_center_drive';face='south'
    if x==z==0 and y==1-height:name='clarifier_feed_port';face='down'
    if name:scene.block(cx+x,cy+y,cz+z,NS+name,**({} if name=='tank_casing' else {'facing':face}))

def ponder():
 s=p.Structure(27,10);s.reactor(5,2,6,'stirred',2,burners=((-1,-1),));machine(s,(17,5,13),15,4)
 for x in (2,4):s.block(x,2,12,'create:crushing_wheel',axis='z')
 s.block(7,3,6,'create:andesite_funnel',facing='east',extracting='false',powered='false')
 # Bottom liquid ports, upper gas input and finite output storage use normal transport.
 points=[(5,2,z)for z in (8,9,10)]+[(5,2,z)for z in (4,3)]+[(x,2,3)for x in range(6,18)]+[(17,1,z)for z in range(3,14)]
 points += [(17,1,3),(17,5,21),(17,5,22)]
 points=list(dict.fromkeys(points));occupied=set(points)|{(5,2,7),(5,2,5),(17,2,13),(5,2,11),(17,5,20),(17,5,23),(7,3,5)}
 for x,y,z in points:
  faces=[d for d,delta in p.DIRECTIONS.items() if tuple(a+b for a,b in zip((x,y,z),delta))in occupied]
  if (x,y,z) in ((5,2,9),(7,2,3),(17,1,7),(17,5,22)):
   s.block(x,y,z,'create:mechanical_pump',facing='north' if x==5 else 'east' if x==7 else 'south',waterlogged='false');continue
  s.block(x,y,z,NS+'stainless_steel_pipe',waterlogged='false',**{d:str(d in faces).lower()for d in p.DIRECTIONS})
 for z in (4,5):s.block(7,3,z,NS+'reinforced_pipe',waterlogged='false',**{d:str(d in ('north','south') if z==4 else d in ('north','west')).lower()for d in p.DIRECTIONS})
 for z in (2,3):s.block(7,3,z,NS+'gas_receiver',axis='z',bottom=str(z==2).lower(),top=str(z==3).lower(),shape='plain')
 s.block(17,2,5,'create:andesite_funnel',facing='north',extracting='true',powered='false')
 s.block(9,3,13,'create:andesite_funnel',facing='west',extracting='false',powered='false')
 s.block(25,3,13,NS+'stainless_steel_pump',facing='west',waterlogged='false')
 s.block(26,3,13,NS+'stainless_steel_tank',bottom='true',top='true',shape='window')
 for x,y,z in ((5,2,11),(17,5,23)):s.block(x,y,z,NS+'stainless_steel_tank',bottom='true',top='true',shape='window')
 s.block(17,6,13,'create:shaft',axis='y',waterlogged='false');s.block(17,7,13,'create:creative_motor',facing='down')
 p.OUT=A/'ponder/plant';s.save('wet_processing')
 (A/'ponder/plant/ore_recovery.nbt').write_bytes((A/'ponder/plant/wet_processing.nbt').read_bytes())

def main():
 assets();recipes();translations();ponder()
 import gzip
 import nbtlib
 test=nbtlib.File({'DataVersion':nbtlib.Int(3955),'size':nbtlib.List[nbtlib.Int]([32,16,32]),'palette':nbtlib.List[nbtlib.Compound]([{'Name':nbtlib.String('minecraft:air')}]),'blocks':nbtlib.List[nbtlib.Compound]([]),'entities':nbtlib.List[nbtlib.Compound]([])})
 target=D/'structure/test_wet_processing.nbt';test.save(target,gzipped=True);target.write_bytes(gzip.compress(gzip.decompress(target.read_bytes()),mtime=0))
if __name__=='__main__':main()
